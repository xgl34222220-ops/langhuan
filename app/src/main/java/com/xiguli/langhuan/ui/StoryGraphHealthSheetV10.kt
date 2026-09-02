package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.StoryGraphHealthReport
import com.xiguli.langhuan.engine.StoryGraphNodeType
import com.xiguli.langhuan.engine.StoryHealthCategory
import com.xiguli.langhuan.engine.StoryHealthIssue
import com.xiguli.langhuan.engine.StoryHealthSeverity
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
internal fun StoryGraphHealthPillV10(
    state: StoryGraphHealthUiState,
    onClick: () -> Unit,
) {
    val report = state.report
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = when {
            report == null -> MaterialTheme.colorScheme.surfaceContainerHigh
            report.highCount > 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .72f)
            report.score >= 90 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
            else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .72f)
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (report?.highCount ?: 0 > 0) Icons.Rounded.WarningAmber else Icons.Rounded.Route,
                    null,
                    Modifier.size(14.dp),
                    tint = if (report?.highCount ?: 0 > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                when {
                    state.isLoading -> "健康检查"
                    report != null -> "健康度 ${report.score}"
                    else -> "Story Graph"
                },
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryGraphHealthSheetV10(
    state: StoryGraphHealthUiState,
    onRefresh: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onOpenMigration: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp).squircleClip(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Story Graph · 整本健康度", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "只读取已确认结构化事实，不调用 AI、不扫描整本正文、不修改项目",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
                TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("重新检查") }
            }

            when {
                state.isLoading && state.report == null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().squircleClip(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在构建 Story Graph…", Modifier.padding(start = 10.dp))
                        }
                    }
                }
                state.error != null && state.report == null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().squircleClip(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(state.error, Modifier.padding(16.dp))
                    }
                }
                state.report != null -> StoryGraphHealthContentV10(
                    report = state.report,
                    onOpenChapter = onOpenChapter,
                    onOpenMigration = onOpenMigration,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp)) {
                Text("回到写作总控")
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun StoryGraphHealthContentV10(
    report: StoryGraphHealthReport,
    onOpenChapter: (Int) -> Unit,
    onOpenMigration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().squircleClip(22.dp),
                color = when {
                    report.highCount > 0 -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .58f)
                    report.score >= 90 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f)
                    else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .58f)
                },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${report.score}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                        Text(report.statusLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.padding(start = 18.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${report.nodes.size} 节点 · ${report.edges.size} 条关系", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${report.highCount} 高风险 · ${report.mediumCount} 需注意 · ${report.affectedChapters} 章受影响",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMiuixTokens.current.textSecondary,
                        )
                        Text(
                            "分数来自确定性结构检查，不是 AI 主观打分。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalMiuixTokens.current.textSecondary,
                        )
                    }
                }
            }
        }

        if (report.hotspots.isNotEmpty()) {
            item {
                Text("高关联热点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "连接越多，改动越容易波及后续事实。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMiuixTokens.current.textSecondary,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.hotspots, key = { it.node.id }) { hotspot ->
                        Surface(
                            modifier = Modifier.size(width = 220.dp, height = 116.dp).squircleClip(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    "${hotspot.node.type.label} · ${hotspot.node.label}",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("${hotspot.degree} 条关系", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    hotspot.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = LocalMiuixTokens.current.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text("健康问题", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (report.issues.isEmpty()) "当前没有检测到结构化健康问题。" else "按风险排序，只展示能由现有正式数据证明的问题。",
                style = MaterialTheme.typography.bodySmall,
                color = LocalMiuixTokens.current.textSecondary,
            )
        }

        if (report.issues.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(19.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("结构化 Story Graph 当前稳定", Modifier.padding(start = 9.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            items(report.issues, key = { it.id }) { issue ->
                StoryHealthIssueCardV10(issue, onOpenChapter, onOpenMigration)
            }
        }
    }
}

@Composable
private fun StoryHealthIssueCardV10(
    issue: StoryHealthIssue,
    onOpenChapter: (Int) -> Unit,
    onOpenMigration: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(19.dp),
        color = if (issue.severity == StoryHealthSeverity.HIGH) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = .48f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (issue.severity == StoryHealthSeverity.HIGH) Icons.Rounded.WarningAmber else Icons.Rounded.Route,
                    null,
                    Modifier.size(17.dp),
                    tint = if (issue.severity == StoryHealthSeverity.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${issue.severity.label} · ${issue.category.label}",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMiuixTokens.current.textSecondary,
                )
            }
            Text(issue.title, fontWeight = FontWeight.SemiBold)
            Text(issue.detail, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (issue.category == StoryHealthCategory.WORKFLOW && issue.sourceNodeId?.startsWith("migration:") == true) {
                    TextButton(onClick = onOpenMigration) { Text("打开修复编排") }
                }
                issue.chapterNumber?.takeIf { it > 0 }?.let { chapter ->
                    OutlinedButton(onClick = { onOpenChapter(chapter) }, shape = RoundedCornerShape(14.dp)) {
                        Text("打开第${chapter}章")
                    }
                }
            }
        }
    }
}
