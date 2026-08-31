package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingSkillsTest {
    @Test
    fun skillDecoratorInjectsCraftLayerWithoutTouchingPayload() = runBlocking {
        val capture = CapturingGateway()
        val skill = WritingSkillCatalog.all.first { it.id == "story-long-write" }
        val gateway = SkillAwareAiGateway(capture, AiTaskType.PROSE_AUTHOR, listOf(skill))
        val prompt = PromptBundle(
            system = "你是正文作者",
            user = "写第一章",
            attachments = listOf(PromptAttachment("setting.md", "text/markdown", "YWJj")),
            messages = listOf(PromptMessage("user", "保持第一人称")),
            jsonMode = false,
        )

        assertEquals("ok", gateway.generateText(prompt))
        val decorated = capture.lastPrompt!!
        assertTrue(decorated.system.contains("C·写作 Skill"))
        assertTrue(decorated.system.contains("不能覆盖用户明确要求"))
        assertTrue(decorated.system.contains("长篇网文写作"))
        assertEquals(prompt.user, decorated.user)
        assertEquals(prompt.attachments, decorated.attachments)
        assertEquals(prompt.messages, decorated.messages)
        assertEquals(prompt.jsonMode, decorated.jsonMode)
    }

    @Test
    fun emptySkillListLeavesPromptUntouched() = runBlocking {
        val capture = CapturingGateway()
        val gateway = SkillAwareAiGateway(capture, AiTaskType.PROSE_AUTHOR, emptyList())
        val prompt = PromptBundle("system", "user", jsonMode = false)
        gateway.generateText(prompt)
        assertSame(prompt, capture.lastPrompt)
    }

    @Test
    fun recommendedBindingsDoNotLetAntiAiSkillControlPlanning() {
        val anti = WritingSkillCatalog.all.first { it.id == "avoid-ai-writing" }
        val binding = WritingSkillCatalog.defaultBinding(anti)
        assertTrue(AiTaskType.NOVELIZATION in binding.tasks)
        assertTrue(AiTaskType.EDITOR_REWRITE in binding.tasks)
        assertFalse(AiTaskType.AUTONOMOUS_PLANNER in anti.supportedTasks)
        assertFalse(AiTaskType.SCENE_DIRECTOR in binding.tasks)
    }

    @Test
    fun sepiaSkillKeepsAttributionAndUsesTheThreePassMethod() {
        val sepia = WritingSkillCatalog.all.first { it.id == "sepia-fiction" }

        assertEquals("MIT", sepia.license)
        assertEquals("Nanako Tsai", sepia.author)
        assertEquals("https://github.com/Nanako0129/sepia", sepia.sourceUrl)
        assertEquals("94f6dc2fc1eaff50570e735f2ae397eedbd49782", sepia.sourceRevision)

        val guidance = WritingSkillCatalog.guidance(sepia, AiTaskType.EDITOR_REVIEW)
        assertTrue(guidance.contains("叙事结构"))
        assertTrue(guidance.contains("段落/信息推进"))
        assertTrue(guidance.contains("词句表面"))
        assertTrue(guidance.contains("3-5"))
        assertTrue(guidance.contains("绝不为了增加“人味”发明具体信息"))
    }

    @Test
    fun sepiaIsEnabledForWritingAndEditingButNotAutonomousPlanningByDefault() {
        val sepia = WritingSkillCatalog.all.first { it.id == "sepia-fiction" }
        val binding = WritingSkillCatalog.defaultBinding(sepia)

        assertTrue(AiTaskType.PROSE_AUTHOR in binding.tasks)
        assertTrue(AiTaskType.EDITOR_REVIEW in binding.tasks)
        assertTrue(AiTaskType.FULL_BOOK_EDITOR in binding.tasks)
        assertTrue(AiTaskType.AUTONOMOUS_PLANNER in sepia.supportedTasks)
        assertFalse(AiTaskType.AUTONOMOUS_PLANNER in binding.tasks)
    }

    private class CapturingGateway : AiGateway {
        var lastPrompt: PromptBundle? = null

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter = error("not used")

        override suspend fun generateText(prompt: PromptBundle): String {
            lastPrompt = prompt
            return "ok"
        }
    }
}
