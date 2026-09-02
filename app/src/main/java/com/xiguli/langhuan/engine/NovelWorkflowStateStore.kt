package com.xiguli.langhuan.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json

interface NovelWorkflowStateStore {
    fun load(novelId: String): NovelWorkflowState?
    fun save(state: NovelWorkflowState)
    fun clear(novelId: String)

    fun loadOrCreate(novelId: String): NovelWorkflowState {
        return load(novelId) ?: NovelWorkflowStateMachine.initial(novelId).also(::save)
    }
}

/**
 * Durable process-state storage. This is intentionally separate from StorySnapshot/Canon storage:
 * workflow metadata may be rebuilt or cleared without altering novel facts.
 */
class PersistentNovelWorkflowStateStore(context: Context) : NovelWorkflowStateStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "langhuan_novel_workflow_state_v7",
        Context.MODE_PRIVATE,
    )
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override fun load(novelId: String): NovelWorkflowState? {
        if (novelId.isBlank()) return null
        val raw = prefs.getString(key(novelId), null) ?: return null
        return runCatching {
            json.decodeFromString(NovelWorkflowState.serializer(), raw)
        }.onFailure {
            // Corrupt workflow metadata must never block opening the novel or damage Canon.
            prefs.edit().remove(key(novelId)).commit()
        }.getOrNull()
    }

    override fun save(state: NovelWorkflowState) {
        if (state.novelId.isBlank()) return
        val bounded = state.copy(
            stageHistory = state.stageHistory.takeLast(160),
            artifacts = state.artifacts.takeLast(240),
            updatedAt = System.currentTimeMillis(),
        )
        // Gate state should be durable before the next user turn, so use commit() rather than apply().
        prefs.edit().putString(
            key(state.novelId),
            json.encodeToString(NovelWorkflowState.serializer(), bounded),
        ).commit()
    }

    override fun clear(novelId: String) {
        prefs.edit().remove(key(novelId)).commit()
    }

    /**
     * Live process-state stream for inspectable UI surfaces.
     *
     * It listens only to this novel's workflow metadata key. StorySnapshot/Canon remain owned by
     * their existing stores and are intentionally not mirrored into this flow.
     */
    fun observe(novelId: String): Flow<NovelWorkflowState> = callbackFlow {
        if (novelId.isBlank()) {
            close()
            return@callbackFlow
        }
        val workflowKey = key(novelId)
        trySend(loadOrCreate(novelId))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == workflowKey) {
                load(novelId)?.let(::trySend)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun syncRoute(novelId: String, route: NovelRouteDecision): NovelWorkflowState {
        val updated = NovelWorkflowStateMachine.syncRoute(loadOrCreate(novelId), route)
        save(updated)
        return updated
    }

    fun applyGateReply(novelId: String, message: String): NovelWorkflowState {
        val before = loadOrCreate(novelId)
        val after = NovelWorkflowStateMachine.applyGateReply(before, message)
        if (after != before) save(after)
        return after
    }

    private fun key(novelId: String) = "novel:$novelId"
}
