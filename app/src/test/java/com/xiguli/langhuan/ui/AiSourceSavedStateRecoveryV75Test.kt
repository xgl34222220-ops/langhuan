package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSourceSavedStateRecoveryV75Test {
    @Test
    fun savedIdentityRestoresOnlyFromTheDurableSourceAndUsesItsCurrentName() {
        val durable = source("saved-v75", "持久书源新名称")

        val restored = restoredAiSavedSourceV75(listOf(durable), durable.id)

        assertEquals(durable, restored)
        assertNull(restoredAiSavedSourceV75(listOf(durable), "missing-v75"))
        assertNull(restoredAiSavedSourceV75(listOf(durable), ""))
    }

    @Test
    fun reconciliationRefreshesRenamesAndClearsADeletedSavedIdentity() {
        val renamed = source("saved-v75", "已更新名称")
        val stale = OnlineBooksStateV36(
            sources = listOf(renamed),
            aiSavedSourceId = renamed.id,
            aiSavedSourceName = "旧名称不得恢复",
        )

        val refreshed = reconcileAiSavedSourceV75(stale)
        assertEquals(renamed.id, refreshed.aiSavedSourceId)
        assertEquals(renamed.name, refreshed.aiSavedSourceName)

        val removed = reconcileAiSavedSourceV75(refreshed.copy(sources = emptyList()))
        assertNull(removed.aiSavedSourceId)
        assertNull(removed.aiSavedSourceName)
    }

    private fun source(id: String, name: String) = BookSourceV36(
        id = id,
        name = name,
        baseUrl = "https://$id.example.invalid",
    )
}
