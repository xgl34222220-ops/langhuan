package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceAiBuilderV37Test {
    @Test
    fun getSearchFormBecomesTemplate() {
        val doc = Jsoup.parse(
            """<form action="/search.php" method="get"><input type="hidden" name="t" value="1"><input type="text" name="searchkey"></form>""",
            "https://www.example.com/",
        )
        assertEquals("https://www.example.com/search.php?t=1&searchkey={{key}}", detectSearchUrlV37(doc))
    }

    @Test
    fun postGbkFormKeepsMethodAndCharset() {
        val doc = Jsoup.parse(
            """<html><head><meta charset="gbk"></head><body><form action="/modules/search.php" method="post"><input name="searchkey"></form></body></html>""",
            "https://gbk.example.com/",
        )
        val url = detectSearchUrlV37(doc)!!
        assertTrue(url.startsWith("https://gbk.example.com/modules/search.php,{"))
        assertTrue(url.contains("\"method\":\"POST\""))
        assertTrue(url.contains("\"charset\":\"gbk\""))
        val request = buildSearchRequestV36(BookSourceV36(id = "x", name = "x", baseUrl = "https://gbk.example.com", searchUrl = url), "长夜")
        assertEquals("POST", request.method)
        assertEquals("gbk", request.charset)
        assertFalse(request.body!!.contains("{{key}}"))
    }

    @Test
    fun loginFormsAreNotMistakenForSearch() {
        val doc = Jsoup.parse("""<form action="/login"><input type="text" name="username"><input type="password" name="pass"></form>""", "https://a.example/")
        assertNull(detectSearchUrlV37(doc))
    }

    @Test
    fun skeletonCollapsesRepeatsAndKeepsSelectorsAndLinks() {
        val items = (1..30).joinToString("") { """<li class="book"><a href="/b/$it">书$it</a></li>""" }
        val doc = Jsoup.parse("""<body><div id="main"><ul class="list">$items</ul></div><script>var x=1</script></body>""", "https://a.example/")
        val skeleton = pageSkeletonV37(doc)
        assertTrue(skeleton.contains("div#main"))
        assertTrue(skeleton.contains("ul.list"))
        assertTrue(skeleton.contains("li.book ×30"))
        assertTrue(skeleton.contains("[href=/b/1]"))
        assertFalse(skeleton.contains("书30"))
        assertFalse(skeleton.contains("script"))
    }

    @Test
    fun httpInputHasHttpsFallbackAndCanonicalOriginUsesRedirectTarget() {
        assertEquals("https://101kanshu.com/", httpsFallbackUrlV36("http://101kanshu.com/"))
        assertNull(httpsFallbackUrlV36("https://101kanshu.com/"))
        assertEquals("https://101kks.com", canonicalOriginV37("https://101kks.com/path?q=1"))
        assertEquals("https://example.com:8443", canonicalOriginV37("https://example.com:8443/a"))
    }

    @Test
    fun rulesParseFromMessyModelOutput() {
        val rules = parseRulesV37("好的：\n```json\n{\"searchList\":\"@css:li.book\",\"searchName\":\"@css:a@text\",}\n```")
        assertEquals("@css:li.book", rules["searchList"])
        assertEquals("@css:a@text", rules["searchName"])
        assertEquals("https://a.example", normalizeSiteV37(" a.example "))
    }
}
