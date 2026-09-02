package com.xiguli.langhuan.engine

/**
 * Novel Skill OS V6/V7 author-command router.
 *
 * This is intentionally deterministic and conservative. V7 adds a Canon proposal boundary:
 * explicit project-fact mutations may create a preview, but never write facts immediately.
 */
enum class WorkspaceNaturalAction(
    val label: String,
    val mutatesWorkingDraft: Boolean,
) {
    DISCUSS("讨论分析", false),
    CANON_PROPOSAL("Canon 变更提案", false),
    SCENE_PLAN("场景重排", true),
    PROSE("正文生成/重写", true),
    REVIEW("连续性审校", false),
}

data class WorkspaceNaturalPlan(
    val original: String,
    val actions: List<WorkspaceNaturalAction>,
    val reasons: List<String> = emptyList(),
) {
    val requestsCanonProposal: Boolean get() = WorkspaceNaturalAction.CANON_PROPOSAL in actions
    val hasSceneMutation: Boolean get() = WorkspaceNaturalAction.SCENE_PLAN in actions
    val hasProseMutation: Boolean get() = WorkspaceNaturalAction.PROSE in actions
    val requestsReview: Boolean get() = WorkspaceNaturalAction.REVIEW in actions
    val isDiscussionOnly: Boolean get() = actions == listOf(WorkspaceNaturalAction.DISCUSS)
    val isReviewOnly: Boolean get() = actions == listOf(WorkspaceNaturalAction.REVIEW)
    val mutatesWorkingDraft: Boolean get() = actions.any { it.mutatesWorkingDraft }

    val summary: String
        get() = actions.joinToString(" → ") { it.label }.ifBlank { WorkspaceNaturalAction.DISCUSS.label }

    val executionGuidance: String
        get() = buildString {
            append("本轮总控：$summary")
            if (requestsCanonProposal) append("；只生成影响分析和差异预览，确认前不写 StorySnapshot")
            if (hasSceneMutation) append("；先在 working ScenePlan 中调整，不直接写 Canon")
            if (hasProseMutation) append("；正文走现有章节 Runtime")
            if (requestsReview && hasProseMutation) append("；生成后由现有 Consistency Gate 做硬检查")
        }
}

object WorkspaceNaturalLanguageRouter {
    fun route(input: String): WorkspaceNaturalPlan {
        val text = input.trim()
        if (text.isBlank()) return WorkspaceNaturalPlan("", listOf(WorkspaceNaturalAction.DISCUSS))

        val sceneCue = containsAny(
            text,
            "场景", "场次", "第一场", "第二场", "第三场", "第四场", "第五场", "第六场",
            "入场", "出场", "场景顺序", "镜头", "切场",
        ) || Regex("第\\s*[0-9一二三四五六七八九十]+\\s*场").containsMatchIn(text)

        val proseCue = containsAny(
            text,
            "正文", "这一章", "这章", "本章", "这一段", "这段", "段落", "对白", "描写", "叙述",
            "写得", "写成", "重写", "润色", "续写", "继续写", "开始写", "文风", "语气", "措辞",
        ) || Regex("把.{1,24}写(?:得|成)").containsMatchIn(text)

        val reviewCue = containsAny(
            text,
            "检查", "审查", "连续性", "前后冲突", "前后矛盾", "设定冲突", "逻辑不通", "逻辑问题",
            "时间线", "时代问题", "年代问题", "不合理", "有没有冲突", "有没有矛盾", "是否冲突",
        )

        val explicitMutation = containsAny(
            text,
            "调整", "改成", "改为", "改一下", "修改", "重写", "润色", "提前", "延后", "移动", "删掉",
            "删除", "加入", "增加", "补上", "换成", "写得", "写成", "续写", "继续写", "开始写", "安排到",
        )

        val canonCue = containsAny(
            text,
            "核心设定", "世界设定", "世界观", "世界规则", "小说圣经", "故事圣经", "canon", "Bible",
            "人物设定", "角色设定", "人物状态", "角色状态", "主角设定", "能力设定", "能力规则",
            "时间线", "伏笔", "信息边界", "秘密真相", "总纲", "卷纲", "主题", "故事简介", "小说简介",
        )
        val canonMutation = canonCue && explicitMutation && !sceneCue

        // V7 safety boundary: once an explicit Canon-level edit is detected, stop downstream
        // prose/scene execution. The author must see and confirm the diff before anything uses it.
        if (canonMutation) {
            return WorkspaceNaturalPlan(
                original = text,
                actions = listOf(WorkspaceNaturalAction.CANON_PROPOSAL),
                reasons = listOf("检测到项目事实/蓝图级修改；进入提案→影响分析→确认写入边界"),
            )
        }

        val directProseCommand = containsAny(
            text,
            "重写这章", "重写本章", "重写正文", "续写正文", "继续写这章", "继续写本章", "开始写这一章", "开始写本章",
        )

        val questionLike = text.contains('?') || text.contains('？') || containsAny(
            text,
            "为什么", "怎么看", "怎么理解", "是不是", "是否", "有没有", "你觉得", "分析一下", "聊聊", "说说",
        )

        val sceneMutation = sceneCue && explicitMutation
        val proseMutation = directProseCommand || (proseCue && explicitMutation)
        val actions = mutableListOf<WorkspaceNaturalAction>()
        val reasons = mutableListOf<String>()

        if (sceneMutation) {
            actions += WorkspaceNaturalAction.SCENE_PLAN
            reasons += "检测到明确场景调整动作"
        }
        if (proseMutation) {
            actions += WorkspaceNaturalAction.PROSE
            reasons += "检测到明确正文执行动作"
        }
        if (reviewCue) {
            actions += WorkspaceNaturalAction.REVIEW
            reasons += "检测到连续性/逻辑检查要求"
        }

        if (actions.isEmpty()) {
            actions += WorkspaceNaturalAction.DISCUSS
            reasons += if (questionLike) "问题/讨论语气，不跨越项目写入边界" else "没有足够明确的场景、正文或 Canon 执行信号"
        }

        return WorkspaceNaturalPlan(text, actions.distinct(), reasons)
    }

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any { text.contains(it, ignoreCase = true) }
}
