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

/** Static Legado categories only. Unsupported entries are reported, never executed or silently lost. */
internal fun sourceDiscoveryCatalogV41(source: BookSourceV36): SourceDiscoveryCatalogV41 {
    if (!source.enabled || !source.enabledExplore || source.exploreUrl.isBlank()) return SourceDiscoveryCatalogV41(emptyList(), emptyList())
    val issues = ArrayList<String>()
    val raw = source.exploreUrl.trim()
    if (raw.length > 8192) return SourceDiscoveryCatalogV41(emptyList(), listOf("exploreUrl 超过 8192 字符限制"))
    if (raw.contains("@js:", true) || raw.contains("<js>", true) || raw.startsWith("{{") || raw.contains("java.")) {
        return SourceDiscoveryCatalogV41(emptyList(), listOf("exploreUrl 依赖动态 JavaScript，暂不支持；请改为静态分类地址"))
    }
    fun entry(label: String, rawUrl: String): SourceDiscoveryV41? = runCatching {
        val template = rawUrl.trim()
        require(template.isNotBlank()) { "分类地址为空" }
        require(!template.replace("{{page}}", "1").contains("{{")) { "仅支持 {{page}} 分页，不支持脚本表达式" }
        val optionsAt = template.indexOf(",{")
        if (optionsAt > 0) {
            val options = parseSourceOptionsV94(template.substring(optionsAt + 1))
            require(options.keys.all { it in setOf("method", "body", "charset") }) { "请求包含不支持的选项：${options.keys - setOf("method", "body", "charset")}" }
        }
        val request = buildSearchRequestV36(source.copy(searchUrl = template), "", 1)
        SourceDiscoveryV41(source.id, label.trim().take(40).ifBlank { "发现" }, publicSourceUrlV36(request.url).toString(), template)
    }.getOrElse { error ->
        issues += "${label.ifBlank { "发现" }}：${error.message.orEmpty()}"
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
    val hasMore = books.isNotEmpty() && (observedNext != null || section.template.contains("{{page}}"))
    return SourceDiscoveryPageV41(books, observedNext.takeIf { books.isNotEmpty() }, hasMore)
}

internal fun discoverBooksV41(source: BookSourceV36, section: SourceDiscoveryV41): List<OnlineBookV36> =
    discoverPageV41(source, section).books
