package com.xiguli.langhuan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.xiguli.langhuan.ui.design.LanghuanUiTokens
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * 琅嬛主题。整个 App 只有这一个主题入口。
 *
 * 之前是两个文件并存：`Theme.kt` 里的 `LanghuanTheme`（materialkolor 动态取色，
 * 代码里零处调用）和 `LanghuanStableTheme.kt` 里的实际入口；而 11 个页面引用的
 * `LocalLanghuanTokens` 恰恰定义在那个没人调用的文件里。这里合并成一套。
 *
 * 配色见 [LanghuanLightColors]（羊皮墨蓝），尺度见 [LanghuanRadius]、
 * [LanghuanSpacing]、[LanghuanTypography]。
 *
 * 角色模型仍然沿用 shadcn 那一套（background / foreground / card / muted /
 * border / primary / accent / destructive / ring），32 个页面已经在读它，
 * 这次只换底下的颜色和尺度，不动角色本身。
 */

/** 用户可调的外观设置。 */
@Immutable
data class LanghuanAppearance(
    /** 跟随系统取色（Android 12+）。默认关闭：琅嬛要有稳定的视觉身份。 */
    val dynamicColor: Boolean = false,
    /** null = 跟随系统。 */
    val darkMode: Boolean? = null,
    /** OLED 纯黑省电模式。 */
    val amoledBlack: Boolean = false,
    /** 浮层是否使用模糊。低端机可关。 */
    val blurEnabled: Boolean = true,
    /**
     * 液态玻璃层。DESIGN.md §1：玻璃只用于浮在内容上方的控制层，
     * 不是所有卡片都做成玻璃。
     */
    val glassEnabled: Boolean = true,
)

/**
 * 早期页面用的语义 token。新页面请直接用 [LanghuanUiTokens]（shadcn 角色模型）
 * 或 `MaterialTheme.colorScheme`，这套只为存量页面保留。
 */
@Immutable
data class LanghuanTokens(
    val pageBackground: Color,
    val cardBackground: Color,
    val elevatedCardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
)

val LocalLanghuanTokens: ProvidableCompositionLocal<LanghuanTokens> =
    staticCompositionLocalOf {
        error("LanghuanTokens 未提供：把内容包在 LanghuanTheme { } 里")
    }

val LocalLanghuanAppearance: ProvidableCompositionLocal<LanghuanAppearance> =
    staticCompositionLocalOf { LanghuanAppearance() }

@Composable
fun LanghuanTheme(
    appearance: LanghuanAppearance = LanghuanAppearance(),
    content: @Composable () -> Unit,
) {
    val dark = appearance.darkMode ?: isSystemInDarkTheme()
    val context = LocalContext.current

    val base = when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> LanghuanDarkColors
        else -> LanghuanLightColors
    }

    val colors = if (dark && appearance.amoledBlack) {
        base.copy(
            background = Color.Black,
            surface = Color(0xFF0B0A08),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0B0A08),
        )
    } else {
        base
    }

    val success = if (dark) LanghuanSuccessDark else LanghuanSuccessLight
    val warning = if (dark) LanghuanWarnDark else LanghuanWarnLight

    val legacyTokens = LanghuanTokens(
        pageBackground = colors.background,
        cardBackground = colors.surfaceContainerLow,
        // 浅色底上「抬起」= 更接近象牙白；深色底上「抬起」= 更亮一档。
        // 两边不能都用 surfaceContainerLowest，那在深色下会比卡片更暗。
        elevatedCardBackground =
            if (dark) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
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
        // accent 是中性的悬停 / 选中底色，不是强调色。保持中性，
        // 否则页面上会出现第二种「强调」（DESIGN.md §3）。
        accent = colors.surfaceContainerHigh,
        accentForeground = colors.onSurface,
        destructive = colors.error,
        destructiveForeground = colors.onError,
        success = success,
        successForeground = if (dark) Color(0xFF12200C) else Color(0xFFFAF9F5),
        warning = warning,
        warningForeground = if (dark) Color(0xFF2E2409) else Color(0xFFFAF9F5),
        ring = colors.primary.copy(alpha = if (dark) .72f else .60f),
        warmSurface = colors.surfaceContainerLow,
        // 尺度统一到 DESIGN.md §4，不再用 shadcn 的 6 / 8 / 10 / 12dp
        radiusSm = LanghuanRadius.chip,
        radiusMd = LanghuanRadius.card,
        radiusLg = LanghuanRadius.panel,
        radiusXl = LanghuanRadius.sheet,
    )

    MaterialTheme(
        colorScheme = colors,
        shapes = LanghuanShapes,
        typography = LanghuanTypography,
    ) {
        CompositionLocalProvider(
            LocalLanghuanTokens provides legacyTokens,
            LocalLanghuanUiTokens provides uiTokens,
            LocalLanghuanAppearance provides appearance,
            content = content,
        )
    }
}
