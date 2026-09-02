package com.xiguli.langhuan.engine

/**
 * Novel Skill OS v2 execution plan.
 *
 * V1 answers "what capability is relevant". V2 answers "which existing Langhuan runtime
 * component actually handles it this turn". This object is transient telemetry/context only:
 * it is never Canon, never persisted into a story and never becomes RAG evidence.
 */
data class NovelExecutionStep(
    val label: String,
    val detail: String,
)

data class NovelSkillExecutionPlan(
    val primaryTask: AiTaskType?,
    val modelLabel: String,
    val inheritedGlobalModel: Boolean,
    val activeSkills: List<String>,
    val executed: List<NovelExecutionStep>,
    val projectPhaseEngines: List<NovelExecutionStep>,
    val status: NovelRouteStatus = NovelRouteStatus.SELECTED,
) {
    val compactSummary: String
        get() = buildList {
            primaryTask?.let { add(it.label) }
            if (modelLabel.isNotBlank()) add(modelLabel)
            if (activeSkills.isNotEmpty()) add("Skill ${activeSkills.size} 个")
            if (executed.isNotEmpty()) add("执行 ${executed.size} 项")
        }.joinToString(" · ")

    fun systemGuidance(): String = buildString {
        appendLine("【Novel Skill OS · V2 执行计划】")
        if (primaryTask != null) {
            appendLine("本轮主任务：${primaryTask.label}")
            appendLine("实际任务模型：$modelLabel${if (inheritedGlobalModel) "（继承全局）" else "（任务路由）"}")
        } else {
            appendLine("本轮不强行映射写作任务模型，使用全局会谈模型：$modelLabel")
        }
        if (activeSkills.isNotEmpty()) appendLine("实际加载 Writing Skills：${activeSkills.joinToString("、")}")
        if (executed.isNotEmpty()) {
            appendLine("本轮已准备/执行的数据源与组件：")
            executed.forEach { appendLine("- ${it.label}：${it.detail}") }
        }
        if (projectPhaseEngines.isNotEmpty()) {
            appendLine("以下能力需要正式项目 StorySnapshot 才能执行硬检查；当前会谈只做预防性分析，不得声称已经跑过这些引擎：")
            projectPhaseEngines.forEach { appendLine("- ${it.label}：${it.detail}") }
        }
        appendLine("Writing Skill 只控制写法；参考 DNA 只来自用户显式选择；任何路由/执行状态都不是作品事实。")
    }.trim()
}

object NovelSkillExecutionPlanner {
    /**
     * Only map a creation-chat turn onto an existing paid-writing task when the semantics are strong.
     * Fact lookup, casual chat, world/character Q&A deliberately stay on the normal chat model so
     * style Skills do not contaminate factual/design discussion.
     */
    fun primaryTask(route: NovelRouteDecision): AiTaskType? = when {
        route.intent == NovelIntent.CONTINUITY_REVIEW -> AiTaskType.EDITOR_REVIEW
        route.intent == NovelIntent.PROSE_REVISION -> AiTaskType.EDITOR_REWRITE
        route.intent == NovelIntent.STRUCTURE_PLANNING -> if (NovelCapability.SCENE_DIRECTOR in route.capabilities) {
            AiTaskType.SCENE_DIRECTOR
        } else {
            AiTaskType.AUTONOMOUS_PLANNER
        }
        route.intent == NovelIntent.STORY_DESIGN && (
            NovelCapability.LONG_STRUCTURE in route.capabilities ||
                NovelCapability.ENSEMBLE_CAST in route.capabilities
            ) -> AiTaskType.AUTONOMOUS_PLANNER
        else -> null
    }

    fun build(
        route: NovelRouteDecision,
        session: TaskRoutingSession,
        skills: WritingSkillSnapshot,
        referenceUsage: ReferenceDnaUsage,
        hasAttachments: Boolean,
        hasFoundation: Boolean,
    ): NovelSkillExecutionPlan {
        val task = primaryTask(route)
        val selected = task?.let(session::selection)
        val modelLabel = selected?.label ?: "${session.defaultProvider.name} · ${session.defaultProvider.model}"
        val activeSkills = task?.let(skills::forTask).orEmpty().map { it.name }

        val executed = buildList {
            if (NovelCapability.CONVERSATION_CONTEXT in route.capabilities) {
                add(NovelExecutionStep("多轮会谈", "读取当前会谈最近消息与用户后续覆盖决定"))
            }
            if (NovelCapability.ATTACHMENT_READER in route.capabilities && hasAttachments) {
                add(NovelExecutionStep("附件读取", "本轮附件文本/多模态附件进入模型上下文"))
            }
            if (NovelCapability.REFERENCE_DNA in route.capabilities && referenceUsage.reportCount > 0) {
                add(
                    NovelExecutionStep(
                        "Reference DNA",
                        if (referenceUsage.matchedItems > 0) {
                            "从 ${referenceUsage.reportCount} 本已选作品主动检索 ${referenceUsage.matchedItems} 条相关 DNA"
                        } else {
                            "已绑定 ${referenceUsage.reportCount} 本参考，但本轮没有达到检索阈值的 DNA 条目"
                        },
                    )
                )
            }
            if (hasFoundation && route.capabilities.any { it in FOUNDATION_AWARE_CAPABILITIES }) {
                add(NovelExecutionStep("当前方案/蓝图缓存", "作为会谈期已确认基线；后续明确决定可覆盖旧缓存"))
            }
            if (task != null) {
                add(
                    NovelExecutionStep(
                        "Task Model Router",
                        "${task.label} → $modelLabel${if (selected?.inheritedGlobal == true) "（继承全局）" else "（任务模型覆盖）"}",
                    )
                )
            }
            if (activeSkills.isNotEmpty()) {
                add(NovelExecutionStep("Writing Skill Store", "实际注入：${activeSkills.joinToString("、")}"))
            }
        }

        val projectEngines = buildList {
            if (NovelCapability.WORLD_CANON in route.capabilities) {
                add(NovelExecutionStep("Canon / Context Builder", "正式建书后进入 A·Canon 硬边界；会谈阶段不得自动写 Canon"))
            }
            if (NovelCapability.CHARACTER_STATE in route.capabilities || NovelCapability.ENSEMBLE_CAST in route.capabilities) {
                add(NovelExecutionStep("人物状态 / Agent", "正式项目按 StorySnapshot 人物状态、认知边界与关系事实执行"))
            }
            if (NovelCapability.TIMELINE in route.capabilities) {
                add(NovelExecutionStep("ChronologyGuard", "正式项目按结构化时间线与场景时间锁执行硬连续性检查"))
            }
            if (NovelCapability.ERA_TECH in route.capabilities) {
                add(NovelExecutionStep("EraTechnologyGuard", "正式项目结合 Canon 年代锚点做设备/技术确定性检查"))
            }
            if (NovelCapability.CONTINUITY in route.capabilities) {
                add(NovelExecutionStep("Consistency / Continuity", "正式项目对照章节合同、人物认知、Canon 与前文状态做 Gate"))
            }
            if (NovelCapability.FORESHADOWING in route.capabilities) {
                add(NovelExecutionStep("伏笔状态库", "正式项目读取 PLANTED/DEVELOPING 等状态与回收窗口，禁止会谈期伪造状态"))
            }
        }.distinctBy { it.label }

        return NovelSkillExecutionPlan(
            primaryTask = task,
            modelLabel = modelLabel,
            inheritedGlobalModel = selected?.inheritedGlobal ?: true,
            activeSkills = activeSkills,
            executed = executed,
            projectPhaseEngines = projectEngines,
        )
    }

    private val FOUNDATION_AWARE_CAPABILITIES = setOf(
        NovelCapability.WORLD_CANON,
        NovelCapability.CHARACTER_STATE,
        NovelCapability.ENSEMBLE_CAST,
        NovelCapability.LONG_STRUCTURE,
        NovelCapability.SCENE_DIRECTOR,
        NovelCapability.TIMELINE,
        NovelCapability.CONTINUITY,
        NovelCapability.FORESHADOWING,
    )
}
