package com.xiguli.langhuan.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.NewStoryRequest
import com.xiguli.langhuan.data.local.LanghuanDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Full ACTION_VIEW -> ContentResolver -> confirmation -> Room, with original test data only. */
class ExternalBookOpenFlowV1DeviceTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun realContentProviderTxtAndEpubImportOnceAfterConfirmation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val manager = StoryProjectManager(app)
        val oldActive = manager.activeStoryId()
        val active = manager.createStory(NewStoryRequest("导入不切换当前创作测试", "测试", "原创测试", "保留当前作品", 10000)).snapshot.novel.id
        val before = manager.observeStories().first().map { it.id }.toSet()
        val created = mutableSetOf<String>()
        try {
            for (extension in listOf("txt", "epub")) {
                val uri = Uri.parse("content://com.xiguli.langhuan.test.books/novel.$extension")
                val launch = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/octet-stream").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ActivityScenario.launch<MainActivity>(launch).use { scenario ->
                    try {
                    rule.waitUntil(20_000) { rule.onAllNodesWithText("导入到琅嬛？").fetchSemanticsNodes().size == 1 }
                    assertEquals(before + created, manager.observeStories().first().map { it.id }.toSet())
                    rule.onNodeWithText("导入").performClick()
                    rule.waitUntil(20_000) { rule.onAllNodesWithText("导入结果").fetchSemanticsNodes().size == 1 }
                    rule.onAllNodesWithText("已加入书架", substring = true).onFirst().assertIsDisplayed()
                    val books = withTimeout(10_000) { manager.observeStories().first { it.map { b -> b.id }.toSet().size == before.size + created.size + 1 } }
                    val id = (books.map { it.id }.toSet() - before - created).single()
                    created += id
                    assertTrue(manager.chapterDrafts(id).any { it.content.contains("原创小说正文") })
                    assertEquals(active, manager.activeStoryId())
                    scenario.onActivity { instrumentation.callActivityOnNewIntent(it, Intent(launch)) }
                    rule.waitForIdle()
                    assertEquals(before + created, manager.observeStories().first().map { it.id }.toSet())
                    assertEquals(app.packageName, instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
                    val image = instrumentation.uiAutomation.takeScreenshot() ?: error("Missing native import result")
                    val file = File(app.getExternalFilesDir(null), "reader-qa/v52-external-$extension-import-test-data.png").apply { parentFile!!.mkdirs() }
                    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
                    instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                    instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                    rule.onNodeWithText("关闭").performClick()
                    if (extension == "epub") {
                        rule.waitUntil(20_000) {
                            var opened = false
                            instrumentation.runOnMainSync {
                                opened = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                                    .any { it is com.xiguli.langhuan.ui.epub.EpubReaderActivity }
                            }
                            opened
                        }
                        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        rule.waitUntil(10_000) {
                            var returned = false
                            instrumentation.runOnMainSync {
                                returned = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).any { it is MainActivity }
                            }
                            returned
                        }
                    }
                    } catch (error: Throwable) {
                        runCatching { deviceWindowEvidenceV46("v55-external-$extension-failure") }
                        runCatching {
                            var state = ""
                            scenario.onActivity { activity ->
                                val vm = androidx.lifecycle.ViewModelProvider(activity)[LocalBookImportViewModelV1::class.java]
                                state = vm.state.value.toString()
                            }
                            val file = File(app.getExternalFilesDir(null), "reader-qa/v55-external-$extension-state.txt").apply { parentFile!!.mkdirs() }
                            file.writeText(state)
                            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
                        }
                        throw error
                    }
                }
            }
        } finally {
            val sql = LanghuanDatabase.get(app).openHelper.writableDatabase
            (created + active).forEach { id -> listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) } }
            if (oldActive == null) manager.clearActiveStoryId() else manager.setActiveStoryId(oldActive)
        }
    }
}
