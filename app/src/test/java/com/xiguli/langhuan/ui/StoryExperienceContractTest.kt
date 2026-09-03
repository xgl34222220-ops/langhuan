package com.xiguli.langhuan.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoryExperienceContractTest {
    @Test
    fun immersiveStoryOwnsCommonPlayerWorldBranchAndDraftControls() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val story = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryExperience.kt").readText()
        val wrapper = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryManagementExperience.kt").readText()
        val management = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryManagementScreen.kt").readText()

        assertTrue(story.contains("StoryManagementExperience("))
        assertTrue(story.contains("说什么，或做什么"))
        assertTrue(story.contains("世界正在回应"))
        assertTrue(wrapper.contains("StoryManagementScreen("))
        assertTrue(management.contains("storyVm.updateProfile"))
        assertTrue(management.contains("storyVm.updateWorld"))
        assertTrue(management.contains("vm.renameBranch"))
        assertTrue(management.contains("vm.duplicateBranch"))
        assertTrue(management.contains("vm.deleteBranch"))
        assertTrue(management.contains("storyVm.generateChapterDraft"))
        assertTrue(management.contains("NpcMemoryViewModelV1"))
        assertTrue(management.contains("StoryRoleEntrySnapshotViewModelV1"))
        assertTrue(management.contains("StoryDraftAdoptionViewModel"))
        assertTrue(management.contains("空间 / 感知"))
        assertFalse(management.contains("Text(\"完整工具\")"))
    }
}
