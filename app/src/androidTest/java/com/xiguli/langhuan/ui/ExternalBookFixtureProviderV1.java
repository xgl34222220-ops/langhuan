package com.xiguli.langhuan.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Test APK process has no target-app Kotlin runtime. Fixed fixtures use Android/Java only. */
public final class ExternalBookFixtureProviderV1 extends ContentProvider {
    private static final class Payload {
        final String name;
        final byte[] bytes;
        Payload(String name, byte[] bytes) { this.name = name; this.bytes = bytes; }
    }
    @Override public boolean onCreate() { return true; }
    private Payload payload(Uri uri) {
        if ("/novel.txt".equals(uri.getPath())) {
            return new Payload("外部TXT受控测试.txt", "第一章 初遇\n窗外的树影慢慢移动。这是为导入测试编写的原创小说正文。".getBytes(StandardCharsets.UTF_8));
        }
        if (!"/novel.epub".equals(uri.getPath())) throw new IllegalArgumentException("Unknown synthetic fixture");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/epub+zip");
        entries.put("META-INF/container.xml", "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OPS/book.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
        entries.put("OPS/book.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"id\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"id\">original-import-fixture</dc:identifier><dc:title>外部EPUB受控测试</dc:title><dc:language>zh</dc:language><meta property=\"dcterms:modified\">2026-10-01T00:00:00Z</meta></metadata><manifest><item id=\"one\" href=\"one.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/></manifest><spine><itemref idref=\"one\"/></spine></package>");
        entries.put("OPS/one.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>第一章 初遇</title></head><body><h1>第一章 初遇</h1><p>窗外的树影慢慢移动。这是为导入测试编写的原创小说正文。</p></body></html>");
        entries.put("OPS/nav.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>目录</title></head><body><nav epub:type=\"toc\"><ol><li><a href=\"one.xhtml\">第一章 初遇</a></li></ol></nav></body></html>");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    byte[] data = entry.getValue().getBytes(StandardCharsets.UTF_8);
                    ZipEntry item = new ZipEntry(entry.getKey());
                    if ("mimetype".equals(entry.getKey())) {
                        java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(data);
                        item.setMethod(ZipEntry.STORED); item.setSize(data.length); item.setCrc(crc.getValue());
                    }
                    zip.putNextEntry(item);
                    zip.write(data);
                    zip.closeEntry();
                }
            }
            return new Payload("外部EPUB受控测试.epub", output.toByteArray());
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override public String getType(Uri uri) { payload(uri); return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Payload data = payload(uri);
        String[] columns = projection != null ? projection : new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = data.name;
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = data.bytes.length;
        }
        cursor.addRow(row);
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only fixture");
        final byte[] bytes = payload(uri).bytes;
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                } catch (IOException ignored) { /* A cancelled reader may close its end first. */ }
            }, "original-book-fixture");
            writer.setDaemon(true);
            writer.start();
            return pipe[0];
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
