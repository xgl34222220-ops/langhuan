package com.xiguli.langhuan.engine

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
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(chronologyGuard),
    private val eraTechnologyGuard: EraTechnologyGuard = EraTechnologyGuard(),
) {
    /**
     * Pure prose prompt. Context Builder 2.0 keeps execution/canon/state/style/history isolated,
     * so RAG history cannot crowd out or silently override the current chapter contract.
     */
    fun buildProse(
        request: GenerationRequest,
        retrievedContext: List<RetrievedContextItem> = emptyList(),
    ): PromptBundle {
        val snapshot = request.snapshot
        val context = contextBuilder.build(request, retrievedContext)
        return PromptBundle(
            system = """
                你现在只担任长篇小说的“正文作者”。你的唯一输出是可以直接发布的小说正文，不做摘要、不做数据库维护、不解释写作过程。

                上下文优先级永远是 S > A > B > C > D：
                S = 本章执行合同；A = Canon、信息边界、主时间钟；B = 当前人物/时间线/伏笔状态；C = 文风；D = 历史召回。
                D 层即使与本章高度相似，也绝不能覆盖 S/A 层。召回材料只证明“过去发生过什么”，不是允许本章提前兑现什么。

                写作硬规则：
                1. 【S·本章执行合同】是最高优先级。必须发生、绝不能发生、允许揭露、必须保密、人物进出状态和章末钩子都不得被文风、RAG、模型常识或临时发挥覆盖。
                2. 只完成本章任务。全书方向和本卷方向只是背景，不得把未来章节的谜底、转折、势力、能力、证据或终局提前搬进本章。
                3. 【A·Canon 与硬边界】区分作者真相、人物认知和读者认知。人物只能基于已知信息行动；受保护秘密不得被正文直接确认。
                4. 锁定设定是事实，但“作者知道”不等于“读者现在应该知道”。除非章节合同明确授权，否则不要解释世界观，不要集中罗列规则。
                5. 把信息写成场景：人物行动、选择、对话、观察、记忆和后果。禁止把材料整理成报告、百科、案件清单、会议纪要或调查总结。
                6. 禁止正文中出现“他目前掌握的信息”“本章总结”“状态更新”“已确认事实”“伏笔”“章纲”“场景计划”“本章约X字”等创作后台措辞。
                7. 不要为了证明设定完整而一次性枚举大量姓名、职业、死法、规则或证据。若多个个案只承担同一叙事功能，选择最有戏剧价值的少数例子，其余自然概括。
                8. 人物首先是人，不是检索器。每个重要调查/推理动作都要有欲望、情绪、犹豫、关系或代价支撑；不要连续“搜索—核对—记录—分类”而没有人物戏。
                9. 悬疑靠信息差和递进建立。每个异常最多推进一层认知；尚未到回收期的伏笔只允许保持存在感，不解释答案。
                10. 现实场景必须可信。普通人不能无理由获取保密档案、进入受限场所、让工作人员违反常识或制度来服务剧情；如必须做到，正文中先建立可信渠道和代价。
                11. 恐怖/异常优先使用安静而具体的现实错位，不要机械叠加水雾、敲门、血字、怪声等模板惊吓。异常越少越要精准。
                12. 严格遵守人物已知信息、地点、时间轴和场景耗时；不得瞬移、无因跳时、无因改变关系/能力/性格。
                13. 本章结尾必须由本章已有因果自然推出一个新的问题、选择或威胁，形成钩子，但不能靠提前揭底制造刺激。
                14. 文风服从 C 层，但任何写法偏好都不能修改 S/A/B 的事实和边界。避免AI腔：少用排比式解释、总结句、同义复述和连续“不是A，是B”句型。
                15. D 层只允许按需取材。不要因为召回了某段旧剧情就重复讲述，更不要把 RAG 中的旧状态当成当前状态。
                16. 目标字数是节奏参考，不为凑字重复信息，也不要用清单填充篇幅。
                17. 只输出小说正文。不要 Markdown 标题、不要前言、不要后记、不要说明。
                18. 严格执行【A·时代与技术锁】。年代、设备能力、社会普及度和人物使用理由必须同时成立；不得把手机界面词套给座机，也不得在现代场景无理由用旧式设备制造悬疑。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                核心命题：${snapshot.novel.premise}
                主题：${snapshot.novel.theme}

                【S·本章执行合同】
                ${context.execution}

                【A·Canon 与硬边界】
                ${context.canon}

                【A·时代与技术锁】
                ${eraTechnologyGuard.promptText(snapshot)}

                【B·当前剧情状态】
                ${context.state}

                【C·文风与写法】
                ${context.style}

                【D·历史召回｜只作历史证据】
                ${context.history}

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
        val contractText = ChapterContractGuard.renderContract(snapshot, request.chapter)
        val knowledgeText = ChapterContractGuard.renderKnowledge(snapshot, current)
        return PromptBundle(
            system = """
                你是严苛的中文长篇小说章节主编。你的任务是主动寻找不能通过的理由，不是替作者证明正文“整体不错”。你不续写，只判断这版正文是否可以交付。
                输出 GeneratedChapter JSON，不要 Markdown：
                - title 只能是 PASS 或 REWRITE。
                - content：若 REWRITE，列出最关键的重写指令；若 PASS 写“通过”。
                - summary：用一句话说明判断理由。
                - stateChanges=[]；touchedForeshadowingIds=[]。

                任何一项成立都必须 REWRITE：
                1. 违反章节合同的 must happen / must not happen / character state / reveal / secret / hook 约束。
                2. 把后续章节才该发生/揭示的内容提前写进本章，抢了后面的戏。
                3. 人物知道了信息边界账本中明确不应知道的事实，或把保护期秘密直接确认给读者。
                4. 大量罗列姓名、职业、案件、规则、设定、证据，像报告/说明书而不是小说。
                5. 正文出现“目前掌握的信息/本章总结/状态更新/伏笔/章纲/场景计划/本章约X字”等后台总结。
                6. 主角连续执行检索、核对、记录等功能动作，却缺乏明确欲望、情绪、关系或代价。
                7. 现实逻辑明显为了推进剧情开绿灯，例如普通人无铺垫取得保密材料或进入受限地点。
                8. 异常/恐怖元素机械堆叠，缺乏单一精准的核心异常。
                9. 设定通过作者解释而不是场景呈现；同一信息重复解释。
                10. 未完成本章唯一目标，或结尾钩子与本章因果无关。
                11. 时间地点不连续、与锁定设定冲突，或人物出场/离场状态无因跳变。
                12. 正文不是纯小说文本，包含创作说明或面向用户的解释。
                13. 年代、技术存在时间、设备能力、社会普及度或使用场景不匹配；尤其是现代私人场景无理由使用座机，或把手机 UI/通知方式写给座机。
            """.trimIndent(),
            user = """
                【本章】第${request.chapter.chapterNumber}章 ${request.chapter.title}
                唯一目标：${request.chapter.objective}
                本章冲突：${currentNode?.conflict.orEmpty()}
                本章转折：${currentNode?.turningPoint.orEmpty()}
                本章必须：${currentNode?.mustInclude.orEmpty().joinToString("、")}
                本章禁止：${currentNode?.forbidden.orEmpty().joinToString("、")}

                【章节合同】
                $contractText

                【信息边界账本】
                $knowledgeText

                【时代与技术锁】
                ${eraTechnologyGuard.promptText(snapshot)}

                【后续章纲｜只用于检查本章是否提前抢戏】
                $future

                【待审正文】
                $prose
            """.trimIndent(),
        )
    }

    fun buildRewrite(
        request: GenerationRequest,
        prose: String,
        instructions: String,
        retrievedContext: List<RetrievedContextItem> = emptyList(),
    ): PromptBundle {
        val base = buildProse(request, retrievedContext)
        return base.copy(
            system = base.system + "\n\n这是第二稿。必须根据主编意见从头重写整章，不要在旧稿后追加修补，不要解释修改过程。章节合同与信息边界仍为最高优先级。",
            user = base.user + "\n\n【上一稿】\n$prose\n\n【主编退回意见】\n$instructions\n\n请从头给出完整新正文。",
            jsonMode = false,
        )
    }

    /** Metadata is extracted after prose is frozen, so it can never leak into the visible chapter. */
    fun buildMetadata(request: GenerationRequest, prose: String): PromptBundle {
        val snapshot = request.snapshot
        val chronology = chronologyGuard.promptText(snapshot, request.chapter.scenePlan)
        val knowledgeText = ChapterContractGuard.renderKnowledge(snapshot, request.chapter.chapterNumber)
        return PromptBundle(
            system = """
                你是“琅嬛”的章节事实提取器。正文已经写完且不可改动。你的任务只是从正文中提取结构化记忆，绝不能续写、润色或新增事实。
                输出必须是 GeneratedChapter JSON：
                - title=当前章节标题；
                - content=""（必须为空，正文由 App 单独保存）；
                - summary=120-260字高信息密度事实摘要，只写正文真实发生的事，并在末尾写“本章结束时=故事第N天·时段”；
                - stateChanges=正文明确造成的状态变化。每项含 subject、field、before、after、evidence；没有变化就空数组；
                - 若人物在正文中明确获得了新的秘密/事实，必须额外给一项 field=knownSecrets，subject=人物名，before=原认知，after=新获知的秘密标题或事实，evidence=正文证据；
                - touchedForeshadowingIds=正文确实触及的既有伏笔 id；没有就空数组。
                禁止把推测当事实，禁止根据大纲或作者真相补正文没有发生的事件。信息边界账本中的 truth 只能用于判断，不能反向写进摘要。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                章节：第${request.chapter.chapterNumber}章 ${request.chapter.title}

                【人物写前状态】
                ${snapshot.characters.joinToString("\n") { "- ${it.name}: 地点=${it.location}; 身体=${it.physicalState}; 情绪=${it.emotionalState}; 目标=${it.goal}; 已知秘密=${it.knownSecrets.joinToString("、")}" }}

                【信息边界账本｜只做事实核对】
                $knowledgeText

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
