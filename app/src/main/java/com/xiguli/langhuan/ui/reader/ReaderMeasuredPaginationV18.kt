@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class ReaderPaginationV18(
    val pages: List<String>,
    val offsets: List<Int>,
    val pageStartsParagraph: List<Boolean>,
    val layoutToken: String,
)

private data class ParagraphV18(val start: Int, val end: Int)
private data class PieceV18(val start: Int, val end: Int, val startsParagraph: Boolean)

/**
 * Reader V12 pagination has one source of truth:
 * stable system insets + fixed header/footer chrome + an isolated text viewport.
 * Text is split only at complete Compose line boundaries.
 */
@Composable
internal fun rememberReaderPaginationV18(
    text: String,
    title: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
): ReaderPaginationV18 {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val windowInfo = LocalWindowInfo.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val stableInsets = WindowInsets.systemBarsIgnoringVisibility

    val fallbackWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val windowWidth = windowInfo.containerSize.width.takeIf { it > 0 } ?: fallbackWidth
    val windowHeight = windowInfo.containerSize.height.takeIf { it > 0 } ?: fallbackHeight
    val stableWidth = (
        windowWidth - stableInsets.getLeft(density, direction) - stableInsets.getRight(density, direction)
        ).coerceAtLeast(1)
    val stableHeight = (
        windowHeight - stableInsets.getTop(density) - stableInsets.getBottom(density)
        ).coerceAtLeast(1)

    val horizontal = with(density) { sidePadding.coerceIn(12f, 48f).dp.roundToPx() }
    val bodyWidth = (stableWidth - horizontal * 2).coerceAtLeast(with(density) { 180.dp.roundToPx() })

    val pageTop = with(density) { 16.dp.roundToPx() }
    val pageBottom = with(density) { 12.dp.roundToPx() }
    val headerGap = with(density) { 14.dp.roundToPx() }
    val footerGap = with(density) { 8.dp.roundToPx() }
    val rasterGuard = with(density) { 3.dp.roundToPx() }
    val paragraphGap = with(density) { paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx() }

    val headerStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = family,
        fontWeight = FontWeight.Medium,
    )
    val footerStyle = TextStyle(
        fontSize = 9.sp,
        lineHeight = 12.sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )
    val bodyStyle = TextStyle(
        fontSize = fontSize.coerceIn(13f, 32f).sp,
        lineHeight = (fontSize.coerceIn(13f, 32f) * lineFactor.coerceIn(1.25f, 2.35f)).sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )

    val headerHeight = measurer.measure(
        text = title,
        style = headerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidth),
    ).size.height
    val footerHeight = measurer.measure(
        text = "18:09  83%                         88/100",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidth),
    ).size.height

    val bodyHeight = (
        stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })

    val normalized = remember(text) { readerNormalizeBodyV14(text) }
    val token = "$stableWidth:$stableHeight:$bodyWidth:$bodyHeight:$fontSize:$lineFactor:$paragraphSpacing:$firstLineIndent:${family.hashCode()}"

    return remember(normalized, bodyWidth, bodyHeight, bodyStyle, paragraphGap, firstLineIndent, fontSize, token) {
        paginateV18(
            text = normalized,
            measurer = measurer,
            bodyStyle = bodyStyle,
            width = bodyWidth,
            height = bodyHeight,
            paragraphGap = paragraphGap,
            indent = firstLineIndent,
            fontSize = fontSize,
            token = token,
        )
    }
}

private fun paginateV18(
    text: String,
    measurer: TextMeasurer,
    bodyStyle: TextStyle,
    width: Int,
    height: Int,
    paragraphGap: Int,
    indent: Boolean,
    fontSize: Float,
    token: String,
): ReaderPaginationV18 {
    if (text.isBlank()) return ReaderPaginationV18(listOf(""), listOf(0), listOf(true), token)
    val paragraphs = paragraphsV18(text)
    if (paragraphs.isEmpty()) return ReaderPaginationV18(listOf(text), listOf(0), listOf(true), token)

    val pages = mutableListOf<String>()
    val offsets = mutableListOf<Int>()
    val starts = mutableListOf<Boolean>()
    var paragraphIndex = 0
    var cursor = paragraphs.first().start

    while (cursor < text.length && paragraphIndex < paragraphs.size) {
        while (paragraphIndex < paragraphs.size && cursor >= paragraphs[paragraphIndex].end) {
            paragraphIndex++
            if (paragraphIndex < paragraphs.size) cursor = paragraphs[paragraphIndex].start
        }
        if (paragraphIndex >= paragraphs.size) break

        val pageStart = cursor
        val pageStartsParagraph = cursor == paragraphs[paragraphIndex].start
        val pieces = mutableListOf<PieceV18>()
        var used = 0

        while (paragraphIndex < paragraphs.size && cursor < text.length) {
            val paragraph = paragraphs[paragraphIndex]
            if (cursor < paragraph.start) cursor = paragraph.start
            if (cursor >= paragraph.end) {
                paragraphIndex++
                continue
            }

            val startsParagraph = cursor == paragraph.start
            val gap = if (pieces.isNotEmpty() && startsParagraph) paragraphGap else 0
            val available = height - used - gap
            if (available <= 0 && pieces.isNotEmpty()) break

            val style = bodyStyle.copy(
                textIndent = TextIndent(
                    firstLine = if (indent && startsParagraph) (fontSize * 2f).sp else 0.sp,
                ),
            )
            val remaining = text.substring(cursor, paragraph.end)
            val layout = measureV18(measurer, remaining, style, width)

            if (layout.size.height <= available.coerceAtLeast(1)) {
                if (gap > 0) used += gap
                pieces += PieceV18(cursor, paragraph.end, startsParagraph)
                used += layout.size.height
                cursor = paragraph.end
                paragraphIndex++
                if (paragraphIndex < paragraphs.size) cursor = paragraphs[paragraphIndex].start
                continue
            }

            if (available <= 0 && pieces.isNotEmpty()) break
            val relativeEnd = lastCompleteLineEndV18(layout, available.coerceAtLeast(1))
            if (relativeEnd <= 0 && pieces.isNotEmpty()) break
            val safeEnd = if (relativeEnd > 0) {
                (cursor + relativeEnd).coerceAtMost(paragraph.end)
            } else nextCodePointV18(text, cursor, paragraph.end)
            if (gap > 0) used += gap
            pieces += PieceV18(cursor, safeEnd, startsParagraph)
            cursor = safeEnd
            break
        }

        if (pieces.isEmpty()) {
            val p = paragraphs[paragraphIndex]
            val end = nextCodePointV18(text, cursor, p.end)
            pieces += PieceV18(cursor, end, cursor == p.start)
            cursor = end
        }

        pages += buildString {
            pieces.forEachIndexed { index, piece ->
                if (index > 0 && piece.startsParagraph) append('\n')
                append(text.substring(piece.start, piece.end))
            }
        }.trimEnd()
        offsets += pageStart.coerceIn(0, text.length)
        starts += pageStartsParagraph
    }

    return ReaderPaginationV18(
        pages = pages.ifEmpty { listOf(text) },
        offsets = offsets.ifEmpty { listOf(0) },
        pageStartsParagraph = starts.ifEmpty { listOf(true) },
        layoutToken = token,
    )
}

private fun measureV18(measurer: TextMeasurer, text: String, style: TextStyle, width: Int): TextLayoutResult =
    measurer.measure(text = text, style = style, constraints = Constraints(maxWidth = width))

private fun lastCompleteLineEndV18(layout: TextLayoutResult, available: Int): Int {
    var lastLine = -1
    for (line in 0 until layout.lineCount) {
        if (layout.getLineBottom(line) <= available.toFloat()) lastLine = line else break
    }
    return if (lastLine < 0) 0 else layout.getLineEnd(lastLine, visibleEnd = false).coerceAtLeast(0)
}

private fun nextCodePointV18(text: String, start: Int, end: Int): Int {
    if (start >= end) return end
    var next = (start + 1).coerceAtMost(end)
    if (next < end && Character.isHighSurrogate(text[start]) && Character.isLowSurrogate(text[next])) next++
    return next.coerceAtMost(end)
}

private fun paragraphsV18(text: String): List<ParagraphV18> = buildList {
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start] == '\n') start++
        if (start >= text.length) break
        val newline = text.indexOf('\n', start)
        val end = if (newline < 0) text.length else newline
        if (end > start) add(ParagraphV18(start, end))
        start = if (newline < 0) text.length else newline + 1
    }
}
