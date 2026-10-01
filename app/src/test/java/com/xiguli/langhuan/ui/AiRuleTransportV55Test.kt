package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.net.SocketTimeoutException
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class AiRuleTransportV55Test {
    private data class Evidence(val calls: Int, val requests: Int, val error: Throwable?, val steps: List<AiSourceStepV37>)
    private fun run(error: Exception, large: Boolean = true, firstEmpty: Boolean = false): Evidence = runBlocking {
        var model = 0; var search = 0; var steps = emptyList<AiSourceStepV37>()
        val raw = "<ul id='list'><li><a href='/book/1'>原创小说</a></li></ul><p>" + (if (large) "&".repeat(900000) else "small") + "</p>"
        assertTrue(raw.toByteArray().size < MAX_SOURCE_BYTES_V36)
        val searchDoc = Jsoup.parse(raw, "https://books.example/search?q=test")
        if (large) assertTrue(searchDoc.outerHtml().length.toLong() * 2 > 8L * 1024 * 1024)
        val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("No structured provider")
            override suspend fun generateText(prompt: PromptBundle): String {
                model++
                if (firstEmpty && model == 1) return """{"searchList":"@css:#missing","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
                return """{"searchList":"@css:#list li","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
            }
        }
        val result = runCatching {
            BookSourceAiBuilderV37(gateway, { steps = it }) { _, request ->
                when {
                    request.url.endsWith("/") -> Jsoup.parse("<form action='/search'><input name='q'></form>", request.url)
                    request.url.contains("/search") -> {
                        search++
                        if (search > 1) throw error
                        searchDoc
                    }
                    else -> throw java.io.IOException("fixture ends after the search stage")
                }
            }.build("https://books.example/", "test")
        }
        println("AUDIT type=${error.javaClass.simpleName} large=$large modelCalls=$model searchFetches=$search final=${result.exceptionOrNull()?.message}")
        Evidence(model, search, result.exceptionOrNull(), steps)
    }
    @Test fun ordinarySuccessDocumentIsReusedWithoutASecondHttpRequest() {
        val e = run(SourceHttpStatusExceptionV44(429,"https://books.example","rate limited"), false)
        assertEquals(1,e.calls); assertEquals(1,e.requests)
    }
    @Test fun uncachedSearch429StopsBeforePaidCorrection() {
        val e = run(SourceHttpStatusExceptionV44(429,"https://books.example","rate limited"))
        assertEquals(1,e.calls); assertEquals(2,e.requests)
        assertTrue(e.error!!.message!!.contains("429")); assertEquals(false,e.steps.last().ok)
    }
    @Test fun uncachedSearch403MustNotBecomeSelectorCorrection() {
        val e = run(SourceHttpStatusExceptionV44(403,"https://books.example","forbidden"))
        assertEquals("Transport failure must not consume a second model call",1,e.calls)
        assertTrue(e.error!!.message!!.contains("403"))
    }
    @Test fun uncachedSearchTimeoutMustNotBecomeSelectorCorrection() {
        val e = run(SocketTimeoutException("synthetic timeout"))
        assertEquals("Transport timeout must not consume a second model call",1,e.calls)
        assertTrue(e.error!!.message!!.contains("timeout"))
    }
    @Test fun uncachedSearchCancellationStillPropagates() {
        val e = run(CancellationException("synthetic cancel"))
        assertEquals(1,e.calls);assertEquals(2,e.requests);assertTrue(e.error is CancellationException)
    }
    @Test fun actualZeroMatchesStillAllowsOneSelectorCorrection() {
        val e = run(SocketTimeoutException("must remain cached"), large=false, firstEmpty=true)
        assertEquals(2,e.calls); assertEquals(1,e.requests)
        assertTrue(e.error!!.message!!.contains("fixture ends after the search stage"))
    }
    @Test fun fullTocRequest403DoesNotSpendAnotherTocModelCall() = stageFailure(content=false)
    @Test fun nextContentPageTimeoutDoesNotSpendAnotherContentModelCall() = stageFailure(content=true)

    private fun stageFailure(content: Boolean): Unit = runBlocking {
        var modelCalls=0
        val gateway=object:AiGateway {
            override suspend fun generate(prompt:PromptBundle):GeneratedChapter = error("Not used")
            override suspend fun generateText(prompt:PromptBundle):String {
                modelCalls++
                return when(modelCalls) {
                    1 -> """{"searchList":"@css:li","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
                    2 -> if(content) """{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""
                        else """{"infoName":"@css:h1@text","infoTocUrl":"@css:a.full@href","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""
                    3 -> """{"contentText":"@css:#body@html","contentNext":"@css:a.next@href"}"""
                    else -> error("Unexpected paid correction")
                }
            }
        }
        val result=runCatching {
            BookSourceAiBuilderV37(gateway,{}) { _,request ->
                val html=when {
                    request.url.endsWith("/") -> "<form action='/search'><input name='q'></form>"
                    request.url.contains("/search") -> "<li><a href='/book/1'>原创小说</a></li>"
                    request.url.endsWith("/book/1") -> "<h1>原创小说</h1>"+(if(content) "" else "<a class='full' href='/toc'>完整目录</a>")+"<div id='chapters'><a href='/read/1'>第一章</a><a href='/read/2'>第二章</a></div>"
                    request.url.endsWith("/toc") -> throw SourceHttpStatusExceptionV44(403,"https://books.example","synthetic forbidden")
                    request.url.endsWith("/read/1/2") -> throw SocketTimeoutException("synthetic content timeout")
                    else -> "<h1>第一章</h1><div id='body'>"+"这是用来核对原文章节的合成正文。".repeat(20)+"</div><a class='next' href='/read/1/2'>下一页</a>"
                }
                Jsoup.parse(html,request.url)
            }.build("https://books.example/","原创小说")
        }
        assertEquals(if(content) 3 else 2,modelCalls)
        val failure = requireNotNull(result.exceptionOrNull())
        assertTrue(failure.message.orEmpty(),generateSequence(failure as Throwable) { it.cause }.take(12).any { it is java.io.IOException })
        assertTrue(failure.message.orEmpty(),failure.message.orEmpty().contains(if(content) "synthetic content timeout" else "403"))
    }

}
