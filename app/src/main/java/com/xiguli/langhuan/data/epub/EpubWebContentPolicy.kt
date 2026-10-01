package com.xiguli.langhuan.data.epub

import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/** Final-response policy for Readium 3.4's two underscore-containing virtual origins. */
object EpubWebContentPolicy {
    const val PACKAGE_ORIGIN = "https://readium_package/"
    const val SDK_ORIGIN = "https://readium_assets/"
    const val SDK_ALIAS_PATH = "__langhuan_readium_3_4__/"
    const val SDK_ALIAS = PACKAGE_ORIGIN + SDK_ALIAS_PATH
    const val REFLOW_SCRIPT = "readium/scripts/readium-reflowable.js"
    const val FIXED_SCRIPT = "readium/scripts/readium-fixed.js"
    const val REFLOW_HASH = "sha256-1hP+D3S4dxEbDG8uU0ZSsCzF1GE3kxZ75eaSkIHV+sk="
    const val FIXED_HASH = "sha256-ySatQJeZ+aC4QdXbNqULAddIQB+U93azAO4lvRD7jVE="
    // A URL source containing '_' is INVALID CSP syntax. 'self' is valid for the package
    // origin; exact hashes + SRI authorize only the SDK code, even after a controlled reload.
    const val CSP = "default-src 'none'; script-src '$REFLOW_HASH' '$FIXED_HASH'; " +
        "script-src-attr 'none'; style-src 'self' 'unsafe-inline'; img-src 'self'; font-src 'self'; " +
        "connect-src 'none'; frame-src 'none'; child-src 'none'; object-src 'none'; " +
        "media-src 'none'; form-action 'none'; base-uri 'none'; worker-src 'none'"

    val scriptHashes = mapOf(REFLOW_SCRIPT to REFLOW_HASH, FIXED_SCRIPT to FIXED_HASH)
    val stylesheetPaths: Set<String> = buildSet {
        for (folder in listOf("", "cjk-horizontal/", "cjk-vertical/", "rtl/")) {
            for (position in listOf("before", "default", "after")) add("readium/readium-css/${folder}ReadiumCSS-$position.css")
        }
        add("readium/readium-css/ReadiumCSS-ebpaj_fonts_patch.css")
    }
    val fontPaths = setOf("readium/fonts/OpenDyslexic-Regular.otf", "readium/readium-css/fonts/AccessibleDfA.otf",
        "readium/readium-css/fonts/iAWriterDuospace-Regular.ttf")
    val trustedAssetPaths = scriptHashes.keys + stylesheetPaths + fontPaths

    fun assetHashMatches(path: String, bytes: ByteArray): Boolean = scriptHashes[path]?.let { expected ->
        "sha256-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes)) == expected
    } ?: (path in stylesheetPaths || path in fontPaths)

    /** Returns a canonical package-relative request path, never a remote/file/content URI. */
    fun packagePath(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.rawAuthority != "readium_package") return null
        EpubArchivePolicy.localPath("", uri.rawPath.removePrefix("/"))
    }.getOrNull()

    fun aliasedAsset(url: String): String? = packagePath(url)?.takeIf { it.startsWith(SDK_ALIAS_PATH) }
        ?.removePrefix(SDK_ALIAS_PATH)?.takeIf { it in trustedAssetPaths }

    /** Called AFTER the SDK injects its scripts/styles. Never run on raw publisher HTML. */
    fun secureFinalHtml(bytes: ByteArray, xhtml: Boolean): ByteArray {
        val document = Jsoup.parse(java.io.ByteArrayInputStream(bytes), "UTF-8", "", if (xhtml) Parser.xmlParser() else Parser.htmlParser())
        document.outputSettings().prettyPrint(false).charset(Charsets.UTF_8).syntax(
            if (xhtml) Document.OutputSettings.Syntax.xml else Document.OutputSettings.Syntax.html)
        document.select("script").toList().forEach { script ->
            val path = script.attr("src").takeIf { it.startsWith(SDK_ORIGIN) }?.removePrefix(SDK_ORIGIN)
            val hash = scriptHashes[path]
            if (path == null || hash == null) script.remove()
            else {
                script.attr("src", SDK_ALIAS + path).attr("integrity", hash).attr("crossorigin", "anonymous")
                // Explicit end tag is required when the served MIME type is text/html.
                script.empty().appendChild(org.jsoup.nodes.DataNode(" "))
            }
        }
        document.select("link").toList().forEach { link ->
            val path = link.attr("href").takeIf { it.startsWith(SDK_ORIGIN) }?.removePrefix(SDK_ORIGIN)
            when {
                path in stylesheetPaths && link.attr("rel").equals("stylesheet", true) -> link.attr("href", SDK_ALIAS + path)
                !EpubContentSanitizer.isLocalReference(link.attr("href")) -> link.remove()
            }
        }
        document.select("style").forEach { style ->
            val css = style.data().ifEmpty { style.text() }.replace(SDK_ORIGIN, SDK_ALIAS)
            style.empty().appendChild(org.jsoup.nodes.DataNode(css))
        }
        document.select("meta").filter { it.attr("http-equiv").equals("Content-Security-Policy", true) }.forEach { it.remove() }
        document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", CSP)
        document.selectFirst("html")?.attr("data-langhuan-secure-readium", "3")
        return document.outerHtml().toByteArray(Charsets.UTF_8)
    }
}
