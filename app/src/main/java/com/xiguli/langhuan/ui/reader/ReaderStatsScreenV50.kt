package com.xiguli.langhuan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlin.math.max


/**
 * 阅读统计 · V50
 *
 * 页面本身不读取 ReaderStatsV35 / SharedPreferences。
 *
 * 所有统计数据由上层通过已经存在的
 * ReaderDailyGoalSnapshotV50 传入。
 *
 * 可用真实字段：
 *
 * - goalMinutes
 * - goalSeconds
 * - todaySeconds
 * - todayProgress
 * - todayReached
 * - week
 * - weekReachedDays
 * - totalReachedDays
 * - readingStreak
 * - totalSeconds
 * - pages
 * - chapters
 *
 * 修改每日目标只通过 onGoalChange 回调交上层。
 */
@Composable
internal fun ReaderStatsScreenV50(
    snapshot: ReaderDailyGoalSnapshotV50,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onGoalChange: (Int) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    var goalDialogOpen by
        remember {
            mutableStateOf(
                false,
            )
        }


    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                t.background,
            )
            .statusBarsPadding(),
        contentPadding =
            PaddingValues(
                start =
                    t.space4,
                end =
                    t.space4,
                top =
                    t.space3,
                bottom =
                    t.space6,
            ),
        verticalArrangement =
            Arrangement.spacedBy(
                t.space4,
            ),
    ) {

        /* ------------------------------------------------------------------ */
        /* Header                                                             */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "reader-stats-header",
        ) {
            ReaderStatsHeaderV50(
                onBack =
                    onBack,
            )
        }


        /* ------------------------------------------------------------------ */
        /* Today                                                              */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "reader-stats-today",
        ) {
            ReaderStatsTodayCardV50(
                snapshot =
                    snapshot,
                onGoalClick = {
                    goalDialogOpen =
                        true
                },
            )
        }


        /* ------------------------------------------------------------------ */
        /* Week                                                               */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "reader-stats-week",
        ) {
            ReaderStatsWeekCardV50(
                snapshot =
                    snapshot,
            )
        }


        /* ------------------------------------------------------------------ */
        /* Overview                                                           */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "reader-stats-overview-title",
        ) {
            Text(
                text =
                    "阅读概览",
                style =
                    MaterialTheme.typography.titleLarge,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )
        }


        item(
            key =
                "reader-stats-overview",
        ) {
            ReaderStatsOverviewV50(
                snapshot =
                    snapshot,
            )
        }


        /* ------------------------------------------------------------------ */
        /* Goal                                                              */
        /* ------------------------------------------------------------------ */

        item(
            key =
                "reader-stats-goal",
        ) {
            ReaderStatsGoalCardV50(
                snapshot =
                    snapshot,
                onClick = {
                    goalDialogOpen =
                        true
                },
            )
        }
    }


    if (
        goalDialogOpen
    ) {
        ReaderStatsGoalDialogV50(
            current =
                snapshot.goalMinutes,
            onDismiss = {
                goalDialogOpen =
                    false
            },
            onSelect = {
                minutes ->

                goalDialogOpen =
                    false

                onGoalChange(
                    minutes,
                )
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsHeaderV50(
    onBack: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        val shape =
            RoundedCornerShape(
                t.radiusMd,
            )

        Box(
            modifier = Modifier
                .size(
                    40.dp,
                )
                .background(
                    color =
                        t.card,
                    shape =
                        shape,
                )
                .border(
                    width =
                        1.dp,
                    color =
                        t.border,
                    shape =
                        shape,
                )
                .clickable(
                    onClick =
                        onBack,
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.ArrowBack,
                contentDescription =
                    "返回",
                modifier =
                    Modifier.size(
                        20.dp,
                    ),
                tint =
                    t.secondaryForeground,
            )
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Column {
            Text(
                text =
                    "阅读统计",
                style =
                    MaterialTheme.typography.headlineLarge,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(
                Modifier.height(
                    t.space1,
                ),
            )

            Text(
                text =
                    "每一页，都算数",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Today                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsTodayCardV50(
    snapshot: ReaderDailyGoalSnapshotV50,
    onGoalClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val reached =
        snapshot.todayReached

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    if (reached) {
                        t.goldContainer
                    } else {
                        t.card
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        42.dp,
                    )
                    .background(
                        color =
                            if (reached) {
                                t.goldContainer
                            } else {
                                t.accent
                            },
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.BarChart,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            21.dp,
                        ),
                    tint =
                        if (reached) {
                            t.goldForeground
                        } else {
                            t.accentForeground
                        },
                )
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            ) {
                Text(
                    text =
                        "今日阅读",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.secondaryForeground,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        ReaderStatsV35.format(
                            snapshot.todaySeconds,
                        ),
                    style =
                        MaterialTheme.typography.headlineMedium,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                )
            }

            if (reached) {
                Row(
                    modifier = Modifier
                        .background(
                            color =
                                t.gold,
                            shape =
                                RoundedCornerShape(
                                    t.radiusXl,
                                ),
                        )
                        .padding(
                            horizontal =
                                t.space2,
                            vertical =
                                t.space1,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector =
                            Icons.Rounded.Check,
                        contentDescription =
                            null,
                        modifier =
                            Modifier.size(
                                14.dp,
                            ),
                        tint =
                            t.card,
                    )

                    Spacer(
                        Modifier.width(
                            t.space1,
                        ),
                    )

                    Text(
                        text =
                            "今日达标",
                        style =
                            MaterialTheme.typography.labelMedium,
                        color =
                            t.card,
                        fontWeight =
                            FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                t.space4,
            ),
        )

        ReaderStatsProgressV50(
            progress =
                snapshot.todayProgress,
            reached =
                reached,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    if (reached) {
                        "今天已经完成 ${snapshot.goalMinutes} 分钟目标"
                    } else {
                        val remaining =
                            (
                                snapshot.goalSeconds -
                                    snapshot.todaySeconds
                                )
                                .coerceAtLeast(
                                    0L,
                                )

                        "再读 ${ReaderStatsV35.format(remaining)} 即可达标"
                    },
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    if (reached) {
                        t.goldForeground
                    } else {
                        t.mutedForeground
                    },
            )

            Text(
                text =
                    "设置目标",
                modifier = Modifier
                    .clickable(
                        onClick =
                            onGoalClick,
                    )
                    .padding(
                        horizontal =
                            t.space2,
                        vertical =
                            t.space1,
                    ),
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    t.primary,
                fontWeight =
                    FontWeight.Medium,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Progress                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsProgressV50(
    progress: Float,
    reached: Boolean,
) {
    val t =
        LocalLanghuanUiTokens.current

    val animation =
        remember {
            Animatable(
                0f,
            )
        }

    LaunchedEffect(
        progress,
    ) {
        animation.snapTo(
            0f,
        )

        animation.animateTo(
            targetValue =
                progress.coerceIn(
                    0f,
                    1f,
                ),
            animationSpec =
                tween(
                    durationMillis =
                        560,
                    easing =
                        FastOutSlowInEasing,
                ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                6.dp,
            )
            .background(
                color =
                    t.border,
                shape =
                    RoundedCornerShape(
                        t.radiusSm,
                    ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(
                    animation.value,
                )
                .height(
                    6.dp,
                )
                .background(
                    color =
                        if (reached) {
                            t.gold
                        } else {
                            t.primary
                        },
                    shape =
                        RoundedCornerShape(
                            t.radiusSm,
                        ),
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Week Card                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsWeekCardV50(
    snapshot: ReaderDailyGoalSnapshotV50,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            ) {
                Text(
                    text =
                        "最近 7 天",
                    style =
                        MaterialTheme.typography.titleLarge,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text =
                        "本周 ${snapshot.weekReachedDays} / 7 天达标",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                )
            }

            Box(
                modifier = Modifier
                    .size(
                        40.dp,
                    )
                    .background(
                        color =
                            if (
                                snapshot.weekReachedDays >
                                0
                            ) {
                                t.goldContainer
                            } else {
                                t.input
                            },
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusMd,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.LocalFireDepartment,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            20.dp,
                        ),
                    tint =
                        if (
                            snapshot.weekReachedDays >
                            0
                        ) {
                            t.gold
                        } else {
                            t.mutedForeground
                        },
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space4,
            ),
        )

        ReaderStatsWeekChartV50(
            days =
                snapshot.week,
            goalSeconds =
                snapshot.goalSeconds,
        )

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(
                        20.dp,
                    )
                    .height(
                        2.dp,
                    )
                    .background(
                        color =
                            t.gold,
                        shape =
                            RoundedCornerShape(
                                t.radiusSm,
                            ),
                    ),
            )

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            Text(
                text =
                    "目标线 · ${snapshot.goalMinutes} 分钟",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Week Chart                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsWeekChartV50(
    days: List<ReaderDailyGoalDayV50>,
    goalSeconds: Long,
) {
    val t =
        LocalLanghuanUiTokens.current

    val grow =
        remember {
            Animatable(
                0f,
            )
        }

    LaunchedEffect(
        days,
        goalSeconds,
    ) {
        grow.snapTo(
            0f,
        )

        grow.animateTo(
            targetValue =
                1f,
            animationSpec =
                tween(
                    durationMillis =
                        650,
                    easing =
                        FastOutSlowInEasing,
                ),
        )
    }


    val maximum =
        max(
            goalSeconds,
            days.maxOfOrNull {
                it.seconds
            }
                ?: 0L,
        )
            .coerceAtLeast(
                60L,
            )

    val chartMaximum =
        maximum *
            1.12f


    Column(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    154.dp,
                ),
        ) {
            if (
                days.isEmpty() ||
                chartMaximum <=
                0f
            ) {
                return@Canvas
            }

            val widthPerDay =
                size.width /
                    days.size.toFloat()

            val barWidth =
                widthPerDay *
                    0.42f

            val bottom =
                size.height -
                    6.dp.toPx()

            val usableHeight =
                size.height -
                    14.dp.toPx()


            listOf(
                0.25f,
                0.50f,
                0.75f,
            )
                .forEach {
                    fraction ->

                    val y =
                        bottom -
                            usableHeight *
                            fraction

                    drawLine(
                        color =
                            t.border.copy(
                                alpha =
                                    0.62f,
                            ),
                        start =
                            Offset(
                                0f,
                                y,
                            ),
                        end =
                            Offset(
                                size.width,
                                y,
                            ),
                        strokeWidth =
                            1.dp.toPx(),
                    )
                }


            val goalFraction =
                (
                    goalSeconds.toFloat() /
                        chartMaximum
                    )
                    .coerceIn(
                        0f,
                        1f,
                    )

            val goalY =
                bottom -
                    usableHeight *
                    goalFraction

            drawLine(
                color =
                    t.gold,
                start =
                    Offset(
                        0f,
                        goalY,
                    ),
                end =
                    Offset(
                        size.width,
                        goalY,
                    ),
                strokeWidth =
                    1.dp.toPx(),
                cap =
                    StrokeCap.Round,
                pathEffect =
                    PathEffect.dashPathEffect(
                        floatArrayOf(
                            6.dp.toPx(),
                            5.dp.toPx(),
                        ),
                    ),
            )


            days.forEachIndexed {
                index,
                day ->

                val fraction =
                    (
                        day.seconds.toFloat() /
                            chartMaximum
                        )
                        .coerceIn(
                            0f,
                            1f,
                        ) *
                        grow.value

                val barHeight =
                    (
                        usableHeight *
                            fraction
                        )
                        .coerceAtLeast(
                            2.dp.toPx(),
                        )

                val centerX =
                    widthPerDay *
                        index +
                        widthPerDay /
                        2f

                val left =
                    centerX -
                        barWidth /
                        2f

                val top =
                    bottom -
                        barHeight

                drawRoundRect(
                    color =
                        when {
                            day.reached -> {
                                t.gold
                            }

                            day.label ==
                                "今" -> {
                                t.primary
                            }

                            else -> {
                                t.primary.copy(
                                    alpha =
                                        0.34f,
                                )
                            }
                        },
                    topLeft =
                        Offset(
                            left,
                            top,
                        ),
                    size =
                        Size(
                            width =
                                barWidth,
                            height =
                                barHeight,
                        ),
                    cornerRadius =
                        CornerRadius(
                            x =
                                4.dp.toPx(),
                            y =
                                4.dp.toPx(),
                        ),
                )
            }
        }


        Row(
            modifier =
                Modifier.fillMaxWidth(),
        ) {
            days.forEach {
                day ->

                Text(
                    text =
                        day.label,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        if (
                            day.label ==
                            "今"
                        ) {
                            t.foreground
                        } else {
                            t.mutedForeground
                        },
                    fontWeight =
                        if (
                            day.label ==
                            "今"
                        ) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    textAlign =
                        TextAlign.Center,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Overview                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsOverviewV50(
    snapshot: ReaderDailyGoalSnapshotV50,
) {
    val t =
        LocalLanghuanUiTokens.current

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                t.space2,
            ),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            ReaderStatsMetricV50(
                icon =
                    Icons.Rounded.Schedule,
                value =
                    readerStatsCompactTimeV50(
                        snapshot.totalSeconds,
                    ),
                label =
                    "累计阅读",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )

            ReaderStatsMetricV50(
                icon =
                    Icons.Rounded.LocalFireDepartment,
                value =
                    "${snapshot.readingStreak} 天",
                label =
                    "连续阅读",
                gold =
                    snapshot.readingStreak >
                        0,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            ReaderStatsMetricV50(
                icon =
                    Icons.Rounded.Check,
                value =
                    "${snapshot.totalReachedDays} 天",
                label =
                    "累计达标",
                gold =
                    snapshot.totalReachedDays >
                        0,
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )

            ReaderStatsMetricV50(
                icon =
                    Icons.Rounded.TrackChanges,
                value =
                    "${snapshot.weekReachedDays}/7",
                label =
                    "本周达标",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            ReaderStatsMetricV50(
                icon =
                    null,
                value =
                    snapshot.pages.toString(),
                label =
                    "累计翻页",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )

            ReaderStatsMetricV50(
                icon =
                    null,
                value =
                    snapshot.chapters.toString(),
                label =
                    "读完章节",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Metric                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsMetricV50(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gold: Boolean = false,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Column(
        modifier = modifier
            .background(
                color =
                    if (gold) {
                        t.goldContainer
                    } else {
                        t.card
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
    ) {
        if (
            icon !=
            null
        ) {
            Box(
                modifier = Modifier
                    .size(
                        34.dp,
                    )
                    .background(
                        color =
                            if (gold) {
                                t.goldContainer
                            } else {
                                t.accent
                            },
                        shape =
                            RoundedCornerShape(
                                t.radiusSm,
                            ),
                    )
                    .border(
                        width =
                            1.dp,
                        color =
                            t.border,
                        shape =
                            RoundedCornerShape(
                                t.radiusSm,
                            ),
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        icon,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(
                            17.dp,
                        ),
                    tint =
                        if (gold) {
                            t.gold
                        } else {
                            t.primary
                        },
                )
            }

            Spacer(
                Modifier.height(
                    t.space3,
                ),
            )
        }

        Text(
            text =
                value,
            style =
                MaterialTheme.typography.titleLarge,
            color =
                if (gold) {
                    t.goldForeground
                } else {
                    t.foreground
                },
            fontWeight =
                FontWeight.SemiBold,
            maxLines =
                1,
            overflow =
                TextOverflow.Ellipsis,
        )

        Spacer(
            Modifier.height(
                t.space1,
            ),
        )

        Text(
            text =
                label,
            style =
                MaterialTheme.typography.bodySmall,
            color =
                t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Goal Card                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsGoalCardV50(
    snapshot: ReaderDailyGoalSnapshotV50,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            )
            .padding(
                t.space4,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(
                    42.dp,
                )
                .background(
                    color =
                        t.accent,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                )
                .border(
                    width =
                        1.dp,
                    color =
                        t.border,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.TrackChanges,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(
                        21.dp,
                    ),
                tint =
                    t.accentForeground,
            )
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Column(
            modifier =
                Modifier.weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    "每日阅读目标",
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(
                Modifier.height(
                    t.space1,
                ),
            )

            Text(
                text =
                    "每天 ${snapshot.goalMinutes} 分钟",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        Text(
            text =
                "修改",
            style =
                MaterialTheme.typography.labelLarge,
            color =
                t.primary,
            fontWeight =
                FontWeight.Medium,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Goal Dialog                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsGoalDialogV50(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Dialog(
        onDismissRequest =
            onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress =
                    true,
                dismissOnClickOutside =
                    true,
            ),
    ) {
        val shape =
            RoundedCornerShape(
                t.radiusXl,
            )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    max =
                        520.dp,
                )
                .background(
                    color =
                        t.card,
                    shape =
                        shape,
                )
                .border(
                    width =
                        1.dp,
                    color =
                        t.border,
                    shape =
                        shape,
                )
                .padding(
                    t.space4,
                ),
        ) {
            Text(
                text =
                    "每日阅读目标",
                style =
                    MaterialTheme.typography.titleLarge,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(
                Modifier.height(
                    t.space1,
                ),
            )

            Text(
                text =
                    "选择每天想保持的阅读时间",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )

            ReaderDailyGoalPresetsV50
                .forEach {
                    minutes ->

                    ReaderStatsGoalOptionV50(
                        minutes =
                            minutes,
                        selected =
                            current ==
                                minutes,
                        onClick = {
                            onSelect(
                                minutes,
                            )
                        },
                    )

                    Spacer(
                        Modifier.height(
                            t.space2,
                        ),
                    )
                }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Goal Option                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsGoalOptionV50(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                48.dp,
            )
            .background(
                color =
                    if (selected) {
                        t.accent
                    } else {
                        t.input
                    },
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    if (selected) {
                        t.primary
                    } else {
                        t.border
                    },
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            )
            .padding(
                horizontal =
                    t.space3,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text =
                "$minutes 分钟",
            modifier =
                Modifier.weight(
                    1f,
                ),
            style =
                MaterialTheme.typography.bodyLarge,
            color =
                if (selected) {
                    t.accentForeground
                } else {
                    t.foreground
                },
            fontWeight =
                if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
        )

        if (selected) {
            Icon(
                imageVector =
                    Icons.Rounded.Check,
                contentDescription =
                    "当前目标",
                modifier =
                    Modifier.size(
                        19.dp,
                    ),
                tint =
                    t.primary,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Helpers                                   */
/* -------------------------------------------------------------------------- */

private fun readerStatsCompactTimeV50(
    seconds: Long,
): String {
    val safe =
        seconds.coerceAtLeast(
            0L,
        )

    return when {
        safe <
            60L -> {
            "${safe}秒"
        }

        safe <
            3_600L -> {
            "${safe / 60L}分钟"
        }

        safe <
            36_000L -> {
            val hours =
                safe /
                    3_600L

            val minutes =
                (
                    safe %
                        3_600L
                    ) /
                    60L

            if (
                minutes >
                0L
            ) {
                "${hours}小时${minutes}分"
            } else {
                "${hours}小时"
            }
        }

        else -> {
            "${safe / 3_600L}小时"
        }
    }
}
