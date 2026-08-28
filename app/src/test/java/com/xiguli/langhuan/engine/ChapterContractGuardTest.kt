package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterContract
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterContractGuardTest {
    @Test
    fun `old chapter derives contract from chapter outline`() {
        val snapshot = snapshot(
            outline = OutlineNode(
                id = "o1",
                novelId = "n1",
                level = OutlineLevel.CHAPTER,
                order = 1,
                title = "查无此人",
                objective = "确认记录异常",
                conflict = "记忆与现实冲突",
                turningPoint = "报案记录内容为空",
                mustInclude = listOf("报案记录"),
                forbidden = listOf("管理局"),
            )
        )
        val contract = ChapterContractGuard.resolve(snapshot, chapter())

        assertTrue("报案记录" in contract.mustHappen)
        assertTrue("管理局" in contract.mustNotHappen)
        assertEquals("报案记录内容为空", contract.hookOut)
        assertTrue(contract.characterStateIn.containsKey("周衍"))
    }

    @Test
    fun `chapter inherits full contract stored on outline`() {
        val snapshot = snapshot(
            outline = OutlineNode(
                id = "o1",
                novelId = "n1",
                level = OutlineLevel.CHAPTER,
                order = 1,
                title = "查无此人",
                objective = "确认记录异常",
                conflict = "记忆与现实冲突",
                turningPoint = "进入梦境",
                chapterContract = ChapterContract(
                    purpose = "只确认现实记录被改写",
                    secretsPreserved = listOf("梦主身份", "管理局存在"),
                    reveals = listOf("陆清璃查无此人"),
                    hookOut = "梦里有人叫出陆清璃的名字",
                    continuityRisks = listOf("不要提前解释十九起猝死"),
                ),
            )
        )

        val contract = ChapterContractGuard.resolve(snapshot, chapter())

        assertEquals("只确认现实记录被改写", contract.purpose)
        assertTrue("梦主身份" in contract.secretsPreserved)
        assertTrue("陆清璃查无此人" in contract.reveals)
        assertEquals("梦里有人叫出陆清璃的名字", contract.hookOut)
    }

    @Test
    fun `forbidden contract item blocks commit`() {
        val request = GenerationRequest(
            snapshot = snapshot(
                outline = OutlineNode(
                    id = "o1",
                    novelId = "n1",
                    level = OutlineLevel.CHAPTER,
                    order = 1,
                    title = "查无此人",
                    objective = "确认记录异常",
                    conflict = "记忆与现实冲突",
                    turningPoint = "进入梦境",
                    forbidden = listOf("管理局"),
                )
            ),
            chapter = chapter(),
            targetWords = 3000,
        )
        val issues = ChapterContractGuard.inspect(
            request,
            GeneratedChapter(content = "周衍刚走出门，就被管理局的人拦住。", summary = "测试"),
        )

        assertTrue(issues.any { it.code == "CHAPTER_CONTRACT_FORBIDDEN" && it.severity == IssueSeverity.BLOCKING })
    }

    @Test
    fun `hidden knowledge trigger blocks premature reveal`() {
        val secret = KnowledgeBoundary(
            id = "dream-master",
            title = "梦主身份",
            truth = "陆清璃就是梦主",
            unknownTo = listOf("周衍"),
            revealPolicy = KnowledgeRevealPolicy.HIDDEN,
            earliestFullRevealChapter = 40,
            triggerTerms = listOf("陆清璃就是梦主"),
        )
        val request = GenerationRequest(
            snapshot = snapshot(knowledge = listOf(secret)),
            chapter = chapter(),
            targetWords = 3000,
        )
        val issues = ChapterContractGuard.inspect(
            request,
            GeneratedChapter(
                content = "周衍终于明白，陆清璃就是梦主。",
                summary = "测试",
                stateChanges = listOf(
                    StateChange("周衍", "knownSecrets", "未知", "梦主身份", "正文直接确认"),
                ),
            ),
        )

        assertTrue(issues.any { it.code == "KNOWLEDGE_REVEAL_LEAK" && it.severity == IssueSeverity.BLOCKING })
        assertTrue(issues.any { it.code == "CHARACTER_KNOWLEDGE_OVERREACH" && it.severity == IssueSeverity.BLOCKING })
    }

    private fun chapter() = ChapterDraft(
        id = "c1",
        novelId = "n1",
        chapterNumber = 1,
        title = "查无此人",
        objective = "确认记录异常",
        scenePlan = emptyList(),
    )

    private fun snapshot(
        outline: OutlineNode? = null,
        knowledge: List<KnowledgeBoundary> = emptyList(),
    ) = StorySnapshot(
        novel = Novel(
            id = "n1",
            title = "无人生还的梦",
            genre = "悬疑",
            premise = "现实正在删除一个人的存在记录",
            theme = "记忆与存在",
            targetWords = 800_000,
            status = NovelStatus.WRITING,
        ),
        activeOutline = listOfNotNull(outline),
        bible = emptyList(),
        characters = listOf(
            CharacterState(
                id = "p1",
                novelId = "n1",
                name = "周衍",
                personality = listOf("谨慎"),
                location = "家中",
                physicalState = "疲惫",
                emotionalState = "焦虑",
                goal = "找到陆清璃",
                lastUpdatedChapter = 0,
            )
        ),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
        knowledgeLedger = knowledge,
    )
}
