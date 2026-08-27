package com.xiguli.langhuan.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF211A2E)
val Violet = Color(0xFF7257D8)
val Iris = Color(0xFF9D88ED)
val Mist = Color(0xFFF4F0FC)
val BlueMist = Color(0xFFE9F2FF)
val Glass = Color(0xDDFBF9FF)
val Success = Color(0xFF2E7D68)
val Warning = Color(0xFFB26A18)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0FF),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF52688D),
    background = Mist,
    onBackground = Ink,
    surface = Glass,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE7F6),
    outline = Color(0x33705E86),
)

private val DarkColors = darkColorScheme(
    primary = Iris,
    background = Color(0xFF15111D),
    surface = Color(0xDD211A2E),
    onSurface = Color(0xFFF6F0FF),
)

@Composable
fun LanghuanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = LanghuanTypography,
        content = content,
    )
}

