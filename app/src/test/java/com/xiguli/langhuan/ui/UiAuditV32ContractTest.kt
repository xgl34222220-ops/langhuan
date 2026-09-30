package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the layout-overlap and navigation fixes from the UI audit. */
class UiAuditV32ContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")
    private fun source(path: String): String = File(root, "src/main/java/com/xiguli/langhuan/ui/$path").readText()

    @Test
    fun systemBackNeverFallsThroughAndClosesTheApp() {
        val router = source("LanghuanRootV4.kt")
        assertTrue(router.contains("BackHandler(enabled = route != RootRouteV4.SHELF)"))
        val shelf = source("shell/ShelfLuoShuFunctionalV1.kt")
        assertTrue(shelf.contains("BackHandler(enabled = searchOpen && screen == LuoShelfScreenV1.SHELF)"))
    }

    @Test
    fun storyAreaHasOneBackPathAndNoFloatingButtons() {
        val router = source("LanghuanRootV4.kt")
        val tavern = source("TavernNovelCharacterExperienceV3.kt")
        assertFalse(router.contains("Icon(Icons.Rounded.ArrowBack, \"返回书架\")"))
        assertFalse(tavern.contains("padding(top = 58.dp)"))
        assertFalse(tavern.contains("Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 10.dp)"))
        assertTrue(tavern.contains("consumeWindowInsets(WindowInsets.statusBars)"))
    }

    @Test
    fun workspaceHealthPillIsLaidOutNotFloated() {
        val entry = source("WritingWorkspaceV10.kt")
        assertFalse(entry.contains("padding(top = 104.dp"))
        assertTrue(entry.contains("statusAccessory = {"))
    }

    @Test
    fun inputScreensMoveAboveTheKeyboard() {
        assertTrue(source("CreationChatV4.kt").contains("Column(Modifier.fillMaxSize().imePadding())"))
        listOf("ResearchNewBookConversationPage.kt", "AiProviderSetupPage.kt", "writing/ChapterEditorExperience.kt", "story/StoryCoreExperience.kt")
            .forEach { assertTrue(it, source(it).contains("modifier = Modifier.imePadding(),")) }
    }

    @Test
    fun destructiveActionsAskFirst() {
        assertTrue(source("AiProviderSetupPage.kt").contains("onClick = { pendingDeleteId = provider.id }"))
        assertTrue(source("CreationChatV4.kt").contains("重新开始创作？"))
    }
}
