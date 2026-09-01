package com.xiguli.langhuan.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubOriginalTocV1Test {
    @Test
    fun epub3NestedTocKeepsHierarchyAndLeafOrder() {
        val bytes = zipOf(
            "META-INF/container.xml" to """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine/></package>""",
            "OEBPS/nav.xhtml" to """<?xml version="1.0"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><span>第一卷</span><ol><li><a href="c1.xhtml">第一章 初见</a></li><li><a href="c2.xhtml">第二章 夜雨</a></li></ol></li><li><span>第二卷</span><ol><li><a href="c3.xhtml">第三章 归途</a></li></ol></li></ol></nav></body></html>""",
        )
        val toc = EpubOriginalTocV1.extract(bytes, 3)
        assertEquals(2, toc.size)
        assertEquals("第一卷", toc[0].title)
        assertNull(toc[0].chapterNumber)
        assertEquals(2, toc[0].children.size)
        assertEquals(1, toc[0].children[0].chapterNumber)
        assertEquals(2, toc[0].children[1].chapterNumber)
        assertEquals(3, toc[1].children[0].chapterNumber)
    }

    @Test
    fun missingNavigationFallsBackToEmptyTree() {
        val bytes = zipOf("META-INF/container.xml" to "<container/>")
        assertTrue(EpubOriginalTocV1.extract(bytes, 4).isEmpty())
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (path, text) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
