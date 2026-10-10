package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.ui.design.LanghuanBottomBarV94
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import com.xiguli.langhuan.ui.theme.LanghuanThemeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V96 polish: the shelf's 「显示书名」 switch (整理 → 显示) hides the titles under the covers,
 * tightens the grid, is persisted across a fresh composition and keeps each title reachable for
 * TalkBack; the refined bottom bar renders in light and dark. Screenshots feed the PR sheet.
 */
class ShelfPolishV96DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun book(id: String, title: String, words: Int, updated: Long) =
        ReaderBookUi("v96-$id", title, "导入作品", "", "", "", words, 0, 3, updated)

    private val books = listOf(
        book("night", "夜航记", 126000, 9L),
        book("mountain", "山中来信", 88000, 8L),
        book("river", "江上书", 52000, 7L),
        book("lamp", "灯下集", 64000, 6L),
        book("snow", "雪落无声", 41000, 5L),
        book("bridge", "旧桥", 37000, 4L),
    )

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

    /** Six tiles in a 3-column grid make two rows: the spread of their tops is one row pitch. */
    private fun rowPitch(): Float {
        val tops = books.map { rule.onNodeWithTag("shelf-book-${it.id}").fetchSemanticsNode().boundsInRoot.top }
        return tops.max() - tops.min()
    }

    private fun titleShown(id: String) =
        rule.onAllNodesWithTag("shelf-title-v96-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun hideTitlesSwitchIsPersistedTightensGridAndKeepsTitlesAccessible() {
        val shelf = rule.activity.getSharedPreferences(HOME_SHELF_PREFS_NAME_V96, Context.MODE_PRIVATE)
        val previousLayout = shelf.getString("shelf_layout_v4", null)
        val hadTitles = shelf.contains(HOME_SHOW_TITLES_V96)
        val previousTitles = shelf.getBoolean(HOME_SHOW_TITLES_V96, true)
        try {
            shelf.edit().remove("shelf_layout_v4").remove(HOME_SHOW_TITLES_V96).commit()
            val remount = mutableIntStateOf(0)
            rule.setContent {
                LanghuanStableTheme(themeMode = LanghuanThemeMode.LIGHT) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            key(remount.intValue) {
                                LanghuanHomeV4(
                                    state = LibraryExperienceState(stories = books, libraryLoaded = true),
                                    importState = LocalBookImportUiStateV1(),
                                    onOpenBook = {}, onImportLocal = {}, onDeleteBook = {}, onCreate = {},
                                    onOpenTavern = {}, insideTabs = true,
                                )
                            }
                        }
                        LanghuanBottomBarV94(tabs = rootBottomTabsV94(false), selectedKey = "SHELF", onSelect = {})
                    }
                }
            }
            rule.waitUntil(10000) { rule.onAllNodesWithText("旧桥").fetchSemanticsNodes().isNotEmpty() }
            // Default: titles shown under the covers.
            assertTrue(rule.onAllNodesWithTag("shelf-grid-titled").fetchSemanticsNodes().isNotEmpty())
            assertTrue(titleShown("mountain"))
            rule.onNodeWithTag("bottom-tab-SHELF").assertIsSelected()
            val titledPitch = rowPitch()
            screenshot("v96-shelf-titles")

            // 整理 → 显示 → 「显示书名」 off.
            rule.onNodeWithContentDescription("整理书架").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTag("shelf-show-titles").fetchSemanticsNodes().isNotEmpty() }
            screenshot("v96-shelf-display-options")
            rule.onNodeWithTag("shelf-show-titles").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTag("shelf-grid-covers").fetchSemanticsNodes().isNotEmpty() }
            rule.runOnIdle { assertFalse(shelf.getBoolean(HOME_SHOW_TITLES_V96, true)) }
            rule.onNodeWithContentDescription("整理书架").performClick()
            rule.waitUntil(5000) { !titleShown("mountain") }
            // Titles are gone from under the covers but still announced on each tile.
            assertTrue(rule.onAllNodesWithContentDescription("山中来信").fetchSemanticsNodes().isNotEmpty())
            val coverPitch = rowPitch()
            assertTrue("cover-only rows are tighter ($coverPitch < $titledPitch)", coverPitch < titledPitch - 20f)
            screenshot("v96-shelf-covers")

            // A fresh composition (e.g. the app restarted) reads the saved choice.
            rule.runOnIdle { remount.intValue++ }
            rule.waitUntil(5000) { rule.onAllNodesWithTag("shelf-grid-covers").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(titleShown("river"))

            // Switching it back on restores the titled grid and the saved value.
            rule.onNodeWithContentDescription("整理书架").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTag("shelf-show-titles").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("shelf-show-titles").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithTag("shelf-grid-titled").fetchSemanticsNodes().isNotEmpty() }
            rule.runOnIdle { assertTrue(shelf.getBoolean(HOME_SHOW_TITLES_V96, false)) }
            rule.waitUntil(5000) { titleShown("river") }
        } finally {
            shelf.edit().apply {
                if (previousLayout == null) remove("shelf_layout_v4") else putString("shelf_layout_v4", previousLayout)
                if (hadTitles) putBoolean(HOME_SHOW_TITLES_V96, previousTitles) else remove(HOME_SHOW_TITLES_V96)
            }.commit()
        }
    }

    @Test fun bottomBarRendersInLightAndDark() {
        val dark = mutableStateOf(false)
        val selected = mutableStateOf("MINE")
        rule.setContent {
            LanghuanStableTheme(themeMode = if (dark.value) LanghuanThemeMode.DARK else LanghuanThemeMode.LIGHT) {
                val t = LocalLanghuanUiTokens.current
                Column(Modifier.fillMaxSize().background(t.background)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        MineTabV94(
                            bookCount = books.size, finishedCount = 1, sourceCount = 3, enabledSourceCount = 2,
                            aiLabel = null, onSources = {}, onImportLocal = {}, onAiSetup = {},
                        )
                    }
                    LanghuanBottomBarV94(
                        tabs = rootBottomTabsV94(runActive = true),
                        selectedKey = selected.value,
                        onSelect = { selected.value = it },
                    )
                }
            }
        }
        rule.waitUntil(5000) { rule.onAllNodesWithTag("langhuan-bottom-bar").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("bottom-tab-MINE").assertIsSelected()
        // The 创作 tab carries the run badge; its state is announced.
        assertEquals(1, rule.onAllNodesWithTag("bottom-tab-CREATE_HUB").fetchSemanticsNodes().size)
        screenshot("v96-bar-light")
        rule.onNodeWithTag("bottom-tab-SHELF").performClick()
        rule.onNodeWithTag("bottom-tab-SHELF").assertIsSelected()
        rule.runOnIdle { selected.value = "MINE"; dark.value = true }
        rule.waitForIdle()
        rule.onNodeWithTag("bottom-tab-MINE").assertIsSelected()
        screenshot("v96-bar-dark")
    }
}
