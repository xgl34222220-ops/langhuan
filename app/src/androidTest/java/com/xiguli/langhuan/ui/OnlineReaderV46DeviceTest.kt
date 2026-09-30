package com.xiguli.langhuan.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test

/** Opens the real reader on an uncached online book and turns across a chapter boundary. */
class OnlineReaderV46DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun realReaderLoadsOnlyCurrentChapterThenReadsCachedTextWithoutSource() = runBlocking {
        val base = InstrumentationRegistry.getArguments().getString("sourceFixtureBase")
        assumeNotNull(base)
        val context = rule.activity.applicationContext
        val previous = BookSourceStoreV36.load(context)
        val source = BookSourceV36("reader-on-demand-v46", "受控阅读站", base!!, contentText = ".content@html")
        BookSourceStoreV36.save(context, previous.filterNot { it.id == source.id } + source)
        context.getSharedPreferences("reader_qingmo_v9", 0).edit().putString("pageMode", "none").putBoolean("clickAnimation", false).commit()
        val projects = StoryProjectManager(context)
        val title = "按需加载阅读回归V46"
        val id = projects.createImportedStory(ImportedManuscript(title, listOf(
            ImportedChapter("第一章 灯火", "", base + "chapter.html"),
            ImportedChapter("第二章 归港", "", base + "chapter-second.html"),
        ), source.id, base + "on-demand-book")).snapshot.novel.id
        lateinit var vm: LibraryExperienceViewModel
        try {
            rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[LibraryExperienceViewModel::class.java] }
            rule.waitUntil(20000) { vm.state.value.stories.any { it.id == id } }
            rule.onNodeWithText(title).performClick()
            rule.waitUntil(30000) { vm.state.value.readingChapter?.content?.contains("远处亮起一盏灯") == true }
            assertTrue("The next chapter must stay uncached until it is read", projects.chapterDraft(id, 2)!!.content.isBlank())
            rule.waitUntil(20000) {
                rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription].startsWith("第")
                }
            }
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            rule.waitUntil(30000) { vm.state.value.readingChapter?.chapterNumber == 2 && vm.state.value.readingChapter?.content?.contains("归航的船停在港口") == true }
            assertTrue(projects.chapterDraft(id, 1)!!.content.isNotBlank())
            assertTrue(projects.chapterDraft(id, 2)!!.content.isNotBlank())
            BookSourceStoreV36.save(context, previous.filterNot { it.id == source.id })
            rule.runOnUiThread { vm.openReader(1) }
            rule.waitUntil(10000) { vm.state.value.readingChapter?.content?.contains("远处亮起一盏灯") == true }
            assertNull(vm.state.value.readerLoadError)
            rule.waitForIdle()
            deviceWindowEvidenceV46("v46-reader-before-back")
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            try {
                rule.waitUntil(20000) { vm.state.value.openedBook == null }
            } catch (failure: Throwable) {
                deviceWindowEvidenceV46("v46-reader-after-back-failure")
                throw failure
            }
        } finally {
            BookSourceStoreV36.save(context, previous)
            val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
            listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) }
        }
    }
}
