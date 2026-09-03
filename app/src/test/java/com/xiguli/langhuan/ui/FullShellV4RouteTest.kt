package com.xiguli.langhuan.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullShellV4RouteTest {
    @Test
    fun rootUsesFirstClassShelfCreationReaderAndTavernExperiences() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV3.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV9.kt").readText()
        val creation = File(root, "src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt").readText()

        assertTrue(router.contains("ReaderShelfV9("))
        assertTrue(router.contains("ReaderExperience("))
        assertTrue(router.contains("CreationChatV4("))
        assertTrue(router.contains("StoryPlayPanelV17("))
        assertTrue(router.contains("RootRouteV3.CREATION_RESEARCH"))
        assertTrue(shelf.contains("title = \"书架\""))
        assertTrue(shelf.contains("AI 新建小说"))
        assertTrue(shelf.contains("移动到其他书架"))
        assertTrue(shelf.contains("进入故事"))
        assertTrue(shelf.contains("onDeleteBook(book.id)"))
        assertTrue(reader.contains("ReaderProgressStoreV11.load"))
        assertTrue(reader.contains("自动进入下一章"))
        assertTrue(creation.contains("高级研究 / Reference DNA"))
        assertTrue(creation.contains("viewModel::syncConversationProposal"))
        assertTrue(creation.contains("viewModel::createCurrentFoundation"))
    }
}
