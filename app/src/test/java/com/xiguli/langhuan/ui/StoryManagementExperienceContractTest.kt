package com.xiguli.langhuan.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoryManagementExperienceContractTest {
    @Test
    fun managementUsesFunctionalScreenAndExposesMatureStoryTools() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val wrapper = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryManagementExperience.kt").readText()
        val screen = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryManagementScreen.kt").readText()

        assertTrue(wrapper.contains("StoryManagementScreen("))
        assertTrue(screen.contains("NPC 记忆"))
        assertTrue(screen.contains("原著入场"))
        assertTrue(screen.contains("章节草稿"))
        assertTrue(screen.contains("NpcMemoryViewModelV1"))
        assertTrue(screen.contains("StoryRoleEntrySnapshotViewModelV1"))
        assertTrue(screen.contains("StoryDraftAdoptionViewModel"))
        assertTrue(screen.contains("applyStoryRoleEntrySnapshotToWorldV1"))
        assertTrue(screen.contains("备份原稿并采用"))
        assertTrue(screen.contains("空间 / 感知"))
        assertFalse(screen.contains("Text(\"完整工具\")"))
    }
}
