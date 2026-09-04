package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.BookEditorIssue
import com.xiguli.langhuan.domain.BookEditorSeverity
import com.xiguli.langhuan.domain.LongFormHealthLevel
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.theme.LocalLanghuanTokens

@Composable
internal fun FullBookEditorPanel(snapshot: StorySnapshot) {
    val report = snapshot.longForm.editorReport
    AgentCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("全书主编", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (report.lastAuditChapter > 0) "最近巡检到第${report.lastAuditChapter}章 · 扫描${report.scannedChapterCount}章" else "尚未形成全书主编报告",
                    color = LocalLanghuanTokens.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (report.lastAuditChapter > 0) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("${report.score}分") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (report.lastAuditChapter <= 0) {
            Text(
                "保存章节后会按长篇巡检周期自动做本地扫描；上面的“全书一致性巡检”会再调用当前模型做语义级深度检查。两者都只给诊断，不自动改正文或 Canon。",
                color = LocalLanghuanTokens.current.textSecondary,
            )
            return@AgentCardSurface
        }

        val levelText = when (report.level) {
            LongFormHealthLevel.HEALTHY -> "健康"
            LongFormHealthLevel.WATCH -> "需关注"
            LongFormHealthLevel.RISK -> "高风险"
        }
        Text("全书状态：$levelText", fontWeight = FontWeight.Bold, color = if (report.level == LongFormHealthLevel.RISK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        ScoreRow("结构", report.structureScore, "变化")
        ScoreRow("套路多样性", report.varietyScore, "人物声线 ${report.characterVoiceScore}")
        ScoreRow("悬念节奏", report.suspenseScore, "支线 ${report.subplotScore}")
        ScoreRow("文风稳定", report.styleScore, "总分 ${report.score}")

        if (report.issues.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("当前最值得处理", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            report.issues.take(6).forEach { issue -> EditorIssueRow(issue) }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("当前没有达到提醒阈值的全书级模式问题。", color = LocalLanghuanTokens.current.textSecondary)
        }

        if (report.aiSummary.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("AI 深度巡检补充", fontWeight = FontWeight.Bold)
            Text(report.aiSummary.take(700), style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
        }
    }
}

@Composable
private fun ScoreRow(left: String, leftScore: Int, right: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
        Text(leftScore.toString(), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        Text(right, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
    }
}

@Composable
private fun EditorIssueRow(issue: BookEditorIssue) {
    val high = issue.severity == BookEditorSeverity.HIGH
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (high) MaterialTheme.colorScheme.errorContainer.copy(alpha = .32f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, modifier = Modifier.size(16.dp), tint = if (high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("${issue.severity} · ${issue.title}", fontWeight = FontWeight.SemiBold)
            }
            if (issue.chapterStart > 0) {
                Text("影响：第${issue.chapterStart}-${issue.chapterEnd.coerceAtLeast(issue.chapterStart)}章 · ${issue.source}", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
            }
            if (issue.evidence.isNotBlank()) Text(issue.evidence, style = MaterialTheme.typography.bodySmall)
            if (issue.minimalRepair.isNotBlank()) Text("最小修复：${issue.minimalRepair}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AgentCardSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}