package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.LanghuanApplication
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.engine.ChapterRunCheckpoint
import com.xiguli.langhuan.engine.DurableRunPhase
import com.xiguli.langhuan.engine.PersistentChapterRunCheckpointStore
import com.xiguli.langhuan.engine.RunEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** Display labels resolved from the project database for one checkpoint. */
internal data class RunCenterLabelsV85(
    val novelTitle: String,
    val chapterTitle: String,
)

/**
 * Small per-ViewModel cache for checkpoint labels.
 *
 * Silent polling only re-reads the lightweight checkpoint file; book and chapter titles are
 * re-resolved from Room on an explicit refresh, or the first time a checkpoint appears.
 */
internal class RunCenterLabelCacheV85 {
    private val labels = java.util.concurrent.ConcurrentHashMap<String, RunCenterLabelsV85>()

    fun get(novelId: String, chapterNumber: Int): RunCenterLabelsV85? = labels[key(novelId, chapterNumber)]

    fun put(novelId: String, chapterNumber: Int, value: RunCenterLabelsV85) {
        labels[key(novelId, chapterNumber)] = value
    }

    /** Drop labels of checkpoints that no longer exist so the cache cannot grow unbounded. */
    fun retainOnly(keys: Collection<Pair<String, Int>>) {
        val keep = keys.mapTo(HashSet()) { (novelId, chapterNumber) -> key(novelId, chapterNumber) }
        labels.keys.retainAll(keep)
    }

    val size: Int get() = labels.size

    private fun key(novelId: String, chapterNumber: Int) = "$novelId:$chapterNumber"
}

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

    private val labelCache = RunCenterLabelCacheV85()

    fun refresh(silent: Boolean = false) {
        if (refreshRunning) return
        refreshRunning = true
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Checkpoint JSON decoding and Room/story lookups never run on the main thread.
                val items = withContext(Dispatchers.IO) {
                    val list = checkpoints.list()
                    labelCache.retainOnly(list.map { it.novelId to it.chapterNumber })
                    val novelTitles = HashMap<String, String>()
                    list.map { checkpoint ->
                        val labels = labelCache.get(checkpoint.novelId, checkpoint.chapterNumber)
                            ?.takeIf { silent }
                            ?: resolveLabels(checkpoint, novelTitles).also {
                                labelCache.put(checkpoint.novelId, checkpoint.chapterNumber, it)
                            }
                        checkpoint.toUi(labels)
                    }
                }
                _state.update { it.copy(items = items, isLoading = false, error = if (silent) it.error else null) }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
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
        // Remove the card immediately; the durable commit() happens off the main thread.
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.novelId == item.novelId && it.chapterNumber == item.chapterNumber })
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { checkpoints.clear(item.novelId, item.chapterNumber) }
                runtime.clearTerminalState(item.novelId, item.chapterNumber)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(error = error.message ?: "无法删除运行断点") }
                refresh(silent = true)
            }
        }
    }

    fun consumeOpenRequest() {
        _state.update { it.copy(openRequest = null) }
    }

    /**
     * Resolve labels with at most one story load per novel and a single-chapter lookup.
     * The whole-book [StoryProjectManager.chapterDrafts] scan is only a fallback.
     */
    private suspend fun resolveLabels(
        checkpoint: ChapterRunCheckpoint,
        novelTitles: MutableMap<String, String>,
    ): RunCenterLabelsV85 {
        val novelId = checkpoint.novelId
        val chapterNumber = checkpoint.chapterNumber
        val novelTitle = novelTitles.getOrPut(novelId) {
            projects.loadStory(novelId)?.snapshot?.novel?.title.orEmpty().ifBlank { "未命名小说" }
        }
        val chapter = projects.chapterDraft(novelId, chapterNumber)
            ?: projects.chapterDrafts(novelId).firstOrNull { it.chapterNumber == chapterNumber }
        return RunCenterLabelsV85(
            novelTitle = novelTitle,
            chapterTitle = chapter?.title.orEmpty().ifBlank { "第${chapterNumber}章" },
        )
    }

    private fun ChapterRunCheckpoint.toUi(labels: RunCenterLabelsV85): RunCenterItemUi {
        return RunCenterItemUi(
            runId = runId,
            novelId = novelId,
            novelTitle = labels.novelTitle,
            chapterNumber = chapterNumber,
            chapterTitle = labels.chapterTitle,
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
