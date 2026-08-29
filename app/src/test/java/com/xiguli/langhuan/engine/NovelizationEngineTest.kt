package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelizationEngineTest {
    @Test
    fun `report style information dump requires novelization`() {
        val report = NovelizationEngine().analyze(BAD_PROSE)
        assertTrue(report.requiresNovelization)
        assertTrue(report.blocking)
        assertTrue(report.score < 76)
        assertTrue(report.problems.any { it.contains("后台") || it.contains("清单") })
    }

    @Test
    fun `scene driven prose passes local novelization gate`() {
        val report = NovelizationEngine().analyze(GOOD_PROSE)
        assertFalse(report.requiresNovelization)
        assertFalse(report.blocking)
        assertTrue(report.score >= 82)
    }

    @Test
    fun `pipeline auto novelizes bad first draft before adversarial review`() = runBlocking {
        val gateway = NovelizationGateway()
        val result = GenerationPipeline(gateway).generate(request())

        assertEquals(GOOD_PROSE, result.chapter.content)
        assertEquals(2, gateway.proseCalls)
        assertEquals(1, gateway.reviewCalls)
        assertTrue(result.issues.any { it.code == "PROSE_NOVELIZED" })
        assertFalse(result.issues.any { it.code == "PROSE_QUALITY_FAILED" })
        assertTrue(result.canCommit)
    }

    private class NovelizationGateway : AiGateway {
        var proseCalls = 0
        var reviewCalls = 0

        override suspend fun generateText(prompt: PromptBundle): String {
            proseCalls++
            return if (prompt.system.contains("小说化重构编辑")) GOOD_PROSE else BAD_PROSE
        }

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            return if (prompt.system.contains("对抗式章节主编委员会")) {
                reviewCalls++
                GeneratedChapter(
                    title = "PASS",
                    content = "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过",
                    summary = "四席通过",
                )
            } else {
                GeneratedChapter(
                    title = "查无此人",
                    summary = "周衍在现实记录中再次确认陆清璃的信息异常，并从一张旧凭证发现自己的报案记录也被改写。",
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
                premise = "一个人寻找被所有人遗忘的旧友。",
                theme = "记忆与存在",
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
            title = "查无此人",
            objective = "让主角确认现实记录正在否认一个他明确记得的人。",
            scenePlan = emptyList(),
        ),
        targetWords = 2_000,
    )

    private companion object {
        val BAD_PROSE = """
            周衍把资料放在桌上。他目前掌握的信息如下：
            1. 第一名死者二十八岁，死亡地点是出租屋。
            2. 第二名死者三十一岁，死亡地点是公司。
            3. 第三名死者二十六岁，死亡地点是车内。
            4. 第四名死者四十岁，死亡地点是酒店。
            5. 第五名死者二十九岁，死亡地点是宿舍。
            6. 第六名死者三十五岁，死亡地点是办公室。
            7. 第七名死者三十岁，死亡地点是家中。
            8. 第八名死者二十七岁，死亡地点是医院。
            这说明这些案件存在共同规律。也就是说，所有人都在死亡前出现睡眠异常。显然，他必须继续搜索、核对、记录和整理这些资料。
        """.trimIndent()

        val GOOD_PROSE = """
            窗口里的女警又把名字敲了一遍。

            “陆清璃，哪个璃？”

            “琉璃的璃。”

            键盘声停了。女警看了两秒屏幕，把身份证推回窗口：“查不到。你确定名字没错？”

            周衍没有回答。他从钱包最里层抽出一张折得发白的回执。日期是一年前，受理人和这里的印章都还在，唯独“失踪人姓名”那一栏空着。

            女警接过去，眉头慢慢皱起来。

            周衍盯着那块空白。昨晚之前，他还以为只有别人忘了她。
        """.trimIndent()
    }
}
