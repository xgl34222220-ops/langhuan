package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.engine.AgentAction
import com.xiguli.langhuan.engine.AgentActionKind
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

@Composable
internal fun AgentPage(
    state: StudioUiState,
    vm: StudioViewModel,
    onProjectBackup: () -> Unit,
    onProjectRestore: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val style = state.snapshot.bible.firstOrNull { it.category == BibleCategory.STYLE }
    var styleName by remember(state.snapshot.novel.id, style?.id) { mutableStateOf(style?.name ?: "主文风") }
    var styleText by remember(state.snapshot.novel.id, style?.id) { mutableStateOf(style?.content.orEmpty()) }
    val review = state.agentReview

    Surface(Modifier.fillMaxSize(), color = t.background) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanghuanIconButton(Icons.Rounded.Close, "关闭 AI 助手", onClose)
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                        Text("AI 助手", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                        Text("章节复盘 · 全书巡检 · 自治规划 · 长期记忆", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }

            item {
                AgentCard(depth = 2) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(t.radiusMd),
                            color = t.warmSurface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Psychology, null, tint = t.primary, modifier = Modifier.size(23.dp))
                            }
                        }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text("小说创作 Agent", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                            Text("只给建议；事实记忆仍按现有确认流程进入 Canon。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = vm::runChapterReview,
                        enabled = state.provider.ready && !state.isAgentReviewing && !state.isAuditing && state.draft.content.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = t.primaryForeground),
                    ) {
                        if (state.isAgentReviewing) CircularProgressIndicator(Modifier.size(18.dp), color = t.primaryForeground, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.isAgentReviewing) "正在复盘第${state.draft.chapterNumber}章…" else "复盘当前章节")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = vm::runFullBookAudit,
                        enabled = state.provider.ready && !state.isAgentReviewing && !state.isAuditing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
                    ) {
                        if (state.isAuditing) CircularProgressIndicator(Modifier.size(18.dp), color = t.foreground, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Timeline, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.isAuditing) "全书主编正在深度巡检…" else "全书主编深度巡检")
                    }
                }
            }

            item { LongFormAgentPanel(state.snapshot) }
            item { FullBookEditorPanel(state.snapshot) }
            item { AutonomousPlanPanel(state, vm) }
            item { CandidateCanonPanel(state, vm) }

            item {
                AgentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = t.primary)
                        Column(Modifier.padding(start = 9.dp).weight(1f)) {
                            Text("文风模板", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                            Text("作为“怎么写”的硬约束，不会覆盖总纲和事实设定。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    OutlinedTextField(
                        styleName,
                        { styleName = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("模板名称") },
                        singleLine = true,
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = agentFieldColors(t),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        styleText,
                        { styleText = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("叙述语气、句式、节奏、视角距离、禁用表达等") },
                        minLines = 4,
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = agentFieldColors(t),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.saveStyleTemplate(styleName, styleText) },
                        enabled = styleText.isNotBlank() && !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                    ) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(7.dp))
                        Text("保存为当前文风")
                    }
                }
            }

            item {
                AgentCard {
                    Text("项目备份", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text(
                        ".lhproj 保存大纲、圣经、人物、关系、时间线、伏笔和所有章节；不会包含 API Key。恢复时生成新项目，不覆盖原书。",
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                    Spacer(Modifier.height(11.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onProjectBackup,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isExporting,
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
                        ) {
                            Icon(Icons.Rounded.FileDownload, null)
                            Spacer(Modifier.width(5.dp))
                            Text("备份")
                        }
                        Button(
                            onClick = onProjectRestore,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isImporting,
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
                        ) {
                            Icon(Icons.Rounded.FileOpen, null)
                            Spacer(Modifier.width(5.dp))
                            Text("恢复")
                        }
                    }
                }
            }

            if (review == null && !state.isAgentReviewing && !state.isAuditing) {
                item {
                    AgentCard {
                        Text("还没有复盘报告", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                        Text("正文保存后会自动尝试生成一次章节复盘；也可以在这里手动运行。", modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }

            review?.let { report ->
                item {
                    AgentCard(depth = 2) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (report.fullBook) Icons.Rounded.Timeline else Icons.Rounded.CheckCircle, null, tint = t.primary)
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text(report.title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
                                Text(if (report.fullBook) "全书巡检" else "第${state.draft.chapterNumber}章复盘", color = t.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (report.metrics.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(report.metrics, color = t.mutedForeground)
                        }
                        if (report.summary.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(report.summary, color = t.foreground)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(vm::dismissAgentReview) { Text("清除本次报告") }
                    }
                }

                if (report.diagnostics.isNotEmpty()) {
                    item { Text("诊断", style = MaterialTheme.typography.titleLarge, color = t.foreground) }
                    items(report.diagnostics) { action -> AgentActionCard(action, false) }
                }

                if (report.memoryActions.isNotEmpty()) {
                    item {
                        AgentCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Memory, null, tint = t.primary)
                                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                    Text("本次提取的候选事实", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                    Text("${report.memoryActions.size} 项结构化事实。先加入 Candidate；只有通过本地证明或你确认后才会进入 Canon。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("这些提取项已经自动进入 Candidate 候选区；这里的报告本身不会直接改 Canon。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                    }
                    items(report.memoryActions.take(16)) { action -> AgentActionCard(action, true) }
                }

                if (report.nextOptions.isNotEmpty()) {
                    item { Text("下一章候选", style = MaterialTheme.typography.titleLarge, color = t.foreground) }
                    report.nextOptions.forEachIndexed { index, option ->
                        item {
                            AgentCard {
                                Text(option.title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                Text("目标：${option.objective}", modifier = Modifier.padding(top = 6.dp), color = t.foreground)
                                Text("冲突：${option.conflict}", color = t.mutedForeground)
                                Text("转折：${option.turningPoint}", color = t.mutedForeground)
                                Spacer(Modifier.height(9.dp))
                                Button(
                                    onClick = { vm.useAgentNextOption(index) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(t.radiusMd),
                                    colors = ButtonDefaults.buttonColors(containerColor = t.muted, contentColor = t.foreground),
                                ) { Text("转为章纲并预览") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentActionCard(action: AgentAction, memory: Boolean) = AgentCard {
    val t = LocalLanghuanUiTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(action.kind.label(), color = if (memory) t.primary else t.warning, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(action.subject, Modifier.weight(1f), color = t.foreground, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (action.before.isNotBlank()) Text("现状：${action.before}", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
    if (action.after.isNotBlank()) Text("建议/结果：${action.after}", modifier = Modifier.padding(top = 4.dp), color = t.foreground)
    if (action.evidence.isNotBlank()) Text("依据：${action.evidence}", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun AgentCard(
    depth: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 18.dp, depth = depth) {
        Column(content = content)
    }
}

@Composable
private fun agentFieldColors(t: com.xiguli.langhuan.ui.design.LanghuanUiTokens): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = t.muted.copy(alpha = .58f),
    unfocusedContainerColor = t.muted.copy(alpha = .42f),
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedTextColor = t.foreground,
    unfocusedTextColor = t.foreground,
    focusedLabelColor = t.primary,
    unfocusedLabelColor = t.mutedForeground,
)

private fun AgentActionKind.label(): String = when (this) {
    AgentActionKind.CHARACTER_NEW -> "新人物"
    AgentActionKind.CHARACTER_LOCATION -> "位置"
    AgentActionKind.CHARACTER_EMOTION -> "情绪"
    AgentActionKind.CHARACTER_GOAL -> "目标"
    AgentActionKind.RELATION -> "关系"
    AgentActionKind.KNOWLEDGE_GAIN -> "获知信息"
    AgentActionKind.TIMELINE -> "时间线"
    AgentActionKind.FORESHADOW_NEW -> "新伏笔"
    AgentActionKind.FORESHADOW_UPDATE -> "伏笔变化"
    AgentActionKind.CONSISTENCY -> "一致性"
    AgentActionKind.OUTLINE_GAP -> "大纲漏洞"
    AgentActionKind.PACING -> "节奏"
    AgentActionKind.ARC -> "角色弧光"
    AgentActionKind.NEXT_OPTION -> "下一章"
    AgentActionKind.UNKNOWN -> "其他"
}
