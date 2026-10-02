package com.xiguli.langhuan.engine

import android.content.Context
import android.content.SharedPreferences
import com.xiguli.langhuan.domain.AutonomousStoryPlan
import com.xiguli.langhuan.domain.ChapterExecutionRecord
import com.xiguli.langhuan.domain.GenerationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class DurableRunPhase {
    GENERATING,
    READY_TO_COMMIT,
    COMMITTING,
    REVIEWING,
    INTERRUPTED,
    COMPLETE,
}

@Serializable
enum class RunResumePolicy {
    CONTINUE_GENERATION,
    RESTORE_RESULT,
    RESUME_POST_COMMIT,
    RESUME_REVIEW,
    NONE,
}

/** Model outputs already completed inside GenerationPipeline. */
@Serializable
data class GenerationStageCheckpoint(
    val logicPlanAttempted: Boolean = false,
    val logicPlanText: String = "",
    val draftProse: String = "",
    val novelizationAttempted: Boolean = false,
    val postNovelizationProse: String = "",
    val novelizationSucceeded: Boolean = false,
    val firstReviewAttempted: Boolean = false,
    val firstReview: com.xiguli.langhuan.domain.GeneratedChapter? = null,
    val editorRewriteAttempted: Boolean = false,
    val editorRewriteProse: String = "",
    val secondReviewAttempted: Boolean = false,
    val secondReview: com.xiguli.langhuan.domain.GeneratedChapter? = null,
    val metadataAttempted: Boolean = false,
    val metadataSucceeded: Boolean = false,
    val metadata: com.xiguli.langhuan.domain.GeneratedChapter? = null,
    val modelAttributions: List<com.xiguli.langhuan.domain.ModelUsageAttribution> = emptyList(),
    val telemetrySignals: List<String> = emptyList(),
)

@Serializable
data class DurableRunEvent(
    val stage: String,
    val status: String,
    val detail: String = "",
    val atMillis: Long = 0L,
) {
    fun toUi(): RunEvent? {
        val parsedStage = runCatching { RunStage.valueOf(stage) }.getOrNull() ?: return null
        val parsedStatus = runCatching { RunStatus.valueOf(status) }.getOrNull() ?: return null
        return RunEvent(parsedStage, parsedStatus, detail, atMillis)
    }

    companion object {
        fun from(event: RunEvent) = DurableRunEvent(
            stage = event.stage.name,
            status = event.status.name,
            detail = event.detail,
            atMillis = event.atMillis,
        )
    }
}

/**
 * Crash-safe execution state. It is operational metadata only: never Canon and never RAG input.
 * Model outputs are saved before their side effects are applied, so resume can reuse them without
 * repeating paid calls.
 */
@Serializable
data class ChapterRunCheckpoint(
    val runId: String,
    val novelId: String,
    val chapterNumber: Int,
    val inputFingerprint: String,
    val phase: DurableRunPhase = DurableRunPhase.GENERATING,
    val currentStage: String = RunStage.CONTEXT.name,
    val completedStages: List<String> = emptyList(),
    val events: List<DurableRunEvent> = emptyList(),
    val partialPreview: String = "",
    val generation: GenerationStageCheckpoint = GenerationStageCheckpoint(),
    val generationResult: GenerationResult? = null,
    val savedDraftVersion: Int = 0,
    val executionRecord: ChapterExecutionRecord? = null,
    val agentReview: AgentReview? = null,
    val autonomousPlan: AutonomousStoryPlan? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val note: String = "",
)

data class ChapterRunRecovery(
    val runId: String,
    val policy: RunResumePolicy,
    val result: GenerationResult?,
    val preview: String,
    val events: List<RunEvent>,
    val message: String,
)

interface ChapterRunCheckpointStore {
    fun load(novelId: String, chapterNumber: Int): ChapterRunCheckpoint?
    fun save(checkpoint: ChapterRunCheckpoint)
    fun clear(novelId: String, chapterNumber: Int)

    /** Enumerates operational checkpoints for Run Center. Implementations that cannot list may stay empty. */
    fun list(): List<ChapterRunCheckpoint> = emptyList()
}

object NoopChapterRunCheckpointStore : ChapterRunCheckpointStore {
    override fun load(novelId: String, chapterNumber: Int): ChapterRunCheckpoint? = null
    override fun save(checkpoint: ChapterRunCheckpoint) = Unit
    override fun clear(novelId: String, chapterNumber: Int) = Unit
}

/** Small durable checkpoint file backed by app-private SharedPreferences; not part of project Canon. */
class PersistentChapterRunCheckpointStore internal constructor(
    private val prefs: SharedPreferences,
) : ChapterRunCheckpointStore {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("langhuan_chapter_run_checkpoints", Context.MODE_PRIVATE)
    )
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override fun load(novelId: String, chapterNumber: Int): ChapterRunCheckpoint? {
        val raw = prefs.getString(key(novelId, chapterNumber), null) ?: return null
        return decode(raw, key(novelId, chapterNumber))
    }

    override fun list(): List<ChapterRunCheckpoint> {
        return prefs.all.entries
            .mapNotNull { (key, value) ->
                val raw = value as? String ?: return@mapNotNull null
                decode(raw, key)
            }
            .filter { it.phase != DurableRunPhase.COMPLETE }
            .sortedByDescending { it.updatedAt }
    }

    override fun save(checkpoint: ChapterRunCheckpoint) {
        val safe = checkpoint.copy(
            partialPreview = checkpoint.partialPreview.takeLast(30_000),
            events = checkpoint.events.takeLast(96),
            updatedAt = System.currentTimeMillis(),
        )
        // commit(), not apply(): a checkpoint must be durable before the next paid/side-effect stage starts.
        val committed = prefs.edit().putString(
            key(safe.novelId, safe.chapterNumber),
            json.encodeToString(ChapterRunCheckpoint.serializer(), safe),
        ).commit()
        if (!committed) throw ChapterRunCheckpointWriteException("保存")
    }

    override fun clear(novelId: String, chapterNumber: Int) {
        remove(key(novelId, chapterNumber))
    }

    private fun decode(raw: String, storageKey: String): ChapterRunCheckpoint? {
        return runCatching { json.decodeFromString(ChapterRunCheckpoint.serializer(), raw) }
            .onFailure { remove(storageKey) }
            .getOrNull()
    }

    private fun remove(storageKey: String) {
        if (!prefs.edit().remove(storageKey).commit()) throw ChapterRunCheckpointWriteException("清理")
    }

    private fun key(novelId: String, chapterNumber: Int) = "$novelId:$chapterNumber"
}

/** A missing durability acknowledgement must stop the run before another paid call or side effect. */
internal class ChapterRunCheckpointWriteException(operation: String) : IllegalStateException(
    "章节断点${operation}未能确认写入磁盘，已停止执行；请检查可用存储空间后重试。"
)
