package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryClockV1Test {

    @Test
    fun `clock formats real hh mm anchor across midnight`() {
        assertEquals("23:55", formatStoryClockTimeV1("23:30", 25))
        assertEquals("第 +1 天 00:10", formatStoryClockTimeV1("23:30", 40))
        assertEquals("深夜 + 1小时5分钟", formatStoryClockTimeV1("深夜", 65))
    }

    @Test
    fun `due event fires only after story time reaches it`() {
        val event = StoryClockEventV1(
            id = "e1",
            title = "抵达医院",
            summary = "程野赶到医院",
            dueMinute = 20,
        )
        val scene = StoryClockSceneV1(sessionId = "s1", events = listOf(event))

        val ten = advanceStoryClockV1(scene, 10)
        assertEquals(StoryClockEventStatusV1.PENDING, ten.events.single().status)

        val twenty = advanceStoryClockV1(ten, 10)
        assertEquals(StoryClockEventStatusV1.FIRED, twenty.events.single().status)
        assertEquals(20L, twenty.events.single().firedMinute)
    }

    @Test
    fun `causal dependency fires in order on same clock tick`() {
        val first = StoryClockEventV1(
            id = "first",
            title = "消息送达",
            summary = "信使把消息送到",
            dueMinute = 30,
        )
        val second = StoryClockEventV1(
            id = "second",
            title = "收到消息后出发",
            summary = "顾宁开始赶路",
            dueMinute = 30,
            prerequisiteEventIds = listOf("first"),
        )
        val scene = StoryClockSceneV1(sessionId = "s1", events = listOf(first, second))

        val fired = advanceStoryClockV1(scene, 30)

        assertTrue(fired.events.all { it.status == StoryClockEventStatusV1.FIRED })
    }

    @Test
    fun `blocked dependency remains pending`() {
        val blocked = StoryClockEventV1(
            id = "blocked",
            title = "后续行动",
            summary = "必须等待前置事件",
            dueMinute = 5,
            prerequisiteEventIds = listOf("missing"),
        )
        val scene = advanceStoryClockV1(StoryClockSceneV1(sessionId = "s1", events = listOf(blocked)), 60)

        assertEquals(StoryClockEventStatusV1.PENDING, scene.events.single().status)
    }

    @Test
    fun `public bridge never exposes private or targeted events`() {
        val scene = StoryClockSceneV1(
            sessionId = "s1",
            anchorTimeLabel = "14:00",
            currentMinute = 30,
            events = listOf(
                StoryClockEventV1(
                    id = "private",
                    title = "藏起钥匙",
                    summary = "顾宁把钥匙藏起来",
                    owner = "顾宁",
                    visibility = StoryClockEventVisibilityV1.PRIVATE,
                    dueMinute = 10,
                    status = StoryClockEventStatusV1.FIRED,
                    firedMinute = 10,
                ),
                StoryClockEventV1(
                    id = "target",
                    title = "短信送达",
                    summary = "程野收到短信",
                    participants = listOf("程野"),
                    visibility = StoryClockEventVisibilityV1.TARGETED,
                    dueMinute = 20,
                    status = StoryClockEventStatusV1.FIRED,
                    firedMinute = 20,
                ),
                StoryClockEventV1(
                    id = "public",
                    title = "医院封锁",
                    summary = "医院大厅开始封锁",
                    visibility = StoryClockEventVisibilityV1.PUBLIC,
                    dueMinute = 30,
                    status = StoryClockEventStatusV1.FIRED,
                    firedMinute = 30,
                ),
            ),
        )

        val block = renderStoryClockPublicNoteV1(scene)
        val merged = mergeStoryClockPublicNoteV1("作者备注", block)

        assertTrue(merged.contains("医院封锁"))
        assertFalse(merged.contains("藏起钥匙"))
        assertFalse(merged.contains("短信送达"))
        assertEquals(1, "【故事时钟公开因果｜导演层】".toRegex().findAll(merged).count())
    }

    @Test
    fun `delivery respects event visibility`() {
        val privateEvent = StoryClockEventV1(
            title = "秘密决定",
            summary = "顾宁改变计划",
            owner = "顾宁",
            visibility = StoryClockEventVisibilityV1.PRIVATE,
            dueMinute = 10,
        )
        val targeted = StoryClockEventV1(
            title = "消息",
            summary = "消息送达",
            owner = "顾宁",
            participants = listOf("程野"),
            visibility = StoryClockEventVisibilityV1.TARGETED,
            dueMinute = 10,
        )
        val public = StoryClockEventV1(
            title = "广播",
            summary = "大厅广播",
            participants = listOf("顾宁", "程野"),
            visibility = StoryClockEventVisibilityV1.PUBLIC,
            dueMinute = 10,
        )

        assertEquals(listOf("顾宁"), clockDeliveryRecipientsV1(privateEvent).map { it.first })
        assertEquals(setOf("顾宁", "程野"), clockDeliveryRecipientsV1(targeted).map { it.first }.toSet())
        assertTrue(clockDeliveryRecipientsV1(targeted).all { it.second == NpcMemoryPrivacyV1.PRIVATE })
        assertTrue(clockDeliveryRecipientsV1(public).all { it.second == NpcMemoryPrivacyV1.PUBLIC })
    }

    @Test
    fun `ai schedule parser rejects unknown private owner and filters participants`() {
        val invalidPrivate = parseStoryClockPlanChangeV1(
            value = "+30||医院||未来角色||||秘密行动||未来角色开始行动||产生影响||",
            visibility = StoryClockEventVisibilityV1.PRIVATE,
            allowedNames = setOf("顾宁", "程野"),
            validDependencyIds = emptySet(),
        )
        assertEquals(null, invalidPrivate)

        val targeted = parseStoryClockPlanChangeV1(
            value = "+20||医院||顾宁||程野,未来角色||短信送达||程野收到消息||程野得知线索||",
            visibility = StoryClockEventVisibilityV1.TARGETED,
            allowedNames = setOf("顾宁", "程野"),
            validDependencyIds = emptySet(),
        )!!
        assertEquals(setOf("顾宁", "程野"), targeted.participants.toSet())
        assertFalse(targeted.participants.contains("未来角色"))
    }
}
