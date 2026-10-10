package com.xiguli.langhuan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanDarkUiTokens
import com.xiguli.langhuan.ui.design.LanghuanLightUiTokens
import com.xiguli.langhuan.ui.design.LanghuanTypography
import com.xiguli.langhuan.ui.design.LocalLanghuanReducedMotion
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.rememberLanghuanReducedMotion

/**
 * 琅嬛全局主题模式。
 *
 * FOLLOW_SYSTEM
 * 跟随 Android 系统浅色 / 深色设置。
 *
 * LIGHT
 * 强制使用琅嬛 v3 浅色主题。
 *
 * DARK
 * 强制使用琅嬛 v3 深色主题。
 */
enum class LanghuanThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}


/* -------------------------------------------------------------------------- */
/*                              Material Colors                               */
/* -------------------------------------------------------------------------- */

/**
 * Material3 仍然是大量现有 Compose 页面与系统组件的基础，
 * 因此这里把 Claude v3 的语义 Token 映射到 Material ColorScheme。
 *
 * 所有核心颜色均直接来自 LanghuanDesignTokens.kt，
 * 不另起一套品牌颜色。
 *
 * v3 的真正 UI 组件应优先读取 LocalLanghuanUiTokens，
 * MaterialTheme.colorScheme 主要负责兼容：
 *
 * - Dialog
 * - Snackbar
 * - TextField
 * - Material Button
 * - ProgressIndicator
 * - 尚未迁移完成的旧页面
 */


/**
 * 浅色主题（2026-10 zinc 中性色）。
 *
 * background #FAFAFA · card #FFFFFF · input #F4F4F5 · border #E4E4E7
 * foreground #18181B · secondary #3F3F46 · muted #5C5C66
 * primary #1E6A5A（唯一强调色）· gold #A9681C（稀有语义）· destructive #B03A2B
 */
private val LanghuanLightColorScheme = lightColorScheme(

    /* Primary */
    primary = LanghuanLightUiTokens.primary,

    /*
     * v3 Token 表没有单独定义 primaryForeground。
     * 这里直接复用现有 card 白色作为实色玉青按钮前景，
     * 不新增额外品牌色。
     */
    onPrimary = LanghuanLightUiTokens.card,

    primaryContainer = LanghuanLightUiTokens.accent,
    onPrimaryContainer = LanghuanLightUiTokens.accentForeground,


    /* Secondary / Gold */
    secondary = LanghuanLightUiTokens.gold,

    /*
     * Gold 的语义文字色已在 v3 表中定义。
     * Material secondary 实色面暂以 card 作为高对比前景；
     * goldForeground 仍通过 LocalLanghuanUiTokens.goldForeground
     * 用于文字型 Gold 强调。
     */
    onSecondary = LanghuanLightUiTokens.card,

    secondaryContainer = LanghuanLightUiTokens.goldContainer,
    onSecondaryContainer = LanghuanLightUiTokens.goldForeground,


    /* Tertiary */
    tertiary = LanghuanLightUiTokens.gold,
    onTertiary = LanghuanLightUiTokens.card,

    tertiaryContainer = LanghuanLightUiTokens.goldContainer,
    onTertiaryContainer = LanghuanLightUiTokens.goldForeground,


    /* Background */
    background = LanghuanLightUiTokens.background,
    onBackground = LanghuanLightUiTokens.foreground,


    /* Surface */
    surface = LanghuanLightUiTokens.card,
    onSurface = LanghuanLightUiTokens.foreground,

    surfaceVariant = LanghuanLightUiTokens.input,
    onSurfaceVariant = LanghuanLightUiTokens.secondaryForeground,


    /* Material 3 surface hierarchy */
    surfaceContainerLowest = LanghuanLightUiTokens.card,

    surfaceContainerLow = LanghuanLightUiTokens.background,

    surfaceContainer = LanghuanLightUiTokens.input,

    surfaceContainerHigh = LanghuanLightUiTokens.input,

    surfaceContainerHighest = LanghuanLightUiTokens.input,


    /* Border */
    outline = LanghuanLightUiTokens.border,
    outlineVariant = LanghuanLightUiTokens.border,


    /* Error / Destructive */
    error = LanghuanLightUiTokens.destructive,
    onError = LanghuanLightUiTokens.destructiveForeground,

    errorContainer = LanghuanLightUiTokens.destructive.copy(alpha = 0.10f),
    onErrorContainer = LanghuanLightUiTokens.destructive,


    /* Inverse */
    inverseSurface = LanghuanDarkUiTokens.card,
    inverseOnSurface = LanghuanDarkUiTokens.foreground,
    inversePrimary = LanghuanDarkUiTokens.primary,


    /* Misc */
    scrim = Color.Black,
)


/**
 * 深色主题（2026-10 zinc 中性色）。
 *
 * background #09090B · card #131316 · input #202024 · border #2A2A2F
 * foreground #FAFAFA · secondary #D4D4D8 · muted #A1A1AA
 * primary #7CCAB3 · gold #D8A45E · destructive #F08C7C
 */
private val LanghuanDarkColorScheme = darkColorScheme(

    /* Primary */
    primary = LanghuanDarkUiTokens.primary,

    /*
     * 深色 primary 本身较亮，
     * 直接复用 v3 background 作为高对比前景，
     * 不新增额外色值。
     */
    onPrimary = LanghuanDarkUiTokens.background,

    primaryContainer = LanghuanDarkUiTokens.accent,
    onPrimaryContainer = LanghuanDarkUiTokens.accentForeground,


    /* Secondary / Gold */
    secondary = LanghuanDarkUiTokens.gold,
    onSecondary = LanghuanDarkUiTokens.background,

    secondaryContainer = LanghuanDarkUiTokens.goldContainer,
    onSecondaryContainer = LanghuanDarkUiTokens.goldForeground,


    /* Tertiary */
    tertiary = LanghuanDarkUiTokens.gold,
    onTertiary = LanghuanDarkUiTokens.background,

    tertiaryContainer = LanghuanDarkUiTokens.goldContainer,
    onTertiaryContainer = LanghuanDarkUiTokens.goldForeground,


    /* Background */
    background = LanghuanDarkUiTokens.background,
    onBackground = LanghuanDarkUiTokens.foreground,


    /* Surface */
    surface = LanghuanDarkUiTokens.card,
    onSurface = LanghuanDarkUiTokens.foreground,

    surfaceVariant = LanghuanDarkUiTokens.input,
    onSurfaceVariant = LanghuanDarkUiTokens.secondaryForeground,


    /* Material 3 surface hierarchy */
    surfaceContainerLowest = LanghuanDarkUiTokens.background,

    surfaceContainerLow = LanghuanDarkUiTokens.card,

    surfaceContainer = LanghuanDarkUiTokens.card,

    surfaceContainerHigh = LanghuanDarkUiTokens.input,

    surfaceContainerHighest = LanghuanDarkUiTokens.input,


    /* Border */
    outline = LanghuanDarkUiTokens.border,
    outlineVariant = LanghuanDarkUiTokens.border,


    /* Error / Destructive */
    error = LanghuanDarkUiTokens.destructive,
    onError = LanghuanDarkUiTokens.destructiveForeground,

    errorContainer = LanghuanDarkUiTokens.destructive.copy(alpha = 0.14f),
    onErrorContainer = LanghuanDarkUiTokens.destructive,


    /* Inverse */
    inverseSurface = LanghuanLightUiTokens.card,
    inverseOnSurface = LanghuanLightUiTokens.foreground,
    inversePrimary = LanghuanLightUiTokens.primary,


    /* Misc */
    scrim = Color.Black,
)


/* -------------------------------------------------------------------------- */
/*                                  Shapes                                    */
/* -------------------------------------------------------------------------- */

/**
 * Claude v3 只保留四级圆角：
 *
 * 8dp
 * 12dp
 * 16dp
 * 24dp
 *
 * Material3 本身有五级 Shapes，因此 extraSmall 与 small
 * 都映射到最小 8dp 档，不再产生第五种圆角。
 *
 * 禁止重新引入：
 *
 * - 20dp
 * - 28dp
 * - 30dp
 *
 * 等旧档位。
 */
private val LanghuanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)


/* -------------------------------------------------------------------------- */
/*                               Theme Resolver                               */
/* -------------------------------------------------------------------------- */

/**
 * 根据用户主题设置计算当前是否使用深色主题。
 */
@Composable
private fun resolveLanghuanDarkTheme(
    themeMode: LanghuanThemeMode,
): Boolean {
    return when (themeMode) {
        LanghuanThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        LanghuanThemeMode.LIGHT -> false
        LanghuanThemeMode.DARK -> true
    }
}


/* -------------------------------------------------------------------------- */
/*                              Stable Theme                                  */
/* -------------------------------------------------------------------------- */

/**
 * 琅嬛全局稳定主题 · v3。
 *
 * 默认行为：
 *
 * themeMode = FOLLOW_SYSTEM
 *
 * 即保持 Android 用户预期：
 * 系统深色时使用深色，
 * 系统浅色时使用浅色。
 *
 * 设置页后续可以直接传：
 *
 * LanghuanStableTheme(
 *     themeMode = LanghuanThemeMode.LIGHT
 * )
 *
 * 或：
 *
 * LanghuanStableTheme(
 *     themeMode = LanghuanThemeMode.DARK
 * )
 *
 * v3 已取消动态壁纸取色。
 *
 * 原因：
 * 琅嬛拥有固定的纸白 / 墨色 / 玉青 / 赤金视觉系统，
 * 如果继续使用 Android Dynamic Color，
 * 不同用户设备会出现完全不同的品牌颜色。
 */
@Composable
fun LanghuanStableTheme(
    themeMode: LanghuanThemeMode = LanghuanThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveLanghuanDarkTheme(themeMode)

    val uiTokens = if (darkTheme) {
        LanghuanDarkUiTokens
    } else {
        LanghuanLightUiTokens
    }

    val materialColors = if (darkTheme) {
        LanghuanDarkColorScheme
    } else {
        LanghuanLightColorScheme
    }


    /*
     * 旧页面兼容层。
     *
     * 当前工程仍有部分页面使用 LocalMiuixTokens。
     * 在所有页面完成 v3 Design System 迁移前，
     * 这里继续向旧组件提供与 v3 一致的基础色。
     *
     * 不再让旧页面返回蓝色 LuoShu 风格。
     */
    val legacyTokens = MiuixTokens(
        pageBackground = uiTokens.background,

        cardBackground = uiTokens.card,

        /*
         * v3 明确要求取消投影分层。
         * 因此旧 elevatedCard 也不再制造另一种浮起 Surface，
         * 直接保持 card。
         */
        elevatedCardBackground = uiTokens.card,

        textPrimary = uiTokens.foreground,

        /*
         * 旧 Token 只有一个 secondary text，
         * 优先映射到新的二级文字。
         */
        textSecondary = uiTokens.secondaryForeground,

        /*
         * MiuixTokens 暂时仍需要 success / warning。
         * v3 Claude Token 表没有重新定义这两项，
         * 因此兼容层不创造新的品牌色：
         *
         * success → primary
         * warning → gold
         *
         * 新版组件不应依赖这两个旧语义。
         */
        success = uiTokens.primary,
        warning = uiTokens.gold,
    )


    // 系统「移除动画」只在主题根部读取一次，所有动效 token 据此退化为 snap。
    val reducedMotion = rememberLanghuanReducedMotion()

    MaterialTheme(
        colorScheme = materialColors,
        typography = LanghuanTypography,
        shapes = LanghuanShapes,
    ) {
        CompositionLocalProvider(
            LocalMiuixTokens provides legacyTokens,
            LocalLanghuanUiTokens provides uiTokens,
            LocalLanghuanReducedMotion provides reducedMotion,
        ) {
            content()
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                          Theme Mode Persistence                            */
/* -------------------------------------------------------------------------- */

/**
 * 主题三档（跟随系统 / 浅色 / 深色）的 SharedPreferences 持久化。
 *
 * 与设置页的外观卡片、MainActivity 的 LanghuanStableTheme 接线配合。
 * v3 新增：此前工程没有手动主题切换。
 */
internal object LanghuanThemeModeStoreV50 {
    private const val PREFS = "langhuan_theme_v50"
    private const val KEY_MODE = "theme_mode"

    fun load(context: android.content.Context): LanghuanThemeMode {
        val name = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        return runCatching { if (name == null) LanghuanThemeMode.FOLLOW_SYSTEM else LanghuanThemeMode.valueOf(name) }
            .getOrDefault(LanghuanThemeMode.FOLLOW_SYSTEM)
    }

    fun save(context: android.content.Context, mode: LanghuanThemeMode) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}

/**
 * 进程内主题状态。设置页写入后立即生效，无需重启。
 */
internal object LanghuanThemeModeStateV50 {
    var current: LanghuanThemeMode by androidx.compose.runtime.mutableStateOf(LanghuanThemeMode.FOLLOW_SYSTEM)
        private set

    fun init(context: android.content.Context) {
        current = LanghuanThemeModeStoreV50.load(context)
    }

    fun set(context: android.content.Context, mode: LanghuanThemeMode) {
        LanghuanThemeModeStoreV50.save(context, mode)
        current = mode
    }
}
