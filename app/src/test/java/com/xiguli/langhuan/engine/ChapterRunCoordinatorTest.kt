package com.xiguli.langhuan.engine

import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterRunCoordinatorTest {
    @Test
    fun `generation uses one shared D-layer retrieval and keeps streaming pipeline`() = runBlocking {
        val store = FakeStore(snapshot(), draft())
        val gateway = StreamingGateway()
        val events = mutableListOf<RunEvent>()
        val previews = mutableListOf<String>()
        val coordinator = ChapterRunCoordinator(store)

        val result = coordinator.generate(
            snapshot = store.snapshot,
            draft = store.draft,
            gateway = gateway,
            targetWords = 100,
            onDelta = { previews += it },
            onRunEvent = { events += it },
        )

        assertEquals(PROSE, result.chapter.content)
        assertEquals(1, store.retrievalCalls)
        assertTrue(store.lastQuery.contains("门外的人"))
        assertTrue(store.lastQuery.contains("玄关"))
        assertTrue(store.lastQuery.contains("周衍"))
        assertTrue(store.lastQuery.contains("身份错位"))
        assertTrue(previews.distinct().size >= 3)
        assertEquals(1, gateway.streamingCalls)
        assertEquals(RunStage.CONTEXT_PACK, events.first().stage)
        assertTrue(events.any { it.stage == RunStage.CHARACTER_STATE && it.status == RunStatus.SUCCESS })
        assertTrue(events.any { it.stage == RunStage.HYBRID_RAG && it.status == RunStatus.SUCCESS })
        assertTrue(events.any { it.stage == RunStage.CONTEXT })
        assertTrue(events.any { it.stage == RunStage.DRAFT && it.status == RunStatus.SUCCESS })
        assertTrue(events.any { it.stage == RunStage.READY_TO_COMMIT && it.status == RunStatus.SUCCESS })
    }

    @Test
    fun `commit kernel owns save audit and no-AI skip stages`() = runBlocking {
        val store = FakeStore(snapshot(), draft())
        val coordinator = ChapterRunCoordinator(store)
        val events = mutableListOf<RunEvent>()
        val result = GenerationResult(
            chapter = GeneratedChapter(
                title = store.draft.title,
                content = PROSE,
                summary = "周衍确认门外来客身份存在矛盾，没有开门。",
            ),
            issues = emptyList(),
        )

        val outcome = coordinator.commit(
            snapshot = store.snapshot,
            draft = store.draft,
            result = result,
            gateway = null,
            onRunEvent = { events += it },
        )

        assertEquals(1, store.commitCalls)
        assertEquals(PROSE, outcome.persisted.draft.content)
        assertTrue(events.any { it.stage == RunStage.SAVE && it.status == RunStatus.SUCCESS })
        assertTrue(events.any { it.stage == RunStage.FULL_BOOK_AUDIT })
        assertTrue(events.any { it.stage == RunStage.EXECUTION_AUDIT && it.status == RunStatus.SKIPPED })
        assertTrue(events.any { it.stage == RunStage.CANDIDATE && it.status == RunStatus.SKIPPED })
        assertTrue(events.any { it.stage == RunStage.AUTONOMOUS_REPLAN && it.status == RunStatus.SKIPPED })
        assertTrue(events.any { it.stage == RunStage.COMPLETE && it.status == RunStatus.SUCCESS })
    }

    @Test
    fun `manual Agent review reuses paid result after Candidate side effect failure`() = runBlocking {
        val reviewedDraft = draft().copy(content = PROSE)
        val store = FakeStore(snapshot(), reviewedDraft)
        val checkpoints = MemoryCheckpointStore()
        val gateway = StreamingGateway()
        val coordinator = ChapterRunCoordinator(store, checkpoints)

        store.failNextSave = true
        val first = runCatching {
            coordinator.reviewSavedChapter(store.snapshot, reviewedDraft, gateway)
        }

        assertTrue(first.isFailure)
        assertEquals(1, gateway.generateCalls)
        val durable = checkpoints.load(reviewedDraft.novelId, reviewedDraft.chapterNumber)
        assertNotNull(durable)
        assertEquals(DurableRunPhase.REVIEWING, durable?.phase)
        assertNotNull(durable?.agentReview)

        val second = coordinator.reviewSavedChapter(store.snapshot, reviewedDraft, gateway)

        assertEquals(1, gateway.generateCalls)
        assertEquals(reviewedDraft.chapterNumber, second.persisted.draft.chapterNumber)
        assertNull(checkpoints.load(reviewedDraft.novelId, reviewedDraft.chapterNumber))
    }

    private class MemoryCheckpointStore : ChapterRunCheckpointStore {
        private val values = mutableMapOf<String, ChapterRunCheckpoint>()

        override fun load(novelId: String, chapterNumber: Int): ChapterRunCheckpoint? =
            values["$novelId:$chapterNumber"]

        override fun save(checkpoint: ChapterRunCheckpoint) {
            values["${checkpoint.novelId}:${checkpoint.chapterNumber}"] = checkpoint
        }

        override fun clear(novelId: String, chapterNumber: Int) {
            values.remove("$novelId:$chapterNumber")
        }

        override fun list(): List<ChapterRunCheckpoint> = values.values.toList()
    }

    private class FakeStore(
        var snapshot: StorySnapshot,
        var draft: ChapterDraft,
    ) : ChapterRunStore {
        var retrievalCalls = 0
        var commitCalls = 0
        var saveCalls = 0
        var lastQuery = ""
        var failNextSave = false

        override suspend fun retrieveRelevantContext(
            novelId: String,
            query: String,
            currentChapter: Int,
            limit: Int,
        ): List<RetrievedContextItem> {
            retrievalCalls++
            lastQuery = query
            return emptyList()
        }

        override suspend fun commitGenerated(
            snapshot: StorySnapshot,
            draft: ChapterDraft,
            generated: GeneratedChapter,
            runId: String,
        ): PersistedStory {
            commitCalls++
            val nextDraft = draft.copy(
                title = generated.title.ifBlank { draft.title },
                content = generated.content,
                summary = generated.summary,
                version = draft.version + 1,
                lastCommittedRunId = runId,
            )
            val nextSnapshot = snapshot.copy(
                novel = snapshot.novel.copy(currentChapter = draft.chapterNumber),
                recentSummaries = snapshot.recentSummaries + "第${draft.chapterNumber}章：${generated.summary}",
            )
            this.snapshot = nextSnapshot
            this.draft = nextDraft
            return PersistedStory(nextSnapshot, nextDraft)
        }

        override suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
            saveCalls++
            if (failNextSave) {
                failNextSave = false
                error("模拟 Candidate 落库失败")
            }
            this.snapshot = snapshot
            this.draft = draft
            return PersistedStory(snapshot, draft)
        }

        override suspend fun chapterDrafts(novelId: String): List<ChapterDraft> = listOf(draft)
        override suspend fun loadStory(novelId: String): PersistedStory = PersistedStory(snapshot, draft)
    }

    private class StreamingGateway : AiGateway {
        var streamingCalls = 0
        var generateCalls = 0

        override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
            streamingCalls++
            listOf(PROSE.take(28), PROSE.take(62), PROSE).forEach(onDelta)
            return PROSE
        }

        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            generateCalls++
            return if (prompt.system.contains("对抗式章节主编委员会")) {
                GeneratedChapter(
                    title = "PASS",
                    content = "【结构】通过\n【人物】通过\n【文字】通过\n【连续性】通过",
                    summary = "四席通过",
                )
            } else {
                GeneratedChapter(
                    title = "门外的人",
                    content = "",
                    summary = "周衍确认门外来客身份存在矛盾，没有开门。",
                )
            }
        }
    }

    private fun snapshot(): StorySnapshot {
        val novel = Novel(
            id = "n-coordinator",
            title = "统一执行链测试",
            genre = "悬疑",
            premise = "熟悉现实出现身份错位。",
            theme = "记忆与选择",
            targetWords = 300_000,
            status = NovelStatus.WRITING,
        )
        val chapter = OutlineNode(
            id = "o-1",
            novelId = novel.id,
            level = OutlineLevel.CHAPTER,
            order = 1,
            title = "门外的人",
            objective = "确认身份错位，并基于证据作出选择。",
            conflict = "门外来客声称自己一直认识周衍。",
            turningPoint = "周衍发现照片日期与记忆冲突。",
            mustInclude = listOf("身份错位"),
        )
        return StorySnapshot(
            novel = novel,
            activeOutline = listOf(chapter),
            bible = emptyList(),
            characters = listOf(
                CharacterState(
                    id = "p-1",
                    novelId = novel.id,
                    name = "周衍",
                    personality = listOf("克制"),
                    location = "家中玄关",
                    physicalState = "正常",
                    emotionalState = "警惕",
                    goal = "验证门外来客的真实身份",
                    lastUpdatedChapter = 1,
                )
            ),
            recentTimeline = emptyList(),
            relevantForeshadowing = emptyList(),
            recentSummaries = emptyList(),
            outline = listOf(chapter),
        )
    }

    private fun draft() = ChapterDraft(
        id = "d-1",
        novelId = "n-coordinator",
        chapterNumber = 1,
        title = "门外的人",
        objective = "确认身份错位，并基于证据作出选择。",
        scenePlan = listOf(
            ScenePlan(
                order = 1,
                viewpoint = "周衍",
                location = "家中玄关",
                purpose = "验证门外来客",
                conflict = "照片与记忆互相矛盾",
                outcome = "周衍决定不开门",
            )
        ),
    )

    private companion object {
        const val PROSE = "门铃响了第二遍，周衍仍站在玄关里。他从猫眼看见那张熟悉的脸，却没有立刻伸手开门。门外的人把一张旧照片从门缝下推了进来，照片背面的日期让他停了几秒。他把照片放到灯下，拨通另一个人的电话，始终没有开门。"
    }
}
