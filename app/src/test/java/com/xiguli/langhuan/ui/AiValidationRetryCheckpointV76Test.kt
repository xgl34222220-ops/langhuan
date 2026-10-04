package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiValidationRetryCheckpointV76Test {
    @Test
    fun tocFailureRetryReusesValidatedSearchRulesButRechecksLivePages() = runBlocking {
        val fixture = Fixture { stage, attempt ->
            when {
                stage == Stage.TOC && attempt <= 2 -> """{"unexpected":"invalid"}"""
                else -> stage.rules
            }
        }

        assertTrue(runCatching { fixture.build() }.isFailure)
        assertEquals(1, fixture.calls(Stage.SEARCH))
        assertEquals(2, fixture.calls(Stage.TOC))
        assertEquals(0, fixture.calls(Stage.CONTENT))
        assertTrue(fixture.checkpoint.hasValidatedRules())

        val report = fixture.build()

        assertEquals("Earlier validated search rules must not spend another model call", 1, fixture.calls(Stage.SEARCH))
        assertEquals(3, fixture.calls(Stage.TOC))
        assertEquals(1, fixture.calls(Stage.CONTENT))
        assertEquals("原创小说", report.bookName)
        assertEquals(2, fixture.requests.count { it == "https://retry.example/" })
        assertEquals(2, fixture.requests.count { it.startsWith("https://retry.example/search") })
    }

    @Test
    fun contentTransportFailureRetryKeepsSearchAndTocRulesWithoutCachingTheFailure() = runBlocking {
        val fixture = Fixture { stage, attempt ->
            if (stage == Stage.CONTENT && attempt == 1) throw IOException("fixture provider disconnected")
            stage.rules
        }

        assertTrue(runCatching { fixture.build() }.isFailure)
        assertEquals(1, fixture.calls(Stage.SEARCH))
        assertEquals(1, fixture.calls(Stage.TOC))
        assertEquals(1, fixture.calls(Stage.CONTENT))

        val report = fixture.build()

        assertEquals(1, fixture.calls(Stage.SEARCH))
        assertEquals(1, fixture.calls(Stage.TOC))
        assertEquals(2, fixture.calls(Stage.CONTENT))
        assertTrue(report.sample.length >= 60)
        assertEquals(2, fixture.requests.count { it == "https://retry.example/book/1" })
        assertEquals(2, fixture.requests.count { it == "https://retry.example/read/1" })
    }

    private enum class Stage(val rules: String) {
        SEARCH("""{"searchList":"@css:li.book","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""),
        TOC("""{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""),
        CONTENT("""{"contentText":"@css:#content@html"}"""),
    }

    private class Fixture(
        private val respond: suspend (Stage, Int) -> String,
    ) {
        val checkpoint = AiValidationCheckpointV76()
        val requests = mutableListOf<String>()
        private val prompts = mutableListOf<Stage>()
        private val base = "https://retry.example"
        private val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("Structured generation is unused")

            override suspend fun generateText(prompt: PromptBundle): String {
                val stage = when {
                    prompt.user.contains("搜索结果页") -> Stage.SEARCH
                    prompt.user.contains("书籍详情页") -> Stage.TOC
                    prompt.user.contains("章节正文页") -> Stage.CONTENT
                    else -> error("Unexpected prompt: ${prompt.user.take(100)}")
                }
                prompts += stage
                return respond(stage, calls(stage))
            }
        }

        fun calls(stage: Stage): Int = prompts.count { it == stage }

        private fun fetch(source: BookSourceV36, request: SourceRequestV36): Document {
            requests += request.url
            require(request.url.startsWith(base))
            val html = when (request.url.removePrefix(base).substringBefore('?')) {
                "/" -> "<title>重试回归站</title><form action='/search'><input name='q'></form>"
                "/search" -> "<ul><li class='book'><a href='/book/1'>原创小说</a></li></ul>"
                "/book/1" -> "<h1>原创小说</h1><div id='chapters'><a href='/read/1'>第一章</a><a href='/read/2'>第二章</a></div>"
                "/read/1", "/read/2" -> "<a href='/book/1'>原创小说</a><h1>第一章</h1><div id='content'>${"这是用于验证分阶段重试的原创正文。".repeat(16)}</div>"
                else -> error("Unexpected fixture request: ${request.url}")
            }
            return Jsoup.parse(html, request.url)
        }

        suspend fun build(): AiSourceReportV37 = BookSourceAiBuilderV37(
            gateway = gateway,
            onSteps = {},
            validationCheckpoint = checkpoint,
            fetchDocument = ::fetch,
        ).build("$base/", "原创小说")
    }
}
