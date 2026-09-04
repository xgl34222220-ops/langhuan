package com.xiguli.langhuan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class TavernHubScreenV2 {
    LIBRARY,
    DETAIL,
    CHAT,
    STORY,
}

@Composable
fun TavernCharacterHubV2(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val vm: TavernCharacterLibraryViewModelV2 = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var screen by rememberSaveable(book.id) { mutableStateOf(TavernHubScreenV2.LIBRARY) }
    var selectedCardId by rememberSaveable(book.id) { mutableStateOf<String?>(null) }
    var search by rememberSaveable(book.id) { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importChat(uri, aiReady)
    }

    LaunchedEffect(book.id) { vm.open(book.id) }
    LaunchedEffect(state.notice, state.error) {
        val message = state.error ?: state.notice
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            vm.clearFeedback()
        }
    }

    val selected = state.cards.firstOrNull { it.id == selectedCardId }
    LaunchedEffect(selectedCardId, state.cards) {
        if (selectedCardId != null && selected == null) {
            selectedCardId = null
            screen = TavernHubScreenV2.LIBRARY
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            TavernHubScreenV2.LIBRARY -> TavernCharacterLibraryScreenV2(
                book = book,
                state = state,
                search = search,
                onSearch = { search = it },
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "text/plain",
                            "text/markdown",
                            "text/html",
                            "application/json",
                            "application/octet-stream",
                        )
                    )
                },
                onOpenCharacter = { card ->
                    selectedCardId = card.id
                    screen = TavernHubScreenV2.DETAIL
                },
                onOpenStory = { screen = TavernHubScreenV2.STORY },
            )

            TavernHubScreenV2.DETAIL -> {
                if (selected != null) {
                    TavernCharacterDetailScreenV2(
                        card = selected,
                        messageCount = state.chats[selected.id].orEmpty().size,
                        onBack = { screen = TavernHubScreenV2.LIBRARY },
                        onStartChat = {
                            if (aiReady) screen = TavernHubScreenV2.CHAT else onAiSetup()
                        },
                        onDelete = {
                            vm.deleteCard(selected.id)
                            selectedCardId = null
                            screen = TavernHubScreenV2.LIBRARY
                        },
                    )
                }
            }

            TavernHubScreenV2.CHAT -> {
                if (selected != null) {
                    TavernDirectCharacterChatV2(
                        card = selected,
                        messages = state.chats[selected.id].orEmpty(),
                        busy = state.chatting,
                        onBack = { screen = TavernHubScreenV2.DETAIL },
                        onSend = { text ->
                            if (aiReady) vm.sendMessage(selected.id, text) else onAiSetup()
                        },
                        onClear = { vm.clearChat(selected.id) },
                    )
                }
            }

            TavernHubScreenV2.STORY -> Box(Modifier.fillMaxSize()) {
                StoryCoreExperience(
                    book = book,
                    libraryState = libraryState,
                    aiReady = aiReady,
                    onAiSetup = onAiSetup,
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 10.dp, end = 14.dp),
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                ) {
                    IconButton(onClick = { screen = TavernHubScreenV2.LIBRARY }) {
                        Icon(Icons.Rounded.PeopleAlt, "返回人物库")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 48.dp, vertical = 10.dp),
        )
    }

    if (state.preview.isNotEmpty()) {
        TavernCharacterPreviewDialogV2(
            sourceName = state.sourceName,
            cards = state.preview,
            onDismiss = vm::discardPreview,
            onSave = vm::savePreview,
        )
    }
}

@Composable
private fun TavernCharacterLibraryScreenV2(
    book: ReaderBookUi,
    state: TavernCharacterLibraryUiStateV2,
    search: String,
    onSearch: (String) -> Unit,
    onImport: () -> Unit,
    onOpenCharacter: (TavernCharacterCardV2) -> Unit,
    onOpenStory: () -> Unit,
) {
    val query = search.trim().lowercase()
    val visibleCards = remember(state.cards, query) {
        if (query.isBlank()) state.cards else state.cards.filter { card ->
            buildString {
                append(card.name).append(' ')
                append(card.aliases.joinToString(" ")).append(' ')
                append(card.identity).append(' ')
                append(card.personality).append(' ')
                append(card.sourceTitle)
            }.lowercase().contains(query)
        }
    }
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("酒馆", fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            "人物、记忆与聊天",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    FilledTonalIconButton(onClick = onOpenStory) {
                        Icon(Icons.Rounded.AutoStories, "故事分支")
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("搜索人物、身份、来源") },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, "清空") }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImport,
                        enabled = !state.importing,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (state.importing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.FileOpen, null)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(if (state.importing) "提取中…" else "导入聊天")
                    }
                    FilledTonalButton(
                        onClick = onOpenStory,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Icon(Icons.Rounded.TheaterComedy, null)
                        Spacer(Modifier.width(7.dp))
                        Text("故事分支")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("来自《${book.title}》", fontWeight = FontWeight.SemiBold)
                            Text(
                                "聊天人物与这本书的酒馆分支分开保存",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("${state.cards.size} 人", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("我的人物", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (visibleCards.isEmpty()) "导入聊天后，人物会先提取预览，再保存到这里" else "点击人物查看完整角色卡并开始聊天",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (visibleCards.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyTavernCharactersV2(onImport)
            }
        } else {
            items(visibleCards, key = { it.id }) { card ->
                TavernCharacterGridCardV2(card = card, onClick = { onOpenCharacter(card) })
            }
        }
    }
}

@Composable
private fun EmptyTavernCharactersV2(onImport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Groups, null, modifier = Modifier.padding(18.dp).size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text("还没有人物", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "导入 TXT / Markdown / JSON / HTML 聊天记录，琅嬛会识别多人并建立角色卡。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = onImport, modifier = Modifier.padding(top = 8.dp)) { Text("选择聊天记录") }
        }
    }
}

@Composable
private fun TavernCharacterGridCardV2(card: TavernCharacterCardV2, onClick: () -> Unit) {
    val seed = card.name.hashCode()
    val start = if (seed and 1 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val end = if (seed and 2 == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        modifier = Modifier.fillMaxWidth().height(222.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 1.dp,
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(start, end)))) {
            Text(
                text = card.name.take(1).ifBlank { "人" },
                modifier = Modifier.align(Alignment.Center),
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .13f),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
            ) {
                Text(card.name, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    card.identity.ifBlank { card.personality.ifBlank { "来自聊天导入" } },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.sourceTitle.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .66f)) {
                        Text(card.sourceTitle, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TavernCharacterDetailScreenV2(
    card: TavernCharacterCardV2,
    messageCount: Int,
    onBack: () -> Unit,
    onStartChat: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(top = 60.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
            Text("人物详情", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(230.dp),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        card.name.take(1).ifBlank { "人" },
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 92.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .22f),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(card.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        if (card.aliases.isNotEmpty()) Text(card.aliases.joinToString(" · "), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(card.sourceTitle.ifBlank { "聊天导入" }, maxLines = 1) })
                if (messageCount > 0) AssistChip(onClick = {}, label = { Text("$messageCount 条聊天") })
                if (card.knowledgeCutoffChapter > 0) AssistChip(onClick = {}, label = { Text("截至 ${card.knowledgeCutoffChapter} 章") })
            }
            TavernDetailBlockV2("身份", card.identity)
            TavernDetailBlockV2("性格", card.personality)
            TavernDetailBlockV2("说话方式", card.speechStyle)
            TavernDetailBlockV2("外貌", card.appearance)
            TavernDetailBlockV2("与你的关系", card.relationshipToUser)
            TavernDetailListV2("人物关系", card.relationships)
            TavernDetailListV2("经历", card.history)
            TavernDetailListV2("喜好", card.likes)
            TavernDetailListV2("厌恶与雷区", card.dislikes + card.boundaries)
            TavernDetailListV2("世界信息", card.worldFacts)
            TavernDetailListV2("当前记忆", card.currentMemory)
            TavernDetailListV2("说话样例", card.dialogueExamples)
            Spacer(Modifier.height(24.dp))
        }
        Surface(shadowElevation = 6.dp) {
            Button(
                onClick = onStartChat,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Chat, null)
                Spacer(Modifier.width(8.dp))
                Text(if (messageCount > 0) "继续聊天" else "开始聊天", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${card.name}？") },
            text = { Text("人物卡和这个人物的聊天记录都会删除。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TavernDetailBlockV2(title: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(value, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
    }
}

@Composable
private fun TavernDetailListV2(title: String, values: List<String>) {
    val clean = values.filter { it.isNotBlank() }.distinct()
    if (clean.isEmpty()) return
    Column(Modifier.padding(top = 18.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        clean.forEach { value ->
            Row(Modifier.padding(top = 7.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Text(value, Modifier.padding(start = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun TavernDirectCharacterChatV2(
    card: TavernCharacterCardV2,
    messages: List<TavernCharacterChatMessageV2>,
    busy: Boolean,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by rememberSaveable(card.id) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(top = 60.dp).imePadding()) {
        Surface(tonalElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回人物") }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(card.name.take(1), fontWeight = FontWeight.Black) }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(card.name, fontWeight = FontWeight.Bold)
                    Text(card.relationshipToUser.ifBlank { "角色聊天" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "清空聊天") }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(Modifier.fillParentMaxHeight(.72f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(card.name.take(1), Modifier.padding(horizontal = 23.dp, vertical = 16.dp), fontSize = 30.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("和 ${card.name} 说点什么", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("人物卡、说话样例与既有聊天会一起进入上下文", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                val mine = message.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        modifier = Modifier.widthIn(max = 310.dp),
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (mine) 20.dp else 6.dp,
                            bottomEnd = if (mine) 6.dp else 20.dp,
                        ),
                        color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            message.text,
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
            if (busy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("${card.name} 正在回复…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Surface(shadowElevation = 5.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    placeholder = { Text("和 ${card.name} 聊天…") },
                    maxLines = 5,
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotBlank()) {
                            onSend(text)
                            input = ""
                        }
                    },
                    enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Rounded.Send, "发送") }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空聊天？") },
            text = { Text("人物卡会保留，只删除你和 ${card.name} 的聊天记录。") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TavernCharacterPreviewDialogV2(
    sourceName: String,
    cards: List<TavernCharacterCardV2>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(cards) { mutableStateOf(cards.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("提取到 ${cards.size} 个人物")
                Text(sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 470.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    val checked = card.id in selected
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selected = if (checked) selected - card.id else selected + card.id
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(card.name.take(1), fontWeight = FontWeight.Black) }
                            }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(card.name, fontWeight = FontWeight.Bold)
                                Text(
                                    card.identity.ifBlank { card.personality.ifBlank { "已从聊天中识别" } },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (card.dialogueExamples.isNotEmpty()) {
                                    Text(
                                        "“${card.dialogueExamples.first()}”",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected) }, enabled = selected.isNotEmpty()) {
                Icon(Icons.Rounded.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("保存 ${selected.size} 个")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
