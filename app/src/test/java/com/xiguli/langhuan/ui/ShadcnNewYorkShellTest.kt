package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadcnNewYorkShellTest {
    @Test
    fun shadcnStaysAComponentBaselineWhileMainShellIsMobileFirst() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfMobileExperience.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt").readText()
        val kit = File(root, "src/main/java/com/xiguli/langhuan/ui/design/ShadcnCompose.kt").readText()
        val theme = File(root, "src/main/java/com/xiguli/langhuan/ui/theme/LanghuanTheme.kt").readText()

        // The shelf is book-first, not a desktop dashboard copied onto a phone.
        assertTrue(shelf.contains("MobileLibraryPage("))
        assertTrue(shelf.contains("MobileShelfDock("))
        assertTrue(shelf.contains("GridCells.Adaptive"))
        assertTrue(shelf.contains("LanghuanShape.card"))
        assertTrue(shelf.contains("CircleShape"))
        assertFalse(shelf.contains("NavigationBar("))
        assertFalse(shelf.contains("NavigationBarItem("))
        assertFalse(shelf.contains("TopAppBar("))
        assertFalse(shelf.contains("ShelfShadcnBottomBar("))

        // Reader settings use reader-native choices and step controls, not a web form of sliders.
        assertTrue(reader.contains("MobileThemeChoice("))
        assertTrue(reader.contains("MobilePresetRow("))
        assertTrue(reader.contains("MobileRoundControl("))
        assertTrue(reader.contains("MobileBookInfoSheet("))
        assertFalse(reader.contains("ReaderCoreSlider("))
        assertFalse(reader.contains("ShadcnTabs("))
        assertFalse(reader.contains("FilterChip("))
        assertFalse(reader.contains("ReaderCoreBoundary("))

        // shadcn remains useful underneath as reusable source-owned primitives and neutral tokens.
        assertTrue(kit.contains("enum class ShadcnButtonVariant"))
        assertTrue(kit.contains("fun ShadcnInput("))
        assertTrue(theme.contains("accent = colors.surfaceContainerHigh"))
        assertTrue(theme.contains("radiusMd = LanghuanRadius.card"))
    }
}
