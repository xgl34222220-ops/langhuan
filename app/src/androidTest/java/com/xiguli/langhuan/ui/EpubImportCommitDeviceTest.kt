package com.xiguli.langhuan.ui

import android.app.Application
import android.app.AlertDialog
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.ui.epub.EpubReaderActivity
import com.xiguli.langhuan.ui.epub.EpubReaderEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
    @Test fun cancellingAfterEpubPreparationLeavesShelfMetadataAndOriginalFilesUnchanged() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val manager = StoryProjectManager(app)
        val originals = File(app.filesDir, "epub_originals_v1")
        val before = originalsSnapshot(originals)
        val shelves = manager.observeStories().first()
        val active = manager.activeStoryId()
        val metadata = app.getSharedPreferences("local_book_meta_v1", 0).all.toMap()
        val bytes = instrumentation.context.assets.open("epub/original-reflow.epub").use { it.readBytes() }
        val prepared = CompletableDeferred<Unit>()
        val vm = LocalBookImportViewModelV1(app)
        try {
            instrumentation.runOnMainSync {
                assertTrue(vm.importDocument({ "synthetic-cancel.epub" }, { bytes.inputStream() }, afterPreparation = {
                    prepared.complete(Unit)
                    awaitCancellation()
                }))
            }
            withTimeout(30_000) { prepared.await() }
            assertTrue(originals.listFiles().orEmpty().any { it.name.startsWith("pending-import-") })
            assertEquals(shelves, manager.observeStories().first())
            instrumentation.runOnMainSync { vm.cancelImport() }
            val cancelled = withTimeout(15_000) { vm.state.first { !it.busy } }
            withTimeout(10_000) { while (originalsSnapshot(originals) != before) delay(20) }
            assertEquals("已取消导入", cancelled.message)
            assertNull(cancelled.importedBookId); assertNull(cancelled.error)
            assertEquals(shelves, manager.observeStories().first())
            assertEquals(active, manager.activeStoryId())
            assertEquals(metadata, app.getSharedPreferences("local_book_meta_v1", 0).all)
        } finally { instrumentation.runOnMainSync { vm.viewModelScope.cancel() } }
    }

    @Test fun successfulEpubImportPublishesCanonicalOriginalBeforeReleasingTemporaryFiles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val manager = StoryProjectManager(app)
        val originals = File(app.filesDir, "epub_originals_v1")
        val beforeNames = originals.listFiles().orEmpty().map { it.name }.toSet()
        val active = manager.activeStoryId()
        val bytes = instrumentation.context.assets.open("epub/original-reflow.epub").use { it.readBytes() }
        val prepared = CompletableDeferred<Unit>(); val proceed = CompletableDeferred<Unit>()
        val vm = LocalBookImportViewModelV1(app)
        var importedId: String? = null
        try {
            instrumentation.runOnMainSync {
                assertTrue(vm.importDocument({ "synthetic-confirm.epub" }, { bytes.inputStream() }, afterPreparation = {
                    prepared.complete(Unit); proceed.await()
                }))
            }
            withTimeout(30_000) { prepared.await() }
            val pending = originals.listFiles().orEmpty().filter { it.name !in beforeNames }
            assertEquals(1, pending.size); assertTrue(pending.single().name.startsWith("pending-import-"))
            proceed.complete(Unit)
            val result = withTimeout(30_000) { vm.state.first { !it.busy } }
            assertNull(result.error)
            importedId = requireNotNull(result.importedBookId)
            withTimeout(10_000) { while (pending.any { it.exists() }) delay(20) }
            val store = EpubOriginalStore(originals)
            assertArrayEquals(bytes, store.original(requireNotNull(importedId))!!.readBytes())
            assertEquals(originals.canonicalFile, store.open(requireNotNull(importedId)).rendering.parentFile.canonicalFile)
            assertNotNull(manager.loadStory(requireNotNull(importedId)))
            assertTrue(manager.chapterDrafts(requireNotNull(importedId)).any { it.content.isNotBlank() })
            assertEquals(active, manager.activeStoryId())
        } finally {
            instrumentation.runOnMainSync { vm.viewModelScope.cancel() }
            importedId?.let { id ->
                val sql = LanghuanDatabase.get(app).openHelper.writableDatabase
                listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                    sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
                }
            }
            // Test-only directory: remove only names this controlled test added, never prior files.
            originals.listFiles().orEmpty().filter { it.name !in beforeNames }.forEach { it.deleteRecursively() }
        }
    }

    @Test fun backingOutOfAssociationDialogCleansStagingAndLeavesExistingFilesUntouched(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val originals = File(app.filesDir, "epub_originals_v1")
        val before = originalsSnapshot(originals)
        val input = File(app.cacheDir, "synthetic-association-${UUID.randomUUID()}.epub")
        instrumentation.context.assets.open("epub/original-fixed.epub").use { source -> input.outputStream().use { source.copyTo(it) } }
        try {
            ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(app, "unassociated-staging-test")).use { scenario ->
                scenario.onActivity { activity ->
                    val method = EpubReaderActivity::class.java.getDeclaredMethod("prepareAssociation", Uri::class.java)
                    method.isAccessible = true; method.invoke(activity, Uri.fromFile(input))
                }
                var dialog: AlertDialog? = null
                withTimeout(30_000) {
                    while (dialog?.isShowing != true) {
                        scenario.onActivity { activity ->
                            val field = EpubReaderActivity::class.java.getDeclaredField("associationDialog")
                            field.isAccessible = true; dialog = field.get(activity) as? AlertDialog
                        }
                        delay(20)
                    }
                }
                assertTrue(originals.listFiles().orEmpty().any { it.name.startsWith("pending-import-") })
                instrumentation.runOnMainSync { requireNotNull(dialog).onBackPressed() }
                withTimeout(10_000) { while (originalsSnapshot(originals) != before) delay(20) }
                assertFalse(requireNotNull(dialog).isShowing)
                assertFalse(EpubOriginalStore(originals).hasOriginal("unassociated-staging-test"))
                scenario.onActivity { assertFalse(it.isFinishing) }
            }
        } finally { input.delete() }
    }

    private fun originalsSnapshot(root: File): Map<String, String> = root.walkTopDown().filter { it.isFile }
        .associate { file -> file.relativeTo(root).path to java.security.MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) } }

}
