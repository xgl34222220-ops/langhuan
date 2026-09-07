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
 * 琅嬛稳定主题：以 MIUIx / Android 原生移动端层级为主。
 *
 * 普通界面使用柔和 Surface、较大的语义圆角和 Monet；阅读正文自己的纸张主题由 Reader 负责，
 * 不再把 shadcn 的黑白 Web 风格直接套到手机主界面。
 */
private val LanghuanLightColors = lightColorScheme(
    primary = Color(0xFF245FD3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8FF),
    onPrimaryContainer = Color(0xFF0C326D),
    secondary = Color(0xFF596273),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EAF1),
    onSecondaryContainer = Color(0xFF252A33),
    tertiary = Color(0xFF7A5AA6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEDCFF),
    onTertiaryContainer = Color(0xFF392151),
    background = Color(0xFFF4F3FA),
    onBackground = Color(0xFF171923),
    surface = Color(0xFFFBFAFE),
    onSurface = Color(0xFF171923),
    surfaceVariant = Color(0xFFEAE9F1),
    onSurfaceVariant = Color(0xFF666B7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F7FD),
    surfaceContainer = Color(0xFFF0EFF6),
    surfaceContainerHigh = Color(0xFFE9E8F0),
    surfaceContainerHighest = Color(0xFFE2E1E9),
    outline = Color(0xFFC9C8D2),
    outlineVariant = Color(0xFFDFDEE7),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val LanghuanDarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF07346E),
    primaryContainer = Color(0xFF174986),
    onPrimaryContainer = Color(0xFFD9E5FF),
    secondary = Color(0xFFC6CAD5),
    onSecondary = Color(0xFF2B303A),
    secondaryContainer = Color(0xFF3A404C),
    onSecondaryContainer = Color(0xFFE2E6F0),
    tertiary = Color(0xFFD8B9FF),
    onTertiary = Color(0xFF442661),
    tertiaryContainer = Color(0xFF5C3E79),
    onTertiaryContainer = Color(0xFFEEDCFF),
    background = Color(0xFF101116),
    onBackground = Color(0xFFE7E7ED),
    surface = Color(0xFF17181E),
    onSurface = Color(0xFFE7E7ED),
    surfaceVariant = Color(0xFF292A32),
    onSurfaceVariant = Color(0xFFB9BAC4),
    surfaceContainerLowest = Color(0xFF0C0D11),
    surfaceContainerLow = Color(0xFF15161B),
    surfaceContainer = Color(0xFF1C1D23),
    surfaceContainerHigh = Color(0xFF24252C),
    surfaceContainerHighest = Color(0xFF2D2E36),
    outline = Color(0xFF74757F),
    outlineVariant = Color(0xFF3E3F47),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LanghuanShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val LanghuanTypography = Typography(
    displaySmall = TextStyle(fontSize = 31.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LanghuanStableTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) LanghuanDarkColors else LanghuanLightColors
    }

    val success = if (dark) Color(0xFF74D9AD) else Color(0xFF1B8A61)
    val warning = if (dark) Color(0xFFFFC46B) else Color(0xFFC47700)

    val legacyTokens = MiuixTokens(
        pageBackground = colors.background,
        cardBackground = colors.surfaceContainerLow,
        elevatedCardBackground = colors.surface,
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
        input = colors.surfaceContainerHigh,
        primary = colors.primary,
        primaryForeground = colors.onPrimary,
        accent = colors.primaryContainer,
        accentForeground = colors.onPrimaryContainer,
        destructive = colors.error,
        destructiveForeground = colors.onError,
        success = success,
        successForeground = if (dark) Color(0xFF063824) else Color.White,
        warning = warning,
        warningForeground = if (dark) Color(0xFF4A2C00) else Color.White,
        ring = colors.primary.copy(alpha = .55f),
        warmSurface = colors.surfaceContainerLow,
        radiusSm = 12.dp,
        radiusMd = 16.dp,
        radiusLg = 20.dp,
        radiusXl = 26.dp,
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = LanghuanShapes,
        typography = LanghuanTypography,
    ) {
        CompositionLocalProvider(
            LocalMiuixTokens provides legacyTokens,
            LocalLanghuanUiTokens provides uiTokens,
            content = content,
        )
    }
}
