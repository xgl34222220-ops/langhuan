package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfBookEditingContractTest {
    @Test
    fun shelfUsesQuietThreeColumnLayoutWithoutLosingBookEditing() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLibraryV5.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfQingmoV6.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/BookEditV5.kt").readText()

        assertTrue(router.contains("ShelfLibraryV5("))
        assertTrue(entry.contains("ShelfQingmoV6("))
        assertFalse(router.contains("ShelfNativeExperienceV4("))

        assertTrue(shelf.contains("GridCells.Fixed(3)"))
        assertTrue(shelf.contains("combinedClickable"))
        assertTrue(shelf.contains("onLongClick = onLongPress"))
        assertTrue(shelf.contains("编辑书籍"))
        assertTrue(shelf.contains("BookEditPageV5("))
        assertFalse(shelf.contains("最近更新"))
        assertFalse(shelf.contains("按更新时间"))
        assertFalse(shelf.contains("book.currentWords"))
        assertFalse(shelf.contains("Icons.Rounded.MoreHoriz"))

        assertTrue(editor.contains("ActivityResultContracts.GetContent()"))
        assertTrue(editor.contains("setLocalCover"))
        assertTrue(editor.contains("coverPath = target.absolutePath"))
        assertTrue(editor.contains("title = title.trim()"))
        assertTrue(editor.contains("genre = genre.trim()"))
        assertTrue(editor.contains("premise = premise.trim()"))
        assertTrue(editor.contains("保存修改"))
    }
}
