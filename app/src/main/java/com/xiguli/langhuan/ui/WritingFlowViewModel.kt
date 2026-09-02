package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.LanghuanApplication
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AgentReview
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.AppChapterRunStore
import com.xiguli.langhuan.engine.ChapterRunCoordinator
import com.xiguli.langhuan.engine.ChapterRunRuntimeState
import com.xiguli.langhuan.engine.ChapterRuntimeTaskKind
import com.xiguli.langhuan.engine.PersistentChapterRunCheckpointStore
import com.xiguli.langhuan.engine.ProjectRuntimePhase
import com.xiguli.langhuan.engine.ProjectRuntimeSkillAudit
import com.xiguli.langhuan.engine.ProjectRuntimeSkillPlan
import com.xiguli.langhuan.engine.ProjectRuntimeSkillPlanner
import com.xiguli.langhuan.engine.ReferenceDnaAwareAiGateway
import com.xiguli.langhuan.engine.ReferenceDnaBindingStore
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunResumePolicy
import com.xiguli.langhuan.engine.TaskDispatchingAiGateway
import com.xiguli.langhuan.engine.TaskModelRouter
import com.xiguli.langhuan.engine.WorkspaceAiEngine
import com.xiguli.langhuan.engine.WritingFlowEngine
import java.util.UUID
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
    /** Planned runtime capabilities are not proof that they executed. */
    val runtimePlan: ProjectRuntimeSkillPlan? = null,
    /** Evidence-backed receipts rebuilt from real RunEvent records. */
    val runtimeAudit: ProjectRuntimeSkillAudit? = null,
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
    private val referenceDna = ReferenceDnaBindingStore(application)
    private val chapterRuns = ChapterRunCoordinator(AppChapterRunStore(repository, projects), PersistentChapterRunCheckpointStore(application))
    private val runtime = (application as LanghuanApplication).chapterRunRuntime
    private val _state = MutableStateFlow(WritingFlowUiState())
    val state: StateFlow<WritingFlowUiState> = _state.asStateFlow()

    init { viewModelScope.launch { runtime.state.collect(::syncRuntimeState) } }

    fun load(novelId: String) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.ready) { syncRuntimeState(runtime.state.value); return }
        viewModelScope.launch {
            _state.value = WritingFlowUiState(novelId = novelId, isLoading = true)
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到这本小说")
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                Triple(loaded, provider?.name.orEmpty(), provider?.id)
            }.onSuccess { (loaded, label, _) ->
                val binding = referenceDna.summary(novelId)
                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot, draft = loaded.draft,
                        providerLabel = buildString { append(label.ifBlank { "未配置 AI 服务" }); if (binding.count > 0) append(" · DNA ${binding.count}本") },
                        workingScenes = loaded.draft.scenePlan, runEvents = emptyList(), runtimePlan = null, runtimeAudit = null,
                        chapterCommitted = loaded.draft.content.isNotBlank(),
                        isLoading = false, error = null,
                        message = if (binding.count > 0) binding.label else it.message,
                    )
                }
                val live = runtime.state.value
                val hasRuntimeState = live.matches(loaded.snapshot.novel.id, loaded.draft.chapterNumber) && (live.active || live.result != null || live.review != null || live.message != null || live.error != null)
                if (hasRuntimeState) syncRuntimeState(live) else restoreDurableRun(loaded.snapshot, loaded.draft)
            }.onFailure { error -> _state.update { it.copy(isLoading = false, error = error.message ?: "加载小说失败") } }
        }
    }

    fun planScenes(instruction: String = "") {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active) return
        viewModelScope.launch {
            val gateway = runCatching { activeGateway() }.getOrElse { error -> _state.update { it.copy(error = error.message ?: "请先配置 AI 服务") }; return@launch }
            val clean = instruction.trim()
            val conversation = if (clean.isBlank()) current.sceneConversation else current.sceneConversation + WritingFlowMessage(role = "user", text = clean)
            val usage = referenceDna.usage(snapshot.novel.id, listOf(draft.title, draft.objective, clean, current.workingScenes.joinToString(" ") { it.purpose + " " + it.conflict }).joinToString(" "), com.xiguli.langhuan.engine.ReferenceDnaPurpose.SCENE)
            _state.update { it.copy(isPlanningScenes = true, error = null, sceneConversation = conversation, message = usage.label.takeIf(String::isNotBlank)) }
            runCatching {
                WritingFlowEngine(gateway).planCurrentChapter(snapshot, draft, current.workingScenes.ifEmpty { draft.scenePlan }, conversation.map { it.role to it.text }, clean)
            }.onSuccess { suggestion ->
                _state.update {
                    it.copy(
                        isPlanningScenes = false, workingScenes = suggestion.scenes, sceneNote = suggestion.note,
                        sceneConversation = it.sceneConversation + WritingFlowMessage(role = "assistant", text = "场景计划已更新：${suggestion.note}"),
                        message = if (usage.reportCount > 0) "AI 已重新编排本章场景 · ${usage.label}" else "AI 已重新编排本章场景，确认后可直接生成正文",
                    )
                }
            }.onFailure { error -> _state.update { it.copy(isPlanningScenes = false, error = error.message ?: "AI 场景规划失败") } }
        }
    }

    fun applyScenePlan() {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active || current.workingScenes.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { projects.saveStructure(snapshot, draft.copy(scenePlan = current.workingScenes)) }
                .onSuccess { persisted -> _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, workingScenes = persisted.draft.scenePlan, isSaving = false, message = "场景计划已写入当前章节") } }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "保存场景计划失败") } }
        }
    }

    fun generate(extraInstruction: String = "") { submitGenerate(extraInstruction, forceNew = false) }

    private fun submitGenerate(extraInstruction: String, forceNew: Boolean) {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active) return
        val workingDraft = draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan })
        val usage = referenceDna.usage(snapshot.novel.id, listOf(draft.title, draft.objective, extraInstruction, workingDraft.scenePlan.joinToString(" ") { it.purpose + " " + it.conflict + " " + it.outcome }).joinToString(" "), com.xiguli.langhuan.engine.ReferenceDnaPurpose.PROSE)
        _state.update { it.copy(isGenerating = true, streamPreview = "", runEvents = emptyList(), runtimePlan = null, runtimeAudit = null, result = null, review = null, memoryApplied = false, message = usage.label.takeIf(String::isNotBlank), error = null) }
        runtime.generate(snapshot, workingDraft, 2_800, extraInstruction, forceNew)
    }

    fun cancelGeneration() { runtime.stopCurrentGeneration() }

    fun repairAndRegenerate() {
        val current = _state.value; val result = current.result ?: return
        val repairs = result.issues.filter { it.severity != IssueSeverity.INFO }.joinToString("\n") { "- ${it.repairInstruction}" }.ifBlank { "保持现有章纲不变，修复所有一致性问题后重写正文。" }
        val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active) return
        runtime.abandon(snapshot, draft)
        submitGenerate("上一版没有通过一致性审查。必须保留章纲目标和既定事实，按以下要求重写：\n$repairs", true)
    }

    fun commitAndReview() {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return; val result = current.result ?: return
        if (!result.canCommit || current.busy || runtime.state.value.active) return
        _state.update { it.copy(isSaving = true, error = null) }
        runtime.commit(snapshot, draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan }), result)
    }

    fun reviewCommittedChapter() {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active || draft.content.isBlank()) return
        _state.update { it.copy(isReviewing = true, error = null, review = null) }; runtime.review(snapshot, draft)
    }

    fun confirmCandidateFact(candidateId: String) {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { chapterRuns.confirmCandidate(snapshot, draft, candidateId) }
                .onSuccess { persisted -> _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, isSaving = false, message = "候选事实已确认并进入 Canon") } }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "确认 Candidate 失败") } }
        }
    }

    fun rejectCandidateFact(candidateId: String) {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { chapterRuns.rejectCandidate(snapshot, draft, candidateId) }
                .onSuccess { persisted -> _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, isSaving = false, message = "候选事实已拒绝，不会进入 Canon") } }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "拒绝 Candidate 失败") } }
        }
    }

    fun advanceToNext(optionIndex: Int?) {
        val current = _state.value; val snapshot = current.snapshot ?: return; val draft = current.draft ?: return
        if (current.busy || runtime.state.value.active || draft.content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val review = current.review
                val pendingForChapter = snapshot.candidateFacts.any { it.sourceChapter == draft.chapterNumber && it.status == com.xiguli.langhuan.domain.CandidateFactStatus.PENDING }
                require(!pendingForChapter) { "当前章节还有待确认 Candidate。请先确认或拒绝这些事实，再进入下一章，避免下一章缺少关键连续性状态。" }
                val base = projects.saveStructure(snapshot, draft)
                val option = optionIndex?.let { review?.nextOptions?.getOrNull(it) }
                val nextNumber = draft.chapterNumber + 1
                val existingNext = base.snapshot.outline.firstOrNull { it.level == OutlineLevel.CHAPTER && it.order == nextNumber }
                if (existingNext != null) {
                    val selected = projects.selectChapter(base.snapshot.novel.id, nextNumber) ?: error("无法载入已经规划好的第${nextNumber}章")
                    if (option != null) {
                        val updatedNode = existingNext.copy(title = option.title.ifBlank { existingNext.title }, objective = option.objective.ifBlank { existingNext.objective }, conflict = option.conflict.ifBlank { existingNext.conflict }, turningPoint = option.turningPoint.ifBlank { existingNext.turningPoint })
                        val updatedOutline = selected.snapshot.outline.map { if (it.id == existingNext.id) updatedNode else it }
                        projects.saveStructure(selected.snapshot.copy(outline = updatedOutline), selected.draft.copy(title = updatedNode.title, objective = updatedNode.objective))
                    } else selected
                } else if (option != null) {
                    projects.createChapter(base.snapshot, option.title, option.objective, option.conflict, option.turningPoint)
                } else {
                    val gateway = activeGateway(); val plan = WorkspaceAiEngine(gateway).planNextChapter(base.snapshot, base.draft)
                    projects.createChapter(base.snapshot, plan.title, plan.objective, plan.conflict, plan.turningPoint, plan.scenes)
                }
            }.onSuccess { next ->
                projects.setActiveStoryId(next.snapshot.novel.id); runtime.clearTerminalState(snapshot.novel.id, draft.chapterNumber)
                _state.update { it.copy(snapshot = next.snapshot, draft = next.draft, workingScenes = next.draft.scenePlan, sceneNote = "", sceneConversation = emptyList(), streamPreview = "", runEvents = emptyList(), runtimePlan = null, runtimeAudit = null, result = null, review = null, isSaving = false, chapterCommitted = next.draft.content.isNotBlank(), memoryApplied = false, message = "已进入第${next.draft.chapterNumber}章。先确认场景计划，再开始正文生成。") }
            }.onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "进入下一章失败") } }
        }
    }

    fun clearNotice() = _state.update { it.copy(message = null, error = null) }

    /** The chapter editor persists through a separate store; force the next writing-page entry to reload it. */
    fun invalidateAfterExternalEdit(novelId: String) {
        val current = _state.value
        if (current.novelId == novelId && !current.busy && !runtime.state.value.active) {
            _state.value = WritingFlowUiState()
        }
    }

    private fun syncRuntimeState(run: ChapterRunRuntimeState) {
        val current = _state.value; val draft = current.draft ?: return
        if (!run.matches(current.novelId, draft.chapterNumber)) return
        val runtimeDraft = run.draft
        _state.update { state ->
            state.copy(
                snapshot = run.snapshot ?: state.snapshot, draft = runtimeDraft ?: state.draft, providerLabel = run.providerLabel.ifBlank { state.providerLabel },
                workingScenes = if (runtimeDraft != null && run.taskKind != ChapterRuntimeTaskKind.GENERATE) runtimeDraft.scenePlan else state.workingScenes,
                streamPreview = run.preview, runEvents = run.events, runtimePlan = run.runtimePlan, runtimeAudit = run.runtimeAudit,
                result = run.result, review = run.review,
                isGenerating = run.active && run.taskKind == ChapterRuntimeTaskKind.GENERATE, isSaving = run.active && run.taskKind == ChapterRuntimeTaskKind.COMMIT,
                isReviewing = run.active && run.taskKind == ChapterRuntimeTaskKind.REVIEW,
                chapterCommitted = runtimeDraft?.content?.isNotBlank() ?: state.chapterCommitted, memoryApplied = false,
                message = run.message ?: state.message, error = run.error ?: state.error,
            )
        }
    }

    private fun restoreDurableRun(snapshot: StorySnapshot, draft: ChapterDraft) {
        val recovery = chapterRuns.recover(snapshot, draft) ?: return
        val plan = if (recovery.policy == RunResumePolicy.RESUME_REVIEW) {
            ProjectRuntimeSkillPlanner.manualReview(snapshot, draft)
        } else {
            ProjectRuntimeSkillPlanner.build(snapshot, draft, referenceDna.summary(snapshot.novel.id).count)
        }
        val phases = when (recovery.policy) {
            RunResumePolicy.RESUME_REVIEW -> setOf(ProjectRuntimePhase.MANUAL_REVIEW)
            RunResumePolicy.RESUME_POST_COMMIT -> setOf(ProjectRuntimePhase.GENERATION, ProjectRuntimePhase.POST_COMMIT)
            RunResumePolicy.CONTINUE_GENERATION,
            RunResumePolicy.RESTORE_RESULT -> setOf(ProjectRuntimePhase.GENERATION)
            RunResumePolicy.NONE -> emptySet()
        }
        val audit = ProjectRuntimeSkillPlanner.audit(plan, recovery.events, phases, finalize = false)
        _state.update {
            it.copy(
                streamPreview = recovery.preview,
                result = recovery.result,
                runEvents = recovery.events,
                runtimePlan = plan,
                runtimeAudit = audit,
                message = recovery.message,
            )
        }
    }

    private suspend fun activeGateway(): AiGateway {
        val routed = TaskDispatchingAiGateway(TaskModelRouter(getApplication<Application>()).snapshot())
        val novelId = _state.value.novelId
        val binding = referenceDna.summary(novelId)
        _state.update { it.copy(providerLabel = routed.summary + if (binding.count > 0) " · DNA ${binding.count}本" else "") }
        return ReferenceDnaAwareAiGateway(getApplication<Application>(), novelId, routed)
    }
}
