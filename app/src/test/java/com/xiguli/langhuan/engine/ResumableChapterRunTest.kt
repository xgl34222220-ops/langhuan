package com.xiguli.langhuan.engine

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
