package com.xiguli.langhuan.ui.agent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.LanghuanApplication
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
    private val runtime = (application as LanghuanApplication).chapterRunRuntime
    private val _state = MutableStateFlow(RunCenterUiState())
    val state: StateFlow<RunCenterUiState> = _state.asStateFlow()
    private var refreshRunning = false

    fun refresh(silent: Boolean = false) {
        if (refreshRunning) return
        refreshRunning = true
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            try {
                val items = buildList {
                    for (checkpoint in checkpoints.list()) add(checkpoint.toUi())
                }
                _state.update { it.copy(items = items, isLoading = false, error = null) }
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "无法读取运行断点",
                    )
                }
            } finally {
                refreshRunning = false
            }
        }
    }

    fun open(item: RunCenterItemUi) {
        val live = runtime.state.value
        if (live.active && !live.matches(item.novelId, item.chapterNumber)) {
            _state.update {
                it.copy(error = "第${live.chapterNumber}章还有 Application 级后台任务正在执行。请先打开并停止当前任务，再切换到其他 Run。")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                projects.setActiveStoryId(item.novelId)
                projects.selectChapter(item.novelId, item.chapterNumber)
                    ?: error("找不到第${item.chapterNumber}章，无法恢复这个 Run")
                _state.update {
                    it.copy(
                        isLoading = false,
                        openRequest = RunCenterOpenRequest(item.novelId, item.chapterNumber),
                    )
                }
            } catch (error: Throwable) {
                _state.update { it.copy(isLoading = false, error = error.message ?: "无法打开运行断点") }
            }
        }
    }

    fun abandon(item: RunCenterItemUi) {
        val live = runtime.state.value
        if (live.active && live.matches(item.novelId, item.chapterNumber)) {
            _state.update { it.copy(error = "这个 Run 仍在执行，先停止当前生成再放弃断点。") }
            return
        }
        checkpoints.clear(item.novelId, item.chapterNumber)
        runtime.clearTerminalState(item.novelId, item.chapterNumber)
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
