package com.xiguli.langhuan.ui

import com.xiguli.langhuan.engine.AiStructuredOutputException
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceEngineV36Test {
    private val searchHtml = """
        <html><body><div class="list">
          <div class="item"><h3><a href="/book/1">长夜</a></h3><span class="author">甲</span><img src="/c1.jpg"></div>
          <div class="item"><h3><a href="/book/2">长夜未央</a></h3><span class="author">乙</span></div>
        </div></body></html>
    """.trimIndent()

    @Test
    fun legadoDefaultSyntaxSelectsListAndValues() {
        val doc = Jsoup.parse(searchHtml, "https://example.com/search")
        val items = ruleElementsV36(doc, "class.item")
        assertEquals(2, items.size)
        assertEquals("长夜", ruleStringV36(items[0], "tag.h3@tag.a@text"))
        assertEquals("https://example.com/book/1", ruleStringV36(items[0], "tag.a.0@href"))
        assertEquals("甲", ruleStringV36(items[0], "class.author@text"))
    }

    @Test
    fun cssRulesAlternativesAndCleanup() {
        val doc = Jsoup.parse(searchHtml, "https://example.com/search")
        val items = ruleElementsV36(doc, "@css:div.item")
        assertEquals(2, items.size)
        assertEquals("长夜", ruleStringV36(items[0], "@css:h3 a@text"))
        assertEquals("乙", ruleStringV36(items[1], "class.missing@text||class.author@text"))
        assertEquals("长夜", ruleStringV36(items[1], "@css:h3 a@text##未央"))
        assertEquals(listOf("长夜未央", "长夜"), ruleElementsV36(doc, "-class.item").map { ruleStringV36(it, "tag.a@text") })
    }

    @Test
    fun contentHtmlBecomesParagraphs() {
        val doc = Jsoup.parse("<div id=\"content\">第一段<br>第二段<p>第三段</p><script>x()</script></div>")
        assertEquals("第一段\n第二段\n第三段", htmlToTextV36(doc.getElementById("content")!!))
        assertEquals("正文", cleanContentV36("正文\n本站广告", "##本站广告"))
    }

    @Test
    fun legadoImportKeepsHtmlSourcesAndSkipsScripted() {
        val json = """
            [
              {"bookSourceName":"甲站","bookSourceUrl":"https://a.example","searchUrl":"/s?q={{key}}",
               "ruleSearch":{"bookList":"class.item","name":"tag.a@text","bookUrl":"tag.a@href"},
               "ruleToc":{"chapterList":"id.list@tag.dd","chapterName":"tag.a@text","chapterUrl":"tag.a@href"},
               "ruleContent":{"content":"id.content@html"}},
              {"bookSourceName":"乙站","bookSourceUrl":"https://b.example","searchUrl":"/api?q={{key}}",
               "ruleSearch":{"bookList":"$.data.list[*]","name":"$.name","bookUrl":"$.url"}}
            ]
        """.trimIndent()
        val result = parseBookSourcesV36(json)
        // V95: JSON API sources ($.…) import too.
        assertEquals(2, result.sources.size)
        assertEquals("甲站", result.sources[0].name)
        assertEquals(0, result.skipped.size)
        val request = buildSearchRequestV36(result.sources[0], "长夜")
        assertTrue(request.url.startsWith("https://a.example/s?q="))
        assertFalse(request.url.contains("{{key}}"))
    }

    @Test
    fun ttsChunksStartAtParagraphAndCutLongOnes() {
        val body = "第一段。\n" + "长".repeat(1300) + "。\n第三段"
        val chunks = readerTtsChunksV35(body, fromOffset = 7, maxChars = 600)
        assertEquals(5, chunks.first().offset)
        assertTrue(chunks.all { it.text.length <= 600 })
        assertEquals("第三段", chunks.last().text)
    }

    @Test
    fun retryClassificationSeparatesTransientFromConfigErrors() {
        assertTrue(foundationRetryableV34(AiStructuredOutputException("cut off")))
        assertTrue(foundationRetryableV34(java.net.SocketTimeoutException("read timed out")))
        assertTrue(foundationRetryableV34(IllegalStateException("AI 服务返回 429：rate limited")))
        assertTrue(foundationRetryableV34(IllegalStateException("AI 服务返回 503：busy")))
        assertFalse(foundationRetryableV34(IllegalStateException("AI 服务返回 401：bad key")))
        assertFalse(foundationRetryableV34(IllegalArgumentException("请先配置 API 地址")))
    }
}
