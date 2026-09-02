package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CreationChatV4(
    viewModel: NewBookConversationViewModel,
    onClose: () -> Unit,
    onConfigureAi: () -> Unit,
    onAdvancedResearch: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addConversationAttachments(uris)
    }

    val hasUser = state.messages.any { it.role == "user" }
    val stageLabel = when {
        state.foundation != null && state.blueprintDirty -> "蓝图待同步"
        state.foundationStage >= 3 -> "蓝图完整"
        state.foundationStage > 0 -> "蓝图 ${state.foundationStage}/3"
        state.proposal != null -> "方案已整理"
        hasUser -> "正在构思"
        else -> "自由聊天"
    }

    LaunchedEffect(
        state.messages.size,
        state.streamingReply.length,
        state.pendingAttachments.size,
        state.lastRouteDecision?.status,
        state.isBusy,
        state.error,
    ) {
        val total = state.messages.size +
            (if (state.lastRouteDecision != null) 1 else 0) +
            (if (state.streamingReply.isNotBlank() || state.isBusy) 1 else 0) +
            (if (state.error != null) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LaunchedEffect(state.createdStoryId) {
        state.createdStoryId?.let { id ->
            viewModel.consumeCreatedStory()
            onCreated(id)
        }
    }

    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .18f), bg, bg)
                )
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            CreationHeaderV4(
                stageLabel = stageLabel,
                menuOpen = menuOpen,
                onBack = onClose,
                onOpenMenu = { menuOpen = true },
                onDismissMenu = { menuOpen = false },
                onConfigureAi = onConfigureAi,
                onAdvancedResearch = onAdvancedResearch,
                onReset = viewModel::reset,
                busy = state.isBusy,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (!hasUser) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp)) {
                            Text("和琅嬛聊一本书", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "不用填表。说题材、人物、画面、参考作品，或者直接上传设定文件。",
                                modifier = Modifier.padding(top = 7.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                itemsIndexed(state.messages) { index, message ->
                    CreationMessageV4(
                        message = message,
                        isFirstAssistant = index == 0 && message.role != "user" && !hasUser,
                    )
                }

                state.lastRouteDecision?.let { route ->
                    item {
                        NovelRouteTraceCardV4(route)
                    }
                }

                if (state.streamingReply.isNotBlank()) {
                    item {
                        CreationAssistantTextV4(state.streamingReply, streaming = true)
                    }
                } else if (state.isBusy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                state.busyLabel.ifBlank { "AI 正在继续思考…" },
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.error?.let { error ->
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = .7f))
                                .padding(13.dp),
                        ) {
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            CreationComposerV4(
                input = input,
                onInput = { input = it },
                state = state,
                hasUser = hasUser,
                onAttach = {
                    attachmentLauncher.launch(
                        arrayOf(
                            "text/*",
                            "application/json",
                            "application/pdf",
                            "application/epub+zip",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "image/*",
                        )
                    )
                },
                onRemoveAttachment = viewModel::removePendingAttachment,
                onSend = {
                    if (!state.isBusy && !state.isLoadingAttachments && (input.isNotBlank() || state.pendingAttachments.isNotEmpty())) {
                        val text = input
                        input = ""
                        viewModel.send(text)
                    }
                },
                onSyncProposal = viewModel::syncConversationProposal,
                onGenerateFoundation = {
                    viewModel.generateFoundation(regenerate = state.foundation != null || state.blueprintDirty)
                },
                onCreate = viewModel::createCurrentFoundation,
            )
        }
    }
}

@Composable
private fun CreationHeaderV4(
    stageLabel: String,
    menuOpen: Boolean,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onConfigureAi: () -> Unit,
    onAdvancedResearch: () -> Unit,
    onReset: () -> Unit,
    busy: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CreationRoundIconV4(Icons.Rounded.ArrowBack, "返回", onBack)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("创作", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stageLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CreationRoundIconV4(Icons.Rounded.Tune, "AI 设置", onConfigureAi)
        Spacer(Modifier.width(7.dp))
        Box {
            CreationRoundIconV4(Icons.Rounded.MoreHoriz, "更多", onOpenMenu)
            DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text("高级研究 / Reference DNA") },
                    leadingIcon = { Icon(Icons.Rounded.TravelExplore, null) },
                    onClick = { onDismissMenu(); onAdvancedResearch() },
                )
                DropdownMenuItem(
                    text = { Text("AI 服务与模型") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                    onClick = { onDismissMenu(); onConfigureAi() },
                )
                DropdownMenuItem(
                    text = { Text("重新开始") },
                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                    enabled = !busy,
                    onClick = { onDismissMenu(); onReset() },
                )
            }
        }
    }
}

@Composable
private fun CreationRoundIconV4(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .84f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(20.dp)) }
}

@Composable
private fun CreationMessageV4(message: CreationChatMessage, isFirstAssistant: Boolean) {
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(22.dp, 22.dp, 7.dp, 22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f))
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            ) {
                Text(message.text, color = MaterialTheme.colorScheme.onPrimaryContainer, lineHeight = 22.sp)
                if (message.attachments.isNotEmpty()) {
                    Text(
                        message.attachments.joinToString(" · ") { it.fileName },
                        modifier = Modifier.padding(top = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        if (!isFirstAssistant) CreationAssistantTextV4(message.text, streaming = false)
    }
}

@Composable
private fun CreationAssistantTextV4(text: String, streaming: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
            if (streaming) {
                Text("正在生成", modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CreationComposerV4(
    input: String,
    onInput: (String) -> Unit,
    state: NewBookConversationState,
    hasUser: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onSyncProposal: () -> Unit,
    onGenerateFoundation: () -> Unit,
    onCreate: () -> Unit,
) {
    val busy = state.isBusy || state.isLoadingAttachments
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        if (hasUser) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CreationStageActionV4(
                    icon = Icons.Rounded.AutoAwesome,
                    label = if (state.proposal == null) "整理方案" else "同步方案",
                    enabled = !busy,
                    onClick = onSyncProposal,
                )
                CreationStageActionV4(
                    icon = Icons.Rounded.AccountTree,
                    label = when {
                        state.foundation == null -> "生成蓝图"
                        state.blueprintDirty -> "同步蓝图"
                        state.foundationStage < 3 -> "继续蓝图"
                        else -> "重建蓝图"
                    },
                    enabled = !busy,
                    onClick = onGenerateFoundation,
                )
                if (state.foundationStage >= 1 && !state.blueprintDirty) {
                    CreationStageActionV4(
                        icon = Icons.Rounded.LibraryAdd,
                        label = "正式建书",
                        enabled = !busy,
                        emphasized = true,
                        onClick = onCreate,
                    )
                }
            }
        }

        if (state.pendingAttachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                state.pendingAttachments.forEach { attachment ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f))
                            .clickable { onRemoveAttachment(attachment.id) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description, null, Modifier.size(16.dp))
                        Text(attachment.fileName, modifier = Modifier.padding(start = 5.dp).widthIn(max = 170.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.Close, "移除", Modifier.padding(start = 5.dp).size(14.dp))
                    }
                }
            }
        }

        val shape = RoundedCornerShape(28.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .95f))
                .border(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f), shape)
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = onAttach, enabled = !busy) {
                Icon(Icons.Rounded.AttachFile, "上传文件")
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 116.dp).padding(horizontal = 4.dp, vertical = 11.dp),
                enabled = !busy,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (input.isBlank()) {
                            Text(
                                if (state.foundation == null) "继续说你的想法…" else "继续修改这本书…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (!busy && (input.isNotBlank() || state.pendingAttachments.isNotEmpty())) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = !busy && (input.isNotBlank() || state.pendingAttachments.isNotEmpty()), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    "发送",
                    tint = if (!busy && (input.isNotBlank() || state.pendingAttachments.isNotEmpty())) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreationStageActionV4(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (emphasized) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f)
                else MaterialTheme.colorScheme.surface.copy(alpha = .82f)
            )
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
        Text(
            label,
            modifier = Modifier.padding(start = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f),
        )
    }
}
