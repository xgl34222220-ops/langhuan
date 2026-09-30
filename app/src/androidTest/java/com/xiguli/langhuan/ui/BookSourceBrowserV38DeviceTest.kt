package com.xiguli.langhuan.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookSourceBrowserV38DeviceTest {
    @Test
    fun browserFallbackExecutesJavascriptAndCapturesDom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        BookSourceBrowserV38.install(context)
        val html = """
            <html><body>
              <div id="probe">before</div>
              <script>document.getElementById('probe').textContent='after';</script>
            </body></html>
        """.trimIndent()
        val dataUrl = "data:text/html;charset=utf-8," + java.net.URLEncoder.encode(html, "UTF-8")
        val doc = BookSourceBrowserV38.fetchDocument(
            SourceRequestV36(dataUrl),
            timeoutMs = 12_000L,
        )
        assertEquals("after", doc.selectFirst("#probe")?.text())
    }
}
