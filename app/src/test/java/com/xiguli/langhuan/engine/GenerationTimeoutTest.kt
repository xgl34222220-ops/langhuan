package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationTimeoutTest {
    @Test
    fun `existing scene plan skips an extra ai logic request`() = runBlocking {
        val gateway = TimeoutGateway(blockLogic = true)
        val result = GenerationPipeline(gateway, timeouts = fastTimeouts()).generate(request(withScene = true))

        assertEquals(0, gateway.logicCalls)
        assertEquals(PROSE, result.chapter.content)
    }

    @Test
    fun `logic timeout falls back locally and still reaches prose`() = runBlocking {
        val gateway = TimeoutGateway(blockLogic = true)
        val events = mutableListOf<RunEvent>()
        val result = GenerationPipeline(gateway, timeouts = fastTimeouts()).generate(
            request(withScene = false),
            onRunEvent = events::add,
        )

        assertEquals(1, gateway.logicCalls)
        assertEquals(1, gateway.proseCalls)
        assertEquals(PROSE, result.chapter.content)
        assertTrue(events.any { it.stage == RunStage.LOGIC_PLAN && it.status == RunStatus.WARNING })
    }

    @Test
    fun `prose timeout ends generation with a clear error`() = runBlocking {
        val gateway = TimeoutGateway(blockProse = true)
        val error = runCatching {
            GenerationPipeline(gateway, timeouts = fastTimeouts()).generate(request(withScene = true))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("正文生成"))
        assertTrue(error?.message.orEmpty().contains("自动停止"))
        assertFalse(error?.message.orEmpty().contains("Cancellation"))
    }

    private class TimeoutGateway(
        private val blockLogic: Boolean = false,
        private val blockProse: Boolean = false,
    ) : AiGateway {
        var logicCalls = 0
        var proseCalls = 0

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            if (prompt.system.contains("章节逻辑导演")) {
                logicCalls++
                if (blockLogic) awaitCancellation()
            }
            return if (prompt.system.contains("对抗式章节主编委员会")) {
                GeneratedChapter("PASS", "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过", "四席通过")
            } else {
                GeneratedChapter("测试章", "", "周衍核对证据后决定暂不开门。")
            }
        }

        override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
            proseCalls++
            if (blockProse) awaitCancellation()
            onDelta(PROSE)
            return PROSE
        }
    }

    private fun request(withScene: Boolean): GenerationRequest {
        val novel = Novel("n-timeout", "超时测试", "悬疑", "现实出现身份错位", "记忆", 300_000, status = NovelStatus.WRITING)
        val scenes = if (withScene) listOf(
            ScenePlan(1, "周衍", "出租屋", "核对门外来客", "照片日期矛盾", "决定暂不开门", participants = listOf("周衍"))
        ) else emptyList()
        val draft = ChapterDraft("c-timeout", novel.id, 1, "门外的人", "确认来客身份有问题", scenes)
        return GenerationRequest(
            StorySnapshot(novel, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            draft,
            100,
        )
    }

    private fun fastTimeouts() = GenerationTimeouts(
        logicPlanMs = 30,
        proseMs = 30,
        novelizationMs = 30,
        reviewMs = 100,
        rewriteMs = 30,
        metadataMs = 100,
    )

    private companion object {
        const val PROSE = "门铃响了第二遍，周衍没有开门。他把门缝下的旧照片放到灯下，确认背面的日期与记忆冲突，于是先拨通另一个人的电话核对。"
    }
}
