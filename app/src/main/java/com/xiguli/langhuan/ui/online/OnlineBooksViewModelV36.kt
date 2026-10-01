package com.xiguli.langhuan.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
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

internal data class OnlineDetailV36(val book: OnlineBookV36, val chapters: List<OnlineChapterV36>, val shelfStoryId: String? = null)

internal data class OnlineDownloadV36(val done: Int, val total: Int, val failed: Int, val saving: Boolean = false)

internal data class OnlineBooksStateV36(
    val sources: List<BookSourceV36> = emptyList(),
    val sourceStorageError: String? = null,
    val sourceEditId: String? = null,
    val sourceEditDraft: String = "",
    val sourceEditSaving: Boolean = false,
    val sourceEditError: String? = null,
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
    val results: List<OnlineBookV36> = emptyList(),
    val detailLoading: Boolean = false,
    val detail: OnlineDetailV36? = null,
    val download: OnlineDownloadV36? = null,
    val addingToShelf: Boolean = false,
    val createdStoryId: String? = null,
    val message: String? = null,
    val error: String? = null,
    /** AI source builder progress; empty when not running. */
    val aiProviderLabel: String? = null,
    val aiSteps: List<AiSourceStepV37> = emptyList(),
    val aiRunning: Boolean = false,
    val aiReport: AiSourceReportV37? = null,
    val aiError: String? = null,
)

internal class OnlineBooksViewModelV36(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val projects = StoryProjectManager(application)
    private val initialSources = BookSourceStoreV36.read(application)
    private val _state = MutableStateFlow(OnlineBooksStateV36(sources = initialSources.getOrDefault(emptyList()), sourceStorageError = initialSources.exceptionOrNull()?.message))
    val state: StateFlow<OnlineBooksStateV36> = _state.asStateFlow()
    private var sourceEditJob: Job? = null
    private var searchJob: Job? = null
    private val searchGeneration = java.util.concurrent.atomic.AtomicLong()
    private val aiGeneration = java.util.concurrent.atomic.AtomicLong()
    private var downloadJob: Job? = null
    private var detailJob: Job? = null
    private var aiJob: Job? = null
    private val repository = com.xiguli.langhuan.data.PersistentStoryRepository(application)
    private var activeProviderId: String? = null

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                val selected = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                activeProviderId = selected?.id
                _state.update { it.copy(aiProviderLabel = selected?.let { provider ->
                    listOf(provider.name, provider.model).filter(String::isNotBlank).joinToString(" · ")
                }) }
            }
        }
    }

    // ---- AI-written sources ---------------------------------------------------------------------

    fun buildWithAi(siteUrl: String, keyword: String) {
        if (!sourceStorageReady()) { _state.update { it.copy(aiError = it.sourceStorageError) }; return }
        if (aiJob?.isActive == true) return
        if (siteUrl.isBlank() || keyword.isBlank()) {
            _state.update { it.copy(aiError = "请填写网站链接和一本该站能搜到的书名") }
            return
        }
        val generation = aiGeneration.incrementAndGet()
        _state.update { it.copy(aiSteps = emptyList(), aiRunning = true, aiReport = null, aiError = null) }
        aiJob = viewModelScope.launch {
            val config = activeProviderId?.let { repository.providerConfig(it) }
            if (config == null) {
                _state.update { it.copy(aiRunning = false, aiError = "请先在设置里添加并启用一个 AI 服务") }
                return@launch
            }
            currentCoroutineContext().ensureActive()
            val builder = BookSourceAiBuilderV37(com.xiguli.langhuan.engine.UniversalAiGateway(config), onSteps = { steps ->
                _state.update { if (aiGeneration.get() == generation) it.copy(aiSteps = steps) else it }
            })
            sourceAttemptV36 { builder.build(siteUrl, keyword) }
                .onSuccess { report -> _state.update { if (aiGeneration.get() == generation) it.copy(aiRunning = false, aiReport = report) else it } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { if (aiGeneration.get() == generation) it.copy(aiRunning = false, aiError = e.message ?: "生成失败") else it }
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
        _state.update { it.copy(aiReport = null, aiSteps = emptyList()) }
        return true
    }

    fun cancelAi() {
        aiGeneration.incrementAndGet()
        aiJob?.cancel()
        _state.update { it.copy(aiRunning = false, aiSteps = emptyList(), aiReport = null, aiError = null) }
    }

    // ---- Sources ------------------------------------------------------------------------------

    fun importSources(raw: String) {
        if (!sourceStorageReady()) return
        viewModelScope.launch {
            sourceAttemptV36 { withContext(Dispatchers.Default) { parseBookSourcesV36(raw) } }
                .onSuccess { result -> merge(result) }
                .onFailure { e -> _state.update { it.copy(error = "书源格式无法识别：${e.message.orEmpty().take(120)}") } }
        }
    }

    fun importFromUrl(url: String) {
        if (!sourceStorageReady()) return
        viewModelScope.launch {
            sourceAttemptV36 {
                runInterruptible(Dispatchers.IO) {
                    String(fetchSourceBytesV36(url.trim()), Charsets.UTF_8)
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> _state.update { it.copy(error = "下载书源失败：${e.message.orEmpty()}") } }
        }
    }

    fun importFromFile(uri: Uri) {
        if (!sourceStorageReady()) return
        viewModelScope.launch {
            sourceAttemptV36 {
                runInterruptible(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { String(readSourceBytesV36(it, MAX_SOURCE_BYTES_V36), Charsets.UTF_8) } ?: error("无法读取文件")
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> _state.update { it.copy(error = "读取书源文件失败：${e.message.orEmpty()}") } }
        }
    }

    private fun merge(result: BookSourceImportResultV36): Boolean {
        val previous = _state.value.sources
        val merged = mergeSourceImportsV36(previous, result)
        if (!saveSources(merged.sources, previous)) return false
        invalidateSourceResults()
        val skipped = if (merged.skipped.isEmpty()) "" else "；${merged.skipped.size} 个跳过：${merged.skipped.take(3).joinToString("、")}${if (merged.skipped.size > 3) " 等" else ""}"
        val added = merged.sources.size - previous.size
        _state.update { it.copy(sources = merged.sources, message = "导入 $added 个书源$skipped") }
        return true
    }

    fun toggleSource(id: String) { updateSources { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } } }

    fun deleteSource(id: String) { updateSources { list -> list.filterNot { it.id == id } } }

    private fun updateSources(transform: (List<BookSourceV36>) -> List<BookSourceV36>): Boolean {
        val previous = _state.value.sources
        val next = transform(previous)
        if (!saveSources(next, previous)) return false
        invalidateSourceResults()
        _state.update { it.copy(sources = next) }
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
        searchJob?.cancel()
        val generation = searchGeneration.incrementAndGet()
        _state.update { it.copy(query = key, discoveryLabel = null, discoverySection = null, discoveryPage = 0,
            discoveryNextUrl = null, discoveryHasMore = false, discoveryVisitedUrls = emptySet(), discoveryPageError = null, searching = true, searchedSources = 0, failedSources = 0, results = emptyList(), error = null) }
        searchJob = viewModelScope.launch {
            val gate = Semaphore(6)
            coroutineScope {
                sources.map { source ->
                    async(Dispatchers.IO) {
                        val attempt = gate.withPermit { sourceAttemptV36 { runInterruptible { searchSourceV36(source, key) } } }
                        val found = attempt.getOrDefault(emptyList())
                        currentCoroutineContext().ensureActive()
                        // Results stream in per source; exact title matches float to the top.
                        _state.update { state ->
                            if (generation != searchGeneration.get()) return@update state
                            val merged = (state.results + found)
                                .distinctBy { it.sourceId + it.bookUrl }
                                .sortedWith(compareByDescending<OnlineBookV36> { it.name == key }.thenByDescending { it.name.contains(key) })
                            state.copy(results = merged, searchedSources = state.searchedSources + 1, failedSources = state.failedSources + if (attempt.isFailure) 1 else 0)
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
            discoveryPageError = if (it.discoverySection != null && it.searching) "已停止加载，可以重试本页" else it.discoveryPageError) }
    }

    // ---- Detail & download --------------------------------------------------------------------

    fun openDetail(book: OnlineBookV36) {
        val source = _state.value.sources.firstOrNull { it.id == book.sourceId } ?: return
        detailJob?.cancel()
        _state.update { it.copy(detailLoading = true, detail = OnlineDetailV36(book, emptyList()), error = null) }
        detailJob = viewModelScope.launch {
            sourceAttemptV36 { runInterruptible(Dispatchers.IO) { loadBookV36(source, book) } }
                .onSuccess { (detailed, chapters) ->
                    val existing = withContext(Dispatchers.IO) { projects.findOnlineStory(source.id, detailed.bookUrl) }
                    _state.update { it.copy(detailLoading = false, detail = OnlineDetailV36(detailed, chapters, existing)) }
                }
                .onFailure { e -> _state.update { it.copy(detailLoading = false, error = "读取目录失败：${e.message.orEmpty()}") } }
        }
    }

    fun closeDetail() {
        if (_state.value.download != null) return
        detailJob?.cancel()
        _state.update { it.copy(detail = null, detailLoading = false) }
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
                val existing = withContext(Dispatchers.IO) { projects.chapterDrafts(novelId) }
                val fresh = onlineCatalogueAppendV46(existing, chapters)
                if (fresh.isEmpty()) return@sourceAttemptV36 "目录已是最新，共 ${chapters.size} 章"
                currentCoroutineContext().ensureActive()
                val added = withContext(Dispatchers.IO) {
                    projects.appendOnlineCatalogue(novelId, source.id, novel.sourceBookUrl, chapters.map { ImportedChapter(it.title, "", it.url) })
                }
                "目录新增 $added 章，正文将在阅读时加载"
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
        _state.update { it.copy(sourceEditId = id, sourceEditSaving = false, sourceEditError = null, sourceEditDraft = BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), source)) }
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
        _state.update { it.copy(sourceEditId = null, sourceEditDraft = "", sourceEditSaving = false, sourceEditError = null) }
    }

    fun editSource(id: String, raw: String) {
        if (_state.value.sourceEditId != id || _state.value.sourceEditSaving) return
        _state.update { it.copy(sourceEditSaving = true, sourceEditError = null) }
        sourceEditJob?.cancel()
        sourceEditJob = viewModelScope.launch {
            sourceAttemptV36 {
                val result = withContext(Dispatchers.Default) { parseBookSourcesV36(raw) }
                currentCoroutineContext().ensureActive()
                require(result.sources.size == 1 && result.skipped.isEmpty()) { "请提供一个有效的静态 HTML 书源" }
                val edited = result.sources.single().copy(id = id)
                require(_state.value.sourceEditId == id && _state.value.sources.any { it.id == id }) { "书源编辑已取消或原书源已删除" }
                check(updateSources { list -> list.map { if (it.id == id) edited else it } }) { _state.value.error ?: "书源保存失败" }
                _state.update { it.copy(sourceEditId = null, sourceEditDraft = "", sourceEditSaving = false, sourceEditError = null, message = "已更新书源「${edited.name}」") }
            }.onFailure { error -> _state.update { it.copy(sourceEditSaving = false, sourceEditError = error.message ?: "书源编辑失败") } }
        }
    }
}
