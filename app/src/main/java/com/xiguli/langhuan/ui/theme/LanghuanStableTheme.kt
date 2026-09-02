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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FallbackLightColors = lightColorScheme(
    primary = Color(0xFF52698F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E8F5),
    onPrimaryContainer = Color(0xFF172A47),
    secondary = Color(0xFF687386),
    secondaryContainer = Color(0xFFE8EBF0),
    tertiary = Color(0xFF71697A),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1D1E21),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF1D1E21),
    surfaceVariant = Color(0xFFE7E9ED),
    onSurfaceVariant = Color(0xFF70747C),
    outline = Color(0xFF8A8E96),
    outlineVariant = Color(0xFFD8DADE),
)

private val FallbackDarkColors = darkColorScheme(
    primary = Color(0xFFB9C8E5),
    onPrimary = Color(0xFF233A5E),
    primaryContainer = Color(0xFF344C70),
    onPrimaryContainer = Color(0xFFDCE6F9),
    secondary = Color(0xFFC5CAD3),
    secondaryContainer = Color(0xFF41464F),
    tertiary = Color(0xFFD5C7DB),
    background = Color(0xFF111214),
    onBackground = Color(0xFFE7E8EB),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFE7E8EB),
    surfaceVariant = Color(0xFF292B2F),
    onSurfaceVariant = Color(0xFFB8BBC2),
    outline = Color(0xFF8E929A),
    outlineVariant = Color(0xFF42454B),
)

private val StableShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val StableTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
)

/**
 * Stable theme used only after StartupDatabaseGate succeeds.
 * Android 12+ follows the system Monet palette through Material3's platform API; older devices
 * use a restrained blue-grey fallback. No Miuix or MaterialKolor runtime is touched here.
 */
@Composable
fun LanghuanStableTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) FallbackDarkColors else FallbackLightColors
    }

    MaterialTheme(
        colorScheme = colors,
        shapes = StableShapes,
        typography = StableTypography,
        content = content,
    )
}
