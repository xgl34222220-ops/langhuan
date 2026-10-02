package com.xiguli.langhuan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanThemeMode
import kotlin.math.max


/* -------------------------------------------------------------------------- */
/*                               UI-only model                                */
/* -------------------------------------------------------------------------- */

/**
 * 「我的」页只负责表达原型中的听书定时选择。
 *
 * 实际启动 / 停止 TTS Timer 仍交给上层回调，
 * 本页面不创建第二套听书存储。
 */
@Immutable
internal enum class MineSleepTimerChoiceV50(
    val label: String,
) {
    OFF("不开启"),
    MINUTES_15("15 分钟"),
    MINUTES_30("30 分钟"),
    END_OF_CHAPTER("读完本章"),
}


/* -------------------------------------------------------------------------- */
/*                                  Screen                                    */
/* -------------------------------------------------------------------------- */

@Composable
internal fun MineScreenV50(
    libraryState: LibraryExperienceState,
    studioState: StudioUiState,
    readingStats: ReaderDailyGoalSnapshotV50,
    customShelfCount: Int,
    enabledBookSourceCount: Int,
    themeMode: LanghuanThemeMode,
    sleepTimerChoice: MineSleepTimerChoiceV50,
    modifier: Modifier = Modifier,
    userName: String = "游客",
    userSubtitle: String = "阅读，保持简单",
    onSettings: () -> Unit,
    onReadingStats: () -> Unit,
    onDailyGoalChange: (Int) -> Unit,
    onReadingHistory: () -> Unit,
    onShelfManagement: () -> Unit,
    onImportLocal: () -> Unit,
    onBookSources: () -> Unit,
    onAiConfig: () -> Unit,
    onCreateBlankBook: () -> Unit,
    onAiCreation: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
    onThemeModeChange: (LanghuanThemeMode) -> Unit,
    onSleepTimerChange: (MineSleepTimerChoiceV50) -> Unit,
    onLogout: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    var creationExpanded by rememberSaveable { mutableStateOf(false) }
    var goalDialogOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }
    var sleepDialogOpen by remember { mutableStateOf(false) }
    var logoutDialogOpen by remember { mutableStateOf(false) }

    val runningTaskCount = remember(
        studioState.isGenerating,
        studioState.isPlanning,
        studioState.isRewriting,
        studioState.isAgentReviewing,
        studioState.isAuditing,
        studioState.isAutonomousPlanning,
    ) {
        listOf(
            studioState.isGenerating,
            studioState.isPlanning,
            studioState.isRewriting,
            studioState.isAgentReviewing,
            studioState.isAuditing,
            studioState.isAutonomousPlanning,
        ).count { it }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = t.space4,
            end = t.space4,
            top = t.space3,
            bottom = t.space6,
        ),
        verticalArrangement = Arrangement.spacedBy(t.space4),
    ) {
        /* Header */
        item(key = "mine-header") {
            MineHeaderV50(onSettings = onSettings)
        }

        /* User */
        item(key = "mine-user") {
            MineUserHeaderV50(
                name = userName,
                subtitle = userSubtitle,
                bookCount = libraryState.stories.size,
            )
        }

        /* Reading stats */
        item(key = "mine-reading-stats") {
            MineReadingStatsCardV50(
                stats = readingStats,
                onClick = onReadingStats,
                onGoalClick = { goalDialogOpen = true },
            )
        }

        /* Library */
        item(key = "mine-library-section") {
            Column {
                MineSectionTitleV50("阅读与书架")
                Spacer(Modifier.height(t.space2))
                MineGroupCardV50 {
                    MineNavigationRowV50(
                        icon = Icons.Rounded.History,
                        title = "阅读记录",
                        subtitle = "接着上次的故事读下去",
                        onClick = onReadingHistory,
                    )
                    MineRowDividerV50()
                    MineNavigationRowV50(
                        icon = Icons.Rounded.FolderOpen,
                        title = "书架管理",
                        subtitle = "整理分组，让好书各得其所",
                        value = "${customShelfCount.coerceAtLeast(0)} 个书架",
                        onClick = onShelfManagement,
                    )
                    MineRowDividerV50()
                    MineNavigationRowV50(
                        icon = Icons.Rounded.FileOpen,
                        title = "导入本地书籍",
                        subtitle = "TXT、EPUB、Markdown",
                        onClick = onImportLocal,
                    )
                }
            }
        }

        /* Sources & AI */
        item(key = "mine-source-ai") {
            Column {
                MineSectionTitleV50("书源与 AI")
                Spacer(Modifier.height(t.space2))
                MineGroupCardV50 {
                    MineNavigationRowV50(
                        icon = Icons.Rounded.Source,
                        title = "书源管理",
                        subtitle = "导入、启用与整理书源",
                        value = "已启用 ${enabledBookSourceCount.coerceAtLeast(0)}",
                        onClick = onBookSources,
                    )
                    MineRowDividerV50()
                    MineNavigationRowV50(
                        icon = Icons.Rounded.SmartToy,
                        title = "AI 配置",
                        subtitle = "管理服务、模型与连接",
                        value = if (studioState.provider.ready) "已连接" else "未连接",
                        valueAccent = studioState.provider.ready,
                        onClick = onAiConfig,
                    )
                }
            }
        }

        /* Creation */
        item(key = "mine-creation") {
            Column {
                MineCollapsibleSectionHeaderV50(
                    title = "创作",
                    expanded = creationExpanded,
                    onClick = { creationExpanded = !creationExpanded },
                )
                if (creationExpanded) {
                    Spacer(Modifier.height(t.space2))
                    MineGroupCardV50 {
                        MineNavigationRowV50(
                            icon = Icons.Rounded.NoteAdd,
                            title = "空白新书",
                            subtitle = "从一个故事开始写起",
                            onClick = onCreateBlankBook,
                        )
                        MineRowDividerV50()
                        MineNavigationRowV50(
                            icon = Icons.Rounded.AutoAwesome,
                            title = "AI 创作",
                            subtitle = "继续原有创作工作流",
                            onClick = onAiCreation,
                        )
                        MineRowDividerV50()
                        MineNavigationRowV50(
                            icon = Icons.Rounded.TaskAlt,
                            title = "运行与任务",
                            subtitle = if (runningTaskCount > 0) {
                                "查看正在执行的创作任务"
                            } else {
                                "查看创作运行状态"
                            },
                            value = if (runningTaskCount > 0) {
                                "$runningTaskCount 个执行中"
                            } else {
                                "无执行中"
                            },
                            valueGold = runningTaskCount > 0,
                            onClick = onRunCenter,
                        )
                        MineRowDividerV50()
                        MineNavigationRowV50(
                            icon = Icons.Rounded.AutoStories,
                            title = "创作技能",
                            subtitle = "一卷在手，心有远山",
                            onClick = onSkills,
                        )
                    }
                }
            }
        }

        /* Appearance & listen */
        item(key = "mine-preferences") {
            Column {
                MineSectionTitleV50("偏好")
                Spacer(Modifier.height(t.space2))
                MineGroupCardV50 {
                    MineNavigationRowV50(
                        icon = Icons.Rounded.Palette,
                        title = "主题",
                        subtitle = "跟随系统、浅色、深色",
                        value = mineThemeLabelV50(themeMode),
                        onClick = { themeDialogOpen = true },
                    )
                    MineRowDividerV50()
                    MineNavigationRowV50(
                        icon = Icons.Rounded.Timer,
                        title = "定时关闭",
                        subtitle = "听书自动停止",
                        value = sleepTimerChoice.label,
                        onClick = { sleepDialogOpen = true },
                    )
                }
            }
        }

        /* Logout */
        item(key = "mine-logout") {
            MineLogoutButtonV50(onClick = { logoutDialogOpen = true })
        }
    }

    /* Daily goal dialog */
    if (goalDialogOpen) {
        MineDailyGoalDialogV50(
            current = readingStats.goalMinutes,
            onDismiss = { goalDialogOpen = false },
            onSelect = { minutes ->
                goalDialogOpen = false
                onDailyGoalChange(minutes)
            },
        )
    }

    /* Theme dialog */
    if (themeDialogOpen) {
        MineThemeDialogV50(
            current = themeMode,
            onDismiss = { themeDialogOpen = false },
            onSelect = { mode ->
                themeDialogOpen = false
                onThemeModeChange(mode)
            },
        )
    }

    /* Sleep timer dialog */
    if (sleepDialogOpen) {
        MineSleepTimerDialogV50(
            current = sleepTimerChoice,
            onDismiss = { sleepDialogOpen = false },
            onSelect = { choice ->
                sleepDialogOpen = false
                onSleepTimerChange(choice)
            },
        )
    }

    /* Logout confirm */
    if (logoutDialogOpen) {
        MineLogoutConfirmDialogV50(
            onDismiss = { logoutDialogOpen = false },
            onConfirm = {
                logoutDialogOpen = false
                onLogout()
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineHeaderV50(
    onSettings: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "我的",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        MineIconButtonV50(
            icon = Icons.Rounded.Settings,
            contentDescription = "设置",
            onClick = onSettings,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                 User                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineUserHeaderV50(
    name: String,
    subtitle: String,
    bookCount: Int,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(color = t.accent, shape = CircleShape)
                .border(width = 1.dp, color = t.border, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().firstOrNull()?.toString()?.takeIf { it.isNotBlank() }
                    ?: "客",
                style = MaterialTheme.typography.titleLarge,
                color = t.accentForeground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { "游客" },
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = bookCount.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.titleLarge,
                color = t.goldForeground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = "本藏书",
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                            Reading Statistics                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineReadingStatsCardV50(
    stats: ReaderDailyGoalSnapshotV50,
    onClick: () -> Unit,
    onGoalClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(t.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = t.goldContainer,
                        shape = RoundedCornerShape(t.radiusMd),
                    )
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = RoundedCornerShape(t.radiusMd),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = t.goldForeground,
                )
            }
            Spacer(Modifier.width(t.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "阅读统计",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = "最近 7 天阅读",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = t.mutedForeground,
            )
        }
        Spacer(Modifier.height(t.space4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space3),
        ) {
            MineReadingStatV50(
                label = "今日阅读",
                value = mineMinutesV50(stats.todaySeconds),
                modifier = Modifier.weight(1f),
            )
            MineReadingStatV50(
                label = "目标",
                value = "${stats.goalMinutes} 分钟",
                modifier = Modifier.weight(1f),
            )
            MineReadingStatV50(
                label = "已连续",
                value = "${stats.readingStreak} 天",
                gold = stats.readingStreak > 0,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(t.space4))
        MineReadingWeekChartV50(stats = stats)
        Spacer(Modifier.height(t.space3))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(t.border),
        )
        Spacer(Modifier.height(t.space3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "今天 ${mineMinutesPlainV50(stats.todaySeconds)}，" +
                    "每日目标 ${stats.goalMinutes} 分钟",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (stats.todayReached) {
                    t.goldForeground
                } else {
                    t.secondaryForeground
                },
            )
            Text(
                text = "设置目标",
                modifier = Modifier
                    .clickable(onClick = onGoalClick)
                    .padding(horizontal = t.space2, vertical = t.space1),
                style = MaterialTheme.typography.labelMedium,
                color = t.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}


@Composable
private fun MineReadingStatV50(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    Column(modifier = modifier) {
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
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Week chart                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineReadingWeekChartV50(
    stats: ReaderDailyGoalSnapshotV50,
) {
    val t = LocalLanghuanUiTokens.current
    val maximum = max(
        stats.goalSeconds,
        stats.week.maxOfOrNull { it.seconds } ?: 0L,
    ).coerceAtLeast(60L).toFloat()

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
        ) {
            val bottom = size.height - 4.dp.toPx()
            val usable = size.height - 8.dp.toPx()
            val columnWidth = size.width / 7f
            val barWidth = columnWidth * 0.42f
            val targetFraction = (stats.goalSeconds.toFloat() / maximum)
                .coerceIn(0f, 1f)
            val targetY = bottom - usable * targetFraction

            drawLine(
                color = t.gold,
                start = Offset(x = 0f, y = targetY),
                end = Offset(x = size.width, y = targetY),
                strokeWidth = 1.dp.toPx(),
            )

            stats.week.take(7).forEachIndexed { index, day ->
                val fraction = (day.seconds.toFloat() / maximum).coerceIn(0f, 1f)
                val height = (usable * fraction).coerceAtLeast(2.dp.toPx())
                val center = columnWidth * index + columnWidth / 2f
                val left = center - barWidth / 2f
                drawRoundRect(
                    color = when {
                        day.reached -> t.gold
                        day.label == "今" -> t.primary
                        else -> t.primary.copy(alpha = 0.38f)
                    },
                    topLeft = Offset(x = left, y = bottom - height),
                    size = Size(width = barWidth, height = height),
                    cornerRadius = CornerRadius(
                        x = 4.dp.toPx(),
                        y = 4.dp.toPx(),
                    ),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            stats.week.take(7).forEach { day ->
                Text(
                    text = day.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.label == "今") t.foreground else t.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Sections                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineSectionTitleV50(title: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = t.foreground,
        fontWeight = FontWeight.SemiBold,
    )
}


@Composable
private fun MineCollapsibleSectionHeaderV50(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = t.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Rounded.ExpandLess
            } else {
                Icons.Rounded.ExpandMore
            },
            contentDescription = if (expanded) "收起创作" else "展开创作",
            tint = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Group card                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineGroupCardV50(
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(horizontal = t.space3),
    ) {
        content()
    }
}


/* -------------------------------------------------------------------------- */
/*                              Navigation row                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineNavigationRowV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    value: String = "",
    valueAccent: Boolean = false,
    valueGold: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = when {
                        valueGold -> t.goldContainer
                        valueAccent -> t.accent
                        else -> t.input
                    },
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when {
                    valueGold -> t.goldForeground
                    valueAccent -> t.accentForeground
                    else -> t.secondaryForeground
                },
            )
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (value.isNotBlank()) {
            Spacer(Modifier.width(t.space2))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    valueGold -> t.goldForeground
                    valueAccent -> t.primary
                    else -> t.secondaryForeground
                },
                fontWeight = if (valueGold || valueAccent) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(t.space2))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Divider                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineRowDividerV50() {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(t.border),
    )
}


/* -------------------------------------------------------------------------- */
/*                              Icon Button                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineIconButtonV50(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = t.secondaryForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Logout button                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineLogoutButtonV50(onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = t.destructive.copy(alpha = 0.08f),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.destructive.copy(alpha = 0.38f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space4),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Logout,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.destructive,
        )
        Spacer(Modifier.width(t.space2))
        Text(
            text = "退出登录",
            style = MaterialTheme.typography.labelLarge,
            color = t.destructive,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                          Daily goal dialog                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineDailyGoalDialogV50(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(15, 30, 45, 60)
    MineSingleChoiceDialogV50(
        title = "每日阅读目标",
        subtitle = "设置每天希望保持的阅读时间",
        onDismiss = onDismiss,
    ) {
        options.forEach { minutes ->
            MineDialogChoiceV50(
                title = "$minutes 分钟",
                selected = current == minutes,
                onClick = { onSelect(minutes) },
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Theme dialog                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineThemeDialogV50(
    current: LanghuanThemeMode,
    onDismiss: () -> Unit,
    onSelect: (LanghuanThemeMode) -> Unit,
) {
    MineSingleChoiceDialogV50(
        title = "主题",
        subtitle = "切换后立即应用到琅嬛界面",
        onDismiss = onDismiss,
    ) {
        MineDialogChoiceV50(
            title = "跟随系统",
            selected = current == LanghuanThemeMode.FOLLOW_SYSTEM,
            onClick = { onSelect(LanghuanThemeMode.FOLLOW_SYSTEM) },
        )
        MineDialogChoiceV50(
            title = "浅色",
            selected = current == LanghuanThemeMode.LIGHT,
            onClick = { onSelect(LanghuanThemeMode.LIGHT) },
        )
        MineDialogChoiceV50(
            title = "深色",
            selected = current == LanghuanThemeMode.DARK,
            onClick = { onSelect(LanghuanThemeMode.DARK) },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                          Sleep timer dialog                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineSleepTimerDialogV50(
    current: MineSleepTimerChoiceV50,
    onDismiss: () -> Unit,
    onSelect: (MineSleepTimerChoiceV50) -> Unit,
) {
    MineSingleChoiceDialogV50(
        title = "定时关闭",
        subtitle = "听书到指定时间后自动停止",
        onDismiss = onDismiss,
    ) {
        MineSleepTimerChoiceV50.entries.forEach { choice ->
            MineDialogChoiceV50(
                title = choice.label,
                selected = current == choice,
                onClick = { onSelect(choice) },
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Choice dialog                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineSingleChoiceDialogV50(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
            Spacer(Modifier.height(t.space4))
            content()
        }
    }
}


@Composable
private fun MineDialogChoiceV50(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = t.space2)
            .background(
                color = if (selected) t.accent else t.input,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) t.accentForeground else t.foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "当前选择",
                modifier = Modifier.size(19.dp),
                tint = t.primary,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                          Logout confirmation                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun MineLogoutConfirmDialogV50(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = t.destructive.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(t.radiusMd),
                    )
                    .border(
                        width = 1.dp,
                        color = t.destructive.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(t.radiusMd),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = t.destructive,
                )
            }
            Spacer(Modifier.height(t.space4))
            Text(
                text = "退出登录？",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = "确认后将退出当前账户。本页不会自行删除书架、小说或阅读记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
            )
            Spacer(Modifier.height(t.space5))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(t.space2),
            ) {
                MineDialogButtonV50(
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                MineDialogButtonV50(
                    text = "退出登录",
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}


@Composable
private fun MineDialogButtonV50(
    text: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (destructive) t.destructive else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (destructive) t.destructive else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (destructive) {
                t.destructiveForeground
            } else {
                t.secondaryForeground
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Helpers                                   */
/* -------------------------------------------------------------------------- */

private fun mineThemeLabelV50(mode: LanghuanThemeMode): String {
    return when (mode) {
        LanghuanThemeMode.FOLLOW_SYSTEM -> "跟随系统"
        LanghuanThemeMode.LIGHT -> "浅色"
        LanghuanThemeMode.DARK -> "深色"
    }
}


private fun mineMinutesV50(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return when {
        safe < 60L -> "$safe 秒"
        safe < 3_600L -> "${safe / 60L} 分钟"
        else -> {
            val hours = safe / 3_600L
            val minutes = (safe % 3_600L) / 60L
            if (minutes > 0L) {
                "${hours}小时${minutes}分"
            } else {
                "${hours}小时"
            }
        }
    }
}


private fun mineMinutesPlainV50(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0L) / 60L
    return "$minutes 分钟"
}
