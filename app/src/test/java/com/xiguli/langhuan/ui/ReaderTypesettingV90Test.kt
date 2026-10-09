package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTypesettingV90Test {
    private val em = 50f
    private fun widths(units: List<String>, quote: Float = em) =
        FloatArray(units.size) { if (units[it].length == 1 && (readerIsOpeningPunctV90(units[it]) || readerIsClosingPunctV90(units[it]))) quote else em }

    /* ------------------------------ Horizontal ------------------------------ */

    @Test fun firstLineIndentIsExactlyTwoIdeographs() {
        assertEquals(100, readerIndentPxV90(50f, 50f))
        assertEquals(109, readerIndentPxV90(54.4f, 54.4f))
        // A font that reports no ideograph advance falls back to the font size, never zero.
        assertEquals(100, readerIndentPxV90(0f, 50f))
        assertEquals(100, readerIndentPxV90(Float.NaN, 50f))
    }

    @Test fun indentedLineStartsOnTheIndentRail() {
        val units = listOf("她", "的", "目", "光")
        val xs = readerPlaceUnitsV90(units, widths(units), 1000f, startX = 100f, justify = false, fontSizePx = em)
        assertEquals(100f, xs[0], 0f)
        units.indices.drop(1).forEach { assertEquals(xs[it - 1] + em, xs[it], 0.001f) }
    }

    @Test fun openingQuoteAtLineStartDropsItsBlankHalfSoInkMeetsTheRail() {
        val units = listOf("\u201C", "先", "生", "，")
        // Indented paragraph start: the quote box starts half an em left of the indent so the
        // visible mark (right half of the box) begins exactly on the 2-em rail.
        val indented = readerPlaceUnitsV90(units, widths(units), 1000f, 100f, justify = false, fontSizePx = em)
        assertEquals(75f, indented[0], 0.001f)
        assertEquals(100f, indented[0] + em / 2f, 0.001f)
        assertEquals(125f, indented[1], 0.001f)
        // Continuation line: the quote's ink sits on the left margin like any other glyph.
        val wrapped = readerPlaceUnitsV90(units, widths(units), 1000f, 0f, justify = false, fontSizePx = em)
        assertEquals(-25f, wrapped[0], 0.001f)
        assertEquals(25f, wrapped[1], 0.001f)
        listOf("\u300C", "\uFF08", "\u300A", "\u2018").forEach { assertTrue(readerIsOpeningPunctV90(it)) }
        assertFalse(readerIsOpeningPunctV90("中"))
    }

    @Test fun proportionalLatinQuoteIsNotCompressed() {
        val units = listOf("\u201C", "A", "B")
        val narrow = FloatArray(3) { if (it == 0) 12f else em }
        val xs = readerPlaceUnitsV90(units, narrow, 1000f, 0f, justify = false, fontSizePx = em)
        assertEquals(0f, xs[0], 0f)
    }

    @Test fun justifiedLineMeetsTheRightMarginExactly() {
        val units = List(19) { "字" }
        val xs = readerPlaceUnitsV90(units, widths(units), 1000f, 0f, justify = true, fontSizePx = em)
        assertEquals(0f, xs.first(), 0f)
        assertEquals(1000f, xs.last() + em, 0.01f)
        val gaps = xs.toList().zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(gaps.first(), it, 0.001f) }
    }

    @Test fun trailingClosingMarkHangsItsBlankHalfOnJustifiedLines() {
        val units = List(18) { "字" } + "\u3002"
        val xs = readerPlaceUnitsV90(units, widths(units), 1000f, 0f, justify = true, fontSizePx = em)
        // The ink half of 。 ends on the margin; its blank half lies beyond it.
        assertEquals(1000f, xs.last() + em / 2f, 0.01f)
        assertTrue(readerIsClosingPunctV90("\u201D"))
        assertTrue(readerIsClosingPunctV90("\uFF0C"))
    }

    @Test fun indentedJustifiedLineWithQuoteStillEndsOnTheMargin() {
        val units = listOf("\u201C") + List(17) { "字" }
        val xs = readerPlaceUnitsV90(units, widths(units), 1000f, 100f, justify = true, fontSizePx = em)
        assertEquals(100f, xs[0] + em / 2f, 0.001f)
        assertEquals(1000f, xs.last() + em, 0.01f)
    }

    @Test fun lastLineOfParagraphKeepsNaturalSpacing() {
        val units = listOf("好", "的", "\u3002")
        val xs = readerPlaceUnitsV90(units, widths(units), 1000f, 0f, justify = false, fontSizePx = em)
        assertEquals(listOf(0f, 50f, 100f), xs.toList())
    }

    @Test fun forcedBreakIsNotStretched() {
        val units = listOf("https://example.com/a", "/")
        val w = floatArrayOf(300f, 10f)
        val xs = readerPlaceUnitsV90(units, w, 1000f, 0f, justify = true, fontSizePx = em)
        assertEquals(300f, xs[1], 0f)
    }

    /* ------------------------------- Vertical ------------------------------- */

    private fun page(tops: FloatArray, used: Float) = ReaderPackedPageV30(0, tops.lastIndex, tops, used)

    @Test fun fullPageLastLineMeetsTheBottomMarginAndTopStays() {
        // 20 lines of 40px extents on 60px baselines: used = 19*60+40 = 1180 of 1200.
        val tops = FloatArray(20) { it * 60f }
        val aligned = readerJustifyPageVerticallyV90(page(tops, 1180f), 1200f,
            BooleanArray(20) { it > 0 }, BooleanArray(20), lineCapPx = 7.2f, paragraphCapPx = 30f)
        assertEquals(0f, aligned.tops.first(), 0f)
        assertEquals(1200f, aligned.used, 0.01f)
        assertEquals(1160f, aligned.tops.last(), 0.01f)
        val gaps = aligned.tops.toList().zipWithNext { a, b -> b - a }
        gaps.forEach { assertEquals(60f + 20f / 19f, it, 0.01f) }
    }

    @Test fun paragraphGapsTakeTheLargerShare() {
        val tops = floatArrayOf(0f, 60f, 140f, 200f) // gap 2 is a paragraph (60 + 20)
        val paragraph = booleanArrayOf(false, false, true, false)
        val aligned = readerJustifyPageVerticallyV90(page(tops, 240f), 250f,
            booleanArrayOf(false, true, true, true), paragraph, lineCapPx = 7f, paragraphCapPx = 30f)
        val extra = aligned.tops.toList().zipWithNext { a, b -> b - a }.zip(listOf(60f, 80f, 60f)) { g, n -> g - n }
        assertEquals(2f, extra[0], 0.01f)
        assertEquals(6f, extra[1], 0.01f)
        assertEquals(2f, extra[2], 0.01f)
        assertEquals(250f, aligned.used, 0.01f)
    }

    @Test fun cappedGapsRedistributeAndSparsePagesStayNatural() {
        val tops = floatArrayOf(0f, 60f, 140f)
        val aligned = readerJustifyPageVerticallyV90(page(tops, 180f), 300f,
            booleanArrayOf(false, true, true), booleanArrayOf(false, false, true), lineCapPx = 5f, paragraphCapPx = 20f)
        // Caps hold: the page is not stretched into a loose layout to hide a big remainder.
        assertEquals(65f, aligned.tops[1], 0.01f)
        assertEquals(165f, aligned.tops[2], 0.01f)
        assertEquals(205f, aligned.used, 0.01f)
    }

    @Test fun titleLinesAndTheTitleGapNeverMove() {
        // Two title lines, then body; only body→body gaps stretch.
        val tops = floatArrayOf(0f, 80f, 200f, 260f, 320f)
        val stretchable = booleanArrayOf(false, false, false, true, true)
        val aligned = readerJustifyPageVerticallyV90(page(tops, 360f), 370f,
            stretchable, BooleanArray(5), lineCapPx = 10f, paragraphCapPx = 30f)
        assertEquals(0f, aligned.tops[0], 0f)
        assertEquals(80f, aligned.tops[1], 0f)
        assertEquals(200f, aligned.tops[2], 0f)
        assertEquals(265f, aligned.tops[3], 0.01f)
        assertEquals(330f, aligned.tops[4], 0.01f)
        assertEquals(370f, aligned.used, 0.01f)
    }

    @Test fun exactlyFullAndSingleLinePagesAreUntouched() {
        val full = page(floatArrayOf(0f, 60f), 100f)
        assertTrue(full === readerJustifyPageVerticallyV90(full, 100f, booleanArrayOf(false, true), BooleanArray(2), 5f, 5f))
        val single = page(floatArrayOf(0f), 40f)
        assertTrue(single === readerJustifyPageVerticallyV90(single, 100f, booleanArrayOf(false), BooleanArray(1), 5f, 5f))
    }

    @Test fun justificationKeepsPaginationAndOrderStable() {
        // Same slots → same page breaks whether or not pages are then justified: progress,
        // page counts, TTS and bookmark offsets depend only on the packer's first/last.
        val slots = List(300) { index -> ReaderSlotV30(40f, if (index % 6 == 0) 38f else 20f) }
        val packed = readerPackSlotsV30(slots, 1180f, maxCompressionPerGapPx = 3f)
        val lineCap = readerVerticalLineCapV90(60f)
        val paraCap = readerVerticalParagraphCapV90(60f, 18f)
        var expected = 0
        packed.forEachIndexed { index, natural ->
            val n = natural.tops.size
            val aligned = if (index < packed.lastIndex) readerJustifyPageVerticallyV90(natural, 1180f,
                BooleanArray(n) { it > 0 }, BooleanArray(n) { (natural.first + it) % 6 == 0 }, lineCap, paraCap)
            else natural
            assertEquals(expected, aligned.first)
            assertEquals(natural.last, aligned.last)
            assertTrue(aligned.used <= 1180.5f)
            aligned.tops.toList().zipWithNext().forEachIndexed { k, (a, b) -> assertTrue(b - a >= slots[natural.first + k].height) }
            if (index < packed.lastIndex) assertEquals(1180f, aligned.used, 0.5f)
            expected = aligned.last + 1
        }
        assertEquals(slots.size, expected)
    }

    @Test fun capsScaleWithTheLineHeight() {
        assertEquals(7.2f, readerVerticalLineCapV90(60f), 0.001f)
        assertEquals(39f, readerVerticalParagraphCapV90(60f, 18f), 0.001f)
        assertEquals(0f, readerVerticalLineCapV90(-1f), 0f)
    }

    /* ------------------------------ Directory ------------------------------ */

    @Test fun directoryOpensWithTheCurrentChapterInView() {
        assertEquals(0, readerTocInitialIndexV90(0, 421))
        assertEquals(0, readerTocInitialIndexV90(1, 421))
        assertEquals(98, readerTocInitialIndexV90(100, 421))
        assertEquals(420, readerTocInitialIndexV90(500, 421))
        assertEquals(0, readerTocInitialIndexV90(3, 0))
    }
}
