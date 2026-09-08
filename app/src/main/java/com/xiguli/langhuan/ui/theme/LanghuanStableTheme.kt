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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.LanghuanUiTokens
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * 琅嬛稳定主题。
 *
 * UI typography follows an Inter Tight-like compact rhythm while keeping SansSerif fallback for
 * Chinese glyphs. Reader body typography remains completely independent from this UI theme.
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
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val UiSans = FontFamily.SansSerif

private val LanghuanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.75).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = UiSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.7).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = .6.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = UiSans,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = .35.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .3.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = .3.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = UiSans,
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = .3.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = .25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 14.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = UiSans,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .25.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .25.sp,
    ),
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
        strong = colors.onSurface.copy(alpha = .82f),
        track = colors.onSurface.copy(alpha = if (dark) .12f else .075f),
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
        radiusMd = 15.dp,
        radiusLg = 18.dp,
        radiusXl = 24.dp,
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
