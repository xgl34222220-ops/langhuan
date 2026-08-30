package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationStreamingRunTest {
    @Test
    fun `prose uses raw streaming and exposes real pipeline stages`() = runBlocking {
        val gateway = StreamingGateway()
        val previews = mutableListOf<String>()
        val events = mutableListOf<RunEvent>()

        val result = GenerationPipeline(gateway).generate(
            request = request(),
            onDelta = { previews += it },
            onRunEvent = { events += it },
        )

        assertEquals(PROSE, result.chapter.content)
        assertTrue("expected multiple incremental previews", previews.distinct().size >= 3)
        assertTrue(previews.any { it.length < PROSE.length })
        assertTrue(previews.last().contains("没有开门"))
        assertEquals(1, gateway.streamingCalls)

        assertTrue(events.any { it.stage == RunStage.DRAFT && it.status == RunStatus.RUNNING })
        assertTrue(events.any { it.stage == RunStage.DRAFT && it.status == RunStatus.SUCCESS })
        assertTrue(events.any { it.stage == RunStage.NOVELIZATION && it.status == RunStatus.SKIPPED })
        assertTrue(events.any { it.stage == RunStage.EDITOR_REVIEW_1 && it.status == RunStatus.SUCCESS })
        assertTrue(events.any { it.stage == RunStage.READY_TO_COMMIT && it.status == RunStatus.SUCCESS })
    }

    private class StreamingGateway : AiGateway {
        var streamingCalls = 0

        override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
            streamingCalls++
            val chunks = listOf(
                PROSE.take(24),
                PROSE.take(55),
                PROSE,
            )
            chunks.forEach(onDelta)
            return PROSE
        }

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            return if (prompt.system.contains("对抗式章节主编委员会")) {
                GeneratedChapter(
                    title = "PASS",
                    content = "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过",
                    summary = "四席通过",
                )
            } else {
                GeneratedChapter(
                    title = "门外的人",
                    content = "",
                    summary = "周衍通过猫眼与照片确认来客身份存在矛盾，选择不开门并先验证证据。",
                )
            }
        }
    }

    private fun request() = GenerationRequest(
        snapshot = StorySnapshot(
            novel = Novel(
                id = "n-stream",
                title = "流式测试",
                genre = "悬疑",
                premise = "熟悉的现实出现一处无法解释的身份错位。",
                theme = "记忆与选择",
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
            id = "c-stream",
            novelId = "n-stream",
            chapterNumber = 1,
            title = "门外的人",
            objective = "让主角确认来客身份有问题，并基于眼前证据做出选择。",
            scenePlan = emptyList(),
        ),
        targetWords = 100,
    )

    private companion object {
        const val PROSE = "门铃响了第二遍，周衍仍站在玄关里。他从猫眼看见那张熟悉的脸，却没有立刻伸手开门。门外的人把一张旧照片从门缝下推了进来，照片背面的日期让他停了几秒。他把照片放到灯下，拨通另一个人的电话，始终没有开门。"
    }
}
