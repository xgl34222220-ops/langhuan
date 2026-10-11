package com.xiguli.langhuan.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/** Tables of contents shorter than this scroll fine with a finger; no fast-scroll bar. */
internal const val READER_TOC_FAST_SCROLL_MIN_V92 = 60
internal const val READER_TOC_FAST_SCROLL_LABEL_V92 = "目录快速滚动"
/** Width reserved on the right of the list for the fast-scroll touch strip. */
internal val READER_TOC_FAST_SCROLL_GUTTER_V92 = 28.dp

/** Row index for a touch at [y] on a track of [trackHeight] px. Pure; unit tested. */
internal fun readerTocFastScrollIndexV92(y: Float, trackHeight: Float, count: Int): Int {
    if (count <= 0) return 0
    if (trackHeight <= 0f) return 0
    val fraction = (y / trackHeight).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/** Thumb position 0..1 for the current scroll position. Pure; unit tested. */
internal fun readerTocThumbFractionV92(firstVisible: Int, visibleCount: Int, count: Int): Float {
    val scrollable = count - visibleCount.coerceAtLeast(1)
    if (scrollable <= 0) return 0f
    return (firstVisible.toFloat() / scrollable).coerceIn(0f, 1f)
}

/**
 * Fast-scroll strip for a long directory: drag (or tap) the right edge to jump through
 * hundreds of chapters; while dragging a bubble shows the chapter under the finger.
 */
@Composable
internal fun BoxScope.ReaderTocFastScrollerV92(
    listState: LazyListState,
    itemCount: Int,
    labelFor: (Int) -> String,
) {
    if (itemCount < READER_TOC_FAST_SCROLL_MIN_V92) return
    val t = LocalLanghuanUiTokens.current
    val density = LocalDensity.current
    val labels = rememberUpdatedState(labelFor)
    var dragging by remember { mutableStateOf(false) }
    var target by remember { mutableIntStateOf(-1) }
    // Each touch is a new request, so tapping the same spot twice still jumps.
    var request by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    fun jump(index: Int) {
        target = index
        request = index to System.nanoTime()
    }
    var touchY by remember { mutableFloatStateOf(0f) }

    // Scroll requests are conflated: only the latest finger position is honoured.
    LaunchedEffect(listState, itemCount) {
        snapshotFlow { request }
            .filter { it != null }
            .collectLatest { listState.scrollToItem(it!!.first.coerceIn(0, itemCount - 1)) }
    }
    val thumbFraction by remember(listState, itemCount) {
        derivedStateOf {
            readerTocThumbFractionV92(
                listState.firstVisibleItemIndex,
                listState.layoutInfo.visibleItemsInfo.size,
                itemCount,
            )
        }
    }
    val active = dragging || listState.isScrollInProgress
    val thumbAlpha by animateFloatAsState(if (active) 1f else 0.45f, tween(180), label = "tocThumbAlpha")

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(READER_TOC_FAST_SCROLL_GUTTER_V92)
            .padding(vertical = 6.dp)
            .semantics {
                contentDescription = READER_TOC_FAST_SCROLL_LABEL_V92
                stateDescription = labels.value(
                    if (target >= 0 && dragging) target else listState.firstVisibleItemIndex,
                )
            }
            .pointerInput(itemCount) {
                detectTapGestures { offset ->
                    jump(readerTocFastScrollIndexV92(offset.y, size.height.toFloat(), itemCount))
                }
            }
            .pointerInput(itemCount) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        touchY = offset.y
                        jump(readerTocFastScrollIndexV92(offset.y, size.height.toFloat(), itemCount))
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    touchY = change.position.y.coerceIn(0f, size.height.toFloat())
                    val index = readerTocFastScrollIndexV92(touchY, size.height.toFloat(), itemCount)
                    if (index != target) jump(index)
                }
            },
    ) {
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbHeight = 44.dp
        val thumbPx = with(density) { thumbHeight.toPx() }
        val fraction = if (dragging && trackPx > 0f) (touchY / trackPx).coerceIn(0f, 1f) else thumbFraction
        val thumbTop = ((trackPx - thumbPx) * fraction).roundToInt()
        // Faint rail so the strip is discoverable without competing with chapter titles.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .width(2.dp)
                .background(t.border.copy(alpha = 0.6f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbTop) }
                .size(width = 6.dp, height = thumbHeight)
                .alpha(thumbAlpha)
                .background(if (active) t.primary else t.mutedForeground, CircleShape),
        )
    }

    // Chapter hint bubble beside the thumb while dragging.
    AnimatedVisibility(
        visible = dragging && target >= 0,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = READER_TOC_FAST_SCROLL_GUTTER_V92 + 6.dp)
            .offset { IntOffset(0, (touchY - with(density) { 20.dp.toPx() }).roundToInt().coerceAtLeast(0)) },
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(160)),
    ) {
        val shape = RoundedCornerShape(t.radiusMd)
        Box(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .background(t.card, shape)
                .border(1.dp, t.primary, shape)
                .padding(horizontal = t.space3, vertical = t.space2),
        ) {
            Text(
                text = labels.value(target.coerceAtLeast(0)),
                style = MaterialTheme.typography.labelLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
