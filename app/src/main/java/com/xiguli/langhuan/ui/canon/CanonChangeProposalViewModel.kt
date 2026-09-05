package com.xiguli.langhuan.ui.canon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.CanonChangeProposal
import com.xiguli.langhuan.engine.CanonChangeProposalEngine
import com.xiguli.langhuan.engine.CanonMigrationExecutionMode
import com.xiguli.langhuan.engine.CanonMigrationExecutionPlan
import com.xiguli.langhuan.engine.CanonMigrationOrchestrator
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
    val migrationPlan: CanonMigrationExecutionPlan? = null,
    val migrationVisible: Boolean = false,
    val migrationSourceTaskId: String? = null,
    val isMigrationExecuting: Boolean = false,
    val migrationMessage: String? = null,
    val migrationError: String? = null,
    val isBusy: Boolean = false,
    val isApplying: Boolean = false,
    val appliedAt: Long = 0L,
    val message: String? = null,
    val error: String? = null,
) {
    val active: Boolean get() = isBusy || isApplying || isMigrationExecuting
    val pendingMigrationCount: Int get() = migrationQueue?.pending?.size ?: 0
}

/**
 * V7 keeps proposal generation and confirmed writes separate. V8 adds a persistent repair queue.
 * V9 adds an explicit migration orchestrator: safe local memory rebuilds may run directly, while
 * structure changes still stop at the V7 diff gate and chapter repairs still return to Chapter Runtime.
 */
class CanonChangeProposalViewModel(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val taskRouter = TaskModelRouter(application)
    private val migrationStore = CanonMigrationQueueStore(application)
    private val _state = MutableStateFlow(CanonChangeProposalUiState())
    val state: StateFlow<CanonChangeProposalUiState> = _state.asStateFlow()

    fun loadMigrationQueue(novelId: String) {
        if (novelId.isBlank()) return
        val queue = migrationStore.recoverInterrupted(novelId)
        _state.update { current ->
            current.copy(
                novelId = novelId,
                migrationQueue = queue,
                migrationPlan = CanonMigrationOrchestrator.build(queue),
            )
        }
    }

    fun openMigrationQueue() {
        val novelId = _state.value.novelId
        if (novelId.isBlank()) return
        val queue = migrationStore.load(novelId)
        _state.update {
            it.copy(
                migrationQueue = queue,
                migrationPlan = CanonMigrationOrchestrator.build(queue),
                migrationVisible = true,
                migrationError = null,
                error = null,
            )
        }
    }

    fun closeMigrationQueue() = _state.update { it.copy(migrationVisible = false) }

    fun propose(novelId: String, request: String) = startProposal(novelId, request, null)

    fun proposeMigrationRepair(task: CanonMigrationTask) {
        val novelId = _state.value.novelId
        if (novelId.isBlank() || task.status !in setOf(CanonMigrationTaskStatus.PENDING, CanonMigrationTaskStatus.FAILED)) return
        if (CanonMigrationOrchestrator.modeOf(task) != CanonMigrationExecutionMode.PROPOSAL_GATE) return
        val queue = migrationStore.setStatus(novelId, task.id, CanonMigrationTaskStatus.RUNNING)
        _state.update {
            it.copy(
                migrationQueue = queue,
                migrationPlan = CanonMigrationOrchestrator.build(queue),
                migrationVisible = false,
                migrationMessage = "正在推进：${task.action.label}",
                migrationError = null,
            )
        }
        startProposal(novelId, task.repairInstruction, task.id)
    }

    private fun startProposal(novelId: String, request: String, migrationTaskId: String?) {
        val clean = request.trim()
        if (novelId.isBlank() || clean.isBlank() || _state.value.active && migrationTaskId == null) return
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
                val queue = migrationTaskId?.let {
                    migrationStore.setStatus(novelId, it, CanonMigrationTaskStatus.AWAITING_CONFIRMATION)
                } ?: migrationStore.load(novelId)
                _state.update {
                    it.copy(
                        proposal = proposal,
                        migrationQueue = queue,
                        migrationPlan = CanonMigrationOrchestrator.build(queue),
                        isBusy = false,
                        migrationMessage = migrationTaskId?.let { "最小修复提案已就绪，等待确认" },
                        message = "提案已生成：${proposal.patches.size} 项差异 · ${proposal.impacts.size} 处影响",
                        error = null,
                    )
                }
            }.onFailure { error ->
                val queue = migrationTaskId?.let {
                    migrationStore.setStatus(novelId, it, CanonMigrationTaskStatus.FAILED)
                } ?: migrationStore.load(novelId)
                _state.update {
                    it.copy(
                        migrationQueue = queue,
                        migrationPlan = CanonMigrationOrchestrator.build(queue),
                        isBusy = false,
                        migrationSourceTaskId = null,
                        migrationMessage = null,
                        migrationError = migrationTaskId?.let { "${error.message ?: "最小修复提案生成失败"}，可重试" },
                        message = null,
                        error = if (migrationTaskId == null) error.message ?: "Canon 变更提案生成失败" else null,
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
                        migrationPlan = CanonMigrationOrchestrator.build(queue),
                        migrationVisible = queue.pending.isNotEmpty(),
                        migrationSourceTaskId = null,
                        isApplying = false,
                        appliedAt = System.currentTimeMillis(),
                        migrationMessage = if (before.migrationSourceTaskId != null) "当前迁移项已确认写入，队列已重新编排" else it.migrationMessage,
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

    /** Execute every currently ready SAFE_LOCAL task with one deterministic memory rebuild. */
    fun executeReadySafeMigrations() {
        val before = _state.value
        val novelId = before.novelId
        if (novelId.isBlank() || before.active) return
        val queue = migrationStore.load(novelId)
        val plan = CanonMigrationOrchestrator.build(queue)
        val safe = plan.readySafeItems
        if (safe.isEmpty()) {
            _state.update {
                it.copy(
                    migrationQueue = queue,
                    migrationPlan = plan,
                    migrationMessage = plan.next?.let { next -> "下一项需要${next.mode.label}，不会自动越过确认边界" }
                        ?: "当前没有可自动执行的安全同步",
                    migrationError = null,
                )
            }
            return
        }

        var runningQueue = queue
        safe.forEach { item ->
            runningQueue = migrationStore.setStatus(novelId, item.task.id, CanonMigrationTaskStatus.RUNNING)
        }
        _state.update {
            it.copy(
                migrationQueue = runningQueue,
                migrationPlan = CanonMigrationOrchestrator.build(runningQueue),
                isMigrationExecuting = true,
                migrationMessage = "正在从已确认 Canon / 正式项目事实重建结构化记忆……",
                migrationError = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到当前小说项目")
                // saveStructure with the same confirmed snapshot is deterministic here; its purpose is
                // to rebuild structured memory only, not to invent or mutate story facts.
                projects.saveStructure(loaded.snapshot, loaded.draft)
                var updated = migrationStore.load(novelId)
                safe.forEach { item ->
                    updated = migrationStore.setStatus(novelId, item.task.id, CanonMigrationTaskStatus.DONE)
                }
                updated
            }.onSuccess { updated ->
                _state.update {
                    it.copy(
                        migrationQueue = updated,
                        migrationPlan = CanonMigrationOrchestrator.build(updated),
                        isMigrationExecuting = false,
                        migrationMessage = "安全同步完成：${safe.size} 项结构化记忆已按当前 Canon 重建",
                        migrationError = null,
                    )
                }
            }.onFailure { error ->
                var restored = migrationStore.load(novelId)
                safe.forEach { item ->
                    restored = migrationStore.setStatus(novelId, item.task.id, CanonMigrationTaskStatus.FAILED)
                }
                _state.update {
                    it.copy(
                        migrationQueue = restored,
                        migrationPlan = CanonMigrationOrchestrator.build(restored),
                        isMigrationExecuting = false,
                        migrationMessage = null,
                        migrationError = error.message ?: "安全同步失败，可重试",
                    )
                }
            }
        }
    }

    fun setMigrationStatus(taskId: String, status: CanonMigrationTaskStatus) {
        val novelId = _state.value.novelId
        if (novelId.isBlank()) return
        val queue = migrationStore.setStatus(novelId, taskId, status)
        _state.update {
            it.copy(
                migrationQueue = queue,
                migrationPlan = CanonMigrationOrchestrator.build(queue),
                migrationError = null,
            )
        }
    }

    fun clearResolvedMigration() {
        val novelId = _state.value.novelId
        if (novelId.isBlank()) return
        val queue = migrationStore.clearResolved(novelId)
        _state.update {
            it.copy(
                migrationQueue = queue,
                migrationPlan = CanonMigrationOrchestrator.build(queue),
            )
        }
    }

    fun discardPending() {
        val before = _state.value
        val taskId = before.migrationSourceTaskId
        val queue = if (taskId != null && before.novelId.isNotBlank()) {
            migrationStore.setStatus(before.novelId, taskId, CanonMigrationTaskStatus.PENDING)
        } else before.migrationQueue
        _state.update {
            it.copy(
                proposal = null,
                migrationSourceTaskId = null,
                migrationQueue = queue,
                migrationPlan = queue?.let(CanonMigrationOrchestrator::build),
                migrationMessage = if (taskId != null) "已取消当前提案，迁移项已回到待处理" else it.migrationMessage,
                message = null,
                error = null,
            )
        }
    }

    fun clearNotice() = _state.update {
        it.copy(message = null, error = null, migrationMessage = null, migrationError = null)
    }
}
