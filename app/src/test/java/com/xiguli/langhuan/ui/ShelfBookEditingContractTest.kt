package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfBookEditingContractTest {
    @Test
    fun shelfUsesQingmoReplicaFlowsWithoutLosingBookEditing() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLibraryV5.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfQingmoReplicaV8.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/BookEditV5.kt").readText()

        assertTrue(router.contains("ShelfLibraryV5("))
        assertTrue(entry.contains("ShelfQingmoReplicaV8("))
        assertFalse(router.contains("ShelfNativeExperienceV4("))

        assertTrue(shelf.contains("\"正在阅读\""))
        assertTrue(shelf.contains("GridCells.Fixed(3)"))
        assertTrue(shelf.contains("Arrangement.spacedBy(28.dp)"))
        assertTrue(shelf.contains("RoundedCornerShape(1.dp)"))
        assertTrue(shelf.contains("fontSize = 14.sp"))
        assertTrue(shelf.contains("textAlign = TextAlign.Center"))
        assertTrue(shelf.contains("Color(0xFFFFFFFF)"))
        assertTrue(shelf.contains("Icons.Outlined.Person"))
        assertTrue(shelf.contains("combinedClickable"))
        assertTrue(shelf.contains("onLongClick = { onLongPress(book) }"))
        assertTrue(shelf.contains("BookEditPageV5("))
        assertTrue(shelf.contains("QingmoProfileV8("))
        assertTrue(shelf.contains("QingmoShelfManagerV8("))
        assertTrue(shelf.contains("QingmoNewShelfV8("))
        assertTrue(shelf.contains("tween(190"))
        assertFalse(shelf.contains("最近更新"))
        assertFalse(shelf.contains("book.currentWords"))

        assertTrue(editor.contains("ActivityResultContracts.GetContent()"))
        assertTrue(editor.contains("setLocalCover"))
        assertTrue(editor.contains("coverPath = target.absolutePath"))
        assertTrue(editor.contains("title = title.trim()"))
        assertTrue(editor.contains("genre = genre.trim()"))
        assertTrue(editor.contains("premise = premise.trim()"))
        assertTrue(editor.contains("保存修改"))
    }
}
