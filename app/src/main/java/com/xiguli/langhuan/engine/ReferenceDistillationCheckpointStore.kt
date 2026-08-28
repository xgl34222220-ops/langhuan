package com.xiguli.langhuan.engine

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReferenceDistillationCheckpoint(
    val fingerprint: String,
    val title: String,
    val chapters: Int,
    val samples: Int,
    val providerId: String,
    val provider: String,
    val model: String,
    val completedBatches: Int,
    val totalBatches: Int,
    val observations: List<String>,
    val localMetrics: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Persists only distilled observations and progress. It never stores imported novel prose, so a
 * retry can resume from the next AI batch without turning the reference text into permanent memory.
 */
class ReferenceDistillationCheckpointStore(context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val root = File(context.filesDir, "distillation/checkpoints").apply { mkdirs() }

    fun load(fingerprint: String): ReferenceDistillationCheckpoint? {
        if (fingerprint.isBlank()) return null
        val atomic = AtomicFile(file(fingerprint))
        return runCatching {
            if (!atomic.baseFile.exists()) return@runCatching null
            atomic.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                json.decodeFromString<ReferenceDistillationCheckpoint>(reader.readText())
            }
        }.getOrNull()
    }

    fun save(checkpoint: ReferenceDistillationCheckpoint) {
        if (checkpoint.fingerprint.isBlank()) return
        val atomic = AtomicFile(file(checkpoint.fingerprint))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(json.encodeToString(checkpoint).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (_: Throwable) {
            stream?.let(atomic::failWrite)
        }
    }

    fun clear(fingerprint: String) {
        if (fingerprint.isBlank()) return
        runCatching { file(fingerprint).delete() }
    }

    private fun file(fingerprint: String): File = File(
        root,
        fingerprint.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96) + ".json",
    )
}
