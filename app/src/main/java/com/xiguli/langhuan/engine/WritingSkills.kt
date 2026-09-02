package com.xiguli.langhuan.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Skill 只属于 C 层写作方法，不是事实源，也不允许覆盖 Canon / ChapterContract / 时间线。
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
    val author: String = "",
    val builtin: Boolean = true,
    val customGuidance: String = "",
)

@Serializable
data class WritingSkillBinding(
    val skillId: String,
    val enabled: Boolean = true,
    val tasks: List<AiTaskType> = emptyList(),
)

@Serializable
data class UserWritingSkillManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val license: String = "自定义",
    val sourceUrl: String = "",
    val sourceRevision: String = "",
    val supportedTasks: List<AiTaskType> = listOf(AiTaskType.PROSE_AUTHOR),
    val defaultTasks: List<AiTaskType> = emptyList(),
    val guidance: String,
)

@Serializable
private data class WritingSkillConfig(
    val schemaVersion: Int = 2,
    val bindings: List<WritingSkillBinding> = emptyList(),
    val installed: List<UserWritingSkillManifest> = emptyList(),
)

sealed interface SkillInstallResult {
    data class Success(val skillName: String, val replaced: Boolean) : SkillInstallResult
    data class Error(val message: String) : SkillInstallResult
}

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
            author = "zenstory-ai",
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
            author = "conorbronsdon",
        ),
        WritingSkillDefinition(
            id = "sepia-fiction",
            name = "Sepia 叙事去 AI 化",
            description = "从叙事结构、段落推进和表面文风三层诊断 AI 痕迹；按五组叙事特征分步审查，先修结构，再做最小文字修改。",
            version = "0.4.1-adapted",
            license = "MIT",
            sourceUrl = "https://github.com/Nanako0129/sepia",
            sourceRevision = "ac2f06e8aa3d5a7ea3052e80e5815818322d688a",
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
                AiTaskType.NOVELIZATION,
                AiTaskType.EDITOR_REVIEW,
                AiTaskType.EDITOR_REWRITE,
                AiTaskType.AUTONOMOUS_PLANNER,
                AiTaskType.FULL_BOOK_EDITOR,
            ),
            author = "Nanako Tsai",
        ),
    )

    fun defaultBinding(skill: WritingSkillDefinition): WritingSkillBinding = WritingSkillBinding(
        skillId = skill.id,
        enabled = true,
        tasks = skill.defaultTasks.toList(),
    )

    fun guidance(skill: WritingSkillDefinition, task: AiTaskType): String {
        if (!skill.builtin) return skill.customGuidance.trim()
        return when (skill.id) {
            "story-long-write" -> storyLongWriteGuidance(task)
            "avoid-ai-writing" -> avoidAiWritingGuidance(task)
            "sepia-fiction" -> sepiaFictionGuidance(task)
            else -> ""
        }
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

    private fun sepiaFictionGuidance(task: AiTaskType): String {
        val common = """
            来源适配：Nanako0129/sepia v0.4.1（MIT）。这是叙事校准方法，不是作者身份检测器，也不能输出“AI 概率”。
            - Sepia 的四种操作要区分：write=新写；review=只诊断不改；refactor=完整列缺陷后做最小原位修改；recreate=先抽取事实/意图，再从事实重写。不要把 review 偷偷变成 rewrite。
            - 修订顺序固定为“叙事结构 → 段落/信息推进 → 词句表面”。结构问题不能靠同义改写掩盖；refactor/recreate 必须先列出缺陷，再从最深层开始处理。
            - 诊断时按五组分开读：A 主题过度决定；B 感官/身体化表演；C 结构过度整齐；D 人类正向标记；E 时间复杂度与多样性。每个命中都要能指出具体证据；无证据就不报。
            - 不把 30 项特征合成“作者身份分数”。它们只用于编辑校准；没有评估机会的项标 n/a，反向拉满也要视作过度校准风险。
            - 不要把所有规则同时套满。每章/每次规划只选择真正相关的 3-5 项，再允许一个有依据的稀有结构选择；保留普通句、未被解释的细节和适度粗粝感。
            - 逻辑必须成立，但“成立”不等于每个节拍都整齐服务单一主线。可以保留一个有生活质感的摩擦、旁支或未闭合细节，前提是不制造剧情漏洞、不违反章节合同。
            - 主题默认由事件、选择和后果暗示；删除旁白替读者总结的人生道理、成长结论和对符号的解释。
            - 情绪使用对白、行为、回避、误判、沉默、明确感受和身体反应的混合；身体感受只留在真正峰值，禁止全章反复“心头一紧/呼吸一滞”。
            - 揭示尽量后置并分层；不要在场景刚开始就解释异常、人物动机或主题。章末优先落在外部行动、代价、未完成选择或新威胁，不默认用“终于理解/接受自己”收束。
            - 做 QUD 检查：观察场景/段落是否反复沿“发生了什么→为什么→后果→意义”直线推进；允许比较、验证、矛盾、延迟解释和有回报的岔开。
            - 中段是重点检查区：避免开头承诺之后，中段退化为匀速填充、按计划解决悬念；相邻场景允许密度、对白比例和节奏明显不同。
            - 段落长度、关键句位置和问答节拍要自然变化；禁止连续使用“提出问题→立即回答→总结意义”的同一模板，也不要机械三段式/三项目。
            - 现实作品、地点、品牌和技术细节只能来自已确认 Canon、可靠研究或正文原有事实；绝不为了增加“人味”发明具体信息。
            - 尊重作者画像与人物对白。自然、有个人习惯的段落不动；优先替换/删除，新增只用于真实且必要的具体性。
        """.trimIndent()
        val taskSpecific = when (task) {
            AiTaskType.SCENE_DIRECTOR -> "章纲导演（write）：先填本章叙事架构选择，检查主题是否被直接讲明、转折是否是该故事独有、揭示是否过早；只选 3-5 个校准动作，不机械加支线。"
            AiTaskType.PROSE_AUTHOR -> "正文作者（write）：先保证人物为什么这么做在场景中成立，再避免单轨过度整齐、主题解说、同一种情绪写法和公式化成长收尾；风格清扫永远放最后。"
            AiTaskType.NOVELIZATION -> "小说化重构（recreate）：先抽取不可变事实、事件顺序与作者意图，再重新小说化；不得借重写发明具体事实。"
            AiTaskType.EDITOR_REVIEW -> "主编审稿（review）：只诊断，不直接改。按 A→E 五组分开检查，再做 QUD/中段/表面文风；每个问题给短证据和修复层级，不给 AI 身份概率。"
            AiTaskType.EDITOR_REWRITE -> "主编修订（refactor）：先使用已有审稿缺陷表，从最深层命中项开始，优先替换/删除；只改命中片段，禁止把整章统一抛光成另一种模型模板。"
            AiTaskType.AUTONOMOUS_PLANNER -> "自治规划（write/architecture）：跨章先看主题显性度、单轨因果、支线网络、揭示前置、时间结构和结局模式；只采用 3-5 个与当前故事匹配的结构动作，并给后续留下松弛空间。"
            AiTaskType.FULL_BOOK_EDITOR -> "全书主编（review）：跨章按 A-E 五组抽样比较主题、情绪、结构、时间和段落节拍趋势；把单章特例与全书重复模式分开报告，不用汇总分数判作者身份。"
            else -> ""
        }
        return "$common\n$taskSpecific".trim()
    }
}

class WritingSkillStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun definitions(): List<WritingSkillDefinition> = WritingSkillCatalog.all + load().installed.map(::toDefinition)

    @Synchronized
    fun bindings(): List<WritingSkillBinding> {
        val definitions = definitions()
        val stored = load().bindings.associateBy { it.skillId }
        return definitions.map { skill ->
            val existing = stored[skill.id]
            if (existing == null) WritingSkillCatalog.defaultBinding(skill)
            else existing.copy(tasks = existing.tasks.filter { it in skill.supportedTasks }.distinct())
        }
    }

    @Synchronized
    fun install(raw: String): SkillInstallResult {
        val manifest = runCatching { json.decodeFromString(UserWritingSkillManifest.serializer(), raw) }
            .getOrElse { return SkillInstallResult.Error("无法解析 Skill 文件：${it.message ?: "JSON 格式错误"}") }
        validate(manifest)?.let { return SkillInstallResult.Error(it) }
        if (WritingSkillCatalog.all.any { it.id == manifest.id }) {
            return SkillInstallResult.Error("不能覆盖琅嬛内置 Skill：${manifest.id}")
        }
        val current = load()
        val replaced = current.installed.any { it.id == manifest.id }
        val normalized = manifest.copy(
            id = manifest.id.trim(),
            name = manifest.name.trim(),
            description = manifest.description.trim(),
            version = manifest.version.trim(),
            author = manifest.author.trim(),
            license = manifest.license.trim(),
            sourceUrl = manifest.sourceUrl.trim(),
            sourceRevision = manifest.sourceRevision.trim(),
            supportedTasks = manifest.supportedTasks.distinct(),
            defaultTasks = manifest.defaultTasks.distinct().filter { it in manifest.supportedTasks },
            guidance = manifest.guidance.trim(),
        )
        val installed = current.installed.filterNot { it.id == normalized.id } + normalized
        val bindings = current.bindings.filterNot { it.skillId == normalized.id } + WritingSkillBinding(
            skillId = normalized.id,
            enabled = true,
            tasks = normalized.defaultTasks.ifEmpty { normalized.supportedTasks },
        )
        save(current.copy(installed = installed, bindings = bindings))
        return SkillInstallResult.Success(normalized.name, replaced)
    }

    @Synchronized
    fun uninstall(skillId: String): Boolean {
        if (WritingSkillCatalog.all.any { it.id == skillId }) return false
        val current = load()
        if (current.installed.none { it.id == skillId }) return false
        save(current.copy(
            installed = current.installed.filterNot { it.id == skillId },
            bindings = current.bindings.filterNot { it.skillId == skillId },
        ))
        return true
    }

    @Synchronized
    fun setEnabled(skillId: String, enabled: Boolean) {
        mutate(skillId) { it.copy(enabled = enabled) }
    }

    @Synchronized
    fun setTaskEnabled(skillId: String, task: AiTaskType, enabled: Boolean) {
        val skill = definitions().firstOrNull { it.id == skillId } ?: return
        if (task !in skill.supportedTasks) return
        mutate(skillId) { binding ->
            val tasks = binding.tasks.toMutableSet()
            if (enabled) tasks += task else tasks -= task
            binding.copy(tasks = tasks.toList())
        }
    }

    @Synchronized
    fun resetDefaults() {
        val current = load()
        val defaults = definitions().map(WritingSkillCatalog::defaultBinding)
        save(current.copy(bindings = defaults))
    }

    @Synchronized
    fun snapshot(): WritingSkillSnapshot {
        val definitions = definitions()
        val current = bindings().associateBy { it.skillId }
        val active = AiTaskType.entries.associateWith { task ->
            definitions.filter { skill ->
                val binding = current[skill.id]
                binding?.enabled == true && task in binding.tasks && task in skill.supportedTasks
            }
        }
        return WritingSkillSnapshot(active)
    }

    private fun validate(skill: UserWritingSkillManifest): String? {
        if (skill.schemaVersion != 1) return "暂不支持 schemaVersion=${skill.schemaVersion}"
        if (!skill.id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9._-]{2,63}"))) return "Skill id 只能使用 3-64 位字母、数字、点、横线或下划线"
        if (skill.name.isBlank() || skill.name.length > 80) return "Skill 名称不能为空且不能超过 80 字"
        if (skill.guidance.isBlank()) return "Skill guidance 不能为空"
        if (skill.guidance.length > MAX_GUIDANCE) return "Skill guidance 不能超过 $MAX_GUIDANCE 字"
        if (skill.supportedTasks.isEmpty()) return "至少选择一个 supportedTasks"
        return null
    }

    private fun toDefinition(skill: UserWritingSkillManifest) = WritingSkillDefinition(
        id = skill.id,
        name = skill.name,
        description = skill.description,
        version = skill.version,
        license = skill.license,
        sourceUrl = skill.sourceUrl,
        sourceRevision = skill.sourceRevision,
        supportedTasks = skill.supportedTasks.toSet(),
        defaultTasks = skill.defaultTasks.toSet().ifEmpty { skill.supportedTasks.toSet() },
        author = skill.author,
        builtin = false,
        customGuidance = skill.guidance,
    )

    private fun mutate(skillId: String, update: (WritingSkillBinding) -> WritingSkillBinding) {
        val skill = definitions().firstOrNull { it.id == skillId } ?: return
        val currentConfig = load()
        val all = bindings().associateBy { it.skillId }.toMutableMap()
        val current = all[skillId] ?: WritingSkillCatalog.defaultBinding(skill)
        all[skillId] = update(current)
        val ordered = definitions().mapNotNull { all[it.id] }
        save(currentConfig.copy(bindings = ordered))
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
        const val MAX_GUIDANCE = 24_000
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
    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit) = delegate.generateStreaming(decorate(prompt), onDelta)
    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String = delegate.generateTextStreaming(decorate(prompt), onDelta)

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
