package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ConsistencyIssue
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.domain.TimelineEvent

data class ChronologyFrame(
    val latestMainEvent: TimelineEvent?,
    val anchorDay: Int,
    val anchorTimeOfDay: String,
    val anchorLabel: String,
    val hasStructuredAnchor: Boolean,
    val allowsLongSkip: Boolean,
    val allowsFlashback: Boolean,
)

/**
 * 把时间从“模型自己理解的自然语言”提升成写作硬约束。
 * 这里不尝试理解所有日期表达，只负责守住主时间钟、场景顺序以及未计划的长跳时。
 */
class ChronologyGuard {
    fun frame(snapshot: StorySnapshot): ChronologyFrame {
        val ordered = snapshot.recentTimeline.sortedWith(
            compareBy<TimelineEvent> { it.chapter }.thenBy { it.orderInChapter }
        )
        val latestMain = ordered.lastOrNull { !it.isFlashback }
        val parsedDay = latestMain?.let(::eventDay) ?: 0
        val anchorDay = parsedDay.takeIf { it > 0 } ?: 1
        val anchorTime = latestMain?.timeOfDay?.ifBlank { inferTimeOfDay(latestMain.storyTime) }
            .orEmpty()
            .ifBlank { latestMain?.storyTime.orEmpty().ifBlank { "故事开场" } }
        val outlineText = snapshot.activeOutline.joinToString(" ") {
            listOf(it.title, it.objective, it.conflict, it.turningPoint, it.mustInclude.joinToString(" "), it.forbidden.joinToString(" ")).joinToString(" ")
        }
        return ChronologyFrame(
            latestMainEvent = latestMain,
            anchorDay = anchorDay,
            anchorTimeOfDay = anchorTime,
            anchorLabel = latestMain?.let {
                if (parsedDay > 0) "故事第${parsedDay}天·$anchorTime" else it.storyTime.ifBlank { "上一章末尾" }
            } ?: "故事第1天·开场",
            hasStructuredAnchor = parsedDay > 0,
            allowsLongSkip = LONG_SKIP_HINT.containsMatchIn(outlineText),
            allowsFlashback = FLASHBACK_HINT.containsMatchIn(outlineText),
        )
    }

    fun promptText(snapshot: StorySnapshot, scenes: List<ScenePlan>): String {
        val frame = frame(snapshot)
        val sorted = scenes.sortedBy { it.order }
        val hasLockedScenes = sorted.any { it.storyDay > 0 || it.timeOfDay.isNotBlank() }
        val sceneText = if (hasLockedScenes) {
            sorted.joinToString("\n") { scene ->
                val mode = if (scene.isFlashback) "闪回" else "主时间线"
                val day = scene.storyDay.takeIf { it > 0 }?.let { "故事第${it}天" } ?: "沿用上一场故事日"
                "- 场景${scene.order}: $day·${scene.timeOfDay.ifBlank { "时段未标注" }}｜距上一场=${scene.elapsedFromPrevious.ifBlank { "紧接/由上下文确定" }}｜$mode｜地点=${scene.location}"
            }
        } else {
            "- 旧场景计划尚未结构化时间：默认从“${frame.anchorLabel}”连续承接，不得自行跨天；若确需跨天或闪回，必须由章纲/补充要求明确授权。"
        }
        return """
            主时间钟锚点：${frame.anchorLabel}
            ${if (frame.hasStructuredAnchor) "当前主时间线已经结构化，不得倒退。" else "旧项目时间锚点较弱，本章必须从保守连续时间开始，禁止凭空发明年月日。"}
            是否允许长跳时：${if (frame.allowsLongSkip) "章纲已明确允许" else "默认不允许（最多自然推进到相邻故事日）"}
            是否允许闪回：${if (frame.allowsFlashback) "章纲已明确允许" else "默认不允许切换叙事时间层"}
            场景时间表：
            $sceneText

            时间硬规则：
            1. 正文只能按场景时间表从前到后发生；不得把后一个场景写到前一个场景之前，闪回场景除外。
            2. 没有明确授权时，不得突然出现“几天后 / 第二天 / 数月后 / 一年后 / 多年后”等跳时。
            3. 场景之间移动、睡眠、等待、调查、交通都必须消耗合理时间，不能一笔带过造成瞬移式时间推进。
            4. 回忆只允许作为人物短暂想起的内容；若要整段切入过去，必须对应 isFlashback=true 的场景。
            5. 章末 summary 必须明确写出“本章结束时=故事第N天·时段”，供下一章锁定承接。
        """.trimIndent()
    }

    fun inspect(request: GenerationRequest, output: GeneratedChapter): List<ConsistencyIssue> {
        val issues = mutableListOf<ConsistencyIssue>()
        val frame = frame(request.snapshot)
        val scenes = request.chapter.scenePlan.sortedBy { it.order }
        val mainScenes = scenes.filterNot { it.isFlashback }
        val lockedMainDays = mainScenes.mapNotNull { it.storyDay.takeIf { day -> day > 0 } }

        if (scenes.isEmpty() || scenes.none { it.storyDay > 0 || it.timeOfDay.isNotBlank() }) {
            issues += ConsistencyIssue(
                severity = IssueSeverity.WARNING,
                code = "TIME_PLAN_MISSING",
                message = "本章场景还没有结构化时间锁，正文只能依赖保守承接规则",
                repairInstruction = "先运行一次“AI 检查场景”，为每个场景补齐故事日、时段和距上一场耗时",
            )
        }

        mainScenes.zipWithNext().forEach { (before, after) ->
            if (before.storyDay > 0 && after.storyDay > 0 && after.storyDay < before.storyDay) {
                issues += blocking(
                    "SCENE_TIME_REVERSED",
                    "场景${after.order}的故事日早于场景${before.order}，主时间线发生倒退",
                    "重新规划场景时间；若确实是过去场景，必须显式标记为闪回",
                    "场景${before.order}=第${before.storyDay}天，场景${after.order}=第${after.storyDay}天",
                )
            }
        }

        val firstDay = lockedMainDays.firstOrNull()
        if (frame.hasStructuredAnchor && firstDay != null && firstDay < frame.anchorDay) {
            issues += blocking(
                "CHAPTER_START_BEFORE_CLOCK",
                "本章主时间线从故事第${firstDay}天开始，但上一条确定主时间已经到第${frame.anchorDay}天",
                "让第一场承接故事第${frame.anchorDay}天，或把过去段落显式规划成闪回",
            )
        }

        val maxDay = lockedMainDays.maxOrNull()
        if (!frame.allowsLongSkip && maxDay != null && maxDay > frame.anchorDay + 1) {
            issues += blocking(
                "SCENE_LONG_TIME_SKIP",
                "场景计划未经章纲授权就从故事第${frame.anchorDay}天跳到了第${maxDay}天",
                "把时间压回连续的一天内，或先在章纲中明确这次时间跳跃的原因和跨度",
            )
        }

        mainScenes.forEach { scene ->
            if (scene.elapsedFromPrevious.contains(Regex("(?:周|个月|月|年)")) && !frame.allowsLongSkip) {
                issues += blocking(
                    "ELAPSED_TIME_TOO_LARGE",
                    "场景${scene.order}写明距上一场“${scene.elapsedFromPrevious}”，但当前章纲没有授权长时间跳跃",
                    "改成合理的分钟/小时级推进，或先修改章纲明确时间跳跃",
                )
            }
        }

        val plannedAcrossDay = maxDay != null && maxDay > frame.anchorDay
        if (!frame.allowsLongSkip && !plannedAcrossDay && HARD_JUMP_IN_PROSE.containsMatchIn(output.content)) {
            val evidence = HARD_JUMP_IN_PROSE.find(output.content)?.value.orEmpty()
            issues += blocking(
                "UNPLANNED_TIME_JUMP",
                "正文出现了场景计划之外的时间跳跃：$evidence",
                "删除未经计划的跳时并按当前场景时间连续重写；如果跳时确有必要，先回到场景规划明确故事日",
                evidence,
            )
        }

        val hasFlashbackScene = scenes.any { it.isFlashback }
        if (!frame.allowsFlashback && !hasFlashbackScene && HARD_FLASHBACK_IN_PROSE.containsMatchIn(output.content)) {
            val evidence = HARD_FLASHBACK_IN_PROSE.find(output.content)?.value.orEmpty()
            issues += ConsistencyIssue(
                severity = IssueSeverity.WARNING,
                code = "UNPLANNED_FLASHBACK",
                message = "正文出现明显过去时间切换，但场景计划没有闪回：$evidence",
                evidence = evidence,
                repairInstruction = "若只是人物想起往事，压缩成短暂回忆；若要完整写过去场景，先在场景规划中显式标记闪回",
            )
        }

        return issues.distinctBy { listOf(it.code, it.message, it.evidence) }
    }

    fun eventDay(event: TimelineEvent): Int {
        if (event.storyDay > 0) return event.storyDay
        return STORY_DAY.find(event.storyTime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun inferTimeOfDay(value: String): String = TIME_OF_DAY.find(value)?.value.orEmpty()

    private fun blocking(code: String, message: String, repair: String, evidence: String = "") =
        ConsistencyIssue(IssueSeverity.BLOCKING, code, message, evidence, repair)

    companion object {
        private val STORY_DAY = Regex("(?:故事)?第(\\d+)天")
        private val TIME_OF_DAY = Regex("凌晨|清晨|早晨|上午|中午|下午|傍晚|黄昏|晚上|夜间|深夜|子夜")
        private val LONG_SKIP_HINT = Regex("次日|翌日|第二天|几天后|数日后|天后|周后|月后|个月后|年后|多年后|时间跳跃|跳时|数周|数月|数年")
        private val FLASHBACK_HINT = Regex("闪回|回忆|过去|往事|年前|当年|童年|旧事")
        private val HARD_JUMP_IN_PROSE = Regex("(?:第二天|次日|翌日|数日后|几天后|[两二三四五六七八九十\\d]+天后|数周后|几周后|[两二三四五六七八九十\\d]+周后|数月后|几个月后|[两二三四五六七八九十\\d]+个月后|[两二三四五六七八九十\\d]+年后|数年后|多年后)")
        private val HARD_FLASHBACK_IN_PROSE = Regex("(?:一年前|两年前|几年前|数年前|多年以前|回到.{0,8}年前|那一年|当年的)")
    }
}
