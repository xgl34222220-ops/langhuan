package com.xiguli.langhuan.data

import android.content.Context
import com.xiguli.langhuan.data.local.AiProviderEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.data.local.SecureApiKeyStore
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.ApiProtocol
import com.xiguli.langhuan.engine.HybridMemoryRetriever
import com.xiguli.langhuan.engine.MemoryCandidate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

data class PersistedStory(
    val snapshot: StorySnapshot,
    val draft: ChapterDraft,
)

data class StoredChapterVersion(
    val id: String,
    val chapterNumber: Int,
    val version: Int,
    val title: String,
    val content: String,
    val summary: String,
    val createdAt: Long,
)

data class StoredAiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val protocol: ApiProtocol,
    val model: String,
    val temperature: Double,
    val supportsJsonMode: Boolean,
    val isDefault: Boolean,
    val hasApiKey: Boolean,
)

data class ProviderSaveRequest(
    val id: String? = null,
    val name: String,
    val baseUrl: String,
    val protocol: ApiProtocol,
    val model: String,
    val temperature: Double = 0.72,
    val supportsJsonMode: Boolean,
    val apiKey: String,
    val makeDefault: Boolean = true,
)

class PersistentStoryRepository(context: Context) {
    private val db = LanghuanDatabase.get(context)
    private val storyDao = db.storyStateDao()
    private val chapterDao = db.chapterVersionDao()
    private val providerDao = db.aiProviderDao()
    private val memoryDao = db.memoryChunkDao()
    private val keyStore = SecureApiKeyStore(context)
    private val memoryRetriever = HybridMemoryRetriever()

    suspend fun seedIfNeeded(demo: DemoStoryRepository) {
        if (storyDao.get(demo.snapshot.novel.id) != null) return
        val now = System.currentTimeMillis()
        storyDao.upsert(
            StoryStateEntity(
                novelId = demo.snapshot.novel.id,
                snapshotJson = StoreJson.encodeToString(StorySnapshot.serializer(), demo.snapshot),
                draftJson = StoreJson.encodeToString(ChapterDraft.serializer(), demo.currentDraft),
                updatedAt = now,
            )
        )
        rebuildMemoryIndex(demo.snapshot)
    }

    suspend fun loadStory(novelId: String, fallback: PersistedStory): PersistedStory {
        val entity = storyDao.get(novelId) ?: return fallback
        return runCatching {
            PersistedStory(
                snapshot = StoreJson.decodeFromString(StorySnapshot.serializer(), entity.snapshotJson),
                draft = StoreJson.decodeFromString(ChapterDraft.serializer(), entity.draftJson),
            )
        }.getOrElse { fallback }
    }

    suspend fun commitGenerated(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        generated: GeneratedChapter,
    ): PersistedStory {
        val now = System.currentTimeMillis()
        val latestVersion = chapterDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version
        val newVersion = maxOf(draft.version, latestVersion) + 1
        val newDraft = draft.copy(
            title = generated.title.ifBlank { draft.title },
            content = generated.content,
            summary = generated.summary,
            version = newVersion,
        )
        val wordDelta = generated.content.length - draft.content.length
        val withChanges = applyCharacterChanges(snapshot, generated.stateChanges, draft.chapterNumber)
        val chapterSummary = "第${draft.chapterNumber}章：${generated.summary}".trim()
        val summaryHistory = (withChanges.recentSummaries + chapterSummary)
            .filter { it.isNotBlank() }
            .distinct()
        val foldCount = (summaryHistory.size - 8).coerceAtLeast(0)
        val folded = summaryHistory.take(foldCount)
        val newSnapshot = withChanges.copy(
            novel = withChanges.novel.copy(
                currentWords = (withChanges.novel.currentWords + wordDelta).coerceAtLeast(0),
            ),
            recentSummaries = summaryHistory.takeLast(8),
            longTermSummary = foldLongTermSummary(withChanges.longTermSummary, folded),
        )
        persistStory(newSnapshot, newDraft, now)
        saveChapterVersion(newDraft, now)
        rebuildMemoryIndex(newSnapshot)
        upsertChapterMemory(newDraft, now)
        return PersistedStory(newSnapshot, newDraft)
    }

    suspend fun saveDraft(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
        val now = System.currentTimeMillis()
        val stored = storyDao.get(snapshot.novel.id)
        val oldDraft = stored?.let {
            runCatching { StoreJson.decodeFromString(ChapterDraft.serializer(), it.draftJson) }.getOrNull()
        }
        val latestVersion = chapterDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version
        val versionedDraft = draft.copy(version = maxOf(draft.version, latestVersion) + 1)
        val delta = versionedDraft.content.length - (oldDraft?.content?.length ?: draft.content.length)
        val updatedSnapshot = snapshot.copy(
            novel = snapshot.novel.copy(currentWords = (snapshot.novel.currentWords + delta).coerceAtLeast(0)),
        )
        persistStory(updatedSnapshot, versionedDraft, now)
        saveChapterVersion(versionedDraft, now)
        rebuildMemoryIndex(updatedSnapshot)
        upsertChapterMemory(versionedDraft, now)
        return PersistedStory(updatedSnapshot, versionedDraft)
    }

    suspend fun chapterVersions(novelId: String, chapterNumber: Int): List<StoredChapterVersion> =
        chapterDao.forChapter(novelId, chapterNumber).map { it.toStored() }

    suspend fun restoreVersion(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        version: StoredChapterVersion,
    ): PersistedStory {
        require(version.chapterNumber == draft.chapterNumber) { "版本不属于当前章节" }
        return saveDraft(
            snapshot,
            draft.copy(
                title = version.title,
                content = version.content,
                summary = version.summary,
                version = maxOf(draft.version, version.version),
            ),
        )
    }

    suspend fun retrieveRelevantMemories(
        novelId: String,
        query: String,
        currentChapter: Int,
        limit: Int = 8,
    ): List<String> {
        val candidates = memoryDao.recent(novelId, 1_200).map {
            MemoryCandidate(
                text = it.text,
                sourceType = it.sourceType,
                sourceId = it.sourceId,
                chapterNumber = it.chapterNumber,
                updatedAt = it.updatedAt,
            )
        }
        return memoryRetriever.rank(query, candidates, currentChapter, limit)
            .map { hit -> "[${hit.candidate.sourceType}] ${hit.candidate.text}" }
    }

    fun observeProviders(): Flow<List<StoredAiProvider>> = providerDao.observeAll().map { list ->
        list.map { it.toStored() }
    }

    suspend fun saveProvider(request: ProviderSaveRequest): StoredAiProvider {
        val id = request.id ?: UUID.randomUUID().toString()
        val existing = providerDao.getById(id)
        val now = System.currentTimeMillis()
        val shouldDefault = request.makeDefault || existing?.isDefault == true || providerDao.count() == 0
        val entity = AiProviderEntity(
            id = id,
            name = request.name.ifBlank { request.protocol.label },
            baseUrl = request.baseUrl.trimEnd('/'),
            protocol = request.protocol.name,
            model = request.model.trim(),
            temperature = request.temperature,
            supportsJsonMode = request.supportsJsonMode,
            isDefault = shouldDefault,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        providerDao.upsert(entity)
        if (request.apiKey.isNotBlank()) keyStore.put(id, request.apiKey)
        if (shouldDefault) providerDao.markDefault(id, now)
        return (providerDao.getById(id) ?: entity).toStored()
    }

    suspend fun setDefaultProvider(id: String) {
        providerDao.markDefault(id, System.currentTimeMillis())
    }

    suspend fun deleteProvider(id: String) {
        providerDao.delete(id)
        keyStore.remove(id)
    }

    suspend fun apiKey(id: String): String? = keyStore.get(id)

    suspend fun providerConfig(id: String): AiProviderConfig? {
        val entity = providerDao.getById(id) ?: return null
        return AiProviderConfig(
            baseUrl = entity.baseUrl,
            apiKey = keyStore.get(id).orEmpty(),
            model = entity.model,
            protocol = entity.protocol.toProtocol(),
            temperature = entity.temperature,
            supportsJsonMode = entity.supportsJsonMode,
        )
    }

    private suspend fun persistStory(snapshot: StorySnapshot, draft: ChapterDraft, now: Long) {
        storyDao.upsert(
            StoryStateEntity(
                novelId = snapshot.novel.id,
                snapshotJson = StoreJson.encodeToString(StorySnapshot.serializer(), snapshot),
                draftJson = StoreJson.encodeToString(ChapterDraft.serializer(), draft),
                updatedAt = now,
            )
        )
    }

    private suspend fun saveChapterVersion(draft: ChapterDraft, now: Long) {
        chapterDao.upsert(
            ChapterVersionEntity(
                id = "${draft.id}:v${draft.version}",
                novelId = draft.novelId,
                chapterNumber = draft.chapterNumber,
                version = draft.version,
                title = draft.title,
                content = draft.content,
                summary = draft.summary,
                createdAt = now,
            )
        )
    }

    private suspend fun upsertChapterMemory(draft: ChapterDraft, now: Long) {
        memoryDao.upsert(
            MemoryChunkEntity(
                id = "chapter:${draft.id}:v${draft.version}",
                novelId = draft.novelId,
                sourceType = "CHAPTER",
                sourceId = draft.id,
                chapterNumber = draft.chapterNumber,
                text = buildString {
                    append("第${draft.chapterNumber}章 ${draft.title}。")
                    if (draft.summary.isNotBlank()) append(draft.summary)
                    if (draft.content.isNotBlank()) append("\n").append(draft.content.take(4_000))
                },
                updatedAt = now,
            )
        )
    }

    private suspend fun rebuildMemoryIndex(snapshot: StorySnapshot) {
        val now = System.currentTimeMillis()
        val novelId = snapshot.novel.id
        val chunks = buildList {
            snapshot.bible.forEach { item ->
                add(MemoryChunkEntity("bible:${item.id}", novelId, "BIBLE", item.id, null, "${item.name}：${item.content}", now))
            }
            snapshot.characters.forEach { item ->
                add(
                    MemoryChunkEntity(
                        "character:${item.id}", novelId, "CHARACTER", item.id, item.lastUpdatedChapter,
                        "${item.name}。性格：${item.personality.joinToString("、")}。地点：${item.location}。身体：${item.physicalState}。情绪：${item.emotionalState}。目标：${item.goal}。已知秘密：${item.knownSecrets.joinToString("、")}。持有：${item.possessions.joinToString("、")}。",
                        now,
                    )
                )
            }
            snapshot.recentTimeline.forEach { item ->
                add(MemoryChunkEntity("timeline:${item.id}", novelId, "TIMELINE", item.id, item.chapter, "第${item.chapter}章 ${item.storyTime} ${item.location}：${item.summary} 后果：${item.consequences.joinToString("、")}", now))
            }
            snapshot.relevantForeshadowing.forEach { item ->
                add(MemoryChunkEntity("foreshadow:${item.id}", novelId, "FORESHADOW", item.id, item.plantedChapter, "伏笔「${item.title}」：${item.detail}；预计回收：${item.expectedPayoff}；状态：${item.status.name}", now))
            }
            if (snapshot.longTermSummary.isNotBlank()) {
                add(MemoryChunkEntity("long-summary:$novelId", novelId, "LONG_SUMMARY", "long-summary", null, snapshot.longTermSummary, now))
            }
            snapshot.recentSummaries.forEachIndexed { index, text ->
                add(MemoryChunkEntity("summary:${novelId}:$index", novelId, "SUMMARY", "summary-$index", null, text, now))
            }
        }
        // 只重建结构化记忆，历史章节块必须保留，否则长篇写到后面会遗失旧正文。
        memoryDao.deleteStructuredForNovel(novelId)
        if (chunks.isNotEmpty()) memoryDao.upsertAll(chunks)
    }

    private fun foldLongTermSummary(existing: String, folded: List<String>): String {
        if (folded.isEmpty()) return existing
        val additions = folded.joinToString("\n") { it.take(360) }
        val merged = listOf(existing.trim(), additions.trim()).filter { it.isNotBlank() }.joinToString("\n")
        if (merged.length <= 6_500) return merged
        // 保留最早的核心开头和最近的折叠摘要，避免上下文无限增长。
        return merged.take(1_500) + "\n……\n" + merged.takeLast(4_700)
    }

    private fun applyCharacterChanges(snapshot: StorySnapshot, changes: List<StateChange>, chapter: Int): StorySnapshot {
        if (changes.isEmpty()) return snapshot
        val updated = snapshot.characters.map { original ->
            changes.filter { it.subject == original.name }.fold(original) { current, change ->
                current.applyChange(change, chapter)
            }
        }
        return snapshot.copy(characters = updated)
    }

    private fun CharacterState.applyChange(change: StateChange, chapter: Int): CharacterState = when (change.field.lowercase()) {
        "location", "位置" -> copy(location = change.after, lastUpdatedChapter = chapter)
        "physicalstate", "身体状态", "伤势" -> copy(physicalState = change.after, lastUpdatedChapter = chapter)
        "emotionalstate", "情绪", "情绪状态" -> copy(emotionalState = change.after, lastUpdatedChapter = chapter)
        "goal", "目标" -> copy(goal = change.after, lastUpdatedChapter = chapter)
        "knownsecrets", "秘密", "已知秘密" -> copy(
            knownSecrets = (knownSecrets + change.after).filter { it.isNotBlank() }.distinct(),
            lastUpdatedChapter = chapter,
        )
        "possessions", "物品", "持有物" -> copy(
            possessions = (possessions + change.after).filter { it.isNotBlank() }.distinct(),
            lastUpdatedChapter = chapter,
        )
        else -> this
    }

    private fun AiProviderEntity.toStored() = StoredAiProvider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        protocol = protocol.toProtocol(),
        model = model,
        temperature = temperature,
        supportsJsonMode = supportsJsonMode,
        isDefault = isDefault,
        hasApiKey = keyStore.has(id),
    )

    private fun ChapterVersionEntity.toStored() = StoredChapterVersion(
        id = id,
        chapterNumber = chapterNumber,
        version = version,
        title = title,
        content = content,
        summary = summary,
        createdAt = createdAt,
    )

    private fun String.toProtocol(): ApiProtocol = ApiProtocol.entries.firstOrNull { it.name == this } ?: ApiProtocol.AUTO
}
