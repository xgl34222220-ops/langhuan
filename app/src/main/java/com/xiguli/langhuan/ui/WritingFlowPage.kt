package com.xiguli.langhuan.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import com.xiguli.langhuan.domain.IssueSeverity
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingFlowPage(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sceneInstruction by remember { mutableStateOf("") }
    var extraInstruction by remember { mutableStateOf("") }

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
                        Text("写作流", fontWeight = FontWeight.Bold)
                        state.draft?.let { Text("第${it.chapterNumber}章 · ${it.title}", style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭写作流") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading || !state.ready) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在载入小说、章纲与长期记忆……")
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            return@Scaffold
        }

        val snapshot = state.snapshot ?: return@Scaffold
        val draft = state.draft ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FlowCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(snapshot.novel.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("第${draft.chapterNumber}章 ${draft.title}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(draft.objective, color = LocalMiuixTokens.current.textSecondary)
                        }
                        AssistChip(onClick = {}, label = { Text(state.providerLabel) })
                    }
                    Spacer(Modifier.height(12.dp))
                    WritingSteps(state)
                }
            }

            item {
                FlowCard {
                    FlowTitle(Icons.Rounded.Route, "① 场景规划", "先把章纲拆成 2-6 个真正发生变化的场景，再交给正文模型。")
                    Spacer(Modifier.height(10.dp))
                    val scenes = state.workingScenes.ifEmpty { draft.scenePlan }
                    scenes.sortedBy { it.order }.forEach { scene ->
                        ScenePlanCard(scene)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.sceneNote.isNotBlank()) {
                        Text(state.sceneNote, color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    state.sceneConversation.takeLast(4).forEach { message ->
                        val prefix = if (message.role == "user") "你：" else "琅嬛："
                        Text(prefix + message.text, style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sceneInstruction,
                        onValueChange = { sceneInstruction = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("直接告诉 AI 怎么改场景") },
                        placeholder = { Text("例如：第三个场景太拖，合并掉；章末钩子再狠一点") },
                        shape = RoundedCornerShape(18.dp),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.planScenes(sceneInstruction); sceneInstruction = "" },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.isPlanningScenes) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.AutoAwesome, null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (state.isPlanningScenes) "编排中" else if (sceneInstruction.isBlank()) "AI 检查场景" else "让 AI 修改")
                        }
                        Button(
                            onClick = viewModel::applyScenePlan,
                            enabled = state.sceneDirty && !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (state.sceneDirty) "确认场景" else "已确认")
                        }
                    }
                }
            }

            item {
                FlowCard {
                    FlowTitle(Icons.Rounded.AutoAwesome, "② 正文生成", "生成前会检索长期记忆、人物状态、时间线、伏笔和当前大纲链。")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = extraInstruction,
                        onValueChange = { extraInstruction = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("本次额外要求（可选）") },
                        placeholder = { Text("例如：本章控制在 3000 字左右，前半段克制，后半段突然加速") },
                        shape = RoundedCornerShape(18.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    if (state.isGenerating) {
                        Button(
                            onClick = viewModel::cancelGeneration,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.StopCircle, null)
                            Spacer(Modifier.size(7.dp))
                            Text("停止生成")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.generate(extraInstruction) },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null)
                            Spacer(Modifier.size(7.dp))
                            Text(if (draft.content.isBlank()) "生成本章正文" else "重新生成本章新版本")
                        }
                    }
                    if (state.streamPreview.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(if (state.isGenerating) "实时生成预览" else "本次生成结果", fontWeight = FontWeight.Bold)
                        Text(
                            state.streamPreview,
                            color = LocalMiuixTokens.current.textSecondary,
                            maxLines = if (state.isGenerating) 16 else 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            state.result?.let { result ->
                item {
                    FlowCard {
                        FlowTitle(Icons.Rounded.Shield, "③ 一致性审查", "阻断级问题不允许直接写入正文和长期记忆。")
                        Spacer(Modifier.height(10.dp))
                        if (result.issues.isEmpty()) {
                            Text("未发现一致性问题，可以保存。", color = LocalMiuixTokens.current.success, fontWeight = FontWeight.Bold)
                        } else {
                            result.issues.forEach { issue ->
                                val label = when (issue.severity) {
                                    IssueSeverity.INFO -> "提示"
                                    IssueSeverity.WARNING -> "警告"
                                    IssueSeverity.BLOCKING -> "阻断"
                                }
                                Text("$label · ${issue.code}", fontWeight = FontWeight.Bold, color = if (issue.severity == IssueSeverity.BLOCKING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                Text(issue.message)
                                if (issue.repairInstruction.isNotBlank()) Text("修复：${issue.repairInstruction}", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!result.canCommit) {
                                OutlinedButton(onClick = viewModel::repairAndRegenerate, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.size(6.dp)); Text("按审查重写")
                                }
                            }
                            Button(onClick = viewModel::commitAndReview, enabled = result.canCommit && !state.busy, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.size(6.dp)); Text("保存并自动复盘")
                            }
                        }
                    }
                }
            }

            if (state.chapterCommitted && state.result == null && state.review == null) {
                item {
                    FlowCard {
                        FlowTitle(Icons.Rounded.Psychology, "④ Agent 章节复盘", "从已经保存的正文中抽取事实变化，而不是让 AI 猜。")
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = viewModel::reviewCommittedChapter, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            if (state.isReviewing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Psychology, null)
                            Spacer(Modifier.size(7.dp))
                            Text(if (state.isReviewing) "正在复盘" else "复盘当前已保存正文")
                        }
                    }
                }
            }

            state.review?.let { review ->
                item {
                    FlowCard {
                        FlowTitle(Icons.Rounded.Memory, "④ 事实记忆与诊断", "事实记忆需要确认后写入；诊断只给建议，不会偷偷改锁定设定。")
                        Spacer(Modifier.height(10.dp))
                        Text(review.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(review.summary, color = LocalMiuixTokens.current.textSecondary)
                        if (review.metrics.isNotBlank()) {
                            Spacer(Modifier.height(8.dp)); Text(review.metrics, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("事实变化 · ${review.memoryActions.size} 条", fontWeight = FontWeight.Bold)
                        if (review.memoryActions.isEmpty()) {
                            Text("本章没有需要新增的长期事实。", color = LocalMiuixTokens.current.textSecondary)
                        } else {
                            review.memoryActions.forEach { action ->
                                Text("• ${action.kind}｜${action.subject} → ${action.after}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (review.diagnostics.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("一致性 / 节奏诊断", fontWeight = FontWeight.Bold)
                            review.diagnostics.forEach { action ->
                                Text("• ${action.subject}：${action.after}", style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = viewModel::applyMemoryOnly,
                            enabled = !state.memoryApplied && !state.busy && review.memoryActions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Memory, null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (state.memoryApplied) "事实记忆已写入" else "确认并写入事实记忆")
                        }
                    }
                }

                item {
                    FlowCard {
                        FlowTitle(Icons.Rounded.ArrowForward, "⑤ 下一章", "先选方向，再进入下一章；新一轮仍会从场景规划开始。")
                        Spacer(Modifier.height(10.dp))
                        if (review.nextOptions.isEmpty()) {
                            Text("本次复盘没有给出候选，可让章节规划器自动生成下一章。", color = LocalMiuixTokens.current.textSecondary)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.advanceToNext(null) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.size(6.dp)); Text("AI 自动规划下一章")
                            }
                        } else {
                            review.nextOptions.forEachIndexed { index, option ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("候选 ${index + 1} · ${option.title}", fontWeight = FontWeight.Bold)
                                        Text("目标：${option.objective}", style = MaterialTheme.typography.bodySmall)
                                        Text("冲突：${option.conflict}", style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                                        Text("转折：${option.turningPoint}", style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = { viewModel.advanceToNext(index) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                            Text("采用这个方向并进入下一章")
                                            Spacer(Modifier.size(6.dp))
                                            Icon(Icons.Rounded.ArrowForward, null)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun FlowCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().squircleClip(24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun FlowTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(42.dp).squircleClip(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
        ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) } }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
        }
    }
}

@Composable
private fun ScenePlanCard(scene: ScenePlan) {
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("场景 ${scene.order} · ${scene.viewpoint}", fontWeight = FontWeight.Bold)
            Text(scene.location, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text("目的：${scene.purpose}", style = MaterialTheme.typography.bodySmall)
            Text("冲突：${scene.conflict}", style = MaterialTheme.typography.bodySmall)
            Text("结果：${scene.outcome}", style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
        }
    }
}

@Composable
private fun WritingSteps(state: WritingFlowUiState) {
    val active = when {
        state.review != null || state.isReviewing || state.chapterCommitted -> 4
        state.result != null -> 3
        state.isGenerating || state.streamPreview.isNotBlank() -> 2
        else -> 1
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("场景", "正文", "审查", "记忆", "下一章").forEachIndexed { index, label ->
            val step = index + 1
            AssistChip(
                onClick = {},
                label = { Text("$step $label") },
                leadingIcon = if (step < active) {
                    { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}
