package com.xiguli.langhuan.ui

import androidx.compose.ui.geometry.Offset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionV30Test {
    // Body: two paragraphs; the first wraps onto two lines.
    private val body = "第一段第一行第一段第二行\n第二段"
    private val geometry = ReaderGeometryV30(400f, 800f, 20f, 380f, 100f, 700f, 60f, 760f)
    private val page = ReaderPageV30(
        chapterIndex = 0,
        index = 0,
        lines = listOf(
            ReaderLineV30("第一段第一行", 0f, 24f, false, 0, null, null),
            ReaderLineV30("第一段第二行", 32f, 56f, false, 6, null, null),
            ReaderLineV30("第二段", 74f, 98f, false, 13, null, null),
        ),
        startOffset = 0,
        endOffset = body.length,
        usedHeight = 106f,
    )

    @Test
    fun longPressOnSecondLineSelectsWholeFirstParagraph() {
        val picked = readerParagraphAtV30(page, Offset(50f, 100f + 40f), geometry, body)!!
        assertEquals(0, picked.start)
        assertEquals(12, picked.end)
        assertEquals("第一段第一行第一段第二行", picked.text)
        assertEquals(100f, picked.top, 0f)
        assertEquals(174f, picked.bottom, 0f)
    }

    @Test
    fun secondParagraphIsSelectedOnItsOwn() {
        val picked = readerParagraphAtV30(page, Offset(50f, 100f + 80f), geometry, body)!!
        assertEquals("第二段", picked.text)
        assertEquals(13, picked.start)
    }

    @Test
    fun pressesOutsideTheBodyDoNothing() {
        assertNull(readerParagraphAtV30(page, Offset(50f, 40f), geometry, body))
        assertNull(readerParagraphAtV30(page, Offset(50f, 750f), geometry, body))
    }

    @Test
    fun readerWiresLongPressWithoutBreakingTaps() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val screen = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderScreenV30.kt").readText()
        assertTrue(screen.contains("viewConfiguration.longPressTimeoutMillis"))
        assertTrue(screen.contains("!longPressed && abs(totalX) <= slop"))
        assertTrue(screen.contains("ReaderSelectionBarV30("))
    }
}
