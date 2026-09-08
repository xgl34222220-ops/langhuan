package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceKnowledgeRoutingContractTest {
    @Test
    fun workspaceRoutesKnowledgeMemoryAndAiToDistinctSurfaces() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val workspace = File(root, "src/main/java/com/xiguli/langhuan/ui/workspace/LanghuanBookWorkspaceV1.kt").readText()
        val intelligence = File(root, "src/main/java/com/xiguli/langhuan/ui/StoryIntelligencePage.kt").readText()
        val memory = File(root, "src/main/java/com/xiguli/langhuan/ui/workspace/ProjectMemoryPageV1.kt").readText()

        assertTrue(workspace.contains("WorkspaceToolV1.OUTLINE"))
        assertTrue(workspace.contains("WorkspaceToolV1.WORLD"))
        assertTrue(workspace.contains("WorkspaceToolV1.CHARACTERS"))
        assertTrue(workspace.contains("WorkspaceToolV1.TIMELINE"))
        assertTrue(workspace.contains("WorkspaceToolV1.MEMORY"))
        assertTrue(workspace.contains("StoryIntelligenceSectionV1.OUTLINE"))
        assertTrue(workspace.contains("StoryIntelligenceSectionV1.WORLD"))
        assertTrue(workspace.contains("StoryIntelligenceSectionV1.CHARACTERS"))
        assertTrue(workspace.contains("StoryIntelligenceSectionV1.TIMELINE"))
        assertTrue(workspace.contains("ProjectMemoryPageV1("))

        assertTrue(intelligence.contains("enum class StoryIntelligenceSectionV1"))
        assertTrue(intelligence.contains("OUTLINE(\"大纲\""))
        assertTrue(intelligence.contains("WORLD(\"世界\""))
        assertTrue(intelligence.contains("CHARACTERS(\"角色\""))
        assertTrue(intelligence.contains("TIMELINE(\"时间\""))
        assertTrue(intelligence.contains("Canon 世界与规则"))
        assertTrue(intelligence.contains("角色状态与弧线"))
        assertTrue(intelligence.contains("伏笔雷达"))

        assertTrue(memory.contains("Canon 单一事实源"))
        assertTrue(memory.contains("CandidateCanonPanel(state, vm)"))
        assertTrue(memory.contains("relationshipNotes"))
        assertFalse(memory.contains("AgentPage("))

        assertTrue(workspace.contains("title = \"项目记忆\""))
        assertTrue(workspace.contains("title = \"AI 助手\""))
        assertFalse(workspace.contains("title = \"项目记忆\"\n                        subtitle = if (aiReady)"))
    }
}
