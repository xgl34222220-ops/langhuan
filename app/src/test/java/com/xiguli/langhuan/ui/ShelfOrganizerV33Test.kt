package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.shell.LuoShelfSortV33
import com.xiguli.langhuan.ui.shell.luoSortBooksV33
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfOrganizerV33Test {
    private fun book(id: String, title: String, updated: Long) =
        ReaderBookUi(id, title, "", "", "", "", 0, 0, 1, updated)

    @Test
    fun sortModesCycleAndParse() {
        assertEquals(LuoShelfSortV33.UPDATED, LuoShelfSortV33.RECENT_READ.next())
        assertEquals(LuoShelfSortV33.RECENT_READ, LuoShelfSortV33.TITLE.next())
        assertEquals(LuoShelfSortV33.RECENT_READ, LuoShelfSortV33.of("nope"))
        assertEquals(LuoShelfSortV33.TITLE, LuoShelfSortV33.of("title"))
    }

    @Test
    fun recentReadFallsBackToUpdateTime() {
        val books = listOf(book("a", "甲", 10), book("b", "乙", 30), book("c", "丙", 20))
        val lastRead = mapOf("a" to 100L)
        val sorted = luoSortBooksV33(books, LuoShelfSortV33.RECENT_READ) { lastRead[it.id] ?: 0L }
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
        assertEquals(listOf("b", "c", "a"), luoSortBooksV33(books, LuoShelfSortV33.UPDATED) { 0L }.map { it.id })
    }

    @Test
    fun shelfFeaturesAreWired() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLuoShuFunctionalV1.kt").readText()
        val edit = File(root, "src/main/java/com/xiguli/langhuan/ui/BookEditV5.kt").readText()
        val cover = File(root, "src/main/java/com/xiguli/langhuan/ui/CoverStudioV3.kt").readText()
        assertTrue(shelf.contains("\"移动书架\""))
        assertTrue(shelf.contains("LuoShelfAssignmentsV33.removeShelf(prefs, name)"))
        assertTrue(shelf.contains("LuoShelfTabsV33("))
        assertTrue(edit.contains("放弃未保存的修改？"))
        assertFalse(edit.contains("BitmapFactory.decodeFile"))
        assertFalse(cover.contains("BitmapFactory.decodeFile(it)?.asImageBitmap()"))
    }
}
