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
            assertTrue(rejected.error.orEmpty().contains("EPUB 单个"))
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
            val invalid = ReaderFontStoreV10.import(app, Uri.fromFile(oversized))
            assertTrue(invalid.isFailure)
            assertTrue(invalid.exceptionOrNull()?.message.orEmpty().contains("字体格式无效"))
            assertEquals(before, directory.list().orEmpty().toSet())
            // A plausible sfnt signature must not turn a truncated table directory into a font.
            oversized.writeBytes(java.nio.ByteBuffer.allocate(28).apply {
                putInt(0x00010000); putShort(1); putShort(16); putShort(0); putShort(0)
                put("head".toByteArray()); putInt(0); putInt(28); putInt(54)
            }.array())
            assertTrue(ReaderFontStoreV10.import(app, Uri.fromFile(oversized)).isFailure)
            assertEquals(before, directory.list().orEmpty().toSet())
        } finally {
            oversized.delete()
        }
    }

    @Test
    fun realSystemFontImportsAfterValidationAndKeepsItsBytes() {
        val source = File("/system/fonts").listFiles().orEmpty().sortedBy { it.name }.firstOrNull {
            it.isFile && it.canRead() && it.extension.lowercase() in setOf("ttf", "otf") &&
                it.length() in 1..LocalImportLimitsV1.FONT_BYTES.toLong() &&
                runCatching { android.graphics.Typeface.Builder(it).build() != null }.getOrDefault(false)
        }
        assertNotNull("The Android image must provide a readable native-parseable TTF or OTF for this test", source)
        val fixture = File.createTempFile("valid-reader-font-", ".ttf", app.cacheDir)
        var imported: ReaderFontAssetV10? = null
        val directory = File(app.filesDir, "reader_fonts_v1")
        val before = directory.list().orEmpty().toSet()
        try {
            requireNotNull(source).inputStream().use { input -> fixture.outputStream().use { input.copyTo(it) } }
            val result = ReaderFontStoreV10.import(app, Uri.fromFile(fixture))
            assertTrue("Real font must remain importable: ${result.exceptionOrNull()}", result.isSuccess)
            imported = result.getOrThrow()
            assertEquals(fixture.length(), File(imported.path).length())
            assertArrayEquals(fixture.readBytes(), File(imported.path).readBytes())
            assertNotNull(android.graphics.Typeface.Builder(File(imported.path)).build())
            assertEquals(before + File(imported.path).name, directory.list().orEmpty().toSet())
        } finally {
            imported?.let { ReaderFontStoreV10.delete(it) }
            fixture.delete()
        }
    }

    private fun epub(padding: Int): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) {
                val bytes = text.toByteArray()
                val item = ZipEntry(name)
                if (name == "mimetype") {
                    item.method = ZipEntry.STORED; item.size = bytes.size.toLong()
                    item.crc = java.util.zip.CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(item); zip.write(bytes); zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            entry("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0" unique-identifier="id"><metadata><dc:identifier id="id">original-import-bounds</dc:identifier><dc:title>本地导入边界测试</dc:title><dc:language>zh</dc:language><meta property="dcterms:modified">2026-10-01T00:00:00Z</meta></metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("c.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>正文</title></head><body><p>合法正文保留</p></body></html>")
            entry("nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>目录</title></head><body><nav epub:type="toc"><ol><li><a href="c.xhtml">正文</a></li></ol></nav></body></html>""")
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
