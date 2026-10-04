package com.xiguli.langhuan.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs real WebView rendering/IPC/profile reuse, with an explicit synthetic model and site. */
@RunWith(AndroidJUnit4::class)
class SourceBrowserSessionV56DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val base = "https://browser-fixture.example"
    private fun transport() = SourceBrowserTransportV56(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
    private fun keyboardVisible(): Boolean {
        var shown = false
        rule.runOnUiThread {
            shown = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }
    private fun renderedWindowHasBodyInk(): Boolean {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: return false
        return try {
            var bodyInk = 0
            for (y in bitmap.height / 6 until bitmap.height * 5 / 6 step 3) {
                for (x in bitmap.width / 20 until bitmap.width * 19 / 20 step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(pixel) < 190 &&
                        android.graphics.Color.green(pixel) < 190 &&
                        android.graphics.Color.blue(pixel) < 190
                    ) bodyInk++
                }
            }
            bodyInk > 80
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun dynamicSourceBuildAndSavedSourceReadingKeepTheBrowserSession(): Unit = runBlocking {
        val sessionCookie = "session_${UUID.randomUUID().toString().replace("-", "")}"
        val text = "这是浏览器渲染后才出现的原创小说正文，灯光照亮了桌边的纸张。".repeat(8)
        fun dynamic(content: String, cookie: Boolean = false): String = """<!doctype html><html><body><header>原创书城</header><a href='/home'>首页</a><main><p>Loading...</p></main><script>
            ${if (cookie) "document.cookie='$sessionCookie=present; path=/; Secure';" else ""}
            setTimeout(function(){document.body.innerHTML = ${BookSourceJsonV36.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(content))};}, 4000);
            </script></body></html>"""
        val requests = mutableListOf<SourceRequestV36>()
        val fixture: (SourceRequestV36) -> String = { request ->
            requests += request
            val html = when {
                request.url.trimEnd('/') == base -> dynamic("<title>Browser fixture</title><form action='/search' method='post'><input name='q'></form>", cookie = true)
                request.url.contains("/search") -> """<html><body><header>原创书城</header><p>Loading...</p><script>
                    setTimeout(function(){document.body.innerHTML = document.cookie.indexOf('$sessionCookie=present')>=0 ? '<ul id="results"><li><a href="/book/1">原创小说</a></li></ul>' : '<p>会话丢失</p>';},4000);
                    </script></body></html>"""
                request.url.endsWith("/book/1") -> dynamic("<h1>原创小说</h1><div id='chapters'><a href='/read/1'>第一章 灯下</a><a href='/read/2'>第二章 清晨</a></div>")
                request.url.endsWith("/read/1") -> dynamic("<a href='/book/1'>原创小说</a><h1>第一章 灯下</h1><div id='content'>$text</div>")
                else -> error("Unexpected fixture request: ${request.url}")
            }
            html
        }
        var modelCalls = 0
        val model = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("This test only generates source rules")
            override suspend fun generateText(prompt: PromptBundle): String = when (++modelCalls) {
                1 -> """{"searchList":"@css:#results li","searchName":"@css:a@text","searchBookUrl":"@css:a@href"}"""
                2 -> """{"infoName":"@css:h1@text","tocList":"@css:#chapters a","tocName":"@css:@text","tocUrl":"@css:@href"}"""
                3 -> """{"contentText":"@css:#content@html"}"""
                else -> """{"exploreUrl":""}"""
            }
        }
        BookSourceBrowserV38.withFixtureSiteV56(fixture) {
            val report = runBlocking { BookSourceAiBuilderV37(model, {}).build(base, "原创小说", useBrowser = true) }
            assertEquals("原创小说", report.bookName)
            assertEquals(2, report.chapterCount)
            assertTrue(report.sample.contains("浏览器渲染"))
            assertEquals("POST", requests.first { it.url.contains("/search") }.method)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val previous = BookSourceStoreV36.load(context)
            val savedSource = report.source.copy(id = "browser-chain-${UUID.randomUUID()}")
            try {
                BookSourceStoreV36.save(context, previous + savedSource, expected = previous)
                val restored = BookSourceStoreV36.load(context).single { it.id == savedSource.id }
                assertTrue(restored.useBrowser)
                val books = searchSourceV36(restored, "原创小说")
                val catalogue = loadBookV36(restored, books.single())
                assertEquals(2, catalogue.second.size)
                val chapter = loadChapterTextV36(restored, catalogue.second.first(), catalogue.second.map { it.url }.toSet())
                assertEquals(text, chapter)
            } finally { BookSourceStoreV36.save(context, previous) }
        }
    }

    @Test fun cancellationInterruptsABrowserWaitAndTheNextRequestCanStillComplete() {
        val browser = transport()
        val source = BookSourceV36("cancel", "Cancel fixture", base, useBrowser = true)
        var cancelled: Throwable? = null
        val worker = Thread {
            try { browser.fixtureDocument(source, SourceRequestV36("$base/pending"), "<p>Loading...</p>") }
            catch (error: Throwable) { cancelled = error }
        }
        worker.start()
        Thread.sleep(1500)
        worker.interrupt()
        worker.join(10_000)
        assertFalse("Cancellation must release the calling thread", worker.isAlive)
        assertTrue(cancelled.toString(), cancelled is java.util.concurrent.CancellationException)
        val doc = browser.fixtureDocument(source, SourceRequestV36("$base/retry"), "<h1>重试完成</h1>")
        assertEquals("重试完成", doc.selectFirst("h1")?.text())
    }
    private fun waitForNode(text: String): android.view.accessibility.AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // WebView exposes virtual descendants that platform text search can omit.
        // Traverse the active foreground tree, keeping exact text and visibility checks.
        fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if ((node.text?.toString() == text || node.contentDescription?.toString() == text) && node.isVisibleToUser) return node
            for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
            return null
        }
        val deadline = android.os.SystemClock.uptimeMillis() + 20_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            find(automation.rootInActiveWindow)?.let { return it }
            Thread.sleep(100)
        }
        deviceWindowEvidenceV46("browser-missing-verification-control")
        val tree = StringBuilder()
        fun describe(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null || tree.length > 30000 || depth > 30) return
            tree.append("  ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                .append(" description=").append(node.contentDescription).append(" visible=").append(node.isVisibleToUser).append('\n')
            for (index in 0 until node.childCount) describe(node.getChild(index), depth + 1)
        }
        describe(automation.rootInActiveWindow)
        val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "reader-qa/browser-accessibility.txt")
        file.writeText(tree.toString())
        error("Browser verification control did not appear: $text\n$tree")
    }

    @Test fun verificationRecreationWaitsForTheUserAndKeepsTheResultingCookie() {
        val browser = transport()
        val source = BookSourceV36("verify", "Legal synthetic verification", base, useBrowser = true)
        val result = java.util.concurrent.atomic.AtomicReference<org.jsoup.nodes.Document?>()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val cookie = "verified_${UUID.randomUUID().toString().replace("-", "")}"
        val worker = Thread {
            try { result.set(browser.fixtureDocument(source, SourceRequestV36("$base/verification"),
                """<title>Just a moment...</title><form id='challenge-form'><h1>Verify you are human</h1>
                <button type='button' onclick="document.cookie='$cookie=present; path=/; Secure';document.body.innerHTML='<h1>验证后的原创目录</h1>'">完成合成验证</button></form>""")) }
            catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            waitForNode("取消验证")
            assertTrue("A verification page must keep extraction pending", worker.isAlive)
            assertNull(result.get())
            waitForNode("完成合成验证")
            deviceWindowEvidenceV46("browser-verification-before-recreation")
            assertTrue(automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90))
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            var landscape = false
            while (!landscape && android.os.SystemClock.uptimeMillis() < deadline) {
                val bounds = android.graphics.Rect()
                automation.rootInActiveWindow?.getBoundsInScreen(bounds)
                landscape = bounds.width() > bounds.height()
                if (!landscape) Thread.sleep(100)
            }
            assertTrue("The visible verification Activity must actually rotate", landscape)
            waitForNode("取消验证")
            assertTrue(worker.isAlive)
            assertTrue(waitForNode("完成合成验证").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            worker.join(15_000)
            assertFalse(worker.isAlive)
            assertNull(failure.get())
            assertEquals("验证后的原创目录", result.get()?.selectFirst("h1")?.text())
            val next = browser.fixtureDocument(source, SourceRequestV36("$base/after-verification"),
                """<body><script>document.body.innerHTML=document.cookie.includes('$cookie=present')?'<h1>会话继续</h1>':'<h1>会话丢失</h1>';</script></body>""")
            assertEquals("会话继续", next.selectFirst("h1")?.text())
        } finally { worker.interrupt(); worker.join(10_000); automation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE) }
    }

    private fun cancelVisibleVerification(useBack: Boolean) {
        val browser = transport()
        val source = BookSourceV36("verify-cancel", "Legal synthetic verification", base, useBrowser = true)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread {
            try { browser.fixtureDocument(source, SourceRequestV36("$base/verification-cancel"),
                "<title>Just a moment...</title><form id='challenge-form'><h1>Verify you are human</h1></form>") }
            catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        try {
            val cancel = waitForNode("取消验证")
            if (useBack) {
                val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
                assertTrue(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            } else assertTrue(cancel.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            worker.join(10_000)
            assertFalse("User cancellation must release the browser request", worker.isAlive)
            assertTrue(failure.get().toString(), failure.get() is java.util.concurrent.CancellationException)
            val retry = browser.fixtureDocument(source, SourceRequestV36("$base/retry-after-verification"), "<h1>取消后重试完成</h1>")
            assertEquals("取消后重试完成", retry.selectFirst("h1")?.text())
        } finally { worker.interrupt(); worker.join(10_000) }
    }

    @Test fun cancellingVerificationWithItsButtonAllowsRetry() = cancelVisibleVerification(false)
    @Test fun backingOutOfVerificationAllowsRetry() = cancelVisibleVerification(true)

    @Test fun rendererExitWhileVerifyingReleasesTheWaitAndRetryKeepsTheProfile() {
        assertTrue("Renderer termination evidence requires the API 35 CI emulator", android.os.Build.VERSION.SDK_INT >= 29)
        val browser = transport()
        val source = BookSourceV36("renderer-exit", "Synthetic renderer recovery", base, useBrowser = true)
        val token = UUID.randomUUID().toString()
        val pid = android.os.Process.myPid()
        val seeded = browser.fixtureDocument(source, SourceRequestV36("$base/renderer-seed"),
            """<h1>异常恢复会话已保存</h1><script>document.cookie='renderer_v57=$token; Max-Age=3600; Path=/; Secure';localStorage.setItem('renderer_v57','$token');</script>""")
        assertEquals("异常恢复会话已保存", seeded.selectFirst("h1")?.text())
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                browser.fixtureDocument(source, SourceRequestV36("$base/renderer-pending"),
                    "<title>Just a moment...</title><form id='challenge-form'><h1>Verify you are human</h1></form>")
                failure.set(AssertionError("A terminated renderer cannot return a document"))
            } catch (error: Throwable) { failure.set(error) }
        }
        worker.start()
        try {
            waitForNode("取消验证")
            assertTrue("Verification must still own a pending request", worker.isAlive)
            deviceWindowEvidenceV46("browser-before-renderer-exit")
            browser.terminateFixtureRendererV57()
            worker.join(10_000)
            assertFalse("Renderer exit must promptly release the waiting request", worker.isAlive)
            assertTrue(failure.get().toString(), failure.get() is java.io.IOException)
            assertTrue(failure.get().toString(), failure.get()?.message?.contains("网页渲染进程已结束") == true)
            assertEquals("Renderer exit must not kill the app", pid, android.os.Process.myPid())
            val retry = browser.fixtureDocument(source, SourceRequestV36("$base/renderer-retry"),
                """<body><script>document.body.innerHTML=(document.cookie.includes('renderer_v57=$token') && localStorage.getItem('renderer_v57')==='$token')?'<h1>新渲染器会话已恢复</h1>':'<h1>会话丢失</h1>';</script></body>""")
            assertEquals("新渲染器会话已恢复", retry.selectFirst("h1")?.text())
            deviceWindowEvidenceV46("browser-after-renderer-retry")
        } finally { worker.interrupt(); worker.join(10_000) }
    }

    /** Dedicated CI invokes seed and restore with a real package force-stop between them. */
    @Test fun browserProfileSurvivesProcessDeath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidence = context.getSharedPreferences("browser_profile_evidence_v56", 0)
        val phase = InstrumentationRegistry.getArguments().getString("browserPhase") ?: "both"
        val source = BookSourceV36("profile", "Persistent synthetic session", base, useBrowser = true)
        if (phase == "both" || phase == "seed") {
            val token = UUID.randomUUID().toString()
            val seeded = transport().fixtureDocument(source, SourceRequestV36("$base/profile-seed"),
                """<h1>会话已保存</h1><script>document.cookie='profile_v56=$token; Max-Age=3600; Path=/; Secure';localStorage.setItem('profile_v56','$token');</script>""")
            assertEquals("会话已保存", seeded.selectFirst("h1")?.text())
            assertTrue(evidence.edit().putString("token", token).putInt("pid", android.os.Process.myPid()).commit())
        }
        if (phase == "both" || phase == "restore") {
            val token = requireNotNull(evidence.getString("token", null))
            if (phase == "restore") assertNotEquals("Profile restore must run in a fresh app process", evidence.getInt("pid", -1), android.os.Process.myPid())
            val restored = transport().fixtureDocument(source, SourceRequestV36("$base/profile-restore"),
                """<body><script>document.body.innerHTML=(document.cookie.includes('profile_v56=$token') && localStorage.getItem('profile_v56')==='$token')?'<h1>持久会话已恢复</h1>':'<h1>会话丢失</h1>';</script></body>""")
            assertEquals("持久会话已恢复", restored.selectFirst("h1")?.text())
        }
        require(phase in setOf("both", "seed", "restore"))
    }

    @Test fun browserGenerationAndRegenerationKeepTheChosenMode() {
        val starts = mutableListOf<Boolean>()
        val state = androidx.compose.runtime.mutableStateOf(OnlineBooksStateV36(aiProviderLabel = "合成测试模型"))
        var cancellations = 0
        rule.setContent {
            AiBookSourceScreenV50(state.value, base, "原创小说",
                onBack = {}, onSiteUrlChange = {}, onTestBookNameChange = {}, onConfigureAi = {},
                onStart = { _, _ -> starts += false }, onStartWithBrowser = { url, keyword ->
                    assertEquals(base, url); assertEquals("原创小说", keyword); starts += true
                }, onCancel = { cancellations++ }, onSave = {})
        }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("浏览器模式生成"))
        rule.onNodeWithText("浏览器模式生成").assertIsEnabled().performClick()
        assertEquals(listOf(true), starts)
        rule.runOnIdle { state.value = state.value.copy(aiReport = AiSourceReportV37(
            BookSourceV36("regenerate", "合法合成书源", base, useBrowser = true), 1, "原创小说", 2, "原创正文。")) }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("重新生成"))
        rule.onNodeWithText("重新生成").performClick()
        assertEquals(listOf(true, true), starts)
        assertEquals(1, cancellations)
    }

    @Test fun leavingActiveAiGenerationCancelsBeforeHeaderAndSystemBack() {
        val state = androidx.compose.runtime.mutableStateOf(
            OnlineBooksStateV36(aiProviderLabel = "合成测试模型", aiRunning = true),
        )
        val events = mutableListOf<String>()
        rule.setContent {
            AiBookSourceScreenV50(
                state = state.value,
                siteUrl = base,
                testBookName = "原创小说",
                onBack = { events += "back" },
                onSiteUrlChange = {},
                onTestBookNameChange = {},
                onConfigureAi = {},
                onStart = { _, _ -> },
                onStartWithBrowser = { _, _ -> },
                onCancel = { events += "cancel" },
                onSave = {},
            )
        }

        rule.onNodeWithContentDescription("返回").performClick()
        rule.runOnIdle {
            assertEquals(listOf("cancel", "back"), events)
            events.clear()
        }
        rule.onNodeWithText(base, useUnmergedTree = true).assertExists()
        rule.onNodeWithText("原创小说", useUnmergedTree = true).assertExists()

        assertTrue(
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            ),
        )
        rule.waitUntil { events.size == 2 }
        rule.runOnIdle {
            assertEquals(listOf("cancel", "back"), events)
            events.clear()
            state.value = state.value.copy(aiRunning = false, aiStopped = true)
        }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("生成已停止"))
        rule.onNodeWithText("生成已停止").assertIsDisplayed()

        assertTrue(
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
            ),
        )
        rule.waitUntil { events.size == 1 }
        rule.runOnIdle {
            assertEquals(listOf("back"), events)
        }
    }

    @Test fun failedAiGenerationRetriesItsBrowserModeFirstAndStillOffersNormalMode() {
        val starts = mutableListOf<Boolean>()
        val state = androidx.compose.runtime.mutableStateOf(
            OnlineBooksStateV36(
                aiProviderLabel = "合成测试模型",
                aiError = "动态正文验证失败",
                aiLastUseBrowser = true,
            ),
        )
        rule.setContent {
            AiBookSourceScreenV50(
                state = state.value,
                siteUrl = base,
                testBookName = "原创小说",
                onBack = {},
                onSiteUrlChange = {},
                onTestBookNameChange = {},
                onConfigureAi = {},
                onStart = { _, _ -> starts += false },
                onStartWithBrowser = { _, _ -> starts += true },
                onCancel = {},
                onSave = {},
            )
        }

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("重试浏览器模式"))
        rule.onNodeWithText("动态正文验证失败").assertIsDisplayed()
        rule.onNodeWithText("重试浏览器模式").assertIsEnabled().performClick()
        rule.onNodeWithText("改用普通模式").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf(true, false), starts) }

        rule.runOnIdle {
            starts.clear()
            state.value = state.value.copy(
                aiError = "目录规则验证失败",
                aiLastUseBrowser = false,
            )
        }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("重试普通模式"))
        rule.onNodeWithText("目录规则验证失败").assertIsDisplayed()
        rule.onNodeWithText("重试普通模式").assertIsEnabled().performClick()
        rule.onNodeWithText("改用浏览器模式").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf(false, true), starts) }
    }

    @Test fun failedValidationOffersCheckpointRetryAndKeepsTransportChoiceExplicit() {
        val starts = mutableListOf<Boolean>()
        rule.setContent {
            AiBookSourceScreenV50(
                state = OnlineBooksStateV36(
                    aiProviderLabel = "合成测试模型",
                    aiSteps = listOf(
                        AiSourceStepV37("分析搜索结果页", ok = true, detail = "已通过实际页面验证", completed = true),
                        AiSourceStepV37("分析书籍页与目录", ok = false, detail = "目录规则未通过", completed = true),
                    ),
                    aiError = "目录规则未通过，已保留通过验证的搜索规则",
                    aiLastUseBrowser = true,
                    aiCanResumeValidatedRules = true,
                ),
                siteUrl = base,
                testBookName = "原创小说",
                onBack = {},
                onSiteUrlChange = {},
                onTestBookNameChange = {},
                onConfigureAi = {},
                onStart = { _, _ -> starts += false },
                onStartWithBrowser = { _, _ -> starts += true },
                onCancel = {},
                onSave = {},
            )
        }

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("保留已通过规则重试（浏览器）"))
        rule.onNodeWithText("保留已通过规则重试（浏览器）").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("改用普通模式").assertIsDisplayed().assertIsEnabled()
        deviceWindowEvidenceV46("v76-ai-validation-checkpoint-retry")

        rule.onNodeWithText("保留已通过规则重试（浏览器）").performClick()
        rule.onNodeWithText("改用普通模式").performClick()
        rule.runOnIdle { assertEquals(listOf(true, false), starts) }
    }

    @Test fun failedAiSourceSaveShowsRetryThenAConfirmedSavedState() {
        val report = AiSourceReportV37(
            BookSourceV36("save-recovery", "合法合成书源", base, enabledExplore = true),
            1,
            "原创小说",
            2,
            "原创正文。",
        )
        val state = androidx.compose.runtime.mutableStateOf(
            OnlineBooksStateV36(
                aiProviderLabel = "合成测试模型",
                aiReport = report,
                aiError = "书源保存失败：隔离夹具拒绝本次写入",
            ),
        )
        var saves = 0
        rule.setContent {
            AiBookSourceScreenV50(
                state = state.value,
                siteUrl = base,
                testBookName = "原创小说",
                onBack = {},
                onSiteUrlChange = {},
                onTestBookNameChange = {},
                onConfigureAi = {},
                onStart = { _, _ -> },
                onStartWithBrowser = { _, _ -> },
                onCancel = {},
                onSave = { saves++ },
            )
        }

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("重试保存书源"))
        rule.onNodeWithText("书源保存失败：隔离夹具拒绝本次写入").assertIsDisplayed()
        rule.onNodeWithText("重试保存书源").assertIsEnabled().performClick()
        rule.runOnIdle {
            assertEquals(1, saves)
            state.value = aiSourceSavedStateV68(state.value, report.source)
        }

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("书源已保存"))
        rule.onNodeWithText("书源已保存").assertIsDisplayed()
        rule.onNodeWithText("“合法合成书源”已写入书源管理，可继续生成或返回使用。").assertIsDisplayed()
        rule.onNodeWithText("书源保存失败：隔离夹具拒绝本次写入").assertDoesNotExist()
        rule.onNodeWithText("开始生成").assertIsEnabled()
        rule.onNodeWithText("浏览器模式生成").assertIsEnabled()
    }

    @Test fun savedAiSourceOffersDirectManagementReturnWithoutRestartingWork() {
        val state = OnlineBooksStateV36(
            aiProviderLabel = "合成测试模型",
            aiSavedSourceId = "discoverable-source",
            aiSavedSourceName = "可发现合成书源",
        )
        var backs = 0
        var cancellations = 0
        val starts = mutableListOf<Boolean>()
        val openedSavedSources = mutableListOf<String>()
        rule.setContent {
            AiBookSourceScreenV50(
                state = state,
                siteUrl = base,
                testBookName = "原创小说",
                onBack = { backs++ },
                onSiteUrlChange = {},
                onTestBookNameChange = {},
                onConfigureAi = {},
                onStart = { _, _ -> starts += false },
                onStartWithBrowser = { _, _ -> starts += true },
                onCancel = { cancellations++ },
                onSave = {},
                onOpenSavedSource = { openedSavedSources += it },
            )
        }

        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("返回书源管理查看"))
        rule.onNodeWithText("书源已保存").assertIsDisplayed()
        rule.onNodeWithText("“可发现合成书源”已写入书源管理，可继续生成或返回使用。").assertIsDisplayed()
        rule.onNodeWithText("返回书源管理查看").assertIsEnabled()
        deviceWindowEvidenceV46("v73-ai-source-saved-management")
        rule.onNodeWithText("返回书源管理查看").performClick()

        rule.runOnIdle {
            assertEquals(0, backs)
            assertEquals(0, cancellations)
            assertTrue(starts.isEmpty())
            assertEquals(listOf("discoverable-source"), openedSavedSources)
        }
    }

    @Test fun savedAiSourceConfirmationRestoresFromSavedStateAndRejectsADeletedIdentity() {
        val app = rule.activity.application as Application
        val previous = BookSourceStoreV36.load(app)
        val saved = BookSourceV36(
            id = "saved-source-v75-${UUID.randomUUID()}",
            name = "进程恢复合成书源",
            baseUrl = "https://saved-state.example.invalid",
        )
        val handle = SavedStateHandle(mapOf(AI_SAVED_SOURCE_ID_KEY_V75 to saved.id))
        var vm: OnlineBooksViewModelV36? = null
        try {
            BookSourceStoreV36.save(app, previous + saved, expected = previous)
            rule.runOnUiThread { vm = OnlineBooksViewModelV36(app, handle) }
            val model = requireNotNull(vm)
            rule.setContent {
                val state by model.state.collectAsState()
                AiBookSourceScreenV50(
                    state = state,
                    siteUrl = base,
                    testBookName = "原创小说",
                    onBack = {},
                    onSiteUrlChange = {},
                    onTestBookNameChange = {},
                    onConfigureAi = {},
                    onStart = { _, _ -> },
                    onStartWithBrowser = { _, _ -> },
                    onCancel = model::cancelAi,
                    onSave = {},
                    onOpenSavedSource = {},
                )
            }

            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("返回书源管理查看"))
            rule.onNodeWithText("书源已保存").assertIsDisplayed()
            rule.onNodeWithText("“${saved.name}”已写入书源管理，可继续生成或返回使用。").assertIsDisplayed()
            assertEquals(saved.id, model.state.value.aiSavedSourceId)
            assertEquals(saved.name, model.state.value.aiSavedSourceName)
            assertEquals(saved.id, handle.get<String>(AI_SAVED_SOURCE_ID_KEY_V75))
            deviceWindowEvidenceV46("v75-ai-source-saved-state-restored")

            rule.runOnUiThread { model.deleteSource(saved.id) }
            rule.waitUntil(5_000) { model.state.value.aiSavedSourceId == null }
            rule.onNodeWithText("书源已保存").assertDoesNotExist()
            rule.onNodeWithText("返回书源管理查看").assertDoesNotExist()
            assertNull(handle.get<String>(AI_SAVED_SOURCE_ID_KEY_V75))
        } finally {
            rule.runOnUiThread { vm?.viewModelScope?.cancel() }
            BookSourceStoreV36.save(app, previous)
        }
    }

    @Test fun savedAiSourceReturnPinsTheExactStoredIdentityInManagement() {
        val saved = BookSourceV36(
            id = "saved-source-v74",
            name = "枝上新月书源",
            baseUrl = "https://saved.example.invalid",
            group = "综合",
        )
        val existing = BookSourceV36(
            id = "existing-source-v74",
            name = "旧书源",
            baseUrl = "https://existing.example.invalid",
            group = "综合",
        )
        val page = androidx.compose.runtime.mutableStateOf("manage")
        val focusId = androidx.compose.runtime.mutableStateOf<String?>(null)
        val opened = mutableListOf<String>()
        var cancellations = 0
        val starts = mutableListOf<Boolean>()
        val aiState = OnlineBooksStateV36(
            aiProviderLabel = "合成测试模型",
            aiSavedSourceId = saved.id,
            aiSavedSourceName = saved.name,
        )

        rule.setContent {
            when (page.value) {
                "ai" -> AiBookSourceScreenV50(
                    state = aiState,
                    siteUrl = base,
                    testBookName = "原创小说",
                    onBack = { page.value = "manage" },
                    onSiteUrlChange = {},
                    onTestBookNameChange = {},
                    onConfigureAi = {},
                    onStart = { _, _ -> starts += false },
                    onStartWithBrowser = { _, _ -> starts += true },
                    onCancel = { cancellations++ },
                    onSave = {},
                    onOpenSavedSource = {
                        focusId.value = it
                        page.value = "manage"
                    },
                )

                else -> BookSourceManageScreenV50(
                    sources = listOf(existing, saved),
                    focusSourceId = focusId.value,
                    onBack = {},
                    onOpenSource = { opened += it.id },
                    onToggleSource = {},
                    onImportSource = {},
                    onAiGenerateSource = { page.value = "ai" },
                )
            }
        }

        rule.onNode(hasSetTextAction()).performTextInput(existing.name)
        rule.onNodeWithText(saved.name).assertDoesNotExist()
        rule.onNodeWithText("AI 生成书源").performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("返回书源管理查看"))
        rule.onNodeWithText("返回书源管理查看").performClick()

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(saved.name).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(5_000) { !keyboardVisible() }
        rule.onNodeWithText("刚保存").assertIsDisplayed()
        rule.onNodeWithText(saved.name).assertIsDisplayed()
        // Compose semantics and IME visibility can settle before SurfaceFlinger presents
        // the replacement page. Preserve only a real window frame containing page ink.
        rule.waitUntil(10_000) { renderedWindowHasBodyInk() }
        deviceWindowEvidenceV46("v74-ai-source-saved-management-focus")
        rule.onNodeWithText(saved.name).performClick()

        rule.runOnIdle {
            assertEquals(listOf(saved.id), opened)
            assertEquals(0, cancellations)
            assertTrue(starts.isEmpty())
        }
    }

}
