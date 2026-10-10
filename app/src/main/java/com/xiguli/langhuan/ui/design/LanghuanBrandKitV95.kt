package com.xiguli.langhuan.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

/*
 * V95 brand kit: the 琅嬛 mark (月洞门 over an open book, with a rising moon) for in-app use,
 * iOS-style large titles that collapse on scroll, illustrated empty states, and the tab-reselect
 * signal the bottom bar sends to scroll a tab back to its top.
 */

/** A bottom-bar tab was tapped while already selected. [token] increments on every reselect. */
@Immutable
data class TabReselectV95(val key: String = "", val token: Int = 0)

/**
 * Process-wide reselect signal written by the bottom bar (kept here, not in the root router, so the
 * bar and the tab pages agree without threading a callback through every host).
 */
object TabReselectBusV95 {
    var current by mutableStateOf(TabReselectV95())
        private set

    fun reselect(key: String) {
        current = TabReselectV95(key, current.token + 1)
    }
}

/** Runs [onReselect] (typically `animateScrollToItem(0)`) each time the tab [key] is reselected. */
@Composable
fun OnTabReselectV95(key: String, onReselect: suspend () -> Unit) {
    val signal = TabReselectBusV95.current
    val latest by rememberUpdatedState(onReselect)
    val baseline = remember(key) { signal.token }
    LaunchedEffect(signal) {
        if (signal.key == key && signal.token != baseline) latest()
    }
}

private const val MARK_RING_V95 = "M76.32,51.83 A22.35,22.35 0 0,0 31.68,51.83 A1.35,1.35 0 0,0 34.38,51.97 A19.65,19.65 0 0,1 73.62,51.97 A1.35,1.35 0 0,0 76.32,51.83 Z"
private const val MARK_MOON_V95 = "M56.9,41.5 A4.1,4.1 0 1,0 65.1,41.5 A4.1,4.1 0 1,0 56.9,41.5 Z"
private const val MARK_LEFT_V95 = "M53.1,59 Q45,50.5 33,55 L33,69.6 Q45,66.4 53.1,75 Z"
private const val MARK_RIGHT_V95 = "M54.9,59 Q63,50.5 75,55 L75,69.6 Q63,66.4 54.9,75 Z"

/** The brand mark as a vector, cropped to its own bounds (viewport 46 x 46 around the art). */
fun langhuanMarkVectorV95(ink: Color, moon: Color): ImageVector =
    ImageVector.Builder(
        name = "LanghuanMarkV95",
        defaultWidth = 46.dp,
        defaultHeight = 46.dp,
        viewportWidth = 46f,
        viewportHeight = 46f,
    ).apply {
        addGroup(translationX = -31f, translationY = -30f)
        addPath(addPathNodes(MARK_RING_V95), fill = SolidColor(ink))
        addPath(addPathNodes(MARK_MOON_V95), fill = SolidColor(moon))
        addPath(addPathNodes(MARK_LEFT_V95), fill = SolidColor(ink))
        addPath(addPathNodes(MARK_RIGHT_V95), fill = SolidColor(ink))
        clearGroup()
    }.build()

@Composable
fun LanghuanMarkV95(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ink: Color = LocalLanghuanUiTokens.current.primary,
    moon: Color = LocalLanghuanUiTokens.current.gold,
) {
    val vector = remember(ink, moon) { langhuanMarkVectorV95(ink, moon) }
    Image(rememberVectorPainter(vector), contentDescription = null, modifier = modifier.size(size))
}

/**
 * Large title (iOS 「大标题」) that shrinks into a compact bar title once the page scrolls; a
 * hairline fades in under it at the same time. [collapsed] is usually `scroll.canScrollBackward`.
 * Actions sit on the right at a constant size so their touch targets never move vertically.
 */
@Composable
fun LargeTitleBarV95(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    val f by animateFloatAsState(if (collapsed) 1f else 0f, tween(220), label = "large-title")
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(lerp(LARGE_TITLE_HEIGHT_V95, COMPACT_TITLE_HEIGHT_V95, f))
                .padding(start = horizontalPadding, end = horizontalPadding - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = (30f + (19f - 30f) * f).sp,
                    lineHeight = (36f + (24f - 36f) * f).sp,
                    letterSpacing = (0.2f * (1f - f)).sp,
                ),
                color = t.foreground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            actions()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .alpha(f)
                .background(t.border),
        )
    }
}

val LARGE_TITLE_HEIGHT_V95 = 64.dp
val COMPACT_TITLE_HEIGHT_V95 = 50.dp

/**
 * Empty state: the brand mark in a soft jade disc, a short title and one line of guidance, with
 * an optional action slot underneath. Used by the shelf, 书城, 创作 and source pages.
 */
@Composable
fun EmptyStateV95(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(t.primary.copy(alpha = 0.07f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            LanghuanMarkV95(size = 56.dp, ink = t.primary.copy(alpha = 0.78f), moon = t.gold)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                modifier = Modifier.widthIn(max = 300.dp),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = t.mutedForeground,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}
