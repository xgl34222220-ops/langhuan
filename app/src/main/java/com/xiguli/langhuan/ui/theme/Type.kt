package com.xiguli.langhuan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 字体层级，按 DESIGN.md §5 的区间定死：
 *
 * - 页面标题 28–32sp / Bold
 * - 作品标题、章节标题 24–28sp / Bold
 * - 正文默认 18–20sp，行高 1.70–1.95 ×
 * - 普通正文 UI 15–17sp
 * - 辅助信息 12–14sp
 *
 * 原来 shadcn 那套 `bodyLarge` 是 15sp / 行高 1.53×，低于规范下限不少。阅读页
 * 第一观感的差距主要来自这里，不是配色。这里抬到 19sp / 1.84×。
 *
 * 字族只用系统内置：衬线走 [FontFamily.Serif]（Android 上映射到 Noto Serif CJK），
 * 不内置任何字体文件（DESIGN.md §5）。kami 的「衬线承载层级」用衬线标题 + 衬线
 * 阅读正文实现；UI 文字仍用系统无衬线，避免整屏衬线在低端机上的渲染成本。
 *
 * kami 原包锁定单一 W500 字重、禁用 bold；Android 各 ROM 对 CJK Medium 的支持
 * 不一致，容易触发合成字重，所以这里按 DESIGN.md §5 走 Bold 标题 + Regular 正文。
 */

private val Serif = FontFamily.Serif
private val Sans = FontFamily.Default

/** CJK 要按行高居中排版，否则中文在行框里会偏上。 */
private val CjkLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun cjk(
    family: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = CjkLineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

internal val LanghuanTypography = Typography(
    // 页面级大标题：一个页面最多一个（DESIGN.md §5）。
    displayLarge = cjk(Serif, 36, 46, FontWeight.Bold, -0.4),
    displayMedium = cjk(Serif, 33, 42, FontWeight.Bold, -0.3),
    displaySmall = cjk(Serif, 30, 39, FontWeight.Bold, -0.2),

    // 作品标题 / 章节标题：24–28sp。
    headlineLarge = cjk(Serif, 28, 37, FontWeight.Bold),
    headlineMedium = cjk(Serif, 25, 34, FontWeight.Bold),
    headlineSmall = cjk(Serif, 22, 30, FontWeight.SemiBold),

    // 区块标题、列表主文案。
    titleLarge = cjk(Sans, 19, 26, FontWeight.SemiBold),
    titleMedium = cjk(Sans, 17, 24, FontWeight.SemiBold),
    titleSmall = cjk(Sans, 15, 21, FontWeight.SemiBold),

    // 正文：bodyLarge 是阅读正文，衬线 + 宽行高。
    bodyLarge = cjk(Serif, 19, 35),
    bodyMedium = cjk(Sans, 16, 27),
    bodySmall = cjk(Sans, 13, 21),

    // 按钮与标签。
    labelLarge = cjk(Sans, 15, 20, FontWeight.SemiBold),
    labelMedium = cjk(Sans, 13, 17, FontWeight.Medium, 0.2),
    labelSmall = cjk(Sans, 12, 16, FontWeight.Medium, 0.4),
)

/**
 * 阅读页正文样式。阅读器的字号 / 行距由用户在阅读设置里调，所以不写死在
 * Typography 里，而是按用户设置生成。
 *
 * @param fontSizeSp 用户选择的正文字号，规范区间 18–20sp（DESIGN.md §5）。
 * @param lineHeightMultiplier 行高倍数，规范区间 1.70–1.95。
 * @param serif 是否使用衬线正文。
 */
fun readingTextStyle(
    fontSizeSp: Int = 19,
    lineHeightMultiplier: Float = 1.84f,
    serif: Boolean = true,
): TextStyle {
    val size = fontSizeSp.coerceIn(14, 28)
    val multiplier = lineHeightMultiplier.coerceIn(1.5f, 2.2f)
    return TextStyle(
        fontFamily = if (serif) Serif else Sans,
        fontSize = size.sp,
        lineHeight = (size * multiplier).sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = CjkLineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}
