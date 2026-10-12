package com.xiguli.langhuan.data.shelf

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** Pure formatting for the 图书详情 info rows; unit tested. */
object BookFacts {
    /** Counts readable characters: whitespace is not a word in Chinese web-novel counting. */
    fun wordCount(text: CharSequence): Long {
        var count = 0L
        for (ch in text) if (!ch.isWhitespace()) count++
        return count
    }

    /** 「644.6万字」 style, as on the reference reader; below ten thousand the exact figure. */
    fun formatWords(words: Long): String = when {
        words <= 0L -> "0字"
        words < 10_000L -> "${words}字"
        words < 100_000_000L -> "${oneDecimal(words / 10_000.0)}万字"
        else -> "${oneDecimal(words / 100_000_000.0)}亿字"
    }

    /** 「13.1 M」 / 「512 K」 / 「800 B」. */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "未知"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${(bytes / 1024.0).roundToLong()} K"
        bytes < 1024L * 1024L * 1024L -> "${oneDecimal(bytes / (1024.0 * 1024.0))} M"
        else -> "${oneDecimal(bytes / (1024.0 * 1024.0 * 1024.0))} G"
    }

    /** Whole-book percent with one decimal, e.g. 「0.7」. */
    fun formatPercent(fraction: Float?): String = oneDecimal(((fraction ?: 0f).coerceIn(0f, 1f) * 100.0))

    fun formatTime(millis: Long): String =
        if (millis <= 0L) "—" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))

    /** Upper-cased extension of the original file name, falling back to the recorded format. */
    fun formatLabel(fileName: String, recorded: String): String {
        val fromName = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 5 && fileName.contains('.') }
        return (recorded.ifBlank { fromName.orEmpty() }).uppercase(Locale.US).ifBlank { "TXT" }
    }

    private fun oneDecimal(value: Double): String {
        val rounded = (value * 10.0).roundToLong() / 10.0
        return if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}.0" else rounded.toString()
    }
}
