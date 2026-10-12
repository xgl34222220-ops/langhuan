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
        string(KEY_FONT, DEFAULT_FONT_KEY).takeIf { isUsableFontKey(it) }
            ?: DEFAULT_FONT_KEY,
    )
    var weight by mutableIntStateOf(readWeight())

    /* --------------------------- Reader Theme --------------------------- */
    /** V101 custom themes (published to [ReaderThemeRegistryV101] for the page painters). */
    var customThemes by mutableStateOf(
        ReaderThemeCatalogV101.decodeCustom(string(KEY_CUSTOM_THEMES, "")),
    )
        private set

    init {
        ReaderThemeRegistryV101.custom = customThemes
    }

    private val legacyTheme = readerThemeKeyV30(string(KEY_THEME, DEFAULT_THEME))

    /** Selected theme of the day list / the night list. */
    var dayTheme by mutableStateOf(
        readerThemeKeyV30(string(KEY_DAY_THEME, legacyTheme.takeIf { !isDark(it) } ?: DEFAULT_THEME))
            .takeIf { !isDark(it) } ?: DEFAULT_THEME,
    )
        private set
    var nightTheme by mutableStateOf(
        readerThemeKeyV30(string(KEY_NIGHT_THEME, NIGHT_THEME)).takeIf { isDark(it) } ?: NIGHT_THEME,
    )
        private set
    var nightMode by mutableStateOf(bool(KEY_NIGHT_MODE, isDark(legacyTheme)))

    /** The day and night lists shown on the 主题 pages (built-ins, 更多主题 extras, custom). */
    var dayInstalled by mutableStateOf(installed(KEY_DAY_INSTALLED, dark = false))
        private set
    var nightInstalled by mutableStateOf(installed(KEY_NIGHT_INSTALLED, dark = true))
        private set

    init {
        // A theme chosen by an older version (e.g. 纸白) stays visible in its list.
        if (dayTheme !in dayInstalled) dayInstalled = dayInstalled + dayTheme
        if (nightTheme !in nightInstalled) nightInstalled = nightInstalled + nightTheme
    }

    /** 背景跟随: day/night follows the system dark mode instead of the night icon. */
    var followSystemNight by mutableStateOf(bool(KEY_FOLLOW_SYSTEM, false))

    /** Active theme key. */
    val theme: String get() = if (nightMode) nightTheme else dayTheme
    val night: Boolean get() = nightMode

    private fun isDark(key: String): Boolean =
        ReaderThemeCatalogV101.resolve(key, customThemes)?.dark ?: (key == NIGHT_THEME)

    private fun installed(key: String, dark: Boolean): List<String> {
        val raw = string(key, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return ReaderThemeCatalogV101.sanitizeInstalled(raw, dark, customThemes)
    }

    fun toggleNight() {
        followSystemNight = false
        nightMode = !nightMode
    }

    /** Applies the system dark mode while 背景跟随 is on. */
    fun applySystemNight(systemDark: Boolean) {
        if (followSystemNight && nightMode != systemDark) nightMode = systemDark
    }

    /** Selects a theme in its own (day or night) list and shows it. */
    fun selectTheme(key: String) {
        val normalized = readerThemeKeyV30(key)
        if (isDark(normalized)) {
            nightTheme = normalized
            if (normalized !in nightInstalled) nightInstalled = nightInstalled + normalized
            nightMode = true
        } else {
            dayTheme = normalized
            if (normalized !in dayInstalled) dayInstalled = dayInstalled + normalized
            nightMode = false
        }
    }

    /** 更多主题: adds a bundled extra to its list (no-op when already there). */
    fun installTheme(key: String) {
        val spec = ReaderThemeCatalogV101.resolve(key, customThemes) ?: return
        if (spec.dark) {
            if (key !in nightInstalled) nightInstalled = nightInstalled + key
        } else if (key !in dayInstalled) {
            dayInstalled = dayInstalled + key
        }
    }

    /** Removes a theme from its list; custom themes are deleted. The last theme always stays. */
    fun removeTheme(key: String): Boolean {
        val spec = ReaderThemeCatalogV101.resolve(key, customThemes) ?: return false
        val list = if (spec.dark) nightInstalled else dayInstalled
        if (key !in list || list.size <= 1) return false
        val next = list - key
        if (spec.dark) {
            nightInstalled = next
            if (nightTheme == key) nightTheme = next.first()
        } else {
            dayInstalled = next
            if (dayTheme == key) dayTheme = next.first()
        }
        if (spec.custom) {
            customThemes = customThemes.filterNot { it.key == key }
            ReaderThemeRegistryV101.custom = customThemes
        }
        return true
    }

    /** 新建主题: stores a custom theme, installs and selects it. */
    fun addCustomTheme(spec: ReaderThemeSpecV101): Boolean {
        if (!spec.custom || !spec.key.startsWith(ReaderThemeCatalogV101.CUSTOM_PREFIX)) return false
        if (customThemes.size >= ReaderThemeCatalogV101.MAX_CUSTOM) return false
        customThemes = customThemes.filterNot { it.key == spec.key } + spec
        ReaderThemeRegistryV101.custom = customThemes
        selectTheme(spec.key)
        return true
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
    /** V101: system status bar / navigation bar while reading (沉浸式 hides both). */
    var showStatusBar by mutableStateOf(bool(KEY_STATUS_BAR, true))
    var showNavigationBar by mutableStateOf(bool(KEY_NAVIGATION_BAR, true))
    /** V101 下拉书签: pulling the page down toggles the chapter bookmark. */
    var pullBookmark by mutableStateOf(bool(KEY_PULL_BOOKMARK, true))

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
        dayTheme, nightTheme, nightMode, dayInstalled, nightInstalled, customThemes, followSystemNight,
        showStatusBar, showNavigationBar, pullBookmark,
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
            .putString(KEY_NIGHT_THEME, nightTheme)
            .putBoolean(KEY_NIGHT_MODE, nightMode)
            .putString(KEY_DAY_INSTALLED, dayInstalled.joinToString(","))
            .putString(KEY_NIGHT_INSTALLED, nightInstalled.joinToString(","))
            .putString(KEY_CUSTOM_THEMES, ReaderThemeCatalogV101.encodeCustom(customThemes))
            .putBoolean(KEY_FOLLOW_SYSTEM, followSystemNight)
            .putBoolean(KEY_STATUS_BAR, showStatusBar)
            .putBoolean(KEY_NAVIGATION_BAR, showNavigationBar)
            .putBoolean(KEY_PULL_BOOKMARK, pullBookmark)
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
        const val DEFAULT_THEME = ReaderThemeCatalogV101.DEFAULT_DAY
        const val NIGHT_THEME = "night"
        const val DEFAULT_FONT_KEY = "sans"
        val SUPPORTED_FONT_KEYS = setOf("sans", "serif", "mono")
        /** V101 imported font: `custom:` + absolute path of a validated TTF/OTF in app storage. */
        const val CUSTOM_FONT_PREFIX = "custom:"
        fun isUsableFontKey(key: String): Boolean = key in SUPPORTED_FONT_KEYS ||
            (key.startsWith(CUSTOM_FONT_PREFIX) && runCatching { java.io.File(key.removePrefix(CUSTOM_FONT_PREFIX)).isFile }.getOrDefault(false))

        private const val KEY_COMFORT_MIGRATED = "reader_comfort_v26"
        private const val KEY_THEME = "theme"
        private const val KEY_DAY_THEME = "dayTheme"
        const val KEY_NIGHT_THEME = "nightThemeV101"
        /** Also read by ReaderWindowSessionV27 (light / dark system bar icons). */
        const val KEY_NIGHT_MODE = "nightModeV101"
        private const val KEY_DAY_INSTALLED = "dayThemesV101"
        private const val KEY_NIGHT_INSTALLED = "nightThemesV101"
        private const val KEY_CUSTOM_THEMES = "customThemesV101"
        private const val KEY_FOLLOW_SYSTEM = "followSystemNightV101"
        const val KEY_STATUS_BAR = "statusBarV101"
        const val KEY_NAVIGATION_BAR = "navigationBarV101"
        private const val KEY_PULL_BOOKMARK = "pullBookmarkV101"
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
