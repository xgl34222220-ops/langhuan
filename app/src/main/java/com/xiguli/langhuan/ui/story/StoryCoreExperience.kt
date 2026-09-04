package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * New player-facing story surface. It talks to StoryPlayV3ViewModel directly and does not mount
 * StoryPlayPanelV17 or any invisible legacy UI for side effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCoreExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val vm: StoryPlayV3ViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val anchor = libraryState.readingChapter
        ?: libraryState.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
        ?: libraryState.chapters.lastOrNull()

    var input by remember(book.id, state.active?.id) { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showBranches by remember { mutableStateOf(false) }
    var showWorld by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, anchor?.chapterNumber) {
        vm.open(book.id, anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
    }
    LaunchedEffect(state.active?.turns?.size, state.busy) {
        val count = state.active?.turns?.size ?: 0
        if (count > 0) listState.animateScrollToItem((count * 2).coerceAtLeast(0))
    }

    val session = state.active
    val world = state.runtime?.world

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        session?.title?.ifBlank { "故事" } ?: "故事",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        book.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreHoriz, "更多") }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
                val choices = session?.turns?.lastOrNull()?.choices.orEmpty()
                if (choices.isNotEmpty() && !state.busy) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        choices.forEach { choice ->
                            OutlinedButton(
                                onClick = { vm.act(book, anchor?.content.orEmpty(), choice) },
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                            ) { Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (aiReady) "说什么，或做什么……" else "先配置 AI") },
                        minLines = 1,
                        maxLines = 4,
                        enabled = aiReady && !state.busy,
                        shape = RoundedCornerShape(22.dp),
                    )
                    FilledIconButton(
                        onClick = {
                            val action = input.trim()
                            if (action.isNotBlank()) {
                                input = ""
                                vm.act(book, anchor?.content.orEmpty(), action)
                            }
                        },
                        enabled = aiReady && !state.busy && input.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Send, "发送")
                    }
                }
                if (!aiReady) {
                    TextButton(onClick = onAiSetup, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("配置 AI 后继续故事")
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (session == null || session.turns.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
                        Text(
                            session?.anchorTitle?.ifBlank { anchor?.title ?: "从这里开始" } ?: anchor?.title ?: "从这里开始",
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "这是一条独立故事线。原著正文保持不变，你只需要决定自己说什么、做什么。",
                            Modifier.padding(top = 10.dp),
                            fontSize = 15.sp,
                            lineHeight = 25.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            session?.turns.orEmpty().forEach { turn ->
                item(key = "player-${turn.id}") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            modifier = Modifier.widthIn(max = 300.dp),
                            shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                turn.player,
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item(key = "story-${turn.id}") {
                    Column(Modifier.fillMaxWidth()) {
                        Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(turn.narration, Modifier.padding(top = 7.dp), fontSize = 17.sp, lineHeight = 29.sp)
                    }
                }
            }

            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        Text("世界正在回应……", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = vm::clearError) { Icon(Icons.Rounded.Close, "关闭", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("故事", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 10.dp))
                StoryCoreMenuRow("故事分支", "切换或新建独立故事线") { showMenu = false; showBranches = true }
                HorizontalDivider()
                StoryCoreMenuRow("世界状态", "查看当前位置与故事时间") { showMenu = false; showWorld = true }
                if (!aiReady) {
                    HorizontalDivider()
                    StoryCoreMenuRow("AI 设置", "配置故事模式使用的模型") { showMenu = false; onAiSetup() }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showBranches) {
        ModalBottomSheet(onDismissRequest = { showBranches = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("故事分支", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showBranches = false }) { Icon(Icons.Rounded.Close, "关闭") }
                }
                Button(
                    onClick = {
                        vm.newBranch(anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
                        showBranches = false
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("从当前章节新建分支")
                }
                state.sessions.sortedByDescending { it.updatedAt }.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !state.busy) {
                            vm.selectSession(item.id)
                            showBranches = false
                        }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title.ifBlank { "故事分支" }, fontWeight = if (item.id == session?.id) FontWeight.SemiBold else FontWeight.Normal)
                            Text("第 ${item.anchorChapter} 章 · ${item.turns.size} 轮", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showWorld) {
        ModalBottomSheet(onDismissRequest = { showWorld = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("世界状态", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showWorld = false }) { Icon(Icons.Rounded.Close, "关闭") }
                }
                StoryCoreStateRow(Icons.Rounded.Place, "地点", world?.location?.ifBlank { "未知" } ?: "未知")
                StoryCoreStateRow(Icons.Rounded.Schedule, "时间", world?.time?.ifBlank { "未记录" } ?: "未记录")
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun StoryCoreMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoryCoreStateRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.padding(start = 10.dp).width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f))
    }
    HorizontalDivider()
}
