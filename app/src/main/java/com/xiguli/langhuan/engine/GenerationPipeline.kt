package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.IssueSeverity
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
    private val adversarialEditor: AdversarialChapterEditor = AdversarialChapterEditor(),
    private val novelizationEngine: NovelizationEngine = NovelizationEngine(),
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

        // 2) Cheap local prose diagnostics. Only clearly non-novel drafts spend one extra model call.
        val initialQuality = novelizationEngine.analyze(prose)
        var quality = initialQuality
        var novelizationSucceeded = false
        if (initialQuality.requiresNovelization) {
            val novelized = runCatching {
                cleanVisibleProse(
                    aiGateway.generateText(
                        novelizationEngine.buildRewrite(request, prose, initialQuality, retrievedContext)
                    )
                )
            }.getOrNull().orEmpty()
            if (novelized.isNotBlank()) {
                prose = novelized
                novelizationSucceeded = true
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)
            }
        }

        // 3) One adversarial review call contains four independent editor seats.
        val firstReview = runCatching {
            aiGateway.generate(adversarialEditor.buildReview(request, prose, round = 1))
        }.getOrNull()
        val firstDeterministic = buildList {
            addAll(obviousProseProblems(prose))
            if (quality.requiresNovelization) addAll(quality.problems)
        }.distinct()
        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()
        var editorBlockingIssue: ConsistencyIssue? = null

        if (firstRejected) {
            val instructions = adversarialEditor.instructions(firstReview, firstDeterministic)
            val rewritten = runCatching {
                cleanVisibleProse(
                    aiGateway.generateText(
                        promptAssembler.buildRewrite(request, prose, instructions, retrievedContext)
                    )
                )
            }.getOrNull().orEmpty()

            if (rewritten.isBlank()) {
                editorBlockingIssue = ConsistencyIssue(
                    severity = IssueSeverity.BLOCKING,
                    code = "EDITOR_REWRITE_EMPTY",
                    message = "主编已退回稿件，但 AI 没有返回可用的修订稿",
                    evidence = instructions.take(800),
                    repairInstruction = "保留当前草稿但禁止正式保存；重新生成或切换模型后再次执行章节生成。",
                )
            } else {
                prose = rewritten
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)

                // 4) The rewritten draft must face a fresh second review. No infinite retry loop.
                val secondReview = runCatching {
                    aiGateway.generate(adversarialEditor.buildReview(request, prose, round = 2))
                }.getOrNull()
                val secondDeterministic = buildList {
                    addAll(obviousProseProblems(prose))
                    if (quality.requiresNovelization) addAll(quality.problems)
                }.distinct()
                val secondRejected = adversarialEditor.requestsRewrite(secondReview) || secondDeterministic.isNotEmpty()
                if (secondRejected) {
                    val secondInstructions = adversarialEditor.instructions(secondReview, secondDeterministic)
                    editorBlockingIssue = ConsistencyIssue(
                        severity = IssueSeverity.BLOCKING,
                        code = "EDITOR_REVIEW_FAILED",
                        message = "修订稿仍未通过四视角主编复审，已阻止进入正式版本和长期记忆",
                        evidence = secondReview?.summary.orEmpty().ifBlank { secondInstructions.take(800) },
                        repairInstruction = secondInstructions,
                    )
                }
            }
        }

        // 5) Only after prose is frozen do we extract summary/state/foreshadowing for the database.
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

        val finalQuality = novelizationEngine.analyze(prose)
        val issues = buildList {
            addAll(consistencyGate.inspect(request, chapter))
            editorBlockingIssue?.let(::add)
            if (novelizationSucceeded) {
                add(
                    ConsistencyIssue(
                        severity = IssueSeverity.INFO,
                        code = "PROSE_NOVELIZED",
                        message = "初稿命中报告体/AI腔阈值，已在主编审核前自动执行小说化重构",
                        evidence = "重构前 ${initialQuality.summary()} → 重构后 ${finalQuality.summary()}",
                        repairInstruction = "无需额外操作；如仍不符合你的写法，可在编辑器修改，作者画像会继续学习。",
                    )
                )
            }
            if (finalQuality.blocking) {
                add(
                    ConsistencyIssue(
                        severity = IssueSeverity.BLOCKING,
                        code = "PROSE_QUALITY_FAILED",
                        message = "最终稿仍存在严重报告体/后台痕迹，已禁止写入正式版本",
                        evidence = finalQuality.summary(),
                        repairInstruction = finalQuality.problems.joinToString("；").ifBlank { "重新生成整章并增加场景化表达。" },
                    )
                )
            } else if (finalQuality.score < 82) {
                add(
                    ConsistencyIssue(
                        severity = IssueSeverity.WARNING,
                        code = "PROSE_QUALITY_WATCH",
                        message = "正文可以保存，但小说化质量仍有可改进项（${finalQuality.score}分）",
                        evidence = finalQuality.problems.take(4).joinToString("；").ifBlank { finalQuality.summary() },
                        repairInstruction = "优先通过人物行动、对白、具体物件和因果变化承载信息，避免解释性总结。",
                    )
                )
            }
        }.distinctBy { listOf(it.code, it.message, it.evidence) }

        return GenerationResult(
            chapter = chapter,
            issues = issues,
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
