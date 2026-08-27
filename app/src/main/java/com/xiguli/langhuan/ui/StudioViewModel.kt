package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.DemoStoryRepository
import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.ProviderSaveRequest
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.data.StoredChapterVersion
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.ApiProtocol
import com.xiguli.langhuan.engine.DemoAiGateway
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.GenerationPipeline
import com.xiguli.langhuan.engine.ProviderAutoDetector
import com.xiguli.langhuan.engine.ProviderDiscovery
import com.xiguli.langhuan.engine.UniversalAiGateway
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

data class StudioUiState(
    val snapshot: StorySnapshot,
    val draft: ChapterDraft,
    val provider: ProviderUiState = ProviderUiState(),
    val versions: List<ChapterVersionUi> = emptyList(),
    val streamPreview: String = "",
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val isRestoringVersion: Boolean = false,
    val isDraftDirty: Boolean = false,
    val result: GenerationResult? = null,
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
    private val detector = ProviderAutoDetector()
    private val _state = MutableStateFlow(
        StudioUiState(snapshot = demo.snapshot, draft = demo.currentDraft)
    )
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfNeeded(demo)
            val loaded = repository.loadStory(
                demo.snapshot.novel.id,
                PersistedStory(demo.snapshot, demo.currentDraft),
            )
            _state.update { it.copy(snapshot = loaded.snapshot, draft = loaded.draft) }
            refreshVersions()
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

    fun setProviderName(value: String) = updateProvider { it.copy(providerName = value, error = null) }

    fun setBaseUrl(value: String) = updateProvider {
        it.copy(baseUrl = value, discovery = null, selectedModel = "", manualModel = "", error = null)
    }

    fun setApiKey(value: String) = updateProvider { it.copy(apiKey = value, error = null) }

    fun setManualModel(value: String) = updateProvider {
        it.copy(manualModel = value, selectedModel = "", error = null)
    }

    fun selectModel(model: DiscoveredModel) = updateProvider {
        it.copy(selectedModel = model.id, manualModel = "", error = null)
    }

    fun setDraftContent(value: String) {
        _state.update { it.copy(draft = it.draft.copy(content = value), isDraftDirty = true, error = null) }
    }

    fun saveDraftVersion() {
        val current = _state.value
        if (current.isSaving || !current.isDraftDirty) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { repository.saveDraft(current.snapshot, current.draft) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            isSaving = false,
                            isDraftDirty = false,
                        )
                    }
                    refreshVersions()
                }
                .onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "保存草稿版本失败") }
                }
        }
    }

    fun restoreVersion(versionId: String) {
        val current = _state.value
        val version = current.versions.firstOrNull { it.id == versionId } ?: return
        if (current.isRestoringVersion || current.isSaving || current.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(isRestoringVersion = true, error = null) }
            runCatching {
                repository.restoreVersion(current.snapshot, current.draft, version.toStored())
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isRestoringVersion = false,
                        isDraftDirty = false,
                        streamPreview = "",
                    )
                }
                refreshVersions()
            }.onFailure { error ->
                _state.update { it.copy(isRestoringVersion = false, error = error.message ?: "恢复版本失败") }
            }
        }
    }

    fun newProvider() = updateProvider { current ->
        current.copy(
            editingProviderId = null,
            providerName = "",
            baseUrl = "",
            apiKey = "",
            hasStoredKey = false,
            discovery = null,
            selectedModel = "",
            manualModel = "",
            error = null,
        )
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
                discovery = ProviderDiscovery(
                    protocol = provider.protocol,
                    providerLabel = provider.name,
                    normalizedBaseUrl = provider.baseUrl,
                    models = listOf(DiscoveredModel(provider.model)),
                    supportsJsonMode = provider.supportsJsonMode,
                    message = "已载入保存配置，可重新探测或直接修改后保存",
                ),
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
                if (current.editingProviderId == id) {
                    current.copy(
                        activeProviderId = current.activeProviderId.takeUnless { it == id },
                        editingProviderId = null,
                        providerName = "",
                        baseUrl = "",
                        apiKey = "",
                        hasStoredKey = false,
                        discovery = null,
                        selectedModel = "",
                        manualModel = "",
                    )
                } else current.copy(activeProviderId = current.activeProviderId.takeUnless { it == id })
            }
        }
    }

    fun detectProvider() {
        val provider = _state.value.provider
        if (provider.isDetecting) return
        viewModelScope.launch {
            updateProvider { it.copy(isDetecting = true, error = null, discovery = null) }
            val effectiveKey = provider.apiKey.ifBlank {
                provider.editingProviderId?.let { repository.apiKey(it).orEmpty() }.orEmpty()
            }
            runCatching { detector.detect(provider.baseUrl, effectiveKey) }
                .onSuccess { discovery ->
                    updateProvider {
                        it.copy(
                            isDetecting = false,
                            providerName = it.providerName.ifBlank { discovery.providerLabel },
                            baseUrl = discovery.normalizedBaseUrl,
                            discovery = discovery,
                            selectedModel = discovery.models.firstOrNull()?.id.orEmpty(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    updateProvider {
                        it.copy(isDetecting = false, error = error.message ?: "无法识别该接口")
                    }
                }
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
                    ProviderSaveRequest(
                        id = provider.editingProviderId,
                        name = provider.providerName.ifBlank { discovery.providerLabel },
                        baseUrl = discovery.normalizedBaseUrl.ifBlank { provider.baseUrl },
                        protocol = discovery.protocol,
                        model = provider.formModel,
                        supportsJsonMode = discovery.supportsJsonMode,
                        apiKey = provider.apiKey,
                        makeDefault = true,
                    )
                )
            }.onSuccess { saved ->
                updateProvider {
                    it.copy(
                        isSaving = false,
                        activeProviderId = saved.id,
                        editingProviderId = saved.id,
                        providerName = saved.name,
                        apiKey = "",
                        hasStoredKey = saved.hasApiKey,
                        error = null,
                    )
                }
            }.onFailure { error ->
                updateProvider { it.copy(isSaving = false, error = error.message ?: "保存 AI 配置失败") }
            }
        }
    }

    fun generateChapter() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, streamPreview = "正在分析章纲与长期记忆…", error = null) }
            val current = _state.value
            val provider = current.provider
            val ragQuery = buildString {
                append(current.draft.title).append(' ')
                append(current.draft.objective).append(' ')
                current.draft.scenePlan.forEach {
                    append(it.viewpoint).append(' ').append(it.location).append(' ')
                    append(it.purpose).append(' ').append(it.conflict).append(' ')
                }
                current.snapshot.activeOutline.forEach { append(it.objective).append(' ').append(it.turningPoint).append(' ') }
                current.snapshot.characters.forEach { append(it.name).append(' ').append(it.goal).append(' ') }
            }
            val memories = repository.retrieveRelevantMemories(
                novelId = current.snapshot.novel.id,
                query = ragQuery,
                currentChapter = current.draft.chapterNumber,
                limit = 8,
            )
            val snapshotForPrompt = current.snapshot.copy(
                recentSummaries = (current.snapshot.recentSummaries + memories).distinct().takeLast(18)
            )
            val savedConfig = provider.activeProviderId?.let { repository.providerConfig(it) }
            val transientConfig = provider.discovery?.takeIf { provider.formModel.isNotBlank() }?.let { discovery ->
                AiProviderConfig(
                    baseUrl = discovery.normalizedBaseUrl,
                    apiKey = provider.apiKey,
                    model = provider.formModel,
                    protocol = discovery.protocol,
                    supportsJsonMode = discovery.supportsJsonMode,
                )
            }
            val gateway = (savedConfig ?: transientConfig)?.let(::UniversalAiGateway) ?: DemoAiGateway()
            runCatching {
                GenerationPipeline(gateway).generate(
                    GenerationRequest(
                        snapshot = snapshotForPrompt,
                        chapter = current.draft,
                        targetWords = 2_500,
                    )
                ) { preview ->
                    _state.update { state -> state.copy(streamPreview = preview) }
                }
            }.onSuccess { result ->
                _state.update { it.copy(isGenerating = false, streamPreview = result.chapter.content, result = result) }
            }.onFailure { error ->
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
                repository.commitGenerated(current.snapshot, current.draft, result.chapter)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isSaving = false,
                        isDraftDirty = false,
                        streamPreview = "",
                        result = null,
                    )
                }
                refreshVersions()
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "保存章节失败") }
            }
        }
    }

    fun dismissResult() = _state.update { it.copy(result = null, streamPreview = "") }

    private suspend fun refreshVersions() {
        val current = _state.value
        val versions = repository.chapterVersions(current.snapshot.novel.id, current.draft.chapterNumber)
            .map { it.toUi() }
        _state.update { it.copy(versions = versions) }
    }

    private fun updateProvider(block: (ProviderUiState) -> ProviderUiState) {
        _state.update { it.copy(provider = block(it.provider)) }
    }

    private fun StoredAiProvider.toUi() = SavedProviderUi(
        id = id,
        name = name,
        baseUrl = baseUrl,
        protocol = protocol,
        model = model,
        supportsJsonMode = supportsJsonMode,
        isDefault = isDefault,
        hasApiKey = hasApiKey,
    )

    private fun StoredChapterVersion.toUi() = ChapterVersionUi(
        id = id,
        chapterNumber = chapterNumber,
        version = version,
        title = title,
        content = content,
        summary = summary,
        createdAt = createdAt,
    )

    private fun ChapterVersionUi.toStored() = StoredChapterVersion(
        id = id,
        chapterNumber = chapterNumber,
        version = version,
        title = title,
        content = content,
        summary = summary,
        createdAt = createdAt,
    )
}
