package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationLuoShuVisualContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun creationChatUsesCalmLuoShuSurfaceInsteadOfFullscreenSpatialDecoration() {
        val chat = source("src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt")
        assertFalse(chat.contains("LanghuanAmbientBackdrop"))
        assertFalse(chat.contains("LanghuanConstellationField"))
        assertFalse(chat.contains("BorderStroke"))
        assertTrue(chat.contains("MaterialTheme.typography.headlineSmall"))
        assertTrue(chat.contains("LanghuanGlassPanel("))
        assertTrue(chat.contains("radius = 24.dp"))
        assertTrue(chat.contains("color = t.accent"))
        assertTrue(chat.contains("onSyncProposal = viewModel::syncConversationProposal"))
        assertTrue(chat.contains("viewModel.generateFoundation"))
        assertTrue(chat.contains("onCreate = viewModel::createCurrentFoundation"))
    }

    @Test
    fun sharedSpatialPrimitivesNoLongerAnimatePageBackgroundsOrDrawConstellations() {
        val spatial = source("src/main/java/com/xiguli/langhuan/ui/design/LanghuanSpatialMotion.kt")
        assertFalse(spatial.contains("rememberInfiniteTransition"))
        assertFalse(spatial.contains("Canvas("))
        assertFalse(spatial.contains("BorderStroke"))
        assertFalse(spatial.contains("drawCircle"))
        assertTrue(spatial.contains("color = t.card.copy(alpha = .94f)"))
        assertTrue(spatial.contains("shadowElevation = 2.dp"))
        assertTrue(spatial.contains("shape = RoundedCornerShape(24.dp)"))
        assertTrue(spatial.contains("style = MaterialTheme.typography.headlineSmall"))
    }
}
