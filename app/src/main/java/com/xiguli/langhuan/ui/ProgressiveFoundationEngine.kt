package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.FoundationBibleItem
import com.xiguli.langhuan.data.FoundationChapter
import com.xiguli.langhuan.data.FoundationCharacter
import com.xiguli.langhuan.data.FoundationForeshadow
import com.xiguli.langhuan.data.FoundationVolume
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 分阶段建书引擎。
 *
 * 用户上传并被识别为“作品设定”的文件是硬约束，不是参考素材。
 * AI 可以补缺口和整理表达，但不能删卷、并卷、把类别当人物，或偷换明确规则和终局。
 */
internal class ProgressiveFoundationEngine(
    private val gateway: AiGateway,
) {
    suspend fun build(
        proposal: NewBookProposal,
        messages: List<CreationChatMessage>,
        current: StoryFoundation?,
        instruction: String,
        referenceContext: String = "",
        resumeStage: Int = 0,
        onStage: (String) -> Unit = {},
        onCheckpoint: (Int, StoryFoundation) -> Unit = { _, _ -> },
    ): StoryFoundation {
        val conversation = compactConversation(messages)
        val locks = BlueprintLocks.from(messages)
        val safeResume = resumeStage.coerceIn(0, 2)
        var working = current ?: seedFromLocks(proposal, locks)

        if (safeResume < 1 || !coreUsable(working, locks)) {
            onStage("1/3 · 正在按上传原设整理世界、规则与总纲……")
            val world = requestOptional(
                stage = "1/3 世界与总纲",
                prompt = PromptBundle(
                    system = WORLD_SYSTEM,
                    user = buildString {
                        appendHardLocks(locks)
                        appendLine("【事实优先级】")
                        appendLine("最新用户明确修改 > 用户上传作品设定原文 > 当前方案缓存 > 琅嬛旧建议。附件未被用户修改的内容必须保持语义不变。")
                        appendLine()
                        appendLine("【完整会谈】")
                        appendLine(conversation)
                        appendLine()
                        appendProposal(proposal)
                        if (referenceContext.isNotBlank()) {
                            appendLine()
                            appendLine("【用户显式选择的参考 DNA，仅可提炼高层方法】")
                            appendLine(referenceContext)
                        }
                        appendLine()
                        appendLine("【本轮要求】")
                        appendLine(instruction)
                    },
                ),
            )

            onStage("1/3 · 正在按上传原设整理人物与完整分卷……")
            val cast = requestOptional(
                stage = "1/3 人物与分卷",
                prompt = PromptBundle(
                    system = CAST_SYSTEM,
                    user = buildString {
                        appendHardLocks(locks)
                        appendLine("【完整会谈】")
                        appendLine(conversation)
                        appendLine()
                        appendProposal(proposal)
                        world?.let {
                            appendLine()
                            appendLine("【刚生成的世界/总纲，仅作补充，不得覆盖附件原设】")
                            appendLine(compactGenerated(it))
                        }
                        if (locks.coreCharacterNames.isNotEmpty()) {
                            appendLine()
                            appendLine("【附件明确命名的核心人物】${locks.coreCharacterNames.joinToString("、")}")
                            appendLine("核心人物只能从上述明确命名人物中生成。类别、身份称谓、群体和组织不得升格成核心人物。")
                        }
                        locks.expectedVolumeCount?.let {
                            appendLine()
                            appendLine("【锁定卷数】$it 卷。必须恰好输出 $it 条 VOLUME，禁止删卷、并卷或改序。")
                        }
                        if (referenceContext.isNotBlank()) {
                            appendLine()
                            appendLine("【参考 DNA】")
                            appendLine(referenceContext)
                        }
                    },
                ),
            )

            working = mergeCore(
                base = seedFromLocks(proposal, locks, working),
                proposal = proposal,
                world = world,
                cast = cast,
                locks = locks,
            )
            validateCore(working, locks)
            onCheckpoint(1, working)
        } else {
            onStage("1/3 · 已恢复核心蓝图断点")
        }

        if (safeResume < 2 || firstVolumeChapterCount(working) < 8) {
            var chapterCount = firstVolumeChapterCount(working)
            if (chapterCount < 5) {
                onStage("2/3 · 正在生成第一卷第 1–5 章……")
                requestOptional(
                    stage = "2/3 第1–5章",
                    prompt = chapterPrompt(working, locks, referenceContext, 1, 5, emptyList()),
                )?.let { working = mergeChapters(working, it) }
                chapterCount = firstVolumeChapterCount(working)
                if (chapterCount > 0) onCheckpoint(1, working)
            }

            if (chapterCount < 8) {
                onStage("2/3 · 正在生成第一卷第 6–10 章……")
                requestOptional(
                    stage = "2/3 第6–10章",
                    prompt = chapterPrompt(
                        working,
                        locks,
                        referenceContext,
                        6,
                        10,
                        working.volumes.firstOrNull { it.order == 1 }?.chapters.orEmpty(),
                    ),
                )?.let { working = mergeChapters(working, it) }
                chapterCount = firstVolumeChapterCount(working)
            }

            if (chapterCount >= 5) onCheckpoint(2, working)
        } else {
            onStage("2/3 · 已恢复第一卷章纲断点")
        }

        onStage("3/3 · 正在按原设整理伏笔计划……")
        requestOptional(
            stage = "3/3 伏笔计划",
            prompt = PromptBundle(
                system = FORESHADOW_SYSTEM,
                user = buildString {
                    appendHardLocks(locks)
                    appendLine("【核心蓝图 + 当前第一卷章纲】")
                    appendLine(compactFoundation(working, includeChapters = true))
                    if (referenceContext.isNotBlank()) {
                        appendLine()
                        appendLine("【参考 DNA】")
                        appendLine(referenceContext)
                    }
                    appendLine()
                    appendLine("只补伏笔计划。附件已经写明的谜底释放、误导、卷末揭晓和终局信息不得提前或改写。")
                },
            ),
        )?.let { working = mergeForeshadowing(working, it) }

        if (working.foreshadowing.size >= 3) onCheckpoint(3, working)
        return working
    }

    /** 单个阶段最多等待 75 秒，避免 UI 永久卡在 2/3。 */
    private suspend fun requestOptional(stage: String, prompt: PromptBundle): GeneratedChapter? = try {
        withTimeout(STAGE_TIMEOUT_MS) { gateway.generate(prompt) }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun chapterPrompt(
        foundation: StoryFoundation,
        locks: BlueprintLocks,
        referenceContext: String,
        start: Int,
        end: Int,
        prior: List<FoundationChapter>,
    ): PromptBundle = PromptBundle(
        system = """
            你是“琅嬛”的第一卷章纲设计师。只生成第一卷第 $start–$end 章。
            不得修改书名、人物、世界规则、卷数、卷名或用户附件中的既定剧情节点。
            如果附件明确写了“前 N 章现实线”“第 N 章进入某副本/事件”等安排，必须严格保持章节位置和事件顺序。
            输出 GeneratedChapter JSON：title="FIRST_VOLUME_${start}_${end}"；content=""；summary=本批节奏摘要；touchedForeshadowingIds=[]。
            stateChanges 只允许 CHAPTER:1:序号，序号覆盖 $start 到 $end：field=章名；before=本章目标；after=本章冲突；evidence=章末转折。
        """.trimIndent(),
        user = buildString {
            appendHardLocks(locks)
            appendLine("【核心蓝图】")
            appendLine(compactFoundation(foundation))
            if (prior.isNotEmpty()) {
                appendLine()
                appendLine("【已经确认的前置章纲】")
                prior.sortedBy { it.order }.takeLast(8).forEach {
                    appendLine("${it.order}. ${it.title}：${it.objective} / ${it.conflict} / ${it.turningPoint}")
                }
            }
            if (referenceContext.isNotBlank()) {
                appendLine()
                appendLine("【参考 DNA 的节奏方法】")
                appendLine(referenceContext)
            }
        },
    )

    private fun seedFromLocks(
        proposal: NewBookProposal,
        locks: BlueprintLocks,
        current: StoryFoundation? = null,
    ): StoryFoundation {
        val lockedBible = locks.sections
            .filterNot { section ->
                listOf("简介", "梗概", "主角", "人物", "角色", "分卷", "副本示例").any { section.title.contains(it) }
            }
            .mapNotNull { section ->
                val content = section.body.trim()
                if (content.isBlank()) return@mapNotNull null
                FoundationBibleItem(
                    category = categoryForSection(section.title),
                    name = cleanHeading(section.title),
                    content = content.take(MAX_LOCKED_SECTION_CHARS),
                    locked = true,
                )
            }
            .distinctBy { it.category to it.name }
            .take(24)

        val lockedCharacters = locks.coreCharacterNames.map { name ->
            current?.characters?.firstOrNull { it.name == name }
                ?: characterFromSource(name, locks.source)
        }

        val volumes = when {
            locks.lockedVolumes.isNotEmpty() -> locks.lockedVolumes.map { locked ->
                val old = current?.volumes?.firstOrNull { it.order == locked.order }
                locked.copy(chapters = old?.chapters.orEmpty())
            }
            !current?.volumes.isNullOrEmpty() -> current!!.volumes
            else -> emptyList()
        }

        val creationBrief = buildString {
            if (proposal.decisionLedger.isNotBlank()) appendLine(proposal.decisionLedger.trim())
            if (locks.source.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                appendLine("【用户上传作品设定原文｜硬约束】")
                appendLine(locks.source)
            }
        }.trim()

        return StoryFoundation(
            title = locks.title ?: current?.title ?: proposal.title,
            genre = locks.genre ?: current?.genre ?: proposal.genre,
            premise = locks.premise ?: current?.premise ?: proposal.premise,
            theme = current?.theme ?: proposal.theme,
            targetWords = locks.targetWords ?: current?.targetWords ?: proposal.targetWords,
            coreHook = current?.coreHook ?: proposal.coreHook,
            storyPromise = current?.storyPromise ?: proposal.rationale,
            styleGuide = locks.styleGuide ?: current?.styleGuide ?: "人物行动有因果，信息逐层释放；用户锁定规则不得临时改写。",
            coverBrief = current?.coverBrief ?: proposal.coverBrief,
            masterTitle = current?.masterTitle ?: "总纲",
            masterObjective = current?.masterObjective ?: locks.storySummary ?: proposal.premise,
            masterConflict = current?.masterConflict ?: "主角追求核心目标，同时承担用户设定中不断升级且不可回避的代价。",
            masterTurningPoint = current?.masterTurningPoint ?: "按用户锁定的分卷和终局方向逐步揭晓。",
            bible = (lockedBible + current?.bible.orEmpty()).distinctBy { it.category to it.name }.take(32),
            characters = if (lockedCharacters.isNotEmpty()) lockedCharacters else current?.characters.orEmpty(),
            volumes = volumes,
            foreshadowing = current?.foreshadowing.orEmpty(),
            creationBrief = creationBrief.ifBlank { current?.creationBrief.orEmpty() },
        )
    }

    private fun mergeCore(
        base: StoryFoundation,
        proposal: NewBookProposal,
        world: GeneratedChapter?,
        cast: GeneratedChapter?,
        locks: BlueprintLocks,
    ): StoryFoundation {
        val allChanges = world?.stateChanges.orEmpty() + cast?.stateChanges.orEmpty()
        val meta = allChanges.firstOrNull { subject(it) in META_SUBJECTS }
        val style = allChanges.firstOrNull { subject(it) in STYLE_SUBJECTS }
        val master = allChanges.firstOrNull { subject(it) in MASTER_SUBJECTS }

        val aiBible = allChanges.mapNotNull { change ->
            val category = bibleCategoryFromSubject(subject(change), change) ?: return@mapNotNull null
            val name = change.field.trim()
            val content = change.before.trim().ifBlank { change.after.trim() }
            if (name.isBlank() || content.isBlank()) return@mapNotNull null
            FoundationBibleItem(category, name, content, split(change.after).filterNot { it == content }, true)
        }

        val aiCharacters = allChanges.filter { isCharacterSubject(subject(it)) }.mapNotNull { change ->
            val name = change.field.trim()
            if (name.isBlank()) return@mapNotNull null
            val parts = change.evidence.split("||")
            FoundationCharacter(
                name = name,
                personality = split(change.before).ifEmpty { listOf("以用户原设为准") },
                location = parts.getOrNull(0).orEmpty().trim().ifBlank { "故事起点" },
                physicalState = parts.getOrNull(1).orEmpty().trim().ifBlank { "正常" },
                emotionalState = parts.getOrNull(2).orEmpty().trim().ifBlank { "平静" },
                goal = change.after.trim().ifBlank { "严格遵循用户锁定人物动机" },
                knownSecrets = split(parts.getOrNull(3).orEmpty()),
                possessions = split(parts.getOrNull(4).orEmpty()),
                relationships = parseRelationships(parts.getOrNull(5).orEmpty()),
            )
        }.distinctBy { it.name }

        val aiVolumes = allChanges.mapNotNull { change ->
            val order = volumeOrder(subject(change)) ?: return@mapNotNull null
            FoundationVolume(
                order = order,
                title = change.field.trim().ifBlank { "第${order}卷" },
                objective = change.before.trim(),
                conflict = change.after.trim(),
                turningPoint = change.evidence.trim(),
                chapters = base.volumes.firstOrNull { it.order == order }?.chapters.orEmpty(),
            )
        }.distinctBy { it.order }.sortedBy { it.order }.take(10)

        val characters = if (locks.coreCharacterNames.isNotEmpty()) {
            locks.coreCharacterNames.map { name ->
                aiCharacters.firstOrNull { it.name == name }
                    ?: base.characters.firstOrNull { it.name == name }
                    ?: characterFromSource(name, locks.source)
            }
        } else {
            aiCharacters.ifEmpty { base.characters }
        }

        val volumes = if (locks.lockedVolumes.isNotEmpty()) {
            locks.lockedVolumes.map { locked ->
                val ai = aiVolumes.firstOrNull { it.order == locked.order }
                locked.copy(
                    conflict = ai?.conflict?.takeIf(String::isNotBlank) ?: locked.conflict,
                    chapters = base.volumes.firstOrNull { it.order == locked.order }?.chapters.orEmpty(),
                )
            }
        } else {
            aiVolumes.ifEmpty { base.volumes }
        }

        return base.copy(
            title = locks.title ?: world?.title?.trim()?.takeIf { it.length in 2..18 } ?: base.title,
            genre = locks.genre ?: meaningfulValue(meta?.field, GENRE_PLACEHOLDERS, base.genre),
            premise = locks.premise ?: world?.content?.trim()?.takeIf { it.length >= 50 } ?: base.premise,
            theme = meaningfulValue(meta?.after, THEME_PLACEHOLDERS, base.theme),
            targetWords = locks.targetWords
                ?: meta?.before?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(10_000, 5_000_000)
                ?: base.targetWords,
            coreHook = meta?.evidence?.trim().orEmpty().ifBlank { base.coreHook.ifBlank { proposal.coreHook } },
            storyPromise = world?.summary?.trim().orEmpty().ifBlank { base.storyPromise },
            styleGuide = locks.styleGuide ?: style?.after?.trim().orEmpty().ifBlank { base.styleGuide },
            coverBrief = style?.evidence?.trim().orEmpty().ifBlank { base.coverBrief },
            masterTitle = master?.field?.trim().orEmpty().ifBlank { base.masterTitle },
            masterObjective = master?.before?.trim().orEmpty().ifBlank { base.masterObjective },
            masterConflict = master?.after?.trim().orEmpty().ifBlank { base.masterConflict },
            masterTurningPoint = master?.evidence?.trim().orEmpty().ifBlank { base.masterTurningPoint },
            bible = (base.bible + aiBible).distinctBy { it.category to it.name }.take(32),
            characters = characters.take(16),
            volumes = volumes.sortedBy { it.order }.take(10),
        )
    }

    private fun validateCore(foundation: StoryFoundation, locks: BlueprintLocks) {
        require(foundation.bible.size >= 3) {
            "核心世界规则不足（${foundation.bible.size}/3）。用户上传原设仍然保留，请重试核心蓝图。"
        }
        require(foundation.characters.size >= 2) {
            "核心人物不足（${foundation.characters.size}/2）。不会再用类别或群体名称冒充核心人物。"
        }
        if (locks.coreCharacterNames.isNotEmpty()) {
            val missing = locks.coreCharacterNames.filterNot { expected -> foundation.characters.any { it.name == expected } }
            require(missing.isEmpty()) { "缺少附件明确人物：${missing.joinToString("、")}" }
        }
        locks.expectedVolumeCount?.let { expected ->
            require(foundation.volumes.size == expected) {
                "上传原设明确为 $expected 卷，但蓝图得到 ${foundation.volumes.size} 卷。已阻止错误蓝图继续生成。"
            }
        } ?: require(foundation.volumes.isNotEmpty()) { "没有解析到有效分卷。" }
    }

    private fun coreUsable(foundation: StoryFoundation, locks: BlueprintLocks): Boolean {
        if (foundation.bible.size < 3 || foundation.characters.size < 2 || foundation.volumes.isEmpty()) return false
        if (locks.coreCharacterNames.any { expected -> foundation.characters.none { it.name == expected } }) return false
        val expectedVolumes = locks.expectedVolumeCount
        if (expectedVolumes != null && foundation.volumes.size != expectedVolumes) return false
        return true
    }

    private fun mergeChapters(foundation: StoryFoundation, output: GeneratedChapter): StoryFoundation {
        val incoming = output.stateChanges.mapNotNull { change ->
            val (volume, order) = chapterNumbers(subject(change)) ?: return@mapNotNull null
            if (volume != 1 || order !in 1..20) return@mapNotNull null
            FoundationChapter(
                order = order,
                title = change.field.trim().ifBlank { "第${order}章" },
                objective = change.before.trim().ifBlank { "按用户原设推进本章目标" },
                conflict = change.after.trim().ifBlank { "本章目标遭遇具体阻力" },
                turningPoint = change.evidence.trim().ifBlank { "章末改变下一章条件" },
            )
        }
        if (incoming.isEmpty()) return foundation
        val existing = foundation.volumes.firstOrNull { it.order == 1 }?.chapters.orEmpty()
        val chapters = (existing + incoming).distinctBy { it.order }.sortedBy { it.order }.take(20)
        return foundation.copy(
            volumes = foundation.volumes.map { volume ->
                if (volume.order == 1) volume.copy(chapters = chapters) else volume
            }
        )
    }

    private fun mergeForeshadowing(foundation: StoryFoundation, output: GeneratedChapter): StoryFoundation {
        val incoming = output.stateChanges.mapNotNull { change ->
            if (!isForeshadowSubject(subject(change))) return@mapNotNull null
            val title = change.field.trim()
            if (title.isBlank()) return@mapNotNull null
            val range = Regex("(\\d+)\\D+(\\d+)").find(change.evidence)
            val start = range?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
            val end = range?.groupValues?.getOrNull(2)?.toIntOrNull() ?: maxOf(start + 3, 12)
            FoundationForeshadow(
                title = title,
                detail = change.before.trim().ifBlank { "前期以可观察细节出现" },
                expectedPayoff = change.after.trim().ifBlank { "按既定谜底释放顺序完成回收" },
                expectedChapterStart = start,
                expectedChapterEnd = end,
            )
        }.distinctBy { it.title }.take(12)
        return if (incoming.isEmpty()) foundation else foundation.copy(
            foreshadowing = (foundation.foreshadowing + incoming).distinctBy { it.title }.take(12)
        )
    }

    private fun firstVolumeChapterCount(foundation: StoryFoundation): Int =
        foundation.volumes.firstOrNull { it.order == 1 }?.chapters?.size ?: 0

    private fun StringBuilder.appendHardLocks(locks: BlueprintLocks) {
        if (locks.source.isBlank()) return
        appendLine("【用户上传作品设定原文｜硬约束，不是参考摘要】")
        appendLine("以下原文已经是用户确认基线。除非后续用户明确说‘改掉/删除/替换’，否则不得删减卷数、合并人物、改规则、改势力、改剧情节点或改终局。")
        appendLine(locks.source)
        appendLine("【作品设定原文结束】")
        appendLine()
    }

    private fun StringBuilder.appendProposal(proposal: NewBookProposal) {
        appendLine("【当前方案缓存】")
        appendLine("书名：${proposal.title}")
        appendLine("类型：${proposal.genre}")
        appendLine("平台简介：${proposal.premise}")
        appendLine("主题：${proposal.theme}")
        appendLine("目标字数：${proposal.targetWords}")
        appendLine("核心钩子：${proposal.coreHook}")
        appendLine("封面方向：${proposal.coverBrief}")
        appendLine("内部策划：${proposal.rationale}")
        if (proposal.decisionLedger.isNotBlank()) {
            appendLine("【会谈确认事实账本】")
            appendLine(proposal.decisionLedger)
        }
    }

    private fun compactConversation(messages: List<CreationChatMessage>): String = messages.joinToString("\n") { message ->
        val raw = if (message.role == "user") message.text.substringBefore(RESEARCH_MARKER).trimEnd() else message.text
        val attachment = attachmentContext(message.attachments)
        val combined = if (attachment.isBlank()) raw else "$raw\n$attachment"
        if (message.role == "user") "用户：$combined" else "琅嬛：$combined"
    }

    private fun compactGenerated(output: GeneratedChapter): String = buildString {
        if (output.title.isNotBlank()) appendLine("书名：${output.title}")
        if (output.content.isNotBlank()) appendLine("简介：${output.content}")
        if (output.summary.isNotBlank()) appendLine("摘要：${output.summary}")
        output.stateChanges.forEach { appendLine("${subject(it)} | ${it.field} | ${it.before} | ${it.after} | ${it.evidence}") }
    }

    private fun compactFoundation(foundation: StoryFoundation, includeChapters: Boolean = false): String = buildString {
        appendLine("书名：${foundation.title}")
        appendLine("类型：${foundation.genre}")
        appendLine("简介：${foundation.premise}")
        appendLine("主题：${foundation.theme}")
        appendLine("核心钩子：${foundation.coreHook}")
        appendLine("总纲：${foundation.masterObjective} / ${foundation.masterConflict} / ${foundation.masterTurningPoint}")
        appendLine("圣经：${foundation.bible.take(20).joinToString("；") { "${it.category.name}:${it.name}=${it.content.take(160)}" }}")
        appendLine("角色：${foundation.characters.take(12).joinToString("；") { "${it.name} 目标=${it.goal.take(140)}" }}")
        foundation.volumes.take(10).forEach { volume ->
            appendLine("第${volume.order}卷 ${volume.title}：${volume.objective.take(500)} / ${volume.conflict} / ${volume.turningPoint}")
            if (includeChapters && volume.order == 1) {
                volume.chapters.take(20).forEach { chapter ->
                    appendLine("  ${chapter.order}. ${chapter.title}：${chapter.objective} / ${chapter.conflict} / ${chapter.turningPoint}")
                }
            }
        }
    }

    private fun subject(change: StateChange): String = change.subject
        .trim().replace('：', ':').replace(Regex("\\s+"), "_").uppercase()

    private fun isCharacterSubject(value: String): Boolean {
        val normalized = value.replace('-', '_')
        return normalized in setOf("CHAR", "CHARACTER", "ROLE", "人物", "角色", "人物设定", "角色设定") ||
            normalized.startsWith("CHAR:") || normalized.startsWith("CHAR_") ||
            normalized.startsWith("CHARACTER:") || normalized.startsWith("CHARACTER_")
    }

    private fun isForeshadowSubject(value: String): Boolean {
        val normalized = value.replace('-', '_')
        return normalized in setOf("FORESHADOW", "FORESHADOWING", "伏笔", "伏笔计划") ||
            normalized.startsWith("FORESHADOW:") || normalized.startsWith("FORESHADOW_") ||
            normalized.startsWith("FORESHADOWING:") || normalized.startsWith("FORESHADOWING_")
    }

    private fun volumeOrder(value: String): Int? {
        val normalized = value.replace('：', ':').replace('-', '_').replace("VOL:", "VOLUME:").replace("VOL_", "VOLUME_").uppercase()
        Regex("(?:VOLUME|分卷|卷)[:_ ]*(\\d+)", RegexOption.IGNORE_CASE).find(normalized)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("第(\\d+)卷").find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("第?([一二三四五六七八九十]+)卷|卷([一二三四五六七八九十]+)").find(normalized)?.let { match ->
            val raw = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            raw?.let(::chineseOrder)?.let { return it }
        }
        return null
    }

    private fun chapterNumbers(value: String): Pair<Int, Int>? {
        val normalized = value.replace('：', ':').replace('-', '_')
        val match = Regex("(?:CHAPTER|章纲)[:_ ]*(\\d+)[:_ ]+(\\d+)", RegexOption.IGNORE_CASE).find(normalized) ?: return null
        return (match.groupValues[1].toIntOrNull() ?: return null) to (match.groupValues[2].toIntOrNull() ?: return null)
    }

    private fun bibleCategoryFromSubject(value: String, change: StateChange): BibleCategory? {
        val normalized = value.replace('-', '_')
        val payload = when {
            normalized.startsWith("BIBLE:") -> normalized.substringAfter(':')
            normalized.startsWith("BIBLE_") -> normalized.substringAfter('_')
            normalized in setOf("WORLD", "世界", "世界观") -> "WORLD"
            normalized in setOf("RULE", "RULES", "规则", "规则体系") -> "RULE"
            normalized in setOf("FACTION", "势力", "组织") -> "FACTION"
            normalized in setOf("LOCATION", "PLACE", "地点", "场景") -> "LOCATION"
            normalized in setOf("ITEM", "OBJECT", "物品", "道具") -> "ITEM"
            normalized in setOf("FORBIDDEN", "TABOO", "禁忌", "禁止") -> "FORBIDDEN"
            normalized == "BIBLE" -> inferBiblePayload(change)
            else -> return null
        }
        return when (payload.trim().uppercase()) {
            "WORLD", "世界", "世界观" -> BibleCategory.WORLD
            "RULE", "RULES", "规则", "规则体系" -> BibleCategory.RULE
            "FACTION", "势力", "组织" -> BibleCategory.FACTION
            "LOCATION", "PLACE", "地点", "场景" -> BibleCategory.LOCATION
            "ITEM", "OBJECT", "物品", "道具" -> BibleCategory.ITEM
            "STYLE", "风格" -> BibleCategory.STYLE
            "FORBIDDEN", "TABOO", "禁忌", "禁止" -> BibleCategory.FORBIDDEN
            else -> null
        }
    }

    private fun inferBiblePayload(change: StateChange): String {
        val text = "${change.field} ${change.before} ${change.after}".lowercase()
        return when {
            listOf("规则", "机制", "限制", "代价", "条件").any(text::contains) -> "RULE"
            listOf("势力", "组织", "集团", "门派", "机构").any(text::contains) -> "FACTION"
            listOf("地点", "城市", "村", "镇", "学校", "医院", "区域").any(text::contains) -> "LOCATION"
            listOf("物品", "道具", "遗物", "器物").any(text::contains) -> "ITEM"
            listOf("禁忌", "禁止", "不可", "不能").any(text::contains) -> "FORBIDDEN"
            else -> "WORLD"
        }
    }

    private fun categoryForSection(title: String): BibleCategory = when {
        listOf("规则", "分野", "机制", "死亡", "能力", "代价", "副本").any(title::contains) -> BibleCategory.RULE
        listOf("势力", "组织", "阵营").any(title::contains) -> BibleCategory.FACTION
        listOf("地点", "场景", "地图").any(title::contains) -> BibleCategory.LOCATION
        listOf("风格", "写作", "叙事").any(title::contains) -> BibleCategory.STYLE
        listOf("禁忌", "尺度", "禁止").any(title::contains) -> BibleCategory.FORBIDDEN
        else -> BibleCategory.WORLD
    }

    private fun characterFromSource(name: String, source: String): FoundationCharacter {
        val body = subsectionBody(source, name)
        val goalLine = body.lineSequence().firstOrNull { line ->
            listOf("核心行为逻辑", "目标", "为什么不出来", "想要", "要做").any(line::contains)
        }.orEmpty().replace(Regex("[*#>`]"), "").trim()
        return FoundationCharacter(
            name = name,
            personality = listOf("以用户上传人物设定为准"),
            location = "故事起点",
            physicalState = "按用户原设",
            emotionalState = "按用户原设",
            goal = goalLine.ifBlank { "严格遵循用户上传作品设定中的人物动机与长期目标" }.take(500),
            knownSecrets = body.take(1800).takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
        )
    }

    private fun subsectionBody(source: String, heading: String): String {
        val lines = source.lines()
        val start = lines.indexOfFirst { it.trim().matches(Regex("^#{2,4}\\s+.*${Regex.escape(heading)}.*$")) }
        if (start < 0) return ""
        val level = lines[start].takeWhile { it == '#' }.length
        val end = (start + 1 until lines.size).firstOrNull { index ->
            val line = lines[index].trim()
            line.startsWith("#") && line.takeWhile { it == '#' }.length <= level
        } ?: lines.size
        return lines.subList(start + 1, end).joinToString("\n").trim()
    }

    private fun meaningfulValue(value: String?, placeholders: Set<String>, fallback: String): String {
        val clean = value.orEmpty().trim()
        return if (clean.isBlank() || placeholders.any { clean.equals(it, ignoreCase = true) }) fallback else clean
    }

    private fun split(text: String): List<String> = text
        .split(Regex("[、,，;；]"))
        .map(String::trim)
        .filter(String::isNotBlank)

    private fun parseRelationships(text: String): Map<String, String> = text
        .split(Regex("[;；]"))
        .mapNotNull { item ->
            val index = item.indexOf('=')
            if (index <= 0) null else item.substring(0, index).trim() to item.substring(index + 1).trim()
        }
        .filter { it.first.isNotBlank() && it.second.isNotBlank() }
        .toMap()

    private fun cleanHeading(value: String): String = value
        .replace(Regex("^[一二三四五六七八九十\\d]+[、.．\\s]*"), "")
        .trim().ifBlank { value.trim() }

    private fun chineseOrder(value: String): Int? = when (value) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5
        "六" -> 6; "七" -> 7; "八" -> 8; "九" -> 9; "十" -> 10
        else -> value.toIntOrNull()
    }

    private data class LockedSection(val title: String, val body: String)

    private data class BlueprintLocks(
        val source: String,
        val title: String?,
        val genre: String?,
        val targetWords: Int?,
        val premise: String?,
        val storySummary: String?,
        val styleGuide: String?,
        val coreCharacterNames: List<String>,
        val expectedVolumeCount: Int?,
        val lockedVolumes: List<FoundationVolume>,
        val sections: List<LockedSection>,
    ) {
        companion object {
            fun from(messages: List<CreationChatMessage>): BlueprintLocks {
                val source = messages.flatMap { it.attachments }
                    .filter { attachmentPurpose(it) == "作品设定" && it.extractedText.isNotBlank() }
                    .joinToString("\n\n") { it.extractedText.trim() }
                if (source.isBlank()) {
                    return BlueprintLocks("", null, null, null, null, null, null, emptyList(), null, emptyList(), emptyList())
                }

                val sections = parseSections(source)
                val title = Regex("(?m)^#\\s*《([^》]+)》").find(source)?.groupValues?.getOrNull(1)?.trim()
                val genre = Regex("(?m)^\\*\\*类型\\*\\*[:：]\\s*(.+)$").find(source)?.groupValues?.getOrNull(1)?.trim()
                val targetWords = Regex("(?m)^\\*\\*篇幅\\*\\*[:：].*?(\\d+(?:\\.\\d+)?)\\s*万字")
                    .find(source)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { (it * 10_000).toInt() }
                val premise = sections.firstOrNull { it.title.contains("一句话简介") }?.body
                    ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.replace("**", "")?.trim()
                val storySummary = sections.firstOrNull { it.title.contains("故事梗概") }?.body?.trim()?.take(5000)
                val styleGuide = sections.firstOrNull { it.title.contains("写作风格") }?.body?.trim()?.take(4000)

                val coreSection = sections.firstOrNull { section ->
                    section.title.contains("主角") || section.title.contains("核心人物")
                }
                val coreNames = coreSection?.let { section ->
                    parseSubheadings(section.body)
                        .map { heading -> heading.substringBefore("（").substringBefore('(').trim() }
                        .filter { name -> name.length in 2..12 && !name.contains("规则") && !name.contains("示例") }
                        .distinct()
                }.orEmpty()

                val lockedVolumes = parseVolumes(source)
                val declaredVolumeCount = Regex("(?m)^\\*\\*篇幅\\*\\*[:：].*?([一二三四五六七八九十\\d]+)卷")
                    .find(source)?.groupValues?.getOrNull(1)?.let(::chineseOrderStatic)
                val expected = declaredVolumeCount ?: lockedVolumes.size.takeIf { it > 0 }

                return BlueprintLocks(
                    source = source,
                    title = title,
                    genre = genre,
                    targetWords = targetWords,
                    premise = premise,
                    storySummary = storySummary,
                    styleGuide = styleGuide,
                    coreCharacterNames = coreNames,
                    expectedVolumeCount = expected,
                    lockedVolumes = lockedVolumes,
                    sections = sections,
                )
            }

            private fun parseSections(source: String): List<LockedSection> {
                val lines = source.lines()
                val indexes = lines.indices.filter { lines[it].matches(Regex("^##\\s+.+")) && !lines[it].startsWith("###") }
                return indexes.mapIndexed { index, start ->
                    val end = indexes.getOrNull(index + 1) ?: lines.size
                    LockedSection(
                        title = lines[start].removePrefix("##").trim(),
                        body = lines.subList(start + 1, end).joinToString("\n").trim(),
                    )
                }
            }

            private fun parseSubheadings(body: String): List<String> = body.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("### ") }
                .map { it.removePrefix("### ").trim() }
                .toList()

            private fun parseVolumes(source: String): List<FoundationVolume> {
                val sections = parseSections(source)
                val volumeBody = sections.firstOrNull { it.title.contains("分卷") }?.body ?: return emptyList()
                val lines = volumeBody.lines()
                val starts = lines.indices.filter { index ->
                    Regex("^###\\s+(?:第?[一二三四五六七八九十\\d]+卷|卷[一二三四五六七八九十\\d]+)").containsMatchIn(lines[index].trim())
                }
                return starts.mapIndexedNotNull { idx, start ->
                    val heading = lines[start].removePrefix("###").trim()
                    val orderMatch = Regex("(?:第?([一二三四五六七八九十\\d]+)卷|卷([一二三四五六七八九十\\d]+))").find(heading)
                        ?: return@mapIndexedNotNull null
                    val rawOrder = orderMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return@mapIndexedNotNull null
                    val order = chineseOrderStatic(rawOrder) ?: return@mapIndexedNotNull null
                    val end = starts.getOrNull(idx + 1) ?: lines.size
                    val body = lines.subList(start + 1, end).joinToString("\n").trim()
                    val name = heading
                        .replace(orderMatch.value, "")
                        .replace(Regex("^[·・\\-—:：\\s]+"), "")
                        .substringBefore("（")
                        .substringBefore('(')
                        .trim()
                        .ifBlank { "第${order}卷" }
                    val lastLine = body.lineSequence().map(String::trim).filter(String::isNotBlank).lastOrNull().orEmpty()
                    FoundationVolume(
                        order = order,
                        title = name,
                        objective = body.take(1800).ifBlank { "严格按用户上传的第${order}卷原设推进" },
                        conflict = "本卷所有既定事件、人物关系和信息释放按用户原设执行，禁止与其它卷合并。",
                        turningPoint = lastLine.take(500).ifBlank { "按用户上传的卷末节点收束" },
                    )
                }.distinctBy { it.order }.sortedBy { it.order }.take(10)
            }

            private fun chineseOrderStatic(value: String): Int? = when (value) {
                "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5
                "六" -> 6; "七" -> 7; "八" -> 8; "九" -> 9; "十" -> 10
                else -> value.toIntOrNull()
            }
        }
    }

    private companion object {
        const val RESEARCH_MARKER = "\n\n【琅嬛联网检索资料（隐藏上下文）】"
        const val STAGE_TIMEOUT_MS = 75_000L
        const val MAX_LOCKED_SECTION_CHARS = 5_000
        val META_SUBJECTS = setOf("META", "BOOK_META", "BOOKMETA", "书籍信息", "元信息")
        val STYLE_SUBJECTS = setOf("STYLE", "STYLE_GUIDE", "STYLEGUIDE", "风格", "叙事风格")
        val MASTER_SUBJECTS = setOf("MASTER", "MASTER_OUTLINE", "MASTEROUTLINE", "OUTLINE", "总纲", "总纲大纲")
        val GENRE_PLACEHOLDERS = setOf("类型", "小说类型", "题材", "genre")
        val THEME_PLACEHOLDERS = setOf("主题", "主题命题", "核心主题", "theme")

        val WORLD_SYSTEM = """
            你是“琅嬛”的长篇小说世界与总纲整理器。用户上传的作品设定是硬约束，不是灵感材料。
            只整理世界规则、总纲、简介和风格，不生成角色列表、分卷、章纲或伏笔。
            如果附件已明确某条规则、势力、主线、终局、篇幅或故事起点，必须保持原意；只能补缺口，不能重写成你觉得更好的另一套设定。
            输出 GeneratedChapter JSON：title=正式书名；content=连贯平台简介；summary=故事承诺；touchedForeshadowingIds=[]。
            stateChanges：META 1条；STYLE 1条；MASTER 1条；BIBLE:* 4-12条。
            禁止把参考作品专名写进新书；但用户自己附件中的专名必须保留。
        """.trimIndent()

        val CAST_SYSTEM = """
            你是“琅嬛”的人物与完整分卷整理器。用户上传作品设定是硬约束。
            只输出 CHAR 和 VOLUME，不重写世界规则或总纲。
            CHAR 只允许明确命名的人物。身份类别、群体、组织、职业或规则术语（例如“老客”“管理局”“玩家”“守夜人”等）绝不能因为剧情重要就自动变成核心人物。
            如果用户附件明确了卷数，VOLUME 必须逐卷完整输出，不能限制在2-4卷，最多支持10卷；禁止删卷、并卷、改卷序。
            输出 GeneratedChapter JSON：title="CAST_AND_VOLUMES"；content=""；summary=人物/分卷关系；touchedForeshadowingIds=[]。
            CHAR：field=角色名；before=性格/行为倾向；after=长期目标；evidence=地点||身体||情绪||秘密||物品||关系。
            VOLUME:序号：field=卷名；before=本卷目标；after=核心冲突；evidence=卷末转折。
        """.trimIndent()

        val FORESHADOW_SYSTEM = """
            你是“琅嬛”的长篇伏笔编辑。只根据既有蓝图和用户上传原设补充伏笔，不修改其它结构。
            用户附件已经明确的谜底、误导、身份真相、卷末揭晓和终局必须按原顺序释放，禁止提前剧透。
            输出 GeneratedChapter JSON：title="FORESHADOW_PLAN"；content=""；summary=伏笔策略；touchedForeshadowingIds=[]。
            stateChanges 只包含 3-8 条 FORESHADOW：field=伏笔名；before=首次可观察细节；after=回收方式；evidence=预计开始章-结束章。
        """.trimIndent()
    }
}
