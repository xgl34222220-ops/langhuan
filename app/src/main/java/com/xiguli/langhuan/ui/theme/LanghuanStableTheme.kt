package com.xiguli.langhuan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val StableLightColors = lightColorScheme(
    primary = Color(0xFF7457D5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    onPrimaryContainer = Color(0xFF29195B),
    secondary = Color(0xFF675E78),
    secondaryContainer = Color(0xFFECE5F6),
    tertiary = Color(0xFF8A5266),
    background = Color(0xFFF7F5FB),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFF7F5FB),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE8E4ED),
    onSurfaceVariant = Color(0xFF77727F),
    outline = Color(0xFF8E8996),
    outlineVariant = Color(0xFFDAD5E0),
)

private val StableDarkColors = darkColorScheme(
    primary = Color(0xFFCDBDFF),
    onPrimary = Color(0xFF3E267D),
    primaryContainer = Color(0xFF513B93),
    onPrimaryContainer = Color(0xFFE9E1FF),
    secondary = Color(0xFFD0C5DE),
    secondaryContainer = Color(0xFF4A4355),
    tertiary = Color(0xFFF1B7CB),
    background = Color(0xFF121116),
    onBackground = Color(0xFFE8E3EA),
    surface = Color(0xFF121116),
    onSurface = Color(0xFFE8E3EA),
    surfaceVariant = Color(0xFF2A2830),
    onSurfaceVariant = Color(0xFFBBB5C0),
    outline = Color(0xFF958F9A),
    outlineVariant = Color(0xFF46424B),
)

private val StableShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val StableTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

/**
 * Stable visual theme used only after StartupDatabaseGate succeeds.
 * It deliberately avoids MaterialKolor/Miuix runtime initialization on the launcher path while
 * restoring the rounded, layered visual language expected by the rest of Langhuan.
 */
@Composable
fun LanghuanStableTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) StableDarkColors else StableLightColors,
        shapes = StableShapes,
        typography = StableTypography,
        content = content,
    )
}
