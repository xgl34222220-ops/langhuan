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
 * Stable Langhuan theme, visually aligned with LuoShu while keeping the dependency-light startup
 * path that has already proven reliable on devices.
 *
 * LuoShu is the mother UI: calm page surfaces, MIUIX-like radii, large page titles, compact rows,
 * and glass reserved for navigation/floating layers. Reader body typography remains independent.
 */
private val LanghuanLightColors = lightColorScheme(
    primary = Color(0xFF315F8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8FA),
    onPrimaryContainer = Color(0xFF153451),
    secondary = Color(0xFF5A6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EAF0),
    onSecondaryContainer = Color(0xFF252B32),
    tertiary = Color(0xFF6E5B87),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECDDFA),
    onTertiaryContainer = Color(0xFF342843),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF171A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A1F),
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = Color(0xFF646A72),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Color(0xFFF0F3F7),
    surfaceContainerHigh = Color(0xFFE9EDF2),
    surfaceContainerHighest = Color(0xFFE2E7ED),
    outline = Color(0xFFC8CED6),
    outlineVariant = Color(0xFFDDE2E8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val LanghuanDarkColors = darkColorScheme(
    primary = Color(0xFFA4C9F2),
    onPrimary = Color(0xFF073353),
    primaryContainer = Color(0xFF244B6C),
    onPrimaryContainer = Color(0xFFD5E8FC),
    secondary = Color(0xFFC3CAD2),
    onSecondary = Color(0xFF2B3036),
    secondaryContainer = Color(0xFF3A4148),
    onSecondaryContainer = Color(0xFFE0E6EC),
    tertiary = Color(0xFFD6BDE9),
    onTertiary = Color(0xFF3C2C4C),
    tertiaryContainer = Color(0xFF544162),
    onTertiaryContainer = Color(0xFFEEDCF8),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE5E8EC),
    surface = Color(0xFF191C21),
    onSurface = Color(0xFFE5E8EC),
    surfaceVariant = Color(0xFF292D33),
    onSurfaceVariant = Color(0xFFB8BEC6),
    surfaceContainerLowest = Color(0xFF0C0F13),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF1C2025),
    surfaceContainerHigh = Color(0xFF24282E),
    surfaceContainerHighest = Color(0xFF2D3238),
    outline = Color(0xFF747B84),
    outlineVariant = Color(0xFF3D434B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** LuoShu / MIUIX geometry: 7 / 11 / 18 / 24 / 30. */
private val LanghuanShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val UiSans = FontFamily.SansSerif

private val LanghuanTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-.75).sp,
    ),
    // Main page title. Mirrors LuoShu's 26/34 top-bar rhythm.
    headlineMedium = TextStyle(
        fontFamily = UiSans,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-.7).sp,
    ),
    // Detail/page-section title.
    headlineSmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.45).sp,
    ),
    // Novel-specific chapter title rhythm stays at the user's 19/24 spec.
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
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = .25.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = .2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = .2.sp,
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
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = .2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = UiSans,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = .15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = UiSans,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = UiSans,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = .15.sp,
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
    val page = colors.background
    val card = if (dark) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val raised = colors.surface

    val legacyTokens = MiuixTokens(
        pageBackground = page,
        cardBackground = card,
        elevatedCardBackground = raised,
        textPrimary = colors.onSurface,
        textSecondary = colors.onSurfaceVariant,
        success = success,
        warning = warning,
    )

    val uiTokens = LanghuanUiTokens(
        background = page,
        foreground = colors.onBackground,
        card = card,
        cardForeground = colors.onSurface,
        muted = colors.surfaceContainer,
        mutedForeground = colors.onSurfaceVariant,
        strong = colors.onSurface.copy(alpha = .86f),
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
        ring = colors.primary.copy(alpha = .48f),
        warmSurface = colors.surfaceContainerLow,
        radiusSm = 11.dp,
        radiusMd = 18.dp,
        radiusLg = 24.dp,
        radiusXl = 30.dp,
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
