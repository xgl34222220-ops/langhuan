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

    fun load(context: Context): List<BookSourceV36> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sources", null) ?: return emptyList()
        BookSourceJsonV36.decodeFromString(ListSerializer(BookSourceV36.serializer()), raw)
    }.getOrDefault(emptyList())

    fun save(context: Context, sources: List<BookSourceV36>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("sources", BookSourceJsonV36.encodeToString(ListSerializer(BookSourceV36.serializer()), sources))
            .apply()
    }

    fun link(context: Context, novelId: String): OnlineLinkV36? = runCatching {
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

internal data class OnlineDownloadV36(val done: Int, val total: Int, val failed: Int)

internal data class OnlineBooksStateV36(
    val sources: List<BookSourceV36> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    val searchedSources: Int = 0,
    val results: List<OnlineBookV36> = emptyList(),
    val detailLoading: Boolean = false,
    val detail: OnlineDetailV36? = null,
    val download: OnlineDownloadV36? = null,
    val createdStoryId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

internal class OnlineBooksViewModelV36(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val projects = StoryProjectManager(application)
    private val _state = MutableStateFlow(OnlineBooksStateV36(sources = BookSourceStoreV36.load(application)))
    val state: StateFlow<OnlineBooksStateV36> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var downloadJob: Job? = null

    // ---- Sources ------------------------------------------------------------------------------

    fun importSources(raw: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.Default) { parseBookSourcesV36(raw) } }
                .onSuccess { result -> merge(result) }
                .onFailure { e -> _state.update { it.copy(error = "书源格式无法识别：${e.message.orEmpty().take(120)}") } }
        }
    }

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val connection = java.net.URL(url.trim()).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 20_000
                    try { connection.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } } finally { connection.disconnect() }
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> _state.update { it.copy(error = "下载书源失败：${e.message.orEmpty()}") } }
        }
    }

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: error("无法读取文件")
                }
            }.onSuccess { importSources(it) }
                .onFailure { e -> _state.update { it.copy(error = "读取书源文件失败：${e.message.orEmpty()}") } }
        }
    }

    private fun merge(result: BookSourceImportResultV36) {
        val existing = _state.value.sources.associateBy { it.id }.toMutableMap()
        result.sources.forEach { existing[it.id] = it }
        val merged = existing.values.sortedBy { it.name }
        BookSourceStoreV36.save(context, merged)
        val skipped = if (result.skipped.isEmpty()) "" else "；${result.skipped.size} 个跳过：${result.skipped.take(3).joinToString("、")}${if (result.skipped.size > 3) " 等" else ""}"
        _state.update { it.copy(sources = merged, message = "导入 ${result.sources.size} 个书源$skipped") }
    }

    fun toggleSource(id: String) = updateSources { list -> list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it } }

    fun deleteSource(id: String) = updateSources { list -> list.filterNot { it.id == id } }

    private fun updateSources(transform: (List<BookSourceV36>) -> List<BookSourceV36>) {
        val next = transform(_state.value.sources)
        BookSourceStoreV36.save(context, next)
        _state.update { it.copy(sources = next) }
    }

    // ---- Search -------------------------------------------------------------------------------

    fun search(query: String) {
        val key = query.trim()
        if (key.isEmpty()) return
        val sources = _state.value.sources.filter { it.enabled }
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "还没有启用的书源。先到「书源」页导入。") }
            return
        }
        searchJob?.cancel()
        _state.update { it.copy(query = key, searching = true, searchedSources = 0, results = emptyList(), error = null) }
        searchJob = viewModelScope.launch {
            val gate = Semaphore(6)
            coroutineScope {
                sources.map { source ->
                    async(Dispatchers.IO) {
                        val found = gate.withPermit { runCatching { searchSourceV36(source, key) }.getOrDefault(emptyList()) }
                        // Results stream in per source; exact title matches float to the top.
                        _state.update { state ->
                            val merged = (state.results + found)
                                .distinctBy { it.sourceId + it.bookUrl }
                                .sortedWith(compareByDescending<OnlineBookV36> { it.name == key }.thenByDescending { it.name.contains(key) })
                            state.copy(results = merged, searchedSources = state.searchedSources + 1)
                        }
                    }
                }.awaitAll()
            }
            _state.update { it.copy(searching = false, message = if (it.results.isEmpty()) "没有找到「$key」，换个关键词或书源试试" else null) }
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searching = false) }
    }

    // ---- Detail & download --------------------------------------------------------------------

    fun openDetail(book: OnlineBookV36) {
        val source = _state.value.sources.firstOrNull { it.id == book.sourceId } ?: return
        _state.update { it.copy(detailLoading = true, detail = OnlineDetailV36(book, emptyList()), error = null) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadBookV36(source, book) } }
                .onSuccess { (detailed, chapters) -> _state.update { it.copy(detailLoading = false, detail = OnlineDetailV36(detailed, chapters)) } }
                .onFailure { e -> _state.update { it.copy(detailLoading = false, error = "读取目录失败：${e.message.orEmpty()}") } }
        }
    }

    fun closeDetail() {
        if (_state.value.download != null) return
        _state.update { it.copy(detail = null, detailLoading = false) }
    }

    /** Downloads every chapter and puts the book on the shelf. */
    fun addToShelf() {
        val detail = _state.value.detail ?: return
        val source = _state.value.sources.firstOrNull { it.id == detail.book.sourceId } ?: return
        if (detail.chapters.isEmpty() || downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            val texts = downloadChapters(source, detail.chapters) ?: return@launch
            runCatching {
                val manuscript = ImportedManuscript(
                    title = detail.book.name,
                    chapters = detail.chapters.mapIndexed { i, chapter -> ImportedChapter(chapter.title, texts[i]) },
                )
                projects.createImportedStory(manuscript)
            }.onSuccess { created ->
                val id = created.snapshot.novel.id
                BookSourceStoreV36.saveLink(context, OnlineLinkV36(id, source.id, detail.book.bookUrl, detail.chapters.size))
                _state.update { it.copy(download = null, detail = null, createdStoryId = id, message = "《${detail.book.name}》已加入书架") }
            }.onFailure { e -> _state.update { it.copy(download = null, error = "保存到书架失败：${e.message.orEmpty()}") } }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.update { it.copy(download = null, message = "已取消下载") }
    }

    fun consumeCreated() = _state.update { it.copy(createdStoryId = null) }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    /** Checks the source for chapters beyond what is on the shelf and appends them. */
    fun checkUpdate(novelId: String, onDone: (String) -> Unit) {
        val link = BookSourceStoreV36.link(context, novelId) ?: return onDone("这本书不是从书源添加的")
        val source = _state.value.sources.firstOrNull { it.id == link.sourceId } ?: return onDone("原书源已被删除")
        viewModelScope.launch {
            val result = runCatching {
                val book = OnlineBookV36(source.id, source.name, "", "", "", "", "", link.bookUrl)
                val (_, chapters) = withContext(Dispatchers.IO) { loadBookV36(source, book) }
                val fresh = chapters.drop(link.chapterCount)
                if (fresh.isEmpty()) return@runCatching "已是最新，共 ${chapters.size} 章"
                val texts = downloadChapters(source, fresh) ?: return@runCatching "已取消"
                projects.appendImportedChapters(novelId, fresh.mapIndexed { i, c -> ImportedChapter(c.title, texts[i]) })
                BookSourceStoreV36.saveLink(context, link.copy(chapterCount = chapters.size))
                "新增 ${fresh.size} 章"
            }.getOrElse { e -> "检查更新失败：${e.message.orEmpty()}" }
            _state.update { it.copy(download = null) }
            onDone(result)
        }
    }

    private suspend fun downloadChapters(source: BookSourceV36, chapters: List<OnlineChapterV36>): List<String>? {
        val tocUrls = chapters.map { it.url }.toSet()
        val texts = arrayOfNulls<String>(chapters.size)
        var done = 0
        var failed = 0
        _state.update { it.copy(download = OnlineDownloadV36(0, chapters.size, 0)) }
        val gate = Semaphore(4)
        return runCatching {
            coroutineScope {
                chapters.mapIndexed { index, chapter ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            var text: String? = null
                            // Two retries per chapter; one bad page must not sink the book.
                            for (attempt in 0 until 3) {
                                text = runCatching { loadChapterTextV36(source, chapter, tocUrls) }.getOrNull()?.takeIf { it.isNotBlank() }
                                if (text != null) break
                                delay(600L * (attempt + 1))
                            }
                            texts[index] = text ?: "（本章下载失败，可稍后在书架长按本书「检查更新」或重新添加）"
                            synchronized(this@OnlineBooksViewModelV36) {
                                done++
                                if (text == null) failed++
                            }
                            _state.update { it.copy(download = OnlineDownloadV36(done, chapters.size, failed)) }
                        }
                    }
                }.awaitAll()
            }
            texts.map { it.orEmpty() }
        }.getOrNull()
    }
}
