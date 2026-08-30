package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest

/**
 * 写正文前的因果骨架。
 *
 * 它只属于本次生成 Run：不进入 Canon，不写入 RAG，也不改变章纲。目标是先把“为什么发生”
 * 锁清楚，再让正文作者负责“怎么写出来”，避免正文模型拿到大量设定后直接自由联想。
 */
class ChapterLogicPlanner(
    private val contextBuilder: GenerationContextBuilder = GenerationContextBuilder(),
) {
    fun buildPrompt(
        request: GenerationRequest,
        retrievedContext: List<RetrievedContextItem> = emptyList(),
    ): PromptBundle {
        val context = contextBuilder.build(request, retrievedContext)
        return PromptBundle(
            system = """
                你是“琅嬛”的章节逻辑导演。你不写小说正文，只在正文生成前锁定本章的因果骨架。
                输出必须是 GeneratedChapter JSON，不要 Markdown。

                你的第一原则不是“丰富”，而是“每一步都有前提、动机、依据和后果”。
                S > A > B > D：章节合同、Canon、当前状态优先于历史召回。不得新增未提供的关键设定来补逻辑洞。

                输出约定：
                - title 固定为 logic-plan。
                - content 用 220-700 字写清六项：
                  起点状态；人物当前欲望/动机；本章核心因果链；场景之间的桥；章末结果为何由前文自然推出；本章禁止跳过的逻辑步骤。
                - summary 用一句话写“本章因果主轴”。
                - stateChanges = 3-8 个因果节点。每项：
                  subject=执行行动的人物；
                  field=本节点的触发条件/当前问题；
                  before=人物在行动前已经拥有的依据、欲望或压力；
                  after=人物因此采取的具体行动，以及该行动直接造成的结果；
                  evidence=“依据来源||付出的代价/风险||该结果如何逼出下一节点”。
                - touchedForeshadowingIds=[]。

                逻辑硬规则：
                1. 每个关键行动都必须回答“人物为什么现在做这件事”，不能因为作者需要就突然行动。
                2. 每个发现/判断都必须有来源：亲眼所见、已知事实、合理调查结果、他人明确告知等；禁止无来源知道答案。
                3. 每个场景结果必须由该场景里的行动/冲突推出；下一场必须承接上一场留下的新信息、代价、选择或威胁。
                4. 如果人物要换地点、找人、调查、等待、战斗、合作或翻脸，必须有触发原因和可见过渡。
                5. 人物行为不得无铺垫违背当前目标、性格、关系和已知信息；如果必须反常，要先给足推动因素。
                6. 不得用巧合、突然出现的万能人物、临时新增能力、临时新增证据直接解决本章核心冲突。
                7. 场景计划只是执行骨架；若其中本身存在明显因果断口，在不改变章节合同的前提下补“桥”，不能另起新剧情。
                8. 章末钩子必须是本章因果链的结果，而不是从下一章硬搬一个惊吓/谜底过来。
                9. 不得提前兑现后续核心转折和秘密；缺少证据时宁可保持未知，也不要脑补完整答案。
                10. 输出的是内部逻辑计划，禁止写成正文段落，禁止追求文采。
            """.trimIndent(),
            user = """
                小说：${request.snapshot.novel.title}
                第${request.chapter.chapterNumber}章：${request.chapter.title}
                唯一目标：${request.chapter.objective}

                【S·章节执行合同 / 场景计划】
                ${context.execution}

                【A·Canon 与硬边界】
                ${context.canon}

                【B·人物 / 时间 / 伏笔当前状态】
                ${context.state}

                【D·近期剧情与相关历史｜只作已发生事实】
                ${context.history}

                请先证明本章“为什么这样发生”是连得起来的，再输出 logic-plan。不要写正文。
            """.trimIndent(),
        )
    }

    fun render(
        request: GenerationRequest,
        output: GeneratedChapter?,
    ): String {
        val structured = output?.let { value ->
            buildString {
                value.content.trim().takeIf(String::isNotBlank)?.let {
                    appendLine(it.take(3_000))
                }
                value.stateChanges.take(8).forEachIndexed { index, change ->
                    append("因果节点${index + 1}｜人物=${change.subject.ifBlank { "场景角色" }}")
                    append("｜触发=${change.field.ifBlank { "承接上一节点结果" }}")
                    append("｜行动前依据=${change.before.ifBlank { "必须来自已确认上下文" }}")
                    append("｜行动与直接结果=${change.after.ifBlank { "推进本章唯一目标" }}")
                    if (change.evidence.isNotBlank()) append("｜桥接=${change.evidence}")
                    appendLine()
                }
                value.summary.trim().takeIf(String::isNotBlank)?.let { appendLine("因果主轴=$it") }
            }.trim()
        }.orEmpty()
        return structured.ifBlank { fallback(request) }.take(MAX_PLAN_CHARS)
    }

    fun fallback(request: GenerationRequest): String {
        val scenes = request.chapter.scenePlan.sortedBy { it.order }
        if (scenes.isEmpty()) {
            return buildString {
                appendLine("起点：严格承接最近已确认人物状态和时间线，不新增便利条件。")
                appendLine("人物动机：主角只能基于当前目标与已知信息采取行动。")
                appendLine("核心因果：先出现与本章目标直接相关的触发 → 人物基于已有证据做选择 → 选择遭遇阻碍 → 付出代价后得到有限结果。")
                appendLine("场景桥：下一步必须由上一结果产生，禁止无因换地点、换目标或突然知道答案。")
                appendLine("章末：只用本章已经建立的因果推出新的问题/选择/威胁。")
                append("禁止跳步：不得用巧合、临时能力、临时证据或未铺垫人物直接解决“${request.chapter.objective}”。")
            }.take(MAX_PLAN_CHARS)
        }

        return buildString {
            appendLine("本章唯一目标=${request.chapter.objective}")
            var previousOutcome = "承接上一章已确认结果"
            scenes.forEach { scene ->
                appendLine(
                    "场景${scene.order}：前提=$previousOutcome → ${scene.viewpoint.ifBlank { "视角人物" }}为了“${scene.purpose}”" +
                        "在${scene.location}采取行动；阻碍=${scene.conflict}；必须通过可见行动得到结果=${scene.outcome}。"
                )
                previousOutcome = scene.outcome.ifBlank { "本场必须产生新的信息、代价、选择或威胁" }
            }
            appendLine("场景桥：每一场开头必须承接上一场结果；若换地点/对象/目标，正文必须写出触发原因和过渡。")
            append("章末：最后场景的结果自然推出章节合同钩子；不得从后续章纲硬搬新谜底。")
        }.take(MAX_PLAN_CHARS)
    }

    companion object {
        private const val MAX_PLAN_CHARS = 5_200
        const val INSTRUCTION_HEADER = "【写前逻辑骨架｜内部执行，不得在正文解释】"
    }
}
