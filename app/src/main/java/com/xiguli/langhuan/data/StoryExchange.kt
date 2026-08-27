package com.xiguli.langhuan.data

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ExportFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    EPUB("epub", "application/epub+zip"),
}

data class ExportArtifact(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ImportedChapter(
    val title: String,
    val content: String,
)

data class ImportedManuscript(
    val title: String,
    val chapters: List<ImportedChapter>,
)

object StoryExchange {
    fun export(snapshot: StorySnapshot, drafts: List<ChapterDraft>, format: ExportFormat): ExportArtifact {
        val ordered = drafts.sortedBy { it.chapterNumber }
        val safeName = snapshot.novel.title.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "琅嬛小说" }
        val bytes = when (format) {
            ExportFormat.TXT -> exportText(snapshot, ordered).toByteArray(Charsets.UTF_8)
            ExportFormat.MARKDOWN -> exportMarkdown(snapshot, ordered).toByteArray(Charsets.UTF_8)
            ExportFormat.EPUB -> exportEpub(snapshot, ordered)
        }
        return ExportArtifact("$safeName.${format.extension}", format.mimeType, bytes)
    }

    fun `import`(fileName: String, bytes: ByteArray): ImportedManuscript {
        val lower = fileName.lowercase()
        return if (lower.endsWith(".epub")) importEpub(fileName, bytes)
        else importText(fileName, bytes.toString(Charsets.UTF_8), markdown = lower.endsWith(".md") || lower.endsWith(".markdown"))
    }

    private fun exportText(snapshot: StorySnapshot, drafts: List<ChapterDraft>): String = buildString {
        appendLine(snapshot.novel.title)
        appendLine()
        drafts.forEach { chapter ->
            appendLine("第${chapter.chapterNumber}章 ${chapter.title}")
            appendLine()
            appendLine(chapter.content.trim())
            appendLine()
        }
    }

    private fun exportMarkdown(snapshot: StorySnapshot, drafts: List<ChapterDraft>): String = buildString {
        appendLine("# ${snapshot.novel.title}")
        appendLine()
        appendLine("> ${snapshot.novel.premise}")
        appendLine()
        drafts.forEach { chapter ->
            appendLine("## 第${chapter.chapterNumber}章 ${chapter.title}")
            appendLine()
            appendLine(chapter.content.trim())
            appendLine()
        }
    }

    private fun exportEpub(snapshot: StorySnapshot, drafts: List<ChapterDraft>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val mime = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mime) }
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                this.crc = crc.value
            })
            zip.write(mime)
            zip.closeEntry()

            zip.writeEntry("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""")

            val manifest = drafts.joinToString("\n") { "    <item id=\"c${it.chapterNumber}\" href=\"c${it.chapterNumber}.xhtml\" media-type=\"application/xhtml+xml\"/>" }
            val spine = drafts.joinToString("\n") { "    <itemref idref=\"c${it.chapterNumber}\"/>" }
            zip.writeEntry("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">${xml(snapshot.novel.id)}</dc:identifier>
    <dc:title>${xml(snapshot.novel.title)}</dc:title>
    <dc:language>zh-CN</dc:language>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
$manifest
  </manifest>
  <spine>
$spine
  </spine>
</package>""")

            val navItems = drafts.joinToString("\n") { "      <li><a href=\"c${it.chapterNumber}.xhtml\">第${it.chapterNumber}章 ${xml(it.title)}</a></li>" }
            zip.writeEntry("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>目录</title></head><body>
<nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops"><h1>目录</h1><ol>
$navItems
</ol></nav></body></html>""")

            drafts.forEach { chapter ->
                val paragraphs = chapter.content.split(Regex("\\n\\s*\\n"))
                    .filter { it.isNotBlank() }
                    .joinToString("\n") { "<p>${xml(it.trim()).replace("\n", "<br/>")}</p>" }
                zip.writeEntry("OEBPS/c${chapter.chapterNumber}.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${xml(chapter.title)}</title></head><body>
<h1>第${chapter.chapterNumber}章 ${xml(chapter.title)}</h1>
$paragraphs
</body></html>""")
            }
        }
        return output.toByteArray()
    }

    private fun importText(fileName: String, text: String, markdown: Boolean): ImportedManuscript {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val heading = if (markdown) {
            Regex("(?m)^#{1,3}\\s*(?:第\\s*([0-9一二三四五六七八九十百千万零〇两]+)\\s*章)?\\s*(.+?)\\s*$")
        } else {
            Regex("(?m)^\\s*第\\s*([0-9一二三四五六七八九十百千万零〇两]+)\\s*章[：:\\s]*(.*?)\\s*$")
        }
        val matches = heading.findAll(normalized).toList()
        val chapters = if (matches.isEmpty()) {
            listOf(ImportedChapter("第一章", normalized.trim()))
        } else {
            matches.mapIndexed { index, match ->
                val start = match.range.last + 1
                val end = matches.getOrNull(index + 1)?.range?.first ?: normalized.length
                val rawTitle = if (markdown) match.groupValues.lastOrNull().orEmpty() else match.groupValues.getOrNull(2).orEmpty()
                ImportedChapter(
                    title = rawTitle.trim().ifBlank { "第${index + 1}章" },
                    content = normalized.substring(start, end).trim(),
                )
            }
        }
        val firstLine = normalized.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            .removePrefix("#").trim()
        val title = firstLine.takeIf { it.isNotBlank() && !it.startsWith("第") }
            ?: fileName.substringBeforeLast('.').ifBlank { "导入小说" }
        return ImportedManuscript(title, chapters.filter { it.content.isNotBlank() })
    }

    private fun importEpub(fileName: String, bytes: ByteArray): ImportedManuscript {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (entry.name.endsWith(".xhtml", true) || entry.name.endsWith(".html", true) || entry.name.endsWith(".opf", true))) {
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        val opf = entries.entries.firstOrNull { it.key.endsWith(".opf", true) }?.value.orEmpty()
        val title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.IGNORE_CASE).find(opf)?.groupValues?.getOrNull(1)
            ?.let(::htmlDecode)?.trim().orEmpty()
            .ifBlank { fileName.substringBeforeLast('.').ifBlank { "导入 EPUB" } }
        val chapters = entries.entries
            .filter { (name, _) -> !name.endsWith("nav.xhtml", true) && !name.endsWith("toc.xhtml", true) && !name.endsWith(".opf", true) }
            .sortedBy { naturalOrderKey(it.key) }
            .mapIndexedNotNull { index, (_, html) ->
                val chapterTitle = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.stripTags()?.let(::htmlDecode)?.trim()
                    ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(html)?.groupValues?.getOrNull(1)?.stripTags()?.let(::htmlDecode)?.trim()
                    ?: "第${index + 1}章"
                val body = Regex("<body[^>]*>(.*?)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1) ?: html
                val text = body
                    .replace(Regex("(?i)<br\\s*/?>"), "\n")
                    .replace(Regex("(?i)</p>|</div>|</h[1-6]>"), "\n\n")
                    .stripTags()
                    .let(::htmlDecode)
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
                if (text.isBlank()) null else ImportedChapter(chapterTitle.replace(Regex("^第.+?章\\s*"), "").ifBlank { chapterTitle }, text)
            }
        return ImportedManuscript(title, chapters.ifEmpty { listOf(ImportedChapter("第一章", "")) })
    }

    private fun ZipOutputStream.writeEntry(path: String, value: String) {
        putNextEntry(ZipEntry(path))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun String.stripTags(): String = replace(Regex("<[^>]+>"), "")

    private fun htmlDecode(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun naturalOrderKey(value: String): String = value.replace(Regex("\\d+")) { it.value.padStart(8, '0') }
}
