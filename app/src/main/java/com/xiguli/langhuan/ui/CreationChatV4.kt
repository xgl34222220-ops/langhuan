package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.LanghuanMotionStatus
import com.xiguli.langhuan.ui.design.LanghuanAmbientBackdrop
import com.xiguli.langhuan.ui.design.LanghuanConstellationField
import com.xiguli.langhuan.ui.design.LanghuanGlassPanel
import com.xiguli.langhuan.ui.design.LanghuanOrb
import com.xiguli.langhuan.ui.design.LanghuanSpatialHero

/**
 * Conversation-first creation shell.
 *
 * The user can keep talking naturally while proposal / blueprint / create-book actions stay next
 * to the composer. This avoids the old workflow where important actions lived far away from the
 * latest conversation state.
 */
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
    val t = LocalLanghuanUiTokens.current
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
        state.lastExecutionPlan?.status,
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

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Box(Modifier.fillMaxSize()) {
            LanghuanAmbientBackdrop(Modifier.fillMaxSize(), active = state.isBusy)
            LanghuanConstellationField(Modifier.fillMaxSize(), active = state.isBusy)
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
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (!hasUser) {
                    item {
                        CreationWelcomeV4(onAdvancedResearch = onAdvancedResearch)
                    }
                }

                itemsIndexed(state.messages) { index, message ->
                    CreationMessageV4(
                        message = message,
                        isFirstAssistant = index == 0 && message.role != "user" && !hasUser,
                    )
                }

                state.lastRouteDecision?.let { route ->
                    item { NovelRouteTraceCardV4(route, state.lastExecutionPlan) }
                }

                if (state.streamingReply.isNotBlank()) {
                    item { CreationAssistantTextV4(state.streamingReply, streaming = true) }
                } else if (state.isBusy) {
                    item { CreationThinkingV4(state.busyLabel.ifBlank { "AI 正在继续思考…" }) }
                }

                state.error?.let { error ->
                    item {
                        CreationErrorPanelV4(
                            error = error,
                            retryTarget = state.retryTarget,
                            onRetry = viewModel::retryFailedOperation,
                            onConfigureAi = onConfigureAi,
                        )
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
                    viewModel.generateFoundation(regenerate = state.blueprintDirty || state.foundationStage >= 3)
                },
                onCreate = viewModel::createCurrentFoundation,
                onCancelCurrent = viewModel::cancelCurrentAiOperation,
            )
            }
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
    val t = LocalLanghuanUiTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text("创作", style = MaterialTheme.typography.titleLarge, color = t.foreground)
            Text("像聊天一样把一本书聊清楚", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        LanghuanBadge(stageLabel, accent = stageLabel == "蓝图完整")
        Spacer(Modifier.width(8.dp))
        Box {
            LanghuanIconButton(Icons.Rounded.MoreHoriz, "更多", onOpenMenu)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onDismissMenu,
                shape = RoundedCornerShape(t.radiusMd),
                containerColor = t.card,
                border = BorderStroke(1.dp, t.border),
            ) {
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
private fun CreationWelcomeV4(onAdvancedResearch: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanSpatialHero(
        title = "和琅嬛聊一本书",
        subtitle = "不用填表。说题材、人物、画面、参考作品，或者直接上传设定文件；对话会逐步沉淀成方案和蓝图。",
        eyebrow = "CREATION SPACE",
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        trailing = {
            LanghuanOrb(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp),
                size = 48.dp,
            )
        },
    ) {
        Surface(
            modifier = Modifier.padding(top = 16.dp).clickable(onClick = onAdvancedResearch),
            shape = RoundedCornerShape(t.radiusMd),
            color = t.card.copy(alpha = .78f),
            contentColor = t.foreground,
            border = BorderStroke(1.dp, t.border.copy(alpha = .8f)),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.TravelExplore, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "高级研究 / Reference DNA",
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = t.foreground,
                )
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = t.mutedForeground)
            }
        }
    }
}

@Composable
private fun CreationMessageV4(message: CreationChatMessage, isFirstAssistant: Boolean) {
    val t = LocalLanghuanUiTokens.current
    if (message.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                modifier = Modifier.widthIn(max = 320.dp),
                shape = RoundedCornerShape(t.radiusMd),
                color = t.foreground,
                contentColor = t.primaryForeground,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(message.text, color = t.primaryForeground, lineHeight = 22.sp)
                    if (message.attachments.isNotEmpty()) {
                        Text(
                            message.attachments.joinToString(" · ") { it.fileName },
                            modifier = Modifier.padding(top = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = t.primaryForeground.copy(alpha = .68f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    } else if (!isFirstAssistant) {
        CreationAssistantTextV4(message.text, streaming = false)
    }
}

@Composable
private fun CreationAssistantTextV4(text: String, streaming: Boolean) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = RoundedCornerShape(t.radiusSm),
            color = t.warmSurface,
            contentColor = t.accent,
            border = BorderStroke(1.dp, t.accent.copy(alpha = .14f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = t.accent)
            }
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(creationChatDisplayTextV20(text), style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp, color = t.foreground)
            if (streaming) {
                Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    LanghuanOrb(active = true, size = 18.dp)
                    Text(
                        "正在生成",
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = t.accent,
                    )
                }
            }
        }
    }
}


internal fun creationChatDisplayTextV20(raw: String): String = raw
    .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
    .replace(Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
    .replace(Regex("__(.+?)__", RegexOption.DOT_MATCHES_ALL)) { it.groupValues[1] }
    .replace("```markdown", "")
    .replace("```md", "")
    .replace("```", "")
    .trim()

@Composable
private fun CreationThinkingV4(label: String) {
    LanghuanMotionStatus(
        text = label,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        active = true,
    )
}

@Composable
private fun CreationErrorPanelV4(
    error: String,
    retryTarget: CreationRetryTarget?,
    onRetry: () -> Unit,
    onConfigureAi: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        radius = 20.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                LanghuanOrb(active = false, size = 26.dp)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("这一轮没有正常完成", style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text(error, modifier = Modifier.padding(top = 3.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (retryTarget != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(t.radiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = t.foreground, contentColor = t.primaryForeground),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (retryTarget) {
                                CreationRetryTarget.CHAT -> "重试这一轮"
                                CreationRetryTarget.PROPOSAL -> "重新整理"
                                CreationRetryTarget.BLUEPRINT -> "继续蓝图"
                            }
                        )
                    }
                }
                OutlinedButton(
                    onClick = onConfigureAi,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("切换模型")
                }
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
    onCancelCurrent: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val busy = state.isBusy || state.isLoadingAttachments
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (hasUser) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
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
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    state.pendingAttachments.forEach { attachment ->
                        Surface(
                            modifier = Modifier.clickable { onRemoveAttachment(attachment.id) },
                            shape = RoundedCornerShape(t.radiusSm),
                            color = t.muted,
                            contentColor = t.foreground,
                            border = BorderStroke(1.dp, t.border),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (attachment.mimeType.startsWith("image/")) Icons.Rounded.Image else Icons.Rounded.Description,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = t.mutedForeground,
                                )
                                Text(
                                    attachment.fileName,
                                    modifier = Modifier.padding(start = 5.dp).widthIn(max = 170.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(Icons.Rounded.Close, "移除", Modifier.padding(start = 5.dp).size(14.dp), tint = t.mutedForeground)
                            }
                        }
                    }
                }
            }

            LanghuanGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(5.dp),
                radius = 20.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = onAttach, enabled = !busy) {
                        Icon(Icons.Rounded.AttachFile, "上传文件", tint = t.mutedForeground)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInput,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 116.dp)
                            .padding(horizontal = 4.dp, vertical = 11.dp),
                        enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.foreground),
                        cursorBrush = SolidColor(t.accent),
                        decorationBox = { inner ->
                            Box {
                                if (input.isBlank()) {
                                    Text(
                                        if (state.foundation == null) "继续说你的想法…" else "继续修改这本书…",
                                        color = t.mutedForeground,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    val canSend = !busy && (input.isNotBlank() || state.pendingAttachments.isNotEmpty())
                    val canStop = state.isBusy && state.canCancelCurrentOperation
                    val actionEnabled = canSend || canStop
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(t.radiusSm),
                        color = when {
                            canStop -> t.destructive
                            canSend -> t.foreground
                            else -> t.muted
                        },
                        contentColor = if (actionEnabled) t.primaryForeground else t.mutedForeground,
                    ) {
                        Box(
                            Modifier.clickable(
                                enabled = actionEnabled,
                                onClick = if (canStop) onCancelCurrent else onSend,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (canStop) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                if (canStop) "停止生成" else "发送",
                                Modifier.size(19.dp),
                                tint = if (actionEnabled) t.primaryForeground else t.mutedForeground,
                            )
                        }
                    }
                }
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
    val t = LocalLanghuanUiTokens.current
    val container = if (emphasized) t.foreground else t.card
    val foreground = if (emphasized) t.primaryForeground else t.foreground
    Surface(
        shape = RoundedCornerShape(t.radiusSm),
        color = if (enabled) container else t.muted,
        contentColor = if (enabled) foreground else t.mutedForeground,
        border = BorderStroke(1.dp, if (emphasized && enabled) t.foreground else t.border),
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                null,
                Modifier.size(16.dp),
                tint = if (enabled) foreground else t.mutedForeground,
            )
            Text(
                label,
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) foreground else t.mutedForeground,
            )
        }
    }
}
