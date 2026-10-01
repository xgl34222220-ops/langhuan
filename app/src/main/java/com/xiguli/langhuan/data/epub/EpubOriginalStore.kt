package com.xiguli.langhuan.data.epub

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Exact original bytes + an offline sanitized rendering copy. Text-reader progress is untouched. */
class EpubOriginalStore(private val root: File) {
    data class Prepared(val sha256: String, val original: File, val rendering: File, val archive: EpubArchivePolicy.Archive)

    fun prepare(input: InputStream, checkCancelled: () -> Unit = {}): Prepared {
        check(root.isDirectory || root.mkdirs()) { "无法创建 EPUB 目录" }
        val temporary = File.createTempFile("import-", ".pending", root)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    checkCancelled()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= EpubArchivePolicy.MAX_ARCHIVE) { "EPUB 文件超过 96 MB" }
                    hash.update(buffer, 0, read); output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
            val archive = EpubArchivePolicy.validate(temporary, checkCancelled)
            val digest = hash.digest().joinToString("") { "%02x".format(it) }
            val original = File(root, "$digest.epub")
            val rendering = File(root, "$digest.safe-${EpubContentSanitizer.POLICY_VERSION}.epub")
            if (!original.exists()) move(temporary, original)
            if (!rendering.exists()) writeRendering(original, rendering, archive, checkCancelled)
            return Prepared(digest, original, rendering, archive)
        } finally { temporary.delete() }
    }

    /** Called before committing a NEW book's DB transaction, or after explicit reassociation. */
    fun associate(bookId: String, prepared: Prepared) {
        require(prepared.original.isFile && prepared.rendering.isFile) { "EPUB 原文件尚未准备完成" }
        atomicWrite(pointer(bookId), prepared.sha256.toByteArray(Charsets.US_ASCII))
    }

    fun hasOriginal(bookId: String): Boolean = original(bookId)?.isFile == true
    fun original(bookId: String): File? = digest(bookId)?.let { File(root, "$it.epub") }
    fun digest(bookId: String): String? = runCatching {
        pointer(bookId).takeIf { it.isFile && it.length() == 64L }?.readText()?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
    }.getOrNull()

    fun open(bookId: String, checkCancelled: () -> Unit = {}): Prepared {
        val original = original(bookId) ?: error("这本书尚未保存原 EPUB，请重新关联原文件")
        // Revalidate and regenerate the safe derivative on policy upgrades, never trust a pointer alone.
        return original.inputStream().use { prepare(it, checkCancelled) }
    }

    fun loadLocator(bookId: String, digest: String): String? = runCatching {
        locatorFile(bookId, digest).takeIf { it.isFile && it.length() <= 64 * 1024 }?.readText()
    }.getOrNull()

    fun saveLocator(bookId: String, digest: String, json: String) {
        require(json.toByteArray().size <= 64 * 1024)
        atomicWrite(locatorFile(bookId, digest), json.toByteArray(Charsets.UTF_8))
    }

    private fun pointer(bookId: String) = File(root, "book-${key(bookId)}.ref")
    private fun locatorFile(bookId: String, digest: String): File {
        require(digest.matches(Regex("[a-f0-9]{64}")))
        return File(root, "book-${key(bookId)}-$digest.locator.json")
    }
    private fun key(bookId: String): String {
        require(bookId.isNotBlank() && bookId.length <= 512)
        return MessageDigest.getInstance("SHA-256").digest(bookId.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun writeRendering(source: File, target: File, archive: EpubArchivePolicy.Archive, checkCancelled: () -> Unit) {
        val temporary = File.createTempFile("render-", ".pending", root)
        try {
            FileOutputStream(temporary).use { output ->
                ZipOutputStream(output).use { destination ->
                    ZipFile(source).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            checkCancelled()
                            val entry = entries.nextElement()
                            if (entry.isDirectory) continue
                            val name = entry.name
                            var bytes = zip.getInputStream(entry).use { EpubArchivePolicy.readBounded(it, EpubArchivePolicy.MAX_ENTRY, checkCancelled) }
                            val type = archive.mediaTypes[name].orEmpty()
                            bytes = when {
                                type in setOf("application/xhtml+xml", "text/html") || name.endsWith(".xhtml", true) || name.endsWith(".html", true) -> EpubContentSanitizer.markup(bytes)
                                type == "image/svg+xml" || name.endsWith(".svg", true) -> EpubContentSanitizer.markup(bytes, svg = true)
                                type == "text/css" || name.endsWith(".css", true) -> EpubContentSanitizer.css(bytes.toString(Charsets.UTF_8)).toByteArray()
                                type.contains("xml") || name.endsWith(".xml", true) || name.endsWith(".opf", true) || name.endsWith(".ncx", true) -> {
                                    val xml = EpubArchivePolicy.safeXml(bytes)
                                    xml.doctype?.let { xml.removeChild(it) }
                                    java.io.ByteArrayOutputStream().also { buffer ->
                                        TransformerFactory.newInstance().newTransformer().transform(DOMSource(xml), StreamResult(buffer))
                                    }.toByteArray()
                                }
                                else -> bytes
                            }
                            destination.putNextEntry(ZipEntry(name))
                            destination.write(bytes); destination.closeEntry()
                        }
                    }
                    destination.finish()
                    output.fd.sync()
                }
            }
            checkCancelled()
            move(temporary, target)
        } finally { temporary.delete() }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        check(root.isDirectory || root.mkdirs())
        val temporary = File.createTempFile("save-", ".pending", root)
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            move(temporary, file)
        } finally { temporary.delete() }
    }
    private fun move(from: File, to: File) = Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
