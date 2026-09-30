package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfOpenHotfixContractTest {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun shelfBookNavigationUsesDirectClickAndShowsFailures() {
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/shell/ShelfLuoShuFunctionalV1.kt").readText()
        val library = File(root, "src/main/java/com/xiguli/langhuan/ui/LibraryExperience.kt").readText()
        val rootUi = File(root, "src/main/java/com/xiguli/langhuan/ui/LanghuanRootV4.kt").readText()

        assertTrue(shelf.contains(".combinedClickable("))
        assertTrue(shelf.contains("onClick = { onOpenBook(book.id) }"))
        assertTrue(library.contains("withContext(Dispatchers.IO) { projects.chapterDrafts(id) }"))
        assertTrue(library.contains("这本小说没有可读取的章节"))
        assertTrue(rootUi.contains("libraryState.error?.let { error ->"))
        assertTrue(rootUi.contains("text = { Text(error) }"))
        assertTrue(rootUi.contains("libraryVm::clearMessage"))
    }
}
