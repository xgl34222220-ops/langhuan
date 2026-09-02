package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SepiaSkillCatalogTest {
    @Test
    fun `sepia adaptation tracks v041 and planner task`() {
        val sepia = WritingSkillCatalog.all.first { it.id == "sepia-fiction" }

        assertEquals("0.4.1-adapted", sepia.version)
        assertEquals("MIT", sepia.license)
        assertTrue(AiTaskType.AUTONOMOUS_PLANNER in sepia.defaultTasks)
        assertTrue(WritingSkillCatalog.guidance(sepia, AiTaskType.EDITOR_REVIEW).contains("A→E"))
        assertTrue(WritingSkillCatalog.guidance(sepia, AiTaskType.EDITOR_REVIEW).contains("只诊断，不直接改"))
    }
}
