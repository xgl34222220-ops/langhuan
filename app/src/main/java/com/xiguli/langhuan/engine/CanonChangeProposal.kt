package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.FactProvenance
import com.xiguli.langhuan.domain.Foreshadowing
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.OutlineNode
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
enum class CanonPatchTargetType {
    NOVEL,
    BIBLE,
    CHARACTER,
    OUTLINE,
    TIMELINE,
    FORESHADOW,
    KNOWLEDGE,
}

@Serializable
enum class CanonChangeRisk { LOW, MEDIUM, HIGH }

@Serializable
data class CanonChangePatch(
    val targetType: CanonPatchTargetType,
    val targetId: String,
    val targetLabel: String = "",
    val field: String,
    val before: String = "",
    val after: String,
    val reason: String = "",
    val risk: CanonChangeRisk = CanonChangeRisk.MEDIUM,
)

@Serializable
private data class CanonChangeAiPayload(
    val summary: String = "",
    val patches: List<CanonChangePatch> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class CanonChangeImpact(
    val scope: String,
    val label: String,
    val detail: String,
    val chapterNumber: Int? = null,
)

data class CanonChangeProposal(
    val id: String = UUID.randomUUID().toString(),
    val request: String,
    val summary: String,
    val patches: List<CanonChangePatch>,
    val impacts: List<CanonChangeImpact>,
    val warnings: List<String>,
)

/**
 * V7 proposal builder. AI may only propose scalar patches. It never receives a write-capable store.
 * Every patch is re-bound to the current StorySnapshot locally before it can reach the confirmer.
 */
object CanonChangeProposalEngine {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun propose(
        gateway: AiGateway,
        snapshot: StorySnapshot,
        drafts: List<ChapterDraft>,
        request: String,
    ): CanonChangeProposal {
        val raw = gateway.generateText(
            PromptBundle(
                system = proposalSystem(snapshot),
                user = request.trim(),
                jsonMode = true,
            )
        )
        val payload = decodePayload(raw)
        val normalized = CanonPatchEngine.normalize(snapshot, payload.patches)
        require(normalized.isNotEmpty()) {
            payload.warnings.firstOrNull().orEmpty().ifBlank {
                "没有生成可安全预览的 Canon 变更。当前 V7 只支持修改已有小说元信息、Bible、人物状态、总纲/卷纲、时间线、伏笔和信息边界的明确字段。"
            }
        }
        return CanonChangeProposal(
            request = request.trim(),
            summary = payload.summary.ifBlank { "准备修改 ${normalized.size} 项已确认项目事实" },
            patches = normalized,
            impacts = CanonImpactAnalyzer.analyze(snapshot, drafts, normalized),
            warnings = payload.warnings.distinct(),
        )
    }

    private fun decodePayload(raw: String): CanonChangeAiPayload {
        val clean = raw.trim()
        val candidate = clean
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
            .let { text ->
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start >= 0 && end > start) text.substring(start, end + 1) else text
            }
        return runCatching { json.decodeFromString<CanonChangeAiPayload>(candidate) }
            .getOrElse { error("AI 变更提案不是有效 JSON，请重试：${it.message.orEmpty()}") }
    }

    private fun proposalSystem(snapshot: StorySnapshot): String = buildString {
        appendLine("你是琅嬛 Novel Skill OS V7 的 Canon 变更规划器。你只能输出提案，不得声称已经修改项目。")
        appendLine("目标：把用户明确的设定修改拆成最小数量的标量字段 patch。不要顺手重写无关内容。")
        appendLine("输出严格 JSON：{\"summary\":\"...\",\"patches\":[{\"targetType\":\"BIBLE\",\"targetId\":\"...\",\"targetLabel\":\"...\",\"field\":\"content\",\"before\":\"...\",\"after\":\"...\",\"reason\":\"...\",\"risk\":\"HIGH\"}],\"warnings\":[\"...\"]}")
        appendLine("允许 targetType/field：")
        appendLine("NOVEL: title, genre, premise, theme")
        appendLine("BIBLE: name, content")
        appendLine("CHARACTER: name, location, physicalState, emotionalState, goal")
        appendLine("OUTLINE: 只允许 MASTER/VOLUME 的 title, objective, conflict, turningPoint；禁止直接改 CHAPTER")
        appendLine("TIMELINE: storyTime, location, summary")
        appendLine("FORESHADOW: title, detail, expectedPayoff")
        appendLine("KNOWLEDGE: title, truth, note")
        appendLine("如果用户要求新增实体、删除实体、批量改章节正文、直接改章纲、或目标不明确，不要编造 patch；放进 warnings。")
        appendLine("targetId 必须从下面清单复制；before 必须照抄当前值。")
        appendLine("任何修改都必须尊重现有 StorySnapshot；不要把聊天中未确认想法当成当前事实。")
        appendLine("Sepia 原则只用于影响判断：先判断叙事架构影响，再判断段落/表面文风；不要为了去 AI 而扩大修改范围。")
        appendLine()
        appendLine("【当前可修改对象】")
        appendLine("NOVEL id=${snapshot.novel.id} title=${clip(snapshot.novel.title)} genre=${clip(snapshot.novel.genre)} premise=${clip(snapshot.novel.premise)} theme=${clip(snapshot.novel.theme)}")
        snapshot.bible.take(40).forEach { item ->
            appendLine("BIBLE id=${item.id} name=${clip(item.name)} content=${clip(item.content, 800)}")
        }
        snapshot.characters.take(40).forEach { item ->
            appendLine("CHARACTER id=${item.id} name=${clip(item.name)} location=${clip(item.location)} physicalState=${clip(item.physicalState)} emotionalState=${clip(item.emotionalState)} goal=${clip(item.goal)}")
        }
        snapshot.outline.filter { it.level != OutlineLevel.CHAPTER }.take(30).forEach { item ->
            appendLine("OUTLINE id=${item.id} level=${item.level} title=${clip(item.title)} objective=${clip(item.objective)} conflict=${clip(item.conflict)} turningPoint=${clip(item.turningPoint)}")
        }
        snapshot.recentTimeline.takeLast(30).forEach { item ->
            appendLine("TIMELINE id=${item.id} chapter=${item.chapter} storyTime=${clip(item.storyTime)} location=${clip(item.location)} summary=${clip(item.summary)}")
        }
        snapshot.relevantForeshadowing.take(30).forEach { item ->
            appendLine("FORESHADOW id=${item.id} title=${clip(item.title)} detail=${clip(item.detail)} expectedPayoff=${clip(item.expectedPayoff)}")
        }
        snapshot.knowledgeLedger.take(30).forEach { item ->
            appendLine("KNOWLEDGE id=${item.id} title=${clip(item.title)} truth=${clip(item.truth, 500)} note=${clip(item.note)}")
        }
    }

    private fun clip(value: String, max: Int = 240): String = value.replace("\n", " ").trim().take(max)
}

object CanonPatchEngine {
    private val allowed = mapOf(
        CanonPatchTargetType.NOVEL to setOf("title", "genre", "premise", "theme"),
        CanonPatchTargetType.BIBLE to setOf("name", "content"),
        CanonPatchTargetType.CHARACTER to setOf("name", "location", "physicalState", "emotionalState", "goal"),
        CanonPatchTargetType.OUTLINE to setOf("title", "objective", "conflict", "turningPoint"),
        CanonPatchTargetType.TIMELINE to setOf("storyTime", "location", "summary"),
        CanonPatchTargetType.FORESHADOW to setOf("title", "detail", "expectedPayoff"),
        CanonPatchTargetType.KNOWLEDGE to setOf("title", "truth", "note"),
    )

    fun normalize(snapshot: StorySnapshot, proposed: List<CanonChangePatch>): List<CanonChangePatch> =
        proposed.mapNotNull { patch ->
            val field = patch.field.trim()
            if (field !in allowed[patch.targetType].orEmpty()) return@mapNotNull null
            val targetId = if (patch.targetType == CanonPatchTargetType.NOVEL) snapshot.novel.id else patch.targetId.trim()
            val current = fieldValue(snapshot, patch.targetType, targetId, field) ?: return@mapNotNull null
            if (patch.targetType == CanonPatchTargetType.OUTLINE) {
                val node = snapshot.outline.firstOrNull { it.id == targetId } ?: return@mapNotNull null
                if (node.level == OutlineLevel.CHAPTER) return@mapNotNull null
            }
            val after = patch.after.trim()
            if (after == current.trim()) return@mapNotNull null
            patch.copy(
                targetId = targetId,
                targetLabel = targetLabel(snapshot, patch.targetType, targetId),
                field = field,
                before = current,
                after = after,
                risk = maxRisk(patch.risk, defaultRisk(patch.targetType, field)),
            )
        }.distinctBy { listOf(it.targetType.name, it.targetId, it.field) }

    fun conflicts(snapshot: StorySnapshot, patches: List<CanonChangePatch>): List<String> = buildList {
        patches.forEach { patch ->
            val current = fieldValue(snapshot, patch.targetType, patch.targetId, patch.field)
            when {
                current == null -> add("${patch.targetLabel}.${patch.field} 已不存在")
                current != patch.before -> add("${patch.targetLabel}.${patch.field} 在预览后已变化，请重新生成提案")
            }
        }
    }

    fun apply(snapshot: StorySnapshot, patches: List<CanonChangePatch>, request: String): StorySnapshot {
        val conflicts = conflicts(snapshot, patches)
        require(conflicts.isEmpty()) { conflicts.joinToString("；") }
        var updated = snapshot
        val now = System.currentTimeMillis()
        val provenance = mutableListOf<FactProvenance>()
        patches.forEach { patch ->
            updated = applyOne(updated, patch)
            provenance += FactProvenance(
                id = "author-change-${UUID.randomUUID()}",
                novelId = snapshot.novel.id,
                chapter = snapshot.novel.currentChapter,
                kind = "AUTHOR_CHANGE:${patch.targetType.name}",
                subject = "${patch.targetLabel}.${patch.field}",
                before = patch.before,
                after = patch.after,
                evidence = "V7 用户确认：${request.take(600)}",
                recordedAt = now,
            )
        }
        return updated.copy(factHistory = (updated.factHistory + provenance).takeLast(2_000))
    }

    private fun applyOne(snapshot: StorySnapshot, patch: CanonChangePatch): StorySnapshot = when (patch.targetType) {
        CanonPatchTargetType.NOVEL -> snapshot.copy(novel = patchNovel(snapshot.novel, patch.field, patch.after))
        CanonPatchTargetType.BIBLE -> snapshot.copy(bible = snapshot.bible.map { if (it.id == patch.targetId) patchBible(it, patch.field, patch.after) else it })
        CanonPatchTargetType.CHARACTER -> snapshot.copy(characters = snapshot.characters.map { if (it.id == patch.targetId) patchCharacter(it, patch.field, patch.after) else it })
        CanonPatchTargetType.OUTLINE -> {
            val outline = snapshot.outline.map { if (it.id == patch.targetId) patchOutline(it, patch.field, patch.after) else it }
            val active = snapshot.activeOutline.map { if (it.id == patch.targetId) patchOutline(it, patch.field, patch.after) else it }
            snapshot.copy(outline = outline, activeOutline = active)
        }
        CanonPatchTargetType.TIMELINE -> snapshot.copy(recentTimeline = snapshot.recentTimeline.map { if (it.id == patch.targetId) patchTimeline(it, patch.field, patch.after) else it })
        CanonPatchTargetType.FORESHADOW -> snapshot.copy(relevantForeshadowing = snapshot.relevantForeshadowing.map { if (it.id == patch.targetId) patchForeshadow(it, patch.field, patch.after) else it })
        CanonPatchTargetType.KNOWLEDGE -> snapshot.copy(knowledgeLedger = snapshot.knowledgeLedger.map { if (it.id == patch.targetId) patchKnowledge(it, patch.field, patch.after) else it })
    }

    private fun fieldValue(snapshot: StorySnapshot, type: CanonPatchTargetType, id: String, field: String): String? = when (type) {
        CanonPatchTargetType.NOVEL -> when (field) {
            "title" -> snapshot.novel.title; "genre" -> snapshot.novel.genre; "premise" -> snapshot.novel.premise; "theme" -> snapshot.novel.theme; else -> null
        }
        CanonPatchTargetType.BIBLE -> snapshot.bible.firstOrNull { it.id == id }?.let { when (field) { "name" -> it.name; "content" -> it.content; else -> null } }
        CanonPatchTargetType.CHARACTER -> snapshot.characters.firstOrNull { it.id == id }?.let { when (field) { "name" -> it.name; "location" -> it.location; "physicalState" -> it.physicalState; "emotionalState" -> it.emotionalState; "goal" -> it.goal; else -> null } }
        CanonPatchTargetType.OUTLINE -> snapshot.outline.firstOrNull { it.id == id }?.let { when (field) { "title" -> it.title; "objective" -> it.objective; "conflict" -> it.conflict; "turningPoint" -> it.turningPoint; else -> null } }
        CanonPatchTargetType.TIMELINE -> snapshot.recentTimeline.firstOrNull { it.id == id }?.let { when (field) { "storyTime" -> it.storyTime; "location" -> it.location; "summary" -> it.summary; else -> null } }
        CanonPatchTargetType.FORESHADOW -> snapshot.relevantForeshadowing.firstOrNull { it.id == id }?.let { when (field) { "title" -> it.title; "detail" -> it.detail; "expectedPayoff" -> it.expectedPayoff; else -> null } }
        CanonPatchTargetType.KNOWLEDGE -> snapshot.knowledgeLedger.firstOrNull { it.id == id }?.let { when (field) { "title" -> it.title; "truth" -> it.truth; "note" -> it.note; else -> null } }
    }

    private fun targetLabel(snapshot: StorySnapshot, type: CanonPatchTargetType, id: String): String = when (type) {
        CanonPatchTargetType.NOVEL -> "作品《${snapshot.novel.title}》"
        CanonPatchTargetType.BIBLE -> snapshot.bible.firstOrNull { it.id == id }?.name ?: id
        CanonPatchTargetType.CHARACTER -> snapshot.characters.firstOrNull { it.id == id }?.name ?: id
        CanonPatchTargetType.OUTLINE -> snapshot.outline.firstOrNull { it.id == id }?.title ?: id
        CanonPatchTargetType.TIMELINE -> snapshot.recentTimeline.firstOrNull { it.id == id }?.let { "第${it.chapter}章时间线" } ?: id
        CanonPatchTargetType.FORESHADOW -> snapshot.relevantForeshadowing.firstOrNull { it.id == id }?.title ?: id
        CanonPatchTargetType.KNOWLEDGE -> snapshot.knowledgeLedger.firstOrNull { it.id == id }?.title ?: id
    }

    private fun patchNovel(item: Novel, field: String, value: String): Novel = when (field) {
        "title" -> item.copy(title = value); "genre" -> item.copy(genre = value); "premise" -> item.copy(premise = value); "theme" -> item.copy(theme = value); else -> item
    }
    private fun patchBible(item: BibleEntry, field: String, value: String): BibleEntry = when (field) {
        "name" -> item.copy(name = value); "content" -> item.copy(content = value); else -> item
    }
    private fun patchCharacter(item: CharacterState, field: String, value: String): CharacterState = when (field) {
        "name" -> item.copy(name = value); "location" -> item.copy(location = value); "physicalState" -> item.copy(physicalState = value); "emotionalState" -> item.copy(emotionalState = value); "goal" -> item.copy(goal = value); else -> item
    }
    private fun patchOutline(item: OutlineNode, field: String, value: String): OutlineNode = when (field) {
        "title" -> item.copy(title = value); "objective" -> item.copy(objective = value); "conflict" -> item.copy(conflict = value); "turningPoint" -> item.copy(turningPoint = value); else -> item
    }
    private fun patchTimeline(item: TimelineEvent, field: String, value: String): TimelineEvent = when (field) {
        "storyTime" -> item.copy(storyTime = value); "location" -> item.copy(location = value); "summary" -> item.copy(summary = value); else -> item
    }
    private fun patchForeshadow(item: Foreshadowing, field: String, value: String): Foreshadowing = when (field) {
        "title" -> item.copy(title = value); "detail" -> item.copy(detail = value); "expectedPayoff" -> item.copy(expectedPayoff = value); else -> item
    }
    private fun patchKnowledge(item: KnowledgeBoundary, field: String, value: String): KnowledgeBoundary = when (field) {
        "title" -> item.copy(title = value); "truth" -> item.copy(truth = value); "note" -> item.copy(note = value); else -> item
    }

    private fun defaultRisk(type: CanonPatchTargetType, field: String): CanonChangeRisk = when {
        type == CanonPatchTargetType.NOVEL && field in setOf("premise", "theme") -> CanonChangeRisk.HIGH
        type == CanonPatchTargetType.BIBLE && field == "content" -> CanonChangeRisk.HIGH
        type == CanonPatchTargetType.OUTLINE -> CanonChangeRisk.HIGH
        type in setOf(CanonPatchTargetType.TIMELINE, CanonPatchTargetType.KNOWLEDGE) -> CanonChangeRisk.HIGH
        type in setOf(CanonPatchTargetType.CHARACTER, CanonPatchTargetType.FORESHADOW) -> CanonChangeRisk.MEDIUM
        else -> CanonChangeRisk.LOW
    }

    private fun maxRisk(a: CanonChangeRisk, b: CanonChangeRisk): CanonChangeRisk =
        if (a.ordinal >= b.ordinal) a else b
}

object CanonImpactAnalyzer {
    fun analyze(
        snapshot: StorySnapshot,
        drafts: List<ChapterDraft>,
        patches: List<CanonChangePatch>,
    ): List<CanonChangeImpact> {
        val tokens = patches.flatMap { patch ->
            buildList {
                if (patch.targetLabel.length >= 2) add(patch.targetLabel.substringAfter('《').substringBefore('》'))
                val before = patch.before.trim()
                if (before.length in 2..36) add(before)
            }
        }.map(String::trim).filter { it.length >= 2 }.distinct()
        if (tokens.isEmpty()) return emptyList()

        val impacts = mutableListOf<CanonChangeImpact>()
        fun hits(text: String): Boolean = tokens.any { token -> text.contains(token, ignoreCase = true) }

        snapshot.outline.forEach { node ->
            val text = listOf(node.title, node.objective, node.conflict, node.turningPoint, node.mustInclude.joinToString(" "), node.forbidden.joinToString(" ")).joinToString(" ")
            if (hits(text)) impacts += CanonChangeImpact("蓝图", node.title, "${node.level} 仍引用被修改对象/旧值", node.order.takeIf { node.level == OutlineLevel.CHAPTER })
        }
        snapshot.characters.forEach { item ->
            val text = listOf(item.name, item.location, item.physicalState, item.emotionalState, item.goal, item.knownSecrets.joinToString(" "), item.relationshipNotes.values.joinToString(" ")).joinToString(" ")
            if (hits(text)) impacts += CanonChangeImpact("人物", item.name, "人物状态或关系可能需要复核")
        }
        snapshot.recentTimeline.forEach { item ->
            if (hits("${item.storyTime} ${item.location} ${item.summary} ${item.consequences.joinToString(" ")}")) {
                impacts += CanonChangeImpact("时间线", "第${item.chapter}章", item.summary.take(120), item.chapter)
            }
        }
        snapshot.relevantForeshadowing.forEach { item ->
            if (hits("${item.title} ${item.detail} ${item.expectedPayoff}")) impacts += CanonChangeImpact("伏笔", item.title, "伏笔种植/回收逻辑可能受影响", item.plantedChapter)
        }
        snapshot.knowledgeLedger.forEach { item ->
            if (hits("${item.title} ${item.truth} ${item.note} ${item.knownBy.joinToString(" ")} ${item.unknownTo.joinToString(" ")}")) {
                impacts += CanonChangeImpact("信息边界", item.title, "人物/读者知情边界可能需要复核")
            }
        }
        drafts.forEach { draft ->
            val sceneText = draft.scenePlan.joinToString(" ") { "${it.viewpoint} ${it.location} ${it.purpose} ${it.conflict} ${it.outcome} ${it.participants.joinToString(" ")}" }
            if (hits("${draft.title} ${draft.objective} ${draft.summary} $sceneText ${draft.content}")) {
                impacts += CanonChangeImpact("章节", "第${draft.chapterNumber}章《${draft.title}》", if (draft.content.isBlank()) "章纲/场景计划受影响" else "已有正文命中旧设定，建议后续重审", draft.chapterNumber)
            }
        }
        if (hits(snapshot.longTermSummary) || snapshot.recentSummaries.any(::hits)) {
            impacts += CanonChangeImpact("长期记忆", "剧情摘要", "压缩摘要中仍可能保留旧设定；后续整理记忆时应刷新")
        }
        return impacts.distinctBy { listOf(it.scope, it.label, it.chapterNumber?.toString().orEmpty()) }.take(60)
    }
}
