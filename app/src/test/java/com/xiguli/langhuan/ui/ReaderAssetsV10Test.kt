package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.reader.parseReaderHexColorV10
import com.xiguli.langhuan.ui.reader.splitReaderPagesV10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAssetsV10Test {
    @Test
    fun hexColorsAcceptRgbAndArgb() {
        assertNotNull(parseReaderHexColorV10("#F4F0E6"))
        assertNotNull(parseReaderHexColorV10("#FFF4F0E6"))
        assertEquals(null, parseReaderHexColorV10("#12345"))
    }

    @Test
    fun longTextSplitsIntoMultipleReadablePagesWithoutLoss() {
        val paragraph = "这是用于阅读分页测试的一段正文。".repeat(70)
        val text = List(8) { paragraph }.joinToString("\n\n")
        val pages = splitReaderPagesV10(text, 19f, 1.8f, 24f)
        assertTrue(pages.size > 3)
        val restored = pages.joinToString("").replace("\n", "").replace(" ", "")
        val original = text.replace("\n", "").replace(" ", "")
        assertEquals(original, restored)
    }
}
