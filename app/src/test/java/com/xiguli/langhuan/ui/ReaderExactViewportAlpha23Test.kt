package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderExactViewportAlpha23Test {
    private fun source(path: String): String = File(System.getProperty("user.dir") ?: ".", path).readText()

    @Test fun paginationUsesMeasuredBodyViewportInsteadOfOnlyScreenPrediction() {
        val reader = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderQingmoHeroV13.kt")
        val paginator = source("src/main/java/com/xiguli/langhuan/ui/reader/ReaderMeasuredPaginationV18.kt")
        assertTrue(reader.contains("var measuredBodyViewport"))
        assertTrue(reader.contains(".onSizeChanged(onBodyViewportChanged)"))
        assertTrue(reader.contains("viewportWidthPx = measuredBodyViewport.width"))
        assertTrue(reader.contains("viewportHeightPx = measuredBodyViewport.height"))
        assertTrue(paginator.contains("viewportWidthPx: Int = 0"))
        assertTrue(paginator.contains("viewportHeightPx: Int = 0"))
        assertTrue(paginator.contains("val bodyWidth = viewportWidthPx.takeIf { it > 0 } ?: fallbackBodyWidth"))
        assertTrue(paginator.contains("val bodyHeight = viewportHeightPx.takeIf { it > 0 } ?: fallbackBodyHeight"))
        assertTrue(paginator.contains("textAlign = TextAlign.Justify"))
    }
}
