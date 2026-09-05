package com.xiguli.langhuan.ui.canon

import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.ui.creation.CreationChatMessage
import com.xiguli.langhuan.ui.creation.NewBookProposal
import com.xiguli.langhuan.ui.creation.attachmentContext
import com.xiguli.langhuan.ui.creation.messagesPromptAttachments

/**
 * Rebuilds the current new-book proposal from the latest conversation facts before foundation work.
 *
 * A proposal card is only a cache. The conversation is the source of truth, and later explicit user
 * decisions always override older proposal wording and assistant suggestions.
 */
internal class ProposalConsolidator(
    private val gateway: AiGateway,
) {
    suspend fun consolidate(
        current: NewBookProposal,
        messages: List<CreationChatMessage>,
    ): NewBookProposal {
        val ledger = runCatching { buildDecisionLedger(current, messages) }
            .getOrElse { fallbackLedger(current, messages) }
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书方案合并器。你的任务不是继续聊天，而是把整段会谈里已经确认的最新决定合并成“此刻唯一有效”的新书方案。

                    优先级必须严格遵守：
                    1. 越新的用户明确决定优先级越高；用户后面说“改成/不要/就选B/按这个/不是这样/换掉”时，旧设定立刻失效。
                    2. 用户的话高于琅嬛之前的建议。你收到的“确认事实账本”已经把短句指代和新旧覆盖关系整理好；不得再从旧助手建议里捡设定。
                    3. “当前缓存方案”只是旧基线，可能已经过期；只要与后续用户决定冲突，就必须丢弃旧内容，不能为了保持连续而偷偷复用。
                    4. 如果主角能力、身份、目标、世界规则、核心冲突、故事起点或阅读体验发生变化，平台简介必须基于最新方案重新写，禁止原封不动保留旧简介。
                    5. 参考作品只提炼高层机制，所有角色、专名、规则和剧情骨架必须原创。

                    必须输出完整 GeneratedChapter JSON：
                    - title=2-12字正式书名；
                    - content=完整、连贯的“当前最新”平台简介，篇幅服从故事表达，不机械截断；
                    - summary=足以指导后续蓝图的内部策划摘要；
                    - stateChanges 只返回1项：subject=实际小说类型；field=一句实际主题命题；before=目标总字数纯数字；after=一句话核心钩子；evidence=封面视觉简报；
                    - touchedForeshadowingIds=[]。

                    平台简介必须是一个连贯故事，不是设定清单：交代主角身份与触发事件、眼前目标、阻碍他的核心规则/异常，并用具体代价或危险收住。不要为了字数删掉必要因果，不加“简介/核心钩子/主题”等小标题，不写参考、融合、借鉴过程，不泄露中后期答案。不要解释你做了什么，只返回结构化结果。
                """.trimIndent(),
                user = buildString {
                    appendLine("【当前缓存方案：可能已经过期】")
                    appendLine("书名：${current.title}")
                    appendLine("类型：${current.genre}")
                    appendLine("简介：${current.premise}")
                    appendLine("主题：${current.theme}")
                    appendLine("目标字数：${current.targetWords}")
                    appendLine("核心钩子：${current.coreHook}")
                    appendLine("封面：${current.coverBrief}")
                    appendLine("内部策划：${current.rationale}")
                    if (current.decisionLedger.isNotBlank()) {
                        appendLine("已有确认事实账本：")
                        appendLine(current.decisionLedger)
                    }
                    appendLine()
                    appendLine("【确认事实账本：这是本轮生成唯一允许采用的创作事实】")
                    appendLine(ledger)
                    appendLine()
                    appendLine("【最近会谈，仅用于核对措辞；若与事实账本冲突，以事实账本为准】")
                    appendLine(decisionTranscript(messages))
                },
                attachments = messagesPromptAttachments(messages),
            )
        )

        val meta = output.stateChanges.firstOrNull()
        val title = normalizeTitle(output.title).ifBlank { current.title }
        val premise = normalizeSynopsis(output.content)
        val proposal = NewBookProposal(
            title = title,
            genre = meta?.subject.orEmpty().trim().takeUnless(::isGenrePlaceholder) ?: current.genre,
            premise = premise.ifBlank { current.premise },
            theme = meta?.field.orEmpty().trim().takeUnless(::isThemePlaceholder) ?: current.theme,
            targetWords = meta?.before.orEmpty().filter(Char::isDigit).toIntOrNull()
                ?.coerceIn(10_000, 5_000_000)
                ?: current.targetWords,
            coreHook = meta?.after.orEmpty().trim().ifBlank { current.coreHook },
            coverBrief = meta?.evidence.orEmpty().trim().ifBlank { current.coverBrief },
            rationale = output.summary.trim().ifBlank { current.rationale },
            decisionLedger = ledger,
        )
        return SynopsisQualityEditor(gateway).ensure(proposal, ledger)
    }

    private suspend fun buildDecisionLedger(
        current: NewBookProposal,
        messages: List<CreationChatMessage>,
    ): String {
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是新书会谈事实整理员，不写小说方案、不发挥创意，只整理用户已经确认的决定。
                    时间越新的用户消息优先级越高。“B吧/就这个/换成/不要/他/他们/这两本”等短句必须结合紧邻上下文解析；被用户否定、纠正或替换的旧内容放入“已作废”，绝不能继续算确认事实。
                    助手曾经提出的选项只有在用户明确选择后才成立；研究资料只能说明参考作品，不自动成为新书设定。
                    输出 GeneratedChapter JSON：title="DECISION_LEDGER"；content=完整事实账本，按“确认事实 / 已作废 / 仍未确定”三段书写；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    确认事实必须逐条保留能确认的：题材体验、主角身份/能力/目标、人物关系、故事起点、世界规则、核心冲突、参考作品分别只借鉴什么、用户选中的方案、用户明确禁止什么。信息多就写长一些，绝不能为了压缩篇幅合并掉关键差异。没有确认就写未确定，禁止补空白。
                """.trimIndent(),
                user = buildString {
                    appendLine("【当前缓存，仅作为最早基线】")
                    appendLine("${current.title}｜${current.genre}｜${current.premise}｜${current.theme}｜${current.coreHook}")
                    if (current.decisionLedger.isNotBlank()) {
                        appendLine("已有确认事实账本：")
                        appendLine(current.decisionLedger)
                    }
                    appendLine()
                    appendLine("【按时间顺序的会谈】")
                    appendLine(decisionTranscript(messages))
                },
                attachments = messagesPromptAttachments(messages),
            )
        )
        return output.content.trim().ifBlank { fallbackLedger(current, messages) }
    }

    private fun fallbackLedger(current: NewBookProposal, messages: List<CreationChatMessage>): String = buildString {
        appendLine("确认事实：当前有效方案为《${current.title}》；类型=${current.genre}；主题=${current.theme}；核心钩子=${current.coreHook}。")
        appendLine("最近用户决定：")
        messages.filter { it.role == "user" }.forEach { message ->
            appendLine("- ${message.text.substringBefore(RESEARCH_MARKER).trim()}")
            if (message.attachments.isNotEmpty()) appendLine(attachmentContext(message.attachments))
        }
        appendLine("已作废：凡与更晚用户决定冲突的旧方案。")
        appendLine("仍未确定：会谈中没有被用户明确确认的细节。")
    }

    private fun decisionTranscript(messages: List<CreationChatMessage>): String {
        return messages.joinToString("\n") { message ->
            val plain = if (message.role == "user") {
                message.text.substringBefore(RESEARCH_MARKER).trimEnd()
            } else {
                message.text
            }
            val withAttachments = if (message.attachments.isEmpty()) plain else "$plain\n${attachmentContext(message.attachments)}"
            if (message.role == "user") "用户决定/问题：$withAttachments" else "助手上下文（仅用于解析用户短句，不等于已确认）：$withAttachments"
        }
    }

    private fun normalizeTitle(value: String): String = value.trim()
        .removePrefix("《")
        .removeSuffix("》")
        .replace(Regex("[\n\r]"), "")
        .replace(Regex("[《》“”\"'，,。！？!?：:；;、\\s]"), "")
        .take(12)

    private fun normalizeSynopsis(value: String): String = value.trim()
        .replace(Regex("\\n{3,}"), "\n\n")

    private fun isGenrePlaceholder(value: String): Boolean =
        value.isBlank() || value.equals("小说类型", true) || value.equals("类型", true) ||
            value.equals("题材", true) || value.equals("genre", true)

    private fun isThemePlaceholder(value: String): Boolean =
        value.isBlank() || value.equals("主题命题", true) || value.equals("主题", true) ||
            value.equals("核心主题", true) || value.equals("theme", true)

    private companion object {
        const val RESEARCH_MARKER = "\n\n【琅嬛联网检索资料（隐藏上下文）】"
    }
}

internal object SynopsisQuality {
    fun normalize(value: String): String = value.trim()
        .replace(Regex("^(?:#+\\s*)?(?:平台)?(?:故事)?简介[:：]?\\s*"), "")
        .replace(Regex("(?m)^\\s*(?:[-*•]|\\d+[.、])\\s*"), "")
        .replace(Regex("\\n{2,}"), "\n")

    fun needsRewrite(value: String): Boolean {
        val text = normalize(value)
        val compact = text.filterNot(Char::isWhitespace)
        if (compact.length < 90) return true
        if (Regex("(?:简介|核心钩子|主题|设定融合|参考《|借鉴《|本书将|故事讲述的是)[:：]").containsMatchIn(text)) return true
        if (text.lines().size > 5) return true
        val sentenceCount = Regex("[。！？!?]").findAll(text).count()
        return sentenceCount < 2
    }
}

internal class SynopsisQualityEditor(private val gateway: AiGateway) {
    suspend fun ensure(
        proposal: NewBookProposal,
        decisionLedger: String,
        force: Boolean = false,
    ): NewBookProposal {
        val normalized = proposal.copy(premise = SynopsisQuality.normalize(proposal.premise))
        if (!force && !SynopsisQuality.needsRewrite(normalized.premise)) return normalized
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是中文网文平台的简介主编。只改简介，不添加、删除或偷换任何设定。
                    输出 GeneratedChapter JSON：title="SYNOPSIS"；content=完整连贯的平台简介，篇幅由故事信息决定，不机械凑字或截断；summary=""；stateChanges=[]；touchedForeshadowingIds=[]。
                    必须写成连续故事：①主角身份与触发事件；②主角眼前必须完成的目标；③阻碍他的核心异常/规则；④失败的具体代价或迫近危险。
                    不写世界观说明书，不罗列名词，不出现“参考/融合/借鉴/主题/核心钩子/本书”，不剧透幕后真相和终局反转，不凭空补人物、能力或规则。句子之间必须因果连贯，主角称谓和人称保持一致。
                """.trimIndent(),
                user = """
                    书名：${proposal.title}
                    类型：${proposal.genre}
                    主题：${proposal.theme}
                    核心钩子：${proposal.coreHook}
                    当前简介：${proposal.premise}
                    确认事实账本：$decisionLedger
                """.trimIndent(),
            )
        )
        val rewritten = SynopsisQuality.normalize(output.content)
        return if (rewritten.length >= 70) normalized.copy(premise = rewritten) else normalized
    }
}
