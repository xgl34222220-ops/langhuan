package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.LanghuanApplication
import com.xiguli.langhuan.data.DemoStoryRepository
import com.xiguli.langhuan.data.ExportFormat
import com.xiguli.langhuan.data.NewStoryRequest
import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.ProjectBackupManager
import com.xiguli.langhuan.data.ProviderSaveRequest
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.data.StoredChapterVersion
import com.xiguli.langhuan.data.StoryExchange
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.StoryShelfItem
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.Foreshadowing
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent
import com.xiguli.langhuan.engine.AutonomousStoryPlanner
import com.xiguli.langhuan.engine.AgentReview
import com.xiguli.langhuan.engine.AppChapterRunStore
import com.xiguli.langhuan.engine.ChapterRunCoordinator
import com.xiguli.langhuan.engine.ChapterRunRuntimeState
import com.xiguli.langhuan.engine.ChapterRuntimeTaskKind
import com.xiguli.langhuan.engine.PersistentChapterRunCheckpointStore
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.ApiProtocol
import com.xiguli.langhuan.engine.ChapterPlanSuggestion
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.FullBookEditorEngine
import com.xiguli.langhuan.engine.NovelAgentEngine
import com.xiguli.langhuan.engine.ProviderAutoDetector
import com.xiguli.langhuan.engine.ProviderDiscovery
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.UniversalAiGateway
import com.xiguli.langhuan.engine.WorkspaceAiEngine
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SavedProviderUi(
    val id: String,
    val name: String,
    val baseUrl: String,
    val protocol: ApiProtocol,
    val model: String,
    val supportsJsonMode: Boolean,
    val isDefault: Boolean,
    val hasApiKey: Boolean,
)

data class ChapterVersionUi(
    val id: String,
    val chapterNumber: Int,
    val version: Int,
    val title: String,
    val content: String,
    val summary: String,
    val createdAt: Long,
)

data class StoryShelfUi(
    val id: String,
    val title: String,
    val genre: String,
    val currentWords: Int,
    val targetWords: Int,
    val currentChapter: Int,
)

data class ChapterShelfUi(
    val number: Int,
    val title: String,
    val objective: String,
    val words: Int,
    val selected: Boolean,
)

data class RewriteSuggestion(
    val start: Int,
    val end: Int,
    val replacement: String,
    val instruction: String,
)

data class StudioUiState(
    val snapshot: StorySnapshot,
    val draft: ChapterDraft,
    val stories: List<StoryShelfUi> = emptyList(),
    val chapters: List<ChapterShelfUi> = emptyList(),
    val provider: ProviderUiState = ProviderUiState(),
    val versions: List<ChapterVersionUi> = emptyList(),
    val streamPreview: String = "",
    val runEvents: List<RunEvent> = emptyList(),
    val pendingPlan: ChapterPlanSuggestion? = null,
    val rewriteSuggestion: RewriteSuggestion? = null,
    val agentReview: AgentReview? = null,
    val isGenerating: Boolean = false,
    val isPlanning: Boolean = false,
    val isRewriting: Boolean = false,
    val isAgentReviewing: Boolean = false,
    val isAuditing: Boolean = false,
    val isAutonomousPlanning: Boolean = false,
    val isSaving: Boolean = false,
    val isCreatingStory: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val isRestoringVersion: Boolean = false,
    val isDraftDirty: Boolean = false,
    val result: GenerationResult? = null,
    val message: String? = null,
    val error: String? = null,
)

data class ProviderUiState(
    val savedProviders: List<SavedProviderUi> = emptyList(),
    val activeProviderId: String? = null,
    val editingProviderId: String? = null,
    val providerName: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    val isDetecting: Boolean = false,
    val isSaving: Boolean = false,
    val discovery: ProviderDiscovery? = null,
    val selectedModel: String = "",
    val manualModel: String = "",
    val error: String? = null,
) {
    val formModel: String get() = selectedModel.ifBlank { manualModel.trim() }
    val activeProvider: SavedProviderUi? get() = savedProviders.firstOrNull { it.id == activeProviderId }
    val generationModel: String get() = activeProvider?.model ?: formModel
    val activeProviderLabel: String get() = activeProvider?.name ?: discovery?.providerLabel.orEmpty()
    val transientReady: Boolean get() = discovery != null && formModel.isNotBlank()
    val ready: Boolean get() = activeProvider != null || transientReady
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val demo = DemoStoryRepository()
    private val repository = PersistentStoryRepository(application)
    private val projects = StoryProjectManager(application)
    private val chapterRuns = ChapterRunCoordinator(
        AppChapterRunStore(repository, projects),
        PersistentChapterRunCheckpointStore(application),
    )
    private val runtime = (application as LanghuanApplication).chapterRunRuntime
    private val backups = ProjectBackupManager(application)
    private val detector = ProviderAutoDetector()
    private val _state = MutableStateFlow(
        StudioUiState(snapshot = demo.snapshot, draft = demo.currentDraft)
    )
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runtime.state.collect { run ->
                val before = _state.value
                syncRuntimeState(run)
                if (!run.active && run.draft != null && run.matches(before.snapshot.novel.id, before.draft.chapterNumber)) {
                    refreshWorkspace()
                }
            }
        }
        viewModelScope.launch {
            repository.seedIfNeeded(demo)
            val preferredId = projects.activeStoryId() ?: demo.snapshot.novel.id
            val loaded = projects.loadStory(preferredId)
                ?: repository.loadStory(
                    demo.snapshot.novel.id,
                    PersistedStory(demo.snapshot, demo.currentDraft),
                )
            projects.setActiveStoryId(loaded.snapshot.novel.id)
            _state.update { it.copy(snapshot = loaded.snapshot, draft = loaded.draft) }
            restoreOrAttachRun(loaded.snapshot, loaded.draft)
            refreshWorkspace()
        }
        viewModelScope.launch {
            projects.observeStories().collect { stories ->
                _state.update { state -> state.copy(stories = stories.map { it.toUi() }) }
            }
        }
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                val ui = providers.map { it.toUi() }
                _state.update { state ->
                    val currentActive = state.provider.activeProviderId
                    val active = currentActive?.takeIf { id -> ui.any { it.id == id } }
                        ?: ui.firstOrNull { it.isDefault }?.id
                        ?: ui.firstOrNull()?.id
                    state.copy(provider = state.provider.copy(savedProviders = ui, activeProviderId = active))
                }
            }
        }
    }

    fun selectStory(id: String) {
        val current = _state.value
        if (current.snapshot.novel.id == id || busy(current)) return
        viewModelScope.launch {
            val loaded = projects.loadStory(id) ?: return@launch
            projects.setActiveStoryId(id)
            _state.update {
                it.copy(
                    snapshot = loaded.snapshot,
                    draft = loaded.draft,
                    versions = emptyList(),
                    chapters = emptyList(),
                    streamPreview = "",
                    runEvents = emptyList(),
                    isDraftDirty = false,
                    pendingPlan = null,
                    rewriteSuggestion = null,
                    agentReview = null,
                    result = null,
                    error = null,
                )
            }
            restoreOrAttachRun(loaded.snapshot, loaded.draft)
            refreshWorkspace()
        }
    }

    fun createStory(title: String, genre: String, premise: String, theme: String, targetWords: Int) {
        if (_state.value.isCreatingStory || runtime.state.value.active) return
        viewModelScope.launch {
            _state.update { it.copy(isCreatingStory = true, error = null) }
            runCatching {
                projects.createStory(NewStoryRequest(title, genre, premise, theme, targetWords))
            }.onSuccess { created ->
                _state.update {
                    it.copy(
                        snapshot = created.snapshot,
                        draft = created.draft,
                        versions = emptyList(),
                        chapters = emptyList(),
                        streamPreview = "",
                        isCreatingStory = false,
                        isDraftDirty = false,
                        agentReview = null,
                        result = null,
                        message = "已创建《${created.snapshot.novel.title}》",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isCreatingStory = false, error = error.message ?: "新建小说失败") }
            }
        }
    }

    fun selectChapter(number: Int) {
        val current = _state.value
        if (current.draft.chapterNumber == number || busy(current)) return
        viewModelScope.launch {
            runCatching { projects.selectChapter(current.snapshot.novel.id, number) }
                .onSuccess { persisted ->
                    if (persisted != null) {
                        _state.update {
                            it.copy(
                                snapshot = persisted.snapshot,
                                draft = persisted.draft,
                                streamPreview = "",
                                runEvents = emptyList(),
                                isDraftDirty = false,
                                result = null,
                                pendingPlan = null,
                                rewriteSuggestion = null,
                                agentReview = null,
                                error = null,
                            )
                        }
                        restoreOrAttachRun(persisted.snapshot, persisted.draft)
                        refreshWorkspace()
                    }
                }.onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "切换章节失败") }
                }
        }
    }

    fun createChapter(title: String, objective: String, conflict: String, turningPoint: String) {
        val current = _state.value
        if (busy(current)) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { projects.createChapter(current.snapshot, title, objective, conflict, turningPoint) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            isSaving = false,
                            isDraftDirty = false,
                            agentReview = null,
                            message = "已创建第${persisted.draft.chapterNumber}章",
                        )
                    }
                    refreshWorkspace()
                }.onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "创建章节失败") }
                }
        }
    }

    fun planNextChapter() {
        val current = _state.value
        if (current.isPlanning || busy(current)) return
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "请先在 AI 服务中配置并启用一个模型") }
                return@launch
            }
            _state.update { it.copy(isPlanning = true, error = null, pendingPlan = null) }
            runCatching { WorkspaceAiEngine(gateway).planNextChapter(current.snapshot, current.draft) }
                .onSuccess { plan -> _state.update { it.copy(isPlanning = false, pendingPlan = plan) } }
                .onFailure { error -> _state.update { it.copy(isPlanning = false, error = error.message ?: "AI 规划下一章失败") } }
        }
    }

    fun acceptPlannedChapter() {
        val current = _state.value
        val plan = current.pendingPlan ?: return
        if (busy(current)) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                projects.createChapter(
                    current.snapshot,
                    plan.title,
                    plan.objective,
                    plan.conflict,
                    plan.turningPoint,
                    plan.scenes,
                )
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        pendingPlan = null,
                        agentReview = null,
                        isSaving = false,
                        isDraftDirty = false,
                        message = "AI 章纲已加入第${persisted.draft.chapterNumber}章",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "保存 AI 章纲失败") }
            }
        }
    }

    fun dismissPlan() = _state.update { it.copy(pendingPlan = null) }

    fun refreshAutonomousPlan(horizon: Int = 6) {
        val current = _state.value
        if (current.isAutonomousPlanning || busy(current)) return
        if (current.isDraftDirty) {
            _state.update { it.copy(error = "当前正文还有未保存修改，请先保存版本再刷新自治计划") }
            return
        }
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "长篇自治规划需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isAutonomousPlanning = true, error = null) }
            runCatching {
                val planner = AutonomousStoryPlanner(gateway)
                val plan = planner.plan(current.snapshot, current.draft, horizon)
                projects.saveStructure(planner.apply(current.snapshot, plan), current.draft)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isAutonomousPlanning = false,
                        message = "未来 ${persisted.snapshot.longForm.autonomousPlan.chapters.size} 章自治计划已刷新",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isAutonomousPlanning = false, error = error.message ?: "自治规划失败") }
            }
        }
    }

    fun saveOutlineNode(
        existingId: String?,
        level: OutlineLevel,
        parentId: String?,
        title: String,
        objective: String,
        conflict: String,
        turningPoint: String,
        locked: Boolean,
    ) {
        val current = _state.value
        if (busy(current) || title.isBlank()) return
        val all = effectiveOutline(current.snapshot).toMutableList()
        val existing = existingId?.let { id -> all.firstOrNull { it.id == id } }
        val resolvedParent = when (level) {
            OutlineLevel.MASTER -> null
            OutlineLevel.VOLUME -> parentId ?: all.firstOrNull { it.level == OutlineLevel.MASTER }?.id
            OutlineLevel.CHAPTER -> parentId ?: current.snapshot.activeOutline.firstOrNull { it.level == OutlineLevel.VOLUME }?.id
        }
        val order = existing?.order ?: ((all.filter { it.level == level && it.parentId == resolvedParent }.maxOfOrNull { it.order } ?: 0) + 1)
        val node = OutlineNode(
            id = existing?.id ?: UUID.randomUUID().toString(),
            novelId = current.snapshot.novel.id,
            parentId = resolvedParent,
            level = level,
            order = order,
            title = title.trim(),
            objective = objective.trim(),
            conflict = conflict.trim(),
            turningPoint = turningPoint.trim(),
            mustInclude = existing?.mustInclude.orEmpty(),
            forbidden = existing?.forbidden.orEmpty(),
            locked = locked,
        )
        all.removeAll { it.id == node.id }
        all += node
        var draft = current.draft
        if (level == OutlineLevel.CHAPTER && existing?.order == current.draft.chapterNumber) {
            draft = draft.copy(title = node.title, objective = node.objective)
        }
        val snapshot = current.snapshot.copy(
            outline = all,
            activeOutline = chainForChapter(all, draft.chapterNumber).ifEmpty { current.snapshot.activeOutline },
        )
        saveStructure(snapshot, draft, "大纲已保存")
    }

    fun deleteOutlineNode(id: String) {
        val current = _state.value
        if (busy(current) || current.snapshot.activeOutline.any { it.id == id }) {
            if (current.snapshot.activeOutline.any { it.id == id }) _state.update { it.copy(error = "当前章节所在的大纲链不能直接删除") }
            return
        }
        val all = effectiveOutline(current.snapshot)
        val ids = mutableSetOf(id)
        var changed: Boolean
        do {
            val children = all.filter { it.parentId in ids }.map { it.id }
            changed = ids.addAll(children)
        } while (changed)
        saveStructure(current.snapshot.copy(outline = all.filterNot { it.id in ids }), current.draft, "大纲节点已删除")
    }

    fun saveBibleEntry(existingId: String?, category: BibleCategory, name: String, content: String, locked: Boolean) {
        if (name.isBlank() || content.isBlank() || _state.value.isSaving) return
        val current = _state.value
        val id = existingId ?: UUID.randomUUID().toString()
        val entry = BibleEntry(
            id = id,
            novelId = current.snapshot.novel.id,
            category = category,
            name = name.trim(),
            content = content.trim(),
            aliases = current.snapshot.bible.firstOrNull { it.id == existingId }?.aliases.orEmpty(),
            locked = locked,
        )
        saveStructure(current.snapshot.copy(bible = current.snapshot.bible.filterNot { it.id == id } + entry), current.draft, "小说圣经已更新")
    }

    fun saveStyleTemplate(name: String, content: String) {
        val current = _state.value
        if (content.isBlank() || busy(current)) return
        val existing = current.snapshot.bible.firstOrNull { it.category == BibleCategory.STYLE && it.name == name.trim().ifBlank { "主文风" } }
            ?: current.snapshot.bible.firstOrNull { it.category == BibleCategory.STYLE }
        saveBibleEntry(existing?.id, BibleCategory.STYLE, name.trim().ifBlank { "主文风" }, content, true)
    }

    fun deleteBibleEntry(id: String) {
        val current = _state.value
        if (!busy(current)) saveStructure(current.snapshot.copy(bible = current.snapshot.bible.filterNot { it.id == id }), current.draft, "设定已删除")
    }

    fun saveCharacter(
        existingId: String?,
        name: String,
        personality: String,
        location: String,
        physicalState: String,
        emotionalState: String,
        goal: String,
        relationships: String,
    ) {
        val current = _state.value
        if (busy(current) || name.isBlank()) return
        val old = current.snapshot.characters.firstOrNull { it.id == existingId }
        val id = old?.id ?: UUID.randomUUID().toString()
        val item = CharacterState(
            id = id,
            novelId = current.snapshot.novel.id,
            name = name.trim(),
            personality = splitList(personality),
            location = location.trim(),
            physicalState = physicalState.trim().ifBlank { "正常" },
            emotionalState = emotionalState.trim().ifBlank { "平静" },
            goal = goal.trim(),
            knownSecrets = old?.knownSecrets.orEmpty(),
            possessions = old?.possessions.orEmpty(),
            relationshipNotes = parseRelations(relationships),
            lastUpdatedChapter = current.draft.chapterNumber,
        )
        saveStructure(current.snapshot.copy(characters = current.snapshot.characters.filterNot { it.id == id } + item), current.draft, "人物状态已保存")
    }

    fun deleteCharacter(id: String) {
        val current = _state.value
        if (!busy(current)) saveStructure(current.snapshot.copy(characters = current.snapshot.characters.filterNot { it.id == id }), current.draft, "人物已删除")
    }

    fun saveTimeline(
        existingId: String?,
        chapter: Int,
        storyTime: String,
        location: String,
        participants: String,
        summary: String,
        consequences: String,
    ) {
        val current = _state.value
        if (busy(current) || summary.isBlank()) return
        val id = existingId ?: UUID.randomUUID().toString()
        val item = TimelineEvent(
            id = id,
            novelId = current.snapshot.novel.id,
            chapter = chapter.coerceAtLeast(1),
            storyTime = storyTime.trim(),
            location = location.trim(),
            participants = splitList(participants),
            summary = summary.trim(),
            consequences = splitList(consequences),
        )
        saveStructure(current.snapshot.copy(recentTimeline = (current.snapshot.recentTimeline.filterNot { it.id == id } + item).sortedBy { it.chapter }.takeLast(80)), current.draft, "时间线已保存")
    }

    fun deleteTimeline(id: String) {
        val current = _state.value
        if (!busy(current)) saveStructure(current.snapshot.copy(recentTimeline = current.snapshot.recentTimeline.filterNot { it.id == id }), current.draft, "时间线事件已删除")
    }

    fun saveForeshadowing(
        existingId: String?,
        title: String,
        plantedChapter: Int,
        detail: String,
        payoff: String,
        chapterStart: Int,
        chapterEnd: Int,
        status: ForeshadowStatus,
    ) {
        val current = _state.value
        if (busy(current) || title.isBlank() || detail.isBlank()) return
        val id = existingId ?: UUID.randomUUID().toString()
        val item = Foreshadowing(
            id = id,
            novelId = current.snapshot.novel.id,
            title = title.trim(),
            plantedChapter = plantedChapter.coerceAtLeast(1),
            detail = detail.trim(),
            expectedPayoff = payoff.trim(),
            expectedChapterStart = chapterStart.coerceAtLeast(1),
            expectedChapterEnd = maxOf(chapterStart, chapterEnd).coerceAtLeast(1),
            status = status,
        )
        saveStructure(current.snapshot.copy(relevantForeshadowing = current.snapshot.relevantForeshadowing.filterNot { it.id == id } + item), current.draft, "伏笔已保存")
    }

    fun deleteForeshadowing(id: String) {
        val current = _state.value
        if (!busy(current)) saveStructure(current.snapshot.copy(relevantForeshadowing = current.snapshot.relevantForeshadowing.filterNot { it.id == id }), current.draft, "伏笔已删除")
    }

    fun saveScene(
        existingOrder: Int?,
        viewpoint: String,
        location: String,
        purpose: String,
        conflict: String,
        outcome: String,
    ) {
        val current = _state.value
        if (busy(current)) return
        val order = existingOrder ?: ((current.draft.scenePlan.maxOfOrNull { it.order } ?: 0) + 1)
        val scene = ScenePlan(order, viewpoint.trim(), location.trim(), purpose.trim(), conflict.trim(), outcome.trim())
        val draft = current.draft.copy(scenePlan = (current.draft.scenePlan.filterNot { it.order == order } + scene).sortedBy { it.order })
        saveStructure(current.snapshot, draft, "场景计划已保存")
    }

    fun deleteScene(order: Int) {
        val current = _state.value
        if (!busy(current) && current.draft.scenePlan.size > 1) {
            saveStructure(current.snapshot, current.draft.copy(scenePlan = current.draft.scenePlan.filterNot { it.order == order }), "场景已删除")
        }
    }

    fun setDraftContent(value: String) {
        if (runtime.state.value.active) return
        _state.update { it.copy(draft = it.draft.copy(content = value), isDraftDirty = true, error = null) }
    }

    fun saveDraftVersion() {
        val current = _state.value
        if (current.isSaving || !current.isDraftDirty || runtime.state.value.active) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { repository.saveDraft(current.snapshot, current.draft) }
                .onSuccess { persisted ->
                    _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, isSaving = false, isDraftDirty = false, message = "草稿版本已保存") }
                    refreshWorkspace()
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "保存草稿版本失败") } }
        }
    }

    fun requestRewrite(start: Int, end: Int, instruction: String) {
        val current = _state.value
        if (current.isRewriting || busy(current)) return
        val safeStart = start.coerceIn(0, current.draft.content.length)
        val safeEnd = end.coerceIn(safeStart, current.draft.content.length)
        if (safeEnd <= safeStart) {
            _state.update { it.copy(error = "请先在正文编辑器中选中要重写的文字") }
            return
        }
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "局部重写需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isRewriting = true, error = null, rewriteSuggestion = null) }
            val selected = current.draft.content.substring(safeStart, safeEnd)
            runCatching { WorkspaceAiEngine(gateway).rewriteSelection(current.snapshot, current.draft, selected, instruction) }
                .onSuccess { replacement ->
                    _state.update { it.copy(isRewriting = false, rewriteSuggestion = RewriteSuggestion(safeStart, safeEnd, replacement, instruction)) }
                }.onFailure { error ->
                    _state.update { it.copy(isRewriting = false, error = error.message ?: "局部重写失败") }
                }
        }
    }

    fun applyRewrite() {
        val current = _state.value
        val suggestion = current.rewriteSuggestion ?: return
        val content = current.draft.content
        if (suggestion.start !in 0..content.length || suggestion.end !in suggestion.start..content.length) {
            _state.update { it.copy(rewriteSuggestion = null, error = "正文已变化，请重新选择片段") }
            return
        }
        val replaced = content.replaceRange(suggestion.start, suggestion.end, suggestion.replacement)
        _state.update { it.copy(draft = it.draft.copy(content = replaced), rewriteSuggestion = null, isDraftDirty = true, message = "已应用 AI 局部重写，记得保存版本") }
    }

    fun dismissRewrite() = _state.update { it.copy(rewriteSuggestion = null) }

    fun runChapterReview() {
        val current = _state.value
        if (busy(current)) return
        if (current.draft.content.isBlank()) {
            _state.update { it.copy(error = "当前章节还没有正文，无法复盘") }
            return
        }
        _state.update { it.copy(isAgentReviewing = true, error = null, agentReview = null) }
        runtime.review(current.snapshot, current.draft)
    }

    fun runFullBookAudit() {
        val current = _state.value
        if (busy(current)) return
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "全书主编深度巡检需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isAuditing = true, error = null, agentReview = null) }
            runCatching {
                val drafts = projects.chapterDrafts(current.snapshot.novel.id)
                val editor = FullBookEditorEngine()
                val local = editor.localAudit(current.snapshot, drafts)
                val review = NovelAgentEngine(gateway).auditStory(current.snapshot, drafts)
                val report = editor.mergeAgentReview(local, review)
                val persisted = projects.saveStructure(editor.apply(current.snapshot, report), current.draft)
                Triple(persisted, review, report)
            }.onSuccess { (persisted, review, report) ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isAuditing = false,
                        agentReview = review,
                        message = "全书主编巡检完成：${report.score}分 · ${report.level}",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isAuditing = false, error = error.message ?: "全书主编巡检失败") }
            }
        }
    }

    fun confirmCandidateFact(candidateId: String) {
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
    }

    fun useAgentNextOption(index: Int) {
        val current = _state.value
        val option = current.agentReview?.nextOptions?.getOrNull(index) ?: return
        val viewpoint = current.snapshot.characters.firstOrNull()?.name ?: "主角"
        val nextNumber = current.draft.chapterNumber + 1
        _state.update {
            it.copy(
                pendingPlan = ChapterPlanSuggestion(
                    title = option.title,
                    objective = option.objective,
                    conflict = option.conflict,
                    turningPoint = option.turningPoint,
                    scenes = listOf(
                        ScenePlan(1, viewpoint, "承接上一章的场景", "承接上一章结果并进入本章目标", option.conflict, "冲突升级并暴露新的信息"),
                        ScenePlan(2, viewpoint, "第${nextNumber}章核心场景", option.objective, option.conflict, option.turningPoint),
                    ),
                ),
            )
        }
    }

    fun dismissAgentReview() = _state.update { it.copy(agentReview = null) }

    fun restoreVersion(versionId: String) {
        val current = _state.value
        val version = current.versions.firstOrNull { it.id == versionId } ?: return
        if (current.isRestoringVersion || busy(current)) return
        viewModelScope.launch {
            _state.update { it.copy(isRestoringVersion = true, error = null) }
            runCatching { repository.restoreVersion(current.snapshot, current.draft, version.toStored()) }
                .onSuccess { persisted ->
                    _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, isRestoringVersion = false, isDraftDirty = false, streamPreview = "", agentReview = null, message = "已恢复为 v${version.version} 的内容，并保存为新版本") }
                    refreshWorkspace()
                }.onFailure { error -> _state.update { it.copy(isRestoringVersion = false, error = error.message ?: "恢复版本失败") } }
        }
    }

    fun importDocument(uri: Uri) {
        if (_state.value.isImporting || runtime.state.value.active) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "导入稿件.txt"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取选择的文件")
                if (StoryExchange.isProjectBackup(name)) {
                    backups.restore(StoryExchange.importProject(bytes))
                } else {
                    val manuscript = StoryExchange.import(name, bytes)
                    require(manuscript.chapters.isNotEmpty()) { "没有识别到可导入的正文" }
                    projects.createImportedStory(manuscript)
                }
            }.onSuccess { created ->
                _state.update { it.copy(snapshot = created.snapshot, draft = created.draft, isImporting = false, isDraftDirty = false, agentReview = null, message = "已导入《${created.snapshot.novel.title}》") }
                refreshWorkspace()
            }.onFailure { error -> _state.update { it.copy(isImporting = false, error = error.message ?: "导入失败") } }
        }
    }

    fun exportDocument(uri: Uri, format: ExportFormat) {
        if (_state.value.isExporting) return
        val novelId = _state.value.snapshot.novel.id
        viewModelScope.launch {
            _state.update { it.copy(isExporting = true, error = null) }
            runCatching {
                val artifact = projects.exportStory(novelId, format)
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(artifact.bytes) }
                    ?: error("无法写入目标文件")
            }.onSuccess {
                _state.update { it.copy(isExporting = false, message = if (format == ExportFormat.PROJECT) "项目备份完成（不含 API Key）" else "${format.name} 导出完成") }
            }.onFailure { error -> _state.update { it.copy(isExporting = false, error = error.message ?: "导出失败") } }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    fun setProviderName(value: String) = updateProvider { it.copy(providerName = value, error = null) }
    fun setBaseUrl(value: String) = updateProvider { it.copy(baseUrl = value, discovery = null, selectedModel = "", manualModel = "", error = null) }
    fun setApiKey(value: String) = updateProvider { it.copy(apiKey = value, error = null) }
    fun setManualModel(value: String) = updateProvider { it.copy(manualModel = value, selectedModel = "", error = null) }
    fun selectModel(model: DiscoveredModel) = updateProvider { it.copy(selectedModel = model.id, manualModel = "", error = null) }

    fun newProvider() = updateProvider { current ->
        current.copy(editingProviderId = null, providerName = "", baseUrl = "", apiKey = "", hasStoredKey = false, discovery = null, selectedModel = "", manualModel = "", error = null)
    }

    fun editProvider(id: String) {
        val provider = _state.value.provider.savedProviders.firstOrNull { it.id == id } ?: return
        updateProvider {
            it.copy(
                editingProviderId = provider.id,
                providerName = provider.name,
                baseUrl = provider.baseUrl,
                apiKey = "",
                hasStoredKey = provider.hasApiKey,
                discovery = ProviderDiscovery(provider.protocol, provider.name, provider.baseUrl, listOf(DiscoveredModel(provider.model)), provider.supportsJsonMode, "已载入保存配置，可重新探测或直接修改后保存"),
                selectedModel = provider.model,
                manualModel = "",
                error = null,
            )
        }
    }

    fun activateProvider(id: String) {
        updateProvider { it.copy(activeProviderId = id) }
        viewModelScope.launch { repository.setDefaultProvider(id) }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.deleteProvider(id)
            updateProvider { current ->
                if (current.editingProviderId == id) current.copy(activeProviderId = current.activeProviderId.takeUnless { it == id }, editingProviderId = null, providerName = "", baseUrl = "", apiKey = "", hasStoredKey = false, discovery = null, selectedModel = "", manualModel = "")
                else current.copy(activeProviderId = current.activeProviderId.takeUnless { it == id })
            }
        }
    }

    fun detectProvider() {
        val provider = _state.value.provider
        if (provider.isDetecting) return
        viewModelScope.launch {
            updateProvider { it.copy(isDetecting = true, error = null, discovery = null) }
            val effectiveKey = provider.apiKey.ifBlank { provider.editingProviderId?.let { repository.apiKey(it).orEmpty() }.orEmpty() }
            runCatching { detector.detect(provider.baseUrl, effectiveKey) }
                .onSuccess { discovery ->
                    updateProvider { it.copy(isDetecting = false, providerName = it.providerName.ifBlank { discovery.providerLabel }, baseUrl = discovery.normalizedBaseUrl, discovery = discovery, selectedModel = discovery.models.firstOrNull()?.id.orEmpty(), error = null) }
                }
                .onFailure { error -> updateProvider { it.copy(isDetecting = false, error = error.message ?: "无法识别该接口") } }
        }
    }

    fun saveProvider() {
        val provider = _state.value.provider
        val discovery = provider.discovery ?: return
        if (provider.formModel.isBlank() || provider.baseUrl.isBlank()) return
        viewModelScope.launch {
            updateProvider { it.copy(isSaving = true, error = null) }
            runCatching {
                repository.saveProvider(
                    ProviderSaveRequest(provider.editingProviderId, provider.providerName.ifBlank { discovery.providerLabel }, discovery.normalizedBaseUrl.ifBlank { provider.baseUrl }, discovery.protocol, provider.formModel, supportsJsonMode = discovery.supportsJsonMode, apiKey = provider.apiKey, makeDefault = true)
                )
            }.onSuccess { saved ->
                updateProvider { it.copy(isSaving = false, activeProviderId = saved.id, editingProviderId = saved.id, providerName = saved.name, apiKey = "", hasStoredKey = saved.hasApiKey, error = null) }
            }.onFailure { error -> updateProvider { it.copy(isSaving = false, error = error.message ?: "保存 AI 配置失败") } }
        }
    }

    fun generateChapter() {
        val current = _state.value
        if (busy(current)) return
        _state.update {
            it.copy(
                isGenerating = true,
                streamPreview = "",
                error = null,
                runEvents = emptyList(),
                result = null,
                agentReview = null,
            )
        }
        runtime.generate(
            snapshot = current.snapshot,
            draft = current.draft,
            targetWords = 2_500,
            allowDemoFallback = true,
        )
    }

    fun commitResult() {
        val current = _state.value
        val result = current.result ?: return
        if (!result.canCommit || busy(current)) return
        _state.update { it.copy(isSaving = true, error = null) }
        runtime.commit(
            snapshot = current.snapshot,
            draft = current.draft,
            result = result,
            allowNoAi = true,
        )
    }

    fun dismissResult() {
        val current = _state.value
        runtime.abandon(current.snapshot, current.draft)
        _state.update { it.copy(result = null, streamPreview = "", runEvents = emptyList()) }
    }

    private fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft, message: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { projects.saveStructure(snapshot, draft) }
                .onSuccess { persisted ->
                    _state.update { it.copy(snapshot = persisted.snapshot, draft = persisted.draft, isSaving = false, message = message) }
                    refreshWorkspace()
                }.onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "保存失败") } }
        }
    }

    private fun syncRuntimeState(run: ChapterRunRuntimeState) {
        val current = _state.value
        if (!run.matches(current.snapshot.novel.id, current.draft.chapterNumber)) return
        _state.update { state ->
            state.copy(
                snapshot = run.snapshot ?: state.snapshot,
                draft = run.draft ?: state.draft,
                streamPreview = run.preview,
                runEvents = run.events,
                result = run.result,
                agentReview = run.review,
                isGenerating = run.active && run.taskKind == ChapterRuntimeTaskKind.GENERATE,
                isSaving = run.active && run.taskKind == ChapterRuntimeTaskKind.COMMIT,
                isAgentReviewing = run.active && run.taskKind == ChapterRuntimeTaskKind.REVIEW,
                isAutonomousPlanning = false,
                isDraftDirty = if (!run.active && run.taskKind == ChapterRuntimeTaskKind.COMMIT && run.draft != null) false else state.isDraftDirty,
                message = run.message ?: state.message,
                error = run.error ?: state.error,
            )
        }
    }

    private fun restoreOrAttachRun(snapshot: StorySnapshot, draft: ChapterDraft) {
        val live = runtime.state.value
        val hasRuntimeState = live.matches(snapshot.novel.id, draft.chapterNumber) &&
            (live.active || live.result != null || live.review != null || live.message != null || live.error != null)
        if (hasRuntimeState) syncRuntimeState(live) else restoreDurableRun(snapshot, draft)
    }

    private fun restoreDurableRun(snapshot: StorySnapshot, draft: ChapterDraft) {
        val recovery = chapterRuns.recover(snapshot, draft) ?: return
        _state.update {
            it.copy(
                streamPreview = recovery.preview,
                result = recovery.result,
                runEvents = recovery.events,
                message = recovery.message,
            )
        }
    }

    private suspend fun configuredGateway(): AiGateway? {
        val provider = _state.value.provider
        val savedConfig = provider.activeProviderId?.let { repository.providerConfig(it) }
        val transientConfig = provider.discovery?.takeIf { provider.formModel.isNotBlank() }?.let { discovery ->
            AiProviderConfig(discovery.normalizedBaseUrl, provider.apiKey, provider.formModel, discovery.protocol, supportsJsonMode = discovery.supportsJsonMode)
        }
        return (savedConfig ?: transientConfig)?.let(::UniversalAiGateway)
    }

    private suspend fun refreshWorkspace() {
        val current = _state.value
        val chapterDrafts = projects.chapterDrafts(current.snapshot.novel.id)
        val versions = repository.chapterVersions(current.snapshot.novel.id, current.draft.chapterNumber).map { it.toUi() }
        _state.update { state ->
            state.copy(
                chapters = chapterDrafts.map { draft -> ChapterShelfUi(draft.chapterNumber, draft.title, draft.objective, draft.content.length, draft.chapterNumber == state.draft.chapterNumber) },
                versions = versions,
            )
        }
    }

    private fun busy(state: StudioUiState): Boolean =
        runtime.state.value.active || state.isGenerating || state.isSaving || state.isPlanning || state.isRewriting || state.isAgentReviewing || state.isAuditing || state.isAutonomousPlanning || state.isImporting || state.isExporting || state.isRestoringVersion

    private fun effectiveOutline(snapshot: StorySnapshot): List<OutlineNode> = (if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline).distinctBy { it.id }

    private fun chainForChapter(nodes: List<OutlineNode>, chapterNumber: Int): List<OutlineNode> {
        val chapter = nodes.firstOrNull { it.level == OutlineLevel.CHAPTER && it.order == chapterNumber } ?: return emptyList()
        val volume = nodes.firstOrNull { it.id == chapter.parentId }
        val master = volume?.parentId?.let { id -> nodes.firstOrNull { it.id == id } } ?: nodes.firstOrNull { it.level == OutlineLevel.MASTER }
        return listOfNotNull(master, volume, chapter)
    }

    private fun splitList(value: String): List<String> = value.split('、', ',', '，', ';', '；', '\n').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun parseRelations(value: String): Map<String, String> = value.lineSequence().mapNotNull { line ->
        val normalized = line.trim()
        val index = listOf(normalized.indexOf('='), normalized.indexOf('：'), normalized.indexOf(':')).filter { it > 0 }.minOrNull() ?: return@mapNotNull null
        val name = normalized.substring(0, index).trim()
        val note = normalized.substring(index + 1).trim()
        if (name.isBlank() || note.isBlank()) null else name to note
    }.toMap()

    private fun updateProvider(block: (ProviderUiState) -> ProviderUiState) {
        _state.update { it.copy(provider = block(it.provider)) }
    }

    private fun StoredAiProvider.toUi() = SavedProviderUi(id, name, baseUrl, protocol, model, supportsJsonMode, isDefault, hasApiKey)
    private fun StoredChapterVersion.toUi() = ChapterVersionUi(id, chapterNumber, version, title, content, summary, createdAt)
    private fun ChapterVersionUi.toStored() = StoredChapterVersion(id, chapterNumber, version, title, content, summary, createdAt)
    private fun StoryShelfItem.toUi() = StoryShelfUi(id, title, genre, currentWords, targetWords, currentChapter)
}
