package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reader V30 · 阅读设置状态。
 *
 * 与 ReaderScreenV30.kt / ReaderMenuV30.kt / ReaderLayoutEngineV30.kt 保持统一。
 * 阅读器正文主题与 App 全局浅色 / 深色主题完全独立。
 *
 * 默认正文排版：18sp / 1.95 行距 / 首行缩进两个中文字符 / 500 字重。
 *
 * 注意（适配核实项）：
 * - ReaderTurnModeV30 按 ChatGPT 说明「已由 ReaderLayoutEngineV30.kt 定义
 *   （COVER/SLIDE/SIMULATION/SCROLL/NONE + key + label + of()）」，
 *   本文件不重复声明，避免 Kotlin Redeclaration。若仓库实际没有该定义，
 *   适配时使用下面注释中的枚举定义。
 * - readerThemeKeyV30() 为本文件引用但未定义的顶层函数，需确认他处已存在。
 */
// --- ReaderTurnModeV30 备选定义（仅当仓库 ReaderLayoutEngineV30.kt 未定义时启用） ---
// internal enum class ReaderTurnModeV30(val key: String, val label: String) {
//     COVER("cover", "覆盖"),
//     SLIDE("page", "平移"),
//     SIMULATION("simulation", "仿真"),
//     SCROLL("scroll", "上下滚动"),
//     NONE("none", "无动画");
//     companion object {
//         fun of(key: String?): ReaderTurnModeV30 =
//             entries.firstOrNull { it.key == key } ?: COVER
//     }
// }

@Stable
internal class ReaderSettingsV30(
    private val prefs: SharedPreferences,
) {
    /* ------------------------- Preference readers ------------------------- */
    private fun float(key: String, fallback: Float): Float {
        return runCatching { prefs.getFloat(key, fallback) }.getOrNull()
            ?: runCatching { prefs.getInt(key, fallback.toInt()).toFloat() }.getOrNull()
            ?: fallback
    }

    private fun bool(key: String, fallback: Boolean): Boolean {
        return runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)
    }

    private fun string(key: String, fallback: String): String {
        return runCatching { prefs.getString(key, fallback) }.getOrNull() ?: fallback
    }

    private fun readWeight(): Int {
        val direct = runCatching { prefs.getInt(KEY_FONT_WEIGHT, DEFAULT_WEIGHT) }.getOrNull()
        if (direct != null) return direct.coerceIn(100, 900)
        return when (string(KEY_FONT_WEIGHT, "").lowercase()) {
            "thin" -> 200
            "light" -> 300
            "regular", "normal" -> 400
            "medium" -> 500
            "semibold", "semi_bold" -> 600
            "bold" -> 700
            "heavy", "black" -> 800
            else -> DEFAULT_WEIGHT
        }
    }

    /* ------------------------------ Typography ------------------------------ */
    var fontSize by mutableFloatStateOf(
        float(KEY_FONT_SIZE, DEFAULT_FONT).coerceIn(MIN_FONT, MAX_FONT),
    )
    var letterSpacing by mutableFloatStateOf(
        float(KEY_LETTER_SPACING, DEFAULT_LETTER_SPACING)
            .coerceIn(MIN_LETTER_SPACING, MAX_LETTER_SPACING),
    )
    var lineFactor by mutableFloatStateOf(
        float(KEY_LINE_FACTOR, DEFAULT_LINE).coerceIn(MIN_LINE, MAX_LINE),
    )
    var paragraphSpacing by mutableFloatStateOf(
        float(KEY_PARAGRAPH_SPACING, DEFAULT_PARAGRAPH).coerceIn(MIN_PARAGRAPH, MAX_PARAGRAPH),
    )
    var sidePadding by mutableFloatStateOf(
        float(KEY_SIDE_PADDING, DEFAULT_SIDE).coerceIn(MIN_SIDE, MAX_SIDE),
    )
    var indent by mutableStateOf(bool(KEY_INDENT, true))
    var fontKey by mutableStateOf(
        string(KEY_FONT, DEFAULT_FONT_KEY).takeIf { it in SUPPORTED_FONT_KEYS }
            ?: DEFAULT_FONT_KEY,
    )
    var weight by mutableIntStateOf(readWeight())

    /* --------------------------- Reader Theme --------------------------- */
    var theme by mutableStateOf(
        readerThemeKeyV30(string(KEY_THEME, DEFAULT_THEME)),
    )
    private var dayTheme by mutableStateOf(
        readerThemeKeyV30(string(KEY_DAY_THEME, DEFAULT_THEME))
            .takeIf { it != NIGHT_THEME } ?: DEFAULT_THEME,
    )
    val night: Boolean get() = theme == NIGHT_THEME

    fun toggleNight() {
        if (night) {
            theme = dayTheme.takeIf { it != NIGHT_THEME } ?: DEFAULT_THEME
        } else {
            if (theme != NIGHT_THEME) dayTheme = theme
            theme = NIGHT_THEME
        }
    }

    fun selectTheme(key: String) {
        val normalized = readerThemeKeyV30(key)
        theme = normalized
        if (normalized != NIGHT_THEME) dayTheme = normalized
    }

    /* ---------------------------- Page Turn ---------------------------- */
    var turnMode by mutableStateOf(
        ReaderTurnModeV30.of(string(KEY_PAGE_MODE, ReaderTurnModeV30.COVER.key)),
    )
    var lastPagedMode by mutableStateOf(
        ReaderTurnModeV30.of(string(KEY_LAST_PAGED_MODE, ReaderTurnModeV30.COVER.key))
            .takeIf { it != ReaderTurnModeV30.SCROLL } ?: ReaderTurnModeV30.COVER,
    )

    fun selectTurnMode(mode: ReaderTurnModeV30) {
        if (mode != ReaderTurnModeV30.SCROLL) lastPagedMode = mode
        turnMode = mode
    }

    var clickAnimation by mutableStateOf(bool(KEY_CLICK_ANIMATION, true))
    var fullNext by mutableStateOf(bool(KEY_FULL_NEXT, false))
    var volumeTurn by mutableStateOf(bool(KEY_VOLUME_TURN, false))

    /* ------------------------------- Screen ------------------------------- */
    var keepScreen by mutableStateOf(bool(KEY_KEEP_SCREEN, false))
    var showTimeBattery by mutableStateOf(bool(KEY_TIME_BATTERY, true))
    var immersive by mutableStateOf(bool(KEY_IMMERSIVE, false))
    var lockPortrait by mutableStateOf(bool(KEY_LOCK_PORTRAIT, true))

    /* -------------------------- Light & backdrop (V92) -------------------------- */
    /** Window brightness 0.05..1, or [READER_BRIGHTNESS_SYSTEM_V92] to follow the system. */
    var brightness by mutableFloatStateOf(
        float(KEY_BRIGHTNESS, READER_BRIGHTNESS_SYSTEM_V92).let {
            if (it < 0f) READER_BRIGHTNESS_SYSTEM_V92 else it.coerceIn(READER_MIN_BRIGHTNESS_V92, 1f)
        },
    )
    /** Warm-light strength 0..1; 0 is off. */
    var warmth by mutableFloatStateOf(float(KEY_WARMTH, 0f).coerceIn(0f, 1f))
    var backdrop by mutableStateOf(ReaderBackdropV92.of(string(KEY_BACKDROP, ReaderBackdropV92.NONE.key)).key)
    /** Bumped when a new gallery picture is imported so the decoded bitmap is refreshed. */
    var backdropImageVersion by mutableStateOf(
        runCatching { prefs.getLong(KEY_BACKDROP_IMAGE_VERSION, 0L) }.getOrDefault(0L),
    )

    /* ------------------------- Reset Typography ------------------------- */
    fun resetTypography() {
        fontSize = DEFAULT_FONT
        letterSpacing = DEFAULT_LETTER_SPACING
        lineFactor = DEFAULT_LINE
        paragraphSpacing = DEFAULT_PARAGRAPH
        sidePadding = DEFAULT_SIDE
        indent = true
        fontKey = DEFAULT_FONT_KEY
        weight = DEFAULT_WEIGHT
    }

    /* ------------------------------ Snapshot ------------------------------ */
    fun snapshot(): List<Any> = listOf(
        theme, dayTheme,
        fontSize, letterSpacing, lineFactor, paragraphSpacing,
        sidePadding, indent, fontKey, weight,
        turnMode, lastPagedMode, clickAnimation, fullNext, volumeTurn,
        keepScreen, showTimeBattery, immersive, lockPortrait,
        brightness, warmth, backdrop, backdropImageVersion,
    )

    /* -------------------------------- Save -------------------------------- */
    fun save() {
        prefs.edit()
            .putBoolean(KEY_COMFORT_MIGRATED, true)
            .putString(KEY_THEME, theme)
            .putString(KEY_DAY_THEME, dayTheme)
            .putFloat(KEY_FONT_SIZE, fontSize)
            .putFloat(KEY_LETTER_SPACING, letterSpacing)
            .putFloat(KEY_LINE_FACTOR, lineFactor)
            .putFloat(KEY_PARAGRAPH_SPACING, paragraphSpacing)
            .putFloat(KEY_SIDE_PADDING, sidePadding)
            .putBoolean(KEY_INDENT, indent)
            .putString(KEY_FONT, fontKey)
            .putInt(KEY_FONT_WEIGHT, weight)
            .putString(KEY_PAGE_MODE, turnMode.key)
            .putString(KEY_LAST_PAGED_MODE, lastPagedMode.key)
            .putBoolean(KEY_CLICK_ANIMATION, clickAnimation)
            .putBoolean(KEY_FULL_NEXT, fullNext)
            .putBoolean(KEY_VOLUME_TURN, volumeTurn)
            .putBoolean(KEY_KEEP_SCREEN, keepScreen)
            .putBoolean(KEY_TIME_BATTERY, showTimeBattery)
            .putBoolean(KEY_IMMERSIVE, immersive)
            .putBoolean(KEY_LOCK_PORTRAIT, lockPortrait)
            .putFloat(KEY_BRIGHTNESS, brightness)
            .putFloat(KEY_WARMTH, warmth)
            .putString(KEY_BACKDROP, backdrop)
            .putLong(KEY_BACKDROP_IMAGE_VERSION, backdropImageVersion)
            .putString(KEY_PRESET, "custom")
            .apply()
    }

    companion object {
        const val DEFAULT_FONT = 18f
        const val DEFAULT_LINE = 1.95f
        const val DEFAULT_PARAGRAPH = 10f
        const val DEFAULT_SIDE = 22f
        const val DEFAULT_LETTER_SPACING = 0f
        const val DEFAULT_WEIGHT = 500
        const val MIN_FONT = 12f
        const val MAX_FONT = 34f
        const val MIN_LINE = 1.20f
        const val MAX_LINE = 2.40f
        const val MIN_PARAGRAPH = 0f
        const val MAX_PARAGRAPH = 28f
        const val MIN_SIDE = 8f
        const val MAX_SIDE = 40f
        const val MIN_LETTER_SPACING = 0f
        const val MAX_LETTER_SPACING = 0.20f
        const val DEFAULT_THEME = "paper"
        const val NIGHT_THEME = "night"
        const val DEFAULT_FONT_KEY = "sans"
        val SUPPORTED_FONT_KEYS = setOf("sans", "serif", "mono")

        private const val KEY_COMFORT_MIGRATED = "reader_comfort_v26"
        private const val KEY_THEME = "theme"
        private const val KEY_DAY_THEME = "dayTheme"
        private const val KEY_FONT_SIZE = "font"
        private const val KEY_LETTER_SPACING = "letterSpacing"
        private const val KEY_LINE_FACTOR = "line"
        private const val KEY_PARAGRAPH_SPACING = "paragraph"
        private const val KEY_SIDE_PADDING = "sidePadding"
        private const val KEY_INDENT = "indent"
        private const val KEY_FONT = "fontKey"
        private const val KEY_FONT_WEIGHT = "fontWeight"
        private const val KEY_PAGE_MODE = "pageMode"
        private const val KEY_LAST_PAGED_MODE = "lastPagedMode"
        private const val KEY_CLICK_ANIMATION = "clickAnimation"
        private const val KEY_FULL_NEXT = "fullNext"
        private const val KEY_VOLUME_TURN = "volumeTurn"
        private const val KEY_KEEP_SCREEN = "keepScreen"
        private const val KEY_TIME_BATTERY = "timeBattery"
        private const val KEY_IMMERSIVE = "immersive"
        private const val KEY_LOCK_PORTRAIT = "lockPortrait"
        private const val KEY_PRESET = "preset"
        /** Also read by ReaderWindowSessionV27, which applies the window override. */
        const val KEY_BRIGHTNESS = "brightnessV92"
        private const val KEY_WARMTH = "warmthV92"
        private const val KEY_BACKDROP = "backdropV92"
        private const val KEY_BACKDROP_IMAGE_VERSION = "backdropImageV92"
    }
}
