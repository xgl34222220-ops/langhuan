package com.xiguli.langhuan.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A deliberately small adapter for the documented, static Legado ruleExplore object. */
internal fun normalizeAiExploreObjectV51(root: JsonObject, stage: AiRuleStageV45?): JsonObject {
    if (stage != AiRuleStageV45.EXPLORE || "ruleExplore" !in root) return root
    val unknown = root.keys - setOf("ruleExplore", "type") - stage.fields
    if (unknown.isNotEmpty()) throw AiRuleFormatExceptionV45("AI 返回不支持的字段：${unknown.take(12)}")
    val nested = root["ruleExplore"] as? JsonObject
        ?: throw AiRuleFormatExceptionV45("AI 的 ruleExplore 必须是静态规则对象")
    val names = mapOf(
        "bookList" to "exploreList", "name" to "exploreName", "author" to "exploreAuthor",
        "bookUrl" to "exploreBookUrl", "coverUrl" to "exploreCover", "intro" to "exploreIntro",
        "lastChapter" to "exploreLatest",
    )
    val innerUnknown = nested.keys - names.keys
    if (innerUnknown.isNotEmpty()) throw AiRuleFormatExceptionV45("AI 的 ruleExplore 包含不支持的字段：${innerUnknown.take(12)}")
    if (nested.isEmpty()) throw AiRuleFormatExceptionV45("AI 的 ruleExplore 没有实际规则字段")
    val flat = root.filterKeys { it != "ruleExplore" }.toMutableMap()
    nested.forEach { (key, value) ->
        val name = names.getValue(key)
        if (value !is JsonPrimitive || !value.isString) throw AiRuleFormatExceptionV45("AI 的 ruleExplore.$key 必须是字符串")
        if (name in flat && flat[name] != value) throw AiRuleFormatExceptionV45("AI 的 $name 与 ruleExplore.$key 冲突")
        flat[name] = value
    }
    return JsonObject(flat)
}

internal fun aiDiscoveryNavigationV51(link: AiDiscoveryLinkV37): Boolean =
    Regex("^(?:全部|小说|小說)?(?:分类|分類|排行(?:榜)?|总榜|總榜|月榜|周榜|日榜|玄幻|奇幻|玄幻奇幻|仙侠|仙俠|武侠|武俠|武侠仙侠|武俠仙俠|都市|现代都市|現代都市|历史|歷史|军事|軍事|历史军事|歷史軍事|科幻|游戏|遊戲|竞技|競技|游戏竞技|遊戲競技|言情|灵异|靈異|恐怖|恐怖灵异|恐怖靈異|悬疑|懸疑|完结|完結|新书|新書|全部)(?:小说|小說|分类|分類|榜)?$").matches(link.label) ||
        Regex("/(?:categor(?:y|ies)|class(?:ify)?|sort|rank(?:ing)?|top|list)(?:[/_.?=-]|$)", RegexOption.IGNORE_CASE).containsMatchIn(link.url)

private val discoveryChromeV51 = Regex("(?i)^(?:(?:site|page)[_-])?(?:header|footer|sidebar|side[_-]bar|recommendations|related[_-]books)$")

private fun aiDiscoveryContentAnchorsV51(doc: Document, includeNavigation: Boolean): List<Element> {
    val primary = doc.select("main, [role=main]")
    val anchors = if (primary.isNotEmpty()) primary.flatMap { it.select("a[href]") } else doc.select("a[href]")
    return anchors.filter { anchor ->
        anchor.parents().none { parent ->
            parent.tagName() in setOf("header", "footer", "aside") || parent.attr("role") == "complementary" ||
                !includeNavigation && parent.tagName() == "nav" ||
                discoveryChromeV51.matches(parent.id()) || parent.classNames().any(discoveryChromeV51::matches)
        }
    }.distinct()
}

internal fun aiDiscoveryExcludedUrlsV51(doc: Document): Set<String> {
    val content = aiDiscoveryContentAnchorsV51(doc, includeNavigation = false).mapTo(hashSetOf()) { it.absUrl("href") }
    return doc.select("a[href]").map { it.absUrl("href") }.filter { it.isNotBlank() && it !in content }.toSet()
}

/** A mixed page is not a directory: any unclassified content link keeps it available for
 * rule generation. This deliberately favors inspecting a possible book page over dropping it. */
internal fun aiDiscoveryDirectoryV51(doc: Document, knownBookUrls: List<String>): Boolean {
    if (aiDiscoveryBookLinksV51(doc, knownBookUrls).isNotEmpty()) return false
    val origin = publicSourceUrlV36(doc.location())
    val links = aiDiscoveryContentAnchorsV51(doc, includeNavigation = true).mapNotNull { anchor ->
        val label = anchor.text().trim()
        if (label.isBlank() || anchor.attr("href").startsWith("#")) return@mapNotNull null
        val url = sourceAttemptV36 { publicSourceUrlV36(anchor.absUrl("href")) }.getOrNull() ?: return@mapNotNull null
        if (!sameSourceOriginV36(origin, url)) return@mapNotNull null
        AiDiscoveryLinkV37(label, url.toString())
    }
    val utility = Regex("^(?:首页|首頁|主页|主頁|返回|返回首页|返回首頁|登录|登入|登錄|注册|註冊|搜索|搜尋|书架|書架|上一页|上一頁|下一页|下一頁|上页|下页|末页|末頁|隐私政策|隱私政策|联系我们|聯繫我們)$")
    return links.any(::aiDiscoveryNavigationV51) && links.all { aiDiscoveryNavigationV51(it) || utility.matches(it.label) }
}

/** Only infer a URL family from a book that search actually returned. Root-level siblings
 * are deliberately not generalized: /about and /login are not evidence of book detail pages. */
private fun aiBookFamiliesV51(known: List<String>): Set<Pair<String, String>> = known.mapNotNull { raw ->
    val url = sourceAttemptV36 { publicSourceUrlV36(raw) }.getOrNull() ?: return@mapNotNull null
    val path = url.encodedPath.trimEnd('/')
    val prefix = path.substringBeforeLast('/', "") + "/"
    if (prefix == "/") return@mapNotNull null
    sourceOriginV46(url) to prefix
}.toSet()

internal fun aiDiscoveryBookLinksV51(doc: Document, knownBookUrls: List<String>): List<Element> {
    val families = aiBookFamiliesV51(knownBookUrls)
    val origin = publicSourceUrlV36(doc.location())
    return aiDiscoveryContentAnchorsV51(doc, includeNavigation = false).filter { anchor ->
        val url = sourceAttemptV36 { publicSourceUrlV36(anchor.absUrl("href")) }.getOrNull() ?: return@filter false
        if (!sameSourceOriginV36(origin, url) || isLikelyChapterUrlV39(url.toString()) || anchor.text().isBlank()) return@filter false
        url.toString() in knownBookUrls || families.any { (familyOrigin, prefix) ->
            sourceOriginV46(url) == familyOrigin && url.encodedPath.startsWith(prefix) &&
                url.encodedPath.removePrefix(prefix).trimEnd('/').let { it.isNotBlank() && '/' !in it }
        }
    }
}

/** Reserve the prompt for real book rows, keeping their ancestor selectors. Headers and
 * repeated navigation cannot push the only useful list structure past the character limit. */
internal fun aiDiscoverySkeletonV51(doc: Document, knownBookUrls: List<String>, maxChars: Int): String {
    val anchors = aiDiscoveryBookLinksV51(doc, knownBookUrls)
    if (anchors.isEmpty()) {
        val content = doc.clone()
        content.select("nav, header, footer, aside, [role=complementary], form, script, style, noscript").remove()
        content.select("[id], [class]").filter { discoveryChromeV51.matches(it.id()) || it.classNames().any(discoveryChromeV51::matches) }.forEach { it.remove() }
        return pageSkeletonV37(content, maxChars)
    }
    val rows: List<Element> = anchors.map { anchor ->
        val parent: Element? = anchor.parent()
        val row: Element? = generateSequence<Element>(parent) { it.parent() }.takeWhile { it.tagName() != "body" }
            .firstOrNull { it.tagName() in setOf("li", "tr", "article", "dl") ||
                it.classNames().any { name -> Regex("(?i)book|item|result").containsMatchIn(name) } }
        row ?: parent ?: anchor
    }.distinctBy { it.cssSelector() }.distinctBy { row ->
        // Keep representative structures, including different rank and category layouts.
        row.tagName() + row.className() + row.children().joinToString { it.tagName() + it.className() }
    }.take(4)
    val focused = Jsoup.parse("<body></body>", doc.location())
    for (row in rows) {
        var target: Element = focused.body()
        row.parents().takeWhile { it.tagName() !in setOf("body", "html", "#root") }.asReversed().forEach { ancestor ->
            val shell = ancestor.clone()
            shell.empty()
            target.appendChild(shell)
            target = shell
        }
        target.appendChild(row.clone())
    }
    return pageSkeletonV37(focused, maxChars)
}

internal fun aiSearchAsExploreV51(source: BookSourceV36): Map<String, String> = mapOf(
    "exploreList" to source.searchList, "exploreName" to source.searchName,
    "exploreAuthor" to source.searchAuthor, "exploreBookUrl" to source.searchBookUrl,
    "exploreCover" to source.searchCover, "exploreIntro" to source.searchIntro, "exploreLatest" to source.searchLatest,
)

/** A reusable CSS fallback based on observed sibling detail URLs. It uses the anchor itself
 * for both title and URL, so alternative layouts cannot mix selectors from unrelated rows. */
internal fun aiObservedExploreRulesV51(pages: Collection<Document>, knownBookUrls: List<String>): Map<String, String>? {
    val families = aiBookFamiliesV51(knownBookUrls)
    val prefixes = linkedSetOf<String>()
    for (doc in pages) for (anchor in aiDiscoveryBookLinksV51(doc, knownBookUrls)) {
        val raw = anchor.attr("href")
        val url = publicSourceUrlV36(anchor.absUrl("href"))
        val family = families.firstOrNull { (origin, prefix) -> sourceOriginV46(url) == origin && url.encodedPath.startsWith(prefix) } ?: continue
        val prefix = when {
            raw.startsWith("/") && !raw.startsWith("//") -> family.second
            raw.startsWith("http://") || raw.startsWith("https://") -> family.first + family.second
            else -> continue
        }
        // Avoid synthesizing CSS escapes, credentials or unsafe query matching.
        if (prefix.any { it == '\'' || it == '"' || it == '\\' || it == '[' || it == ']' || it.isWhitespace() }) continue
        val scope = when {
            anchor.parents().any { it.tagName() == "main" } -> "main "
            anchor.parents().any { it.attr("role") == "main" } -> "[role=main] "
            else -> ""
        }
        val exclusions = ":not(header a):not(footer a):not(aside a):not(nav a):not(.sidebar a):not(#sidebar a):not([role=complementary] a)"
        prefixes += "${scope}a[href^='$prefix']$exclusions"
    }
    if (prefixes.isEmpty()) return null
    return mapOf(
        "exploreList" to "@css:" + prefixes.take(8).joinToString(", "),
        "exploreName" to "@css:@text", "exploreBookUrl" to "@css:@href",
    )
}
