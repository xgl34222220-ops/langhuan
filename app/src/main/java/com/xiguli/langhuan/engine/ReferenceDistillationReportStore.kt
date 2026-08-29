package com.xiguli.langhuan.engine

import android.content.Context
import android.util.AtomicFile
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.StateChange
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ReferenceDistillationReportItem(
    val kind: String,
    val dimension: String,
    val value: String,
    val evidence: String = "",
)

@Serializable
data class ReferenceDistillationReport(
    val taskId: String,
    val title: String,
    val chapters: Int,
    val samples: Int,
    val provider: String,
    val model: String,
    val createdAt: Long,
    val overview: String,
    val summary: String,
    val localMetrics: String = "",
    /** 最终聚合后的高层 DNA，用于报告首页。 */
    val items: List<ReferenceDistillationReportItem> = emptyList(),
    /** V2：保留批次级 DNA，作为真正可检索的长期参考库。 */
    val retrievalItems: List<ReferenceDistillationReportItem> = emptyList(),
    val coverageGrade: String = "",
    val coverageNote: String = "",
    val legacySummaryOnly: Boolean = false,
)

data class ReferenceDnaUsage(
    val reportCount: Int,
    val matchedItems: Int,
    val availableItems: Int,
    val titles: List<String>,
) {
    val label: String
        get() = when {
            reportCount <= 0 -> ""
            matchedItems <= 0 -> "参考 DNA · 已绑定 $reportCount 本"
            else -> "参考 DNA · 本轮调用 $matchedItems 条 · ${titles.take(2).joinToString(" / ")}"
        }
}

/** Full, user-visible Style DNA + Story DNA report storage. */
class ReferenceDistillationReportStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val root = File(context.filesDir, "distillation/reports").apply { mkdirs() }

    fun save(
        taskId: String,
        title: String,
        chapters: Int,
        samples: Int,
        provider: String,
        model: String,
        localMetrics: String,
        dossier: GeneratedChapter,
        retainedObservations: List<String> = emptyList(),
    ): ReferenceDistillationReport {
        val allDossierItems = dossier.stateChanges.mapNotNull(::fromStateChange)
        val finalItems = allDossierItems.take(MAX_FINAL_ITEMS)
        val retained = buildList {
            retainedObservations.flatMapTo(this, ::parseObservationItems)
            addAll(allDossierItems)
        }
            .map(::normalizeItem)
            .distinctBy(::itemKey)
            .take(MAX_RETRIEVAL_ITEMS)

        val report = ReferenceDistillationReport(
            taskId = taskId,
            title = title,
            chapters = chapters,
            samples = samples,
            provider = provider,
            model = model,
            createdAt = System.currentTimeMillis(),
            overview = dossier.content.trim(),
            summary = dossier.summary.trim(),
            localMetrics = localMetrics.trim(),
            items = finalItems,
            retrievalItems = retained,
            coverageGrade = computeCoverageGrade(chapters, samples),
            coverageNote = computeCoverageNote(chapters, samples),
        )
        write(report)
        return report
    }

    fun load(taskId: String): ReferenceDistillationReport? {
        val file = AtomicFile(reportFile(taskId))
        return runCatching {
            if (!file.baseFile.exists()) return@runCatching null
            file.openRead().bufferedReader(Charsets.UTF_8).use { reader ->
                json.decodeFromString<ReferenceDistillationReport>(reader.readText()).normalizeKinds()
            }
        }.getOrNull()
    }

    fun listReports(): List<ReferenceDistillationReport> = root.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension.equals("json", true) }
        .mapNotNull { file ->
            runCatching {
                file.bufferedReader(Charsets.UTF_8).use { reader ->
                    json.decodeFromString<ReferenceDistillationReport>(reader.readText()).normalizeKinds()
                }
            }.getOrNull()
        }
        .distinctBy { it.taskId }
        .sortedByDescending { it.createdAt }

    fun coverageLabel(report: ReferenceDistillationReport): String = report.coverageGrade.ifBlank {
        computeCoverageGrade(report.chapters, report.samples)
    }

    fun coverageDescription(report: ReferenceDistillationReport): String = report.coverageNote.ifBlank {
        computeCoverageNote(report.chapters, report.samples)
    }

    fun hasStoryDna(report: ReferenceDistillationReport): Boolean =
        allItems(report).any { it.kind == "STORY" }

    fun retainedItemCount(report: ReferenceDistillationReport): Int = allItems(report).size

    fun kindCounts(report: ReferenceDistillationReport): Map<String, Int> =
        allItems(report).groupingBy { it.kind }.eachCount()

    fun promptContext(selectedTaskIds: List<String>, maxChars: Int = 13_000): String {
        val selected = selectedReports(selectedTaskIds)
        if (selected.isEmpty()) return ""
        val header = buildHeader(activeRetrieval = false)
        val remaining = (maxChars - header.length).coerceAtLeast(1_600)
        val perReportBudget = (remaining / selected.size).coerceIn(2_200, 7_500)
        val body = selected.mapIndexed { index, report -> buildReferencePacket(index, report, perReportBudget) }.joinToString("\n")
        return (header + body).take(maxChars)
    }

    fun searchContext(
        selectedTaskIds: List<String>,
        query: String,
        maxChars: Int = 8_500,
        maxItemsPerReport: Int = 18,
        allowedKinds: Set<String>? = null,
    ): String {
        val reports = selectedReports(selectedTaskIds)
        if (reports.isEmpty()) return ""
        val terms = queryTerms(query)
        val header = buildHeader(activeRetrieval = true)
        val remaining = (maxChars - header.length).coerceAtLeast(1_500)
        val perReportBudget = (remaining / reports.size).coerceAtLeast(1_100)
        val blocks = reports.mapIndexed { index, report ->
            val candidates = allItems(report).filter { allowedKinds == null || it.kind in allowedKinds }
            val ranked = rankItems(candidates, terms, query).take(maxItemsPerReport)
            buildString {
                appendLine("--- ${index + 1}. 《${report.title}》 · 命中 ${ranked.size} 条 / 可检索 ${candidates.size} 条 ---")
                ranked.forEach { item ->
                    append("${item.kind}/${item.dimension}: ${item.value.take(440)}")
                    if (item.evidence.isNotBlank()) append(" [${item.evidence.take(100)}]")
                    appendLine()
                }
                if (ranked.isEmpty() && report.summary.isNotBlank()) appendLine("STYLE_OVERVIEW: ${report.summary.take(420)}")
            }.take(perReportBudget)
        }
        return (header + blocks.joinToString("\n")).take(maxChars)
    }

    fun usage(
        selectedTaskIds: List<String>,
        query: String,
        maxItemsPerReport: Int = 18,
        allowedKinds: Set<String>? = null,
    ): ReferenceDnaUsage {
        val reports = selectedReports(selectedTaskIds)
        val terms = queryTerms(query)
        var available = 0
        var matched = 0
        reports.forEach { report ->
            val candidates = allItems(report).filter { allowedKinds == null || it.kind in allowedKinds }
            available += candidates.size
            matched += rankItems(candidates, terms, query).take(maxItemsPerReport).size
        }
        return ReferenceDnaUsage(reports.size, matched, available, reports.map { it.title })
    }

    private fun selectedReports(ids: List<String>): List<ReferenceDistillationReport> = ids.distinct().mapNotNull(::load)

    private fun buildHeader(activeRetrieval: Boolean): String = buildString {
        appendLine(if (activeRetrieval) "【本轮主动检索的参考 DNA】" else "【用户显式选择的参考双层 DNA】")
        appendLine("只允许读取用户显式选择的蒸馏档案，未选择作品不得自动混入。")
        if (activeRetrieval) appendLine("以下内容已经按当前问题排序；回答前必须先利用真正相关的条目，而不是把参考资料当背景略过。")
        appendLine("原作事实问答允许准确使用 STORY；原创设计/场景/正文只迁移 STYLE、KEEP、TRANSFORM，并遵守 AVOID。")
        appendLine("禁止把原作人物名、专名、具体能力规则、独特谜底和剧情骨架直接写进用户的新书。")
        appendLine("蒸馏没有确认的事实必须明确为未确认，不得脑补。")
    }

    private fun buildReferencePacket(index: Int, report: ReferenceDistillationReport, maxChars: Int): String {
        val sourceItems = allItems(report)
        val story = sourceItems.filter { it.kind == "STORY" }.sortedBy { storyPriority(it.dimension) }.take(24)
        val style = sourceItems.filter { it.kind == "STYLE" }.take(14)
        val transfer = sourceItems.filter { it.kind in TRANSFER_KINDS }.take(14)
        return buildString {
            appendLine()
            appendLine("--- ${index + 1}. 《${report.title}》 ---")
            appendLine("覆盖：${coverageLabel(report)}；${coverageDescription(report)}；可检索 DNA=${sourceItems.size}")
            if (story.isNotEmpty()) {
                appendLine("【Story DNA / 原作事实层】")
                story.forEach { item -> appendItem(item) }
            }
            if (report.overview.isNotBlank()) appendLine("【作品结构总览】${report.overview.take(1_100)}")
            if (report.summary.isNotBlank()) appendLine("【Style DNA 摘要】${report.summary.take(650)}")
            if (style.isNotEmpty()) {
                appendLine("【Style DNA / 写法层】")
                style.forEach { item -> appendItem(item) }
            }
            if (transfer.isNotEmpty()) {
                appendLine("【原创迁移边界】")
                transfer.forEach { item -> appendItem(item) }
            }
        }.take(maxChars)
    }

    private fun StringBuilder.appendItem(item: ReferenceDistillationReportItem) {
        append("${item.kind}/${item.dimension}: ${item.value.take(420)}")
        if (item.evidence.isNotBlank()) append(" [${item.evidence.take(90)}]")
        appendLine()
    }

    private fun rankItems(candidates: List<ReferenceDistillationReportItem>, terms: Set<String>, rawQuery: String): List<ReferenceDistillationReportItem> = candidates
        .map { it to scoreItem(it, terms, rawQuery) }
        .sortedWith(compareByDescending<Pair<ReferenceDistillationReportItem, Int>> { it.second }.thenBy { kindPriority(it.first.kind) }.thenBy { it.first.dimension })
        .map { it.first }
        .distinctBy(::itemKey)

    private fun fromStateChange(change: StateChange): ReferenceDistillationReportItem? {
        val kind = normalizeKind(change.subject.trim().uppercase()) ?: return null
        val value = change.after.trim().ifBlank { change.before.trim() }
        if (value.isBlank()) return null
        return ReferenceDistillationReportItem(kind, change.field.trim().ifBlank { kind }, value.take(560), change.evidence.trim().take(120))
    }

    private fun parseObservationItems(observation: String): List<ReferenceDistillationReportItem> = observation.lineSequence().map(String::trim).mapNotNull { line ->
        val colon = line.indexOf(':')
        val slash = line.indexOf('/')
        if (slash <= 0 || colon <= slash + 1) return@mapNotNull null
        val kind = normalizeKind(line.substring(0, slash).trim().uppercase()) ?: return@mapNotNull null
        val dimension = line.substring(slash + 1, colon).trim().ifBlank { kind }
        var value = line.substring(colon + 1).trim()
        if (value.isBlank()) return@mapNotNull null
        var evidence = ""
        val evidenceStart = value.lastIndexOf(" [")
        if (evidenceStart >= 0 && value.endsWith(']')) {
            evidence = value.substring(evidenceStart + 2, value.length - 1).trim()
            value = value.substring(0, evidenceStart).trim()
        }
        ReferenceDistillationReportItem(kind, dimension, value.take(560), evidence.take(120))
    }.toList()

    private fun allItems(report: ReferenceDistillationReport): List<ReferenceDistillationReportItem> =
        (report.retrievalItems.ifEmpty { report.items } + report.items).map(::normalizeItem).distinctBy(::itemKey)

    private fun normalizeItem(item: ReferenceDistillationReportItem): ReferenceDistillationReportItem =
        if (item.kind.equals("DNA", true)) item.copy(kind = "STYLE") else item.copy(kind = item.kind.uppercase())

    private fun itemKey(item: ReferenceDistillationReportItem): String = "${item.kind}|${item.dimension.uppercase()}|${normalizeForKey(item.value)}"

    private fun scoreItem(item: ReferenceDistillationReportItem, terms: Set<String>, rawQuery: String): Int {
        val haystack = "${item.kind} ${item.dimension} ${item.value}".lowercase()
        var score = terms.sumOf { term -> if (term.length >= 2 && haystack.contains(term)) 9 else 0 }
        val q = rawQuery.lowercase()
        val preferred = when {
            listOf("主角", "人物", "角色", "能力", "规则", "世界", "势力", "关系", "原作", "是谁", "叫什么").any(q::contains) -> setOf("STORY")
            listOf("文风", "写法", "氛围", "节奏", "对白", "悬念", "压迫感", "开头", "章末", "怎么写").any(q::contains) -> setOf("STYLE", "KEEP", "TRANSFORM", "AVOID")
            listOf("借鉴", "参考", "融合", "迁移", "设计", "创作", "场景", "正文", "主编").any(q::contains) -> setOf("KEEP", "TRANSFORM", "STYLE", "AVOID")
            else -> setOf("STYLE", "STORY", "KEEP", "TRANSFORM")
        }
        if (item.kind in preferred) score += 8
        if (item.dimension.lowercase() in terms) score += 5
        if (item.evidence.isNotBlank()) score += 1
        return score
    }

    private fun queryTerms(query: String): Set<String> {
        val normalized = query.lowercase().replace(Regex("[，。！？、,.!?;；:：()（）《》“”\\\"']"), " ")
        val terms = normalized.split(Regex("\\s+")).filter { it.length >= 2 }.toMutableSet()
        val cues = listOf("主角", "配角", "人物", "能力", "世界观", "世界", "规则", "关系", "势力", "地点", "冲突", "谜团", "主题", "剧情", "结局", "文风", "节奏", "对白", "悬念", "氛围", "结构", "开头", "章末", "塑造", "成长", "场景", "正文", "审稿", "主编")
        cues.filter { normalized.contains(it) }.forEach(terms::add)
        return terms
    }

    private fun normalizeKind(rawKind: String): String? = when (rawKind) {
        "DNA" -> "STYLE"
        "STYLE", "STORY", "KEEP", "TRANSFORM", "AVOID" -> rawKind
        else -> null
    }

    private fun kindPriority(kind: String): Int = when (kind) {
        "STORY" -> 0
        "KEEP" -> 1
        "TRANSFORM" -> 2
        "STYLE" -> 3
        "AVOID" -> 4
        else -> 9
    }

    private fun storyPriority(dimension: String): Int = when (dimension.trim().uppercase()) {
        "PROTAGONIST" -> 0
        "SUPPORTING" -> 1
        "RELATIONSHIP" -> 2
        "POWER" -> 3
        "WORLD" -> 4
        "RULE" -> 5
        "FACTION" -> 6
        "LOCATION" -> 7
        "CONFLICT" -> 8
        "MYSTERY" -> 9
        "ARC" -> 10
        "PROGRESSION" -> 11
        "THEME" -> 12
        else -> 50
    }

    fun loadOrArchiveFallback(taskId: String, title: String): ReferenceDistillationReport? {
        load(taskId)?.let { return it }
        val entry = CreationResearchArchiveStore(context).load().entries.firstOrNull { normalize(it.target) == normalize(title) } ?: return null
        val source = entry.sources.firstOrNull { it.url.startsWith("local://distillation/") || it.title.startsWith("[本地蒸馏]") } ?: return null
        return ReferenceDistillationReport(
            taskId = taskId,
            title = title.ifBlank { entry.target },
            chapters = 0,
            samples = 0,
            provider = "",
            model = "",
            createdAt = entry.updatedAt,
            overview = source.snippet,
            summary = source.detail,
            coverageGrade = "旧版摘要",
            coverageNote = "旧版没有保存批次级 DNA；只能作为高层摘要。重新蒸馏后会升级为 V2 可检索知识库。",
            legacySummaryOnly = true,
        )
    }

    fun delete(taskId: String) { runCatching { reportFile(taskId).delete() } }

    private fun ReferenceDistillationReport.normalizeKinds(): ReferenceDistillationReport = copy(
        items = items.map(::normalizeItem),
        retrievalItems = retrievalItems.map(::normalizeItem),
    )

    private fun write(report: ReferenceDistillationReport) {
        val file = AtomicFile(reportFile(report.taskId))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json.encodeToString(report).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (_: Throwable) {
            stream?.let(file::failWrite)
        }
    }

    private fun reportFile(taskId: String): File = File(root, "${safeId(taskId)}.json")
    private fun safeId(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
    private fun normalize(value: String): String = value.lowercase().replace(Regex("[《》“”\\\"'\\s·._—-]"), "")
    private fun normalizeForKey(value: String): String = value.lowercase().replace(Regex("[\\s，。！？、,.!?;；:：()（）《》“”\\\"'·._—-]"), "").take(200)

    private fun computeCoverageGrade(chapters: Int, samples: Int): String = when {
        chapters <= 0 || samples <= 0 -> "旧版摘要"
        samples >= chapters -> "高覆盖"
        samples >= 120 -> "深度覆盖"
        samples >= 80 -> "中高覆盖"
        samples >= 48 -> "中等覆盖"
        samples >= 28 -> "基础覆盖"
        else -> "低覆盖"
    }

    private fun computeCoverageNote(chapters: Int, samples: Int): String {
        if (chapters <= 0 || samples <= 0) return "缺少采样统计；现有内容只视为高层提示。"
        val percent = (samples * 100 / chapters.coerceAtLeast(1)).coerceIn(0, 100)
        return if (samples >= chapters) {
            "全书结构统计 $chapters 章，AI 深读全部有效章节；V2 同时保留最终总览与批次级可检索 DNA。"
        } else {
            "全书结构统计 $chapters 章，AI 深度分层阅读 $samples 章（约 $percent%）；V2 保留批次级 DNA，不再只剩最终摘要。"
        }
    }

    private companion object {
        const val MAX_FINAL_ITEMS = 96
        const val MAX_RETRIEVAL_ITEMS = 960
        val TRANSFER_KINDS = setOf("KEEP", "TRANSFORM", "AVOID")
    }
}
