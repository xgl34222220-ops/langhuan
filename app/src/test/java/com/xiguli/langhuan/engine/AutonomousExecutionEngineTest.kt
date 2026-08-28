package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.AutonomousStoryPlan
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.LongFormState
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.PlanExecutionStatus
import com.xiguli.langhuan.domain.PlannedChapterBeat
import com.xiguli.langhuan.domain.ReaderKnowledgeState
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousExecutionEngineTest {
    @Test
    fun `reveal budget never grants full reveal before earliest chapter`() {
        val snapshot = snapshot().copy(
            knowledgeLedger = listOf(
                KnowledgeBoundary(
                    id = "secret-1",
                    title = "终局秘密",
                    readerState = ReaderKnowledgeState.UNKNOWN,
                    revealPolicy = KnowledgeRevealPolicy.FULL,
                    earliestFullRevealChapter = 20,
                ),
                KnowledgeBoundary(
                    id = "secret-2",
                    title = "当前只能暗示",
                    readerState = ReaderKnowledgeState.PARTIAL,
                    revealPolicy = KnowledgeRevealPolicy.HINT_ONLY,
                    earliestFullRevealChapter = 30,
                ),
            )
        )

        val budget = AutonomousExecutionEngine.revealBudget(snapshot, 12)

        assertEquals(0, budget.maxFullReveals)
        assertTrue("secret-1" in budget.forbiddenBoundaryIds)
        assertTrue("secret-2" in budget.allowedPartialBoundaryIds)
        assertFalse("secret-1" in budget.allowedFullBoundaryIds)
    }

    @Test
    fun `partial execution marks only nearby future chapters for replanning`() = runBlocking {
        val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter = GeneratedChapter(
                title = "EXECUTION_AUDIT",
                content = "68||PARTIAL",
                summary = "主角拿到证据，但没有按原计划完成对质。",
                stateChanges = listOf(
                    StateChange(
                        subject = "CONFRONT_DELAYED",
                        field = "DEVIATION",
                        before = "本章完成对质",
                        after = "只拿到证据，对质延后",
                        evidence = "WATCH||6,7||让第6章承接证据并完成对质，第7章再恢复原因果链",
                    )
                ),
            )
        }
        val snapshot = snapshotWithPlan()
        val chapter = draft(5)
        val record = AutonomousExecutionEngine(gateway).assess(
            snapshot,
            chapter,
            GeneratedChapter(title = "第五章", content = "正文", summary = "拿到证据但暂未对质"),
        )

        assertEquals(PlanExecutionStatus.PARTIAL, record.status)
        assertEquals(68, record.completionScore)
        assertEquals(listOf(6, 7), record.affectedFutureChapters)
        assertTrue(AutonomousExecutionEngine.shouldSelectiveReplan(record))
    }

    @Test
    fun `selective merge preserves unaffected beats and replaces affected beats`() {
        val old = AutonomousStoryPlan(
            baseChapter = 5,
            horizonEndChapter = 8,
            generation = 1,
            chapters = (6..8).map { chapter ->
                PlannedChapterBeat(chapter, "旧$chapter", "旧目标$chapter", "旧冲突", "旧转折")
            },
        )
        val snapshot = snapshot().copy(longForm = LongFormState(autonomousPlan = old))
        val candidate = AutonomousStoryPlan(
            baseChapter = 5,
            horizonEndChapter = 8,
            generation = 2,
            chapters = (6..8).map { chapter ->
                PlannedChapterBeat(chapter, "新$chapter", "新目标$chapter", "新冲突", "新转折")
            },
        )

        val merged = AutonomousExecutionEngine().mergeSelectivePlan(snapshot, candidate, listOf(6, 7))

        assertEquals("新6", merged.chapters.first { it.chapterNumber == 6 }.title)
        assertEquals("新7", merged.chapters.first { it.chapterNumber == 7 }.title)
        assertEquals("旧8", merged.chapters.first { it.chapterNumber == 8 }.title)
        assertEquals(8, merged.chapters.first { it.chapterNumber == 8 }.revealBudget.chapterNumber)
    }

    private fun snapshotWithPlan(): StorySnapshot = snapshot().copy(
        longForm = LongFormState(
            autonomousPlan = AutonomousStoryPlan(
                baseChapter = 4,
                horizonEndChapter = 8,
                chapters = listOf(
                    PlannedChapterBeat(5, "第五章", "拿到证据并完成对质", "对方拒绝承认", "确认对方说谎"),
                    PlannedChapterBeat(6, "第六章", "追查说谎原因", "证据被销毁", "发现新线索"),
                    PlannedChapterBeat(7, "第七章", "确认线索来源", "人物关系恶化", "决定继续调查"),
                ),
            )
        )
    )

    private fun snapshot(): StorySnapshot = StorySnapshot(
        novel = Novel(
            id = "n1",
            title = "测试小说",
            genre = "悬疑",
            premise = "现实出现无法解释的缺口",
            theme = "记忆",
            targetWords = 300_000,
            currentChapter = 5,
            status = NovelStatus.WRITING,
        ),
        activeOutline = emptyList(),
        bible = emptyList(),
        characters = emptyList(),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
    )

    private fun draft(number: Int) = ChapterDraft(
        id = "c$number",
        novelId = "n1",
        chapterNumber = number,
        title = "第${number}章",
        objective = "拿到证据并完成对质",
        scenePlan = emptyList(),
    )
}
