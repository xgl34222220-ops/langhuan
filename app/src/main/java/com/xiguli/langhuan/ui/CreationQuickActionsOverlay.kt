package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 建书会谈常驻快捷操作。
 *
 * 这块只负责“随时可达”，不再做一整块高阴影大卡片。三个动作各自表达状态：
 * - 参考：始终是中性的入口；
 * - 方案：没有方案时强调，已有方案后显示“更新方案”；
 * - 蓝图：未生成/已过期时强调，已同步时退回“查看蓝图”。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreationQuickActionsOverlay(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasUserMessage = state.messages.any { it.role == "user" }
    if (!hasUserMessage) return

    var showReferenceSheet by remember { mutableStateOf(false) }
    var showBlueprintSheet by remember { mutableStateOf(false) }
    val blocked = state.isLoadingAttachments
    val proposalBusy = state.isBusy && state.busyLabel.contains("方案")
    val blueprintBusy = state.isBusy && (
        state.busyLabel.contains("蓝图") ||
            state.busyLabel.contains("分阶段") ||
            state.busyLabel.contains("章纲") ||
            state.busyLabel.contains("伏笔")
        )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            modifier = Modifier
                .padding(start = 18.dp, end = 18.dp, bottom = 108.dp)
                .widthIn(max = 430.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreationDockAction(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Rounded.AutoStories, null, Modifier.size(17.dp)) },
                text = if (state.selectedReferenceTemplateIds.isEmpty()) "参考" else "参考·${state.selectedReferenceTemplateIds.size}",
                enabled = !state.isBusy && !blocked,
                emphasized = false,
                onClick = { showReferenceSheet = true },
            )
            CreationDockAction(
                modifier = Modifier.weight(1f),
                icon = {
                    if (proposalBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(if (state.proposal == null) Icons.Rounded.AutoAwesome else Icons.Rounded.Sync, null, Modifier.size(17.dp))
                },
                text = when {
                    proposalBusy -> "整理中"
                    state.proposal == null -> "整理方案"
                    else -> "更新方案"
                },
                enabled = !state.isBusy && !blocked,
                emphasized = state.proposal == null,
                onClick = viewModel::syncConversationProposal,
            )
            CreationDockAction(
                modifier = Modifier.weight(1f),
                icon = {
                    if (blueprintBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(
                        if (state.foundation != null && !state.blueprintDirty) Icons.Rounded.CheckCircle else Icons.Rounded.AccountTree,
                        null,
                        Modifier.size(17.dp),
                    )
                },
                text = when {
                    blueprintBusy -> "生成中"
                    state.foundation == null -> "建书蓝图"
                    state.blueprintDirty -> "更新蓝图"
                    else -> "查看蓝图"
                },
                enabled = !state.isBusy && !blocked,
                emphasized = state.foundation == null || state.blueprintDirty,
                onClick = {
                    when {
                        state.foundation == null -> viewModel.generateFoundation(false)
                        state.blueprintDirty -> viewModel.generateFoundation(false)
                        else -> showBlueprintSheet = true
                    }
                },
            )
        }
    }

    if (showReferenceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReferenceSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            Icons.Rounded.AutoStories,
                            null,
                            modifier = Modifier.padding(10.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("参考 DNA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.selectedReferenceTemplateIds.isEmpty()) "当前没有绑定参考" else "当前已选 ${state.selectedReferenceTemplateIds.size} 本，可随时调整",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "参考只提供检索和创作迁移，不会替代当前作品已经确认的设定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ReferenceTemplateSelectionPanel(viewModel)
            }
        }
    }

    if (showBlueprintSheet) {
        val foundation = state.foundation
        if (foundation != null) {
            ModalBottomSheet(
                onDismissRequest = { showBlueprintSheet = false },
                dragHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (state.blueprintDirty) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                if (state.blueprintDirty) Icons.Rounded.Sync else Icons.Rounded.CheckCircle,
                                null,
                                modifier = Modifier.padding(10.dp).size(20.dp),
                                tint = if (state.blueprintDirty) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("建书蓝图", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (state.blueprintDirty) "聊天里有新要求，蓝图待更新" else "蓝图已与当前会谈同步",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(foundation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${foundation.genre} · ${foundation.targetWords / 10_000} 万字", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                            Text("故事承诺", fontWeight = FontWeight.SemiBold)
                            Text(foundation.storyPromise, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "核心角色 ${foundation.characters.size} 人 · ${foundation.volumes.size} 卷 · 伏笔 ${foundation.foreshadowing.size} 条 · 圣经 ${foundation.bible.size} 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.blueprintDirty) {
                        Button(
                            onClick = {
                                showBlueprintSheet = false
                                viewModel.generateFoundation(false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Sync, null)
                            Spacer(Modifier.width(7.dp))
                            Text("把当前聊天更新到蓝图")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showBlueprintSheet = false
                                viewModel.generateFoundation(true)
                            },
                            enabled = !state.isBusy && !blocked,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(5.dp))
                            Text("整套重做")
                        }
                        Button(
                            onClick = {
                                showBlueprintSheet = false
                                viewModel.createCurrentFoundation()
                            },
                            enabled = !state.isBusy && !blocked && !state.blueprintDirty,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null)
                            Spacer(Modifier.width(5.dp))
                            Text("正式建书")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreationDockAction(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    text: String,
    enabled: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val container = when {
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
        tonalElevation = if (emphasized) 2.dp else 1.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(5.dp))
            Text(text, maxLines = 1, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
