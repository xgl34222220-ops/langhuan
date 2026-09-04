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

        // AI service owns connections/models. Skill management stays in its own screen.
        assertFalse(ai.contains("WritingSkillPanel("))
        assertFalse(ai.contains("LanghuanBadge("))
        assertTrue(ai.contains("var showRouting by remember { mutableStateOf(false) }"))
        assertTrue(ai.contains("任务模型路由"))
        assertTrue(ai.contains("需要时再展开"))

        // Run center and Skill surfaces are lists/groups, not dashboard card walls.
        assertFalse(runCenter.contains("LanghuanCard("))
        assertFalse(runCenter.contains("\"IDLE\""))
        assertFalse(skillsPage.contains("LanghuanCard("))
        assertFalse(skills.contains("LanghuanCard("))
        assertTrue(skills.contains("expandedSkillId"))
        assertTrue(skills.contains("SkillGroup("))

        // The shelf uses a restrained native bottom bar instead of a floating oversized pill.
        assertTrue(shelf.contains("MobileShelfNavigation("))
        assertFalse(shelf.contains("MobileShelfDock("))

        // Reader renders a single quiet page counter and no branded footer.
        assertTrue(reader.contains("\"$page / $pageCount\""))
        assertFalse(reader.contains("\"琅嬛\", style = MaterialTheme.typography.labelSmall"))
        assertFalse(reader.contains("chapterIndex + 1"))
    }
}
