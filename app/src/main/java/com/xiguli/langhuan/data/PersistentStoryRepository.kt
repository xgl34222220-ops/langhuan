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
        val newVersion = draft.version + 1
        val newDraft = draft.copy(
            title = generated.title.ifBlank { draft.title },
            content = generated.content,
            summary = generated.summary,
            version = newVersion,
        )
        val wordDelta = (generated.content.length - draft.content.length).coerceAtLeast(0)
        val withChanges = applyCharacterChanges(snapshot, generated.stateChanges, draft.chapterNumber)
        val newSnapshot = withChanges.copy(
            novel = withChanges.novel.copy(currentWords = withChanges.novel.currentWords + wordDelta),
            recentSummaries = (withChanges.recentSummaries + "第${draft.chapterNumber}章：${generated.summary}")
                .filter { it.isNotBlank() }.distinct().takeLast(12),
        )
        storyDao.upsert(
            StoryStateEntity(
                novelId = newSnapshot.novel.id,
                snapshotJson = StoreJson.encodeToString(StorySnapshot.serializer(), newSnapshot),
                draftJson = StoreJson.encodeToString(ChapterDraft.serializer(), newDraft),
                updatedAt = now,
            )
        )
        chapterDao.upsert(
            ChapterVersionEntity(
                id = "${newDraft.id}:v$newVersion",
                novelId = newDraft.novelId,
                chapterNumber = newDraft.chapterNumber,
                version = newVersion,
                title = newDraft.title,
                content = newDraft.content,
                summary = newDraft.summary,
                createdAt = now,
            )
        )
        rebuildMemoryIndex(newSnapshot)
        memoryDao.upsert(
            MemoryChunkEntity(
                id = "chapter:${newDraft.id}:v$newVersion",
                novelId = newDraft.novelId,
                sourceType = "CHAPTER",
                sourceId = newDraft.id,
                chapterNumber = newDraft.chapterNumber,
                text = buildString {
                    append("第${newDraft.chapterNumber}章 ${newDraft.title}。")
                    append(newDraft.summary)
                    if (newDraft.content.isNotBlank()) append("\n").append(newDraft.content.take(2800))
                },
                updatedAt = now,
            )
        )
        return PersistedStory(newSnapshot, newDraft)
    }

    suspend fun retrieveRelevantMemories(novelId: String, query: String, limit: Int = 6): List<String> {
        val q = grams(query)
        if (q.isEmpty()) return emptyList()
        return memoryDao.recent(novelId, 900)
            .asSequence()
            .map { item ->
                val grams = grams(item.text)
                val overlap = grams.count { it in q }
                val score = if (grams.isEmpty()) 0.0 else overlap.toDouble() / q.size.coerceAtLeast(1)
                item to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first.text }
            .distinct()
            .take(limit)
            .toList()
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
            snapshot.recentSummaries.forEachIndexed { index, text ->
                add(MemoryChunkEntity("summary:${novelId}:$index", novelId, "SUMMARY", "summary-$index", null, text, now))
            }
        }
        memoryDao.deleteForNovel(novelId)
        if (chunks.isNotEmpty()) memoryDao.upsertAll(chunks)
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

    private fun String.toProtocol(): ApiProtocol = ApiProtocol.entries.firstOrNull { it.name == this } ?: ApiProtocol.AUTO

    private fun grams(value: String): Set<String> {
        val normalized = value.lowercase().filter { it.isLetterOrDigit() }
        if (normalized.isBlank()) return emptySet()
        if (normalized.length == 1) return setOf(normalized)
        return normalized.windowed(size = 2, step = 1).toSet()
    }
}
