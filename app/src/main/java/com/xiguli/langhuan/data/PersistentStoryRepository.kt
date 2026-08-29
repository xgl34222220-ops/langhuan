package com.xiguli.langhuan.data

import android.content.Context
import com.xiguli.langhuan.data.local.AiProviderEntity
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.data.local.SecureApiKeyStore
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.ApiProtocol
import com.xiguli.langhuan.engine.GenerationContextBuilder
import com.xiguli.langhuan.engine.HybridMemoryRetriever
import com.xiguli.langhuan.engine.LongFormContinuityEngine
import com.xiguli.langhuan.engine.MemoryCandidate
import com.xiguli.langhuan.engine.RetrievedContextItem
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
    private val chapterStateDao = db.chapterStateDao()
    private val providerDao = db.aiProviderDao()
    private val memoryDao = db.memoryChunkDao()
    private val keyStore = SecureApiKeyStore(context)
    private val memoryRetriever = HybridMemoryRetriever()
    private val longFormEngine = LongFormContinuityEngine()

    suspend fun seedIfNeeded(demo: DemoStoryRepository) {
        if (storyDao.get(demo.snapshot.novel.id) != null) return
        val now = System.currentTimeMillis()
        persistStory(demo.snapshot, demo.currentDraft, now)
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
        runId: String = "",
    ): PersistedStory {
        val existingDraft = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraftOrNull()
        if (runId.isNotBlank() && existingDraft?.lastCommittedRunId == runId) {
            val storedSnapshot = storyDao.get(draft.novelId)?.let { entity ->
                runCatching { StoreJson.decodeFromString(StorySnapshot.serializer(), entity.snapshotJson) }.getOrNull()
            } ?: snapshot
            return PersistedStory(storedSnapshot, existingDraft)
        }
        val now = System.currentTimeMillis()
        val latestVersion = chapterDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version
        val newVersion = maxOf(draft.version, latestVersion) + 1
        val newDraft = draft.copy(
            title = generated.title.ifBlank { draft.title },
            content = generated.content,
            summary = generated.summary,
            version = newVersion,
            lastCommittedRunId = runId.ifBlank { draft.lastCommittedRunId },
        )
        val previous = existingDraft ?: draft
        val wordDelta = generated.content.length - previous.content.length
        // Generated metadata is untrusted extraction. Do not let stateChanges mutate Canon here.
        // Character/knowledge/timeline/foreshadow facts must travel Agent -> Candidate -> Canon.
        val chapterSummary = "第${draft.chapterNumber}章：${generated.summary}".trim()
        val summaryHistory = (snapshot.recentSummaries + chapterSummary)
            .filter { it.isNotBlank() }
            .distinct()
        val hotWindow = snapshot.longForm.config.hotChapterWindow.coerceIn(5, 14)
        val foldCount = (summaryHistory.size - hotWindow).coerceAtLeast(0)
        val folded = summaryHistory.take(foldCount)
        val baseSnapshot = snapshot.copy(
            novel = snapshot.novel.copy(
                currentWords = (snapshot.novel.currentWords + wordDelta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            ),
            recentSummaries = summaryHistory.takeLast(hotWindow),
            longTermSummary = foldLongTermSummary(snapshot.longTermSummary, folded),
        )
        val safeGenerated = generated.copy(stateChanges = emptyList())
        val newSnapshot = longFormEngine.settle(baseSnapshot, newDraft, safeGenerated)
        persistStory(newSnapshot, newDraft, now)
        saveChapterVersion(newDraft, now)
        rebuildMemoryIndex(newSnapshot)
        upsertChapterMemory(newDraft, now)
        return PersistedStory(newSnapshot, newDraft)
    }

    suspend fun saveDraft(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
        val now = System.currentTimeMillis()
        val oldDraft = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraftOrNull() ?: draft
        val latestVersion = chapterDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version
        val versionedDraft = draft.copy(version = maxOf(draft.version, latestVersion) + 1)
        val delta = versionedDraft.content.length - oldDraft.content.length
        val updatedSnapshot = snapshot.copy(
            novel = snapshot.novel.copy(
                currentWords = (snapshot.novel.currentWords + delta).coerceAtLeast(0),
                currentChapter = draft.chapterNumber,
            ),
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

    suspend fun retrieveRelevantContext(
        novelId: String,
        query: String,
        currentChapter: Int,
        limit: Int = 10,
    ): List<RetrievedContextItem> {
        val candidates = memoryDao.recent(novelId, 1_600)
            .asSequence()
            .filterNot { it.text.contains(GenerationContextBuilder.CREATION_FACT_LEDGER) }
            .map {
                MemoryCandidate(
                    text = it.text,
                    sourceType = it.sourceType,
                    sourceId = it.sourceId,
                    chapterNumber = it.chapterNumber,
                    updatedAt = it.updatedAt,
                )
            }
            .toList()
        return memoryRetriever.rank(query, candidates, currentChapter, limit).map { hit ->
            RetrievedContextItem(
                sourceType = hit.candidate.sourceType,
                sourceId = hit.candidate.sourceId,
                chapterNumber = hit.candidate.chapterNumber,
                text = hit.candidate.text,
                score = hit.score,
                reasons = hit.reasons,
            )
        }
    }

    /** 兼容旧调用；新正文生成应使用 retrieveRelevantContext 获取可解释召回。 */
    suspend fun retrieveRelevantMemories(
        novelId: String,
        query: String,
        currentChapter: Int,
        limit: Int = 8,
    ): List<String> = retrieveRelevantContext(novelId, query, currentChapter, limit)
        .map { hit -> "[${hit.sourceType}] ${hit.text}" }

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
        chapterStateDao.upsert(
            ChapterStateEntity(
                id = draft.id,
                novelId = draft.novelId,
                chapterNumber = draft.chapterNumber,
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
            snapshot.bible
                .filterNot { it.name == GenerationContextBuilder.CREATION_FACT_LEDGER }
                .forEach { item ->
                    add(MemoryChunkEntity("bible:${item.id}", novelId, "BIBLE", item.id, null, "${item.name}：${item.content}", now))
                }
            snapshot.characters.forEach { item ->
                add(
                    MemoryChunkEntity(
                        "character:${item.id}", novelId, "CHARACTER", item.id, item.lastUpdatedChapter,
                        "${item.name}。性格：${item.personality.joinToString("、")}。地点：${item.location}。身体：${item.physicalState}。情绪：${item.emotionalState}。目标：${item.goal}。关系：${item.relationshipNotes.entries.joinToString("；") { "${it.key}=${it.value}" }}。已知秘密：${item.knownSecrets.joinToString("、")}。持有：${item.possessions.joinToString("、")}。",
                        now,
                    )
                )
            }
            snapshot.recentTimeline.forEach { item ->
                add(MemoryChunkEntity("timeline:${item.id}", novelId, "TIMELINE", item.id, item.chapter, "第${item.chapter}章 ${item.storyTime} ${item.location}：${item.summary} 后果：${item.consequences.joinToString("、")}", now))
            }
            snapshot.relevantForeshadowing.forEach { item ->
                // expectedPayoff 是作者层真相，不能作为普通 RAG 文本暴露给正文作者。
                add(
                    MemoryChunkEntity(
                        "foreshadow:${item.id}", novelId, "FORESHADOW", item.id, item.plantedChapter,
                        "伏笔「${item.title}」：${item.detail}；回收窗口：第${item.expectedChapterStart}-${item.expectedChapterEnd}章；状态：${item.status.name}",
                        now,
                    )
                )
            }
            if (snapshot.longTermSummary.isNotBlank()) {
                add(MemoryChunkEntity("long-summary:$novelId", novelId, "LONG_SUMMARY", "long-summary", null, snapshot.longTermSummary, now))
            }
            snapshot.recentSummaries.forEachIndexed { index, text ->
                add(MemoryChunkEntity("summary:${novelId}:$index", novelId, "SUMMARY", "summary-$index", null, text, now))
            }
            snapshot.longForm.arcs.forEach { arc ->
                // 这里只索引当前弧状态；预期收束/未来底牌不进入正文 RAG。
                add(
                    MemoryChunkEntity(
                        id = "arc:${arc.id}",
                        novelId = novelId,
                        sourceType = "ARC",
                        sourceId = arc.id,
                        chapterNumber = arc.lastUpdatedChapter.takeIf { it > 0 },
                        text = "剧情弧「${arc.title}」第${arc.startChapter}-${arc.plannedEndChapter}章，阶段=${arc.phase}。当前目标：${arc.objective}。当前核心冲突：${arc.centralConflict}。",
                        updatedAt = now,
                    )
                )
            }
            snapshot.longForm.mediumMemories.forEach { memory ->
                add(
                    MemoryChunkEntity(
                        id = "medium:$novelId:${memory.startChapter}",
                        novelId = novelId,
                        sourceType = "MEDIUM",
                        sourceId = "${memory.startChapter}-${memory.endChapter}",
                        chapterNumber = memory.endChapter,
                        text = "第${memory.startChapter}-${memory.endChapter}章中期记忆：${memory.summary}\n关键事实：${memory.keyFacts.joinToString("；")}",
                        updatedAt = memory.updatedAt.takeIf { it > 0 } ?: now,
                    )
                )
            }
            snapshot.longForm.characterGrowth.forEach { growth ->
                add(
                    MemoryChunkEntity(
                        id = "growth:${growth.characterId}",
                        novelId = novelId,
                        sourceType = "GROWTH",
                        sourceId = growth.characterId,
                        chapterNumber = growth.lastTurningChapter.takeIf { it > 0 },
                        text = "${growth.name}当前成长阶段=${growth.stage}。当前人格基线=${growth.currentBelief}。当前内部冲突=${growth.internalConflict}。已确认里程碑=${growth.milestones.joinToString("；")}。",
                        updatedAt = now,
                    )
                )
            }
        }
        memoryDao.deleteStructuredForNovel(novelId)
        if (chunks.isNotEmpty()) memoryDao.upsertAll(chunks)
    }

    private fun foldLongTermSummary(existing: String, folded: List<String>): String {
        if (folded.isEmpty()) return existing
        val additions = folded.joinToString("\n") { it.take(360) }
        val merged = listOf(existing.trim(), additions.trim()).filter { it.isNotBlank() }.joinToString("\n")
        if (merged.length <= 6_500) return merged
        return merged.take(1_500) + "\n……\n" + merged.takeLast(4_700)
    }

    private fun ChapterStateEntity.decodeDraftOrNull(): ChapterDraft? = runCatching {
        StoreJson.decodeFromString(ChapterDraft.serializer(), draftJson)
    }.getOrNull()

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
