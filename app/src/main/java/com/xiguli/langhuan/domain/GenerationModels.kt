package com.xiguli.langhuan.domain

import kotlinx.serialization.Serializable

@Serializable
data class GenerationRequest(
    val snapshot: StorySnapshot,
    val chapter: ChapterDraft,
    val targetWords: Int,
    val extraInstruction: String = "",
)

/**
 * AI 的结构化输出属于“非可信外部输入”。除正文 content 外，辅助字段允许模型漏填，
 * 由业务层再决定是否可用，避免一次可恢复的 JSON 缺字段让整条生成/复盘链直接崩溃。
 */
@Serializable
data class GeneratedChapter(
    val title: String = "",
    val content: String = "",
    val summary: String = "",
    val stateChanges: List<StateChange> = emptyList(),
    val touchedForeshadowingIds: List<String> = emptyList(),
)

@Serializable
data class StateChange(
    val subject: String = "",
    val field: String = "",
    val before: String = "",
    val after: String = "",
    val evidence: String = "",
)

@Serializable
enum class IssueSeverity { INFO, WARNING, BLOCKING }

@Serializable
data class ConsistencyIssue(
    val severity: IssueSeverity,
    val code: String,
    val message: String,
    val evidence: String = "",
    val repairInstruction: String,
)

@Serializable
data class GenerationResult(
    val chapter: GeneratedChapter,
    val issues: List<ConsistencyIssue>,
) {
    val canCommit: Boolean
        get() = issues.none { it.severity == IssueSeverity.BLOCKING }
}
