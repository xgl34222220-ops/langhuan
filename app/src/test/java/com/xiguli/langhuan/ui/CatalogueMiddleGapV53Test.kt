package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class CatalogueMiddleGapV53Test {
    private val base = "https://books.example"
    private val source = BookSourceV36("gap", "fixture", base, tocList = "a.chapter", tocName = "text", tocUrl = "href")
    private val book = OnlineBookV36(source.id, source.name, "原创测试", "", "", "", "", "$base/book/7.html")
    private fun links(range: IntRange) = range.joinToString("") { "<a class='chapter' href='/txt/7/$it.html'>第${it}章 测试</a>" }
    private val preview get() = links(1..15) + "<a class='chapter' href='/txt/7/notice.html'>读者公告</a>" + links(985..1004)

    @Test fun thirtySixHeadAndTailRowsCannotPassEvenWhenLatestOrdinalMatches() {
        var calls = 0
        val failure = runCatching { loadBookCatalogueV50(source, book.copy(latest = "第1004章 测试")) { _, request ->
            calls++; Jsoup.parse("<h2>章节目录</h2>$preview", request.url)
        } }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message.orEmpty().contains("大段缺章"))
        assertEquals(1, calls)
    }
    @Test fun observedFullIndexOverridesGeneratedLatestWidgetLinkAndFillsMiddle() {
        val requests = mutableListOf<String>()
        val result = loadBookCatalogueV50(source.copy(infoTocUrl = ".wrong@href"), book) { _, request ->
            requests += request.url
            val html = when (request.url) {
                book.bookUrl -> "<article><header><h1>原创测试</h1><span>1004章節數</span></header><a class='wrong' href='/recent/7'>目录</a><a href='/book/7/index.html'>完整目錄</a>$preview</article>"
                "$base/book/7/index.html" -> "<h2>完整目录</h2>${links(1..1004)}"
                else -> error("unexpected request")
            }
            Jsoup.parse(html, request.url)
        }
        assertEquals(1004, result.chapters.size)
        assertEquals(listOf(book.bookUrl, "$base/book/7/index.html"), requests)
        assertTrue(result.proof.hasCompletenessEvidence)
    }
    @Test fun failedFullIndexDoesNotFallBackToPreviewOrRetry() {
        val requests = mutableListOf<String>()
        val failure = runCatching { loadBookCatalogueV50(source, book) { _, request ->
            requests += request.url
            if (request.url == book.bookUrl) Jsoup.parse("<a href='/book/7/index.html'>完整目錄</a>$preview", request.url)
            else error("HTTP 429")
        } }.exceptionOrNull()
        assertTrue(failure!!.message.orEmpty().contains("完整目录读取失败"))
        assertEquals(2, requests.size)
    }
    @Test fun volumeResetsAndShortNaturalGapsAreNotDeclaredMissingMiddle() {
        assertNull(catalogueMiddleGapV53((1..15).map { "第${it}章" } + "公告" + (17..25).map { "第${it}章" }))
        assertNull(catalogueMiddleGapV53((1..15).map { "第${it}章" } + (985..1004).map { "第${it}章" }, hasVolumes = true))
        assertNull(catalogueMiddleGapV53((1..15).map { "第${it}章" } + (1..15).map { "第${it}章" }))
    }
    @Test fun unrelatedRecommendationStatisticsDoNotCertifyTheCurrentBook() {
        val doc = Jsoup.parse("<main><h1>原创测试</h1><div>${links(1..36)}</div><aside><h2>推荐书</h2><span>36章節數</span></aside></main>",book.bookUrl)
        assertNull(CataloguePageInspectorV50(doc).declaredTotal((1..36).map { OnlineChapterV36("第${it}章", "$base/txt/7/$it.html") }))
    }

}
