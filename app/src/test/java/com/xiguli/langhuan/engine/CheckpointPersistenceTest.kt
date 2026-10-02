package com.xiguli.langhuan.engine

import android.content.SharedPreferences
import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.StorySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CheckpointPersistenceTest {
    @Test
    fun `successful save round trips and clear removes checkpoint`() {
        val prefs = MemoryPrefs()
        val store = PersistentChapterRunCheckpointStore(prefs)
        store.save(checkpoint())
        assertEquals("run-1", store.load("novel-1", 1)?.runId)
        assertEquals(1, store.list().size)
        store.clear("novel-1", 1)
        assertNull(store.load("novel-1", 1))
    }

    @Test
    fun `save and clear reject unacknowledged durable writes`() {
        val prefs = MemoryPrefs().apply { reject = { true } }
        val store = PersistentChapterRunCheckpointStore(prefs)
        assertTrue(runCatching { store.save(checkpoint()) }.exceptionOrNull() is ChapterRunCheckpointWriteException)
        assertTrue(runCatching { store.clear("novel-1", 1) }.exceptionOrNull() is ChapterRunCheckpointWriteException)
    }

    @Test
    fun `failed initial checkpoint stops generation and commit before paid or storage work`() = runBlocking {
        val prefs = MemoryPrefs().apply { reject = { true } }
        val store = CountingStore()
        val gateway = CountingGateway()
        val coordinator = ChapterRunCoordinator(store, PersistentChapterRunCheckpointStore(prefs))
        val generation = runCatching { coordinator.generate(snapshot(), draft(), gateway, 100) }
        val commit = runCatching { coordinator.commit(snapshot(), draft(), result(), gateway) }
        assertTrue(generation.exceptionOrNull() is ChapterRunCheckpointWriteException)
        assertTrue(commit.exceptionOrNull() is ChapterRunCheckpointWriteException)
        assertEquals(0, gateway.calls)
        assertEquals(0, store.retrievalCalls)
        assertEquals(0, store.commitCalls)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `failed Agent result checkpoint prevents Candidate writes and later model calls`() = runBlocking {
        val prefs = MemoryPrefs().apply {
            reject = { pending -> pending.values.filterIsInstance<String>().any { it.contains("\"agentReview\":{") } }
        }
        val store = CountingStore()
        val gateway = CountingGateway()
        val coordinator = ChapterRunCoordinator(store, PersistentChapterRunCheckpointStore(prefs))

        val failure = runCatching { coordinator.commit(snapshot(), draft(), result(), gateway) }

        assertTrue(failure.exceptionOrNull() is ChapterRunCheckpointWriteException)
        assertEquals(1, store.commitCalls)
        assertEquals(2, store.saveCalls) // Local full-book audit and execution settlement only.
        assertEquals(1, gateway.calls) // Agent completed, paid replanning never started.
    }

    @Test
    fun `optional rewrite never downgrades a wrapped checkpoint failure to model fallback`() = runBlocking {
        val prefs = MemoryPrefs().apply { reject = { true } }
        val checkpoints = PersistentChapterRunCheckpointStore(prefs)
        val gateway = CountingGateway()
        val cachedProse = "本章总结：目前掌握的信息。已确认事实：门外有一个人。"
        val failure = runCatching {
            GenerationPipeline(gateway).generate(
                GenerationRequest(snapshot(), draft(), 100),
                resumeCheckpoint = GenerationStageCheckpoint(logicPlanAttempted = true, draftProse = cachedProse),
                onDelta = { partial ->
                    if (partial == PROSE) {
                        try { checkpoints.save(checkpoint()) } catch (error: ChapterRunCheckpointWriteException) {
                            // The HTTP streaming adapter wraps callback errors after emitting content.
                            throw IllegalStateException("stream interrupted", error)
                        }
                    }
                },
            )
        }
        assertNotNull(failure.exceptionOrNull())
        assertEquals(1, gateway.calls) // Novelization only; no editorial review or metadata request.
    }

    private class CountingGateway : AiGateway {
        var calls = 0
        override suspend fun generate(prompt: PromptBundle): GeneratedChapter {
            calls++
            return GeneratedChapter("门外的人", PROSE, "摘要")
        }
        override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String {
            calls++
            onDelta(PROSE)
            return PROSE
        }
    }

    private class CountingStore : ChapterRunStore {
        var retrievalCalls = 0
        var commitCalls = 0
        var saveCalls = 0
        private var current: PersistedStory? = null
        override suspend fun retrieveRelevantContext(novelId: String, query: String, currentChapter: Int, limit: Int): List<RetrievedContextItem> {
            retrievalCalls++
            return emptyList()
        }
        override suspend fun commitGenerated(snapshot: StorySnapshot, draft: ChapterDraft, generated: GeneratedChapter, runId: String): PersistedStory {
            commitCalls++
            return PersistedStory(snapshot, draft.copy(content = generated.content, version = draft.version + 1, lastCommittedRunId = runId)).also { current = it }
        }
        override suspend fun saveStructure(snapshot: StorySnapshot, draft: ChapterDraft): PersistedStory {
            saveCalls++
            return PersistedStory(snapshot, draft).also { current = it }
        }
        override suspend fun chapterDrafts(novelId: String): List<ChapterDraft> = listOfNotNull(current?.draft)
        override suspend fun loadStory(novelId: String): PersistedStory? = current
    }

    private fun checkpoint() = ChapterRunCheckpoint("run-1", "novel-1", 1, "fingerprint")
    private fun snapshot() = StorySnapshot(
        novel = Novel("novel-1", "恢复测试", "悬疑", "身份错位", "记忆", 300_000),
        activeOutline = emptyList(), bible = emptyList(), characters = emptyList(), recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(), recentSummaries = emptyList(),
    )
    private fun draft() = ChapterDraft("draft-1", "novel-1", 1, "门外的人", "确认矛盾", emptyList())
    private fun result() = GenerationResult(GeneratedChapter("门外的人", PROSE, "摘要"), emptyList())

    private companion object {
        const val PROSE = "门铃响了第二遍。周衍隔着猫眼看见熟悉的脸，却没有开门。他把门缝下推进来的旧照片放到灯下，日期与记忆对不上。"
    }

    private class MemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        var reject: (Map<String, Any?>) -> Boolean = { false }
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, fallback: String?): String? = values[key] as String? ?: fallback
        @Suppress("UNCHECKED_CAST") override fun getStringSet(key: String?, fallback: MutableSet<String>?): MutableSet<String>? = (values[key] as Set<String>?)?.toMutableSet() ?: fallback
        override fun getInt(key: String?, fallback: Int) = values[key] as Int? ?: fallback
        override fun getLong(key: String?, fallback: Long) = values[key] as Long? ?: fallback
        override fun getFloat(key: String?, fallback: Float) = values[key] as Float? ?: fallback
        override fun getBoolean(key: String?, fallback: Boolean) = values[key] as Boolean? ?: fallback
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            var clearing = false
            override fun putString(key: String?, value: String?) = apply { pending[requireNotNull(key)] = value }
            override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { pending[requireNotNull(key)] = value?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { pending[requireNotNull(key)] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[requireNotNull(key)] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[requireNotNull(key)] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[requireNotNull(key)] = value }
            override fun remove(key: String?) = apply { pending[requireNotNull(key)] = null }
            override fun clear() = apply { clearing = true }
            // Android publishes changes in memory even if the durable commit reports failure.
            override fun commit(): Boolean { apply(); return !reject(pending) }
            override fun apply() { if (clearing) values.clear(); pending.forEach { (k,v) -> if (v == null) values.remove(k) else values[k] = v } }
        }
    }

}
