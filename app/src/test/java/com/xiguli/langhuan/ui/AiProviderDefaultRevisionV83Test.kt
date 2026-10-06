package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.engine.ApiProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderDefaultRevisionV83Test {
    private fun provider(
        id: String,
        revision: Long,
        isDefault: Boolean,
    ) = StoredAiProvider(
        id = id,
        name = id,
        baseUrl = "https://ai.example/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        model = "model",
        temperature = 0.72,
        supportsJsonMode = true,
        isDefault = isDefault,
        hasApiKey = true,
        revision = revision,
    )

    @Test
    fun switchingDefaultAwayAndBackKeepsTheAcceptedAttemptCurrentWhenRevisionIsStable() {
        val attempt = AiProviderAttemptRevisionV81("provider-a", revision = 83L)
        val switchedAway = listOf(
            provider("provider-b", revision = 12L, isDefault = true),
            provider("provider-a", revision = 83L, isDefault = false),
        )
        val switchedBack = listOf(
            provider("provider-a", revision = 83L, isDefault = true),
            provider("provider-b", revision = 12L, isDefault = false),
        )

        assertTrue(aiProviderAttemptStillCurrentV81(attempt, switchedAway))
        assertTrue(aiProviderAttemptStillCurrentV81(attempt, switchedBack))
    }

    @Test
    fun editingTheAcceptedProviderStillInvalidatesAfterADefaultRoundTrip() {
        val attempt = AiProviderAttemptRevisionV81("provider-a", revision = 83L)
        val editedAfterSwitch = listOf(
            provider("provider-a", revision = 84L, isDefault = true),
            provider("provider-b", revision = 12L, isDefault = false),
        )

        assertFalse(aiProviderAttemptStillCurrentV81(attempt, editedAfterSwitch))
    }
}
