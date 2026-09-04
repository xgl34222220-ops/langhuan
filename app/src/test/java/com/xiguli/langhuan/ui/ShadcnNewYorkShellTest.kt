package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadcnNewYorkShellTest {
    @Test
    fun shadcnStaysAComponentBaselineWhileMainShellIsMiuixMobileFirst() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfMobileExperience.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt").readText()
        val kit = File(root, "src/main/java/com/xiguli/langhuan/ui/design/ShadcnCompose.kt").readText()
        val theme = File(root, "src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt").readText()

        // Production shelf must read as a native phone reading app, not a responsive web dashboard.
        assertTrue(shelf.contains("MobileLibraryPage("))
        assertTrue(shelf.contains("MobileShelfDock("))
        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertTrue(shelf.contains("RoundedCornerShape(18.dp)"))
        assertTrue(shelf.contains("CircleShape"))
        assertFalse(shelf.contains("NavigationBar("))
        assertFalse(shelf.contains("NavigationBarItem("))
        assertFalse(shelf.contains("TopAppBar("))
        assertFalse(shelf.contains("ShelfShadcnBottomBar("))

        // Reader remains reading-native: choices and direct step controls, not web-form sliders.
        assertTrue(reader.contains("MobileThemeChoice("))
        assertTrue(reader.contains("MobilePresetRow("))
        assertTrue(reader.contains("MobileRoundControl("))
        assertTrue(reader.contains("MobileBookInfoSheet("))
        assertFalse(reader.contains("ReaderCoreSlider("))
        assertFalse(reader.contains("ShadcnTabs("))
        assertFalse(reader.contains("FilterChip("))
        assertFalse(reader.contains("ReaderCoreBoundary("))

        // shadcn stays only as source-owned primitives; the visual system itself is MIUIx/Material mobile.
        assertTrue(kit.contains("enum class ShadcnButtonVariant"))
        assertTrue(kit.contains("fun ShadcnInput("))
        assertTrue(theme.contains("dynamicColor: Boolean = true"))
        assertTrue(theme.contains("accent = colors.primaryContainer"))
        assertTrue(theme.contains("radiusMd = 16.dp"))
        assertTrue(theme.contains("large = RoundedCornerShape(24.dp)"))
    }
}
