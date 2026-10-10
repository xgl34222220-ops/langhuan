package com.xiguli.langhuan.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V92 follow-up to the V91 polish: shelf progress, reader light/backdrop and TOC fast scroll. */
class ShelfAndReaderV92Test {
    private val root = File(System.getProperty("user.dir") ?: ".")
    private fun source(path: String): String =
        File(root, "src/main/java/com/xiguli/langhuan/$path").readText()

    private fun book(id: String, updated: Long = 0L) =
        ReaderBookUi(id, id, "", "", "", "", 0, 0, 1, updated)

    @Test fun shelfProgressMapsReaderValues() {
        assertEquals(ShelfReadingProgressV92.UNREAD, shelfReadingProgressV92(0L, 3, 10, 0.5f, false))
        val unknownCount = shelfReadingProgressV92(5L, 3, 0, 0.5f, false)
        assertTrue(unknownCount.started)
        assertNull(unknownCount.fraction)
        assertFalse(unknownCount.finished)
        val middle = shelfReadingProgressV92(5L, 4, 10, 0.5f, false)
        assertEquals(0.45f, middle.fraction!!, 0.0001f)
        assertEquals(45, middle.percent)
        // The end flag only counts on the last chapter.
        assertFalse(shelfReadingProgressV92(5L, 8, 10, 0.9f, true).finished)
        val done = shelfReadingProgressV92(5L, 9, 10, 0.8f, true)
        assertTrue(done.finished)
        assertEquals(1f, done.fraction!!, 0f)
        // A stale index beyond a shortened catalogue is treated as unknown, not >100%.
        assertNull(shelfReadingProgressV92(5L, 12, 10, 0.2f, false).fraction)
    }

    @Test fun bookEndNeedsLastPageOfLastChapter() {
        assertTrue(readerPositionIsBookEndV92(chapterIndex = 2, chapterCount = 3, pageIndex = 4, pageCount = 5))
        assertTrue(readerPositionIsBookEndV92(chapterIndex = 0, chapterCount = 1, pageIndex = 0, pageCount = 1))
        assertFalse(readerPositionIsBookEndV92(chapterIndex = 2, chapterCount = 3, pageIndex = 3, pageCount = 5))
        assertFalse(readerPositionIsBookEndV92(chapterIndex = 1, chapterCount = 3, pageIndex = 4, pageCount = 5))
        assertFalse(readerPositionIsBookEndV92(chapterIndex = 0, chapterCount = 0, pageIndex = 0, pageCount = 0))
    }

    @Test fun progressSortPutsFinishedFirstAndUnreadLast() {
        val books = listOf(book("unread", 50), book("half", 10), book("done", 5), book("quarter", 20))
        val progress = mapOf(
            "half" to shelfReadingProgressV92(1L, 5, 10, 0f, false),
            "done" to shelfReadingProgressV92(1L, 9, 10, 0.9f, true),
            "quarter" to shelfReadingProgressV92(1L, 2, 10, 0.5f, false),
        )
        val sorted = luoSortBooksV33(
            books = books,
            sort = LuoShelfSortV33.PROGRESS,
            progress = { progress[it.id]?.sortKey ?: -1f },
            lastRead = { 0L },
        )
        assertEquals(listOf("done", "half", "quarter", "unread"), sorted.map { it.id })
        assertEquals(LuoShelfSortV33.PROGRESS, LuoShelfSortV33.of("progress"))
    }

    @Test fun readerLightValues() {
        assertEquals(-1f, readerWindowBrightnessV92(READER_BRIGHTNESS_SYSTEM_V92), 0f)
        assertEquals(READER_MIN_BRIGHTNESS_V92, readerWindowBrightnessV92(0f), 0f)
        assertEquals(1f, readerWindowBrightnessV92(3f), 0f)
        assertEquals(0f, readerWarmLightAlphaV92(0f, dark = false), 0f)
        assertTrue(readerWarmLightAlphaV92(1f, dark = true) < readerWarmLightAlphaV92(1f, dark = false))
        assertTrue(readerWarmLightAlphaV92(5f, dark = false) <= 0.30f)
        assertEquals(ReaderBackdropV92.NONE, ReaderBackdropV92.of("missing"))
        assertEquals(ReaderBackdropV92.XUAN, ReaderBackdropV92.of("xuan"))
    }

    @Test fun centerCropFillsThePage() {
        val (wideOffset, wideSize) = readerCenterCropV92(IntSize(2000, 1000), Size(1000f, 2000f))
        assertEquals(IntSize(500, 1000), wideSize)
        assertEquals(IntOffset(750, 0), wideOffset)
        val (tallOffset, tallSize) = readerCenterCropV92(IntSize(1000, 4000), Size(1000f, 2000f))
        assertEquals(IntSize(1000, 2000), tallSize)
        assertEquals(IntOffset(0, 1000), tallOffset)
    }

    @Test fun tocFastScrollMapsTouchToChapter() {
        assertEquals(0, readerTocFastScrollIndexV92(-20f, 1000f, 500))
        assertEquals(499, readerTocFastScrollIndexV92(5000f, 1000f, 500))
        assertEquals(250, readerTocFastScrollIndexV92(500f, 1000f, 501))
        assertEquals(0, readerTocFastScrollIndexV92(10f, 0f, 500))
        assertEquals(0f, readerTocThumbFractionV92(0, 10, 500), 0f)
        assertEquals(1f, readerTocThumbFractionV92(490, 10, 500), 0f)
        assertEquals(0f, readerTocThumbFractionV92(3, 20, 10), 0f)
        assertTrue(READER_TOC_FAST_SCROLL_MIN_V92 in 30..200)
    }

    @Test fun onlineCoversAreDownsampledForTheList() {
        assertEquals(1, onlineCoverSampleSizeV92(300, 400))
        assertEquals(2, onlineCoverSampleSizeV92(1000, 1400))
        assertEquals(8, onlineCoverSampleSizeV92(4000, 6000))
        val screen = source("ui/online/OnlineBooksScreenV50.kt")
        assertTrue(screen.contains("decodeOnlineCoverV92(fetchSourceBytesV36(coverUrl, maxBytes = 2 * 1024 * 1024))"))
    }

    @Test fun liveScreensAreWired() {
        val home = source("ui/LanghuanHomeV4.kt")
        // V94: the run badge sits on the 创作 bottom tab; the shelf still owns the observer.
        assertTrue(source("ui/LanghuanRootV4.kt").contains("badge = runActive"))
        assertTrue(home.contains("internal fun rememberRunCenterActiveV92()"))
        assertTrue(home.contains("collectAsStateWithLifecycle(initialValue = false)"))
        assertTrue(home.contains(".flowOn(Dispatchers.Default)"))
        assertTrue(home.contains("chapterRunRuntimeIfStarted"))
        assertTrue(home.contains("fun count(value: Int): Int? = if (loadingShelf) null else value"))
        assertTrue(home.contains("HomeShelfTabV4(HOME_TAB_FINISHED_V4, \"已读完\""))
        val app = source("LanghuanApplication.kt")
        assertTrue(app.contains("chapterRunRuntimeLazy.isInitialized()"))
        val library = source("ui/LibraryExperience.kt")
        assertTrue(library.contains("}.flowOn(Dispatchers.Default).collect { books ->"))
        val window = source("ui/reader/ReaderWindowSessionV27.kt")
        assertTrue(window.contains("screenBrightness = oldBrightness"))
        val screen = source("ui/reader/ReaderScreenV30.kt")
        assertTrue(screen.contains("ReaderWarmLightOverlayV92(warmth = settings.warmth, dark = theme.dark)"))
        assertTrue(screen.contains("ShelfReadingProgressStoreV92.finishedKey(book.id)"))
        val render = source("ui/reader/ReaderRenderV30.kt")
        assertTrue(render.contains("theme.backdrop?.let { drawReaderBackdropV92(it, geometry.width, geometry.height) }"))
        val menu = source("ui/reader/ReaderMenuV30.kt")
        assertTrue(menu.contains("ReaderLightAndBackdropSectionV92(settings = settings)"))
        assertTrue(menu.contains("ReaderTocFastScrollerV92("))
    }
}
