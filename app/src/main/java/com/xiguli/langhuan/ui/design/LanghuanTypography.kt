package com.xiguli.langhuan.ui.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 琅嬛 Typography Design System。
 *
 * 设计目标：
 * 1. 页面大标题、书名、章节名等内容型标题使用 Serif，保留书卷气。
 * 2. 正文说明、按钮、设置项、状态信息等 UI 内容使用 Sans。
 * 3. 字号、行高与字间距以中文界面为主要基准。
 * 4. Reader V30 小说正文拥有独立排版体系。
 * 5. 当前不依赖额外字体资源，优先保证设备与 Preview 稳定性。
 */

val LanghuanSerif: FontFamily = FontFamily.Serif

val LanghuanSans: FontFamily = FontFamily.SansSerif

val LanghuanTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
    ),

    displayMedium = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.7).sp,
    ),

    displaySmall = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 39.sp,
        letterSpacing = (-0.6).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 37.sp,
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

@Immutable
object LanghuanTextStyles {
    val pageTitle: TextStyle = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.55).sp,
    )

    val detailTitle: TextStyle = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.35).sp,
    )

    val bookTitle: TextStyle = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.05.sp,
    )

    val chapterTitle: TextStyle = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    )

    val sectionTitle: TextStyle = TextStyle(
        fontFamily = LanghuanSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.1.sp,
    )

    val prose: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
    )

    val body: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp,
    )

    val secondary: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.15.sp,
    )

    val button: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )

    val navigation: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    )

    val badge: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    )

    val caption: TextStyle = TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
    )
}

fun langhuanBodyLineHeight(
    fontSize: TextUnit,
    multiplier: Float = 1.55f,
): TextUnit {
    if (fontSize == TextUnit.Unspecified) {
        return TextUnit.Unspecified
    }

    if (!fontSize.isSp) {
        return TextUnit.Unspecified
    }

    return (fontSize.value * multiplier).sp
}

fun langhuanProseStyle(
    fontSize: TextUnit = 16.sp,
    lineHeightMultiplier: Float = 1.62f,
): TextStyle {
    return TextStyle(
        fontFamily = LanghuanSans,
        fontWeight = FontWeight.Normal,
        fontSize = fontSize,
        lineHeight = langhuanBodyLineHeight(
            fontSize = fontSize,
            multiplier = lineHeightMultiplier,
        ),
        letterSpacing = 0.15.sp,
    )
}
