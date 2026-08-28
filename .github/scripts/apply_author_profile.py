from pathlib import Path

ROOT = Path('.')

def replace_once(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing pattern in {path}: {old[:120]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# 1) Long-form models: backward-compatible author preference profile.
path = 'app/src/main/java/com/xiguli/langhuan/domain/LongFormModels.kt'
p = ROOT / path
text = p.read_text(encoding='utf-8')
marker = '''@Serializable
data class LongFormState(
'''
insert = r'''@Serializable
enum class AuthorLearningSource {
    MANUAL_EDIT,
    AI_REWRITE_ACCEPTED,
    AI_REWRITE_REJECTED,
}

@Serializable
enum class AuthorPreferenceKind {
    PROSE,
    DIALOGUE,
    PACING,
    EXPLANATION,
    DESCRIPTION,
    RHYTHM,
    OTHER,
}

/** A compact edit event. Only the changed excerpts are kept; full chapter history stays in versions. */
@Serializable
data class AuthorEditSignal(
    val id: String,
    val chapterNumber: Int,
    val source: AuthorLearningSource,
    val beforeExcerpt: String = "",
    val afterExcerpt: String = "",
    val instruction: String = "",
    val deltaChars: Int = 0,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
)

/** Stable writing preference distilled from repeated edits or an explicitly accepted instruction. */
@Serializable
data class AuthorPreferenceRule(
    val id: String,
    val kind: AuthorPreferenceKind = AuthorPreferenceKind.OTHER,
    val instruction: String,
    val confidence: Int = 50,
    val evidenceCount: Int = 1,
    val lastChapter: Int = 0,
    val active: Boolean = true,
)

@Serializable
data class AuthorPreferenceProfile(
    val enabled: Boolean = true,
    val rules: List<AuthorPreferenceRule> = emptyList(),
    val recentSignals: List<AuthorEditSignal> = emptyList(),
    val manualEditBatches: Int = 0,
    val acceptedAiRewrites: Int = 0,
    val rejectedAiRewrites: Int = 0,
    val updatedAt: Long = 0L,
)

'''
if marker not in text:
    raise SystemExit('LongFormState marker missing')
text = text.replace(marker, insert + marker, 1)
old = '''    /** Outstanding narrative promises. These may trigger planning pressure but never rewrite Canon. */
    val narrativeDebts: List<NarrativeDebt> = emptyList(),
    val lastSettledChapter: Int = 0,
)'''
new = '''    /** Outstanding narrative promises. These may trigger planning pressure but never rewrite Canon. */
    val narrativeDebts: List<NarrativeDebt> = emptyList(),
    /** Learns stable prose preferences from accepted/rejected rewrites and meaningful manual edit batches. */
    val authorProfile: AuthorPreferenceProfile = AuthorPreferenceProfile(),
    val lastSettledChapter: Int = 0,
)'''
if old not in text:
    raise SystemExit('LongFormState tail missing')
text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')

# 2) Pure local preference learner. It never changes Canon and never stores whole chapters.
engine = r'''package com.xiguli.langhuan.engine

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
'''
(ROOT / 'app/src/main/java/com/xiguli/langhuan/engine/AuthorPreferenceEngine.kt').write_text(engine, encoding='utf-8')

# 3) Feed stable learned rules into C/style context only.
path = 'app/src/main/java/com/xiguli/langhuan/engine/ContextBuilder.kt'
p = ROOT / path
text = p.read_text(encoding='utf-8')
old = '''        val styleItems = snapshot.bible
            .filter { it.category == BibleCategory.STYLE }
            .filterNot { it.name == CREATION_FACT_LEDGER }
            .map { "${it.name}：${it.content}${if (it.locked) "（必须遵守）" else "（偏好）"}" }
            .ifEmpty { listOf("保持自然、具体、有场景感的中文小说叙事；不要写成设定说明或案件报告。") }
        trace += ContextTraceEntry(ContextLayer.C_STYLE, "作品文风", "控制叙事声音，不得覆盖 S/A 层事实")
        val style = fitItems(styleItems, 4_000)
'''
new = '''        val declaredStyle = snapshot.bible
            .filter { it.category == BibleCategory.STYLE }
            .filterNot { it.name == CREATION_FACT_LEDGER }
            .map { "${it.name}：${it.content}${if (it.locked) "（必须遵守）" else "（偏好）"}" }
        val learnedStyle = AuthorPreferenceEngine.promptText(snapshot)
        val styleItems = buildList {
            if (declaredStyle.isEmpty()) add("保持自然、具体、有场景感的中文小说叙事；不要写成设定说明或案件报告。")
            else addAll(declaredStyle)
            if (learnedStyle.isNotBlank()) add(learnedStyle)
        }
        trace += ContextTraceEntry(ContextLayer.C_STYLE, "作品文风/作者编辑画像", "只控制叙事声音；学习偏好不得覆盖 S/A 层事实")
        val style = fitItems(styleItems, 4_800)
'''
if old not in text:
    raise SystemExit('ContextBuilder style block missing')
p.write_text(text.replace(old, new, 1), encoding='utf-8')

# 4) Editor learns meaningful stable diffs, accepted rewrite instructions, and explicit rejection.
path = 'app/src/main/java/com/xiguli/langhuan/ui/ChapterEditorViewModel.kt'
p = ROOT / path
text = p.read_text(encoding='utf-8')
text = text.replace('''import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
''', '''import com.xiguli.langhuan.domain.AuthorLearningSource
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
''', 1)
text = text.replace('''import com.xiguli.langhuan.engine.ChapterDependencyAnalyzer
''', '''import com.xiguli.langhuan.engine.AuthorPreferenceEngine
import com.xiguli.langhuan.engine.ChapterDependencyAnalyzer
''', 1)
text = text.replace('''    private var autosaveJob: Job? = null
''', '''    private var autosaveJob: Job? = null
    private var lastPersistedContent: String = ""
    private var pendingLearningSource: AuthorLearningSource = AuthorLearningSource.MANUAL_EDIT
    private var pendingLearningInstruction: String = ""
''', 1)

# Set baseline after initial load.
old = '''                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        chapters = chapters.replaceDraft(loaded.draft),
                        versions = versions,
                        dirty = false,
                        isLoading = false,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
'''
new = '''                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        chapters = chapters.replaceDraft(loaded.draft),
                        versions = versions,
                        dirty = false,
                        isLoading = false,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
                lastPersistedContent = loaded.draft.content
                pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                pendingLearningInstruction = ""
            }.onFailure { error ->
'''
if old not in text:
    raise SystemExit('initial load block missing')
text = text.replace(old, new, 1)

old = '''        _state.update { it.copy(isSaving = true, error = null) }
        return runCatching {
            if (createVersion) store.checkpoint(snapshot, draft) else store.autosave(snapshot, draft)
        }.fold(
            onSuccess = { persisted ->
                val versions = if (createVersion) store.versions(persisted.draft.novelId, persisted.draft.chapterNumber) else _state.value.versions
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        chapters = it.chapters.replaceDraft(persisted.draft),
                        versions = versions,
                        dirty = false,
                        isSaving = false,
                        lastSavedAt = System.currentTimeMillis(),
                        message = if (announce) "已创建版本 v${persisted.draft.version}" else it.message,
                    )
                }
                true
            },
'''
new = '''        _state.update { it.copy(isSaving = true, error = null) }
        val baseline = lastPersistedContent
        val learningSource = pendingLearningSource
        val learningInstruction = pendingLearningInstruction
        return runCatching {
            val persisted = if (createVersion) store.checkpoint(snapshot, draft) else store.autosave(snapshot, draft)
            val profiledSnapshot = AuthorPreferenceEngine.observeEdit(
                snapshot = persisted.snapshot,
                chapterNumber = persisted.draft.chapterNumber,
                before = baseline,
                after = persisted.draft.content,
                source = learningSource,
                instruction = learningInstruction,
            )
            if (profiledSnapshot != persisted.snapshot) store.autosave(profiledSnapshot, persisted.draft) else persisted
        }.fold(
            onSuccess = { persisted ->
                val versions = if (createVersion) store.versions(persisted.draft.novelId, persisted.draft.chapterNumber) else _state.value.versions
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        chapters = it.chapters.replaceDraft(persisted.draft),
                        versions = versions,
                        dirty = false,
                        isSaving = false,
                        lastSavedAt = System.currentTimeMillis(),
                        message = if (announce) "已创建版本 v${persisted.draft.version}" else it.message,
                    )
                }
                lastPersistedContent = persisted.draft.content
                pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                pendingLearningInstruction = ""
                true
            },
'''
if old not in text:
    raise SystemExit('persist block missing')
text = text.replace(old, new, 1)

# Open chapter baseline: replace the next identical state-success tail after openChapter.
old = '''                        repairPlan = null,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "切换章节失败") }
'''
new = '''                        repairPlan = null,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
                lastPersistedContent = loaded.draft.content
                pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                pendingLearningInstruction = ""
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "切换章节失败") }
'''
if old not in text:
    raise SystemExit('open chapter success block missing')
text = text.replace(old, new, 1)

# Feed learned profile to selection rewrite.
old = '''                val chronology = current.snapshot?.let { ChronologyGuard().promptText(it, draft.scenePlan) }.orEmpty()
                val output = gateway.generate(
'''
new = '''                val chronology = current.snapshot?.let { ChronologyGuard().promptText(it, draft.scenePlan) }.orEmpty()
                val learnedStyle = current.snapshot?.let { AuthorPreferenceEngine.promptText(it) }.orEmpty()
                val output = gateway.generate(
'''
if old not in text:
    raise SystemExit('rewrite chronology block missing')
text = text.replace(old, new, 1)
old = '''                            【时间轴锁】
                            $chronology

                            选区前文：
'''
new = '''                            【时间轴锁】
                            $chronology

                            【已学习作者偏好｜只控制表达】
                            ${learnedStyle.ifBlank { "暂无稳定学习规则。" }}

                            选区前文：
'''
if old not in text:
    raise SystemExit('rewrite prompt insertion missing')
text = text.replace(old, new, 1)

# Mark applied rewrite as strong positive preference before autosave.
old = '''        val updatedContent = draft.content.substring(0, proposal.start) + proposal.replacement + draft.content.substring(proposal.end)
        val updated = draft.copy(content = updatedContent)
        _state.update {
'''
new = '''        val updatedContent = draft.content.substring(0, proposal.start) + proposal.replacement + draft.content.substring(proposal.end)
        val updated = draft.copy(content = updatedContent)
        pendingLearningSource = AuthorLearningSource.AI_REWRITE_ACCEPTED
        pendingLearningInstruction = proposal.instruction
        _state.update {
'''
if old not in text:
    raise SystemExit('apply rewrite block missing')
text = text.replace(old, new, 1)

old = '''    fun dismissRewrite() = _state.update { it.copy(rewriteProposal = null) }

    fun compare(version: StoredChapterVersion) {
'''
new = '''    fun dismissRewrite() = _state.update { it.copy(rewriteProposal = null) }

    fun rejectRewriteAndLearn() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        val proposal = current.rewriteProposal ?: return
        if (current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val profiled = AuthorPreferenceEngine.observeEdit(
                    snapshot = snapshot,
                    chapterNumber = draft.chapterNumber,
                    before = proposal.replacement,
                    after = proposal.original,
                    source = AuthorLearningSource.AI_REWRITE_REJECTED,
                )
                store.autosave(profiled, draft)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        chapters = it.chapters.replaceDraft(persisted.draft),
                        isSaving = false,
                        rewriteProposal = null,
                        message = "已记住这次明确拒绝；只有重复出现的倾向才会升级为稳定偏好",
                    )
                }
                lastPersistedContent = persisted.draft.content
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "记录偏好失败") }
            }
        }
    }

    fun setAuthorLearningEnabled(enabled: Boolean) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { store.autosave(AuthorPreferenceEngine.setEnabled(snapshot, enabled), draft) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            chapters = it.chapters.replaceDraft(persisted.draft),
                            isSaving = false,
                            message = if (enabled) "作者编辑画像学习已开启" else "作者编辑画像学习已关闭；已有规则保留但不会注入写作",
                        )
                    }
                    lastPersistedContent = persisted.draft.content
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "更新画像设置失败") } }
        }
    }

    fun clearAuthorProfile() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching { store.autosave(AuthorPreferenceEngine.clear(snapshot), draft) }
                .onSuccess { persisted ->
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            chapters = it.chapters.replaceDraft(persisted.draft),
                            isSaving = false,
                            message = "作者编辑画像已重置",
                        )
                    }
                    lastPersistedContent = persisted.draft.content
                }
                .onFailure { error -> _state.update { it.copy(isSaving = false, error = error.message ?: "重置画像失败") } }
        }
    }

    fun compare(version: StoredChapterVersion) {
'''
if old not in text:
    raise SystemExit('dismiss rewrite marker missing')
text = text.replace(old, new, 1)

# Ensure immediate chronology repair / restore update the edit baseline.
old = '''            }.onSuccess { (persisted, versions, report) ->
                _state.update {
'''
new = '''            }.onSuccess { (persisted, versions, report) ->
                lastPersistedContent = persisted.draft.content
                pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                pendingLearningInstruction = ""
                _state.update {
'''
if old not in text:
    raise SystemExit('chronology success marker missing')
text = text.replace(old, new, 1)
old = '''                .onSuccess { persisted ->
                    val chapters = store.chapters(persisted.draft.novelId)
                    val versions = store.versions(persisted.draft.novelId, persisted.draft.chapterNumber)
'''
new = '''                .onSuccess { persisted ->
                    lastPersistedContent = persisted.draft.content
                    pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                    pendingLearningInstruction = ""
                    val chapters = store.chapters(persisted.draft.novelId)
                    val versions = store.versions(persisted.draft.novelId, persisted.draft.chapterNumber)
'''
if old not in text:
    raise SystemExit('restore success marker missing')
text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')

# 5) Editor UI exposes profile and explicit negative feedback.
path = 'app/src/main/java/com/xiguli/langhuan/ui/ChapterEditorPage.kt'
p = ROOT / path
text = p.read_text(encoding='utf-8')
# Add reset confirmation state.
old = '''    var showVersions by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<StoredChapterVersion?>(null) }
'''
new = '''    var showVersions by remember { mutableStateOf(false) }
    var restoreTarget by remember { mutableStateOf<StoredChapterVersion?>(null) }
    var confirmResetAuthorProfile by remember { mutableStateOf(false) }
'''
if old not in text:
    raise SystemExit('page state marker missing')
text = text.replace(old, new, 1)

# Add profile card between prose editor and chronology card.
old = '''            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .34f),
                ) {
'''
profile_card = '''            item {
                val profile = state.snapshot?.longForm?.authorProfile
                val learnedRules = profile?.rules.orEmpty()
                    .filter { it.active && it.confidence >= 60 }
                    .sortedByDescending { it.confidence }
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("作者编辑画像", fontWeight = FontWeight.Bold)
                                Text(
                                    "从稳定保存后的实际改稿学习；单次小改不会直接变成长期规则。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = profile?.enabled ?: true,
                                onCheckedChange = viewModel::setAuthorLearningEnabled,
                                enabled = !state.busy,
                            )
                        }
                        Text(
                            "稳定规则 ${learnedRules.size} 条 · 手动改稿 ${profile?.manualEditBatches ?: 0} 批 · 采用 AI 改写 ${profile?.acceptedAiRewrites ?: 0} 次 · 明确拒绝 ${profile?.rejectedAiRewrites ?: 0} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        learnedRules.take(4).forEach { rule ->
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(rule.instruction, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("置信 ${rule.confidence} · ${rule.evidenceCount} 次证据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (learnedRules.isEmpty()) {
                            Text("还没有达到稳定阈值的偏好。继续正常改稿即可，不需要专门训练。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if ((profile?.rules?.isNotEmpty() == true) || (profile?.recentSignals?.isNotEmpty() == true)) {
                            TextButton(onClick = { confirmResetAuthorProfile = true }, enabled = !state.busy) {
                                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("重置画像")
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .34f),
                ) {
'''
if old not in text:
    raise SystemExit('chronology card marker missing')
text = text.replace(old, profile_card, 1)

old = '''                    Text("AI 只会替换刚才选中的部分。应用后仍会先进入自动保存，不会自动写入长期事实记忆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
'''
new = '''                    Text("AI 只会替换刚才选中的部分。应用后会把这次明确采用的修改要求计入作者编辑画像，但不会写入剧情事实记忆。", color = MaterialTheme.colorScheme.onSurfaceVariant)
'''
if old not in text:
    raise SystemExit('rewrite dialog description missing')
text = text.replace(old, new, 1)
old = '''            confirmButton = { Button(onClick = viewModel::applyRewrite) { Text("应用替换") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRewrite) { Text("不要这版") } },
        )
    }

    state.comparison?.let { comparison ->
'''
new = '''            confirmButton = { Button(onClick = viewModel::applyRewrite) { Text("应用替换") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = viewModel::dismissRewrite) { Text("暂不采用") }
                    TextButton(onClick = viewModel::rejectRewriteAndLearn) { Text("不喜欢，记住") }
                }
            },
        )
    }

    if (confirmResetAuthorProfile) {
        AlertDialog(
            onDismissRequest = { confirmResetAuthorProfile = false },
            title = { Text("重置作者编辑画像？") },
            text = { Text("会清空从改稿中学习到的偏好规则和编辑信号，但不会修改正文、设定、大纲或历史版本。") },
            confirmButton = {
                Button(onClick = {
                    confirmResetAuthorProfile = false
                    viewModel.clearAuthorProfile()
                }) { Text("确认重置") }
            },
            dismissButton = { TextButton(onClick = { confirmResetAuthorProfile = false }) { Text("取消") } },
        )
    }

    state.comparison?.let { comparison ->
'''
if old not in text:
    raise SystemExit('rewrite dialog buttons missing')
text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')

# 6) Tests.
test = r'''package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.AuthorLearningSource
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.Novel
import com.xiguli.langhuan.domain.NovelStatus
import com.xiguli.langhuan.domain.StorySnapshot
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorPreferenceEngineTest {
    @Test
    fun `accepted rewrite instruction becomes active preference`() {
        val before = "周衍看着门。他觉得这里很危险，但还是决定进去。"
        val after = "周衍盯着门缝。里面没有声音。他把手搭上门把，停了两秒，还是压了下去。"
        val updated = AuthorPreferenceEngine.observeEdit(
            snapshot = snapshot(),
            chapterNumber = 4,
            before = before,
            after = after,
            source = AuthorLearningSource.AI_REWRITE_ACCEPTED,
            instruction = "对白更自然，减少网文腔，情绪不要直接解释",
            now = 100L,
        )
        val rules = updated.longForm.authorProfile.rules
        assertTrue(rules.any { it.id.startsWith("explicit:") && it.confidence >= 60 })
        assertTrue(AuthorPreferenceEngine.promptText(updated).contains("对白更自然"))
    }

    @Test
    fun `repeated removal of explanatory conclusions promotes stable rule`() {
        val before1 = "这说明他已经暴露了。这意味着门后的人早就知道他会来。显然，继续停留没有意义。" + "他站在楼道里。".repeat(30)
        val after1 = "门后没有动静。周衍收起手机，转身看向安全出口。" + "他站在楼道里。".repeat(30)
        val first = AuthorPreferenceEngine.observeEdit(snapshot(), 5, before1, after1, AuthorLearningSource.MANUAL_EDIT, now = 101L)
        val before2 = "这说明电话是假的。也就是说，对方一直在诱导他。由此可见，他不能再相信这个号码。" + "雨落在窗上。".repeat(30)
        val after2 = "号码又亮了一次。周衍没有接，把手机扣在桌面。" + "雨落在窗上。".repeat(30)
        val second = AuthorPreferenceEngine.observeEdit(first, 6, before2, after2, AuthorLearningSource.MANUAL_EDIT, now = 102L)
        val rule = second.longForm.authorProfile.rules.first { it.id == "less-explanation" }
        assertTrue(rule.evidenceCount >= 2)
        assertTrue(rule.confidence >= 60)
        assertTrue(AuthorPreferenceEngine.promptText(second).contains("减少解释性总结句"))
    }

    @Test
    fun `tiny manual typo does not pollute profile`() {
        val updated = AuthorPreferenceEngine.observeEdit(
            snapshot(), 2, "他走进房间。", "他走进房间里。", AuthorLearningSource.MANUAL_EDIT, now = 103L
        )
        assertTrue(updated.longForm.authorProfile.rules.isEmpty())
        assertTrue(updated.longForm.authorProfile.recentSignals.isEmpty())
    }

    private fun snapshot() = StorySnapshot(
        novel = Novel(
            id = "n1",
            title = "测试",
            genre = "悬疑",
            premise = "测试",
            theme = "选择",
            targetWords = 500_000,
            currentChapter = 1,
            status = NovelStatus.WRITING,
        ),
        activeOutline = emptyList(),
        bible = emptyList(),
        characters = emptyList(),
        recentTimeline = emptyList(),
        relevantForeshadowing = emptyList(),
        recentSummaries = emptyList(),
    )
}
'''
(ROOT / 'app/src/test/java/com/xiguli/langhuan/engine/AuthorPreferenceEngineTest.kt').write_text(test, encoding='utf-8')

# 7) Version bump.
path = 'app/build.gradle.kts'
p = ROOT / path
text = p.read_text(encoding='utf-8')
if 'versionCode = 57' not in text or 'versionName = "0.25.9-alpha01"' not in text:
    raise SystemExit('version baseline changed')
text = text.replace('versionCode = 57', 'versionCode = 58', 1)
text = text.replace('versionName = "0.25.9-alpha01"', 'versionName = "0.26.0-alpha01"', 1)
p.write_text(text, encoding='utf-8')

print('author preference learning stage applied')
