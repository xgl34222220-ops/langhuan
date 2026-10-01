package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.EpubImporterV2
import com.xiguli.langhuan.data.LocalImportLimitsV1
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ExternalBookImportV1Test {
    @Test fun `view and share deliveries coalesce while pending but can be deliberately reopened`() {
        val queue = ExternalBookImportQueueV1()
        assertTrue(queue.offer(VIEW, URI, "text/plain"))
        assertFalse(queue.offer(SEND, URI, "application/octet-stream"))
        assertEquals(1, queue.snapshot().size)
        val restored = ExternalBookImportQueueV1(queue.snapshot())
        assertFalse(restored.offer(VIEW, URI, "text/plain"))
        restored.dismiss(URI)
        assertTrue(restored.offer(VIEW, URI, "text/plain"))
    }

    @Test fun `distinct queued files keep order and cancelling one preserves the next`() {
        val queue = ExternalBookImportQueueV1()
        queue.offer(VIEW, URI, null)
        queue.offer(SEND, "$URI/next", null)
        queue.dismiss(URI)
        assertEquals("$URI/next", queue.snapshot().single().uri)
    }

    @Test fun `network file path missing and unsupported intent inputs are rejected`() {
        listOf(null, "", "https://example.com/book.txt", "file:///sdcard/book.txt", "/sdcard/book.txt", "content:///no-authority", "content://user@provider/book").forEach { uri ->
            assertThrows(IllegalArgumentException::class.java) { ExternalBookImportQueueV1().offer(VIEW, uri, "text/plain") }
        }
        assertThrows(IllegalArgumentException::class.java) { ExternalBookImportQueueV1().offer("android.intent.action.SEND_MULTIPLE", URI, null) }
    }

    @Test fun `incoming queue has a finite bound without discarding an existing request`() {
        val queue = ExternalBookImportQueueV1()
        repeat(4) { queue.offer(SEND, "$URI/$it", null) }
        val before = queue.snapshot()
        assertThrows(IllegalArgumentException::class.java) { queue.offer(SEND, "$URI/5", null) }
        assertEquals(before, queue.snapshot())
    }

    @Test fun `restored state cannot smuggle paths network URLs or malformed content providers`() {
        listOf("file:///private/not-read.txt", "https://example.com/book.txt", "http://example.com/book.txt",
            "content:///missing-authority", "content://user@provider/book", "content://provider/" + "a".repeat(8192),
            "content://bad authority/book").forEach { injected ->
            val queue = ExternalBookImportQueueV1(listOf(ExternalBookRequestV1(injected), ExternalBookRequestV1(URI)))
            assertEquals(listOf(ExternalBookRequestV1(URI)), queue.snapshot())
        }
    }

    @Test fun `mislabeled epub keeps its original archive bytes and parses readable content`() {
        val raw = epub()
        listOf("book.txt" to "text/plain", "download" to "application/octet-stream", "book.bin" to "image/jpeg").forEach { (name, mime) ->
            val prepared = prepareLocalBookPayloadV1(name, mime, raw)
            assertSame(raw, prepared.bytes)
            assertSame(raw, normalizeBookBytesV1(name, raw))
            assertEquals(LocalBookFormatV1.EPUB, prepared.format)
            assertTrue(EpubImporterV2.import(prepared.fileName, prepared.bytes).manuscript.chapters.single().content.contains("这是正确的正文"))
        }
    }

    @Test fun `ordinary zip is never accepted as a book just because it starts with PK`() {
        val raw = archive("readme.txt" to "This is an ordinary zip")
        val prepared = prepareLocalBookPayloadV1("book.txt", "text/plain", raw)
        assertThrows(IllegalStateException::class.java) { EpubImporterV2.import(prepared.fileName, prepared.bytes) }
    }

    @Test fun `mislabeled archive still applies expanded entry limits`() {
        val raw = epub(ByteArray(LocalImportLimitsV1.EPUB_ENTRY_BYTES + 1) { 65 }.toString(Charsets.US_ASCII))
        val prepared = prepareLocalBookPayloadV1("book.txt", "text/plain", raw)
        val error = assertThrows(IllegalArgumentException::class.java) { EpubImporterV2.import(prepared.fileName, prepared.bytes) }
        assertTrue(error.message.orEmpty().contains("EPUB 单个文件"))
    }

    @Test fun `known text extension tolerates a wrong MIME without corrupting Chinese`() {
        val raw = "第一章 夜雨\n他推开了门。".toByteArray(Charset.forName("GB18030"))
        val prepared = prepareLocalBookPayloadV1("小说.TXT", "image/jpeg", raw)
        assertEquals(LocalBookFormatV1.TEXT, prepared.format)
        assertEquals("第一章 夜雨\n他推开了门。", prepared.bytes.toString(Charsets.UTF_8))
        assertEquals(LocalBookFormatV1.TEXT, prepareLocalBookPayloadV1("小说.TXT", "application/epub+zip", raw).format)
    }

    @Test fun `provider without a filename can supply readable plain text or markdown MIME`() {
        val text = "# 第一章 夜雨\n他推开了门。".toByteArray()
        assertEquals(LocalBookFormatV1.TEXT, prepareLocalBookPayloadV1("", null, text).format)
        assertEquals(LocalBookFormatV1.MARKDOWN, prepareLocalBookPayloadV1("document-42", "text/markdown; charset=utf-8", text).format)
    }

    @Test fun `UTF16 novels are readable even though source bytes contain zeros`() {
        val text = "第一章\n这是正文"
        val raw = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, prepareLocalBookPayloadV1("novel.txt", "application/octet-stream", raw).bytes.toString(Charsets.UTF_8))
    }

    @Test fun `empty binary PDF and false epub labels fail before a manuscript is created`() {
        val cases = listOf(
            Triple("book.txt", "text/plain", byteArrayOf()),
            Triple("book.txt", "text/plain", byteArrayOf(0, 1, 2, 3)),
            Triple("book.txt", "text/plain", "%PDF-1.7 example".toByteArray()),
            Triple("book.epub", "application/epub+zip", "Plain text is not an epub".toByteArray()),
            Triple("script.apk", "application/vnd.android.package-archive", "executable".toByteArray()),
        )
        cases.forEach { (name, mime, bytes) ->
            assertThrows(IllegalArgumentException::class.java) { prepareLocalBookPayloadV1(name, mime, bytes) }
        }
    }

    @Test fun `cancellation propagates during text validation`() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            prepareLocalBookPayloadV1("big.txt", null, "正文".repeat(10_000).toByteArray()) {
                checks++
                if (checks > 2) throw CancellationException("test cancellation")
            }
        }
        assertEquals(3, checks)
    }

    @Test fun `provider filename is display text not a filesystem path`() {
        assertEquals("小说.txt", safeLocalBookNameV1("../../folder/小说.txt\n"))
        assertEquals("外部小说", safeLocalBookNameV1("\n\r"))
    }

    private fun epub(ignored: String = ""): ByteArray = archive(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""",
        "book.opf" to """<package xmlns:dc="http://purl.org/dc/elements/1.1/"><metadata><dc:title>测试小说</dc:title></metadata><manifest><item id="c" href="c.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c"/></spine></package>""",
        "c.xhtml" to "<html><body><p>这是正确的正文</p></body></html>",
        "ignored.bin" to ignored,
    )

    private fun archive(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry() }
        }
    }.toByteArray()

    companion object {
        private const val VIEW = "android.intent.action.VIEW"
        private const val SEND = "android.intent.action.SEND"
        private const val URI = "content://files.example/opaque/1234"
    }
}
