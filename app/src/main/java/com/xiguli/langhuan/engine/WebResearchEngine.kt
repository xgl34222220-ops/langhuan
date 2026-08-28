package com.xiguli.langhuan.engine

import android.text.Html
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Lightweight public-web research for creation conversations. No search API key required. */
data class WebResearchSource(
    val title: String,
    val url: String,
    val snippet: String,
)

data class WebResearchResult(
    val query: String,
    val sources: List<WebResearchSource>,
    val engine: String,
) {
    val context: String get() = sources.joinToString("\n") { source ->
        "- ${source.title}\n  ${source.snippet}\n  来源：${source.url}"
    }
}

data class ReferenceResearchGroup(
    val target: String,
    val result: WebResearchResult,
)

data class CreationResearchBundle(
    val originalText: String,
    val groups: List<ReferenceResearchGroup>,
) {
    val sources: List<WebResearchSource>
        get() = groups.flatMap { it.result.sources }.distinctBy { it.url }

    val hasSources: Boolean get() = sources.isNotEmpty()

    val context: String get() = groups.joinToString("\n\n") { group ->
        buildString {
            appendLine("【参考对象：${group.target}】")
            appendLine("检索词：${group.result.query}")
            if (group.result.sources.isEmpty()) {
                append("本次未检索到足够可靠的公开结果。")
            } else {
                append(group.result.context)
            }
        }
    }
}

class WebResearchEngine {
    suspend fun search(userText: String, limit: Int = 6): WebResearchResult {
        val query = normalizeQuery(userText)
        return searchQuery(query, limit)
    }

    /**
     * 新书会谈专用研究入口。
     * 会优先拆出《作品名》、"某某的小说/作品"等参考对象，分别检索，避免多本作品混在一个 query 里。
     */
    suspend fun researchForCreation(userText: String, perTargetLimit: Int = 5): CreationResearchBundle = coroutineScope {
        val targets = referenceTargets(userText)
        val effectiveTargets = if (targets.isEmpty()) listOf(normalizeQuery(userText)) else targets
        val groups = effectiveTargets
            .filter { it.isNotBlank() }
            .take(6)
            .map { target ->
                async(Dispatchers.IO) {
                    ReferenceResearchGroup(target, searchReferenceTarget(target, perTargetLimit))
                }
            }
            .awaitAll()
        CreationResearchBundle(userText, groups)
    }

    fun shouldResearch(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false

        // 只要明确提到书名，就默认允许琅嬛先查公开资料，而不是赌模型训练数据里是否刚好有。
        if (Regex("《[^》]{1,60}》").containsMatchIn(text)) return true

        val direct = listOf(
            "搜一下", "搜索", "查一下", "查查", "联网", "资料", "作品有哪些", "有哪些小说", "哪几本小说",
            "参考", "借鉴", "融合", "结合", "混合", "揉在一起", "取长补短", "类似", "像这本", "这种小说",
        )
        if (direct.any(value::contains)) return true

        if (("你知道" in value || "了解" in value || "听说过" in value) &&
            ("作者" in value || "小说" in value || "作品" in value || "书" in value)) return true

        // “薄情书生的小说”“某作者的作品”这类不要求用户必须先说“搜”。
        if (Regex("[\\p{L}\\p{N}·_]{2,24}(?:的)?(?:小说|作品|网文|书)").containsMatchIn(text)) return true
        return false
    }

    /** 解析一次会谈里用户想参考的多本小说/作者。 */
    fun referenceTargets(text: String): List<String> {
        val result = linkedSetOf<String>()

        Regex("《([^》]{1,60})》").findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let(result::add)
        }

        // 兼容“搜一下薄情书生的小说 / 参考紫金陈的作品”。避免把整句话丢进搜索引擎。
        Regex("(?:搜一下|搜索|查一下|查查|参考|借鉴|了解|看看)?\\s*([\\p{L}\\p{N}·_]{2,24})的(?:小说|作品|网文|书)")
            .findAll(text)
            .forEach { match ->
                match.groupValues.getOrNull(1)
                    ?.trim()
                    ?.trimStart('一', '下')
                    ?.takeIf { candidate ->
                        candidate.isNotBlank() && candidate !in setOf("作者", "这个", "那个", "好几本", "几本")
                    }
                    ?.let(result::add)
            }

        return result.take(6)
    }

    private suspend fun searchReferenceTarget(target: String, limit: Int): WebResearchResult {
        val cleanTarget = target.trim().trim('《', '》', '“', '”', '"')
        if (cleanTarget.isBlank()) return WebResearchResult("", emptyList(), "none")

        val primary = searchQuery("\"$cleanTarget\" 小说 作品 剧情 世界观 设定", limit)
        if (primary.sources.size >= 2) return primary

        val secondary = searchQuery("\"$cleanTarget\" 作者 小说 简介", limit)
        val merged = (primary.sources + secondary.sources).distinctBy { it.url }.take(limit)
        return WebResearchResult(
            query = primary.query + " / " + secondary.query,
            sources = merged,
            engine = listOf(primary.engine, secondary.engine).filter { it != "unavailable" && it != "none" }.distinct().joinToString(" + ").ifBlank { "unavailable" },
        )
    }

    private suspend fun searchQuery(query: String, limit: Int): WebResearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext WebResearchResult("", emptyList(), "none")

        val bing = runCatching { searchBingRss(query, limit) }.getOrDefault(emptyList())
        if (bing.isNotEmpty()) return@withContext WebResearchResult(query, bing, "Bing RSS")

        val duck = runCatching { searchDuckDuckGo(query, limit) }.getOrDefault(emptyList())
        WebResearchResult(query, duck, if (duck.isEmpty()) "unavailable" else "DuckDuckGo")
    }

    private fun normalizeQuery(text: String): String {
        var q = text.trim()
            .replace(Regex("^(你)?(去)?(帮我)?(联网)?(搜一下|搜索一下|搜索|查一下|查查)[:：,，\\s]*"), "")
            .replace(Regex("[？?！!]$"), "")
            .trim()
        if (q.endsWith("的小说")) q += " 作品"
        if (q.length > 180) q = q.take(180)
        return q
    }

    private fun searchBingRss(query: String, limit: Int): List<WebResearchSource> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://www.bing.com/search?q=$encoded&format=rss&count=${limit.coerceIn(3, 10)}")
        val itemRegex = Regex("<item>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return itemRegex.findAll(body).mapNotNull { match ->
            val item = match.groupValues[1]
            val title = xmlValue(item, "title")
            val url = xmlValue(item, "link")
            val description = xmlValue(item, "description")
            if (title.isBlank() || url.isBlank()) null else WebResearchSource(
                title = clean(title).take(160),
                url = clean(url),
                snippet = clean(description).take(700),
            )
        }.distinctBy { it.url }.take(limit).toList()
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<WebResearchSource> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://html.duckduckgo.com/html/?q=$encoded")
        val blockRegex = Regex(
            "<a[^>]+class=\\\"result__a\\\"[^>]+href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>.*?(?:class=\\\"result__snippet\\\"[^>]*>(.*?)</(?:a|div|span)>)",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        return blockRegex.findAll(body).mapNotNull { match ->
            val url = decodeDuckUrl(clean(match.groupValues[1]))
            val title = clean(match.groupValues[2])
            val snippet = clean(match.groupValues[3])
            if (title.isBlank() || url.isBlank()) null else WebResearchSource(title.take(160), url, snippet.take(700))
        }.distinctBy { it.url }.take(limit).toList()
    }

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36 Langhuan/0.15")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            val status = connection.responseCode
            if (status !in 200..299) error("搜索服务返回 $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun xmlValue(text: String, tag: String): String {
        val raw = Regex("<$tag>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        return raw.removePrefix("<![CDATA[").removeSuffix("]]>")
    }

    @Suppress("DEPRECATION")
    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun decodeDuckUrl(url: String): String {
        if (!url.contains("uddg=")) return url
        val encoded = url.substringAfter("uddg=").substringBefore('&')
        return runCatching { java.net.URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(url)
    }
}
