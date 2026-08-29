package com.xiguli.langhuan.engine

import android.content.Context
import android.util.AtomicFile
import com.xiguli.langhuan.domain.GeneratedChapter
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
    /** Final high-level DNA produced by the final aggregation pass. */
    val items: List<ReferenceDistillationReportItem> = emptyList(),
    /** V2: retained batch-level DNA. This is the searchable long-form memory, not just a summary. */
    val retrievalItems: List<ReferenceDistillationReportItem> = emptyList(),
    val coverageGrade: String = "",
    val coverageNote: String = "",
    val legacySummaryOnly: Boolean = false,
)

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
        val finalItems = dossier.stateChanges.mapNotNull(::fromStateChange).take(72)
        val retained = retainedObservations
            .flatMap(::parseObservationItems)
            .plus(finalItems)
            .distinctBy { item ->
                "${item.kind}|${item.dimension}|${normalizeForKey(item.value)}"
            }
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
        allItems(report).any { it.kind.equals("STORY", ignoreCase = true) }

    fun retainedItemCount(report: ReferenceDistillationReport): Int = allItems(report).size

    /** Backward-compatible broad packet. New dialogue code should prefer searchContext(). */
    fun promptContext(selectedTaskIds: List<String>, maxChars: Int = 13_000): String {
        if (selectedTaskIds.isEmpty()) return ""
        val selected = selectedTaskIds.distinct().mapNotNull(::load)
        if (selected.isEmpty()) return ""
        val header = buildHeader()
        val remaining = (maxChars - header.length).coerceAtLeast(1_600)
        val perReportBudget = (remaining / selected.size.coerceAtLeast(1)).coerceIn(2_200, 7_500)
        val body = selected.mapIndexed { index, report -> buildReferencePacket(index, report, perReportBudget) }
            .joinToString("\n")
        return (header + body).take(maxChars)
    }

    /**
     * V2 active retrieval: choose DNA that is relevant to the current user turn instead of dumping
     * the whole report into every prompt. The scorer is intentionally local/deterministic so it works
     * with every AI provider and does not add another network call.
     */
    fun searchContext(
        selectedTaskIds: List<String>,
        query: String,
        maxChars: Int = 8_500,
        maxItemsPerReport: Int = 18,
    ): String {
        if (selectedTaskIds.isEmpty()) return ""
        val reports = selectedTaskIds.distinct().mapNotNull(::load)
        if (reports.isEmpty()) return ""
        val terms = queryTerms(query)
        val header = buildString {
            appendLine("【本轮主动检索的参考 DNA】")
            appendLine("以下条目来自用户显式选择的蒸馏档案，是本轮最相关的检索结果；回答前必须先利用这些条目。")
            appendLine("问原作事实时允许准确引用 STORY；设计新书时只迁移方法，不复用原作专名、具体能力规则或剧情骨架。")
        }
        val remaining = (maxChars - header.length).coerceAtLeast(1_500)
        val perReportBudget = (remaining / reports.size.coerceAtLeast(1)).coerceAtLeast(1_200)
        val blocks = reports.mapIndexed { index, report ->
            val candidates = allItems(report)
            val ranked = candidates
                .map { it to scoreItem(it, terms, query) }
                .sortedWith(compareByDescending<Pair<ReferenceDistillationReportItem, Int>> { it.second }
                    .thenBy { kindPriority(it.first.kind) })
                .map { it.first }
                .distinctBy { "${it.kind}|${it.dimension}|${normalizeForKey(it.value)}" }
                .take(maxItemsPerReport)
            buildString {
                appendLine("--- ${index + 1}. 《${report.title}》 · 命中 ${ranked.size} 条 / 库存 ${candidates.size} 条 ---")
                ranked.forEach { item ->
                    append("${item.kind}/${item.dimension}: ${item.value.take(420)}")
                    if (item.evidence.isNotBlank()) append(" [${item.evidence.take(90)}]")
                    appendLine()
                }
                if (report.summary.isNotBlank()) appendLine("STYLE_OVERVIEW: ${report.summary.take(420)}")
            }.take(perReportBudget)
        }
        return (header + blocks.joinToString("\n")).take(maxChars)
    }

    private fun buildHeader(): String = buildString {
        appendLine("【用户显式选择的参考双层 DNA】")
        appendLine("只允许读取下列已选档案；未选择的蒸馏作品禁止自动混入。")
        appendLine("重要：这里同时支持‘原作事实问答’与‘新书原创参考’两种用途。")
        appendLine("问原作事实时优先读取 STORY；创作自己的新书时使用 STYLE/KEEP/TRANSFORM/AVOID，并避免照搬原作专名、能力规则和剧情骨架。")
        appendLine("覆盖等级代表采样可靠度；报告没有确认的事实不得编造。")
    }

    private fun buildReferencePacket(index: Int, report: ReferenceDistillationReport, maxChars: Int): String {
        val sourceItems = allItems(report)
        val story = sourceItems.filter { it.kind == "STORY" }.sortedBy { storyPriority(it.dimension) }.take(28)
        val style = sourceItems.filter { it.kind == "STYLE" }.take(14)
        val transforms = sourceItems.filter { it.kind in setOf("KEEP", "TRANSFORM", "AVOID") }.take(12)
        return buildString {
            appendLine()
            appendLine("--- ${index + 1}. 《${report.title}》 ---")
            appendLine("覆盖等级：${coverageLabel(report)}；${coverageDescription(report)}；可检索 DNA ${sourceItems.size} 条")
            if (story.isNotEmpty()) {
                appendLine("【原作事实层 / STORY】")
                story.forEach { item ->
                    append("STORY/${item.dimension}: ${item.value.take(480)}")
                    if (item.evidence.isNotBlank()) append(" [${item.evidence.take(90)}]")
                    appendLine()
                }
            }
            if (report.overview.isNotBlank()) appendLine("【作品结构总览】${report.overview.take(1_100)}")
            if (report.summary.isNotBlank()) appendLine("【Style DNA 摘要】${report.summary.take(650)}")
            if (style.isNotEmpty()) {
                appendLine("【写法层】")
                style.forEach { item -> appendLine("STYLE/${item.dimension}: ${item.value.take(340)}") }
            }
            if (transforms.isNotEmpty()) {
                appendLine("【原创迁移边界】")
                transforms.forEach { item -> appendLine("${item.kind}/${item.dimension}: ${item.value.take(320)}") }
            }
        }.take(maxChars)
    }

    private fun fromStateChange(change: com.xiguli.langhuan.domain.StateChange): ReferenceDistillationReportItem? {
        val rawKind = change.subject.trim().uppercase()
        val kind = normalizeKind(rawKind) ?: return null
        val value = change.after.trim().ifBlank { change.before.trim() }
        if (value.isBlank()) return null
        return ReferenceDistillationReportItem(
            kind = kind,
            dimension = change.field.trim().ifBlank { kind },
            value = value,
            evidence = change.evidence.trim(),
        )
    }

    private fun parseObservationItems(observation: String): List<ReferenceDistillationReportItem> = observation
        .lineSequence()
        .map(String::trim)
        .mapNotNull { line ->
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
            ReferenceDistillationReportItem(kind, dimension, value.take(520), evidence.take(100))
        }
        .toList()

    private fun allItems(report: ReferenceDistillationReport): List<ReferenceDistillationReportItem> =
        (report.retrievalItems.ifEmpty { report.items } + report.items)
            .map { if (it.kind.equals("DNA", true)) it.copy(kind = "STYLE") else it }
            .distinctBy { "${it.kind}|${it.dimension}|${normalizeForKey(it.value)}" }

    private fun scoreItem(item: ReferenceDistillationReportItem, terms: Set<String>, rawQuery: String): Int {
        val haystack = "${item.kind} ${item.dimension} ${item.value}".lowercase()
        var score = terms.sumOf { term -> if (term.length >= 2 && haystack.contains(term)) 8 else 0 }
        val q = rawQuery.lowercase()
        val preferred = when {
            listOf("主角", "人物", "角色", "能力", "规则", "世界", "势力", "关系", "原作", "是谁", "叫什么").any(q::contains) -> setOf("STORY")
            listOf("文风", "写法", "氛围", "节奏", "对白", "悬念", "压迫感", "开头", "章末", "怎么写").any(q::contains) -> setOf("STYLE", "KEEP", "TRANSFORM")
            listOf("借鉴", "参考", "融合", "迁移", "设计", "创作").any(q::contains) -> setOf("KEEP", "TRANSFORM", "STYLE", "AVOID")
            else -> setOf("STYLE", "STORY", "KEEP", "TRANSFORM")
        }
        if (item.kind in preferred) score += 7
        if (item.dimension.lowercase() in terms) score += 5
        if (item.evidence.isNotBlank()) score += 1
        return score
    }

    private fun queryTerms(query: String): Set<String> {
        val normalized = query.lowercase().replace(Regex("[，。！？、,.!?;；:：()（）《》“”\"']"), " ")
        val terms = normalized.split(Regex("\\s+")).filter { it.length >= 2 }.toMutableSet()
        val cues = listOf("主角", "配角", "人物", "能力", "世界观", "世界", "规则", "关系", "势力", "地点", "冲突", "谜团", "主题", "剧情", "结局", "文风", "节奏", "对白", "悬念", "氛围", "结构", "开头", "章末", "塑造", "成长")
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
        val entry = CreationResearchArchiveStore(context).load().entries.firstOrNull {
            normalize(it.target) == normalize(title)
        } ?: return null
        val source = entry.sources.firstOrNull {
            it.url.startsWith("local://distillation/") || it.title.startsWith("[本地蒸馏]")
        } ?: return null
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
            coverageNote = "旧版本没有保存分层样本数量，也没有 Story DNA；只能作为写法层高层参考。",
            legacySummaryOnly = true,
        )
    }

    fun delete(taskId: String) {
        runCatching { reportFile(taskId).delete() }
    }

    private fun ReferenceDistillationReport.normalizeKinds(): ReferenceDistillationReport = copy(
        items = items.map { if (it.kind.equals("DNA", true)) it.copy(kind = "STYLE") else it },
        retrievalItems = retrievalItems.map { if (it.kind.equals("DNA", true)) it.copy(kind = "STYLE") else it },
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
    private fun normalizeForKey(value: String): String = value.lowercase().replace(Regex("[\\s，。！？、,.!?;；:：()（）《》“”\\\"'·._—-]"), "").take(180)

    private fun computeCoverageGrade(chapters: Int, samples: Int): String = when {
        chapters <= 0 || samples <= 0 -> "旧版摘要"
        samples >= chapters -> "高覆盖"
        samples >= 96 -> "深度覆盖"
        samples >= 64 -> "中高覆盖"
        samples >= 40 -> "中等覆盖"
        samples >= 24 -> "基础覆盖"
        else -> "低覆盖"
    }

    private fun computeCoverageNote(chapters: Int, samples: Int): String {
        if (chapters <= 0 || samples <= 0) return "缺少采样统计；只把现有摘要当作高层提示，不代表完整作品分析。"
        val percent = (samples * 100 / chapters.coerceAtLeast(1)).coerceIn(0, 100)
        return if (samples >= chapters) {
            "全书结构统计 $chapters 章，AI 已覆盖全部可用章节；V2 同时保留最终总览与批次级可检索 DNA。"
        } else {
            "全书结构统计 $chapters 章，AI 深度分层阅读 $samples 章（约 $percent% 章节覆盖）；V2 会保留批次级 DNA，不再只剩最终摘要。"
        }
    }

    private companion object {
        const val MAX_RETRIEVAL_ITEMS = 640
    }
}
