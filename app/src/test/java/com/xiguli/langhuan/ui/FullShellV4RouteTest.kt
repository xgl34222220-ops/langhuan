package com.xiguli.langhuan.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullShellV4RouteTest {
    @Test
    fun rootUsesFirstClassShelfCreationAndTavernExperiences() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV3.kt").readText()
        val home = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanHomeV4.kt").readText()
        val creation = File(root, "src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt").readText()

        assertTrue(router.contains("LanghuanHomeV4("))
        assertTrue(router.contains("CreationChatV4("))
        assertTrue(router.contains("StoryPlayPanelV17("))
        assertTrue(router.contains("RootRouteV3.CREATION_RESEARCH"))
        assertTrue(home.contains("\"书架\""))
        assertTrue(home.contains("\"创作\""))
        assertTrue(home.contains("\"酒馆\""))
        assertTrue(home.contains("onDeleteBook(book.id)"))
        assertTrue(creation.contains("高级研究 / Reference DNA"))
        assertTrue(creation.contains("viewModel::syncConversationProposal"))
        assertTrue(creation.contains("viewModel::createCurrentFoundation"))
    }
}
