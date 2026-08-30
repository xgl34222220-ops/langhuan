package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot

data class ChapterPlanSuggestion(
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
    val scenes: List<ScenePlan>,
)

class WorkspaceAiEngine(
    private val gateway: AiGateway,
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
    private val longFormEngine: LongFormContinuityEngine = LongFormContinuityEngine(),
) {
    suspend fun planNextChapter(snapshot: StorySnapshot, current: ChapterDraft): ChapterPlanSuggestion {
        val frame = chronologyGuard.frame(snapshot)
        val recent = snapshot.recentSummaries.takeLast(snapshot.longForm.config.hotChapterWindow.coerceIn(5, 10)).joinToString("\n")
        val characters = snapshot.characters.joinToString("\n") {
            "${it.name}｜地点=${it.location}｜目标=${it.goal}｜情绪=${it.emotionalState}"
        }
        val nextChapter = current.chapterNumber + 1
        val activeForeshadowing = snapshot.relevantForeshadowing
            .filter { it.status.name != "RESOLVED" && it.status.name != "ABANDONED" }
            .joinToString("\n") { item ->
                val urgency = when {
                    item.expectedChapterEnd > 0 && nextChapter > item.expectedChapterEnd -> "，已超过计划回收窗口"
                    item.expectedChapterStart > 0 && nextChapter >= item.expectedChapterStart -> "，已进入计划回收窗口"
                    else -> ""
                }
                "${item.title}：${item.detail}，状态=${item.status}，计划${item.expectedChapterStart}-${item.expectedChapterEnd}章回收$urgency"
            }
        val outline = snapshot.activeOutline.joinToString("\n") {
            "${it.level}:${it.title}｜目标=${it.objective}｜冲突=${it.conflict}｜转折=${it.turningPoint}"
        }
        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(20)
            .joinToString("\n") {
                val clock = if (it.storyDay > 0) "故事第${it.storyDay}天·${it.timeOfDay}" else it.storyTime
                "第${it.chapter}章 $clock ${if (it.isFlashback) "[FLASHBACK]" else "[NORMAL]"} ${it.location}：${it.summary}"
            }
        val longFormNavigation = longFormEngine.promptText(snapshot) + "\n\n" + AutonomousStoryPlanner.promptText(snapshot)
        val prompt = PromptBundle(
            system = """
                你是长篇小说的章节策划引擎。你必须沿用现有总纲、卷纲、人物状态、滚动剧情弧、伏笔、主时间钟和最近剧情，规划紧接当前章节的下一章，不得擅自换主线。
                输出必须符合 GeneratedChapter JSON：title、content、summary、stateChanges、touchedForeshadowingIds。
                这里字段有专门含义：
                - title = 下一章标题。
                - content = 下一章唯一主目标，必须具体可验证。
                - summary = “主要冲突 || 章末转折”，必须用两个竖线分隔。
                - stateChanges = 场景计划。每项 subject=视角人物，field=地点，before=场景目的，after=场景冲突，evidence 必须为“故事日序号||时段||距上一场经过多久||NORMAL或FLASHBACK||场景结果||参与人物（顿号分隔）”。
                - touchedForeshadowingIds = 建议本章触及的伏笔 id。
                至少规划 2 个、最多 6 个场景。不要返回小说正文。

                超长篇规则：
                1. 下一章优先推进当前 20-40 章滚动剧情弧，不要因为灵感突然新增同等级主线。
                2. 若有伏笔已经进入计划回收窗口或超过最晚回收窗口，优先寻找自然触及或回收机会；禁止机械硬塞。
                3. 角色成长必须承接已记录的阶段与最近转折，不能突然恢复到几十章前的心理状态。
                4. 长篇体检提醒是风险提示，不是强制剧情；先解决重复、拖延和旧坑，再考虑继续扩张复杂度。

                时间规则：
                1. 第一场默认承接 App 给出的最新主时间钟，不得为了“新章开场”自动跳到第二天。
                2. 没有总纲/卷纲明确要求时，下一章最多自然推进到相邻故事日，禁止几周、几个月、几年后的突跳。
                3. NORMAL 场景故事日不可倒退；每次移动、等待、睡眠和交通必须填写合理耗时。
                4. 不要默认使用 FLASHBACK。只有大纲明确需要过去叙事时才允许。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                核心命题：${snapshot.novel.premise}
                主题：${snapshot.novel.theme}
                当前章节：第${current.chapterNumber}章 ${current.title}
                当前章节目标：${current.objective}
                当前章节摘要：${current.summary.ifBlank { "尚未形成摘要" }}
                最新主时间钟：${frame.anchorLabel}

                【当前大纲链】
                $outline

                【超长篇滚动导航】
                $longFormNavigation

                【人物状态】
                $characters

                【结构化时间线】
                ${timeline.ifBlank { "暂无；按故事第1天连续建立。" }}

                【活跃伏笔】
                $activeForeshadowing

                【热记忆 / 最近剧情】
                $recent

                请规划第${current.chapterNumber + 1}章，并先把每一个场景的故事日、时段和耗时锁死。
            """.trimIndent(),
        )
        val output = gateway.generate(prompt)
        val summaryParts = output.summary.split("||", limit = 2).map { it.trim() }
        var lastMainDay = frame.anchorDay
        val scenes = output.stateChanges.take(6).mapIndexed { index, item ->
            val parts = item.evidence.split("||", limit = 6).map { it.trim() }
            val encoded = parts.size >= 5
            val requestedDay = if (encoded) parts.getOrNull(0)?.toIntOrNull() ?: lastMainDay else lastMainDay
            val flashback = encoded && parts.getOrNull(3).equals("FLASHBACK", ignoreCase = true) && frame.allowsFlashback
            val day = when {
                flashback -> requestedDay.coerceAtLeast(1)
                requestedDay < lastMainDay -> lastMainDay
                !frame.allowsLongSkip && requestedDay > frame.anchorDay + 1 -> frame.anchorDay + 1
                else -> requestedDay.coerceAtLeast(lastMainDay)
            }
            if (!flashback) lastMainDay = day
            ScenePlan(
                order = index + 1,
                viewpoint = item.subject.ifBlank { "主角" },
                location = item.field.ifBlank { "待定地点" },
                purpose = item.before.ifBlank { "推动本章目标" },
                conflict = item.after.ifBlank { "目标受到阻碍" },
                outcome = if (encoded) parts.getOrNull(4).orEmpty().ifBlank { "形成新的信息或选择" } else item.evidence.ifBlank { "形成新的信息或选择" },
                participants = buildList {
                    add(item.subject.trim())
                    if (encoded) addAll(splitPeople(parts.getOrNull(5).orEmpty()))
                }.filter(String::isNotBlank).distinct(),
                storyDay = day,
                timeOfDay = if (encoded) parts.getOrNull(1).orEmpty().ifBlank { if (index == 0) frame.anchorTimeOfDay else "稍后" } else if (index == 0) frame.anchorTimeOfDay else "稍后",
                elapsedFromPrevious = if (encoded) parts.getOrNull(2).orEmpty().ifBlank { if (index == 0) "紧接上一章" else "约10-30分钟" } else if (index == 0) "紧接上一章" else "约10-30分钟",
                isFlashback = flashback,
            )
        }.ifEmpty {
            listOf(
                ScenePlan(
                    order = 1,
                    viewpoint = "主角",
                    location = snapshot.characters.firstOrNull()?.location ?: "承接上一章的场景",
                    purpose = "承接上一章结果",
                    conflict = "新问题立即出现",
                    outcome = "主角被迫做出下一步选择",
                    storyDay = frame.anchorDay,
                    timeOfDay = frame.anchorTimeOfDay,
                    elapsedFromPrevious = "紧接上一章",
                ),
                ScenePlan(
                    order = 2,
                    viewpoint = "主角",
                    location = "核心场景",
                    purpose = "推进本章唯一目标",
                    conflict = "关键阻碍升级",
                    outcome = "章末形成新的代价或转折",
                    storyDay = frame.anchorDay,
                    timeOfDay = "稍后",
                    elapsedFromPrevious = "约10-30分钟",
                ),
            )
        }
        return ChapterPlanSuggestion(
            title = output.title.ifBlank { "第${current.chapterNumber + 1}章" },
            objective = output.content.trim().ifBlank { "承接上一章结果并推进当前卷主线。" },
            conflict = summaryParts.getOrNull(0).orEmpty().ifBlank { "人物目标遭遇更具体的阻碍。" },
            turningPoint = summaryParts.getOrNull(1).orEmpty().ifBlank { "章末出现新的信息、代价或选择。" },
            scenes = scenes,
        )
    }

    private fun splitPeople(value: String): List<String> = value
        .split('、', ',', '，', ';', '；')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    suspend fun rewriteSelection(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        selectedText: String,
        instruction: String,
    ): String {
        require(selectedText.isNotBlank()) { "请先选择需要重写的正文" }
        val contextBefore = chapter.content.substringBefore(selectedText, "").takeLast(1_200)
        val contextAfter = chapter.content.substringAfter(selectedText, "").take(1_200)
        val chronology = chronologyGuard.promptText(snapshot, chapter.scenePlan)
        val prompt = PromptBundle(
            system = """
                你是长篇小说局部改写引擎。只重写用户选中的片段，不改变片段前后的既定事实、人物状态、剧情方向和时间位置。
                输出必须符合 GeneratedChapter JSON。title 固定为 rewrite；content 只放替换后的正文，不要包含原文、解释、Markdown 或引号；summary 简述改写策略；stateChanges 和 touchedForeshadowingIds 均返回空数组。
                改写后的长度原则上保持原片段的 60%-160%，除非用户明确要求扩写或缩写。
                禁止在局部改写中擅自新增“第二天、几天后、几个月后、几年后”等时间跳跃；禁止把普通回忆改成完整闪回，也不得改变故事第N天。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                本章：第${chapter.chapterNumber}章 ${chapter.title}
                本章目标：${chapter.objective}
                用户要求：${instruction.ifBlank { "润色表达，增强画面感与节奏，不改变事实。" }}

                【时间轴锁】
                $chronology

                【片段之前】
                $contextBefore

                【要重写的片段】
                $selectedText

                【片段之后】
                $contextAfter
            """.trimIndent(),
        )
        return gateway.generate(prompt).content.trim().ifBlank { error("AI 没有返回改写内容") }
    }
}
