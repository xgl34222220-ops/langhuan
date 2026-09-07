package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReferenceDensityAlpha22Test {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun renderAndPaginationUseSameCompactChromeGeometry() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        val pager = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(reader.contains("top = 8.dp, bottom = 6.dp"))
        assertTrue(reader.contains("Spacer(Modifier.height(7.dp))"))
        assertTrue(reader.contains("Spacer(Modifier.height(4.dp))"))
        assertTrue(pager.contains("pageTop = with(density) { 8.dp.roundToPx() }"))
        assertTrue(pager.contains("pageBottom = with(density) { 6.dp.roundToPx() }"))
        assertTrue(pager.contains("headerGap = with(density) { 7.dp.roundToPx() }"))
        assertTrue(pager.contains("footerGap = with(density) { 4.dp.roundToPx() }"))
    }

    @Test
    fun matureReaderDefaultsUseContentFirstDensity() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        assertTrue(reader.contains("1.56f"))
        assertTrue(reader.contains("initialSide = if (densityMigrationNeeded) 18f"))
        assertTrue(reader.contains("reader_density_v22"))
        assertTrue(reader.contains("textAlign = TextAlign.Justify"))
        assertTrue(reader.contains("point.x < size.width * .33f -> previousPage()"))
        assertTrue(reader.contains("point.x > size.width * .67f -> nextPage()"))
    }

    @Test
    fun paginatorDoesNotThrowAwayAWholeLineForAlignment() {
        val pager = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertFalse(pager.contains("rawBodyHeight / lineBoxHeight"))
        assertFalse(pager.contains("* lineBoxHeight"))
        assertTrue(pager.contains("lastCompleteLineEndV18"))
    }
}
