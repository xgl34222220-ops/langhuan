package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.StoredAiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTaskRoutingTest {
    @Test
    fun `classifier maps core chapter prompts to dedicated task lanes`() {
        assertEquals(
            AiTaskType.PROSE_AUTHOR,
            AiPromptTaskClassifier.classify(PromptBundle("你现在只担任长篇小说的“正文作者”。", "正文目标", jsonMode = false)),
        )
        assertEquals(
            AiTaskType.EDITOR_REWRITE,
            AiPromptTaskClassifier.classify(PromptBundle("正文作者。\n这是第二稿。", "【主编退回意见】重写", jsonMode = false)),
        )
        assertEquals(
            AiTaskType.NOVELIZATION,
            AiPromptTaskClassifier.classify(PromptBundle("你是中文长篇小说的“小说化重构编辑”。", "重构正文", jsonMode = false)),
        )
        assertEquals(
            AiTaskType.EDITOR_REVIEW,
            AiPromptTaskClassifier.classify(PromptBundle("你是“琅嬛”的对抗式章节主编委员会。", "审核正文")),
        )
        assertEquals(
            AiTaskType.FACT_EXTRACTION,
            AiPromptTaskClassifier.classify(PromptBundle("你是“琅嬛”的章节事实提取器。", "提取摘要")),
        )
    }

    @Test
    fun `classifier separates agent fullbook planning audit and scene tasks`() {
        assertEquals(
            AiTaskType.AGENT_EXTRACTION,
            AiPromptTaskClassifier.classify(PromptBundle("你是“琅嬛”长篇小说创作 Agent，本次只复盘刚完成章节。", "章节复盘")),
        )
        assertEquals(
            AiTaskType.FULL_BOOK_EDITOR,
            AiPromptTaskClassifier.classify(PromptBundle("你是“琅嬛”长篇小说创作 Agent，本次是全书巡检。", "从全书尺度巡检")),
        )
        assertEquals(
            AiTaskType.EXECUTION_AUDIT,
            AiPromptTaskClassifier.classify(PromptBundle("比较计划与实际并执行审计。", "给出执行完成度")),
        )
        assertEquals(
            AiTaskType.AUTONOMOUS_PLANNER,
            AiPromptTaskClassifier.classify(PromptBundle("你负责长篇自治规划与滚动计划。", "补足未来 6 章")),
        )
        assertEquals(
            AiTaskType.SCENE_DIRECTOR,
            AiPromptTaskClassifier.classify(PromptBundle("你是场景导演。", "编排本章场景计划")),
        )
    }

    @Test
    fun `capability inference stays conservative and blocks unsupported transport`() {
        val normal = provider(baseUrl = "https://relay.example.com/v1", model = "claude-4-opus-thinking")
        val profile = ModelCapabilityProfiler.infer(normal, normal.model)
        assertEquals(200_000, profile.contextWindow)
        assertTrue(profile.reasoning)
        assertTrue(profile.longText)
        assertTrue(profile.supportsStreaming)
        assertTrue(profile.transportSupported)

        val unknown = ModelCapabilityProfiler.infer(normal.copy(model = "custom-fiction-model"), "custom-fiction-model")
        assertEquals(0, unknown.contextWindow)
        assertEquals("未知", unknown.contextLabel)

        val mixed = provider(baseUrl = "https://opencode.ai/zen/go", model = "gpt-5.6-luna")
        val unsupported = ModelCapabilityProfiler.infer(mixed, mixed.model)
        assertFalse(unsupported.transportSupported)
        assertTrue(ModelCapabilityProfiler.warnings(AiTaskType.PROSE_AUTHOR, unsupported).any { "协议" in it })
    }

    private fun provider(baseUrl: String, model: String) = StoredAiProvider(
        id = "provider-test",
        name = "测试服务",
        baseUrl = baseUrl,
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        model = model,
        temperature = 0.72,
        supportsJsonMode = true,
        isDefault = true,
        hasApiKey = true,
    )
}
