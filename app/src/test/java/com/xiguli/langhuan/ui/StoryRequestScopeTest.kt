package com.xiguli.langhuan.ui

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRequestScopeTest {
    @Test
    fun changingBooksCancelsAnInFlightCooperativeGateway() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = StoryRequestScope()
        var cancelled = false
        val gateway = object : AiGateway {
            override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        requests.open("A")
        val request = requests.begin("distill")
        requests.launch(scope, request) { gateway.generate(PromptBundle("synthetic", "A")) }
        requests.open("B")
        assertTrue(cancelled)
        assertFalse(requests.isCurrent(request))
        scope.cancel()
    }

    @Test
    fun returningToTheSameBookCannotCommitAnOldNonCooperativeReply() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = StoryRequestScope()
        val gateway = DelayedGateway()
        val persisted = mutableListOf<String>()
        requests.open("A")
        val old = requests.begin("chat", "profile")
        requests.launch(scope, old) {
            val reply = gateway.generate(PromptBundle("synthetic", "A"))
            requests.ensureCurrent(old)
            persisted += reply.content
        }
        requests.open("B")
        requests.open("A")
        gateway.complete("stale")
        assertTrue(persisted.isEmpty())
        scope.cancel()
    }

    @Test
    fun clearingAChatCancelsOnlyThatLaneAndAllowsItsNextRequest() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = StoryRequestScope()
        val oldGateway = DelayedGateway()
        val newGateway = DelayedGateway()
        val persisted = mutableListOf<String>()
        requests.open("A")
        val distill = requests.begin("distill")
        val old = requests.begin("chat", "profile")
        requests.launch(scope, old) {
            val reply = oldGateway.generate(PromptBundle("synthetic", "old"))
            requests.ensureCurrent(old)
            persisted += reply.content
        }
        requests.cancel("chat")
        val current = requests.begin("chat", "profile")
        requests.launch(scope, current) {
            val reply = newGateway.generate(PromptBundle("synthetic", "new"))
            requests.ensureCurrent(current)
            persisted += reply.content
        }
        oldGateway.complete("stale")
        newGateway.complete("current")
        assertTrue(requests.isCurrent(distill))
        assertEquals(listOf("current"), persisted)
        scope.cancel()
    }

    @Test
    fun anObsoleteRequestCannotStartAiAfterANewerBookOpens() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = StoryRequestScope()
        requests.open("A")
        val old = requests.begin("load")
        requests.open("B")
        var started = false
        requests.launch(scope, old) { started = true }
        assertFalse(started)
        scope.cancel()
    }

    @Test
    fun aCompletedPreviewRetainsItsIdentityUntilDismissedOrReplaced() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = StoryRequestScope()
        requests.open("A")
        val preview = requests.begin("distill")
        requests.launch(scope, preview) { requests.ensureCurrent(preview) }
        assertTrue(requests.isCurrent(preview))
        requests.begin("distill")
        assertFalse(requests.isCurrent(preview))
        scope.cancel()
    }

    /** Deliberately ignores Job cancellation, as a callback adapter can do. */
    private class DelayedGateway : AiGateway {
        private lateinit var continuation: Continuation<GeneratedChapter>
        override suspend fun generate(prompt: PromptBundle): GeneratedChapter =
            suspendCoroutine { continuation = it }
        fun complete(content: String) = continuation.resume(GeneratedChapter("", content, ""))
    }
}
