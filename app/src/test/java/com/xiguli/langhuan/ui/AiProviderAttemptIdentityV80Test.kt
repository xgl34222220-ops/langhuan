package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.aiProviderLabelV80
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderAttemptIdentityV80Test {
    @Test
    fun resolvedIdentityUsesTheSameNameAndModelShownToTheUser() {
        assertEquals(
            "隔离服务 · isolated-model",
            aiProviderLabelV80("隔离服务", "isolated-model"),
        )
        assertEquals("isolated-model", aiProviderLabelV80("", "isolated-model"))
    }

    @Test
    fun activeAttemptIdentityWinsOnlyWhileThatAttemptIsRunning() {
        val active = OnlineBooksStateV36(
            aiProviderLabel = "新默认服务 · next-model",
            aiAttemptProviderLabel = "当前尝试服务 · attempt-model",
            aiRunning = true,
        )
        assertEquals("当前尝试服务 · attempt-model", displayedAiProviderLabelV80(active))
        assertEquals(
            "新默认服务 · next-model",
            displayedAiProviderLabelV80(active.copy(aiRunning = false)),
        )
    }
}
