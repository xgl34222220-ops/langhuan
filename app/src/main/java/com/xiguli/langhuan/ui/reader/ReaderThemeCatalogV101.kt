package com.xiguli.langhuan.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * Reader V101 · 日间 / 夜间主题。
 *
 * Every theme is a page colour, a text colour and a decoration drawn under the text. Built-in
 * themes ship in the APK (procedural decorations, no downloads). 「更多主题」 adds bundled extras
 * to the user's day or night list; 「新建主题」 creates a custom theme (colours + an optional
 * picture copied into app storage). The day list and the night list each have one selection;
 * the reader shows the night selection in night mode (night icon or 背景跟随系统).
 */

/** Decoration painted under the body text. */
internal enum class ReaderThemeDecorV101(val key: String) {
    NONE("none"),
    /** Ink bamboo in the top corner (番茄). */
    BAMBOO("bamboo"),
    /** Two soft concentric arcs in the lower corner. */
    ARC("arc"),
    /** A sprig of small blossoms in the top corner (花开). */
    FLOWER("flower"),
    /** The user's own picture ([ReaderThemeSpecV101.imagePath]). */
    IMAGE("image"),
    ;

    companion object {
        fun of(key: String?): ReaderThemeDecorV101 = entries.firstOrNull { it.key == key } ?: NONE
    }
}

@Serializable
internal data class ReaderThemeSpecV101(
    val key: String,
    val name: String,
    val subtitle: String = "",
    /** ARGB. */
    val page: Long,
    /** ARGB. */
    val text: Long,
    val dark: Boolean = false,
    val decor: String = ReaderThemeDecorV101.NONE.key,
    val custom: Boolean = false,
    val imagePath: String? = null,
) {
    val decoration: ReaderThemeDecorV101 get() = ReaderThemeDecorV101.of(decor)

    fun toTheme(): ReaderThemeV30 {
        val pageColor = Color(page.toInt())
        val textColor = Color(text.toInt())
        return ReaderThemeV30(
            key = key,
            name = name,
            page = pageColor,
            text = textColor,
            secondary = lerp(textColor, pageColor, 0.52f),
            dark = dark,
            decor = decoration,
            imagePath = imagePath,
            subtitle = subtitle,
        )
    }
}

internal object ReaderThemeCatalogV101 {
    const val DEFAULT_DAY = "tea"
    const val DEFAULT_NIGHT = "night"
    const val CUSTOM_PREFIX = "custom_"
    const val MAX_CUSTOM = 24

    private fun spec(
        key: String, name: String, subtitle: String, page: Long, text: Long,
        decor: ReaderThemeDecorV101 = ReaderThemeDecorV101.NONE, dark: Boolean = false,
    ) = ReaderThemeSpecV101(key, name, subtitle, page, text, dark, decor.key)

    /** Shown in the day list on first run, in this order. */
    val BUILT_IN_DAY: List<ReaderThemeSpecV101> = listOf(
        spec("tea", "番茄", "鲸落", 0xFFDCD8C2, 0xFF1C1A16, ReaderThemeDecorV101.BAMBOO),
        spec("green", "绿", "竹青", 0xFFCFE0CB, 0xFF1D2A1C, ReaderThemeDecorV101.ARC),
        spec("sheep", "书卷", "书香", 0xFFEBDDBF, 0xFF3A2E1F, ReaderThemeDecorV101.ARC),
        spec("qidian", "起点护眼色", "文章一代颜风流", 0xFFC7DFC4, 0xFF20301F, ReaderThemeDecorV101.ARC),
        spec("ink", "墨色", "momo", 0xFFE3E7EE, 0xFF1C2129, ReaderThemeDecorV101.ARC),
        spec("blossom", "花开", "微宁", 0xFFF1ECE4, 0xFF2B2622, ReaderThemeDecorV101.FLOWER),
    )

    /** Shown in the night list on first run. */
    val BUILT_IN_NIGHT: List<ReaderThemeSpecV101> = listOf(
        spec("night", "夜间", "静夜", 0xFF151516, 0xFFA8A8AA, ReaderThemeDecorV101.NONE, dark = true),
        spec("nightInk", "墨夜", "深蓝", 0xFF161B24, 0xFF9AA4B4, ReaderThemeDecorV101.ARC, dark = true),
        spec("nightBamboo", "竹影", "夜色", 0xFF161A16, 0xFF9DAA9B, ReaderThemeDecorV101.BAMBOO, dark = true),
    )

    /** Bundled extras offered by 「更多主题」; never downloaded. */
    val MORE_DAY: List<ReaderThemeSpecV101> = listOf(
        spec("paper", "纸白", "素笺", 0xFFF7F5EF, 0xFF1E1D1B),
        spec("pink", "樱粉", "春日", 0xFFF4E4E1, 0xFF2E2121, ReaderThemeDecorV101.FLOWER),
        spec("langhuan", "雾蓝", "琅嬛", 0xFFDDE5EE, 0xFF1C2129, ReaderThemeDecorV101.ARC),
        spec("white", "纯白", "无饰", 0xFFFFFFFF, 0xFF151515),
        spec("parchment", "宣纸", "旧时光", 0xFFE9E1CF, 0xFF33291C, ReaderThemeDecorV101.BAMBOO),
    )
    val MORE_NIGHT: List<ReaderThemeSpecV101> = listOf(
        spec("nightAmber", "烛光", "暖夜", 0xFF1E1914, 0xFFB3A48C, ReaderThemeDecorV101.ARC, dark = true),
        spec("nightPure", "纯黑", "OLED", 0xFF000000, 0xFF8E8E90, dark = true),
    )

    val ALL_BUILT_IN: List<ReaderThemeSpecV101> = BUILT_IN_DAY + BUILT_IN_NIGHT + MORE_DAY + MORE_NIGHT

    fun builtIn(key: String): ReaderThemeSpecV101? = ALL_BUILT_IN.firstOrNull { it.key == key }

    fun more(dark: Boolean): List<ReaderThemeSpecV101> = if (dark) MORE_NIGHT else MORE_DAY

    fun defaultInstalled(dark: Boolean): List<String> =
        (if (dark) BUILT_IN_NIGHT else BUILT_IN_DAY).map { it.key }

    /** Resolves a key against the built-ins and the given custom themes. */
    fun resolve(key: String, custom: List<ReaderThemeSpecV101>): ReaderThemeSpecV101? =
        builtIn(key) ?: custom.firstOrNull { it.key == key }

    /** Installed keys, dropping unknown entries and duplicates; empty falls back to the defaults. */
    fun sanitizeInstalled(keys: List<String>, dark: Boolean, custom: List<ReaderThemeSpecV101>): List<String> {
        val clean = keys.distinct().filter { key -> resolve(key, custom)?.dark == dark }
        return clean.ifEmpty { defaultInstalled(dark) }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(ReaderThemeSpecV101.serializer())

    fun encodeCustom(themes: List<ReaderThemeSpecV101>): String = json.encodeToString(listSerializer, themes)

    fun decodeCustom(raw: String?): List<ReaderThemeSpecV101> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
            .filter { it.custom && it.key.startsWith(CUSTOM_PREFIX) && it.name.isNotBlank() }
            .distinctBy { it.key }
            .take(MAX_CUSTOM)
    }

    fun newCustom(
        name: String, page: Color, text: Color, dark: Boolean, imagePath: String?, id: String,
    ): ReaderThemeSpecV101 = ReaderThemeSpecV101(
        key = CUSTOM_PREFIX + id,
        name = name.trim().take(12).ifBlank { "我的主题" },
        subtitle = "自定义",
        page = page.toArgbLong(),
        text = text.toArgbLong(),
        dark = dark,
        decor = if (imagePath != null) ReaderThemeDecorV101.IMAGE.key else ReaderThemeDecorV101.NONE.key,
        custom = true,
        imagePath = imagePath,
    )

    private fun Color.toArgbLong(): Long {
        val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * Custom themes visible to [readerThemeV30]. [ReaderSettingsV30] owns the list and keeps this
 * registry current, so every page painter resolves custom keys without carrying the settings.
 */
internal object ReaderThemeRegistryV101 {
    var custom by mutableStateOf<List<ReaderThemeSpecV101>>(emptyList())
}
