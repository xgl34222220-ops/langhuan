package com.xiguli.langhuan.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderEditionV41Test {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val body = (1..160).joinToString("\n") { i ->
        if (i % 4 == 0) "他抬头，看见远方的一点灯火。"
        else "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。中文与 hello world 相遇，故事仍在继续。"
    }

    private fun saveFrame(name: String) {
        // Wait for lazy content effects to start, then advance past their staggered delays.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        android.os.SystemClock.sleep(400)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Pixel Launcher's occasional emulator-only ANR must not obscure the app evidence.
        // Never dismiss this app's dialogs or affect a physical device's launcher.
        if (android.os.Build.HARDWARE in setOf("ranchu", "goldfish")) {
            automation.executeShellCommand("am force-stop com.google.android.apps.nexuslauncher").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            android.os.SystemClock.sleep(300)
        }
        val bitmap = automation.takeScreenshot()
        var shelfInk: Int? = null
        if (name == "v41-shelf") {
            var ink = 0
            for (y in bitmap.height / 6 until bitmap.height / 2 step 3) {
                for (x in bitmap.width / 12 until bitmap.width * 11 / 12 step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) < 190 && android.graphics.Color.green(pixel) < 190 && android.graphics.Color.blue(pixel) < 190) ink++
                }
            }
            shelfInk = ink
        }
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        val target = File(dir, "$name.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        automation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        automation.executeShellCommand("cp ${target.absolutePath} /sdcard/Download/reader-qa/$name.png").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
        shelfInk?.let { assertTrue("Shelf fixture exists semantically but covers were not painted", it > 150) }
    }

    @Test fun measuredFullPagesAlignAcrossWidthsAndFontScales() {
        for (width in listOf(320, 360, 411)) for (scale in listOf(1f, 1.3f, 2f)) for (font in listOf(16f, 20f, 28f)) {
            val fontPx = font * 2f * scale
            val spec = ReaderTypeSpecV30((width - 40) * 2, 1207, fontPx, fontPx * 1.65f,
                17f, fontPx * 1.36f, fontPx * 1.8f, 24f, true, "sans", 400, 0f)
            val chapter = readerPaginateChapterV30(0, "fixture", "夜航记", body, spec)
            assertTrue(chapter.pages.size > 2)
            val full = chapter.pages.dropLast(1).filter { it.lines.size > 1 && it.lines.none { line -> line.title } }
            assertTrue(full.isNotEmpty())
            val firstBaseline = full.first().lines.first().baseline
            val lastBaseline = full.first().lines.last().baseline
            full.forEach { page ->
                assertEquals(firstBaseline, page.lines.first().baseline, 0.6f)
                assertEquals(lastBaseline, page.lines.last().baseline, 0.6f)
                assertEquals(spec.bodyHeightPx.toFloat(), page.usedHeight, 0.6f)
                assertTrue(page.lines.zipWithNext().all { (a, b) -> b.baseline > a.baseline })
            }
            chapter.pages.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
            assertEquals(body.length, chapter.pages.last().endOffset)
        }
    }

    @Test fun continuousFullPageScreenshotsUseTheProductionRenderer() {
        val selected = mutableIntStateOf(1)
        rule.setContent {
            ReaderWindowSessionV27(true)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val geometry = remember(constraints, density.density) {
                    ReaderGeometryV30.of(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), density.density,
                        0f, 0f, 22f)
                }
                val spec = remember(geometry) {
                    val font = 20f * density.density
                    ReaderTypeSpecV30(geometry.bodyWidth.toInt(), geometry.bodyHeight.toInt(), font, font * 1.65f,
                        8f * density.density, font * 1.36f, font * 1.8f, 22f, true, "sans", 400, 0f)
                }
                val chapter = remember(spec) { readerPaginateChapterV30(0, "visual", "夜航记", body, spec) }
                val theme = readerThemeV30("paper")
                val paints = remember(spec) { readerPaintsV30(spec, theme, 11f * density.density) }
                Canvas(Modifier.fillMaxSize()) {
                    drawReaderPageV30(chapter.pages[selected.intValue], geometry, theme, paints,
                        ReaderChromeInfoV30("夜航记", "${selected.intValue + 1}/${chapter.pages.size}", "", "21:30", 80, true, false))
                }
            }
        }
        repeat(10) { index ->
            rule.runOnIdle { selected.intValue = index + 1 }
            saveFrame("v41-page-${index + 1}")
        }
    }

    @Test fun readingFirstNavigationAndSourceEmptyStates() {
        val vm = OnlineBooksViewModelV36(rule.activity.application as Application)
        val books = listOf(
            ReaderBookUi("fixture1", "夜航记", "悬疑", "一封迟来的信", "", "", 12000, 100000, 1, 1L),
            ReaderBookUi("fixture2", "山中来信", "文学", "", "", "", 5000, 60000, 1, 2L),
        )
        rule.setContent {
            LanghuanStableTheme {
                ShelfLuoShuFunctionalV1(
                    state = LibraryExperienceState(stories = books, libraryLoaded = true),
                    importState = LocalBookImportUiStateV1(), openingBookId = null,
                    onOpenBook = {}, onOpenTavern = {}, onImportLocal = {}, onDeleteBook = {},
                    onCreate = {}, onAiSetup = {}, onRunCenter = {}, onSkills = {},
                    onlineContent = { sources -> OnlineBooksPageV36(vm, {}, {}, embedded = true, startWithSources = sources) },
                )
            }
        }
        rule.onNodeWithText("夜航记").assertIsDisplayed()
        saveFrame("v41-shelf")
        rule.onAllNodesWithText("书城").onLast().performClick()
        rule.onNodeWithText("发现下一本好书").assertIsDisplayed()
        saveFrame("v41-bookstore")
        rule.onNodeWithText("管理书源").performClick()
        rule.onNodeWithText("AI 生成书源").assertIsDisplayed()
        saveFrame("v41-sources")
        rule.onNodeWithText("AI 生成书源").performClick()
        rule.onNodeWithText("网站链接").assertIsDisplayed()
        saveFrame("v41-ai-source")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onAllNodesWithText("我的").onLast().performClick()
        rule.onNodeWithText("书源与 AI").assertIsDisplayed()
        saveFrame("v41-profile")
        repeat(3) {
            rule.onAllNodesWithText("书架").onLast().performClick()
            rule.onNodeWithText("夜航记").assertIsDisplayed()
            rule.onAllNodesWithText("书城").onLast().performClick()
            rule.onNodeWithText("发现下一本好书").assertIsDisplayed()
        }
    }
}
