package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfBookEditingContractTest {
    @Test
    fun literaryShelfKeepsBookEditingAndFunctionalRoutes() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val router = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()
        val entry = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLibraryV5.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/LanghuanLiteraryShelfV1.kt").readText()
        val workspace = File(root, "src/main/java/com/xiguli/langhuan/ui/workspace/LanghuanBookWorkspaceV1.kt").readText()
        val editor = File(root, "src/main/java/com/xiguli/langhuan/ui/BookEditV5.kt").readText()

        assertTrue(router.contains("ShelfLibraryV5("))
        assertTrue(entry.contains("LanghuanLiteraryShelfV1("))
        assertFalse(router.contains("ShelfNativeExperienceV4("))

        assertTrue(shelf.contains("LiteraryShelfTabV1.HOME"))
        assertTrue(shelf.contains("LiteraryShelfTabV1.LIBRARY"))
        assertTrue(shelf.contains("LiteraryShelfTabV1.CREATION"))
        assertTrue(shelf.contains("LiteraryShelfTabV1.PROFILE"))
        assertTrue(shelf.contains("GridCells.Fixed(2)"))
        assertTrue(shelf.contains("combinedClickable"))
        assertTrue(shelf.contains("onOpenTavern"))
        assertTrue(shelf.contains("onDeleteBook"))
        assertFalse(shelf.contains("TextButton(onClick = {})"))

        assertTrue(router.contains("RootRouteV4.WORKSPACE"))
        assertTrue(router.contains("LanghuanBookWorkspaceV1("))
        assertTrue(workspace.contains("BookEditViewModelV5"))
        assertTrue(workspace.contains("BookEditPageV5("))
        assertTrue(workspace.contains("contentDescription = \"编辑书籍\""))
        assertTrue(workspace.contains("\"继续正文\""))
        assertTrue(workspace.contains("\"写作\""))
        assertTrue(workspace.contains("\"故事\""))
        assertTrue(workspace.contains("\"世界与角色\""))
        assertTrue(workspace.contains("\"AI 助手\""))

        assertTrue(editor.contains("ActivityResultContracts.GetContent()"))
        assertTrue(editor.contains("setLocalCover"))
        assertTrue(editor.contains("coverPath = target.absolutePath"))
        assertTrue(editor.contains("title = title.trim()"))
        assertTrue(editor.contains("genre = genre.trim()"))
        assertTrue(editor.contains("premise = premise.trim()"))
        assertTrue(editor.contains("保存修改"))
    }
}
