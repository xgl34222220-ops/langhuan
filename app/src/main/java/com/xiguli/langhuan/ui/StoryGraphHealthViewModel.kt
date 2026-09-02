package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.engine.CanonMigrationQueueStore
import com.xiguli.langhuan.engine.StoryGraphHealthEngine
import com.xiguli.langhuan.engine.StoryGraphHealthReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StoryGraphHealthUiState(
    val novelId: String = "",
    val report: StoryGraphHealthReport? = null,
    val isLoading: Boolean = false,
    val checkedAt: Long = 0L,
    val error: String? = null,
)

/** V10 computes a deterministic full-book Story Graph from persisted project truth. */
class StoryGraphHealthViewModel(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val migrationStore = CanonMigrationQueueStore(application)
    private val _state = MutableStateFlow(StoryGraphHealthUiState())
    val state: StateFlow<StoryGraphHealthUiState> = _state.asStateFlow()

    fun load(novelId: String, force: Boolean = false) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (!force && current.novelId == novelId && current.report != null) return
        if (current.isLoading) return
        _state.update { it.copy(novelId = novelId, isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到当前小说项目")
                val chapters = projects.chapterDrafts(novelId)
                val migration = migrationStore.load(novelId)
                withContext(Dispatchers.Default) {
                    StoryGraphHealthEngine.analyze(loaded.snapshot, chapters, migration)
                }
            }.onSuccess { report ->
                _state.update {
                    it.copy(
                        novelId = novelId,
                        report = report,
                        isLoading = false,
                        checkedAt = System.currentTimeMillis(),
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        novelId = novelId,
                        isLoading = false,
                        error = error.message ?: "Story Graph 健康检查失败",
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
