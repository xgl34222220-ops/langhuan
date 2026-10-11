package com.xiguli.langhuan.ui

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V97: 书城 list covers — lazy-load fallback, relative URL resolution, detail-page backfill, disk cache. */
class OnlineListCoverV97Test {
    private val page = "https://www.qiushu.example/top/allvisit_1.html"
    private val tinyGif = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0, 1, 1, 0)

    private fun source(infoCover: String = "", infoInit: String = "") = BookSourceV36(
        id = "qiushu", name = "求书网", baseUrl = "https://www.qiushu.example",
        searchUrl = "https://www.qiushu.example/s?q={{key}}", searchList = "class.item", searchName = "tag.h3@text",
        searchBookUrl = "tag.a@href", searchCover = "tag.img@src", infoCover = infoCover, infoInit = infoInit,
    )

    private fun book(cover: String = "", url: String = "https://www.qiushu.example/book/1/") = OnlineBookV36(
        sourceId = "qiushu", sourceName = "求书网", name = "斗破苍穹", author = "天蚕土豆",
        cover = cover, intro = "", latest = "", bookUrl = url,
    )

    // ---- usable / placeholder ------------------------------------------------------------------

    @Test fun placeholdersAndTextAreNotCovers() {
        listOf(
            "", "  ", "/static/images/loading.gif", "https://x.example/img/lazy.png", "/images/nocover.jpg",
            "/img/no-image.png", "/blank.gif", tinyGif, "javascript:void(0)", "#", "斗破苍穹", "/book/1.html",
            "https://x.example/cover.svg", "{{book.cover}}",
        ).forEach { assertFalse(it, coverLooksUsableV97(it)) }
    }

    @Test fun realAddressesAreCovers() {
        listOf(
            "https://img.example.net/1.jpg", "//img.example.net/2.webp", "/files/article/image/1/1s.jpg",
            "cover/3.png", "https://api.example.com/cover?id=7",
            "https://img.example.net/4.jpg,{\"headers\":{\"Referer\":\"https://www.qiushu.example/\"}}",
        ).forEach { assertTrue(it, coverLooksUsableV97(it)) }
    }

    // ---- URL resolution ------------------------------------------------------------------------

    @Test fun relativeCoversResolveAgainstThePage() {
        assertEquals("https://www.qiushu.example/files/1s.jpg", resolveCoverUrlV97(page, "/files/1s.jpg"))
        assertEquals("https://www.qiushu.example/top/c/2.jpg", resolveCoverUrlV97(page, "c/2.jpg"))
        assertEquals("https://www.qiushu.example/c/3.jpg", resolveCoverUrlV97(page, "../c/3.jpg"))
        assertEquals("https://img.example.net/4.jpg", resolveCoverUrlV97(page, "//img.example.net/4.jpg"))
        assertEquals("http://img.example.net/5.jpg", resolveCoverUrlV97("http://www.qiushu.example/", "//img.example.net/5.jpg"))
        assertEquals("https://cdn.example.org/6.jpg", resolveCoverUrlV97(page, "https://cdn.example.org/6.jpg"))
        // Legado header options survive resolution.
        assertEquals(
            "https://www.qiushu.example/7.jpg,{\"headers\":{\"Referer\":\"x\"}}",
            resolveCoverUrlV97(page, "/7.jpg,{\"headers\":{\"Referer\":\"x\"}}"),
        )
        // Multi-line rule output keeps the first address.
        assertEquals("https://www.qiushu.example/8.jpg", resolveCoverUrlV97(page, "\n/8.jpg\n/9.jpg"))
    }

    // ---- lazy-load fallback --------------------------------------------------------------------

    @Test fun placeholderRuleResultFallsBackToLazyAttributes() {
        val doc = Jsoup.parse(
            """
            <div class="item"><img src="/static/loading.gif" data-original="/files/1s.jpg"><h3>甲</h3></div>
            <div class="item"><img src="$tinyGif" data-src="//img.example.net/2.webp"><h3>乙</h3></div>
            <div class="item"><img data-lazy-src="cover/3.jpg"><h3>丙</h3></div>
            <div class="item"><img lazy="/4.jpg" src=""><h3>丁</h3></div>
            <div class="item"><img data-srcset="/5.jpg 1x, /5@2x.jpg 2x"><h3>戊</h3></div>
            <div class="item"><h3>己</h3></div>
            """.trimIndent(),
            page,
        )
        val items = doc.select("div.item")
        val covers = items.map { listCoverV97(it, ruleStringV36(it, "tag.img@src"), page) }
        assertEquals(
            listOf(
                "https://www.qiushu.example/files/1s.jpg",
                "https://img.example.net/2.webp",
                "https://www.qiushu.example/top/cover/3.jpg",
                "https://www.qiushu.example/4.jpg",
                "https://www.qiushu.example/5.jpg",
                "",
            ),
            covers,
        )
    }

    @Test fun usableRuleResultWinsOverLazyAttributes() {
        val item = Jsoup.parse("<div><img src=\"/real.jpg\" data-original=\"/other.jpg\"></div>", page).selectFirst("div")!!
        assertEquals("https://www.qiushu.example/real.jpg", listCoverV97(item, "/real.jpg", page))
        assertEquals("https://www.qiushu.example/real.jpg", listCoverV97(item, ruleStringV36(item, "tag.img@src"), page))
    }

    @Test fun searchRowsUseTheFallback() {
        val html = """
            <html><body>
              <div class="item"><h3>斗破苍穹</h3><a href="/book/1/">斗破苍穹</a><img src="/images/loading.gif" data-original="/files/1s.jpg"></div>
              <div class="item"><h3>吞噬星空</h3><a href="/book/2/">吞噬星空</a></div>
            </body></html>
        """.trimIndent()
        val books = searchSourceV36(source(), "斗") { _, _ -> Jsoup.parse(html, page) }
        assertEquals(2, books.size)
        assertEquals("https://www.qiushu.example/files/1s.jpg", books[0].cover)
        assertEquals("", books[1].cover)
    }

    // ---- detail-page backfill ------------------------------------------------------------------

    @Test fun detailCoverUsesInfoCoverThenMetadata() {
        val detail = Jsoup.parse(
            """
            <html><head><meta property="og:image" content="/og/1.jpg"></head>
            <body><div id="fmimg"><img src="/static/loading.gif" data-original="/files/1.jpg" alt="斗破苍穹"></div></body></html>
            """.trimIndent(),
            "https://www.qiushu.example/book/1/",
        )
        // infoCover returns a placeholder → og:image
        assertEquals("https://www.qiushu.example/og/1.jpg", detailCoverFromPageV97(source(infoCover = "id.fmimg@tag.img@src"), "斗破苍穹", detail))
        // infoCover reads the lazy attribute directly
        assertEquals(
            "https://www.qiushu.example/files/1.jpg",
            detailCoverFromPageV97(source(infoCover = "id.fmimg@tag.img@data-original"), "斗破苍穹", detail),
        )
        // no rule, no metadata → an image named after the book
        val bare = Jsoup.parse(
            "<div><img src=\"/logo.png\" alt=\"求书网\"><img data-src=\"/files/2.jpg\" alt=\"斗破苍穹\"></div>",
            "https://www.qiushu.example/book/1/",
        )
        assertEquals("https://www.qiushu.example/files/2.jpg", detailCoverFromPageV97(source(), "斗破苍穹", bare))
        // nothing usable → blank (the row keeps its placeholder)
        assertEquals("", detailCoverFromPageV97(source(), "斗破苍穹", Jsoup.parse("<p>无封面</p>", "https://www.qiushu.example/book/1/")))
    }

    @Test fun fetchDetailCoverReadsTheBookPage() {
        var requested = ""
        val cover = fetchDetailCoverV97(source(infoCover = "id.fmimg@tag.img@src"), book()) { _, request ->
            requested = request.url
            Jsoup.parse("<div id=\"fmimg\"><img src=\"/files/1s.jpg\"></div>", request.url)
        }
        assertEquals("https://www.qiushu.example/book/1/", requested)
        assertEquals("https://www.qiushu.example/files/1s.jpg", cover)
    }

    @Test fun detailLookupsAreRememberedAndFailuresKeepThePlaceholder() = runBlocking {
        val target = book(url = "https://www.qiushu.example/book/v97-${System.nanoTime()}/")
        var calls = 0
        val first = OnlineDetailCoversV97.lookup(source(), target) { _, _ -> calls++; "https://img.example.net/9.jpg" }
        val second = OnlineDetailCoversV97.lookup(source(), target) { _, _ -> calls++; "https://img.example.net/other.jpg" }
        assertEquals("https://img.example.net/9.jpg", first)
        assertEquals(first, second)
        assertEquals(1, calls)
        assertEquals(first, rowCoverV97(target))

        val failing = book(url = "https://www.qiushu.example/book/v97-fail-${System.nanoTime()}/")
        val none = OnlineDetailCoversV97.lookup(source(), failing) { _, _ -> throw java.io.IOException("403") }
        assertEquals("", none)
        assertEquals("", rowCoverV97(failing))
        // A usable list cover is shown as is.
        assertEquals("https://img.example.net/1.jpg", rowCoverV97(book(cover = "https://img.example.net/1.jpg")))
    }

    // ---- disk cache ----------------------------------------------------------------------------

    @Test fun diskCacheStoresImagesAndEvictsLeastRecentlyUsed() {
        val dir = Files.createTempDirectory("online-covers").toFile()
        val image = jpeg + ByteArray(400)
        val cache = OnlineCoverDiskCacheV97(dir, maxBytes = (image.size * 2 + 10).toLong())
        assertNull(cache.get("https://a/1.jpg"))
        cache.put("https://a/1.jpg", image)
        cache.put("https://a/2.jpg", image)
        dir.listFiles()!!.forEach { it.setLastModified(1_000L) }
        assertNotNull(cache.get("https://a/1.jpg")) // touch 1 → 2 is now least recently used
        cache.put("https://a/3.jpg", image)
        assertNotNull(cache.get("https://a/1.jpg"))
        assertNull(cache.get("https://a/2.jpg"))
        assertNotNull(cache.get("https://a/3.jpg"))
        assertTrue(cache.sizeBytes() <= image.size * 2 + 10)
        // Non-image bytes are never served.
        cache.put("https://a/bad.jpg", ByteArray(64) { 'x'.code.toByte() })
        assertNull(cache.get("https://a/bad.jpg"))
        dir.deleteRecursively()
    }
}
