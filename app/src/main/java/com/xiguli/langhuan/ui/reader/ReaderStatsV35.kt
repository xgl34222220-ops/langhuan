package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Local reading statistics: time per day, total time, pages turned, chapters finished. */
internal object ReaderStatsV35 {
    private const val PREFS = "reader_stats_v35"
    private fun day(date: Date = Date()): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(date)

    fun addSeconds(context: Context, seconds: Long) {
        if (seconds <= 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "day_" + day()
        prefs.edit()
            .putLong(key, prefs.getLong(key, 0L) + seconds)
            .putLong("total", prefs.getLong("total", 0L) + seconds)
            .apply()
    }

    fun addPage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putLong("pages", prefs.getLong("pages", 0L) + 1).apply()
    }

    fun markChapterFinished(context: Context, bookId: String, chapterNumber: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "done_${bookId}_$chapterNumber"
        if (!prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, true).putLong("chapters", prefs.getLong("chapters", 0L) + 1).apply()
        }
    }

    data class Snapshot(val today: Long, val total: Long, val pages: Long, val chapters: Long, val week: List<Pair<String, Long>>, val streak: Int)

    fun snapshot(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val calendar = Calendar.getInstance()
        val labels = listOf("日", "一", "二", "三", "四", "五", "六")
        val week = (6 downTo 0).map { back ->
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
            val label = if (back == 0) "今" else labels[c.get(Calendar.DAY_OF_WEEK) - 1]
            label to prefs.getLong("day_" + day(c.time), 0L)
        }
        // Consecutive days (ending today or yesterday) with at least one minute of reading.
        var streak = 0
        val cursor = calendar.clone() as Calendar
        if (prefs.getLong("day_" + day(cursor.time), 0L) < 60) cursor.add(Calendar.DAY_OF_YEAR, -1)
        while (prefs.getLong("day_" + day(cursor.time), 0L) >= 60 && streak < 3650) {
            streak++
            cursor.add(Calendar.DAY_OF_YEAR, -1)
        }
        return Snapshot(
            today = prefs.getLong("day_" + day(), 0L),
            total = prefs.getLong("total", 0L),
            pages = prefs.getLong("pages", 0L),
            chapters = prefs.getLong("chapters", 0L),
            week = week,
            streak = streak,
        )
    }

    fun format(seconds: Long): String = when {
        seconds < 60 -> "${seconds} 秒"
        seconds < 3600 -> "${seconds / 60} 分钟"
        else -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
    }
}

@Composable
internal fun ReaderStatsPanelV35(theme: ReaderThemeV30, onBack: () -> Unit) {
    val context = LocalContext.current
    val stats = remember { ReaderStatsV35.snapshot(context) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "‹  阅读统计",
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 6.dp, horizontal = 2.dp),
            color = theme.sheetText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            ReaderStatCellV35("今日", ReaderStatsV35.format(stats.today), theme)
            ReaderStatCellV35("连续", "${stats.streak} 天", theme)
            ReaderStatCellV35("累计", ReaderStatsV35.format(stats.total), theme)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            ReaderStatCellV35("翻页", "${stats.pages} 页", theme)
            ReaderStatCellV35("读完", "${stats.chapters} 章", theme)
            Spacer(Modifier.weight(1f))
        }
        Text("最近 7 天", Modifier.padding(top = 18.dp, bottom = 8.dp), color = theme.sheetMuted, fontSize = 12.sp)
        val max = stats.week.maxOf { it.second }.coerceAtLeast(60L)
        Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            stats.week.forEach { (label, seconds) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    // Bars grow from zero when the panel opens.
                    val fraction = ((seconds.toFloat() / max).coerceIn(0f, 1f) * grow.value).coerceAtLeast(.02f)
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(if (label == "今") theme.accent else theme.accent.copy(alpha = .35f)),
                        )
                    }
                    Text(label, Modifier.padding(top = 5.dp), color = theme.sheetMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun RowScope.ReaderStatCellV35(label: String, value: String, theme: ReaderThemeV30) {
    Column(Modifier.weight(1f)) {
        Text(value, color = theme.sheetText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, Modifier.padding(top = 2.dp), color = theme.sheetMuted, fontSize = 12.sp)
    }
}
