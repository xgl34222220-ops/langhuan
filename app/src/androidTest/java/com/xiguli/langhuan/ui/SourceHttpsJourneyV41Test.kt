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

    @Test fun publicHttpsCategoryAndRankFollowObservedNextPagesThenOpenTheirBooks() {
        val base = InstrumentationRegistry.getArguments().getString("sourceFixtureBase")
        assumeNotNull(base)
        val source = BookSourceV36(
            id = "https-discovery", name = "发现分页回归专用", baseUrl = base!!,
            infoName = "h1@text", infoTocUrl = ".catalogue@href",
            tocList = "#chapters a", tocName = "@text", tocUrl = "@href", contentText = ".content@html",
            exploreUrl = "测试分类::category-1.html&&测试月榜::rank-1.html",
            exploreList = ".book", exploreName = "h2@text", exploreBookUrl = "a@href",
        )
        val sections = sourceDiscoveriesV41(source)
        assertEquals(listOf("测试分类", "测试月榜"), sections.map { it.label })
        for (section in sections) {
            val first = discoverPageV41(source, section)
            assertEquals("测试航行记", first.books.single().name)
            assertTrue(first.hasMore)
            assertNotNull(first.nextUrl)
            val second = discoverPageV41(source, section, 2, first.nextUrl)
            assertEquals("测试归港记", second.books.single().name)
            assertFalse(second.hasMore)
            assertNotEquals(first.books.single().bookUrl, second.books.single().bookUrl)
            val (book, chapters) = loadBookV36(source, second.books.single())
            assertEquals("测试归港记", book.name)
            assertEquals(1, chapters.size)
            val text = loadChapterTextV36(source, chapters.single(), chapters.map { it.url }.toSet())
            assertTrue(text.contains("归航的船停在港口"))
            assertTrue(text.contains("沿着岸边的小路走向家门"))
        }
    }
}
