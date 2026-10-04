package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

/** Uses the real builder, HTML rule engine and bounded navigation. No provider key or network bypass. */
class AiDiscoveryJourneyV43Test {
    private val base = "https://books.example"
    private val requests = ArrayList<String>()
    private val steps = ArrayList<AiSourceStepV37>()
    private var discovery = "分类::${base}/category?page=1&&月榜::${base}/ranking?page=1"
    private var failRank = false
    private var cancelDiscovery = false
    private var brokenSecond = false
    private val gateway = object : AiGateway {
        override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("Structured path is not used")
        override suspend fun generateText(prompt: PromptBundle): String = when {
            prompt.user.contains("识别网站已有的发现分类") -> {
                if (cancelDiscovery) throw CancellationException("fixture cancelled")
                BookSourceJsonV36.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.buildJsonObject {
                    put("exploreUrl", kotlinx.serialization.json.JsonPrimitive(discovery))
                })
            }
            prompt.user.contains("共用的发现书目规则") -> """{"exploreList":"@css:li.book","exploreName":"@css:a@text","exploreBookUrl":"@css:a@href"}"""
            prompt.user.contains("搜索结果页") -> """{"searchList":"@css:li.book","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
            prompt.user.contains("书籍详情页") -> """{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""
            prompt.user.contains("章节正文页") -> """{"contentText":"@css:#content@html"}"""
            else -> error("Unexpected prompt: ${prompt.user.take(100)}")
        }
    }
    private fun fetch(source: BookSourceV36, request: SourceRequestV36): org.jsoup.nodes.Document {
        requests += request.url
        val path = request.url.removePrefix(base).ifEmpty { "/" }
        if (path.startsWith("/ranking") && failRank) throw IOException("rank unavailable")
        if (path.contains("page=2") && brokenSecond) throw IOException("page 2 unavailable")
        fun row(number: Int) = "<li class='book'><a href='/book/$number'>示例小说$number</a></li>"
        val html = when {
            path == "/" -> """<title>公开测试书站</title><form action='/search'><input name='q'></form><nav><a href='/category?page=1'>分类</a><a href='/ranking?page=1'>月榜</a></nav>"""
            path.startsWith("/search") -> "<ul>${row(1)}</ul>"
            path == "/category?page=1" || path == "/ranking?page=1" -> "<ul>${row(1)}</ul><a rel='next' href='${path.replace("page=1", "page=2")}'>下一页</a>"
            path == "/category?page=2" || path == "/ranking?page=2" -> "<ul>${row(2)}</ul>"
            path.startsWith("/book/") -> "<h1>示例小说${path.substringAfterLast('/')}</h1><div id='chapters'><a href='/read/1'>第一章</a><a href='/read/2'>第二章</a></div>"
            path.startsWith("/read/") -> "<div id='content'><p>${"这是可控测试夹具的原创正文，用来验证完整阅读链路。".repeat(12)}</p></div>"
            else -> error("No fixture for ${request.url}")
        }
        return Jsoup.parse(html, request.url)
    }
    private suspend fun build() = BookSourceAiBuilderV37(gateway, { steps.clear(); steps.addAll(it) }, fetchDocument = ::fetch).build(base, "示例小说1")

    @Test fun generatedCategoriesAndRanksVerifyPagingAndTheFullReadingJourney() = runBlocking {
        val report = build()
        assertEquals(listOf("分类", "月榜"), report.discoveryLabels)
        assertTrue(report.discoveryWarnings.toString(), report.discoveryWarnings.isEmpty())
        assertTrue(report.source.enabledExplore)
        assertEquals(2, report.discoveryEvidence.size)
        report.discoveryEvidence.forEach { proof ->
            assertEquals(1, proof.bookCount)
            assertEquals(2, proof.chapterCount)
            assertEquals(1, proof.nextPageBookCount)
            assertTrue(proof.nextPageUrl!!.contains("page=2"))
        }
        val categories = sourceDiscoveriesV41(report.source)
        for (category in categories) {
            val first = discoverPageV41(report.source, category, fetchDocument = ::fetch)
            val second = discoverPageV41(report.source, category, 2, first.nextUrl, ::fetch)
            assertEquals("示例小说2", second.books.single().name)
            assertNull(second.nextUrl)
            val (detail, toc) = loadBookV36(report.source, second.books.single(), ::fetch)
            assertEquals("示例小说2", detail.name)
            assertEquals(2, toc.size)
            assertTrue(loadChapterTextV36(report.source, toc.first(), toc.map { it.url }.toSet(), ::fetch).length > 60)
        }
        assertTrue(requests.any { it.startsWith("$base/search?") })
        assertTrue(requests.contains("$base/ranking?page=2"))
        assertTrue(steps.last().ok == true)
    }

    @Test fun missingDiscoveryIsExplicitAndDoesNotInventARank() = runBlocking {
        discovery = ""
        val report = build()
        assertFalse(report.source.enabledExplore)
        assertTrue(report.discoveryLabels.isEmpty())
        assertTrue(report.discoveryWarnings.isEmpty())
        assertTrue(steps.last().detail.contains("未找到"))
        assertTrue(requests.none { it.contains("/ranking") })
    }

    @Test fun inventedRankIsRejectedWithoutFetchingIt() = runBlocking {
        discovery = "总榜::$base/invented-ranking"
        val report = build()
        assertFalse(report.source.enabledExplore)
        assertTrue(report.discoveryWarnings.single().contains("没有网页链接证据"))
        assertTrue(requests.none { it.contains("invented") })
        assertEquals(false, steps.last().ok)
    }

    @Test fun oneBrokenRankKeepsVerifiedCategoryAndReportsPartialFailure() = runBlocking {
        failRank = true
        val report = build()
        assertEquals(listOf("分类"), report.discoveryLabels)
        assertTrue(report.discoveryWarnings.any { it.contains("月榜") && it.contains("失败") })
        assertEquals(false, steps.last().ok)
        assertTrue(report.searchCount > 0)
    }

    @Test fun failedSecondPageDoesNotClaimPaginationWasVerified() = runBlocking {
        brokenSecond = true
        val report = build()
        assertEquals(2, report.discoveryLabels.size)
        assertTrue(report.discoveryEvidence.all { it.nextPageUrl != null && it.nextPageBookCount == null })
        assertTrue(report.discoveryWarnings.all { it.contains("下一页未验证通过") })
        assertEquals(false, steps.last().ok)
    }

    @Test fun cancellationDuringDiscoveryIsNotConvertedToSuccess() = runBlocking {
        cancelDiscovery = true
        val result = runCatching { build() }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
