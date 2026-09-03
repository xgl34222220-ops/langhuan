package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.engine.CanonChangeRisk
import com.xiguli.langhuan.engine.CanonMigrationAction
import com.xiguli.langhuan.engine.CanonMigrationExecutionItem
import com.xiguli.langhuan.engine.CanonMigrationExecutionMode
import com.xiguli.langhuan.engine.CanonMigrationTask
import com.xiguli.langhuan.engine.CanonMigrationTaskStatus
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanonMigrationQueueSheetV8(
    state: CanonChangeProposalUiState,
    onGenerateRepairProposal: (CanonMigrationTask) -> Unit,
    onOpenChapter: (Int) -> Unit,
    onDone: (String) -> Unit,
    onSkip: (String) -> Unit,
    onClearResolved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val migrationVm: CanonChangeProposalViewModel = viewModel()
    val executeSafe = migrationVm::executeReadySafeMigrations
    val queue = state.migrationQueue
    val plan = state.migrationPlan
    val pending = queue?.pending.orEmpty()
    val resolved = queue?.tasks.orEmpty().count { it.status == CanonMigrationTaskStatus.DONE || it.status == CanonMigrationTaskStatus.SKIPPED }
    val failed = queue?.tasks.orEmpty().count { it.status == CanonMigrationTaskStatus.FAILED }
    val next = plan?.next

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.warmSurface,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .18f)),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Route, null, tint = t.accent)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Canon 修复队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = t.foreground)
                    Text(
                        buildString {
                            append("${pending.size} 项待处理")
                            if (failed > 0) append(" · $failed 项失败")
                            if (resolved > 0) append(" · $resolved 项已处理/跳过")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                if (resolved > 0) TextButton(onClick = onClearResolved) { Text("清理已完成") }
            }

            MigrationPlanSummaryV8(plan?.summary() ?: "正在读取修复计划")

            state.migrationMessage?.let { notice -> MigrationQueueNoticeV8(notice, t.accent) }
            state.migrationError?.let { error -> MigrationQueueNoticeV8(error, t.destructive) }

            if (state.isMigrationExecuting) {
                MigrationQueueNoticeV8("正在执行就绪的安全同步……", t.accent, progress = true)
            } else if (next != null) {
                MigrationNextActionV9(
                    item = next,
                    onGenerateRepairProposal = { onGenerateRepairProposal(next.task) },
                    onOpenChapter = { next.task.chapterNumber?.let(onOpenChapter) },
                    onExecuteSafe = executeSafe,
                )
            }

            if (pending.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusLg),
                    color = t.success.copy(alpha = .08f),
                    border = BorderStroke(1.dp, t.success.copy(alpha = .18f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = t.success)
                        Text("当前没有待修复项", modifier = Modifier.padding(top = 7.dp), fontWeight = FontWeight.SemiBold, color = t.foreground)
                        Text(
                            "新的 Canon 变更确认后，受影响内容会自动进入这里并按依赖重新编排。",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(pending, key = { it.id }) { task ->
                        val item = plan?.items?.firstOrNull { it.task.id == task.id }
                        MigrationTaskCardV8(
                            task = task,
                            item = item,
                            onGenerateRepairProposal = { onGenerateRepairProposal(task) },
                            onOpenChapter = { task.chapterNumber?.let(onOpenChapter) },
                            onExecuteSafe = executeSafe,
                            onDone = { onDone(task.id) },
                            onSkip = { onSkip(task.id) },
                        )
                    }
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(t.radiusMd)) {
                Text("回到写作总控")
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MigrationPlanSummaryV8(summary: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusLg),
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp), tint = t.accent)
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("修复编排", fontWeight = FontWeight.SemiBold, color = t.foreground)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = t.foreground)
                }
            }
            HorizontalDivider(color = t.border)
            Text(
                "顺序：项目结构 → 章节 Runtime → 长期记忆。只有确定性的记忆重建会直接执行；Canon 差异必须经过提案确认，正文必须进入章节 Runtime 重新检查，不会后台静默改写。",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun MigrationNextActionV9(
    item: CanonMigrationExecutionItem,
    onGenerateRepairProposal: () -> Unit,
    onOpenChapter: () -> Unit,
    onExecuteSafe: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val blocked = item.blockedByTaskIds.isNotEmpty()
    val waitingConfirmation = item.task.status == CanonMigrationTaskStatus.AWAITING_CONFIRMATION
    val tone = when {
        blocked -> t.warning
        waitingConfirmation -> t.warning
        item.mode == CanonMigrationExecutionMode.SAFE_LOCAL -> t.success
        else -> t.accent
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusLg),
        color = tone.copy(alpha = .08f),
        border = BorderStroke(1.dp, tone.copy(alpha = .20f)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        blocked -> Icons.Rounded.Block
                        waitingConfirmation -> Icons.Rounded.HourglassTop
                        else -> Icons.Rounded.AutoFixHigh
                    },
                    null,
                    Modifier.size(18.dp),
                    tint = tone,
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("下一步 · ${item.phase.label}", style = MaterialTheme.typography.labelMedium, color = tone, fontWeight = FontWeight.SemiBold)
                    Text("${item.task.action.label} · ${item.task.label}", fontWeight = FontWeight.SemiBold, color = t.foreground)
                }
                MigrationModeBadgeV8(item.mode)
            }
            when {
                blocked -> Text(
                    "被 ${item.blockedByTaskIds.size} 项前置任务阻塞；前置修复完成后才会解锁。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                waitingConfirmation -> Text(
                    "差异提案已经生成，必须先确认或放弃当前提案；不会自动越过 Canon Gate。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                else -> Button(
                    onClick = when (item.mode) {
                        CanonMigrationExecutionMode.SAFE_LOCAL -> onExecuteSafe
                        CanonMigrationExecutionMode.PROPOSAL_GATE -> onGenerateRepairProposal
                        CanonMigrationExecutionMode.CHAPTER_RUNTIME -> onOpenChapter
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(17.dp))
                    Text(
                        when (item.mode) {
                            CanonMigrationExecutionMode.SAFE_LOCAL -> "执行全部就绪安全同步"
                            CanonMigrationExecutionMode.PROPOSAL_GATE -> "生成下一项最小差异提案"
                            CanonMigrationExecutionMode.CHAPTER_RUNTIME -> "打开第${item.task.chapterNumber}章进入 Runtime"
                        },
                        Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationTaskCardV8(
    task: CanonMigrationTask,
    item: CanonMigrationExecutionItem?,
    onGenerateRepairProposal: () -> Unit,
    onOpenChapter: () -> Unit,
    onExecuteSafe: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val chapterRuntime = task.action == CanonMigrationAction.REWRITE_CHAPTER && task.chapterNumber != null
    val safeMemory = task.action == CanonMigrationAction.REFRESH_MEMORY
    val blockedCount = item?.blockedByTaskIds.orEmpty().size
    val blocked = blockedCount > 0
    val failed = task.status == CanonMigrationTaskStatus.FAILED
    val priorityColor = when (task.priority) {
        CanonChangeRisk.LOW -> t.success
        CanonChangeRisk.MEDIUM -> t.warning
        CanonChangeRisk.HIGH -> t.destructive
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusLg),
        color = t.card,
        border = BorderStroke(1.dp, if (failed) t.destructive.copy(alpha = .28f) else t.border),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (failed || task.priority == CanonChangeRisk.HIGH) Icons.Rounded.WarningAmber else Icons.Rounded.EditNote,
                    null,
                    Modifier.size(18.dp),
                    tint = if (failed) t.destructive else priorityColor,
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("${task.action.label} · ${task.label}", fontWeight = FontWeight.SemiBold, color = t.foreground)
                    Text(
                        buildString {
                            append(task.scope)
                            task.chapterNumber?.let { append(" · 第${it}章") }
                            item?.let { append(" · ${it.phase.label}") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = t.mutedForeground,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PriorityBadgeV8(task.priority)
                    item?.let { MigrationModeBadgeV8(it.mode) }
                }
            }

            Text(task.detail, style = MaterialTheme.typography.bodySmall, color = t.foreground)
            when {
                blocked -> MigrationQueueNoticeV8("等待 $blockedCount 项前置修复完成", t.warning)
                failed -> MigrationQueueNoticeV8("上次执行失败，可从这里按原路径重试。", t.destructive)
                task.status == CanonMigrationTaskStatus.AWAITING_CONFIRMATION -> MigrationQueueNoticeV8("提案已生成，等待你确认或放弃。", t.warning)
            }
            HorizontalDivider(color = t.border)

            when {
                chapterRuntime -> OutlinedButton(
                    onClick = onOpenChapter,
                    enabled = !blocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.EditNote, null, Modifier.size(17.dp))
                    Text("打开第${task.chapterNumber}章进入 Runtime", Modifier.padding(start = 6.dp))
                }
                safeMemory -> OutlinedButton(
                    onClick = onExecuteSafe,
                    enabled = !blocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(17.dp))
                    Text("按当前 Canon 安全重建记忆", Modifier.padding(start = 6.dp))
                }
                else -> OutlinedButton(
                    onClick = onGenerateRepairProposal,
                    enabled = !blocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(17.dp))
                    Text(if (failed) "重试最小差异提案" else "生成最小差异提案", Modifier.padding(start = 6.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) {
                    Icon(Icons.Rounded.SkipNext, null, Modifier.size(16.dp))
                    Text("跳过", Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = onDone) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp))
                    Text("标记完成", Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun PriorityBadgeV8(risk: CanonChangeRisk) {
    val t = LocalLanghuanUiTokens.current
    val (label, color) = when (risk) {
        CanonChangeRisk.LOW -> "低" to t.success
        CanonChangeRisk.MEDIUM -> "中" to t.warning
        CanonChangeRisk.HIGH -> "高" to t.destructive
    }
    MigrationSmallBadgeV8("${label}风险", color)
}

@Composable
private fun MigrationModeBadgeV8(mode: CanonMigrationExecutionMode) {
    val t = LocalLanghuanUiTokens.current
    val (label, color) = when (mode) {
        CanonMigrationExecutionMode.SAFE_LOCAL -> "安全自动" to t.success
        CanonMigrationExecutionMode.PROPOSAL_GATE -> "需确认" to t.warning
        CanonMigrationExecutionMode.CHAPTER_RUNTIME -> "章节 Runtime" to t.accent
    }
    MigrationSmallBadgeV8(label, color)
}

@Composable
private fun MigrationSmallBadgeV8(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .18f)),
    ) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MigrationQueueNoticeV8(text: String, color: Color, progress: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusMd),
        color = color.copy(alpha = .07f),
        border = BorderStroke(1.dp, color.copy(alpha = .18f)),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (progress) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = color)
            else Icon(if (color == t.destructive) Icons.Rounded.WarningAmber else Icons.Rounded.HourglassTop, null, Modifier.size(17.dp), tint = color)
            Text(text, Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.bodySmall, color = t.foreground)
        }
    }
}
