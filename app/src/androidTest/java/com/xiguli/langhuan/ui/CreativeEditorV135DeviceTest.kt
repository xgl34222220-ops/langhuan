package com.xiguli.langhuan.ui

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.data.*
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Real editor UI and Room persistence; only the save start is held for deterministic input races. */
class CreativeEditorV135DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val initial = "编辑器合成正文：海风吹过港口。"
    private val first = "第一次修改：林舟打开书店的门。"
    private val latest = "第二次修改：林舟保留新写的台词，不能被旧保存覆盖。"

    private fun create(context: Context) = runBlocking {
        StoryProjectManager(context).createImportedStory(ImportedManuscript("编辑保存回归", listOf(ImportedChapter("第一章 书店", initial))))
    }
    private fun remove(context: Context, id: String) {
        val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
        listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
            sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
        }
    }
    private fun vm(hook: suspend () -> Unit): ChapterEditorViewModel {
        lateinit var vm: ChapterEditorViewModel
        rule.runOnUiThread {
            vm = ViewModelProvider(rule.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ChapterEditorViewModel(rule.activity.application as Application, hook) as T
            })[ChapterEditorViewModel::class.java]
        }
        return vm
    }
    private fun body(text: String) = rule.onNode(hasSetTextAction() and hasText(text))
    private fun checkpoint() { rule.onNodeWithText("建版本").performClick() }

    @Test fun checkpointKeepsNewerVisibleInputAndOneBackDrainsItToRoom() = runBlocking {
        val context = rule.activity.applicationContext
        val book = create(context); val id = book.snapshot.novel.id
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(); val closed = AtomicInteger(); val show = mutableStateOf(true)
        val vm = vm { if (calls.incrementAndGet() == 1) { started.complete(Unit); release.await() } }
        try {
            rule.setContent { if (show.value) ChapterEditorExperience(id, 1, vm) { closed.incrementAndGet(); show.value = false } else Text("已返回创作") }
            rule.waitUntil(15_000) { vm.state.value.ready }
            body(initial).performTextReplacement(first)
            checkpoint()
            rule.waitUntil(10_000) { started.isCompleted }
            body(first).performTextReplacement(latest)
            rule.onNode(hasSetTextAction() and hasText("章节标题")).performTextReplacement("第一章 保留新标题")
            // One real toolbar click while checkpoint is pending must eventually leave safely.
            rule.onNodeWithContentDescription("保存并返回").performClick()
            assertEquals(0, closed.get())
            release.complete(Unit)
            rule.waitUntil(20_000) { closed.get() == 1 }
            rule.onNodeWithText("已返回创作").assertIsDisplayed()
            val loaded = ChapterEditorStore(context).load(id, 1)
            assertEquals(latest, loaded.draft.content)
            assertEquals("第一章 保留新标题", loaded.draft.title)
            assertEquals(2, loaded.draft.version)
            assertEquals(1, closed.get())
            val savedVersion = ChapterEditorStore(context).versions(id, 1).first()
            assertEquals(first, savedVersion.content)
            assertFalse(vm.state.value.dirty)
        } finally {
            release.complete(Unit)
            try { rule.activityRule.scenario.close() } finally { remove(context, id) }
        }
    }

    @Test fun completedCheckpointDoesNotReplaceTextTypedDuringItsWrite() = runBlocking {
        val context = rule.activity.applicationContext
        val book = create(context); val id = book.snapshot.novel.id
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val vm = vm { if (calls.incrementAndGet() == 1) { started.complete(Unit); release.await() } }
        try {
            rule.setContent { ChapterEditorExperience(id, 1, vm) {} }
            rule.waitUntil(15_000) { vm.state.value.ready }
            body(initial).performTextReplacement(first)
            checkpoint()
            rule.waitUntil(10_000) { started.isCompleted }
            body(first).performTextReplacement(latest)
            release.complete(Unit)
            rule.waitUntil(15_000) { vm.state.value.draft?.version == 2 && !vm.state.value.isSaving }
            body(latest).assertExists()
            assertEquals(latest, vm.state.value.draft?.content)
            // Autosave may already have run; in either case visible input and durable checkpoint differ intentionally.
            assertEquals(first, ChapterEditorStore(context).versions(id, 1).first().content)
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(15_000) { !vm.state.value.dirty && !vm.state.value.isSaving }
            assertEquals(latest, ChapterEditorStore(context).load(id, 1).draft.content)
        } finally {
            release.complete(Unit)
            try { rule.activityRule.scenario.close() } finally { remove(context, id) }
        }
    }

    @Test fun actualRoomWriteFailureKeepsInputAndRetryCanSaveAndClose() = runBlocking {
        val context = rule.activity.applicationContext
        val book = create(context); val id = book.snapshot.novel.id
        val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
        val trigger = "creative_v135_write_failure"
        val closed = AtomicInteger()
        val vm = vm {}
        try {
            rule.setContent { ChapterEditorExperience(id, 1, vm) { closed.incrementAndGet() } }
            rule.waitUntil(15_000) { vm.state.value.ready }
            // Abort the real Room write, without touching files or simulating a success result.
            sql.execSQL("CREATE TRIGGER $trigger BEFORE INSERT ON story_state WHEN NEW.novelId = '$id' BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
            body(initial).performTextReplacement(latest)
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(15_000) { !vm.state.value.isSaving && vm.state.value.dirty && vm.state.value.error?.contains("synthetic write failure") == true }
            rule.onNodeWithText("synthetic write failure", substring = true).assertExists()
            body(latest).assertExists()
            assertEquals(0, closed.get())
            assertEquals(initial, StoryProjectManager(context).chapterDraft(id, 1)!!.content)
            sql.execSQL("DROP TRIGGER $trigger")
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(15_000) { closed.get() == 1 }
            assertEquals(latest, ChapterEditorStore(context).load(id, 1).draft.content)
        } finally {
            sql.execSQL("DROP TRIGGER IF EXISTS $trigger")
            try { rule.activityRule.scenario.close() } finally { remove(context, id) }
        }
    }
}
