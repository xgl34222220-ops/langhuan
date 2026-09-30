package com.xiguli.langhuan.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xiguli.langhuan.domain.ChapterDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real session regression: leaving immediately after a turn must bypass the save debounce. */
class ReaderProgressV42DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val book = ReaderBookUi("progress-v42", "夜航记", "文学", "", "", "", 12000, 100000, 1, 1L)
    private val chapter = ChapterDraft("progress-chapter-v42", book.id, 1, "第一章 夜航", "", emptyList(),
        content = (1..600).joinToString("\n") { "第${it}段，夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。" })

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun saved() = ReaderProgressStoreV11.load(rule.activity, book.id, 1)

    @Test fun pauseAndDisposeFlushTheLatestPageBeforeDebounce() {
        lateinit var owner: Owner
        lateinit var settings: ReaderSettingsV30
        val visible = mutableStateOf(true)
        rule.runOnUiThread {
            ReaderProgressStoreV11.save(rule.activity, book.id, ReaderProgressV11(chapterNumber = 1))
            owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            settings = ReaderSettingsV30(rule.activity.getSharedPreferences("progress-test-settings-v42", 0)).apply {
                turnMode = ReaderTurnModeV30.NONE
                clickAnimation = false
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (visible.value) ReaderSessionV30(book, listOf(chapter), chapter.id, settings,
                    false, true, {}, {}, {}, {}, {})
            }
        }
        rule.waitUntil(20000) {
            rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.StateDescription].startsWith("第")
            }
        }
        repeat(5) {
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            rule.mainClock.advanceTimeBy(400)
        }
        rule.waitForIdle()
        val before = saved()
        assertTrue("Fixture must have advanced beyond the first page", before.textOffset > 0)

        // Freeze virtual time so the 260ms debounce cannot conceal a broken lifecycle save.
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
            val paused = saved()
            assertTrue("ON_PAUSE saved a stale page", paused.textOffset > before.textOffset)
            assertEquals(before.pageIndex + 1, paused.pageIndex)
    
            rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            rule.runOnUiThread { visible.value = false }
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val disposed = saved()
            assertTrue("Leaving the session saved a stale page", disposed.textOffset > paused.textOffset)
            assertEquals(paused.pageIndex + 1, disposed.pageIndex)
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }
    @Test fun sameFramePageTurnAndFontChangeKeepTheNewSentence() {
        lateinit var owner: Owner
        lateinit var settings: ReaderSettingsV30
        rule.runOnUiThread {
            ReaderProgressStoreV11.save(rule.activity, book.id, ReaderProgressV11(chapterNumber = 1))
            owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            settings = ReaderSettingsV30(rule.activity.getSharedPreferences("progress-test-settings-v42", 0)).apply {
                fontSize = 20f
                turnMode = ReaderTurnModeV30.NONE
                clickAnimation = false
                volumeTurn = true
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ReaderSessionV30(book, listOf(chapter), chapter.id, settings, false, true, {}, {}, {}, {}, {})
            }
        }
        rule.waitUntil(20000) {
            rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.StateDescription].startsWith("第")
            }
        }
        repeat(3) {
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            rule.mainClock.advanceTimeBy(400)
        }
        val before = saved().textOffset
        assertTrue(before > 0)
        // Both actions run in one UI callback, so no composition can record the turn first.
        rule.runOnUiThread {
            assertTrue(rule.activity.dispatchKeyEvent(android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN)))
            settings.fontSize = 24f
            rule.activity.dispatchKeyEvent(android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
        }
        rule.waitUntil(20000) {
            rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.StateDescription].startsWith("第")
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertTrue("Same-frame reflow discarded the page turn", saved().textOffset > before)
        val turnedAnchor = saved().textOffset
        rule.runOnUiThread { settings.fontSize = 18f }
        rule.waitUntil(20000) {
            rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.StateDescription].startsWith("第")
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals("A second reflow rounded the sentence backwards", turnedAnchor, saved().textOffset)
    }

    @Test fun pausingDuringAnAnimatedTurnSavesTheRequestedPage() {
        lateinit var owner: Owner
        lateinit var settings: ReaderSettingsV30
        rule.runOnUiThread {
            ReaderProgressStoreV11.save(rule.activity, book.id, ReaderProgressV11(chapterNumber = 1))
            owner = Owner().also { it.registry.currentState = Lifecycle.State.RESUMED }
            settings = ReaderSettingsV30(rule.activity.getSharedPreferences("progress-test-settings-v42", 0)).apply {
                fontSize = 20f
                turnMode = ReaderTurnModeV30.COVER
                clickAnimation = true
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ReaderSessionV30(book, listOf(chapter), chapter.id, settings, false, true, {}, {}, {}, {}, {})
            }
        }
        rule.waitUntil(20000) {
            rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.StateDescription].startsWith("第")
            }
        }
        rule.mainClock.advanceTimeBy(400)
        val before = saved().textOffset
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
            assertTrue("Pause discarded the in-flight page turn", saved().textOffset > before)
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

}
