package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.ChapterContract
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent
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

    @Test
    fun `timeline proven by prose and locked scene auto confirms`() {
        val evidence = "周衍在市局档案室查到一份被涂改的报案记录。"
        val scene = scene(day = 2)
        val staged = CandidateCanonEngine.stage(
            snapshot(),
            draft(evidence, listOf(scene)),
            review(
                AgentAction(
                    AgentActionKind.TIMELINE,
                    "查到涂改记录",
                    "",
                    "2||深夜||约20分钟||NORMAL||市局档案室||周衍||查到被涂改的报案记录||决定追查原始卷宗",
                    evidence,
                )
            ),
            now = 50L,
        )

        assertEquals(1, staged.autoConfirmedCount)
        assertEquals(CandidateFactStatus.CONFIRMED, staged.snapshot.candidateFacts.single().status)
        assertEquals(2, staged.snapshot.recentTimeline.single().storyDay)
        assertEquals("市局档案室", staged.snapshot.recentTimeline.single().location)
    }

    @Test
    fun `backward main timeline stays pending even when prose contains evidence`() {
        val evidence = "周衍在市局档案室查到一份被涂改的报案记录。"
        val prior = TimelineEvent(
            id = "t1",
            novelId = "n1",
            chapter = 1,
            storyTime = "故事第3天·傍晚",
            location = "出租屋",
            participants = listOf("周衍"),
            summary = "收到匿名信",
            storyDay = 3,
            timeOfDay = "傍晚",
        )
        val staged = CandidateCanonEngine.stage(
            snapshot().copy(recentTimeline = listOf(prior)),
            draft(evidence, listOf(scene(day = 2))),
            review(AgentAction(AgentActionKind.TIMELINE, "查记录", "", "2||深夜||约20分钟||NORMAL||市局档案室||周衍||查到记录||继续追查", evidence)),
            now = 60L,
        )

        assertEquals(0, staged.autoConfirmedCount)
        assertEquals(CandidateFactStatus.PENDING, staged.snapshot.candidateFacts.single().status)
        assertEquals(listOf(prior), staged.snapshot.recentTimeline)
    }

    @Test
    fun `authorized full knowledge gain auto confirms and reaches next chapter context`() {
        val evidence = "陆清璃亲口承认，梦主身份就是她自己。"
        val character = CharacterState(
            id = "zhou",
            novelId = "n1",
            name = "周衍",
            personality = listOf("克制"),
            location = "市局档案室",
            physicalState = "正常",
            emotionalState = "警惕",
            goal = "确认梦境真相",
            lastUpdatedChapter = 1,
        )
        val boundary = KnowledgeBoundary(
            id = "dreamer",
            title = "梦主身份",
            truth = "陆清璃就是梦主",
            unknownTo = listOf("周衍"),
            revealPolicy = KnowledgeRevealPolicy.FULL,
            earliestFullRevealChapter = 2,
            triggerTerms = listOf("陆清璃就是梦主"),
        )
        val chapterTwo = draft(
            evidence,
            listOf(scene(day = 2)),
            contract = ChapterContract(reveals = listOf("梦主身份")),
        )
        val staged = CandidateCanonEngine.stage(
            snapshot().copy(characters = listOf(character), knowledgeLedger = listOf(boundary)),
            chapterTwo,
            review(AgentAction(AgentActionKind.KNOWLEDGE_GAIN, "周衍", "不知道梦主身份", "梦主身份", evidence)),
            now = 70L,
        )

        assertEquals(1, staged.autoConfirmedCount)
        assertTrue(staged.snapshot.characters.single().knownSecrets.contains("梦主身份"))
        assertTrue(staged.snapshot.knowledgeLedger.single().knownBy.contains("周衍"))
        assertFalse(staged.snapshot.knowledgeLedger.single().unknownTo.contains("周衍"))

        val nextChapter = ChapterDraft("c3", "n1", 3, "继续追查", "根据已知身份寻找入口", listOf(scene(day = 3)))
        val context = GenerationContextBuilder().build(GenerationRequest(staged.snapshot, nextChapter, 2_000))
        assertTrue(context.canon.contains("梦主身份"))
        assertTrue(context.canon.contains("已知者=周衍"))
        assertTrue(context.state.contains("周衍"))
        assertTrue(context.state.contains("本人已知=梦主身份"))
    }

    @Test
    fun `hidden knowledge remains pending despite direct prose evidence`() {
        val evidence = "陆清璃亲口承认，梦主身份就是她自己。"
        val character = CharacterState("zhou", "n1", "周衍", listOf("克制"), "档案室", "正常", "警惕", "追查", lastUpdatedChapter = 1)
        val boundary = KnowledgeBoundary(
            id = "dreamer",
            title = "梦主身份",
            truth = "陆清璃就是梦主",
            unknownTo = listOf("周衍"),
            revealPolicy = KnowledgeRevealPolicy.HIDDEN,
            triggerTerms = listOf("陆清璃就是梦主"),
        )
        val staged = CandidateCanonEngine.stage(
            snapshot().copy(characters = listOf(character), knowledgeLedger = listOf(boundary)),
            draft(evidence, listOf(scene(day = 2)), ChapterContract(reveals = listOf("梦主身份"))),
            review(AgentAction(AgentActionKind.KNOWLEDGE_GAIN, "周衍", "未知", "梦主身份", evidence)),
            now = 80L,
        )

        assertEquals(0, staged.autoConfirmedCount)
        assertTrue(staged.snapshot.characters.single().knownSecrets.isEmpty())
        assertEquals(CandidateFactStatus.PENDING, staged.snapshot.candidateFacts.single().status)
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

    private fun draft(
        content: String,
        scenePlan: List<ScenePlan> = emptyList(),
        contract: ChapterContract = ChapterContract(),
    ) = ChapterDraft(
        id = "c2",
        novelId = "n1",
        chapterNumber = 2,
        title = "查无此人",
        objective = "确认现实记录异常",
        scenePlan = scenePlan,
        content = content,
        contract = contract,
    )

    private fun scene(day: Int) = ScenePlan(
        order = 1,
        viewpoint = "周衍",
        location = "市局档案室",
        purpose = "核对报案记录",
        conflict = "原始卷宗被涂改",
        outcome = "确认记录异常",
        participants = listOf("周衍"),
        storyDay = day,
        timeOfDay = "深夜",
        elapsedFromPrevious = "约20分钟",
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
