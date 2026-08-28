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
    val items: List<ReferenceDistillationReportItem> = emptyList(),
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
    ): ReferenceDistillationReport {
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
            items = dossier.stateChanges.mapNotNull { change ->
                val rawKind = change.subject.trim().uppercase()
                val kind = when (rawKind) {
                    "DNA" -> "STYLE" // backward-compatible alias from pre-0.23 reports/prompts
                    "STYLE", "STORY", "KEEP", "TRANSFORM", "AVOID" -> rawKind
                    else -> return@mapNotNull null
                }
                val value = change.after.trim().ifBlank { change.before.trim() }
                if (value.isBlank()) return@mapNotNull null
                ReferenceDistillationReportItem(
                    kind = kind,
                    dimension = change.field.trim().ifBlank { kind },
                    value = value,
                    evidence = change.evidence.trim(),
                )
            }.take(56),
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

    /** All complete reports available for explicit new-book template selection. */
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
        report.items.any { it.kind.equals("STORY", ignoreCase = true) }

    /**
     * Build a compact prompt packet from *only* the reports explicitly selected by the user.
     * Unselected reports never enter the creation prompt.
     */
    fun promptContext(selectedTaskIds: List<String>, maxChars: Int = 11_000): String {
        if (selectedTaskIds.isEmpty()) return ""
        val selected = selectedTaskIds.distinct().mapNotNull(::load)
        if (selected.isEmpty()) return ""
        return buildString {
            appendLine("【用户显式选择的参考双层 DNA】")
            appendLine("只允许使用下列已选档案；未选择的蒸馏作品禁止自动混入。")
            appendLine("STYLE 是写法层：可参考视角、节奏、悬念、信息释放、场景切换等高层技术。")
            appendLine("STORY 是理解层：用于理解原作的主角定位、人物关系、世界观、规则/能力体系、势力、地点、冲突、剧情阶段与主题；这些内容只能作为结构分析，角色名、专名、具体能力、世界规则和剧情骨架必须重新原创，禁止换名照搬。")
            appendLine("覆盖等级代表采样可靠度，不等于逐章掌握全部剧情；低覆盖内容层事实必须降权处理，不能擅自补全。")
            selected.forEachIndexed { index, report ->
                appendLine()
                appendLine("--- ${index + 1}. 《${report.title}》 ---")
                appendLine("覆盖等级：${coverageLabel(report)}；${coverageDescription(report)}")
                if (report.summary.isNotBlank()) appendLine("Style DNA 摘要：${report.summary.take(900)}")
                if (report.overview.isNotBlank()) appendLine("Story DNA / 作品结构总览：${report.overview.take(1_500)}")

                val style = report.items.filter { it.kind == "STYLE" }.take(14)
                val story = report.items.filter { it.kind == "STORY" }.take(18)
                val transforms = report.items.filter { it.kind in setOf("KEEP", "TRANSFORM", "AVOID") }.take(12)

                if (style.isNotEmpty()) {
                    appendLine("【写法层】")
                    style.forEach { item -> appendLine("STYLE/${item.dimension}: ${item.value.take(360)}") }
                }
                if (story.isNotEmpty()) {
                    appendLine("【内容结构层，仅供理解与原创化转换】")
                    story.forEach { item -> appendLine("STORY/${item.dimension}: ${item.value.take(420)}") }
                }
                if (transforms.isNotEmpty()) {
                    appendLine("【迁移边界】")
                    transforms.forEach { item -> appendLine("${item.kind}/${item.dimension}: ${item.value.take(360)}") }
                }
            }
        }.take(maxChars)
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
        items = items.map { item ->
            if (item.kind.equals("DNA", ignoreCase = true)) item.copy(kind = "STYLE") else item
        }
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

    private fun computeCoverageGrade(chapters: Int, samples: Int): String = when {
        chapters <= 0 || samples <= 0 -> "旧版摘要"
        samples >= chapters -> "高覆盖"
        samples >= 64 -> "深度覆盖"
        samples >= 48 -> "中高覆盖"
        samples >= 32 -> "中等覆盖"
        samples >= 20 -> "基础覆盖"
        else -> "低覆盖"
    }

    private fun computeCoverageNote(chapters: Int, samples: Int): String {
        if (chapters <= 0 || samples <= 0) {
            return "缺少采样统计；只把现有摘要当作高层提示，不代表完整作品分析。"
        }
        val percent = (samples * 100 / chapters.coerceAtLeast(1)).coerceIn(0, 100)
        return if (samples >= chapters) {
            "全书结构统计 $chapters 章，AI 已覆盖全部可用章节；报告同时包含 Style DNA 与 Story DNA。"
        } else {
            "全书结构统计 $chapters 章，AI 深度分层阅读 $samples 章（约 $percent% 章节覆盖）；其余章节参与本地结构统计。Story DNA 是分层理解，不等于逐章复述。"
        }
    }
}
