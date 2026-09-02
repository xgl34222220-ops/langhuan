package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelSkillRouterTest {
    @Test
    fun `reference ensemble story routes research deimitation world cast and structure`() {
        val route = NovelSkillRouter.route(
            NovelRouteInput(
                message = "我想写一本类似《神秘复苏》的群像灵异长篇，但不要照搬它的核心设定。",
                hasSelectedReferences = true,
            )
        )

        assertEquals(NovelIntent.STORY_DESIGN, route.intent)
        assertTrue(NovelCapability.REFERENCE_DNA in route.capabilities)
        assertTrue(NovelCapability.REFERENCE_ABSTRACTION in route.capabilities)
        assertTrue(NovelCapability.DEIMITATION in route.capabilities)
        assertTrue(NovelCapability.WORLD_CANON in route.capabilities)
        assertTrue(NovelCapability.ENSEMBLE_CAST in route.capabilities)
        assertTrue(NovelCapability.LONG_STRUCTURE in route.capabilities)
    }

    @Test
    fun `modern era contradiction routes continuity timeline era tech and editor`() {
        val route = NovelSkillRouter.route(
            NovelRouteInput(
                message = "第三章这里不合理，明明是2026年还写座机来电显示，帮我改一下。",
                hasFoundation = true,
            )
        )

        assertEquals(NovelIntent.CONTINUITY_REVIEW, route.intent)
        assertTrue(NovelCapability.CONTINUITY in route.capabilities)
        assertTrue(NovelCapability.TIMELINE in route.capabilities)
        assertTrue(NovelCapability.ERA_TECH in route.capabilities)
        assertTrue(NovelCapability.PROSE_EDITOR in route.capabilities)
    }

    @Test
    fun `simple character naming does not activate full planning stack`() {
        val route = NovelSkillRouter.route(NovelRouteInput(message = "主角叫什么名字好？"))

        assertEquals(NovelIntent.CHARACTER_DESIGN, route.intent)
        assertTrue(NovelCapability.CHARACTER_STATE in route.capabilities)
        assertFalse(NovelCapability.WORLD_CANON in route.capabilities)
        assertFalse(NovelCapability.LONG_STRUCTURE in route.capabilities)
        assertFalse(NovelCapability.SCENE_DIRECTOR in route.capabilities)
    }

    @Test
    fun `reference fact lookup reads selected dna without turning into story design`() {
        val route = NovelSkillRouter.route(
            NovelRouteInput(
                message = "他们两个作品的主角能力分别是什么？",
                hasSelectedReferences = true,
                referenceFactQuestion = true,
            )
        )

        assertEquals(NovelIntent.FACT_LOOKUP, route.intent)
        assertTrue(NovelCapability.REFERENCE_DNA in route.capabilities)
        assertTrue(NovelCapability.REFERENCE_ABSTRACTION in route.capabilities)
        assertFalse(NovelCapability.WORLD_CANON in route.capabilities)
        assertFalse(NovelCapability.LONG_STRUCTURE in route.capabilities)
    }

    @Test
    fun `plain conversation stays minimal`() {
        val route = NovelSkillRouter.route(NovelRouteInput(message = "这个方向我觉得还行，继续聊。"))

        assertEquals(NovelIntent.CASUAL_CHAT, route.intent)
        assertEquals(listOf(NovelCapability.CONVERSATION_CONTEXT), route.capabilities)
    }

    @Test
    fun `attachment enables reader but does not infer every capability`() {
        val route = NovelSkillRouter.route(
            NovelRouteInput(
                message = "看看这个文件有什么问题。",
                attachmentPurposes = listOf("作品设定"),
                hasFoundation = true,
            )
        )

        assertTrue(NovelCapability.ATTACHMENT_READER in route.capabilities)
        assertFalse(NovelCapability.ENSEMBLE_CAST in route.capabilities)
        assertFalse(NovelCapability.SCENE_DIRECTOR in route.capabilities)
    }
}
