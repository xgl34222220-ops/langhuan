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
     * 先从自然语言里抽出真正的作品/作者名，再分别检索；绝不拿整句问话当作品名。
     */
    suspend fun researchForCreation(userText: String, perTargetLimit: Int = 5): CreationResearchBundle = coroutineScope {
        val targets = referenceTargets(userText)
        val fallback = cleanReferenceTarget(normalizeQuery(userText))
        val effectiveTargets = if (targets.isEmpty()) listOf(fallback) else targets
        val groups = effectiveTargets
            .filter { it.isNotBlank() }
            .distinct()
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

        if (Regex("《[^》]{1,60}》").containsMatchIn(text)) return true

        val direct = listOf(
            "搜一下", "搜索", "查一下", "查查", "联网", "资料", "作品有哪些", "有哪些小说", "哪几本小说",
            "参考", "借鉴", "融合", "结合", "混合", "揉在一起", "取长补短", "类似", "像这本", "这种小说",
        )
        if (direct.any(value::contains)) return true

        if (("你知道" in value || "知道" in value || "了解" in value || "听说过" in value || "看过" in value || "读过" in value) &&
            ("作者" in value || "小说" in value || "作品" in value || "书" in value)) return true

        if (Regex("[\\p{L}\\p{N}·_]{2,24}(?:的)?(?:小说|作品|网文|书)").containsMatchIn(text)) return true
        return false
    }

    /** 解析一次会谈里用户真正想参考的小说/作者。 */
    fun referenceTargets(text: String): List<String> {
        val result = linkedSetOf<String>()
        val ignored = setOf(
            "作者", "这个", "那个", "好几本", "几本", "一些", "这几本", "小说", "作品", "设定", "世界观", "优点", "特点", "风格", "这本", "这部"
        )

        fun addCandidate(raw: String) {
            val candidate = cleanReferenceTarget(raw)
            if (candidate.length in 2..60 && candidate !in ignored) result += candidate
        }

        fun addCandidates(raw: String) {
            // 有书名号时由精确提取负责，避免把《A》《B》整段误识别为一个目标。
            if ('《' in raw || '》' in raw) return
            raw.split(Regex("[、,，/+]|和|与"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(::addCandidate)
        }

        Regex("《([^》]{1,60})》").findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.let(::addCandidate)
        }

        // 直接问书：你知道迷雾之上这本书吗 / 了解迷雾之上这部小说吗。
        val directBookPattern = Regex(
            "(?:^|[，,。；;！？!?\\s])(?:你)?(?:知道|了解|听说过|看过|读过|查一下|查查|搜一下|搜索一下|搜索)?\\s*" +
                "[《“\"]?([^》”\"，,。；;！？!?\\n]{2,60}?)[》”\"]?\\s*" +
                "(?:这本|这部)?(?:小说|书|作品)(?:吗|么|嘛)?(?=$|[，,。；;！？!?\\s])"
        )
        directBookPattern.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.let(::addCandidate)
        }

        // “你去搜一下薄情书生的小说 / 我想参考紫金陈的作品”。
        val authorPattern = Regex(
            "(?:^|[，,。；;！？!?\\s])(?:我想)?(?:你)?(?:去)?(?:帮我)?(?:先)?(?:联网)?" +
                "(?:搜一下|搜索一下|搜索|查一下|查查|看看|了解一下|了解|参考|借鉴|融合|结合|知道|听说过)?\\s*" +
                "([\\p{L}\\p{N}·_、，,与和]{2,40})的(?:小说|作品|网文|书)"
        )
        authorPattern.findAll(text).forEach { match -> addCandidates(match.groupValues.getOrNull(1).orEmpty()) }

        // 不强制书名号：融合迷雾之上、怪谈玩家、十日终焉的设定。
        val fusionAfter = Regex(
            "(?:融合|结合|混合|参考|借鉴)\\s*([^。！？!?\\n]{2,140}?)(?:的(?:部分)?(?:设定|世界观|优点|特点|风格|机制|叙事)|来写|$)"
        )
        fusionAfter.findAll(text).forEach { match -> addCandidates(match.groupValues.getOrNull(1).orEmpty()) }

        // 也兼容：把迷雾之上、怪谈玩家、十日终焉融合起来。
        val fusionBefore = Regex(
            "把\\s*([^。！？!?\\n]{2,140}?)\\s*(?:融合|结合|混合|揉在一起|放在一起)"
        )
        fusionBefore.findAll(text).forEach { match -> addCandidates(match.groupValues.getOrNull(1).orEmpty()) }

        return result.take(6)
    }

    private suspend fun searchReferenceTarget(target: String, limit: Int): WebResearchResult = coroutineScope {
        val cleanTarget = cleanReferenceTarget(target)
        if (cleanTarget.isBlank()) return@coroutineScope WebResearchResult("", emptyList(), "none")

        // 同一对象用多组搜索词并行检索。搜索服务偶尔会忽略中文引号，因此不能只赌一条 query。
        val queries = listOf(
            "\"$cleanTarget\" 小说",
            "$cleanTarget 小说 作者 简介",
            "\"$cleanTarget\" 作品 剧情 世界观 设定",
        ).distinct()
        val fetchLimit = (limit * 4).coerceIn(12, 28)
        val results = queries.map { query ->
            async(Dispatchers.IO) { searchQuery(query, fetchLimit) }
        }.awaitAll()

        val ranked = results
            .flatMap { it.sources }
            .distinctBy { it.url }
            .map { source -> source to referenceScore(source, cleanTarget) }
            .filter { (_, score) -> score >= relevanceThreshold(cleanTarget) }
            .sortedByDescending { (_, score) -> score }
            .map { it.first }
            .take(limit)

        val engines = results.map { it.engine }
            .filter { it != "unavailable" && it != "none" }
            .distinct()
            .joinToString(" + ")
            .ifBlank { "unavailable" }

        WebResearchResult(
            query = queries.joinToString(" / "),
            sources = ranked,
            engine = engines,
        )
    }

    /**
     * Bing RSS 只要返回任意内容就算成功的旧逻辑会把完全无关的结果吞进来。
     * 现在两路搜索都取，再由上层按真实参考对象做相关性过滤。
     */
    private suspend fun searchQuery(query: String, limit: Int): WebResearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext WebResearchResult("", emptyList(), "none")

        val requestLimit = limit.coerceIn(6, 30)
        val bing = runCatching { searchBingRss(query, requestLimit) }.getOrDefault(emptyList())
        val duck = runCatching { searchDuckDuckGo(query, requestLimit) }.getOrDefault(emptyList())
        val merged = (bing + duck).distinctBy { it.url }.take(limit)
        val engine = buildList {
            if (bing.isNotEmpty()) add("Bing RSS")
            if (duck.isNotEmpty()) add("DuckDuckGo")
        }.joinToString(" + ").ifBlank { "unavailable" }
        WebResearchResult(query, merged, engine)
    }

    private fun normalizeQuery(text: String): String {
        val directTargets = referenceTargets(text)
        if (directTargets.size == 1) return directTargets.first()

        var q = text.trim()
            .replace(Regex("^(你)?(去)?(帮我)?(联网)?(搜一下|搜索一下|搜索|查一下|查查)[:：,，\\s]*"), "")
            .replace(Regex("[？?！!]$"), "")
            .trim()
        if (q.endsWith("的小说")) q += " 作品"
        if (q.length > 180) q = q.take(180)
        return q
    }

    private fun cleanReferenceTarget(raw: String): String {
        var value = raw.trim().trim('《', '》', '“', '”', '"', '\'', '？', '?', '！', '!')
        value = value
            .replace(Regex("^(?:你)?(?:知道|了解|听说过|看过|读过|搜一下|搜索一下|搜索|查一下|查查)\\s*"), "")
            .replace(Regex("(?:这本|这部)?(?:小说|书|作品)(?:吗|么|嘛)?$"), "")
            .replace(Regex("(?:吗|么|嘛)$"), "")
            .trim()
        return value.take(60)
    }

    private fun referenceScore(source: WebResearchSource, target: String): Int {
        val key = compact(target)
        if (key.isBlank()) return 0
        val title = compact(source.title)
        val snippet = compact(source.snippet)
        var score = 0

        if (title.contains(key, ignoreCase = true)) score += 120
        if (snippet.contains(key, ignoreCase = true)) score += 70

        if (key.length >= 4) {
            val grams = key.windowed(2).distinct()
            score += grams.count { title.contains(it, ignoreCase = true) } * 10
            score += grams.count { snippet.contains(it, ignoreCase = true) } * 4
        } else {
            val tokens = target.split(Regex("\\s+")).filter { it.length >= 2 }
            score += tokens.count { title.contains(it, ignoreCase = true) } * 18
            score += tokens.count { snippet.contains(it, ignoreCase = true) } * 8
        }

        val host = runCatching { URI(source.url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.endsWith("qidian.com") || host.endsWith("xxsy.net") || host.endsWith("readnovel.com") ||
            host.endsWith("hongxiu.com") || host.endsWith("jjwxc.net") || host.endsWith("xs8.cn")) {
            score += 8
        }
        return score
    }

    private fun relevanceThreshold(target: String): Int {
        val key = compact(target)
        return if (key.length >= 4) 26 else 40
    }

    private fun compact(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun searchBingRss(query: String, limit: Int): List<WebResearchSource> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://www.bing.com/search?q=$encoded&format=rss&count=${limit.coerceIn(6, 30)}")
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
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36 Langhuan/0.19")
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
