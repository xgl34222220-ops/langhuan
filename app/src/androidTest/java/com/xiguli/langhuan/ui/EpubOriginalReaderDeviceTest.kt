@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.xiguli.langhuan.ui.epub.EpubReaderActivity
import com.xiguli.langhuan.ui.epub.EpubReaderEntry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

/** This test must run on an Android WebView. JVM extraction tests do not prove rendered artwork. */
class EpubOriginalReaderDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun reflowActuallyDrawsPngSvgCssFontAndBlocksBookCode() {
        val id = seed("original-reflow.epub", "epub-device-reflow")
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitForArt(scenario)
            scenario.onActivity { activity ->
                webViews(activity.window.decorView).forEach { view ->
                    assertFalse(view.settings.allowFileAccess)
                    assertFalse(view.settings.allowContentAccess)
                    assertTrue(view.settings.blockNetworkLoads)
                    assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
                }
            }
            val result = JSONObject(evaluate(scenario, """JSON.stringify({
                color:getComputedStyle(document.body).color,
                css:getComputedStyle(document.querySelector('.author-box')).borderLeftWidth,
                font:Array.from(document.fonts).some(f => f.family.replaceAll('"','') === 'LanghuanFixture' && f.status === 'loaded') && document.fonts.check('24px LanghuanFixture'),
                scripts:!!(window.bookScriptExecuted||window.bookEventExecuted||window.svgScriptExecuted),
                forbidden:document.querySelectorAll('iframe,script:not([src^="https://readium_assets/"]),[onerror]').length
            })"""))
            assertEquals("rgb(51, 68, 85)", result.getString("color"))
            assertEquals("6px", result.getString("css"))
            assertTrue(result.getBoolean("font"))
            assertFalse(result.getBoolean("scripts"))
            assertEquals(0, result.getInt("forbidden"))
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                requireNotNull(screenshot)
                val colours = listOf(Color.rgb(8, 145, 178), Color.rgb(234, 88, 12), Color.rgb(192, 38, 211), Color.rgb(101, 163, 13))
                colours.forEach { color -> assertTrue("Artwork color $color not drawn", countPixels(screenshot, color) > 100) }
                val destination = File(context.getExternalFilesDir(null), "epub-evidence").apply { mkdirs() }
                File(destination, "original-reflow.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot?.recycle() }
        }
    }

    @Test fun navigationAndLocatorSurviveRecreationAndColdActivityOpen() {
        val id = seed("original-reflow.epub", "epub-device-locator")
        var before: Locator? = null
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitForArt(scenario)
            // Follow the actual EPUB navigation anchor instead of indexing extracted text chapters.
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitUntil { current(scenario)?.href?.toString()?.endsWith("two.xhtml") == true }
            scenario.onActivity { nav(it).goForward(animated = false) }
            waitUntil { (current(scenario)?.locations?.progression ?: 0.0) > 0.0 }
            before = current(scenario)
            scenario.recreate()
            waitUntil { current(scenario)?.href == before?.href && kotlin.math.abs((current(scenario)?.locations?.progression ?: -1.0) - (before?.locations?.progression ?: 0.0)) < 0.08 }
            scenario.onActivity { nav(it).goBackward(animated = false) }
            waitUntil { (current(scenario)?.locations?.progression ?: 1.0) < (before?.locations?.progression ?: 0.0) }
            before = current(scenario)
        }
        // Fresh Activity + reopened publication proves disk-backed Locator reconstruction.
        // Actual OS process-death coverage is the separate two-invocation CI recipe.
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { reopened ->
            waitUntil { current(reopened)?.href == before?.href }
            assertEquals(before!!.locations.progression ?: 0.0, current(reopened)!!.locations.progression ?: 0.0, 0.08)
        }
    }

    @Test fun fixedLayoutDrawsOriginalIllustrations() {
        val id = seed("original-fixed.epub", "epub-device-fixed")
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitForArt(scenario)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                requireNotNull(screenshot)
                assertTrue(countPixels(screenshot, Color.rgb(234, 88, 12)) > 100)
                assertTrue(countPixels(screenshot, Color.rgb(192, 38, 211)) > 100)
                val destination = File(context.getExternalFilesDir(null), "epub-evidence").apply { mkdirs() }
                File(destination, "original-fixed.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot?.recycle() }
            scenario.onActivity { nav(it).goForward(animated = false) }
            waitUntil { current(scenario)?.href?.toString()?.endsWith("two.xhtml") == true }
            scenario.onActivity { nav(it).goBackward(animated = false) }
            waitForArt(scenario)
        }
    }

    @Test fun epub2NcxNavigatesAndDrawsOriginalArt() {
        val id = seed("original-epub2.epub", "epub-device-epub2")
        val contents = kotlinx.coroutines.runBlocking {
            val publication = com.xiguli.langhuan.data.epub.EpubPublicationSession.open(context, EpubReaderEntry.store(context).open(id))
            try { publication.tableOfContents } finally { publication.close() }
        }
        assertEquals(2, contents.size)
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitForArt(scenario)
            scenario.onActivity { activity ->
                val navigator = nav(activity)
                navigator.go(contents[1], animated = false)
            }
            waitUntil { current(scenario)?.href?.toString()?.endsWith("two.xhtml") == true }
        }
    }

    @Test fun backgroundResumeAndRecreationPreserveLocatorAndArtwork() {
        val id = seed("original-reflow.epub", "epub-device-background")
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitForArt(scenario)
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitUntil { current(scenario)?.href?.toString()?.endsWith("two.xhtml") == true }
            scenario.onActivity { nav(it).goForward(animated = false) }
            waitUntil { (current(scenario)?.locations?.progression ?: 0.0) > 0.0 }
            val before = requireNotNull(current(scenario))
            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil { current(scenario)?.href == before.href }
            scenario.moveToState(Lifecycle.State.CREATED)
            // ActivityScenario.recreate temporarily resumes even when called while stopped.
            // It restores CREATED on return; do not pretend the new Activity was never resumed.
            scenario.recreate()
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            // Keep that actual stopped instance in the background while pending IO can finish.
            // Loading or an already attached navigator are both valid until we resume below.
            val backgroundDeadline = android.os.SystemClock.uptimeMillis() + 1500
            while (android.os.SystemClock.uptimeMillis() < backgroundDeadline) {
                scenario.onActivity { activity ->
                    assertFalse(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    val failure = textViews(activity.window.decorView).any { it.text.contains("可重新关联原文件，或继续阅读文字版") }
                    assertFalse("Background open reported an attachment error", failure)
                }
                Thread.sleep(100)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitUntil { current(scenario)?.href == before.href && kotlin.math.abs((current(scenario)?.locations?.progression ?: -1.0) - (before.locations.progression ?: 0.0)) < 0.08 }
            // A second ordinary stop/resume must retain a functioning navigator and original art.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            val contents = kotlinx.coroutines.runBlocking {
                val publication = com.xiguli.langhuan.data.epub.EpubPublicationSession.open(context, EpubReaderEntry.store(context).open(id))
                try { publication.tableOfContents } finally { publication.close() }
            }
            scenario.onActivity { nav(it).go(contents.first(), animated = false) }
            waitForArt(scenario)
        }
    }

    /** Default suite executes both phases; explicit phases require a real process boundary. */
    @Test fun processDeathRoundTrip() {
        when (val phase = InstrumentationRegistry.getArguments().getString("phase") ?: "both") {
            "both" -> { seedProcessDeath(); restoreProcessDeath(requireNewProcess = false) }
            "seed" -> seedProcessDeath()
            "restore" -> restoreProcessDeath(requireNewProcess = true)
            else -> error("Unsupported EPUB process test phase: $phase")
        }
    }

    private fun seedProcessDeath() {
        val id = seed("original-reflow.epub", "epub-device-process-death")
        var expected: Locator? = null
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitForArt(scenario)
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitUntil { current(scenario)?.href?.toString()?.endsWith("two.xhtml") == true }
            scenario.onActivity { nav(it).goForward(animated = false) }
            waitUntil { (current(scenario)?.locations?.progression ?: 0.0) > 0.0 }
            expected = current(scenario)
        }
        val locator = requireNotNull(expected)
        assertTrue("Seed did not reach chapter two", locator.href.toString().endsWith("two.xhtml"))
        assertTrue("Seed did not turn a page", (locator.locations.progression ?: 0.0) > 0.0)
        assertTrue(context.getSharedPreferences("epub_process_test_evidence", 0).edit()
            .putInt("seed_pid", android.os.Process.myPid())
            .putString("expected_locator", locator.toJSON().toString()).commit())
    }

    private fun restoreProcessDeath(requireNewProcess: Boolean) {
        val id = "epub-device-process-death"
        val store = EpubReaderEntry.store(context)
        assertTrue("Restore requires a successful seed; this test never skips", store.hasOriginal(id))
        val evidence = context.getSharedPreferences("epub_process_test_evidence", 0)
        val seedPid = evidence.getInt("seed_pid", -1)
        assertTrue("Missing completed seed evidence", seedPid > 0)
        if (requireNewProcess) assertNotEquals("Restore must run after force-stop in a new process", seedPid, android.os.Process.myPid())
        val before = Locator.fromJSON(JSONObject(requireNotNull(evidence.getString("expected_locator", null))))!!
        val persisted = Locator.fromJSON(JSONObject(requireNotNull(store.loadLocator(id, requireNotNull(store.digest(id))))))!!
        assertEquals("Seed locator was not persisted", before.href, persisted.href)
        assertEquals(before.locations.progression ?: 0.0, persisted.locations.progression ?: 0.0, 0.08)
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            waitUntil { current(scenario)?.href == before.href && (current(scenario)?.locations?.progression ?: 0.0) > 0.0 }
            assertEquals(before.locations.progression ?: 0.0, current(scenario)!!.locations.progression ?: 0.0, 0.08)
        }
    }

    private fun seed(file: String, id: String): String {
        val store = EpubReaderEntry.store(context)
        val prepared = instrumentation.context.assets.open("epub/$file").use { store.prepare(it) }
        store.associate(id, prepared)
        // Explicitly reset only this test fixture's EPUB locator, never the text-reader store.
        store.saveLocator(id, prepared.sha256, "{}")
        return id
    }

    private fun nav(activity: EpubReaderActivity) = activity.supportFragmentManager.findFragmentByTag("epub_original_navigator") as EpubNavigatorFragment
    private fun current(scenario: ActivityScenario<EpubReaderActivity>): Locator? {
        var locator: Locator? = null
        scenario.onActivity { activity -> locator = (activity.supportFragmentManager.findFragmentByTag("epub_original_navigator") as? EpubNavigatorFragment)?.currentLocator?.value }
        return locator
    }
    private fun webViews(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun textViews(view: View): List<android.widget.TextView> = when (view) {
        is android.widget.TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun evaluate(scenario: ActivityScenario<EpubReaderActivity>, code: String): String {
        val latch = CountDownLatch(1); var result = "null"
        scenario.onActivity { activity ->
            val view = webViews(activity.window.decorView).firstOrNull { it.isShown && it.url?.contains("one.xhtml") == true }
                ?: webViews(activity.window.decorView).firstOrNull { it.isShown }
            if (view == null) latch.countDown()
            else view.evaluateJavascript(code) { value -> result = value; latch.countDown() }
        }
        assertTrue("WebView JS response timed out", latch.await(5, TimeUnit.SECONDS))
        return if (result.startsWith('"')) org.json.JSONArray("[$result]").getString(0) else result
    }
    private fun waitForArt(scenario: ActivityScenario<EpubReaderActivity>) {
        waitUntil { evaluate(scenario, "!!(document.getElementById('png-art')?.complete && document.getElementById('png-art')?.naturalWidth===240 && document.getElementById('svg-art')?.complete && document.getElementById('svg-art')?.naturalWidth===240 && document.fonts.status==='loaded' && window.readium)") == "true" }
    }
    private fun waitUntil(test: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 25_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (runCatching(test).getOrDefault(false)) return
            Thread.sleep(100)
        }
        assertTrue("EPUB rendering or navigation did not become ready", test())
    }
    private fun countPixels(bitmap: Bitmap, color: Int): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            if (kotlin.math.abs(Color.red(pixel) - Color.red(color)) < 8 && kotlin.math.abs(Color.green(pixel) - Color.green(color)) < 8 && kotlin.math.abs(Color.blue(pixel) - Color.blue(color)) < 8) count++
        }
        return count
    }
}
