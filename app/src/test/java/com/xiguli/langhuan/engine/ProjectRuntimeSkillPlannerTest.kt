package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterContract
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
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
            RunEvent(RunStage.CONTEXT_PACK, RunStatus.SUCCESS, "输入快照已锁定", 900L),
            RunEvent(RunStage.CHARACTER_STATE, RunStatus.SUCCESS, "读取 1 名人物状态", 920L),
            RunEvent(RunStage.HYBRID_RAG, RunStatus.RUNNING, "开始检索", 1_000L),
            RunEvent(RunStage.HYBRID_RAG, RunStatus.SUCCESS, "检索已执行 · 召回 2 条", 1_260L),
            RunEvent(RunStage.REFERENCE_DNA, RunStatus.SUCCESS, "PROSE_AUTHOR · PROSE · 实际注入 640 字", 1_300L),
            RunEvent(RunStage.DRAFT, RunStatus.SUCCESS, "初稿完成", 2_000L),
            RunEvent(RunStage.EDITOR_REVIEW_1, RunStatus.SUCCESS, "四席通过", 2_200L),
            RunEvent(RunStage.CONSISTENCY, RunStatus.RUNNING, "开始一致性检查", 2_300L),
            RunEvent(RunStage.CONSISTENCY, RunStatus.SUCCESS, "BLOCKING=0", 2_430L),
            RunEvent(RunStage.FULL_BOOK_AUDIT, RunStatus.SKIPPED, "未到周期巡检点", 2_500L),
            RunEvent(RunStage.EXECUTION_AUDIT, RunStatus.SUCCESS, "执行完成度 92 分", 2_600L),
            RunEvent(RunStage.CANDIDATE, RunStatus.SUCCESS, "2 条 Candidate", 2_700L),
            RunEvent(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "无需重规划", 2_800L),
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

        val rag = audit.receipts.single { it.step.capability == ProjectRuntimeCapability.HYBRID_RAG }
        assertEquals(260L, rag.durationMs)
        assertTrue(rag.dataInputs.isNotEmpty())
        assertTrue(rag.outputSummary.contains("召回 2 条"))
        assertEquals(2, rag.evidenceTrail.size)
    }

    @Test
    fun `reference dna is never inferred from prose execution`() {
        val plan = ProjectRuntimeSkillPlanner.build(snapshot(), draft(), referenceDnaCount = 2)
        val draftOnly = listOf(
            RunEvent(RunStage.DRAFT, RunStatus.RUNNING, "开始正文", 1_000L),
            RunEvent(RunStage.DRAFT, RunStatus.SUCCESS, "初稿完成", 2_000L),
        )

        val finalized = ProjectRuntimeSkillPlanner.audit(
            plan,
            draftOnly,
            setOf(ProjectRuntimePhase.GENERATION),
            finalize = true,
        )
        val dnaWithoutProof = finalized.receipts.single { it.step.capability == ProjectRuntimeCapability.REFERENCE_DNA }
        assertEquals(ProjectRuntimeReceiptState.SKIPPED, dnaWithoutProof.state)
        assertTrue(dnaWithoutProof.dataInputs.isEmpty())

        val live = ProjectRuntimeSkillPlanner.audit(
            plan,
            draftOnly,
            setOf(ProjectRuntimePhase.GENERATION),
            finalize = false,
        )
        assertEquals(
            ProjectRuntimeReceiptState.PENDING,
            live.receipts.single { it.step.capability == ProjectRuntimeCapability.REFERENCE_DNA }.state,
        )

        val withExactProof = ProjectRuntimeSkillPlanner.audit(
            plan,
            draftOnly + RunEvent(RunStage.REFERENCE_DNA, RunStatus.SUCCESS, "PROSE_AUTHOR · PROSE · 实际注入 512 字", 2_100L),
            setOf(ProjectRuntimePhase.GENERATION),
        )
        val dnaExecuted = withExactProof.receipts.single { it.step.capability == ProjectRuntimeCapability.REFERENCE_DNA }
        assertEquals(ProjectRuntimeReceiptState.EXECUTED, dnaExecuted.state)
        assertTrue(dnaExecuted.dataInputs.isNotEmpty())
        assertTrue(dnaExecuted.outputSummary.contains("实际注入 512 字"))
    }

    @Test
    fun `new running attempt does not reuse an old success receipt`() {
        val plan = ProjectRuntimeSkillPlanner.build(snapshot(), draft(), referenceDnaCount = 0)
        val events = listOf(
            RunEvent(RunStage.HYBRID_RAG, RunStatus.RUNNING, "第一次", 1_000L),
            RunEvent(RunStage.HYBRID_RAG, RunStatus.SUCCESS, "第一次完成", 1_100L),
            RunEvent(RunStage.HYBRID_RAG, RunStatus.RUNNING, "第二次", 2_000L),
        )

        val audit = ProjectRuntimeSkillPlanner.audit(
            plan,
            events,
            setOf(ProjectRuntimePhase.GENERATION),
            finalize = false,
        )
        val rag = audit.receipts.single { it.step.capability == ProjectRuntimeCapability.HYBRID_RAG }
        assertEquals(ProjectRuntimeReceiptState.PENDING, rag.state)
        assertTrue(rag.dataInputs.isEmpty())
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
        val chapterOutline = OutlineNode(
            id = "chapter-1",
            novelId = novel.id,
            level = OutlineLevel.CHAPTER,
            order = 1,
            title = "门外的人",
            objective = "验证门外来客身份。",
            conflict = "周衍不敢直接开门。",
            turningPoint = "门外人说出只有周衍知道的童年细节。",
            chapterContract = ChapterContract(
                purpose = "验证身份而不揭开镜像来源",
                mustHappen = listOf("出现一次身份验证动作"),
                mustNotHappen = listOf("揭示镜像来源"),
                characterStateIn = mapOf("周衍" to "独居，警惕"),
                characterStateOut = mapOf("周衍" to "确认来客知道童年秘密"),
                reveals = listOf("来客掌握童年细节"),
                secretsPreserved = listOf("镜像来源"),
                hookOut = "来客说出童年细节",
            ),
        )
        return StorySnapshot(
            novel = novel,
            activeOutline = listOf(chapterOutline),
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
        scenePlan = listOf(
            ScenePlan(
                order = 1,
                viewpoint = "周衍",
                location = "家中玄关",
                purpose = "隔门核验来客身份",
                conflict = "周衍不敢直接开门",
                outcome = "来客说出只有周衍知道的童年细节",
                participants = listOf("周衍", "门外来客"),
                storyDay = 1,
                timeOfDay = "夜间",
            )
        ),
    )
}
