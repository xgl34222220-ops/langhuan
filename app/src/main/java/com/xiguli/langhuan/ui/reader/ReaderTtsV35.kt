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
    fun create(context: Context, onReady: (Boolean) -> Unit, onChunkStart: (Int) -> Unit, onQueueDone: () -> Unit): ReaderSpeechV47
}

internal val systemReaderSpeechFactoryV47 = ReaderSpeechFactoryV47 { context, ready, chunk, done ->
    ReaderTtsV35(context, ready, chunk, done)
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

/**
 * Thin wrapper over [TextToSpeech]. Callbacks arrive on the main thread.
 * [onChunkStart] reports the body offset being read; [onQueueDone] fires after the last chunk.
 */
internal class ReaderTtsV35(
    context: Context,
    private val onReady: (Boolean) -> Unit,
    private val onChunkStart: (Int) -> Unit,
    private val onQueueDone: () -> Unit,
) : ReaderSpeechV47 {
    private val main = Handler(Looper.getMainLooper())
    private var chunks: List<ReaderTtsChunkV35> = emptyList()
    private var generation = 0
    private var ready = false
    override var rate: Float = 1f
        set(value) {
            field = value
            tts.setSpeechRate(value)
        }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
        main.post { onReady(ready) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val (gen, index) = parse(utteranceId) ?: return
                main.post { if (gen == generation) chunks.getOrNull(index)?.let { onChunkStart(it.offset) } }
            }

            override fun onDone(utteranceId: String?) {
                val (gen, index) = parse(utteranceId) ?: return
                main.post { if (gen == generation && index == chunks.lastIndex) onQueueDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onDone(utteranceId)
        })
    }

    private fun parse(id: String?): Pair<Int, Int>? {
        val parts = id?.split(':') ?: return null
        return (parts.getOrNull(0)?.toIntOrNull() ?: return null) to (parts.getOrNull(1)?.toIntOrNull() ?: return null)
    }

    /** Replaces whatever is queued with [items] and starts speaking. Returns false if TTS is unusable. */
    override fun speak(items: List<ReaderTtsChunkV35>): Boolean {
        if (!ready) return false
        generation++
        chunks = items
        tts.stop()
        items.forEachIndexed { index, chunk ->
            tts.speak(chunk.text, TextToSpeech.QUEUE_ADD, null, "$generation:$index")
        }
        if (items.isEmpty()) main.post(readerSpeechCompletionV47(generation, { generation }, onQueueDone))
        return true
    }

    override fun stop() {
        generation++
        tts.stop()
    }

    override fun release() {
        stop()
        tts.shutdown()
    }
}
