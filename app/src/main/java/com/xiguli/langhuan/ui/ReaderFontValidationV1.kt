package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.LocalImportLimitsV1
import com.xiguli.langhuan.data.checkImportThreadV1
import java.io.File
import java.io.RandomAccessFile

private const val INVALID_READER_FONT_V1 = "字体格式无效或文件已损坏，请选择有效的 TTF 或 OTF 字体"

/** Checks sfnt structure without loading table payloads; native parsing must also succeed. */
internal fun validateReaderFontFileV1(file: File, parseFont: (File) -> Boolean) {
    data class Table(val offset: Long, val length: Long)
    RandomAccessFile(file, "r").use { input ->
        val size = input.length()
        require(size in 12L..LocalImportLimitsV1.FONT_BYTES.toLong()) { INVALID_READER_FONT_V1 }
        val signature = input.readInt()
        require(signature == 0x00010000 || signature == 0x4F54544F || signature == 0x74727565) { INVALID_READER_FONT_V1 }
        val count = input.readUnsignedShort()
        // sfnt's 16-bit searchRange permits at most 4095 tables, bounding all metadata work.
        require(count in 1..4095) { INVALID_READER_FONT_V1 }
        val directoryEnd = 12L + count * 16L
        require(directoryEnd <= size) { INVALID_READER_FONT_V1 }
        input.seek(12)
        val tables = linkedMapOf<String, Table>()
        repeat(count) {
            checkImportThreadV1()
            val tagBytes = ByteArray(4).also(input::readFully)
            require(tagBytes.all { it.toInt() in 32..126 }) { INVALID_READER_FONT_V1 }
            val tag = tagBytes.toString(Charsets.US_ASCII)
            input.readInt() // Checksum is not used as a validity substitute for native parsing.
            val offset = input.readInt().toLong() and 0xFFFF_FFFFL
            val length = input.readInt().toLong() and 0xFFFF_FFFFL
            require(offset >= directoryEnd && offset % 4 == 0L && offset <= size && length <= size - offset) {
                INVALID_READER_FONT_V1
            }
            require(tables.put(tag, Table(offset, length)) == null) { INVALID_READER_FONT_V1 }
        }
        var previousEnd = directoryEnd
        tables.values.filter { it.length > 0 }.sortedBy { it.offset }.forEach { table ->
            require(table.offset >= previousEnd) { INVALID_READER_FONT_V1 }
            previousEnd = table.offset + table.length
        }
        // Required sfnt table prefixes. Detailed cmap/glyph/outline checks remain native parser work.
        mapOf("head" to 54, "maxp" to 6, "cmap" to 4, "hhea" to 36, "hmtx" to 4, "name" to 6).forEach { (tag, minimum) ->
            require((tables[tag]?.length ?: 0) >= minimum) { INVALID_READER_FONT_V1 }
        }
        input.seek(tables.getValue("head").offset + 12)
        require(input.readInt() == 0x5F0F3CF5) { INVALID_READER_FONT_V1 }
        input.seek(tables.getValue("maxp").offset + 4)
        require(input.readUnsignedShort() > 0) { INVALID_READER_FONT_V1 }
    }
    checkImportThreadV1()
    require(parseFont(file)) { INVALID_READER_FONT_V1 }
}
