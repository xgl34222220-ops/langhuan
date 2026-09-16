package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuoShuToolsV9ContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun skillsKeepImportUpdatesBindingsAndUninstall() {
        val page = source("src/main/java/com/xiguli/langhuan/ui/SkillsPageV3.kt")
        val panel = source("src/main/java/com/xiguli/langhuan/ui/WritingSkillPanel.kt")

        assertTrue(page.contains("viewModel.importSkill(uri)"))
        assertTrue(page.contains("LanghuanIconButton("))
        assertTrue(page.contains("WritingSkillPanel(viewModel)"))

        assertTrue(panel.contains("store.setEnabled"))
        assertTrue(panel.contains("store.setTaskEnabled"))
        assertTrue(panel.contains("updateClient.check(skill)"))
        assertTrue(panel.contains("store.applyRemoteUpdate"))
        assertTrue(panel.contains("store.uninstall(skillId)"))
        assertTrue(panel.contains("store.resetDefaults()"))
        assertTrue(panel.contains("expandedSkillId"))
        assertTrue(panel.contains("SkillTaskToggle("))
        assertTrue(panel.contains("ModalBottomSheet("))
        assertFalse(panel.contains("HorizontalDivider("))
        assertFalse(panel.contains("AlertDialog("))
    }

    @Test
    fun runCenterKeepsResumeOpenAndAbandonFlow() {
        val source = source("src/main/java/com/xiguli/langhuan/ui/RunCenterPage.kt")

        assertTrue(source.contains("ChapterRunKeepAliveRegistry.state"))
        assertTrue(source.contains("viewModel.refresh()"))
        assertTrue(source.contains("viewModel.refresh(silent = true)"))
        assertTrue(source.contains("viewModel.open(item)"))
        assertTrue(source.contains("viewModel.abandon(item)"))
        assertTrue(source.contains("DurableRunPhase.READY_TO_COMMIT"))
        assertTrue(source.contains("DurableRunPhase.INTERRUPTED"))
        assertTrue(source.contains("LanghuanCard("))
        assertTrue(source.contains("ModalBottomSheet("))
        assertFalse(source.contains("HorizontalDivider("))
        assertFalse(source.contains("AlertDialog("))
    }
}
