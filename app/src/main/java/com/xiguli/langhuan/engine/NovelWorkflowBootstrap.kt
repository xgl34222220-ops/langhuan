package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.StorySnapshot

/**
 * Compatibility bridge for projects created before V7 workflow metadata existed.
 * It derives only a safe resume stage from already-confirmed StorySnapshot facts; it does not
 * synthesize workflow artifacts or pretend earlier gates were individually confirmed.
 */
object NovelWorkflowBootstrap {
    fun fromSnapshot(state: NovelWorkflowState, snapshot: StorySnapshot): NovelWorkflowState {
        if (state.currentStage != NovelWorkflowStage.START) return state
        if (state.stageHistory.isNotEmpty() || state.artifacts.isNotEmpty()) return state

        val outline = if (snapshot.outline.isNotEmpty()) snapshot.outline else snapshot.activeOutline
        val resumeStage = when {
            snapshot.recentSummaries.isNotEmpty() || snapshot.factHistory.isNotEmpty() -> NovelWorkflowStage.CHAPTER_PLAN
            outline.any { it.level == OutlineLevel.CHAPTER } -> NovelWorkflowStage.CHAPTER_PLAN
            outline.any { it.level == OutlineLevel.VOLUME } -> NovelWorkflowStage.VOLUME_OUTLINE
            outline.any { it.level == OutlineLevel.MASTER } -> NovelWorkflowStage.MASTER_OUTLINE
            snapshot.bible.isNotEmpty() || snapshot.characters.isNotEmpty() -> NovelWorkflowStage.BLUEPRINT
            snapshot.novel.premise.isNotBlank() || snapshot.novel.genre.isNotBlank() || snapshot.novel.theme.isNotBlank() -> NovelWorkflowStage.FOUNDATION
            else -> NovelWorkflowStage.BRIEF
        }

        return state.copy(
            currentStage = resumeStage,
            stageStatus = NovelWorkflowStatus.RUNNING,
            pendingRequest = pendingFor(resumeStage),
            nextStage = NovelWorkflowStage.entries.getOrNull(resumeStage.ordinal + 1),
            decisions = state.decisions + (
                "v7_bootstrap" to NovelWorkflowDecision(
                    key = "v7_bootstrap",
                    value = "resume:${resumeStage.name}",
                    source = "confirmed StorySnapshot",
                    confirmed = true,
                )
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun pendingFor(stage: NovelWorkflowStage): String = when (stage) {
        NovelWorkflowStage.BRIEF -> "继续完善这本书的创作意图；只补当前真正缺失的关键信息。"
        NovelWorkflowStage.FOUNDATION -> "继续完善世界规则、人物、势力、地点与硬约束。"
        NovelWorkflowStage.BLUEPRINT -> "检查并继续完善建书蓝图的核心承诺、矛盾和长期方向。"
        NovelWorkflowStage.MASTER_OUTLINE -> "继续完善总纲与主线升级路径。"
        NovelWorkflowStage.VOLUME_OUTLINE -> "继续完善当前卷纲；不需要分卷时可以跳过。"
        NovelWorkflowStage.CHAPTER_PLAN -> "从当前已确认项目继续，先处理下一章或当前章的章纲。"
        NovelWorkflowStage.DRAFT -> "继续当前正文工作稿。"
        NovelWorkflowStage.REVIEW -> "继续审校当前正文。"
        NovelWorkflowStage.CANON_SYNC -> "确认本章真实发生的事实变更后再同步 Canon。"
        NovelWorkflowStage.RESEARCH -> "继续参考研究。"
        NovelWorkflowStage.REFERENCE_DISTILLATION -> "继续参考蒸馏。"
        NovelWorkflowStage.START -> "确认这是新项目还是继续已有项目。"
        NovelWorkflowStage.COMPLETE -> ""
    }
}
