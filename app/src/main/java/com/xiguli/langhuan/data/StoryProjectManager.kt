package com.xiguli.langhuan.data

import android.content.Context
import androidx.room.withTransaction
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.data.local.StoryStateEntity
import com.xiguli.langhuan.data.local.StoryStateHeader
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

internal data class ActiveStorySelectionV51(val id: String?, val revision: Long)

class StoryProjectManager internal constructor(context: Context, private val db: LanghuanDatabase) {
    constructor(context: Context) : this(context, LanghuanDatabase.get(context))
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

    fun activeStoryId(): String? = activeStorySelectionV51().id

    internal fun activeStorySelectionV51(): ActiveStorySelectionV51 = synchronized(ACTIVE_STORY_LOCK) {
        ActiveStorySelectionV51(preferences.getString(KEY_ACTIVE_STORY, null), activeStoryRevision)
    }

    fun setActiveStoryId(id: String) = synchronized(ACTIVE_STORY_LOCK) {
        preferences.edit().putString(KEY_ACTIVE_STORY, id).apply()
        activeStoryRevision += 1
    }

    fun clearActiveStoryId() = synchronized(ACTIVE_STORY_LOCK) {
        preferences.edit().remove(KEY_ACTIVE_STORY).apply()
        activeStoryRevision += 1
    }

    /** Publish only if another project selection has not superseded this restore request. */
    internal fun publishRestoredStory(expected: ActiveStorySelectionV51, restoredId: String): Result<Boolean> =
        synchronized(ACTIVE_STORY_LOCK) {
            runCatching {
                require(restoredId.isNotBlank()) { "恢复项目标识无效" }
                val previous = preferences.getString(KEY_ACTIVE_STORY, null)
                if (previous != expected.id || activeStoryRevision != expected.revision) return@runCatching false
                val saved = runCatching {
                    check(preferences.edit().putString(KEY_ACTIVE_STORY, restoredId).commit()) {
                        "项目已恢复，但无法保存当前项目"
                    }
                }
                saved.exceptionOrNull()?.let { failure ->
                    // SharedPreferences changes its memory view even when committing to disk fails.
                    runCatching {
                        val rollback = preferences.edit()
                        if (previous == null) rollback.remove(KEY_ACTIVE_STORY)
                        else rollback.putString(KEY_ACTIVE_STORY, previous)
                        rollback.commit()
                    }
                    throw failure
                }
                activeStoryRevision += 1
                true
            }
        }

    suspend fun loadStory(id: String): PersistedStory? {
        val header = storyDao.getHeader(id) ?: return null
        return runCatching {
            val storedSnapshot = ProjectJson.decodeFromString(StorySnapshot.serializer(), header.snapshotJson)
            val fallbackDraft = loadStoryDraftCursorSafe(id) ?: return@runCatching null
            ensureChapterState(fallbackDraft, header.updatedAt)
            val selected = loadChapterDraftCursorSafe(id, storedSnapshot.novel.currentChapter) ?: fallbackDraft
            val snapshot = normalizeSnapshot(storedSnapshot, selected.chapterNumber)
            if (snapshot != storedSnapshot || selected != fallbackDraft) {
                persistCurrent(snapshot, selected, System.currentTimeMillis())
            }
            PersistedStory(snapshot, selected)
        }.getOrNull()
    }

    suspend fun createStory(request: NewStoryRequest): PersistedStory = createStory(request, selectActive = true)

    private suspend fun createStory(request: NewStoryRequest, selectActive: Boolean): PersistedStory {
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
        if (selectActive) setActiveStoryId(id)
        return persisted
    }

    suspend fun createImportedStory(
        manuscript: ImportedManuscript,
        beforeCommit: (PersistedStory) -> Unit = {},
    ): PersistedStory = db.withTransaction {
        if (manuscript.sourceId.isNotBlank()) {
            require(manuscript.sourceBookUrl.isNotBlank() && manuscript.chapters.isNotEmpty()) { "在线书籍缺少来源或目录" }
            require(manuscript.chapters.all { it.sourceUrl.isNotBlank() } && manuscript.chapters.map { it.sourceUrl }.distinct().size == manuscript.chapters.size) { "在线目录包含空地址或重复章节" }
            findOnlineStory(manuscript.sourceId, manuscript.sourceBookUrl)?.let { id ->
                loadStory(id)?.let { return@withTransaction it }
            }
        }
        createImportedStoryInTransaction(manuscript).also(beforeCommit)
    }

    private suspend fun createImportedStoryInTransaction(manuscript: ImportedManuscript): PersistedStory {
        // Build a reading import without publishing a temporary active project.
        // A concurrent explicit Studio selection must survive both success and rollback.
        return run {
            val created = createStory(
                NewStoryRequest(
                    title = manuscript.title,
                    genre = if (manuscript.sourceId.isBlank()) "导入作品" else "网络小说",
                    premise = manuscript.intro.ifBlank { "从外部稿件导入，待补充核心命题与完整大纲。" },
                    theme = "待完善",
                    targetWords = (manuscript.chapters.sumOf { it.content.length.toLong() } * 2)
                        .coerceIn(50_000L, 5_000_000L).toInt(),
                ),
                selectActive = false,
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
                        sourceUrl = item.sourceUrl,
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
                    sourceId = manuscript.sourceId,
                    sourceBookUrl = manuscript.sourceBookUrl,
                ),
                outline = full.sortedWith(compareBy({ it.level.ordinal }, { it.order })),
                activeOutline = activeChain(full, 1),
            )
            saveStructure(snapshot, first)
        }
    }
    /** Appends downloaded chapters (online-source updates) after the current last chapter. */
    suspend fun appendImportedChapters(novelId: String, items: List<ImportedChapter>): PersistedStory? = db.withTransaction {
        appendImportedChaptersInTransaction(novelId, items)
    }

    private suspend fun appendImportedChaptersInTransaction(novelId: String, items: List<ImportedChapter>): PersistedStory? {
        if (items.isEmpty()) return null
        val loaded = loadStory(novelId) ?: return null
        val full = effectiveOutline(loaded.snapshot).toMutableList()
        val volume = full.lastOrNull { it.level == OutlineLevel.VOLUME } ?: error("请先创建卷纲")
        var number = full.filter { it.level == OutlineLevel.CHAPTER }.maxOfOrNull { it.order } ?: 0
        val now = System.currentTimeMillis()
        val drafts = items.map { item ->
            number += 1
            val node = OutlineNode(
                id = "chapter-$novelId-$number-${UUID.randomUUID()}",
                novelId = novelId,
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
                id = "draft-$novelId-$number",
                novelId = novelId,
                chapterNumber = number,
                title = node.title,
                objective = node.objective,
                scenePlan = listOf(defaultScene(number)),
                content = item.content,
                sourceUrl = item.sourceUrl,
                version = 1,
            )
        }
        drafts.forEach { draft ->
            chapterStateDao.upsert(draft.toEntity(now))
            upsertChapterMemory(draft, now)
        }
        val current = loaded.snapshot.novel.currentChapter
        val snapshot = loaded.snapshot.copy(
            novel = loaded.snapshot.novel.copy(
                currentWords = loaded.snapshot.novel.currentWords + drafts.sumOf { it.content.length },
            ),
            outline = full.sortedWith(compareBy({ it.level.ordinal }, { it.order })),
            activeOutline = activeChain(full, current),
        )
        return saveStructure(snapshot, loaded.draft)
    }

    /** Online shelf identity is in the same transaction as its catalogue, not only preferences. */
    suspend fun findOnlineStory(sourceId: String, bookUrl: String): String? =
        storyDao.allHeaders().firstNotNullOfOrNull { row ->
            runCatching { ProjectJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson).novel }
                .getOrNull()?.takeIf { it.sourceId == sourceId && it.sourceBookUrl == bookUrl }?.id
        }

    /** Cache exactly one verified URL without overwriting existing text or changing chapter order. */
    suspend fun cacheOnlineChapter(novelId: String, number: Int, chapterId: String, sourceUrl: String, text: String,
        expectedSourceId: String, expectedBookUrl: String): ChapterDraft? = db.withTransaction {
        require(text.isNotBlank()) { "本章正文为空，未写入缓存" }
        val loaded = loadStory(novelId) ?: return@withTransaction null
        check(loaded.snapshot.novel.sourceId == expectedSourceId && loaded.snapshot.novel.sourceBookUrl == expectedBookUrl) { "书籍来源已变化，未保存旧响应" }
        val chapter = loadChapterDraftCursorSafe(novelId, number) ?: return@withTransaction null
        check(chapter.id == chapterId && chapter.sourceUrl.isNotBlank() && chapter.sourceUrl == sourceUrl) { "章节来源已变化，未覆盖原正文" }
        if (chapter.content.isNotBlank()) return@withTransaction chapter
        val cached = chapter.copy(content = text)
        val now = System.currentTimeMillis()
        chapterStateDao.upsert(cached.toEntity(now))
        // The imported placeholder is not a meaningful old revision. Keep its first version
        // consistent with the first successfully cached text, so restoring v1 cannot erase it.
        chapterVersionDao.upsert(ChapterVersionEntity("${cached.id}:v${cached.version}", novelId, number,
            cached.version, cached.title, cached.content, cached.summary, now))
        val snapshot = loaded.snapshot.copy(novel = loaded.snapshot.novel.copy(currentWords = loaded.snapshot.novel.currentWords + text.length))
        persistCurrent(snapshot, if (loaded.draft.chapterNumber == number) cached else loaded.draft, now)
        cached
    }

    suspend fun chapterDraft(novelId: String, number: Int): ChapterDraft? = loadChapterDraftCursorSafe(novelId, number)

    /** Reading imports are reference material; AI writing receives a separate local project. */
    suspend fun createWritingCopy(novelId: String, number: Int, expectedChapterId: String): PersistedStory = db.withTransaction {
        val original = loadStory(novelId) ?: error("原书已移除")
        val chapter = loadChapterDraftCursorSafe(novelId, number) ?: error("当前章节已移除")
        check(chapter.id == expectedChapterId) { "当前章节已变化，请重新进入创作" }
        require(chapter.content.isNotBlank()) { "请先读取当前章节正文，再创建创作副本" }
        val copy = createImportedStoryInTransaction(ImportedManuscript(
            title = original.snapshot.novel.title + " · 创作副本",
            chapters = listOf(ImportedChapter(chapter.title, chapter.content)),
            intro = "以《${original.snapshot.novel.title}》的《${chapter.title}》为起点，建立独立创作。" + chapter.summary.take(1200),
        ))
        saveStructure(copy.snapshot.copy(novel = copy.snapshot.novel.copy(genre = "创作副本")), copy.draft)
    }

    suspend fun appendOnlineCatalogue(novelId: String, sourceId: String, bookUrl: String, items: List<ImportedChapter>,
        beforeCommit: suspend () -> Unit = {}): Int = db.withTransaction {
        val novel = loadStory(novelId)?.snapshot?.novel ?: error("书籍已移除")
        check(novel.sourceId == sourceId && novel.sourceBookUrl == bookUrl) { "书籍来源已变化，未更新目录" }
        val existing = chapterDrafts(novelId)
        val plan = planOnlineCatalogueV53(existing, items)
        if (plan.added.isEmpty()) return@withTransaction 0
        // Allocate only new stable chapter keys. Existing chapter IDs, bodies, versions,
        // annotations and progress continue to refer to exactly the same chapters.
        appendImportedChaptersInTransaction(novelId, plan.added)
        val allocated = chapterDrafts(novelId).associateBy { it.sourceUrl }
        val ordered = plan.items.mapIndexed { index, item ->
            allocated.getValue(item.sourceUrl).copy(readingOrder = index + 1)
        }
        val now = System.currentTimeMillis()
        ordered.forEach { chapterStateDao.upsert(it.toEntity(now)) }
        val updated = loadStory(novelId) ?: error("书籍已移除")
        val current = ordered.single { it.chapterNumber == updated.draft.chapterNumber }
        persistCurrent(updated.snapshot, current, now)
        beforeCommit()
        plan.added.size
    }

    suspend fun chapterDrafts(novelId: String): List<ChapterDraft> {
        val loaded = loadStory(novelId) ?: return emptyList()

        // Do not SELECT * for the whole book: one giant imported chapter can exceed CursorWindow.
        val existing = linkedMapOf<Int, ChapterDraft>()
        chapterStateDao.chapterNumbers(novelId).forEach { number ->
            loadChapterDraftCursorSafe(novelId, number)?.let { existing[number] = it }
        }

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
        return existing.values.sortedBy { it.readingOrder }
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

    /** Renames one chapter in both the outline and its draft, using CursorWindow-safe reads. */
    suspend fun renameChapter(novelId: String, chapterNumber: Int, title: String): PersistedStory? {
        val clean = title.trim().take(40)
        if (clean.isBlank()) return null
        val loaded = loadStory(novelId) ?: return null
        val draft = loadChapterDraftCursorSafe(novelId, chapterNumber) ?: return null
        val renamed = draft.copy(title = clean)
        val now = System.currentTimeMillis()
        chapterStateDao.upsert(renamed.toEntity(now))
        upsertChapterMemory(renamed, now)
        val outline = effectiveOutline(loaded.snapshot).map { node ->
            if (node.level == OutlineLevel.CHAPTER && node.order == chapterNumber) node.copy(title = clean) else node
        }
        val current = loaded.snapshot.novel.currentChapter
        val snapshot = loaded.snapshot.copy(outline = outline, activeOutline = activeChain(outline, current))
        return saveStructure(snapshot, if (loaded.draft.chapterNumber == chapterNumber) renamed else loaded.draft)
    }

    /**
     * Deletes the last chapter only. Middle deletion would renumber memory/timeline/progress keys.
     * The draft is read through the chunked loader so even a very large final chapter is safe.
     */
    suspend fun deleteLastChapter(novelId: String): PersistedStory? = db.withTransaction {
        val loaded = loadStory(novelId) ?: return@withTransaction null
        val outline = effectiveOutline(loaded.snapshot)
        val chapters = chapterDrafts(novelId)
        check(chapters.size > 1) { "至少要保留一章" }
        val removedDraft = chapters.last()
        val last = outline.single { it.level == OutlineLevel.CHAPTER && it.order == removedDraft.chapterNumber }
        val remaining = outline.filterNot { it.id == last.id }
        chapterStateDao.delete(novelId, last.order)
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM chapter_versions WHERE novelId = ? AND chapterNumber = ?",
            arrayOf<Any?>(novelId, last.order),
        )
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM memory_chunks WHERE novelId = ? AND sourceType = 'CHAPTER' AND chapterNumber = ?",
            arrayOf<Any?>(novelId, last.order),
        )
        val current = if (loaded.snapshot.novel.currentChapter == last.order) chapters[chapters.lastIndex - 1].chapterNumber
            else loaded.snapshot.novel.currentChapter
        val draft = loadChapterDraftCursorSafe(novelId, current)
            ?: loaded.draft.takeIf { it.chapterNumber != last.order }
            ?: error("找不到可切换的章节")
        val snapshot = loaded.snapshot.copy(
            novel = loaded.snapshot.novel.copy(
                currentChapter = current,
                currentWords = (loaded.snapshot.novel.currentWords - removedDraft.content.length).coerceAtLeast(0),
            ),
            outline = remaining,
            activeOutline = activeChain(remaining, current),
        )
        saveStructure(snapshot, draft)
    }

    /** Appends an empty chapter after the last one. */
    suspend fun appendChapter(novelId: String, title: String): PersistedStory? {
        val loaded = loadStory(novelId) ?: return null
        return createChapter(loaded.snapshot, title, objective = "", conflict = "", turningPoint = "")
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
        if (chapterStateDao.draftJsonLength(draft.novelId, draft.chapterNumber) == null) {
            chapterStateDao.upsert(draft.toEntity(now))
        }
    }

    private suspend fun loadStoryDraftCursorSafe(novelId: String): ChapterDraft? {
        val totalChars = storyDao.draftJsonLength(novelId) ?: return null
        if (totalChars <= 0) return null

        val json = StringBuilder(totalChars.coerceAtMost(2_000_000))
        var start = 1
        while (start <= totalChars) {
            val chunk = storyDao.draftJsonChunk(
                novelId = novelId,
                start = start,
                length = DRAFT_JSON_CHUNK_CHARS,
            ) ?: return null
            if (chunk.isEmpty()) break
            json.append(chunk)
            start += DRAFT_JSON_CHUNK_CHARS
        }
        return runCatching {
            ProjectJson.decodeFromString(ChapterDraft.serializer(), json.toString())
        }.getOrNull()
    }

    /**
     * CursorWindow-safe chapter loader.
     *
     * SQLite's CursorWindow has a per-row capacity. Imported novels can contain a chapter whose
     * serialized draftJson is larger than that capacity, so selecting the whole row crashes before
     * Room can deserialize it. Reading substr() slices keeps every cursor row small.
     */
    private suspend fun loadChapterDraftCursorSafe(novelId: String, chapterNumber: Int): ChapterDraft? {
        val totalChars = chapterStateDao.draftJsonLength(novelId, chapterNumber) ?: return null
        if (totalChars <= 0) return null

        val json = StringBuilder(totalChars.coerceAtMost(2_000_000))
        var start = 1 // SQLite substr() is 1-based.
        while (start <= totalChars) {
            val chunk = chapterStateDao.draftJsonChunk(
                novelId = novelId,
                chapterNumber = chapterNumber,
                start = start,
                length = DRAFT_JSON_CHUNK_CHARS,
            ) ?: return null
            if (chunk.isEmpty()) break
            json.append(chunk)
            start += DRAFT_JSON_CHUNK_CHARS
        }
        return runCatching {
            ProjectJson.decodeFromString(ChapterDraft.serializer(), json.toString())
        }.getOrNull()
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

    private fun StoryStateHeader.toShelfItemOrNull(): StoryShelfItem? = runCatching {
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
        private val ACTIVE_STORY_LOCK = Any()
        private var activeStoryRevision = 0L
        private const val KEY_ACTIVE_STORY = "active_story_id"
        // Keep each SQLite substr() result comfortably below CursorWindow's per-row ceiling.
        private const val DRAFT_JSON_CHUNK_CHARS = 96 * 1024
    }
}
