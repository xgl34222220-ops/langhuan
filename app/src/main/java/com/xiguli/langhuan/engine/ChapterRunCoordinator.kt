package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StorySnapshot

/**
 * Storage boundary for the unified chapter run coordinator.
 *
 * Keeping this interface free of Android types lets the orchestration itself stay unit-testable while
 * production still uses the existing Room-backed repository/project manager.
 */
interface ChapterRunStore {
    suspend fun retrieveRelevantContext(
        novelId: String,
        query: String,
        currentChapter: Int,
        limit: Int,
    ): List<RetrievedContextItem>

    suspend fun commitGenerated(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        generated: GeneratedChapter,
    ): PersistedStory

    suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory

    suspend fun chapterDrafts(novelId: String): List<ChapterDraft>
}

class AppChapterRunStore(
    private val repository: PersistentStoryRepository,
    private val projects: StoryProjectManager,
) : ChapterRunStore {
    override suspend fun retrieveRelevantContext(
        novelId: String,
        query: String,
        currentChapter: Int,
        limit: Int,
    ): List<RetrievedContextItem> = repository.retrieveRelevantContext(novelId, query, currentChapter, limit)

    override suspend fun commitGenerated(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        generated: GeneratedChapter,
    ): PersistedStory = repository.commitGenerated(snapshot, draft, generated)

    override suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory =
        projects.saveStructure(snapshot, draft)

    override suspend fun chapterDrafts(novelId: String): List<ChapterDraft> = projects.chapterDrafts(novelId)
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
        if (warnings.isNotEmpty()) append(" · ${warnings.size} 个后处理阶段未完成，可稍后补算")
    }
}

/**
 * The single execution kernel for every chapter-writing entry point.
 *
 * ViewModels are deliberately not allowed to rebuild this chain themselves. Both Studio and Writing Flow
 * call the same coordinator for D-layer retrieval, prose generation, commit, post-commit audits,
 * Candidate staging and autonomous replanning.
 */
class ChapterRunCoordinator(
    private val store: ChapterRunStore,
    private val pipelineFactory: (AiGateway) -> GenerationPipeline = { gateway -> GenerationPipeline(gateway) },
) {
    suspend fun generate(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        gateway: AiGateway,
        targetWords: Int,
        extraInstruction: String = "",
        onDelta: (String) -> Unit = {},
        onRunEvent: (RunEvent) -> Unit = {},
    ): GenerationResult {
        fun emit(stage: RunStage, status: RunStatus, detail: String = "") {
            onRunEvent(RunEvent(stage = stage, status = status, detail = detail))
        }

        emit(RunStage.CONTEXT, RunStatus.RUNNING, "统一 Coordinator 正在构建 S/A/B/C/D 上下文并检索相关历史")
        val query = buildChapterRunRagQuery(snapshot, draft)
        val retrievedContext = runCatching {
            store.retrieveRelevantContext(
                novelId = snapshot.novel.id,
                query = query,
                currentChapter = draft.chapterNumber,
                limit = 10,
            )
        }.getOrElse { error ->
            emit(RunStage.CONTEXT, RunStatus.WARNING, "D 层历史召回失败：${error.message.orEmpty()}；继续使用结构化 Canon")
            emptyList()
        }
        if (retrievedContext.isNotEmpty()) {
            emit(RunStage.CONTEXT, RunStatus.SUCCESS, "D 层召回 ${retrievedContext.size} 条可解释历史；不会污染 recentSummaries")
        } else {
            emit(RunStage.CONTEXT, RunStatus.SUCCESS, "本章无需额外历史召回，继续使用结构化 Canon")
        }

        return pipelineFactory(gateway).generate(
            request = GenerationRequest(
                snapshot = snapshot,
                chapter = draft,
                targetWords = targetWords,
                extraInstruction = extraInstruction.trim(),
            ),
            retrievedContext = retrievedContext,
            onDelta = onDelta,
            onRunEvent = onRunEvent,
        )
    }

    suspend fun commit(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        result: GenerationResult,
        gateway: AiGateway?,
        onRunEvent: (RunEvent) -> Unit = {},
    ): ChapterRunCommitOutcome {
        require(result.canCommit) { "当前生成结果仍有 BLOCKING 问题，不能写入正式版本" }
        fun emit(stage: RunStage, status: RunStatus, detail: String = "") {
            onRunEvent(RunEvent(stage = stage, status = status, detail = detail))
        }

        emit(RunStage.SAVE, RunStatus.RUNNING, "统一 Coordinator 写入正文、版本与摘要；AI 抽取事实不会直接改 Canon")
        var working = try {
            store.commitGenerated(snapshot, draft, result.chapter)
        } catch (error: Throwable) {
            emit(RunStage.SAVE, RunStatus.FAILED, error.message ?: "保存章节失败")
            emit(RunStage.COMPLETE, RunStatus.FAILED, "正文未写入正式版本")
            throw error
        }
        emit(RunStage.SAVE, RunStatus.SUCCESS, "正文 v${working.draft.version} 已保存")

        val warnings = mutableListOf<String>()
        var fullBookAuditScore: Int? = null
        var executionScore: Int? = null
        var reviewOutcome: ChapterRunReviewOutcome? = null
        var replanned = false

        if (FullBookEditorEngine.shouldAudit(working.snapshot, working.draft.chapterNumber)) {
            emit(RunStage.FULL_BOOK_AUDIT, RunStatus.RUNNING, "到达周期巡检点，执行零额外模型成本的全书本地扫描")
            runCatching {
                val drafts = store.chapterDrafts(working.snapshot.novel.id)
                val editor = FullBookEditorEngine()
                val report = editor.localAudit(working.snapshot, drafts)
                store.saveStructure(editor.apply(working.snapshot, report), working.draft)
            }.onSuccess { audited ->
                working = audited
                fullBookAuditScore = audited.snapshot.longForm.editorReport.score
                emit(RunStage.FULL_BOOK_AUDIT, RunStatus.SUCCESS, "全书主编 ${audited.snapshot.longForm.editorReport.score} 分")
            }.onFailure { error ->
                val text = "全书巡检未完成：${error.message.orEmpty()}"
                warnings += text
                emit(RunStage.FULL_BOOK_AUDIT, RunStatus.WARNING, text)
            }
        } else {
            emit(RunStage.FULL_BOOK_AUDIT, RunStatus.SKIPPED, "未到周期巡检点")
        }

        if (gateway == null) {
            emit(RunStage.EXECUTION_AUDIT, RunStatus.SKIPPED, "未配置 AI 服务，跳过语义执行审计")
            emit(RunStage.CANDIDATE, RunStatus.SKIPPED, "未配置 AI 服务，可稍后手动复盘")
            emit(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "未配置 AI 服务")
            emit(RunStage.COMPLETE, RunStatus.SUCCESS, "正文已保存；需要 AI 的后处理阶段已安全跳过")
            return ChapterRunCommitOutcome(
                persisted = working,
                fullBookAuditScore = fullBookAuditScore,
                warnings = warnings,
            )
        }

        emit(RunStage.EXECUTION_AUDIT, RunStatus.RUNNING, "比较滚动计划与实际正文，只标记真正受影响的未来章节")
        val executionEngine = AutonomousExecutionEngine(gateway)
        val execution = runCatching {
            executionEngine.assess(working.snapshot, working.draft, result.chapter)
        }.getOrNull()
        if (execution != null) {
            executionScore = execution.completionScore
            runCatching {
                store.saveStructure(
                    executionEngine.settle(working.snapshot, working.draft, result.chapter, execution),
                    working.draft,
                )
            }.onSuccess { settled ->
                working = settled
                emit(
                    RunStage.EXECUTION_AUDIT,
                    RunStatus.SUCCESS,
                    "执行完成度 ${execution.completionScore} 分 · 影响后续 ${execution.affectedFutureChapters.size} 章",
                )
            }.onFailure { error ->
                val text = "计划执行审计完成但未能落库：${error.message.orEmpty()}"
                warnings += text
                emit(RunStage.EXECUTION_AUDIT, RunStatus.WARNING, text)
            }
        } else {
            val text = "AI 执行审计未返回可用结果"
            warnings += text
            emit(RunStage.EXECUTION_AUDIT, RunStatus.WARNING, text)
        }

        reviewOutcome = runCatching {
            reviewAndStage(
                snapshot = working.snapshot,
                draft = working.draft,
                gateway = gateway,
                onRunEvent = onRunEvent,
            )
        }.onSuccess { reviewed ->
            working = reviewed.persisted
        }.onFailure { error ->
            warnings += "Candidate 提取失败：${error.message.orEmpty()}"
        }.getOrNull()

        val selective = execution?.let(AutonomousExecutionEngine::shouldSelectiveReplan) == true
        val fullRefresh = AutonomousStoryPlanner.shouldRefresh(working.snapshot, working.draft.chapterNumber)
        if (selective || fullRefresh) {
            emit(
                RunStage.AUTONOMOUS_REPLAN,
                RunStatus.RUNNING,
                if (selective && !fullRefresh) "只重算被计划偏差影响的后续章节" else "滚动窗口变薄或风险升高，补足未来计划",
            )
            runCatching {
                val planner = AutonomousStoryPlanner(gateway)
                val candidate = planner.plan(working.snapshot, working.draft, 6)
                val nextPlan = if (selective && !fullRefresh) {
                    executionEngine.mergeSelectivePlan(
                        working.snapshot,
                        candidate,
                        execution?.affectedFutureChapters.orEmpty(),
                    )
                } else {
                    candidate
                }
                store.saveStructure(planner.apply(working.snapshot, nextPlan), working.draft)
            }.onSuccess { planned ->
                working = planned
                replanned = true
                emit(RunStage.AUTONOMOUS_REPLAN, RunStatus.SUCCESS, "未来滚动计划已同步")
            }.onFailure { error ->
                val text = "自治重规划失败：${error.message.orEmpty()}"
                warnings += text
                emit(RunStage.AUTONOMOUS_REPLAN, RunStatus.WARNING, text)
            }
        } else {
            emit(RunStage.AUTONOMOUS_REPLAN, RunStatus.SKIPPED, "计划与实际仍对齐，无需洗掉未来规划")
        }

        emit(RunStage.COMPLETE, RunStatus.SUCCESS, "统一章节执行链已结束")
        return ChapterRunCommitOutcome(
            persisted = working,
            review = reviewOutcome?.review,
            stagedCount = reviewOutcome?.stagedCount ?: 0,
            autoConfirmedCount = reviewOutcome?.autoConfirmedCount ?: 0,
            fullBookAuditScore = fullBookAuditScore,
            executionScore = executionScore,
            replanned = replanned,
            warnings = warnings,
        )
    }

    suspend fun reviewSavedChapter(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        gateway: AiGateway,
        onRunEvent: (RunEvent) -> Unit = {},
    ): ChapterRunReviewOutcome = reviewAndStage(snapshot, draft, gateway, onRunEvent)

    suspend fun confirmCandidate(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        candidateId: String,
    ): PersistedStory = store.saveStructure(CandidateCanonEngine.confirm(snapshot, candidateId), draft)

    suspend fun rejectCandidate(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        candidateId: String,
    ): PersistedStory = store.saveStructure(CandidateCanonEngine.reject(snapshot, candidateId), draft)

    private suspend fun reviewAndStage(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        gateway: AiGateway,
        onRunEvent: (RunEvent) -> Unit,
    ): ChapterRunReviewOutcome {
        fun emit(stage: RunStage, status: RunStatus, detail: String = "") {
            onRunEvent(RunEvent(stage = stage, status = status, detail = detail))
        }
        emit(RunStage.CANDIDATE, RunStatus.RUNNING, "Agent 从已保存正文抽取结构化事实；全部先进入 Candidate")
        return try {
            val review = NovelAgentEngine(gateway).reviewChapter(snapshot, draft)
            val staged = CandidateCanonEngine.stage(snapshot, draft, review)
            val persisted = store.saveStructure(staged.snapshot, draft)
            emit(
                RunStage.CANDIDATE,
                RunStatus.SUCCESS,
                "新增 ${staged.stagedCount} 条 Candidate · 自动确认 ${staged.autoConfirmedCount} 条低风险事实",
            )
            ChapterRunReviewOutcome(
                persisted = persisted,
                review = review,
                stagedCount = staged.stagedCount,
                autoConfirmedCount = staged.autoConfirmedCount,
            )
        } catch (error: Throwable) {
            emit(RunStage.CANDIDATE, RunStatus.WARNING, "Candidate 提取失败：${error.message.orEmpty()}；正文仍保持已保存状态")
            throw error
        }
    }
}

internal fun buildChapterRunRagQuery(snapshot: StorySnapshot, draft: ChapterDraft): String = buildString {
    append(draft.title).append(' ')
    append(draft.objective).append(' ')
    draft.scenePlan.sortedBy { it.order }.forEach { scene ->
        append(scene.viewpoint).append(' ')
        append(scene.location).append(' ')
        append(scene.purpose).append(' ')
        append(scene.conflict).append(' ')
        append(scene.outcome).append(' ')
    }
    snapshot.activeOutline.forEach { outline ->
        append(outline.objective).append(' ')
        append(outline.turningPoint).append(' ')
        append(outline.mustInclude.joinToString(" ")).append(' ')
    }
    snapshot.characters.forEach { character ->
        append(character.name).append(' ')
        append(character.goal).append(' ')
        append(character.location).append(' ')
        append(character.emotionalState).append(' ')
    }
}.trim()
