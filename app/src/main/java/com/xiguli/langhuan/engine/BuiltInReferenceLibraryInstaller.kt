package com.xiguli.langhuan.engine

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Installs derivative Reference DNA built from user-provided EPUBs.
 *
 * The packaged asset contains no novel prose. It stores high-level core DNA plus one
 * compact row per parsed chapter: broad semantic tags and high-frequency entity hits.
 * This gives every chapter a searchable footprint without redistributing the source text.
 */
object BuiltInReferenceLibraryInstaller {
    private const val ASSET_DIR = "reference_builtin"
    private const val LIBRARY_VERSION = "2026-08-29-1"
    private const val PINNED_CREATED_AT = 4_102_444_800_000L // 2100-01-01, keeps built-ins visible first.

    private val tags = listOf(
        "副本/任务",
        "规则",
        "灵异",
        "能力/成长",
        "世界/组织",
        "谜团/线索",
        "时间/空间",
    )

    fun install(context: Context) {
        runCatching {
            val chunkNames = context.assets.list(ASSET_DIR)
                .orEmpty()
                .filter { it.endsWith(".dat") }
                .sorted()
            if (chunkNames.isEmpty()) return@runCatching

            val encoded = buildString {
                chunkNames.forEach { name ->
                    context.assets.open("$ASSET_DIR/$name").bufferedReader(Charsets.UTF_8).use { reader ->
                        append(reader.readText().trim())
                    }
                }
            }
            val compressed = Base64.decode(encoded, Base64.DEFAULT)
            val raw = GZIPInputStream(ByteArrayInputStream(compressed))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val books = Json.parseToJsonElement(raw).jsonArray

            val root = File(context.filesDir, "distillation/reports").apply { mkdirs() }
            val marker = File(context.filesDir, "distillation/builtin-reference-$LIBRARY_VERSION.ready")
            val expectedFiles = books.map { element ->
                val id = element.jsonObject.getValue("id").jsonPrimitive.content
                File(root, "${safeId(id)}.json")
            }
            if (marker.exists() && expectedFiles.all(File::exists)) return@runCatching

            books.forEachIndexed { bookIndex, element ->
                val book = element.jsonObject
                val taskId = book.getValue("id").jsonPrimitive.content
                val title = book.getValue("title").jsonPrimitive.content
                val entities = book.getValue("entities").jsonArray.map { it.jsonPrimitive.content }
                val core = book.getValue("core").jsonArray.map { coreElement ->
                    val parts = coreElement.jsonArray
                    ReferenceDistillationReportItem(
                        kind = parts[0].jsonPrimitive.content,
                        dimension = parts[1].jsonPrimitive.content,
                        value = parts[2].jsonPrimitive.content,
                        evidence = "用户提供 EPUB · 全书结构化提取",
                    )
                }
                val rows = book.getValue("rows").jsonArray
                val chapterTraces = rows.mapIndexed { index, rowElement ->
                    val row = rowElement.jsonArray
                    val tagMask = row[0].jsonPrimitive.int
                    val entityMask = row[1].jsonPrimitive.long
                    val matchedTags = tags.mapIndexedNotNull { bit, label ->
                        if ((tagMask and (1 shl bit)) != 0) label else null
                    }
                    val matchedEntities = entities.mapIndexedNotNull { bit, name ->
                        if ((entityMask and (1L shl bit)) != 0L) name else null
                    }
                    ReferenceDistillationReportItem(
                        kind = "STORY",
                        dimension = "CHAPTER_TRACE",
                        value = buildString {
                            append("第${index + 1}章")
                            if (matchedTags.isNotEmpty()) append("；标签=${matchedTags.joinToString("/")}")
                            if (matchedEntities.isNotEmpty()) append("；实体=${matchedEntities.joinToString("/")}")
                        },
                        evidence = "内置全章索引",
                    )
                }
                val overview = core.firstOrNull {
                    it.dimension in setOf("CORE_SYSTEM", "CORE_CURSE", "CORE_SPACE", "WORLD_RULE")
                }?.value ?: core.firstOrNull()?.value.orEmpty()

                val report = ReferenceDistillationReport(
                    taskId = taskId,
                    title = title,
                    chapters = rows.size,
                    samples = rows.size,
                    provider = "内置 Reference Library",
                    model = "用户提供 EPUB · 全章结构索引",
                    createdAt = PINNED_CREATED_AT - bookIndex,
                    overview = overview,
                    summary = "内置高密度参考：核心 Story/Style DNA + 全部有效章节的结构标签与高频实体索引。用于检索、建书与原创迁移，不保存小说原文正文。",
                    localMetrics = "全章索引=${rows.size}；核心 DNA=${core.size}；高频实体=${entities.size}",
                    items = core,
                    retrievalItems = core + chapterTraces,
                    coverageGrade = "内置全书索引",
                    coverageNote = "基于用户提供 EPUB 对全部有效章节建立副本/任务、规则、灵异、成长、组织、谜团、时空标签与高频实体索引；原文正文不进入内置数据库。",
                )
                writeReport(root, report)
            }
            marker.parentFile?.mkdirs()
            marker.writeText("ok", Charsets.UTF_8)
        }
    }

    private fun writeReport(root: File, report: ReferenceDistillationReport) {
        val file = AtomicFile(File(root, "${safeId(report.taskId)}.json"))
        val json = Json {
            encodeDefaults = true
            explicitNulls = false
        }
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(json.encodeToString(report).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (_: Throwable) {
            stream?.let(file::failWrite)
        }
    }

    private fun safeId(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)
}
