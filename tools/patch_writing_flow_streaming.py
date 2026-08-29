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


# --- WritingFlowViewModel -----------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/ui/WritingFlowViewModel.kt')
text = path.read_text()
text = text.replace('import com.xiguli.langhuan.engine.AgentMemoryApplier\n', '')
text = replace_once(text, 'import com.xiguli.langhuan.engine.AgentReview\n', 'import com.xiguli.langhuan.engine.AgentReview\nimport com.xiguli.langhuan.engine.CandidateCanonEngine\n', 'candidate import')
text = replace_once(text, 'import com.xiguli.langhuan.engine.NovelAgentEngine\n', 'import com.xiguli.langhuan.engine.NovelAgentEngine\nimport com.xiguli.langhuan.engine.RunEvent\nimport com.xiguli.langhuan.engine.RunStage\nimport com.xiguli.langhuan.engine.RunStatus\n', 'run imports')
text = replace_once(text, '    val streamPreview: String = "",\n', '    val streamPreview: String = "",\n    val runEvents: List<RunEvent> = emptyList(),\n', 'run state')

# Clear inspector when loading a new story / moving chapter.
text = text.replace('                        workingScenes = loaded.draft.scenePlan,\n                        chapterCommitted', '                        workingScenes = loaded.draft.scenePlan,\n                        runEvents = emptyList(),\n                        chapterCommitted')

# helpers before planScenes
anchor = '    fun planScenes(instruction: String = "") {\n'
helpers = '''    private fun emitRun(event: RunEvent) {
        _state.update { state -> state.copy(runEvents = (state.runEvents + event).takeLast(96)) }
    }

    private fun emitRun(stage: RunStage, status: RunStatus, detail: String = "") {
        emitRun(RunEvent(stage = stage, status = status, detail = detail))
    }

'''
text = replace_once(text, anchor, helpers + anchor, 'run helpers')

generate_fn = '''    fun generate(extraInstruction: String = "") {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                val gateway = activeGateway()
                _state.update {
                    it.copy(
                        isGenerating = true,
                        streamPreview = "",
                        runEvents = emptyList(),
                        result = null,
                        review = null,
                        memoryApplied = false,
                        error = null,
                    )
                }
                emitRun(RunStage.CONTEXT, RunStatus.RUNNING, "构建 S/A/B/C/D 上下文并检索本章相关历史")
                val workingDraft = draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan })
                val query = buildString {
                    append(workingDraft.title).append(' ').append(workingDraft.objective).append(' ')
                    workingDraft.scenePlan.forEach {
                        append(it.viewpoint).append(' ').append(it.location).append(' ')
                        append(it.purpose).append(' ').append(it.conflict).append(' ')
                    }
                    snapshot.activeOutline.forEach {
                        append(it.objective).append(' ').append(it.turningPoint).append(' ')
                        append(it.mustInclude.joinToString(" ")).append(' ')
                    }
                    snapshot.characters.forEach { append(it.name).append(' ').append(it.goal).append(' ') }
                }
                val retrievedContext = runCatching {
                    repository.retrieveRelevantContext(
                        snapshot.novel.id,
                        query,
                        workingDraft.chapterNumber,
                        10,
                    )
                }.getOrElse { error ->
                    emitRun(RunStage.CONTEXT, RunStatus.WARNING, "RAG 召回失败：${error.message.orEmpty()}；继续使用结构化 Canon")
                    emptyList()
                }
                if (retrievedContext.isNotEmpty()) {
                    emitRun(RunStage.CONTEXT, RunStatus.SUCCESS, "D 层召回 ${retrievedContext.size} 条历史，不再污染 recentSummaries")
                } else if (_state.value.runEvents.lastOrNull { it.stage == RunStage.CONTEXT }?.status == RunStatus.RUNNING) {
                    emitRun(RunStage.CONTEXT, RunStatus.SUCCESS, "无需额外历史召回，继续使用结构化 Canon")
                }

                val result = GenerationPipeline(gateway).generate(
                    request = GenerationRequest(
                        snapshot = snapshot,
                        chapter = workingDraft,
                        targetWords = 2_800,
                        extraInstruction = extraInstruction.trim(),
                    ),
                    retrievedContext = retrievedContext,
                    onDelta = { preview ->
                        _state.update { state -> state.copy(streamPreview = preview) }
                    },
                    onRunEvent = ::emitRun,
                )
                _state.update {
                    it.copy(
                        isGenerating = false,
                        streamPreview = result.chapter.content,
                        result = result,
                        message = if (result.canCommit) "正文已生成并通过阻断级检查" else "正文已生成，但存在阻断级一致性问题",
                    )
                }
            } catch (_: CancellationException) {
                emitRun(RunStage.READY_TO_COMMIT, RunStatus.WARNING, "你主动停止了生成；已收到的内容不会自动重发")
                _state.update {
                    it.copy(
                        isGenerating = false,
                        message = "已停止本次生成；已收到的内容不会自动二次请求",
                    )
                }
            } catch (error: Throwable) {
                emitRun(RunStage.READY_TO_COMMIT, RunStatus.FAILED, error.message ?: "正文生成失败")
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error = error.message ?: "正文生成失败",
                    )
                }
            } finally {
                generationJob = null
            }
        }
    }

'''
text = replace_between(text, '    fun generate(extraInstruction: String = "") {\n', '    fun cancelGeneration() {\n', generate_fn, 'generate')

commit_fn = '''    fun commitAndReview() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        val result = current.result ?: return
        if (!result.canCommit || current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            emitRun(RunStage.SAVE, RunStatus.RUNNING, "保存正文与版本；结构化事实不会在这里直接进入 Canon")
            runCatching {
                repository.commitGenerated(
                    snapshot = snapshot,
                    draft = draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan }),
                    generated = result.chapter,
                )
            }.onSuccess { persisted ->
                emitRun(RunStage.SAVE, RunStatus.SUCCESS, "正文 v${persisted.draft.version} 已保存")
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        workingScenes = persisted.draft.scenePlan,
                        result = null,
                        streamPreview = "",
                        isSaving = false,
                        chapterCommitted = true,
                        memoryApplied = false,
                        message = "正文与版本已保存，开始 Agent 复盘并写入 Candidate",
                    )
                }
                reviewCommittedChapter()
            }.onFailure { error ->
                emitRun(RunStage.SAVE, RunStatus.FAILED, error.message ?: "保存正文失败")
                _state.update { it.copy(isSaving = false, error = error.message ?: "保存正文失败") }
            }
        }
    }

'''
text = replace_between(text, '    fun commitAndReview() {\n', '    fun reviewCommittedChapter() {\n', commit_fn, 'commit')

review_fn = '''    fun reviewCommittedChapter() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.isReviewing || current.isGenerating || current.isSaving || draft.content.isBlank()) return
        viewModelScope.launch {
            val gateway = runCatching { activeGateway() }.getOrElse {
                _state.update { state -> state.copy(error = it.message ?: "Agent 复盘需要 AI 服务") }
                return@launch
            }
            emitRun(RunStage.CANDIDATE, RunStatus.RUNNING, "从已保存正文抽取事实，全部先进入 Candidate")
            _state.update { it.copy(isReviewing = true, error = null, review = null) }
            runCatching {
                val review = NovelAgentEngine(gateway).reviewChapter(snapshot, draft)
                val staged = CandidateCanonEngine.stage(snapshot, draft, review)
                val persisted = projects.saveStructure(staged.snapshot, draft)
                Triple(review, staged, persisted)
            }.onSuccess { (review, staged, persisted) ->
                emitRun(RunStage.CANDIDATE, RunStatus.SUCCESS, "新增 ${staged.stagedCount} 条 Candidate · 自动确认 ${staged.autoConfirmedCount} 条低风险事实")
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isReviewing = false,
                        review = review,
                        memoryApplied = false,
                        message = "Agent 已完成复盘；候选事实已进入 Candidate，请确认后再进入下一章",
                    )
                }
            }.onFailure { error ->
                emitRun(RunStage.CANDIDATE, RunStatus.WARNING, "Candidate 提取失败：${error.message.orEmpty()}")
                _state.update { it.copy(isReviewing = false, error = error.message ?: "章节复盘失败") }
            }
        }
    }

    fun confirmCandidateFact(candidateId: String) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                projects.saveStructure(CandidateCanonEngine.confirm(snapshot, candidateId), draft)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isSaving = false,
                        message = "候选事实已确认并进入 Canon",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "确认 Candidate 失败") }
            }
        }
    }

    fun rejectCandidateFact(candidateId: String) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                projects.saveStructure(CandidateCanonEngine.reject(snapshot, candidateId), draft)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isSaving = false,
                        message = "候选事实已拒绝，不会进入 Canon",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "拒绝 Candidate 失败") }
            }
        }
    }

'''
text = replace_between(text, '    fun reviewCommittedChapter() {\n', '    fun applyMemoryOnly() {\n', review_fn, 'review')

# Remove old direct-memory helper entirely.
text = replace_between(text, '    fun applyMemoryOnly() {\n', '    fun advanceToNext(optionIndex: Int?) {\n', '', 'old direct memory')

# Remove direct AgentMemoryApplier from next-chapter path.
old_base = '''                val review = current.review
                val base = if (review != null && !current.memoryApplied) {
                    val updated = AgentMemoryApplier.apply(snapshot, draft.chapterNumber, review)
                    projects.saveStructure(updated, draft)
                } else {
                    projects.saveStructure(snapshot, draft)
                }

                val option = optionIndex?.let { review?.nextOptions?.getOrNull(it) }
'''
new_base = '''                val review = current.review
                val pendingForChapter = snapshot.candidateFacts.any {
                    it.sourceChapter == draft.chapterNumber && it.status == com.xiguli.langhuan.domain.CandidateFactStatus.PENDING
                }
                require(!pendingForChapter) { "当前章节还有待确认 Candidate。请先确认或拒绝这些事实，再进入下一章，避免下一章缺少关键连续性状态。" }
                val base = projects.saveStructure(snapshot, draft)

                val option = optionIndex?.let { review?.nextOptions?.getOrNull(it) }
'''
text = replace_once(text, old_base, new_base, 'advance candidate gate')
text = text.replace('                        streamPreview = "",\n                        result = null,', '                        streamPreview = "",\n                        runEvents = emptyList(),\n                        result = null,')
path.write_text(text)


# --- WritingFlowPage ----------------------------------------------------------
path = Path('app/src/main/java/com/xiguli/langhuan/ui/WritingFlowPage.kt')
text = path.read_text()
text = replace_once(text, 'import com.xiguli.langhuan.domain.IssueSeverity\n', 'import com.xiguli.langhuan.domain.CandidateFactRisk\nimport com.xiguli.langhuan.domain.CandidateFactStatus\nimport com.xiguli.langhuan.domain.IssueSeverity\n', 'page candidate imports')

# Run inspector directly under writing header so it stays visible during all stages.
text = replace_once(
    text,
    '''            item {
                FlowCard {
                    FlowTitle(Icons.Rounded.Route, "① 场景规划",''',
    '''            if (state.runEvents.isNotEmpty()) {
                item { RunInspectorPanel(state.runEvents, "写作 Run Inspector") }
            }

            item {
                FlowCard {
                    FlowTitle(Icons.Rounded.Route, "① 场景规划",''',
    'writing inspector',
)
text = text.replace('Text(if (state.isGenerating) "实时生成预览" else "本次生成结果", fontWeight = FontWeight.Bold)', 'Text(if (state.isGenerating) state.runEvents.lastOrNull { it.status == com.xiguli.langhuan.engine.RunStatus.RUNNING }?.stage?.label ?: "实时生成预览" else "本次生成结果", fontWeight = FontWeight.Bold)')

# Replace review/memory card with Candidate workflow.
old_button = '''                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::applyMemoryOnly,
                            enabled = !state.memoryApplied && !state.busy && review.memoryActions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Memory, null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (state.memoryApplied) "事实记忆已写入" else "确认并写入事实记忆")
                        }
'''
new_button = '''                        Spacer(Modifier.height(10.dp))
                        Text(
                            "这些提取项已经进入 Candidate；未经本地证明或你的确认，不会进入人物、时间线、伏笔、信息边界或 RAG。",
                            color = LocalMiuixTokens.current.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val pendingCandidates = snapshot.candidateFacts.filter {
                            it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING
                        }
                        if (pendingCandidates.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("待确认 Candidate · ${pendingCandidates.size} 条", fontWeight = FontWeight.Bold)
                            pendingCandidates.take(16).forEach { fact ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).squircleClip(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("${fact.kind} · ${if (fact.risk == CandidateFactRisk.HIGH) "高风险" else if (fact.risk == CandidateFactRisk.MEDIUM) "需确认" else "低风险"}", fontWeight = FontWeight.Bold)
                                        Text(fact.subject, color = MaterialTheme.colorScheme.primary)
                                        Text(fact.after, style = MaterialTheme.typography.bodySmall)
                                        if (fact.evidence.isNotBlank()) Text("依据：${fact.evidence}", style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { viewModel.rejectCandidateFact(fact.id) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("拒绝") }
                                            Button(onClick = { viewModel.confirmCandidateFact(fact.id) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("确认进 Canon") }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text("当前章节没有待确认 Candidate，可以安全进入下一章。", color = LocalMiuixTokens.current.success, fontWeight = FontWeight.SemiBold)
                        }
'''
text = replace_once(text, old_button, new_button, 'candidate panel')

# Gate next chapter buttons visibly while pending Candidate exists.
text = replace_once(
    text,
    '''                item {
                    FlowCard {
                        FlowTitle(Icons.Rounded.ArrowForward, "⑤ 下一章", "先选方向，再进入下一章；新一轮仍会从场景规划开始。")
                        Spacer(Modifier.height(10.dp))
''',
    '''                item {
                    FlowCard {
                        val pendingBeforeNext = snapshot.candidateFacts.count {
                            it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING
                        }
                        FlowTitle(Icons.Rounded.ArrowForward, "⑤ 下一章", "先处理当前章 Candidate，再选方向进入下一章；新一轮仍会从场景规划开始。")
                        Spacer(Modifier.height(10.dp))
                        if (pendingBeforeNext > 0) {
                            Text("还有 $pendingBeforeNext 条候选事实待确认/拒绝。为避免下一章在错误状态上续写，先处理上面的 Candidate。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
''',
    'next candidate warning',
)
text = text.replace('Button(onClick = { viewModel.advanceToNext(null) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())', 'Button(onClick = { viewModel.advanceToNext(null) }, enabled = !state.busy && pendingBeforeNext == 0, modifier = Modifier.fillMaxWidth())')
text = text.replace('Button(onClick = { viewModel.advanceToNext(index) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())', 'Button(onClick = { viewModel.advanceToNext(index) }, enabled = !state.busy && pendingBeforeNext == 0, modifier = Modifier.fillMaxWidth())')
text = text.replace('listOf("场景", "正文", "审查", "记忆", "下一章")', 'listOf("场景", "正文", "审查", "事实", "下一章")')
path.write_text(text)
