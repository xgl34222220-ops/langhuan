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

/**
 * Relay-friendly new-book foundation builder.
 *
 * The UI still exposes three meaningful checkpoints, but large stages are internally split into
 * smaller JSON requests. This avoids asking a relay/model to emit dozens of records in one response
 * and also avoids SSE compatibility problems for structured generation.
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
        val safeResume = resumeStage.coerceIn(0, 2)
        var working = current

        if (safeResume < 1 || working == null || !coreUsable(working)) {
            onStage("1/3 · 正在生成世界、规则与总纲……")
            val world = request(
                stage = "1/3 世界与总纲",
                prompt = PromptBundle(
                    system = WORLD_SYSTEM,
                    user = buildString {
                        appendLine("【事实优先级】")
                        appendLine("后出现的用户明确决定 > 当前方案基线 > 琅嬛旧建议。若冲突，必须采用最新用户决定，禁止恢复被否定的旧简介、旧能力或旧冲突。")
                        appendLine()
                        appendLine("【用户确认过的会谈事实】")
                        appendLine(conversation)
                        appendLine()
                        appendProposal(proposal)
                        if (referenceContext.isNotBlank()) {
                            appendLine()
                            appendLine("【本次显式选中的参考 DNA】")
                            appendLine(referenceContext)
                        }
                        appendLine()
                        appendLine("【本轮要求】")
                        appendLine(instruction)
                    },
                ),
            )

            onStage("1/3 · 正在生成核心人物与分卷路线……")
            val cast = request(
                stage = "1/3 人物与分卷",
                prompt = PromptBundle(
                    system = CAST_SYSTEM,
                    user = buildString {
                        appendProposal(proposal)
                        appendLine()
                        appendLine("【刚生成的世界/总纲摘要】")
                        appendLine(compactGenerated(world))
                        if (referenceContext.isNotBlank()) {
                            appendLine()
                            appendLine("【参考 DNA 的人物/结构约束】")
                            appendLine(referenceContext)
                        }
                        appendLine()
                        appendLine("只补人物与分卷，不重写 META / STYLE / MASTER / BIBLE。后续用户决定优先于任何旧缓存。")
                    },
                ),
            )

            val combined = world.copy(
                stateChanges = world.stateChanges + cast.stateChanges,
                summary = world.summary.ifBlank { cast.summary },
            )
            working = parseCore(combined, proposal, current)
            if (!coreUsable(working)) {
                onStage("1/3 · 返回结构不标准，正在自动整理蓝图……")
                val normalized = request(
                    stage = "1/3 蓝图自动整理",
                    prompt = PromptBundle(
                        system = CORE_NORMALIZER_SYSTEM,
                        user = buildString {
                            appendProposal(proposal)
                            appendLine()
                            appendLine("【第一次返回的可读内容】")
                            appendLine(compactGenerated(combined))
                            appendLine()
                            appendLine("当前缺口：世界规则 ${working.bible.size}/3；核心人物 ${working.characters.size}/2；分卷 ${working.volumes.size}/1。")
                            appendLine("请重新输出一份完整的标准结构，不能只解释标签，不能把条目写进正文，也不能为了缩短输出遗漏确认事实。")
                        },
                    ),
                )
                val repaired = combined.copy(
                    title = normalized.title.ifBlank { combined.title },
                    content = normalized.content.ifBlank { combined.content },
                    summary = normalized.summary.ifBlank { combined.summary },
                    stateChanges = combined.stateChanges + normalized.stateChanges,
                )
                working = parseCore(repaired, proposal, current)
            }
            validateCore(working)
            onCheckpoint(1, working)
        } else {
            onStage("1/3 · 已恢复核心蓝图断点")
        }

        working = requireNotNull(working)

        if (safeResume < 2 || firstVolumeChapterCount(working) < 8) {
            var chapterCount = firstVolumeChapterCount(working)
            if (chapterCount < 5) {
                onStage("2/3 · 正在生成第一卷第 1–5 章……")
                val firstHalf = request(
                    stage = "2/3 第1–5章",
                    prompt = chapterPrompt(
                        foundation = working,
                        referenceContext = referenceContext,
                        start = 1,
                        end = 5,
                        prior = emptyList(),
                    ),
                )
                working = mergeChapters(working, firstHalf)
                chapterCount = firstVolumeChapterCount(working)
                require(chapterCount >= 4) {
                    "2/3 第1–5章只解析到 $chapterCount 条有效章纲。核心蓝图已保留，可直接重试本阶段。"
                }
                onCheckpoint(1, working)
            }

            chapterCount = firstVolumeChapterCount(working)
            if (chapterCount < 8) {
                onStage("2/3 · 正在生成第一卷第 6–10 章……")
                val secondHalf = request(
                    stage = "2/3 第6–10章",
                    prompt = chapterPrompt(
                        foundation = working,
                        referenceContext = referenceContext,
                        start = 6,
                        end = 10,
                        prior = working.volumes.firstOrNull { it.order == 1 }?.chapters.orEmpty(),
                    ),
                )
                working = mergeChapters(working, secondHalf)
                chapterCount = firstVolumeChapterCount(working)
            }

            require(chapterCount >= 8) {
                "2/3 第一卷目前只有 $chapterCount 条有效章纲，至少需要 8 条。已成功内容不会丢，重试会继续补齐。"
            }
            onCheckpoint(2, working)
        } else {
            onStage("2/3 · 已恢复第一卷章纲断点")
        }

        onStage("3/3 · 正在布置可观察、可回收的伏笔……")
        val foreshadow = request(
            stage = "3/3 伏笔计划",
            prompt = PromptBundle(
                system = FORESHADOW_SYSTEM,
                user = buildString {
                    appendLine("【核心蓝图 + 第一卷章纲】")
                    appendLine(compactFoundation(working, includeChapters = true))
                    if (referenceContext.isNotBlank()) {
                        appendLine()
                        appendLine("【已选参考 DNA】")
                        appendLine(referenceContext)
                    }
                    appendLine()
                    appendLine("只输出伏笔计划，不重写其它结构。")
                },
            ),
        )
        working = mergeForeshadowing(working, foreshadow)
        require(working.foreshadowing.size >= 3) {
            "3/3 伏笔阶段只解析到 ${working.foreshadowing.size} 条有效伏笔。前两阶段已保存，重试只需继续伏笔阶段。"
        }
        onCheckpoint(3, working)
        return working
    }

    private suspend fun request(stage: String, prompt: PromptBundle): GeneratedChapter = try {
        gateway.generate(prompt)
    } catch (error: Throwable) {
        throw IllegalStateException("$stage 失败：${error.message ?: "AI 没有返回可解析结果"}", error)
    }

    private fun chapterPrompt(
        foundation: StoryFoundation,
        referenceContext: String,
        start: Int,
        end: Int,
        prior: List<FoundationChapter>,
    ): PromptBundle = PromptBundle(
        system = """
            你是“琅嬛”的第一卷章纲设计师。只生成第一卷第 $start–$end 章，不修改书名、世界规则、人物设定或卷纲。
            输出 GeneratedChapter JSON：title="FIRST_VOLUME_${start}_${end}"；content=""；summary=本批节奏摘要；touchedForeshadowingIds=[]。
            stateChanges 只放 CHAPTER:1:序号，序号必须覆盖 $start 到 $end：field=章名；before=本章明确目标；after=本章具体冲突；evidence=章末转折。
            每章必须承接上一章结果，形成连续因果链。不要写正文，不要一次输出其它结构。
        """.trimIndent(),
        user = buildString {
            appendLine("【核心蓝图】")
            appendLine(compactFoundation(foundation))
            if (prior.isNotEmpty()) {
                appendLine()
                appendLine("【已经确认的前置章纲】")
                prior.sortedBy { it.order }.takeLast(6).forEach { chapter ->
                    appendLine("${chapter.order}. ${chapter.title}：${chapter.objective} / ${chapter.conflict} / ${chapter.turningPoint}")
                }
            }
            if (referenceContext.isNotBlank()) {
                appendLine()
                appendLine("【已选参考 DNA 的节奏/信息释放约束】")
                appendLine(referenceContext)
            }
        },
    )

    private fun parseCore(
        output: GeneratedChapter,
        fallback: NewBookProposal,
        current: StoryFoundation?,
    ): StoryFoundation {
        val changes = output.stateChanges
        val meta = changes.firstOrNull { subject(it) in setOf("META", "BOOK_META", "BOOKMETA", "书籍信息", "元信息") }
        val style = changes.firstOrNull { subject(it) in setOf("STYLE", "STYLE_GUIDE", "STYLEGUIDE", "风格", "叙事风格") }
        val master = changes.firstOrNull { subject(it) in setOf("MASTER", "MASTER_OUTLINE", "MASTEROUTLINE", "OUTLINE", "总纲", "总纲大纲") }

        val bible = changes.mapNotNull { change ->
            val normalized = subject(change)
            val category = bibleCategoryFromSubject(normalized, change) ?: return@mapNotNull null
            val name = change.field.trim()
            val content = change.before.trim().ifBlank { change.after.trim() }
            if (name.isBlank() || content.isBlank()) return@mapNotNull null
            FoundationBibleItem(
                category = category,
                name = name,
                content = content,
                aliases = split(change.after).filterNot { it == content },
                locked = true,
            )
        }.distinctBy { it.category to it.name }.take(14)

        val characters = changes.filter { isCharacterSubject(subject(it)) }.mapNotNull { change ->
            val name = change.field.trim()
            if (name.isBlank()) return@mapNotNull null
            val parts = change.evidence.split("||")
            FoundationCharacter(
                name = name,
                personality = split(change.before).ifEmpty { listOf("克制") },
                location = parts.getOrNull(0)?.trim().orEmpty().ifBlank { "故事起点" },
                physicalState = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "正常" },
                emotionalState = parts.getOrNull(2)?.trim().orEmpty().ifBlank { "平静" },
                goal = change.after.trim().ifBlank { "推动当前主线目标" },
                knownSecrets = split(parts.getOrNull(3).orEmpty()),
                possessions = split(parts.getOrNull(4).orEmpty()),
                relationships = parseRelationships(parts.getOrNull(5).orEmpty()),
            )
        }.distinctBy { it.name }.take(7)

        val volumes = changes.mapNotNull { change ->
            val order = volumeOrder(subject(change)) ?: return@mapNotNull null
            FoundationVolume(
                order = order,
                title = change.field.trim().ifBlank { "第${order}卷" },
                objective = change.before.trim().ifBlank { "推进第${order}阶段主线" },
                conflict = change.after.trim().ifBlank { "核心目标遭遇新的不可回避阻力" },
                turningPoint = change.evidence.trim().ifBlank { "本卷末改变下一阶段条件" },
                chapters = current?.volumes?.firstOrNull { it.order == order }?.chapters.orEmpty(),
            )
        }.sortedBy { it.order }.distinctBy { it.order }.take(4)

        val targetWords = meta?.before?.filter(Char::isDigit)?.toIntOrNull()
            ?.coerceIn(10_000, 5_000_000)
            ?: fallback.targetWords
        val safeTitle = output.title.trim().takeIf { it.filterNot(Char::isWhitespace).length in 2..18 }
            ?: fallback.title
        val safePremise = output.content.trim().takeIf { it.filterNot(Char::isWhitespace).length >= 50 }
            ?: fallback.premise
        val genre = meaningfulValue(meta?.field, setOf("类型", "小说类型", "题材", "genre"), fallback.genre)
        val theme = meaningfulValue(
            meta?.after,
            setOf("主题", "主题命题", "核心主题", "theme"),
            fallback.theme.takeUnless { it in setOf("主题", "主题命题") }
                ?: "人在真相、执念与代价之间如何选择",
        )

        return StoryFoundation(
            title = safeTitle,
            genre = genre,
            premise = safePremise,
            theme = theme,
            targetWords = targetWords,
            coreHook = meta?.evidence?.trim().orEmpty().ifBlank { fallback.coreHook },
            storyPromise = output.summary.trim().ifBlank { style?.before?.trim().orEmpty() }.ifBlank { fallback.rationale },
            styleGuide = style?.after?.trim().orEmpty().ifBlank {
                current?.styleGuide ?: "人物行动必须有因果，信息逐层释放，不临时改规则，不靠降智推动剧情。"
            },
            coverBrief = style?.evidence?.trim().orEmpty().ifBlank { fallback.coverBrief },
            masterTitle = master?.field?.trim().orEmpty().ifBlank { current?.masterTitle ?: "总纲" },
            masterObjective = master?.before?.trim().orEmpty().ifBlank { current?.masterObjective ?: fallback.premise },
            masterConflict = master?.after?.trim().orEmpty().ifBlank {
                current?.masterConflict ?: "主角追求核心目标的同时，必须承担不断升级且不可回避的代价。"
            },
            masterTurningPoint = master?.evidence?.trim().orEmpty().ifBlank {
                current?.masterTurningPoint ?: "终局重新解释关键线索，并迫使主角做出不可逆选择。"
            },
            bible = bible.ifEmpty { current?.bible.orEmpty() },
            characters = characters.ifEmpty { current?.characters.orEmpty() }.ifEmpty {
                listOf(
                    FoundationCharacter(
                        name = "主角",
                        personality = listOf("克制", "谨慎", "有判断力"),
                        location = "故事起点",
                        physicalState = "正常",
                        emotionalState = "平静",
                        goal = "弄清核心异常，并守住不能失去的人或事",
                    )
                )
            },
            volumes = volumes.ifEmpty { current?.volumes.orEmpty() }.ifEmpty {
                listOf(
                    FoundationVolume(
                        order = 1,
                        title = "第一卷",
                        objective = "建立主角、规则与核心问题，并迫使主角主动进入主线。",
                        conflict = "现实目标与核心异常正面冲突。",
                        turningPoint = "主角获得无法忽视的新证据。",
                        chapters = emptyList(),
                    )
                )
            },
            foreshadowing = current?.foreshadowing.orEmpty(),
            creationBrief = fallback.decisionLedger.ifBlank { current?.creationBrief.orEmpty() },
        )
    }

    private fun validateCore(foundation: StoryFoundation) {
        require(foundation.bible.size >= 3) {
            "模型连续两次都没有给出可用的世界规则（${foundation.bible.size}/3）。会谈和当前方案已经保留，请直接重试；若仍失败请临时切换另一个模型。"
        }
        require(foundation.characters.size >= 2) {
            "模型连续两次都没有给出完整核心人物（${foundation.characters.size}/2）。会谈和当前方案已经保留，请直接重试。"
        }
        require(foundation.volumes.isNotEmpty()) { "模型连续两次都没有给出有效分卷路线。会谈和当前方案已经保留，请直接重试。" }
    }

    private fun coreUsable(foundation: StoryFoundation): Boolean =
        foundation.bible.size >= 3 && foundation.characters.size >= 2 && foundation.volumes.isNotEmpty()

    private fun mergeChapters(foundation: StoryFoundation, output: GeneratedChapter): StoryFoundation {
        val incoming = output.stateChanges.mapNotNull { change ->
            val parsed = chapterNumbers(subject(change)) ?: return@mapNotNull null
            val (volume, order) = parsed
            if (volume != 1 || order !in 1..14) return@mapNotNull null
            FoundationChapter(
                order = order,
                title = change.field.trim().ifBlank { "第${order}章" },
                objective = change.before.trim().ifBlank { "推动本章明确目标" },
                conflict = change.after.trim().ifBlank { "本章目标遭遇直接阻力" },
                turningPoint = change.evidence.trim().ifBlank { "章末结果改变下一章条件" },
            )
        }
        if (incoming.isEmpty()) return foundation

        val existing = foundation.volumes.firstOrNull { it.order == 1 }?.chapters.orEmpty()
        val chapters = (existing + incoming).distinctBy { it.order }.sortedBy { it.order }.take(14)
        val volumes = if (foundation.volumes.any { it.order == 1 }) {
            foundation.volumes.map { if (it.order == 1) it.copy(chapters = chapters) else it }
        } else {
            listOf(
                FoundationVolume(
                    order = 1,
                    title = "第一卷",
                    objective = foundation.masterObjective,
                    conflict = foundation.masterConflict,
                    turningPoint = chapters.lastOrNull()?.turningPoint.orEmpty(),
                    chapters = chapters,
                )
            ) + foundation.volumes
        }
        return foundation.copy(volumes = volumes.sortedBy { it.order })
    }

    private fun mergeForeshadowing(foundation: StoryFoundation, output: GeneratedChapter): StoryFoundation {
        val foreshadowing = output.stateChanges.mapNotNull { change ->
            if (!isForeshadowSubject(subject(change))) return@mapNotNull null
            val title = change.field.trim()
            if (title.isBlank()) return@mapNotNull null
            val range = Regex("(\\d+)\\D+(\\d+)").find(change.evidence)
            val start = range?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
            val end = range?.groupValues?.getOrNull(2)?.toIntOrNull() ?: maxOf(start + 3, 12)
            FoundationForeshadow(
                title = title,
                detail = change.before.trim().ifBlank { "前期以可观察细节出现" },
                expectedPayoff = change.after.trim().ifBlank { "后续通过因果链完成回收" },
                expectedChapterStart = start,
                expectedChapterEnd = end,
            )
        }.distinctBy { it.title }.take(7)
        return if (foreshadowing.isEmpty()) foundation else foundation.copy(foreshadowing = foreshadowing)
    }

    private fun firstVolumeChapterCount(foundation: StoryFoundation): Int =
        foundation.volumes.firstOrNull { it.order == 1 }?.chapters?.size ?: 0

    private fun subject(change: StateChange): String = change.subject
        .trim()
        .replace('：', ':')
        .replace(Regex("\\s+"), "_")
        .uppercase()

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
        val normalized = value
            .replace('：', ':')
            .replace('-', '_')
            .replace("VOL:", "VOLUME:")
            .replace("VOL_", "VOLUME_")
            .uppercase()
        Regex("(?:VOLUME|分卷|卷)[:_ ]*(\\d+)", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        Regex("第(\\d+)卷").find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        Regex("第([一二三四五六七八九十]+)卷").find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::chineseOrder)
            ?.let { return it }
        return null
    }

    private fun chapterNumbers(value: String): Pair<Int, Int>? {
        val normalized = value.replace('：', ':').replace('-', '_')
        val match = Regex("(?:CHAPTER|章纲)[:_ ]*(\\d+)[:_ ]+(\\d+)", RegexOption.IGNORE_CASE).find(normalized)
            ?: return null
        val volume = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val order = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return volume to order
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
        return bibleCategory(payload)
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

    private fun bibleCategory(raw: String): BibleCategory? {
        val value = raw.trim().uppercase()
        return when {
            value in setOf("WORLD", "世界", "世界观") -> BibleCategory.WORLD
            value in setOf("RULE", "RULES", "规则", "规则体系") -> BibleCategory.RULE
            value in setOf("CHARACTER", "CHAR", "人物", "角色") -> BibleCategory.CHARACTER
            value in setOf("FACTION", "势力", "组织") -> BibleCategory.FACTION
            value in setOf("LOCATION", "PLACE", "地点", "场景") -> BibleCategory.LOCATION
            value in setOf("ITEM", "OBJECT", "物品", "道具") -> BibleCategory.ITEM
            value in setOf("STYLE", "风格") -> BibleCategory.STYLE
            value in setOf("FORBIDDEN", "TABOO", "禁忌", "禁止") -> BibleCategory.FORBIDDEN
            else -> runCatching { BibleCategory.valueOf(value) }.getOrNull()
        }
    }

    private fun chineseOrder(value: String): Int? = when (value) {
        "一" -> 1
        "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "七" -> 7
        "八" -> 8
        "九" -> 9
        "十" -> 10
        else -> null
    }

    private fun compactConversation(messages: List<CreationChatMessage>): String = messages
        .joinToString("\n") { message ->
            val raw = if (message.role == "user") message.text.substringBefore(RESEARCH_MARKER).trimEnd() else message.text
            val withAttachments = if (message.attachments.isEmpty()) raw else "$raw\n${attachmentContext(message.attachments)}"
            if (message.role == "user") "用户：$withAttachments" else "琅嬛：$withAttachments"
        }

    private fun compactGenerated(output: GeneratedChapter): String = buildString {
        if (output.title.isNotBlank()) appendLine("书名：${output.title}")
        if (output.content.isNotBlank()) appendLine("简介：${output.content}")
        if (output.summary.isNotBlank()) appendLine("摘要：${output.summary}")
        output.stateChanges.forEach { change ->
            appendLine("${subject(change)} | ${change.field} | ${change.before} | ${change.after} | ${change.evidence}")
        }
    }

    private fun compactFoundation(foundation: StoryFoundation, includeChapters: Boolean = false): String = buildString {
        appendLine("书名：${foundation.title}")
        appendLine("类型：${foundation.genre}")
        appendLine("简介：${foundation.premise}")
        appendLine("主题：${foundation.theme}")
        appendLine("核心钩子：${foundation.coreHook}")
        appendLine("故事承诺：${foundation.storyPromise}")
        if (foundation.creationBrief.isNotBlank()) {
            appendLine("【建书会谈确认事实：所有后续阶段逐条遵守】")
            appendLine(foundation.creationBrief)
        }
        appendLine("总纲：${foundation.masterObjective} / ${foundation.masterConflict} / ${foundation.masterTurningPoint}")
        appendLine("圣经：${foundation.bible.take(10).joinToString("；") { "${it.category.name}:${it.name}=${it.content.take(120)}" }}")
        appendLine("角色：${foundation.characters.take(6).joinToString("；") { "${it.name}[${it.personality.joinToString("、")}]目标=${it.goal.take(100)}" }}")
        foundation.volumes.take(4).forEach { volume ->
            appendLine("第${volume.order}卷 ${volume.title}：${volume.objective} / ${volume.conflict} / ${volume.turningPoint}")
            if (includeChapters && volume.order == 1) {
                volume.chapters.take(12).forEach { chapter ->
                    appendLine("  ${chapter.order}. ${chapter.title}：${chapter.objective} / ${chapter.conflict} / ${chapter.turningPoint}")
                }
            }
        }
    }

    private fun StringBuilder.appendProposal(proposal: NewBookProposal) {
        appendLine("【当前方案基线：已由会谈合并器刷新；若仍与后续用户决定冲突，以后续用户决定为准】")
        appendLine("书名：${proposal.title}")
        appendLine("类型：${proposal.genre}")
        appendLine("平台简介：${proposal.premise}")
        appendLine("主题：${proposal.theme}")
        appendLine("目标字数：${proposal.targetWords}")
        appendLine("核心钩子：${proposal.coreHook}")
        appendLine("封面方向：${proposal.coverBrief}")
        appendLine("内部策划：${proposal.rationale}")
        if (proposal.decisionLedger.isNotBlank()) {
            appendLine("【整段会谈确认事实账本：蓝图所有阶段必须逐条遵守】")
            appendLine(proposal.decisionLedger)
        }
    }

    private fun meaningfulValue(value: String?, placeholders: Set<String>, fallback: String): String {
        val clean = value.orEmpty().trim()
        if (clean.isBlank() || clean.lowercase() in placeholders.map(String::lowercase).toSet()) return fallback
        return clean
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

    private companion object {
        const val RESEARCH_MARKER = "\n\n【琅嬛联网检索资料（隐藏上下文）】"

        val WORLD_SYSTEM = """
            你是“琅嬛”的长篇小说世界与总纲架构师。只生成世界规则和总纲，不生成角色列表、分卷、章纲或伏笔；内容篇幅服从已确认事实的完整表达。
            最新用户决定拥有最高优先级。禁止因为输入里存在旧方案，就恢复已经被用户否定、替换或改写的简介、主角能力、身份、目标、世界规则或核心冲突。
            输出 GeneratedChapter JSON：
            - title=正式书名；content=完整连贯的平台简介；summary=足以约束后续阶段的故事承诺；touchedForeshadowingIds=[]。
            - stateChanges：
              * META 1条：field=实际小说类型；before=目标总字数纯数字；after=实际主题命题；evidence=核心钩子。
              * STYLE 1条：field=叙事风格；before=阅读承诺；after=风格基线；evidence=封面方向。
              * MASTER 1条：field=总纲标题；before=全书目标；after=核心冲突；evidence=最大转折/终局方向。
              * BIBLE:* 4-7条，分类可用 WORLD/RULE/FACTION/LOCATION/ITEM/FORBIDDEN/STYLE；field=名称；before=硬设定内容；after=别名可留空。
            强烈建议严格使用 BIBLE:WORLD、BIBLE:RULE 等标签；解析器也兼容 BIBLE_WORLD、WORLD、世界、规则等常见中转输出。
            不要输出 CHAR、VOLUME、CHAPTER、FORESHADOW。设定必须少而硬，禁止复制参考作品的专名、人物或独特剧情骨架。
        """.trimIndent()

        val CAST_SYSTEM = """
            你是“琅嬛”的长篇人物与分卷架构师。只补核心人物与分卷路线，不重写世界规则和总纲。
            输出 GeneratedChapter JSON：title="CAST_AND_VOLUMES"；content=""；summary=人物/分卷总体关系；touchedForeshadowingIds=[]。
            stateChanges 只允许：
            - CHAR 2-4条：field=角色名；before=2-5个性格/行为倾向；after=长期目标；evidence=地点||身体||情绪||秘密||物品||关系。
            - VOLUME:序号 2-4条：field=卷名；before=本卷目标；after=核心冲突；evidence=关键转折。
            必须真的输出至少2条 VOLUME:*，不能只在 summary 里描述分卷。解析器同时兼容 VOLUME_1、分卷1、卷1 等常见等价标签。
            至少包含主角和一个长期重要关系角色。分卷必须形成逐级升级，而不是重复同一目标。
        """.trimIndent()

        val FORESHADOW_SYSTEM = """
            你是“琅嬛”的长篇伏笔编辑。只根据既有蓝图设计伏笔，不修改其它结构。
            输出 GeneratedChapter JSON：title="FORESHADOW_PLAN"；content=""；summary=伏笔整体策略；touchedForeshadowingIds=[]。
            stateChanges 只包含 3-5 条 FORESHADOW：field=伏笔名；before=首次呈现时可观察细节；after=预期回收方式；evidence=预计开始章-结束章，例如 2-18。
            伏笔必须可观察、可误解、可回收；禁止把正文从未出现的作者秘密冒充伏笔。
        """.trimIndent()

        val CORE_NORMALIZER_SYSTEM = """
            你是“琅嬛”的蓝图结构整理器。上一个模型返回的内容可能被中转站改了字段或把数组压成了文本；你必须根据当前方案重新输出一份完整、标准的 GeneratedChapter JSON，不得为了缩短输出遗漏确认事实。
            不要解释，不要输出 Markdown，不要把条目写在 content/summary 里。
            title=正式书名；content=完整连贯的平台简介；summary=足以约束后续阶段的故事承诺；touchedForeshadowingIds=[]。
            stateChanges 必须是 JSON 数组，并完整包含：
            - META 1条：field=实际小说类型；before=目标字数纯数字；after=主题；evidence=核心钩子。
            - STYLE 1条：field=叙事风格；before=阅读承诺；after=风格基线；evidence=封面方向。
            - MASTER 1条：field=总纲名；before=全书目标；after=核心冲突；evidence=终局转折方向。
            - BIBLE:WORLD / BIBLE:RULE / BIBLE:LOCATION / BIBLE:FACTION / BIBLE:FORBIDDEN 中至少4条：field=设定名；before=不可随意改变的硬设定；after=别名或空字符串。
            - CHAR 至少2条：field=角色名；before=性格；after=长期目标；evidence=地点||身体||情绪||秘密||物品||关系。
            - VOLUME:1、VOLUME:2 至少2条：field=卷名；before=本卷目标；after=本卷冲突；evidence=卷末转折。
            所有角色、规则、专名和因果链必须原创；后出现的用户决定优先，禁止恢复被否定的旧设定。
        """.trimIndent()
    }
}
