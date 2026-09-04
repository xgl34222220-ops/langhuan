package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A frozen page plan measured by the same Compose text stack that renders the reader.
 * Offsets are raw offsets inside the normalized chapter body and remain the persistence anchor.
 */
internal data class ReaderMeasuredPaginationV16(
    val pages: List<String>,
    val offsets: List<Int>,
    val indentFirstParagraph: List<Boolean>,
    val layoutToken: String,
)

private data class ReaderParagraphV16(
    val start: Int,
    val end: Int,
)

private data class ReaderPagePieceV16(
    val start: Int,
    val end: Int,
    val startsParagraph: Boolean,
)

/**
 * Compose-native pagination.
 *
 * Measurement intentionally mirrors ReaderMobileExperience's editorial page geometry. If the
 * rendered title/header/footer spacing changes, update these metrics in the same PR so a page can
 * never be measured taller than the space it is actually rendered into.
 */
@Composable
internal fun rememberReaderMeasuredPaginationV16(
    text: String,
    displayTitle: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
): ReaderMeasuredPaginationV16 {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowInfo = LocalWindowInfo.current
    val configuration = LocalConfiguration.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 96)
    val safeInsets = WindowInsets.safeDrawing

    val fallbackWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val fallbackHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val windowWidthPx = windowInfo.containerSize.width.takeIf { it > 0 } ?: fallbackWidthPx
    val windowHeightPx = windowInfo.containerSize.height.takeIf { it > 0 } ?: fallbackHeightPx
    val safeWidthPx = (
        windowWidthPx -
            safeInsets.getLeft(density, layoutDirection) -
            safeInsets.getRight(density, layoutDirection)
        ).coerceAtLeast(1)
    val safeHeightPx = (
        windowHeightPx -
            safeInsets.getTop(density) -
            safeInsets.getBottom(density)
        ).coerceAtLeast(1)
    val sidePaddingPx = with(density) { sidePadding.coerceIn(10f, 60f).dp.roundToPx() }
    val bodyWidthPx = (
        safeWidthPx - sidePaddingPx * 2
        ).coerceAtLeast(with(density) { 180.dp.roundToPx() })

    val bodyStyle = TextStyle(
        fontSize = fontSize.coerceIn(12f, 36f).sp,
        lineHeight = (fontSize.coerceIn(12f, 36f) * lineFactor.coerceIn(1.2f, 2.6f)).sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
    )
    val titleStyle = TextStyle(
        fontSize = (fontSize + 2f).sp,
        lineHeight = (fontSize + 8f).sp,
        fontWeight = FontWeight.Medium,
        fontFamily = family,
    )
    val headerStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = family,
    )
    val footerStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontFamily = family,
    )

    // Must match MobileReaderPageContent exactly:
    // 14dp page top/bottom, +5dp before first title, 20dp title gap,
    // 12dp subsequent-page header gap and 5dp gap before the footer row.
    val pageTopPx = with(density) { 14.dp.roundToPx() }
    val firstTitleTopExtraPx = with(density) { 5.dp.roundToPx() }
    val titleGapPx = with(density) { 20.dp.roundToPx() }
    val headerGapPx = with(density) { 12.dp.roundToPx() }
    val footerGapPx = with(density) { 5.dp.roundToPx() }
    val pageBottomPx = with(density) { 14.dp.roundToPx() }
    val paragraphGapPx = with(density) {
        paragraphSpacing.coerceIn(0f, 24f).dp.roundToPx()
    }

    val titleHeightPx = textMeasurer.measure(
        text = displayTitle,
        style = titleStyle,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val headerHeightPx = textMeasurer.measure(
        text = displayTitle,
        style = headerStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height
    val footerHeightPx = textMeasurer.measure(
        text = "99 / 99    琅嬛",
        style = footerStyle,
        maxLines = 1,
        constraints = Constraints(maxWidth = bodyWidthPx),
    ).size.height

    val firstBodyHeightPx = (
        safeHeightPx -
            pageTopPx -
            firstTitleTopExtraPx -
            titleHeightPx -
            titleGapPx -
            footerGapPx -
            footerHeightPx -
            pageBottomPx
        ).coerceAtLeast(with(density) { 220.dp.roundToPx() })
    val normalBodyHeightPx = (
        safeHeightPx -
            pageTopPx -
            headerHeightPx -
            headerGapPx -
            footerGapPx -
            footerHeightPx -
            pageBottomPx
        ).coerceAtLeast(with(density) { 240.dp.roundToPx() })

    val normalized = remember(text) { readerNormalizeBodyV14(text) }
    val token = "$safeWidthPx:$safeHeightPx:$bodyWidthPx:$firstBodyHeightPx:$normalBodyHeightPx"

    return remember(
        normalized,
        displayTitle,
        fontSize,
        lineFactor,
        sidePadding,
        paragraphSpacing,
        firstLineIndent,
        family,
        token,
    ) {
        paginateReaderV16(
            text = normalized,
            textMeasurer = textMeasurer,
            bodyStyle = bodyStyle,
            widthPx = bodyWidthPx,
            firstBodyHeightPx = firstBodyHeightPx,
            normalBodyHeightPx = normalBodyHeightPx,
            paragraphGapPx = paragraphGapPx,
            firstLineIndent = firstLineIndent,
            fontSize = fontSize,
            layoutToken = token,
        )
    }
}

private fun paginateReaderV16(
    text: String,
    textMeasurer: TextMeasurer,
    bodyStyle: TextStyle,
    widthPx: Int,
    firstBodyHeightPx: Int,
    normalBodyHeightPx: Int,
    paragraphGapPx: Int,
    firstLineIndent: Boolean,
    fontSize: Float,
    layoutToken: String,
): ReaderMeasuredPaginationV16 {
    if (text.isBlank()) {
        return ReaderMeasuredPaginationV16(listOf(""), listOf(0), listOf(true), layoutToken)
    }

    val paragraphs = readerParagraphsV16(text)
    if (paragraphs.isEmpty()) {
        return ReaderMeasuredPaginationV16(listOf(text), listOf(0), listOf(true), layoutToken)
    }

    val pages = mutableListOf<String>()
    val offsets = mutableListOf<Int>()
    val firstIndent = mutableListOf<Boolean>()
    var cursor = paragraphs.first().start
    var paragraphIndex = 0

    while (cursor < text.length && paragraphIndex < paragraphs.size) {
        while (paragraphIndex < paragraphs.size && cursor >= paragraphs[paragraphIndex].end) {
            paragraphIndex++
            if (paragraphIndex < paragraphs.size) cursor = paragraphs[paragraphIndex].start
        }
        if (paragraphIndex >= paragraphs.size) break

        val maxHeight = if (pages.isEmpty()) firstBodyHeightPx else normalBodyHeightPx
        val pieces = mutableListOf<ReaderPagePieceV16>()
        var usedHeight = 0
        val pageStart = cursor
        val pageStartsParagraph = cursor == paragraphs[paragraphIndex].start

        while (paragraphIndex < paragraphs.size && cursor < text.length) {
            val paragraph = paragraphs[paragraphIndex]
            if (cursor < paragraph.start) cursor = paragraph.start
            if (cursor >= paragraph.end) {
                paragraphIndex++
                continue
            }

            val startsParagraph = cursor == paragraph.start
            val gap = if (pieces.isNotEmpty() && startsParagraph) paragraphGapPx else 0
            val available = maxHeight - usedHeight - gap
            if (available <= 0 && pieces.isNotEmpty()) break

            val style = bodyStyle.withReaderIndentV16(
                enabled = firstLineIndent && startsParagraph,
                fontSize = fontSize,
            )
            val remaining = text.substring(cursor, paragraph.end)
            val fullHeight = measureReaderTextHeightV16(textMeasurer, remaining, style, widthPx)

            if (fullHeight <= available.coerceAtLeast(1)) {
                if (gap > 0) usedHeight += gap
                pieces += ReaderPagePieceV16(cursor, paragraph.end, startsParagraph)
                usedHeight += fullHeight
                cursor = paragraph.end
                paragraphIndex++
                if (paragraphIndex < paragraphs.size) cursor = paragraphs[paragraphIndex].start
                continue
            }

            if (available <= 0 && pieces.isNotEmpty()) break
            val splitEnd = findReaderSplitV16(
                source = text,
                start = cursor,
                end = paragraph.end,
                availableHeightPx = available.coerceAtLeast(1),
                textMeasurer = textMeasurer,
                style = style,
                widthPx = widthPx,
            )

            // Never force a partial glyph line into the remaining viewport. This is what prevents
            // the old "half a line above the footer" artifact at the end of a page.
            if (splitEnd <= cursor && pieces.isNotEmpty()) break

            val safeEnd = if (splitEnd > cursor) {
                splitEnd.coerceAtMost(paragraph.end)
            } else {
                // Empty pages should still make forward progress if a device reports malformed
                // metrics; this fallback is intentionally only used when there are no page pieces.
                (cursor + 1).coerceAtMost(paragraph.end)
            }
            if (gap > 0) usedHeight += gap
            pieces += ReaderPagePieceV16(cursor, safeEnd, startsParagraph)
            cursor = safeEnd
            break
        }

        if (pieces.isEmpty()) {
            val paragraph = paragraphs[paragraphIndex]
            val safeEnd = (cursor + 1).coerceAtMost(paragraph.end)
            pieces += ReaderPagePieceV16(cursor, safeEnd, cursor == paragraph.start)
            cursor = safeEnd
        }

        val pageText = buildString {
            pieces.forEachIndexed { index, piece ->
                if (index > 0 && piece.startsParagraph) append('\n')
                append(text.substring(piece.start, piece.end))
            }
        }.trimEnd()
        pages += pageText
        offsets += pageStart.coerceIn(0, text.length)
        firstIndent += pageStartsParagraph
    }

    return ReaderMeasuredPaginationV16(
        pages = pages.ifEmpty { listOf(text) },
        offsets = offsets.ifEmpty { listOf(0) },
        indentFirstParagraph = firstIndent.ifEmpty { listOf(true) },
        layoutToken = layoutToken,
    )
}

private fun readerParagraphsV16(text: String): List<ReaderParagraphV16> = buildList {
    var start = 0
    while (start < text.length) {
        while (start < text.length && text[start] == '\n') start++
        if (start >= text.length) break
        val newline = text.indexOf('\n', start)
        val end = if (newline < 0) text.length else newline
        if (end > start) add(ReaderParagraphV16(start, end))
        start = if (newline < 0) text.length else newline + 1
    }
}

private fun TextStyle.withReaderIndentV16(enabled: Boolean, fontSize: Float): TextStyle = copy(
    textIndent = TextIndent(firstLine = if (enabled) (fontSize * 2f).sp else 0.sp),
)

private fun measureReaderTextHeightV16(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
): Int = measurer.measure(
    text = text,
    style = style,
    constraints = Constraints(maxWidth = widthPx),
).size.height

private fun findReaderSplitV16(
    source: String,
    start: Int,
    end: Int,
    availableHeightPx: Int,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
): Int {
    var low = (start + 1).coerceAtMost(end)
    var high = end
    var best = start
    while (low <= high) {
        val mid = low + (high - low) / 2
        val height = measureReaderTextHeightV16(
            textMeasurer,
            source.substring(start, mid),
            style,
            widthPx,
        )
        if (height <= availableHeightPx) {
            best = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best.coerceIn(start, end)
}
