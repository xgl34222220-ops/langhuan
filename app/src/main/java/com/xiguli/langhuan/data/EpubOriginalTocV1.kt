package com.xiguli.langhuan.data

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

@Serializable
data class EpubTocNodeV1(
    val title: String,
    val chapterNumber: Int? = null,
    val children: List<EpubTocNodeV1> = emptyList(),
)

@Serializable
data class EpubTocArchiveV1(
    val novelId: String,
    val source: String = "epub",
    val nodes: List<EpubTocNodeV1> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

object EpubOriginalTocV1 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun extract(bytes: ByteArray, chapterCount: Int): List<EpubTocNodeV1> {
        if (chapterCount <= 0) return emptyList()
        val archive = unzip(bytes)
        val opfPath = findOpfPath(archive) ?: return emptyList()
        val opf = findEntry(archive, opfPath)?.let(::parseXml) ?: return emptyList()
        val manifest = descendants(opf, "item").mapNotNull { item ->
            val id = item.getAttribute("id").trim()
            val href = item.getAttribute("href").trim()
            if (id.isBlank() || href.isBlank()) null else ManifestItemV1(
                id = id,
                href = href,
                mediaType = item.getAttribute("media-type").trim(),
                properties = item.getAttribute("properties").split(Regex("\\s+")).filter { it.isNotBlank() }.toSet(),
            )
        }

        val navItem = manifest.firstOrNull { "nav" in it.properties }
        val rawTree = if (navItem != null) {
            val path = resolvePath(opfPath, navItem.href)
            findEntry(archive, path)?.let { parseEpub3Tree(path, it) }.orEmpty()
        } else emptyList()

        val tree = if (rawTree.isNotEmpty()) rawTree else {
            val spine = descendants(opf, "spine").firstOrNull()
            val tocId = spine?.getAttribute("toc").orEmpty()
            val ncx = manifest.firstOrNull { it.id == tocId }
                ?: manifest.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", true) || it.href.endsWith(".ncx", true) }
            ncx?.let { item ->
                val path = resolvePath(opfPath, item.href)
                findEntry(archive, path)?.let { parseNcxTree(path, it) }
            }.orEmpty()
        }

        return assignLeafChapters(tree, chapterCount).ifEmpty { emptyList() }
    }

    fun save(context: Context, novelId: String, nodes: List<EpubTocNodeV1>) {
        if (nodes.isEmpty()) return
        val dir = File(context.filesDir, "local_toc_v1").apply { mkdirs() }
        File(dir, "$novelId.json").writeText(
            json.encodeToString(EpubTocArchiveV1.serializer(), EpubTocArchiveV1(novelId = novelId, nodes = nodes))
        )
    }

    fun load(context: Context, novelId: String): List<EpubTocNodeV1> = runCatching {
        val file = File(context.filesDir, "local_toc_v1/$novelId.json")
        if (!file.exists()) emptyList() else json.decodeFromString(EpubTocArchiveV1.serializer(), file.readText()).nodes
    }.getOrDefault(emptyList())

    private data class ManifestItemV1(val id: String, val href: String, val mediaType: String, val properties: Set<String>)
    private data class RawNodeV1(val title: String, val href: String = "", val children: List<RawNodeV1> = emptyList())

    private fun parseEpub3Tree(basePath: String, bytes: ByteArray): List<RawNodeV1> {
        val doc = parseXml(bytes) ?: return emptyList()
        val navs = descendants(doc, "nav")
        val toc = navs.firstOrNull { nav ->
            val type = nav.getAttribute("epub:type").ifBlank { nav.getAttributeNS("http://www.idpf.org/2007/ops", "type") }
            type.split(Regex("\\s+")).any { it.equals("toc", true) }
        } ?: navs.firstOrNull() ?: return emptyList()
        val ol = directChildren(toc, "ol").firstOrNull() ?: descendants(toc, "ol").firstOrNull() ?: return emptyList()
        return parseOl(basePath, ol)
    }

    private fun parseOl(basePath: String, ol: Element): List<RawNodeV1> = directChildren(ol, "li").mapNotNull { li ->
        val anchor = directChildren(li, "a").firstOrNull()
        val span = directChildren(li, "span").firstOrNull()
        val label = (anchor?.textContent ?: span?.textContent ?: "").cleanText()
        val href = anchor?.getAttribute("href").orEmpty().trim()
        val childOl = directChildren(li, "ol").firstOrNull()
        val children = childOl?.let { parseOl(basePath, it) }.orEmpty()
        if (label.isBlank() && children.isEmpty()) null else RawNodeV1(
            title = label.ifBlank { "目录" },
            href = if (href.isBlank()) "" else resolvePath(basePath, href.substringBefore('?')),
            children = children,
        )
    }

    private fun parseNcxTree(basePath: String, bytes: ByteArray): List<RawNodeV1> {
        val doc = parseXml(bytes) ?: return emptyList()
        val map = descendants(doc, "navMap").firstOrNull() ?: return emptyList()
        return directChildren(map, "navPoint").mapNotNull { parseNavPoint(basePath, it) }
    }

    private fun parseNavPoint(basePath: String, point: Element): RawNodeV1? {
        val labelNode = directChildren(point, "navLabel").firstOrNull()
        val label = labelNode?.let { descendants(it, "text").firstOrNull()?.textContent }.orEmpty().cleanText()
        val src = directChildren(point, "content").firstOrNull()?.getAttribute("src").orEmpty().trim()
        val children = directChildren(point, "navPoint").mapNotNull { parseNavPoint(basePath, it) }
        if (label.isBlank() && children.isEmpty()) return null
        return RawNodeV1(
            title = label.ifBlank { "目录" },
            href = if (src.isBlank()) "" else resolvePath(basePath, src.substringBefore('?')),
            children = children,
        )
    }

    internal fun assignLeafChapters(raw: List<RawNodeV1>, chapterCount: Int): List<EpubTocNodeV1> {
        var next = 1
        fun map(node: RawNodeV1): EpubTocNodeV1 {
            val mappedChildren = node.children.map(::map)
            val number = if (mappedChildren.isEmpty() && node.href.isNotBlank() && next <= chapterCount) next++ else null
            return EpubTocNodeV1(node.title, number, mappedChildren)
        }
        val result = raw.map(::map)
        if (next <= chapterCount) {
            val extras = (next..chapterCount).map { EpubTocNodeV1("第 $it 章", it) }
            return result + extras
        }
        return result
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) result[normalizePath(entry.name)] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return result
    }

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = findEntry(entries, "META-INF/container.xml")?.let(::parseXml)
        val full = container?.let { descendants(it, "rootfile").firstOrNull()?.getAttribute("full-path") }.orEmpty()
        if (full.isNotBlank()) return normalizePath(percentDecode(full))
        return entries.keys.firstOrNull { it.endsWith(".opf", true) }
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
            for (i in 0 until children.length) {
                val child = children.item(i)
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

    private fun directChildren(node: Node, localName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                val name = element.localName ?: element.nodeName.substringAfter(':')
                if (name.equals(localName, true)) result += element
            }
        }
        return result
    }

    private fun resolvePath(baseFile: String, rawHref: String): String {
        val href = percentDecode(rawHref.substringBefore('#')).replace('\\', '/')
        if (href.isBlank()) return ""
        val baseDir = baseFile.substringBeforeLast('/', "")
        return normalizePath(if (baseDir.isBlank()) href else "$baseDir/$href")
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

    private fun findEntry(entries: Map<String, ByteArray>, path: String): ByteArray? =
        entries[normalizePath(path)] ?: entries.entries.firstOrNull { it.key.equals(normalizePath(path), true) }?.value

    private fun percentDecode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun String.cleanText(): String = replace(Regex("\\s+"), " ").trim()
}
