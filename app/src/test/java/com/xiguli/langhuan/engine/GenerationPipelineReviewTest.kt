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
        val gateway = ScriptedGateway(mutableListOf("PASS"))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(FIRST_PROSE, result.chapter.content)
        assertEquals(1, gateway.reviewCalls)
        assertEquals(1, gateway.proseCalls)
        assertFalse(result.issues.any { it.code.startsWith("EDITOR_") && it.severity == IssueSeverity.BLOCKING })
    }

    @Test
    fun `rejected first draft is rewritten and second pass can commit`() = runBlocking {
        val gateway = ScriptedGateway(mutableListOf("REWRITE", "PASS"))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(SECOND_PROSE, result.chapter.content)
        assertEquals(2, gateway.reviewCalls)
        assertEquals(2, gateway.proseCalls)
        assertFalse(result.issues.any { it.code == "EDITOR_REVIEW_FAILED" })
    }

    @Test
    fun `second rejection blocks commit instead of infinite rewriting`() = runBlocking {
        val gateway = ScriptedGateway(mutableListOf("REWRITE", "REWRITE"))
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(2, gateway.reviewCalls)
        assertEquals(2, gateway.proseCalls)
        assertTrue(result.issues.any { it.code == "EDITOR_REVIEW_FAILED" && it.severity == IssueSeverity.BLOCKING })
        assertFalse(result.canCommit)
    }

    private class ScriptedGateway(
        private val verdicts: MutableList<String>,
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
                val verdict = verdicts.removeAt(0)
                GeneratedChapter(
                    title = verdict,
                    content = if (verdict == "PASS") {
                        "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过"
                    } else {
                        "【结构】章末推进不足，需要让选择产生不可逆后果\n【人物】通过\n【文字】通过\n【连续性】通过"
                    },
                    summary = if (verdict == "PASS") "四席通过" else "结构席退回",
                )
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
