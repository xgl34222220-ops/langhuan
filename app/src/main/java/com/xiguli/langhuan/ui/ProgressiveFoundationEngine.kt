package com.xiguli.langhuan.ui

import com.xiguli.langhuan.data.FoundationBibleItem
import com.xiguli.langhuan.data.FoundationChapter
import com.xiguli.langhuan.data.FoundationCharacter
import com.xiguli.langhuan.data.FoundationForeshadow
import com.xiguli.langhuan.data.FoundationVolume
import com.xiguli.langhuan.data.StoryFoundation
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.engine.AiGateway
import com.xiguli.langhuan.engine.PromptBundle

/**
 * Builds a new-book foundation in bounded requests instead of asking one model call to emit dozens
 * of records at once. Each completed stage can be checkpointed by the caller so a timeout in a later
 * stage does not force the user to pay for and regenerate earlier successful stages.
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
        val currentSummary = current?.let(::compactFoundation).orEmpty()
        val safeResume = resumeStage.coerceIn(0, 2)
        var foundation = current

        if (safeResume < 1 || foundation == null) {
            onStage("1/3 · 正在搭建世界规则、核心角色和分卷主线……")
            val coreOutput = request(
                PromptBundle(
                    system = CORE_SYSTEM,
                    user = buildString {
                        appendLine("【已确认方案】")
                        appendLine("书名：${proposal.title}")
                        appendLine("类型：${proposal.genre}")
                        appendLine("平台简介：${proposal.premise}")
                        appendLine("主题：${proposal.theme}")
                        appendLine("目标字数：${proposal.targetWords}")
                        appendLine("核心钩子：${proposal.coreHook}")
                        appendLine("封面方向：${proposal.coverBrief}")
                        appendLine("内部策划：${proposal.rationale}")
                        if (referenceContext.isNotBlank()) {
                            appendLine()
                            appendLine(referenceContext.take(7_000))
                            appendLine("上面的 Style DNA 是用户本次显式选中的唯一参考集合。未选择的蒸馏作品禁止自动混入。")
                        }
                        appendLine()
                        appendLine("【用户确认过的会谈事实】")
                        appendLine(conversation)
                        if (currentSummary.isNotBlank()) {
                            appendLine()
                            appendLine("【当前已有蓝图】")
                            appendLine(currentSummary)
                        }
                        appendLine()
                        appendLine("【本轮要求】")
                        appendLine(instruction)
                        appendLine()
                        appendLine("只完成核心架构，不生成详细章纲和伏笔。")
                    },
                )
            )
            validateCoreOutput(coreOutput)
            foundation = parseCore(coreOutput, proposal, current)
            onCheckpoint(1, foundation)
        } else {
            onStage("1/3 · 已恢复核心蓝图断点，跳过重复生成")
        }

        var working = requireNotNull(foundation)
        val existingChapterCount = working.volumes.firstOrNull { it.order == 1 }?.chapters?.size ?: 0
        if (safeResume < 2 || existingChapterCount < 8) {
            onStage("2/3 · 正在展开第一卷 10–12 章因果链……")
            val chapterOutput = request(
                PromptBundle(
                    system = CHAPTER_SYSTEM,
                    user = buildString {
                        appendLine("【核心蓝图】")
                        appendLine(compactFoundation(working))
                        if (referenceContext.isNotBlank()) {
                            appendLine()
                            appendLine("【已选 Style DNA 的节奏/信息释放约束】")
                            appendLine(referenceContext.take(2_800))
                        }
                        appendLine()
                        appendLine("【本轮要求】")
                        appendLine(instruction)
                        appendLine()
                        appendLine("只输出第一卷详细章纲，必须形成连续因果链。")
                    },
                )
            )
            val chapterRecords = chapterOutput.stateChanges.count {
                it.subject.startsWith("CHAPTER:1:", ignoreCase = true)
            }
            require(chapterRecords >= 8) {
                "AI 只返回了 $chapterRecords 条有效第一卷章纲，未达到最低 8 条。核心蓝图断点已保留；重试只会重新生成第 2 阶段。"
            }
            working = mergeChapters(working, chapterOutput)
            val chapterCount = working.volumes.firstOrNull { it.order == 1 }?.chapters?.size ?: 0
            require(chapterCount >= 8) {
                "第一卷章纲解析后不足 8 条，核心蓝图断点已保留；可直接重试第 2 阶段。"
            }
            onCheckpoint(2, working)
        } else {
            onStage("2/3 · 已恢复第一卷章纲断点，跳过重复生成")
        }

        onStage("3/3 · 正在布置伏笔并做蓝图收口……")
        val foreshadowOutput = request(
            PromptBundle(
                system = FORESHADOW_SYSTEM,
                user = buildString {
                    appendLine("【核心蓝图 + 第一卷章纲】")
                    appendLine(compactFoundation(working, includeChapters = true))
                    if (referenceContext.isNotBlank()) {
                        appendLine()
                        appendLine("【已选 Style DNA】")
                        appendLine(referenceContext.take(1_800))
                    }
                    appendLine()
                    appendLine("请只输出 3–6 条跨章节伏笔计划；不要重写其它结构。")
                },
            )
        )
        val foreshadowRecords = foreshadowOutput.stateChanges.count {
            it.subject.equals("FORESHADOW", ignoreCase = true) && it.field.isNotBlank()
        }
        require(foreshadowRecords >= 3) {
            "AI 只返回了 $foreshadowRecords 条有效伏笔，未达到最低 3 条。前两阶段断点已保留；重试只会重新生成伏笔阶段。"
        }
        working = mergeForeshadowing(working, foreshadowOutput)
        require(working.foreshadowing.size >= 3) {
            "伏笔解析结果不足 3 条，前两阶段断点已保留；不会把半成品蓝图标记为完成。"
        }
        onCheckpoint(3, working)

        return working
    }

    private suspend fun request(prompt: PromptBundle): GeneratedChapter =
        gateway.generateStreaming(prompt) { /* structured generation: progress is represented by stages */ }

    private fun validateCoreOutput(output: GeneratedChapter) {
        val changes = output.stateChanges
        val bible = changes.count { it.subject.startsWith("BIBLE:", ignoreCase = true) && it.field.isNotBlank() }
        val characters = changes.count { it.subject.equals("CHAR", ignoreCase = true) && it.field.isNotBlank() }
        val volumes = changes.count { it.subject.startsWith("VOLUME:", ignoreCase = true) && it.field.isNotBlank() }
        val hasMaster = changes.any { it.subject.equals("MASTER", ignoreCase = true) }
        require(bible >= 4 && characters >= 2 && volumes >= 1 && hasMaster) {
            "核心蓝图不完整：有效圣经 $bible 条、角色 $characters 个、分卷 $volumes 个、总纲=${if (hasMaster) "有" else "缺失"}。未保存这个半成品，请重试第 1 阶段。"
        }
    }

    private fun parseCore(
        output: GeneratedChapter,
        fallback: NewBookProposal,
        current: StoryFoundation?,
    ): StoryFoundation {
        val changes = output.stateChanges
        val meta = changes.firstOrNull { it.subject.equals("META", true) }
        val style = changes.firstOrNull { it.subject.equals("STYLE", true) }
        val master = changes.firstOrNull { it.subject.equals("MASTER", true) }

        val bible = changes.mapNotNull { change ->
            if (!change.subject.startsWith("BIBLE:", true)) return@mapNotNull null
            val category = runCatching {
                BibleCategory.valueOf(change.subject.substringAfter(':').trim().uppercase())
            }.getOrNull() ?: return@mapNotNull null
            FoundationBibleItem(
                category = category,
                name = change.field.trim(),
                content = change.before.trim(),
                aliases = split(change.after),
                locked = true,
            )
        }.filter { it.name.isNotBlank() && it.content.isNotBlank() }.take(18)

        val characters = changes.filter { it.subject.equals("CHAR", true) }.mapNotNull { change ->
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
        }.take(8)

        val volumes = changes.mapNotNull { change ->
            if (!change.subject.startsWith("VOLUME:", true)) return@mapNotNull null
            val order = change.subject.substringAfter(':').trim().toIntOrNull() ?: return@mapNotNull null
            FoundationVolume(
                order = order,
                title = change.field.trim().ifBlank { "第${order}卷" },
                objective = change.before.trim(),
                conflict = change.after.trim(),
                turningPoint = change.evidence.trim(),
                chapters = current?.volumes?.firstOrNull { it.order == order }?.chapters.orEmpty(),
            )
        }.sortedBy { it.order }.distinctBy { it.order }.take(5)

        val targetWords = meta?.before?.filter(Char::isDigit)?.toIntOrNull()
            ?.coerceIn(10_000, 5_000_000)
            ?: fallback.targetWords

        val safeTitle = output.title.trim().takeIf { it.filterNot(Char::isWhitespace).length in 2..12 }
            ?: fallback.title
        val safePremise = output.content.trim().takeIf { it.filterNot(Char::isWhitespace).length in 80..260 }
            ?: fallback.premise
        val genre = meaningfulValue(
            meta?.field,
            placeholders = setOf("类型", "小说类型", "题材", "genre"),
            fallback = fallback.genre,
        )
        val theme = meaningfulValue(
            meta?.after,
            placeholders = setOf("主题", "主题命题", "核心主题", "theme"),
            fallback = fallback.theme.takeUnless { it in setOf("主题", "主题命题") }
                ?: "人在真相、执念与代价之间如何选择",
        )

        return StoryFoundation(
            title = safeTitle,
            genre = genre,
            premise = safePremise,
            theme = theme,
            targetWords = targetWords,
            coreHook = meta?.evidence?.trim().orEmpty().ifBlank { fallback.coreHook },
            storyPromise = output.summary.trim().ifBlank { style?.before?.trim().orEmpty() }
                .ifBlank { fallback.rationale },
            styleGuide = style?.after?.trim().orEmpty().ifBlank {
                current?.styleGuide ?: "人物行动必须有因果，信息逐层释放，不临时改规则，不靠降智推动剧情。"
            },
            coverBrief = style?.evidence?.trim().orEmpty().ifBlank { fallback.coverBrief },
            masterTitle = master?.field?.trim().orEmpty().ifBlank { current?.masterTitle ?: "总纲" },
            masterObjective = master?.before?.trim().orEmpty().ifBlank {
                current?.masterObjective ?: fallback.premise
            },
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
            volumes = volumes.ifEmpty {
                current?.volumes.orEmpty().ifEmpty {
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
                }
            },
            foreshadowing = current?.foreshadowing.orEmpty(),
        )
    }

    private fun mergeChapters(foundation: StoryFoundation, output: GeneratedChapter): StoryFoundation {
        val chapters = output.stateChanges.mapNotNull { change ->
            if (!change.subject.startsWith("CHAPTER:", true)) return@mapNotNull null
            val parts = change.subject.split(':')
            val volume = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val order = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            if (volume != 1) return@mapNotNull null
            FoundationChapter(
                order = order,
                title = change.field.trim().ifBlank { "第${order}章" },
                objective = change.before.trim(),
                conflict = change.after.trim(),
                turningPoint = change.evidence.trim(),
            )
        }.sortedBy { it.order }.distinctBy { it.order }.take(14)

        if (chapters.isEmpty()) return foundation
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
            if (!change.subject.equals("FORESHADOW", true)) return@mapNotNull null
            val title = change.field.trim()
            if (title.isBlank()) return@mapNotNull null
            val range = Regex("(\\d+)\\D+(\\d+)").find(change.evidence)
            val start = range?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
            val end = range?.groupValues?.getOrNull(2)?.toIntOrNull() ?: maxOf(start + 3, 12)
            FoundationForeshadow(
                title = title,
                detail = change.before.trim(),
                expectedPayoff = change.after.trim(),
                expectedChapterStart = start,
                expectedChapterEnd = end,
            )
        }.take(8)
        return if (foreshadowing.isEmpty()) foundation else foundation.copy(foreshadowing = foreshadowing)
    }

    private fun compactConversation(messages: List<CreationChatMessage>): String = messages
        .takeLast(14)
        .joinToString("\n") { message ->
            val raw = if (message.role == "user") {
                message.text.substringBefore(RESEARCH_MARKER).trimEnd()
            } else message.text
            val text = raw.take(700)
            if (message.role == "user") "用户：$text" else "琅嬛：$text"
        }
        .takeLast(6_000)

    private fun compactFoundation(foundation: StoryFoundation, includeChapters: Boolean = false): String = buildString {
        appendLine("书名：${foundation.title}")
        appendLine("类型：${foundation.genre}")
        appendLine("简介：${foundation.premise}")
        appendLine("主题：${foundation.theme}")
        appendLine("核心钩子：${foundation.coreHook}")
        appendLine("故事承诺：${foundation.storyPromise}")
        appendLine("风格：${foundation.styleGuide}")
        appendLine("总纲：${foundation.masterObjective} / ${foundation.masterConflict} / ${foundation.masterTurningPoint}")
        appendLine("圣经：${foundation.bible.take(14).joinToString("；") { "${it.category.name}:${it.name}=${it.content.take(180)}" }}")
        appendLine("角色：${foundation.characters.take(8).joinToString("；") { "${it.name}[${it.personality.joinToString("、")}]目标=${it.goal.take(120)}" }}")
        foundation.volumes.take(5).forEach { volume ->
            appendLine("第${volume.order}卷 ${volume.title}：${volume.objective} / ${volume.conflict} / ${volume.turningPoint}")
            if (includeChapters && volume.order == 1) {
                volume.chapters.take(14).forEach { chapter ->
                    appendLine("  ${chapter.order}. ${chapter.title}：${chapter.objective} / ${chapter.conflict} / ${chapter.turningPoint}")
                }
            }
        }
    }.take(9_000)

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

        val CORE_SYSTEM = """
            你是“琅嬛”的长篇小说总架构师。现在只完成核心架构，不写详细章纲，不写正文。
            必须输出 GeneratedChapter JSON：
            - title：正式书名；content：120-190字平台简介；summary：80-180字故事承诺；touchedForeshadowingIds=[]。
            - stateChanges 只允许：
              1. 1条 META：subject 必须固定写 META；field 必须填写“实际小说类型”，例如“中式灵异悬疑”，禁止原样写“类型/小说类型”；before=目标总字数纯数字；after 必须填写一句“实际主题命题”，禁止原样写“主题/主题命题”；evidence=一句话核心钩子。
              2. 1条 STYLE：subject=STYLE；field=叙事风格；before=故事承诺补充；after=具体风格基线；evidence=封面方向。
              3. 1条 MASTER：subject=MASTER；field=总纲标题；before=全书目标；after=核心冲突；evidence=最大转折/终局方向。
              4. 6-12条 BIBLE:分类；分类仅 WORLD/RULE/CHARACTER/FACTION/LOCATION/ITEM/STYLE/FORBIDDEN。
              5. 3-6条 CHAR；field=角色名；before=性格词；after=长期目标；evidence=地点||身体||情绪||秘密||物品||关系。
              6. 3-5条 VOLUME:序号；field=卷名；before=本卷目标；after=核心冲突；evidence=关键转折。
            禁止输出 CHAPTER 和 FORESHADOW。设定少而硬，人物选择有代价，不临时改规则；参考作品只提炼高层技巧，不复制角色、专名、句式和剧情骨架。
        """.trimIndent()

        val CHAPTER_SYSTEM = """
            你是“琅嬛”的第一卷章纲设计师。只负责第一卷详细章纲，不修改书名、简介、人物、世界规则和其它卷纲。
            输出 GeneratedChapter JSON：title="FIRST_VOLUME_CHAPTERS"；content=""；summary=第一卷节奏摘要；touchedForeshadowingIds=[]。
            stateChanges 必须且只能包含 10-12 条 CHAPTER:1:序号，从1连续编号：field=章名；before=本章明确目标；after=本章具体冲突；evidence=章末转折。
            10-12章必须是连续因果链：上一章结果改变下一章条件；每2-3章至少一次信息差或关系变化；不要连续堆怪事，不要提前揭完终局真相。
        """.trimIndent()

        val FORESHADOW_SYSTEM = """
            你是“琅嬛”的长篇伏笔编辑。只根据既有核心蓝图和第一卷章纲设计伏笔，不修改其它结构。
            输出 GeneratedChapter JSON：title="FORESHADOW_PLAN"；content=""；summary=伏笔整体策略；touchedForeshadowingIds=[]。
            stateChanges 必须且只能包含 3-6 条 FORESHADOW：field=伏笔名；before=首次呈现时读者能看到的细节；after=预期回收方式；evidence=预计开始章-结束章，例如 2-18。
            伏笔必须可观察、可误解、可回收；禁止用“作者知道但正文从未出现”的秘密冒充伏笔。
        """.trimIndent()
    }
}
