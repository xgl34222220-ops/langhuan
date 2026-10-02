package com.xiguli.langhuan.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 琅嬛全局 UI 语义 Token。
 *
 * 这里不直接描述某个具体页面的颜色，而是描述 UI 中的“角色”：
 *
 * - background：页面背景
 * - foreground：页面主文本
 * - card：普通内容容器
 * - muted：弱层级背景
 * - primary：品牌主色 / 主要操作
 * - accent：低强调选中态
 * - warmSurface：带书卷气的暖色内容表面
 *
 * 所有非阅读正文页面应优先使用语义 Token，
 * 而不是在各页面直接硬编码 Color。
 *
 * Reader V30 的正文纸张、正文颜色等拥有独立主题体系，
 * 不应该被这里强制覆盖。
 */
@Immutable
data class LanghuanUiTokens(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val strong: Color,
    val track: Color,
    val border: Color,
    val input: Color,
    val primary: Color,
    val primaryForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val success: Color,
    val successForeground: Color,
    val warning: Color,
    val warningForeground: Color,
    val ring: Color,
    val warmSurface: Color,
    val radiusSm: Dp = 12.dp,
    val radiusMd: Dp = 16.dp,
    val radiusLg: Dp = 20.dp,
    val radiusXl: Dp = 28.dp,
)

private val DefaultLanghuanUiTokens = LanghuanUiTokens(
    background = Color(0xFFF7F5F0),
    foreground = Color(0xFF22221F),

    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF22221F),

    muted = Color(0xFFF1EFE9),
    mutedForeground = Color(0xFF5F605A),

    strong = Color(0xE122221F),

    track = Color(0x1222221F),

    border = Color(0xFFDDDAD3),

    input = Color(0xFFEAE7E0),

    primary = Color(0xFF456A61),
    primaryForeground = Color(0xFFFFFFFF),

    accent = Color(0xFFDDE9E4),
    accentForeground = Color(0xFF294A42),

    destructive = Color(0xFFB3261E),
    destructiveForeground = Color(0xFFFFFFFF),

    success = Color(0xFF377B58),
    successForeground = Color(0xFFFFFFFF),

    warning = Color(0xFF9A6B24),
    warningForeground = Color(0xFFFFFFFF),

    ring = Color(0x6B456A61),

    warmSurface = Color(0xFFF4EFE5),

    radiusSm = 12.dp,
    radiusMd = 16.dp,
    radiusLg = 20.dp,
    radiusXl = 28.dp,
)

val LocalLanghuanUiTokens = staticCompositionLocalOf {
    DefaultLanghuanUiTokens
}
