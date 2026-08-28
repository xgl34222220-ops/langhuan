package com.xiguli.langhuan.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBlueprintSyncTest {
    @Test
    fun keepsNormalQuestionsFromInvalidatingBlueprint() {
        assertFalse(
            blueprintDirtyAfterConversation(
                alreadyDirty = false,
                hasFoundation = true,
                proposalUpdated = false,
            )
        )
    }

    @Test
    fun marksExistingBlueprintDirtyWhenConversationChangesProposal() {
        assertTrue(
            blueprintDirtyAfterConversation(
                alreadyDirty = false,
                hasFoundation = true,
                proposalUpdated = true,
            )
        )
    }

    @Test
    fun keepsDirtyStateAcrossFurtherConversation() {
        assertTrue(
            blueprintDirtyAfterConversation(
                alreadyDirty = true,
                hasFoundation = true,
                proposalUpdated = false,
            )
        )
    }
}
