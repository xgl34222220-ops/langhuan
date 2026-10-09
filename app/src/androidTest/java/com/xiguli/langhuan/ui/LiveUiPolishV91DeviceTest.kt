package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V91 UI polish: renders the screens that are actually routed by LanghuanRootV4 (the live
 * shelf and the V30 reader settings sheet), checks the restored / added affordances and keeps
 * screenshots under reader-qa for visual review.
 */
class LiveUiPolishV91DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val book = ReaderBookUi("v91-night", "夜航记", "悬疑", "渡船停在无人的码头。", "", "", 12000, 100000, 3, 1L)
    private val other = ReaderBookUi("v91-mountain", "山中来信", "导入作品", "一封迟到二十年的信。", "", "", 8000, 0, 12, 2L)
    private val chapters = (1..3).map { n ->
        ChapterDraft("v91-chapter-$n", book.id, n, "第${n}章 夜航", "", emptyList(),
            content = (1..20).joinToString("\n") { "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。" })
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

    private fun home(
        state: LibraryExperienceState,
        aiSetup: () -> Unit = {},
        runCenter: () -> Unit = {},
        skills: () -> Unit = {},
    ) {
        rule.setContent {
            LanghuanStableTheme {
                LanghuanHomeV4(
                    state = state,
                    importState = LocalBookImportUiStateV1(),
                    onOpenBook = {}, onImportLocal = {}, onDeleteBook = {}, onCreate = {},
                    onOpenTavern = {}, onAiSetup = aiSetup, onRunCenter = runCenter, onSkills = skills,
                )
            }
        }
    }

    @Test fun shelfMoreMenuRestoresAiRunCenterAndSkills() {
        var opened = ""
        home(
            LibraryExperienceState(stories = listOf(book, other), libraryLoaded = true),
            aiSetup = { opened += "ai," }, runCenter = { opened += "run," }, skills = { opened += "skills," },
        )
        rule.waitUntil(10000) { rule.onAllNodesWithText("山中来信").fetchSemanticsNodes().isNotEmpty() }
        screenshot("v91-home-shelf")
        rule.onNodeWithContentDescription("整理书架").performClick()
        rule.onNodeWithText("批量整理").assertIsDisplayed()
        screenshot("v91-home-organizer")
        rule.onNodeWithContentDescription("整理书架").performClick()
        rule.onNodeWithContentDescription("更多功能").performClick()
        rule.onNodeWithText("运行中心").assertIsDisplayed()
        rule.onNodeWithText("AI 与模型").assertIsDisplayed()
        rule.onNodeWithText("写作技能").assertIsDisplayed()
        screenshot("v91-home-more")
        rule.onNodeWithText("运行中心").performClick()
        rule.waitForIdle()
        assertTrue(opened, opened.contains("run,"))
    }

    @Test fun shelfShowsPlaceholdersBeforeTheFirstLoadAndOneImportActionWhenEmpty() {
        val state = mutableStateOf(LibraryExperienceState(libraryLoaded = false))
        rule.setContent {
            LanghuanStableTheme {
                LanghuanHomeV4(
                    state = state.value,
                    importState = LocalBookImportUiStateV1(),
                    onOpenBook = {}, onImportLocal = {}, onDeleteBook = {}, onCreate = {},
                    onOpenTavern = {}, onAiSetup = {}, onRunCenter = {}, onSkills = {},
                )
            }
        }
        assertTrue(rule.onAllNodesWithContentDescription("正在载入书架").fetchSemanticsNodes().isNotEmpty())
        screenshot("v91-home-loading")
        rule.runOnIdle { state.value = LibraryExperienceState(libraryLoaded = true) }
        rule.onNodeWithText("导入本地书籍").assertIsDisplayed()
        rule.onNodeWithText("书架还是空的").assertIsDisplayed()
        screenshot("v91-home-empty")
    }

    @Test fun readerSettingsPanelsShowALiveTypesettingPreview() {
        val prefs = rule.activity.getSharedPreferences("v91-reader-settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = ReaderSettingsV30(prefs)
        val panel = mutableStateOf(ReaderMenuPanelV30.FONT)
        rule.setContent {
            LanghuanStableTheme {
                ReaderMenuV30(
                    visible = true,
                    tab = ReaderMenuTabV30.MORE,
                    panel = panel.value,
                    book = book,
                    chapters = chapters,
                    chapterIndex = 0,
                    pageIndex = 0,
                    pageCount = 4,
                    settings = settings,
                    theme = readerThemeV30(settings.theme),
                    bookmarked = false,
                    onDismiss = {}, onTab = {}, onPanel = { panel.value = it }, onBack = {},
                    onToggleBookmark = {}, onJumpChapter = { _, _ -> }, onPageFraction = {},
                    onEdit = {}, onWriting = {}, onStory = {},
                    bookmarkedChapters = emptySet(), bookmarkError = null,
                    legacyBookmarkedChapters = emptySet(), legacyBookmarkError = null,
                    onRestoreLegacyBookmark = {},
                    listening = false, onListen = {},
                    onRenameChapter = { _, _ -> }, onAppendChapter = {}, onDeleteLastChapter = {},
                    onRefreshCatalogue = {}, refreshingCatalogue = false, catalogueMessage = null,
                )
            }
        }
        rule.onNodeWithContentDescription(READER_PREVIEW_LABEL_V91).assertExists()
        screenshot("v91-reader-font-panel")
        rule.runOnIdle { panel.value = ReaderMenuPanelV30.SPACING }
        rule.onNodeWithContentDescription(READER_PREVIEW_LABEL_V91).assertExists()
        rule.onNodeWithText("宽松").performClick()
        rule.runOnIdle { assertTrue(kotlin.math.abs(settings.lineFactor - 2.10f) < 0.01f) }
        screenshot("v91-reader-spacing-panel")
    }
}
