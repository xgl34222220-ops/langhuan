package com.xiguli.langhuan.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
enum class CanonMigrationAction(val label: String) {
    REPLAN_OUTLINE("重排蓝图/章纲"),
    RECONCILE_CHARACTER("复核人物状态"),
    REPAIR_TIMELINE("修复时间线"),
    REWORK_FORESHADOW("重排伏笔"),
    RECONCILE_KNOWLEDGE("修复信息边界"),
    REWRITE_CHAPTER("重审并修订章节"),
    REFRESH_MEMORY("刷新长期记忆"),
    REVIEW_STRUCTURE("结构复核"),
}

@Serializable
enum class CanonMigrationTaskStatus { PENDING, DONE, SKIPPED }

@Serializable
data class CanonMigrationTask(
    val id: String = UUID.randomUUID().toString(),
    val sourceProposalId: String,
    val sourceRequest: String,
    val scope: String,
    val label: String,
    val detail: String,
    val chapterNumber: Int? = null,
    val action: CanonMigrationAction,
    val priority: CanonChangeRisk,
    val repairInstruction: String,
    val status: CanonMigrationTaskStatus = CanonMigrationTaskStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0L,
)

@Serializable
data class CanonMigrationQueue(
    val novelId: String,
    val tasks: List<CanonMigrationTask> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val pending: List<CanonMigrationTask> get() = tasks.filter { it.status == CanonMigrationTaskStatus.PENDING }
    val completedCount: Int get() = tasks.count { it.status == CanonMigrationTaskStatus.DONE }
}

/**
 * V8 turns V7's passive impact list into an executable repair backlog.
 * It still does not silently rewrite project truth: each repair instruction is routed back through
 * the existing Canon proposal / chapter runtime so normal preview, validation and consistency gates remain active.
 */
object CanonMigrationPlanner {
    fun build(novelId: String, proposal: CanonChangeProposal): CanonMigrationQueue {
        val baseRisk = proposal.patches.maxOfOrNull { it.risk.ordinal }?.let { CanonChangeRisk.entries[it] }
            ?: CanonChangeRisk.MEDIUM
        val tasks = proposal.impacts.map { impact ->
            val action = actionFor(impact)
            val priority = when {
                action == CanonMigrationAction.REWRITE_CHAPTER -> CanonChangeRisk.HIGH
                action in setOf(
                    CanonMigrationAction.REPAIR_TIMELINE,
                    CanonMigrationAction.RECONCILE_KNOWLEDGE,
                    CanonMigrationAction.REPLAN_OUTLINE,
                ) -> maxRisk(baseRisk, CanonChangeRisk.HIGH)
                action == CanonMigrationAction.REFRESH_MEMORY -> CanonChangeRisk.MEDIUM
                else -> maxRisk(baseRisk, CanonChangeRisk.MEDIUM)
            }
            CanonMigrationTask(
                sourceProposalId = proposal.id,
                sourceRequest = proposal.request,
                scope = impact.scope,
                label = impact.label,
                detail = impact.detail,
                chapterNumber = impact.chapterNumber,
                action = action,
                priority = priority,
                repairInstruction = repairInstruction(proposal, impact, action),
            )
        }.distinctBy { listOf(it.sourceProposalId, it.scope, it.label, it.chapterNumber?.toString().orEmpty()) }

        return CanonMigrationQueue(
            novelId = novelId,
            tasks = tasks.sortedWith(
                compareByDescending<CanonMigrationTask> { it.priority.ordinal }
                    .thenBy { it.chapterNumber ?: Int.MAX_VALUE }
                    .thenBy { it.scope }
            ),
        )
    }

    private fun actionFor(impact: CanonChangeImpact): CanonMigrationAction = when (impact.scope) {
        "蓝图" -> CanonMigrationAction.REPLAN_OUTLINE
        "人物" -> CanonMigrationAction.RECONCILE_CHARACTER
        "时间线" -> CanonMigrationAction.REPAIR_TIMELINE
        "伏笔" -> CanonMigrationAction.REWORK_FORESHADOW
        "信息边界" -> CanonMigrationAction.RECONCILE_KNOWLEDGE
        "章节" -> CanonMigrationAction.REWRITE_CHAPTER
        "长期记忆" -> CanonMigrationAction.REFRESH_MEMORY
        else -> CanonMigrationAction.REVIEW_STRUCTURE
    }

    private fun repairInstruction(
        proposal: CanonChangeProposal,
        impact: CanonChangeImpact,
        action: CanonMigrationAction,
    ): String = buildString {
        append("Canon 已确认变更：${proposal.summary}。原始作者要求：${proposal.request}。")
        append("\n现在只处理受影响项【${impact.scope} · ${impact.label}】：${impact.detail}。")
        append("\n执行动作：${action.label}。")
        when (action) {
            CanonMigrationAction.REWRITE_CHAPTER -> append("\n只修与新 Canon 冲突的场景/正文，不借机全面改写；修订后必须重新走连续性检查。")
            CanonMigrationAction.REPLAN_OUTLINE -> append("\n优先调整因果、目标、冲突和转折；不要为了适配新设定提前泄露后续答案。")
            CanonMigrationAction.RECONCILE_CHARACTER -> append("\n只校准人物位置、状态、目标、关系或知识边界中被新 Canon 影响的部分，避免 OOC。")
            CanonMigrationAction.REPAIR_TIMELINE -> append("\n保持已经确认的事件顺序；只修时间锚、地点或事件描述中与新 Canon 的矛盾。")
            CanonMigrationAction.REWORK_FORESHADOW -> append("\n重新检查种植与回收窗口，不删除仍有效的伏笔，也不要提前解释谜底。")
            CanonMigrationAction.RECONCILE_KNOWLEDGE -> append("\n重新核对谁知道、谁不知道以及真相内容，禁止让人物获得越界信息。")
            CanonMigrationAction.REFRESH_MEMORY -> append("\n刷新压缩摘要中的旧设定；摘要只能从已确认 Canon 与正式章节事实重建。")
            CanonMigrationAction.REVIEW_STRUCTURE -> append("\n先分析结构影响，再提出最小必要修改。")
        }
    }

    private fun maxRisk(a: CanonChangeRisk, b: CanonChangeRisk): CanonChangeRisk =
        if (a.ordinal >= b.ordinal) a else b
}

class CanonMigrationQueueStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun load(novelId: String): CanonMigrationQueue {
        if (novelId.isBlank()) return CanonMigrationQueue("")
        val raw = prefs.getString(key(novelId), null) ?: return CanonMigrationQueue(novelId)
        return runCatching { json.decodeFromString(CanonMigrationQueue.serializer(), raw) }
            .getOrElse { CanonMigrationQueue(novelId) }
    }

    @Synchronized
    fun appendFromProposal(novelId: String, proposal: CanonChangeProposal): CanonMigrationQueue {
        val current = load(novelId)
        val incoming = CanonMigrationPlanner.build(novelId, proposal)
        val retained = current.tasks.filter { old ->
            incoming.tasks.none { fresh ->
                old.status == CanonMigrationTaskStatus.PENDING &&
                    old.scope == fresh.scope &&
                    old.label == fresh.label &&
                    old.chapterNumber == fresh.chapterNumber
            }
        }
        val merged = CanonMigrationQueue(
            novelId = novelId,
            tasks = (retained + incoming.tasks).takeLast(MAX_TASKS),
            updatedAt = System.currentTimeMillis(),
        )
        save(merged)
        return merged
    }

    @Synchronized
    fun setStatus(novelId: String, taskId: String, status: CanonMigrationTaskStatus): CanonMigrationQueue {
        val current = load(novelId)
        val now = System.currentTimeMillis()
        val updated = current.copy(
            tasks = current.tasks.map { task ->
                if (task.id == taskId) task.copy(
                    status = status,
                    resolvedAt = if (status == CanonMigrationTaskStatus.PENDING) 0L else now,
                ) else task
            },
            updatedAt = now,
        )
        save(updated)
        return updated
    }

    @Synchronized
    fun clearResolved(novelId: String): CanonMigrationQueue {
        val current = load(novelId)
        val updated = current.copy(
            tasks = current.tasks.filter { it.status == CanonMigrationTaskStatus.PENDING },
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
        return updated
    }

    private fun save(queue: CanonMigrationQueue) {
        prefs.edit().putString(key(queue.novelId), json.encodeToString(CanonMigrationQueue.serializer(), queue)).commit()
    }

    private fun key(novelId: String) = "queue_$novelId"

    private companion object {
        const val PREFS = "langhuan_canon_migration_v8"
        const val MAX_TASKS = 300
    }
}
