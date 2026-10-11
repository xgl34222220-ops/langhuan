package com.xiguli.langhuan.ui

import java.text.Normalizer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

/*
 * 搜索聚合 / 换源 / 书源校验, modelled on Legado (gedoor/legado):
 *  - SearchModel merges results of every enabled source by 书名+作者 into one row with its origins;
 *  - ChangeBookSourceViewModel searches all sources for the same name (author filter), loads each
 *    candidate's catalogue for 最新章节/章节数, sorts by response time and switches with
 *    BookHelp.getDurChapter (match the chapter title near the old index, fall back to the index);
 *  - CheckSource runs 搜索 → 详情 → 目录 → 正文 per source and lets the user disable failed ones.
 */

/** Per-source request budget for aggregated search, 换源 and 校验 (Legado default: 15 s). */
internal const val SOURCE_TIMEOUT_MS_V97 = 15_000L
internal const val SOURCE_CONCURRENCY_V97 = 8

// ---- Identity of a book across sources ------------------------------------------------------

private val TITLE_NOISE_V97 = Regex("[\\s\\p{P}\\p{S}·•]+")
private val TITLE_SUFFIX_V97 = Regex("(（|\\()?(全文阅读|最新章节|全集|完整版|无弹窗|精校版|全本|完本|小说|txt下载)(）|\\))?$")
private val AUTHOR_PREFIX_V97 = Regex("^(作\\s*者|作家|著|author)[:：\\s]*", RegexOption.IGNORE_CASE)
private val AUTHOR_SUFFIX_V97 = Regex("(\\s*(著|作品|编著|/著))$")

internal fun normalizeBookNameV97(name: String): String {
    var text = Normalizer.normalize(name, Normalizer.Form.NFKC).trim()
    text = text.removePrefix("《").substringBefore("》").ifBlank { text }
    repeat(2) { text = TITLE_SUFFIX_V97.replace(text, "") }
    return TITLE_NOISE_V97.replace(text, "").lowercase()
}

internal fun normalizeAuthorV97(author: String): String {
    var text = Normalizer.normalize(author, Normalizer.Form.NFKC).trim()
    text = AUTHOR_PREFIX_V97.replace(text, "")
    text = AUTHOR_SUFFIX_V97.replace(text, "")
    // "作者：某某 | 更新：…" style values keep only the first field.
    text = text.split('|', '｜', '/', '\n', ' ').firstOrNull { it.isNotBlank() }.orEmpty()
    return TITLE_NOISE_V97.replace(text, "").lowercase()
}

/** Same book: equal normalized names, and authors equal or one of them unknown. */
internal fun sameBookV97(aName: String, aAuthor: String, bName: String, bAuthor: String): Boolean {
    if (normalizeBookNameV97(aName).isEmpty() || normalizeBookNameV97(aName) != normalizeBookNameV97(bName)) return false
    val x = normalizeAuthorV97(aAuthor)
    val y = normalizeAuthorV97(bAuthor)
    return x.isEmpty() || y.isEmpty() || x == y || x.contains(y) || y.contains(x)
}

internal data class AggregatedBookV97(
    /** The row shown: the first source that returned it (sources stream in by speed). */
    val primary: OnlineBookV36,
    /** Every source result for this book, primary first, one per source. */
    val origins: List<OnlineBookV36>,
) {
    val key: String get() = normalizeBookNameV97(primary.name) + "|" + normalizeAuthorV97(primary.author)
    val sourceCount: Int get() = origins.size
}

/**
 * Merges streamed search results by 书名+作者. A result with an unknown author joins the one group of
 * that name; arrival order is kept inside a group, and groups are ordered by relevance to [query]
 * (exact title, then contains), then by number of sources, then by first arrival.
 */
internal fun aggregateSearchResultsV97(results: List<OnlineBookV36>, query: String = ""): List<AggregatedBookV97> {
    val groups = ArrayList<MutableList<OnlineBookV36>>()
    for (book in results) {
        if (book.name.isBlank()) continue
        // Compare with the group's known author, so an author-less row cannot bridge two different authors.
        val group = groups.firstOrNull { members ->
            val known = members.firstOrNull { normalizeAuthorV97(it.author).isNotEmpty() } ?: members.first()
            sameBookV97(known.name, known.author, book.name, book.author)
        }
        if (group == null) groups += mutableListOf(book)
        else if (group.none { it.sourceId == book.sourceId }) group += book
    }
    val q = normalizeBookNameV97(query)
    return groups.mapIndexed { index, members -> index to AggregatedBookV97(members.first(), members.toList()) }
        .sortedWith(
            compareByDescending<Pair<Int, AggregatedBookV97>> { q.isNotEmpty() && normalizeBookNameV97(it.second.primary.name) == q }
                .thenByDescending { q.isNotEmpty() && normalizeBookNameV97(it.second.primary.name).contains(q) }
                .thenByDescending { it.second.sourceCount }
                .thenBy { it.first },
        ).map { it.second }
}

// ---- Reading position across catalogues -----------------------------------------------------

private val CHAPTER_PREFIX_V97 = Regex("^(正文|第[一二三四五六七八九十百千零〇两\\d]+卷|卷[一二三四五六七八九十百千零〇两\\d]+)[\\s:：、]*")

/** Title used to recognize the same chapter on two sources: number + words, punctuation-free. */
internal fun chapterMatchKeyV97(title: String): String {
    val text = Normalizer.normalize(title, Normalizer.Form.NFKC).trim()
    val stripped = CHAPTER_PREFIX_V97.replace(text, "")
    return TITLE_NOISE_V97.replace(stripped, "").lowercase()
}

/**
 * Legado BookHelp.getDurChapter: the chapter index on the new source for the reader's current chapter.
 * 1) the same title (normalized) nearest to the old index (scaled by catalogue sizes);
 * 2) the same chapter number (第123章 / 123.) nearest to it;
 * 3) the old index, clamped to the new catalogue.
 */
internal fun mapChapterIndexV97(oldTitles: List<String>, oldIndex: Int, newTitles: List<String>): Int {
    if (newTitles.isEmpty()) return 0
    val safeOld = oldIndex.coerceIn(0, (oldTitles.size - 1).coerceAtLeast(0))
    val title = oldTitles.getOrNull(safeOld).orEmpty()
    val scaled = if (oldTitles.size > 1) (safeOld.toLong() * (newTitles.size - 1) / (oldTitles.size - 1)).toInt() else safeOld
    val anchor = scaled.coerceIn(0, newTitles.lastIndex)
    fun nearest(predicate: (Int) -> Boolean): Int? = newTitles.indices.filter(predicate)
        .minWithOrNull(compareBy<Int> { kotlin.math.abs(it - anchor) }.thenBy { kotlin.math.abs(it - safeOld) })
    val key = chapterMatchKeyV97(title)
    if (key.isNotEmpty()) nearest { chapterMatchKeyV97(newTitles[it]) == key }?.let { return it }
    val number = chapterOrdinalV50(title)
    if (number != null) nearest { chapterOrdinalV50(newTitles[it]) == number }?.let { return it }
    return safeOld.coerceIn(0, newTitles.lastIndex)
}

// ---- 换源 candidates --------------------------------------------------------------------------

internal data class SourceCandidateV97(
    val sourceId: String,
    val sourceName: String,
    val book: OnlineBookV36,
    /** null while the catalogue of this candidate is still loading. */
    val chapterCount: Int? = null,
    val latestChapter: String = "",
    val responseMs: Long = 0,
    val tocError: String? = null,
    val current: Boolean = false,
)

internal data class SourceSwitchStateV97(
    val bookName: String,
    val author: String,
    val currentSourceId: String,
    val running: Boolean = true,
    val searched: Int = 0,
    val total: Int = 0,
    val candidates: List<SourceCandidateV97> = emptyList(),
    val failures: List<OnlineSourceFailureV57> = emptyList(),
    /** Only keep candidates whose author matches (Legado 「校验作者」), on by default. */
    val checkAuthor: Boolean = true,
    val switchingTo: String? = null,
    val error: String? = null,
)

/** Usable first (loaded catalogue, newest by chapter count tie), then by response time. */
internal fun sortCandidatesV97(list: List<SourceCandidateV97>): List<SourceCandidateV97> = list.sortedWith(
    compareByDescending<SourceCandidateV97> { it.current }
        .thenBy { if (it.tocError != null) 2 else if (it.chapterCount == null) 1 else 0 }
        .thenBy { it.responseMs },
)

/** Candidates visible with the author filter. */
internal fun visibleCandidatesV97(state: SourceSwitchStateV97): List<SourceCandidateV97> =
    sortCandidatesV97(state.candidates.filter { candidate ->
        candidate.current || !state.checkAuthor || state.author.isBlank() || candidate.book.author.isBlank() ||
            sameBookV97(state.bookName, state.author, candidate.book.name, candidate.book.author)
    })

/** Pick the best result of one source for the book: same name, matching author preferred. */
internal fun pickSourceResultV97(results: List<OnlineBookV36>, name: String, author: String): OnlineBookV36? {
    val same = results.filter { normalizeBookNameV97(it.name) == normalizeBookNameV97(name) }
    if (same.isEmpty()) return null
    return same.firstOrNull { author.isNotBlank() && normalizeAuthorV97(it.author) == normalizeAuthorV97(author) }
        ?: same.firstOrNull { sameBookV97(name, author, it.name, it.author) }
        ?: same.first()
}

/** Runs [block] on IO with a hard per-source deadline; the worker thread is interrupted on timeout. */
internal suspend fun <T> withSourceDeadlineV97(timeoutMs: Long = SOURCE_TIMEOUT_MS_V97, block: () -> T): T = try {
    withTimeout(timeoutMs) { runInterruptible(Dispatchers.IO) { block() } }
} catch (e: TimeoutCancellationException) {
    throw java.net.SocketTimeoutException("书源超过 ${timeoutMs / 1000} 秒未响应").apply { initCause(e) }
}

/**
 * Searches every source for [name], keeps each source's same-name result, then loads that candidate's
 * catalogue for chapter count / latest chapter. Results are reported as they arrive.
 */
internal suspend fun searchSourceCandidatesV97(
    sources: List<BookSourceV36>,
    name: String,
    author: String,
    search: (BookSourceV36, String) -> List<OnlineBookV36> = { s, k -> searchSourceV36(s, k) },
    catalogue: (BookSourceV36, OnlineBookV36) -> OnlineBookCatalogueV50 = { s, b -> loadBookCatalogueV50(s, b) },
    timeoutMs: Long = SOURCE_TIMEOUT_MS_V97,
    concurrency: Int = SOURCE_CONCURRENCY_V97,
    onFound: (SourceCandidateV97) -> Unit,
    onUpdated: (SourceCandidateV97) -> Unit,
    onSourceDone: (BookSourceV36, Throwable?) -> Unit,
) = coroutineScope {
    val gate = Semaphore(concurrency.coerceAtLeast(1))
    sources.map { source ->
        async {
            gate.withPermit {
                val started = System.nanoTime()
                val searched = try {
                    Result.success(withSourceDeadlineV97(timeoutMs) { search(source, name) })
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
                val hit = searched.getOrNull()?.let { pickSourceResultV97(it, name, author) }
                val elapsed = (System.nanoTime() - started) / 1_000_000
                if (hit == null) {
                    onSourceDone(source, searched.exceptionOrNull())
                    return@withPermit
                }
                val candidate = SourceCandidateV97(source.id, source.name, hit, responseMs = elapsed, latestChapter = hit.latest)
                onFound(candidate)
                onSourceDone(source, null)
                val toc = try {
                    Result.success(withSourceDeadlineV97(timeoutMs) { catalogue(source, hit) })
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
                onUpdated(toc.fold(
                    onSuccess = { loaded ->
                        candidate.copy(book = loaded.book.copy(latest = loaded.chapters.lastOrNull()?.title ?: hit.latest),
                            chapterCount = loaded.chapters.size,
                            latestChapter = loaded.chapters.lastOrNull()?.title ?: hit.latest,
                            tocError = if (loaded.chapters.isEmpty()) "目录为空" else null)
                    },
                    onFailure = { e -> candidate.copy(chapterCount = 0, tocError = sourceFailureMessageV69(e, "目录读取失败")) },
                ))
            }
        }
    }.awaitAll()
    Unit
}

// ---- 书源校验 ---------------------------------------------------------------------------------

internal enum class SourceCheckStatusV97(val label: String) {
    CHECKING("校验中"),
    OK("可用"),
    SEARCH_FAILED("搜索失败"),
    TOC_FAILED("目录失败"),
    CONTENT_FAILED("正文失败"),
    TIMEOUT("超时"),
}

internal data class SourceCheckResultV97(
    val sourceId: String,
    val status: SourceCheckStatusV97,
    val detail: String = "",
    val elapsedMs: Long = 0,
)

internal data class SourceCheckStateV97(
    val keyword: String,
    val running: Boolean = true,
    val total: Int = 0,
    val results: Map<String, SourceCheckResultV97> = emptyMap(),
) {
    val done: Int get() = results.values.count { it.status != SourceCheckStatusV97.CHECKING }
    val failedIds: List<String> get() = results.values
        .filter { it.status != SourceCheckStatusV97.OK && it.status != SourceCheckStatusV97.CHECKING }.map { it.sourceId }
    val okCount: Int get() = results.values.count { it.status == SourceCheckStatusV97.OK }
}

/** Default check keyword (Legado's CheckSource default is 「我的」); a common word most sites index. */
internal const val SOURCE_CHECK_KEYWORD_V97 = "我的"

private fun timedOutV97(e: Throwable): Boolean {
    var current: Throwable? = e
    repeat(5) {
        val c = current ?: return false
        if (c is java.net.SocketTimeoutException || c is java.io.InterruptedIOException || c is TimeoutCancellationException) return true
        current = c.cause
    }
    return false
}

/**
 * One source through search → detail/toc → first chapter content. Each stage has its own deadline,
 * so a slow site is 「超时」 rather than stalling the whole check.
 */
internal suspend fun checkSourceV97(
    source: BookSourceV36,
    keyword: String,
    search: (BookSourceV36, String) -> List<OnlineBookV36> = { s, k -> searchSourceV36(s, k) },
    catalogue: (BookSourceV36, OnlineBookV36) -> OnlineBookCatalogueV50 = { s, b -> loadBookCatalogueV50(s, b) },
    content: (BookSourceV36, OnlineBookV36, OnlineChapterV36, Set<String>) -> String =
        { s, b, c, urls -> loadChapterAuditV50(s, b, c, urls).text },
    timeoutMs: Long = SOURCE_TIMEOUT_MS_V97,
): SourceCheckResultV97 {
    val started = System.nanoTime()
    fun result(status: SourceCheckStatusV97, detail: String = "") =
        SourceCheckResultV97(source.id, status, detail, (System.nanoTime() - started) / 1_000_000)
    suspend fun <T> stage(failed: SourceCheckStatusV97, fallback: String, block: () -> T): Result<T> = try {
        Result.success(withSourceDeadlineV97(timeoutMs, block))
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
        Result.failure(SourceCheckFailureV97(if (timedOutV97(e)) SourceCheckStatusV97.TIMEOUT else failed,
            sourceFailureMessageV69(e, fallback), e))
    }
    return try {
        if (source.searchUrl.isBlank() || source.searchList.isBlank()) return result(SourceCheckStatusV97.SEARCH_FAILED, "未配置搜索")
        val books = stage(SourceCheckStatusV97.SEARCH_FAILED, "搜索失败") { search(source, keyword) }.getOrThrow()
        val book = books.firstOrNull() ?: return result(SourceCheckStatusV97.SEARCH_FAILED, "搜索「$keyword」没有结果")
        val toc = stage(SourceCheckStatusV97.TOC_FAILED, "目录读取失败") { catalogue(source, book) }.getOrThrow()
        val first = toc.chapters.firstOrNull() ?: return result(SourceCheckStatusV97.TOC_FAILED, "目录为空")
        val urls = toc.chapters.map { it.url }.toSet()
        val text = stage(SourceCheckStatusV97.CONTENT_FAILED, "正文读取失败") { content(source, toc.book, first, urls) }.getOrThrow()
        if (text.isBlank()) result(SourceCheckStatusV97.CONTENT_FAILED, "正文为空")
        else result(SourceCheckStatusV97.OK, "${toc.chapters.size} 章")
    } catch (e: SourceCheckFailureV97) {
        result(e.status, e.message.orEmpty())
    }
}

private class SourceCheckFailureV97(val status: SourceCheckStatusV97, message: String, cause: Throwable) : Exception(message, cause)

/** Check every source concurrently (bounded); each result is reported as it finishes. */
internal suspend fun checkSourcesV97(
    sources: List<BookSourceV36>,
    keyword: String,
    concurrency: Int = 4,
    check: suspend (BookSourceV36) -> SourceCheckResultV97 = { checkSourceV97(it, keyword) },
    onResult: (SourceCheckResultV97) -> Unit,
) = coroutineScope {
    val gate = Semaphore(concurrency.coerceAtLeast(1))
    sources.map { source -> async { gate.withPermit { onResult(check(source)) } } }.awaitAll()
    Unit
}

/** Manage-list order after a check: usable first, then unchecked, then failed; stable otherwise. */
internal fun sortSourcesByCheckV97(sources: List<BookSourceV36>, check: SourceCheckStateV97?): List<BookSourceV36> {
    if (check == null) return sources
    fun rank(id: String) = when (check.results[id]?.status) {
        SourceCheckStatusV97.OK -> 0
        null, SourceCheckStatusV97.CHECKING -> 1
        else -> 2
    }
    return sources.withIndex().sortedWith(compareBy({ rank(it.value.id) }, { it.index })).map { it.value }
}

// ---- Shared 换源 controller (book detail + reader) -------------------------------------------

/**
 * Owns one 换源 search at a time. The caller decides what picking a candidate does; loaded catalogues
 * are kept so the pick does not fetch the same table of contents twice.
 */
internal class SourceSwitchControllerV97(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val sources: () -> List<BookSourceV36>,
    private val search: (BookSourceV36, String) -> List<OnlineBookV36> = { s, k -> searchSourceV36(s, k) },
    private val catalogue: (BookSourceV36, OnlineBookV36) -> OnlineBookCatalogueV50 = { s, b -> loadBookCatalogueV50(s, b) },
    private val timeoutMs: Long = SOURCE_TIMEOUT_MS_V97,
) {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<SourceSwitchStateV97?>(null)
    val state: kotlinx.coroutines.flow.StateFlow<SourceSwitchStateV97?> = _state
    private var job: kotlinx.coroutines.Job? = null
    private val generation = java.util.concurrent.atomic.AtomicLong()
    private val catalogues = java.util.concurrent.ConcurrentHashMap<String, OnlineBookCatalogueV50>()

    /** Starts (or restarts) the search. [current] is listed first as 「当前」 and is not searched again. */
    fun open(name: String, author: String, current: OnlineBookV36?) {
        val id = generation.incrementAndGet()
        job?.cancel()
        catalogues.clear()
        val all = runCatching(sources).getOrElse { e ->
            _state.value = SourceSwitchStateV97(name, author, current?.sourceId.orEmpty(), running = false,
                error = e.message ?: "书源读取失败")
            return
        }
        val searchable = all.filter { it.enabled && it.searchUrl.isNotBlank() && it.searchList.isNotBlank() }
        val currentCandidate = current?.let { SourceCandidateV97(it.sourceId, it.sourceName, it, latestChapter = it.latest, current = true) }
        _state.value = SourceSwitchStateV97(
            bookName = name, author = author, currentSourceId = current?.sourceId.orEmpty(),
            running = searchable.isNotEmpty(), total = searchable.count { it.id != current?.sourceId },
            candidates = listOfNotNull(currentCandidate),
            error = if (searchable.isEmpty()) "没有启用的书源，请先在书源管理中导入或启用" else null,
        )
        if (searchable.isEmpty()) return
        fun update(transform: (SourceSwitchStateV97) -> SourceSwitchStateV97) {
            if (id != generation.get()) return
            _state.update { it?.let(transform) }
        }
        job = scope.launch {
            // The current source's catalogue (for its chapter count) loads alongside the search.
            current?.let { book ->
                searchable.firstOrNull { it.id == book.sourceId }?.let { source ->
                    launch {
                        val loaded = try { Result.success(withSourceDeadlineV97(timeoutMs) { catalogue(source, book) }) }
                            catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
                        loaded.getOrNull()?.let { catalogues[source.id] = it }
                        update { s -> s.copy(candidates = s.candidates.map { c -> if (!c.current) c else loaded.fold(
                            { l -> c.copy(chapterCount = l.chapters.size, latestChapter = l.chapters.lastOrNull()?.title ?: c.latestChapter) },
                            { e -> c.copy(chapterCount = 0, tocError = sourceFailureMessageV69(e, "目录读取失败")) },
                        ) }) }
                    }
                }
            }
            searchSourceCandidatesV97(
                searchable.filter { it.id != current?.sourceId }, name, author,
                search = search,
                catalogue = { s, b -> catalogue(s, b).also { catalogues[s.id] = it } },
                timeoutMs = timeoutMs,
                onFound = { c -> update { s -> s.copy(candidates = s.candidates.filterNot { it.sourceId == c.sourceId } + c) } },
                onUpdated = { c -> update { s -> s.copy(candidates = s.candidates.map { if (it.sourceId == c.sourceId) c else it }) } },
                onSourceDone = { source, error -> update { s -> s.copy(searched = s.searched + 1,
                    failures = if (error == null) s.failures else s.failures + OnlineSourceFailureV57(source.id, source.name,
                        sourceFailureMessageV69(error, "书源搜索失败"))) } },
            )
            update { it.copy(running = false) }
        }
    }

    fun toggleAuthorCheck() = _state.update { it?.copy(checkAuthor = !it.checkAuthor) }

    fun setSwitching(sourceId: String?, error: String? = null) = _state.update { it?.copy(switchingTo = sourceId, error = error) }

    /** The catalogue for a candidate: reuse the one loaded for the list, otherwise load it now. */
    suspend fun catalogueFor(candidate: SourceCandidateV97): OnlineBookCatalogueV50 {
        catalogues[candidate.sourceId]?.takeIf { it.book.bookUrl == candidate.book.bookUrl && it.chapters.isNotEmpty() }?.let { return it }
        val source = sources().firstOrNull { it.id == candidate.sourceId } ?: error("该书源已被删除或停用")
        val loaded = withSourceDeadlineV97(timeoutMs * 2) { catalogue(source, candidate.book) }
        check(loaded.chapters.isNotEmpty()) { "${source.name} 的目录为空，未换源" }
        catalogues[source.id] = loaded
        return loaded
    }

    fun close() {
        generation.incrementAndGet()
        job?.cancel()
        catalogues.clear()
        _state.value = null
    }
}
