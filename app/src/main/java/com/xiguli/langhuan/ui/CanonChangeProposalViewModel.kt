package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.CanonChangeProposal
import com.xiguli.langhuan.engine.CanonChangeProposalEngine
import com.xiguli.langhuan.engine.CanonMigrationQueue
import com.xiguli.langhuan.engine.CanonMigrationQueueStore
import com.xiguli.langhuan.engine.CanonMigrationTask
import com.xiguli.langhuan.engine.CanonMigrationTaskStatus
import com.xiguli.langhuan.engine.CanonPatchEngine
import com.xiguli.langhuan.engine.TaskModelRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CanonChangeProposalUiState(
    val novelId: String = "",
    val proposal: CanonChangeProposal? = null,
    val migrationQueue: CanonMigrationQueue? = null,
    val migrationVisible: Boolean = false,
    val migrationSourceTaskId: String? = null,
    val isBusy: Boolean = false,
    val isApplying: Boolean = false,
    val appliedAt: Long = 0L,
    val message: String? = null,
    val error: String? = null,
) {
    val active: Boolean get() = isBusy || isApplying
    val pendingMigrationCount: Int get() = migrationQueue?.pending?.size ?: 0
}

/**
 * V7 keeps proposal generation and confirmed writes separate. V8 adds a persistent repair queue
 * after confirmed Canon changes; repair tasks still route back through the same proposal/runtime gates.
 */
class CanonChangeProposalViewModel(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val taskRouter = TaskModelRouter(application)
    private val migrationStore = CanonMigrationQueueStore(application)
    private val _state = MutableStateFlow(CanonChangeProposalUiState())
    val state: StateFlow<CanonChangeProposalUiState> = _state.asStateFlow()

    fun loadMigrationQueue(novelId: String) {
        if (novelId.isBlank()) return
        val queue = migrationStore.load(novelId)
        _state.update { current ->
            current.copy(
                novelId = novelId,
                migrationQueue = queue,
            )
        }
    }

    fun openMigrationQueue() {
        val novelId = _state.value.novelId
        if (novelId.isBlank()) return
        _state.update {
            it.copy(
                migrationQueue = migrationStore.load(novelId),
                migrationVisible = true,
                error = null,
            )
        }
    }

    fun closeMigrationQueue() = _state.update { it.copy(migrationVisible = false) }

    fun propose(novelId: String, request: String) = startProposal(novelId, request, null)

    fun proposeMigrationRepair(task: CanonMigrationTask) {
        val novelId = _state.value.novelId
        if (novelId.isBlank() || task.status != CanonMigrationTaskStatus.PENDING) return
        _state.update { it.copy(migrationVisible = false) }
        startProposal(novelId, task.repairInstruction, task.id)
    }

    private fun startProposal(novelId: String, request: String, migrationTaskId: String?) {
        val clean = request.trim()
        if (novelId.isBlank() || clean.isBlank() || _state.value.active) return
        _state.update {
            it.copy(
                novelId = novelId,
                proposal = null,
                migrationSourceTaskId = migrationTaskId,
                isBusy = true,
                message = if (migrationTaskId == null) {
                    "正在分析受影响的 Canon、人物、时间线与章节……"
                } else {
                    "正在为修复队列生成最小变更提案……"
                },
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到当前小说项目")
                val drafts = projects.chapterDrafts(novelId)
                val session = taskRouter.snapshot()
                val gateway = session.selection(AiTaskType.AUTONOMOUS_PLANNER).gateway
                CanonChangeProposalEngine.propose(gateway, loaded.snapshot, drafts, clean)
            }.onSuccess { proposal ->
                _state.update {
                    it.copy(
                        proposal = proposal,
                        isBusy = false,
                        message = "提案已生成：${proposal.patches.size} 项差异 · ${proposal.impacts.size} 处影响",
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        migrationSourceTaskId = null,
                        message = null,
                        error = error.message ?: "Canon 变更提案生成失败",
                    )
                }
            }
        }
    }

    fun applyPending() {
        val before = _state.value
        val proposal = before.proposal ?: return
        val novelId = before.novelId
        if (novelId.isBlank() || before.active) return
        _state.update { it.copy(isApplying = true, message = "正在重新校验旧值并一次性写入……", error = null) }
        viewModelScope.launch {
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到当前小说项目")
                val conflicts = CanonPatchEngine.conflicts(loaded.snapshot, proposal.patches)
                require(conflicts.isEmpty()) { conflicts.joinToString("；") }
                val updated = CanonPatchEngine.apply(loaded.snapshot, proposal.patches, proposal.request)
                projects.saveStructure(updated, loaded.draft)

                before.migrationSourceTaskId?.let { taskId ->
                    migrationStore.setStatus(novelId, taskId, CanonMigrationTaskStatus.DONE)
                }
                migrationStore.appendFromProposal(novelId, proposal)
            }.onSuccess { queue ->
                _state.update {
                    it.copy(
                        proposal = null,
                        migrationQueue = queue,
                        migrationVisible = queue.pending.isNotEmpty(),
                        migrationSourceTaskId = null,
                        isApplying = false,
                        appliedAt = System.currentTimeMillis(),
                        message = buildString {
                            append("已确认写入 ${proposal.patches.size} 项变更；结构化记忆已同步重建")
                            if (queue.pending.isNotEmpty()) append(" · 已生成 ${queue.pending.size} 项修复队列")
                        },
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isApplying = false,
                        message = null,
                        error = error.message ?: "Canon 变更写入失败",
                    )
                }
            }
        }
    }

    fun setMigrationStatus(taskId: String, status: CanonMigrationTaskStatus) {
        val novelId = _state.value.novelId
        if (novelId.isBlank()) return
        val queue = migrationStore.setStatus(novelId, taskId, status)
        _state.update { it.copy(migrationQueue = queue) }
    }

    fun clearResolvedMigration() {
        val novelId = _state.value.novelId
        if (novelId.isBlank()) return
        val queue = migrationStore.clearResolved(novelId)
        _state.update { it.copy(migrationQueue = queue) }
    }

    fun discardPending() = _state.update {
        it.copy(
            proposal = null,
            migrationSourceTaskId = null,
            message = null,
            error = null,
        )
    }

    fun clearNotice() = _state.update { it.copy(message = null, error = null) }
}
