package com.xiguli.langhuan.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Serializable
data class GenerationRequest(
    val snapshot: StorySnapshot,
    val chapter: ChapterDraft,
    val targetWords: Int,
    val extraInstruction: String = "",
)

/**
 * AI 的结构化输出属于“非可信外部输入”。
 *
 * 不同模型/中转站经常会返回：
 * - null；
 * - 数字/布尔值代替字符串；
 * - stateChanges 单对象而不是数组；
 * - snake_case 字段；
 * - result/data/chapter 外包一层；
 * - 用 kind/name/value/detail 等同义字段。
 *
 * 这里统一做宽容归一化，业务层再判断内容质量。这样格式小差异不会把
 * 建书蓝图、正文生成、Agent 复盘或参考小说蒸馏整条链直接炸掉。
 */
@Serializable(with = GeneratedChapterSerializer::class)
data class GeneratedChapter(
    val title: String = "",
    val content: String = "",
    val summary: String = "",
    val stateChanges: List<StateChange> = emptyList(),
    val touchedForeshadowingIds: List<String> = emptyList(),
)

@Serializable
data class StateChange(
    val subject: String = "",
    val field: String = "",
    val before: String = "",
    val after: String = "",
    val evidence: String = "",
)

object GeneratedChapterSerializer : KSerializer<GeneratedChapter> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("GeneratedChapter")

    override fun deserialize(decoder: Decoder): GeneratedChapter {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("GeneratedChapter 只支持 JSON 反序列化")
        val root = unwrap(jsonDecoder.decodeJsonElement())
        return GeneratedChapter(
            title = root.stringAny("title", "name", "bookTitle", "book_title"),
            content = root.stringAny("content", "text", "premise", "synopsis", "body"),
            summary = root.stringAny("summary", "abstract", "rationale", "note"),
            stateChanges = parseStateChanges(
                root.firstAny("stateChanges", "state_changes", "changes", "states", "items")
            ),
            touchedForeshadowingIds = parseStringList(
                root.firstAny(
                    "touchedForeshadowingIds",
                    "touched_foreshadowing_ids",
                    "foreshadowingIds",
                    "foreshadowing_ids",
                )
            ),
        )
    }

    override fun serialize(encoder: Encoder, value: GeneratedChapter) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("GeneratedChapter 只支持 JSON 序列化")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("title", value.title)
                put("content", value.content)
                put("summary", value.summary)
                put(
                    "stateChanges",
                    buildJsonArray {
                        value.stateChanges.forEach { change ->
                            add(
                                buildJsonObject {
                                    put("subject", change.subject)
                                    put("field", change.field)
                                    put("before", change.before)
                                    put("after", change.after)
                                    put("evidence", change.evidence)
                                }
                            )
                        }
                    },
                )
                put(
                    "touchedForeshadowingIds",
                    buildJsonArray { value.touchedForeshadowingIds.forEach { add(JsonPrimitive(it)) } },
                )
            }
        )
    }

    private fun unwrap(element: JsonElement): JsonObject {
        val root = element as? JsonObject ?: return JsonObject(emptyMap())
        if (root.hasChapterKeys()) return root
        val wrappers = listOf("result", "data", "chapter", "output", "response")
        wrappers.forEach { key ->
            val nested = root[key] as? JsonObject
            if (nested != null && nested.hasChapterKeys()) return nested
        }
        return root
    }

    private fun JsonObject.hasChapterKeys(): Boolean =
        keys.any {
            it in setOf(
                "title", "content", "summary", "stateChanges", "state_changes",
                "changes", "touchedForeshadowingIds", "touched_foreshadowing_ids"
            )
        }

    private fun JsonObject.firstAny(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { this[it] }

    private fun JsonObject.stringAny(vararg keys: String): String =
        elementToString(firstAny(*keys))

    private fun elementToString(element: JsonElement?): String = when (element) {
        null, JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }.trim()

    private fun parseStringList(element: JsonElement?): List<String> = when (element) {
        null, JsonNull -> emptyList()
        is JsonArray -> element.map(::elementToString).filter(String::isNotBlank)
        is JsonPrimitive -> element.contentOrNull
            .orEmpty()
            .split(Regex("[,，;；\\n]"))
            .map(String::trim)
            .filter(String::isNotBlank)
        else -> emptyList()
    }

    private fun parseStateChanges(element: JsonElement?): List<StateChange> {
        val objects: List<Pair<String?, JsonObject>> = when (element) {
            null, JsonNull -> emptyList()
            is JsonArray -> element.mapNotNull { item ->
                (item as? JsonObject)?.let { null to it }
            }
            is JsonObject -> {
                if (element.looksLikeStateChange()) {
                    listOf(null to element)
                } else {
                    element.mapNotNull { (key, value) ->
                        (value as? JsonObject)?.let { key to it }
                    }
                }
            }
            else -> emptyList()
        }

        return objects.mapNotNull { (mapKey, obj) ->
            val subject = obj.stringAny("subject", "kind", "type", "category", "scope")
                .ifBlank { mapKey.orEmpty() }
            val field = obj.stringAny("field", "name", "dimension", "key", "title")
            val before = obj.stringAny("before", "old", "from", "objective", "description")
            val after = obj.stringAny("after", "new", "to", "value", "result", "conflict")
            val evidence = obj.stringAny(
                "evidence", "detail", "note", "notes", "turningPoint", "turning_point", "proof"
            )
            if (listOf(subject, field, before, after, evidence).all(String::isBlank)) {
                null
            } else {
                StateChange(
                    subject = subject,
                    field = field,
                    before = before,
                    after = after,
                    evidence = evidence,
                )
            }
        }
    }

    private fun JsonObject.looksLikeStateChange(): Boolean =
        keys.any {
            it in setOf(
                "subject", "kind", "type", "category", "field", "name", "dimension",
                "before", "after", "evidence", "value", "detail", "turningPoint"
            )
        }
}

@Serializable
enum class IssueSeverity { INFO, WARNING, BLOCKING }

@Serializable
data class ConsistencyIssue(
    val severity: IssueSeverity,
    val code: String,
    val message: String,
    val evidence: String = "",
    val repairInstruction: String,
)

@Serializable
data class GenerationResult(
    val chapter: GeneratedChapter,
    val issues: List<ConsistencyIssue>,
) {
    val canCommit: Boolean
        get() = issues.none { it.severity == IssueSeverity.BLOCKING }
}
