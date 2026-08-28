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
        val knowledgeLedger = snapshot.knowledgeLedger.toMutableList()
        val provenance = snapshot.factHistory.toMutableList()
        val now = System.currentTimeMillis()

        fun characterIndex(name: String): Int = characters.indexOfFirst { it.name.equals(name.trim(), ignoreCase = true) }
        fun packed(value: String, limit: Int): List<String> = value.split("||", limit = limit).map { it.trim() }
        fun splitList(value: String): List<String> = value
            .split('、', ',', '，', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        fun parseDay(value: String): Int = Regex("(?:故事)?第(\\d+)天").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        fun inferTime(value: String): String = Regex("凌晨|清晨|早晨|上午|中午|下午|傍晚|黄昏|晚上|夜间|深夜|子夜").find(value)?.value.orEmpty()
        fun latestMainDay(): Int = timeline
            .sortedWith(compareBy<TimelineEvent> { it.chapter }.thenBy { it.orderInChapter })
            .lastOrNull { !it.isFlashback }
            ?.let { if (it.storyDay > 0) it.storyDay else parseDay(it.storyTime) }
            ?: 0

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

                AgentActionKind.KNOWLEDGE_GAIN -> {
                    val index = characterIndex(action.subject)
                    val learned = action.after.trim()
                    if (index >= 0 && learned.isNotBlank()) {
                        val current = characters[index]
                        if (current.knownSecrets.none { it.equals(learned, ignoreCase = true) }) {
                            characters[index] = current.copy(
                                knownSecrets = (current.knownSecrets + learned).distinct(),
                                lastUpdatedChapter = chapterNumber,
                            )
                        }

                        // 只更新已经存在、由作者/项目确认过的信息边界；绝不让 Agent 自己创造“真相”。
                        val ledgerIndex = knowledgeLedger.indexOfFirst { boundary ->
                            boundary.id.equals(learned, ignoreCase = true) ||
                                boundary.title.equals(learned, ignoreCase = true) ||
                                (boundary.truth.isNotBlank() && learned.contains(boundary.truth, ignoreCase = true)) ||
                                boundary.triggerTerms.any { term ->
                                    term.isNotBlank() && (learned.contains(term, ignoreCase = true) || term.contains(learned, ignoreCase = true))
                                }
                        }
                        if (ledgerIndex >= 0) {
                            val boundary = knowledgeLedger[ledgerIndex]
                            knowledgeLedger[ledgerIndex] = boundary.copy(
                                knownBy = (boundary.knownBy + action.subject.trim()).distinct(),
                                unknownTo = boundary.unknownTo.filterNot { it.equals(action.subject.trim(), ignoreCase = true) },
                            )
                        }
                    }
                }

                AgentActionKind.TIMELINE -> {
                    val raw = action.after.split("||").map { it.trim() }
                    val structured = raw.size >= 8 && raw.getOrNull(0)?.toIntOrNull() != null
                    val previousMainDay = latestMainDay()
                    val storyDay: Int
                    val timeOfDay: String
                    val elapsed: String
                    val flashback: Boolean
                    val location: String
                    val participants: List<String>
                    val summary: String
                    val consequences: List<String>
                    val storyTime: String

                    if (structured) {
                        val requestedDay = raw[0].toIntOrNull()?.coerceAtLeast(1) ?: (previousMainDay.takeIf { it > 0 } ?: 1)
                        flashback = raw[3].equals("FLASHBACK", ignoreCase = true)
                        if (!flashback && previousMainDay > 0 && requestedDay < previousMainDay) {
                            return@forEach
                        }
                        storyDay = requestedDay
                        timeOfDay = raw[1].ifBlank { "时段未标注" }
                        elapsed = raw[2]
                        location = raw[4]
                        participants = splitList(raw[5])
                        summary = raw[6].ifBlank { action.subject }
                        consequences = splitList(raw[7])
                        storyTime = "故事第${storyDay}天·$timeOfDay${if (flashback) "（闪回）" else ""}"
                    } else {
                        val p = packed(action.after, 5)
                        val oldTime = p.getOrNull(0).orEmpty()
                        val parsed = parseDay(oldTime)
                        storyDay = parsed.takeIf { it > 0 } ?: previousMainDay
                        timeOfDay = inferTime(oldTime)
                        elapsed = ""
                        flashback = false
                        location = p.getOrNull(1).orEmpty()
                        participants = splitList(p.getOrNull(2).orEmpty())
                        summary = p.getOrNull(3).orEmpty().ifBlank { action.subject }
                        consequences = splitList(p.getOrNull(4).orEmpty())
                        storyTime = oldTime
                    }

                    val order = (timeline.filter { it.chapter == chapterNumber }.maxOfOrNull { it.orderInChapter } ?: 0) + 1
                    if (summary.isNotBlank() && timeline.none { it.chapter == chapterNumber && it.summary == summary }) {
                        timeline += TimelineEvent(
                            id = UUID.randomUUID().toString(),
                            novelId = snapshot.novel.id,
                            chapter = chapterNumber,
                            storyTime = storyTime,
                            location = location,
                            participants = participants,
                            summary = summary,
                            consequences = consequences,
                            storyDay = storyDay,
                            timeOfDay = timeOfDay,
                            orderInChapter = order,
                            elapsedFromPrevious = elapsed,
                            isFlashback = flashback,
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

        val updated = snapshot.copy(
            characters = characters,
            recentTimeline = timeline
                .sortedWith(compareBy<TimelineEvent> { it.chapter }.thenBy { it.orderInChapter })
                .takeLast(160),
            relevantForeshadowing = foreshadowing,
            factHistory = provenance
                .distinctBy { listOf(it.chapter, it.kind, it.subject, it.before, it.after, it.evidence) }
                .takeLast(1_200),
            knowledgeLedger = knowledgeLedger,
        )
        return LongFormContinuityEngine().refreshAfterMemoryUpdate(updated, chapterNumber)
    }
}
