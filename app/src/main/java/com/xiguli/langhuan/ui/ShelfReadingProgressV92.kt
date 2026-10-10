package com.xiguli.langhuan.ui

import android.content.SharedPreferences

/**
 * V92 · whole-book reading progress for the shelf.
 *
 * The V30 reader already persists, next to the sentence anchor in `reader_progress_v2`,
 * how many chapters the book had (`total_`), which chapter index was open (`index_`) and the
 * position inside that chapter (`fraction_`). Since V92 it also records whether the saved
 * position was the last page of the last chapter (`finished_`). The shelf only reads these
 * values; nothing here touches the reader's own progress keys.
 */
internal data class ShelfReadingProgressV92(
    /** The reader has saved a position for this book at least once. */
    val started: Boolean = false,
    /** Whole-book position 0..1, or null when the chapter count is not known yet. */
    val fraction: Float? = null,
    /** The last saved position was the final page of the final chapter. */
    val finished: Boolean = false,
) {
    /** Sort key for "阅读进度": finished books first, then by position; unread last. */
    val sortKey: Float
        get() = when {
            finished -> 2f
            fraction != null -> fraction.coerceIn(0f, 1f)
            started -> 0f
            else -> -1f
        }

    val percent: Int? get() = fraction?.let { (it.coerceIn(0f, 1f) * 100f).toInt() }

    companion object {
        val UNREAD = ShelfReadingProgressV92()
    }
}

internal object ShelfReadingProgressStoreV92 {
    const val PREFS = "reader_progress_v2"

    fun finishedKey(bookId: String) = "finished_$bookId"

    fun load(prefs: SharedPreferences, bookId: String): ShelfReadingProgressV92 =
        shelfReadingProgressV92(
            updatedAt = prefs.getLong("updated_$bookId", 0L),
            chapterIndex = prefs.getInt("index_$bookId", -1),
            chapterCount = prefs.getInt("total_$bookId", 0),
            chapterFraction = prefs.getFloat("fraction_$bookId", 0f),
            atEnd = prefs.getBoolean(finishedKey(bookId), false),
        )
}

/** Pure mapping from the persisted reader values to shelf progress; unit tested. */
internal fun shelfReadingProgressV92(
    updatedAt: Long,
    chapterIndex: Int,
    chapterCount: Int,
    chapterFraction: Float,
    atEnd: Boolean,
): ShelfReadingProgressV92 {
    if (updatedAt <= 0L) return ShelfReadingProgressV92.UNREAD
    if (chapterCount <= 0 || chapterIndex !in 0 until chapterCount) {
        return ShelfReadingProgressV92(started = true, fraction = null, finished = false)
    }
    val inside = chapterFraction.coerceIn(0f, 1f)
    val finished = atEnd && chapterIndex == chapterCount - 1
    val raw = if (finished) 1f else (chapterIndex + inside) / chapterCount.toFloat()
    return ShelfReadingProgressV92(started = true, fraction = raw.coerceIn(0f, 1f), finished = finished)
}

/**
 * Whether the reader's saved position is the end of the book: the last chapter and its last
 * page. A one-page final chapter counts as finished as soon as it is opened.
 */
internal fun readerPositionIsBookEndV92(
    chapterIndex: Int,
    chapterCount: Int,
    pageIndex: Int,
    pageCount: Int,
): Boolean = chapterCount > 0 && chapterIndex == chapterCount - 1 &&
    pageCount > 0 && pageIndex >= pageCount - 1
