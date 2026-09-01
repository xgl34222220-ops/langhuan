package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRoleTakeoverV1Test {

    @Test
    fun `canon role binding enables takeover and keeps role identity`() {
        val session = StoryPlaySession(
            id = "s1",
            anchorChapter = 12,
            anchorTitle = "雨夜",
            playerProfile = StoryPlayerProfile(
                name = "沈砚",
                identity = "原著角色 · 截至第12章",
            ),
        )

        val scene = normalizeStoryRoleTakeoverSceneV1(null, session)

        assertTrue(scene.enabled)
        assertTrue(scene.canonicalRole)
        assertEquals("沈砚", scene.roleName)
        assertTrue(scene.preserveCanonPersonality)
    }

    @Test
    fun `custom role is not treated as canon role`() {
        val session = StoryPlaySession(
            id = "s2",
            anchorChapter = 4,
            playerProfile = StoryPlayerProfile(name = "路人甲", identity = "原创记者"),
        )

        val scene = normalizeStoryRoleTakeoverSceneV1(null, session)

        assertTrue(scene.enabled)
        assertFalse(scene.canonicalRole)
        assertFalse(scene.preserveCanonPersonality)
    }

    @Test
    fun `takeover note locks player dialogue action mind and future canon`() {
        val session = StoryPlaySession(
            id = "s3",
            anchorChapter = 20,
            anchorTitle = "旧医院",
            playerProfile = StoryPlayerProfile(name = "顾宁", identity = "原著角色 · 截至第20章"),
        )
        val scene = StoryRoleTakeoverSceneV1(
            sessionId = session.id,
            enabled = true,
            roleName = "顾宁",
            roleIdentity = session.playerProfile.identity,
            canonicalRole = true,
        )

        val note = renderStoryRoleTakeoverDirectorNoteV1(scene, session)

        assertTrue(note.contains("真人玩家独占控制"))
        assertTrue(note.contains("台词锁"))
        assertTrue(note.contains("行动锁"))
        assertTrue(note.contains("心理锁"))
        assertTrue(note.contains("第 21 章及之后"))
        assertTrue(note.contains("不得强行把剧情掰回原著未来"))
    }

    @Test
    fun `destiny rewrite explicitly makes future outcomes non mandatory`() {
        val session = StoryPlaySession(
            id = "s4",
            anchorChapter = 8,
            playerProfile = StoryPlayerProfile(name = "程野", identity = "原著角色 · 截至第8章"),
        )
        val scene = StoryRoleTakeoverSceneV1(
            sessionId = session.id,
            enabled = true,
            mode = StoryRoleTakeoverModeV1.DESTINY_REWRITE,
            roleName = "程野",
            roleIdentity = session.playerProfile.identity,
            canonicalRole = true,
        )

        val note = renderStoryRoleTakeoverDirectorNoteV1(scene, session)

        assertTrue(note.contains("允许彻底分叉"))
        assertTrue(note.contains("死亡、恋爱、背叛、胜负、相遇和结局都不是必须发生"))
        assertTrue(note.contains("不得用“原著本来如此”强行回轨"))
    }

    @Test
    fun `director note merge replaces old takeover block and can remove it`() {
        val session = StoryPlaySession(
            id = "s5",
            anchorChapter = 3,
            playerProfile = StoryPlayerProfile(name = "A", identity = "原著角色 · 截至第3章"),
        )
        val scene = StoryRoleTakeoverSceneV1(
            sessionId = session.id,
            enabled = true,
            roleName = "A",
            roleIdentity = session.playerProfile.identity,
            canonicalRole = true,
        )
        val original = "作者备注\n\n【故事空间约束｜导演层】\n空间内容\n【/故事空间约束】"
        val first = mergeStoryRoleTakeoverDirectorNoteV1(original, renderStoryRoleTakeoverDirectorNoteV1(scene, session))
        val second = mergeStoryRoleTakeoverDirectorNoteV1(first, renderStoryRoleTakeoverDirectorNoteV1(scene, session))
        val removed = mergeStoryRoleTakeoverDirectorNoteV1(second, "")

        assertEquals(1, "【玩家角色接管｜导演层】".toRegex().findAll(second).count())
        assertTrue(second.contains("【故事空间约束｜导演层】"))
        assertFalse(removed.contains("【玩家角色接管｜导演层】"))
        assertTrue(removed.contains("【故事空间约束｜导演层】"))
    }

    @Test
    fun `role change syncs name without resetting selected story mode`() {
        val oldSession = StoryPlaySession(
            id = "s6",
            anchorChapter = 9,
            playerProfile = StoryPlayerProfile(name = "A", identity = "原著角色 · 截至第9章"),
        )
        val old = normalizeStoryRoleTakeoverSceneV1(null, oldSession).copy(
            mode = StoryRoleTakeoverModeV1.DESTINY_REWRITE,
            lockDialogue = false,
        )
        val newSession = oldSession.copy(
            playerProfile = StoryPlayerProfile(name = "B", identity = "原著角色 · 截至第9章"),
        )

        val synced = normalizeStoryRoleTakeoverSceneV1(old, newSession)

        assertEquals("B", synced.roleName)
        assertEquals(StoryRoleTakeoverModeV1.DESTINY_REWRITE, synced.mode)
        assertFalse(synced.lockDialogue)
    }
}
