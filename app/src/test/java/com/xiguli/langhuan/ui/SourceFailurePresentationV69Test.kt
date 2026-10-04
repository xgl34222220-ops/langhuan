package com.xiguli.langhuan.ui

import java.net.SocketTimeoutException
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
}
