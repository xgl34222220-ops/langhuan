package com.xiguli.langhuan.ui

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal enum class SourceCatalogueEvidenceV51 { PARSED_ONLY, STATIC_PAGINATION_END, MATCHED_DECLARED_TOTAL }

/** Evidence about the observed catalogue, not a claim based on a plausible chapter count. */
internal data class SourceCatalogueProofV50(
    val tocUrl: String,
    val pagesRead: Int,
    val declaredTotal: Int? = null,
    val latestOrdinal: Int? = null,
    val warnings: List<String> = emptyList(),
    val hasCompletenessEvidence: Boolean = false,
    val evidence: SourceCatalogueEvidenceV51 = SourceCatalogueEvidenceV51.PARSED_ONLY,
)

internal data class OnlineBookCatalogueV50(
    val book: OnlineBookV36,
    val chapters: List<OnlineChapterV36>,
    val proof: SourceCatalogueProofV50,
)

internal data class CatalogueSectionV50(val full: Boolean = false, val preview: Boolean = false, val weakPreview: Boolean = false)

private val latestLabelV50 = Regex("(?i)(?:最新.{0,6}[章节節]|最近更新|最新更新|latest\\s+chapters|recent\\s+chapters)")
private val fullLabelV50 = Regex("(?i)(全部[章节節]|所有[章节節]|完整.{0,4}(?:[章节節]|目录|目錄)|全[书書]目录|全[书書]目錄|[章节節]{2}目录|[章节節]{2}目錄|^目录$|^目錄$|full\\s+catalog|all\\s+chapters)")
private val headingTagsV50 = setOf("h1", "h2", "h3", "h4", "h5", "h6", "dt", "legend", "header")
private val numberPatternV50 = "[0-9０-９零〇一二三四五六七八九十百千万萬两兩,，]+"

internal fun catalogueNumberV50(raw: String): Int? {
    val text = raw.filterNot { it == ',' || it == '，' || it.isWhitespace() }.map { if (it in '０'..'９') '0' + (it - '０') else it }.joinToString("")
    text.toIntOrNull()?.let { return it.takeIf { value -> value in 0..1_000_000 } }
    if (text.isEmpty()) return null
    val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '兩' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if (text.all { it in digits }) return text.map { digits.getValue(it) }.joinToString("").toIntOrNull()
    var total = 0L
    var section = 0L
    var digit = 0L
    for (character in text) {
        if (character in digits) { digit = digits.getValue(character).toLong(); continue }
        val unit = when (character) { '十' -> 10L; '百' -> 100L; '千' -> 1000L; '万', '萬' -> 10_000L; else -> return null }
        if (unit == 10_000L) { total += (section + digit).coerceAtLeast(1) * unit; section = 0; digit = 0 }
        else { section += digit.coerceAtLeast(1) * unit; digit = 0 }
        if (total + section + digit > 1_000_000) return null
    }
    return (total + section + digit).toInt()
}

internal fun chapterOrdinalV50(title: String): Int? {
    val formal = Regex("(?i)(?:第\\s*($numberPatternV50)\\s*[章节節回]|chapter\\s*($numberPatternV50))").find(title)
        ?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.let(::catalogueNumberV50)
    if (formal != null) return formal
    val stripped = title.trim().replace(Regex("^(?:最新章节|最新章節|最新更新)\\s*[：:]\\s*"), "")
    return Regex("^($numberPatternV50)\\s*(?:[、：:]|[.．](?![0-9０-９]))\\s*\\S").find(stripped)
        ?.groupValues?.get(1)?.let(::catalogueNumberV50)
}

/** Strong head-plus-tail evidence, not a demand that notices or volume-local numbers be consecutive. */
internal fun catalogueMiddleGapV53(titles: List<String>, hasVolumes: Boolean = false): Pair<Int, Int>? {
    if (hasVolumes || titles.any { Regex("第.{1,12}[卷部篇]").containsMatchIn(it) }) return null
    val numbers = titles.mapNotNull(::chapterOrdinalV50)
    if (numbers.size < 8 || numbers.distinct().size != numbers.size) return null
    if (numbers.zipWithNext().any { (a, b) -> b < a }) return null
    val first = numbers.first()
    if (first !in 0..3 || numbers.last() < numbers.size * 2) return null
    return numbers.zipWithNext().firstOrNull { (a, b) -> b - a > maxOf(10, numbers.size / 2) }
}

internal fun catalogueCanonicalUrlV50(url: String): String = publicSourceUrlV36(url).newBuilder().fragment(null).build().toString()

internal fun sameCatalogueOriginV50(base: String, candidate: String): Boolean = runCatching {
    sameSourceOriginV36(publicSourceUrlV36(base), publicSourceUrlV36(candidate))
}.getOrDefault(false)

internal fun catalogueFullLabelV50(text: String): Boolean = fullLabelV50.containsMatchIn(text.replace("\\s+".toRegex(), ""))

/** Resolve only real static HTML targets. Numeric option values are JS parameters, not URLs. */
internal fun catalogueObservedUrlV50(doc: Document, element: Element): String? {
    val attributes = listOf(if (element.tagName() == "option") "value" else "href", "data-url", "data-href", "data-next-url", "data-next", "data-page-url")
    return attributes.firstNotNullOfOrNull { attribute ->
        val raw = element.attr(attribute).trim()
        if (raw.isBlank() || raw == "#" || Regex("(?i)^(javascript|data|mailto):").containsMatchIn(raw)) return@firstNotNullOfOrNull null
        if ((element.tagName() == "option" || attribute.startsWith("data-")) && !Regex("(?i)^(https?://|/|\\./|\\.\\./|\\?|#)|\\.html?(?:[?#]|$)").containsMatchIn(raw)) return@firstNotNullOfOrNull null
        resolveUrlV36(doc.location(), raw).takeIf { sameCatalogueOriginV50(doc.location(), it) }
    }
}

private fun catalogueControlsV51(doc: Document): List<Element> = doc.select("button, a, [role=button], [aria-controls], [data-target], [data-bs-target], [data-url], [data-href], [data-next-url], .load-more, .loadmore, .more-chapters")

private fun catalogueControlTargetsV51(doc: Document, control: Element): List<Element> {
    val ids = control.attr("aria-controls").split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
    listOf("data-target", "data-bs-target").forEach { attribute ->
        control.attr(attribute).takeIf { it.startsWith("#") && !it.contains(Regex("\\s")) }?.removePrefix("#")?.let(ids::add)
    }
    catalogueObservedUrlV50(doc, control)?.let { url ->
        if (catalogueCanonicalUrlV50(url) == catalogueCanonicalUrlV50(doc.location())) runCatching { java.net.URI(url).fragment }.getOrNull()?.let(ids::add)
    }
    return ids.distinct().mapNotNull(doc::getElementById)
}

private fun catalogueMoreControlV51(doc: Document, node: Element, inspector: CataloguePageInspectorV50): Boolean {
    if (node.tagName() == "a" && (isLikelyChapterUrlV39(node.absUrl("href")) || chapterOrdinalV50(node.text()) != null)) return false
    val text = (node.text().ifBlank { node.attr("aria-label") }).trim()
    val section = inspector.section(node)
    val hints = generateSequence<Element>(node) { it.parent() }.take(8).joinToString(" ") { element ->
        element.attributes().filter { it.key in setOf("id", "class", "name", "aria-controls") || it.key.startsWith("data-") }.joinToString(" ") { it.key + " " + it.value }
    }
    val inCatalogue = section.full || section.preview || section.weakPreview || Regex("(?i)chapter|catalog|toc|目录|目錄").containsMatchIn(hints) ||
        catalogueControlTargetsV51(doc, node).any { target -> target.select("a[href]").any { isLikelyChapterUrlV39(it.absUrl("href")) } }
    return Regex("(?:加载|加載|展开|展開|查看更多|查看全部|更多)[\\s：:]*(?:全部|更多)?[\\s：:]*(?:章节|章節|目录|目錄)|^(?:加载更多|加載更多|展开全部|展開全部|全部章节|全部章節|完整目录|完整目錄)$").containsMatchIn(text) ||
        (inCatalogue && text in setOf("更多", "查看更多", "查看全部", "展开", "展開"))
}

/** Parse already delivered hidden HTML, never execute the handler which toggles or fills it. */
internal fun catalogueInlineChapterLinksV51(doc: Document, bookUrl: String): List<OnlineChapterV36> {
    val inspector = CataloguePageInspectorV50(doc)
    return catalogueControlsV51(doc).filter { catalogueMoreControlV51(doc, it, inspector) }
        .flatMap { catalogueControlTargetsV51(doc, it) }.distinct().flatMap { target ->
            target.select("a[href]").mapNotNull { anchor ->
                val title = anchor.text().trim()
                val url = anchor.absUrl("href")
                if (isLikelyChapterLinkV39(title, url, bookUrl)) OnlineChapterV36(title, url) else null
            }
        }.distinctBy { it.url }
}

/** Cache sibling heading scopes once per parent, including dl/dt and deeply nested lists. */
internal class CataloguePageInspectorV50(private val doc: Document) {
    private val before = HashMap<Element, CatalogueSectionV50?>()
    private val scopedParents = HashSet<Element>()
    private val sectionCache = HashMap<Element, CatalogueSectionV50>()
    private val fullLinksCache = HashMap<String, List<OnlineChapterV36>>()
    private val directLabelsCache = HashMap<Element, CatalogueSectionV50?>()

    private fun ownHeading(node: Element): CatalogueSectionV50? {
        if (node.tagName() !in headingTagsV50 && node.selectFirst("a[href]") != null) return null
        val text = node.text().trim()
        if (text.length !in 1..120) return null
        if (latestLabelV50.containsMatchIn(text) && !Regex("^(?:第.+[章节節回]|chapter\\s*\\d+)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return CatalogueSectionV50(preview = true)
        if (catalogueFullLabelV50(text)) return CatalogueSectionV50(full = true)
        return null
    }

    private fun precedingHeading(node: Element): CatalogueSectionV50? {
        val parent = node.parent() ?: return null
        if (scopedParents.add(parent)) {
            var current: CatalogueSectionV50? = null
            parent.children().forEach { child ->
                before[child] = current
                val heading = ownHeading(child)
                val volumeHeading = Regex("^第\\s*$numberPatternV50\\s*[卷部篇]").containsMatchIn(child.text())
                if (heading != null || (child.tagName() in headingTagsV50 && !volumeHeading)) current = heading
            }
        }
        return before[node]
    }

    private fun directHeading(node: Element): CatalogueSectionV50? {
        if (!directLabelsCache.containsKey(node)) directLabelsCache[node] = node.children().mapNotNull(::ownHeading).distinct().singleOrNull()
        return directLabelsCache[node]
    }

    fun section(element: Element): CatalogueSectionV50 = sectionCache.getOrPut(element) {
        var weak = false
        var node: Element? = element
        var result: CatalogueSectionV50? = null
        var depth = 0
        while (node != null && depth++ < 16) {
            if (node.tagName() in setOf("html", "body", "main")) break
            val signal = ownHeading(node) ?: precedingHeading(node) ?: directHeading(node)
            if (signal != null) { result = signal; break }
            if (Regex("(?i)(?:^|[-_\\s])(latest|recent|newest)(?:$|[-_\\s])").containsMatchIn(node.id() + " " + node.className())) weak = true
            node = node.parent()
        }
        result ?: CatalogueSectionV50(weakPreview = weak)
    }

    fun anchors(chapters: List<OnlineChapterV36>): List<Element> {
        val urls = chapters.map { it.url }.toHashSet()
        return doc.select("a[href]").filter { it.absUrl("href") in urls }
    }

    fun selection(chapters: List<OnlineChapterV36>): CatalogueSectionV50 {
        val selected = anchors(chapters)
        if (selected.isEmpty()) return CatalogueSectionV50()
        // A latest widget may repeat URLs also present in the real full list. Its duplicate
        // anchors must not contaminate the full list's evidence.
        val sections = selected.groupBy { it.absUrl("href") }.values.map { duplicates ->
            duplicates.map(::section).let { signals -> signals.firstOrNull { it.full } ?: signals.first() }
        }
        return CatalogueSectionV50(
            full = sections.all { it.full },
            preview = sections.all { it.preview },
            weakPreview = sections.all { it.preview || it.weakPreview },
        )
    }

    fun fullChapterLinks(bookUrl: String): List<OnlineChapterV36> = fullLinksCache.getOrPut(bookUrl) {
        doc.select("a[href]").mapNotNull { anchor ->
            val url = anchor.absUrl("href")
            val title = anchor.text().trim()
            if (section(anchor).full && isLikelyChapterLinkV39(title, url, bookUrl)) OnlineChapterV36(title, url) else null
        }.distinctBy { it.url }
    }

    fun declaredTotal(chapters: List<OnlineChapterV36>): Int? {
        val nodes = LinkedHashSet<Element>()
        val seenAncestors = HashSet<Element>()
        anchors(chapters).forEach { anchor ->
            generateSequence<Element>(anchor.parent()) { it.parent() }.take(12).forEach { ancestor ->
                if (seenAncestors.add(ancestor) && ancestor.tagName() !in setOf("html", "body", "main")) {
                    ancestor.children().filter { it.tagName() in headingTagsV50 || (it.selectFirst("a[href]") == null && it.text().length <= 100) }.forEach(nodes::add)
                    val preceding: Element? = ancestor.previousElementSibling()
                    if (preceding != null && preceding.tagName() in headingTagsV50) nodes.add(preceding)
                }
            }
        }
        // Standard novel metadata is attached to this book, unlike arbitrary recommendation text.
        val metadata = doc.select("meta[property], meta[name]").filter {
            val key = it.attr("property") + it.attr("name")
            Regex("(?i)(?:novel|book).*(?:chapter_count|total_chapters)").containsMatchIn(key) && !Regex("(?i)latest|recent|newest").containsMatchIn(key)
        }.mapNotNull { catalogueNumberV50(it.attr("content")) }
        val pattern = Regex("(?:共|总共|總共|总计|總計|合计|合計)\\s*($numberPatternV50)\\s*(?:章节|章節|[章节節])|(?:总章节|總章節|章节总数|章節總數)\\s*[：:]\\s*($numberPatternV50)")
        val visible = nodes.mapNotNull { node ->
            val text = node.text()
            val wholeBookCount = !latestLabelV50.containsMatchIn(text) && (catalogueFullLabelV50(text) || section(node).full || Regex("全[书書]|总章节|總章節|章节总数|章節總數").containsMatchIn(text))
            if (!wholeBookCount) null
            else pattern.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.let(::catalogueNumberV50)
        }
        // Some detail pages present compact book statistics as "1045章節數".
        // Only short, link-free statistics near the book's main heading are evidence;
        // recommendation cards and chapter titles must not contribute a count.
        val heading = doc.selectFirst("h1")
        val stats = heading?.parent()?.let { parent ->
            generateSequence<Element>(parent) { it.parent() }.take(3)
                .takeWhile { it.tagName() !in setOf("html", "body", "#root", "main") }
                .map { scope -> scope.getAllElements().filter {
                    it.selectFirst("a[href]") == null && it.text().length in 1..40
                }.mapNotNull { node ->
                    Regex("^\\s*($numberPatternV50)\\s*(?:章節數|章节数|章節總數|章节总数)\\s*$").find(node.text())
                        ?.groupValues?.get(1)?.let(::catalogueNumberV50)
                }.distinct() }.firstOrNull { it.isNotEmpty() }?.singleOrNull()?.let(::listOf)
        }.orEmpty()
        return (metadata + visible + stats).filter { it > 0 }.maxOrNull()
    }

    fun latestOrdinal(bookUrl: String): Int? {
        val fromSections = doc.select("a[href]").filter {
            isLikelyChapterLinkV39(it.text(), it.absUrl("href"), bookUrl) && (section(it).preview || section(it).weakPreview)
        }.mapNotNull { chapterOrdinalV50(it.text()) }
        val fromMetadata = doc.select("meta[property], meta[name]").filter {
            Regex("(?i)(?:novel|book).*latest.*chapter").containsMatchIn(it.attr("property") + it.attr("name"))
        }.mapNotNull { chapterOrdinalV50(it.attr("content")) }
        return (fromSections + fromMetadata).maxOrNull()
    }

    fun hasVolumes(): Boolean = doc.select("h2,h3,h4,h5,h6,dt,legend").any {
        Regex("第\\s*$numberPatternV50\\s*[卷部篇]").containsMatchIn(it.text())
    }
}

internal data class CatalogueNavigationV50(val next: String? = null, val unresolved: Boolean = false, val paginated: Boolean = false)

internal fun catalogueNavigationV50(doc: Document, ruleNext: String = "", bookUrl: String = doc.location()): CatalogueNavigationV50 {
    val labels = setOf("下一页", "下一頁", "下页", "下頁", "后一页", "後一頁", "next", "›", "»", ">")
    fun isNext(node: Element): Boolean = node.attr("rel").split(Regex("\\s+")).any { it.equals("next", true) } ||
        listOf(node.text(), node.attr("aria-label"), node.attr("title")).any { it.replace("\\s+".toRegex(), "").lowercase() in labels }
    fun disabled(node: Element): Boolean = node.hasAttr("disabled") || node.attr("aria-disabled") == "true" ||
        node.classNames().any { it.equals("disabled", true) } || node.parent()?.classNames()?.any { it.equals("disabled", true) } == true
    val linked = doc.select("a[href], link[rel][href], option[value], [data-url], [data-href], [data-next-url], [data-next], [data-page-url]")
    if (ruleNext.isNotBlank()) {
        val resolved = resolveUrlV36(doc.location(), ruleNext)
        val observed = linked.firstOrNull { catalogueObservedUrlV50(doc, it) == resolved && !disabled(it) }
        if (observed != null && !isLikelyChapterUrlV39(resolved)) return CatalogueNavigationV50(resolved, paginated = true)
        // A configured selector which accidentally points at a chapter is not a TOC paginator.
        if (!isLikelyChapterUrlV39(resolved)) return CatalogueNavigationV50(unresolved = true, paginated = true)
    }
    var unresolved = false
    for (node in linked.filter { isNext(it) && !disabled(it) }) {
        val url = catalogueObservedUrlV50(doc, node)
        if (url != null && !isLikelyChapterUrlV39(url)) return CatalogueNavigationV50(url, paginated = true)
        if (url == null && node.attr("href") != "#") unresolved = true
    }
    for (select in doc.select("select")) {
        val options = select.select("option")
        if (options.size < 2) continue
        val catalogueHint = select.attributes().filter { it.key in setOf("id", "class", "name", "aria-label") || it.key.startsWith("data-") }
            .joinToString(" ") { it.key + " " + it.value }.lowercase()
        val observedNumberedPages = options.all { it.text().trim().matches(Regex("\\d+(?:\\s*[-—~～至到]\\s*\\d+)?")) } &&
            options.count { option -> catalogueObservedUrlV50(doc, option)?.let { url ->
                val path = publicSourceUrlV36(url).encodedPath
                path == publicSourceUrlV36(doc.location()).encodedPath || Regex("(?i)catalog|/toc(?:/|\\.)|/index(?:[_-]\\d+)?\\.html?").containsMatchIn(path)
            } == true } >= 2
        val relevant = Regex("page|chapter|catalog|toc|目录|目錄|[页頁]").containsMatchIn(catalogueHint) ||
            options.any { Regex("[页頁]|[章节節].*[-—~～]").containsMatchIn(it.text()) } || observedNumberedPages
        if (!relevant) continue
        val selected = options.indexOfFirst { it.hasAttr("selected") }.takeIf { it >= 0 }
            ?: options.indexOfFirst { catalogueObservedUrlV50(doc, it)?.let(::catalogueCanonicalUrlV50) == catalogueCanonicalUrlV50(doc.location()) }.takeIf { it >= 0 } ?: 0
        val nextOption = options.drop(selected + 1).firstOrNull { !disabled(it) }
        if (nextOption != null) {
            val url = catalogueObservedUrlV50(doc, nextOption)
            if (url != null && !isLikelyChapterUrlV39(url)) return CatalogueNavigationV50(url, paginated = true)
            unresolved = true
        }
    }
    for (pager in doc.select(".pagination, .pager, .pages, .pagebar, nav[aria-label]")) {
        val numbered = pager.select("a, span, li").mapNotNull { node -> node.ownText().trim().toIntOrNull()?.let { it to node } }
        if (numbered.size < 2) continue
        val current = numbered.firstOrNull { (_, node) -> node.attr("aria-current") == "page" || node.classNames().any { it in setOf("active", "current", "selected") } || node.parent()?.classNames()?.any { it in setOf("active", "current", "selected") } == true }?.first
            ?: numbered.firstOrNull { (_, node) -> catalogueObservedUrlV50(doc, node)?.let(::catalogueCanonicalUrlV50) == catalogueCanonicalUrlV50(doc.location()) }?.first
            ?: numbered.firstOrNull { (_, node) -> node.tagName() != "a" && node.selectFirst("a") == null }?.first
            ?: continue
        val next = numbered.filter { it.first > current && !disabled(it.second) }.minByOrNull { it.first } ?: continue
        val url = catalogueObservedUrlV50(doc, next.second)
        if (url != null && !isLikelyChapterUrlV39(url)) return CatalogueNavigationV50(url, paginated = true)
        unresolved = true
    }
    val inspector = CataloguePageInspectorV50(doc)
    for (node in catalogueControlsV51(doc).filter { !disabled(it) && catalogueMoreControlV51(doc, it, inspector) }) {
        val observed = catalogueObservedUrlV50(doc, node)
        if (observed != null && catalogueCanonicalUrlV50(observed) != catalogueCanonicalUrlV50(doc.location()) && !isLikelyChapterUrlV39(observed)) return CatalogueNavigationV50(observed, paginated = true)
        val staticChapters = catalogueControlTargetsV51(doc, node).any { target ->
            target.select("a[href]").any { isLikelyChapterLinkV39(it.text(), it.absUrl("href"), bookUrl) }
        }
        if (!staticChapters) unresolved = true
    }
    return CatalogueNavigationV50(unresolved = unresolved)
}
