package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceTocHeuristicsV39Test {
    @Test
    fun findsExplicitCatalogueLink() {
        val doc = Jsoup.parse(
            """
            <html><body>
              <a href="/book/44220.html">书页</a>
              <a href="/catalog/44220.html">全部章节</a>
            </body></html>
            """.trimIndent(),
            "https://101kks.com/book/44220.html",
        )
        assertEquals("https://101kks.com/catalog/44220.html", heuristicTocUrlV39(doc))
    }

    @Test
    fun extractsDenseTxtChapterListInOrder() {
        val latest = """
            <div class="latest">
              <a href="/txt/44220/9003.html">第3章 最新</a>
              <a href="/txt/44220/9002.html">第2章 最新</a>
            </div>
        """.trimIndent()
        val full = (1..6).joinToString("") { n ->
            """<li><a href="/txt/44220/${9000 + n}.html">第${n}章 正文</a></li>"""
        }
        val doc = Jsoup.parse(
            """<html><body>$latest<ul id="chapter-list">$full</ul></body></html>""",
            "https://101kks.com/book/44220.html",
        )
        val chapters = heuristicChapterLinksV39(doc, "https://101kks.com/book/44220.html")
        assertEquals(6, chapters.size)
        assertEquals("第1章 正文", chapters.first().title)
        assertEquals("https://101kks.com/txt/44220/9001.html", chapters.first().url)
        assertEquals("第6章 正文", chapters.last().title)
    }

    @Test
    fun recognizes101KanshuTxtPattern() {
        assertTrue(isLikelyChapterUrlV39("https://101kks.com/txt/4778/12189973.html"))
    }
}
