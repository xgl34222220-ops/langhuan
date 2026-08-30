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
                    events = previous?.events.orEmpty(), result = previous?.result, review = previous?.review, queuedCount = queuedSize(),
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
        val gateway = ReferenceDnaAwareAiGateway(app, command.novelId, baseGateway)
        val dnaSummary = ReferenceDnaBindingStore(app).summary(command.novelId)
        _state.value = ChapterRunRuntimeState(
            active = true, taskKind = ChapterRuntimeTaskKind.GENERATE, novelId = command.novelId, chapterNumber = command.chapterNumber,
            snapshot = command.snapshot, draft = command.draft,
            providerLabel = if (dnaSummary.count > 0) "$providerLabel · DNA ${dnaSummary.count}本" else providerLabel,
            queuedCount = queuedSize(), startedAt = started, updatedAt = started,
        )
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
            _state.update { it.copy(active = false, preview = result.chapter.content, result = result, message = if (result.canCommit) "正文已生成并通过阻断级检查" else "正文已生成，但存在阻断级一致性问题", error = null, updatedAt = System.currentTimeMillis()) }
        } catch (_: TimeoutCancellationException) {
            val message = "整章生成超过 8 分钟，已自动停止；已返回内容和断点仍保留。请重试或切换更稳定的模型/中转站。"
            emitLocalFailure(command, RunStage.READY_TO_COMMIT, message)
            _state.update { it.copy(active = false, error = message, updatedAt = System.currentTimeMillis()) }
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(active = false, message = "已停止本次生成；断点已保留，已收到内容不会自动二次请求", error = null, updatedAt = System.currentTimeMillis()) }
        } catch (error: Throwable) {
            emitLocalFailure(command, RunStage.READY_TO_COMMIT, error.message ?: "正文生成失败")
            _state.update { it.copy(active = false, error = error.message ?: "正文生成失败", updatedAt = System.currentTimeMillis()) }
        }
    }

    private suspend fun executeCommit(command: RuntimeCommand.Commit) {
        val started = System.currentTimeMillis()
        val resolved = resolveGatewayOrNull()
        if (resolved == null && !command.allowNoAi) error("请先到设置添加并启用一个 AI 服务")
        val providerLabel = resolved?.first ?: "未配置 AI · 仅保存正文"
        val gateway = resolved?.second?.let { ReferenceDnaAwareAiGateway(app, command.novelId, it) }
        val previous = _state.value.takeIf { it.matches(command.novelId, command.chapterNumber) }
        _state.value = ChapterRunRuntimeState(
            active = true, taskKind = ChapterRuntimeTaskKind.COMMIT, novelId = command.novelId, chapterNumber = command.chapterNumber,
            snapshot = command.snapshot, draft = command.draft, providerLabel = providerLabel, preview = command.result.chapter.content,
            events = previous?.events.orEmpty(), result = command.result, review = previous?.review, queuedCount = queuedSize(), startedAt = started, updatedAt = started,
        )
        showForeground(command, "正在保存正文并执行章节后处理", false)
        try {
            val outcome = coordinator.commit(command.snapshot, command.draft, command.result, gateway) { event -> emit(command, event, false) }
            command.result.modelAttributions.firstOrNull { it.task == AiTaskType.PROSE_AUTHOR.name }?.let { attribution ->
                val acceptanceKey = listOf(command.novelId, command.chapterNumber.toString(), command.draft.version.toString(), command.result.chapter.content.hashCode().toString()).joinToString(":")
                modelTelemetry.recordUserAccepted(attribution, acceptanceKey)
            }
            applyPersistedOutcome(outcome.persisted, outcome.review, outcome.summary(), true)
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(active = false, message = "保存后处理被系统中断；已完成阶段仍可从断点恢复", updatedAt = System.currentTimeMillis()) }; throw cancelled
        } catch (error: Throwable) {
            _state.update { it.copy(active = false, error = error.message ?: "保存正文失败", updatedAt = System.currentTimeMillis()) }
        }
    }

    private suspend fun executeReview(command: RuntimeCommand.Review) {
        val started = System.currentTimeMillis()
        val resolved = resolveGatewayOrNull() ?: error("请先到设置添加并启用一个 AI 服务")
        val gateway = ReferenceDnaAwareAiGateway(app, command.novelId, resolved.second)
        val previous = _state.value.takeIf { it.matches(command.novelId, command.chapterNumber) }
        _state.value = ChapterRunRuntimeState(
            active = true, taskKind = ChapterRuntimeTaskKind.REVIEW, novelId = command.novelId, chapterNumber = command.chapterNumber,
            snapshot = command.snapshot, draft = command.draft, providerLabel = resolved.first, preview = previous?.preview.orEmpty(), events = previous?.events.orEmpty(),
            result = previous?.result, queuedCount = queuedSize(), startedAt = started, updatedAt = started,
        )
        showForeground(command, "正在进行 Agent 章节复盘", false)
        try {
            val reviewed = coordinator.reviewSavedChapter(command.snapshot, command.draft, gateway) { event -> emit(command, event, false) }
            applyPersistedOutcome(reviewed.persisted, reviewed.review, "Agent 已完成复盘；${reviewed.stagedCount} 条候选事实已进入 Candidate", false)
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(active = false, message = "Agent 复盘被系统中断；不会自动重复已完成请求", updatedAt = System.currentTimeMillis()) }; throw cancelled
        } catch (error: Throwable) {
            _state.update { it.copy(active = false, error = error.message ?: "章节复盘失败", updatedAt = System.currentTimeMillis()) }
        }
    }

    private fun applyPersistedOutcome(persisted: PersistedStory, review: AgentReview?, message: String, clearResult: Boolean) {
        _state.update { it.copy(active = false, snapshot = persisted.snapshot, draft = persisted.draft, preview = if (clearResult) "" else it.preview, result = if (clearResult) null else it.result, review = review, message = message, error = null, updatedAt = System.currentTimeMillis()) }
    }

    private fun emit(command: RuntimeCommand, event: RunEvent, canStop: Boolean) {
        _state.update { it.copy(events = (it.events + event).takeLast(96), updatedAt = System.currentTimeMillis()) }
        val detail = buildString { append(event.stage.label); if (event.detail.isNotBlank()) append(" · ").append(event.detail.take(96)) }
        showForeground(command, detail, canStop)
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
