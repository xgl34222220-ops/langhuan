package com.xiguli.langhuan.domain

import kotlinx.serialization.Serializable

typealias StoryId = String

@Serializable
data class Novel(
    val id: StoryId,
    val title: String,
    val genre: String,
    val premise: String,
    val theme: String,
    val targetWords: Int,
    val currentWords: Int = 0,
    val currentChapter: Int = 1,
    val status: NovelStatus = NovelStatus.PLANNING,
    /** 应用私有目录中的封面文件路径；默认值保证旧项目可直接升级。 */
    val coverPath: String = "",
)

@Serializable
enum class NovelStatus { PLANNING, WRITING, PAUSED, FINISHED }

@Serializable
enum class OutlineLevel { MASTER, VOLUME, CHAPTER }

@Serializable
data class OutlineNode(
    val id: String,
    val novelId: StoryId,
    val parentId: String? = null,
    val level: OutlineLevel,
    val order: Int,
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
    val mustInclude: List<String> = emptyList(),
    val forbidden: List<String> = emptyList(),
    val locked: Boolean = true,
)

@Serializable
enum class BibleCategory { WORLD, RULE, CHARACTER, FACTION, LOCATION, ITEM, STYLE, FORBIDDEN }

@Serializable
data class BibleEntry(
    val id: String,
    val novelId: StoryId,
    val category: BibleCategory,
    val name: String,
    val content: String,
    val aliases: List<String> = emptyList(),
    val locked: Boolean = true,
)

@Serializable
data class CharacterState(
    val id: String,
    val novelId: StoryId,
    val name: String,
    val personality: List<String>,
    val location: String,
    val physicalState: String,
    val emotionalState: String,
    val goal: String,
    val knownSecrets: List<String> = emptyList(),
    val possessions: List<String> = emptyList(),
    val relationshipNotes: Map<String, String> = emptyMap(),
    val lastUpdatedChapter: Int,
)

@Serializable
data class TimelineEvent(
    val id: String,
    val novelId: StoryId,
    val chapter: Int,
    val storyTime: String,
    val location: String,
    val participants: List<String>,
    val summary: String,
    val consequences: List<String> = emptyList(),
)

@Serializable
enum class ForeshadowStatus { PLANTED, DEVELOPING, RESOLVED, ABANDONED }

@Serializable
data class Foreshadowing(
    val id: String,
    val novelId: StoryId,
    val title: String,
    val plantedChapter: Int,
    val detail: String,
    val expectedPayoff: String,
    val expectedChapterStart: Int,
    val expectedChapterEnd: Int,
    val status: ForeshadowStatus,
)

@Serializable
data class ChapterDraft(
    val id: String,
    val novelId: StoryId,
    val chapterNumber: Int,
    val title: String,
    val objective: String,
    val scenePlan: List<ScenePlan>,
    val content: String = "",
    val summary: String = "",
    val version: Int = 1,
)

@Serializable
data class ScenePlan(
    val order: Int,
    val viewpoint: String,
    val location: String,
    val purpose: String,
    val conflict: String,
    val outcome: String,
)

@Serializable
data class StorySnapshot(
    val novel: Novel,
    /** 当前章节所处的总纲→卷纲→章纲链，直接送入写作 Prompt。 */
    val activeOutline: List<OutlineNode>,
    val bible: List<BibleEntry>,
    val characters: List<CharacterState>,
    val recentTimeline: List<TimelineEvent>,
    val relevantForeshadowing: List<Foreshadowing>,
    val recentSummaries: List<String>,
    /** 被折叠的较早章节摘要。默认值保证 0.3 数据可直接反序列化。 */
    val longTermSummary: String = "",
    /** 完整三级大纲。0.4 以前没有该字段，加载时为空则用 activeOutline 自动补齐。 */
    val outline: List<OutlineNode> = emptyList(),
)
