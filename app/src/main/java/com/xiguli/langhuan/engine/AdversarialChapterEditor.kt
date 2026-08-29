package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

/**
 * 单次模型调用内的四席位对抗审稿。
 *
 * AI 主编只能补充“解释性审稿”，不能凌驾于本地 Consistency Gate 之上。
 * 只有能锚定到明确章节合同 / Canon / 信息边界 / 时间线的【硬冲突】才允许触发 REWRITE；
 * 节奏、线索密度、异常数量、审美取舍等普通编辑意见只能作为【建议】，不能卡死正文。
 */
class AdversarialChapterEditor(
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
) {
    fun buildReview(
        request: GenerationRequest,
        prose: String,
        round: Int,
    ): PromptBundle {
        val snapshot = request.snapshot
        val current = request.chapter.chapterNumber.coerceAtLeast(1)
        val currentNode = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.CHAPTER }
        val future = snapshot.outline
            .filter { it.level == OutlineLevel.CHAPTER && it.order > current }
            .sortedBy { it.order }
            .take(12)
            .joinToString("\n") {
                "- 第${it.order}章 ${it.title}｜目标=${it.objective}｜转折=${it.turningPoint}｜必须=${it.mustInclude.joinToString("、")}"
            }
            .ifBlank { "- 暂无后续章纲。" }
        val context = contextBuilder.build(request)

        return PromptBundle(
            system = """
                你是“琅嬛”的对抗式章节主编委员会。这是第 $round 轮审核。
                一次审核中让四个互相独立的编辑席位分别检查：

                1. Story Architect / 结构编辑：章节合同、因果推进、唯一目标、章末钩子、是否明确提前消费后续章纲。
                2. Character Editor / 人物编辑：人物当前欲望、关系、情绪、已知信息；是否无因变性或越权获知。
                3. Narrative Editor / 文字编辑：是否像小说而不是报告；节奏、场景化、重复、AI腔。
                4. Consistency Checker / 连续性编辑：给定 Canon、信息边界、时间、地点、能力、物品、伏笔保护期和人物状态。

                你必须严格区分“硬冲突”和“编辑建议”。

                【硬冲突】只允许用于以下情况，而且必须能从我提供的 S/A/B 权威上下文中逐字指出锚点：
                - 明确违反章节合同 mustHappen / mustNotHappen / 人物进出状态 / reveal / secret / hook；
                - 正文明确写出了后续章纲才允许发生或揭晓的具体内容；
                - 人物明确知道了信息边界中禁止其知道的事实；
                - 正文与给出的锁定 Canon、时间线、地点、能力、物品或人物当前状态直接矛盾；
                - 正文出现确定性的创作后台文本、严重报告体，达到无法作为小说正文交付的程度。

                以下情况绝不能单独判为硬冲突，只能写【建议】：
                - 你觉得异常、线索、证据“太多”“太复杂”“最好删一条”；
                - 你更喜欢另一种节奏、悬念、人物反应或叙事取舍；
                - 正文第一次出现了一个新异常/新证据，但 S/A/B 并没有禁止它；
                - 你根据类型套路、常识或自己的推测，猜测某内容“应该不是 Canon”；
                - 你认为某事实可能会影响后续，但后续章纲并没有明确把该事实锁定到未来章节；
                - 只是“可以更自然”“可以更克制”“可以更集中”等质量建议。

                证据不足时必须降级为【建议】，不得用猜测制造冲突。后续章纲只用于检查“明确提前抢戏”，不能反过来当成当前 Canon，也不能要求正文只保留一种异常。

                输出 GeneratedChapter JSON，不要 Markdown：
                - title 只能是 PASS 或 REWRITE。
                - 只有至少一席存在合格的【硬冲突】时 title 才能是 REWRITE；只有建议时必须 PASS。
                - content 必须有四行，分别以【结构】【人物】【文字】【连续性】开头。
                - 通过：`【结构】通过`
                - 普通意见：`【结构】【建议】具体建议`
                - 硬冲突：`【结构】【硬冲突】锚点=引用 S/A/B 中的明确约束｜正文证据=引用正文事实｜修法=最小修改方案`
                - summary 用一句话给出最终结论。
                - stateChanges=[]；touchedForeshadowingIds=[]。

                第 2 轮也遵守同一标准：只有修订稿仍存在可验证【硬冲突】才能继续 REWRITE。
                不续写正文，不新增设定，不把作者真相泄露给读者。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                审核章节：第${request.chapter.chapterNumber}章 ${request.chapter.title}
                唯一目标：${request.chapter.objective}
                本章冲突：${currentNode?.conflict.orEmpty()}
                本章转折：${currentNode?.turningPoint.orEmpty()}

                【S·本章执行合同｜权威】
                ${context.execution}

                【A·Canon 与硬边界｜权威】
                ${context.canon}

                【B·当前剧情状态｜权威】
                ${context.state}

                【后续章纲｜只用于检查明确提前抢戏，不是当前 Canon】
                $future

                【待审正文】
                $prose
            """.trimIndent(),
        )
    }

    /**
     * 模型说 REWRITE 还不够：必须同时按协议给出至少一个【硬冲突】。
     * 这样普通审美/节奏建议不会把章节变成 BLOCKING；真正硬规则仍由本地 Gate 再兜底。
     */
    fun requestsRewrite(review: GeneratedChapter?): Boolean {
        if (review?.title?.trim()?.equals("REWRITE", ignoreCase = true) != true) return false
        return review.content.contains(HARD_CONFLICT_MARKER)
    }

    fun instructions(review: GeneratedChapter?, deterministicProblems: List<String>): String = buildString {
        review?.content?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("通过", ignoreCase = true)
        }?.let { appendLine(it) }
        deterministicProblems.forEach { appendLine("【确定性检查】$it") }
    }.trim().ifBlank {
        "从头重写本章：只修复明确硬问题，保持已经成立的剧情与人物动机；只完成章节合同允许的内容，不写报告，不提前泄露后续章纲。"
    }

    companion object {
        const val HARD_CONFLICT_MARKER = "【硬冲突】"
    }
}
