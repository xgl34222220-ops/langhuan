package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiguli.langhuan.engine.ReferenceDistillationReport
import com.xiguli.langhuan.engine.ReferenceDistillationReportItem
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReferenceDistillationReportDialog(
    taskId: String,
    title: String,
    fallbackProvider: String,
    fallbackModel: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { ReferenceDistillationReportStore(context) }
    val t = LocalLanghuanUiTokens.current
    var report by remember(taskId, title) { mutableStateOf<ReferenceDistillationReport?>(null) }
    var loaded by remember(taskId, title) { mutableStateOf(false) }
    var confirmDelete by remember(taskId) { mutableStateOf(false) }
    var showDataBrowser by remember(taskId) { mutableStateOf(false) }

    LaunchedEffect(taskId, title) {
        report = withContext(Dispatchers.IO) { store.loadOrArchiveFallback(taskId, title) }
        loaded = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
            shape = LanghuanShape.sheet,
            color = t.background,
            contentColor = t.foreground,
            border = BorderStroke(1.dp, t.border),
            shadowElevation = 12.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = LanghuanShape.chip,
                        color = t.warmSurface,
                        contentColor = t.accent,
                        border = BorderStroke(1.dp, t.accent.copy(alpha = .14f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Science, null, Modifier.size(21.dp), tint = t.accent)
                        }
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("蒸馏报告", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                        Text(title, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    if (report != null && !report!!.taskId.startsWith("builtin:")) {
                        LanghuanIconButton(
                            icon = Icons.Rounded.DeleteOutline,
                            contentDescription = "删除蒸馏数据",
                            onClick = { confirmDelete = true },
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    LanghuanIconButton(Icons.Rounded.Close, "关闭", onDismiss)
                }

                when {
                    !loaded -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.accent)
                                Text(
                                    "正在读取 Story DNA / Style DNA……",
                                    Modifier.padding(top = 10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                    }

                    report == null -> {
                        Box(Modifier.fillMaxWidth().weight(1f).padding(18.dp), contentAlignment = Alignment.Center) {
                            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 20.dp) {
                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("没有找到完整蒸馏结果", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                    Text(
                                        "这个任务可能来自更早版本，当时没有保存完整报告。重新蒸馏后会永久保存 Story DNA、Style DNA、覆盖度和可检索 DNA。",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = t.mutedForeground,
                                    )
                                }
                            }
                        }
                    }

                    else -> DistillationReportContent(
                        report = report!!,
                        store = store,
                        fallbackProvider = fallbackProvider,
                        fallbackModel = fallbackModel,
                        onBrowseAll = { showDataBrowser = true },
                    )
                }
            }
        }
    }

    report?.takeIf { showDataBrowser }?.let { current ->
        ReferenceDistillationDataBrowserDialog(
            report = current,
            store = store,
            onDismiss = { showDataBrowser = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = LanghuanShape.sheet,
            containerColor = t.background,
            title = { Text("删除这份蒸馏数据？", color = t.foreground) },
            text = {
                Text(
                    "《${report?.title ?: title}》的 Story DNA、Style DNA 和可检索数据会从本机删除。原始 EPUB 文件不会被删除。",
                    color = t.mutedForeground,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    report?.taskId?.let(store::delete) ?: store.delete(taskId)
                    confirmDelete = false
                    onDismiss()
                }) { Text("删除", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun DistillationReportContent(
    report: ReferenceDistillationReport,
    store: ReferenceDistillationReportStore,
    fallbackProvider: String,
    fallbackModel: String,
    onBrowseAll: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val provider = report.provider.ifBlank { fallbackProvider }
    val model = report.model.ifBlank { fallbackModel }
    val storyItems = report.items.filter { it.kind == "STORY" }
    val styleItems = report.items.filter { it.kind == "STYLE" || it.kind == "DNA" }
    val keepItems = report.items.filter { it.kind == "KEEP" }
    val transformItems = report.items.filter { it.kind == "TRANSFORM" }
    val avoidItems = report.items.filter { it.kind == "AVOID" }
    val hasStory = store.hasStoryDna(report)
    val retainedCount = store.retainedItemCount(report)
    val counts = store.kindCounts(report)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = LanghuanShape.chip,
                            color = t.warmSurface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = t.accent)
                            }
                        }
                        Column(Modifier.padding(start = 9.dp).weight(1f)) {
                            Text(
                                if (report.retrievalItems.isNotEmpty()) "V2 可检索 DNA 已保存" else "已进入长期研究档案",
                                style = MaterialTheme.typography.titleSmall,
                                color = t.foreground,
                            )
                            Text(store.coverageDescription(report), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        }
                        LanghuanBadge(store.coverageLabel(report), accent = true)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        SmallInfoPill("全书 ${report.chapters.coerceAtLeast(0)} 章")
                        SmallInfoPill("深读 ${report.samples.coerceAtLeast(0)} 章")
                        SmallInfoPill("可检索 $retainedCount 条")
                    }

                    if (retainedCount > 0) {
                        Text(
                            listOf("STORY", "STYLE", "KEEP", "TRANSFORM", "AVOID")
                                .mapNotNull { kind -> counts[kind]?.takeIf { it > 0 }?.let { "$kind $it" } }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = t.mutedForeground,
                        )
                    }
                    if (provider.isNotBlank() || model.isNotBlank()) {
                        Text(
                            "${provider.ifBlank { "未知服务" }} · ${model.ifBlank { "未知模型" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.mutedForeground,
                        )
                    }

                    when {
                        report.legacySummaryOnly -> ReportWarning("旧版任务只留下摘要，没有批次级可检索 DNA。重新蒸馏一次即可升级到 V2。")
                        report.retrievalItems.isEmpty() -> ReportWarning("这份报告来自旧版蒸馏：最终 DNA 仍可使用，但被压缩掉的批次观察无法恢复。重新蒸馏可获得完整 V2 知识库。")
                        !hasStory -> ReportWarning("这份 V2 报告暂未提取到可靠 Story DNA；事实问答会明确提示未确认，不会编造。")
                    }

                    Button(
                        onClick = onBrowseAll,
                        enabled = retainedCount > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shape = LanghuanShape.chip,
                        colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                    ) {
                        Icon(Icons.Rounded.DataObject, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("查看全部 $retainedCount 条 DNA 数据")
                    }
                }
            }
        }

        if (hasStory || report.overview.isNotBlank()) {
            item { SectionHeader(Icons.Rounded.MenuBook, "Story DNA · 作品结构", "主角、群像、世界观、规则、人物关系与剧情阶段") }
            if (report.overview.isNotBlank()) item { ReportTextSection("作品结构总览", report.overview, story = true) }
            items(storyItems) { item -> DistillationItemCard(item, story = true) }
        }

        if (report.summary.isNotBlank() || styleItems.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.AutoAwesome, "Style DNA · 写法", "视角、节奏、悬念、信息释放与叙事组织") }
            if (report.summary.isNotBlank()) item { ReportTextSection("写法摘要", report.summary, story = false) }
            items(styleItems) { item -> DistillationItemCard(item, story = false) }
        }

        if (report.localMetrics.isNotBlank()) item { ReportTextSection("全书本地结构统计", report.localMetrics, story = false) }
        if (keepItems.isNotEmpty()) {
            item { SectionText("KEEP · 可借鉴的高层机制") }
            items(keepItems) { item -> DistillationItemCard(item, story = false) }
        }
        if (transformItems.isNotEmpty()) {
            item { SectionText("TRANSFORM · 必须原创化改造") }
            items(transformItems) { item -> DistillationItemCard(item, story = false) }
        }
        if (avoidItems.isNotEmpty()) {
            item { SectionText("AVOID · 禁止照搬") }
            items(avoidItems) { item -> DistillationItemCard(item, story = false) }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.chip,
                color = t.muted,
                border = BorderStroke(1.dp, t.border),
            ) {
                Text(
                    if (report.retrievalItems.isNotEmpty()) {
                        "V2 DNA 已就绪 · 对话和写作会按当前任务主动检索"
                    } else {
                        "旧版 DNA 可继续使用 · 建议重新蒸馏升级 V2"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = t.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun ReportWarning(text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = LanghuanShape.chip,
        color = t.destructive.copy(alpha = .07f),
        border = BorderStroke(1.dp, t.destructive.copy(alpha = .16f)),
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = t.destructive,
        )
    }
}

@Composable
private fun SmallInfoPill(text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = LanghuanShape.pill,
        color = t.muted,
        border = BorderStroke(1.dp, t.border),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    val t = LocalLanghuanUiTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = LanghuanShape.chip,
            color = t.muted,
            border = BorderStroke(1.dp, t.border),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = t.foreground) }
        }
        Column(Modifier.padding(start = 9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
        }
    }
}

@Composable
private fun SectionText(text: String) {
    val t = LocalLanghuanUiTokens.current
    Text(text, style = MaterialTheme.typography.titleMedium, color = t.foreground)
}

@Composable
private fun ReportTextSection(title: String, text: String, story: Boolean) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanghuanBadge(if (story) "STORY" else "STYLE", accent = story)
                Text(title, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.titleSmall, color = t.foreground)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = t.foreground)
        }
    }
}

@Composable
private fun DistillationItemCard(item: ReferenceDistillationReportItem, story: Boolean) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 13.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanghuanBadge(item.kind.ifBlank { if (story) "STORY" else "DNA" }, accent = story)
                Text(
                    dimensionLabel(item.dimension),
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = t.mutedForeground,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(item.value, style = MaterialTheme.typography.bodyMedium, color = t.foreground)
            if (item.evidence.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LanghuanShape.chip,
                    color = t.muted,
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Text(
                        "依据：${item.evidence}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }
        }
    }
}

private fun dimensionLabel(value: String): String = when (value.trim().uppercase()) {
    "POV" -> "叙事视角 / 距离"
    "RHYTHM" -> "句段与节奏"
    "DIALOGUE" -> "对白使用"
    "INFO" -> "信息释放"
    "SUSPENSE" -> "悬念 / 章末钩子"
    "CHARACTER", "CHARACTERIZATION" -> "人物塑造手法"
    "RULE_PRESENTATION" -> "规则呈现手法"
    "SCENE" -> "场景切换"
    "EMOTION" -> "情绪温度"
    "STRUCTURE" -> "叙事结构"
    "PROTAGONIST" -> "主角设定"
    "SUPPORTING" -> "重要配角"
    "RELATIONSHIP" -> "人物关系"
    "WORLD" -> "世界观"
    "RULE" -> "世界规则"
    "POWER" -> "能力 / 成长体系"
    "FACTION" -> "势力 / 组织"
    "LOCATION" -> "关键地点"
    "CONFLICT" -> "核心冲突"
    "MYSTERY" -> "核心谜团"
    "ARC" -> "剧情阶段 / 主线演化"
    "PROGRESSION" -> "成长与升级路线"
    "THEME" -> "主题命题"
    else -> value.ifBlank { "高层特征" }
}
