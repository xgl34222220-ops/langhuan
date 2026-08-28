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
            selected.forEachIndexed { index, report ->
                appendLine()
                appendLine("--- ${index + 1}. 《${report.title}》 ---")
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
            legacySummaryOnly = true,
        )
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
}
