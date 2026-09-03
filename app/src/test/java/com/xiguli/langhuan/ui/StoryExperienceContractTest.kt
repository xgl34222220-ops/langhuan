package com.xiguli.langhuan.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoryExperienceContractTest {
    @Test
    fun immersiveStoryOwnsCommonPlayerWorldBranchAndDraftControls() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val story = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryExperience.kt").readText()
        val management = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryManagementExperience.kt").readText()

        assertTrue(story.contains("StoryManagementExperience("))
        assertTrue(story.contains("说什么，或做什么"))
        assertTrue(story.contains("世界正在回应"))
        assertTrue(management.contains("vm.updateProfile"))
        assertTrue(management.contains("vm.updateWorld"))
        assertTrue(management.contains("vm.renameBranch"))
        assertTrue(management.contains("vm.duplicateBranch"))
        assertTrue(management.contains("vm.deleteBranch"))
        assertTrue(management.contains("vm.generateChapterDraft"))
        assertTrue(management.contains("完整工具"))
    }
}
