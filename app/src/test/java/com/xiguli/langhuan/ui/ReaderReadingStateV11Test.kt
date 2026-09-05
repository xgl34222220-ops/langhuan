package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.reader.ReaderPageModeV10
import com.xiguli.langhuan.ui.reader.ReaderThemePresetV11
import com.xiguli.langhuan.ui.reader.readerExcerptAtV11
import com.xiguli.langhuan.ui.reader.readerLocationLabelV11
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadingStateV11Test {
    @Test
    fun excerptTracksProgressInsteadOfAlwaysUsingChapterStart() {
        val text = (1..240).joinToString(" ") { "词$it" }
        val start = readerExcerptAtV11(text, 0f, 50)
        val late = readerExcerptAtV11(text, .86f, 50)
        assertTrue(start.contains("词1"))
        assertFalse(late.contains("词1 "))
        assertTrue(late.isNotBlank())
    }

    @Test
    fun pageLocationUsesHumanPageNumber() {
        assertEquals("第 12 章 · 第 4 页", readerLocationLabelV11(12, 3, 0, ReaderPageModeV10.PAGE))
    }

    @Test
    fun scrollLocationKeepsVerticalPosition() {
        assertEquals("第 8 章 · 章首", readerLocationLabelV11(8, 0, 0, ReaderPageModeV10.SCROLL))
        assertEquals("第 8 章 · 纵向位置 1260", readerLocationLabelV11(8, 0, 1260, ReaderPageModeV10.SCROLL))
    }

    @Test
    fun themePresetRetainsFullReadingConfiguration() {
        val preset = ReaderThemePresetV11(
            id = "p1",
            name = "夜读",
            themeKey = "night",
            fontKey = "serif",
            pageModeKey = ReaderPageModeV10.COVER.key,
            fontSize = 21f,
            lineFactor = 1.9f,
            sidePadding = 28f,
            customBg = "#FF111111",
            customFg = "#FFECECEC",
        )
        assertEquals("night", preset.themeKey)
        assertEquals(ReaderPageModeV10.COVER.key, preset.pageModeKey)
        assertEquals(21f, preset.fontSize)
        assertEquals("#FFECECEC", preset.customFg)
    }
}
