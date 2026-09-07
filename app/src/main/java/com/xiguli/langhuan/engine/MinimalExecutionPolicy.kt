package com.xiguli.langhuan.engine

/**
 * Minimal-execution policy inspired by Ponytail's reuse-before-expansion discipline.
 *
 * The goal is not to run fewer checks blindly; it is to select the smallest execution depth that
 * can satisfy the request while preserving required safety/consistency gates.
 */
enum class ExecutionDepth(val label: String) {
    AUTO("自动"),
    LIGHT("轻量"),
    STANDARD("标准"),
    DEEP("深度"),
}

data class ExecutionSignals(
    val mutatesCanon: Boolean = false,
    val writesLongFormContent: Boolean = false,
    val needsHistoricalRecall: Boolean = false,
    val touchesMultipleChapters: Boolean = false,
    val needsExternalResearch: Boolean = false,
    val userExplicitlyRequestsDeepReview: Boolean = false,
    val existingContextIsSufficient: Boolean = true,
)

data class ExecutionDecision(
    val depth: ExecutionDepth,
    val reason: String,
    val allowRag: Boolean,
    val allowAgentReplan: Boolean,
    val requireConsistencyGate: Boolean,
)

object MinimalExecutionPolicy {
    fun decide(
        requested: ExecutionDepth = ExecutionDepth.AUTO,
        signals: ExecutionSignals,
    ): ExecutionDecision {
        val resolved = when {
            requested != ExecutionDepth.AUTO -> requested
            signals.userExplicitlyRequestsDeepReview -> ExecutionDepth.DEEP
            signals.touchesMultipleChapters || signals.needsExternalResearch -> ExecutionDepth.DEEP
            signals.writesLongFormContent || signals.mutatesCanon || signals.needsHistoricalRecall -> ExecutionDepth.STANDARD
            else -> ExecutionDepth.LIGHT
        }

        return when (resolved) {
            ExecutionDepth.AUTO -> error("AUTO must be resolved before building a decision")
            ExecutionDepth.LIGHT -> ExecutionDecision(
                depth = resolved,
                reason = if (signals.existingContextIsSufficient) {
                    "当前上下文足够，复用已有状态并执行最小必要能力"
                } else {
                    "轻量补齐上下文，不启动全书级流程"
                },
                allowRag = !signals.existingContextIsSufficient && signals.needsHistoricalRecall,
                allowAgentReplan = false,
                requireConsistencyGate = signals.mutatesCanon,
            )
            ExecutionDepth.STANDARD -> ExecutionDecision(
                depth = resolved,
                reason = "正文/Canon/历史连续性需要标准写作链路",
                allowRag = signals.needsHistoricalRecall || signals.writesLongFormContent,
                allowAgentReplan = false,
                requireConsistencyGate = signals.mutatesCanon || signals.writesLongFormContent,
            )
            ExecutionDepth.DEEP -> ExecutionDecision(
                depth = resolved,
                reason = "跨章节、外部研究或用户明确要求深度复盘",
                allowRag = true,
                allowAgentReplan = signals.touchesMultipleChapters || signals.userExplicitlyRequestsDeepReview,
                requireConsistencyGate = true,
            )
        }
    }
}
