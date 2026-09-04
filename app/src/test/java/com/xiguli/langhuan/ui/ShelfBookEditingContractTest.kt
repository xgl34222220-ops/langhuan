package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfBookEditingContractTest {
    @Test
    fun shelfUsesFunctionalQingmoFlowsWithoutLosingBookEditing() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLibraryV5.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfQingmoFunctionalV9.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/BookEditV5.kt").readText()

        assertTrue(router.contains("ShelfLibraryV5("))
        assertTrue(entry.contains("ShelfQingmoFunctionalV9("))
        assertFalse(router.contains("ShelfNativeExperienceV4("))

        assertTrue(shelf.contains("\"正在阅读\""))
        assertTrue(shelf.contains("GridCells.Fixed(3)"))
        assertTrue(shelf.contains("Arrangement.spacedBy(28.dp)"))
        assertTrue(shelf.contains("RoundedCornerShape(1.dp)"))
        assertTrue(shelf.contains("fontSize = 14.sp"))
        assertTrue(shelf.contains("Icons.Outlined.Person"))
        assertTrue(shelf.contains("combinedClickable"))
        assertTrue(shelf.contains("BookEditPageV5("))
        assertTrue(shelf.contains("QingmoProfileV9("))
        assertTrue(shelf.contains("QingmoShelfManagerV9("))
        assertTrue(shelf.contains("QingmoNewShelfV9("))
        assertTrue(shelf.contains("QingmoExploreV9("))
        assertTrue(shelf.contains("QingmoHistoryV9("))
        assertTrue(shelf.contains("QingmoMedalsV9("))
        assertTrue(shelf.contains("putStringSet(\"custom_shelves\""))
        assertTrue(shelf.contains("putString(\"checkin_date\""))
        assertTrue(shelf.contains("putString(\"nickname\""))
        assertFalse(shelf.contains("TextButton(onClick = {})"))
        assertFalse(shelf.contains("\"签到\", \"点击签到\") {}"))
        assertFalse(shelf.contains("\"探索\") {}"))
        assertFalse(shelf.contains("\"阅历\") {}"))
        assertFalse(shelf.contains("\"勋章\") {}"))
        assertFalse(shelf.contains("\"读过\") {}"))

        assertTrue(editor.contains("ActivityResultContracts.GetContent()"))
        assertTrue(editor.contains("setLocalCover"))
        assertTrue(editor.contains("coverPath = target.absolutePath"))
        assertTrue(editor.contains("title = title.trim()"))
        assertTrue(editor.contains("genre = genre.trim()"))
        assertTrue(editor.contains("premise = premise.trim()"))
        assertTrue(editor.contains("保存修改"))
    }
}
