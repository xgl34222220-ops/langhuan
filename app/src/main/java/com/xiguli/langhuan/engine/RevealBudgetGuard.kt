package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.ReaderKnowledgeState

/** Deterministic last line of defense for full-secret disclosures. */
object RevealBudgetGuard {
    fun inspect(request: GenerationRequest, prose: String): List<ConsistencyIssue> {
        if (prose.isBlank() || request.snapshot.knowledgeLedger.isEmpty()) return emptyList()
        val budget = AutonomousExecutionEngine.revealBudget(request.snapshot, request.chapter.chapterNumber)
        val boundaries = request.snapshot.knowledgeLedger.filter { it.readerState != ReaderKnowledgeState.KNOWN }
        val exposed = boundaries.filter { boundary ->
            boundary.triggerTerms
                .map(String::trim)
                .filter { it.length >= 2 }
                .any { term -> prose.contains(term, ignoreCase = true) }
        }
        if (exposed.isEmpty()) return emptyList()

        val issues = mutableListOf<ConsistencyIssue>()
        exposed.filter { it.id in budget.forbiddenBoundaryIds }.forEach { boundary ->
            issues += ConsistencyIssue(
                severity = IssueSeverity.BLOCKING,
                code = "REVEAL_BUDGET_FORBIDDEN",
                message = "第${request.chapter.chapterNumber}章触发了本章禁止揭底的信息：${boundary.title}",
                evidence = boundary.triggerTerms.joinToString("、"),
                repairInstruction = "保留场景压力和线索，但删除完整答案；严格按信息边界改成暗示或延后揭露。",
            )
        }

        val fullExposed = exposed.filter { it.id in budget.allowedFullBoundaryIds }
        if (fullExposed.size > budget.maxFullReveals) {
            issues += ConsistencyIssue(
                severity = IssueSeverity.BLOCKING,
                code = "REVEAL_BUDGET_EXCEEDED",
                message = "本章完整揭露 ${fullExposed.size} 条受保护信息，超过预算 ${budget.maxFullReveals} 条",
                evidence = fullExposed.joinToString("、") { it.title },
                repairInstruction = "只保留最必要的一条完整揭露，其余降级为局部线索或推迟到后续章节。",
            )
        }
        return issues
    }
}
