package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAiWireCompatibilityTest {
    @Test fun readsClassicOpenAiContent() {
        val body = """{"choices":[{"message":{"content":"正常回答"}}]}"""
        assertEquals("正常回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsContentArrayUsedByCompatibleRelays() {
        val body = """{"choices":[{"message":{"content":[{"type":"text","text":"数组回答"}]}}]}"""
        assertEquals("数组回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun recoversReasoningOnlyDeepSeekRelay() {
        val body = """{"choices":[{"message":{"content":"","reasoning_content":"可恢复回答"}}]}"""
        assertEquals("可恢复回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsLegacyChoiceText() {
        val body = """{"choices":[{"text":"legacy text"}]}"""
        assertEquals("legacy text", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsResponsesApiOutputBlocks() {
        val body = """{"output":[{"content":[{"type":"output_text","text":"responses text"}]}]}"""
        assertEquals("responses text", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun readsSseAndPrefersVisibleContentOverReasoning() {
        val body = """
            data: {"choices":[{"delta":{"reasoning_content":"内部推演"}}]}
            data: {"choices":[{"delta":{"content":"最终回答"}}]}
            data: [DONE]
        """.trimIndent()
        assertEquals("最终回答", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, body))
    }

    @Test fun preservesPlainTextRelayResponse() {
        assertEquals("直接返回的文本", extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, "直接返回的文本"))
    }

    @Test fun stillRejectsHtmlErrorPage() {
        val result = runCatching { extractProviderText(ApiProtocol.OPENAI_COMPATIBLE, "<html>bad gateway</html>") }
        assertTrue(result.isFailure)
    }
}
