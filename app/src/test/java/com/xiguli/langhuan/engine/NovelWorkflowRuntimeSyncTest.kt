package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelWorkflowRuntimeSyncTest {
    @Test
    fun `starting real generation confirms chapter plan and enters draft`() {
        val run = runState(
            kind = ChapterRuntimeTaskKind.GENERATE,
            active = true,
            chapter = 1,
        )

        val synced = NovelWorkflowRuntimeSync.sync(
            NovelWorkflowStateMachine.initial("novel-1"),
            run,
        )

        assertEquals(NovelWorkflowStage.DRAFT, synced.currentStage)
        assertEquals(NovelWorkflowStatus.RUNNING, synced.stageStatus)
        assertEquals("1", synced.decisions["runtime_active_chapter"]?.value)
        assertTrue(synced.artifacts.any { it.id == "chapter-1-plan" && !it.stale })
    }

    @Test
    fun `finished generation creates a draft confirmation gate`() {
        val running = NovelWorkflowRuntimeSync.sync(
            NovelWorkflowStateMachine.initial("novel-1"),
            runState(ChapterRuntimeTaskKind.GENERATE, active = true, chapter = 1),
        )
        val result = GenerationResult(
            chapter = GeneratedChapter(title = "第一章", content = "正文"),
            issues = emptyList(),
        )

        val finished = NovelWorkflowRuntimeSync.sync(
            running,
            runState(
                kind = ChapterRuntimeTaskKind.GENERATE,
                active = false,
                chapter = 1,
                result = result,
            ),
        )

        assertEquals(NovelWorkflowStage.DRAFT, finished.currentStage)
        assertEquals(NovelWorkflowStatus.AWAITING_CONFIRMATION, finished.stageStatus)
        assertTrue(finished.artifacts.any { it.id == "chapter-1-draft-v1" && !it.stale })
    }

    @Test
    fun `blocking draft stays in rework and chat approval cannot advance it`() {
        val running = NovelWorkflowRuntimeSync.sync(
            NovelWorkflowStateMachine.initial("novel-1"),
            runState(ChapterRuntimeTaskKind.GENERATE, active = true, chapter = 1),
        )
        val blocked = GenerationResult(
            chapter = GeneratedChapter(title = "第一章", content = "有硬冲突的正文"),
            issues = listOf(
                ConsistencyIssue(
                    severity = IssueSeverity.BLOCKING,
                    code = "CONTRACT_CONFLICT",
                    message = "违反章节合同",
                    repairInstruction = "修复后重新检查",
                )
            ),
        )

        val finished = NovelWorkflowRuntimeSync.sync(
            running,
            runState(
                kind = ChapterRuntimeTaskKind.GENERATE,
                active = false,
                chapter = 1,
                result = blocked,
            ),
        )
        val afterChatApproval = NovelWorkflowStateMachine.applyGateReply(finished, "继续")

        assertEquals(NovelWorkflowStage.DRAFT, finished.currentStage)
        assertEquals(NovelWorkflowStatus.NEEDS_REWORK, finished.stageStatus)
        assertEquals(finished, afterChatApproval)
        assertTrue(finished.artifacts.any { it.id == "chapter-1-draft-v1" })
    }

    @Test
    fun `real commit action confirms draft and enters review`() {
        val result = GenerationResult(
            chapter = GeneratedChapter(title = "第一章", content = "正文"),
            issues = emptyList(),
        )
        val running = NovelWorkflowRuntimeSync.sync(
            NovelWorkflowStateMachine.initial("novel-1"),
            runState(ChapterRuntimeTaskKind.GENERATE, active = true, chapter = 1),
        )
        val draftGate = NovelWorkflowRuntimeSync.sync(
            running,
            runState(ChapterRuntimeTaskKind.GENERATE, active = false, chapter = 1, result = result),
        )

        val committing = NovelWorkflowRuntimeSync.sync(
            draftGate,
            runState(ChapterRuntimeTaskKind.COMMIT, active = true, chapter = 1, result = result),
        )

        assertEquals(NovelWorkflowStage.REVIEW, committing.currentStage)
        assertEquals(NovelWorkflowStatus.RUNNING, committing.stageStatus)
    }

    @Test
    fun `starting next chapter closes previous canon boundary without deleting artifacts`() {
        val previous = NovelWorkflowState(
            novelId = "novel-1",
            currentStage = NovelWorkflowStage.REVIEW,
            stageStatus = NovelWorkflowStatus.AWAITING_CONFIRMATION,
            pendingRequest = "确认审校",
            nextStage = NovelWorkflowStage.CANON_SYNC,
            decisions = mapOf(
                "runtime_active_chapter" to NovelWorkflowDecision(
                    key = "runtime_active_chapter",
                    value = "1",
                    source = "ChapterRunRuntime",
                )
            ),
            artifacts = listOf(
                NovelWorkflowArtifact(
                    id = "chapter-1-draft-v1",
                    kind = NovelArtifactKind.WORKING_DRAFT,
                    stage = NovelWorkflowStage.DRAFT,
                    chapterNumber = 1,
                ),
                NovelWorkflowArtifact(
                    id = "chapter-1-review-v1",
                    kind = NovelArtifactKind.REVIEW_REPORT,
                    stage = NovelWorkflowStage.REVIEW,
                    chapterNumber = 1,
                ),
            ),
        )

        val nextChapter = NovelWorkflowRuntimeSync.sync(
            previous,
            runState(ChapterRuntimeTaskKind.GENERATE, active = true, chapter = 2),
        )

        assertEquals(NovelWorkflowStage.DRAFT, nextChapter.currentStage)
        assertEquals("2", nextChapter.decisions["runtime_active_chapter"]?.value)
        assertTrue(nextChapter.artifacts.any { it.id == "chapter-1-canon" })
        assertTrue(nextChapter.artifacts.any { it.id == "chapter-1-draft-v1" })
        assertTrue(nextChapter.artifacts.any { it.id == "chapter-2-plan" })
    }

    private fun runState(
        kind: ChapterRuntimeTaskKind,
        active: Boolean,
        chapter: Int,
        result: GenerationResult? = null,
    ): ChapterRunRuntimeState = ChapterRunRuntimeState(
        active = active,
        taskKind = kind,
        novelId = "novel-1",
        chapterNumber = chapter,
        snapshot = snapshot(chapter),
        draft = ChapterDraft(
            id = "draft-$chapter",
            novelId = "novel-1",
            chapterNumber = chapter,
            title = "第${chapter}章",
            objective = "推进故事",
            scenePlan = emptyList(),
            content = if (kind == ChapterRuntimeTaskKind.COMMIT && !active) "已保存正文" else "",
        ),
        result = result,
    )

    private fun snapshot(chapter: Int) = StorySnapshot(
        novel = Novel(
            id = "novel-1",
            title = "测试书",
            genre = "悬疑",
            premise = "测试",
            theme = "代价",
            targetWords = 500_000,
            currentChapter = chapter,
        ),
        activeOutline = emptyList(),
        bible = emptyList(),
        characters = emptyList(),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
    )
}
