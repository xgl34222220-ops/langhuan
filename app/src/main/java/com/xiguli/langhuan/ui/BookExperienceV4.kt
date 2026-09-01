package com.xiguli.langhuan.ui

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class BookTabV4(val label: String) {
    DIRECTORY("目录"),
    READER("阅读"),
    WRITING("创作"),
    STORY("故事"),
    SETTING("设定"),
}

@Composable
fun BookExperienceV4(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAgent: () -> Unit,
    onOpenIntelligence: () -> Unit,
    onOpenRunCenter: () -> Unit,
    onOpenAiSetup: () -> Unit,
    onOpenCoverStudio: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook ?: return
    var tab by remember(book.id) { mutableStateOf(BookTabV4.DIRECTORY) }
    var showOriginalCanon by remember(book.id) { mutableStateOf(false) }

    LaunchedEffect(tab, book.id, state.readingChapter?.id) {
        if (tab == BookTabV4.READER && state.readingChapter == null) {
            val preferred = state.chapters.firstOrNull { it.chapterNumber == book.currentChapter && it.content.isNotBlank() }
                ?: state.chapters.firstOrNull { it.content.isNotBlank() }
                ?: state.chapters.firstOrNull()
            preferred?.let { viewModel.openReader(it.chapterNumber) }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onBackToShelf) { Icon(Icons.Rounded.ArrowBack, "返回书架") }
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${book.genre} · ${state.chapters.size} 章 · ${book.currentWords} 字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton({ onOpenCoverStudio(book.id) }) { Icon(Icons.Rounded.Image, "封面") }
                    IconButton(onOpenAiSetup) { Icon(Icons.Rounded.MoreVert, "更多") }
                }
                ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 10.dp, divider = {}) {
                    BookTabV4.entries.forEach { item ->
                        Tab(
                            selected = tab == item,
                            onClick = { tab = item },
                            text = { Text(item.label, fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Normal) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                BookTabV4.DIRECTORY -> DirectoryTabV4(
                    book = book,
                    chapters = state.chapters,
                    onRead = { number -> viewModel.openReader(number); tab = BookTabV4.READER },
                    onEdit = { number -> onOpenEditor(book.id, number) },
                    onWriting = { onEnterWriting(book.id) },
                )
                BookTabV4.READER -> ReaderTabV4(
                    state = state,
                    onPrevious = viewModel::readPrevious,
                    onNext = viewModel::readNext,
                    onEdit = { number -> onOpenEditor(book.id, number) },
                    onEnterStory = { tab = BookTabV4.STORY },
                )
                BookTabV4.WRITING -> WritingTabV4(
                    book = book,
                    state = state,
                    onWriting = { onEnterWriting(book.id) },
                    onEdit = { number -> onOpenEditor(book.id, number) },
                    onAgent = onOpenAgent,
                    onRunCenter = onOpenRunCenter,
                )
                BookTabV4.STORY -> StoryPlayPanelV12(
                    book = book,
                    libraryState = state,
                    aiReady = studioState.provider.ready,
                    onAiSetup = onOpenAiSetup,
                    onAdopted = { viewModel.openBook(book.id) },
                )
                BookTabV4.SETTING -> SettingTabV4(
                    book = book,
                    aiReady = studioState.provider.ready,
                    aiLabel = studioState.provider.activeProviderLabel,
                    onIntelligence = onOpenIntelligence,
                    onExtractCanon = { showOriginalCanon = true },
                    onAgent = onOpenAgent,
                    onAiSetup = onOpenAiSetup,
                    onCover = { onOpenCoverStudio(book.id) },
                )
            }
        }
    }

    if (showOriginalCanon) {
        OriginalCanonExtractionDialogV1(
            book = book,
            chapters = state.chapters,
            aiReady = studioState.provider.ready,
            onDismiss = { showOriginalCanon = false },
            onApplied = { viewModel.openBook(book.id) },
        )
    }
}

@Composable
private fun DirectoryTabV4(
    book: ReaderBookUi,
    chapters: List<com.xiguli.langhuan.domain.ChapterDraft>,
    onRead: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onWriting: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(shape = RoundedCornerShape(26.dp), tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverPreviewV3(book.coverPath, book.title, Modifier.width(78.dp).height(112.dp))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(book.premise, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onWriting, shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Rounded.EditNote, null)
                            Spacer(Modifier.width(6.dp))
                            Text("继续创作")
                        }
                    }
                }
            }
        }
        item {
            Text("目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("点章节直接阅读；铅笔进入该章编辑。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(chapters.sortedBy { it.chapterNumber }, key = { it.id }) { chapter ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onRead(chapter.chapterNumber) },
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 1.dp,
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${chapter.chapterNumber}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (chapter.content.isBlank()) "暂无正文" else "${chapter.content.length} 字 · v${chapter.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton({ onEdit(chapter.chapterNumber) }) { Icon(Icons.Rounded.EditNote, "编辑本章") }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReaderTabV4(
    state: LibraryExperienceState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: (Int) -> Unit,
    onEnterStory: () -> Unit,
) {
    val chapter = state.readingChapter
    if (chapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("这本书还没有可阅读的章节") }
        return
    }
    val sorted = state.chapters.sortedBy { it.chapterNumber }
    val index = sorted.indexOfFirst { it.id == chapter.id }
    var fontSize by remember { mutableFloatStateOf(19f) }
    val scroll = rememberScrollState()
    LaunchedEffect(chapter.id) { scroll.scrollTo(0) }

    Column(Modifier.fillMaxSize()) {
        SelectionContainer(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 26.dp)) {
                Text("第 ${chapter.chapterNumber} 章", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(chapter.title.ifBlank { "未命名章节" }, fontSize = (fontSize + 7).sp, lineHeight = (fontSize + 13).sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(22.dp))
                Text(
                    chapter.content.ifBlank { "这一章还没有正文，可以切到“创作”或点下方编辑。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.9f).sp,
                )
                Spacer(Modifier.height(30.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onPrevious, Modifier.weight(1f), enabled = index > 0, shape = RoundedCornerShape(16.dp)) { Text("上一章") }
                    Button(onNext, Modifier.weight(1f), enabled = index >= 0 && index < sorted.lastIndex, shape = RoundedCornerShape(16.dp)) { Text("下一章") }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aa", fontSize = 15.sp)
                    Slider(fontSize, { fontSize = it }, valueRange = 15f..26f, modifier = Modifier.padding(horizontal = 10.dp).weight(1f))
                    Text("Aa", fontSize = 23.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onEdit(chapter.chapterNumber) }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.EditNote, null); Spacer(Modifier.width(5.dp)); Text("编辑本章")
                    }
                    Button(onEnterStory, Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("进入故事")
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingTabV4(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    onWriting: () -> Unit,
    onEdit: (Int) -> Unit,
    onAgent: () -> Unit,
    onRunCenter: () -> Unit,
) {
    val current = state.chapters.firstOrNull { it.chapterNumber == book.currentChapter } ?: state.chapters.lastOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("创作", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("高级流程继续放到底层，这里只保留最常用的写作动作。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onWriting, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("继续 AI 创作")
            }
        }
        item {
            ActionCardV4(
                Icons.Rounded.EditNote,
                current?.let { "编辑第 ${it.chapterNumber} 章" } ?: "编辑章节",
                "直接修改正文，确认过的章节也可以继续修改。",
                current != null,
            ) { current?.let { onEdit(it.chapterNumber) } }
        }
        item { ActionCardV4(Icons.Rounded.SmartToy, "AI 助手", "讨论剧情、人物、章节走向和修改方案。", true, onAgent) }
        item { ActionCardV4(Icons.Rounded.TaskAlt, "后台任务", "查看正在生成、等待或失败的长篇任务。", true, onRunCenter) }
    }
}

@Composable
private fun SettingTabV4(
    book: ReaderBookUi,
    aiReady: Boolean,
    aiLabel: String,
    onIntelligence: () -> Unit,
    onExtractCanon: () -> Unit,
    onAgent: () -> Unit,
    onAiSetup: () -> Unit,
    onCover: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("设定", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("《${book.title}》的人物、世界、记忆和 AI 配置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ActionCardV4(Icons.Rounded.Hub, "人物 / 世界 / 时间线", "查看长期事实、人物状态、伏笔与故事一致性。", true, onIntelligence) }
        item {
            ActionCardV4(
                Icons.Rounded.MenuBook,
                "从原著抽设定",
                "逐章读取全书正文，建立人物、地点、规则、事件、关系和证据索引；支持断点继续。",
                true,
                onExtractCanon,
            )
        }
        item { ActionCardV4(Icons.Rounded.SmartToy, "AI 助手", "围绕本书继续讨论和整理设定。", true, onAgent) }
        item { ActionCardV4(Icons.Rounded.Image, "封面工作室", "生成、预览、采用和恢复作品封面。", true, onCover) }
        item {
            ActionCardV4(
                Icons.Rounded.CloudDone,
                if (aiReady) aiLabel.ifBlank { "AI 已连接" } else "配置 AI / 中转站",
                if (aiReady) "切换模型或修改当前服务。" else "添加兼容 OpenAI 风格接口的模型服务。",
                true,
                onAiSetup,
            )
        }
    }
}

@Composable
private fun ActionCardV4(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
