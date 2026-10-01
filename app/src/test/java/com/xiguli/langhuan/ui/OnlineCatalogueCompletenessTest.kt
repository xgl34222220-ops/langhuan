package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

/** Synthetic documents only. These structures reproduce completeness gaps, not a captured site. */
class OnlineCatalogueCompletenessTest {
    private val base = "https://books.example"
    private val source = BookSourceV36(
        "completeness-fixture", "Completeness fixture", base,
        tocList = "a.chapter", tocName = "text", tocUrl = "href",
    )
    private val book = OnlineBookV36(source.id, source.name, "原创目录测试书", "", "", "", "", "$base/book/7")

    private fun links(range: IntRange, title: (Int) -> String = { "第${it}章 正文" }): String =
        range.joinToString("") { "<a class='chapter' href='/txt/7/$it.html'>${title(it)}</a>" }

    private fun fetch(pages: Map<String, String>, requests: MutableList<String> = mutableListOf()): (BookSourceV36, SourceRequestV36) -> Document =
        { _, request ->
            requests += request.url
            Jsoup.parse(pages.getValue(request.url), request.url)
        }

    private fun assertIncomplete(html: String, configured: BookSourceV36 = source) {
        val result = runCatching { loadBookV36(configured, book, fetch(mapOf(book.bookUrl to html))) }
        assertTrue("An explicitly labelled latest preview must not be returned as a complete catalogue; got ${result.getOrNull()?.second?.size} chapters", result.isFailure)
    }

    @Test fun chineseNumberedLatestPreviewCannotBeReportedAsComplete() {
        val chineseTitles = listOf("第一千章 正文", "第一千零一章 正文", "第一千零二章 正文")
        assertIncomplete("<section class='latest'><h2>最新章節</h2>${links(1000..1002) { chineseTitles[it - 1000] }}</section>")
    }

    @Test fun latestHeadingWithUnnumberedTitlesCannotBeReportedAsComplete() {
        assertIncomplete("<section class='latest'><h2>最新章节</h2>${links(1000..1035) { "今夜的故事 ${('甲'.code + it - 1000).toChar()}" }}</section>")
    }

    @Test fun definitionListLatestHeadingCannotBeReportedAsComplete() {
        val chapters = (1000..1035).joinToString("") { "<dd>${links(it..it)}</dd>" }
        assertIncomplete("<dl><dt>最新章節</dt>$chapters</dl>")
    }

    @Test fun siblingHeadingOutsideListCannotHideFullCatalogueOnSamePage() {
        val html = "<section><h2>最新章节</h2><div><div><ul id='preview'>${links(1000..1035)}</ul></div></div></section>" +
            "<section><h2>全部章节</h2><ul id='chapter-list'>${links(1..1035)}</ul></section>"
        val (_, chapters) = loadBookV36(source.copy(tocList = "#preview a"), book, fetch(mapOf(book.bookUrl to html)))
        assertEquals(1035, chapters.size)
        assertEquals("第1章 正文", chapters.first().title)
    }

    @Test fun fallbackAfterSelectorMissDoesNotBypassLatestPreviewGuard() {
        assertIncomplete("<section class='latest'><h2>最新章节</h2>${links(1000..1035)}</section>", source.copy(tocList = ".obsolete a"))
    }

    @Test fun separateCatalogueTargetStillMustNotBeOnlyLatestPreview() {
        val pages = mapOf(
            book.bookUrl to "<h1>原创目录测试书</h1><a href='/catalog/7'>全部章节</a>",
            "$base/catalog/7" to "<section class='latest'><h2>最新章节</h2>${links(1000..1035)}</section>",
        )
        val result = runCatching { loadBookV36(source, book, fetch(pages)) }
        assertTrue("Following a full-catalogue link is not proof that its response is complete", result.isFailure)
    }

    @Test fun semanticNextLinkWithIconLoadsTheFollowingCataloguePage() {
        val requests = mutableListOf<String>()
        val pages = mapOf(
            book.bookUrl to "${links(1..36)}<a rel='next' aria-label='下一页' href='/catalog/7?page=2'><svg></svg></a>",
            "$base/catalog/7?page=2" to links(37..72),
        )
        val (_, chapters) = loadBookV36(source, book, fetch(pages, requests))
        assertEquals(72, chapters.size)
        assertEquals(listOf(book.bookUrl, "$base/catalog/7?page=2"), requests)
    }

    @Test fun duplicateNextCataloguePageCannotSilentlyCompleteTheBook() {
        val requests = mutableListOf<String>()
        val pages = mapOf(
            book.bookUrl to "${links(1..36)}<a href='/catalog/7?page=2'>下一页</a>",
            "$base/catalog/7?page=2" to links(1..36),
        )
        val result = runCatching { loadBookV36(source, book, fetch(pages, requests)) }
        assertTrue("A next page that adds no chapters is not a verified terminal catalogue page", result.isFailure)
        assertEquals(2, requests.size)
    }

    @Test fun explicitNextPageLoopCannotSilentlyCompleteTheBook() {
        val requests = mutableListOf<String>()
        val pages = mapOf(
            book.bookUrl to "${links(1..36)}<a href='/catalog/7?page=2'>下一页</a>",
            "$base/catalog/7?page=2" to "${links(37..72)}<a href='${book.bookUrl}'>下一页</a>",
        )
        val result = runCatching { loadBookV36(source, book, fetch(pages, requests)) }
        assertTrue("A cyclic next-page link leaves completeness unverified", result.isFailure)
        assertEquals(2, requests.size)
    }

    @Test fun completeShortCatalogueWithChineseTitlesRemainsValid() {
        val titles = listOf("第一章 正文", "第二章 正文", "第三章 正文")
        val (_, chapters) = loadBookV36(source, book, fetch(mapOf(book.bookUrl to "<section><h2>全部章节</h2>${links(1..3) { titles[it - 1] }}</section>")))
        assertEquals(titles, chapters.map { it.title })
    }

    @Test fun completeShortCatalogueWithUnnumberedTitlesRemainsValid() {
        val (_, chapters) = loadBookV36(source, book, fetch(mapOf(book.bookUrl to "<section><h2>全部章节</h2>${links(1..3) { "故事 ${('甲'.code + it - 1).toChar()}" }}</section>")))
        assertEquals(3, chapters.size)
    }

    @Test fun overlappingNextCataloguePageThatAddsNewChaptersRemainsValid() {
        val pages = mapOf(
            book.bookUrl to "${links(1..36)}<a href='/catalog/7?page=2'>下一页</a>",
            "$base/catalog/7?page=2" to links(36..72),
        )
        val (_, chapters) = loadBookV36(source, book, fetch(pages))
        assertEquals(72, chapters.size)
        assertEquals("第1章 正文", chapters.first().title)
        assertEquals("第72章 正文", chapters.last().title)
    }

    @Test fun unlabelledThirtySixChapterBookRemainsReadableWithUnknownCompleteness() {
        val result = loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to links(1..36))))
        assertEquals(36, result.chapters.size)
        assertEquals(book.bookUrl, result.proof.tocUrl)
        assertEquals(1, result.proof.pagesRead)
        assertFalse(result.proof.hasCompletenessEvidence)
        assertTrue(result.proof.warnings.isNotEmpty())
    }

    @Test fun observedFullListAndMatchingDeclaredTotalProvideCompletenessEvidence() {
        val result = loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to "<section><h2>全部章节（共36章）</h2>${links(1..36)}</section>")))
        assertEquals(36, result.proof.declaredTotal)
        assertTrue(result.proof.hasCompletenessEvidence)
        assertTrue(result.proof.warnings.isEmpty())
    }

    @Test fun fullLabelCannotCertifyASelectorThatOnlySelectsTheFirstThirtySix() {
        val html = "<section><h2>全部章节</h2><ul id='chapter-list'>${links(1..72)}</ul></section>"
        val result = loadBookCatalogueV50(source.copy(tocList = "a.chapter:lt(36)"), book, fetch(mapOf(book.bookUrl to html)))
        assertEquals(72, result.chapters.size)
        assertFalse("A full-list label alone is not independent completeness evidence", result.proof.hasCompletenessEvidence)
    }

    @Test fun declaredTotalLargerThanObservedCatalogueFailsEvenWithFullLabel() {
        val html = "<section><h2>全部章节（共72章）</h2>${links(1..36)}</section>"
        val result = runCatching { loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html))) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("72"))
    }

    @Test fun declaredTotalSmallerThanObservedEntriesDoesNotCertifyCompleteness() {
        val html = "<section><h2>全部章节（共35章）</h2>${links(1..36)}</section>"
        val result = loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html)))
        assertEquals(36, result.chapters.size)
        assertFalse(result.proof.hasCompletenessEvidence)
        assertTrue(result.proof.warnings.isNotEmpty())
    }

    @Test fun observedLatestOrdinalBeyondTheCatalogueRequiresFurtherReading() {
        val result = runCatching { loadBookCatalogueV50(source, book.copy(latest = "第一千章 正文"), fetch(mapOf(book.bookUrl to "<section><h2>全部章节</h2>${links(1..36)}</section>"))) }
        assertTrue(result.isFailure)
    }

    @Test fun volumeLocalNumberingIsNotTreatedAsGlobalChapterCount() {
        val html = "<section><h2>全部章节</h2><h3>第一卷</h3>${links(1..3)}<h3>第二卷</h3>${links(4..6) { "第${it - 3}章 第二卷正文" }}</section>"
        val result = loadBookCatalogueV50(source, book.copy(latest = "第二卷 第3章 第二卷正文"), fetch(mapOf(book.bookUrl to html)))
        assertEquals(6, result.chapters.size)
        assertFalse("A full-list label alone is not independent completeness evidence", result.proof.hasCompletenessEvidence)
    }

    @Test fun numberedPaginationFollowsOnlyTheObservedNextPage() {
        val requests = mutableListOf<String>()
        val pages = mapOf(
            book.bookUrl to "${links(1..36)}<nav class='pagination'><span class='current'>1</span><a href='/catalog/7?page=2'>2</a></nav>",
            "$base/catalog/7?page=2" to "${links(37..72)}<nav class='pagination'><a href='${book.bookUrl}'>1</a><span class='current'>2</span></nav>",
        )
        val result = loadBookCatalogueV50(source, book, fetch(pages, requests))
        assertEquals(72, result.chapters.size)
        assertEquals(2, result.proof.pagesRead)
        assertEquals(listOf(book.bookUrl, "$base/catalog/7?page=2"), requests)
        assertFalse("Pagination alone does not certify an unlabelled list", result.proof.hasCompletenessEvidence)
    }

    @Test fun selectPaginationFollowsStaticObservedOptionUrls() {
        val pages = mapOf(
            book.bookUrl to "${links(1..36)}<select id='catalog-pages'><option value='${book.bookUrl}' selected>第1页</option><option value='/catalog/7?page=2'>第2页</option></select>",
            "$base/catalog/7?page=2" to "${links(37..72)}<select id='catalog-pages'><option value='${book.bookUrl}'>第1页</option><option value='/catalog/7?page=2' selected>第2页</option></select>",
        )
        assertEquals(72, loadBookCatalogueV50(source, book, fetch(pages)).chapters.size)
    }

    @Test fun javascriptOnlyMoreChaptersCannotCertifyTheVisiblePrefix() {
        val html = "<section><h2>全部章节</h2>${links(1..36)}<button>加载更多章节</button></section>"
        assertTrue(runCatching { loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html))) }.isFailure)
    }

    @Test fun selectWithOnlyPageNumbersDoesNotInventRequestUrls() {
        val requests = mutableListOf<String>()
        val html = "${links(1..36)}<select id='catalog-pages'><option value='1' selected>第1页</option><option value='2'>第2页</option></select>"
        assertTrue(runCatching { loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html), requests)) }.isFailure)
        assertEquals(listOf(book.bookUrl), requests)
    }

    @Test fun crossOriginNextLinkIsNotFetchedOrIgnoredAsACompleteEnding() {
        val requests = mutableListOf<String>()
        val html = "${links(1..36)}<a rel='next' href='https://elsewhere.example/catalog/7?page=2'>下一页</a>"
        assertTrue(runCatching { loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html), requests)) }.isFailure)
        assertEquals(listOf(book.bookUrl), requests)
    }

    @Test fun bookNamePrefixedLatestHeadingStillMarksAPreview() {
        assertIncomplete("<dl><dt>原创目录测试书最新章節</dt><dd>${links(1000..1035)}</dd></dl>")
    }

    @Test fun plainMoreButtonInsideChapterContainerIsNotACompleteEnding() {
        val html = "<div id='chapter-list'>${links(1..36)}<button>更多</button></div>"
        assertTrue(runCatching { loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html))) }.isFailure)
    }

    @Test fun unrelatedMoreButtonDoesNotBreakACompleteCatalogue() {
        val html = "<aside><button>更多</button></aside><section><h2>全部章节</h2>${links(1..36)}</section>"
        val result = loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html)))
        assertEquals(36, result.chapters.size)
        assertFalse("A full-list label alone is not independent completeness evidence", result.proof.hasCompletenessEvidence)
    }

    @Test fun emptyLocalTargetForMoreChaptersIsNotACompleteEnding() {
        val html = "<section><h2>全部章节</h2>${links(1..36)}<a href='#remaining'>查看全部章节</a><div id='remaining'></div></section>"
        assertTrue(runCatching { loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html))) }.isFailure)
    }

    @Test fun anotherBooksLatestRecommendationDoesNotRejectTheCurrentCompleteBook() {
        val html = "<section><h2>全部章节</h2>${links(1..3)}</section>" +
            "<aside class='latest'><h2>最新章节推荐</h2><a href='/txt/8/999.html'>第999章 另一本书</a></aside>"
        val result = loadBookCatalogueV50(source, book, fetch(mapOf(book.bookUrl to html)))
        assertEquals(3, result.chapters.size)
        assertNull(result.proof.latestOrdinal)
        assertFalse("A full-list label alone is not independent completeness evidence", result.proof.hasCompletenessEvidence)
    }
}
