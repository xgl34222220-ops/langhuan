package com.xiguli.langhuan.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class GenerationRequest(
    val snapshot: StorySnapshot,
    val chapter: ChapterDraft,
    val targetWords: Int,
    val extraInstruction: String = "",
)

/**
 * AI 的结构化输出属于“非可信外部输入”。除正文 content 外，辅助字段允许模型漏填、
 * 返回 null 或空值，由业务层再决定是否可用。这样不同模型/中转站的 JSON 小差异
 * 不会让生成、复盘或参考小说蒸馏整条链直接崩溃。
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

@Serializable
private data class GeneratedChapterWire(
    val title: String? = null,
    val content: String? = null,
    val summary: String? = null,
    val stateChanges: List<StateChangeWire?>? = null,
    val touchedForeshadowingIds: List<String?>? = null,
)

@Serializable
private data class StateChangeWire(
    val subject: String? = null,
    val field: String? = null,
    val before: String? = null,
    val after: String? = null,
    val evidence: String? = null,
)

object GeneratedChapterSerializer : KSerializer<GeneratedChapter> {
    private val delegate = GeneratedChapterWire.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): GeneratedChapter {
        val wire = decoder.decodeSerializableValue(delegate)
        return GeneratedChapter(
            title = wire.title.orEmpty(),
            content = wire.content.orEmpty(),
            summary = wire.summary.orEmpty(),
            stateChanges = wire.stateChanges.orEmpty().mapNotNull { change ->
                change?.let {
                    StateChange(
                        subject = it.subject.orEmpty(),
                        field = it.field.orEmpty(),
                        before = it.before.orEmpty(),
                        after = it.after.orEmpty(),
                        evidence = it.evidence.orEmpty(),
                    )
                }
            },
            touchedForeshadowingIds = wire.touchedForeshadowingIds.orEmpty().mapNotNull { it },
        )
    }

    override fun serialize(encoder: Encoder, value: GeneratedChapter) {
        encoder.encodeSerializableValue(
            delegate,
            GeneratedChapterWire(
                title = value.title,
                content = value.content,
                summary = value.summary,
                stateChanges = value.stateChanges.map { change ->
                    StateChangeWire(
                        subject = change.subject,
                        field = change.field,
                        before = change.before,
                        after = change.after,
                        evidence = change.evidence,
                    )
                },
                touchedForeshadowingIds = value.touchedForeshadowingIds,
            ),
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
