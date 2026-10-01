package com.xiguli.langhuan.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LocalImportBoundsV1Test {
    @Test
    fun `production book and font limits stop infinite sources on one detection byte`() {
        for (limit in listOf(LocalImportLimitsV1.BOOK_BYTES, LocalImportLimitsV1.FONT_BYTES)) {
            val input = CountingInput(Int.MAX_VALUE)
            assertTrue(runCatching { input.copyBoundedImportV1(null, limit, "too big") }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(limit + 1, input.consumed)
        }
    }

    @Test
    fun `oversized stream stops exactly on limit plus one without writing excess`() {
        for (limit in listOf(0, 7, 8191, 8192, 8193)) {
            val input = CountingInput(limit + 50_000)
            val output = ByteArrayOutputStream()
            val failure = runCatching { input.copyBoundedImportV1(output, limit, "too big") }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(limit + 1, input.consumed)
            assertTrue(output.size() <= limit)
        }
    }

    @Test
    fun `exact limit and zero-returning stream preserve data`() {
        val bytes = ByteArray(8193) { (it % 251).toByte() }
        val source = bytes.inputStream()
        val intermittent = object : InputStream() {
            var zero = true
            override fun read() = source.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                zero = !zero
                return if (!zero) 0 else source.read(buffer, offset, length)
            }
        }
        assertArrayEquals(bytes, intermittent.readBoundedImportV1(bytes.size, "too big"))
        assertArrayEquals(byteArrayOf(), byteArrayOf().inputStream().readBoundedImportV1(0, "too big"))
    }

    @Test
    fun `archive input size checked before ZIP parsing`() {
        val failure = runCatching {
            readBoundedEpubV1(ByteArray(33), limits = EpubReadLimitsV1(inputBytes = 32))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("EPUB 文件过大"))
    }

    @Test
    fun `cumulative expansion includes discarded directory and colliding entries`() {
        val cases = listOf(
            zipOf("one.bin" to 6, "two.bin" to 6) to false,
            zipOf("directory/" to 6, "two.bin" to 6) to true,
            zipOf("one.bin" to 6, "same.bin" to 6) to true,
        )
        cases.forEach { (zip, keep) ->
            val failure = runCatching {
                readBoundedEpubV1(zip, normalizeName = { "same" }, keepEntry = { keep },
                    limits = EpubReadLimitsV1(entryBytes = 8, expandedBytes = 10))
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message.orEmpty().contains("总大小"))
        }
    }

    @Test
    fun `entry count includes empty directories and stops before extra entry body`() {
        val archive = zipOf("one/" to 0, "two/" to 0, "three.bin" to 100)
        val failure = runCatching {
            readBoundedEpubV1(archive, limits = EpubReadLimitsV1(entries = 2, entryBytes = 1))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("条目过多"))
        assertEquals(2, readBoundedEpubV1(zipOf("one" to 4, "two" to 4),
            limits = EpubReadLimitsV1(entryBytes = 4, expandedBytes = 8, entries = 2)).size)
    }

    @Test
    fun `cancellation propagates during reads and through all EPUB entry points`() {
        val cancelled = CancellationException("test cancellation")
        val input = CountingInput(50_000)
        val failure = runCatching {
            input.readBoundedImportV1(40_000, "too big") { if (input.consumed >= 8192) throw cancelled }
        }.exceptionOrNull()
        assertSame(cancelled, failure)
        assertEquals(8192, input.consumed)
        val archive = epubWithPadding(20_000)
        val checks: List<(() -> Unit) -> Unit> = listOf(
            { EpubImporterV2.import("book.epub", archive, it) },
            { EpubOriginalTocV1.extract(archive, 1, it) },
            { StoryExchange.`import`("book.epub", archive, it) },
        )
        checks.forEach { parse ->
            var polls = 0
            assertSame(cancelled, runCatching { parse { if (++polls == 6) throw cancelled } }.exceptionOrNull())
        }
    }

    @Test
    fun `all EPUB entry points reject oversized ignored resources`() {
        val epub = epubWithPadding(16 * 1024 * 1024 + 1)
        val importers: List<() -> Unit> = listOf(
            { EpubImporterV2.import("book.epub", epub) },
            { EpubOriginalTocV1.extract(epub, 1) },
            { StoryExchange.`import`("book.epub", epub) },
        )
        importers.forEachIndexed { index, importer ->
            val failure = runCatching(importer).exceptionOrNull()
            assertTrue("EPUB entry point $index must reject expanded resource", failure is IllegalArgumentException)
            assertTrue(failure?.message.orEmpty().contains("EPUB"))
        }
    }

    @Test
    fun `ordinary EPUB still keeps readable body`() {
        val epub = epubWithPadding(0)
        assertEquals("Bounds book", EpubImporterV2.import("book.epub", epub).manuscript.title)
        assertTrue(EpubImporterV2.import("book.epub", epub).manuscript.chapters.single().content.contains("Readable body"))
        assertTrue(StoryExchange.`import`("book.epub", epub).chapters.single().content.contains("Readable body"))
    }

    private fun epubWithPadding(padding: Int): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry("book.opf", """<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>Bounds book</dc:title></metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""")
            entry("c.xhtml", "<html><body><h1>Chapter one</h1><p>Readable body</p></body></html>")
            zip.putNextEntry(ZipEntry("ignored.bin"))
            val block = ByteArray(8192) { 'a'.code.toByte() }
            var remaining = padding
            while (remaining > 0) {
                val count = minOf(remaining, block.size)
                zip.write(block, 0, count)
                remaining -= count
            }
            zip.closeEntry()
        }
    }.toByteArray()

    private fun zipOf(vararg entries: Pair<String, Int>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, size) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(ByteArray(size) { 42 })
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private class CountingInput(private val length: Int) : InputStream() {
        var consumed = 0
        override fun read(): Int = if (consumed == length) -1 else { consumed++; 42 }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (consumed == this.length) return -1
            val count = minOf(length, this.length - consumed)
            buffer.fill(42, offset, offset + count)
            consumed += count
            return count
        }
    }
}
