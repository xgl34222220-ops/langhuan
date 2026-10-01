package com.xiguli.langhuan.ui

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.ChapterEditorStore
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Actual MainActivity entry, retained editor ViewModel, editor UI and Room.
 * External model output is a synthetic production-store checkpoint; no AI is called.
 * Reconstructed from the retained 136 test record after environment replacement.
 */
class CreativeEditorEntryV136DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun bookEntryReloadsANewerExternalBodyBeforeFurtherManualEdits() = runBlocking {
        exercise(fromWriting = false)
    }

    @Test fun writingEntryReloadsANewerExternalBodyBeforeFurtherManualEdits() = runBlocking {
        exercise(fromWriting = true)
    }

    private fun readerDetails() {
        if (rule.onAllNodesWithText("详情").fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
        }
        rule.onNodeWithText("详情").performClick()
    }

    private fun enterEditor(fromWriting: Boolean) {
        if (fromWriting) {
            val label = "精修正文 · 保存后仍可反复修改"
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
            // A LazyColumn item may be taller than its viewport; expose the actual child button too.
            rule.onNodeWithText(label).performScrollTo()
            var lastBounds: Rect? = null
            var stableSince = SystemClock.uptimeMillis()
            rule.waitUntil(10_000) {
                val target = rule.onNodeWithText(label)
                val bounds = target.fetchSemanticsNode().boundsInRoot
                if (bounds != lastBounds) { lastBounds = bounds; stableSince = SystemClock.uptimeMillis() }
                target.isDisplayed() && SystemClock.uptimeMillis() - stableSince >= 300
            }
            rule.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().performClick()
        } else {
            readerDetails()
            rule.onNodeWithText("编辑本章").assertIsDisplayed().performClick()
        }
    }

    private fun body(text: String) = rule.onNode(hasSetTextAction() and hasText(text))

    private suspend fun exercise(fromWriting: Boolean) {
        val context = rule.activity.applicationContext
        val projects = StoryProjectManager(context)
        val store = ChapterEditorStore(context)
        val previousActive = projects.activeStoryId()
        val title = "编辑重进合成测试 ${UUID.randomUUID().toString().take(8)}"
        val initial = "合成初稿：林舟看见码头上的灯，收起尚未写完的回信。"
        val firstManual = "第一次手改：林舟写下称呼，把回信放在书店桌边。"
        val externalBody = "外部新稿：邮差提前到达，林舟重新写好完整回信，并决定亲手交给收信人。"
        val finalBody = externalBody + "他又在信末补上新的约定。"
        val imported = projects.createImportedStory(ImportedManuscript(title, listOf(ImportedChapter("第一章 来信", initial))))
        val sourceId = imported.snapshot.novel.id
        var targetId = sourceId
        lateinit var library: LibraryExperienceViewModel
        lateinit var editor: ChapterEditorViewModel
        lateinit var writing: WritingFlowViewModel
        var phase = "open synthetic reader"
        try {
            rule.runOnUiThread {
                library = ViewModelProvider(rule.activity)[LibraryExperienceViewModel::class.java]
                editor = ViewModelProvider(rule.activity)[ChapterEditorViewModel::class.java]
                writing = ViewModelProvider(rule.activity)[WritingFlowViewModel::class.java]
            }
            rule.waitUntil(20_000) { library.state.value.stories.any { it.id == sourceId } }
            rule.onNodeWithText(title).performClick()
            rule.waitUntil(20_000) { library.state.value.readingChapter?.content == initial }
            if (fromWriting) {
                phase = "enter Writing through reader"
                readerDetails()
                rule.onNodeWithText("AI 创作").assertIsDisplayed().performClick()
                rule.onNodeWithText("创建副本并进入").performClick()
                rule.waitUntil(20_000) { writing.state.value.ready && writing.state.value.novelId != sourceId }
                targetId = writing.state.value.novelId
                assertEquals(initial, writing.state.value.draft?.content)
            }

            phase = "first editor entry and manual save"
            enterEditor(fromWriting)
            rule.waitUntil(15_000) { editor.state.value.ready && editor.state.value.novelId == targetId && editor.state.value.draft?.content == initial }
            rule.onNodeWithText("正文编辑").assertIsDisplayed()
            body(initial).performTextReplacement(firstManual)
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(20_000) {
                if (fromWriting) writing.state.value.draft?.content == firstManual
                else library.state.value.readingChapter?.content == firstManual
            }
            assertFalse(editor.state.value.dirty)
            assertEquals(firstManual, editor.state.value.draft?.content)

            phase = "external newer body commits to real Room"
            val beforeExternal = requireNotNull(projects.loadStory(targetId))
            val committed = store.checkpoint(beforeExternal.snapshot,
                beforeExternal.draft.copy(title = "第一章 新来信", content = externalBody))
            assertEquals(externalBody, projects.chapterDraft(targetId, 1)?.content)
            // Do not call editor invalidation or load from the test; the actual Root entry must do it.
            assertEquals(firstManual, editor.state.value.draft?.content)

            phase = "actual Root entry must replace the clean retained editor cache"
            enterEditor(fromWriting)
            rule.waitUntil(15_000) { editor.state.value.draft?.content == externalBody || editor.state.value.error != null }
            assertNull(editor.state.value.error)
            assertTrue(editor.state.value.ready)
            assertEquals(targetId, editor.state.value.novelId)
            rule.onNodeWithText("正文编辑").assertIsDisplayed()
            body(externalBody).assertExists()
            assertEquals(committed.draft.version, editor.state.value.draft?.version)
            assertEquals("第一章 新来信", editor.state.value.draft?.title)
            deviceWindowEvidenceV46(if (fromWriting) "v136-writing-editor-fresh-body" else "v136-book-editor-fresh-body")

            phase = "further edit must preserve the external body and version"
            body(externalBody).performTextReplacement(finalBody)
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(20_000) {
                if (fromWriting) writing.state.value.draft?.content == finalBody
                else library.state.value.readingChapter?.content == finalBody
            }
            val durable = requireNotNull(projects.chapterDraft(targetId, 1))
            assertEquals(finalBody, durable.content)
            assertEquals(committed.draft.version, durable.version)
            assertEquals(externalBody, store.versions(targetId, 1).first().content)
            if (fromWriting) assertEquals(initial, projects.chapterDraft(sourceId, 1)?.content)
        } catch (error: Throwable) {
            runCatching { deviceWindowEvidenceV46(if (fromWriting) "v136-writing-editor-entry-failure" else "v136-book-editor-entry-failure") }
            val state = runCatching { editor.state.value }.getOrNull()
            throw AssertionError("Editor reentry failed at '$phase': novel=${state?.novelId}, " +
                "ready=${state?.ready}, busy=${state?.busy}, dirty=${state?.dirty}, error=${state?.error}, " +
                "title=${state?.draft?.title}, body=${state?.draft?.content}", error)
        } finally {
            try { rule.activityRule.scenario.close() } finally {
                val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
                setOf(sourceId, targetId).forEach { id ->
                    listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                        sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
                    }
                }
                if (previousActive == null) projects.clearActiveStoryId() else projects.setActiveStoryId(previousActive)
            }
        }
    }
}
