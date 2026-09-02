package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelWorkflowBootstrapTest {
    @Test
    fun `existing chapter outline resumes at chapter plan`() {
        val snapshot = snapshot(
            outline = listOf(
                OutlineNode(
                    id = "chapter-1",
                    novelId = "novel-1",
                    level = OutlineLevel.CHAPTER,
                    order = 1,
                    title = "第一章",
                    objective = "建立异常",
                    conflict = "主角不相信眼前证据",
                    turningPoint = "第一次规则失效",
                )
            )
        )

        val bootstrapped = NovelWorkflowBootstrap.fromSnapshot(
            NovelWorkflowStateMachine.initial("novel-1"),
            snapshot,
        )

        assertEquals(NovelWorkflowStage.CHAPTER_PLAN, bootstrapped.currentStage)
        assertEquals("resume:CHAPTER_PLAN", bootstrapped.decisions["v7_bootstrap"]?.value)
    }

    @Test
    fun `existing foundation without outline resumes at blueprint`() {
        val base = snapshot()
        val snapshot = base.copy(
            bible = listOf(
                com.xiguli.langhuan.domain.BibleEntry(
                    id = "rule-1",
                    novelId = "novel-1",
                    category = com.xiguli.langhuan.domain.BibleCategory.RULE,
                    name = "代价规则",
                    content = "每次使用能力都必须支付可追踪代价",
                )
            )
        )

        val bootstrapped = NovelWorkflowBootstrap.fromSnapshot(
            NovelWorkflowStateMachine.initial("novel-1"),
            snapshot,
        )

        assertEquals(NovelWorkflowStage.BLUEPRINT, bootstrapped.currentStage)
    }

    @Test
    fun `already tracked workflow is never reclassified from snapshot`() {
        val tracked = NovelWorkflowState(
            novelId = "novel-1",
            currentStage = NovelWorkflowStage.REVIEW,
            stageStatus = NovelWorkflowStatus.AWAITING_CONFIRMATION,
            stageHistory = listOf(
                NovelWorkflowHistoryEntry(
                    stage = NovelWorkflowStage.DRAFT,
                    status = NovelWorkflowStatus.CONFIRMED,
                )
            ),
        )
        val snapshot = snapshot(
            outline = listOf(
                OutlineNode(
                    id = "chapter-1",
                    novelId = "novel-1",
                    level = OutlineLevel.CHAPTER,
                    order = 1,
                    title = "第一章",
                    objective = "目标",
                    conflict = "冲突",
                    turningPoint = "转折",
                )
            )
        )

        assertEquals(tracked, NovelWorkflowBootstrap.fromSnapshot(tracked, snapshot))
    }

    private fun snapshot(outline: List<OutlineNode> = emptyList()): StorySnapshot = StorySnapshot(
        novel = Novel(
            id = "novel-1",
            title = "测试书",
            genre = "悬疑",
            premise = "一个测试故事",
            theme = "选择与代价",
            targetWords = 500_000,
        ),
        activeOutline = outline,
        outline = outline,
        bible = emptyList(),
        characters = emptyList(),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
    )
}
