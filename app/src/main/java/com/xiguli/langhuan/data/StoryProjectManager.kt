package com.xiguli.langhuan.data

import android.content.Context
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
            PersistedStory(
                snapshot = ProjectJson.decodeFromString(StorySnapshot.serializer(), entity.snapshotJson),
                draft = ProjectJson.decodeFromString(ChapterDraft.serializer(), entity.draftJson),
            )
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
        val snapshot = StorySnapshot(
            novel = novel,
            activeOutline = listOf(master, volume, chapter),
            bible = emptyList(),
            characters = emptyList(),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
            longTermSummary = "",
        )
        val draft = ChapterDraft(
            id = "draft-$id-1",
            novelId = id,
            chapterNumber = 1,
            title = "第一章",
            objective = chapterObjective,
            scenePlan = listOf(
                ScenePlan(
                    order = 1,
                    viewpoint = "主角",
                    location = "开篇场景",
                    purpose = "建立人物、环境和核心冲突",
                    conflict = "主人公的目标受到第一次阻碍",
                    outcome = "主人公做出进入故事主线的选择",
                )
            ),
        )
        val persisted = saveStructure(snapshot, draft)
        setActiveStoryId(id)
        return persisted
    }

    suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
        val now = System.currentTimeMillis()
        storyDao.upsert(
            StoryStateEntity(
                novelId = snapshot.novel.id,
                snapshotJson = ProjectJson.encodeToString(StorySnapshot.serializer(), snapshot),
                draftJson = ProjectJson.encodeToString(ChapterDraft.serializer(), draft),
                updatedAt = now,
            )
        )
        rebuildStructuredMemory(snapshot, now)
        return PersistedStory(snapshot, draft)
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
                        text = "${item.name}。性格：${item.personality.joinToString("、")}。地点：${item.location}。身体：${item.physicalState}。情绪：${item.emotionalState}。目标：${item.goal}。已知秘密：${item.knownSecrets.joinToString("、")}。持有：${item.possessions.joinToString("、")}。",
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
