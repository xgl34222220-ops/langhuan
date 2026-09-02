package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.CanonChangeProposal
import com.xiguli.langhuan.engine.CanonChangeProposalEngine
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
    val isBusy: Boolean = false,
    val isApplying: Boolean = false,
    val appliedAt: Long = 0L,
    val message: String? = null,
    val error: String? = null,
) {
    val active: Boolean get() = isBusy || isApplying
}

/**
 * V7 keeps proposal generation and confirmed writes separate. The model never owns a repository;
 * applying a proposal always reloads the latest StorySnapshot and re-validates every old value.
 */
class CanonChangeProposalViewModel(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val taskRouter = TaskModelRouter(application)
    private val _state = MutableStateFlow(CanonChangeProposalUiState())
    val state: StateFlow<CanonChangeProposalUiState> = _state.asStateFlow()

    fun propose(novelId: String, request: String) {
        val clean = request.trim()
        if (novelId.isBlank() || clean.isBlank() || _state.value.active) return
        _state.update {
            it.copy(
                novelId = novelId,
                proposal = null,
                isBusy = true,
                message = "正在分析受影响的 Canon、人物、时间线与章节……",
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
            }.onSuccess {
                _state.update {
                    it.copy(
                        proposal = null,
                        isApplying = false,
                        appliedAt = System.currentTimeMillis(),
                        message = "已确认写入 ${proposal.patches.size} 项变更；结构化记忆已同步重建",
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

    fun discardPending() = _state.update { it.copy(proposal = null, message = null, error = null) }
    fun clearNotice() = _state.update { it.copy(message = null, error = null) }
}
