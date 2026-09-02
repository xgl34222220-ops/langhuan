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
    /** Declared operational inputs. They are exposed as "read" only after execution has proof. */
    val dataInputs: List<String> = emptyList(),
    val outputContract: String = "",
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

enum class ProjectRuntimeReceiptState(val label: String) {
    EXECUTED("已执行"),
    SKIPPED("未触发 / 已跳过"),
    PENDING("未证实执行"),
    FAILED("执行失败"),
}

data class ProjectRuntimeExecutionEvidence(
    val stage: RunStage,
    val status: RunStatus,
    val detail: String,
    val atMillis: Long,
)

data class ProjectRuntimeSkillReceipt(
    val step: ProjectRuntimeSkillStep,
    val state: ProjectRuntimeReceiptState,
    /** Backward-compatible compact evidence string. */
    val evidence: String = "",
    /** Structured evidence copied from real RunEvent records only. */
    val evidenceTrail: List<ProjectRuntimeExecutionEvidence> = emptyList(),
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    /** Null means the capability has no independently measurable timing window. */
    val durationMs: Long? = null,
    /** Empty unless state == EXECUTED. Planned inputs are never presented as actually read. */
    val dataInputs: List<String> = emptyList(),
    val outputSummary: String = "",
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
        if (skippedCount > 0) append(" · 未触发/跳过 ").append(skippedCount)
        if (failedCount > 0) append(" · 失败 ").append(failedCount)
        if (pendingCount > 0) append(" · 无专属证据 ").append(pendingCount)
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
                    dataInputs = listOf(
                        "章节合同 / 章纲",
                        "Canon ${snapshot.bible.size} 条",
                        "场景计划 ${draft.scenePlan.size} 个",
                    ),
                    outputContract = "冻结高优先级写作上下文",
                )
            )
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.HYBRID_RAG,
                    ProjectRuntimePhase.GENERATION,
                    engine = "ChapterRunStore.retrieveRelevantContext",
                    reason = "按本章目标、场景、人物与章纲检索 D 层历史；召回结果不能覆盖 S/A",
                    dataInputs = listOf("本章目标", "场景计划", "人物名与当前状态", "当前章纲"),
                    outputContract = "D 层可解释历史召回",
                )
            )
            if (referenceDnaCount > 0) {
                add(
                    ProjectRuntimeSkillStep(
                        ProjectRuntimeCapability.REFERENCE_DNA,
                        ProjectRuntimePhase.GENERATION,
                        engine = "ReferenceDnaAwareAiGateway",
                        reason = "当前作品绑定 $referenceDnaCount 本 Reference DNA；只有实际命中并注入模型提示词才算执行",
                        conditional = true,
                        dataInputs = listOf("长期绑定 Reference DNA $referenceDnaCount 本", "当前模型任务查询"),
                        outputContract = "命中后注入可泛化的结构/文风方法",
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
                        dataInputs = listOf("人物状态 ${snapshot.characters.size} 名"),
                        outputContract = "B_STATE 人物状态约束",
                    )
                )
            }
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.CHRONOLOGY,
                    ProjectRuntimePhase.GENERATION,
                    engine = "ChronologyGuard",
                    reason = "时间轴锁 + 场景时间/耗时进入 A/B 层，并在最终 Gate 再校验",
                    dataInputs = listOf("近期时间线 ${snapshot.recentTimeline.size} 条", "场景 storyDay / timeOfDay"),
                    outputContract = "时间连续性检查结果",
                )
            )
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.ERA_TECH,
                    ProjectRuntimePhase.GENERATION,
                    engine = "EraTechnologyGuard",
                    reason = "年代、设备能力、社会普及度和人物使用理由执行确定性检查",
                    dataInputs = listOf("作品时代设定", "正文设备/技术表述"),
                    outputContract = "时代技术确定性检查结果",
                )
            )
            if (snapshot.relevantForeshadowing.isNotEmpty()) {
                add(
                    ProjectRuntimeSkillStep(
                        ProjectRuntimeCapability.FORESHADOWING,
                        ProjectRuntimePhase.GENERATION,
                        engine = "GenerationContextBuilder + Metadata",
                        reason = "读取 ${snapshot.relevantForeshadowing.size} 条相关伏笔的可见/回收窗口，并只提取正文真实触碰项",
                        dataInputs = listOf("相关伏笔 ${snapshot.relevantForeshadowing.size} 条", "冻结正文"),
                        outputContract = "正文真实触碰的伏笔 ID",
                    )
                )
            }
            add(
                ProjectRuntimeSkillStep(
                    ProjectRuntimeCapability.CONSISTENCY_GATE,
                    ProjectRuntimePhase.GENERATION,
                    engine = "ConsistencyGate",
                    reason = "正文冻结后统一检查章节合同、信息边界、时间线、时代技术、质量与主编结果",
                    dataInputs = listOf("冻结正文", "章节合同", "Canon", "主编审校结果"),
                    outputContract = "BLOCKING / WARNING 一致性报告",
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
                dataInputs = listOf("全书章节草稿", "LongForm 状态"),
                outputContract = "全书主编巡检报告",
            ),
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.EXECUTION_AUDIT,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "AutonomousExecutionEngine",
                reason = "比较滚动计划与实际正文，沉淀执行完成度",
                conditional = true,
                dataInputs = listOf("滚动计划", "已保存正文", "当前 StorySnapshot"),
                outputContract = "章节执行完成度与偏航范围",
            ),
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.AGENT_CANDIDATE,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "NovelAgentEngine → CandidateCanonEngine",
                reason = "Agent 只抽取正文事实；先进入 Candidate，再按规则确认 Canon",
                conditional = true,
                dataInputs = listOf("已保存正文", "当前 StorySnapshot"),
                outputContract = "Candidate 事实集合",
            ),
            ProjectRuntimeSkillStep(
                ProjectRuntimeCapability.AUTONOMOUS_REPLAN,
                ProjectRuntimePhase.POST_COMMIT,
                engine = "AutonomousStoryPlanner",
                reason = "只有计划偏航或到刷新点时才局部重算未来章节",
                conditional = true,
                dataInputs = listOf("执行审计结果", "未来滚动计划", "当前 StorySnapshot"),
                outputContract = "受影响的未来章节局部新计划",
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
                dataInputs = listOf("第${draft.chapterNumber}章已保存正文", "当前 StorySnapshot"),
                outputContract = "Candidate 事实集合",
            )
        ),
    )

    /**
     * Builds receipts from real RunEvent evidence only.
     *
     * [finalize] should be false for a live in-progress view. When true, a conditional step with no
     * dedicated evidence is reported as not triggered instead of pretending that it executed.
     */
    fun audit(
        plan: ProjectRuntimeSkillPlan,
        events: List<RunEvent>,
        phases: Set<ProjectRuntimePhase> = ProjectRuntimePhase.entries.toSet(),
        finalize: Boolean = true,
    ): ProjectRuntimeSkillAudit {
        val receipts = plan.steps
            .filter { it.phase in phases }
            .map { step -> receipt(step, events, finalize) }
        return ProjectRuntimeSkillAudit(receipts)
    }

    private fun receipt(
        step: ProjectRuntimeSkillStep,
        events: List<RunEvent>,
        finalize: Boolean,
    ): ProjectRuntimeSkillReceipt {
        val relevantStages = evidenceStages(step.capability)
        val evidenceEvents = events.filter { it.stage in relevantStages }
        if (evidenceEvents.isEmpty()) {
            val state = if (finalize && step.conditional) ProjectRuntimeReceiptState.SKIPPED else ProjectRuntimeReceiptState.PENDING
            return ProjectRuntimeSkillReceipt(
                step = step,
                state = state,
                evidence = if (state == ProjectRuntimeReceiptState.SKIPPED) "本轮没有专属执行证据，条件未触发" else "尚无专属 RunEvent 证明实际执行",
                outputSummary = if (state == ProjectRuntimeReceiptState.SKIPPED) "条件未触发" else "",
            )
        }

        val latestTerminalIndex = evidenceEvents.indexOfLast { it.status != RunStatus.RUNNING }
        val latestRunningIndex = evidenceEvents.indexOfLast { it.status == RunStatus.RUNNING }
        val latest = evidenceEvents.last()
        val terminal = evidenceEvents.getOrNull(latestTerminalIndex)
        val state = when {
            latestRunningIndex > latestTerminalIndex -> ProjectRuntimeReceiptState.PENDING
            terminal?.status == RunStatus.FAILED -> ProjectRuntimeReceiptState.FAILED
            terminal?.status == RunStatus.SKIPPED -> ProjectRuntimeReceiptState.SKIPPED
            terminal?.status == RunStatus.SUCCESS || terminal?.status == RunStatus.WARNING -> ProjectRuntimeReceiptState.EXECUTED
            else -> ProjectRuntimeReceiptState.PENDING
        }

        val completion = terminal?.takeIf { latestRunningIndex <= latestTerminalIndex }
        val matchingStart = completion?.let { done ->
            evidenceEvents.indexOfLast { event ->
                event.stage == done.stage && event.status == RunStatus.RUNNING && event.atMillis <= done.atMillis
            }.takeIf { it >= 0 }?.let(evidenceEvents::get)
        }
        val duration = if (matchingStart != null && completion != null && completion.atMillis >= matchingStart.atMillis) {
            completion.atMillis - matchingStart.atMillis
        } else null
        val latestForText = completion ?: latest
        val structured = evidenceEvents.takeLast(8).map { event ->
            ProjectRuntimeExecutionEvidence(
                stage = event.stage,
                status = event.status,
                detail = event.detail,
                atMillis = event.atMillis,
            )
        }

        return ProjectRuntimeSkillReceipt(
            step = step,
            state = state,
            evidence = "${latestForText.stage.label} · ${latestForText.status.name}${latestForText.detail.takeIf(String::isNotBlank)?.let { " · ${it.take(180)}" }.orEmpty()}",
            evidenceTrail = structured,
            startedAt = matchingStart?.atMillis ?: 0L,
            completedAt = completion?.atMillis ?: 0L,
            durationMs = duration,
            dataInputs = if (state == ProjectRuntimeReceiptState.EXECUTED) step.dataInputs else emptyList(),
            outputSummary = when (state) {
                ProjectRuntimeReceiptState.EXECUTED,
                ProjectRuntimeReceiptState.SKIPPED,
                ProjectRuntimeReceiptState.FAILED -> latestForText.detail.take(240)
                ProjectRuntimeReceiptState.PENDING -> ""
            },
        )
    }

    private fun evidenceStages(capability: ProjectRuntimeCapability): Set<RunStage> = when (capability) {
        ProjectRuntimeCapability.CONTEXT_PACK -> setOf(RunStage.CONTEXT_PACK)
        ProjectRuntimeCapability.HYBRID_RAG -> setOf(RunStage.HYBRID_RAG)
        // Never infer DNA use from DRAFT/EDITOR stages: a binding can exist while the query matches nothing.
        ProjectRuntimeCapability.REFERENCE_DNA -> setOf(RunStage.REFERENCE_DNA)
        ProjectRuntimeCapability.CHARACTER_STATE -> setOf(RunStage.CHARACTER_STATE)
        // ConsistencyGate deterministically invokes both guards on every completed inspection.
        ProjectRuntimeCapability.CHRONOLOGY -> setOf(RunStage.CONSISTENCY)
        ProjectRuntimeCapability.ERA_TECH -> setOf(RunStage.CONSISTENCY)
        ProjectRuntimeCapability.FORESHADOWING -> setOf(RunStage.METADATA)
        ProjectRuntimeCapability.CONSISTENCY_GATE -> setOf(RunStage.CONSISTENCY)
        ProjectRuntimeCapability.FULL_BOOK_AUDIT -> setOf(RunStage.FULL_BOOK_AUDIT)
        ProjectRuntimeCapability.EXECUTION_AUDIT -> setOf(RunStage.EXECUTION_AUDIT)
        ProjectRuntimeCapability.AGENT_CANDIDATE -> setOf(RunStage.CANDIDATE)
        ProjectRuntimeCapability.AUTONOMOUS_REPLAN -> setOf(RunStage.AUTONOMOUS_REPLAN)
    }
}
