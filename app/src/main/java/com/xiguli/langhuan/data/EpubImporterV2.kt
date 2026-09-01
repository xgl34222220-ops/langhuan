package com.xiguli.langhuan.data

import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * EPUB 读取器 V2。
 *
 * 旧实现按 zip 文件名排序 XHTML，目录顺序和真正的 EPUB spine 无关，也完全没有读取封面。
 * 这里严格按 container.xml -> OPF -> manifest/spine/nav(ncx) 解析，并保留原书目录标题。
 */
data class EpubImportedCoverV2(
    val bytes: ByteArray,
    val mediaType: String,
    val fileName: String,
)

data class EpubImportResultV2(
    val manuscript: ImportedManuscript,
    val author: String = "",
    val cover: EpubImportedCoverV2? = null,
)

object EpubImporterV2 {
    fun import(fileName: String, bytes: ByteArray): EpubImportResultV2 {
        val archive = unzip(bytes)
        require(archive.isNotEmpty()) { "EPUB 压缩包是空的" }

        val opfPath = findOpfPath(archive)
            ?: error("EPUB 缺少 META-INF/container.xml 或 OPF 包描述")
        val opfBytes = findEntry(archive, opfPath)
            ?: error("EPUB 找不到包描述：$opfPath")
        val opf = parseXml(opfBytes)
            ?: error("EPUB 的 OPF 包描述无法解析")

        val title = firstText(opf, "title")
            .decodeHtmlEntities()
            .cleanInlineText()
            .ifBlank { fileName.substringBeforeLast('.').ifBlank { "导入 EPUB" } }
        val author = firstText(opf, "creator")
            .decodeHtmlEntities()
            .cleanInlineText()

        val manifest = parseManifest(opf)
        val spine = parseSpine(opf, manifest)
        val navigation = parseNavigation(opf, opfPath, manifest, archive)
        val cover = findCover(opf, opfPath, manifest, archive)

        val chapters = buildChapters(
            opfPath = opfPath,
            manifest = manifest,
            spine = spine,
            navigation = navigation,
            archive = archive,
        )

        require(chapters.any { it.content.isNotBlank() }) { "EPUB 没有识别到可阅读正文" }
        return EpubImportResultV2(
            manuscript = ImportedManuscript(title = title, chapters = chapters),
            author = author,
            cover = cover,
        )
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: Set<String>,
    )

    private data class SpineItem(
        val item: ManifestItem,
        val linear: Boolean,
    )

    private data class NavEntry(
        val path: String,
        val fragment: String,
        val label: String,
    )

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = normalizePath(entry.name)
                    if (name.isNotBlank()) entries[name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = findEntry(entries, "META-INF/container.xml")
        if (container != null) {
            val doc = parseXml(container)
            val rootFile = doc?.let { descendants(it, "rootfile").firstOrNull() }
            val fullPath = rootFile?.getAttribute("full-path").orEmpty().trim()
            if (fullPath.isNotBlank()) {
                val normalized = normalizePath(percentDecode(fullPath))
                if (findEntry(entries, normalized) != null) return normalized
            }
        }
        return entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    private fun parseManifest(opf: Document): Map<String, ManifestItem> = buildMap {
        descendants(opf, "item").forEach { element ->
            val id = element.getAttribute("id").trim()
            val href = element.getAttribute("href").trim()
            if (id.isBlank() || href.isBlank()) return@forEach
            put(
                id,
                ManifestItem(
                    id = id,
                    href = href,
                    mediaType = element.getAttribute("media-type").trim().lowercase(Locale.ROOT),
                    properties = element.getAttribute("properties")
                        .split(Regex("\\s+"))
                        .map { it.trim().lowercase(Locale.ROOT) }
                        .filter { it.isNotBlank() }
                        .toSet(),
                )
            )
        }
    }

    private fun parseSpine(opf: Document, manifest: Map<String, ManifestItem>): List<SpineItem> {
        val refs = descendants(opf, "spine").firstOrNull()
            ?.let { descendants(it, "itemref") }
            .orEmpty()
            .mapNotNull { ref ->
                val idRef = ref.getAttribute("idref").trim()
                manifest[idRef]?.let { item ->
                    SpineItem(item = item, linear = !ref.getAttribute("linear").equals("no", true))
                }
            }
        val linear = refs.filter { it.linear }
        return linear.ifEmpty { refs }
    }

    private fun parseNavigation(
        opf: Document,
        opfPath: String,
        manifest: Map<String, ManifestItem>,
        archive: Map<String, ByteArray>,
    ): List<NavEntry> {
        val navItem = manifest.values.firstOrNull { "nav" in it.properties }
        if (navItem != null) {
            val navPath = resolvePath(opfPath, navItem.href)
            val navBytes = findEntry(archive, navPath)
            if (navBytes != null) {
                val parsed = parseEpub3Nav(navPath, navBytes)
                if (parsed.isNotEmpty()) return parsed
            }
        }

        val spineElement = descendants(opf, "spine").firstOrNull()
        val tocId = spineElement?.getAttribute("toc").orEmpty().trim()
        val ncxItem = manifest[tocId]
            ?: manifest.values.firstOrNull {
                it.mediaType == "application/x-dtbncx+xml" || it.href.endsWith(".ncx", true)
            }
        if (ncxItem != null) {
            val ncxPath = resolvePath(opfPath, ncxItem.href)
            val ncxBytes = findEntry(archive, ncxPath)
            if (ncxBytes != null) {
                val parsed = parseNcx(ncxPath, ncxBytes)
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return emptyList()
    }

    private fun parseEpub3Nav(navPath: String, bytes: ByteArray): List<NavEntry> {
        val doc = parseXml(bytes)
        if (doc != null) {
            val navs = descendants(doc, "nav")
            val toc = navs.firstOrNull { element ->
                val type = element.getAttribute("epub:type").ifBlank {
                    element.getAttributeNS("http://www.idpf.org/2007/ops", "type")
                }
                type.split(Regex("\\s+")).any { it.equals("toc", true) }
            } ?: navs.firstOrNull()
            if (toc != null) {
                val result = descendants(toc, "a").mapNotNull { anchor ->
                    navEntry(navPath, anchor.getAttribute("href"), anchor.textContent)
                }
                if (result.isNotEmpty()) return result
            }
        }

        val html = bytes.toString(Charsets.UTF_8)
        return Regex(
            """(?is)<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>"""
        ).findAll(html).mapNotNull { match ->
            navEntry(navPath, match.groupValues[1], match.groupValues[2].stripTags().decodeHtmlEntities())
        }.toList()
    }

    private fun parseNcx(ncxPath: String, bytes: ByteArray): List<NavEntry> {
        val doc = parseXml(bytes)
        if (doc != null) {
            val result = descendants(doc, "navPoint").mapNotNull { point ->
                val src = descendants(point, "content").firstOrNull()?.getAttribute("src").orEmpty()
                val labelNode = descendants(point, "navLabel").firstOrNull()
                val label = labelNode?.let { descendants(it, "text").firstOrNull()?.textContent }
                    ?: labelNode?.textContent.orEmpty()
                navEntry(ncxPath, src, label)
            }
            if (result.isNotEmpty()) return result
        }

        val xml = bytes.toString(Charsets.UTF_8)
        return Regex("""(?is)<navPoint\\b.*?</navPoint>""").findAll(xml).mapNotNull { point ->
            val block = point.value
            val src = Regex("""(?is)<content\\b[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']""")
                .find(block)?.groupValues?.getOrNull(1).orEmpty()
            val label = Regex("""(?is)<navLabel\\b.*?<text\\b[^>]*>(.*?)</text>.*?</navLabel>""")
                .find(block)?.groupValues?.getOrNull(1).orEmpty().stripTags().decodeHtmlEntities()
            navEntry(ncxPath, src, label)
        }.toList()
    }

    private fun navEntry(basePath: String, rawHref: String, rawLabel: String): NavEntry? {
        val href = rawHref.trim()
        if (href.isBlank()) return null
        val fragment = percentDecode(href.substringAfter('#', "")).trim()
        val pathPart = href.substringBefore('#').substringBefore('?').trim()
        if (pathPart.isBlank()) return null
        val path = resolvePath(basePath, pathPart)
        val label = rawLabel.stripTags().decodeHtmlEntities().cleanInlineText()
        if (path.isBlank() || label.isBlank()) return null
        return NavEntry(path = path, fragment = fragment, label = label)
    }

    private fun findCover(
        opf: Document,
        opfPath: String,
        manifest: Map<String, ManifestItem>,
        archive: Map<String, ByteArray>,
    ): EpubImportedCoverV2? {
        val candidates = mutableListOf<Pair<String, String>>()

        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { item ->
            candidates += resolvePath(opfPath, item.href) to item.mediaType
        }

        val epub2CoverId = descendants(opf, "meta").firstOrNull { meta ->
            meta.getAttribute("name").equals("cover", true) && meta.getAttribute("content").isNotBlank()
        }?.getAttribute("content").orEmpty().trim()
        if (epub2CoverId.isNotBlank()) {
            manifest[epub2CoverId]?.let { item ->
                candidates += resolvePath(opfPath, item.href) to item.mediaType
            }
        }

        descendants(opf, "reference").firstOrNull { reference ->
            reference.getAttribute("type").split(Regex("\\s+")).any { it.equals("cover", true) }
        }?.getAttribute("href")?.takeIf { it.isNotBlank() }?.let { href ->
            candidates += resolvePath(opfPath, href) to ""
        }

        manifest.values.filter { item ->
            item.mediaType.startsWith("image/") &&
                (item.id.contains("cover", true) || item.href.contains("cover", true))
        }.forEach { item ->
            candidates += resolvePath(opfPath, item.href) to item.mediaType
        }

        candidates.distinctBy { it.first.lowercase(Locale.ROOT) }.forEach { (path, mediaType) ->
            coverFromPath(path, mediaType, archive)?.let { return it }
        }
        return null
    }

    private fun coverFromPath(
        path: String,
        mediaType: String,
        archive: Map<String, ByteArray>,
    ): EpubImportedCoverV2? {
        val bytes = findEntry(archive, path) ?: return null
        val type = mediaType.ifBlank { guessMediaType(path) }
        if (type.startsWith("image/") || isImagePath(path)) {
            return EpubImportedCoverV2(bytes = bytes, mediaType = type.ifBlank { guessMediaType(path) }, fileName = path.substringAfterLast('/'))
        }

        if (path.endsWith(".xhtml", true) || path.endsWith(".html", true) || type.contains("xhtml")) {
            val doc = parseXml(bytes)
            val rawImage = doc?.let { document ->
                descendants(document, "img").firstOrNull()?.getAttribute("src")
                    ?.takeIf { it.isNotBlank() }
                    ?: descendants(document, "image").firstOrNull()?.let { image ->
                        image.getAttribute("href").ifBlank { image.getAttribute("xlink:href") }
                    }?.takeIf { it.isNotBlank() }
            } ?: run {
                val html = bytes.toString(Charsets.UTF_8)
                Regex("""(?is)<(?:img|image)\\b[^>]*(?:src|(?:xlink:)?href)\\s*=\\s*[\"']([^\"']+)[\"']""")
                    .find(html)?.groupValues?.getOrNull(1)
            }
            if (!rawImage.isNullOrBlank()) {
                val imagePath = resolvePath(path, rawImage.substringBefore('#').substringBefore('?'))
                val imageBytes = findEntry(archive, imagePath)
                if (imageBytes != null) {
                    return EpubImportedCoverV2(
                        bytes = imageBytes,
                        mediaType = guessMediaType(imagePath),
                        fileName = imagePath.substringAfterLast('/'),
                    )
                }
            }
        }
        return null
    }

    private fun buildChapters(
        opfPath: String,
        manifest: Map<String, ManifestItem>,
        spine: List<SpineItem>,
        navigation: List<NavEntry>,
        archive: Map<String, ByteArray>,
    ): List<ImportedChapter> {
        val readingItems = spine.map { it.item }.ifEmpty {
            manifest.values.filter { item ->
                item.mediaType.contains("xhtml") || item.mediaType == "text/html" ||
                    item.href.endsWith(".xhtml", true) || item.href.endsWith(".html", true)
            }.sortedBy { naturalOrderKey(it.href) }
        }

        val chapters = mutableListOf<ImportedChapter>()
        readingItems.forEach { item ->
            val path = resolvePath(opfPath, item.href)
            val htmlBytes = findEntry(archive, path) ?: return@forEach
            val html = decodeMarkup(htmlBytes)
            if (html.isBlank()) return@forEach

            val navForPath = navigation.filter { it.path.equals(path, true) }
            val fragmentEntries = navForPath.filter { it.fragment.isNotBlank() }
            val shouldSplitByFragments = fragmentEntries.size >= 2 && (
                fragmentEntries.count { looksLikeChapterLabel(it.label) } >= 2 || readingItems.size <= 3
            )

            if (shouldSplitByFragments) {
                fragmentEntries.forEachIndexed { index, nav ->
                    val next = fragmentEntries.getOrNull(index + 1)?.fragment
                    val section = extractFragmentSection(html, nav.fragment, next) ?: return@forEachIndexed
                    val text = htmlToText(section)
                    if (text.isNotBlank()) {
                        chapters += ImportedChapter(
                            title = nav.label.ifBlank { "第${chapters.size + 1}章" },
                            content = stripLeadingDuplicateTitle(text, nav.label),
                        )
                    }
                }
            } else {
                val title = navForPath.firstOrNull()?.label
                    ?.takeIf { it.isNotBlank() }
                    ?: extractDocumentTitle(html)
                    ?: "第${chapters.size + 1}章"
                val text = htmlToText(html)
                if (text.isNotBlank()) {
                    chapters += ImportedChapter(
                        title = title,
                        content = stripLeadingDuplicateTitle(text, title),
                    )
                }
            }
        }

        if (chapters.isNotEmpty()) return chapters

        return archive.entries
            .filter { (path, _) ->
                (path.endsWith(".xhtml", true) || path.endsWith(".html", true)) &&
                    navigation.none { nav -> nav.path.equals(path, true) && nav.label.equals("目录", true) }
            }
            .sortedBy { naturalOrderKey(it.key) }
            .mapNotNull { (path, content) ->
                val html = decodeMarkup(content)
                val text = htmlToText(html)
                if (text.isBlank()) null else ImportedChapter(
                    title = navigation.firstOrNull { it.path.equals(path, true) }?.label
                        ?: extractDocumentTitle(html)
                        ?: "第1章",
                    content = text,
                )
            }
    }

    private fun extractFragmentSection(html: String, fragment: String, nextFragment: String?): String? {
        val startMatch = findAnchor(html, fragment) ?: return null
        val end = nextFragment?.let { findAnchor(html, it)?.range?.first } ?: html.length
        if (end <= startMatch.range.first) return null
        return html.substring(startMatch.range.first, end)
    }

    private fun findAnchor(html: String, fragment: String): MatchResult? {
        if (fragment.isBlank()) return null
        val escaped = Regex.escape(fragment)
        val regex = Regex(
            """(?is)<[^>]+(?:id|name)\\s*=\\s*[\"']$escaped[\"'][^>]*>"""
        )
        return regex.find(html)
    }

    private fun extractDocumentTitle(html: String): String? {
        val heading = Regex(
            """(?is)<h[1-3]\\b[^>]*>(.*?)</h[1-3]>"""
        ).find(html)?.groupValues?.getOrNull(1)
            ?.stripTags()?.decodeHtmlEntities()?.cleanInlineText().orEmpty()
        if (heading.isNotBlank()) return heading

        val title = Regex("""(?is)<title\\b[^>]*>(.*?)</title>""")
            .find(html)?.groupValues?.getOrNull(1)
            ?.stripTags()?.decodeHtmlEntities()?.cleanInlineText().orEmpty()
        return title.takeIf { it.isNotBlank() }
    }

    private fun htmlToText(html: String): String {
        var body = Regex("""(?is)<body\\b[^>]*>(.*?)</body>""")
            .find(html)?.groupValues?.getOrNull(1) ?: html
        body = body
            .replace(Regex("""(?is)<(?:script|style|head|nav|svg)\\b[^>]*>.*?</(?:script|style|head|nav|svg)>"""), "")
            .replace(Regex("""(?i)<br\\s*/?>"""), "\n")
            .replace(Regex("""(?i)</(?:p|div|section|article|blockquote|li|h[1-6]|tr)>"""), "\n\n")
            .replace(Regex("""(?i)<li\\b[^>]*>"""), "")
            .stripTags()
            .decodeHtmlEntities()
            .replace('\u00A0', ' ')

        return body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun stripLeadingDuplicateTitle(text: String, title: String): String {
        val cleanTitle = title.cleanInlineText()
        if (cleanTitle.isBlank()) return text
        val lines = text.lines().toMutableList()
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstIndex >= 0 && lines[firstIndex].cleanInlineText() == cleanTitle) {
            lines.removeAt(firstIndex)
            return lines.joinToString("\n").trimStart()
        }
        return text
    }

    private fun looksLikeChapterLabel(label: String): Boolean {
        val value = label.cleanInlineText()
        return Regex("""(?i)^(第.{1,12}[章回节卷]|chapter\\s*\\d+|ch\\.?\\s*\\d+)""").containsMatchIn(value)
    }

    private fun parseXml(bytes: ByteArray): Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }.getOrNull()

    private fun descendants(node: Node, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        fun walk(current: Node) {
            val children = current.childNodes
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val element = child as Element
                    val name = element.localName ?: element.nodeName.substringAfter(':')
                    if (name.equals(localName, true)) result += element
                    walk(element)
                }
            }
        }
        walk(node)
        return result
    }

    private fun firstText(document: Document, localName: String): String =
        descendants(document, localName).firstOrNull()?.textContent.orEmpty()

    private fun resolvePath(baseFile: String, href: String): String {
        val raw = percentDecode(href.trim()).replace('\\', '/')
        if (raw.isBlank()) return ""
        if (raw.startsWith('/')) return normalizePath(raw)
        val baseDir = baseFile.substringBeforeLast('/', "")
        return normalizePath(if (baseDir.isBlank()) raw else "$baseDir/$raw")
    }

    private fun normalizePath(path: String): String {
        val parts = mutableListOf<String>()
        path.replace('\\', '/').split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private fun findEntry(entries: Map<String, ByteArray>, path: String): ByteArray? {
        val normalized = normalizePath(path)
        return entries[normalized]
            ?: entries.entries.firstOrNull { it.key.equals(normalized, true) }?.value
    }

    private fun percentDecode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun decodeMarkup(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val utf8 = bytes.toString(Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return runCatching { bytes.toString(Charsets.UTF_16) }.getOrDefault(utf8)
    }

    private fun guessMediaType(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun isImagePath(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp", "gif", "svg")

    private fun String.stripTags(): String = replace(Regex("<[^>]+>"), "")

    private fun String.cleanInlineText(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun String.decodeHtmlEntities(): String {
        var value = this
            .replace("&nbsp;", " ", true)
            .replace("&lt;", "<", true)
            .replace("&gt;", ">", true)
            .replace("&quot;", "\"", true)
            .replace("&apos;", "'", true)
            .replace("&#39;", "'", true)
            .replace("&mdash;", "—", true)
            .replace("&ndash;", "–", true)
            .replace("&hellip;", "…", true)
            .replace("&ldquo;", "“", true)
            .replace("&rdquo;", "”", true)
            .replace("&lsquo;", "‘", true)
            .replace("&rsquo;", "’", true)
            .replace("&amp;", "&", true)

        value = Regex("""&#x([0-9a-fA-F]+);""").replace(value) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { code ->
                runCatching { String(Character.toChars(code)) }.getOrNull()
            } ?: match.value
        }
        value = Regex("""&#([0-9]+);""").replace(value) { match ->
            match.groupValues[1].toIntOrNull()?.let { code ->
                runCatching { String(Character.toChars(code)) }.getOrNull()
            } ?: match.value
        }
        return value
    }

    private fun naturalOrderKey(value: String): String =
        value.replace(Regex("\\d+")) { it.value.padStart(10, '0') }
}
