package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.CandidateFactRisk
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.StorySnapshot

enum class StoryGraphNodeType(val label: String) {
    NOVEL("作品"),
    OUTLINE("大纲"),
    CHAPTER("章节"),
    CHARACTER("人物"),
    BIBLE("Canon"),
    TIMELINE("时间线"),
    FORESHADOW("伏笔"),
    KNOWLEDGE("信息边界"),
    FACT("事实来源"),
    MIGRATION("修复任务"),
}

enum class StoryGraphEdgeType(val label: String) {
    CONTAINS("包含"),
    PARENT_OF("父级"),
    STATE_FROM("状态来源"),
    OCCURS_IN("发生于"),
    PARTICIPATES_IN("参与"),
    PLANTED_IN("埋设于"),
    PAYOFF_WINDOW("回收窗口"),
    KNOWN_BY("已知"),
    UNKNOWN_TO("未知"),
    PROVENANCE("事实来源"),
    FACT_CHAIN("事实延续"),
    REPAIR_TARGET("修复目标"),
}

data class StoryGraphNode(
    val id: String,
    val type: StoryGraphNodeType,
    val label: String,
    val chapterNumber: Int? = null,
    val detail: String = "",
)

data class StoryGraphEdge(
    val from: String,
    val to: String,
    val type: StoryGraphEdgeType,
    val detail: String = "",
)

enum class StoryHealthSeverity(val weight: Int, val label: String) {
    LOW(2, "提示"),
    MEDIUM(5, "需注意"),
    HIGH(10, "高风险"),
}

enum class StoryHealthCategory(val label: String) {
    STRUCTURE("结构"),
    CHARACTER("人物"),
    TIMELINE("时间线"),
    FORESHADOW("伏笔"),
    KNOWLEDGE("信息边界"),
    PROVENANCE("事实来源"),
    WORKFLOW("修复/候选"),
}

data class StoryHealthIssue(
    val id: String,
    val category: StoryHealthCategory,
    val severity: StoryHealthSeverity,
    val title: String,
    val detail: String,
    val chapterNumber: Int? = null,
    val sourceNodeId: String? = null,
)

data class StoryGraphHotspot(
    val node: StoryGraphNode,
    val degree: Int,
    val reason: String,
)

data class StoryGraphHealthReport(
    val novelId: String,
    val score: Int,
    val nodes: List<StoryGraphNode>,
    val edges: List<StoryGraphEdge>,
    val issues: List<StoryHealthIssue>,
    val hotspots: List<StoryGraphHotspot>,
) {
    val highCount: Int get() = issues.count { it.severity == StoryHealthSeverity.HIGH }
    val mediumCount: Int get() = issues.count { it.severity == StoryHealthSeverity.MEDIUM }
    val lowCount: Int get() = issues.count { it.severity == StoryHealthSeverity.LOW }
    val affectedChapters: Int get() = issues.mapNotNull { it.chapterNumber }.distinct().size
    val statusLabel: String
        get() = when {
            score >= 90 -> "健康"
            score >= 75 -> "基本稳定"
            score >= 60 -> "需要关注"
            else -> "高风险"
        }

    fun compactSummary(): String = "$score 分 · $statusLabel · $highCount 高风险 · $affectedChapters 章受影响"
}

/**
 * V10 full-book Story Graph and deterministic health analyzer.
 *
 * It intentionally consumes structured project truth only. It does not rescan all prose, call AI,
 * or mutate Canon. Provenance chains, chapter state anchors, timeline, foreshadowing, knowledge
 * boundaries and V9 migration tasks become inspectable graph edges. This keeps the check useful on
 * million-word projects without turning every health refresh into an O(book-text) semantic scan.
 */
object StoryGraphHealthAnalyzer {
    fun analyze(
        snapshot: StorySnapshot,
        chapters: List<ChapterDraft>,
        migrationQueue: CanonMigrationQueue = CanonMigrationQueue(snapshot.novel.id),
    ): StoryGraphHealthReport {
        val nodes = linkedMapOf<String, StoryGraphNode>()
        val edges = mutableListOf<StoryGraphEdge>()
        val issues = mutableListOf<StoryHealthIssue>()
        val novelId = snapshot.novel.id
        val novelNode = "novel:$novelId"
        nodes[novelNode] = StoryGraphNode(novelNode, StoryGraphNodeType.NOVEL, snapshot.novel.title)

        val outline = (if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline)
            .distinctBy { it.id }
        val chapterNodes = outline.filter { it.level == OutlineLevel.CHAPTER }.associateBy { it.order }
        val maxChapter = maxOf(
            chapterNodes.keys.maxOrNull() ?: 0,
            chapters.maxOfOrNull { it.chapterNumber } ?: 0,
            snapshot.novel.currentChapter,
        )

        outline.forEach { item ->
            val id = if (item.level == OutlineLevel.CHAPTER) "chapter:${item.order}" else "outline:${item.id}"
            val type = if (item.level == OutlineLevel.CHAPTER) StoryGraphNodeType.CHAPTER else StoryGraphNodeType.OUTLINE
            nodes[id] = StoryGraphNode(
                id = id,
                type = type,
                label = item.title,
                chapterNumber = item.order.takeIf { item.level == OutlineLevel.CHAPTER },
                detail = item.objective,
            )
            val parentNode = item.parentId?.let { parentId ->
                outline.firstOrNull { it.id == parentId }?.let { parent ->
                    if (parent.level == OutlineLevel.CHAPTER) "chapter:${parent.order}" else "outline:${parent.id}"
                }
            }
            if (parentNode != null) edges += StoryGraphEdge(parentNode, id, StoryGraphEdgeType.PARENT_OF)
            else edges += StoryGraphEdge(novelNode, id, StoryGraphEdgeType.CONTAINS)
        }

        chapters.forEach { draft ->
            val id = "chapter:${draft.chapterNumber}"
            nodes.putIfAbsent(
                id,
                StoryGraphNode(id, StoryGraphNodeType.CHAPTER, draft.title, draft.chapterNumber, draft.objective),
            )
        }

        val chapterOrders = chapterNodes.keys.sorted()
        chapterOrders.zipWithNext().forEach { (a, b) ->
            if (b != a + 1) {
                issues += issue(
                    StoryHealthCategory.STRUCTURE,
                    StoryHealthSeverity.HIGH,
                    "章纲编号断层",
                    "第${a}章之后直接进入第${b}章，三级大纲存在编号断层。",
                    b,
                    "chapter:$b",
                )
            }
        }
        outline.filter { it.level == OutlineLevel.CHAPTER }.forEach { chapter ->
            if (chapter.objective.isBlank() || chapter.conflict.isBlank() || chapter.turningPoint.isBlank()) {
                issues += issue(
                    StoryHealthCategory.STRUCTURE,
                    StoryHealthSeverity.MEDIUM,
                    "第${chapter.order}章章纲不完整",
                    "目标、冲突、转折至少有一项为空，写作 Runtime 的章节约束会变弱。",
                    chapter.order,
                    "chapter:${chapter.order}",
                )
            }
        }
        chapters.filter { it.content.isNotBlank() && it.summary.isBlank() }.forEach { draft ->
            issues += issue(
                StoryHealthCategory.STRUCTURE,
                StoryHealthSeverity.LOW,
                "第${draft.chapterNumber}章缺少正式摘要",
                "正文已存在但章节摘要为空，后续长程上下文只能依赖其它结构化事实。",
                draft.chapterNumber,
                "chapter:${draft.chapterNumber}",
            )
        }

        val characterByName = snapshot.characters.associateBy { it.name.trim() }.filterKeys { it.isNotBlank() }
        snapshot.characters.forEach { character ->
            val id = "character:${character.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.CHARACTER, character.name, character.lastUpdatedChapter, character.goal)
            edges += StoryGraphEdge(novelNode, id, StoryGraphEdgeType.CONTAINS)
            if (character.lastUpdatedChapter > 0) {
                val chapterId = "chapter:${character.lastUpdatedChapter}"
                edges += StoryGraphEdge(id, chapterId, StoryGraphEdgeType.STATE_FROM)
                if (character.lastUpdatedChapter > maxChapter || nodes[chapterId] == null) {
                    issues += issue(
                        StoryHealthCategory.CHARACTER,
                        StoryHealthSeverity.HIGH,
                        "${character.name} 的状态来源章节不存在",
                        "人物状态指向第${character.lastUpdatedChapter}章，但当前项目没有对应正式章节。",
                        character.lastUpdatedChapter,
                        id,
                    )
                }
            }
            character.relationshipNotes.keys.filter { it.isNotBlank() && it !in characterByName }.forEach { target ->
                issues += issue(
                    StoryHealthCategory.CHARACTER,
                    StoryHealthSeverity.MEDIUM,
                    "${character.name} 的关系对象未建档",
                    "关系记录引用“$target”，但当前人物表中没有同名人物。可能是别名，也可能是遗漏建档。",
                    character.lastUpdatedChapter.takeIf { it > 0 },
                    id,
                )
            }
        }

        snapshot.bible.forEach { entry ->
            val id = "bible:${entry.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.BIBLE, entry.name, detail = entry.content.take(180))
            edges += StoryGraphEdge(novelNode, id, StoryGraphEdgeType.CONTAINS)
        }

        val timelineSorted = snapshot.recentTimeline.sortedWith(
            compareBy({ if (it.storyDay > 0) it.storyDay else Int.MAX_VALUE }, { it.chapter }, { it.orderInChapter })
        )
        snapshot.recentTimeline.forEach { event ->
            val id = "timeline:${event.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.TIMELINE, event.summary.ifBlank { "第${event.chapter}章事件" }, event.chapter, event.storyTime)
            edges += StoryGraphEdge(id, "chapter:${event.chapter}", StoryGraphEdgeType.OCCURS_IN)
            if (event.chapter <= 0 || event.chapter > maxChapter || nodes["chapter:${event.chapter}"] == null) {
                issues += issue(
                    StoryHealthCategory.TIMELINE,
                    StoryHealthSeverity.HIGH,
                    "时间线事件指向不存在章节",
                    "“${event.summary.take(80)}”记录在第${event.chapter}章，但项目没有对应章节。",
                    event.chapter.takeIf { it > 0 },
                    id,
                )
            }
            event.participants.filter { it.isNotBlank() }.forEach { name ->
                val character = characterByName[name]
                if (character != null) {
                    edges += StoryGraphEdge("character:${character.id}", id, StoryGraphEdgeType.PARTICIPATES_IN)
                } else {
                    issues += issue(
                        StoryHealthCategory.TIMELINE,
                        StoryHealthSeverity.LOW,
                        "时间线参与者未建人物档案",
                        "第${event.chapter}章事件引用“$name”，人物表中没有同名角色。",
                        event.chapter,
                        id,
                    )
                }
            }
        }
        timelineSorted.filterNot { it.isFlashback }.zipWithNext().forEach { (a, b) ->
            if (a.storyDay > 0 && b.storyDay > 0 && b.storyDay < a.storyDay) {
                issues += issue(
                    StoryHealthCategory.TIMELINE,
                    StoryHealthSeverity.HIGH,
                    "主时间线发生倒退",
                    "第${a.chapter}章已到故事第${a.storyDay}天，第${b.chapter}章却回到第${b.storyDay}天，且后者未标记为闪回。",
                    b.chapter,
                    "timeline:${b.id}",
                )
            }
        }
        snapshot.recentTimeline.groupBy { it.chapter }.forEach { (chapter, events) ->
            val explicit = events.filter { it.orderInChapter > 0 }
            val duplicates = explicit.groupBy { it.orderInChapter }.filterValues { it.size > 1 }
            if (duplicates.isNotEmpty()) {
                issues += issue(
                    StoryHealthCategory.TIMELINE,
                    StoryHealthSeverity.MEDIUM,
                    "第${chapter}章时间线顺序重复",
                    "同章事件出现重复 orderInChapter：${duplicates.keys.sorted().joinToString("、")}。",
                    chapter,
                    explicit.firstOrNull()?.let { "timeline:${it.id}" },
                )
            }
        }

        snapshot.relevantForeshadowing.forEach { clue ->
            val id = "foreshadow:${clue.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.FORESHADOW, clue.title, clue.plantedChapter, clue.expectedPayoff)
            edges += StoryGraphEdge(id, "chapter:${clue.plantedChapter}", StoryGraphEdgeType.PLANTED_IN)
            clue.expectedChapterStart.takeIf { it > 0 }?.let { edges += StoryGraphEdge(id, "chapter:$it", StoryGraphEdgeType.PAYOFF_WINDOW, "start") }
            clue.expectedChapterEnd.takeIf { it > 0 && it != clue.expectedChapterStart }?.let { edges += StoryGraphEdge(id, "chapter:$it", StoryGraphEdgeType.PAYOFF_WINDOW, "end") }
            when {
                clue.expectedChapterStart > 0 && clue.expectedChapterEnd > 0 && clue.expectedChapterStart > clue.expectedChapterEnd -> issues += issue(
                    StoryHealthCategory.FORESHADOW,
                    StoryHealthSeverity.HIGH,
                    "伏笔“${clue.title}”回收窗口反向",
                    "预计回收起点 ${clue.expectedChapterStart} 大于终点 ${clue.expectedChapterEnd}。",
                    clue.plantedChapter,
                    id,
                )
                clue.expectedChapterEnd > 0 && clue.plantedChapter > clue.expectedChapterEnd -> issues += issue(
                    StoryHealthCategory.FORESHADOW,
                    StoryHealthSeverity.HIGH,
                    "伏笔“${clue.title}”埋设晚于回收窗口",
                    "第${clue.plantedChapter}章才埋设，但回收窗口已在第${clue.expectedChapterEnd}章结束。",
                    clue.plantedChapter,
                    id,
                )
                clue.status in setOf(ForeshadowStatus.PLANTED, ForeshadowStatus.DEVELOPING) &&
                    clue.expectedChapterEnd > 0 && snapshot.novel.currentChapter > clue.expectedChapterEnd -> issues += issue(
                    StoryHealthCategory.FORESHADOW,
                    StoryHealthSeverity.HIGH,
                    "伏笔“${clue.title}”已经超期",
                    "当前已到第${snapshot.novel.currentChapter}章，计划回收窗口在第${clue.expectedChapterEnd}章结束，但状态仍为 ${clue.status.name}。",
                    clue.expectedChapterEnd,
                    id,
                )
            }
        }

        snapshot.knowledgeLedger.forEach { boundary ->
            val id = "knowledge:${boundary.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.KNOWLEDGE, boundary.title, detail = boundary.truth.take(160))
            boundary.knownBy.filter { it.isNotBlank() }.forEach { name ->
                val character = characterByName[name]
                if (character != null) edges += StoryGraphEdge(id, "character:${character.id}", StoryGraphEdgeType.KNOWN_BY)
                else issues += issue(
                    StoryHealthCategory.KNOWLEDGE,
                    StoryHealthSeverity.MEDIUM,
                    "信息边界的已知人物不存在",
                    "“${boundary.title}”标记为 $name 已知，但人物表中没有同名角色。",
                    sourceNodeId = id,
                )
            }
            boundary.unknownTo.filter { it.isNotBlank() }.forEach { name ->
                val character = characterByName[name]
                if (character != null) edges += StoryGraphEdge(id, "character:${character.id}", StoryGraphEdgeType.UNKNOWN_TO)
                else issues += issue(
                    StoryHealthCategory.KNOWLEDGE,
                    StoryHealthSeverity.LOW,
                    "信息边界的未知人物不存在",
                    "“${boundary.title}”的 unknownTo 引用 $name，但人物表中没有同名角色。",
                    sourceNodeId = id,
                )
            }
            if (boundary.revealPolicy == KnowledgeRevealPolicy.FULL &&
                boundary.earliestFullRevealChapter > 0 &&
                snapshot.novel.currentChapter < boundary.earliestFullRevealChapter
            ) {
                issues += issue(
                    StoryHealthCategory.KNOWLEDGE,
                    StoryHealthSeverity.HIGH,
                    "“${boundary.title}”过早开放完整揭露",
                    "当前第${snapshot.novel.currentChapter}章，但最早完整揭露被锁定在第${boundary.earliestFullRevealChapter}章。",
                    snapshot.novel.currentChapter,
                    id,
                )
            }
        }

        val entityNodesByLabel = buildMap<String, String> {
            snapshot.characters.filter { it.name.isNotBlank() }.forEach { put(it.name.trim(), "character:${it.id}") }
            snapshot.bible.filter { it.name.isNotBlank() }.forEach { put(it.name.trim(), "bible:${it.id}") }
            snapshot.relevantForeshadowing.filter { it.title.isNotBlank() }.forEach { put(it.title.trim(), "foreshadow:${it.id}") }
            snapshot.knowledgeLedger.filter { it.title.isNotBlank() }.forEach { put(it.title.trim(), "knowledge:${it.id}") }
        }
        snapshot.factHistory.forEach { fact ->
            val id = "fact:${fact.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.FACT, fact.subject.ifBlank { fact.kind }, fact.chapter, fact.after)
            edges += StoryGraphEdge(id, "chapter:${fact.chapter}", StoryGraphEdgeType.PROVENANCE, fact.kind)
            entityNodesByLabel.entries.firstOrNull { (label, _) -> fact.subject.contains(label, ignoreCase = true) }
                ?.value?.let { entityId -> edges += StoryGraphEdge(entityId, id, StoryGraphEdgeType.PROVENANCE) }
            if (fact.chapter <= 0 || fact.chapter > maxChapter || nodes["chapter:${fact.chapter}"] == null) {
                issues += issue(
                    StoryHealthCategory.PROVENANCE,
                    StoryHealthSeverity.HIGH,
                    "事实来源指向不存在章节",
                    "${fact.subject.ifBlank { fact.kind }} 的 FactProvenance 指向第${fact.chapter}章。",
                    fact.chapter.takeIf { it > 0 },
                    id,
                )
            }
        }
        snapshot.factHistory
            .filter { it.subject.isNotBlank() }
            .groupBy { it.subject.trim().lowercase() }
            .values
            .forEach { chain ->
                chain.sortedWith(compareBy({ it.chapter }, { it.recordedAt })).zipWithNext().forEach { (a, b) ->
                    if (a.chapter != b.chapter || a.id != b.id) {
                        edges += StoryGraphEdge("fact:${a.id}", "fact:${b.id}", StoryGraphEdgeType.FACT_CHAIN, a.subject)
                    }
                }
            }

        migrationQueue.tasks.filterNot { it.status.isResolved }.forEach { task ->
            val id = "migration:${task.id}"
            nodes[id] = StoryGraphNode(id, StoryGraphNodeType.MIGRATION, task.action.label, task.chapterNumber, task.label)
            val target = task.chapterNumber?.let { "chapter:$it" } ?: novelNode
            edges += StoryGraphEdge(id, target, StoryGraphEdgeType.REPAIR_TARGET, task.status.name)
            val severity = if (task.priority == CanonChangeRisk.HIGH) StoryHealthSeverity.HIGH else StoryHealthSeverity.MEDIUM
            issues += issue(
                StoryHealthCategory.WORKFLOW,
                severity,
                "仍有未完成的 Canon 迁移：${task.action.label}",
                "${task.label} · ${task.detail} · 当前状态=${task.status.name}",
                task.chapterNumber,
                id,
            )
        }

        val pendingCandidates = snapshot.candidateFacts.filter { it.status == CandidateFactStatus.PENDING }
        pendingCandidates.filter { it.risk == CandidateFactRisk.HIGH }.forEach { candidate ->
            issues += issue(
                StoryHealthCategory.WORKFLOW,
                StoryHealthSeverity.MEDIUM,
                "高风险 Candidate 尚未处理：${candidate.subject}",
                "来源第${candidate.sourceChapter}章 · ${candidate.kind.name}。它还不是 Canon，但长期悬而未决会让后续写作意图不明确。",
                candidate.sourceChapter,
            )
        }

        val edgeDistinct = edges.filter { nodes[it.from] != null && nodes[it.to] != null }
            .distinctBy { listOf(it.from, it.to, it.type, it.detail) }
        val issueDistinct = issues.distinctBy { listOf(it.category, it.title, it.chapterNumber, it.sourceNodeId) }
            .sortedWith(compareByDescending<StoryHealthIssue> { it.severity.ordinal }.thenBy { it.chapterNumber ?: Int.MAX_VALUE })
        val high = issueDistinct.count { it.severity == StoryHealthSeverity.HIGH }
        val medium = issueDistinct.count { it.severity == StoryHealthSeverity.MEDIUM }
        val low = issueDistinct.count { it.severity == StoryHealthSeverity.LOW }
        val score = (100 - minOf(60, high * 10) - minOf(28, medium * 4) - minOf(12, low * 2)).coerceIn(0, 100)

        val degree = mutableMapOf<String, Int>()
        edgeDistinct.forEach { edge ->
            degree[edge.from] = (degree[edge.from] ?: 0) + 1
            degree[edge.to] = (degree[edge.to] ?: 0) + 1
        }
        val hotspots = degree.entries
            .mapNotNull { (id, count) -> nodes[id]?.takeIf { it.type !in setOf(StoryGraphNodeType.NOVEL, StoryGraphNodeType.OUTLINE) }?.let { it to count } }
            .sortedWith(compareByDescending<Pair<StoryGraphNode, Int>> { it.second }.thenBy { it.first.label })
            .take(8)
            .map { (node, count) ->
                StoryGraphHotspot(
                    node = node,
                    degree = count,
                    reason = when (node.type) {
                        StoryGraphNodeType.CHAPTER -> "这章连接了较多正式事实/时间线/伏笔或迁移任务，改动前应优先检查依赖。"
                        StoryGraphNodeType.CHARACTER -> "该人物跨多个结构化事实节点，是当前故事的高关联角色。"
                        StoryGraphNodeType.FORESHADOW -> "该伏笔同时连接埋设点与回收窗口。"
                        StoryGraphNodeType.FACT -> "这条事实处于连续 Provenance 链中。"
                        else -> "该节点与多个正式故事状态相连。"
                    },
                )
            }

        return StoryGraphHealthReport(novelId, score, nodes.values.toList(), edgeDistinct, issueDistinct, hotspots)
    }

    private fun issue(
        category: StoryHealthCategory,
        severity: StoryHealthSeverity,
        title: String,
        detail: String,
        chapterNumber: Int? = null,
        sourceNodeId: String? = null,
    ) = StoryHealthIssue(
        id = "${category.name}:${severity.name}:${title}:${chapterNumber ?: 0}:$sourceNodeId",
        category = category,
        severity = severity,
        title = title,
        detail = detail,
        chapterNumber = chapterNumber,
        sourceNodeId = sourceNodeId,
    )
}
