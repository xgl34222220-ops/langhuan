package com.xiguli.langhuan.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
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
 * V100 local shelf: 书架 home, 书架列表 (a page to the left, opened by swiping right), 我的 (V104:
 * a page to the right, opened by swiping left or the person icon; half-way frames of both swipes
 * are captured), the empty-shelf state, the long-press sheet and 图书详情 (both tabs), in light
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

    /** True once the node is settled on screen (pages of the shelf pager stay composed off-screen). */
    private fun onScreen(tag: String): Boolean = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
        .any { kotlin.math.abs(it.boundsInRoot.left) < 2f }

    private fun waitOnScreen(tag: String, timeout: Long = 20_000) = rule.waitUntil(timeout) { onScreen(tag) }

    /**
     * V102: drags right on 正在阅读 to reveal 书架列表. Captures the half-way frame (the list
     * emerging under the shelf), then finishes the drag and waits for the list to settle.
     */
    private fun swipeOpenShelfList(suffix: String) {
        // Let any slide-back animation finish so the drag starts on a settled pager.
        rule.waitForIdle()
        SystemClock.sleep(500)
        rule.waitForIdle()
        val pager = rule.onNodeWithTag("shelf_pager")
        pager.performTouchInput {
            down(Offset(width * 0.12f, height * 0.55f))
            repeat(9) { moveBy(Offset(width * 0.05f, 0f)) }
        }
        rule.waitForIdle()
        val left = { tag: String -> rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { it.boundsInRoot.left } }
        val moved = left("shelf_home")
        Log.i("LocalShelfV100", "mid-swipe $suffix shelf_home=$moved list=${left("shelf_list_page")}")
        shot("local-shelf-swipe-$suffix")
        assertTrue("mid-swipe: the shelf did not follow the finger ($moved)", moved.any { it > 40f })
        pager.performTouchInput {
            repeat(6) { moveBy(Offset(width * 0.05f, 0f)) }
            up()
        }
        waitOnScreen("shelf_list_page")
    }

    /**
     * V104: drags left on 正在阅读 to reveal 我的 (the right-hand page). Captures the half-way
     * frame, then finishes the drag and waits for 我的 to settle.
     */
    private fun swipeOpenMine(suffix: String) {
        rule.waitForIdle()
        SystemClock.sleep(500)
        rule.waitForIdle()
        val pager = rule.onNodeWithTag("shelf_pager")
        pager.performTouchInput {
            down(Offset(width * 0.88f, height * 0.55f))
            repeat(9) { moveBy(Offset(-width * 0.05f, 0f)) }
        }
        rule.waitForIdle()
        val left = { tag: String -> rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().map { it.boundsInRoot.left } }
        // boundsInRoot clips at the window edge, so track 我的 coming in from the right instead.
        val width = rule.onNodeWithTag("shelf_pager").fetchSemanticsNode().size.width
        val mine = left("mine_page")
        Log.i("LocalShelfV100", "mid-swipe mine $suffix shelf_home=${left("shelf_home")} mine=$mine width=$width")
        shot("local-mine-swipe-$suffix")
        assertTrue("mid-swipe: 我的 did not follow the finger ($mine of $width)", mine.any { it > 40f && it < width - 40f })
        pager.performTouchInput {
            repeat(6) { moveBy(Offset(-width * 0.05f, 0f)) }
            up()
        }
        waitOnScreen("mine_page")
    }

    private fun backEvent(progress: Float) = androidx.activity.BackEventCompat(8f, 1200f, progress, androidx.activity.BackEventCompat.EDGE_LEFT)

    /**
     * V103: drives a predictive back swipe by hand and captures it part-way, so the contact sheet
     * shows the real mid-transition frames (the CI emulator runs with animations scaled to 0).
     */
    private fun predictiveBack(label: String, vararg stops: Float, commit: Boolean = true) {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f)) }
        rule.waitForIdle()
        stops.forEach { progress ->
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(progress)) }
            rule.waitForIdle()
            shot("$label-${(progress * 100).toInt()}")
        }
        rule.runOnUiThread {
            if (commit) rule.activity.onBackPressedDispatcher.onBackPressed()
            else rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        settle()
    }

    /** V103: captures a push one frame-batch in (the test clock is held so the frame stays put). */
    private fun midPush(label: String, millis: Long, action: () -> Unit) {
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        try {
            action()
            rule.mainClock.advanceTimeBy(millis)
            SystemClock.sleep(500)
            deviceWindowEvidenceV46(label)
        } finally {
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
        }
    }

    /** Lets the render thread present the settled frame before the window screenshot. */
    private fun shot(label: String) {
        rule.waitForIdle()
        SystemClock.sleep(600)
        deviceWindowEvidenceV46(label)
    }

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
        val store = { ShelfLibraryCodec.decode(File(context.filesDir, ShelfLibraryStore.FILE_NAME).readText())!! }
        // A week of reading time so 我的 shows its chart and streak (restored afterwards).
        val statsPrefs = context.getSharedPreferences("reader_stats_v35", 0)
        val savedStats = statsPrefs.all.toMap()
        run {
            val format = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            val minutes = listOf(22L, 64L, 35L, 0L, 48L, 18L, 26L) // today first, then each day back
            val editor = statsPrefs.edit()
            minutes.forEachIndexed { back, m ->
                val day = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -back) }
                editor.putLong("day_" + format.format(day.time), m * 60L)
            }
            editor.putLong("total", 41L * 3600L + 20L * 60L).putLong("pages", 3260L).putLong("chapters", 214L).commit()
        }
        try {
            for (mode in listOf(LanghuanThemeMode.LIGHT, LanghuanThemeMode.DARK)) {
                val suffix = if (mode == LanghuanThemeMode.LIGHT) "light" else "dark"
                setTheme(mode)
                waitTag("shelf_grid")
                waitTag("shelf_book_$first")
                rule.onNodeWithTag("shelf_title").assertExistsCompat()
                settle()
                shot("local-shelf-$suffix")

                // Long-press sheet.
                rule.onNodeWithTag("shelf_book_$first").performTouchInput { longClick() }
                waitTag("book_action_sheet")
                waitText("查看详情")
                settle()
                shot("local-sheet-$suffix")

                // 图书详情 · 详情.
                rule.onNodeWithTag("sheet_detail").performClick()
                waitTag("detail_details_tab")
                waitText("全文字数")
                rule.waitUntil(20_000) {
                    rule.onAllNodes(hasText("计算中…")).fetchSemanticsNodes().isEmpty()
                }
                waitText("文件大小")
                settle()
                shot("local-detail-$suffix")

                // 图书详情 · 封面.
                rule.onNodeWithTag("detail_tab_cover").performClick()
                waitTag("detail_cover_tab")
                waitTag("cover_gallery")
                settle()
                shot("local-detail-cover-$suffix")
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                waitTag("shelf_grid")
                waitOnScreen("shelf_home")

                // 书架列表: swipe right from 正在阅读 (finger-tracking pager), then back.
                swipeOpenShelfList(suffix)
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                waitOnScreen("shelf_home")
                // Tapping the title still opens the list; tapping a shelf there slides back.
                rule.onNodeWithTag("shelf_title").performClick()
                waitOnScreen("shelf_list_page")
                // Tap the shelf's name (the row's cover strip would open a book instead).
                rule.onNodeWithTag("shelf_group_name_${ShelfLibrary.DEFAULT_GROUP_ID}", useUnmergedTree = true).performClick()
                waitOnScreen("shelf_home")
                swipeOpenShelfList(suffix)
                if (mode == LanghuanThemeMode.LIGHT) {
                    rule.onNodeWithTag("shelf_list_add").performClick()
                    waitTag("shelf_name_field")
                    rule.onNodeWithTag("shelf_name_field").performTextReplacement("18禁$token")
                    rule.onNodeWithTag("shelf_name_confirm").performClick()
                    waitText("18禁$token")
                    // The new shelf is empty: open it for the empty-state frame, then go back to 默认.
                    rule.waitUntil(10_000) { store().groups.any { it.name == "18禁$token" } }
                    val emptyId = store().groups.first { it.name == "18禁$token" }.id
                    rule.onNodeWithTag("shelf_group_name_$emptyId", useUnmergedTree = true).performClick()
                    waitOnScreen("shelf_home")
                    waitTag("shelf_empty_import")
                    settle()
                    shot("local-shelf-empty-$suffix")
                    rule.onNodeWithTag("shelf_title").performClick()
                    waitOnScreen("shelf_list_page")
                    rule.onNodeWithTag("shelf_group_name_${ShelfLibrary.DEFAULT_GROUP_ID}", useUnmergedTree = true).performClick()
                    waitOnScreen("shelf_home")
                    waitTag("shelf_grid")
                    rule.onNodeWithTag("shelf_title").performClick()
                    waitOnScreen("shelf_list_page")
                }
                settle()
                shot("local-shelf-list-$suffix")
                if (mode == LanghuanThemeMode.LIGHT) {
                    // Predictive back from 书架列表: both pages follow the gesture, attached.
                    predictiveBack("local-list-back-$suffix", 0.5f)
                } else {
                    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                }
                waitOnScreen("shelf_home")
                waitTag("shelf_grid")
                if (mode == LanghuanThemeMode.LIGHT) {
                    // V104: 我的 is the page to the right. The person icon slides there (captured
                    // part-way), predictive back drags it home at 35 % / 70 %, and a left swipe
                    // follows the finger there too.
                    midPush("local-mine-push-mid-$suffix", 96) { rule.onNodeWithTag("shelf_mine").performClick() }
                    waitOnScreen("mine_page")
                    waitTag("mine_week_card")
                    settle()
                    shot("local-mine-shelfflow-$suffix")
                    predictiveBack("local-mine-back-$suffix", 0.35f, 0.7f)
                    waitOnScreen("shelf_home")
                    swipeOpenMine(suffix)
                    settle()
                    rule.onNodeWithTag("mine_back").performClick()
                    waitOnScreen("shelf_home")
                    waitTag("shelf_grid")
                }
            }

            // 移动书架: the second book moves to the new shelf and leaves 正在阅读.
            val moving = ids[1]
            val groupId = store().groups.first { it.name == "18禁$token" }.id
            rule.onNodeWithTag("shelf_book_$moving").performTouchInput { longClick() }
            waitTag("sheet_move")
            rule.onNodeWithTag("sheet_move").performClick()
            waitTag("move_to_$groupId")
            settle()
            shot("local-move-sheet-dark")
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

            // 我的 opens from the person icon; back slides home.
            rule.onNodeWithTag("shelf_mine").performClick()
            waitOnScreen("mine_page")
            settle()
            shot("local-mine-dark")
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            waitOnScreen("shelf_home")
            waitTag("shelf_grid")
            assertTrue(rule.onAllNodes(hasText("书城")).fetchSemanticsNodes().isEmpty())
        } finally {
            rule.runOnUiThread { LanghuanThemeModeStateV50.set(rule.activity, LanghuanThemeMode.FOLLOW_SYSTEM) }
            statsPrefs.edit().clear().also { editor ->
                savedStats.forEach { (key, value) ->
                    when (value) {
                        is Long -> editor.putLong(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is String -> editor.putString(key, value)
                    }
                }
            }.commit()
            runBlocking { ids.forEach { runCatching { StoryProjectManager(context).deleteBook(it) } } }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
