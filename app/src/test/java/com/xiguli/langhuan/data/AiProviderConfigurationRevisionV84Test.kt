package com.xiguli.langhuan.data

import com.xiguli.langhuan.data.local.AiProviderEntity
import com.xiguli.langhuan.engine.ApiProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderConfigurationRevisionV84Test {
    private val existing = AiProviderEntity(
        id = "provider-v84",
        name = "稳定服务",
        baseUrl = "https://ai.example/v1",
        protocol = ApiProtocol.OPENAI_COMPATIBLE.name,
        model = "stable-model",
        temperature = 0.72,
        supportsJsonMode = true,
        isDefault = false,
        createdAt = 41L,
        updatedAt = 84L,
    )

    private fun request(
        model: String = existing.model,
        apiKey: String = "",
        makeDefault: Boolean = true,
    ) = ProviderSaveRequest(
        id = existing.id,
        name = existing.name,
        baseUrl = "${existing.baseUrl}/",
        protocol = ApiProtocol.OPENAI_COMPATIBLE,
        model = model,
        temperature = existing.temperature,
        supportsJsonMode = existing.supportsJsonMode,
        apiKey = apiKey,
        makeDefault = makeDefault,
    )

    @Test
    fun unchangedSaveAndDefaultSelectionKeepTheConfigurationRevision() {
        assertTrue(aiProviderConfigurationUnchangedV84(existing, request(), "stored-secret"))
        assertTrue(
            aiProviderConfigurationUnchangedV84(
                existing,
                request(apiKey = "stored-secret", makeDefault = false),
                "stored-secret",
            ),
        )
    }

    @Test
    fun modelOrCredentialChangeStillAdvancesTheConfigurationRevision() {
        assertFalse(
            aiProviderConfigurationUnchangedV84(
                existing,
                request(model = "replacement-model"),
                "stored-secret",
            ),
        )
        assertFalse(
            aiProviderConfigurationUnchangedV84(
                existing,
                request(apiKey = "replacement-secret"),
                "stored-secret",
            ),
        )
        assertFalse(aiProviderConfigurationUnchangedV84(null, request(), null))
    }
}
