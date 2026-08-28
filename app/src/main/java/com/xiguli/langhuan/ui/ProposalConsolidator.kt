package com.xiguli.langhuan.ui

import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle

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
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的新书方案合并器。你的任务不是继续聊天，而是把整段会谈里已经确认的最新决定合并成“此刻唯一有效”的新书方案。

                    优先级必须严格遵守：
                    1. 越新的用户明确决定优先级越高；用户后面说“改成/不要/就选B/按这个/不是这样/换掉”时，旧设定立刻失效。
                    2. 用户的话高于琅嬛之前的建议。琅嬛提出但用户没有接受的内容不能当成已确认事实。
                    3. “当前缓存方案”只是旧基线，可能已经过期；只要与后续用户决定冲突，就必须丢弃旧内容，不能为了保持连续而偷偷复用。
                    4. 如果主角能力、身份、目标、世界规则、核心冲突、故事起点或阅读体验发生变化，平台简介必须基于最新方案重新写，禁止原封不动保留旧简介。
                    5. 参考作品只提炼高层机制，所有角色、专名、规则和剧情骨架必须原创。

                    必须输出完整 GeneratedChapter JSON：
                    - title=2-12字正式书名；
                    - content=100-220字“当前最新”的平台简介；
                    - summary=80-180字内部策划摘要；
                    - stateChanges 只返回1项：subject=实际小说类型；field=一句实际主题命题；before=目标总字数纯数字；after=一句话核心钩子；evidence=封面视觉简报；
                    - touchedForeshadowingIds=[]。

                    平台简介只写故事起点、主角当下目标、核心异常/规则和眼前代价/悬念，不泄露中后期答案。不要解释你做了什么，只返回结构化结果。
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
                    appendLine("内部策划：${current.rationale.take(500)}")
                    appendLine()
                    appendLine("【会谈记录：按时间顺序，后面的用户决定覆盖前面】")
                    appendLine(decisionTranscript(messages))
                },
            )
        )

        val meta = output.stateChanges.firstOrNull()
            ?: throw IllegalStateException("最终方案合并失败：AI 没有返回方案元数据")
        val title = normalizeTitle(output.title).ifBlank { current.title }
        val premise = normalizeSynopsis(output.content)
        require(premise.length >= 40) { "最终方案合并失败：AI 返回的简介过短" }

        return NewBookProposal(
            title = title,
            genre = meta.subject.trim().takeUnless(::isGenrePlaceholder) ?: current.genre,
            premise = premise,
            theme = meta.field.trim().takeUnless(::isThemePlaceholder) ?: current.theme,
            targetWords = meta.before.filter(Char::isDigit).toIntOrNull()
                ?.coerceIn(10_000, 5_000_000)
                ?: current.targetWords,
            coreHook = meta.after.trim().ifBlank { current.coreHook },
            coverBrief = meta.evidence.trim().ifBlank { current.coverBrief },
            rationale = output.summary.trim().ifBlank { current.rationale },
        )
    }

    private fun decisionTranscript(messages: List<CreationChatMessage>): String {
        val recent = messages.takeLast(28)
        return recent.joinToString("\n") { message ->
            val plain = if (message.role == "user") {
                message.text.substringBefore(RESEARCH_MARKER).trimEnd().take(1_000)
            } else {
                message.text.take(420)
            }
            if (message.role == "user") "用户：$plain" else "琅嬛：$plain"
        }.takeLast(12_000)
    }

    private fun normalizeTitle(value: String): String = value.trim()
        .removePrefix("《")
        .removeSuffix("》")
        .replace(Regex("[\n\r]"), "")
        .replace(Regex("[《》“”\"'，,。！？!?：:；;、\\s]"), "")
        .take(12)

    private fun normalizeSynopsis(value: String): String = value.trim()
        .replace(Regex("\\n{3,}"), "\n\n")
        .take(220)

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
