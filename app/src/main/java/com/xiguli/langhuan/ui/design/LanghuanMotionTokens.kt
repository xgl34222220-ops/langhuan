package com.xiguli.langhuan.ui.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 琅嬛 Motion Tokens（2026-10 UI refresh）。
 *
 * 参考 transitions.dev 的 token 化动效：所有时长 / 曲线从这里取，页面不再散落 tween(xxx)。
 *
 * - 进入用 ease-out（快起慢停），退出用 ease-in 且更短（约为进入的 2/3），离开不拖泥带水。
 * - 只动 alpha / translation / scale（graphicsLayer），不动布局尺寸，避免重组和重新测量。
 * - 系统「移除动画」（ANIMATOR_DURATION_SCALE == 0）时，所有规格退化为 snap，内容立即可见。
 */
object LanghuanMotion {
    /** 按压反馈、图标切换：近乎无感。 */
    const val DURATION_INSTANT = 100

    /** 淡入淡出、颜色 / 选中态变化。 */
    const val DURATION_QUICK = 160

    /** 菜单、Sheet、弹窗内容进入。 */
    const val DURATION_STANDARD = 220

    /** 页面级切换（fade-through / 共享轴）。 */
    const val DURATION_EMPHASIZED = 300

    /** 退出时长：进入的约 2/3。 */
    fun exitDuration(enterMillis: Int): Int = (enterMillis * 2) / 3

    /** ease-out：cubic-bezier(0.23, 1, 0.32, 1)，进入与位移的默认曲线。 */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** ease-in：cubic-bezier(0.5, 0, 0.75, 0)，只用于退出。 */
    val EaseIn: Easing = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)

    /** ease-in-out：cubic-bezier(0.77, 0, 0.175, 1)，用于屏内位置互换（如指示条滑动）。 */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** 卡片 / 行的按压缩放。比 0.96 更克制，避免“果冻感”。 */
    const val PRESS_SCALE = 0.97f

    /** 弹窗 / 菜单进入时的起始缩放。 */
    const val POP_INITIAL_SCALE = 0.96f

    /** 列表项、面板进入时的位移距离。 */
    val RISE: Dp = 8.dp

    fun <T> enter(durationMillis: Int = DURATION_STANDARD, reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = EaseOut)

    fun <T> exit(enterMillis: Int = DURATION_STANDARD, reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(exitDuration(enterMillis), easing = EaseIn)

    fun <T> standard(durationMillis: Int = DURATION_QUICK, reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis, easing = EaseOut)

    /** 底部浮层（阅读菜单、Sheet）：上移 + 淡入。 */
    fun sheetEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else slideInVertically(tween<IntOffset>(DURATION_STANDARD, easing = EaseOut)) { it / 8 } +
            fadeIn(tween(DURATION_QUICK, easing = EaseOut))

    fun sheetExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None
        else slideOutVertically(tween<IntOffset>(exitDuration(DURATION_STANDARD), easing = EaseIn)) { it / 8 } +
            fadeOut(tween(exitDuration(DURATION_QUICK), easing = EaseIn))

    /** 弹窗 / 下拉面板：轻微放大 + 淡入（origin-aware 由调用方 transformOrigin 决定）。 */
    fun popEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else scaleIn(tween(DURATION_STANDARD, easing = EaseOut), initialScale = POP_INITIAL_SCALE) +
            fadeIn(tween(DURATION_QUICK, easing = EaseOut))

    /** 屏内展开区域（搜索框、整理面板）：自上方滑入。 */
    fun revealEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(tween(DURATION_QUICK, easing = EaseOut)) +
            slideInVertically(tween<IntOffset>(DURATION_STANDARD, easing = EaseOut)) { -it / 4 }

    fun revealExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None
        else fadeOut(tween(exitDuration(DURATION_QUICK), easing = EaseIn)) +
            slideOutVertically(tween<IntOffset>(exitDuration(DURATION_STANDARD), easing = EaseIn)) { -it / 4 }
}

/** 系统动画缩放为 0（开发者选项「移除动画」/ 无障碍「移除动画」）时为 true。由主题注入。 */
val LocalLanghuanReducedMotion = staticCompositionLocalOf { false }

/** 读取系统动画缩放；只在主题根部读取一次，不在列表项里反复查询 Settings。 */
@Composable
fun rememberLanghuanReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isLanghuanReducedMotion(context) }
}

internal fun isLanghuanReducedMotion(context: android.content.Context): Boolean = runCatching {
    android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}.getOrDefault(false)

/**
 * 弹窗 / 浮层首次出现时播放一次“轻放大 + 淡入”（或 [rise] 上移）。只写 graphicsLayer，不影响测量。
 * 减少动画时直接处于终态。
 */
fun Modifier.langhuanEnterOnMount(
    rise: Dp = 0.dp,
    initialScale: Float = LanghuanMotion.POP_INITIAL_SCALE,
    durationMillis: Int = LanghuanMotion.DURATION_STANDARD,
): Modifier = composed {
    val reduced = LocalLanghuanReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f)
        else progress.animateTo(1f, tween(durationMillis, easing = LanghuanMotion.EaseOut))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        val s = initialScale + (1f - initialScale) * p
        scaleX = s
        scaleY = s
        translationY = (1f - p) * rise.toPx()
    }
}

/** 按压缩放（无 ripple 的卡片、封面、行）。减少动画时不缩放。 */
fun Modifier.langhuanPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = LanghuanMotion.PRESS_SCALE,
): Modifier = composed {
    val reduced = LocalLanghuanReducedMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && !reduced) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = LanghuanMotion.standard<Float>(LanghuanMotion.DURATION_INSTANT, reduced),
        label = "langhuanPress",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** 颜色 / 透明度等状态动画的统一入口。 */
@Composable
fun <T> langhuanStateSpec(durationMillis: Int = LanghuanMotion.DURATION_QUICK): AnimationSpec<T> =
    LanghuanMotion.standard(durationMillis, LocalLanghuanReducedMotion.current)
