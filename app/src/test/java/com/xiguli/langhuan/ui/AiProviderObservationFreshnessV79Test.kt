package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.selectAiProviderIdV79
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiProviderObservationFreshnessV79Test {
    @Test
    fun changedPrioritySnapshotUsesTheCurrentDefaultEvenWhenTheOldIdStillExists() {
        assertEquals(
            "current-default",
            selectAiProviderIdV79(
                currentProviderIdsInPriorityOrder = listOf("current-default", "old-default"),
                observedProviderIdsInPriorityOrder = listOf("old-default", "current-default"),
                preferredId = "old-default",
            ),
        )
    }

    @Test
    fun matchingSnapshotRetainsTheObservedPreferenceAndEmptyCurrentStateRemainsEmpty() {
        val current = listOf("default", "explicit-current", "secondary")

        assertEquals(
            "explicit-current",
            selectAiProviderIdV79(
                currentProviderIdsInPriorityOrder = current,
                observedProviderIdsInPriorityOrder = current,
                preferredId = "explicit-current",
            ),
        )
        assertNull(
            selectAiProviderIdV79(
                currentProviderIdsInPriorityOrder = emptyList(),
                observedProviderIdsInPriorityOrder = current,
                preferredId = "explicit-current",
            ),
        )
    }
}
