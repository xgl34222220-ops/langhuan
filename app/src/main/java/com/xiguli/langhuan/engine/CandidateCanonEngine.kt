package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.CandidateFact
import com.xiguli.langhuan.domain.CandidateFactKind
import com.xiguli.langhuan.domain.CandidateFactRisk
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.FactProvenance
import com.xiguli.langhuan.domain.KnowledgeBoundary
import com.xiguli.langhuan.domain.KnowledgeRevealPolicy
import com.xiguli.langhuan.domain.ReaderKnowledgeState
import com.xiguli.langhuan.domain.StorySnapshot
import java.util.UUID

/** Result of staging one Agent review into the persistent candidate ledger. */
data class CandidateStageResult(
    val snapshot: StorySnapshot,
    val stagedCount: Int,
    val autoConfirmedCount: Int,
)

/**
 * Candidate -> Canon gate.
 *
 * AI output is never Canon merely because it was generated. Every memory action is first persisted as a
 * CandidateFact. Only locally provable low-risk changes may auto-confirm; everything else waits for the
 * author. Canon writes are delegated to AgentMemoryApplier whenever possible so there is still exactly one
 * character/timeline/foreshadow/knowledge mutation path.
 */
object CandidateCanonEngine {
    fun stage(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        review: AgentReview,
        now: Long = System.currentTimeMillis(),
    ): CandidateStageResult {
        val existingKeys = snapshot.candidateFacts.map(::signature).toMutableSet()
        val staged = review.memoryActions.mapNotNull { action ->
            val candidate = candidateFromAction(snapshot, draft, action, now) ?: return@mapNotNull null
            val key = signature(candidate)
            if (!existingKeys.add(key)) null else candidate
        }

        var working = snapshot.copy(
            candidateFacts = (snapshot.candidateFacts + staged).takeLast(500),
        )
        var autoConfirmed = 0
        staged.filter { it.autoApprovable && it.risk == CandidateFactRisk.LOW }.forEach { candidate ->
            working = confirm(working, candidate.id, now)
            autoConfirmed++
        }
        return CandidateStageResult(working, staged.size, autoConfirmed)
    }

    /** Generic entry point for future creation chat/tools. These facts still remain pending by default. */
    fun stageExternal(
        snapshot: StorySnapshot,
        sourceChapter: Int,
        kind: CandidateFactKind,
        subject: String,
        before: String = "",
        after: String,
        evidence: String = "",
        now: Long = System.currentTimeMillis(),
    ): StorySnapshot {
        if (subject.isBlank() || after.isBlank()) return snapshot
        val risk = when (kind) {
            CandidateFactKind.BIBLE_ENTRY,
            CandidateFactKind.KNOWLEDGE_BOUNDARY,
            CandidateFactKind.CHARACTER_NEW,
            CandidateFactKind.FORESHADOW_NEW -> CandidateFactRisk.HIGH
            CandidateFactKind.RELATION,
            CandidateFactKind.KNOWLEDGE_GAIN,
            CandidateFactKind.TIMELINE -> CandidateFactRisk.MEDIUM
            else -> CandidateFactRisk.MEDIUM
        }
        val candidate = CandidateFact(
            id = UUID.randomUUID().toString(),
            novelId = snapshot.novel.id,
            sourceChapter = sourceChapter.coerceAtLeast(0),
            kind = kind,
            subject = subject.trim(),
            before = before.trim(),
            after = after.trim(),
            evidence = evidence.trim(),
            risk = risk,
            validationNotes = listOf("外部 AI/工具提案：未经作者确认不得进入 Canon"),
            createdAt = now,
        )
        if (snapshot.candidateFacts.any { signature(it) == signature(candidate) }) return snapshot
        return snapshot.copy(candidateFacts = (snapshot.candidateFacts + candidate).takeLast(500))
    }

    fun confirm(
        snapshot: StorySnapshot,
        candidateId: String,
        now: Long = System.currentTimeMillis(),
    ): StorySnapshot {
        val candidate = snapshot.candidateFacts.firstOrNull { it.id == candidateId } ?: return snapshot
        if (candidate.status != CandidateFactStatus.PENDING) return snapshot

        val applied = when (candidate.kind) {
            CandidateFactKind.BIBLE_ENTRY -> confirmBible(snapshot, candidate, now)
            CandidateFactKind.KNOWLEDGE_BOUNDARY -> confirmKnowledgeBoundary(snapshot, candidate, now)
            else -> confirmAgentFact(snapshot, candidate)
        }
        return applied.copy(
            candidateFacts = applied.candidateFacts.map { item ->
                if (item.id == candidateId) item.copy(status = CandidateFactStatus.CONFIRMED, resolvedAt = now) else item
            }.takeLast(500),
        )
    }

    fun reject(
        snapshot: StorySnapshot,
        candidateId: String,
        now: Long = System.currentTimeMillis(),
    ): StorySnapshot = snapshot.copy(
        candidateFacts = snapshot.candidateFacts.map { item ->
            if (item.id == candidateId && item.status == CandidateFactStatus.PENDING) {
                item.copy(status = CandidateFactStatus.REJECTED, resolvedAt = now)
            } else item
        }.takeLast(500),
    )

    fun pending(snapshot: StorySnapshot): List<CandidateFact> = snapshot.candidateFacts
        .filter { it.status == CandidateFactStatus.PENDING }
        .sortedWith(compareByDescending<CandidateFact> { riskWeight(it.risk) }.thenByDescending { it.createdAt })

    private fun candidateFromAction(
        snapshot: StorySnapshot,
        draft: ChapterDraft,
        action: AgentAction,
        now: Long,
    ): CandidateFact? {
        val kind = action.kind.toCandidateKind() ?: return null
        if (action.subject.isBlank() || action.after.isBlank()) return null
        val proof = evidenceIsInProse(action.evidence, draft.content)
        val existingCharacter = snapshot.characters.any { it.name.equals(action.subject.trim(), true) }
        val existingForeshadow = snapshot.relevantForeshadowing.any {
            it.id == action.subject.trim() || it.title.equals(action.subject.trim(), true)
        }
        val notes = mutableListOf<String>()
        if (action.evidence.isBlank()) notes += "没有正文证据摘录，不能自动确认"
        else if (proof) notes += "正文中找到可复核证据"
        else notes += "证据不是正文中的可直接匹配片段，需要作者判断"

        val (risk, auto) = when (kind) {
            CandidateFactKind.CHARACTER_LOCATION,
            CandidateFactKind.CHARACTER_EMOTION,
            CandidateFactKind.CHARACTER_GOAL -> {
                if (!existingCharacter) {
                    notes += "人物尚未存在于 Canon"
                    CandidateFactRisk.HIGH to false
                } else if (proof) {
                    notes += "仅更新已有角色的当前状态，不创造新实体"
                    CandidateFactRisk.LOW to true
                } else CandidateFactRisk.MEDIUM to false
            }
            CandidateFactKind.FORESHADOW_UPDATE -> {
                if (existingForeshadow && proof) {
                    notes += "只更新已有伏笔状态"
                    CandidateFactRisk.LOW to true
                } else CandidateFactRisk.MEDIUM to false
            }
            CandidateFactKind.KNOWLEDGE_GAIN -> {
                val boundaryExists = snapshot.knowledgeLedger.any { boundary ->
                    boundary.id.equals(action.after.trim(), true) || boundary.title.equals(action.after.trim(), true) ||
                        boundary.triggerTerms.any { term -> term.isNotBlank() && action.after.contains(term, true) }
                }
                if (!boundaryExists) notes += "没有匹配到已确认的信息边界，禁止自动创建真相"
                CandidateFactRisk.MEDIUM to false
            }
            CandidateFactKind.TIMELINE -> CandidateFactRisk.MEDIUM to false
            CandidateFactKind.RELATION -> CandidateFactRisk.MEDIUM to false
            CandidateFactKind.CHARACTER_NEW,
            CandidateFactKind.FORESHADOW_NEW -> CandidateFactRisk.HIGH to false
            CandidateFactKind.BIBLE_ENTRY,
            CandidateFactKind.KNOWLEDGE_BOUNDARY -> CandidateFactRisk.HIGH to false
        }

        return CandidateFact(
            id = UUID.randomUUID().toString(),
            novelId = snapshot.novel.id,
            sourceChapter = draft.chapterNumber,
            kind = kind,
            subject = action.subject.trim(),
            before = action.before.trim(),
            after = action.after.trim(),
            evidence = action.evidence.trim(),
            risk = risk,
            validationNotes = notes.distinct(),
            autoApprovable = auto,
            createdAt = now,
        )
    }

    private fun confirmAgentFact(snapshot: StorySnapshot, candidate: CandidateFact): StorySnapshot {
        val actionKind = candidate.kind.toAgentKind()
            ?: error("${candidate.kind} 不能通过 Agent Canon 写入器确认")
        val review = AgentReview(
            title = "Candidate Canon Commit",
            summary = "",
            metrics = "",
            memoryActions = listOf(
                AgentAction(
                    kind = actionKind,
                    subject = candidate.subject,
                    before = candidate.before,
                    after = candidate.after,
                    evidence = candidate.evidence,
                )
            ),
            diagnostics = emptyList(),
            nextOptions = emptyList(),
            touchedForeshadowingIds = emptyList(),
            fullBook = false,
        )
        return AgentMemoryApplier.apply(snapshot, candidate.sourceChapter.coerceAtLeast(1), review)
    }

    private fun confirmBible(snapshot: StorySnapshot, candidate: CandidateFact, now: Long): StorySnapshot {
        val category = BibleCategory.entries.firstOrNull { it.name.equals(candidate.before.trim(), true) }
            ?: BibleCategory.WORLD
        require(snapshot.bible.none { it.category == category && it.name.equals(candidate.subject, true) }) {
            "Canon 中已存在同名 ${category.name} 设定，候选不能直接覆盖，请先编辑现有设定"
        }
        val entry = BibleEntry(
            id = UUID.randomUUID().toString(),
            novelId = snapshot.novel.id,
            category = category,
            name = candidate.subject,
            content = candidate.after,
            locked = true,
        )
        return snapshot.copy(
            bible = snapshot.bible + entry,
            factHistory = (snapshot.factHistory + provenance(snapshot, candidate, now)).takeLast(1_200),
        )
    }

    private fun confirmKnowledgeBoundary(snapshot: StorySnapshot, candidate: CandidateFact, now: Long): StorySnapshot {
        require(snapshot.knowledgeLedger.none { it.title.equals(candidate.subject, true) }) {
            "Canon 中已存在同名信息边界，候选不能直接覆盖"
        }
        val boundary = KnowledgeBoundary(
            id = UUID.randomUUID().toString(),
            title = candidate.subject,
            truth = candidate.after,
            readerState = ReaderKnowledgeState.UNKNOWN,
            revealPolicy = KnowledgeRevealPolicy.HIDDEN,
            earliestFullRevealChapter = (candidate.sourceChapter + 1).coerceAtLeast(1),
            triggerTerms = candidate.after.split('，', '。', '；', ',', ';')
                .map { it.trim() }.filter { it.length in 4..40 }.take(4),
            note = "由候选事实经作者确认进入 Canon",
        )
        return snapshot.copy(
            knowledgeLedger = snapshot.knowledgeLedger + boundary,
            factHistory = (snapshot.factHistory + provenance(snapshot, candidate, now)).takeLast(1_200),
        )
    }

    private fun provenance(snapshot: StorySnapshot, candidate: CandidateFact, now: Long) = FactProvenance(
        id = UUID.randomUUID().toString(),
        novelId = snapshot.novel.id,
        chapter = candidate.sourceChapter,
        kind = "CANDIDATE_${candidate.kind.name}",
        subject = candidate.subject,
        before = candidate.before,
        after = candidate.after,
        evidence = candidate.evidence,
        recordedAt = now,
    )

    private fun evidenceIsInProse(evidence: String, prose: String): Boolean {
        val needle = normalize(evidence)
        val haystack = normalize(prose)
        if (needle.length < 6 || haystack.isBlank()) return false
        if (haystack.contains(needle)) return true
        // Agent evidence may add a tiny prefix/suffix; a stable 10-char excerpt still counts as direct proof.
        return needle.windowed(size = minOf(14, needle.length), step = 3, partialWindows = false)
            .any { it.length >= 10 && haystack.contains(it) }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[\\s，。！？、；：,.!?;:\\-—_()（）《》\"“”'‘’]"), "")

    private fun signature(candidate: CandidateFact): String = listOf(
        candidate.sourceChapter.toString(), candidate.kind.name, normalize(candidate.subject), normalize(candidate.after)
    ).joinToString("|")

    private fun riskWeight(risk: CandidateFactRisk): Int = when (risk) {
        CandidateFactRisk.HIGH -> 3
        CandidateFactRisk.MEDIUM -> 2
        CandidateFactRisk.LOW -> 1
    }

    private fun AgentActionKind.toCandidateKind(): CandidateFactKind? = when (this) {
        AgentActionKind.CHARACTER_NEW -> CandidateFactKind.CHARACTER_NEW
        AgentActionKind.CHARACTER_LOCATION -> CandidateFactKind.CHARACTER_LOCATION
        AgentActionKind.CHARACTER_EMOTION -> CandidateFactKind.CHARACTER_EMOTION
        AgentActionKind.CHARACTER_GOAL -> CandidateFactKind.CHARACTER_GOAL
        AgentActionKind.RELATION -> CandidateFactKind.RELATION
        AgentActionKind.KNOWLEDGE_GAIN -> CandidateFactKind.KNOWLEDGE_GAIN
        AgentActionKind.TIMELINE -> CandidateFactKind.TIMELINE
        AgentActionKind.FORESHADOW_NEW -> CandidateFactKind.FORESHADOW_NEW
        AgentActionKind.FORESHADOW_UPDATE -> CandidateFactKind.FORESHADOW_UPDATE
        else -> null
    }

    private fun CandidateFactKind.toAgentKind(): AgentActionKind? = when (this) {
        CandidateFactKind.CHARACTER_NEW -> AgentActionKind.CHARACTER_NEW
        CandidateFactKind.CHARACTER_LOCATION -> AgentActionKind.CHARACTER_LOCATION
        CandidateFactKind.CHARACTER_EMOTION -> AgentActionKind.CHARACTER_EMOTION
        CandidateFactKind.CHARACTER_GOAL -> AgentActionKind.CHARACTER_GOAL
        CandidateFactKind.RELATION -> AgentActionKind.RELATION
        CandidateFactKind.KNOWLEDGE_GAIN -> AgentActionKind.KNOWLEDGE_GAIN
        CandidateFactKind.TIMELINE -> AgentActionKind.TIMELINE
        CandidateFactKind.FORESHADOW_NEW -> AgentActionKind.FORESHADOW_NEW
        CandidateFactKind.FORESHADOW_UPDATE -> AgentActionKind.FORESHADOW_UPDATE
        CandidateFactKind.BIBLE_ENTRY,
        CandidateFactKind.KNOWLEDGE_BOUNDARY -> null
    }
}
