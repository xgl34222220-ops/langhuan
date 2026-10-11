package com.xiguli.langhuan.ui

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/*
 * V97 书城列表封面。
 *
 * 发现 / 排行 / 搜索列表里只显示灰色占位，原因：
 *  1. 列表页的 <img> 多是懒加载：src 是 loading.gif / 1×1 像素，真地址在 data-src / data-original 里，
 *     书源的封面规则取到的是占位图，或者干脆为空（很多排行榜是纯文字列表）；
 *  2. 相对地址（/cover/1.jpg、//img.x.com/1.jpg）在部分路径上没有按页面地址补全；
 *  3. 封面只有 32 张的内存缓存，滚动回来、重新进入都要重新下载。
 * 这里：规则结果不像图片地址时回退到懒加载属性并按页面地址补全；仍为空时，仅对屏幕上可见的行
 * 读取详情页的 infoCover（最多 2 个并发）回填；所有下载过的封面写入 cacheDir/online-covers（LRU 约 50MB）。
 * 拿不到时保持原来的占位。
 */

/** Attributes lazy-loading scripts keep the real image address in, most specific first; plain src last. */
internal val LAZY_COVER_ATTRS_V97 = listOf(
    "data-src", "data-original", "data-lazy-src", "data-lazy", "data-echo", "data-url",
    "lazy-src", "lazy", "original", "data-original-src", "src",
)

private val PLACEHOLDER_NAME_V97 = Regex(
    "^(loading|loader|lazy|lazyload|blank|grey|gray|placeholder|nopic|nocover|no[-_]?cover|no[-_]?image|no[-_]?img|noimg|" +
        "pixel|spacer|transparent|1x1|empty)([-_.\\w]*)\\.(gif|png|jpe?g|webp|svg)$",
)

/** First non-blank line; Legado header options (`url,{"headers":…}`) are kept intact. */
private fun firstCoverCandidateV97(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.contains(",{")) return trimmed
    return trimmed.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
}

/**
 * True when a cover rule result plausibly addresses an image: not blank, not a lazy-load placeholder
 * (loading.gif, 1×1 data URI, nocover.png), not a page link, not leftover text.
 */
internal fun coverLooksUsableV97(raw: String): Boolean {
    val candidate = firstCoverCandidateV97(raw)
    val address = candidate.substringBefore(",{").trim()
    if (address.isEmpty() || address.length > 4096) return false
    if (address.startsWith("data:", true)) {
        // Lazy loaders put a tiny transparent GIF/PNG data URI in src; a real inline cover is far larger.
        return address.startsWith("data:image/", true) && !address.startsWith("data:image/svg", true) &&
            address.substringAfter("base64,", "").length >= 256
    }
    if (address.any { it.isWhitespace() }) return false
    val lower = address.lowercase()
    if (listOf("javascript:", "about:", "blob:", "#", "mailto:").any { lower.startsWith(it) }) return false
    if (lower.contains("{{") || lower.contains("}}")) return false
    if (lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://")) return false
    val path = lower.substringBefore('#').substringBefore('?')
    val name = path.substringAfterLast('/')
    if (PLACEHOLDER_NAME_V97.matches(name)) return false
    if (name.endsWith(".svg") || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".shtml")) return false
    // A bare word with no path or extension is text a rule picked up, not an address.
    if (!lower.startsWith("http") && !lower.startsWith("/") && !path.contains('/') && !name.contains('.')) return false
    return true
}

/** Resolves a cover address against the page it was found on; `//host/x` takes the page's scheme. */
internal fun resolveCoverUrlV97(pageUrl: String, raw: String): String {
    val candidate = firstCoverCandidateV97(raw)
    if (candidate.isEmpty()) return ""
    if (candidate.startsWith("data:", true)) return candidate
    val split = candidate.indexOf(",{")
    val address = if (split >= 0) candidate.substring(0, split).trim() else candidate
    val options = if (split >= 0) candidate.substring(split) else ""
    if (pageUrl.isBlank()) return candidate
    return resolveUrlV36(pageUrl, address) + options
}

/** Real image address from lazy-load attributes / srcset of the images inside a list row. */
internal fun lazyCoverFromElementV97(item: Element, pageUrl: String): String {
    if (jsonOfElementV95(item) != null) return ""
    val images = buildList {
        if (item.tagName().equals("img", true)) add(item)
        addAll(item.select("img").take(8))
    }
    for (image in images) {
        for (attr in LAZY_COVER_ATTRS_V97) {
            val value = image.attr(attr).trim()
            if (coverLooksUsableV97(value)) return resolveCoverUrlV97(pageUrl, value)
        }
        for (attr in listOf("data-srcset", "srcset")) {
            val first = image.attr(attr).trim().split(',').firstOrNull()?.trim()?.substringBefore(' ').orEmpty()
            if (coverLooksUsableV97(first)) return resolveCoverUrlV97(pageUrl, first)
        }
    }
    return ""
}

/**
 * The cover for a search / discovery row: the rule's result when it looks like an image address,
 * else the row's lazy-load attributes. Relative addresses are resolved against [pageUrl].
 */
internal fun listCoverV97(item: Element, ruleValue: String, pageUrl: String): String {
    if (coverLooksUsableV97(ruleValue)) return resolveCoverUrlV97(pageUrl, ruleValue)
    return lazyCoverFromElementV97(item, pageUrl)
}

/**
 * The cover on a book's detail page: infoCover, then og:image style metadata, then an image whose
 * alt/title names the book. Returns "" when the page has none.
 */
internal fun detailCoverFromPageV97(source: BookSourceV36, bookName: String, page: Document): String {
    val pageUrl = page.location()
    val ruled = runCatching {
        if (source.infoCover.isBlank()) "" else ruleStringV36(ruleContextV95(page, source.infoInit), source.infoCover)
    }.getOrElse { if (it is CancellationException) throw it else "" }
    if (coverLooksUsableV97(ruled)) return resolveCoverUrlV97(pageUrl, ruled)
    if (jsonOfElementV95(page) != null) return ""
    val meta = page.select(
        "meta[property=og:image], meta[name=og:image], meta[property=og:image:url], meta[name=twitter:image], link[rel=image_src]",
    ).firstNotNullOfOrNull { element ->
        element.attr("content").ifBlank { element.attr("href") }.trim().takeIf(::coverLooksUsableV97)
    }
    if (meta != null) return resolveCoverUrlV97(pageUrl, meta)
    val name = bookName.trim()
    if (name.isNotEmpty()) {
        page.select("img").asSequence().take(60)
            .filter { it.attr("alt").contains(name) || it.attr("title").contains(name) }
            .forEach { image -> lazyCoverFromElementV97(image, pageUrl).takeIf { it.isNotEmpty() }?.let { return it } }
    }
    return ""
}

/** Reads the book's detail page and returns its cover ("" when it has none). Network; call off the main thread. */
internal fun fetchDetailCoverV97(
    source: BookSourceV36,
    book: OnlineBookV36,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): String = withSourceRuleScopeV95(SourceRuleScopeV95(source, book = book)) {
    if (book.bookUrl.isBlank()) return@withSourceRuleScopeV95 ""
    val page = fetchDocument(source, SourceRequestV36(book.bookUrl))
    detailCoverFromPageV97(source, book.name, page).also { OnlineCoverSourcesV95.remember(it, source) }
}

/**
 * Detail-page cover lookups for list rows that have no cover. At most two run at once; results
 * (including "none") are remembered for the process so scrolling back does not refetch.
 */
internal object OnlineDetailCoversV97 {
    private const val MAX_ENTRIES = 1024
    private val semaphore = Semaphore(2)
    private val found = ConcurrentHashMap<String, String>()

    private fun key(book: OnlineBookV36) = "${book.sourceId}\u0000${book.bookUrl}"

    fun cached(book: OnlineBookV36): String? = found[key(book)]

    suspend fun lookup(
        source: BookSourceV36,
        book: OnlineBookV36,
        fetch: (BookSourceV36, OnlineBookV36) -> String = { s, b -> fetchDetailCoverV97(s, b) },
    ): String {
        val key = key(book)
        found[key]?.let { return it }
        return semaphore.withPermit {
            found[key]?.let { return@withPermit it }
            val cover = runInterruptible(Dispatchers.IO) {
                try {
                    fetch(source, book)
                } catch (error: Exception) {
                    if (error is CancellationException || error is InterruptedException) throw error
                    ""
                }
            }
            if (found.size >= MAX_ENTRIES) found.clear()
            found[key] = cover
            cover
        }
    }
}

/** The cover a row shows right away: its own when usable, else one found earlier on its detail page. */
internal fun rowCoverV97(book: OnlineBookV36): String =
    if (coverLooksUsableV97(book.cover)) book.cover else OnlineDetailCoversV97.cached(book).orEmpty()

/**
 * Disk cache for downloaded list covers: one file per cover url under [dir], least recently used
 * files removed once the folder grows past [maxBytes].
 */
internal class OnlineCoverDiskCacheV97(private val dir: File, private val maxBytes: Long = 50L * 1024 * 1024) {
    private val lock = Any()

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".img")
    }

    fun get(url: String): ByteArray? = synchronized(lock) {
        val file = fileFor(url)
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (!looksLikeImageV95(bytes)) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        bytes
    }

    fun put(url: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > maxBytes) return
        synchronized(lock) {
            runCatching {
                dir.mkdirs()
                val file = fileFor(url)
                val temp = File(dir, file.name + ".tmp")
                temp.writeBytes(bytes)
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
                file.setLastModified(System.currentTimeMillis())
                trim(keep = file)
            }
        }
    }

    private fun trim(keep: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (file == keep) continue
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    fun sizeBytes(): Long = synchronized(lock) { dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L }
}

internal object OnlineCoverDiskV97 {
    @Volatile private var cache: OnlineCoverDiskCacheV97? = null

    fun get(cacheDir: File): OnlineCoverDiskCacheV97 =
        cache ?: synchronized(this) { cache ?: OnlineCoverDiskCacheV97(File(cacheDir, "online-covers")).also { cache = it } }
}
