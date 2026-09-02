package com.xiguli.langhuan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle

@Immutable
data class LanghuanAppearance(
    val seedArgb: Int = 0xFFF4553D.toInt(),
    val monetEnabled: Boolean = true,
    val darkMode: Boolean? = null,
    val amoledBlack: Boolean = false,
    val blurEnabled: Boolean = true,
    val glassEnabled: Boolean = true,
)

@Immutable
data class MiuixTokens(
    val pageBackground: Color,
    val cardBackground: Color,
    val elevatedCardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color = Color(0xFF1E9E6A),
    val warning: Color = Color(0xFFD98A16),
)

val LocalMiuixTokens = staticCompositionLocalOf {
    MiuixTokens(
        pageBackground = Color(0xFFFFFFFF),
        cardBackground = Color(0xFFF5F5F7),
        elevatedCardBackground = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF1A1A1C),
        textSecondary = Color(0xFF9A9AA0),
    )
}

val LocalLanghuanAppearance = staticCompositionLocalOf { LanghuanAppearance() }

private val MiuixShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val MiuixTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = .4.sp),
)

@Composable
fun LanghuanTheme(
    appearance: LanghuanAppearance = LanghuanAppearance(),
    content: @Composable () -> Unit,
) {
    val dark = appearance.darkMode ?: isSystemInDarkTheme()
    val seed = if (appearance.monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Color(LocalResources.current.getColor(android.R.color.system_accent1_500, null))
    } else {
        Color(appearance.seedArgb)
    }
    DynamicMaterialTheme(
        seedColor = seed,
        useDarkTheme = dark,
        withAmoled = dark && appearance.amoledBlack,
        style = PaletteStyle.TonalSpot,
        shapes = MiuixShapes,
        typography = MiuixTypography,
        animate = true,
    ) {
        val scheme = MaterialTheme.colorScheme
        val pureBlack = dark && appearance.amoledBlack
        val tokens = MiuixTokens(
            pageBackground = when {
                pureBlack -> Color.Black
                dark -> scheme.surfaceContainerLowest
                else -> Color(0xFFFFFFFF)
            },
            cardBackground = when {
                pureBlack -> Color(0xFF080808)
                dark -> scheme.surfaceContainer
                else -> scheme.surfaceContainerLowest
            },
            elevatedCardBackground = when {
                pureBlack -> Color(0xFF111111)
                dark -> scheme.surfaceContainerHigh
                else -> scheme.surfaceContainer
            },
            textPrimary = scheme.onSurface,
            textSecondary = scheme.onSurfaceVariant,
        )
        CompositionLocalProvider(
            LocalMiuixTokens provides tokens,
            LocalLanghuanAppearance provides appearance,
            content = content,
        )
    }
}
