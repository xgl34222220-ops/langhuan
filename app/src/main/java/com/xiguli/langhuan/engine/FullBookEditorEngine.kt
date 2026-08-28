package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BookEditorIssue
import com.xiguli.langhuan.domain.BookEditorIssueKind
import com.xiguli.langhuan.domain.BookEditorSeverity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.FullBookEditorReport
import com.xiguli.langhuan.domain.LongFormHealthLevel
import com.xiguli.langhuan.domain.NarrativeDebtKind
import com.xiguli.langhuan.domain.NarrativeDebtStatus
import com.xiguli.langhuan.domain.PlanExecutionStatus
import com.xiguli.langhuan.domain.StorySnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Persistent whole-book editor.
 *
 * The local pass is deliberately cheap and deterministic, so it can run after chapter commits without
 * spending model calls. A manual AI audit can then be merged as semantic evidence. Neither path edits
 * prose or Canon; the result is planning/style pressure only.
 */
class FullBookEditorEngine {
    fun localAudit(
        snapshot: StorySnapshot,
        chapters: List<ChapterDraft>,
        now: Long = System.currentTimeMillis(),
    ): FullBookEditorReport {
        val written = chapters.filter { it.content.isNotBlank() }.sortedBy { it.chapterNumber }.takeLast(120)
        if (written.isEmpty()) return FullBookEditorReport(updatedAt = now)
        val currentChapter = written.last().chapterNumber
        val issues = buildList {
            addAll(structuralFatigue(written))
            addAll(patternRepetition(written))
            addAll(characterVoiceConvergence(snapshot, written))
            addAll(suspenseDensity(snapshot, written))
            addAll(subplotAbsence(snapshot, currentChapter))
            addAll(styleDrift(snapshot, written))
            addAll(lowChangeStreak(snapshot, written))
        }.distinctBy { it.kind to it.title }.sortedWith(
            compareByDescending<BookEditorIssue> { severityWeight(it.severity) }
                .thenByDescending { it.chapterEnd }
        ).take(24)

        fun scoreFor(kinds: Set<BookEditorIssueKind>): Int {
            var score = 100
            issues.filter { it.kind in kinds }.forEach {
                score -= when (it.severity) {
                    BookEditorSeverity.HIGH -> 22
                    BookEditorSeverity.WATCH -> 11
                    BookEditorSeverity.INFO -> 4
                }
            }
            return score.coerceIn(0, 100)
        }

        val structureScore = scoreFor(setOf(BookEditorIssueKind.STRUCTURAL_FATIGUE, BookEditorIssueKind.LOW_CHANGE_STREAK))
        val varietyScore = scoreFor(setOf(BookEditorIssueKind.PATTERN_REPETITION))
        val voiceScore = scoreFor(setOf(BookEditorIssueKind.CHARACTER_VOICE_CONVERGENCE))
        val suspenseScore = scoreFor(setOf(BookEditorIssueKind.SUSPENSE_DENSITY))
        val subplotScore = scoreFor(setOf(BookEditorIssueKind.SUBPLOT_ABSENCE))
        val styleScore = scoreFor(setOf(BookEditorIssueKind.STYLE_DRIFT))
        val score = listOf(structureScore, varietyScore, voiceScore, suspenseScore, subplotScore, styleScore)
            .average().roundToInt().coerceIn(0, 100)
        val level = when {
            score >= 84 -> LongFormHealthLevel.HEALTHY
            score >= 64 -> LongFormHealthLevel.WATCH
            else -> LongFormHealthLevel.RISK
        }
        return FullBookEditorReport(
            lastAuditChapter = currentChapter,
            scannedChapterStart = written.first().chapterNumber,
            scannedChapterEnd = currentChapter,
            scannedChapterCount = written.size,
            score = score,
            level = level,
            structureScore = structureScore,
            varietyScore = varietyScore,
            characterVoiceScore = voiceScore,
            suspenseScore = suspenseScore,
            subplotScore = subplotScore,
            styleScore = styleScore,
            issues = issues,
            updatedAt = now,
        )
    }

    /** Adds semantic findings from the existing full-book Agent without letting AI overwrite local facts. */
    fun mergeAgentReview(
        local: FullBookEditorReport,
        review: AgentReview,
        now: Long = System.currentTimeMillis(),
    ): FullBookEditorReport {
        val aiIssues = review.diagnostics.mapNotNull { action ->
            val kind = classifyAiIssue(action.subject + " " + action.before + " " + action.after)
            val range = parseChapterRange(action.evidence + " " + action.before)
            val severity = when {
                action.evidence.contains("高", true) || action.evidence.contains("严重", true) -> BookEditorSeverity.HIGH
                else -> BookEditorSeverity.WATCH
            }
            BookEditorIssue(
                id = "ai:${kind.name.lowercase()}:${stableKey(action.subject + action.evidence)}",
                kind = kind,
                severity = severity,
                title = action.subject.ifBlank { kind.label() },
                chapterStart = range.first,
                chapterEnd = range.second,
                evidence = action.evidence.ifBlank { action.before }.take(520),
                diagnosis = action.before.take(420),
                minimalRepair = action.after.take(520),
                source = "AI深度巡检",
            ).takeIf { it.title.isNotBlank() || it.evidence.isNotBlank() }
        }
        val merged = (local.issues + aiIssues)
            .distinctBy { it.kind to normalize(it.title) }
            .sortedWith(compareByDescending<BookEditorIssue> { severityWeight(it.severity) }.thenByDescending { it.chapterEnd })
            .take(28)
        var score = local.score
        val extraHigh = aiIssues.count { it.severity == BookEditorSeverity.HIGH && local.issues.none { old -> old.kind == it.kind } }
        val extraWatch = aiIssues.count { it.severity == BookEditorSeverity.WATCH && local.issues.none { old -> old.kind == it.kind } }
        score = (score - extraHigh * 6 - extraWatch * 2).coerceIn(0, 100)
        return local.copy(
            score = score,
            level = when {
                score >= 84 -> LongFormHealthLevel.HEALTHY
                score >= 64 -> LongFormHealthLevel.WATCH
                else -> LongFormHealthLevel.RISK
            },
            issues = merged,
            aiSummary = listOf(review.metrics, review.summary).filter { it.isNotBlank() }.joinToString("\n").take(1_500),
            updatedAt = now,
        )
    }

    fun apply(snapshot: StorySnapshot, report: FullBookEditorReport): StorySnapshot =
        snapshot.copy(longForm = snapshot.longForm.copy(editorReport = report))

    companion object {
        fun shouldAudit(snapshot: StorySnapshot, chapterNumber: Int): Boolean {
            if (!snapshot.longForm.config.enabled) return false
            val last = snapshot.longForm.editorReport.lastAuditChapter
            val interval = snapshot.longForm.config.auditInterval.coerceIn(10, 50)
            return last == 0 || chapterNumber - last >= interval
        }

        /** Guidance only. It never carries hidden truth and must never override chapter contracts or Canon. */
        fun promptText(snapshot: StorySnapshot): String {
            val report = snapshot.longForm.editorReport
            if (report.lastAuditChapter <= 0 || report.issues.isEmpty()) return ""
            val active = report.issues
                .filter { it.severity != BookEditorSeverity.INFO }
                .sortedByDescending { severityWeight(it.severity) }
                .take(6)
            if (active.isEmpty()) return ""
            return buildString {
                appendLine("【全书主编提醒｜只纠正长期写作模式，不得修改 Canon/章节合同】")
                active.forEach { issue ->
                    appendLine("- ${issue.severity}/${issue.kind}：${issue.title}｜最小修复=${issue.minimalRepair.ifBlank { issue.diagnosis }}")
                }
            }.take(2_800)
        }

        private fun severityWeight(value: BookEditorSeverity): Int = when (value) {
            BookEditorSeverity.HIGH -> 3
            BookEditorSeverity.WATCH -> 2
            BookEditorSeverity.INFO -> 1
        }
    }

    private fun structuralFatigue(chapters: List<ChapterDraft>): List<BookEditorIssue> {
        val recent = chapters.takeLast(10)
        if (recent.size < 6) return emptyList()
        var similarAdjacent = 0
        for (i in 1 until recent.size) {
            val a = recent[i - 1].objective.ifBlank { recent[i - 1].summary }
            val b = recent[i].objective.ifBlank { recent[i].summary }
            if (similarity(a, b) >= 0.64) similarAdjacent++
        }
        if (similarAdjacent < 4) return emptyList()
        return listOf(
            issue(
                kind = BookEditorIssueKind.STRUCTURAL_FATIGUE,
                severity = if (similarAdjacent >= 6) BookEditorSeverity.HIGH else BookEditorSeverity.WATCH,
                title = "连续章节主目标过于同构",
                start = recent.first().chapterNumber,
                end = recent.last().chapterNumber,
                evidence = "最近${recent.size}章中有 $similarAdjacent 组相邻章节的目标/摘要高度相似。",
                diagnosis = "章节可能持续重复同一种调查、追问、遭遇或验证动作，阶段性变化不足。",
                repair = "后续只改变受影响的滚动计划：至少让一章产生不可逆代价、关系变化或目标切换，不新增同等级主线。",
            )
        )
    }

    private fun patternRepetition(chapters: List<ChapterDraft>): List<BookEditorIssue> {
        val recent = chapters.takeLast(12)
        if (recent.size < 6) return emptyList()
        var openingPairs = 0
        var endingPairs = 0
        for (i in recent.indices) {
            for (j in 0 until i) {
                if (similarity(recent[i].content.take(320), recent[j].content.take(320)) >= 0.68) openingPairs++
                if (similarity(recent[i].content.takeLast(320), recent[j].content.takeLast(320)) >= 0.68) endingPairs++
            }
        }
        if (openingPairs < 3 && endingPairs < 3) return emptyList()
        val focus = when {
            openingPairs >= 3 && endingPairs >= 3 -> "开场与章末"
            openingPairs >= 3 -> "开场"
            else -> "章末钩子"
        }
        return listOf(
            issue(
                BookEditorIssueKind.PATTERN_REPETITION,
                if (openingPairs + endingPairs >= 8) BookEditorSeverity.HIGH else BookEditorSeverity.WATCH,
                "$focus 出现重复模板",
                recent.first().chapterNumber,
                recent.last().chapterNumber,
                "近${recent.size}章：开场相似对=$openingPairs，章末相似对=$endingPairs。",
                "读者可能开始预判章节节奏，悬念和动作失去新鲜感。",
                "下一窗口改变信息获得方式和章末因果类型；不要只换地点/名字后重复同一种节拍。",
            )
        )
    }

    private fun characterVoiceConvergence(snapshot: StorySnapshot, chapters: List<ChapterDraft>): List<BookEditorIssue> {
        if (snapshot.characters.size < 2) return emptyList()
        val text = chapters.takeLast(16).joinToString("\n") { it.content }
        val samples = snapshot.characters.take(10).associate { character ->
            character.name to attributedDialogue(text, character.name).takeLast(24)
        }.filterValues { it.size >= 5 }
        if (samples.size < 2) return emptyList()
        val pairs = mutableListOf<String>()
        val entries = samples.entries.toList()
        for (i in entries.indices) {
            for (j in 0 until i) {
                val sim = voiceSimilarity(entries[i].value, entries[j].value)
                if (sim >= 0.88) pairs += "${entries[j].key}/${entries[i].key}"
            }
        }
        if (pairs.isEmpty()) return emptyList()
        return listOf(
            issue(
                BookEditorIssueKind.CHARACTER_VOICE_CONVERGENCE,
                if (pairs.size >= 3) BookEditorSeverity.HIGH else BookEditorSeverity.WATCH,
                "主要人物对白声线趋同",
                chapters.takeLast(16).first().chapterNumber,
                chapters.last().chapterNumber,
                "本地对白指纹相似人物：${pairs.take(5).joinToString("、")}。",
                "不同人物的句长、疑问/感叹比例和停顿方式正在接近；这是保守提示，不据此改人物事实。",
                "后续对话场景优先恢复人物独有词汇、回避方式、句长和决策习惯；不要用口癖硬贴标签。",
            )
        )
    }

    private fun suspenseDensity(snapshot: StorySnapshot, chapters: List<ChapterDraft>): List<BookEditorIssue> {
        val recent = chapters.takeLast(8)
        if (recent.size < 5) return emptyList()
        val contracts = recent.map { chapter ->
            snapshot.outline.firstOrNull { it.order == chapter.chapterNumber }?.chapterContract ?: chapter.contract
        }
        val emptySuspense = contracts.count { it.reveals.isEmpty() && it.foreshadowing.isEmpty() && it.hookOut.isBlank() }
        val revealHeavy = contracts.count { it.reveals.size >= 3 }
        return when {
            emptySuspense >= 5 -> listOf(
                issue(
                    BookEditorIssueKind.SUSPENSE_DENSITY,
                    BookEditorSeverity.WATCH,
                    "连续多章缺少新的信息债或章末问题",
                    recent.first().chapterNumber,
                    recent.last().chapterNumber,
                    "最近${recent.size}章有 $emptySuspense 章的合同没有 reveal/foreshadow/hook。",
                    "主线可能在推进事件，却没有同步推进读者期待。",
                    "未来3章至少安排一次新问题、一次旧线索升级或一次有代价的答案；不要靠突然新增幕后势力制造悬念。",
                )
            )
            revealHeavy >= 4 -> listOf(
                issue(
                    BookEditorIssueKind.SUSPENSE_DENSITY,
                    BookEditorSeverity.WATCH,
                    "秘密揭露密度过高",
                    recent.first().chapterNumber,
                    recent.last().chapterNumber,
                    "最近${recent.size}章有 $revealHeavy 章计划一次揭露3条以上信息。",
                    "连续兑现会快速耗尽信息差，并让重要答案彼此抢戏。",
                    "降低下一窗口完整揭露数量，把次要答案改为证据/局部确认，严格服从信息揭露预算。",
                )
            )
            else -> emptyList()
        }
    }

    private fun subplotAbsence(snapshot: StorySnapshot, currentChapter: Int): List<BookEditorIssue> {
        val stale = snapshot.longForm.narrativeDebts.filter {
            it.status != NarrativeDebtStatus.RESOLVED &&
                it.kind in setOf(NarrativeDebtKind.RELATIONSHIP, NarrativeDebtKind.CHARACTER_ARC, NarrativeDebtKind.PLOT_PROMISE) &&
                currentChapter - maxOf(it.lastTouchedChapter, it.openedChapter) >= 12
        }
        val staleCharacters = snapshot.characters.filter { currentChapter - it.lastUpdatedChapter >= 24 }
        if (stale.isEmpty() && staleCharacters.isEmpty()) return emptyList()
        return listOf(
            issue(
                BookEditorIssueKind.SUBPLOT_ABSENCE,
                if (stale.any { currentChapter - maxOf(it.lastTouchedChapter, it.openedChapter) >= 24 }) BookEditorSeverity.HIGH else BookEditorSeverity.WATCH,
                "支线/关系长期失去推进",
                maxOf(1, currentChapter - 30),
                currentChapter,
                buildString {
                    if (stale.isNotEmpty()) append("长期未触及债务：${stale.take(5).joinToString("、") { it.title }}。")
                    if (staleCharacters.isNotEmpty()) append(" 长期未更新人物：${staleCharacters.take(5).joinToString("、") { it.name }}。")
                },
                "这些线索不一定要立刻回收，但长期完全消失会让前期承诺显得被遗忘。",
                "在不打断主线的前提下，用场景后果、关系反应或短触点重新建立存在感；不要为补支线单独硬开新章。",
            )
        )
    }

    private fun styleDrift(snapshot: StorySnapshot, chapters: List<ChapterDraft>): List<BookEditorIssue> {
        val recent = chapters.takeLast(6)
        val prior = chapters.dropLast(recent.size).takeLast(6)
        if (recent.size < 4 || prior.size < 4) return emptyList()
        val recentText = recent.joinToString("\n") { it.content.take(5_000) }
        val priorText = prior.joinToString("\n") { it.content.take(5_000) }
        val recentMetric = styleMetric(recentText)
        val priorMetric = styleMetric(priorText)
        val drift = mutableListOf<String>()
        if (abs(recentMetric.avgSentence - priorMetric.avgSentence) >= 7.0) drift += "平均句长 ${priorMetric.avgSentence.roundToInt()}→${recentMetric.avgSentence.roundToInt()}"
        if (abs(recentMetric.dialogueRatio - priorMetric.dialogueRatio) >= 0.08) drift += "对白占比变化明显"
        if (recentMetric.explanationRate >= priorMetric.explanationRate + 0.004) drift += "解释性总结句增多"
        if (recentMetric.clicheRate >= priorMetric.clicheRate + 0.005) drift += "模板化氛围词增多"

        val learned = snapshot.longForm.authorProfile.rules.filter { it.active && it.confidence >= 60 }.map { it.id }.toSet()
        if ("less-explanation" in learned && recentMetric.explanationRate > 0.008) drift += "再次触发作者已明确的‘少解释’偏好"
        if ("less-ai-cliche" in learned && recentMetric.clicheRate > 0.012) drift += "再次触发作者已明确的‘少AI腔’偏好"
        if (drift.isEmpty()) return emptyList()
        return listOf(
            issue(
                BookEditorIssueKind.STYLE_DRIFT,
                if (drift.size >= 3) BookEditorSeverity.HIGH else BookEditorSeverity.WATCH,
                "近期叙事声音出现持续漂移",
                prior.first().chapterNumber,
                recent.last().chapterNumber,
                drift.joinToString("；"),
                "漂移来自连续窗口统计，不代表单句必须统一；重点检查是否无意偏离作品既定声音。",
                "下一章优先恢复已确认文风和高置信作者画像，禁止为了‘统一风格’改剧情事实或人物认知。",
            )
        )
    }

    private fun lowChangeStreak(snapshot: StorySnapshot, chapters: List<ChapterDraft>): List<BookEditorIssue> {
        val recent = snapshot.longForm.executionHistory.takeLast(6)
        if (recent.size < 4) return emptyList()
        val weak = recent.count { it.completionScore < 65 || it.status in setOf(PlanExecutionStatus.PARTIAL, PlanExecutionStatus.DEVIATED) }
        if (weak < 4) return emptyList()
        return listOf(
            issue(
                BookEditorIssueKind.LOW_CHANGE_STREAK,
                if (weak >= 5) BookEditorSeverity.HIGH else BookEditorSeverity.WATCH,
                "连续多章计划完成度偏低",
                recent.first().chapterNumber,
                recent.last().chapterNumber,
                "最近${recent.size}次计划执行中有 $weak 次低于65分或发生明显偏差。",
                "作品可能在靠局部场景惯性前进，而不是稳定完成阶段目标。",
                "只重算被偏差影响的未来章节，并强制下一章形成一个可验证状态变化；不要推翻未受影响的计划。",
            )
        )
    }

    private data class StyleMetric(
        val avgSentence: Double,
        val dialogueRatio: Double,
        val explanationRate: Double,
        val clicheRate: Double,
    )

    private fun styleMetric(text: String): StyleMetric {
        val sentences = text.split(Regex("[。！？!?]+" )).map(String::trim).filter(String::isNotBlank)
        val avg = if (sentences.isEmpty()) 0.0 else sentences.sumOf { it.length }.toDouble() / sentences.size
        val safe = text.length.coerceAtLeast(1).toDouble()
        val quotes = text.count { it == '“' || it == '”' || it == '「' || it == '」' }
        val explanation = listOf("这说明", "这意味着", "显然", "也就是说", "换句话说", "由此可见").sumOf { countLiteral(text, it) }
        val cliches = listOf("仿佛", "似乎", "那一刻", "某种", "无声地", "缓缓", "微微", "猛地", "不由得", "空气中").sumOf { countLiteral(text, it) }
        return StyleMetric(avg, quotes / safe, explanation / safe, cliches / safe)
    }

    private fun attributedDialogue(text: String, name: String): List<String> {
        if (name.isBlank()) return emptyList()
        val regex = Regex("“([^”]{2,120})”")
        return regex.findAll(text).mapNotNull { match ->
            val prefix = text.substring((match.range.first - 48).coerceAtLeast(0), match.range.first)
            match.groupValues[1].takeIf { prefix.contains(name) }
        }.toList()
    }

    private fun voiceSimilarity(a: List<String>, b: List<String>): Double {
        fun fp(lines: List<String>): List<Double> {
            val joined = lines.joinToString("。")
            val lengths = lines.map { it.length.toDouble() }
            val n = joined.length.coerceAtLeast(1).toDouble()
            return listOf(
                (lengths.average() / 40.0).coerceIn(0.0, 1.0),
                joined.count { it == '？' || it == '?' } / n * 20.0,
                joined.count { it == '！' || it == '!' } / n * 20.0,
                joined.count { it == '，' || it == ',' } / n * 8.0,
            ).map { it.coerceIn(0.0, 1.0) }
        }
        val x = fp(a)
        val y = fp(b)
        val distance = x.indices.sumOf { abs(x[it] - y[it]) } / x.size
        return (1.0 - distance).coerceIn(0.0, 1.0)
    }

    private fun similarity(a: String, b: String): Double {
        val ga = grams(a)
        val gb = grams(b)
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        return ga.intersect(gb).size.toDouble() / minOf(ga.size, gb.size).toDouble()
    }

    private fun grams(text: String): Set<String> {
        val clean = normalize(text)
        if (clean.length < 2) return emptySet()
        return (0 until clean.length - 1).map { clean.substring(it, it + 2) }.toSet()
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("第\\d+章|\\s+|[，。！？、；：,.!?;:\\-—_（）()“”‘’]"), "")
        .take(1_600)

    private fun classifyAiIssue(text: String): BookEditorIssueKind {
        val t = text.lowercase()
        return when {
            listOf("声线", "对白", "口吻", "voice").any { t.contains(it) } -> BookEditorIssueKind.CHARACTER_VOICE_CONVERGENCE
            listOf("支线", "关系线", "失踪", "长期未", "弧光断裂").any { t.contains(it) } -> BookEditorIssueKind.SUBPLOT_ABSENCE
            listOf("悬念", "揭露", "信息密度", "秘密", "伏笔密度").any { t.contains(it) } -> BookEditorIssueKind.SUSPENSE_DENSITY
            listOf("文风", "语气", "句式", "ai腔", "风格漂移").any { t.contains(it) } -> BookEditorIssueKind.STYLE_DRIFT
            listOf("重复", "模板", "同一种", "套路").any { t.contains(it) } -> BookEditorIssueKind.PATTERN_REPETITION
            listOf("拖沓", "疲劳", "结构", "阶段目标", "主线推进").any { t.contains(it) } -> BookEditorIssueKind.STRUCTURAL_FATIGUE
            else -> BookEditorIssueKind.LOW_CHANGE_STREAK
        }
    }

    private fun parseChapterRange(text: String): Pair<Int, Int> {
        val numbers = Regex("第?(\\d+)章").findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        return if (numbers.isEmpty()) 0 to 0 else numbers.minOrNull()!! to numbers.maxOrNull()!!
    }

    private fun issue(
        kind: BookEditorIssueKind,
        severity: BookEditorSeverity,
        title: String,
        start: Int,
        end: Int,
        evidence: String,
        diagnosis: String,
        repair: String,
    ) = BookEditorIssue(
        id = "local:${kind.name.lowercase()}:$start:$end:${stableKey(title)}",
        kind = kind,
        severity = severity,
        title = title,
        chapterStart = start,
        chapterEnd = end,
        evidence = evidence.take(520),
        diagnosis = diagnosis.take(420),
        minimalRepair = repair.take(520),
        source = "本地巡检",
    )

    private fun countLiteral(text: String, token: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(token, index)
            if (index < 0) return count
            count++
            index += token.length.coerceAtLeast(1)
        }
    }

    private fun stableKey(text: String): String = text.hashCode().toUInt().toString(16)

    private fun BookEditorIssueKind.label(): String = when (this) {
        BookEditorIssueKind.STRUCTURAL_FATIGUE -> "结构疲劳"
        BookEditorIssueKind.PATTERN_REPETITION -> "套路重复"
        BookEditorIssueKind.CHARACTER_VOICE_CONVERGENCE -> "人物声线趋同"
        BookEditorIssueKind.SUSPENSE_DENSITY -> "悬念密度"
        BookEditorIssueKind.SUBPLOT_ABSENCE -> "支线失踪"
        BookEditorIssueKind.STYLE_DRIFT -> "文风漂移"
        BookEditorIssueKind.LOW_CHANGE_STREAK -> "连续低变化"
    }
}