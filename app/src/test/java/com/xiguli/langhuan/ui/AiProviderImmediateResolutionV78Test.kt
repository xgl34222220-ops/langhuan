package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.selectAiProviderIdV78
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiProviderImmediateResolutionV78Test {
    @Test
    fun missingOrStaleObservedIdFallsBackToTheCurrentPriorityProvider() {
        val providers = listOf("default-current", "newer-secondary")

        assertEquals("default-current", selectAiProviderIdV78(providers, null))
        assertEquals("default-current", selectAiProviderIdV78(providers, "already-removed"))
        assertNull(selectAiProviderIdV78(emptyList(), "already-removed"))
    }

    @Test
    fun aStillCurrentObservedIdRemainsTheExplicitPreference() {
        val providers = listOf("default-current", "selected-current", "older-secondary")

        assertEquals(
            "selected-current",
            selectAiProviderIdV78(providers, "selected-current"),
        )
    }
}
