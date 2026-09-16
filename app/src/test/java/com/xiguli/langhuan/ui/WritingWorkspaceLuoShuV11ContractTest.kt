package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingWorkspaceLuoShuV11ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun productionEntryUsesLuoShuV11InsteadOfLegacyVisibleWorkspace() {
        val entry = source("src/main/java/com/xiguli/langhuan/ui/WritingWorkspaceV10.kt")
        assertTrue(entry.contains("WritingWorkspaceLuoShuV11("))
        assertFalse(entry.contains("WritingWorkspaceV8("))
    }

    @Test
    fun luoShuWorkspaceKeepsFullAuthoringFlow() {
        val source = source("src/main/java/com/xiguli/langhuan/ui/WritingWorkspaceLuoShuV11.kt")

        assertTrue(source.contains("LanghuanCard"))
        assertTrue(source.contains("LanghuanGlassPanel"))
        assertTrue(source.contains("LanghuanIconButton"))
        assertFalse(source.contains("BorderStroke"))
        assertFalse(source.contains("OutlinedTextField"))
        assertFalse(source.contains("OutlinedButton"))

        assertTrue(source.contains("WorkspaceNaturalLanguageRouter.route"))
        assertTrue(source.contains("conversationVm.send"))
        assertTrue(source.contains("canonVm.propose"))
        assertTrue(source.contains("viewModel.planScenes"))
        assertTrue(source.contains("viewModel.applyScenePlan"))
        assertTrue(source.contains("viewModel.generate"))
        assertTrue(source.contains("viewModel.cancelGeneration"))
        assertTrue(source.contains("viewModel.repairAndRegenerate"))
        assertTrue(source.contains("viewModel.commitAndReview"))
        assertTrue(source.contains("viewModel.reviewCommittedChapter"))
        assertTrue(source.contains("viewModel.confirmCandidateFact"))
        assertTrue(source.contains("viewModel.rejectCandidateFact"))
        assertTrue(source.contains("viewModel.advanceToNext"))
        assertTrue(source.contains("精修正文 · 保存后仍可反复修改"))
        assertTrue(source.contains("ProjectWorkflowTraceSheetV7"))
        assertTrue(source.contains("CanonMigrationQueueSheetV8"))
        assertTrue(source.contains("RunInspectorPanel"))
    }
}
