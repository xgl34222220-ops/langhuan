package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchLuoShuVisualContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun researchWorkspaceUsesLuoShuChromeWithoutDroppingCreationFlow() {
        val source = source("src/main/java/com/xiguli/langhuan/ui/ResearchNewBookConversationPage.kt")

        assertTrue(source.contains("LanghuanIconButton"))
        assertTrue(source.contains("LanghuanGlassPanel"))
        assertTrue(source.contains("LanghuanSpatialHero"))
        assertFalse(source.contains("TopAppBar("))
        assertFalse(source.contains("border = BorderStroke"))
        assertFalse(source.contains("color = if (user) t.foreground else t.card"))

        assertTrue(source.contains("WebResearchEngine"))
        assertTrue(source.contains("CreationResearchArchiveStore"))
        assertTrue(source.contains("addConversationAttachments"))
        assertTrue(source.contains("syncConversationProposal"))
        assertTrue(source.contains("generateFoundation(false)"))
        assertTrue(source.contains("createCurrentFoundation"))
    }

    @Test
    fun referenceDnaSurfacesStayLuoShuAndKeepFullDataBrowser() {
        val picker = source("src/main/java/com/xiguli/langhuan/ui/ReferenceTemplateSelectionPanel.kt")
        val browser = source("src/main/java/com/xiguli/langhuan/ui/ReferenceDistillationDataBrowser.kt")
        val report = source("src/main/java/com/xiguli/langhuan/ui/ReferenceDistillationReportDialog.kt")

        assertFalse(picker.contains("AlertDialog("))
        assertFalse(picker.contains("BorderStroke"))
        assertFalse(picker.contains("OutlinedButton("))
        assertTrue(picker.contains("ReferenceDistillationDataBrowserDialog"))
        assertTrue(picker.contains("setReferenceTemplateIds"))

        assertFalse(browser.contains("BorderStroke"))
        assertFalse(browser.contains("OutlinedTextField("))
        assertTrue(browser.contains("DnaCategory(\"CHARACTER\""))
        assertTrue(browser.contains("DnaCategory(\"POWER\""))
        assertTrue(browser.contains("DnaCategory(\"PLOT\""))
        assertTrue(browser.contains("DnaCategory(\"MYSTERY\""))
        assertTrue(browser.contains("DnaCategory(\"WORLD\""))
        assertTrue(browser.contains("report.retrievalItems + report.items"))

        assertFalse(report.contains("AlertDialog("))
        assertFalse(report.contains("BorderStroke"))
        assertTrue(report.contains("Story DNA · 作品结构"))
        assertTrue(report.contains("Style DNA · 写法"))
        assertTrue(report.contains("KEEP · 可借鉴的高层机制"))
        assertTrue(report.contains("TRANSFORM · 必须原创化改造"))
        assertTrue(report.contains("AVOID · 禁止照搬"))
    }
}
