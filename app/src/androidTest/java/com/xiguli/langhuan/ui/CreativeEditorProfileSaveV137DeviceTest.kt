package com.xiguli.langhuan.ui

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** A profile-only SQLite failure must not leave a partially saved manual edit.
 * Uses the real editor ViewModel and Room; no UI error consumer, AI or private data.
 * Prepared independently of the frozen 136 candidate; not executed on a device yet.
 */
class CreativeEditorProfileSaveV137DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val json = Json { ignoreUnknownKeys = true }
    private val tables = listOf("story_state", "chapter_state", "memory_chunks", "chapter_versions")

    @Test fun failedProfileSaveKeepsWholeAutosaveAndRetryWordTotalConsistent() = runBlocking {
        exercise(checkpoint = false)
    }

    @Test fun failedProfileSaveKeepsCheckpointHistoryAndRetryWordTotalConsistent() = runBlocking {
        exercise(checkpoint = true)
    }

    private fun rows(db: LanghuanDatabase, id: String): Map<String, List<List<String?>>> =
        tables.associateWith { table ->
            val sql = db.openHelper.readableDatabase
            val columns = sql.query("PRAGMA table_info(`$table`)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            val projection = columns.joinToString(", ") { name ->
                "CASE WHEN `$name` IS NULL THEN NULL ELSE typeof(`$name`) || ':' || hex(CAST(`$name` AS BLOB)) END"
            }
            sql.query("SELECT $projection FROM `$table` WHERE novelId = ? ORDER BY 1", arrayOf(id)).use { cursor ->
                buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                    if (cursor.isNull(it)) null else cursor.getString(it)
                }) }
            }
        }

    private suspend fun exercise(checkpoint: Boolean) {
        val context = rule.activity.applicationContext
        val projects = StoryProjectManager(context)
        val activeBefore = projects.activeStoryId()
        val initial = "甲".repeat(120)
        val edited = initial + "乙".repeat(120)
        val imported = projects.createImportedStory(ImportedManuscript("合成偏好保存 ${UUID.randomUUID()}",
            listOf(ImportedChapter("第一章 合成正文", initial))))
        val id = imported.snapshot.novel.id
        val db = LanghuanDatabase.get(context)
        val sql = db.openHelper.writableDatabase
        val prefix = "profile_save_${UUID.randomUUID().toString().replace("-", "")}" // Generated identifier only.
        val triggers = listOf("${prefix}_insert", "${prefix}_update")
        val closed = AtomicInteger()
        lateinit var vm: ChapterEditorViewModel
        try {
            rule.runOnUiThread {
                vm = ViewModelProvider(rule.activity)[ChapterEditorViewModel::class.java]
                vm.load(id, 1)
            }
            rule.waitUntil(15_000) { vm.state.value.ready }
            assertEquals(120, vm.state.value.snapshot!!.novel.currentWords)
            assertTrue(vm.state.value.snapshot!!.longForm.authorProfile.enabled)
            assertEquals(0, vm.state.value.snapshot!!.longForm.authorProfile.manualEditBatches)
            val before = rows(db, id)
            val beforeSnapshot = vm.state.value.snapshot!!
            val beforeVersions = db.chapterVersionDao().forChapter(id, 1)
            val beforeVersion = vm.state.value.draft!!.version
            listOf("INSERT", "UPDATE").forEachIndexed { index, operation ->
                sql.execSQL("CREATE TRIGGER ${triggers[index]} BEFORE $operation ON story_state " +
                    "WHEN NEW.novelId = '$id' AND instr(NEW.snapshotJson, '\"manualEditBatches\":1') > 0 " +
                    "BEGIN SELECT RAISE(ABORT, 'synthetic profile-only save failure'); END")
            }
            rule.runOnUiThread {
                vm.updateContent(edited)
                if (checkpoint) vm.saveCheckpoint() else vm.flushAndClose { closed.incrementAndGet() }
            }
            rule.waitUntil(15_000) {
                !vm.state.value.isSaving && vm.state.value.dirty &&
                    vm.state.value.error.orEmpty().contains("synthetic profile-only save failure")
            }
            assertEquals(edited, vm.state.value.draft!!.content)
            assertEquals(0, closed.get())
            assertEquals(beforeSnapshot, vm.state.value.snapshot)
            assertEquals(beforeVersion, vm.state.value.draft!!.version)
            val failedStory = requireNotNull(db.storyStateDao().get(id))
            val failedSnapshot = json.decodeFromString(StorySnapshot.serializer(), failedStory.snapshotJson)
            val failedDraft = json.decodeFromString(ChapterDraft.serializer(), failedStory.draftJson)
            Log.i("EditorProfileV137", "checkpoint=$checkpoint; visibleChars=${vm.state.value.draft!!.content.length}; " +
                "visibleWords=${vm.state.value.snapshot!!.novel.currentWords}; durableChars=${failedDraft.content.length}; " +
                "durableWords=${failedSnapshot.novel.currentWords}; durableVersion=${failedDraft.version}; " +
                "profileBatches=${failedSnapshot.longForm.authorProfile.manualEditBatches}; closeCount=${closed.get()}")
            // Direct DAO/SQL reads avoid any load/select method that may itself persist normalization.
            assertEquals("A failed profile write must preserve the entire prior save", before, rows(db, id))
            triggers.forEach { sql.execSQL("DROP TRIGGER $it") }
            rule.runOnUiThread {
                if (checkpoint) vm.saveCheckpoint() else vm.flushAndClose { closed.incrementAndGet() }
            }
            rule.waitUntil(15_000) {
                !vm.state.value.isSaving && !vm.state.value.dirty &&
                    (checkpoint || closed.get() == 1)
            }
            val story = requireNotNull(db.storyStateDao().get(id))
            val snapshot = json.decodeFromString(StorySnapshot.serializer(), story.snapshotJson)
            val draft = json.decodeFromString(ChapterDraft.serializer(), story.draftJson)
            val chapter = requireNotNull(db.chapterStateDao().get(id, 1))
            assertEquals(story.draftJson, chapter.draftJson)
            assertEquals(edited, draft.content)
            assertEquals(240, snapshot.novel.currentWords)
            assertEquals(1, snapshot.longForm.authorProfile.manualEditBatches)
            val signals = snapshot.longForm.authorProfile.recentSignals
            assertEquals(1, signals.size)
            assertEquals(edited.length - initial.length, signals.single().deltaChars)
            val versions = db.chapterVersionDao().forChapter(id, 1)
            assertEquals(beforeVersions.size + if (checkpoint) 1 else 0, versions.size)
            assertEquals(beforeVersions, versions.filter { it.version <= beforeVersion })
            if (checkpoint) {
                assertEquals(edited, versions.first().content)
                assertEquals(beforeVersion + 1, versions.first().version)
            }
            assertEquals(if (checkpoint) beforeVersion + 1 else beforeVersion, draft.version)
            assertEquals(snapshot, vm.state.value.snapshot)
            assertNull(vm.state.value.error)
        } finally {
            triggers.forEach { sql.execSQL("DROP TRIGGER IF EXISTS $it") }
            try { rule.activityRule.scenario.close() } finally {
                tables.forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) }
                if (activeBefore == null) projects.clearActiveStoryId() else projects.setActiveStoryId(activeBefore)
            }
        }
    }
}
