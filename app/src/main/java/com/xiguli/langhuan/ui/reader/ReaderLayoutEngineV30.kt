package com.xiguli.langhuan.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * V30 reader layout engine.
 *
 * The old pipeline measured pages with one text layout and then rendered them with separate
 * Compose Text nodes (with trimming and different line boxes). Measurement and drawing could
 * never agree, so pages either overflowed or left blank bands. V30 measures every paragraph once
 * with [StaticLayout], turns it into absolute positioned lines, and the renderer draws exactly
 * those lines. What is paginated is what is drawn.
 */

internal enum class ReaderTurnModeV30(val key: String, val label: String) {
    COVER("cover", "覆盖"),
    SLIDE("page", "平移"),
    SIMULATION("simulation", "仿真"),
    SCROLL("scroll", "上下滚动"),
    NONE("none", "无动画"),
    ;

    companion object {
        fun of(key: String?): ReaderTurnModeV30 = entries.firstOrNull { it.key == key } ?: COVER
    }
}

/** Everything that influences line breaking. Any change produces a new [key]. */
internal data class ReaderTypeSpecV30(
    val bodyWidthPx: Int,
    val bodyHeightPx: Int,
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val paragraphGapPx: Float,
    val titleSizePx: Float,
    val titleLineHeightPx: Float,
    val titleGapPx: Float,
    val indent: Boolean,
    val fontKey: String,
    val weight: Int,
    val letterSpacingEm: Float,
) {
    val key: String = listOf(
        bodyWidthPx, bodyHeightPx, fontSizePx, lineHeightPx, paragraphGapPx, titleSizePx,
        titleLineHeightPx, titleGapPx, indent, fontKey, weight, letterSpacingEm,
    ).joinToString("|")
}

/**
 * One drawable line. [top] is relative to the body area of the page. Justified lines carry
 * pre-computed glyph units and x positions so drawing never measures text.
 */
internal class ReaderLineV30(
    val text: String,
    val top: Float,
    val baseline: Float,
    val title: Boolean,
    val offset: Int,
    val units: Array<String>?,
    val xs: FloatArray?,
)

internal class ReaderPageV30(
    val chapterIndex: Int,
    val index: Int,
    val lines: List<ReaderLineV30>,
    val startOffset: Int,
    val endOffset: Int,
    val usedHeight: Float,
    /** Paragraph gap swallowed at the top of this page; scroll mode restores it. */
    val leadingGap: Float = 0f,
)

internal class ReaderChapterPagesV30(
    val chapterIndex: Int,
    val chapterId: String,
    val title: String,
    val textLength: Int,
    val pages: List<ReaderPageV30>,
) {
    fun pageForOffset(offset: Int): Int {
        if (pages.isEmpty()) return 0
        if (offset >= textLength) return pages.lastIndex
        var low = 0
        var high = pages.lastIndex
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (pages[mid].startOffset <= offset) {
                answer = mid
                low = mid + 1
            } else high = mid - 1
        }
        return answer
    }
}

/** A measured line before page placement. Pure data so the packer is unit-testable. */
internal data class ReaderSlotV30(val height: Float, val gapBefore: Float)

internal data class ReaderPackedPageV30(val first: Int, val last: Int, val tops: FloatArray, val used: Float)

/**
 * Packs measured lines into pages. Paragraph gaps are dropped at the top of a page so every
 * page starts flush with the body top. A slot that is taller than the page gets a page alone.
 */
internal fun readerPackSlotsV30(slots: List<ReaderSlotV30>, pageHeight: Float): List<ReaderPackedPageV30> {
    if (slots.isEmpty()) return emptyList()
    val result = ArrayList<ReaderPackedPageV30>()
    var first = 0
    var y = 0f
    val tops = ArrayList<Float>()
    // A tiny tolerance absorbs float rounding so an exactly fitting last line is not pushed.
    val limit = pageHeight + 0.5f
    slots.forEachIndexed { index, slot ->
        val gap = if (index == first) 0f else slot.gapBefore
        if (index > first && y + gap + slot.height > limit) {
            result += ReaderPackedPageV30(first, index - 1, tops.toFloatArray(), y)
            first = index
            y = 0f
            tops.clear()
            tops += 0f
            y = slot.height
        } else {
            val top = y + gap
            tops += top
            y = top + slot.height
        }
    }
    result += ReaderPackedPageV30(first, slots.lastIndex, tops.toFloatArray(), y)
    return result
}

internal fun readerTypefaceV30(fontKey: String, weight: Int): Typeface {
    val base = when (fontKey) {
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        else -> Typeface.SANS_SERIF
    }
    return Typeface.create(base, weight.coerceIn(100, 900), false)
}

internal fun readerTextPaintV30(sizePx: Float, fontKey: String, weight: Int, letterSpacingEm: Float): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        typeface = readerTypefaceV30(fontKey, weight)
        letterSpacing = letterSpacingEm
        isSubpixelText = true
        isLinearText = false
    }

private fun readerStaticLayoutV30(text: String, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, 1f)
        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

private fun isLatinWordCodePointV30(cp: Int): Boolean =
    cp < 0x2E80 && (Character.isLetterOrDigit(cp) || cp == '\''.code || cp == '-'.code || cp == '.'.code || cp == '_'.code)

/** Splits a line into justification units: CJK glyphs one by one, Latin words kept whole. */
internal fun readerJustifyUnitsV30(text: String): List<String> {
    val units = ArrayList<String>(text.length)
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val n = Character.charCount(cp)
        if (isLatinWordCodePointV30(cp)) {
            var j = i + n
            while (j < text.length) {
                val next = text.codePointAt(j)
                if (!isLatinWordCodePointV30(next)) break
                j += Character.charCount(next)
            }
            units += text.substring(i, j)
            i = j
        } else {
            units += text.substring(i, i + n)
            i += n
        }
    }
    return units
}

/**
 * Distributes the free space of a full line evenly between units. Leading ideographic spaces
 * (the first-line indent) keep their natural width so indents line up down the page.
 */
private fun readerJustifyV30(text: String, paint: TextPaint, width: Float): Pair<Array<String>, FloatArray>? {
    val units = readerJustifyUnitsV30(text)
    if (units.size < 2) return null
    var fixed = 0
    while (fixed < units.size && units[fixed] == "\u3000") fixed++
    val widths = FloatArray(units.size) { paint.measureText(units[it]) }
    val total = widths.sum()
    val free = width - total
    val flexibleGaps = (units.size - 1 - fixed).coerceAtLeast(0)
    if (flexibleGaps <= 0 || free <= 0.5f) return null
    val gap = free / flexibleGaps
    // A huge gap means the line break was forced (for example a long URL); do not stretch it.
    if (gap > paint.textSize * 0.9f) return null
    val xs = FloatArray(units.size)
    var x = 0f
    for (k in units.indices) {
        xs[k] = x
        x += widths[k]
        if (k >= fixed && k < units.lastIndex) x += gap
    }
    return units.toTypedArray() to xs
}

private fun baselineInBoxV30(paint: TextPaint, boxHeight: Float): Float {
    val fm = paint.fontMetrics
    val textHeight = fm.descent - fm.ascent
    return (boxHeight - textHeight) / 2f - fm.ascent
}

/**
 * Paginates one chapter. Runs on a background dispatcher: it only touches its own paints.
 * [body] must already be normalized; offsets are relative to it so saved progress survives
 * any typography change.
 */
internal fun readerPaginateChapterV30(
    chapterIndex: Int,
    chapterId: String,
    title: String,
    body: String,
    spec: ReaderTypeSpecV30,
): ReaderChapterPagesV30 {
    val width = spec.bodyWidthPx.coerceAtLeast(1)
    val bodyPaint = readerTextPaintV30(spec.fontSizePx, spec.fontKey, spec.weight, spec.letterSpacingEm)
    val titlePaint = readerTextPaintV30(spec.titleSizePx, spec.fontKey, 700, spec.letterSpacingEm)
    val bodyBaseline = baselineInBoxV30(bodyPaint, spec.lineHeightPx)
    val titleBaseline = baselineInBoxV30(titlePaint, spec.titleLineHeightPx)

    class Raw(
        val text: String,
        val title: Boolean,
        val offset: Int,
        val justified: Pair<Array<String>, FloatArray>?,
        val slot: ReaderSlotV30,
    )

    val raws = ArrayList<Raw>()
    if (title.isNotBlank()) {
        val layout = readerStaticLayoutV30(title, titlePaint, width)
        for (li in 0 until layout.lineCount) {
            val text = title.substring(layout.getLineStart(li), layout.getLineEnd(li)).trimEnd()
            raws += Raw(text, true, 0, null, ReaderSlotV30(spec.titleLineHeightPx, 0f))
        }
    }

    val prefix = if (spec.indent) "\u3000\u3000" else ""
    var start = 0
    var firstParagraph = true
    val source = body.ifBlank { "本章暂无正文。" }
    while (start <= source.length) {
        val newline = source.indexOf('\n', start)
        val end = if (newline < 0) source.length else newline
        val paragraph = source.substring(start, end)
        val leading = paragraph.indexOfFirst { !it.isWhitespace() && it != '\u3000' }
        if (leading >= 0) {
            val content = paragraph.substring(leading).trimEnd()
            val display = prefix + content
            val layout = readerStaticLayoutV30(display, bodyPaint, width)
            for (li in 0 until layout.lineCount) {
                val ls = layout.getLineStart(li)
                val le = layout.getLineEnd(li)
                val text = display.substring(ls, le).trimEnd('\n', ' ')
                val isLast = li == layout.lineCount - 1
                val justified = if (isLast) null else readerJustifyV30(text, bodyPaint, width.toFloat())
                val gap = when {
                    li != 0 -> 0f
                    firstParagraph -> if (raws.isEmpty()) 0f else spec.titleGapPx
                    else -> spec.paragraphGapPx
                }
                val offset = start + leading + (ls - prefix.length).coerceAtLeast(0)
                raws += Raw(text, false, offset, justified, ReaderSlotV30(spec.lineHeightPx, gap))
            }
            firstParagraph = false
        }
        if (newline < 0) break
        start = end + 1
    }

    val packed = readerPackSlotsV30(raws.map { it.slot }, spec.bodyHeightPx.toFloat())
    val pages = packed.mapIndexed { pageIndex, packedPage ->
        val lines = (packedPage.first..packedPage.last).mapIndexed { k, rawIndex ->
            val raw = raws[rawIndex]
            val top = packedPage.tops[k]
            ReaderLineV30(
                text = raw.text,
                top = top,
                baseline = top + if (raw.title) titleBaseline else bodyBaseline,
                title = raw.title,
                offset = raw.offset,
                units = raw.justified?.first,
                xs = raw.justified?.second,
            )
        }
        val startOffset = if (pageIndex == 0) 0 else lines.firstOrNull { !it.title }?.offset ?: 0
        val leadingGap = raws.getOrNull(packedPage.first)?.slot?.gapBefore ?: 0f
        ReaderPageV30(chapterIndex, pageIndex, lines, startOffset, source.length, packedPage.used, leadingGap)
    }
    // End offsets are the next page's start offsets.
    val finished = pages.mapIndexed { i, page ->
        val end = pages.getOrNull(i + 1)?.startOffset ?: source.length
        ReaderPageV30(page.chapterIndex, page.index, page.lines, page.startOffset, end, page.usedHeight, page.leadingGap)
    }
    return ReaderChapterPagesV30(chapterIndex, chapterId, title, source.length, finished)
}
