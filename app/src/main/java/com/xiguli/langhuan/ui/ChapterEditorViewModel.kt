package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.ChapterEditorStore
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoredChapterVersion
import com.xiguli.langhuan.domain.AuthorLearningSource
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.AuthorPreferenceEngine
import com.xiguli.langhuan.engine.ChapterDependencyAnalyzer
import com.xiguli.langhuan.engine.ChapterDependencyReport
import com.xiguli.langhuan.engine.ChronologyGuard
import com.xiguli.langhuan.engine.ChronologyRepairAnalyzer
import com.xiguli.langhuan.engine.ChronologyRepairReport
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewriteProposal(
    val start: Int,
    val end: Int,
    val original: String,
    val replacement: String,
    val instruction: String,
)

data class ChronologyRepairProposal(
    val original: String,
    val repaired: String,
    val diagnosis: String,
)

data class VersionComparison(
    val version: StoredChapterVersion,
    val oldChanged: String,
    val currentChanged: String,
    val prefixChars: Int,
    val suffixChars: Int,
)

data class ChapterEditorUiState(
    val novelId: String = "",
    val snapshot: StorySnapshot? = null,
    val draft: ChapterDraft? = null,
    val chapters: List<ChapterDraft> = emptyList(),
    val versions: List<StoredChapterVersion> = emptyList(),
    val dirty: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isRewriting: Boolean = false,
    val isAnalyzingDependencies: Boolean = false,
    val isPlanningRepair: Boolean = false,
    val isAnalyzingChronology: Boolean = false,
    val isRepairingChronology: Boolean = false,
    val lastSavedAt: Long? = null,
    val rewriteProposal: RewriteProposal? = null,
    val chronologyProposal: ChronologyRepairProposal? = null,
    val comparison: VersionComparison? = null,
    val dependencyReport: ChapterDependencyReport? = null,
    val chronologyReport: ChronologyRepairReport? = null,
    val repairPlan: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val ready: Boolean get() = snapshot != null && draft != null
    val busy: Boolean get() = isLoading || isSaving || isRewriting || isPlanningRepair || isRepairingChronology
}

class ChapterEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ChapterEditorStore(application)
    private val repository = PersistentStoryRepository(application)
    private val _state = MutableStateFlow(ChapterEditorUiState())
    val state: StateFlow<ChapterEditorUiState> = _state.asStateFlow()
    private var autosaveJob: Job? = null
    private var lastPersistedContent: String = ""
    private var pendingLearningSource: AuthorLearningSource = AuthorLearningSource.MANUAL_EDIT
    private var pendingLearningInstruction: String = ""

    fun load(novelId: String, chapterNumber: Int? = null) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.ready && (chapterNumber == null || current.draft?.chapterNumber == chapterNumber)) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.value = ChapterEditorUiState(novelId = novelId, isLoading = true)
            runCatching {
                val loaded = store.load(novelId, chapterNumber)
                Triple(loaded, store.chapters(novelId), store.versions(novelId, loaded.draft.chapterNumber))
            }.onSuccess { (loaded, chapters, versions) ->
                _state.update {
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
                _state.update { it.copy(isLoading = false, error = error.message ?: "加载正文失败") }
            }
        }
    }

    fun updateTitle(title: String) {
        val draft = _state.value.draft ?: return
        if (draft.title == title) return
        val updated = draft.copy(title = title)
        _state.update {
            it.copy(
                draft = updated,
                chapters = it.chapters.replaceDraft(updated),
                dirty = true,
                dependencyReport = null,
                chronologyReport = null,
                chronologyProposal = null,
                repairPlan = null,
            )
        }
        scheduleAutosave()
    }

    fun updateContent(content: String) {
        val draft = _state.value.draft ?: return
        if (draft.content == content) return
        val updated = draft.copy(content = content)
        _state.update {
            it.copy(
                draft = updated,
                chapters = it.chapters.replaceDraft(updated),
                dirty = true,
                rewriteProposal = null,
                chronologyProposal = null,
                comparison = null,
                dependencyReport = null,
                chronologyReport = null,
                repairPlan = null,
            )
        }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1_100)
            persist(createVersion = false, announce = false)
        }
    }

    fun saveCheckpoint() {
        autosaveJob?.cancel()
        viewModelScope.launch { persist(createVersion = true, announce = true) }
    }

    private suspend fun persist(createVersion: Boolean, announce: Boolean): Boolean {
        val current = _state.value
        val snapshot = current.snapshot ?: return false
        val draft = current.draft ?: return false
        if (current.isSaving) return false
        if (!current.dirty && !createVersion) return true
        _state.update { it.copy(isSaving = true, error = null) }
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
            onFailure = { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "保存正文失败") }
                false
            },
        )
    }

    fun openChapter(chapterNumber: Int) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (draft.chapterNumber == chapterNumber || current.busy) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = current.dirty, error = null) }
            runCatching {
                if (current.dirty) store.autosave(snapshot, draft)
                val loaded = store.load(current.novelId, chapterNumber)
                Triple(loaded, store.chapters(current.novelId), store.versions(current.novelId, chapterNumber))
            }.onSuccess { (loaded, chapters, versions) ->
                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        chapters = chapters.replaceDraft(loaded.draft),
                        versions = versions,
                        dirty = false,
                        isSaving = false,
                        rewriteProposal = null,
                        chronologyProposal = null,
                        comparison = null,
                        dependencyReport = null,
                        chronologyReport = null,
                        repairPlan = null,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
                lastPersistedContent = loaded.draft.content
                pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                pendingLearningInstruction = ""
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "切换章节失败") }
            }
        }
    }

    fun analyzeChronology() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.isAnalyzingChronology) return
        _state.update { it.copy(isAnalyzingChronology = true, error = null) }
        runCatching { ChronologyRepairAnalyzer.analyze(snapshot, draft.content) }
            .onSuccess { report ->
                _state.update {
                    it.copy(
                        isAnalyzingChronology = false,
                        chronologyReport = report,
                        chronologyProposal = null,
                        message = "时间体检完成：${report.overallRisk.label}风险 · ${report.anchors.size} 个时间锚点 · ${report.findings.size} 个问题",
                    )
                }
            }
            .onFailure { error ->
                _state.update { it.copy(isAnalyzingChronology = false, error = error.message ?: "时间线体检失败") }
            }
    }

    fun generateChronologyRepair() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy || draft.content.isBlank()) return
        val report = current.chronologyReport ?: ChronologyRepairAnalyzer.analyze(snapshot, draft.content)
        viewModelScope.launch {
            _state.update { it.copy(isRepairingChronology = true, chronologyReport = report, chronologyProposal = null, error = null) }
            runCatching {
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                    ?: error("请先在设置里添加 AI 服务")
                val config = repository.providerConfig(provider.id) ?: error("当前 AI 服务不可用")
                val findings = report.findings.joinToString("\n") {
                    "- ${it.risk.label}风险｜${it.code}｜第${it.paragraph}段｜${it.title}｜${it.detail}｜建议=${it.repair}"
                }.ifBlank { "- 本地规则没有发现确定性冲突；仍请检查时间层切换是否清楚。" }
                val anchors = report.anchors.take(60).joinToString("\n") {
                    "- 第${it.paragraph}段｜${it.kind}｜${it.phrase}${it.relativeDay?.let { d -> "｜相对当前=$d 天" }.orEmpty()}"
                }
                val timeline = snapshot.recentTimeline.sortedWith(compareBy({ it.chapter }, { it.orderInChapter })).takeLast(50).joinToString("\n") {
                    "- 第${it.chapter}章｜故事第${it.storyDay.takeIf { day -> day > 0 } ?: 0}天｜${it.timeOfDay.ifBlank { it.storyTime }}｜${it.location}｜${it.summary}｜${if (it.isFlashback) "闪回" else "主线"}"
                }
                val clock = ChronologyGuard().promptText(snapshot, draft.scenePlan)
                val output = UniversalAiGateway(config).generate(
                    PromptBundle(
                        system = """
                            你是“琅嬛”的旧稿时间线修复编辑。你的任务不是重写小说，而是修复时间层级、场景归属和必要的过渡句。
                            必须输出 GeneratedChapter JSON：title=chronology-repair；content=修复后的完整章节正文；summary=80-220 字说明你修了哪些时间问题；stateChanges=[]；touchedForeshadowingIds=[]。

                            硬规则：
                            1. 最大限度保留原文。除时间桥、时间表达、场景归属提示和为消除硬矛盾所必需的极少句子外，不改剧情、人物、线索、道具、地点、对白含义和悬疑信息。
                            2. 不得为了“顺”而删掉超自然时间异常。梦境时间冻结、监控时间错位等若明显是剧情线索，要保留并写得更清楚，而不是修成正常时间。
                            3. 背景资料中的“十七年前/三年前”等不等于叙事镜头真的跳到过去；只有镜头进入过去场景时才算闪回。
                            4. 遇到“某人已失踪三天，却又像正在参加三天前的聚会”这类冲突，必须明确选择一个合法解释：把聚会改成调查历史聚会资料/访问当晚参与者，或明确整段是三天前闪回。禁止两种时间同时成立。
                            5. 如果证据不足，不编造新的日期、证人、监控或剧情事实；使用中性的时间桥解决歧义。
                            6. 保留原章节叙事风格、悬疑节奏和段落结构，不做无关润色。
                        """.trimIndent(),
                        user = """
                            小说：${snapshot.novel.title}
                            当前章节：第${draft.chapterNumber}章 ${draft.title}

                            【现有主时间钟】
                            $clock

                            【已保存长期时间线】
                            ${timeline.ifBlank { "暂无可靠结构化时间线。" }}

                            【本地扫描时间锚点】
                            ${anchors.ifBlank { "未识别到明确锚点。" }}

                            【本地确定/保守发现的问题】
                            $findings

                            【需要修复的完整正文】
                            ${draft.content.take(32_000)}

                            请只修时间与场景归属问题。若正文超过输入上限导致末尾缺失，禁止返回残缺章节，直接在 summary 说明无法安全整章修复并让 content 原样返回。
                        """.trimIndent(),
                    )
                )
                val repaired = output.content.trim().ifBlank { error("AI 没有返回修复正文") }
                require(repaired.length >= (draft.content.length * 0.65).toInt()) { "AI 修复稿异常过短，已阻止覆盖原文" }
                require(repaired.length <= (draft.content.length * 1.45).toInt() + 500) { "AI 修复稿异常膨胀，已阻止覆盖原文" }
                ChronologyRepairProposal(
                    original = draft.content,
                    repaired = repaired,
                    diagnosis = output.summary.trim().ifBlank { "已生成时间线修复预览，请逐段核对后再应用。" },
                )
            }.onSuccess { proposal ->
                _state.update { it.copy(isRepairingChronology = false, chronologyProposal = proposal) }
            }.onFailure { error ->
                _state.update { it.copy(isRepairingChronology = false, error = error.message ?: "AI 时间线修复失败") }
            }
        }
    }

    fun applyChronologyRepair() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        val proposal = current.chronologyProposal ?: return
        if (current.busy || proposal.original != draft.content) {
            if (proposal.original != draft.content) _state.update { it.copy(chronologyProposal = null, error = "正文已经变化，请重新扫描后再应用时间修复") }
            return
        }
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val checkpoint = store.checkpoint(snapshot, draft)
                val repairedDraft = checkpoint.draft.copy(content = proposal.repaired)
                val persisted = store.autosave(checkpoint.snapshot, repairedDraft)
                Triple(persisted, store.versions(draft.novelId, draft.chapterNumber), ChronologyRepairAnalyzer.analyze(persisted.snapshot, repairedDraft.content))
            }.onSuccess { (persisted, versions, report) ->
                lastPersistedContent = persisted.draft.content
                pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                pendingLearningInstruction = ""
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        chapters = it.chapters.replaceDraft(persisted.draft),
                        versions = versions,
                        dirty = false,
                        isSaving = false,
                        chronologyProposal = null,
                        chronologyReport = report,
                        dependencyReport = null,
                        repairPlan = null,
                        lastSavedAt = System.currentTimeMillis(),
                        message = "时间修复已应用；原稿已先保存为历史版本。当前仍有 ${report.findings.size} 个时间问题待复核",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "应用时间修复失败") }
            }
        }
    }

    fun dismissChronologyRepair() = _state.update { it.copy(chronologyProposal = null) }

    fun analyzeDependencies() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.isAnalyzingDependencies) return
        val liveChapters = current.chapters.replaceDraft(draft)
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzingDependencies = true, error = null) }
            runCatching { ChapterDependencyAnalyzer.analyze(snapshot, liveChapters, draft.chapterNumber) }
                .onSuccess { report ->
                    _state.update {
                        it.copy(
                            isAnalyzingDependencies = false,
                            dependencyReport = report,
                            repairPlan = null,
                            message = "依赖检查完成：${report.overallRisk.label}风险 · ${report.all.size} 项影响",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isAnalyzingDependencies = false, error = error.message ?: "依赖检查失败") }
                }
        }
    }

    fun generateRepairPlan() {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        val report = current.dependencyReport ?: run {
            analyzeDependencies()
            return
        }
        if (current.isPlanningRepair || current.isRewriting || current.isSaving) return
        val liveChapters = current.chapters.replaceDraft(draft)
        viewModelScope.launch {
            _state.update { it.copy(isPlanningRepair = true, error = null) }
            runCatching {
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                    ?: error("请先在设置里添加 AI 服务")
                val config = repository.providerConfig(provider.id) ?: error("当前 AI 服务不可用")
                val impacts = report.all.take(30).joinToString("\n") {
                    "- ${it.risk.label}风险｜${it.kind.label}｜${it.title}｜${it.detail}"
                }
                val later = liveChapters.filter { it.chapterNumber > draft.chapterNumber }.take(20).joinToString("\n") {
                    "第${it.chapterNumber}章 ${it.title}｜目标=${it.objective}｜摘要=${it.summary.take(240)}"
                }
                UniversalAiGateway(config).generate(
                    PromptBundle(
                        system = """
                            你是长篇小说连续性修复规划器，不写小说正文。根据确定的依赖清单，给出删除/回滚/大改当前章节后如何修复后续剧情的执行计划。
                            必须输出 GeneratedChapter JSON；title=依赖修复计划；content=纯文本计划；summary=一句风险结论；stateChanges=[]；touchedForeshadowingIds=[]。
                            不得宣称已修改任何事实，不得擅自删除锁定设定。按“先处理结构化事实→再处理章纲→再处理正文→最后全书一致性检查”的顺序给出步骤。
                        """.trimIndent(),
                        user = """
                            小说：${snapshot.novel.title}
                            当前章节：第${draft.chapterNumber}章 ${draft.title}
                            当前风险：${report.overallRisk.label}
                            建议：${report.recommendation}

                            【本地确定/推断的依赖】
                            $impacts

                            【后续章节摘要】
                            $later

                            请生成可执行修复计划，并明确哪些项必须人工确认，哪些项可以交给 AI 重写。
                        """.trimIndent(),
                    )
                ).content.trim().ifBlank { error("AI 返回了空修复计划") }
            }.onSuccess { plan ->
                _state.update { it.copy(isPlanningRepair = false, repairPlan = plan) }
            }.onFailure { error ->
                _state.update { it.copy(isPlanningRepair = false, error = error.message ?: "生成修复计划失败") }
            }
        }
    }

    fun rewriteSelection(start: Int, end: Int, instruction: String) {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.busy) return
        val safeStart = start.coerceIn(0, draft.content.length)
        val safeEnd = end.coerceIn(safeStart, draft.content.length)
        val selected = draft.content.substring(safeStart, safeEnd)
        if (selected.isBlank()) {
            _state.update { it.copy(error = "请先选中要重写的正文") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRewriting = true, error = null, rewriteProposal = null) }
            runCatching {
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                    ?: error("请先在设置里添加 AI 服务")
                val config = repository.providerConfig(provider.id) ?: error("当前 AI 服务不可用")
                val gateway = UniversalAiGateway(config)
                val before = draft.content.substring(0, safeStart).takeLast(900)
                val after = draft.content.substring(safeEnd).take(900)
                val request = instruction.trim().ifBlank { "在不改变事实和剧情含义的前提下润色，使表达更自然、更有画面感。" }
                val chronology = current.snapshot?.let { ChronologyGuard().promptText(it, draft.scenePlan) }.orEmpty()
                val learnedStyle = current.snapshot?.let { AuthorPreferenceEngine.promptText(it) }.orEmpty()
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是中文长篇小说精修编辑。只改用户选中的片段，绝不能擅自续写选区外剧情，不能改变人物身份、时间线、地点、因果、伏笔事实。
                            时间轴锁与锁定设定同级；即使用户只要求润色，也不得改变故事日、时段、事件先后、主线/闪回归属。
                            必须输出 GeneratedChapter JSON：title 固定 rewrite；content 只放替换后的片段正文；summary 留空；stateChanges=[]；touchedForeshadowingIds=[]。
                            不要在 content 里解释修改原因，不要加 Markdown 代码块。
                        """.trimIndent(),
                        user = """
                            小说：${current.snapshot?.novel?.title.orEmpty()}
                            第${draft.chapterNumber}章：${draft.title}
                            修改要求：$request

                            【时间轴锁】
                            $chronology

                            【已学习作者偏好｜只控制表达】
                            ${learnedStyle.ifBlank { "暂无稳定学习规则。" }}

                            选区前文：
                            $before

                            ===== 只重写下面选区 =====
                            $selected
                            ===== 选区结束 =====

                            选区后文：
                            $after
                        """.trimIndent(),
                    )
                )
                RewriteProposal(
                    start = safeStart,
                    end = safeEnd,
                    original = selected,
                    replacement = output.content.trim().ifBlank { error("AI 返回了空内容") },
                    instruction = request,
                )
            }.onSuccess { proposal ->
                _state.update { it.copy(isRewriting = false, rewriteProposal = proposal) }
            }.onFailure { error ->
                _state.update { it.copy(isRewriting = false, error = error.message ?: "局部重写失败") }
            }
        }
    }

    fun applyRewrite() {
        val proposal = _state.value.rewriteProposal ?: return
        val draft = _state.value.draft ?: return
        if (proposal.end > draft.content.length || draft.content.substring(proposal.start, proposal.end) != proposal.original) {
            _state.update { it.copy(rewriteProposal = null, error = "正文已发生变化，请重新选择片段后再重写") }
            return
        }
        val updatedContent = draft.content.substring(0, proposal.start) + proposal.replacement + draft.content.substring(proposal.end)
        val updated = draft.copy(content = updatedContent)
        pendingLearningSource = AuthorLearningSource.AI_REWRITE_ACCEPTED
        pendingLearningInstruction = proposal.instruction
        _state.update {
            it.copy(
                draft = updated,
                chapters = it.chapters.replaceDraft(updated),
                dirty = true,
                rewriteProposal = null,
                chronologyProposal = null,
                chronologyReport = null,
                dependencyReport = null,
                repairPlan = null,
                message = "已应用 AI 局部重写，正在自动保存",
            )
        }
        scheduleAutosave()
    }

    fun dismissRewrite() = _state.update { it.copy(rewriteProposal = null) }

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
        val current = _state.value.draft?.content ?: return
        val old = version.content
        var prefix = 0
        val maxPrefix = minOf(old.length, current.length)
        while (prefix < maxPrefix && old[prefix] == current[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(old.length - prefix, current.length - prefix)
        while (suffix < maxSuffix && old[old.length - 1 - suffix] == current[current.length - 1 - suffix]) suffix++
        val oldEnd = (old.length - suffix).coerceAtLeast(prefix)
        val currentEnd = (current.length - suffix).coerceAtLeast(prefix)
        _state.update {
            it.copy(
                comparison = VersionComparison(
                    version = version,
                    oldChanged = old.substring(prefix, oldEnd),
                    currentChanged = current.substring(prefix, currentEnd),
                    prefixChars = prefix,
                    suffixChars = suffix,
                )
            )
        }
    }

    fun dismissComparison() = _state.update { it.copy(comparison = null) }

    fun restore(version: StoredChapterVersion) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, comparison = null) }
            runCatching { store.restore(snapshot, draft, version) }
                .onSuccess { persisted ->
                    lastPersistedContent = persisted.draft.content
                    pendingLearningSource = AuthorLearningSource.MANUAL_EDIT
                    pendingLearningInstruction = ""
                    val chapters = store.chapters(persisted.draft.novelId)
                    val versions = store.versions(persisted.draft.novelId, persisted.draft.chapterNumber)
                    val liveChapters = chapters.replaceDraft(persisted.draft)
                    val report = ChapterDependencyAnalyzer.analyze(persisted.snapshot, liveChapters, persisted.draft.chapterNumber)
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            chapters = liveChapters,
                            versions = versions,
                            dirty = false,
                            isSaving = false,
                            rewriteProposal = null,
                            chronologyProposal = null,
                            chronologyReport = null,
                            dependencyReport = report,
                            repairPlan = null,
                            lastSavedAt = System.currentTimeMillis(),
                            message = "已恢复 v${version.version} 为新版本 v${persisted.draft.version}；${report.overallRisk.label}风险依赖 ${report.all.size} 项，请复核后续剧情",
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "恢复版本失败") }
                }
        }
    }

    fun flushAndClose(onDone: () -> Unit) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            val ok = if (_state.value.dirty) persist(createVersion = false, announce = false) else true
            if (ok) onDone()
        }
    }

    fun clearNotice() = _state.update { it.copy(message = null, error = null) }

    private fun List<ChapterDraft>.replaceDraft(draft: ChapterDraft): List<ChapterDraft> {
        val found = any { it.id == draft.id || it.chapterNumber == draft.chapterNumber }
        return if (found) {
            map { chapter -> if (chapter.id == draft.id || chapter.chapterNumber == draft.chapterNumber) draft else chapter }
                .sortedBy { it.chapterNumber }
        } else {
            (this + draft).sortedBy { it.chapterNumber }
        }
    }
}
