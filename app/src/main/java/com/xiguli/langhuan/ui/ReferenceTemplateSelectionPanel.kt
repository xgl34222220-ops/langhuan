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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** Explicit dual-DNA selector for one new-book creation session. */
@Composable
internal fun ReferenceTemplateSelectionPanel(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReferenceDistillationReportStore(context) }
    var reports by remember { mutableStateOf(store.listReports()) }
    var open by remember { mutableStateOf(false) }

    val selected = state.selectedReferenceTemplateIds
    val selectedReports = reports.filter { it.taskId in selected }
    val single = selectedReports.singleOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Icon(
                        Icons.Rounded.LibraryBooks,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(9.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("参考双层 DNA", fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            selectedReports.isEmpty() -> "本次创作不读取任何蒸馏模板"
                            single != null -> "仅使用《${single.title}》 · ${store.coverageLabel(single)}"
                            else -> "融合 ${selectedReports.size} 份模板 · ${selectedReports.take(2).joinToString(" + ") { "《${it.title}》" }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (single != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniDnaBadge(Icons.Rounded.AutoAwesome, "Style DNA")
                    MiniDnaBadge(
                        Icons.Rounded.MenuBook,
                        if (store.hasStoryDna(single)) "Story DNA" else "旧版无 Story DNA",
                    )
                }
            }

            Text(
                "只有这里选中的模板会进入聊天和建书蓝图。Style DNA 参考写法；Story DNA 只帮助理解原作主角、世界、规则和结构，最终人物、能力、专名和剧情必须重新原创。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilledTonalButton(
                onClick = {
                    reports = store.listReports()
                    open = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(6.dp))
                Text(if (reports.isEmpty()) "查看蒸馏模板" else "选择 / 切换参考模板")
            }
        }
    }

    if (open) {
        ReferenceTemplatePickerDialog(
            reports = reports,
            selectedIds = selected,
            store = store,
            onSelectedIds = viewModel::setReferenceTemplateIds,
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun MiniDnaBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReferenceTemplatePickerDialog(
    reports: List<ReferenceDistillationReport>,
    selectedIds: List<String>,
    store: ReferenceDistillationReportStore,
    onSelectedIds: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("选择本次新书参考")
                Text(
                    "可只读一本，也可多本融合",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (reports.isEmpty()) {
                    Text("还没有完整蒸馏报告。先回书架导入参考小说并完成 AI 蒸馏。")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(reports, key = { it.taskId }) { report ->
                            val checked = report.taskId in selectedIds
                            TemplateReportCard(
                                report = report,
                                checked = checked,
                                store = store,
                                onToggle = { value ->
                                    val next = if (value) selectedIds + report.taskId else selectedIds - report.taskId
                                    onSelectedIds(next.distinct())
                                },
                                onOnlyThis = { onSelectedIds(listOf(report.taskId)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { onSelectedIds(emptyList()) }) { Text("本次不使用模板") }
        },
    )
}

@Composable
private fun TemplateReportCard(
    report: ReferenceDistillationReport,
    checked: Boolean,
    store: ReferenceDistillationReportStore,
    onToggle: (Boolean) -> Unit,
    onOnlyThis: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) },
        shape = RoundedCornerShape(18.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        tonalElevation = if (checked) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onToggle)
                Column(Modifier.weight(1f)) {
                    Text(
                        report.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${store.coverageLabel(report)} · 全书 ${report.chapters} 章 · AI 深度 ${report.samples} 章",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniDnaBadge(Icons.Rounded.AutoAwesome, "Style")
                MiniDnaBadge(
                    Icons.Rounded.MenuBook,
                    if (store.hasStoryDna(report)) "Story" else "旧版仅 Style",
                )
            }

            if (report.summary.isNotBlank()) {
                Text(
                    report.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onOnlyThis, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.width(4.dp))
                Text("只用这一本")
            }
        }
    }
}
