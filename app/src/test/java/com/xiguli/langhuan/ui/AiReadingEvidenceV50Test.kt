package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.*
import org.junit.Test

/** Invented local HTML/model responses, including a fictional novel quoting a news headline. */
class AiReadingEvidenceV50Test {
    private class Fixture(
        val bookHtml: String = "<h2>全部章节</h2><div id='chapters'>${links(1..36)}</div>",
        val chapterHtml: String = chapterPage(),
        val tocRule: String = TOC,
        val failSeparateToc: Boolean = false,
    ) {
        val prompts = mutableListOf<PromptBundle>()
        val requests = mutableListOf<String>()
        var steps = emptyList<AiSourceStepV37>()
        private val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("No structured call")
            override suspend fun generateText(prompt: PromptBundle): String {
                prompts += prompt
                return when {
                    prompt.user.contains("搜索结果页") -> """{"searchList":"li.book","searchName":"a@text","searchBookUrl":"a@href"}"""
                    prompt.user.contains("书籍详情页") -> tocRule
                    prompt.user.contains("章节正文页") -> """{"contentText":"#content@html"}"""
                    else -> error("Unexpected prompt")
                }
            }
        }
        private fun fetch(source: BookSourceV36, request: SourceRequestV36): Document {
            requests += request.url
            val html = when (request.url.removePrefix(BASE).substringBefore('?').ifEmpty { "/" }) {
                "/" -> "<title>离线抽样站</title><form action='/search'><input name='q'></form>"
                "/search" -> "<ul><li class='book'><a href='/book/1'>$BOOK</a></li></ul>"
                "/book/1" -> "<h1>$BOOK</h1>$bookHtml"
                "/toc" -> if (failSeparateToc) throw SourceHttpStatusExceptionV44(429, BASE, "测试限流", 60_000) else bookHtml
                "/read/1" -> chapterHtml
                else -> error("Unexpected request ${request.url}")
            }
            return Jsoup.parse(html, request.url)
        }
        suspend fun build() = BookSourceAiBuilderV37(gateway, { steps = it }, ::fetch).build(BASE, BOOK)
        fun count(stage: String) = prompts.count { it.user.startsWith("任务：为“$stage”") }
    }

    @Test fun reportKeepsActualBookChapterUrlAndThirtySixCanBeACompleteShortBook() = runBlocking {
        val f = Fixture()
        val report = f.build()
        assertEquals(36, report.chapterCount)
        assertEquals(BOOK, report.bookName)
        assertEquals("第1章 开场", report.sampleChapter)
        assertEquals("$BASE/read/1", report.sampleChapterUrl)
        assertEquals("$BASE/book/1", report.sampleBookUrl)
        assertTrue(report.catalogueProof!!.hasCompletenessEvidence)
        assertTrue(report.chapterProof!!.identityVerified)
        assertTrue(report.sample.contains("JTBC"))
        assertTrue("Fiction quoting news must not be silently deleted", report.sample.contains("小說人物在讀新聞"))
        assertTrue(sourceSampleScopeV50(report.chapterProof).contains("未逐章验证"))
        assertEquals(1, f.count("章节正文页"))
    }

    @Test fun latestOnlyFailureIsNeverPresentedAsAThirtySixChapterSuccess() = runBlocking {
        val f = Fixture(bookHtml = "<dl><dt>最新章节</dt>${links(1000..1035)}</dl>")
        val result = runCatching { f.build() }
        assertTrue(result.isFailure)
        assertEquals(2, f.count("书籍详情页"))
        assertEquals(0, f.count("章节正文页"))
        assertFalse(f.prompts.last().user.contains("没有取到任何章节"))
        assertTrue(f.prompts.last().user.contains("未通过目录检查"))
        assertEquals(false, f.steps.last().ok)
        assertTrue(f.steps.last().completed)
        assertEquals(result.exceptionOrNull()!!.message, f.steps.last().detail)
    }

    @Test fun aWrongBookCannotPassByHavingMoreThanSixtyCharacters() = runBlocking {
        val f = Fixture(chapterHtml = chapterPage("完全不同的书"))
        val result = runCatching { f.build() }
        assertTrue(result.isFailure)
        assertEquals(2, f.count("章节正文页"))
        assertTrue(f.prompts.last().user.contains("未通过正文检查"))
        assertFalse(f.prompts.last().user.contains("正文一般是页面里字数最多"))
        assertEquals(false, f.steps.last().ok)
        assertTrue(f.steps.last().completed)
    }

    @Test fun missingIdentityAndCompletenessEvidenceAreReportedAsUnknown() = runBlocking {
        val f = Fixture(bookHtml = "<div id='chapters'>${links(1..36)}</div>",
            chapterHtml = "<div id='content'>${BODY}</div>")
        val report = f.build()
        assertFalse(report.catalogueProof!!.hasCompletenessEvidence)
        assertFalse(report.chapterProof!!.identityVerified)
        assertTrue(sourceCatalogueSummaryV50(36, report.catalogueProof).contains("完整性待核实"))
        assertTrue(sourceSampleScopeV50(report.chapterProof).contains("缺少"))
        assertTrue(report.readingWarnings.isNotEmpty())
    }

    @Test fun catalogue429StopsBeforeAnotherModelCallOrNewRequest() = runBlocking {
        val f = Fixture(bookHtml = "<a id='toc' href='/toc'>目录</a>",
            tocRule = """{"infoTocUrl":"#toc@href","tocList":"#chapters a","tocName":"text","tocUrl":"href"}""", failSeparateToc = true)
        val result = runCatching { f.build() }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("429"))
        assertEquals(1, f.count("书籍详情页"))
        assertEquals(0, f.count("章节正文页"))
        assertEquals(1, f.requests.count { it == "$BASE/toc" })
        assertEquals("$BASE/toc", f.requests.last())
        assertTrue(f.steps.last().completed)
    }

    @Test fun anIdentifiedShortChapterDoesNotNeedAnUnnecessaryModelRetry() = runBlocking {
        val f = Fixture(chapterHtml = chapterPage().replace(BODY, "完。"))
        val report = f.build()
        assertEquals("完。", report.sample)
        assertTrue(report.chapterProof!!.identityVerified)
        assertEquals(1, f.count("章节正文页"))
    }

    @Test fun unidentifiedTinyExtractionStillFailsWithinTwoCalls() = runBlocking {
        val f = Fixture(chapterHtml = "<div id='content'>完。</div>")
        val failure = runCatching { f.build() }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals(2, f.count("章节正文页"))
        assertTrue(failure!!.message.orEmpty().contains("缺少书名或章名"))
        assertEquals(false, f.steps.last().ok)
    }

    @Test fun reportHelpersNeverTurnMissingProofIntoFullSiteSuccess() {
        assertTrue(sourceCatalogueSummaryV50(36, null).contains("完整性待核实"))
        assertTrue(sourceSampleScopeV50(null).contains("未逐章验证"))
    }

    companion object {
        private const val BASE = "https://books.example"
        private const val BOOK = "半島：原創測試故事"
        private const val TOC = """{"infoName":"h1@text","tocList":"#chapters a","tocName":"text","tocUrl":"href"}"""
        private val BODY = "「JTBC新劇官宣！」小說人物在讀新聞，隨即把手機放回桌上。".repeat(8)
        private fun chapterPage(name: String = BOOK) = "<meta property='og:novel:book_name' content='$name'><meta property='og:novel:chapter_name' content='第1章 开场'><h1>第1章 开场</h1><div id='content'><p>$BODY</p></div>"
        private fun links(range: IntRange) = range.joinToString("") { "<a href='/read/$it'>第${it}章 开场</a>" }
    }
}
