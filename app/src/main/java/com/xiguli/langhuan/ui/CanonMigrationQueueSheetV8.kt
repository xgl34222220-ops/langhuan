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
import com.xiguli.langhuan.engine.CanonChangeRisk
import com.xiguli.langhuan.engine.CanonMigrationAction
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
    val queue = state.migrationQueue
    val pending = queue?.pending.orEmpty()
    val resolved = queue?.tasks.orEmpty().count { it.status != CanonMigrationTaskStatus.PENDING }

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
                    Text("Canon 修复队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${pending.size} 项待处理${if (resolved > 0) " · $resolved 项已处理/跳过" else ""}",
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
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "这里不是一键乱改。结构化事实会重新生成 V7 差异提案并再次确认；章纲/正文回到章节 Runtime 修复并重新过连续性检查。",
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
                            "后续确认新的 Canon 变更时，受影响内容会自动进入这里。",
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
                        MigrationTaskCardV8(
                            task = task,
                            onGenerateRepairProposal = { onGenerateRepairProposal(task) },
                            onOpenChapter = { task.chapterNumber?.let(onOpenChapter) },
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
private fun MigrationTaskCardV8(
    task: CanonMigrationTask,
    onGenerateRepairProposal: () -> Unit,
    onOpenChapter: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val chapterRuntime = task.action == CanonMigrationAction.REWRITE_CHAPTER && task.chapterNumber != null
    val manualMemory = task.action == CanonMigrationAction.REFRESH_MEMORY

    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(19.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (task.priority == CanonChangeRisk.HIGH) Icons.Rounded.WarningAmber else Icons.Rounded.EditNote,
                    null,
                    Modifier.size(18.dp),
                    tint = if (task.priority == CanonChangeRisk.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("${task.action.label} · ${task.label}", fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(task.scope)
                            task.chapterNumber?.let { append(" · 第${it}章") }
                            append(" · ")
                            append(
                                when (task.priority) {
                                    CanonChangeRisk.LOW -> "低优先级"
                                    CanonChangeRisk.MEDIUM -> "中优先级"
                                    CanonChangeRisk.HIGH -> "高优先级"
                                }
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
            }

            Text(task.detail, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()

            when {
                chapterRuntime -> {
                    OutlinedButton(onClick = onOpenChapter, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Rounded.EditNote, null, Modifier.size(17.dp))
                        Text("打开第${task.chapterNumber}章修复", Modifier.padding(start = 6.dp))
                    }
                }
                manualMemory -> {
                    Text(
                        "长期记忆会在后续正式整理/重建时按新 Canon 刷新；确认已整理后可标记完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
                else -> {
                    OutlinedButton(onClick = onGenerateRepairProposal, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(17.dp))
                        Text("生成最小修复提案", Modifier.padding(start = 6.dp))
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
