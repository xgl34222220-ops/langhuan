package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.engine.ChapterRunCheckpoint
import com.xiguli.langhuan.engine.DurableRunPhase
import com.xiguli.langhuan.engine.PersistentChapterRunCheckpointStore
import com.xiguli.langhuan.engine.RunEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RunCenterItemUi(
    val runId: String,
    val novelId: String,
    val novelTitle: String,
    val chapterNumber: Int,
    val chapterTitle: String,
    val phase: DurableRunPhase,
    val currentStage: String,
    val completedCount: Int,
    val preview: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val events: List<RunEvent>,
)

data class RunCenterOpenRequest(
    val novelId: String,
    val chapterNumber: Int,
    val token: Long = System.nanoTime(),
)

data class RunCenterUiState(
    val items: List<RunCenterItemUi> = emptyList(),
    val isLoading: Boolean = false,
    val openRequest: RunCenterOpenRequest? = null,
    val error: String? = null,
)

class RunCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val checkpoints = PersistentChapterRunCheckpointStore(application)
    private val projects = StoryProjectManager(application)
    private val _state = MutableStateFlow(RunCenterUiState())
    val state: StateFlow<RunCenterUiState> = _state.asStateFlow()
    private var refreshRunning = false

    fun refresh(silent: Boolean = false) {
        if (refreshRunning) return
        refreshRunning = true
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                checkpoints.list().map { checkpoint -> checkpoint.toUi() }
            }.onSuccess { items ->
                _state.update { it.copy(items = items, isLoading = false, error = null) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "无法读取运行断点",
                    )
                }
            }
            refreshRunning = false
        }
    }

    fun open(item: RunCenterItemUi) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                projects.setActiveStoryId(item.novelId)
                projects.selectChapter(item.novelId, item.chapterNumber)
                    ?: error("找不到第${item.chapterNumber}章，无法恢复这个 Run")
            }.onSuccess {
                _state.update {
                    it.copy(
                        isLoading = false,
                        openRequest = RunCenterOpenRequest(item.novelId, item.chapterNumber),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "无法打开运行断点") }
            }
        }
    }

    fun abandon(item: RunCenterItemUi) {
        checkpoints.clear(item.novelId, item.chapterNumber)
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.novelId == item.novelId && it.chapterNumber == item.chapterNumber })
        }
    }

    fun consumeOpenRequest() {
        _state.update { it.copy(openRequest = null) }
    }

    private suspend fun ChapterRunCheckpoint.toUi(): RunCenterItemUi {
        val story = projects.loadStory(novelId)
        val chapter = projects.chapterDrafts(novelId).firstOrNull { it.chapterNumber == chapterNumber }
        return RunCenterItemUi(
            runId = runId,
            novelId = novelId,
            novelTitle = story?.snapshot?.novel?.title.orEmpty().ifBlank { "未命名小说" },
            chapterNumber = chapterNumber,
            chapterTitle = chapter?.title.orEmpty().ifBlank { "第${chapterNumber}章" },
            phase = phase,
            currentStage = currentStage,
            completedCount = completedStages.size,
            preview = generationResult?.chapter?.content.orEmpty().ifBlank { partialPreview }.takeLast(1_200),
            note = note,
            createdAt = createdAt,
            updatedAt = updatedAt,
            events = events.mapNotNull { it.toUi() },
        )
    }
}
