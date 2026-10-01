package com.xiguli.langhuan.ui

import android.view.KeyEvent
import android.view.WindowManager
import android.util.Log
import android.os.SystemClock
import java.io.File
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.MainActivity
import com.xiguli.langhuan.LanghuanApplication
import com.xiguli.langhuan.data.*
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Launcher navigation, real source cache, actual HTTP gateway, production VMs and Room. */
class CreativeWritingV135DeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun back() = InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun input() = rule.onNode(hasSetTextAction())
    private fun keyboardVisible(): Boolean {
        var shown = false
        rule.runOnUiThread {
            shown = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }
    private fun workspaceItem(text: String): SemanticsNodeInteraction {
        // The workspace is lazy: an off-screen card may not yet have a semantics node.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        return rule.onNodeWithText(text).assertIsDisplayed()
    }
    private fun stableButton(text: String): SemanticsNodeInteraction {
        val node = rule.onNodeWithText(text)
        var previous = ""
        var stableSince = SystemClock.uptimeMillis()
        rule.waitUntil(10_000) {
            val bounds = node.fetchSemanticsNode().boundsInWindow.toString()
            if (bounds != previous || !node.isDisplayed()) {
                previous = bounds
                stableSince = SystemClock.uptimeMillis()
                false
            } else SystemClock.uptimeMillis() - stableSince >= 300
        }
        return node.assertIsDisplayed().assertIsEnabled()
    }
    private fun send(text: String) {
        input().performTextReplacement(text)
        rule.onNodeWithContentDescription("发送").performClick()
    }
    private fun sceneReply() = JSONObject().put("title", "scene-plan").put("content", "先在港口书店讨论来信，再决定如何回应，两个场景按同一日上午顺序连续推进。")
        .put("summary", "故事第一天上午，林舟决定回信。")
        .put("stateChanges", JSONArray().put(JSONObject().put("subject", "林舟").put("field", "港口书店")
            .put("before", "读懂来信").put("after", "回信需要承担责任").put("evidence", "1||上午||紧接上一章||NORMAL||决定回信||林舟"))
            .put(JSONObject().put("subject", "林舟").put("field", "书店门口")
            .put("before", "寄出回信").put("after", "邮差已经走远").put("evidence", "1||上午||约十分钟||NORMAL||找到邮差||林舟")))
        .put("touchedForeshadowingIds", JSONArray()).toString()

    @Test fun onlineReaderCreatesCopyThenSettingsChatScenesAndRepeatedEditsWork(): Unit = runBlocking {
        val base = requireNotNull(InstrumentationRegistry.getArguments().getString("sourceFixtureBase")) { "This device test requires the controlled public source fixture" }
        val context = rule.activity.applicationContext
        val projects = StoryProjectManager(context)
        val repository = PersistentStoryRepository(context)
        val previousProviders = repository.observeProviders().first()
        assertTrue("Fresh emulator must start without private or preconfigured AI providers", previousProviders.isEmpty())
        val previousSources = BookSourceStoreV36.load(context)
        val previousActive = projects.activeStoryId()
        val routes = AiTaskRoutingStore(context)
        val previousRoutes = routes.routes()
        val source = BookSourceV36("creative-v135-${UUID.randomUUID()}", "创作按需受控站", base, contentText = ".content@html")
        BookSourceStoreV36.save(context, previousSources + source)
        val original = projects.createImportedStory(ImportedManuscript("创作完整入口V135", listOf(
            ImportedChapter("第一章 灯火", "", base + "chapter.html"),
            ImportedChapter("第二章 归港", "", base + "chapter-second.html")), source.id, base + "creative-book"))
        val originalId = original.snapshot.novel.id
        var copiedId: String? = null
        var providerId: String? = null
        lateinit var library: LibraryExperienceViewModel
        lateinit var flow: WritingFlowViewModel
        lateinit var chat: ProjectConversationViewModel
        val generatedProse = InstrumentationRegistry.getInstrumentation().context.assets.open("creative-fixture/prose.txt").bufferedReader().use { it.readText().trim() }
        val secondInstruction = "只讨论一下：让林舟的回信语气更温和，先不要改正文。"
        val server = CreativeAiFixtureServerV135 { request ->
            if (request.optString("model") == "gpt-4o-scenes-v135") sceneReply()
            else if (request.optString("model") == "gpt-4o-prose-v135") generatedProse
            else if (request.optString("model") == "gpt-4o-structured-v135") {
                val system = request.getJSONArray("messages").getJSONObject(0).optString("content")
                JSONObject().put("title", if (system.contains("对抗式章节主编委员会")) "PASS" else "第一章 回信")
                    .put("content", if (system.contains("对抗式章节主编委员会")) "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过" else "未提取需要新增的事实，保持既有设定。")
                    .put("summary", "林舟保存旧书，询问交付条件后寄出回信。本章结束时=故事第1天·上午")
                    .put("stateChanges", JSONArray()).put("touchedForeshadowingIds", JSONArray()).toString()
            }
            else if (request.getJSONArray("messages").toString().contains(secondInstruction)) "第二轮已收到：回信可以更温和，正文尚未修改。"
            else "第一轮讨论：让角色先说出自己的顾虑，再作决定。"
        }
        var currentPhase = "setup"
        fun markPhase(next: String) { currentPhase = next; Log.i("CreativeWritingV135", "phase=$next; modelRequests=${server.requests.size}") }
        try {
            markPhase("prepare view models")
            rule.runOnUiThread {
                library = ViewModelProvider(rule.activity)[LibraryExperienceViewModel::class.java]
                flow = ViewModelProvider(rule.activity)[WritingFlowViewModel::class.java]
                chat = ViewModelProvider(rule.activity)[ProjectConversationViewModel::class.java]
            }
            rule.waitUntil(20_000) { library.state.value.stories.any { it.id == originalId } }
            markPhase("open reader")
            rule.onNodeWithText("创作完整入口V135").performClick()
            rule.waitUntil(30_000) { library.state.value.readingChapter?.content?.contains("远处亮起一盏灯") == true }
            markPhase("current chapter loaded")
            val sourceBody = projects.chapterDraft(originalId, 1)!!.content
            assertTrue(projects.chapterDraft(originalId, 2)!!.content.isBlank())
            rule.onNodeWithContentDescription("阅读正文").performTouchInput { click(center) }
            rule.onNodeWithText("详情").performClick()
            // Reader detail actions are fixed, visible controls, not children of a scroll container.
            rule.onNodeWithText("AI 创作").assertIsDisplayed().performClick()
            markPhase("create independent writing copy")
            rule.onNodeWithText("创建副本并进入").performClick()
            rule.waitUntil(20_000) { flow.state.value.ready && flow.state.value.novelId != originalId && chat.state.value.isLoaded }
            markPhase("writing workspace ready")
            copiedId = flow.state.value.novelId
            assertEquals(sourceBody, flow.state.value.draft?.content)
            assertTrue(flow.state.value.snapshot!!.novel.sourceId.isBlank())
            rule.onNodeWithText("先配置 AI 服务").assertExists()
            val unsent = "只讨论一下：这个场景可以怎样写得更自然？"
            input().performTextReplacement(unsent)
            markPhase("keyboard must preserve workspace header")
            rule.waitUntil(10_000) { keyboardVisible() }
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                rule.activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            rule.waitUntil(10_000) { rule.onRoot().fetchSemanticsNode().boundsInWindow.top >= 0f }
            rule.onNodeWithText("章节工作台").assertIsDisplayed()
            rule.onNodeWithText("AI 服务").assertIsDisplayed()
            markPhase("open AI settings")
            val setup = workspaceItem("配置 AI 服务")
            deviceWindowEvidenceV46("v135-writing-settings-with-keyboard")
            setup.performClick()
            rule.onNodeWithText("还没有配置可用服务").assertExists()
            assertEquals(0, server.requests.size)
            // Save a key-free local test provider through the production repository. No external AI account is used.
            providerId = repository.saveProvider(ProviderSaveRequest(name = "本机受控模型", baseUrl = server.baseUrl,
                protocol = ApiProtocol.OPENAI_COMPATIBLE, model = "gpt-4o-chat-v135", supportsJsonMode = false, apiKey = "")).id
            AiTaskType.entries.forEach { routes.setRoute(it, providerId!!, if (it == AiTaskType.SCENE_DIRECTOR) "gpt-4o-scenes-v135" else "gpt-4o-chat-v135") }
            markPhase("return from AI settings")
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(15_000) { flow.aiReady.value }
            markPhase("send first discussion")
            input().assertTextContains(unsent)
            rule.onNodeWithContentDescription("发送").performClick()
            rule.waitUntil(20_000) { !chat.state.value.isBusy && chat.state.value.messages.any { it.text.startsWith("第一轮讨论") } }
            rule.onNodeWithText("第一轮讨论：让角色先说出自己的顾虑，再作决定。").assertIsDisplayed()
            markPhase("send second discussion")
            send(secondInstruction)
            rule.waitUntil(20_000) { !chat.state.value.isBusy && chat.state.value.messages.any { it.text.startsWith("第二轮已收到") } }
            assertEquals(2, server.requests.size)
            val secondMessages = server.requests[1].getJSONArray("messages")
            assertEquals("user", secondMessages.getJSONObject(secondMessages.length() - 1).getString("role"))
            assertTrue(secondMessages.getJSONObject(secondMessages.length() - 1).getString("content").contains(secondInstruction))
            assertEquals(4, ProjectConversationStore(context).load(copiedId!!).size)
            markPhase("plan scenes")
            rule.onNodeWithText("场景").performClick()
            rule.onNode(hasSetTextAction() and hasText("告诉 AI 怎么调整：例如第三场提前到傍晚、不要闪回、让配角更早入场")).performTextReplacement("安排两个上午连续发生的书店场景。")
            stableButton("AI 调整").performClick()
            rule.waitUntil(20_000) { flow.state.value.sceneDirty && !flow.state.value.isPlanningScenes }
            assertEquals("gpt-4o-scenes-v135", server.requests.last().getString("model"))
            assertEquals(2, flow.state.value.workingScenes.size)
            assertEquals(1, projects.chapterDraft(copiedId!!, 1)!!.scenePlan.size)
            markPhase("confirm scene preview")
            // Read the returned preview after the IME closes; do not click through its moving window.
            if (keyboardVisible()) {
                back()
                rule.waitUntil(10_000) { !keyboardVisible() }
            }
            val confirmScenes = stableButton("确认场景")
            val runtime = (rule.activity.application as LanghuanApplication).chapterRunRuntime.state.value
            Log.i("CreativeWritingV135", "before confirm: dirty=${flow.state.value.sceneDirty}; busy=${flow.state.value.busy}; runtimeActive=${runtime.active}; roomScenes=${projects.chapterDraft(copiedId!!, 1)!!.scenePlan.size}")
            deviceWindowEvidenceV46("v135-writing-scene-preview-before-confirm")
            confirmScenes.performClick()
            markPhase("scene confirmation clicked")
            rule.waitUntil(20_000) { !flow.state.value.sceneDirty && !flow.state.value.isSaving }
            assertEquals(2, projects.chapterDraft(copiedId!!, 1)!!.scenePlan.size)
            deviceWindowEvidenceV46("v135-writing-scenes-saved")
            // Android Back first dismisses a visible IME, then closes the actual scene sheet.
            if (keyboardVisible()) {
                back()
                rule.waitUntil(10_000) { !keyboardVisible() }
            }
            back()
            rule.onNodeWithText("本章场景").assertDoesNotExist()
            listOf(AiTaskType.PROSE_AUTHOR, AiTaskType.NOVELIZATION, AiTaskType.EDITOR_REWRITE).forEach {
                routes.setRoute(it, providerId!!, "gpt-4o-prose-v135")
            }
            listOf(AiTaskType.EDITOR_REVIEW, AiTaskType.FACT_EXTRACTION, AiTaskType.AGENT_EXTRACTION,
                AiTaskType.EXECUTION_AUDIT, AiTaskType.AUTONOMOUS_PLANNER).forEach {
                routes.setRoute(it, providerId!!, "gpt-4o-structured-v135")
            }
            markPhase("generate prose")
            send("重写本章正文，写出完整回信经过。")
            rule.waitUntil(60_000) { !flow.state.value.isGenerating && (flow.state.value.result != null || flow.state.value.error != null) }
            markPhase("prose response received")
            val result = requireNotNull(flow.state.value.result) { flow.state.value.error.orEmpty() }
            assertTrue(result.issues.toString(), result.canCommit)
            assertEquals(generatedProse, result.chapter.content)
            workspaceItem("新版本已完成")
            deviceWindowEvidenceV46("v135-writing-generated-preview-controlled-model")
            assertEquals(sourceBody, projects.chapterDraft(copiedId!!, 1)!!.content)
            markPhase("save generated prose")
            stableButton("保存本章").performClick()
            rule.waitUntil(60_000) { !flow.state.value.isSaving && flow.state.value.draft?.content == generatedProse }
            assertEquals(generatedProse, projects.chapterDraft(copiedId!!, 1)!!.content)
            assertTrue(server.requests.any { it.optString("model") == "gpt-4o-prose-v135" && it.optBoolean("stream") })
            assertTrue(server.requests.any { it.optString("model") == "gpt-4o-structured-v135" })
            assertEquals(listOf("gpt-4o-chat-v135", "gpt-4o-chat-v135", "gpt-4o-scenes-v135", "gpt-4o-prose-v135"), server.requests.take(4).map { it.getString("model") })
            assertTrue(server.requests.drop(4).all { it.getString("model") == "gpt-4o-structured-v135" })
            workspaceItem("精修正文 · 保存后仍可反复修改").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("正文编辑").fetchSemanticsNodes().isNotEmpty() }
            markPhase("edit saved prose")
            val firstEdit = "受控创作副本第一次修改：林舟读完来信，走向港口书店。"
            rule.onNode(hasSetTextAction() and hasText(generatedProse)).performTextReplacement(firstEdit)
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(20_000) { flow.state.value.draft?.content == firstEdit }
            workspaceItem("精修正文 · 保存后仍可反复修改").performClick()
            markPhase("edit prose again")
            val finalEdit = "受控创作副本第二次修改：林舟温和地回信，保留了自己的选择。"
            rule.onNode(hasSetTextAction() and hasText(firstEdit)).performTextReplacement(finalEdit)
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(20_000) { flow.state.value.draft?.content == finalEdit }
            assertEquals(finalEdit, projects.chapterDraft(copiedId!!, 1)!!.content)
            // Reenter the same retained editor and leave without typing; a previous close must not lock Back.
            workspaceItem("精修正文 · 保存后仍可反复修改").performClick()
            rule.onNodeWithText("正文编辑").assertIsDisplayed()
            rule.onNodeWithContentDescription("保存并返回").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithText("章节工作台").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(sourceBody, projects.chapterDraft(originalId, 1)!!.content)
            assertTrue(projects.chapterDraft(originalId, 2)!!.content.isBlank())
            assertTrue("Bounded fixture requests: ${server.requests.size}", server.requests.size in 6..12)
            assertTrue(server.failures.toString(), server.failures.isEmpty())
            markPhase("return to reader")
            deviceWindowEvidenceV46("v135-creative-writing-controlled-model")
            rule.onNodeWithContentDescription("返回").performClick()
            rule.waitUntil(20_000) { library.state.value.openedBook?.id == copiedId && library.state.value.readingChapter?.content == finalEdit }
            rule.onNodeWithContentDescription("阅读正文").assertExists()
        } catch (failure: Throwable) {
            val libraryState = runCatching { library.state.value.let { "book=${it.openedBook?.id}, chapter=${it.readingChapter?.chapterNumber}, chars=${it.readingChapter?.content?.length}, loading=${it.loadingChapterNumber}, error=${it.readerLoadError}" } }.getOrDefault("unavailable")
            val writingState = runCatching { flow.state.value.let { "ready=${it.ready}, loading=${it.isLoading}, generating=${it.isGenerating}, saving=${it.isSaving}, busy=${it.busy}, sceneDirty=${it.sceneDirty}, workingScenes=${it.workingScenes.size}, draftScenes=${it.draft?.scenePlan?.size}, message=${it.message}, error=${it.error}" } }.getOrDefault("unavailable")
            val durableScenes = runCatching { copiedId?.let { projects.chapterDraft(it, 1)?.scenePlan?.size } }.getOrNull()
            val runtimeState = (rule.activity.application as LanghuanApplication).chapterRunRuntime.state.value
            val chatState = runCatching { chat.state.value.let { "loaded=${it.isLoaded}, busy=${it.isBusy}, messages=${it.messages.size}, error=${it.error}" } }.getOrDefault("unavailable")
            val diagnostic = "phase=$currentPhase; modelRequests=${server.requests.size}; models=${server.requests.map { it.optString("model") }}; serverFailures=${server.failures.map { it.toString() }}; library=$libraryState; writing=$writingState; durableScenes=$durableScenes; runtimeActive=${runtimeState.active}; chat=$chatState"
            Log.e("CreativeWritingV135", diagnostic, failure)
            runCatching {
                val file = File(context.getExternalFilesDir(null), "reader-qa/v135-writing-failure-state.txt").apply { parentFile!!.mkdirs() }
                file.writeText(diagnostic)
                file.appendText("\n" + runCatching { rule.onAllNodes(isRoot(), useUnmergedTree = true).printToString() }.getOrDefault("Semantics unavailable"))
            }
            runCatching { deviceWindowEvidenceV46("v135-writing-failure") }
            throw AssertionError(diagnostic, failure)
        } finally {
            try { rule.activityRule.scenario.close() } finally {
                server.close()
                providerId?.let { repository.deleteProvider(it) }
                AiTaskType.entries.forEach(routes::clearRoute)
                previousRoutes.values.forEach { routes.setRoute(it.task, it.providerId, it.modelId) }
                BookSourceStoreV36.save(context, previousSources)
                val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
                listOfNotNull(originalId, copiedId).forEach { id ->
                    listOf("chapter_versions", "chapter_state", "memory_chunks", "story_state").forEach { sql.execSQL("DELETE FROM $it WHERE novelId = ?", arrayOf(id)) }
                }
                if (previousActive == null) projects.clearActiveStoryId() else projects.setActiveStoryId(previousActive)
            }
        }
    }
}
