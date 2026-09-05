package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.reader.decodeLocalBookTextV1
import com.xiguli.langhuan.ui.reader.normalizeBookBytesV1
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookImportV1Test {
    @Test
    fun `utf8 text keeps chinese characters`() {
        val raw = "第一章\n你好，琅嬛".toByteArray(Charsets.UTF_8)
        assertEquals("第一章\n你好，琅嬛", decodeLocalBookTextV1(raw))
    }

    @Test
    fun `utf8 bom is removed`() {
        val body = "书名\n正文".toByteArray(Charsets.UTF_8)
        val raw = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body
        assertEquals("书名\n正文", decodeLocalBookTextV1(raw))
    }

    @Test
    fun `gb18030 local novel can be decoded`() {
        val raw = "第十二章 夜雨\n他推开了门。".toByteArray(Charset.forName("GB18030"))
        val decoded = decodeLocalBookTextV1(raw)
        assertTrue(decoded.contains("第十二章 夜雨"))
        assertTrue(decoded.contains("他推开了门"))
    }

    @Test
    fun `epub bytes are not transcoded`() {
        val raw = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertTrue(raw.contentEquals(normalizeBookBytesV1("book.epub", raw)))
    }
}
