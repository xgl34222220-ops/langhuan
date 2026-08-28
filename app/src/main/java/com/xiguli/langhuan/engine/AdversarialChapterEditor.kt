package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

/**
 * 单次模型调用内的四席位对抗审稿。
 *
 * 不把四个角色拆成四次 API 请求：同一个主编请求中强制 Story Architect、
 * Character Editor、Narrative Editor、Consistency Checker 各自给出独立结论，
 * 任一席位发现硬问题都必须整体 REWRITE。
 */
class AdversarialChapterEditor {
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
        val contract = ChapterContractGuard.renderContract(snapshot, request.chapter)
        val knowledge = ChapterContractGuard.renderKnowledge(snapshot, current)

        return PromptBundle(
            system = """
                你是“琅嬛”的对抗式章节主编委员会。这是第 $round 轮审核。
                一次审核中必须让四个互相独立的编辑席位分别找问题，禁止互相替作者圆场：

                1. Story Architect / 结构编辑：检查章节合同、因果推进、唯一目标、章末钩子、是否提前消费后续章纲。
                2. Character Editor / 人物编辑：检查人物是否按当前欲望、关系、情绪和已知信息行动；是否无因变性、越权获知、工具人化。
                3. Narrative Editor / 文字编辑：检查是否像真正小说而不是报告/设定说明；节奏、场景化、信息密度、重复、AI腔是否合格。
                4. Consistency Checker / 连续性编辑：检查 Canon、信息边界、时间、地点、能力、物品、伏笔保护期和人物状态是否冲突。

                输出 GeneratedChapter JSON，不要 Markdown：
                - title 只能是 PASS 或 REWRITE。
                - content 必须有四行，分别以【结构】【人物】【文字】【连续性】开头；每行写“通过”或具体问题+可执行修法。
                - summary 用一句话给出委员会最终结论。
                - stateChanges=[]；touchedForeshadowingIds=[]。

                判定规则：
                - 四席全部通过，title 才能是 PASS。
                - 任一席位发现会改变剧情事实、人物认知、章节合同、时间线、后续揭晓顺序的错误，必须 REWRITE。
                - 不因为“整体可读”“瑕不掩瑜”放行硬冲突。
                - 不续写正文，不新增设定，不把作者真相泄露给读者。
                - 第 2 轮仍存在第一轮指出的核心问题，必须继续 REWRITE；App 会阻止该稿进入正式版本。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                审核章节：第${request.chapter.chapterNumber}章 ${request.chapter.title}
                唯一目标：${request.chapter.objective}
                本章冲突：${currentNode?.conflict.orEmpty()}
                本章转折：${currentNode?.turningPoint.orEmpty()}

                【章节合同】
                $contract

                【信息边界账本】
                $knowledge

                【后续章纲｜仅用于查提前抢戏】
                $future

                【待审正文】
                $prose
            """.trimIndent(),
        )
    }

    fun requestsRewrite(review: GeneratedChapter?): Boolean =
        review?.title?.trim()?.equals("REWRITE", ignoreCase = true) == true

    fun instructions(review: GeneratedChapter?, deterministicProblems: List<String>): String = buildString {
        review?.content?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("通过", ignoreCase = true)
        }?.let { appendLine(it) }
        deterministicProblems.forEach { appendLine("【确定性检查】$it") }
    }.trim().ifBlank {
        "从头重写本章：只完成章节合同允许的内容，用人物与场景推进，不写报告，不提前泄露后续章纲。"
    }
}
