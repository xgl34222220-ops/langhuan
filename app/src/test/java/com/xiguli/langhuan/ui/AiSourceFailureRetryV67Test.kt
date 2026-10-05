package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSourceFailureRetryV67Test {
    @Test
    fun acceptedStartRecordsItsModeAndClearsOnlyThePreviousAttemptState() {
        val started = aiSourceStartingStateV67(
            OnlineBooksStateV36(
                query = "保留的搜索",
                aiSteps = listOf(AiSourceStepV37("旧步骤", completed = true)),
                aiError = "旧错误",
                aiStopped = true,
                aiCanResumeValidatedRules = true,
            ),
            useBrowser = true,
        )

        assertTrue(started.aiRunning)
        assertTrue(started.aiLastUseBrowser == true)
        assertTrue(started.aiSteps.isEmpty())
        assertNull(started.aiReport)
        assertNull(started.aiError)
        assertFalse(started.aiStopped)
        assertFalse(started.aiCanResumeValidatedRules)
        assertEquals("保留的搜索", started.query)
    }

    @Test
    fun terminalFailureRetriesTheSameTransportFirstAndKeepsTheAlternative() {
        val browser = aiSourceStartActionsV67(
            OnlineBooksStateV36(aiError = "动态正文验证失败", aiLastUseBrowser = true),
            "https://example.com",
            "测试书",
        )
        assertEquals(
            listOf(
                AiSourceStartActionV67("重试浏览器模式", true),
                AiSourceStartActionV67("改用普通模式", false),
            ),
            browser,
        )

        val normal = aiSourceStartActionsV67(
            OnlineBooksStateV36(aiError = "目录规则验证失败", aiLastUseBrowser = false),
            "https://example.com",
            "测试书",
        )
        assertEquals(
            listOf(
                AiSourceStartActionV67("重试普通模式", false),
                AiSourceStartActionV67("改用浏览器模式", true),
            ),
            normal,
        )

        val initial = aiSourceStartActionsV67(OnlineBooksStateV36(), "https://example.com", "测试书")
        assertEquals("开始生成", initial.first().label)
        assertEquals("浏览器模式生成", initial.last().label)

        val checkpoint = aiSourceStartActionsV67(
            OnlineBooksStateV36(
                aiError = "正文验证失败",
                aiLastUseBrowser = true,
                aiCanResumeValidatedRules = true,
                aiValidationRetryInput = aiValidationRetryInputV77("https://example.com", "测试书", true),
            ),
            "https://example.com",
            "测试书",
        )
        assertEquals("保留已通过规则重试（浏览器）", checkpoint.first().label)
        assertEquals(true, checkpoint.first().useBrowser)
        assertEquals("改用普通模式", checkpoint.last().label)

        val stopped = aiSourceStartActionsV67(
            aiSourceStoppedStateV65(
                OnlineBooksStateV36(aiLastUseBrowser = true, aiRunning = true),
            ),
            "https://example.com",
            "测试书",
        )
        assertEquals("开始生成", stopped.first().label)
        assertEquals("浏览器模式生成", stopped.last().label)
        assertFalse(aiSourceStoppedStateV65(OnlineBooksStateV36(aiCanResumeValidatedRules = true)).aiCanResumeValidatedRules)
    }
}
