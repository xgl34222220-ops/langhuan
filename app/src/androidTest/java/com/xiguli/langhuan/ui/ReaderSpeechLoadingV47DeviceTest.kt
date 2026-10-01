package com.xiguli.langhuan.ui

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.xiguli.langhuan.domain.ChapterDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Production Compose session with a silent speech adapter: no audio service or private text. */
class ReaderSpeechLoadingV47DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private class Speech(val ready: (Boolean) -> Unit, val done: () -> Unit) : ReaderSpeechV47 {
        override var rate = 1f
        val spoken = mutableListOf<String>()
        var emptyQueues = 0
        var stops = 0
        var released = false
        override fun speak(items: List<ReaderTtsChunkV35>): Boolean {
            spoken += items.joinToString("\n") { it.text }
            if (items.isEmpty()) { emptyQueues++; Handler(Looper.getMainLooper()).post { done() } }
            return true
        }
        override fun stop() { stops++ }
        override fun release() { released = true }
    }
    private class Fixture(online: Boolean) {
        val book = ReaderBookUi("tts-loading-v47-${java.util.UUID.randomUUID()}", "朗读等待测试", "", "", "", "", 0, 10000, 1, 1, sourceId = if (online) "fixture" else "")
        val chapters = mutableStateOf(listOf(
            ChapterDraft("one", book.id, 1, "第一章", "", emptyList(), "已缓存的第一章正文。".repeat(12), sourceUrl = if (online) "https://example.org/1" else ""),
            ChapterDraft("two", book.id, 2, "第二章", "", emptyList(), sourceUrl = if (online) "https://example.org/2" else ""),
            ChapterDraft("three", book.id, 3, "第三章", "", emptyList(), "第三章正文。".repeat(12), sourceUrl = if (online) "https://example.org/3" else ""),
        ))
        val selected = mutableIntStateOf(1)
        val loading = mutableStateOf<Int?>(null)
        val error = mutableStateOf<String?>(null)
        val visible = mutableStateOf(true)
        lateinit var speech: Speech
        fun request(number: Int) { loading.value = number; error.value = null }
    }
    private fun start(online: Boolean = true, advance: Boolean = true): Fixture {
        val f = Fixture(online)
        lateinit var settings: ReaderSettingsV30
        rule.runOnUiThread {
            ReaderProgressStoreV11.save(rule.activity, f.book.id, ReaderProgressV11(1))
            settings = ReaderSettingsV30(rule.activity.getSharedPreferences("tts-loading-settings-v47", 0)).apply {
                turnMode = ReaderTurnModeV30.NONE; clickAnimation = false; fontSize = 20f
            }
        }
        rule.setContent {
            if (f.visible.value) ReaderSessionV30(f.book, f.chapters.value,
                f.chapters.value.first { it.chapterNumber == f.selected.intValue }.id,
                settings, false, true,
                { number -> f.selected.intValue = number; if (f.chapters.value[number - 1].content.isBlank()) f.request(number) },
                {}, {}, {}, {}, onLoadChapter = f::request,
                loadingChapterNumber = f.loading.value, chapterLoadError = f.error.value,
                speechFactory = ReaderSpeechFactoryV47 { _, ready, _, done -> Speech(ready, done).also { f.speech = it } },
            )
        }
        rule.waitUntil(20000) { rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any { it.config[SemanticsProperties.StateDescription].startsWith("第") } }
        rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .5f, height * .5f)) }
        rule.onNodeWithText("听书").performClick()
        rule.runOnIdle { f.speech.ready(true) }
        rule.waitUntil(10000) { f.speech.spoken.size == 1 }
        if (advance) rule.runOnIdle { f.speech.done() }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        return f
    }

    @Test fun missingBodyWaitsThenFailureAndExplicitRetryResumeTheSameChapter() {
        val f = start()
        assertEquals(2, f.selected.intValue)
        assertEquals(0, f.speech.emptyQueues)
        assertEquals(1, f.speech.spoken.size)
        assertFalse(rule.activity.getSharedPreferences("reader_stats_v35", 0).getBoolean("done_${f.book.id}_2", false))
        rule.runOnIdle { f.loading.value = null; f.error.value = "fixture: body unavailable" }
        rule.onNodeWithText("朗读已暂停").assertExists()
        assertEquals(2, f.selected.intValue)
        rule.onNodeWithText("重试加载").performClick()
        rule.runOnIdle {
            f.chapters.value = f.chapters.value.map { if (it.chapterNumber == 2) it.copy(content = "重试成功的第二章正文。".repeat(12)) else it }
            f.loading.value = null
        }
        rule.waitUntil(10000) { f.speech.spoken.size == 2 }
        assertTrue(f.speech.spoken.last().contains("重试成功的第二章正文"))
        assertEquals(2, f.selected.intValue)
        assertEquals(0, f.speech.emptyQueues)
        assertFalse(rule.activity.getSharedPreferences("reader_stats_v35", 0).getBoolean("done_${f.book.id}_2", false))
    }

    @Test fun manualChapterChangeWaitsAndStoppingDoesNotResumeOnLateBody() {
        val f = start(advance = false)
        rule.runOnIdle { f.selected.intValue = 2; f.request(2) }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertTrue("Switching chapter must stop the previous utterance", f.speech.stops > 0)
        assertEquals(1, f.speech.spoken.size)
        assertEquals(0, f.speech.emptyQueues)
        rule.onNodeWithContentDescription("停止朗读").performClick()
        rule.runOnIdle {
            f.chapters.value = f.chapters.value.map { if (it.chapterNumber == 2) it.copy(content = "晚到正文。".repeat(12)) else it }
            f.loading.value = null
            f.speech.done()
        }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertEquals(1, f.speech.spoken.size)
        assertEquals(2, f.selected.intValue)
        assertEquals(0, f.speech.emptyQueues)
    }

    @Test fun leavingBookReleasesSpeechAndIgnoresLateCallbacks() {
        val f = start()
        rule.runOnIdle { f.visible.value = false }
        rule.waitForIdle()
        assertTrue(f.speech.released)
        rule.runOnIdle { f.speech.ready(true); f.speech.done() }
        assertEquals(1, f.speech.spoken.size)
        assertEquals(2, f.selected.intValue)
    }

    @Test fun actualEmptyLocalChapterStillCompletesNormally() {
        val f = start(online = false)
        rule.waitUntil(10000) { f.selected.intValue == 3 && f.speech.spoken.lastOrNull()?.contains("第三章正文") == true }
        assertEquals(1, f.speech.emptyQueues)
    }

    @Test fun speechInitializationFailureStopsRatherThanLeavingAutoResumeArmed() {
        val f = start()
        rule.runOnIdle {
            f.speech.ready(false)
            f.chapters.value = f.chapters.value.map { if (it.chapterNumber == 2) it.copy(content = "后来取得的正文。".repeat(12)) else it }
            f.loading.value = null
        }
        rule.waitForIdle()
        rule.runOnIdle { f.speech.ready(true) }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertEquals(1, f.speech.spoken.size)
        rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .5f, height * .5f)) }
        rule.onNodeWithText("听书").performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        assertEquals("Manual restart must enqueue once, without a stale pending resume", 2, f.speech.spoken.size)
    }
}
