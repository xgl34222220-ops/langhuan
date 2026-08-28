package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
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
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("蒸馏报告", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                }
                HorizontalDivider()

                when {
                    !loaded -> {
                        Column(Modifier.padding(24.dp)) { Text("正在读取 Style DNA 报告……") }
                    }
                    report == null -> {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("没有找到可读取的蒸馏结果", fontWeight = FontWeight.Bold)
                            Text(
                                "这个任务可能来自更早的版本，当时只记录了完成状态而没有保存可展示的报告。重新蒸馏一次后会永久保存完整 Style DNA。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> DistillationReportContent(
                        report = report!!,
                        fallbackProvider = fallbackProvider,
                        fallbackModel = fallbackModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun DistillationReportContent(
    report: ReferenceDistillationReport,
    fallbackProvider: String,
    fallbackModel: String,
) {
    val provider = report.provider.ifBlank { fallbackProvider }
    val model = report.model.ifBlank { fallbackModel }
    val sections = listOf(
        "DNA" to "Style DNA",
        "KEEP" to "可直接借鉴的高层机制",
        "TRANSFORM" to "需要原创化改造",
        "AVOID" to "不要照搬",
    ).mapNotNull { (kind, label) ->
        val items = report.items.filter { it.kind == kind }
        if (items.isEmpty()) null else Triple(kind, label, items)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text(" 已进入长期研究档案", fontWeight = FontWeight.Bold)
                    }
                    if (provider.isNotBlank() || model.isNotBlank()) {
                        Text("AI：${provider.ifBlank { "未知服务" }} · ${model.ifBlank { "未知模型" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.chapters > 0 || report.samples > 0) {
                        Text("分析 ${report.chapters} 章 · ${report.samples} 个代表样本", style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.legacySummaryOnly) {
                        Text(
                            "这是旧版本任务留下的档案摘要；升级后重新蒸馏可看到完整结构化维度和本地统计。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        if (report.summary.isNotBlank()) {
            item { ReportTextSection("Style DNA 摘要", report.summary) }
        }
        if (report.overview.isNotBlank()) {
            item { ReportTextSection("作品高层档案", report.overview) }
        }
        if (report.localMetrics.isNotBlank()) {
            item { ReportTextSection("本地结构统计", report.localMetrics) }
        }

        sections.forEach { (_, label, sectionItems) ->
            item { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(sectionItems) { item -> DistillationItemCard(item) }
        }

        if (!report.legacySummaryOnly) {
            item {
                FilledTonalButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("已自动加入长期研究档案，可在建书会谈中继续引用")
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun ReportTextSection(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DistillationItemCard(item: ReferenceDistillationReportItem) {
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(dimensionLabel(item.dimension), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(item.value)
            if (item.evidence.isNotBlank()) {
                Text("依据：${item.evidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    "CHARACTER" -> "人物塑造"
    "RULE" -> "规则 / 设定呈现"
    "SCENE" -> "场景切换"
    "EMOTION" -> "情绪温度"
    "STRUCTURE" -> "结构模式"
    else -> value.ifBlank { "高层特征" }
}
