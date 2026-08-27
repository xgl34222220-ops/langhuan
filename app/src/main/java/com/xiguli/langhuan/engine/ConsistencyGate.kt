package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity

class ConsistencyGate {
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
                repairInstruction = "补充不超过 300 字的事实性摘要",
            )
        }

        return issues
    }

    private fun blocking(
        code: String,
        message: String,
        repair: String,
        evidence: String = "",
    ) = ConsistencyIssue(IssueSeverity.BLOCKING, code, message, evidence, repair)
}

