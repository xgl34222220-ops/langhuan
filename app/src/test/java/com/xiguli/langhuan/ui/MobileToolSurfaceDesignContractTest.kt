package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileToolSurfaceDesignContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun secondaryToolsStayNativeAndProgressivelyDisclosed() {
        val ai = File(root, "src/main/java/com/xiguli/langhuan/ui/AiProviderSetupPage.kt").readText()
        val runCenter = File(root, "src/main/java/com/xiguli/langhuan/ui/RunCenterPage.kt").readText()
        val skillsPage = File(root, "src/main/java/com/xiguli/langhuan/ui/SkillsPageV3.kt").readText()
        val skills = File(root, "src/main/java/com/xiguli/langhuan/ui/WritingSkillPanel.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfMobileExperience.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderMobileExperience.kt").readText()
        val design = File(root.parentFile ?: root, "design-systems/langhuan/DESIGN.md").takeIf { it.exists() }?.readText().orEmpty()

        assertFalse(ai.contains("WritingSkillPanel("))
        assertFalse(ai.contains("LanghuanBadge("))
        assertTrue(ai.contains("var showRouting by remember { mutableStateOf(false) }"))
        assertTrue(ai.contains("任务模型路由"))

        assertFalse(runCenter.contains("LanghuanCard("))
        assertFalse(runCenter.contains("\"IDLE\""))
        assertFalse(skillsPage.contains("LanghuanCard("))
        assertFalse(skills.contains("LanghuanCard("))
        assertTrue(skills.contains("expandedSkillId"))

        // Shelf v3: one recent-reading anchor + cover wall, no floating dock.
        assertTrue(shelf.contains("ContinueReadingHeroV3("))
        assertTrue(shelf.contains("MobileShelfNavigationV3("))
        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertFalse(shelf.contains("MobileShelfDock("))
        assertFalse(shelf.contains("shadowElevation = 5.dp"))

        // Reader v3: single quiet page counter, attached controls, progressive settings.
        assertTrue(reader.contains("\"${'$'}page / ${'$'}pageCount\""))
        assertTrue(reader.contains("MobileReaderChromeV3("))
        assertTrue(reader.contains("if (!advancedOpen)"))
        assertTrue(reader.contains("高级排版"))
        assertFalse(reader.contains("\"琅嬛\", style = MaterialTheme.typography.labelSmall"))
        assertFalse(reader.contains("故事\", palette.foreground"))
        assertFalse(reader.contains("编辑\", palette.foreground"))

        assertTrue(design.contains("HeroUI Native"))
        assertTrue(design.contains("tweakcn"))
        assertTrue(design.contains("Ponytail"))
    }
}
