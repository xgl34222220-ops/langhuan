package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.LongFormState
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousStoryPlannerTest {
    @Test
    fun `locked future outline wins over ai proposal`() = runBlocking {
        val snapshot = snapshot()
        val plan = AutonomousStoryPlanner(FakeGateway()).plan(snapshot, draft(), 3)

        val locked = plan.chapters.first { it.chapterNumber == 3 }
        assertEquals("锁定的第三章", locked.title)
        assertEquals("必须确认旧证据来自同一来源", locked.objective)
        assertTrue(locked.fixedByOutline)
        assertEquals(listOf(3, 4, 5), plan.chapters.map { it.chapterNumber })
    }

    @Test
    fun `applying rolling plan never mutates canon`() = runBlocking {
        val snapshot = snapshot()
        val planner = AutonomousStoryPlanner(FakeGateway())
        val plan = planner.plan(snapshot, draft(), 3)
        val updated = planner.apply(snapshot, plan)
        val expectedPlan = AutonomousExecutionEngine().enrichRevealBudgets(snapshot, plan)

        assertEquals(snapshot.bible, updated.bible)
        assertEquals(snapshot.outline, updated.outline)
        assertEquals(snapshot.knowledgeLedger, updated.knowledgeLedger)
        assertEquals(expectedPlan, updated.longForm.autonomousPlan)
    }

    @Test
    fun `thin or stale plan requests refresh`() {
        val empty = snapshot()
        assertTrue(AutonomousStoryPlanner.shouldRefresh(empty, 2))

        val plan = com.xiguli.langhuan.domain.AutonomousStoryPlan(
            baseChapter = 1,
            horizonEndChapter = 4,
            chapters = listOf(
                com.xiguli.langhuan.domain.PlannedChapterBeat(3, "三", "目标", "冲突", "转折"),
                com.xiguli.langhuan.domain.PlannedChapterBeat(4, "四", "目标", "冲突", "转折"),
            ),
        )
        val withPlan = empty.copy(longForm = LongFormState(autonomousPlan = plan))
        assertTrue(AutonomousStoryPlanner.shouldRefresh(withPlan, 2))
    }

    private class FakeGateway : AiGateway {
        override suspend fun generate(prompt: PromptBundle): GeneratedChapter = GeneratedChapter(
            title = "AUTONOMOUS_PLAN",
            content = "保持同一调查主线，先兑现旧证据的因果，再扩大冲突。",
            summary = "未来三章围绕旧证据逐级升级。",
            stateChanges = listOf(
                StateChange("3||AI想改掉第三章", "FUTURE_CHAPTER", "AI目标", "AI冲突", "AI转折||周衍||f1||不要越界"),
                StateChange("4||电话另一端", "FUTURE_CHAPTER", "确认电话来源", "对方拒绝说明", "得到有限线索||周衍||||不揭底"),
                StateChange("5||旧楼入口", "FUTURE_CHAPTER", "抵达旧楼外围", "现实阻力增加", "确认下一步选择||周衍||||不新增幕后组织"),
                StateChange("周衍", "CHARACTER_ARC", "谨慎回避", "愿意为确认事实承担一次现实代价", "5||让他必须在安全和证据之间选择||不能突然变成无所畏惧"),
                StateChange("PACE_REPEAT", "DRIFT", "最近调查动作相似", "让第4章转为关系冲突", "WATCH||连续调查动作有重复风险"),
            ),
        )
    }

    private fun draft() = ChapterDraft(
        id = "c2",
        novelId = "n1",
        chapterNumber = 2,
        title = "照片背面",
        objective = "确认照片日期存在矛盾",
        scenePlan = emptyList(),
        summary = "周衍确认照片日期与记忆不一致。",
    )

    private fun snapshot(): StorySnapshot {
        val locked = OutlineNode(
            id = "ch3",
            novelId = "n1",
            parentId = "v1",
            level = OutlineLevel.CHAPTER,
            order = 3,
            title = "锁定的第三章",
            objective = "必须确认旧证据来自同一来源",
            conflict = "证据链缺少关键一环",
            turningPoint = "主角发现一个能继续验证的现实入口",
            locked = true,
        )
        return StorySnapshot(
            novel = Novel(
                id = "n1",
                title = "测试小说",
                genre = "悬疑",
                premise = "普通现实出现细小但无法忽略的偏差。",
                theme = "认知与选择",
                targetWords = 500_000,
                currentChapter = 2,
                status = NovelStatus.WRITING,
            ),
            activeOutline = listOf(locked),
            outline = listOf(locked),
            bible = listOf(BibleEntry("b1", "n1", BibleCategory.RULE, "现实获取规则", "普通人不能无理由取得保密资料", locked = true)),
            characters = emptyList(),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = listOf("第1章发现异常", "第2章确认照片日期矛盾"),
        )
    }
}
