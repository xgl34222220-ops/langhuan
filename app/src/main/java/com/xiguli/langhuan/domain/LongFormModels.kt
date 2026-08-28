package com.xiguli.langhuan.domain

import kotlinx.serialization.Serializable

/** Controls the lightweight continuity layer used for very long novels. */
@Serializable
data class LongFormConfig(
    val enabled: Boolean = true,
    val hotChapterWindow: Int = 8,
    val mediumWindow: Int = 12,
    val arcSpan: Int = 30,
    val auditInterval: Int = 25,
)

@Serializable
enum class PlotArcPhase {
    SETUP,
    ESCALATION,
    TURN,
    CLIMAX,
    PAYOFF,
    OVERDUE,
    RESOLVED,
}

@Serializable
data class RollingPlotArc(
    val id: String,
    val title: String,
    val startChapter: Int,
    val plannedEndChapter: Int,
    val objective: String,
    val centralConflict: String,
    val expectedPayoff: String,
    val phase: PlotArcPhase = PlotArcPhase.SETUP,
    val lastUpdatedChapter: Int = 0,
    val milestones: List<String> = emptyList(),
)

/** 10-20 chapter factual roll-up. It stores compressed facts, never full prose. */
@Serializable
data class MediumTermMemory(
    val startChapter: Int,
    val endChapter: Int,
    val summary: String,
    val keyFacts: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

@Serializable
data class CharacterGrowthState(
    val characterId: String,
    val name: String,
    val stage: String = "起点",
    val currentBelief: String = "",
    val internalConflict: String = "",
    val growthDirection: String = "",
    val lastTurningChapter: Int = 0,
    val milestones: List<String> = emptyList(),
)

@Serializable
enum class LongFormHealthLevel { HEALTHY, WATCH, RISK }

@Serializable
data class LongFormHealthReport(
    val lastAuditChapter: Int = 0,
    val score: Int = 100,
    val level: LongFormHealthLevel = LongFormHealthLevel.HEALTHY,
    val warnings: List<String> = emptyList(),
    val overdueForeshadows: List<String> = emptyList(),
    val staleCharacters: List<String> = emptyList(),
    val openArcCount: Int = 0,
)

/** Per-chapter information release allowance. IDs only: the truth itself never enters rolling plans. */
@Serializable
data class RevealBudget(
    val chapterNumber: Int = 0,
    val maxFullReveals: Int = 0,
    val maxPartialReveals: Int = 1,
    val allowedFullBoundaryIds: List<String> = emptyList(),
    val allowedPartialBoundaryIds: List<String> = emptyList(),
    val forbiddenBoundaryIds: List<String> = emptyList(),
)

/** One chapter in the forward-looking rolling plan. This is a proposal, never Canon. */
@Serializable
data class PlannedChapterBeat(
    val chapterNumber: Int,
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
    val characterFocus: List<String> = emptyList(),
    val foreshadowingTargets: List<String> = emptyList(),
    val guardrail: String = "",
    /** True means title/objective/conflict/turningPoint came from a locked formal outline. */
    val fixedByOutline: Boolean = false,
    /** Limits how much hidden information this chapter may release. */
    val revealBudget: RevealBudget = RevealBudget(),
)

/** A gradual, observable character change to pursue inside the current rolling horizon. */
@Serializable
data class CharacterArcTarget(
    val name: String,
    val targetChapter: Int,
    val currentState: String = "",
    val desiredChange: String,
    val pressure: String = "",
    val forbiddenRegression: String = "",
)

@Serializable
enum class ForeshadowPlanAction { HOLD, TOUCH, ESCALATE, PAYOFF }

@Serializable
data class ForeshadowCadence(
    val foreshadowId: String,
    val title: String,
    val targetChapter: Int,
    val action: ForeshadowPlanAction = ForeshadowPlanAction.HOLD,
    val reason: String = "",
)

@Serializable
enum class DriftSeverity { INFO, WATCH, HIGH }

@Serializable
data class StoryDriftSignal(
    val code: String,
    val severity: DriftSeverity = DriftSeverity.INFO,
    val message: String,
    val evidence: String = "",
    val repair: String = "",
)

/**
 * Future 3-10 chapter plan maintained by the autonomous editor.
 * It lives beside continuity state so it can be regenerated without changing Canon or formal outlines.
 */
@Serializable
data class AutonomousStoryPlan(
    val baseChapter: Int = 0,
    val horizonEndChapter: Int = 0,
    val generation: Int = 0,
    val updatedAt: Long = 0L,
    val chapters: List<PlannedChapterBeat> = emptyList(),
    val characterTargets: List<CharacterArcTarget> = emptyList(),
    val foreshadowCadence: List<ForeshadowCadence> = emptyList(),
    val driftSignals: List<StoryDriftSignal> = emptyList(),
    val correctionStrategy: String = "",
    /** Detects when locked Canon changed after this rolling plan was generated. */
    val canonDigest: String = "",
)

@Serializable
enum class PlanExecutionStatus { ALIGNED, PARTIAL, DEVIATED, UNPLANNED }

/** What actually happened compared with the rolling plan at commit time. */
@Serializable
data class ChapterExecutionRecord(
    val chapterNumber: Int,
    val plannedObjective: String = "",
    val actualSummary: String = "",
    val status: PlanExecutionStatus = PlanExecutionStatus.UNPLANNED,
    val completionScore: Int = 0,
    val deviations: List<String> = emptyList(),
    val affectedFutureChapters: List<Int> = emptyList(),
    val repairHint: String = "",
    val recordedAt: Long = 0L,
)

@Serializable
enum class NarrativeDebtKind {
    FORESHADOW,
    CHARACTER_ARC,
    PLOT_PROMISE,
    RELATIONSHIP,
    KNOWLEDGE_REVEAL,
}

@Serializable
enum class NarrativeDebtStatus { OPEN, DUE, OVERDUE, RESOLVED }

/** A promise the story owes the reader. It is tracking state, not Canon. */
@Serializable
data class NarrativeDebt(
    val id: String,
    val kind: NarrativeDebtKind,
    val title: String,
    val openedChapter: Int,
    val dueStartChapter: Int = 0,
    val dueEndChapter: Int = 0,
    val lastTouchedChapter: Int = 0,
    val priority: Int = 50,
    val status: NarrativeDebtStatus = NarrativeDebtStatus.OPEN,
    val evidence: String = "",
    val resolutionCriteria: String = "",
)

@Serializable
enum class AuthorLearningSource {
    MANUAL_EDIT,
    AI_REWRITE_ACCEPTED,
    AI_REWRITE_REJECTED,
}

@Serializable
enum class AuthorPreferenceKind {
    PROSE,
    DIALOGUE,
    PACING,
    EXPLANATION,
    DESCRIPTION,
    RHYTHM,
    OTHER,
}

/** A compact edit event. Only the changed excerpts are kept; full chapter history stays in versions. */
@Serializable
data class AuthorEditSignal(
    val id: String,
    val chapterNumber: Int,
    val source: AuthorLearningSource,
    val beforeExcerpt: String = "",
    val afterExcerpt: String = "",
    val instruction: String = "",
    val deltaChars: Int = 0,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
)

/** Stable writing preference distilled from repeated edits or an explicitly accepted instruction. */
@Serializable
data class AuthorPreferenceRule(
    val id: String,
    val kind: AuthorPreferenceKind = AuthorPreferenceKind.OTHER,
    val instruction: String,
    val confidence: Int = 50,
    val evidenceCount: Int = 1,
    val lastChapter: Int = 0,
    val active: Boolean = true,
)

@Serializable
data class AuthorPreferenceProfile(
    val enabled: Boolean = true,
    val rules: List<AuthorPreferenceRule> = emptyList(),
    val recentSignals: List<AuthorEditSignal> = emptyList(),
    val manualEditBatches: Int = 0,
    val acceptedAiRewrites: Int = 0,
    val rejectedAiRewrites: Int = 0,
    val updatedAt: Long = 0L,
)

@Serializable
data class LongFormState(
    val config: LongFormConfig = LongFormConfig(),
    val arcs: List<RollingPlotArc> = emptyList(),
    val mediumMemories: List<MediumTermMemory> = emptyList(),
    val characterGrowth: List<CharacterGrowthState> = emptyList(),
    val health: LongFormHealthReport = LongFormHealthReport(),
    /** Forward-looking proposal state; default keeps all old projects backward-compatible. */
    val autonomousPlan: AutonomousStoryPlan = AutonomousStoryPlan(),
    /** Plan-vs-actual history. Full prose is never stored here. */
    val executionHistory: List<ChapterExecutionRecord> = emptyList(),
    /** Outstanding narrative promises. These may trigger planning pressure but never rewrite Canon. */
    val narrativeDebts: List<NarrativeDebt> = emptyList(),
    /** Learns stable prose preferences from accepted/rejected rewrites and meaningful manual edit batches. */
    val authorProfile: AuthorPreferenceProfile = AuthorPreferenceProfile(),
    val lastSettledChapter: Int = 0,
)
