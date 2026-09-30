package com.xiguli.langhuan.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A literal, user-provided discovery URL. Script-generated categories are never evaluated. */
internal data class SourceDiscoveryV41(val sourceId: String, val label: String, val url: String)

internal fun sourceDiscoveriesV41(source: BookSourceV36): List<SourceDiscoveryV41> {
    if (!source.enabled || !source.enabledExplore || source.exploreUrl.isBlank()) return emptyList()
    val raw = source.exploreUrl.trim()
    if (raw.length > 16384 || raw.contains("@js:", true) || raw.contains("<js>", true)) return emptyList()
    fun entry(label: String, rawUrl: String): SourceDiscoveryV41? = runCatching {
        val url = rawUrl.trim().replace("{{page}}", "1")
        require(!url.contains("{{") && url.isNotBlank())
        SourceDiscoveryV41(source.id, label.trim().take(40).ifBlank { "发现" }, publicSourceUrlV36(resolveUrlV36(source.baseUrl, url)).toString())
    }.getOrNull()
    val sections = if (raw.startsWith("[")) {
        val array = runCatching { BookSourceJsonV36.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyList()
        array.take(30).mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val label = (obj["title"] as? JsonPrimitive)?.contentOrNull ?: (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            entry(label, (obj["url"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
    } else {
        raw.split("&&", "\n").take(30).mapNotNull { part ->
            val fields = part.split("::", limit = 2)
            if (fields.size == 2) entry(fields[0], fields[1]) else entry("发现", fields[0])
        }
    }
    return sections.distinctBy { it.url }
}

internal fun discoverBooksV41(source: BookSourceV36, section: SourceDiscoveryV41): List<OnlineBookV36> {
    require(sourceDiscoveriesV41(source).any { it == section }) { "发现分类已失效，请重新选择书源" }
    // Reuse the same bounded transport and declaration-only HTML parser as search.
    val discoverySource = source.copy(
        searchUrl = section.url,
        searchList = source.exploreList.ifBlank { source.searchList },
        searchName = source.exploreName.ifBlank { source.searchName },
        searchBookUrl = source.exploreBookUrl.ifBlank { source.searchBookUrl },
        searchAuthor = source.exploreAuthor.ifBlank { source.searchAuthor },
        searchCover = source.exploreCover.ifBlank { source.searchCover },
        searchIntro = source.exploreIntro.ifBlank { source.searchIntro },
        searchLatest = source.exploreLatest.ifBlank { source.searchLatest },
    )
    require(bookSourceSupportedV36(discoverySource)) { "发现规则包含暂不支持的脚本能力" }
    return searchSourceV36(discoverySource, "").take(300)
}
