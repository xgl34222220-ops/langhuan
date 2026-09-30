package com.xiguli.langhuan.ui

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.design.springClickV31
import kotlin.math.roundToInt

/** A long-pressed paragraph on the current page. Offsets are into the normalized chapter body. */
internal data class ReaderSelectionV30(
    val chapterIndex: Int,
    val pageIndex: Int,
    val start: Int,
    val end: Int,
    val text: String,
    /** Visible band on screen, in px, used for the highlight and to place the bubble. */
    val top: Float,
    val bottom: Float,
)

/**
 * Finds the paragraph under [point] on [page]. Paragraph bounds come from [body]; only the lines
 * of that paragraph that are on this page are highlighted.
 */
internal fun readerParagraphAtV30(
    page: ReaderPageV30,
    point: Offset,
    geometry: ReaderGeometryV30,
    body: String,
): ReaderSelectionV30? {
    val lines = page.lines
    if (lines.isEmpty() || body.isEmpty()) return null
    val y = point.y - geometry.bodyTop
    if (y < 0f || point.y > geometry.bodyBottom) return null
    val hit = lines.indices.lastOrNull { lines[it].top <= y } ?: return null
    val line = lines[hit]
    if (line.title) return null
    val offset = line.offset.coerceIn(0, body.length)
    val start = if (offset == 0) 0 else body.lastIndexOf('\n', offset - 1) + 1
    val end = body.indexOf('\n', offset).let { if (it < 0) body.length else it }
    val text = body.substring(start, end).trim().trim('\u3000')
    if (text.isBlank()) return null
    val inParagraph = lines.filter { !it.title && it.offset in start until end.coerceAtLeast(start + 1) }
    val first = inParagraph.firstOrNull() ?: line
    val last = inParagraph.lastOrNull() ?: line
    val lastIndex = lines.indexOf(last)
    val lineHeight = (last.baseline - last.top) * 1.32f
    val bottom = lines.getOrNull(lastIndex + 1)?.top?.takeIf { it > last.top } ?: (last.top + lineHeight)
    return ReaderSelectionV30(
        chapterIndex = page.chapterIndex,
        pageIndex = page.index,
        start = start,
        end = end,
        text = text,
        top = geometry.bodyTop + first.top,
        bottom = geometry.bodyTop + bottom,
    )
}

/** Soft highlighter band over the selected paragraph, drawn above the text. */
internal fun DrawScope.drawReaderSelectionV30(selection: ReaderSelectionV30, geometry: ReaderGeometryV30, theme: ReaderThemeV30, alpha: Float) {
    val pad = 4f * density
    drawRoundRect(
        color = theme.accent.copy(alpha = .16f * alpha),
        topLeft = Offset(geometry.left - pad, selection.top - pad * .5f),
        size = Size(geometry.bodyWidth + pad * 2f, selection.bottom - selection.top + pad),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * density),
    )
    // Accent bar on the left edge, like a margin note.
    drawRoundRect(
        color = theme.accent.copy(alpha = .85f * alpha),
        topLeft = Offset(geometry.left - pad - 3f * density, selection.top),
        size = Size(2.5f * density, selection.bottom - selection.top),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * density),
    )
}

/** Floating actions for a selected paragraph; sits above it, or below when near the top. */
@Composable
internal fun ReaderSelectionBarV30(
    selection: ReaderSelectionV30,
    geometry: ReaderGeometryV30,
    theme: ReaderThemeV30,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    onBookmark: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val appear = remember(selection) { Animatable(0f) }
    LaunchedEffect(selection) { appear.animateTo(1f, tween(180)) }
    val barSize = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }
    val gap = with(density) { 10.dp.toPx() }
    val above = selection.top - gap - barSize.value.height
    val below = selection.bottom + gap
    val maxY = geometry.height - barSize.value.height - gap
    val y = when {
        above > geometry.bodyTop * .6f -> above
        below < maxY -> below
        // Paragraph fills the page: float over its middle instead of off-screen.
        else -> ((selection.top + selection.bottom) / 2f - barSize.value.height / 2f).coerceIn(0f, maxY.coerceAtLeast(0f))
    }
    val x = (geometry.width - barSize.value.width) / 2f
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { barSize.value = it }
            .graphicsLayer {
                alpha = appear.value
                val s = .9f + .1f * appear.value
                scaleX = s
                scaleY = s
            },
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = theme.sheetText.copy(alpha = .94f), shadowElevation = 8.dp) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ReaderSelectionActionV30(Icons.Rounded.TextFields, "复制", theme, onCopy)
                ReaderSelectionActionV30(Icons.Outlined.Bookmark, "书签", theme, onBookmark)
                ReaderSelectionActionV30(Icons.Rounded.Search, "查询", theme, onSearch)
                ReaderSelectionActionV30(Icons.Rounded.Share, "分享", theme, onShare)
                ReaderSelectionActionV30(Icons.Rounded.Close, "取消", theme, onDismiss)
            }
        }
    }
}

@Composable
private fun ReaderSelectionActionV30(icon: ImageVector, label: String, theme: ReaderThemeV30, onClick: () -> Unit) {
    Column(
        Modifier.springClickV31(pressedScale = .88f, onClick = onClick).padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(19.dp), tint = theme.sheet)
        Text(label, color = theme.sheet, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

internal fun readerShareIntentV30(text: String, bookTitle: String): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$text\n\n——《$bookTitle》")
        },
        "分享段落",
    )

internal fun readerSearchIntentV30(text: String): Intent =
    Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, text.take(64))
