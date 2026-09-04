package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ReferenceTemplateSelectionPanel(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReferenceDistillationReportStore(context) }
    val scope = rememberCoroutineScope()
    val t = LocalLanghuanUiTokens.current
    var reports by remember { mutableStateOf(emptyList<ReferenceDistillationReport>()) }
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    fun refresh(openAfter: Boolean = false) {
        scope.launch {
            loading = true
            reports = withContext(Dispatchers.IO) {
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
    val selectedWithStory = selectedReports.count(store::hasStoryDna)

    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    color = t.warmSurface,
                    contentColor = t.accent,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .14f)),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.LibraryBooks, null, Modifier.size(21.dp), tint = t.accent)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Reference DNA", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text(
                        when {
                            loading -> "正在校验内置参考库……"
                            selectedReports.isEmpty() -> "共 ${reports.size} 本参考 · 本次创作尚未绑定"
                            selectedReports.size == 1 -> "已绑定《${selectedReports.first().title}》 · $totalSearchable 条可检索 DNA"
                            else -> "已绑定 ${selectedReports.size} 本 · $totalSearchable 条可检索 DNA"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = t.mutedForeground,
                    )
                }
                if (selectedReports.isNotEmpty()) LanghuanBadge("${selectedReports.size} 已选", accent = true)
            }

            if (selectedReports.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    DnaBadge(Icons.Rounded.AutoAwesome, "Style 方法")
                    DnaBadge(Icons.Rounded.MenuBook, "Story $selectedWithStory/${selectedReports.size}")
                    DnaBadge(Icons.Rounded.Search, "$totalSearchable 条")
                }
            }

            if (state.lastReferenceUsage.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(t.radiusSm),
                    color = t.warmSurface,
                    contentColor = t.accent,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .12f)),
                ) {
                    Text(
                        state.lastReferenceUsage,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = t.accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                "每份参考都可以单独打开完整蒸馏数据库，查看 Story / Style DNA 与全部可检索条目；选择参考和查看数据是两个独立动作。",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )

            Button(
                onClick = { refresh(openAfter = true) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(t.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = t.foreground,
                    contentColor = t.primaryForeground,
                ),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = t.primaryForeground)
                } else {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(7.dp))
                Text(if (loading) "正在刷新……" else "选择参考 / 查看完整 DNA")
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
private fun DnaBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = t.muted,
        contentColor = t.mutedForeground,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = t.mutedForeground)
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
    val t = LocalLanghuanUiTokens.current
    var deleteTarget by remember { mutableStateOf<ReferenceDistillationReport?>(null) }
    var browseTarget by remember { mutableStateOf<ReferenceDistillationReport?>(null) }
    val builtIns = reports.filter { it.taskId.startsWith("builtin:") }
    val userReports = reports.filterNot { it.taskId.startsWith("builtin:") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(t.radiusXl),
        containerColor = t.background,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Reference DNA", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                Text(
                    "共 ${reports.size} 本 · 内置 ${builtIns.size} · 我的蒸馏 ${userReports.size} · 已选 ${selectedIds.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        },
        text = {
            if (reports.isEmpty()) {
                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                    Text("没有找到参考 DNA。", color = t.mutedForeground)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (builtIns.isNotEmpty()) {
                        item { DnaSectionLabel("内置参考", "随琅嬛提供，可直接选择和查看") }
                        items(builtIns, key = { it.taskId }) { report ->
                            TemplateReportCard(
                                report = report,
                                checked = report.taskId in selectedIds,
                                store = store,
                                deletable = false,
                                onToggle = { value ->
                                    onSelectedIds(if (value) (selectedIds + report.taskId).distinct() else selectedIds - report.taskId)
                                },
                                onView = { browseTarget = report },
                                onDelete = {},
                            )
                        }
                    }
                    if (userReports.isNotEmpty()) {
                        item { DnaSectionLabel("我的蒸馏", "由你导入的作品生成") }
                        items(userReports, key = { it.taskId }) { report ->
                            TemplateReportCard(
                                report = report,
                                checked = report.taskId in selectedIds,
                                store = store,
                                deletable = true,
                                onToggle = { value ->
                                    onSelectedIds(if (value) (selectedIds + report.taskId).distinct() else selectedIds - report.taskId)
                                },
                                onView = { browseTarget = report },
                                onDelete = { deleteTarget = report },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(t.radiusSm),
                colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
            ) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = { onSelectedIds(emptyList()) }) { Text("清空选择", color = t.mutedForeground) }
        },
    )

    browseTarget?.let { report ->
        ReferenceDistillationDataBrowserDialog(
            report = report,
            store = store,
            onDismiss = { browseTarget = null },
        )
    }

    deleteTarget?.let { report ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除《${report.title}》的蒸馏数据？") },
            text = { Text("会删除这份 Story DNA、Style DNA 和可检索条目。不会删除你手机上的原始 EPUB/TXT 文件。") },
            confirmButton = {
                Button(
                    onClick = { onDelete(report); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = t.destructive, contentColor = t.destructiveForeground),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DnaSectionLabel(title: String, subtitle: String) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 1.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = t.foreground)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
    }
}

@Composable
private fun TemplateReportCard(
    report: ReferenceDistillationReport,
    checked: Boolean,
    store: ReferenceDistillationReportStore,
    deletable: Boolean,
    onToggle: (Boolean) -> Unit,
    onView: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val searchable = store.retainedItemCount(report)
    val counts = store.kindCounts(report)
    val storyCount = counts["STORY"] ?: 0
    val styleCount = counts["STYLE"] ?: 0

    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = onToggle)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            report.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = t.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (checked) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                "已选择",
                                modifier = Modifier.padding(start = 6.dp).size(16.dp),
                                tint = t.accent,
                            )
                        }
                    }
                    Text(
                        "${if (report.taskId.startsWith("builtin:")) "内置 · " else ""}${store.coverageLabel(report)} · ${report.chapters} 章 · $searchable 条可检索",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.mutedForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (deletable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, "删除蒸馏数据", tint = t.destructive)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DnaBadge(Icons.Rounded.MenuBook, "Story $storyCount")
                DnaBadge(Icons.Rounded.AutoAwesome, "Style $styleCount")
                DnaBadge(Icons.Rounded.Search, if (report.retrievalItems.isNotEmpty()) "Retrieval V2" else "旧版索引")
            }

            if (report.summary.isNotBlank()) {
                Text(
                    report.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = t.mutedForeground,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onToggle(!checked) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusSm),
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Text(if (checked) "取消引用" else "引用这份 DNA")
                }
                Button(
                    onClick = onView,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusSm),
                    colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                ) {
                    Icon(Icons.Rounded.DataObject, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("查看全部数据")
                }
            }
        }
    }
}
