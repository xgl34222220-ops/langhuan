package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfBookEditingContractTest {
    @Test
    fun luoShuShelfKeepsFunctionalFlowsAndBookEditing() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLibraryV5.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLuoShuFunctionalV1.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/BookEditV5.kt").readText()

        assertTrue(router.contains("ShelfLibraryV5("))
        assertTrue(entry.contains("ShelfLuoShuFunctionalV1("))
        assertFalse(entry.contains("ShelfQingmoFunctionalV9("))
        assertFalse(router.contains("ShelfNativeExperienceV4("))

        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertTrue(shelf.contains("Arrangement.spacedBy(16.dp)"))
        assertTrue(shelf.contains("RoundedCornerShape(18.dp)"))
        assertTrue(shelf.contains("Icons.Outlined.Person"))
        assertTrue(shelf.contains("combinedClickable"))
        assertTrue(shelf.contains("BookEditPageV5("))
        assertTrue(shelf.contains("LuoShelfProfileV1("))
        assertTrue(shelf.contains("LuoShelfManagerV1("))
        assertTrue(shelf.contains("LuoNewShelfV1("))
        assertTrue(shelf.contains("LuoExploreV1("))
        assertTrue(shelf.contains("LuoHistoryV1("))
        assertTrue(shelf.contains("LuoMedalsV1("))
        assertTrue(shelf.contains("putStringSet(\"custom_shelves\""))
        assertTrue(shelf.contains("putString(\"checkin_date\""))
        assertTrue(shelf.contains("putString(\"nickname\""))
        assertTrue(shelf.contains("putBoolean(\"sync_enabled\""))
        assertFalse(shelf.contains("GridCells.Fixed(3)"))
        assertFalse(shelf.contains("Color(0xFFF5F1E9)"))

        assertTrue(editor.contains("ActivityResultContracts.GetContent()"))
        assertTrue(editor.contains("setLocalCover"))
        assertTrue(editor.contains("coverPath = target.absolutePath"))
        assertTrue(editor.contains("title = title.trim()"))
        assertTrue(editor.contains("genre = genre.trim()"))
        assertTrue(editor.contains("premise = premise.trim()"))
        assertTrue(editor.contains("保存修改"))
    }
}
