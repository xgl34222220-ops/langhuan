package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.StoredAiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModelCapabilityExactContextTest {
    @Test
    fun `provider reported context window overrides family estimate`() {
        val provider = StoredAiProvider(
            id = "provider-exact-context",
            name = "测试服务",
            baseUrl = "https://relay.example.com/v1",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            model = "claude-custom",
            temperature = 0.72,
            supportsJsonMode = true,
            isDefault = true,
            hasApiKey = true,
        )
        val discovered = DiscoveredModel(
            id = "claude-custom",
            displayName = "Claude Custom",
            contextWindow = 262_144,
        )

        val profile = ModelCapabilityProfiler.infer(provider, discovered.id, discovered)

        assertEquals(262_144, profile.contextWindow)
        assertFalse(profile.estimated)
    }
}
