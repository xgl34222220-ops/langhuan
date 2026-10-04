package com.xiguli.langhuan.ui

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFailurePresentationV69Test {
    @Test fun nestedTimeoutBecomesBoundedActionableChineseCopy() {
        val message = sourceFailureMessageV69(
            IllegalStateException("outer transport wrapper", SocketTimeoutException("Read timed out: private-host.example")),
        )
        assertEquals(SOURCE_TIMEOUT_MESSAGE_V69, message)
        assertFalse(message.contains("private-host.example"))
        assertTrue(message.contains("重试"))
    }

    @Test fun ordinaryFailureKeepsUsefulDiagnosticButRemovesNewlines() {
        val message = sourceFailureMessageV69(IllegalStateException("HTTP 503\r\n合成服务暂不可用"))
        assertEquals("HTTP 503  合成服务暂不可用", message)
    }

    @Test fun nestedTlsFailureUsesSafeActionableCopyWithoutCertificateDetails() {
        val handshake = SSLHandshakeException("Chain validation failed: CN=private.books.example").apply {
            initCause(CertificateException("Trust anchor for certification path not found"))
        }
        val message = sourceFailureMessageV69(IllegalStateException("outer transport wrapper", handshake))
        assertEquals(SOURCE_TLS_MESSAGE_V70, message)
        assertFalse(message.contains("private.books.example"))
        assertFalse(message.contains("Trust anchor"))
        assertFalse(message.contains("忽略"))
    }

    @Test fun ordinaryDnsFailureIsSafeButAnApplicationBlockKeepsItsSpecificReason() {
        val lookup = sourceFailureMessageV69(
            IllegalStateException(
                "outer transport wrapper",
                UnknownHostException("Unable to resolve host private.books.example: No address associated with hostname"),
            ),
        )
        assertEquals(SOURCE_DNS_LOOKUP_MESSAGE_V71, lookup)
        assertFalse(lookup.contains("private.books.example"))

        val blocked = sourceFailureMessageV69(
            IllegalStateException(
                "outer transport wrapper",
                SourceDnsBlockedV54(SourceDnsFailureV54.BENCHMARK_RANGE, "books.example"),
            ),
        )
        assertTrue(blocked.contains("books.example"))
        assertTrue(blocked.contains("Fake-IP"))
        assertTrue(blocked.contains("尚未连接网站"))
        assertFalse(blocked.contains(SOURCE_DNS_LOOKUP_MESSAGE_V71))
    }
}
