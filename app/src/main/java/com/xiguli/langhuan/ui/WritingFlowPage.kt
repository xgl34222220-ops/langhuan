package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.ScenePlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingFlowPage(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sceneInstruction by remember { mutableStateOf("") }
    var extraInstruction by remember { mutableStateOf("") }
    var showScenes by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(novelId) { viewModel.load(novelId) }
    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("写作", fontWeight = FontWeight.Bold)
                        state.draft?.let { Text("第${it.chapterNumber}章 · ${it.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading || !state.ready) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在准备本章……", fontWeight = FontWeight.SemiBold)
                    Text("加载章纲、人物状态与长期记忆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        val snapshot = state.snapshot ?: return@Scaffold
        val draft = state.draft ?: return@Scaffold
        val scenes = state.workingScenes.ifEmpty { draft.scenePlan }
        val blocking = state.result?.issues.orEmpty().filter { it.severity == IssueSeverity.BLOCKING }
        val warnings = state.result?.issues.orEmpty().filter { it.severity == IssueSeverity.WARNING }
        val pendingCandidates = snapshot.candidateFacts.filter { it.sourceChapter == draft.chapterNumber && it.status == CandidateFactStatus.PENDING }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f), tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(snapshot.novel.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("第${draft.chapterNumber}章  ${draft.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (draft.objective.isNotBlank()) Text(draft.objective, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {}, label = { Text(state.providerLabel.ifBlank { "AI" }, maxLines = 1) })
                            if (scenes.isNotEmpty()) AssistChip(onClick = { showScenes = !showScenes }, label = { Text("${scenes.size} 个场景") }, leadingIcon = { Icon(Icons.Rounded.Route, null, Modifier.size(16.dp)) })
                        }
                        WritingProgress(state)
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("场景", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(if (scenes.isEmpty()) "还没有场景计划" else "已准备 ${scenes.size} 个场景；正常情况下不用反复重生成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showScenes = !showScenes }) { Icon(if (showScenes) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) }
                        }
                        if (showScenes) {
                            scenes.sortedBy { it.order }.forEach { SceneMiniCard(it) }
                            OutlinedTextField(
                                value = sceneInstruction,
                                onValueChange = { sceneInstruction = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("需要时再告诉 AI 怎么改") },
                                placeholder = { Text("例如：第三场提前到傍晚；不要闪回") },
                                minLines = 2,
                                shape = RoundedCornerShape(18.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.planScenes(sceneInstruction); sceneInstruction = "" },
                                    enabled = !state.busy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text(if (state.isPlanningScenes) "调整中…" else "AI 调整") }
                                Button(
                                    onClick = viewModel::applyScenePlan,
                                    enabled = state.sceneDirty && !state.busy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text(if (state.sceneDirty) "确认场景" else "已确认") }
                            }
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                            }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(if (state.isGenerating) "正在写正文" else "正文生成", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(friendlyCurrentStage(state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!state.isGenerating) {
                            OutlinedTextField(
                                value = extraInstruction,
                                onValueChange = { extraInstruction = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("额外要求（可选）") },
                                shape = RoundedCornerShape(18.dp),
                                maxLines = 3,
                            )
                        }
                        Button(
                            onClick = { if (state.isGenerating) viewModel.cancelGeneration() else viewModel.generate(extraInstruction) },
                            enabled = state.isGenerating || !state.busy,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(if (state.isGenerating) Icons.Rounded.StopCircle else Icons.Rounded.AutoAwesome, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isGenerating) "停止生成" else if (draft.content.isBlank()) "开始写这一章" else "生成新版本")
                        }
                        if (state.streamPreview.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .72f)) {
                                Text(
                                    state.streamPreview,
                                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                                    maxLines = if (state.isGenerating) 14 else 9,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            state.result?.let { result ->
                item {
                    val canSave = result.canCommit
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = if (canSave) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = .46f),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (canSave) Icons.Rounded.CheckCircle else Icons.Rounded.BuildCircle, null, tint = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (canSave) "正文已完成" else "需要自动修一处硬问题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        when {
                                            blocking.isNotEmpty() -> "发现 ${blocking.size} 个真正会影响连续性的硬冲突"
                                            warnings.isNotEmpty() -> "有 ${warnings.size} 条建议，不影响保存"
                                            else -> "一致性检查通过，可以保存"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            blocking.take(3).forEach { issue ->
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(issue.message, fontWeight = FontWeight.SemiBold)
                                        if (issue.repairInstruction.isNotBlank()) Text(issue.repairInstruction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (canSave && warnings.isNotEmpty()) {
                                Text("还有一些编辑建议，但不会再因为主编意见把整章卡死。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!canSave) {
                                    Button(onClick = viewModel::repairAndRegenerate, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                        Icon(Icons.Rounded.AutoFixHigh, null); Spacer(Modifier.width(6.dp)); Text("自动修复")
                                    }
                                } else {
                                    Button(onClick = viewModel::commitAndReview, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                        Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("保存本章")
                                    }
                                }
                                OutlinedButton(onClick = { showDiagnostics = !showDiagnostics }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(5.dp)); Text(if (showDiagnostics) "收起详情" else "运行详情")
                                }
                            }
                        }
                    }
                }
            }

            if (state.chapterCommitted && state.review == null && !state.isReviewing) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onEditChapter(draft.novelId, draft.chapterNumber) },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text("继续修改")
                        }
                        FilledTonalButton(onClick = viewModel::reviewCommittedChapter, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Rounded.Memory, null); Spacer(Modifier.width(7.dp)); Text("整理记忆")
                        }
                    }
                }
            }

            if (state.chapterCommitted && state.review != null) {
                item {
                    OutlinedButton(
                        onClick = { onEditChapter(draft.novelId, draft.chapterNumber) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(7.dp)); Text("返回修改本章（确认后仍可反复修改）")
                    }
                }
            }

            state.review?.let { review ->
                item {
                    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("章节记忆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(review.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (pendingCandidates.isEmpty()) {
                                Text("本章记忆已整理，没有待确认事实。", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Button(onClick = { viewModel.advanceToNext(null) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                    Text("进入下一章"); Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.ArrowForward, null)
                                }
                            } else {
                                Text("有 ${pendingCandidates.size} 条新事实需要确认", fontWeight = FontWeight.SemiBold)
                                pendingCandidates.take(8).forEach { fact ->
                                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(fact.subject, fontWeight = FontWeight.Bold)
                                            Text(fact.after, style = MaterialTheme.typography.bodySmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = { viewModel.rejectCandidateFact(fact.id) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("忽略") }
                                                Button(onClick = { viewModel.confirmCandidateFact(fact.id) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("确认") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showDiagnostics && state.runEvents.isNotEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Column(Modifier.padding(12.dp)) {
                            Text("高级诊断", modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                            Text("仅用于排查模型/流水线问题，不影响正常写作。", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RunInspectorPanel(state.runEvents, "运行详情")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun WritingProgress(state: WritingFlowUiState) {
    val done = when {
        state.review != null -> 5
        state.chapterCommitted -> 4
        state.result != null -> 3
        state.isGenerating || state.streamPreview.isNotBlank() -> 2
        else -> 1
    }
    val labels = listOf("准备", "正文", "检查", "保存", "记忆")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val active = index < done
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(99.dp),
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .11f) else MaterialTheme.colorScheme.surface.copy(alpha = .58f),
            ) {
                Row(Modifier.padding(vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (active) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    if (active) Spacer(Modifier.width(3.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SceneMiniCard(scene: ScenePlan) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .7f)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("场景 ${scene.order}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (scene.location.isNotBlank()) Text(scene.location, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scene.purpose.isNotBlank()) Text(scene.purpose, fontWeight = FontWeight.SemiBold)
            if (scene.conflict.isNotBlank()) Text(scene.conflict, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun friendlyCurrentStage(state: WritingFlowUiState): String = when {
    state.isPlanningScenes -> "正在调整场景"
    state.isGenerating -> when {
        state.runEvents.size <= 1 -> "正在读取上下文"
        state.runEvents.size <= 3 -> "正在写正文"
        state.runEvents.size <= 5 -> "正在润色"
        else -> "正在做一致性检查"
    }
    state.isSaving -> "正在保存正文"
    state.isReviewing -> "正在整理长期记忆"
    state.result != null -> "正文已生成，等待确认"
    state.chapterCommitted -> "正文已保存"
    else -> "场景确认后即可生成正文"
}
