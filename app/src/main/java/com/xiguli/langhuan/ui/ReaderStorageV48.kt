package com.xiguli.langhuan.ui

import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Same-directory atomic replacement. Failure/cancellation never truncates the original file. */
internal fun writeReaderArchiveAtomicallyV48(
    file: File, bytes: ByteArray,
    write: (FileOutputStream, ByteArray) -> Unit = { output, content -> output.write(content) },
) {
    fun checkActive() { if (Thread.currentThread().isInterrupted) throw InterruptedIOException("保存已取消") }
    checkActive()
    val parent = file.absoluteFile.parentFile ?: error("归档目录不可用")
    check(parent.isDirectory || parent.mkdirs()) { "无法创建归档目录" }
    val temporary = File.createTempFile(".${file.name}.", ".pending", parent)
    try {
        FileOutputStream(temporary).use { stream -> write(stream, bytes); stream.fd.sync() }
        checkActive()
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { temporary.delete() }
}
