package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

private enum class StoryManagementPage(val label: String) {
    ROLE("角色"),
    WORLD("世界"),
    BRANCHES("分支"),
    DRAFT("章节草稿"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryManagementExperience(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
    onClose: () -> Unit,
) {
    val vm: StoryPlayV3ViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val t = LocalLanghuanUiTokens.current
    val anchor = libraryState.readingChapter
        ?: libraryState.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
        ?: libraryState.chapters.lastOrNull()

    var page by remember { mutableStateOf(StoryManagementPage.ROLE) }
    var showLegacy by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, anchor?.chapterNumber) {
        vm.open(book.id, anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty())
    }

    if (showLegacy) {
        Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(book, libraryState, aiReady, onAiSetup, onAdopted)
            Surface(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                shape = RoundedCornerShape(99.dp),
                color = t.card.copy(alpha = .96f),
                border = BorderStroke(1.dp, t.border),
                shadowElevation = 4.dp,
            ) {
                TextButton(onClick = { showLegacy = false }) {
                    Icon(Icons.Rounded.ArrowBack, null)
                    Spacer(Modifier.width(6.dp))
                    Text("返回故事设置")
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = t.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("故事设置", color = t.foreground, fontWeight = FontWeight.SemiBold)
                        Text(state.active?.title ?: book.title, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, "返回故事", tint = t.foreground) } },
                actions = {
                    TextButton(onClick = { showLegacy = true }) { Text("完整工具") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.background),
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StoryManagementPage.entries.forEach { item ->
                    FilterChip(
                        selected = page == item,
                        onClick = { page = item },
                        label = { Text(item.label) },
                    )
                }
            }
            HorizontalDivider(color = t.border)
            when (page) {
                StoryManagementPage.ROLE -> StoryRoleManagement(vm, state)
                StoryManagementPage.WORLD -> StoryWorldManagement(vm, state)
                StoryManagementPage.BRANCHES -> StoryBranchManagement(vm, state, anchor)
                StoryManagementPage.DRAFT -> StoryDraftManagement(vm, state, book, aiReady, onAiSetup, onOpenLegacy = { showLegacy = true })
            }
        }
    }
}

@Composable
private fun StoryRoleManagement(vm: StoryPlayV3ViewModel, state: StoryPlayV3UiState) {
    val t = LocalLanghuanUiTokens.current
    val profile = state.active?.playerProfile ?: StoryPlayerProfile()
    var name by remember(profile) { mutableStateOf(profile.name) }
    var identity by remember(profile) { mutableStateOf(profile.identity) }
    var traits by remember(profile) { mutableStateOf(profile.traits) }
    var known by remember(profile) { mutableStateOf(profile.knownFacts) }
    var forbidden by remember(profile) { mutableStateOf(profile.forbiddenKnowledge) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementIntro(
            icon = Icons.Rounded.Person,
            title = "我是谁",
            description = "这里定义玩家在故事里的身份和知识边界。AI 不能把“禁止提前知道”的内容直接塞给角色。",
        )
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("姓名") }, singleLine = true)
        OutlinedTextField(identity, { identity = it }, Modifier.fillMaxWidth(), label = { Text("身份") }, placeholder = { Text("例如：原著角色 / 原创调查员 / 普通路人") })
        OutlinedTextField(traits, { traits = it }, Modifier.fillMaxWidth(), label = { Text("性格与特征") }, minLines = 2)
        OutlinedTextField(known, { known = it }, Modifier.fillMaxWidth(), label = { Text("当前已经知道") }, minLines = 3)
        OutlinedTextField(forbidden, { forbidden = it }, Modifier.fillMaxWidth(), label = { Text("禁止提前知道") }, minLines = 3)
        Button(
            onClick = { vm.updateProfile(StoryPlayerProfile(name.trim(), identity.trim(), traits.trim(), known.trim(), forbidden.trim())) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.Save, null)
            Spacer(Modifier.width(7.dp))
            Text("保存角色身份")
        }
        state.notice?.let { Text(it, color = t.success, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun StoryWorldManagement(vm: StoryPlayV3ViewModel, state: StoryPlayV3UiState) {
    val t = LocalLanghuanUiTokens.current
    val world = state.runtime?.world ?: StoryWorldStateV3()
    var location by remember(world) { mutableStateOf(world.location) }
    var time by remember(world) { mutableStateOf(world.time) }
    var atmosphere by remember(world) { mutableStateOf(world.atmosphere) }
    var situation by remember(world) { mutableStateOf(world.situation) }
    var notes by remember(world) { mutableStateOf(world.notes) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementIntro(
            icon = Icons.Rounded.Public,
            title = "此刻的世界",
            description = "世界状态属于当前故事分支。修改这里只影响互动分支，不会静默改写原小说正文。",
        )
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("地点") }, singleLine = true)
        OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth(), label = { Text("时间") }, singleLine = true)
        OutlinedTextField(atmosphere, { atmosphere = it }, Modifier.fillMaxWidth(), label = { Text("环境 / 氛围") }, minLines = 2)
        OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), label = { Text("当前局势") }, minLines = 3)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("导演备注") }, minLines = 3)
        Button(
            onClick = { vm.updateWorld(StoryWorldStateV3(location.trim(), time.trim(), atmosphere.trim(), situation.trim(), notes.trim())) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.Save, null)
            Spacer(Modifier.width(7.dp))
            Text("保存世界状态")
        }
        Surface(shape = RoundedCornerShape(t.radiusMd), color = t.muted, border = BorderStroke(1.dp, t.border)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StoryStat("知识", state.runtime?.knowledge?.size ?: 0)
                StoryStat("关系", state.runtime?.relationships?.size ?: 0)
                StoryStat("变量", state.active?.variables?.size ?: 0)
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun StoryBranchManagement(vm: StoryPlayV3ViewModel, state: StoryPlayV3UiState, anchor: com.xiguli.langhuan.domain.ChapterDraft?) {
    val t = LocalLanghuanUiTokens.current
    var renameTarget by remember { mutableStateOf<StoryPlaySession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<StoryPlaySession?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            StoryManagementIntro(
                icon = Icons.Rounded.ForkRight,
                title = "故事分支",
                description = "复制、回溯和新建分支都彼此独立。你可以大胆尝试，不会覆盖其它路线。",
            )
        }
        item {
            Button(
                onClick = { vm.newBranch(anchor?.chapterNumber ?: 1, anchor?.title.orEmpty(), anchor?.content.orEmpty()) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(7.dp))
                Text("从当前章节新建分支")
            }
        }
        items(state.sessions.sortedByDescending { it.updatedAt }, key = { it.id }) { session ->
            val selected = session.id == state.active?.id
            Surface(
                shape = RoundedCornerShape(t.radiusLg),
                color = if (selected) t.warmSurface else t.card,
                border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .32f) else t.border),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(session.title, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("第 ${session.anchorChapter} 章 · ${session.turns.size} 轮互动", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        if (selected) Icon(Icons.Rounded.CheckCircle, "当前", tint = t.accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { vm.selectSession(session.id) }, enabled = !selected && !state.busy) { Text(if (selected) "正在使用" else "切换") }
                        TextButton(onClick = { renameTarget = session; renameText = session.title }, enabled = !state.busy) { Text("重命名") }
                        TextButton(onClick = { vm.duplicateBranch(session.id) }, enabled = !state.busy) { Text("复制") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { deleteTarget = session }, enabled = state.sessions.size > 1 && !state.busy) { Text("删除", color = t.destructive) }
                    }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(20.dp)) }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名分支") },
            text = { OutlinedTextField(renameText, { renameText = it }, Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { Button(onClick = { vm.renameBranch(target.id, renameText); renameTarget = null }, enabled = renameText.isNotBlank()) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个故事分支？") },
            text = { Text("只删除「${target.title}」的互动路线，不会删除原小说，也不会影响其它分支。") },
            confirmButton = { Button(onClick = { vm.deleteBranch(target.id); deleteTarget = null }, colors = ButtonDefaults.buttonColors(containerColor = t.destructive)) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun StoryDraftManagement(
    vm: StoryPlayV3ViewModel,
    state: StoryPlayV3UiState,
    book: ReaderBookUi,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onOpenLegacy: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val session = state.active
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StoryManagementIntro(
            icon = Icons.Rounded.AutoStories,
            title = "把演绎整理成小说章节",
            description = "先从当前分支生成独立候选草稿。不会自动覆盖原著正文；真正采用仍然经过原有确认链。",
        )
        Surface(shape = RoundedCornerShape(t.radiusLg), color = t.card, border = BorderStroke(1.dp, t.border)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("${session?.turns?.size ?: 0} 轮互动", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("锚点：第 ${session?.anchorChapter ?: 1} 章 · ${session?.anchorTitle.orEmpty().ifBlank { "当前章节" }}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }
        if (!aiReady) {
            OutlinedButton(onClick = onAiSetup, modifier = Modifier.fillMaxWidth()) { Text("先配置 AI") }
        } else {
            Button(
                onClick = { vm.generateChapterDraft(book) },
                enabled = !state.busy && session?.turns?.isNotEmpty() == true,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(7.dp))
                Text(if (state.busy) "正在整理" else "生成章节候选草稿")
            }
        }
        session?.chapterDraftCandidate?.takeIf { it.isNotBlank() }?.let { candidate ->
            Text("候选草稿", fontWeight = FontWeight.SemiBold, color = t.foreground)
            Surface(shape = RoundedCornerShape(t.radiusLg), color = t.card, border = BorderStroke(1.dp, t.border)) {
                SelectionContainer {
                    Text(candidate, Modifier.fillMaxWidth().padding(16.dp), color = t.foreground, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight)
                }
            }
            OutlinedButton(onClick = onOpenLegacy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FactCheck, null)
                Spacer(Modifier.width(7.dp))
                Text("进入完整工具确认采用")
            }
        }
        Text("NPC 记忆、原著角色快照、空间/感知与候选草稿采用仍保留在「完整工具」，后续继续逐项迁入。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable
private fun StoryManagementIntro(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(t.radiusMd), color = t.warmSurface) {
            Icon(icon, null, Modifier.padding(9.dp).size(20.dp), tint = t.accent)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
            Text(description, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}

@Composable
private fun StoryStat(label: String, value: Int) {
    val t = LocalLanghuanUiTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), fontWeight = FontWeight.SemiBold, color = t.foreground)
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
    }
}
