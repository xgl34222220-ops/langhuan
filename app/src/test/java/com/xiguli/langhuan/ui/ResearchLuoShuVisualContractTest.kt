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
}
