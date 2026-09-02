package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRuntimeSkillPlannerTest {
    @Test
    fun `project plan activates real generation and post commit engines`() {
        val plan = ProjectRuntimeSkillPlanner.build(snapshot(), draft(), referenceDnaCount = 2)
        val capabilities = plan.steps.map { it.capability }.toSet()

        assertTrue(ProjectRuntimeCapability.CONTEXT_PACK in capabilities)
        assertTrue(ProjectRuntimeCapability.HYBRID_RAG in capabilities)
        assertTrue(ProjectRuntimeCapability.REFERENCE_DNA in capabilities)
        assertTrue(ProjectRuntimeCapability.CHARACTER_STATE in capabilities)
        assertTrue(ProjectRuntimeCapability.CHRONOLOGY in capabilities)
        assertTrue(ProjectRuntimeCapability.ERA_TECH in capabilities)
        assertTrue(ProjectRuntimeCapability.CONSISTENCY_GATE in capabilities)
        assertTrue(ProjectRuntimeCapability.AGENT_CANDIDATE in capabilities)
        assertTrue(ProjectRuntimeCapability.FULL_BOOK_AUDIT in capabilities)
        assertTrue(ProjectRuntimeCapability.EXECUTION_AUDIT in capabilities)
        assertTrue(ProjectRuntimeCapability.AUTONOMOUS_REPLAN in capabilities)
        assertFalse(ProjectRuntimeCapability.FORESHADOWING in capabilities)
        assertEquals(7, plan.stepsFor(ProjectRuntimePhase.GENERATION).size)
        assertEquals(4, plan.stepsFor(ProjectRuntimePhase.POST_COMMIT).size)
    }

    @Test
    fun `reference dna capability is omitted when project has no binding`() {
        val plan = ProjectRuntimeSkillPlanner.build(snapshot(), draft(), referenceDnaCount = 0)

        assertFalse(plan.steps.any { it.capability == ProjectRuntimeCapability.REFERENCE_DNA })
    }

    @Test
    fun `audit distinguishes executed and intentionally skipped engines`() {
        val plan = ProjectRuntimeSkillPlanner.build(snapshot(), draft(), referenceDnaCount = 2)
        val events = listOf(
            RunEvent(RunStage.CONTEXT, RunStatus.SUCCESS, "D 层无额外命中"),
            RunEvent(RunStage.DRAFT, RunStatus.SUCCESS, "初稿完成"),
            RunEvent(RunStage.EDITOR_REVIEW_1, RunStatus.SUCCESS, "四席通过"),
            RunEvent(RunStage.CONSISTENCY, RunStatus.SUCCESS, "BLOCKING=0"),
            RunEvent(RunStage.FULL_BOOK_AUDIT, RunStatus.SKIPPED, "未到周期巡检点"),
            RunEvent(RunStage.EXECUTION_AUDIT, RunStatus.SUCCESS, "执行完成度 92 分"),
            RunEvent(RunStage.CANDIDATE, RunStatus.SUCCESS, "2 条 Candidate"),
            RunEvent(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "无需重规划"),
        )

        val audit = ProjectRuntimeSkillPlanner.audit(
            plan,
            events,
            setOf(ProjectRuntimePhase.GENERATION, ProjectRuntimePhase.POST_COMMIT),
        )

        assertEquals(9, audit.executedCount)
        assertEquals(2, audit.skippedCount)
        assertEquals(0, audit.failedCount)
        assertEquals(0, audit.pendingCount)
        assertEquals(RunStatus.SUCCESS, audit.runStatus)
    }

    @Test
    fun `manual review contract only audits agent candidate`() {
        val plan = ProjectRuntimeSkillPlanner.manualReview(snapshot(), draft())
        val audit = ProjectRuntimeSkillPlanner.audit(
            plan,
            listOf(RunEvent(RunStage.CANDIDATE, RunStatus.SUCCESS, "1 条 Candidate")),
            setOf(ProjectRuntimePhase.MANUAL_REVIEW),
        )

        assertEquals(1, plan.steps.size)
        assertEquals(ProjectRuntimeCapability.AGENT_CANDIDATE, plan.steps.single().capability)
        assertEquals(1, audit.executedCount)
        assertEquals(0, audit.pendingCount)
    }

    private fun snapshot(): StorySnapshot {
        val novel = Novel(
            id = "n-skill-os-v3",
            title = "运行时矩阵测试",
            genre = "悬疑",
            premise = "现实中的身份与记忆逐渐错位。",
            theme = "选择与代价",
            targetWords = 300_000,
            status = NovelStatus.WRITING,
        )
        return StorySnapshot(
            novel = novel,
            bible = emptyList(),
            characters = listOf(
                CharacterState(
                    id = "p-1",
                    novelId = novel.id,
                    name = "周衍",
                    personality = listOf("克制"),
                    location = "家中玄关",
                    physicalState = "正常",
                    emotionalState = "警惕",
                    goal = "验证来客身份",
                    lastUpdatedChapter = 1,
                )
            ),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
        )
    }

    private fun draft() = ChapterDraft(
        id = "d-1",
        novelId = "n-skill-os-v3",
        chapterNumber = 1,
        title = "门外的人",
        objective = "验证门外来客身份。",
    )
}
