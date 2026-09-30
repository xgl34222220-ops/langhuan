package com.xiguli.langhuan.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The old fallback executed untrusted JavaScript. Guard the deliberately static-only boundary. */
@RunWith(AndroidJUnit4::class)
class BookSourceBrowserV38DeviceTest {
    @Test
    fun dataUrlsAndLocalUrlsCannotEnterSourceNetwork() {
        BookSourceBrowserV38.install(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        val source = BookSourceV36("test", "test", "https://example.com")
        listOf("data:text/html,<script>alert(1)</script>", "file:///data/local/tmp/probe", "http://127.0.0.1/")
            .forEach { url -> assertTrue(url, runCatching { fetchDocumentV36(source, SourceRequestV36(url)) }.isFailure) }
    }

    @Test
    fun staticParserDoesNotExecutePageJavascript() {
        val doc = Jsoup.parse("""<div id="probe">before</div><script>document.getElementById('probe').textContent='after';</script>""")
        assertEquals("before", doc.selectFirst("#probe")?.text())
        assertTrue(browserChallengePendingV38("<title>Just a moment...</title><div id='cf-chl-widget'></div>"))
    }
}
