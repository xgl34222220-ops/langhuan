package com.xiguli.langhuan.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal data class OnlineDetailV36(val book: OnlineBookV36, val chapters: List<OnlineChapterV36>, val shelfStoryId: String? = null, val catalogueProof: SourceCatalogueProofV50? = null)

internal data class OnlineDownloadV36(val done: Int, val total: Int, val failed: Int, val saving: Boolean = false)

internal data class OnlineSourceFailureV57(val sourceId: String, val sourceName: String, val reason: String)

/** Exact transient input identity for rules which passed live validation in this process. */
internal data class AiValidationRetryInputV77(
    val siteUrl: String,
    val keyword: String,
    val useBrowser: Boolean,
)

/** Exact persisted service revision accepted by one in-memory AI attempt. */
internal data class AiProviderAttemptRevisionV81(
    val id: String,
    val revision: Long,
)

internal fun aiProviderAttemptStillCurrentV81(
    attempt: AiProviderAttemptRevisionV81,
    providers: List<com.xiguli.langhuan.data.StoredAiProvider>,
): Boolean = providers.any { provider ->
    provider.id == attempt.id && provider.revision == attempt.revision
}

/**
 * A cached Flow value may predate the database snapshot that resolved this attempt. Only an
 * observation delivered while resolution was in flight can invalidate that newly resolved
 * identity here; later Flow emissions are still handled continuously by the collector.
 */
internal fun aiProviderObservationInvalidatesResolvedAttemptV82(
    attempt: AiProviderAttemptRevisionV81,
    providers: List<com.xiguli.langhuan.data.StoredAiProvider>,
    observationVersionAtResolutionStart: Long,
    currentObservationVersion: Long,
): Boolean = currentObservationVersion != observationVersionAtResolutionStart &&
    !aiProviderAttemptStillCurrentV81(attempt, providers)

internal fun aiValidationRetryInputV77(
    siteUrl: String,
    keyword: String,
    useBrowser: Boolean,
): AiValidationRetryInputV77 = AiValidationRetryInputV77(
    siteUrl = siteUrl.trim(),
    keyword = keyword.trim(),
    useBrowser = useBrowser,
)

internal data class OnlineBooksStateV36(
    val sources: List<BookSourceV36> = emptyList(),
    val sourceStorageError: String? = null,
    val sourceEditId: String? = null,
    val sourceEditDraft: String = "",
    val sourceEditSaving: Boolean = false,
    val sourceEditError: String? = null,
    /** V94: an import (text/file/URL) is running; the manage page shows progress. */
    val sourceImporting: Boolean = false,
    /** V94: result of the last import, shown as a dialog until dismissed. */
    val sourceImportReport: SourceImportReportV94? = null,
    /** V94: 「测试搜索」 on the source editor draft. */
    val sourceTest: SourceTestStateV94? = null,
    val query: String = "",
    val discoveryLabel: String? = null,
    val discoverySection: SourceDiscoveryV41? = null,
    val discoveryPage: Int = 0,
    val discoveryNextUrl: String? = null,
    val discoveryHasMore: Boolean = false,
    val discoveryVisitedUrls: Set<String> = emptySet(),
    val discoveryPageError: String? = null,
    val searching: Boolean = false,
    val searchedSources: Int = 0,
    val failedSources: Int = 0,
    val searchFailures: List<OnlineSourceFailureV57> = emptyList(),
    val pendingSearchSourceIds: Set<String> = emptySet(),
    val searchStopped: Boolean = false,
    val results: List<OnlineBookV36> = emptyList(),
    val detailLoading: Boolean = false,
    val detail: OnlineDetailV36? = null,
    val detailError: String? = null,
    val detailStopped: Boolean = false,
    val download: OnlineDownloadV36? = null,
    val addingToShelf: Boolean = false,
    val createdStoryId: String? = null,
    val message: String? = null,
    val error: String? = null,
    /** Current default/provider observation for the next attempt. */
    val aiProviderLabel: String? = null,
    /** Exact service identity resolved with the active attempt's configuration; never persisted. */
    val aiAttemptProviderLabel: String? = null,
    val aiSteps: List<AiSourceStepV37> = emptyList(),
    val aiRunning: Boolean = false,
    val aiReport: AiSourceReportV37? = null,
    val aiError: String? = null,
    val aiStopped: Boolean = false,
    /** Mode of the last accepted attempt, retained so a terminal failure can retry consistently. */
    val aiLastUseBrowser: Boolean? = null,
    /** Same-input retry can reuse rules which already passed live extraction. */
    val aiCanResumeValidatedRules: Boolean = false,
    /** Input identity backing [aiCanResumeValidatedRules]; never persisted across processes. */
    val aiValidationRetryInput: AiValidationRetryInputV77? = null,
    /** Last source confirmed written by the AI flow; cleared by the next attempt or explicit stop. */
    val aiSavedSourceId: String? = null,
    val aiSavedSourceName: String? = null,
)

/** Import summary: what was added, what was kept as-is, what was skipped and why. */
internal data class SourceImportReportV94(
    val added: List<String>,
    val skipped: List<String>,
    val warnings: List<String>,
    val error: String? = null,
)

internal data class SourceTestStateV94(
    val keyword: String,
    val running: Boolean = true,
    val books: List<OnlineBookV36> = emptyList(),
    val error: String? = null,
)

/** Sentinel edit id: the editor is creating a new source rather than editing an existing one. */
internal const val SOURCE_NEW_ID_V94 = "__new_source_v94__"

internal const val AI_SAVED_SOURCE_ID_KEY_V75 = "online_ai_saved_source_id_v75"

internal fun displayedAiProviderLabelV80(state: OnlineBooksStateV36): String? =
    state.aiAttemptProviderLabel.takeIf { state.aiRunning } ?: state.aiProviderLabel

/** Restore only a persisted source identity; the durable source remains the name authority. */
internal fun restoredAiSavedSourceV75(
    sources: List<BookSourceV36>,
    savedSourceId: String?,
): BookSourceV36? = savedSourceId
    ?.takeIf(String::isNotBlank)
    ?.let { id -> sources.firstOrNull { it.id == id } }

internal fun reconcileAiSavedSourceV75(
    state: OnlineBooksStateV36,
    sources: List<BookSourceV36> = state.sources,
): OnlineBooksStateV36 {
    val restored = restoredAiSavedSourceV75(sources, state.aiSavedSourceId)
    return state.copy(
        aiSavedSourceId = restored?.id,
        aiSavedSourceName = restored?.name,
    )
}

internal fun aiSourceStartingStateV67(
    state: OnlineBooksStateV36,
    useBrowser: Boolean,
): OnlineBooksStateV36 = state.copy(
    aiSteps = emptyList(),
    aiRunning = true,
    aiAttemptProviderLabel = null,
    aiReport = null,
    aiError = null,
    aiStopped = false,
    aiLastUseBrowser = useBrowser,
    aiCanResumeValidatedRules = false,
    aiValidationRetryInput = null,
    aiSavedSourceId = null,
    aiSavedSourceName = null,
)

internal fun aiSourceStoppedStateV65(state: OnlineBooksStateV36): OnlineBooksStateV36 =
    state.copy(
        aiSteps = emptyList(),
        aiRunning = false,
        aiAttemptProviderLabel = null,
        aiReport = null,
        aiError = null,
        aiStopped = true,
        aiCanResumeValidatedRules = false,
        aiValidationRetryInput = null,
        aiSavedSourceId = null,
        aiSavedSourceName = null,
    )

internal fun aiSourceSavedStateV68(
    state: OnlineBooksStateV36,
    source: BookSourceV36,
): OnlineBooksStateV36 = state.copy(
    aiSteps = emptyList(),
    aiRunning = false,
    aiAttemptProviderLabel = null,
    aiReport = null,
    aiError = null,
    aiStopped = false,
    aiCanResumeValidatedRules = false,
    aiValidationRetryInput = null,
    aiSavedSourceId = source.id,
    aiSavedSourceName = source.name,
)

internal fun aiSourceProviderChangedStateV81(state: OnlineBooksStateV36): OnlineBooksStateV36 =
    state.copy(
        aiSteps = emptyList(),
        aiRunning = false,
        aiAttemptProviderLabel = null,
        aiReport = null,
        aiError = "本次使用的 AI 服务已被删除或修改，生成已停止；请确认服务后重试",
        aiStopped = false,
        aiCanResumeValidatedRules = false,
        aiValidationRetryInput = null,
        aiSavedSourceId = null,
        aiSavedSourceName = null,
    )

internal class OnlineBooksViewModelV36(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val projects = StoryProjectManager(application)
    private val initialSources = BookSourceStoreV36.read(application)
    private val restoredAiSavedSource = restoredAiSavedSourceV75(
        initialSources.getOrDefault(emptyList()),
        savedState[AI_SAVED_SOURCE_ID_KEY_V75],
    )
    private val _state = MutableStateFlow(OnlineBooksStateV36(
        sources = initialSources.getOrDefault(emptyList()),
        sourceStorageError = initialSources.exceptionOrNull()?.message,
        aiSavedSourceId = restoredAiSavedSource?.id,
        aiSavedSourceName = restoredAiSavedSource?.name,
    ))
    val state: StateFlow<OnlineBooksStateV36> = _state.asStateFlow()
    private var sourceEditJob: Job? = null
    private var searchJob: Job? = null
    private val searchGeneration = java.util.concurrent.atomic.AtomicLong()
    private val aiGeneration = java.util.concurrent.atomic.AtomicLong()
    private var downloadJob: Job? = null
    private var detailJob: Job? = null
    private val detailGeneration = java.util.concurrent.atomic.AtomicLong()
    private var aiJob: Job? = null
    private var aiRetryKey: AiValidationRetryInputV77? = null
    private var aiValidationCheckpoint = AiValidationCheckpointV76()
    private val repository = com.xiguli.langhuan.data.PersistentStoryRepository(application)
    private var activeProviderId: String? = null
    private var activeProviderPriorityIds: List<String> = emptyList()
    private var observedProviders: List<com.xiguli.langhuan.data.StoredAiProvider>? = null
    private var providerObservationVersion: Long = 0L
    private var aiAttemptProviderRevision: AiProviderAttemptRevisionV81? = null

    init {
        if (restoredAiSavedSource == null) clearAiSavedSourceCheckpoint()
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                providerObservationVersion++
                observedProviders = providers
                val selected = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                activeProviderPriorityIds = providers.map { it.id }
                activeProviderId = selected?.id
                _state.update { it.copy(aiProviderLabel = selected?.let { provider ->
                    listOf(provider.name, provider.model).filter(String::isNotBlank).joinToString(" · ")
                }) }
                aiAttemptProviderRevision
                    ?.takeIf { attempt ->
                        _state.value.aiRunning && !aiProviderAttemptStillCurrentV81(attempt, providers)
                    }
                    ?.let { stopAiForProviderChangeV81() }
            }
        }
    }

    // ---- AI-written sources ---------------------------------------------------------------------

    private fun clearAiSavedSourceCheckpoint() {
        savedState.remove<String>(AI_SAVED_SOURCE_ID_KEY_V75)
    }

    private fun syncAiSavedSourceCheckpoint() {
        _state.value.aiSavedSourceId?.let { savedState[AI_SAVED_SOURCE_ID_KEY_V75] = it }
            ?: savedState.remove<String>(AI_SAVED_SOURCE_ID_KEY_V75)
    }

    private fun clearAiValidationRetryV76() {
        aiRetryKey = null
        // Replace rather than only clearing: a cancelled builder may still be unwinding on another
        // dispatcher and must not be able to populate the next attempt's checkpoint.
        aiValidationCheckpoint = AiValidationCheckpointV76()
    }

    private fun stopAiForProviderChangeV81() {
        aiGeneration.incrementAndGet()
        aiJob?.cancel()
        aiAttemptProviderRevision = null
        clearAiValidationRetryV76()
        clearAiSavedSourceCheckpoint()
        _state.update(::aiSourceProviderChangedStateV81)
    }

    fun buildWithAi(siteUrl: String, keyword: String, useBrowser: Boolean = false) {
        if (!sourceStorageReady()) {
            clearAiValidationRetryV76()
            clearAiSavedSourceCheckpoint()
            _state.update { it.copy(aiError = it.sourceStorageError, aiStopped = false, aiCanResumeValidatedRules = false, aiValidationRetryInput = null, aiSavedSourceId = null, aiSavedSourceName = null) }
            return
        }
        if (aiJob?.isActive == true) return
        if (siteUrl.isBlank() || keyword.isBlank()) {
            clearAiValidationRetryV76()
            clearAiSavedSourceCheckpoint()
            _state.update { it.copy(aiError = "请填写网站链接和一本该站能搜到的书名", aiStopped = false, aiCanResumeValidatedRules = false, aiValidationRetryInput = null, aiSavedSourceId = null, aiSavedSourceName = null) }
            return
        }
        val retryKey = aiValidationRetryInputV77(siteUrl, keyword, useBrowser)
        if (aiRetryKey != retryKey) {
            aiValidationCheckpoint.clear()
            aiRetryKey = retryKey
        }
        val generation = aiGeneration.incrementAndGet()
        // Capture one coherent main-thread observation. The repository will retain this preference
        // only if the database priority identity is still the same when the attempt resolves.
        val preferredProviderId = activeProviderId
        val observedProviderPriorityIds = activeProviderPriorityIds
        val providerObservationVersionAtResolutionStart = providerObservationVersion
        aiAttemptProviderRevision = null
        clearAiSavedSourceCheckpoint()
        _state.update { aiSourceStartingStateV67(it, useBrowser) }
        aiJob = viewModelScope.launch {
            try {
                val provider = repository.activeProviderV80(
                    preferredId = preferredProviderId,
                    observedProviderIdsInPriorityOrder = observedProviderPriorityIds,
                )
                currentCoroutineContext().ensureActive()
                if (provider == null) {
                    val hasValidatedRules = aiValidationCheckpoint.hasValidatedRules()
                    _state.update {
                        if (aiGeneration.get() == generation) it.copy(
                            aiRunning = false,
                            aiAttemptProviderLabel = null,
                            aiError = "请先在设置里添加并启用一个 AI 服务",
                            aiCanResumeValidatedRules = hasValidatedRules,
                            aiValidationRetryInput = retryKey.takeIf { hasValidatedRules },
                        ) else it
                    }
                    return@launch
                }
                val providerRevision = AiProviderAttemptRevisionV81(provider.id, provider.revision)
                aiAttemptProviderRevision = providerRevision
                if (observedProviders?.let { providers ->
                        aiProviderObservationInvalidatesResolvedAttemptV82(
                            attempt = providerRevision,
                            providers = providers,
                            observationVersionAtResolutionStart = providerObservationVersionAtResolutionStart,
                            currentObservationVersion = providerObservationVersion,
                        )
                    } == true
                ) {
                    stopAiForProviderChangeV81()
                    return@launch
                }
                _state.update {
                    if (aiGeneration.get() == generation && it.aiRunning) {
                        it.copy(aiAttemptProviderLabel = provider.label)
                    } else it
                }
                val builder = BookSourceAiBuilderV37(
                    com.xiguli.langhuan.engine.UniversalAiGateway(provider.config),
                    onSteps = { steps ->
                        _state.update { if (aiGeneration.get() == generation) it.copy(aiSteps = steps) else it }
                    },
                    validationCheckpoint = aiValidationCheckpoint,
                )
                sourceAttemptV36 { builder.build(siteUrl, keyword, useBrowser) }
                    .onSuccess { report ->
                        if (aiGeneration.get() == generation) {
                            clearAiValidationRetryV76()
                            _state.update { it.copy(aiRunning = false, aiAttemptProviderLabel = null, aiReport = report, aiCanResumeValidatedRules = false, aiValidationRetryInput = null) }
                        }
                    }
                    .onFailure { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        _state.update { if (aiGeneration.get() == generation) it.copy(
                            aiRunning = false,
                            aiAttemptProviderLabel = null,
                            aiError = e.message ?: "生成失败",
                            aiCanResumeValidatedRules = aiValidationCheckpoint.hasValidatedRules(),
                            aiValidationRetryInput = retryKey.takeIf { aiValidationCheckpoint.hasValidatedRules() },
                        ) else it }
                    }
            } catch (error: kotlinx.coroutines.CancellationException) {
                _state.update { if (aiGeneration.get() == generation) it.copy(aiSteps = emptyList(), aiReport = null, aiError = null) else it }
                throw error
            } catch (error: Exception) {
                _state.update { if (aiGeneration.get() == generation) it.copy(
                    aiError = error.message ?: "AI 书源生成失败，请重试",
                    aiCanResumeValidatedRules = aiValidationCheckpoint.hasValidatedRules(),
                    aiValidationRetryInput = retryKey.takeIf { aiValidationCheckpoint.hasValidatedRules() },
                ) else it }
            } finally {
                if (aiGeneration.get() == generation) aiAttemptProviderRevision = null
                _state.update {
                    if (aiGeneration.get() == generation) {
                        it.copy(aiRunning = false, aiAttemptProviderLabel = null)
                    } else it
                }
            }
        }
    }

    fun saveAiSource(): Boolean {
        val report = _state.value.aiReport ?: return false
        if (!bookSourceSupportedV36(report.source)) {
            _state.update { it.copy(aiError = "生成的规则包含不支持的语法，无法保存") }
            return false
        }
        if (_state.value.sources.any { it.id == report.source.id }) {
            _state.update { it.copy(aiError = "这个网站的书源已存在。为保护原配置，本次没有覆盖；请在书源管理中编辑或移除旧规则后重试。") }
            return false
        }
        if (!merge(BookSourceImportResultV36(listOf(report.source), emptyList()))) {
            _state.update { it.copy(aiError = it.sourceStorageError ?: it.error ?: "书源保存失败") }
            return false
        }
        savedState[AI_SAVED_SOURCE_ID_KEY_V75] = report.source.id
        _state.update { aiSourceSavedStateV68(it, report.source) }
        return true
    }

    fun cancelAi() {
        aiGeneration.incrementAndGet()
        aiJob?.cancel()
        aiAttemptProviderRevision = null
        clearAiValidationRetryV76()
        clearAiSavedSourceCheckpoint()
        _state.update(::aiSourceStoppedStateV65)
    }

    // ---- Sources ------------------------------------------------------------------------------

    fun importSources(raw: String) {
        if (!sourceStorageReady()) return
        _state.update { it.copy(sourceImporting = true, sourceImportReport = null) }
        viewModelScope.launch {
            sourceAttemptV36 { withContext(Dispatchers.Default) { parseBookSourcesV36(raw) } }
                .onSuccess { result -> merge(result) }
                .onFailure { e -> importFailed(sourceJsonErrorV94(e)) }
        }
    }

    fun importFromUrl(url: String) {
        if (!sourceStorageReady()) return
        _state.update { it.copy(sourceImporting = true, sourceImportReport = null) }
        viewModelScope.launch {
            sourceAttemptV36 {
                val target = sourceImportUrlV94(url)
                runInterruptible(Dispatchers.IO) {
                    String(fetchSourceBytesV36(target), Charsets.UTF_8)
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> importFailed("下载书源失败：${e.message.orEmpty().take(160)}") }
        }
    }

    fun importFromFile(uri: Uri) {
        if (!sourceStorageReady()) return
        _state.update { it.copy(sourceImporting = true, sourceImportReport = null) }
        viewModelScope.launch {
            sourceAttemptV36 {
                runInterruptible(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { String(readSourceBytesV36(it, MAX_SOURCE_BYTES_V36), Charsets.UTF_8) } ?: error("无法读取文件")
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> importFailed("读取书源文件失败：${e.message.orEmpty().take(160)}") }
        }
    }

    private fun importFailed(message: String) {
        _state.update {
            it.copy(
                sourceImporting = false,
                error = message,
                sourceImportReport = SourceImportReportV94(emptyList(), emptyList(), emptyList(), error = message),
            )
        }
    }

    fun dismissSourceImportReport() = _state.update { it.copy(sourceImportReport = null, error = null, message = null) }

    private fun merge(result: BookSourceImportResultV36): Boolean {
        val previous = _state.value.sources
        val merged = mergeSourceImportsV36(previous, result)
        if (!saveSources(merged.sources, previous)) {
            _state.update { it.copy(sourceImporting = false) }
            return false
        }
        invalidateSourceResults()
        val skipped = if (merged.skipped.isEmpty()) "" else "；${merged.skipped.size} 个跳过：${merged.skipped.take(3).joinToString("、")}${if (merged.skipped.size > 3) " 等" else ""}"
        val previousIds = previous.mapTo(HashSet()) { it.id }
        val addedNames = merged.sources.filter { it.id !in previousIds }.map { it.name }
        val added = merged.sources.size - previous.size
        _state.update {
            it.copy(
                sources = merged.sources,
                message = "导入 $added 个书源$skipped",
                sourceImporting = false,
                sourceImportReport = SourceImportReportV94(addedNames, merged.skipped, merged.warnings),
            )
        }
        return true
    }

    /** Writes every stored source (Langhuan JSON) to a user-chosen document. */
    fun exportSourcesTo(uri: Uri) {
        viewModelScope.launch {
            val raw = exportSources().ifBlank { return@launch }
            sourceAttemptV36 {
                runInterruptible(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("无法写入文件")
                }
            }.onSuccess { _state.update { it.copy(message = "已导出 ${_state.value.sources.size} 个书源") } }
                .onFailure { e -> _state.update { it.copy(error = "导出书源失败：${e.message.orEmpty().take(120)}") } }
        }
    }

    fun toggleSource(id: String) { updateSources { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } } }

    fun deleteSource(id: String) { updateSources { list -> list.filterNot { it.id == id } } }

    private fun updateSources(transform: (List<BookSourceV36>) -> List<BookSourceV36>): Boolean {
        val previous = _state.value.sources
        val next = transform(previous)
        if (!saveSources(next, previous)) return false
        invalidateSourceResults()
        _state.update { reconcileAiSavedSourceV75(it.copy(sources = next), next) }
        syncAiSavedSourceCheckpoint()
        return true
    }

    private fun sourceStorageReady(): Boolean {
        val read = BookSourceStoreV36.read(context)
        if (read.isFailure) {
            _state.update { it.copy(sourceStorageError = read.exceptionOrNull()?.message, error = read.exceptionOrNull()?.message) }
            return false
        }
        if (_state.value.sourceStorageError != null) _state.update { it.copy(sources = read.getOrThrow(), sourceStorageError = null) }
        return true
    }

    private fun saveSources(next: List<BookSourceV36>, previous: List<BookSourceV36>): Boolean = sourceAttemptV36 {
        BookSourceStoreV36.save(context, next, expected = previous)
    }.onFailure { error ->
        val current = BookSourceStoreV36.read(context)
        if (current.isSuccess && current.getOrThrow() != previous) invalidateSourceResults()
        _state.update { it.copy(sources = current.getOrDefault(it.sources), error = error.message ?: "书源保存失败", sourceStorageError = current.exceptionOrNull()?.message) }
    }.isSuccess

    private fun invalidateSourceResults() {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _state.update { it.copy(searching = false, discoveryLabel = null, discoverySection = null,
            searchFailures = emptyList(), pendingSearchSourceIds = emptySet(), searchStopped = false,
            discoveryPage = 0, discoveryNextUrl = null, discoveryHasMore = false, discoveryVisitedUrls = emptySet(), discoveryPageError = null, results = emptyList()) }
    }

    // ---- Search -------------------------------------------------------------------------------

    fun search(query: String) {
        val key = query.trim()
        if (key.isEmpty()) return
        val sources = _state.value.sources.filter { it.enabled && it.searchUrl.isNotBlank() && it.searchList.isNotBlank() }
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "还没有启用的书源。先到「书源」页导入。") }
            return
        }
        launchSearch(key, sources, preserveCompleted = false)
    }

    /** Retry only failed or cancelled work; successful sources and their rows stay intact. */
    fun retrySearch() {
        val previous = _state.value
        if (previous.searching || previous.query.isBlank() || previous.discoverySection != null) return
        val ids = previous.pendingSearchSourceIds + previous.searchFailures.map { it.sourceId }
        if (ids.isEmpty()) return
        val sources = previous.sources.filter {
            it.id in ids && it.enabled && it.searchUrl.isNotBlank() && it.searchList.isNotBlank()
        }
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "未完成的书源已停用或移除，请检查书源") }
            return
        }
        launchSearch(previous.query, sources, preserveCompleted = true)
    }

    private fun launchSearch(key: String, sources: List<BookSourceV36>, preserveCompleted: Boolean) {
        val generation = searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _state.update { it.copy(query = key, discoveryLabel = null, discoverySection = null, discoveryPage = 0,
            discoveryNextUrl = null, discoveryHasMore = false, discoveryVisitedUrls = emptySet(), discoveryPageError = null,
            searching = true, searchedSources = if (preserveCompleted) (it.searchedSources - it.failedSources).coerceAtLeast(0) else 0,
            failedSources = 0, searchFailures = emptyList(), pendingSearchSourceIds = sources.map { source -> source.id }.toSet(),
            searchStopped = false, results = if (preserveCompleted) it.results else emptyList(), error = null, message = null) }
        searchJob = viewModelScope.launch {
            val gate = Semaphore(6)
            coroutineScope {
                sources.map { source ->
                    async(Dispatchers.IO) {
                        val attempt = gate.withPermit { sourceAttemptV36 { runInterruptible { searchSourceV36(source, key) } } }
                        val found = attempt.getOrDefault(emptyList())
                        val failure = attempt.exceptionOrNull()?.let { error -> OnlineSourceFailureV57(
                            source.id, source.name,
                            sourceFailureMessageV69(error, "书源搜索失败，请检查网络或规则"),
                        ) }
                        currentCoroutineContext().ensureActive()
                        // Results stream in per source; exact title matches float to the top.
                        _state.update { state ->
                            if (generation != searchGeneration.get()) return@update state
                            val merged = (state.results + found)
                                .distinctBy { it.sourceId + it.bookUrl }
                                .sortedWith(compareByDescending<OnlineBookV36> { it.name == key }.thenByDescending { it.name.contains(key) })
                            state.copy(results = merged, searchedSources = state.searchedSources + 1,
                                failedSources = state.failedSources + if (failure != null) 1 else 0,
                                searchFailures = if (failure != null) state.searchFailures + failure else state.searchFailures,
                                pendingSearchSourceIds = state.pendingSearchSourceIds - source.id)
                        }
                    }
                }.awaitAll()
            }
            _state.update {
                if (generation != searchGeneration.get()) return@update it
                it.copy(searching = false,
                    message = when {
                        it.failedSources > 0 -> "${it.failedSources}/${sources.size} 个书源请求失败，已保留成功结果；可重试或在书源页更换书源"
                        it.results.isEmpty() -> "没有找到「$key」，换个关键词或书源试试"
                        else -> null
                    })
            }
        }
    }

    fun discover(section: SourceDiscoveryV41) {
        val source = _state.value.sources.firstOrNull { it.id == section.sourceId && it.enabled } ?: return
        searchJob?.cancel()
        searchGeneration.incrementAndGet()
        _state.update { it.copy(query = "", discoveryLabel = "${source.name} · ${section.label}",
            searchFailures = emptyList(), pendingSearchSourceIds = emptySet(), searchStopped = false,
            discoverySection = section, discoveryPage = 0, discoveryNextUrl = null, discoveryHasMore = false, discoveryVisitedUrls = emptySet(), discoveryPageError = null,
            searching = false, searchedSources = 0, failedSources = 0, results = emptyList(), error = null, message = null) }
        loadMoreDiscovery()
    }

    fun loadMoreDiscovery() {
        val previous = _state.value
        if (previous.searching) return
        val section = previous.discoverySection ?: return
        val source = previous.sources.firstOrNull { it.id == section.sourceId && it.enabled } ?: return
        if (previous.discoveryPage > 0 && !previous.discoveryHasMore) return
        val page = previous.discoveryPage + 1
        val nextUrl = previous.discoveryNextUrl
        searchJob?.cancel()
        val generation = searchGeneration.incrementAndGet()
        _state.update { it.copy(searching = true, discoveryPageError = null, error = null) }
        searchJob = viewModelScope.launch {
            sourceAttemptV36 { runInterruptible(Dispatchers.IO) { discoverPageV41(source, section, page, nextUrl) } }
                .onSuccess { loaded ->
                    currentCoroutineContext().ensureActive()
                    _state.update { state ->
                        if (generation != searchGeneration.get()) return@update state
                        val merged = (state.results + loaded.books).distinctBy { it.sourceId to it.bookUrl }
                        val hasNew = merged.size > state.results.size
                        val visited = state.discoveryVisitedUrls + (nextUrl ?: section.url)
                        val pagingIssue = discoveryPagingIssueV41(loaded, hasNew, visited)
                        if (pagingIssue != null) state.copy(searching = false, results = merged, discoveryPageError = pagingIssue)
                        else state.copy(searching = false, searchedSources = 1, results = merged,
                            discoveryPage = page, discoveryNextUrl = loaded.nextUrl, discoveryVisitedUrls = visited,
                            discoveryHasMore = loaded.hasMore, discoveryPageError = null, message = null)
                    }
                }
                .onFailure { error ->
                    _state.update { state ->
                        if (generation != searchGeneration.get()) state else state.copy(searching = false,
                            failedSources = 1, discoveryPageError = "第 $page 页读取失败：${error.message.orEmpty()}")
                    }
                }
        }
    }

    fun stopSearch() {
        searchGeneration.incrementAndGet()
        searchJob?.cancel()
        _state.update { it.copy(searching = false,
            searchStopped = it.searchStopped || it.searching && it.discoverySection == null && it.query.isNotBlank(),
            message = if (it.searching && it.discoverySection == null) "已停止搜索，已完成结果保留" else it.message,
            discoveryPageError = if (it.discoverySection != null && it.searching) "已停止加载，可以重试本页" else it.discoveryPageError) }
    }

    // ---- Detail & download --------------------------------------------------------------------

    fun openDetail(book: OnlineBookV36) {
        val source = _state.value.sources.firstOrNull { it.id == book.sourceId } ?: return
        val generation = detailGeneration.incrementAndGet()
        detailJob?.cancel()
        _state.update { it.copy(detailLoading = true, detail = OnlineDetailV36(book, emptyList()),
            detailError = null, detailStopped = false, error = null) }
        detailJob = viewModelScope.launch {
            sourceAttemptV36 { runInterruptible(Dispatchers.IO) { loadBookCatalogueV50(source, book) } }
                .onSuccess { catalogue ->
                    val existing = withContext(Dispatchers.IO) { projects.findOnlineStory(source.id, catalogue.book.bookUrl) }
                    currentCoroutineContext().ensureActive()
                    _state.update { state ->
                        if (generation != detailGeneration.get()) state else state.copy(detailLoading = false,
                            detail = OnlineDetailV36(catalogue.book, catalogue.chapters, existing, catalogue.proof),
                            detailError = null, detailStopped = false)
                    }
                }
                .onFailure { e ->
                    currentCoroutineContext().ensureActive()
                    val message = "读取目录失败：" + (e.message?.replace('\n', ' ')?.replace('\r', ' ')?.take(320)
                        ?.takeIf(String::isNotBlank) ?: "请检查网络或书源规则")
                    _state.update { state -> if (generation != detailGeneration.get()) state else
                        state.copy(detailLoading = false, detailError = message, error = message) }
                }
        }
    }

    fun retryDetail() {
        val previous = _state.value
        if (previous.detailLoading || previous.detailError == null && !previous.detailStopped) return
        previous.detail?.book?.let(::openDetail)
    }

    fun stopDetail() {
        if (!_state.value.detailLoading) return
        detailGeneration.incrementAndGet()
        detailJob?.cancel()
        _state.update { it.copy(detailLoading = false, detailError = null, detailStopped = true, error = null) }
    }

    fun closeDetail() {
        if (_state.value.download != null) return
        detailGeneration.incrementAndGet()
        detailJob?.cancel()
        _state.update { it.copy(detail = null, detailLoading = false, detailError = null, detailStopped = false, error = null) }
    }

    /** Save identity and catalogue only. Reading and offline caching are separate actions. */
    fun addToShelf() {
        val detail = _state.value.detail ?: return
        if (detail.chapters.isEmpty() || _state.value.detailLoading || _state.value.addingToShelf) return
        if (detail.shelfStoryId != null) return
        _state.update { it.copy(addingToShelf = true, error = null) }
        viewModelScope.launch {
            sourceAttemptV36 {
                withContext(Dispatchers.IO) {
                    projects.createImportedStory(ImportedManuscript(
                        title = detail.book.name,
                        chapters = detail.chapters.map { ImportedChapter(it.title, "", it.url) },
                        sourceId = detail.book.sourceId, sourceBookUrl = detail.book.bookUrl, intro = detail.book.intro,
                    )).snapshot.novel.id
                }
            }.onSuccess { id ->
                _state.update { state -> state.copy(addingToShelf = false,
                    detail = state.detail?.takeIf { it.book == detail.book }?.copy(shelfStoryId = id) ?: state.detail,
                    message = "《${detail.book.name}》已加入书架，正文按阅读需要加载") }
            }.onFailure { e -> _state.update { it.copy(addingToShelf = false, error = "收藏失败：${e.message.orEmpty()}") } }
        }
    }

    fun readAddedBook() {
        val id = _state.value.detail?.shelfStoryId ?: return
        _state.update { it.copy(detail = null, createdStoryId = id) }
    }

    fun downloadDetail() {
        val id = _state.value.detail?.shelfStoryId ?: return
        downloadBook(id)
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.update { it.copy(download = null, message = "已停止缓存，已完成的章节保留") }
    }

    fun consumeCreated() = _state.update { it.copy(createdStoryId = null) }
    fun clearMessage() = _state.update { it.copy(message = null, error = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Refresh only the catalogue. No body request is made by checking for updates. */
    fun checkUpdate(novelId: String, onDone: (String) -> Unit) {
        if (downloadJob?.isActive == true) return onDone("已有离线缓存正在进行")
        viewModelScope.launch {
            val result = sourceAttemptV36 {
                val novel = withContext(Dispatchers.IO) { projects.loadStory(novelId)?.snapshot?.novel } ?: error("书籍已移除")
                check(novel.sourceId.isNotBlank()) { "旧版离线书缺少逐章来源，需核对目录；已保留原正文与阅读进度" }
                val source = _state.value.sources.firstOrNull { it.id == novel.sourceId } ?: error("原书源已被删除")
                val book = OnlineBookV36(source.id, source.name, novel.title, "", "", "", "", novel.sourceBookUrl)
                val (_, chapters) = runInterruptible(Dispatchers.IO) { loadBookV36(source, book) }
                currentCoroutineContext().ensureActive()
                val added = withContext(Dispatchers.IO) {
                    projects.appendOnlineCatalogue(novelId, source.id, novel.sourceBookUrl, chapters.map { ImportedChapter(it.title, "", it.url) })
                }
                if (added == 0) "本次目录未发现新增，已解析 ${chapters.size} 章"
                else "已补齐目录中缺少的 $added 章，原正文、书签与阅读位置保留；正文将在阅读时加载"
            }.getOrElse { e -> "检查更新失败：${e.message.orEmpty()}" }
            onDone(result)
        }
    }

    /** An explicit offline action; successful chapters survive cancellation and later failures. */
    fun downloadBook(novelId: String, onDone: (String) -> Unit = {}) {
        if (downloadJob?.isActive == true) return onDone("已有离线缓存正在进行")
        downloadJob = viewModelScope.launch {
            sourceAttemptV36 {
                val chapters = withContext(Dispatchers.IO) { projects.chapterDrafts(novelId) }
                val pending = chapters.filter { it.sourceUrl.isNotBlank() && it.content.isBlank() }
                if (pending.isEmpty()) return@sourceAttemptV36 "已有正文均已缓存在本机"
                val urls = chapters.map { it.sourceUrl }.filter(String::isNotBlank).toSet()
                _state.update { it.copy(download = OnlineDownloadV36(0, pending.size, 0), error = null) }
                pending.forEachIndexed { index, chapter ->
                    currentCoroutineContext().ensureActive()
                    // Sequential requests avoid flooding the source. A network failure stops
                    // this explicit batch; never loop automatically against a 429/challenge.
                    OnlineChapterCacheV46.load(context, novelId, chapter.chapterNumber, urls)
                    _state.update { it.copy(download = OnlineDownloadV36(index + 1, pending.size, 0)) }
                    if (index < pending.lastIndex) delay(400)
                }
                "已离线缓存 ${pending.size} 章"
            }.onSuccess { message ->
                _state.update { it.copy(download = null, message = message) }
                onDone(message)
            }.onFailure { error ->
                val message = "离线缓存已停止，已完成章节保留：${error.message.orEmpty()}"
                _state.update { it.copy(download = null, error = message) }
                onDone(message)
            }
        }
    }

    /** Exports the supported stored format; credentials in source headers stay user-visible. */
    fun exportSources(): String = sourceAttemptV36 { BookSourceStoreV36.raw(context) ?: "[]" }
        .getOrElse { error -> _state.update { it.copy(error = "原始书源无法导出，请保留应用数据：${error.javaClass.simpleName}") }; "" }

    fun beginSourceEdit(id: String) {
        val source = _state.value.sources.firstOrNull { it.id == id } ?: return
        sourceEditJob?.cancel()
        sourceTestJob?.cancel()
        _state.update { it.copy(sourceEditId = id, sourceEditSaving = false, sourceEditError = null, sourceTest = null, sourceEditDraft = BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), source)) }
    }

    /** 「新建书源」: an editor over a blank skeleton; saving adds a new source. */
    fun beginSourceCreate(initialJson: String? = null) {
        sourceEditJob?.cancel()
        sourceTestJob?.cancel()
        val draft = initialJson?.let(::normalizeSourceTextV94)?.takeIf { it.isNotBlank() }
            ?: BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), newSourceTemplateV94())
        _state.update { it.copy(sourceEditId = SOURCE_NEW_ID_V94, sourceEditSaving = false, sourceEditError = null, sourceTest = null, sourceEditDraft = draft) }
    }

    fun updateSourceEditDraft(raw: String) {
        if (_state.value.sourceEditSaving) return
        if (raw.length > 262144) {
            _state.update { it.copy(sourceEditError = "单个书源编辑草稿不能超过 256 KiB") }
            return
        }
        _state.update { it.copy(sourceEditDraft = raw, sourceEditError = null) }
    }

    fun cancelSourceEdit() {
        sourceEditJob?.cancel()
        sourceTestJob?.cancel()
        _state.update { it.copy(sourceEditId = null, sourceEditDraft = "", sourceEditSaving = false, sourceEditError = null, sourceTest = null) }
    }

    fun editSource(id: String, raw: String) {
        if (_state.value.sourceEditId != id || _state.value.sourceEditSaving) return
        _state.update { it.copy(sourceEditSaving = true, sourceEditError = null) }
        sourceEditJob?.cancel()
        sourceEditJob = viewModelScope.launch {
            sourceAttemptV36 {
                val parsed = withContext(Dispatchers.Default) { validateSourceDraftV94(raw) }
                currentCoroutineContext().ensureActive()
                require(_state.value.sourceEditId == id) { "书源编辑已取消" }
                if (id == SOURCE_NEW_ID_V94) {
                    val created = parsed.copy(id = parsed.id.ifBlank { manualSourceIdV94(parsed.baseUrl) }, enabled = true)
                    _state.value.sources.firstOrNull { it.id == created.id }?.let { existing ->
                        throw IllegalArgumentException("已有相同网址的书源「${existing.name}」，请在书源详情里编辑它")
                    }
                    check(updateSources { list -> (list + created).sortedBy { it.name } }) { _state.value.error ?: "书源保存失败" }
                    _state.update { it.copy(sourceEditId = null, sourceEditDraft = "", sourceEditSaving = false, sourceEditError = null, sourceTest = null, message = "已添加书源「${created.name}」") }
                } else {
                    require(_state.value.sources.any { it.id == id }) { "书源编辑已取消或原书源已删除" }
                    val edited = parsed.copy(id = id)
                    check(updateSources { list -> list.map { if (it.id == id) edited else it } }) { _state.value.error ?: "书源保存失败" }
                    _state.update { it.copy(sourceEditId = null, sourceEditDraft = "", sourceEditSaving = false, sourceEditError = null, sourceTest = null, message = "已更新书源「${edited.name}」") }
                }
            }.onFailure { error -> _state.update { it.copy(sourceEditSaving = false, sourceEditError = error.message ?: "书源编辑失败") } }
        }
    }

    private var sourceTestJob: Job? = null

    /** 「测试搜索」: runs the draft against the live site without saving it. */
    fun testSourceDraft(raw: String, keyword: String) {
        val key = keyword.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(sourceTest = SourceTestStateV94(keyword = "", running = false, error = "请输入一个测试书名")) }
            return
        }
        sourceTestJob?.cancel()
        _state.update { it.copy(sourceTest = SourceTestStateV94(keyword = key)) }
        sourceTestJob = viewModelScope.launch {
            sourceAttemptV36 {
                val source = withContext(Dispatchers.Default) { validateSourceDraftV94(raw) }
                    .let { it.copy(id = it.id.ifBlank { manualSourceIdV94(it.baseUrl) }) }
                require(source.searchUrl.isNotBlank() && source.searchList.isNotBlank()) { "这个书源没有搜索规则，无法测试搜索" }
                runInterruptible(Dispatchers.IO) { searchSourceV36(source, key) }
            }.onSuccess { books ->
                _state.update { state ->
                    state.copy(sourceTest = SourceTestStateV94(
                        keyword = key,
                        running = false,
                        books = books.take(20),
                        error = if (books.isEmpty()) "请求成功，但书籍列表规则没有匹配到结果；请检查「书籍列表」和「书名」规则" else null,
                    ))
                }
            }.onFailure { error ->
                _state.update { it.copy(sourceTest = SourceTestStateV94(keyword = key, running = false, error = sourceFailurePresentationOrMessageV94(error))) }
            }
        }
    }

    fun clearSourceTest() {
        sourceTestJob?.cancel()
        _state.update { it.copy(sourceTest = null) }
    }
}

private fun sourceFailurePresentationOrMessageV94(error: Throwable): String =
    error.message?.takeIf { it.isNotBlank() }?.take(200) ?: error.javaClass.simpleName
