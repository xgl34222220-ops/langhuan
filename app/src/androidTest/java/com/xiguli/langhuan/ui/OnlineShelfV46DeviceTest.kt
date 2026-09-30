package com.xiguli.langhuan.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class OnlineShelfV46DeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = BookSourceV36("online-shelf-v46", "fixture", "https://example.org", contentText = "#content@text")
    private val body = "这是独立测试站点的一章正文，离线缓存只保存验证成功的当前章节。".repeat(8)
    private fun manuscript(bookUrl: String = "https://example.org/book/${UUID.randomUUID()}") = ImportedManuscript(
        "按需阅读测试", listOf(ImportedChapter("第一章", "", "https://example.org/read/7/1.html"), ImportedChapter("第二章", "", "https://example.org/read/7/2.html")),
        source.id, bookUrl,
    )
    private fun remove(id: String) {
        val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
        listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) }
    }

    @Test fun collectionStoresIndexAndIdentityWithoutBodiesAndDuplicateCollectionIsAtomic() = runBlocking {
        val projects = StoryProjectManager(context)
        val input = manuscript()
        val active = projects.activeStoryId()
        val results = listOf(async { projects.createImportedStory(input) }, async { projects.createImportedStory(input) }).awaitAll()
        val id = results.first().snapshot.novel.id
        try {
            assertEquals(id, results.last().snapshot.novel.id)
            assertEquals(active, projects.activeStoryId())
            assertEquals(source.id, projects.loadStory(id)!!.snapshot.novel.sourceId)
            assertEquals(input.sourceBookUrl, projects.loadStory(id)!!.snapshot.novel.sourceBookUrl)
            val chapters = projects.chapterDrafts(id)
            assertEquals(2, chapters.size)
            assertTrue(chapters.all { it.content.isBlank() && it.sourceUrl.isNotBlank() })
            com.xiguli.langhuan.data.ExportFormat.entries.forEach { format ->
                assertTrue("Uncached chapters must not export as a complete book", runCatching { projects.exportStory(id, format) }.isFailure)
            }
            val snapshot = projects.loadStory(id)!!.snapshot
            val backup = com.xiguli.langhuan.data.StoryExchange.exportProject(snapshot, chapters)
            val restored = com.xiguli.langhuan.data.StoryExchange.importProject(backup.bytes)
            assertEquals(snapshot.novel.sourceId, restored.snapshot.novel.sourceId)
            assertEquals(chapters.map { it.sourceUrl }, restored.chapters.map { it.sourceUrl })
        } finally { remove(id) }
    }

    @Test fun readAndOfflineRequestsShareOneFetchAndRetainOtherUncachedChapters() = runBlocking {
        val projects = StoryProjectManager(context)
        val id = projects.createImportedStory(manuscript()).snapshot.novel.id
        val previous = BookSourceStoreV36.load(context)
        BookSourceStoreV36.save(context, previous.filterNot { it.id == source.id } + source)
        try {
            val urls = projects.chapterDrafts(id).map { it.sourceUrl }.toSet()
            val calls = AtomicInteger()
            val fetch: (BookSourceV36, SourceRequestV36) -> org.jsoup.nodes.Document = { _, request ->
                calls.incrementAndGet(); Jsoup.parse("<div id=content>$body</div>", request.url)
            }
            listOf(async { OnlineChapterCacheV46.load(context, id, 1, urls, fetch) }, async { OnlineChapterCacheV46.load(context, id, 1, urls, fetch) }).awaitAll()
            assertEquals(1, calls.get())
            assertEquals(body, projects.chapterDraft(id, 1)!!.content)
            assertEquals("", projects.chapterDraft(id, 2)!!.content)
            assertEquals(body.length, projects.loadStory(id)!!.snapshot.novel.currentWords)
        } finally { BookSourceStoreV36.save(context, previous); remove(id) }
    }

    @Test fun cancelledReadDoesNotWriteAndCachedContentIsNeverOverwritten() = runBlocking {
        val projects = StoryProjectManager(context)
        val id = projects.createImportedStory(manuscript()).snapshot.novel.id
        val previous = BookSourceStoreV36.load(context)
        BookSourceStoreV36.save(context, previous + source)
        try {
            val one = projects.chapterDraft(id, 1)!!
            val result = runCatching { OnlineChapterCacheV46.load(context, id, 1, setOf(one.sourceUrl)) { _, _ -> throw CancellationException("fixture cancellation") } }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue(projects.chapterDraft(id, 1)!!.content.isBlank())
            projects.cacheOnlineChapter(id, 1, one.id, one.sourceUrl, body, source.id, projects.loadStory(id)!!.snapshot.novel.sourceBookUrl)
            projects.cacheOnlineChapter(id, 1, one.id, one.sourceUrl, "replacement", source.id, projects.loadStory(id)!!.snapshot.novel.sourceBookUrl)
            assertEquals(body, projects.chapterDraft(id, 1)!!.content)
            assertEquals(body, LanghuanDatabase.get(context).chapterVersionDao().forChapter(id, 1).first().content)
        } finally { BookSourceStoreV36.save(context, previous); remove(id) }
    }

    @Test fun localEmptyChapterNeverRequestsNetwork() = runBlocking {
        val projects = StoryProjectManager(context)
        val id = projects.createImportedStory(ImportedManuscript("本地空章", listOf(ImportedChapter("第一章", "")))).snapshot.novel.id
        try {
            var calls = 0
            assertTrue(runCatching { OnlineChapterCacheV46.load(context, id, 1, emptySet()) { _, request -> calls++; Jsoup.parse("bad", request.url) } }.isFailure)
            assertEquals(0, calls)
        } finally { remove(id) }
    }

    @Test fun reorderedDirectoryLeavesCachedTextAndChapterIdentityUntouched() = runBlocking {
        val projects = StoryProjectManager(context)
        val input = manuscript()
        val id = projects.createImportedStory(input).snapshot.novel.id
        try {
            val one = projects.chapterDraft(id, 1)!!
            projects.cacheOnlineChapter(id, 1, one.id, one.sourceUrl, body, source.id, projects.loadStory(id)!!.snapshot.novel.sourceBookUrl)
            assertTrue(runCatching { projects.appendOnlineCatalogue(id, source.id, input.sourceBookUrl, input.chapters.reversed()) }.isFailure)
            assertEquals(one.id, projects.chapterDraft(id, 1)!!.id)
            assertEquals(body, projects.chapterDraft(id, 1)!!.content)
            assertEquals(2, projects.chapterDrafts(id).size)
        } finally { remove(id) }
    }

    @Test fun lateResponseAfterBookDeletionDoesNotRecreateBook() = runBlocking {
        val projects = StoryProjectManager(context)
        val id = projects.createImportedStory(manuscript()).snapshot.novel.id
        val previous = BookSourceStoreV36.load(context)
        BookSourceStoreV36.save(context, previous + source)
        try {
            val chapter = projects.chapterDraft(id, 1)!!
            val result = runCatching { OnlineChapterCacheV46.load(context, id, 1, setOf(chapter.sourceUrl)) { _, request ->
                remove(id); Jsoup.parse("<div id=content>$body</div>", request.url)
            } }
            assertTrue(result.isFailure)
            assertNull(projects.loadStory(id))
            assertNull(projects.chapterDraft(id, 1))
        } finally { BookSourceStoreV36.save(context, previous); remove(id) }
    }

    @Test fun changedBookSourceRejectsLateResponseEvenWhenChapterUrlIsUnchanged() = runBlocking {
        val projects = StoryProjectManager(context)
        val id = projects.createImportedStory(manuscript()).snapshot.novel.id
        val previous = BookSourceStoreV36.load(context)
        BookSourceStoreV36.save(context, previous + source)
        try {
            val chapter = projects.chapterDraft(id, 1)!!
            val result = runCatching { OnlineChapterCacheV46.load(context, id, 1, setOf(chapter.sourceUrl)) { _, request ->
                runBlocking {
                    val snapshot = projects.loadStory(id)!!.snapshot
                    val changed = snapshot.copy(novel = snapshot.novel.copy(sourceId = "different-source"))
                    LanghuanDatabase.get(context).storyStateDao().updateSnapshot(id,
                        kotlinx.serialization.json.Json.encodeToString(com.xiguli.langhuan.domain.StorySnapshot.serializer(), changed), System.currentTimeMillis())
                }
                Jsoup.parse("<div id=content>$body</div>", request.url)
            } }
            assertTrue(result.isFailure)
            assertTrue(projects.chapterDraft(id, 1)!!.content.isBlank())
            assertEquals("different-source", projects.loadStory(id)!!.snapshot.novel.sourceId)
        } finally { BookSourceStoreV36.save(context, previous); remove(id) }
    }
}
