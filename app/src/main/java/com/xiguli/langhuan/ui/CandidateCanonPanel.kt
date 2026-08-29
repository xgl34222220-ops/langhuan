package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.CandidateFact
import com.xiguli.langhuan.domain.CandidateFactKind
import com.xiguli.langhuan.domain.CandidateFactRisk
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
internal fun CandidateCanonPanel(state: StudioUiState, vm: StudioViewModel) {
    val pending = state.snapshot.candidateFacts
        .filter { it.status == CandidateFactStatus.PENDING }
        .sortedWith(compareByDescending<CandidateFact> { it.risk.weight() }.thenByDescending { it.createdAt })
    val confirmed = state.snapshot.candidateFacts.count { it.status == CandidateFactStatus.CONFIRMED }
    val rejected = state.snapshot.candidateFacts.count { it.status == CandidateFactStatus.REJECTED }

    CandidatePanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FactCheck, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text("Candidate / Canon", style = MaterialTheme.typography.titleMedium)
                Text(
                    "AI 提案先进入候选区，确认后才成为正式事实。PENDING 不进入正文上下文、RAG 或长期记忆。",
                    color = LocalMiuixTokens.current.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "待确认 ${pending.size} · 已确认 $confirmed · 已拒绝 $rejected",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        if (pending.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("当前没有待确认事实。低风险且能从已保存正文直接证明的状态变化会自动确认。", color = LocalMiuixTokens.current.textSecondary)
        }
    }

    pending.take(20).forEach { fact ->
        CandidatePanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (fact.risk == CandidateFactRisk.HIGH) Icons.Rounded.WarningAmber else Icons.Rounded.Shield,
                    null,
                    tint = if (fact.risk == CandidateFactRisk.HIGH) LocalMiuixTokens.current.warning else MaterialTheme.colorScheme.primary,
                )
                Text(fact.kind.label(), Modifier.padding(start = 7.dp).weight(1f), fontWeight = FontWeight.Bold)
                AssistChip(onClick = {}, label = { Text(fact.risk.label()) })
            }
            Text(fact.subject, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            if (fact.before.isNotBlank()) {
                Text("原状态：${fact.before}", color = LocalMiuixTokens.current.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Text("候选事实：${fact.after}", modifier = Modifier.padding(top = 4.dp))
            if (fact.evidence.isNotBlank()) {
                Text("正文依据：${fact.evidence}", color = LocalMiuixTokens.current.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            fact.validationNotes.take(3).forEach { note ->
                Text("· $note", color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.rejectCandidateFact(fact.id) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("拒绝") }
                Button(
                    onClick = { vm.confirmCandidateFact(fact.id) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
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
private fun CandidatePanelCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(2.dp, shape)
            .squircleClip(26.dp)
            .background(LocalMiuixTokens.current.cardBackground.copy(alpha = .94f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f), shape)
            .padding(18.dp),
        content = content,
    )
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
