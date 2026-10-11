package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SourceBrowserModeV56Test {
    @Test fun browserModeSurvivesSavingAndImportWithoutEnablingScriptRules() {
        val source = BookSourceV36("browser", "Browser source", "https://books.example", useBrowser = true,
            searchUrl = "/search?q={{key}}", searchList = "@css:li", searchName = "@css:a@text", searchBookUrl = "@css:a@href")
        val saved = BookSourceJsonV36.encodeToString(BookSourceV36.serializer(), source)
        assertEquals(source, parseBookSourcesV36(saved).sources.single())
        // V95: sandboxed Legado scripts import; scripts reaching for Java classes still do not.
        assertTrue(parseBookSourcesV36(saved.replace("@css:li", "@js:java.ajax('/read')")).sources.isNotEmpty())
        assertFalse(parseBookSourcesV36(saved.replace("@css:li", "@js:Packages.java.lang.System.exit(0)")).sources.isNotEmpty())
        assertFalse(BookSourceJsonV36.decodeFromString(BookSourceV36.serializer(), """{"id":"old","name":"Old","baseUrl":"https://books.example"}""").useBrowser)
    }

    @Test fun aChallengeOrLoadingPlaceholderIsNeverAUsableBookPage() {
        listOf("", "<script>window.pending=true</script>", "<p>Loading...</p>", "<header>原创书城</header><a href='/home'>首页</a><main><p>Loading...</p></main>",
            "<h1>第一章</h1><div aria-busy=true>尚未完成</div>",
            "<title>Just a moment...</title><form id=challenge-form>Verify you are human</form>")
            .forEach { assertFalse(it, browserDocumentReadyV56(it)) }
        assertTrue(browserDocumentReadyV56("<form action='/s'><input name='q'></form>"))
        assertTrue(browserDocumentReadyV56("<h1>第一章</h1><div>他读到‘verify you are human’，放下了手里的纸。</div>"))
    }

    @Test fun browserAndHttpDocumentsCannotShareAnExtractionCache() {
        var calls = 0
        val source = BookSourceV36("s", "Source", "https://books.example")
        val session = SourceRequestSessionV46 { actual, request ->
            calls++
            Jsoup.parse("<p>${if (actual.useBrowser) "rendered" else "static"}</p>", request.url)
        }
        val request = SourceRequestV36("https://books.example/")
        assertEquals("static", session.document(source, request).body().text())
        assertEquals("rendered", session.document(source.copy(useBrowser = true), request).body().text())
        assertEquals("rendered", session.document(source.copy(useBrowser = true), request).body().text())
        assertEquals(2, calls)
    }

    @Test fun onlyAnExplicitTlsErrorCanUpgradeAnHttpGet() {
        val html = "<h1>400 Bad Request</h1><p>The plain HTTP request was sent to HTTPS port</p>"
        assertEquals("https://books.example/search?q=book", browserHttpsUpgradeV56(400, "GET", "http://books.example/search?q=book", html))
        assertNull(browserHttpsUpgradeV56(400, "POST", "http://books.example/search", html))
        assertNull(browserHttpsUpgradeV56(400, "GET", "https://books.example/search", html))
        assertNull(browserHttpsUpgradeV56(400, "GET", "http://books.example/search", "<p>Invalid search query</p>"))
        assertNull(browserHttpsUpgradeV56(403, "GET", "http://books.example/search", html))
    }
}
