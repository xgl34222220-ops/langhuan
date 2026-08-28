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

/** Public-web research for creation conversations. No search API key required. */
data class WebResearchSource(
    val title: String,
    val url: String,
    val snippet: String,
    val detail: String = "",
)

data class WebResearchResult(
    val query: String,
    val sources: List<WebResearchSource>,
    val engine: String,
) {
    val context: String get() = sources.joinToString("\n") { source ->
        buildString {
            appendLine("- ${source.title}")
            if (source.snippet.isNotBlank()) appendLine("  搜索摘要：${source.snippet}")
            if (source.detail.isNotBlank()) appendLine("  页面深读：${source.detail}")
            append("  来源：${source.url}")
        }
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
    val deepReadCount: Int get() = sources.count { it.detail.isNotBlank() }

    val context: String get() = groups.joinToString("\n\n") { group ->
        buildString {
            appendLine("【参考对象：${group.target}】")
            appendLine("检索词：${group.result.query}")
            if (group.result.sources.isEmpty()) {
                append("本次网页核验未检索到足够可靠的公开结果。")
            } else {
                append(group.result.context)
            }
        }
    }
}

/**
 * Public-web research with light conversation memory.
 *
 * The AI conversation already keeps its full message transcript. This class keeps the research-side
 * referent as well, so follow-ups such as “再看看他的其他小说” continue researching the author
 * mentioned in the previous turn instead of treating “进我的小说” as a new search target.
 */
class WebResearchEngine {
    private var lastAuthorTarget: String? = null
    private var lastWorkTargets: List<String> = emptyList()

    fun resetContext() {
        lastAuthorTarget = null
        lastWorkTargets = emptyList()
    }

    suspend fun search(userText: String, limit: Int = 6): WebResearchResult {
        val query = normalizeQuery(userText)
        return searchQuery(query, limit)
    }

    suspend fun researchForCreation(
        userText: String,
        perTargetLimit: Int = 5,
        preResolvedTargets: List<String> = emptyList(),
    ): CreationResearchBundle = coroutineScope {
        val explicitAuthor = authorTarget(userText)
        val contextualAuthor = explicitAuthor ?: lastAuthorTarget?.takeIf { isAuthorFollowUp(userText) }

        if (contextualAuthor != null) {
            lastAuthorTarget = contextualAuthor
            val result = searchAuthorPortfolio(contextualAuthor, perTargetLimit)
            return@coroutineScope CreationResearchBundle(
                originalText = userText,
                groups = listOf(
                    ReferenceResearchGroup(
                        "作者：$contextualAuthor（承接会话 · 跨作品风格）",
                        result,
                    )
                ),
            )
        }

        // The UI resolves once before showing the status card. Reuse that exact result here so a
        // second pass cannot reinterpret a trailing pronoun (for example “他们”) as a new title.
        val targets = preResolvedTargets
            .map(::cleanReferenceTarget)
            .filter(::isUsableTarget)
            .distinct()
            .take(6)
            .ifEmpty { referenceTargets(userText) }
        val contextualWorks = if (targets.isEmpty() && isWorkFollowUp(userText)) lastWorkTargets else emptyList()
        val fallback = cleanReferenceTarget(normalizeQuery(userText))
        val effectiveTargets = when {
            targets.isNotEmpty() -> targets
            contextualWorks.isNotEmpty() -> contextualWorks
            // Never send a bare pronoun or a whole instruction sentence to the search engine as if
            // it were a book title. With no resolvable title we return an empty evidence bundle and
            // let the conversation ask one concise clarification question.
            looksLikeUnresolvedReference(userText) -> emptyList()
            else -> listOf(fallback)
        }
            .filter { isUsableTarget(it) }
            .distinct()
            .take(6)

        if (effectiveTargets.isNotEmpty()) lastWorkTargets = effectiveTargets

        val groups = effectiveTargets.map { target ->
            async(Dispatchers.IO) {
                ReferenceResearchGroup(target, searchReferenceTarget(target, perTargetLimit))
            }
        }.awaitAll()

        CreationResearchBundle(userText, groups)
    }

    fun shouldResearch(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false

        if (authorTarget(text) != null) return true
        if (lastAuthorTarget != null && isAuthorFollowUp(text)) return true
        if (lastWorkTargets.isNotEmpty() && isWorkFollowUp(text)) return true
        if (Regex("《[^》]{1,60}》").containsMatchIn(text)) return true

        val direct = listOf(
            "搜一下", "搜索", "查一下", "查查", "联网", "资料", "作品有哪些", "有哪些小说", "哪几本小说",
            "参考", "借鉴", "融合", "结合", "混合", "揉在一起", "取长补短", "类似", "像这本", "这种小说",
            "再看看", "继续看看", "继续搜", "再搜", "其他小说", "其它小说", "其他作品", "其它作品",
        )
        if (direct.any(value::contains)) return true

        if (("你知道" in value || "知道" in value || "了解" in value || "听说过" in value ||
                "看过" in value || "读过" in value) &&
            ("作者" in value || "小说" in value || "作品" in value || "书" in value)
        ) return true

        return Regex("[\\p{L}\\p{N}·_]{2,24}(?:的)?(?:小说|作品|网文|书)").containsMatchIn(text)
    }

    /** Extract an explicit author from natural Chinese requests. */
    fun authorTarget(text: String): String? {
        val patterns = listOf(
            Regex("(?:融合|参考|借鉴|研究|看看|搜一下|搜索|查一下|了解)?\\s*([\\p{L}\\p{N}·_]{2,20})\\s*(?:所?写的|创作的|写过的)\\s*(?:小说|作品|网文|书)(?:的(?:风格|特点|优点|设定|叙事|写法))?"),
            Regex("(?:融合|参考|借鉴|研究|看看|搜一下|搜索|查一下|了解)?\\s*([\\p{L}\\p{N}·_]{2,20})\\s*的(?:小说|作品|网文|书)(?:的?(?:风格|特点|优点|设定|叙事|写法))?"),
            Regex("(?:作者[:：]?\\s*)([\\p{L}\\p{N}·_]{2,20})"),
            Regex("([\\p{L}\\p{N}·_]{2,20})(?:这个|这位)?作者"),
        )
        patterns.forEach { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                val candidate = cleanAuthor(raw)
                if (isPlausibleAuthor(candidate)) return candidate
            }
        }
        return null
    }

    /** High-confidence work extraction. Contextual author follow-ups are intercepted first. */
    fun referenceTargets(text: String): List<String> {
        authorTarget(text)?.let { author ->
            lastAuthorTarget = author
            return listOf(author)
        }

        if (lastAuthorTarget != null && isAuthorFollowUp(text)) return listOf(lastAuthorTarget!!)

        val exact = linkedSetOf<String>()
        val direct = linkedSetOf<String>()
        val fusion = linkedSetOf<String>()
        val ignored = setOf(
            "作者", "这个", "那个", "好几本", "几本", "一些", "这几本", "小说", "作品", "设定", "世界观",
            "优点", "特点", "风格", "这本", "这部", "高层设定", "部分设定", "核心设定", "中式悬疑",
            "中式灵异", "无限流", "惊悚无限流", "我的小说", "进我的小说", "融合进我的小说",
            "其他小说", "其它小说", "其他作品", "其它作品", "他的其他小说", "他的其它小说",
            "他", "他们", "它", "它们", "他们的", "它们的", "你知不知道", "知不知道", "你知道吗",
        )

        fun sanitize(raw: String): String = cleanReferenceTarget(raw).removeSuffix("的").trim()
        fun addTo(set: LinkedHashSet<String>, raw: String) {
            val candidate = sanitize(raw)
            if (isUsableTarget(candidate) && candidate !in ignored && !looksLikeInstruction(candidate)) set += candidate
        }
        fun splitTargets(raw: String, destination: LinkedHashSet<String>) {
            raw.split(Regex("[、,，/+]|以及|还有|和|与"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { addTo(destination, it) }
        }

        Regex("《([^》]{1,60})》").findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.let { addTo(exact, it) }
        }
        if (exact.isNotEmpty()) {
            val found = exact.take(6)
            lastWorkTargets = found
            return found
        }

        // Natural Chinese often omits book-title brackets. Resolve the noun phrase nearest the
        // research action before the broader fusion regex. Examples:
        // - “搜迷雾之上和神秘复苏的核心设定”
        // - “神秘复苏和迷雾之上设定融合，你知不知道他们的特点”
        val bareWorks = linkedSetOf<String>()
        val barePatterns = listOf(
            Regex("(?:搜一下|搜|搜索一下|搜索|查一下|查查|研究一下|研究|了解一下|了解)\\s*([^，。！？!?\\n]{2,100}?)(?:的)?(?:核心)?(?:设定|世界观|规则|特点|优点|剧情|主角|能力)(?:是什么|有哪些|如何|怎么)?"),
            Regex("([^，。！？!?\\n]{2,100}?)(?:的)?(?:核心)?设定\\s*(?:进行|来|做)?(?:融合|结合|混合|对比|分析|研究)"),
            Regex("(?:融合|结合|混合|参考|借鉴)\\s*([^，。！？!?\\n]{2,100}?)(?:的)?(?:核心)?(?:设定|世界观|规则|特点|优点)"),
        )
        barePatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val raw = match.groupValues.getOrNull(1).orEmpty()
                    .replace(Regex("^(?:一下|一下子|看看|一下看看)"), "")
                    .removeSuffix("的")
                    .trim()
                if (Regex("[、,，/+]|以及|还有|和|与").containsMatchIn(raw)) {
                    splitTargets(raw, bareWorks)
                } else {
                    addTo(bareWorks, raw)
                }
            }
        }
        if (bareWorks.isNotEmpty()) {
            val found = bareWorks.take(6)
            lastWorkTargets = found
            return found
        }

        val directBook = Regex(
            "(?:知道|了解|听说过|看过|读过|查一下|查查|搜一下|搜索一下|搜索)\\s*" +
                "[《“\"]?([^》”\"，,。；;！？!?\\n]{2,32}?)[》”\"]?\\s*" +
                "(?:这本|这部)?(?:小说|书|作品)(?:吗|么|嘛)?"
        )
        directBook.findAll(text).forEach { match ->
            match.groupValues.getOrNull(1)?.let { addTo(direct, it) }
        }
        if (direct.isNotEmpty()) {
            val found = direct.take(6)
            lastWorkTargets = found
            return found
        }

        // Only fall back to prior works after all explicit-title forms have been checked. Otherwise
        // a sentence containing both new titles and “他们” would incorrectly reuse an older turn.
        if (lastWorkTargets.isNotEmpty() && isWorkFollowUp(text)) return lastWorkTargets

        val fusionAfter = Regex(
            "(?:融合|结合|混合|参考|借鉴)\\s*([^。！？!?\\n]{2,120}?)(?:的(?:部分)?(?:设定|世界观|优点|特点|风格|机制|叙事)|来写|$)"
        )
        fusionAfter.findAll(text).forEach { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty().trim()
            if (!raw.contains("我的小说") && !raw.startsWith("进我")) {
                if (Regex("[、,，/+]|和|与").containsMatchIn(raw)) splitTargets(raw, fusion) else addTo(fusion, raw)
            }
        }

        val fusionBefore = Regex("把\\s*([^。！？!?\\n]{2,120}?)\\s*(?:融合|结合|混合|揉在一起|放在一起)")
        fusionBefore.findAll(text).forEach { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty().trim()
            if (!raw.contains("我的小说")) {
                if (Regex("[、,，/+]|和|与").containsMatchIn(raw)) splitTargets(raw, fusion) else addTo(fusion, raw)
            }
        }

        val found = fusion.take(6)
        if (found.isNotEmpty()) lastWorkTargets = found
        return found
    }

    private fun isAuthorFollowUp(text: String): Boolean {
        val value = text.trim()
        val pronoun = listOf(
            "他的", "他写的", "他创作的", "这个作者", "这位作者", "该作者",
            "作者其他", "作者其它", "作者的其他", "作者的其它",
        ).any(value::contains)
        val continuation = listOf(
            "其他小说", "其它小说", "别的小说", "更多小说", "其他作品", "其它作品", "别的作品", "更多作品",
            "再看看", "继续看看", "继续搜", "再搜", "还有哪些", "还有什么",
        ).any(value::contains)
        val researchIntent = listOf("小说", "作品", "书", "作者", "风格", "设定", "融合", "参考", "借鉴").any(value::contains)
        return researchIntent && (pronoun || continuation)
    }

    private fun isWorkFollowUp(text: String): Boolean {
        val value = text.trim()
        val referent = listOf(
            "这本", "这部", "这两本", "这几本", "这些作品", "这些小说", "他", "他们", "他们的",
            "它", "它的", "它们", "它们的", "前面那本", "前面两本", "前面那几本", "刚才那本", "刚才两本",
        ).any(value::contains)
        val continuation = listOf("再看看", "继续", "深入", "详细", "主角", "能力", "剧情", "主题", "设定", "核心设定", "世界观", "规则", "特点", "优点", "风格").any(value::contains)
        return referent && continuation
    }

    private fun cleanAuthor(raw: String): String = raw
        .trim()
        .replace(Regex("^(?:我想|我要|帮我|给我|就是|类似于|像|融合|参考|借鉴|研究|看看|搜一下|搜索|查一下|了解)+"), "")
        .trim('《', '》', '“', '”', '"', '\'', '，', ',', '。', '：', ':', ' ')
        .take(20)

    private fun isPlausibleAuthor(value: String): Boolean {
        if (value.length !in 2..20) return false
        val noise = listOf(
            "我想写", "中式", "灵异", "惊悚", "无限流", "小说", "作品", "风格", "设定", "高层", "类似", "这种",
            "他的", "其他", "其它", "我的",
        )
        return noise.none(value::contains)
    }

    private fun isUsableTarget(value: String): Boolean {
        if (value.length !in 2..40) return false
        val noise = listOf(
            "我的小说", "进我的", "融合进", "可以融合", "有什么可以", "再看看他的", "其他小说", "其它小说",
            "其他作品", "其它作品", "你知不知道", "知不知道", "你知道吗", "什么意思", "是什么",
        )
        val pronouns = setOf("他", "他们", "他们的", "它", "它们", "它们的", "这本", "这部", "这两本", "这些")
        return value !in pronouns && noise.none(value::contains)
    }

    private fun looksLikeUnresolvedReference(text: String): Boolean {
        val value = text.trim()
        val pronoun = listOf("他们", "他们的", "它们", "它们的", "这两本", "这几本", "这些作品", "前面那几本")
            .any(value::contains)
        return pronoun && lastWorkTargets.isEmpty() && referenceTargets(value).isEmpty()
    }

    private fun looksLikeInstruction(value: String): Boolean {
        val noise = listOf(
            "我想写", "我想要", "帮我", "给我", "提炼", "借鉴", "融合", "高层", "设定", "世界观",
            "方向", "题材", "类型", "感觉", "风格", "看看有什么", "可以融合",
        )
        return noise.any(value::contains) || value.length > 28
    }

    private suspend fun searchAuthorPortfolio(author: String, limit: Int): WebResearchResult = coroutineScope {
        val authorQueries = listOf(
            "\"$author\" 小说 作者 作品",
            "$author 代表作 小说列表",
            "$author 作者 作品集",
            "$author 小说 风格 特点",
            "$author 其他小说 作品",
        )
        val authorResults = authorQueries.map { query ->
            async(Dispatchers.IO) { searchQuery(query, (limit * 4).coerceIn(16, 28)) }
        }.awaitAll()

        val authorSources = authorResults
            .flatMap { it.sources }
            .distinctBy { it.url }
            .map { it to referenceScore(it, author) }
            .filter { it.second >= relevanceThreshold(author) }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(10)

        val discoveredWorks = discoverWorkTitles(author, authorSources).take(6)
        if (discoveredWorks.isNotEmpty()) lastWorkTargets = discoveredWorks

        val workSources = discoveredWorks.map { work ->
            async(Dispatchers.IO) { searchSpecificWorkForAuthor(author, work) }
        }.awaitAll().flatten()

        val deepAuthorSources = authorSources.take(3).map { source ->
            async(Dispatchers.IO) {
                source.copy(detail = runCatching { fetchReadablePage(source.url, author) }.getOrDefault(""))
            }
        }.awaitAll()

        val combined = (workSources + deepAuthorSources + authorSources.drop(3))
            .distinctBy { it.url }
            .take(20)

        val engines = authorResults.map { it.engine }
            .filter { it != "unavailable" && it != "none" }
            .distinct()
            .joinToString(" + ")
            .ifBlank { "unavailable" }

        val worksLabel = if (discoveredWorks.isEmpty()) {
            "未可靠提取代表作，交由模型既有知识补充并标记未核验"
        } else {
            discoveredWorks.joinToString("、")
        }
        WebResearchResult(
            query = "作者研究：$author；承接当前会话；已发现作品：$worksLabel",
            sources = combined,
            engine = engines,
        )
    }

    private fun discoverWorkTitles(author: String, sources: List<WebResearchSource>): List<String> {
        val works = linkedSetOf<String>()
        val authorKey = compact(author)
        fun add(raw: String) {
            val value = raw.trim().trim('《', '》', '“', '”', '"', '\'', '，', ',', '。', '：', ':')
            if (value.length !in 2..18) return
            if (compact(value) == authorKey) return
            val noise = listOf(
                "小说", "作品", "作者", "简介", "作品集", "全部作品", "最新章节", "免费阅读", "在线阅读",
                "小说大全", "代表作",
            )
            if (noise.any { value == it || value.contains(it) && value.length > 10 }) return
            works += value
        }
        sources.forEach { source ->
            val sourceText = source.title + "\n" + source.snippet
            if (!compact(sourceText).contains(authorKey)) return@forEach
            Regex("《([^》]{2,24})》").findAll(sourceText).forEach { match -> add(match.groupValues[1]) }
            Regex("[“\"]([^”\"，,。；;]{2,18})[”\"]").findAll(sourceText).forEach { match -> add(match.groupValues[1]) }
        }
        return works.take(8)
    }

    private suspend fun searchSpecificWorkForAuthor(
        author: String,
        work: String,
    ): List<WebResearchSource> = coroutineScope {
        val queries = listOf(
            "\"$work\" \"$author\" 小说",
            "$work $author 主角 性格 能力 剧情",
            "$work $author 世界观 规则 主题 剧情",
        )
        val results = queries.map { query -> async(Dispatchers.IO) { searchQuery(query, 12) } }.awaitAll()

        val ranked = results
            .flatMap { it.sources }
            .distinctBy { it.url }
            .map { source ->
                val authorBonus = if (compact(source.title + source.snippet).contains(compact(author))) 30 else 0
                source to (referenceScore(source, work) + authorBonus)
            }
            .filter { it.second >= relevanceThreshold(work) }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(3)

        ranked.mapIndexed { index, source ->
            async(Dispatchers.IO) {
                val detail = if (index < 2) runCatching { fetchReadablePage(source.url, work) }.getOrDefault("") else ""
                source.copy(title = "[$work] ${source.title}", detail = detail)
            }
        }.awaitAll()
    }

    private suspend fun searchReferenceTarget(target: String, limit: Int): WebResearchResult = coroutineScope {
        val cleanTarget = cleanReferenceTarget(target)
        if (!isUsableTarget(cleanTarget)) return@coroutineScope WebResearchResult("", emptyList(), "none")

        val queries = listOf(
            "\"$cleanTarget\" 小说",
            "$cleanTarget 小说 作者 简介",
            "$cleanTarget 主角 性格 能力 剧情",
            "$cleanTarget 世界观 规则 主题 剧情",
            "$cleanTarget 代表作 主角 剧情 设定",
        ).distinct()
        val fetchLimit = (limit * 4).coerceIn(12, 28)
        val results = queries.map { query -> async(Dispatchers.IO) { searchQuery(query, fetchLimit) } }.awaitAll()

        val rankedBase = results
            .flatMap { it.sources }
            .distinctBy { it.url }
            .map { source -> source to referenceScore(source, cleanTarget) }
            .filter { (_, score) -> score >= relevanceThreshold(cleanTarget) }
            .sortedByDescending { (_, score) -> score }
            .map { it.first }
            .take(limit)

        val ranked = rankedBase.mapIndexed { index, source ->
            async(Dispatchers.IO) {
                if (index >= 3) source else source.copy(detail = runCatching { fetchReadablePage(source.url, cleanTarget) }.getOrDefault(""))
            }
        }.awaitAll()

        val engines = results.map { it.engine }
            .filter { it != "unavailable" && it != "none" }
            .distinct()
            .joinToString(" + ")
            .ifBlank { "unavailable" }

        WebResearchResult(query = queries.joinToString(" / "), sources = ranked, engine = engines)
    }

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
        val author = authorTarget(text) ?: lastAuthorTarget?.takeIf { isAuthorFollowUp(text) }
        if (author != null) return "$author 小说 作品"
        if (lastWorkTargets.isNotEmpty() && isWorkFollowUp(text)) return lastWorkTargets.joinToString(" ")

        val directTargets = referenceTargets(text)
        if (directTargets.size == 1) return directTargets.first()

        var q = text.trim()
            .replace(Regex("^(你)?(去)?(帮我)?(联网)?(搜一下|搜索一下|搜索|查一下|查查)[:：,，\\s]*"), "")
            .replace(Regex("[？?！!]$"), "")
            .trim()
        if (q.contains("我的小说") && q.contains("融合")) return ""
        if (q.endsWith("的小说")) q += " 作品"
        if (q.length > 180) q = q.take(180)
        return q
    }

    private fun cleanReferenceTarget(raw: String): String {
        var value = raw.trim().trim('《', '》', '“', '”', '"', '\'', '？', '?', '！', '!')
        value = value
            .replace(Regex("^(?:你)?(?:去)?(?:帮我)?(?:先)?(?:联网)?(?:知道|了解|听说过|看过|读过|搜一下|搜索一下|搜索|查一下|查查|看看|研究|参考|借鉴)\\s*"), "")
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
            host.endsWith("hongxiu.com") || host.endsWith("jjwxc.net") || host.endsWith("xs8.cn")) score += 8
        return score
    }

    private fun relevanceThreshold(target: String): Int {
        val key = compact(target)
        return if (key.length >= 4) 26 else 40
    }

    private fun compact(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun searchBingRss(query: String, limit: Int): List<WebResearchSource> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://www.bing.com/search?q=$encoded&format=rss&count=${limit.coerceIn(6, 30)}", 700_000)
        val itemRegex = Regex("<item>(.*?)</item>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return itemRegex.findAll(body).mapNotNull { match ->
            val item = match.groupValues[1]
            val title = xmlValue(item, "title")
            val url = xmlValue(item, "link")
            val description = xmlValue(item, "description")
            if (title.isBlank() || url.isBlank()) null else WebResearchSource(
                title = clean(title).take(160), url = clean(url), snippet = clean(description).take(700),
            )
        }.distinctBy { it.url }.take(limit).toList()
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<WebResearchSource> {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://html.duckduckgo.com/html/?q=$encoded", 900_000)
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

    private fun fetchReadablePage(url: String, target: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return ""
        val html = get(url, 700_000)
        if (html.isBlank()) return ""
        val withoutNoise = html
            .replace(Regex("<script\\b[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<style\\b[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<noscript\\b[^>]*>.*?</noscript>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<(?:br|p|div|li|h1|h2|h3|article|section)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        @Suppress("DEPRECATION")
        val visible = Html.fromHtml(withoutNoise, Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00A0', ' ')
        val targetKey = compact(target)
        val keywords = listOf("主角", "性格", "能力", "剧情", "简介", "世界观", "设定", "主题", "故事", "作者", "作品", "规则", "金手指", "内容")
        val lines = visible.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 18..600 }
            .filterNot { line ->
                val lower = line.lowercase()
                lower.contains("cookie") || lower.contains("隐私政策") || lower.contains("登录后") || lower.contains("app下载")
            }.toList()
        val selected = lines.mapIndexed { index, line ->
            val key = compact(line)
            var score = 0
            if (targetKey.isNotBlank() && key.contains(targetKey)) score += 100
            score += keywords.count(line::contains) * 18
            Triple(index, line, score)
        }.filter { it.third > 0 }.sortedByDescending { it.third }.take(18).sortedBy { it.first }.map { it.second }.distinct()
        return (if (selected.isEmpty()) lines.take(12) else selected).joinToString(" ").take(3200)
    }

    private fun get(url: String, maxChars: Int): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36 Langhuan/0.20")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6")
            val status = connection.responseCode
            if (status !in 200..299) error("搜索服务返回 $status")
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

    private fun xmlValue(text: String, tag: String): String {
        val raw = Regex("<$tag>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(text)?.groupValues?.getOrNull(1).orEmpty()
        return raw.removePrefix("<![CDATA[").removeSuffix("]]>")
    }

    @Suppress("DEPRECATION")
    private fun clean(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("\\s+"), " ").trim()

    private fun decodeDuckUrl(url: String): String {
        if (!url.contains("uddg=")) return url
        val encoded = url.substringAfter("uddg=").substringBefore('&')
        return runCatching { java.net.URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(url)
    }
}
