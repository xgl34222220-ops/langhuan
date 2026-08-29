package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationPipelineReviewTest {
    @Test
    fun `first pass does not rewrite`() = runBlocking {
        val gateway = ScriptedGateway(mutableListOf(ReviewScript.Pass))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(FIRST_PROSE, result.chapter.content)
        assertEquals(1, gateway.reviewCalls)
        assertEquals(1, gateway.proseCalls)
        assertFalse(result.issues.any { it.code.startsWith("EDITOR_") && it.severity == IssueSeverity.BLOCKING })
    }

    @Test
    fun `editorial advice cannot force rewrite or block`() = runBlocking {
        val gateway = ScriptedGateway(mutableListOf(ReviewScript.AdviceRewrite))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(FIRST_PROSE, result.chapter.content)
        assertEquals(1, gateway.reviewCalls)
        assertEquals(1, gateway.proseCalls)
        assertFalse(result.issues.any { it.code == "EDITOR_REVIEW_FAILED" })
        assertTrue(result.canCommit)
    }

    @Test
    fun `ai claimed hard conflict cannot force rewrite when deterministic gate is clean`() = runBlocking {
        val gateway = ScriptedGateway(mutableListOf(ReviewScript.HardRewrite))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(FIRST_PROSE, result.chapter.content)
        assertEquals(1, gateway.reviewCalls)
        assertEquals(1, gateway.proseCalls)
        assertFalse(result.issues.any { it.code == "EDITOR_REVIEW_FAILED" })
        assertTrue(result.canCommit)
    }

    @Test
    fun `repeated ai hard claims cannot create editor blocking`() = runBlocking {
        val gateway = ScriptedGateway(mutableListOf(ReviewScript.HardRewrite))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(1, gateway.reviewCalls)
        assertEquals(1, gateway.proseCalls)
        assertFalse(result.issues.any { it.code.startsWith("EDITOR_") && it.severity == IssueSeverity.BLOCKING })
        assertTrue(result.canCommit)
    }

    private enum class ReviewScript { Pass, AdviceRewrite, HardRewrite }

    private class ScriptedGateway(
        private val verdicts: MutableList<ReviewScript>,
    ) : AiGateway {
        var reviewCalls = 0
        var proseCalls = 0

        override suspend fun generateText(prompt: PromptBundle): String {
            proseCalls++
            return if (prompt.user.contains("【上一稿】")) SECOND_PROSE else FIRST_PROSE
        }

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            return if (prompt.system.contains("对抗式章节主编委员会")) {
                reviewCalls++
                when (verdicts.removeAt(0)) {
                    ReviewScript.Pass -> GeneratedChapter(
                        title = "PASS",
                        content = "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过",
                        summary = "四席通过",
                    )
                    ReviewScript.AdviceRewrite -> GeneratedChapter(
                        title = "REWRITE",
                        content = "【结构】【建议】章末可以更集中，让选择产生更强后果\n【人物】通过\n【文字】通过\n【连续性】通过",
                        summary = "结构席有编辑建议，但不存在可验证硬冲突",
                    )
                    ReviewScript.HardRewrite -> GeneratedChapter(
                        title = "REWRITE",
                        content = "【结构】【硬冲突】锚点=本章必须确认来客身份不一致｜正文证据=正文没有完成该确认｜修法=在本章场景内补足明确确认\n【人物】通过\n【文字】通过\n【连续性】通过",
                        summary = "结构席声称发现硬冲突",
                    )
                }
            } else {
                GeneratedChapter(
                    title = "测试章",
                    summary = "人物在安静的现实场景中确认一个异常细节，并基于眼前证据做出下一步选择。",
                )
            }
        }
    }

    private fun request() = GenerationRequest(
        snapshot = StorySnapshot(
            novel = Novel(
                id = "n1",
                title = "测试小说",
                genre = "悬疑",
                premise = "一个普通人发现熟悉的现实出现细小偏差。",
                theme = "认知与选择",
                targetWords = 300_000,
                status = NovelStatus.WRITING,
            ),
            activeOutline = emptyList(),
            bible = emptyList(),
            characters = emptyList(),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
        ),
        chapter = ChapterDraft(
            id = "c1",
            novelId = "n1",
            chapterNumber = 1,
            title = "门外的人",
            objective = "让主角确认门外来客与记忆中的身份不一致。",
            scenePlan = emptyList(),
        ),
        targetWords = 2_000,
    )

    private companion object {
        const val FIRST_PROSE = "周衍听见门铃后没有立刻开门。他隔着猫眼看见一个熟悉的侧脸，却想不起对方为什么会知道自己的地址。门外的人等了很久，只把一张旧照片从门缝下推了进来。"
        const val SECOND_PROSE = "门铃第二次响起时，周衍把手从门把上收了回来。猫眼外那张脸他认识，可照片背面的日期却证明两人不该在今天见面。他没有开门，而是先拨通了照片里另一个人的电话。"
    }
}
