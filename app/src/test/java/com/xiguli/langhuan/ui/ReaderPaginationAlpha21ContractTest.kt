package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginationAlpha21ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun pagerCannotRestWithTwoPagesVisible() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("Modifier.fillMaxSize().clipToBounds().nestedScroll(edgeSwipe)"))
        assertTrue(reader.contains("beyondViewportPageCount = 0"))
        assertTrue(reader.contains("pagerState.currentPageOffsetFraction"))
        assertTrue(reader.contains("abs(offset) > 0.001f"))
        assertTrue(reader.contains("pagerState.scrollToPage(pagerState.currentPage.coerceIn(0, pagerPageCount - 1))"))
    }

    @Test
    fun pageOwnsLeftCenterRightTapZones() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains(".pointerInput(chapter.id, pagerPage, panelVisible)"))
        assertTrue(reader.contains("point.x < size.width * .33f -> previousPage()"))
        assertTrue(reader.contains("point.x > size.width * .67f -> nextPage()"))
        assertTrue(reader.contains("else -> panelVisible = true"))
    }

    @Test
    fun pageBodyUsesFullMeasuredViewportWithoutArtificialBlankBand() {
        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(paginator.contains("val fallbackBodyHeight = ("))
        assertTrue(paginator.contains("val bodyHeight = viewportHeightPx.takeIf { it > 0 } ?: fallbackBodyHeight"))
        assertTrue(paginator.contains("val bodyWidth = viewportWidthPx.takeIf { it > 0 } ?: fallbackBodyWidth"))
        assertTrue(paginator.contains("stableHeight - pageTop - headerHeight - headerGap - footerGap - footerHeight - pageBottom - rasterGuard"))
        assertTrue(!paginator.contains("rawBodyHeight / lineBoxHeight"))
    }
}
