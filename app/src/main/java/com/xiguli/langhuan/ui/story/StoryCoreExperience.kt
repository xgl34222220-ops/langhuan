package com.xiguli.langhuan.ui.story

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.ShadcnButton
import com.xiguli.langhuan.ui.design.ShadcnButtonSize
import com.xiguli.langhuan.ui.design.ShadcnButtonVariant
import com.xiguli.langhuan.ui.design.ShadcnCard
import com.xiguli.langhuan.ui.design.ShadcnIconButton
import com.xiguli.langhuan.ui.design.ShadcnMenuRow
import com.xiguli.langhuan.ui.design.ShadcnSeparator
import com.xiguli.langhuan.ui.reader.LibraryExperienceState
import com.xiguli.langhuan.ui.reader.ReaderBookUi
import com.xiguli.langhuan.ui.theme.LanghuanShape

/**
 * Player-facing story surface. The runtime stays on StoryPlayV3ViewModel while the visual shell
 * follows shadcn/ui New York: flat neutral canvas, compact bordered controls and grouped menus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryCoreExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
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
        containerColor = t.background,
        topBar = {
            Surface(color = t.background, contentColor = t.foreground) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(start = 68.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session?.title?.ifBlank { "故事" } ?: "故事",
                                style = MaterialTheme.typography.titleMedium,
                                color = t.foreground,
                                fontWeight = FontWeight.SemiBold,
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
                        ShadcnIconButton(Icons.Rounded.MoreHoriz, "更多", { showMenu = true })
                    }
                    HorizontalDivider(thickness = 1.dp, color = t.border)
                }
            }
        },
        bottomBar = {
            Surface(color = t.card, contentColor = t.foreground) {
                Column {
                    HorizontalDivider(thickness = 1.dp, color = t.border)
                    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        val choices = session?.turns?.lastOrNull()?.choices.orEmpty()
                        if (choices.isNotEmpty() && !state.busy) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                choices.forEach { choice ->
                                    ShadcnButton(
                                        text = choice,
                                        onClick = { vm.act(book, anchor?.content.orEmpty(), choice) },
                                        variant = ShadcnButtonVariant.OUTLINE,
                                        size = ShadcnButtonSize.XS,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (aiReady) "说什么，或做什么……" else "先配置 AI", color = t.mutedForeground) },
                                minLines = 1,
                                maxLines = 4,
                                enabled = aiReady && !state.busy,
                                shape = LanghuanShape.card,
                            )
                            ShadcnButton(
                                text = "",
                                onClick = {
                                    val action = input.trim()
                                    if (action.isNotBlank()) {
                                        input = ""
                                        vm.act(book, anchor?.content.orEmpty(), action)
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                enabled = aiReady && !state.busy && input.isNotBlank(),
                                size = ShadcnButtonSize.ICON,
                                leadingIcon = Icons.Rounded.Send,
                            )
                        }
                        if (!aiReady) {
                            ShadcnButton(
                                text = "配置 AI 后继续故事",
                                onClick = onAiSetup,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                                variant = ShadcnButtonVariant.GHOST,
                                size = ShadcnButtonSize.SM,
                                leadingIcon = Icons.Rounded.Settings,
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (session == null || session.turns.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp)) {
                        Text(
                            session?.anchorTitle?.ifBlank { anchor?.title ?: "从这里开始" } ?: anchor?.title ?: "从这里开始",
                            style = MaterialTheme.typography.headlineMedium,
                            color = t.foreground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "这是一条独立故事线。原著正文保持不变，你只需要决定自己说什么、做什么。",
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
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
                            shape = LanghuanShape.panel,
                            color = t.primary,
                            contentColor = t.primaryForeground,
                        ) {
                            Text(
                                turn.player,
                                Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                color = t.primaryForeground,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item(key = "story-${turn.id}") {
                    Column(Modifier.fillMaxWidth()) {
                        Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground, fontWeight = FontWeight.Medium)
                        Text(
                            turn.narration,
                            Modifier.padding(top = 6.dp),
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            color = t.foreground,
                        )
                    }
                }
            }

            if (state.busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = t.foreground)
                        Text("世界正在回应……", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
            }
            state.error?.let { error ->
                item {
                    ShadcnCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, Modifier.weight(1f), color = t.destructive, style = MaterialTheme.typography.bodySmall)
                            ShadcnIconButton(Icons.Rounded.Close, "关闭", vm::clearError)
                        }
                    }
                }
            }
        }
    }

    if (showMenu) {
        ModalBottomSheet(onDismissRequest = { showMenu = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Text("故事", style = MaterialTheme.typography.titleLarge, color = t.foreground, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                ShadcnCard(Modifier.fillMaxWidth()) {
                    ShadcnMenuRow(Icons.Rounded.ChevronRight, "故事分支", "切换或新建独立故事线", {
                        showMenu = false
                        showBranches = true
                    })
                    ShadcnSeparator()
                    ShadcnMenuRow(Icons.Rounded.Place, "世界状态", "查看当前位置与故事时间", {
                        showMenu = false
                        showWorld = true
                    })
                    if (!aiReady) {
                        ShadcnSeparator()
                        ShadcnMenuRow(Icons.Rounded.Settings, "AI 设置", "配置故事模式使用的模型", {
                            showMenu = false
                            onAiSetup()
                        })
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showBranches) {
        ModalBottomSheet(onDismissRequest = { showBranches = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("故事分支", style = MaterialTheme.typography.titleLarge, color = t.foreground, modifier = Modifier.weight(1f))
                    ShadcnIconButton(Icons.Rounded.Close, "关闭", { showBranches = false })
                }
                ShadcnButton(
                    text = "从当前章节新建分支",
                    onClick = {
                        vm.newBranch(anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
                        showBranches = false
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
                    leadingIcon = Icons.Rounded.Add,
                )
                ShadcnCard(Modifier.fillMaxWidth()) {
                    state.sessions.sortedByDescending { it.updatedAt }.forEachIndexed { index, item ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !state.busy) {
                                vm.selectSession(item.id)
                                showBranches = false
                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title.ifBlank { "故事分支" },
                                    color = t.foreground,
                                    fontWeight = if (item.id == session?.id) FontWeight.SemiBold else FontWeight.Medium,
                                )
                                Text("第 ${item.anchorChapter} 章 · ${item.turns.size} 轮", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = t.mutedForeground)
                        }
                        if (index < state.sessions.lastIndex) ShadcnSeparator()
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (showWorld) {
        ModalBottomSheet(onDismissRequest = { showWorld = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("世界状态", style = MaterialTheme.typography.titleLarge, color = t.foreground, modifier = Modifier.weight(1f))
                    ShadcnIconButton(Icons.Rounded.Close, "关闭", { showWorld = false })
                }
                ShadcnCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    StoryCoreStateRow(Icons.Rounded.Place, "地点", world?.location?.ifBlank { "未知" } ?: "未知")
                    ShadcnSeparator()
                    StoryCoreStateRow(Icons.Rounded.Schedule, "时间", world?.time?.ifBlank { "未记录" } ?: "未记录")
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun StoryCoreStateRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = t.mutedForeground)
        Text(label, Modifier.padding(start = 11.dp).width(52.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = t.foreground)
    }
}
