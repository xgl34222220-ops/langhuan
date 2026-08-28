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

/**
 * Secondary public-search path used only when the primary RSS/DDG research returns no usable evidence.
 * It parses Bing's normal HTML results and keeps only entries that actually mention the requested
 * author/work. This avoids replacing a failed search with unrelated filler pages.
 */
class ResearchFallbackEngine {
    suspend fun supplement(
        originalText: String,
        targets: List<String>,
        limitPerTarget: Int = 5,
    ): CreationResearchBundle? = coroutineScope {
        val cleanTargets = targets
            .map(::canonicalTarget)
            .filter { it.length in 2..40 }
            .distinct()
            .take(6)
        if (cleanTargets.isEmpty()) return@coroutineScope null

        val groups = cleanTargets.map { target ->
            async(Dispatchers.IO) {
                val result = fallbackSearch(target, limitPerTarget)
                ReferenceResearchGroup(target, result)
            }
        }.awaitAll()

        CreationResearchBundle(originalText, groups)
    }

    private suspend fun fallbackSearch(target: String, limit: Int): WebResearchResult = coroutineScope {
        val queries = listOf(
            "\"$target\" 小说 作者 作品",
            "$target 小说 简介 主角 剧情",
            "$target 作品集 代表作",
            "$target 世界观 规则 主题",
        )
        val results = queries.map { query ->
            async(Dispatchers.IO) { runCatching { searchBingHtml(query, limit * 3) }.getOrDefault(emptyList()) }
        }.awaitAll()

        val ranked = results
            .flatten()
            .distinctBy { it.url }
            .map { source -> source to relevance(source, target) }
            .filter { it.second >= 70 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)

        val enriched = ranked.mapIndexed { index, source ->
            async(Dispatchers.IO) {
                if (index >= 2) source else source.copy(
                    detail = runCatching { fetchReadablePage(source.url, target) }.getOrDefault("")
                )
            }
        }.awaitAll()

        WebResearchResult(
            query = "HTML fallback: ${queries.joinToString(" / ")}",
            sources = enriched,
            engine = if (enriched.isEmpty()) "Bing HTML fallback unavailable" else "Bing HTML fallback",
        )
    }

    private fun searchBingHtml(query: String, limit: Int): List<WebResearchSource> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val html = get(
            "https://www.bing.com/search?q=$encoded&count=${limit.coerceIn(8, 30)}&setlang=zh-CN",
            900_000,
        )
        val blocks = Regex(
            "<li[^>]*class=\\\"[^\\\"]*b_algo[^\\\"]*\\\"[^>]*>(.*?)</li>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        return blocks.findAll(html).mapNotNull { match ->
            val block = match.groupValues[1]
            val anchor = Regex(
                "<h2[^>]*>.*?<a[^>]+href=\\\"([^\\\"]+)\\\"[^>]*>(.*?)</a>.*?</h2>",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
            ).find(block) ?: return@mapNotNull null
            val url = clean(anchor.groupValues[1])
            val title = clean(anchor.groupValues[2])
            val snippetRaw = Regex(
                "<p[^>]*>(.*?)</p>",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
            ).find(block)?.groupValues?.getOrNull(1).orEmpty()
            val snippet = clean(snippetRaw)
            if (!url.startsWith("http") || title.isBlank()) null
            else WebResearchSource(title.take(180), url.take(900), snippet.take(700))
        }.distinctBy { it.url }.take(limit).toList()
    }

    private fun relevance(source: WebResearchSource, target: String): Int {
        val key = compact(target)
        val title = compact(source.title)
        val snippet = compact(source.snippet)
        if (key.isBlank()) return 0
        var score = 0
        if (title.contains(key)) score += 140
        if (snippet.contains(key)) score += 90
        if (key.length >= 4) {
            key.windowed(2).distinct().forEach { gram ->
                if (title.contains(gram)) score += 12
                if (snippet.contains(gram)) score += 5
            }
        }
        val host = runCatching { URI(source.url).host.orEmpty().lowercase() }.getOrDefault("")
        if (listOf("qidian.com", "hongxiu.com", "readnovel.com", "qq.com", "baidu.com", "zhihu.com")
                .any(host::endsWith)) score += 8
        return score
    }

    private fun fetchReadablePage(url: String, target: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return ""
        val html = get(url, 650_000)
        val withoutNoise = html
            .replace(Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<style\\b[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<noscript\\b[^>]*>.*?</noscript>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<(?:br|p|div|li|h1|h2|h3|article|section)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        @Suppress("DEPRECATION")
        val visible = Html.fromHtml(withoutNoise, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00A0', ' ')
        val key = compact(target)
        val keywords = listOf("作者", "作品", "简介", "主角", "性格", "能力", "剧情", "世界观", "规则", "主题", "设定")
        val lines = visible.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 18..600 }
            .filterNot { it.contains("隐私政策") || it.lowercase().contains("cookie") }
            .toList()
        val selected = lines.mapIndexed { index, line ->
            var score = keywords.count(line::contains) * 16
            if (key.isNotBlank() && compact(line).contains(key)) score += 100
            Triple(index, line, score)
        }.filter { it.third > 0 }
            .sortedByDescending { it.third }
            .take(14)
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
        return (if (selected.isEmpty()) lines.take(10) else selected).joinToString(" ").take(2800)
    }

    private fun get(url: String, maxChars: Int): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36 Langhuan/0.20.7")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.5")
            if (connection.responseCode !in 200..299) return ""
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val out = StringBuilder()
                val buffer = CharArray(4096)
                while (out.length < maxChars) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
                    if (count <= 0) break
                    out.append(buffer, 0, count)
                }
                out.toString()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun canonicalTarget(raw: String): String = raw
        .trim()
        .replace(Regex("^作者[:：]\\s*"), "")
        .substringBefore("（")
        .substringBefore("(")
        .trim('《', '》', '“', '”', '"', '\'', ' ')
        .take(40)

    private fun compact(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    @Suppress("DEPRECATION")
    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("\\s+"), " ").trim()
}
