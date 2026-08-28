package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.engine.ReferenceDistillationReport
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore

/**
 * Explicit Style DNA selector for a single new-book creation session.
 * No report is injected unless the user selects it here.
 */
@Composable
internal fun ReferenceTemplateSelectionPanel(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReferenceDistillationReportStore(context) }
    var reports by remember { mutableStateOf(store.listReports()) }
    var open by remember { mutableStateOf(false) }

    val selected = state.selectedReferenceTemplateIds
    val selectedReports = reports.filter { it.taskId in selected }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LibraryBooks, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text("参考 Style DNA", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            selectedReports.isEmpty() -> "当前不读取任何蒸馏模板"
                            selectedReports.size == 1 -> "仅使用《${selectedReports.first().title}》"
                            else -> "已选择 ${selectedReports.size} 本：${selectedReports.take(3).joinToString("、") { "《${it.title}》" }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "只有这里勾选的蒸馏报告会进入本次聊天和建书蓝图。未选择的作品不会偷偷混入；只借鉴高层结构与节奏，不照搬角色、专名、原句或剧情骨架。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = {
                    reports = store.listReports()
                    open = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.AutoFixHigh, null)
                Spacer(Modifier.width(6.dp))
                Text(if (reports.isEmpty()) "查看蒸馏模板" else "选择 / 切换模板")
            }
        }
    }

    if (open) {
        ReferenceTemplatePickerDialog(
            reports = reports,
            selectedIds = selected,
            onSelectedIds = viewModel::setReferenceTemplateIds,
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun ReferenceTemplatePickerDialog(
    reports: List<ReferenceDistillationReport>,
    selectedIds: List<String>,
    onSelectedIds: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择本次新书参考模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "可以只选一本，也可以多选融合。0 本 = 完全不读取蒸馏模板。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reports.isEmpty()) {
                    Text("还没有完整蒸馏报告。先回书架导入参考小说并完成 AI 蒸馏。")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(reports, key = { it.taskId }) { report ->
                            val checked = report.taskId in selectedIds
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val next = if (checked) selectedIds - report.taskId else selectedIds + report.taskId
                                    onSelectedIds(next.distinct())
                                },
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { value ->
                                                val next = if (value) selectedIds + report.taskId else selectedIds - report.taskId
                                                onSelectedIds(next.distinct())
                                            },
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(report.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${report.chapters} 章 · AI 分层 ${report.samples} 章 · ${report.model.ifBlank { report.provider }}",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    if (report.summary.isNotBlank()) {
                                        Text(report.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(
                                        onClick = { onSelectedIds(listOf(report.taskId)) },
                                        modifier = Modifier.align(Alignment.End),
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("只用这一本")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { onSelectedIds(emptyList()) }) { Text("全部取消") }
        },
    )
}
