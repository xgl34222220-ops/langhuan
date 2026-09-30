package com.xiguli.langhuan.ui

import java.io.IOException
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/** Runs the production reader against deterministic documents; no network-policy exceptions. */
class BookSourceRecoveryV42Test {
    private val base = "https://books.example"
    private val source = BookSourceV36(
        "recovery", "Recovery fixture", base,
        tocList = "@css:#chapters a", tocName = "@text", tocUrl = "@href",
        tocNext = "@css:a.next@href", contentText = "@css:#content@html",
    )
    private val book = OnlineBookV36(source.id, source.name, "Book one", "", "", "", "", "$base/book/1")

    private fun catalogue(number: Int, next: Int? = null): String =
        """<div id="chapters"><a href="/read/1/$number.html">第${number}章</a></div>""" +
            (next?.let { """<a class="next" href="/catalog/$it">下一页</a>""" } ?: "")

    @Test fun declaredOnPageCatalogueDoesNotFollowRecommendation() {
        val requests = mutableListOf<String>()
        val (_, chapters) = loadBookV36(source, book) { _, request ->
            requests += request.url
            val html = if (request.url == book.bookUrl) {
                catalogue(1) + """<a href="/book/2">推荐：另一本书</a>"""
            } else catalogue(999)
            Jsoup.parse(html, request.url)
        }
        assertEquals(listOf(book.bookUrl), requests)
        assertEquals(listOf("$base/read/1/1.html"), chapters.map { it.url })
    }

    @Test fun bookRecommendationAloneIsNotDirectoryEvidence() {
        val doc = Jsoup.parse("""<a href="/book/2">推荐：另一本书</a>""", book.bookUrl)
        assertNull(heuristicTocUrlV39(doc))
    }

    @Test fun explicitlyDeclaredSeparateCatalogueStillTakesPrecedence() {
        val requests = mutableListOf<String>()
        val (_, chapters) = loadBookV36(source.copy(infoTocUrl = "@css:a.catalogue@href"), book) { _, request ->
            requests += request.url
            val html = if (request.url == book.bookUrl) {
                catalogue(99) + """<a class="catalogue" href="/catalog/full">全部章节</a>"""
            } else catalogue(1) + catalogue(2)
            Jsoup.parse(html, request.url)
        }
        assertEquals(listOf(book.bookUrl, "$base/catalog/full"), requests)
        assertEquals(listOf("第1章", "第2章"), chapters.map { it.title })
    }

    @Test fun workingRuleDoesNotAppendUnrelatedHeuristicChapterLinks() {
        val (_, chapters) = loadBookV36(source, book) { _, request ->
            Jsoup.parse(
                catalogue(1) + """<aside><a href="/read/2/99.html">第99章 其他书推荐</a></aside>""",
                request.url,
            )
        }
        assertEquals(listOf("$base/read/1/1.html"), chapters.map { it.url })
    }

    @Test fun secondCataloguePageFailureFailsThenRetryReturnsWholeCatalogue() {
        var failSecondPage = true
        val failure = IOException("fixture: second page unavailable")
        val fetch: (BookSourceV36, SourceRequestV36) -> org.jsoup.nodes.Document = { _, request ->
            if (request.url == "$base/catalog/2" && failSecondPage) throw failure
            Jsoup.parse(if (request.url == book.bookUrl) catalogue(1, 2) else catalogue(2), request.url)
        }
        val failed = runCatching { loadBookV36(source, book, fetch) }
        assertTrue("A failed next page must not look like a complete one-chapter book", failed.isFailure)
        assertTrue(generateSequence(failed.exceptionOrNull()) { it.cause }.any { it === failure })
        failSecondPage = false
        assertEquals(listOf("第1章", "第2章"), loadBookV36(source, book, fetch).second.map { it.title })
    }

    @Test fun nonterminalFortiethCataloguePageFailsWithoutFetchingPageFortyOne() {
        val requests = mutableListOf<String>()
        val result = runCatching {
            loadBookV36(source, book) { _, request ->
                requests += request.url
                val number = if (request.url == book.bookUrl) 1 else request.url.substringAfterLast('/').toInt()
                Jsoup.parse(catalogue(number, number + 1), request.url)
            }
        }
        assertTrue("The page bound must not silently truncate a book", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("40"))
        assertEquals(40, requests.size)
    }

    @Test fun terminalFortiethCataloguePageRemainsSupported() {
        val (_, chapters) = loadBookV36(source, book) { _, request ->
            val number = if (request.url == book.bookUrl) 1 else request.url.substringAfterLast('/').toInt()
            Jsoup.parse(catalogue(number, (number + 1).takeIf { number < 40 }), request.url)
        }
        assertEquals(40, chapters.size)
        assertEquals("第40章", chapters.last().title)
    }

    @Test fun contentCleanupIsPreservedWhenAddingTheDefaultAttribute() {
        val chapter = OnlineChapterV36("Chapter", "$base/read/1/1.html")
        val rules = mapOf(
            "@css:#content@html##广告" to "正文",
            "@css:#content@html##广告##" to "正文",
            "@css:#content@html##广告##替换" to "正文替换",
            "@css:#content@text##广告##" to "正文",
            "@css:#content##广告##" to "正文",
            "id.content##广告##" to "正文",
        )
        rules.forEach { (rule, expected) ->
            val text = loadChapterTextV36(source.copy(contentText = rule), chapter, setOf(chapter.url)) { _, request ->
                Jsoup.parse("<div id=content>正文广告</div>", request.url)
            }
            assertEquals(rule, expected, text)
        }
    }
}
