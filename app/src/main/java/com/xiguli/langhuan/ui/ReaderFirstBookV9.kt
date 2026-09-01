package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

private enum class ReaderBookRouteV9 { INFO, READER, STORY }
private enum class ReaderSettingsPageV9 { MAIN, THEME, FONT }
private enum class ReaderThemeV9(val key: String, val label: String, val summary: String) {
    SYSTEM("system", "默认", "明亮中性的系统阅读底色"),
    PAPER("paper", "纸张", "偏纸张的低对比暖白"),
    WARM("warm", "暖黄", "夜间前的温暖阅读色"),
    GREEN("green", "护眼", "柔和低饱和绿色"),
    NIGHT("night", "夜间", "深色低亮度阅读"),
}
private enum class ReaderFontV9(val key: String, val label: String, val summary: String) {
    DEFAULT("default", "系统默认", "跟随 ROM 中文字体"),
    SERIF("serif", "衬线", "更接近纸书的正文气质"),
    SANS("sans", "无衬线", "简洁、清楚、现代"),
    MONO("mono", "等宽", "字符宽度一致，适合特殊文本"),
}

@Composable
fun ReaderFirstBookV9(
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
    var route by remember(book.id) { mutableStateOf(if (isLocal) ReaderBookRouteV9.READER else ReaderBookRouteV9.INFO) }

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
        ReaderBookRouteV9.INFO -> ReaderBookInfoPageV9(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = { if (isLocal) route = ReaderBookRouteV9.READER else onBackToShelf() },
            onRead = {
                val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
                val target = state.chapters.firstOrNull { it.chapterNumber == saved } ?: state.chapters.firstOrNull()
                target?.let { openChapter(it.chapterNumber); route = ReaderBookRouteV9.READER }
            },
            onStory = { route = ReaderBookRouteV9.STORY },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )

        ReaderBookRouteV9.READER -> ReaderPageV9(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = onBackToShelf,
            onOpenInfo = { route = ReaderBookRouteV9.INFO },
            onChapter = ::openChapter,
            onStory = { route = ReaderBookRouteV9.STORY },
            onEdit = { onOpenEditor(book.id, it) },
        )

        ReaderBookRouteV9.STORY -> Box(Modifier.fillMaxSize()) {
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
                MiuixIconButton(onClick = { route = ReaderBookRouteV9.READER }) {
                    Icon(Icons.Rounded.ArrowBack, "返回阅读")
                }
            }
        }
    }
}

@Composable
private fun ReaderPageV9(
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
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", ReaderThemeV9.PAPER.key) ?: ReaderThemeV9.PAPER.key) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", ReaderFontV9.DEFAULT.key) ?: ReaderFontV9.DEFAULT.key) }
    val theme = ReaderThemeV9.entries.firstOrNull { it.key == themeKey } ?: ReaderThemeV9.PAPER
    val font = ReaderFontV9.entries.firstOrNull { it.key == fontKey } ?: ReaderFontV9.DEFAULT
    val scroll = rememberScrollState()
    val palette = readerPaletteV9(theme)
    val family = readerFontFamilyV9(font)

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
                colors = MiuixCardDefaults.defaultColors(color = palette.chrome.copy(alpha = .97f), contentColor = palette.foreground),
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
                colors = MiuixCardDefaults.defaultColors(color = palette.chrome.copy(alpha = .985f), contentColor = palette.foreground),
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
                    ReaderBottomActionV9(Icons.Rounded.Info, "详情", palette.foreground) { showInfo = true }
                    ReaderBottomActionV9(Icons.Rounded.FormatListBulleted, "目录", palette.foreground) { showDirectory = true }
                    ReaderBottomActionV9(Icons.Rounded.MoreHoriz, "更多", palette.foreground) { showMore = true }
                }
            }
        }
    }

    if (showInfo) BookInfoSheetV9(book, state, { showInfo = false }, onOpenInfo)
    if (showDirectory) DirectorySheetV9(state, { showDirectory = false }) { number -> showDirectory = false; onChapter(number) }
    if (showMore) ReaderMoreSheetV9(
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
    if (showSearch) SearchBookSheetV9(state, { showSearch = false }) { number -> showSearch = false; onChapter(number) }
}

@Composable
private fun ReaderBottomActionV9(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
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
private fun ReaderMoreSheetV9(
    theme: ReaderThemeV9,
    font: ReaderFontV9,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    isLocal: Boolean,
    onTheme: (ReaderThemeV9) -> Unit,
    onFont: (ReaderFontV9) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onSearch: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    var page by remember { mutableStateOf(ReaderSettingsPageV9.MAIN) }
    val title = when (page) {
        ReaderSettingsPageV9.MAIN -> "阅读设置"
        ReaderSettingsPageV9.THEME -> "阅读主题"
        ReaderSettingsPageV9.FONT -> "字体"
    }
    OverlayBottomSheet(
        show = true,
        title = title,
        onDismissRequest = {
            if (page == ReaderSettingsPageV9.MAIN) onDismiss() else page = ReaderSettingsPageV9.MAIN
        },
    ) {
        when (page) {
            ReaderSettingsPageV9.MAIN -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                ReaderPreviewV9(theme, font, fontSize)
                Spacer(Modifier.height(14.dp))
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                    ReaderSheetActionV9(Icons.Rounded.Palette, "阅读主题", theme.label) { page = ReaderSettingsPageV9.THEME }
                    HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    ReaderSheetActionV9(Icons.Rounded.TextFields, "字体", font.label) { page = ReaderSettingsPageV9.FONT }
                }
                SettingSliderV9("字号", fontSize, 14f..30f, { it.toInt().toString() }, onFontSize)
                SettingSliderV9("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine)
                SettingSliderV9("页边距", sidePadding, 14f..44f, { "${it.toInt()} dp" }, onPadding)
                Spacer(Modifier.height(10.dp))
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                    ReaderSheetActionV9(Icons.Rounded.Search, "全文搜索", "在整本书中查找文字", onSearch)
                    HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    ReaderSheetActionV9(Icons.Rounded.AutoAwesome, "从本章进入故事", "当前章节作为原著知识边界", onStory)
                    if (!isLocal) {
                        HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                        ReaderSheetActionV9(Icons.Rounded.EditNote, "编辑本章", "回到创作模式修改正文", onEdit)
                    }
                }
            }
            ReaderSettingsPageV9.THEME -> ThemeCenterV9(theme, onTheme)
            ReaderSettingsPageV9.FONT -> FontCenterV9(font, onFont)
        }
    }
}

@Composable
private fun ReaderPreviewV9(theme: ReaderThemeV9, font: ReaderFontV9, fontSize: Float) {
    val palette = readerPaletteV9(theme)
    val family = readerFontFamilyV9(font)
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        colors = MiuixCardDefaults.defaultColors(color = palette.background, contentColor = palette.foreground),
    ) {
        Text("阅读预览", style = MaterialTheme.typography.labelSmall, color = palette.secondary)
        Text("山色入窗，纸页在指间安静展开。", modifier = Modifier.padding(top = 10.dp), fontFamily = family, fontSize = fontSize.sp, lineHeight = (fontSize * 1.8f).sp, color = palette.foreground)
    }
}

@Composable
private fun ThemeCenterV9(selected: ReaderThemeV9, onTheme: (ReaderThemeV9) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        ReaderThemeV9.entries.forEach { option ->
            val palette = readerPaletteV9(option)
            MiuixCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                colors = MiuixCardDefaults.defaultColors(color = palette.background, contentColor = palette.foreground),
                onClick = { onTheme(option) },
                showIndication = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, fontWeight = FontWeight.SemiBold, color = palette.foreground)
                        Text(option.summary, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = palette.secondary)
                        Text("人间忽晚，山河已秋。", modifier = Modifier.padding(top = 12.dp), fontFamily = FontFamily.Serif, color = palette.foreground)
                    }
                    if (selected == option) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun FontCenterV9(selected: ReaderFontV9, onFont: (ReaderFontV9) -> Unit) {
    val tokens = LocalMiuixTokens.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("字体只影响正文排版，不改变 EPUB 原文件。", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        Spacer(Modifier.height(12.dp))
        ReaderFontV9.entries.forEach { option ->
            val family = readerFontFamilyV9(option)
            MiuixCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                onClick = { onFont(option) },
                showIndication = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                        Text(option.summary, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        Text("琅嬛书页 · ABC 123", modifier = Modifier.padding(top = 10.dp), fontFamily = family, fontSize = 18.sp, color = tokens.textPrimary)
                    }
                    if (selected == option) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SettingSliderV9(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: (Float) -> String, onValue: (Float) -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary)
        Text(valueText(value), modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}

@Composable
private fun ReaderSheetActionV9(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = tokens.textSecondary)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, color = tokens.textPrimary, fontWeight = FontWeight.Medium)
            Text(summary, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = tokens.textSecondary)
    }
}

@Composable
private fun DirectorySheetV9(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    val tokens = LocalMiuixTokens.current
    val grouped = remember(state.chapters, query, descending) {
        val groups = buildDirectoryGroupsV9(state.chapters, query)
        if (!descending) groups else groups.asReversed().map { it.copy(chapters = it.chapters.asReversed()) }
    }

    OverlayBottomSheet(show = true, title = "目录 · ${state.chapters.size} 章", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
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
                MiuixIconButton(onClick = { descending = !descending }, modifier = Modifier.padding(start = 6.dp)) {
                    Icon(if (descending) Icons.Rounded.South else Icons.Rounded.North, if (descending) "倒序" else "正序", tint = tokens.textPrimary)
                }
            }

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 590.dp).padding(top = 8.dp)) {
                grouped.forEach { group ->
                    item(key = "group_${group.title}_${group.chapters.firstOrNull()?.chapterNumber}") {
                        Text(group.title, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                    }
                    items(group.chapters.size, key = { index -> group.chapters[index].id }) { i ->
                        val item = group.chapters[i]
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
}

@Composable
private fun SearchBookSheetV9(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
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
            if (query.length >= 2) Text("找到 ${results.size} 个章节", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                items(results.size) { i ->
                    val item = results[i]
                    Column(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(vertical = 12.dp, horizontal = 4.dp)) {
                        Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val hit = searchSnippetV9(item.content, query)
                        if (hit.isNotBlank()) Text(hit, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoSheetV9(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    val context = LocalContext.current
    val tokens = LocalMiuixTokens.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val author = meta.getString("author_${book.id}", "").orEmpty()
    OverlayBottomSheet(show = true, title = null, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(104.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (author.isNotBlank()) Text(author, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    Text("${state.chapters.size} 章 · ${humanWordsV9(book.currentWords)}", modifier = Modifier.padding(top = 6.dp), color = tokens.textSecondary)
                    Text(state.readingChapter?.title ?: "第 ${state.readingChapter?.chapterNumber ?: book.currentChapter} 章", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MiuixButton(onClick = { onDismiss(); onOpenFull() }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), cornerRadius = 18.dp) { Text("查看完整详情") }
        }
    }
}

@Composable
private fun ReaderBookInfoPageV9(
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
    val author = meta.getString("author_${book.id}", "").orEmpty()
    val importedAt = meta.getLong("imported_${book.id}", 0L)
    val recent = progress.getLong("last_${book.id}", 0L)
    val current = progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
    val percent = if (state.chapters.isEmpty()) 0 else ((current.coerceIn(1, state.chapters.size).toFloat() / state.chapters.size) * 100f).roundToInt()

    MiuixScaffold(
        containerColor = tokens.pageBackground,
        topBar = {
            MiuixTopAppBar(
                title = "图书详情",
                largeTitle = "图书详情",
                subtitle = if (isLocal) "本地书籍" else book.genre,
                navigationIcon = { MiuixIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = tokens.textPrimary) } },
                actions = { if (!isLocal) MiuixIconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置", tint = tokens.textPrimary) } },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(108.dp).height(156.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.padding(start = 18.dp).weight(1f)) {
                    Text(book.title, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                    if (author.isNotBlank()) Text(author, modifier = Modifier.padding(top = 7.dp), color = tokens.textSecondary)
                    Text("${state.chapters.size} 章 · ${humanWordsV9(book.currentWords)}", modifier = Modifier.padding(top = 8.dp), color = tokens.textSecondary)
                    Text("阅读 $percent% · 第 $current 章", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                }
            }

            MiuixButton(onClick = onRead, modifier = Modifier.fillMaxWidth().padding(top = 26.dp), cornerRadius = 18.dp, colors = MiuixButtonDefaults.buttonColorsPrimary()) { Text("继续阅读 · 第 $current 章") }
            MiuixButton(onClick = onStory, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), cornerRadius = 18.dp) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("从当前章节进入故事")
            }

            SectionTitleV9("阅读信息")
            MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) {
                InfoRowV9("阅读进度", "$percent%")
                InfoRowV9("当前章节", "第 $current 章")
                InfoRowV9("总章节", "${state.chapters.size} 章")
                InfoRowV9("总字数", humanWordsV9(book.currentWords))
                InfoRowV9("最近阅读", if (recent > 0L) formatTimeV9(recent) else "暂无", divider = false)
            }

            if (isLocal) {
                SectionTitleV9("文件信息")
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) {
                    if (author.isNotBlank()) InfoRowV9("作者", author)
                    InfoRowV9("文件名", fileName.ifBlank { book.title })
                    InfoRowV9("文件大小", if (fileSize > 0L) humanBytesV9(fileSize) else "未知")
                    InfoRowV9("格式", format.ifBlank { "本地文件" })
                    InfoRowV9("导入时间", if (importedAt > 0L) formatTimeV9(importedAt) else "旧版本导入", divider = false)
                }
            }

            if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) {
                SectionTitleV9("简介")
                Text(book.premise, lineHeight = 24.sp, color = tokens.textSecondary)
            }
            if (!isLocal) TextButton(onClick = onWriting, modifier = Modifier.padding(top = 18.dp).align(Alignment.End)) { Text("打开创作工作台") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitleV9(text: String) {
    val tokens = LocalMiuixTokens.current
    Text(text, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
}

@Composable
private fun InfoRowV9(label: String, value: String, divider: Boolean = true) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.width(86.dp), color = tokens.textSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f))
}

internal data class ReaderDirectoryGroupV9(val title: String, val chapters: List<ChapterDraft>)

internal fun buildDirectoryGroupsV9(chapters: List<ChapterDraft>, query: String = ""): List<ReaderDirectoryGroupV9> {
    val key = query.trim()
    val filtered = chapters.sortedBy { it.chapterNumber }.filter {
        key.isBlank() || it.title.contains(key, true)
    }
    if (filtered.isEmpty()) return emptyList()

    val groups = linkedMapOf<String, MutableList<ChapterDraft>>()
    filtered.forEach { chapter ->
        val group = directoryVolumeLabelV9(chapter.title) ?: "正文"
        groups.getOrPut(group) { mutableListOf() } += chapter
    }
    return groups.map { ReaderDirectoryGroupV9(it.key, it.value) }
}

internal fun directoryVolumeLabelV9(title: String): String? {
    val clean = title.trim()
    val patterns = listOf(
        Regex("""^(第[零〇一二三四五六七八九十百千万两0-9]+[卷部篇])"""),
        Regex("""^([卷部篇][零〇一二三四五六七八九十百千万两0-9]+)"""),
    )
    return patterns.firstNotNullOfOrNull { it.find(clean)?.groupValues?.getOrNull(1) }
}

private data class ReaderPaletteV9(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)
private fun readerPaletteV9(theme: ReaderThemeV9): ReaderPaletteV9 = when (theme) {
    ReaderThemeV9.SYSTEM -> ReaderPaletteV9(Color(0xFFF8F8F6), Color(0xFF202020), Color(0xFF77746F), Color(0xFFF2F2EF))
    ReaderThemeV9.PAPER -> ReaderPaletteV9(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF7B756C), Color(0xFFEDE8DB))
    ReaderThemeV9.WARM -> ReaderPaletteV9(Color(0xFFF3E5C9), Color(0xFF362D23), Color(0xFF7E6E5B), Color(0xFFEADAB9))
    ReaderThemeV9.GREEN -> ReaderPaletteV9(Color(0xFFE4ECE0), Color(0xFF283129), Color(0xFF657166), Color(0xFFD8E3D5))
    ReaderThemeV9.NIGHT -> ReaderPaletteV9(Color(0xFF171819), Color(0xFFD5D2CC), Color(0xFF8E8C87), Color(0xFF242527))
}

private fun readerFontFamilyV9(font: ReaderFontV9): FontFamily = when (font) {
    ReaderFontV9.DEFAULT -> FontFamily.Default
    ReaderFontV9.SERIF -> FontFamily.Serif
    ReaderFontV9.SANS -> FontFamily.SansSerif
    ReaderFontV9.MONO -> FontFamily.Monospace
}

private fun searchSnippetV9(text: String, query: String): String {
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return text.replace('\n', ' ').take(90)
    val start = (index - 35).coerceAtLeast(0)
    val end = (index + query.length + 55).coerceAtMost(text.length)
    return text.substring(start, end).replace('\n', ' ').trim()
}

private fun humanWordsV9(words: Int): String = if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
private fun humanBytesV9(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
private fun formatTimeV9(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
