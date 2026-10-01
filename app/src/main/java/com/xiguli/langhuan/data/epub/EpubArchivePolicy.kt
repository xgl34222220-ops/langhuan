package com.xiguli.langhuan.data.epub

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Validate every entry before Readium sees an archive. Never extract user paths to disk. */
object EpubArchivePolicy {
    const val MAX_ARCHIVE = 96L * 1024 * 1024
    const val MAX_ENTRY = 16L * 1024 * 1024
    const val MAX_EXPANDED = 96L * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    const val MAX_IMAGE_SIDE = 8192
    const val MAX_IMAGE_PIXELS = 32_000_000L
    const val MAX_MARKUP_DEPTH = 128
    const val MAX_MARKUP_NODES = 100_000

    data class Archive(
        val entries: Set<String>,
        val mediaTypes: Map<String, String>,
        val spine: List<String>,
        val title: String,
    )

    fun validate(file: File, checkCancelled: () -> Unit = {}): Archive {
        require(file.length() in 1..MAX_ARCHIVE) { "EPUB 文件过大或为空，单本最大 96 MB" }
        ZipFile(file).use { zip ->
            val names = linkedSetOf<String>()
            var expanded = 0L
            var count = 0
            val metadata = mutableMapOf<String, ByteArray>()
            val detectedRasters = mutableSetOf<String>()
            val detectedSvg = mutableSetOf<String>()
            val zipEntries = zip.entries()
            while (zipEntries.hasMoreElements()) {
                checkCancelled()
                val entry = zipEntries.nextElement()
                require(++count <= MAX_ENTRIES) { "EPUB 文件条目过多" }
                val name = entry.name.removeSuffix("/")
                require(safeEntryName(name)) { "EPUB 包含不安全的文件路径" }
                require(names.add(name)) { "EPUB 包含重复文件路径" }
                if (entry.isDirectory) continue
                require(entry.size <= MAX_ENTRY) { "EPUB 单个资源超过 16 MB" }
                val bytes = zip.getInputStream(entry).use { readBounded(it, MAX_ENTRY, checkCancelled) }
                require(entry.size == bytes.size.toLong() && java.util.zip.CRC32().apply { update(bytes) }.value == entry.crc) {
                    "EPUB 资源校验失败，文件可能损坏"
                }
                expanded += bytes.size
                require(expanded <= MAX_EXPANDED) { "EPUB 解压总量超过 96 MB" }
                // File bytes, not an OPF label, decide whether an image decoder can see a raster.
                EpubImageBounds.read(bytes)?.let { (width, height) ->
                    validateRasterSize(width, height)
                    detectedRasters += name
                }
                if (looksLikeSvg(bytes)) {
                    val svg = safeXml(bytes).documentElement
                    validateSvgSize(svg.getAttribute("width"), svg.getAttribute("height"), svg.getAttribute("viewBox"))
                    detectedSvg += name
                }
                // Check all XML, including undeclared markup, before the SDK's XML parser runs.
                if (name.endsWith(".xml", true) || name.endsWith(".opf", true) ||
                    name.endsWith(".ncx", true) || name.endsWith(".svg", true)) {
                    val xml = safeXml(bytes)
                    if (name.endsWith(".svg", true)) xml.documentElement.let {
                        validateSvgSize(it.getAttribute("width"), it.getAttribute("height"), it.getAttribute("viewBox"))
                    }
                }
                if (name.endsWith(".xhtml", true) || name.endsWith(".html", true)) {
                    rejectEntityDeclarations(bytes)
                    validateMarkupStructure(bytes, html = true)
                }
                if (name == "mimetype" || name == "META-INF/container.xml" || name.endsWith(".opf", true) ||
                    name == "META-INF/encryption.xml") metadata[name] = bytes
            }
            require(metadata["mimetype"]?.toString(Charsets.US_ASCII)?.trim() == "application/epub+zip") { "这不是有效的 EPUB 文件" }
            val container = safeXml(metadata["META-INF/container.xml"] ?: error("EPUB 缺少 container.xml"))
            val opfPath = elements(container, "rootfile").firstOrNull()?.getAttribute("full-path")
                ?: error("EPUB 缺少书籍描述")
            require(safeEntryName(opfPath) && opfPath in names) { "EPUB 书籍描述路径无效" }
            val opf = safeXml(metadata[opfPath] ?: zip.getInputStream(zip.getEntry(opfPath)).use { readBounded(it, MAX_ENTRY, checkCancelled) })
            val items = elements(opf, "item")
            val itemIds = items.map { it.getAttribute("id") }
            require(itemIds.all { it.isNotBlank() } && itemIds.distinct().size == itemIds.size) { "EPUB 资源 ID 无效或重复" }
            val byId = items.associateBy { it.getAttribute("id") }
            val types = linkedMapOf<String, String>()
            items.forEach { item ->
                val href = localPath(opfPath, item.getAttribute("href"))
                // Remote manifest resources are unavailable; a remote spine is rejected below.
                if (href != null) {
                    require(href in names) { "EPUB 缺少声明的资源" }
                    types[href] = item.getAttribute("media-type").lowercase()
                    require(href !in detectedRasters || types[href]?.startsWith("image/") == true) { "EPUB 图片声明与实际文件不一致" }
                    require(href !in detectedSvg || types[href] == "image/svg+xml") { "EPUB SVG 声明与实际文件不一致" }
                }
            }
            val spine = elements(opf, "itemref").filter { it.getAttribute("linear") != "no" }.map { ref ->
                val item = byId[ref.getAttribute("idref")] ?: error("EPUB 阅读顺序引用了缺失的章节")
                val path = localPath(opfPath, item.getAttribute("href")) ?: error("不支持远程 EPUB 章节")
                require(types[path] in setOf("application/xhtml+xml", "text/html")) { "暂支持 HTML/XHTML 章节中的 EPUB 插画，独立 SVG 章节尚不支持" }
                path
            }
            require(spine.isNotEmpty()) { "EPUB 没有可阅读的章节" }
            metadata["META-INF/encryption.xml"]?.let { encryption ->
                elements(safeXml(encryption), "EncryptionMethod").forEach { method ->
                    require(method.getAttribute("Algorithm") in setOf(
                        "http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC")) {
                        "此 EPUB 受 DRM 保护，暂不支持打开"
                    }
                }
            }
            types.forEach { (name, type) ->
                checkCancelled()
                if (type.startsWith("image/") && type != "image/svg+xml") {
                    val bytes = zip.getInputStream(zip.getEntry(name)).use { readBounded(it, MAX_ENTRY, checkCancelled) }
                    val (width, height) = EpubImageBounds.read(bytes) ?: error("EPUB 图片格式或尺寸无法验证")
                    validateRasterSize(width, height)
                }
                if (type.contains("xml") || type == "text/html") {
                    val bytes = zip.getInputStream(zip.getEntry(name)).use { readBounded(it, MAX_ENTRY, checkCancelled) }
                    rejectEntityDeclarations(bytes)
                    validateMarkupStructure(bytes)
                }
            }
            return Archive(names, types, spine, elements(opf, "title").firstOrNull()?.textContent.orEmpty())
        }
    }

    fun safeEntryName(name: String): Boolean = name.isNotEmpty() && name.length <= 2048 &&
        !name.startsWith('/') && !name.contains('\\') && !name.contains(':') &&
        !name.contains('%') && !name.contains('?') && !name.contains('#') &&
        name.none { it.code < 32 || it.code == 127 } &&
        name.split('/').none { it.isEmpty() || it == "." || it == ".." }

    /** Decode once, normalize relative traversal inside the archive, reject escape and schemes. */
    fun localPath(base: String, href: String): String? = runCatching {
        val decoded = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
        if (decoded.contains('\\') || decoded.any { it.code < 32 } || decoded.startsWith('/') ||
            decoded.substringBefore('/').contains(':')) return null
        val path = decoded.substringBefore('#').substringBefore('?')
        if (path.isEmpty()) return base.takeIf(::safeEntryName)
        val stack = base.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
        for (part in path.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
            else -> stack.add(part)
        }
        stack.joinToString("/").takeIf(::safeEntryName)
    }.getOrNull()

    fun readBounded(input: InputStream, maximum: Long, checkCancelled: () -> Unit = {}): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var count = 0L
        while (true) {
            checkCancelled()
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            require(count <= maximum) { "EPUB 资源超过大小限制" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun validateSvgSize(width: String, height: String, viewBox: String) {
        fun length(value: String, fallback: Double): Double {
            if (value.isBlank() || value.trim().endsWith('%') || value.trim() == "auto") return fallback
            val match = Regex("(?i)^\\s*([0-9]+(?:\\.[0-9]+)?(?:e[+-]?[0-9]+)?)(px|pt|pc|cm|mm|in|em|ex)?\\s*$").matchEntire(value)
                ?: error("EPUB SVG 图片尺寸无法验证")
            val number = match.groupValues[1].toDouble()
            val multiplier = when (match.groupValues[2].lowercase()) {
                "pt" -> 96.0 / 72; "pc" -> 16.0; "cm" -> 96.0 / 2.54; "mm" -> 96.0 / 25.4; "in" -> 96.0
                "em", "ex" -> 24.0
                else -> 1.0
            }
            return number * multiplier
        }
        val box = viewBox.trim().split(Regex("[ ,]+")).mapNotNull { it.toDoubleOrNull() }
        if (viewBox.isNotBlank()) require(box.size == 4 && box.all { it.isFinite() } && box[2] > 0 && box[3] > 0) { "EPUB SVG viewBox 无效" }
        val w = length(width, box.getOrNull(2) ?: 300.0)
        val h = length(height, box.getOrNull(3) ?: 150.0)
        require(w.isFinite() && h.isFinite() && w in 0.0..MAX_IMAGE_SIDE.toDouble() && h in 0.0..MAX_IMAGE_SIDE.toDouble() && w * h <= MAX_IMAGE_PIXELS) {
            "EPUB SVG 图片尺寸过大"
        }
    }

    private fun rejectEntityDeclarations(bytes: ByteArray) {
        // Check both common Unicode encodings; NUL stripping also catches UTF-16/32 declarations.
        val text = bytes.toString(Charsets.ISO_8859_1).replace("\u0000", "")
        require(!Regex("<!\\s*ENTITY", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
            !Regex("<!\\s*DOCTYPE[^>]*\\[", RegexOption.IGNORE_CASE).containsMatchIn(text)) { "EPUB 不允许 XML 实体声明" }
    }

    fun safeXml(bytes: ByteArray): Document {
        rejectEntityDeclarations(bytes)
        validateMarkupStructure(bytes)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Android's built-in parser does not implement all Java/Xerces switches. The
            // mandatory boundary below removes external DOCTYPEs before parsing and rejects
            // entity declarations; the empty resolver also applies on every supported parser.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val normalized = removeExternalDoctype(decodeMarkup(bytes).removePrefix("\uFEFF"))
            .replace(Regex("encoding\\s*=\\s*['\"][^'\"]+['\"]", RegexOption.IGNORE_CASE), "encoding=\"UTF-8\"")
            .replace(Regex("&([A-Za-z][A-Za-z0-9]+);")) { match ->
                if (match.groupValues[1] in setOf("amp", "lt", "gt", "quot", "apos")) match.value
                else org.jsoup.nodes.Entities.getByName(match.groupValues[1]).takeIf { it.isNotEmpty() }
                    ?.codePoints()?.toArray()?.joinToString("") { "&#$it;" } ?: match.value
            }
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(java.io.StringReader("")) }
        }.parse(ByteArrayInputStream(normalized.toByteArray(Charsets.UTF_8)))
    }

    /** Preserve ordinary EPUB2 declarations semantically, without passing a DTD to any parser. */
    private fun removeExternalDoctype(text: String): String {
        val result = StringBuilder(text.length)
        var position = 0
        while (position < text.length) {
            val start = text.indexOf('<', position)
            if (start < 0) { result.append(text, position, text.length); break }
            result.append(text, position, start)
            val terminator = when {
                text.startsWith("<!--", start) -> "-->"
                text.startsWith("<![CDATA[", start) -> "]]>"
                else -> null
            }
            if (terminator != null) {
                val end = text.indexOf(terminator, start + 3)
                require(end >= 0) { "EPUB XML 标记未结束" }
                position = end + terminator.length
                result.append(text, start, position)
            } else if (text.regionMatches(start, "<!DOCTYPE", 0, 9, ignoreCase = true)) {
                var cursor = start + 9
                var quote: Char? = null
                while (cursor < text.length) {
                    val char = text[cursor]
                    if (quote != null) { if (char == quote) quote = null }
                    else if (char == '\'' || char == '"') quote = char
                    else if (char == '>') break
                    cursor++
                }
                require(cursor < text.length) { "EPUB DOCTYPE 未结束" }
                position = cursor + 1
            } else { result.append('<'); position = start + 1 }
        }
        return result.toString()
    }

    private fun decodeMarkup(bytes: ByteArray): String {
        val charset = when {
            bytes.size >= 4 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte() -> java.nio.charset.Charset.forName("UTF-32LE")
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0xfe.toByte() && bytes[3] == 0xff.toByte() -> java.nio.charset.Charset.forName("UTF-32BE")
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 60.toByte() && bytes[2] == 0.toByte() -> Charsets.UTF_16BE
            bytes.size >= 4 && bytes[0] == 60.toByte() && bytes[1] == 0.toByte() && bytes[3] == 0.toByte() -> Charsets.UTF_16LE
            else -> Regex("encoding\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
                .find(bytes.take(200).toByteArray().toString(Charsets.US_ASCII))?.groupValues?.get(1)
                ?.let { java.nio.charset.Charset.forName(it) } ?: Charsets.UTF_8
        }
        return bytes.toString(charset)
    }

    private fun validateRasterSize(width: Int, height: Int) {
        require(width in 1..MAX_IMAGE_SIDE && height in 1..MAX_IMAGE_SIDE && width.toLong() * height <= MAX_IMAGE_PIXELS) {
            "EPUB 图片尺寸过大（最大边长 8192，最大 3200 万像素）"
        }
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        // Most binary resources can be dismissed without decoding their whole contents.
        val first = bytes.firstOrNull { value -> value != 9.toByte() && value != 10.toByte() && value != 13.toByte() && value != 32.toByte() }?.toInt()?.and(255) ?: return false
        if (first !in setOf(60, 0, 0xef, 0xfe, 0xff)) return false
        val text = decodeMarkup(bytes).removePrefix("\uFEFF")
        var position = 0
        while (position < text.length) {
            while (position < text.length && text[position].isWhitespace()) position++
            when {
                text.startsWith("<?", position) -> {
                    val end = text.indexOf("?>", position + 2); if (end < 0) return false
                    position = end + 2
                }
                text.startsWith("<!--", position) -> {
                    val end = text.indexOf("-->", position + 4); if (end < 0) return false
                    position = end + 3
                }
                text.regionMatches(position, "<!DOCTYPE", 0, 9, ignoreCase = true) -> {
                    rejectEntityDeclarations(bytes)
                    var quote: Char? = null
                    position += 9
                    while (position < text.length) {
                        val char = text[position++]
                        if (quote != null) { if (char == quote) quote = null }
                        else if (char == '\'' || char == '"') quote = char
                        else if (char == '>') break
                    }
                }
                else -> {
                    if (text.getOrNull(position) != '<') return false
                    val name = text.substring(position + 1).takeWhile { it.isLetterOrDigit() || it in ":_-" }
                    return name.substringAfter(':').equals("svg", ignoreCase = true)
                }
            }
        }
        return false
    }

    /** A non-recursive preflight bounds tree construction and subsequent DOM serialization. */
    fun validateMarkupStructure(bytes: ByteArray, html: Boolean = false) {
        val text = decodeMarkup(bytes)
        val open = ArrayList<String>()
        val void = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
        val optionalClose = setOf("li", "dt", "dd", "p", "option", "optgroup", "tr", "td", "th")
        var position = 0
        var nodes = 0
        while (position < text.length) {
            val start = text.indexOf('<', position)
            if (start < 0) break
            if (text.startsWith("<!--", start)) {
                val end = text.indexOf("-->", start + 4); require(end >= 0) { "EPUB 注释未结束" }
                position = end + 3; continue
            }
            if (text.startsWith("<![CDATA[", start)) {
                val end = text.indexOf("]]>", start + 9); require(end >= 0) { "EPUB CDATA 未结束" }
                position = end + 3; continue
            }
            var cursor = start + 1
            val closing = text.getOrNull(cursor) == '/'
            if (closing) cursor++
            val nameStart = cursor
            while (cursor < text.length && (text[cursor].isLetterOrDigit() || text[cursor] in ":_-")) cursor++
            val name = text.substring(nameStart, cursor).lowercase()
            if (name.isEmpty() && text.getOrNull(cursor) !in listOf('!', '?')) { position = cursor + 1; continue }
            var quote: Char? = null
            while (cursor < text.length) {
                val char = text[cursor]
                if (quote != null) { if (char == quote) quote = null }
                else if (char == '\'' || char == '"') quote = char
                else if (char == '>') break
                cursor++
            }
            require(cursor < text.length) { "EPUB 标记未结束" }
            position = cursor + 1
            if (name.isEmpty()) continue
            if (closing) {
                val match = open.indexOfLast { it == name }
                if (match >= 0) while (open.size > match) open.removeAt(open.lastIndex)
            } else {
                require(++nodes <= MAX_MARKUP_NODES) { "EPUB 单页标记过多" }
                if (html && name in optionalClose && open.lastOrNull() == name) open.removeAt(open.lastIndex)
                val selfClosing = text.substring(start, cursor).trimEnd().endsWith('/')
                if (!selfClosing && (!html || name !in void)) {
                    open.add(name)
                    require(open.size <= MAX_MARKUP_DEPTH) { "EPUB 标记嵌套过深" }
                    if (html && name in setOf("script", "style", "textarea", "title")) {
                        val end = text.indexOf("</$name", position, ignoreCase = true)
                        if (end >= 0) position = end
                    }
                }
            }
        }
    }

    private fun elements(doc: Document, name: String): List<Element> {
        val list = doc.getElementsByTagNameNS("*", name)
        return (0 until list.length).map { list.item(it) as Element }
    }
}

/** Header-only dimension parsing avoids allocating attacker-sized bitmaps during import. */
internal object EpubImageBounds {
    fun read(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        fun u(i: Int) = bytes[i].toInt() and 255
        fun be16(i: Int) = (u(i) shl 8) or u(i + 1)
        fun le16(i: Int) = u(i) or (u(i + 1) shl 8)
        fun be32(i: Int) = (u(i) shl 24) or (u(i + 1) shl 16) or (u(i + 2) shl 8) or u(i + 3)
        fun le24(i: Int) = u(i) or (u(i + 1) shl 8) or (u(i + 2) shl 16)
        when {
            bytes.size >= 24 && bytes.sliceArray(0..7).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> be32(16) to be32(20)
            bytes.size >= 10 && bytes.copyOfRange(0, 3).toString(Charsets.US_ASCII) == "GIF" -> le16(6) to le16(8)
            bytes.size >= 30 && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> when (bytes.copyOfRange(12, 16).toString(Charsets.US_ASCII)) {
                "VP8X" -> (le24(24) + 1) to (le24(27) + 1)
                "VP8 " -> (le16(26) and 0x3fff) to (le16(28) and 0x3fff)
                "VP8L" -> (1 + (u(21) or ((u(22) and 0x3f) shl 8))) to (1 + ((u(22) shr 6) or (u(23) shl 2) or ((u(24) and 15) shl 10)))
                else -> null
            }
            bytes.size > 4 && u(0) == 255 && u(1) == 216 -> {
                var i = 2
                var result: Pair<Int, Int>? = null
                while (i + 8 < bytes.size) {
                    if (u(i) != 255) break
                    while (i < bytes.size && u(i) == 255) i++
                    val marker = u(i++)
                    if (marker in setOf(0xd8, 0x01) || marker in 0xd0..0xd7) continue
                    if (marker in setOf(0xda, 0xd9)) break
                    val length = be16(i)
                    if (length < 2 || i + length > bytes.size) break
                    if (marker in setOf(0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf)) {
                        result = be16(i + 5) to be16(i + 3); break
                    }
                    i += length
                }
                result
            }
            else -> null
        }
    }.getOrNull()
}
