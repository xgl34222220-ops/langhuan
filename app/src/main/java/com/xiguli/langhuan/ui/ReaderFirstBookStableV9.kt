package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

private enum class StableBookRouteV9 { OVERVIEW, READER, STORY }
private enum class StableReaderToneV9(val key: String, val label: String) {
    SYSTEM("system", "默认"),
    PAPER("paper", "纸张"),
    GREEN("green", "护眼"),
    NIGHT("night", "夜间"),
}

@Composable
fun ReaderFirstBookStableV9(
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
        mutableStateOf(if (isLocal) StableBookRouteV9.READER else StableBookRouteV9.OVERVIEW)
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
        StableBookRouteV9.OVERVIEW -> StableBookOverviewV9(
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
                    route = StableBookRouteV9.READER
                }
            },
            onChapter = { number ->
                viewModel.openReader(number)
                rememberChapter(number)
                route = StableBookRouteV9.READER
            },
            onStory = {
                val target = state.readingChapter
                    ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                    ?: state.chapters.firstOrNull()
                target?.let { viewModel.openReader(it.chapterNumber) }
                route = StableBookRouteV9.STORY
            },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )

        StableBookRouteV9.READER -> StableImmersiveReaderV9(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = {
                if (isLocal) onBackToShelf() else route = StableBookRouteV9.OVERVIEW
            },
            onOpenInfo = { route = StableBookRouteV9.OVERVIEW },
            onChapter = { number ->
                viewModel.openReader(number)
                rememberChapter(number)
            },
            onStory = { route = StableBookRouteV9.STORY },
            onEdit = { chapter -> onOpenEditor(book.id, chapter) },
        )

        StableBookRouteV9.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            Surface(
                modifier = Modifier.statusBarsPadding().padding(14.dp).align(Alignment.TopStart),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
                shadowElevation = 4.dp,
            ) {
                IconButton(onClick = { route = StableBookRouteV9.READER }) {
                    Icon(Icons.Rounded.ArrowBack, "返回阅读")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StableBookOverviewV9(
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            CoverPreviewV3(
                book.coverPath,
                book.title,
                Modifier.width(122.dp).height(174.dp).clip(RoundedCornerShape(13.dp)),
            )
            Text(
                book.title,
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(if (isLocal) "本地书籍" else book.genre)
                    append(" · ${state.chapters.size} 章")
                    if (book.currentWords > 0) append(" · ${stableOverviewWordsV9(book.currentWords)}")
                },
                modifier = Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onContinue,
                enabled = state.chapters.isNotEmpty(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(48.dp),
            ) {
                Icon(Icons.Rounded.MenuBook, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("继续阅读")
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StableOverviewActionV9(Icons.Rounded.FormatListBulleted, "目录", state.chapters.isNotEmpty()) {
                    showDirectory = true
                }
                StableOverviewActionV9(Icons.Rounded.AutoAwesome, "故事", state.chapters.isNotEmpty(), onStory)
                if (!isLocal) {
                    StableOverviewActionV9(Icons.Rounded.EditNote, "创作", true, onWriting)
                } else {
                    StableOverviewActionV9(Icons.Rounded.Info, "详情", true) { }
                }
            }

            if (book.premise.isNotBlank() && book.premise != "从外部稿件导入，待补充核心命题与完整大纲。") {
                StableSectionTitleV9("简介")
                Text(
                    book.premise,
                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 23.sp,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("章节", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showDirectory = true }, enabled = state.chapters.isNotEmpty()) {
                    Text("全部 ${state.chapters.size}")
                }
            }
            state.chapters.sortedBy { it.chapterNumber }.take(8).forEach { chapter ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onChapter(chapter.chapterNumber) }
                        .padding(vertical = 12.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${chapter.chapterNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }

        Surface(
            modifier = Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopStart),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
            border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
        }
        if (!isLocal) {
            Surface(
                modifier = Modifier.statusBarsPadding().padding(10.dp).align(Alignment.TopEnd),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
            ) {
                IconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") }
            }
        }
    }

    if (showDirectory) {
        StableDirectorySheetV9(state, onDismiss = { showDirectory = false }) { number ->
            showDirectory = false
            onChapter(number)
        }
    }
}

@Composable
private fun StableOverviewActionV9(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) .60f else .30f),
        ) {
            Icon(
                icon,
                label,
                modifier = Modifier.padding(9.dp).size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .4f),
            )
        }
        Text(
            label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .4f),
        )
    }
}

@Composable
private fun StableSectionTitleV9(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StableImmersiveReaderV9(
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
    val chapter = state.readingChapter
        ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
        ?: state.chapters.firstOrNull()
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
    var toneKey by remember(book.id) { mutableStateOf(prefs.getString("tone_${book.id}", StableReaderToneV9.SYSTEM.key) ?: StableReaderToneV9.SYSTEM.key) }
    val tone = StableReaderToneV9.entries.firstOrNull { it.key == toneKey } ?: StableReaderToneV9.SYSTEM
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
        StableReaderToneV9.SYSTEM -> MaterialTheme.colorScheme.background
        StableReaderToneV9.PAPER -> Color(0xFFF4F0E6)
        StableReaderToneV9.GREEN -> Color(0xFFE9F0E5)
        StableReaderToneV9.NIGHT -> Color(0xFF151618)
    }
    val foreground = when (tone) {
        StableReaderToneV9.SYSTEM -> MaterialTheme.colorScheme.onBackground
        StableReaderToneV9.PAPER -> Color(0xFF302D28)
        StableReaderToneV9.GREEN -> Color(0xFF273127)
        StableReaderToneV9.NIGHT -> Color(0xFFD7D5D0)
    }
    val secondary = when (tone) {
        StableReaderToneV9.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
        StableReaderToneV9.PAPER -> Color(0xFF756F65)
        StableReaderToneV9.GREEN -> Color(0xFF687267)
        StableReaderToneV9.NIGHT -> Color(0xFF92908C)
    }

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
                    .padding(top = 92.dp, bottom = 116.dp),
            ) {
                Text(
                    "第 ${chapter.chapterNumber} 章",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary,
                )
                Text(
                    chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                    modifier = Modifier.padding(top = 7.dp),
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
                Spacer(Modifier.height(54.dp))
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = background.copy(alpha = .88f),
                border = BorderStroke(.5.dp, secondary.copy(alpha = .22f)),
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.height(50.dp).padding(horizontal = 2.dp),
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
                    )
                    IconButton(onClick = { showDirectory = true }) {
                        Icon(Icons.Rounded.FormatListBulleted, "目录", tint = foreground)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.TextFields, "阅读设置", tint = foreground)
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Rounded.MoreHoriz, "更多", tint = foreground)
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                shape = RoundedCornerShape(28.dp),
                color = background.copy(alpha = .88f),
                border = BorderStroke(.5.dp, secondary.copy(alpha = .22f)),
                shadowElevation = 4.dp,
            ) {
                Row(
                    Modifier.widthIn(min = 270.dp).height(52.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { moveTo(index - 1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.ChevronLeft, "上一章", tint = if (index > 0) foreground else secondary.copy(alpha = .35f))
                    }
                    Text(
                        "${index + 1} / ${ordered.size}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = secondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = { moveTo(index + 1) }, enabled = index >= 0 && index < ordered.lastIndex) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            "下一章",
                            tint = if (index >= 0 && index < ordered.lastIndex) foreground else secondary.copy(alpha = .35f),
                        )
                    }
                }
            }
        }
    }

    if (showDirectory) {
        StableDirectorySheetV9(state, onDismiss = { showDirectory = false }) { number ->
            showDirectory = false
            onChapter(number)
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp),
            ) {
                Text("阅读设置", style = MaterialTheme.typography.titleLarge)
                Text("背景", modifier = Modifier.padding(top = 20.dp), fontWeight = FontWeight.Medium)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StableReaderToneV9.entries.forEach { option ->
                        FilterChip(
                            selected = tone == option,
                            onClick = { toneKey = option.key },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text("字号", modifier = Modifier.padding(top = 18.dp), fontWeight = FontWeight.Medium)
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
                Text("行距", modifier = Modifier.padding(top = 6.dp), fontWeight = FontWeight.Medium)
                Slider(value = lineFactor, onValueChange = { lineFactor = it }, valueRange = 1.45f..2.20f)
                Text("页边距", modifier = Modifier.padding(top = 6.dp), fontWeight = FontWeight.Medium)
                Slider(value = sidePadding, onValueChange = { sidePadding = it }, valueRange = 16f..42f)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StableDirectorySheetV9(
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
                    Text("目录", style = MaterialTheme.typography.titleLarge)
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
                            .padding(horizontal = 20.dp, vertical = 13.dp),
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
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                Box(Modifier.size(7.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun stableOverviewWordsV9(words: Int): String =
    if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
