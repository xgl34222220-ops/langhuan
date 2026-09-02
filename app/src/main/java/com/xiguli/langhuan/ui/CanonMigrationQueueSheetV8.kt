package com.xiguli.langhuan.ui

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.engine.CanonChangeRisk
import com.xiguli.langhuan.engine.CanonMigrationAction
import com.xiguli.langhuan.engine.CanonMigrationExecutionItem
import com.xiguli.langhuan.engine.CanonMigrationExecutionMode
import com.xiguli.langhuan.engine.CanonMigrationTask
import com.xiguli.langhuan.engine.CanonMigrationTaskStatus
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

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
    // Same ViewModelStoreOwner as WritingWorkspaceV6, therefore this resolves the already-created
    // project-scoped instance instead of creating a second migration state owner.
    val migrationVm: CanonChangeProposalViewModel = viewModel()
    val executeSafe = migrationVm::executeReadySafeMigrations
    val queue = state.migrationQueue
    val plan = state.migrationPlan
    val pending = queue?.pending.orEmpty()
    val resolved = queue?.tasks.orEmpty().count { it.status == CanonMigrationTaskStatus.DONE || it.status == CanonMigrationTaskStatus.SKIPPED }
    val next = plan?.next

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp).squircleClip(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Canon 修复编排", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${pending.size} 项未完成${if (resolved > 0) " · $resolved 项已处理/跳过" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
                if (resolved > 0) {
                    TextButton(onClick = onClearResolved) { Text("清理已完成") }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text("V9 自动编排", fontWeight = FontWeight.SemiBold)
                            Text(
                                plan?.summary() ?: "正在读取修复计划",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(
                        "顺序固定为：项目结构 → 章节 Runtime → 长期记忆。自动化只会直接执行确定性的记忆重建；Canon 差异仍必须确认，正文仍必须重新过连续性检查。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
            }

            state.migrationMessage?.let { notice ->
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
                ) {
                    Text(notice, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.migrationError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(15.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(error, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.isMigrationExecuting) {
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(17.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在执行安全同步…", Modifier.padding(start = 9.dp), fontWeight = FontWeight.Medium)
                    }
                }
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
                    modifier = Modifier.fillMaxWidth().squircleClip(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("当前没有待修复项", modifier = Modifier.padding(top = 7.dp), fontWeight = FontWeight.Bold)
                        Text(
                            "后续确认新的 Canon 变更时，受影响内容会自动进入这里并重新编排。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMiuixTokens.current.textSecondary,
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

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                Text("回到写作总控")
            }
            Spacer(Modifier.height(6.dp))
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
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("下一步 · ${item.phase.label}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("${item.task.action.label} · ${item.task.label}", fontWeight = FontWeight.SemiBold)
            when {
                item.blockedByTaskIds.isNotEmpty() -> Text(
                    "等待 ${item.blockedByTaskIds.size} 项前置修复完成后自动解锁。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMiuixTokens.current.textSecondary,
                )
                item.task.status == CanonMigrationTaskStatus.AWAITING_CONFIRMATION -> Text(
                    "差异提案已经生成，先确认或取消当前提案。",
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> Button(
                    onClick = when (item.mode) {
                        CanonMigrationExecutionMode.SAFE_LOCAL -> onExecuteSafe
                        CanonMigrationExecutionMode.PROPOSAL_GATE -> onGenerateRepairProposal
                        CanonMigrationExecutionMode.CHAPTER_RUNTIME -> onOpenChapter
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
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
    val chapterRuntime = task.action == CanonMigrationAction.REWRITE_CHAPTER && task.chapterNumber != null
    val safeMemory = task.action == CanonMigrationAction.REFRESH_MEMORY
    val blocked = item?.blockedByTaskIds.orEmpty().isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(19.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (task.priority == CanonChangeRisk.HIGH || task.status == CanonMigrationTaskStatus.FAILED) Icons.Rounded.WarningAmber else Icons.Rounded.EditNote,
                    null,
                    Modifier.size(18.dp),
                    tint = if (task.priority == CanonChangeRisk.HIGH || task.status == CanonMigrationTaskStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("${task.action.label} · ${task.label}", fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(task.scope)
                            task.chapterNumber?.let { append(" · 第${it}章") }
                            item?.let { append(" · ${it.phase.label} / ${it.mode.label}") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
            }

            Text(task.detail, style = MaterialTheme.typography.bodySmall)
            if (blocked) {
                Text(
                    "等待 ${item?.blockedByTaskIds?.size ?: 0} 项前置修复完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (task.status == CanonMigrationTaskStatus.FAILED) {
                Text("上次执行失败，可从这里重试。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()

            when {
                chapterRuntime -> {
                    OutlinedButton(
                        onClick = onOpenChapter,
                        enabled = !blocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Rounded.EditNote, null, Modifier.size(17.dp))
                        Text("打开第${task.chapterNumber}章修复", Modifier.padding(start = 6.dp))
                    }
                }
                safeMemory -> {
                    OutlinedButton(
                        onClick = onExecuteSafe,
                        enabled = !blocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(17.dp))
                        Text("按当前 Canon 安全重建记忆", Modifier.padding(start = 6.dp))
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onGenerateRepairProposal,
                        enabled = !blocked,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(17.dp))
                        Text(if (task.status == CanonMigrationTaskStatus.FAILED) "重试最小修复提案" else "生成最小修复提案", Modifier.padding(start = 6.dp))
                    }
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
