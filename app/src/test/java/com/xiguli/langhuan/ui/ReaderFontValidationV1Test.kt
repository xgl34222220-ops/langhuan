package com.xiguli.langhuan.ui

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ReaderFontValidationV1Test {
    @Test
    fun `non-font and truncated sfnt never reach native parser`() {
        listOf("not a font".toByteArray(), ByteArray(24), byteArrayOf(0, 1, 0, 0),
            validStructure().copyOf(20), validStructure().also { ByteBuffer.wrap(it).putShort(4, 4096) }).forEach {
            rejected(it)
        }
    }

    @Test
    fun `table offsets lengths overlap and duplicate tags are rejected`() {
        listOf(
            validStructure().also { ByteBuffer.wrap(it).putInt(20, -4) },
            validStructure().also { ByteBuffer.wrap(it).putInt(24, -1) },
            validStructure().also { ByteBuffer.wrap(it).putInt(20, 0) },
            validStructure().also { ByteBuffer.wrap(it).putInt(20, 109) },
            validStructure().also { ByteBuffer.wrap(it).putInt(36, 108) },
            validStructure().also { it.copyInto(it, 28, 12, 16) },
        ).forEach(::rejected)
    }

    @Test
    fun `missing required tables bad head magic and zero glyph count are rejected`() {
        listOf(
            validStructure().also { "junk".toByteArray().copyInto(it, 12) },
            validStructure().also { ByteBuffer.wrap(it).putInt(108 + 12, 0) },
            validStructure().also { ByteBuffer.wrap(it).putShort(164 + 4, 0) },
        ).forEach(::rejected)
    }

    @Test
    fun `structural check does not replace real font parser`() {
        withFont(validStructure()) { file ->
            var parsed = 0
            val failure = runCatching { validateReaderFontFileV1(file) { parsed++; false } }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(1, parsed)
        }
    }

    @Test
    fun `trueType and OpenType headers continue to native parser`() {
        for (signature in listOf(0x00010000, 0x4F54544F, 0x74727565)) {
            withFont(validStructure().also { ByteBuffer.wrap(it).putInt(0, signature) }) { file ->
                var parsed = false
                validateReaderFontFileV1(file) { assertEquals(file, it); parsed = true; true }
                assertTrue(parsed)
            }
        }
    }

    private fun rejected(bytes: ByteArray) = withFont(bytes) { file ->
        var parsed = false
        val failure = runCatching { validateReaderFontFileV1(file) { parsed = true; true } }.exceptionOrNull()
        assertTrue("Malformed font must fail", failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("字体格式无效"))
        assertFalse("Malformed structure must not reach native parser", parsed)
    }

    /** Deliberately only structurally valid: Android must independently parse actual font contents. */
    private fun validStructure(): ByteArray {
        val tables = linkedMapOf("head" to 54, "maxp" to 6, "cmap" to 4, "hhea" to 36, "hmtx" to 4, "name" to 6)
        val buffer = ByteBuffer.allocate(228)
        buffer.putInt(0x00010000).putShort(6).putShort(64).putShort(2).putShort(32)
        var offset = 108
        tables.forEach { (tag, length) ->
            buffer.put(tag.toByteArray()).putInt(0).putInt(offset).putInt(length)
            offset += (length + 3) / 4 * 4
        }
        buffer.putInt(108 + 12, 0x5F0F3CF5)
        buffer.putShort(164 + 4, 1)
        return buffer.array()
    }

    private fun withFont(bytes: ByteArray, action: (File) -> Unit) {
        val file = File.createTempFile("reader-font-test-", ".ttf")
        try { file.writeBytes(bytes); action(file) } finally { file.delete() }
    }
}
