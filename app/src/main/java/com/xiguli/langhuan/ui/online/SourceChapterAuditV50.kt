package com.xiguli.langhuan.ui

import java.text.Normalizer
import java.util.concurrent.CancellationException
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal enum class SourceIdentityStateV50 { VERIFIED, UNKNOWN }

internal data class SourceChapterProofV50(
    val chapterUrl: String,
    val pagesRead: Int,
    val bookIdentity: SourceIdentityStateV50,
    val chapterIdentity: SourceIdentityStateV50,
    val identityEvidence: List<String>,
    val warnings: List<String>,
) {
    val identityVerified: Boolean
        get() = bookIdentity == SourceIdentityStateV50.VERIFIED && chapterIdentity == SourceIdentityStateV50.VERIFIED
}

internal data class OnlineChapterAuditV50(val text: String, val proof: SourceChapterProofV50)

/**
 * Evidence concerns the requested book/chapter and the selected DOM, never the subject of the prose.
 * A novel can quote news, use a news-like title, or publish og:type=article. None proves a mismatch.
 * Missing identity is readable but explicitly unknown; a length threshold cannot prove identity.
 */
internal fun loadChapterAuditV50(
    source: BookSourceV36,
    book: OnlineBookV36?,
    chapter: OnlineChapterV36,
    tocUrls: Set<String>,
    fetchDocument: (BookSourceV36, SourceRequestV36) -> Document = ::fetchDocumentV36,
): OnlineChapterAuditV50 {
    val initialUrl = publicSourceUrlV36(chapter.url).toString()
    val otherChapters = tocUrls.map { publicSourceUrlV36(it).toString() }.toSet() - initialUrl
    val visited = HashSet<String>()
    val parts = ArrayList<String>()
    val evidence = ArrayList<String>()
    val unknownBooks = ArrayList<Int>()
    val unknownChapters = ArrayList<Int>()
    var url = initialUrl
    var firstPageUrl = initialUrl
    var chars = 0

    fun completed(): OnlineChapterAuditV50 = OnlineChapterAuditV50(
        parts.joinToString("\n").trim(),
        SourceChapterProofV50(
            chapterUrl = firstPageUrl,
            pagesRead = parts.size,
            bookIdentity = if (unknownBooks.isEmpty()) SourceIdentityStateV50.VERIFIED else SourceIdentityStateV50.UNKNOWN,
            chapterIdentity = if (unknownChapters.isEmpty()) SourceIdentityStateV50.VERIFIED else SourceIdentityStateV50.UNKNOWN,
            identityEvidence = evidence.toList(),
            warnings = buildList {
                if (unknownBooks.isNotEmpty()) add("第 ${unknownBooks.joinToString("、")} 页缺少可核对的书名身份，书籍身份未知")
                if (unknownChapters.isNotEmpty()) add("第 ${unknownChapters.joinToString("、")} 页缺少可核对的章节身份，章节身份未知")
            },
        ),
    )

    repeat(12) { index ->
        val page = index + 1
        if (Thread.currentThread().isInterrupted) throw CancellationException("正文读取已取消")
        check(visited.add(url)) { "正文分页出现循环，未返回不完整正文" }
        val doc = fetchDocument(source, SourceRequestV36(url))
        val finalUrl = publicSourceUrlV36(doc.location()).toString()
        if (index == 0) firstPageUrl = finalUrl
        check(finalUrl !in otherChapters) { "正文跳转到目录中的其他章节，未拼接跨章正文" }
        check(finalUrl == url || visited.add(finalUrl)) { "正文分页重定向到已读页面，未返回不完整正文" }

        val (textRule, selected) = chapterBodySelectionV50(doc, source.contentText)
        check(!chapterSelectionIsNavigationV50(selected)) { "正文规则选中了导航或链接目录（第 $page 页），未验证通过" }
        val identity = chapterPageIdentityV50(doc, selected, book?.name.orEmpty(), chapter.title, page)
        evidence += identity.evidence
        if (!identity.bookVerified) unknownBooks += page
        if (!identity.chapterVerified) unknownChapters += page
        val text = cleanContentV36(ruleStringV36(doc, textRule), source.contentReplace).trim()
        check(text.isNotBlank()) { "正文规则未提取到可读文字（第 $page 页）" }
        parts += text
        chars += text.length
        check(chars <= MAX_SOURCE_BYTES_V36) { "单章正文超过大小限制" }

        val next = ruleStringV36(doc, source.contentNext).takeIf { it.isNotBlank() }
            ?.let { publicSourceUrlV36(resolveUrlV36(doc.location(), it)).toString() }
        if (next == null || next in otherChapters) return completed()
        check(next !in visited) { "正文分页出现循环，未返回不完整正文" }
        check(page < 12) { "正文超过 12 页且仍有下一页，未返回不完整正文" }
        url = next
    }
    error("正文超过 12 页限制，未返回不完整正文")
}

private val CHAPTER_VALUE_ATTRS_V50 = setOf(
    "text", "textNodes", "ownText", "html", "all", "href", "src", "content", "value", "title", "alt", "data-src", "data-original",
)

/** Keep the existing value-rule behavior, and inspect only the alternative that actually returned text. */
private fun chapterBodySelectionV50(doc: Document, rawRule: String): Pair<String, List<Element>> {
    val rule = rawRule.ifBlank { "@css:#content@html" }
    val cleanupAt = rule.indexOf("##").takeIf { it >= 0 } ?: rule.length
    val valueRule = rule.substring(0, cleanupAt)
    val cleanup = rule.substring(cleanupAt)
    val textRule = if (valueRule.substringAfterLast('@') in CHAPTER_VALUE_ATTRS_V50) rule else "$valueRule@html$cleanup"
    val selectedAlternative = textRule.substringBefore("##").split("||")
        .firstOrNull { ruleStringV36(doc, it + cleanup).isNotBlank() }.orEmpty()
    val selected = selectedAlternative.split("&&").flatMap { part ->
        val value = part.trim()
        val attr = value.substringAfterLast('@')
        val selector = if ('@' in value && (attr in CHAPTER_VALUE_ATTRS_V50 || attr.startsWith("data-"))) {
            value.substringBeforeLast('@')
        } else value
        if (selector.isBlank() || selector == "@css:") listOf(doc) else ruleElementsV36(doc, selector)
    }.distinct()
    return textRule to selected
}

private fun chapterSelectionIsNavigationV50(selected: List<Element>): Boolean {
    if (selected.isEmpty()) return false
    val navigationTags = setOf("nav", "select", "option")
    val navigationRoles = setOf("navigation", "menu", "menubar", "tablist")
    if (selected.all { element ->
        generateSequence(element) { it.parent() }.any { it.tagName() in navigationTags || it.attr("role").lowercase() in navigationRoles }
    }) return true
    val roots = selected.filter { element -> selected.none { other -> other !== element && element.parents().contains(other) } }
    val links = roots.flatMap { it.select("a[href]") }.distinct()
    if (links.isEmpty()) return false
    val totalLength = roots.sumOf { it.text().count { c -> !c.isWhitespace() } }
    val linkLength = links.sumOf { it.text().count { c -> !c.isWhitespace() } }
    val explicitList = roots.all { root ->
        root.tagName() in setOf("a", "ul", "ol", "dl", "li", "dd") || root.attr("role") == "list" ||
            Regex("(?i)(^|[ _-])(toc|catalog|catalogue|chapterlist|chapter-list)([ _-]|$)").containsMatchIn(root.id() + " " + root.className())
    }
    return (links.size >= 3 || explicitList) && totalLength > 0 && linkLength.toDouble() / totalLength >= 0.8
}

private data class ChapterPageIdentityV50(val bookVerified: Boolean, val chapterVerified: Boolean, val evidence: List<String>)

private fun chapterPageIdentityV50(
    doc: Document,
    selected: List<Element>,
    expectedBook: String,
    expectedChapter: String,
    page: Int,
): ChapterPageIdentityV50 {
    val evidence = ArrayList<String>()
    var bookVerified = false
    var chapterVerified = false
    val bookFields = setOf("og:novel:book_name", "novel:book_name")
    val chapterFields = setOf("og:novel:chapter_name", "og:novel:chapter_title", "novel:chapter_name", "novel:chapter_title")
    doc.select("meta[property], meta[name]").forEach { meta ->
        val field = meta.attr("property").ifBlank { meta.attr("name") }.lowercase()
        val actual = meta.attr("content").trim()
        if (actual.isBlank()) return@forEach
        if (field in bookFields && expectedBook.isNotBlank()) {
            check(identityTextV50(actual) == identityTextV50(expectedBook)) {
                "正文书籍身份不符（第 $page 页，$field），未验证通过"
            }
            bookVerified = true
            evidence += "第 $page 页：$field 与期待书名一致"
        }
        if (field in chapterFields && expectedChapter.isNotBlank()) {
            check(chapterTitlesMatchV50(actual, expectedChapter)) {
                "正文章节身份不符（第 $page 页，$field），未拼接跨章正文"
            }
            chapterVerified = true
            evidence += "第 $page 页：$field 与期待章节一致"
        }
    }
    // An ordinary title is not a novel-identity field. Only an explicit chapter-number conflict
    // can reject it. Ignore headings inside the prose, where a quoted article may have its own title.
    val titles = buildList {
        add("title" to doc.title())
        doc.select("meta[property=og:title], meta[name=og:title]").forEach { add("og:title" to it.attr("content")) }
        doc.select("h1").filter { heading -> selected.none { it === heading || heading.parents().contains(it) } }
            .takeIf { it.size == 1 }?.firstOrNull()?.let { add("h1" to it.text()) }
    }
    val expectedNumber = chapterNumberV50(expectedChapter)
    titles.forEach { (field, actual) ->
        val actualNumber = chapterNumberV50(actual)
        if (expectedNumber != null && actualNumber != null) {
            check(expectedNumber == actualNumber) { "正文章节号不符（第 $page 页，$field），未拼接跨章正文" }
        }
        if (expectedChapter.isNotBlank() && chapterTitlesMatchV50(actual, expectedChapter)) {
            chapterVerified = true
            evidence += "第 $page 页：$field 与期待章节一致"
        }
    }
    return ChapterPageIdentityV50(bookVerified, chapterVerified, evidence)
}

private fun identityTextV50(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .lowercase().filter { it.isLetterOrDigit() }

private fun chapterTitleV50(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
    .replace(Regex("\\s*\\((?:第\\s*)?\\d+\\s*(?:[/]\\s*\\d+|[页頁])\\)\\s*$"), "").trim()

private val CHAPTER_NUMBER_V50 = Regex("^(?:第\\s*([0-9零〇一二两兩三四五六七八九十百千万萬]+)\\s*[章节節回]|chapter\\s+(\\d+))", RegexOption.IGNORE_CASE)

private fun chapterNumberV50(value: String): Long? {
    val match = CHAPTER_NUMBER_V50.find(chapterTitleV50(value)) ?: return null
    val digits = match.groupValues[1].ifBlank { match.groupValues[2] }
    digits.toLongOrNull()?.let { return it }
    var total = 0L
    var group = 0L
    var number = 0L
    digits.forEach { c ->
        val digit = when (c) { '零', '〇' -> 0; '一' -> 1; '二', '两', '兩' -> 2; '三' -> 3; '四' -> 4; '五' -> 5; '六' -> 6; '七' -> 7; '八' -> 8; '九' -> 9; else -> null }
        if (digit != null) number = number * 10 + digit else {
            val unit = when (c) { '十' -> 10; '百' -> 100; '千' -> 1000; '万', '萬' -> 10000; else -> return null }
            if (unit == 10000) { total += (group + number).coerceAtLeast(1) * unit; group = 0 } else group += number.coerceAtLeast(1) * unit
            number = 0
        }
    }
    return total + group + number
}

private fun chapterTitlesMatchV50(actual: String, expected: String): Boolean {
    val a = chapterTitleV50(actual)
    val b = chapterTitleV50(expected)
    if (identityTextV50(a) == identityTextV50(b)) return true
    val number = chapterNumberV50(a) ?: return false
    if (number != chapterNumberV50(b)) return false
    val actualTail = identityTextV50(a.replace(CHAPTER_NUMBER_V50, ""))
    val expectedTail = identityTextV50(b.replace(CHAPTER_NUMBER_V50, ""))
    return actualTail == expectedTail || actualTail.isBlank() || expectedTail.isBlank()
}
