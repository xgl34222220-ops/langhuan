package com.xiguli.langhuan.ui

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/*
 * Online book sources.
 *
 * Langhuan ships no sources. Users import their own (Langhuan JSON or Legado 书源 JSON). The engine
 * understands the parts of Legado's rule language that address HTML:
 *   - default syntax:  class.item.0@tag.a@href   (class./id./tag./text./children, index, !exclude)
 *   - @css:            @css:div.item > a@href
 *   - alternatives ||, concatenation &&, cleanup ##regex##replacement, leading '-' to reverse lists
 * Sources that need JavaScript (<js>, @js:) or JSON APIs (@json:, $.) are reported as unsupported.
 */

@Serializable
internal data class BookSourceV36(
    val id: String,
    val name: String,
    val baseUrl: String,
    val group: String = "",
    val enabled: Boolean = true,
    val searchUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val searchList: String = "",
    val searchName: String = "",
    val searchAuthor: String = "",
    val searchCover: String = "",
    val searchIntro: String = "",
    val searchLatest: String = "",
    val searchBookUrl: String = "",
    val infoName: String = "",
    val infoAuthor: String = "",
    val infoCover: String = "",
    val infoIntro: String = "",
    val infoTocUrl: String = "",
    val tocList: String = "",
    val tocName: String = "",
    val tocUrl: String = "",
    val tocNext: String = "",
    val contentText: String = "",
    val contentNext: String = "",
    val contentReplace: String = "",
)

internal data class OnlineBookV36(
    val sourceId: String,
    val sourceName: String,
    val name: String,
    val author: String,
    val cover: String,
    val intro: String,
    val latest: String,
    val bookUrl: String,
)

internal data class OnlineChapterV36(val title: String, val url: String)

internal val BookSourceJsonV36 = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

// ---- Import --------------------------------------------------------------------------------

internal data class BookSourceImportResultV36(val sources: List<BookSourceV36>, val skipped: List<String>)

/** Parses a Langhuan or Legado source list (array or single object). */
internal fun parseBookSourcesV36(raw: String): BookSourceImportResultV36 {
    val root = BookSourceJsonV36.parseToJsonElement(raw.trim())
    val items = when (root) {
        is JsonArray -> root.toList()
        is JsonObject -> listOf(root)
        else -> emptyList()
    }
    val sources = ArrayList<BookSourceV36>()
    val skipped = ArrayList<String>()
    items.forEach { element ->
        val obj = element as? JsonObject ?: return@forEach
        val source = if (obj.containsKey("bookSourceUrl")) fromLegadoV36(obj) else runCatching {
            BookSourceJsonV36.decodeFromJsonElement(BookSourceV36.serializer(), obj)
        }.getOrNull()
        val name = source?.name ?: obj.string("bookSourceName").ifBlank { "未命名书源" }
        when {
            source == null -> skipped += "$name（格式无法识别）"
            source.searchUrl.isBlank() || source.searchList.isBlank() -> skipped += "$name（没有搜索规则）"
            !bookSourceSupportedV36(source) -> skipped += "$name（使用了 JS 或 JSON 接口，暂不支持）"
            else -> sources += source
        }
    }
    return BookSourceImportResultV36(sources, skipped)
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.obj(key: String): JsonObject? = when (val v = this[key]) {
    is JsonObject -> v
    is JsonPrimitive -> v.contentOrNull?.let { runCatching { BookSourceJsonV36.parseToJsonElement(it).jsonObject }.getOrNull() }
    else -> null
}

private fun fromLegadoV36(obj: JsonObject): BookSourceV36 {
    val search = obj.obj("ruleSearch")
    val info = obj.obj("ruleBookInfo")
    val toc = obj.obj("ruleToc")
    val content = obj.obj("ruleContent")
    val headers = obj.obj("header")?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
    val base = obj.string("bookSourceUrl").trimEnd('/').substringBefore("#")
    return BookSourceV36(
        id = base.ifBlank { obj.string("bookSourceName") },
        name = obj.string("bookSourceName").ifBlank { base },
        baseUrl = base,
        group = obj.string("bookSourceGroup"),
        enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
        searchUrl = obj.string("searchUrl"),
        headers = headers,
        searchList = search?.string("bookList").orEmpty(),
        searchName = search?.string("name").orEmpty(),
        searchAuthor = search?.string("author").orEmpty(),
        searchCover = search?.string("coverUrl").orEmpty(),
        searchIntro = search?.string("intro").orEmpty(),
        searchLatest = search?.string("lastChapter").orEmpty(),
        searchBookUrl = search?.string("bookUrl").orEmpty(),
        infoName = info?.string("name").orEmpty(),
        infoAuthor = info?.string("author").orEmpty(),
        infoCover = info?.string("coverUrl").orEmpty(),
        infoIntro = info?.string("intro").orEmpty(),
        infoTocUrl = info?.string("tocUrl").orEmpty(),
        tocList = toc?.string("chapterList").orEmpty(),
        tocName = toc?.string("chapterName").orEmpty(),
        tocUrl = toc?.string("chapterUrl").orEmpty(),
        tocNext = toc?.string("nextTocUrl").orEmpty(),
        contentText = content?.string("content").orEmpty(),
        contentNext = content?.string("nextContentUrl").orEmpty(),
        contentReplace = content?.string("replaceRegex").orEmpty(),
    )
}

internal fun bookSourceSupportedV36(source: BookSourceV36): Boolean {
    val rules = listOf(
        source.searchUrl, source.searchList, source.searchName, source.searchBookUrl,
        source.tocList, source.tocName, source.tocUrl, source.contentText,
    )
    return rules.none { rule ->
        rule.contains("<js>", true) || rule.contains("@js:", true) || rule.startsWith("@json:", true) ||
            rule.trimStart().startsWith("$.") || rule.trimStart().startsWith("{{") || rule.contains("java.", false)
    }
}

// ---- Rule evaluation -------------------------------------------------------------------------

private val VALUE_ATTRS = setOf("text", "textNodes", "ownText", "html", "all", "href", "src", "content", "value", "title", "alt", "data-src", "data-original")

/** Elements selected by a list rule. */
internal fun ruleElementsV36(context: Element, rawRule: String): List<Element> {
    var rule = rawRule.trim()
    if (rule.isEmpty()) return emptyList()
    val reverse = rule.startsWith("-")
    if (reverse) rule = rule.drop(1)
    val alternatives = rule.split("||")
    for (alternative in alternatives) {
        val found = selectChainV36(context, alternative.trim(), valueRule = false).first
        if (found.isNotEmpty()) return if (reverse) found.reversed() else found
    }
    return emptyList()
}

/** A string value from a value rule, cleaned and trimmed. */
internal fun ruleStringV36(context: Element, rawRule: String): String {
    val rule = rawRule.trim()
    if (rule.isEmpty()) return ""
    val (body, regex, replacement) = splitReplaceV36(rule)
    for (alternative in body.split("||")) {
        val joined = alternative.split("&&").joinToString("") { part -> evaluateValueV36(context, part.trim()) }
        val cleaned = applyReplaceV36(joined, regex, replacement).trim()
        if (cleaned.isNotEmpty()) return cleaned
    }
    return ""
}

private fun splitReplaceV36(rule: String): Triple<String, String?, String> {
    val index = rule.indexOf("##")
    if (index < 0) return Triple(rule, null, "")
    val rest = rule.substring(index + 2).split("##")
    return Triple(rule.substring(0, index), rest.getOrNull(0), rest.getOrNull(1).orEmpty())
}

private fun applyReplaceV36(text: String, regex: String?, replacement: String): String =
    if (regex.isNullOrEmpty()) text else runCatching { text.replace(Regex(regex), replacement) }.getOrDefault(text)

private fun evaluateValueV36(context: Element, rule: String): String {
    if (rule.isEmpty()) return ""
    val (elements, attr) = selectChainV36(context, rule, valueRule = true)
    return elements.mapNotNull { element -> attrValueV36(element, attr ?: "text") }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

internal fun attrValueV36(element: Element, attr: String): String? = when (attr) {
    "text" -> element.text()
    "ownText" -> element.ownText()
    "textNodes" -> element.textNodes().joinToString("\n") { it.text().trim() }.trim()
    "html" -> htmlToTextV36(element)
    "all" -> element.outerHtml()
    "href", "src" -> element.absUrl(attr).ifBlank { element.attr(attr) }
    else -> element.attr(attr).ifBlank { null }
}

/**
 * Walks a rule into (elements, attribute). The attribute is only split off for value rules.
 * Supports @css: and Legado's default class./id./tag./text./children segments.
 */
private fun selectChainV36(context: Element, rule: String, valueRule: Boolean): Pair<List<Element>, String?> {
    if (rule.startsWith("@css:", ignoreCase = true)) {
        val body = rule.substring(5)
        val at = body.lastIndexOf('@')
        val (css, attr) = if (valueRule && at > 0 && !body.substring(at + 1).contains(' ')) body.substring(0, at) to body.substring(at + 1)
        else if (valueRule && at == 0) "" to body.substring(1)
        else body to null
        val selected = if (css.isBlank()) listOf(context) else runCatching { context.select(css).toList() }.getOrDefault(emptyList())
        return selected to attr
    }
    val segments = rule.split('@').filter { it.isNotEmpty() }
    var attr: String? = null
    val steps = if (valueRule && segments.isNotEmpty() && isAttrSegmentV36(segments.last())) {
        attr = segments.last()
        segments.dropLast(1)
    } else segments
    // A bare CSS selector (common in hand-written sources) is used directly.
    if (steps.size == 1 && !Regex("^(class|id|tag|text|children)\\.?").containsMatchIn(steps[0])) {
        val selected = runCatching { context.select(steps[0]).toList() }.getOrDefault(emptyList())
        return selected to attr
    }
    var current: List<Element> = listOf(context)
    for (step in steps) {
        current = current.flatMap { applySegmentV36(it, step) }
        if (current.isEmpty()) break
    }
    if (steps.isEmpty()) current = listOf(context)
    return current to attr
}

private fun isAttrSegmentV36(segment: String): Boolean = segment in VALUE_ATTRS || segment.startsWith("data-")

private fun applySegmentV36(element: Element, segment: String): List<Element> {
    // type.name.index  |  type.name!index  |  children
    val exclude = segment.contains('!')
    val parts = segment.replace('!', '.').split('.')
    val type = parts.getOrNull(0).orEmpty()
    val name = parts.getOrNull(1).orEmpty()
    val index = parts.getOrNull(2)?.toIntOrNull()
    val found: List<Element> = when (type) {
        "class" -> element.getElementsByClass(name).toList().filter { it !== element || element.hasClass(name) }
        "id" -> listOfNotNull(element.getElementById(name))
        "tag" -> element.getElementsByTag(name).toList().filter { it !== element }
        "text" -> element.getElementsContainingOwnText(name).toList()
        "children" -> element.children().toList()
        else -> runCatching { element.select(segment).toList() }.getOrDefault(emptyList())
    }
    if (index == null) return found
    val resolved = if (index < 0) found.size + index else index
    return if (exclude) found.filterIndexed { i, _ -> i != resolved } else listOfNotNull(found.getOrNull(resolved))
}

/** Element HTML to readable paragraphs: <br> and block tags become line breaks. */
internal fun htmlToTextV36(element: Element): String {
    val out = StringBuilder()
    fun walk(node: Node) {
        when (node) {
            is TextNode -> out.append(node.text())
            is Element -> {
                val tag = node.tagName().lowercase()
                if (tag in setOf("script", "style")) return
                val block = tag in setOf("p", "div", "br", "li", "h1", "h2", "h3", "h4", "section", "article")
                if (tag == "br") {
                    out.append('\n')
                    return
                }
                if (block) out.append('\n')
                node.childNodes().forEach { walk(it) }
                if (block) out.append('\n')
            }
        }
    }
    element.childNodes().forEach { walk(it) }
    return out.toString()
        .replace('\u00A0', ' ')
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

// ---- Network -------------------------------------------------------------------------------+
internal data class SourceRequestV36(val url: String, val method: String = "GET", val body: String? = null, val charset: String? = null)

/** Builds a request from a Legado-style url: `path?q={{key}}` optionally followed by `,{json options}`. */
internal fun buildSearchRequestV36(source: BookSourceV36, key: String, page: Int = 1): SourceRequestV36 {
    val raw = source.searchUrl.trim()
    val optionIndex = raw.indexOf(",{")
    val urlPart = if (optionIndex > 0) raw.substring(0, optionIndex) else raw
    val options = if (optionIndex > 0) runCatching { BookSourceJsonV36.parseToJsonElement(raw.substring(optionIndex + 1)).jsonObject }.getOrNull() else null
    val charset = options?.get("charset")?.jsonPrimitive?.contentOrNull
    val encoded = URLEncoder.encode(key, charset ?: "UTF-8")
    fun fill(template: String) = template.replace("{{key}}", encoded).replace("{{page}}", page.toString())
        .replace("searchKey", encoded).replace("searchPage", page.toString())
    val url = resolveUrlV36(source.baseUrl, fill(urlPart))
    val method = options?.get("method")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
    val body = options?.get("body")?.jsonPrimitive?.contentOrNull?.let(::fill)
    return SourceRequestV36(url, method, body, charset)
}

internal fun resolveUrlV36(base: String, target: String): String {
    val clean = target.trim()
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    return runCatching { URL(URL(if (base.endsWith("/")) base else "$base/"), clean).toString() }.getOrDefault(clean)
}

internal fun fetchDocumentV36(source: BookSourceV36, request: SourceRequestV36): Document {
    val first = runCatching { fetchDocumentFollowingRedirectsV36(source, request) }
    if (first.isSuccess) return first.getOrThrow()

    // Browsers frequently upgrade an explicitly typed http:// URL to HTTPS through HSTS or an
    // internal redirect before the request ever reaches the page. HttpURLConnection does not have
    // the browser's HSTS state, so retry the same URL over HTTPS once before reporting a failure.
    val https = httpsFallbackUrlV36(request.url)
    if (https != null) {
        val retry = runCatching { fetchDocumentFollowingRedirectsV36(source, request.copy(url = https)) }
        if (retry.isSuccess) return retry.getOrThrow()
        val firstMessage = first.exceptionOrNull()?.message.orEmpty()
        val secondMessage = retry.exceptionOrNull()?.message.orEmpty()
        error(listOf(firstMessage, secondMessage).filter { it.isNotBlank() }.distinct().joinToString("；").ifBlank { "网页请求失败" })
    }
    throw first.exceptionOrNull() ?: IllegalStateException("网页请求失败")
}

private fun fetchDocumentFollowingRedirectsV36(source: BookSourceV36, initial: SourceRequestV36): Document {
    var url = initial.url
    var method = initial.method.uppercase()
    var body = initial.body
    var referer: String? = source.baseUrl.takeIf { it.startsWith("http") }
    val cookies = LinkedHashMap<String, String>()

    repeat(8) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.requestMethod = method
        connection.setRequestProperty(
            "User-Agent",
            source.headers["User-Agent"]
                ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        )
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Pragma", "no-cache")
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
        referer?.takeIf { it.startsWith("http") }?.let { connection.setRequestProperty("Referer", it) }
        val configuredCookie = source.headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value.orEmpty()
        val learnedCookie = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        listOf(configuredCookie, learnedCookie).filter { it.isNotBlank() }.joinToString("; ").takeIf { it.isNotBlank() }
            ?.let { connection.setRequestProperty("Cookie", it) }
        source.headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", true) && !k.equals("Cookie", true)) connection.setRequestProperty(k, v)
        }
        if (body != null && method !in setOf("GET", "HEAD")) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body!!.toByteArray(Charset.forName(initial.charset ?: "UTF-8"))) }
        }
        try {
            val code = connection.responseCode
            connection.headerFields["Set-Cookie"].orEmpty().forEach { raw ->
                val pair = raw.substringBefore(';')
                val eq = pair.indexOf('=')
                if (eq > 0) cookies[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
            }
            if (code in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() }
                    ?: error("书源返回 $code，但没有跳转地址")
                val next = resolveUrlV36(url, location)
                val previousHost = runCatching { URL(url).host }.getOrNull()
                val nextHost = runCatching { URL(next).host }.getOrNull()
                if (previousHost != null && nextHost != null && !previousHost.equals(nextHost, true)) {
                    // Never leak cookies learned from one host to a different redirect target.
                    cookies.clear()
                }
                referer = url
                url = next
                if (code in setOf(301, 302, 303) && method !in setOf("GET", "HEAD")) {
                    method = "GET"
                    body = null
                }
                return@repeat
            }
            check(code in 200..299) { "书源返回 $code" }
            val bytes = connection.inputStream.use { it.readBytes() }
            val charset = initial.charset
                ?: connection.contentType?.substringAfter("charset=", "")?.substringBefore(';')?.trim()?.ifBlank { null }
                ?: sniffCharsetV36(bytes)
            val html = String(bytes, runCatching { Charset.forName(charset) }.getOrDefault(Charsets.UTF_8))
            return Jsoup.parse(html, url)
        } finally {
            connection.disconnect()
        }
    }
    error("网页跳转次数过多")
}

internal fun httpsFallbackUrlV36(raw: String): String? {
    val url = runCatching { URL(raw) }.getOrNull() ?: return null
    if (!url.protocol.equals("http", true)) return null
    return URL("https", url.host, if (url.port == 80) -1 else url.port, url.file).toString()
}

private fun sniffCharsetV36(bytes: ByteArray): String {
    val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
    return Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1) ?: "UTF-8"
}

// ---- Operations ----------------------------------------------------------------------------

internal fun searchSourceV36(source: BookSourceV36, key: String): List<OnlineBookV36> {
    val doc = fetchDocumentV36(source, buildSearchRequestV36(source, key))
    return ruleElementsV36(doc, source.searchList).mapNotNull { item ->
        val name = ruleStringV36(item, source.searchName)
        val url = ruleStringV36(item, source.searchBookUrl).ifBlank { item.selectFirst("a[href]")?.absUrl("href").orEmpty() }
        if (name.isBlank() || url.isBlank()) return@mapNotNull null
        OnlineBookV36(
            sourceId = source.id,
            sourceName = source.name,
            name = name,
            author = ruleStringV36(item, source.searchAuthor),
            cover = ruleStringV36(item, source.searchCover).let { if (it.isBlank()) it else resolveUrlV36(doc.location(), it) },
            intro = ruleStringV36(item, source.searchIntro),
            latest = ruleStringV36(item, source.searchLatest),
            bookUrl = resolveUrlV36(doc.location(), url),
        )
    }
}

/** Fills intro/cover from the book page and returns the table of contents. */
internal fun loadBookV36(source: BookSourceV36, book: OnlineBookV36): Pair<OnlineBookV36, List<OnlineChapterV36>> {
    val page = fetchDocumentV36(source, SourceRequestV36(book.bookUrl))
    val detailed = book.copy(
        name = ruleStringV36(page, source.infoName).ifBlank { book.name },
        author = ruleStringV36(page, source.infoAuthor).ifBlank { book.author },
        cover = ruleStringV36(page, source.infoCover).ifBlank { book.cover }.let { if (it.isBlank()) it else resolveUrlV36(page.location(), it) },
        intro = ruleStringV36(page, source.infoIntro).ifBlank { book.intro },
    )
    val tocStart = ruleStringV36(page, source.infoTocUrl).takeIf { it.isNotBlank() }?.let { resolveUrlV36(page.location(), it) }
    var doc = if (tocStart != null && tocStart != page.location()) fetchDocumentV36(source, SourceRequestV36(tocStart)) else page
    val chapters = ArrayList<OnlineChapterV36>()
    val visited = HashSet<String>()
    repeat(40) {
        visited += doc.location()
        ruleElementsV36(doc, source.tocList).forEach { item ->
            val title = ruleStringV36(item, source.tocName).ifBlank { item.text() }
            val url = ruleStringV36(item, source.tocUrl).ifBlank { item.selectFirst("a[href]")?.absUrl("href").orEmpty() }
            if (title.isNotBlank() && url.isNotBlank()) chapters += OnlineChapterV36(title, resolveUrlV36(doc.location(), url))
        }
        val next = ruleStringV36(doc, source.tocNext).takeIf { it.isNotBlank() }?.let { resolveUrlV36(doc.location(), it) }
        if (next == null || next in visited) return detailed to chapters.distinctBy { it.url }
        doc = fetchDocumentV36(source, SourceRequestV36(next))
    }
    return detailed to chapters.distinctBy { it.url }
}

/** Chapter body; follows "next page" links that stay inside the same chapter. */
internal fun loadChapterTextV36(source: BookSourceV36, chapter: OnlineChapterV36, tocUrls: Set<String>): String {
    val parts = ArrayList<String>()
    var url = chapter.url
    val visited = HashSet<String>()
    repeat(12) {
        visited += url
        val doc = fetchDocumentV36(source, SourceRequestV36(url))
        val rule = source.contentText.ifBlank { "@css:#content@html" }
        val text = ruleStringV36(doc, if (rule.substringAfterLast('@') in VALUE_ATTRS) rule else "$rule@html")
        parts += cleanContentV36(text, source.contentReplace)
        val next = ruleStringV36(doc, source.contentNext).takeIf { it.isNotBlank() }?.let { resolveUrlV36(doc.location(), it) }
        if (next == null || next in visited || next in tocUrls) return parts.joinToString("\n").trim()
        url = next
    }
    return parts.joinToString("\n").trim()
}

/** Legado replaceRegex: "##regex##replacement", possibly several joined; plain text is a regex. */
internal fun cleanContentV36(text: String, replace: String): String {
    if (replace.isBlank()) return text
    val body = replace.removePrefix("##")
    val parts = body.split("##")
    return applyReplaceV36(text, parts.getOrNull(0), parts.getOrNull(1).orEmpty())
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
