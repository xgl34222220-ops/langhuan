package com.xiguli.langhuan.data

import android.content.Context
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val ProjectJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

data class StoryShelfItem(
    val id: String,
    val title: String,
    val genre: String,
    val currentWords: Int,
    val targetWords: Int,
    val currentChapter: Int,
    val status: NovelStatus,
    val updatedAt: Long,
)

data class NewStoryRequest(
    val title: String,
    val genre: String,
    val premise: String,
    val theme: String,
    val targetWords: Int,
)

class StoryProjectManager(context: Context) {
    private val db = LanghuanDatabase.get(context)
    private val storyDao = db.storyStateDao()
    private val chapterStateDao = db.chapterStateDao()
    private val chapterVersionDao = db.chapterVersionDao()
    private val memoryDao = db.memoryChunkDao()
    private val preferences = context.applicationContext.getSharedPreferences(
        "langhuan_project_state",
        Context.MODE_PRIVATE,
    )

    fun observeStories(): Flow<List<StoryShelfItem>> = storyDao.observeAll().map { entities ->
        entities.mapNotNull { entity -> entity.toShelfItemOrNull() }
    }

    fun activeStoryId(): String? = preferences.getString(KEY_ACTIVE_STORY, null)

    fun setActiveStoryId(id: String) {
        preferences.edit().putString(KEY_ACTIVE_STORY, id).apply()
    }

    suspend fun loadStory(id: String): PersistedStory? {
        val entity = storyDao.get(id) ?: return null
        return runCatching {
            val storedSnapshot = ProjectJson.decodeFromString(StorySnapshot.serializer(), entity.snapshotJson)
            val fallbackDraft = ProjectJson.decodeFromString(ChapterDraft.serializer(), entity.draftJson)
            ensureChapterState(fallbackDraft, entity.updatedAt)
            val selected = chapterStateDao.get(id, storedSnapshot.novel.currentChapter)?.decodeDraftOrNull() ?: fallbackDraft
            val snapshot = normalizeSnapshot(storedSnapshot, selected.chapterNumber)
            if (snapshot != storedSnapshot || selected != fallbackDraft) {
                persistCurrent(snapshot, selected, System.currentTimeMillis())
            }
            PersistedStory(snapshot, selected)
        }.getOrNull()
    }

    suspend fun createStory(request: NewStoryRequest): PersistedStory {
        val id = UUID.randomUUID().toString()
        val title = request.title.trim().ifBlank { "未命名小说" }
        val premise = request.premise.trim().ifBlank { "围绕主人公的核心目标展开故事。" }
        val targetWords = request.targetWords.coerceIn(10_000, 5_000_000)
        val chapterObjective = "完成开篇钩子，建立主角当前目标，并推动故事进入核心冲突。"

        val master = OutlineNode(
            id = "master-$id",
            novelId = id,
            level = OutlineLevel.MASTER,
            order = 1,
            title = "总纲",
            objective = premise,
            conflict = "主人公必须为核心目标付出代价。",
            turningPoint = "核心矛盾逐步揭露，迫使主人公做出不可逆选择。",
        )
        val volume = OutlineNode(
            id = "volume-$id-1",
            novelId = id,
            parentId = master.id,
            level = OutlineLevel.VOLUME,
            order = 1,
            title = "第一卷",
            objective = "建立主要人物与规则，让主人公进入故事主线。",
            conflict = "主人公的初始目标受到现实阻碍。",
            turningPoint = "主人公发现问题比预想更深，并主动进入下一阶段。",
        )
        val chapter = OutlineNode(
            id = "chapter-$id-1",
            novelId = id,
            parentId = volume.id,
            level = OutlineLevel.CHAPTER,
            order = 1,
            title = "第一章",
            objective = chapterObjective,
            conflict = "主人公的行动第一次遭遇阻碍。",
            turningPoint = "章末出现一个足以推动下一章的新信息或选择。",
        )
        val novel = Novel(
            id = id,
            title = title,
            genre = request.genre.trim().ifBlank { "未分类" },
            premise = premise,
            theme = request.theme.trim().ifBlank { "待完善" },
            targetWords = targetWords,
            currentWords = 0,
            currentChapter = 1,
            status = NovelStatus.WRITING,
        )
        val outline = listOf(master, volume, chapter)
        val snapshot = StorySnapshot(
            novel = novel,
            activeOutline = outline,
            bible = emptyList(),
            characters = emptyList(),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
            longTermSummary = "",
            outline = outline,
        )
        val draft = defaultDraft(id, chapter)
        val persisted = saveStructure(snapshot, draft)
        setActiveStoryId(id)
        return persisted
    }

    suspend fun createImportedStory(manuscript: ImportedManuscript): PersistedStory {
        val created = createStory(
            NewStoryRequest(
                title = manuscript.title,
                genre = "导入作品",
                premise = "从外部稿件导入，待补充核心命题与完整大纲。",
                theme = "待完善",
                targetWords = maxOf(50_000, manuscript.chapters.sumOf { it.content.length } * 2),
            )
        )
        val base = created.snapshot
        val full = effectiveOutline(base).toMutableList()
        val volume = full.first { it.level == OutlineLevel.VOLUME }
        full.removeAll { it.level == OutlineLevel.CHAPTER }
        val chapters = manuscript.chapters.ifEmpty { listOf(ImportedChapter("第一章", "")) }
            .mapIndexed { index, item ->
                val number = index + 1
                val node = OutlineNode(
                    id = "chapter-${base.novel.id}-$number",
                    novelId = base.novel.id,
                    parentId = volume.id,
                    level = OutlineLevel.CHAPTER,
                    order = number,
                    title = item.title.ifBlank { "第${number}章" },
                    objective = "梳理导入正文后补充本章目标。",
                    conflict = "待从正文提取冲突。",
                    turningPoint = "待从正文提取转折。",
                    locked = false,
                )
                full += node
                ChapterDraft(
                    id = "draft-${base.novel.id}-$number",
                    novelId = base.novel.id,
                    chapterNumber = number,
                    title = node.title,
                    objective = node.objective,
                    scenePlan = listOf(defaultScene(number)),
                    content = item.content,
                    version = 1,
                )
            }
        val now = System.currentTimeMillis()
        chapters.forEach { draft ->
            chapterStateDao.upsert(draft.toEntity(now))
            chapterVersionDao.upsert(
                ChapterVersionEntity(
                    id = "${draft.id}:v1",
                    novelId = draft.novelId,
                    chapterNumber = draft.chapterNumber,
                    version = 1,
                    title = draft.title,
                    content = draft.content,
                    summary = draft.summary,
                    createdAt = now,
                )
            )
            upsertChapterMemory(draft, now)
        }
        val first = chapters.first()
        val snapshot = base.copy(
            novel = base.novel.copy(
                currentWords = chapters.sumOf { it.content.length },
                currentChapter = 1,
            ),
            outline = full.sortedWith(compareBy({ it.level.ordinal }, { it.order })),
            activeOutline = activeChain(full, 1),
        )
        val persisted = saveStructure(snapshot, first)
        setActiveStoryId(snapshot.novel.id)
        return persisted
    }

    suspend fun chapterDrafts(novelId: String): List<ChapterDraft> {
        val loaded = loadStory(novelId) ?: return emptyList()
        val existing = chapterStateDao.allForNovel(novelId).mapNotNull { it.decodeDraftOrNull() }.associateBy { it.chapterNumber }.toMutableMap()
        val nodes = effectiveOutline(loaded.snapshot).filter { it.level == OutlineLevel.CHAPTER }.sortedBy { it.order }
        val now = System.currentTimeMillis()
        nodes.forEach { node ->
            if (existing[node.order] == null) {
                val draft = defaultDraft(novelId, node)
                chapterStateDao.upsert(draft.toEntity(now))
                existing[node.order] = draft
            }
        }
        if (existing.isEmpty()) {
            chapterStateDao.upsert(loaded.draft.toEntity(now))
            existing[loaded.draft.chapterNumber] = loaded.draft
        }
        return existing.values.sortedBy { it.chapterNumber }
    }

    suspend fun selectChapter(novelId: String, chapterNumber: Int): PersistedStory? {
        val loaded = loadStory(novelId) ?: return null
        val draft = chapterDrafts(novelId).firstOrNull { it.chapterNumber == chapterNumber } ?: return null
        val snapshot = loaded.snapshot.copy(
            novel = loaded.snapshot.novel.copy(currentChapter = chapterNumber),
            activeOutline = activeChain(effectiveOutline(loaded.snapshot), chapterNumber),
            outline = effectiveOutline(loaded.snapshot),
        )
        return saveStructure(snapshot, draft)
    }

    suspend fun createChapter(
        snapshot: StorySnapshot,
        title: String,
        objective: String,
        conflict: String,
        turningPoint: String,
        scenePlan: List<ScenePlan> = emptyList(),
    ): PersistedStory {
        val outline = effectiveOutline(snapshot).toMutableList()
        val nextNumber = (outline.filter { it.level == OutlineLevel.CHAPTER }.maxOfOrNull { it.order } ?: 0) + 1
        val volume = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.VOLUME }
            ?: outline.filter { it.level == OutlineLevel.VOLUME }.maxByOrNull { it.order }
            ?: error("请先创建卷纲")
        val node = OutlineNode(
            id = "chapter-${snapshot.novel.id}-$nextNumber-${UUID.randomUUID()}",
            novelId = snapshot.novel.id,
            parentId = volume.id,
            level = OutlineLevel.CHAPTER,
            order = nextNumber,
            title = title.trim().ifBlank { "第${nextNumber}章" },
            objective = objective.trim().ifBlank { "推动当前主线并制造新的选择。" },
            conflict = conflict.trim().ifBlank { "人物目标遭遇阻碍。" },
            turningPoint = turningPoint.trim().ifBlank { "章末出现新的信息、代价或选择。" },
        )
        outline += node
        val draft = ChapterDraft(
            id = "draft-${snapshot.novel.id}-$nextNumber",
            novelId = snapshot.novel.id,
            chapterNumber = nextNumber,
            title = node.title,
            objective = node.objective,
            scenePlan = scenePlan.ifEmpty { listOf(defaultScene(nextNumber)) },
        )
        val updated = snapshot.copy(
            novel = snapshot.novel.copy(currentChapter = nextNumber),
            outline = outline,
            activeOutline = activeChain(outline, nextNumber),
        )
        return saveStructure(updated, draft)
    }

    suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
        val now = System.currentTimeMillis()
        val normalized = normalizeSnapshot(snapshot, draft.chapterNumber)
        persistCurrent(normalized, draft, now)
        rebuildStructuredMemory(normalized, now)
        return PersistedStory(normalized, draft)
    }

    suspend fun exportStory(novelId: String, format: ExportFormat): ExportArtifact {
        val loaded = loadStory(novelId) ?: error("找不到当前小说")
        return StoryExchange.export(loaded.snapshot, chapterDrafts(novelId), format)
    }

    private suspend fun persistCurrent(snapshot: StorySnapshot, draft: ChapterDraft, now: Long) {
        storyDao.upsert(
            StoryStateEntity(
                novelId = snapshot.novel.id,
                snapshotJson = ProjectJson.encodeToString(StorySnapshot.serializer(), snapshot),
                draftJson = ProjectJson.encodeToString(ChapterDraft.serializer(), draft),
                updatedAt = now,
            )
        )
        chapterStateDao.upsert(draft.toEntity(now))
    }

    private suspend fun ensureChapterState(draft: ChapterDraft, now: Long) {
        if (chapterStateDao.get(draft.novelId, draft.chapterNumber) == null) {
            chapterStateDao.upsert(draft.toEntity(now))
        }
    }

    private suspend fun rebuildStructuredMemory(snapshot: StorySnapshot, now: Long) {
        val novelId = snapshot.novel.id
        val chunks = buildList {
            snapshot.bible.forEach { item ->
                add(MemoryChunkEntity("bible:${item.id}", novelId, "BIBLE", item.id, null, "${item.name}：${item.content}", now))
            }
            snapshot.characters.forEach { item ->
                add(
                    MemoryChunkEntity(
                        id = "character:${item.id}",
                        novelId = novelId,
                        sourceType = "CHARACTER",
                        sourceId = item.id,
                        chapterNumber = item.lastUpdatedChapter,
                        text = "${item.name}。性格：${item.personality.joinToString("、")}。地点：${item.location}。身体：${item.physicalState}。情绪：${item.emotionalState}。目标：${item.goal}。关系：${item.relationshipNotes.entries.joinToString("；") { "${it.key}=${it.value}" }}。已知秘密：${item.knownSecrets.joinToString("、")}。持有：${item.possessions.joinToString("、")}。",
                        updatedAt = now,
                    )
                )
            }
            snapshot.recentTimeline.forEach { item ->
                add(
                    MemoryChunkEntity(
                        "timeline:${item.id}", novelId, "TIMELINE", item.id, item.chapter,
                        "第${item.chapter}章 ${item.storyTime} ${item.location}：${item.summary} 后果：${item.consequences.joinToString("、")}",
                        now,
                    )
                )
            }
            snapshot.relevantForeshadowing.forEach { item ->
                add(
                    MemoryChunkEntity(
                        "foreshadow:${item.id}", novelId, "FORESHADOW", item.id, item.plantedChapter,
                        "伏笔「${item.title}」：${item.detail}；预计回收：${item.expectedPayoff}；状态：${item.status.name}",
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
        }
        memoryDao.deleteStructuredForNovel(novelId)
        if (chunks.isNotEmpty()) memoryDao.upsertAll(chunks)
    }

    private suspend fun upsertChapterMemory(draft: ChapterDraft, now: Long) {
        memoryDao.upsert(
            MemoryChunkEntity(
                id = "chapter:${draft.id}:v${draft.version}",
                novelId = draft.novelId,
                sourceType = "CHAPTER",
                sourceId = draft.id,
                chapterNumber = draft.chapterNumber,
                text = "第${draft.chapterNumber}章 ${draft.title}。${draft.summary}\n${draft.content.take(4_000)}",
                updatedAt = now,
            )
        )
    }

    private fun normalizeSnapshot(snapshot: StorySnapshot, chapterNumber: Int): StorySnapshot {
        val full = effectiveOutline(snapshot)
        return snapshot.copy(
            novel = snapshot.novel.copy(currentChapter = chapterNumber),
            outline = full,
            activeOutline = activeChain(full, chapterNumber).ifEmpty { snapshot.activeOutline },
        )
    }

    private fun effectiveOutline(snapshot: StorySnapshot): List<OutlineNode> =
        (if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline).distinctBy { it.id }

    private fun activeChain(nodes: List<OutlineNode>, chapterNumber: Int): List<OutlineNode> {
        val chapter = nodes.filter { it.level == OutlineLevel.CHAPTER }.firstOrNull { it.order == chapterNumber } ?: return emptyList()
        val volume = nodes.firstOrNull { it.id == chapter.parentId }
        val master = volume?.parentId?.let { id -> nodes.firstOrNull { it.id == id } }
            ?: nodes.firstOrNull { it.level == OutlineLevel.MASTER }
        return listOfNotNull(master, volume, chapter)
    }

    private fun defaultDraft(novelId: String, node: OutlineNode): ChapterDraft = ChapterDraft(
        id = "draft-$novelId-${node.order}",
        novelId = novelId,
        chapterNumber = node.order,
        title = node.title,
        objective = node.objective,
        scenePlan = listOf(defaultScene(node.order)),
    )

    private fun defaultScene(chapterNumber: Int) = ScenePlan(
        order = 1,
        viewpoint = "主角",
        location = "第${chapterNumber}章主要场景",
        purpose = "完成本章目标并推动主线",
        conflict = "人物目标受到具体阻碍",
        outcome = "形成新的信息、代价或选择",
    )

    private fun ChapterDraft.toEntity(now: Long) = ChapterStateEntity(
        id = id,
        novelId = novelId,
        chapterNumber = chapterNumber,
        draftJson = ProjectJson.encodeToString(ChapterDraft.serializer(), this),
        updatedAt = now,
    )

    private fun ChapterStateEntity.decodeDraftOrNull(): ChapterDraft? = runCatching {
        ProjectJson.decodeFromString(ChapterDraft.serializer(), draftJson)
    }.getOrNull()

    private fun StoryStateEntity.toShelfItemOrNull(): StoryShelfItem? = runCatching {
        val snapshot = ProjectJson.decodeFromString(StorySnapshot.serializer(), snapshotJson)
        StoryShelfItem(
            id = novelId,
            title = snapshot.novel.title,
            genre = snapshot.novel.genre,
            currentWords = snapshot.novel.currentWords,
            targetWords = snapshot.novel.targetWords,
            currentChapter = snapshot.novel.currentChapter,
            status = snapshot.novel.status,
            updatedAt = updatedAt,
        )
    }.getOrNull()

    companion object {
        private const val KEY_ACTIVE_STORY = "active_story_id"
    }
}
