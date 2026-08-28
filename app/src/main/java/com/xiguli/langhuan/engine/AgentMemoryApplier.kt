package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.FactProvenance
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.Foreshadowing
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent
import java.util.UUID

object AgentMemoryApplier {
    fun apply(snapshot: StorySnapshot, chapterNumber: Int, review: AgentReview): StorySnapshot {
        val characters = snapshot.characters.toMutableList()
        val timeline = snapshot.recentTimeline.toMutableList()
        val foreshadowing = snapshot.relevantForeshadowing.toMutableList()
        val provenance = snapshot.factHistory.toMutableList()
        val now = System.currentTimeMillis()

        fun characterIndex(name: String): Int = characters.indexOfFirst { it.name.equals(name.trim(), ignoreCase = true) }
        fun packed(value: String, limit: Int): List<String> = value.split("||", limit = limit).map { it.trim() }
        fun splitList(value: String): List<String> = value
            .split('、', ',', '，', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        review.memoryActions.forEach { action ->
            provenance += FactProvenance(
                id = UUID.randomUUID().toString(),
                novelId = snapshot.novel.id,
                chapter = chapterNumber,
                kind = action.kind.name,
                subject = action.subject.trim(),
                before = action.before.trim(),
                after = action.after.trim(),
                evidence = action.evidence.trim(),
                recordedAt = now,
            )

            when (action.kind) {
                AgentActionKind.CHARACTER_NEW -> {
                    if (action.subject.isNotBlank() && characterIndex(action.subject) < 0) {
                        val p = packed(action.after, 4)
                        characters += CharacterState(
                            id = UUID.randomUUID().toString(),
                            novelId = snapshot.novel.id,
                            name = action.subject.trim(),
                            personality = splitList(p.getOrNull(3).orEmpty()),
                            location = p.getOrNull(0).orEmpty().ifBlank { "未知" },
                            physicalState = "正常",
                            emotionalState = p.getOrNull(1).orEmpty().ifBlank { "待观察" },
                            goal = p.getOrNull(2).orEmpty(),
                            lastUpdatedChapter = chapterNumber,
                        )
                    }
                }

                AgentActionKind.CHARACTER_LOCATION,
                AgentActionKind.CHARACTER_EMOTION,
                AgentActionKind.CHARACTER_GOAL -> {
                    val index = characterIndex(action.subject)
                    if (index >= 0 && action.after.isNotBlank()) {
                        val current = characters[index]
                        characters[index] = when (action.kind) {
                            AgentActionKind.CHARACTER_LOCATION -> current.copy(location = action.after, lastUpdatedChapter = chapterNumber)
                            AgentActionKind.CHARACTER_EMOTION -> current.copy(emotionalState = action.after, lastUpdatedChapter = chapterNumber)
                            AgentActionKind.CHARACTER_GOAL -> current.copy(goal = action.after, lastUpdatedChapter = chapterNumber)
                            else -> current
                        }
                    }
                }

                AgentActionKind.RELATION -> {
                    val index = characterIndex(action.subject)
                    val p = packed(action.after, 2)
                    val target = p.getOrNull(0).orEmpty()
                    val note = p.getOrNull(1).orEmpty()
                    if (index >= 0 && target.isNotBlank() && note.isNotBlank()) {
                        val current = characters[index]
                        characters[index] = current.copy(
                            relationshipNotes = current.relationshipNotes + (target to note),
                            lastUpdatedChapter = chapterNumber,
                        )
                    }
                }

                AgentActionKind.TIMELINE -> {
                    val p = packed(action.after, 5)
                    val summary = p.getOrNull(3).orEmpty().ifBlank { action.subject }
                    if (summary.isNotBlank() && timeline.none { it.chapter == chapterNumber && it.summary == summary }) {
                        timeline += TimelineEvent(
                            id = UUID.randomUUID().toString(),
                            novelId = snapshot.novel.id,
                            chapter = chapterNumber,
                            storyTime = p.getOrNull(0).orEmpty(),
                            location = p.getOrNull(1).orEmpty(),
                            participants = splitList(p.getOrNull(2).orEmpty()),
                            summary = summary,
                            consequences = splitList(p.getOrNull(4).orEmpty()),
                        )
                    }
                }

                AgentActionKind.FORESHADOW_NEW -> {
                    val p = packed(action.after, 4)
                    if (action.subject.isNotBlank() && foreshadowing.none { it.title.equals(action.subject, ignoreCase = true) }) {
                        val start = p.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(chapterNumber + 1) ?: chapterNumber + 3
                        val end = p.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(start) ?: start + 10
                        foreshadowing += Foreshadowing(
                            id = UUID.randomUUID().toString(),
                            novelId = snapshot.novel.id,
                            title = action.subject.trim(),
                            plantedChapter = chapterNumber,
                            detail = p.getOrNull(0).orEmpty().ifBlank { action.evidence },
                            expectedPayoff = p.getOrNull(1).orEmpty(),
                            expectedChapterStart = start,
                            expectedChapterEnd = end,
                            status = ForeshadowStatus.PLANTED,
                        )
                    }
                }

                AgentActionKind.FORESHADOW_UPDATE -> {
                    val index = foreshadowing.indexOfFirst {
                        it.id == action.subject || it.title.equals(action.subject, ignoreCase = true)
                    }
                    if (index >= 0) {
                        val p = packed(action.after, 2)
                        val current = foreshadowing[index]
                        val status = ForeshadowStatus.entries.firstOrNull {
                            it.name == p.getOrNull(0).orEmpty().uppercase()
                        } ?: current.status
                        val note = p.getOrNull(1).orEmpty()
                        foreshadowing[index] = current.copy(
                            status = status,
                            detail = if (note.isBlank() || current.detail.contains(note)) current.detail else "${current.detail}；第${chapterNumber}章：$note",
                        )
                    }
                }

                else -> Unit
            }
        }

        review.touchedForeshadowingIds.forEach { id ->
            val index = foreshadowing.indexOfFirst { it.id == id }
            if (index >= 0 && foreshadowing[index].status == ForeshadowStatus.PLANTED) {
                val current = foreshadowing[index]
                foreshadowing[index] = current.copy(status = ForeshadowStatus.DEVELOPING)
                provenance += FactProvenance(
                    id = UUID.randomUUID().toString(),
                    novelId = snapshot.novel.id,
                    chapter = chapterNumber,
                    kind = "FORESHADOW_TOUCH",
                    subject = current.title,
                    before = current.status.name,
                    after = ForeshadowStatus.DEVELOPING.name,
                    evidence = "Agent touchedForeshadowingIds=${current.id}",
                    recordedAt = now,
                )
            }
        }

        return snapshot.copy(
            characters = characters,
            recentTimeline = timeline.sortedBy { it.chapter }.takeLast(120),
            relevantForeshadowing = foreshadowing,
            factHistory = provenance
                .distinctBy { listOf(it.chapter, it.kind, it.subject, it.before, it.after, it.evidence) }
                .takeLast(1_200),
        )
    }
}
