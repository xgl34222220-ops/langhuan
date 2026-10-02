package com.xiguli.langhuan.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Reader V50 · 每日阅读目标。
 *
 * 数据关系：
 *
 * ReaderStatsV35
 * └─ 继续负责真实阅读行为数据
 *    - day_yyyyMMdd
 *    - total
 *    - pages
 *    - chapters
 *
 * ReaderDailyGoalV50
 * └─ 只负责用户目标配置与目标统计
 */

/* -------------------------------------------------------------------------- */
/*                                  Model                                     */
/* -------------------------------------------------------------------------- */

@Immutable
internal data class ReaderDailyGoalDayV50(
    val key: String,
    val label: String,
    val seconds: Long,
    val reached: Boolean,
)

@Immutable
internal data class ReaderDailyGoalSnapshotV50(
    /** 每日目标分钟数。 */
    val goalMinutes: Int,
    /** 每日目标秒数。 */
    val goalSeconds: Long,
    /** 今天阅读秒数。 */
    val todaySeconds: Long,
    /** 今天完成比例。可以大于 1。UI 自己决定是否 clamp。 */
    val todayProgress: Float,
    /** 今天是否已经达标。 */
    val todayReached: Boolean,
    /** 最近七天。 */
    val week: List<ReaderDailyGoalDayV50>,
    /** 最近七天内达到目标的天数。 */
    val weekReachedDays: Int,
    /** ReaderStats 历史数据中所有达到当前目标的日期数量。 */
    val totalReachedDays: Int,
    /**
     * 原 ReaderStats 连续阅读天数。
     * 注意：这是"每天至少阅读 1 分钟"的 streak，不等同于目标达标 streak。
     */
    val readingStreak: Int,
    val totalSeconds: Long,
    val pages: Long,
    val chapters: Long,
)

/* -------------------------------------------------------------------------- */
/*                                  Store                                     */
/* -------------------------------------------------------------------------- */

internal object ReaderDailyGoalStoreV50 {

    private const val PREFS = "reader_daily_goal_v50"
    private const val KEY_GOAL_MINUTES = "goal_minutes"

    /**
     * ReaderStatsV35 当前固定的统计文件名。
     * Goal 层不写入它，只读现有 day_yyyyMMdd 数据。
     */
    private const val READER_STATS_PREFS = "reader_stats_v35"

    const val DEFAULT_GOAL_MINUTES = 30
    const val MIN_GOAL_MINUTES = 5
    const val MAX_GOAL_MINUTES = 240

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun statsPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(READER_STATS_PREFS, Context.MODE_PRIVATE)

    fun loadGoalMinutes(context: Context): Int =
        runCatching {
            prefs(context).getInt(KEY_GOAL_MINUTES, DEFAULT_GOAL_MINUTES)
        }.getOrDefault(DEFAULT_GOAL_MINUTES).coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES)

    fun saveGoalMinutes(context: Context, minutes: Int): Result<Int> = runCatching {
        require(minutes in MIN_GOAL_MINUTES..MAX_GOAL_MINUTES) {
            "每日阅读目标需在 $MIN_GOAL_MINUTES–$MAX_GOAL_MINUTES 分钟之间"
        }
        val committed = prefs(context).edit().putInt(KEY_GOAL_MINUTES, minutes).commit()
        check(committed) { "每日阅读目标保存失败" }
        minutes
    }

    fun snapshot(context: Context): ReaderDailyGoalSnapshotV50 {
        val goalMinutes = loadGoalMinutes(context)
        val goalSeconds = goalMinutes * 60L
        val readerStats = ReaderStatsV35.snapshot(context)
        val statsPrefs = statsPrefs(context)
        val week = buildWeekV50(prefs = statsPrefs, goalSeconds = goalSeconds)
        val totalReached = countHistoricalReachedDaysV50(prefs = statsPrefs, goalSeconds = goalSeconds)
        return ReaderDailyGoalSnapshotV50(
            goalMinutes = goalMinutes,
            goalSeconds = goalSeconds,
            todaySeconds = readerStats.today,
            todayProgress = if (goalSeconds <= 0L) 0f else readerStats.today.toFloat() / goalSeconds.toFloat(),
            todayReached = readerStats.today >= goalSeconds,
            week = week,
            weekReachedDays = week.count { it.reached },
            totalReachedDays = totalReached,
            readingStreak = readerStats.streak,
            totalSeconds = readerStats.total,
            pages = readerStats.pages,
            chapters = readerStats.chapters,
        )
    }

    /**
     * 最近 7 天，顺序：六天前 → 今天。
     */
    private fun buildWeekV50(
        prefs: SharedPreferences,
        goalSeconds: Long,
    ): List<ReaderDailyGoalDayV50> {
        val labels = listOf("日", "一", "二", "三", "四", "五", "六")
        return (6 downTo 0).map { back ->
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
            val key = dayKeyV50(calendar.time)
            val seconds = prefs.getLong("day_$key", 0L)
            ReaderDailyGoalDayV50(
                key = key,
                label = if (back == 0) "今" else labels[calendar.get(Calendar.DAY_OF_WEEK) - 1],
                seconds = seconds,
                reached = seconds >= goalSeconds,
            )
        }
    }

    /**
     * 统计当前 ReaderStats 文件中所有历史 day_yyyyMMdd。
     * 目标改变后，"累计达标天数"会按新目标重新计算。
     */
    private fun countHistoricalReachedDaysV50(
        prefs: SharedPreferences,
        goalSeconds: Long,
    ): Int =
        prefs.all.asSequence()
            .filter { (key, _) -> key.startsWith("day_") && key.length == 12 }
            .mapNotNull { (_, value) ->
                when (value) {
                    is Long -> value
                    is Int -> value.toLong()
                    else -> null
                }
            }
            .count { it >= goalSeconds }

    private fun dayKeyV50(date: Date): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
}

/* -------------------------------------------------------------------------- */
/*                              Goal Options                                  */
/* -------------------------------------------------------------------------- */

internal val ReaderDailyGoalPresetsV50: List<Int> = listOf(10, 20, 30, 45, 60)

/* -------------------------------------------------------------------------- */
/*                              Main Panel                                    */
/* -------------------------------------------------------------------------- */

@Composable
internal fun ReaderDailyGoalPanelV50(
    modifier: Modifier = Modifier,
    onGoalChanged: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current

    var goalMinutes by remember {
        mutableIntStateOf(ReaderDailyGoalStoreV50.loadGoalMinutes(context))
    }

    /*
     * goalMinutes 变化就重新读取 ReaderStats，
     * 因为达标天数和周线都会随目标变化。
     */
    val snapshot = remember(goalMinutes) {
        ReaderDailyGoalStoreV50.snapshot(context)
    }

    Column(modifier = modifier.fillMaxWidth()) {

        /* ------------------------- Header ----------------------------------- */

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = t.accent, shape = RoundedCornerShape(t.radiusMd))
                    .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.TrackChanges,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = t.accentForeground,
                )
            }

            Spacer(Modifier.width(t.space3))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "每日阅读目标",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = "每天 ${snapshot.goalMinutes} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                )
            }
        }

        Spacer(Modifier.height(t.space4))

        /* ------------------------- Today ------------------------------------ */

        ReaderDailyGoalTodayCardV50(snapshot = snapshot)

        Spacer(Modifier.height(t.space4))

        /* ------------------------- Presets ---------------------------------- */

        Text(
            text = "目标时长",
            style = MaterialTheme.typography.labelLarge,
            color = t.secondaryForeground,
        )
        Spacer(Modifier.height(t.space2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            ReaderDailyGoalPresetsV50.forEach { minutes ->
                ReaderDailyGoalPresetV50(
                    minutes = minutes,
                    selected = minutes == goalMinutes,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        ReaderDailyGoalStoreV50.saveGoalMinutes(context = context, minutes = minutes)
                            .onSuccess {
                                goalMinutes = it
                                onGoalChanged(it)
                            }
                    },
                )
            }
        }

        Spacer(Modifier.height(t.space5))

        /* ------------------------- Week ------------------------------------- */

        ReaderDailyGoalWeekCardV50(snapshot = snapshot)

        Spacer(Modifier.height(t.space4))

        /* ------------------------- More Stats ------------------------------- */

        ReaderDailyGoalStatsV50(snapshot = snapshot)
    }
}

/* -------------------------------------------------------------------------- */
/*                              Today Card                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalTodayCardV50(snapshot: ReaderDailyGoalSnapshotV50) {
    val t = LocalLanghuanUiTokens.current
    val reached = snapshot.todayReached
    val shape = RoundedCornerShape(t.radiusLg)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = if (reached) t.goldContainer else t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今日阅读",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = ReaderStatsV35.format(snapshot.todaySeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (reached) {
                Row(
                    modifier = Modifier
                        .background(color = t.gold, shape = CircleShape)
                        .padding(horizontal = t.space2, vertical = t.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = t.card,
                    )
                    Spacer(Modifier.width(t.space1))
                    Text(
                        text = "今日达标",
                        style = MaterialTheme.typography.labelMedium,
                        color = t.card,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(t.space4))

        ReaderDailyGoalProgressV50(progress = snapshot.todayProgress, reached = reached)

        Spacer(Modifier.height(t.space2))

        val remaining = (snapshot.goalSeconds - snapshot.todaySeconds).coerceAtLeast(0L)

        Text(
            text = if (reached) {
                "已完成今天的阅读目标"
            } else {
                "再读 ${ReaderStatsV35.format(remaining)} 即可达标"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (reached) t.goldForeground else t.mutedForeground,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Progress                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalProgressV50(progress: Float, reached: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val animated = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        animated.animateTo(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(color = t.border, shape = CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.value)
                .height(6.dp)
                .background(
                    color = if (reached) t.gold else t.primary,
                    shape = CircleShape,
                ),
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Preset                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalPresetV50(
    minutes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(t.radiusMd)

    Box(
        modifier = modifier
            .height(42.dp)
            .background(
                color = if (selected) t.accent else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = minutes.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Week Card                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalWeekCardV50(snapshot: ReaderDailyGoalSnapshotV50) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近 7 天",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = "本周 ${snapshot.weekReachedDays} / 7 天达标",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                )
            }

            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = if (snapshot.weekReachedDays > 0) t.gold else t.mutedForeground,
            )
        }

        Spacer(Modifier.height(t.space4))

        ReaderDailyGoalWeekChartV50(
            days = snapshot.week,
            goalSeconds = snapshot.goalSeconds,
        )

        Spacer(Modifier.height(t.space2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(color = t.gold, shape = CircleShape),
            )
            Spacer(Modifier.width(t.space2))
            Text(
                text = "目标线 · ${snapshot.goalMinutes} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                               Week Chart                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalWeekChartV50(
    days: List<ReaderDailyGoalDayV50>,
    goalSeconds: Long,
) {
    val t = LocalLanghuanUiTokens.current
    val grow = remember { Animatable(0f) }

    LaunchedEffect(days, goalSeconds) {
        grow.snapTo(0f)
        grow.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
    }

    /*
     * 图表纵轴上限至少是目标值，
     * 如果某天读得更多，则自动扩大。
     * 再加 12% 顶部空间，避免柱子顶死。
     */
    val maximum = max(
        goalSeconds,
        days.maxOfOrNull { it.seconds } ?: 0L,
    ).coerceAtLeast(60L)

    val chartMaximum = maximum * 1.12f

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier.fillMaxWidth().height(142.dp),
        ) {
            drawReaderDailyGoalChartV50(
                days = days,
                goalSeconds = goalSeconds,
                chartMaximum = chartMaximum,
                animation = grow.value,
                primary = t.primary,
                gold = t.gold,
                border = t.border,
                muted = t.mutedForeground,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    text = day.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.label == "今") t.foreground else t.mutedForeground,
                    textAlign = TextAlign.Center,
                    fontWeight = if (day.label == "今") FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Canvas 只负责图形，不创建任何新色值。
 */
private fun DrawScope.drawReaderDailyGoalChartV50(
    days: List<ReaderDailyGoalDayV50>,
    goalSeconds: Long,
    chartMaximum: Float,
    animation: Float,
    primary: Color,
    gold: Color,
    border: Color,
    muted: Color,
) {
    if (days.isEmpty() || chartMaximum <= 0f) return

    val widthPerDay = size.width / days.size.toFloat()
    val barWidth = widthPerDay * 0.46f
    val bottom = size.height - 4f * density
    val usableHeight = size.height - 10f * density

    /* 三条极弱辅助水平线。 */
    listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
        val y = bottom - usableHeight * fraction
        drawLine(
            color = border.copy(alpha = 0.55f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f * density,
        )
    }

    /* ---------------- Goal Line ---------------- */

    val goalFraction = (goalSeconds.toFloat() / chartMaximum).coerceIn(0f, 1f)
    val goalY = bottom - usableHeight * goalFraction

    drawLine(
        color = gold.copy(alpha = 0.88f),
        start = Offset(0f, goalY),
        end = Offset(size.width, goalY),
        strokeWidth = 1.5f * density,
        cap = StrokeCap.Round,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            intervals = floatArrayOf(6f * density, 5f * density),
        ),
    )

    /* ---------------- Bars ---------------- */

    days.forEachIndexed { index, day ->
        val fraction = (day.seconds.toFloat() / chartMaximum).coerceIn(0f, 1f) * animation
        val barHeight = usableHeight * fraction
        val centerX = widthPerDay * index + widthPerDay / 2f
        val left = centerX - barWidth / 2f
        val top = bottom - barHeight

        drawRoundRect(
            color = when {
                day.reached -> gold
                day.label == "今" -> primary
                else -> primary.copy(alpha = 0.38f)
            },
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(
                width = barWidth,
                height = max(barHeight, 2f * density),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                x = 5f * density,
                y = 5f * density,
            ),
        )

        /* 达标点。 */
        if (day.reached && animation > 0.92f) {
            drawCircle(
                color = gold,
                radius = 2.5f * density,
                center = Offset(
                    centerX,
                    (goalY - 5f * density).coerceAtLeast(3f * density),
                ),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                              Stats                                         */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalStatsV50(snapshot: ReaderDailyGoalSnapshotV50) {
    val t = LocalLanghuanUiTokens.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "阅读统计",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
        )
        Spacer(Modifier.height(t.space3))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            ReaderDailyGoalStatCellV50(
                icon = Icons.Rounded.Check,
                value = "${snapshot.totalReachedDays}",
                label = "达标天数",
                gold = true,
                modifier = Modifier.weight(1f),
            )
            ReaderDailyGoalStatCellV50(
                icon = Icons.Rounded.LocalFireDepartment,
                value = "${snapshot.readingStreak}",
                label = "连续阅读",
                gold = snapshot.readingStreak > 0,
                modifier = Modifier.weight(1f),
            )
            ReaderDailyGoalStatCellV50(
                icon = Icons.Rounded.Schedule,
                value = compactReadingTimeV50(snapshot.totalSeconds),
                label = "累计阅读",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(t.space2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            ReaderDailyGoalStatCellV50(
                value = snapshot.pages.toString(),
                label = "累计翻页",
                modifier = Modifier.weight(1f),
            )
            ReaderDailyGoalStatCellV50(
                value = snapshot.chapters.toString(),
                label = "读完章节",
                modifier = Modifier.weight(1f),
            )
            ReaderDailyGoalStatCellV50(
                value = "${snapshot.weekReachedDays}/7",
                label = "本周达标",
                gold = snapshot.weekReachedDays > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                             Stat Cell                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDailyGoalStatCellV50(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gold: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Column(
        modifier = modifier
            .background(
                color = if (gold) t.goldContainer else t.card,
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space3),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (gold) t.gold else t.primary,
            )
            Spacer(Modifier.height(t.space2))
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (gold) t.goldForeground else t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Helpers                                       */
/* -------------------------------------------------------------------------- */

private fun compactReadingTimeV50(seconds: Long): String = when {
    seconds < 60L -> "${seconds}秒"
    seconds < 3_600L -> "${seconds / 60L}分"
    seconds < 36_000L -> {
        val hours = seconds / 3_600L
        val minutes = (seconds % 3_600L) / 60L
        if (minutes > 0L) "${hours}h${minutes}m" else "${hours}h"
    }
    else -> "${seconds / 3_600L}h"
}
