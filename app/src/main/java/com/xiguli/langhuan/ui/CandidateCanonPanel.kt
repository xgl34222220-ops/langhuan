package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.CandidateFact
import com.xiguli.langhuan.domain.CandidateFactKind
import com.xiguli.langhuan.domain.CandidateFactRisk
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape

@Composable
internal fun CandidateCanonPanel(state: StudioUiState, vm: StudioViewModel) {
    val t = LocalLanghuanUiTokens.current
    val pending = state.snapshot.candidateFacts
        .filter { it.status == CandidateFactStatus.PENDING }
        .sortedWith(compareByDescending<CandidateFact> { it.risk.weight() }.thenByDescending { it.createdAt })
    val confirmed = state.snapshot.candidateFacts.count { it.status == CandidateFactStatus.CONFIRMED }
    val rejected = state.snapshot.candidateFacts.count { it.status == CandidateFactStatus.REJECTED }

    CandidatePanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = LanghuanShape.card,
                color = t.warmSurface,
                border = BorderStroke(1.dp, t.accent.copy(alpha = .18f)),
            ) {
                Icon(Icons.Rounded.FactCheck, null, Modifier.padding(9.dp), tint = t.accent)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Candidate → Canon Gate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
                Text(
                    "AI 提案先进入候选区；只有确认后才成为正式事实。PENDING 不进入正文上下文、RAG 或长期记忆。",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CandidateCountBadgeV1("待确认 ${pending.size}", if (pending.isEmpty()) t.success else t.warning)
            CandidateCountBadgeV1("已确认 $confirmed", t.success)
            CandidateCountBadgeV1("已拒绝 $rejected", t.mutedForeground)
        }
        if (pending.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.chip,
                color = t.success.copy(alpha = .07f),
            ) {
                Text(
                    "当前没有待确认事实。只有低风险且能从已保存正文直接证明的状态变化才允许自动确认。",
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.foreground,
                )
            }
        }
    }

    pending.take(20).forEach { fact ->
        val riskColor = fact.risk.color()
        CandidatePanelCard(borderColor = riskColor.copy(alpha = .20f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (fact.risk == CandidateFactRisk.HIGH) Icons.Rounded.WarningAmber else Icons.Rounded.Shield,
                    null,
                    tint = riskColor,
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(fact.kind.label(), fontWeight = FontWeight.SemiBold, color = t.foreground)
                    Text(fact.subject, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                }
                CandidateRiskBadgeV1(fact.risk)
            }

            if (fact.before.isNotBlank()) {
                CandidateFactValueV1("原状态", fact.before, accent = false)
            }
            CandidateFactValueV1("候选事实", fact.after, accent = true)

            if (fact.evidence.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LanghuanShape.chip,
                    color = t.muted,
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("正文依据", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        Text(fact.evidence, style = MaterialTheme.typography.bodySmall, color = t.foreground)
                    }
                }
            }
            fact.validationNotes.take(3).forEach { note ->
                Text("· $note", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.chip,
                color = riskColor.copy(alpha = .06f),
                border = BorderStroke(1.dp, riskColor.copy(alpha = .14f)),
            ) {
                Text(
                    when (fact.risk) {
                        CandidateFactRisk.LOW -> "低风险候选：仍可人工拒绝；确认后才写入 Canon。"
                        CandidateFactRisk.MEDIUM -> "需要你明确确认：不会因为 AI 判断看起来合理就自动进入 Canon。"
                        CandidateFactRisk.HIGH -> "高风险事实：会影响关键人物、关系、时间线或规则，必须人工确认。"
                    },
                    Modifier.padding(9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.foreground,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.rejectCandidateFact(fact.id) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = LanghuanShape.card,
                ) { Text("拒绝") }
                Button(
                    onClick = { vm.confirmCandidateFact(fact.id) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = LanghuanShape.card,
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(5.dp))
                    Text("确认进 Canon")
                }
            }
        }
    }
}

@Composable
private fun CandidatePanelCard(
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.panel,
        color = t.card,
        border = BorderStroke(1.dp, borderColor ?: t.border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun CandidateFactValueV1(label: String, value: String, accent: Boolean) {
    val t = LocalLanghuanUiTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (accent) t.accent else t.mutedForeground)
        Text(value.ifBlank { "（空）" }, style = MaterialTheme.typography.bodyMedium, color = t.foreground)
    }
}

@Composable
private fun CandidateCountBadgeV1(label: String, color: Color) {
    Surface(shape = LanghuanShape.pill, color = color.copy(alpha = .09f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun CandidateRiskBadgeV1(risk: CandidateFactRisk) {
    val color = risk.color()
    Surface(
        shape = LanghuanShape.pill,
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .18f)),
    ) {
        Text(risk.label(), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CandidateFactRisk.color(): Color {
    val t = LocalLanghuanUiTokens.current
    return when (this) {
        CandidateFactRisk.LOW -> t.success
        CandidateFactRisk.MEDIUM -> t.warning
        CandidateFactRisk.HIGH -> t.destructive
    }
}

private fun CandidateFactRisk.weight(): Int = when (this) {
    CandidateFactRisk.HIGH -> 3
    CandidateFactRisk.MEDIUM -> 2
    CandidateFactRisk.LOW -> 1
}

private fun CandidateFactRisk.label(): String = when (this) {
    CandidateFactRisk.LOW -> "低风险"
    CandidateFactRisk.MEDIUM -> "需确认"
    CandidateFactRisk.HIGH -> "高风险"
}

private fun CandidateFactKind.label(): String = when (this) {
    CandidateFactKind.CHARACTER_NEW -> "新人物"
    CandidateFactKind.CHARACTER_LOCATION -> "人物位置"
    CandidateFactKind.CHARACTER_EMOTION -> "人物情绪"
    CandidateFactKind.CHARACTER_GOAL -> "人物目标"
    CandidateFactKind.RELATION -> "人物关系"
    CandidateFactKind.KNOWLEDGE_GAIN -> "人物获知秘密"
    CandidateFactKind.TIMELINE -> "时间线事件"
    CandidateFactKind.FORESHADOW_NEW -> "新伏笔"
    CandidateFactKind.FORESHADOW_UPDATE -> "伏笔变化"
    CandidateFactKind.BIBLE_ENTRY -> "小说圣经"
    CandidateFactKind.KNOWLEDGE_BOUNDARY -> "秘密 / 信息边界"
}
