package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Route
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
import com.xiguli.langhuan.ui.agent.ProjectWorkflowTracePillV7
import com.xiguli.langhuan.ui.agent.ProjectWorkflowTraceSheetV7
import com.xiguli.langhuan.ui.canon.CanonChangeProposalSheetV7
import com.xiguli.langhuan.ui.canon.CanonChangeProposalViewModel
import com.xiguli.langhuan.ui.canon.CanonMigrationQueueSheetV8
import com.xiguli.langhuan.ui.creation.ProjectConversationUiState
import com.xiguli.langhuan.ui.creation.ProjectConversationViewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
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
    val t = LocalLanghuanUiTokens.current
    val quickAction = workspaceQuickActionV6(flow)
    val latestAssistant = conversation.streamingReply.takeIf(String::isNotBlank)
        ?: conversation.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()
    val disabled = flow.busy || conversation.isBusy || externalBusy

    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        color = t.background,
        contentColor = t.foreground,
        shadowElevation = 8.dp,
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (latestAssistant.isNotBlank() || conversation.isBusy) {
                LanghuanCard(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onHistory),
                    contentPadding = 10.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = LanghuanShape.chip,
                            color = t.warmSurface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (conversation.isBusy) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.7.dp, color = t.accent)
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp), tint = t.accent)
                                }
                            }
                        }
                        Text(
                            if (conversation.isBusy && latestAssistant.isBlank()) {
                                conversation.routeSummary.ifBlank { "琅嬛正在结合当前项目分析…" }
                            } else latestAssistant,
                            modifier = Modifier.padding(start = 9.dp).weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Rounded.History, "会话记录", Modifier.size(17.dp), tint = t.mutedForeground)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkspaceToolChipV6(
                        icon = Icons.Rounded.History,
                        label = "会话",
                        onClick = onHistory,
                    )
                    ProjectWorkflowTracePillV7(
                        workflow = conversation.workflow,
                        flow = flow,
                        onClick = onTrace,
                    )
                    if (migrationCount > 0) {
                        WorkspaceToolChipV6(
                            icon = Icons.Rounded.Route,
                            label = "修复队列 $migrationCount",
                            accent = true,
                            onClick = onMigrationQueue,
                        )
                    }
                    lastPlan?.let { plan -> LanghuanBadge(plan.summary) }
                }

                quickAction?.let { action ->
                    Spacer(Modifier.width(7.dp))
                    Surface(
                        shape = LanghuanShape.chip,
                        color = if (action == WorkspaceQuickActionV6.STOP) t.destructive.copy(alpha = .08f) else t.foreground,
                        contentColor = if (action == WorkspaceQuickActionV6.STOP) t.destructive else t.primaryForeground,
                        border = BorderStroke(
                            1.dp,
                            if (action == WorkspaceQuickActionV6.STOP) t.destructive.copy(alpha = .22f) else t.foreground,
                        ),
                    ) {
                        Row(
                            Modifier
                                .clickable(
                                    enabled = action == WorkspaceQuickActionV6.STOP || !flow.busy,
                                    onClick = { onQuickAction(action) },
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (action == WorkspaceQuickActionV6.STOP) Icons.Rounded.StopCircle else Icons.Rounded.CheckCircle,
                                null,
                                Modifier.size(16.dp),
                            )
                            Text(
                                action.label,
                                modifier = Modifier.padding(start = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 5.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("直接说：聊设定、改 Canon、调场景、改正文、检查冲突都可以…") },
                        minLines = 1,
                        maxLines = 4,
                        enabled = !disabled,
                        shape = LanghuanShape.chip,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                    val canSend = input.isNotBlank() && !disabled
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = LanghuanShape.chip,
                        color = if (canSend) t.foreground else t.muted,
                        contentColor = if (canSend) t.primaryForeground else t.mutedForeground,
                    ) {
                        Box(
                            Modifier.clickable(enabled = canSend, onClick = onSend),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ArrowUpward,
                                "发送",
                                Modifier.size(19.dp),
                                tint = if (canSend) t.primaryForeground else t.mutedForeground,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceToolChipV6(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = LanghuanShape.chip,
        color = if (accent) t.warmSurface else t.card,
        contentColor = if (accent) t.accent else t.foreground,
        border = BorderStroke(1.dp, if (accent) t.accent.copy(alpha = .18f) else t.border),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(15.dp), tint = if (accent) t.accent else t.mutedForeground)
            Text(
                label,
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (accent) t.accent else t.foreground,
            )
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
    val t = LocalLanghuanUiTokens.current
    val inherited = state.messages.count { it.origin == ProjectConversationOrigin.CREATION }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        shape = LanghuanShape.sheetTop,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.86f).navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("连续创作会话", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
                    Text(
                        if (inherited > 0) {
                            "承接建书阶段 $inherited 条消息 · 项目事实始终高于旧讨论"
                        } else {
                            "讨论与执行指令会保留，但不会自动成为 Canon"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                LanghuanBadge("${state.messages.size} 条")
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    if (message.role == "assistant") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = LanghuanShape.chip,
                                color = t.warmSurface,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp), tint = t.accent)
                                }
                            }
                            Text(
                                message.text,
                                modifier = Modifier.padding(start = 9.dp).weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.foreground,
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Surface(
                                modifier = Modifier.widthIn(max = 320.dp),
                                shape = LanghuanShape.card,
                                color = t.foreground,
                                contentColor = t.primaryForeground,
                            ) {
                                Text(
                                    message.text,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = t.primaryForeground,
                                )
                            }
                        }
                    }
                }
                if (state.streamingReply.isNotBlank()) {
                    item {
                        Row(verticalAlignment = Alignment.Top) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = t.accent)
                            Text(
                                state.streamingReply,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.foreground,
                            )
                        }
                    }
                }
            }

            state.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LanghuanShape.chip,
                    color = t.destructive.copy(alpha = .08f),
                    border = BorderStroke(1.dp, t.destructive.copy(alpha = .18f)),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = t.destructive)
                        TextButton(onClick = onClearError) { Text("知道了", color = t.destructive) }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.card,
                colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
            ) {
                Text("回到写作继续说")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
