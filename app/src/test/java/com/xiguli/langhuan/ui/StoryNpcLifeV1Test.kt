package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryNpcLifeV1Test {

    @Test
    fun `seed keeps player out and only current cast present`() {
        val roles = listOf(
            StoryCanonRoleCandidateV1(name = "林默", lastChapter = 3, mentions = 4),
            StoryCanonRoleCandidateV1(name = "顾宁", lastChapter = 3, mentions = 3),
            StoryCanonRoleCandidateV1(name = "程野", lastChapter = 2, mentions = 2),
        )

        val states = seedNpcLifeStatesV1(
            candidates = roles,
            initialCast = listOf("林默", "顾宁"),
            playerName = "林默",
            anchorChapter = 3,
        )

        assertFalse(states.any { it.name == "林默" })
        assertEquals(NpcPresenceV1.PRESENT, states.first { it.name == "顾宁" }.presence)
        assertEquals(NpcPresenceV1.AWAY, states.first { it.name == "程野" }.presence)
    }

    @Test
    fun `desired cast follows presence and preserves player`() {
        val cast = desiredNpcCastV1(
            states = listOf(
                NpcLifeStateV1("顾宁", presence = NpcPresenceV1.PRESENT),
                NpcLifeStateV1("程野", presence = NpcPresenceV1.NEARBY),
                NpcLifeStateV1("周启", presence = NpcPresenceV1.AWAY),
            ),
            playerName = "林默",
        )

        assertEquals(listOf("林默", "顾宁"), cast)
    }

    @Test
    fun `life notes keep canon boundary evidence intact`() {
        val scene = NpcLifeSceneV1(
            sessionId = "s1",
            states = listOf(
                NpcLifeStateV1(
                    name = "顾宁",
                    presence = NpcPresenceV1.PRESENT,
                    currentGoal = "确认门外动静",
                    hiddenIntent = "暂不暴露怀疑",
                )
            ),
        )
        val original = "作者备注\n\n【酒馆导演事件】\n标题：敲门声\n门外有人。\n\n【原著章节边界证据】\n- 第3章｜顾宁尚未知道幕后人身份"

        val merged = mergeNpcLifeNoteV1(original, renderNpcLifeNoteV1(scene))

        assertTrue(merged.contains("【NPC生命状态｜仅导演】"))
        assertTrue(merged.contains("隐藏意图=暂不暴露怀疑"))
        assertTrue(merged.contains("【原著章节边界证据】"))
        assertTrue(merged.contains("顾宁尚未知道幕后人身份"))
        assertEquals(1, "【NPC生命状态｜仅导演】".toRegex().findAll(merged).count())
    }

    @Test
    fun `situation bridge is replaced instead of endlessly appended`() {
        val first = NpcLifeSceneV1(
            sessionId = "s1",
            states = listOf(NpcLifeStateV1("顾宁", presence = NpcPresenceV1.PRESENT, emotion = "警觉")),
        )
        val second = first.copy(states = listOf(NpcLifeStateV1("顾宁", presence = NpcPresenceV1.PRESENT, emotion = "放松")))

        val once = mergeNpcLifeSituationV1("调查继续", first)
        val twice = mergeNpcLifeSituationV1(once, second)

        assertEquals(1, "【NPC态势】".toRegex().findAll(twice).count())
        assertTrue(twice.contains("放松"))
        assertFalse(twice.contains("警觉"))
    }
}
