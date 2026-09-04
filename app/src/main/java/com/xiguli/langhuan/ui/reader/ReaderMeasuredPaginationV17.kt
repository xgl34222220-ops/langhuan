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

/**
 * Stable pagination used by Reader V11.
 *
 * The page model follows the same structure as mature Android readers such as Legado:
 * system-inset reservation -> fixed header -> dedicated content viewport -> fixed footer.
 * Header/footer never participate in the body's intrinsic height and therefore cannot be pushed
 * outside the screen by one extra line of text.
 */
internal data class ReaderMeasuredPaginationV17(
    val pages: List<String>,
    val offsets: List<Int>,
    val firstParagraphStartsAtBoundary: List<Boolean>,
    val layoutToken: String,
)

private data class ReaderParagraphV17(val start: Int, val end: Int)
private data class ReaderPieceV17(val start: Int, val end: Int, val startsParagraph: Boolean)

@Composable
internal fun rememberReaderMeasuredPaginationV17(
    text: String,
    displayTitle: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
): ReaderMeasuredPaginationV17 {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInfo = LocalWindowInfo.current
    val configuration = LocalConfiguration.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 128)

    // Important: ignoringVisibility keeps the reading viewport stable when immersive mode toggles
    // or Android temporarily hides bars during recents/background transitions.
    val stableInsets = WindowInsets.systemBarsIgnoringVisibility
    val fallbackWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val windowWidthPx = windowInfo.containerSize.width.takeIf { it > 0 } ?: fallbackWidthPx
    val windowHeightPx = windowInfo.containerSize.height.takeIf { it > 0 } ?: fallbackHeightPx
    val stableWidthPx = (
        windowWidthPx -
            stableInsets.getLeft(density, layoutDirection) -
            stableInsets.getRight(density, layoutDirection)
        ).coerceAtLeast(1)
    val stableHeightPx = (
        windowHeightPx -
            stableInsets.getTop(density) -
            stableInsets.getBottom(density)
        ).coerceAtLeast(1)

    val horizontalPaddingPx = with(density) { sidePadding.coerceIn(12f, 48f).dp.roundToPx() }
    val bodyWidthPx = (stableWidthPx - horizontalPaddingPx * 2)
        .coerceAtLeast(with(density) { 180.dp.roundToPx() })

    // Must stay byte-for-byte conceptually aligned with ReaderPageCanvasV11.
    val pageTopPx = with(density) { 16.dp.roundToPx() }
    val pageBottomPx = with(density) { 12.dp.roundToPx() }
    val headerGapPx = with(density) { 14.dp.roundToPx() }
    val footerGapPx = with(density) { 8.dp.roundToPx() }
    val rasterGuardPx = with(density) { 2.dp.roundToPx() }
    val paragraphGapPx = with(density) { paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx() }

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

    val headerHeightPx = textMeasurer.measure(
        displayTitle,
        style = headerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val footerHeightPx = textMeasurer.measure(
        "18:09   83%     88/100",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height

    val bodyHeightPx = (
        stableHeightPx -
            pageTopPx - headerHeightPx - headerGapPx -
            footerGapPx - footerHeightPx - pageBottomPx -
            rasterGuardPx
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })

    val normalized = remember(text) { readerNormalizeBodyV14(text) }
    val token = buildString {
        append(stableWidthPx).append(':')
        append(stableHeightPx).append(':')
        append(bodyWidthPx).append(':')
        append(bodyHeightPx).append(':')
        append(fontSize).append(':')
        append(lineFactor).append(':')
        append(paragraphSpacing).append(':')
        append(firstLineIndent).append(':')
        append(family.hashCode())
    }

    return remember(
        normalized,
        bodyWidthPx,
        bodyHeightPx,
        bodyStyle,
        paragraphGapPx,
        firstLineIndent,
        fontSize,
        token,
    ) {
        paginateReaderV17(
            text = normalized,
            textMeasurer = textMeasurer,
            bodyStyle = bodyStyle,
            widthPx = bodyWidthPx,
            bodyHeightPx = bodyHeightPx,
            paragraphGapPx = paragraphGapPx,
            firstLineIndent = firstLineIndent,
            fontSize = fontSize,
            layoutToken = token,
        )
    }
}

private fun paginateReaderV17(
    text: String,
    textMeasurer: TextMeasurer,
    bodyStyle: TextStyle,
    widthPx: Int,
    bodyHeightPx: Int,
    paragraphGapPx: Int,
    firstLineIndent: Boolean,
    fontSize: Float,
    layoutToken: String,
): ReaderMeasuredPaginationV17 {
    if (text.isBlank()) {
        return ReaderMeasuredPaginationV17(listOf(""), listOf(0), listOf(true), layoutToken)
    }

    val paragraphs = readerParagraphsV17(text)
    if (paragraphs.isEmpty()) {
        return ReaderMeasuredPaginationV17(listOf(text), listOf(0), listOf(true), layoutToken)
    }

    val pages = mutableListOf<String>()
    val offsets = mutableListOf<Int>()
    val firstStarts = mutableListOf<Boolean>()
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
        val pieces = mutableListOf<ReaderPieceV17>()
        var usedHeight = 0

        while (paragraphIndex < paragraphs.size && cursor < text.length) {
            val paragraph = paragraphs[paragraphIndex]
            if (cursor < paragraph.start) cursor = paragraph.start
            if (cursor >= paragraph.end) {
                paragraphIndex++
                continue
            }

            val startsParagraph = cursor == paragraph.start
            val gap = if (pieces.isNotEmpty() && startsParagraph) paragraphGapPx else 0
            val available = bodyHeightPx - usedHeight - gap
            if (available <= 0 && pieces.isNotEmpty()) break

            // A continuation at the top of a new page is NOT a new paragraph. Mature readers do not
            // re-apply first-line indent there; doing so caused the visibly shifted first line in V10.
            val style = bodyStyle.copy(
                textIndent = TextIndent(
                    firstLine = if (firstLineIndent && startsParagraph) (fontSize * 2f).sp else 0.sp,
                ),
            )
            val remaining = text.substring(cursor, paragraph.end)
            val layout = measureReaderLayoutV17(textMeasurer, remaining, style, widthPx)

            if (layout.size.height <= available.coerceAtLeast(1)) {
                if (gap > 0) usedHeight += gap
                pieces += ReaderPieceV17(cursor, paragraph.end, startsParagraph)
                usedHeight += layout.size.height
                cursor = paragraph.end
                paragraphIndex++
                if (paragraphIndex < paragraphs.size) cursor = paragraphs[paragraphIndex].start
                continue
            }

            if (available <= 0 && pieces.isNotEmpty()) break
            val splitRelative = lastWholeLineEndV17(layout, available.coerceAtLeast(1))
            if (splitRelative <= 0 && pieces.isNotEmpty()) break

            val safeEnd = if (splitRelative > 0) {
                (cursor + splitRelative).coerceAtMost(paragraph.end)
            } else {
                nextSafeCharBoundaryV17(text, cursor, paragraph.end)
            }
            if (gap > 0) usedHeight += gap
            pieces += ReaderPieceV17(cursor, safeEnd, startsParagraph)
            cursor = safeEnd
            break
        }

        if (pieces.isEmpty()) {
            val paragraph = paragraphs[paragraphIndex]
            val safeEnd = nextSafeCharBoundaryV17(text, cursor, paragraph.end)
            pieces += ReaderPieceV17(cursor, safeEnd, cursor == paragraph.start)
            cursor = safeEnd
        }

        pages += buildString {
            pieces.forEachIndexed { index, piece ->
                if (index > 0 && piece.startsParagraph) append('\n')
                append(text.substring(piece.start, piece.end))
            }
        }.trimEnd()
        offsets += pageStart.coerceIn(0, text.length)
        firstStarts += pageStartsParagraph
    }

    return ReaderMeasuredPaginationV17(
        pages = pages.ifEmpty { listOf(text) },
        offsets = offsets.ifEmpty { listOf(0) },
        firstParagraphStartsAtBoundary = firstStarts.ifEmpty { listOf(true) },
        layoutToken = layoutToken,
    )
}

private fun measureReaderLayoutV17(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
): TextLayoutResult = measurer.measure(
    text = text,
    style = style,
    constraints = Constraints(maxWidth = widthPx),
)

/** Returns a source-relative offset ending on the last fully visible text line. */
private fun lastWholeLineEndV17(layout: TextLayoutResult, availableHeightPx: Int): Int {
    if (layout.lineCount <= 0) return 0
    var last = -1
    for (line in 0 until layout.lineCount) {
        if (layout.getLineBottom(line) <= availableHeightPx.toFloat()) last = line else break
    }
    if (last < 0) return 0
    return layout.getLineEnd(last, visibleEnd = false).coerceAtLeast(0)
}

private fun nextSafeCharBoundaryV17(text: String, start: Int, end: Int): Int {
    if (start >= end) return end
    var next = (start + 1).coerceAtMost(end)
    if (next < end && Character.isHighSurrogate(text[start]) && Character.isLowSurrogate(text[next])) {
        next++
    }
    return next.coerceAtMost(end)
}

private fun readerParagraphsV17(text: String): List<ReaderParagraphV17> = buildList {
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start] == '\n') start++
        if (start >= text.length) break
        val newline = text.indexOf('\n', start)
        val end = if (newline < 0) text.length else newline
        if (end > start) add(ReaderParagraphV17(start, end))
        start = if (newline < 0) text.length else newline + 1
    }
}
