package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.IssueSeverity

/**
 * 对正文里明确写出的“硬规则截止点”做保守的确定性检查。
 * 目标不是理解所有自然语言规则，而是拦住类似：
 * “第三次报站前必须下车” -> 第三次报站已经发生 -> 人物之后仍被当作可以正常下车。
 */
object NarrativeRuleGuard {
    private val deadlineRule = Regex(
        "第([一二两三四五六七八九十百\\d]+)次([^，。！？\\n]{1,12})(?:前|之前)必须([^，。！？\\n]{1,28})"
    )
    private val actionKeywords = listOf(
        "下车", "上车", "离开", "逃离", "返回", "进入", "到达", "抵达", "打开", "关闭",
        "交出", "提交", "支付", "完成", "选择", "醒来", "找到", "杀死", "交付", "说出",
    )
    private val invalidated = Regex("不能|无法|来不及|没能|禁止|不得|不再|已经错过|失败|失去资格")

    fun inspect(text: String): List<ConsistencyIssue> {
        if (text.isBlank()) return emptyList()
        val issues = mutableListOf<ConsistencyIssue>()

        deadlineRule.findAll(text).forEach { rule ->
            val ordinal = rule.groupValues[1]
            val event = rule.groupValues[2].trim()
            val required = rule.groupValues[3].trim()
            val action = actionKeywords.firstOrNull { required.contains(it) } ?: return@forEach
            val tail = text.substring(rule.range.last + 1)

            // 同一数字的“第N次 / 第N声 / 第N站”都视为可能到达截止点。
            // 对“报站”这类叙事，模型常把“第三次报站”改写成“广播第三声”，因此不能只匹配原词。
            val boundary = Regex("第${Regex.escape(ordinal)}(?:次|声|站)(?:${Regex.escape(event)})?").find(tail)
                ?: return@forEach
            val afterBoundary = tail.substring(boundary.range.last + 1)
            val actionMatch = Regex(Regex.escape(action)).find(afterBoundary) ?: return@forEach
            val contextStart = (actionMatch.range.first - 12).coerceAtLeast(0)
            val contextEnd = (actionMatch.range.last + 18).coerceAtMost(afterBoundary.length)
            val context = afterBoundary.substring(contextStart, contextEnd)
            if (invalidated.containsMatchIn(context)) return@forEach

            issues += ConsistencyIssue(
                severity = IssueSeverity.BLOCKING,
                code = "RULE_DEADLINE_VIOLATION",
                message = "正文可能违反自己刚建立的硬规则：第${ordinal}次$event 前必须$required",
                evidence = "截止点之后仍出现“$action”：${context.trim()}",
                repairInstruction = "二选一修正：要么把规则改成在第${ordinal}次${event}时/之后执行；要么让“$action”真正发生在截止点之前，并保持后续结果一致。",
            )
        }

        return issues.distinctBy { it.code to it.evidence }
    }
}
