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
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V27 window-session and navigation checks, migrated in V92 from the unrouted legacy screens
 * (HeroReaderPageV13 / ShelfQingmoFunctionalV9) to what LanghuanRootV4 actually shows: the V30
 * reader session ([ReaderSessionV30]) and the live shelf ([LanghuanHomeV4]). Screenshot names
 * are kept so earlier evidence stays comparable.
 */
class ReaderSessionV27Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val book = ReaderBookUi("reader-qa", "夜航记", "悬疑", "渡船停在无人的码头。", "", "", 12000, 100000, 1, 1L)
    private val chapters = (1..3).map { n ->
        ChapterDraft("chapter-$n", book.id, n, "第${n}章 夜航", "", emptyList(),
            content = (1..30).joinToString("\n") { "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。" })
    }

    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        android.os.SystemClock.sleep(500) // Wait for SurfaceFlinger to present the settled frame.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // AGP uninstalls the target after tests; preserve images outside app storage.
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "mkdir -p /sdcard/Download/reader-qa"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${File(dir, "$name.png").absolutePath} /sdcard/Download/reader-qa/$name.png"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun barsVisible(): Boolean = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
        ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true

    private fun readerLaidOut(): Boolean = rule.onAllNodesWithContentDescription("阅读正文")
        .fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("第") == true }

    @Test fun chapterChangesKeepImmersionAndToolsStayAligned() {
        val selected = mutableIntStateOf(0)
        val reading = mutableStateOf(true)
        lateinit var settings: ReaderSettingsV30
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            val prefs = rule.activity.getSharedPreferences("reader_qingmo_v9", 0)
            prefs.edit().clear()
                .putBoolean("immersive", true).putFloat("font", 20f).putFloat("line", 1.65f)
                .putFloat("paragraph", 8f).putFloat("sidePadding", 22f).putString("fontKey", "sans")
                .putString("pageMode", "none").putBoolean("clickAnimation", false).commit()
            settings = ReaderSettingsV30(prefs)
        }
        rule.setContent {
            PaperReaderThemeV44 {
                if (reading.value) {
                    ReaderWindowSessionV27(true)
                    key(selected.intValue) {
                        val chapter = chapters[selected.intValue]
                        ReaderSessionV30(book, chapters, chapter.id, settings, false, true,
                            {}, { reading.value = false }, {}, {}, {})
                    }
                } else Text("书库")
            }
        }
        rule.waitUntil(10000) { !barsVisible() }
        rule.waitUntil(10000) { readerLaidOut() }
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
        rule.waitUntil(10000) { readerLaidOut() }
        rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
        rule.waitUntil(10000) {
            rule.onAllNodesWithContentDescription("阅读菜单：目录").fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("02-reader-tools")
        val centers = listOf("详情", "目录", "更多").map { label ->
            rule.onAllNodesWithContentDescription("阅读菜单：$label", useUnmergedTree = true)
                .fetchSemanticsNodes().minBy { node -> node.boundsInRoot.width }.boundsInRoot.center.x
        }
        val gaps = centers.zipWithNext { a, b -> b - a }
        assertTrue("Menu tabs do not have equal spacing: $centers", gaps.max() - gaps.min() < 2f)
        rule.onNodeWithContentDescription("阅读菜单：目录").performClick()
        rule.onNodeWithText("正在阅读").assertIsDisplayed()
        screenshot("03-directory")
        rule.onNodeWithContentDescription("阅读菜单：详情").performClick()
        rule.onNodeWithText("当前阅读").assertExists()
        screenshot("08-reader-tools-settled")
        rule.onNodeWithContentDescription("返回书架").performClick()
        rule.waitUntil(10000) { barsVisible() }
        rule.onNodeWithText("书库").assertIsDisplayed()
    }

    @Test fun shelfAndProfileHaveWorkingNavigation() {
        var opened = ""
        val other = book.copy(id = "other", title = "山中来信", genre = "导入作品")
        rule.setContent {
            LanghuanStableTheme {
                LanghuanHomeV4(
                    state = LibraryExperienceState(stories = listOf(book, other), libraryLoaded = true),
                    importState = LocalBookImportUiStateV1(),
                    onOpenBook = { opened += "open:$it," }, onImportLocal = {}, onDeleteBook = {},
                    onCreate = {}, onOpenTavern = {}, onAiSetup = { opened += "ai," },
                    onRunCenter = {}, onSkills = {}, onOnline = { opened += "online," },
                    runCenterActive = false,
                )
            }
        }
        rule.onNodeWithText("书架").assertIsDisplayed()
        rule.onNodeWithText("山中来信").assertIsDisplayed()
        screenshot("04-home")
        // The 在写 tab keeps only the user's own works; the imported novel leaves the list.
        rule.onNodeWithText("在写").performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText("山中来信").fetchSemanticsNodes().isEmpty() }
        // The book can show both in the continue-reading card and its shelf row.
        rule.onAllNodesWithText("夜航记").onFirst().assertIsDisplayed()
        screenshot("05-library")
        rule.onNodeWithText("全部").performClick()
        rule.onNodeWithText("山中来信").performClick()
        rule.runOnIdle { assertTrue(opened, opened.contains("open:other,")) }
        rule.onNodeWithContentDescription("在线书城").performClick()
        rule.runOnIdle { assertTrue(opened, opened.contains("online,")) }
        rule.onNodeWithContentDescription("更多功能").performClick()
        rule.onNodeWithText("AI 与模型").assertIsDisplayed()
        screenshot("07-settings")
        rule.onNodeWithText("AI 与模型").performClick()
        rule.runOnIdle { assertTrue(opened, opened.contains("ai,")) }
    }
}
