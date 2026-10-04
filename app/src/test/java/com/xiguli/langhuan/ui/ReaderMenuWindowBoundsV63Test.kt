package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMenuWindowBoundsV63Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun readerMenuLimitsUseTheActualWindowInsteadOfTheDeviceScreen() {
        val source = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMenuV30.kt",
        ).readText()

        assertTrue(source.contains("LocalWindowInfo.current.containerSize.height.toDp()"))
        assertEquals(9, Regex("readerMenuWindowHeightV63\\(\\)").findAll(source).count())
        assertFalse(source.contains("LocalConfiguration"))
        assertFalse(source.contains("screenHeightDp"))
    }
}
