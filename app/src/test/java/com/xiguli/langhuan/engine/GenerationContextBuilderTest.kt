package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterContract
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationContextBuilderTest {
    @Test
    fun `rag history cannot crowd out execution and canon`() {
        val request = request()
        val retrieved = (1..40).map { index ->
            RetrievedContextItem(
                sourceType = "CHAPTER",
                sourceId = "old-$index",
                chapterNumber = index,
                text = "旧剧情$index " + "很长的历史材料".repeat(600),
                score = 0.9 - index / 100.0,
                reasons = listOf("语义相关", "临近当前章节"),
            )
        }

        val pack = GenerationContextBuilder().build(request, retrieved)

        assertTrue(pack.execution.contains("必须发生"))
        assertTrue(pack.execution.contains("只确认记录异常"))
        assertTrue(pack.canon.contains("信息边界"))
        assertTrue(pack.canon.contains("梦主身份"))
        assertTrue(pack.history.length <= 8_010)
        assertTrue(pack.trace.any { it.layer == ContextLayer.D_HISTORY && it.reason.contains("语义相关") })
    }

    @Test
    fun `creation fact ledger is never treated as prose style`() {
        val pack = GenerationContextBuilder().build(request())
        assertFalse(pack.style.contains(GenerationContextBuilder.CREATION_FACT_LEDGER))
        assertTrue(pack.style.contains("克制悬疑"))
    }

    private fun request(): GenerationRequest {
        val novelId = "n1"
        val chapterContract = ChapterContract(
            purpose = "只确认记录异常",
            mustHappen = listOf("发现报案记录为空"),
            mustNotHappen = listOf("解释梦主身份"),
            secretsPreserved = listOf("梦主身份"),
            hookOut = "梦里有人叫出陆清璃的名字",
        )
        val outline = OutlineNode(
            id = "c1",
            novelId = novelId,
            level = OutlineLevel.CHAPTER,
            order = 1,
            title = "查无此人",
            objective = "只确认记录异常",
            conflict = "记忆与现实冲突",
            turningPoint = "报案记录内容为空",
            chapterContract = chapterContract,
        )
        val snapshot = StorySnapshot(
            novel = Novel(novelId, "无人生还的梦", "悬疑", "现实正在删除一个人的存在", "记忆与存在", 800_000),
            activeOutline = listOf(outline),
            bible = listOf(
                BibleEntry("style", novelId, BibleCategory.STYLE, "主文风", "克制悬疑，异常少而精准"),
                BibleEntry("ledger", novelId, BibleCategory.STYLE, GenerationContextBuilder.CREATION_FACT_LEDGER, "最终梦主其实是陆清璃"),
                BibleEntry("rule", novelId, BibleCategory.RULE, "现实记录规则", "记录被改写必须留下不一致痕迹"),
            ),
            characters = listOf(
                CharacterState("p1", novelId, "周衍", listOf("谨慎"), "家中", "疲惫", "焦虑", "找到陆清璃", lastUpdatedChapter = 0)
            ),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = listOf("前一章只确认陆清璃失联"),
            knowledgeLedger = listOf(
                KnowledgeBoundary(
                    id = "k1",
                    title = "梦主身份",
                    truth = "陆清璃就是梦主",
                    unknownTo = listOf("周衍"),
                    revealPolicy = KnowledgeRevealPolicy.HIDDEN,
                    earliestFullRevealChapter = 40,
                    triggerTerms = listOf("陆清璃就是梦主"),
                )
            ),
        )
        val draft = ChapterDraft(
            id = "d1",
            novelId = novelId,
            chapterNumber = 1,
            title = "查无此人",
            objective = "只确认记录异常",
            scenePlan = emptyList(),
            contract = chapterContract,
        )
        return GenerationRequest(snapshot, draft, 3_000)
    }
}
