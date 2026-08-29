from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[:start] + replacement + "\n\n" + text[end:]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


studio_path = ROOT / "app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt"
studio = studio_path.read_text()
for line in [
    "import com.xiguli.langhuan.domain.GenerationRequest\n",
    "import com.xiguli.langhuan.engine.CandidateCanonEngine\n",
    "import com.xiguli.langhuan.engine.AutonomousExecutionEngine\n",
    "import com.xiguli.langhuan.engine.GenerationPipeline\n",
]:
    studio = studio.replace(line, "")
studio = replace_once(
    studio,
    "import com.xiguli.langhuan.engine.AgentReview\n",
    "import com.xiguli.langhuan.engine.AgentReview\nimport com.xiguli.langhuan.engine.AppChapterRunStore\nimport com.xiguli.langhuan.engine.ChapterRunCoordinator\n",
    "studio coordinator imports",
)
studio = replace_once(
    studio,
    "    private val projects = StoryProjectManager(application)\n",
    "    private val projects = StoryProjectManager(application)\n    private val chapterRuns = ChapterRunCoordinator(AppChapterRunStore(repository, projects))\n",
    "studio coordinator property",
)

studio_generate_commit = '''    fun generateChapter() {
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
            val gateway = configuredGateway() ?: DemoAiGateway()
            runCatching {
                chapterRuns.generate(
                    snapshot = current.snapshot,
                    draft = current.draft,
                    gateway = gateway,
                    targetWords = 2_500,
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

    fun commitResult() {
        val current = _state.value
        val result = current.result ?: return
        if (!result.canCommit || current.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                chapterRuns.commit(
                    snapshot = current.snapshot,
                    draft = current.draft,
                    result = result,
                    gateway = configuredGateway(),
                    onRunEvent = ::emitRun,
                )
            }.onSuccess { outcome ->
                _state.update {
                    it.copy(
                        snapshot = outcome.persisted.snapshot,
                        draft = outcome.persisted.draft,
                        isSaving = false,
                        isAgentReviewing = false,
                        isAutonomousPlanning = false,
                        isDraftDirty = false,
                        streamPreview = "",
                        result = null,
                        agentReview = outcome.review,
                        message = outcome.summary(),
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSaving = false,
                        isAgentReviewing = false,
                        isAutonomousPlanning = false,
                        error = error.message ?: "保存章节失败",
                    )
                }
            }
        }
    }'''
studio = replace_between(
    studio,
    "    fun generateChapter() {",
    "    fun dismissResult()",
    studio_generate_commit,
)

studio_review = '''    fun runChapterReview() {
        val current = _state.value
        if (busy(current)) return
        if (current.draft.content.isBlank()) {
            _state.update { it.copy(error = "当前章节还没有正文，无法复盘") }
            return
        }
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "Agent 复盘需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isAgentReviewing = true, error = null, agentReview = null) }
            runCatching {
                chapterRuns.reviewSavedChapter(
                    snapshot = current.snapshot,
                    draft = current.draft,
                    gateway = gateway,
                    onRunEvent = ::emitRun,
                )
            }.onSuccess { reviewed ->
                _state.update {
                    it.copy(
                        snapshot = reviewed.persisted.snapshot,
                        draft = reviewed.persisted.draft,
                        isAgentReviewing = false,
                        agentReview = reviewed.review,
                        message = "Agent 复盘完成；${reviewed.stagedCount} 条事实已进入 Candidate${if (reviewed.autoConfirmedCount > 0) "，${reviewed.autoConfirmedCount} 条低风险状态自动确认" else ""}",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isAgentReviewing = false, error = error.message ?: "Agent 章节复盘失败") }
            }
        }
    }'''
studio = replace_between(
    studio,
    "    fun runChapterReview() {",
    "    fun runFullBookAudit()",
    studio_review,
)

studio_candidates = '''    fun confirmCandidateFact(candidateId: String) {
        val current = _state.value
        if (busy(current)) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { chapterRuns.confirmCandidate(current.snapshot, current.draft, candidateId) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            isSaving = false,
                            message = "候选事实已确认并写入 Canon",
                        )
                    }
                    refreshWorkspace()
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "候选事实无法写入 Canon") }
                }
        }
    }

    fun rejectCandidateFact(candidateId: String) {
        val current = _state.value
        if (busy(current)) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { chapterRuns.rejectCandidate(current.snapshot, current.draft, candidateId) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            isSaving = false,
                            message = "候选事实已拒绝；不会进入 Canon",
                        )
                    }
                    refreshWorkspace()
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "拒绝 Candidate 失败") }
                }
        }
    }'''
studio = replace_between(
    studio,
    "    fun confirmCandidateFact(candidateId: String) {",
    "    fun useAgentNextOption(index: Int)",
    studio_candidates,
)
studio_path.write_text(studio)


writing_path = ROOT / "app/src/main/java/com/xiguli/langhuan/ui/WritingFlowViewModel.kt"
writing = writing_path.read_text()
for line in [
    "import com.xiguli.langhuan.domain.GenerationRequest\n",
    "import com.xiguli.langhuan.engine.CandidateCanonEngine\n",
    "import com.xiguli.langhuan.engine.GenerationPipeline\n",
    "import com.xiguli.langhuan.engine.NovelAgentEngine\n",
]:
    writing = writing.replace(line, "")
writing = replace_once(
    writing,
    "import com.xiguli.langhuan.engine.AgentReview\n",
    "import com.xiguli.langhuan.engine.AgentReview\nimport com.xiguli.langhuan.engine.AppChapterRunStore\nimport com.xiguli.langhuan.engine.ChapterRunCoordinator\n",
    "writing coordinator imports",
)
writing = replace_once(
    writing,
    "    private val projects = StoryProjectManager(application)\n",
    "    private val projects = StoryProjectManager(application)\n    private val chapterRuns = ChapterRunCoordinator(AppChapterRunStore(repository, projects))\n",
    "writing coordinator property",
)

writing_generate = '''    fun generate(extraInstruction: String = "") {
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
                val workingDraft = draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan })
                val result = chapterRuns.generate(
                    snapshot = snapshot,
                    draft = workingDraft,
                    gateway = gateway,
                    targetWords = 2_800,
                    extraInstruction = extraInstruction,
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
    }'''
writing = replace_between(
    writing,
    "    fun generate(extraInstruction: String = \"\") {",
    "    fun cancelGeneration()",
    writing_generate,
)

writing_commit = '''    fun commitAndReview() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        val result = current.result ?: return
        if (!result.canCommit || current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val gateway = activeGateway()
                chapterRuns.commit(
                    snapshot = snapshot,
                    draft = draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan }),
                    result = result,
                    gateway = gateway,
                    onRunEvent = ::emitRun,
                )
            }.onSuccess { outcome ->
                _state.update {
                    it.copy(
                        snapshot = outcome.persisted.snapshot,
                        draft = outcome.persisted.draft,
                        workingScenes = outcome.persisted.draft.scenePlan,
                        result = null,
                        streamPreview = "",
                        isSaving = false,
                        isReviewing = false,
                        chapterCommitted = true,
                        review = outcome.review,
                        memoryApplied = false,
                        message = outcome.summary(),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, isReviewing = false, error = error.message ?: "保存正文失败") }
            }
        }
    }'''
writing = replace_between(
    writing,
    "    fun commitAndReview() {",
    "    fun reviewCommittedChapter()",
    writing_commit,
)

writing_review = '''    fun reviewCommittedChapter() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.isReviewing || current.isGenerating || current.isSaving || draft.content.isBlank()) return
        viewModelScope.launch {
            val gateway = runCatching { activeGateway() }.getOrElse {
                _state.update { state -> state.copy(error = it.message ?: "Agent 复盘需要 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isReviewing = true, error = null, review = null) }
            runCatching {
                chapterRuns.reviewSavedChapter(
                    snapshot = snapshot,
                    draft = draft,
                    gateway = gateway,
                    onRunEvent = ::emitRun,
                )
            }.onSuccess { reviewed ->
                _state.update {
                    it.copy(
                        snapshot = reviewed.persisted.snapshot,
                        draft = reviewed.persisted.draft,
                        isReviewing = false,
                        review = reviewed.review,
                        memoryApplied = false,
                        message = "Agent 已完成复盘；${reviewed.stagedCount} 条候选事实已进入 Candidate",
                    )
                }
            }.onFailure { error ->
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
            runCatching { chapterRuns.confirmCandidate(snapshot, draft, candidateId) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            isSaving = false,
                            message = "候选事实已确认并进入 Canon",
                        )
                    }
                }
                .onFailure { error ->
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
            runCatching { chapterRuns.rejectCandidate(snapshot, draft, candidateId) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            isSaving = false,
                            message = "候选事实已拒绝，不会进入 Canon",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "拒绝 Candidate 失败") }
                }
        }
    }'''
writing = replace_between(
    writing,
    "    fun reviewCommittedChapter() {",
    "    fun advanceToNext(optionIndex: Int?)",
    writing_review,
)
writing_path.write_text(writing)


gradle_path = ROOT / "app/build.gradle.kts"
gradle = gradle_path.read_text()
gradle = replace_once(gradle, 'versionCode = 62', 'versionCode = 63', 'version code')
gradle = replace_once(gradle, 'versionName = "0.26.4-alpha01"', 'versionName = "0.26.5-alpha01"', 'version name')
gradle_path.write_text(gradle)

print("chapter run coordinator migration applied")
