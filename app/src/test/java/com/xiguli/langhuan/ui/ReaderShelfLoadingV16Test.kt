package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderShelfLoadingV16Test {
    private val root = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun shelfDoesNotShowEmptyStateBeforeRoomFirstEmission() {
        val library = File(root, "src/main/java/com/xiguli/langhuan/ui/LibraryExperience.kt").readText()
        val shelf = File(root, "src/main/java/com/xiguli/langhuan/ui/ReaderShelfV9.kt").readText()

        assertTrue(library.contains("val libraryLoaded: Boolean = false"))
        assertTrue(library.contains("libraryLoaded = true"))
        assertTrue(shelf.contains("libraryLoaded = state.libraryLoaded"))
        assertTrue(shelf.contains("if (!libraryLoaded)"))
        assertTrue(shelf.contains("正在载入书架"))
        assertTrue(shelf.contains("else if (books.isEmpty())"))
        assertTrue(shelf.contains("这个书架还是空的"))
        assertFalse(shelf.contains("eyebrow = \"琅嬛 · 阅读\""))
        assertFalse(shelf.contains("本地优先"))
    }
}
