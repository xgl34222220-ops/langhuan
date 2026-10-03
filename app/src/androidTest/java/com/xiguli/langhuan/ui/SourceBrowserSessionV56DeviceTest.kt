package com.xiguli.langhuan.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real WebView rendering/IPC/profile reuse, with an explicit synthetic model and site. */
@RunWith(AndroidJUnit4::class)
class SourceBrowserSessionV56DeviceTest {
    private val base = "https://browser-fixture.example"
    private fun transport() = SourceBrowserTransportV56(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

    @Test fun dynamicSourceBuildAndSavedSourceReadingKeepTheBrowserSession(): Unit = runBlocking {
        val browser = transport()
        val sessionCookie = "session_${UUID.randomUUID().toString().replace("-", "")}"
        val text = "这是浏览器渲染后才出现的原创小说正文，灯光照亮了桌边的纸张。".repeat(8)
        fun dynamic(content: String, cookie: Boolean = false): String = """<!doctype html><html><body><p>Loading...</p><script>
            ${if (cookie) "document.cookie='$sessionCookie=present; path=/; Secure';" else ""}
            setTimeout(function(){document.body.innerHTML = ${BookSourceJsonV36.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(content))};}, 1400);
            </script></body></html>"""
        val requests = mutableListOf<SourceRequestV36>()
        val fetch: (BookSourceV36, SourceRequestV36) -> org.jsoup.nodes.Document = { source, request ->
            assertTrue("The browser mode must survive every generated rule update", source.useBrowser)
            requests += request
            val html = when {
                request.url.trimEnd('/') == base -> dynamic("<title>Browser fixture</title><form action='/search' method='post'><input name='q'></form>", cookie = true)
                request.url.contains("/search") -> """<html><body><p>Loading...</p><script>
                    setTimeout(function(){document.body.innerHTML = document.cookie.indexOf('$sessionCookie=present')>=0 ? '<ul id="results"><li><a href="/book/1">原创小说</a></li></ul>' : '<p>会话丢失</p>';},1400);
                    </script></body></html>"""
                request.url.endsWith("/book/1") -> dynamic("<h1>原创小说</h1><div id='chapters'><a href='/read/1'>第一章 灯下</a><a href='/read/2'>第二章 清晨</a></div>")
                request.url.endsWith("/read/1") -> dynamic("<a href='/book/1'>原创小说</a><h1>第一章 灯下</h1><div id='content'>$text</div>")
                else -> error("Unexpected fixture request: ${request.url}")
            }
            browser.fixtureDocument(source, request, html)
        }
        var modelCalls = 0
        val model = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("This test only generates source rules")
            override suspend fun generateText(prompt: PromptBundle): String = when (++modelCalls) {
                1 -> """{"searchList":"@css:#results li","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
                2 -> """{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""
                3 -> """{"contentText":"@css:#content@html"}"""
                else -> """{"exploreUrl":""}"""
            }
        }
        val report = BookSourceAiBuilderV37(model, {}, fetch).build(base, "原创小说", useBrowser = true)
        assertEquals("原创小说", report.bookName)
        assertEquals(2, report.chapterCount)
        assertTrue(report.sample.contains("浏览器渲染"))
        assertEquals("POST", requests.first { it.url.contains("/search") }.method)
        val saved = BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), report.source)
        val restored = parseBookSourcesV36(saved).sources.single()
        assertTrue(restored.useBrowser)
        val books = searchSourceV36(restored, "原创小说", fetchDocument = fetch)
        val catalogue = loadBookV36(restored, books.single(), fetch)
        assertEquals(2, catalogue.second.size)
        val chapter = loadChapterTextV36(restored, catalogue.second.first(), catalogue.second.map { it.url }.toSet(), fetch)
        assertEquals(text, chapter)
    }

    @Test fun cancellationInterruptsABrowserWaitAndTheNextRequestCanStillComplete() {
        val browser = transport()
        val source = BookSourceV36("cancel", "Cancel fixture", base, useBrowser = true)
        var cancelled: Throwable? = null
        val worker = Thread {
            try { browser.fixtureDocument(source, SourceRequestV36("$base/pending"), "<p>Loading...</p>") }
            catch (error: Throwable) { cancelled = error }
        }
        worker.start()
        Thread.sleep(1500)
        worker.interrupt()
        worker.join(10_000)
        assertFalse("Cancellation must release the calling thread", worker.isAlive)
        assertTrue(cancelled.toString(), cancelled is java.util.concurrent.CancellationException)
        val doc = browser.fixtureDocument(source, SourceRequestV36("$base/retry"), "<h1>重试完成</h1>")
        assertEquals("重试完成", doc.selectFirst("h1")?.text())
    }
}
