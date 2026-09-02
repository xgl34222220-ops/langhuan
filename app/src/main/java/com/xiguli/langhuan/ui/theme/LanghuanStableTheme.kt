package com.xiguli.langhuan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 琅嬛「纸白」主题。
 *
 * 设计取向：近乎单色的纸白底 + 高对比正文 + 单一暖橙强调色，圆角克制，
 * 让封面和正文本身成为页面上唯一的颜色来源。
 */

/** 暖橙强调色：选中态下划线、徽标、可点击强调文字。 */
private val AccentLight = Color(0xFFF4553D)
private val AccentDark = Color(0xFFFF7A66)

private val PaperLightColors = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6E1),
    onPrimaryContainer = Color(0xFF5C1A10),
    secondary = Color(0xFF5A5A5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F2),
    onSecondaryContainer = Color(0xFF1A1A1C),
    tertiary = Color(0xFFC8912F),
    onTertiary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1C),
    surfaceVariant = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFF9A9AA0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color(0xFFEFEFF2),
    surfaceContainerHighest = Color(0xFFE9E9ED),
    outline = Color(0xFFC6C6CC),
    outlineVariant = Color(0xFFEDEDF0),
    error = Color(0xFFD93025),
    onError = Color.White,
)

private val PaperDarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF3A0F08),
    primaryContainer = Color(0xFF5C2419),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFFB6B6BC),
    onSecondary = Color(0xFF1A1A1C),
    secondaryContainer = Color(0xFF2A2A2E),
    onSecondaryContainer = Color(0xFFEDEDEF),
    tertiary = Color(0xFFE2BC6E),
    onTertiary = Color(0xFF3A2D08),
    background = Color(0xFF0F0F10),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF0F0F10),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFF8A8A90),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF141416),
    surfaceContainer = Color(0xFF1A1A1C),
    surfaceContainerHigh = Color(0xFF232326),
    surfaceContainerHighest = Color(0xFF2C2C30),
    outline = Color(0xFF5A5A60),
    outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF3A0A06),
)

/** 圆角收敛：封面 4dp、卡片 8/12dp、面板 16dp、底部弹层 20dp。 */
private val StableShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 字阶：标题重、正文松、次要信息小而灰，拉开层级而不靠颜色。 */
private val StableTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

/**
 * Stable theme used only after StartupDatabaseGate succeeds.
 *
 * 默认使用固定的纸白配色，让应用有稳定的视觉身份；把 [dynamicColor] 置为 true
 * 可以退回 Android 12+ 的系统 Monet 取色。无论走哪条分支，都会向下提供
 * [LocalMiuixTokens]，因此所有读 tokens 的页面都会跟随明暗模式，而不再固定
 * 落在浅色默认值上。
 */
@Composable
fun LanghuanStableTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) PaperDarkColors else PaperLightColors
    }

    val tokens = MiuixTokens(
        pageBackground = colors.background,
        cardBackground = if (dark) colors.surfaceContainer else colors.surfaceVariant,
        elevatedCardBackground = if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        textPrimary = colors.onSurface,
        textSecondary = colors.onSurfaceVariant,
        success = if (dark) Color(0xFF4FD39B) else Color(0xFF1E9E6A),
        warning = if (dark) Color(0xFFF5B95C) else Color(0xFFD98A16),
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = StableShapes,
        typography = StableTypography,
    ) {
        CompositionLocalProvider(
            LocalMiuixTokens provides tokens,
            content = content,
        )
    }
}
