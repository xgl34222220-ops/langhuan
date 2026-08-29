package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.OutlineLevel

private enum class BookWorkspaceTab(val label: String) {
    OVERVIEW("概览"),
    CHAPTERS("章节"),
    PLAN("规划"),
    MEMORY("记忆"),
}

@Composable
fun ShelfV3(
    state: LibraryExperienceState,
    aiReady: Boolean,
    aiLabel: String,
    onOpenBook: (String) -> Unit,
    onCreate: () -> Unit,
    onReference: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("长篇小说 AI 创作工作台", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onRunCenter) { Icon(Icons.Rounded.TaskAlt, "后台任务") }
                IconButton(onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (aiReady) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            null,
                            tint = if (aiReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(if (aiReady) aiLabel.ifBlank { "AI 已连接" } else "还没有可用 AI", fontWeight = FontWeight.Bold)
                            Text(
                                if (aiReady) "可以直接建书、规划和写作" else "先配置中转站或官方 API，再开始 AI 创作",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 新建小说")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onReference,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.LibraryBooks, null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入参考小说 / 资料")
                    }
                }
            }
        }

        item {
            Text("我的作品", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (state.stories.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MenuBook, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("还没有小说", fontWeight = FontWeight.Bold)
                        Text("从上面的 AI 新建小说开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(state.stories, key = { it.id }) { book ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 1.dp,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(78.dp).height(112.dp))
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(book.genre, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                book.premise,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("第 ${book.currentChapter} 章 · ${book.currentWords} 字", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderFirstLibraryV3(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenAgent: () -> Unit,
    onOpenIntelligence: () -> Unit,
    onOpenRunCenter: () -> Unit,
    onOpenAiSetup: () -> Unit,
    onOpenCoverStudio: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook

    if (state.readingChapter != null && book != null) {
        ReaderV3(
            state = state,
            book = book,
            onBack = viewModel::closeReader,
            onOpenChapter = viewModel::openReader,
            onWrite = { onEnterWriting(book.id) },
        )
        return
    }

    if (book == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var tab by remember(book.id) { mutableStateOf(BookWorkspaceTab.OVERVIEW) }
    var editing by remember(book.id) { mutableStateOf(false) }
    var title by remember(book.title) { mutableStateOf(book.title) }
    var premise by remember(book.premise) { mutableStateOf(book.premise) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBackToShelf) { Icon(Icons.Rounded.ArrowBack, "返回书架") }
                Column(Modifier.weight(1f)) {
                    Text("作品工作台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("当前：${book.title}", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑作品资料") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text("AI 服务设置") },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                            onClick = { menu = false; onOpenAiSetup() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除作品", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                }
            }
        }

        item {
            Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row {
                        Box(Modifier.clickable { onOpenCoverStudio(book.id) }) {
                            CoverPreviewV3(book.coverPath, book.title, Modifier.width(118.dp).height(170.dp))
                        }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(book.genre, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(book.premise, maxLines = 5, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { book.currentWords.toFloat() / book.targetWords.coerceAtLeast(1).toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(5.dp))
                            Text("${book.currentWords} / ${book.targetWords} 字", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onEnterWriting(book.id) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.EditNote, null)
                        Spacer(Modifier.width(8.dp))
                        Text("继续写作")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val chapter = state.chapters.firstOrNull { it.content.isNotBlank() } ?: state.chapters.firstOrNull()
                            chapter?.let { viewModel.openReader(it.chapterNumber) }
                        },
                        enabled = state.chapters.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.MenuBook, null)
                        Spacer(Modifier.width(8.dp))
                        Text("阅读正文")
                    }
                }
            }
        }

        item {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp, divider = {}) {
                BookWorkspaceTab.entries.forEach { item ->
                    Tab(
                        selected = item == tab,
                        onClick = { tab = item },
                        text = { Text(item.label) },
                    )
                }
            }
        }

        when (tab) {
            BookWorkspaceTab.OVERVIEW -> {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("作品简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(book.premise, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("AI 作品包装", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("生成结果先预览，确认后再采用；封面不会再覆盖旧版本。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = viewModel::generateIdentity,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("AI 书名/简介") }
                                OutlinedButton(
                                    onClick = { onOpenCoverStudio(book.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("封面工作室") }
                            }
                            state.identitySuggestion?.let { suggestion ->
                                Spacer(Modifier.height(14.dp))
                                Text(suggestion.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(suggestion.premise, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = viewModel::applyIdentitySuggestion,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                ) { Text("采用这套书名和简介") }
                            }
                        }
                    }
                }
                item {
                    Text("工作台工具", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onOpenAgent, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.Psychology, null)
                            Spacer(Modifier.width(4.dp))
                            Text("AI 助手")
                        }
                        FilledTonalButton(onOpenIntelligence, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.Insights, null)
                            Spacer(Modifier.width(4.dp))
                            Text("长篇监控")
                        }
                        FilledTonalButton(onOpenRunCenter, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.TaskAlt, null)
                            Spacer(Modifier.width(4.dp))
                            Text("任务")
                        }
                    }
                }
            }

            BookWorkspaceTab.CHAPTERS -> {
                item {
                    Text("目录 · ${state.chapters.size} 章", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(state.chapters.sortedBy { it.chapterNumber }, key = { it.id }) { chapter ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.openReader(chapter.chapterNumber) },
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${chapter.chapterNumber}", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(chapter.title.ifBlank { "第${chapter.chapterNumber}章" }, fontWeight = FontWeight.Bold)
                                Text(
                                    if (chapter.content.isBlank()) "暂无正文" else "${chapter.content.length} 字 · v${chapter.version}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                }
            }

            BookWorkspaceTab.PLAN -> {
                val outline = studioState.snapshot.outline.ifEmpty { studioState.snapshot.activeOutline }
                item {
                    Text("三级大纲", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("总纲 → 卷纲 → 章纲。写作时只把当前链路送给 AI。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (outline.isEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                            Text("当前还没有可显示的大纲。进入写作流后可继续规划章节。", Modifier.fillMaxWidth().padding(18.dp))
                        }
                    }
                } else {
                    items(outline.sortedWith(compareBy({ it.level.ordinal }, { it.order })), key = { it.id }) { node ->
                        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
                            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                Text(
                                    when (node.level) {
                                        OutlineLevel.MASTER -> "总纲"
                                        OutlineLevel.VOLUME -> "卷纲 ${node.order}"
                                        OutlineLevel.CHAPTER -> "章纲 ${node.order}"
                                    },
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(node.title, fontWeight = FontWeight.Bold)
                                if (node.objective.isNotBlank()) Text(node.objective, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            BookWorkspaceTab.MEMORY -> {
                val snapshot = studioState.snapshot
                item {
                    Text("长期记忆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("这里只展示已经进入 Canon 的正式状态，不把候选事实混进来。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("人物状态 · ${snapshot.characters.size}", fontWeight = FontWeight.Bold)
                            snapshot.characters.take(6).forEach { character ->
                                Text("${character.name} · ${character.location} · ${character.goal}", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("时间线 · ${snapshot.recentTimeline.size}", fontWeight = FontWeight.Bold)
                            snapshot.recentTimeline.takeLast(6).forEach { event ->
                                Text("第${event.chapter}章 · ${event.summary}", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("伏笔 · ${snapshot.relevantForeshadowing.size}", fontWeight = FontWeight.Bold)
                            snapshot.relevantForeshadowing.take(6).forEach { item ->
                                Text("${item.title} · ${item.status}", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("编辑作品资料") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("书名") }, singleLine = true)
                    OutlinedTextField(premise, { premise = it }, label = { Text("简介") }, minLines = 4)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveMetadata(title, premise); editing = false },
                    enabled = title.isNotBlank() && premise.isNotBlank() && !state.isBusy,
                ) { Text("保存") }
            },
            dismissButton = { TextButton({ editing = false }) { Text("取消") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除《${book.title}》？") },
            text = { Text("会删除章节、版本、记忆和封面，此操作不能撤销。") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; viewModel.deleteBook(book.id); onBackToShelf() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除作品") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ReaderV3(
    state: LibraryExperienceState,
    book: ReaderBookUi,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onWrite: () -> Unit,
) {
    val chapter = state.readingChapter ?: return
    val chapters = state.chapters.sortedBy { it.chapterNumber }
    val index = chapters.indexOfFirst { it.id == chapter.id }
    val previous = chapters.getOrNull(index - 1)
    val next = chapters.getOrNull(index + 1)
    var fontSize by remember { mutableFloatStateOf(19f) }
    val scroll = rememberScrollState()

    LaunchedEffect(chapter.id) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回目录") }
            Column(Modifier.weight(1f)) {
                Text("《${book.title}》 · 第${chapter.chapterNumber}章", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(chapter.title.ifBlank { "第${chapter.chapterNumber}章" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onWrite) { Icon(Icons.Rounded.EditNote, "继续创作") }
        }
        HorizontalDivider()
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Text(chapter.title.ifBlank { "第${chapter.chapterNumber}章" }, fontSize = (fontSize + 7).sp, lineHeight = (fontSize + 14).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Text(
                chapter.content.ifBlank { "这一章还没有正文。点击右上角写作按钮继续创作。" },
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.9f).sp,
            )
            Spacer(Modifier.height(42.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { previous?.let { onOpenChapter(it.chapterNumber) } },
                    enabled = previous != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(17.dp),
                ) { Text("上一章") }
                Button(
                    onClick = { next?.let { onOpenChapter(it.chapterNumber) } },
                    enabled = next != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(17.dp),
                ) { Text("下一章") }
            }
            Spacer(Modifier.height(80.dp))
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aa", fontSize = 15.sp)
                Slider(fontSize, { fontSize = it }, valueRange = 15f..26f, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
                Text("Aa", fontSize = 23.sp)
            }
        }
    }
}
