package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.engine.BuiltInReferenceLibraryInstaller
import com.xiguli.langhuan.engine.ReferenceDistillationReport
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanSpatialHero
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

    LanghuanSpatialHero(
        title = "Reference DNA",
        subtitle = when {
            loading -> "正在校验内置参考库……"
            selectedReports.isEmpty() -> "共 ${reports.size} 本参考 · 本次创作尚未绑定"
            selectedReports.size == 1 -> "已绑定《${selectedReports.first().title}》 · $totalSearchable 条可检索 DNA"
            else -> "已绑定 ${selectedReports.size} 本 · $totalSearchable 条可检索 DNA"
        },
        eyebrow = "REFERENCE DNA",
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    color = t.muted,
                    contentColor = t.strong,
                    shadowElevation = 1.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.LibraryBooks, null, Modifier.size(21.dp), tint = t.strong)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("参考库", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text(
                        if (selectedReports.isEmpty()) "选择一本或多本参考，完整 DNA 仍可单独查看" else "Story / Style / 群像 / 关系 / 规则会按需检索",
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
                    color = t.accent.copy(alpha = .34f),
                    contentColor = t.accentForeground,
                ) {
                    Text(
                        state.lastReferenceUsage,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = t.accentForeground,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                "每份参考都可以展开完整蒸馏数据库；选择引用与查看数据相互独立，不会因为没勾选就把蒸馏内容藏起来。",
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
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.90f),
            shape = RoundedCornerShape(t.radiusXl),
            color = t.background,
            contentColor = t.foreground,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reference DNA", style = MaterialTheme.typography.headlineSmall, color = t.foreground)
                        Text(
                            "共 ${reports.size} 本 · 内置 ${builtIns.size} · 我的蒸馏 ${userReports.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                    if (selectedIds.isNotEmpty()) LanghuanBadge("${selectedIds.size} 已选", accent = true)
                    Spacer(Modifier.width(6.dp))
                    LanghuanIconButton(Icons.Rounded.Close, "关闭", onDismiss)
                }

                if (reports.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f).padding(18.dp), contentAlignment = Alignment.Center) {
                        LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                            Text("没有找到参考 DNA。", color = t.mutedForeground)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = { onSelectedIds(emptyList()) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(t.radiusMd),
                        enabled = selectedIds.isNotEmpty(),
                    ) { Text("清空选择") }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                    ) { Text("完成") }
                }
            }
        }
    }

    browseTarget?.let { report ->
        ReferenceDistillationDataBrowserDialog(
            report = report,
            store = store,
            onDismiss = { browseTarget = null },
        )
    }

    deleteTarget?.let { report ->
        Dialog(onDismissRequest = { deleteTarget = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(t.radiusXl),
                color = t.card,
                contentColor = t.foreground,
                shadowElevation = 8.dp,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("删除《${report.title}》的蒸馏数据？", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "会删除这份 Story DNA、Style DNA 和可检索条目。不会删除你手机上的原始 EPUB/TXT 文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { deleteTarget = null }, modifier = Modifier.weight(1f)) { Text("取消") }
                        Button(
                            onClick = { onDelete(report); deleteTarget = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = t.destructive, contentColor = t.destructiveForeground),
                        ) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnaSectionLabel(title: String, subtitle: String) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
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

    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                tint = t.primary,
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
                    LanghuanIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "删除蒸馏数据",
                        onClick = onDelete,
                    )
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
                FilledTonalButton(
                    onClick = { onToggle(!checked) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Text(if (checked) "取消引用" else "引用这份 DNA")
                }
                Button(
                    onClick = onView,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusMd),
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
