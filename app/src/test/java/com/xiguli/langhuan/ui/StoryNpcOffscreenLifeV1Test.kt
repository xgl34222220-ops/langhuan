package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryNpcOffscreenLifeV1Test {

    @Test
    fun `offscreen selection excludes player and present cast`() {
        val life = NpcLifeSceneV1(
            sessionId = "s1",
            states = listOf(
                NpcLifeStateV1("林默", presence = NpcPresenceV1.AWAY),
                NpcLifeStateV1("顾宁", presence = NpcPresenceV1.PRESENT),
                NpcLifeStateV1("程野", presence = NpcPresenceV1.NEARBY),
                NpcLifeStateV1("周启", presence = NpcPresenceV1.AWAY),
            ),
        )
        val memory = NpcMemorySceneV1(
            sessionId = "s1",
            plans = listOf(
                NpcPlanV1(owner = "周启", goal = "追查线索", priority = 5),
                NpcPlanV1(owner = "程野", goal = "赶回现场", priority = 2),
            ),
        )

        val selected = selectOffscreenNpcV1(life, memory, playerName = "林默", maxActors = 3)

        assertFalse(selected.any { it.name == "林默" })
        assertFalse(selected.any { it.name == "顾宁" })
        assertEquals("程野", selected.first().name)
        assertTrue(selected.any { it.name == "周启" })
    }

    @Test
    fun `private and shared events only become memories for rightful owners`() {
        val privateEvent = NpcOffscreenEventV1(
            owner = "顾宁",
            summary = "发现一张旧照片",
            visibility = OffscreenEventVisibilityV1.PRIVATE,
        )
        val sharedEvent = NpcOffscreenEventV1(
            owner = "顾宁",
            participants = listOf("顾宁", "程野"),
            summary = "两人在楼梯间碰面",
            visibility = OffscreenEventVisibilityV1.SHARED,
        )

        val privateMemories = memoriesFromOffscreenEventV1(privateEvent)
        val sharedMemories = memoriesFromOffscreenEventV1(sharedEvent)

        assertEquals(listOf("顾宁"), privateMemories.map { it.first })
        assertEquals(setOf("顾宁", "程野"), sharedMemories.map { it.first }.toSet())
        assertTrue(privateMemories.all { it.third == NpcMemoryPrivacyV1.PRIVATE })
        assertTrue(sharedMemories.all { it.third == NpcMemoryPrivacyV1.PRIVATE })
    }

    @Test
    fun `public director bridge never exposes private offscreen events`() {
        val scene = NpcOffscreenSceneV1(
            sessionId = "s1",
            events = listOf(
                NpcOffscreenEventV1(
                    owner = "顾宁",
                    summary = "私下藏起钥匙",
                    visibility = OffscreenEventVisibilityV1.PRIVATE,
                ),
                NpcOffscreenEventV1(
                    owner = "程野",
                    location = "医院大厅",
                    summary = "大厅临时封锁",
                    visibility = OffscreenEventVisibilityV1.PUBLIC,
                ),
            ),
        )
        val original = "作者备注\n\n【原著章节边界证据】\n- 第3章｜幕后人身份未知"

        val block = renderOffscreenPublicNoteV1(scene)
        val merged = mergeOffscreenPublicNoteV1(original, block)

        assertTrue(merged.contains("大厅临时封锁"))
        assertFalse(merged.contains("私下藏起钥匙"))
        assertTrue(merged.contains("【原著章节边界证据】"))
        assertEquals(1, "【离场NPC公开动态｜导演层】".toRegex().findAll(merged).count())
    }

    @Test
    fun `return now cannot teleport and present npc is never demoted`() {
        val away = NpcOffscreenActorV1(
            name = "顾宁",
            location = "医院",
            returnCue = OffscreenReturnCueV1.RETURN_NOW,
        )

        assertEquals(
            NpcPresenceV1.NEARBY,
            desiredPresenceAfterOffscreenV1(NpcPresenceV1.AWAY, away, playerLocation = "学校"),
        )
        assertEquals(
            NpcPresenceV1.PRESENT,
            desiredPresenceAfterOffscreenV1(NpcPresenceV1.AWAY, away, playerLocation = "医院"),
        )
        assertEquals(
            NpcPresenceV1.PRESENT,
            desiredPresenceAfterOffscreenV1(NpcPresenceV1.PRESENT, away, playerLocation = "学校"),
        )
    }

    @Test
    fun `shared event filters participants outside allowed cast`() {
        val scene = NpcOffscreenSceneV1(sessionId = "s1")
        val updated = applyOffscreenChangesV1(
            scene = scene,
            owner = "顾宁",
            changes = listOf(
                NpcOffscreenChangeV1(
                    owner = "顾宁",
                    field = "事件:共享",
                    value = "程野,未来角色||交换了当前已知线索||双方分头行动",
                )
            ),
            allowedOwners = setOf("顾宁", "程野"),
            beat = 1,
        )

        val event = updated.events.single()
        assertEquals(setOf("顾宁", "程野"), event.participants.toSet())
        assertFalse(event.participants.contains("未来角色"))
    }
}
