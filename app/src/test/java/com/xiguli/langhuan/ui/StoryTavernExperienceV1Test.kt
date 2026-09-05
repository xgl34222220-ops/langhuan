package com.xiguli.langhuan.ui

import com.xiguli.langhuan.ui.story.StoryCanonRoleCandidateV1
import com.xiguli.langhuan.ui.story.advanceTavernTimeV1
import com.xiguli.langhuan.ui.story.mergeTavernDirectorNoteV1
import com.xiguli.langhuan.ui.story.recommendTavernCastV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTavernExperienceV1Test {

    @Test
    fun `recommended cast only uses characters actually visible in anchor snapshot`() {
        val candidates = listOf(
            StoryCanonRoleCandidateV1(name = "林默", aliases = listOf("小林"), lastChapter = 5, mentions = 4),
            StoryCanonRoleCandidateV1(name = "顾宁", aliases = listOf("顾队"), lastChapter = 5, mentions = 8),
            StoryCanonRoleCandidateV1(name = "程野", aliases = listOf("记者"), lastChapter = 5, mentions = 20),
        )

        val cast = recommendTavernCastV1(
            candidates = candidates,
            snapshot = "顾队推门进来，小林已经站在窗边。屋里没有其他人。",
            playerName = "林默",
        )

        assertEquals(listOf("林默", "顾宁"), cast)
        assertFalse("程野" in cast)
    }

    @Test
    fun `director note replaces old beat but preserves canon boundary evidence`() {
        val existing = """
            作者手动备注

            【酒馆导演事件】
            标题：旧场景
            旧内容

            【原著章节边界证据】
            - 第3章｜门锁被破坏
        """.trimIndent()

        val merged = mergeTavernDirectorNoteV1(
            existing = existing,
            beatTitle = "走廊有人靠近",
            beat = "脚步声停在门外，顾宁抬眼示意所有人安静。",
            cast = listOf("林默", "顾宁"),
        )

        assertTrue(merged.contains("作者手动备注"))
        assertTrue(merged.contains("走廊有人靠近"))
        assertTrue(merged.contains("林默、顾宁"))
        assertTrue(merged.contains("【原著章节边界证据】"))
        assertTrue(merged.contains("门锁被破坏"))
        assertFalse(merged.contains("旧内容"))
        assertEquals(1, Regex("【酒馆导演事件】").findAll(merged).count())
    }

    @Test
    fun `time helper keeps a readable relative trail`() {
        assertEquals("夜间 · 约30分钟后", advanceTavernTimeV1("夜间", "约30分钟后"))
        assertEquals("片刻后", advanceTavernTimeV1("", "片刻后"))
    }
}
