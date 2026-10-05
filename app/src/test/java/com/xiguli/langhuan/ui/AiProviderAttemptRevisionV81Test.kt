package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.engine.ApiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderAttemptRevisionV81Test {
    private fun provider(revision: Long, isDefault: Boolean = true) = StoredAiProvider(
        id = "accepted-provider",
        name = "已接受服务",
        baseUrl = "https://ai.example/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        model = "accepted-model",
        temperature = 0.72,
        supportsJsonMode = true,
        isDefault = isDefault,
        hasApiKey = true,
        revision = revision,
    )

    @Test
    fun deletionOrReconfigurationInvalidatesTheAcceptedAttemptButDefaultSwitchDoesNot() {
        val accepted = AiProviderAttemptRevisionV81("accepted-provider", revision = 41L)

        assertTrue(aiProviderAttemptStillCurrentV81(accepted, listOf(provider(41L))))
        assertTrue(aiProviderAttemptStillCurrentV81(accepted, listOf(provider(41L, isDefault = false))))
        assertFalse(aiProviderAttemptStillCurrentV81(accepted, emptyList()))
        assertFalse(aiProviderAttemptStillCurrentV81(accepted, listOf(provider(42L))))
    }

    @Test
    fun providerChangeStopsWithoutKeepingRulesOrAnObsoleteIdentity() {
        val changed = aiSourceProviderChangedStateV81(
            OnlineBooksStateV36(
                aiRunning = true,
                aiAttemptProviderLabel = "已接受服务 · accepted-model",
                aiLastUseBrowser = true,
                aiCanResumeValidatedRules = true,
                aiValidationRetryInput = AiValidationRetryInputV77(
                    siteUrl = "https://books.example",
                    keyword = "原创小说",
                    useBrowser = true,
                ),
            ),
        )

        assertFalse(changed.aiRunning)
        assertFalse(changed.aiStopped)
        assertEquals(true, changed.aiLastUseBrowser)
        assertEquals("本次使用的 AI 服务已被删除或修改，生成已停止；请确认服务后重试", changed.aiError)
        assertNull(changed.aiAttemptProviderLabel)
        assertFalse(changed.aiCanResumeValidatedRules)
        assertNull(changed.aiValidationRetryInput)
    }
}
