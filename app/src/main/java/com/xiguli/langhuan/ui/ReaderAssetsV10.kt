package com.xiguli.langhuan.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.Locale
import java.util.UUID

internal enum class ReaderPageModeV10(val key: String, val label: String, val summary: String) {
    SCROLL("scroll", "上下滚动", "连续阅读，适合长时间阅读"),
    PAGE("page", "左右滑页", "整页横向滑动，支持跨章前后翻"),
    COVER("cover", "覆盖翻页", "整页覆盖切换，保持稳定阅读区域"),
}

internal data class ReaderCustomThemeV10(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
)

internal data class ReaderFontAssetV10(
    val id: String,
    val name: String,
    val path: String,
)

internal object ReaderFontStoreV10 {
    private const val MAX_FONT_BYTES = 24L * 1024L * 1024L

    fun import(context: Context, uri: Uri): Result<ReaderFontAssetV10> = runCatching {
        val resolver = context.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("").ifBlank { "reader-font.ttf" }
        val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(ext in setOf("ttf", "otf")) { "请选择 TTF 或 OTF 字体文件" }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取字体文件")
        require(bytes.isNotEmpty()) { "字体文件是空的" }
        require(bytes.size.toLong() <= MAX_FONT_BYTES) { "字体文件过大，目前最大支持 24 MB" }
        val dir = File(context.filesDir, "reader_fonts_v1").apply { mkdirs() }
        val safeBase = displayName.substringBeforeLast('.').replace(Regex("[^0-9A-Za-z一-龥._-]+"), "_").take(48).ifBlank { "font" }
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val file = File(dir, "${safeBase}_$id.$ext")
        file.writeBytes(bytes)
        Typeface.createFromFile(file)
        ReaderFontAssetV10(id = file.nameWithoutExtension, name = displayName.substringBeforeLast('.'), path = file.absolutePath)
    }

    fun list(context: Context): List<ReaderFontAssetV10> {
        val dir = File(context.filesDir, "reader_fonts_v1")
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("ttf", "otf") }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .map { file ->
                val readable = file.nameWithoutExtension.substringBeforeLast('_').replace('_', ' ').ifBlank { file.nameWithoutExtension }
                ReaderFontAssetV10(file.nameWithoutExtension, readable, file.absolutePath)
            }
    }

    fun delete(asset: ReaderFontAssetV10): Boolean = runCatching { File(asset.path).delete() }.getOrDefault(false)

    fun family(path: String): FontFamily? = if (path.isBlank()) null else runCatching {
        FontFamily(Typeface.createFromFile(path))
    }.getOrNull()
}

internal fun parseReaderHexColorV10(value: String): Color? {
    val raw = value.trim().removePrefix("#")
    val normalized = when (raw.length) {
        6 -> "FF$raw"
        8 -> raw
        else -> return null
    }
    return normalized.toLongOrNull(16)?.let { Color(it) }
}

internal fun readerColorHexV10(color: Color): String {
    val a = (color.alpha * 255f).toInt().coerceIn(0, 255)
    val r = (color.red * 255f).toInt().coerceIn(0, 255)
    val g = (color.green * 255f).toInt().coerceIn(0, 255)
    val b = (color.blue * 255f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X%02X".format(a, r, g, b)
}

/**
 * Conservative phone-page fallback. The old implementation assumed about 920 characters per page,
 * which is far beyond a normal phone viewport and caused the lower part of a page to be clipped.
 *
 * This fallback estimates capacity from the actual device dp size and font scale, then keeps generous
 * headroom for safe-system insets, chapter title, footer and mixed-width punctuation. Stable reading
 * position is stored separately as chapter + textOffset, so repagination can change page count safely.
 */
internal fun splitReaderPagesV10(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float = 12f,
): List<String> {
    val normalized = text.trim()
    if (normalized.isBlank()) return listOf("")

    val metrics = runCatching { Resources.getSystem().displayMetrics }.getOrNull()
    val density = metrics?.density?.coerceAtLeast(1f) ?: 1f
    val scaledDensity = metrics?.scaledDensity?.coerceAtLeast(density) ?: density
    // The fallback values are intentionally phone-sized and are used only by plain JVM tests where
    // android.content.res.Resources is a stub. A real Android process always uses its display metrics.
    val widthDp = metrics?.let { it.widthPixels / density } ?: 393f
    val heightDp = metrics?.let { it.heightPixels / density } ?: 820f
    val effectiveFontDp = fontSize * (scaledDensity / density)

    // ReaderExperience itself also applies WindowInsets.safeDrawing. Reserve extra vertical space
    // here because the first page additionally contains a chapter title and every page has a footer.
    val usableWidthDp = (widthDp - sidePadding * 2f).coerceAtLeast(220f)
    val usableHeightDp = (heightDp - 170f).coerceAtLeast(360f)
    val approximateCharWidthDp = (effectiveFontDp * 1.02f).coerceAtLeast(12f)
    val approximateLineHeightDp = (effectiveFontDp * lineFactor).coerceAtLeast(20f)
    val charsPerLine = (usableWidthDp / approximateCharWidthDp).coerceIn(10f, 38f)
    val linesPerPage = (usableHeightDp / approximateLineHeightDp).coerceIn(10f, 32f)

    val paragraphPenalty = (1f + (paragraphSpacing - 12f) / 95f).coerceIn(.9f, 1.25f)
    // 82% leaves room for glyph-width variance, punctuation, English runs and the chapter-title page.
    val target = ((charsPerLine * linesPerPage * .82f) / paragraphPenalty)
        .toInt()
        .coerceIn(180, 620)

    val paragraphs = normalized.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    val pages = mutableListOf<String>()
    val buffer = StringBuilder()

    fun flush() {
        if (buffer.isNotBlank()) pages += buffer.toString().trim()
        buffer.clear()
    }

    paragraphs.forEach { paragraph ->
        if (paragraph.length > target * 2) {
            flush()
            paragraph.chunked(target).forEach { chunk -> if (chunk.isNotBlank()) pages += chunk.trim() }
        } else if (buffer.length + paragraph.length + 2 > target && buffer.isNotEmpty()) {
            flush()
            buffer.append(paragraph)
        } else {
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(paragraph)
        }
    }
    flush()
    return pages.ifEmpty { listOf(normalized) }
}
