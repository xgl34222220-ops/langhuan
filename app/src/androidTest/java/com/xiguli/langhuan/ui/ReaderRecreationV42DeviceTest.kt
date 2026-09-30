package com.xiguli.langhuan.ui

import android.app.UiAutomation
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Same Activity reconstruction used for configuration changes, with a real persisted book. */
class ReaderRecreationV42DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun longChapterAndChangedFontStayInReaderAfterActivityRecreation() = runBlocking {
        val context = rule.activity.applicationContext
        val title = "阅读位置回归V42"
        context.getSharedPreferences("reader_qingmo_v9", 0).edit()
            .putBoolean("reader_comfort_v26", true).putBoolean("clickAnimation", false)
            .putString("pageMode", "none").putFloat("font", 20f).commit()
        val manager = StoryProjectManager(context)
        val story = manager.createImportedStory(ImportedManuscript(title = title, chapters = listOf(
            ImportedChapter("第一章 夜航", (1..600).joinToString("\n") {
                "第${it}段，夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。"
            }),
        )))
        val id = story.snapshot.novel.id
        lateinit var vm: LibraryExperienceViewModel
        try {
            rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[LibraryExperienceViewModel::class.java] }
            rule.waitUntil(20000) { vm.state.value.stories.any { it.id == id } }
            rule.onNodeWithText(title).performClick()
            rule.waitUntil(20000) { vm.state.value.readingChapter != null }
            rule.waitUntil(20000) {
                rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription].startsWith("第")
                }
            }
            repeat(8) {
                rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
                rule.mainClock.advanceTimeBy(400)
            }
            val beforeFont = ReaderProgressStoreV11.load(context, id, 1)
            assertTrue(beforeFont.textOffset > 1000)
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.onNodeWithText("字号").performClick()
            rule.onNodeWithText("A+").performClick()
            rule.mainClock.advanceTimeBy(1000)
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.mainClock.advanceTimeBy(1000)
            rule.waitForIdle()
            val beforeRecreate = ReaderProgressStoreV11.load(context, id, 1)
            assertTrue("Font reflow lost the chapter anchor", beforeRecreate.textOffset > 0)
            assertTrue("Font reflow jumped forwards", beforeRecreate.textOffset <= beforeFont.textOffset)
            assertTrue("Font reflow jumped back more than a full page", beforeFont.textOffset - beforeRecreate.textOffset < 1200)
            rule.activityRule.scenario.recreate()
            rule.waitUntil(20000) { rule.onAllNodesWithText("正在检查琅嬛数据…").fetchSemanticsNodes().isEmpty() }
            rule.waitUntil(20000) {
                rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription].startsWith("第")
                }
            }
            // The shelf has a semantic book title, while the reader title is drawn in Canvas.
            rule.onAllNodesWithText("书城").assertCountEquals(0)
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(300)
            // The reader opens its “更多” tab; the shelf button belongs to “目录”/“详情”.
            rule.onNodeWithText("字号").assertIsDisplayed()
            rule.onNodeWithText("目录").performClick()
            rule.onNodeWithContentDescription("返回书架").assertIsDisplayed()
            rule.mainClock.advanceTimeBy(1000)
            val afterRecreate = ReaderProgressStoreV11.load(context, id, 1)
            assertEquals(beforeRecreate.chapterNumber, afterRecreate.chapterNumber)
            assertEquals(beforeRecreate.textOffset, afterRecreate.textOffset)
            assertEquals(21f, context.getSharedPreferences("reader_qingmo_v9", 0).getFloat("font", 0f), 0f)
            rule.runOnUiThread {
                rule.activity.onBackPressedDispatcher.onBackPressed()
                context.getSharedPreferences("reader_qingmo_v9", 0).edit().putBoolean("lockPortrait", false).commit()
            }
            rule.waitForIdle()
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            rule.waitUntil(20000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            rule.mainClock.advanceTimeBy(1000)
            rule.waitForIdle()
            rule.onAllNodesWithText("书城").assertCountEquals(0)
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(300)
            // The reader opens its “更多” tab; the shelf button belongs to “目录”/“详情”.
            rule.onNodeWithText("字号").assertIsDisplayed()
            rule.onNodeWithText("目录").performClick()
            rule.onNodeWithContentDescription("返回书架").assertIsDisplayed()
            val rotated = ReaderProgressStoreV11.load(context, id, 1)
            assertTrue("Rotation lost the sentence anchor", rotated.textOffset > 0)
            assertTrue("Rotation jumped forwards", rotated.textOffset <= afterRecreate.textOffset)
            assertTrue("Rotation jumped backwards more than a full page", afterRecreate.textOffset - rotated.textOffset < 1600)
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            rule.runOnUiThread { vm.closeBook() }
            val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
            listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
            }
        }
    }
}
