@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.xiguli.langhuan.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Rule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.xiguli.langhuan.ui.epub.EpubReaderActivity
import com.xiguli.langhuan.ui.epub.EpubReaderEntry
import com.xiguli.langhuan.data.epub.EpubWebContentPolicy
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
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withReader(id: String, block: (ActivityScenario<EpubReaderActivity>) -> Unit) {
        ActivityScenario.launch<EpubReaderActivity>(EpubReaderEntry.intent(context, id)).use { scenario ->
            try { block(scenario) }
            catch (error: Throwable) {
                val name = "${id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}-failure"
                runCatching { deviceWindowEvidenceV46(name) }
                runCatching {
                    val state = evaluate(scenario, """JSON.stringify({url:location.href,ready:document.readyState,
                        readium:!!window.readium,fonts:document.fonts.status,
                        images:Array.from(document.images).map(i=>({src:i.src,complete:i.complete,width:i.naturalWidth,height:i.naturalHeight})),
                        html:document.documentElement.outerHTML.slice(0,12000)})""")
                    val file = File(context.getExternalFilesDir(null), "reader-qa/$name-dom.json").apply { parentFile!!.mkdirs() }
                    file.writeText(state)
                    instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
                    }
                }
                throw error
            }
        }
    }

    @Test fun reflowActuallyDrawsPngSvgCssFontAndBlocksBookCode() {
        EpubWebContentPolicy.scriptHashes.keys.forEach { path ->
            val bytes = context.assets.open(path).use { it.readBytes() }
            assertTrue("Packaged SDK script does not match the pinned Readium 3.4 build: $path", EpubWebContentPolicy.assetHashMatches(path, bytes))
        }
        val id = seed("original-reflow.epub", "epub-device-reflow")
        withReader(id) { scenario ->
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
                borderStyle:getComputedStyle(document.querySelector('.author-box')).borderLeftStyle,
                borderReference:(() => { const probe=document.createElement('div');
                    probe.style.cssText='position:absolute;visibility:hidden;border-left:6px solid transparent';
                    document.body.appendChild(probe);const value=getComputedStyle(probe).borderLeftWidth;probe.remove();return value; })(),
                dpr:window.devicePixelRatio,
                font:Array.from(document.fonts).some(f => f.family.replaceAll('"','') === 'LanghuanFixture' && f.status === 'loaded') && document.fonts.check('24px LanghuanFixture'),
                scripts:!!(window.bookScriptExecuted||window.bookEventExecuted||window.svgScriptExecuted),
                protectedDocument:document.documentElement.getAttribute('data-langhuan-secure-readium') === '3',
                pinnedScripts:Array.from(document.scripts).every(s =>
                    s.src.startsWith('https://readium_package/__langhuan_readium_3_4__/readium/scripts/') &&
                    ['sha256-1hP+D3S4dxEbDG8uU0ZSsCzF1GE3kxZ75eaSkIHV+sk=', 'sha256-ySatQJeZ+aC4QdXbNqULAddIQB+U93azAO4lvRD7jVE='].includes(s.integrity)),
                forbidden:document.querySelectorAll('iframe,script:not([integrity]),[onerror]').length
            })"""))
            assertEquals("rgb(51, 68, 85)", result.getString("color"))
            // Blink snaps borders to device pixels. At this emulator's 2.625 DPR,
            // a declared 6 CSS px can resolve to 15 physical px / 2.625 = 5.71429 CSS px.
            // Compare against an independent, same-WebView 6px control rather than
            // accepting arbitrary widths or changing publisher CSS to fit the test.
            assertEquals("solid", result.getString("borderStyle"))
            assertEquals(result.getString("borderReference"), result.getString("css"))
            val measured = result.getString("css").removeSuffix("px").toDouble()
            val dpr = result.getDouble("dpr")
            assertTrue("Invalid WebView pixel ratio", dpr > 0.0)
            assertTrue("Author border differs by more than one device pixel", kotlin.math.abs(6.0 - measured) * dpr <= 1.01)
            assertTrue("Author border disappeared", measured > 0.0)
            assertTrue(result.getBoolean("font"))
            assertFalse(result.getBoolean("scripts"))
            assertTrue(result.getBoolean("protectedDocument"))
            assertTrue(result.getBoolean("pinnedScripts"))
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
        withReader(id) { scenario ->
            waitForArt(scenario)
            // Follow the actual EPUB navigation anchor instead of indexing extracted text chapters.
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitForPage(scenario, "two.xhtml")
            scenario.onActivity { nav(it).goForward(animated = false) }
            waitUntil { (current(scenario)?.locations?.progression ?: 0.0) > 0.0 }
            before = current(scenario)
            scenario.recreate()
            waitForPage(scenario, "two.xhtml")
            waitUntil { current(scenario)?.href == before?.href && kotlin.math.abs((current(scenario)?.locations?.progression ?: -1.0) - (before?.locations?.progression ?: 0.0)) < 0.08 }
            scenario.onActivity { nav(it).goBackward(animated = false) }
            waitUntil { (current(scenario)?.locations?.progression ?: 1.0) < (before?.locations?.progression ?: 0.0) }
            before = current(scenario)
        }
        // Fresh Activity + reopened publication proves disk-backed Locator reconstruction.
        // Actual OS process-death coverage is the separate two-invocation CI recipe.
        withReader(id) { reopened ->
            waitForPage(reopened, "two.xhtml")
            waitUntil { current(reopened)?.href == before?.href }
            assertEquals(before!!.locations.progression ?: 0.0, current(reopened)!!.locations.progression ?: 0.0, 0.08)
        }
    }

    @Test fun fixedLayoutDrawsOriginalIllustrations() {
        val id = seed("original-fixed.epub", "epub-device-fixed")
        withReader(id) { scenario ->
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
            waitForPage(scenario, "two.xhtml")
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
        withReader(id) { scenario ->
            waitForArt(scenario)
            scenario.onActivity { activity ->
                val navigator = nav(activity)
                navigator.go(contents[1], animated = false)
            }
            waitForPage(scenario, "two.xhtml")
        }
    }

    @Test fun backgroundResumeAndRecreationPreserveLocatorAndArtwork() {
        val id = seed("original-reflow.epub", "epub-device-background")
        withReader(id) { scenario ->
            waitForArt(scenario)
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitForPage(scenario, "two.xhtml")
            scenario.onActivity { assertTrue("The ready EPUB must accept the page turn", nav(it).goForward(animated = false)) }
            waitUntil { (current(scenario)?.locations?.progression ?: 0.0) > 0.0 &&
                evaluate(scenario, "window.scrollX > 0 || window.scrollY > 0") == "true" }
            val before = requireNotNull(current(scenario))
            traceBackgroundLocator(scenario, id, "before-stop", before)
            deviceWindowEvidenceV46("epub-background-before-stop")
            scenario.moveToState(Lifecycle.State.CREATED)
            traceBackgroundLocator(scenario, id, "after-stop", before)
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForPage(scenario, "two.xhtml")
            traceBackgroundLocator(scenario, id, "first-resume", before)
            waitUntil { current(scenario)?.href == before.href && kotlin.math.abs((current(scenario)?.locations?.progression ?: -1.0) - (before.locations.progression ?: 0.0)) < 0.08 }
            traceBackgroundLocator(scenario, id, "first-resume-settled", before)
            scenario.moveToState(Lifecycle.State.CREATED)
            // ActivityScenario.recreate temporarily resumes even when called while stopped.
            // It restores CREATED on return; do not pretend the new Activity was never resumed.
            scenario.recreate()
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            traceBackgroundLocator(scenario, id, "after-recreate-stopped", before)
            // Keep that actual stopped instance in the background while pending IO can finish.
            // Loading or an already attached navigator are both valid until we resume below.
            val backgroundDeadline = android.os.SystemClock.uptimeMillis() + 1500
            while (android.os.SystemClock.uptimeMillis() < backgroundDeadline) {
                scenario.onActivity { activity ->
                    assertFalse(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    val failure = (EpubReaderActivity::class.java.getDeclaredField("readerUiState\$delegate").apply { isAccessible = true }.get(activity) as androidx.compose.runtime.State<*>).value.let { (it as EpubReaderUiStateV50).statusMessage.contains("可重新关联原文件，或继续阅读文字版") }
                    assertFalse("Background open reported an attachment error", failure)
                }
                Thread.sleep(100)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForPage(scenario, "two.xhtml")
            traceBackgroundLocator(scenario, id, "recreated-resume", before)
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

    @Test fun stoppingBeforeRestoreCompletesPreservesTheSavedNonzeroPosition() {
        val id = seed("original-reflow.epub", "epub-device-pending-restore")
        val store = EpubReaderEntry.store(context)
        val digest = requireNotNull(store.digest(id))
        val target = Locator.fromJSON(JSONObject("""{"href":"OPS/two.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}}"""))!!
        store.saveLocator(id, digest, target.toJSON().toString())
        repeat(3) {
            withReader(id) { scenario ->
                // Do not wait for a painted page: stop and destroy during the opening transition.
                scenario.moveToState(Lifecycle.State.CREATED)
            }
            val saved = Locator.fromJSON(JSONObject(requireNotNull(store.loadLocator(id, digest))))!!
            assertEquals(target.href, saved.href)
            assertEquals("An unfinished restore must never replace the prior position with page zero",
                0.5, saved.locations.progression ?: -1.0, 0.08)
        }
        withReader(id) { scenario ->
            waitForPage(scenario, "two.xhtml")
            waitUntil { kotlin.math.abs((current(scenario)?.locations?.progression ?: -1.0) - 0.5) < 0.08 &&
                evaluate(scenario, "window.scrollX > 0 || window.scrollY > 0") == "true" }
        }
    }

    @Test fun explicitContinueAfterRestoreFailureCancelsOldIntentAndAllowsNewNavigation() {
        val id = seed("original-reflow.epub", "epub-device-restore-continue")
        withReader(id) { scenario ->
            waitForArt(scenario)
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitForPage(scenario, "two.xhtml")
            // Controlled renderer-failure state on a real loaded book; no external site or timer.
            scenario.onActivity { activity ->
                val target = nav(activity).currentLocator.value.copy(locations = Locator.Locations(progression = .7))
                EpubReaderActivity::class.java.getDeclaredField("restoreTarget").apply { isAccessible = true }.set(activity, target)
                EpubReaderActivity::class.java.getDeclaredField("loaded").apply { isAccessible = true }.setBoolean(activity, false)
                EpubReaderActivity::class.java.getDeclaredMethod("restorePendingPosition").apply { isAccessible = true }.invoke(activity)
                EpubReaderActivity::class.java.getDeclaredMethod("showRestoreFailure", String::class.java).apply { isAccessible = true }
                    .invoke(activity, "合成恢复失败")
            }
            compose.onNodeWithText("从当前页继续").assertIsDisplayed().performClick()
            scenario.onActivity { activity ->
                assertNull(EpubReaderActivity::class.java.getDeclaredField("restoreTarget").apply { isAccessible = true }.get(activity))
            }
            compose.onNodeWithContentDescription("下一页").assertIsEnabled().performClick()
            waitUntil { (current(scenario)?.locations?.progression ?: 0.0) > 0.0 &&
                evaluate(scenario, "window.scrollX > 0 || window.scrollY > 0") == "true" }
            val selected = requireNotNull(current(scenario))
            assertTrue((selected.locations.progression ?: 0.0) < .5)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitForPage(scenario, "two.xhtml")
            assertEquals(selected.locations.progression ?: 0.0, current(scenario)!!.locations.progression ?: 0.0, .08)
            val store = EpubReaderEntry.store(context)
            val persisted = Locator.fromJSON(JSONObject(requireNotNull(store.loadLocator(id, requireNotNull(store.digest(id))))))!!
            assertEquals(selected.locations.progression ?: 0.0, persisted.locations.progression ?: 0.0, .08)
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
        withReader(id) { scenario ->
            waitForArt(scenario)
            evaluate(scenario, "document.querySelector('a[href=\"two.xhtml#second\"]').click(); true")
            waitForPage(scenario, "two.xhtml")
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
        val id = requireNotNull(context.getSharedPreferences("epub_device_fixture_v56", 0).getString("epub-device-process-death", null))
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
        withReader(id) { scenario ->
            waitForPage(scenario, "two.xhtml")
            waitUntil { current(scenario)?.href == before.href && (current(scenario)?.locations?.progression ?: 0.0) > 0.0 }
            assertEquals(before.locations.progression ?: 0.0, current(scenario)!!.locations.progression ?: 0.0, 0.08)
        }
    }

    private fun traceBackgroundLocator(scenario: ActivityScenario<EpubReaderActivity>, id: String, phase: String, expected: Locator) {
        val store = EpubReaderEntry.store(context)
        val digest = store.digest(id)
        val record = JSONObject().put("phase", phase).put("lifecycle", scenario.state.name)
            .put("expected", expected.toJSON()).put("current", current(scenario)?.toJSON())
            .put("persisted", digest?.let { store.loadLocator(id,it) })
        if (scenario.state.isAtLeast(Lifecycle.State.RESUMED)) record.put("viewport", runCatching {
            evaluate(scenario, "JSON.stringify({href:location.href,x:window.scrollX,y:window.scrollY,width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight,fonts:document.fonts.status})")
        }.getOrElse { it.message.orEmpty() })
        val file = File(context.getExternalFilesDir(null), "reader-qa/epub-background-locator-trace.jsonl").apply { parentFile!!.mkdirs() }
        file.appendText(record.toString()+"\n")
        instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
    }

    private fun seed(file: String, key: String): String {
        val id = epubShelfFixtureV56(context, key)
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
            val currentHref = (activity.supportFragmentManager.findFragmentByTag("epub_original_navigator") as? EpubNavigatorFragment)
                ?.currentLocator?.value?.href?.toString()?.substringBefore('#')?.substringBefore('?')
            val visible = webViews(activity.window.decorView).filter { it.isShown }
            val view = visible.firstOrNull { currentHref != null && it.url?.substringBefore('#')?.substringBefore('?')?.endsWith("/$currentHref") == true }
                ?: visible.firstOrNull()
            if (view == null) latch.countDown()
            else view.evaluateJavascript(code) { value -> result = value; latch.countDown() }
        }
        assertTrue("WebView JS response timed out", latch.await(5, TimeUnit.SECONDS))
        return if (result.startsWith('"')) org.json.JSONArray("[$result]").getString(0) else result
    }
    private fun waitForPage(scenario: ActivityScenario<EpubReaderActivity>, file: String) {
        waitUntil { current(scenario)?.href?.toString()?.endsWith(file) == true &&
            readerAcceptsNavigation(scenario) &&
            evaluate(scenario, "!!(location.pathname.endsWith(${JSONObject.quote(file)}) && window.readium && document.documentElement.getAttribute('data-langhuan-secure-readium') === '3')") == "true" }
    }
    private fun readerAcceptsNavigation(scenario: ActivityScenario<EpubReaderActivity>): Boolean {
        var ready = false
        scenario.onActivity { activity ->
            // initialLocator is observable before the first layout. The SDK can accept direct
            // test calls while the app still blocks navigation behind its restore overlay.
            val loaded = EpubReaderActivity::class.java.getDeclaredField("loaded").apply { isAccessible = true }.getBoolean(activity)
            ready = loaded
        }
        return ready
    }
    private fun waitForArt(scenario: ActivityScenario<EpubReaderActivity>) {
        waitForPage(scenario, "one.xhtml")
        waitUntil { evaluate(scenario, "!!(document.getElementById('png-art')?.complete && document.getElementById('png-art')?.naturalWidth===240 && document.getElementById('svg-art')?.complete && document.getElementById('svg-art')?.naturalWidth===240 && document.fonts.status==='loaded' && window.readium)") == "true" }
        // Decoded images and JS can precede the first painted WebView frame. Require
        // the real window to show all four original artwork colours before proceeding.
        waitUntil {
            val frame = instrumentation.uiAutomation.takeScreenshot() ?: return@waitUntil false
            try {
                listOf(Color.rgb(8,145,178), Color.rgb(234,88,12), Color.rgb(192,38,211), Color.rgb(101,163,13))
                    .all { countPixels(frame,it) > 100 }
            } finally { frame.recycle() }
        }
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
