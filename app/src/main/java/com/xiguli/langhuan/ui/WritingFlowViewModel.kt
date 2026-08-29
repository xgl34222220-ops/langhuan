package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AgentReview
import com.xiguli.langhuan.engine.CandidateCanonEngine
import com.xiguli.langhuan.engine.GenerationPipeline
import com.xiguli.langhuan.engine.NovelAgentEngine
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStage
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.engine.UniversalAiGateway
import com.xiguli.langhuan.engine.WorkspaceAiEngine
import com.xiguli.langhuan.engine.WritingFlowEngine
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WritingFlowMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
)

data class WritingFlowUiState(
    val novelId: String = "",
    val snapshot: StorySnapshot? = null,
    val draft: ChapterDraft? = null,
    val providerLabel: String = "",
    val workingScenes: List<ScenePlan> = emptyList(),
    val sceneNote: String = "",
    val sceneConversation: List<WritingFlowMessage> = emptyList(),
    val streamPreview: String = "",
    val runEvents: List<RunEvent> = emptyList(),
    val result: GenerationResult? = null,
    val review: AgentReview? = null,
    val isLoading: Boolean = false,
    val isPlanningScenes: Boolean = false,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val isReviewing: Boolean = false,
    val chapterCommitted: Boolean = false,
    val memoryApplied: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val ready: Boolean get() = snapshot != null && draft != null
    val sceneDirty: Boolean get() = draft != null && workingScenes != draft.scenePlan
    val busy: Boolean get() = isLoading || isPlanningScenes || isGenerating || isSaving || isReviewing
}

class WritingFlowViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val projects = StoryProjectManager(application)
    private val _state = MutableStateFlow(WritingFlowUiState())
    val state: StateFlow<WritingFlowUiState> = _state.asStateFlow()
    private var generationJob: Job? = null

    fun load(novelId: String) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.ready) return
        generationJob?.cancel()
        viewModelScope.launch {
            _state.value = WritingFlowUiState(novelId = novelId, isLoading = true)
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到这本小说")
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                Triple(loaded, provider?.name.orEmpty(), provider?.id)
            }.onSuccess { (loaded, label, _) ->
                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        providerLabel = label.ifBlank { "未配置 AI 服务" },
                        workingScenes = loaded.draft.scenePlan,
                        runEvents = emptyList(),
                        chapterCommitted = loaded.draft.content.isNotBlank(),
                        isLoading = false,
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "加载小说失败") }
            }
        }
    }

    private fun emitRun(event: RunEvent) {
        _state.update { state -> state.copy(runEvents = (state.runEvents + event).takeLast(96)) }
    }

    private fun emitRun(stage: RunStage, status: RunStatus, detail: String = "") {
        emitRun(RunEvent(stage = stage, status = status, detail = detail))
    }

    fun planScenes(instruction: String = "") {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        viewModelScope.launch {
            val gateway = runCatching { activeGateway() }.getOrElse {
                _state.update { state -> state.copy(error = it.message ?: "请先配置 AI 服务") }
                return@launch
            }
            val clean = instruction.trim()
            val conversation = if (clean.isBlank()) current.sceneConversation else {
                current.sceneConversation + WritingFlowMessage(role = "user", text = clean)
            }
            _state.update { it.copy(isPlanningScenes = true, error = null, sceneConversation = conversation) }
            runCatching {
                WritingFlowEngine(gateway).planCurrentChapter(
                    snapshot = snapshot,
                    chapter = draft,
                    currentScenes = current.workingScenes.ifEmpty { draft.scenePlan },
                    conversation = conversation.map { it.role to it.text },
                    instruction = clean,
                )
            }.onSuccess { suggestion ->
                _state.update {
                    it.copy(
                        isPlanningScenes = false,
                        workingScenes = suggestion.scenes,
                        sceneNote = suggestion.note,
                        sceneConversation = it.sceneConversation + WritingFlowMessage(
                            role = "assistant",
                            text = "场景计划已更新：${suggestion.note}",
                        ),
                        message = "AI 已重新编排本章场景，确认后可直接生成正文",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isPlanningScenes = false, error = error.message ?: "AI 场景规划失败") }
            }
        }
    }

    fun applyScenePlan() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy || current.workingScenes.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                projects.saveStructure(snapshot, draft.copy(scenePlan = current.workingScenes))
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        workingScenes = persisted.draft.scenePlan,
                        isSaving = false,
                        message = "场景计划已写入当前章节",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "保存场景计划失败") }
            }
        }
    }

    fun generate(extraInstruction: String = "") {
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

    fun cancelGeneration() {
        if (!_state.value.isGenerating) return
        generationJob?.cancel(CancellationException("用户停止生成"))
    }

    fun repairAndRegenerate() {
        val current = _state.value
        val result = current.result ?: return
        val repairs = result.issues
            .filter { it.severity != IssueSeverity.INFO }
            .joinToString("\n") { "- ${it.repairInstruction}" }
            .ifBlank { "保持现有章纲不变，修复所有一致性问题后重写正文。" }
        generate("上一版没有通过一致性审查。必须保留章纲目标和既定事实，按以下要求重写：\n$repairs")
    }

    fun commitAndReview() {
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

    fun reviewCommittedChapter() {
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

    fun advanceToNext(optionIndex: Int?) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy || draft.content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val review = current.review
                val pendingForChapter = snapshot.candidateFacts.any {
                    it.sourceChapter == draft.chapterNumber && it.status == com.xiguli.langhuan.domain.CandidateFactStatus.PENDING
                }
                require(!pendingForChapter) { "当前章节还有待确认 Candidate。请先确认或拒绝这些事实，再进入下一章，避免下一章缺少关键连续性状态。" }
                val base = projects.saveStructure(snapshot, draft)

                val option = optionIndex?.let { review?.nextOptions?.getOrNull(it) }
                val nextNumber = draft.chapterNumber + 1
                val existingNext = base.snapshot.outline.firstOrNull {
                    it.level == OutlineLevel.CHAPTER && it.order == nextNumber
                }

                if (existingNext != null) {
                    val selected = projects.selectChapter(base.snapshot.novel.id, nextNumber)
                        ?: error("无法载入已经规划好的第${nextNumber}章")
                    if (option != null) {
                        val updatedNode = existingNext.copy(
                            title = option.title.ifBlank { existingNext.title },
                            objective = option.objective.ifBlank { existingNext.objective },
                            conflict = option.conflict.ifBlank { existingNext.conflict },
                            turningPoint = option.turningPoint.ifBlank { existingNext.turningPoint },
                        )
                        val updatedOutline = selected.snapshot.outline.map { node ->
                            if (node.id == existingNext.id) updatedNode else node
                        }
                        projects.saveStructure(
                            snapshot = selected.snapshot.copy(outline = updatedOutline),
                            draft = selected.draft.copy(
                                title = updatedNode.title,
                                objective = updatedNode.objective,
                            ),
                        )
                    } else {
                        selected
                    }
                } else if (option != null) {
                    projects.createChapter(
                        snapshot = base.snapshot,
                        title = option.title,
                        objective = option.objective,
                        conflict = option.conflict,
                        turningPoint = option.turningPoint,
                    )
                } else {
                    val gateway = activeGateway()
                    val plan = WorkspaceAiEngine(gateway).planNextChapter(base.snapshot, base.draft)
                    projects.createChapter(
                        snapshot = base.snapshot,
                        title = plan.title,
                        objective = plan.objective,
                        conflict = plan.conflict,
                        turningPoint = plan.turningPoint,
                        scenePlan = plan.scenes,
                    )
                }
            }.onSuccess { next ->
                projects.setActiveStoryId(next.snapshot.novel.id)
                _state.update {
                    it.copy(
                        snapshot = next.snapshot,
                        draft = next.draft,
                        workingScenes = next.draft.scenePlan,
                        sceneNote = "",
                        sceneConversation = emptyList(),
                        streamPreview = "",
                        runEvents = emptyList(),
                        result = null,
                        review = null,
                        isSaving = false,
                        chapterCommitted = next.draft.content.isNotBlank(),
                        memoryApplied = false,
                        message = "已进入第${next.draft.chapterNumber}章。先确认场景计划，再开始正文生成。",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "进入下一章失败") }
            }
        }
    }

    fun clearNotice() = _state.update { it.copy(message = null, error = null) }

    private suspend fun activeGateway(): UniversalAiGateway {
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
            ?: error("请先到设置添加并启用一个 AI 服务")
        val config = repository.providerConfig(provider.id) ?: error("当前 AI 服务配置不可用")
        _state.update { it.copy(providerLabel = "${provider.name} · ${provider.model}") }
        return UniversalAiGateway(config)
    }
}
