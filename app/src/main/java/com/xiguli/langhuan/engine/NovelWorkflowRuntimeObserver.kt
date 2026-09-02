package com.xiguli.langhuan.engine

import android.content.Context
import com.xiguli.langhuan.domain.CandidateFactStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Bridges real ChapterRunRuntime evidence into the durable workflow state.
 *
 * It never invents story facts. Runtime events only advance process gates that the user has
 * already acted on (generate / commit / review), while Canon remains owned by the existing
 * CandidateCanonEngine and StoryProjectManager pipeline.
 */
class NovelWorkflowRuntimeObserver private constructor(
    context: Context,
    private val runtime: ChapterRunRuntime,
) {
    private val store = PersistentNovelWorkflowStateStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            runtime.state.collectLatest { run ->
                if (run.novelId.isBlank() || run.chapterNumber <= 0) return@collectLatest
                val before = store.loadOrCreate(run.novelId)
                val after = NovelWorkflowRuntimeSync.sync(before, run)
                if (after != before) store.save(after)
            }
        }
    }

    companion object {
        fun attach(context: Context, runtime: ChapterRunRuntime) {
            NovelWorkflowRuntimeObserver(context.applicationContext, runtime).start()
        }
    }
}

/** Pure synchronizer kept separate so runtime/workflow behavior can be unit-tested without Android. */
object NovelWorkflowRuntimeSync {
    private const val ACTIVE_CHAPTER_KEY = "runtime_active_chapter"

    fun sync(state: NovelWorkflowState, run: ChapterRunRuntimeState): NovelWorkflowState {
        if (run.novelId.isBlank() || run.novelId != state.novelId || run.chapterNumber <= 0) return state
        var next = state
        val trackedChapter = next.decisions[ACTIVE_CHAPTER_KEY]?.value?.toIntOrNull()

        if (trackedChapter == null) {
            next = rememberChapter(next, run.chapterNumber)
        } else if (run.chapterNumber > trackedChapter) {
            next = closePreviousChapter(next, trackedChapter, run)
            next = enterChapterPlan(next, run.chapterNumber, "检测到真实 Runtime 已进入新章节")
        } else if (run.chapterNumber < trackedChapter) {
            // Editing an earlier chapter is an upstream change. Preserve later artifacts but make
            // the affected chain stale instead of pretending the newer chapter is still trusted.
            next = NovelWorkflowStateMachine.rewindTo(
                next,
                earliestStage = NovelWorkflowStage.CHAPTER_PLAN,
                reason = "返回修改第${run.chapterNumber}章",
                chapterNumbers = setOf(run.chapterNumber),
            )
            next = rememberChapter(next, run.chapterNumber)
        }

        return when (run.taskKind) {
            ChapterRuntimeTaskKind.IDLE -> next
            ChapterRuntimeTaskKind.GENERATE -> syncGenerate(next, run)
            ChapterRuntimeTaskKind.COMMIT -> syncCommit(next, run)
            ChapterRuntimeTaskKind.REVIEW -> syncReview(next, run)
        }
    }

    private fun syncGenerate(state: NovelWorkflowState, run: ChapterRunRuntimeState): NovelWorkflowState {
        var next = state
        val chapter = run.chapterNumber
        if (run.active) {
            if (next.currentStage.ordinal > NovelWorkflowStage.DRAFT.ordinal) {
                next = NovelWorkflowStateMachine.rewindTo(
                    next,
                    earliestStage = NovelWorkflowStage.DRAFT,
                    reason = "第${chapter}章重新生成正文",
                    chapterNumbers = setOf(chapter),
                )
            }
            if (next.currentStage != NovelWorkflowStage.DRAFT) {
                next = enterChapterPlan(next, chapter, "用户开始生成正文，章纲视为本轮已采用")
                next = confirmPlanForGeneration(next, chapter)
            }
            if (next.stageStatus != NovelWorkflowStatus.RUNNING) {
                next = next.copy(
                    stageStatus = NovelWorkflowStatus.RUNNING,
                    pendingRequest = "第${chapter}章正在生成正文；生成完成后只确认这一版正文。",
                    nextStage = NovelWorkflowStage.REVIEW,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            return next
        }

        val result = run.result ?: return next
        if (next.currentStage != NovelWorkflowStage.DRAFT) {
            next = forceStage(next, NovelWorkflowStage.DRAFT, "根据正文生成结果恢复到正文 Gate")
        }
        val artifactId = "chapter-$chapter-draft-v${run.draft?.version ?: 1}"
        if (next.hasCurrentArtifact(artifactId)) return next
        return NovelWorkflowStateMachine.submitArtifact(
            next,
            NovelWorkflowArtifact(
                id = artifactId,
                kind = NovelArtifactKind.WORKING_DRAFT,
                stage = NovelWorkflowStage.DRAFT,
                label = "第${chapter}章正文工作稿",
                revision = run.draft?.version ?: 1,
                chapterNumber = chapter,
            ),
            pendingRequest = if (result.canCommit) {
                "第${chapter}章正文已通过阻断级检查。确认这一版后再进入审校/Canon 同步；要改就直接指出问题。"
            } else {
                "第${chapter}章正文仍有阻断级问题，需要先修复；不要进入 Canon。"
            },
        )
    }

    private fun syncCommit(state: NovelWorkflowState, run: ChapterRunRuntimeState): NovelWorkflowState {
        var next = state
        val chapter = run.chapterNumber
        if (run.active) {
            // Pressing the real commit action is the user's approval of the current draft gate.
            if (next.currentStage == NovelWorkflowStage.DRAFT && next.stageStatus == NovelWorkflowStatus.AWAITING_CONFIRMATION) {
                next = NovelWorkflowStateMachine.confirmCurrent(next, "用户执行正式保存，确认当前正文 Gate")
            }
            if (next.currentStage != NovelWorkflowStage.REVIEW) {
                next = forceStage(next, NovelWorkflowStage.REVIEW, "正文已进入正式保存与后处理")
            }
            return next.copy(
                stageStatus = NovelWorkflowStatus.RUNNING,
                pendingRequest = "第${chapter}章正在保存并执行后处理；完成后检查审校结果和 Candidate。",
                nextStage = NovelWorkflowStage.CANON_SYNC,
                updatedAt = System.currentTimeMillis(),
            )
        }

        val persistedDraft = run.draft ?: return next
        if (persistedDraft.content.isBlank()) return next
        if (next.currentStage != NovelWorkflowStage.REVIEW) {
            next = forceStage(next, NovelWorkflowStage.REVIEW, "正文已正式保存，进入审校 Gate")
        }
        return run.review?.let { submitReview(next, run) } ?: next.copy(
            stageStatus = NovelWorkflowStatus.RUNNING,
            pendingRequest = "第${chapter}章已正式保存；继续完成审校/候选事实检查。",
            nextStage = NovelWorkflowStage.CANON_SYNC,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun syncReview(state: NovelWorkflowState, run: ChapterRunRuntimeState): NovelWorkflowState {
        var next = state
        val chapter = run.chapterNumber
        if (next.currentStage != NovelWorkflowStage.REVIEW) {
            next = forceStage(next, NovelWorkflowStage.REVIEW, "启动真实 Agent 章节复盘")
        }
        if (run.active) {
            return next.copy(
                stageStatus = NovelWorkflowStatus.RUNNING,
                pendingRequest = "第${chapter}章正在执行 Agent 复盘；完成后只处理真实发现的问题与 Candidate。",
                nextStage = NovelWorkflowStage.CANON_SYNC,
                updatedAt = System.currentTimeMillis(),
            )
        }
        return if (run.review != null) submitReview(next, run) else next
    }

    private fun submitReview(state: NovelWorkflowState, run: ChapterRunRuntimeState): NovelWorkflowState {
        val chapter = run.chapterNumber
        val artifactId = "chapter-$chapter-review-v${run.draft?.version ?: 1}"
        if (state.hasCurrentArtifact(artifactId)) return state
        val pendingCandidates = run.snapshot?.candidateFacts
            ?.count { it.sourceChapter == chapter && it.status == CandidateFactStatus.PENDING }
            ?: 0
        return NovelWorkflowStateMachine.submitArtifact(
            state,
            NovelWorkflowArtifact(
                id = artifactId,
                kind = NovelArtifactKind.REVIEW_REPORT,
                stage = NovelWorkflowStage.REVIEW,
                label = "第${chapter}章审校/Agent 复盘",
                revision = run.draft?.version ?: 1,
                chapterNumber = chapter,
            ),
            pendingRequest = if (pendingCandidates > 0) {
                "第${chapter}章审校完成，还有 $pendingCandidates 条 Candidate 待确认/拒绝；全部处理后再进入下一章。"
            } else {
                "第${chapter}章审校完成。确认审校结果后同步最终 Canon 状态。"
            },
        )
    }

    private fun closePreviousChapter(
        state: NovelWorkflowState,
        previousChapter: Int,
        nextRun: ChapterRunRuntimeState,
    ): NovelWorkflowState {
        var next = state
        if (next.currentStage == NovelWorkflowStage.REVIEW && next.stageStatus == NovelWorkflowStatus.AWAITING_CONFIRMATION) {
            next = NovelWorkflowStateMachine.confirmCurrent(next, "进入下一章前，上一章审校 Gate 已由真实推进动作确认")
        }
        if (next.currentStage != NovelWorkflowStage.CANON_SYNC) {
            next = forceStage(next, NovelWorkflowStage.CANON_SYNC, "进入下一章前核对上一章 Canon")
        }

        // WritingFlow refuses advance while the previous chapter still has pending Candidate facts.
        // The next runtime snapshot is therefore evidence that the authoring pipeline crossed that
        // boundary; record only process completion, never duplicate any Canon content here.
        val remainingPreviousCandidates = nextRun.snapshot?.candidateFacts
            ?.count { it.sourceChapter == previousChapter && it.status == CandidateFactStatus.PENDING }
            ?: 0
        if (remainingPreviousCandidates == 0) {
            val artifactId = "chapter-$previousChapter-canon"
            if (!next.hasCurrentArtifact(artifactId)) {
                next = NovelWorkflowStateMachine.submitArtifact(
                    next,
                    NovelWorkflowArtifact(
                        id = artifactId,
                        kind = NovelArtifactKind.CANON_DELTA,
                        stage = NovelWorkflowStage.CANON_SYNC,
                        label = "第${previousChapter}章 Canon 同步",
                        chapterNumber = previousChapter,
                    ),
                    pendingRequest = "上一章 Candidate 已处理完毕，Canon 边界可关闭。",
                )
            }
            if (next.stageStatus == NovelWorkflowStatus.AWAITING_CONFIRMATION) {
                next = NovelWorkflowStateMachine.confirmCurrent(next, "真实 Runtime 已进入下一章，上一章 Canon Gate 关闭")
            }
        }
        return next
    }

    private fun confirmPlanForGeneration(state: NovelWorkflowState, chapter: Int): NovelWorkflowState {
        var next = state
        val artifactId = "chapter-$chapter-plan"
        if (!next.hasCurrentArtifact(artifactId)) {
            next = NovelWorkflowStateMachine.submitArtifact(
                next,
                NovelWorkflowArtifact(
                    id = artifactId,
                    kind = NovelArtifactKind.CHAPTER_PLAN,
                    stage = NovelWorkflowStage.CHAPTER_PLAN,
                    label = "第${chapter}章章纲/场景计划",
                    chapterNumber = chapter,
                ),
                pendingRequest = "章纲已用于本次正文生成。",
            )
        }
        return if (next.stageStatus == NovelWorkflowStatus.AWAITING_CONFIRMATION) {
            NovelWorkflowStateMachine.confirmCurrent(next, "用户启动正文生成，确认本轮章纲/场景计划")
        } else next
    }

    private fun enterChapterPlan(state: NovelWorkflowState, chapter: Int, note: String): NovelWorkflowState {
        var next = forceStage(state, NovelWorkflowStage.CHAPTER_PLAN, note)
        next = rememberChapter(next, chapter)
        return next.copy(
            stageStatus = NovelWorkflowStatus.RUNNING,
            pendingRequest = "第${chapter}章：先确认章纲/场景计划，再生成正文。",
            nextStage = NovelWorkflowStage.DRAFT,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun rememberChapter(state: NovelWorkflowState, chapter: Int): NovelWorkflowState =
        NovelWorkflowStateMachine.recordDecision(
            state,
            key = ACTIVE_CHAPTER_KEY,
            value = chapter.toString(),
            source = "ChapterRunRuntime",
            confirmed = true,
        )

    private fun forceStage(state: NovelWorkflowState, stage: NovelWorkflowStage, note: String): NovelWorkflowState {
        if (state.currentStage == stage) return state
        return state.copy(
            currentStage = stage,
            stageStatus = NovelWorkflowStatus.RUNNING,
            pendingRequest = when (stage) {
                NovelWorkflowStage.CHAPTER_PLAN -> "确认当前章纲/场景计划。"
                NovelWorkflowStage.DRAFT -> "生成并检查当前正文工作稿。"
                NovelWorkflowStage.REVIEW -> "检查审校结果与 Candidate。"
                NovelWorkflowStage.CANON_SYNC -> "处理 Candidate 后关闭当前章节 Canon Gate。"
                else -> state.pendingRequest
            },
            nextStage = NovelWorkflowStage.entries.getOrNull(stage.ordinal + 1),
            stageHistory = state.stageHistory + NovelWorkflowHistoryEntry(
                stage = state.currentStage,
                status = state.stageStatus,
                note = note,
                artifactIds = state.artifacts.filter { it.stage == state.currentStage && !it.stale }.map { it.id },
            ),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun NovelWorkflowState.hasCurrentArtifact(id: String): Boolean =
        artifacts.any { it.id == id && !it.stale }
}
