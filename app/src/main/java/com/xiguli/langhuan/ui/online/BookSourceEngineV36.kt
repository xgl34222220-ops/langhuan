package com.xiguli.langhuan.ui

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
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
    val enabledExplore: Boolean = true,
    val exploreUrl: String = "",
    val exploreList: String = "",
    val exploreName: String = "",
    val exploreAuthor: String = "",
    val exploreCover: String = "",
    val exploreIntro: String = "",
    val exploreLatest: String = "",
    val exploreBookUrl: String = "",
    /** Explicitly selected by the user; rendering never enables executable source rules. */
    val useBrowser: Boolean = false,
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

internal data class BookSourceImportResultV36(
    val sources: List<BookSourceV36>,
    val skipped: List<String>,
    /** Sources that were imported with some optional rule dropped or discovery disabled. */
    val warnings: List<String> = emptyList(),
    /** Existing ids replaced by an explicit edit/update; imports never fill this. */
    val updated: Int = 0,
)

/** Parses a Langhuan or Legado source list (array or single object). */
internal fun parseBookSourcesV36(raw: String): BookSourceImportResultV36 {
    require(raw.length <= MAX_SOURCE_BYTES_V36) { "书源文件超过 4 MiB 限制" }
    val text = normalizeSourceTextV94(raw)
    require(text.isNotEmpty()) { "书源内容为空" }
    require(text.startsWith("[") || text.startsWith("{")) {
        if (text.startsWith("<")) "内容是网页而不是书源 JSON，请确认链接直接指向 .json 书源文件"
        else "不是书源 JSON：书源应以 [ 或 { 开头"
    }
    val root = BookSourceJsonV36.parseToJsonElement(text)
    val items = when (root) {
        is JsonArray -> root.toList()
        is JsonObject -> listOf(root)
        else -> emptyList()
    }
    require(items.size <= 500) { "单次最多导入 500 个书源" }
    val sources = ArrayList<BookSourceV36>()
    val skipped = ArrayList<String>()
    val warnings = ArrayList<String>()
    items.forEach { element ->
        val obj = element as? JsonObject ?: run { skipped += "未命名书源（不是对象）"; return@forEach }
        val legado = obj.containsKey("bookSourceUrl")
        val decoded = if (legado) fromLegadoV36(obj) else runCatching {
            BookSourceJsonV36.decodeFromJsonElement(BookSourceV36.serializer(), obj)
                // A hand-made source (V94 editor) has no id yet: like Legado, its site address is the id.
                .let { if (it.id.isBlank()) it.copy(id = manualSourceIdV94(it.baseUrl)) else it }
        }.getOrNull()
        val name = decoded?.name?.ifBlank { null } ?: obj.string("bookSourceName").ifBlank { obj.string("name").ifBlank { "未命名书源" } }
        val sourceType = (obj["bookSourceType"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val unsupported = if (legado) unsupportedLegadoCapabilitiesV41(obj) else emptyList()
        val sanitized = decoded?.let(::sanitizeImportedSourceV94)
        val source = sanitized?.source
        val discoveryIssues = source?.let { sourceDiscoveryCatalogV41(it.copy(enabled = true, enabledExplore = true)).issues }.orEmpty()
        val discoveryUsable = source?.let { sourceDiscoveriesV41(it.copy(enabled = true, enabledExplore = true)).isNotEmpty() } ?: false
        val searchRequestIssue = source?.takeIf { it.searchUrl.isNotBlank() }?.let {
            runCatching { buildSearchRequestV36(it, "test") }.exceptionOrNull()?.message
        }
        when {
            sourceType != 0 -> skipped += "$name（${when (sourceType) { 1 -> "音频"; 2 -> "图片/漫画"; 3 -> "文件下载"; else -> "非文字" }}书源，暂只支持文字小说）"
            unsupported.isNotEmpty() -> skipped += "$name（暂不支持：${unsupported.joinToString("、")}）"
            source == null -> skipped += "$name（格式无法识别：缺少 bookSourceUrl 或 id/name/baseUrl）"
            sourceRuleUnsupportedV94(source.searchUrl) != null ->
                skipped += "$name（搜索地址 使用了 ${sourceRuleUnsupportedV94(source.searchUrl)}，暂不支持）"
            searchRequestIssue != null -> skipped += "$name（searchUrl：$searchRequestIssue）"
            discoveryIssues.isNotEmpty() && !discoveryUsable && source.searchUrl.isBlank() -> skipped += "$name（发现规则无效：${discoveryIssues.joinToString("；")}）"
            source.id.isBlank() || runCatching { publicSourceUrlV36(source.baseUrl) }.isFailure -> skipped += "$name（网站地址无效）"
            (source.searchUrl.isBlank() || source.searchList.isBlank()) &&
                (source.exploreList.isBlank() || !discoveryUsable) -> skipped += "$name（没有可用的搜索或发现规则）"
            !bookSourceSupportedV36(source) -> skipped += "$name（${unsupportedEssentialRuleV94(source)}，暂不支持）"
            else -> {
                sources += source
                sanitized.notices.forEach { warnings += "$name：$it" }
            }
        }
    }
    return BookSourceImportResultV36(sources, skipped, warnings)
}

/** Names the first essential rule that still needs scripts after optional rules were dropped. */
private fun unsupportedEssentialRuleV94(source: BookSourceV36): String {
    val essential = listOf(
        "搜索地址" to source.searchUrl, "搜索列表" to source.searchList, "搜索书名" to source.searchName,
        "搜索链接" to source.searchBookUrl, "目录地址" to source.infoTocUrl, "章节列表" to source.tocList,
        "章节名" to source.tocName, "章节链接" to source.tocUrl, "正文" to source.contentText,
        "发现列表" to source.exploreList, "发现书名" to source.exploreName, "发现链接" to source.exploreBookUrl,
    )
    val hit = essential.firstNotNullOfOrNull { (label, rule) -> sourceRuleUnsupportedV94(rule)?.let { "$label 使用了 $it" } }
    return hit ?: "使用了 JS 或 JSON 接口"
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.obj(key: String): JsonObject? = when (val v = this[key]) {
    is JsonObject -> v
    is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() }?.let { runCatching { parseSourceOptionsV94(it) }.getOrNull() }
    else -> null
}

/** These fields change execution; accepting them while dropping them would corrupt an imported source. */
private fun unsupportedLegadoCapabilitiesV41(obj: JsonObject): List<String> {
    val dynamic = listOf("jsLib", "mainJs", "loginCheckJs", "coverDecodeJs", "exploreScreen")
    return dynamic.filter { key ->
        val value = obj[key]
        value != null && value.toString() !in setOf("null", "\"\"", "{}", "[]") &&
            ((value as? JsonPrimitive)?.contentOrNull?.isNotBlank() ?: true)
    }
}

private fun fromLegadoV36(obj: JsonObject): BookSourceV36 {
    val explore = obj.obj("ruleExplore")
    val search = obj.obj("ruleSearch")
    val info = obj.obj("ruleBookInfo")
    val toc = obj.obj("ruleToc")
    val content = obj.obj("ruleContent")
    val headers = obj.obj("header")?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
    val base = obj.string("bookSourceUrl").trim().trimEnd('/').substringBefore("#")
    return BookSourceV36(
        id = base.ifBlank { obj.string("bookSourceName") },
        name = obj.string("bookSourceName").ifBlank { base },
        baseUrl = base,
        group = obj.string("bookSourceGroup"),
        enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: true,
        searchUrl = obj.string("searchUrl"),
        enabledExplore = (obj["enabledExplore"] as? JsonPrimitive)?.booleanOrNull ?: true,
        exploreUrl = obj.string("exploreUrl"),
        exploreList = explore?.string("bookList").orEmpty(),
        exploreName = explore?.string("name").orEmpty(),
        exploreAuthor = explore?.string("author").orEmpty(),
        exploreCover = explore?.string("coverUrl").orEmpty(),
        exploreIntro = explore?.string("intro").orEmpty(),
        exploreLatest = explore?.string("lastChapter").orEmpty(),
        exploreBookUrl = explore?.string("bookUrl").orEmpty(),
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
        source.searchAuthor, source.searchCover, source.searchIntro, source.searchLatest,
        source.infoName, source.infoAuthor, source.infoCover, source.infoIntro, source.infoTocUrl,
        source.tocList, source.tocName, source.tocUrl, source.tocNext, source.contentText,
        source.contentNext, source.contentReplace,
        source.exploreUrl, source.exploreList, source.exploreName, source.exploreAuthor,
        source.exploreCover, source.exploreIntro, source.exploreLatest, source.exploreBookUrl,
    )
    return rules.none { rule ->
        rule.length > 8192 ||
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

/** Linear-time remote cleanup rules. Lookaround and pattern backreferences are unsupported. */
internal fun sourceRegexReplaceV36(text: String, regex: String, replacement: String): String {
    require(regex.length <= 1024 && replacement.length <= 1024) { "书源正则或替换文本超过 1024 字符限制" }
    val matcher = try { com.google.re2j.Pattern.compile(regex).matcher(text) }
        catch (e: com.google.re2j.PatternSyntaxException) {
            throw IllegalArgumentException("书源正则不受支持（不支持环视或模式反向引用）", e)
        }
    val out = StringBuffer()
    while (matcher.find()) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("书源规则已取消")
        matcher.appendReplacement(out, replacement)
        require(out.length <= MAX_SOURCE_BYTES_V36) { "书源替换结果超过大小限制" }
    }
    matcher.appendTail(out)
    require(out.length <= MAX_SOURCE_BYTES_V36) { "书源替换结果超过大小限制" }
    return out.toString()
}

private fun applyReplaceV36(text: String, regex: String?, replacement: String): String =
    if (regex.isNullOrEmpty()) text else sourceRegexReplaceV36(text, regex, replacement)

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
    require(rule.length <= 8192) { "书源选择器过长" }
    require(!rule.contains(":matches", true) && !rule.contains("~=") && !rule.contains('\\')) {
        "不支持书源中的正则 CSS 选择器或转义选择器；请使用普通 class/id 选择器和 ## 文本清理"
    }
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
    val optionIndex = Regex(",\\s*(?=\\{)").find(raw)?.range?.first ?: -1
    val urlPart = if (optionIndex > 0) raw.substring(0, optionIndex) else raw
    val options = if (optionIndex > 0) {
        parseSourceOptionsV94(raw.substring(optionIndex + 1))
    } else null
    require(options == null || options.keys.all { it in setOf("method", "body", "charset") }) {
        "请求包含不支持的选项：${options?.keys.orEmpty() - setOf("method", "body", "charset") }"
    }
    require(options == null || options.values.all { it is JsonPrimitive && it.isString }) { "method/body/charset 请求选项必须是字符串" }
    require(!urlPart.contains('<') && !urlPart.contains('>')) { "暂不支持 <首页,后续页> 地址表达式，请使用 {{page}} 或静态下一页链接" }
    val charset = options?.get("charset")?.jsonPrimitive?.contentOrNull
    val encoded = URLEncoder.encode(key, charset ?: "UTF-8")
    fun fill(template: String): String {
        val filled = template.replace("{{key}}", encoded).replace("{{page}}", page.toString())
            .replace("searchKey", encoded).replace("searchPage", page.toString())
        require(!filled.contains("{{") && !filled.contains("}}")) { "仅支持 {{key}}/{{page}}，不支持 JavaScript 模板表达式" }
        return filled
    }
    val url = resolveUrlV36(source.baseUrl, fill(urlPart))
    val method = options?.get("method")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
    val body = options?.get("body")?.jsonPrimitive?.contentOrNull?.let(::fill)
    require(method in setOf("GET", "POST", "HEAD")) { "书源仅支持 GET、POST、HEAD 请求" }
    publicSourceUrlV36(url)
    return SourceRequestV36(url, method, body, charset)
}

internal fun resolveUrlV36(base: String, target: String): String {
    val clean = target.trim()
    if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
    return runCatching { URL(URL(base), clean).toString() }.getOrDefault(clean)
}

/** Static HTTP boundary for sources, covers and imports. Explicit browser mode is routed separately. */
internal const val MAX_SOURCE_BYTES_V36 = 4 * 1024 * 1024

internal fun publicSourceUrlV36(raw: String): HttpUrl {
    require(raw.length <= 8192) { "网址过长" }
    val url = raw.trim().toHttpUrl()
    require(url.username.isEmpty() && url.password.isEmpty()) { "网址不能包含登录凭据" }
    val host = url.host.lowercase().trimEnd('.')
    require(host != "localhost" && !host.endsWith(".localhost") &&
        !host.endsWith(".local") && !host.endsWith(".internal") &&
        !host.endsWith(".lan") && !host.endsWith(".home")) { "不允许访问本机或内网书源" }
    // Numeric literals are rejected here as well as in DNS, so imports fail early.
    if (host.contains(':') || host.all { it.isDigit() || it == '.' }) {
        require(connectableSourceAddressV54(InetAddress.getByName(host))) { "不允许访问本机或内网书源" }
    }
    return url.newBuilder().fragment(null).build()
}

internal fun publicSourceAddressV36(address: InetAddress): Boolean {
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress) return false
    val b = address.address.map { it.toInt() and 255 }
    if (b.size == 4) {
        return !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 ||
            (b[0] == 100 && b[1] in 64..127) || (b[0] == 169 && b[1] == 254) ||
            (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
            (b[0] == 192 && b[1] == 0) || (b[0] == 192 && b[1] == 88 && b[2] == 99) ||
            (b[0] == 198 && b[1] in 18..19) || (b[0] == 198 && b[1] == 51 && b[2] == 100) ||
            (b[0] == 203 && b[1] == 0 && b[2] == 113))
    }
    // Only global unicast IPv6; exclude transition tunnels and documentation addresses.
    return b.size == 16 && (b[0] and 0xe0) == 0x20 &&
        !(b[0] == 0x20 && b[1] == 0x02) &&
        !(b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) &&
        !(b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0d && b[3] == 0xb8)
}

internal fun sameSourceOriginV36(a: HttpUrl, b: HttpUrl): Boolean =
    a.scheme == b.scheme && a.host == b.host && a.port == b.port

/** Never forward a configured API key/cookie (including custom header names) across origins. */
internal fun sourceHeadersForUrlV36(source: BookSourceV36?, url: HttpUrl): Map<String, String> {
    if (source == null || !sameSourceOriginV36(publicSourceUrlV36(source.baseUrl), url)) return emptyMap()
    return source.headers.filter { (name, value) ->
        require(!name.contains('\r') && !name.contains('\n') && !value.contains('\r') && !value.contains('\n')) { "书源请求头无效" }
        name.lowercase() !in setOf("host", "content-length", "connection", "transfer-encoding", "proxy-authorization")
    }
}

internal fun sourceRedirectV36(from: HttpUrl, location: String, code: Int, method: String): HttpUrl {
    val next = publicSourceUrlV36(resolveUrlV36(from.toString(), location))
    require(!(from.isHttps && !next.isHttps)) { "已阻止 HTTPS 降级跳转" }
    require(method != "POST" || code !in setOf(307, 308) || sameSourceOriginV36(from, next)) { "已阻止跨站转发搜索表单" }
    return next
}

private val sourceClientV36 by lazy {
    OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .dns(checkedSourceDnsV54())
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).build()
}

internal fun readSourceBytesV36(input: InputStream, maxBytes: Int): ByteArray {
    require(maxBytes in 1..MAX_SOURCE_BYTES_V36)
    val out = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("书源请求已取消")
        val count = input.read(buffer)
        if (count < 0) break
        require(count <= maxBytes - out.size()) { "书源响应超过大小限制" }
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

private data class SourceResponseV36(
    val code: Int, val url: String, val location: String?, val type: String?, val bytes: ByteArray,
    val cookies: List<Cookie>, val mitigationHeader: String?, val retryAfter: String?,
)

internal const val MAX_SOURCE_ERROR_BYTES_V44 = 8 * 1024

/** Read only a bounded diagnostic prefix. A huge error page must not hide its HTTP status. */
internal fun readSourceErrorPrefixV44(input: InputStream, maxBytes: Int = MAX_SOURCE_ERROR_BYTES_V44): ByteArray {
    require(maxBytes in 1..MAX_SOURCE_ERROR_BYTES_V44)
    val out = ByteArrayOutputStream(maxBytes)
    val buffer = ByteArray(minOf(maxBytes, 1024))
    while (out.size() < maxBytes) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("书源请求已取消")
        val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
        if (count < 0) break
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

internal class SourceHttpStatusExceptionV44(val statusCode: Int, val origin: String, val detail: String,
    val retryAfterMillis: Long? = null, val browserChallenge: Boolean = false) :
    IOException("网站返回 HTTP $statusCode（$origin）：$detail")

internal class SourceBrowserChallengeV46(val origin: String) : IOException("网站要求浏览器验证，尚未取得页面内容；本轮已停止访问该站")

/** Only verified status/evidence is shown. Never echo cookies, query strings or an error page. */
internal fun sourceHttpFailureV44(
    code: Int, url: HttpUrl, bytes: ByteArray, mitigationHeader: String? = null, retryAfter: String? = null,
): SourceHttpStatusExceptionV44 {
    val prefix = String(bytes, 0, minOf(bytes.size, MAX_SOURCE_ERROR_BYTES_V44), Charsets.UTF_8)
    val errorText = Jsoup.parse(prefix).text().lowercase()
    val requiresHttps = code == 400 && !url.isHttps && listOf(
        "plain http request was sent to https port", "this combination of host and port requires tls",
        "the http request was sent to https port",
    ).any(errorText::contains)
    val challenge = browserChallengePendingV38(prefix, mitigationHeader)
    val retryMillis = sourceRetryAfterMillisV46(retryAfter)
    val detail = when {
        code == 429 -> "网站限制了请求频率，本轮已停止访问" +
            (retryMillis?.let { "；请至少等待 ${((it + 999) / 1000).coerceAtLeast(1)} 秒后再试" } ?: "；请稍后再试") +
            if (challenge) "；响应中同时检测到浏览器验证页面" else ""
        requiresHttps -> "服务器明确要求 HTTPS，请改用该站 HTTPS 地址重试"
        challenge -> "网站返回了浏览器验证或访问拦截页面，请先在浏览器中确认访问状态"
        code == 400 -> "服务器未接受该请求" + if (!url.isHttps && url.port == 80) "；当前使用 HTTP，可尝试该站 HTTPS 地址" else "；请检查网站地址"
        code == 401 -> "网站要求身份验证"
        code == 403 -> "网站拒绝访问，请先在浏览器中确认该网址能否打开"
        code == 404 || code == 410 -> "该页面不存在或已移除，请检查网站地址"
        code in 500..599 -> "网站服务暂时出错，请稍后再试"
        else -> "未能取得页面，请在浏览器中确认该网址能否打开"
    }
    val origin = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")
    return SourceHttpStatusExceptionV44(code, origin, detail, retryMillis, challenge)
}

// Waiting is interruptible. runInterruptible in callers cancels the actual socket, not just UI state.
private fun awaitSourceResponseV36(request: Request, maxBytes: Int, remainingMs: Long): SourceResponseV36 {
    val call = sourceClientV36.newCall(request)
    call.timeout().timeout(remainingMs, TimeUnit.MILLISECONDS)
    val latch = CountDownLatch(1)
    var result: SourceResponseV36? = null
    var failure: Throwable? = null
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { failure = e; latch.countDown() }
        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    val body = it.body
                    val bytes = if (it.code in 200..299 && body != null) {
                        require(body.contentLength() <= maxBytes) { "书源响应超过大小限制" }
                        body.byteStream().use { stream -> readSourceBytesV36(stream, maxBytes) }
                    } else if (it.code >= 400 && body != null) {
                        // A broken diagnostic body must not replace a known HTTP status with an
                        // unrelated stream error. Cancellation still propagates to the caller.
                        try {
                            body.byteStream().use { stream -> readSourceErrorPrefixV44(stream, minOf(maxBytes, MAX_SOURCE_ERROR_BYTES_V44)) }
                        } catch (_: IOException) { ByteArray(0) }
                    } else ByteArray(0)
                    result = SourceResponseV36(it.code, it.request.url.toString(), it.header("Location"), it.header("Content-Type"), bytes, Cookie.parseAll(it.request.url, it.headers), it.header("cf-mitigated"), it.header("Retry-After"))
                }
            } catch (e: Exception) { failure = e } finally { latch.countDown() }
        }
    })
    try {
        if (!latch.await(remainingMs, TimeUnit.MILLISECONDS)) throw java.net.SocketTimeoutException("书源请求超时")
        failure?.let { throw it }
        return result ?: error("书源未返回响应")
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CancellationException("书源请求已取消").apply { initCause(e) }
    } finally { call.cancel() }
}

private fun fetchSourceResponseV36(source: BookSourceV36?, initial: SourceRequestV36, maxBytes: Int): SourceResponseV36 {
    require(maxBytes in 1..MAX_SOURCE_BYTES_V36)
    var url = publicSourceUrlV36(initial.url)
    var method = initial.method.uppercase()
    var body = initial.body
    require(method in setOf("GET", "HEAD", "POST")) { "不支持的书源请求方法" }
    require(body == null || body!!.length <= 64 * 1024) { "搜索请求过大" }
    val visited = HashSet<String>()
    val routeHosts = ArrayList<String>()
    val cookies = ArrayList<Cookie>()
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
    repeat(8) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("书源请求已取消")
        check(visited.add(url.toString())) { "书源跳转形成循环" }
        if (routeHosts.lastOrNull() != url.host) routeHosts += url.host
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        if (remaining <= 0) throw java.net.SocketTimeoutException("书源请求超时")
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/json,image/*;q=0.8,*/*;q=0.5")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
        // All configured headers (not merely known credential names) stay on the configured origin.
        // No global CookieManager or automatic cookie jar is used by source requests.
        val configuredHeaders = sourceHeadersForUrlV36(source, url)
        configuredHeaders.forEach { (name, value) -> request.header(name, value) }
        val configuredCookie = configuredHeaders.entries.firstOrNull { it.key.equals("Cookie", true) }?.value
        val learnedCookies = cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }.joinToString("; ") { "${it.name}=${it.value}" }
        listOfNotNull(configuredCookie, learnedCookies.takeIf { it.isNotEmpty() }).joinToString("; ")
            .takeIf { it.isNotEmpty() }?.let { request.header("Cookie", it) }
        val requestBody = if (method == "POST") body.orEmpty().toByteArray(Charset.forName(initial.charset ?: "UTF-8"))
            .toRequestBody("application/x-www-form-urlencoded".toMediaType()) else null
        SourceCooldownV46.check(url)
        val response = try {
            awaitSourceResponseV36(request.method(method, requestBody).build(), maxBytes, remaining)
        } catch (error: Exception) {
            throw sourceDnsWithRouteV55(error, routeHosts)
        }
        response.cookies.forEach { cookie ->
            cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > System.currentTimeMillis()) cookies.add(cookie)
        }
        check(cookies.size <= 100) { "书源返回了过多 Cookie" }
        if (response.code in setOf(301, 302, 303, 307, 308)) {
            val next = sourceRedirectV36(url, response.location ?: error("跳转没有目标地址"), response.code, method)
            if (response.code in setOf(301, 302, 303) && method == "POST") { method = "GET"; body = null }
            url = next
        } else {
            if (response.code !in 200..299) {
                val failure = sourceHttpFailureV44(response.code, url, response.bytes, response.mitigationHeader, response.retryAfter)
                SourceCooldownV46.record(failure)
                throw failure
            }
            return response
        }
    }
    error("网页跳转次数过多")
}

internal fun fetchSourceBytesV36(url: String, maxBytes: Int = MAX_SOURCE_BYTES_V36): ByteArray =
    fetchSourceResponseV36(null, SourceRequestV36(url), maxBytes).bytes

internal fun fetchDocumentV36(source: BookSourceV36, request: SourceRequestV36): Document {
    if (source.useBrowser) return BookSourceBrowserV38.document(source, request)
    val response = fetchSourceResponseV36(source, request, MAX_SOURCE_BYTES_V36)
    return parseSourceDocumentV44(response.bytes, response.url, request.charset, response.type, response.mitigationHeader)
}

internal fun parseSourceDocumentV44(
    bytes: ByteArray, url: String, requestedCharset: String? = null, type: String? = null, mitigationHeader: String? = null,
): Document {
    require(bytes.size <= MAX_SOURCE_BYTES_V36) { "书源响应超过大小限制" }
    val charset = requestedCharset ?: type?.let { Regex("charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        ?: sniffCharsetV36(bytes)
    val html = String(bytes, runCatching { Charset.forName(charset) }.getOrDefault(Charsets.UTF_8))
    if (browserChallengePendingV38(html, mitigationHeader)) throw SourceBrowserChallengeV46(sourceOriginV46(publicSourceUrlV36(url)))
    return Jsoup.parse(html, url)
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

internal fun searchSourceV36(
    source: BookSourceV36,
    key: String,
    page: Int = 1,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): List<OnlineBookV36> {
    val doc = fetchDocument(source, buildSearchRequestV36(source, key, page))
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
internal fun loadBookV36(
    source: BookSourceV36,
    book: OnlineBookV36,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): Pair<OnlineBookV36, List<OnlineChapterV36>> = loadBookCatalogueV50(source, book, fetchDocument).let { it.book to it.chapters }

internal fun loadBookCatalogueV50(
    source: BookSourceV36,
    book: OnlineBookV36,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): OnlineBookCatalogueV50 {
    if (Thread.currentThread().isInterrupted) throw CancellationException("目录读取已取消")
    val page = fetchDocument(source, SourceRequestV36(book.bookUrl))
    val detailed = book.copy(
        name = ruleStringV36(page, source.infoName).ifBlank { book.name },
        author = ruleStringV36(page, source.infoAuthor).ifBlank { book.author },
        cover = ruleStringV36(page, source.infoCover).ifBlank { book.cover }.let { if (it.isBlank()) it else resolveUrlV36(page.location(), it) },
        intro = ruleStringV36(page, source.infoIntro).ifBlank { book.intro },
    )
    val inspectors = HashMap<Document, CataloguePageInspectorV50>()
    fun inspect(doc: Document) = inspectors.getOrPut(doc) { CataloguePageInspectorV50(doc) }

    fun declaredChapters(doc: Document): List<OnlineChapterV36> {
        fun withInline(found: List<OnlineChapterV36>) = (found + catalogueInlineChapterLinksV51(doc, book.bookUrl)).distinctBy { it.url }
        val found = ruleElementsV36(doc, source.tocList).mapNotNull { item ->
            val title = ruleStringV36(item, source.tocName).ifBlank { item.text() }.trim()
            val url = ruleStringV36(item, source.tocUrl).ifBlank { item.selectFirst("a[href]")?.absUrl("href").orEmpty() }
            if (title.isNotBlank() && url.isNotBlank()) OnlineChapterV36(title, resolveUrlV36(doc.location(), url)) else null
        }.distinctBy { it.url }
        val observedFull = inspect(doc).fullChapterLinks(book.bookUrl)
        if (found.isEmpty()) return withInline(observedFull.ifEmpty { heuristicChapterLinksV39(doc, book.bookUrl) })
        val fullUrls = observedFull.map { it.url }.toHashSet()
        if (observedFull.size > found.size && found.all { it.url in fullUrls }) return withInline(observedFull)
        val evidence = inspect(doc).selection(found)
        // Compare with a real full-list candidate even when the declared rule returns a plausible
        // nonempty prefix. A working rule must never acquire recommendation/sidebar links.
        val alternative = heuristicChapterLinksV39(doc, book.bookUrl)
        if (alternative.size > found.size) {
            val other = inspect(doc).selection(alternative)
            val missingTotal = inspect(doc).declaredTotal(found)?.let { it > found.size } == true
            if (other.full || ((evidence.preview || evidence.weakPreview || missingTotal) && !other.preview && !other.weakPreview)) return withInline(alternative)
        }
        return withInline(found)
    }

    val onPageChapters = declaredChapters(page)
    val declaredToc = ruleStringV36(page, source.infoTocUrl).takeIf { it.isNotBlank() }?.let { resolveUrlV36(page.location(), it) }
    val explicitToc = heuristicTocUrlV39(page, explicitOnly = true)
    val heuristicToc = heuristicTocUrlV39(page)
    fun otherPage(url: String?): String? = url?.takeIf { catalogueCanonicalUrlV50(it) != catalogueCanonicalUrlV50(page.location()) }
    // A visible "完整目录" entry is stronger than a generated selector that picked a
    // latest-chapters widget. Never synthesize an unobserved URL.
    val fullToc = page.select("a[href]").firstNotNullOfOrNull { anchor ->
        if (!Regex("完整|全部|所有|全[书書]|all|full", RegexOption.IGNORE_CASE).containsMatchIn(anchor.text()) ||
            !catalogueFullLabelV50(anchor.text())) null
        else catalogueObservedUrlV50(page, anchor)?.takeUnless(::isLikelyChapterUrlV39)?.let(::otherPage)
    }
    val firstToc = fullToc ?: otherPage(declaredToc) ?: otherPage(explicitToc) ?: otherPage(heuristicToc).takeIf { onPageChapters.isEmpty() }
    fun read(url: String, message: String): Document {
        check(sameCatalogueOriginV50(page.location(), url)) { "目录入口不在当前网站，未继续读取" }
        if (Thread.currentThread().isInterrupted) throw CancellationException("目录读取已取消")
        return sourceAttemptV36 { fetchDocument(source, SourceRequestV36(url)) }.getOrElse { failure ->
            throw IllegalStateException("$message：${failure.message.orEmpty()}", failure)
        }.also { check(sameCatalogueOriginV50(page.location(), it.location())) { "目录跳转到站外，未将结果当作完整目录" } }
    }
    var doc = firstToc?.let { read(it, "完整目录读取失败，未将预览章节当作完整目录") } ?: page
    val firstCatalogueUrl = catalogueCanonicalUrlV50(doc.location())
    val chapters = LinkedHashMap<String, OnlineChapterV36>()
    val visited = LinkedHashSet<String>()
    val declaredTotals = LinkedHashSet<Int>()
    inspect(page).declaredTotal(onPageChapters)?.let(declaredTotals::add)
    var latestOrdinal = listOfNotNull(chapterOrdinalV50(book.latest), inspect(page).latestOrdinal(book.bookUrl)).maxOrNull()
    var unresolvedPreview = false
    var hasVolumeNumbers = inspect(page).hasVolumes()

    fun completed(): OnlineBookCatalogueV50 {
        val distinct = chapters.values.toList()
        val declaredTotal = declaredTotals.singleOrNull()
        check(declaredTotals.size <= 1) { "目录总章数在分页间不一致，尚未确认完整目录" }
        check(declaredTotal == null || distinct.size >= declaredTotal) {
            "目录不完整：页面声明 $declaredTotal 章，实际仅取得 ${distinct.size} 章；未将所取数量当作总章数"
        }
        val ordinals = distinct.mapNotNull { chapterOrdinalV50(it.title) }
        val maxOrdinal = ordinals.maxOrNull()
        // Volume-local numbering can restart. A chapter ordinal is not a chapter count.
        hasVolumeNumbers = hasVolumeNumbers || catalogueVolumeTitlesV53(distinct.map { it.title })
        val middleGap = catalogueMiddleGapV53(distinct.map { it.title }, hasVolumeNumbers)
        check(middleGap == null) {
            "目录存在大段缺章：第 ${middleGap!!.first} 章后跳到第 ${middleGap.second} 章；未把开头与最新章节拼接列表当作完整目录，请检查完整目录入口"
        }
        check(hasVolumeNumbers || latestOrdinal == null || maxOrdinal == null || maxOrdinal >= latestOrdinal!!) {
            "目录不完整：页面最新章节序号为 $latestOrdinal，所取目录仅到 $maxOrdinal；请检查完整目录入口"
        }
        check(!unresolvedPreview || declaredTotal == distinct.size) {
            "仅取得最新章节预览，尚未取得完整目录；请检查完整目录入口与规则，未将预览数量当作总章数"
        }
        val totalsMatch = declaredTotal != null && declaredTotal == distinct.size
        val warnings = buildList {
            if (declaredTotal != null && !totalsMatch) add("页面声明 $declaredTotal 章，实际取得 ${distinct.size} 条；条目数不一致，完整性未确认")
            if (!totalsMatch) add(if (visited.size > 1) "已沿静态分页读取到当前末端；没有独立总章数核对，不能据此确认全书完整"
                else "已解析当前页面的章节；目录标签与当页条目自比不能证明全书完整，尚无独立总章数核对")
        }
        return OnlineBookCatalogueV50(detailed, distinct, SourceCatalogueProofV50(
            tocUrl = firstCatalogueUrl,
            pagesRead = visited.size,
            declaredTotal = declaredTotal,
            latestOrdinal = latestOrdinal,
            warnings = warnings,
            hasCompletenessEvidence = distinct.isNotEmpty() && totalsMatch,
            evidence = when {
                distinct.isNotEmpty() && totalsMatch -> SourceCatalogueEvidenceV51.MATCHED_DECLARED_TOTAL
                visited.size > 1 -> SourceCatalogueEvidenceV51.STATIC_PAGINATION_END
                else -> SourceCatalogueEvidenceV51.PARSED_ONLY
            },
        ))
    }

    repeat(40) { pageIndex ->
        if (Thread.currentThread().isInterrupted) throw CancellationException("目录读取已取消")
        val currentUrl = catalogueCanonicalUrlV50(doc.location())
        check(visited.add(currentUrl)) { "目录分页循环返回已读取页面，未返回不完整目录" }
        val found = if (doc === page) onPageChapters else declaredChapters(doc)
        val inspector = inspect(doc)
        val section = inspector.selection(found)
        val previousSize = chapters.size
        found.forEach { chapters.putIfAbsent(it.url, it) }
        check(chapters.size <= 50_000) { "目录超过 50000 章限制" }
        check(pageIndex == 0 || chapters.size > previousSize) { "目录分页没有新增章节，未返回不完整目录" }
        inspector.declaredTotal(found)?.let(declaredTotals::add)
        latestOrdinal = listOfNotNull(latestOrdinal, inspector.latestOrdinal(book.bookUrl)).maxOrNull()
        hasVolumeNumbers = hasVolumeNumbers || inspector.hasVolumes()
        val numbers = found.mapNotNull { chapterOrdinalV50(it.title) }
        val weakPreviewGap = section.weakPreview && numbers.maxOrNull()?.let { it > found.size && numbers.minOrNull() != 1 } == true
        unresolvedPreview = unresolvedPreview || section.preview || weakPreviewGap
        val ruleNext = ruleStringV36(doc, source.tocNext)
        val navigation = catalogueNavigationV50(doc, ruleNext, book.bookUrl)
        check(!navigation.unresolved) { "页面仍有更多章节或目录分页，但没有可读取的静态同站链接；未将当前列表当作完整目录" }
        val next = navigation.next
        if (next == null) return completed()
        check(catalogueCanonicalUrlV50(next) !in visited) { "目录分页循环返回已读取页面，未返回不完整目录" }
        check(pageIndex < 39) { "目录超过 40 页限制，未返回不完整目录" }
        doc = read(next, "目录分页读取失败，未返回不完整目录")
    }
    error("目录超过 40 页限制，未返回不完整目录")
}

/**
 * Finds a separate catalogue entry on a book page when the configured infoTocUrl no longer works.
 * Text labels are deliberately multilingual because many imported sources use simplified/traditional
 * Chinese interchangeably.
 */
internal fun heuristicTocUrlV39(doc: Document, explicitOnly: Boolean = false): String? {
    val labels = listOf("全部章节", "全部章節", "章节目录", "章節目錄", "目录", "目錄", "查看目录", "查看目錄")
    val candidates = doc.select("a[href]").mapNotNull { a ->
        val text = a.text().replace("\\s+".toRegex(), "").trim()
        val href = a.absUrl("href").ifBlank { resolveUrlV36(doc.location(), a.attr("href")) }
        if (href.isBlank() || !sameCatalogueOriginV50(doc.location(), href) || catalogueCanonicalUrlV50(href) == catalogueCanonicalUrlV50(doc.location()) || isLikelyChapterUrlV39(href)) return@mapNotNull null
        val labelScore = labels.indexOfFirst { text.equals(it, true) }.let { if (it >= 0) 100 - it else 0 }
        val containsScore = if (labels.any { text.contains(it, true) }) 45 else 0
        if (explicitOnly && maxOf(labelScore, containsScore) == 0) return@mapNotNull null
        val path = runCatching { URL(href).path }.getOrDefault("")
        val urlScore = if (Regex("(?i)(catalog|chapter|list|dir|menu)").containsMatchIn(path)) 18 else 0
        val score = maxOf(labelScore, containsScore) + urlScore
        if (score <= 0) null else score to href
    }
    return candidates.maxByOrNull { it.first }?.second
}

/**
 * Extracts chapter links without a configured toc rule. It first chooses the most chapter-dense
 * DOM container so "latest chapters" widgets do not jump ahead of the actual full catalogue.
 */
internal fun heuristicChapterLinksV39(doc: Document, bookUrl: String = ""): List<OnlineChapterV36> {
    val all = doc.select("a[href]").mapNotNull { a ->
        val href = a.absUrl("href").ifBlank { resolveUrlV36(doc.location(), a.attr("href")) }
        val title = a.text().replace("\\s+".toRegex(), " ").trim()
        if (!isLikelyChapterLinkV39(title, href, bookUrl)) null else a to OnlineChapterV36(title, href)
    }
    if (all.isEmpty()) return emptyList()

    // Group links by their nearest five ancestors once. The previous implementation rebuilt
    // each ancestor's full descendant list for every link, becoming quadratic/cubic on large TOCs.
    val inspector = CataloguePageInspectorV50(doc)
    val candidates = LinkedHashMap<Element, MutableList<OnlineChapterV36>>()
    all.forEach { (anchor, chapter) ->
        var parent: Element? = anchor.parent()
        repeat(10) {
            val node = parent ?: return@repeat
            candidates.getOrPut(node) { ArrayList() }.add(chapter)
            parent = node.parent()
        }
    }

    val best = candidates.mapNotNull { (container, matches) ->
        val links = matches.distinctBy { it.url }
        if (links.size < 2) return@mapNotNull null
        val totalAnchors = container.select("a[href]").size.coerceAtLeast(links.size)
        val density = links.size.toDouble() / totalAnchors.toDouble()
        var depth = 0
        var p: Element? = container
        while (depth < 20) {
            val parent = p?.parent() ?: break
            depth++
            p = parent
        }
        val hint = (container.id() + " " + container.className() + " " + container.tagName()).lowercase()
        val semanticBonus = when {
            listOf("chapter", "catalog", "toc", "directory", "list", "目录", "目錄").any(hint::contains) -> 500.0
            container.tagName() in setOf("ul", "ol") -> 180.0
            else -> 0.0
        }
        val broadPenalty = if (container.tagName() in setOf("html", "body", "main")) 400.0 else 0.0
        val section = inspector.section(container)
        val catalogueBonus = if (section.full) 2000.0 else if (section.preview || section.weakPreview) -2000.0 else 0.0
        val score = links.size.coerceAtMost(800) + density * 500.0 + depth * 35.0 + semanticBonus + catalogueBonus - broadPenalty
        Triple(score, container, links)
    }.maxByOrNull { it.first }

    val picked = best?.third ?: all.map { it.second }.distinctBy { it.url }
    return picked.filter { it.title.isNotBlank() }.distinctBy { it.url }
}

internal fun heuristicTocNextUrlV39(doc: Document): String? = catalogueNavigationV50(doc).next

internal fun isLikelyChapterUrlV39(url: String): Boolean {
    val clean = runCatching { URL(url).path }.getOrDefault(url)
    if (Regex("(?i)/(index|list|catalog|catalogue|toc|menu)(?:[_-]\\d+)?\\.html?$").containsMatchIn(clean)) return false
    return Regex("(?i)/(txt|read|chapter|chapters?)/[^/]+/[^/]+(?:\\.html?)?$").containsMatchIn(clean) ||
        Regex("(?i)/txt/\\d+/\\d+(?:[_-]\\d+)?\\.html?$").containsMatchIn(clean)
}

internal fun isLikelyChapterLinkV39(title: String, url: String, bookUrl: String): Boolean {
    if (title.isBlank() || url.isBlank() || url == bookUrl) return false
    if (bookUrl.isNotBlank() && !sameCatalogueOriginV50(bookUrl, url)) return false
    val bookPath = runCatching { URL(bookUrl).path }.getOrDefault("")
    val chapterPath = runCatching { URL(url).path }.getOrDefault("")
    val bookKey = Regex("(?i)/(?:book|novel|txt)/(\\d+)(?:\\.html?)?/?$").find(bookPath)?.groupValues?.get(1)
    val chapterKey = Regex("(?i)/(?:txt|read|chapters?)/(\\d+)/").find(chapterPath)?.groupValues?.get(1)
    if (bookKey != null && chapterKey != null && bookKey != chapterKey) return false
    if (isLikelyChapterUrlV39(url)) return true
    val chapterTitle = Regex(
        "^(第.{1,16}[章节章節回卷]|番外|楔子|序章|序言|后记|後記|尾声|尾聲|chapter\\s*\\d+)",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(title.trim())
    if (!chapterTitle) return false
    val path = runCatching { URL(url).path.lowercase() }.getOrDefault(url.lowercase())
    return listOf("/read/", "/chapter/", "/chapters/", "/txt/", ".html").any(path::contains)
}

/** Chapter body; follows "next page" links that stay inside the same chapter. */
internal fun loadChapterTextV36(
    source: BookSourceV36,
    chapter: OnlineChapterV36,
    tocUrls: Set<String>,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): String = loadChapterAuditV50(source, null, chapter, tocUrls, fetchDocument).text

/** Legado replaceRegex: "##regex##replacement", possibly several joined; plain text is a regex. */
internal fun cleanContentV36(text: String, replace: String): String {
    if (replace.isBlank()) return text
    val body = replace.removePrefix("##")
    val parts = body.split("##")
    return applyReplaceV36(text, parts.getOrNull(0), parts.getOrNull(1).orEmpty())
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

/** Unlike runCatching, never turn cancellation into a successful empty result. */
internal inline fun <T> sourceAttemptV36(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) { throw e } catch (e: InterruptedException) {
    Thread.currentThread().interrupt()
    throw CancellationException("书源操作已取消").apply { initCause(e) }
} catch (e: Exception) { Result.failure(e) }

/** Import never silently replaces an existing source or a prior duplicate in the same batch. */
internal fun mergeSourceImportsV36(existing: List<BookSourceV36>, incoming: BookSourceImportResultV36): BookSourceImportResultV36 {
    val merged = existing.associateBy { it.id }.toMutableMap()
    val skipped = incoming.skipped.toMutableList()
    incoming.sources.forEach { source ->
        if (merged.containsKey(source.id)) skipped += "${source.name}（ID 已存在，保留原书源；如需修改请使用编辑）"
        else merged[source.id] = source
    }
    return BookSourceImportResultV36(merged.values.sortedBy { it.name }, skipped, incoming.warnings)
}
