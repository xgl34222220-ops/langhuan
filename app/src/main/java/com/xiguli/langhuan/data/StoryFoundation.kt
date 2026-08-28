package com.xiguli.langhuan.data

import android.content.Context
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.Foreshadowing
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class FoundationBibleItem(
    val category: BibleCategory,
    val name: String,
    val content: String,
    val aliases: List<String> = emptyList(),
    val locked: Boolean = true,
)

@Serializable
data class FoundationCharacter(
    val name: String,
    val personality: List<String>,
    val location: String,
    val physicalState: String,
    val emotionalState: String,
    val goal: String,
    val knownSecrets: List<String> = emptyList(),
    val possessions: List<String> = emptyList(),
    val relationships: Map<String, String> = emptyMap(),
)

@Serializable
data class FoundationChapter(
    val order: Int,
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
)

@Serializable
data class FoundationVolume(
    val order: Int,
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
    val chapters: List<FoundationChapter> = emptyList(),
)

@Serializable
data class FoundationForeshadow(
    val title: String,
    val detail: String,
    val expectedPayoff: String,
    val expectedChapterStart: Int,
    val expectedChapterEnd: Int,
)

@Serializable
data class StoryFoundation(
    val title: String,
    val genre: String,
    val premise: String,
    val theme: String,
    val targetWords: Int,
    val coreHook: String,
    val storyPromise: String,
    val styleGuide: String,
    val coverBrief: String,
    val masterTitle: String,
    val masterObjective: String,
    val masterConflict: String,
    val masterTurningPoint: String,
    val bible: List<FoundationBibleItem>,
    val characters: List<FoundationCharacter>,
    val volumes: List<FoundationVolume>,
    val foreshadowing: List<FoundationForeshadow>,
)

class StoryFoundationApplier(context: Context) {
    private val projects = StoryProjectManager(context)

    suspend fun create(foundation: StoryFoundation): PersistedStory {
        val base = projects.createStory(
            NewStoryRequest(
                title = foundation.title,
                genre = foundation.genre,
                premise = foundation.premise,
                theme = foundation.theme,
                targetWords = foundation.targetWords,
            )
        )
        val novelId = base.snapshot.novel.id

        val master = OutlineNode(
            id = "master-$novelId",
            novelId = novelId,
            level = OutlineLevel.MASTER,
            order = 1,
            title = foundation.masterTitle.ifBlank { "总纲" },
            objective = foundation.masterObjective.ifBlank { foundation.premise },
            conflict = foundation.masterConflict.ifBlank { "主人公的核心目标与必须承担的代价正面冲突。" },
            turningPoint = foundation.masterTurningPoint.ifBlank { "真相被重新定义，迫使主人公做出不可逆选择。" },
            mustInclude = listOfNotNull(foundation.coreHook.takeIf { it.isNotBlank() }),
        )

        val sourceVolumes = foundation.volumes.sortedBy { it.order }.ifEmpty {
            listOf(
                FoundationVolume(
                    order = 1,
                    title = "第一卷",
                    objective = "建立人物、规则与核心异常，让主角主动进入主线。",
                    conflict = "主角的现实目标与核心异常第一次正面碰撞。",
                    turningPoint = "主角获得无法忽视的新证据，并主动继续追查。",
                )
            )
        }
        val volumeNodes = sourceVolumes.mapIndexed { index, volume ->
            val order = index + 1
            OutlineNode(
                id = "volume-$novelId-$order",
                novelId = novelId,
                parentId = master.id,
                level = OutlineLevel.VOLUME,
                order = order,
                title = volume.title.ifBlank { "第${order}卷" },
                objective = volume.objective,
                conflict = volume.conflict,
                turningPoint = volume.turningPoint,
            )
        }

        val chapterNodes = mutableListOf<OutlineNode>()
        val chapterSeeds = mutableListOf<FoundationChapter>()
        var globalOrder = 1
        sourceVolumes.forEachIndexed { volumeIndex, volume ->
            val parent = volumeNodes[volumeIndex]
            volume.chapters.sortedBy { it.order }.forEach { seed ->
                chapterNodes += OutlineNode(
                    id = "chapter-$novelId-$globalOrder-${UUID.randomUUID()}",
                    novelId = novelId,
                    parentId = parent.id,
                    level = OutlineLevel.CHAPTER,
                    order = globalOrder,
                    title = seed.title.ifBlank { "第${globalOrder}章" },
                    objective = seed.objective.ifBlank { "推动当前主线并让人物做出新的选择。" },
                    conflict = seed.conflict.ifBlank { "人物目标遭遇具体阻碍。" },
                    turningPoint = seed.turningPoint.ifBlank { "章末出现新的信息、代价或选择。" },
                )
                chapterSeeds += seed
                globalOrder++
            }
        }
        if (chapterNodes.isEmpty()) {
            val seed = FoundationChapter(
                order = 1,
                title = "第一章",
                objective = "用一个具体异常建立开篇钩子、主角现实目标与核心问题。",
                conflict = "主角试图维持原有生活，但异常第一次越过安全边界。",
                turningPoint = "章末出现无法用常识解释的新证据。",
            )
            chapterNodes += OutlineNode(
                id = "chapter-$novelId-1-${UUID.randomUUID()}",
                novelId = novelId,
                parentId = volumeNodes.first().id,
                level = OutlineLevel.CHAPTER,
                order = 1,
                title = seed.title,
                objective = seed.objective,
                conflict = seed.conflict,
                turningPoint = seed.turningPoint,
            )
            chapterSeeds += seed
        }

        val foundationBible = foundation.bible.take(32).map { item ->
            BibleEntry(
                id = "bible-${UUID.randomUUID()}",
                novelId = novelId,
                category = item.category,
                name = item.name.ifBlank { item.category.name },
                content = item.content,
                aliases = item.aliases,
                locked = item.locked,
            )
        }.toMutableList()
        if (foundation.styleGuide.isNotBlank() && foundationBible.none { it.category == BibleCategory.STYLE }) {
            foundationBible += BibleEntry(
                id = "bible-${UUID.randomUUID()}",
                novelId = novelId,
                category = BibleCategory.STYLE,
                name = "叙事风格基线",
                content = foundation.styleGuide,
                locked = true,
            )
        }

        val characters = foundation.characters.take(16).map { item ->
            CharacterState(
                id = "character-${UUID.randomUUID()}",
                novelId = novelId,
                name = item.name.ifBlank { "未命名角色" },
                personality = item.personality.ifEmpty { listOf("待在正文中继续显形") },
                location = item.location.ifBlank { "故事起点" },
                physicalState = item.physicalState.ifBlank { "正常" },
                emotionalState = item.emotionalState.ifBlank { "平静" },
                goal = item.goal.ifBlank { "解决眼前最迫切的问题" },
                knownSecrets = item.knownSecrets,
                possessions = item.possessions,
                relationshipNotes = item.relationships,
                lastUpdatedChapter = 0,
            )
        }

        val foreshadowing = foundation.foreshadowing.take(12).map { item ->
            val start = item.expectedChapterStart.coerceAtLeast(1)
            val end = item.expectedChapterEnd.coerceAtLeast(start)
            Foreshadowing(
                id = "foreshadow-${UUID.randomUUID()}",
                novelId = novelId,
                title = item.title.ifBlank { "待命名伏笔" },
                plantedChapter = 0,
                detail = item.detail,
                expectedPayoff = item.expectedPayoff,
                expectedChapterStart = start,
                expectedChapterEnd = end,
                status = ForeshadowStatus.PLANTED,
            )
        }

        val firstNode = chapterNodes.first()
        val firstSeed = chapterSeeds.first()
        val firstVolume = volumeNodes.firstOrNull { it.id == firstNode.parentId } ?: volumeNodes.first()
        val protagonist = characters.firstOrNull()
        val firstDraft = ChapterDraft(
            id = "draft-$novelId-1",
            novelId = novelId,
            chapterNumber = 1,
            title = firstNode.title,
            objective = firstNode.objective,
            scenePlan = listOf(
                ScenePlan(
                    order = 1,
                    viewpoint = protagonist?.name ?: "主角",
                    location = protagonist?.location ?: "故事起点",
                    purpose = firstSeed.objective,
                    conflict = firstSeed.conflict,
                    outcome = firstSeed.turningPoint,
                )
            ),
        )

        val outline = buildList {
            add(master)
            addAll(volumeNodes)
            addAll(chapterNodes)
        }
        val snapshot = base.snapshot.copy(
            novel = base.snapshot.novel.copy(
                title = foundation.title.ifBlank { base.snapshot.novel.title },
                genre = foundation.genre.ifBlank { base.snapshot.novel.genre },
                premise = foundation.premise.ifBlank { base.snapshot.novel.premise },
                theme = foundation.theme.ifBlank { base.snapshot.novel.theme },
                targetWords = foundation.targetWords.coerceIn(10_000, 5_000_000),
                currentChapter = 1,
                status = NovelStatus.WRITING,
            ),
            activeOutline = listOf(master, firstVolume, firstNode),
            bible = foundationBible,
            characters = characters,
            relevantForeshadowing = foreshadowing,
            recentTimeline = emptyList(),
            recentSummaries = emptyList(),
            longTermSummary = "",
            outline = outline,
        )
        val saved = projects.saveStructure(snapshot, firstDraft)
        projects.setActiveStoryId(novelId)
        return saved
    }
}
