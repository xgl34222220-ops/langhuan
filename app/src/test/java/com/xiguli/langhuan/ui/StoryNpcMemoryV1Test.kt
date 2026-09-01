package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryNpcMemoryV1Test {

    @Test
    fun `memory slice never returns another npc private memory`() {
        val scene = NpcMemorySceneV1(
            sessionId = "s1",
            memories = listOf(
                NpcLongMemoryV1(owner = "顾宁", summary = "我怀疑钥匙在林默手里", privacy = NpcMemoryPrivacyV1.PRIVATE),
                NpcLongMemoryV1(owner = "程野", summary = "我看见顾宁藏起了照片", privacy = NpcMemoryPrivacyV1.PRIVATE),
                NpcLongMemoryV1(owner = "顾宁", summary = "停电发生在九点", privacy = NpcMemoryPrivacyV1.PUBLIC),
            ),
        )

        val gu = memorySliceForNpcV1(scene, "顾宁")

        assertEquals(2, gu.memories.size)
        assertTrue(gu.memories.all { it.owner == "顾宁" })
        assertFalse(gu.memories.any { it.summary.contains("照片") })
    }

    @Test
    fun `memory changes reject owners outside current npc set`() {
        val scene = NpcMemorySceneV1(sessionId = "s1")
        val updated = applyNpcMemoryChangesV1(
            scene = scene,
            changes = listOf(
                NpcMemoryChangeV1("顾宁", "长期记忆:私有", "5|林默在门后说了暗号", "第3轮"),
                NpcMemoryChangeV1("未来角色", "长期记忆:私有", "5|我知道第80章的真相", "未来"),
            ),
            allowedOwners = setOf("顾宁"),
            turnId = "t3",
        )

        assertEquals(1, updated.memories.size)
        assertEquals("顾宁", updated.memories.single().owner)
        assertEquals(5, updated.memories.single().importance)
        assertFalse(updated.memories.any { it.summary.contains("第80章") })
    }

    @Test
    fun `duplicate long memories are compacted instead of endlessly appended`() {
        val original = NpcMemorySceneV1(
            sessionId = "s1",
            memories = listOf(
                NpcLongMemoryV1(owner = "顾宁", summary = "林默答应今晚回来", importance = 2),
            ),
        )
        val updated = applyNpcMemoryChangesV1(
            scene = original,
            changes = listOf(NpcMemoryChangeV1("顾宁", "长期记忆:私有", "5|林默答应今晚回来", "再次确认")),
            allowedOwners = setOf("顾宁"),
            turnId = "t4",
        )

        assertEquals(1, updated.memories.size)
        assertEquals(5, updated.memories.single().importance)
    }

    @Test
    fun `active private plan drives only its owner life state`() {
        val memory = NpcMemorySceneV1(
            sessionId = "s1",
            plans = listOf(
                NpcPlanV1(
                    owner = "顾宁",
                    goal = "确认地下室里的人是谁",
                    steps = listOf("先拿到钥匙", "等走廊没人时下楼"),
                    privateReason = "怀疑那个人和失踪案有关",
                    priority = 5,
                )
            ),
        )
        val states = listOf(
            NpcLifeStateV1("顾宁", currentGoal = "观察四周", hiddenIntent = "暂不表态"),
            NpcLifeStateV1("程野", currentGoal = "找水", hiddenIntent = "无"),
        )

        val updated = applyNpcPlansToLifeV1(states, memory)

        assertEquals("确认地下室里的人是谁", updated.first { it.name == "顾宁" }.currentGoal)
        assertTrue(updated.first { it.name == "顾宁" }.hiddenIntent.contains("先拿到钥匙"))
        assertEquals("找水", updated.first { it.name == "程野" }.currentGoal)
        assertEquals("无", updated.first { it.name == "程野" }.hiddenIntent)
    }
}
