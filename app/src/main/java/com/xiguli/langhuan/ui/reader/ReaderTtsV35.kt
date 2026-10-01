package com.xiguli.langhuan.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** One paragraph queued for speech, with its offset in the normalized chapter body. */
internal data class ReaderTtsChunkV35(val offset: Int, val text: String)

internal interface ReaderSpeechV47 {
    var rate: Float
    fun speak(items: List<ReaderTtsChunkV35>): Boolean
    fun stop()
    fun release()
}

internal fun interface ReaderSpeechFactoryV47 {
    fun create(context: Context, onReady: (Boolean) -> Unit, onChunkStart: (Int) -> Unit,
        onQueueDone: () -> Unit, onQueueError: () -> Unit): ReaderSpeechV47
}

internal val systemReaderSpeechFactoryV47 = ReaderSpeechFactoryV47 { context, ready, chunk, done, error ->
    ReaderTtsV35(context, ready, chunk, done, error)
}

/**
 * Splits [body] from [fromOffset] into speakable chunks: one per paragraph, long paragraphs cut at
 * sentence ends so no utterance exceeds the engine limit.
 */
internal fun readerTtsChunksV35(body: String, fromOffset: Int, maxChars: Int = 600): List<ReaderTtsChunkV35> {
    if (body.isEmpty()) return emptyList()
    val result = ArrayList<ReaderTtsChunkV35>()
    var cursor = fromOffset.coerceIn(0, body.length)
    // Start from the beginning of the paragraph the page starts in, so nothing is half-read.
    if (cursor > 0) cursor = body.lastIndexOf('\n', cursor - 1) + 1
    while (cursor < body.length) {
        val end = body.indexOf('\n', cursor).let { if (it < 0) body.length else it }
        var start = cursor
        while (start < end) {
            var stop = minOf(end, start + maxChars)
            if (stop < end) {
                val cut = body.substring(start, stop).lastIndexOfAny(charArrayOf('。', '！', '？', '…', '；', '.', '!', '?'))
                if (cut > maxChars / 3) stop = start + cut + 1
            }
            val text = body.substring(start, stop).trim().trim('\u3000')
            if (text.isNotEmpty()) result += ReaderTtsChunkV35(start, text)
            start = stop
        }
        cursor = end + 1
    }
    return result
}

/** The Android boundary is replaceable so tests exercise the production queue and callbacks. */
internal interface ReaderTtsEngineV48 {
    fun useChinese(): Boolean
    fun setRate(rate: Float)
    fun setCallbacks(start: (String?) -> Unit, done: (String?) -> Unit, error: (String?) -> Unit)
    fun enqueue(text: String, id: String): Boolean
    fun stop()
    fun release()
}

private class AndroidReaderTtsEngineV48(context: Context, initialized: (Boolean) -> Unit) : ReaderTtsEngineV48 {
    private val tts = TextToSpeech(context.applicationContext) { initialized(it == TextToSpeech.SUCCESS) }
    override fun useChinese(): Boolean {
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }
    override fun setRate(rate: Float) { tts.setSpeechRate(rate) }
    override fun setCallbacks(start: (String?) -> Unit, done: (String?) -> Unit, error: (String?) -> Unit) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = start(utteranceId)
            override fun onDone(utteranceId: String?) = done(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = error(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = error(utteranceId)
        })
    }
    override fun enqueue(text: String, id: String) = tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS
    override fun stop() { tts.stop() }
    override fun release() { tts.shutdown() }
}

/**
 * A queue completes only when every chunk succeeds. Any enqueue/utterance failure stops it and
 * reports [onQueueError] separately. All engine callbacks are serialized onto the main thread;
 * stopping, replacing a queue or releasing also invalidates callbacks already posted there.
 */
internal class ReaderTtsV35(
    private val onReady: (Boolean) -> Unit,
    private val onChunkStart: (Int) -> Unit,
    private val onQueueDone: () -> Unit,
    private val onQueueError: () -> Unit,
    private val postToMain: (() -> Unit) -> Unit,
    createEngine: ((Boolean) -> Unit) -> ReaderTtsEngineV48,
) : ReaderSpeechV47 {
    constructor(context: Context, onReady: (Boolean) -> Unit, onChunkStart: (Int) -> Unit,
        onQueueDone: () -> Unit, onQueueError: () -> Unit) : this(
        onReady, onChunkStart, onQueueDone, onQueueError,
        { action -> Handler(Looper.getMainLooper()).post(action) },
        { initialized -> AndroidReaderTtsEngineV48(context, initialized) },
    )

    private var chunks: List<ReaderTtsChunkV35> = emptyList()
    private var completed = BooleanArray(0)
    private var generation = 0
    private var active = false
    private var ready = false
    private var released = false
    override var rate: Float = 1f
        set(value) {
            field = value
            if (!released) engine.setRate(value)
        }

    // Posting initialization also avoids accessing the engine before its constructor returns.
    private val engine: ReaderTtsEngineV48 = createEngine { initialized ->
        postToMain {
            if (!released) {
                ready = initialized && engine.useChinese()
                if (!ready) stop()
                onReady(ready)
            }
        }
    }

    init {
        engine.setCallbacks(
            start = { id -> withChunk(id) { index -> onChunkStart(chunks[index].offset) } },
            done = { id -> withChunk(id) { index ->
                completed[index] = true
                if (completed.all { it }) {
                    invalidateQueue()
                    onQueueDone()
                }
            } },
            error = { id -> withChunk(id) { failQueue() } },
        )
    }

    private fun withChunk(id: String?, action: (Int) -> Unit) {
        val parts = id?.split(':') ?: return
        if (parts.size != 2) return
        val gen = parts[0].toIntOrNull() ?: return
        val index = parts[1].toIntOrNull() ?: return
        postToMain {
            if (!released && active && gen == generation && index in chunks.indices) action(index)
        }
    }

    private fun invalidateQueue() {
        generation++
        active = false
        chunks = emptyList()
        completed = BooleanArray(0)
    }

    private fun failQueue() {
        invalidateQueue()
        engine.stop()
        val failedGeneration = generation
        postToMain {
            if (!released && generation == failedGeneration) onQueueError()
        }
    }

    /** Replaces the current queue. False also covers an engine rejecting any individual chunk. */
    override fun speak(items: List<ReaderTtsChunkV35>): Boolean {
        if (!ready || released) return false
        stop()
        chunks = items.toList()
        completed = BooleanArray(items.size)
        active = true
        val queuedGeneration = generation
        for ((index, chunk) in chunks.withIndex()) {
            if (!engine.enqueue(chunk.text, "$queuedGeneration:$index")) {
                failQueue()
                return false
            }
        }
        if (items.isEmpty()) postToMain {
            if (!released && active && generation == queuedGeneration) {
                invalidateQueue()
                onQueueDone()
            }
        }
        return true
    }

    override fun stop() {
        invalidateQueue()
        if (!released) engine.stop()
    }

    override fun release() {
        if (released) return
        stop()
        ready = false
        released = true
        engine.release()
    }
}
