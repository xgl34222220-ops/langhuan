package com.xiguli.langhuan.engine

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
        val terminal = event.status in setOf(RunStatus.SUCCESS, RunStatus.SKIPPED)
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
