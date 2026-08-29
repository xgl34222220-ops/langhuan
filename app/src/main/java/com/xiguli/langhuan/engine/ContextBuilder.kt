package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.OutlineLevel

/** 单条被 RAG 召回、但尚未进入 Prompt 的历史材料。 */
data class RetrievedContextItem(
    val sourceType: String,
    val sourceId: String,
    val chapterNumber: Int?,
    val text: String,
    val score: Double,
    val reasons: List<String> = emptyList(),
)

enum class ContextLayer(val label: String) {
    S_EXECUTION("S·本章执行合同"),
    A_CANON("A·Canon 与硬边界"),
    B_STATE("B·当前剧情状态"),
    C_STYLE("C·文风与写法"),
    D_HISTORY("D·历史召回"),
}

data class ContextTraceEntry(
    val layer: ContextLayer,
    val source: String,
    val reason: String,
    val score: Double? = null,
)

data class GenerationContextPack(
    val execution: String,
    val canon: String,
    val state: String,
    val style: String,
    val history: String,
    val trace: List<ContextTraceEntry>,
) {
    fun traceLines(): List<String> = trace.map { item ->
        val score = item.score?.let { " · ${(it * 100).toInt()}分" }.orEmpty()
        "${item.layer.label} · ${item.source}$score · ${item.reason}"
    }
}

/**
 * Context Builder 2.0
 *
 * S > A > B > C > D 独立预算。历史 RAG 永远只能进入 D 层，所以再多历史材料也不能
 * 把章节合同、Canon、时间锁和信息边界从 Prompt 中挤掉。
 */
class GenerationContextBuilder(
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
) {
    fun build(
        request: GenerationRequest,
        retrieved: List<RetrievedContextItem> = emptyList(),
    ): GenerationContextPack {
        val snapshot = request.snapshot
        val chapter = request.chapter
        val chapterNumber = chapter.chapterNumber.coerceAtLeast(1)
        val trace = mutableListOf<ContextTraceEntry>()

        val chapterOutline = snapshot.activeOutline.lastOrNull { it.level == OutlineLevel.CHAPTER }
        val contract = ChapterContractGuard.resolve(request)
        val contractText = ChapterContractGuard.renderContract(contract)
        val revealBudget = AutonomousExecutionEngine.revealBudget(snapshot, chapterNumber)
        val sceneItems = chapter.scenePlan.sortedBy { it.order }.map { scene ->
            val clock = if (scene.storyDay > 0 || scene.timeOfDay.isNotBlank()) {
                "故事第${scene.storyDay.takeIf { it > 0 } ?: 0}天·${scene.timeOfDay.ifBlank { "待锁定" }}；距上一场=${scene.elapsedFromPrevious.ifBlank { "连续" }}；${if (scene.isFlashback) "闪回" else "主时间线"}"
            } else "时间沿用时间轴锁"
            "场景${scene.order}｜$clock｜视角=${scene.viewpoint}｜地点=${scene.location}｜目的=${scene.purpose}｜冲突=${scene.conflict}｜落点=${scene.outcome}"
        }
        val executionItems = buildList {
            add("章节：第${chapter.chapterNumber}章 ${chapter.title}")
            add("唯一主目标：${chapter.objective}")
            chapterOutline?.let { node ->
                add("本章冲突：${node.conflict}")
                add("本章转折/落点：${node.turningPoint}")
            }
            add("章节合同：\n$contractText")
            add(
                "本章信息揭露预算：完整揭露最多${revealBudget.maxFullReveals}条，部分/暗示最多${revealBudget.maxPartialReveals}条；" +
                    "可完整boundaryId=${revealBudget.allowedFullBoundaryIds.joinToString("、")}；" +
                    "只可部分boundaryId=${revealBudget.allowedPartialBoundaryIds.joinToString("、")}；" +
                    "禁止揭底boundaryId=${revealBudget.forbiddenBoundaryIds.joinToString("、")}。不得因为预算存在而主动解释秘密。"
            )
            if (sceneItems.isNotEmpty()) add("场景计划：\n${sceneItems.joinToString("\n") { "- $it" }}")
            if (request.extraInstruction.isNotBlank()) add("用户本轮补充要求：${request.extraInstruction}")
        }
        trace += ContextTraceEntry(ContextLayer.S_EXECUTION, "章节合同/章纲/场景计划", "当前章节不可丢失的执行约束")
        val execution = fitItems(executionItems, 8_000)

        val lockedBible = snapshot.bible
            .filter { it.category != BibleCategory.STYLE }
            .filter { it.locked || it.category == BibleCategory.FORBIDDEN }
            .filterNot { it.name == CREATION_FACT_LEDGER }
            .sortedWith(compareBy({ if (it.category == BibleCategory.FORBIDDEN || it.category == BibleCategory.RULE) 0 else 1 }, { it.name }))
            .map { "[${it.category}] ${it.name}：${it.content}" }
        val direction = snapshot.activeOutline
            .filter { it.level != OutlineLevel.CHAPTER }
            .sortedWith(compareBy({ it.level.ordinal }, { it.order }))
            .map { node ->
                val level = if (node.level == OutlineLevel.MASTER) "全书方向" else "本卷方向"
                "[$level] ${node.title}｜阶段目标=${node.objective}｜长期冲突=${node.conflict}"
            }
        val canonItems = buildList {
            if (lockedBible.isNotEmpty()) add("锁定设定：\n${lockedBible.joinToString("\n") { "- $it" }}")
            if (direction.isNotEmpty()) add("长期方向（仅防跑偏，不可提前兑现）：\n${direction.joinToString("\n") { "- $it" }}")
            add("信息边界：\n${ChapterContractGuard.renderKnowledge(snapshot, chapterNumber)}")
            add("时间轴锁：\n${chronologyGuard.promptText(snapshot, chapter.scenePlan)}")
        }
        trace += ContextTraceEntry(ContextLayer.A_CANON, "锁定设定/信息边界/主时间钟", "硬约束始终完整优先于历史召回")
        val canon = fitItems(canonItems, 10_000)

        val sceneParticipants = chapter.scenePlan.map { it.viewpoint }.filter(String::isNotBlank).toSet()
        val characters = snapshot.characters
            .sortedWith(compareBy({ if (it.name in sceneParticipants) 0 else 1 }, { -it.lastUpdatedChapter }))
            .map { character ->
                "${character.name}｜地点=${character.location}｜身体=${character.physicalState}｜情绪=${character.emotionalState}｜目标=${character.goal}｜性格=${character.personality.joinToString("、")}｜关系=${character.relationshipNotes.entries.joinToString("；") { "${it.key}=${it.value}" }}｜本人已知=${character.knownSecrets.joinToString("、")}"
            }
        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(24)
            .map { event ->
                val clock = if (event.storyDay > 0) {
                    "故事第${event.storyDay}天·${event.timeOfDay.ifBlank { event.storyTime }}｜距上次=${event.elapsedFromPrevious.ifBlank { "未记录" }}${if (event.isFlashback) "｜闪回" else ""}"
                } else event.storyTime.ifBlank { "旧时间记录未结构化" }
                "第${event.chapter}章｜$clock｜${event.location}：${event.summary}"
            }
        val foreshadows = snapshot.relevantForeshadowing
            .filter { it.status !in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED) }
            .sortedBy { it.expectedChapterEnd }
            .map { item ->
                val due = item.expectedChapterStart > 0 && chapterNumber >= item.expectedChapterStart
                if (due) {
                    "${item.title}｜已有线索=${item.detail}｜已进入可触及窗口；只有章节合同需要时才推进，禁止自动揭底"
                } else {
                    "${item.title}｜已有线索=${item.detail}｜尚未到解释/回收期，禁止揭底"
                }
            }
        val stateItems = buildList {
            if (characters.isNotEmpty()) add("人物当前状态：\n${characters.joinToString("\n") { "- $it" }}")
            if (timeline.isNotEmpty()) add("最近时间线：\n${timeline.joinToString("\n") { "- $it" }}")
            if (foreshadows.isNotEmpty()) add("伏笔可见范围：\n${foreshadows.joinToString("\n") { "- $it" }}")
        }
        trace += ContextTraceEntry(ContextLayer.B_STATE, "人物/时间线/伏笔状态", "当前章节直接依赖的动态状态")
        val state = fitItems(stateItems, 9_000)

        val declaredStyle = snapshot.bible
            .filter { it.category == BibleCategory.STYLE }
            .filterNot { it.name == CREATION_FACT_LEDGER }
            .map { "${it.name}：${it.content}${if (it.locked) "（必须遵守）" else "（偏好）"}" }
        val learnedStyle = AuthorPreferenceEngine.promptText(snapshot)
        val fullBookGuidance = FullBookEditorEngine.promptText(snapshot)
        val styleItems = buildList {
            if (declaredStyle.isEmpty()) add("保持自然、具体、有场景感的中文小说叙事；不要写成设定说明或案件报告。")
            else addAll(declaredStyle)
            if (learnedStyle.isNotBlank()) add(learnedStyle)
            if (fullBookGuidance.isNotBlank()) add(fullBookGuidance)
        }
        trace += ContextTraceEntry(ContextLayer.C_STYLE, "作品文风/作者编辑画像/全书主编", "只控制长期写作模式；任何提醒不得覆盖 S/A 层事实")
        val style = fitItems(styleItems, 4_800)

        val recent = snapshot.recentSummaries.takeLast(10).map { "近期剧情：$it" }
        val safeRetrieved = retrieved
            .filterNot { it.text.contains(CREATION_FACT_LEDGER) }
            .sortedByDescending { it.score }
            .take(12)
        val ragItems = safeRetrieved.map { hit ->
            "[${hit.sourceType}${hit.chapterNumber?.let { "/第${it}章" }.orEmpty()}] ${hit.text}"
        }
        safeRetrieved.forEach { hit ->
            trace += ContextTraceEntry(
                ContextLayer.D_HISTORY,
                source = "${hit.sourceType}:${hit.sourceId}",
                reason = hit.reasons.joinToString("+").ifBlank { "综合相关性" },
                score = hit.score,
            )
        }
        val historyItems = buildList {
            addAll(recent)
            addAll(ragItems)
        }
        val history = fitItems(historyItems, 8_000).ifBlank { "暂无需要额外召回的历史材料。" }

        return GenerationContextPack(execution, canon, state, style, history, trace)
    }

    private fun fitItems(items: List<String>, budgetChars: Int): String {
        if (items.isEmpty() || budgetChars <= 0) return ""
        val out = StringBuilder()
        for (raw in items) {
            val item = raw.trim()
            if (item.isBlank()) continue
            val remaining = budgetChars - out.length
            if (remaining <= 0) break
            val piece = if (item.length <= remaining) item else item.take(remaining.coerceAtLeast(0)).trimEnd() + "…"
            if (out.isNotEmpty()) out.append("\n\n")
            out.append(piece)
            if (piece.length < item.length) break
        }
        return out.toString().trim()
    }

    companion object {
        const val CREATION_FACT_LEDGER = "建书会谈确认事实"
    }
}
