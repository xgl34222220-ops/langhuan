package com.xiguli.langhuan.domain

import kotlinx.serialization.Serializable

@Serializable
data class GenerationRequest(
    val snapshot: StorySnapshot,
    val chapter: ChapterDraft,
    val targetWords: Int,
    val extraInstruction: String = "",
)

@Serializable
data class GeneratedChapter(
    val title: String,
    val content: String,
    val summary: String,
    val stateChanges: List<StateChange>,
    val touchedForeshadowingIds: List<String>,
)

@Serializable
data class StateChange(
    val subject: String,
    val field: String,
    val before: String,
    val after: String,
    val evidence: String,
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

