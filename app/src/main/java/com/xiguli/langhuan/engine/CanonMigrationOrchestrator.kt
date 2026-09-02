package com.xiguli.langhuan.engine

/**
 * V9 execution policy for V8 Canon repair tasks.
 *
 * The orchestrator deliberately separates scheduling from mutation. It may decide what can run
 * next, but it never writes Canon or chapter prose itself. Proposal-gated work returns to V7,
 * chapter work returns to Chapter Runtime, and only deterministic structured-memory rebuilds are
 * eligible for local execution without another AI write.
 */
enum class CanonMigrationExecutionMode(val label: String) {
    SAFE_LOCAL("安全同步"),
    PROPOSAL_GATE("差异提案"),
    CHAPTER_RUNTIME("章节 Runtime"),
}

enum class CanonMigrationPhase(val label: String) {
    PROJECT_STRUCTURE("项目结构"),
    CHAPTER_REPAIR("章节修复"),
    MEMORY_REFRESH("记忆刷新"),
}

data class CanonMigrationExecutionItem(
    val task: CanonMigrationTask,
    val mode: CanonMigrationExecutionMode,
    val phase: CanonMigrationPhase,
    val blockedByTaskIds: List<String> = emptyList(),
) {
    val ready: Boolean
        get() = blockedByTaskIds.isEmpty() && task.status in setOf(
            CanonMigrationTaskStatus.PENDING,
            CanonMigrationTaskStatus.FAILED,
        )
}

data class CanonMigrationExecutionPlan(
    val novelId: String,
    val items: List<CanonMigrationExecutionItem>,
) {
    val active: CanonMigrationExecutionItem?
        get() = items.firstOrNull {
            it.task.status == CanonMigrationTaskStatus.RUNNING ||
                it.task.status == CanonMigrationTaskStatus.AWAITING_CONFIRMATION
        }

    val next: CanonMigrationExecutionItem?
        get() = active ?: items.firstOrNull { it.ready }

    val readySafeItems: List<CanonMigrationExecutionItem>
        get() = items.filter { it.ready && it.mode == CanonMigrationExecutionMode.SAFE_LOCAL }

    val unresolvedCount: Int get() = items.size
    val blockedCount: Int get() = items.count { !it.ready && it.task.status == CanonMigrationTaskStatus.PENDING }

    fun summary(): String = when {
        items.isEmpty() -> "修复迁移已完成"
        active?.task?.status == CanonMigrationTaskStatus.AWAITING_CONFIRMATION -> "等待确认当前差异提案"
        active != null -> "正在执行 ${active!!.task.action.label}"
        next != null -> "下一项：${next!!.task.action.label} · ${next!!.mode.label}"
        else -> "还有 $unresolvedCount 项待处理，其中 $blockedCount 项等待前置修复"
    }
}

object CanonMigrationOrchestrator {
    fun build(queue: CanonMigrationQueue): CanonMigrationExecutionPlan {
        val unresolved = queue.tasks.filterNot { it.status.isResolved }
        val ordered = unresolved.sortedWith(
            compareBy<CanonMigrationTask> { phaseOf(it).ordinal }
                .thenByDescending { it.priority.ordinal }
                .thenBy { it.chapterNumber ?: Int.MAX_VALUE }
                .thenBy { it.createdAt }
        )
        val items = ordered.map { task ->
            CanonMigrationExecutionItem(
                task = task,
                mode = modeOf(task),
                phase = phaseOf(task),
                blockedByTaskIds = blockersFor(task, unresolved).map { it.id },
            )
        }
        return CanonMigrationExecutionPlan(queue.novelId, items)
    }

    fun modeOf(task: CanonMigrationTask): CanonMigrationExecutionMode = when (task.action) {
        CanonMigrationAction.REFRESH_MEMORY -> CanonMigrationExecutionMode.SAFE_LOCAL
        CanonMigrationAction.REWRITE_CHAPTER -> CanonMigrationExecutionMode.CHAPTER_RUNTIME
        CanonMigrationAction.REPLAN_OUTLINE,
        CanonMigrationAction.RECONCILE_CHARACTER,
        CanonMigrationAction.REPAIR_TIMELINE,
        CanonMigrationAction.REWORK_FORESHADOW,
        CanonMigrationAction.RECONCILE_KNOWLEDGE,
        CanonMigrationAction.REVIEW_STRUCTURE -> CanonMigrationExecutionMode.PROPOSAL_GATE
    }

    fun phaseOf(task: CanonMigrationTask): CanonMigrationPhase = when (task.action) {
        CanonMigrationAction.REWRITE_CHAPTER -> CanonMigrationPhase.CHAPTER_REPAIR
        CanonMigrationAction.REFRESH_MEMORY -> CanonMigrationPhase.MEMORY_REFRESH
        else -> CanonMigrationPhase.PROJECT_STRUCTURE
    }

    private fun blockersFor(
        task: CanonMigrationTask,
        unresolved: List<CanonMigrationTask>,
    ): List<CanonMigrationTask> {
        val phase = phaseOf(task)
        return unresolved.filter { other ->
            if (other.id == task.id) return@filter false
            when (phase) {
                CanonMigrationPhase.PROJECT_STRUCTURE -> false
                CanonMigrationPhase.CHAPTER_REPAIR -> phaseOf(other) == CanonMigrationPhase.PROJECT_STRUCTURE
                CanonMigrationPhase.MEMORY_REFRESH -> phaseOf(other) != CanonMigrationPhase.MEMORY_REFRESH
            }
        }
    }
}

val CanonMigrationTaskStatus.isResolved: Boolean
    get() = this == CanonMigrationTaskStatus.DONE || this == CanonMigrationTaskStatus.SKIPPED
