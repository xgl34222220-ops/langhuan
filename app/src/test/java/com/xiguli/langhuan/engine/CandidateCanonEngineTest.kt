package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateCanonEngineTest {
    @Test
    fun `new character is staged but never becomes canon automatically`() {
        val snapshot = snapshot()
        val draft = draft("门口站着一个陌生女人。她说自己叫顾遥。")
        val review = review(
            AgentAction(
                AgentActionKind.CHARACTER_NEW,
                "顾遥",
                "",
                "旧城区||警惕||寻找失踪者||克制、谨慎",
                "门口站着一个陌生女人。她说自己叫顾遥。",
            )
        )

        val staged = CandidateCanonEngine.stage(snapshot, draft, review, now = 10L)

        assertEquals(1, staged.stagedCount)
        assertEquals(0, staged.autoConfirmedCount)
        assertFalse(staged.snapshot.characters.any { it.name == "顾遥" })
        assertEquals(CandidateFactStatus.PENDING, staged.snapshot.candidateFacts.single().status)
    }

    @Test
    fun `existing character state with direct prose evidence can auto confirm`() {
        val snapshot = snapshot().copy(
            characters = listOf(
                CharacterState(
                    id = "zhou",
                    novelId = "n1",
                    name = "周衍",
                    personality = listOf("克制"),
                    location = "出租屋",
                    physicalState = "正常",
                    emotionalState = "疲惫",
                    goal = "寻找陆清璃",
                    lastUpdatedChapter = 1,
                )
            )
        )
        val evidence = "周衍离开出租屋，进了市局一楼大厅。"
        val review = review(
            AgentAction(
                AgentActionKind.CHARACTER_LOCATION,
                "周衍",
                "出租屋",
                "市局一楼大厅",
                evidence,
            )
        )

        val staged = CandidateCanonEngine.stage(snapshot, draft(evidence), review, now = 20L)

        assertEquals(1, staged.autoConfirmedCount)
        assertEquals("市局一楼大厅", staged.snapshot.characters.single().location)
        assertEquals(CandidateFactStatus.CONFIRMED, staged.snapshot.candidateFacts.single().status)
        assertTrue(staged.snapshot.factHistory.any { it.kind == "CHARACTER_LOCATION" })
    }

    @Test
    fun `manual confirmation promotes high risk candidate exactly once`() {
        val staged = CandidateCanonEngine.stage(
            snapshot(),
            draft("顾遥推门进来。"),
            review(
                AgentAction(
                    AgentActionKind.CHARACTER_NEW,
                    "顾遥",
                    "",
                    "档案室||冷静||查清旧案||谨慎",
                    "顾遥推门进来。",
                )
            ),
            now = 30L,
        ).snapshot
        val id = staged.candidateFacts.single().id

        val confirmed = CandidateCanonEngine.confirm(staged, id, now = 31L)
        val confirmedAgain = CandidateCanonEngine.confirm(confirmed, id, now = 32L)

        assertEquals(1, confirmed.characters.count { it.name == "顾遥" })
        assertEquals(CandidateFactStatus.CONFIRMED, confirmed.candidateFacts.single().status)
        assertEquals(confirmed.characters, confirmedAgain.characters)
    }

    @Test
    fun `rejected candidate cannot mutate canon`() {
        val staged = CandidateCanonEngine.stage(
            snapshot(),
            draft("顾遥推门进来。"),
            review(AgentAction(AgentActionKind.CHARACTER_NEW, "顾遥", "", "档案室||冷静||查案||谨慎", "顾遥推门进来。")),
            now = 40L,
        ).snapshot
        val id = staged.candidateFacts.single().id

        val rejected = CandidateCanonEngine.reject(staged, id, now = 41L)
        val attempted = CandidateCanonEngine.confirm(rejected, id, now = 42L)

        assertEquals(CandidateFactStatus.REJECTED, attempted.candidateFacts.single().status)
        assertFalse(attempted.characters.any { it.name == "顾遥" })
    }

    private fun snapshot() = StorySnapshot(
        novel = Novel(
            id = "n1",
            title = "测试",
            genre = "悬疑",
            premise = "现实记录开始出现异常。",
            theme = "记忆",
            targetWords = 500_000,
            status = NovelStatus.WRITING,
        ),
        activeOutline = emptyList(),
        bible = emptyList(),
        characters = emptyList(),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
    )

    private fun draft(content: String) = ChapterDraft(
        id = "c2",
        novelId = "n1",
        chapterNumber = 2,
        title = "查无此人",
        objective = "确认现实记录异常",
        scenePlan = emptyList(),
        content = content,
    )

    private fun review(vararg actions: AgentAction) = AgentReview(
        title = "章节复盘",
        summary = "",
        metrics = "",
        memoryActions = actions.toList(),
        diagnostics = emptyList(),
        nextOptions = emptyList(),
        touchedForeshadowingIds = emptyList(),
        fullBook = false,
    )
}
