package com.xiguli.langhuan.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StartupSafeRouteV2Test {
    @Test
    fun compatibilityRoutesStayOnMaterialOnlyReaderAndShelf() {
        val root = File(System.getProperty("user.dir"))
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV7.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderFirstBookV7.kt").readText()
        val theme = File(root, "src/main/java/com/xiguli/langhuan/ui/theme/Theme.kt").readText()

        assertTrue(shelf.contains("ReaderShelfV6("))
        assertTrue(reader.contains("ReaderFirstBookV6("))
        assertTrue(!theme.contains("RealMiuixTheme"))
    }
}
