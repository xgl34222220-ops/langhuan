package com.xiguli.langhuan.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

/** Real Room writes, including SQLite abort and cancellation after writes have started. */
class ProjectRestoreAtomicityV54DeviceTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "restore-test-${UUID.randomUUID()}-"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences(prefix + name, mode)
    }

    private suspend fun fixture(block: suspend (Context, LanghuanDatabase, StoryProjectManager, StoryProjectBackup) -> Unit) {
        val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val db = Room.inMemoryDatabaseBuilder(context, LanghuanDatabase::class.java).build()
        try {
            val projects = StoryProjectManager(context, db)
            val old = projects.createImportedStory(ImportedManuscript("原书完整保留", listOf(
                ImportedChapter("第一章", "原书正文一"), ImportedChapter("第三章", "原书正文三"))))
            projects.setActiveStoryId(old.snapshot.novel.id)
            val loaded = projects.loadStory(old.snapshot.novel.id)!!
            val backup = StoryExchange.importProject(StoryExchange.exportProject(loaded.snapshot,
                projects.chapterDrafts(old.snapshot.novel.id)).bytes)
            block(context, db, projects, backup)
        } finally { db.close() }
    }

    private fun rows(db: LanghuanDatabase): Map<String, List<List<String?>>> =
        listOf("story_state", "chapter_state", "chapter_versions", "memory_chunks").associateWith { table ->
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                }) }
            }
        }

    @Test fun checkpointFailuresLeaveEveryExistingRowAndSelectionUnchanged() = runBlocking {
        fixture { context, db, projects, backup ->
            val before = rows(db)
            val selection = projects.activeStorySelectionV51()
            for (stage in ProjectRestoreStage.entries) {
                val failure = IOException("synthetic checkpoint $stage")
                val manager = ProjectBackupManager(context, db) { if (it.stage == stage) throw failure }
                val result = runCatching { manager.restore(backup) }
                assertSame("failure at $stage", failure, result.exceptionOrNull())
                assertEquals("all four tables at $stage", before, rows(db))
                assertEquals(selection, projects.activeStorySelectionV51())
            }
        }
    }

    @Test fun cancellingSuspendedRestoreRollsBackEveryStageWithoutPublishing() = runBlocking {
        fixture { context, db, projects, backup ->
            val before = rows(db)
            val selection = projects.activeStorySelectionV51()
            for (stage in ProjectRestoreStage.entries) {
                supervisorScope {
                    val reached = CompletableDeferred<Unit>()
                    val wait = CompletableDeferred<Unit>()
                    val manager = ProjectBackupManager(context, db) {
                        if (it.stage == stage) { reached.complete(Unit); wait.await() }
                    }
                    val job = async { manager.restore(backup) }
                    withTimeout(10_000) { reached.await() }
                    job.cancelAndJoin()
                    assertTrue(job.isCancelled)
                }
                assertEquals("cancelled $stage", before, rows(db))
                assertEquals(selection, projects.activeStorySelectionV51())
            }
        }
    }

    @Test fun realSqliteWriteFailureCannotLeaveOrphanChapterRows() = runBlocking {
        fixture { context, db, projects, backup ->
            val before = rows(db)
            val selection = projects.activeStorySelectionV51()
            db.openHelper.writableDatabase.execSQL("""CREATE TEMP TRIGGER fail_restore_version
                BEFORE INSERT ON chapter_versions BEGIN SELECT RAISE(ABORT, 'synthetic SQLite failure'); END""")
            try {
                assertTrue(runCatching { ProjectBackupManager(context, db).restore(backup) }.isFailure)
                assertEquals(before, rows(db))
                assertEquals(selection, projects.activeStorySelectionV51())
            } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_restore_version") }
        }
    }

    @Test fun successfulRestoreIsCompleteAndPreservesRepairedReadingOrderAndOriginalData() = runBlocking {
        fixture { context, db, projects, backup ->
            val before = rows(db)
            val selection = projects.activeStorySelectionV51()
            val reordered = backup.copy(chapters=backup.chapters.map {
                it.copy(readingOrder=3-it.chapterNumber, sourceUrl="https://books.example/chapter/${it.chapterNumber}")
            })
            val restored = ProjectBackupManager(context, db).restore(reordered)
            val id = restored.snapshot.novel.id
            assertNotEquals(backup.snapshot.novel.id, id)
            val chapters = projects.chapterDrafts(id)
            assertEquals(listOf(2,1), chapters.map { it.chapterNumber })
            assertEquals(listOf(1,2), chapters.map { it.readingOrder })
            assertEquals(reordered.chapters.associate { it.chapterNumber to it.content }, chapters.associate { it.chapterNumber to it.content })
            assertTrue(chapters.all { it.novelId==id && it.id !in backup.chapters.map { old -> old.id } })
            assertEquals(2,db.chapterVersionDao().allForNovel(id).size)
            assertEquals(selection,projects.activeStorySelectionV51())
            val after = rows(db)
            before.forEach { (table, records) -> assertTrue("original rows in $table", after.getValue(table).containsAll(records)) }
            val roundTrip = StoryExchange.importProject(StoryExchange.exportProject(restored.snapshot,chapters).bytes)
            assertEquals(chapters,roundTrip.chapters)
        }
    }

    @Test fun restoreDoesNotOverrideANewerSelectionIncludingAwayAndBack() = runBlocking {
        fixture { context, db, projects, backup ->
            val original=projects.activeStorySelectionV51()
            val restored=ProjectBackupManager(context,db) {
                if (it.stage==ProjectRestoreStage.STRUCTURE_SAVED) {
                    projects.setActiveStoryId("newer-user-choice")
                    projects.setActiveStoryId(requireNotNull(original.id))
                }
            }.restore(backup)
            assertFalse(projects.publishRestoredStory(original,restored.snapshot.novel.id).getOrThrow())
            assertEquals(original.id,projects.activeStoryId())
            val current=projects.activeStorySelectionV51()
            assertTrue(projects.publishRestoredStory(current,restored.snapshot.novel.id).getOrThrow())
            assertEquals(restored.snapshot.novel.id,projects.activeStoryId())
            assertNotNull(projects.loadStory(restored.snapshot.novel.id))
        }
    }

    @Test fun readingImportNeverPublishesATemporaryActiveProjectEvenWhenItsCommitFails() = runBlocking {
        fixture { _, _, projects, _ ->
            val before=projects.activeStorySelectionV51()
            val failure=IOException("synthetic import association failure")
            val result=runCatching {
                projects.createImportedStory(ImportedManuscript("不会切换创作",listOf(ImportedChapter("一", "正文")))) {
                    assertEquals(before,projects.activeStorySelectionV51())
                    projects.setActiveStoryId("newer-user-choice")
                    throw failure
                }
            }
            assertSame(failure,result.exceptionOrNull())
            assertEquals("newer-user-choice",projects.activeStoryId())
        }
    }
    @Test fun failedSelectionCommitKeepsOldSelectionAndTheCompletelyRestoredBook() = runBlocking {
        fixture { context, db, projects, backup ->
            val before=projects.activeStorySelectionV51()
            val restored=ProjectBackupManager(context,db).restore(backup)
            val failing=object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
                    val real=context.getSharedPreferences(name,mode)
                    return object : SharedPreferences by real {
                        override fun edit():SharedPreferences.Editor {
                            val editor=real.edit()
                            return object : SharedPreferences.Editor by editor {
                                override fun putString(key:String?,value:String?):SharedPreferences.Editor { editor.putString(key,value);return this }
                                override fun remove(key:String?):SharedPreferences.Editor { editor.remove(key);return this }
                                override fun commit():Boolean { editor.apply();return false }
                            }
                        }
                    }
                }
            }
            val result=StoryProjectManager(failing,db).publishRestoredStory(before,restored.snapshot.novel.id)
            assertTrue(result.isFailure)
            assertEquals(before,projects.activeStorySelectionV51())
            assertEquals(2,projects.chapterDrafts(restored.snapshot.novel.id).size)
            assertNotNull(projects.loadStory(restored.snapshot.novel.id))
        }
    }

    @Test fun invalidBackupIsRejectedBeforeAnyTableOrSelectionChanges() = runBlocking {
        fixture { context,db,projects,backup ->
            val before=rows(db)
            val selection=projects.activeStorySelectionV51()
            for (invalid in listOf(backup.copy(formatVersion=99),backup.copy(chapters=emptyList()),
                backup.copy(chapters=backup.chapters+backup.chapters.first()))) {
                assertTrue(runCatching { ProjectBackupManager(context,db).restore(invalid) }.isFailure)
                assertEquals(before,rows(db))
                assertEquals(selection,projects.activeStorySelectionV51())
            }
        }
    }

}
