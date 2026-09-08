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
 * Literary MIUIx / Langhuan Glass:
 * - warm paper background instead of clinical white/blue
 * - amber primary + pale-cyan secondary accent
 * - hierarchy comes from soft surfaces, radius and shadow, not visible borders
 * - UI typography keeps the compact Inter Tight rhythm; reader body typography stays independent
 *
 * Dynamic color remains available for future appearance settings, but the default is intentionally
 * disabled so the product keeps a stable literary identity across devices and ROMs.
 */
private val LanghuanLightColors = lightColorScheme(
    primary = Color(0xFFB47A32),
    onPrimary = Color(0xFFFFFBF5),
    primaryContainer = Color(0xFFF1DFC2),
    onPrimaryContainer = Color(0xFF4F3517),
    secondary = Color(0xFF617B7E),
    onSecondary = Color(0xFFF9FFFF),
    secondaryContainer = Color(0xFFC6D9DC),
    onSecondaryContainer = Color(0xFF263E41),
    tertiary = Color(0xFF7C6A58),
    onTertiary = Color(0xFFFFFBF6),
    tertiaryContainer = Color(0xFFEADFD2),
    onTertiaryContainer = Color(0xFF3D3025),
    background = Color(0xFFE8E5DF),
    onBackground = Color(0xFF292724),
    surface = Color(0xFFF4F1EA),
    onSurface = Color(0xFF292724),
    surfaceVariant = Color(0xFFE3DED5),
    onSurfaceVariant = Color(0xFF77736D),
    surfaceContainerLowest = Color(0xFFF9F6EF),
    surfaceContainerLow = Color(0xFFF1EEE7),
    surfaceContainer = Color(0xFFECE8E0),
    surfaceContainerHigh = Color(0xFFE5E0D8),
    surfaceContainerHighest = Color(0xFFDDD8CF),
    outline = Color(0xFFD6D1C9),
    outlineVariant = Color(0xFFD6D1C9),
    error = Color(0xFFB64B45),
    onError = Color.White,
)

private val LanghuanDarkColors = darkColorScheme(
    primary = Color(0xFFD9A968),
    onPrimary = Color(0xFF442B0E),
    primaryContainer = Color(0xFF62451F),
    onPrimaryContainer = Color(0xFFF3DFC3),
    secondary = Color(0xFFA9C5C8),
    onSecondary = Color(0xFF193538),
    secondaryContainer = Color(0xFF354F52),
    onSecondaryContainer = Color(0xFFD3E7E9),
    tertiary = Color(0xFFCDBAA8),
    onTertiary = Color(0xFF392C21),
    tertiaryContainer = Color(0xFF514236),
    onTertiaryContainer = Color(0xFFEADFD2),
    background = Color(0xFF171613),
    onBackground = Color(0xFFE9E3DA),
    surface = Color(0xFF201E1A),
    onSurface = Color(0xFFE9E3DA),
    surfaceVariant = Color(0xFF302D28),
    onSurfaceVariant = Color(0xFFBBB4AA),
    surfaceContainerLowest = Color(0xFF12110F),
    surfaceContainerLow = Color(0xFF1B1916),
    surfaceContainer = Color(0xFF24211D),
    surfaceContainerHigh = Color(0xFF2C2924),
    surfaceContainerHighest = Color(0xFF37332D),
    outline = Color(0xFF49443D),
    outlineVariant = Color(0xFF49443D),
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
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) LanghuanDarkColors else LanghuanLightColors
    }

    val success = if (dark) Color(0xFF8BD4AD) else Color(0xFF4F856B)
    val warning = if (dark) Color(0xFFE6B86F) else Color(0xFFA96F27)

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
        track = if (dark) Color(0xFF49443D) else Color(0xFFD6D1C9),
        border = if (dark) Color(0xFF49443D) else Color(0xFFD6D1C9),
        input = colors.surfaceContainerHigh,
        primary = colors.primary,
        primaryForeground = colors.onPrimary,
        accent = colors.secondaryContainer,
        accentForeground = colors.onSecondaryContainer,
        destructive = colors.error,
        destructiveForeground = colors.onError,
        success = success,
        successForeground = if (dark) Color(0xFF153728) else Color.White,
        warning = warning,
        warningForeground = if (dark) Color(0xFF422C10) else Color.White,
        ring = colors.primary.copy(alpha = .48f),
        warmSurface = if (dark) Color(0xFF2A251E) else Color(0xFFF1E9DD),
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
