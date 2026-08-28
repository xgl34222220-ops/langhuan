package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

data class PromptMessage(
    val role: String,
    val content: String,
)

data class PromptBundle(
    val system: String,
    val user: String,
    val attachments: List<PromptAttachment> = emptyList(),
    val messages: List<PromptMessage> = emptyList(),
    val jsonMode: Boolean = true,
)

data class PromptAttachment(
    val fileName: String,
    val mimeType: String,
    val base64Data: String,
)

class PromptAssembler(
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
) {
    /**
     * Pure prose prompt. The author never has to maintain database fields while writing.
     * It only receives the current chapter's dramatic job plus past facts needed for continuity.
     */
    fun buildProse(request: GenerationRequest): PromptBundle {
        val snapshot = request.snapshot
        val chapterNumber = request.chapter.chapterNumber.coerceAtLeast(1)
        val styleRules = snapshot.bible
            .filter { it.category == BibleCategory.STYLE }
            .joinToString("\n") { "- ${it.name}: ${it.content}${if (it.locked) "（必须遵守）" else "（偏好）"}" }
            .ifBlank { "- 保持自然、具体、有场景感的中文小说叙事；不要写成设定说明或案件报告。" }

        val hardRules = snapshot.bible
            .filter { it.category != BibleCategory.STYLE && (it.locked || it.category == BibleCategory.FORBIDDEN) }
            .joinToString("\n") { "- [${it.category}] ${it.name}: ${it.content}" }

        // Higher-level outlines provide direction only. Their later turning points are deliberately hidden
        // from the prose author so it cannot spend future chapters' reveals early.
        val direction = snapshot.activeOutline
            .filter { it.level != OutlineLevel.CHAPTER }
            .sortedWith(compareBy({ it.level.ordinal }, { it.order }))
            .joinToString("\n") { node ->
                val level = if (node.level == OutlineLevel.MASTER) "全书方向" else "本卷方向"
                "- [$level] ${node.title}｜阶段目标=${node.objective}｜长期冲突=${node.conflict}"
            }
            .ifBlank { "- 以当前章纲为准。" }

        val chapterOutline = snapshot.activeOutline
            .lastOrNull { it.level == OutlineLevel.CHAPTER }
        val chapterTask = buildString {
            appendLine("章节：第${request.chapter.chapterNumber}章 ${request.chapter.title}")
            appendLine("本章唯一主目标：${request.chapter.objective}")
            chapterOutline?.let { node ->
                appendLine("本章冲突：${node.conflict}")
                appendLine("本章转折/落点：${node.turningPoint}")
                if (node.mustInclude.isNotEmpty()) appendLine("本章必须出现：${node.mustInclude.joinToString("、")}")
                if (node.forbidden.isNotEmpty()) appendLine("本章绝对禁止：${node.forbidden.joinToString("、")}")
            }
        }.trim()

        val characters = snapshot.characters.joinToString("\n") {
            "- ${it.name}: 地点=${it.location}; 身体=${it.physicalState}; 情绪=${it.emotionalState}; " +
                "当前目标=${it.goal}; 性格=${it.personality.joinToString("、")}; " +
                "关系=${it.relationshipNotes.entries.joinToString("；") { e -> "${e.key}=${e.value}" }}; " +
                "本人已知=${it.knownSecrets.joinToString("、")}"
        }

        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(24)
            .joinToString("\n") {
                val structured = if (it.storyDay > 0) {
                    "故事第${it.storyDay}天·${it.timeOfDay.ifBlank { it.storyTime }}｜距上次=${it.elapsedFromPrevious.ifBlank { "未记录" }}${if (it.isFlashback) "｜闪回" else ""}"
                } else it.storyTime.ifBlank { "旧时间记录未结构化" }
                "- 第${it.chapter}章/$structured/${it.location}: ${it.summary}"
            }

        val foreshadowing = snapshot.relevantForeshadowing
            .filter { it.status !in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED) }
            .joinToString("\n") { item ->
                val due = item.expectedChapterStart > 0 && chapterNumber >= item.expectedChapterStart
                if (due) {
                    "- ${item.title}: 已进入可触及窗口；已有线索=${item.detail}；本章只有章纲需要时才自然推进。"
                } else {
                    "- ${item.title}: 已有线索=${item.detail}；尚未到解释/回收期，本章禁止揭示答案。"
                }
            }
            .ifBlank { "- 无需主动处理伏笔。" }

        val scenes = request.chapter.scenePlan.sortedBy { it.order }.joinToString("\n") {
            val clock = if (it.storyDay > 0 || it.timeOfDay.isNotBlank()) {
                "故事第${it.storyDay.takeIf { day -> day > 0 } ?: 0}天·${it.timeOfDay.ifBlank { "待锁定" }}; 距上一场=${it.elapsedFromPrevious.ifBlank { "连续" }}; ${if (it.isFlashback) "闪回" else "主时间线"}; "
            } else "时间沿用时间轴锁; "
            "- 场景${it.order}: $clock 视角=${it.viewpoint}; 地点=${it.location}; 目的=${it.purpose}; 冲突=${it.conflict}; 场景落点=${it.outcome}"
        }

        val recentMemory = snapshot.recentSummaries.takeLast(12).joinToString("\n") { "- $it" }
        val chronology = chronologyGuard.promptText(snapshot, request.chapter.scenePlan)

        return PromptBundle(
            system = """
                你现在只担任长篇小说的“正文作者”。你的唯一输出是可以直接发布的小说正文，不做摘要、不做数据库维护、不解释写作过程。

                写作硬规则：
                1. 只完成【本章任务】。全书方向和本卷方向只是背景，不得把未来章节的谜底、转折、势力、能力、证据或终局提前搬进本章。
                2. 锁定设定是事实，但“作者知道”不等于“读者现在应该知道”。除非本章场景确实需要，否则不要解释世界观，不要集中罗列规则。
                3. 把信息写成场景：人物行动、选择、对话、观察、记忆和后果。禁止把材料整理成报告、百科、案件清单、会议纪要或调查总结。
                4. 禁止正文中出现“他目前掌握的信息”“本章总结”“状态更新”“已确认事实”“伏笔”“章纲”“场景计划”“本章约X字”等创作后台措辞。
                5. 不要为了证明设定完整而一次性枚举大量姓名、职业、死法、规则或证据。若多个个案只承担同一叙事功能，选择最有戏剧价值的少数例子，其余用自然概括。
                6. 人物首先是人，不是检索器。每个重要调查/推理动作都要有欲望、情绪、犹豫、关系或代价支撑；不要连续“搜索—核对—记录—分类”而没有人物戏。
                7. 悬疑靠信息差和递进建立。每个异常最多推进一层认知；尚未到回收期的伏笔只允许保持存在感，不解释答案。
                8. 现实场景必须可信。普通人不能无理由获取保密档案、进入受限场所、让工作人员违反常识或制度来服务剧情；如必须做到，正文中先建立可信渠道和代价。
                9. 恐怖/异常优先使用安静而具体的现实错位，不要机械叠加水雾、敲门、血字、怪声等模板惊吓。异常越少越要精准。
                10. 严格遵守人物已知信息、地点、时间轴和场景耗时；不得瞬移、无因跳时、无因改变关系/能力/性格。
                11. 本章结尾必须由本章已有因果自然推出一个新的问题、选择或威胁，形成钩子，但不能靠提前揭底制造刺激。
                12. 文风服从作品模板。避免AI腔：少用排比式解释、总结句、反复加粗式强调、同义复述和“不是A，是B”连续句型。
                13. 目标字数是节奏参考，不为凑字重复信息，也不要用清单填充篇幅。
                14. 只输出小说正文。不要 Markdown 标题、不要前言、不要后记、不要说明。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                核心命题：${snapshot.novel.premise}
                主题：${snapshot.novel.theme}

                【文风】
                $styleRules

                【锁定设定｜只能按本章需要显露】
                $hardRules

                【长期方向｜只用于不跑偏，禁止提前兑现】
                $direction

                【本章任务】
                $chapterTask

                【本章场景计划】
                ${scenes.ifBlank { "尚未拆场景；严格围绕本章唯一主目标组织 2-5 个递进场景。" }}

                【人物当前状态】
                $characters

                【时间轴锁】
                $chronology

                【最近已发生剧情】
                ${recentMemory.ifBlank { "暂无前章正文；把本章当作故事真实起点来写。" }}

                【最近时间线】
                ${timeline.ifBlank { "暂无。" }}

                【伏笔可见范围】
                $foreshadowing

                【用户补充要求】
                ${request.extraInstruction.ifBlank { "无。" }}

                正文目标：约${request.targetWords}字。只写正文。
            """.trimIndent(),
            jsonMode = false,
        )
    }

    /** Reviewer is allowed to see later chapter beats solely to detect premature reveals. */
    fun buildQualityReview(request: GenerationRequest, prose: String): PromptBundle {
        val snapshot = request.snapshot
        val current = request.chapter.chapterNumber.coerceAtLeast(1)
        val future = snapshot.outline
            .filter { it.level == OutlineLevel.CHAPTER && it.order > current }
            .sortedBy { it.order }
            .take(10)
            .joinToString("\n") {
                "- 第${it.order}章 ${it.title}｜目标=${it.objective}｜转折=${it.turningPoint}｜必须=${it.mustInclude.joinToString("、")}"
            }
            .ifBlank { "- 暂无后续章纲。" }
        val currentNode = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.CHAPTER }
        return PromptBundle(
            system = """
                你是严苛的中文长篇小说章节主编。你不续写，只判断这版正文是否可以交付。
                输出 GeneratedChapter JSON，不要 Markdown：
                - title 只能是 PASS 或 REWRITE。
                - content：若 REWRITE，列出最关键的重写指令；若 PASS 写“通过”。
                - summary：用一句话说明判断理由。
                - stateChanges=[]；touchedForeshadowingIds=[]。

                任何一项成立都必须 REWRITE：
                1. 把后续章节才该发生/揭示的内容提前写进本章，抢了后面的戏。
                2. 大量罗列姓名、职业、案件、规则、设定、证据，像报告/说明书而不是小说。
                3. 正文出现“目前掌握的信息/本章总结/状态更新/伏笔/章纲/场景计划/本章约X字”等后台总结。
                4. 主角连续执行检索、核对、记录等功能动作，却缺乏明确欲望、情绪、关系或代价。
                5. 现实逻辑明显为了推进剧情开绿灯，例如普通人无铺垫取得保密材料或进入受限地点。
                6. 异常/恐怖元素机械堆叠，缺乏单一精准的核心异常。
                7. 设定通过作者解释而不是场景呈现；同一信息重复解释。
                8. 未完成本章唯一目标，或结尾钩子与本章因果无关。
                9. 人物知道了不该知道的信息、时间地点不连续、与锁定设定冲突。
                10. 正文不是纯小说文本，包含创作说明或面向用户的解释。
            """.trimIndent(),
            user = """
                【本章】第${request.chapter.chapterNumber}章 ${request.chapter.title}
                唯一目标：${request.chapter.objective}
                本章冲突：${currentNode?.conflict.orEmpty()}
                本章转折：${currentNode?.turningPoint.orEmpty()}
                本章必须：${currentNode?.mustInclude.orEmpty().joinToString("、")}
                本章禁止：${currentNode?.forbidden.orEmpty().joinToString("、")}

                【后续章纲｜只用于检查本章是否提前抢戏】
                $future

                【待审正文】
                $prose
            """.trimIndent(),
        )
    }

    fun buildRewrite(request: GenerationRequest, prose: String, instructions: String): PromptBundle {
        val base = buildProse(request)
        return base.copy(
            system = base.system + "\n\n这是第二稿。必须根据主编意见从头重写整章，不要在旧稿后追加修补，不要解释修改过程。",
            user = base.user + "\n\n【上一稿】\n$prose\n\n【主编退回意见】\n$instructions\n\n请从头给出完整新正文。",
            jsonMode = false,
        )
    }

    /** Metadata is extracted after prose is frozen, so it can never leak into the visible chapter. */
    fun buildMetadata(request: GenerationRequest, prose: String): PromptBundle {
        val snapshot = request.snapshot
        val chronology = chronologyGuard.promptText(snapshot, request.chapter.scenePlan)
        return PromptBundle(
            system = """
                你是“琅嬛”的章节事实提取器。正文已经写完且不可改动。你的任务只是从正文中提取结构化记忆，绝不能续写、润色或新增事实。
                输出必须是 GeneratedChapter JSON：
                - title=当前章节标题；
                - content=""（必须为空，正文由 App 单独保存）；
                - summary=120-260字高信息密度事实摘要，只写正文真实发生的事，并在末尾写“本章结束时=故事第N天·时段”；
                - stateChanges=正文明确造成的状态变化。每项含 subject、field、before、after、evidence；没有变化就空数组；
                - touchedForeshadowingIds=正文确实触及的既有伏笔 id；没有就空数组。
                禁止把推测当事实，禁止根据大纲补正文没有发生的事件。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                章节：第${request.chapter.chapterNumber}章 ${request.chapter.title}

                【人物写前状态】
                ${snapshot.characters.joinToString("\n") { "- ${it.name}: 地点=${it.location}; 身体=${it.physicalState}; 情绪=${it.emotionalState}; 目标=${it.goal}; 已知秘密=${it.knownSecrets.joinToString("、")}" }}

                【时间轴锁】
                $chronology

                【既有伏笔】
                ${snapshot.relevantForeshadowing.joinToString("\n") { "- id=${it.id}; ${it.title}; ${it.detail}; 状态=${it.status}" }}

                【已冻结正文】
                $prose
            """.trimIndent(),
        )
    }
}
