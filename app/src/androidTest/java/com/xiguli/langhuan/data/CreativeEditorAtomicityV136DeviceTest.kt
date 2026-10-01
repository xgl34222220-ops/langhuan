package com.xiguli.langhuan.data

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.local.ChapterStateDao
import com.xiguli.langhuan.data.local.ChapterStateEntity
import com.xiguli.langhuan.data.local.ChapterVersionEntity
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.local.MemoryChunkDao
import com.xiguli.langhuan.data.local.MemoryChunkEntity
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Actual Room faults and deterministic coroutine cancellation between DAO writes.
 * Reconstructed from the retained 136 test record after environment replacement.
 * No AI, key, network, Activity or user book is used.
 */
class CreativeEditorAtomicityV136DeviceTest {
    private enum class Fault(val table: String, val operations: List<String>) {
        CHAPTER_WRITE("chapter_state", listOf("INSERT", "UPDATE")),
        MEMORY_DELETE("memory_chunks", listOf("DELETE")),
        MEMORY_INSERT("memory_chunks", listOf("INSERT", "UPDATE")),
    }
    private enum class Gate { BEFORE_CHAPTER_WRITE, AFTER_MEMORY_DELETE }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val tables = listOf("story_state", "chapter_state", "memory_chunks", "chapter_versions")

    private data class Fixture(
        val context: Context,
        val db: LanghuanDatabase,
        val store: ChapterEditorStore,
        val snapshot: StorySnapshot,
        val original: ChapterDraft,
        val edited: ChapterDraft,
        val controlId: String,
    ) {
        val id: String get() = original.novelId
        val ids: List<String> get() = listOf(id, controlId)
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = LanghuanDatabase.get(context)
        val store = ChapterEditorStore(context)
        val id = "editor-atomicity-${UUID.randomUUID()}"
        val controlId = "editor-atomicity-control-${UUID.randomUUID()}"
        val original = ChapterDraft("$id:1", id, 1, "合成旧标题", "保存一致性", emptyList(),
            content = "合成旧正文\r\n林舟保留来信。☂", summary = "合成旧摘要", version = 1)
        val control = original.copy(id = "$controlId:1", novelId = controlId, content = "对照书正文不能改变。")
        fun snapshot(draft: ChapterDraft) = StorySnapshot(
            novel = Novel(draft.novelId, "保存原子性合成书", "测试", "合成设定", "保存", 10_000,
                currentWords = draft.content.length),
            activeOutline = emptyList(), bible = emptyList(), characters = emptyList(),
            recentTimeline = emptyList(), relevantForeshadowing = emptyList(), recentSummaries = emptyList(),
        )
        val originalSnapshot = snapshot(original)
        val f = Fixture(context, db, store, originalSnapshot, original,
            original.copy(title = "合成新标题", content = "合成新正文\r\n林舟重新写好回信，准备亲自送到港口书店。✉", summary = "合成新摘要"), controlId)
        try {
            store.autosave(originalSnapshot, original)
            store.autosave(snapshot(control), control)
            db.chapterVersionDao().upsert(ChapterVersionEntity("${original.id}:v1", id, 1, 1,
                original.title, original.content, original.summary, 135L))
            db.memoryChunkDao().upsert(MemoryChunkEntity("$id:original", id, "ORIGINAL_CHAPTER", original.id,
                1, "来源正文必须保留", 136L))
            db.memoryChunkDao().upsert(MemoryChunkEntity("$id:legacy-live", id, "CHAPTER", original.id,
                1, "旧的章节检索行也必须随失败回滚保留", 137L))
            block(f)
        } finally {
            val sql = db.openHelper.writableDatabase
            tables.forEach { table ->
                sql.execSQL("DELETE FROM $table WHERE novelId IN (?, ?)", arrayOf(id, controlId))
            }
        }
    }

    /** Compare every stored cell, including original JSON bytes and timestamps. */
    private fun rows(f: Fixture, ids: List<String> = f.ids): Map<String, List<List<String?>>> =
        tables.associateWith { table ->
            val sql = f.db.openHelper.readableDatabase
            val columns = sql.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            val projection = columns.joinToString(", ") { name ->
                "CASE WHEN `$name` IS NULL THEN NULL ELSE typeof(`$name`) || ':' || hex(CAST(`$name` AS BLOB)) END"
            }
            val placeholders = ids.joinToString(",") { "?" }
            sql.query("SELECT $projection FROM `$table` WHERE novelId IN ($placeholders) ORDER BY 1", ids.toTypedArray()).use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                }) }
            }
        }

    private fun assertUnchanged(label: String, before: Map<String, List<List<String?>>>, f: Fixture) {
        val after = rows(f)
        val changed = tables.filter { before[it] != after[it] }
        Log.i("EditorAtomicityV136", "$label; changed tables=$changed")
        assertEquals("$label must preserve every original cell; changed=$changed", before, after)
    }

    private suspend fun withFault(f: Fixture, fault: Fault, block: suspend () -> Unit) {
        val sql = f.db.openHelper.writableDatabase
        val prefix = "editor_fault_${UUID.randomUUID().toString().replace("-", "")}" // Synthetic SQL identifier.
        val names = fault.operations.mapIndexed { index, _ -> "${prefix}_$index" }
        try {
            fault.operations.forEachIndexed { index, operation ->
                val row = if (operation == "DELETE") "OLD" else "NEW"
                val scope = if (fault == Fault.CHAPTER_WRITE) "$row.novelId = '${f.id}'"
                    else "$row.novelId = '${f.id}' AND $row.sourceType = 'CHAPTER' AND $row.sourceId = '${f.original.id}'"
                sql.execSQL("CREATE TRIGGER ${names[index]} BEFORE $operation ON ${fault.table} " +
                    "WHEN $scope BEGIN SELECT RAISE(ABORT, 'synthetic editor ${fault.name}'); END")
            }
            block()
        } finally { names.forEach { sql.execSQL("DROP TRIGGER IF EXISTS $it") } }
    }

    private suspend fun failedSaveKeepsRows(fault: Fault, checkpoint: Boolean = false) = fixture { f ->
        val before = rows(f)
        withFault(f, fault) {
            val result = runCatching {
                if (checkpoint) f.store.checkpoint(f.snapshot, f.edited) else f.store.autosave(f.snapshot, f.edited)
            }
            val failure = requireNotNull(result.exceptionOrNull()) { "The SQLite trigger did not abort the write" }
            assertTrue("Actual trigger failure: $failure",
                generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("synthetic editor ${fault.name}") })
            assertUnchanged("${if (checkpoint) "checkpoint" else "autosave"} ${fault.name}", before, f)
        }
    }

    @Test fun autosaveChapterWriteFailureKeepsAllOriginalRows() = runBlocking { failedSaveKeepsRows(Fault.CHAPTER_WRITE) }
    @Test fun autosaveMemoryDeleteFailureKeepsAllOriginalRows() = runBlocking { failedSaveKeepsRows(Fault.MEMORY_DELETE) }
    @Test fun autosaveMemoryInsertFailureKeepsAllOriginalRows() = runBlocking { failedSaveKeepsRows(Fault.MEMORY_INSERT) }
    @Test fun checkpointMemoryInsertFailureRollsBackAllTablesAndVersions() = runBlocking { failedSaveKeepsRows(Fault.MEMORY_INSERT, checkpoint = true) }

    /** The decorator only adds a cancellable boundary; prior SQL writes and Room remain real. */
    private suspend fun cancelledSaveKeepsRows(gate: Gate, checkpoint: Boolean = false) = fixture { f ->
        val before = rows(f)
        val reached = CompletableDeferred<Unit>()
        val field = ChapterEditorStore::class.java.getDeclaredField(
            if (gate == Gate.BEFORE_CHAPTER_WRITE) "chapterStateDao" else "memoryDao",
        ).apply { isAccessible = true }
        val originalDao = field.get(f.store)
        val decorated: Any = when (gate) {
            Gate.BEFORE_CHAPTER_WRITE -> object : ChapterStateDao by (originalDao as ChapterStateDao) {
                override suspend fun upsert(entity: ChapterStateEntity) { reached.complete(Unit); awaitCancellation() }
            }
            Gate.AFTER_MEMORY_DELETE -> object : MemoryChunkDao by (originalDao as MemoryChunkDao) {
                override suspend fun upsert(item: MemoryChunkEntity) { reached.complete(Unit); awaitCancellation() }
            }
        }
        field.set(f.store, decorated)
        try {
            supervisorScope {
                val job = async {
                    if (checkpoint) f.store.checkpoint(f.snapshot, f.edited) else f.store.autosave(f.snapshot, f.edited)
                }
                job.invokeOnCompletion { failure ->
                    if (!reached.isCompleted) reached.completeExceptionally(failure ?: AssertionError("Save never reached $gate"))
                }
                try {
                    withTimeout(10_000) { reached.await() }
                    withTimeout(10_000) { job.cancelAndJoin() }
                    assertTrue(job.isCancelled)
                } finally { job.cancelAndJoin() }
            }
        } finally { field.set(f.store, originalDao) }
        assertUnchanged("${if (checkpoint) "checkpoint" else "autosave"} cancellation $gate", before, f)
    }

    @Test fun autosaveCancelledBeforeChapterWriteKeepsAllOriginalRows() = runBlocking { cancelledSaveKeepsRows(Gate.BEFORE_CHAPTER_WRITE) }
    @Test fun autosaveCancelledAfterMemoryDeleteKeepsAllOriginalRows() = runBlocking { cancelledSaveKeepsRows(Gate.AFTER_MEMORY_DELETE) }
    @Test fun checkpointCancelledAfterMemoryDeleteRollsBackAllTablesAndVersions() = runBlocking {
        cancelledSaveKeepsRows(Gate.AFTER_MEMORY_DELETE, checkpoint = true)
    }

    @Test fun successfulAutosaveUpdatesAllThreeTablesWithoutChangingVersionsOrControlBook() = runBlocking {
        fixture { f ->
            val before = rows(f)
            val controlBefore = rows(f, listOf(f.controlId))
            val originalMemory = f.db.memoryChunkDao().recent(f.id, 20).single { it.sourceType == "ORIGINAL_CHAPTER" }
            val saved = f.store.autosave(f.snapshot, f.edited)
            assertEquals(f.edited, saved.draft)
            val story = requireNotNull(f.db.storyStateDao().get(f.id))
            val chapter = requireNotNull(f.db.chapterStateDao().get(f.id, 1))
            assertEquals(f.edited, json.decodeFromString(ChapterDraft.serializer(), story.draftJson))
            assertEquals(story.draftJson, chapter.draftJson)
            val snapshot = json.decodeFromString(StorySnapshot.serializer(), story.snapshotJson)
            assertEquals(f.edited.content.length, snapshot.novel.currentWords)
            val memory = f.db.memoryChunkDao().recent(f.id, 20)
            assertEquals(originalMemory, memory.single { it.sourceType == "ORIGINAL_CHAPTER" })
            val live = memory.single { it.sourceType == "CHAPTER" && it.sourceId == f.edited.id }
            assertEquals("chapter:${f.edited.id}:live", live.id)
            assertTrue(live.text.endsWith(f.edited.content))
            assertEquals(before.getValue("chapter_versions"), rows(f).getValue("chapter_versions"))
            assertEquals(controlBefore, rows(f, listOf(f.controlId)))
        }
    }
}
