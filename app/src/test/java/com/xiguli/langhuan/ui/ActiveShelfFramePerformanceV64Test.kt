package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveShelfFramePerformanceV64Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun activeShelfDockDefersAnimatedPlacementAndKeepsRevisionUnboxed() {
        val source = File(
            root,
            "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLuoShuFunctionalV1.kt",
        ).readText()

        assertTrue(source.contains(".offset {\n                        IntOffset("))
        assertTrue(source.contains("x = (pillX + (slot - indicatorWidth) / 2).roundToPx()"))
        assertFalse(source.contains(".offset(x = pillX"))
        assertTrue(source.contains("var shelfRevision by rememberSaveable { mutableIntStateOf(0) }"))
        assertFalse(source.contains("var shelfRevision by rememberSaveable { mutableStateOf(0) }"))
    }
}
