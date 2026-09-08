package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderSessionV27Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val book = ReaderBookUi("reader-qa", "夜航记", "悬疑", "渡船停在无人的码头。", "", "", 12000, 100000, 1, 1L)
    private val chapters = (1..3).map { n ->
        ChapterDraft("chapter-$n", book.id, n, "第${n}章 夜航", "", emptyList(),
            content = (1..30).joinToString("\n") { "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。" })
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun barsVisible(): Boolean = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true

    @Test fun chapterChangesKeepImmersionAndToolsStayAligned() {
        val selected = mutableIntStateOf(0)
        val reading = mutableStateOf(true)
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            rule.activity.getSharedPreferences("reader_qingmo_v9", 0).edit().clear()
                .putBoolean("immersive", true).putFloat("font", 20f).putFloat("line", 1.65f)
                .putFloat("paragraph", 8f).putFloat("sidePadding", 22f).putString("fontKey", "sans").commit()
        }
        rule.setContent {
            MaterialTheme {
                if (reading.value) {
                    ReaderWindowSessionV27(true)
                    key(selected.intValue) {
                        val chapter = chapters[selected.intValue]
                        HeroReaderPageV13(book, LibraryExperienceState(stories = listOf(book), chapters = chapters, readingChapter = chapter), chapter,
                            startPanel = false, interactionEnabled = true,
                            onBack = { reading.value = false }, onOpenChapter = { selected.intValue = it - 1 },
                            onEdit = {}, onWriting = {}, onStory = {})
                    }
                } else Text("书库")
            }
        }
        rule.waitUntil(10000) { !barsVisible() }
        rule.waitForIdle()
        screenshot("01-reader")
        val flash = AtomicBoolean(false)
        val view = rule.activity.window.decorView
        val observer = android.view.ViewTreeObserver.OnPreDrawListener {
            if (barsVisible()) flash.set(true)
            true
        }
        rule.runOnUiThread { view.viewTreeObserver.addOnPreDrawListener(observer) }
        repeat(6) { index ->
            rule.runOnIdle { selected.intValue = (index + 1) % 3 }
            rule.waitForIdle()
            assertFalse("Status bar appeared after changing chapters", barsVisible())
        }
        rule.runOnUiThread { view.viewTreeObserver.removeOnPreDrawListener(observer) }
        assertFalse("A frame exposed the status bar during chapter change", flash.get())
        rule.onRoot().performTouchInput { click(center) }
        rule.onNodeWithText("设置").assertIsDisplayed()
        screenshot("02-reader-tools")
        val centers = listOf("排版预设", "主题", "字体", "字号").map {
            rule.onAllNodesWithContentDescription(it, useUnmergedTree = true).fetchSemanticsNodes().minBy { node -> node.boundsInRoot.width }.boundsInRoot.center.x
        }
        val gaps = centers.zipWithNext { a, b -> b - a }
        assertTrue("Tool icon columns do not have equal spacing", gaps.max() - gaps.min() < 2f)
        rule.onNodeWithText("目录").performClick()
        rule.onNodeWithText("正在读").assertIsDisplayed()
        screenshot("03-directory")
        rule.onNodeWithContentDescription("返回书架").performClick()
        rule.waitUntil(10000) { barsVisible() }
    }

    @Test fun shelfAndProfileHaveWorkingNavigation() {
        rule.setContent {
            LanghuanStableTheme {
                ShelfQingmoFunctionalV9(LibraryExperienceState(stories = listOf(book, book.copy(id = "other", title = "山中来信")), libraryLoaded = true),
                    LocalBookImportUiStateV1(), null, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onNodeWithText("首页").assertIsDisplayed()
        screenshot("04-home")
        rule.onNodeWithText("书库").performClick()
        rule.onNodeWithText("我的书库").assertIsDisplayed()
        screenshot("05-library")
        rule.onNodeWithText("我的").performClick()
        rule.onNodeWithText("阅读记录").assertIsDisplayed()
        screenshot("06-profile")
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("AI 与模型").assertIsDisplayed()
        screenshot("07-settings")
    }
}
