package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.AutonomousStoryPlan
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ChapterExecutionRecord
import com.xiguli.langhuan.domain.DriftSeverity
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.NarrativeDebt
import com.xiguli.langhuan.domain.NarrativeDebtKind
import com.xiguli.langhuan.domain.NarrativeDebtStatus
import com.xiguli.langhuan.domain.PlanExecutionStatus
import com.xiguli.langhuan.domain.ReaderKnowledgeState
import com.xiguli.langhuan.domain.RevealBudget
import com.xiguli.langhuan.domain.StorySnapshot
import kotlin.math.roundToInt

/**
 * Closes the autonomous-planning loop after a chapter is actually committed.
 *
 * The forward plan is a proposal. This engine compares that proposal with committed prose/metadata,
 * records what really happened, maintains narrative debts, and decides which future chapters need
 * replanning. It never mutates locked Canon, formal outlines, bible entries, or knowledge truth.
 */
class AutonomousExecutionEngine(
    private val gateway: AiGateway? = null,
) {
    suspend fun assess(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        generated: GeneratedChapter,
    ): ChapterExecutionRecord {
        val planned = snapshot.longForm.autonomousPlan.chapters
            .firstOrNull { it.chapterNumber == chapter.chapterNumber }
            ?: return ChapterExecutionRecord(
                chapterNumber = chapter.chapterNumber,
                actualSummary = generated.summary.trim().ifBlank { fallbackSummary(generated.content) },
                status = PlanExecutionStatus.UNPLANNED,
                completionScore = 50,
                deviations = listOf("当前章节没有对应的滚动计划记录，无法进行计划命中校验。"),
                affectedFutureChapters = futureWindow(chapter.chapterNumber, 3),
                repairHint = "从已提交正文重新建立未来滚动计划，不回写既成事实。",
                recordedAt = System.currentTimeMillis(),
            )

        val fallback = localAssessment(planned.objective, planned.turningPoint, chapter.chapterNumber, generated)
        val ai = gateway ?: return fallback
        val excerpt = generated.content.trim().let { text ->
            when {
                text.length <= 5_000 -> text
                else -> text.take(3_000) + "\n……\n" + text.takeLast(1_800)
            }
        }
        val prompt = PromptBundle(
            system = """
                你是长篇小说的“计划执行审计员”。你只比较本章原滚动计划与已经冻结的实际正文，不评价文笔，也不能改写 Canon。

                判断标准：
                - ALIGNED：核心目标完成，转折方向成立；允许场景细节自然变化。
                - PARTIAL：核心目标只完成一部分，或转折发生但因果/人物结果明显变化，需要调整少量后续章节。
                - DEVIATED：核心目标未完成、出现不可逆的新结果、关键人物选择改变，原后续因果链已不能直接成立。
                不要因为措辞不同判偏航；只看剧情因果、人物选择、已发生事实和承诺是否改变。

                输出 GeneratedChapter JSON，不要 Markdown：
                - title 固定 EXECUTION_AUDIT
                - content = “0-100整数||ALIGNED/PARTIAL/DEVIATED”
                - summary = 一句话概括实际发生结果
                - stateChanges 仅在确有偏差时输出，field 固定 DEVIATION：
                  subject=稳定短代码；before=计划要求；after=实际变化；evidence="WATCH或HIGH||受影响未来章号用逗号分隔||最小修复"
                - touchedForeshadowingIds=[]
                受影响章节只能是当前章之后 1-6 章；不要要求重写已经提交的正文。
            """.trimIndent(),
            user = """
                章节：第${chapter.chapterNumber}章 ${chapter.title}

                【原滚动计划】
                目标：${planned.objective}
                冲突：${planned.conflict}
                转折：${planned.turningPoint}
                人物焦点：${planned.characterFocus.joinToString("、")}
                伏笔目标：${planned.foreshadowingTargets.joinToString("、")}
                护栏：${planned.guardrail}

                【正式章节合同】
                目的：${chapter.contract.purpose}
                必须发生：${chapter.contract.mustHappen.joinToString("；")}
                禁止发生：${chapter.contract.mustNotHappen.joinToString("；")}

                【冻结后的实际摘要】
                ${generated.summary}

                【实际状态变化】
                ${generated.stateChanges.joinToString("\n") { "- ${it.subject}/${it.field}: ${it.before} -> ${it.after}｜${it.evidence}" }}

                【实际触及伏笔】
                ${generated.touchedForeshadowingIds.joinToString("、")}

                【正文摘录】
                $excerpt
            """.trimIndent(),
        )
        val output = runCatching { ai.generate(prompt) }.getOrNull() ?: return fallback
        val header = output.content.split("||", limit = 2).map { it.trim() }
        val score = header.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 100) ?: fallback.completionScore
        val status = runCatching { PlanExecutionStatus.valueOf(header.getOrNull(1).orEmpty().uppercase()) }
            .getOrDefault(fallback.status)
        val deviations = output.stateChanges
            .filter { it.field.equals("DEVIATION", true) }
        val affected = deviations.flatMap { change ->
            change.evidence.split("||", limit = 3).getOrNull(1).orEmpty()
                .split(',', '，', '、')
                .mapNotNull { it.trim().toIntOrNull() }
        }.filter { it in (chapter.chapterNumber + 1)..(chapter.chapterNumber + 6) }
            .distinct().sorted()
            .ifEmpty {
                when (status) {
                    PlanExecutionStatus.DEVIATED -> futureWindow(chapter.chapterNumber, 3)
                    PlanExecutionStatus.PARTIAL -> futureWindow(chapter.chapterNumber, 2)
                    else -> emptyList()
                }
            }
        val repair = deviations.firstNotNullOfOrNull { change ->
            change.evidence.split("||", limit = 3).getOrNull(2)?.takeIf(String::isNotBlank)
        }.orEmpty().ifBlank { fallback.repairHint }
        val deviationText = deviations.map { change ->
            listOf(change.subject, change.after, change.evidence.split("||", limit = 3).firstOrNull().orEmpty())
                .filter { it.isNotBlank() }.joinToString("：")
        }.ifEmpty { if (status == PlanExecutionStatus.ALIGNED) emptyList() else fallback.deviations }
        return ChapterExecutionRecord(
            chapterNumber = chapter.chapterNumber,
            plannedObjective = planned.objective,
            actualSummary = output.summary.trim().ifBlank { generated.summary.trim().ifBlank { fallback.actualSummary } },
            status = status,
            completionScore = score,
            deviations = deviationText.take(8),
            affectedFutureChapters = affected,
            repairHint = repair,
            recordedAt = System.currentTimeMillis(),
        )
    }

    fun settle(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        generated: GeneratedChapter,
        execution: ChapterExecutionRecord,
    ): StorySnapshot {
        val debts = synchronizeDebts(snapshot, chapter, generated, execution)
        val history = (snapshot.longForm.executionHistory.filterNot { it.chapterNumber == chapter.chapterNumber } + execution)
            .sortedBy { it.chapterNumber }
            .takeLast(80)
        return snapshot.copy(
            longForm = snapshot.longForm.copy(
                executionHistory = history,
                narrativeDebts = debts,
            )
        )
    }

    fun mergeSelectivePlan(
        snapshot: StorySnapshot,
        candidate: AutonomousStoryPlan,
        affectedChapters: List<Int>,
    ): AutonomousStoryPlan {
        val old = snapshot.longForm.autonomousPlan
        if (old.chapters.isEmpty() || affectedChapters.isEmpty()) return enrichRevealBudgets(snapshot, candidate)
        val affected = affectedChapters.toSet()
        val oldByChapter = old.chapters.associateBy { it.chapterNumber }
        val mergedChapters = candidate.chapters.map { fresh ->
            val previous = oldByChapter[fresh.chapterNumber]
            val chosen = if (previous == null || fresh.chapterNumber in affected) fresh else previous
            chosen.copy(revealBudget = revealBudget(snapshot, chosen.chapterNumber))
        }
        val oldCharacter = old.characterTargets.associateBy { it.name }
        val mergedCharacter = candidate.characterTargets.map { fresh ->
            if (fresh.targetChapter in affected) fresh else oldCharacter[fresh.name] ?: fresh
        }.distinctBy { it.name }
        val oldCadence = old.foreshadowCadence.associateBy { it.foreshadowId }
        val mergedCadence = candidate.foreshadowCadence.map { fresh ->
            if (fresh.targetChapter in affected) fresh else oldCadence[fresh.foreshadowId] ?: fresh
        }.distinctBy { it.foreshadowId }
        return candidate.copy(
            chapters = mergedChapters,
            characterTargets = mergedCharacter,
            foreshadowCadence = mergedCadence,
            correctionStrategy = candidate.correctionStrategy.ifBlank { old.correctionStrategy },
        )
    }

    fun enrichRevealBudgets(snapshot: StorySnapshot, plan: AutonomousStoryPlan): AutonomousStoryPlan =
        plan.copy(chapters = plan.chapters.map { beat -> beat.copy(revealBudget = revealBudget(snapshot, beat.chapterNumber)) })

    private fun synchronizeDebts(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        generated: GeneratedChapter,
        execution: ChapterExecutionRecord,
    ): List<NarrativeDebt> {
        val current = chapter.chapterNumber
        val touched = generated.touchedForeshadowingIds.toSet()
        val byId = snapshot.longForm.narrativeDebts.associateBy { it.id }.toMutableMap()

        snapshot.relevantForeshadowing.forEach { item ->
            val id = "foreshadow:${item.id}"
            val old = byId[id]
            val resolved = item.status in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED)
            val dueStart = item.expectedChapterStart.coerceAtLeast(0)
            val dueEnd = item.expectedChapterEnd.coerceAtLeast(dueStart)
            val status = debtStatus(current, dueStart, dueEnd, resolved)
            byId[id] = NarrativeDebt(
                id = id,
                kind = NarrativeDebtKind.FORESHADOW,
                title = item.title,
                openedChapter = old?.openedChapter ?: item.plantedChapter.coerceAtLeast(1),
                dueStartChapter = dueStart,
                dueEndChapter = dueEnd,
                lastTouchedChapter = if (item.id in touched) current else old?.lastTouchedChapter ?: item.plantedChapter,
                priority = when (status) {
                    NarrativeDebtStatus.OVERDUE -> 100
                    NarrativeDebtStatus.DUE -> 85
                    NarrativeDebtStatus.OPEN -> 65
                    NarrativeDebtStatus.RESOLVED -> 0
                },
                status = status,
                evidence = item.detail.take(360),
                resolutionCriteria = item.expectedPayoff.take(360),
            )
        }

        snapshot.longForm.autonomousPlan.characterTargets.forEach { target ->
            val id = "character:${target.name}:${target.targetChapter}"
            val old = byId[id]
            val reached = current >= target.targetChapter
            val character = snapshot.characters.firstOrNull { it.name == target.name }
            val resolved = reached && character != null && character.lastUpdatedChapter >= target.targetChapter
            byId[id] = NarrativeDebt(
                id = id,
                kind = NarrativeDebtKind.CHARACTER_ARC,
                title = "${target.name}：${target.desiredChange}",
                openedChapter = old?.openedChapter ?: snapshot.longForm.autonomousPlan.baseChapter.coerceAtLeast(1),
                dueStartChapter = target.targetChapter,
                dueEndChapter = target.targetChapter,
                lastTouchedChapter = maxOf(old?.lastTouchedChapter ?: 0, character?.lastUpdatedChapter ?: 0),
                priority = if (resolved) 0 else if (reached) 90 else 60,
                status = debtStatus(current, target.targetChapter, target.targetChapter, resolved),
                evidence = target.pressure.take(320),
                resolutionCriteria = target.desiredChange.take(320),
            )
        }

        if (execution.status in setOf(PlanExecutionStatus.PARTIAL, PlanExecutionStatus.DEVIATED)) {
            val id = "plot:${current}:${execution.plannedObjective.hashCode()}"
            byId[id] = NarrativeDebt(
                id = id,
                kind = NarrativeDebtKind.PLOT_PROMISE,
                title = "第${current}章未完全兑现的计划目标",
                openedChapter = current,
                dueStartChapter = current + 1,
                dueEndChapter = current + if (execution.status == PlanExecutionStatus.DEVIATED) 3 else 2,
                lastTouchedChapter = current,
                priority = if (execution.status == PlanExecutionStatus.DEVIATED) 95 else 78,
                status = NarrativeDebtStatus.OPEN,
                evidence = execution.deviations.joinToString("；").take(420),
                resolutionCriteria = execution.repairHint.ifBlank { execution.plannedObjective }.take(420),
            )
        }

        return byId.values
            .map { debt ->
                if (debt.status == NarrativeDebtStatus.RESOLVED) debt
                else debt.copy(status = debtStatus(current, debt.dueStartChapter, debt.dueEndChapter, false))
            }
            .sortedWith(compareByDescending<NarrativeDebt> { it.status == NarrativeDebtStatus.OVERDUE }
                .thenByDescending { it.priority }
                .thenBy { it.dueEndChapter.takeIf { end -> end > 0 } ?: Int.MAX_VALUE })
            .take(120)
    }

    private fun localAssessment(
        objective: String,
        turningPoint: String,
        chapterNumber: Int,
        generated: GeneratedChapter,
    ): ChapterExecutionRecord {
        val actual = (generated.summary + "\n" + generated.content.take(4_000)).lowercase()
        val expected = (objective + " " + turningPoint).trim().lowercase()
        val score = semanticSurfaceScore(expected, actual)
        val status = when {
            score >= 78 -> PlanExecutionStatus.ALIGNED
            score >= 55 -> PlanExecutionStatus.PARTIAL
            else -> PlanExecutionStatus.DEVIATED
        }
        return ChapterExecutionRecord(
            chapterNumber = chapterNumber,
            plannedObjective = objective,
            actualSummary = generated.summary.trim().ifBlank { fallbackSummary(generated.content) },
            status = status,
            completionScore = score,
            deviations = if (status == PlanExecutionStatus.ALIGNED) emptyList() else listOf("本地保守校验认为正文与滚动计划的核心语义覆盖不足。"),
            affectedFutureChapters = when (status) {
                PlanExecutionStatus.DEVIATED -> futureWindow(chapterNumber, 3)
                PlanExecutionStatus.PARTIAL -> futureWindow(chapterNumber, 2)
                else -> emptyList()
            },
            repairHint = if (status == PlanExecutionStatus.ALIGNED) "" else "以后续已发生事实为起点，只重算受影响章节，不回滚正文。",
            recordedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        fun shouldSelectiveReplan(execution: ChapterExecutionRecord): Boolean =
            execution.status in setOf(PlanExecutionStatus.PARTIAL, PlanExecutionStatus.DEVIATED, PlanExecutionStatus.UNPLANNED) &&
                execution.affectedFutureChapters.isNotEmpty()

        fun revealBudget(snapshot: StorySnapshot, chapterNumber: Int): RevealBudget {
            val full = mutableListOf<String>()
            val partial = mutableListOf<String>()
            val forbidden = mutableListOf<String>()
            snapshot.knowledgeLedger.forEach { boundary ->
                if (boundary.readerState == ReaderKnowledgeState.KNOWN) return@forEach
                val fullTimeReached = boundary.earliestFullRevealChapter <= 0 || chapterNumber >= boundary.earliestFullRevealChapter
                when (boundary.revealPolicy) {
                    KnowledgeRevealPolicy.FULL -> if (fullTimeReached) full += boundary.id else forbidden += boundary.id
                    KnowledgeRevealPolicy.PARTIAL -> partial += boundary.id
                    KnowledgeRevealPolicy.HINT_ONLY -> partial += boundary.id
                    KnowledgeRevealPolicy.HIDDEN -> forbidden += boundary.id
                }
            }
            return RevealBudget(
                chapterNumber = chapterNumber,
                maxFullReveals = if (full.isEmpty()) 0 else 1,
                maxPartialReveals = partial.size.coerceIn(0, 2),
                allowedFullBoundaryIds = full.take(4),
                allowedPartialBoundaryIds = partial.take(8),
                forbiddenBoundaryIds = forbidden.take(24),
            )
        }

        fun planningContext(snapshot: StorySnapshot, chapterNumber: Int): String {
            val debt = snapshot.longForm.narrativeDebts
                .filter { it.status != NarrativeDebtStatus.RESOLVED }
                .sortedWith(compareByDescending<NarrativeDebt> { it.status == NarrativeDebtStatus.OVERDUE }.thenByDescending { it.priority })
                .take(10)
            val budget = revealBudget(snapshot, chapterNumber)
            val execution = snapshot.longForm.executionHistory.lastOrNull()
            return buildString {
                appendLine("【剧情债务｜只施加规划压力，不得借此修改 Canon】")
                if (debt.isEmpty()) appendLine("- 暂无明确未偿债务。")
                debt.forEach { item ->
                    appendLine("- [${item.status}/${item.kind}] ${item.title}｜截止=${item.dueStartChapter}-${item.dueEndChapter}｜优先=${item.priority}｜兑现标准=${item.resolutionCriteria}")
                }
                appendLine("【第${chapterNumber}章信息揭露预算】")
                appendLine("- 完整揭露最多 ${budget.maxFullReveals} 条；部分/暗示最多 ${budget.maxPartialReveals} 条。")
                if (budget.allowedFullBoundaryIds.isNotEmpty()) appendLine("- 可完整揭露 boundaryId：${budget.allowedFullBoundaryIds.joinToString("、")}")
                if (budget.allowedPartialBoundaryIds.isNotEmpty()) appendLine("- 只可部分/暗示 boundaryId：${budget.allowedPartialBoundaryIds.joinToString("、")}")
                if (budget.forbiddenBoundaryIds.isNotEmpty()) appendLine("- 禁止揭底 boundaryId：${budget.forbiddenBoundaryIds.joinToString("、")}")
                execution?.takeIf { it.status != PlanExecutionStatus.ALIGNED }?.let {
                    appendLine("【最近一次计划偏差】第${it.chapterNumber}章 ${it.status} ${it.completionScore}分｜${it.repairHint}")
                }
            }.take(5_500)
        }

        private fun debtStatus(current: Int, start: Int, end: Int, resolved: Boolean): NarrativeDebtStatus {
            if (resolved) return NarrativeDebtStatus.RESOLVED
            if (end > 0 && current > end) return NarrativeDebtStatus.OVERDUE
            if (start > 0 && current >= start) return NarrativeDebtStatus.DUE
            return NarrativeDebtStatus.OPEN
        }

        private fun semanticSurfaceScore(expected: String, actual: String): Int {
            if (expected.isBlank()) return 80
            if (actual.contains(expected)) return 96
            fun grams(text: String): Set<String> {
                val clean = text.replace(Regex("[\\s，。！？；：、,.!?;:\\-—_]+"), "")
                if (clean.length < 2) return setOf(clean).filter { it.isNotBlank() }.toSet()
                return (0 until clean.length - 1).map { clean.substring(it, it + 2) }.toSet()
            }
            val a = grams(expected)
            val b = grams(actual)
            if (a.isEmpty()) return 75
            val recall = a.count { it in b }.toDouble() / a.size.toDouble()
            return (45 + recall * 50).roundToInt().coerceIn(40, 95)
        }

        private fun futureWindow(chapter: Int, count: Int): List<Int> =
            (chapter + 1..chapter + count.coerceIn(1, 6)).toList()

        private fun fallbackSummary(text: String): String {
            val compact = text.replace(Regex("\\s+"), " ").trim()
            return if (compact.length <= 220) compact else compact.take(217) + "…"
        }
    }
}
