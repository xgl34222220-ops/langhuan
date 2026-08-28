package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StateChange
import kotlinx.coroutines.delay

interface AiGateway {
    suspend fun generate(prompt: PromptBundle): GeneratedChapter

    /** Plain text path for normal conversation and novel prose. */
    suspend fun generateText(prompt: PromptBundle): String = generate(prompt).content

    /** Structured streaming remains available for legacy structured generation paths. */
    suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter {
        val chapter = generate(prompt)
        onDelta(chapter.content)
        return chapter
    }
}

class GenerationPipeline(
    private val aiGateway: AiGateway,
    private val promptAssembler: PromptAssembler = PromptAssembler(),
    private val consistencyGate: ConsistencyGate = ConsistencyGate(),
) {
    suspend fun generate(
        request: GenerationRequest,
        retrievedContext: List<RetrievedContextItem> = emptyList(),
        onDelta: (String) -> Unit = {},
    ): GenerationResult {
        // 1) Author writes only prose. Context Builder 2.0 keeps RAG in D layer.
        var prose = cleanVisibleProse(
            aiGateway.generateText(promptAssembler.buildProse(request, retrievedContext))
        )
        require(prose.isNotBlank()) { "AI 没有返回可用正文" }
        onDelta(prose)

        // 2) A separate editor checks narrative quality and future-chapter leakage.
        val review = runCatching {
            aiGateway.generate(promptAssembler.buildQualityReview(request, prose))
        }.getOrNull()
        val deterministicProblems = obviousProseProblems(prose)
        val aiRequestsRewrite = review?.title?.trim()?.equals("REWRITE", ignoreCase = true) == true
        if (aiRequestsRewrite || deterministicProblems.isNotEmpty()) {
            val instructions = buildString {
                review?.content?.trim()?.takeIf { it.isNotBlank() && !it.equals("通过", true) }?.let {
                    appendLine(it)
                }
                deterministicProblems.forEach { appendLine("- $it") }
            }.trim().ifBlank {
                "从头重写本章：严格只完成本章目标，用人物与场景推进，不写调查报告，不提前泄露后续章纲。"
            }
            val rewritten = cleanVisibleProse(
                aiGateway.generateText(promptAssembler.buildRewrite(request, prose, instructions, retrievedContext))
            )
            if (rewritten.isNotBlank()) {
                prose = rewritten
                onDelta(prose)
            }
        }

        // 3) Only after prose is frozen do we extract summary/state/foreshadowing for the database.
        val metadata = runCatching {
            aiGateway.generate(promptAssembler.buildMetadata(request, prose))
        }.getOrElse {
            GeneratedChapter(
                title = request.chapter.title,
                content = "",
                summary = fallbackSummary(prose),
            )
        }
        val chapter = GeneratedChapter(
            title = metadata.title.trim().ifBlank { request.chapter.title },
            content = prose,
            summary = metadata.summary.trim().ifBlank { fallbackSummary(prose) },
            stateChanges = metadata.stateChanges,
            touchedForeshadowingIds = metadata.touchedForeshadowingIds,
        )

        return GenerationResult(
            chapter = chapter,
            issues = consistencyGate.inspect(request, chapter),
        )
    }

    private fun cleanVisibleProse(raw: String): String {
        var text = raw.trim()
            .removePrefix("```markdown").removePrefix("```text").removePrefix("```")
            .removeSuffix("```").trim()
        // Some relays still wrap plain-text requests in a tiny JSON object. Extract content defensively.
        if (text.startsWith("{") && text.contains("\"content\"")) {
            val extracted = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .find(text)?.groupValues?.getOrNull(1)
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?.trim()
            if (!extracted.isNullOrBlank()) text = extracted
        }
        return text.trim()
    }

    private fun obviousProseProblems(prose: String): List<String> {
        val problems = mutableListOf<String>()
        val backendPhrases = listOf(
            "他目前掌握的信息", "她目前掌握的信息", "目前掌握的信息", "本章总结", "状态更新",
            "已确认事实", "touchedForeshadowingIds", "stateChanges", "场景计划", "本章约",
        )
        if (backendPhrases.any { prose.contains(it, ignoreCase = true) }) {
            problems += "正文混入了后台总结/状态字段，必须全部删除并改成场景叙事。"
        }
        val numberedListLines = prose.lineSequence().count { line ->
            Regex("^\\s*(?:第[一二三四五六七八九十百]+人|第\\d+人|\\d+[.、])").containsMatchIn(line)
        }
        if (numberedListLines >= 8) {
            problems += "存在大段枚举式信息倾倒；只保留最有戏剧价值的少数例子，其余自然概括。"
        }
        val functionVerbs = Regex("(?:搜索|核对|记录|分类|整理|重新排列|逐条|输入|打开|写下)")
            .findAll(prose).count()
        if (functionVerbs >= 18) {
            problems += "功能性调查动作过密，人物像检索程序；重写时增加欲望、情绪、关系、阻力和具体场景。"
        }
        return problems
    }

    private fun fallbackSummary(prose: String): String {
        val compact = prose.replace(Regex("\\s+"), " ").trim()
        return if (compact.length <= 220) compact else compact.take(217) + "…"
    }
}

class DemoAiGateway : AiGateway {
    override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
        delay(900)
        return demoChapter()
    }

    override suspend fun generateText(prompt: PromptBundle): String {
        delay(900)
        return demoChapter().content
    }

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter {
        val chapter = demoChapter()
        val chunks = chapter.content.chunked(36)
        val buffer = StringBuilder()
        chunks.forEach {
            delay(45)
            buffer.append(it)
            onDelta(buffer.toString())
        }
        return chapter
    }

    private fun demoChapter() = GeneratedChapter(
        title = "雾港来信",
        content = """
            港城的雾在子夜后压得更低。沈砚把那封没有署名的信平放在灯下，纸角残留的银色盐晶与旧案卷上的样本完全一致。

            他没有立刻去码头，而是先敲响了顾遥的门。两人核对城门记录，发现失踪商队入城的日期恰好被人改过一次。顾遥坚持从档案馆追查，沈砚却注意到窗外那道停留过久的影子。

            他们故意熄灯，从后门离开。追踪者把二人引向废弃钟楼，也让沈砚确认：寄信人并不是求救，而是在测试他们是否已经发现时间记录的矛盾。
        """.trimIndent(),
        summary = "沈砚与顾遥通过匿名信确认失踪商队记录被篡改，并在废弃钟楼发现寄信人正在试探他们。",
        stateChanges = listOf(
            StateChange("沈砚", "knownSecrets", "不知道记录被改", "确认商队记录被篡改", "核对城门记录"),
        ),
        touchedForeshadowingIds = listOf("f1"),
    )
}
