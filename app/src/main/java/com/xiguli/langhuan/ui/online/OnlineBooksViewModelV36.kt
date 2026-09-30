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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Where a shelf book came from online, for "检查更新". */
@Serializable
internal data class OnlineLinkV36(
    val novelId: String,
    val sourceId: String,
    val bookUrl: String,
    val chapterCount: Int,
)

internal object BookSourceStoreV36 {
    private const val PREFS = "book_sources_v36"

    fun load(context: Context): List<BookSourceV36> = sourceAttemptV36 {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sources", null) ?: return emptyList()
        BookSourceJsonV36.decodeFromString(ListSerializer(BookSourceV36.serializer()), raw)
    }.getOrDefault(emptyList())

    fun save(context: Context, sources: List<BookSourceV36>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("sources", BookSourceJsonV36.encodeToString(ListSerializer(BookSourceV36.serializer()), sources))
            .apply()
    }

    fun link(context: Context, novelId: String): OnlineLinkV36? = sourceAttemptV36 {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("link_$novelId", null) ?: return null
        BookSourceJsonV36.decodeFromString(OnlineLinkV36.serializer(), raw)
    }.getOrNull()

    fun saveLink(context: Context, link: OnlineLinkV36) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("link_${link.novelId}", BookSourceJsonV36.encodeToString(OnlineLinkV36.serializer(), link))
            .apply()
    }
}

internal data class OnlineDetailV36(val book: OnlineBookV36, val chapters: List<OnlineChapterV36>)

internal data class OnlineDownloadV36(val done: Int, val total: Int, val failed: Int, val saving: Boolean = false)

internal data class OnlineBooksStateV36(
    val sources: List<BookSourceV36> = emptyList(),
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
    private val _state = MutableStateFlow(OnlineBooksStateV36(sources = BookSourceStoreV36.load(application)))
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
        merge(BookSourceImportResultV36(listOf(report.source), emptyList()))
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
        viewModelScope.launch {
            sourceAttemptV36 { withContext(Dispatchers.Default) { parseBookSourcesV36(raw) } }
                .onSuccess { result -> merge(result) }
                .onFailure { e -> _state.update { it.copy(error = "书源格式无法识别：${e.message.orEmpty().take(120)}") } }
        }
    }

    fun importFromUrl(url: String) {
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
        viewModelScope.launch {
            sourceAttemptV36 {
                runInterruptible(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { String(readSourceBytesV36(it, MAX_SOURCE_BYTES_V36), Charsets.UTF_8) } ?: error("无法读取文件")
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> _state.update { it.copy(error = "读取书源文件失败：${e.message.orEmpty()}") } }
        }
    }

    private fun merge(result: BookSourceImportResultV36) {
        val previous = _state.value.sources
        val merged = mergeSourceImportsV36(previous, result)
        invalidateSourceResults()
        BookSourceStoreV36.save(context, merged.sources)
        val skipped = if (merged.skipped.isEmpty()) "" else "；${merged.skipped.size} 个跳过：${merged.skipped.take(3).joinToString("、")}${if (merged.skipped.size > 3) " 等" else ""}"
        val added = merged.sources.size - previous.size
        _state.update { it.copy(sources = merged.sources, message = "导入 $added 个书源$skipped") }
    }

    fun toggleSource(id: String) = updateSources { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }

    fun deleteSource(id: String) = updateSources { list -> list.filterNot { it.id == id } }

    private fun updateSources(transform: (List<BookSourceV36>) -> List<BookSourceV36>) {
        val next = transform(_state.value.sources)
        invalidateSourceResults()
        BookSourceStoreV36.save(context, next)
        _state.update { it.copy(sources = next) }
    }

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
                .onSuccess { (detailed, chapters) -> _state.update { it.copy(detailLoading = false, detail = OnlineDetailV36(detailed, chapters)) } }
                .onFailure { e -> _state.update { it.copy(detailLoading = false, error = "读取目录失败：${e.message.orEmpty()}") } }
        }
    }

    fun closeDetail() {
        if (_state.value.download != null) return
        detailJob?.cancel()
        _state.update { it.copy(detail = null, detailLoading = false) }
    }

    /** Downloads every chapter and puts the book on the shelf. */
    fun addToShelf() {
        val detail = _state.value.detail ?: return
        val source = _state.value.sources.firstOrNull { it.id == detail.book.sourceId } ?: return
        if (detail.chapters.isEmpty() || downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            val texts = downloadChapters(source, detail.chapters) ?: return@launch
            currentCoroutineContext().ensureActive()
            _state.update { it.copy(download = it.download?.copy(saving = true)) }
            sourceAttemptV36 {
                val manuscript = ImportedManuscript(
                    title = detail.book.name,
                    chapters = detail.chapters.mapIndexed { i, chapter -> ImportedChapter(chapter.title, texts[i]) },
                )
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    val created = projects.createImportedStory(manuscript)
                    BookSourceStoreV36.saveLink(context, OnlineLinkV36(created.snapshot.novel.id, source.id, detail.book.bookUrl, detail.chapters.size))
                    created
                }
            }.onSuccess { created ->
                val id = created.snapshot.novel.id
                _state.update { it.copy(download = null, detail = null, createdStoryId = id, message = "《${detail.book.name}》已加入书架") }
            }.onFailure { e -> _state.update { it.copy(download = null, error = "保存到书架失败：${e.message.orEmpty()}") } }
        }
    }

    fun cancelDownload() {
        if (_state.value.download?.saving == true) return
        downloadJob?.cancel()
        _state.update { it.copy(download = null, message = "已取消下载") }
    }

    fun consumeCreated() = _state.update { it.copy(createdStoryId = null) }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    /** Checks the source for chapters beyond what is on the shelf and appends them. */
    fun checkUpdate(novelId: String, onDone: (String) -> Unit) {
        val link = BookSourceStoreV36.link(context, novelId) ?: return onDone("这本书不是从书源添加的")
        val source = _state.value.sources.firstOrNull { it.id == link.sourceId } ?: return onDone("原书源已被删除")
        if (downloadJob?.isActive == true) return onDone("已有下载正在进行")
        downloadJob = viewModelScope.launch {
            val result = sourceAttemptV36 {
                val book = OnlineBookV36(source.id, source.name, "", "", "", "", "", link.bookUrl)
                val (_, chapters) = runInterruptible(Dispatchers.IO) { loadBookV36(source, book) }
                val fresh = chapters.drop(link.chapterCount)
                if (fresh.isEmpty()) return@sourceAttemptV36 "已是最新，共 ${chapters.size} 章"
                val texts = downloadChapters(source, fresh) ?: return@sourceAttemptV36 "下载未完成，本次未更新"
                currentCoroutineContext().ensureActive()
                _state.update { it.copy(download = it.download?.copy(saving = true)) }
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    projects.appendImportedChapters(novelId, fresh.mapIndexed { i, c -> ImportedChapter(c.title, texts[i]) })
                        ?: error("书架中的书籍已不存在")
                    BookSourceStoreV36.saveLink(context, link.copy(chapterCount = chapters.size))
                }
                "新增 ${fresh.size} 章"
            }.getOrElse { e -> "检查更新失败：${e.message.orEmpty()}" }
            _state.update { it.copy(download = null) }
            onDone(result)
        }
    }

    private suspend fun downloadChapters(source: BookSourceV36, chapters: List<OnlineChapterV36>): List<String>? {
        val tocUrls = chapters.map { it.url }.toSet()
        val texts = arrayOfNulls<String>(chapters.size)
        val nextIndex = java.util.concurrent.atomic.AtomicInteger()
        var done = 0
        var failed = 0
        var totalChars = 0L
        _state.update { it.copy(download = OnlineDownloadV36(0, chapters.size, 0)) }
        return sourceAttemptV36 {
            coroutineScope {
                // A fixed worker pool avoids creating one suspended coroutine per chapter.
                List(minOf(4, chapters.size)) {
                    async(Dispatchers.IO) {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val index = nextIndex.getAndIncrement()
                            if (index >= chapters.size) break
                            var text: String? = null
                            for (attempt in 0 until 3) {
                                text = sourceAttemptV36 { runInterruptible { loadChapterTextV36(source, chapters[index], tocUrls) } }
                                    .getOrNull()?.takeIf { it.isNotBlank() }
                                if (text != null) break
                                if (attempt < 2) delay(600L * (attempt + 1))
                            }
                            currentCoroutineContext().ensureActive()
                            texts[index] = text
                            synchronized(this@OnlineBooksViewModelV36) {
                                done++
                                if (text == null) failed++
                                totalChars += text?.length ?: 0
                                check(totalChars <= 32L * 1024 * 1024) { "全书超过离线缓存大小限制" }
                                _state.update { it.copy(download = OnlineDownloadV36(done, chapters.size, failed)) }
                            }
                        }
                    }
                }.awaitAll()
            }
            check(failed == 0) { "$failed 章下载失败，本次未写入书架；请重试或更换书源" }
            texts.map { it.orEmpty() }
        }.getOrElse { error ->
            _state.update { it.copy(download = null, error = error.message ?: "下载失败") }
            null
        }
    }

    /** Exports the supported stored format; credentials in source headers stay user-visible. */
    fun exportSources(): String = BookSourceJsonV36.encodeToString(ListSerializer(BookSourceV36.serializer()), _state.value.sources)

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
                updateSources { list -> list.map { if (it.id == id) edited else it } }
                _state.update { it.copy(sourceEditId = null, sourceEditDraft = "", sourceEditSaving = false, sourceEditError = null, message = "已更新书源「${edited.name}」") }
            }.onFailure { error -> _state.update { it.copy(sourceEditSaving = false, sourceEditError = error.message ?: "书源编辑失败") } }
        }
    }
}
