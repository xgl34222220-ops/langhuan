package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

/**
 * 四席位章节主编。
 *
 * 普通编辑建议仍不能单独制造 BLOCKING；但如果主编能在正文中指出明确的逻辑/动机/因果/连续性断裂，
 * 允许触发一次整章重写。二审若仍存在同类断裂，再由 Pipeline 阻止保存。
 */
class AdversarialChapterEditor(
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
    private val eraTechnologyGuard: EraTechnologyGuard = EraTechnologyGuard(),
) {
    fun buildReview(request: GenerationRequest, prose: String, round: Int): PromptBundle {
        val snapshot = request.snapshot
        val current = request.chapter.chapterNumber.coerceAtLeast(1)
        val currentNode = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.CHAPTER }
        val future = snapshot.outline
            .filter { it.level == OutlineLevel.CHAPTER && it.order > current }
            .sortedBy { it.order }
            .take(10)
            .joinToString("\n") {
                "- 第${it.order}章 ${it.title}｜目标=${it.objective}｜转折=${it.turningPoint}｜必须=${it.mustInclude.joinToString("、")}"
            }
            .ifBlank { "- 暂无后续章纲。" }
        val context = contextBuilder.build(request)

        return PromptBundle(
            system = """
                你是琅嬛的对抗式章节主编委员会，第 $round 轮审核。
                分别从【结构】【人物】【文字】【连续性】四个角度检查，不续写正文。

                先区分两类问题：
                - 【建议】：节奏、表达、线索密度、场景组织、可读性等优化项。只有建议时 title 必须 PASS。
                - 【可重写逻辑问题】：正文中能直接定位、并会让读者觉得“这一步为什么会发生”的明确断裂。只有这类问题才允许 title=REWRITE。

                可重写问题只允许使用以下标记：
                - 【逻辑断裂】：关键行动/结论缺少必要前提或依据，正文没有建立它为什么成立。
                - 【动机断裂】：重要人物的关键行为与当前目标、性格、关系或已知信息明显脱节，正文没有给推动因素。
                - 【因果断裂】：上一场的行动/冲突无法推出结果，或下一场完全没有承接上一场产生的新信息、代价、选择或威胁。
                - 【连续性断裂】：地点、时间、人物状态、能力或关系发生明显跳变，正文没有过渡。
                - 【目标未完成】：正文写了不少内容，但本章唯一目标、必须发生项或章末结果实际没有发生。
                - 【叙事断裂】：场景之间缺少可追踪的时间、地点、行动或信息承接，读者无法判断事情如何发展到下一步。
                - 【场景散乱】：多个场景各说各话，没有围绕本章唯一目标形成递进，删除任一场也不影响结果。
                - 【人物失真】：关键人物的说话、选择或反应与其当前目标、关系、认知和稳定行为模式直接矛盾。
                - 【信息倾倒】：大量设定、名单、规则或推理结论没有通过当前场景中的行动与后果发生，正文实质是资料说明。
                - 【时代技术冲突】：故事年代、技术存在时间、设备能力、社会普及度或人物使用理由互相冲突。例如现代私人场景无理由依赖座机，或座机出现锁屏、头像、App/微信通知等手机行为。

                标记格式必须是：
                `【逻辑断裂】正文证据=...｜缺失前提=...｜最小修法=...`
                其他三个标记同理。
                必须引用待审正文里的具体事件作为“正文证据”；不能只说“感觉不自然”。证据不足一律降级为【建议】。

                另外，正文与 S/A/B 权威上下文有可逐字核对的直接矛盾时，可以继续写【硬冲突】；
                格式必须是 `【硬冲突】正文证据=...｜权威依据=...｜最小修法=...`。正文证据和权威依据缺少任一项都只能降级为【建议】。

                输出 GeneratedChapter JSON：
                - title 只能 PASS 或 REWRITE；只有至少存在一个上述可重写问题标记时才能 REWRITE。
                - content 四行，分别以【结构】【人物】【文字】【连续性】开头；每行可包含一个或多个标记/建议。
                - summary 一句话总结；stateChanges=[]；touchedForeshadowingIds=[]。
                - 不新增设定，不把后续章纲当当前 Canon，不因文风偏好要求无关整章重写。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                第${request.chapter.chapterNumber}章：${request.chapter.title}
                唯一目标：${request.chapter.objective}
                当前冲突：${currentNode?.conflict.orEmpty()}
                当前转折：${currentNode?.turningPoint.orEmpty()}

                【S·章节执行合同 / 写前逻辑骨架｜权威】
                ${context.execution}

                【A·Canon 与硬边界｜权威】
                ${context.canon}

                【A·时代与技术锁｜权威】
                ${eraTechnologyGuard.promptText(snapshot)}

                【B·当前剧情状态｜权威】
                ${context.state}

                【后续章纲｜仅用于检查明确提前抢戏】
                $future

                【待审正文】
                $prose
            """.trimIndent(),
        )
    }

    /**
     * 只有主编明确给出“可重写逻辑问题”并返回 REWRITE 时，才触发一次重写。
     * 普通建议、单纯的 AI 【硬冲突】声称、文风偏好都不能触发。
     */
    fun requestsRewrite(review: GeneratedChapter?): Boolean {
        if (!review?.title.orEmpty().trim().equals("REWRITE", ignoreCase = true)) return false
        val body = review?.content.orEmpty()
        val verifiedHardConflict = HARD_CONFLICT_MARKER in body && "正文证据=" in body && "权威依据=" in body
        return verifiedHardConflict || LOGIC_REWRITE_MARKERS.any(body::contains)
    }

    fun instructions(review: GeneratedChapter?, deterministicProblems: List<String>): String = buildString {
        deterministicProblems.forEach { appendLine("【确定性检查】$it") }
        review?.content?.trim()?.takeIf { it.isNotBlank() }?.let { appendLine("【主编建议】$it") }
    }.trim().ifBlank {
        "只做最小必要修改；保持既有剧情、人物动机、场景顺序和已经成立的正文，禁止无关整章重写。"
    }

    companion object {
        const val HARD_CONFLICT_MARKER = "【硬冲突】"
        val LOGIC_REWRITE_MARKERS = listOf(
            "【逻辑断裂】",
            "【动机断裂】",
            "【因果断裂】",
            "【连续性断裂】",
            "【目标未完成】",
            "【叙事断裂】",
            "【场景散乱】",
            "【人物失真】",
            "【信息倾倒】",
            "【时代技术冲突】",
        )
    }
}
