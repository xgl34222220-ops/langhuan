package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterEditorLuoShuVisualContractTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun chapterEditorUsesLuoShuSurfacesWithoutDroppingEditingFlow() {
        val source = source("src/main/java/com/xiguli/langhuan/ui/ChapterEditorPage.kt")

        assertTrue(source.contains("LanghuanIconButton"))
        assertTrue(source.contains("LanghuanCard"))
        assertTrue(source.contains("editorTextFieldColors"))
        assertFalse(source.contains("TopAppBar("))
        assertFalse(source.contains("OutlinedTextField("))
        assertFalse(source.contains("OutlinedButton("))

        assertTrue(source.contains("saveCheckpoint"))
        assertTrue(source.contains("rewriteSelection"))
        assertTrue(source.contains("analyzeChronology"))
        assertTrue(source.contains("generateChronologyRepair"))
        assertTrue(source.contains("analyzeDependencies"))
        assertTrue(source.contains("generateRepairPlan"))
        assertTrue(source.contains("viewModel.restore(version)"))
        assertTrue(source.contains("applyRewrite"))
        assertTrue(source.contains("rejectRewriteAndLearn"))
    }
}
