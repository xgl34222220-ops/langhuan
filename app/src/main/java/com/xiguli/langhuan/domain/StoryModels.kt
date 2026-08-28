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
    /** 可排序的故事日序号。0 代表 0.16 以前的旧数据尚未结构化。 */
    val storyDay: Int = 0,
    /** 清晨/上午/中午/下午/傍晚/夜间/深夜等故事内时段。 */
    val timeOfDay: String = "",
    /** 同一章节内的事件顺序，从 1 开始。 */
    val orderInChapter: Int = 0,
    /** 距上一条主时间线事件经过多久，例如“约20分钟”。 */
    val elapsedFromPrevious: String = "",
    /** 闪回事件不会推进当前主时间钟。 */
    val isFlashback: Boolean = false,
)

@Serializable
enum class ForeshadowStatus {
    PLANTED,
    DEVELOPING,
    /** 已进入计划回收窗口，但正文尚未确认回收。 */
    PAYOFF_DUE,
    /** 已超过计划最晚回收章节，长篇体检会持续提醒。 */
    OVERDUE,
    RESOLVED,
    ABANDONED,
}

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

/**
 * Agent 已确认写入长期记忆的事实来源记录。
 * 默认字段让旧项目无需数据库迁移即可继续反序列化；从 0.14 开始的新事实都会留下章级来源。
 */
@Serializable
data class FactProvenance(
    val id: String,
    val novelId: StoryId,
    val chapter: Int,
    val kind: String,
    val subject: String,
    val before: String = "",
    val after: String = "",
    val evidence: String = "",
    val recordedAt: Long = 0L,
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
    /** 该场景发生在故事第几天。0 表示旧计划未锁定。 */
    val storyDay: Int = 0,
    /** 场景发生时段。 */
    val timeOfDay: String = "",
    /** 与上一场之间经过多久。 */
    val elapsedFromPrevious: String = "",
    /** 是否为明确的回忆/闪回场景。 */
    val isFlashback: Boolean = false,
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
    /** 结构化长期事实的章级来源。旧项目为空；新写入事实逐章累积，供依赖分析和安全回滚使用。 */
    val factHistory: List<FactProvenance> = emptyList(),
    /**
     * 百万字/两百万字连续创作状态。使用带默认值的 JSON 字段，因此旧项目无需 Room migration。
     * 只保存压缩后的剧情弧、成长轨迹和中期记忆，不复制整章正文。
     */
    val longForm: LongFormState = LongFormState(),
)
