package com.xiguli.langhuan.ui.design

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * V103 page motion, measured from the reference recording: a full-width horizontal push. The
 * incoming page and the outgoing page stay attached (no parallax, no dim, no shadow) and move
 * over ≈320 ms on a fast-start, long-tail curve (fit: cubic-bezier(0.3, 0.3, 0.1, 1)).
 */
object PagePushV103 {
    const val DURATION = 320
    val Easing = CubicBezierEasing(0.3f, 0.3f, 0.1f, 1f)

    fun <T> spec(durationMillis: Int = DURATION) = tween<T>(durationMillis, easing = Easing)

    /** Remaining time for a push that resumes from [fraction] of the way (0…1 left to travel). */
    fun remaining(fraction: Float): Int = (DURATION * fraction.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(120)

    /** Translation of the page underneath (fraction of the width) when the top page is [progress] in. */
    fun baseOffset(progress: Float): Float = -progress.coerceIn(0f, 1f)

    /** Translation of the top page (fraction of the width) when it is [progress] in. */
    fun topOffset(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)
}

/** Full-width push for [androidx.compose.animation.AnimatedContent]: forward slides left, back slides right. */
fun <S> AnimatedContentTransitionScope<S>.pagePushV103(forward: Boolean): ContentTransform {
    val spec = PagePushV103.spec<IntOffset>()
    return if (forward) {
        slideInHorizontally(spec) { it } togetherWith slideOutHorizontally(spec) { -it }
    } else {
        slideInHorizontally(spec) { -it } togetherWith slideOutHorizontally(spec) { it }
    }
}

/**
 * A two-level page stack: [base] is always composed (keeps its scroll and pager state), [top]
 * is pushed over it when [topKey] is non-null. System back — including the Android 14+
 * predictive back swipe — drags the top page off with the finger, the base following attached;
 * releasing past the commit point calls [onPop], cancelling springs it back.
 */
@Composable
fun PagePushStackV103(
    topKey: String?,
    onPop: () -> Unit,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    top: @Composable (String) -> Unit,
) {
    var shown by remember { mutableStateOf(topKey) }
    val progress = remember { Animatable(if (topKey != null) 1f else 0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(topKey) {
        if (topKey != null) {
            if (shown != null && shown != topKey && progress.value >= 0.999f) {
                shown = topKey
                return@LaunchedEffect
            }
            shown = topKey
            progress.animateTo(1f, PagePushV103.spec(PagePushV103.remaining(1f - progress.value)))
        } else if (shown != null) {
            progress.animateTo(0f, PagePushV103.spec(PagePushV103.remaining(progress.value)))
            shown = null
        }
    }
    val covered by remember { derivedStateOf { shown != null && progress.value >= 0.999f } }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = PagePushV103.baseOffset(progress.value) * size.width }
                .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier),
        ) { base() }
        // Registered after the base page and before the top page: the top page's own nested
        // handlers (sub-pages, dialogs) still win, the base page's never fire while covered.
        PredictiveBackHandler(enabled = topKey != null) { events ->
            try {
                events.collect { event -> progress.snapTo(1f - event.progress) }
                onPop()
            } catch (cancel: CancellationException) {
                scope.launch { progress.animateTo(1f, PagePushV103.spec(PagePushV103.remaining(1f - progress.value))) }
                throw cancel
            }
        }
        shown?.let { key ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = PagePushV103.topOffset(progress.value) * size.width },
            ) { top(key) }
        }
    }
}
