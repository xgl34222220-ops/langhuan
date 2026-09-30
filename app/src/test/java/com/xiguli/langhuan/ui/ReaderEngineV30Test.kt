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
    fun fullBodyPagesShareFirstAndLastLineRails() {
        val slots = List(87) { index -> ReaderSlotV30(31f, if (index % 4 == 0) 13f else 0f) }
        val natural = readerPackSlotsV30(slots, 317f)
        natural.forEachIndexed { index, page ->
            val aligned = readerAlignFullPageV41(page, 317f, index < natural.lastIndex)
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
        assertTrue(page === readerAlignFullPageV41(page, 317f, false))
        val single = ReaderPackedPageV30(0, 0, floatArrayOf(0f), 500f)
        assertTrue(single === readerAlignFullPageV41(single, 317f, true))
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
