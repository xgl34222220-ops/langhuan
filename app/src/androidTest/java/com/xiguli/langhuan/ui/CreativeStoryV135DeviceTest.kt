package com.xiguli.langhuan.ui

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.xiguli.langhuan.data.*
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.engine.*
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Actual Compose screens, ViewModels, Room providers/books and UniversalAiGateway on a device. */
class CreativeStoryV135DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun extractPreviewSaveChatTwiceAndReturnUsesExplicitModels() = runBlocking {
        val replies = AtomicInteger()
        val server = CreativeAiFixtureServerV135 { request ->
            when (request.getString("model")) {
                EXTRACT -> chapter(characterBlock())
                ROLE -> chapter("合成角色回复${replies.incrementAndGet()}")
                else -> error("Unexpected model: ${request.optString("model")}")
            }
        }
        val fixture = prepare(server)
        val back = AtomicInteger()
        try {
            val vm = showTavern(fixture, back)
            extractAndSave(vm)
            rule.onNodeWithText(NAME).performScrollTo().performClick()
            rule.onNodeWithText("角色卡").assertIsDisplayed()
            rule.onNodeWithText("开始聊天").performClick()
            sendCharacterMessage("第一轮合成问题")
            rule.waitUntil(15_000) { vm.state.value.chats.values.singleOrNull()?.size == 2 }
            sendCharacterMessage("第二轮新要求：让角色检查窗边的合成信封")
            rule.waitUntil(15_000) { vm.state.value.chats.values.singleOrNull()?.size == 4 }
            assertEquals(listOf(EXTRACT, ROLE, ROLE), server.requests.map { it.getString("model") })
            val secondWire = lastUserContent(server.requests[2])
            assertTrue(secondWire.contains("第二轮新要求：让角色检查窗边的合成信封"))
            assertTrue(secondWire.contains("合成角色回复1"))
            rule.onNodeWithText("合成角色回复2").assertIsDisplayed()
            deviceWindowEvidenceV46("v135-character-chat-controlled-model")
            rule.onNodeWithContentDescription("返回人物").performClick()
            rule.onNodeWithText("角色卡").assertIsDisplayed()
            rule.onNodeWithContentDescription("返回").performClick()
            rule.onNodeWithText("进入故事").assertIsDisplayed()
            rule.onNodeWithContentDescription("返回").performClick()
            assertEquals(1, back.get())
            // Reopen through the real archive reader; a displayed bubble alone is not a save check.
            rule.runOnUiThread { vm.open("other-${fixture.id}"); vm.open(fixture.id) }
            assertEquals(NAME, vm.state.value.profiles.single().name)
            assertEquals(4, vm.state.value.chats.values.single().size)
            assertEquals(fixture.original, fixture.projects.loadStory(fixture.id))
            assertTrue(server.failures.toString(), server.failures.isEmpty())
        } finally { cleanup(fixture, server) }
    }

    @Test fun clearingChatWhileHttpReplyIsDelayedCannotResurrectIt() = runBlocking {
        val release = CountDownLatch(1)
        val replied = CountDownLatch(1)
        val server = CreativeAiFixtureServerV135 { request ->
            if (request.getString("model") == EXTRACT) chapter(characterBlock()) else {
                try { check(release.await(15, TimeUnit.SECONDS)); chapter("已经取消的合成回复") }
                finally { replied.countDown() }
            }
        }
        val fixture = prepare(server)
        try {
            val vm = showTavern(fixture)
            extractAndSave(vm)
            rule.onNodeWithText(NAME).performScrollTo().performClick()
            rule.onNodeWithText("开始聊天").performClick()
            sendCharacterMessage("这一条会被清空")
            rule.waitUntil(15_000) { server.requests.size == 2 && vm.state.value.chatting }
            rule.onNodeWithContentDescription("清空聊天").performClick()
            rule.onNodeWithText("清空", substring = false).performClick()
            rule.waitUntil(5_000) { !vm.state.value.chatting && vm.state.value.chats.values.all { it.isEmpty() } }
            release.countDown()
            check(replied.await(5, TimeUnit.SECONDS))
            assertStableAfterResponse {
                assertTrue(vm.state.value.chats.values.all { it.isEmpty() })
                assertNull(vm.state.value.error)
            }
            rule.runOnUiThread { vm.open("other-${fixture.id}"); vm.open(fixture.id) }
            assertTrue(vm.state.value.chats.values.all { it.isEmpty() })
            assertEquals(ROLE, server.requests.last().getString("model"))
        } finally { release.countDown(); cleanup(fixture, server) }
    }

    @Test fun uncachedCurrentChapterExposesLoadFailureAndRetryWithoutCallingAi() = runBlocking {
        val server = CreativeAiFixtureServerV135 { error("No model call is expected before chapter loading") }
        val fixture = prepare(server, body = "")
        val library = mutableStateOf(fixture.library)
        val loads = AtomicInteger()
        try {
            rule.setContent {
                LanghuanStableTheme {
                    TavernNovelCharacterExperienceV3(
                        fixture.book, library.value, true, {}, onLoadCurrentChapter = {
                            loads.incrementAndGet()
                            library.value = library.value.copy(loadingChapterNumber = 1, readerLoadError = null)
                        },
                    )
                }
            }
            rule.onNodeWithText("从当前章开始互动故事").assertIsNotEnabled()
            rule.onNodeWithText("加载当前章正文").performScrollTo().performClick()
            assertEquals(1, loads.get())
            rule.onNodeWithText("正在加载第 1 章…").assertIsNotEnabled()
            rule.runOnUiThread { library.value = library.value.copy(loadingChapterNumber = null, readerLoadError = "合成章节加载失败") }
            rule.onNodeWithText("合成章节加载失败").assertExists()
            rule.onNodeWithText("重试加载当前章").performClick()
            assertEquals(2, loads.get())
            val old = fixture.original.draft
            fixture.projects.cacheOnlineChapter(fixture.id, 1, old.id, old.sourceUrl, BODY, fixture.book.sourceId, fixture.book.sourceBookUrl)
            val cached = fixture.projects.chapterDrafts(fixture.id)
            rule.runOnUiThread { library.value = library.value.copy(chapters = cached, readingChapter = cached.single(), loadingChapterNumber = null, readerLoadError = null) }
            rule.onNodeWithText("从当前章开始互动故事").assertIsEnabled()
            rule.onNodeWithText("快速蒸馏").assertIsEnabled()
            assertTrue(server.requests.isEmpty())
            assertEquals(BODY, fixture.projects.chapterDraft(fixture.id, 1)?.content)
        } finally { cleanup(fixture, server) }
    }

    @Test fun storyUiUsesRoleplayAndCanStopThenContinueTheSameBranch() = runBlocking {
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val replied = CountDownLatch(1)
        val server = CreativeAiFixtureServerV135 {
            when (calls.incrementAndGet()) {
                1 -> chapter("合成场景一：角色走近码头。", "观察岸边\n检查灯光")
                2 -> try { check(release.await(15, TimeUnit.SECONDS)); chapter("不应出现的旧场景") } finally { replied.countDown() }
                else -> chapter("合成场景二：角色检查新的脚印。")
            }
        }
        val fixture = prepare(server)
        lateinit var vm: StoryPlayV3ViewModel
        var stage = "create StoryCore"
        try {
            rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[StoryPlayV3ViewModel::class.java] }
            rule.setContent { LanghuanStableTheme { StoryCoreExperience(fixture.book, fixture.library, true, {}) } }
            rule.waitUntil(15_000) { vm.state.value.active != null }
            val branchId = vm.state.value.active!!.id
            stage = "send first action"
            sendStoryAction("观察合成码头")
            rule.waitUntil(15_000) { vm.state.value.active?.turns?.size == 1 }
            stage = "send held action"
            sendStoryAction("等待一条将取消的回复")
            rule.waitUntil(15_000) { server.requests.size == 2 && vm.state.value.busy }
            stage = "stop held action"
            rule.onNodeWithText("停止生成").performScrollTo().performClick()
            rule.waitUntil(5_000) { !vm.state.value.busy }
            assertEquals(branchId, vm.state.value.active?.id)
            deviceWindowEvidenceV46("v135-story-stopped-controlled-model")
            stage = "send action after stop"
            sendStoryAction("继续检查新的脚印")
            // Preserve the original total 15-second budget; distinguish UI dispatch from HTTP/result handling.
            val deadline = SystemClock.uptimeMillis() + 15_000
            fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1)
            stage = "third HTTP request must arrive"
            rule.waitUntil(remaining()) { server.requests.size >= 3 || vm.state.value.error != null }
            assertNull("New action failed before its HTTP request", vm.state.value.error)
            assertEquals("The new action must use the same branch", branchId, vm.state.value.active?.id)
            assertTrue("The third request must contain the new action", lastUserContent(server.requests[2]).contains("继续检查新的脚印"))
            stage = "third response must become the second turn"
            rule.waitUntil(remaining()) { vm.state.value.active?.turns?.size == 2 || vm.state.value.error != null }
            assertNull("New action returned an error", vm.state.value.error)
            assertEquals(2, vm.state.value.active?.turns?.size)
            assertEquals("继续检查新的脚印", vm.state.value.active!!.turns.last().player)
            deviceWindowEvidenceV46("v135-story-resumed-controlled-model")
            stage = "release cancelled response and verify isolation"
            release.countDown(); check(replied.await(5, TimeUnit.SECONDS))
            assertStableAfterResponse {
                assertEquals(2, vm.state.value.active?.turns?.size)
                assertFalse(vm.state.value.active!!.turns.any { it.narration.contains("不应出现") })
            }
            assertEquals(listOf(ROLE, ROLE, ROLE), server.requests.map { it.getString("model") })
            assertTrue(lastUserContent(server.requests.last()).contains("继续检查新的脚印"))
            rule.runOnUiThread { vm.open("other-${fixture.id}", 1, "other", "source"); vm.open(fixture.id, 1, "合成章", BODY) }
            assertEquals(2, vm.state.value.active?.turns?.size)
            assertEquals(fixture.original, fixture.projects.loadStory(fixture.id))
        } catch (error: Throwable) {
            val diagnostic = storyFailureEvidence(stage, runCatching { vm }.getOrNull(), server,
                "callbacks=${calls.get()}, heldRelease=${release.count}, heldReplied=${replied.count}")
            throw AssertionError("StoryCore failed at '$stage': $diagnostic", error)
        } finally { release.countDown(); cleanup(fixture, server) }
    }

    private fun showTavern(f: Fixture, back: AtomicInteger = AtomicInteger()): TavernNovelCharacterViewModelV3 {
        lateinit var vm: TavernNovelCharacterViewModelV3
        rule.runOnUiThread { vm = ViewModelProvider(rule.activity)[TavernNovelCharacterViewModelV3::class.java] }
        // Deliberately false: saved Room configuration is authoritative over stale Studio form state.
        rule.setContent { LanghuanStableTheme { TavernNovelCharacterExperienceV3(f.book, f.library, false, { error("Saved fixture provider must be usable") }, onBack = { back.incrementAndGet() }) } }
        rule.waitUntil(15_000) { vm.state.value.novelId == f.id && vm.aiReady.value }
        return vm
    }

    private fun extractAndSave(vm: TavernNovelCharacterViewModelV3) {
        rule.onNodeWithText("快速蒸馏").performScrollTo().performClick()
        rule.waitUntil(15_000) { vm.state.value.preview.size == 1 }
        assertTrue(vm.state.value.profiles.isEmpty())
        rule.onNodeWithText("蒸馏到 1 个人物").assertIsDisplayed()
        deviceWindowEvidenceV46("v135-character-preview-controlled-model")
        rule.onNodeWithText("保存 1 个").performClick()
        rule.waitUntil(5_000) { vm.state.value.preview.isEmpty() && vm.state.value.profiles.size == 1 }
    }

    private fun sendCharacterMessage(text: String) {
        rule.onNode(hasSetTextAction()).performTextInput(text)
        rule.onNodeWithContentDescription("发送").performClick()
    }
    private fun sendStoryAction(text: String) {
        rule.onNode(hasSetTextAction()).assertIsEnabled().performTextReplacement(text).assertTextContains(text)
        rule.onNodeWithContentDescription("发送故事动作").assertIsEnabled().assertIsDisplayed().performClick()
    }

    /** Capture before cleanup: screenshot is the full device surface, not a Compose crop. */
    private fun storyFailureEvidence(stage: String, vm: StoryPlayV3ViewModel?, server: CreativeAiFixtureServerV135, extra: String): String {
        val state = vm?.state?.value
        val summary = "novel=${state?.novelId}, active=${state?.active?.id}, runtime=${state?.runtime?.sessionId}, " +
            "busy=${state?.busy}, turns=${state?.active?.turns?.size}, error=${state?.error}, notice=${state?.notice}, " +
            "requests=${server.requests.size}, completed=${server.completedResponses.get()}, serverFailures=${server.failures}; $extra"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "reader-qa").apply { mkdirs() }
        val file = File(dir, "v135-story-failure-diagnostics.txt")
        // Save state first even if the emulator/screen capture later fails.
        runCatching { file.writeText("stage=$stage\n$summary\n\nturns=${state?.active?.turns}\n\n" +
            server.requests.mapIndexed { index, request -> "REQUEST ${index + 1}: $request" }.joinToString("\n\n")) }
        runCatching { deviceWindowEvidenceV46("v135-story-failure") }
            .onFailure { runCatching { file.appendText("\nScreenshot/window capture failed: $it\n") } }
        runCatching { rule.onRoot(useUnmergedTree = true).printToString() }
            .onSuccess { runCatching { file.appendText("\nUNMERGED SEMANTICS\n$it\n") } }
            .onFailure { runCatching { file.appendText("\nSemantics capture failed: $it\n") } }
        runCatching {
            instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/reader-qa").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/reader-qa/${file.name}").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
        return summary
    }

    /** The held HTTP callback has returned; give queued IO/main-thread completions a bounded drain window. */
    private fun assertStableAfterResponse(checkState: () -> Unit) {
        val until = SystemClock.uptimeMillis() + 500
        do { rule.runOnIdle(checkState); Thread.sleep(25) } while (SystemClock.uptimeMillis() < until)
    }

    private suspend fun prepare(server: CreativeAiFixtureServerV135, body: String = BODY): Fixture {
        val context = rule.activity.applicationContext
        val repository = PersistentStoryRepository(context)
        val projects = StoryProjectManager(context)
        val defaultBefore = repository.observeProviders().first().firstOrNull { it.isDefault }?.id
        val routes = AiTaskRoutingStore(context)
        val routesBefore = routes.routes()
        val activeBefore = projects.activeStoryId()
        val provider = repository.saveProvider(ProviderSaveRequest(
            name = "本机合成创作测试", baseUrl = server.baseUrl, protocol = ApiProtocol.OPENAI_COMPATIBLE,
            model = "fixture-default", supportsJsonMode = true, apiKey = "", makeDefault = true,
        ))
        try {
            routes.setRoute(AiTaskType.CHARACTER_EXTRACTION, provider.id, EXTRACT)
            routes.setRoute(AiTaskType.ROLEPLAY, provider.id, ROLE)
            val url = "https://creative-fixture.invalid/${UUID.randomUUID()}"
            val original = projects.createImportedStory(ImportedManuscript("合成创作验证", listOf(ImportedChapter("合成第一章", body, "$url/1")), "creative-fixture", url))
            val n = original.snapshot.novel
            val book = ReaderBookUi(n.id, n.title, n.genre, n.premise, n.theme, n.coverPath, n.currentWords, n.targetWords, n.currentChapter, 0L, n.sourceId, n.sourceBookUrl)
            val chapters = projects.chapterDrafts(n.id)
            return Fixture(context, projects, repository, provider.id, defaultBefore, routesBefore, activeBefore, original, book, LibraryExperienceState(openedBook = book, chapters = chapters, readingChapter = chapters.single()))
        } catch (error: Throwable) {
            server.close()
            repository.deleteProvider(provider.id)
            defaultBefore?.let { repository.setDefaultProvider(it) }
            listOf(AiTaskType.CHARACTER_EXTRACTION, AiTaskType.ROLEPLAY).forEach { task ->
                val old = routesBefore[task]
                if (old == null) routes.clearRoute(task) else routes.setRoute(task, old.providerId, old.modelId)
            }
            if (activeBefore == null) projects.clearActiveStoryId() else projects.setActiveStoryId(activeBefore)
            throw error
        }
    }

    private suspend fun cleanup(f: Fixture, server: CreativeAiFixtureServerV135) {
        try { rule.activityRule.scenario.close() } finally {
            server.close()
            f.repository.deleteProvider(f.providerId)
            f.defaultBefore?.let { f.repository.setDefaultProvider(it) }
            val routes = AiTaskRoutingStore(f.context)
            listOf(AiTaskType.CHARACTER_EXTRACTION, AiTaskType.ROLEPLAY).forEach { task ->
                val old = f.routesBefore[task]
                if (old == null) routes.clearRoute(task) else routes.setRoute(task, old.providerId, old.modelId)
            }
            val sql = LanghuanDatabase.get(f.context).openHelper.writableDatabase
            listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(f.id)) }
            listOf(f.id, "other-${f.id}").forEach { id ->
                listOf("tavern_novel_character_v3", "story_play", "story_runtime_v3").forEach { directory -> File(f.context.filesDir, "$directory/$id.json").delete() }
            }
            if (f.activeBefore == null) f.projects.clearActiveStoryId() else f.projects.setActiveStoryId(f.activeBefore)
        }
    }

    private data class Fixture(val context: Context, val projects: StoryProjectManager, val repository: PersistentStoryRepository, val providerId: String,
        val defaultBefore: String?, val routesBefore: Map<AiTaskType, TaskModelRoute>, val activeBefore: String?, val original: PersistedStory,
        val book: ReaderBookUi, val library: LibraryExperienceState) { val id get() = book.id }

    private fun chapter(content: String, summary: String = "") = JSONObject().put("title", "合成章节").put("content", content).put("summary", summary)
        .put("stateChanges", JSONArray()).put("touchedForeshadowingIds", JSONArray()).toString()
    private fun lastUserContent(request: JSONObject): String {
        val messages = request.getJSONArray("messages")
        return (messages.length() - 1 downTo 0).map { messages.getJSONObject(it) }.first { it.getString("role") == "user" }.getString("content")
    }
    private fun characterBlock() = "<CHARACTER>\nname=$NAME\nidentity=合成码头守望者\npersonality=谨慎\ndialogueExamples=先看看窗边。\nevidence=1~身份~林舟守在合成码头。\n</CHARACTER>"
    private companion object {
        const val NAME = "林舟（合成）"
        const val BODY = "林舟守在合成码头。他指着窗边说：先看看窗边。这是设备测试专用的原创合成正文。"
        const val EXTRACT = "fixture-extract"
        const val ROLE = "fixture-role"
    }
}
