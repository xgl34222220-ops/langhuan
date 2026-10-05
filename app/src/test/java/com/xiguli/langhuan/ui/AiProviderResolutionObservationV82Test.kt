package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.engine.ApiProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderResolutionObservationV82Test {
    private fun provider(revision: Long) = StoredAiProvider(
        id = "resolved-provider",
        name = "已解析服务",
        baseUrl = "https://ai.example/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        model = "resolved-model",
        temperature = 0.72,
        supportsJsonMode = true,
        isDefault = true,
        hasApiKey = true,
        revision = revision,
    )

    @Test
    fun staleObservationFromBeforeResolutionCannotStopTheCurrentDatabaseRevision() {
        val resolved = AiProviderAttemptRevisionV81("resolved-provider", revision = 52L)

        assertFalse(
            aiProviderObservationInvalidatesResolvedAttemptV82(
                attempt = resolved,
                providers = listOf(provider(revision = 51L)),
                observationVersionAtResolutionStart = 8L,
                currentObservationVersion = 8L,
            ),
        )
        assertFalse(
            aiProviderObservationInvalidatesResolvedAttemptV82(
                attempt = resolved,
                providers = emptyList(),
                observationVersionAtResolutionStart = 8L,
                currentObservationVersion = 8L,
            ),
        )
    }

    @Test
    fun observationDeliveredDuringResolutionStillStopsADeletedOrReconfiguredService() {
        val resolved = AiProviderAttemptRevisionV81("resolved-provider", revision = 52L)

        assertTrue(
            aiProviderObservationInvalidatesResolvedAttemptV82(
                attempt = resolved,
                providers = emptyList(),
                observationVersionAtResolutionStart = 8L,
                currentObservationVersion = 9L,
            ),
        )
        assertTrue(
            aiProviderObservationInvalidatesResolvedAttemptV82(
                attempt = resolved,
                providers = listOf(provider(revision = 53L)),
                observationVersionAtResolutionStart = 8L,
                currentObservationVersion = 9L,
            ),
        )
        assertFalse(
            aiProviderObservationInvalidatesResolvedAttemptV82(
                attempt = resolved,
                providers = listOf(provider(revision = 52L)),
                observationVersionAtResolutionStart = 8L,
                currentObservationVersion = 9L,
            ),
        )
    }
}
