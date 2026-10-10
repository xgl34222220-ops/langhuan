package com.xiguli.langhuan.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Document

/** Keep the declared template; expanding it while building tabs used to lock every category to page 1. */
internal data class SourceDiscoveryV41(
    val sourceId: String,
    val label: String,
    val url: String,
    val template: String = url,
)

internal data class SourceDiscoveryCatalogV41(val sections: List<SourceDiscoveryV41>, val issues: List<String>)
internal data class SourceDiscoveryPageV41(
    val books: List<OnlineBookV36>,
    /** Null with hasMore means expand the original request template, preserving POST body/charset. */
    val nextUrl: String?,
    val hasMore: Boolean = nextUrl != null,
)

/** Repeated sticky books do not end a category when HTML still points at a new page. */
internal fun discoveryPagingIssueV41(page: SourceDiscoveryPageV41, hasNew: Boolean, visitedUrls: Set<String>): String? = when {
    page.nextUrl != null && page.nextUrl in visitedUrls -> "分页链接出现循环，请检查规则或重试本页"
    page.hasMore && page.nextUrl == null && !hasNew -> "分页重复返回已有书籍，无法确认后续页；请检查模板或重试本页"
    else -> null
}

internal fun sourceDiscoveriesV41(source: BookSourceV36): List<SourceDiscoveryV41> = sourceDiscoveryCatalogV41(source).sections

private val discoveryScriptCacheV95 = object : LinkedHashMap<String, Result<String>>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result<String>>?): Boolean = size > 64
}
private val DISCOVERY_JS_V95 = Regex("<js>([\\s\\S]*?)</js>|@js:([\\s\\S]*)", RegexOption.IGNORE_CASE)

/** A scripted exploreUrl (`@js:` / `<js>`) evaluated offline into its category list; cached per source. */
private fun discoveryScriptV95(source: BookSourceV36, raw: String): Result<String> = synchronized(discoveryScriptCacheV95) {
    val key = source.id + "\u0000" + raw + "\u0000" + source.jsLib.hashCode()
    discoveryScriptCacheV95[key]?.let { return it }
    val result = runCatching {
        withSourceRuleScopeV95(SourceRuleScopeV95(source, allowNetwork = false)) {
            var value = ""
            var last = 0
            DISCOVERY_JS_V95.findAll(raw).forEach { match ->
                val before = raw.substring(last, match.range.first).trim()
                if (before.isNotEmpty()) value = before
                value = SourceJsEngineV95.evalStrict((match.groups[1]?.value ?: match.groups[2]?.value).orEmpty(), mapOf("result" to value), source.baseUrl)
                last = match.range.last + 1
            }
            raw.substring(last).trim().takeIf { it.isNotEmpty() }?.let { value = it }
            value.trim()
        }
    }
    discoveryScriptCacheV95[key] = result
    result
}

/** Legado categories: static lists, JSON arrays, or a script that returns either. Unusable entries are reported. */
internal fun sourceDiscoveryCatalogV41(source: BookSourceV36): SourceDiscoveryCatalogV41 {
    if (!source.enabled || !source.enabledExplore || source.exploreUrl.isBlank()) return SourceDiscoveryCatalogV41(emptyList(), emptyList())
    val issues = ArrayList<String>()
    var raw = source.exploreUrl.trim()
    if (raw.length > 64 * 1024) return SourceDiscoveryCatalogV41(emptyList(), listOf("exploreUrl 超过 64K 字符限制"))
    if (DISCOVERY_JS_V95.containsMatchIn(raw)) {
        sourceScriptUnsupportedV95(raw)?.let { return SourceDiscoveryCatalogV41(emptyList(), listOf("发现分类使用了$it")) }
        raw = discoveryScriptV95(source, raw).getOrElse { error ->
            val reason = if (sourceNeedsNetworkV95(error)) "发现分类脚本需要联网生成，暂不支持" else "发现分类脚本出错：${error.message.orEmpty().take(80)}"
            return SourceDiscoveryCatalogV41(emptyList(), listOf(reason))
        }
        if (raw.length > 64 * 1024) return SourceDiscoveryCatalogV41(emptyList(), listOf("发现分类脚本结果过长"))
    }
    fun entry(label: String, rawUrl: String): SourceDiscoveryV41? = runCatching {
        val template = rawUrl.trim()
        require(template.isNotBlank()) { "分类地址为空" }
        sourceRuleUnsupportedV94(template)?.let { throw IllegalArgumentException("分类地址使用了$it") }
        // Templates ({{page}}, {{(page-1)*20}}, <1,2>) are evaluated offline; a url that needs a request is kept as-is.
        val request = withSourceRuleScopeV95(SourceRuleScopeV95(source, allowNetwork = false)) {
            buildSearchRequestV36(source.copy(searchUrl = template), "", 1)
        }
        SourceDiscoveryV41(source.id, label.trim().take(40).ifBlank { "发现" }, publicSourceUrlV36(request.url).toString(), template)
    }.getOrElse { error ->
        issues += "${label.ifBlank { "发现" }}：${if (sourceNeedsNetworkV95(error)) "分类地址脚本需要联网生成，暂不支持" else error.message.orEmpty()}"
        null
    }
    val sections = if (raw.startsWith("[")) {
        val array = runCatching { BookSourceJsonV36.parseToJsonElement(raw) as? JsonArray }.getOrNull()
            ?: return SourceDiscoveryCatalogV41(emptyList(), listOf("exploreUrl 分类数组格式无效"))
        if (array.size > 60) return SourceDiscoveryCatalogV41(emptyList(), listOf("发现最多支持 60 个分类，请拆分书源"))
        array.mapNotNull { item ->
            val obj = item as? JsonObject
            if (obj == null) { issues += "分类条目必须是对象"; return@mapNotNull null }
            val dynamicFields = listOf("action", "viewName").filter { key ->
                val value = (obj[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
                value.isNotBlank() && !(key == "viewName" && value.length >= 2 && value.startsWith("'") && value.endsWith("'"))
            }
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (dynamicFields.isNotEmpty() || type !in setOf("", "url")) {
                issues += "分类条目依赖不支持的动态控件：${(dynamicFields + if (type !in setOf("", "url")) listOf("type=$type") else emptyList()).joinToString("、")}"
                return@mapNotNull null
            }
            if (obj["url"] != null && ((obj["url"] as? JsonPrimitive)?.isString != true)) {
                issues += "分类 url 必须是字符串"
                return@mapNotNull null
            }
            val label = (obj["title"] as? JsonPrimitive)?.contentOrNull ?: (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            // A title-only object is a Legado visual section heading, not a category to fetch.
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            // An empty object (title and url both blank) is a Legado layout spacer cell.
            if (url.isBlank()) null else entry(label, url)
        }
    } else {
        val parts = raw.split("&&", "\n").filter { it.isNotBlank() }
        if (parts.size > 60) return SourceDiscoveryCatalogV41(emptyList(), listOf("发现最多支持 60 个分类，请拆分书源"))
        parts.mapNotNull { part ->
            val fields = part.split("::", limit = 2)
            if (fields.size == 2) entry(fields[0], fields[1]) else entry("发现", fields[0])
        }
    }
    return SourceDiscoveryCatalogV41(sections.distinctBy { it.template }, issues)
}

internal fun discoveryRuleSourceV41(source: BookSourceV36, section: SourceDiscoveryV41): BookSourceV36 = source.copy(
    searchUrl = section.template,
    searchList = source.exploreList.ifBlank { source.searchList },
    searchName = source.exploreName.ifBlank { source.searchName },
    searchBookUrl = source.exploreBookUrl.ifBlank { source.searchBookUrl },
    searchAuthor = source.exploreAuthor.ifBlank { source.searchAuthor },
    searchCover = source.exploreCover.ifBlank { source.searchCover },
    searchIntro = source.exploreIntro.ifBlank { source.searchIntro },
    searchLatest = source.exploreLatest.ifBlank { source.searchLatest },
)

/** Only observed same-origin next links can extend a static category. */
internal fun discoveryNextUrlV41(doc: Document): String? {
    val origin = publicSourceUrlV36(doc.location())
    return doc.select("a[href]").firstNotNullOfOrNull { anchor ->
        val text = anchor.text().replace(Regex("\\s+"), "").lowercase()
        val isNext = anchor.attr("rel").split(' ').any { it.equals("next", true) } ||
            text in setOf("下一页", "下页", "下一頁", "下頁", "next", "nextpage", "下一页>", "下一页»")
        if (!isNext) return@firstNotNullOfOrNull null
        val url = runCatching { publicSourceUrlV36(anchor.absUrl("href")) }.getOrNull() ?: return@firstNotNullOfOrNull null
        url.toString().takeIf { sameSourceOriginV36(origin, url) && url != origin }
    }
}

internal fun discoverPageV41(
    source: BookSourceV36,
    section: SourceDiscoveryV41,
    page: Int = 1,
    pageUrl: String? = null,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): SourceDiscoveryPageV41 {
    require(page in 1..1000) { "发现分页已达到 1000 页限制，请缩小分类范围" }
    val catalog = sourceDiscoveryCatalogV41(source)
    require(catalog.sections.any { it == section }) { catalog.issues.firstOrNull() ?: "发现分类已失效，请重新选择书源" }
    val discoverySource = discoveryRuleSourceV41(source, section)
    require(bookSourceSupportedV36(discoverySource)) { "发现规则包含暂不支持的脚本能力" }
    var document: Document? = null
    val templatedRequest = buildSearchRequestV36(discoverySource, "", page)
    val requestSource = if (pageUrl == null || pageUrl == templatedRequest.url) discoverySource else {
        require(sameSourceOriginV36(publicSourceUrlV36(section.url), publicSourceUrlV36(pageUrl))) { "已阻止发现分页跨站跳转" }
        discoverySource.copy(searchUrl = pageUrl)
    }
    val books = searchSourceV36(requestSource, "", page) { src, request ->
        fetchDocument(src, request).also { document = it }
    }.distinctBy { it.bookUrl }
    require(books.size <= 2000) { "发现单页超过 2000 本限制，无法完整显示，请缩小分类范围" }
    val doc = requireNotNull(document)
    val observedNext = discoveryNextUrlV41(doc)
    val hasMore = books.isNotEmpty() && (observedNext != null || discoveryTemplatePagedV95(section.template))
    return SourceDiscoveryPageV41(books, observedNext.takeIf { books.isNotEmpty() }, hasMore)
}

/** `{{page}}`, a page expression such as `{{(page-1)*20}}`, or a `<1,2,3>` page list. */
internal fun discoveryTemplatePagedV95(template: String): Boolean =
    Regex("\\{\\{[^}]*\\bpage\\b").containsMatchIn(template) || Regex("<[^<>]*,[^<>]*>").containsMatchIn(template) ||
        template.contains("searchPage")

internal fun discoverBooksV41(source: BookSourceV36, section: SourceDiscoveryV41): List<OnlineBookV36> =
    discoverPageV41(source, section).books
