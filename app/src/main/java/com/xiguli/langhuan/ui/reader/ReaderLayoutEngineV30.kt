package com.xiguli.langhuan.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.roundToInt

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
    /** Per-line font extents, including any fallback font used for CJK or emoji. */
    val ascent: Float = top - baseline,
    val descent: Float = 0f,
) {
    val bottom: Float get() = baseline + descent
}

internal class ReaderPageV30(
    val chapterIndex: Int,
    val index: Int,
    val lines: List<ReaderLineV30>,
    val startOffset: Int,
    val endOffset: Int,
    val usedHeight: Float,
    /** Inter-line/paragraph gap swallowed at the page top; scroll mode restores it. */
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

/** Font extent and the preceding whitespace, not a box containing trailing line leading. */
internal data class ReaderSlotV30(val height: Float, val gapBefore: Float)

internal data class ReaderPackedPageV30(val first: Int, val last: Int, val tops: FloatArray, val used: Float)

/**
 * Packs measured lines into pages. Paragraph gaps are dropped at the top of a page so every
 * page starts flush with the body top. A slot that is taller than the page gets a page alone.
 */
internal fun readerPackSlotsV30(
    slots: List<ReaderSlotV30>,
    pageHeight: Float,
    maxCompressionPerGapPx: Float = 0f,
    naturalPrefixSlots: Int = 0,
): List<ReaderPackedPageV30> {
    if (slots.isEmpty()) return emptyList()
    val result = ArrayList<ReaderPackedPageV30>()
    var first = 0
    var y = 0f
    val tops = ArrayList<Float>()
    val compression = ArrayList<Float>()
    var compressionBudget = 0f
    // A tiny tolerance absorbs float rounding so an exactly fitting last line is not pushed.
    val limit = pageHeight + 0.5f

    fun finish(last: Int, shrink: Boolean = false) {
        val deficit = if (shrink) (y - pageHeight).coerceAtLeast(0f) else 0f
        val fraction = if (compressionBudget > 0f) deficit / compressionBudget else 0f
        var removed = 0f
        val placed = FloatArray(tops.size) { index ->
            removed += compression[index] * fraction
            tops[index] - removed
        }
        result += ReaderPackedPageV30(first, last, placed, y - deficit)
        first = last + 1
        y = 0f
        tops.clear()
        compression.clear()
        compressionBudget = 0f
    }

    slots.forEachIndexed { index, slot ->
        val gap = if (index == first) 0f else slot.gapBefore
        val gapBudget = if (first >= naturalPrefixSlots) minOf(gap, maxCompressionPerGapPx).coerceAtLeast(0f) else 0f
        if (index > first && y + gap + slot.height > limit) {
            val deficit = y + gap + slot.height - pageHeight
            val budget = compressionBudget + gapBudget
            val expansion = if (tops.size > 1) (pageHeight - y) / (tops.size - 1) else Float.POSITIVE_INFINITY
            // Prefer the smaller spacing change: either fill this page gently, or recover
            // the next line with a slight squeeze. Never overlap fallback font extents.
            if (slot.height <= pageHeight && deficit <= budget && deficit / tops.size < expansion) {
                tops += y + gap
                compression += gapBudget
                compressionBudget = budget
                y += gap + slot.height
                finish(index, shrink = true)
                return@forEachIndexed
            }
            finish(index - 1)
        }
        val placedGap = if (index == first) 0f else gap
        tops += y + placedGap
        compression += if (index == first) 0f else gapBudget
        compressionBudget += compression.last()
        y += placedGap + slot.height
    }
    if (tops.isNotEmpty()) finish(slots.lastIndex)
    return result
}

/**
 * Bring the last font extent towards the bottom rail without changing the top rail.
 * A bounded addition to each existing gap avoids loose pages at large sizes/paragraph gaps.
 * Chapter endings, title pages and oversized/single-line pages retain natural spacing.
 */
internal fun readerAlignFullPageV41(
    page: ReaderPackedPageV30,
    pageHeight: Float,
    isFullBodyPage: Boolean,
    maxExtraPerGapPx: Float,
): ReaderPackedPageV30 {
    val remainder = pageHeight - page.used
    if (!isFullBodyPage || page.tops.size < 2 || remainder <= 0f || !remainder.isFinite() ||
        maxExtraPerGapPx <= 0f || !maxExtraPerGapPx.isFinite()) return page
    val interval = (remainder / (page.tops.size - 1)).coerceAtMost(maxExtraPerGapPx)
    return page.copy(
        tops = FloatArray(page.tops.size) { index -> page.tops[index] + interval * index },
        used = page.used + interval * (page.tops.size - 1),
    )
}

/** At most 6% of the requested baseline advance, also limited to one tenth of an em. */
internal fun readerAlignmentLimitV43(fontSizePx: Float, lineHeightPx: Float): Float =
    minOf(fontSizePx * .10f, lineHeightPx * .06f).coerceAtLeast(0f)

/** Largest extra space a line gap may receive from vertical justification. */
internal fun readerVerticalLineCapV90(lineHeightPx: Float): Float = (lineHeightPx * .12f).coerceAtLeast(0f)

/** Largest extra space a paragraph gap may receive from vertical justification. */
internal fun readerVerticalParagraphCapV90(lineHeightPx: Float, paragraphGapPx: Float): Float =
    (lineHeightPx * .5f + paragraphGapPx.coerceAtLeast(0f) * .5f).coerceAtLeast(0f)

/** The requested line height is a baseline advance. A taller fallback font must still fit. */
internal fun readerLineGapV43(
    previousDescent: Float,
    ascent: Float,
    baselineAdvance: Float,
    paragraphGap: Float,
): Float = (baselineAdvance - (previousDescent - ascent)).coerceAtLeast(0f) + paragraphGap.coerceAtLeast(0f)

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

private fun readerStaticLayoutV30(
    text: String,
    paint: TextPaint,
    width: Int,
    leftIndents: IntArray? = null,
): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIndents(leftIndents, null)
        .setIncludePad(false)
        .setUseLineSpacingFromFallbacks(true)
        .setLineSpacing(0f, 1f)
        .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
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
            var end = i + n
            var regionalCount = if (cp in 0x1F1E6..0x1F1FF) 1 else 0
            while (end < text.length) {
                val next = text.codePointAt(end)
                val type = Character.getType(next)
                val combining = type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()
                when {
                    combining || next in 0xFE00..0xFE0F || next in 0xE0100..0xE01EF ||
                        next in 0x1F3FB..0x1F3FF -> end += Character.charCount(next)
                    next == 0x200D && end + 1 < text.length -> {
                        end++ // Keep the joiner and its following glyph in the same draw unit.
                        end += Character.charCount(text.codePointAt(end))
                    }
                    regionalCount == 1 && next in 0x1F1E6..0x1F1FF -> {
                        end += Character.charCount(next)
                        regionalCount++
                    }
                    else -> break
                }
            }
            units += text.substring(i, end)
            i = end
        }
    }
    return units
}

/** Opening punctuation whose blank leading half is removed at the start of a line (CLREQ). */
private const val READER_OPENING_PUNCT_V90 = "\u201C\u2018\u300C\u300E\uFF08\u300A\u3008\u3010\u3014\u3016\uFF3B\uFF5B"

/** Closing punctuation whose blank trailing half may hang at the end of a justified line. */
private const val READER_CLOSING_PUNCT_V90 =
    "\u201D\u2019\u300D\u300F\uFF09\u300B\u3009\u3011\u3015\u3017\uFF3D\uFF5D\uFF0C\u3002\u3001\uFF1B\uFF1A\uFF01\uFF1F"

internal fun readerIsOpeningPunctV90(unit: String): Boolean = unit.length == 1 && unit[0] in READER_OPENING_PUNCT_V90
internal fun readerIsClosingPunctV90(unit: String): Boolean = unit.length == 1 && unit[0] in READER_CLOSING_PUNCT_V90

/**
 * Places one body line on the measure.
 *
 * * [startX] is the exact first-line indent (two ideographic advances) or 0.
 * * A full-width opening quote/bracket at the start of the line drops its blank left half, so
 *   its ink starts on the same rail as the text of every other line.
 * * On a justified line a full-width closing mark at the end drops its blank right half, so the
 *   ink of the last glyph meets the right margin like a plain character does.
 * * Justified lines spread the remaining space evenly between units; the last line of a
 *   paragraph keeps natural spacing. A gap wider than 0.9 em means a forced break (long URL),
 *   which is left unstretched.
 *
 * [widths] are the measured advances of [units]; the function is pure so it is unit tested.
 */
internal fun readerPlaceUnitsV90(
    units: List<String>,
    widths: FloatArray,
    measure: Float,
    startX: Float,
    justify: Boolean,
    fontSizePx: Float,
): FloatArray {
    val xs = FloatArray(units.size)
    if (units.isEmpty()) return xs
    val fullWidth = fontSizePx * 0.7f
    val leadShift = if (readerIsOpeningPunctV90(units.first()) && widths[0] >= fullWidth) widths[0] / 2f else 0f
    val trailTrim = if (justify && units.size > 1 && readerIsClosingPunctV90(units.last()) &&
        widths[units.lastIndex] >= fullWidth) widths[units.lastIndex] / 2f else 0f
    var gap = 0f
    if (justify && units.size > 1) {
        val content = widths.sum() - leadShift - trailTrim
        val free = (measure - startX) - content
        val candidate = free / (units.size - 1)
        // Small negative values only absorb float rounding of the line breaker.
        if (free > -1f && candidate <= fontSizePx * 0.9f) gap = candidate
    }
    var x = startX - leadShift
    for (k in units.indices) {
        xs[k] = x
        x += widths[k]
        if (k < units.lastIndex) x += gap
    }
    return xs
}

private fun readerPlaceLineV90(
    text: String,
    paint: TextPaint,
    measure: Float,
    startX: Float,
    justify: Boolean,
): Pair<Array<String>, FloatArray>? {
    if (text.isEmpty()) return null
    val units = readerJustifyUnitsV30(text)
    if (units.isEmpty()) return null
    val widths = FloatArray(units.size) { paint.measureText(units[it]) }
    val xs = readerPlaceUnitsV90(units, widths, measure, startX, justify, paint.textSize)
    return units.toTypedArray() to xs
}

/** Two ideographic advances of [paint], rounded to whole pixels so line breaking and drawing agree. */
internal fun readerIndentPxV90(ideographAdvance: Float, fontSizePx: Float): Int {
    val advance = if (ideographAdvance.isFinite() && ideographAdvance > 0f) ideographAdvance else fontSizePx
    return (advance * 2f).roundToInt().coerceAtLeast(0)
}

/**
 * Vertical justification of a full page: the leftover space below the last line is spread over
 * the gaps between body lines, so the last line of every full page sits on the bottom margin.
 * Paragraph gaps take a larger share than line gaps (weights 3:1), and every gap has a cap so a
 * sparse page (huge type, or a page broken before an oversized slot) is not visibly distorted.
 * The top rail and every gap marked not [stretchable] (title lines, title→body gap) stay fixed.
 *
 * [stretchable] and [paragraph] are indexed by the line that follows the gap (index 0 unused).
 */
internal fun readerJustifyPageVerticallyV90(
    page: ReaderPackedPageV30,
    pageHeight: Float,
    stretchable: BooleanArray,
    paragraph: BooleanArray,
    lineCapPx: Float,
    paragraphCapPx: Float,
): ReaderPackedPageV30 {
    val n = page.tops.size
    val remainder = pageHeight - page.used
    if (n < 2 || remainder <= 0.01f || !remainder.isFinite()) return page
    val weight = FloatArray(n)
    val cap = FloatArray(n)
    for (k in 1 until n) {
        if (!stretchable.getOrElse(k) { false }) continue
        val para = paragraph.getOrElse(k) { false }
        weight[k] = if (para) 3f else 1f
        cap[k] = (if (para) paragraphCapPx else lineCapPx).coerceAtLeast(0f)
    }
    val extra = FloatArray(n)
    var left = remainder
    repeat(4) {
        var active = 0f
        for (k in 1 until n) if (weight[k] > 0f && extra[k] < cap[k] - 1e-4f) active += weight[k]
        if (active <= 0f || left <= 1e-3f) return@repeat
        val share = left / active
        for (k in 1 until n) {
            if (weight[k] <= 0f || extra[k] >= cap[k] - 1e-4f) continue
            val add = minOf(weight[k] * share, cap[k] - extra[k])
            extra[k] += add
            left -= add
        }
    }
    val added = remainder - left
    if (added <= 0.01f) return page
    var shift = 0f
    val tops = FloatArray(n) { index ->
        shift += extra[index]
        page.tops[index] + shift
    }
    return page.copy(tops = tops, used = page.used + added)
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
    class Raw(
        val text: String,
        val title: Boolean,
        val offset: Int,
        val justified: Pair<Array<String>, FloatArray>?,
        val ascent: Float,
        val descent: Float,
        val slot: ReaderSlotV30,
    )

    val raws = ArrayList<Raw>()
    if (title.isNotBlank()) {
        val layout = readerStaticLayoutV30(title, titlePaint, width)
        for (li in 0 until layout.lineCount) {
            val text = title.substring(layout.getLineStart(li), layout.getLineEnd(li)).trimEnd()
            val ascent = layout.getLineAscent(li).toFloat()
            val descent = layout.getLineDescent(li).toFloat()
            val gap = raws.lastOrNull()?.let {
                readerLineGapV43(it.descent, ascent, spec.titleLineHeightPx, 0f)
            } ?: 0f
            raws += Raw(text, true, 0, null, ascent, descent, ReaderSlotV30(descent - ascent, gap))
        }
    }

    // The first-line indent is an exact two-ideograph measure given to the line breaker as a
    // pixel indent (not as U+3000 characters whose width depends on the fallback font).
    val indentPx = if (spec.indent) readerIndentPxV90(bodyPaint.measureText("\u4E2D"), spec.fontSizePx)
        .coerceAtMost(width / 2) else 0
    val firstLineIndents = if (indentPx > 0) intArrayOf(indentPx, 0) else null
    val paragraphStarts = ArrayList<Boolean>()
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
            val layout = readerStaticLayoutV30(content, bodyPaint, width, firstLineIndents)
            for (li in 0 until layout.lineCount) {
                val ls = layout.getLineStart(li)
                val le = layout.getLineEnd(li)
                val text = content.substring(ls, le).trimEnd('\n', ' ')
                val isLast = li == layout.lineCount - 1
                val startX = if (li == 0) indentPx.toFloat() else 0f
                val placed = readerPlaceLineV90(text, bodyPaint, width.toFloat(), startX, justify = !isLast)
                val ascent = layout.getLineAscent(li).toFloat()
                val descent = layout.getLineDescent(li).toFloat()
                val previous = raws.lastOrNull()
                val paragraphStart = li == 0 && !firstParagraph
                val gap = when {
                    previous == null -> 0f
                    previous.title -> spec.titleGapPx.coerceAtLeast(0f)
                    else -> readerLineGapV43(previous.descent, ascent, spec.lineHeightPx,
                        if (paragraphStart) spec.paragraphGapPx else 0f)
                }
                val offset = start + leading + ls
                raws += Raw(text, false, offset, placed, ascent, descent, ReaderSlotV30(descent - ascent, gap))
                paragraphStarts += paragraphStart || (li == 0 && firstParagraph)
            }
            firstParagraph = false
        }
        if (newline < 0) break
        start = end + 1
    }
    val titleCount = raws.size - paragraphStarts.size

    val alignmentLimit = readerAlignmentLimitV43(spec.fontSizePx, spec.lineHeightPx)
    val packed = readerPackSlotsV30(raws.map { it.slot }, spec.bodyHeightPx.toFloat(),
        maxCompressionPerGapPx = alignmentLimit, naturalPrefixSlots = titleCount)
    val lineCap = readerVerticalLineCapV90(spec.lineHeightPx)
    val paragraphCap = readerVerticalParagraphCapV90(spec.lineHeightPx, spec.paragraphGapPx)
    val pages = packed.mapIndexed { pageIndex, naturalPage ->
        // Every page except the chapter's last is a full page; its last line meets the bottom
        // margin. Title lines and the title→body gap stay natural; only body gaps stretch.
        val packedPage = if (pageIndex < packed.lastIndex) {
            val count = naturalPage.last - naturalPage.first + 1
            val stretchable = BooleanArray(count) { k ->
                k > 0 && !raws[naturalPage.first + k].title && !raws[naturalPage.first + k - 1].title
            }
            val paragraph = BooleanArray(count) { k ->
                val raw = naturalPage.first + k
                raw >= titleCount && paragraphStarts[raw - titleCount]
            }
            readerJustifyPageVerticallyV90(naturalPage, spec.bodyHeightPx.toFloat(), stretchable, paragraph,
                lineCap, paragraphCap)
        } else naturalPage
        val lines = (packedPage.first..packedPage.last).mapIndexed { k, rawIndex ->
            val raw = raws[rawIndex]
            val top = packedPage.tops[k]
            ReaderLineV30(
                text = raw.text,
                top = top,
                baseline = top - raw.ascent,
                title = raw.title,
                offset = raw.offset,
                units = raw.justified?.first,
                xs = raw.justified?.second,
                ascent = raw.ascent,
                descent = raw.descent,
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
