package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.engine.CanonMigrationTaskStatus
import com.xiguli.langhuan.engine.ProjectConversationOrigin
import com.xiguli.langhuan.engine.WorkspaceNaturalLanguageRouter
import com.xiguli.langhuan.engine.WorkspaceNaturalPlan
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip
import java.util.UUID

private data class PendingCompoundV6(
    val token: String = UUID.randomUUID().toString(),
    val plan: WorkspaceNaturalPlan,
    val sceneConversationSizeBefore: Int,
)

private enum class WorkspaceQuickActionV6(val label: String) {
    STOP("停止生成"),
    REPAIR("修复硬冲突"),
    SAVE("保存本章"),
    APPLY_SCENE("确认场景"),
    REVIEW_MEMORY("整理记忆"),
    NEXT("下一章"),
    GENERATE("直接写这一章"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingWorkspaceV6(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    val flow by viewModel.state.collectAsStateWithLifecycle()
    val conversationVm: ProjectConversationViewModel = viewModel()
    val conversation by conversationVm.state.collectAsStateWithLifecycle()
    val canonVm: CanonChangeProposalViewModel = viewModel()
    val canon by canonVm.state.collectAsStateWithLifecycle()

    var input by remember(novelId) { mutableStateOf("") }
    var lastPlan by remember(novelId) { mutableStateOf<WorkspaceNaturalPlan?>(null) }
    var pendingCompound by remember(novelId) { mutableStateOf<PendingCompoundV6?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var showTrace by remember { mutableStateOf(false) }

    LaunchedEffect(novelId) {
        conversationVm.load(novelId)
        canonVm.loadMigrationQueue(novelId)
    }
    LaunchedEffect(canon.appliedAt) {
        if (canon.appliedAt > 0L) {
            viewModel.invalidateAfterExternalEdit(novelId)
            viewModel.load(novelId)
        }
    }

    LaunchedEffect(
        pendingCompound?.token,
        flow.isPlanningScenes,
        flow.sceneConversation.size,
        flow.error,
    ) {
        val pending = pendingCompound ?: return@LaunchedEffect
        if (flow.isPlanningScenes) return@LaunchedEffect

        val sceneSucceeded = flow.sceneConversation.size >= pending.sceneConversationSizeBefore + 2
        if (sceneSucceeded) {
            when {
                pending.plan.hasProseMutation -> viewModel.generate(pending.plan.original)
                pending.plan.requestsReview -> {
                    conversationVm.sendWithTransientContext(
                        text = "检查刚才按这个要求调整后的场景有没有和当前项目事实、时间线或人物状态冲突：${pending.plan.original}",
                        transientContext = workingSceneContextV6(flow.workingScenes),
                    )
                }
            }
            pendingCompound = null
        } else if (flow.error != null) {
            pendingCompound = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        WritingWorkspaceV4(
            novelId = novelId,
            viewModel = viewModel,
            onClose = onClose,
            onEditChapter = onEditChapter,
        )

        WorkspaceNaturalControllerDockV6(
            modifier = Modifier.align(Alignment.BottomCenter),
            flow = flow,
            conversation = conversation,
            externalBusy = canon.active,
            migrationCount = canon.pendingMigrationCount,
            input = input,
            lastPlan = lastPlan,
            onInput = { input = it },
            onHistory = { showHistory = true },
            onTrace = { showTrace = true },
            onMigrationQueue = canonVm::openMigrationQueue,
            onQuickAction = { action -> performQuickActionV6(action, flow, viewModel) },
            onSend = {
                val clean = input.trim()
                if (clean.isNotBlank() && !flow.busy && !conversation.isBusy && !canon.active) {
                    val plan = WorkspaceNaturalLanguageRouter.route(clean)
                    input = ""
                    lastPlan = plan
                    when {
                        plan.requestsCanonProposal -> {
                            conversationVm.recordWorkspaceCommand(clean, plan.summary)
                            canonVm.propose(novelId, clean)
                        }
                        plan.isDiscussionOnly || plan.isReviewOnly -> conversationVm.send(clean)
                        plan.mutatesWorkingDraft -> {
                            conversationVm.recordWorkspaceCommand(clean, plan.summary)
                            when {
                                plan.hasSceneMutation -> {
                                    pendingCompound = if (plan.hasProseMutation || plan.requestsReview) {
                                        PendingCompoundV6(plan = plan, sceneConversationSizeBefore = flow.sceneConversation.size)
                                    } else null
                                    viewModel.planScenes(clean)
                                }
                                plan.hasProseMutation -> viewModel.generate(clean)
                            }
                        }
                    }
                }
            },
        )
    }

    if (showHistory) {
        WorkspaceConversationHistoryV6(
            state = conversation,
            onDismiss = { showHistory = false },
            onClearError = conversationVm::clearError,
        )
    }

    if (showTrace) {
        ProjectWorkflowTraceSheetV7(
            conversation = conversation,
            flow = flow,
            onDismiss = { showTrace = false },
        )
    }

    if (canon.isBusy || canon.isApplying || canon.proposal != null || canon.error != null) {
        CanonChangeProposalSheetV7(
            state = canon,
            onApply = canonVm::applyPending,
            onDiscard = canonVm::discardPending,
            onDismiss = canonVm::discardPending,
        )
    } else if (canon.migrationVisible) {
        CanonMigrationQueueSheetV8(
            state = canon,
            onGenerateRepairProposal = canonVm::proposeMigrationRepair,
            onOpenChapter = { chapterNumber ->
                canonVm.closeMigrationQueue()
                onEditChapter(novelId, chapterNumber)
            },
            onDone = { taskId -> canonVm.setMigrationStatus(taskId, CanonMigrationTaskStatus.DONE) },
            onSkip = { taskId -> canonVm.setMigrationStatus(taskId, CanonMigrationTaskStatus.SKIPPED) },
            onClearResolved = canonVm::clearResolvedMigration,
            onDismiss = canonVm::closeMigrationQueue,
        )
    }
}

@Composable
private fun WorkspaceNaturalControllerDockV6(
    modifier: Modifier = Modifier,
    flow: WritingFlowUiState,
    conversation: ProjectConversationUiState,
    externalBusy: Boolean,
    migrationCount: Int,
    input: String,
    lastPlan: WorkspaceNaturalPlan?,
    onInput: (String) -> Unit,
    onHistory: () -> Unit,
    onTrace: () -> Unit,
    onMigrationQueue: () -> Unit,
    onQuickAction: (WorkspaceQuickActionV6) -> Unit,
    onSend: () -> Unit,
) {
    val quickAction = workspaceQuickActionV6(flow)
    val latestAssistant = conversation.streamingReply.takeIf(String::isNotBlank)
        ?: conversation.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()
    val disabled = flow.busy || conversation.isBusy || externalBusy

    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 5.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (latestAssistant.isNotBlank() || conversation.isBusy) {
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(17.dp).clickable(onClick = onHistory),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (conversation.isBusy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (conversation.isBusy && latestAssistant.isBlank()) conversation.routeSummary.ifBlank { "琅嬛正在结合当前项目分析…" } else latestAssistant,
                            modifier = Modifier.padding(start = 7.dp).weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Rounded.History, "会话记录", Modifier.size(17.dp), tint = LocalMiuixTokens.current.textSecondary)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .68f),
                        modifier = Modifier.clickable(onClick = onHistory),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("会话", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    ProjectWorkflowTracePillV7(
                        workflow = conversation.workflow,
                        flow = flow,
                        onClick = onTrace,
                    )
                    if (migrationCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .75f),
                            modifier = Modifier.clickable(onClick = onMigrationQueue),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Route, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Text(
                                    "修复队列 $migrationCount",
                                    modifier = Modifier.padding(start = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    lastPlan?.let { plan ->
                        Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                plan.summary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                quickAction?.let { action ->
                    Spacer(Modifier.width(7.dp))
                    AssistChip(
                        onClick = { onQuickAction(action) },
                        enabled = action == WorkspaceQuickActionV6.STOP || !flow.busy,
                        label = { Text(action.label) },
                        leadingIcon = {
                            Icon(
                                if (action == WorkspaceQuickActionV6.STOP) Icons.Rounded.StopCircle else Icons.Rounded.CheckCircle,
                                null,
                                Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().squircleClip(25.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
            ) {
                Row(
                    Modifier.padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("直接说：聊设定、改 Canon、调场景、改正文、检查冲突都可以…") },
                        minLines = 1,
                        maxLines = 4,
                        enabled = !disabled,
                        shape = RoundedCornerShape(19.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = if (input.isNotBlank() && !disabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        IconButton(onClick = onSend, enabled = input.isNotBlank() && !disabled) {
                            Icon(
                                Icons.Rounded.Send,
                                "发送",
                                tint = if (input.isNotBlank() && !disabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun workspaceQuickActionV6(state: WritingFlowUiState): WorkspaceQuickActionV6? {
    val draft = state.draft ?: return null
    val result = state.result
    val hasPendingResult = result?.let { it.chapter.content.isNotBlank() && it.chapter.content != draft.content } == true
    val pendingCandidates = state.snapshot?.candidateFacts.orEmpty().count {
        it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING
    }
    return when {
        state.isGenerating -> WorkspaceQuickActionV6.STOP
        state.busy -> null
        hasPendingResult && result?.canCommit == false -> WorkspaceQuickActionV6.REPAIR
        hasPendingResult && result?.canCommit == true -> WorkspaceQuickActionV6.SAVE
        state.sceneDirty && !state.chapterCommitted -> WorkspaceQuickActionV6.APPLY_SCENE
        state.chapterCommitted && state.review == null -> WorkspaceQuickActionV6.REVIEW_MEMORY
        state.review != null && pendingCandidates == 0 -> WorkspaceQuickActionV6.NEXT
        !state.chapterCommitted && result == null -> WorkspaceQuickActionV6.GENERATE
        else -> null
    }
}

private fun performQuickActionV6(action: WorkspaceQuickActionV6, state: WritingFlowUiState, viewModel: WritingFlowViewModel) {
    when (action) {
        WorkspaceQuickActionV6.STOP -> viewModel.cancelGeneration()
        WorkspaceQuickActionV6.REPAIR -> viewModel.repairAndRegenerate()
        WorkspaceQuickActionV6.SAVE -> viewModel.commitAndReview()
        WorkspaceQuickActionV6.APPLY_SCENE -> viewModel.applyScenePlan()
        WorkspaceQuickActionV6.REVIEW_MEMORY -> viewModel.reviewCommittedChapter()
        WorkspaceQuickActionV6.NEXT -> viewModel.advanceToNext(null)
        WorkspaceQuickActionV6.GENERATE -> if (!state.busy) viewModel.generate("")
    }
}

private fun workingSceneContextV6(scenes: List<ScenePlan>): String = buildString {
    appendLine("当前尚未保存的 working ScenePlan：")
    scenes.sortedBy { it.order }.forEach { scene ->
        appendLine("- 场景${scene.order}｜地点：${scene.location}｜目的：${scene.purpose}｜冲突：${scene.conflict}｜结果：${scene.outcome}")
    }
}.trim()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceConversationHistoryV6(
    state: ProjectConversationUiState,
    onDismiss: () -> Unit,
    onClearError: () -> Unit,
) {
    val inherited = state.messages.count { it.origin == ProjectConversationOrigin.CREATION }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.86f).navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("连续创作会话", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (inherited > 0) "承接建书阶段 $inherited 条消息 · 项目事实始终高于旧讨论" else "讨论与执行指令都保留在这里，但不会自动成为 Canon",
                style = MaterialTheme.typography.bodySmall,
                color = LocalMiuixTokens.current.textSecondary,
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    if (message.role == "assistant") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(message.text, modifier = Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Surface(
                                modifier = Modifier.widthIn(max = 320.dp).squircleClip(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .78f),
                            ) {
                                Text(message.text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                            }
                        }
                    }
                }
                if (state.streamingReply.isNotBlank()) {
                    item {
                        Row(verticalAlignment = Alignment.Top) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(state.streamingReply, modifier = Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            state.error?.let { error ->
                Surface(modifier = Modifier.fillMaxWidth().squircleClip(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onClearError) { Text("知道了") }
                    }
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                Text("回到总控继续说")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
