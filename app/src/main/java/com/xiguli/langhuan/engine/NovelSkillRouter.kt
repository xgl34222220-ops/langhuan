package com.xiguli.langhuan.engine

/**
 * Novel Skill OS v1.
 *
 * This layer routes a user's natural-language creation request to the minimum set of
 * novel capabilities needed for the turn. It intentionally sits above WritingSkillStore:
 * user-installed writing skills still control writing technique, while this router decides
 * which product capabilities should participate in a turn.
 *
 * Routing is advisory/contextual only. A decision never writes Canon, blueprint or project data.
 */
enum class NovelIntent(val label: String) {
    CASUAL_CHAT("自由聊天"),
    STORY_DESIGN("新书构思"),
    REFERENCE_ANALYSIS("参考研究"),
    WORLD_BUILDING("世界设定"),
    CHARACTER_DESIGN("人物设计"),
    STRUCTURE_PLANNING("结构规划"),
    PROSE_REVISION("正文修改"),
    CONTINUITY_REVIEW("连续性检查"),
    FACT_LOOKUP("资料事实读取"),
}

enum class NovelCapability(
    val label: String,
    val description: String,
) {
    CONVERSATION_CONTEXT("多轮上下文", "承接当前会谈与最近明确决定"),
    ATTACHMENT_READER("附件识别", "读取设定、人物、大纲、正文与资料附件"),
    REFERENCE_DNA("参考 DNA", "检索已选参考作品中与本轮真正相关的条目"),
    REFERENCE_ABSTRACTION("参考抽象", "只抽取结构、情绪、节奏与功能位"),
    DEIMITATION("去模仿化", "避免复刻专名、能力规则、独特桥段与谜底"),
    WORLD_CANON("世界规则", "检查世界规则、能力代价、势力与设定闭环"),
    CHARACTER_STATE("人物状态", "关注人物动机、认知边界、关系与当前状态"),
    ENSEMBLE_CAST("群像角色", "同时维护多名关键人物的目标、关系与功能"),
    LONG_STRUCTURE("长篇结构", "关注总纲、分卷、主线升级与长期承诺"),
    SCENE_DIRECTOR("章纲导演", "规划本章目标、阻力、选择、代价与场景顺序"),
    TIMELINE("时间线", "检查事件先后、时间锚与跨章时间一致性"),
    ERA_TECH("时代技术", "检查年代、设备、制度与技术细节是否符合时代"),
    CONTINUITY("连续性", "对照前文、Canon、人物状态与已发生事实"),
    FORESHADOWING("伏笔", "检查伏笔的埋设、触碰、回收与释放节奏"),
    PROSE_EDITOR("正文编辑", "定位正文中的逻辑、叙事、表达与场景问题"),
}

enum class NovelRouteStatus {
    SELECTED,
    RUNNING,
    SUCCESS,
    FAILED,
}

data class NovelRouteInput(
    val message: String,
    val attachmentPurposes: List<String> = emptyList(),
    val hasConversationHistory: Boolean = true,
    val hasFoundation: Boolean = false,
    val hasSelectedReferences: Boolean = false,
    val referenceFactQuestion: Boolean = false,
)

data class NovelRouteDecision(
    val intent: NovelIntent,
    val capabilities: List<NovelCapability>,
    val reasons: List<String> = emptyList(),
    val status: NovelRouteStatus = NovelRouteStatus.SELECTED,
) {
    val summary: String
        get() = if (capabilities.isEmpty()) intent.label
        else "${intent.label} · ${capabilities.joinToString(" / ") { it.label }}"

    val compactSummary: String
        get() = if (capabilities.isEmpty()) intent.label
        else "已启用 ${capabilities.size} 个能力：${capabilities.joinToString(" · ") { it.label }}"

    /** Hidden prompt instructions. Never expose internal routing fields as story facts. */
    fun systemGuidance(): String = buildString {
        appendLine("【Novel Skill OS · 本轮自动路由】")
        appendLine("意图：${intent.label}")
        if (capabilities.isNotEmpty()) {
            appendLine("仅启用以下能力：")
            capabilities.forEach { appendLine("- ${it.label}：${it.description}") }
        }
        appendLine("路由规则：未命中的能力不要为了展示功能而主动展开；普通聊天只做理解、分析和建议，不得因此自动写入 Canon、建书蓝图或正式项目。")
        appendLine("用户明确决定、上传原文、Canon、时间线和章节合同始终高于任何写作技巧或参考迁移。")
        if (NovelCapability.REFERENCE_ABSTRACTION in capabilities || NovelCapability.DEIMITATION in capabilities) {
            appendLine("参考作品只允许迁移可泛化机制；禁止复刻专名、独特能力规则、标志性桥段、连续表达和核心谜底。")
        }
    }.trim()
}

object NovelSkillRouter {
    fun route(input: NovelRouteInput): NovelRouteDecision {
        val text = input.message.trim()
        if (text.isBlank() && input.attachmentPurposes.isEmpty()) {
            return NovelRouteDecision(NovelIntent.CASUAL_CHAT, emptyList())
        }

        val capabilities = linkedSetOf<NovelCapability>()
        val reasons = mutableListOf<String>()

        if (input.hasConversationHistory) capabilities += NovelCapability.CONVERSATION_CONTEXT
        if (input.attachmentPurposes.isNotEmpty()) {
            capabilities += NovelCapability.ATTACHMENT_READER
            reasons += "本轮包含用户附件"
        }

        val referenceCue = containsAny(text, "参考", "类似", "像《", "风格", "原作", "这本", "那本", "他们", "这几本", "模板", "dna")
        val designCue = containsAny(text, "想写", "写一本", "新书", "题材", "核心设定", "设定一个", "构思", "故事", "小说")
        val worldCue = containsAny(text, "世界观", "世界规则", "规则", "设定", "能力体系", "能力代价", "力量体系", "势力", "组织", "地点")
        val characterCue = containsAny(text, "主角", "配角", "人物", "角色", "人设", "动机", "关系", "姓名", "名字", "取名")
        val ensembleCue = containsAny(text, "群像", "多主角", "多人视角", "多个主角", "全员", "配角群", "角色群")
        val structureCue = containsAny(text, "总纲", "卷纲", "分卷", "大纲", "章纲", "章节规划", "结构", "主线", "支线", "节奏", "下一章", "本章")
        val sceneCue = containsAny(text, "章纲", "场景", "下一章", "本章怎么写", "本章安排", "章节规划")
        val foreshadowCue = containsAny(text, "伏笔", "线索", "回收", "谜底", "悬念")
        val revisionCue = containsAny(text, "修改", "改一下", "重写", "润色", "不合理", "逻辑不通", "有问题", "bug", "前后矛盾", "不对劲")
        val continuityCue = containsAny(text, "前文", "前面", "之前", "前后", "矛盾", "连续性", "设定冲突", "状态不对", "逻辑不通", "不合理")
        val timelineCue = containsAny(text, "时间线", "时间", "日期", "年份", "年代", "几天后", "当天", "昨天", "第二天", "先后") || containsYear(text)
        val eraTechCue = containsAny(text, "座机", "手机", "来电显示", "互联网", "网络", "微信", "短信", "电脑", "技术", "设备", "年代", "年份") || containsYear(text)
        val explicitNoCopy = containsAny(text, "不要照搬", "不能照搬", "别照搬", "不要抄", "不能抄", "去模仿", "去同质化", "不要复刻", "避免照搬")

        if (input.referenceFactQuestion) {
            if (input.hasSelectedReferences) capabilities += NovelCapability.REFERENCE_DNA
            capabilities += NovelCapability.REFERENCE_ABSTRACTION
            reasons += "用户正在询问已选参考作品的事实或设定"
            return NovelRouteDecision(
                intent = NovelIntent.FACT_LOOKUP,
                capabilities = capabilities.toList(),
                reasons = reasons,
            )
        }

        if (input.hasSelectedReferences && (referenceCue || designCue)) {
            capabilities += NovelCapability.REFERENCE_DNA
        }
        if (referenceCue) {
            capabilities += NovelCapability.REFERENCE_ABSTRACTION
            capabilities += NovelCapability.DEIMITATION
            reasons += "检测到参考作品/风格迁移意图"
        } else if (explicitNoCopy) {
            capabilities += NovelCapability.DEIMITATION
        }

        if (worldCue) capabilities += NovelCapability.WORLD_CANON
        if (characterCue) capabilities += NovelCapability.CHARACTER_STATE
        if (ensembleCue) {
            capabilities += NovelCapability.CHARACTER_STATE
            capabilities += NovelCapability.ENSEMBLE_CAST
        }
        if (structureCue) capabilities += NovelCapability.LONG_STRUCTURE
        if (sceneCue) capabilities += NovelCapability.SCENE_DIRECTOR
        if (foreshadowCue) capabilities += NovelCapability.FORESHADOWING
        if (timelineCue) capabilities += NovelCapability.TIMELINE
        if (eraTechCue && (revisionCue || timelineCue)) capabilities += NovelCapability.ERA_TECH
        if (continuityCue || (revisionCue && input.hasFoundation)) capabilities += NovelCapability.CONTINUITY
        if (revisionCue) capabilities += NovelCapability.PROSE_EDITOR

        val intent = when {
            revisionCue && (continuityCue || timelineCue || eraTechCue) -> NovelIntent.CONTINUITY_REVIEW
            revisionCue -> NovelIntent.PROSE_REVISION
            referenceCue && !designCue -> NovelIntent.REFERENCE_ANALYSIS
            structureCue -> NovelIntent.STRUCTURE_PLANNING
            worldCue && !designCue -> NovelIntent.WORLD_BUILDING
            characterCue && !designCue -> NovelIntent.CHARACTER_DESIGN
            designCue -> NovelIntent.STORY_DESIGN
            else -> NovelIntent.CASUAL_CHAT
        }

        // A broad story-design request should get a useful but still bounded planning set.
        if (intent == NovelIntent.STORY_DESIGN) {
            if (ensembleCue) capabilities += NovelCapability.ENSEMBLE_CAST
            if (worldCue) capabilities += NovelCapability.WORLD_CANON
            if (structureCue || containsAny(text, "长篇", "网文", "几十万字", "百万字")) {
                capabilities += NovelCapability.LONG_STRUCTURE
            }
        }

        return NovelRouteDecision(intent, capabilities.toList(), reasons)
    }

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it, ignoreCase = true) }

    private fun containsYear(text: String): Boolean = Regex("(?:19|20)\\d{2}年?").containsMatchIn(text)
}
