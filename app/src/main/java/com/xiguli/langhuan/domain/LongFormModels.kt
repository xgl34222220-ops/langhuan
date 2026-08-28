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
data class LongFormState(
    val config: LongFormConfig = LongFormConfig(),
    val arcs: List<RollingPlotArc> = emptyList(),
    val mediumMemories: List<MediumTermMemory> = emptyList(),
    val characterGrowth: List<CharacterGrowthState> = emptyList(),
    val health: LongFormHealthReport = LongFormHealthReport(),
    /** Forward-looking proposal state; default keeps all old projects backward-compatible. */
    val autonomousPlan: AutonomousStoryPlan = AutonomousStoryPlan(),
    val lastSettledChapter: Int = 0,
)
