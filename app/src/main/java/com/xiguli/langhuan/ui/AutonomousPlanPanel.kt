package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.DriftSeverity
import com.xiguli.langhuan.domain.ForeshadowPlanAction
import com.xiguli.langhuan.ui.theme.LocalLanghuanTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
internal fun AutonomousPlanPanel(state: StudioUiState, vm: StudioViewModel) {
    val snapshot = state.snapshot
    val plan = snapshot.longForm.autonomousPlan
    val current = state.draft.chapterNumber
    val future = plan.chapters.filter { it.chapterNumber > current }.take(10)
    val highRisk = plan.driftSignals.count { it.severity == DriftSeverity.HIGH }
    val watchRisk = plan.driftSignals.count { it.severity == DriftSeverity.WATCH }
    val activeDebts = snapshot.longForm.narrativeDebts.filter { it.status.name != "RESOLVED" }
    val overdueDebts = activeDebts.count { it.status.name == "OVERDUE" }
    val lastExecution = snapshot.longForm.executionHistory.lastOrNull()
    val shape = RoundedCornerShape(26.dp)

    Column(
        Modifier.fillMaxWidth()
            .shadow(2.dp, shape)
            .squircleClip(26.dp)
            .background(LocalLanghuanTokens.current.cardBackground.copy(alpha = .94f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Route, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("长篇自治规划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (future.isEmpty()) "还没有未来滚动计划" else "未来 ${future.size} 章 · 第 ${plan.generation} 代计划",
                    color = LocalLanghuanTokens.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (highRisk > 0 || watchRisk > 0) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = if (highRisk > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (highRisk > 0) "$highRisk 高风险" else "$watchRisk 提醒", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            "它只维护未来提案，不会直接修改锁定总纲、卷纲、圣经或信息边界。已有锁定章纲永远优先。",
            style = MaterialTheme.typography.bodySmall,
            color = LocalLanghuanTokens.current.textSecondary,
        )

        lastExecution?.let { execution ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (execution.status.name == "DEVIATED") MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("最近计划执行 · 第${execution.chapterNumber}章 · ${execution.completionScore}分", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text("状态：${execution.status.name}｜实际：${execution.actualSummary}", style = MaterialTheme.typography.bodySmall)
                    if (execution.affectedFutureChapters.isNotEmpty()) Text("受影响后续：${execution.affectedFutureChapters.joinToString("、") { "第${it}章" }}", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
                    if (execution.repairHint.isNotBlank()) Text("最小修复：${execution.repairHint}", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
                }
            }
        }

        if (activeDebts.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (overdueDebts > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = .42f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .34f),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("剧情债务 · ${activeDebts.size} 项${if (overdueDebts > 0) " · $overdueDebts 项逾期" else ""}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    activeDebts.sortedByDescending { it.priority }.take(6).forEach { debt ->
                        Text("• [${debt.status.name}/${debt.kind.name}] ${debt.title}｜截止 ${debt.dueStartChapter}-${debt.dueEndChapter}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Button(
            onClick = { vm.refreshAutonomousPlan(6) },
            enabled = state.provider.ready && !state.isAutonomousPlanning && !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
        ) {
            if (state.isAutonomousPlanning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.AutoAwesome, null)
            }
            Spacer(Modifier.width(7.dp))
            Text(if (state.isAutonomousPlanning) "正在重算未来 6 章…" else if (future.isEmpty()) "生成未来 6 章自治计划" else "刷新未来 6 章自治计划")
        }

        if (future.isNotEmpty()) {
            future.forEach { beat ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("第${beat.chapterNumber}章", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Text(beat.title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            if (beat.fixedByOutline) {
                                Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text("锁定章纲", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text("目标：${beat.objective}", style = MaterialTheme.typography.bodySmall)
                        Text("冲突：${beat.conflict}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                        Text("转折：${beat.turningPoint}", style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
                        if (beat.characterFocus.isNotEmpty()) Text("人物：${beat.characterFocus.joinToString("、")}", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
                        if (beat.foreshadowingTargets.isNotEmpty()) Text("伏笔：${beat.foreshadowingTargets.joinToString("、")}", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
                        if (beat.guardrail.isNotBlank()) Text("护栏：${beat.guardrail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        val budget = beat.revealBudget
                        Text(
                            "揭露预算：完整≤${budget.maxFullReveals} · 部分/暗示≤${budget.maxPartialReveals}${if (budget.forbiddenBoundaryIds.isEmpty()) "" else " · ${budget.forbiddenBoundaryIds.size}条禁止揭底"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLanghuanTokens.current.textSecondary,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = vm::planNextChapter,
                enabled = state.provider.ready && !state.isPlanning && !state.isAutonomousPlanning && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text("按自治计划细化下一章场景")
            }
        }

        if (plan.characterTargets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("人物弧窗口目标", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                plan.characterTargets.take(6).forEach { target ->
                    Text(
                        "• ${target.name} → 第${target.targetChapter}章：${target.desiredChange}${if (target.pressure.isBlank()) "" else "｜压力=${target.pressure}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLanghuanTokens.current.textSecondary,
                    )
                }
            }
        }

        if (plan.foreshadowCadence.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("伏笔释放节奏", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                plan.foreshadowCadence.take(8).forEach { cadence ->
                    Text(
                        "• 第${cadence.targetChapter}章 ${cadence.title}：${cadence.action.zhLabel()}${if (cadence.reason.isBlank()) "" else "｜${cadence.reason}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLanghuanTokens.current.textSecondary,
                    )
                }
            }
        }

        if (plan.driftSignals.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (highRisk > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = .52f) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .42f),
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("偏航监测", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    plan.driftSignals.take(6).forEach { signal ->
                        val prefix = when (signal.severity) {
                            DriftSeverity.HIGH -> "高风险"
                            DriftSeverity.WATCH -> "关注"
                            DriftSeverity.INFO -> "提示"
                        }
                        Text("• [$prefix] ${signal.message}", style = MaterialTheme.typography.bodySmall)
                        if (signal.repair.isNotBlank()) Text("  修复：${signal.repair}", style = MaterialTheme.typography.labelSmall, color = LocalLanghuanTokens.current.textSecondary)
                    }
                }
            }
        }

        if (plan.correctionStrategy.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("最小纠偏策略", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(plan.correctionStrategy, style = MaterialTheme.typography.bodySmall, color = LocalLanghuanTokens.current.textSecondary)
            }
        }

        if (future.isEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "生成后会持续保留未来 3–10 章因果链；计划不足 3 章、跨过 2 个章节或出现高风险偏航时，正式保存章节后会自动尝试刷新。",
                style = MaterialTheme.typography.labelSmall,
                color = LocalLanghuanTokens.current.textSecondary,
            )
        }
    }
}

private fun ForeshadowPlanAction.zhLabel(): String = when (this) {
    ForeshadowPlanAction.HOLD -> "保持"
    ForeshadowPlanAction.TOUCH -> "轻触"
    ForeshadowPlanAction.ESCALATE -> "升级"
    ForeshadowPlanAction.PAYOFF -> "回收"
}
