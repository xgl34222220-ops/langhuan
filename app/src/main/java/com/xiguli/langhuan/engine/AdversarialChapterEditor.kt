package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

/**
 * 四席位章节主编。AI 主编只负责解释性审稿和修改建议，不能单独制造 BLOCKING。
 * 真正的硬阻断只由本地 ConsistencyGate / ChronologyGuard / Canon 边界决定。
 */
class AdversarialChapterEditor(
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
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
                你是琅嬛的章节主编委员会，第 $round 轮审核。
                分别从【结构】【人物】【文字】【连续性】四个角度给意见。

                必须区分：
                - 【建议】：节奏、表达、线索密度、场景组织、可读性等编辑意见。
                - 【硬冲突】：只有正文与提供的 S/A/B 权威上下文存在可以逐字核对的直接矛盾时才允许标记。

                注意：你不是最终 Gate。即使你输出 REWRITE，App 仍会由本地确定性规则决定是否真的重写或阻断。
                因此不要为了“更好看”夸大成硬冲突；证据不足一律降级为【建议】。

                输出 GeneratedChapter JSON：
                - title 只能 PASS 或 REWRITE；
                - content 四行，分别以【结构】【人物】【文字】【连续性】开头；
                - 硬冲突格式：`【硬冲突】锚点=...｜正文证据=...｜最小修法=...`；
                - summary 一句话总结；stateChanges=[]；touchedForeshadowingIds=[]。
                不续写正文，不新增设定，不把后续章纲当当前 Canon。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                第${request.chapter.chapterNumber}章：${request.chapter.title}
                唯一目标：${request.chapter.objective}
                当前冲突：${currentNode?.conflict.orEmpty()}
                当前转折：${currentNode?.turningPoint.orEmpty()}

                【S·章节执行合同｜权威】
                ${context.execution}

                【A·Canon 与硬边界｜权威】
                ${context.canon}

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
     * AI 主编从本版本开始不再直接触发重写。
     * 本地确定性规则如果发现真正硬问题，GenerationPipeline 仍会自动修订或阻断。
     * 这样 CHAPTER_CONTRACT_MISSING / MISSING_REQUIRED_ELEMENT 这类 WARNING 不会被 AI 升级成 EDITOR_REVIEW_FAILED。
     */
    fun requestsRewrite(review: GeneratedChapter?): Boolean = false

    fun instructions(review: GeneratedChapter?, deterministicProblems: List<String>): String = buildString {
        deterministicProblems.forEach { appendLine("【确定性检查】$it") }
        review?.content?.trim()?.takeIf { it.isNotBlank() }?.let { appendLine("【主编建议】$it") }
    }.trim().ifBlank {
        "只做最小必要修改；保持既有剧情、人物动机、场景顺序和已经成立的正文，禁止无关整章重写。"
    }

    companion object {
        const val HARD_CONFLICT_MARKER = "【硬冲突】"
    }
}
