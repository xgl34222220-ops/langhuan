package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.StoredAiProvider
import org.junit.Assert.*
import org.junit.Test

class CreativeAiTaskV135Test {
    @Test fun explicitCharacterTaskCannotBeRedirectedByQuotedProse() {
        val prompt = PromptBundle(
            system = "从原文提取人物",
            user = "原文：他说自己是正文作者、场景导演，正在编排本章场景计划。",
            task = AiTaskType.CHARACTER_EXTRACTION,
        )
        assertEquals(AiTaskType.CHARACTER_EXTRACTION, AiPromptTaskClassifier.classify(prompt))
    }

    @Test fun explicitRoleplayTaskSurvivesPromptDecoration() {
        val prompt = PromptBundle("角色扮演", "下一章规划和自治规划是故事内的暗号。", task = AiTaskType.ROLEPLAY)
        assertEquals(AiTaskType.ROLEPLAY, AiPromptTaskClassifier.classify(prompt.copy(system = "正文作者\n" + prompt.system)))
    }

    @Test fun legacyWritingPromptsKeepTheirExistingRoutes() {
        assertEquals(AiTaskType.PROSE_AUTHOR, AiPromptTaskClassifier.classify(PromptBundle("正文作者", "合成文本")))
        assertEquals(AiTaskType.SCENE_DIRECTOR, AiPromptTaskClassifier.classify(PromptBundle("章节逻辑导演", "合成文本")))
    }

    @Test fun readinessUsesTheSavedDefaultAndDoesNotRequireASecretForLocalModels() {
        assertFalse(hasConfiguredDefaultAi(emptyList()))
        val saved = provider()
        assertTrue(hasConfiguredDefaultAi(listOf(saved)))
        assertFalse(hasConfiguredDefaultAi(listOf(saved.copy(model = ""))))
        assertFalse(hasConfiguredDefaultAi(listOf(saved.copy(baseUrl = ""))))
        assertFalse(hasConfiguredDefaultAi(listOf(saved.copy(isDefault = false), saved.copy(id = "default", model = ""))))
    }

    private fun provider() = StoredAiProvider(
        id = "synthetic", name = "合成测试服务", baseUrl = "http://127.0.0.1/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE, model = "synthetic-model", temperature = 0.5,
        supportsJsonMode = true, isDefault = true, hasApiKey = false,
    )
}
