package com.xiguli.langhuan.ui

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal enum class LocalBookFormatV1(val extension: String, val label: String) {
    TEXT("txt", "TXT"), MARKDOWN("md", "Markdown"), EPUB("epub", "EPUB"),
}

internal data class LocalBookPayloadV1(
    val fileName: String,
    val bytes: ByteArray,
    val format: LocalBookFormatV1,
)

/** Names and MIME types are hints only. Archive candidates still require full EPUB validation. */
internal fun prepareLocalBookPayloadV1(
    displayName: String,
    mimeType: String?,
    bytes: ByteArray,
    checkCancelled: () -> Unit = {},
): LocalBookPayloadV1 {
    require(bytes.isNotEmpty()) { "文件是空的" }
    checkCancelled()
    val name = safeLocalBookNameV1(displayName)
    val extension = name.substringAfterLast('.', "").lowercase()
    val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
    if (isZipBookCandidateV1(bytes)) {
        // Never run a mislabeled EPUB through a text decoder. EpubImporterV2 verifies OPF/spine
        // and readable chapters, and charges every ZIP entry against the existing size limits.
        return LocalBookPayloadV1(bookNameWithExtensionV1(name, "epub"), bytes, LocalBookFormatV1.EPUB)
    }
    val textExtensions = setOf("txt", "md", "markdown")
    require(extension != "epub" && (mime != "application/epub+zip" || extension in textExtensions)) { "这个文件不是有效的 EPUB 压缩包" }
    val textMimes = setOf("text/plain", "text/markdown", "text/x-markdown")
    val genericMimes = setOf("", "application/octet-stream", "binary/octet-stream", "application/zip", "application/x-zip-compressed", "*/*")
    require(extension in textExtensions || mime in textMimes ||
        (extension in setOf("", "bin", "dat") && mime in genericMimes)) {
        "目前支持 TXT、Markdown 和 EPUB 小说文件"
    }
    val prefix = bytes.take(16).toByteArray().toString(StandardCharsets.ISO_8859_1)
    require(!prefix.startsWith("%PDF-") && !prefix.startsWith("GIF8") &&
        !prefix.startsWith("\u0089PNG") && !prefix.startsWith("\u00ff\u00d8\u00ff") &&
        !prefix.startsWith("MZ") && !prefix.startsWith("\u007fELF") && !prefix.startsWith("SQLite format 3")) {
        "这个文件不是可阅读的 TXT、Markdown 或 EPUB 小说"
    }
    val text = decodeLocalBookTextV1(bytes)
    var invalid = 0
    text.forEachIndexed { index, char ->
        if (index % 8192 == 0) checkCancelled()
        require(char != '\u0000') { "文件包含二进制内容，无法作为小说导入" }
        if ((char.isISOControl() && char !in "\n\r\t\u000c") || char == '\ufffd') invalid++
    }
    require(invalid <= maxOf(1, text.length / 100) && text.any { !it.isWhitespace() && !it.isISOControl() }) {
        "没有识别到可阅读文本，请检查文件格式或编码"
    }
    val format = if (extension in setOf("md", "markdown") || mime in setOf("text/markdown", "text/x-markdown")) {
        LocalBookFormatV1.MARKDOWN
    } else LocalBookFormatV1.TEXT
    checkCancelled()
    return LocalBookPayloadV1(bookNameWithExtensionV1(name, format.extension), text.toByteArray(StandardCharsets.UTF_8), format)
}

internal fun safeLocalBookNameV1(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
    .filterNot { it.isISOControl() }.trim().take(180).ifBlank { "外部小说" }

private fun bookNameWithExtensionV1(name: String, extension: String): String =
    name.substringBeforeLast('.', name).ifBlank { "外部小说" } + ".$extension"

internal fun normalizeBookBytesV1(fileName: String, bytes: ByteArray): ByteArray {
    if (fileName.endsWith(".epub", ignoreCase = true) || isZipBookCandidateV1(bytes)) return bytes
    return decodeLocalBookTextV1(bytes).toByteArray(StandardCharsets.UTF_8)
}

private fun isZipBookCandidateV1(bytes: ByteArray): Boolean =
    bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) ||
            (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()) ||
            (bytes[2] == 7.toByte() && bytes[3] == 8.toByte()))

internal fun decodeLocalBookTextV1(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val decoded = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
        else -> decodeStrictUtf8V1(bytes) ?: Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString()
    }
    return decoded.removePrefix("\uFEFF")
}

private fun decodeStrictUtf8V1(bytes: ByteArray): String? = runCatching {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()
