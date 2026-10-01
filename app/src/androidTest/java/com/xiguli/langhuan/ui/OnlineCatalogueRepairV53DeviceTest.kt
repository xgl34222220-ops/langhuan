package com.xiguli.langhuan.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.*
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class OnlineCatalogueRepairV53DeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun item(n: Int) = ImportedChapter("第${n}章 原创测试", "", "https://catalogue.example/txt/7/$n.html")
    private fun remove(id: String) {
        val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
        listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) }
    }
    @Test fun repairOldThirtySixRowsPreservesCachedChapterKeysProgressBookmarksAndBackup() = runBlocking {
        val projects = StoryProjectManager(context)
        val url = "https://catalogue.example/book/${UUID.randomUUID()}"
        val oldNumbers = (1..15).toList() + (985..1005).toList()
        val id = projects.createImportedStory(ImportedManuscript("旧目录修复", oldNumbers.map(::item), "fixture", url)).snapshot.novel.id
        val prefs = context.getSharedPreferences("reader_qingmo_v9", 0)
        try {
            val tail = projects.chapterDraft(id,16)!!
            val text = "已缓存的第985章正文，原始阅读位置应始终留在这里。".repeat(30)
            projects.cacheOnlineChapter(id,16,tail.id,tail.sourceUrl,text,"fixture",url)
            projects.selectChapter(id,16)
            ReaderProgressStoreV11.save(context,id,ReaderProgressV11(chapterNumber=16,textOffset=127,bodyVersion=48))
            ReaderBookmarkStoreV49.setMarked(prefs,id,16,true).getOrThrow()
            val versions = LanghuanDatabase.get(context).chapterVersionDao().forChapter(id,16)
            val active = projects.activeStoryId()
            assertEquals(969, projects.appendOnlineCatalogue(id,"fixture",url,(1..1005).map(::item)))
            val chapters = projects.chapterDrafts(id)
            assertEquals(1005,chapters.size)
            assertEquals((1..1005).map { item(it).sourceUrl },chapters.map { it.sourceUrl })
            val retained = chapters[984]
            assertEquals(tail.id,retained.id)
            assertEquals(16,retained.chapterNumber)
            assertEquals(985,retained.readingOrder)
            assertEquals(text,retained.content)
            assertEquals(16,projects.loadStory(id)!!.draft.chapterNumber)
            assertEquals(127,ReaderProgressStoreV11.load(context,id,1).textOffset)
            assertTrue("16" in ReaderBookmarkStoreV49.load(prefs,id).getOrThrow())
            assertEquals(versions,LanghuanDatabase.get(context).chapterVersionDao().forChapter(id,16))
            assertEquals(active,projects.activeStoryId())
            val backup = StoryExchange.importProject(StoryExchange.exportProject(projects.loadStory(id)!!.snapshot,chapters).bytes)
            assertEquals(chapters,backup.chapters)
            assertEquals(0,projects.appendOnlineCatalogue(id,"fixture",url,(1..1005).map(::item)))
            assertTrue(chapters.filter { it.id != retained.id }.all { it.content.isBlank() })
        } finally { remove(id) }
    }
    @Test fun failureAndCancellationAfterAllWritesRollBackOriginalCatalogueAndCurrentDraft() = runBlocking {
        val projects = StoryProjectManager(context)
        val url = "https://catalogue.example/book/${UUID.randomUUID()}"
        val id = projects.createImportedStory(ImportedManuscript("原子修复",listOf(item(1),item(20)),"fixture",url)).snapshot.novel.id
        try {
            val before = projects.chapterDrafts(id)
            val current = projects.loadStory(id)!!
            listOf(IllegalStateException("disk fixture"),CancellationException("cancel fixture")).forEach { failure ->
                val result = runCatching { projects.appendOnlineCatalogue(id,"fixture",url,(1..20).map(::item)) { throw failure } }
                assertSame(failure,result.exceptionOrNull())
                assertEquals(before,projects.chapterDrafts(id))
                assertEquals(current,projects.loadStory(id))
            }
        } finally { remove(id) }
    }
    @Test fun sourceChangedOrOldUrlsMissingNeverReplacesOriginalDirectory() = runBlocking {
        val projects = StoryProjectManager(context)
        val url = "https://catalogue.example/book/${UUID.randomUUID()}"
        val id = projects.createImportedStory(ImportedManuscript("来源保护",listOf(item(1),item(20)),"fixture",url)).snapshot.novel.id
        try {
            val before=projects.chapterDrafts(id)
            assertTrue(runCatching { projects.appendOnlineCatalogue(id,"other-source",url,(1..20).map(::item)) }.isFailure)
            assertTrue(runCatching { projects.appendOnlineCatalogue(id,"fixture",url,(1..19).map(::item)) }.isFailure)
            assertEquals(before,projects.chapterDrafts(id))
        } finally { remove(id) }
    }
    @Test fun deleteLastAfterRepairUsesReadingOrderWithoutRenumberingOtherKeys() = runBlocking {
        val projects = StoryProjectManager(context)
        val url = "https://catalogue.example/book/${UUID.randomUUID()}"
        val id = projects.createImportedStory(ImportedManuscript("末章测试",listOf(item(1),item(20)),"fixture",url)).snapshot.novel.id
        try {
            projects.appendOnlineCatalogue(id,"fixture",url,(1..20).map(::item))
            val before=projects.chapterDrafts(id)
            assertEquals(2,before.last().chapterNumber)
            projects.selectChapter(id,2)
            projects.deleteLastChapter(id)
            assertEquals(before.dropLast(1),projects.chapterDrafts(id))
            assertEquals(before[18].chapterNumber,projects.loadStory(id)!!.snapshot.novel.currentChapter)
            assertNull(projects.chapterDraft(id,2))
        } finally { remove(id) }
    }

}
