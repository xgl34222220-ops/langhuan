package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot

enum class ProjectRuntimePhase(val label: String) {
    GENERATION("生成期"),
    POST_COMMIT("提交后"),
    MANUAL_REVIEW("手动复盘"),
}

enum class ProjectRuntimeCapability(val label: String) {
    CONTEXT_PACK("S/A/B/C 上下文"),
    HYBRID_RAG("D 层历史召回"),
    REFERENCE_DNA("Reference DNA"),
    CHARACTER_STATE("人物状态"),
    CHRONOLOGY("时间线"),
    ERA_TECH("时代技术锁"),
    FORESHADOWING("伏笔状态"),
    CONSISTENCY_GATE("一致性 Gate"),
    FULL_BOOK_AUDIT("全书主编"),
    EXECUTION_AUDIT("计划执行审计"),
    AGENT_CANDIDATE("Agent → Candidate"),
    AUTONOMOUS_REPLAN("自治重规划"),
}

data class ProjectRuntimeSkillStep(
    val capability: ProjectRuntimeCapability,
    val phase: ProjectRuntimePhase,
    val engine: String,
    val reason: String,
    val conditional: Boolean = false,
)

data class ProjectRuntimeSkillPlan(
    val novelId: String,
    val chapterNumber: Int,
    val steps: List<ProjectRuntimeSkillStep>,
) {
    fun stepsFor(phase: ProjectRuntimePhase): List<ProjectRuntimeSkillStep> = steps.filter { it.phase == phase }

    fun phaseSummary(phase: ProjectRuntimePhase): String {
        val selected = stepsFor(phase)
        if (selected.isEmpty()) return "${phase.label}无额外能力"
        return "${phase.label} ${selected.size} 个能力 · ${selected.joinToString(" · ") { it.capability.label }}"
    }

    fun compactSummary(): String {
        val generation = stepsFor(ProjectRuntimePhase.GENERATION).size
        val post = stepsFor(ProjectRuntimePhase.POST_COMMIT).size
        val manual = stepsFor(ProjectRuntimePhase.MANUAL_REVIEW).size
        return buildString {
            append("正式项目 Skill OS · 生成期 $generation")
            if (post > 0) append(" · 提交后 $post")
            if (manual > 0) append(" · 复盘 $manual")
        }
    }
}

enum class ProjectRuntimeReceiptState {
    EXECUTED,
    SKIPPED,
    PENDING,
    FAILED,
}

data class ProjectRuntimeSkillReceipt(
    val step: ProjectRuntimeSkillStep,
    val state: ProjectRuntimeReceiptState,
    val evidence: String = "",
)

data class ProjectRuntimeSkillAudit(
    val receipts: List<ProjectRuntimeSkillReceipt>,
) {
    val executedCount: Int get() = receipts.count { it.state == ProjectRuntimeReceiptState.EXECUTED }
    val skippedCount: Int get() = receipts.count { it.state == ProjectRuntimeReceiptState.SKIPPED }
    val failedCount: Int get() = receipts.count { it.state == ProjectRuntimeReceiptState.FAILED }
    val pendingCount: Int get() = receipts.count { it.state == ProjectRuntimeReceiptState.PENDING }

    val runStatus: RunStatus
        get() = when {
            failedCount > 0 -> RunStatus.WARNING
            pendingCount > 0 -> RunStatus.WARNING
            else -> RunStatus.SUCCESS
        }

    fun summary(prefix: String = "Skill OS"): String = buildString {
        append(prefix).append(" · 已执行 ").append(executedCount)
        if (skippedCount > 0) append(" · 跳过 ").append(skippedCount)
        if (failedCount > 0) append(" · 失败 ").append(failedCount)
        if (pendingCount > 0) append(" · 待执行 ").append(pendingCount)
    }
}

object ProjectRuntimeSkillPlanner {
    fun build(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        referenceDnaCount: Int = 0,
    ): ProjectRuntimeSkillPlan {
        val generation = buildList {
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.CONTEXT_PACK,
                    ProjectRuntimePhase.GENERATION,
                    engine = "GenerationContextBuilder",
                    reason = "锁定 S/A/B/C：章节合同、Canon、当前状态与文风；Canon=${snapshot.bible.size} 条",
                )
            )
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.HYBRID_RAG,
                    ProjectRuntimePhase.GENERATION,
                    engine = "ChapterRunStore.retrieveRelevantContext",
                    reason = "按本章目标、场景、人物与章纲检索 D 层历史；召回结果不能覆盖 S/A",
                )
            )
            if (referenceDnaCount > 0) {
                add(
                    ProjectRuntimeSkillStep(
                        ProjectRuntimeCapability.REFERENCE_DNA,
                        ProjectRuntimePhase.GENERATION,
                        engine = "ReferenceDnaAwareAiGateway",
                        reason = "当前作品绑定 $referenceDnaCount 本 Reference DNA；按具体写作任务动态注入",
                    )
                )
            }
            if (snapshot.characters.isNotEmpty()) {
                add(
                    ProjectRuntimeSkillStep(
                        ProjectRuntimeCapability.CHARACTER_STATE,
                        ProjectRuntimePhase.GENERATION,
                        engine = "GenerationContextBuilder.B_STATE",
                        reason = "读取 ${snapshot.characters.size} 名人物的地点、身体、情绪、目标、关系与已知秘密",
                    )
                )
            }
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.CHRONOLOGY,
                    ProjectRuntimePhase.GENERATION,
                    engine = "ChronologyGuard",
                    reason = "时间轴锁 + 场景时间/耗时进入 A/B 层，并在最终 Gate 再校验",
                )
            )
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.ERA_TECH,
                    ProjectRuntimePhase.GENERATION,
                    engine = "EraTechnologyGuard",
                    reason = "年代、设备能力、社会普及度和人物使用理由执行确定性检查",
                )
            )
            if (snapshot.relevantForeshadowing.isNotEmpty()) {
                add(
                    ProjectRuntimeSkillStep(
                        ProjectRuntimeCapability.FORESHADOWING,
                        ProjectRuntimePhase.GENERATION,
                        engine = "GenerationContextBuilder + Metadata",
                        reason = "读取 ${snapshot.relevantForeshadowing.size} 条相关伏笔的可见/回收窗口，并只提取正文真实触碰项",
                    )
                )
            }
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.CONSISTENCY_GATE,
                    ProjectRuntimePhase.GENERATION,
                    engine = "ConsistencyGate",
                    reason = "正文冻结后统一检查章节合同、信息边界、时间线、时代技术、质量与主编结果",
                )
            )
        }

        val postCommit = listOf(
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.FULL_BOOK_AUDIT,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "FullBookEditorEngine",
                reason = "到周期巡检点时执行零额外模型成本的全书本地扫描",
                conditional = true,
            ),
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.EXECUTION_AUDIT,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "AutonomousExecutionEngine",
                reason = "比较滚动计划与实际正文，沉淀执行完成度",
                conditional = true,
            ),
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.AGENT_CANDIDATE,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "NovelAgentEngine → CandidateCanonEngine",
                reason = "Agent 只抽取正文事实；先进入 Candidate，再按规则确认 Canon",
                conditional = true,
            ),
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.AUTONOMOUS_REPLAN,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "AutonomousStoryPlanner",
                reason = "只有计划偏航或到刷新点时才局部重算未来章节",
                conditional = true,
            ),
        )

        return ProjectRuntimeSkillPlan(
            novelId = snapshot.novel.id,
            chapterNumber = draft.chapterNumber,
            steps = generation + postCommit,
        )
    }

    fun manualReview(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
    ): ProjectRuntimeSkillPlan = ProjectRuntimeSkillPlan(
        novelId = snapshot.novel.id,
        chapterNumber = draft.chapterNumber,
        steps = listOf(
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.AGENT_CANDIDATE,
                ProjectRuntimePhase.MANUAL_REVIEW,
                engine = "NovelAgentEngine → CandidateCanonEngine",
                reason = "复用正式 StorySnapshot 与已保存正文抽取结构化事实；不直接篡改 Canon",
            )
        ),
    )

    fun audit(
        plan: ProjectRuntimeSkillPlan,
        events: List<RunEvent>,
        phases: Set<ProjectRuntimePhase> = ProjectRuntimePhase.entries.toSet(),
    ): ProjectRuntimeSkillAudit {
        val receipts = plan.steps
            .filter { it.phase in phases }
            .map { step -> receipt(step, events) }
        return ProjectRuntimeSkillAudit(receipts)
    }

    private fun receipt(step: ProjectRuntimeSkillStep, events: List<RunEvent>): ProjectRuntimeSkillReceipt {
        val relevantStages = evidenceStages(step.capability)
        val evidence = events.filter { it.stage in relevantStages }
        if (evidence.isEmpty()) return ProjectRuntimeSkillReceipt(step, ProjectRuntimeReceiptState.PENDING)
        val latest = evidence.last()
        val state = when {
            evidence.any { it.status == RunStatus.FAILED } -> ProjectRuntimeReceiptState.FAILED
            evidence.any { it.status == RunStatus.SUCCESS || it.status == RunStatus.WARNING } -> ProjectRuntimeReceiptState.EXECUTED
            evidence.all { it.status == RunStatus.SKIPPED } -> ProjectRuntimeReceiptState.SKIPPED
            else -> ProjectRuntimeReceiptState.PENDING
        }
        return ProjectRuntimeSkillReceipt(
            step = step,
            state = state,
            evidence = "${latest.stage.label} · ${latest.status.name}${latest.detail.takeIf(String::isNotBlank)?.let { " · ${it.take(120)}" }.orEmpty()}",
        )
    }

    private fun evidenceStages(capability: ProjectRuntimeCapability): Set<RunStage> = when (capability) {
        ProjectRuntimeCapability.CONTEXT_PACK -> setOf(RunStage.DRAFT)
        ProjectRuntimeCapability.HYBRID_RAG -> setOf(RunStage.CONTEXT)
        ProjectRuntimeCapability.REFERENCE_DNA -> setOf(RunStage.DRAFT, RunStage.EDITOR_REWRITE)
        ProjectRuntimeCapability.CHARACTER_STATE -> setOf(RunStage.DRAFT)
        ProjectRuntimeCapability.CHRONOLOGY -> setOf(RunStage.DRAFT, RunStage.CONSISTENCY)
        ProjectRuntimeCapability.ERA_TECH -> setOf(RunStage.EDITOR_REVIEW_1, RunStage.EDITOR_REVIEW_2, RunStage.CONSISTENCY)
        ProjectRuntimeCapability.FORESHADOWING -> setOf(RunStage.METADATA)
        ProjectRuntimeCapability.CONSISTENCY_GATE -> setOf(RunStage.CONSISTENCY)
        ProjectRuntimeCapability.FULL_BOOK_AUDIT -> setOf(RunStage.FULL_BOOK_AUDIT)
        ProjectRuntimeCapability.EXECUTION_AUDIT -> setOf(RunStage.EXECUTION_AUDIT)
        ProjectRuntimeCapability.AGENT_CANDIDATE -> setOf(RunStage.CANDIDATE)
        ProjectRuntimeCapability.AUTONOMOUS_REPLAN -> setOf(RunStage.AUTONOMOUS_REPLAN)
    }
}
