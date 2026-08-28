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

@Serializable
data class LongFormState(
    val config: LongFormConfig = LongFormConfig(),
    val arcs: List<RollingPlotArc> = emptyList(),
    val mediumMemories: List<MediumTermMemory> = emptyList(),
    val characterGrowth: List<CharacterGrowthState> = emptyList(),
    val health: LongFormHealthReport = LongFormHealthReport(),
    val lastSettledChapter: Int = 0,
)
