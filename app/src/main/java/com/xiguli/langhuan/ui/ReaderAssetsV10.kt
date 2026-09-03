package com.xiguli.langhuan.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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

internal fun readerDisplayChapterTitleV13(title: String, chapterNumber: Int): String =
    title.replace(Regex("\\s+"), " ").trim().ifBlank { "第 $chapterNumber 章" }

internal fun readerBodyWithoutDuplicateHeadingV13(title: String, content: String): String {
    val body = content.replace("\r\n", "\n").trimStart()
    if (body.isBlank()) return body
    val displayTitle = title.replace(Regex("\\s+"), " ").trim()
    if (displayTitle.isBlank()) return body

    val compactBody = body.replaceFirst(Regex("^[\\uFEFF\\s]*"), "")
    if (compactBody.startsWith(displayTitle, ignoreCase = true)) {
        return compactBody.drop(displayTitle.length).trimStart('\n', '\r', ' ', '\t')
    }

    val chapterPrefix = Regex("^(第\\s*[0-9０-９一二三四五六七八九十百千万零〇两]+\\s*[章节回卷])(?:\\s+|$)")
        .find(displayTitle)?.groupValues?.getOrNull(1)?.trim()
    val suffix = chapterPrefix?.let { displayTitle.removePrefix(it).trim() }.orEmpty()
    if (!chapterPrefix.isNullOrBlank()) {
        val lines = compactBody.lines()
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstIndex >= 0 && lines[firstIndex].trim().replace(Regex("\\s+"), " ").equals(chapterPrefix.replace(Regex("\\s+"), " "), true)) {
            var endIndex = firstIndex + 1
            if (suffix.isNotBlank()) {
                val secondIndex = lines.indexOfFirstFromV13(firstIndex + 1) { it.isNotBlank() }
                if (secondIndex >= 0 && lines[secondIndex].trim().replace(Regex("\\s+"), " ").equals(suffix, true)) {
                    endIndex = secondIndex + 1
                }
            }
            return lines.drop(endIndex).joinToString("\n").trimStart()
        }
    }
    return compactBody
}

private inline fun List<String>.indexOfFirstFromV13(start: Int, predicate: (String) -> Boolean): Int {
    for (index in start.coerceAtLeast(0)..lastIndex) if (predicate(this[index])) return index
    return -1
}

/** Normalize prose paragraphs so the paginator and renderer share one paragraph model. */
internal fun readerNormalizeBodyV14(text: String): String = text
    .replace("\r\n", "\n")
    .replace(Regex("\\n[ \\t]*\\n+"), "\n")
    .trim()

/**
 * Real measured pagination for phone reading.
 *
 * V14 measures real Android text lines and models paragraph gaps in dp. It no longer counts a blank
 * text row as paragraph spacing. The first page has a compact chapter heading; ordinary pages reserve
 * room for a quiet running header. Both reserve the persistent footer used by ReaderPagedLayoutV14.
 */
internal fun splitReaderPagesV10(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float = 8f,
): List<String> {
    val normalized = readerNormalizeBodyV14(text)
    if (normalized.isBlank()) return listOf("")

    return runCatching {
        val metrics = Resources.getSystem().displayMetrics
        val density = metrics.density.coerceAtLeast(1f)
        val scaledDensity = metrics.scaledDensity.coerceAtLeast(density)
        val widthDp = metrics.widthPixels / density
        val heightDp = metrics.heightPixels / density

        val widthPx = ((widthDp - sidePadding * 2f).coerceAtLeast(180f) * density).toInt().coerceAtLeast(1)
        // V14 page chrome is intentionally small. The old 205/190dp reserve caused every page to
        // stop several lines too early, leaving a large fake blank band above the footer.
        // Keep only the real title/running-header + footer + system inset budget here.
        val firstBodyHeightPx = ((heightDp - 152f).coerceAtLeast(300f) * density).toInt()
        val normalBodyHeightPx = ((heightDp - 136f).coerceAtLeast(320f) * density).toInt()
        val paragraphExtraPx = (paragraphSpacing.coerceIn(0f, 24f) * density).toInt()

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize.coerceIn(12f, 36f) * scaledDensity
            typeface = Typeface.DEFAULT
        }
        val baseFontHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top).coerceAtLeast(1f)
        val requestedLineHeight = fontSize.coerceIn(12f, 36f) * lineFactor.coerceIn(1.2f, 2.6f) * scaledDensity
        val spacingMultiplier = (requestedLineHeight / baseFontHeight).coerceIn(.8f, 2.6f)

        val layout = StaticLayout.Builder.obtain(normalized, 0, normalized.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, spacingMultiplier)
            .build()

        val pages = mutableListOf<String>()
        var line = 0
        while (line < layout.lineCount) {
            val firstLine = line
            val maxHeight = if (pages.isEmpty()) firstBodyHeightPx else normalBodyHeightPx
            var usedHeight = 0
            while (line < layout.lineCount) {
                val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(1)
                val lineStart = layout.getLineStart(line).coerceIn(0, normalized.length)
                val lineEnd = layout.getLineEnd(line).coerceIn(lineStart, normalized.length)
                val segment = normalized.substring(lineStart, lineEnd)
                val paragraphExtra = if (segment.endsWith("\n") && lineEnd < normalized.length) paragraphExtraPx else 0
                val nextHeight = usedHeight + lineHeight + paragraphExtra
                if (line > firstLine && nextHeight > maxHeight) break
                usedHeight = nextHeight
                line++
            }
            if (line == firstLine) line++
            val startOffset = layout.getLineStart(firstLine).coerceIn(0, normalized.length)
            val endOffset = layout.getLineEnd((line - 1).coerceAtLeast(firstLine)).coerceIn(startOffset, normalized.length)
            val page = normalized.substring(startOffset, endOffset).trim()
            if (page.isNotBlank()) pages += page
        }
        pages.ifEmpty { listOf(normalized) }
    }.getOrElse {
        val fontPenalty = (fontSize / 20f).coerceIn(.7f, 2f)
        val linePenalty = (lineFactor / 1.68f).coerceIn(.75f, 1.6f)
        val widthPenalty = (1f + ((sidePadding - 24f) / 75f)).coerceIn(.72f, 1.45f)
        val target = (330f / (fontPenalty * linePenalty * widthPenalty)).toInt().coerceIn(80, 460)
        normalized.chunked(target).filter { it.isNotBlank() }.ifEmpty { listOf(normalized) }
    }
}