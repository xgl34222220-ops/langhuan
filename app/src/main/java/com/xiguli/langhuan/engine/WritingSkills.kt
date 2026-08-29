package com.xiguli.langhuan.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Native Android adaptation of external writing skills.
 *
 * Skills are craft-layer guidance only. They may influence how a task is performed, but they never
 * mutate Canon, ChapterContract, knowledge boundaries, chronology, Candidate facts or RAG contents.
 */
data class WritingSkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val license: String,
    val sourceUrl: String,
    val sourceRevision: String,
    val supportedTasks: Set<AiTaskType>,
    val defaultTasks: Set<AiTaskType>,
)

@Serializable
data class WritingSkillBinding(
    val skillId: String,
    val enabled: Boolean = true,
    val tasks: List<AiTaskType> = emptyList(),
)

@Serializable
private data class WritingSkillConfig(
    val schemaVersion: Int = 1,
    val bindings: List<WritingSkillBinding> = emptyList(),
)

object WritingSkillCatalog {
    val all: List<WritingSkillDefinition> = listOf(
        WritingSkillDefinition(
            id = "story-long-write",
            name = "长篇网文写作",
            description = "长篇写作方法：约束锁、情绪目标、章内推进、悬疑/反转、章尾钩子与按需上下文。",
            version = "1.0.0-adapted",
            license = "MIT",
            sourceUrl = "https://github.com/zenstory-ai/oh-story-claudecode/tree/main/skills/story-long-write",
            sourceRevision = "70c294b20ce89440e70edb766b0446d3057bc077",
            supportedTasks = setOf(
                AiTaskType.SCENE_DIRECTOR,
                AiTaskType.PROSE_AUTHOR,
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
                AiTaskType.AUTONOMOUS_PLANNER,
                AiTaskType.FULL_BOOK_EDITOR,
            ),
            defaultTasks = setOf(
                AiTaskType.SCENE_DIRECTOR,
                AiTaskType.PROSE_AUTHOR,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
                AiTaskType.AUTONOMOUS_PLANNER,
            ),
        ),
        WritingSkillDefinition(
            id = "avoid-ai-writing",
            name = "去 AI 写作痕迹",
            description = "检测并压低模板句、报告腔、解释腔、机械排比、同义复述和过度总结。",
            version = "3.28.0-adapted",
            license = "MIT",
            sourceUrl = "https://github.com/conorbronsdon/avoid-ai-writing",
            sourceRevision = "3bd64f19f41ae941d44e8261fe575624a2b1b8f6",
            supportedTasks = setOf(
                AiTaskType.PROSE_AUTHOR,
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
            ),
            defaultTasks = setOf(
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
            ),
        ),
    )

    fun definition(id: String): WritingSkillDefinition? = all.firstOrNull { it.id == id }

    fun defaultBinding(skill: WritingSkillDefinition): WritingSkillBinding = WritingSkillBinding(
        skillId = skill.id,
        enabled = true,
        tasks = skill.defaultTasks.toList(),
    )

    fun guidance(skill: WritingSkillDefinition, task: AiTaskType): String = when (skill.id) {
        "story-long-write" -> storyLongWriteGuidance(task)
        "avoid-ai-writing" -> avoidAiWritingGuidance(task)
        else -> ""
    }

    private fun storyLongWriteGuidance(task: AiTaskType): String {
        val common = """
            来源适配：oh-story-claudecode / story-long-write（MIT）。
            - 先锁定本次任务的用户明确要求、必须发生、禁止发生、时间锚、人物进出状态与停笔点，再考虑任何写作技法。
            - 技法只属于 C 层。S/A/B、Canon、Chapter Contract、人物认知边界、时间线与用户明确决定拥有更高优先级；发生冲突时立刻放弃 Skill 建议。
            - 每个场景必须有明确的情绪目标和剧情功能，至少推进信息、关系、选择、代价、威胁中的一项；不要写只负责填字数的场景。
            - 只加载/使用本任务真正需要的信息。不要为了展示世界观完整而把设定、人物、规则、伏笔集中解释给读者。
            - 章尾钩子必须由本章已有因果推出新的问题、选择或威胁，禁止靠提前揭露未来答案制造刺激。
            - 悬疑/惊悚优先递进信息差：一次异常只推进一层认知，未到回收期的线索只保持存在感，不解释谜底。
            - 参考作品只能抽取结构、情绪、节奏和功能位，禁止复刻独特角色、专名、具体桥段或连续表达。
        """.trimIndent()
        val taskSpecific = when (task) {
            AiTaskType.SCENE_DIRECTOR -> "章纲导演额外要求：先写清主角本章目标、阻力、关键选择、代价、信息增量和章末新债，再编排场景顺序。"
            AiTaskType.PROSE_AUTHOR -> "正文作者额外要求：把规则和推理写进人物行动、观察、对话和后果，不把后台资料改写成正文报告。"
            AiTaskType.NOVELIZATION -> "小说化额外要求：保留事实与事件顺序，只把说明/清单转换成可感知的场景、动作、阻碍、反应和选择。"
            AiTaskType.EDITOR_REVIEW -> "主编审稿额外要求：检查场景是否真正兑现情绪目标、章内推进与读者契约，而不是只看语句通顺。"
            AiTaskType.EDITOR_REWRITE -> "主编修订额外要求：只修审稿命中的问题，不借重写机会增加新设定、提前伏笔答案或改变角色立场。"
            AiTaskType.AUTONOMOUS_PLANNER -> "自治规划额外要求：滚动计划必须保留未来空间，优先安排承诺兑现、人物选择与新债，不把终局答案提前塞进近期章节。"
            AiTaskType.FULL_BOOK_EDITOR -> "全书主编额外要求：关注情绪模块重复、钩子同质化、人物功能位固化和长期承诺拖欠。"
            else -> ""
        }
        return "$common\n$taskSpecific".trim()
    }

    private fun avoidAiWritingGuidance(task: AiTaskType): String {
        val common = """
            来源适配：conorbronsdon/avoid-ai-writing（MIT）。
            - 这是写作质量提示，不是“AI 检测器”，不得把命中模式当作作者身份判断。
            - 删除报告腔、总结腔、空泛结论、机械排比、同义反复、连续“不是 A 而是 B”、模板化升华和无必要的小标题/清单。
            - 已经自然、具体、有角色声音的段落不要为了“去 AI”而整段重写；优先最小修改，保留作者已经形成的句法节奏和粗粝感。
            - 用具体动作、物件、空间关系、潜台词和后果替代抽象评价；不要把场景结论再解释一遍给读者。
            - 不得为了追求“人味”故意制造事实错误、语法破坏、角色 OOC 或与本书既定文风冲突的口语化。
        """.trimIndent()
        val taskSpecific = when (task) {
            AiTaskType.PROSE_AUTHOR -> "正文阶段只把这些规则当自检清单，不要在正文里谈论 AI、规则、检测或修改过程。"
            AiTaskType.NOVELIZATION -> "小说化阶段优先处理信息清单、功能动作堆叠、解释性结论和泛化恐怖意象。"
            AiTaskType.EDITOR_REVIEW -> "审稿阶段把 AI 腔问题定位到具体段落，并区分‘明显损伤叙事’与‘作者可能有意的重复/节奏’。"
            AiTaskType.EDITOR_REWRITE -> "修订阶段只改命中片段；不要把整章抛光成统一、无棱角的模型腔。"
            else -> ""
        }
        return "$common\n$taskSpecific".trim()
    }
}

class WritingSkillStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun bindings(): List<WritingSkillBinding> {
        val stored = load().bindings.associateBy { it.skillId }
        return WritingSkillCatalog.all.map { skill ->
            val existing = stored[skill.id]
            if (existing == null) WritingSkillCatalog.defaultBinding(skill)
            else existing.copy(tasks = existing.tasks.filter { it in skill.supportedTasks }.distinct())
        }
    }

    @Synchronized
    fun setEnabled(skillId: String, enabled: Boolean) {
        mutate(skillId) { it.copy(enabled = enabled) }
    }

    @Synchronized
    fun setTaskEnabled(skillId: String, task: AiTaskType, enabled: Boolean) {
        val skill = WritingSkillCatalog.definition(skillId) ?: return
        if (task !in skill.supportedTasks) return
        mutate(skillId) { binding ->
            val tasks = binding.tasks.toMutableSet()
            if (enabled) tasks += task else tasks -= task
            binding.copy(tasks = tasks.toList())
        }
    }

    @Synchronized
    fun resetDefaults() {
        save(WritingSkillConfig(bindings = WritingSkillCatalog.all.map(WritingSkillCatalog::defaultBinding)))
    }

    @Synchronized
    fun snapshot(): WritingSkillSnapshot {
        val current = bindings().associateBy { it.skillId }
        val active = AiTaskType.entries.associateWith { task ->
            WritingSkillCatalog.all.filter { skill ->
                val binding = current[skill.id]
                binding?.enabled == true && task in binding.tasks && task in skill.supportedTasks
            }
        }
        return WritingSkillSnapshot(active)
    }

    private fun mutate(skillId: String, update: (WritingSkillBinding) -> WritingSkillBinding) {
        val skill = WritingSkillCatalog.definition(skillId) ?: return
        val all = bindings().associateBy { it.skillId }.toMutableMap()
        val current = all[skillId] ?: WritingSkillCatalog.defaultBinding(skill)
        all[skillId] = update(current)
        save(WritingSkillConfig(bindings = WritingSkillCatalog.all.mapNotNull { all[it.id] }))
    }

    private fun load(): WritingSkillConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return WritingSkillConfig()
        return runCatching { json.decodeFromString(WritingSkillConfig.serializer(), raw) }
            .getOrElse { WritingSkillConfig() }
    }

    private fun save(config: WritingSkillConfig) {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(WritingSkillConfig.serializer(), config)).commit()
    }

    private companion object {
        const val PREFS = "langhuan_writing_skills"
        const val KEY_CONFIG = "writing_skills_v1"
    }
}

data class WritingSkillSnapshot(
    private val activeByTask: Map<AiTaskType, List<WritingSkillDefinition>>,
) {
    fun forTask(task: AiTaskType): List<WritingSkillDefinition> = activeByTask[task].orEmpty()
}

class SkillAwareAiGateway(
    private val delegate: AiGateway,
    private val task: AiTaskType,
    private val skills: List<WritingSkillDefinition>,
) : AiGateway {
    override suspend fun generate(prompt: PromptBundle) = delegate.generate(decorate(prompt))

    override suspend fun generateText(prompt: PromptBundle): String = delegate.generateText(decorate(prompt))

    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit) =
        delegate.generateStreaming(decorate(prompt), onDelta)

    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String =
        delegate.generateTextStreaming(decorate(prompt), onDelta)

    internal fun decorate(prompt: PromptBundle): PromptBundle {
        if (skills.isEmpty()) return prompt
        val blocks = skills.mapNotNull { skill ->
            WritingSkillCatalog.guidance(skill, task).takeIf { it.isNotBlank() }?.let { guidance ->
                "【Skill · ${skill.name}】\n$guidance"
            }
        }
        if (blocks.isEmpty()) return prompt
        return prompt.copy(
            system = buildString {
                append(prompt.system.trimEnd())
                append("\n\n【C·写作 Skill（只影响写法，不是事实源）】\n")
                append("以下 Skill 不能覆盖用户明确要求、S/A/B、Canon、Chapter Contract、人物知识边界、时间线或 Candidate/Canon 审批。发生冲突时以上层约束为准。\n\n")
                append(blocks.joinToString("\n\n"))
            },
        )
    }
}
