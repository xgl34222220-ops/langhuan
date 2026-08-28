package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.AuthorEditSignal
import com.xiguli.langhuan.domain.AuthorLearningSource
import com.xiguli.langhuan.domain.AuthorPreferenceKind
import com.xiguli.langhuan.domain.AuthorPreferenceProfile
import com.xiguli.langhuan.domain.AuthorPreferenceRule
import com.xiguli.langhuan.domain.StorySnapshot
import kotlin.math.abs

/**
 * Learns the author's editing habits from stable diffs.
 *
 * This engine is intentionally local and conservative: a single manual edit only creates low-confidence
 * candidates. Repeated evidence promotes them. Explicitly accepted rewrite instructions are stronger.
 * Rules only enter the C/style layer, so they can never override Canon, chronology, knowledge gates or contracts.
 */
object AuthorPreferenceEngine {
    fun observeEdit(
        snapshot: StorySnapshot,
        chapterNumber: Int,
        before: String,
        after: String,
        source: AuthorLearningSource,
        instruction: String = "",
        now: Long = System.currentTimeMillis(),
    ): StorySnapshot {
        val profile = snapshot.longForm.authorProfile
        if (!profile.enabled || before == after) return snapshot
        val diff = compactDiff(before, after)
        val explicit = instruction.trim().take(180)
        if (diff.changedChars < 24 && explicit.isBlank()) return snapshot

        val candidates = buildCandidates(before, after, source, explicit)
        if (candidates.isEmpty() && diff.changedChars < 80) return snapshot

        val signal = AuthorEditSignal(
            id = "edit:${chapterNumber}:${now}:${source.name}",
            chapterNumber = chapterNumber,
            source = source,
            beforeExcerpt = diff.before.take(520),
            afterExcerpt = diff.after.take(520),
            instruction = explicit,
            deltaChars = after.length - before.length,
            tags = candidates.map { it.id }.take(8),
            createdAt = now,
        )
        val rules = mergeRules(profile.rules, candidates, chapterNumber)
        val updated = profile.copy(
            rules = rules,
            recentSignals = (profile.recentSignals + signal).takeLast(40),
            manualEditBatches = profile.manualEditBatches + if (source == AuthorLearningSource.MANUAL_EDIT) 1 else 0,
            acceptedAiRewrites = profile.acceptedAiRewrites + if (source == AuthorLearningSource.AI_REWRITE_ACCEPTED) 1 else 0,
            rejectedAiRewrites = profile.rejectedAiRewrites + if (source == AuthorLearningSource.AI_REWRITE_REJECTED) 1 else 0,
            updatedAt = now,
        )
        return snapshot.copy(longForm = snapshot.longForm.copy(authorProfile = updated))
    }

    fun promptText(snapshot: StorySnapshot): String {
        val profile = snapshot.longForm.authorProfile
        if (!profile.enabled) return ""
        val active = profile.rules
            .filter { it.active && it.confidence >= 60 }
            .sortedWith(compareByDescending<AuthorPreferenceRule> { it.confidence }.thenByDescending { it.evidenceCount })
            .take(10)
        if (active.isEmpty()) return ""
        return buildString {
            appendLine("【作者编辑画像｜来自实际改稿，只控制表达，不得覆盖事实与剧情合同】")
            active.forEach { rule ->
                appendLine("- ${rule.instruction}（置信=${rule.confidence}，证据=${rule.evidenceCount}次）")
            }
        }.trim()
    }

    fun setEnabled(snapshot: StorySnapshot, enabled: Boolean): StorySnapshot =
        snapshot.copy(
            longForm = snapshot.longForm.copy(
                authorProfile = snapshot.longForm.authorProfile.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
            )
        )

    fun clear(snapshot: StorySnapshot): StorySnapshot =
        snapshot.copy(
            longForm = snapshot.longForm.copy(
                authorProfile = AuthorPreferenceProfile(enabled = snapshot.longForm.authorProfile.enabled)
            )
        )

    private data class Candidate(
        val id: String,
        val kind: AuthorPreferenceKind,
        val text: String,
        val baseConfidence: Int,
        val boost: Int,
    )

    private data class Diff(val before: String, val after: String, val changedChars: Int)

    private fun compactDiff(before: String, after: String): Diff {
        var prefix = 0
        val maxPrefix = minOf(before.length, after.length)
        while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(before.length - prefix, after.length - prefix)
        while (suffix < maxSuffix && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
        val beforeEnd = (before.length - suffix).coerceAtLeast(prefix)
        val afterEnd = (after.length - suffix).coerceAtLeast(prefix)
        val bStart = (prefix - 120).coerceAtLeast(0)
        val aStart = (prefix - 120).coerceAtLeast(0)
        val bEnd = (beforeEnd + 120).coerceAtMost(before.length)
        val aEnd = (afterEnd + 120).coerceAtMost(after.length)
        return Diff(
            before = before.substring(bStart, bEnd),
            after = after.substring(aStart, aEnd),
            changedChars = (beforeEnd - prefix) + (afterEnd - prefix),
        )
    }

    private fun buildCandidates(
        before: String,
        after: String,
        source: AuthorLearningSource,
        explicit: String,
    ): List<Candidate> = buildList {
        if (source == AuthorLearningSource.AI_REWRITE_ACCEPTED && explicit.isNotBlank()) {
            add(
                Candidate(
                    id = "explicit:${stableKey(explicit)}",
                    kind = AuthorPreferenceKind.PROSE,
                    text = "优先遵守作者明确采用过的修改要求：$explicit",
                    baseConfidence = 76,
                    boost = 8,
                )
            )
        }

        val size = maxOf(before.length, 1)
        val lengthRatio = (after.length - before.length).toDouble() / size
        if (abs(after.length - before.length) >= 100) {
            if (lengthRatio <= -0.12) add(Candidate("concise", AuthorPreferenceKind.PACING, "表达保持克制紧凑，避免同义复述和为凑字反复解释。", 48, 9))
            if (lengthRatio >= 0.15) add(Candidate("concrete-detail", AuthorPreferenceKind.DESCRIPTION, "需要展开时优先补动作、感官和场景细节，不要只给结论。", 48, 9))
        }

        val explanationMarkers = listOf("这说明", "这意味着", "显然", "也就是说", "换句话说", "不难看出", "由此可见")
        val beforeExplain = explanationMarkers.sumOf { countLiteral(before, it) }
        val afterExplain = explanationMarkers.sumOf { countLiteral(after, it) }
        if (beforeExplain - afterExplain >= 2) {
            add(Candidate("less-explanation", AuthorPreferenceKind.EXPLANATION, "减少解释性总结句，让含义尽量由动作、细节和上下文自己成立。", 50, 10))
        }

        val cliches = listOf("仿佛", "似乎", "那一刻", "某种", "无声地", "缓缓", "微微", "猛地", "不由得", "空气中")
        val beforeCliche = cliches.sumOf { countLiteral(before, it) }
        val afterCliche = cliches.sumOf { countLiteral(after, it) }
        if (beforeCliche - afterCliche >= 2) {
            add(Candidate("less-ai-cliche", AuthorPreferenceKind.PROSE, "减少模板化 AI 腔词和泛化氛围词，优先使用具体、可观察的表达。", 50, 10))
        }

        val beforeBang = before.count { it == '！' || it == '!' }
        val afterBang = after.count { it == '！' || it == '!' }
        if (beforeBang - afterBang >= 2) {
            add(Candidate("restrained-tone", AuthorPreferenceKind.RHYTHM, "语气保持克制，少用连续感叹号和强行拔高情绪。", 48, 9))
        }

        val beforeDialogue = dialogueRatio(before)
        val afterDialogue = dialogueRatio(after)
        if (afterDialogue - beforeDialogue >= 0.08 && diffEnough(before, after)) {
            add(Candidate("more-dialogue", AuthorPreferenceKind.DIALOGUE, "人物互动场景可提高有效对白占比，用对白承载关系、选择和信息推进。", 47, 8))
        } else if (beforeDialogue - afterDialogue >= 0.08 && diffEnough(before, after)) {
            add(Candidate("less-dialogue", AuthorPreferenceKind.DIALOGUE, "避免对白过密；需要时用动作、观察和停顿承接人物关系与信息。", 47, 8))
        }

        val beforeSentence = averageSentenceLength(before)
        val afterSentence = averageSentenceLength(after)
        if (beforeSentence - afterSentence >= 5.0 && diffEnough(before, after)) {
            add(Candidate("shorter-sentences", AuthorPreferenceKind.RHYTHM, "关键段落偏好更利落的句子，避免一个句子塞入过多解释层。", 47, 8))
        } else if (afterSentence - beforeSentence >= 6.0 && diffEnough(before, after)) {
            add(Candidate("longer-flow", AuthorPreferenceKind.RHYTHM, "非高压段落允许更连贯的长句推进，避免把完整动作切得过碎。", 47, 8))
        }
    }.distinctBy { it.id }

    private fun mergeRules(
        existing: List<AuthorPreferenceRule>,
        candidates: List<Candidate>,
        chapterNumber: Int,
    ): List<AuthorPreferenceRule> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        candidates.forEach { candidate ->
            val old = byId[candidate.id]
            byId[candidate.id] = if (old == null) {
                AuthorPreferenceRule(
                    id = candidate.id,
                    kind = candidate.kind,
                    instruction = candidate.text,
                    confidence = candidate.baseConfidence,
                    evidenceCount = 1,
                    lastChapter = chapterNumber,
                )
            } else {
                old.copy(
                    instruction = candidate.text,
                    confidence = maxOf(old.confidence, candidate.baseConfidence).plus(candidate.boost).coerceAtMost(96),
                    evidenceCount = old.evidenceCount + 1,
                    lastChapter = chapterNumber,
                    active = true,
                )
            }
        }
        return byId.values
            .sortedWith(compareByDescending<AuthorPreferenceRule> { it.confidence }.thenByDescending { it.evidenceCount })
            .take(32)
    }

    private fun countLiteral(text: String, token: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(token, index)
            if (index < 0) return count
            count++
            index += token.length.coerceAtLeast(1)
        }
    }

    private fun dialogueRatio(text: String): Double {
        if (text.isBlank()) return 0.0
        val quoteChars = text.count { it == '“' || it == '”' || it == '「' || it == '」' || it == '『' || it == '』' }
        return quoteChars.toDouble() / text.length.toDouble()
    }

    private fun averageSentenceLength(text: String): Double {
        val parts = text.split(Regex("[。！？!?]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        if (parts.isEmpty()) return text.length.toDouble()
        return parts.sumOf { it.length }.toDouble() / parts.size.toDouble()
    }

    private fun diffEnough(before: String, after: String): Boolean =
        abs(before.length - after.length) >= 60 || maxOf(before.length, after.length) >= 240

    private fun stableKey(text: String): String = text.trim().lowercase().hashCode().toUInt().toString(16)
}
