package com.xiguli.langhuan.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanThemeMode


/**
 * 琅嬛设置页 · V50
 *
 * 本页面严格只负责展示、用户交互、调用上层回调。
 * 不直接访问 SharedPreferences / DataStore / Repository / 文件系统 /
 * AI Provider Store。所有真实状态由上层已有 ViewModel / Store 转换后传入。
 *
 * v3 视觉规则：
 * - LocalLanghuanUiTokens
 * - 普通层级统一 1dp border，不使用 shadow / elevation
 * - 圆角仅 8 / 12 / 16 / 24
 * - 间距只使用 space1 ~ space6
 * - primary = 玉青，gold = 赤金
 */
@Composable
internal fun SettingsScreenV50(
    themeMode: LanghuanThemeMode,

    readerThemeLabel: String,
    readerFontSizeSp: Float,
    keepScreen: Boolean,
    volumeTurn: Boolean,
    immersive: Boolean,
    showTimeBattery: Boolean,

    primaryServiceName: String,
    primaryServiceConnected: Boolean,
    taskModelRouteLabel: String,
    activeSkillCount: Int,

    modifier: Modifier = Modifier,
    appVersionLabel: String = "Alpha",

    onBack: () -> Unit,
    onThemeModeChange: (LanghuanThemeMode) -> Unit,

    onReadingTypography: () -> Unit,
    onKeepScreenChange: (Boolean) -> Unit,
    onVolumeTurnChange: (Boolean) -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    onShowTimeBatteryChange: (Boolean) -> Unit,

    onPrimaryService: () -> Unit,
    onTaskModelRouting: () -> Unit,
    onSkills: () -> Unit,

    onBackupProject: () -> Unit,
    onRestoreProject: () -> Unit,

    onAbout: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

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
        verticalArrangement = Arrangement.spacedBy(t.space5),
    ) {
        /* Top bar */
        item(key = "settings-header") {
            SettingsHeaderV50(onBack = onBack)
        }

        /* Appearance */
        item(key = "settings-appearance") {
            Column {
                SettingsSectionTitleV50(title = "外观")
                Spacer(Modifier.height(t.space2))
                SettingsGroupCardV50 {
                    SettingsThemeBlockV50(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                    )
                }
            }
        }

        /* Reading */
        item(key = "settings-reading") {
            Column {
                SettingsSectionTitleV50(title = "阅读")
                Spacer(Modifier.height(t.space2))
                SettingsGroupCardV50 {
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.TextFields,
                        title = "阅读排版",
                        subtitle = "字体、字号、行距与阅读主题",
                        value = "$readerThemeLabel ${readerFontSizeSp.toInt()}",
                        onClick = onReadingTypography,
                    )
                    SettingsDividerV50()
                    SettingsSwitchRowV50(
                        icon = Icons.Rounded.WbSunny,
                        title = "屏幕常亮",
                        subtitle = "阅读时保持屏幕常亮",
                        checked = keepScreen,
                        onCheckedChange = onKeepScreenChange,
                    )
                    SettingsDividerV50()
                    SettingsSwitchRowV50(
                        icon = Icons.Rounded.VolumeUp,
                        title = "音量键翻页",
                        subtitle = "使用音量键控制上一页和下一页",
                        checked = volumeTurn,
                        onCheckedChange = onVolumeTurnChange,
                    )
                    SettingsDividerV50()
                    SettingsSwitchRowV50(
                        icon = Icons.Rounded.Fullscreen,
                        title = "沉浸式",
                        subtitle = "阅读时隐藏状态栏",
                        checked = immersive,
                        onCheckedChange = onImmersiveChange,
                    )
                    SettingsDividerV50()
                    SettingsSwitchRowV50(
                        icon = Icons.Rounded.Visibility,
                        title = "时间电量",
                        subtitle = "页脚显示时间与电量",
                        checked = showTimeBattery,
                        onCheckedChange = onShowTimeBatteryChange,
                    )
                }
            }
        }

        /* AI */
        item(key = "settings-ai") {
            Column {
                SettingsSectionTitleV50(title = "AI 服务")
                Spacer(Modifier.height(t.space2))
                SettingsGroupCardV50 {
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.SmartToy,
                        title = "主力服务",
                        subtitle = primaryServiceName.ifBlank { "选择默认 AI 服务" },
                        value = if (primaryServiceConnected) "已连接" else "未连接",
                        valueAccent = primaryServiceConnected,
                        onClick = onPrimaryService,
                    )
                    SettingsDividerV50()
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "任务模型路由",
                        subtitle = "为不同创作任务选择模型",
                        value = taskModelRouteLabel.ifBlank { "继承默认" },
                        onClick = onTaskModelRouting,
                    )
                    SettingsDividerV50()
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "创作技能",
                        subtitle = "管理写作流程中的技能能力",
                        value = "${activeSkillCount.coerceAtLeast(0)} 项生效",
                        valueGold = activeSkillCount > 0,
                        onClick = onSkills,
                    )
                }
            }
        }

        /* Data */
        item(key = "settings-data") {
            Column {
                SettingsSectionTitleV50(title = "数据")
                Spacer(Modifier.height(t.space2))
                SettingsGroupCardV50 {
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.Backup,
                        title = "备份项目",
                        subtitle = "导出 .lhproj 文件",
                        onClick = onBackupProject,
                    )
                    SettingsDividerV50()
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.Restore,
                        title = "恢复项目",
                        subtitle = "从备份文件导入",
                        onClick = onRestoreProject,
                    )
                    SettingsDividerV50()
                    SettingsStaticRowV50(
                        icon = Icons.Rounded.Visibility,
                        title = "同步",
                        subtitle = "仅保存在本机",
                        value = "本机",
                    )
                }
            }
        }

        /* About */
        item(key = "settings-about") {
            Column {
                SettingsSectionTitleV50(title = "关于")
                Spacer(Modifier.height(t.space2))
                SettingsGroupCardV50 {
                    SettingsNavigationRowV50(
                        icon = Icons.Rounded.Info,
                        title = "琅嬛",
                        subtitle = "AI 长篇小说创作与阅读",
                        value = appVersionLabel,
                        valueGold = true,
                        onClick = onAbout,
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsHeaderV50(
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconButtonV50(
            icon = Icons.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Spacer(Modifier.width(t.space3))
        Text(
            text = "设置",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Section                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsSectionTitleV50(title: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = t.foreground,
        fontWeight = FontWeight.SemiBold,
    )
}


/* -------------------------------------------------------------------------- */
/*                               Group Card                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsGroupCardV50(
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
/*                             Theme Selector                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsThemeBlockV50(
    themeMode: LanghuanThemeMode,
    onThemeModeChange: (LanghuanThemeMode) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.space3),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = t.accent,
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
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = t.accentForeground,
                )
            }
            Spacer(Modifier.width(t.space3))
            Column {
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.bodyLarge,
                    color = t.foreground,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = "跟随系统、浅色、深色",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }
        Spacer(Modifier.height(t.space3))
        val segmentShape = RoundedCornerShape(t.radiusMd)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(color = t.input, shape = segmentShape)
                .border(width = 1.dp, color = t.border, shape = segmentShape)
                .padding(t.space1),
            horizontalArrangement = Arrangement.spacedBy(t.space1),
        ) {
            SettingsThemeSegmentV50(
                text = "跟随系统",
                selected = themeMode == LanghuanThemeMode.FOLLOW_SYSTEM,
                modifier = Modifier.weight(1f),
                onClick = { onThemeModeChange(LanghuanThemeMode.FOLLOW_SYSTEM) },
            )
            SettingsThemeSegmentV50(
                text = "浅色",
                selected = themeMode == LanghuanThemeMode.LIGHT,
                modifier = Modifier.weight(1f),
                onClick = { onThemeModeChange(LanghuanThemeMode.LIGHT) },
            )
            SettingsThemeSegmentV50(
                text = "深色",
                selected = themeMode == LanghuanThemeMode.DARK,
                modifier = Modifier.weight(1f),
                onClick = { onThemeModeChange(LanghuanThemeMode.DARK) },
            )
        }
    }
}


@Composable
private fun SettingsThemeSegmentV50(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = if (selected) t.card else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.primary else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                            Navigation Row                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsNavigationRowV50(
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
        SettingsRowIconV50(icon = icon, accent = valueAccent, gold = valueGold)
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
                fontWeight = if (valueAccent || valueGold) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
/*                               Switch Row                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsSwitchRowV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIconV50(icon = icon, accent = checked)
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
            )
        }
        Spacer(Modifier.width(t.space3))
        SettingsSwitchV50(checked = checked)
    }
}


/* -------------------------------------------------------------------------- */
/*                                Static Row                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsStaticRowV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String = "",
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowIconV50(icon = icon)
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
            )
        }
        if (value.isNotBlank()) {
            Spacer(Modifier.width(t.space2))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = t.secondaryForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Row Icon                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsRowIconV50(
    icon: ImageVector,
    accent: Boolean = false,
    gold: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = when {
                    gold -> t.goldContainer
                    accent -> t.accent
                    else -> t.input
                },
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when {
                gold -> t.goldForeground
                accent -> t.accentForeground
                else -> t.secondaryForeground
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Switch                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsSwitchV50(checked: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val trackShape = CircleShape
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .background(
                color = if (checked) t.primary else t.input,
                shape = trackShape,
            )
            .border(
                width = 1.dp,
                color = if (checked) t.primary else t.border,
                shape = trackShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(
                    start = if (checked) 20.dp else 2.dp,
                    top = 2.dp,
                )
                .size(20.dp)
                .background(
                    color = if (checked) t.card else t.mutedForeground,
                    shape = CircleShape,
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Divider                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun SettingsDividerV50() {
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
private fun SettingsIconButtonV50(
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
