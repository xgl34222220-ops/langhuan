package com.xiguli.langhuan.ui.reference

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Icon
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
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape

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
    DnaCategory("INSTANCE", "副本 / 任务", setOf("INSTANCE", "INSTANCE_RULE", "INSTANCE_ENTRY", "INSTANCE_OBJECTIVE", "INSTANCE_NPC", "INSTANCE_MONSTER", "INSTANCE_CLUE", "INSTANCE_FAILURE", "INSTANCE_CLEAR", "INSTANCE_REWARD", "INSTANCE_MAINLINE")),
    DnaCategory("PLOT", "剧情 / 事件", setOf("ARC", "CONFLICT", "EVENT", "TIMELINE", "STRUCTURE", "CHAPTER_TRACE")),
    DnaCategory("MYSTERY", "谜团 / 伏笔", setOf("MYSTERY", "CLUE", "REVEAL", "FORESHADOW", "PAYOFF")),
    DnaCategory("WORLD", "世界 / 势力 / 地点", setOf("WORLD", "FACTION", "LOCATION", "THEME", "ORGANIZATION", "SETTING")),
    DnaCategory("STYLE", "Style DNA", kinds = setOf("STYLE", "DNA")),
    DnaCategory("KEEP", "KEEP", kinds = setOf("KEEP")),
    DnaCategory("TRANSFORM", "TRANSFORM", kinds = setOf("TRANSFORM")),
    DnaCategory("AVOID", "AVOID", kinds = setOf("AVOID")),
)

/**
 * Full Reference DNA inspector.
 *
 * This is intentionally a data browser, not a one-line summary. It exposes the retained
 * character/relationship, rule, event, mystery, world and style items so users can verify what
 * distillation actually produced before binding a reference to a new project.
 */
@Composable
internal fun ReferenceDistillationDataBrowserDialog(
    report: ReferenceDistillationReport,
    store: ReferenceDistillationReportStore,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var query by remember(report.taskId) { mutableStateOf("") }
    var categoryKey by remember(report.taskId) { mutableStateOf("ALL") }

    val allItems = remember(report) {
        (report.retrievalItems + report.items)
            .distinctBy { listOf(it.kind, it.dimension, it.value, it.evidence).joinToString("|") }
    }
    val active = DnaCategories.first { it.key == categoryKey }
    val categoryCounts = remember(allItems) {
        DnaCategories.drop(1).associate { category -> category.key to allItems.count { matchesCategory(it, category) } }
    }
    val kindCounts = remember(report) { store.kindCounts(report) }
    val filtered = remember(allItems, query, categoryKey) {
        val q = query.trim()
        allItems.filter { item ->
            val categoryMatch = active.key == "ALL" || matchesCategory(item, active)
            val queryMatch = q.isBlank() || listOf(item.kind, item.dimension, item.value, item.evidence)
                .any { it.contains(q, ignoreCase = true) }
            categoryMatch && queryMatch
        }
    }
    val builtin = report.taskId.startsWith("builtin:")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f),
            shape = LanghuanShape.sheet,
            color = t.background,
            contentColor = t.foreground,
            border = BorderStroke(1.dp, t.border),
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 9.dp),
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
                            Icon(Icons.Rounded.DataObject, null, Modifier.size(21.dp), tint = t.accent)
                        }
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text(
                            report.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = t.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "完整蒸馏数据库 · ${allItems.size} 条可浏览数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                    LanghuanBadge(if (builtin) "内置" else "我的蒸馏", accent = builtin)
                    Spacer(Modifier.width(7.dp))
                    LanghuanIconButton(Icons.Rounded.Close, "关闭", onDismiss)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        DnaCoverageCard(
                            report = report,
                            store = store,
                            builtin = builtin,
                            categoryCounts = categoryCounts,
                            kindCounts = kindCounts,
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = LanghuanShape.card,
                            leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(19.dp)) },
                            placeholder = { Text("搜索人物、关系、能力、规则、事件、地点……") },
                        )
                    }

                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(DnaCategories, key = { it.key }) { category ->
                                val selected = category.key == categoryKey
                                val count = if (category.key == "ALL") allItems.size else categoryCounts[category.key] ?: 0
                                DnaCategoryChip(
                                    label = category.label,
                                    count = count,
                                    selected = selected,
                                    onClick = { categoryKey = category.key },
                                )
                            }
                        }
                    }

                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                active.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = t.foreground,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                " · 命中 ${filtered.size} 条",
                                style = MaterialTheme.typography.labelMedium,
                                color = t.mutedForeground,
                            )
                            Spacer(Modifier.weight(1f))
                            if (query.isNotBlank()) LanghuanBadge("搜索中", accent = true)
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Rounded.Search, null, Modifier.size(24.dp), tint = t.mutedForeground)
                                    Text(
                                        "当前分类或关键词没有命中数据",
                                        Modifier.padding(top = 9.dp),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = t.foreground,
                                    )
                                    Text(
                                        "换一个分类，或搜索角色名、能力、规则、地点和事件关键词。",
                                        Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = t.mutedForeground,
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { itemKey(it) }) { item -> DnaBrowserItem(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DnaCoverageCard(
    report: ReferenceDistillationReport,
    store: ReferenceDistillationReportStore,
    builtin: Boolean,
    categoryCounts: Map<String, Int>,
    kindCounts: Map<String, Int>,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (builtin) {
                    "${store.coverageLabel(report)} · 全书 ${report.chapters} 章 · 结构索引 ${report.samples} 章"
                } else {
                    "${store.coverageLabel(report)} · 全书 ${report.chapters} 章 · AI 深读 ${report.samples} 章"
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DnaMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Group,
                    label = "人物 / 关系",
                    value = categoryCounts["CHARACTER"] ?: 0,
                )
                DnaMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Hub,
                    label = "规则 / 能力",
                    value = categoryCounts["POWER"] ?: 0,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DnaMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.MenuBook,
                    label = "剧情 / 事件",
                    value = categoryCounts["PLOT"] ?: 0,
                )
                DnaMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Style,
                    label = "Style DNA",
                    value = categoryCounts["STYLE"] ?: 0,
                )
            }

            Text(
                "副本/任务 ${categoryCounts["INSTANCE"] ?: 0} · 谜团/伏笔 ${categoryCounts["MYSTERY"] ?: 0} · 世界/势力/地点 ${categoryCounts["WORLD"] ?: 0}",
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
            )
            Text(
                "KEEP ${kindCounts["KEEP"] ?: 0} · TRANSFORM ${kindCounts["TRANSFORM"] ?: 0} · AVOID ${kindCounts["AVOID"] ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun DnaMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = LanghuanShape.chip,
        color = t.muted,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp), tint = t.mutedForeground)
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground, maxLines = 1)
                Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = t.foreground)
            }
        }
    }
}

@Composable
private fun DnaCategoryChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        shape = LanghuanShape.chip,
        color = if (selected) t.foreground else t.card,
        contentColor = if (selected) t.primaryForeground else t.foreground,
        border = BorderStroke(1.dp, if (selected) t.foreground else t.border),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) t.primaryForeground else t.foreground,
            )
            Text(
                count.toString(),
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) t.primaryForeground.copy(alpha = .72f) else t.mutedForeground,
            )
        }
    }
}

private fun matchesCategory(item: ReferenceDistillationReportItem, category: DnaCategory): Boolean {
    val dimension = item.dimension.trim().uppercase()
    val kind = item.kind.trim().uppercase()
    return (category.dimensions.isNotEmpty() && dimension in category.dimensions) ||
        (category.kinds.isNotEmpty() && kind in category.kinds)
}

@Composable
private fun DnaBrowserItem(item: ReferenceDistillationReportItem) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 13.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LanghuanBadge(item.kind.ifBlank { "DNA" }, accent = item.kind.equals("STYLE", true))
                Text(
                    browserDimensionLabel(item.dimension),
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = t.mutedForeground,
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

private fun itemKey(item: ReferenceDistillationReportItem): String =
    listOf(item.kind, item.dimension, item.value, item.evidence).joinToString("|").hashCode().toString()

private fun browserDimensionLabel(value: String): String = when (value.trim().uppercase()) {
    "PROTAGONIST" -> "主角"
    "SUPPORTING" -> "重要配角"
    "RELATIONSHIP", "RELATION_STATE" -> "人物关系 / 共现"
    "CHARACTER", "CHARACTER_STATE", "CHARACTERIZATION" -> "人物 / 状态"
    "POWER", "POWER_STATE" -> "能力"
    "PROGRESSION" -> "成长路线"
    "RULE", "WORLD_RULE", "RULE_PRESENTATION" -> "规则"
    "INSTANCE" -> "副本 / 任务"
    "INSTANCE_RULE" -> "副本规则"
    "INSTANCE_ENTRY" -> "进入条件"
    "INSTANCE_OBJECTIVE" -> "阶段目标"
    "INSTANCE_NPC" -> "参与人物 / NPC"
    "INSTANCE_MONSTER" -> "怪物 / 威胁"
    "INSTANCE_CLUE" -> "副本线索"
    "INSTANCE_FAILURE" -> "失败 / 死亡条件"
    "INSTANCE_CLEAR" -> "通关 / 离开条件"
    "INSTANCE_REWARD" -> "奖励 / 代价"
    "INSTANCE_MAINLINE" -> "与主线关系"
    "ARC" -> "主线阶段"
    "CONFLICT" -> "冲突"
    "EVENT" -> "关键 / 异常事件段"
    "TIMELINE" -> "时间 / 空间线索"
    "CHAPTER_TRACE" -> "章节索引"
    "MYSTERY" -> "谜团 / 线索段"
    "CLUE" -> "线索"
    "REVEAL" -> "揭示"
    "FORESHADOW" -> "伏笔"
    "PAYOFF" -> "伏笔回收"
    "WORLD" -> "世界观 / 组织段"
    "ORGANIZATION" -> "组织"
    "SETTING" -> "舞台 / 地点"
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
