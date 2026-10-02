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
 * 琅嬛全局稳定主题。
 *
 * 设计原则：
 * 1. 应用层保持现代、克制、低饱和，不把“书卷气”简单等同于仿古纸张。
 * 2. 标题使用系统 Serif，形成出版物/书籍气质；功能正文和控件使用 Sans。
 * 3. 浅色主题以暖纸白、墨色、青黛为主；深色主题使用暖墨黑而非纯黑。
 * 4. 阅读器正文主题独立于这里的 MaterialTheme，由 ReaderRenderV30 等模块管理。
 * 5. 保留 LocalMiuixTokens / LocalLanghuanUiTokens，避免现有页面调用断裂。
 */

/* -------------------------------------------------------------------------- */
/*                                    Color                                   */
/* -------------------------------------------------------------------------- */

private val Ink = Color(0xFF22221F)
private val InkSoft = Color(0xFF5F605A)

private val Paper = Color(0xFFF7F5F0)
private val PaperSurface = Color(0xFFFCFBF8)
private val PaperRaised = Color(0xFFFFFFFF)

private val Jade = Color(0xFF456A61)
private val JadeDeep = Color(0xFF294A42)
private val JadeSoft = Color(0xFFDDE9E4)

private val Tea = Color(0xFF806A4B)
private val TeaSoft = Color(0xFFEDE5D8)

private val Bamboo = Color(0xFF647761)
private val BambooSoft = Color(0xFFE2E8DE)

private val Night = Color(0xFF111310)
private val NightSurface = Color(0xFF191C18)
private val NightInk = Color(0xFFE9E8E1)
private val NightInkSoft = Color(0xFFB9BAB2)

private val LanghuanLightColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = JadeSoft,
    onPrimaryContainer = JadeDeep,

    secondary = Tea,
    onSecondary = Color(0xFFFFFFFF),

    secondaryContainer = TeaSoft,
    onSecondaryContainer = Color(0xFF493B28),

    tertiary = Bamboo,
    onTertiary = Color(0xFFFFFFFF),

    tertiaryContainer = BambooSoft,
    onTertiaryContainer = Color(0xFF354333),

    background = Paper,
    onBackground = Ink,

    surface = PaperSurface,
    onSurface = Ink,

    surfaceVariant = Color(0xFFECEAE4),
    onSurfaceVariant = InkSoft,

    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Color(0xFFF1EFE9),
    surfaceContainerHigh = Color(0xFFEAE7E0),
    surfaceContainerHighest = Color(0xFFE2DED6),

    outline = Color(0xFFAAA9A1),
    outlineVariant = Color(0xFFDDDAD3),

    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val LanghuanDarkColors = darkColorScheme(
    primary = Color(0xFFA7CFC2),
    onPrimary = Color(0xFF12372F),

    primaryContainer = Color(0xFF284C43),
    onPrimaryContainer = Color(0xFFC2E9DD),

    secondary = Color(0xFFD7BE94),
    onSecondary = Color(0xFF3C2F1C),

    secondaryContainer = Color(0xFF55462F),
    onSecondaryContainer = Color(0xFFF1D7AA),

    tertiary = Color(0xFFB8CDB2),
    onTertiary = Color(0xFF253623),

    tertiaryContainer = Color(0xFF3B4D38),
    onTertiaryContainer = Color(0xFFD3E7CD),

    background = Night,
    onBackground = NightInk,

    surface = NightSurface,
    onSurface = NightInk,

    surfaceVariant = Color(0xFF292D28),
    onSurfaceVariant = NightInkSoft,

    surfaceContainerLowest = Color(0xFF0C0E0C),
    surfaceContainerLow = Color(0xFF151815),
    surfaceContainer = Color(0xFF1B1F1B),
    surfaceContainerHigh = Color(0xFF222722),
    surfaceContainerHighest = Color(0xFF2A302A),

    outline = Color(0xFF858880),
    outlineVariant = Color(0xFF3C403A),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/* -------------------------------------------------------------------------- */
/*                                    Shape                                   */
/* -------------------------------------------------------------------------- */

private val LanghuanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/* -------------------------------------------------------------------------- */
/*                                 Typography                                 */
/* -------------------------------------------------------------------------- */

private val LanghuanSerif = FontFamily.Serif
private val LanghuanSans = FontFamily.SansSerif

private val LanghuanTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 43.sp,
        letterSpacing = (-0.8).sp,
    ),

    displayMedium = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.7).sp,
    ),

    displaySmall = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.55).sp,
    ),

    headlineMedium = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.35).sp,
    ),

    headlineSmall = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),

    titleLarge = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    ),

    titleMedium = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.05.sp,
    ),

    titleSmall = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.15.sp,
    ),

    bodyMedium = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp,
    ),

    bodySmall = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    labelMedium = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    ),

    labelSmall = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)

/* -------------------------------------------------------------------------- */
/*                                    Theme                                   */
/* -------------------------------------------------------------------------- */

@Composable
fun LanghuanStableTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current

    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> LanghuanDarkColors
        else -> LanghuanLightColors
    }

    val success = if (darkTheme) {
        Color(0xFF8DD5AE)
    } else {
        Color(0xFF377B58)
    }

    val warning = if (darkTheme) {
        Color(0xFFE5BE76)
    } else {
        Color(0xFF9A6B24)
    }

    val pageBackground = colors.background
    val cardBackground = if (darkTheme) {
        colors.surfaceContainerLow
    } else {
        colors.surfaceContainerLowest
    }
    val raisedBackground = colors.surface

    val legacyTokens = MiuixTokens(
        pageBackground = pageBackground,
        cardBackground = cardBackground,
        elevatedCardBackground = raisedBackground,
        textPrimary = colors.onSurface,
        textSecondary = colors.onSurfaceVariant,
        success = success,
        warning = warning,
    )

    val uiTokens = LanghuanUiTokens(
        background = pageBackground,
        foreground = colors.onBackground,

        card = cardBackground,
        cardForeground = colors.onSurface,

        muted = colors.surfaceContainer,
        mutedForeground = colors.onSurfaceVariant,

        strong = colors.onSurface.copy(
            alpha = if (darkTheme) 0.92f else 0.88f,
        ),

        track = colors.onSurface.copy(
            alpha = if (darkTheme) 0.12f else 0.07f,
        ),

        border = colors.outlineVariant,

        input = colors.surfaceContainerHigh,

        primary = colors.primary,
        primaryForeground = colors.onPrimary,

        accent = colors.primaryContainer,
        accentForeground = colors.onPrimaryContainer,

        destructive = colors.error,
        destructiveForeground = colors.onError,

        success = success,
        successForeground = if (darkTheme) {
            Color(0xFF102D20)
        } else {
            Color.White
        },

        warning = warning,
        warningForeground = if (darkTheme) {
            Color(0xFF352508)
        } else {
            Color.White
        },

        ring = colors.primary.copy(
            alpha = if (darkTheme) 0.56f else 0.42f,
        ),

        warmSurface = if (darkTheme) {
            Color(0xFF211F1A)
        } else {
            Color(0xFFF4EFE5)
        },

        radiusSm = 12.dp,
        radiusMd = 16.dp,
        radiusLg = 20.dp,
        radiusXl = 28.dp,
    )

    MaterialTheme(
        colorScheme = colors,
        typography = LanghuanTypography,
        shapes = LanghuanShapes,
    ) {
        CompositionLocalProvider(
            LocalMiuixTokens provides legacyTokens,
            LocalLanghuanUiTokens provides uiTokens,
            content = content,
        )
    }
}
