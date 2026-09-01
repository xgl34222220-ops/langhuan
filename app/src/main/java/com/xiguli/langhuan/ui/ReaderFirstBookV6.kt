package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class ReaderBookRouteV6 { OVERVIEW, READER, STORY }
private enum class ReaderToneV6(val key: String, val label: String) {
    SYSTEM("system", "默认"),
    PAPER("paper", "纸张"),
    GREEN("green", "护眼"),
    NIGHT("night", "夜间"),
}

/**
 * 本地小说默认直接进入阅读，不再先进入“项目详情页”。
 * 创作和 AI 能力保留，但退到阅读流程之后。
 */
@Composable
fun ReaderFirstBookV6(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook ?: return
    val isLocal = book.genre == "导入作品"
    val context = LocalContext.current
    val progressPrefs = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    var route by remember(book.id) {
        mutableStateOf(if (isLocal) ReaderBookRouteV6.READER else ReaderBookRouteV6.OVERVIEW)
    }

    LaunchedEffect(book.id, state.chapters.size) {
        if (isLocal && state.chapters.isNotEmpty() && state.readingChapter == null) {
            val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
            val target = state.chapters.firstOrNull { it.chapterNumber == saved }
                ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                ?: state.chapters.first()
            viewModel.openReader(target.chapterNumber)
        }
    }

    fun rememberChapter(number: Int) {
        progressPrefs.edit().putInt("chapter_${book.id}", number).apply()
    }

    when (route) {
        ReaderBookRouteV6.OVERVIEW -> ReaderBookOverviewV6(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = onBackToShelf,
            onContinue = {
                val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
                val target = state.chapters.firstOrNull { it.chapterNumber == saved }
                    ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                    ?: state.chapters.firstOrNull()
                target?.let {
                    viewModel.openReader(it.chapterNumber)
                    rememberChapter(it.chapterNumber)
                    route = ReaderBookRouteV6.READER
                }
            },
            onChapter = { number ->
                viewModel.openReader(number)
                rememberChapter(number)
                route = ReaderBookRouteV6.READER
            },
            onStory = {
                val target = state.readingChapter
                    ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                    ?: state.chapters.firstOrNull()
                target?.let { viewModel.openReader(it.chapterNumber) }
                route = ReaderBookRouteV6.STORY
            },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )

        ReaderBookRouteV6.READER -> ImmersiveReaderV6(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = {
                if (isLocal) onBackToShelf() else route = ReaderBookRouteV6.OVERVIEW
            },
            onOpenInfo = { route = ReaderBookRouteV6.OVERVIEW },
            onChapter = { number ->
                viewModel.openReader(number)
                rememberChapter(number)
            },
            onStory = { route = ReaderBookRouteV6.STORY },
            onEdit = { chapter -> onOpenEditor(book.id, chapter) },
        )

        ReaderBookRouteV6.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            SmallFloatingActionButton(
                onClick = { route = ReaderBookRouteV6.READER },
                modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart),
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
            ) { Icon(Icons.Rounded.ArrowBack, "返回阅读") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBookOverviewV6(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    isLocal: Boolean,
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
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回书架") }
                Spacer(Modifier.weight(1f))
                if (!isLocal) {
                    IconButton(onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") }
                }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CoverPreviewV3(
                    book.coverPath,
                    book.title,
                    Modifier.width(112.dp).height(160.dp).clip(RoundedCornerShape(8.dp)),
                )
                Column(Modifier.padding(start = 18.dp).weight(1f)) {
                    Text(
                        book.title,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (isLocal) "本地书籍" else book.genre,
                        modifier = Modifier.padding(top = 7.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${state.chapters.size} 章 · ${overviewWordsV6(book.currentWords)}",
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = state.chapters.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text("继续阅读")
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showDirectory = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    enabled = state.chapters.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.FormatListBulleted, null)
                    Spacer(Modifier.width(6.dp))
                    Text("目录")
                }
                OutlinedButton(
                    onClick = onStory,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    enabled = state.chapters.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("进入故事")
                }
            }

            if (!isLocal) {
                TextButton(onClick = onWriting, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.EditNote, null)
                    Spacer(Modifier.width(6.dp))
                    Text("打开创作工作台")
                }
            }

            if (book.premise.isNotBlank() && book.premise != "从外部稿件导入，待补充核心命题与完整大纲。") {
                Text("简介", modifier = Modifier.padding(top = 28.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    book.premise,
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                )
            }

            Text("章节", modifier = Modifier.padding(top = 30.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            state.chapters.take(8).forEach { chapter ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChapter(chapter.chapterNumber) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (showDirectory) {
        ChapterDirectorySheetV6(state, onDismiss = { showDirectory = false }) { number ->
            showDirectory = false
            onChapter(number)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveReaderV6(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    isLocal: Boolean,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onChapter: (Int) -> Unit,
    onStory: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader_settings_v1", Context.MODE_PRIVATE) }
    val chapter = state.readingChapter ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter } ?: state.chapters.firstOrNull()
    if (chapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("这本书没有可阅读正文") }
        return
    }

    val ordered = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val index = ordered.indexOfFirst { it.id == chapter.id }
    var chrome by remember { mutableStateOf(true) }
    var showDirectory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 19f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.80f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var toneKey by remember(book.id) { mutableStateOf(prefs.getString("tone_${book.id}", ReaderToneV6.SYSTEM.key) ?: ReaderToneV6.SYSTEM.key) }
    val tone = ReaderToneV6.entries.firstOrNull { it.key == toneKey } ?: ReaderToneV6.SYSTEM
    val scroll = rememberScrollState()

    LaunchedEffect(chapter.id) { scroll.scrollTo(0) }
    LaunchedEffect(fontSize, lineFactor, sidePadding, toneKey) {
        prefs.edit()
            .putFloat("font_${book.id}", fontSize)
            .putFloat("line_${book.id}", lineFactor)
            .putFloat("padding_${book.id}", sidePadding)
            .putString("tone_${book.id}", toneKey)
            .apply()
    }

    val background = when (tone) {
        ReaderToneV6.SYSTEM -> MaterialTheme.colorScheme.surface
        ReaderToneV6.PAPER -> Color(0xFFF5F0E5)
        ReaderToneV6.GREEN -> Color(0xFFE8F0E4)
        ReaderToneV6.NIGHT -> Color(0xFF171717)
    }
    val foreground = when (tone) {
        ReaderToneV6.SYSTEM -> MaterialTheme.colorScheme.onSurface
        ReaderToneV6.PAPER -> Color(0xFF302D28)
        ReaderToneV6.GREEN -> Color(0xFF273127)
        ReaderToneV6.NIGHT -> Color(0xFFD6D2CB)
    }
    val secondary = when (tone) {
        ReaderToneV6.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
        ReaderToneV6.PAPER -> Color(0xFF756F65)
        ReaderToneV6.GREEN -> Color(0xFF687267)
        ReaderToneV6.NIGHT -> Color(0xFF8E8B86)
    }
    val barColor = background.copy(alpha = .97f)

    fun moveTo(newIndex: Int) {
        ordered.getOrNull(newIndex)?.let { onChapter(it.chapterNumber) }
    }

    Box(
        Modifier.fillMaxSize().background(background).clickable { chrome = !chrome },
    ) {
        SelectionContainer {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .verticalScroll(scroll)
                    .padding(horizontal = sidePadding.dp)
                    .padding(top = 74.dp, bottom = 104.dp),
            ) {
                Text(
                    "第 ${chapter.chapterNumber} 章",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                )
                Text(
                    chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = (fontSize + 6).sp,
                    lineHeight = (fontSize + 12).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    chapter.content.ifBlank { "这一章没有正文。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineFactor).sp,
                    color = foreground,
                )
                Spacer(Modifier.height(52.dp))
                Text(
                    "第 ${chapter.chapterNumber} 章完",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondary,
                )
            }
        }

        if (chrome) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                color = barColor,
                shadowElevation = 0.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = foreground) }
                    Text(
                        book.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = foreground,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    IconButton(onClick = { showDirectory = true }) {
                        Icon(Icons.Rounded.FormatListBulleted, "目录", tint = foreground)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.TextFields, "阅读设置", tint = foreground)
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Rounded.MoreVert, "更多", tint = foreground)
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("书籍详情") },
                                leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                onClick = { showMore = false; onOpenInfo() },
                            )
                            DropdownMenuItem(
                                text = { Text("从本章进入故事") },
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                                onClick = { showMore = false; onStory() },
                            )
                            if (!isLocal) {
                                DropdownMenuItem(
                                    text = { Text("编辑本章") },
                                    leadingIcon = { Icon(Icons.Rounded.EditNote, null) },
                                    onClick = { showMore = false; onEdit(chapter.chapterNumber) },
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = barColor,
                shadowElevation = 0.dp,
            ) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = secondary.copy(alpha = .16f))
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { moveTo(index - 1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.ChevronLeft, null)
                            Text("上一章")
                        }
                        Text(
                            "${index + 1} / ${ordered.size}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = secondary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = { moveTo(index + 1) }, enabled = index >= 0 && index < ordered.lastIndex) {
                            Text("下一章")
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }
                }
            }
        }
    }

    if (showDirectory) {
        ChapterDirectorySheetV6(state, onDismiss = { showDirectory = false }) { number ->
            showDirectory = false
            onChapter(number)
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                Text("阅读设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("背景", modifier = Modifier.padding(top = 20.dp), fontWeight = FontWeight.Medium)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderToneV6.entries.forEach { option ->
                        FilterChip(
                            selected = tone == option,
                            onClick = { toneKey = option.key },
                            label = { Text(option.label) },
                        )
                    }
                }

                Text("字号", modifier = Modifier.padding(top = 20.dp), fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 14.sp)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 15f..28f,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text("A", fontSize = 24.sp)
                }

                Text("行距", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                Slider(value = lineFactor, onValueChange = { lineFactor = it }, valueRange = 1.45f..2.20f)

                Text("页边距", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Medium)
                Slider(value = sidePadding, onValueChange = { sidePadding = it }, valueRange = 16f..42f)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterDirectorySheetV6(
    state: LibraryExperienceState,
    onDismiss: () -> Unit,
    onChapter: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${state.chapters.size} 章", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Icon(Icons.Rounded.RadioButtonChecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun overviewWordsV6(words: Int): String =
    if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
