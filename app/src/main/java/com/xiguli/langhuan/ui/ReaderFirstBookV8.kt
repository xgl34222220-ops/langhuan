package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

private enum class ReaderBookRouteV8 { INFO, READER, STORY }
private enum class ReaderThemeV8(val key: String, val label: String) {
    SYSTEM("system", "默认"), PAPER("paper", "纸张"), WARM("warm", "暖黄"), GREEN("green", "护眼"), NIGHT("night", "夜间")
}
private enum class ReaderFontV8(val key: String, val label: String) {
    DEFAULT("default", "默认"), SERIF("serif", "衬线"), SANS("sans", "黑体"), MONO("mono", "等宽")
}

/**
 * Reader V8：按厚墨/清墨式“正文优先、控制后退”重做阅读壳，
 * 同时使用真实 Miuix 组件而不是 Material3 圆角模仿。
 */
@Composable
fun ReaderFirstBookV8(
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
    var route by remember(book.id) { mutableStateOf(if (isLocal) ReaderBookRouteV8.READER else ReaderBookRouteV8.INFO) }

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
        ReaderBookRouteV8.INFO -> ReaderBookInfoPageV8(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = { if (isLocal) route = ReaderBookRouteV8.READER else onBackToShelf() },
            onRead = {
                val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
                val target = state.chapters.firstOrNull { it.chapterNumber == saved } ?: state.chapters.firstOrNull()
                target?.let { openChapter(it.chapterNumber); route = ReaderBookRouteV8.READER }
            },
            onStory = { route = ReaderBookRouteV8.STORY },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )

        ReaderBookRouteV8.READER -> ReaderPageV8(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = onBackToShelf,
            onOpenInfo = { route = ReaderBookRouteV8.INFO },
            onChapter = ::openChapter,
            onStory = { route = ReaderBookRouteV8.STORY },
            onEdit = { onOpenEditor(book.id, it) },
        )

        ReaderBookRouteV8.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            MiuixCard(
                modifier = Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                MiuixIconButton(onClick = { route = ReaderBookRouteV8.READER }) {
                    Icon(Icons.Rounded.ArrowBack, "返回阅读")
                }
            }
        }
    }
}

@Composable
private fun ReaderPageV8(
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
    val index = ordered.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    var chrome by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 19f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.82f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", ReaderThemeV8.PAPER.key) ?: ReaderThemeV8.PAPER.key) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", ReaderFontV8.DEFAULT.key) ?: ReaderFontV8.DEFAULT.key) }
    val theme = ReaderThemeV8.entries.firstOrNull { it.key == themeKey } ?: ReaderThemeV8.PAPER
    val font = ReaderFontV8.entries.firstOrNull { it.key == fontKey } ?: ReaderFontV8.DEFAULT
    val scroll = rememberScrollState()
    val palette = readerPaletteV8(theme)
    val family = when (font) {
        ReaderFontV8.DEFAULT -> FontFamily.Default
        ReaderFontV8.SERIF -> FontFamily.Serif
        ReaderFontV8.SANS -> FontFamily.SansSerif
        ReaderFontV8.MONO -> FontFamily.Monospace
    }

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

    fun move(offset: Int) {
        ordered.getOrNull(index + offset)?.let { onChapter(it.chapterNumber) }
    }

    Box(
        Modifier.fillMaxSize()
            .background(palette.background)
            .clickable { chrome = !chrome }
    ) {
        SelectionContainer {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = sidePadding.dp)
                    .padding(top = 54.dp, bottom = 84.dp),
            ) {
                Text(
                    chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                    fontSize = (fontSize + 5).sp,
                    lineHeight = (fontSize + 11).sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = family,
                    color = palette.foreground,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    chapter.content.ifBlank { "这一章没有正文。" },
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineFactor).sp,
                    fontFamily = family,
                    color = palette.foreground,
                )
                Spacer(Modifier.height(54.dp))
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
                color = palette.secondary.copy(alpha = .74f),
            )
        }

        if (chrome) {
            MiuixCard(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 7.dp).fillMaxWidth(),
                cornerRadius = 22.dp,
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                colors = MiuixCardDefaults.defaultColors(
                    color = palette.chrome.copy(alpha = .97f),
                    contentColor = palette.foreground,
                ),
            ) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiuixIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = palette.foreground) }
                    Text(
                        chapter.title.ifBlank { book.title },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.foreground,
                    )
                    MiuixIconButton(onClick = { showDirectory = true }) { Icon(Icons.Rounded.FormatListBulleted, "目录", tint = palette.foreground) }
                    MiuixIconButton(onClick = onStory) { Icon(Icons.Rounded.AutoAwesome, "进入故事", tint = palette.foreground) }
                }
            }

            MiuixCard(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),
                cornerRadius = 24.dp,
                insideMargin = PaddingValues(vertical = 5.dp),
                colors = MiuixCardDefaults.defaultColors(
                    color = palette.chrome.copy(alpha = .985f),
                    contentColor = palette.foreground,
                ),
            ) {
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ move(-1) }, enabled = index > 0) { Text("上一章", color = if (index > 0) palette.foreground else palette.secondary) }
                    Text(
                        "第 ${chapter.chapterNumber} 章 · ${index + 1}/${ordered.size}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondary,
                    )
                    TextButton({ move(1) }, enabled = index < ordered.lastIndex) { Text("下一章", color = if (index < ordered.lastIndex) palette.foreground else palette.secondary) }
                }
                HorizontalDivider(color = palette.secondary.copy(alpha = .13f))
                Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ReaderBottomActionV8(Icons.Rounded.Info, "详情", palette.foreground) { showInfo = true }
                    ReaderBottomActionV8(Icons.Rounded.FormatListBulleted, "目录", palette.foreground) { showDirectory = true }
                    ReaderBottomActionV8(Icons.Rounded.MoreHoriz, "更多", palette.foreground) { showMore = true }
                }
            }
        }
    }

    if (showInfo) BookInfoSheetV8(book, state, { showInfo = false }, onOpenInfo)
    if (showDirectory) DirectorySheetV8(state, { showDirectory = false }) { number -> showDirectory = false; onChapter(number) }
    if (showMore) ReaderMoreSheetV8(
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
    if (showSearch) SearchBookSheetV8(state, { showSearch = false }) { number -> showSearch = false; onChapter(number) }
}

@Composable
private fun ReaderBottomActionV8(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(90.dp).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ReaderMoreSheetV8(
    theme: ReaderThemeV8,
    font: ReaderFontV8,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    isLocal: Boolean,
    onTheme: (ReaderThemeV8) -> Unit,
    onFont: (ReaderFontV8) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onSearch: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    OverlayBottomSheet(show = true, title = "阅读设置", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text("主题", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReaderThemeV8.entries.forEach { option ->
                    val p = readerPaletteV8(option)
                    MiuixCard(
                        modifier = Modifier.width(92.dp).height(72.dp),
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(11.dp),
                        colors = MiuixCardDefaults.defaultColors(color = p.background, contentColor = p.foreground),
                        onClick = { onTheme(option) },
                        showIndication = true,
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Text("Aa", color = p.foreground, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(option.label, modifier = Modifier.weight(1f), color = p.secondary, style = MaterialTheme.typography.labelSmall)
                                if (theme == option) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            Text("字体", modifier = Modifier.padding(top = 20.dp), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFontV8.entries.forEach { option ->
                    FilterChip(selected = font == option, onClick = { onFont(option) }, label = { Text(option.label) })
                }
            }

            SettingSliderV8("字号", fontSize, 14f..30f, { it.toInt().toString() }, onFontSize)
            SettingSliderV8("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine)
            SettingSliderV8("页边距", sidePadding, 14f..44f, { "${it.toInt()} dp" }, onPadding)

            Spacer(Modifier.height(10.dp))
            MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                ReaderSheetActionV8(Icons.Rounded.Search, "全文搜索", "在整本书中查找文字", onSearch)
                HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                ReaderSheetActionV8(Icons.Rounded.AutoAwesome, "从本章进入故事", "当前章节作为原著知识边界", onStory)
                if (!isLocal) {
                    HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    ReaderSheetActionV8(Icons.Rounded.EditNote, "编辑本章", "回到创作模式修改正文", onEdit)
                }
            }
        }
    }
}

@Composable
private fun SettingSliderV8(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onValue: (Float) -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
        Text(valueText(value), modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}

@Composable
private fun ReaderSheetActionV8(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = tokens.textSecondary)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, color = tokens.textPrimary, fontWeight = FontWeight.Medium)
            Text(summary, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = tokens.textSecondary)
    }
}

@Composable
private fun DirectorySheetV8(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val tokens = LocalMiuixTokens.current
    val chapters = remember(state.chapters, query) {
        state.chapters.sortedBy { it.chapterNumber }.filter { query.isBlank() || it.title.contains(query, true) }
    }
    OverlayBottomSheet(show = true, title = "目录 · ${state.chapters.size} 章", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索章节") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "清空") } },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = tokens.cardBackground,
                    unfocusedContainerColor = tokens.cardBackground,
                ),
            )
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 590.dp).padding(top = 8.dp)) {
                items(chapters.size) { i ->
                    val item = chapters[i]
                    val selected = item.id == state.readingChapter?.id
                    Row(
                        Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(horizontal = 8.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.title.ifBlank { "第 ${item.chapterNumber} 章" },
                            modifier = Modifier.weight(1f),
                            color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBookSheetV8(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val tokens = LocalMiuixTokens.current
    val results = remember(state.chapters, query) {
        if (query.trim().length < 2) emptyList() else state.chapters.filter {
            it.title.contains(query, true) || it.content.contains(query, true)
        }.take(120)
    }
    OverlayBottomSheet(show = true, title = "全文搜索", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入两个字以上") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = tokens.cardBackground,
                    unfocusedContainerColor = tokens.cardBackground,
                ),
            )
            if (query.length >= 2) {
                Text("找到 ${results.size} 个章节", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(results.size) { i ->
                    val item = results[i]
                    Column(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(vertical = 12.dp, horizontal = 4.dp)) {
                        Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val hit = searchSnippetV8(item.content, query)
                        if (hit.isNotBlank()) Text(hit, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoSheetV8(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    OverlayBottomSheet(show = true, title = null, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(104.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${state.chapters.size} 章 · ${humanWordsV8(book.currentWords)}", modifier = Modifier.padding(top = 6.dp), color = tokens.textSecondary)
                    Text(state.readingChapter?.title ?: "第 ${state.readingChapter?.chapterNumber ?: book.currentChapter} 章", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MiuixButton(
                onClick = { onDismiss(); onOpenFull() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                cornerRadius = 18.dp,
            ) { Text("查看完整详情") }
        }
    }
}

@Composable
private fun ReaderBookInfoPageV8(
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
    val tokens = LocalMiuixTokens.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val progress = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    val fileName = meta.getString("name_${book.id}", book.title).orEmpty()
    val fileSize = meta.getLong("size_${book.id}", 0L)
    val format = meta.getString("format_${book.id}", if (isLocal) "本地" else "创作").orEmpty()
    val importedAt = meta.getLong("imported_${book.id}", 0L)
    val recent = progress.getLong("last_${book.id}", 0L)
    val current = progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))

    MiuixScaffold(
        containerColor = tokens.pageBackground,
        topBar = {
            MiuixTopAppBar(
                title = "图书详情",
                largeTitle = "图书详情",
                subtitle = if (isLocal) "本地书籍" else book.genre,
                navigationIcon = {
                    MiuixIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = tokens.textPrimary) }
                },
                actions = {
                    if (!isLocal) MiuixIconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置", tint = tokens.textPrimary) }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(108.dp).height(156.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.padding(start = 18.dp).weight(1f)) {
                    Text(book.title, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                    Text("${state.chapters.size} 章 · ${humanWordsV8(book.currentWords)}", modifier = Modifier.padding(top = 10.dp), color = tokens.textSecondary)
                    Text("当前第 $current 章", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
            }

            MiuixButton(
                onClick = onRead,
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                cornerRadius = 18.dp,
                colors = MiuixButtonDefaults.buttonColorsPrimary(),
            ) { Text("继续阅读 · 第 $current 章") }

            MiuixButton(
                onClick = onStory,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                cornerRadius = 18.dp,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("从当前章节进入故事")
            }

            SectionTitleV8("阅读信息")
            MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) {
                InfoRowV8("当前章节", "第 $current 章")
                InfoRowV8("总章节", "${state.chapters.size} 章")
                InfoRowV8("总字数", humanWordsV8(book.currentWords))
                InfoRowV8("最近阅读", if (recent > 0L) formatTimeV8(recent) else "暂无", divider = false)
            }

            if (isLocal) {
                SectionTitleV8("文件信息")
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) {
                    InfoRowV8("文件名", fileName.ifBlank { book.title })
                    InfoRowV8("文件大小", if (fileSize > 0L) humanBytesV8(fileSize) else "未知")
                    InfoRowV8("格式", format.ifBlank { "本地文件" })
                    InfoRowV8("导入时间", if (importedAt > 0L) formatTimeV8(importedAt) else "旧版本导入", divider = false)
                }
            }

            if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) {
                SectionTitleV8("简介")
                Text(book.premise, lineHeight = 24.sp, color = tokens.textSecondary)
            }

            if (!isLocal) {
                TextButton(onClick = onWriting, modifier = Modifier.padding(top = 18.dp).align(Alignment.End)) { Text("打开创作工作台") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitleV8(text: String) {
    val tokens = LocalMiuixTokens.current
    Text(text, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
}

@Composable
private fun InfoRowV8(label: String, value: String, divider: Boolean = true) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(86.dp), color = tokens.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f))
}

private data class ReaderPaletteV8(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
)

private fun readerPaletteV8(theme: ReaderThemeV8): ReaderPaletteV8 = when (theme) {
    ReaderThemeV8.SYSTEM -> ReaderPaletteV8(Color(0xFFF8F8F6), Color(0xFF202020), Color(0xFF77746F), Color(0xFFF2F2EF))
    ReaderThemeV8.PAPER -> ReaderPaletteV8(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF7B756C), Color(0xFFEDE8DB))
    ReaderThemeV8.WARM -> ReaderPaletteV8(Color(0xFFF3E5C9), Color(0xFF362D23), Color(0xFF7E6E5B), Color(0xFFEADAB9))
    ReaderThemeV8.GREEN -> ReaderPaletteV8(Color(0xFFE4ECE0), Color(0xFF283129), Color(0xFF657166), Color(0xFFD8E3D5))
    ReaderThemeV8.NIGHT -> ReaderPaletteV8(Color(0xFF171819), Color(0xFFD5D2CC), Color(0xFF8E8C87), Color(0xFF242527))
}

private fun searchSnippetV8(text: String, query: String): String {
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return text.replace('\n', ' ').take(90)
    val start = (index - 35).coerceAtLeast(0)
    val end = (index + query.length + 55).coerceAtMost(text.length)
    return text.substring(start, end).replace('\n', ' ').trim()
}

private fun humanWordsV8(words: Int): String = if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
private fun humanBytesV8(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
private fun formatTimeV8(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
