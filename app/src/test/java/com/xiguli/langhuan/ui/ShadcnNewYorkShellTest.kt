package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadcnNewYorkShellTest {
    @Test
    fun shadcnStaysAComponentBaselineWhileMainShellUsesLuoShuMobileGeometry() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfMobileExperience.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt").readText()
        val kit = File(root, "src/main/java/com/xiguli/langhuan/ui/design/ShadcnCompose.kt").readText()
        val theme = File(root, "src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt").readText()
        val tokens = File(root, "src/main/java/com/xiguli/langhuan/ui/design/LanghuanDesignTokens.kt").readText()

        assertTrue(shelf.contains("MobileLibraryPageV3("))
        assertTrue(shelf.contains("MobileShelfNavigationV3("))
        assertTrue(shelf.contains("ContinueReadingHeroV3("))
        assertFalse(shelf.contains("MobileShelfDock("))
        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertTrue(shelf.contains("RoundedCornerShape(14.dp)"))
        assertFalse(shelf.contains("NavigationBar("))
        assertFalse(shelf.contains("NavigationBarItem("))
        assertFalse(shelf.contains("TopAppBar("))
        assertFalse(shelf.contains("ShadcnButton("))
        assertFalse(shelf.contains("ShadcnInput("))

        assertTrue(reader.contains("ReaderThemeChoiceV3("))
        assertTrue(reader.contains("ReaderPresetRowV3("))
        assertTrue(reader.contains("ReaderRoundControlV3("))
        assertTrue(reader.contains("MobileBookInfoSheetV3("))
        assertTrue(reader.contains("var advancedOpen"))
        assertTrue(reader.contains("if (!advancedOpen)"))
        assertFalse(reader.contains("ReaderCoreSlider("))
        assertFalse(reader.contains("ShadcnTabs("))
        assertFalse(reader.contains("FilterChip("))
        assertFalse(reader.contains("ReaderCoreBoundary("))

        assertTrue(kit.contains("enum class ShadcnButtonVariant"))
        assertTrue(kit.contains("fun ShadcnInput("))
        // v3：主题三档 + v3 token 映射，圆角仅 8/12/16/24。
        assertTrue(theme.contains("themeMode: LanghuanThemeMode = LanghuanThemeMode.FOLLOW_SYSTEM"))
        assertTrue(theme.contains("primaryContainer = LanghuanLightUiTokens.accent"))
        // LuoShu / MIUIX hierarchy is the active shell contract; Shadcn stays a component library.
        assertTrue(tokens.contains("radiusMd = 12.dp"))
        assertTrue(theme.contains("large = RoundedCornerShape(16.dp)"))
        assertTrue(theme.contains("extraLarge = RoundedCornerShape(24.dp)"))
    }
}
