package com.xiguli.langhuan.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ReaderStorageProtectionV48DeviceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "storage-fixture-${UUID.randomUUID()}-"
    private val files = File(base.cacheDir, prefix).apply { mkdirs() }
    private val context = object : ContextWrapper(base) {
        override fun getFilesDir() = files
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix + name, mode)
    }
    private val source = BookSourceV36("kept", "Existing fixture", "https://example.org")
    @After fun cleanup() { files.deleteRecursively(); listOf("book_sources_v36", "reader_progress_v2", "reader_progress_v1").forEach { base.deleteSharedPreferences(prefix + it) } }

    @Test fun corruptSourceEntryCannotBeReplacedByOrdinarySave() {
        BookSourceStoreV36.save(context, listOf(source))
        val prefs = context.getSharedPreferences("book_sources_v36", 0)
        val corrupt = prefs.getString("sources", null)!!.dropLast(1) + ",{\"id\":\"broken\"}]"
        prefs.edit().putString("sources", corrupt).commit()
        assertTrue(BookSourceStoreV36.read(context).isFailure)
        assertTrue(runCatching { BookSourceStoreV36.load(context) }.isFailure)
        assertTrue(runCatching { BookSourceStoreV36.save(context, emptyList()) }.isFailure)
        assertEquals(corrupt, prefs.getString("sources", null))
        assertEquals(corrupt, BookSourceStoreV36.raw(context))
    }
    @Test fun syntacticallyValidDuplicateOrBlankSourceIdsStayProtected() {
        val prefs = context.getSharedPreferences("book_sources_v36", 0)
        val cases = listOf(
            "[{\"id\":\"same\",\"name\":\"A\",\"baseUrl\":\"https://a.example\"},{\"id\":\"same\",\"name\":\"B\",\"baseUrl\":\"https://b.example\"}]",
            "[{\"id\":\"\",\"name\":\"A\",\"baseUrl\":\"https://a.example\"}]",
        )
        cases.forEach { original ->
            prefs.edit().putString("sources", original).commit()
            assertTrue(BookSourceStoreV36.read(context).isFailure)
            assertTrue(runCatching { BookSourceStoreV36.save(context, listOf(source)) }.isFailure)
            assertEquals(original, BookSourceStoreV36.raw(context))
        }
    }
    @Test fun staleSourceEditorCannotOverwriteNewerConfiguration() {
        BookSourceStoreV36.save(context, listOf(source))
        val updated = source.copy(name = "New name")
        BookSourceStoreV36.save(context, listOf(updated), listOf(source))
        assertTrue(runCatching { BookSourceStoreV36.save(context, emptyList(), listOf(source)) }.isFailure)
        assertEquals(listOf(updated), BookSourceStoreV36.load(context))
    }
    @Test fun failedPreferenceCommitRestoresThePriorInMemoryValue() {
        BookSourceStoreV36.save(context, listOf(source))
        val failing = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val real = context.getSharedPreferences(name, mode)
                return object : SharedPreferences by real {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = real.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                            override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                            override fun commit(): Boolean { editor.apply(); return false }
                        }
                    }
                }
            }
        }
        assertTrue(runCatching { BookSourceStoreV36.save(failing, emptyList()) }.isFailure)
        assertEquals(listOf(source), BookSourceStoreV36.load(context))
    }
    @Test fun corruptLegacyNotesStayByteForByteIntactWhenAddingBookmarkOrNote() {
        ReaderReadingStoreV11.save(context, ReaderReadingArchiveV11("book", annotations = listOf(ReaderAnnotationV11("keep", 1, note = "Original fixture note"))))
        val file = File(files, "reader_notes_v1/book.json")
        file.appendText(",broken")
        val before = file.readBytes()
        assertNotNull(ReaderReadingStoreV11.load(context, "book").storageError)
        assertNotNull(ReaderReadingStoreV11.addBookmark(context, "book", 2, 0, 0, "Bookmark", "Fixture").storageError)
        assertNotNull(ReaderReadingStoreV11.addAnnotation(context, "book", 2, 0, 0, "Quote", "New note").storageError)
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun validLegacyArchiveRemainsReadableAndDoesNotPersistErrorStatus() {
        val saved = ReaderReadingStoreV11.addAnnotation(context, "book", 1, 0, 0, "Quote", "Fixture note")
        assertNull(saved.storageError)
        ReaderReadingStoreV11.addBookmark(context, "book", 1, 0, 0, "Bookmark", "Fixture")
        val read = ReaderReadingStoreV11.load(context, "book")
        assertEquals(1, read.annotations.size)
        assertEquals(1, read.bookmarks.size)
        assertFalse(File(files, "reader_notes_v1/book.json").readText().contains("storageError"))
    }
    @Test fun wrongBookArchiveCannotBeOverwrittenAndOldProgressGetsExplicitVersion() {
        ReaderReadingStoreV11.save(context, ReaderReadingArchiveV11("other"))
        File(files, "reader_notes_v1/other.json").copyTo(File(files, "reader_notes_v1/book.json"))
        val file = File(files, "reader_notes_v1/book.json"); val before = file.readBytes()
        assertNotNull(ReaderReadingStoreV11.save(context, ReaderReadingArchiveV11("book")).storageError)
        assertArrayEquals(before, file.readBytes())
        assertEquals(0, ReaderProgressStoreV11.load(context, "book", 1).bodyVersion)
        ReaderProgressStoreV11.save(context, "book", ReaderProgressV11(1, textOffset = 20))
        assertEquals(48, ReaderProgressStoreV11.load(context, "book", 1).bodyVersion)
    }
    @Test fun staleViewModelRefreshesTheNewerSourcesSoAnExplicitRetryCanSucceed() {
        val app = base.applicationContext as Application
        val prior = BookSourceStoreV36.load(app)
        var vm: OnlineBooksViewModelV36? = null
        try {
            BookSourceStoreV36.save(app, listOf(source))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = OnlineBooksViewModelV36(app, SavedStateHandle()) }
            val changed = source.copy(name = "Newer fixture")
            BookSourceStoreV36.save(app, listOf(changed))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm!!.toggleSource(source.id) }
            assertNotNull(vm!!.state.value.error)
            assertEquals(listOf(changed), vm!!.state.value.sources)
            assertEquals(listOf(changed), BookSourceStoreV36.load(app))
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm!!.toggleSource(source.id) }
            assertEquals(listOf(changed.copy(enabled = !changed.enabled)), BookSourceStoreV36.load(app))
        } finally {
            vm?.let { model -> InstrumentationRegistry.getInstrumentation().runOnMainSync { model.viewModelScope.cancel() } }
            BookSourceStoreV36.save(app, prior)
        }
    }

}
