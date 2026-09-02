package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelWorkflowStateMachineTest {
    @Test
    fun `approval confirms only current gate`() {
        val brief = NovelWorkflowStateMachine.begin(NovelWorkflowStateMachine.initial("novel-1"))
        assertEquals(NovelWorkflowStage.BRIEF, brief.currentStage)

        val waiting = NovelWorkflowStateMachine.submitArtifact(
            brief,
            NovelWorkflowArtifact(
                id = "brief-v1",
                kind = NovelArtifactKind.BRIEF,
                stage = NovelWorkflowStage.BRIEF,
            ),
        )
        val approved = NovelWorkflowStateMachine.applyGateReply(waiting, "可以")

        assertEquals(NovelWorkflowStage.RESEARCH, approved.currentStage)
        assertEquals(NovelWorkflowStatus.RUNNING, approved.stageStatus)
        assertEquals(1, approved.stageHistory.size)

        // The same short reply cannot silently approve a future stage that has not reached a gate.
        val unchanged = NovelWorkflowStateMachine.applyGateReply(approved, "继续")
        assertEquals(approved, unchanged)
    }

    @Test
    fun `rewind preserves downstream artifacts but marks them stale`() {
        val state = NovelWorkflowState(
            novelId = "novel-2",
            currentStage = NovelWorkflowStage.REVIEW,
            stageStatus = NovelWorkflowStatus.AWAITING_CONFIRMATION,
            artifacts = listOf(
                NovelWorkflowArtifact("foundation", NovelArtifactKind.FOUNDATION, NovelWorkflowStage.FOUNDATION),
                NovelWorkflowArtifact("plan-1", NovelArtifactKind.CHAPTER_PLAN, NovelWorkflowStage.CHAPTER_PLAN, chapterNumber = 1),
                NovelWorkflowArtifact("draft-1", NovelArtifactKind.WORKING_DRAFT, NovelWorkflowStage.DRAFT, chapterNumber = 1),
                NovelWorkflowArtifact("review-1", NovelArtifactKind.REVIEW_REPORT, NovelWorkflowStage.REVIEW, chapterNumber = 1),
            ),
        )

        val rewound = NovelWorkflowStateMachine.rewindTo(
            state,
            earliestStage = NovelWorkflowStage.DRAFT,
            reason = "人物动机改变",
            chapterNumbers = setOf(1),
        )

        assertEquals(NovelWorkflowStage.DRAFT, rewound.currentStage)
        assertEquals(NovelWorkflowStatus.NEEDS_REWORK, rewound.stageStatus)
        assertFalse(rewound.artifacts.first { it.id == "foundation" }.stale)
        assertFalse(rewound.artifacts.first { it.id == "plan-1" }.stale)
        assertTrue(rewound.artifacts.first { it.id == "draft-1" }.stale)
        assertTrue(rewound.artifacts.first { it.id == "review-1" }.stale)
        assertEquals(4, rewound.artifacts.size)
    }

    @Test
    fun `chapter dependency outline impact rewinds only to chapter plan`() {
        val report = ChapterDependencyReport(
            sourceChapter = 1,
            sourceTitle = "第一章",
            overallRisk = DependencyRisk.HIGH,
            direct = listOf(
                ChapterDependencyImpact(
                    kind = DependencyKind.CHARACTER,
                    risk = DependencyRisk.HIGH,
                    title = "林川",
                    detail = "人物状态来自第一章",
                    chapterNumber = 1,
                )
            ),
            downstream = listOf(
                ChapterDependencyImpact(
                    kind = DependencyKind.OUTLINE,
                    risk = DependencyRisk.MEDIUM,
                    title = "第2章章纲",
                    detail = "继续依赖第一章事实",
                    chapterNumber = 2,
                )
            ),
            anchors = listOf("林川"),
        )
        val state = NovelWorkflowState(
            novelId = "novel-3",
            currentStage = NovelWorkflowStage.CANON_SYNC,
            stageStatus = NovelWorkflowStatus.AWAITING_CONFIRMATION,
            artifacts = listOf(
                NovelWorkflowArtifact("plan-1", NovelArtifactKind.CHAPTER_PLAN, NovelWorkflowStage.CHAPTER_PLAN, chapterNumber = 1),
                NovelWorkflowArtifact("draft-1", NovelArtifactKind.WORKING_DRAFT, NovelWorkflowStage.DRAFT, chapterNumber = 1),
                NovelWorkflowArtifact("plan-2", NovelArtifactKind.CHAPTER_PLAN, NovelWorkflowStage.CHAPTER_PLAN, chapterNumber = 2),
                NovelWorkflowArtifact("draft-8", NovelArtifactKind.WORKING_DRAFT, NovelWorkflowStage.DRAFT, chapterNumber = 8),
            ),
        )

        val impacted = NovelWorkflowStateMachine.applyChapterDependencyImpact(state, report)

        assertEquals(NovelWorkflowStage.CHAPTER_PLAN, impacted.currentStage)
        assertTrue(impacted.artifacts.first { it.id == "plan-1" }.stale)
        assertTrue(impacted.artifacts.first { it.id == "draft-1" }.stale)
        assertTrue(impacted.artifacts.first { it.id == "plan-2" }.stale)
        assertFalse(impacted.artifacts.first { it.id == "draft-8" }.stale)
    }

    @Test
    fun `route capabilities are recorded as process metadata`() {
        val route = NovelRouteDecision(
            intent = NovelIntent.CONTINUITY_REVIEW,
            capabilities = listOf(
                NovelCapability.CHARACTER_STATE,
                NovelCapability.TIMELINE,
                NovelCapability.CONTINUITY,
            ),
        )

        val state = NovelWorkflowStateMachine.syncRoute(
            NovelWorkflowStateMachine.initial("novel-4"),
            route,
        )

        assertEquals("CONTINUITY_REVIEW", state.capabilities.routeIntent)
        assertEquals(
            listOf("CHARACTER_STATE", "TIMELINE", "CONTINUITY"),
            state.capabilities.enabledCapabilities,
        )
    }

    @Test
    fun `rework reply stays at current gate`() {
        val brief = NovelWorkflowStateMachine.begin(NovelWorkflowStateMachine.initial("novel-5"))
        val waiting = NovelWorkflowStateMachine.submitArtifact(
            brief,
            NovelWorkflowArtifact("brief", NovelArtifactKind.BRIEF, NovelWorkflowStage.BRIEF),
        )

        val rework = NovelWorkflowStateMachine.applyGateReply(waiting, "不对，这里修改一下")

        assertEquals(NovelWorkflowStage.BRIEF, rework.currentStage)
        assertEquals(NovelWorkflowStatus.NEEDS_REWORK, rework.stageStatus)
    }
}
