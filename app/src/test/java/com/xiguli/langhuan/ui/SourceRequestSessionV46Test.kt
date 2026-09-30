package com.xiguli.langhuan.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SourceRequestSessionV46Test {
    private val source = BookSourceV36("s", "s", "https://example.org")

    @Test fun duplicateDocumentUsesCloneAndDoesNotRepeatNetworkAcrossRuleChanges() {
        var calls = 0
        val session = SourceRequestSessionV46 { _, request -> calls++; Jsoup.parse("<div id=body>book</div>", request.url) }
        val request = SourceRequestV36("https://example.org/book/1")
        session.document(source, request).select("#body").remove()
        assertEquals("book", session.document(source.copy(tocList = "a"), request).select("#body").text())
        assertEquals(1, calls)
    }

    @Test fun methodBodyAndHeaderIdentityDoNotShareDocuments() {
        var calls = 0
        val session = SourceRequestSessionV46 { _, request -> calls++; Jsoup.parse("ok", request.url) }
        val url = "https://example.org/search"
        session.document(source, SourceRequestV36(url))
        session.document(source, SourceRequestV36(url, "POST", "q=one"))
        session.document(source, SourceRequestV36(url, "POST", "q=two"))
        session.document(source.copy(headers = mapOf("Cookie" to "session=fixture")), SourceRequestV36(url))
        assertEquals(4, calls)
    }

    @Test fun first429StopsLaterRequestsAndModelRetryGuard() {
        var calls = 0
        val session = SourceRequestSessionV46 { _, _ -> calls++; throw SourceHttpStatusExceptionV44(429, "https://example.org", "limited") }
        repeat(5) { runCatching { session.document(source, SourceRequestV36("https://example.org/category/$it")) } }
        assertEquals(1, calls)
        assertTrue(runCatching { session.checkActive() }.exceptionOrNull() is SourceHttpStatusExceptionV44)
    }

    @Test fun browserChallengeStopsWithoutRetryOrTreatingItAsContent() {
        var calls = 0
        val session = SourceRequestSessionV46 { _, _ -> calls++; throw SourceBrowserChallengeV46("https://example.org") }
        repeat(3) { runCatching { session.document(source, SourceRequestV36("https://example.org/$it")) } }
        assertEquals(1, calls)
    }

    @Test fun retryAfterSupportsSecondsAndHttpDate() {
        assertEquals(120000L, sourceRetryAfterMillisV46("120", 0))
        assertEquals(60000L, sourceRetryAfterMillisV46("Thu, 01 Jan 1970 00:01:00 GMT", 0))
        assertNull(sourceRetryAfterMillisV46("secret-invalid"))
        assertNull(sourceRetryAfterMillisV46("-1"))
    }

    @Test fun limitedChallengeKeepsStatusAndFrequencyPrimaryWithoutEchoingBody() {
        val error = sourceHttpFailureV44(429, publicSourceUrlV36("https://example.org/?token=secret"),
            "<h1>Verify you are human</h1><div>private diagnostic</div>".toByteArray(), retryAfter = "120")
        assertTrue(error.message!!.contains("请求频率"))
        assertTrue(error.message!!.contains("120 秒"))
        assertTrue(error.browserChallenge)
        assertFalse(error.message!!.contains("secret"))
        assertFalse(error.message!!.contains("private diagnostic"))
    }

    @Test fun newsAndForeignLinksCannotBeDiscoveryEvidence() {
        val doc = Jsoup.parse("<a href='/novels/full'>完结小说</a><a href='/news/a'>JTBC新剧官宣</a><a href='https://outside.example/category'>修仙</a>", source.baseUrl)
        assertEquals(listOf("完结小说"), aiDiscoveryLinkEvidenceV37(doc).map { it.label })
    }
}
