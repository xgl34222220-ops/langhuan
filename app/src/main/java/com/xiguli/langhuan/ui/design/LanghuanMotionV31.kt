package com.xiguli.langhuan.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One motion vocabulary for the whole app. Screens used to pick their own durations and
 * easings; keeping them here makes every press, entrance and state change feel related.
 */
object LanghuanMotionV31 {
    const val FAST = 160
    const val MEDIUM = 240
    const val SLOW = 340
    const val STAGGER = 38
    const val STAGGER_MAX = 10

    /** Springy but settled: used for presses and selection. */
    fun <T> press() = spring<T>(dampingRatio = .62f, stiffness = Spring.StiffnessMedium)

    /** Soft spring for things that move into place. */
    fun <T> settle() = spring<T>(dampingRatio = .86f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Remembers which list items already played their entrance, so scrolling an item back into view
 * does not replay it. Hold one per screen.
 */
class LanghuanEnterRegistryV31 {
    private val seen = HashSet<Any>()
    internal fun firstTime(key: Any): Boolean = seen.add(key)

    /** Marks content that is already on screen (restored history) so it never animates in. */
    fun markSeenV31(key: Any) {
        seen.add(key)
    }
}

@Composable
fun rememberEnterRegistryV31(): LanghuanEnterRegistryV31 = remember { LanghuanEnterRegistryV31() }

/**
 * Fades and lifts content into place the first time [key] is shown. [index] staggers siblings;
 * anything past [LanghuanMotionV31.STAGGER_MAX] enters together so long lists never feel slow.
 */
fun Modifier.enterOnceV31(
    registry: LanghuanEnterRegistryV31,
    key: Any,
    index: Int = 0,
    rise: Dp = 14.dp,
): Modifier = composed {
    val first = remember(key) { registry.firstTime(key) }
    val progress = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            delay((index.coerceIn(0, LanghuanMotionV31.STAGGER_MAX) * LanghuanMotionV31.STAGGER).toLong())
            progress.animateTo(1f, tween(LanghuanMotionV31.SLOW, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise.toPx()
    }
}

/** Shrinks slightly while pressed. Pair with an interaction source that has no ripple. */
fun Modifier.pressScaleV31(interactionSource: MutableInteractionSource, pressedScale: Float = .96f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, LanghuanMotionV31.press(), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Tactile click: press scale, no ripple, light haptic on long press. The default for cards,
 * covers and tiles, where a ripple looks cheap on rounded, shadowed surfaces.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.springClickV31(
    enabled: Boolean = true,
    pressedScale: Float = .96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    this
        .pressScaleV31(interaction, pressedScale)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let { longClick ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longClick()
                }
            },
            onClick = onClick,
        )
}

/** Moving highlight used by skeleton placeholders. */
fun Modifier.shimmerV31(base: Color, highlight: Color): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    drawWithContent {
        drawRect(base)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(w * x - w * .5f, 0f),
                end = Offset(w * x + w * .5f, size.height),
            ),
        )
        drawContent()
    }
}

/** A placeholder block that matches the shape of the content it stands in for. */
@Composable
fun LanghuanSkeletonV31(modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val t = LocalLanghuanUiTokens.current
    Box(modifier.clip(shape).shimmerV31(t.muted, t.card.copy(alpha = .75f)))
}

/** Three dots that breathe in sequence; the "AI is thinking" signal. */
@Composable
fun LanghuanTypingDotsV31(color: Color, modifier: Modifier = Modifier, dot: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "typingPhase",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            if (index > 0) Spacer(Modifier.width(dot * .7f))
            val distance = kotlin.math.abs(((phase - index + 3f) % 3f) - .5f).coerceAtMost(1.5f)
            val lift = (1f - (distance / 1.5f)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer {
                        translationY = -lift * dot.toPx() * .55f
                        alpha = .35f + .65f * lift
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Soft blinking caret shown at the end of streaming text. */
@Composable
fun LanghuanCaretV31(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "caret")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = .15f,
        animationSpec = infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    Box(modifier.size(width = 2.dp, height = 16.dp).graphicsLayer { alpha = blink }.background(color))
}
