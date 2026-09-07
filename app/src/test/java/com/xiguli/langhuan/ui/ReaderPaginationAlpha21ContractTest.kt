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
        assertTrue(reader.contains("point.x < size.width * .28f -> previousPage()"))
        assertTrue(reader.contains("point.x > size.width * .72f -> nextPage()"))
        assertTrue(reader.contains("else -> panelVisible = true"))
    }

    @Test
    fun pageBodyUsesAnIntegerLineGrid() {
        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(paginator.contains("val lineBoxHeight = measurer.measure("))
        assertTrue(paginator.contains("rawBodyHeight / lineBoxHeight"))
        assertTrue(paginator.contains("* lineBoxHeight"))
    }
}
