from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'missing marker: {label}')
    return text.replace(old, new, 1)

# 1) Durable checkpoint model/store. Stored outside Canon and Room schema.
write('app/src/main/java/com/xiguli/langhuan/engine/ChapterRunCheckpointStore.kt', r'''package com.xiguli.langhuan.engine

import android.content.Context
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
    INTERRUPTED,
    COMPLETE,
}

@Serializable
enum class RunResumePolicy {
    CONTINUE_GENERATION,
    RESTORE_RESULT,
    RESUME_POST_COMMIT,
    NONE,
}

/** Model outputs already completed inside GenerationPipeline. */
@Serializable
data class GenerationStageCheckpoint(
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
}

object NoopChapterRunCheckpointStore : ChapterRunCheckpointStore {
    override fun load(novelId: String, chapterNumber: Int): ChapterRunCheckpoint? = null
    override fun save(checkpoint: ChapterRunCheckpoint) = Unit
    override fun clear(novelId: String, chapterNumber: Int) = Unit
}

/** Small durable checkpoint file backed by app-private SharedPreferences; not part of project Canon. */
class PersistentChapterRunCheckpointStore(context: Context) : ChapterRunCheckpointStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "langhuan_chapter_run_checkpoints",
        Context.MODE_PRIVATE,
    )
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override fun load(novelId: String, chapterNumber: Int): ChapterRunCheckpoint? {
        val raw = prefs.getString(key(novelId, chapterNumber), null) ?: return null
        return runCatching { json.decodeFromString(ChapterRunCheckpoint.serializer(), raw) }
            .onFailure { prefs.edit().remove(key(novelId, chapterNumber)).commit() }
            .getOrNull()
    }

    override fun save(checkpoint: ChapterRunCheckpoint) {
        val safe = checkpoint.copy(
            partialPreview = checkpoint.partialPreview.takeLast(30_000),
            events = checkpoint.events.takeLast(96),
            updatedAt = System.currentTimeMillis(),
        )
        // commit(), not apply(): a checkpoint must be durable before the next paid/side-effect stage starts.
        prefs.edit().putString(
            key(safe.novelId, safe.chapterNumber),
            json.encodeToString(ChapterRunCheckpoint.serializer(), safe),
        ).commit()
    }

    override fun clear(novelId: String, chapterNumber: Int) {
        prefs.edit().remove(key(novelId, chapterNumber)).commit()
    }

    private fun key(novelId: String, chapterNumber: Int) = "$novelId:$chapterNumber"
}
''')

# 2) Agent review must be serializable because a paid review is checkpointed before Candidate side effects.
path = 'app/src/main/java/com/xiguli/langhuan/engine/NovelAgentEngine.kt'
text = read(path)
if 'import kotlinx.serialization.Serializable' not in text:
    text = text.replace('import com.xiguli.langhuan.domain.StorySnapshot\n', 'import com.xiguli.langhuan.domain.StorySnapshot\nimport kotlinx.serialization.Serializable\n')
for marker in ['enum class AgentActionKind', 'data class AgentAction(', 'data class AgentNextOption(', 'data class AgentReview(']:
    text = text.replace(marker, '@Serializable\n' + marker, 1)
write(path, text)

# 3) Chapter save idempotency token (JSON field only; no Room schema migration).
path = 'app/src/main/java/com/xiguli/langhuan/domain/StoryModels.kt'
text = read(path)
text = replace_once(
    text,
    '    /** 0.25.4 起新增；为空时会由章纲和当前状态生成有效合同。 */\n    val contract: ChapterContract = ChapterContract(),\n)',
    '    /** 0.25.4 起新增；为空时会由章纲和当前状态生成有效合同。 */\n    val contract: ChapterContract = ChapterContract(),\n    /** 最近一次正式正文提交的运行 id；只用于 exactly-once 保存，不属于 Canon。 */\n    val lastCommittedRunId: String = "",\n)',
    'ChapterDraft lastCommittedRunId',
)
write(path, text)

# 4) PersistentStoryRepository save becomes exactly-once by runId.
path = 'app/src/main/java/com/xiguli/langhuan/data/PersistentStoryRepository.kt'
text = read(path)
text = replace_once(
    text,
    '    suspend fun commitGenerated(\n        snapshot: StorySnapshot,\n        draft: ChapterDraft,\n        generated: GeneratedChapter,\n    ): PersistedStory {\n        val now = System.currentTimeMillis()\n        val latestVersion = chapterDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version\n',
    '    suspend fun commitGenerated(\n        snapshot: StorySnapshot,\n        draft: ChapterDraft,\n        generated: GeneratedChapter,\n        runId: String = "",\n    ): PersistedStory {\n        val existingDraft = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraftOrNull()\n        if (runId.isNotBlank() && existingDraft?.lastCommittedRunId == runId) {\n            val storedSnapshot = storyDao.get(draft.novelId)?.let { entity ->\n                runCatching { StoreJson.decodeFromString(StorySnapshot.serializer(), entity.snapshotJson) }.getOrNull()\n            } ?: snapshot\n            return PersistedStory(storedSnapshot, existingDraft)\n        }\n        val now = System.currentTimeMillis()\n        val latestVersion = chapterDao.forChapter(draft.novelId, draft.chapterNumber).firstOrNull()?.version ?: draft.version\n',
    'commitGenerated signature/idempotency',
)
text = replace_once(
    text,
    '            summary = generated.summary,\n            version = newVersion,\n        )\n        val previous = chapterStateDao.get(draft.novelId, draft.chapterNumber)?.decodeDraftOrNull() ?: draft\n',
    '            summary = generated.summary,\n            version = newVersion,\n            lastCommittedRunId = runId.ifBlank { draft.lastCommittedRunId },\n        )\n        val previous = existingDraft ?: draft\n',
    'commitGenerated newDraft token',
)
write(path, text)

# 5) GenerationPipeline becomes stage-resumable. Completed model outputs are reused after restart.
path = 'app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt'
text = read(path)
start = text.index('    suspend fun generate(\n')
end = text.index('    private fun cleanVisibleProse', start)
method = r'''    suspend fun generate(
        request: GenerationRequest,
        retrievedContext: List<RetrievedContextItem> = emptyList(),
        onDelta: (String) -> Unit = {},
        onRunEvent: (RunEvent) -> Unit = {},
        resumeCheckpoint: GenerationStageCheckpoint = GenerationStageCheckpoint(),
        onCheckpoint: (GenerationStageCheckpoint) -> Unit = {},
    ): GenerationResult {
        fun emit(stage: RunStage, status: RunStatus, detail: String = "") {
            onRunEvent(RunEvent(stage = stage, status = status, detail = detail))
        }
        var checkpoint = resumeCheckpoint
        fun persist(next: GenerationStageCheckpoint) {
            checkpoint = next
            onCheckpoint(next)
        }

        // 1) Draft prose. If this model call already completed, never send it again.
        val draftProse = if (checkpoint.draftProse.isNotBlank()) {
            val restored = cleanVisibleProse(checkpoint.draftProse)
            onDelta(restored)
            emit(RunStage.DRAFT, RunStatus.SUCCESS, "从持久化断点恢复初稿 ${restored.length} 字；未重复请求模型")
            restored
        } else {
            emit(RunStage.DRAFT, RunStatus.RUNNING, "S/A/B/C/D 上下文已锁定，模型开始返回正文")
            val generated = cleanVisibleProse(
                aiGateway.generateTextStreaming(promptAssembler.buildProse(request, retrievedContext)) { partial ->
                    val visible = cleanVisibleProse(partial)
                    if (visible.isNotBlank()) onDelta(visible)
                }
            )
            require(generated.isNotBlank()) { "AI 没有返回可用正文" }
            persist(checkpoint.copy(draftProse = generated))
            onDelta(generated)
            emit(RunStage.DRAFT, RunStatus.SUCCESS, "初稿 ${generated.length} 字")
            generated
        }

        val initialQuality = novelizationEngine.analyze(draftProse)
        var prose = draftProse
        var novelizationSucceeded = checkpoint.novelizationSucceeded

        // 2) Novelization. Attempt outcome (including an empty/failed response) is checkpointed.
        if (initialQuality.requiresNovelization) {
            if (checkpoint.novelizationAttempted) {
                prose = checkpoint.postNovelizationProse.ifBlank { draftProse }
                novelizationSucceeded = checkpoint.novelizationSucceeded
                onDelta(prose)
                emit(
                    RunStage.NOVELIZATION,
                    if (novelizationSucceeded) RunStatus.SUCCESS else RunStatus.WARNING,
                    if (novelizationSucceeded) "从断点恢复小说化重构；未重复请求模型" else "上次小说化重构未得到可用文本；恢复初稿继续",
                )
            } else {
                emit(RunStage.NOVELIZATION, RunStatus.RUNNING, initialQuality.problems.take(3).joinToString("；"))
                onDelta("")
                val novelized = runCatching {
                    cleanVisibleProse(
                        aiGateway.generateTextStreaming(
                            novelizationEngine.buildRewrite(request, draftProse, initialQuality, retrievedContext)
                        ) { partial ->
                            val visible = cleanVisibleProse(partial)
                            if (visible.isNotBlank()) onDelta(visible)
                        }
                    )
                }.getOrNull().orEmpty()
                novelizationSucceeded = novelized.isNotBlank()
                prose = novelized.ifBlank { draftProse }
                persist(
                    checkpoint.copy(
                        novelizationAttempted = true,
                        postNovelizationProse = prose,
                        novelizationSucceeded = novelizationSucceeded,
                    )
                )
                onDelta(prose)
                emit(
                    RunStage.NOVELIZATION,
                    if (novelizationSucceeded) RunStatus.SUCCESS else RunStatus.WARNING,
                    if (novelizationSucceeded) "重构后小说化评分 ${novelizationEngine.analyze(prose).score} 分" else "小说化重构未返回可用文本，保留初稿交给主编继续检查",
                )
            }
        } else {
            if (!checkpoint.novelizationAttempted) {
                persist(checkpoint.copy(novelizationAttempted = true, postNovelizationProse = draftProse))
            }
            prose = draftProse
            emit(RunStage.NOVELIZATION, RunStatus.SKIPPED, "初稿未命中报告体 / AI 腔阈值")
        }

        val preEditorProse = prose
        var quality = novelizationEngine.analyze(preEditorProse)

        // 3) First adversarial review. A null response is also a completed attempt and is not replayed.
        val firstReview = if (checkpoint.firstReviewAttempted) {
            emit(RunStage.EDITOR_REVIEW_1, RunStatus.RUNNING, "恢复已完成的一审结果")
            checkpoint.firstReview
        } else {
            emit(RunStage.EDITOR_REVIEW_1, RunStatus.RUNNING, "结构 / 人物 / 文字 / 连续性四席同时审稿")
            val reviewed = runCatching {
                aiGateway.generate(adversarialEditor.buildReview(request, preEditorProse, round = 1))
            }.getOrNull()
            persist(checkpoint.copy(firstReviewAttempted = true, firstReview = reviewed))
            reviewed
        }
        val firstDeterministic = buildList {
            addAll(obviousProseProblems(preEditorProse))
            if (quality.requiresNovelization) addAll(quality.problems)
        }.distinct()
        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()
        emit(
            RunStage.EDITOR_REVIEW_1,
            when {
                firstReview == null -> RunStatus.WARNING
                firstRejected -> RunStatus.WARNING
                else -> RunStatus.SUCCESS
            },
            when {
                checkpoint.firstReviewAttempted && firstReview != null && !firstRejected -> "一审结果从断点复用 · 四席通过"
                firstReview == null -> "AI 主编响应失败；仍会执行本地确定性质量规则"
                firstRejected -> "主编退回：${adversarialEditor.instructions(firstReview, firstDeterministic).take(220)}"
                else -> "四席通过"
            },
        )

        var editorBlockingIssue: ConsistencyIssue? = null
        if (firstRejected) {
            val instructions = adversarialEditor.instructions(firstReview, firstDeterministic)
            val rewritten = if (checkpoint.editorRewriteAttempted) {
                checkpoint.editorRewriteProse
            } else {
                emit(RunStage.EDITOR_REWRITE, RunStatus.RUNNING, "按主编意见从头修订整章")
                onDelta("")
                val value = runCatching {
                    cleanVisibleProse(
                        aiGateway.generateTextStreaming(
                            promptAssembler.buildRewrite(request, preEditorProse, instructions, retrievedContext)
                        ) { partial ->
                            val visible = cleanVisibleProse(partial)
                            if (visible.isNotBlank()) onDelta(visible)
                        }
                    )
                }.getOrNull().orEmpty()
                persist(checkpoint.copy(editorRewriteAttempted = true, editorRewriteProse = value))
                value
            }

            if (rewritten.isBlank()) {
                prose = preEditorProse
                onDelta(prose)
                emit(RunStage.EDITOR_REWRITE, RunStatus.FAILED, "主编已退回，但修订请求没有返回可用正文")
                emit(RunStage.EDITOR_REVIEW_2, RunStatus.SKIPPED, "没有可复审的修订稿")
                editorBlockingIssue = ConsistencyIssue(
                    severity = IssueSeverity.BLOCKING,
                    code = "EDITOR_REWRITE_EMPTY",
                    message = "主编已退回稿件，但 AI 没有返回可用的修订稿",
                    evidence = instructions.take(800),
                    repairInstruction = "保留当前草稿但禁止正式保存；重新生成或切换模型后再次执行章节生成。",
                )
            } else {
                prose = cleanVisibleProse(rewritten)
                quality = novelizationEngine.analyze(prose)
                onDelta(prose)
                emit(
                    RunStage.EDITOR_REWRITE,
                    RunStatus.SUCCESS,
                    if (checkpoint.editorRewriteAttempted) "从断点恢复修订稿 ${prose.length} 字" else "修订稿 ${prose.length} 字",
                )

                val secondReview = if (checkpoint.secondReviewAttempted) {
                    checkpoint.secondReview
                } else {
                    emit(RunStage.EDITOR_REVIEW_2, RunStatus.RUNNING, "修订稿重新进入四席复审；不会无限循环改写")
                    val reviewed = runCatching {
                        aiGateway.generate(adversarialEditor.buildReview(request, prose, round = 2))
                    }.getOrNull()
                    persist(checkpoint.copy(secondReviewAttempted = true, secondReview = reviewed))
                    reviewed
                }
                val secondDeterministic = buildList {
                    addAll(obviousProseProblems(prose))
                    if (quality.requiresNovelization) addAll(quality.problems)
                }.distinct()
                val secondRejected = adversarialEditor.requestsRewrite(secondReview) || secondDeterministic.isNotEmpty()
                emit(
                    RunStage.EDITOR_REVIEW_2,
                    when {
                        secondReview == null -> RunStatus.WARNING
                        secondRejected -> RunStatus.WARNING
                        else -> RunStatus.SUCCESS
                    },
                    when {
                        checkpoint.secondReviewAttempted && secondReview != null && !secondRejected -> "二审结果从断点复用 · 四席通过"
                        secondReview == null -> "二审响应失败；以本地规则和 Consistency Gate 继续判定"
                        secondRejected -> "二审仍退回，结果将被 BLOCKING"
                        else -> "二审通过"
                    },
                )
                if (secondRejected) {
                    val secondInstructions = adversarialEditor.instructions(secondReview, secondDeterministic)
                    editorBlockingIssue = ConsistencyIssue(
                        severity = IssueSeverity.BLOCKING,
                        code = "EDITOR_REVIEW_FAILED",
                        message = "修订稿仍未通过四视角主编复审，已阻止进入正式版本和长期记忆",
                        evidence = secondReview?.summary.orEmpty().ifBlank { secondInstructions.take(800) },
                        repairInstruction = secondInstructions,
                    )
                }
            }
        } else {
            emit(RunStage.EDITOR_REWRITE, RunStatus.SKIPPED, "一审通过，无需修订")
            emit(RunStage.EDITOR_REVIEW_2, RunStatus.SKIPPED, "一审通过，无需二审")
        }

        // 4) Metadata extraction is paid/remote, so its fallback result is checkpointed too.
        emit(RunStage.METADATA, RunStatus.RUNNING, "正文冻结后再提取摘要 / 状态 / 伏笔触碰；提取结果仍不是 Canon")
        val metadata: GeneratedChapter
        val metadataSucceeded: Boolean
        if (checkpoint.metadataAttempted && checkpoint.metadata != null) {
            metadata = checkpoint.metadata
            metadataSucceeded = checkpoint.metadataSucceeded
        } else {
            val metadataResult = runCatching { aiGateway.generate(promptAssembler.buildMetadata(request, prose)) }
            metadataSucceeded = metadataResult.isSuccess
            metadata = metadataResult.getOrElse {
                GeneratedChapter(
                    title = request.chapter.title,
                    content = "",
                    summary = fallbackSummary(prose),
                )
            }
            persist(
                checkpoint.copy(
                    metadataAttempted = true,
                    metadataSucceeded = metadataSucceeded,
                    metadata = metadata,
                )
            )
        }
        emit(
            RunStage.METADATA,
            if (metadataSucceeded) RunStatus.SUCCESS else RunStatus.WARNING,
            if (checkpoint.metadataAttempted) "元数据从断点恢复；未重复请求模型" else if (metadataSucceeded) "结构化提取完成" else "元数据提取失败，使用正文摘要兜底；不会凭空写入 Canon",
        )

        val chapter = GeneratedChapter(
            title = metadata.title.trim().ifBlank { request.chapter.title },
            content = prose,
            summary = metadata.summary.trim().ifBlank { fallbackSummary(prose) },
            stateChanges = metadata.stateChanges,
            touchedForeshadowingIds = metadata.touchedForeshadowingIds,
        )

        val finalQuality = novelizationEngine.analyze(prose)
        emit(RunStage.CONSISTENCY, RunStatus.RUNNING, "检查章节合同、信息边界、时间线、小说化质量与主编结果")
        val issues = buildList {
            addAll(consistencyGate.inspect(request, chapter))
            editorBlockingIssue?.let(::add)
            if (novelizationSucceeded) {
                add(
                    ConsistencyIssue(
                        severity = IssueSeverity.INFO,
                        code = "PROSE_NOVELIZED",
                        message = "初稿命中报告体/AI腔阈值，已在主编审核前自动执行小说化重构",
                        evidence = "重构前 ${initialQuality.summary()} → 重构后 ${finalQuality.summary()}",
                        repairInstruction = "无需额外操作；如仍不符合你的写法，可在编辑器修改，作者画像会继续学习。",
                    )
                )
            }
            if (finalQuality.blocking) {
                add(
                    ConsistencyIssue(
                        severity = IssueSeverity.BLOCKING,
                        code = "PROSE_QUALITY_FAILED",
                        message = "最终稿仍存在严重报告体/后台痕迹，已禁止写入正式版本",
                        evidence = finalQuality.summary(),
                        repairInstruction = finalQuality.problems.joinToString("；").ifBlank { "重新生成整章并增加场景化表达。" },
                    )
                )
            } else if (finalQuality.score < 82) {
                add(
                    ConsistencyIssue(
                        severity = IssueSeverity.WARNING,
                        code = "PROSE_QUALITY_WATCH",
                        message = "正文可以保存，但小说化质量仍有可改进项（${finalQuality.score}分）",
                        evidence = finalQuality.problems.take(4).joinToString("；").ifBlank { finalQuality.summary() },
                        repairInstruction = "优先通过人物行动、对白、具体物件和因果变化承载信息，避免解释性总结。",
                    )
                )
            }
        }.distinctBy { listOf(it.code, it.message, it.evidence) }
        val blockingCount = issues.count { it.severity == IssueSeverity.BLOCKING }
        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }
        emit(
            RunStage.CONSISTENCY,
            if (blockingCount > 0) RunStatus.WARNING else RunStatus.SUCCESS,
            "BLOCKING=$blockingCount · WARNING=$warningCount · 小说化=${finalQuality.score}分",
        )
        emit(
            RunStage.READY_TO_COMMIT,
            if (blockingCount > 0) RunStatus.WARNING else RunStatus.SUCCESS,
            if (blockingCount > 0) "生成完成，但存在阻止保存的问题" else "正文已通过生成链，可查看结果并确认保存",
        )
        return GenerationResult(chapter = chapter, issues = issues)
    }

'''
text = text[:start] + method + text[end:]
write(path, text)

# 6) Replace coordinator with a durable, resumable implementation while retaining one execution kernel.
write('app/src/main/java/com/xiguli/langhuan/engine/ChapterRunCoordinator.kt', r'''package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StorySnapshot
import java.util.UUID
import kotlinx.coroutines.CancellationException

interface ChapterRunStore {
    suspend fun retrieveRelevantContext(novelId: String, query: String, currentChapter: Int, limit: Int): List<RetrievedContextItem>
    suspend fun commitGenerated(snapshot: StorySnapshot, draft: ChapterDraft, generated: GeneratedChapter, runId: String): PersistedStory
    suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory
    suspend fun chapterDrafts(novelId: String): List<ChapterDraft>
    suspend fun loadStory(novelId: String): PersistedStory?
}

class AppChapterRunStore(
    private val repository: PersistentStoryRepository,
    private val projects: StoryProjectManager,
) : ChapterRunStore {
    override suspend fun retrieveRelevantContext(novelId: String, query: String, currentChapter: Int, limit: Int) =
        repository.retrieveRelevantContext(novelId, query, currentChapter, limit)
    override suspend fun commitGenerated(snapshot: StorySnapshot, draft: ChapterDraft, generated: GeneratedChapter, runId: String) =
        repository.commitGenerated(snapshot, draft, generated, runId)
    override suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft) = projects.saveStructure(snapshot, draft)
    override suspend fun chapterDrafts(novelId: String) = projects.chapterDrafts(novelId)
    override suspend fun loadStory(novelId: String) = projects.loadStory(novelId)
}

data class ChapterRunReviewOutcome(
    val persisted: PersistedStory,
    val review: AgentReview,
    val stagedCount: Int,
    val autoConfirmedCount: Int,
)

data class ChapterRunCommitOutcome(
    val persisted: PersistedStory,
    val review: AgentReview? = null,
    val stagedCount: Int = 0,
    val autoConfirmedCount: Int = 0,
    val fullBookAuditScore: Int? = null,
    val executionScore: Int? = null,
    val replanned: Boolean = false,
    val warnings: List<String> = emptyList(),
) {
    fun summary(): String = buildString {
        append("正文与版本已保存")
        fullBookAuditScore?.let { append(" · 全书主编 $it 分") }
        executionScore?.let { append(" · 执行审计 $it 分") }
        if (review != null) append(" · $stagedCount 条事实进入 Candidate")
        if (autoConfirmedCount > 0) append("（$autoConfirmedCount 条低风险自动确认）")
        if (replanned) append(" · 未来滚动计划已同步")
        if (warnings.isNotEmpty()) append(" · ${warnings.size} 个后处理阶段未完成，可从断点继续")
    }
}

class ChapterRunCoordinator(
    private val store: ChapterRunStore,
    private val checkpointStore: ChapterRunCheckpointStore = NoopChapterRunCheckpointStore,
    private val pipelineFactory: (AiGateway) -> GenerationPipeline = { gateway -> GenerationPipeline(gateway) },
) {
    fun recover(snapshot: StorySnapshot, draft: ChapterDraft): ChapterRunRecovery? {
        val checkpoint = checkpointStore.load(snapshot.novel.id, draft.chapterNumber) ?: return null
        if (!matches(checkpoint, snapshot, draft)) {
            checkpointStore.clear(snapshot.novel.id, draft.chapterNumber)
            return null
        }
        if (checkpoint.phase == DurableRunPhase.COMPLETE) {
            checkpointStore.clear(snapshot.novel.id, draft.chapterNumber)
            return null
        }
        val safe = if (checkpoint.phase == DurableRunPhase.GENERATING) {
            checkpoint.copy(
                phase = DurableRunPhase.INTERRUPTED,
                note = "上次执行在模型阶段被系统中断；已完成的模型阶段和部分正文已保留。",
            ).also(checkpointStore::save)
        } else checkpoint
        val policy = when (safe.phase) {
            DurableRunPhase.READY_TO_COMMIT -> RunResumePolicy.RESTORE_RESULT
            DurableRunPhase.COMMITTING -> RunResumePolicy.RESUME_POST_COMMIT
            DurableRunPhase.GENERATING, DurableRunPhase.INTERRUPTED -> RunResumePolicy.CONTINUE_GENERATION
            DurableRunPhase.COMPLETE -> RunResumePolicy.NONE
        }
        val message = when (policy) {
            RunResumePolicy.RESTORE_RESULT -> "检测到已完成但尚未保存的生成结果，已恢复；不会重新请求模型。"
            RunResumePolicy.RESUME_POST_COMMIT -> "检测到未完成的章节后处理 Run；再次保存会从断点继续，不重复已完成模型调用。"
            RunResumePolicy.CONTINUE_GENERATION -> "检测到被中断的生成 Run；再次生成会从最近持久化阶段继续，已完成阶段不会重跑。"
            RunResumePolicy.NONE -> ""
        }
        return ChapterRunRecovery(
            runId = safe.runId,
            policy = policy,
            result = safe.generationResult,
            preview = safe.generationResult?.chapter?.content ?: safe.partialPreview,
            events = safe.events.mapNotNull(DurableRunEvent::toUi),
            message = message,
        )
    }

    fun abandon(snapshot: StorySnapshot, draft: ChapterDraft) {
        checkpointStore.clear(snapshot.novel.id, draft.chapterNumber)
    }

    suspend fun generate(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        gateway: AiGateway,
        targetWords: Int,
        extraInstruction: String = "",
        onDelta: (String) -> Unit = {},
        onRunEvent: (RunEvent) -> Unit = {},
        forceNew: Boolean = false,
    ): GenerationResult {
        val fingerprint = chapterRunFingerprint(snapshot, draft)
        val existing = checkpointStore.load(snapshot.novel.id, draft.chapterNumber)
            ?.takeIf { !forceNew && it.inputFingerprint == fingerprint && it.phase != DurableRunPhase.COMPLETE }
        if (existing?.phase == DurableRunPhase.READY_TO_COMMIT && existing.generationResult != null) {
            existing.generationResult.chapter.content.takeIf(String::isNotBlank)?.let(onDelta)
            existing.events.mapNotNull(DurableRunEvent::toUi).forEach(onRunEvent)
            return existing.generationResult
        }
        if (forceNew) checkpointStore.clear(snapshot.novel.id, draft.chapterNumber)

        var durable = existing?.takeIf { it.phase in setOf(DurableRunPhase.GENERATING, DurableRunPhase.INTERRUPTED) }
            ?.copy(phase = DurableRunPhase.GENERATING, note = "从持久化断点继续")
            ?: ChapterRunCheckpoint(
                runId = UUID.randomUUID().toString(),
                novelId = snapshot.novel.id,
                chapterNumber = draft.chapterNumber,
                inputFingerprint = fingerprint,
                phase = DurableRunPhase.GENERATING,
            )
        checkpointStore.save(durable)

        fun emit(event: RunEvent) {
            durable = durable.withEvent(event)
            checkpointStore.save(durable)
            onRunEvent(event)
        }

        emit(RunEvent(RunStage.CONTEXT, RunStatus.RUNNING, "统一 Coordinator 正在构建 S/A/B/C/D 上下文并检索相关历史"))
        val retrievedContext = runCatching {
            store.retrieveRelevantContext(snapshot.novel.id, buildChapterRunRagQuery(snapshot, draft), draft.chapterNumber, 10)
        }.getOrElse { error ->
            emit(RunEvent(RunStage.CONTEXT, RunStatus.WARNING, "D 层历史召回失败：${error.message.orEmpty()}；继续使用结构化 Canon"))
            emptyList()
        }
        if (retrievedContext.isNotEmpty()) {
            emit(RunEvent(RunStage.CONTEXT, RunStatus.SUCCESS, "D 层召回 ${retrievedContext.size} 条可解释历史；不会污染 recentSummaries"))
        } else {
            emit(RunEvent(RunStage.CONTEXT, RunStatus.SUCCESS, "本章无需额外历史召回，继续使用结构化 Canon"))
        }

        var lastPersistedLength = durable.partialPreview.length
        try {
            val result = pipelineFactory(gateway).generate(
                request = com.xiguli.langhuan.domain.GenerationRequest(
                    snapshot = snapshot,
                    chapter = draft,
                    targetWords = targetWords,
                    extraInstruction = extraInstruction.trim(),
                ),
                retrievedContext = retrievedContext,
                onDelta = { preview ->
                    if (preview.length - lastPersistedLength >= 384 || (preview.isBlank() && durable.partialPreview.isNotBlank())) {
                        durable = durable.copy(partialPreview = preview)
                        checkpointStore.save(durable)
                        lastPersistedLength = preview.length
                    }
                    onDelta(preview)
                },
                onRunEvent = ::emit,
                resumeCheckpoint = durable.generation,
                onCheckpoint = { generation ->
                    durable = durable.copy(
                        generation = generation,
                        partialPreview = generation.editorRewriteProse.ifBlank {
                            generation.postNovelizationProse.ifBlank { generation.draftProse }
                        },
                    )
                    checkpointStore.save(durable)
                },
            )
            durable = durable.copy(
                phase = DurableRunPhase.READY_TO_COMMIT,
                currentStage = RunStage.READY_TO_COMMIT.name,
                generationResult = result,
                partialPreview = result.chapter.content,
                note = "生成结果已完整持久化，等待用户确认保存",
            )
            checkpointStore.save(durable)
            return result
        } catch (cancelled: CancellationException) {
            durable = durable.copy(phase = DurableRunPhase.INTERRUPTED, note = "用户停止生成；断点已保留")
            checkpointStore.save(durable)
            throw cancelled
        } catch (error: Throwable) {
            durable = durable.copy(phase = DurableRunPhase.INTERRUPTED, note = error.message ?: "生成被中断")
            checkpointStore.save(durable)
            throw error
        }
    }

    suspend fun commit(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        result: GenerationResult,
        gateway: AiGateway?,
        onRunEvent: (RunEvent) -> Unit = {},
    ): ChapterRunCommitOutcome {
        require(result.canCommit) { "当前生成结果仍有 BLOCKING 问题，不能写入正式版本" }
        val fingerprint = chapterRunFingerprint(snapshot, draft)
        val stored = checkpointStore.load(snapshot.novel.id, draft.chapterNumber)
        var durable = stored?.takeIf { matches(it, snapshot, draft) }
            ?: ChapterRunCheckpoint(
                runId = UUID.randomUUID().toString(),
                novelId = snapshot.novel.id,
                chapterNumber = draft.chapterNumber,
                inputFingerprint = fingerprint,
                phase = DurableRunPhase.READY_TO_COMMIT,
                generationResult = result,
                partialPreview = result.chapter.content,
            )
        durable = durable.copy(phase = DurableRunPhase.COMMITTING, generationResult = durable.generationResult ?: result)
        checkpointStore.save(durable)

        fun emit(event: RunEvent) {
            durable = durable.withEvent(event)
            checkpointStore.save(durable)
            onRunEvent(event)
        }
        fun completed(stage: RunStage) = stage.name in durable.completedStages
        fun mark(stage: RunStage, status: RunStatus, detail: String) {
            emit(RunEvent(stage, status, detail))
        }

        var working: PersistedStory
        if (completed(RunStage.SAVE)) {
            working = store.loadStory(snapshot.novel.id) ?: PersistedStory(snapshot, draft)
            mark(RunStage.SAVE, RunStatus.SUCCESS, "从断点确认正文已保存；不会再次增加版本号")
        } else {
            mark(RunStage.SAVE, RunStatus.RUNNING, "以 runId 幂等写入正文、版本与摘要")
            working = try {
                store.commitGenerated(snapshot, draft, result.chapter, durable.runId)
            } catch (error: Throwable) {
                mark(RunStage.SAVE, RunStatus.FAILED, error.message ?: "保存章节失败")
                throw error
            }
            durable = durable.copy(savedDraftVersion = working.draft.version)
            checkpointStore.save(durable)
            mark(RunStage.SAVE, RunStatus.SUCCESS, "正文 v${working.draft.version} 已保存 · runId=${durable.runId.take(8)}")
        }

        val warnings = mutableListOf<String>()
        var fullBookAuditScore: Int? = working.snapshot.longForm.editorReport.score.takeIf { completed(RunStage.FULL_BOOK_AUDIT) }
        var executionScore: Int? = durable.executionRecord?.completionScore
        var reviewOutcome: ChapterRunReviewOutcome? = null
        var replanned = completed(RunStage.AUTONOMOUS_REPLAN) && durable.autonomousPlan != null

        if (!completed(RunStage.FULL_BOOK_AUDIT)) {
            if (FullBookEditorEngine.shouldAudit(working.snapshot, working.draft.chapterNumber)) {
                mark(RunStage.FULL_BOOK_AUDIT, RunStatus.RUNNING, "执行零额外模型成本的全书本地扫描")
                runCatching {
                    val editor = FullBookEditorEngine()
                    val report = editor.localAudit(working.snapshot, store.chapterDrafts(working.snapshot.novel.id))
                    store.saveStructure(editor.apply(working.snapshot, report), working.draft)
                }.onSuccess { audited ->
                    working = audited
                    fullBookAuditScore = audited.snapshot.longForm.editorReport.score
                    mark(RunStage.FULL_BOOK_AUDIT, RunStatus.SUCCESS, "全书主编 ${audited.snapshot.longForm.editorReport.score} 分")
                }.onFailure { error ->
                    warnings += "全书巡检未完成：${error.message.orEmpty()}"
                    mark(RunStage.FULL_BOOK_AUDIT, RunStatus.WARNING, warnings.last())
                }
            } else mark(RunStage.FULL_BOOK_AUDIT, RunStatus.SKIPPED, "未到周期巡检点")
        }

        if (gateway == null) {
            if (!completed(RunStage.EXECUTION_AUDIT)) mark(RunStage.EXECUTION_AUDIT, RunStatus.SKIPPED, "未配置 AI 服务")
            if (!completed(RunStage.CANDIDATE)) mark(RunStage.CANDIDATE, RunStatus.SKIPPED, "未配置 AI 服务，可稍后手动复盘")
            if (!completed(RunStage.AUTONOMOUS_REPLAN)) mark(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "未配置 AI 服务")
            mark(RunStage.COMPLETE, RunStatus.SUCCESS, "正文已保存；需要 AI 的后处理阶段安全跳过")
            durable = durable.copy(phase = DurableRunPhase.COMPLETE)
            checkpointStore.save(durable)
            checkpointStore.clear(snapshot.novel.id, draft.chapterNumber)
            return ChapterRunCommitOutcome(working, fullBookAuditScore = fullBookAuditScore, warnings = warnings)
        }

        val executionEngine = AutonomousExecutionEngine(gateway)
        var execution = durable.executionRecord
        if (!completed(RunStage.EXECUTION_AUDIT)) {
            mark(RunStage.EXECUTION_AUDIT, RunStatus.RUNNING, "比较滚动计划与实际正文")
            if (execution == null) {
                execution = runCatching { executionEngine.assess(working.snapshot, working.draft, result.chapter) }.getOrNull()
                durable = durable.copy(executionRecord = execution)
                checkpointStore.save(durable) // paid model output first, side effect second
            }
            if (execution != null) {
                executionScore = execution!!.completionScore
                runCatching {
                    store.saveStructure(executionEngine.settle(working.snapshot, working.draft, result.chapter, execution!!), working.draft)
                }.onSuccess { settled ->
                    working = settled
                    mark(RunStage.EXECUTION_AUDIT, RunStatus.SUCCESS, "执行完成度 ${execution!!.completionScore} 分 · 结果已从断点安全落库")
                }.onFailure { error ->
                    warnings += "计划执行审计未能落库：${error.message.orEmpty()}"
                    mark(RunStage.EXECUTION_AUDIT, RunStatus.WARNING, warnings.last())
                }
            } else {
                warnings += "AI 执行审计未返回可用结果"
                mark(RunStage.EXECUTION_AUDIT, RunStatus.WARNING, warnings.last())
            }
        } else {
            mark(RunStage.EXECUTION_AUDIT, RunStatus.SUCCESS, "执行审计已在上次 Run 完成；未重复调用模型")
        }

        if (!completed(RunStage.CANDIDATE)) {
            mark(RunStage.CANDIDATE, RunStatus.RUNNING, "Agent 抽取结构化事实；模型结果先落断点再进入 Candidate")
            var review = durable.agentReview
            if (review == null) {
                review = NovelAgentEngine(gateway).reviewChapter(working.snapshot, working.draft)
                durable = durable.copy(agentReview = review)
                checkpointStore.save(durable)
            }
            runCatching {
                val staged = CandidateCanonEngine.stage(working.snapshot, working.draft, review!!)
                val persisted = store.saveStructure(staged.snapshot, working.draft)
                ChapterRunReviewOutcome(persisted, review!!, staged.stagedCount, staged.autoConfirmedCount)
            }.onSuccess { reviewed ->
                reviewOutcome = reviewed
                working = reviewed.persisted
                mark(RunStage.CANDIDATE, RunStatus.SUCCESS, "${reviewed.stagedCount} 条 Candidate · 模型复盘已持久化，不会重跑")
            }.onFailure { error ->
                warnings += "Candidate 落库失败：${error.message.orEmpty()}"
                mark(RunStage.CANDIDATE, RunStatus.WARNING, warnings.last())
            }
        } else {
            mark(RunStage.CANDIDATE, RunStatus.SUCCESS, "Candidate 阶段已完成；未重复 Agent 调用")
        }

        val selective = execution?.let(AutonomousExecutionEngine::shouldSelectiveReplan) == true
        val fullRefresh = AutonomousStoryPlanner.shouldRefresh(working.snapshot, working.draft.chapterNumber)
        if (!completed(RunStage.AUTONOMOUS_REPLAN)) {
            if (selective || fullRefresh) {
                mark(RunStage.AUTONOMOUS_REPLAN, RunStatus.RUNNING, if (selective && !fullRefresh) "只重算受影响章节" else "补足未来滚动计划")
                var plan = durable.autonomousPlan
                if (plan == null) {
                    val planner = AutonomousStoryPlanner(gateway)
                    val candidate = planner.plan(working.snapshot, working.draft, 6)
                    plan = if (selective && !fullRefresh) {
                        executionEngine.mergeSelectivePlan(working.snapshot, candidate, execution?.affectedFutureChapters.orEmpty())
                    } else candidate
                    durable = durable.copy(autonomousPlan = plan)
                    checkpointStore.save(durable) // paid planning output first
                }
                runCatching {
                    store.saveStructure(AutonomousStoryPlanner(gateway).apply(working.snapshot, plan!!), working.draft)
                }.onSuccess { planned ->
                    working = planned
                    replanned = true
                    mark(RunStage.AUTONOMOUS_REPLAN, RunStatus.SUCCESS, "未来滚动计划已同步；规划结果可从断点复用")
                }.onFailure { error ->
                    warnings += "自治重规划未能落库：${error.message.orEmpty()}"
                    mark(RunStage.AUTONOMOUS_REPLAN, RunStatus.WARNING, warnings.last())
                }
            } else mark(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "计划与实际仍对齐，无需重规划")
        } else {
            mark(RunStage.AUTONOMOUS_REPLAN, RunStatus.SUCCESS, "自治重规划阶段已完成；未重复模型调用")
        }

        mark(RunStage.COMPLETE, RunStatus.SUCCESS, "统一可恢复章节 Run 已结束")
        durable = durable.copy(phase = DurableRunPhase.COMPLETE)
        checkpointStore.save(durable)
        checkpointStore.clear(snapshot.novel.id, draft.chapterNumber)
        return ChapterRunCommitOutcome(
            persisted = working,
            review = reviewOutcome?.review ?: durable.agentReview,
            stagedCount = reviewOutcome?.stagedCount ?: 0,
            autoConfirmedCount = reviewOutcome?.autoConfirmedCount ?: 0,
            fullBookAuditScore = fullBookAuditScore,
            executionScore = executionScore,
            replanned = replanned,
            warnings = warnings,
        )
    }

    suspend fun reviewSavedChapter(snapshot: StorySnapshot, draft: ChapterDraft, gateway: AiGateway, onRunEvent: (RunEvent) -> Unit = {}): ChapterRunReviewOutcome {
        onRunEvent(RunEvent(RunStage.CANDIDATE, RunStatus.RUNNING, "手动 Agent 复盘：事实先进入 Candidate"))
        val review = NovelAgentEngine(gateway).reviewChapter(snapshot, draft)
        val staged = CandidateCanonEngine.stage(snapshot, draft, review)
        val persisted = store.saveStructure(staged.snapshot, draft)
        onRunEvent(RunEvent(RunStage.CANDIDATE, RunStatus.SUCCESS, "新增 ${staged.stagedCount} 条 Candidate"))
        return ChapterRunReviewOutcome(persisted, review, staged.stagedCount, staged.autoConfirmedCount)
    }

    suspend fun confirmCandidate(snapshot: StorySnapshot, draft: ChapterDraft, candidateId: String) =
        store.saveStructure(CandidateCanonEngine.confirm(snapshot, candidateId), draft)

    suspend fun rejectCandidate(snapshot: StorySnapshot, draft: ChapterDraft, candidateId: String) =
        store.saveStructure(CandidateCanonEngine.reject(snapshot, candidateId), draft)

    private fun matches(checkpoint: ChapterRunCheckpoint, snapshot: StorySnapshot, draft: ChapterDraft): Boolean =
        checkpoint.novelId == snapshot.novel.id && checkpoint.chapterNumber == draft.chapterNumber &&
            (checkpoint.inputFingerprint == chapterRunFingerprint(snapshot, draft) || draft.lastCommittedRunId == checkpoint.runId)

    private fun ChapterRunCheckpoint.withEvent(event: RunEvent): ChapterRunCheckpoint {
        val terminal = event.status != RunStatus.RUNNING
        return copy(
            currentStage = event.stage.name,
            completedStages = if (terminal) (completedStages + event.stage.name).distinct() else completedStages,
            events = (events + DurableRunEvent.from(event)).takeLast(96),
            updatedAt = System.currentTimeMillis(),
        )
    }
}

internal fun chapterRunFingerprint(snapshot: StorySnapshot, draft: ChapterDraft): String = buildString {
    append(snapshot.novel.id).append('|').append(draft.chapterNumber).append('|').append(draft.version).append('|')
    append(draft.title).append('|').append(draft.objective).append('|')
    draft.scenePlan.sortedBy { it.order }.forEach { append(it.order).append(':').append(it.viewpoint).append(':').append(it.location).append(':').append(it.purpose).append(':').append(it.conflict).append(':').append(it.outcome).append('|') }
    snapshot.activeOutline.forEach { append(it.id).append(':').append(it.objective).append(':').append(it.turningPoint).append(':').append(it.locked).append('|') }
    snapshot.knowledgeLedger.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.readerState).append(':').append(it.revealPolicy).append(':').append(it.knownBy.sorted()).append('|') }
    snapshot.characters.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.location).append(':').append(it.emotionalState).append(':').append(it.goal).append(':').append(it.lastUpdatedChapter).append('|') }
}.hashCode().toUInt().toString(16)

internal fun buildChapterRunRagQuery(snapshot: StorySnapshot, draft: ChapterDraft): String = buildString {
    append(draft.title).append(' ').append(draft.objective).append(' ')
    draft.scenePlan.sortedBy { it.order }.forEach { scene ->
        append(scene.viewpoint).append(' ').append(scene.location).append(' ').append(scene.purpose).append(' ').append(scene.conflict).append(' ').append(scene.outcome).append(' ')
    }
    snapshot.activeOutline.forEach { outline ->
        append(outline.objective).append(' ').append(outline.turningPoint).append(' ').append(outline.mustInclude.joinToString(" ")).append(' ')
    }
    snapshot.characters.forEach { character ->
        append(character.name).append(' ').append(character.goal).append(' ').append(character.location).append(' ').append(character.emotionalState).append(' ')
    }
}.trim()
''')

# 7) ViewModels use the persistent checkpoint store and surface recoverable runs on load.
for path in [
    'app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt',
    'app/src/main/java/com/xiguli/langhuan/ui/WritingFlowViewModel.kt',
]:
    text = read(path)
    if 'PersistentChapterRunCheckpointStore' not in text:
        text = text.replace('import com.xiguli.langhuan.engine.ChapterRunCoordinator\n', 'import com.xiguli.langhuan.engine.ChapterRunCoordinator\nimport com.xiguli.langhuan.engine.PersistentChapterRunCheckpointStore\n')
    text = text.replace(
        'private val chapterRuns = ChapterRunCoordinator(AppChapterRunStore(repository, projects))',
        'private val chapterRuns = ChapterRunCoordinator(\n        AppChapterRunStore(repository, projects),\n        PersistentChapterRunCheckpointStore(application),\n    )',
    )
    write(path, text)

# Studio: apply recovery after init/select chapter/select story and abandon on dismiss.
path = 'app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt'
text = read(path)
text = replace_once(text,
    '            _state.update { it.copy(snapshot = loaded.snapshot, draft = loaded.draft) }\n            refreshWorkspace()\n',
    '            _state.update { it.copy(snapshot = loaded.snapshot, draft = loaded.draft) }\n            restoreDurableRun(loaded.snapshot, loaded.draft)\n            refreshWorkspace()\n',
    'studio init recovery')
text = text.replace('                        refreshWorkspace()\n                    }\n                }.onFailure { error ->\n                    _state.update { it.copy(error = error.message ?: "切换章节失败") }',
                    '                        restoreDurableRun(persisted.snapshot, persisted.draft)\n                        refreshWorkspace()\n                    }\n                }.onFailure { error ->\n                    _state.update { it.copy(error = error.message ?: "切换章节失败") }', 1)
text = text.replace('    fun dismissResult() = _state.update { it.copy(result = null, streamPreview = "") }',
'''    fun dismissResult() {
        val current = _state.value
        chapterRuns.abandon(current.snapshot, current.draft)
        _state.update { it.copy(result = null, streamPreview = "", runEvents = emptyList()) }
    }''')
insert = '''
    private fun restoreDurableRun(snapshot: StorySnapshot, draft: ChapterDraft) {
        val recovery = chapterRuns.recover(snapshot, draft) ?: return
        _state.update {
            it.copy(
                streamPreview = recovery.preview,
                result = recovery.result,
                runEvents = recovery.events,
                message = recovery.message,
            )
        }
    }

'''
text = text.replace('    private suspend fun configuredGateway(): AiGateway? {', insert + '    private suspend fun configuredGateway(): AiGateway? {', 1)
write(path, text)

# Writing Flow: recovery on load; repair is intentionally a fresh run.
path = 'app/src/main/java/com/xiguli/langhuan/ui/WritingFlowViewModel.kt'
text = read(path)
old = '''                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        providerLabel = label.ifBlank { "未配置 AI 服务" },
                        workingScenes = loaded.draft.scenePlan,
                        runEvents = emptyList(),
                        chapterCommitted = loaded.draft.content.isNotBlank(),
                        isLoading = false,
                        error = null,
                    )
                }
'''
new = old + '''                restoreDurableRun(loaded.snapshot, loaded.draft)
'''
text = replace_once(text, old, new, 'writing flow load recovery')
text = replace_once(text,
    '        generate("上一版没有通过一致性审查。必须保留章纲目标和既定事实，按以下要求重写：\\n$repairs")\n',
    '        val snapshot = current.snapshot ?: return\n        val draft = current.draft ?: return\n        chapterRuns.abandon(snapshot, draft)\n        generate("上一版没有通过一致性审查。必须保留章纲目标和既定事实，按以下要求重写：\\n$repairs")\n',
    'repair fresh run')
insert = '''
    private fun restoreDurableRun(snapshot: StorySnapshot, draft: ChapterDraft) {
        val recovery = chapterRuns.recover(snapshot, draft) ?: return
        _state.update {
            it.copy(
                streamPreview = recovery.preview,
                result = recovery.result,
                runEvents = recovery.events,
                message = recovery.message,
            )
        }
    }

'''
# place before activeGateway helper
if '    private suspend fun activeGateway()' in text:
    text = text.replace('    private suspend fun activeGateway()', insert + '    private suspend fun activeGateway()', 1)
else:
    raise SystemExit('missing activeGateway marker')
write(path, text)

# 8) Tests for paid-stage resume and exactly-once coordinator semantics.
write('app/src/test/java/com/xiguli/langhuan/engine/ResumableChapterRunTest.kt', r'''package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableChapterRunTest {
    @Test
    fun `pipeline reuses completed draft review and metadata checkpoints`() = runBlocking {
        val gateway = CountingGateway()
        val checkpoint = GenerationStageCheckpoint(
            draftProse = PROSE,
            novelizationAttempted = true,
            postNovelizationProse = PROSE,
            firstReviewAttempted = true,
            firstReview = passReview(),
            metadataAttempted = true,
            metadataSucceeded = true,
            metadata = GeneratedChapter(title = "门外的人", content = "", summary = "已恢复摘要"),
        )
        val result = GenerationPipeline(gateway).generate(
            request = request(),
            resumeCheckpoint = checkpoint,
        )
        assertEquals(PROSE, result.chapter.content)
        assertEquals("已恢复摘要", result.chapter.summary)
        assertEquals(0, gateway.streamingCalls)
        assertEquals(0, gateway.structuredCalls)
    }

    @Test
    fun `coordinator restores generated result without another model request`() = runBlocking {
        val snapshot = snapshot()
        val draft = draft()
        val result = GenerationResult(GeneratedChapter("门外的人", PROSE, "摘要"), emptyList())
        val checkpoints = MemoryCheckpointStore()
        checkpoints.save(
            ChapterRunCheckpoint(
                runId = "run-ready",
                novelId = snapshot.novel.id,
                chapterNumber = draft.chapterNumber,
                inputFingerprint = chapterRunFingerprint(snapshot, draft),
                phase = DurableRunPhase.READY_TO_COMMIT,
                generationResult = result,
                partialPreview = PROSE,
            )
        )
        val gateway = CountingGateway()
        val coordinator = ChapterRunCoordinator(FakeStore(PersistedStory(snapshot, draft)), checkpoints)
        val restored = coordinator.generate(snapshot, draft, gateway, 2_000)
        assertEquals(PROSE, restored.chapter.content)
        assertEquals(0, gateway.streamingCalls)
        assertEquals(0, gateway.structuredCalls)
        assertEquals(RunResumePolicy.RESTORE_RESULT, coordinator.recover(snapshot, draft)?.policy)
    }

    @Test
    fun `commit uses run id and resume does not create a second saved version`() = runBlocking {
        val snapshot = snapshot()
        val draft = draft()
        val result = GenerationResult(GeneratedChapter("门外的人", PROSE, "摘要"), emptyList())
        val checkpoints = MemoryCheckpointStore()
        val store = FakeStore(PersistedStory(snapshot, draft))
        val coordinator = ChapterRunCoordinator(store, checkpoints)
        val outcome = coordinator.commit(snapshot, draft, result, gateway = null)
        assertEquals(1, store.commitCalls)
        assertTrue(outcome.persisted.draft.lastCommittedRunId.isNotBlank())
        assertEquals(2, outcome.persisted.draft.version)

        // Simulate a stale caller retrying the same persistence token at repository boundary.
        val sameRun = outcome.persisted.draft.lastCommittedRunId
        store.commitGenerated(outcome.persisted.snapshot, draft, result.chapter, sameRun)
        assertEquals(2, outcome.persisted.draft.version)
        assertEquals(2, store.commitCalls) // call reached fake store, but fake's runId guard kept version unchanged
    }

    private class MemoryCheckpointStore : ChapterRunCheckpointStore {
        var value: ChapterRunCheckpoint? = null
        override fun load(novelId: String, chapterNumber: Int) = value?.takeIf { it.novelId == novelId && it.chapterNumber == chapterNumber }
        override fun save(checkpoint: ChapterRunCheckpoint) { value = checkpoint }
        override fun clear(novelId: String, chapterNumber: Int) { value = null }
    }

    private class FakeStore(initial: PersistedStory) : ChapterRunStore {
        var current = initial
        var commitCalls = 0
        override suspend fun retrieveRelevantContext(novelId: String, query: String, currentChapter: Int, limit: Int) = emptyList<RetrievedContextItem>()
        override suspend fun commitGenerated(snapshot: StorySnapshot, draft: ChapterDraft, generated: GeneratedChapter, runId: String): PersistedStory {
            commitCalls++
            if (current.draft.lastCommittedRunId == runId && runId.isNotBlank()) return current
            current = PersistedStory(
                snapshot.copy(novel = snapshot.novel.copy(currentChapter = draft.chapterNumber)),
                draft.copy(content = generated.content, summary = generated.summary, version = draft.version + 1, lastCommittedRunId = runId),
            )
            return current
        }
        override suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
            current = PersistedStory(snapshot, draft)
            return current
        }
        override suspend fun chapterDrafts(novelId: String) = listOf(current.draft)
        override suspend fun loadStory(novelId: String) = current
    }

    private class CountingGateway : AiGateway {
        var streamingCalls = 0
        var structuredCalls = 0
        override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
            streamingCalls++
            onDelta(PROSE)
            return PROSE
        }
        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            structuredCalls++
            return passReview()
        }
    }

    private fun request() = GenerationRequest(snapshot(), draft(), 2_000)
    private fun snapshot() = StorySnapshot(
        novel = Novel("resume-novel", "恢复测试", "悬疑", "身份错位", "记忆", 300_000, status = NovelStatus.WRITING),
        activeOutline = emptyList(), bible = emptyList(), characters = emptyList(), recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(), recentSummaries = emptyList(),
    )
    private fun draft() = ChapterDraft("resume-draft", "resume-novel", 1, "门外的人", "确认身份矛盾", emptyList())

    companion object {
        const val PROSE = "门铃响了第二遍。周衍隔着猫眼看见熟悉的脸，却没有开门。他把门缝下推进来的旧照片放到灯下，日期与记忆对不上。"
        fun passReview() = GeneratedChapter("PASS", "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过", "四席通过")
    }
}
''')

# 9) Version bump.
path = 'app/build.gradle.kts'
text = read(path)
text = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 64', text, count=1)
text = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.26.6-alpha01"', text, count=1)
write(path, text)

# 10) Static architecture assertions.
assert 'lastCommittedRunId' in read('app/src/main/java/com/xiguli/langhuan/domain/StoryModels.kt')
assert 'resumeCheckpoint: GenerationStageCheckpoint' in read('app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt')
assert 'PersistentChapterRunCheckpointStore(application)' in read('app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt')
assert 'PersistentChapterRunCheckpointStore(application)' in read('app/src/main/java/com/xiguli/langhuan/ui/WritingFlowViewModel.kt')
print('resumable run migration applied')
