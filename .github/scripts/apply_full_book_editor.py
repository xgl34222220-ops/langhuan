from pathlib import Path


def replace_once(path: str, old: str, new: str):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"pattern not found: {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# 1) Persistent models.
path = "app/src/main/java/com/xiguli/langhuan/domain/LongFormModels.kt"
marker = "@Serializable\ndata class LongFormState(\n"
models = '''@Serializable
enum class BookEditorIssueKind {
    STRUCTURAL_FATIGUE,
    PATTERN_REPETITION,
    CHARACTER_VOICE_CONVERGENCE,
    SUSPENSE_DENSITY,
    SUBPLOT_ABSENCE,
    STYLE_DRIFT,
    LOW_CHANGE_STREAK,
}

@Serializable
enum class BookEditorSeverity { INFO, WATCH, HIGH }

/** Whole-book editorial diagnosis. It is guidance, never Canon. */
@Serializable
data class BookEditorIssue(
    val id: String,
    val kind: BookEditorIssueKind,
    val severity: BookEditorSeverity = BookEditorSeverity.INFO,
    val title: String,
    val chapterStart: Int = 0,
    val chapterEnd: Int = 0,
    val evidence: String = "",
    val diagnosis: String = "",
    val minimalRepair: String = "",
    val source: String = "本地巡检",
)

/** Persistent full-book editor state. Old projects decode to this empty report. */
@Serializable
data class FullBookEditorReport(
    val lastAuditChapter: Int = 0,
    val scannedChapterStart: Int = 0,
    val scannedChapterEnd: Int = 0,
    val scannedChapterCount: Int = 0,
    val score: Int = 100,
    val level: LongFormHealthLevel = LongFormHealthLevel.HEALTHY,
    val structureScore: Int = 100,
    val varietyScore: Int = 100,
    val characterVoiceScore: Int = 100,
    val suspenseScore: Int = 100,
    val subplotScore: Int = 100,
    val styleScore: Int = 100,
    val issues: List<BookEditorIssue> = emptyList(),
    val aiSummary: String = "",
    val updatedAt: Long = 0L,
)

'''
replace_once(path, marker, models + marker)
replace_once(
    path,
    "    val authorProfile: AuthorPreferenceProfile = AuthorPreferenceProfile(),\n    val lastSettledChapter: Int = 0,\n",
    "    val authorProfile: AuthorPreferenceProfile = AuthorPreferenceProfile(),\n    /** Persistent whole-book editorial health; diagnostics never mutate Canon by themselves. */\n    val editorReport: FullBookEditorReport = FullBookEditorReport(),\n    val lastSettledChapter: Int = 0,\n",
)

# 2) Feed stable editorial guidance into C/style layer.
path = "app/src/main/java/com/xiguli/langhuan/engine/ContextBuilder.kt"
replace_once(
    path,
    "        val learnedStyle = AuthorPreferenceEngine.promptText(snapshot)\n        val styleItems = buildList {\n",
    "        val learnedStyle = AuthorPreferenceEngine.promptText(snapshot)\n        val fullBookGuidance = FullBookEditorEngine.promptText(snapshot)\n        val styleItems = buildList {\n",
)
replace_once(
    path,
    "            if (learnedStyle.isNotBlank()) add(learnedStyle)\n        }\n        trace += ContextTraceEntry(ContextLayer.C_STYLE, \"作品文风/作者编辑画像\", \"只控制叙事声音；学习偏好不得覆盖 S/A 层事实\")\n",
    "            if (learnedStyle.isNotBlank()) add(learnedStyle)\n            if (fullBookGuidance.isNotBlank()) add(fullBookGuidance)\n        }\n        trace += ContextTraceEntry(ContextLayer.C_STYLE, \"作品文风/作者编辑画像/全书主编\", \"只控制长期写作模式；任何提醒不得覆盖 S/A 层事实\")\n",
)

# 3) Make autonomous planning absorb whole-book problems without rewriting Canon.
path = "app/src/main/java/com/xiguli/langhuan/engine/AutonomousStoryPlanner.kt"
replace_once(
    path,
    "import com.xiguli.langhuan.domain.ForeshadowStatus\n",
    "import com.xiguli.langhuan.domain.ForeshadowStatus\nimport com.xiguli.langhuan.domain.LongFormHealthLevel\n",
)
replace_once(
    path,
    "        val executionContext = AutonomousExecutionEngine.planningContext(snapshot, startChapter)\n        val prompt = PromptBundle(\n",
    "        val executionContext = AutonomousExecutionEngine.planningContext(snapshot, startChapter)\n        val fullBookEditorContext = FullBookEditorEngine.promptText(snapshot)\n        val prompt = PromptBundle(\n",
)
replace_once(
    path,
    "                【长篇健康提醒】\n                ${snapshot.longForm.health.warnings.joinToString(\"\\n\") { \"- $it\" }.ifBlank { \"- 当前没有本地健康警报。\" }}\n\n                $executionContext\n",
    "                【长篇健康提醒】\n                ${snapshot.longForm.health.warnings.joinToString(\"\\n\") { \"- $it\" }.ifBlank { \"- 当前没有本地健康警报。\" }}\n\n                【全书主编长期模式提醒｜只用于规划纠偏】\n                ${fullBookEditorContext.ifBlank { \"当前没有达到阈值的全书级模式问题。\" }}\n\n                $executionContext\n",
)
replace_once(
    path,
    "            return remaining < 3 ||\n                plan.driftSignals.any { it.severity == DriftSeverity.HIGH } ||\n                (plan.canonDigest.isNotBlank() && plan.canonDigest != canonDigest(snapshot))\n",
    "            return remaining < 3 ||\n                plan.driftSignals.any { it.severity == DriftSeverity.HIGH } ||\n                snapshot.longForm.editorReport.level == LongFormHealthLevel.RISK ||\n                (plan.canonDigest.isNotBlank() && plan.canonDigest != canonDigest(snapshot))\n",
)

# 4) Studio integration: automatic local audit + persistent deep audit.
path = "app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt"
replace_once(
    path,
    "import com.xiguli.langhuan.engine.GenerationPipeline\n",
    "import com.xiguli.langhuan.engine.GenerationPipeline\nimport com.xiguli.langhuan.engine.FullBookEditorEngine\n",
)
old_audit = '''    fun runFullBookAudit() {
        val current = _state.value
        if (busy(current)) return
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "全书巡检需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isAuditing = true, error = null, agentReview = null) }
            runCatching {
                val drafts = projects.chapterDrafts(current.snapshot.novel.id)
                NovelAgentEngine(gateway).auditStory(current.snapshot, drafts)
            }.onSuccess { review ->
                _state.update { it.copy(isAuditing = false, agentReview = review, message = "全书一致性巡检完成") }
            }.onFailure { error ->
                _state.update { it.copy(isAuditing = false, error = error.message ?: "全书巡检失败") }
            }
        }
    }
'''
new_audit = '''    fun runFullBookAudit() {
        val current = _state.value
        if (busy(current)) return
        viewModelScope.launch {
            val gateway = configuredGateway()
            if (gateway == null) {
                _state.update { it.copy(error = "全书主编深度巡检需要先配置 AI 服务") }
                return@launch
            }
            _state.update { it.copy(isAuditing = true, error = null, agentReview = null) }
            runCatching {
                val drafts = projects.chapterDrafts(current.snapshot.novel.id)
                val editor = FullBookEditorEngine()
                val local = editor.localAudit(current.snapshot, drafts)
                val review = NovelAgentEngine(gateway).auditStory(current.snapshot, drafts)
                val report = editor.mergeAgentReview(local, review)
                val persisted = projects.saveStructure(editor.apply(current.snapshot, report), current.draft)
                Triple(persisted, review, report)
            }.onSuccess { (persisted, review, report) ->
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        isAuditing = false,
                        agentReview = review,
                        message = "全书主编巡检完成：${report.score}分 · ${report.level}",
                    )
                }
                refreshWorkspace()
            }.onFailure { error ->
                _state.update { it.copy(isAuditing = false, error = error.message ?: "全书主编巡检失败") }
            }
        }
    }
'''
replace_once(path, old_audit, new_audit)
replace_once(
    path,
    "                    refreshWorkspace()\n                    val gateway = configuredGateway()\n                    if (gateway != null) {\n                        var working = persisted\n",
    '''                    refreshWorkspace()
                    var working = persisted
                    if (FullBookEditorEngine.shouldAudit(working.snapshot, working.draft.chapterNumber)) {
                        runCatching {
                            val drafts = projects.chapterDrafts(working.snapshot.novel.id)
                            val editor = FullBookEditorEngine()
                            val report = editor.localAudit(working.snapshot, drafts)
                            projects.saveStructure(editor.apply(working.snapshot, report), working.draft)
                        }.onSuccess { audited ->
                            working = audited
                            _state.update { state ->
                                state.copy(
                                    snapshot = audited.snapshot,
                                    draft = audited.draft,
                                    message = "正文已保存；全书主编本地巡检 ${audited.snapshot.longForm.editorReport.score} 分",
                                )
                            }
                        }
                    }
                    val gateway = configuredGateway()
                    if (gateway != null) {
''',
)

# 5) Agent page exposes persistent whole-book editor state.
path = "app/src/main/java/com/xiguli/langhuan/ui/AgentPage.kt"
replace_once(
    path,
    "                    Text(\"自动复盘章节 · 全书巡检 · 未来滚动自治规划 · 结构化长期记忆\", color = LocalMiuixTokens.current.textSecondary)\n",
    "                    Text(\"自动复盘章节 · 全书主编 · 未来滚动自治规划 · 结构化长期记忆\", color = LocalMiuixTokens.current.textSecondary)\n",
)
replace_once(
    path,
    "                    Text(if (state.isAuditing) \"正在巡检整部作品…\" else \"全书一致性巡检\")\n",
    "                    Text(if (state.isAuditing) \"全书主编正在深度巡检…\" else \"全书主编深度巡检\")\n",
)
replace_once(
    path,
    "        item { LongFormAgentPanel(state.snapshot) }\n        item { AutonomousPlanPanel(state, vm) }\n",
    "        item { LongFormAgentPanel(state.snapshot) }\n        item { FullBookEditorPanel(state.snapshot) }\n        item { AutonomousPlanPanel(state, vm) }\n",
)

# 6) Tell semantic full-book audit to explicitly inspect long-form quality failure modes.
path = "app/src/main/java/com/xiguli/langhuan/engine/NovelAgentEngine.kt"
replace_once(
    path,
    "            请从全书尺度巡检：信息越权、秘密提前揭底、人物认知倒退、时间倒退、未经计划跨天/跨月/跨年、同一人物同一时间出现在冲突地点、闪回污染主时间钟、设定矛盾、人物弧光断裂、目标重复、节奏拖沓/跳跃、伏笔过期未回收、章纲与总纲脱节、连续章节缺乏状态变化等问题。\n            全书巡检时以 CONSISTENCY、OUTLINE_GAP、PACING、ARC 为主；只有证据非常明确时才提出事实记忆动作。\n",
    '''            请从全书尺度巡检：信息越权、秘密提前揭底、人物认知倒退、时间倒退、未经计划跨天/跨月/跨年、同一人物同一时间出现在冲突地点、闪回污染主时间钟、设定矛盾、人物弧光断裂、目标重复、节奏拖沓/跳跃、伏笔过期未回收、章纲与总纲脱节、连续章节缺乏状态变化等问题。
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
''',
)

# 7) Version bump.
path = "app/build.gradle.kts"
replace_once(path, '        versionCode = 58\n        versionName = "0.26.0-alpha01"\n', '        versionCode = 59\n        versionName = "0.26.1-alpha01"\n')

print("full-book editor wiring applied")
