package com.xiguli.langhuan.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.xiguli.langhuan.engine.StoryHealthCategory
import com.xiguli.langhuan.engine.StoryHealthIssue
import com.xiguli.langhuan.engine.StoryHealthSeverity
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

@Composable
internal fun StoryGraphHealthPillV10(
    state: StoryGraphHealthUiState,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val report = state.report
    val danger = (report?.highCount ?: 0) > 0
    Surface(
        shape = RoundedCornerShape(t.radiusSm),
        color = when {
            danger -> t.destructive.copy(alpha = .08f)
            report != null && report.score >= 90 -> t.warmSurface
            else -> t.card
        },
        contentColor = when {
            danger -> t.destructive
            report != null && report.score >= 90 -> t.accent
            else -> t.foreground
        },
        border = BorderStroke(
            1.dp,
            when {
                danger -> t.destructive.copy(alpha = .20f)
                report != null && report.score >= 90 -> t.accent.copy(alpha = .18f)
                else -> t.border
            },
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.7.dp, color = t.accent)
            } else {
                Icon(
                    if (danger) Icons.Rounded.WarningAmber else Icons.Rounded.Route,
                    null,
                    Modifier.size(14.dp),
                    tint = if (danger) t.destructive else if (report != null && report.score >= 90) t.accent else t.mutedForeground,
                )
            }
            Text(
                when {
                    state.isLoading -> "健康检查"
                    report != null -> "健康度 ${report.score}"
                    else -> "Story Graph"
                },
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
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
    val t = LocalLanghuanUiTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    color = t.warmSurface,
                    contentColor = t.accent,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .14f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Route, null, Modifier.size(20.dp), tint = t.accent)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Story Graph", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    Text(
                        "整本结构健康度 · 只读取已确认事实，不调用 AI、不修改项目",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                TextButton(onClick = onRefresh, enabled = !state.isLoading) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("重新检查")
                }
            }

            when {
                state.isLoading && state.report == null -> {
                    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 1.8.dp, color = t.accent)
                            Text("正在构建 Story Graph…", Modifier.padding(start = 10.dp), color = t.mutedForeground)
                        }
                    }
                }

                state.error != null && state.report == null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(t.radiusMd),
                        color = t.destructive.copy(alpha = .08f),
                        border = BorderStroke(1.dp, t.destructive.copy(alpha = .18f)),
                    ) {
                        Text(state.error, Modifier.padding(14.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                    }
                }

                state.report != null -> StoryGraphHealthContentV10(
                    report = state.report,
                    onOpenChapter = onOpenChapter,
                    onOpenMigration = onOpenMigration,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(t.radiusMd),
                colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
            ) {
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
    val t = LocalLanghuanUiTokens.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${report.score}", style = MaterialTheme.typography.displaySmall, color = t.foreground)
                        LanghuanBadge(report.statusLabel, accent = report.score >= 90 && report.highCount == 0)
                    }
                    Column(Modifier.padding(start = 17.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "${report.nodes.size} 节点 · ${report.edges.size} 条关系",
                            style = MaterialTheme.typography.titleSmall,
                            color = t.foreground,
                        )
                        Text(
                            "${report.highCount} 高风险 · ${report.mediumCount} 需注意 · ${report.affectedChapters} 章受影响",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                        Text(
                            "分数来自确定性结构检查，不是 AI 主观打分。",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.mutedForeground,
                        )
                    }
                }
            }
        }

        if (report.hotspots.isNotEmpty()) {
            item {
                Column {
                    Text("高关联热点", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text("连接越多，改动越容易波及后续事实。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.hotspots, key = { it.node.id }) { hotspot ->
                        LanghuanCard(
                            modifier = Modifier.size(width = 220.dp, height = 116.dp),
                            contentPadding = 12.dp,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    "${hotspot.node.type.label} · ${hotspot.node.label}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = t.foreground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                LanghuanBadge("${hotspot.degree} 条关系", accent = true)
                                Text(
                                    hotspot.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(top = 3.dp)) {
                Text("健康问题", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                Text(
                    if (report.issues.isEmpty()) "当前没有检测到结构化健康问题。" else "按风险排序，只展示能由现有正式数据证明的问题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }

        if (report.issues.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.warmSurface,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .16f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = t.accent)
                        Text(
                            "结构化 Story Graph 当前稳定",
                            Modifier.padding(start = 9.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = t.foreground,
                        )
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
    val t = LocalLanghuanUiTokens.current
    val danger = issue.severity == StoryHealthSeverity.HIGH
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusMd),
        color = if (danger) t.destructive.copy(alpha = .06f) else t.card,
        border = BorderStroke(1.dp, if (danger) t.destructive.copy(alpha = .18f) else t.border),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (danger) Icons.Rounded.WarningAmber else Icons.Rounded.Route,
                    null,
                    Modifier.size(17.dp),
                    tint = if (danger) t.destructive else t.mutedForeground,
                )
                Text(
                    "${issue.severity.label} · ${issue.category.label}",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (danger) t.destructive else t.mutedForeground,
                )
            }
            Text(issue.title, style = MaterialTheme.typography.titleSmall, color = t.foreground)
            Text(issue.detail, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (issue.category == StoryHealthCategory.WORKFLOW && issue.sourceNodeId?.startsWith("migration:") == true) {
                    TextButton(onClick = onOpenMigration) { Text("打开修复编排") }
                }
                issue.chapterNumber?.takeIf { it > 0 }?.let { chapter ->
                    OutlinedButton(
                        onClick = { onOpenChapter(chapter) },
                        shape = RoundedCornerShape(t.radiusSm),
                        border = BorderStroke(1.dp, t.border),
                    ) {
                        Text("打开第${chapter}章")
                    }
                }
            }
        }
    }
}
