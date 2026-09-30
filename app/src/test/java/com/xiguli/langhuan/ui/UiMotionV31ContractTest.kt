package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiMotionV31ContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")
    private fun source(path: String): String = File(root, "src/main/java/com/xiguli/langhuan/ui/$path").readText()

    @Test
    fun sharedKitUsesOneMotionVocabulary() {
        val motion = source("design/LanghuanMotionV31.kt")
        val uiKit = source("design/LanghuanUiKit.kt")
        assertTrue(motion.contains("fun Modifier.springClickV31("))
        assertTrue(motion.contains("fun Modifier.enterOnceV31("))
        assertTrue(motion.contains("HapticFeedbackType.LongPress"))
        assertTrue(uiKit.contains("onClick: (() -> Unit)? = null"))
        assertTrue(uiKit.contains("pressScaleV31(interaction"))
        assertTrue(uiKit.contains("Crossfade(targetState = icon"))
    }

    @Test
    fun shelfFeelsTactileAndNeverShowsABareSpinner() {
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        assertTrue(shelf.contains("LuoShelfSkeletonV31()"))
        assertTrue(shelf.contains("enterOnceV31(enter, book.id, index)"))
        assertTrue(shelf.contains("onLongClick = { onLongPress(book) }"))
        assertTrue(shelf.contains("animateDpAsState((slot + gap) * activeIndex"))
        assertFalse(shelf.contains("CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }"))
    }

    @Test
    fun readerMenuCanBeSwipedAway() {
        val menu = source("reader/ReaderMenuV30.kt")
        assertTrue(menu.contains("if (dragY.value > dismissPx) onDismiss()"))
        assertTrue(menu.contains("HapticFeedbackType.TextHandleMove"))
    }

    @Test
    fun authoringScreensShareTheMotionSystem() {
        val workspace = source("WritingWorkspaceLuoShuV11.kt")
        val editor = source("writing/ChapterEditorExperience.kt")
        val runCenter = source("RunCenterPage.kt")
        val ai = source("AiProviderSetupPage.kt")
        assertTrue(workspace.contains("LanghuanSkeletonV31("))
        assertTrue(workspace.contains("enterOnceV31(enter, \"mission\", 1)"))
        assertTrue(workspace.contains("label = \"quickAction\""))
        assertTrue(editor.contains("label = \"saveState\""))
        assertTrue(editor.contains("visible = selectedText.isNotBlank()"))
        assertTrue(runCenter.contains("rememberInfiniteTransition(label = \"runPulse\")"))
        assertTrue(ai.contains("label = \"routingChevron\""))
    }
}
