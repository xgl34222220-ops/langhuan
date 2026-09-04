package com.xiguli.langhuan.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullShellV4RouteTest {
    @Test
    fun rootUsesCleanOnePassShelfReaderAndStoryCores() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV3.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfCoreExperience.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderCoreExperience.kt").readText()
        val story = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryCoreExperience.kt").readText()
        val storyEntry = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryCleanExperience.kt").readText()
        val creation = File(root, "src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/writing/ChapterEditorExperience.kt").readText()

        assertTrue(router.contains("ShelfCoreExperience("))
        assertTrue(router.contains("ReaderCoreExperience("))
        assertTrue(router.contains("StoryCleanExperience("))
        assertTrue(router.contains("pendingBookId"))
        assertTrue(router.contains("ReaderProgressStoreV11.load"))
        assertFalse(router.contains("route == RootRouteV3.BOOK && libraryState.openedBook == null"))
        assertFalse(router.contains("ReaderShelfV9("))
        assertFalse(router.contains("ReaderExperienceEntryGuard("))

        assertTrue(shelf.contains("openingBookId"))
        assertTrue(shelf.contains("onOpenBook(book.id)"))
        assertTrue(shelf.contains("AI 新建小说"))
        assertTrue(shelf.contains("导入本地小说"))
        assertFalse(shelf.contains("LanghuanCard("))
        assertFalse(shelf.contains("ReaderShelfV9"))

        assertTrue(reader.contains("rememberPagerState("))
        assertTrue(reader.contains("initialPage ="))
        assertTrue(reader.contains("ReaderProgressStoreV11.load"))
        assertTrue(reader.contains("ReaderProgressStoreV11.moveTo"))
        assertTrue(reader.contains("WindowInsets.safeDrawing"))
        assertTrue(reader.contains("pagerState.settledPage"))
        assertTrue(reader.contains("detectTapGestures"))
        assertTrue(reader.contains("appliedLayoutKey"))
        assertFalse(reader.contains("ReaderResumeGate"))
        assertFalse(reader.contains("ReaderQingmoChrome("))
        assertFalse(reader.contains("ReaderPagedLayoutV14("))

        assertTrue(story.contains("StoryPlayV3ViewModel"))
        assertTrue(story.contains("说什么，或做什么"))
        assertTrue(story.contains("故事分支"))
        assertTrue(story.contains("世界状态"))
        assertFalse(story.contains("StoryPlayPanelV17("))
        assertTrue(storyEntry.contains("StoryCoreExperience("))
        assertFalse(storyEntry.contains("StoryPlayPanelV17("))

        assertTrue(router.contains("CreationChatV4("))
        assertTrue(router.contains("ChapterEditorExperience("))
        assertTrue(router.contains("RootRouteV3.CREATION_RESEARCH"))
        assertTrue(editor.contains("viewModel.rewriteSelection"))
        assertTrue(creation.contains("高级研究 / Reference DNA"))
        assertTrue(creation.contains("viewModel::syncConversationProposal"))
        assertTrue(creation.contains("viewModel::createCurrentFoundation"))
    }
}
