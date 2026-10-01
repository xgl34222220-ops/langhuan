package com.xiguli.langhuan.ui

import org.junit.Assert.*
import org.junit.Test

/** Exercises ReaderTtsV35 itself. The fake replaces only Android's audio-service boundary. */
class ReaderTtsFailureV48Test {
    private class Engine(val initialized: (Boolean) -> Unit) : ReaderTtsEngineV48 {
        var chinese = true
        var rejectAt = -1
        var stops = 0
        var releases = 0
        val ids = mutableListOf<String>()
        lateinit var start: (String?) -> Unit
        lateinit var done: (String?) -> Unit
        lateinit var error: (String?) -> Unit
        override fun useChinese() = chinese
        override fun setRate(rate: Float) = Unit
        override fun setCallbacks(start: (String?) -> Unit, done: (String?) -> Unit, error: (String?) -> Unit) {
            this.start = start; this.done = done; this.error = error
        }
        override fun enqueue(text: String, id: String): Boolean {
            ids += id
            return ids.lastIndex != rejectAt
        }
        override fun stop() { stops++ }
        override fun release() { releases++ }
    }
    private class Fixture(initialize: Boolean = true) {
        val tasks = java.util.ArrayDeque<() -> Unit>()
        val readiness = mutableListOf<Boolean>()
        val offsets = mutableListOf<Int>()
        var completions = 0
        var errors = 0
        lateinit var engine: Engine
        val reader = ReaderTtsV35(
            { readiness += it }, { offsets += it }, { completions++ }, { errors++ },
            { tasks.add(it) }, { initialized -> Engine(initialized).also { engine = it } },
        )
        init { if (initialize) { engine.initialized(true); drain() } }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst()() }
        fun speak(size: Int = 3): List<String> {
            val before = engine.ids.size
            assertTrue(reader.speak((0 until size).map { ReaderTtsChunkV35(it * 100, "Public fixture $it.") }))
            return engine.ids.drop(before)
        }
    }

    @Test fun errorInAnyChunkStopsQueueAndNeverCompletes() {
        for (failed in 0..2) {
            val f = Fixture()
            val ids = f.speak()
            val stops = f.engine.stops
            f.engine.error(ids[failed]); f.drain()
            assertEquals(1, f.errors)
            assertEquals(stops + 1, f.engine.stops)
            ids.forEach { f.engine.done(it); f.engine.error(it); f.engine.start(it) }; f.drain()
            assertEquals(0, f.completions)
            assertEquals(1, f.errors)
            assertTrue(f.offsets.isEmpty())
        }
    }

    @Test fun engineRejectingAnyChunkReturnsFalseStopsQueueAndReportsError() {
        for (failed in 0..2) {
            val f = Fixture()
            f.engine.rejectAt = failed
            assertFalse(f.reader.speak((0..2).map { ReaderTtsChunkV35(it, "Fixture.") }))
            assertEquals(failed + 1, f.engine.ids.size)
            f.engine.ids.forEach { f.engine.done(it); f.engine.error(it) }; f.drain()
            assertEquals(1, f.errors)
            assertEquals(0, f.completions)
        }
    }

    @Test fun manualRetryWorksAndLateEventsFromFailedQueueCannotAffectIt() {
        val f = Fixture()
        val old = f.speak()
        f.engine.error(old[1]); f.drain()
        val current = f.speak()
        old.forEach { f.engine.error(it); f.engine.done(it); f.engine.start(it) }
        current.forEach { f.engine.start(it); f.engine.done(it) }; f.drain()
        assertEquals(listOf(0, 100, 200), f.offsets)
        assertEquals(1, f.errors)
        assertEquals(1, f.completions)
    }

    @Test fun successRequiresEveryChunkAndCanOnlyBeDeliveredOnce() {
        val f = Fixture()
        val ids = f.speak()
        f.engine.done(ids.last()); f.engine.done(ids.last()); f.drain()
        assertEquals(0, f.completions)
        f.engine.done(ids[0]); f.drain()
        assertEquals(0, f.completions)
        f.engine.done(ids[1]); f.drain()
        ids.forEach { f.engine.done(it); f.engine.error(it) }; f.drain()
        assertEquals(1, f.completions)
        assertEquals(0, f.errors)
    }

    @Test fun lastDoneBeforeEarlierErrorDoesNotCompleteTheChapter() {
        val f = Fixture()
        val ids = f.speak()
        f.engine.done(ids.last()); f.engine.error(ids.first()); f.drain()
        assertEquals(0, f.completions)
        assertEquals(1, f.errors)
    }

    @Test fun stopInvalidatesErrorsAlreadyPostedAndThoseArrivingLater() {
        val f = Fixture()
        val ids = f.speak()
        f.engine.error(ids.first())
        f.reader.stop()
        ids.forEach { f.engine.error(it); f.engine.done(it) }; f.drain()
        assertEquals(0, f.errors)
        assertEquals(0, f.completions)
    }

    @Test fun replacementInvalidatesPostedErrorsBeforeTheyCanStopNewQueue() {
        val f = Fixture()
        val old = f.speak()
        f.engine.error(old.first())
        val current = f.speak()
        val stops = f.engine.stops
        f.drain()
        assertEquals(stops, f.engine.stops)
        assertEquals(0, f.errors)
        current.forEach(f.engine.done); f.drain()
        assertEquals(1, f.completions)
    }

    @Test fun stopOrNewQueueAlsoCancelsAnEnqueueFailureNotification() {
        for (retry in listOf(false, true)) {
            val f = Fixture()
            f.engine.rejectAt = 0
            assertFalse(f.reader.speak(listOf(ReaderTtsChunkV35(0, "Fixture."))))
            if (retry) f.speak() else f.reader.stop()
            f.drain()
            assertEquals(0, f.errors)
            assertEquals(0, f.completions)
        }
    }

    @Test fun releaseIgnoresPostedAndLateEngineEventsAndIsIdempotent() {
        val f = Fixture()
        val ids = f.speak()
        f.engine.error(ids.first())
        f.reader.release(); f.reader.release()
        f.engine.initialized(false); f.engine.initialized(true)
        ids.forEach { f.engine.done(it); f.engine.error(it); f.engine.start(it) }; f.drain()
        assertEquals(listOf(true), f.readiness)
        assertEquals(1, f.engine.releases)
        assertFalse(f.reader.speak(emptyList()))
        assertEquals(0, f.errors)
        assertEquals(0, f.completions)
    }

    @Test fun initializationOrMissingLanguageFailureNeverStartsOrCompletesSpeech() {
        for (missingLanguage in listOf(false, true)) {
            val f = Fixture(initialize = false)
            assertFalse(f.reader.speak(emptyList()))
            f.engine.chinese = !missingLanguage
            f.engine.initialized(missingLanguage); f.drain()
            assertEquals(listOf(false), f.readiness)
            assertFalse(f.reader.speak(listOf(ReaderTtsChunkV35(0, "Fixture."))))
            assertTrue(f.engine.ids.isEmpty())
            assertEquals(0, f.completions)
        }
    }

    @Test fun releaseDuringInitializationIgnoresItsLateResult() {
        val f = Fixture(initialize = false)
        f.reader.release()
        f.engine.initialized(true); f.drain()
        assertTrue(f.readiness.isEmpty())
        assertFalse(f.reader.speak(emptyList()))
    }

    @Test fun emptyLocalChapterCompletesButOldEmptyQueueCannotFinishRetry() {
        val f = Fixture()
        f.speak(size = 0); f.reader.stop()
        val ids = f.speak()
        f.drain()
        assertEquals(0, f.completions)
        ids.forEach(f.engine.done); f.drain()
        f.speak(size = 0); f.drain()
        assertEquals(2, f.completions)
        assertEquals(0, f.errors)
    }

    @Test fun malformedOrOutOfRangeUtteranceIdsAreIgnored() {
        val f = Fixture()
        val ids = f.speak()
        val gen = ids.first().substringBefore(':')
        for (id in listOf(null, "", "broken", "$gen:-1", "$gen:3", "$gen:0:extra")) {
            f.engine.error(id); f.engine.done(id); f.engine.start(id)
        }
        f.drain()
        assertEquals(0, f.errors)
        assertEquals(0, f.completions)
        assertTrue(f.offsets.isEmpty())
    }
}
