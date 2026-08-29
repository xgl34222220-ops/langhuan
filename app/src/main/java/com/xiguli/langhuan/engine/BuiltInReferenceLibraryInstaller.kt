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
 * compact row per parsed chapter. During installation those full-book rows are expanded
 * into browseable character, relation, instance, rule, progression, mystery and world
 * structure indexes, so the built-in library is useful beyond a flat chapter-tag list.
 */
object BuiltInReferenceLibraryInstaller {
    private const val ASSET_DIR = "reference_builtin"
    private const val LIBRARY_VERSION = "2026-08-30-3"
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

            // v1 used Chinese task ids. ReferenceDistillationReportStore.safeId() replaces every
            // non-ASCII character with '_', so six built-ins collapsed into only two filenames.
            listOf("builtin_____.json", "builtin________.json").forEach { legacy ->
                runCatching { File(root, legacy).delete() }
            }

            val marker = File(context.filesDir, "distillation/builtin-reference-$LIBRARY_VERSION.ready")
            val expectedFiles = books.map { element ->
                val title = element.jsonObject.getValue("title").jsonPrimitive.content
                File(root, "${safeId(stableTaskId(title))}.json")
            }
            if (marker.exists() && expectedFiles.all(File::exists)) return@runCatching

            books.forEachIndexed { bookIndex, element ->
                val book = element.jsonObject
                val title = book.getValue("title").jsonPrimitive.content
                val taskId = stableTaskId(title)
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
                val tagMasks = IntArray(rows.size)
                val entityMasks = LongArray(rows.size)
                rows.forEachIndexed { index, rowElement ->
                    val row = rowElement.jsonArray
                    tagMasks[index] = row[0].jsonPrimitive.int
                    entityMasks[index] = row[1].jsonPrimitive.long
                }

                val chapterTraces = rows.indices.map { index ->
                    val tagMask = tagMasks[index]
                    val entityMask = entityMasks[index]
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
                            if (matchedTags.isNotEmpty()) append("；结构=${matchedTags.joinToString("/")}")
                            if (matchedEntities.isNotEmpty()) append("；人物/实体=${matchedEntities.joinToString("/")}")
                        },
                        evidence = "内置全章结构索引",
                    )
                }

                val derived = buildDerivedItems(entities, tagMasks, entityMasks)
                val retrieval = (core + derived + chapterTraces)
                    .distinctBy { listOf(it.kind, it.dimension, it.value, it.evidence).joinToString("|") }
                val overview = core.firstOrNull {
                    it.dimension in setOf("CORE_SYSTEM", "CORE_CURSE", "CORE_SPACE", "WORLD_RULE")
                }?.value ?: core.firstOrNull()?.value.orEmpty()

                val report = ReferenceDistillationReport(
                    taskId = taskId,
                    title = title,
                    chapters = rows.size,
                    samples = rows.size,
                    provider = "内置 Reference Library",
                    model = "用户提供 EPUB · 全章结构图谱",
                    createdAt = PINNED_CREATED_AT - bookIndex,
                    overview = overview,
                    summary = "内置参考库：核心 Story/Style DNA + 全章结构索引 + 人物/实体共现关系 + 副本、规则、成长、谜团、世界与异常事件结构段。用于检索、建书与原创迁移，不保存小说原文正文。",
                    localMetrics = "全章=${rows.size}；核心 DNA=${core.size}；人物/实体=${entities.size}；结构图谱=${derived.size}；可检索=${retrieval.size}",
                    items = core,
                    retrievalItems = retrieval,
                    coverageGrade = "内置全书结构图谱",
                    coverageNote = "基于用户提供 EPUB 的全部有效章节建立结构与实体索引，并按全书共现关系聚合人物、关系、副本/任务、规则、成长、谜团、世界/组织、异常事件与时空段。章节结构图谱用于定位与检索，不冒充逐章剧情摘要。",
                )
                writeReport(root, report)
            }
            marker.parentFile?.mkdirs()
            marker.writeText("ok", Charsets.UTF_8)
        }
    }

    private fun buildDerivedItems(
        entities: List<String>,
        tagMasks: IntArray,
        entityMasks: LongArray,
    ): List<ReferenceDistillationReportItem> {
        val out = mutableListOf<ReferenceDistillationReportItem>()
        if (tagMasks.isEmpty()) return out

        fun entityChapters(entityIndex: Int): List<Int> = entityMasks.indices.filter { chapter ->
            (entityMasks[chapter] and (1L shl entityIndex)) != 0L
        }

        fun compactChapters(chapters: List<Int>, limit: Int = 8): String {
            if (chapters.isEmpty()) return "无"
            val oneBased = chapters.map { it + 1 }
            if (oneBased.size <= limit) return oneBased.joinToString("、") { "第${it}章" }
            val head = oneBased.take(limit / 2)
            val tail = oneBased.takeLast(limit / 2)
            return (head + tail).joinToString("、") { "第${it}章" } + " 等"
        }

        val chaptersByEntity = entities.indices.associateWith(::entityChapters)

        entities.forEachIndexed { index, name ->
            val chapters = chaptersByEntity[index].orEmpty()
            if (chapters.isEmpty()) return@forEachIndexed
            val co = entities.indices
                .asSequence()
                .filter { it != index }
                .map { other ->
                    val count = chapters.count { chapter -> (entityMasks[chapter] and (1L shl other)) != 0L }
                    other to count
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(5)
                .toList()
            val coText = co.joinToString("、") { (other, count) -> "${entities[other]}($count)" }.ifBlank { "暂无高频共现" }
            out += ReferenceDistillationReportItem(
                kind = "STORY",
                dimension = "CHARACTER",
                value = "$name｜全书命中 ${chapters.size} 章｜首次第${chapters.first() + 1}章｜末次第${chapters.last() + 1}章｜高频共现：$coText｜代表章节：${compactChapters(chapters)}",
                evidence = "内置全章人物/实体索引聚合",
            )
        }

        data class RelationHit(val a: Int, val b: Int, val chapters: List<Int>)
        val relationThreshold = when {
            tagMasks.size >= 1200 -> 5
            tagMasks.size >= 700 -> 4
            else -> 3
        }
        val relationHits = mutableListOf<RelationHit>()
        for (a in entities.indices) {
            for (b in (a + 1) until entities.size) {
                val chapters = entityMasks.indices.filter { chapter ->
                    val mask = entityMasks[chapter]
                    (mask and (1L shl a)) != 0L && (mask and (1L shl b)) != 0L
                }
                if (chapters.size >= relationThreshold) relationHits += RelationHit(a, b, chapters)
            }
        }
        relationHits.sortedByDescending { it.chapters.size }.take(80).forEach { hit ->
            out += ReferenceDistillationReportItem(
                kind = "STORY",
                dimension = "RELATIONSHIP",
                value = "${entities[hit.a]} ↔ ${entities[hit.b]}｜同章共现 ${hit.chapters.size} 次｜共现章节：${compactChapters(hit.chapters)}。这是关系检索入口，具体关系性质需结合对应章节/更深语义蒸馏判断。",
                evidence = "内置全章实体共现图谱",
            )
        }

        fun segmentsForBit(bit: Int, bridgeGap: Int = 1): List<IntRange> {
            val hits = tagMasks.indices.filter { (tagMasks[it] and (1 shl bit)) != 0 }
            if (hits.isEmpty()) return emptyList()
            val segments = mutableListOf<IntRange>()
            var start = hits.first()
            var last = hits.first()
            hits.drop(1).forEach { current ->
                if (current - last <= bridgeGap + 1) {
                    last = current
                } else {
                    segments += start..last
                    start = current
                    last = current
                }
            }
            segments += start..last
            return segments
        }

        fun topEntities(range: IntRange, limit: Int = 8): List<Pair<String, Int>> = entities.indices
            .map { index ->
                val count = range.count { chapter -> (entityMasks[chapter] and (1L shl index)) != 0L }
                entities[index] to count
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)

        fun rangeLabel(range: IntRange): String =
            if (range.first == range.last) "第${range.first + 1}章" else "第${range.first + 1}—${range.last + 1}章"

        fun addSegments(
            bit: Int,
            dimension: String,
            label: String,
            maxItems: Int = 120,
            bridgeGap: Int = 1,
        ) {
            segmentsForBit(bit, bridgeGap)
                .sortedByDescending { it.last - it.first }
                .take(maxItems)
                .sortedBy { it.first }
                .forEach { range ->
                    val people = topEntities(range).joinToString("、") { (name, count) -> "$name($count)" }.ifBlank { "未命中高频实体" }
                    out += ReferenceDistillationReportItem(
                        kind = "STORY",
                        dimension = dimension,
                        value = "${rangeLabel(range)}｜$label｜关联人物/实体：$people",
                        evidence = "内置全章结构信号聚合",
                    )
                }
        }

        // 副本/任务单独展开为多维入口，避免浏览器中的“副本”分类长期为 0。
        segmentsForBit(0, bridgeGap = 2)
            .sortedByDescending { it.last - it.first }
            .take(120)
            .sortedBy { it.first }
            .forEach { range ->
                val people = topEntities(range).joinToString("、") { (name, count) -> "$name($count)" }.ifBlank { "未命中高频实体" }
                out += ReferenceDistillationReportItem(
                    kind = "STORY",
                    dimension = "INSTANCE",
                    value = "${rangeLabel(range)}｜副本/任务结构段｜主要参与人物/实体：$people",
                    evidence = "内置全章副本/任务结构聚合",
                )
                if (range.any { chapter -> (tagMasks[chapter] and (1 shl 1)) != 0 }) {
                    out += ReferenceDistillationReportItem(
                        kind = "STORY",
                        dimension = "INSTANCE_RULE",
                        value = "${rangeLabel(range)}｜该副本/任务段同时命中规则/限制信号；可从对应章节继续检索具体规则。",
                        evidence = "副本段 × 规则信号交叉索引",
                    )
                }
                if (range.any { chapter -> (tagMasks[chapter] and (1 shl 5)) != 0 }) {
                    out += ReferenceDistillationReportItem(
                        kind = "STORY",
                        dimension = "INSTANCE_CLUE",
                        value = "${rangeLabel(range)}｜该副本/任务段同时命中谜团/线索信号；可从对应章节继续追踪线索链。",
                        evidence = "副本段 × 谜团/线索信号交叉索引",
                    )
                }
                if (people != "未命中高频实体") {
                    out += ReferenceDistillationReportItem(
                        kind = "STORY",
                        dimension = "INSTANCE_NPC",
                        value = "${rangeLabel(range)}｜参与/相关人物实体：$people",
                        evidence = "副本段 × 人物实体共现索引",
                    )
                }
            }

        addSegments(1, "RULE", "规则/限制相关结构信号集中出现")
        addSegments(2, "EVENT", "异常/灵异事件信号集中出现")
        addSegments(3, "PROGRESSION", "能力/成长变化信号集中出现")
        addSegments(4, "WORLD", "世界观/组织相关信息集中出现")
        addSegments(5, "MYSTERY", "谜团/线索推进信号集中出现")
        addSegments(6, "TIMELINE", "时间/空间信息集中出现")

        return out
    }

    private fun stableTaskId(title: String): String = when (title) {
        "怪谈玩家" -> "builtin:guaitan-wanjia"
        "惊惧盛宴" -> "builtin:jingju-shengyan"
        "惊悚乐园" -> "builtin:jingsong-leyuan"
        "迷雾之上" -> "builtin:miwu-zhishang"
        "神秘复苏" -> "builtin:shenmi-fusu"
        "我有一座冒险屋" -> "builtin:maoxianwu"
        else -> "builtin:${title.hashCode().toUInt().toString(16)}"
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
