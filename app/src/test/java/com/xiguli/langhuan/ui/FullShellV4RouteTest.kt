package com.xiguli.langhuan.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullShellV4RouteTest {
    @Test
    fun rootUsesFirstClassShelfCreationReaderStoryAndEditorExperiences() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV3.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV9.kt").readText()
        val creation = File(root, "src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt").readText()
        val reader = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperience.kt").readText()
        val entryGuard = File(root, "src/main/java/com/xiguli/langhuan/ui/reader/ReaderExperienceEntryGuard.kt").readText()
        val story = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryExperience.kt").readText()
        val storyManagement = File(root, "src/main/java/com/xiguli/langhuan/ui/story/StoryManagementScreen.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/writing/ChapterEditorExperience.kt").readText()

        assertTrue(router.contains("ReaderShelfV9("))
        assertTrue(router.contains("ReaderExperienceEntryGuard("))
        assertTrue(entryGuard.contains("ReaderExperience("))
        assertTrue(entryGuard.contains("failedAttempts >= 4"))
        assertTrue(entryGuard.contains("没有可阅读章节"))
        assertTrue(router.contains("CreationChatV4("))
        assertTrue(router.contains("StoryExperience("))
        assertTrue(router.contains("ChapterEditorExperience("))
        assertTrue(router.contains("RootRouteV3.CREATION_RESEARCH"))
        assertTrue(shelf.contains("title = \"书架\""))
        assertTrue(shelf.contains("AI 新建小说"))
        assertTrue(shelf.contains("移动到其他书架"))
        assertTrue(shelf.contains("进入故事"))
        assertTrue(shelf.contains("onDeleteBook(book.id)"))

        // Reader is resume-first and supports both chapter boundaries in horizontal modes.
        assertTrue(reader.contains("ReaderProgressStoreV11.load"))
        assertTrue(reader.contains("ReaderResumeGate"))
        assertTrue(reader.contains("WindowInsets.safeDrawing"))
        assertTrue(reader.contains("leadingPageCount"))
        assertTrue(reader.contains("trailingPageCount"))
        assertTrue(reader.contains("pagerState.settledPage"))
        assertTrue(reader.contains("翻到边界自动进入上一章 / 下一章"))

        assertTrue(story.contains("故事设置"))
        assertTrue(story.contains("说什么，或做什么"))
        assertTrue(story.contains("StoryManagementExperience("))
        assertTrue(storyManagement.contains("空间 / 感知"))
        assertTrue(storyManagement.contains("NPC 记忆"))
        assertTrue(storyManagement.contains("原著入场"))
        assertTrue(storyManagement.contains("备份原稿并采用"))
        assertTrue(editor.contains("高级检查"))
        assertTrue(editor.contains("viewModel.rewriteSelection"))
        assertTrue(editor.contains("ChapterEditorPage("))
        assertTrue(creation.contains("高级研究 / Reference DNA"))
        assertTrue(creation.contains("viewModel::syncConversationProposal"))
        assertTrue(creation.contains("viewModel::createCurrentFoundation"))
    }
}
