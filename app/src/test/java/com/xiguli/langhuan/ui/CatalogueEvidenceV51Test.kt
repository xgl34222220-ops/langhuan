package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/** Synthetic structural fixtures; no claim about the live site's real chapter count. */
class CatalogueEvidenceV51Test {
    private val base = "https://books.example"
    private val source = BookSourceV36("fixture", "fixture", base, tocList = "a.chapter", tocName = "text", tocUrl = "href")
    private val book = OnlineBookV36("fixture", "fixture", "原创目录书", "", "", "", "", "$base/book/7")
    private fun links(range: IntRange) = range.joinToString("") { "<a class='chapter' href='/txt/7/$it.html'>$it、原创测试正文</a>" }
    private fun full(html: String) = "<section><h2>全部章节</h2>$html</section>"
    private fun load(html: String, pages: Map<String, String> = emptyMap(), configured: BookSourceV36 = source, selected: OnlineBookV36 = book): OnlineBookCatalogueV50 =
        loadBookCatalogueV50(configured, selected) { _, request ->
            Jsoup.parse(if (request.url == book.bookUrl) html else pages.getValue(request.url), request.url)
        }

    @Test fun fullLabelAndSamePageThirtySixLinksDoNotIndependentlyProveCompleteness() {
        val result = load(full(links(1..36)))
        assertEquals(36, result.chapters.size)
        assertFalse(result.proof.hasCompletenessEvidence)
        assertEquals(SourceCatalogueEvidenceV51.PARSED_ONLY, result.proof.evidence)
        assertTrue(result.proof.warnings.isNotEmpty())
    }

    @Test fun bareArabicAndChineseChapterOrdinalsAreRecognizedWithoutInventingCounts() {
        mapOf("1、咖啡店没有剧本" to 1, "36. 原创标题" to 36, "３６．原创标题" to 36, "三十六、原创标题" to 36, "一、原创标题" to 1).forEach { (title, number) ->
            assertEquals(title, number, chapterOrdinalV50(title))
        }
        assertNull(chapterOrdinalV50("2026年的故事"))
        assertNull(chapterOrdinalV50("3.14是圆周率近似值"))
    }

    @Test fun explicitBareLatestOrdinalBeyondParsedRangeIsNotIgnored() {
        assertTrue(runCatching { load(full(links(1..36)), selected = book.copy(latest = "72、后续故事")) }.isFailure)
    }

    @Test fun selectNameIdentifiesRealStaticCataloguePaging() {
        val first = full(links(1..36)) + "<select name='chapter_page'><option value='${book.bookUrl}' selected>1-36</option><option value='/catalog/7?p=2'>37-72</option></select>"
        val second = full(links(37..72)) + "<select name='chapter_page'><option value='${book.bookUrl}'>1-36</option><option value='/catalog/7?p=2' selected>37-72</option></select>"
        val result = load(first, mapOf("$base/catalog/7?p=2" to second))
        assertEquals(72, result.chapters.size)
        assertEquals(2, result.proof.pagesRead)
        assertFalse(result.proof.hasCompletenessEvidence)
        assertEquals(SourceCatalogueEvidenceV51.STATIC_PAGINATION_END, result.proof.evidence)
    }

    @Test fun selectDataRoleAndObservedOptionDataUrlAreFollowed() {
        val first = full(links(1..36)) + "<select data-role='catalog-pages'><option value='1' data-url='${book.bookUrl}' selected>1-36</option><option value='2' data-url='/catalog/7?p=2'>37-72</option></select>"
        val second = full(links(37..72)) + "<select data-role='catalog-pages'><option value='1' data-url='${book.bookUrl}'>1-36</option><option value='2' data-url='/catalog/7?p=2' selected>37-72</option></select>"
        val result = load(first, mapOf("$base/catalog/7?p=2" to second))
        assertEquals(72, result.chapters.size)
        assertFalse(result.proof.hasCompletenessEvidence)
    }

    @Test fun numericOnlySelectValuesRequireUnsupportedDynamicNavigation() {
        val html = full(links(1..36)) + "<select name='chapter_page'><option value='1' selected>1-36</option><option value='2'>37-72</option></select>"
        assertTrue(runCatching { load(html) }.isFailure)
    }

    @Test fun outsideExpandControlWithEmptyChapterTargetIsNotIgnored() {
        val html = full(links(1..36)) + "<div><button aria-controls='remaining-chapters' aria-expanded='false'>展开</button></div><div id='remaining-chapters' hidden></div>"
        assertTrue(runCatching { load(html) }.isFailure)
    }

    @Test fun alreadyPresentHiddenChaptersBehindObservedControlAreParsed() {
        val html = full("<div class='initial'>${links(1..36)}</div>") +
            "<button aria-controls='remaining-chapters' aria-expanded='false'>展开</button><div id='remaining-chapters' hidden>${links(37..72)}</div>"
        val result = load(html, configured = source.copy(tocList = ".initial a"))
        assertEquals(72, result.chapters.size)
        assertEquals("1、原创测试正文", result.chapters.first().title)
        assertEquals("72、原创测试正文", result.chapters.last().title)
        assertFalse(result.proof.hasCompletenessEvidence)
    }

    @Test fun staticMoreChaptersDataUrlIsReadWithoutExecutingJavascript() {
        val first = full(links(1..36)) + "<button data-url='/catalog/7?p=2'>更多章节</button>"
        val result = load(first, mapOf("$base/catalog/7?p=2" to full(links(37..72))))
        assertEquals(72, result.chapters.size)
        assertEquals(2, result.proof.pagesRead)
        assertFalse(result.proof.hasCompletenessEvidence)
    }

    @Test fun onlyJavascriptExpandHandlerIsExplicitlyUnsupported() {
        val html = full(links(1..36)) + "<span role='button' data-target='#remaining-chapters' onclick='loadChapters()'>展开</span><div id='remaining-chapters'></div>"
        assertTrue(runCatching { load(html) }.isFailure)
    }

    @Test fun pageCountIsNeverReinterpretedAsDeclaredChapterCount() {
        val result = load("<section><h2>章节目录（共2页）</h2>${links(1..2)}</section>")
        assertNull(result.proof.declaredTotal)
        assertFalse(result.proof.hasCompletenessEvidence)
    }

    @Test fun matchingIndependentlyDeclaredTotalRemainsPositiveEvidence() {
        val result = load("<section><h2>全部章节（共36章）</h2>${links(1..36)}</section>")
        assertEquals(36, result.proof.declaredTotal)
        assertTrue(result.proof.hasCompletenessEvidence)
        assertEquals(SourceCatalogueEvidenceV51.MATCHED_DECLARED_TOTAL, result.proof.evidence)
    }

    @Test fun latestWidgetCountIsNotAWholeBookTotal() {
        assertTrue(runCatching { load("<section class='latest'><h2>最新章节（共36章）</h2>${links(1000..1035)}</section>") }.isFailure)
    }

    @Test fun ordinaryChapterTitleMentioningMoreChaptersIsNotAnExpandControl() {
        val html = "<section><h2>全部章节（共1章）</h2><a class='chapter' href='/txt/7/1.html'>1、作者说要写更多章节</a></section>"
        val result = load(html)
        assertEquals(1, result.chapters.size)
        assertTrue(result.proof.hasCompletenessEvidence)
    }

    @Test fun optionDataUrlsAndNumericRangesCanIdentifyPagingWithoutSelectAttributes() {
        val first = full(links(1..36)) + "<select><option data-url='${book.bookUrl}' selected>1-36</option><option data-url='/catalog/7?p=2'>37-72</option></select>"
        val second = full(links(37..72)) + "<select><option data-url='${book.bookUrl}'>1-36</option><option data-url='/catalog/7?p=2' selected>37-72</option></select>"
        val result = load(first, mapOf("$base/catalog/7?p=2" to second))
        assertEquals(72, result.chapters.size)
        assertEquals(SourceCatalogueEvidenceV51.STATIC_PAGINATION_END, result.proof.evidence)
        assertFalse(result.proof.hasCompletenessEvidence)
    }
}
