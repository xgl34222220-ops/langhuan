package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BookSourceHttpDiagnosticsV44Test {
    @Test fun loadingCopyAndChallengeScriptsDoNotBlockAReadableHomepage() {
        val html = """<title>示例书站</title><h1>小说首页</h1>
            <form action='/search'><input name='q'></form><a href='/book/1'>示例小说</a>
            <div hidden>请稍候，正在加载书架</div><script>const hint='just a moment';</script>
            <script src='/cdn-cgi/challenge-platform/scripts/jsd/main.js'></script>"""
        assertFalse(browserChallengePendingV38(html))
        val doc = parseSourceDocumentV44(html.toByteArray(), "https://books.example/")
        assertEquals("https://books.example/search?q={{key}}", detectSearchUrlV37(doc))
    }

    @Test fun quotedChallengeTermsInChapterTextDoNotBlockReading() {
        val html = """<title>第十二章 稍候</title><article><h1>第十二章</h1>
            <p>他指着屏幕上 checking your browser 的提示说：请稍候。</p>
            <p>他们一边等候，一边讨论标题为 Just a moment 的短篇。</p></article>"""
        assertFalse(browserChallengePendingV38(html))
    }

    @Test fun actualInterstitialsStillStopBeforeAi() {
        assertTrue(browserChallengePendingV38("<title>Just a moment...</title><div id='cf-chl-widget'></div>"))
        assertTrue(browserChallengePendingV38("<title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1'></script><noscript>Enable JavaScript and cookies to continue</noscript>"))
        assertTrue(browserChallengePendingV38("<p>正在检查您的浏览器，请稍候</p>"))
        assertTrue(browserChallengePendingV38("<title>Attention Required! | Cloudflare</title><div id='cf-error-details'><h1>Sorry, you have been blocked</h1></div>"))
    }

    @Test fun responseHeaderIsAuthoritativeEvenForAnUnfamiliarChallengePage() {
        assertTrue(browserChallengePendingV38("<p>Unknown verification interface</p>", "challenge"))
        assertTrue(runCatching {
            parseSourceDocumentV44("<p>Unknown verification interface</p>".toByteArray(), "https://books.example/", mitigationHeader = "challenge")
        }.isFailure)
        assertFalse(browserChallengePendingV38("<h1>小说首页</h1>", "unrelated-value"))
    }

    @Test fun badRequestDoesNotInventLoginOrJavascriptRequirements() {
        val failure = sourceHttpFailureV44(400, publicSourceUrlV36("http://books.example/search?token=private"), "<p>Bad request</p>".toByteArray())
        assertEquals(400, failure.statusCode)
        assertEquals("http://books.example", failure.origin)
        assertTrue(failure.message!!.contains("HTTP 400"))
        assertTrue(failure.message!!.contains("可尝试"))
        assertFalse(failure.message!!.contains("明确要求"))
        listOf("登录", "验证码", "动态", "静态", "private", "token", "/search").forEach {
            assertFalse(it, failure.message!!.contains(it))
        }
        val httpsFailure = sourceHttpFailureV44(400, publicSourceUrlV36("https://books.example/"), ByteArray(0))
        assertFalse(httpsFailure.message!!.contains("可尝试"))
    }

    @Test fun protocolRequirementNeedsExplicitServerEvidence() {
        val failure = sourceHttpFailureV44(400, publicSourceUrlV36("http://books.example/"),
            "<title>400 Bad Request</title><p>The plain HTTP request was sent to HTTPS port</p>".toByteArray())
        assertTrue(failure.message!!.contains("明确要求 HTTPS"))
        assertFalse(sourceHttpFailureV44(400, publicSourceUrlV36("https://books.example/"), ByteArray(0)).message!!.contains("明确要求"))
    }

    @Test fun challengeAndRateLimitsKeepTheActualStatusWithoutEchoingResponseSecrets() {
        val challenge = sourceHttpFailureV44(403, publicSourceUrlV36("https://books.example/private/path?key=secret"),
            "<title>Attention Required! | Cloudflare</title><div id='cf-error-details'>cookie=session-secret</div>".toByteArray())
        assertTrue(challenge.message!!.contains("HTTP 403"))
        assertTrue(challenge.message!!.contains("访问拦截"))
        listOf("private", "key", "secret", "cookie").forEach { assertFalse(challenge.message!!.contains(it)) }
        assertTrue(sourceHttpFailureV44(429, publicSourceUrlV36("https://books.example/"), ByteArray(0)).message!!.contains("请求频率"))
        assertTrue(sourceHttpFailureV44(503, publicSourceUrlV36("https://books.example/"), ByteArray(0)).message!!.contains("网站服务"))
    }

    @Test fun errorPrefixIsBoundedWithoutReadingTheRestOfTheResponse() {
        val stream = ByteArrayInputStream(ByteArray(MAX_SOURCE_ERROR_BYTES_V44 + 100))
        assertEquals(MAX_SOURCE_ERROR_BYTES_V44, readSourceErrorPrefixV44(stream).size)
        assertEquals(100, stream.available())
        assertTrue(runCatching { readSourceErrorPrefixV44(stream, MAX_SOURCE_ERROR_BYTES_V44 + 1) }.isFailure)
    }

    @Test fun failedHomepageMakesOneRequestAndNeverInvokesTheModel() = runBlocking {
        var modelCalls = 0
        var requests = 0
        var reported = emptyList<AiSourceStepV37>()
        val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("Unused structured path")
            override suspend fun generateText(prompt: PromptBundle): String { modelCalls++; error("Should not invoke AI") }
        }
        val builder = BookSourceAiBuilderV37(gateway, { reported = it }) { _, request ->
            requests++
            throw sourceHttpFailureV44(400, publicSourceUrlV36(request.url), ByteArray(0))
        }
        val failure = runCatching { builder.build("http://books.example/", "我们") }.exceptionOrNull()!!
        assertEquals(1, requests)
        assertEquals(0, modelCalls)
        assertEquals(1, reported.size)
        assertEquals(false, reported.single().ok)
        assertTrue(reported.single().completed)
        assertEquals(reported.single().detail, failure.message)
        assertTrue(failure.message!!.contains("首页读取失败"))
        assertTrue(failure.message!!.contains("HTTP 400"))
    }
}
