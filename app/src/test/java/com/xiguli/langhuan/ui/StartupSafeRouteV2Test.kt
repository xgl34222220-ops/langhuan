package com.xiguli.langhuan.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StartupSafeRouteV2Test {
    @Test
    fun compatibilityRoutesStayOnMaterialOnlyReaderAndShelf() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV7.kt").readText()
        val shelfUi = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV6.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderFirstBookV7.kt").readText()
        val stableReader = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderFirstBookStableV9.kt").readText()
        val theme = File(root, "src/main/java/com/xiguli/langhuan/ui/theme/LanghuanTheme.kt").readText()

        assertTrue(shelf.contains("ReaderShelfV6("))
        assertTrue(shelf.contains("onDeleteBook = onDeleteBook"))
        assertTrue(shelfUi.contains("onDeleteBook: (String) -> Unit"))
        assertTrue(shelfUi.contains("删除小说"))
        assertTrue(reader.contains("ReaderFirstBookStableV9("))
        assertTrue(!reader.contains("miuix", ignoreCase = true))
        assertTrue(!stableReader.contains("miuix", ignoreCase = true))
        assertTrue(!theme.contains("RealMiuixTheme"))
    }
}
