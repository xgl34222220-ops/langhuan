package com.xiguli.langhuan.data.epub

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.Closeable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Exact original bytes + an offline sanitized rendering copy. Text-reader progress is untouched. */
class EpubOriginalStore(private val root: File) {
    data class Prepared(val sha256: String, val original: File, val rendering: File, val archive: EpubArchivePolicy.Archive)

    /** Owns only one new import's private staging directory, never an existing book or cache. */
    class Staged internal constructor(val prepared: Prepared, private val directory: File) : Closeable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                deleteOwnedDirectory(directory)
            } catch (error: Exception) {
                closed.set(false)
                throw error
            }
        }
    }

    /** Uncommitted input stays isolated until the user confirms its shelf/association transaction. */
    fun stage(input: InputStream, checkCancelled: () -> Unit = {}): Staged {
        check(root.isDirectory || root.mkdirs()) { "无法创建 EPUB 目录" }
        val directory = Files.createTempDirectory(root.toPath(), "pending-import-").toFile()
        try {
            val prepared = EpubOriginalStore(directory).prepare(input, checkCancelled)
            checkCancelled()
            return Staged(prepared, directory)
        } catch (error: Exception) {
            // The owned directory contains no published references or another book's files.
            runCatching { deleteOwnedDirectory(directory) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

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
    fun associate(bookId: String, prepared: Prepared, checkCancelled: () -> Unit = {}): Prepared {
        require(prepared.original.isFile && prepared.rendering.isFile) { "EPUB 原文件尚未准备完成" }
        require(prepared.sha256.matches(Regex("[a-f0-9]{64}"))) { "EPUB 原文件标识无效" }
        val destination = pointer(bookId) // Validate identity before copying any bytes.
        check(root.isDirectory || root.mkdirs()) { "无法创建 EPUB 目录" }
        val original = File(root, "${prepared.sha256}.epub")
        val rendering = File(root, "${prepared.sha256}.safe-${EpubContentSanitizer.POLICY_VERSION}.epub")
        if (prepared.original.canonicalFile == original.canonicalFile && prepared.rendering.canonicalFile == rendering.canonicalFile) {
            checkCancelled()
            synchronized(PUBLISH_LOCK) { atomicWrite(destination, prepared.sha256.toByteArray(Charsets.US_ASCII)) }
            return prepared
        }
        var originalCopy: File? = null
        var renderingCopy: File? = null
        try {
            originalCopy = copyForPublication(prepared.original, prepared.sha256, checkCancelled)
            renderingCopy = copyForPublication(prepared.rendering, null, checkCancelled)
            synchronized(PUBLISH_LOCK) {
                checkCancelled()
                var createdOriginal = false
                var createdRendering = false
                try {
                    if (original.exists()) {
                        require(original.isFile && fileDigest(original, checkCancelled) == prepared.sha256) { "已保存的 EPUB 校验失败，原文件已保留" }
                    } else {
                        move(requireNotNull(originalCopy), original); createdOriginal = true
                    }
                    if (rendering.exists()) {
                        require(rendering.isFile) { "已保存的 EPUB 排版文件无效，原文件已保留" }
                    } else {
                        move(requireNotNull(renderingCopy), rendering); createdRendering = true
                    }
                    atomicWrite(destination, prepared.sha256.toByteArray(Charsets.US_ASCII))
                } catch (error: Exception) {
                    // Never remove reused/shared data. Unknown or damaged references also retain it.
                    if (!hasReferenceOrUncertainty(prepared.sha256)) {
                        if (createdRendering) rendering.delete()
                        if (createdOriginal) original.delete()
                    }
                    throw error
                }
            }
            return Prepared(prepared.sha256, original, rendering, prepared.archive)
        } finally {
            originalCopy?.delete()
            renderingCopy?.delete()
        }
    }

    private fun copyForPublication(source: File, expectedHash: String?, checkCancelled: () -> Unit): File {
        val temporary = File.createTempFile("adopt-", ".pending", root)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input -> FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= EpubArchivePolicy.MAX_EXPANDED * 2) { "EPUB 暂存文件过大" }
                    digest.update(buffer, 0, count); output.write(buffer, 0, count)
                }
                output.fd.sync()
            } }
            if (expectedHash != null) require(digest.digest().joinToString("") { "%02x".format(it) } == expectedHash) { "EPUB 暂存文件已变化，未关联" }
            return temporary
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun fileDigest(file: File, checkCancelled: () -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) { checkCancelled(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hasReferenceOrUncertainty(digest: String): Boolean = root.listFiles()?.any { file ->
        file.name.startsWith("book-") && file.name.endsWith(".ref") &&
            runCatching { !file.isFile || file.length() != 64L || file.readText().let { !it.matches(Regex("[a-f0-9]{64}")) || it == digest } }.getOrDefault(true)
    } ?: true

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
                                type in setOf("application/xhtml+xml", "text/html") || name.endsWith(".xhtml", true) || name.endsWith(".html", true) -> EpubContentSanitizer.markup(bytes, xhtml = type == "application/xhtml+xml" || name.endsWith(".xhtml", true))
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

    private companion object {
        val PUBLISH_LOCK = Any()

        fun deleteOwnedDirectory(directory: File) {
            if (!Files.exists(directory.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
            // Never follow a symlink out of this attempt's private directory.
            Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
