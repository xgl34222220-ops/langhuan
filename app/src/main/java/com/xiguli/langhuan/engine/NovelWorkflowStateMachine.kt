package com.xiguli.langhuan.engine

import kotlinx.serialization.Serializable

/**
 * Novel Skill OS V7 workflow state.
 *
 * This is process metadata, not story Canon. It tracks where the authoring workflow is, what the
 * user has confirmed, and which downstream artifacts became stale after an upstream change.
 */
@Serializable
enum class NovelWorkflowStage(val label: String) {
    START("项目接续"),
    BRIEF("创作意图"),
    RESEARCH("参考研究"),
    REFERENCE_DISTILLATION("参考蒸馏"),
    FOUNDATION("作品设定"),
    BLUEPRINT("建书蓝图"),
    MASTER_OUTLINE("总纲"),
    VOLUME_OUTLINE("卷纲"),
    CHAPTER_PLAN("章纲"),
    DRAFT("正文工作稿"),
    REVIEW("审校"),
    CANON_SYNC("Canon 同步"),
    COMPLETE("当前阶段完成"),
}

@Serializable
enum class NovelWorkflowStatus(val label: String) {
    NOT_STARTED("未开始"),
    RUNNING("进行中"),
    AWAITING_CONFIRMATION("待确认"),
    CONFIRMED("已确认"),
    NEEDS_REWORK("需要返工"),
    SKIPPED("已跳过"),
}

@Serializable
enum class NovelArtifactKind(val label: String) {
    BRIEF("创作意图"),
    RESEARCH("参考研究"),
    REFERENCE_DNA("Reference DNA"),
    FOUNDATION("作品设定"),
    BLUEPRINT("建书蓝图"),
    MASTER_OUTLINE("总纲"),
    VOLUME_OUTLINE("卷纲"),
    CHAPTER_PLAN("章纲"),
    WORKING_DRAFT("正文工作稿"),
    REVIEW_REPORT("审校报告"),
    CANON_DELTA("Canon 变更集"),
}

@Serializable
data class NovelWorkflowArtifact(
    val id: String,
    val kind: NovelArtifactKind,
    val stage: NovelWorkflowStage,
    val label: String = kind.label,
    val revision: Int = 1,
    val chapterNumber: Int? = null,
    val stale: Boolean = false,
    val staleReason: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NovelWorkflowDecision(
    val key: String,
    val value: String,
    val source: String = "user",
    val confirmed: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NovelWorkflowHistoryEntry(
    val stage: NovelWorkflowStage,
    val status: NovelWorkflowStatus,
    val note: String = "",
    val artifactIds: List<String> = emptyList(),
    val atMillis: Long = System.currentTimeMillis(),
)

@Serializable
data class NovelWorkflowCapabilitySnapshot(
    val routeIntent: String = "",
    val enabledCapabilities: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class NovelWorkflowState(
    val schemaVersion: Int = 1,
    val novelId: String,
    val currentStage: NovelWorkflowStage = NovelWorkflowStage.START,
    val stageStatus: NovelWorkflowStatus = NovelWorkflowStatus.RUNNING,
    val pendingRequest: String = "确认这是新项目还是继续已有项目。",
    val nextStage: NovelWorkflowStage? = NovelWorkflowStage.BRIEF,
    val decisions: Map<String, NovelWorkflowDecision> = emptyMap(),
    val artifacts: List<NovelWorkflowArtifact> = emptyList(),
    val stageHistory: List<NovelWorkflowHistoryEntry> = emptyList(),
    val capabilities: NovelWorkflowCapabilitySnapshot = NovelWorkflowCapabilitySnapshot(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val staleArtifacts: List<NovelWorkflowArtifact> get() = artifacts.filter { it.stale }

    fun compactSummary(): String = buildString {
        append(currentStage.label).append(" · ").append(stageStatus.label)
        if (staleArtifacts.isNotEmpty()) append(" · 待复核 ").append(staleArtifacts.size)
    }

    /** Hidden process guidance for the conversational host. This must never be treated as Canon. */
    fun systemGuidance(): String = buildString {
        appendLine("【Novel Skill OS V7 · 工作流状态（仅流程元数据，不是故事事实）】")
        appendLine("当前阶段：${currentStage.label} / ${stageStatus.label}")
        pendingRequest.takeIf { it.isNotBlank() }?.let { appendLine("当前唯一待处理：$it") }
        nextStage?.let { appendLine("当前 Gate 通过后：${it.label}") }
        if (capabilities.enabledCapabilities.isNotEmpty()) {
            appendLine("本轮已路由能力：${capabilities.enabledCapabilities.joinToString("、")}")
        }
        if (staleArtifacts.isNotEmpty()) {
            appendLine("存在 ${staleArtifacts.size} 个因上游修改而待复核的旧产物；保留旧产物，但不得当作已确认最新版本。")
        }
        appendLine("交互规则：不要把整套流程一次性丢给用户；只处理当前阻塞项。‘继续/可以/确认’只确认当前 Gate，不代表后续阶段全部通过。")
        appendLine("返工规则：上游发生实质修改时保留下游产物并标记 stale，只回到最早受影响阶段，不整本推倒重来。")
    }.trim()
}

object NovelWorkflowStateMachine {
    private val skippableStages = setOf(
        NovelWorkflowStage.RESEARCH,
        NovelWorkflowStage.REFERENCE_DISTILLATION,
        NovelWorkflowStage.VOLUME_OUTLINE,
    )

    fun initial(novelId: String): NovelWorkflowState = NovelWorkflowState(novelId = novelId)

    /** START has no material artifact; this confirms the resume point and enters BRIEF. */
    fun begin(state: NovelWorkflowState): NovelWorkflowState {
        if (state.currentStage != NovelWorkflowStage.START) return state
        return moveToNext(
            state = state,
            completedStatus = NovelWorkflowStatus.CONFIRMED,
            note = "项目接续点已确认",
        )
    }

    /**
     * Records a material artifact and stops at the current confirmation gate.
     * A newer revision replaces only the same artifact id; older unrelated artifacts are preserved.
     */
    fun submitArtifact(
        state: NovelWorkflowState,
        artifact: NovelWorkflowArtifact,
        pendingRequest: String = "请确认当前${state.currentStage.label}；如需修改，直接说具体要改什么。",
    ): NovelWorkflowState {
        require(artifact.stage == state.currentStage) {
            "artifact stage ${artifact.stage} does not match current stage ${state.currentStage}"
        }
        val normalized = artifact.copy(stale = false, staleReason = "", updatedAt = System.currentTimeMillis())
        val updatedArtifacts = state.artifacts.filterNot { it.id == normalized.id } + normalized
        return state.copy(
            stageStatus = NovelWorkflowStatus.AWAITING_CONFIRMATION,
            pendingRequest = pendingRequest,
            artifacts = updatedArtifacts,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** A positive reply advances exactly one gate. */
    fun confirmCurrent(state: NovelWorkflowState, note: String = "用户确认当前 Gate"): NovelWorkflowState {
        if (state.stageStatus != NovelWorkflowStatus.AWAITING_CONFIRMATION) return state
        return moveToNext(state, NovelWorkflowStatus.CONFIRMED, note)
    }

    fun skipCurrent(state: NovelWorkflowState, note: String = "用户明确跳过当前阶段"): NovelWorkflowState {
        if (state.currentStage !in skippableStages) return state
        return moveToNext(state, NovelWorkflowStatus.SKIPPED, note)
    }

    /**
     * Return to the earliest affected stage and mark only downstream artifacts stale.
     * Nothing is deleted: stale artifacts remain available for comparison/re-confirmation.
     */
    fun rewindTo(
        state: NovelWorkflowState,
        earliestStage: NovelWorkflowStage,
        reason: String,
        chapterNumbers: Set<Int> = emptySet(),
    ): NovelWorkflowState {
        val staleArtifacts = state.artifacts.map { artifact ->
            val downstream = artifact.stage.ordinal >= earliestStage.ordinal
            val chapterMatches = chapterNumbers.isEmpty() || artifact.chapterNumber == null || artifact.chapterNumber in chapterNumbers
            if (downstream && chapterMatches) {
                artifact.copy(
                    stale = true,
                    staleReason = reason,
                    updatedAt = System.currentTimeMillis(),
                )
            } else artifact
        }
        return state.copy(
            currentStage = earliestStage,
            stageStatus = NovelWorkflowStatus.NEEDS_REWORK,
            pendingRequest = "上游内容已变化：$reason。只需处理最早受影响的${earliestStage.label}，其余旧产物已保留为待复核。",
            nextStage = nextOf(earliestStage),
            artifacts = staleArtifacts,
            stageHistory = state.stageHistory + NovelWorkflowHistoryEntry(
                stage = state.currentStage,
                status = NovelWorkflowStatus.NEEDS_REWORK,
                note = reason,
                artifactIds = staleArtifacts.filter { it.stale }.map { it.id },
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    /** Convert an actual chapter dependency report into a bounded workflow rewind. */
    fun applyChapterDependencyImpact(
        state: NovelWorkflowState,
        report: ChapterDependencyReport,
        reason: String = "第${report.sourceChapter}章发生实质修改",
    ): NovelWorkflowState {
        val affectedChapters = buildSet {
            add(report.sourceChapter)
            report.downstream.mapNotNullTo(this) { it.chapterNumber }
        }
        val earliest = if (report.downstream.any { it.kind == DependencyKind.OUTLINE }) {
            NovelWorkflowStage.CHAPTER_PLAN
        } else {
            NovelWorkflowStage.DRAFT
        }
        return rewindTo(state, earliest, reason, affectedChapters)
    }

    fun recordDecision(
        state: NovelWorkflowState,
        key: String,
        value: String,
        source: String = "user",
        confirmed: Boolean = true,
    ): NovelWorkflowState {
        val decision = NovelWorkflowDecision(key, value, source, confirmed)
        return state.copy(
            decisions = state.decisions + (key to decision),
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun syncRoute(state: NovelWorkflowState, route: NovelRouteDecision): NovelWorkflowState = state.copy(
        capabilities = NovelWorkflowCapabilitySnapshot(
            routeIntent = route.intent.name,
            enabledCapabilities = route.capabilities.map { it.name },
            updatedAt = System.currentTimeMillis(),
        ),
        updatedAt = System.currentTimeMillis(),
    )

    /**
     * Natural-language Gate handling is deliberately conservative. It only fires on short,
     * unambiguous approval/rework replies and therefore cannot turn normal discussion into writes.
     */
    fun applyGateReply(state: NovelWorkflowState, message: String): NovelWorkflowState {
        if (state.stageStatus != NovelWorkflowStatus.AWAITING_CONFIRMATION) return state
        val normalized = message.trim().replace(" ", "")
        val approve = normalized in setOf("继续", "可以", "确认", "没问题", "就这样", "通过", "确定")
        if (approve) return confirmCurrent(state)

        val reject = listOf("不行", "不对", "重做", "重新", "修改", "改一下", "有问题").any { normalized.contains(it) }
        if (reject) {
            return state.copy(
                stageStatus = NovelWorkflowStatus.NEEDS_REWORK,
                pendingRequest = "当前${state.currentStage.label}需要返工。直接说明最需要修改的地方即可。",
                updatedAt = System.currentTimeMillis(),
            )
        }
        return state
    }

    private fun moveToNext(
        state: NovelWorkflowState,
        completedStatus: NovelWorkflowStatus,
        note: String,
    ): NovelWorkflowState {
        val artifactsAtStage = state.artifacts
            .filter { it.stage == state.currentStage && !it.stale }
            .map { it.id }
        val history = state.stageHistory + NovelWorkflowHistoryEntry(
            stage = state.currentStage,
            status = completedStatus,
            note = note,
            artifactIds = artifactsAtStage,
        )
        val next = nextOf(state.currentStage)
        if (next == null) {
            return state.copy(
                currentStage = NovelWorkflowStage.COMPLETE,
                stageStatus = NovelWorkflowStatus.CONFIRMED,
                pendingRequest = "",
                nextStage = null,
                stageHistory = history,
                updatedAt = System.currentTimeMillis(),
            )
        }
        return state.copy(
            currentStage = next,
            stageStatus = NovelWorkflowStatus.RUNNING,
            pendingRequest = defaultPending(next),
            nextStage = nextOf(next),
            stageHistory = history,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun nextOf(stage: NovelWorkflowStage): NovelWorkflowStage? = when (stage) {
        NovelWorkflowStage.COMPLETE -> null
        else -> NovelWorkflowStage.entries.getOrNull(stage.ordinal + 1)
    }

    private fun defaultPending(stage: NovelWorkflowStage): String = when (stage) {
        NovelWorkflowStage.START -> "确认这是新项目还是继续已有项目。"
        NovelWorkflowStage.BRIEF -> "说清这本书现在最核心的题材、读者体验和你最不想要的东西。"
        NovelWorkflowStage.RESEARCH -> "提供参考材料，或允许只读研究；没有参考也可以明确跳过。"
        NovelWorkflowStage.REFERENCE_DISTILLATION -> "确认要保留的参考机制与明确禁止照搬的部分。"
        NovelWorkflowStage.FOUNDATION -> "确认世界规则、人物、势力、地点与硬约束。"
        NovelWorkflowStage.BLUEPRINT -> "确认建书蓝图的核心承诺、矛盾和长期方向。"
        NovelWorkflowStage.MASTER_OUTLINE -> "确认总纲与主线升级路径。"
        NovelWorkflowStage.VOLUME_OUTLINE -> "确认当前卷纲；不需要分卷时可以跳过。"
        NovelWorkflowStage.CHAPTER_PLAN -> "确认当前章目标、冲突、转折和场景顺序。"
        NovelWorkflowStage.DRAFT -> "检查正文工作稿；指出具体问题或确认进入审校。"
        NovelWorkflowStage.REVIEW -> "确认审校结果与需要修复的问题。"
        NovelWorkflowStage.CANON_SYNC -> "确认本章真实发生的事实变更后再同步 Canon。"
        NovelWorkflowStage.COMPLETE -> ""
    }
}
