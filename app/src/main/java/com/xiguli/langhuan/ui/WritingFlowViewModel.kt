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
import com.xiguli.langhuan.engine.AgentMemoryApplier
import com.xiguli.langhuan.engine.AgentReview
import com.xiguli.langhuan.engine.GenerationPipeline
import com.xiguli.langhuan.engine.NovelAgentEngine
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
                        streamPreview = "正在检索与本章最相关的历史剧情……",
                        result = null,
                        review = null,
                        memoryApplied = false,
                        error = null,
                    )
                }
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
                val memories = repository.retrieveRelevantMemories(
                    snapshot.novel.id,
                    query,
                    workingDraft.chapterNumber,
                    10,
                )
                val promptSnapshot = snapshot.copy(
                    recentSummaries = (snapshot.recentSummaries + memories).distinct().takeLast(20)
                )
                val result = GenerationPipeline(gateway).generate(
                    GenerationRequest(
                        snapshot = promptSnapshot,
                        chapter = workingDraft,
                        targetWords = 2_800,
                        extraInstruction = extraInstruction.trim(),
                    )
                ) { preview ->
                    _state.update { state -> state.copy(streamPreview = preview) }
                }
                _state.update {
                    it.copy(
                        isGenerating = false,
                        streamPreview = result.chapter.content,
                        result = result,
                        message = if (result.canCommit) "正文已生成并通过阻断级检查" else "正文已生成，但存在阻断级一致性问题",
                    )
                }
            } catch (_: CancellationException) {
                _state.update {
                    it.copy(
                        isGenerating = false,
                        message = "已停止本次生成；已收到的内容不会自动二次请求",
                    )
                }
            } catch (error: Throwable) {
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
            runCatching {
                repository.commitGenerated(
                    snapshot = snapshot,
                    draft = draft.copy(scenePlan = current.workingScenes.ifEmpty { draft.scenePlan }),
                    generated = result.chapter,
                )
            }.onSuccess { persisted ->
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
                        message = "正文与版本已保存，开始自动复盘事实、伏笔和人物状态",
                    )
                }
                reviewCommittedChapter()
            }.onFailure { error ->
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
            _state.update { it.copy(isReviewing = true, error = null, review = null) }
            runCatching { NovelAgentEngine(gateway).reviewChapter(snapshot, draft) }
                .onSuccess { review ->
                    _state.update {
                        it.copy(
                            isReviewing = false,
                            review = review,
                            memoryApplied = false,
                            message = "Agent 已完成章节复盘，等待你确认事实记忆和下一章方向",
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isReviewing = false, error = error.message ?: "章节复盘失败") }
                }
        }
    }

    fun applyMemoryOnly() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        val review = current.review ?: return
        if (current.busy || current.memoryApplied) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val updated = AgentMemoryApplier.apply(snapshot, draft.chapterNumber, review)
                projects.saveStructure(updated, draft)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isSaving = false,
                        memoryApplied = true,
                        message = "人物、关系、时间线与伏笔变化已写入长期记忆",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "写入 Agent 记忆失败") }
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
                val base = if (review != null && !current.memoryApplied) {
                    val updated = AgentMemoryApplier.apply(snapshot, draft.chapterNumber, review)
                    projects.saveStructure(updated, draft)
                } else {
                    projects.saveStructure(snapshot, draft)
                }

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
