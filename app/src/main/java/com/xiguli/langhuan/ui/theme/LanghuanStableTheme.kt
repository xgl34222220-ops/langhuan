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
import com.xiguli.langhuan.ui.design.LanghuanUiTokens
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * 琅嬛稳定主题：shadcn-inspired semantic design system for Compose.
 *
 * 不是照搬网页样式，而是保留「纸白阅读感 + 暖橙品牌色」，同时引入统一的
 * background / foreground / card / muted / border / accent / destructive / success / warning /
 * ring 语义层。业务页不应再自行发明颜色和边框。
 */
private val AccentLight = Color(0xFFF4553D)
private val AccentDark = Color(0xFFFF7A66)
private val InkLight = Color(0xFF18181B)
private val InkDark = Color(0xFFF4F4F5)
private val SuccessLight = Color(0xFF16875D)
private val SuccessDark = Color(0xFF4FD39B)
private val WarningLight = Color(0xFFC87912)
private val WarningDark = Color(0xFFF5B95C)

private val PaperLightColors = lightColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = Color(0xFFF0EFED),
    onPrimaryContainer = InkLight,
    secondary = Color(0xFF52525B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F2),
    onSecondaryContainer = InkLight,
    tertiary = AccentLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8E2),
    onTertiaryContainer = Color(0xFF6A1D11),
    background = Color(0xFFFCFCFB),
    onBackground = InkLight,
    surface = Color(0xFFFFFFFF),
    onSurface = InkLight,
    surfaceVariant = Color(0xFFF4F4F2),
    onSurfaceVariant = Color(0xFF71717A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAF9),
    surfaceContainer = Color(0xFFF6F6F4),
    surfaceContainerHigh = Color(0xFFF0EFED),
    surfaceContainerHighest = Color(0xFFE9E8E5),
    outline = Color(0xFFD6D3D1),
    outlineVariant = Color(0xFFE7E5E4),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val PaperDarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = Color(0xFF111113),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = InkDark,
    secondary = Color(0xFFD4D4D8),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = InkDark,
    tertiary = AccentDark,
    onTertiary = Color(0xFF3A0F08),
    tertiaryContainer = Color(0xFF5A241A),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF0B0B0C),
    onBackground = InkDark,
    surface = Color(0xFF111113),
    onSurface = InkDark,
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Color(0xFF18181B),
    surfaceContainerHigh = Color(0xFF202023),
    surfaceContainerHighest = Color(0xFF27272A),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

private val StableShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val StableTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

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
    val success = if (dark) SuccessDark else SuccessLight
    val warning = if (dark) WarningDark else WarningLight

    val legacyTokens = MiuixTokens(
        pageBackground = colors.background,
        cardBackground = colors.surfaceContainer,
        elevatedCardBackground = colors.surfaceContainerLowest,
        textPrimary = colors.onSurface,
        textSecondary = colors.onSurfaceVariant,
        success = success,
        warning = warning,
    )

    val uiTokens = LanghuanUiTokens(
        background = colors.background,
        foreground = colors.onBackground,
        card = colors.surface,
        cardForeground = colors.onSurface,
        muted = colors.surfaceContainer,
        mutedForeground = colors.onSurfaceVariant,
        border = colors.outlineVariant,
        input = colors.outlineVariant,
        primary = colors.primary,
        primaryForeground = colors.onPrimary,
        accent = colors.tertiary,
        accentForeground = colors.onTertiary,
        destructive = colors.error,
        destructiveForeground = colors.onError,
        success = success,
        successForeground = if (dark) Color(0xFF052E20) else Color.White,
        warning = warning,
        warningForeground = if (dark) Color(0xFF3A2400) else Color.White,
        ring = colors.tertiary,
        warmSurface = if (dark) Color(0xFF281713) else Color(0xFFFFF4F0),
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = StableShapes,
        typography = StableTypography,
    ) {
        CompositionLocalProvider(
            LocalMiuixTokens provides legacyTokens,
            LocalLanghuanUiTokens provides uiTokens,
            content = content,
        )
    }
}
