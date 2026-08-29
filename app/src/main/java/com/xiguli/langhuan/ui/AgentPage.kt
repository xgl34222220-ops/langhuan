package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.engine.AgentAction
import com.xiguli.langhuan.engine.AgentActionKind
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
internal fun AgentPage(
    state: StudioUiState,
    vm: StudioViewModel,
    onProjectBackup: () -> Unit,
    onProjectRestore: () -> Unit,
    onClose: () -> Unit,
) {
    val style = state.snapshot.bible.firstOrNull { it.category == BibleCategory.STYLE }
    var styleName by remember(state.snapshot.novel.id, style?.id) { mutableStateOf(style?.name ?: "主文风") }
    var styleText by remember(state.snapshot.novel.id, style?.id) { mutableStateOf(style?.content.orEmpty()) }
    val review = state.agentReview

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("创作 Agent", style = MaterialTheme.typography.displaySmall, color = LocalMiuixTokens.current.textPrimary)
                    Text("自动复盘章节 · 全书主编 · 未来滚动自治规划 · 结构化长期记忆", color = LocalMiuixTokens.current.textSecondary)
                }
                IconButton(onClose) { Icon(Icons.Rounded.Close, "关闭 Agent") }
            }
        }

        item {
            AgentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("小说创作 Agent", style = MaterialTheme.typography.titleMedium)
                        Text("分析默认只生成建议；事实记忆必须确认后才写入。", color = LocalMiuixTokens.current.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::runChapterReview,
                    enabled = state.provider.ready && !state.isAgentReviewing && !state.isAuditing && state.draft.content.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    if (state.isAgentReviewing) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.isAgentReviewing) "正在复盘第${state.draft.chapterNumber}章…" else "复盘当前章节")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = vm::runFullBookAudit,
                    enabled = state.provider.ready && !state.isAgentReviewing && !state.isAuditing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    if (state.isAuditing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("文风模板", style = MaterialTheme.typography.titleMedium)
                        Text("作为“怎么写”的硬约束，不会覆盖总纲和事实设定。", color = LocalMiuixTokens.current.textSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(styleName, { styleName = it }, Modifier.fillMaxWidth(), label = { Text("模板名称") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    styleText,
                    { styleText = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("叙述语气、句式、节奏、视角距离、禁用表达等") },
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = { vm.saveStyleTemplate(styleName, styleText) },
                    enabled = styleText.isNotBlank() && !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(7.dp)); Text("保存为当前文风")
                }
            }
        }

        item {
            AgentCard {
                Text("项目备份", style = MaterialTheme.typography.titleMedium)
                Text(".lhproj 保存大纲、圣经、人物、关系、时间线、伏笔和所有章节；不会包含 API Key。恢复时生成新项目，不覆盖原书。", color = LocalMiuixTokens.current.textSecondary)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onProjectBackup, Modifier.weight(1f), enabled = !state.isExporting, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.FileDownload, null); Spacer(Modifier.width(5.dp)); Text("备份")
                    }
                    OutlinedButton(onProjectRestore, Modifier.weight(1f), enabled = !state.isImporting, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(5.dp)); Text("恢复")
                    }
                }
            }
        }

        if (review == null && !state.isAgentReviewing && !state.isAuditing) {
            item {
                AgentCard {
                    Text("还没有复盘报告", style = MaterialTheme.typography.titleMedium)
                    Text("正文保存后会自动尝试生成一次章节复盘；也可以在这里手动运行。", color = LocalMiuixTokens.current.textSecondary)
                }
            }
        }

        review?.let { report ->
            item {
                AgentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (report.fullBook) Icons.Rounded.Timeline else Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 9.dp).weight(1f)) {
                            Text(report.title, style = MaterialTheme.typography.titleLarge)
                            Text(if (report.fullBook) "全书巡检" else "第${state.draft.chapterNumber}章复盘", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (report.metrics.isNotBlank()) {
                        Spacer(Modifier.height(9.dp)); Text(report.metrics, color = LocalMiuixTokens.current.textSecondary)
                    }
                    if (report.summary.isNotBlank()) {
                        Spacer(Modifier.height(9.dp)); Text(report.summary)
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(vm::dismissAgentReview) { Text("清除本次报告") }
                }
            }

            if (report.diagnostics.isNotEmpty()) {
                item { Text("诊断", style = MaterialTheme.typography.titleLarge) }
                items(report.diagnostics) { action -> AgentActionCard(action, false) }
            }

            if (report.memoryActions.isNotEmpty()) {
                item {
                    AgentCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text("本次提取的候选事实", style = MaterialTheme.typography.titleMedium)
                                Text("${report.memoryActions.size} 项结构化事实。先加入 Candidate；只有通过本地证明或你确认后才会进入 Canon。", color = LocalMiuixTokens.current.textSecondary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("这些提取项已经自动进入 Candidate 候选区；这里的报告本身不会直接改 Canon。", color = LocalMiuixTokens.current.textSecondary)
                    }
                }
                items(report.memoryActions.take(16)) { action -> AgentActionCard(action, true) }
            }

            if (report.nextOptions.isNotEmpty()) {
                item { Text("下一章候选", style = MaterialTheme.typography.titleLarge) }
                report.nextOptions.forEachIndexed { index, option ->
                    item {
                        AgentCard {
                            Text(option.title, style = MaterialTheme.typography.titleMedium)
                            Text("目标：${option.objective}", modifier = Modifier.padding(top = 6.dp))
                            Text("冲突：${option.conflict}", color = LocalMiuixTokens.current.textSecondary)
                            Text("转折：${option.turningPoint}", color = LocalMiuixTokens.current.textSecondary)
                            Spacer(Modifier.height(9.dp))
                            OutlinedButton({ vm.useAgentNextOption(index) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Text("转为章纲并预览")
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(action.kind.label(), color = if (memory) MaterialTheme.colorScheme.primary else LocalMiuixTokens.current.warning, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(action.subject, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (action.before.isNotBlank()) Text("现状：${action.before}", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
    if (action.after.isNotBlank()) Text("建议/结果：${action.after}", modifier = Modifier.padding(top = 4.dp))
    if (action.evidence.isNotBlank()) Text("依据：${action.evidence}", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun AgentCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(2.dp, shape)
            .squircleClip(26.dp)
            .background(LocalMiuixTokens.current.cardBackground.copy(alpha = .94f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), shape)
            .padding(18.dp),
        content = content,
    )
}

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
