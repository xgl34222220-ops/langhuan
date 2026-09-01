package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ReaderBookRouteV7 { INFO, READER, STORY }
private enum class ReaderThemeV7(val key: String, val label: String) {
    SYSTEM("system", "默认"), PAPER("paper", "纸张"), WARM("warm", "暖黄"), GREEN("green", "护眼"), NIGHT("night", "夜间")
}
private enum class ReaderFontV7(val key: String, val label: String) {
    DEFAULT("default", "默认"), SERIF("serif", "衬线"), SANS("sans", "黑体"), MONO("mono", "等宽")
}

@Composable
fun ReaderFirstBookV7(
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
    var route by remember(book.id) { mutableStateOf(if (isLocal) ReaderBookRouteV7.READER else ReaderBookRouteV7.INFO) }

    LaunchedEffect(book.id, state.chapters.size) {
        if (state.chapters.isNotEmpty() && state.readingChapter == null) {
            val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
            val target = state.chapters.firstOrNull { it.chapterNumber == saved }
                ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                ?: state.chapters.first()
            viewModel.openReader(target.chapterNumber)
        }
    }

    fun openChapter(number: Int) {
        viewModel.openReader(number)
        progressPrefs.edit()
            .putInt("chapter_${book.id}", number)
            .putLong("last_${book.id}", System.currentTimeMillis())
            .apply()
    }

    when (route) {
        ReaderBookRouteV7.INFO -> ReaderBookInfoPageV7(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = { if (isLocal) route = ReaderBookRouteV7.READER else onBackToShelf() },
            onRead = {
                val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
                val target = state.chapters.firstOrNull { it.chapterNumber == saved } ?: state.chapters.firstOrNull()
                target?.let { openChapter(it.chapterNumber); route = ReaderBookRouteV7.READER }
            },
            onStory = { route = ReaderBookRouteV7.STORY },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )
        ReaderBookRouteV7.READER -> ReaderPageV7(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = onBackToShelf,
            onOpenInfo = { route = ReaderBookRouteV7.INFO },
            onChapter = ::openChapter,
            onStory = { route = ReaderBookRouteV7.STORY },
            onEdit = { onOpenEditor(book.id, it) },
        )
        ReaderBookRouteV7.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            SmallFloatingActionButton(
                onClick = { route = ReaderBookRouteV7.READER },
                modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart),
                shape = RoundedCornerShape(12.dp),
            ) { Icon(Icons.Rounded.ArrowBack, "返回阅读") }
        }
    }
}

@Composable
private fun ReaderPageV7(
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
    val prefs = remember { context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE) }
    val chapter = state.readingChapter ?: state.chapters.firstOrNull()
    if (chapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("这本书没有可阅读正文") }
        return
    }
    val ordered = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val index = ordered.indexOfFirst { it.id == chapter.id }
    var chrome by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 19f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.78f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 25f)) }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", ReaderThemeV7.PAPER.key) ?: ReaderThemeV7.PAPER.key) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", ReaderFontV7.DEFAULT.key) ?: ReaderFontV7.DEFAULT.key) }
    val theme = ReaderThemeV7.entries.firstOrNull { it.key == themeKey } ?: ReaderThemeV7.PAPER
    val font = ReaderFontV7.entries.firstOrNull { it.key == fontKey } ?: ReaderFontV7.DEFAULT
    val scroll = rememberScrollState()

    LaunchedEffect(chapter.id) { scroll.scrollTo(0) }
    LaunchedEffect(fontSize, lineFactor, sidePadding, themeKey, fontKey) {
        prefs.edit()
            .putFloat("font_${book.id}", fontSize)
            .putFloat("line_${book.id}", lineFactor)
            .putFloat("padding_${book.id}", sidePadding)
            .putString("theme_${book.id}", themeKey)
            .putString("family_${book.id}", fontKey)
            .apply()
    }

    val palette = readerPaletteV7(theme)
    val family = when (font) {
        ReaderFontV7.DEFAULT -> FontFamily.Default
        ReaderFontV7.SERIF -> FontFamily.Serif
        ReaderFontV7.SANS -> FontFamily.SansSerif
        ReaderFontV7.MONO -> FontFamily.Monospace
    }

    fun move(offset: Int) {
        ordered.getOrNull(index + offset)?.let { onChapter(it.chapterNumber) }
    }

    Box(Modifier.fillMaxSize().background(palette.background).clickable { chrome = !chrome }) {
        SelectionContainer {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = sidePadding.dp).padding(top = 46.dp, bottom = 72.dp),
            ) {
                Text(
                    chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                    fontSize = (fontSize + 5).sp,
                    lineHeight = (fontSize + 11).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = family,
                    color = palette.foreground,
                )
                Spacer(Modifier.height(26.dp))
                Text(
                    chapter.content.ifBlank { "这一章没有正文。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineFactor).sp,
                    fontFamily = family,
                    color = palette.foreground,
                )
                Spacer(Modifier.height(46.dp))
                Text(
                    "${index + 1} / ${ordered.size}",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary,
                )
            }
        }

        if (!chrome) {
            Text(
                "${index + 1} / ${ordered.size}",
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .76f),
            )
        }

        if (chrome) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = palette.background.copy(alpha = .985f)) {
                Row(
                    Modifier.statusBarsPadding().height(50.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = palette.foreground) }
                    Text(
                        chapter.title.ifBlank { book.title },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.foreground,
                    )
                    IconButton(onStory) { Icon(Icons.Rounded.AutoAwesome, "进入故事", tint = palette.foreground) }
                }
            }

            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = palette.background.copy(alpha = .99f)) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = palette.secondary.copy(alpha = .14f))
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ move(-1) }, enabled = index > 0) { Text("上一章") }
                        Text(
                            "第 ${chapter.chapterNumber} 章 · ${index + 1}/${ordered.size}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondary,
                        )
                        TextButton({ move(1) }, enabled = index in 0 until ordered.lastIndex) { Text("下一章") }
                    }
                    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ReaderBottomActionV7(Icons.Rounded.Info, "详情") { showInfo = true }
                        ReaderBottomActionV7(Icons.Rounded.FormatListBulleted, "目录") { showDirectory = true }
                        ReaderBottomActionV7(Icons.Rounded.MoreHoriz, "更多") { showMore = true }
                    }
                }
            }
        }
    }

    if (showInfo) BookInfoSheetV7(book, state, { showInfo = false }, onOpenInfo)
    if (showDirectory) DirectorySheetV7(state, { showDirectory = false }) { number -> showDirectory = false; onChapter(number) }
    if (showMore) ReaderMoreSheetV7(
        theme = theme,
        font = font,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        isLocal = isLocal,
        onTheme = { themeKey = it.key },
        onFont = { fontKey = it.key },
        onFontSize = { fontSize = it },
        onLine = { lineFactor = it },
        onPadding = { sidePadding = it },
        onSearch = { showMore = false; showSearch = true },
        onStory = { showMore = false; onStory() },
        onEdit = { showMore = false; onEdit(chapter.chapterNumber) },
        onDismiss = { showMore = false },
    )
    if (showSearch) SearchBookSheetV7(state, { showSearch = false }) { number -> showSearch = false; onChapter(number) }
}

@Composable
private fun ReaderBottomActionV7(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick, modifier = Modifier.height(58.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMoreSheetV7(
    theme: ReaderThemeV7,
    font: ReaderFontV7,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    isLocal: Boolean,
    onTheme: (ReaderThemeV7) -> Unit,
    onFont: (ReaderFontV7) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onSearch: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("主题", modifier = Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReaderThemeV7.entries.forEach { option ->
                    val p = readerPaletteV7(option)
                    Surface(
                        modifier = Modifier.width(88.dp).height(68.dp).clickable { onTheme(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = p.background,
                        border = if (theme == option) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("Aa", color = p.foreground, fontWeight = FontWeight.SemiBold)
                            Text(option.label, color = p.secondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Text("字体", modifier = Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFontV7.entries.forEach { option ->
                    FilterChip(selected = font == option, onClick = { onFont(option) }, label = { Text(option.label) })
                }
            }

            Text("字号  ${fontSize.toInt()}", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onFontSize((fontSize - 1f).coerceAtLeast(14f)) }) { Icon(Icons.Rounded.Remove, "减小字号") }
                Slider(fontSize, onFontSize, valueRange = 14f..30f, modifier = Modifier.weight(1f))
                IconButton({ onFontSize((fontSize + 1f).coerceAtMost(30f)) }) { Icon(Icons.Rounded.Add, "增大字号") }
            }
            Text("行距", style = MaterialTheme.typography.labelLarge)
            Slider(lineFactor, onLine, valueRange = 1.42f..2.25f)
            Text("页边距", style = MaterialTheme.typography.labelLarge)
            Slider(sidePadding, onPadding, valueRange = 14f..44f)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text("全文搜索") },
                leadingContent = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.clickable(onClick = onSearch),
            )
            ListItem(
                headlineContent = { Text("从本章进入故事") },
                supportingContent = { Text("以当前章节为原著知识边界") },
                leadingContent = { Icon(Icons.Rounded.AutoAwesome, null) },
                modifier = Modifier.clickable(onClick = onStory),
            )
            if (!isLocal) {
                ListItem(
                    headlineContent = { Text("编辑本章") },
                    leadingContent = { Icon(Icons.Rounded.EditNote, null) },
                    modifier = Modifier.clickable(onClick = onEdit),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorySheetV7(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val chapters = remember(state.chapters, query) {
        state.chapters.sortedBy { it.chapterNumber }.filter { query.isBlank() || it.title.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${state.chapters.size} 章", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onDismiss) { Text("完成") }
            }
            TextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                placeholder = { Text("搜索章节") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            )
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                items(chapters.size) { i ->
                    val item = chapters[i]
                    val selected = item.id == state.readingChapter?.id
                    Row(
                        Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.title.ifBlank { "第 ${item.chapterNumber} 章" },
                            modifier = Modifier.weight(1f),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) Icon(Icons.Rounded.RadioButtonChecked, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBookSheetV7(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(state.chapters, query) {
        if (query.trim().length < 2) emptyList() else state.chapters.filter {
            it.title.contains(query, true) || it.content.contains(query, true)
        }.take(120)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text("全文搜索", modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                placeholder = { Text("输入两个字以上") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            )
            if (query.length >= 2) Text("找到 ${results.size} 个章节", Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(results.size) { i ->
                    val item = results[i]
                    Column(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val hit = searchSnippetV7(item.content, query)
                        if (hit.isNotBlank()) Text(hit, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookInfoSheetV7(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(103.dp).clip(RoundedCornerShape(7.dp)))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${state.chapters.size} 章 · ${humanWordsV7(book.currentWords)}", modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            InfoRowV7("当前章节", state.readingChapter?.title ?: "第 ${state.readingChapter?.chapterNumber ?: book.currentChapter} 章")
            TextButton(onClick = { onDismiss(); onOpenFull() }, modifier = Modifier.align(Alignment.End)) { Text("查看完整详情") }
        }
    }
}

@Composable
private fun ReaderBookInfoPageV7(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    isLocal: Boolean,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onStory: () -> Unit,
    onWriting: () -> Unit,
    onAiSetup: () -> Unit,
) {
    val context = LocalContext.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val progress = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    val fileName = meta.getString("name_${book.id}", book.title).orEmpty()
    val fileSize = meta.getLong("size_${book.id}", 0L)
    val format = meta.getString("format_${book.id}", if (isLocal) "本地" else "创作").orEmpty()
    val importedAt = meta.getLong("imported_${book.id}", 0L)
    val recent = progress.getLong("last_${book.id}", 0L)
    val current = progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("图书详情", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!isLocal) IconButton(onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") }
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(104.dp).height(149.dp).clip(RoundedCornerShape(7.dp)))
                Column(Modifier.padding(start = 18.dp).weight(1f)) {
                    Text(book.title, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (isLocal) "本地书籍" else book.genre, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.chapters.size} 章 · ${humanWordsV7(book.currentWords)}", modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onRead, Modifier.fillMaxWidth().padding(top = 26.dp).height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("继续阅读 · 第 $current 章") }
            OutlinedButton(onStory, Modifier.fillMaxWidth().padding(top = 9.dp).height(46.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(7.dp))
                Text("从当前章节进入故事")
            }
            Text("阅读信息", modifier = Modifier.padding(top = 28.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            InfoRowV7("当前章节", "第 $current 章")
            InfoRowV7("总章节", "${state.chapters.size} 章")
            InfoRowV7("总字数", humanWordsV7(book.currentWords))
            InfoRowV7("最近阅读", if (recent > 0L) formatTimeV7(recent) else "暂无")
            if (isLocal) {
                Text("文件信息", modifier = Modifier.padding(top = 26.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                InfoRowV7("文件名", fileName.ifBlank { book.title })
                InfoRowV7("文件大小", if (fileSize > 0L) humanBytesV7(fileSize) else "未知")
                InfoRowV7("格式", format.ifBlank { "本地文件" })
                InfoRowV7("导入时间", if (importedAt > 0L) formatTimeV7(importedAt) else "旧版本导入")
            }
            if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) {
                Text("简介", modifier = Modifier.padding(top = 26.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(book.premise, modifier = Modifier.padding(top = 9.dp), lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isLocal) TextButton(onWriting, Modifier.padding(top = 18.dp)) { Text("打开创作工作台") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRowV7(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(88.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .36f))
}

private data class ReaderPaletteV7(val background: Color, val foreground: Color, val secondary: Color)
private fun readerPaletteV7(theme: ReaderThemeV7): ReaderPaletteV7 = when (theme) {
    ReaderThemeV7.SYSTEM -> ReaderPaletteV7(Color(0xFFF9F9F8), Color(0xFF202020), Color(0xFF777777))
    ReaderThemeV7.PAPER -> ReaderPaletteV7(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF7B756C))
    ReaderThemeV7.WARM -> ReaderPaletteV7(Color(0xFFF3E5C9), Color(0xFF362D23), Color(0xFF7E6E5B))
    ReaderThemeV7.GREEN -> ReaderPaletteV7(Color(0xFFE4ECE0), Color(0xFF283129), Color(0xFF657166))
    ReaderThemeV7.NIGHT -> ReaderPaletteV7(Color(0xFF171819), Color(0xFFD5D2CC), Color(0xFF8E8C87))
}

private fun searchSnippetV7(text: String, query: String): String {
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return text.replace('\n', ' ').take(90)
    val start = (index - 35).coerceAtLeast(0)
    val end = (index + query.length + 55).coerceAtMost(text.length)
    return text.substring(start, end).replace('\n', ' ').trim()
}
private fun humanWordsV7(words: Int): String = if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
private fun humanBytesV7(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
private fun formatTimeV7(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
