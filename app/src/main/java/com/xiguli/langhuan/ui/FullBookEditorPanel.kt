package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.BookEditorIssue
import com.xiguli.langhuan.domain.BookEditorSeverity
import com.xiguli.langhuan.domain.LongFormHealthLevel
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

@Composable
internal fun FullBookEditorPanel(snapshot: StorySnapshot) {
    val t = LocalLanghuanUiTokens.current
    val report = snapshot.longForm.editorReport

    LanghuanCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 18.dp,
        depth = if (report.lastAuditChapter > 0) 2 else 1,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.warmSurface,
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoStories, null, tint = t.primary, modifier = Modifier.size(23.dp))
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("全书主编", style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        if (report.lastAuditChapter > 0) "最近巡检到第${report.lastAuditChapter}章 · 扫描${report.scannedChapterCount}章" else "尚未形成全书主编报告",
                        color = t.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (report.lastAuditChapter > 0) {
                    Surface(shape = RoundedCornerShape(99.dp), color = t.muted) {
                        Text(
                            "${report.score}分",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = t.foreground,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (report.lastAuditChapter <= 0) {
                Text(
                    "保存章节后会按长篇巡检周期自动做本地扫描；“全书一致性巡检”会再调用当前模型做语义级深度检查。两者都只给诊断，不自动改正文或 Canon。",
                    color = t.mutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val levelText = when (report.level) {
                    LongFormHealthLevel.HEALTHY -> "健康"
                    LongFormHealthLevel.WATCH -> "需关注"
                    LongFormHealthLevel.RISK -> "高风险"
                }
                Text(
                    "全书状态：$levelText",
                    fontWeight = FontWeight.Bold,
                    color = if (report.level == LongFormHealthLevel.RISK) MaterialTheme.colorScheme.error else t.primary,
                )

                ScoreRow("结构", report.structureScore, "变化")
                ScoreRow("套路多样性", report.varietyScore, "人物声线 ${report.characterVoiceScore}")
                ScoreRow("悬念节奏", report.suspenseScore, "支线 ${report.subplotScore}")
                ScoreRow("文风稳定", report.styleScore, "总分 ${report.score}")

                if (report.issues.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("当前最值得处理", color = t.foreground, fontWeight = FontWeight.Bold)
                    report.issues.take(6).forEach { issue -> EditorIssueRow(issue) }
                } else {
                    Text("当前没有达到提醒阈值的全书级模式问题。", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                }

                if (report.aiSummary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text("AI 深度巡检补充", color = t.foreground, fontWeight = FontWeight.Bold)
                    Text(report.aiSummary.take(700), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(left: String, leftScore: Int, right: String) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        Text(leftScore.toString(), color = t.foreground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        Text(right, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
}

@Composable
private fun EditorIssueRow(issue: BookEditorIssue) {
    val t = LocalLanghuanUiTokens.current
    val high = issue.severity == BookEditorSeverity.HIGH
    Surface(
        shape = RoundedCornerShape(t.radiusMd),
        color = if (high) MaterialTheme.colorScheme.errorContainer.copy(alpha = .36f) else t.muted.copy(alpha = .7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = if (high) MaterialTheme.colorScheme.error else t.warning,
                )
                Spacer(Modifier.width(6.dp))
                Text("${issue.severity} · ${issue.title}", color = t.foreground, fontWeight = FontWeight.SemiBold)
            }
            if (issue.chapterStart > 0) {
                Text(
                    "影响：第${issue.chapterStart}-${issue.chapterEnd.coerceAtLeast(issue.chapterStart)}章 · ${issue.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                )
            }
            if (issue.evidence.isNotBlank()) Text(issue.evidence, style = MaterialTheme.typography.bodySmall, color = t.foreground)
            if (issue.minimalRepair.isNotBlank()) Text("最小修复：${issue.minimalRepair}", style = MaterialTheme.typography.bodySmall, color = t.primary)
        }
    }
}
