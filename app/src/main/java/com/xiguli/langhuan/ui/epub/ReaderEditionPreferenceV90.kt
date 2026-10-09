package com.xiguli.langhuan.ui.epub

import android.content.Context
import android.content.SharedPreferences

/** Which edition of an EPUB book the shelf opens. */
enum class ReaderEditionV90(val key: String) {
    /** The Readium renderer with the publisher's layout and images (原版). */
    ORIGINAL("original"),

    /** Langhuan's own paginated text reader (文字版). */
    TEXT("text"),
    ;

    companion object {
        fun of(key: String?): ReaderEditionV90? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Remembers, per book, whether the reader last chose 原版 or 文字版.
 *
 * Before this store the choice was never written anywhere: the shelf always launched the EPUB
 * Activity, which always starts on 原版, so picking 文字版 lasted only until the book was closed.
 */
object ReaderEditionPreferenceV90 {
    const val PREFS = "reader_edition_v90"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun keyFor(bookId: String) = "edition_$bookId"

    /** The saved choice; books never switched keep opening on 原版 as before. */
    fun load(prefs: SharedPreferences, bookId: String): ReaderEditionV90 =
        ReaderEditionV90.of(prefs.getString(keyFor(bookId), null)) ?: ReaderEditionV90.ORIGINAL

    fun save(prefs: SharedPreferences, bookId: String, edition: ReaderEditionV90): Boolean {
        if (bookId.isBlank()) return false
        return prefs.edit().putString(keyFor(bookId), edition.key).commit()
    }

    fun forget(prefs: SharedPreferences, bookId: String) {
        prefs.edit().remove(keyFor(bookId)).apply()
    }

    /** True when the shelf should open this EPUB in the text reader instead of the original. */
    fun opensAsText(prefs: SharedPreferences, bookId: String): Boolean =
        load(prefs, bookId) == ReaderEditionV90.TEXT
}
