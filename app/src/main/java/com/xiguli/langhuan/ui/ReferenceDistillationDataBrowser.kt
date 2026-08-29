package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xiguli.langhuan.engine.ReferenceDistillationReport
import com.xiguli.langhuan.engine.ReferenceDistillationReportItem
import com.xiguli.langhuan.engine.ReferenceDistillationReportStore

private data class DnaCategory(
    val key: String,
    val label: String,
    val dimensions: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
)

private val DnaCategories = listOf(
    DnaCategory("ALL", "全部 DNA"),
    DnaCategory("CHARACTER", "人物 / 关系", setOf("PROTAGONIST", "SUPPORTING", "CHARACTER", "CHARACTERIZATION", "RELATIONSHIP", "CHARACTER_STATE", "RELATION_STATE")),
    DnaCategory("POWER", "能力 / 规则", setOf("POWER", "PROGRESSION", "RULE", "WORLD_RULE", "RULE_PRESENTATION", "POWER_STATE")),
    DnaCategory("INSTANCE", "副本", setOf("INSTANCE", "INSTANCE_RULE", "INSTANCE_ENTRY", "INSTANCE_OBJECTIVE", "INSTANCE_NPC", "INSTANCE_MONSTER", "INSTANCE_CLUE", "INSTANCE_FAILURE", "INSTANCE_CLEAR", "INSTANCE_REWARD")),
    DnaCategory("PLOT", "剧情 / 事件", setOf("ARC", "CONFLICT", "EVENT", "TIMELINE", "STRUCTURE", "CHAPTER_TRACE")),
    DnaCategory("MYSTERY", "谜团 / 伏笔", setOf("MYSTERY", "CLUE", "REVEAL", "FORESHADOW", "PAYOFF")),
    DnaCategory("WORLD", "世界 / 势力 / 地点", setOf("WORLD", "FACTION", "LOCATION", "THEME")),
    DnaCategory("STYLE", "Style DNA", kinds = setOf("STYLE", "DNA")),
    DnaCategory("KEEP", "KEEP", kinds = setOf("KEEP")),
    DnaCategory("TRANSFORM", "TRANSFORM", kinds = setOf("TRANSFORM")),
    DnaCategory("AVOID", "AVOID", kinds = setOf("AVOID")),
)

@Composable
internal fun ReferenceDistillationDataBrowserDialog(
    report: ReferenceDistillationReport,
    store: ReferenceDistillationReportStore,
    onDismiss: () -> Unit,
) {
    var query by remember(report.taskId) { mutableStateOf("") }
    var categoryKey by remember(report.taskId) { mutableStateOf("ALL") }

    val allItems = remember(report) {
        (report.retrievalItems + report.items)
            .distinctBy { listOf(it.kind, it.dimension, it.value, it.evidence).joinToString("|") }
    }
    val active = DnaCategories.first { it.key == categoryKey }
    val filtered = remember(allItems, query, categoryKey) {
        val q = query.trim()
        allItems.filter { item ->
            val dimension = item.dimension.trim().uppercase()
            val kind = item.kind.trim().uppercase()
            val categoryMatch = when (active.key) {
                "ALL" -> true
                else -> (active.dimensions.isNotEmpty() && dimension in active.dimensions) ||
                    (active.kinds.isNotEmpty() && kind in active.kinds)
            }
            val queryMatch = q.isBlank() || listOf(item.kind, item.dimension, item.value, item.evidence)
                .any { it.contains(q, ignoreCase = true) }
            categoryMatch && queryMatch
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f),
            shape = RoundedCornerShape(30.dp),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Rounded.DataObject, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(report.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "蒸馏数据库 · 共 ${allItems.size} 条 · ${if (report.taskId.startsWith("builtin:")) "内置参考" else "我的蒸馏"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                }

                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("${store.coverageLabel(report)} · 全书 ${report.chapters} 章 · AI 深读 ${report.samples} 章", fontWeight = FontWeight.SemiBold)
                            Text(
                                "STORY ${store.kindCounts(report)["STORY"] ?: 0} · STYLE ${store.kindCounts(report)["STYLE"] ?: 0} · KEEP ${store.kindCounts(report)["KEEP"] ?: 0} · TRANSFORM ${store.kindCounts(report)["TRANSFORM"] ?: 0} · AVOID ${store.kindCounts(report)["AVOID"] ?: 0}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        label = { Text("搜索人物、副本、能力、规则、事件……") },
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(DnaCategories, key = { it.key }) { category ->
                            val selected = category.key == categoryKey
                            AssistChip(
                                onClick = { categoryKey = category.key },
                                label = { Text(category.label) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }

                    Text("命中 ${filtered.size} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text("当前分类/关键词没有命中数据。", modifier = Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(filtered, key = { itemKey(it) }) { item -> DnaBrowserItem(item) }
                    }
                    item { Spacer(Modifier.width(1.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DnaBrowserItem(item: ReferenceDistillationReportItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(item.kind.ifBlank { "DNA" }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(7.dp))
                Text(browserDimensionLabel(item.dimension), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.value, style = MaterialTheme.typography.bodyMedium)
            if (item.evidence.isNotBlank()) {
                Text("依据：${item.evidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun itemKey(item: ReferenceDistillationReportItem): String =
    listOf(item.kind, item.dimension, item.value, item.evidence).joinToString("|").hashCode().toString()

private fun browserDimensionLabel(value: String): String = when (value.trim().uppercase()) {
    "PROTAGONIST" -> "主角"
    "SUPPORTING" -> "重要配角"
    "RELATIONSHIP", "RELATION_STATE" -> "人物关系"
    "CHARACTER", "CHARACTER_STATE", "CHARACTERIZATION" -> "人物 / 状态"
    "POWER", "POWER_STATE" -> "能力"
    "PROGRESSION" -> "成长路线"
    "RULE", "WORLD_RULE" -> "规则"
    "INSTANCE" -> "副本"
    "INSTANCE_RULE" -> "副本规则"
    "INSTANCE_ENTRY" -> "进入条件"
    "INSTANCE_OBJECTIVE" -> "阶段目标"
    "INSTANCE_NPC" -> "副本 NPC"
    "INSTANCE_MONSTER" -> "怪物 / 威胁"
    "INSTANCE_CLUE" -> "副本线索"
    "INSTANCE_FAILURE" -> "失败 / 死亡条件"
    "INSTANCE_CLEAR" -> "通关 / 离开条件"
    "INSTANCE_REWARD" -> "奖励 / 代价"
    "ARC" -> "主线阶段"
    "EVENT" -> "关键事件"
    "TIMELINE" -> "时间线"
    "CHAPTER_TRACE" -> "章节索引"
    "MYSTERY" -> "谜团"
    "CLUE" -> "线索"
    "REVEAL" -> "揭示"
    "FORESHADOW" -> "伏笔"
    "PAYOFF" -> "伏笔回收"
    "WORLD" -> "世界观"
    "FACTION" -> "势力 / 组织"
    "LOCATION" -> "地点"
    "POV" -> "视角"
    "RHYTHM" -> "节奏"
    "DIALOGUE" -> "对白"
    "INFO" -> "信息释放"
    "SUSPENSE" -> "悬念"
    "SCENE" -> "场景组织"
    "EMOTION" -> "情绪"
    "STRUCTURE" -> "叙事结构"
    else -> value.ifBlank { "未分类" }
}
