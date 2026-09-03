package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Player-facing story surface. The V3→V17 wrapper stack is temporarily kept alive invisibly so
 * Original Canon, role snapshots, NPC memory, spatial/perception and takeover side effects keep
 * running while their scattered floating controls are consolidated behind “高级世界工具”.
 */
@Composable
fun StoryExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    var advanced by remember(book.id) { mutableStateOf(false) }

    if (advanced) {
        Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(book, libraryState, aiReady, onAiSetup, onAdopted)
            Surface(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
                shadowElevation = 5.dp,
            ) {
                TextButton(onClick = { advanced = false }) {
                    Icon(Icons.Rounded.ArrowBack, null)
                    Spacer(Modifier.width(6.dp))
                    Text("回到故事")
                }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().alpha(0f)) {
            StoryPlayPanelV17(book, libraryState, aiReady, onAiSetup, onAdopted)
        }
        StoryImmersiveSurface(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdvanced = { advanced = true },
        )
    }
}

@Composable
private fun StoryImmersiveSurface(
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
    var showBranches by remember { mutableStateOf(false) }
    var showContext by remember { mutableStateOf(false) }

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
    val player = session?.playerProfile
    val location = world?.location?.takeIf { it.isNotBlank() } ?: "未知地点"
    val storyTime = world?.time?.takeIf { it.isNotBlank() } ?: "第 ${session?.anchorChapter ?: anchor?.chapterNumber ?: 1} 章"

    Scaffold(
        containerColor = t.background,
        topBar = {
            Surface(color = t.background, border = BorderStroke(0.dp, Color.Transparent)) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session?.title ?: "我的故事",
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
                        IconButton(onClick = { showContext = true }) { Icon(Icons.Rounded.Explore, "世界状态", tint = t.foreground) }
                        IconButton(onClick = { showBranches = true }) { Icon(Icons.Rounded.ForkRight, "故事分支", tint = t.foreground) }
                        IconButton(onClick = onAdvanced) { Icon(Icons.Rounded.Tune, "高级世界工具", tint = t.foreground) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StoryContextChip(Icons.Rounded.Place, location)
                        StoryContextChip(Icons.Rounded.Schedule, storyTime)
                        player?.name?.takeIf { it.isNotBlank() }?.let { StoryContextChip(Icons.Rounded.Person, it) }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = t.background,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, t.border.copy(alpha = .65f)),
            ) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    val choices = session?.turns?.lastOrNull()?.choices.orEmpty()
                    if (choices.isNotEmpty() && !state.busy) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            choices.forEach { choice ->
                                AssistChip(
                                    onClick = { vm.act(book, anchor?.content.orEmpty(), choice) },
                                    label = { Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = t.muted),
                                    border = BorderStroke(1.dp, t.border),
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("说什么，或做什么……") },
                            minLines = 1,
                            maxLines = 4,
                            enabled = !state.busy,
                            shape = RoundedCornerShape(t.radiusLg),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = t.card,
                                unfocusedContainerColor = t.card,
                                focusedBorderColor = t.ring,
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
                            modifier = Modifier.size(52.dp),
                        ) {
                            if (state.busy) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Send, "发送")
                        }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!aiReady) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(t.radiusLg),
                        color = t.destructive.copy(alpha = .08f),
                        border = BorderStroke(1.dp, t.destructive.copy(alpha = .2f)),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudOff, null, tint = t.destructive)
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text("故事模式需要 AI", fontWeight = FontWeight.SemiBold, color = t.foreground)
                                Text("配置模型后可以继续当前分支，不会丢失已有剧情。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            TextButton(onClick = onAiSetup) { Text("配置") }
                        }
                    }
                }
            }

            if (session == null || session.turns.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)) {
                        Text(
                            session?.anchorTitle?.ifBlank { "故事从这里继续" } ?: "故事从这里继续",
                            fontSize = 25.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = t.foreground,
                        )
                        Text(
                            "原著正文不会被改写。你在这个独立分支里决定自己要说什么、做什么；人物知识、关系和世界状态会跟着每一轮推进。",
                            Modifier.padding(top = 12.dp),
                            fontSize = 16.sp,
                            lineHeight = 27.sp,
                            color = t.mutedForeground,
                        )
                        if (player?.name.isNullOrBlank()) {
                            Text(
                                "还没有设置角色身份。可以直接开始，也可以从右上角「高级世界工具」设置原著角色、原创角色或自己的身份。",
                                Modifier.padding(top = 14.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = t.accent,
                            )
                        }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(99.dp), color = t.warmSurface) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(7.dp).size(15.dp), tint = t.accent)
                            }
                            Text("琅嬛", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
                        }
                        Text(
                            turn.narration,
                            Modifier.padding(top = 10.dp),
                            fontSize = 17.sp,
                            lineHeight = 29.sp,
                            color = t.foreground,
                        )
                    }
                }
            }

            if (state.busy) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.accent)
                        Text("世界正在回应……", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Surface(shape = RoundedCornerShape(t.radiusMd), color = t.destructive.copy(alpha = .08f), border = BorderStroke(1.dp, t.destructive.copy(alpha = .2f))) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, Modifier.weight(1f), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = vm::clearError) { Icon(Icons.Rounded.Close, "关闭", tint = t.destructive) }
                        }
                    }
                }
            }
            state.notice?.let { notice ->
                item {
                    Text(notice, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                }
            }
        }
    }

    if (showBranches) {
        StoryBranchSheet(
            sessions = state.sessions,
            activeId = session?.id,
            busy = state.busy,
            onSelect = { vm.selectSession(it); showBranches = false },
            onNew = { vm.newBranch(anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty()); showBranches = false },
            onDismiss = { showBranches = false },
        )
    }
    if (showContext) {
        StoryContextSheet(
            session = session,
            runtime = runtime,
            onAdvanced = { showContext = false; onAdvanced() },
            onDismiss = { showContext = false },
        )
    }
}

@Composable
private fun StoryContextChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(shape = RoundedCornerShape(99.dp), color = t.muted) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(13.dp), tint = t.mutedForeground)
            Text(text, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground, maxLines = 1)
        }
    }
}

@Composable
private fun StoryBranchSheet(
    sessions: List<StoryPlaySession>,
    activeId: String?,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("故事分支", style = MaterialTheme.typography.titleLarge, color = t.foreground); Text("每个分支的互动和状态互不覆盖", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Button(onClick = onNew, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("从当前章节新建分支") }
            sessions.sortedByDescending { it.updatedAt }.forEach { session ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onSelect(session.id) },
                    shape = RoundedCornerShape(t.radiusMd),
                    color = if (session.id == activeId) t.warmSurface else t.card,
                    border = BorderStroke(1.dp, if (session.id == activeId) t.accent.copy(alpha = .28f) else t.border),
                ) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, fontWeight = FontWeight.SemiBold, color = t.foreground)
                            Text("第 ${session.anchorChapter} 章 · ${session.turns.size} 轮互动", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        if (session.id == activeId) Icon(Icons.Rounded.CheckCircle, "当前", tint = t.accent)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StoryContextSheet(
    session: StoryPlaySession?,
    runtime: StoryRuntimeSessionV3?,
    onAdvanced: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val world = runtime?.world
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("此刻的世界", style = MaterialTheme.typography.titleLarge, color = t.foreground); Text("只展示当前分支真实状态", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            StoryContextRow("地点", world?.location.orEmpty().ifBlank { "未记录" })
            StoryContextRow("时间", world?.time.orEmpty().ifBlank { "未记录" })
            StoryContextRow("环境", world?.atmosphere.orEmpty().ifBlank { "未记录" })
            StoryContextRow("局势", world?.situation.orEmpty().ifBlank { session?.anchorTitle.orEmpty().ifBlank { "未记录" } })
            HorizontalDivider(color = t.border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryCountCard("知识", runtime?.knowledge?.size ?: 0, Modifier.weight(1f))
                StoryCountCard("关系", runtime?.relationships?.size ?: 0, Modifier.weight(1f))
                StoryCountCard("变量", session?.variables?.size ?: 0, Modifier.weight(1f))
            }
            session?.playerProfile?.let { profile ->
                if (profile.name.isNotBlank() || profile.identity.isNotBlank()) {
                    StoryContextRow("当前身份", listOf(profile.name, profile.identity).filter { it.isNotBlank() }.joinToString(" · "))
                }
            }
            OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Tune, null)
                Spacer(Modifier.width(6.dp))
                Text("高级世界工具")
            }
            Text(
                "角色卡、原著角色入场快照、NPC 记忆、空间/感知、世界状态编辑、分支管理与转章节草稿暂时集中在高级工具；后续会逐项迁入这个沉浸层。",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StoryContextRow(label: String, value: String) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
        Text(value, Modifier.padding(top = 2.dp), color = t.foreground, lineHeight = 22.sp)
    }
}

@Composable
private fun StoryCountCard(label: String, value: Int, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier, shape = RoundedCornerShape(t.radiusMd), color = t.muted, border = BorderStroke(1.dp, t.border)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.foreground)
            Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
        }
    }
}
