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
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
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

/** Explicit searchable Reference DNA selector for one new-book creation session. */
@Composable
internal fun ReferenceTemplateSelectionPanel(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReferenceDistillationReportStore(context) }
    var reports by remember { mutableStateOf(store.listReports()) }
    var open by remember { mutableStateOf(false) }

    val selected = state.selectedReferenceTemplateIds
    val selectedReports = reports.filter { it.taskId in selected }
    val totalSearchable = selectedReports.sumOf(store::retainedItemCount)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                    Icon(Icons.Rounded.LibraryBooks, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Reference DNA", fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            selectedReports.isEmpty() -> "本次创作未绑定参考"
                            selectedReports.size == 1 -> "《${selectedReports.first().title}》 · 可检索 $totalSearchable 条"
                            else -> "已选 ${selectedReports.size} 本 · 共可检索 $totalSearchable 条 DNA"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (selectedReports.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniDnaBadge(Icons.Rounded.AutoAwesome, "Style / 方法")
                    MiniDnaBadge(Icons.Rounded.MenuBook, if (selectedReports.any(store::hasStoryDna)) "Story / 事实" else "无 Story")
                    MiniDnaBadge(Icons.Rounded.Search, "$totalSearchable 条")
                }
            }

            if (state.lastReferenceUsage.isNotBlank()) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)) {
                    Text(
                        state.lastReferenceUsage,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                "V2 不再把整份报告一股脑塞给 AI：每轮对话会按你当前这句话主动检索相关 DNA。问原作事实可读 Story；设计自己的小说只迁移 Style / KEEP / TRANSFORM，并遵守 AVOID。正式建书后，所选 DNA 会继续绑定到作品并供场景、正文和主编调用。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilledTonalButton(
                onClick = { reports = store.listReports(); open = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(6.dp))
                Text(if (reports.isEmpty()) "查看蒸馏档案" else "选择参考 DNA")
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
    Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text("选择参考 DNA")
                Text("可多选；选中的档案会主动检索，而不是简单拼接摘要", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (reports.isEmpty()) {
                    Text("还没有蒸馏报告。先回书架导入参考小说并完成 AI 蒸馏。")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("完成") } },
        dismissButton = { TextButton(onClick = { onSelectedIds(emptyList()) }) { Text("清空选择") } },
    )
}

@Composable
private fun TemplateReportCard(
    report: ReferenceDistillationReport,
    checked: Boolean,
    store: ReferenceDistillationReportStore,
    onToggle: (Boolean) -> Unit,
) {
    val searchable = store.retainedItemCount(report)
    val counts = store.kindCounts(report)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onToggle(!checked) },
        shape = RoundedCornerShape(18.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        tonalElevation = if (checked) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onToggle)
                Column(Modifier.weight(1f)) {
                    Text(report.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${store.coverageLabel(report)} · 全书 ${report.chapters} 章 · AI 深读 ${report.samples} 章 · 可检索 $searchable 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                counts["STORY"]?.takeIf { it > 0 }?.let { MiniDnaBadge(Icons.Rounded.MenuBook, "Story $it") }
                counts["STYLE"]?.takeIf { it > 0 }?.let { MiniDnaBadge(Icons.Rounded.AutoAwesome, "Style $it") }
                MiniDnaBadge(Icons.Rounded.Search, if (report.retrievalItems.isNotEmpty()) "V2" else "旧版")
            }

            if (report.summary.isNotBlank()) {
                Text(report.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (report.retrievalItems.isEmpty() && !report.legacySummaryOnly) {
                Text("旧版报告可继续使用最终 DNA；若要恢复完整批次知识，需要重新蒸馏一次。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
