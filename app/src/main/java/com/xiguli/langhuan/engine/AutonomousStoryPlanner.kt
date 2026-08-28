package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.AutonomousStoryPlan
import com.xiguli.langhuan.domain.CharacterArcTarget
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.DriftSeverity
import com.xiguli.langhuan.domain.ForeshadowPlanAction
import com.xiguli.langhuan.domain.ForeshadowCadence
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.OutlineLevel
import com.xiguli.langhuan.domain.PlannedChapterBeat
import com.xiguli.langhuan.domain.PlotArcPhase
import com.xiguli.langhuan.domain.StoryDriftSignal
import com.xiguli.langhuan.domain.StorySnapshot
import kotlin.math.max

/**
 * Forward-looking planning layer for long novels.
 *
 * It deliberately stores proposals separately from Canon. Locked bible/outline/knowledge boundaries
 * are inputs and anchors; this planner never mutates them. The rolling plan can be regenerated at any
 * time without rewriting established facts.
 */
class AutonomousStoryPlanner(
    private val gateway: AiGateway,
) {
    suspend fun plan(
        snapshot: StorySnapshot,
        current: ChapterDraft,
        requestedHorizon: Int = 6,
    ): AutonomousStoryPlan {
        val horizon = requestedHorizon.coerceIn(3, 10)
        val startChapter = current.chapterNumber.coerceAtLeast(snapshot.novel.currentChapter) + 1
        val endChapter = startChapter + horizon - 1
        val lockedFuture = snapshot.outline
            .filter { it.level == OutlineLevel.CHAPTER && it.order in startChapter..endChapter && it.locked }
            .sortedBy { it.order }
        val currentArc = snapshot.longForm.arcs
            .filter { it.phase != PlotArcPhase.RESOLVED }
            .lastOrNull { current.chapterNumber <= it.plannedEndChapter + snapshot.longForm.config.arcSpan }
            ?: snapshot.longForm.arcs.lastOrNull()
        val activeForeshadows = snapshot.relevantForeshadowing
            .filter { it.status !in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED) }
            .sortedWith(compareBy({ it.expectedChapterEnd.takeIf { end -> end > 0 } ?: Int.MAX_VALUE }, { it.plantedChapter }))
        val recent = snapshot.recentSummaries.takeLast(10).joinToString("\n")
        val characters = snapshot.characters.take(16).joinToString("\n") {
            "- ${it.name}｜地点=${it.location}｜情绪=${it.emotionalState}｜目标=${it.goal}｜最近更新=第${it.lastUpdatedChapter}章"
        }
        val growth = snapshot.longForm.characterGrowth.take(16).joinToString("\n") {
            "- ${it.name}｜阶段=${it.stage}｜内在冲突=${it.internalConflict}｜成长方向=${it.growthDirection}｜最近转折=第${it.lastTurningChapter}章"
        }
        val foreshadows = activeForeshadows.take(18).joinToString("\n") {
            "- id=${it.id}｜${it.title}｜状态=${it.status}｜回收窗口=${it.expectedChapterStart}-${it.expectedChapterEnd}｜预期=${it.expectedPayoff}"
        }
        val outlineAnchors = lockedFuture.joinToString("\n") {
            "- 第${it.order}章 ${it.title}｜目标=${it.objective}｜冲突=${it.conflict}｜转折=${it.turningPoint}｜必须=${it.mustInclude.joinToString("、")}｜禁止=${it.forbidden.joinToString("、")}"
        }.ifBlank { "- 未来窗口内没有锁定章纲，可生成滚动计划，但必须服从总纲/卷纲。" }
        val canon = snapshot.bible.filter { it.locked }.take(24).joinToString("\n") {
            "- [${it.category}] ${it.name}：${it.content.take(420)}"
        }
        val boundaries = snapshot.knowledgeLedger.take(20).joinToString("\n") {
            "- ${it.title}｜读者=${it.readerState}｜最早完整揭露=第${it.earliestFullRevealChapter}章｜策略=${it.revealPolicy}｜knownBy=${it.knownBy.joinToString("、")}｜unknownTo=${it.unknownTo.joinToString("、")}"
        }
        val prompt = PromptBundle(
            system = """
                你是“琅嬛”的长篇自治总编。你不写正文，也无权修改已确认 Canon。你的任务是维护未来 3-10 章滚动计划，让小说持续朝当前总纲/卷纲推进，并提前发现偏航。

                权限边界：
                1. 锁定小说圣经、总纲、卷纲、章纲、信息边界和已经发生的正文事实都是不可改 Canon。你只能规划未来，不得用新计划覆盖它们。
                2. 未来窗口里如果已有锁定章纲，该章的标题/目标/核心冲突/转折必须以锁定章纲为锚；你只能补充角色焦点、伏笔节奏和防偏航护栏。
                3. 不得为了“更刺激”新增同等级主线、终极反派、世界规则、能力体系或幕后真相。需要新设定时只能作为“待作者确认”的风险，不可当事实。
                4. 信息边界中的隐藏秘密，在 earliestFullRevealChapter 之前只能按当前 revealPolicy 处理，不得提前把答案写入未来计划。
                5. 每章只承担一个可验证主目标；未来 3-10 章要形成因果链，不要 6 个彼此独立的点子。
                6. 人物弧必须从当前状态渐变，明确压力→选择→代价→阶段变化，禁止突然性格反转或回到几十章前的状态。
                7. 伏笔节奏优先处理已进入/接近回收窗口的旧伏笔；没有必要时不要继续开新坑。
                8. 若最近剧情重复、剧情弧逾期、角色长期停滞、伏笔逾期或未来章纲不足，必须给出 DRIFT 信号和最小纠偏策略；纠偏仍不能修改 Canon。

                输出 GeneratedChapter JSON，不要 Markdown。字段编码如下：
                - title 固定为 AUTONOMOUS_PLAN。
                - content = 100-500 字“最小纠偏策略”；没有明显偏航也要说明未来窗口的推进原则。
                - summary = 一句话说明本轮规划总方向。
                - stateChanges 用以下 field：
                  FUTURE_CHAPTER：subject="章号||标题"；before=唯一目标；after=主要冲突；evidence="章末转折||角色焦点（顿号）||伏笔id（顿号）||防偏航护栏"。
                  CHARACTER_ARC：subject=人物名；before=当前阶段；after=窗口结束前希望形成的可观察变化；evidence="目标章号||施压方式||禁止回退/突变"。
                  FORESHADOW_CADENCE：subject=伏笔 id（没有 id 才写标题）；before=当前状态；after=HOLD/TOUCH/ESCALATE/PAYOFF；evidence="目标章号||原因"。
                  DRIFT：subject=稳定短代码；before=证据；after=最小修复；evidence="INFO/WATCH/HIGH||问题说明"。
                - touchedForeshadowingIds=[]。
                FUTURE_CHAPTER 必须覆盖整个规划窗口，按章号递增，不得超出窗口。
            """.trimIndent(),
            user = """
                小说：${snapshot.novel.title}
                核心命题：${snapshot.novel.premise}
                主题：${snapshot.novel.theme}
                当前：第${current.chapterNumber}章 ${current.title}
                当前目标：${current.objective}
                规划窗口：第${startChapter}-${endChapter}章

                【当前滚动剧情弧】
                ${currentArc?.let { "${it.title}｜第${it.startChapter}-${it.plannedEndChapter}章｜阶段=${it.phase}｜目标=${it.objective}｜冲突=${it.centralConflict}｜预期收束=${it.expectedPayoff}" } ?: "尚未建立稳定滚动剧情弧。"}

                【未来锁定章纲｜不可覆盖】
                $outlineAnchors

                【锁定 Canon 摘要】
                ${canon.ifBlank { "暂无额外锁定圣经。" }}

                【信息边界】
                ${boundaries.ifBlank { "暂无结构化信息边界；仍不得自行创造幕后答案。" }}

                【人物当前状态】
                ${characters.ifBlank { "暂无结构化人物状态。" }}

                【人物成长轨迹】
                ${growth.ifBlank { "暂无成长轨迹；从当前人物状态开始规划。" }}

                【活跃伏笔】
                ${foreshadows.ifBlank { "暂无活跃伏笔。" }}

                【最近剧情摘要】
                ${recent.ifBlank { "暂无最近摘要。" }}

                【长篇健康提醒】
                ${snapshot.longForm.health.warnings.joinToString("\n") { "- $it" }.ifBlank { "- 当前没有本地健康警报。" }}

                请维护未来${horizon}章连续计划。任何与 Canon 冲突的灵感都必须舍弃，而不是解释成“新真相”。
            """.trimIndent(),
        )

        val output = gateway.generate(prompt)
        val aiBeats = output.stateChanges
            .filter { it.field.equals("FUTURE_CHAPTER", true) }
            .mapNotNull { change -> parseBeat(change.subject, change.before, change.after, change.evidence, startChapter, endChapter) }
            .associateBy { it.chapterNumber }
        val lockedByChapter = lockedFuture.associateBy { it.order }
        val beats = (startChapter..endChapter).map { chapter ->
            val locked = lockedByChapter[chapter]
            val ai = aiBeats[chapter]
            if (locked != null) {
                PlannedChapterBeat(
                    chapterNumber = chapter,
                    title = locked.title,
                    objective = locked.objective,
                    conflict = locked.conflict,
                    turningPoint = locked.turningPoint,
                    characterFocus = ai?.characterFocus.orEmpty(),
                    foreshadowingTargets = ai?.foreshadowingTargets.orEmpty(),
                    guardrail = listOf(
                        "锁定章纲优先，禁止改写该章核心任务",
                        ai?.guardrail.orEmpty(),
                    ).filter { it.isNotBlank() }.joinToString("；"),
                    fixedByOutline = true,
                )
            } else {
                ai ?: fallbackBeat(snapshot, chapter, currentArc?.objective.orEmpty())
            }
        }

        val characterTargets = output.stateChanges
            .filter { it.field.equals("CHARACTER_ARC", true) }
            .mapNotNull { change ->
                val parts = change.evidence.split("||", limit = 3).map { it.trim() }
                val name = change.subject.trim()
                if (name.isBlank() || change.after.isBlank()) null else CharacterArcTarget(
                    name = name,
                    targetChapter = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(startChapter, endChapter) ?: endChapter,
                    currentState = change.before.trim(),
                    desiredChange = change.after.trim(),
                    pressure = parts.getOrNull(1).orEmpty(),
                    forbiddenRegression = parts.getOrNull(2).orEmpty(),
                )
            }
            .distinctBy { it.name }
            .take(12)

        val cadence = output.stateChanges
            .filter { it.field.equals("FORESHADOW_CADENCE", true) }
            .mapNotNull { change ->
                val target = activeForeshadows.firstOrNull { it.id == change.subject.trim() || it.title.equals(change.subject.trim(), true) }
                    ?: return@mapNotNull null
                val parts = change.evidence.split("||", limit = 2).map { it.trim() }
                val action = runCatching { ForeshadowPlanAction.valueOf(change.after.trim().uppercase()) }.getOrDefault(ForeshadowPlanAction.HOLD)
                ForeshadowCadence(
                    foreshadowId = target.id,
                    title = target.title,
                    targetChapter = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(startChapter, endChapter) ?: endChapter,
                    action = action,
                    reason = parts.getOrNull(1).orEmpty(),
                )
            }
            .distinctBy { it.foreshadowId }
            .take(16)

        val aiDrift = output.stateChanges
            .filter { it.field.equals("DRIFT", true) }
            .mapNotNull { change -> parseDrift(change.subject, change.before, change.after, change.evidence) }
        val localDrift = localDriftSignals(snapshot, current.chapterNumber)
        val oldGeneration = snapshot.longForm.autonomousPlan.generation
        return AutonomousStoryPlan(
            baseChapter = current.chapterNumber,
            horizonEndChapter = endChapter,
            generation = oldGeneration + 1,
            updatedAt = System.currentTimeMillis(),
            chapters = beats,
            characterTargets = characterTargets,
            foreshadowCadence = cadence,
            driftSignals = (localDrift + aiDrift).distinctBy { it.code to it.message }.take(20),
            correctionStrategy = output.content.trim().ifBlank { defaultCorrection(snapshot) },
            canonDigest = canonDigest(snapshot),
        )
    }

    fun apply(snapshot: StorySnapshot, plan: AutonomousStoryPlan): StorySnapshot =
        snapshot.copy(longForm = snapshot.longForm.copy(autonomousPlan = plan))

    companion object {
        fun shouldRefresh(snapshot: StorySnapshot, currentChapter: Int): Boolean {
            val plan = snapshot.longForm.autonomousPlan
            if (plan.baseChapter <= 0 || plan.chapters.isEmpty()) return true
            val remaining = plan.chapters.count { it.chapterNumber > currentChapter }
            return remaining < 3 || currentChapter - plan.baseChapter >= 2 ||
                plan.driftSignals.any { it.severity == DriftSeverity.HIGH }
        }

        /** Planning-only guidance. It is intentionally not injected into the prose Context Builder. */
        fun promptText(snapshot: StorySnapshot): String {
            val plan = snapshot.longForm.autonomousPlan
            val current = snapshot.novel.currentChapter
            val future = plan.chapters.filter { it.chapterNumber > current }.take(5)
            if (future.isEmpty()) return "【自治滚动计划】尚未生成；请按总纲、卷纲和当前状态规划下一章。"
            return buildString {
                appendLine("【自治滚动计划｜仅用于规划，锁定 Canon/章纲优先】")
                future.forEach { beat ->
                    appendLine("- 第${beat.chapterNumber}章 ${beat.title}｜目标=${beat.objective}｜冲突=${beat.conflict}｜转折=${beat.turningPoint}")
                    if (beat.characterFocus.isNotEmpty()) appendLine("  角色焦点=${beat.characterFocus.joinToString("、")}")
                    if (beat.foreshadowingTargets.isNotEmpty()) appendLine("  伏笔节奏=${beat.foreshadowingTargets.joinToString("、")}")
                    if (beat.guardrail.isNotBlank()) appendLine("  护栏=${beat.guardrail}")
                }
                if (plan.correctionStrategy.isNotBlank()) appendLine("最小纠偏：${plan.correctionStrategy.take(900)}")
                val high = plan.driftSignals.filter { it.severity == DriftSeverity.HIGH }.take(4)
                if (high.isNotEmpty()) {
                    appendLine("高优先级偏航：")
                    high.forEach { appendLine("- ${it.message}｜修复=${it.repair}") }
                }
            }.take(5_000)
        }

        private fun parseBeat(
            subject: String,
            objective: String,
            conflict: String,
            evidence: String,
            start: Int,
            end: Int,
        ): PlannedChapterBeat? {
            val subjectParts = subject.split("||", limit = 2).map { it.trim() }
            val number = subjectParts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: return null
            if (number !in start..end) return null
            val evidenceParts = evidence.split("||", limit = 4).map { it.trim() }
            return PlannedChapterBeat(
                chapterNumber = number,
                title = subjectParts.getOrNull(1).orEmpty().ifBlank { "第${number}章" },
                objective = objective.trim().ifBlank { "承接上一章结果并推进当前剧情弧。" },
                conflict = conflict.trim().ifBlank { "既有目标遭遇更具体的阻力。" },
                turningPoint = evidenceParts.getOrNull(0).orEmpty().ifBlank { "章末产生新的代价、选择或信息。" },
                characterFocus = splitList(evidenceParts.getOrNull(1).orEmpty()),
                foreshadowingTargets = splitList(evidenceParts.getOrNull(2).orEmpty()),
                guardrail = evidenceParts.getOrNull(3).orEmpty(),
            )
        }

        private fun parseDrift(subject: String, before: String, after: String, evidence: String): StoryDriftSignal? {
            val parts = evidence.split("||", limit = 2).map { it.trim() }
            val severity = when (parts.getOrNull(0).orEmpty().uppercase()) {
                "HIGH", "BLOCKING", "RISK" -> DriftSeverity.HIGH
                "WATCH", "WARNING" -> DriftSeverity.WATCH
                else -> DriftSeverity.INFO
            }
            val message = parts.getOrNull(1).orEmpty().ifBlank { subject.trim() }
            if (message.isBlank()) return null
            return StoryDriftSignal(
                code = subject.trim().ifBlank { "AI_DRIFT" },
                severity = severity,
                message = message,
                evidence = before.trim(),
                repair = after.trim(),
            )
        }

        private fun localDriftSignals(snapshot: StorySnapshot, chapter: Int): List<StoryDriftSignal> {
            val signals = mutableListOf<StoryDriftSignal>()
            snapshot.longForm.health.warnings.take(8).forEachIndexed { index, warning ->
                signals += StoryDriftSignal(
                    code = "HEALTH_${index + 1}",
                    severity = if (snapshot.longForm.health.level.name == "RISK") DriftSeverity.HIGH else DriftSeverity.WATCH,
                    message = warning,
                    evidence = "本地长篇健康体检",
                    repair = "未来滚动计划优先用最小剧情动作处理该问题，不新增无关主线。",
                )
            }
            val overdue = snapshot.relevantForeshadowing.filter {
                it.status !in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED) &&
                    it.expectedChapterEnd > 0 && chapter > it.expectedChapterEnd
            }
            if (overdue.isNotEmpty()) signals += StoryDriftSignal(
                code = "OVERDUE_FORESHADOW",
                severity = DriftSeverity.HIGH,
                message = "${overdue.size} 条伏笔已超过计划回收窗口：${overdue.take(4).joinToString("、") { it.title }}",
                evidence = overdue.take(8).joinToString("；") { "${it.title}@${it.expectedChapterStart}-${it.expectedChapterEnd}" },
                repair = "未来 3-6 章优先安排自然触及或阶段回收，不再开同类型新坑。",
            )
            val stale = snapshot.characters.filter { chapter - it.lastUpdatedChapter >= 35 }
            if (stale.isNotEmpty()) signals += StoryDriftSignal(
                code = "STALE_CHARACTER_ARC",
                severity = DriftSeverity.WATCH,
                message = "${stale.size} 个长期角色超过 35 章没有有效状态推进。",
                evidence = stale.take(6).joinToString("、") { it.name },
                repair = "只选择仍与当前主线相关的角色，在未来窗口安排一次有代价的选择或关系变化。",
            )
            val fineOutline = snapshot.outline.count { it.level == OutlineLevel.CHAPTER && it.order in (chapter + 1)..(chapter + 5) }
            if (snapshot.outline.any { it.level == OutlineLevel.CHAPTER } && fineOutline < 3) signals += StoryDriftSignal(
                code = "ROLLING_OUTLINE_THIN",
                severity = DriftSeverity.WATCH,
                message = "未来 5 章只有 $fineOutline 条正式章纲，滚动规划过薄。",
                evidence = "chapter=$chapter",
                repair = "用自治计划维持至少 3-5 章可见因果链；只有作者确认后才升级为正式锁定章纲。",
            )
            return signals
        }

        private fun fallbackBeat(snapshot: StorySnapshot, chapter: Int, arcObjective: String): PlannedChapterBeat {
            val previous = snapshot.longForm.autonomousPlan.chapters.firstOrNull { it.chapterNumber == chapter }
            if (previous != null) return previous.copy(fixedByOutline = false)
            return PlannedChapterBeat(
                chapterNumber = chapter,
                title = "第${chapter}章",
                objective = arcObjective.ifBlank { "承接前一章结果，推进当前卷的同一条主线。" },
                conflict = "当前目标遭遇一个来自既有因果链的更具体阻碍。",
                turningPoint = "章末让人物付出代价或获得有限新信息，并自然迫使下一章继续行动。",
                guardrail = "不新增同等级主线；不提前揭露受保护秘密；不跳过人物因果。",
            )
        }

        private fun defaultCorrection(snapshot: StorySnapshot): String {
            val warnings = snapshot.longForm.health.warnings
            return if (warnings.isEmpty()) {
                "保持当前主线，未来窗口每章只推进一个可验证目标；人物变化必须由压力、选择和代价逐步形成，旧伏笔优先于新增复杂度。"
            } else {
                "优先处理现有长篇健康提醒：${warnings.take(3).joinToString("；")}。采用最小剧情修复，不修改锁定 Canon，不以新增世界观或新主线掩盖旧问题。"
            }
        }

        private fun canonDigest(snapshot: StorySnapshot): String {
            val raw = buildString {
                snapshot.bible.filter { it.locked }.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.content).append('|') }
                snapshot.outline.filter { it.locked }.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.objective).append(':').append(it.turningPoint).append('|') }
                snapshot.knowledgeLedger.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.revealPolicy).append(':').append(it.earliestFullRevealChapter).append('|') }
            }
            return raw.hashCode().toString()
        }

        private fun splitList(value: String): List<String> = value
            .split('、', ',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
    }
}
