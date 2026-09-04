package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Difference
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.CanonChangeRisk
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanonChangeProposalSheetV7(
    state: CanonChangeProposalUiState,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    ModalBottomSheet(
        onDismissRequest = { if (!state.active) onDismiss() },
        containerColor = t.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = LanghuanShape.card,
                    color = t.warmSurface,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .18f)),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountTree, null, tint = t.accent)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Canon 变更提案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = t.foreground)
                    Text(
                        "先审差异和影响；确认前不会写入 StorySnapshot",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }

            if (state.isBusy) {
                InspectorNoticeV7(
                    text = state.message ?: "正在生成提案……",
                    tone = InspectorToneV7.RUNNING,
                    progress = true,
                )
            }

            state.error?.let { error ->
                InspectorNoticeV7(text = error, tone = InspectorToneV7.ERROR)
            }

            val proposal = state.proposal
            if (proposal != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LanghuanShape.panel,
                    color = t.card,
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Difference, null, Modifier.size(18.dp), tint = t.accent)
                            Text(
                                "提案摘要",
                                modifier = Modifier.padding(start = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = t.accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(proposal.summary, fontWeight = FontWeight.SemiBold, color = t.foreground)
                        Text(
                            "${proposal.patches.size} 项字段修改 · ${proposal.impacts.size} 处关联影响${if (proposal.warnings.isNotEmpty()) " · ${proposal.warnings.size} 条警告" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item {
                        Text("差异预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
                    }
                    items(proposal.patches, key = { "${it.targetType}:${it.targetId}:${it.field}" }) { patch ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LanghuanShape.panel,
                            color = t.card,
                            border = BorderStroke(1.dp, t.border),
                        ) {
                            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(patch.targetLabel, fontWeight = FontWeight.SemiBold, color = t.foreground)
                                        Text(patch.field, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                                    }
                                    CanonRiskBadgeV7(patch.risk)
                                }
                                DiffValueBlockV7(label = "旧值", value = patch.before.ifBlank { "（空）" }, accent = false)
                                HorizontalDivider(color = t.border)
                                DiffValueBlockV7(label = "新值", value = patch.after.ifBlank { "（空）" }, accent = true)
                                if (patch.reason.isNotBlank()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = LanghuanShape.chip,
                                        color = t.muted,
                                    ) {
                                        Text(
                                            "修改理由：${patch.reason}",
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = t.mutedForeground,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (proposal.impacts.isNotEmpty()) {
                        item {
                            Text(
                                "受影响范围",
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = t.foreground,
                            )
                        }
                        items(proposal.impacts, key = { "${it.scope}:${it.label}:${it.chapterNumber}" }) { impact ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LanghuanShape.card,
                                color = t.warning.copy(alpha = .07f),
                                border = BorderStroke(1.dp, t.warning.copy(alpha = .18f)),
                            ) {
                                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Rounded.WarningAmber, null, Modifier.size(17.dp), tint = t.warning)
                                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                        Text("${impact.scope} · ${impact.label}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = t.foreground)
                                        Text(impact.detail, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                    }
                                }
                            }
                        }
                    }

                    if (proposal.warnings.isNotEmpty()) {
                        item {
                            Text("提案警告", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.destructive)
                        }
                        items(proposal.warnings) { warning ->
                            InspectorNoticeV7(text = warning, tone = InspectorToneV7.ERROR)
                        }
                    }
                }

                if (state.isApplying) {
                    InspectorNoticeV7(text = "正在重新校验并写入 StorySnapshot……", tone = InspectorToneV7.RUNNING, progress = true)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = LanghuanShape.card,
                    color = t.muted,
                ) {
                    Text(
                        "确认写入后，受影响的章节、记忆或结构会进入 Canon 修复队列；正文不会因为这个按钮被静默重写。",
                        Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDiscard,
                        enabled = !state.active,
                        modifier = Modifier.weight(1f),
                        shape = LanghuanShape.card,
                    ) { Text("放弃提案") }
                    Button(
                        onClick = onApply,
                        enabled = !state.active,
                        modifier = Modifier.weight(1f),
                        shape = LanghuanShape.card,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp))
                        Text("确认写入", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CanonRiskBadgeV7(risk: CanonChangeRisk) {
    val t = LocalLanghuanUiTokens.current
    val label: String
    val color: Color
    when (risk) {
        CanonChangeRisk.LOW -> {
            label = "低风险"
            color = t.success
        }
        CanonChangeRisk.MEDIUM -> {
            label = "中风险"
            color = t.warning
        }
        CanonChangeRisk.HIGH -> {
            label = "高风险"
            color = t.destructive
        }
    }
    Surface(
        shape = LanghuanShape.pill,
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .20f)),
    ) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiffValueBlockV7(label: String, value: String, accent: Boolean) {
    val t = LocalLanghuanUiTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (accent) t.accent else t.mutedForeground)
        Text(value, style = if (accent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, color = t.foreground)
    }
}

private enum class InspectorToneV7 { RUNNING, ERROR }

@Composable
private fun InspectorNoticeV7(text: String, tone: InspectorToneV7, progress: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    val color = when (tone) {
        InspectorToneV7.RUNNING -> t.accent
        InspectorToneV7.ERROR -> t.destructive
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.card,
        color = color.copy(alpha = .07f),
        border = BorderStroke(1.dp, color.copy(alpha = .18f)),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (progress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
            else Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp), tint = color)
            Text(text, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodySmall, color = t.foreground)
        }
    }
}
