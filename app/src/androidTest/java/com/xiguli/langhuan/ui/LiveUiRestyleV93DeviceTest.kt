package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * V93 reference restyle: the shelf cover grid, 「我的」 and 「书架设置」 pages, 图书详情, the reader
 * page chrome and the reader menu tabs on the routed live composables. Screenshots go to
 * reader-qa/v93-* for the before/after contact sheet.
 */
class LiveUiRestyleV93DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun book(id: String, title: String, words: Int, updated: Long) =
        ReaderBookUi("v93-$id", title, "导入作品", "", "", "", words, 0, 3, updated)

    private val books = listOf(
        book("night", "夜航记", 126000, 6L),
        book("mountain", "山中来信", 88000, 5L),
        book("river", "江上书", 52000, 4L),
        book("lamp", "灯下集", 64000, 3L),
        book("snow", "雪落无声", 41000, 2L),
        book("bridge", "旧桥", 37000, 1L),
    )
    private val night = books.first()

    private fun chapters(count: Int, bookId: String = night.id) = (1..count).map { n ->
        ChapterDraft("$bookId-chapter-$n", bookId, n, "第${n}章 夜航", "", emptyList(),
            content = (1..14).joinToString("\n") { "夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。" })
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(600)
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

    /** The rule allows one setContent per test: the tab is switched through state. */
    private fun menu(settings: ReaderSettingsV30, tab: MutableState<ReaderMenuTabV30>, list: List<ChapterDraft>, index: Int) {
        rule.setContent {
            LanghuanStableTheme {
                ReaderMenuV30(
                    visible = true, tab = tab.value, panel = ReaderMenuPanelV30.MAIN, book = night, chapters = list,
                    chapterIndex = index, pageIndex = 1, pageCount = 6, settings = settings,
                    theme = readerThemeV30(settings.theme), bookmarked = true,
                    onDismiss = {}, onTab = { tab.value = it }, onPanel = {}, onBack = {},
                    onToggleBookmark = {}, onJumpChapter = { _, _ -> }, onPageFraction = {},
                    onEdit = {}, onWriting = {}, onStory = {},
                    bookmarkedChapters = setOf(3, 12, index + 1), bookmarkError = null,
                    legacyBookmarkedChapters = emptySet(), legacyBookmarkError = null,
                    onRestoreLegacyBookmark = {}, listening = false, onListen = {},
                    onRenameChapter = { _, _ -> }, onAppendChapter = {}, onDeleteLastChapter = {},
                    onRefreshCatalogue = {}, refreshingCatalogue = false, catalogueMessage = null,
                )
            }
        }
    }

    @Test fun shelfProfileSettingsAndBookDetail() {
        val shelf = rule.activity.getSharedPreferences("qingmo_shelf_v9", Context.MODE_PRIVATE)
        val progress = rule.activity.getSharedPreferences(ShelfReadingProgressStoreV92.PREFS, Context.MODE_PRIVATE)
        val previousLayout = shelf.getString("shelf_layout_v4", null)
        try {
            // No saved choice: the restyled shelf opens on the cover grid.
            shelf.edit().remove("shelf_layout_v4").commit()
            progress.edit()
                .putLong("updated_${night.id}", System.currentTimeMillis())
                .putInt("total_${night.id}", 40).putInt("index_${night.id}", 8)
                .putFloat("fraction_${night.id}", 0.4f)
                .commit()
            var opened = ""
            // V94: 我的 is a bottom-bar tab; the rule allows one setContent, so the page is state.
            val page = androidx.compose.runtime.mutableStateOf(0)
            rule.setContent {
                LanghuanStableTheme {
                    if (page.value == 0) LanghuanHomeV4(
                        state = LibraryExperienceState(stories = books, libraryLoaded = true),
                        importState = LocalBookImportUiStateV1(),
                        onOpenBook = {}, onImportLocal = {}, onDeleteBook = {}, onCreate = {},
                        onOpenTavern = {},
                    ) else MineTabV94(
                        bookCount = books.size, finishedCount = 0, sourceCount = 3, enabledSourceCount = 2,
                        aiLabel = null, onSources = { opened += "sources," }, onImportLocal = {}, onAiSetup = { opened += "ai," },
                    )
                }
            }
            rule.waitUntil(10000) { rule.onAllNodesWithText("旧桥").fetchSemanticsNodes().isNotEmpty() }
            // Grid: the first three books share one row.
            val tops = listOf("夜航记", "山中来信", "江上书").map {
                rule.onAllNodesWithText(it).fetchSemanticsNodes().last().boundsInRoot.top
            }
            assertTrue("cover grid row $tops", tops.max() - tops.min() < 4f)
            screenshot("v93-shelf")

            rule.runOnIdle { page.value = 1 }
            rule.onNodeWithText("书源管理").assertIsDisplayed()
            rule.onNodeWithText("2/3 已启用").assertIsDisplayed()
            rule.onNodeWithText("AI 与模型").assertIsDisplayed()
            screenshot("v93-profile")
            rule.onNodeWithText("书源管理").performClick()
            rule.runOnIdle { assertTrue(opened, opened.contains("sources,")) }
            rule.runOnIdle { page.value = 0 }
            rule.waitUntil(10000) { rule.onAllNodesWithText("旧桥").fetchSemanticsNodes().isNotEmpty() }

            rule.onAllNodesWithContentDescription("书籍菜单").onFirst().performClick()
            rule.onNodeWithText("图书详情").assertIsDisplayed()
            rule.onNodeWithText("读到 9 / 40 章").assertIsDisplayed()
            screenshot("v93-book-detail")
        } finally {
            progress.edit().also { editor ->
                progress.all.keys.filter { key -> books.any { key.endsWith("_${it.id}") } }.forEach(editor::remove)
            }.commit()
            shelf.edit().apply {
                if (previousLayout == null) remove("shelf_layout_v4") else putString("shelf_layout_v4", previousLayout)
            }.commit()
        }
    }

    @Test fun readerPageWithBambooPaper() {
        val prefs = rule.activity.getSharedPreferences("v93-reader-page", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = ReaderSettingsV30(prefs).apply {
            turnMode = ReaderTurnModeV30.NONE
            clickAnimation = false
            backdrop = "bamboo"
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
            screenshot("v93-reader")
        } finally {
            rule.activity.deleteSharedPreferences("v93-reader-page")
            for (name in listOf("reader_progress_v1", "reader_progress_v2")) {
                val p = rule.activity.getSharedPreferences(name, 0)
                p.edit().also { e -> p.all.keys.filter { it.endsWith("_${night.id}") }.forEach(e::remove) }.commit()
            }
        }
    }

    @Test fun readerMenuTabs() {
        val prefs = rule.activity.getSharedPreferences("v93-reader-menu", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val settings = ReaderSettingsV30(prefs)
        val list = chapters(120)
        try {
            val tab = mutableStateOf(ReaderMenuTabV30.DIRECTORY)
            menu(settings, tab, list, 20)
            rule.onNodeWithText("正在阅读").assertIsDisplayed()
            screenshot("v93-menu-directory")
            rule.onNodeWithContentDescription("阅读菜单：更多").performClick()
            rule.runOnIdle { assertEquals(ReaderMenuTabV30.MORE, tab.value) }
            rule.onNodeWithText("屏幕常亮").performScrollTo().assertIsDisplayed()
            screenshot("v93-menu-more")
            rule.onNodeWithContentDescription("阅读菜单：详情").performClick()
            rule.onNodeWithText("当前阅读").assertExists()
            screenshot("v93-menu-details")
            assertEquals(18f, settings.fontSize, 4f)
        } finally {
            rule.activity.deleteSharedPreferences("v93-reader-menu")
        }
    }
}
