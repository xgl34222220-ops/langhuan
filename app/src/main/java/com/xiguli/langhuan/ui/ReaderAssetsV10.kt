package com.xiguli.langhuan.ui

import android.content.Context
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
 * Conservative fallback pagination used by the Compose reader.
 *
 * The previous implementation targeted ~920 characters and clamped the minimum to 420.
 * On a phone at 19–21sp with normal line spacing that is roughly 1.7–2x the amount that can
 * actually fit between the safe top/bottom reading margins, so Text was simply clipped at the
 * bottom.  Mature readers paginate from a stable character position and never assume a desktop-
 * sized text viewport.  Until the measured-layout paginator is fully extracted, keep this fallback
 * deliberately conservative: it is better to have a little breathing room than to lose half a page.
 */
internal fun splitReaderPagesV10(text: String, fontSize: Float, lineFactor: Float, sidePadding: Float): List<String> {
    val normalized = text.trim()
    if (normalized.isBlank()) return listOf("")

    val fontPenalty = (fontSize / 19f).coerceIn(.72f, 1.7f)
    val linePenalty = (lineFactor / 1.8f).coerceIn(.75f, 1.45f)
    val widthPenalty = (1f + ((sidePadding - 24f) / 82f)).coerceIn(.78f, 1.35f)
    val densityPenalty = fontPenalty * linePenalty * widthPenalty

    // ~390 Chinese characters is a realistic full phone page at 20sp / 1.8 line height.
    // Leave headroom for punctuation, Latin text, chapter titles and the quiet footer.
    val target = (410f / densityPenalty).toInt().coerceIn(220, 720)
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
            paragraph.chunked(target).forEach { chunk ->
                if (chunk.isNotBlank()) pages += chunk.trim()
            }
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
