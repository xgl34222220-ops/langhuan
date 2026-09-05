package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.ui.agent.RunInspectorPanel
import com.xiguli.langhuan.ui.theme.LanghuanShape
import com.xiguli.langhuan.ui.theme.LocalLanghuanTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

private enum class WorkspaceSheetV4 { SCENES, STORY, RUN }
private enum class WorkspaceComposerModeV4 { PROSE, SCENE }
private enum class WorkspacePrimaryActionV4 { GENERATE, STOP, REPAIR, SAVE, REVIEW, NEXT }

/**
 * Novel Skill OS V4 chapter workspace.
 *
 * V3 already owns the real generation/runtime engines. This layer deliberately does not
 * duplicate routing, Canon, RAG, or consistency logic. It keeps the current chapter task,
 * story state, scene plan and Run receipt in one place and only calls the existing ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingWorkspaceV4(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<WorkspaceSheetV4?>(null) }
    var composerMode by remember { mutableStateOf(WorkspaceComposerModeV4.PROSE) }
    var input by remember { mutableStateOf("") }
    var sceneInstruction by remember { mutableStateOf("") }

    LaunchedEffect(novelId) { viewModel.load(novelId) }
    LaunchedEffect(state.draft?.chapterNumber) {
        input = ""
        composerMode = WorkspaceComposerModeV4.PROSE
    }
    LaunchedEffect(state.message, state.error) {
        val notice = state.error ?: state.message
        if (!notice.isNullOrBlank()) {
            snackbar.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    val snapshot = state.snapshot
    val draft = state.draft
    val pendingCandidates = if (snapshot != null && draft != null) {
        snapshot.candidateFacts.filter {
            it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING
        }
    } else emptyList()
    val hasPendingResult = if (draft != null && state.result != null) {
        state.result!!.chapter.content.isNotBlank() && state.result!!.chapter.content != draft.content
    } else false
    val primaryAction = workspacePrimaryActionV4(state, hasPendingResult, pendingCandidates.size)

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .16f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    )
                )
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            WorkspaceHeaderV4(
                snapshot = snapshot,
                chapterNumber = draft?.chapterNumber,
                chapterTitle = draft?.title.orEmpty(),
                state = state,
                onBack = onClose,
                onStory = { sheet = WorkspaceSheetV4.STORY },
                onRun = { sheet = WorkspaceSheetV4.RUN },
            )

            if (state.isLoading || !state.ready || snapshot == null || draft == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("正在准备章节工作台", fontWeight = FontWeight.Bold)
                        Text(
                            "读取章纲、人物状态、时间线与长期记忆",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLanghuanTokens.current.textSecondary,
                        )
                    }
                }
            } else {
                WorkspaceSkillRibbonV4(
                    events = state.runEvents,
                    providerLabel = state.providerLabel,
                    onClick = { sheet = WorkspaceSheetV4.RUN },
                )

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        WorkspaceMissionCardV4(
                            snapshot = snapshot,
                            draft = draft,
                            state = state,
                        )
                    }
                    item {
                        WorkspaceSceneSummaryV4(
                            scenes = state.workingScenes.ifEmpty { draft.scenePlan },
                            dirty = state.sceneDirty,
                            onClick = { sheet = WorkspaceSheetV4.SCENES },
                        )
                    }
                    item {
                        WorkspaceBodyCardV4(
                            state = state,
                            hasPendingResult = hasPendingResult,
                            onEdit = { onEditChapter(draft.novelId, draft.chapterNumber) },
                        )
                    }
                    if (state.chapterCommitted || state.review != null) {
                        item {
                            WorkspaceMemoryCardV4(
                                state = state,
                                pendingCount = pendingCandidates.size,
                                onConfirm = viewModel::confirmCandidateFact,
                                onReject = viewModel::rejectCandidateFact,
                            )
                        }
                    }
                    item {
                        WorkspaceStoryStatePreviewV4(
                            snapshot = snapshot,
                            pendingCount = pendingCandidates.size,
                            onClick = { sheet = WorkspaceSheetV4.STORY },
                        )
                    }
                }

                WorkspaceComposerDockV4(
                    state = state,
                    mode = composerMode,
                    input = input,
                    pendingCandidates = pendingCandidates.size,
                    primaryAction = primaryAction,
                    onMode = { composerMode = it },
                    onInput = { input = it },
                    onOpenScenes = { sheet = WorkspaceSheetV4.SCENES },
                    onPrimaryAction = {
                        when (primaryAction) {
                            WorkspacePrimaryActionV4.GENERATE -> viewModel.generate("")
                            WorkspacePrimaryActionV4.STOP -> viewModel.cancelGeneration()
                            WorkspacePrimaryActionV4.REPAIR -> viewModel.repairAndRegenerate()
                            WorkspacePrimaryActionV4.SAVE -> viewModel.commitAndReview()
                            WorkspacePrimaryActionV4.REVIEW -> viewModel.reviewCommittedChapter()
                            WorkspacePrimaryActionV4.NEXT -> viewModel.advanceToNext(null)
                            null -> Unit
                        }
                    },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotBlank() && !state.busy) {
                            input = ""
                            when (composerMode) {
                                WorkspaceComposerModeV4.PROSE -> viewModel.generate(text)
                                WorkspaceComposerModeV4.SCENE -> viewModel.planScenes(text)
                            }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 118.dp),
        )
    }

    when (sheet) {
        WorkspaceSheetV4.SCENES -> {
            val currentDraft = state.draft
            if (currentDraft != null) {
                WorkspaceSceneSheetV4(
                    state = state,
                    draftScenes = currentDraft.scenePlan,
                    instruction = sceneInstruction,
                    onInstruction = { sceneInstruction = it },
                    onDismiss = { sheet = null },
                    onAdjust = {
                        viewModel.planScenes(sceneInstruction)
                        sceneInstruction = ""
                    },
                    onApply = viewModel::applyScenePlan,
                )
            } else sheet = null
        }
        WorkspaceSheetV4.STORY -> {
            if (snapshot != null && draft != null) {
                WorkspaceStorySheetV4(
                    snapshot = snapshot,
                    chapterNumber = draft.chapterNumber,
                    onDismiss = { sheet = null },
                )
            } else sheet = null
        }
        WorkspaceSheetV4.RUN -> WorkspaceRunSheetV4(state.runEvents) { sheet = null }
        null -> Unit
    }
}

@Composable
private fun WorkspaceHeaderV4(
    snapshot: StorySnapshot?,
    chapterNumber: Int?,
    chapterTitle: String,
    state: WritingFlowUiState,
    onBack: () -> Unit,
    onStory: () -> Unit,
    onRun: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceRoundButtonV4(Icons.Rounded.ArrowBack, "返回", onBack)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("章节工作台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append(snapshot?.novel?.title ?: "正在载入")
                    if (chapterNumber != null) append(" · 第${chapterNumber}章")
                    if (chapterTitle.isNotBlank()) append(" · $chapterTitle")
                },
                style = MaterialTheme.typography.labelSmall,
                color = LocalLanghuanTokens.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        WorkspaceRoundButtonV4(Icons.Rounded.FactCheck, "故事状态", onStory)
        Spacer(Modifier.width(7.dp))
        WorkspaceRoundButtonV4(
            if (state.runEvents.any { it.status == RunStatus.RUNNING }) Icons.Rounded.AutoAwesome else Icons.Rounded.History,
            "运行轨迹",
            onRun,
        )
    }
}

@Composable
private fun WorkspaceRoundButtonV4(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .9f), CircleShape)
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .52f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp))
    }
}

@Composable
private fun WorkspaceSkillRibbonV4(
    events: List<RunEvent>,
    providerLabel: String,
    onClick: () -> Unit,
) {
    val latestByStage = events.groupBy { it.stage }.mapValues { (_, value) -> value.last() }
    val running = events.lastOrNull { it.status == RunStatus.RUNNING }
    val finished = latestByStage.values.count { it.status == RunStatus.SUCCESS || it.status == RunStatus.SKIPPED }
    val failed = latestByStage.values.count { it.status == RunStatus.FAILED }
    val warning = latestByStage.values.count { it.status == RunStatus.WARNING }
    val statusText = when {
        running != null -> "正在执行 · ${running.stage.label}"
        failed > 0 -> "本轮有 $failed 项失败"
        warning > 0 -> "本轮完成 · $warning 项需注意"
        events.isNotEmpty() -> "本轮已记录 $finished/${latestByStage.size} 项"
        else -> "Skill OS 待命"
    }
    val tint = when {
        running != null -> MaterialTheme.colorScheme.primary
        failed > 0 -> MaterialTheme.colorScheme.error
        warning > 0 -> LocalLanghuanTokens.current.warning
        events.isNotEmpty() -> LocalLanghuanTokens.current.success
        else -> LocalLanghuanTokens.current.textSecondary
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .squircleClip(18.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .82f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), LanghuanShape.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (running != null) {
            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(
                if (failed > 0) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                null,
                Modifier.size(18.dp),
                tint = tint,
            )
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(statusText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                providerLabel.ifBlank { "上下文、时间线、一致性与 Agent 会按项目状态自动调度" },
                style = MaterialTheme.typography.labelSmall,
                color = LocalLanghuanTokens.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ExpandMore, "展开", Modifier.size(18.dp), tint = LocalLanghuanTokens.current.textSecondary)
    }
}

@Composable
private fun WorkspaceMissionCardV4(
    snapshot: StorySnapshot,
    draft: com.xiguli.langhuan.domain.ChapterDraft,
    state: WritingFlowUiState,
) {
    val phase = workspacePhaseV4(state)
    val activeIndex = workspaceProgressIndexV4(state)
    val labels = listOf("准备", "正文", "检查", "保存", "记忆")

    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .48f),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("当前任务", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("第${draft.chapterNumber}章 · ${draft.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                AssistChip(onClick = {}, label = { Text(phase) })
            }
            if (draft.objective.isNotBlank()) {
                Text(draft.objective, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val contract = draft.contract.takeIf { it.purpose.isNotBlank() || it.mustHappen.isNotEmpty() }
                ?: snapshot.activeOutline.lastOrNull()?.chapterContract
            contract?.takeIf { it.purpose.isNotBlank() }?.let {
                Text("章节合同 · ${it.purpose}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                labels.forEachIndexed { index, label ->
                    val completed = index < activeIndex
                    val current = index == activeIndex.coerceAtMost(labels.lastIndex)
                    Surface(
                        shape = LanghuanShape.pill,
                        color = when {
                            completed -> MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                            current -> MaterialTheme.colorScheme.surface.copy(alpha = .8f)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = .45f)
                        },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (completed) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                                color = if (completed || current) MaterialTheme.colorScheme.primary else LocalLanghuanTokens.current.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSceneSummaryV4(
    scenes: List<ScenePlan>,
    dirty: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(24.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .9f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)) {
                Icon(Icons.Rounded.Route, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("场景计划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        scenes.isEmpty() -> "还没有场景计划，点这里让 AI 编排"
                        dirty -> "${scenes.size} 个场景 · AI 调整结果待确认"
                        else -> "${scenes.size} 个场景 · 已进入本章上下文"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalLanghuanTokens.current.textSecondary,
                )
            }
            if (dirty) {
                AssistChip(onClick = onClick, label = { Text("待确认") })
            } else {
                Icon(Icons.Rounded.ExpandMore, null, tint = LocalLanghuanTokens.current.textSecondary)
            }
        }
    }
}

@Composable
private fun WorkspaceBodyCardV4(
    state: WritingFlowUiState,
    hasPendingResult: Boolean,
    onEdit: () -> Unit,
) {
    val draft = state.draft ?: return
    val result = state.result
    val blocking = result?.issues.orEmpty().filter { it.severity == IssueSeverity.BLOCKING }
    val warnings = result?.issues.orEmpty().filter { it.severity == IssueSeverity.WARNING }

    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = LanghuanShape.cover, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(
                        when {
                            state.isGenerating -> "正在写正文"
                            hasPendingResult -> if (blocking.isEmpty()) "新版本已完成" else "新版本需要修复"
                            state.chapterCommitted -> "正文已保存"
                            else -> "正文创作"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        workspaceBodyHintV4(state, hasPendingResult, blocking.size, warnings.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLanghuanTokens.current.textSecondary,
                    )
                }
            }

            when {
                state.isGenerating -> {
                    if (state.streamPreview.isNotBlank()) {
                        WorkspaceTextPreviewV4(state.streamPreview, maxLines = 16)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("正在读取上下文并生成正文…", modifier = Modifier.padding(start = 9.dp), color = LocalLanghuanTokens.current.textSecondary)
                        }
                    }
                }
                hasPendingResult && result != null -> {
                    WorkspaceTextPreviewV4(result.chapter.content, maxLines = 18)
                    blocking.take(3).forEach { issue ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().squircleClip(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .42f),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(issue.message, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                                if (issue.repairInstruction.isNotBlank()) {
                                    Text(issue.repairInstruction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .78f))
                                }
                            }
                        }
                    }
                    if (blocking.isEmpty() && warnings.isNotEmpty()) {
                        Text("有 ${warnings.size} 条编辑建议，但不会阻止保存。", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                    }
                }
                state.chapterCommitted && draft.content.isNotBlank() -> {
                    WorkspaceTextPreviewV4(draft.content, maxLines = 16)
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = LanghuanShape.card) {
                        Icon(Icons.Rounded.Edit, null)
                        Spacer(Modifier.width(7.dp))
                        Text("精修正文 · 保存后仍可反复修改")
                    }
                }
                else -> {
                    Text(
                        "不用再逐项开 Skill。确认场景后直接写；Context、历史召回、人物状态、时间线、时代技术与一致性检查会由运行时自动决定是否启用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTextPreviewV4(text: String, maxLines: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .64f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(15.dp),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 23.sp,
        )
    }
}

@Composable
private fun WorkspaceMemoryCardV4(
    state: WritingFlowUiState,
    pendingCount: Int,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    val snapshot = state.snapshot ?: return
    val draft = state.draft ?: return
    val pending = snapshot.candidateFacts.filter {
        it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING
    }

    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .92f),
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("章节记忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            state.isReviewing -> "Agent 正在整理长期记忆"
                            state.review == null -> "正文已保存，下一步让 Agent 提取长期事实"
                            pendingCount > 0 -> "有 $pendingCount 条新事实待确认；确认前不会进入 Canon"
                            else -> "本章记忆已整理完成"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLanghuanTokens.current.textSecondary,
                    )
                }
            }

            state.review?.summary?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            pending.take(5).forEach { fact ->
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .72f),
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(fact.subject, fontWeight = FontWeight.Bold)
                        if (fact.after.isNotBlank()) Text(fact.after, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onReject(fact.id) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("忽略") }
                            Button(
                                onClick = { onConfirm(fact.id) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("确认") }
                        }
                    }
                }
            }
            if (pending.size > 5) {
                Text("还有 ${pending.size - 5} 条候选事实，可继续逐条确认。", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
            }
        }
    }
}

@Composable
private fun WorkspaceStoryStatePreviewV4(
    snapshot: StorySnapshot,
    pendingCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(24.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .94f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
                Text("故事状态", modifier = Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkspaceMetricV4("人物", snapshot.characters.size.toString(), Modifier.weight(1f))
                WorkspaceMetricV4("时间线", snapshot.recentTimeline.size.toString(), Modifier.weight(1f))
                WorkspaceMetricV4("伏笔", snapshot.relevantForeshadowing.size.toString(), Modifier.weight(1f))
                WorkspaceMetricV4("候选", pendingCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WorkspaceMetricV4(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.squircleClip(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .52f)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
        }
    }
}

@Composable
private fun WorkspaceComposerDockV4(
    state: WritingFlowUiState,
    mode: WorkspaceComposerModeV4,
    input: String,
    pendingCandidates: Int,
    primaryAction: WorkspacePrimaryActionV4?,
    onMode: (WorkspaceComposerModeV4) -> Unit,
    onInput: (String) -> Unit,
    onOpenScenes: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkspaceModeChipV4("写正文", mode == WorkspaceComposerModeV4.PROSE) { onMode(WorkspaceComposerModeV4.PROSE) }
            WorkspaceModeChipV4("调场景", mode == WorkspaceComposerModeV4.SCENE) { onMode(WorkspaceComposerModeV4.SCENE) }
            if (state.sceneDirty) {
                WorkspaceModeChipV4("场景待确认", selected = true, warning = true, onClick = onOpenScenes)
            }
            if (pendingCandidates > 0) {
                AssistChip(onClick = {}, label = { Text("$pendingCandidates 条事实待确认") })
            }
        }

        primaryAction?.let { action ->
            FilledTonalButton(
                onClick = onPrimaryAction,
                enabled = action == WorkspacePrimaryActionV4.STOP || !state.busy,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = LanghuanShape.card,
            ) {
                Icon(workspacePrimaryActionIconV4(action), null, Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text(workspacePrimaryActionLabelV4(action), fontWeight = FontWeight.SemiBold)
            }
        }

        val shape = LanghuanShape.sheet
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, clip = false)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f), shape)
                .border(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), shape)
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onOpenScenes, enabled = !state.isGenerating) {
                Icon(Icons.Rounded.Route, "场景")
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 118.dp).padding(horizontal = 3.dp, vertical = 11.dp),
                enabled = !state.busy,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (input.isBlank()) {
                            Text(
                                if (mode == WorkspaceComposerModeV4.SCENE) "告诉 AI 这一章的场景怎么改…" else "直接告诉琅嬛这一章要怎么写或怎么改…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (input.isNotBlank() && !state.busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    )
                    .clickable(enabled = input.isNotBlank() && !state.busy, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Send,
                    "发送",
                    tint = if (input.isNotBlank() && !state.busy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceModeChipV4(
    label: String,
    selected: Boolean,
    warning: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        warning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f)
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .74f)
        else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .8f)
    }
    val foreground = when {
        warning -> MaterialTheme.colorScheme.onErrorContainer
        selected -> MaterialTheme.colorScheme.primary
        else -> LocalLanghuanTokens.current.textSecondary
    }
    Surface(shape = LanghuanShape.pill, color = background, modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, color = foreground, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceSceneSheetV4(
    state: WritingFlowUiState,
    draftScenes: List<ScenePlan>,
    instruction: String,
    onInstruction: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdjust: () -> Unit,
    onApply: () -> Unit,
) {
    val scenes = state.workingScenes.ifEmpty { draftScenes }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本章场景", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (state.sceneDirty) "AI 已调整 ${scenes.size} 个场景；确认前不会覆盖正式章节结构。" else "这里是本章真实 ScenePlan，正文生成会直接读取它。",
                color = LocalLanghuanTokens.current.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.sceneNote.isNotBlank()) {
                Text(state.sceneNote, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scenes.sortedBy { it.order }.forEach { scene ->
                    item(key = "scene-${scene.order}") { WorkspaceSceneCardV4(scene) }
                }
                if (scenes.isEmpty()) {
                    item { Text("还没有场景计划。输入要求后让 AI 生成。", color = LocalLanghuanTokens.current.textSecondary) }
                }
                if (state.sceneConversation.isNotEmpty()) {
                    item {
                        Text("最近调整", modifier = Modifier.padding(top = 5.dp), fontWeight = FontWeight.Bold)
                    }
                    state.sceneConversation.takeLast(4).forEach { message ->
                        item(key = message.id) {
                            Text(
                                (if (message.role == "user") "你 · " else "琅嬛 · ") + message.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalLanghuanTokens.current.textSecondary,
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = instruction,
                onValueChange = onInstruction,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("告诉 AI 怎么调整") },
                placeholder = { Text("例如：第三场提前到傍晚；不要闪回；让配角更早入场") },
                minLines = 2,
                maxLines = 4,
                shape = LanghuanShape.card,
                enabled = !state.busy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAdjust,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                    shape = LanghuanShape.card,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isPlanningScenes) "调整中" else "AI 调整")
                }
                Button(
                    onClick = onApply,
                    enabled = state.sceneDirty && !state.busy,
                    modifier = Modifier.weight(1f),
                    shape = LanghuanShape.card,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.sceneDirty) "确认场景" else "已确认")
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun WorkspaceSceneCardV4(scene: ScenePlan) {
    Surface(modifier = Modifier.fillMaxWidth().squircleClip(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .66f)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("场景 ${scene.order}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (scene.location.isNotBlank()) Text(scene.location, style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
            }
            if (scene.purpose.isNotBlank()) Text(scene.purpose, fontWeight = FontWeight.SemiBold)
            if (scene.conflict.isNotBlank()) Text("冲突 · ${scene.conflict}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
            if (scene.outcome.isNotBlank()) Text("结果 · ${scene.outcome}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceStorySheetV4(
    snapshot: StorySnapshot,
    chapterNumber: Int,
    onDismiss: () -> Unit,
) {
    val pending = snapshot.candidateFacts.filter { it.sourceChapter == chapterNumber && it.status == CandidateFactStatus.PENDING }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.86f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("故事状态", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("这是本章实际可读的项目上下文，不是 Skill 开关。", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
            }
            snapshot.activeOutline.lastOrNull()?.let { outline ->
                item {
                    WorkspaceSheetSectionV4("当前章纲") {
                        Text(outline.title, fontWeight = FontWeight.Bold)
                        if (outline.objective.isNotBlank()) Text("目标 · ${outline.objective}", style = MaterialTheme.typography.bodySmall)
                        if (outline.conflict.isNotBlank()) Text("冲突 · ${outline.conflict}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                        if (outline.turningPoint.isNotBlank()) Text("转折 · ${outline.turningPoint}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                    }
                }
            }
            item {
                WorkspaceSheetSectionV4("人物 · ${snapshot.characters.size}") {
                    if (snapshot.characters.isEmpty()) {
                        Text("暂无人物状态", color = LocalLanghuanTokens.current.textSecondary)
                    } else {
                        snapshot.characters.take(8).forEach { character ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(character.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(character.location, character.emotionalState, character.goal).filter(String::isNotBlank).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalLanghuanTokens.current.textSecondary,
                                )
                            }
                        }
                        if (snapshot.characters.size > 8) Text("另有 ${snapshot.characters.size - 8} 名人物状态", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                    }
                }
            }
            item {
                WorkspaceSheetSectionV4("最近时间线 · ${snapshot.recentTimeline.size}") {
                    if (snapshot.recentTimeline.isEmpty()) {
                        Text("暂无时间线事件", color = LocalLanghuanTokens.current.textSecondary)
                    } else {
                        snapshot.recentTimeline.takeLast(6).forEach { event ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("第${event.chapter}章 · ${event.storyTime.ifBlank { event.timeOfDay.ifBlank { "时间未锁定" } }}", fontWeight = FontWeight.SemiBold)
                                Text(event.summary, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                            }
                        }
                    }
                }
            }
            item {
                WorkspaceSheetSectionV4("相关伏笔 · ${snapshot.relevantForeshadowing.size}") {
                    if (snapshot.relevantForeshadowing.isEmpty()) {
                        Text("本章没有需要触碰的伏笔", color = LocalLanghuanTokens.current.textSecondary)
                    } else {
                        snapshot.relevantForeshadowing.take(6).forEach { clue ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(clue.title, fontWeight = FontWeight.SemiBold)
                                Text(clue.detail, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                WorkspaceSheetSectionV4("Candidate · ${pending.size}") {
                    if (pending.isEmpty()) {
                        Text("当前章节没有待确认事实", color = LocalLanghuanTokens.current.textSecondary)
                    } else {
                        pending.take(8).forEach { fact ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(fact.subject, fontWeight = FontWeight.SemiBold)
                                Text(fact.after, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSheetSectionV4(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().squircleClip(22.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .9f)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceRunSheetV4(events: List<RunEvent>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Skill OS 执行轨迹", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "这里只展示真实 RunEvent。已执行、主动跳过、失败和未执行不会混成同一个“已启用”。",
                style = MaterialTheme.typography.bodySmall,
                color = LocalLanghuanTokens.current.textSecondary,
            )
            if (events.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth().squircleClip(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text("本章还没有运行记录。开始生成后，V3 Runtime 的计划与回执会出现在这里。", modifier = Modifier.padding(16.dp), color = LocalLanghuanTokens.current.textSecondary)
                }
            } else {
                RunInspectorPanel(events, "本章执行轨迹")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun workspacePrimaryActionV4(
    state: WritingFlowUiState,
    hasPendingResult: Boolean,
    pendingCandidateCount: Int,
): WorkspacePrimaryActionV4? = when {
    state.isGenerating -> WorkspacePrimaryActionV4.STOP
    state.busy -> null
    hasPendingResult && state.result?.canCommit == false -> WorkspacePrimaryActionV4.REPAIR
    hasPendingResult && state.result?.canCommit == true -> WorkspacePrimaryActionV4.SAVE
    state.chapterCommitted && state.review == null -> WorkspacePrimaryActionV4.REVIEW
    state.review != null && pendingCandidateCount == 0 -> WorkspacePrimaryActionV4.NEXT
    !state.chapterCommitted -> WorkspacePrimaryActionV4.GENERATE
    else -> null
}

private fun workspacePrimaryActionLabelV4(action: WorkspacePrimaryActionV4): String = when (action) {
    WorkspacePrimaryActionV4.GENERATE -> "开始写这一章"
    WorkspacePrimaryActionV4.STOP -> "停止生成"
    WorkspacePrimaryActionV4.REPAIR -> "自动修复硬冲突"
    WorkspacePrimaryActionV4.SAVE -> "保存本章"
    WorkspacePrimaryActionV4.REVIEW -> "整理长期记忆"
    WorkspacePrimaryActionV4.NEXT -> "进入下一章"
}

private fun workspacePrimaryActionIconV4(action: WorkspacePrimaryActionV4): ImageVector = when (action) {
    WorkspacePrimaryActionV4.GENERATE -> Icons.Rounded.AutoAwesome
    WorkspacePrimaryActionV4.STOP -> Icons.Rounded.StopCircle
    WorkspacePrimaryActionV4.REPAIR -> Icons.Rounded.BuildCircle
    WorkspacePrimaryActionV4.SAVE -> Icons.Rounded.Save
    WorkspacePrimaryActionV4.REVIEW -> Icons.Rounded.Memory
    WorkspacePrimaryActionV4.NEXT -> Icons.Rounded.ArrowForward
}

private fun workspaceProgressIndexV4(state: WritingFlowUiState): Int = when {
    state.review != null -> 4
    state.chapterCommitted -> 3
    state.result != null -> 2
    state.isGenerating || state.streamPreview.isNotBlank() -> 1
    else -> 0
}

private fun workspacePhaseV4(state: WritingFlowUiState): String = when {
    state.isPlanningScenes -> "调整场景"
    state.isGenerating -> "生成正文"
    state.isSaving -> "保存正文"
    state.isReviewing -> "整理记忆"
    state.review != null -> "记忆已整理"
    state.chapterCommitted -> "正文已保存"
    state.result != null && state.result?.canCommit == true -> "等待保存"
    state.result != null -> "需要修复"
    state.sceneDirty -> "场景待确认"
    else -> "准备写作"
}

private fun workspaceBodyHintV4(
    state: WritingFlowUiState,
    hasPendingResult: Boolean,
    blockingCount: Int,
    warningCount: Int,
): String = when {
    state.isGenerating -> state.runEvents.lastOrNull { it.status == RunStatus.RUNNING }?.let { "Skill OS · ${it.stage.label}" } ?: "正在读取上下文"
    hasPendingResult && blockingCount > 0 -> "$blockingCount 个硬冲突需要修复"
    hasPendingResult && warningCount > 0 -> "一致性通过 · $warningCount 条编辑建议"
    hasPendingResult -> "一致性检查通过，可以保存"
    state.chapterCommitted -> "正文已经进入项目；仍可继续精修或生成新版本"
    else -> "输入要求直接生成；额外要求会跟随本轮 Run 一起执行"
}
