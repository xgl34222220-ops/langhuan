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
import androidx.compose.ui.graphics.PathFillType
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
 * Brand kit: the 琅嬛 mark (V97: two leaning books with a leaf sprig) for in-app use,
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

private const val MARK_BOOKS_V97 = "M18.72,11.08 L18.51,10.93 L18.28,10.81 L18.05,10.71 L17.81,10.63 L17.56,10.57 L17.31,10.54 L17.05,10.53 L16.80,10.55 L16.55,10.60 L16.30,10.67 L16.06,10.76 L2.54,16.78 L2.31,16.89 L2.10,17.03 L1.90,17.19 L1.71,17.36 L1.55,17.56 L1.40,17.76 L1.27,17.99 L1.17,18.22 L1.09,18.46 L1.04,18.71 L1.01,18.97 L1.00,19.22 L1.02,19.47 L1.06,19.73 L1.13,19.97 L1.22,20.21 L10.29,40.58 L10.41,40.81 L10.55,41.02 L10.70,41.23 L10.88,41.41 L11.07,41.58 L11.28,41.72 L11.50,41.85 L11.74,41.95 L11.98,42.03 L12.23,42.09 L12.48,42.12 L12.74,42.12 L12.99,42.10 L13.24,42.06 L13.49,41.99 L13.73,41.90 L16.83,40.52 L18.70,44.72 L19.78,42.71 L21.99,43.25 L20.12,39.05 L24.08,37.29 L23.26,35.46 L14.38,39.42 L14.29,39.24 L13.20,39.73 L3.80,18.62 L4.90,18.14 L13.97,38.51 L22.86,34.55 L17.67,22.90 L17.64,22.82 L17.55,22.59 L17.49,22.43 L17.43,22.18 L17.39,22.02 L17.34,21.77 L17.32,21.60 L17.30,21.35 L17.30,21.18 L17.31,20.93 L17.32,20.76 L17.35,20.51 L17.38,20.34 L17.43,20.09 L17.48,19.93 L17.56,19.69 L17.62,19.53 L17.72,19.30 L17.79,19.15 L17.92,18.93 L18.01,18.79 L18.16,18.58 L18.26,18.45 L18.42,18.25 L18.54,18.13 L18.72,17.96 L18.85,17.85 L19.05,17.69 L19.19,17.59 L19.40,17.46 L19.55,17.37 L19.77,17.26 L19.85,17.22 L21.47,16.50 L19.49,12.08 L19.38,11.85 L19.24,11.63 L19.09,11.43 L18.91,11.25 Z M36.91,13.25 L36.72,13.08 L36.51,12.93 L36.28,12.81 L36.05,12.71 L35.81,12.63 L35.56,12.57 L35.31,12.54 L35.05,12.53 L34.80,12.55 L34.55,12.60 L34.30,12.67 L34.06,12.76 L20.54,18.78 L20.31,18.89 L20.10,19.03 L19.90,19.19 L19.71,19.36 L19.55,19.56 L19.40,19.76 L19.27,19.99 L19.17,20.22 L19.09,20.46 L19.04,20.71 L19.01,20.97 L19.00,21.22 L19.02,21.47 L19.06,21.73 L19.13,21.97 L19.22,22.21 L28.29,42.58 L28.41,42.81 L28.55,43.02 L28.70,43.23 L28.88,43.41 L29.07,43.58 L29.28,43.72 L29.50,43.85 L29.74,43.95 L29.98,44.03 L30.23,44.09 L30.48,44.12 L30.74,44.12 L30.99,44.10 L31.24,44.06 L31.49,43.99 L31.73,43.90 L34.83,42.52 L36.70,46.72 L37.78,44.71 L39.99,45.25 L38.12,41.05 L45.25,37.88 L45.48,37.76 L45.69,37.63 L45.89,37.47 L46.08,37.29 L46.24,37.10 L46.39,36.89 L46.51,36.67 L46.62,36.44 L46.70,36.19 L46.75,35.94 L46.78,35.69 L46.79,35.44 L46.77,35.18 L46.73,34.93 L46.66,34.69 L46.57,34.45 L37.49,14.08 L37.38,13.85 L37.24,13.63 L37.09,13.43 Z M22.90,20.14 L31.97,40.51 L44.94,34.73 L45.35,35.65 L32.38,41.42 L32.29,41.24 L31.20,41.73 L21.80,20.62 Z"
private const val MARK_LEAVES_V97 = "M27.39,10.93 L27.80,10.85 L28.21,10.77 L28.60,10.68 L28.99,10.58 L29.37,10.47 L29.73,10.35 L30.09,10.23 L30.43,10.09 L30.77,9.95 L31.09,9.79 L31.40,9.63 L31.70,9.45 L31.98,9.27 L32.25,9.07 L32.51,8.86 L32.75,8.64 L32.98,8.41 L33.20,8.17 L33.40,7.92 L33.59,7.65 L33.76,7.37 L33.92,7.08 L34.07,6.78 L34.20,6.47 L34.32,6.14 L34.43,5.81 L34.53,5.47 L34.61,5.11 L34.68,4.75 L34.75,4.38 L34.80,4.00 L34.84,3.61 L34.87,3.21 L34.89,2.80 L34.91,2.39 L34.91,1.97 L34.90,1.53 L34.85,1.07 L34.85,1.07 L34.39,1.10 L33.96,1.17 L33.54,1.24 L33.14,1.33 L32.74,1.42 L32.36,1.52 L31.98,1.63 L31.61,1.75 L31.26,1.87 L30.91,2.01 L30.58,2.15 L30.25,2.30 L29.94,2.47 L29.65,2.64 L29.36,2.83 L29.09,3.03 L28.83,3.23 L28.59,3.45 L28.36,3.68 L28.14,3.93 L27.94,4.18 L27.75,4.45 L27.58,4.73 L27.42,5.02 L27.27,5.32 L27.14,5.63 L27.02,5.95 L26.91,6.29 L26.82,6.63 L26.73,6.99 L26.66,7.35 L26.60,7.72 L26.55,8.10 L26.51,8.49 L26.47,8.89 L26.45,9.30 L26.44,9.71 L26.44,10.13 L26.45,10.56 L26.49,11.03 L26.96,10.99 Z M27.74,10.04 L27.25,9.63 L32.93,2.86 L33.42,3.27 Z M25.39,10.29 L25.41,9.94 L25.41,9.60 L25.42,9.27 L25.41,8.94 L25.39,8.62 L25.37,8.31 L25.34,8.00 L25.29,7.70 L25.24,7.41 L25.18,7.12 L25.11,6.85 L25.02,6.58 L24.92,6.32 L24.82,6.07 L24.70,5.83 L24.57,5.60 L24.42,5.38 L24.27,5.17 L24.10,4.97 L23.92,4.77 L23.73,4.59 L23.52,4.42 L23.31,4.25 L23.08,4.10 L22.84,3.95 L22.59,3.81 L22.33,3.68 L22.06,3.56 L21.78,3.45 L21.49,3.34 L21.19,3.24 L20.88,3.15 L20.57,3.06 L20.24,2.98 L19.91,2.91 L19.57,2.84 L19.21,2.79 L18.83,2.75 L18.83,2.75 L18.77,3.14 L18.74,3.49 L18.72,3.84 L18.71,4.18 L18.71,4.51 L18.72,4.84 L18.73,5.16 L18.76,5.48 L18.79,5.78 L18.83,6.08 L18.88,6.38 L18.95,6.66 L19.02,6.94 L19.10,7.20 L19.20,7.46 L19.31,7.71 L19.43,7.95 L19.56,8.18 L19.70,8.40 L19.86,8.62 L20.02,8.82 L20.20,9.01 L20.40,9.19 L20.60,9.37 L20.82,9.53 L21.04,9.69 L21.28,9.83 L21.53,9.97 L21.79,10.10 L22.06,10.22 L22.34,10.34 L22.63,10.44 L22.93,10.54 L23.24,10.63 L23.56,10.72 L23.88,10.80 L24.21,10.87 L24.56,10.94 L24.91,11.00 L25.29,11.03 L25.36,10.65 Z M24.77,9.84 L24.27,10.23 L19.87,4.61 L20.38,4.21 Z"
private const val MARK_VIEWPORT_V97 = 47.79f

/** Leaf green of the mark (the launcher's emerald), shared by light and dark. */
val LEAF_EMERALD_V97 = Color(0xFF2F9A72)

/**
 * The V97 brand mark for in-app use: two leaning books (spine, page edge, ribbon) with a two-leaf
 * sprig, cropped to its own bounds. Same geometry as the themed-icon layer
 * (tools/brand/glass_icon_v97.py → ic_launcher_monochrome_v97.xml).
 */
fun langhuanMarkVectorV95(ink: Color, moon: Color): ImageVector =
    ImageVector.Builder(
        name = "LanghuanMarkV97",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = MARK_VIEWPORT_V97,
        viewportHeight = MARK_VIEWPORT_V97,
    ).apply {
        addPath(addPathNodes(MARK_BOOKS_V97), fill = SolidColor(ink), pathFillType = PathFillType.EvenOdd)
        addPath(addPathNodes(MARK_LEAVES_V97), fill = SolidColor(moon), pathFillType = PathFillType.EvenOdd)
    }.build()

/** Brand mark. [ink] colours the books, [moon] (kept for call-site compatibility) the leaves. */
@Composable
fun LanghuanMarkV95(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ink: Color = LocalLanghuanUiTokens.current.foreground.copy(alpha = 0.86f),
    moon: Color = LEAF_EMERALD_V97,
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
 * Empty state: the brand mark in a soft paper-wash disc, a short title and one line of guidance, with
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
            Modifier.size(96.dp).background(t.foreground.copy(alpha = 0.045f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            LanghuanMarkV95(size = 58.dp, ink = t.foreground.copy(alpha = 0.62f))
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
