package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.StorySnapshot

enum class DependencyRisk(val weight: Int, val label: String) {
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高"),
}

enum class DependencyKind(val label: String) {
    CHARACTER("人物状态"),
    TIMELINE("时间线"),
    FORESHADOW("伏笔"),
    SUMMARY("长期摘要"),
    DOWNSTREAM_CHAPTER("后续章节"),
    OUTLINE("后续章纲"),
}

data class ChapterDependencyImpact(
    val kind: DependencyKind,
    val risk: DependencyRisk,
    val title: String,
    val detail: String,
    val chapterNumber: Int? = null,
)

data class ChapterDependencyReport(
    val sourceChapter: Int,
    val sourceTitle: String,
    val overallRisk: DependencyRisk,
    val direct: List<ChapterDependencyImpact>,
    val downstream: List<ChapterDependencyImpact>,
    val anchors: List<String>,
) {
    val all: List<ChapterDependencyImpact> get() = direct + downstream
    val highCount: Int get() = all.count { it.risk == DependencyRisk.HIGH }
    val mediumCount: Int get() = all.count { it.risk == DependencyRisk.MEDIUM }
    val downstreamChapterCount: Int get() = downstream.mapNotNull { it.chapterNumber }.filter { it > sourceChapter }.distinct().size

    val recommendation: String
        get() = when (overallRisk) {
            DependencyRisk.HIGH -> "这章已经成为后续事实来源。删除、整章重写或回滚前，应先检查人物/时间线/伏笔，并在操作后复核受影响的后续章节。"
            DependencyRisk.MEDIUM -> "存在可见的后续引用。可以修改，但建议先查看影响项，并在完成后复核相关章节。"
            DependencyRisk.LOW -> "目前没有发现强依赖，但仍建议在大改后运行一次一致性检查。"
        }
}

/**
 * 纯本地章节依赖分析器。
 * 0.14+ 优先使用 factHistory 的精确来源；旧项目缺失来源时退化为人物更新时间、时间线、伏笔与正文锚点推断。
 */
object ChapterDependencyAnalyzer {
    fun analyze(snapshot: StorySnapshot, chapters: List<ChapterDraft>, sourceChapter: Int): ChapterDependencyReport {
        val source = chapters.firstOrNull { it.chapterNumber == sourceChapter } ?: error("找不到第${sourceChapter}章")
        val sourceText = source.searchableText()
        val direct = mutableListOf<ChapterDependencyImpact>()
        val downstream = mutableListOf<ChapterDependencyImpact>()
        val anchors = linkedSetOf<String>()

        snapshot.factHistory.filter { it.chapter == sourceChapter }.forEach { fact ->
            if (fact.subject.length >= 2) anchors += fact.subject
            direct += ChapterDependencyImpact(
                kind = fact.kind.toDependencyKind(),
                risk = DependencyRisk.HIGH,
                title = fact.subject.ifBlank { fact.kind },
                detail = buildString {
                    append("已记录精确事实来源")
                    if (fact.before.isNotBlank()) append(" · 修改前=${fact.before}")
                    if (fact.after.isNotBlank()) append(" · 修改后=${fact.after}")
                    if (fact.evidence.isNotBlank()) append(" · 证据=${fact.evidence.take(180)}")
                },
                chapterNumber = sourceChapter,
            )
        }

        val sourceCharacters = snapshot.characters.filter { character ->
            character.name.isNotBlank() && sourceText.contains(character.name, ignoreCase = true)
        }
        sourceCharacters.forEach { character ->
            anchors += character.name
            val exact = character.lastUpdatedChapter == sourceChapter
            direct += ChapterDependencyImpact(
                kind = DependencyKind.CHARACTER,
                risk = if (exact) DependencyRisk.HIGH else DependencyRisk.MEDIUM,
                title = character.name,
                detail = if (exact) {
                    "当前人物状态最后一次明确更新来自第${sourceChapter}章；位置=${character.location}，情绪=${character.emotionalState}，目标=${character.goal}。"
                } else {
                    "该人物在本章正文/章纲中出现。当前状态最后更新于第${character.lastUpdatedChapter}章，修改本章可能改变后续理解。"
                },
                chapterNumber = character.lastUpdatedChapter.takeIf { it > 0 },
            )
        }

        snapshot.recentTimeline.filter { it.chapter == sourceChapter }.forEach { event ->
            event.participants.filter { it.isNotBlank() }.forEach { anchors += it }
            event.location.takeIf { it.length >= 2 }?.let { anchors += it }
            direct += ChapterDependencyImpact(
                kind = DependencyKind.TIMELINE,
                risk = DependencyRisk.HIGH,
                title = event.summary.ifBlank { "第${sourceChapter}章时间线事件" },
                detail = buildString {
                    append("这是明确记录在第${sourceChapter}章的时间线事实")
                    if (event.storyTime.isNotBlank()) append(" · 时间=${event.storyTime}")
                    if (event.location.isNotBlank()) append(" · 地点=${event.location}")
                    if (event.participants.isNotEmpty()) append(" · 参与者=${event.participants.joinToString("、")}")
                    if (event.consequences.isNotEmpty()) append(" · 后果=${event.consequences.joinToString("；")}")
                },
                chapterNumber = sourceChapter,
            )
        }

        snapshot.relevantForeshadowing.forEach { clue ->
            val plantedHere = clue.plantedChapter == sourceChapter
            val touchedHere = clue.detail.contains("第${sourceChapter}章")
            if (plantedHere || touchedHere || sourceText.contains(clue.title, ignoreCase = true)) {
                anchors += clue.title
                direct += ChapterDependencyImpact(
                    kind = DependencyKind.FORESHADOW,
                    risk = if (plantedHere || touchedHere) DependencyRisk.HIGH else DependencyRisk.MEDIUM,
                    title = clue.title,
                    detail = buildString {
                        if (plantedHere) append("伏笔明确埋设于本章") else if (touchedHere) append("伏笔详情明确记录了本章推进") else append("本章文本引用了该伏笔")
                        append(" · 状态=${clue.status.name}")
                        append(" · 预计回收=${clue.expectedChapterStart}-${clue.expectedChapterEnd}章")
                        if (clue.expectedPayoff.isNotBlank()) append(" · 目标=${clue.expectedPayoff}")
                    },
                    chapterNumber = clue.plantedChapter,
                )
            }
        }

        snapshot.recentSummaries.filter { it.trimStart().startsWith("第${sourceChapter}章") }.forEach { summary ->
            direct += ChapterDependencyImpact(
                kind = DependencyKind.SUMMARY,
                risk = DependencyRisk.HIGH,
                title = "章节摘要已进入长期上下文",
                detail = summary.take(260),
                chapterNumber = sourceChapter,
            )
        }

        val stableAnchors = anchors.map { it.trim() }.filter { it.length >= 2 }.distinct().take(24)

        snapshot.factHistory.filter { it.chapter > sourceChapter }.forEach { fact ->
            val text = "${fact.subject} ${fact.before} ${fact.after} ${fact.evidence}"
            val hits = stableAnchors.filter { text.contains(it, ignoreCase = true) }
            if (hits.isNotEmpty()) {
                downstream += ChapterDependencyImpact(
                    kind = fact.kind.toDependencyKind(),
                    risk = DependencyRisk.HIGH,
                    title = "第${fact.chapter}章事实 · ${fact.subject.ifBlank { fact.kind }}",
                    detail = "后续结构化事实继续引用：${hits.take(6).joinToString("、")}。这不是文本猜测，而是已确认写入的事实链。",
                    chapterNumber = fact.chapter,
                )
            }
        }

        chapters.filter { it.chapterNumber > sourceChapter }.sortedBy { it.chapterNumber }.forEach { chapter ->
            val text = chapter.searchableText()
            val hits = stableAnchors.filter { text.contains(it, ignoreCase = true) }
            if (hits.isNotEmpty()) {
                val strong = hits.size >= 2 || hits.any { anchor -> direct.any { it.risk == DependencyRisk.HIGH && it.title.equals(anchor, ignoreCase = true) } }
                downstream += ChapterDependencyImpact(
                    kind = DependencyKind.DOWNSTREAM_CHAPTER,
                    risk = if (strong) DependencyRisk.HIGH else DependencyRisk.MEDIUM,
                    title = "第${chapter.chapterNumber}章 · ${chapter.title}",
                    detail = "后续正文/目标/场景继续引用：${hits.take(6).joinToString("、")}。本章被删除或改写后，这些引用需要复核。",
                    chapterNumber = chapter.chapterNumber,
                )
            }
        }

        val outline = if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline
        outline.filter { it.level == OutlineLevel.CHAPTER && it.order > sourceChapter }.sortedBy { it.order }.forEach { node ->
            val text = listOf(node.title, node.objective, node.conflict, node.turningPoint, node.mustInclude.joinToString(" ")).joinToString(" ")
            val hits = stableAnchors.filter { text.contains(it, ignoreCase = true) }
            if (hits.isNotEmpty()) {
                downstream += ChapterDependencyImpact(
                    kind = DependencyKind.OUTLINE,
                    risk = DependencyRisk.MEDIUM,
                    title = "第${node.order}章章纲 · ${node.title}",
                    detail = "后续章纲引用：${hits.take(6).joinToString("、")}。若本章事实改变，章纲目标/转折可能需要同步调整。",
                    chapterNumber = node.order,
                )
            }
        }

        snapshot.recentTimeline.filter { it.chapter > sourceChapter }.forEach { event ->
            val text = listOf(event.location, event.summary, event.participants.joinToString(" "), event.consequences.joinToString(" ")).joinToString(" ")
            val hits = stableAnchors.filter { text.contains(it, ignoreCase = true) }
            if (hits.isNotEmpty()) {
                downstream += ChapterDependencyImpact(
                    kind = DependencyKind.TIMELINE,
                    risk = DependencyRisk.HIGH,
                    title = "第${event.chapter}章时间线 · ${event.summary}",
                    detail = "结构化时间线继续依赖：${hits.take(6).joinToString("、")}。",
                    chapterNumber = event.chapter,
                )
            }
        }

        val directDistinct = direct.distinctBy { listOf(it.kind, it.title, it.chapterNumber, it.detail) }
        val downstreamDistinct = downstream.distinctBy { listOf(it.kind, it.title, it.chapterNumber) }
        val all = directDistinct + downstreamDistinct
        val overall = when {
            all.any { it.risk == DependencyRisk.HIGH } -> DependencyRisk.HIGH
            all.any { it.risk == DependencyRisk.MEDIUM } -> DependencyRisk.MEDIUM
            else -> DependencyRisk.LOW
        }
        return ChapterDependencyReport(sourceChapter, source.title, overall, directDistinct, downstreamDistinct, stableAnchors)
    }

    private fun String.toDependencyKind(): DependencyKind = when {
        startsWith("CHARACTER") || this == "RELATION" -> DependencyKind.CHARACTER
        startsWith("FORESHADOW") -> DependencyKind.FORESHADOW
        this == "TIMELINE" -> DependencyKind.TIMELINE
        else -> DependencyKind.SUMMARY
    }

    private fun ChapterDraft.searchableText(): String = buildString {
        append(title).append('\n')
        append(objective).append('\n')
        append(summary).append('\n')
        scenePlan.sortedBy { it.order }.forEach { scene ->
            append(scene.viewpoint).append(' ').append(scene.location).append(' ').append(scene.purpose).append(' ').append(scene.conflict).append(' ').append(scene.outcome).append('\n')
        }
        append(content)
    }
}
