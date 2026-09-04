package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadcnNewYorkShellTest {
    @Test
    fun shelfUsesCompactShadcnPrimitivesInsteadOfMaterialNavigationShell() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfCoreExperience.kt").readText()
        val kit = File(root, "src/main/java/com/xiguli/langhuan/ui/design/ShadcnCompose.kt").readText()
        val theme = File(root, "src/main/java/com/xiguli/langhuan/ui/theme/LanghuanStableTheme.kt").readText()

        assertTrue(shelf.contains("ShadcnInput("))
        assertTrue(shelf.contains("ShadcnCard("))
        assertTrue(shelf.contains("ShadcnMenuRow("))
        assertTrue(shelf.contains("ShelfShadcnBottomBar("))
        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertTrue(shelf.contains("book.coverPath"))
        assertFalse(shelf.contains("TopAppBar("))
        assertFalse(shelf.contains("NavigationBar("))
        assertFalse(shelf.contains("NavigationBarItem("))
        assertFalse(shelf.contains("RoundedCornerShape(20.dp)"))
        assertFalse(shelf.contains("RoundedCornerShape(18.dp)"))

        assertTrue(kit.contains("enum class ShadcnButtonVariant"))
        assertTrue(kit.contains("ShadcnButtonVariant.OUTLINE"))
        assertTrue(kit.contains("fun ShadcnInput("))
        assertTrue(kit.contains("fun ShadcnTabs("))
        assertTrue(kit.contains("shadowElevation = 1.dp"))

        assertTrue(theme.contains("radiusMd = 8.dp"))
        assertTrue(theme.contains("radiusLg = 10.dp"))
        assertTrue(theme.contains("background = Color(0xFFFAFAFA)"))
        assertTrue(theme.contains("outlineVariant = Color(0xFFE4E4E7)"))
    }
}
