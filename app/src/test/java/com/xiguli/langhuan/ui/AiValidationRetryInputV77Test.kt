package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiValidationRetryInputV77Test {
    private val checkpointState = OnlineBooksStateV36(
        aiError = "目录规则验证失败",
        aiLastUseBrowser = true,
        aiCanResumeValidatedRules = true,
        aiValidationRetryInput = aiValidationRetryInputV77(
            "https://reader.example",
            "原创小说",
            useBrowser = true,
        ),
    )

    @Test
    fun checkpointPromiseRequiresTheExactTrimmedInputAndTransport() {
        assertTrue(aiCanResumeValidatedRulesV77(
            checkpointState,
            "  https://reader.example ",
            " 原创小说 ",
            useBrowser = true,
        ))
        assertFalse(aiCanResumeValidatedRulesV77(
            checkpointState,
            "https://other.example",
            "原创小说",
            useBrowser = true,
        ))
        assertFalse(aiCanResumeValidatedRulesV77(
            checkpointState,
            "https://reader.example",
            "另一部小说",
            useBrowser = true,
        ))
        assertFalse(aiCanResumeValidatedRulesV77(
            checkpointState,
            "https://reader.example",
            "原创小说",
            useBrowser = false,
        ))
    }

    @Test
    fun editedInputDowngradesOnlyTheReusePromiseAndKeepsBothRetryModes() {
        val matching = aiSourceStartActionsV67(
            checkpointState,
            "https://reader.example",
            "原创小说",
        )
        assertEquals("保留已通过规则重试（浏览器）", matching.first().label)

        val edited = aiSourceStartActionsV67(
            checkpointState,
            "https://reader.example",
            "改过的书名",
        )
        assertEquals(
            listOf(
                AiSourceStartActionV67("重试浏览器模式", true),
                AiSourceStartActionV67("改用普通模式", false),
            ),
            edited,
        )
    }
}
