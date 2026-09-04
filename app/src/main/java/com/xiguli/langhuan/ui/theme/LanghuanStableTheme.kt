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
 * Compose translation of shadcn/ui's neutral semantic theme.
 * Neutral ink/surface/border roles drive ordinary UI. The warm red-orange is a Langhuan brand
 * accent only; it is not used as a generic container color for every selected control.
 */
private val BrandLight = Color(0xFFE84C34)
private val BrandDark = Color(0xFFFF735E)
private val SuccessLight = Color(0xFF15815D)
private val SuccessDark = Color(0xFF55D6A3)
private val WarningLight = Color(0xFFB76A0A)
private val WarningDark = Color(0xFFF0B85B)

private val ShadcnLightColors = lightColorScheme(
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = Color(0xFFF4F4F5),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFAFAFA),
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color(0xFF27272A),
    tertiary = BrandLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9E5),
    onTertiaryContainer = Color(0xFF6E1D11),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF4F4F5),
    surfaceContainerHigh = Color(0xFFEFEFF0),
    surfaceContainerHighest = Color(0xFFE4E4E7),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

private val ShadcnDarkColors = darkColorScheme(
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF18181B),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFFAFAFA),
    secondary = Color(0xFFD4D4D8),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF27272A),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = BrandDark,
    onTertiary = Color(0xFF3D1009),
    tertiaryContainer = Color(0xFF542018),
    onTertiaryContainer = Color(0xFFFFD9D2),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF111113),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF18181B),
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

private val ShadcnShapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private val ShadcnTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
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
        if (dark) ShadcnDarkColors else ShadcnLightColors
    }
    val brand = if (dark) BrandDark else BrandLight
    val success = if (dark) SuccessDark else SuccessLight
    val warning = if (dark) WarningDark else WarningLight

    val legacyTokens = MiuixTokens(
        pageBackground = colors.background,
        cardBackground = colors.surfaceContainer,
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
        input = colors.outlineVariant,
        primary = colors.primary,
        primaryForeground = colors.onPrimary,
        accent = brand,
        accentForeground = if (dark) Color(0xFF3D1009) else Color.White,
        destructive = colors.error,
        destructiveForeground = colors.onError,
        success = success,
        successForeground = if (dark) Color(0xFF052E20) else Color.White,
        warning = warning,
        warningForeground = if (dark) Color(0xFF3A2400) else Color.White,
        ring = colors.onSurface.copy(alpha = if (dark) .70f else .58f),
        warmSurface = if (dark) Color(0xFF211412) else Color(0xFFFFF4F1),
        radiusSm = 6.dp,
        radiusMd = 8.dp,
        radiusLg = 10.dp,
        radiusXl = 12.dp,
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = ShadcnShapes,
        typography = ShadcnTypography,
    ) {
        CompositionLocalProvider(
            LocalMiuixTokens provides legacyTokens,
            LocalLanghuanUiTokens provides uiTokens,
            content = content,
        )
    }
}
