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
 * 建书会谈的常驻快捷操作。
 *
 * 参考 DNA / 整理方案 / 建书蓝图不再依赖聊天列表顶部，用户聊到任何位置都能直接操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreationQuickActionsOverlay(viewModel: NewBookConversationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasUserMessage = state.messages.any { it.role == "user" }
    if (!hasUserMessage) return

    var showReferenceSheet by remember { mutableStateOf(false) }
    var showBlueprintSheet by remember { mutableStateOf(false) }
    val disabled = state.isBusy || state.isLoadingAttachments

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 108.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.AutoStories, null, Modifier.size(18.dp)) },
                    text = if (state.selectedReferenceTemplateIds.isEmpty()) "参考 DNA" else "参考 ${state.selectedReferenceTemplateIds.size}本",
                    enabled = !disabled,
                    onClick = { showReferenceSheet = true },
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(if (state.proposal == null) Icons.Rounded.AutoAwesome else Icons.Rounded.Sync, null, Modifier.size(18.dp)) },
                    text = if (state.proposal == null) "整理方案" else "同步方案",
                    enabled = !disabled,
                    onClick = viewModel::syncConversationProposal,
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Rounded.AccountTree, null, Modifier.size(18.dp)) },
                    text = when {
                        state.foundation == null -> "建书蓝图"
                        state.blueprintDirty -> "同步蓝图"
                        else -> "查看蓝图"
                    },
                    enabled = !disabled,
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
    }

    if (showReferenceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReferenceSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("参考 DNA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "随时增删参考，不需要回到聊天顶部。选中的参考会继续参与方案、蓝图和后续写作。",
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
                    Text("建书蓝图", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(foundation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${foundation.genre} · ${foundation.targetWords / 10_000} 万字", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("故事承诺", fontWeight = FontWeight.SemiBold)
                            Text(foundation.storyPromise)
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
                        ) {
                            Icon(Icons.Rounded.Sync, null)
                            Spacer(Modifier.width(7.dp))
                            Text("同步当前聊天到蓝图")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                showBlueprintSheet = false
                                viewModel.generateFoundation(true)
                            },
                            enabled = !disabled,
                            modifier = Modifier.weight(1f),
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
                            enabled = !disabled && !state.blueprintDirty,
                            modifier = Modifier.weight(1f),
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
private fun QuickActionButton(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}
