package com.xiguli.langhuan.ui.reader

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ReaderBookmarkV11(
    val id: String,
    val chapterNumber: Int,
    val pageIndex: Int = 0,
    val scrollY: Int = 0,
    val positionFraction: Float = 0f,
    val textOffset: Int = 0,
    val title: String = "",
    val excerpt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
internal data class ReaderAnnotationV11(
    val id: String,
    val chapterNumber: Int,
    val pageIndex: Int = 0,
    val scrollY: Int = 0,
    val positionFraction: Float = 0f,
    val textOffset: Int = 0,
    val quote: String = "",
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
internal data class ReaderThemePresetV11(
    val id: String,
    val name: String,
    val themeKey: String,
    val fontKey: String,
    val pageModeKey: String,
    val fontSize: Float,
    val lineFactor: Float,
    val sidePadding: Float,
    val paragraphSpacing: Float = 8f,
    val firstLineIndent: Boolean = true,
    val customBg: String,
    val customFg: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
internal data class ReaderReadingArchiveV11(
    val bookId: String,
    val bookmarks: List<ReaderBookmarkV11> = emptyList(),
    val annotations: List<ReaderAnnotationV11> = emptyList(),
    val presets: List<ReaderThemePresetV11> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Reading progress deliberately stores both old layout coordinates and stable anchors.
 * pageIndex/scrollY make same-layout restoration exact; textOffset/fraction survive font,
 * spacing, page-mode and device-size changes much better.
 */
internal data class ReaderProgressV11(
    val chapterNumber: Int,
    val pageIndex: Int = 0,
    val scrollY: Int = 0,
    val positionFraction: Float = 0f,
    val textOffset: Int = 0,
    val modeKey: String = ReaderPageModeV10.SCROLL.key,
    val updatedAt: Long = System.currentTimeMillis(),
)

internal object ReaderReadingStoreV11 {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun load(context: Context, bookId: String): ReaderReadingArchiveV11 = runCatching {
        val file = archiveFile(context, bookId)
        if (!file.exists()) ReaderReadingArchiveV11(bookId) else json.decodeFromString(ReaderReadingArchiveV11.serializer(), file.readText())
    }.getOrDefault(ReaderReadingArchiveV11(bookId))

    fun save(context: Context, archive: ReaderReadingArchiveV11): ReaderReadingArchiveV11 {
        val normalized = archive.copy(updatedAt = System.currentTimeMillis())
        val file = archiveFile(context, archive.bookId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(ReaderReadingArchiveV11.serializer(), normalized))
        return normalized
    }

    fun addBookmark(
        context: Context,
        bookId: String,
        chapterNumber: Int,
        pageIndex: Int,
        scrollY: Int,
        title: String,
        excerpt: String,
        positionFraction: Float = 0f,
        textOffset: Int = 0,
    ): ReaderReadingArchiveV11 {
        val current = load(context, bookId)
        val existing = current.bookmarks.firstOrNull {
            it.chapterNumber == chapterNumber && (
                (textOffset > 0 && kotlin.math.abs(it.textOffset - textOffset) <= 48) ||
                    (it.pageIndex == pageIndex && kotlin.math.abs(it.scrollY - scrollY) <= 80)
                )
        }
        val next = if (existing != null) {
            current.copy(bookmarks = current.bookmarks.filterNot { it.id == existing.id })
        } else {
            val bookmark = ReaderBookmarkV11(
                id = newIdV11("bookmark"),
                chapterNumber = chapterNumber,
                pageIndex = pageIndex.coerceAtLeast(0),
                scrollY = scrollY.coerceAtLeast(0),
                positionFraction = positionFraction.coerceIn(0f, 1f),
                textOffset = textOffset.coerceAtLeast(0),
                title = title.trim(),
                excerpt = excerpt.cleanExcerptV11(),
            )
            current.copy(bookmarks = (current.bookmarks + bookmark).sortedByDescending { it.createdAt })
        }
        return save(context, next)
    }

    fun deleteBookmark(context: Context, bookId: String, id: String): ReaderReadingArchiveV11 {
        val current = load(context, bookId)
        return save(context, current.copy(bookmarks = current.bookmarks.filterNot { it.id == id }))
    }

    fun addAnnotation(
        context: Context,
        bookId: String,
        chapterNumber: Int,
        pageIndex: Int,
        scrollY: Int,
        quote: String,
        note: String,
        positionFraction: Float = 0f,
        textOffset: Int = 0,
    ): ReaderReadingArchiveV11 {
        require(note.trim().isNotBlank()) { "批注内容不能为空" }
        val current = load(context, bookId)
        val annotation = ReaderAnnotationV11(
            id = newIdV11("note"),
            chapterNumber = chapterNumber,
            pageIndex = pageIndex.coerceAtLeast(0),
            scrollY = scrollY.coerceAtLeast(0),
            positionFraction = positionFraction.coerceIn(0f, 1f),
            textOffset = textOffset.coerceAtLeast(0),
            quote = quote.cleanExcerptV11(220),
            note = note.trim().take(1600),
        )
        return save(context, current.copy(annotations = (current.annotations + annotation).sortedByDescending { it.updatedAt }))
    }

    fun updateAnnotation(context: Context, bookId: String, id: String, note: String): ReaderReadingArchiveV11 {
        require(note.trim().isNotBlank()) { "批注内容不能为空" }
        val current = load(context, bookId)
        return save(
            context,
            current.copy(
                annotations = current.annotations.map {
                    if (it.id == id) it.copy(note = note.trim().take(1600), updatedAt = System.currentTimeMillis()) else it
                }.sortedByDescending { it.updatedAt },
            ),
        )
    }

    fun deleteAnnotation(context: Context, bookId: String, id: String): ReaderReadingArchiveV11 {
        val current = load(context, bookId)
        return save(context, current.copy(annotations = current.annotations.filterNot { it.id == id }))
    }

    fun savePreset(context: Context, bookId: String, preset: ReaderThemePresetV11): ReaderReadingArchiveV11 {
        val current = load(context, bookId)
        val normalizedName = preset.name.trim().ifBlank { "阅读方案" }.take(28)
        val normalized = preset.copy(name = normalizedName)
        val withoutSame = current.presets.filterNot { it.id == normalized.id || it.name.equals(normalizedName, true) }
        return save(context, current.copy(presets = listOf(normalized) + withoutSame))
    }

    fun deletePreset(context: Context, bookId: String, id: String): ReaderReadingArchiveV11 {
        val current = load(context, bookId)
        return save(context, current.copy(presets = current.presets.filterNot { it.id == id }))
    }

    fun capturePreset(context: Context, bookId: String, name: String): ReaderThemePresetV11 {
        val prefs = context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE)
        return ReaderThemePresetV11(
            id = newIdV11("preset"),
            name = name.trim().ifBlank { "阅读方案" }.take(28),
            themeKey = prefs.getString("theme_$bookId", "paper") ?: "paper",
            fontKey = prefs.getString("family_$bookId", "default") ?: "default",
            pageModeKey = prefs.getString("page_mode_$bookId", ReaderPageModeV10.SCROLL.key) ?: ReaderPageModeV10.SCROLL.key,
            fontSize = prefs.getFloat("font_$bookId", 19f),
            lineFactor = prefs.getFloat("line_$bookId", 1.68f),
            sidePadding = prefs.getFloat("padding_$bookId", 24f),
            paragraphSpacing = prefs.getFloat("paragraph_$bookId", 8f),
            firstLineIndent = prefs.getBoolean("indent_$bookId", true),
            customBg = prefs.getString("custom_bg_$bookId", "#FFF4F0E6") ?: "#FFF4F0E6",
            customFg = prefs.getString("custom_fg_$bookId", "#FF302D28") ?: "#FF302D28",
        )
    }

    fun applyPreset(context: Context, bookId: String, preset: ReaderThemePresetV11) {
        context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE).edit()
            .putString("theme_$bookId", preset.themeKey)
            .putString("family_$bookId", preset.fontKey)
            .putString("page_mode_$bookId", preset.pageModeKey)
            .putFloat("font_$bookId", preset.fontSize.coerceIn(14f, 30f))
            .putFloat("line_$bookId", preset.lineFactor.coerceIn(1.42f, 2.25f))
            .putFloat("padding_$bookId", preset.sidePadding.coerceIn(14f, 44f))
            .putFloat("paragraph_$bookId", preset.paragraphSpacing.coerceIn(0f, 30f))
            .putBoolean("indent_$bookId", preset.firstLineIndent)
            .putString("custom_bg_$bookId", preset.customBg)
            .putString("custom_fg_$bookId", preset.customFg)
            .apply()
    }

    private fun archiveFile(context: Context, bookId: String): File =
        File(context.filesDir, "reader_notes_v1/${bookId.safeFileNameV11()}.json")
}

internal object ReaderProgressStoreV11 {
    private const val PREFS = "reader_progress_v2"

    fun load(context: Context, bookId: String, fallbackChapter: Int): ReaderProgressV11 {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ReaderProgressV11(
            chapterNumber = prefs.getInt("chapter_$bookId", fallbackChapter.coerceAtLeast(1)),
            pageIndex = prefs.getInt("page_$bookId", 0).coerceAtLeast(0),
            scrollY = prefs.getInt("scroll_$bookId", 0).coerceAtLeast(0),
            positionFraction = prefs.getFloat("fraction_$bookId", 0f).coerceIn(0f, 1f),
            textOffset = prefs.getInt("offset_$bookId", 0).coerceAtLeast(0),
            modeKey = prefs.getString("mode_$bookId", ReaderPageModeV10.SCROLL.key) ?: ReaderPageModeV10.SCROLL.key,
            updatedAt = prefs.getLong("updated_$bookId", 0L),
        )
    }

    fun save(context: Context, bookId: String, progress: ReaderProgressV11) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("chapter_$bookId", progress.chapterNumber.coerceAtLeast(1))
            .putInt("page_$bookId", progress.pageIndex.coerceAtLeast(0))
            .putInt("scroll_$bookId", progress.scrollY.coerceAtLeast(0))
            .putFloat("fraction_$bookId", progress.positionFraction.coerceIn(0f, 1f))
            .putInt("offset_$bookId", progress.textOffset.coerceAtLeast(0))
            .putString("mode_$bookId", progress.modeKey)
            .putLong("updated_$bookId", System.currentTimeMillis())
            .commit()
        context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE).edit()
            .putInt("chapter_$bookId", progress.chapterNumber.coerceAtLeast(1))
            .putLong("last_$bookId", System.currentTimeMillis())
            .apply()
    }

    fun moveTo(
        context: Context,
        bookId: String,
        chapterNumber: Int,
        pageIndex: Int,
        scrollY: Int,
        modeKey: String,
        positionFraction: Float = 0f,
        textOffset: Int = 0,
    ) {
        save(
            context,
            bookId,
            ReaderProgressV11(
                chapterNumber = chapterNumber,
                pageIndex = pageIndex,
                scrollY = scrollY,
                positionFraction = positionFraction,
                textOffset = textOffset,
                modeKey = modeKey,
            ),
        )
    }
}

/** Built-in typography profiles inspired by common Chinese novel-reader reading densities. */
internal fun readerBuiltInPresetsV12(): List<ReaderThemePresetV11> = listOf(
    ReaderThemePresetV11(
        id = "builtin-langhuan",
        name = "琅嬛 · 沉浸",
        themeKey = "paper",
        fontKey = "serif",
        pageModeKey = ReaderPageModeV10.COVER.key,
        fontSize = 20f,
        lineFactor = 1.72f,
        sidePadding = 24f,
        paragraphSpacing = 8f,
        firstLineIndent = true,
        customBg = "#FFF4F0E6",
        customFg = "#FF302D28",
        createdAt = 0L,
    ),
    ReaderThemePresetV11(
        id = "builtin-tomato",
        name = "番茄灵感 · 舒展",
        themeKey = "warm",
        fontKey = "default",
        pageModeKey = ReaderPageModeV10.COVER.key,
        fontSize = 21f,
        lineFactor = 1.70f,
        sidePadding = 21f,
        paragraphSpacing = 8f,
        firstLineIndent = true,
        customBg = "#FFF3E5C9",
        customFg = "#FF362D23",
        createdAt = 0L,
    ),
    ReaderThemePresetV11(
        id = "builtin-weread",
        name = "微信读书灵感 · 清朗",
        themeKey = "system",
        fontKey = "serif",
        pageModeKey = ReaderPageModeV10.PAGE.key,
        fontSize = 19f,
        lineFactor = 1.68f,
        sidePadding = 27f,
        paragraphSpacing = 9f,
        firstLineIndent = true,
        customBg = "#FFF9F7F1",
        customFg = "#FF252525",
        createdAt = 0L,
    ),
    ReaderThemePresetV11(
        id = "builtin-qidian",
        name = "起点灵感 · 网文",
        themeKey = "paper",
        fontKey = "default",
        pageModeKey = ReaderPageModeV10.PAGE.key,
        fontSize = 19f,
        lineFactor = 1.62f,
        sidePadding = 19f,
        paragraphSpacing = 7f,
        firstLineIndent = true,
        customBg = "#FFF4F0E6",
        customFg = "#FF302D28",
        createdAt = 0L,
    ),
)

internal fun deleteReaderFontV11(context: Context, asset: ReaderFontAssetV10): Boolean {
    val deleted = ReaderFontStoreV10.delete(asset)
    if (!deleted) return false
    val selectedKey = "custom:${asset.path}"
    val prefs = context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    prefs.all.forEach { (key, value) ->
        if (key.startsWith("family_") && value == selectedKey) editor.putString(key, "default")
    }
    editor.apply()
    return true
}

internal fun readerLocationLabelV11(chapterNumber: Int, pageIndex: Int, scrollY: Int, pageMode: ReaderPageModeV10): String =
    if (pageMode == ReaderPageModeV10.SCROLL) {
        if (scrollY <= 0) "第 $chapterNumber 章 · 章首" else "第 $chapterNumber 章 · 纵向位置 $scrollY"
    } else {
        "第 $chapterNumber 章 · 第 ${pageIndex + 1} 页"
    }

internal fun readerExcerptAtV11(text: String, progress: Float, maxLength: Int = 90): String {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isBlank()) return ""
    val center = (clean.length * progress.coerceIn(0f, 1f)).toInt().coerceIn(0, clean.lastIndex)
    val half = (maxLength / 2).coerceAtLeast(12)
    val start = (center - half).coerceAtLeast(0)
    val end = (start + maxLength).coerceAtMost(clean.length)
    return clean.substring(start, end).trim()
}

private fun String.cleanExcerptV11(maxLength: Int = 120): String = replace(Regex("\\s+"), " ").trim().take(maxLength)
private fun String.safeFileNameV11(): String = replace(Regex("[^0-9A-Za-z._-]+"), "_").take(96).ifBlank { "book" }
private fun newIdV11(prefix: String): String = "$prefix-${UUID.randomUUID().toString().replace("-", "").take(16)}"
