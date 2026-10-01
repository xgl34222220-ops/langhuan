package com.xiguli.langhuan.data.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.DocumentType
import org.jsoup.parser.Parser

/** Publisher styling is retained, but EPUBs are documents, never trusted app code. */
object EpubContentSanitizer {
    const val POLICY_VERSION = 1
    const val CSP = "default-src 'none'; " +
        "script-src https://readium_assets/readium/scripts/readium-reflowable.js https://readium_assets/readium/scripts/readium-fixed.js; " +
        "script-src-attr 'none'; style-src 'unsafe-inline' https://readium_package https://readium_assets; " +
        "img-src https://readium_package; font-src https://readium_package https://readium_assets; " +
        "connect-src 'none'; frame-src 'none'; child-src 'none'; object-src 'none'; " +
        "media-src 'none'; form-action 'none'; base-uri 'none'; worker-src 'none'"

    fun markup(bytes: ByteArray, svg: Boolean = false): ByteArray {
        EpubArchivePolicy.validateMarkupStructure(bytes, html = !svg)
        val input = Jsoup.parse(java.io.ByteArrayInputStream(bytes), null, "", if (svg) Parser.xmlParser() else Parser.htmlParser())
        input.outputSettings().prettyPrint(false).charset(Charsets.UTF_8).syntax(Document.OutputSettings.Syntax.xml)
        // These elements can execute code, navigate automatically, contain secondary documents,
        // or mutate hrefs after sanitization. SMIL is intentionally unsupported.
        val forbidden = setOf("script", "iframe", "frame", "frameset", "object", "embed", "base", "applet",
            "form", "input", "button", "textarea", "select", "audio", "video", "source", "track",
            "foreignobject", "animate", "animatetransform", "animatemotion", "set", "discard")
        input.getAllElements().toList().forEach { element ->
            val tag = element.normalName().substringAfter(':').lowercase()
            if (tag in forbidden) { element.remove(); return@forEach }
            if (tag == "svg") EpubArchivePolicy.validateSvgSize(element.attr("width"), element.attr("height"), element.attr("viewBox"))
            if (tag == "meta" && (element.hasAttr("http-equiv") || element.hasAttr("charset"))) {
                element.remove(); return@forEach
            }
            if (tag == "link" && element.attr("rel").lowercase() != "stylesheet") {
                element.remove(); return@forEach
            }
            for (attr in element.attributes().asList()) {
                val name = attr.key.lowercase()
                if (name.startsWith("on") || name in setOf("srcdoc", "srcset", "ping", "action", "formaction", "download", "target", "xml:base", "nonce", "integrity")) {
                    element.removeAttr(attr.key)
                } else if (name in setOf("href", "xlink:href", "src", "poster", "background")) {
                    if (!isLocalReference(attr.value)) element.removeAttr(attr.key)
                } else if (name == "style") element.attr(attr.key, css(attr.value))
            }
            if (tag == "style") {
                val cleaned = css(element.data().ifEmpty { element.text() })
                element.empty().appendChild(org.jsoup.nodes.DataNode(cleaned))
            }
        }
        input.childNodes().filterIsInstance<DocumentType>().forEach { it.remove() }
        if (!svg) {
            input.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", CSP)
            input.head().prependElement("meta").attr("charset", "utf-8")
        }
        return input.outerHtml().toByteArray(Charsets.UTF_8)
    }

    fun isLocalReference(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('/') || trimmed.contains('\\') || trimmed.any { it.code < 32 }) return false
        // Decode URI escapes before checking the scheme. Never authorize an SDK asset from a book.
        val decoded = runCatching { java.net.URLDecoder.decode(trimmed.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return false
        return !decoded.startsWith('/') && !decoded.contains('\\') && decoded.none { it.code < 32 } &&
            !decoded.substringBefore('/').substringBefore('#').substringBefore('?').contains(':')
    }

    fun css(text: String): String {
        // CSP is the enforcement boundary. Removing remote URLs also avoids needless failed fetches.
        // Escaped CSS identifiers are retained: CSP still rejects remote/file/data targets.
        var safe = text.replace(Regex("(?is)@import\\s+(?:url\\([^)]*\\)|[\"'][^\"']*[\"'])[^;]*;")) { match ->
            val target = Regex("(?is)url\\(\\s*([\"']?)(.*?)\\1\\s*\\)|[\"']([^\"']*)[\"']").find(match.value)
            val value = target?.groupValues?.let { it[2].ifEmpty { it[3] } }.orEmpty()
            if (isLocalReference(value)) match.value else ""
        }
        safe = safe.replace(Regex("(?is)url\\(\\s*([\"']?)(.*?)\\1\\s*\\)")) { match ->
            if (isLocalReference(match.groupValues[2])) match.value else "url('')"
        }
        return safe
    }
}
