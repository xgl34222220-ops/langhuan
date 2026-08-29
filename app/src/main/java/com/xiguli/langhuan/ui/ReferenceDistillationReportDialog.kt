package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    var report by remember(taskId, title) { mutableStateOf<ReferenceDistillationReport?>(null) }
    var loaded by remember(taskId, title) { mutableStateOf(false) }

    LaunchedEffect(taskId, title) {
        report = withContext(Dispatchers.IO) { store.loadOrArchiveFallback(taskId, title) }
        loaded = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f),
            shape = RoundedCornerShape(30.dp),
            tonalElevation = 8.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("双层蒸馏报告", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                when {
                    !loaded -> Column(Modifier.padding(24.dp)) { Text("正在读取 Style DNA / Story DNA……") }
                    report == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("没有找到可读取的蒸馏结果", fontWeight = FontWeight.Bold)
                        Text(
                            "这个任务可能来自更早版本，当时没有保存完整报告。重新蒸馏后会永久保存 Story DNA、Style DNA、覆盖度和可检索 DNA。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> DistillationReportContent(report!!, store, fallbackProvider, fallbackModel)
                }
            }
        }
    }
}

@Composable
private fun DistillationReportContent(
    report: ReferenceDistillationReport,
    store: ReferenceDistillationReportStore,
    fallbackProvider: String,
    fallbackModel: String,
) {
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(7.dp))
                        Text(if (report.retrievalItems.isNotEmpty()) "V2 可检索 DNA 已保存" else "已进入长期研究档案", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                            Text(
                                store.coverageLabel(report),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Text(store.coverageDescription(report), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallInfoPill("全书 ${report.chapters.coerceAtLeast(0)} 章")
                        SmallInfoPill("AI 深读 ${report.samples.coerceAtLeast(0)} 章")
                        SmallInfoPill("可检索 $retainedCount 条")
                    }
                    if (retainedCount > 0) {
                        Text(
                            listOf("STORY", "STYLE", "KEEP", "TRANSFORM", "AVOID")
                                .mapNotNull { kind -> counts[kind]?.takeIf { it > 0 }?.let { "$kind $it" } }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (provider.isNotBlank() || model.isNotBlank()) {
                        Text("${provider.ifBlank { "未知服务" }} · ${model.ifBlank { "未知模型" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when {
                        report.legacySummaryOnly -> Text("旧版任务只留下摘要，没有批次级可检索 DNA。重新蒸馏一次即可升级到 V2。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        report.retrievalItems.isEmpty() -> Text("这份报告来自旧版蒸馏：最终 DNA 仍可使用，但之前被压缩掉的批次观察无法恢复。重新蒸馏一次可获得完整 V2 知识库。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        !hasStory -> Text("这份 V2 报告暂未提取到可靠 Story DNA；事实问答会明确提示未确认，不会编造。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (hasStory || report.overview.isNotBlank()) {
            item { SectionHeader(Icons.Rounded.MenuBook, "Story DNA · 作品结构", "理解主角、世界观、规则、人物关系与剧情阶段") }
            if (report.overview.isNotBlank()) item { ReportTextSection("作品结构总览", report.overview, story = true) }
            items(storyItems) { item -> DistillationItemCard(item, story = true) }
        }

        if (report.summary.isNotBlank() || styleItems.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.AutoAwesome, "Style DNA · 写法", "理解视角、节奏、悬念、信息释放与叙事组织") }
            if (report.summary.isNotBlank()) item { ReportTextSection("写法摘要", report.summary, story = false) }
            items(styleItems) { item -> DistillationItemCard(item, story = false) }
        }

        if (report.localMetrics.isNotBlank()) item { ReportTextSection("全书本地结构统计", report.localMetrics, story = false) }
        if (keepItems.isNotEmpty()) {
            item { Text("KEEP · 可借鉴的高层机制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(keepItems) { item -> DistillationItemCard(item, story = false) }
        }
        if (transformItems.isNotEmpty()) {
            item { Text("TRANSFORM · 必须原创化改造", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(transformItems) { item -> DistillationItemCard(item, story = false) }
        }
        if (avoidItems.isNotEmpty()) {
            item { Text("AVOID · 禁止照搬", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(avoidItems) { item -> DistillationItemCard(item, story = false) }
        }

        if (!report.legacySummaryOnly) {
            item {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(if (report.retrievalItems.isNotEmpty()) "V2 DNA 已就绪 · 对话和写作会按当前任务主动检索" else "旧版 DNA 可继续用 · 建议重新蒸馏升级 V2")
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SmallInfoPill(text: String) {
    Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
        }
        Column(Modifier.padding(start = 9.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReportTextSection(title: String, text: String, story: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (story) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DistillationItemCard(item: ReferenceDistillationReportItem, story: Boolean) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = if (story) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(dimensionLabel(item.dimension), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(item.value)
            if (item.evidence.isNotBlank()) Text("依据：${item.evidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
