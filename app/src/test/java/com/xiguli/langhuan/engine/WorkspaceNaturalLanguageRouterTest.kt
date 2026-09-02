package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceNaturalLanguageRouterTest {
    @Test
    fun `plain character discussion stays discussion only`() {
        val plan = WorkspaceNaturalLanguageRouter.route("聊聊周衍现在的动机是不是太弱了？")

        assertEquals(listOf(WorkspaceNaturalAction.DISCUSS), plan.actions)
        assertFalse(plan.mutatesWorkingDraft)
    }

    @Test
    fun `explicit scene move routes to scene planner`() {
        val plan = WorkspaceNaturalLanguageRouter.route("第三场提前到晚上，让配角更早入场")

        assertEquals(listOf(WorkspaceNaturalAction.SCENE_PLAN), plan.actions)
        assertTrue(plan.hasSceneMutation)
    }

    @Test
    fun `explicit prose tone change routes to prose`() {
        val plan = WorkspaceNaturalLanguageRouter.route("把周衍写得更警惕一点，语气别那么松")

        assertEquals(listOf(WorkspaceNaturalAction.PROSE), plan.actions)
        assertTrue(plan.hasProseMutation)
    }

    @Test
    fun `compound instruction becomes scene prose review sequence`() {
        val plan = WorkspaceNaturalLanguageRouter.route("第三场提前到晚上，然后把周衍写得更警惕一点，顺便检查这章有没有和前面冲突")

        assertEquals(
            listOf(
                WorkspaceNaturalAction.SCENE_PLAN,
                WorkspaceNaturalAction.PROSE,
                WorkspaceNaturalAction.REVIEW,
            ),
            plan.actions,
        )
        assertEquals("场景重排 → 正文生成/重写 → 连续性审校", plan.summary)
    }

    @Test
    fun `continuity question routes to non mutating review`() {
        val plan = WorkspaceNaturalLanguageRouter.route("第三章是不是逻辑不通？检查一下前后有没有矛盾")

        assertEquals(listOf(WorkspaceNaturalAction.REVIEW), plan.actions)
        assertFalse(plan.mutatesWorkingDraft)
    }

    @Test
    fun `era technology question routes to review`() {
        val plan = WorkspaceNaturalLanguageRouter.route("2026年这里还写座机来电显示是不是不合理？")

        assertEquals(listOf(WorkspaceNaturalAction.REVIEW), plan.actions)
    }

    @Test
    fun `world canon rewrite request becomes proposal not direct write`() {
        val plan = WorkspaceNaturalLanguageRouter.route("把主角的核心设定改成不死之身")

        assertEquals(listOf(WorkspaceNaturalAction.CANON_PROPOSAL), plan.actions)
        assertTrue(plan.requestsCanonProposal)
        assertFalse(plan.mutatesWorkingDraft)
    }

    @Test
    fun `canon proposal stops downstream prose until confirmed`() {
        val plan = WorkspaceNaturalLanguageRouter.route("把世界规则改成只有午夜才能触发，然后顺便重写这一章")

        assertEquals(listOf(WorkspaceNaturalAction.CANON_PROPOSAL), plan.actions)
        assertFalse(plan.hasProseMutation)
    }
}
