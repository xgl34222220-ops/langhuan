from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f'{label}: anchor not found')
    return text.replace(old, new, 1)


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f'{label}: start marker not found')
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f'{label}: end marker not found')
    return text[:start] + replacement + text[end:]


# -----------------------------------------------------------------------------
# GenerationPipeline: raw text streaming + observable stage events.
# -----------------------------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt')
text = path.read_text()
text = replace_once(
    text,
    '    /** Plain text path for normal conversation and novel prose. */\n    suspend fun generateText(prompt: PromptBundle): String = generate(prompt).content\n\n',
    '''    /** Plain text path for normal conversation and novel prose. */
    suspend fun generateText(prompt: PromptBundle): String = generate(prompt).content

    /**
     * Raw text streaming contract. onDelta receives the cumulative visible response so UI can replace
     * its preview without reconstructing provider-specific token deltas. Providers may fall back to a
     * single final callback only when streaming is unavailable before any bytes are emitted.
     */
    suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val text = generateText(prompt)
        onDelta(text)
        return text
    }

''',
    'AiGateway text streaming method',
)
text = replace_once(
    text,
    '''        retrievedContext: List<RetrievedContextItem> = emptyList(),
        onDelta: (String) -> Unit = {},
    ): GenerationResult {
        // 1) Author writes only prose. Context Builder 2.0 keeps RAG in D layer.
        var prose = cleanVisibleProse(
            aiGateway.generateText(promptAssembler.buildProse(request, retrievedContext))
        )
        require(prose.isNotBlank()) { "AI 没有返回可用正文" }
        onDelta(prose)
''',
    '''        retrievedContext: List<RetrievedContextItem> = emptyList(),
        onDelta: (String) -> Unit = {},
        onRunEvent: (RunEvent) -> Unit = {},
    ): GenerationResult {
        fun emit(stage: RunStage, status: RunStatus, detail: String = "") {
            onRunEvent(RunEvent(stage = stage, status = status, detail = detail))
        }

        // 1) Author writes only prose. Context Builder 2.0 keeps RAG in D layer.
        emit(RunStage.DRAFT, RunStatus.RUNNING, "S/A/B/C/D 上下文已锁定，模型开始返回正文")
        var prose = cleanVisibleProse(
            aiGateway.generateTextStreaming(promptAssembler.buildProse(request, retrievedContext)) { partial ->
                val visible = cleanVisibleProse(partial)
                if (visible.isNotBlank()) onDelta(visible)
            }
        )
        require(prose.isNotBlank()) { "AI 没有返回可用正文" }
        onDelta(prose)
        emit(RunStage.DRAFT, RunStatus.SUCCESS, "初稿 ${prose.length} 字")
''',
    'pipeline draft streaming',
)
text = replace_once(
    text,
    '''        if (initialQuality.requiresNovelization) {
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
''',
    '''        if (initialQuality.requiresNovelization) {
            emit(RunStage.NOVELIZATION, RunStatus.RUNNING, initialQuality.problems.take(3).joinToString("；"))
            onDelta("")
            val novelized = runCatching {
                cleanVisibleProse(
                    aiGateway.generateTextStreaming(
                        novelizationEngine.buildRewrite(request, prose, initialQuality, retrievedContext)
                    ) { partial ->
                        val visible = cleanVisibleProse(partial)
                        if (visible.isNotBlank()) onDelta(visible)
                    }
                )
            }.getOrNull().orEmpty()
            if (novelized.isNotBlank()) {
                prose = novelized
                novelizationSucceeded = true
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)
                emit(RunStage.NOVELIZATION, RunStatus.SUCCESS, "重构后小说化评分 ${quality.score} 分")
            } else {
                onDelta(prose)
                emit(RunStage.NOVELIZATION, RunStatus.WARNING, "小说化重构未返回可用文本，保留初稿交给主编继续检查")
            }
        } else {
            emit(RunStage.NOVELIZATION, RunStatus.SKIPPED, "初稿未命中报告体 / AI 腔阈值")
        }

        // 3) One adversarial review call contains four independent editor seats.
        emit(RunStage.EDITOR_REVIEW_1, RunStatus.RUNNING, "结构 / 人物 / 文字 / 连续性四席同时审稿")
        val firstReview = runCatching {
''',
    'pipeline novelization events',
)
text = replace_once(
    text,
    '''        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()
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
''',
    '''        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()
        emit(
            RunStage.EDITOR_REVIEW_1,
            when {
                firstReview == null -> RunStatus.WARNING
                firstRejected -> RunStatus.WARNING
                else -> RunStatus.SUCCESS
            },
            when {
                firstReview == null -> "AI 主编响应失败；仍会执行本地确定性质量规则"
                firstRejected -> "主编退回：${adversarialEditor.instructions(firstReview, firstDeterministic).take(220)}"
                else -> "四席通过"
            },
        )
        var editorBlockingIssue: ConsistencyIssue? = null

        if (firstRejected) {
            val instructions = adversarialEditor.instructions(firstReview, firstDeterministic)
            emit(RunStage.EDITOR_REWRITE, RunStatus.RUNNING, "按主编意见从头修订整章")
            onDelta("")
            val rewritten = runCatching {
                cleanVisibleProse(
                    aiGateway.generateTextStreaming(
                        promptAssembler.buildRewrite(request, prose, instructions, retrievedContext)
                    ) { partial ->
                        val visible = cleanVisibleProse(partial)
                        if (visible.isNotBlank()) onDelta(visible)
                    }
                )
            }.getOrNull().orEmpty()
''',
    'pipeline editor rewrite streaming',
)
text = replace_once(
    text,
    '''            if (rewritten.isBlank()) {
                editorBlockingIssue = ConsistencyIssue(
''',
    '''            if (rewritten.isBlank()) {
                onDelta(prose)
                emit(RunStage.EDITOR_REWRITE, RunStatus.FAILED, "主编已退回，但修订请求没有返回可用正文")
                emit(RunStage.EDITOR_REVIEW_2, RunStatus.SKIPPED, "没有可复审的修订稿")
                editorBlockingIssue = ConsistencyIssue(
''',
    'pipeline empty rewrite event',
)
text = replace_once(
    text,
    '''            } else {
                prose = rewritten
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)

                // 4) The rewritten draft must face a fresh second review. No infinite retry loop.
                val secondReview = runCatching {
''',
    '''            } else {
                prose = rewritten
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)
                emit(RunStage.EDITOR_REWRITE, RunStatus.SUCCESS, "修订稿 ${prose.length} 字")

                // 4) The rewritten draft must face a fresh second review. No infinite retry loop.
                emit(RunStage.EDITOR_REVIEW_2, RunStatus.RUNNING, "修订稿重新进入四席复审；不会无限循环改写")
                val secondReview = runCatching {
''',
    'pipeline second review start',
)
text = replace_once(
    text,
    '''                val secondRejected = adversarialEditor.requestsRewrite(secondReview) || secondDeterministic.isNotEmpty()
                if (secondRejected) {
''',
    '''                val secondRejected = adversarialEditor.requestsRewrite(secondReview) || secondDeterministic.isNotEmpty()
                emit(
                    RunStage.EDITOR_REVIEW_2,
                    when {
                        secondReview == null -> RunStatus.WARNING
                        secondRejected -> RunStatus.WARNING
                        else -> RunStatus.SUCCESS
                    },
                    when {
                        secondReview == null -> "二审响应失败；以本地规则和 Consistency Gate 继续判定"
                        secondRejected -> "二审仍退回，结果将被 BLOCKING"
                        else -> "二审通过"
                    },
                )
                if (secondRejected) {
''',
    'pipeline second review result',
)
text = replace_once(
    text,
    '''                }
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
''',
    '''                }
            }
        } else {
            emit(RunStage.EDITOR_REWRITE, RunStatus.SKIPPED, "一审通过，无需修订")
            emit(RunStage.EDITOR_REVIEW_2, RunStatus.SKIPPED, "一审通过，无需二审")
        }

        // 5) Only after prose is frozen do we extract summary/state/foreshadowing for the database.
        emit(RunStage.METADATA, RunStatus.RUNNING, "正文冻结后再提取摘要 / 状态 / 伏笔触碰；提取结果仍不是 Canon")
        val metadataResult = runCatching {
            aiGateway.generate(promptAssembler.buildMetadata(request, prose))
        }
        val metadata = metadataResult.getOrElse {
            GeneratedChapter(
                title = request.chapter.title,
                content = "",
                summary = fallbackSummary(prose),
            )
        }
        emit(
            RunStage.METADATA,
            if (metadataResult.isSuccess) RunStatus.SUCCESS else RunStatus.WARNING,
            if (metadataResult.isSuccess) "结构化提取完成" else "元数据提取失败，使用正文摘要兜底；不会凭空写入 Canon",
        )
''',
    'pipeline metadata events',
)
text = replace_once(
    text,
    '''        val finalQuality = novelizationEngine.analyze(prose)
        val issues = buildList {
''',
    '''        val finalQuality = novelizationEngine.analyze(prose)
        emit(RunStage.CONSISTENCY, RunStatus.RUNNING, "检查章节合同、信息边界、时间线、小说化质量与主编结果")
        val issues = buildList {
''',
    'pipeline consistency start',
)
text = replace_once(
    text,
    '''        }.distinctBy { listOf(it.code, it.message, it.evidence) }

        return GenerationResult(
''',
    '''        }.distinctBy { listOf(it.code, it.message, it.evidence) }
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

        return GenerationResult(
''',
    'pipeline ready event',
)
text = replace_once(
    text,
    '''    override suspend fun generateText(prompt: PromptBundle): String {
        delay(900)
        return demoChapter().content
    }

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter {
''',
    '''    override suspend fun generateText(prompt: PromptBundle): String {
        delay(900)
        return demoChapter().content
    }

    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val content = demoChapter().content
        val buffer = StringBuilder()
        content.chunked(24).forEach { chunk ->
            delay(35)
            buffer.append(chunk)
            onDelta(buffer.toString())
        }
        return buffer.toString()
    }

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter {
''',
    'demo raw streaming',
)
path.write_text(text)


# -----------------------------------------------------------------------------
# Universal gateway: provider-native raw text streaming for prose/chat.
# -----------------------------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt')
text = path.read_text()
start = text.find('    override suspend fun generateStreaming(\n')
end = text.find('    private fun resolvedProtocol()', start)
if start < 0 or end < 0:
    raise SystemExit('UniversalAiGateway streaming block not found')
new_stream = '''    override suspend fun generateTextStreaming(
        prompt: PromptBundle,
        onDelta: (String) -> Unit,
    ): String {
        require(config.baseUrl.isNotBlank()) { "请先配置 API 地址" }
        require(config.model.isNotBlank()) { "请先选择或填写模型" }
        val protocol = resolvedProtocol()
        var emitted = false
        val guardedDelta: (String) -> Unit = { preview ->
            emitted = true
            onDelta(preview)
        }

        return try {
            when (protocol) {
                ApiProtocol.ANTHROPIC -> streamAnthropic(prompt, guardedDelta)
                ApiProtocol.GEMINI -> streamGemini(prompt, guardedDelta)
                ApiProtocol.AZURE_OPENAI -> streamOpenAi(prompt, azure = true, guardedDelta)
                ApiProtocol.OLLAMA -> streamOllama(prompt, guardedDelta)
                else -> streamOpenAi(prompt, azure = false, guardedDelta)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (emitted) {
                throw IllegalStateException(
                    "流式连接在已经开始返回内容后中断。为避免重复扣费，琅嬛没有自动发起第二次请求；可手动重试。",
                    error,
                )
            }
            if (prompt.attachments.isNotEmpty()) {
                throw IllegalStateException(
                    "附件请求失败：当前模型或中转站可能不支持该图片/PDF输入格式。琅嬛没有自动重发，避免重复消耗。${error.message.orEmpty()}",
                    error,
                )
            }
            val text = generateText(prompt)
            onDelta(text)
            text
        }
    }

    override suspend fun generateStreaming(
        prompt: PromptBundle,
        onDelta: (String) -> Unit,
    ): GeneratedChapter = decodeChapter(generateTextStreaming(prompt, onDelta))

'''
text = text[:start] + new_stream + text[end:]
path.write_text(text)


# -----------------------------------------------------------------------------
# Studio VM: inspect context/generation/commit/Candidate/replan stages.
# -----------------------------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt')
text = path.read_text()
text = replace_once(text, 'import com.xiguli.langhuan.engine.ProviderDiscovery\n', 'import com.xiguli.langhuan.engine.ProviderDiscovery\nimport com.xiguli.langhuan.engine.RunEvent\nimport com.xiguli.langhuan.engine.RunStage\nimport com.xiguli.langhuan.engine.RunStatus\n', 'Studio run imports')
text = replace_once(text, '    val streamPreview: String = "",\n', '    val streamPreview: String = "",\n    val runEvents: List<RunEvent> = emptyList(),\n', 'Studio state run events')

# Clear stale inspector traces when switching working targets.
text = text.replace('                    streamPreview = "",\n                    isDraftDirty = false,', '                    streamPreview = "",\n                    runEvents = emptyList(),\n                    isDraftDirty = false,')
text = text.replace('                                streamPreview = "",\n                                isDraftDirty = false,', '                                streamPreview = "",\n                                runEvents = emptyList(),\n                                isDraftDirty = false,')

helper_anchor = '    fun clearMessage() = _state.update { it.copy(message = null, error = null) }\n'
helper = '''    private fun emitRun(event: RunEvent) {
        _state.update { state -> state.copy(runEvents = (state.runEvents + event).takeLast(96)) }
    }

    private fun emitRun(stage: RunStage, status: RunStatus, detail: String = "") {
        emitRun(RunEvent(stage = stage, status = status, detail = detail))
    }

'''
text = replace_once(text, helper_anchor, helper + helper_anchor, 'Studio run helper')

generate_fn = '''    fun generateChapter() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isGenerating = true,
                    streamPreview = "",
                    error = null,
                    runEvents = emptyList(),
                )
            }
            val current = _state.value
            emitRun(RunStage.CONTEXT, RunStatus.RUNNING, "正在构建 S/A/B/C/D 分层上下文并检索相关历史")
            val ragQuery = buildString {
                append(current.draft.title).append(' ').append(current.draft.objective).append(' ')
                current.draft.scenePlan.forEach { append(it.viewpoint).append(' ').append(it.location).append(' ').append(it.purpose).append(' ').append(it.conflict).append(' ') }
                current.snapshot.activeOutline.forEach { append(it.objective).append(' ').append(it.turningPoint).append(' ') }
                current.snapshot.characters.forEach { append(it.name).append(' ').append(it.goal).append(' ') }
            }
            val retrievedContext = runCatching {
                repository.retrieveRelevantContext(
                    current.snapshot.novel.id,
                    ragQuery,
                    current.draft.chapterNumber,
                    10,
                )
            }.getOrElse { error ->
                emitRun(RunStage.CONTEXT, RunStatus.WARNING, "历史召回失败：${error.message.orEmpty()}；继续使用结构化 Canon")
                emptyList()
            }
            if (retrievedContext.isNotEmpty()) {
                emitRun(RunStage.CONTEXT, RunStatus.SUCCESS, "召回 ${retrievedContext.size} 条可解释历史；硬约束优先级不会被 RAG 挤掉")
            } else if (_state.value.runEvents.lastOrNull { it.stage == RunStage.CONTEXT }?.status == RunStatus.RUNNING) {
                emitRun(RunStage.CONTEXT, RunStatus.SUCCESS, "本章没有需要补充的历史召回，继续使用结构化上下文")
            }

            val gateway = configuredGateway() ?: DemoAiGateway()
            runCatching {
                GenerationPipeline(gateway).generate(
                    request = GenerationRequest(current.snapshot, current.draft, 2_500),
                    retrievedContext = retrievedContext,
                    onDelta = { preview ->
                        _state.update { state -> state.copy(streamPreview = preview) }
                    },
                    onRunEvent = ::emitRun,
                )
            }.onSuccess { result ->
                _state.update { it.copy(isGenerating = false, streamPreview = result.chapter.content, result = result) }
            }.onFailure { error ->
                emitRun(RunStage.READY_TO_COMMIT, RunStatus.FAILED, error.message ?: "生成失败")
                _state.update { it.copy(isGenerating = false, streamPreview = "", error = error.message ?: "生成失败") }
            }
        }
    }

'''
text = replace_between(text, '    fun generateChapter() {\n', '    fun commitResult() {\n', generate_fn, 'Studio generateChapter')

commit_fn = '''    fun commitResult() {
        val current = _state.value
        val result = current.result ?: return
        if (!result.canCommit || current.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            emitRun(RunStage.SAVE, RunStatus.RUNNING, "写入正文、版本与章节摘要；AI 抽取事实不会直接改 Canon")
            runCatching { repository.commitGenerated(current.snapshot, current.draft, result.chapter) }
                .onSuccess { persisted ->
                    emitRun(RunStage.SAVE, RunStatus.SUCCESS, "正文 v${persisted.draft.version} 已保存")
                    _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, isSaving = false, isDraftDirty = false, streamPreview = "", result = null, message = "正文与版本已保存；结构化事实将先进入 Candidate，正在做 Agent 复盘") }
                    refreshWorkspace()
                    var working = persisted

                    if (FullBookEditorEngine.shouldAudit(working.snapshot, working.draft.chapterNumber)) {
                        emitRun(RunStage.FULL_BOOK_AUDIT, RunStatus.RUNNING, "到达周期巡检点，执行零额外模型成本的全书本地扫描")
                        runCatching {
                            val drafts = projects.chapterDrafts(working.snapshot.novel.id)
                            val editor = FullBookEditorEngine()
                            val report = editor.localAudit(working.snapshot, drafts)
                            projects.saveStructure(editor.apply(working.snapshot, report), working.draft)
                        }.onSuccess { audited ->
                            working = audited
                            emitRun(RunStage.FULL_BOOK_AUDIT, RunStatus.SUCCESS, "全书主编 ${audited.snapshot.longForm.editorReport.score} 分")
                            _state.update { state ->
                                state.copy(
                                    snapshot = audited.snapshot,
                                    draft = audited.draft,
                                    message = "正文已保存；全书主编本地巡检 ${audited.snapshot.longForm.editorReport.score} 分",
                                )
                            }
                        }.onFailure { error ->
                            emitRun(RunStage.FULL_BOOK_AUDIT, RunStatus.WARNING, "巡检未完成：${error.message.orEmpty()}")
                        }
                    } else {
                        emitRun(RunStage.FULL_BOOK_AUDIT, RunStatus.SKIPPED, "未到周期巡检点")
                    }

                    val gateway = configuredGateway()
                    if (gateway != null) {
                        emitRun(RunStage.EXECUTION_AUDIT, RunStatus.RUNNING, "比较滚动计划与实际正文，只标记真正受影响的未来章节")
                        val executionEngine = AutonomousExecutionEngine(gateway)
                        val execution = runCatching {
                            executionEngine.assess(working.snapshot, working.draft, result.chapter)
                        }.getOrNull()
                        if (execution != null) {
                            runCatching {
                                val settled = executionEngine.settle(working.snapshot, working.draft, result.chapter, execution)
                                projects.saveStructure(settled, working.draft)
                            }.onSuccess { settled ->
                                working = settled
                                emitRun(RunStage.EXECUTION_AUDIT, RunStatus.SUCCESS, "执行完成度 ${execution.completionScore} 分 · 影响后续 ${execution.affectedFutureChapters.size} 章")
                                _state.update { state ->
                                    state.copy(
                                        snapshot = settled.snapshot,
                                        draft = settled.draft,
                                        message = "正文已保存；计划执行审计 ${execution.completionScore} 分",
                                    )
                                }
                            }.onFailure { error ->
                                emitRun(RunStage.EXECUTION_AUDIT, RunStatus.WARNING, "审计完成但未能落库：${error.message.orEmpty()}")
                                _state.update { state -> state.copy(message = "正文已保存；计划执行审计未能落库，后续可自动补算") }
                            }
                        } else {
                            emitRun(RunStage.EXECUTION_AUDIT, RunStatus.WARNING, "AI 执行审计未返回可用结果")
                        }

                        emitRun(RunStage.CANDIDATE, RunStatus.RUNNING, "Agent 从已保存正文抽取结构化事实；先进入 Candidate，不直写 Canon")
                        _state.update { it.copy(isAgentReviewing = true) }
                        runCatching {
                            val review = NovelAgentEngine(gateway).reviewChapter(working.snapshot, working.draft)
                            val staged = CandidateCanonEngine.stage(working.snapshot, working.draft, review)
                            val persistedCandidate = projects.saveStructure(staged.snapshot, working.draft)
                            Triple(review, staged, persistedCandidate)
                        }.onSuccess { (review, staged, persistedCandidate) ->
                            working = persistedCandidate
                            emitRun(RunStage.CANDIDATE, RunStatus.SUCCESS, "新增 ${staged.stagedCount} 条 Candidate · 自动确认 ${staged.autoConfirmedCount} 条低风险事实")
                            _state.update {
                                it.copy(
                                    snapshot = persistedCandidate.snapshot,
                                    draft = persistedCandidate.draft,
                                    isAgentReviewing = false,
                                    agentReview = review,
                                    message = "正文已保存；Agent 复盘完成，${staged.stagedCount} 条事实进入 Candidate${if (staged.autoConfirmedCount > 0) "，${staged.autoConfirmedCount} 条低风险状态自动确认" else ""}",
                                )
                            }
                        }.onFailure { error ->
                            emitRun(RunStage.CANDIDATE, RunStatus.WARNING, "Candidate 提取失败：${error.message.orEmpty()}；正文已安全保存，可稍后手动复盘")
                            _state.update { it.copy(isAgentReviewing = false, message = "正文已保存；Agent 自动复盘失败，可在 Agent 页手动重试") }
                        }

                        val selective = execution?.let(AutonomousExecutionEngine::shouldSelectiveReplan) == true
                        val fullRefresh = AutonomousStoryPlanner.shouldRefresh(working.snapshot, working.draft.chapterNumber)
                        if (selective || fullRefresh) {
                            emitRun(
                                RunStage.AUTONOMOUS_REPLAN,
                                RunStatus.RUNNING,
                                if (selective && !fullRefresh) "只重算被计划偏差影响的后续章节" else "滚动窗口变薄或风险升高，补足未来计划",
                            )
                            _state.update {
                                it.copy(
                                    isAutonomousPlanning = true,
                                    message = if (selective) "正文已保存；正在只重算受影响的后续章节" else "正文已保存；正在补足未来滚动计划",
                                )
                            }
                            runCatching {
                                val planner = AutonomousStoryPlanner(gateway)
                                val candidate = planner.plan(working.snapshot, working.draft, 6)
                                val nextPlan = if (selective && !fullRefresh) {
                                    executionEngine.mergeSelectivePlan(
                                        working.snapshot,
                                        candidate,
                                        execution?.affectedFutureChapters.orEmpty(),
                                    )
                                } else candidate
                                projects.saveStructure(planner.apply(working.snapshot, nextPlan), working.draft)
                            }.onSuccess { planned ->
                                working = planned
                                emitRun(RunStage.AUTONOMOUS_REPLAN, RunStatus.SUCCESS, "未来滚动计划已同步")
                                _state.update {
                                    it.copy(
                                        snapshot = planned.snapshot,
                                        draft = planned.draft,
                                        isAutonomousPlanning = false,
                                        message = if (selective && !fullRefresh) "计划-实际偏差已吸收，只重算了受影响章节" else "未来滚动计划已同步",
                                    )
                                }
                                refreshWorkspace()
                            }.onFailure { error ->
                                emitRun(RunStage.AUTONOMOUS_REPLAN, RunStatus.WARNING, "重规划失败：${error.message.orEmpty()}")
                                _state.update { it.copy(isAutonomousPlanning = false, message = "正文已保存；自治计划重算失败，可在 Agent 页手动重试") }
                            }
                        } else {
                            emitRun(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "计划与实际仍对齐，无需洗掉未来规划")
                        }
                    } else {
                        emitRun(RunStage.EXECUTION_AUDIT, RunStatus.SKIPPED, "未配置 AI 服务，跳过语义执行审计")
                        emitRun(RunStage.CANDIDATE, RunStatus.SKIPPED, "未配置 AI 服务，可稍后手动复盘")
                        emitRun(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "未配置 AI 服务")
                    }
                    emitRun(RunStage.COMPLETE, RunStatus.SUCCESS, "正文已保存，所有可执行后处理阶段结束")
                }.onFailure { error ->
                    emitRun(RunStage.SAVE, RunStatus.FAILED, error.message ?: "保存章节失败")
                    emitRun(RunStage.COMPLETE, RunStatus.FAILED, "正文未写入正式版本")
                    _state.update { it.copy(isSaving = false, error = error.message ?: "保存章节失败") }
                }
        }
    }

'''
text = replace_between(text, '    fun commitResult() {\n', '    fun dismissResult() =', commit_fn, 'Studio commitResult')
path.write_text(text)


# -----------------------------------------------------------------------------
# Studio page: visible inspector + current live stage label.
# -----------------------------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/ui/LanghuanApp.kt')
text = path.read_text()
text = replace_once(text, 'import com.xiguli.langhuan.engine.DiscoveredModel\n', 'import com.xiguli.langhuan.engine.DiscoveredModel\nimport com.xiguli.langhuan.engine.RunStatus\n', 'LanghuanApp RunStatus import')
text = replace_once(
    text,
    '''        } }
        if (state.streamPreview.isNotBlank() && state.isGenerating) item {
''',
    '''        } }
        if (state.runEvents.isNotEmpty()) item { RunInspectorPanel(state.runEvents, "章节 Run Inspector") }
        if (state.streamPreview.isNotBlank() && state.isGenerating) item {
''',
    'Studio inspector insertion',
)
text = replace_once(
    text,
    '''            MiuixCard {
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("实时生成预览", Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp)); Text(state.streamPreview, maxLines = 14, overflow = TextOverflow.Ellipsis)
            }
''',
    '''            MiuixCard {
                val activeLabel = state.runEvents.lastOrNull { it.status == RunStatus.RUNNING }?.stage?.label ?: "实时生成预览"
                Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text(activeLabel, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(10.dp)); Text(state.streamPreview, maxLines = 18, overflow = TextOverflow.Ellipsis)
            }
''',
    'Studio live stage label',
)
path.write_text(text)


# -----------------------------------------------------------------------------
# New-book chat: true streaming without persisting every delta + creation inspector.
# -----------------------------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt')
text = path.read_text()
text = replace_once(text, 'import com.xiguli.langhuan.engine.ReferenceDistillationReportStore\n', 'import com.xiguli.langhuan.engine.ReferenceDistillationReportStore\nimport com.xiguli.langhuan.engine.RunEvent\nimport com.xiguli.langhuan.engine.RunStage\nimport com.xiguli.langhuan.engine.RunStatus\nimport com.xiguli.langhuan.engine.blueprintRunStage\n', 'NewBook run imports')
text = replace_once(
    text,
    '    val busyLabel: String = "",\n    val createdStoryId: String? = null,\n',
    '    val busyLabel: String = "",\n    val streamingReply: String = "",\n    val runEvents: List<RunEvent> = emptyList(),\n    val createdStoryId: String? = null,\n',
    'NewBook transient state',
)
text = replace_once(
    text,
    '''            _state.collect { current ->
                if (suppressDraftPersistence) draftStore.clear() else draftStore.persist(current)
            }
''',
    '''            _state.collect { current ->
                if (suppressDraftPersistence) {
                    draftStore.clear()
                } else if (current.streamingReply.isBlank()) {
                    // Streaming deltas are transient; do not rewrite AtomicFile for every chunk.
                    draftStore.persist(current)
                }
            }
''',
    'NewBook avoid per-delta persistence',
)

send_fn = '''    fun send(text: String) {
        val clean = text.trim()
        val before = _state.value
        if ((clean.isBlank() && before.pendingAttachments.isEmpty()) || before.isBusy || before.isLoadingAttachments) return
        val userText = clean.ifBlank { defaultAttachmentInstruction(before.pendingAttachments) }
        val history = before.messages + CreationChatMessage("user", userText, before.pendingAttachments)
        val plainInstruction = userText.substringBefore(RESEARCH_CONTEXT_MARKER).trim()
        val referenceQuestion = isReferenceFactQuestion(plainInstruction) && before.selectedReferenceTemplateIds.isNotEmpty()
        _state.update {
            it.copy(
                messages = history,
                pendingAttachments = emptyList(),
                isBusy = true,
                busyLabel = when {
                    referenceQuestion -> "正在读取所选模板的 Story DNA 事实……"
                    else -> "AI 正在继续和你聊这本书……"
                },
                streamingReply = "",
                runEvents = listOf(RunEvent(RunStage.CREATION_CHAT, RunStatus.RUNNING, "模型正在流式回复")),
                error = null,
            )
        }

        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, "未配置 AI 服务")
                _state.update { it.copy(isBusy = false, busyLabel = "", streamingReply = "", error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }

            runCatching {
                NewBookConversationEngine(gateway).reply(
                    messages = history,
                    currentProposal = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders(),
                    referenceContext = referenceReportStore.promptContext(before.selectedReferenceTemplateIds),
                    onDelta = { partial -> _state.update { it.copy(streamingReply = partial) } },
                )
            }.onSuccess { turn ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.SUCCESS, "回复完成")
                _state.update {
                    it.copy(
                        messages = it.messages + CreationChatMessage("assistant", turn.reply),
                        proposal = if (referenceQuestion) before.proposal else turn.proposal?.sanitizePlaceholders() ?: it.proposal,
                        foundation = if (referenceQuestion) before.foundation else it.foundation,
                        foundationStage = if (referenceQuestion) before.foundationStage else it.foundationStage,
                        blueprintDirty = if (referenceQuestion) it.blueprintDirty else it.blueprintDirty || (before.foundation != null && !isQuestionLike(plainInstruction)),
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                    )
                }
            }.onFailure { error ->
                emitRun(RunStage.CREATION_CHAT, RunStatus.FAILED, error.message.orEmpty())
                _state.update {
                    it.copy(
                        isBusy = false,
                        busyLabel = "",
                        streamingReply = "",
                        error = friendlyAiError(error, if (referenceQuestion) "模板事实读取失败" else "AI 构思失败"),
                    )
                }
            }
        }
    }

'''
text = replace_between(text, '    fun send(text: String) {\n', '    fun addConversationAttachments(uris: List<Uri>) {\n', send_fn, 'NewBook send streaming')

sync_fn = '''    fun syncConversationProposal() {
        val before = _state.value
        if (before.isBusy || before.isLoadingAttachments || before.messages.none { it.role == "user" }) return
        viewModelScope.launch {
            val gateway = activeGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            val baseline = (before.proposal ?: before.foundation?.toProposal())?.sanitizePlaceholders()
                ?: NewBookProposal(
                    title = "未命名",
                    genre = "未分类",
                    premise = "尚未整理",
                    theme = DEFAULT_THEME,
                    targetWords = 500_000,
                    coreHook = "待整理",
                    coverBrief = "",
                    rationale = "",
                )
            _state.update {
                it.copy(
                    isBusy = true,
                    busyLabel = "正在把当前会谈整理为建书方案……",
                    runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "合并用户最新决定，不自动生成蓝图")),
                    error = null,
                )
            }
            runCatching {
                ProposalConsolidator(gateway).consolidate(baseline, before.messages)
            }.onSuccess { proposal ->
                emitRun(RunStage.PROPOSAL_SYNC, RunStatus.SUCCESS, "当前会谈已整理成方案缓存")
                _state.update {
                    it.copy(
                        proposal = proposal.sanitizePlaceholders(),
                        blueprintDirty = before.foundation != null,
                        isBusy = false,
                        busyLabel = "",
                        error = null,
                    )
                }
            }.onFailure { error ->
                emitRun(RunStage.PROPOSAL_SYNC, RunStatus.FAILED, error.message.orEmpty())
                _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "整理当前方案失败")) }
            }
        }
    }

'''
text = replace_between(text, '    fun syncConversationProposal() {\n', '    fun generateFoundation(regenerate: Boolean = false) {\n', sync_fn, 'NewBook sync proposal')

# Add creation inspector events to foundation without rewriting its whole workflow.
text = replace_once(
    text,
    '''                    busyLabel = "正在把整段会谈的最新决定合并为最终方案……",
                    error = null,
''',
    '''                    busyLabel = "正在把整段会谈的最新决定合并为最终方案……",
                    runEvents = listOf(RunEvent(RunStage.PROPOSAL_SYNC, RunStatus.RUNNING, "先把会谈最新决定锁成蓝图输入")),
                    error = null,
''',
    'Foundation reset run events',
)
text = replace_once(
    text,
    '''                _state.update {
                    it.copy(
                        proposal = refreshed,
                        busyLabel = if (resumeStage > 0) {
''',
    '''                emitRun(RunStage.PROPOSAL_SYNC, RunStatus.SUCCESS, "方案合并完成")
                _state.update {
                    it.copy(
                        proposal = refreshed,
                        busyLabel = if (resumeStage > 0) {
''',
    'Foundation proposal event',
)
text = replace_once(
    text,
    '''                    onStage = { label -> _state.update { it.copy(busyLabel = label) } },
                    onCheckpoint = { stage, checkpoint ->
                        val cleanCheckpoint = checkpoint.sanitizeFoundationPlaceholders()
                        _state.update {
''',
    '''                    onStage = { label ->
                        val parsedStage = when {
                            label.contains("3/3") -> 3
                            label.contains("2/3") -> 2
                            label.contains("1/3") -> 1
                            else -> (_state.value.foundationStage + 1).coerceIn(1, 3)
                        }
                        emitRun(blueprintRunStage(parsedStage), RunStatus.RUNNING, label)
                        _state.update { it.copy(busyLabel = label) }
                    },
                    onCheckpoint = { stage, checkpoint ->
                        emitRun(blueprintRunStage(stage), RunStatus.SUCCESS, "第 $stage/3 阶段已保存检查点，可断点续跑")
                        val cleanCheckpoint = checkpoint.sanitizeFoundationPlaceholders()
                        _state.update {
''',
    'Foundation checkpoint events',
)
text = replace_once(
    text,
    '''            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "建书蓝图生成失败")) }
                }
''',
    '''            }.onFailure { error ->
                if (error !is kotlinx.coroutines.CancellationException) {
                    emitRun(blueprintRunStage((_state.value.foundationStage + 1).coerceIn(1, 3)), RunStatus.FAILED, error.message.orEmpty())
                    _state.update { it.copy(isBusy = false, busyLabel = "", error = friendlyAiError(error, "建书蓝图生成失败")) }
                }
''',
    'Foundation failure event',
)
# Formal create events.
text = replace_once(
    text,
    '''                blueprintDirty = false,
                isBusy = true,
                busyLabel = if (stage < 3) {
''',
    '''                blueprintDirty = false,
                isBusy = true,
                runEvents = listOf(RunEvent(RunStage.CREATE_BOOK, RunStatus.RUNNING, "把已确认核心蓝图写入正式项目结构")),
                busyLabel = if (stage < 3) {
''',
    'Create book running event',
)
text = replace_once(
    text,
    '''            .onSuccess { created ->
                suppressDraftPersistence = true
''',
    '''            .onSuccess { created ->
                emitRun(RunStage.CREATE_BOOK, RunStatus.SUCCESS, "《${created.snapshot.novel.title}》项目已创建")
                suppressDraftPersistence = true
''',
    'Create book success event',
)
text = replace_once(
    text,
    '''            }.onFailure { error ->
                _state.update { it.copy(isBusy = false, busyLabel = "", error = error.message ?: "正式建书失败") }
''',
    '''            }.onFailure { error ->
                emitRun(RunStage.CREATE_BOOK, RunStatus.FAILED, error.message.orEmpty())
                _state.update { it.copy(isBusy = false, busyLabel = "", error = error.message ?: "正式建书失败") }
''',
    'Create book failure event',
)
# Helpers before activeGateway.
text = replace_once(
    text,
    '''    private suspend fun activeGateway(): AiGateway? {
''',
    '''    private fun emitRun(stage: RunStage, status: RunStatus, detail: String = "") {
        _state.update { state -> state.copy(runEvents = (state.runEvents + RunEvent(stage, status, detail)).takeLast(72)) }
    }

    private suspend fun activeGateway(): AiGateway? {
''',
    'NewBook emitRun helper',
)
# Conversation engine raw streaming.
text = replace_once(
    text,
    '''        currentProposal: NewBookProposal? = null,
        referenceContext: String = "",
    ): ConversationTurn {
''',
    '''        currentProposal: NewBookProposal? = null,
        referenceContext: String = "",
        onDelta: (String) -> Unit = {},
    ): ConversationTurn {
''',
    'NewBook engine callback signature',
)
text = replace_once(
    text,
    '''        val response = gateway.generateText(
            PromptBundle(
''',
    '''        val response = gateway.generateTextStreaming(
            PromptBundle(
''',
    'NewBook gateway streaming call',
)
text = replace_once(
    text,
    '''                jsonMode = false,
            )
        ).trim()
''',
    '''                jsonMode = false,
            ),
            onDelta = { partial -> onDelta(partial) },
        ).trim()
''',
    'NewBook gateway streaming callback',
)
path.write_text(text)


# -----------------------------------------------------------------------------
# New-book page: show streaming assistant bubble and inspector without duplicate spinner.
# -----------------------------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt')
text = path.read_text()
text = replace_once(
    text,
    '            items(state.messages) { message -> ResearchChatBubble(message) }\n            researchMessage?.let { message -> item { ResearchStatusCard(message, lastTargets, lastSources) } }\n',
    '''            items(state.messages) { message -> ResearchChatBubble(message) }
            if (state.streamingReply.isNotBlank()) item {
                ResearchChatBubble(CreationChatMessage("assistant", state.streamingReply))
            }
            if (state.runEvents.isNotEmpty() && (state.isBusy || state.runEvents.map { it.stage }.distinct().size > 1)) item {
                RunInspectorPanel(state.runEvents, "建书 Run Inspector")
            }
            researchMessage?.let { message -> item { ResearchStatusCard(message, lastTargets, lastSources) } }
''',
    'Creation streaming bubble and inspector',
)
text = replace_once(
    text,
    '            if (state.isBusy || researching || state.isLoadingAttachments) item {\n',
    '            if (researching || state.isLoadingAttachments || (state.isBusy && state.runEvents.isEmpty())) item {\n',
    'Creation duplicate busy spinner',
)
path.write_text(text)


# Version bump.
path = Path('app/build.gradle.kts')
text = path.read_text()
text = text.replace('versionCode = 61', 'versionCode = 62')
text = text.replace('versionName = "0.26.3-alpha01"', 'versionName = "0.26.4-alpha01"')
path.write_text(text)
