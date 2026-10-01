package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.LocalImportLimitsV1
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real import ViewModel and Room shelf; only document acquisition is substituted. */
class LocalImportBoundsV1DeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    @Test
    fun expandedLimitFailureLeavesShelfUntouchedAndNextLegalImportSucceeds() = runBlocking {
        val manager = StoryProjectManager(app)
        val before = manager.observeStories().first()
        val active = manager.activeStoryId()
        val metadata = app.getSharedPreferences("local_book_meta_v1", 0)
        val metadataBefore = metadata.all.toMap()
        val vm = LocalBookImportViewModelV1(app)
        var createdId: String? = null
        try {
            val bomb = epub(LocalImportLimitsV1.EPUB_ENTRY_BYTES + 1)
            instrumentation.runOnMainSync {
                vm.importDocument({ "oversized.epub" }, { bomb.inputStream() })
            }
            val rejected = withTimeout(20_000) { vm.state.first { it.error != null } }
            assertTrue(rejected.error.orEmpty().contains("EPUB 单个文件"))
            assertNull(rejected.importedBookId)
            assertFalse(rejected.busy)
            assertEquals(before, manager.observeStories().first())
            assertEquals(active, manager.activeStoryId())
            assertEquals(metadataBefore, metadata.all)

            val legal = epub(0)
            instrumentation.runOnMainSync { vm.importDocument({ "legal.epub" }, { legal.inputStream() }) }
            val imported = withTimeout(20_000) { vm.state.first { it.importedBookId != null || it.error != null } }
            assertNull(imported.error)
            createdId = requireNotNull(imported.importedBookId)
            assertTrue(manager.chapterDrafts(createdId).single().content.contains("合法正文保留"))
            assertEquals(before.size + 1, manager.observeStories().first().size)
        } finally {
            instrumentation.runOnMainSync { vm.viewModelScope.cancel() }
            createdId?.let { id ->
                val sql = LanghuanDatabase.get(app).openHelper.writableDatabase
                listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                    sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
                }
                metadata.edit().also { editor -> metadata.all.keys.filter { it.endsWith("_$id") }.forEach(editor::remove) }.commit()
                File(app.filesDir, "local_toc_v1/$id.json").delete()
                if (active == null) manager.clearActiveStoryId() else manager.setActiveStoryId(active)
            }
        }
    }

    @Test
    fun cancellationDuringReadClosesInputAndNeverWritesShelfOrImportError() = runBlocking {
        val manager = StoryProjectManager(app)
        val before = manager.observeStories().first()
        val vm = LocalBookImportViewModelV1(app)
        var closed = false
        var bytesRead = 0
        val input = object : InputStream() {
            override fun read(): Int = error("Bulk reads expected")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill(65, offset, offset + length)
                bytesRead += length
                vm.viewModelScope.cancel()
                return length
            }
            override fun close() { closed = true }
        }
        instrumentation.runOnMainSync { vm.importDocument({ "cancelled.txt" }, { input }) }
        withTimeout(20_000) { vm.state.first { !it.busy } }
        assertTrue(closed)
        assertEquals(8192, bytesRead)
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.importedBookId)
        assertEquals(before, manager.observeStories().first())
    }

    @Test
    fun oversizedAndInvalidFontsLeaveNoNewFontOrPartialFile() {
        val directory = File(app.filesDir, "reader_fonts_v1")
        val before = directory.list().orEmpty().toSet()
        val oversized = File.createTempFile("font-limit-", ".ttf", app.cacheDir)
        try {
            java.io.RandomAccessFile(oversized, "rw").use { it.setLength(LocalImportLimitsV1.FONT_BYTES.toLong() + 1) }
            val rejected = ReaderFontStoreV10.import(app, Uri.fromFile(oversized))
            assertTrue(rejected.exceptionOrNull()?.message.orEmpty().contains("24 MB"))
            assertEquals(before, directory.list().orEmpty().toSet())
            oversized.writeText("not a font")
            assertTrue(ReaderFontStoreV10.import(app, Uri.fromFile(oversized)).isFailure)
            assertEquals(before, directory.list().orEmpty().toSet())
        } finally {
            oversized.delete()
        }
    }

    private fun epub(padding: Int): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            entry("book.opf", """<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>本地导入边界测试</dc:title></metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("c.xhtml", "<html><body><p>合法正文保留</p></body></html>")
            zip.putNextEntry(ZipEntry("ignored.bin"))
            val block = ByteArray(8192) { 65 }
            var remaining = padding
            while (remaining > 0) {
                val count = minOf(remaining, block.size)
                zip.write(block, 0, count)
                remaining -= count
            }
            zip.closeEntry()
        }
    }.toByteArray()
}
