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

/** Full, user-visible Style DNA report storage. */
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
                val kind = change.subject.trim().uppercase()
                val value = change.after.trim().ifBlank { change.before.trim() }
                if (kind !in setOf("DNA", "KEEP", "TRANSFORM", "AVOID") || value.isBlank()) return@mapNotNull null
                ReferenceDistillationReportItem(
                    kind = kind,
                    dimension = change.field.trim().ifBlank { kind },
                    value = value,
                    evidence = change.evidence.trim(),
                )
            }.take(32),
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
                json.decodeFromString<ReferenceDistillationReport>(reader.readText())
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
                    json.decodeFromString<ReferenceDistillationReport>(reader.readText())
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

    /**
     * Build a compact prompt packet from *only* the reports explicitly selected by the user.
     * Unselected reports never enter the creation prompt.
     */
    fun promptContext(selectedTaskIds: List<String>, maxChars: Int = 9_000): String {
        if (selectedTaskIds.isEmpty()) return ""
        val selected = selectedTaskIds.distinct().mapNotNull(::load)
        if (selected.isEmpty()) return ""
        return buildString {
            appendLine("【用户显式选择的参考 Style DNA】")
            appendLine("只能使用下列已选择档案；未选择的蒸馏作品禁止自动混入。只借鉴高层写作机制，角色、专名、剧情骨架和标志性表达必须原创。")
            appendLine("覆盖等级只代表风格/结构采样的可靠程度，不代表已经逐章读懂全部剧情。低覆盖或旧版摘要只允许用于稳定的高层节奏、视角、信息释放等特征，禁止据此编造具体人物、能力或剧情事实。")
            selected.forEachIndexed { index, report ->
                appendLine()
                appendLine("--- ${index + 1}. 《${report.title}》 ---")
                appendLine("覆盖等级：${coverageLabel(report)}；${coverageDescription(report)}")
                if (report.summary.isNotBlank()) appendLine("Style DNA：${report.summary.take(900)}")
                if (report.overview.isNotBlank()) appendLine("高层档案：${report.overview.take(1_200)}")
                report.items.take(16).forEach { item ->
                    appendLine("${item.kind}/${item.dimension}: ${item.value.take(360)}")
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
            coverageNote = "旧版本没有保存分层样本数量，只能作为高层参考，不能据此推断具体剧情事实。",
            legacySummaryOnly = true,
        )
    }

    fun delete(taskId: String) {
        runCatching { reportFile(taskId).delete() }
    }

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
        samples >= 36 -> "中高覆盖"
        samples >= 24 -> "中等覆盖"
        samples >= 12 -> "基础覆盖"
        else -> "低覆盖"
    }

    private fun computeCoverageNote(chapters: Int, samples: Int): String {
        if (chapters <= 0 || samples <= 0) {
            return "缺少采样统计；只把现有摘要当作高层提示，不代表完整作品分析。"
        }
        val percent = (samples * 100 / chapters.coerceAtLeast(1)).coerceIn(0, 100)
        return if (samples >= chapters) {
            "全书结构统计 $chapters 章，AI 已覆盖全部可用章节；仍只提炼高层技法，不保存或复刻原文。"
        } else {
            "全书结构统计 $chapters 章，AI 分层阅读 $samples 章（约 $percent% 章节覆盖）；适合参考稳定风格与结构规律，不代表逐章剧情完整覆盖。"
        }
    }
}
