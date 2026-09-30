package com.xiguli.langhuan.ui

import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.repairModelJsonV34
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** One visible step of the AI build, shown to the user as it runs. */
internal data class AiSourceStepV37(val label: String, val ok: Boolean? = null, val detail: String = "")

internal data class AiSourceReportV37(
    val source: BookSourceV36,
    val searchCount: Int,
    val bookName: String,
    val chapterCount: Int,
    val sample: String,
)

/**
 * Writes a book source for a site the user names.
 *
 * Nothing is trusted blindly: after each stage the rules are run against the live site, and an
 * empty result goes back to the model once with the evidence before the build gives up.
 */
internal class BookSourceAiBuilderV37(
    private val gateway: AiGateway,
    private val onSteps: (List<AiSourceStepV37>) -> Unit,
) {
    private val steps = ArrayList<AiSourceStepV37>()

    private fun step(label: String) {
        steps += AiSourceStepV37(label)
        onSteps(steps.toList())
    }

    private fun finish(ok: Boolean, detail: String = "") {
        if (steps.isEmpty()) return
        steps[steps.lastIndex] = steps.last().copy(ok = ok, detail = detail)
        onSteps(steps.toList())
    }

    private fun fail(detail: String): Nothing {
        finish(false, detail)
        error(detail)
    }

    suspend fun build(siteUrl: String, keyword: String): AiSourceReportV37 = withContext(Dispatchers.IO) {
        val home = normalizeSiteV37(siteUrl)
        val origin = URL(home).let { "${it.protocol}://${it.host}${if (it.port > 0) ":${it.port}" else ""}" }
        var source = BookSourceV36(id = origin, name = URL(home).host, baseUrl = origin)

        // 1. Home page and search entry
        step("读取网站首页")
        val homeDoc = runCatching { fetchDocumentV36(source, SourceRequestV36(home)) }.getOrElse { fail("打不开这个网址：${it.message.orEmpty().take(80)}") }
        source = source.copy(name = siteNameV37(homeDoc, URL(home).host))
        finish(true, source.name)

        step("识别搜索入口")
        val searchUrl = detectSearchUrlV37(homeDoc) ?: askSearchUrl(homeDoc, origin)
            ?: fail("首页没有找到搜索框。可以换成网站的搜索页链接再试")
        source = source.copy(searchUrl = searchUrl)
        finish(true, searchUrl.take(80))

        // 2. Search results
        step("分析搜索结果页")
        val searchDoc = runCatching { fetchDocumentV36(source, buildSearchRequestV36(source, keyword)) }
            .getOrElse { fail("搜索请求失败：${it.message.orEmpty().take(80)}") }
        var rules = askRules(SEARCH_TASK, searchDoc, keyword, feedback = null)
        source = source.withSearch(rules)
        var results = runCatching { searchSourceV36(source, keyword) }.getOrDefault(emptyList())
        if (results.isEmpty()) {
            rules = askRules(SEARCH_TASK, searchDoc, keyword, feedback = "上次规则 ${rules.compact()} 在这个页面上一个结果都没取到。请对照页面结构重新写，列表规则要能选中每一本书的外层元素。")
            source = source.withSearch(rules)
            results = runCatching { searchSourceV36(source, keyword) }.getOrDefault(emptyList())
        }
        if (results.isEmpty()) fail("搜索结果页的规则没能取到书。请确认测试书名在该站能搜到")
        val picked = results.firstOrNull { it.name == keyword } ?: results.firstOrNull { it.name.contains(keyword) } ?: results.first()
        finish(true, "找到 ${results.size} 本，选中《${picked.name}》")

        // 3. Book page + table of contents
        step("分析书籍页与目录")
        val bookDoc = runCatching { fetchDocumentV36(source, SourceRequestV36(picked.bookUrl)) }
            .getOrElse { fail("书籍页打不开：${it.message.orEmpty().take(80)}") }
        rules = askRules(TOC_TASK, bookDoc, picked.name, feedback = null)
        source = source.withToc(rules)
        var toc = runCatching { loadBookV36(source, picked).second }.getOrDefault(emptyList())
        if (toc.isEmpty()) {
            // The list may live on a separate page; show that page to the model if the book page links to one.
            val tocPage = ruleStringV36(bookDoc, source.infoTocUrl).takeIf { it.isNotBlank() }
                ?.let { runCatching { fetchDocumentV36(source, SourceRequestV36(resolveUrlV36(bookDoc.location(), it))) }.getOrNull() }
            val evidence = tocPage ?: bookDoc
            rules = askRules(
                TOC_TASK,
                evidence,
                picked.name,
                feedback = "上次规则 ${rules.compact()} 没有取到任何章节。" + if (tocPage != null) "下面是目录页（由 infoTocUrl 打开），请写目录规则并保留 infoTocUrl。" else "请检查章节列表的选择器。",
            )
            source = source.withToc(rules, keepTocUrl = tocPage != null)
            toc = runCatching { loadBookV36(source, picked).second }.getOrDefault(emptyList())
        }
        if (toc.isEmpty()) fail("没能取到目录")
        finish(true, "目录 ${toc.size} 章")

        // 4. Chapter text
        step("分析正文页")
        val first = toc.first()
        val chapterDoc = runCatching { fetchDocumentV36(source, SourceRequestV36(first.url)) }
            .getOrElse { fail("正文页打不开：${it.message.orEmpty().take(80)}") }
        rules = askRules(CONTENT_TASK, chapterDoc, first.title, feedback = null)
        source = source.withContent(rules)
        val tocUrls = toc.map { it.url }.toSet()
        var text = runCatching { loadChapterTextV36(source, first, tocUrls) }.getOrDefault("")
        if (text.length < 60) {
            rules = askRules(CONTENT_TASK, chapterDoc, first.title, feedback = "上次规则 ${rules.compact()} 只取到 ${text.length} 个字。正文一般是页面里字数最多的那一块。")
            source = source.withContent(rules)
            text = runCatching { loadChapterTextV36(source, first, tocUrls) }.getOrDefault("")
        }
        if (text.length < 60) fail("没能取到正文")
        finish(true, "第一章 ${text.length} 字")

        AiSourceReportV37(source, results.size, picked.name, toc.size, text.take(160))
    }

    // ---- Model calls ---------------------------------------------------------------------------

    private suspend fun askSearchUrl(home: Document, origin: String): String? {
        val raw = gateway.generateText(
            PromptBundle(
                system = RULE_SYSTEM,
                user = buildString {
                    appendLine("任务：找出这个网站的搜索地址模板。")
                    appendLine("输出 JSON：{\"searchUrl\": \"...\"}。用 {{key}} 表示关键词；GET 直接写地址，POST 写成 \"地址,{\\\"method\\\":\\\"POST\\\",\\\"body\\\":\\\"字段={{key}}\\\"}\"。网站根地址：$origin")
                    appendLine("找不到就输出 {\"searchUrl\": \"\"}。")
                    appendLine()
                    appendLine("【首页结构】")
                    appendLine(pageSkeletonV37(home, 9_000))
                },
            ),
        )
        return parseRulesV37(raw)["searchUrl"]?.takeIf { it.contains("{{key}}") }
    }

    private suspend fun askRules(task: String, doc: Document, hint: String, feedback: String?): Map<String, String> {
        val raw = gateway.generateText(
            PromptBundle(
                system = RULE_SYSTEM,
                user = buildString {
                    appendLine(task)
                    appendLine("页面地址：${doc.location()}")
                    appendLine("页面里应当能看到：$hint")
                    if (feedback != null) {
                        appendLine()
                        appendLine("【上一次的问题】$feedback")
                    }
                    appendLine()
                    appendLine("【页面结构】（每行：标签#id.class [属性] 「文字片段」；缩进表示层级；「×N」表示同结构重复 N 次）")
                    appendLine(pageSkeletonV37(doc))
                },
            ),
        )
        return parseRulesV37(raw)
    }

    private fun Map<String, String>.compact(): String = entries.joinToString("；") { "${it.key}=${it.value}" }.take(400)

    private fun BookSourceV36.withSearch(r: Map<String, String>) = copy(
        searchList = r["searchList"].orEmpty(),
        searchName = r["searchName"].orEmpty(),
        searchAuthor = r["searchAuthor"].orEmpty(),
        searchCover = r["searchCover"].orEmpty(),
        searchIntro = r["searchIntro"].orEmpty(),
        searchLatest = r["searchLatest"].orEmpty(),
        searchBookUrl = r["searchBookUrl"].orEmpty(),
    )

    private fun BookSourceV36.withToc(r: Map<String, String>, keepTocUrl: Boolean = false) = copy(
        infoName = r["infoName"].orEmpty(),
        infoAuthor = r["infoAuthor"].orEmpty(),
        infoCover = r["infoCover"].orEmpty(),
        infoIntro = r["infoIntro"].orEmpty(),
        infoTocUrl = if (keepTocUrl) r["infoTocUrl"]?.ifBlank { null } ?: infoTocUrl else r["infoTocUrl"].orEmpty(),
        tocList = r["tocList"].orEmpty(),
        tocName = r["tocName"].orEmpty(),
        tocUrl = r["tocUrl"].orEmpty(),
        tocNext = r["tocNext"].orEmpty(),
    )

    private fun BookSourceV36.withContent(r: Map<String, String>) = copy(
        contentText = r["contentText"].orEmpty(),
        contentNext = r["contentNext"].orEmpty(),
        contentReplace = r["contentReplace"].orEmpty(),
    )

    private companion object {
        const val RULE_SYSTEM = """你是网页抓取规则工程师，为小说阅读器写书源规则。只输出一个 JSON 对象，不要任何解释。
规则语法（必须遵守）：
- 一律用 @css: 前缀加 CSS 选择器，例如 "@css:div.book-item"。
- 列表规则只写选择器，不带属性。
- 取值规则写成 "@css:选择器@属性"，属性只能是 text、html、href、src、ownText 或任意 HTML 属性名；取值规则相对于列表中的单个元素。
- 选择器为空表示元素自身，例如 "@css:@href"。
- 需要去掉的文字可在末尾加 "##正则"，例如 "@css:span.author@text##作者：".
- 选择器尽量用稳定的 class/id，不要用 nth-child 这类依赖位置的写法，除非别无选择。
不存在的字段输出空字符串。"""

        const val SEARCH_TASK = """任务：为“搜索结果页”写规则。输出 JSON 键：
searchList（每本书的外层元素）、searchName、searchAuthor、searchCover、searchIntro、searchLatest（最新章节名）、searchBookUrl（书籍详情页链接，用 @href）。"""

        const val TOC_TASK = """任务：为“书籍详情页”写规则，并写目录规则。输出 JSON 键：
infoName、infoAuthor、infoCover、infoIntro、infoTocUrl（如果目录在另一个页面，写跳转到目录页的链接规则；目录就在本页则留空）、
tocList（每一章的元素，按正序）、tocName、tocUrl（用 @href）、tocNext（目录有分页时“下一页”链接规则，没有留空）。
注意：很多站的目录前面有“最新章节”区块，tocList 要选正文目录而不是最新章节。"""

        const val CONTENT_TASK = """任务：为“章节正文页”写规则。输出 JSON 键：
contentText（正文容器，用 @html 保留分段）、contentNext（同一章分页时“下一页”的链接规则，没有留空）、
contentReplace（要从正文删掉的广告/提示文字，写成 "##正则"，没有留空）。"""
    }
}

// ---- Helpers (pure, unit-tested) ----------------------------------------------------------------

internal fun normalizeSiteV37(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

internal fun siteNameV37(doc: Document, host: String): String =
    doc.title().split('-', '_', '|', '–', '—', '·').map { it.trim() }.firstOrNull { it.length in 2..20 } ?: host

internal fun parseRulesV37(raw: String): Map<String, String> {
    val element = runCatching { BookSourceJsonV36.parseToJsonElement(repairModelJsonV34(raw)) }.getOrNull() as? JsonObject
        ?: return emptyMap()
    return element.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it.trim() } }.toMap()
}

/**
 * Finds a search form and turns it into a url template. Hidden inputs are kept; the text input
 * becomes {{key}}. POST forms use the `url,{options}` form the engine understands.
 */
internal fun detectSearchUrlV37(doc: Document): String? {
    val forms = doc.select("form")
    val form = forms.firstOrNull { f ->
        f.select("input").any { isQueryInputV37(it) }
    } ?: return null
    val action = form.absUrl("action").ifBlank { doc.location() }
    val params = form.select("input[name]").mapNotNull { input ->
        val name = input.attr("name")
        when {
            isQueryInputV37(input) -> "$name={{key}}"
            input.attr("type").equals("hidden", true) -> "$name=${input.attr("value")}"
            else -> null
        }
    }.joinToString("&")
    if (!params.contains("{{key}}")) return null
    val charset = form.attr("accept-charset").ifBlank {
        doc.select("meta[charset]").attr("charset").ifBlank {
            doc.select("meta[http-equiv=Content-Type]").attr("content").substringAfter("charset=", "")
        }
    }.trim()
    val charsetOption = if (charset.isNotBlank() && !charset.equals("utf-8", true)) ",\"charset\":\"$charset\"" else ""
    return if (form.attr("method").equals("post", true)) {
        "$action,{\"method\":\"POST\",\"body\":\"$params\"$charsetOption}"
    } else {
        val url = action + (if (action.contains('?')) "&" else "?") + params
        if (charsetOption.isNotEmpty()) "$url,{${charsetOption.removePrefix(",")}}" else url
    }
}

private fun isQueryInputV37(input: Element): Boolean {
    val type = input.attr("type").lowercase()
    if (type !in setOf("", "text", "search")) return false
    val name = (input.attr("name") + " " + input.attr("id") + " " + input.attr("placeholder")).lowercase()
    if (listOf("user", "login", "email", "pass", "mail", "phone").any { name.contains(it) }) return false
    val field = input.attr("name").lowercase()
    return input.hasAttr("name") && (
        field in setOf("q", "s", "k", "wd", "kw") ||
            listOf("key", "search", "word", "query", "bookname", "articlename", "搜索", "书名", "作者").any { name.contains(it) }
        )
}

/**
 * Compresses a page to its structure: one line per element with tag, id, classes, link targets
 * and a short text fragment. Repeated sibling structures are collapsed, which is what makes a
 * 300 KB page fit in a prompt.
 */
internal fun pageSkeletonV37(doc: Document, maxChars: Int = 14_000): String {
    val body = doc.body() ?: return ""
    val out = StringBuilder()
    fun signature(e: Element): String = buildString {
        append(e.tagName())
        if (e.id().isNotBlank()) append('#').append(e.id())
        e.classNames().take(3).forEach { append('.').append(it) }
    }
    fun line(e: Element, depth: Int, repeat: Int) {
        if (out.length > maxChars) return
        out.append(" ".repeat(depth)).append(signature(e))
        listOf("href", "src", "data-src", "data-original").forEach { attr ->
            if (e.hasAttr(attr)) out.append(" [").append(attr).append('=').append(e.attr(attr).take(60)).append(']')
        }
        val own = e.ownText().trim()
        if (own.isNotEmpty()) out.append(" 「").append(own.take(24)).append(if (own.length > 24) "…」" else "」")
        if (repeat > 1) out.append(" ×").append(repeat)
        out.append('\n')
    }
    fun walk(e: Element, depth: Int) {
        if (out.length > maxChars || depth > 18) return
        val children = e.children().filterNot { it.tagName() in setOf("script", "style", "noscript", "svg", "iframe", "link", "meta") }
        var index = 0
        while (index < children.size) {
            val child = children[index]
            val sig = signature(child)
            var run = 1
            while (index + run < children.size && signature(children[index + run]) == sig) run++
            // Show the first two of a repeated run in full; the rest are counted.
            val shown = if (run > 2) 2 else run
            for (k in 0 until shown) {
                line(children[index + k], depth, if (k == shown - 1 && run > shown) run else 1)
                walk(children[index + k], depth + 1)
            }
            index += run
        }
    }
    walk(body, 0)
    if (out.length > maxChars) out.setLength(maxChars)
    return out.toString()
}
