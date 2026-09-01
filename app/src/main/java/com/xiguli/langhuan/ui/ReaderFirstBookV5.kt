package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class ReaderBookRouteV5 { OVERVIEW, READER, STORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderFirstBookV5(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook ?: return
    var route by remember(book.id) { mutableStateOf(ReaderBookRouteV5.OVERVIEW) }

    when (route) {
        ReaderBookRouteV5.OVERVIEW -> ReaderBookOverviewV5(
            book = book,
            state = state,
            onBack = onBackToShelf,
            onContinue = {
                val target = state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                    ?: state.chapters.firstOrNull()
                target?.let { viewModel.openReader(it.chapterNumber); route = ReaderBookRouteV5.READER }
            },
            onChapter = { number -> viewModel.openReader(number); route = ReaderBookRouteV5.READER },
            onStory = {
                val target = state.readingChapter
                    ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                    ?: state.chapters.firstOrNull()
                target?.let { viewModel.openReader(it.chapterNumber) }
                route = ReaderBookRouteV5.STORY
            },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )

        ReaderBookRouteV5.READER -> ImmersiveReaderV5(
            book = book,
            state = state,
            onBack = { route = ReaderBookRouteV5.OVERVIEW },
            onPrevious = viewModel::readPrevious,
            onNext = viewModel::readNext,
            onChapter = viewModel::openReader,
            onStory = { route = ReaderBookRouteV5.STORY },
            onEdit = { chapter -> onOpenEditor(book.id, chapter) },
        )

        ReaderBookRouteV5.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            SmallFloatingActionButton(
                onClick = { route = ReaderBookRouteV5.READER },
                modifier = Modifier.statusBarsPadding().padding(14.dp).align(Alignment.TopStart),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .95f),
            ) { Icon(Icons.Rounded.ArrowBack, "返回阅读") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBookOverviewV5(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onChapter: (Int) -> Unit,
    onStory: () -> Unit,
    onWriting: () -> Unit,
    onAiSetup: () -> Unit,
) {
    var showDirectory by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回书架") }
                Spacer(Modifier.weight(1f))
                IconButton(onAiSetup) { Icon(Icons.Rounded.MoreHoriz, "更多") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.dp))
            val coverShape = RoundedCornerShape(18.dp)
            CoverPreviewV3(
                book.coverPath,
                book.title,
                Modifier
                    .width(168.dp)
                    .height(240.dp)
                    .shadow(12.dp, coverShape, clip = false)
                    .clip(coverShape),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                book.title,
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (book.genre == "导入作品") "本地书籍 · ${state.chapters.size} 章 · ${overviewWordsV5(book.currentWords)}"
                else "${book.genre} · ${state.chapters.size} 章 · ${overviewWordsV5(book.currentWords)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = state.chapters.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text(if (book.currentChapter > 1) "继续阅读 · 第 ${book.currentChapter} 章" else "开始阅读")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onStory,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp),
                enabled = state.chapters.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("进入故事")
            }

            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReaderQuickActionV5(Icons.Rounded.FormatListBulleted, "目录", Modifier.weight(1f)) { showDirectory = true }
                ReaderQuickActionV5(Icons.Rounded.EditNote, "创作", Modifier.weight(1f), onWriting)
                ReaderQuickActionV5(Icons.Rounded.Tune, "AI", Modifier.weight(1f), onAiSetup)
            }

            if (book.premise.isNotBlank() && book.premise != "从外部稿件导入，待补充核心命题与完整大纲。") {
                Spacer(Modifier.height(32.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        book.premise,
                        modifier = Modifier.padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 25.sp,
                    )
                }
            }

            Spacer(Modifier.height(34.dp))
            Column(Modifier.fillMaxWidth()) {
                Text("最近章节", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                state.chapters.take(6).forEach { chapter ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onChapter(chapter.chapterNumber) }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${chapter.chapterNumber}",
                            modifier = Modifier.width(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                }
            }
            Spacer(Modifier.height(44.dp))
        }
    }

    if (showDirectory) {
        ChapterDirectorySheetV5(state, onDismiss = { showDirectory = false }) { number ->
            showDirectory = false
            onChapter(number)
        }
    }
}

@Composable
private fun ReaderQuickActionV5(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveReaderV5(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapter: (Int) -> Unit,
    onStory: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val chapter = state.readingChapter ?: state.chapters.firstOrNull()
    if (chapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("这本书还没有可阅读正文") }
        return
    }
    var chrome by remember { mutableStateOf(true) }
    var showDirectory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var fontSize by rememberSaveable(book.id) { mutableFloatStateOf(19f) }
    var lineFactor by rememberSaveable(book.id) { mutableFloatStateOf(1.85f) }
    var sidePadding by rememberSaveable(book.id) { mutableFloatStateOf(24f) }
    val scroll = rememberScrollState()
    val ordered = state.chapters.sortedBy { it.chapterNumber }
    val index = ordered.indexOfFirst { it.id == chapter.id }

    LaunchedEffect(chapter.id) { scroll.scrollTo(0) }

    Box(
        Modifier.fillMaxSize().clickable { chrome = !chrome },
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = sidePadding.dp)
                    .padding(top = 64.dp, bottom = 118.dp),
            ) {
                Text(
                    "第 ${chapter.chapterNumber} 章",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    chapter.title.ifBlank { "未命名章节" },
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = (fontSize + 7).sp,
                    lineHeight = (fontSize + 13).sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    chapter.content.ifBlank { "这一章没有正文。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineFactor).sp,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    "— 第 ${chapter.chapterNumber} 章完 —",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (chrome) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .95f),
                tonalElevation = 3.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                    Text(
                        book.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton({ showDirectory = true }) { Icon(Icons.Rounded.FormatListBulleted, "目录") }
                    IconButton({ showSettings = true }) { Icon(Icons.Rounded.TextFields, "阅读设置") }
                    IconButton({ onEdit(chapter.chapterNumber) }) { Icon(Icons.Rounded.EditNote, "编辑") }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .96f),
                tonalElevation = 4.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    LinearProgressIndicator(
                        progress = { if (ordered.isEmpty()) 0f else ((index + 1f) / ordered.size).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(99.dp)),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onPrevious, enabled = index > 0) {
                            Icon(Icons.Rounded.ChevronLeft, null)
                            Text("上一章")
                        }
                        Text(
                            "${chapter.chapterNumber} / ${ordered.size}",
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onNext, enabled = index >= 0 && index < ordered.lastIndex) {
                            Text("下一章")
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                    Button(
                        onClick = onStory,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(7.dp))
                        Text("从这一章进入故事")
                    }
                }
            }
        }
    }

    if (showDirectory) ChapterDirectorySheetV5(state, { showDirectory = false }) { number ->
        showDirectory = false
        onChapter(number)
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                Text("阅读设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("字号", modifier = Modifier.padding(top = 20.dp), fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 15.sp)
                    Slider(
                        fontSize,
                        { fontSize = it },
                        valueRange = 15f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("A", fontSize = 25.sp)
                }
                Text("行距", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                Slider(lineFactor, { lineFactor = it }, valueRange = 1.5f..2.2f)
                Text("页边距", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                Slider(sidePadding, { sidePadding = it }, valueRange = 16f..40f)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterDirectorySheetV5(
    state: LibraryExperienceState,
    onDismiss: () -> Unit,
    onChapter: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("目录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${state.chapters.size} 章", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onDismiss) { Text("完成") }
            }
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 620.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                val ordered = state.chapters.sortedBy { it.chapterNumber }
                items(ordered.size) { i ->
                    val chapter = ordered[i]
                    val selected = chapter.id == state.readingChapter?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChapter(chapter.chapterNumber) }
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${chapter.chapterNumber}",
                            modifier = Modifier.width(42.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Icon(
                                Icons.Rounded.RadioButtonChecked,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun overviewWordsV5(words: Int): String =
    if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"