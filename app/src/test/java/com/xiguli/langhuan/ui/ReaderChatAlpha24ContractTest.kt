package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderChatAlpha24ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun pagedReaderHonorsAllTypeControls() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("val pagedParagraphSpacing = paragraphSpacing"))
        assertFalse(reader.contains("else 0f\n    val pagination"))
        assertTrue(reader.contains("lineFactor - .10f"))
        assertTrue(reader.contains("lineFactor + .10f"))
        assertTrue(reader.contains("sidePadding - 4f"))
        assertTrue(reader.contains("sidePadding + 4f"))
        assertTrue(reader.contains("coerceAtLeast(12f)"))
        assertTrue(reader.contains("coerceAtMost(48f)"))
    }

    @Test
    fun creationChatFallsBackOnlyBeforeAnyVisibleOutput() {
        val chat = source("src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt")
        assertTrue(chat.contains("val canFallbackToDefault = routedSelection?.inheritedGlobal == false"))
        assertTrue(chat.contains("if (!canFallbackToDefault || emittedContent) throw error"))
        assertTrue(chat.contains("runReply(routingSession.defaultGateway)"))
        assertTrue(chat.contains("任务模型未响应，正在切回全局默认模型"))
    }
}
