package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SepiaSkillCatalogTest {
    @Test
    fun `sepia adaptation tracks v070 and current upstream revision`() {
        val sepia = WritingSkillCatalog.all.first { it.id == SepiaNarrativeEngine.SKILL_ID }

        assertEquals("0.7.0-adapted", sepia.version)
        assertEquals(SepiaNarrativeEngine.UPSTREAM_REVISION, sepia.sourceRevision)
        assertEquals("MIT", sepia.license)
        assertTrue(AiTaskType.AUTONOMOUS_PLANNER in sepia.defaultTasks)
    }

    @Test
    fun `sepia review is diagnostic and Chinese calibrated`() {
        val sepia = WritingSkillCatalog.all.first { it.id == SepiaNarrativeEngine.SKILL_ID }
        val guidance = WritingSkillCatalog.guidance(sepia, AiTaskType.EDITOR_REVIEW)

        assertTrue(guidance.contains("operation=review"))
        assertTrue(guidance.contains("只诊断不修改"))
        assertTrue(guidance.contains("中文校准"))
        assertTrue(guidance.contains("不要把标点密度"))
        assertTrue(guidance.contains("绝不根据正文猜作者模型"))
    }

    @Test
    fun `sepia operations map to distinct task contracts`() {
        assertEquals(SepiaOperation.WRITE, SepiaNarrativeEngine.operationFor(AiTaskType.PROSE_AUTHOR))
        assertEquals(SepiaOperation.REVIEW, SepiaNarrativeEngine.operationFor(AiTaskType.EDITOR_REVIEW))
        assertEquals(SepiaOperation.REFACTOR, SepiaNarrativeEngine.operationFor(AiTaskType.EDITOR_REWRITE))
        assertEquals(SepiaOperation.RECREATE, SepiaNarrativeEngine.operationFor(AiTaskType.NOVELIZATION))
    }
}
