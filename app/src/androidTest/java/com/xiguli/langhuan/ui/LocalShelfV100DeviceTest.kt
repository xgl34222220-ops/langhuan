package com.xiguli.langhuan.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.shelf.ShelfLibrary
import com.xiguli.langhuan.data.shelf.ShelfLibraryCodec
import com.xiguli.langhuan.data.shelf.ShelfLibraryStore
import com.xiguli.langhuan.ui.theme.LanghuanThemeMode
import com.xiguli.langhuan.ui.theme.LanghuanThemeModeStateV50
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V100 local shelf: 书架 home, 书架列表, the long-press sheet and 图书详情 (both tabs), in light
 * and dark. Screenshots land in reader-qa as local-*.png for the PR contact sheet.
 */
class LocalShelfV100DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val token = System.currentTimeMillis().toString().takeLast(6)

    private fun waitTag(tag: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitText(text: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(tag: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    private fun settle() {
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
    }

    private fun setTheme(mode: LanghuanThemeMode) {
        rule.runOnUiThread { LanghuanThemeModeStateV50.set(rule.activity, mode) }
        settle()
    }

    private fun seed(): List<String> = runBlocking {
        val context = rule.activity.applicationContext
        val manager = StoryProjectManager(context)
        val titles = listOf("玩家请上车$token", "噩梦使徒", "迷雾之上", "绝对之门", "龙族", "蓝白社")
        titles.mapIndexed { index, title ->
            val chapters = (1..(6 + index)).map { n ->
                ImportedChapter("第${n}章 诅咒$n", (1..40).joinToString("\n") { "第${it}段，夜色落在平静的水面上，列车驶入雾中。" })
            }
            val id = manager.createImportedStory(ImportedManuscript(title = title, chapters = chapters)).snapshot.novel.id
            context.getSharedPreferences(LocalReaderViewModel.LOCAL_BOOK_META_PREFS, 0).edit()
                .putString("name_$id", "《$title》作者：海晏山（1-3106章）.txt")
                .putLong("size_$id", 13_736_345L)
                .putString("format_$id", "TXT")
                .putString("author_$id", if (index == 0) "海晏山" else "")
                .putLong("imported_$id", System.currentTimeMillis() - index * 60_000L)
                .commit()
            if (index == 0) {
                context.getSharedPreferences(ShelfReadingProgressStoreV92.PREFS, 0).edit()
                    .putLong("updated_$id", System.currentTimeMillis())
                    .putInt("index_$id", 1).putInt("total_$id", chapters.size).putFloat("fraction_$id", 0.4f)
                    .commit()
            }
            id
        }
    }

    @Test fun shelfGroupsSheetAndDetailInLightAndDark() {
        val context = rule.activity.applicationContext
        context.getSharedPreferences("shelf_ui_v1", 0).edit().clear().commit()
        val ids = seed()
        val first = ids.first()
        try {
            for (mode in listOf(LanghuanThemeMode.LIGHT, LanghuanThemeMode.DARK)) {
                val suffix = if (mode == LanghuanThemeMode.LIGHT) "light" else "dark"
                setTheme(mode)
                waitTag("shelf_grid")
                waitTag("shelf_book_$first")
                rule.onNodeWithTag("shelf_title").assertExistsCompat()
                settle()
                deviceWindowEvidenceV46("local-shelf-$suffix")

                // Long-press sheet.
                rule.onNodeWithTag("shelf_book_$first").performTouchInput { longClick() }
                waitTag("book_action_sheet")
                waitText("查看详情")
                settle()
                deviceWindowEvidenceV46("local-sheet-$suffix")

                // 图书详情 · 详情.
                rule.onNodeWithTag("sheet_detail").performClick()
                waitTag("detail_details_tab")
                waitText("全文字数")
                rule.waitUntil(20_000) {
                    rule.onAllNodes(hasText("计算中…")).fetchSemanticsNodes().isEmpty()
                }
                waitText("文件大小")
                settle()
                deviceWindowEvidenceV46("local-detail-$suffix")

                // 图书详情 · 封面.
                rule.onNodeWithTag("detail_tab_cover").performClick()
                waitTag("detail_cover_tab")
                waitTag("cover_gallery")
                settle()
                deviceWindowEvidenceV46("local-detail-cover-$suffix")
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                waitTag("shelf_grid")

                // 书架列表.
                rule.onNodeWithTag("shelf_title").performClick()
                waitTag("shelf_list_page")
                if (mode == LanghuanThemeMode.LIGHT) {
                    rule.onNodeWithTag("shelf_list_add").performClick()
                    waitTag("shelf_name_field")
                    rule.onNodeWithTag("shelf_name_field").performTextReplacement("18禁$token")
                    rule.onNodeWithTag("shelf_name_confirm").performClick()
                    waitText("18禁$token")
                }
                settle()
                deviceWindowEvidenceV46("local-shelf-list-$suffix")
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                waitTag("shelf_grid")
            }

            // 移动书架: the second book moves to the new shelf and leaves 正在阅读.
            val moving = ids[1]
            val store = { ShelfLibraryCodec.decode(File(context.filesDir, ShelfLibraryStore.FILE_NAME).readText())!! }
            val groupId = store().groups.first { it.name == "18禁$token" }.id
            rule.onNodeWithTag("shelf_book_$moving").performTouchInput { longClick() }
            waitTag("sheet_move")
            rule.onNodeWithTag("sheet_move").performClick()
            waitTag("move_to_$groupId")
            settle()
            deviceWindowEvidenceV46("local-move-sheet-dark")
            rule.onNodeWithTag("move_to_$groupId").performClick()
            waitGone("shelf_book_$moving")
            rule.waitUntil(10_000) { store().books[moving]?.groupId == groupId }

            // Editing the author on 图书详情 persists in the shelf metadata.
            rule.onNodeWithTag("shelf_book_$first").performTouchInput { longClick() }
            waitTag("sheet_detail")
            rule.onNodeWithTag("sheet_detail").performClick()
            waitTag("detail_author")
            rule.onNodeWithTag("detail_author").performTextReplacement("海晏山·改")
            rule.onNodeWithTag("detail_save").performClick()
            waitTag("shelf_grid")
            rule.waitUntil(10_000) { store().books[first]?.author == "海晏山·改" }
            assertEquals(ShelfLibrary.DEFAULT_GROUP_ID, store().books.getValue(first).groupId)

            // 我的 placeholder opens from the person icon.
            rule.onNodeWithTag("shelf_mine").performClick()
            waitTag("mine_page")
            settle()
            deviceWindowEvidenceV46("local-mine-dark")
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            waitTag("shelf_grid")
            assertTrue(rule.onAllNodes(hasText("书城")).fetchSemanticsNodes().isEmpty())
        } finally {
            rule.runOnUiThread { LanghuanThemeModeStateV50.set(rule.activity, LanghuanThemeMode.FOLLOW_SYSTEM) }
            runBlocking { ids.forEach { runCatching { StoryProjectManager(context).deleteBook(it) } } }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
