package com.xiguli.langhuan.engine

import android.app.Application
import com.xiguli.langhuan.data.PersistedStory
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StorySnapshot
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class ChapterRuntimeTaskKind { IDLE, GENERATE, COMMIT, REVIEW }

data class ChapterRunRuntimeState(
    val active: Boolean = false,
    val taskKind: ChapterRuntimeTaskKind = ChapterRuntimeTaskKind.IDLE,
    val novelId: String = "",
    val chapterNumber: Int = 0,
    val snapshot: StorySnapshot? = null,
    val draft: ChapterDraft? = null,
    val providerLabel: String = "",
    val preview: String = "",
    val events: List<RunEvent> = emptyList(),
    val result: GenerationResult? = null,
    val review: AgentReview? = null,
    val runtimePlan: ProjectRuntimeSkillPlan? = null,
    val runtimeAudit: ProjectRuntimeSkillAudit? = null,
    val queuedCount: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    fun matches(novelId: String, chapterNumber: Int): Boolean = this.novelId == novelId && this.chapterNumber == chapterNumber
}

class ChapterRunRuntime(application: Application) {
    private val app = application.applicationContext
    private val repository = PersistentStoryRepository(app)
    private val projects = StoryProjectManager(app)
    private val modelTelemetry = AiModelTelemetryStore(app)
    private val coordinator = ChapterRunCoordinator(AppChapterRunStore(repository, projects), PersistentChapterRunCheckpointStore(app))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val queue = ArrayDeque<RuntimeCommand>()
    private var activeJob: Job? = null
    private val _state = MutableStateFlow(ChapterRunRuntimeState())
    val state: StateFlow<ChapterRunRuntimeState> = _state.asStateFlow()

    fun generate(snapshot: StorySnapshot, draft: ChapterDraft, targetWords: Int, extraInstruction: String = "", forceNew: Boolean = false, allowDemoFallback: Boolean = false) {
        enqueue(RuntimeCommand.Generate(snapshot, draft, targetWords, extraInstruction, forceNew, allowDemoFallback))
    }
    fun commit(snapshot: StorySnapshot, draft: ChapterDraft, result: GenerationResult, allowNoAi: Boolean = false) { enqueue(RuntimeCommand.Commit(snapshot, draft, result, allowNoAi)) }
    fun review(snapshot: StorySnapshot, draft: ChapterDraft) { enqueue(RuntimeCommand.Review(snapshot, draft)) }

    fun stopCurrentGeneration(): Boolean = synchronized(lock) {
        if (_state.value.taskKind != ChapterRuntimeTaskKind.GENERATE || !_state.value.active) return@synchronized false
        activeJob?.cancel(CancellationException("用户停止生成")); true
    }

    fun abandon(snapshot: StorySnapshot, draft: ChapterDraft) {
        coordinator.abandon(snapshot, draft)
        synchronized(lock) {
            queue.removeIf { it.novelId == snapshot.novel.id && it.chapterNumber == draft.chapterNumber }
            val current = _state.value
            if (!current.active && current.matches(snapshot.novel.id, draft.chapterNumber)) _state.value = ChapterRunRuntimeState(queuedCount = queue.size)
            else _state.update { it.copy(queuedCount = queue.size) }
        }
    }

    fun clearTerminalState(novelId: String, chapterNumber: Int) {
        synchronized(lock) {
            val current = _state.value
            if (!current.active && current.matches(novelId, chapterNumber)) _state.value = ChapterRunRuntimeState(queuedCount = queue.size)
        }
    }

    private fun enqueue(command: RuntimeCommand) {
        synchronized(lock) {
            val duplicate = queue.any { it.kind == command.kind && it.novelId == command.novelId && it.chapterNumber == command.chapterNumber }
            val runningDuplicate = _state.value.active && _state.value.taskKind == command.kind && _state.value.matches(command.novelId, command.chapterNumber)
            if (duplicate || runningDuplicate) return
            queue.addLast(command)
            _state.update { it.copy(queuedCount = queue.size, error = null) }
            if (activeJob == null) launchNextLocked()
        }
    }

    private fun launchNextLocked() {
        val command = queue.pollFirst() ?: run { _state.update { it.copy(queuedCount = 0) }; return }
        _state.update { it.copy(queuedCount = queue.size) }
        activeJob = scope.launch {
            try { execute(command) }
            catch (cancelled: CancellationException) {
                if (_state.value.active) _state.update { it.copy(active = false, message = it.message ?: "当前后台任务已停止；持久化断点已保留", updatedAt = System.currentTimeMillis()) }
            } catch (error: Throwable) {
                val previous = _state.value.takeIf { it.matches(command.novelId, command.chapterNumber) }
                _state.value = ChapterRunRuntimeState(
                    active = false, taskKind = command.kind, novelId = command.novelId, chapterNumber = command.chapterNumber,
                    snapshot = previous?.snapshot, draft = previous?.draft, providerLabel = previous?.providerLabel.orEmpty(), preview = previous?.preview.orEmpty(),
                    events = previous?.events.orEmpty(), result = previous?.result, review = previous?.review,
                    runtimePlan = previous?.runtimePlan, runtimeAudit = previous?.runtimeAudit, queuedCount = queuedSize(),
                    error = error.message ?: "后台章节任务失败", updatedAt = System.currentTimeMillis(),
                )
            } finally {
                ChapterRunForegroundService.hide(app)
                synchronized(lock) { activeJob = null; if (queue.isNotEmpty()) launchNextLocked() }
            }
        }
    }

    private suspend fun execute(command: RuntimeCommand) = when (command) {
        is RuntimeCommand.Generate -> executeGenerate(command)
        is RuntimeCommand.Commit -> executeCommit(command)
        is RuntimeCommand.Review -> executeReview(command)
    }

    private suspend fun executeGenerate(command: RuntimeCommand.Generate) {
        val started = System.currentTimeMillis()
        val (providerLabel, baseGateway) = resolveGateway(command.allowDemoFallback)
        val dnaSummary = ReferenceDnaBindingStore(app).summary(command.novelId)
        val runtimePlan = ProjectRuntimeSkillPlanner.build(command.snapshot, command.draft, dnaSummary.count)
        _state.value = ChapterRunRuntimeState(
            active = true, taskKind = ChapterRuntimeTaskKind.GENERATE, novelId = command.novelId, chapterNumber = command.chapterNumber,
            snapshot = command.snapshot, draft = command.draft,
            providerLabel = if (dnaSummary.count > 0) "$providerLabel · DNA ${dnaSummary.count}本" else providerLabel,
            runtimePlan = runtimePlan,
            queuedCount = queuedSize(), startedAt = started, updatedAt = started,
        )
        val gateway = generationReferenceAwareGateway(command, baseGateway, canStop = true)
        emit(command, RunEvent(RunStage.SKILL_PLAN, RunStatus.SUCCESS, runtimePlan.phaseSummary(ProjectRuntimePhase.GENERATION)), true)
        showForeground(command, if (dnaSummary.count > 0) "正在生成正文 · ${dnaSummary.label} · 最长 8 分钟" else "正在生成正文 · 最长 8 分钟，可随时停止", canStop = true)
        try {
            val result = withTimeout(GENERATION_DEADLINE_MS) {
                coordinator.generate(
                    snapshot = command.snapshot, draft = command.draft, gateway = gateway, targetWords = command.targetWords,
                    extraInstruction = command.extraInstruction, forceNew = command.forceNew,
                    onDelta = { preview -> _state.update { it.copy(preview = preview, updatedAt = System.currentTimeMillis()) } },
                    onRunEvent = { event -> emit(command, event, true) },
                )
            }
            val audit = emitSkillAudit(
                command = command,
                plan = runtimePlan,
                phases = setOf(ProjectRuntimePhase.GENERATION),
                prefix = "生成期 Skill OS",
                canStop = false,
            )
            _state.update {
                it.copy(
                    active = false,
                    preview = result.chapter.content,
                    result = result,
                    runtimeAudit = audit,
                    message = if (result.canCommit) "正文已生成并通过阻断级检查" else "正文已生成，但存在阻断级一致性问题",
                    error = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        } catch (_: TimeoutCancellationException) {
            val message = "整章生成超过 8 分钟，已自动停止；已返回内容和断点仍保留。请重试或切换更稳定的模型/中转站。"
            emitLocalFailure(command, RunStage.READY_TO_COMMIT, message)
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.GENERATION), "生成期 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, error = message, updatedAt = System.currentTimeMillis()) }
        } catch (cancelled: CancellationException) {
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.GENERATION), "生成期 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, message = "已停止本次生成；断点已保留，已收到内容不会自动二次请求", error = null, updatedAt = System.currentTimeMillis()) }
        } catch (error: Throwable) {
            emitLocalFailure(command, RunStage.READY_TO_COMMIT, error.message ?: "正文生成失败")
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.GENERATION), "生成期 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, error = error.message ?: "正文生成失败", updatedAt = System.currentTimeMillis()) }
        }
    }

    private suspend fun executeCommit(command: RuntimeCommand.Commit) {
        val started = System.currentTimeMillis()
        val resolved = resolveGatewayOrNull()
        if (resolved == null && !command.allowNoAi) error("请先到设置添加并启用一个 AI 服务")
        val providerLabel = resolved?.first ?: "未配置 AI · 仅保存正文"
        val previous = _state.value.takeIf { it.matches(command.novelId, command.chapterNumber) }
        val dnaCount = ReferenceDnaBindingStore(app).summary(command.novelId).count
        val runtimePlan = previous?.runtimePlan?.takeIf { it.novelId == command.novelId && it.chapterNumber == command.chapterNumber }
            ?: ProjectRuntimeSkillPlanner.build(command.snapshot, command.draft, dnaCount)
        _state.value = ChapterRunRuntimeState(
            active = true, taskKind = ChapterRuntimeTaskKind.COMMIT, novelId = command.novelId, chapterNumber = command.chapterNumber,
            snapshot = command.snapshot, draft = command.draft, providerLabel = providerLabel, preview = command.result.chapter.content,
            events = previous?.events.orEmpty(), result = command.result, review = previous?.review,
            runtimePlan = runtimePlan, runtimeAudit = previous?.runtimeAudit,
            queuedCount = queuedSize(), startedAt = started, updatedAt = started,
        )
        // Post-commit editor tasks may still consume DNA, but they must not masquerade as generation-phase DNA evidence.
        val gateway = resolved?.second?.let { ReferenceDnaAwareAiGateway(app, command.novelId, it) }
        emit(command, RunEvent(RunStage.SKILL_PLAN, RunStatus.SUCCESS, runtimePlan.phaseSummary(ProjectRuntimePhase.POST_COMMIT)), false)
        showForeground(command, "正在保存正文并执行章节后处理", false)
        try {
            val outcome = coordinator.commit(command.snapshot, command.draft, command.result, gateway) { event -> emit(command, event, false) }
            command.result.modelAttributions.firstOrNull { it.task == AiTaskType.PROSE_AUTHOR.name }?.let { attribution ->
                val acceptanceKey = listOf(command.novelId, command.chapterNumber.toString(), command.draft.version.toString(), command.result.chapter.content.hashCode().toString()).joinToString(":")
                modelTelemetry.recordUserAccepted(attribution, acceptanceKey)
            }
            val phases = if (_state.value.events.any { it.stage == RunStage.DRAFT || it.stage == RunStage.CONSISTENCY }) {
                setOf(ProjectRuntimePhase.GENERATION, ProjectRuntimePhase.POST_COMMIT)
            } else {
                setOf(ProjectRuntimePhase.POST_COMMIT)
            }
            val audit = emitSkillAudit(command, runtimePlan, phases, "正式项目 Skill OS", false)
            _state.update { it.copy(runtimeAudit = audit) }
            applyPersistedOutcome(outcome.persisted, outcome.review, "${outcome.summary()} · ${audit.summary("Skill OS")}", true)
        } catch (cancelled: CancellationException) {
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.POST_COMMIT), "提交后 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, message = "保存后处理被系统中断；已完成阶段仍可从断点恢复", updatedAt = System.currentTimeMillis()) }; throw cancelled
        } catch (error: Throwable) {
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.POST_COMMIT), "提交后 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, error = error.message ?: "保存正文失败", updatedAt = System.currentTimeMillis()) }
        }
    }

    private suspend fun executeReview(command: RuntimeCommand.Review) {
        val started = System.currentTimeMillis()
        val resolved = resolveGatewayOrNull() ?: error("请先到设置添加并启用一个 AI 服务")
        val previous = _state.value.takeIf { it.matches(command.novelId, command.chapterNumber) }
        val runtimePlan = ProjectRuntimeSkillPlanner.manualReview(command.snapshot, command.draft)
        _state.value = ChapterRunRuntimeState(
            active = true, taskKind = ChapterRuntimeTaskKind.REVIEW, novelId = command.novelId, chapterNumber = command.chapterNumber,
            snapshot = command.snapshot, draft = command.draft, providerLabel = resolved.first, preview = previous?.preview.orEmpty(), events = previous?.events.orEmpty(),
            result = previous?.result, runtimePlan = runtimePlan, queuedCount = queuedSize(), startedAt = started, updatedAt = started,
        )
        // Manual review has its own Agent receipt; DNA use inside the editor prompt is intentionally not attributed to generation.
        val gateway = ReferenceDnaAwareAiGateway(app, command.novelId, resolved.second)
        emit(command, RunEvent(RunStage.SKILL_PLAN, RunStatus.SUCCESS, runtimePlan.phaseSummary(ProjectRuntimePhase.MANUAL_REVIEW)), false)
        showForeground(command, "正在进行 Agent 章节复盘", false)
        try {
            val reviewed = coordinator.reviewSavedChapter(command.snapshot, command.draft, gateway) { event -> emit(command, event, false) }
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.MANUAL_REVIEW), "复盘 Skill OS", false)
            _state.update { it.copy(runtimeAudit = audit) }
            applyPersistedOutcome(reviewed.persisted, reviewed.review, "Agent 已完成复盘；${reviewed.stagedCount} 条候选事实已进入 Candidate · ${audit.summary("Skill OS")}", false)
        } catch (cancelled: CancellationException) {
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.MANUAL_REVIEW), "复盘 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, message = "Agent 复盘被系统中断；不会自动重复已完成请求", updatedAt = System.currentTimeMillis()) }; throw cancelled
        } catch (error: Throwable) {
            val audit = emitSkillAudit(command, runtimePlan, setOf(ProjectRuntimePhase.MANUAL_REVIEW), "复盘 Skill OS", false)
            _state.update { it.copy(active = false, runtimeAudit = audit, error = error.message ?: "章节复盘失败", updatedAt = System.currentTimeMillis()) }
        }
    }

    private fun applyPersistedOutcome(persisted: PersistedStory, review: AgentReview?, message: String, clearResult: Boolean) {
        _state.update { it.copy(active = false, snapshot = persisted.snapshot, draft = persisted.draft, preview = if (clearResult) "" else it.preview, result = if (clearResult) null else it.result, review = review, message = message, error = null, updatedAt = System.currentTimeMillis()) }
    }

    private fun emitSkillAudit(
        command: RuntimeCommand,
        plan: ProjectRuntimeSkillPlan,
        phases: Set<ProjectRuntimePhase>,
        prefix: String,
        canStop: Boolean,
    ): ProjectRuntimeSkillAudit {
        val audit = ProjectRuntimeSkillPlanner.audit(plan, _state.value.events, phases, finalize = true)
        emit(command, RunEvent(RunStage.SKILL_AUDIT, audit.runStatus, audit.summary(prefix)), canStop)
        return audit
    }

    private fun emit(command: RuntimeCommand, event: RunEvent, canStop: Boolean) {
        _state.update { current ->
            val nextEvents = (current.events + event).takeLast(96)
            val liveAudit = current.runtimePlan?.let { plan ->
                ProjectRuntimeSkillPlanner.audit(
                    plan = plan,
                    events = nextEvents,
                    phases = liveAuditPhases(current.taskKind, nextEvents),
                    finalize = false,
                )
            } ?: current.runtimeAudit
            current.copy(
                events = nextEvents,
                runtimeAudit = liveAudit,
                updatedAt = System.currentTimeMillis(),
            )
        }
        val detail = buildString { append(event.stage.label); if (event.detail.isNotBlank()) append(" · ").append(event.detail.take(96)) }
        showForeground(command, detail, canStop)
    }

    private fun generationReferenceAwareGateway(command: RuntimeCommand.Generate, delegate: AiGateway, canStop: Boolean): AiGateway =
        ReferenceDnaAwareAiGateway(app, command.novelId, delegate) { evidence ->
            emit(
                command,
                RunEvent(
                    stage = RunStage.REFERENCE_DNA,
                    status = RunStatus.SUCCESS,
                    detail = "${evidence.task.name} · ${evidence.purpose.name} · 实际注入 ${evidence.injectedChars} 字",
                ),
                canStop,
            )
        }

    private fun liveAuditPhases(taskKind: ChapterRuntimeTaskKind, events: List<RunEvent>): Set<ProjectRuntimePhase> = when (taskKind) {
        ChapterRuntimeTaskKind.GENERATE -> setOf(ProjectRuntimePhase.GENERATION)
        ChapterRuntimeTaskKind.REVIEW -> setOf(ProjectRuntimePhase.MANUAL_REVIEW)
        ChapterRuntimeTaskKind.COMMIT -> buildSet {
            add(ProjectRuntimePhase.POST_COMMIT)
            if (events.any { it.stage == RunStage.DRAFT || it.stage == RunStage.CONSISTENCY }) add(ProjectRuntimePhase.GENERATION)
        }
        ChapterRuntimeTaskKind.IDLE -> emptySet()
    }

    private fun emitLocalFailure(command: RuntimeCommand, stage: RunStage, detail: String) { emit(command, RunEvent(stage, RunStatus.FAILED, detail), command.kind == ChapterRuntimeTaskKind.GENERATE) }
    private fun showForeground(command: RuntimeCommand, detail: String, canStop: Boolean) {
        ChapterRunForegroundService.show(app, command.novelId, command.chapterNumber, "第${command.chapterNumber}章 · 后台章节任务", detail, canStop)
    }

    private suspend fun resolveGateway(allowDemoFallback: Boolean): Pair<String, AiGateway> {
        val resolved = resolveGatewayOrNull(); if (resolved != null) return resolved
        if (allowDemoFallback) return "离线体验模式" to DemoAiGateway()
        error("请先到设置添加并启用一个 AI 服务")
    }
    private suspend fun resolveGatewayOrNull(): Pair<String, AiGateway>? = runCatching {
        val routed = TaskDispatchingAiGateway(TaskModelRouter(app).snapshot()); routed.summary to routed
    }.getOrNull()
    private fun queuedSize(): Int = synchronized(lock) { queue.size }

    private sealed interface RuntimeCommand {
        val kind: ChapterRuntimeTaskKind; val novelId: String; val chapterNumber: Int
        data class Generate(val snapshot: StorySnapshot, val draft: ChapterDraft, val targetWords: Int, val extraInstruction: String, val forceNew: Boolean, val allowDemoFallback: Boolean) : RuntimeCommand {
            override val kind = ChapterRuntimeTaskKind.GENERATE; override val novelId = snapshot.novel.id; override val chapterNumber = draft.chapterNumber
        }
        data class Commit(val snapshot: StorySnapshot, val draft: ChapterDraft, val result: GenerationResult, val allowNoAi: Boolean) : RuntimeCommand {
            override val kind = ChapterRuntimeTaskKind.COMMIT; override val novelId = snapshot.novel.id; override val chapterNumber = draft.chapterNumber
        }
        data class Review(val snapshot: StorySnapshot, val draft: ChapterDraft) : RuntimeCommand {
            override val kind = ChapterRuntimeTaskKind.REVIEW; override val novelId = snapshot.novel.id; override val chapterNumber = draft.chapterNumber
        }
    }
}

private const val GENERATION_DEADLINE_MS = 8 * 60_000L
