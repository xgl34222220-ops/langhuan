package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.CanonMigrationTaskStatus
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.engine.WorkspaceNaturalLanguageRouter
import com.xiguli.langhuan.engine.WorkspaceNaturalPlan
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanGlassPanel
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.util.UUID

private enum class WritingSheetV11 { SCENES, STORY, RUN, HISTORY }

private enum class WritingQuickActionV11(val label: String) {
    STOP("停止生成"),
    REPAIR("修复硬冲突"),
    SAVE("保存本章"),
    APPLY_SCENE("确认场景"),
    REVIEW_MEMORY("整理记忆"),
    NEXT("下一章"),
    GENERATE("直接写这一章"),
}

private data class PendingCompoundV11(
    val token: String = UUID.randomUUID().toString(),
    val plan: WorkspaceNaturalPlan,
    val sceneConversationSizeBefore: Int,
)

/**
 * LuoShu production authoring workspace.
 *
 * This replaces the stacked V4 + V6 visible surfaces with one controller while preserving the
 * existing ViewModel/runtime/Canon/ScenePlan/ProjectConversation state machines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingWorkspaceLuoShuV11(
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
    val t = LocalLanghuanUiTokens.current
    val snackbar = remember { SnackbarHostState() }

    var sheet by remember { mutableStateOf<WritingSheetV11?>(null) }
    var input by remember(novelId) { mutableStateOf("") }
    var sceneInstruction by remember(novelId) { mutableStateOf("") }
    var lastPlan by remember(novelId) { mutableStateOf<WorkspaceNaturalPlan?>(null) }
    var pendingCompound by remember(novelId) { mutableStateOf<PendingCompoundV11?>(null) }
    var showTrace by remember { mutableStateOf(false) }

    LaunchedEffect(novelId) {
        viewModel.load(novelId)
        conversationVm.load(novelId)
        canonVm.loadMigrationQueue(novelId)
    }
    LaunchedEffect(flow.draft?.chapterNumber) {
        input = ""
        sceneInstruction = ""
    }
    LaunchedEffect(flow.message, flow.error, conversation.error) {
        val notice = flow.error ?: flow.message ?: conversation.error
        if (!notice.isNullOrBlank()) {
            snackbar.showSnackbar(notice)
            viewModel.clearNotice()
            conversationVm.clearError()
        }
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
                pending.plan.requestsReview -> conversationVm.sendWithTransientContext(
                    text = "检查刚才按这个要求调整后的场景有没有和当前项目事实、时间线或人物状态冲突：${pending.plan.original}",
                    transientContext = workingSceneContextV11(flow.workingScenes),
                )
            }
            pendingCompound = null
        } else if (flow.error != null) {
            pendingCompound = null
        }
    }

    val snapshot = flow.snapshot
    val draft = flow.draft
    val pendingCandidates = if (snapshot != null && draft != null) {
        snapshot.candidateFacts.filter {
            it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING
        }
    } else emptyList()
    val hasPendingResult = if (draft != null && flow.result != null) {
        flow.result!!.chapter.content.isNotBlank() && flow.result!!.chapter.content != draft.content
    } else false
    val quickAction = writingQuickActionV11(flow, hasPendingResult, pendingCandidates.size)
    val externalBusy = canon.active
    val disabled = flow.busy || conversation.isBusy || externalBusy

    Scaffold(
        containerColor = t.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            WritingHeaderV11(
                flow = flow,
                onClose = onClose,
                onStory = { sheet = WritingSheetV11.STORY },
                onRun = { sheet = WritingSheetV11.RUN },
            )
        },
        bottomBar = {
            WritingControllerDockV11(
                flow = flow,
                conversation = conversation,
                externalBusy = externalBusy,
                migrationCount = canon.pendingMigrationCount,
                input = input,
                lastPlan = lastPlan,
                quickAction = quickAction,
                onInput = { input = it },
                onHistory = { sheet = WritingSheetV11.HISTORY },
                onTrace = { showTrace = true },
                onScenes = { sheet = WritingSheetV11.SCENES },
                onMigrationQueue = canonVm::openMigrationQueue,
                onQuickAction = { action -> performQuickActionV11(action, flow, viewModel) },
                onSend = {
                    val clean = input.trim()
                    if (clean.isNotBlank() && !disabled) {
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
                                            PendingCompoundV11(
                                                plan = plan,
                                                sceneConversationSizeBefore = flow.sceneConversation.size,
                                            )
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
        },
    ) { padding ->
        if (flow.isLoading || !flow.ready || snapshot == null || draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = t.accentForeground, strokeWidth = 2.dp)
                    Text("正在准备章节工作台", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text("读取章纲、人物状态、时间线与长期记忆", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    WritingRuntimeStatusV11(
                        events = flow.runEvents,
                        providerLabel = flow.providerLabel,
                        onClick = { sheet = WritingSheetV11.RUN },
                    )
                }
                item { WritingMissionCardV11(snapshot, draft, flow) }
                item {
                    WritingSceneCardV11(
                        scenes = flow.workingScenes.ifEmpty { draft.scenePlan },
                        dirty = flow.sceneDirty,
                        onClick = { sheet = WritingSheetV11.SCENES },
                    )
                }
                item {
                    WritingBodyCardV11(
                        flow = flow,
                        hasPendingResult = hasPendingResult,
                        onEdit = { onEditChapter(draft.novelId, draft.chapterNumber) },
                    )
                }
                if (flow.chapterCommitted || flow.review != null) {
                    item {
                        WritingMemoryCardV11(
                            flow = flow,
                            pending = pendingCandidates,
                            onConfirm = viewModel::confirmCandidateFact,
                            onReject = viewModel::rejectCandidateFact,
                        )
                    }
                }
                item {
                    WritingStoryStateCardV11(
                        snapshot = snapshot,
                        pendingCount = pendingCandidates.size,
                        onClick = { sheet = WritingSheetV11.STORY },
                    )
                }
            }
        }
    }

    when (sheet) {
        WritingSheetV11.SCENES -> if (draft != null) {
            WritingSceneSheetV11(
                flow = flow,
                draftScenes = draft.scenePlan,
                instruction = sceneInstruction,
                onInstruction = { sceneInstruction = it },
                onAdjust = {
                    viewModel.planScenes(sceneInstruction)
                    sceneInstruction = ""
                },
                onApply = viewModel::applyScenePlan,
                onDismiss = { sheet = null },
            )
        }
        WritingSheetV11.STORY -> if (snapshot != null && draft != null) {
            WritingStorySheetV11(snapshot, draft.chapterNumber) { sheet = null }
        }
        WritingSheetV11.RUN -> WritingRunSheetV11(flow.runEvents) { sheet = null }
        WritingSheetV11.HISTORY -> WritingConversationSheetV11(conversation) { sheet = null }
        null -> Unit
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
private fun WritingHeaderV11(
    flow: WritingFlowUiState,
    onClose: () -> Unit,
    onStory: () -> Unit,
    onRun: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val snapshot = flow.snapshot
    val draft = flow.draft
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onClose)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text("章节工作台", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
            Text(
                buildString {
                    append(snapshot?.novel?.title ?: "正在载入")
                    draft?.let { append(" · 第${it.chapterNumber}章"); if (it.title.isNotBlank()) append(" · ${it.title}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LanghuanIconButton(Icons.Rounded.FactCheck, "故事状态", onStory)
        LanghuanIconButton(
            if (flow.runEvents.any { it.status == RunStatus.RUNNING }) Icons.Rounded.AutoAwesome else Icons.Rounded.History,
            "运行轨迹",
            onRun,
            selected = flow.runEvents.any { it.status == RunStatus.RUNNING },
        )
    }
}

@Composable
private fun WritingRuntimeStatusV11(events: List<RunEvent>, providerLabel: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val latestByStage = events.groupBy { it.stage }.mapValues { (_, value) -> value.last() }
    val running = events.lastOrNull { it.status == RunStatus.RUNNING }
    val failed = latestByStage.values.count { it.status == RunStatus.FAILED }
    val warning = latestByStage.values.count { it.status == RunStatus.WARNING }
    val finished = latestByStage.values.count { it.status == RunStatus.SUCCESS || it.status == RunStatus.SKIPPED }
    val label = when {
        running != null -> "Skill OS 正在执行 · ${running.stage.label}"
        failed > 0 -> "Skill OS · $failed 项失败"
        warning > 0 -> "Skill OS 已完成 · $warning 项需注意"
        events.isNotEmpty() -> "Skill OS 已完成 $finished/${latestByStage.size} 项"
        else -> "Skill OS 待命"
    }

    LanghuanCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        contentPadding = 13.dp,
        depth = 0,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running != null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.accentForeground)
            else Icon(
                if (failed > 0) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                null,
                Modifier.size(19.dp),
                tint = if (failed > 0) t.destructive else t.accentForeground,
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = t.foreground)
                Text(
                    providerLabel.ifBlank { "Context、RAG、时间线、一致性与 Agent 按项目状态自动调度" },
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("详情", style = MaterialTheme.typography.labelSmall, color = t.accentForeground)
        }
    }
}

@Composable
private fun WritingMissionCardV11(
    snapshot: StorySnapshot,
    draft: com.xiguli.langhuan.domain.ChapterDraft,
    flow: WritingFlowUiState,
) {
    val t = LocalLanghuanUiTokens.current
    val phase = writingPhaseV11(flow)
    val activeIndex = writingProgressIndexV11(flow)
    val labels = listOf("准备", "正文", "检查", "保存", "记忆")
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 18.dp, depth = 2) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("当前任务", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                Text("第${draft.chapterNumber}章 · ${draft.title}", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
            }
            LanghuanBadge(phase, accent = true)
        }
        if (draft.objective.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(draft.objective, style = MaterialTheme.typography.bodyLarge, color = t.foreground)
        }
        val contract = draft.contract.takeIf { it.purpose.isNotBlank() || it.mustHappen.isNotEmpty() }
            ?: snapshot.activeOutline.lastOrNull()?.chapterContract
        contract?.takeIf { it.purpose.isNotBlank() }?.let {
            Spacer(Modifier.height(7.dp))
            Text("章节合同 · ${it.purpose}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            labels.forEachIndexed { index, label ->
                val completed = index < activeIndex
                val current = index == activeIndex.coerceAtMost(labels.lastIndex)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = when {
                        current -> t.accent
                        completed -> t.muted
                        else -> t.card
                    },
                    contentColor = if (current) t.accentForeground else t.mutedForeground,
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (completed) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = t.success)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingSceneCardV11(scenes: List<ScenePlan>, dirty: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = 15.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(t.radiusSm), color = t.accent) {
                Icon(Icons.Rounded.Route, null, Modifier.padding(10.dp), tint = t.accentForeground)
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("场景计划", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                Text(
                    when {
                        scenes.isEmpty() -> "还没有 ScenePlan，点这里让 AI 编排"
                        dirty -> "${scenes.size} 个场景 · AI 调整结果待确认"
                        else -> "${scenes.size} 个场景 · 已进入本章上下文"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            if (dirty) LanghuanBadge("待确认", accent = true)
            else Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
        }
    }
}

@Composable
private fun WritingBodyCardV11(flow: WritingFlowUiState, hasPendingResult: Boolean, onEdit: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val draft = flow.draft ?: return
    val result = flow.result
    val blocking = result?.issues.orEmpty().filter { it.severity == IssueSeverity.BLOCKING }
    val warnings = result?.issues.orEmpty().filter { it.severity == IssueSeverity.WARNING }

    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 17.dp, depth = 2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(t.radiusSm), color = t.accent) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(10.dp), tint = t.accentForeground)
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(
                    when {
                        flow.isGenerating -> "正在写正文"
                        hasPendingResult -> if (blocking.isEmpty()) "新版本已完成" else "新版本需要修复"
                        flow.chapterCommitted -> "正文已保存"
                        else -> "正文创作"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = t.foreground,
                )
                Text(writingBodyHintV11(flow, hasPendingResult, blocking.size, warnings.size), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            flow.isGenerating -> {
                if (flow.streamPreview.isNotBlank()) WritingTextPreviewV11(flow.streamPreview, 16)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.accentForeground)
                    Text("正在读取上下文并生成正文…", Modifier.padding(start = 9.dp), color = t.mutedForeground)
                }
            }
            hasPendingResult && result != null -> {
                WritingTextPreviewV11(result.chapter.content, 18)
                blocking.take(3).forEach { issue ->
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(t.radiusSm), color = t.destructive.copy(alpha = .08f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(issue.message, fontWeight = FontWeight.SemiBold, color = t.destructive)
                            if (issue.repairInstruction.isNotBlank()) Text(issue.repairInstruction, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                }
                if (blocking.isEmpty() && warnings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("有 ${warnings.size} 条编辑建议，但不会阻止保存。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            flow.chapterCommitted && draft.content.isNotBlank() -> {
                WritingTextPreviewV11(draft.content, 16)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(t.radiusMd)) {
                    Icon(Icons.Rounded.Edit, null)
                    Spacer(Modifier.width(7.dp))
                    Text("精修正文 · 保存后仍可反复修改")
                }
            }
            else -> Text(
                "不用逐项开 Skill。确认场景后直接写；Context、历史召回、人物状态、时间线、时代技术与一致性检查由运行时自动决定是否执行。",
                style = MaterialTheme.typography.bodyMedium,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun WritingTextPreviewV11(text: String, maxLines: Int) {
    val t = LocalLanghuanUiTokens.current
    Surface(shape = RoundedCornerShape(t.radiusMd), color = t.muted) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(15.dp),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
        )
    }
}

@Composable
private fun WritingMemoryCardV11(
    flow: WritingFlowUiState,
    pending: List<com.xiguli.langhuan.domain.CandidateFact>,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Memory, null, tint = t.accentForeground)
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text("章节记忆", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                Text(
                    when {
                        flow.isReviewing -> "Agent 正在整理长期记忆"
                        flow.review == null -> "正文已保存，下一步让 Agent 提取长期事实"
                        pending.isNotEmpty() -> "有 ${pending.size} 条新事实待确认；确认前不会进入 Canon"
                        else -> "本章记忆已整理完成"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            if (pending.isNotEmpty()) LanghuanBadge("${pending.size} 待确认", accent = true)
        }
        flow.review?.summary?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(9.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = t.foreground)
        }
        pending.take(5).forEach { fact ->
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(t.radiusSm), color = t.muted) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(fact.subject, fontWeight = FontWeight.SemiBold, color = t.foreground)
                    if (fact.after.isNotBlank()) Text(fact.after, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onReject(fact.id) }, enabled = !flow.busy, modifier = Modifier.weight(1f)) { Text("忽略") }
                        Button(onClick = { onConfirm(fact.id) }, enabled = !flow.busy, modifier = Modifier.weight(1f)) { Text("确认") }
                    }
                }
            }
        }
        if (pending.size > 5) {
            Spacer(Modifier.height(7.dp))
            Text("还有 ${pending.size - 5} 条候选事实，可继续逐条确认。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}

@Composable
private fun WritingStoryStateCardV11(snapshot: StorySnapshot, pendingCount: Int, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onClick), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FactCheck, null, tint = t.accentForeground)
            Text("故事状态", Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleMedium, color = t.foreground)
            Text("展开", style = MaterialTheme.typography.labelSmall, color = t.accentForeground)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WritingMetricV11("人物", snapshot.characters.size, Modifier.weight(1f))
            WritingMetricV11("时间线", snapshot.recentTimeline.size, Modifier.weight(1f))
            WritingMetricV11("伏笔", snapshot.relevantForeshadowing.size, Modifier.weight(1f))
            WritingMetricV11("候选", pendingCount, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WritingMetricV11(label: String, value: Int, modifier: Modifier) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier = modifier, shape = RoundedCornerShape(t.radiusSm), color = t.muted) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = t.foreground)
            Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
        }
    }
}

@Composable
private fun WritingControllerDockV11(
    flow: WritingFlowUiState,
    conversation: ProjectConversationUiState,
    externalBusy: Boolean,
    migrationCount: Int,
    input: String,
    lastPlan: WorkspaceNaturalPlan?,
    quickAction: WritingQuickActionV11?,
    onInput: (String) -> Unit,
    onHistory: () -> Unit,
    onTrace: () -> Unit,
    onScenes: () -> Unit,
    onMigrationQueue: () -> Unit,
    onQuickAction: (WritingQuickActionV11) -> Unit,
    onSend: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val disabled = flow.busy || conversation.isBusy || externalBusy
    val latestAssistant = conversation.streamingReply.takeIf(String::isNotBlank)
        ?: conversation.messages.lastOrNull { it.role == "assistant" }?.text.orEmpty()

    Column(
        Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (latestAssistant.isNotBlank() || conversation.isBusy) {
            LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onHistory), contentPadding = 10.dp, depth = 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.isBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = t.accentForeground)
                    else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp), tint = t.accentForeground)
                    Text(
                        if (conversation.isBusy && latestAssistant.isBlank()) conversation.routeSummary.ifBlank { "琅嬛正在结合当前项目分析…" } else latestAssistant,
                        Modifier.padding(start = 8.dp).weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Rounded.History, "会话", Modifier.size(17.dp), tint = t.mutedForeground)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WritingToolPillV11(Icons.Rounded.Route, "场景", onScenes)
            WritingToolPillV11(Icons.Rounded.History, "会话", onHistory)
            WritingToolPillV11(Icons.Rounded.AccountTree, "流程", onTrace)
            if (migrationCount > 0) WritingToolPillV11(Icons.Rounded.BuildCircle, "修复队列 $migrationCount", onMigrationQueue, accent = true)
            lastPlan?.let { LanghuanBadge(it.summary) }
        }

        quickAction?.let { action ->
            Button(
                onClick = { onQuickAction(action) },
                enabled = action == WritingQuickActionV11.STOP || !flow.busy,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(t.radiusMd),
                colors = if (action == WritingQuickActionV11.STOP) {
                    ButtonDefaults.buttonColors(containerColor = t.destructive.copy(alpha = .12f), contentColor = t.destructive)
                } else ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.accentForeground),
            ) {
                Icon(if (action == WritingQuickActionV11.STOP) Icons.Rounded.StopCircle else Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(action.label, fontWeight = FontWeight.SemiBold)
            }
        }

        LanghuanGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(5.dp),
            radius = 26.dp,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("直接说：聊设定、改 Canon、调场景、改正文、检查冲突都可以…") },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !disabled,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedTextColor = t.foreground,
                        unfocusedTextColor = t.foreground,
                    ),
                )
                val canSend = input.isNotBlank() && !disabled
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (canSend) t.foreground else t.muted,
                    contentColor = if (canSend) t.primaryForeground else t.mutedForeground,
                ) {
                    Box(Modifier.clickable(enabled = canSend, onClick = onSend), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowUpward, "发送", Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingToolPillV11(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (accent) t.accent else t.muted,
        contentColor = if (accent) t.accentForeground else t.foreground,
    ) {
        Row(Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritingSceneSheetV11(
    flow: WritingFlowUiState,
    draftScenes: List<ScenePlan>,
    instruction: String,
    onInstruction: (String) -> Unit,
    onAdjust: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val scenes = flow.workingScenes.ifEmpty { draftScenes }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本章场景", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
            Text(
                if (flow.sceneDirty) "AI 已调整 ${scenes.size} 个场景；确认前不会覆盖正式章节结构。" else "这里是本章真实 ScenePlan，正文生成会直接读取它。",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
            if (flow.sceneNote.isNotBlank()) Text(flow.sceneNote, style = MaterialTheme.typography.bodySmall, color = t.accentForeground)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(scenes.sortedBy { it.order }, key = { it.order }) { scene -> WritingSceneItemV11(scene) }
                if (scenes.isEmpty()) item { Text("还没有场景计划。输入要求后让 AI 生成。", color = t.mutedForeground) }
                if (flow.sceneConversation.isNotEmpty()) {
                    item { Text("最近调整", style = MaterialTheme.typography.titleSmall, color = t.foreground) }
                    items(flow.sceneConversation.takeLast(4), key = { it.id }) { message ->
                        Text((if (message.role == "user") "你 · " else "琅嬛 · ") + message.text, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }
            TextField(
                value = instruction,
                onValueChange = onInstruction,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("告诉 AI 怎么调整：例如第三场提前到傍晚、不要闪回、让配角更早入场") },
                minLines = 2,
                maxLines = 4,
                enabled = !flow.busy,
                shape = RoundedCornerShape(t.radiusMd),
                colors = filledTextFieldColorsV11(t),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAdjust, enabled = !flow.busy && instruction.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (flow.isPlanningScenes) "调整中" else "AI 调整")
                }
                Button(onClick = onApply, enabled = flow.sceneDirty && !flow.busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.CheckCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (flow.sceneDirty) "确认场景" else "已确认")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WritingSceneItemV11(scene: ScenePlan) {
    val t = LocalLanghuanUiTokens.current
    Surface(shape = RoundedCornerShape(t.radiusMd), color = t.muted) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("场景 ${scene.order}", color = t.accentForeground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (scene.location.isNotBlank()) Text(scene.location, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
            }
            if (scene.purpose.isNotBlank()) Text(scene.purpose, color = t.foreground, fontWeight = FontWeight.SemiBold)
            if (scene.conflict.isNotBlank()) Text("冲突 · ${scene.conflict}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            if (scene.outcome.isNotBlank()) Text("结果 · ${scene.outcome}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritingStorySheetV11(snapshot: StorySnapshot, chapterNumber: Int, onDismiss: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val pending = snapshot.candidateFacts.filter { it.sourceChapter == chapterNumber && it.status == CandidateFactStatus.PENDING }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.86f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("故事状态", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
                Text("这是本章真实项目上下文，不是装饰性的 Skill 开关。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            snapshot.activeOutline.lastOrNull()?.let { outline ->
                item {
                    WritingSheetSectionV11("当前章纲") {
                        Text(outline.title, fontWeight = FontWeight.SemiBold, color = t.foreground)
                        if (outline.objective.isNotBlank()) Text("目标 · ${outline.objective}", style = MaterialTheme.typography.bodySmall, color = t.foreground)
                        if (outline.conflict.isNotBlank()) Text("冲突 · ${outline.conflict}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        if (outline.turningPoint.isNotBlank()) Text("转折 · ${outline.turningPoint}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }
            item {
                WritingSheetSectionV11("人物 · ${snapshot.characters.size}") {
                    if (snapshot.characters.isEmpty()) Text("暂无人物状态", color = t.mutedForeground)
                    else snapshot.characters.take(8).forEach { character ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(character.name, fontWeight = FontWeight.SemiBold, color = t.foreground)
                            Text(listOf(character.location, character.emotionalState, character.goal).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                }
            }
            item {
                WritingSheetSectionV11("最近时间线 · ${snapshot.recentTimeline.size}") {
                    if (snapshot.recentTimeline.isEmpty()) Text("暂无时间线事件", color = t.mutedForeground)
                    else snapshot.recentTimeline.takeLast(6).forEach { event ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("第${event.chapter}章 · ${event.storyTime.ifBlank { event.timeOfDay.ifBlank { "时间未锁定" } }}", fontWeight = FontWeight.SemiBold, color = t.foreground)
                            Text(event.summary, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                }
            }
            item {
                WritingSheetSectionV11("相关伏笔 · ${snapshot.relevantForeshadowing.size}") {
                    if (snapshot.relevantForeshadowing.isEmpty()) Text("本章没有需要触碰的伏笔", color = t.mutedForeground)
                    else snapshot.relevantForeshadowing.take(6).forEach { clue ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(clue.title, fontWeight = FontWeight.SemiBold, color = t.foreground)
                            Text(clue.detail, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                WritingSheetSectionV11("Candidate · ${pending.size}") {
                    if (pending.isEmpty()) Text("当前章节没有待确认事实", color = t.mutedForeground)
                    else pending.take(8).forEach { fact ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(fact.subject, fontWeight = FontWeight.SemiBold, color = t.foreground)
                            Text(fact.after, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingSheetSectionV11(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritingRunSheetV11(events: List<RunEvent>, onDismiss: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Skill OS 执行轨迹", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
            Text("这里只展示真实 RunEvent；计划启用不等于真正执行。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            if (events.isEmpty()) {
                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text("本章还没有运行记录。开始生成后，真实计划与回执会出现在这里。", color = t.mutedForeground)
                }
            } else {
                RunInspectorPanel(events, "本章执行轨迹")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WritingConversationSheetV11(state: ProjectConversationUiState, onDismiss: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.86f).navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("连续创作会话", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
                    Text("讨论与执行指令会保留；只有明确确认的事实才进入 Canon。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge("${state.messages.size} 条")
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    val user = message.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Surface(
                            modifier = Modifier.widthIn(max = 330.dp),
                            shape = RoundedCornerShape(t.radiusMd),
                            color = if (user) t.accent else t.muted,
                            contentColor = t.foreground,
                        ) {
                            Text(message.text, Modifier.padding(horizontal = 13.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium, color = t.foreground)
                        }
                    }
                }
                if (state.streamingReply.isNotBlank()) item {
                    Row(verticalAlignment = Alignment.Top) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = t.accentForeground)
                        Text(state.streamingReply, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium, color = t.foreground)
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("回到写作") }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun writingQuickActionV11(state: WritingFlowUiState, hasPendingResult: Boolean, pendingCandidateCount: Int): WritingQuickActionV11? = when {
    state.isGenerating -> WritingQuickActionV11.STOP
    state.busy -> null
    hasPendingResult && state.result?.canCommit == false -> WritingQuickActionV11.REPAIR
    hasPendingResult && state.result?.canCommit == true -> WritingQuickActionV11.SAVE
    state.sceneDirty && !state.chapterCommitted -> WritingQuickActionV11.APPLY_SCENE
    state.chapterCommitted && state.review == null -> WritingQuickActionV11.REVIEW_MEMORY
    state.review != null && pendingCandidateCount == 0 -> WritingQuickActionV11.NEXT
    !state.chapterCommitted && state.result == null -> WritingQuickActionV11.GENERATE
    else -> null
}

private fun performQuickActionV11(action: WritingQuickActionV11, state: WritingFlowUiState, viewModel: WritingFlowViewModel) {
    when (action) {
        WritingQuickActionV11.STOP -> viewModel.cancelGeneration()
        WritingQuickActionV11.REPAIR -> viewModel.repairAndRegenerate()
        WritingQuickActionV11.SAVE -> viewModel.commitAndReview()
        WritingQuickActionV11.APPLY_SCENE -> viewModel.applyScenePlan()
        WritingQuickActionV11.REVIEW_MEMORY -> viewModel.reviewCommittedChapter()
        WritingQuickActionV11.NEXT -> viewModel.advanceToNext(null)
        WritingQuickActionV11.GENERATE -> if (!state.busy) viewModel.generate("")
    }
}

private fun writingPhaseV11(state: WritingFlowUiState): String = when {
    state.isPlanningScenes -> "规划场景"
    state.isGenerating -> "生成正文"
    state.isSaving -> "保存"
    state.isReviewing -> "整理记忆"
    state.result?.canCommit == false -> "修复"
    state.result?.canCommit == true && !state.chapterCommitted -> "待保存"
    state.chapterCommitted && state.review == null -> "待整理记忆"
    state.review != null -> "本章完成"
    state.sceneDirty -> "场景待确认"
    else -> "准备"
}

private fun writingProgressIndexV11(state: WritingFlowUiState): Int = when {
    state.isReviewing || state.review != null -> 4
    state.isSaving || state.chapterCommitted -> 3
    state.result != null -> 2
    state.isGenerating || state.streamPreview.isNotBlank() -> 1
    else -> 0
}

private fun writingBodyHintV11(state: WritingFlowUiState, hasPendingResult: Boolean, blocking: Int, warnings: Int): String = when {
    state.isGenerating -> "正在流式生成；可随时停止，未保存内容不会进入正式章节。"
    hasPendingResult && blocking > 0 -> "$blocking 个阻塞问题 · 修复后才能保存"
    hasPendingResult && warnings > 0 -> "$warnings 条建议 · 当前版本允许保存"
    hasPendingResult -> "检查已通过 · 可以保存并进入记忆整理"
    state.chapterCommitted -> "正文已经持久化；仍可进入编辑器反复修改，不会锁死。"
    else -> "确认 ScenePlan 后开始生成，或直接在底部用自然语言告诉琅嬛怎么写。"
}

private fun workingSceneContextV11(scenes: List<ScenePlan>): String = buildString {
    appendLine("当前尚未保存的 working ScenePlan：")
    scenes.sortedBy { it.order }.forEach { scene ->
        appendLine("- 场景${scene.order}｜地点：${scene.location}｜目的：${scene.purpose}｜冲突：${scene.conflict}｜结果：${scene.outcome}")
    }
}.trim()

@Composable
private fun filledTextFieldColorsV11(t: com.xiguli.langhuan.ui.design.LanghuanUiTokens) = TextFieldDefaults.colors(
    focusedContainerColor = t.muted,
    unfocusedContainerColor = t.muted,
    disabledContainerColor = t.muted.copy(alpha = .55f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = t.foreground,
    unfocusedTextColor = t.foreground,
    focusedPlaceholderColor = t.mutedForeground,
    unfocusedPlaceholderColor = t.mutedForeground,
)
