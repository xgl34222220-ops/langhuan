package com.xiguli.langhuan.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSourceSavedManagementV74Test {
    @Test
    fun confirmedSaveCarriesTheExactStoredIdentityAndTheNextAttemptClearsIt() {
        val source = source("saved-v74", "刚保存书源")
        val saved = aiSourceSavedStateV68(OnlineBooksStateV36(), source)

        assertEquals(source.id, saved.aiSavedSourceId)
        assertEquals(source.name, saved.aiSavedSourceName)

        val restarted = aiSourceStartingStateV67(saved, useBrowser = true)
        assertNull(restarted.aiSavedSourceId)
        assertNull(restarted.aiSavedSourceName)

        val stopped = aiSourceStoppedStateV65(saved)
        assertNull(stopped.aiSavedSourceId)
        assertNull(stopped.aiSavedSourceName)
    }

    @Test
    fun managementFocusPinsOnlyTheExactSavedIdentityWithoutChangingStoredOrder() {
        val first = source("first-v74", "同名书源")
        val saved = source("saved-v74", "同名书源")
        val last = source("last-v74", "末尾书源")
        val durable = listOf(first, saved, last)

        val visible = sourceManageFocusOrderV74(durable, saved.id)

        assertEquals(listOf(saved.id, first.id, last.id), visible.map { it.id })
        assertEquals(listOf(first.id, saved.id, last.id), durable.map { it.id })
        assertEquals(durable, sourceManageFocusOrderV74(durable, "missing-v74"))
        assertEquals(durable, sourceManageFocusOrderV74(durable, null))
    }

    private fun source(id: String, name: String) = BookSourceV36(
        id = id,
        name = name,
        baseUrl = "https://$id.example.invalid",
    )
}
