package com.xiguli.langhuan.ui.reader

import android.content.SharedPreferences
import kotlin.math.abs

/**
 * One-time density migration for the exact V13 built-in/default typography values.
 * User-customized values are left untouched.
 */
internal fun migrateReaderTypographyV14(prefs: SharedPreferences, bookId: String) {
    val versionKey = "typography_version_$bookId"
    if (prefs.getInt(versionKey, 0) >= 14) return

    data class Legacy(
        val font: Float,
        val line: Float,
        val padding: Float,
        val paragraph: Float,
        val newLine: Float,
        val newParagraph: Float,
    )

    val base = prefs.getString("preset_base_$bookId", "").orEmpty()
    val legacy = when (base) {
        "builtin-langhuan" -> Legacy(20f, 1.86f, 24f, 13f, 1.72f, 8f)
        "builtin-tomato" -> Legacy(21f, 1.82f, 21f, 12f, 1.70f, 8f)
        "builtin-weread" -> Legacy(19f, 1.76f, 27f, 14f, 1.68f, 9f)
        "builtin-qidian" -> Legacy(19f, 1.66f, 19f, 9f, 1.62f, 7f)
        "" -> Legacy(20f, 1.82f, 24f, 12f, 1.68f, 8f)
        else -> null
    }

    val edit = prefs.edit().putInt(versionKey, 14)
    if (legacy != null) {
        val font = prefs.getFloat("font_$bookId", 20f)
        val line = prefs.getFloat("line_$bookId", 1.82f)
        val padding = prefs.getFloat("padding_$bookId", 24f)
        val paragraph = prefs.getFloat("paragraph_$bookId", 12f)
        val exactLegacy = abs(font - legacy.font) < .01f &&
            abs(line - legacy.line) < .01f &&
            abs(padding - legacy.padding) < .01f &&
            abs(paragraph - legacy.paragraph) < .01f
        if (exactLegacy) {
            edit.putFloat("line_$bookId", legacy.newLine)
            edit.putFloat("paragraph_$bookId", legacy.newParagraph)
        }
    }
    edit.commit()
}
