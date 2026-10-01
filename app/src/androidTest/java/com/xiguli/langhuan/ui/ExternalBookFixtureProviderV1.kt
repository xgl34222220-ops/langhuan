package com.xiguli.langhuan.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Installed only in the test APK; paths select hard-coded original fixtures, never disk files. */
class ExternalBookFixtureProviderV1 : ContentProvider() {
    override fun onCreate() = true
    private fun payload(uri: Uri): Pair<String, ByteArray> = when (uri.path) {
        "/novel.txt" -> "外部TXT受控测试.txt" to "第一章 初遇\n窗外的树影慢慢移动。这是为导入测试编写的原创小说正文。".toByteArray()
        "/novel.epub" -> "外部EPUB受控测试.epub" to ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                mapOf(
                    "mimetype" to "application/epub+zip",
                    "META-INF/container.xml" to "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OPS/book.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
                    "OPS/book.opf" to "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">original-import-fixture</dc:identifier><dc:title>外部EPUB受控测试</dc:title><dc:language>zh</dc:language><meta property=\"dcterms:modified\">2026-10-01T00:00:00Z</meta></metadata><manifest><item id=\"one\" href=\"one.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"one\"/></spine></package>",
                    "OPS/one.xhtml" to "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>第一章 初遇</title></head><body><h1>第一章 初遇</h1><p>窗外的树影慢慢移动。这是为导入测试编写的原创小说正文。</p></body></html>",
                ).forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            }
        }.toByteArray()
        else -> throw FileNotFoundException("Unknown synthetic fixture")
    }
    override fun getType(uri: Uri): String { payload(uri); return "application/octet-stream" }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val (name, bytes) = payload(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply { addRow(columns.map { when (it) { OpenableColumns.DISPLAY_NAME -> name; OpenableColumns.SIZE -> bytes.size; else -> null } }) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        val bytes = payload(uri).second
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) } }
        }.apply { isDaemon = true; start() }
        return pipe[0]
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}
