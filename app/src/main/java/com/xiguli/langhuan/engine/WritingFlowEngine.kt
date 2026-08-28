package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot

data class ScenePlanSuggestion(
    val note: String,
    val scenes: List<ScenePlan>,
)

class WritingFlowEngine(
    private val gateway: AiGateway,
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
) {
    suspend fun planCurrentChapter(
        snapshot: StorySnapshot,
        chapter: ChapterDraft,
        currentScenes: List<ScenePlan>,
        conversation: List<Pair<String, String>> = emptyList(),
        instruction: String = "",
    ): ScenePlanSuggestion {
        val frame = chronologyGuard.frame(snapshot)
        val outline = snapshot.activeOutline.joinToString("\n") { node ->
            buildString {
                append("${node.level}:${node.title}｜目标=${node.objective}｜冲突=${node.conflict}｜转折=${node.turningPoint}")
                if (node.mustInclude.isNotEmpty()) append("｜必须=${node.mustInclude.joinToString("、")}")
                if (node.forbidden.isNotEmpty()) append("｜禁止=${node.forbidden.joinToString("、")}")
            }
        }
        val characters = snapshot.characters.joinToString("\n") {
            "${it.name}｜地点=${it.location}｜身体=${it.physicalState}｜情绪=${it.emotionalState}｜目标=${it.goal}"
        }
        val foreshadowing = snapshot.relevantForeshadowing
            .filter { it.status.name != "RESOLVED" && it.status.name != "ABANDONED" }
            .joinToString("\n") {
                "id=${it.id}｜${it.title}｜${it.detail}｜预计${it.expectedChapterStart}-${it.expectedChapterEnd}章回收"
            }
        val recent = snapshot.recentSummaries.takeLast(8).joinToString("\n")
        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(24)
            .joinToString("\n") {
                val clock = if (it.storyDay > 0) "故事第${it.storyDay}天·${it.timeOfDay.ifBlank { it.storyTime }}" else it.storyTime
                "第${it.chapter}章｜$clock｜${if (it.isFlashback) "闪回" else "主线"}｜${it.location}｜${it.summary}"
            }
        val current = currentScenes.sortedBy { it.order }.joinToString("\n") {
            val time = if (it.storyDay > 0 || it.timeOfDay.isNotBlank()) {
                "第${it.storyDay.takeIf { day -> day > 0 } ?: frame.anchorDay}天·${it.timeOfDay.ifBlank { "待定时段" }}｜距上一场=${it.elapsedFromPrevious.ifBlank { "连续" }}｜${if (it.isFlashback) "闪回" else "主线"}｜"
            } else "时间未锁定｜"
            "${it.order}. ${time}视角=${it.viewpoint}｜地点=${it.location}｜目的=${it.purpose}｜冲突=${it.conflict}｜结果=${it.outcome}"
        }
        val chat = conversation.takeLast(10).joinToString("\n") { (role, text) ->
            if (role == "user") "用户：$text" else "琅嬛：$text"
        }
        val temporalInstruction = (instruction + "\n" + chat)
        val allowLongSkip = frame.allowsLongSkip || Regex("次日|翌日|第二天|几天后|数日后|周后|月后|年后|跳时|时间跳跃").containsMatchIn(temporalInstruction)
        val allowFlashback = frame.allowsFlashback || Regex("闪回|回忆|过去|往事|年前|当年").containsMatchIn(temporalInstruction)

        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是“琅嬛”的章节场景导演。你的任务是把已经锁定的章纲拆成 2-6 个可直接写正文的场景，而不是改写总纲或另起主线。
                    输出必须严格符合 GeneratedChapter JSON：title、content、summary、stateChanges、touchedForeshadowingIds，不要 Markdown。

                    字段约定：
                    - title 固定为 scene-plan。
                    - content = 80-220 字说明本次场景编排为什么这样安排、时间和节奏如何连续推进。
                    - summary = 一句话说明本章最终应完成的情绪/剧情落点，并写清本章结束时的故事日和时段。
                    - stateChanges = 场景计划；每项：
                      subject=视角人物；field=地点；before=场景目的；after=场景冲突；
                      evidence 必须严格编码为“故事日序号||时段||距上一场经过多久||NORMAL或FLASHBACK||场景结果”。
                      例如：2||深夜||约20分钟||NORMAL||主角确认门外有人监视。
                    - touchedForeshadowingIds = 本章建议明确触及的既有伏笔 id。

                    时间硬规则：
                    1. 最新主时间锚点已经由 App 给出；第一场必须从该时间连续承接，除非章纲/用户明确要求跳时。
                    2. NORMAL 场景的故事日绝不能倒退。未经授权不得突然跳过多天、数周、数月或数年。
                    3. 每次换地点、等待、睡眠、交通、调查都要在“距上一场经过多久”里给出合理耗时。
                    4. 只有真正切入过去叙事的完整场景才能标记 FLASHBACK；人物一句想起往事不算闪回场景。
                    5. 闪回结束后必须回到进入闪回前的主时间钟，不能让过去时间污染当前时间。

                    剧情硬规则：
                    6. 本章唯一目标不能被替换；场景必须共同完成章纲目标。
                    7. 总纲/卷纲/章纲中的 mustInclude 必须落实，forbidden 绝不能出现。
                    8. 不得让人物知道其尚未知晓的信息，不得无因改变位置、能力、关系和性格。
                    9. 每个场景都必须产生新的信息、代价、关系变化或选择，禁止纯过场。
                    10. 最后一个场景必须形成章末转折或强钩子，但不能为了钩子提前泄露后续核心答案。
                """.trimIndent(),
                user = """
                    小说：${snapshot.novel.title}
                    核心命题：${snapshot.novel.premise}
                    主题：${snapshot.novel.theme}

                    【当前主时间钟】
                    最新锚点：${frame.anchorLabel}
                    结构化锚点：${if (frame.hasStructuredAnchor) "是" else "否，旧数据需保守承接"}
                    本章允许长跳时：${if (allowLongSkip) "是" else "否"}
                    本章允许完整闪回：${if (allowFlashback) "是" else "否"}

                    【当前章】
                    第${chapter.chapterNumber}章 ${chapter.title}
                    唯一目标：${chapter.objective}

                    【总纲→卷纲→章纲】
                    $outline

                    【人物当前状态】
                    $characters

                    【已确认时间线】
                    ${timeline.ifBlank { "暂无结构化事件；从故事第1天连续建立。" }}

                    【活跃伏笔】
                    $foreshadowing

                    【最近剧情记忆】
                    $recent

                    【当前场景计划】
                    ${current.ifBlank { "暂无，请从章纲开始拆分，并先确定每场故事日、时段和耗时。" }}

                    【本轮场景讨论】
                    ${chat.ifBlank { "暂无。" }}

                    【用户最新要求】
                    ${instruction.ifBlank { "请重新检查本章时间连续性和场景节奏，给出可直接进入正文写作的版本。" }}
                """.trimIndent(),
            )
        )

        var lastMainDay = frame.anchorDay
        val scenes = output.stateChanges.take(6).mapIndexed { index, change ->
            val parts = change.evidence.split("||", limit = 5).map { it.trim() }
            val encoded = parts.size >= 5
            val requestedDay = if (encoded) parts.getOrNull(0)?.toIntOrNull() ?: lastMainDay else lastMainDay
            val requestedFlashback = encoded && parts.getOrNull(3).equals("FLASHBACK", ignoreCase = true)
            val flashback = requestedFlashback && allowFlashback
            val normalizedDay = when {
                flashback -> requestedDay.coerceAtLeast(1)
                requestedDay < lastMainDay -> lastMainDay
                !allowLongSkip && requestedDay > frame.anchorDay + 1 -> frame.anchorDay + 1
                else -> requestedDay.coerceAtLeast(lastMainDay)
            }
            if (!flashback) lastMainDay = normalizedDay
            ScenePlan(
                order = index + 1,
                viewpoint = change.subject.trim().ifBlank { snapshot.characters.firstOrNull()?.name ?: "主角" },
                location = change.field.trim().ifBlank { "待定地点" },
                purpose = change.before.trim().ifBlank { "推进本章目标" },
                conflict = change.after.trim().ifBlank { "目标受到具体阻碍" },
                outcome = if (encoded) parts.getOrNull(4).orEmpty().ifBlank { "形成新的信息、代价或选择" } else change.evidence.trim().ifBlank { "形成新的信息、代价或选择" },
                storyDay = normalizedDay,
                timeOfDay = if (encoded) parts.getOrNull(1).orEmpty().ifBlank { if (index == 0) frame.anchorTimeOfDay else "稍后" } else if (index == 0) frame.anchorTimeOfDay else "稍后",
                elapsedFromPrevious = if (encoded) parts.getOrNull(2).orEmpty().ifBlank { if (index == 0) "紧接上一章" else "约10-30分钟" } else if (index == 0) "紧接上一章" else "约10-30分钟",
                isFlashback = flashback,
            )
        }.ifEmpty {
            currentScenes.ifEmpty {
                listOf(
                    ScenePlan(
                        order = 1,
                        viewpoint = snapshot.characters.firstOrNull()?.name ?: "主角",
                        location = snapshot.characters.firstOrNull()?.location ?: "承接上一章的场景",
                        purpose = "迅速承接上一章结果并明确本章目标",
                        conflict = "目标立即受到阻碍",
                        outcome = "主角被迫做出具体选择",
                        storyDay = frame.anchorDay,
                        timeOfDay = frame.anchorTimeOfDay,
                        elapsedFromPrevious = "紧接上一章",
                    ),
                    ScenePlan(
                        order = 2,
                        viewpoint = snapshot.characters.firstOrNull()?.name ?: "主角",
                        location = "本章核心场景",
                        purpose = chapter.objective,
                        conflict = "主要冲突升级",
                        outcome = "章末形成新的信息、代价或转折",
                        storyDay = frame.anchorDay,
                        timeOfDay = "稍后",
                        elapsedFromPrevious = "约10-30分钟",
                    ),
                )
            }
        }
        return ScenePlanSuggestion(
            note = output.content.trim().ifBlank { output.summary.trim().ifBlank { "场景计划已按当前章纲和主时间钟重新整理。" } },
            scenes = scenes,
        )
    }
}
