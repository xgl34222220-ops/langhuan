package com.xiguli.langhuan.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 琅嬛「羊皮墨蓝」配色，替换原来的 shadcn neutral（zinc 系）。
 *
 * zinc 是一套冷灰。冷灰底会把中文宋体正文压得发青，长文阅读下观感差别很明显，
 * 所以这里整体换成暖纸感的中性色。
 *
 * 三条硬规则，改色时不要破坏：
 *
 * 1. 页面底色永远不是纯白，正文永远不是纯黑（纯黑只留给 AMOLED 分支）。
 * 2. 所有中性色偏暖：R >= G > B，不允许出现冷蓝灰。
 * 3. 强调色只有墨蓝一套，覆盖面积控制在任一屏幕的 5% 以内（DESIGN.md §3 / §6）。
 *
 * 关于 primary 与 tertiary：shadcn 的写法是 primary 用近黑、品牌色塞在 tertiary
 * 里「只在品牌时刻用」。DESIGN.md §6 要求的是「primary 就是唯一主要强调色」，
 * 所以这里两个角色都给墨蓝——原来 15 处拿 tertiary 当品牌红的地方会自动收敛到
 * 同一套墨蓝，页面上不会再出现第二种强调色。
 *
 * 深色版是按同样三条规则推的，不是把浅色版机械反相。
 */

// ---- 强调色 --------------------------------------------------------------
/** 墨蓝：唯一强调色。 */
private val InkBlue = Color(0xFF1B365D)
private val InkBluePressed = Color(0xFF142A48)

/** 暗底上的墨蓝必须提亮，否则压在暖墨底上读不出来。 */
private val InkBlueOnDark = Color(0xFF93B8E0)

// ---- 暖中性 --------------------------------------------------------------
private val Parchment = Color(0xFFF5F4ED) // 羊皮：页面底
private val Ivory = Color(0xFFFAF9F5) // 象牙：卡片
private val WarmSand = Color(0xFFE8E6DC) // 暖沙：次级表面、描边
private val InkText = Color(0xFF141413) // 近黑带橄榄暖调
private val InkText2 = Color(0xFF3D3D3A)
private val Olive = Color(0xFF504E49) // 次级信息

private val NightGround = Color(0xFF15140F) // 暖墨底
private val NightInk = Color(0xFFEAE7DC)
private val NightInk2 = Color(0xFFC7C3B5)
private val NightMuted = Color(0xFFA8A395)

// ---- 语义色（暖化，避开 Tailwind 系的纯红纯绿）---------------------------
internal val LanghuanSuccessLight = Color(0xFF4A6B3A) // 暖森绿
internal val LanghuanSuccessDark = Color(0xFF8FB07C)
internal val LanghuanWarnLight = Color(0xFF8A6B1F) // 焦赭
internal val LanghuanWarnDark = Color(0xFFD4B268)
private val DangerLight = Color(0xFF8A3A30) // 暖陶土红
private val DangerDark = Color(0xFFE0897E)

internal val LanghuanLightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = Ivory, // 不是纯白：墨蓝上压象牙才是这套系统的写法
    primaryContainer = Color(0xFFE4ECF5),
    onPrimaryContainer = InkBluePressed,
    inversePrimary = InkBlueOnDark,

    secondary = Olive,
    onSecondary = Ivory,
    secondaryContainer = WarmSand,
    onSecondaryContainer = InkText2,

    tertiary = InkBlue,
    onTertiary = Ivory,
    tertiaryContainer = Color(0xFFE4ECF5),
    onTertiaryContainer = InkBluePressed,

    background = Parchment,
    onBackground = InkText,
    surface = Ivory,
    onSurface = InkText,
    surfaceVariant = WarmSand,
    onSurfaceVariant = Olive,
    surfaceTint = InkBlue,
    inverseSurface = Color(0xFF2A2921),
    inverseOnSurface = Color(0xFFF2F0E6),

    surfaceContainerLowest = Color(0xFFFDFCF9),
    surfaceContainerLow = Ivory,
    surfaceContainer = Color(0xFFF1F0E7),
    surfaceContainerHigh = Color(0xFFEBE9DF),
    surfaceContainerHighest = Color(0xFFE5E3D8),

    outline = Color(0xFF9C9A91),
    outlineVariant = WarmSand,
    scrim = InkText,

    error = DangerLight,
    onError = Ivory,
    errorContainer = Color(0xFFF3E1DD),
    onErrorContainer = Color(0xFF4A1F1A),
)

internal val LanghuanDarkColors = darkColorScheme(
    primary = InkBlueOnDark,
    onPrimary = Color(0xFF10233A),
    primaryContainer = Color(0xFF25405E),
    onPrimaryContainer = Color(0xFFD3E2F3),
    inversePrimary = InkBlue,

    secondary = NightInk2,
    onSecondary = Color(0xFF2A281F),
    secondaryContainer = Color(0xFF333026),
    onSecondaryContainer = Color(0xFFE3DFD1),

    tertiary = InkBlueOnDark,
    onTertiary = Color(0xFF10233A),
    tertiaryContainer = Color(0xFF25405E),
    onTertiaryContainer = Color(0xFFD3E2F3),

    background = NightGround,
    onBackground = NightInk,
    surface = Color(0xFF1A1914),
    onSurface = NightInk,
    surfaceVariant = Color(0xFF2A281F),
    onSurfaceVariant = NightMuted,
    surfaceTint = InkBlueOnDark,
    inverseSurface = NightInk,
    inverseOnSurface = Color(0xFF201E18),

    surfaceContainerLowest = Color(0xFF100F0B),
    surfaceContainerLow = Color(0xFF1A1914),
    surfaceContainer = Color(0xFF201E18),
    surfaceContainerHigh = Color(0xFF2A281F),
    surfaceContainerHighest = Color(0xFF343126),

    outline = Color(0xFF6E6A5C),
    outlineVariant = Color(0xFF2E2C24),
    scrim = Color(0xFF000000),

    error = DangerDark,
    onError = Color(0xFF3A130E),
    errorContainer = Color(0xFF5C241C),
    onErrorContainer = Color(0xFFF7DAD4),
)
