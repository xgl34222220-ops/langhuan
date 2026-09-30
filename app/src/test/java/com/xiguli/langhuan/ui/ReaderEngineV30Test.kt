package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEngineV30Test {
    @Test
    fun packerFillsPagesWithoutOverflow() {
        val slots = List(40) { index -> ReaderSlotV30(height = 30f, gapBefore = if (index % 5 == 0) 10f else 0f) }
        val pages = readerPackSlotsV30(slots, pageHeight = 300f)
        assertTrue(pages.isNotEmpty())
        // Every slot lands on exactly one page, in order.
        var expected = 0
        pages.forEach { page ->
            assertEquals(expected, page.first)
            assertEquals(page.last - page.first + 1, page.tops.size)
            assertTrue("page overflows: ${page.used}", page.used <= 300.5f)
            expected = page.last + 1
        }
        assertEquals(slots.size, expected)
    }

    @Test
    fun paragraphGapIsDroppedAtPageTop() {
        val slots = listOf(
            ReaderSlotV30(100f, 0f),
            ReaderSlotV30(100f, 0f),
            ReaderSlotV30(100f, 40f),
        )
        val pages = readerPackSlotsV30(slots, pageHeight = 220f)
        assertEquals(2, pages.size)
        assertEquals(0f, pages[1].tops.first(), 0f)
        assertEquals(100f, pages[1].used, 0f)
    }

    @Test
    fun exactlyFittingLastLineStaysOnPage() {
        val slots = List(10) { ReaderSlotV30(32f, 0f) }
        val pages = readerPackSlotsV30(slots, pageHeight = 320f)
        assertEquals(1, pages.size)
    }

    @Test
    fun oversizedSlotGetsItsOwnPage() {
        val slots = listOf(ReaderSlotV30(50f, 0f), ReaderSlotV30(500f, 0f), ReaderSlotV30(50f, 0f))
        val pages = readerPackSlotsV30(slots, pageHeight = 200f)
        assertEquals(3, pages.size)
        assertEquals(1, pages[1].first)
        assertEquals(1, pages[1].last)
    }

    @Test
    fun ordinaryFullPagesShareFontExtentRailsWithinSpacingBudget() {
        val slots = List(87) { index -> ReaderSlotV30(31f, if (index % 4 == 0) 13f else 0f) }
        val natural = readerPackSlotsV30(slots, 317f)
        natural.forEachIndexed { index, page ->
            val aligned = readerAlignFullPageV41(page, 317f, index < natural.lastIndex, 5f)
            assertEquals(page.first, aligned.first)
            assertEquals(page.last, aligned.last)
            assertEquals(0f, aligned.tops.first(), 0.001f)
            if (index < natural.lastIndex) {
                assertEquals(286f, aligned.tops.last(), 0.001f)
                assertEquals(317f, aligned.used, 0.001f)
            } else {
                assertEquals(page.used, aligned.used, 0f)
                assertTrue(page.tops.contentEquals(aligned.tops))
            }
            assertTrue(aligned.tops.toList().zipWithNext().all { (a, b) -> b - a >= 31f })
        }
    }

    @Test
    fun shortAndTitlePagesAreNeverStretched() {
        val page = ReaderPackedPageV30(0, 1, floatArrayOf(0f, 45f), 76f)
        assertTrue(page === readerAlignFullPageV41(page, 317f, false, 2f))
        val single = ReaderPackedPageV30(0, 0, floatArrayOf(0f), 500f)
        assertTrue(single === readerAlignFullPageV41(single, 317f, true, 2f))
    }

    @Test
    fun lastLineDoesNotReserveUnusedTrailingLeading() {
        // Ten 24px font extents on 32px baselines occupy 312px, not ten 32px boxes.
        val slots = List(10) { ReaderSlotV30(24f, 8f) }
        val pages = readerPackSlotsV30(slots, 312f)
        assertEquals(1, pages.size)
        assertEquals(312f, pages.single().used, .001f)
        assertEquals(0f, pages.single().tops.first(), 0f)
        assertEquals(288f, pages.single().tops.last(), .001f)
    }

    @Test
    fun lineAndParagraphSpacingChangeOnlyTheGaps() {
        for (advance in listOf(30f, 40f, 55f)) for (paragraph in listOf(0f, 10f, 24f)) {
            val slots = List(5) { index -> ReaderSlotV30(24f,
                readerLineGapV43(6f, -18f, advance, if (index == 3) paragraph else 0f)) }
            val page = readerPackSlotsV30(slots, 1000f).single()
            assertEquals(0f, page.tops.first(), 0f)
            page.tops.toList().zipWithNext().forEachIndexed { index, (a, b) ->
                assertEquals(advance + if (index == 2) paragraph else 0f, b - a, .001f)
            }
            assertEquals(4 * advance + paragraph + 24f, page.used, .001f)
        }
    }

    @Test
    fun tallerFallbackExtentsNeverOverlapTheirNeighbours() {
        val extents = listOf(-18f to 6f, -30f to 12f, -18f to 6f)
        val slots = extents.mapIndexed { index, (ascent, descent) ->
            ReaderSlotV30(descent - ascent, if (index == 0) 0f else
                readerLineGapV43(extents[index - 1].second, ascent, 32f, 0f))
        }
        val page = readerPackSlotsV30(slots, 200f).single()
        val baselines = page.tops.mapIndexed { index, top -> top - extents[index].first }
        assertEquals(36f, baselines[1] - baselines[0], .001f)
        assertEquals(32f, baselines[2] - baselines[1], .001f)
        slots.zipWithNext().forEachIndexed { index, (slot, _) ->
            assertTrue(page.tops[index] + slot.height <= page.tops[index + 1])
        }
    }

    @Test
    fun sparseFullPageKeepsUserSpacingInsteadOfStretchingToTheBottom() {
        val page = ReaderPackedPageV30(0, 2, floatArrayOf(0f, 70f, 140f), 190f)
        val aligned = readerAlignFullPageV41(page, 300f, true, 3f)
        assertEquals(73f, aligned.tops[1], .001f)
        assertEquals(146f, aligned.tops[2], .001f)
        assertEquals(196f, aligned.used, .001f)
        assertEquals(0f, aligned.tops.first(), 0f)
    }

    @Test
    fun aSmallSqueezeRecoversTheNextLineWhenItChangesSpacingLess() {
        val slots = List(20) { ReaderSlotV30(24f, 8f) }
        val page = readerPackSlotsV30(slots, 302f, maxCompressionPerGapPx = 2f).first()
        assertEquals(9, page.last)
        assertEquals(302f, page.used, .001f)
        page.tops.toList().zipWithNext().forEach { (a, b) -> assertEquals(30.88889f, b - a, .001f) }
        // At 295px, extending nine lines changes spacing less than squeezing ten.
        assertEquals(8, readerPackSlotsV30(slots, 295f, maxCompressionPerGapPx = 2f).first().last)
        // A smaller budget cannot recover that tenth line, and titles remain natural.
        assertEquals(8, readerPackSlotsV30(slots, 302f, maxCompressionPerGapPx = 1f).first().last)
        assertEquals(8, readerPackSlotsV30(slots, 302f, 2f, naturalPrefixSlots = 1).first().last)
    }

    @Test
    fun compressionPreservesEverySlotAndNeverConsumesFontExtents() {
        val slots = List(217) { index -> ReaderSlotV30(if (index % 11 == 0) 47f else 24f,
            if (index % 3 == 0) 29f else if (index % 7 == 0) 0f else 8f) }
        val pages = readerPackSlotsV30(slots, 302f, maxCompressionPerGapPx = 3f)
        assertEquals(slots.indices.toList(), pages.flatMap { (it.first..it.last).toList() })
        pages.forEach { page ->
            assertTrue(page.used <= 302.5f)
            for (index in 1 until page.tops.size) {
                val gap = page.tops[index] - page.tops[index - 1] - slots[page.first + index - 1].height
                val requested = slots[page.first + index].gapBefore
                assertTrue("gap=$gap", gap >= -.001f)
                assertTrue(requested - gap <= 3.001f)
            }
        }
    }

    @Test
    fun alignmentNeverChangesChosenLinesOrAddsMoreThanSixPercent() {
        for (font in listOf(16f, 20f, 64f)) for (factor in listOf(1.3f, 1.65f, 2f)) {
            val advance = font * factor
            val limit = readerAlignmentLimitV43(font, advance)
            val natural = readerPackSlotsV30(List(55) { index ->
                ReaderSlotV30(font, advance - font + if (index % 3 == 0) 29f else 0f)
            }, 317f)
            natural.forEach { page ->
                val aligned = readerAlignFullPageV41(page, 317f, true, limit)
                assertEquals(page.first, aligned.first)
                assertEquals(page.last, aligned.last)
                assertTrue(aligned.used <= 317.5f)
                for (index in 1 until page.tops.size) {
                    val addition = (aligned.tops[index] - aligned.tops[index - 1]) -
                        (page.tops[index] - page.tops[index - 1])
                    assertTrue("font=$font, factor=$factor, added=$addition", addition in -.001f..(advance * .06f + .001f))
                }
            }
        }
    }

    @Test
    fun justificationSplitsCjkGlyphsAndKeepsLatinWords() {
        val units = readerJustifyUnitsV30("\u3000\u3000他说hello world，好。")
        assertEquals(listOf("\u3000", "\u3000", "他", "说", "hello", " ", "world", "，", "好", "。"), units)
    }

    @Test
    fun justificationKeepsEmojiGraphemesTogether() {
        assertEquals(listOf("看", "👩🏽‍💻", "🇨🇳", "好"), readerJustifyUnitsV30("看👩🏽‍💻🇨🇳好"))
        assertEquals(listOf("好", "❤️", "啊"), readerJustifyUnitsV30("好❤️啊"))
    }

    @Test
    fun turnModesMapFromStoredKeys() {
        assertEquals(ReaderTurnModeV30.COVER, ReaderTurnModeV30.of(null))
        assertEquals(ReaderTurnModeV30.COVER, ReaderTurnModeV30.of("unknown"))
        assertEquals(ReaderTurnModeV30.SLIDE, ReaderTurnModeV30.of("page"))
        assertEquals(ReaderTurnModeV30.SIMULATION, ReaderTurnModeV30.of("simulation"))
        assertEquals(ReaderTurnModeV30.SCROLL, ReaderTurnModeV30.of("scroll"))
        assertEquals(ReaderTurnModeV30.NONE, ReaderTurnModeV30.of("none"))
    }

    @Test
    fun legacyThemeKeysMapOntoV30Palette() {
        assertEquals("tea", readerThemeKeyV30("tomato"))
        assertEquals("green", readerThemeKeyV30("mint"))
        assertEquals("night", readerThemeKeyV30("night"))
        assertEquals("paper", readerThemeKeyV30("whatever"))
        READER_THEMES_V30.forEach { assertEquals(it.key, readerThemeKeyV30(it.key)) }
    }

    @Test
    fun pageLookupByOffsetUsesPageStarts() {
        val pages = listOf(0, 120, 260).mapIndexed { index, start ->
            ReaderPageV30(0, index, emptyList(), start, start + 100, 0f)
        }
        val chapter = ReaderChapterPagesV30(0, "c", "t", 400, pages)
        assertEquals(0, chapter.pageForOffset(0))
        assertEquals(0, chapter.pageForOffset(119))
        assertEquals(1, chapter.pageForOffset(120))
        assertEquals(2, chapter.pageForOffset(399))
        assertEquals(2, chapter.pageForOffset(10_000))
    }

    @Test
    fun readerEntryUsesSingleMeasureAndDrawPipeline() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val dir = "src/main/java/com/xiguli/langhuan/ui/reader"
        val entry = File(root, "$dir/ReaderNativeExperienceV4.kt").readText()
        val engine = File(root, "$dir/ReaderLayoutEngineV30.kt").readText()
        val screen = File(root, "$dir/ReaderScreenV30.kt").readText()
        val render = File(root, "$dir/ReaderRenderV30.kt").readText()

        assertTrue(entry.contains("ReaderEngineV30("))
        assertFalse(entry.contains("key(chapterKey)"))
        assertTrue(engine.contains("StaticLayout.Builder.obtain("))
        assertTrue(render.contains("fun DrawScope.drawReaderTurnV30("))
        assertTrue(screen.contains("ReaderProgressStoreV11.save("))
        assertTrue(screen.contains("Key.VolumeUp"))
        assertFalse("reader must not remount per chapter", screen.contains("key(chapter"))
    }
}
