package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovelSkillExecutionPlannerTest {
    @Test
    fun `scene structure request uses scene director task`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.STRUCTURE_PLANNING,
            capabilities = listOf(NovelCapability.LONG_STRUCTURE, NovelCapability.SCENE_DIRECTOR),
        )

        assertEquals(AiTaskType.SCENE_DIRECTOR, NovelSkillExecutionPlanner.primaryTask(route))
    }

    @Test
    fun `long story design uses autonomous planner task`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.STORY_DESIGN,
            capabilities = listOf(NovelCapability.LONG_STRUCTURE, NovelCapability.ENSEMBLE_CAST),
        )

        assertEquals(AiTaskType.AUTONOMOUS_PLANNER, NovelSkillExecutionPlanner.primaryTask(route))
    }

    @Test
    fun `continuity review uses editor review task`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.CONTINUITY_REVIEW,
            capabilities = listOf(NovelCapability.CONTINUITY, NovelCapability.ERA_TECH),
        )

        assertEquals(AiTaskType.EDITOR_REVIEW, NovelSkillExecutionPlanner.primaryTask(route))
    }

    @Test
    fun `prose revision uses editor rewrite task`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.PROSE_REVISION,
            capabilities = listOf(NovelCapability.PROSE_EDITOR),
        )

        assertEquals(AiTaskType.EDITOR_REWRITE, NovelSkillExecutionPlanner.primaryTask(route))
    }

    @Test
    fun `reference fact lookup stays on normal conversation model`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.FACT_LOOKUP,
            capabilities = listOf(NovelCapability.REFERENCE_DNA, NovelCapability.REFERENCE_ABSTRACTION),
        )

        assertNull(NovelSkillExecutionPlanner.primaryTask(route))
    }

    @Test
    fun `reference analysis does not accidentally inject writing task skills`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.REFERENCE_ANALYSIS,
            capabilities = listOf(NovelCapability.REFERENCE_ABSTRACTION, NovelCapability.DEIMITATION),
        )

        assertNull(NovelSkillExecutionPlanner.primaryTask(route))
    }

    @Test
    fun `casual chat stays on normal conversation model`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.CASUAL_CHAT,
            capabilities = listOf(NovelCapability.CONVERSATION_CONTEXT),
        )

        assertNull(NovelSkillExecutionPlanner.primaryTask(route))
    }
}
