package com.xiguli.langhuan.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebResearchEngineTest {
    @Test
    fun extractsBareWorkNamesBeforeCoreSettingRequest() {
        val engine = WebResearchEngine()

        assertEquals(
            listOf("迷雾之上", "神秘复苏"),
            engine.referenceTargets("搜迷雾之上和神秘复苏的核心设定"),
        )
    }

    @Test
    fun explicitWorksWinOverTrailingPronoun() {
        val engine = WebResearchEngine()

        assertEquals(
            listOf("神秘复苏", "迷雾之上"),
            engine.referenceTargets("乱写是吗？神秘复苏和迷雾之上设定融合你知不知道，他们的特点是什么核心设定是什么"),
        )
    }

    @Test
    fun pluralPronounResolvesToPreviousWorks() {
        val engine = WebResearchEngine()
        engine.referenceTargets("搜迷雾之上和神秘复苏的核心设定")

        assertEquals(
            listOf("迷雾之上", "神秘复苏"),
            engine.referenceTargets("他们的特点和核心设定分别是什么"),
        )
    }

    @Test
    fun unresolvedPronounIsNotAWorkTitle() {
        assertTrue(WebResearchEngine().referenceTargets("他们的核心设定是什么").isEmpty())
    }
}
