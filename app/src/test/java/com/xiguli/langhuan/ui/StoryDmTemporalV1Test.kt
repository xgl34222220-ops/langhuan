package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryDmTemporalV1Test {

    @Test
    fun `dm duration metadata wins over fallback heuristic`() {
        val turn = StoryPlayTurn(
            id = "t1",
            player = "我只是问一句话",
            narration = "两人短暂交谈。",
            variablesAfter = listOf(
                StoryPlayVariable("导演时钟", "本轮耗时分钟", "17", "场景内等待和移动合计"),
            ),
        )

        assertEquals(17, extractStoryDmTurnDurationV1(turn))
    }

    @Test
    fun `duration fallback recognizes travel wait and dialogue`() {
        assertEquals(20, inferStoryTurnDurationV1("我前往医院", "他离开学校赶往医院。"))
        assertEquals(30, inferStoryTurnDurationV1("我等半小时", "半小时后仍没有消息。"))
        assertEquals(2, inferStoryTurnDurationV1("我问他发生了什么", "他回答了我的问题。"))
    }

    @Test
    fun `pending advance only adds remaining clock time`() {
        val temporal = StoryDmTemporalSceneV1(
            sessionId = "s1",
            decisions = listOf(
                StoryDmTemporalDecisionV1(
                    turnId = "t1",
                    durationMinutes = 20,
                    source = "DM",
                    baseClockMinute = 100,
                    expectedClockMinute = 120,
                ),
            ),
        )
        val partial = StoryClockSceneV1(sessionId = "s1", currentMinute = 108)
        val alreadyThere = StoryClockSceneV1(sessionId = "s1", currentMinute = 120)

        assertEquals(12, pendingStoryDmClockAdvanceV1(temporal, partial))
        assertEquals(0, pendingStoryDmClockAdvanceV1(temporal, alreadyThere))
    }

    @Test
    fun `director note exposes time constraints but marks private schedule as director only`() {
        val clock = StoryClockSceneV1(
            sessionId = "s1",
            anchorTimeLabel = "14:30",
            currentMinute = 10,
            events = listOf(
                StoryClockEventV1(
                    title = "顾宁收到私信",
                    summary = "只有顾宁会看到这条消息",
                    owner = "顾宁",
                    visibility = StoryClockEventVisibilityV1.PRIVATE,
                    dueMinute = 25,
                ),
            ),
        )
        val temporal = StoryDmTemporalSceneV1(sessionId = "s1")

        val note = renderStoryDmTemporalDirectorNoteV1(clock, temporal)

        assertTrue(note.contains("当前故事时间"))
        assertTrue(note.contains("未到触发时间"))
        assertTrue(note.contains("私有/定向排程只是导演层约束"))
        assertTrue(note.contains("本轮耗时分钟"))
    }

    @Test
    fun `director note merge preserves canon notes and never duplicates block`() {
        val clock = StoryClockSceneV1(sessionId = "s1", anchorTimeLabel = "09:00")
        val temporal = StoryDmTemporalSceneV1(sessionId = "s1")
        val block = renderStoryDmTemporalDirectorNoteV1(clock, temporal)
        val original = "【原著章节边界证据】\n- 第3章：钥匙仍在抽屉里"

        val once = mergeStoryDmTemporalDirectorNoteV1(original, block)
        val twice = mergeStoryDmTemporalDirectorNoteV1(once, block)

        assertTrue(twice.contains("【原著章节边界证据】"))
        assertEquals(1, "【故事时钟与行动约束｜导演层】".toRegex().findAll(twice).count())
        assertFalse(twice.contains("null"))
    }
}
