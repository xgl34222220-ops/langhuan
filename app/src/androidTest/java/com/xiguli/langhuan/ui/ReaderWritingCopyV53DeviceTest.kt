package com.xiguli.langhuan.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.data.*
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class ReaderWritingCopyV53DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun remove(context: android.content.Context, id: String) {
        val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
        listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) }
    }
    @Test fun readerAiActionCreatesIndependentDraftAndCancelLeavesNoProject() = runBlocking {
        val appContext = rule.activity.applicationContext
        val projects=StoryProjectManager(appContext)
        val original=projects.createImportedStory(ImportedManuscript("阅读转创作测试", listOf(ImportedChapter("第一章 原创", "测试正文，海风吹过码头。".repeat(80), "https://copy.example/chapter/1")), "fixture", "https://copy.example/book/${UUID.randomUUID()}"))
        val id=original.snapshot.novel.id
        val opened=AtomicReference<String?>(null)
        lateinit var vm: LibraryExperienceViewModel
        try {
            rule.runOnUiThread { vm=ViewModelProvider(rule.activity)[LibraryExperienceViewModel::class.java] }
            rule.waitUntil(15000) { vm.state.value.stories.any { it.id==id } }
            rule.runOnUiThread { vm.openBook(id) }
            rule.waitUntil(15000) { vm.state.value.openedBook?.id==id }
            rule.runOnUiThread { vm.openReader(1) }
            rule.waitUntil(15000) { vm.state.value.readingChapter!=null }
            rule.setContent { ReaderEngineV30(vm,StudioUiState(original.snapshot,original.draft),{}, { opened.set(it) },{_,_->},{},startOnInfo=true) }
            rule.onNodeWithText("AI 创作").performClick()
            rule.onNodeWithText("从本章开始 AI 创作").assertIsDisplayed()
            rule.onNodeWithText("取消").performClick()
            assertNull(opened.get())
            assertEquals(original,projects.loadStory(id))
            rule.onNodeWithText("AI 创作").performClick()
            rule.onNodeWithText("创建副本并进入").performClick()
            rule.waitUntil(15000) { opened.get()!=null }
            rule.onNodeWithText("从本章开始 AI 创作").assertDoesNotExist()
            val copied=projects.loadStory(opened.get()!!)!!
            assertNotEquals(id,copied.snapshot.novel.id)
            assertEquals("创作副本",copied.snapshot.novel.genre)
            assertEquals("",copied.snapshot.novel.sourceId)
            assertEquals("",copied.draft.sourceUrl)
            assertEquals(original.draft.content,copied.draft.content)
            assertEquals(original,projects.loadStory(id))
        } finally {
            // Exercise a real Activity/Composition exit before deleting rows it observes.
            // The Activity owns its ViewModel lifecycle; clearing it underneath a live tree
            // and deleting the rows before rule teardown was not the app's navigation flow.
            try { rule.activityRule.scenario.close() }
            finally { remove(appContext, id); opened.get()?.let { remove(appContext, it) } }
        }
    }
    @Test fun uncachedOrChangedChapterCannotCreateEmptyOrWrongWritingCopy() = runBlocking {
        val appContext = rule.activity.applicationContext
        val projects=StoryProjectManager(appContext)
        val original=projects.createImportedStory(ImportedManuscript("未缓存",listOf(ImportedChapter("第一章","","https://copy.example/chapter/1")),"fixture","https://copy.example/book/${UUID.randomUUID()}"))
        val id=original.snapshot.novel.id
        try {
            assertTrue(runCatching { projects.createWritingCopy(id,1,original.draft.id) }.isFailure)
            assertTrue(runCatching { projects.createWritingCopy(id,1,"wrong-chapter") }.isFailure)
            assertEquals(original,projects.loadStory(id))
        } finally { remove(appContext, id) }
    }
}
