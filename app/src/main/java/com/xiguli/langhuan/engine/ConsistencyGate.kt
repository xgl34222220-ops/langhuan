package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity

class ConsistencyGate(
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
    private val eraTechnologyGuard: EraTechnologyGuard = EraTechnologyGuard(),
) {
    fun inspect(request: GenerationRequest, output: GeneratedChapter): List<ConsistencyIssue> {
        val issues = mutableListOf<ConsistencyIssue>()
        val text = output.content

        if (text.isBlank()) {
            issues += blocking("EMPTY_CONTENT", "AI 没有返回正文", "重新生成本章正文")
            return issues
        }

        if (request.chapter.objective.isBlank()) {
            issues += blocking("MISSING_OBJECTIVE", "本章没有明确目标", "先补全章纲目标再生成")
        }

        val visibleLength = storyLength(text)
        val hardMinimum = maxOf(480, (request.targetWords * 0.48).toInt())
        val expectedMinimum = maxOf(700, (request.targetWords * 0.72).toInt())
        when {
            request.targetWords < 800 -> Unit
            visibleLength < hardMinimum -> issues += blocking(
                code = "CHAPTER_SEVERELY_UNDERSIZED",
                message = "正文只有约${visibleLength}字，远未完成约${request.targetWords}字的章节任务",
                repair = "保持本章目标和因果骨架不变，补足完整场景、人物反应、冲突过程和结果；禁止用解释或重复信息凑字。",
                evidence = "最低可交付约${hardMinimum}字",
            )
            visibleLength < expectedMinimum -> issues += ConsistencyIssue(
                severity = IssueSeverity.WARNING,
                code = "CHAPTER_UNDERSIZED",
                message = "正文约${visibleLength}字，明显低于约${request.targetWords}字的目标",
                evidence = "建议至少达到约${expectedMinimum}字",
                repairInstruction = "检查是否缺少场景发展、人物选择、冲突代价或章末结果，不要机械扩写。",
            )
        }

        issues += chronologyGuard.inspect(request, output)
        issues += eraTechnologyGuard.inspect(request, output)
        issues += NarrativeRuleGuard.inspect(text)
        issues += ChapterContractGuard.inspect(request, output)
        issues += RevealBudgetGuard.inspect(request, text)

        // 生成后的正文再跑一次确定性时间体检。这里只把“明确钟点自相矛盾”这类无歧义问题升级为阻断，
        // 避免 04:03 / 03:21 却声称“一模一样”这种文本进入版本库和长期记忆。
        ChronologyRepairAnalyzer.analyze(request.snapshot, text)
            .findings
            .filter { it.code == "CLOCK_EQUIVALENCE_MISMATCH" }
            .forEach { finding ->
                issues += blocking(
                    code = finding.code,
                    message = finding.title,
                    repair = finding.repair,
                    evidence = finding.evidence,
                )
            }

        request.snapshot.bible
            .filter { it.category == BibleCategory.FORBIDDEN }
            .filter { entry -> entry.aliases.plus(entry.name).any { token -> token.isNotBlank() && text.contains(token) } }
            .forEach { entry ->
                issues += blocking(
                    code = "FORBIDDEN_RULE",
                    message = "正文可能触发禁用设定：${entry.name}",
                    repair = "删除或改写与“${entry.name}”冲突的段落",
                    evidence = entry.content,
                )
            }

        val plannedLocations = request.chapter.scenePlan.map { it.location }.filter { it.isNotBlank() }.toSet()
        request.snapshot.characters.forEach { character ->
            val changedLocation = output.stateChanges.firstOrNull {
                it.subject == character.name && it.field in setOf("location", "地点")
            }
            if (changedLocation != null &&
                plannedLocations.isNotEmpty() &&
                changedLocation.after !in plannedLocations
            ) {
                issues += ConsistencyIssue(
                    severity = IssueSeverity.WARNING,
                    code = "UNPLANNED_LOCATION",
                    message = "${character.name}移动到了场景计划之外的地点：${changedLocation.after}",
                    evidence = changedLocation.evidence,
                    repairInstruction = "补充移动过程，或把人物保持在计划地点",
                )
            }
        }

        val chapterOutline = request.snapshot.activeOutline.lastOrNull()
        chapterOutline?.mustInclude?.forEach { required ->
            if (required.isNotBlank() && !text.contains(required)) {
                issues += ConsistencyIssue(
                    severity = IssueSeverity.WARNING,
                    code = "MISSING_REQUIRED_ELEMENT",
                    message = "章纲要求的内容可能未出现：$required",
                    repairInstruction = "在不破坏节奏的前提下补入“$required”",
                )
            }
        }

        if (output.summary.isBlank()) {
            issues += ConsistencyIssue(
                severity = IssueSeverity.WARNING,
                code = "MISSING_SUMMARY",
                message = "缺少可写入长期记忆的章节摘要",
                repairInstruction = "补充不超过 300 字的事实性摘要，并写明本章结束时的故事日与时段",
            )
        }

        return issues.distinctBy { listOf(it.code, it.message, it.evidence) }
    }

    private fun blocking(
        code: String,
        message: String,
        repair: String,
        evidence: String = "",
    ) = ConsistencyIssue(IssueSeverity.BLOCKING, code, message, evidence, repair)

    private fun storyLength(value: String): Int = value.count { char ->
        !char.isWhitespace() && char !in setOf('`', '#', '*', '_')
    }
}
