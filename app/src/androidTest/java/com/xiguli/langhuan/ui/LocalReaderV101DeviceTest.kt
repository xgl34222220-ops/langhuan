package com.xiguli.langhuan.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.ui.theme.LanghuanThemeMode
import com.xiguli.langhuan.ui.theme.LanghuanThemeModeStateV50
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V101 reader: the 目录 / 更多 tabs, the 行段 pill sliders, the 日间 / 夜间 theme pages with
 * 更多主题, and 我的, in light and dark. Screenshots land in reader-qa as local-*.png.
 */
class LocalReaderV101DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val token = System.currentTimeMillis().toString().takeLast(6)

    private fun waitTag(tag: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(tag: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    /** V104: 我的 stays composed beside the shelf in the home pager; wait until it is the page on screen. */
    private fun waitOnScreen(tag: String, timeout: Long = 20_000) = rule.waitUntil(timeout) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().any { kotlin.math.abs(it.boundsInRoot.left) < 2f }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(900)
        rule.waitForIdle()
    }

    /** Lets the render thread present the settled frame (sheet rise, page swap) before capture. */
    private fun shot(label: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(700)
        deviceWindowEvidenceV46(label)
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun waitReader() = rule.waitUntil(30_000) {
        rule.onAllNodes(hasContentDescription("阅读正文")).fetchSemanticsNodes().any {
            it.config.getOrElse(SemanticsProperties.StateDescription) { "" }.startsWith("第")
        }
    }

    private fun openMenu() {
        rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
        waitTag("reader_more_grid")
        settle()
    }

    @Test fun readerSheetThemesSlidersAndMineInLightAndDark() {
        val context = rule.activity.applicationContext
        val prefs = context.getSharedPreferences("reader_qingmo_v9", 0)
        val saved = prefs.all.toMap()
        val manager = StoryProjectManager(context)
        val id = runBlocking {
            val chapters = (1..40).map { n ->
                ImportedChapter("第${n}章 诅咒$n", (1..30).joinToString("\n") { "第${it}段，佝偻的背影比昨天吊长的尸体看上去要矮小得多。" })
            }
            manager.createImportedStory(ImportedManuscript(title = "玩家请上车$token", chapters = chapters)).snapshot.novel.id
        }
        try {
            prefs.edit()
                .remove(ReaderSettingsV30.KEY_NIGHT_MODE).remove("dayTheme").remove("theme")
                .remove("followSystemNightV101").putString("pageMode", "cover").putBoolean("immersive", false)
                .commit()
            for (mode in listOf(LanghuanThemeMode.LIGHT, LanghuanThemeMode.DARK)) {
                val suffix = if (mode == LanghuanThemeMode.LIGHT) "light" else "dark"
                rule.runOnUiThread { LanghuanThemeModeStateV50.set(rule.activity, mode) }
                prefs.edit().putBoolean(ReaderSettingsV30.KEY_NIGHT_MODE, mode == LanghuanThemeMode.DARK).commit()
                settle()
                waitTag("shelf_book_$id")
                rule.onNodeWithTag("shelf_book_$id").performClick()
                waitReader()
                settle()

                // 更多: the 5 x 4 grid.
                openMenu()
                rule.onNodeWithText("背景跟随").assertExistsCompat()
                shot("local-reader-more-$suffix")

                // 目录: header + current chapter highlighted.
                rule.onNodeWithContentDescription("阅读菜单：目录").performClick()
                rule.waitUntil(10_000) { rule.onAllNodes(hasText("正在阅读")).fetchSemanticsNodes().isNotEmpty() }
                settle()
                shot("local-reader-toc-$suffix")

                // 行段: the menu closes and pill sliders float over the page.
                rule.onNodeWithContentDescription("阅读菜单：更多").performClick()
                waitTag("reader_more_grid")
                rule.onNodeWithText("行段").performClick()
                waitTag("reader_quick_adjust")
                settle()
                shot("local-reader-spacing-$suffix")
                back()
                waitGone("reader_quick_adjust")

                // 主题: day (light run) / night (dark run) list, then 更多主题.
                openMenu()
                rule.onNodeWithText("主题").performClick()
                val listTag = if (mode == LanghuanThemeMode.LIGHT) "reader_theme_day" else "reader_theme_night"
                waitTag(listTag)
                if (mode == LanghuanThemeMode.LIGHT) {
                    rule.onNodeWithTag("reader_theme_card_green").performScrollTo().performClick()
                    rule.waitUntil(10_000) { prefs.getString("dayTheme", null) == "green" }
                    rule.onNodeWithTag("reader_theme_card_tea").performScrollTo().performClick()
                    rule.waitUntil(10_000) { prefs.getString("dayTheme", null) == "tea" }
                }
                settle()
                shot("local-reader-theme-$suffix")
                rule.onNodeWithTag("reader_theme_add").performClick()
                waitTag("reader_theme_more")
                rule.onNodeWithTag("reader_theme_more").performClick()
                waitTag("reader_theme_more_page")
                settle()
                shot("local-reader-theme-more-$suffix")
                back() // theme pages -> menu main
                back() // close menu
                back() // reader -> shelf
                waitTag("shelf_book_$id")

                // 我的 (the page to the right of the shelf).
                rule.onNodeWithTag("shelf_mine").performClick()
                waitOnScreen("mine_page")
                waitTag("mine_row_stats")
                if (mode == LanghuanThemeMode.LIGHT) {
                    rule.onNodeWithTag("mine_name").performClick()
                    waitTag("mine_name_field")
                    rule.onNodeWithTag("mine_name_field").performTextReplacement("惜故里")
                    rule.onNodeWithTag("mine_name_save").performClick()
                    rule.waitUntil(10_000) { rule.onAllNodes(hasText("惜故里")).fetchSemanticsNodes().isNotEmpty() }
                    assertEquals("惜故里", LocalProfileStoreV101.name(LocalProfileStoreV101.prefs(context)))
                }
                settle()
                shot("local-mine-$suffix")
                rule.onNodeWithTag("mine_row_stats").performClick()
                waitTag("mine_stats")
                settle()
                shot("local-mine-stats-$suffix")
                back()
                back()
                waitOnScreen("shelf_home")
                waitTag("shelf_book_$id")
            }
            assertTrue(rule.onAllNodes(hasText("签到")).fetchSemanticsNodes().isEmpty())
        } finally {
            rule.runOnUiThread { LanghuanThemeModeStateV50.set(rule.activity, LanghuanThemeMode.FOLLOW_SYSTEM) }
            prefs.edit().clear().also { editor ->
                saved.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
            runBlocking { runCatching { manager.deleteBook(id) } }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
