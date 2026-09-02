package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.NovelRouteInput
import com.xiguli.langhuan.engine.NovelSkillExecutionPlanner
import com.xiguli.langhuan.engine.NovelSkillRouter
import com.xiguli.langhuan.engine.NovelWorkflowBootstrap
import com.xiguli.langhuan.engine.PersistentNovelWorkflowStateStore
import com.xiguli.langhuan.engine.ProjectConversationMessage
import com.xiguli.langhuan.engine.ProjectConversationOrigin
import com.xiguli.langhuan.engine.ProjectConversationStore
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.PromptMessage
import com.xiguli.langhuan.engine.ReferenceDnaAwareAiGateway
import com.xiguli.langhuan.engine.ReferenceDnaBindingStore
import com.xiguli.langhuan.engine.TaskModelRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectConversationUiState(
    val novelId: String = "",
    val messages: List<ProjectConversationMessage> = emptyList(),
    val streamingReply: String = "",
    val routeSummary: String = "",
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
)

/**
 * Project-phase continuation chat.
 *
 * This chat can read the current project and Reference DNA, but it deliberately cannot write
 * Canon, overwrite ScenePlan, or replace chapter text. V7 also reads durable workflow state so
 * the host can resume from the actual current gate without making the user manage the workflow.
 */
class ProjectConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val conversations = ProjectConversationStore(application)
    private val references = ReferenceDnaBindingStore(application)
    private val taskRouter = TaskModelRouter(application)
    private val workflows = PersistentNovelWorkflowStateStore(application)
    private val _state = MutableStateFlow(ProjectConversationUiState())
    val state: StateFlow<ProjectConversationUiState> = _state.asStateFlow()

    fun load(novelId: String) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && !current.isLoading) return
        viewModelScope.launch {
            _state.value = ProjectConversationUiState(novelId = novelId, isLoading = true)
            val messages = conversations.load(novelId)
            val loaded = projects.loadStory(novelId)
            val storedWorkflow = workflows.loadOrCreate(novelId)
            val workflow = loaded?.snapshot?.let {
                NovelWorkflowBootstrap.fromSnapshot(storedWorkflow, it)
            } ?: storedWorkflow
            if (workflow != storedWorkflow) workflows.save(workflow)
            _state.update {
                it.copy(
                    messages = messages,
                    routeSummary = "工作流 · ${workflow.compactSummary()}",
                    isLoading = false,
                )
            }
        }
    }

    fun send(text: String) = sendInternal(text, "")

    /** Review proposed working state without persisting it into StorySnapshot. */
    fun sendWithTransientContext(text: String, transientContext: String) =
        sendInternal(text, transientContext.trim())

    /** Persist an explicit workspace execution request into the continuous authoring history. */
    fun recordWorkspaceCommand(text: String, planSummary: String) {
        val before = _state.value
        val clean = text.trim()
        if (clean.isBlank() || before.novelId.isBlank()) return
        val user = ProjectConversationMessage(
            role = "user",
            text = clean,
            origin = ProjectConversationOrigin.PROJECT,
        )
        val receipt = ProjectConversationMessage(
            role = "assistant",
            text = "已交给章节工作台执行：$planSummary。真实执行结果以 Skill OS 运行轨迹为准，未保存内容不会自动进入 Canon。",
            origin = ProjectConversationOrigin.PROJECT,
        )
        conversations.append(before.novelId, user)
        conversations.append(before.novelId, receipt)
        _state.update { it.copy(messages = it.messages + user + receipt, routeSummary = planSummary) }
    }

    private fun sendInternal(text: String, transientContext: String) {
        val clean = text.trim()
        val before = _state.value
        if (clean.isBlank() || before.isBusy || before.novelId.isBlank()) return
        val novelId = before.novelId
        val userMessage = ProjectConversationMessage(
            role = "user",
            text = clean,
            origin = ProjectConversationOrigin.PROJECT,
        )
        conversations.append(novelId, userMessage)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                streamingReply = "",
                isBusy = true,
                error = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val loaded = projects.loadStory(novelId) ?: error("找不到当前小说项目")
                val binding = references.summary(novelId)

                // Old projects have confirmed StorySnapshot data but no V7 workflow metadata.
                // Derive only a safe resume stage before interpreting a gate reply.
                val storedWorkflow = workflows.loadOrCreate(novelId)
                val bootstrappedWorkflow = NovelWorkflowBootstrap.fromSnapshot(storedWorkflow, loaded.snapshot)
                if (bootstrappedWorkflow != storedWorkflow) workflows.save(bootstrappedWorkflow)

                // A short approval/rework reply only affects the current workflow gate. Normal
                // discussion is ignored by the gate router and remains read-only.
                workflows.applyGateReply(novelId, clean)

                val route = NovelSkillRouter.route(
                    NovelRouteInput(
                        message = clean,
                        hasConversationHistory = before.messages.isNotEmpty(),
                        hasFoundation = true,
                        hasSelectedReferences = binding.count > 0,
                    )
                )
                val workflow = workflows.syncRoute(novelId, route)
                _state.update {
                    it.copy(routeSummary = "${route.compactSummary} · ${workflow.compactSummary()}")
                }
                val session = taskRouter.snapshot()
                val routedTask = NovelSkillExecutionPlanner.primaryTask(route)
                val routedGateway = routedTask?.let { session.selection(it).gateway } ?: session.defaultGateway
                val gateway = ReferenceDnaAwareAiGateway(
                    getApplication(),
                    novelId,
                    routedGateway,
                )
                gateway.generateTextStreaming(
                    PromptBundle(
                        system = projectConversationSystem(
                            snapshot = loaded.snapshot,
                            routeGuidance = route.systemGuidance(),
                            workflowGuidance = workflow.systemGuidance(),
                            transientContext = transientContext,
                        ),
                        user = clean,
                        messages = before.messages.takeLast(18).map {
                            PromptMessage(if (it.role == "assistant") "assistant" else "user", it.text)
                        },
                        jsonMode = false,
                    ),
                    onDelta = { partial -> _state.update { it.copy(streamingReply = partial) } },
                ).trim().ifBlank { "我在。继续按这本书当前已经确认的设定往下聊。" }
            }.onSuccess { reply ->
                val assistant = ProjectConversationMessage(
                    role = "assistant",
                    text = reply,
                    origin = ProjectConversationOrigin.PROJECT,
                )
                conversations.append(novelId, assistant)
                _state.update {
                    it.copy(
                        messages = it.messages + assistant,
                        streamingReply = "",
                        isBusy = false,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        streamingReply = "",
                        isBusy = false,
                        error = error.message ?: "项目会话失败",
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}

private fun projectConversationSystem(
    snapshot: StorySnapshot,
    routeGuidance: String,
    workflowGuidance: String,
    transientContext: String = "",
): String = buildString {
    appendLine("你是‘琅嬛’的项目期创作搭档。你正在延续这本书从建书阶段开始的同一条作者会话。")
    appendLine("当前会话只负责讨论、分析、推演和提出修改方案；不要因为聊天本身直接写 Canon、覆盖 ScenePlan、提交 Candidate 或替换正式章节正文。")
    appendLine("如果用户只是问设定、人物、剧情、逻辑、伏笔或下一步怎么写，就直接结合当前项目回答。")
    appendLine("如果用户明确要改正文或场景，可以给出具体建议；真正执行由章节工作台和 V3 Runtime 完成，不要假装聊天已经保存。")
    appendLine("当前 StorySnapshot 中已确认事实优先级高于历史聊天里的旧想法。后续明确决定覆盖之前的讨论。")
    appendLine(routeGuidance)
    appendLine()
    appendLine(workflowGuidance)
    if (transientContext.isNotBlank()) {
        appendLine()
        appendLine("【本轮临时 working draft，仅用于检查，不得视为已保存 Canon】")
        appendLine(transientContext)
    }
    appendLine()
    appendLine("【当前项目】")
    appendLine("书名：${snapshot.novel.title}")
    appendLine("类型：${snapshot.novel.genre}")
    appendLine("简介：${snapshot.novel.premise}")
    appendLine("主题：${snapshot.novel.theme}")
    snapshot.activeOutline.lastOrNull()?.let { outline ->
        appendLine("当前章：第${outline.order}章《${outline.title}》")
        if (outline.objective.isNotBlank()) appendLine("章目标：${outline.objective}")
        if (outline.conflict.isNotBlank()) appendLine("章冲突：${outline.conflict}")
        if (outline.turningPoint.isNotBlank()) appendLine("章转折：${outline.turningPoint}")
    }
    if (snapshot.characters.isNotEmpty()) {
        appendLine("人物状态：")
        snapshot.characters.take(12).forEach { character ->
            appendLine("- ${character.name}｜${character.location}｜${character.emotionalState}｜目标：${character.goal}")
        }
    }
    if (snapshot.recentTimeline.isNotEmpty()) {
        appendLine("最近时间线：")
        snapshot.recentTimeline.takeLast(8).forEach { event ->
            appendLine("- 第${event.chapter}章｜${event.storyTime.ifBlank { event.timeOfDay }}｜${event.summary}")
        }
    }
    if (snapshot.relevantForeshadowing.isNotEmpty()) {
        appendLine("当前相关伏笔：")
        snapshot.relevantForeshadowing.take(8).forEach { clue ->
            appendLine("- ${clue.title}｜${clue.status}｜${clue.detail}")
        }
    }
}
