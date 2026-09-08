package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.ForeshadowStatus
import com.xiguli.langhuan.domain.LongFormHealthLevel
import com.xiguli.langhuan.domain.PlotArcPhase
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

@Composable
internal fun LongFormAgentPanel(snapshot: StorySnapshot) {
    val t = LocalLanghuanUiTokens.current
    val state = snapshot.longForm
    val chapter = snapshot.novel.currentChapter.coerceAtLeast(1)
    val currentArc = state.arcs
        .filter { it.phase != PlotArcPhase.RESOLVED }
        .lastOrNull { chapter in it.startChapter..(it.plannedEndChapter + state.config.arcSpan) }
        ?: state.arcs.lastOrNull()
    val activeForeshadows = snapshot.relevantForeshadowing.filter {
        it.status !in setOf(ForeshadowStatus.RESOLVED, ForeshadowStatus.ABANDONED)
    }
    val overdue = activeForeshadows.count {
        it.expectedChapterEnd > 0 && chapter > it.expectedChapterEnd
    }
    val due = activeForeshadows.count {
        it.expectedChapterStart > 0 && chapter >= it.expectedChapterStart &&
            (it.expectedChapterEnd <= 0 || chapter <= it.expectedChapterEnd)
    }
    val recentGrowth = state.characterGrowth.sortedByDescending { it.lastTurningChapter }.take(3)
    val health = state.health
    val healthLabel = when (health.level) {
        LongFormHealthLevel.HEALTHY -> "健康"
        LongFormHealthLevel.WATCH -> "需关注"
        LongFormHealthLevel.RISK -> "高风险"
    }
    val healthIcon = if (health.level == LongFormHealthLevel.HEALTHY) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber

    LanghuanCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 18.dp,
        depth = if (health.level == LongFormHealthLevel.RISK) 2 else 1,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.warmSurface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoStories, null, tint = t.primary, modifier = Modifier.size(23.dp))
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("超长篇导航", style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.config.enabled) "百万 / 两百万字连续性引擎已启用" else "超长篇连续性引擎已关闭",
                        color = t.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = when (health.level) {
                        LongFormHealthLevel.RISK -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .66f)
                        LongFormHealthLevel.WATCH -> t.warmSurface
                        LongFormHealthLevel.HEALTHY -> t.muted
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            healthIcon,
                            null,
                            modifier = Modifier.width(16.dp),
                            tint = when (health.level) {
                                LongFormHealthLevel.RISK -> MaterialTheme.colorScheme.error
                                LongFormHealthLevel.WATCH -> t.warning
                                LongFormHealthLevel.HEALTHY -> t.success
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${health.score} · $healthLabel", color = t.foreground, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            currentArc?.let { arc ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.muted.copy(alpha = .62f),
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "当前剧情弧 · 第${arc.startChapter}-${arc.plannedEndChapter}章",
                            style = MaterialTheme.typography.labelLarge,
                            color = t.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(arc.title, style = MaterialTheme.typography.titleSmall, color = t.foreground, fontWeight = FontWeight.Bold)
                        Text(
                            "阶段：${arc.phase.zhLabel()} · 已推进到第${arc.lastUpdatedChapter.coerceAtLeast(chapter)}章",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.foreground,
                        )
                        if (arc.objective.isNotBlank()) Text("目标：${arc.objective}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        if (arc.expectedPayoff.isNotBlank()) Text("预计收束：${arc.expectedPayoff}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            } ?: Text(
                "写完并正式提交第一章后，会自动建立 20–40 章滚动剧情弧。",
                color = t.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LongFormMetric("热记忆", "${state.config.hotChapterWindow}章", Modifier.weight(1f))
                LongFormMetric("中期窗口", "${state.mediumMemories.size}", Modifier.weight(1f))
                LongFormMetric("角色成长", "${state.characterGrowth.size}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LongFormMetric("待回收伏笔", due.toString(), Modifier.weight(1f))
                LongFormMetric("逾期伏笔", overdue.toString(), Modifier.weight(1f), danger = overdue > 0)
                LongFormMetric("开放剧情弧", health.openArcCount.toString(), Modifier.weight(1f))
            }

            if (recentGrowth.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("角色成长最近转折", style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.Bold)
                    recentGrowth.forEach { growth ->
                        Text(
                            "${growth.name} · ${growth.stage} · 最近转折第${growth.lastTurningChapter.coerceAtLeast(0)}章",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }
            }

            if (health.warnings.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = if (health.level == LongFormHealthLevel.RISK) MaterialTheme.colorScheme.errorContainer.copy(alpha = .52f) else t.warmSurface,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("长篇体检提醒", color = t.foreground, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        health.warnings.take(4).forEach { warning ->
                            Text("• $warning", color = t.foreground, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text(
                "系统只保存剧情弧、角色成长、中期摘要和结构化事实；旧正文按需由 RAG 召回，不会把数百万字全文反复塞给模型。",
                color = t.mutedForeground,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun LongFormMetric(label: String, value: String, modifier: Modifier, danger: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(t.radiusMd),
        color = if (danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = .52f) else t.muted.copy(alpha = .78f),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(value, fontWeight = FontWeight.Black, color = if (danger) MaterialTheme.colorScheme.error else t.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
        }
    }
}

private fun PlotArcPhase.zhLabel(): String = when (this) {
    PlotArcPhase.SETUP -> "提出"
    PlotArcPhase.ESCALATION -> "升级"
    PlotArcPhase.TURN -> "转折"
    PlotArcPhase.CLIMAX -> "高潮"
    PlotArcPhase.PAYOFF -> "收束"
    PlotArcPhase.OVERDUE -> "逾期未收束"
    PlotArcPhase.RESOLVED -> "已完成"
}
