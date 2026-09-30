package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class OnlineCatalogueV46Test {
    private val source = BookSourceV36("fixture", "fixture", "https://example.org", tocList = "a.chapter", tocName = "text", tocUrl = "href")
    private val book = OnlineBookV36("fixture", "fixture", "Test book", "", "", "", "", "https://example.org/book/7")
    private fun links(range: IntRange) = range.joinToString("") { "<a class='chapter' href='/txt/7/$it.html'>第${it}章 正文</a>" }

    @Test fun latestThirtySixDoesNotReplaceExplicitFullCatalogue() {
        val requests = mutableListOf<String>()
        val pages = mapOf(
            book.bookUrl to ("<div class='latest'>${links(1000..1035)}</div><a href='/catalog/7'>全部章节</a>"),
            "https://example.org/catalog/7" to "${links(1..500)}<a href='/catalog/7?page=2'>下一页</a>",
            "https://example.org/catalog/7?page=2" to links(501..1035),
        )
        val (_, chapters) = loadBookV36(source, book) { _, request ->
            requests += request.url
            Jsoup.parse(pages.getValue(request.url), request.url)
        }
        assertEquals(1035, chapters.size)
        assertEquals("第1章 正文", chapters.first().title)
        assertEquals("第1035章 正文", chapters.last().title)
        assertEquals(3, requests.size)
    }

    @Test fun advertisedFullCatalogueFailureIsNotReportedAsCompletePreview() {
        val result = runCatching {
            loadBookV36(source, book) { _, request ->
                if (request.url != book.bookUrl) throw java.io.IOException("catalogue unavailable")
                Jsoup.parse("${links(1000..1035)}<a href='/catalog/7'>全部章节</a>", request.url)
            }
        }
        assertTrue("A 36 chapter preview must not silently become the full catalogue", result.isFailure)
    }

    @Test fun nextPageIsNotMistakenForFullCatalogueAndPageOneIsKept() {
        val (_, chapters) = loadBookV36(source, book) { _, request ->
            Jsoup.parse(if (request.url == book.bookUrl) "${links(1..36)}<a href='/catalog/7?page=2'>下一页</a>" else links(37..72), request.url)
        }
        assertEquals(72, chapters.size)
        assertEquals("第1章 正文", chapters.first().title)
    }

    @Test fun catalogueIndexHtmlInsideTxtDirectoryIsNotAChapter() {
        assertFalse(isLikelyChapterUrlV39("https://example.org/txt/7/index.html"))
        val doc = Jsoup.parse("<a href='/txt/7/index.html'>全部章节</a>", book.bookUrl)
        assertEquals("https://example.org/txt/7/index.html", heuristicTocUrlV39(doc, explicitOnly = true))
    }

    @Test fun wronglyGeneratedLatestWidgetRuleDoesNotHideExistingFullList() {
        val html = "<div class='latest'>${links(1000..1035)}</div><ul id='chapter-list'>${links(1..1035)}</ul>"
        val (_, chapters) = loadBookV36(source.copy(tocList = ".latest a"), book) { _, request -> Jsoup.parse(html, request.url) }
        assertEquals(1035, chapters.size)
        assertEquals("第1章 正文", chapters.first().title)
    }
}
