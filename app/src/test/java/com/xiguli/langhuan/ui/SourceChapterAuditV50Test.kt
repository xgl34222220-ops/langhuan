package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChapterAuditV50Test {
    private val source = BookSourceV36("fixture", "合成书站", "https://example.com", contentText = "@css:#content@html", contentNext = "@css:a.next@href")
    private val book = OnlineBookV36("fixture", "合成书站", "半島：我們的故事劇名未定", "", "", "", "", "https://example.com/book/7")
    private val chapter = OnlineChapterV36("第1章 初遇", "https://example.com/read/7/1")

    private fun page(
        body: String = "他关掉电视，窗外的雨还在下。",
        name: String? = book.name,
        title: String? = chapter.title,
        extra: String = "",
        next: String? = null,
    ): String = """
        <html><head>
        ${name?.let { "<meta property='og:novel:book_name' content='$it'>" }.orEmpty()}
        ${title?.let { "<meta property='og:novel:chapter_name' content='$it'>" }.orEmpty()}
        $extra</head><body><div id='content'>$body</div>
        ${next?.let { "<a class='next' href='$it'>下一页</a>" }.orEmpty()}
        </body></html>
    """.trimIndent()

    private fun audit(html: String, selectedSource: BookSourceV36 = source): OnlineChapterAuditV50 =
        loadChapterAuditV50(selectedSource, book, chapter, setOf(chapter.url)) { _, request -> Jsoup.parse(html, request.url) }

    private fun assertRejected(expected: String, block: () -> Any) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("Expected rejection containing $expected, got $failure", failure is IllegalStateException && failure.message.orEmpty().contains(expected))
    }

    @Test fun novelOpeningCanQuoteJtbcNewsAndUseArticleMetadata() {
        val quote = "JTBC 新聞：娛樂圈最新消息。她看完报道，把遥控器搁在窗边。"
        val result = audit(page(quote, extra = "<meta property='og:type' content='article'><title>JTBC 新聞與娛樂報道</title>"))
        assertEquals(quote, result.text)
        assertTrue(result.proof.identityVerified)
        assertEquals(1, result.proof.pagesRead)
        assertEquals(chapter.url, result.proof.chapterUrl)
        assertTrue(result.proof.warnings.isEmpty())
    }

    @Test fun newsWordsWithoutIdentityAreUnknownAndStillReadable() {
        val body = "JTBC 新闻，娱乐消息，这都是小说人物正在看的节目。"
        val result = audit(page(body, name = null, title = null, extra = "<meta property='og:type' content='article'>"))
        assertEquals(body, result.text)
        assertEquals(SourceIdentityStateV50.UNKNOWN, result.proof.bookIdentity)
        assertEquals(SourceIdentityStateV50.UNKNOWN, result.proof.chapterIdentity)
        assertEquals(2, result.proof.warnings.size)
    }

    @Test fun explicitDifferentBookIsRejectedWithoutLeakingValues() {
        val secret = "private-original-content"
        val error = runCatching { audit(page(secret, name = "明确另一本书")) }.exceptionOrNull()!!
        assertTrue(error.message.orEmpty().contains("书籍身份不符"))
        assertFalse(error.message.orEmpty().contains(secret))
        assertFalse(error.message.orEmpty().contains("明确另一本书"))
    }

    @Test fun explicitDifferentChapterNameOrNumberIsRejected() {
        assertRejected("章节身份不符") { audit(page(title = "第2章 重逢")) }
        assertRejected("章节身份不符") { audit(page(title = "第1章 另一段故事")) }
        assertRejected("章节身份不符") { audit(page(title = "不相干的独立章名")) }
    }

    @Test fun conflictingNumberInTitleOrHeadingIsRejected() {
        assertRejected("章节号不符") { audit(page(title = null, extra = "<title>第2章 重逢 - 合成书站</title>")) }
        assertRejected("章节号不符") { audit(page(title = null).replace("<body>", "<body><h1>第2章 重逢</h1>")) }
    }

    @Test fun quotedHeadingInsideProseDoesNotChangeChapterIdentity() {
        val result = audit(page("<h1>第2章 电视专题节目</h1><p>他继续翻看小说的第一页。</p>"))
        assertTrue(result.text.contains("电视专题节目"))
        assertTrue(result.proof.identityVerified)
    }

    @Test fun chineseChapterNumbersAndPaginationSuffixCanMatch() {
        val result = audit(page(title = "第一章 初遇（2/3）"))
        assertTrue(result.proof.identityVerified)
    }

    @Test fun latestChapterMetadataIsNotTheCurrentChapterIdentity() {
        val result = audit(page(title = null, extra = "<meta property='og:novel:latest_chapter_name' content='第99章 尾声'>"))
        assertEquals(SourceIdentityStateV50.UNKNOWN, result.proof.chapterIdentity)
    }

    @Test fun bareChapterOrdinalMismatchIsRejectedWithoutRequiringDiChapterPrefix() {
        for ((expected, actual) in listOf("1、初遇" to "2、重逢", "1. 初遇" to "2. 重逢", "一、初遇" to "二、重逢")) {
            val selected = chapter.copy(title = expected)
            val html = page(title = null, extra = "<title>$actual - 合成书站</title>")
            assertRejected("章节号不符") {
                loadChapterAuditV50(source, book, selected, setOf(selected.url)) { _, request -> Jsoup.parse(html, request.url) }
            }
        }
    }

    @Test fun matchingBareOrdinalNovelChapterRemainsReadable() {
        val selected = chapter.copy(title = "1、咖啡店没有剧本")
        val html = page(title = "1、咖啡店没有剧本", body = "这是合成小说里的咖啡店场景。")
        val result = loadChapterAuditV50(source, book, selected, setOf(selected.url)) { _, request -> Jsoup.parse(html, request.url) }
        assertTrue(result.proof.identityVerified)
        assertTrue(result.text.contains("咖啡店"))
    }

    @Test fun veryShortRealChapterIsReadableWithoutArtificialMinimum() {
        val result = audit(page("完。"))
        assertEquals("完。", result.text)
        assertTrue(result.proof.identityVerified)
        val unknown = audit(page("完。", name = null, title = null))
        assertEquals("完。", unknown.text)
        assertFalse(unknown.proof.identityVerified)
    }

    @Test fun navigationAndLinkedChapterListsAreNotBodySuccess() {
        assertRejected("导航或链接目录") { audit("<nav id=content>上一章 目录 下一章</nav>") }
        assertRejected("导航或链接目录") { audit("<ul id=content><li><a href='/read/7/1'>第1章 初遇</a></li></ul>") }
        assertRejected("导航或链接目录") {
            audit("<div id=content>" + (1..36).joinToString("") { "<a href='/read/7/$it'>第${it}章 标题</a>" } + "</div>")
        }
        assertRejected("导航或链接目录") { audit("<nav><span id=content>目录 下一章</span></nav>") }
    }

    @Test fun ordinaryListAndNarrativeWithLinksRemainReadable() {
        assertEquals("一盏灯\n一封信", audit("<ul id=content><li>一盏灯</li><li>一封信</li></ul>").text)
        val result = audit(page("他依次打开三封信，却发现上面的字迹早已模糊，雨水淹没了回忆。".repeat(8) + "<a href='/a'>甲</a><a href='/b'>乙</a><a href='/c'>丙</a>"))
        assertTrue(result.text.contains("字迹"))
    }

    @Test fun alternativeAndConcatenatedRulesInspectTheirActualSelection() {
        val html = "<nav id=menu>上一章 目录 下一章</nav><div id=content>正文</div>"
        assertEquals("正文", audit(html, source.copy(contentText = "@css:#missing@html||@css:#content@html")).text)
        assertRejected("导航或链接目录") { audit(html, source.copy(contentText = "@css:#menu@html||@css:#content@html")) }
        assertRejected("导航或链接目录") { audit(html, source.copy(contentText = "@css:#menu@html&&@css:#menu@text")) }
    }

    @Test fun emptyExtractionIsNotSuccessful() {
        assertRejected("未提取到可读文字") { audit(page("")) }
    }

    @Test fun selfLoopIncludingFragmentsIsIncomplete() {
        assertRejected("分页出现循环") { audit(page(next = "${chapter.url}#body")) }
    }

    @Test fun twoPageLoopDoesNotReturnPartialText() {
        var requests = 0
        assertRejected("分页出现循环") {
            loadChapterAuditV50(source, book, chapter, setOf(chapter.url)) { _, request ->
                requests++
                Jsoup.parse(page(next = if (requests == 1) "${chapter.url}/2" else chapter.url), request.url)
            }
        }
        assertEquals(2, requests)
    }

    @Test fun redirectBackToReadPageIsIncomplete() {
        var requests = 0
        assertRejected("重定向到已读页面") {
            loadChapterAuditV50(source, book, chapter, setOf(chapter.url)) { _, request ->
                requests++
                Jsoup.parse(page(next = "${chapter.url}/2"), if (requests == 1) request.url else chapter.url)
            }
        }
    }

    @Test fun twelfthPageWithNextIsIncompleteButTwelfthFinalPageWorks() {
        fun read(endAtTwelve: Boolean): OnlineChapterAuditV50 {
            var requests = 0
            return loadChapterAuditV50(source, book, chapter, setOf(chapter.url)) { _, request ->
                requests++
                Jsoup.parse(page(next = if (endAtTwelve && requests == 12) null else "${chapter.url}/page${requests + 1}"), request.url)
            }
        }
        assertRejected("超过 12 页") { read(false) }
        assertEquals(12, read(true).proof.pagesRead)
    }

    @Test fun linkToOtherTocChapterIsNormalBoundaryWithoutFetchingIt() {
        var requests = 0
        val other = "https://example.com/read/7/2"
        val result = loadChapterAuditV50(source, book, chapter, setOf(chapter.url, "$other#top")) { _, request ->
            requests++
            Jsoup.parse(page(next = other), request.url)
        }
        assertEquals(1, requests)
        assertEquals(1, result.proof.pagesRead)
    }

    @Test fun redirectToOtherTocChapterIsRejected() {
        val other = "https://example.com/read/7/2"
        assertRejected("跳转到目录中的其他章节") {
            loadChapterAuditV50(source, book, chapter, setOf(chapter.url, other)) { _, _ -> Jsoup.parse(page(), other) }
        }
    }

    @Test fun everyContinuationPageMustPassIdentityCheck() {
        var requests = 0
        assertRejected("章节身份不符") {
            loadChapterAuditV50(source, book, chapter, setOf(chapter.url)) { _, request ->
                requests++
                Jsoup.parse(if (requests == 1) page(next = "${chapter.url}/2") else page(title = "第2章 跨章内容"), request.url)
            }
        }
        assertEquals(2, requests)
    }

    @Test fun missingIdentityOnSecondPageDoesNotClaimFullyVerified() {
        var requests = 0
        val result = loadChapterAuditV50(source, book, chapter, setOf(chapter.url)) { _, request ->
            requests++
            Jsoup.parse(if (requests == 1) page(next = "${chapter.url}/2") else page(name = null, title = null), request.url)
        }
        assertEquals(2, result.proof.pagesRead)
        assertFalse(result.proof.identityVerified)
        assertEquals(2, result.proof.warnings.size)
        assertTrue(result.proof.warnings.all { it.contains("第 2 页") })
    }

    @Test fun legacySignatureKeepsRuleCleanupAndShortTextCompatibility() {
        assertEquals("正文", loadChapterTextV36(source.copy(contentText = "id.content##广告"), chapter, setOf(chapter.url)) { _, request ->
            Jsoup.parse("<div id=content>正文广告</div>", request.url)
        })
    }
}
