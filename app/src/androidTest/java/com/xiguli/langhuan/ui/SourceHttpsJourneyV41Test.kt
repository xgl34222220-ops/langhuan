package com.xiguli.langhuan.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeNotNull

/** Exercises real public HTTPS with our own fixed, explicitly test-only HTML content. */
class SourceHttpsJourneyV41Test {
    @Test fun publicHttpsSearchDiscoveryCatalogueAndTextUseTheSameSafeEngine() {
        val base = InstrumentationRegistry.getArguments().getString("sourceFixtureBase")
        assumeNotNull(base) // Explicit opt-in locally; CI always supplies the immutable commit URL.
        val source = BookSourceV36(
            id = "https-fixture", name = "回归测试专用", baseUrl = base!!,
            searchUrl = "search.html?key={{key}}", searchList = ".book", searchName = "h2@text",
            searchAuthor = ".author@text", searchIntro = ".intro@text", searchBookUrl = "a@href",
            infoName = "h1@text", infoIntro = ".intro@text", infoTocUrl = ".catalogue@href",
            tocList = "#chapters a", tocName = "@text", tocUrl = "@href", contentText = ".content@html",
            exploreUrl = "测试分类::search.html", exploreList = ".book", exploreName = "h2@text", exploreBookUrl = "a@href",
        )
        val found = searchSourceV36(source, "测试航行记")
        assertEquals(1, found.size)
        assertEquals("测试航行记", found.single().name)
        assertEquals("琅嬛自动测试", found.single().author)
        val discovered = discoverBooksV41(source, sourceDiscoveriesV41(source).single())
        assertEquals(found.single().bookUrl, discovered.single().bookUrl)
        val (book, chapters) = loadBookV36(source, found.single())
        assertEquals("测试航行记", book.name)
        assertEquals(1, chapters.size)
        val content = loadChapterTextV36(source, chapters.single(), chapters.map { it.url }.toSet())
        assertTrue(content.contains("远处亮起一盏灯"))
        assertTrue(content.contains("船慢慢靠岸"))
        assertFalse(content.contains("<p>"))
    }
}
