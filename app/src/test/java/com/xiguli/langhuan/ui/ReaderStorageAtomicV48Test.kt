package com.xiguli.langhuan.ui

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderStorageAtomicV48Test {
    @get:Rule val folder = TemporaryFolder()
    private fun original() = File(folder.root, "notes.json").apply { writeText("original notes") }
    @Test fun replacesCompleteFileAndLeavesNoPendingFile() {
        val file = original()
        writeReaderArchiveAtomicallyV48(file, "new notes".toByteArray())
        assertEquals("new notes", file.readText())
        assertEquals(listOf("notes.json"), folder.root.list()!!.toList())
    }
    @Test fun partialWriteFailurePreservesEveryOriginalByte() {
        val file = original(); val before = file.readBytes()
        val result = runCatching { writeReaderArchiveAtomicallyV48(file, "new notes".toByteArray()) { output, _ -> output.write(1); throw IOException("fixture: disk full") } }
        assertTrue(result.isFailure)
        assertArrayEquals(before, file.readBytes())
        assertEquals(listOf("notes.json"), folder.root.list()!!.toList())
    }
    @Test fun cancellationBeforeAndAfterWritingDoesNotReplaceOriginal() {
        val file = original()
        try {
            Thread.currentThread().interrupt()
            assertTrue(runCatching { writeReaderArchiveAtomicallyV48(file, byteArrayOf(1)) }.isFailure)
        } finally { Thread.interrupted() }
        try {
            assertTrue(runCatching { writeReaderArchiveAtomicallyV48(file, byteArrayOf(1)) { output, bytes -> output.write(bytes); Thread.currentThread().interrupt() } }.isFailure)
        } finally { Thread.interrupted() }
        assertEquals("original notes", file.readText())
        assertEquals(listOf("notes.json"), folder.root.list()!!.toList())
    }
    @Test fun failedFirstWriteDoesNotCreateAnEmptyArchive() {
        val file = File(folder.root, "new-book.json")
        assertTrue(runCatching { writeReaderArchiveAtomicallyV48(file, byteArrayOf(1)) { _, _ -> throw IOException("fixture") } }.isFailure)
        assertFalse(file.exists())
        assertEquals(0, folder.root.list()!!.size)
    }
}
