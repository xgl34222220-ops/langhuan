package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Reader preferences. Stored in the same `reader_qingmo_v9` file and keys as before, so existing
 * typography, theme and window options (read by [ReaderWindowSessionV27]) carry over.
 */
@Stable
internal class ReaderSettingsV30(private val prefs: SharedPreferences) {
    private fun float(key: String, fallback: Float): Float =
        runCatching { prefs.getFloat(key, fallback) }.getOrNull()
            ?: runCatching { prefs.getInt(key, fallback.toInt()).toFloat() }.getOrNull()
            ?: fallback

    private fun bool(key: String, fallback: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, fallback) }.getOrDefault(fallback)

    private fun string(key: String, fallback: String): String =
        runCatching { prefs.getString(key, fallback) }.getOrNull() ?: fallback

    private fun weightPref(): Int = runCatching { prefs.getInt("fontWeight", 500) }.getOrNull()
        ?: when (string("fontWeight", "")) {
            "light", "thin" -> 400
            "medium" -> 500
            "semibold" -> 600
            "bold", "heavy" -> 700
            else -> 500
        }

    private val legacyDefault = !bool("reader_comfort_v26", false) &&
        readerUsesLegacyDefaultV26(
            string("preset", "qingmo"),
            float("font", 18f), float("line", 1.75f),
            float("paragraph", 3f), float("sidePadding", 20f),
        )

    var fontSize by mutableFloatStateOf(if (legacyDefault) DEFAULT_FONT else float("font", DEFAULT_FONT))
    var lineFactor by mutableFloatStateOf(if (legacyDefault) DEFAULT_LINE else float("line", DEFAULT_LINE))
    var paragraphSpacing by mutableFloatStateOf(if (legacyDefault) DEFAULT_PARAGRAPH else float("paragraph", DEFAULT_PARAGRAPH))
    var sidePadding by mutableFloatStateOf(if (legacyDefault) DEFAULT_SIDE else float("sidePadding", DEFAULT_SIDE))
    var letterSpacing by mutableFloatStateOf(float("letterSpacing", 0f))
    var indent by mutableStateOf(bool("indent", true))
    var fontKey by mutableStateOf(string("fontKey", "sans"))
    var weight by mutableIntStateOf(weightPref())
    var theme by mutableStateOf(readerThemeKeyV30(string("theme", "paper")))
    var dayTheme by mutableStateOf(readerThemeKeyV30(string("dayTheme", "paper")))
    var turnMode by mutableStateOf(ReaderTurnModeV30.of(string("pageMode", ReaderTurnModeV30.COVER.key)))
    var lastPagedMode by mutableStateOf(ReaderTurnModeV30.of(string("lastPagedMode", ReaderTurnModeV30.COVER.key)))
    var volumeTurn by mutableStateOf(bool("volumeTurn", false))
    var keepScreen by mutableStateOf(bool("keepScreen", false))
    var showTimeBattery by mutableStateOf(bool("timeBattery", true))
    var immersive by mutableStateOf(bool("immersive", false))
    var clickAnimation by mutableStateOf(bool("clickAnimation", true))
    var fullNext by mutableStateOf(bool("fullNext", false))
    var lockPortrait by mutableStateOf(bool("lockPortrait", true))

    val night: Boolean get() = theme == "night"

    fun toggleNight() {
        if (night) {
            theme = dayTheme.takeIf { it != "night" } ?: "paper"
        } else {
            dayTheme = theme
            theme = "night"
        }
    }

    fun selectTheme(key: String) {
        theme = key
        if (key != "night") dayTheme = key
    }

    fun selectTurnMode(mode: ReaderTurnModeV30) {
        if (mode != ReaderTurnModeV30.SCROLL) lastPagedMode = mode
        turnMode = mode
    }

    fun resetTypography() {
        fontSize = DEFAULT_FONT
        lineFactor = DEFAULT_LINE
        paragraphSpacing = DEFAULT_PARAGRAPH
        sidePadding = DEFAULT_SIDE
        letterSpacing = 0f
        indent = true
        weight = 500
    }

    /** Comparable snapshot; a change triggers exactly one save. */
    fun snapshot(): List<Any> = listOf(
        fontSize, lineFactor, paragraphSpacing, sidePadding, letterSpacing, indent, fontKey, weight, theme, dayTheme,
        turnMode, lastPagedMode, volumeTurn, keepScreen, showTimeBattery, immersive, clickAnimation, fullNext, lockPortrait,
    )

    fun save() {
        prefs.edit()
            .putBoolean("reader_comfort_v26", true)
            .putFloat("font", fontSize)
            .putFloat("line", lineFactor)
            .putFloat("paragraph", paragraphSpacing)
            .putFloat("sidePadding", sidePadding)
            .putFloat("letterSpacing", letterSpacing)
            .putBoolean("indent", indent)
            .putString("fontKey", fontKey)
            .putInt("fontWeight", weight)
            .putString("theme", theme)
            .putString("dayTheme", dayTheme)
            .putString("pageMode", turnMode.key)
            .putString("lastPagedMode", lastPagedMode.key)
            .putString("preset", "custom")
            .putBoolean("volumeTurn", volumeTurn)
            .putBoolean("keepScreen", keepScreen)
            .putBoolean("timeBattery", showTimeBattery)
            .putBoolean("immersive", immersive)
            .putBoolean("clickAnimation", clickAnimation)
            .putBoolean("fullNext", fullNext)
            .putBoolean("lockPortrait", lockPortrait)
            .apply()
    }

    companion object {
        const val DEFAULT_FONT = 20f
        const val DEFAULT_LINE = 1.72f
        const val DEFAULT_PARAGRAPH = 10f
        const val DEFAULT_SIDE = 22f
    }
}
