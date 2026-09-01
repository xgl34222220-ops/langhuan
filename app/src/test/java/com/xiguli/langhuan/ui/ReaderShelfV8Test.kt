package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderShelfV8Test {
    private fun book(id: String, title: String, genre: String = "导入作品", updatedAt: Long = 0L) = ReaderBookUi(
        id = id,
        title = title,
        genre = genre,
        premise = "",
        theme = "",
        coverPath = "",
        currentWords = 0,
        targetWords = 0,
        currentChapter = 1,
        updatedAt = updatedAt,
    )

    @Test
    fun recentSortUsesLastReadBeforeUpdatedTime() {
        val books = listOf(
            book("a", "甲", updatedAt = 300),
            book("b", "乙", updatedAt = 100),
            book("c", "丙", updatedAt = 500),
        )
        val lastRead = mapOf("a" to 10L, "b" to 30L, "c" to 20L)

        val sorted = sortShelfBooksV8(
            books = books,
            query = "",
            sort = ReaderShelfSortV8.RECENT,
            lastRead = { lastRead[it] ?: 0L },
            importedAt = { 0L },
        )

        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun titleAndGenreCanBeSearched() {
        val books = listOf(
            book("a", "迷雾之上"),
            book("b", "长夜", genre = "悬疑"),
            book("c", "春日", genre = "都市"),
        )

        assertEquals(
            listOf("a"),
            sortShelfBooksV8(books, "迷雾", ReaderShelfSortV8.TITLE, { 0L }, { 0L }).map { it.id },
        )
        assertEquals(
            listOf("b"),
            sortShelfBooksV8(books, "悬疑", ReaderShelfSortV8.TITLE, { 0L }, { 0L }).map { it.id },
        )
    }

    @Test
    fun importedSortUsesImportTime() {
        val books = listOf(book("a", "甲"), book("b", "乙"), book("c", "丙"))
        val imported = mapOf("a" to 1L, "b" to 5L, "c" to 3L)

        val sorted = sortShelfBooksV8(
            books = books,
            query = "",
            sort = ReaderShelfSortV8.IMPORTED,
            lastRead = { 0L },
            importedAt = { imported[it] ?: 0L },
        )

        assertEquals(listOf("b", "c", "a"), sorted.map { it.id })
    }
}
