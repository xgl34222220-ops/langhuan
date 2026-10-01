package com.xiguli.langhuan.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

internal object LocalImportLimitsV1 {
    const val BOOK_BYTES = 96 * 1024 * 1024
    const val FONT_BYTES = 24 * 1024 * 1024
    const val EPUB_ENTRY_BYTES = 16 * 1024 * 1024
    const val EPUB_EXPANDED_BYTES = 96 * 1024 * 1024
    const val EPUB_ENTRIES = 10_000
}

internal fun checkImportThreadV1() {
    if (Thread.currentThread().isInterrupted) throw CancellationException("导入已取消")
}

/** Never asks the source for more than the remaining allowance plus one detection byte. */
internal fun InputStream.copyBoundedImportV1(
    output: OutputStream?,
    limit: Int,
    message: String,
    checkCancelled: () -> Unit = ::checkImportThreadV1,
): Int {
    require(limit >= 0)
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        checkCancelled()
        val requested = minOf(buffer.size.toLong(), limit.toLong() - total + 1).toInt()
        var count = read(buffer, 0, requested)
        if (count < 0) break
        // Some content providers return zero without EOF. Still make bounded progress.
        if (count == 0) {
            checkCancelled()
            val byte = read()
            if (byte < 0) break
            buffer[0] = byte.toByte()
            count = 1
        }
        checkCancelled()
        require(count <= limit - total) { message }
        output?.write(buffer, 0, count)
        total += count
    }
    return total
}

internal fun InputStream.readBoundedImportV1(
    limit: Int,
    message: String,
    checkCancelled: () -> Unit = ::checkImportThreadV1,
): ByteArray = ByteArrayOutputStream(minOf(limit, 8192)).use { output ->
    copyBoundedImportV1(output, limit, message, checkCancelled)
    output.toByteArray()
}

internal data class EpubReadLimitsV1(
    val inputBytes: Int = LocalImportLimitsV1.BOOK_BYTES,
    val entryBytes: Int = LocalImportLimitsV1.EPUB_ENTRY_BYTES,
    val expandedBytes: Int = LocalImportLimitsV1.EPUB_EXPANDED_BYTES,
    val entries: Int = LocalImportLimitsV1.EPUB_ENTRIES,
)

/** Every entry consumes the same budget, including directories, ignored assets and duplicate names. */
internal fun readBoundedEpubV1(
    bytes: ByteArray,
    normalizeName: (String) -> String = { it },
    keepEntry: (String) -> Boolean = { true },
    checkCancelled: () -> Unit = ::checkImportThreadV1,
    limits: EpubReadLimitsV1 = EpubReadLimitsV1(),
): Map<String, ByteArray> {
    require(limits.inputBytes >= 0 && limits.entryBytes >= 0 && limits.expandedBytes >= 0 && limits.entries >= 0)
    checkCancelled()
    require(bytes.size <= limits.inputBytes) { "EPUB 文件过大，目前单本最大支持 96 MB" }
    val archive = linkedMapOf<String, ByteArray>()
    var expanded = 0
    var count = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            checkCancelled()
            val entry = zip.nextEntry ?: break
            require(count < limits.entries) { "EPUB 文件条目过多，最多支持 ${limits.entries} 项" }
            count++
            val remaining = limits.expandedBytes - expanded
            val allowed = minOf(limits.entryBytes, remaining)
            val message = if (remaining < limits.entryBytes) {
                "EPUB 解压后总大小过大，目前最大支持 96 MB"
            } else {
                "EPUB 单个文件解压后过大，目前最大支持 16 MB"
            }
            val name = normalizeName(entry.name)
            if (!entry.isDirectory && name.isNotBlank() && keepEntry(name)) {
                val data = zip.readBoundedImportV1(allowed, message, checkCancelled)
                expanded += data.size
                archive[name] = data
            } else {
                expanded += zip.copyBoundedImportV1(null, allowed, message, checkCancelled)
            }
            // EOF has been reached through the bounded path; closeEntry cannot drain unchecked data.
            zip.closeEntry()
        }
    }
    return archive
}
