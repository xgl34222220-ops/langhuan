package com.xiguli.langhuan.data

import com.xiguli.langhuan.data.epub.EpubArchivePolicy
import com.xiguli.langhuan.data.epub.EpubContentSanitizer
import com.xiguli.langhuan.data.epub.EpubOriginalStore
import com.xiguli.langhuan.data.epub.EpubWebContentPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EpubOriginalSafetyTest {
    @Test fun externalDtdIsRemovedBeforeThePlatformParserAndCdataIsPreserved() {
        val document = EpubArchivePolicy.safeXml("""<?xml version="1.0"?><!DOCTYPE root SYSTEM "https://example.invalid/book.dtd"><root>ordinary&nbsp;text<![CDATA[<!DOCTYPE is quoted text>]]></root>""".toByteArray())
        assertNull(document.doctype)
        assertEquals("ordinary\u00a0text<!DOCTYPE is quoted text>", document.documentElement.textContent)
    }
    @Test fun markupDepthIsBoundedBeforeRecursiveXmlSerialization() {
        val levels = EpubArchivePolicy.MAX_MARKUP_DEPTH + 1
        for (tag in listOf("node", "p")) {
            val xml = ("<$tag>".repeat(levels) + "text" + "</$tag>".repeat(levels)).toByteArray()
            assertThrows(IllegalArgumentException::class.java) { EpubArchivePolicy.safeXml(xml) }
        }
        val html = ("<div>".repeat(levels) + "text" + "</div>".repeat(levels)).toByteArray()
        assertThrows(IllegalArgumentException::class.java) { EpubContentSanitizer.markup(html) }
        // Ordinary HTML optional paragraph endings remain accepted by the HTML parser.
        EpubContentSanitizer.markup(("<html><body>" + "<p>text".repeat(200) + "</body></html>").toByteArray())
    }

    @Test fun rasterBytesCannotAvoidLimitsByClaimingToBeAFont() {
        val misdeclared = modified {
            this["OPS/book.opf"] = getValue("OPS/book.opf").toString(Charsets.UTF_8)
                .replace("image/png", "font/ttf").toByteArray()
        }
        rejects(misdeclared)
        val oversized = modified {
            val png = getValue("OPS/art.png").copyOf()
            // 8193px exceeds the documented side limit regardless of an OPF media label.
            png[16] = 0; png[17] = 0; png[18] = 32; png[19] = 1
            this["OPS/art.png"] = png
            this["OPS/book.opf"] = getValue("OPS/book.opf").toString(Charsets.UTF_8)
                .replace("image/png", "font/ttf").toByteArray()
        }
        rejects(oversized)
        rejects(modified {
            this["OPS/book.opf"] = getValue("OPS/book.opf").toString(Charsets.UTF_8)
                .replace("image/svg+xml", "font/ttf").toByteArray()
        })
    }

    @Test fun markupNodeCountIsBoundedWithoutRejectingOrdinaryAuthorCss() {
        val oversized = "<root>" + "<node/>".repeat(EpubArchivePolicy.MAX_MARKUP_NODES) + "</root>"
        assertThrows(IllegalArgumentException::class.java) { EpubArchivePolicy.safeXml(oversized.toByteArray()) }
        EpubContentSanitizer.markup("<html><head><style>p:before {content:'<word>';}</style></head><body><p>text</p></body></html>".toByteArray())
    }
    private fun fixture(name: String = "original-reflow.epub") = javaClass.getResourceAsStream("/epub/$name")!!.use { it.readBytes() }
    private fun root() = Files.createTempDirectory("epub-test").toFile().apply { deleteOnExit() }
    private fun modified(change: MutableMap<String, ByteArray>.() -> Unit): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(fixture().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
        }
        entries.change()
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip ->
            entries.forEach { (path, data) -> zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry() }
        } }.toByteArray()
    }
    private fun rejects(bytes: ByteArray) {
        assertThrows(Exception::class.java) { EpubOriginalStore(root()).prepare(bytes.inputStream()) }
    }

    @Test fun originalBytesImagesFontsAndAuthorCssSurvive() {
        val bytes = fixture()
        val store = EpubOriginalStore(root())
        val book = store.prepare(bytes.inputStream())
        assertArrayEquals(bytes, book.original.readBytes())
        assertEquals(listOf("OPS/one.xhtml", "OPS/two.xhtml"), book.archive.spine)
        java.util.zip.ZipFile(book.rendering).use { zip ->
            val html = zip.getInputStream(zip.getEntry("OPS/one.xhtml")).bufferedReader().readText()
            assertTrue(html.contains("art.png")); assertTrue(html.contains("art.svg"))
            assertTrue(html.contains("author.css")); assertTrue(html.contains("Content-Security-Policy"))
            assertFalse(html.contains("bookScriptExecuted")); assertFalse(html.contains("onerror="))
            assertFalse(html.contains("example.invalid")); assertFalse(html.contains("iframe"))
            assertTrue(zip.getEntry("OPS/font.ttf").size > 0)
            assertTrue(zip.getInputStream(zip.getEntry("OPS/author.css")).bufferedReader().readText().contains("@font-face"))
        }
    }

    @Test fun fixedLayoutMetadataSurvives() {
        val book = EpubOriginalStore(root()).prepare(fixture("original-fixed.epub").inputStream())
        java.util.zip.ZipFile(book.rendering).use { zip ->
            assertTrue(zip.getInputStream(zip.getEntry("OPS/book.opf")).bufferedReader().readText().contains("pre-paginated"))
        }
    }

    @Test fun epub2NcxAndExternalDtdRemainReadableOffline() {
        val book = EpubOriginalStore(root()).prepare(fixture("original-epub2.epub").inputStream())
        java.util.zip.ZipFile(book.rendering).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OPS/book.opf")).bufferedReader().readText()
            val ncx = zip.getInputStream(zip.getEntry("OPS/toc.ncx")).bufferedReader().readText()
            assertTrue(opf.contains("version=\"2.0\"")); assertTrue(opf.contains("toc=\"ncx\""))
            assertFalse(ncx.contains("DOCTYPE", true)); assertTrue(ncx.contains("one.xhtml#start"))
            assertTrue(ncx.contains("Original\u00a0EPUB2"))
        }
    }

    @Test fun validEpub2DoctypeAndNamedEntitiesRemainReadable() {
        val book = EpubOriginalStore(root()).prepare(modified {
            this["OPS/one.xhtml"] = """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Book</title></head><body><p>A&nbsp;B &copy; &mdash; &amp; C</p><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><rect width="10" height="10" fill="#ff0000"/></svg><div style="background-image:url('art.png')">Background</div></body></html>""".toByteArray()
        }.inputStream())
        java.util.zip.ZipFile(book.rendering).use { zip ->
            val html = zip.getInputStream(zip.getEntry("OPS/one.xhtml")).bufferedReader().readText()
            val document = Jsoup.parse(html)
            assertTrue(document.text().contains("A B © — & C"))
            assertEquals(1, document.select("svg rect").size)
            assertTrue(html.contains("background-image:url('art.png')"))
            assertFalse(html.contains("DOCTYPE", true))
        }
        assertTrue(EpubArchivePolicy.safeXml("<ncx><text>A&nbsp;B</text></ncx>".toByteArray()).documentElement.textContent.contains('\u00a0'))
    }

    @Test fun internalEntitiesAndExternalEntityDeclarationsAreRejected() {
        rejects(modified { this["OPS/one.xhtml"] = "<!DOCTYPE html [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><html><head/><body>&xxe;</body></html>".toByteArray() })
        rejects(modified { this["META-INF/container.xml"] = "<!DOCTYPE container [<!ENTITY a 'x'>]><container>&a;</container>".toByteArray() })
    }

    @Test fun archiveTraversalAndEncodedAliasesAreRejected() {
        listOf("../evil", "/absolute", "OPS/../alias", "OPS\\bad", "OPS/%2e%2e/bad", "OPS/x?y", "C:/bad").forEach { name ->
            rejects(modified { this[name] = "x".toByteArray() })
        }
    }

    @Test fun duplicateArchiveEntryRejected() {
        // ZIP writers refuse duplicate names; equal-length raw name replacement produces the adversarial archive.
        val bytes = modified { this["OPS/dup.png"] = this["OPS/art.png"]!! }
        val needle = "OPS/dup.png".toByteArray(); val replacement = "OPS/art.png".toByteArray()
        for (i in 0..bytes.size - needle.size) if (bytes.copyOfRange(i, i + needle.size).contentEquals(needle)) replacement.copyInto(bytes, i)
        rejects(bytes)
    }

    @Test fun oversizedRasterAndMissingSpineRejected() {
        rejects(modified {
            val png = this["OPS/art.png"]!!.copyOf(); png[16] = 0x7f; png[17] = 0x7f; this["OPS/art.png"] = png
        })
        rejects(modified { this["OPS/book.opf"] = this["OPS/book.opf"]!!.toString(Charsets.UTF_8).replace("idref=\"one\"", "idref=\"missing\"").toByteArray() })
        rejects(modified { this["OPS/art.svg"] = "<svg xmlns='http://www.w3.org/2000/svg' width='999999in' height='100'/>".toByteArray() })
        rejects(modified { this["OPS/one.xhtml"] = "<html><head/><body><svg width='1e100' height='100'/></body></html>".toByteArray() })
    }

    @Test fun oversizedEntryAndEntryCountRejected() {
        rejects(modified { this["OPS/bomb"] = ByteArray(EpubArchivePolicy.MAX_ENTRY.toInt() + 1) })
        rejects(modified { repeat(EpubArchivePolicy.MAX_ENTRIES) { this["items/$it"] = byteArrayOf() } })
    }

    @Test fun totalExpandedArchiveBoundIsEnforcedAcrossEntries() {
        val zeros = ByteArray(14 * 1024 * 1024)
        rejects(modified { repeat(7) { this["OPS/expansion-$it"] = zeros } })
    }

    @Test fun archiveInputBoundIsEnforcedBeforePublishing() {
        val directory = root()
        val endless = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, off: Int, len: Int): Int { buffer.fill(0, off, off + len); return len }
        }
        assertThrows(IllegalArgumentException::class.java) { EpubOriginalStore(directory).prepare(endless) }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun drmRejectedButStandardFontObfuscationAllowed() {
        val encryption = """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#"><EncryptionMethod Algorithm="ALGORITHM"/><CipherData><CipherReference URI="OPS/font.ttf"/></CipherData></EncryptedData></encryption>"""
        rejects(modified { this["META-INF/encryption.xml"] = encryption.replace("ALGORITHM", "http://www.w3.org/2001/04/xmlenc#aes256-cbc").toByteArray() })
        EpubOriginalStore(root()).prepare(modified {
            this["META-INF/encryption.xml"] = encryption.replace("ALGORITHM", "http://www.idpf.org/2008/embedding").toByteArray()
        }.inputStream())
    }

    @Test fun publishingFailurePreservesOldBookAndLocators() {
        val directory = root(); val store = EpubOriginalStore(directory)
        val original = store.prepare(fixture().inputStream()); store.associate("book", original)
        store.saveLocator("book", original.sha256, "old location")
        val replacement = store.prepare(fixture("original-fixed.epub").inputStream())
        replacement.rendering.delete()
        assertThrows(IllegalArgumentException::class.java) { store.associate("book", replacement) }
        assertEquals(original.sha256, store.digest("book"))
        assertEquals("old location", store.loadLocator("book", original.sha256))
        assertEquals(original.sha256, EpubOriginalStore(directory).open("book").sha256)
    }

    @Test fun locatorsAreIsolatedByBookAndOriginalHash() {
        val store = EpubOriginalStore(root()); val one = store.prepare(fixture().inputStream()); val two = store.prepare(fixture("original-fixed.epub").inputStream())
        store.associate("a", one); store.associate("b", one)
        store.saveLocator("a", one.sha256, "A"); store.saveLocator("b", one.sha256, "B")
        store.associate("a", two)
        assertNull(store.loadLocator("a", two.sha256)); assertEquals("A", store.loadLocator("a", one.sha256)); assertEquals("B", store.loadLocator("b", one.sha256))
    }

    @Test fun dangerousMarkupIsRemovedWhileLocalArtSurvives() {
        val result = EpubContentSanitizer.markup("""<html><head><base href="https://bad/"/><link rel="stylesheet" href="author.css"/><meta http-equiv="refresh" content="0;url=https://bad"/></head><body onload="attack()"><svg><foreignObject><iframe srcdoc="bad"/></foreignObject><a xlink:href="javascript:alert(1)">bad</a><set attributeName="href" to="javascript:x"/><image xlink:href="art.svg"/></svg><img src="content://bad"/><p style="background:url(https://bad/x)">good</p><a href="two.xhtml#second">local</a></body></html>""".toByteArray(), xhtml = true).toString(Charsets.UTF_8)
        val doc = Jsoup.parse(result)
        assertTrue(doc.select("script,iframe,foreignObject,set,base").isEmpty())
        assertFalse(result.contains("onload=")); assertFalse(result.contains("javascript:")); assertFalse(result.contains("content://")); assertFalse(result.contains("https://bad"))
        assertTrue(result.contains("art.svg")); assertTrue(result.contains("two.xhtml#second"))
        assertTrue(EpubContentSanitizer.CSP.contains(EpubWebContentPolicy.REFLOW_HASH))
        assertFalse(EpubContentSanitizer.CSP.contains("readium_package"))
        assertFalse(EpubContentSanitizer.CSP.contains("readium_assets"))
        assertFalse(EpubContentSanitizer.CSP.substringAfter("script-src ").substringBefore(';').contains("'unsafe-inline'"))
    }

    @Test fun relativePathResolutionCannotEscapeOrAuthorizeAnOrigin() {
        assertEquals("OPS/art.png", EpubArchivePolicy.localPath("OPS/text/ch.xhtml", "../art.png"))
        assertNull(EpubArchivePolicy.localPath("OPS/ch.xhtml", "../../etc/passwd"))
        listOf("https://readium_assets/readium/scripts/readium-fixed.js", "file:///x", "//bad/x", "%6aavascript:bad", "content://x").forEach {
            assertNull(EpubArchivePolicy.localPath("", it)); assertFalse(EpubContentSanitizer.isLocalReference(it))
        }
    }

    @Test fun cancellationDoesNotPublishAPointer() {
        val store = EpubOriginalStore(root())
        assertThrows(InterruptedException::class.java) { store.prepare(fixture().inputStream()) { throw InterruptedException() } }
        assertFalse(store.hasOriginal("new-book"))
    }

    private fun bytesUnder(directory: File): Map<String, List<Byte>> = directory.walkTopDown()
        .filter { it.isFile }.associate { it.relativeTo(directory).path to it.readBytes().toList() }

    @Test fun cancellationAfterOriginalCopyRemovesOnlyItsUnpublishedStaging() {
        val directory = root()
        try {
            val store = EpubOriginalStore(directory)
            var reachedCopiedOriginal = false
            assertThrows(InterruptedException::class.java) {
                store.stage(fixture().inputStream()) {
                    if (directory.walkTopDown().any { it.isFile && it.name.endsWith(".epub") && !it.name.contains(".safe-") }) {
                        reachedCopiedOriginal = true
                        throw InterruptedException("cancel during rendering")
                    }
                }
            }
            assertTrue(reachedCopiedOriginal)
            assertTrue(directory.listFiles()!!.isEmpty())
            assertFalse(store.hasOriginal("new"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun dismissingPreparedImportPreservesAllExistingBytesAndLocations() {
        val directory = root()
        try {
            val store = EpubOriginalStore(directory)
            val old = store.prepare(fixture().inputStream())
            store.associate("old", old); store.saveLocator("old", old.sha256, "kept locator")
            val before = bytesUnder(directory)
            val staged = store.stage(fixture("original-fixed.epub").inputStream())
            assertTrue(staged.prepared.original.isFile)
            assertFalse(store.hasOriginal("new"))
            staged.close(); staged.close()
            assertEquals(before, bytesUnder(directory))
            assertFalse(staged.prepared.original.exists())
            assertEquals("kept locator", store.loadLocator("old", old.sha256))
        } finally { directory.deleteRecursively() }
    }

    @Test fun confirmedImportUsesCanonicalFilesAfterStagingIsClosed() {
        val directory = root()
        try {
            val store = EpubOriginalStore(directory)
            val bytes = fixture()
            val staged = store.stage(bytes.inputStream())
            val published = store.associate("new", staged.prepared)
            assertEquals(directory.canonicalFile, published.original.parentFile.canonicalFile)
            assertNotEquals(staged.prepared.original, published.original)
            staged.close()
            assertArrayEquals(bytes, store.original("new")!!.readBytes())
            assertTrue(store.open("new").rendering.isFile)
            assertTrue(directory.listFiles()!!.none { it.isDirectory || it.name.endsWith(".pending") })
        } finally { directory.deleteRecursively() }
    }

    @Test fun concurrentEqualImportsDoNotOwnEachOthersStagingOrPublishedOriginal() {
        val directory = root()
        try {
            val store = EpubOriginalStore(directory)
            val first = store.stage(fixture().inputStream())
            val second = store.stage(fixture().inputStream())
            store.associate("first", first.prepared)
            first.close()
            assertTrue(second.prepared.original.isFile)
            store.associate("second", second.prepared)
            second.close()
            assertEquals(store.digest("first"), store.digest("second"))
            assertArrayEquals(fixture(), store.open("first").original.readBytes())
            assertArrayEquals(fixture(), store.open("second").original.readBytes())
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancelledPublicationKeepsPreviousAssociationAndRemovesOwnedCopy() {
        val directory = root()
        try {
            val store = EpubOriginalStore(directory)
            val old = store.prepare(fixture().inputStream())
            store.associate("book", old); store.saveLocator("book", old.sha256, "position")
            val before = bytesUnder(directory)
            store.stage(fixture("original-fixed.epub").inputStream()).use { staged ->
                assertThrows(InterruptedException::class.java) {
                    store.associate("book", staged.prepared) {
                        if (directory.listFiles()!!.any { it.name.startsWith("adopt-") }) throw InterruptedException("copy cancelled")
                    }
                }
                assertEquals(old.sha256, store.digest("book"))
            }
            assertEquals(before, bytesUnder(directory))
        } finally { directory.deleteRecursively() }
    }

    @Test fun changedStagedOriginalCannotReplacePreviousBook() {
        val directory = root()
        try {
            val store = EpubOriginalStore(directory)
            val old = store.prepare(fixture().inputStream())
            store.associate("book", old)
            val before = bytesUnder(directory)
            store.stage(fixture("original-fixed.epub").inputStream()).use { staged ->
                staged.prepared.original.appendText("synthetic changed bytes")
                assertThrows(IllegalArgumentException::class.java) { store.associate("book", staged.prepared) }
            }
            assertEquals(before, bytesUnder(directory))
        } finally { directory.deleteRecursively() }
    }

    @Test fun stagingCleanupDoesNotFollowAnExternalSymlink() {
        val directory = root(); val elsewhere = root()
        try {
            val sentinel = File(elsewhere, "keep.txt").apply { writeText("other book") }
            val staged = EpubOriginalStore(directory).stage(fixture().inputStream())
            Files.createSymbolicLink(File(staged.prepared.original.parentFile, "external").toPath(), elsewhere.toPath())
            staged.close()
            assertEquals("other book", sentinel.readText())
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively(); elsewhere.deleteRecursively() }
    }

    @Test fun invalidStagedArchiveLeavesNoImportedOriginalOrTemporaryDirectory() {
        val directory = root()
        try {
            assertThrows(Exception::class.java) { EpubOriginalStore(directory).stage("invalid EPUB".byteInputStream()) }
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun finalSdkInjectionUsesSameOriginResourcesAndHashPinnedScripts() {
        val content = """<html xmlns="http://www.w3.org/1999/xhtml"><head><link rel="stylesheet" href="https://readium_assets/readium/readium-css/ReadiumCSS-before.css"/><link rel="stylesheet" href="author.css"/><style>@font-face{font-family:x;src:url('https://readium_assets/readium/fonts/OpenDyslexic-Regular.otf')}</style><script src="https://readium_assets/readium/scripts/readium-reflowable.js"></script><script src="book.js"></script><script>window.bad=true</script></head><body><img src="art.png"/></body></html>"""
        val result = EpubWebContentPolicy.secureFinalHtml(content.toByteArray(), xhtml = true).toString(Charsets.UTF_8)
        val doc = Jsoup.parse(result)
        assertEquals(1, doc.select("script").size)
        val script = doc.selectFirst("script")!!
        assertEquals(EpubWebContentPolicy.SDK_ALIAS + EpubWebContentPolicy.REFLOW_SCRIPT, script.attr("src"))
        assertEquals(EpubWebContentPolicy.REFLOW_HASH, script.attr("integrity"))
        assertTrue(doc.select("link").any { it.attr("href") == "author.css" })
        assertTrue(result.contains(EpubWebContentPolicy.SDK_ALIAS + "readium/readium-css/ReadiumCSS-before.css"))
        assertFalse(result.contains("https://readium_assets/"))
        assertFalse(result.contains("window.bad"))
        assertEquals("3", doc.selectFirst("html")!!.attr("data-langhuan-secure-readium"))
    }

    @Test fun sdkAliasCannotAuthorizeOutsideAssetsOrAcceptUnpinnedCode() {
        assertEquals("OPS/art.png", EpubWebContentPolicy.packagePath("https://readium_package/OPS/art.png"))
        for (url in listOf("https://evil/OPS/art.png", "https://readium_package.evil/x", "https://readium_package@evil/x",
            "file:///OPS/art.png", "content://book/x", "http://readium_package/x", "https://readium_package:443/x")) {
            assertNull(EpubWebContentPolicy.packagePath(url))
        }
        assertNull(EpubWebContentPolicy.aliasedAsset(EpubWebContentPolicy.SDK_ALIAS + "../secrets"))
        assertNull(EpubWebContentPolicy.aliasedAsset(EpubWebContentPolicy.SDK_ALIAS + "readium/scripts/book.js"))
        assertFalse(EpubWebContentPolicy.assetHashMatches(EpubWebContentPolicy.REFLOW_SCRIPT, "window.bad=true".toByteArray()))
        assertFalse(EpubWebContentPolicy.assetHashMatches("readium/scripts/book.js", ByteArray(0)))
    }
}
