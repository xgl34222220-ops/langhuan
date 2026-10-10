package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V92 follow-up to V91: run badge, loading tab counts, progress sort / 「已读完」, reader light
 * and backdrop settings, and the directory fast-scroll bar, all on the routed live screens.
 * Screenshots go to reader-qa/v92-* for visual review.
 */
class LiveUiPolishV92DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val night = ReaderBookUi("v92-night", "夜航记", "导入作品", "渡船停在无人的码头。", "", "", 12000, 0, 3, 3L)
    private val mountain = ReaderBookUi("v92-mountain", "山中来信", "导入作品", "一封迟到二十年的信。", "", "", 8000, 0, 3, 2L)
    private val river = ReaderBookUi("v92-river", "江上书", "导入作品", "顺流而下的一卷书。", "", "", 5000, 0, 3, 1L)

    private fun chapters(count: Int, bookId: String = night.id) = (1..count).map { n ->
        ChapterDraft("$bookId-chapter-$n", bookId, n, "第${n}章 夜航", "", emptyList(),
            content = (1..12).joinToString("\n") { "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。" })
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "mkdir -p /sdcard/Download/reader-qa"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cp ${File(dir, "$name.png").absolutePath} /sdcard/Download/reader-qa/$name.png"
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun home(state: LibraryExperienceState, runActive: Boolean = false) {
        rule.setContent {
            LanghuanStableTheme {
                LanghuanHomeV4(
                    state = state,
                    importState = LocalBookImportUiStateV1(),
                    onOpenBook = {}, onImportLocal = {}, onDeleteBook = {}, onCreate = {},
                    onOpenTavern = {}, onAiSetup = {}, onRunCenter = {}, onSkills = {},
                    runCenterActive = runActive,
                )
            }
        }
    }

    private fun menu(
        settings: ReaderSettingsV30,
        tab: ReaderMenuTabV30,
        panel: ReaderMenuPanelV30,
        chapters: List<ChapterDraft>,
    ) {
        rule.setContent {
            LanghuanStableTheme {
                ReaderMenuV30(
                    visible = true, tab = tab, panel = panel, book = night, chapters = chapters,
                    chapterIndex = 0, pageIndex = 0, pageCount = 4, settings = settings,
                    theme = readerThemeV30(settings.theme), bookmarked = false,
                    onDismiss = {}, onTab = {}, onPanel = {}, onBack = {},
                    onToggleBookmark = {}, onJumpChapter = { _, _ -> }, onPageFraction = {},
                    onEdit = {}, onWriting = {}, onStory = {},
                    bookmarkedChapters = emptySet(), bookmarkError = null,
                    legacyBookmarkedChapters = emptySet(), legacyBookmarkError = null,
                    onRestoreLegacyBookmark = {}, listening = false, onListen = {},
                    onRenameChapter = { _, _ -> }, onAppendChapter = {}, onDeleteLastChapter = {},
                    onRefreshCatalogue = {}, refreshingCatalogue = false, catalogueMessage = null,
                )
            }
        }
    }

    @Test fun moreButtonShowsARunBadgeWhileATaskIsActive() {
        home(LibraryExperienceState(stories = listOf(night, mountain), libraryLoaded = true), runActive = true)
        rule.waitUntil(10000) { rule.onAllNodesWithText("山中来信").fetchSemanticsNodes().isNotEmpty() }
        val more = rule.onNodeWithContentDescription("更多功能").fetchSemanticsNode()
        assertEquals(HOME_RUN_ACTIVE_LABEL_V92, more.config.getOrNull(SemanticsProperties.StateDescription))
        screenshot("v92-home-run-badge")
        rule.onNodeWithContentDescription("更多功能").performClick()
        rule.onNodeWithText(HOME_RUN_ACTIVE_LABEL_V92).assertIsDisplayed()
        screenshot("v92-home-run-menu")
    }

    @Test fun tabCountsStayHiddenUntilTheLibraryHasLoaded() {
        val state = mutableStateOf(LibraryExperienceState(libraryLoaded = false))
        rule.setContent {
            LanghuanStableTheme {
                LanghuanHomeV4(
                    state = state.value, importState = LocalBookImportUiStateV1(),
                    onOpenBook = {}, onImportLocal = {}, onDeleteBook = {}, onCreate = {},
                    onOpenTavern = {}, onAiSetup = {}, onRunCenter = {}, onSkills = {},
                    runCenterActive = false,
                )
            }
        }
        rule.onNodeWithText("全部").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("0").fetchSemanticsNodes().isEmpty())
        screenshot("v92-home-loading-tabs")
        rule.runOnIdle { state.value = LibraryExperienceState(stories = listOf(night, mountain), libraryLoaded = true) }
        rule.waitUntil(5000) { rule.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun finishedFilterAndProgressSortUseSavedReaderPositions() {
        val progress = rule.activity.getSharedPreferences(ShelfReadingProgressStoreV92.PREFS, Context.MODE_PRIVATE)
        val shelf = rule.activity.getSharedPreferences("qingmo_shelf_v9", Context.MODE_PRIVATE)
        val previousSort = shelf.getString("shelf_sort", null)
        val previousLayout = shelf.getString("shelf_layout_v4", null)
        fun save(id: String, index: Int, fraction: Float, finished: Boolean) {
            progress.edit()
                .putLong("updated_$id", System.currentTimeMillis())
                .putInt("total_$id", 3).putInt("index_$id", index).putInt("chapter_$id", index + 1)
                .putFloat("fraction_$id", fraction)
                .putBoolean(ShelfReadingProgressStoreV92.finishedKey(id), finished)
                .commit()
        }
        try {
            save(mountain.id, index = 2, fraction = 0.9f, finished = true)
            save(night.id, index = 1, fraction = 0.5f, finished = false)
            // V93 opens on the cover grid by default; this check reads row order, so use the list.
            shelf.edit().putString("shelf_sort", LuoShelfSortV33.PROGRESS.key)
                .putString("shelf_layout_v4", "list").commit()
            home(LibraryExperienceState(stories = listOf(night, mountain, river), libraryLoaded = true))
            rule.waitUntil(10000) { rule.onAllNodesWithText("江上书").fetchSemanticsNodes().isNotEmpty() }
            // Progress sort: finished first, then the half-read book, unread last.
            val tops = listOf("山中来信", "夜航记", "江上书").map {
                rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top
            }
            assertTrue("progress order $tops", tops[0] < tops[1] && tops[1] < tops[2])
            rule.onNodeWithText("已读 50%").assertIsDisplayed()
            screenshot("v92-home-progress-sort")
            // The finished book's row label is also 「已读完」; click the tab itself.
            rule.onNode(hasText("已读完") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithText("江上书").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("山中来信").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithText("夜航记").fetchSemanticsNodes().isEmpty())
            screenshot("v92-home-finished")
        } finally {
            progress.edit().also { editor ->
                progress.all.keys.filter { key -> listOf(night, mountain, river).any { key.endsWith("_${it.id}") } }
                    .forEach(editor::remove)
            }.commit()
            shelf.edit().apply {
                if (previousSort == null) remove("shelf_sort") else putString("shelf_sort", previousSort)
                if (previousLayout == null) remove("shelf_layout_v4") else putString("shelf_layout_v4", previousLayout)
            }.commit()
        }
    }

    @Test fun themePanelOffersBrightnessWarmLightAndBackdrops() {
        val prefs = rule.activity.getSharedPreferences("v92-reader-light", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = ReaderSettingsV30(prefs)
        menu(settings, ReaderMenuTabV30.DETAILS, ReaderMenuPanelV30.THEME, chapters(3))
        rule.onNodeWithText(READER_LIGHT_SECTION_LABEL_V92).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("开启暖光").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0.4f, settings.warmth, 0.001f) }
        rule.onNodeWithText("跟随系统亮度").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(settings.brightness > 0f) }
        rule.onNodeWithContentDescription("背景：宣纸").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(ReaderBackdropV92.XUAN.key, settings.backdrop) }
        screenshot("v92-reader-light-panel")
        settings.save()
        val reloaded = ReaderSettingsV30(prefs)
        assertEquals(ReaderBackdropV92.XUAN.key, reloaded.backdrop)
        assertEquals(0.4f, reloaded.warmth, 0.001f)
        rule.activity.deleteSharedPreferences("v92-reader-light")
    }

    @Test fun readerPageShowsWarmLightOverTheTexturedPaper() {
        val prefs = rule.activity.getSharedPreferences("v92-reader-page", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = ReaderSettingsV30(prefs).apply {
            turnMode = ReaderTurnModeV30.NONE
            clickAnimation = false
            warmth = 0.6f
            backdrop = ReaderBackdropV92.XUAN.key
        }
        val list = chapters(3)
        try {
            rule.setContent {
                PaperReaderThemeV44 {
                    ReaderSessionV30(night, list, list.first().id, settings, false, true, {}, {}, {}, {}, {})
                }
            }
            rule.waitUntil(10000) {
                rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                    it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("第") == true
                }
            }
            screenshot("v92-reader-warm-xuan")
        } finally {
            rule.activity.deleteSharedPreferences("v92-reader-page")
            for (name in listOf("reader_progress_v1", "reader_progress_v2")) {
                val p = rule.activity.getSharedPreferences(name, 0)
                p.edit().also { e -> p.all.keys.filter { it.endsWith("_${night.id}") }.forEach(e::remove) }.commit()
            }
        }
    }

    @Test fun longDirectoryHasAFastScrollBarWithAChapterHint() {
        val prefs = rule.activity.getSharedPreferences("v92-reader-toc", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = ReaderSettingsV30(prefs)
        menu(settings, ReaderMenuTabV30.DIRECTORY, ReaderMenuPanelV30.MAIN, chapters(300))
        rule.onNodeWithText("第1章 夜航").assertIsDisplayed()
        val bar = rule.onNodeWithContentDescription(READER_TOC_FAST_SCROLL_LABEL_V92)
        bar.assertExists()
        bar.performTouchInput { down(topCenter); moveTo(center) }
        rule.waitForIdle()
        screenshot("v92-reader-toc-fast-scroll")
        bar.performTouchInput { moveTo(bottomCenter); up() }
        rule.waitUntil(5000) { rule.onAllNodesWithText("第300章 夜航").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("第300章 夜航").assertIsDisplayed()
        rule.activity.deleteSharedPreferences("v92-reader-toc")
    }
}
