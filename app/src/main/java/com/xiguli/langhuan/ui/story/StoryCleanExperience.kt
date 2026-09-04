package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Player-facing story screen with one visual hierarchy: story -> choices -> input.
 * Runtime compatibility stays mounted invisibly, while all author/debug controls are kept out of
 * the player surface and exposed only through the compact overflow sheet.
 */
@Composable
fun StoryCleanExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    var advanced by remember(book.id) { mutableStateOf(false) }
    if (advanced) {
        StoryManagementExperience(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
            onClose = { advanced = false },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Keep the legacy runtime/side-effect chain alive without exposing its accumulated controls.
        Box(Modifier.fillMaxSize().alpha(0f)) {
            StoryPlayPanelV17(book, libraryState, aiReady, onAiSetup, onAdopted)
        }
        StoryCleanPlayer(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdvanced = { advanced = true },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryCleanPlayer(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val vm: StoryPlayV3ViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val t = LocalLanghuanUiTokens.current
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
    val runtime = state.runtime
    val world = runtime?.world

    Scaffold(
        containerColor = t.background,
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
                        color = t.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        book.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = t.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreHoriz, "更多", tint = t.foreground)
                }
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
                                border = BorderStroke(1.dp, t.border),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                            ) {
                                Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            }
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = t.background,
                            unfocusedContainerColor = t.background,
                            focusedBorderColor = t.border,
                            unfocusedBorderColor = t.border,
                        ),
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
                            session?.anchorTitle?.ifBlank { anchor?.title ?: "从这里开始" }
                                ?: anchor?.title ?: "从这里开始",
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = t.foreground,
                        )
                        Text(
                            "原著保持不变。这里是一条独立故事线，你只需要决定自己说什么、做什么。",
                            Modifier.padding(top = 10.dp),
                            fontSize = 15.sp,
                            lineHeight = 25.sp,
                            color = t.mutedForeground,
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
                            color = t.foreground,
                        ) {
                            Text(
                                turn.player,
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = t.background,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item(key = "story-${turn.id}") {
                    Column(Modifier.fillMaxWidth()) {
                        Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        Text(
                            turn.narration,
                            Modifier.padding(top = 7.dp),
                            fontSize = 17.sp,
                            lineHeight = 29.sp,
                            color = t.foreground,
                        )
                    }
                }
            }

            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = t.accent)
                        Text("世界正在回应……", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, Modifier.weight(1f), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = vm::clearError) { Icon(Icons.Rounded.Close, "关闭", tint = t.destructive) }
                    }
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("故事", style = MaterialTheme.typography.titleLarge, color = t.foreground, modifier = Modifier.padding(vertical = 10.dp))
                StoryCleanMenuRow("故事分支", "切换或新建独立故事线") {
                    showMenu = false
                    showBranches = true
                }
                HorizontalDivider(color = t.border)
                StoryCleanMenuRow("世界状态", "查看当前位置与故事时间") {
                    showMenu = false
                    showWorld = true
                }
                HorizontalDivider(color = t.border)
                StoryCleanMenuRow("角色与世界管理", "角色、NPC 记忆、原著入场与高级状态") {
                    showMenu = false
                    onAdvanced()
                }
                if (!aiReady) {
                    HorizontalDivider(color = t.border)
                    StoryCleanMenuRow("AI 设置", "配置故事模式使用的模型") {
                        showMenu = false
                        onAiSetup()
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showBranches) {
        ModalBottomSheet(onDismissRequest = { showBranches = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("故事分支", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), color = t.foreground)
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
                            Text(item.title.ifBlank { "故事分支" }, color = t.foreground, fontWeight = if (item.id == session?.id) FontWeight.SemiBold else FontWeight.Normal)
                            Text("第 ${item.anchorChapter} 章 · ${item.turns.size} 轮", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        Icon(Icons.Rounded.ArrowForward, null, tint = t.mutedForeground)
                    }
                    HorizontalDivider(color = t.border)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showWorld) {
        ModalBottomSheet(onDismissRequest = { showWorld = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("世界状态", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), color = t.foreground)
                    IconButton(onClick = { showWorld = false }) { Icon(Icons.Rounded.Close, "关闭") }
                }
                StoryCleanStateRow(Icons.Rounded.Place, "地点", world?.location?.ifBlank { "未知" } ?: "未知")
                StoryCleanStateRow(Icons.Rounded.Schedule, "时间", world?.time?.ifBlank { "未记录" } ?: "未记录")
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun StoryCleanMenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        Icon(Icons.Rounded.ArrowForward, null, tint = t.mutedForeground)
    }
}

@Composable
private fun StoryCleanStateRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = t.mutedForeground)
        Text(label, Modifier.padding(start = 10.dp).width(56.dp), color = t.mutedForeground)
        Text(value, Modifier.weight(1f), color = t.foreground)
    }
    HorizontalDivider(color = t.border)
}
