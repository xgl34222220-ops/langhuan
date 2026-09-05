package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.writing.ChapterEditorExperience
import com.xiguli.langhuan.ui.writing.ChapterEditorPage
import com.xiguli.langhuan.ui.writing.ChapterEditorViewModel
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChapterEditorExperienceContractTest {
    @Test
    fun focusedEditorKeepsAutosaveRewriteVersionAndAdvancedFallback() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/writing/ChapterEditorExperience.kt").readText()
        val viewModel = File(root, "src/main/java/com/xiguli/langhuan/ui/writing/ChapterEditorViewModel.kt").readText()

        assertTrue(editor.contains("viewModel.flushAndClose"))
        assertTrue(editor.contains("viewModel::saveCheckpoint"))
        assertTrue(editor.contains("viewModel.updateContent"))
        assertTrue(editor.contains("viewModel.rewriteSelection"))
        assertTrue(editor.contains("viewModel::applyRewrite"))
        assertTrue(editor.contains("ChapterEditorPage("))
        assertTrue(editor.contains("高级检查"))
        assertTrue(editor.contains("待自动保存"))
        assertTrue(viewModel.contains("delay(1_100)"))
        assertTrue(viewModel.contains("fun openChapter"))
    }
}
