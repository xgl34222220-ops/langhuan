package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProjectConversationStoreTest {
    @Test
    fun `creation handoff preserves conversation order and marks origin`() {
        val result = projectConversationHandoff(
            listOf(
                "assistant" to "先说说你想写什么。",
                "user" to "我想写群像悬疑。",
                "assistant" to "可以，先锁定核心矛盾。",
            )
        )

        assertEquals(3, result.size)
        assertEquals("assistant", result[0].role)
        assertEquals("user", result[1].role)
        assertEquals(ProjectConversationOrigin.CREATION, result[2].origin)
    }

    @Test
    fun `handoff strips hidden research context`() {
        val result = projectConversationHandoff(
            listOf(
                "user" to "分析这几本的共同点\n\n【琅嬛联网检索资料（隐藏上下文）】\n隐藏网页内容",
            )
        )

        assertEquals("分析这几本的共同点", result.single().text)
        assertFalse(result.single().text.contains("隐藏网页内容"))
    }

    @Test
    fun `handoff normalizes unknown roles to user`() {
        val result = projectConversationHandoff(listOf("tool" to "这是一条可见备注"))

        assertEquals("user", result.single().role)
    }
}
