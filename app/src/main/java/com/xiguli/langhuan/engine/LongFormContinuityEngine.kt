package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterGrowthState
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.LongFormHealthLevel
import com.xiguli.langhuan.domain.LongFormHealthReport
import com.xiguli.langhuan.domain.MediumTermMemory
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.PlotArcPhase
import com.xiguli.langhuan.domain.RollingPlotArc
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.domain.StorySnapshot
import kotlin.math.abs

/**
 * Local continuity layer for 100万~200万字 projects.
 *
 * It does not call an AI model. Every committed chapter deterministically updates:
 * - a rolling 20-40 chapter plot arc,
 * - 10-20 chapter medium-term factual memory,
 * - character growth milestones,
 * - foreshadow payoff windows,
 * - periodic long-form health checks.
 *
 * Full prose never enters this state; only summaries and compact facts are kept.
 */
class LongFormContinuityEngine {
    fun settle(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        generated: GeneratedChapter,
    ): StorySnapshot {
        val config = snapshot.longForm.config
        if (!config.enabled) return snapshot
        val chapterNumber = chapter.chapterNumber.coerceAtLeast(1)
        val summary = generated.summary.trim().ifBlank { chapter.summary.trim() }
        val withForeshadows = updateForeshadowWindows(snapshot, chapterNumber)
        val longForm = withForeshadows.longForm
            .let { state -> state.copy(arcs = updateArcs(withForeshadows, state.arcs, chapter, summary)) }
            .let { state -> state.copy(mediumMemories = updateMediumMemory(state.mediumMemories, chapterNumber, summary, generated.stateChanges, config.mediumWindow)) }
            .let { state -> state.copy(characterGrowth = updateCharacterGrowth(withForeshadows, state.characterGrowth, chapterNumber, generated.stateChanges)) }
            .let { state ->
                val shouldAudit = state.health.lastAuditChapter == 0 ||
                    chapterNumber - state.health.lastAuditChapter >= config.auditInterval.coerceAtLeast(5) ||
                    state.health.level == LongFormHealthLevel.RISK
                state.copy(
                    health = if (shouldAudit) audit(withForeshadows.copy(longForm = state), chapterNumber) else state.health,
                    lastSettledChapter = maxOf(state.lastSettledChapter, chapterNumber),
                )
            }
        return withForeshadows.copy(longForm = longForm)
    }

    /** Recalculate deadline/health state after Agent memory actions without adding duplicate summaries. */
    fun refreshAfterMemoryUpdate(snapshot: StorySnapshot, chapterNumber: Int): StorySnapshot {
        if (!snapshot.longForm.config.enabled) return snapshot
        val updated = updateForeshadowWindows(snapshot, chapterNumber)
        val growth = updateCharacterGrowth(updated, updated.longForm.characterGrowth, chapterNumber, emptyList())
        val health = if (
            updated.longForm.health.lastAuditChapter == 0 ||
            chapterNumber - updated.longForm.health.lastAuditChapter >= updated.longForm.config.auditInterval.coerceAtLeast(5)
        ) audit(updated.copy(longForm = updated.longForm.copy(characterGrowth = growth)), chapterNumber)
        else updated.longForm.health
        return updated.copy(
            longForm = updated.longForm.copy(
                characterGrowth = growth,
                health = health,
                lastSettledChapter = maxOf(updated.longForm.lastSettledChapter, chapterNumber),
            )
        )
    }

    fun promptText(snapshot: StorySnapshot): String {
        val state = snapshot.longForm
        if (!state.config.enabled) return "超长篇导航未启用。"
        val chapter = snapshot.novel.currentChapter.coerceAtLeast(1)
        val currentArc = state.arcs
            .filter { it.phase !in setOf(PlotArcPhase.RESOLVED) }
            .minByOrNull { abs(chapter - it.startChapter).takeIf { d -> chapter <= it.plannedEndChapter + state.config.arcSpan } ?: Int.MAX_VALUE }
        val medium = state.mediumMemories.takeLast(4)
        val growth = state.characterGrowth
            .sortedByDescending { it.lastTurningChapter }
            .take(8)
        val due = snapshot.relevantForeshadowing.filter {
            it.status == ForeshadowStatus.PAYOFF_DUE || it.status == ForeshadowStatus.OVERDUE
        }
        return buildString {
            appendLine("【超长篇导航｜只提供压缩事实，不替代锁定圣经】")
            currentArc?.let { arc ->
                appendLine("当前剧情弧：${arc.title}｜第${arc.startChapter}-${arc.plannedEndChapter}章｜阶段=${arc.phase}")
                appendLine("弧目标：${arc.objective}")
                appendLine("核心冲突：${arc.centralConflict}")
                appendLine("预期收束：${arc.expectedPayoff}")
                if (arc.milestones.isNotEmpty()) appendLine("近期弧里程碑：${arc.milestones.takeLast(5).joinToString("；")}")
            } ?: appendLine("当前剧情弧：尚未建立，下一章规划应先建立 20-40 章可收束的小目标。")
            if (medium.isNotEmpty()) {
                appendLine("中期记忆：")
                medium.forEach { item -> appendLine("- 第${item.startChapter}-${item.endChapter}章：${item.summary.take(650)}") }
            }
            if (growth.isNotEmpty()) {
                appendLine("角色成长：")
                growth.forEach { item ->
                    appendLine("- ${item.name}｜${item.stage}｜方向=${item.growthDirection.ifBlank { "保持已确认成长逻辑" }}｜最近转折=第${item.lastTurningChapter}章")
                }
            }
            if (due.isNotEmpty()) {
                appendLine("必须关注的伏笔窗口：")
                due.take(10).forEach { item ->
                    appendLine("- ${item.title}｜${item.status}｜计划第${item.expectedChapterStart}-${item.expectedChapterEnd}章回收｜${item.expectedPayoff}")
                }
            }
            if (state.health.warnings.isNotEmpty()) {
                appendLine("长篇体检提醒（不能为了消除提醒而强行改剧情）：")
                state.health.warnings.take(8).forEach { appendLine("- $it") }
            }
        }.take(8_000)
    }

    private fun updateForeshadowWindows(snapshot: StorySnapshot, chapter: Int): StorySnapshot {
        val updated = snapshot.relevantForeshadowing.map { item ->
            when {
                item.status in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED) -> item
                item.expectedChapterEnd > 0 && chapter > item.expectedChapterEnd -> item.copy(status = ForeshadowStatus.OVERDUE)
                item.expectedChapterStart > 0 && chapter >= item.expectedChapterStart && item.status in setOf(ForeshadowStatus.PLANTED, ForeshadowStatus.DEVELOPING) ->
                    item.copy(status = ForeshadowStatus.PAYOFF_DUE)
                else -> item
            }
        }
        return if (updated == snapshot.relevantForeshadowing) snapshot else snapshot.copy(relevantForeshadowing = updated)
    }

    private fun updateArcs(
        snapshot: StorySnapshot,
        existing: List<RollingPlotArc>,
        chapter: ChapterDraft,
        summary: String,
    ): List<RollingPlotArc> {
        val span = snapshot.longForm.config.arcSpan.coerceIn(16, 60)
        val number = chapter.chapterNumber.coerceAtLeast(1)
        val start = ((number - 1) / span) * span + 1
        val end = start + span - 1
        val id = "${snapshot.novel.id}:arc:$start"
        val volume = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.VOLUME }
            ?: snapshot.outline.filter { it.level == OutlineLevel.VOLUME }.minByOrNull { abs(it.order - ((number - 1) / span + 1)) }
        val chapterOutline = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.CHAPTER }
        val relative = (number - start).toDouble() / span.toDouble()
        val phase = when {
            number > end -> PlotArcPhase.OVERDUE
            relative < 0.20 -> PlotArcPhase.SETUP
            relative < 0.55 -> PlotArcPhase.ESCALATION
            relative < 0.72 -> PlotArcPhase.TURN
            relative < 0.90 -> PlotArcPhase.CLIMAX
            else -> PlotArcPhase.PAYOFF
        }
        val milestone = summary.takeIf { it.isNotBlank() }?.let { "第${number}章：${it.take(260)}" }
        val current = existing.firstOrNull { it.id == id }
        val arc = (current ?: RollingPlotArc(
            id = id,
            title = volume?.title?.let { "$it · 剧情弧${(start - 1) / span + 1}" } ?: "剧情弧 ${(start - 1) / span + 1}",
            startChapter = start,
            plannedEndChapter = end,
            objective = volume?.objective.orEmpty().ifBlank { chapterOutline?.objective.orEmpty().ifBlank { chapter.objective } },
            centralConflict = volume?.conflict.orEmpty().ifBlank { chapterOutline?.conflict.orEmpty().ifBlank { "围绕当前目标持续升级具体阻碍" } },
            expectedPayoff = volume?.turningPoint.orEmpty().ifBlank { chapterOutline?.turningPoint.orEmpty().ifBlank { "在本剧情弧末形成不可逆变化并进入下一阶段" } },
        )).copy(
            phase = phase,
            lastUpdatedChapter = number,
            milestones = (current?.milestones.orEmpty() + listOfNotNull(milestone)).distinct().takeLast(14),
        )
        val normalized = existing.map { old ->
            if (old.id == id) arc
            else if (old.phase != PlotArcPhase.RESOLVED && number > old.plannedEndChapter) old.copy(phase = PlotArcPhase.OVERDUE)
            else old
        }.toMutableList()
        if (normalized.none { it.id == id }) normalized += arc
        return normalized.sortedBy { it.startChapter }.takeLast(36)
    }

    private fun updateMediumMemory(
        existing: List<MediumTermMemory>,
        chapter: Int,
        summary: String,
        changes: List<StateChange>,
        windowSize: Int,
    ): List<MediumTermMemory> {
        val window = windowSize.coerceIn(8, 20)
        val start = ((chapter - 1) / window) * window + 1
        val end = start + window - 1
        val keyFacts = changes.mapNotNull { change ->
            val value = change.after.trim().ifBlank { change.before.trim() }
            if (change.subject.isBlank() || change.field.isBlank() || value.isBlank()) null
            else "${change.subject}/${change.field}=$value".take(260)
        }.take(14)
        val old = existing.firstOrNull { it.startChapter == start }
        val compactChapter = summary.takeIf { it.isNotBlank() }?.let { "第${chapter}章：${it.take(320)}" }.orEmpty()
        val mergedSummary = listOf(old?.summary.orEmpty(), compactChapter)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .let { if (it.length <= 2_400) it else it.take(500) + "\n……\n" + it.takeLast(1_800) }
        val item = MediumTermMemory(
            startChapter = start,
            endChapter = end,
            summary = mergedSummary,
            keyFacts = (old?.keyFacts.orEmpty() + keyFacts).distinct().takeLast(40),
            updatedAt = System.currentTimeMillis(),
        )
        return (existing.filterNot { it.startChapter == start } + item)
            .sortedBy { it.startChapter }
            .takeLast(80)
    }

    private fun updateCharacterGrowth(
        snapshot: StorySnapshot,
        existing: List<CharacterGrowthState>,
        chapter: Int,
        changes: List<StateChange>,
    ): List<CharacterGrowthState> {
        val estimatedChapters = (snapshot.novel.targetWords / 3_000).coerceAtLeast(60)
        val progress = chapter.toDouble() / estimatedChapters.toDouble()
        val stage = when {
            progress < 0.08 -> "起点"
            progress < 0.28 -> "适应与试错"
            progress < 0.52 -> "信念受压"
            progress < 0.76 -> "主动转变"
            progress < 0.93 -> "代价与决断"
            else -> "终局兑现"
        }
        val prior = existing.associateBy { it.characterId }.toMutableMap()
        snapshot.characters.take(24).forEach { character ->
            val related = changes.filter { it.subject.equals(character.name, ignoreCase = true) }
            val milestones = related.mapNotNull { change ->
                val field = change.field.lowercase()
                if (field !in setOf("goal", "目标", "emotionalstate", "情绪", "情绪状态", "relationship", "关系", "knownsecrets", "秘密", "已知秘密", "physicalstate", "身体状态", "伤势")) return@mapNotNull null
                val value = change.after.ifBlank { change.before }.trim()
                value.takeIf { it.isNotBlank() }?.let { "第${chapter}章 ${change.field}→${it.take(180)}" }
            }
            val old = prior[character.id]
            prior[character.id] = CharacterGrowthState(
                characterId = character.id,
                name = character.name,
                stage = stage,
                currentBelief = character.personality.joinToString("、").take(220),
                internalConflict = "当前情绪=${character.emotionalState}；当前目标=${character.goal}".take(320),
                growthDirection = old?.growthDirection.orEmpty().ifBlank { "目标与代价持续改变选择方式，但核心人格变化必须有章级证据" },
                lastTurningChapter = if (milestones.isNotEmpty()) chapter else maxOf(old?.lastTurningChapter ?: 0, character.lastUpdatedChapter),
                milestones = (old?.milestones.orEmpty() + milestones).distinct().takeLast(18),
            )
        }
        return prior.values.sortedBy { it.name }.take(40)
    }

    private fun audit(snapshot: StorySnapshot, chapter: Int): LongFormHealthReport {
        val warnings = mutableListOf<String>()
        val overdue = snapshot.relevantForeshadowing
            .filter { it.status == ForeshadowStatus.OVERDUE }
            .map { it.title }
        if (overdue.isNotEmpty()) warnings += "有 ${overdue.size} 条伏笔超过计划回收窗口：${overdue.take(4).joinToString("、")}"
        val activeForeshadows = snapshot.relevantForeshadowing.count {
            it.status !in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED)
        }
        if (activeForeshadows > 18) warnings += "活跃伏笔已达 $activeForeshadows 条，建议优先回收旧坑再新增。"

        val stale = snapshot.characters
            .filter { chapter - it.lastUpdatedChapter >= 45 }
            .map { it.name }
        if (stale.isNotEmpty()) warnings += "${stale.size} 个长期角色超过 45 章没有状态更新：${stale.take(5).joinToString("、")}"

        val overdueArcs = snapshot.longForm.arcs.filter {
            it.phase == PlotArcPhase.OVERDUE || (chapter > it.plannedEndChapter && it.phase != PlotArcPhase.RESOLVED)
        }
        if (overdueArcs.isNotEmpty()) warnings += "有 ${overdueArcs.size} 个剧情弧超过计划收束点，检查是否一直开新冲突却没有阶段性结算。"

        val futureFineOutline = snapshot.outline.count { it.level == OutlineLevel.CHAPTER && it.order in (chapter + 1)..(chapter + 5) }
        if (snapshot.outline.any { it.level == OutlineLevel.CHAPTER } && futureFineOutline < 3) {
            warnings += "未来 5 章只有 $futureFineOutline 条精细章纲，建议保持至少 3-5 章滚动规划。"
        }

        if (looksRepetitive(snapshot.recentSummaries.takeLast(8))) {
            warnings += "最近章节摘要高度相似，检查是否重复使用同一种调查/冲突/章末钩子。"
        }

        var score = 100
        score -= (overdue.size * 5).coerceAtMost(25)
        score -= (overdueArcs.size * 9).coerceAtMost(27)
        score -= (stale.size * 2).coerceAtMost(16)
        if (activeForeshadows > 18) score -= ((activeForeshadows - 18) * 2).coerceAtMost(16)
        if (futureFineOutline < 3 && snapshot.outline.any { it.level == OutlineLevel.CHAPTER }) score -= 8
        if (warnings.any { it.contains("高度相似") }) score -= 8
        score = score.coerceIn(0, 100)
        val level = when {
            score >= 82 -> LongFormHealthLevel.HEALTHY
            score >= 60 -> LongFormHealthLevel.WATCH
            else -> LongFormHealthLevel.RISK
        }
        return LongFormHealthReport(
            lastAuditChapter = chapter,
            score = score,
            level = level,
            warnings = warnings.distinct().take(12),
            overdueForeshadows = overdue.take(20),
            staleCharacters = stale.take(20),
            openArcCount = snapshot.longForm.arcs.count { it.phase != PlotArcPhase.RESOLVED },
        )
    }

    private fun looksRepetitive(summaries: List<String>): Boolean {
        if (summaries.size < 5) return false
        val signatures = summaries.map { text ->
            text.lowercase()
                .replace(Regex("第\\d+章|\\s+|[，。！？、；：,.!?;:]"), "")
                .windowed(2, 2, partialWindows = false)
                .filter { it.length == 2 }
                .toSet()
        }
        var highPairs = 0
        for (i in 1 until signatures.size) {
            val a = signatures[i - 1]
            val b = signatures[i]
            if (a.isEmpty() || b.isEmpty()) continue
            val overlap = a.intersect(b).size.toDouble() / minOf(a.size, b.size).toDouble()
            if (overlap >= 0.72) highPairs++
        }
        return highPairs >= 3
    }
}
