package com.xiguli.langhuan.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.xiguli.langhuan.data.EpubOriginalTocV1
import com.xiguli.langhuan.data.EpubTocNodeV1
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

private enum class ReaderBookRouteV10 { INFO, READER, STORY }
private enum class ReaderSettingsPageV10 { MAIN, THEME, FONT, PAGE_MODE }
private enum class ReaderThemeV10(val key: String, val label: String, val summary: String) {
    SYSTEM("system", "默认", "明亮中性的系统阅读底色"),
    PAPER("paper", "纸张", "偏纸张的低对比暖白"),
    WARM("warm", "暖黄", "夜间前的温暖阅读色"),
    GREEN("green", "护眼", "柔和低饱和绿色"),
    NIGHT("night", "夜间", "深色低亮度阅读"),
    CUSTOM("custom", "自定义", "使用你保存的正文与背景颜色"),
}
private enum class ReaderBuiltinFontV10(val key: String, val label: String, val summary: String) {
    DEFAULT("default", "系统默认", "跟随 ROM 中文字体"),
    SERIF("serif", "衬线", "更接近纸书的正文气质"),
    SANS("sans", "无衬线", "简洁、清楚、现代"),
    MONO("mono", "等宽", "字符宽度一致，适合特殊文本"),
}

@Composable
fun ReaderFirstBookV10(
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
    var route by remember(book.id) { mutableStateOf(if (isLocal) ReaderBookRouteV10.READER else ReaderBookRouteV10.INFO) }

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
        progressPrefs.edit().putInt("chapter_${book.id}", number).putLong("last_${book.id}", System.currentTimeMillis()).apply()
    }

    when (route) {
        ReaderBookRouteV10.INFO -> ReaderBookInfoPageV10(
            book, state, isLocal,
            onBack = { if (isLocal) route = ReaderBookRouteV10.READER else onBackToShelf() },
            onRead = {
                val saved = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
                val target = state.chapters.firstOrNull { it.chapterNumber == saved } ?: state.chapters.firstOrNull()
                target?.let { openChapter(it.chapterNumber); route = ReaderBookRouteV10.READER }
            },
            onStory = { route = ReaderBookRouteV10.STORY },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )
        ReaderBookRouteV10.READER -> ReaderPageV10(
            book, state, isLocal,
            onBack = onBackToShelf,
            onOpenInfo = { route = ReaderBookRouteV10.INFO },
            onChapter = ::openChapter,
            onStory = { route = ReaderBookRouteV10.STORY },
            onEdit = { onOpenEditor(book.id, it) },
        )
        ReaderBookRouteV10.STORY -> Box(Modifier.fillMaxSize()) {
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
                MiuixIconButton(onClick = { route = ReaderBookRouteV10.READER }) { Icon(Icons.Rounded.ArrowBack, "返回阅读") }
            }
        }
    }
}

@Composable
private fun ReaderPageV10(
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
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", ReaderThemeV10.PAPER.key) ?: ReaderThemeV10.PAPER.key) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", ReaderBuiltinFontV10.DEFAULT.key) ?: ReaderBuiltinFontV10.DEFAULT.key) }
    var pageModeKey by remember(book.id) { mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.SCROLL.key) ?: ReaderPageModeV10.SCROLL.key) }
    var customBg by remember(book.id) { mutableStateOf(prefs.getString("custom_bg_${book.id}", "#FFF4F0E6") ?: "#FFF4F0E6") }
    var customFg by remember(book.id) { mutableStateOf(prefs.getString("custom_fg_${book.id}", "#FF302D28") ?: "#FF302D28") }

    val theme = ReaderThemeV10.entries.firstOrNull { it.key == themeKey } ?: ReaderThemeV10.PAPER
    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.SCROLL
    val customTheme = remember(customBg, customFg) { customThemeV10(customBg, customFg) }
    val palette = readerPaletteV10(theme, customTheme)
    val family = remember(fontKey) { readerFontFamilyV10(fontKey) }
    val scroll = rememberScrollState()
    val pages = remember(chapter.id, chapter.content, fontSize, lineFactor, sidePadding) {
        splitReaderPagesV10(chapter.content.ifBlank { "这一章没有正文。" }, fontSize, lineFactor, sidePadding)
    }
    val pager = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(chapter.id) { scroll.scrollTo(0); pager.scrollToPage(0) }
    LaunchedEffect(fontSize, lineFactor, sidePadding, themeKey, fontKey, pageModeKey, customBg, customFg) {
        prefs.edit()
            .putFloat("font_${book.id}", fontSize)
            .putFloat("line_${book.id}", lineFactor)
            .putFloat("padding_${book.id}", sidePadding)
            .putString("theme_${book.id}", themeKey)
            .putString("family_${book.id}", fontKey)
            .putString("page_mode_${book.id}", pageModeKey)
            .putString("custom_bg_${book.id}", customBg)
            .putString("custom_fg_${book.id}", customFg)
            .apply()
    }

    fun move(offset: Int) { ordered.getOrNull(index + offset)?.let { onChapter(it.chapterNumber) } }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> SelectionContainer {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).clickable { chrome = !chrome }
                        .padding(horizontal = sidePadding.dp).padding(top = 54.dp, bottom = 84.dp),
                ) {
                    ReaderChapterHeaderV10(chapter, fontSize, family, palette)
                    Spacer(Modifier.height(28.dp))
                    Text(chapter.content.ifBlank { "这一章没有正文。" }, fontSize = fontSize.sp, lineHeight = (fontSize * lineFactor).sp, fontFamily = family, color = palette.foreground)
                    Spacer(Modifier.height(54.dp))
                    Text("${index + 1} / ${ordered.size}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                }
            }
            ReaderPageModeV10.PAGE, ReaderPageModeV10.COVER -> HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = if (pageMode == ReaderPageModeV10.COVER) 0 else 1,
            ) { page ->
                SelectionContainer {
                    Column(
                        Modifier.fillMaxSize().clickable { chrome = !chrome }
                            .padding(horizontal = sidePadding.dp).padding(top = 58.dp, bottom = 62.dp),
                    ) {
                        if (page == 0) {
                            ReaderChapterHeaderV10(chapter, fontSize, family, palette)
                            Spacer(Modifier.height(24.dp))
                        }
                        Text(pages[page], modifier = Modifier.weight(1f), fontSize = fontSize.sp, lineHeight = (fontSize * lineFactor).sp, fontFamily = family, color = palette.foreground)
                        Text("${page + 1}/${pages.size} · 第 ${chapter.chapterNumber} 章", Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                    }
                }
            }
        }

        if (!chrome) {
            val pageText = if (pageMode == ReaderPageModeV10.SCROLL) "${index + 1} / ${ordered.size}" else "${pager.currentPage + 1}/${pages.size}"
            Text(pageText, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .74f))
        }

        if (chrome) {
            ReaderChromeV10(
                chapter = chapter,
                index = index,
                total = ordered.size,
                palette = palette,
                onBack = onBack,
                onPrev = { move(-1) },
                onNext = { move(1) },
                onDirectory = { showDirectory = true },
                onStory = onStory,
                onInfo = { showInfo = true },
                onMore = { showMore = true },
            )
        }
    }

    if (showInfo) BookInfoSheetV10(book, state, { showInfo = false }, onOpenInfo)
    if (showDirectory) DirectorySheetV10(book.id, state, { showDirectory = false }) { number -> showDirectory = false; onChapter(number) }
    if (showMore) ReaderMoreSheetV10(
        bookId = book.id,
        theme = theme,
        fontKey = fontKey,
        pageMode = pageMode,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        customBg = customBg,
        customFg = customFg,
        isLocal = isLocal,
        onTheme = { themeKey = it.key },
        onFont = { fontKey = it },
        onPageMode = { pageModeKey = it.key },
        onFontSize = { fontSize = it },
        onLine = { lineFactor = it },
        onPadding = { sidePadding = it },
        onCustomBg = { customBg = it; themeKey = ReaderThemeV10.CUSTOM.key },
        onCustomFg = { customFg = it; themeKey = ReaderThemeV10.CUSTOM.key },
        onSearch = { showMore = false; showSearch = true },
        onStory = { showMore = false; onStory() },
        onEdit = { showMore = false; onEdit(chapter.chapterNumber) },
        onDismiss = { showMore = false },
    )
    if (showSearch) SearchBookSheetV10(state, { showSearch = false }) { number -> showSearch = false; onChapter(number) }
}

@Composable
private fun ReaderChapterHeaderV10(chapter: ChapterDraft, fontSize: Float, family: FontFamily, palette: ReaderPaletteV10) {
    Text(chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" }, fontSize = (fontSize + 5).sp, lineHeight = (fontSize + 11).sp, fontWeight = FontWeight.SemiBold, fontFamily = family, color = palette.foreground)
}

@Composable
private fun BoxScope.ReaderChromeV10(
    chapter: ChapterDraft,
    index: Int,
    total: Int,
    palette: ReaderPaletteV10,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDirectory: () -> Unit,
    onStory: () -> Unit,
    onInfo: () -> Unit,
    onMore: () -> Unit,
) {
    MiuixCard(
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 7.dp).fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = MiuixCardDefaults.defaultColors(color = palette.chrome.copy(alpha = .97f), contentColor = palette.foreground),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            MiuixIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = palette.foreground) }
            Text(chapter.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = palette.foreground)
            MiuixIconButton(onClick = onDirectory) { Icon(Icons.Rounded.FormatListBulleted, "目录", tint = palette.foreground) }
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
            TextButton(onPrev, enabled = index > 0) { Text("上一章", color = if (index > 0) palette.foreground else palette.secondary) }
            Text("第 ${chapter.chapterNumber} 章 · ${index + 1}/$total", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
            TextButton(onNext, enabled = index < total - 1) { Text("下一章", color = if (index < total - 1) palette.foreground else palette.secondary) }
        }
        HorizontalDivider(color = palette.secondary.copy(alpha = .13f))
        Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ReaderBottomActionV10(Icons.Rounded.Info, "详情", palette.foreground, onInfo)
            ReaderBottomActionV10(Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
            ReaderBottomActionV10(Icons.Rounded.MoreHoriz, "更多", palette.foreground, onMore)
        }
    }
}

@Composable
private fun ReaderBottomActionV10(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(90.dp).fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ReaderMoreSheetV10(
    bookId: String,
    theme: ReaderThemeV10,
    fontKey: String,
    pageMode: ReaderPageModeV10,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    customBg: String,
    customFg: String,
    isLocal: Boolean,
    onTheme: (ReaderThemeV10) -> Unit,
    onFont: (String) -> Unit,
    onPageMode: (ReaderPageModeV10) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCustomBg: (String) -> Unit,
    onCustomFg: (String) -> Unit,
    onSearch: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(ReaderSettingsPageV10.MAIN) }
    val title = when (page) {
        ReaderSettingsPageV10.MAIN -> "阅读设置"
        ReaderSettingsPageV10.THEME -> "阅读主题"
        ReaderSettingsPageV10.FONT -> "字体"
        ReaderSettingsPageV10.PAGE_MODE -> "翻页方式"
    }
    OverlayBottomSheet(show = true, title = title, onDismissRequest = { if (page == ReaderSettingsPageV10.MAIN) onDismiss() else page = ReaderSettingsPageV10.MAIN }) {
        when (page) {
            ReaderSettingsPageV10.MAIN -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                ReaderPreviewV10(theme, fontKey, fontSize, customThemeV10(customBg, customFg))
                Spacer(Modifier.height(14.dp))
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                    ReaderSheetActionV10(Icons.Rounded.Palette, "阅读主题", theme.label) { page = ReaderSettingsPageV10.THEME }
                    DividerInsetV10()
                    ReaderSheetActionV10(Icons.Rounded.TextFields, "字体", readerFontLabelV10(fontKey)) { page = ReaderSettingsPageV10.FONT }
                    DividerInsetV10()
                    ReaderSheetActionV10(Icons.Rounded.Swipe, "翻页方式", pageMode.label) { page = ReaderSettingsPageV10.PAGE_MODE }
                }
                SettingSliderV10("字号", fontSize, 14f..30f, { it.toInt().toString() }, onFontSize)
                SettingSliderV10("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine)
                SettingSliderV10("页边距", sidePadding, 14f..44f, { "${it.toInt()} dp" }, onPadding)
                Spacer(Modifier.height(10.dp))
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                    ReaderSheetActionV10(Icons.Rounded.Search, "全文搜索", "在整本书中查找文字", onSearch)
                    DividerInsetV10()
                    ReaderSheetActionV10(Icons.Rounded.AutoAwesome, "从本章进入故事", "当前章节作为原著知识边界", onStory)
                    if (!isLocal) { DividerInsetV10(); ReaderSheetActionV10(Icons.Rounded.EditNote, "编辑本章", "回到创作模式修改正文", onEdit) }
                }
            }
            ReaderSettingsPageV10.THEME -> ThemeCenterV10(theme, customBg, customFg, onTheme, onCustomBg, onCustomFg)
            ReaderSettingsPageV10.FONT -> FontCenterV10(bookId, fontKey, onFont)
            ReaderSettingsPageV10.PAGE_MODE -> PageModeCenterV10(pageMode, onPageMode)
        }
    }
}

@Composable
private fun ReaderPreviewV10(theme: ReaderThemeV10, fontKey: String, fontSize: Float, custom: ReaderCustomThemeV10) {
    val palette = readerPaletteV10(theme, custom)
    MiuixCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp), colors = MiuixCardDefaults.defaultColors(color = palette.background, contentColor = palette.foreground)) {
        Text("阅读预览", style = MaterialTheme.typography.labelSmall, color = palette.secondary)
        Text("山色入窗，纸页在指间安静展开。", Modifier.padding(top = 10.dp), fontFamily = readerFontFamilyV10(fontKey), fontSize = fontSize.sp, lineHeight = (fontSize * 1.8f).sp, color = palette.foreground)
    }
}

@Composable
private fun ThemeCenterV10(selected: ReaderThemeV10, customBg: String, customFg: String, onTheme: (ReaderThemeV10) -> Unit, onCustomBg: (String) -> Unit, onCustomFg: (String) -> Unit) {
    val custom = customThemeV10(customBg, customFg)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        ReaderThemeV10.entries.filter { it != ReaderThemeV10.CUSTOM }.forEach { option ->
            ThemeRowV10(option, readerPaletteV10(option, custom), selected == option) { onTheme(option) }
        }
        ThemeRowV10(ReaderThemeV10.CUSTOM, readerPaletteV10(ReaderThemeV10.CUSTOM, custom), selected == ReaderThemeV10.CUSTOM) { onTheme(ReaderThemeV10.CUSTOM) }
        Text("自定义颜色", Modifier.padding(top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(customBg, onValueChange = onCustomBg, modifier = Modifier.fillMaxWidth(), label = { Text("背景色") }, placeholder = { Text("#FFF4F0E6") }, singleLine = true)
        OutlinedTextField(customFg, onValueChange = onCustomFg, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("正文色") }, placeholder = { Text("#FF302D28") }, singleLine = true)
        Text("支持 #RRGGBB 或 #AARRGGBB，输入有效颜色后会立即预览。", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
    }
}

@Composable
private fun ThemeRowV10(option: ReaderThemeV10, palette: ReaderPaletteV10, selected: Boolean, onClick: () -> Unit) {
    MiuixCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), cornerRadius = 20.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp), colors = MiuixCardDefaults.defaultColors(color = palette.background, contentColor = palette.foreground), onClick = onClick, showIndication = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(option.label, fontWeight = FontWeight.SemiBold, color = palette.foreground); Text(option.summary, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = palette.secondary); Text("人间忽晚，山河已秋。", Modifier.padding(top = 12.dp), fontFamily = FontFamily.Serif, color = palette.foreground) }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FontCenterV10(bookId: String, selected: String, onFont: (String) -> Unit) {
    val context = LocalContext.current
    val tokens = LocalMiuixTokens.current
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val assets = remember(refresh) { ReaderFontStoreV10.list(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ReaderFontStoreV10.import(context, uri).onSuccess { asset -> onFont("custom:${asset.path}"); refresh++ ; message = "已导入 ${asset.name}" }.onFailure { message = it.message ?: "字体导入失败" }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text("支持从手机导入 TTF / OTF。字体复制到琅嬛私有目录，不修改原文件。", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        MiuixButton(onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "*/*")) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), cornerRadius = 18.dp) {
            Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("导入字体")
        }
        message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
        Spacer(Modifier.height(12.dp))
        ReaderBuiltinFontV10.entries.forEach { option -> FontChoiceRowV10(option.label, option.summary, readerBuiltinFamilyV10(option), selected == option.key) { onFont(option.key) } }
        if (assets.isNotEmpty()) Text("已导入", Modifier.padding(top = 14.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
        assets.forEach { asset ->
            FontChoiceRowV10(asset.name, asset.path.substringAfterLast('/'), ReaderFontStoreV10.family(asset.path) ?: FontFamily.Default, selected == "custom:${asset.path}") { onFont("custom:${asset.path}") }
        }
    }
}

@Composable
private fun FontChoiceRowV10(label: String, summary: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    MiuixCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick = onClick, showIndication = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary); Text(summary, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary); Text("琅嬛书页 · ABC 123", Modifier.padding(top = 10.dp), fontFamily = family, fontSize = 18.sp, color = tokens.textPrimary) }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PageModeCenterV10(selected: ReaderPageModeV10, onSelect: (ReaderPageModeV10) -> Unit) {
    val tokens = LocalMiuixTokens.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        ReaderPageModeV10.entries.forEach { mode ->
            MiuixCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp), onClick = { onSelect(mode) }, showIndication = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (mode) { ReaderPageModeV10.SCROLL -> Icons.Rounded.SwapVert; ReaderPageModeV10.PAGE -> Icons.Rounded.Swipe; ReaderPageModeV10.COVER -> Icons.Rounded.Layers }, null, tint = tokens.textSecondary)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) { Text(mode.label, fontWeight = FontWeight.SemiBold); Text(mode.summary, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
                    if (selected == mode) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SettingSliderV10(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: (Float) -> String, onValue: (Float) -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary); Text(valueText(value), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}

@Composable
private fun ReaderSheetActionV10(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = tokens.textSecondary)
        Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(title, color = tokens.textPrimary, fontWeight = FontWeight.Medium); Text(summary, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = tokens.textSecondary)
    }
}

@Composable private fun DividerInsetV10() = HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))

private data class TocDisplayEntryV10(val title: String, val chapterNumber: Int?, val depth: Int)
private fun flattenTocV10(nodes: List<EpubTocNodeV1>, depth: Int = 0): List<TocDisplayEntryV10> = buildList {
    nodes.forEach { node -> add(TocDisplayEntryV10(node.title, node.chapterNumber, depth)); addAll(flattenTocV10(node.children, depth + 1)) }
}

@Composable
private fun DirectorySheetV10(bookId: String, state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    val tokens = LocalMiuixTokens.current
    val original = remember(bookId) { EpubOriginalTocV1.load(context, bookId) }
    val originalEntries = remember(original, query, descending) {
        val flat = flattenTocV10(original).filter { query.isBlank() || it.title.contains(query, true) }
        if (descending) flat.asReversed() else flat
    }
    val fallback = remember(state.chapters, query, descending) {
        val groups = buildDirectoryGroupsV9(state.chapters, query)
        if (!descending) groups else groups.asReversed().map { it.copy(chapters = it.chapters.asReversed()) }
    }
    OverlayBottomSheet(show = true, title = if (original.isNotEmpty()) "原书目录 · ${state.chapters.size} 章" else "目录 · ${state.chapters.size} 章", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索章节") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = tokens.cardBackground, unfocusedContainerColor = tokens.cardBackground))
                MiuixIconButton(onClick = { descending = !descending }, modifier = Modifier.padding(start = 6.dp)) { Icon(if (descending) Icons.Rounded.South else Icons.Rounded.North, if (descending) "倒序" else "正序", tint = tokens.textPrimary) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 590.dp).padding(top = 8.dp)) {
                if (original.isNotEmpty()) {
                    items(originalEntries.size, key = { i -> "toc_${i}_${originalEntries[i].title}" }) { i ->
                        val item = originalEntries[i]
                        val selected = item.chapterNumber == state.readingChapter?.chapterNumber
                        Row(Modifier.fillMaxWidth().then(if (item.chapterNumber != null) Modifier.clickable { onChapter(item.chapterNumber) } else Modifier).padding(start = (8 + item.depth * 18).dp, end = 8.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (item.chapterNumber == null) Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(17.dp), tint = tokens.textSecondary)
                            Text(item.title, Modifier.padding(start = if (item.chapterNumber == null) 4.dp else 0.dp).weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary, fontWeight = if (item.chapterNumber == null || selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    fallback.forEach { group ->
                        item { Text(group.title, Modifier.padding(horizontal = 8.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary) }
                        items(group.chapters.size, key = { index -> group.chapters[index].id }) { i ->
                            val item = group.chapters[i]; val selected = item.id == state.readingChapter?.id
                            Row(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(horizontal = 8.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis); if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBookSheetV10(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }; val tokens = LocalMiuixTokens.current
    val results = remember(state.chapters, query) { if (query.trim().length < 2) emptyList() else state.chapters.filter { it.title.contains(query, true) || it.content.contains(query, true) }.take(120) }
    OverlayBottomSheet(show = true, title = "全文搜索", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            TextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("输入两个字以上") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = tokens.cardBackground, unfocusedContainerColor = tokens.cardBackground))
            if (query.length >= 2) Text("找到 ${results.size} 个章节", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) { items(results.size) { i -> val item = results[i]; Column(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(vertical = 12.dp, horizontal = 4.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); val hit = searchSnippetV10(item.content, query); if (hit.isNotBlank()) Text(hit, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
        }
    }
}

@Composable
private fun BookInfoSheetV10(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    val context = LocalContext.current; val tokens = LocalMiuixTokens.current; val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }; val author = meta.getString("author_${book.id}", "").orEmpty()
    OverlayBottomSheet(show = true, title = null, onDismissRequest = onDismiss) { Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(104.dp).clip(RoundedCornerShape(10.dp))); Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); if (author.isNotBlank()) Text(author, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary); Text("${state.chapters.size} 章 · ${humanWordsV10(book.currentWords)}", Modifier.padding(top = 6.dp), color = tokens.textSecondary) } }; MiuixButton(onClick = { onDismiss(); onOpenFull() }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), cornerRadius = 18.dp) { Text("查看完整详情") } } }
}

@Composable
private fun ReaderBookInfoPageV10(book: ReaderBookUi, state: LibraryExperienceState, isLocal: Boolean, onBack: () -> Unit, onRead: () -> Unit, onStory: () -> Unit, onWriting: () -> Unit, onAiSetup: () -> Unit) {
    val context = LocalContext.current; val tokens = LocalMiuixTokens.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }; val progress = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    val fileName = meta.getString("name_${book.id}", book.title).orEmpty(); val fileSize = meta.getLong("size_${book.id}", 0L); val format = meta.getString("format_${book.id}", if (isLocal) "本地" else "创作").orEmpty(); val author = meta.getString("author_${book.id}", "").orEmpty(); val importedAt = meta.getLong("imported_${book.id}", 0L); val recent = progress.getLong("last_${book.id}", 0L); val current = progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1)); val percent = if (state.chapters.isEmpty()) 0 else ((current.coerceIn(1, state.chapters.size).toFloat() / state.chapters.size) * 100f).roundToInt()
    MiuixScaffold(containerColor = tokens.pageBackground, topBar = { MiuixTopAppBar(title = "图书详情", largeTitle = "图书详情", subtitle = if (isLocal) "本地书籍" else book.genre, navigationIcon = { MiuixIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = tokens.textPrimary) } }, actions = { if (!isLocal) MiuixIconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置", tint = tokens.textPrimary) } }) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) { CoverPreviewV3(book.coverPath, book.title, Modifier.width(108.dp).height(156.dp).clip(RoundedCornerShape(14.dp))); Column(Modifier.padding(start = 18.dp).weight(1f)) { Text(book.title, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary); if (author.isNotBlank()) Text(author, Modifier.padding(top = 7.dp), color = tokens.textSecondary); Text("${state.chapters.size} 章 · ${humanWordsV10(book.currentWords)}", Modifier.padding(top = 8.dp), color = tokens.textSecondary); Text("阅读 $percent% · 第 $current 章", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) } }
            MiuixButton(onClick = onRead, modifier = Modifier.fillMaxWidth().padding(top = 26.dp), cornerRadius = 18.dp, colors = MiuixButtonDefaults.buttonColorsPrimary()) { Text("继续阅读 · 第 $current 章") }
            MiuixButton(onClick = onStory, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), cornerRadius = 18.dp) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("从当前章节进入故事") }
            SectionTitleV10("阅读信息"); MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) { InfoRowV10("阅读进度", "$percent%"); InfoRowV10("当前章节", "第 $current 章"); InfoRowV10("总章节", "${state.chapters.size} 章"); InfoRowV10("总字数", humanWordsV10(book.currentWords)); InfoRowV10("最近阅读", if (recent > 0L) formatTimeV10(recent) else "暂无", false) }
            if (isLocal) { SectionTitleV10("文件信息"); MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) { if (author.isNotBlank()) InfoRowV10("作者", author); InfoRowV10("文件名", fileName.ifBlank { book.title }); InfoRowV10("文件大小", if (fileSize > 0L) humanBytesV10(fileSize) else "未知"); InfoRowV10("格式", format.ifBlank { "本地文件" }); InfoRowV10("导入时间", if (importedAt > 0L) formatTimeV10(importedAt) else "旧版本导入", false) } }
            if (!isLocal) TextButton(onClick = onWriting, modifier = Modifier.padding(top = 18.dp).align(Alignment.End)) { Text("打开创作工作台") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable private fun SectionTitleV10(text: String) { val t = LocalMiuixTokens.current; Text(text, Modifier.padding(top = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.textPrimary) }
@Composable private fun InfoRowV10(label: String, value: String, divider: Boolean = true) { val t = LocalMiuixTokens.current; Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) { Text(label, Modifier.width(86.dp), color = t.textSecondary, style = MaterialTheme.typography.bodyMedium); Text(value, Modifier.weight(1f), textAlign = TextAlign.End, color = t.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }; if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f)) }

private data class ReaderPaletteV10(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)
private fun customThemeV10(bg: String, fg: String): ReaderCustomThemeV10 { val b = parseReaderHexColorV10(bg) ?: Color(0xFFF4F0E6); val f = parseReaderHexColorV10(fg) ?: Color(0xFF302D28); return ReaderCustomThemeV10(b, f, f.copy(alpha = .58f), blendV10(b, f, .06f)) }
private fun readerPaletteV10(theme: ReaderThemeV10, custom: ReaderCustomThemeV10): ReaderPaletteV10 = when (theme) { ReaderThemeV10.SYSTEM -> ReaderPaletteV10(Color(0xFFF8F8F6), Color(0xFF202020), Color(0xFF77746F), Color(0xFFF2F2EF)); ReaderThemeV10.PAPER -> ReaderPaletteV10(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF7B756C), Color(0xFFEDE8DB)); ReaderThemeV10.WARM -> ReaderPaletteV10(Color(0xFFF3E5C9), Color(0xFF362D23), Color(0xFF7E6E5B), Color(0xFFEADAB9)); ReaderThemeV10.GREEN -> ReaderPaletteV10(Color(0xFFE4ECE0), Color(0xFF283129), Color(0xFF657166), Color(0xFFD8E3D5)); ReaderThemeV10.NIGHT -> ReaderPaletteV10(Color(0xFF171819), Color(0xFFD5D2CC), Color(0xFF8E8C87), Color(0xFF242527)); ReaderThemeV10.CUSTOM -> ReaderPaletteV10(custom.background, custom.foreground, custom.secondary, custom.chrome) }
private fun blendV10(a: Color, b: Color, amount: Float) = Color(red = a.red * (1 - amount) + b.red * amount, green = a.green * (1 - amount) + b.green * amount, blue = a.blue * (1 - amount) + b.blue * amount, alpha = 1f)
private fun readerBuiltinFamilyV10(font: ReaderBuiltinFontV10): FontFamily = when (font) { ReaderBuiltinFontV10.DEFAULT -> FontFamily.Default; ReaderBuiltinFontV10.SERIF -> FontFamily.Serif; ReaderBuiltinFontV10.SANS -> FontFamily.SansSerif; ReaderBuiltinFontV10.MONO -> FontFamily.Monospace }
private fun readerFontFamilyV10(key: String): FontFamily { if (key.startsWith("custom:")) return ReaderFontStoreV10.family(key.removePrefix("custom:")) ?: FontFamily.Default; return readerBuiltinFamilyV10(ReaderBuiltinFontV10.entries.firstOrNull { it.key == key } ?: ReaderBuiltinFontV10.DEFAULT) }
private fun readerFontLabelV10(key: String): String = if (key.startsWith("custom:")) key.removePrefix("custom:").substringAfterLast('/').substringBeforeLast('.') else ReaderBuiltinFontV10.entries.firstOrNull { it.key == key }?.label ?: "系统默认"
private fun searchSnippetV10(text: String, query: String): String { val i = text.indexOf(query, ignoreCase = true); if (i < 0) return text.replace('\n', ' ').take(90); val s = (i - 35).coerceAtLeast(0); val e = (i + query.length + 55).coerceAtMost(text.length); return text.substring(s, e).replace('\n', ' ').trim() }
private fun humanWordsV10(words: Int) = if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
private fun humanBytesV10(bytes: Long) = when { bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f); bytes >= 1024L -> "%.1f KB".format(bytes / 1024f); else -> "$bytes B" }
private fun formatTimeV10(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
