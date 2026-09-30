package com.xiguli.langhuan.ui

import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.CancellationException
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class BookSourceSecurityV42Test {
    private val source = BookSourceV36("test", "test", "https://books.example",
        headers = mapOf("Authorization" to "Bearer secret", "Cookie" to "session=secret", "X-Api-Key" to "secret", "Host" to "other.example"))

    @Test fun privateAndSpecialAddressesAreRejected() {
        listOf("0.0.0.0", "10.1.2.3", "100.64.0.1", "127.0.0.1", "169.254.169.254", "172.31.1.1",
            "192.168.1.1", "192.0.2.1", "198.19.1.1", "224.1.1.1", "255.255.255.255",
            "::", "::1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "2001:db8::1", "2002:7f00:1::")
            .forEach { assertFalse(it, publicSourceAddressV36(InetAddress.getByName(it))) }
        listOf("1.1.1.1", "8.8.8.8", "93.184.216.34", "2606:4700:4700::1111")
            .forEach { assertTrue(it, publicSourceAddressV36(InetAddress.getByName(it))) }
    }

    @Test fun urlBoundaryRejectsNonHttpCredentialsAndLocalTargets() {
        listOf("file:///etc/passwd", "javascript:alert(1)", "https://name:secret@books.example/",
            "http://localhost/", "http://server.local/", "http://127.0.0.1/", "http://[::1]/")
            .forEach { assertTrue(it, runCatching { publicSourceUrlV36(it) }.isFailure) }
        assertEquals("https://books.example/page", publicSourceUrlV36("HTTPS://BOOKS.EXAMPLE:443/page#top").toString())
    }

    @Test fun sourceHeadersStayOnExactOriginAndCannotOverrideRouting() {
        val own = sourceHeadersForUrlV36(source, publicSourceUrlV36("https://books.example/chapter"))
        assertEquals("Bearer secret", own["Authorization"])
        assertEquals("session=secret", own["Cookie"])
        assertFalse(own.containsKey("Host"))
        listOf("https://other.example/", "http://books.example/", "https://books.example:8443/").forEach {
            assertTrue(sourceHeadersForUrlV36(source, publicSourceUrlV36(it)).isEmpty())
        }
    }

    @Test fun redirectPolicyBlocksDowngradePrivateAddressAndPostReplay() {
        val from = publicSourceUrlV36("https://books.example/dir/page.html")
        assertEquals("https://books.example/dir/next.html", sourceRedirectV36(from, "next.html", 302, "GET").toString())
        assertTrue(runCatching { sourceRedirectV36(from, "http://books.example/", 302, "GET") }.isFailure)
        assertTrue(runCatching { sourceRedirectV36(from, "https://127.0.0.1/", 302, "GET") }.isFailure)
        assertTrue(runCatching { sourceRedirectV36(from, "https://other.example/", 307, "POST") }.isFailure)
        assertEquals("other.example", sourceRedirectV36(from, "https://other.example/", 302, "POST").host)
    }

    @Test fun streamLimitCoversUnknownLengthAndExactBoundary() {
        assertEquals(32, readSourceBytesV36(ByteArrayInputStream(ByteArray(32)), 32).size)
        assertTrue(runCatching { readSourceBytesV36(ByteArrayInputStream(ByteArray(33)), 32) }.isFailure)
        assertTrue(runCatching { readSourceBytesV36(ByteArrayInputStream(ByteArray(1)), 0) }.isFailure)
    }

    @Test fun cancellationIsNeverConvertedIntoEmptySuccess() {
        val expected = CancellationException("cancel")
        try {
            sourceAttemptV36<String> { throw expected }.getOrDefault("")
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) { assertSame(expected, actual) }
        assertEquals("fallback", sourceAttemptV36<String> { error("ordinary failure") }.getOrDefault("fallback"))
    }

    @Test fun optionalScriptRulesAreUnsupportedToo() {
        assertFalse(bookSourceSupportedV36(source.copy(contentNext = "@js:java.ajax('/secret')")))
        assertFalse(bookSourceSupportedV36(source.copy(infoTocUrl = "$.chapters")))
        assertFalse(bookSourceSupportedV36(source.copy(searchAuthor = "<js>fetch('http://localhost')</js>")))
    }

    @Test fun formsAreEncodedAndCrossSiteOrLoginFormsRejected() {
        val doc = Jsoup.parse("""<form action='/s' method='post'><input name='q'><input type='hidden' name='a&amp;b' value='&quot;&amp;x=2'></form>""", "https://books.example/")
        val template = detectSearchUrlV37(doc)!!
        val request = buildSearchRequestV36(source.copy(searchUrl = template), "书名")
        assertTrue(request.body!!.contains("a%26b=%22%26x%3D2"))
        assertEquals("POST", request.method)
        assertNull(detectSearchUrlV37(Jsoup.parse("<form action='https://other.example/'><input name='q'></form>", "https://books.example/")))
        assertNull(detectSearchUrlV37(Jsoup.parse("<form><input name='q'><input type='password'></form>", "https://books.example/")))
    }

    @Test fun aiRulesAreBoundedAndCannotEnableJavascript() {
        assertTrue(runCatching { parseRulesV37("{\"contentNext\":\"@js:alert(1)\"}") }.isFailure)
        assertTrue(runCatching { parseRulesV37("x".repeat(65 * 1024)) }.isFailure)
        val skeleton = pageSkeletonV37(Jsoup.parse("<a href='/book?token=private#secret'>书</a>", "https://books.example/"))
        assertFalse(skeleton.contains("private"))
        assertFalse(skeleton.contains("secret"))
    }

    @Test fun relativeLinksResolveAgainstDocumentDirectory() {
        assertEquals("https://books.example/dir/2.html", resolveUrlV36("https://books.example/dir/1.html", "2.html"))
        assertEquals("https://books.example/search?q=x", resolveUrlV36("https://books.example", "/search?q=x"))
    }
    @Test fun importConflictsKeepOriginalCredentialsAndRules() {
        val changed = source.copy(headers = mapOf("Cookie" to "attacker"))
        val result = mergeSourceImportsV36(listOf(source), BookSourceImportResultV36(listOf(changed), emptyList()))
        assertEquals(listOf(source), result.sources)
        assertEquals(1, result.skipped.size)
    }

    @Test fun remoteRegexRunsLinearlyAndUnsupportedConstructsAreRejected() {
        assertEquals("正文", sourceRegexReplaceV36("正文广告", "广告", ""))
        val adversarial = "a".repeat(10000)
        assertEquals(adversarial, sourceRegexReplaceV36(adversarial, "(a+)+b", ""))
        assertTrue(runCatching { sourceRegexReplaceV36("正文", "(?<=正)文", "") }.isFailure)
        assertTrue(runCatching { ruleElementsV36(Jsoup.parse("<p>aaaa</p>"), "@css:p:matches((a+)+b)") }.isFailure)
        assertTrue(runCatching { sourceRegexReplaceV36("正文", "x".repeat(1025), "") }.isFailure)
    }
}
