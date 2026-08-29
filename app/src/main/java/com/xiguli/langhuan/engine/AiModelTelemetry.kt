package com.xiguli.langhuan.engine

import android.content.Context
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.ModelUsageAttribution
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
enum class AiQualitySignal {
    QUALITY_PASSED,
    QUALITY_REJECTED,
    NOVELIZATION_REQUIRED,
    REWRITE_REQUIRED,
    PIPELINE_BLOCKED,
    USER_ACCEPTED,
}

@Serializable
data class ModelTaskTelemetry(
    val providerId: String,
    val modelId: String,
    val task: AiTaskType,
    val calls: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val cancellations: Int = 0,
    val emptyOutputs: Int = 0,
    val totalDurationMs: Long = 0L,
    val firstTokenSamples: Int = 0,
    val totalFirstTokenMs: Long = 0L,
    val totalOutputChars: Long = 0L,
    val structuredAttempts: Int = 0,
    val structuredSuccesses: Int = 0,
    val qualityPasses: Int = 0,
    val qualityRejects: Int = 0,
    val novelizationRequired: Int = 0,
    val rewriteRequired: Int = 0,
    val pipelineBlocked: Int = 0,
    val userAccepted: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val successRate: Double get() = if (calls == 0) 0.0 else successes.toDouble() / calls
    val nonEmptyRate: Double get() = if (successes == 0) 0.0 else (successes - emptyOutputs).coerceAtLeast(0).toDouble() / successes
    val averageDurationMs: Long get() = if (calls == 0) 0L else totalDurationMs / calls
    val averageFirstTokenMs: Long get() = if (firstTokenSamples == 0) 0L else totalFirstTokenMs / firstTokenSamples
    val charsPerSecond: Double get() = if (totalDurationMs <= 0) 0.0 else totalOutputChars * 1000.0 / totalDurationMs
    val structuredRate: Double get() = if (structuredAttempts == 0) 0.0 else structuredSuccesses.toDouble() / structuredAttempts
    val qualitySamples: Int get() = qualityPasses + qualityRejects
    val qualityPassRate: Double get() = if (qualitySamples == 0) 0.0 else qualityPasses.toDouble() / qualitySamples
}

@Serializable
private data class ModelTelemetryConfig(
    val stats: List<ModelTaskTelemetry> = emptyList(),
    /** Bounded idempotency ledger for successful formal-save adoption signals. */
    val acceptedKeys: List<String> = emptyList(),
)

data class ModelRecommendation(
    val task: AiTaskType,
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val score: Int,
    val calls: Int,
    val confidence: String,
    val reason: String,
)

data class AiCallTelemetrySample(
    val attribution: ModelUsageAttribution,
    val success: Boolean,
    val cancelled: Boolean,
    val emptyOutput: Boolean,
    val durationMs: Long,
    val firstTokenMs: Long? = null,
    val outputChars: Int = 0,
    val structuredAttempt: Boolean = false,
    val structuredSuccess: Boolean = false,
)

/**
 * Application-private empirical model statistics. These records are operational preferences only:
 * never Canon, never project data and never RAG input.
 */
class AiModelTelemetryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun all(): List<ModelTaskTelemetry> = load().stats

    @Synchronized
    fun stats(task: AiTaskType): List<ModelTaskTelemetry> = load().stats.filter { it.task == task }

    @Synchronized
    fun recordCall(sample: AiCallTelemetrySample) {
        val task = runCatching { AiTaskType.valueOf(sample.attribution.task) }.getOrNull() ?: return
        val current = load()
        val old = current.stats.lastOrNull {
            it.providerId == sample.attribution.providerId && it.modelId == sample.attribution.modelId && it.task == task
        } ?: ModelTaskTelemetry(sample.attribution.providerId, sample.attribution.modelId, task)
        val next = old.copy(
            calls = old.calls + 1,
            successes = old.successes + if (sample.success) 1 else 0,
            failures = old.failures + if (!sample.success && !sample.cancelled) 1 else 0,
            cancellations = old.cancellations + if (sample.cancelled) 1 else 0,
            emptyOutputs = old.emptyOutputs + if (sample.success && sample.emptyOutput) 1 else 0,
            totalDurationMs = old.totalDurationMs + sample.durationMs.coerceAtLeast(0L),
            firstTokenSamples = old.firstTokenSamples + if (sample.firstTokenMs != null) 1 else 0,
            totalFirstTokenMs = old.totalFirstTokenMs + (sample.firstTokenMs ?: 0L).coerceAtLeast(0L),
            totalOutputChars = old.totalOutputChars + sample.outputChars.coerceAtLeast(0),
            structuredAttempts = old.structuredAttempts + if (sample.structuredAttempt) 1 else 0,
            structuredSuccesses = old.structuredSuccesses + if (sample.structuredAttempt && sample.structuredSuccess) 1 else 0,
            updatedAt = System.currentTimeMillis(),
        )
        replace(current, next)
    }

    @Synchronized
    fun recordUserAccepted(attribution: ModelUsageAttribution, acceptanceKey: String) {
        if (acceptanceKey.isBlank()) return
        val task = runCatching { AiTaskType.valueOf(attribution.task) }.getOrNull() ?: return
        val current = load()
        val scopedKey = "${attribution.providerId}|${attribution.modelId}|${attribution.task}|$acceptanceKey"
        if (scopedKey in current.acceptedKeys) return
        val old = current.stats.lastOrNull {
            it.providerId == attribution.providerId && it.modelId == attribution.modelId && it.task == task
        } ?: ModelTaskTelemetry(attribution.providerId, attribution.modelId, task)
        val next = old.copy(userAccepted = old.userAccepted + 1, updatedAt = System.currentTimeMillis())
        val kept = current.stats.filterNot {
            it.providerId == next.providerId && it.modelId == next.modelId && it.task == next.task
        }
        save(
            current.copy(
                stats = (kept + next).sortedByDescending { it.updatedAt }.take(500),
                acceptedKeys = (current.acceptedKeys + scopedKey).takeLast(2_000),
            )
        )
    }

    @Synchronized
    fun recordSignal(attribution: ModelUsageAttribution, signal: AiQualitySignal) {
        val task = runCatching { AiTaskType.valueOf(attribution.task) }.getOrNull() ?: return
        val current = load()
        val old = current.stats.lastOrNull {
            it.providerId == attribution.providerId && it.modelId == attribution.modelId && it.task == task
        } ?: ModelTaskTelemetry(attribution.providerId, attribution.modelId, task)
        val next = when (signal) {
            AiQualitySignal.QUALITY_PASSED -> old.copy(qualityPasses = old.qualityPasses + 1)
            AiQualitySignal.QUALITY_REJECTED -> old.copy(qualityRejects = old.qualityRejects + 1)
            AiQualitySignal.NOVELIZATION_REQUIRED -> old.copy(novelizationRequired = old.novelizationRequired + 1)
            AiQualitySignal.REWRITE_REQUIRED -> old.copy(rewriteRequired = old.rewriteRequired + 1)
            AiQualitySignal.PIPELINE_BLOCKED -> old.copy(pipelineBlocked = old.pipelineBlocked + 1)
            AiQualitySignal.USER_ACCEPTED -> old.copy(userAccepted = old.userAccepted + 1)
        }.copy(updatedAt = System.currentTimeMillis())
        replace(current, next)
    }

    @Synchronized
    fun recommendation(task: AiTaskType, providers: List<StoredAiProvider>): ModelRecommendation? {
        val providerById = providers.associateBy { it.id }
        val candidates = stats(task).filter { it.calls > 0 && providerById[it.providerId] != null }
        return candidates.maxByOrNull { score(task, it) }?.let { stats ->
            val provider = requireNotNull(providerById[stats.providerId])
            val score = score(task, stats).roundToInt().coerceIn(0, 100)
            val confidence = when {
                stats.calls >= 12 -> "高置信"
                stats.calls >= 5 -> "中等样本"
                else -> "样本较少"
            }
            ModelRecommendation(
                task = task,
                providerId = stats.providerId,
                providerName = provider.name,
                modelId = stats.modelId,
                score = score,
                calls = stats.calls,
                confidence = confidence,
                reason = recommendationReason(task, stats),
            )
        }
    }

    private fun score(task: AiTaskType, s: ModelTaskTelemetry): Double {
        val reliability = s.successRate * 35.0
        val nonEmpty = s.nonEmptyRate * 10.0
        val speed = when {
            s.averageFirstTokenMs in 1..3_000 -> 10.0
            s.averageFirstTokenMs in 3_001..8_000 -> 7.0
            s.averageFirstTokenMs > 8_000 -> 3.0
            else -> 5.0
        }
        val throughput = (s.charsPerSecond / 35.0).coerceIn(0.0, 1.0) * 10.0
        val structuredTasks = setOf(AiTaskType.EDITOR_REVIEW, AiTaskType.FACT_EXTRACTION, AiTaskType.AGENT_EXTRACTION)
        val quality = when {
            task == AiTaskType.PROSE_AUTHOR && s.qualitySamples > 0 -> s.qualityPassRate * 22.0
            task in structuredTasks && s.structuredAttempts > 0 -> s.structuredRate * 22.0
            else -> 11.0
        }
        val penalties = when (task) {
            AiTaskType.PROSE_AUTHOR -> (s.novelizationRequired * 2.0 + s.rewriteRequired * 3.0 + s.pipelineBlocked * 5.0)
                .div(s.calls.coerceAtLeast(1)).coerceAtMost(15.0)
            else -> 0.0
        }
        val adoption = if (task == AiTaskType.PROSE_AUTHOR && s.calls > 0) {
            (s.userAccepted.toDouble() / s.calls).coerceIn(0.0, 1.0) * 5.0
        } else 2.5
        val confidence = (s.calls / 10.0).coerceIn(0.0, 1.0) * 8.0
        return reliability + nonEmpty + speed + throughput + quality + adoption + confidence - penalties
    }

    private fun recommendationReason(task: AiTaskType, s: ModelTaskTelemetry): String = buildString {
        append("成功率 ").append((s.successRate * 100).roundToInt()).append('%')
        if (s.averageFirstTokenMs > 0) append(" · 首字 ").append(s.averageFirstTokenMs).append("ms")
        if (s.charsPerSecond > 0) append(" · ").append(s.charsPerSecond.roundToInt()).append("字/s")
        if (task == AiTaskType.PROSE_AUTHOR && s.qualitySamples > 0) {
            append(" · 一审通过 ").append((s.qualityPassRate * 100).roundToInt()).append('%')
        }
        if (task in setOf(AiTaskType.EDITOR_REVIEW, AiTaskType.FACT_EXTRACTION, AiTaskType.AGENT_EXTRACTION) && s.structuredAttempts > 0) {
            append(" · 结构化成功 ").append((s.structuredRate * 100).roundToInt()).append('%')
        }
    }

    private fun replace(current: ModelTelemetryConfig, next: ModelTaskTelemetry) {
        val kept = current.stats.filterNot {
            it.providerId == next.providerId && it.modelId == next.modelId && it.task == next.task
        }
        save(current.copy(stats = (kept + next).sortedByDescending { it.updatedAt }.take(500)))
    }

    private fun load(): ModelTelemetryConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return ModelTelemetryConfig()
        return runCatching { json.decodeFromString(ModelTelemetryConfig.serializer(), raw) }
            .getOrElse {
                prefs.edit().remove(KEY_CONFIG).commit()
                ModelTelemetryConfig()
            }
    }

    private fun save(config: ModelTelemetryConfig) {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(ModelTelemetryConfig.serializer(), config)).commit()
    }

    private companion object {
        const val PREFS = "langhuan_ai_model_telemetry"
        const val KEY_CONFIG = "telemetry_v1"
    }
}

interface AiTaskAttributionSource {
    fun modelAttributions(): List<ModelUsageAttribution>
}

interface AiTaskQualityFeedback {
    fun recordQualitySignal(task: AiTaskType, signal: AiQualitySignal)
}

/** One task + one provider/model wrapper. It records exactly one sample per public gateway call. */
class TelemetryAiGateway(
    private val delegate: AiGateway,
    private val attribution: ModelUsageAttribution,
    private val store: AiModelTelemetryStore,
) : AiGateway {
    override suspend fun generate(prompt: PromptBundle): GeneratedChapter = trackStructured {
        delegate.generate(prompt)
    }

    override suspend fun generateText(prompt: PromptBundle): String = trackText {
        delegate.generateText(prompt)
    }

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): GeneratedChapter {
        val started = System.nanoTime()
        var firstTokenMs: Long? = null
        return try {
            val result = delegate.generateStreaming(prompt) { partial ->
                if (firstTokenMs == null && partial.isNotBlank()) firstTokenMs = elapsedMs(started)
                onDelta(partial)
            }
            record(
                success = true,
                cancelled = false,
                empty = chapterEmpty(result),
                started = started,
                firstTokenMs = firstTokenMs,
                outputChars = chapterChars(result),
                structuredAttempt = true,
                structuredSuccess = !chapterEmpty(result),
            )
            result
        } catch (cancelled: CancellationException) {
            record(false, true, true, started, firstTokenMs, 0, true, false)
            throw cancelled
        } catch (error: Throwable) {
            record(false, false, true, started, firstTokenMs, 0, true, false)
            throw error
        }
    }

    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val started = System.nanoTime()
        var firstTokenMs: Long? = null
        return try {
            val result = delegate.generateTextStreaming(prompt) { partial ->
                if (firstTokenMs == null && partial.isNotBlank()) firstTokenMs = elapsedMs(started)
                onDelta(partial)
            }
            record(true, false, result.isBlank(), started, firstTokenMs, result.length, false, false)
            result
        } catch (cancelled: CancellationException) {
            record(false, true, true, started, firstTokenMs, 0, false, false)
            throw cancelled
        } catch (error: Throwable) {
            record(false, false, true, started, firstTokenMs, 0, false, false)
            throw error
        }
    }

    private suspend fun trackStructured(block: suspend () -> GeneratedChapter): GeneratedChapter {
        val started = System.nanoTime()
        return try {
            val result = block()
            val empty = chapterEmpty(result)
            record(true, false, empty, started, null, chapterChars(result), true, !empty)
            result
        } catch (cancelled: CancellationException) {
            record(false, true, true, started, null, 0, true, false)
            throw cancelled
        } catch (error: Throwable) {
            record(false, false, true, started, null, 0, true, false)
            throw error
        }
    }

    private suspend fun trackText(block: suspend () -> String): String {
        val started = System.nanoTime()
        return try {
            val result = block()
            record(true, false, result.isBlank(), started, null, result.length, false, false)
            result
        } catch (cancelled: CancellationException) {
            record(false, true, true, started, null, 0, false, false)
            throw cancelled
        } catch (error: Throwable) {
            record(false, false, true, started, null, 0, false, false)
            throw error
        }
    }

    private fun record(
        success: Boolean,
        cancelled: Boolean,
        empty: Boolean,
        started: Long,
        firstTokenMs: Long?,
        outputChars: Int,
        structuredAttempt: Boolean,
        structuredSuccess: Boolean,
    ) {
        store.recordCall(
            AiCallTelemetrySample(
                attribution = attribution,
                success = success,
                cancelled = cancelled,
                emptyOutput = empty,
                durationMs = elapsedMs(started),
                firstTokenMs = firstTokenMs,
                outputChars = outputChars,
                structuredAttempt = structuredAttempt,
                structuredSuccess = structuredSuccess,
            )
        )
    }

    private fun chapterEmpty(chapter: GeneratedChapter): Boolean =
        chapter.title.isBlank() && chapter.content.isBlank() && chapter.summary.isBlank() && chapter.stateChanges.isEmpty()

    private fun chapterChars(chapter: GeneratedChapter): Int =
        chapter.title.length + chapter.content.length + chapter.summary.length +
            chapter.stateChanges.sumOf { it.subject.length + it.field.length + it.before.length + it.after.length + it.evidence.length }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started).coerceAtLeast(0L) / 1_000_000L
}
