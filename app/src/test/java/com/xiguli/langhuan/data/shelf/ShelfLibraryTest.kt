package com.xiguli.langhuan.data.shelf

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfLibraryTest {
    private val now = 1_700_000_000_000L

    @Test
    fun emptyLibraryHasOnlyTheDefaultShelf() {
        val data = ShelfLibrary.empty(now)
        assertEquals(listOf(ShelfLibrary.DEFAULT_GROUP_ID), data.groups.map { it.id })
        assertEquals("正在阅读", data.groups.single().name)
    }

    @Test
    fun reconcileAddsNewBooksToDefaultShelfAndDropsDeletedOnes() {
        var data = ShelfLibrary.empty(now)
        data = ShelfLibrary.createGroup(data, "武侠", now, id = "wx").getOrThrow()
        data = ShelfLibrary.reconcile(data, listOf("a", "b"), now, firstSeenAt = { if (it == "a") 42L else 0L })
        data = ShelfLibrary.moveBook(data, "b", "wx").getOrThrow()
        assertEquals(42L, data.books.getValue("a").addedAt)
        assertEquals(now, data.books.getValue("b").addedAt)

        // "a" was deleted from Room, "c" was imported; "b" keeps its shelf.
        data = ShelfLibrary.reconcile(data, listOf("b", "c"), now + 1)
        assertEquals(setOf("b", "c"), data.books.keys)
        assertEquals("wx", data.books.getValue("b").groupId)
        assertEquals(ShelfLibrary.DEFAULT_GROUP_ID, data.books.getValue("c").groupId)
    }

    @Test
    fun reconcileCanTargetTheOpenShelfForNewBooks() {
        var data = ShelfLibrary.createGroup(ShelfLibrary.empty(now), "科幻", now, id = "sf").getOrThrow()
        data = ShelfLibrary.reconcile(data, listOf("x"), now, targetGroupId = "sf")
        assertEquals("sf", data.books.getValue("x").groupId)
        // An unknown target falls back to the default shelf.
        data = ShelfLibrary.reconcile(data, listOf("x", "y"), now, targetGroupId = "missing")
        assertEquals(ShelfLibrary.DEFAULT_GROUP_ID, data.books.getValue("y").groupId)
    }

    @Test
    fun groupNamesAreTrimmedNonBlankAndUnique() {
        val data = ShelfLibrary.empty(now)
        assertTrue(ShelfLibrary.createGroup(data, "   ", now).isFailure)
        assertTrue(ShelfLibrary.createGroup(data, "正在阅读", now).isFailure)
        val created = ShelfLibrary.createGroup(data, "  18禁  ", now, id = "g").getOrThrow()
        assertEquals("18禁", created.groups.last().name)
        assertEquals(ShelfLibrary.MAX_NAME_LENGTH, ShelfLibrary.cleanName("长".repeat(50))!!.length)
    }

    @Test
    fun renameRejectsDuplicatesButAllowsKeepingTheSameName() {
        var data = ShelfLibrary.createGroup(ShelfLibrary.empty(now), "A", now, id = "a").getOrThrow()
        data = ShelfLibrary.createGroup(data, "B", now, id = "b").getOrThrow()
        assertTrue(ShelfLibrary.renameGroup(data, "a", "B").isFailure)
        assertTrue(ShelfLibrary.renameGroup(data, "a", "A").isSuccess)
        assertEquals("新名", ShelfLibrary.renameGroup(data, "a", "新名").getOrThrow().groups.first { it.id == "a" }.name)
        assertTrue(ShelfLibrary.renameGroup(data, "missing", "x").isFailure)
    }

    @Test
    fun deletingAShelfKeepsItsBooksOnTheDefaultShelf() {
        var data = ShelfLibrary.createGroup(ShelfLibrary.empty(now), "已读", now, id = "done").getOrThrow()
        data = ShelfLibrary.reconcile(data, listOf("a", "b"), now)
        data = ShelfLibrary.moveBook(data, "a", "done").getOrThrow()
        assertEquals(1, ShelfLibrary.counts(data)["done"])

        data = ShelfLibrary.deleteGroup(data, "done").getOrThrow()
        assertFalse(data.groups.any { it.id == "done" })
        assertEquals(ShelfLibrary.DEFAULT_GROUP_ID, data.books.getValue("a").groupId)
        assertEquals(2, ShelfLibrary.counts(data)[ShelfLibrary.DEFAULT_GROUP_ID])
        assertTrue(ShelfLibrary.deleteGroup(data, ShelfLibrary.DEFAULT_GROUP_ID).isFailure)
    }

    @Test
    fun moveToMissingShelfIsRefused() {
        val data = ShelfLibrary.reconcile(ShelfLibrary.empty(now), listOf("a"), now)
        assertTrue(ShelfLibrary.moveBook(data, "a", "nope").isFailure)
    }

    @Test
    fun normalizeRepairsMissingDefaultShelfAndDanglingGroups() {
        val broken = ShelfLibraryData(
            groups = listOf(ShelfGroup("x", "X")),
            books = mapOf("a" to ShelfBookMeta("a", groupId = "gone"), "b" to ShelfBookMeta("b", groupId = "x")),
        )
        val fixed = ShelfLibrary.normalize(broken, now)
        assertEquals(ShelfLibrary.DEFAULT_GROUP_ID, fixed.groups.first().id)
        assertEquals(ShelfLibrary.DEFAULT_GROUP_ID, fixed.books.getValue("a").groupId)
        assertEquals("x", fixed.books.getValue("b").groupId)
    }

    @Test
    fun metadataEditsKeepOtherFields() {
        var data = ShelfLibrary.reconcile(ShelfLibrary.empty(now), listOf("a"), now)
        data = ShelfLibrary.updateBook(data, "a") { it.copy(author = "海晏山", generatedCoverStyle = 3, preferGenerated = true) }
        data = ShelfLibrary.updateBook(data, "a") { it.copy(wordCount = 6_446_000, chapterCount = 3106) }
        val meta = data.books.getValue("a")
        assertEquals("海晏山", meta.author)
        assertEquals(3, meta.generatedCoverStyle)
        assertEquals(3106, meta.chapterCount)
        assertNull(ShelfLibrary.removeBook(data, "a").books["a"])
    }

    @Test
    fun codecRoundTripsAndToleratesUnknownFields() {
        var data = ShelfLibrary.createGroup(ShelfLibrary.empty(now), "武侠", now, id = "wx").getOrThrow()
        data = ShelfLibrary.reconcile(data, listOf("a"), now)
        data = ShelfLibrary.updateBook(data, "a") { it.copy(author = "作者", customCoverPath = "/covers/a.img") }
        assertEquals(data, ShelfLibraryCodec.decode(ShelfLibraryCodec.encode(data)))
        val future = """{"version":2,"groups":[{"id":"reading","name":"正在阅读","color":"red"}],"books":{},"extra":1}"""
        assertEquals("正在阅读", ShelfLibraryCodec.decode(future)!!.groups.single().name)
        assertNull(ShelfLibraryCodec.decode("{not json"))
    }

    @Test
    fun storePersistsAcrossInstancesAndBacksUpABrokenFile() = runBlocking {
        val dir = Files.createTempDirectory("shelf").toFile()
        val file = File(dir, ShelfLibraryStore.FILE_NAME)
        val store = ShelfLibraryStore(file) { now }
        store.tryUpdate { ShelfLibrary.createGroup(it, "收藏", now, id = "fav") }.getOrThrow()
        store.update { ShelfLibrary.reconcile(it, listOf("a"), now) }
        store.tryUpdate { ShelfLibrary.moveBook(it, "a", "fav") }.getOrThrow()
        assertTrue(store.tryUpdate { ShelfLibrary.createGroup(it, "收藏", now) }.isFailure)

        val reopened = ShelfLibraryStore(file) { now }
        assertEquals("fav", reopened.data.value.books.getValue("a").groupId)
        assertEquals(listOf("正在阅读", "收藏"), reopened.data.value.groups.map { it.name })

        file.writeText("garbage")
        val recovered = ShelfLibraryStore(file) { now }
        assertEquals(listOf(ShelfLibrary.DEFAULT_GROUP_ID), recovered.data.value.groups.map { it.id })
        assertTrue(dir.listFiles()!!.any { it.name.startsWith(ShelfLibraryStore.FILE_NAME + ".broken-") })
        dir.deleteRecursively()
        Unit
    }
}

class BookFactsTest {
    @Test
    fun wordCountIgnoresWhitespace() {
        assertEquals(8L, BookFacts.wordCount("第一章 诅咒\n\n  他笑了"))
    }

    @Test
    fun formatsWordsLikeTheReference() {
        assertEquals("0字", BookFacts.formatWords(0))
        assertEquals("9999字", BookFacts.formatWords(9_999))
        assertEquals("644.6万字", BookFacts.formatWords(6_446_000))
        assertEquals("1.0万字", BookFacts.formatWords(10_000))
        assertEquals("1.2亿字", BookFacts.formatWords(120_000_000))
    }

    @Test
    fun formatsFileSizes() {
        assertEquals("未知", BookFacts.formatSize(0))
        assertEquals("800 B", BookFacts.formatSize(800))
        assertEquals("512 K", BookFacts.formatSize(512 * 1024))
        assertEquals("13.1 M", BookFacts.formatSize((13.1 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatsPercentAndFormat() {
        assertEquals("0.7", BookFacts.formatPercent(0.0072f))
        assertEquals("0.0", BookFacts.formatPercent(null))
        assertEquals("100.0", BookFacts.formatPercent(1.5f))
        assertEquals("TXT", BookFacts.formatLabel("《玩家请上车》作者：海晏山.txt", ""))
        assertEquals("EPUB", BookFacts.formatLabel("book.epub", "epub"))
        assertEquals("TXT", BookFacts.formatLabel("", ""))
        assertEquals("—", BookFacts.formatTime(0))
    }
}
