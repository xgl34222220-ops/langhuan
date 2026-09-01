package com.xiguli.langhuan.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubImporterV2Test {
    @Test
    fun `epub3 uses spine order nav titles author and cover image`() {
        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0xFF.toByte(), 0xD9.toByte())
        val epub = epubOf(
            "META-INF/container.xml" to """<?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
            """.trimIndent().toByteArray(),
            "OPS/package.opf" to """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>测试 EPUB</dc:title>
                    <dc:creator>测试作者</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    <item id="c2" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c10" href="text/chapter10.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c10"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent().toByteArray(),
            "OPS/nav.xhtml" to """<?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <body><nav epub:type="toc"><ol>
                    <li><a href="text/chapter10.xhtml">第一章 雨夜</a></li>
                    <li><a href="text/chapter2.xhtml">第二章 门后</a></li>
                  </ol></nav></body>
                </html>
            """.trimIndent().toByteArray(),
            "OPS/text/chapter10.xhtml" to """<html><head><title>错的文件标题</title></head><body><h1>第一章 雨夜</h1><p>雨落在窗外。</p></body></html>""".toByteArray(),
            "OPS/text/chapter2.xhtml" to """<html><body><h1>第二章 门后</h1><p>门后有人。</p></body></html>""".toByteArray(),
            "OPS/images/cover.jpg" to cover,
        )

        val result = EpubImporterV2.import("乱序文件名.epub", epub)

        assertEquals("测试 EPUB", result.manuscript.title)
        assertEquals("测试作者", result.author)
        assertEquals(listOf("第一章 雨夜", "第二章 门后"), result.manuscript.chapters.map { it.title })
        assertTrue(result.manuscript.chapters[0].content.contains("雨落在窗外"))
        assertTrue(result.manuscript.chapters[1].content.contains("门后有人"))
        assertArrayEquals(cover, result.cover?.bytes)
        assertEquals("image/jpeg", result.cover?.mediaType)
    }

    @Test
    fun `epub2 ncx controls directory order instead of zip filename`() {
        val epub = epubOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""".toByteArray(),
            "OEBPS/content.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>EPUB2</dc:title></metadata>
                  <manifest>
                    <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="a" href="Text/z.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="Text/a.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="toc"><itemref idref="a"/><itemref idref="b"/></spine>
                </package>
            """.trimIndent().toByteArray(),
            "OEBPS/toc.ncx" to """
                <ncx><navMap>
                  <navPoint id="n1"><navLabel><text>第十章 真正的第一项</text></navLabel><content src="Text/z.xhtml"/></navPoint>
                  <navPoint id="n2"><navLabel><text>第十一章 真正的第二项</text></navLabel><content src="Text/a.xhtml"/></navPoint>
                </navMap></ncx>
            """.trimIndent().toByteArray(),
            "OEBPS/Text/a.xhtml" to "<html><body><p>第二项正文</p></body></html>".toByteArray(),
            "OEBPS/Text/z.xhtml" to "<html><body><p>第一项正文</p></body></html>".toByteArray(),
        )

        val result = EpubImporterV2.import("book.epub", epub)
        assertEquals(listOf("第十章 真正的第一项", "第十一章 真正的第二项"), result.manuscript.chapters.map { it.title })
        assertTrue(result.manuscript.chapters[0].content.contains("第一项正文"))
        assertTrue(result.manuscript.chapters[1].content.contains("第二项正文"))
    }

    @Test
    fun `single xhtml can be split by chapter anchors from nav`() {
        val epub = epubOf(
            "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>""".toByteArray(),
            "book.opf" to """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>合章书</dc:title></metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="all" href="all.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="all"/></spine>
                </package>
            """.trimIndent().toByteArray(),
            "nav.xhtml" to """
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body>
                  <nav epub:type="toc"><ol>
                    <li><a href="all.xhtml#c1">第一章 开始</a></li>
                    <li><a href="all.xhtml#c2">第二章 继续</a></li>
                  </ol></nav>
                </body></html>
            """.trimIndent().toByteArray(),
            "all.xhtml" to """
                <html><body>
                  <h1 id="c1">第一章 开始</h1><p>第一章正文。</p>
                  <h1 id="c2">第二章 继续</h1><p>第二章正文。</p>
                </body></html>
            """.trimIndent().toByteArray(),
        )

        val result = EpubImporterV2.import("all.epub", epub)
        assertEquals(2, result.manuscript.chapters.size)
        assertEquals("第一章 开始", result.manuscript.chapters[0].title)
        assertEquals("第二章 继续", result.manuscript.chapters[1].title)
        assertTrue(result.manuscript.chapters[0].content.contains("第一章正文"))
        assertTrue(result.manuscript.chapters[1].content.contains("第二章正文"))
    }

    private fun epubOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
