package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StateChange
import com.xiguli.langhuan.domain.StorySnapshot

enum class AgentActionKind {
    CHARACTER_NEW,
    CHARACTER_LOCATION,
    CHARACTER_EMOTION,
    CHARACTER_GOAL,
    RELATION,
    KNOWLEDGE_GAIN,
    TIMELINE,
    FORESHADOW_NEW,
    FORESHADOW_UPDATE,
    CONSISTENCY,
    OUTLINE_GAP,
    PACING,
    ARC,
    NEXT_OPTION,
    UNKNOWN,
}

data class AgentAction(
    val kind: AgentActionKind,
    val subject: String,
    val before: String,
    val after: String,
    val evidence: String,
)

data class AgentNextOption(
    val title: String,
    val objective: String,
    val conflict: String,
    val turningPoint: String,
)

data class AgentReview(
    val title: String,
    val summary: String,
    val metrics: String,
    val memoryActions: List<AgentAction>,
    val diagnostics: List<AgentAction>,
    val nextOptions: List<AgentNextOption>,
    val touchedForeshadowingIds: List<String>,
    val fullBook: Boolean,
)

class NovelAgentEngine(
    private val gateway: AiGateway,
    private val chronologyGuard: ChronologyGuard = ChronologyGuard(),
) {
    suspend fun reviewChapter(snapshot: StorySnapshot, chapter: ChapterDraft): AgentReview {
        require(chapter.content.isNotBlank()) { "当前章节还没有正文，无法做 Agent 复盘" }
        val prompt = PromptBundle(
            system = systemPrompt(fullBook = false),
            user = buildChapterPrompt(snapshot, chapter),
        )
        return parse(gateway.generate(prompt), fullBook = false)
    }

    suspend fun auditStory(snapshot: StorySnapshot, chapters: List<ChapterDraft>): AgentReview {
        require(chapters.any { it.content.isNotBlank() }) { "整部作品还没有可巡检的正文" }
        val prompt = PromptBundle(
            system = systemPrompt(fullBook = true),
            user = buildAuditPrompt(snapshot, chapters),
        )
        return parse(gateway.generate(prompt), fullBook = true)
    }

    private fun systemPrompt(fullBook: Boolean): String = """
        你是“琅嬛”长篇小说创作 Agent，负责结构化复盘、事实抽取和一致性巡检。${if (fullBook) "本次是全书巡检。" else "本次只复盘刚完成的当前章节。"}
        你不是续写模型，不要输出小说正文。只依据提供的正文、章节合同、信息边界、场景时间锁与已知设定做判断，禁止把猜测当事实。
        输出必须严格符合 GeneratedChapter JSON：title、content、summary、stateChanges、touchedForeshadowingIds，不要 Markdown，不要额外解释。

        字段约定：
        - title：本次复盘标题。
        - content：150-500 字高信息密度结论，说明主线推进、时间连续性、信息边界、主要风险与最值得处理的问题。
        - summary：使用“节奏=... || 角色弧光=... || 主线推进=... || 时间线=... || 信息边界=... || 一致性=...”格式。
        - stateChanges：结构化动作/诊断。field 只能使用下列枚举之一：
          CHARACTER_NEW、CHARACTER_LOCATION、CHARACTER_EMOTION、CHARACTER_GOAL、RELATION、KNOWLEDGE_GAIN、TIMELINE、FORESHADOW_NEW、FORESHADOW_UPDATE、CONSISTENCY、OUTLINE_GAP、PACING、ARC、NEXT_OPTION。
        - touchedForeshadowingIds：只放已存在伏笔中本章明确触及的 id。

        stateChanges 编码规则：
        1. CHARACTER_NEW：subject=人物名；after=地点||情绪||当前目标||性格标签（顿号分隔）。只有正文明确出现且对后续有持续意义的人物才记录。
        2. CHARACTER_LOCATION / CHARACTER_EMOTION / CHARACTER_GOAL：subject=已有角色名；before=原状态；after=正文结束时的新状态；evidence=依据。
        3. RELATION：subject=关系发起人物；after=目标人物||关系说明。
        4. KNOWLEDGE_GAIN：subject=获得信息的人物名；before=写前认知；after=正文中明确新获知的秘密标题/事实；evidence=正文证据。只有正文真的让人物确认了该事实才记录，猜测、怀疑、读者旁白不能算人物已知。
        5. TIMELINE：subject=事件短标题；after 必须严格为“故事日序号||时段||距上一事件经过多久||NORMAL或FLASHBACK||地点||参与者（顿号）||事件摘要||后果（顿号）”。
           - 故事日序号只写整数，例如 3，不要写“第三天”。
           - NORMAL 事件的故事日不得比已有主时间线更早；没有明确跳时场景时不得凭空增加多天。
           - FLASHBACK 只记录真正切入过去叙事的事件，且不会推进当前主时间钟。
           - 每个有实质剧情推进的章节至少给 1 条、最多 4 条 TIMELINE，按正文发生顺序排列。
        6. FORESHADOW_NEW：subject=伏笔标题；after=细节||预期回收||建议回收起始章||建议回收结束章。只有明显需要未来兑现的信息才建立伏笔。
        7. FORESHADOW_UPDATE：subject=已有伏笔 id；after=PLANTED/DEVELOPING/RESOLVED/ABANDONED||本次变化说明。
        8. CONSISTENCY / OUTLINE_GAP / PACING / ARC：subject=问题标题；before=现状；after=建议；evidence=依据。这些只做诊断，不写入事实记忆。
        9. NEXT_OPTION：subject=下一章候选标题；after=唯一目标||主要冲突||章末转折。章节复盘时给 3 个差异明显但都服从总纲的候选；全书巡检时可以不给。

        信息边界优先服从 App 给出的【章节合同】与【信息边界账本】。正文若让明确 unknownTo 的人物获得了未授权答案，必须作为 CONSISTENCY 指出；不得因为正文已经写错就把错误认知写进长期记忆。
        时间线事实优先服从 App 给出的【时间轴锁】与【场景时间计划】。正文若出现与时间锁冲突的句子，要作为 CONSISTENCY 诊断指出，不能反过来用错误正文覆盖时间锁。
        事实抽取宁缺毋滥。若没有可靠的新事实，不要为了凑数量生成动作。
    """.trimIndent()

    private fun buildChapterPrompt(snapshot: StorySnapshot, chapter: ChapterDraft): String {
        val bible = snapshot.bible.joinToString("\n") { "[${it.category}] ${it.name}：${it.content}${if (it.locked) "（锁定）" else ""}" }
        val outline = snapshot.activeOutline.joinToString("\n") { "${it.level}:${it.title}｜目标=${it.objective}｜冲突=${it.conflict}｜转折=${it.turningPoint}" }
        val characters = snapshot.characters.joinToString("\n") {
            "${it.name}｜地点=${it.location}｜身体=${it.physicalState}｜情绪=${it.emotionalState}｜目标=${it.goal}｜已知=${it.knownSecrets.joinToString("、")}｜关系=${it.relationshipNotes.entries.joinToString("；") { e -> "${e.key}=${e.value}" }}"
        }
        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(40)
            .joinToString("\n") {
                val clock = if (it.storyDay > 0) "故事第${it.storyDay}天·${it.timeOfDay.ifBlank { it.storyTime }}｜距上次=${it.elapsedFromPrevious.ifBlank { "未记录" }}" else it.storyTime
                "第${it.chapter}章#${it.orderInChapter} $clock ${if (it.isFlashback) "[FLASHBACK]" else "[NORMAL]"} ${it.location}：${it.summary}"
            }
        val scenes = chapter.scenePlan.sortedBy { it.order }.joinToString("\n") {
            "场景${it.order}｜故事第${it.storyDay.takeIf { day -> day > 0 } ?: 0}天·${it.timeOfDay.ifBlank { "未锁定" }}｜距上一场=${it.elapsedFromPrevious.ifBlank { "未标注" }}｜${if (it.isFlashback) "FLASHBACK" else "NORMAL"}｜${it.location}｜${it.outcome}"
        }
        val chronology = chronologyGuard.promptText(snapshot, chapter.scenePlan)
        val contract = ChapterContractGuard.renderContract(snapshot, chapter)
        val knowledge = ChapterContractGuard.renderKnowledge(snapshot, chapter.chapterNumber)
        val foreshadows = snapshot.relevantForeshadowing.joinToString("\n") { "id=${it.id}｜${it.title}｜${it.status}｜${it.detail}｜预计${it.expectedChapterStart}-${it.expectedChapterEnd}章回收" }
        return """
            小说：${snapshot.novel.title}
            核心命题：${snapshot.novel.premise}
            主题：${snapshot.novel.theme}

            【小说圣经】
            $bible

            【当前大纲链】
            $outline

            【章节合同】
            $contract

            【信息边界账本】
            $knowledge

            【人物当前状态】
            $characters

            【时间轴锁】
            $chronology

            【已有时间线】
            ${timeline.ifBlank { "暂无；本章从故事第1天开始建立结构化主时间线。" }}

            【本章场景时间计划】
            ${scenes.ifBlank { "旧章节没有结构化场景时间；只能保守连续承接，禁止自行跨天。" }}

            【已有伏笔】
            $foreshadows

            【刚完成章节】
            第${chapter.chapterNumber}章 ${chapter.title}
            章纲目标：${chapter.objective}
            已有摘要：${chapter.summary}
            正文：
            ${chapter.content.take(18_000)}

            请完成章节复盘、结构化事实抽取、人物认知变化、时间连续性/设定/节奏/角色弧光诊断，并给出 3 个下一章候选方向。
            KNOWLEDGE_GAIN 必须同时满足：正文有明确证据、没有违反信息边界、人物真的从“不知道”变成“知道”。
            TIMELINE 的故事日和时段必须优先从场景时间计划读取，不能仅凭正文中的“后来、第二天、几个月后”等措辞自行改钟。
        """.trimIndent()
    }

    private fun buildAuditPrompt(snapshot: StorySnapshot, chapters: List<ChapterDraft>): String {
        val outline = (if (snapshot.outline.isEmpty()) snapshot.activeOutline else snapshot.outline)
            .sortedWith(compareBy({ it.level.ordinal }, { it.order }))
            .joinToString("\n") { "${it.level} ${it.order}. ${it.title}｜目标=${it.objective}｜冲突=${it.conflict}｜转折=${it.turningPoint}" }
        val bible = snapshot.bible.filter { it.locked }.joinToString("\n") { "[${it.category}] ${it.name}：${it.content}" }
        val characters = snapshot.characters.joinToString("\n") {
            "${it.name}｜地点=${it.location}｜情绪=${it.emotionalState}｜目标=${it.goal}｜已知=${it.knownSecrets.joinToString("、")}｜关系=${it.relationshipNotes.entries.joinToString("；") { e -> "${e.key}=${e.value}" }}"
        }
        val knowledge = ChapterContractGuard.renderKnowledge(snapshot, snapshot.novel.currentChapter)
        val timeline = snapshot.recentTimeline
            .sortedWith(compareBy({ it.chapter }, { it.orderInChapter }))
            .takeLast(160)
            .joinToString("\n") {
                val day = if (it.storyDay > 0) "故事第${it.storyDay}天·${it.timeOfDay}" else it.storyTime.ifBlank { "旧时间未结构化" }
                "第${it.chapter}章#${it.orderInChapter}｜$day｜${if (it.isFlashback) "FLASHBACK" else "NORMAL"}｜${it.location}｜${it.summary}"
            }
        val chapterDigest = chapters.sortedBy { it.chapterNumber }.takeLast(60).joinToString("\n\n") { chapter ->
            val bodyEvidence = if (chapter.summary.isNotBlank()) chapter.summary else {
                val text = chapter.content.trim()
                if (text.length <= 1_400) text else text.take(850) + " …… " + text.takeLast(450)
            }
            val sceneClock = chapter.scenePlan.sortedBy { it.order }.joinToString("；") {
                "S${it.order}=第${it.storyDay}天·${it.timeOfDay}${if (it.isFlashback) "(闪回)" else ""}"
            }
            "第${chapter.chapterNumber}章 ${chapter.title}｜目标=${chapter.objective}｜时间计划=$sceneClock\n$bodyEvidence"
        }
        return """
            小说：${snapshot.novel.title}
            核心命题：${snapshot.novel.premise}
            主题：${snapshot.novel.theme}
            当前总字数：${snapshot.novel.currentWords}

            【锁定设定】
            $bible

            【完整大纲】
            $outline

            【人物当前状态】
            $characters

            【信息边界账本】
            $knowledge

            【结构化时间线】
            ${timeline.ifBlank { "尚无结构化时间线。" }}

            【章节摘要/正文证据，最多最近 60 章】
            $chapterDigest

            请从全书尺度巡检：信息越权、秘密提前揭底、人物认知倒退、时间倒退、未经计划跨天/跨月/跨年、同一人物同一时间出现在冲突地点、闪回污染主时间钟、设定矛盾、人物弧光断裂、目标重复、节奏拖沓/跳跃、伏笔过期未回收、章纲与总纲脱节、连续章节缺乏状态变化等问题。
            还必须专门比较章节窗口，主动寻找以下“写长以后才会出现”的退化：
            - 结构疲劳：连续多章重复同一种调查/遭遇/验证结构，阶段目标没有不可逆变化；
            - 套路重复：开场方式、信息获得方式、冲突节拍、章末钩子反复换皮；
            - 人物声线趋同：主要角色的句长、用词、反问、停顿、回避方式越来越像同一个人；
            - 悬念密度失衡：连续多章没有新问题/旧线索升级，或连续密集揭底导致信息差耗尽；
            - 支线失踪：人物关系、承诺、支线在十几到几十章内完全没有触点；
            - 文风漂移：近期叙述声音明显偏离前期稳定基线或作者编辑画像；
            - 连续低变化：多章结束后人物、关系、目标、风险都几乎回到原位。
            每个诊断必须在 evidence 中写出尽可能明确的章节区间或可核对文本证据；不要用“可能有点拖”这类空话。
            全书巡检时以 CONSISTENCY、OUTLINE_GAP、PACING、ARC 为主；只有证据非常明确时才提出事实记忆动作。
        """.trimIndent()
    }

    private fun parse(output: com.xiguli.langhuan.domain.GeneratedChapter, fullBook: Boolean): AgentReview {
        val actions = output.stateChanges.map { it.toAction() }
        val next = actions.filter { it.kind == AgentActionKind.NEXT_OPTION }.mapNotNull { action ->
            val parts = action.after.split("||", limit = 3).map { it.trim() }
            if (parts.isEmpty()) null else AgentNextOption(
                title = action.subject.ifBlank { "下一章候选" },
                objective = parts.getOrNull(0).orEmpty().ifBlank { "承接当前章节并推进主线" },
                conflict = parts.getOrNull(1).orEmpty().ifBlank { "目标遭遇新的具体阻碍" },
                turningPoint = parts.getOrNull(2).orEmpty().ifBlank { "章末形成新的信息、代价或选择" },
            )
        }.take(3)
        val memoryKinds = setOf(
            AgentActionKind.CHARACTER_NEW,
            AgentActionKind.CHARACTER_LOCATION,
            AgentActionKind.CHARACTER_EMOTION,
            AgentActionKind.CHARACTER_GOAL,
            AgentActionKind.RELATION,
            AgentActionKind.KNOWLEDGE_GAIN,
            AgentActionKind.TIMELINE,
            AgentActionKind.FORESHADOW_NEW,
            AgentActionKind.FORESHADOW_UPDATE,
        )
        return AgentReview(
            title = output.title.ifBlank { if (fullBook) "全书巡检" else "章节复盘" },
            summary = output.content.trim(),
            metrics = output.summary.trim(),
            memoryActions = actions.filter { it.kind in memoryKinds },
            diagnostics = actions.filter { it.kind !in memoryKinds && it.kind != AgentActionKind.NEXT_OPTION && it.kind != AgentActionKind.UNKNOWN },
            nextOptions = next,
            touchedForeshadowingIds = output.touchedForeshadowingIds.distinct(),
            fullBook = fullBook,
        )
    }

    private fun StateChange.toAction(): AgentAction {
        val kind = runCatching { AgentActionKind.valueOf(field.trim().uppercase()) }.getOrDefault(AgentActionKind.UNKNOWN)
        return AgentAction(kind, subject.trim(), before.trim(), after.trim(), evidence.trim())
    }
}
