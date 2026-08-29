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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.engine.BuiltInReferenceLibraryInstaller
import com.xiguli.langhuan.engine.ReferenceDistillationReport
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reference DNA selector.
 *
 * Important UX rule: this is the page users actually manage references from, so install/refresh
 * and user-report deletion must happen here instead of being hidden inside a report detail dialog.
 */
@Composable
internal fun ReferenceTemplateSelectionPanel(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReferenceDistillationReportStore(context) }
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf(emptyList<ReferenceDistillationReport>()) }
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    fun refresh(openAfter: Boolean = false) {
        scope.launch {
            loading = true
            reports = withContext(Dispatchers.IO) {
                // Self-heal every time the real picker is opened. This avoids relying only on
                // Application.onCreate() and guarantees packaged built-ins are revalidated.
                BuiltInReferenceLibraryInstaller.install(context)
                store.listReports()
            }
            loading = false
            if (openAfter) open = true
        }
    }

    LaunchedEffect(Unit) { refresh() }

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
                            loading -> "正在校验内置参考库……"
                            selectedReports.isEmpty() -> "共 ${reports.size} 本 · 本次创作未绑定参考"
                            selectedReports.size == 1 -> "共 ${reports.size} 本 · 《${selectedReports.first().title}》 · 可检索 $totalSearchable 条"
                            else -> "共 ${reports.size} 本 · 已选 ${selectedReports.size} 本 · 可检索 $totalSearchable 条 DNA"
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
                "每轮对话会按当前问题主动检索已选 DNA；正式建书后继续绑定到场景、正文与主编。内置参考锁定，用户自己蒸馏的报告可直接在列表中删除。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FilledTonalButton(
                onClick = { refresh(openAfter = true) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(6.dp))
                Text(if (loading) "正在刷新……" else "选择参考 DNA")
            }
        }
    }

    if (open) {
        ReferenceTemplatePickerDialog(
            reports = reports,
            selectedIds = selected,
            store = store,
            onSelectedIds = viewModel::setReferenceTemplateIds,
            onDelete = { report ->
                scope.launch {
                    withContext(Dispatchers.IO) { store.delete(report.taskId) }
                    viewModel.setReferenceTemplateIds(state.selectedReferenceTemplateIds - report.taskId)
                    reports = withContext(Dispatchers.IO) { store.listReports() }
                }
            },
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
    onDelete: (ReferenceDistillationReport) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<ReferenceDistillationReport?>(null) }
    val builtIns = reports.filter { it.taskId.startsWith("builtin:") }
    val userReports = reports.filterNot { it.taskId.startsWith("builtin:") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("选择参考 DNA · 共 ${reports.size} 本")
                Text("内置 ${builtIns.size} 本 · 我的蒸馏 ${userReports.size} 本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (reports.isEmpty()) {
                    Text("没有找到参考 DNA。")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (builtIns.isNotEmpty()) {
                            item { Text("内置参考", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            items(builtIns, key = { it.taskId }) { report ->
                                TemplateReportCard(report, report.taskId in selectedIds, store, false, onToggle = { value ->
                                    onSelectedIds(if (value) (selectedIds + report.taskId).distinct() else selectedIds - report.taskId)
                                }, onDelete = {})
                            }
                        }
                        if (userReports.isNotEmpty()) {
                            item { Text("我的蒸馏", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            items(userReports, key = { it.taskId }) { report ->
                                TemplateReportCard(report, report.taskId in selectedIds, store, true, onToggle = { value ->
                                    onSelectedIds(if (value) (selectedIds + report.taskId).distinct() else selectedIds - report.taskId)
                                }, onDelete = { deleteTarget = report })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) { Text("完成") } },
        dismissButton = { TextButton(onClick = { onSelectedIds(emptyList()) }) { Text("清空选择") } },
    )

    deleteTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除《${report.title}》的蒸馏数据？") },
            text = { Text("会删除这份 Story DNA、Style DNA 和可检索条目。不会删除你手机上的原始 EPUB/TXT 文件。") },
            confirmButton = {
                Button(
                    onClick = { onDelete(report); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TemplateReportCard(
    report: ReferenceDistillationReport,
    checked: Boolean,
    store: ReferenceDistillationReportStore,
    deletable: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
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
                        "${if (report.taskId.startsWith("builtin:")) "内置 · " else ""}${store.coverageLabel(report)} · ${report.chapters} 章 · 可检索 $searchable 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (deletable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, "删除蒸馏数据", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                counts["STORY"]?.takeIf { it > 0 }?.let { MiniDnaBadge(Icons.Rounded.MenuBook, "Story $it") }
                counts["STYLE"]?.takeIf { it > 0 }?.let { MiniDnaBadge(Icons.Rounded.AutoAwesome, "Style $it") }
                MiniDnaBadge(Icons.Rounded.Search, if (report.retrievalItems.isNotEmpty()) "V2" else "旧版")
            }
            if (report.summary.isNotBlank()) {
                Text(report.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
