package com.xiguli.langhuan.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Book entry and individual request identities, including an A -> B -> A navigation.
 * Owned by the ViewModel's main dispatcher, like the UI actions that enter or leave a book.
 */
internal class StoryRequestScope {
    data class Ticket(
        val novelId: String,
        val session: Long,
        val lane: String,
        val request: Long,
        val profileId: String? = null,
    )

    private var novelId = ""
    private var session = 0L
    private var sequence = 0L
    private val current = mutableMapOf<String, Ticket>()
    private val jobs = mutableMapOf<String, Job>()

    fun open(id: String) {
        novelId = id
        session++
        current.clear()
        val previous = jobs.values.toList()
        jobs.clear()
        previous.forEach(Job::cancel)
    }

    fun begin(lane: String, profileId: String? = null): Ticket {
        cancel(lane)
        return Ticket(novelId, session, lane, ++sequence, profileId).also { current[lane] = it }
    }

    fun current(lane: String): Ticket? = current[lane]

    fun isCurrent(ticket: Ticket): Boolean =
        ticket.novelId == novelId && ticket.session == session && current[ticket.lane] == ticket

    suspend fun ensureCurrent(ticket: Ticket) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(ticket)) throw CancellationException("Story request is no longer current")
    }

    fun cancel(lane: String) {
        current.remove(lane)
        jobs.remove(lane)?.cancel()
    }

    fun launch(scope: CoroutineScope, ticket: Ticket, block: suspend () -> Unit) {
        // Attach before execution so even an immediately completing coroutine has one owner.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            ensureCurrent(ticket)
            block()
        }
        if (isCurrent(ticket)) {
            jobs[ticket.lane] = job
            job.start()
        } else job.cancel()
    }
}
