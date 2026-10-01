package com.xiguli.langhuan.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.EpubImporterV2
import com.xiguli.langhuan.data.NewStoryRequest
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.epub.EpubOriginalStore
import com.xiguli.langhuan.data.local.LanghuanDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual Room transaction boundary: a failed original-file association cannot leave a shelf row. */
class EpubImportCommitDeviceTest {
    @Test fun associationFailureRollsBackNewBookAndKeepsExistingBookAndSelection() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val manager = StoryProjectManager(app)
        val previousActive = manager.activeStoryId()
        val old = manager.createStory(NewStoryRequest("EPUB 原文件事务测试", "测试", "保留已有书籍", "事务", 10000))
        val oldId = old.snapshot.novel.id
        val originals = File(app.cacheDir, "epub-commit-fixture-${UUID.randomUUID()}")
        val store = EpubOriginalStore(originals)
        val bytes = instrumentation.context.assets.open("epub/original-reflow.epub").use { it.readBytes() }
        val prepared = store.prepare(bytes.inputStream())
        val manuscript = EpubImporterV2.import("original.epub", bytes).manuscript
        val before = manager.observeStories().first()
        val chaptersBefore = manager.chapterDrafts(oldId)
        var failedId: String? = null
        var successId: String? = null
        try {
            var thrown = false
            try {
                manager.createImportedStory(manuscript) { created ->
                    failedId = created.snapshot.novel.id
                    store.associate(requireNotNull(failedId), prepared)
                    throw IllegalStateException("synthetic association checkpoint")
                }
            } catch (error: IllegalStateException) {
                assertEquals("synthetic association checkpoint", error.message)
                thrown = true
            }
            assertTrue(thrown)
            assertNotNull(failedId)
            assertNull(manager.loadStory(requireNotNull(failedId)))
            assertTrue(manager.chapterDrafts(requireNotNull(failedId)).isEmpty())
            assertEquals(before, manager.observeStories().first())
            assertEquals(chaptersBefore, manager.chapterDrafts(oldId))
            assertEquals(oldId, manager.activeStoryId())
            val created = manager.createImportedStory(manuscript) { next -> store.associate(next.snapshot.novel.id, prepared) }
            successId = created.snapshot.novel.id
            assertTrue(store.hasOriginal(requireNotNull(successId)))
            assertTrue(manager.chapterDrafts(requireNotNull(successId)).any { it.content.isNotBlank() })
            assertEquals(oldId, manager.activeStoryId())
        } finally {
            val sql = LanghuanDatabase.get(app).openHelper.writableDatabase
            listOfNotNull(oldId, failedId, successId).forEach { id ->
                listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                    sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
                }
            }
            if (previousActive == null) manager.clearActiveStoryId() else manager.setActiveStoryId(previousActive)
            originals.deleteRecursively()
        }
    }
}
