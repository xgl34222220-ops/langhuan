package com.xiguli.langhuan.ui

import android.app.UiAutomation
import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Same Activity reconstruction used for configuration changes, with a real persisted book. */
class ReaderRecreationV42DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** Scroll motion can leave Compose 1.10 merged-node coordinates stale in a rotated
     * AnimatedContent. Validate the foreground system tree and inject a real screen tap;
     * the caller must also observe the actual settings change, so a hidden node cannot pass.
     */
    private fun tapVisibleFontAction(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val description = if (text == "A+") "增大阅读字号" else "减小阅读字号"
        var hit: android.graphics.Rect? = null
        var stableSince = 0L
        var lastCandidate: String? = null
        rule.waitForIdle()
        automation.waitForIdle(250, 5_000)
        fun currentHit(root: android.view.accessibility.AccessibilityNodeInfo): android.graphics.Rect? {
            if (root.packageName?.toString() != "com.xiguli.langhuan") return null
            val window = android.graphics.Rect().also(root::getBoundsInScreen)
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.graphics.Rect? {
                if (node == null) return null
                if (node.contentDescription?.toString() == description) {
                    val labelBounds = android.graphics.Rect().also(node::getBoundsInScreen)
                    // Compose may expose a named label below the actual clickable Box.
                    // Only accept that label or its direct button parent, never a page ancestor.
                    val target = if (node.isClickable) node else node.parent?.takeIf { it.isClickable }
                    val bounds = target?.let { android.graphics.Rect().also(it::getBoundsInScreen) }
                    var parent = target?.parent
                    var inViewport = true
                    while (parent != null) {
                        if (parent.isScrollable) {
                            val viewport = android.graphics.Rect().also(parent::getBoundsInScreen)
                            if (bounds == null || !viewport.contains(bounds)) inViewport = false
                        }
                        parent = parent.parent
                    }
                    val candidate = "$description label=$labelBounds button=$bounds clickable=${target?.isClickable} enabled=${target?.isEnabled} visible=${target?.isVisibleToUser} inViewport=$inViewport"
                    if (candidate != lastCandidate) { android.util.Log.i("ReaderFontInputV57", candidate); lastCandidate = candidate }
                    if (target != null && bounds != null && target.isClickable && target.isEnabled && target.isVisibleToUser &&
                        node.isEnabled && node.isVisibleToUser && !labelBounds.isEmpty && !bounds.isEmpty &&
                        bounds.contains(labelBounds) && window.contains(bounds) && inViewport) return bounds
                }
                for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
                return null
            }
            return find(root)
        }
        fun scrollViewport(node: android.view.accessibility.AccessibilityNodeInfo?): android.graphics.Rect? {
            if (node == null) return null
            if (node.isScrollable && node.isVisibleToUser && node.className?.toString() == "android.widget.ScrollView") {
                return android.graphics.Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty }
            }
            for (index in 0 until node.childCount) scrollViewport(node.getChild(index))?.let { return it }
            return null
        }
        // Scroll using actual screen input: a stale merged-node ScrollToRect can leave
        // the font control outside the clipped body while claiming it was brought in.
        for (attempt in 0 until 4) {
            val root = requireNotNull(automation.rootInActiveWindow)
            assertEquals("Font controls must stay in the foreground app", "com.xiguli.langhuan", root.packageName?.toString())
            if (currentHit(root) != null) break
            val viewport = requireNotNull(scrollViewport(root)) { "Reader menu has no visible scroll viewport" }
            val window = android.graphics.Rect().also(root::getBoundsInScreen)
            assertTrue("Menu scroll must stay inside the landscape window", window.width() > window.height() && window.contains(viewport))
            android.util.Log.i("ReaderFontInputV57", "Physical scroll $attempt inside $viewport")
            val downTime = android.os.SystemClock.uptimeMillis()
            val startY = viewport.bottom - viewport.height() * 0.15f
            fun move(action: Int, y: Float) {
                val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action,
                    viewport.exactCenterX(), y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue("Menu must accept the physical scroll", automation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            move(android.view.MotionEvent.ACTION_DOWN, startY)
            for (step in 1..10) {
                Thread.sleep(25)
                move(android.view.MotionEvent.ACTION_MOVE, startY - viewport.height() * 0.45f * step / 10)
            }
            move(android.view.MotionEvent.ACTION_UP, startY - viewport.height() * 0.45f)
            rule.waitForIdle()
            automation.waitForIdle(250, 5_000)
        }
        rule.waitUntil(5_000) {
            val root = automation.rootInActiveWindow ?: return@waitUntil false
            val window = android.graphics.Rect().also(root::getBoundsInScreen)
            val current = currentHit(root)
            if (current == null || current != hit) stableSince = android.os.SystemClock.elapsedRealtime()
            hit = current
            hit != null && window.width() > window.height() && android.os.SystemClock.elapsedRealtime() - stableSince >= 100
        }
        val bounds = requireNotNull(hit)
        android.util.Log.i("ReaderFontInputV57", "$description physical touch at $bounds")
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue("Visible $text must accept a screen touch", automation.injectInputEvent(down, true)) }
        finally { down.recycle() }
        Thread.sleep(60)
        val up = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
        up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue("Visible $text must accept touch release", automation.injectInputEvent(up, true)) }
        finally { up.recycle() }
    }

    @Test fun longChapterAndChangedFontStayInReaderAfterActivityRecreation() = runBlocking {
        val context = rule.activity.applicationContext
        val title = "阅读位置回归V42"
        context.getSharedPreferences("reader_qingmo_v9", 0).edit()
            .putBoolean("reader_comfort_v26", true).putBoolean("clickAnimation", false)
            .putString("pageMode", "none").putFloat("font", 20f).commit()
        val manager = StoryProjectManager(context)
        val story = manager.createImportedStory(ImportedManuscript(title = title, chapters = listOf(
            ImportedChapter("第一章 夜航", (1..600).joinToString("\n") {
                "第${it}段，夜色落在平静的水面上。船舱里只亮着一盏灯，林远将旧信收进衣袋，望向岸边。他记得来时的路，却找不到原来的码头。"
            }),
        )))
        val id = story.snapshot.novel.id
        lateinit var vm: LibraryExperienceViewModel
        try {
            rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[LibraryExperienceViewModel::class.java] }
            rule.waitUntil(20000) { vm.state.value.stories.any { it.id == id } }
            rule.onNodeWithText(title).performClick()
            rule.waitUntil(20000) { vm.state.value.readingChapter != null }
            rule.waitUntil(20000) {
                rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription].startsWith("第")
                }
            }
            repeat(8) {
                rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(Offset(width * .9f, height * .5f)) }
                rule.mainClock.advanceTimeBy(400)
            }
            val beforeFont = ReaderProgressStoreV11.load(context, id, 1)
            assertTrue(beforeFont.textOffset > 1000)
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.onNodeWithContentDescription("阅读菜单：详情").assertIsDisplayed().performClick()
            rule.onNodeWithText("A+").performScrollTo()
            rule.onNodeWithText("A+").performClick()
            rule.mainClock.advanceTimeBy(1000)
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.mainClock.advanceTimeBy(1000)
            rule.waitForIdle()
            val beforeRecreate = ReaderProgressStoreV11.load(context, id, 1)
            assertTrue("Font reflow lost the chapter anchor", beforeRecreate.textOffset > 0)
            assertTrue("Font reflow jumped forwards", beforeRecreate.textOffset <= beforeFont.textOffset)
            assertTrue("Font reflow jumped back more than a full page", beforeFont.textOffset - beforeRecreate.textOffset < 1200)
            rule.activityRule.scenario.recreate()
            rule.waitUntil(20000) { rule.onAllNodesWithText("正在检查琅嬛数据…").fetchSemanticsNodes().isEmpty() }
            rule.waitUntil(20000) {
                rule.onAllNodesWithContentDescription("阅读正文").fetchSemanticsNodes().any {
                    it.config[SemanticsProperties.StateDescription].startsWith("第")
                }
            }
            // The shelf has a semantic book title, while the reader title is drawn in Canvas.
            rule.onAllNodesWithText("书城").assertCountEquals(0)
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(300)
            // The reader opens its “更多” tab; the shelf button belongs to “目录”/“详情”.
            rule.onNodeWithContentDescription("阅读菜单：详情").assertIsDisplayed().performClick()
            rule.onNodeWithText("A+").performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription("阅读菜单：目录").assertIsDisplayed().performClick()
            rule.onNodeWithContentDescription("返回书架").assertIsDisplayed()
            rule.mainClock.advanceTimeBy(1000)
            val afterRecreate = ReaderProgressStoreV11.load(context, id, 1)
            assertEquals(beforeRecreate.chapterNumber, afterRecreate.chapterNumber)
            assertEquals(beforeRecreate.textOffset, afterRecreate.textOffset)
            assertEquals(21f, context.getSharedPreferences("reader_qingmo_v9", 0).getFloat("font", 0f), 0f)
            rule.runOnUiThread {
                rule.activity.onBackPressedDispatcher.onBackPressed()
                context.getSharedPreferences("reader_qingmo_v9", 0).edit().putBoolean("lockPortrait", false).commit()
            }
            rule.waitForIdle()
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            rule.waitUntil(20000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            rule.mainClock.advanceTimeBy(1000)
            rule.waitForIdle()
            rule.onAllNodesWithText("书城").assertCountEquals(0)
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            deviceWindowEvidenceV46("v42-reader-landscape-menu")
            // The reader opens its “更多” tab; the shelf button belongs to “目录”/“详情”.
            rule.onNodeWithContentDescription("阅读菜单：详情").assertIsDisplayed().performClick()
            rule.onNodeWithText("A+").performScrollTo()
            deviceWindowEvidenceV46("v42-reader-landscape-font")
            val fontAction = rule.onNodeWithText("A+")
            try {
                fontAction.assertExists().assertIsEnabled()
                val prefs = context.getSharedPreferences("reader_qingmo_v9", 0)
                assertEquals("Rotation changed the saved font", 21f, prefs.getFloat("font", 0f), 0f)
                tapVisibleFontAction("A+")
                rule.waitUntil(5_000) { prefs.getFloat("font", 0f) == 22f }
                tapVisibleFontAction("A−")
                rule.waitUntil(5_000) { prefs.getFloat("font", 0f) == 21f }
                rule.waitForIdle()
                InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 5_000)
                rule.waitForIdle()
                assertEquals("Settling the rendered menu changed the font", 21f, prefs.getFloat("font", 0f), 0f)
                rule.onNodeWithText("21sp").assertIsDisplayed()
                deviceWindowEvidenceV46("v42-reader-landscape-font-operable")
            } catch (error: Throwable) {
                deviceWindowEvidenceV46("v42-reader-landscape-font-failure")
                val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                val tree = StringBuilder()
                fun describe(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0) {
                    if (node == null || depth > 30 || tree.length > 30000) return
                    val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                    tree.append("  ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                        .append(" description=").append(node.contentDescription).append(" visible=").append(node.isVisibleToUser)
                        .append(" clickable=").append(node.isClickable).append(" enabled=").append(node.isEnabled)
                        .append(" scrollable=").append(node.isScrollable)
                        .append(" bounds=").append(bounds).append('\n')
                    for (index in 0 until node.childCount) describe(node.getChild(index), depth + 1)
                }
                describe(root)
                throw AssertionError("Landscape font visibility: ${fontAction.printToString()}\n" +
                    "unclipped=${fontAction.getUnclippedBoundsInRoot()}\n$tree", error)
            }
            rule.onNodeWithContentDescription("阅读菜单：目录").assertIsDisplayed().performClick()
            rule.onNodeWithContentDescription("返回书架").assertIsDisplayed()
            val rotated = ReaderProgressStoreV11.load(context, id, 1)
            assertTrue("Rotation lost the sentence anchor", rotated.textOffset > 0)
            assertTrue("Rotation jumped forwards", rotated.textOffset <= afterRecreate.textOffset)
            assertTrue("Rotation jumped backwards more than a full page", afterRecreate.textOffset - rotated.textOffset < 1600)
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            rule.runOnUiThread { vm.closeBook() }
            val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
            listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach {
                sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id))
            }
        }
    }
}
