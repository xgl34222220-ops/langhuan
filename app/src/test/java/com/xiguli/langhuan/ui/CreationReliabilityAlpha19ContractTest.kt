package com.xiguli.langhuan.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationReliabilityAlpha19ContractTest {
    @Test
    fun interruptedChatRetryReusesLogicalUserTurn() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val source = File(root, "src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt").readText()

        assertTrue(source.contains("failedRetryIndex"))
        assertTrue(source.contains("trailingFailedTurnMessages.all(::isProvisionalInterruptedReply)"))
        assertTrue(source.contains("before.messages.take(failedRetryIndex + 1)"))
        assertTrue(source.contains("INTERRUPTED_REPLY_MARKER"))
        assertTrue(source.contains("STOPPED_REPLY_MARKER"))
    }

    @Test
    fun retryTargetsChatProposalOrBlueprintAndLongWaitCanBeStopped() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val vm = File(root, "src/main/java/com/xiguli/langhuan/ui/NewBookConversation.kt").readText()
        val ui = File(root, "src/main/java/com/xiguli/langhuan/ui/CreationChatV4.kt").readText()

        assertTrue(vm.contains("enum class CreationRetryTarget { CHAT, PROPOSAL, BLUEPRINT }"))
        assertTrue(vm.contains("fun retryFailedOperation()"))
        assertTrue(vm.contains("CreationRetryTarget.BLUEPRINT -> generateFoundation"))
        assertTrue(vm.contains("fun cancelCurrentAiOperation()"))
        assertTrue(vm.contains("conversationJob?.cancel()"))
        assertTrue(vm.contains("foundationJob?.cancel()"))
        assertTrue(ui.contains("retryTarget = state.retryTarget"))
        assertTrue(ui.contains("Icons.Rounded.Stop"))
        assertTrue(ui.contains("onCancelCurrent = viewModel::cancelCurrentAiOperation"))
    }

    @Test
    fun blueprintDocumentationNoLongerClaimsInfiniteAppWait() {
        val root = File(System.getProperty("user.dir") ?: ".")
        val blueprint = File(root, "src/main/java/com/xiguli/langhuan/ui/ProgressiveFoundationEngine.kt").readText()
        assertFalse(blueprint.contains("不设置任何 App 侧生成时限"))
        assertTrue(blueprint.contains("较长但有限的容错窗口"))
    }
}
