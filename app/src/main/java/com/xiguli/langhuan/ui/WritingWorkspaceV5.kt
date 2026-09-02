package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.engine.ProjectConversationOrigin
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * V5 keeps V4's proven chapter runtime UI intact and layers the project-scoped continuation
 * conversation on top. The author can therefore keep talking to the same creative partner
 * after book creation without mixing ordinary discussion with explicit prose/scene mutations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingWorkspaceV5(
    novelId: String,
    viewModel: WritingFlowViewModel,
    onClose: () -> Unit,
    onEditChapter: (novelId: String, chapterNumber: Int) -> Unit,
) {
    val conversationVm: ProjectConversationViewModel = viewModel()
    val conversation by conversationVm.state.collectAsStateWithLifecycle()
    var showConversation by remember { mutableStateOf(false) }

    LaunchedEffect(novelId) { conversationVm.load(novelId) }

    Box(Modifier.fillMaxSize()) {
        WritingWorkspaceV4(
            novelId = novelId,
            viewModel = viewModel,
            onClose = onClose,
            onEditChapter = onEditChapter,
        )

        ExtendedFloatingActionButton(
            onClick = { showConversation = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 118.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = { Icon(Icons.Rounded.AutoAwesome, null) },
            text = {
                Text(
                    if (conversation.messages.any { it.origin == ProjectConversationOrigin.CREATION }) {
                        "继续聊 · ${conversation.messages.size}"
                    } else {
                        "聊设定"
                    }
                )
            },
        )
    }

    if (showConversation) {
        ModalBottomSheet(
            onDismissRequest = { showConversation = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            ProjectConversationSheetV5(
                state = conversation,
                onSend = conversationVm::send,
                onClearError = conversationVm::clearError,
            )
        }
    }
}

@Composable
private fun ProjectConversationSheetV5(
    state: ProjectConversationUiState,
    onSend: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var input by remember(state.novelId) { mutableStateOf("") }
    val inheritedCount = state.messages.count { it.origin == ProjectConversationOrigin.CREATION }

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(.86f)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(42.dp).squircleClip(15.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("连续创作会话", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (inheritedCount > 0) "已承接建书阶段 $inheritedCount 条消息 · 当前项目事实优先" else "当前项目会话 · 讨论不会自动写入 Canon",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMiuixTokens.current.textSecondary,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).squircleClip(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                "你可以继续像建书时一样聊人物、世界观、逻辑、下一章和伏笔。普通聊天只讨论；真正改正文或场景仍由工作台的写作动作执行。",
                modifier = Modifier.padding(13.dp),
                style = MaterialTheme.typography.bodySmall,
                color = LocalMiuixTokens.current.textSecondary,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty() && !state.isLoading) {
                item {
                    ProjectConversationAssistantBubbleV5(
                        "这本书已经进入正式项目。继续说你的想法就行，我会读取当前章纲、人物状态、时间线和相关伏笔来接着聊。"
                    )
                }
            }
            items(state.messages, key = { it.id }) { message ->
                if (message.role == "assistant") {
                    ProjectConversationAssistantBubbleV5(message.text)
                } else {
                    ProjectConversationUserBubbleV5(message.text)
                }
            }
            if (state.streamingReply.isNotBlank()) {
                item { ProjectConversationAssistantBubbleV5(state.streamingReply, streaming = true) }
            } else if (state.isBusy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        Text(
                            state.routeSummary.ifBlank { "正在结合当前项目继续思考…" },
                            modifier = Modifier.padding(start = 9.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMiuixTokens.current.textSecondary,
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).squircleClip(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .65f),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onClearError) { Text("知道了") }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).squircleClip(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("继续和琅嬛聊这本书…") },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.width(7.dp))
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = if (input.isNotBlank() && !state.isBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotBlank()) {
                                input = ""
                                onSend(text)
                            }
                        },
                        enabled = input.isNotBlank() && !state.isBusy,
                    ) {
                        Icon(
                            Icons.Rounded.Send,
                            "发送",
                            tint = if (input.isNotBlank() && !state.isBusy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectConversationAssistantBubbleV5(text: String, streaming: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (streaming) {
                Text("正在生成", modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProjectConversationUserBubbleV5(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp).squircleClip(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
