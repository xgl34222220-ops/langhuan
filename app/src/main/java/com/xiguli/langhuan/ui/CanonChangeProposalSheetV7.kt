package com.xiguli.langhuan.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.CanonChangeRisk
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CanonChangeProposalSheetV7(
    state: CanonChangeProposalUiState,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!state.active) onDismiss() },
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
                    modifier = Modifier.size(42.dp).squircleClip(15.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("Canon 变更提案", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "先看差异和影响；确认前不会写入 StorySnapshot",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
            }

            if (state.isBusy) {
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        Text(
                            state.message ?: "正在生成提案……",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            state.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            error,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            val proposal = state.proposal
            if (proposal != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(proposal.summary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${proposal.patches.size} 项字段修改 · ${proposal.impacts.size} 处关联影响",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMiuixTokens.current.textSecondary,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    item {
                        Text("差异预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(proposal.patches, key = { "${it.targetType}:${it.targetId}:${it.field}" }) { patch ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().squircleClip(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${patch.targetLabel} · ${patch.field}",
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        when (patch.risk) {
                                            CanonChangeRisk.LOW -> "低风险"
                                            CanonChangeRisk.MEDIUM -> "中风险"
                                            CanonChangeRisk.HIGH -> "高风险"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (patch.risk == CanonChangeRisk.HIGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text("旧值", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = LocalMiuixTokens.current.textSecondary)
                                Text(patch.before.ifBlank { "（空）" }, style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                Text("新值", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(patch.after.ifBlank { "（空）" }, style = MaterialTheme.typography.bodyMedium)
                                if (patch.reason.isNotBlank()) {
                                    Text(
                                        patch.reason,
                                        modifier = Modifier.padding(top = 7.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalMiuixTokens.current.textSecondary,
                                    )
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
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        items(proposal.impacts, key = { "${it.scope}:${it.label}:${it.chapterNumber}" }) { impact ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().squircleClip(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Rounded.WarningAmber, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                        Text("${impact.scope} · ${impact.label}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text(impact.detail, style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                                    }
                                }
                            }
                        }
                    }

                    if (proposal.warnings.isNotEmpty()) {
                        item {
                            Text("提案警告", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(proposal.warnings) { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (state.isApplying) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在重新校验并写入……", modifier = Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDiscard,
                        enabled = !state.active,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(17.dp),
                    ) { Text("放弃提案") }
                    Button(
                        onClick = onApply,
                        enabled = !state.active,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(17.dp),
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
