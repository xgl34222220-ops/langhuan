package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.IssueSeverity

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
        onRunEvent: (RunEvent) -> Unit = {},
        resumeCheckpoint: GenerationStageCheckpoint = GenerationStageCheckpoint(),
        onCheckpoint: (GenerationStageCheckpoint) -> Unit = {},
    ): GenerationResult {
        fun emit(stage: RunStage, status: RunStatus, detail: String = "") {
            onRunEvent(RunEvent(stage = stage, status = status, detail = detail))
        }
        var checkpoint = resumeCheckpoint
        fun persist(next: GenerationStageCheckpoint) {
            checkpoint = next
            onCheckpoint(next)
        }

        // 1) Draft prose. If this model call already completed, never send it again.
        val draftProse = if (checkpoint.draftProse.isNotBlank()) {
            val restored = cleanVisibleProse(checkpoint.draftProse)
            onDelta(restored)
            emit(RunStage.DRAFT, RunStatus.SUCCESS, "从持久化断点恢复初稿 ${restored.length} 字；未重复请求模型")
            restored
        } else {
            emit(RunStage.DRAFT, RunStatus.RUNNING, "S/A/B/C/D 上下文已锁定，模型开始返回正文")
            val generated = cleanVisibleProse(
                aiGateway.generateTextStreaming(promptAssembler.buildProse(request, retrievedContext)) { partial ->
                    val visible = cleanVisibleProse(partial)
                    if (visible.isNotBlank()) onDelta(visible)
                }
            )
            require(generated.isNotBlank()) { "AI 没有返回可用正文" }
            persist(checkpoint.copy(draftProse = generated))
            onDelta(generated)
            emit(RunStage.DRAFT, RunStatus.SUCCESS, "初稿 ${generated.length} 字")
            generated
        }

        val initialQuality = novelizationEngine.analyze(draftProse)
        var prose = draftProse
        var novelizationSucceeded = checkpoint.novelizationSucceeded

        // 2) Novelization. Attempt outcome (including an empty/failed response) is checkpointed.
        if (initialQuality.requiresNovelization) {
            if (checkpoint.novelizationAttempted) {
                prose = checkpoint.postNovelizationProse.ifBlank { draftProse }
                novelizationSucceeded = checkpoint.novelizationSucceeded
                onDelta(prose)
                emit(
                    RunStage.NOVELIZATION,
                    if (novelizationSucceeded) RunStatus.SUCCESS else RunStatus.WARNING,
                    if (novelizationSucceeded) "从断点恢复小说化重构；未重复请求模型" else "上次小说化重构未得到可用文本；恢复初稿继续",
                )
            } else {
                emit(RunStage.NOVELIZATION, RunStatus.RUNNING, initialQuality.problems.take(3).joinToString("；"))
                onDelta("")
                val novelized = runCatching {
                    cleanVisibleProse(
                        aiGateway.generateTextStreaming(
                            novelizationEngine.buildRewrite(request, draftProse, initialQuality, retrievedContext)
                        ) { partial ->
                            val visible = cleanVisibleProse(partial)
                            if (visible.isNotBlank()) onDelta(visible)
                        }
                    )
                }.getOrNull().orEmpty()
                novelizationSucceeded = novelized.isNotBlank()
                prose = novelized.ifBlank { draftProse }
                persist(
                    checkpoint.copy(
                        novelizationAttempted = true,
                        postNovelizationProse = prose,
                        novelizationSucceeded = novelizationSucceeded,
                    )
                )
                onDelta(prose)
                emit(
                    RunStage.NOVELIZATION,
                    if (novelizationSucceeded) RunStatus.SUCCESS else RunStatus.WARNING,
                    if (novelizationSucceeded) "重构后小说化评分 ${novelizationEngine.analyze(prose).score} 分" else "小说化重构未返回可用文本，保留初稿交给主编继续检查",
                )
            }
        } else {
            if (!checkpoint.novelizationAttempted) {
                persist(checkpoint.copy(novelizationAttempted = true, postNovelizationProse = draftProse))
            }
            prose = draftProse
            emit(RunStage.NOVELIZATION, RunStatus.SKIPPED, "初稿未命中报告体 / AI 腔阈值")
        }

        val preEditorProse = prose
        var quality = novelizationEngine.analyze(preEditorProse)

        // 3) First adversarial review. A null response is also a completed attempt and is not replayed.
        val firstReview = if (checkpoint.firstReviewAttempted) {
            emit(RunStage.EDITOR_REVIEW_1, RunStatus.RUNNING, "恢复已完成的一审结果")
            checkpoint.firstReview
        } else {
            emit(RunStage.EDITOR_REVIEW_1, RunStatus.RUNNING, "结构 / 人物 / 文字 / 连续性四席同时审稿")
            val reviewed = runCatching {
                aiGateway.generate(adversarialEditor.buildReview(request, preEditorProse, round = 1))
            }.getOrNull()
            persist(checkpoint.copy(firstReviewAttempted = true, firstReview = reviewed))
            reviewed
        }
        val firstDeterministic = buildList {
            addAll(obviousProseProblems(preEditorProse))
            if (quality.requiresNovelization) addAll(quality.problems)
        }.distinct()
        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()
        emit(
            RunStage.EDITOR_REVIEW_1,
            when {
                firstReview == null -> RunStatus.WARNING
                firstRejected -> RunStatus.WARNING
                else -> RunStatus.SUCCESS
            },
            when {
                checkpoint.firstReviewAttempted && firstReview != null && !firstRejected -> "一审结果从断点复用 · 四席通过"
                firstReview == null -> "AI 主编响应失败；仍会执行本地确定性质量规则"
                firstRejected -> "主编退回：${adversarialEditor.instructions(firstReview, firstDeterministic).take(220)}"
                else -> "四席通过"
            },
        )

        var editorBlockingIssue: ConsistencyIssue? = null
        if (firstRejected) {
            val instructions = adversarialEditor.instructions(firstReview, firstDeterministic)
            val rewritten = if (checkpoint.editorRewriteAttempted) {
                checkpoint.editorRewriteProse
            } else {
                emit(RunStage.EDITOR_REWRITE, RunStatus.RUNNING, "按主编意见从头修订整章")
                onDelta("")
                val value = runCatching {
                    cleanVisibleProse(
                        aiGateway.generateTextStreaming(
                            promptAssembler.buildRewrite(request, preEditorProse, instructions, retrievedContext)
                        ) { partial ->
                            val visible = cleanVisibleProse(partial)
                            if (visible.isNotBlank()) onDelta(visible)
                        }
                    )
                }.getOrNull().orEmpty()
                persist(checkpoint.copy(editorRewriteAttempted = true, editorRewriteProse = value))
                value
            }

            if (rewritten.isBlank()) {
                prose = preEditorProse
                onDelta(prose)
                emit(RunStage.EDITOR_REWRITE, RunStatus.FAILED, "主编已退回，但修订请求没有返回可用正文")
                emit(RunStage.EDITOR_REVIEW_2, RunStatus.SKIPPED, "没有可复审的修订稿")
                editorBlockingIssue = ConsistencyIssue(
                    severity = IssueSeverity.BLOCKING,
                    code = "EDITOR_REWRITE_EMPTY",
                    message = "主编已退回稿件，但 AI 没有返回可用的修订稿",
                    evidence = instructions.take(800),
                    repairInstruction = "保留当前草稿但禁止正式保存；重新生成或切换模型后再次执行章节生成。",
                )
            } else {
                prose = cleanVisibleProse(rewritten)
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)
                emit(
                    RunStage.EDITOR_REWRITE,
                    RunStatus.SUCCESS,
                    if (checkpoint.editorRewriteAttempted) "从断点恢复修订稿 ${prose.length} 字" else "修订稿 ${prose.length} 字",
                )

                val secondReview = if (checkpoint.secondReviewAttempted) {
                    checkpoint.secondReview
                } else {
                    emit(RunStage.EDITOR_REVIEW_2, RunStatus.RUNNING, "修订稿重新进入四席复审；不会无限循环改写")
                    val reviewed = runCatching {
                        aiGateway.generate(adversarialEditor.buildReview(request, prose, round = 2))
                    }.getOrNull()
                    persist(checkpoint.copy(secondReviewAttempted = true, secondReview = reviewed))
                    reviewed
                }
                val secondDeterministic = buildList {
                    addAll(obviousProseProblems(prose))
                    if (quality.requiresNovelization) addAll(quality.problems)
                }.distinct()
                val secondRejected = adversarialEditor.requestsRewrite(secondReview) || secondDeterministic.isNotEmpty()
                emit(
                    RunStage.EDITOR_REVIEW_2,
                    when {
                        secondReview == null -> RunStatus.WARNING
                        secondRejected -> RunStatus.WARNING
                        else -> RunStatus.SUCCESS
                    },
                    when {
                        checkpoint.secondReviewAttempted && secondReview != null && !secondRejected -> "二审结果从断点复用 · 四席通过"
                        secondReview == null -> "二审响应失败；以本地规则和 Consistency Gate 继续判定"
                        secondRejected -> "二审仍退回，结果将被 BLOCKING"
                        else -> "二审通过"
                    },
                )
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
        } else {
            emit(RunStage.EDITOR_REWRITE, RunStatus.SKIPPED, "一审通过，无需修订")
            emit(RunStage.EDITOR_REVIEW_2, RunStatus.SKIPPED, "一审通过，无需二审")
        }

        // 4) Metadata extraction is paid/remote, so its fallback result is checkpointed too.
        emit(RunStage.METADATA, RunStatus.RUNNING, "正文冻结后再提取摘要 / 状态 / 伏笔触碰；提取结果仍不是 Canon")
        val metadata: GeneratedChapter
        val metadataSucceeded: Boolean
        val metadataRestored = checkpoint.metadataAttempted && checkpoint.metadata != null
        if (metadataRestored) {
            metadata = requireNotNull(checkpoint.metadata)
            metadataSucceeded = checkpoint.metadataSucceeded
        } else {
            val metadataResult = runCatching { aiGateway.generate(promptAssembler.buildMetadata(request, prose)) }
            metadataSucceeded = metadataResult.isSuccess
            metadata = metadataResult.getOrElse {
                GeneratedChapter(
                    title = request.chapter.title,
                    content = "",
                    summary = fallbackSummary(prose),
                )
            }
            persist(
                checkpoint.copy(
                    metadataAttempted = true,
                    metadataSucceeded = metadataSucceeded,
                    metadata = metadata,
                )
            )
        }
        emit(
            RunStage.METADATA,
            if (metadataSucceeded) RunStatus.SUCCESS else RunStatus.WARNING,
            if (metadataRestored) "元数据从断点恢复；未重复请求模型" else if (metadataSucceeded) "结构化提取完成" else "元数据提取失败，使用正文摘要兜底；不会凭空写入 Canon",
        )

        val chapter = GeneratedChapter(
            title = metadata.title.trim().ifBlank { request.chapter.title },
            content = prose,
            summary = metadata.summary.trim().ifBlank { fallbackSummary(prose) },
            stateChanges = metadata.stateChanges,
            touchedForeshadowingIds = metadata.touchedForeshadowingIds,
        )

        val finalQuality = novelizationEngine.analyze(prose)
        emit(RunStage.CONSISTENCY, RunStatus.RUNNING, "检查章节合同、信息边界、时间线、小说化质量与主编结果")
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
        val blockingCount = issues.count { it.severity == IssueSeverity.BLOCKING }
        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }
        emit(
            RunStage.CONSISTENCY,
            if (blockingCount > 0) RunStatus.WARNING else RunStatus.SUCCESS,
            "BLOCKING=$blockingCount · WARNING=$warningCount · 小说化=${finalQuality.score}分",
        )
        emit(
            RunStage.READY_TO_COMMIT,
            if (blockingCount > 0) RunStatus.WARNING else RunStatus.SUCCESS,
            if (blockingCount > 0) "生成完成，但存在阻止保存的问题" else "正文已通过生成链，可查看结果并确认保存",
        )
        return GenerationResult(chapter = chapter, issues = issues)
    }

    private fun cleanVisibleProse(text: String): String {
        var cleaned = text
            .replace(Regex("(?s)^\\s*```(?:markdown|md|text)?\\s*"), "")
            .replace(Regex("(?s)\\s*```\\s*$"), "")
            .trim()
        if (cleaned.startsWith("content:", true)) cleaned = cleaned.substringAfter(':').trim()
        return cleaned
    }

    private fun obviousProseProblems(text: String): List<String> {
        val compact = text.trim()
        val problems = mutableListOf<String>()
        if (compact.isBlank()) return listOf("正文为空")
        val reportPhrases = listOf(
            "目前掌握的信息", "目前已知的信息", "当前掌握的信息", "本章信息汇总",
            "以下是", "综上所述", "总结如下", "已确认事实", "信息如下",
        )
        val matched = reportPhrases.filter { compact.contains(it) }
        if (matched.isNotEmpty()) problems += "存在报告/后台总结措辞：${matched.joinToString("、")}" 
        val listLines = compact.lineSequence().count { line ->
            line.trim().matches(Regex("^(?:[-•*]|\\d+[.、)])\\s*.+"))
        }
        if (listLines >= 5) problems += "正文包含连续清单式信息罗列（$listLines 行）"
        val cardLines = compact.lineSequence().count { line ->
            line.trim().matches(Regex("^[\\p{L}\\p{N}_·]{1,12}[：:].+"))
        }
        if (cardLines >= 5) problems += "正文出现资料卡式字段堆叠（$cardLines 行）"
        return problems
    }

    private fun fallbackSummary(text: String): String {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        return when {
            compact.isBlank() -> "本章正文已生成，等待进一步提取结构化摘要。"
            compact.length <= 220 -> compact
            else -> compact.take(217) + "…"
        }
    }
}
