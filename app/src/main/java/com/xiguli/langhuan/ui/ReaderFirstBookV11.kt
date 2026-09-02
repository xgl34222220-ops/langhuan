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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

private enum class ReaderBookRouteV11 { INFO, READER, STORY }
private enum class ReaderToolPageV11 { HOME, BOOKMARKS, NOTES, DISPLAY, FONTS, PRESETS }
private enum class ReaderThemeV11(val key: String, val label: String) {
    SYSTEM("system", "默认"), PAPER("paper", "纸张"), WARM("warm", "暖黄"), GREEN("green", "护眼"), NIGHT("night", "夜间"), CUSTOM("custom", "自定义")
}
private enum class ReaderBuiltinFontV11(val key: String, val label: String) {
    DEFAULT("default", "系统默认"), SERIF("serif", "衬线"), SANS("sans", "无衬线"), MONO("mono", "等宽")
}
private data class ReaderPaletteV11(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)
private data class TocDisplayEntryV11(val title: String, val chapterNumber: Int?, val depth: Int)

@Composable
fun ReaderFirstBookV11(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val book = state.openedBook ?: return
    val context = LocalContext.current
    val isLocal = book.genre == "导入作品"
    var route by remember(book.id) { mutableStateOf(if (startOnInfo || !isLocal) ReaderBookRouteV11.INFO else ReaderBookRouteV11.READER) }

    LaunchedEffect(book.id, state.chapters.size) {
        if (state.chapters.isNotEmpty() && state.readingChapter == null) {
            val saved = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
            val target = state.chapters.firstOrNull { it.chapterNumber == saved.chapterNumber }
                ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                ?: state.chapters.first()
            viewModel.openReader(target.chapterNumber)
        }
    }

    fun openChapter(number: Int, resetPosition: Boolean = true) {
        if (resetPosition) ReaderProgressStoreV11.moveTo(context, book.id, number, 0, 0, currentPageModeKeyV11(context, book.id))
        viewModel.openReader(number)
    }

    when (route) {
        ReaderBookRouteV11.INFO -> ReaderBookInfoPageV11(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = { if (isLocal) route = ReaderBookRouteV11.READER else onBackToShelf() },
            onRead = {
                val saved = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
                openChapter(saved.chapterNumber, resetPosition = false)
                route = ReaderBookRouteV11.READER
            },
            onStory = { route = ReaderBookRouteV11.STORY },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )
        ReaderBookRouteV11.READER -> ReaderPageV11(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = onBackToShelf,
            onOpenInfo = { route = ReaderBookRouteV11.INFO },
            onChapter = { openChapter(it, resetPosition = true) },
            onJump = { chapter, page, scroll, mode ->
                ReaderProgressStoreV11.moveTo(context, book.id, chapter, page, scroll, mode.key)
                openChapter(chapter, resetPosition = false)
            },
            onStory = { route = ReaderBookRouteV11.STORY },
            onEdit = { onOpenEditor(book.id, it) },
        )
        ReaderBookRouteV11.STORY -> Box(Modifier.fillMaxSize()) {
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
                MiuixIconButton(onClick = { route = ReaderBookRouteV11.READER }) { Icon(Icons.Rounded.ArrowBack, "返回阅读") }
            }
        }
    }
}

@Composable
private fun ReaderPageV11(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    isLocal: Boolean,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onChapter: (Int) -> Unit,
    onJump: (Int, Int, Int, ReaderPageModeV10) -> Unit,
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
    var showDirectory by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var archive by remember(book.id) { mutableStateOf(ReaderReadingStoreV11.load(context, book.id)) }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 19f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.82f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "system") ?: "system") }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "default") ?: "default") }
    var pageModeKey by remember(book.id) { mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.SCROLL.key) ?: ReaderPageModeV10.SCROLL.key) }
    var customBg by remember(book.id) { mutableStateOf(prefs.getString("custom_bg_${book.id}", "#FFF4F0E6") ?: "#FFF4F0E6") }
    var customFg by remember(book.id) { mutableStateOf(prefs.getString("custom_fg_${book.id}", "#FF302D28") ?: "#FF302D28") }

    val theme = ReaderThemeV11.entries.firstOrNull { it.key == themeKey } ?: ReaderThemeV11.PAPER
    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.SCROLL
    val palette = readerPaletteV11(theme, customBg, customFg)
    val family = remember(fontKey) { readerFontFamilyV11(fontKey) }
    val scroll = rememberScrollState()
    val pages = remember(chapter.id, chapter.content, fontSize, lineFactor, sidePadding) {
        splitReaderPagesV10(chapter.content.ifBlank { "这一章没有正文。" }, fontSize, lineFactor, sidePadding)
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val savedProgress = remember(chapter.id, pageModeKey) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }

    LaunchedEffect(chapter.id, pageModeKey, pages.size) {
        if (savedProgress.chapterNumber == chapter.chapterNumber && pageMode != ReaderPageModeV10.SCROLL) {
            pager.scrollToPage(savedProgress.pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))
        }
    }
    LaunchedEffect(chapter.id, pageModeKey, scroll.maxValue) {
        if (pageMode == ReaderPageModeV10.SCROLL && savedProgress.chapterNumber == chapter.chapterNumber && scroll.maxValue > 0) {
            scroll.scrollTo(savedProgress.scrollY.coerceIn(0, scroll.maxValue))
        }
    }
    LaunchedEffect(chapter.id, pageModeKey, scroll) {
        snapshotFlow { scroll.value }.distinctUntilChanged().collect { y ->
            if (pageMode == ReaderPageModeV10.SCROLL) ReaderProgressStoreV11.save(
                context,
                book.id,
                ReaderProgressV11(chapter.chapterNumber, 0, y, pageMode.key),
            )
        }
    }
    LaunchedEffect(chapter.id, pageModeKey, pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            if (pageMode != ReaderPageModeV10.SCROLL) ReaderProgressStoreV11.save(
                context,
                book.id,
                ReaderProgressV11(chapter.chapterNumber, page, 0, pageMode.key),
            )
        }
    }
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
    fun currentPageIndex(): Int = if (pageMode == ReaderPageModeV10.SCROLL) 0 else pager.currentPage
    fun currentScrollY(): Int = if (pageMode == ReaderPageModeV10.SCROLL) scroll.value else 0
    fun currentProgressFraction(): Float = if (pageMode == ReaderPageModeV10.SCROLL) {
        if (scroll.maxValue <= 0) 0f else scroll.value.toFloat() / scroll.maxValue.toFloat()
    } else {
        if (pages.size <= 1) 0f else pager.currentPage.toFloat() / (pages.size - 1).toFloat()
    }
    fun toggleBookmark() {
        archive = ReaderReadingStoreV11.addBookmark(
            context, book.id, chapter.chapterNumber, currentPageIndex(), currentScrollY(),
            chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
            readerExcerptAtV11(chapter.content, currentProgressFraction()),
        )
    }

    val bookmarked = archive.bookmarks.any {
        it.chapterNumber == chapter.chapterNumber && it.pageIndex == currentPageIndex() && kotlin.math.abs(it.scrollY - currentScrollY()) <= 80
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> SelectionContainer {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).clickable { chrome = !chrome }
                        .padding(horizontal = sidePadding.dp).padding(top = 54.dp, bottom = 92.dp),
                ) {
                    ReaderChapterHeaderV11(chapter, fontSize, family, palette)
                    Spacer(Modifier.height(28.dp))
                    Text(chapter.content.ifBlank { "这一章没有正文。" }, fontSize = fontSize.sp, lineHeight = (fontSize * lineFactor).sp, fontFamily = family, color = palette.foreground)
                    Spacer(Modifier.height(54.dp))
                    Text("${index + 1} / ${ordered.size}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                }
            }
            ReaderPageModeV10.PAGE, ReaderPageModeV10.COVER -> HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = if (pageMode == ReaderPageModeV10.COVER) 12.dp else 0.dp,
                beyondViewportPageCount = if (pageMode == ReaderPageModeV10.COVER) 0 else 1,
            ) { page ->
                SelectionContainer {
                    MiuixCard(
                        modifier = Modifier.fillMaxSize().padding(if (pageMode == ReaderPageModeV10.COVER) 4.dp else 0.dp).clickable { chrome = !chrome },
                        cornerRadius = if (pageMode == ReaderPageModeV10.COVER) 14.dp else 0.dp,
                        insideMargin = PaddingValues(horizontal = sidePadding.dp, vertical = 0.dp),
                        colors = MiuixCardDefaults.defaultColors(color = palette.background, contentColor = palette.foreground),
                    ) {
                        Column(Modifier.fillMaxSize().padding(top = 58.dp, bottom = 62.dp)) {
                            if (page == 0) { ReaderChapterHeaderV11(chapter, fontSize, family, palette); Spacer(Modifier.height(24.dp)) }
                            Text(pages[page], modifier = Modifier.weight(1f), fontSize = fontSize.sp, lineHeight = (fontSize * lineFactor).sp, fontFamily = family, color = palette.foreground)
                            Text("${page + 1}/${pages.size} · 第 ${chapter.chapterNumber} 章", Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                        }
                    }
                }
            }
        }

        if (!chrome) {
            val pageText = if (pageMode == ReaderPageModeV10.SCROLL) "${index + 1} / ${ordered.size}" else "${pager.currentPage + 1}/${pages.size}"
            Text(pageText, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .72f))
        }
        if (chrome) ReaderChromeV11(
            chapter = chapter, index = index, total = ordered.size, palette = palette, bookmarked = bookmarked,
            onBack = onBack, onPrev = { move(-1) }, onNext = { move(1) }, onBookmark = ::toggleBookmark,
            onDirectory = { showDirectory = true }, onInfo = { showInfo = true }, onTools = { showTools = true }, onStory = onStory,
        )
    }

    if (showDirectory) DirectorySheetV11(book.id, state, { showDirectory = false }) { showDirectory = false; onChapter(it) }
    if (showInfo) BookInfoSheetV11(book, state, { showInfo = false }, onOpenInfo)
    if (showSearch) SearchBookSheetV11(state, { showSearch = false }) { showSearch = false; onChapter(it) }
    if (showTools) ReaderToolsSheetV11(
        bookId = book.id,
        chapter = chapter,
        archive = archive,
        pageMode = pageMode,
        pageIndex = currentPageIndex(),
        scrollY = currentScrollY(),
        excerpt = readerExcerptAtV11(chapter.content, currentProgressFraction()),
        themeKey = themeKey,
        fontKey = fontKey,
        pageModeKey = pageModeKey,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        customBg = customBg,
        customFg = customFg,
        isLocal = isLocal,
        onArchive = { archive = it },
        onJump = { c, p, s, m -> showTools = false; onJump(c, p, s, m) },
        onTheme = { themeKey = it },
        onFont = { fontKey = it },
        onPageMode = { pageModeKey = it },
        onFontSize = { fontSize = it },
        onLine = { lineFactor = it },
        onPadding = { sidePadding = it },
        onCustomBg = { customBg = it; themeKey = ReaderThemeV11.CUSTOM.key },
        onCustomFg = { customFg = it; themeKey = ReaderThemeV11.CUSTOM.key },
        onApplyPreset = { preset ->
            ReaderReadingStoreV11.applyPreset(context, book.id, preset)
            themeKey = preset.themeKey; fontKey = preset.fontKey; pageModeKey = preset.pageModeKey
            fontSize = preset.fontSize; lineFactor = preset.lineFactor; sidePadding = preset.sidePadding
            customBg = preset.customBg; customFg = preset.customFg
        },
        onSearch = { showTools = false; showSearch = true },
        onStory = { showTools = false; onStory() },
        onEdit = { showTools = false; onEdit(chapter.chapterNumber) },
        onDismiss = { showTools = false },
    )
}

@Composable
private fun ReaderChapterHeaderV11(chapter: ChapterDraft, fontSize: Float, family: FontFamily, palette: ReaderPaletteV11) {
    Text(chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" }, fontSize = (fontSize + 5).sp, lineHeight = (fontSize + 11).sp, fontWeight = FontWeight.SemiBold, fontFamily = family, color = palette.foreground)
}

@Composable
private fun BoxScope.ReaderChromeV11(
    chapter: ChapterDraft,
    index: Int,
    total: Int,
    palette: ReaderPaletteV11,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onBookmark: () -> Unit,
    onDirectory: () -> Unit,
    onInfo: () -> Unit,
    onTools: () -> Unit,
    onStory: () -> Unit,
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
            MiuixIconButton(onClick = onBookmark) { Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "书签", tint = palette.foreground) }
            MiuixIconButton(onClick = onDirectory) { Icon(Icons.Rounded.FormatListBulleted, "目录", tint = palette.foreground) }
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
            ReaderBottomActionV11(Icons.Rounded.Info, "详情", palette.foreground, onInfo)
            ReaderBottomActionV11(Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
            ReaderBottomActionV11(Icons.Rounded.AutoStories, "工具", palette.foreground, onTools)
            ReaderBottomActionV11(Icons.Rounded.AutoAwesome, "故事", palette.foreground, onStory)
        }
    }
}

@Composable
private fun ReaderBottomActionV11(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint); Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ReaderToolsSheetV11(
    bookId: String,
    chapter: ChapterDraft,
    archive: ReaderReadingArchiveV11,
    pageMode: ReaderPageModeV10,
    pageIndex: Int,
    scrollY: Int,
    excerpt: String,
    themeKey: String,
    fontKey: String,
    pageModeKey: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    customBg: String,
    customFg: String,
    isLocal: Boolean,
    onArchive: (ReaderReadingArchiveV11) -> Unit,
    onJump: (Int, Int, Int, ReaderPageModeV10) -> Unit,
    onTheme: (String) -> Unit,
    onFont: (String) -> Unit,
    onPageMode: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onCustomBg: (String) -> Unit,
    onCustomFg: (String) -> Unit,
    onApplyPreset: (ReaderThemePresetV11) -> Unit,
    onSearch: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalMiuixTokens.current
    var page by remember { mutableStateOf(ReaderToolPageV11.HOME) }
    var noteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var quoteText by remember(excerpt) { mutableStateOf(excerpt) }
    var presetName by remember { mutableStateOf("") }
    var fontRefresh by remember { mutableIntStateOf(0) }
    var fontMessage by remember { mutableStateOf<String?>(null) }
    val fonts = remember(fontRefresh) { ReaderFontStoreV10.list(context) }
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ReaderFontStoreV10.import(context, uri)
            .onSuccess { asset -> onFont("custom:${asset.path}"); fontRefresh++; fontMessage = "已导入 ${asset.name}" }
            .onFailure { fontMessage = it.message ?: "字体导入失败" }
    }
    val title = when (page) {
        ReaderToolPageV11.HOME -> "阅读工具"
        ReaderToolPageV11.BOOKMARKS -> "书签"
        ReaderToolPageV11.NOTES -> "批注"
        ReaderToolPageV11.DISPLAY -> "阅读显示"
        ReaderToolPageV11.FONTS -> "字体管理"
        ReaderToolPageV11.PRESETS -> "阅读方案"
    }
    OverlayBottomSheet(show = true, title = title, onDismissRequest = { if (page == ReaderToolPageV11.HOME) onDismiss() else page = ReaderToolPageV11.HOME }) {
        when (page) {
            ReaderToolPageV11.HOME -> Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                    ReaderToolRowV11(Icons.Rounded.Bookmark, "书签", "${archive.bookmarks.size} 个 · 当前 ${readerLocationLabelV11(chapter.chapterNumber, pageIndex, scrollY, pageMode)}") { page = ReaderToolPageV11.BOOKMARKS }
                    ReaderDividerV11(); ReaderToolRowV11(Icons.Rounded.EditNote, "批注", "${archive.annotations.size} 条 · 记录想法和摘录") { page = ReaderToolPageV11.NOTES }
                    ReaderDividerV11(); ReaderToolRowV11(Icons.Rounded.Tune, "阅读显示", "字号、行距、主题和翻页") { page = ReaderToolPageV11.DISPLAY }
                    ReaderDividerV11(); ReaderToolRowV11(Icons.Rounded.TextFields, "字体管理", "导入、选择和删除 TTF / OTF") { page = ReaderToolPageV11.FONTS }
                    ReaderDividerV11(); ReaderToolRowV11(Icons.Rounded.Style, "阅读方案", "保存整套阅读配置") { page = ReaderToolPageV11.PRESETS }
                }
                Spacer(Modifier.height(12.dp))
                MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)) {
                    ReaderToolRowV11(Icons.Rounded.Search, "全文搜索", "在整本书中查找文字", onSearch)
                    ReaderDividerV11(); ReaderToolRowV11(Icons.Rounded.AutoAwesome, "从本章进入故事", "当前章节作为原著知识边界", onStory)
                    if (!isLocal) { ReaderDividerV11(); ReaderToolRowV11(Icons.Rounded.Edit, "编辑本章", "回到创作模式修改正文", onEdit) }
                }
            }
            ReaderToolPageV11.BOOKMARKS -> Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                MiuixButton(onClick = {
                    onArchive(ReaderReadingStoreV11.addBookmark(context, bookId, chapter.chapterNumber, pageIndex, scrollY, chapter.title, excerpt))
                }, modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text("添加 / 取消当前位置书签") }
                if (archive.bookmarks.isEmpty()) ReaderEmptyV11("还没有书签") else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 10.dp)) {
                    items(archive.bookmarks.size, key = { archive.bookmarks[it].id }) { i ->
                        val item = archive.bookmarks[i]
                        MiuixCard(Modifier.fillMaxWidth().padding(bottom = 9.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = {
                            val mode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.SCROLL
                            onJump(item.chapterNumber, item.pageIndex, item.scrollY, mode)
                        }, showIndication = true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, fontWeight = FontWeight.SemiBold); Text(item.excerpt.ifBlank { readerLocationLabelV11(item.chapterNumber, item.pageIndex, item.scrollY, pageMode) }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                MiuixIconButton(onClick = { onArchive(ReaderReadingStoreV11.deleteBookmark(context, bookId, item.id)) }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                            }
                        }
                    }
                }
            }
            ReaderToolPageV11.NOTES -> Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                MiuixButton(onClick = { quoteText = excerpt; noteText = ""; noteDialog = true }, modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Icon(Icons.Rounded.AddComment, null); Spacer(Modifier.width(8.dp)); Text("给当前位置添加批注") }
                if (archive.annotations.isEmpty()) ReaderEmptyV11("还没有批注") else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(top = 10.dp)) {
                    items(archive.annotations.size, key = { archive.annotations[it].id }) { i ->
                        val item = archive.annotations[i]
                        MiuixCard(Modifier.fillMaxWidth().padding(bottom = 9.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = {
                            val mode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.SCROLL
                            onJump(item.chapterNumber, item.pageIndex, item.scrollY, mode)
                        }, showIndication = true) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text("第 ${item.chapterNumber} 章", style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                    if (item.quote.isNotBlank()) Text("“${item.quote}”", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(item.note, Modifier.padding(top = 7.dp), color = tokens.textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                }
                                MiuixIconButton(onClick = { onArchive(ReaderReadingStoreV11.deleteAnnotation(context, bookId, item.id)) }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                            }
                        }
                    }
                }
            }
            ReaderToolPageV11.DISPLAY -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("阅读主题", style = MaterialTheme.typography.labelLarge)
                ReaderThemeV11.entries.forEach { t ->
                    MiuixCard(Modifier.fillMaxWidth().padding(top = 8.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 13.dp), onClick = { onTheme(t.key) }, showIndication = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(t.label, Modifier.weight(1f)); if (themeKey == t.key) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                if (themeKey == ReaderThemeV11.CUSTOM.key) {
                    OutlinedTextField(customBg, onCustomBg, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("背景色") }, singleLine = true)
                    OutlinedTextField(customFg, onCustomFg, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("正文色") }, singleLine = true)
                }
                Text("翻页方式", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.labelLarge)
                ReaderPageModeV10.entries.forEach { m ->
                    MiuixCard(Modifier.fillMaxWidth().padding(top = 8.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 13.dp), onClick = { onPageMode(m.key) }, showIndication = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(m.label); Text(m.summary, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }; if (pageModeKey == m.key) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                ReaderSliderV11("字号", fontSize, 14f..30f, { it.toInt().toString() }, onFontSize)
                ReaderSliderV11("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine)
                ReaderSliderV11("页边距", sidePadding, 14f..44f, { "${it.toInt()} dp" }, onPadding)
            }
            ReaderToolPageV11.FONTS -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                MiuixButton(onClick = { fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "*/*")) }, modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("导入字体") }
                fontMessage?.let { Text(it, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
                ReaderBuiltinFontV11.entries.forEach { f ->
                    MiuixCard(Modifier.fillMaxWidth().padding(top = 9.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 13.dp), onClick = { onFont(f.key) }, showIndication = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(f.label, Modifier.weight(1f), fontFamily = readerFontFamilyV11(f.key)); if (fontKey == f.key) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
                fonts.forEach { asset ->
                    val key = "custom:${asset.path}"
                    MiuixCard(Modifier.fillMaxWidth().padding(top = 9.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 9.dp), onClick = { onFont(key) }, showIndication = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(asset.name, fontFamily = ReaderFontStoreV10.family(asset.path) ?: FontFamily.Default); Text(asset.path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            if (fontKey == key) Icon(Icons.Rounded.CheckCircle, "已选择", tint = MaterialTheme.colorScheme.primary)
                            MiuixIconButton(onClick = {
                                if (deleteReaderFontV11(context, asset)) { if (fontKey == key) onFont("default"); fontRefresh++; fontMessage = "已删除 ${asset.name}" } else fontMessage = "删除失败"
                            }) { Icon(Icons.Rounded.DeleteOutline, "删除字体") }
                        }
                    }
                }
            }
            ReaderToolPageV11.PRESETS -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                OutlinedTextField(presetName, { presetName = it }, Modifier.fillMaxWidth(), label = { Text("方案名称") }, placeholder = { Text("例如：夜间阅读") }, singleLine = true)
                MiuixButton(onClick = {
                    val preset = ReaderReadingStoreV11.capturePreset(context, bookId, presetName.ifBlank { "阅读方案 ${archive.presets.size + 1}" })
                    onArchive(ReaderReadingStoreV11.savePreset(context, bookId, preset)); presetName = ""
                }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), cornerRadius = 18.dp) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("保存当前阅读方案") }
                if (archive.presets.isEmpty()) ReaderEmptyV11("还没有保存阅读方案") else archive.presets.forEach { preset ->
                    MiuixCard(Modifier.fillMaxWidth().padding(top = 9.dp), cornerRadius = 17.dp, insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp), onClick = { onApplyPreset(preset) }, showIndication = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(preset.name, fontWeight = FontWeight.SemiBold); Text("${preset.fontSize.toInt()} 号 · ${ReaderPageModeV10.entries.firstOrNull { it.key == preset.pageModeKey }?.label ?: "阅读"}", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
                            MiuixIconButton(onClick = { onArchive(ReaderReadingStoreV11.deletePreset(context, bookId, preset.id)) }) { Icon(Icons.Rounded.DeleteOutline, "删除方案") }
                        }
                    }
                }
            }
        }
    }
    if (noteDialog) AlertDialog(
        onDismissRequest = { noteDialog = false },
        title = { Text("添加批注") },
        text = { Column { OutlinedTextField(quoteText, { quoteText = it }, Modifier.fillMaxWidth(), label = { Text("摘录，可修改") }, minLines = 2, maxLines = 4); OutlinedTextField(noteText, { noteText = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("批注") }, minLines = 3, maxLines = 6) } },
        confirmButton = { TextButton(onClick = { if (noteText.isNotBlank()) { onArchive(ReaderReadingStoreV11.addAnnotation(context, bookId, chapter.chapterNumber, pageIndex, scrollY, quoteText, noteText)); noteDialog = false } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("取消") } },
    )
}

@Composable
private fun ReaderToolRowV11(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = tokens.textSecondary)
        Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(title, color = tokens.textPrimary, fontWeight = FontWeight.Medium); Text(summary, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = tokens.textSecondary)
    }
}

@Composable private fun ReaderDividerV11() = HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
@Composable private fun ReaderEmptyV11(text: String) { Text(text, Modifier.fillMaxWidth().padding(vertical = 34.dp), textAlign = TextAlign.Center, color = LocalMiuixTokens.current.textSecondary) }

@Composable
private fun ReaderSliderV11(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: (Float) -> String, onValue: (Float) -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 17.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, style = MaterialTheme.typography.labelLarge, color = tokens.textPrimary); Text(valueText(value), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}

private fun flattenTocV11(nodes: List<EpubTocNodeV1>, depth: Int = 0): List<TocDisplayEntryV11> = buildList {
    nodes.forEach { node -> add(TocDisplayEntryV11(node.title, node.chapterNumber, depth)); addAll(flattenTocV11(node.children, depth + 1)) }
}

@Composable
private fun DirectorySheetV11(bookId: String, state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    val context = LocalContext.current; val tokens = LocalMiuixTokens.current
    var query by remember { mutableStateOf("") }; var descending by remember { mutableStateOf(false) }
    val original = remember(bookId) { EpubOriginalTocV1.load(context, bookId) }
    val originalEntries = remember(original, query, descending) { val flat = flattenTocV11(original).filter { query.isBlank() || it.title.contains(query, true) }; if (descending) flat.asReversed() else flat }
    val chapters = remember(state.chapters, query, descending) { val list = state.chapters.sortedBy { it.chapterNumber }.filter { query.isBlank() || it.title.contains(query, true) }; if (descending) list.asReversed() else list }
    OverlayBottomSheet(show = true, title = if (original.isNotEmpty()) "原书目录 · ${state.chapters.size} 章" else "目录 · ${state.chapters.size} 章", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索章节") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = tokens.cardBackground, unfocusedContainerColor = tokens.cardBackground))
                MiuixIconButton(onClick = { descending = !descending }, modifier = Modifier.padding(start = 6.dp)) { Icon(if (descending) Icons.Rounded.South else Icons.Rounded.North, if (descending) "倒序" else "正序") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 590.dp).padding(top = 8.dp)) {
                if (original.isNotEmpty()) items(originalEntries.size, key = { "toc_${it}_${originalEntries[it].title}" }) { i ->
                    val item = originalEntries[i]; val selected = item.chapterNumber == state.readingChapter?.chapterNumber
                    Row(Modifier.fillMaxWidth().then(if (item.chapterNumber != null) Modifier.clickable { onChapter(item.chapterNumber) } else Modifier).padding(start = (8 + item.depth * 18).dp, end = 8.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.chapterNumber == null) Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(17.dp), tint = tokens.textSecondary)
                        Text(item.title, Modifier.padding(start = if (item.chapterNumber == null) 4.dp else 0.dp).weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary, fontWeight = if (item.chapterNumber == null || selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                } else items(chapters.size, key = { chapters[it].id }) { i ->
                    val item = chapters[i]; val selected = item.id == state.readingChapter?.id
                    Row(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(horizontal = 8.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis); if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
private fun SearchBookSheetV11(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }; val tokens = LocalMiuixTokens.current
    val results = remember(state.chapters, query) { if (query.trim().length < 2) emptyList() else state.chapters.filter { it.title.contains(query, true) || it.content.contains(query, true) }.take(120) }
    OverlayBottomSheet(show = true, title = "全文搜索", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            TextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("输入两个字以上") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = tokens.cardBackground, unfocusedContainerColor = tokens.cardBackground))
            if (query.length >= 2) Text("找到 ${results.size} 个章节", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) { items(results.size) { i -> val item = results[i]; Column(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(vertical = 12.dp, horizontal = 4.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis); val hit = searchSnippetV11(item.content, query); if (hit.isNotBlank()) Text(hit, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) } } }
        }
    }
}

@Composable
private fun BookInfoSheetV11(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    val context = LocalContext.current; val tokens = LocalMiuixTokens.current; val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }; val author = meta.getString("author_${book.id}", "").orEmpty()
    OverlayBottomSheet(show = true, title = null, onDismissRequest = onDismiss) { Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(104.dp).clip(RoundedCornerShape(10.dp))); Column(Modifier.padding(start = 16.dp).weight(1f)) { Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis); if (author.isNotBlank()) Text(author, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary); Text("${state.chapters.size} 章 · ${humanWordsV11(book.currentWords)}", Modifier.padding(top = 6.dp), color = tokens.textSecondary) } }; MiuixButton(onClick = { onDismiss(); onOpenFull() }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), cornerRadius = 18.dp) { Text("查看完整详情") } } }
}

@Composable
private fun ReaderBookInfoPageV11(book: ReaderBookUi, state: LibraryExperienceState, isLocal: Boolean, onBack: () -> Unit, onRead: () -> Unit, onStory: () -> Unit, onWriting: () -> Unit, onAiSetup: () -> Unit) {
    val context = LocalContext.current; val tokens = LocalMiuixTokens.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val fileName = meta.getString("name_${book.id}", book.title).orEmpty(); val fileSize = meta.getLong("size_${book.id}", 0L); val format = meta.getString("format_${book.id}", if (isLocal) "本地" else "创作").orEmpty(); val author = meta.getString("author_${book.id}", "").orEmpty(); val importedAt = meta.getLong("imported_${book.id}", 0L)
    val progress = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1)); val percent = if (state.chapters.isEmpty()) 0 else ((progress.chapterNumber.coerceIn(1, state.chapters.size).toFloat() / state.chapters.size) * 100f).roundToInt()
    MiuixScaffold(containerColor = tokens.pageBackground, topBar = { MiuixTopAppBar(title = "图书详情", largeTitle = "图书详情", subtitle = if (isLocal) "本地书籍" else book.genre, navigationIcon = { MiuixIconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } }, actions = { if (!isLocal) MiuixIconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") } }) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) { CoverPreviewV3(book.coverPath, book.title, Modifier.width(108.dp).height(156.dp).clip(RoundedCornerShape(14.dp))); Column(Modifier.padding(start = 18.dp).weight(1f)) { Text(book.title, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary); if (author.isNotBlank()) Text(author, Modifier.padding(top = 7.dp), color = tokens.textSecondary); Text("${state.chapters.size} 章 · ${humanWordsV11(book.currentWords)}", Modifier.padding(top = 8.dp), color = tokens.textSecondary); Text("阅读 $percent% · 第 ${progress.chapterNumber} 章", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary) } }
            MiuixButton(onClick = onRead, modifier = Modifier.fillMaxWidth().padding(top = 26.dp), cornerRadius = 18.dp) { Text("继续阅读 · 第 ${progress.chapterNumber} 章") }
            MiuixButton(onClick = onStory, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), cornerRadius = 18.dp) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("从当前章节进入故事") }
            Text("阅读信息", Modifier.padding(top = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) { InfoRowV11("阅读进度", "$percent%"); InfoRowV11("当前章节", "第 ${progress.chapterNumber} 章"); InfoRowV11("总章节", "${state.chapters.size} 章"); InfoRowV11("总字数", humanWordsV11(book.currentWords), false) }
            if (isLocal) { Text("文件信息", Modifier.padding(top = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); MiuixCard(cornerRadius = 18.dp, insideMargin = PaddingValues(horizontal = 16.dp)) { if (author.isNotBlank()) InfoRowV11("作者", author); InfoRowV11("文件名", fileName.ifBlank { book.title }); InfoRowV11("文件大小", if (fileSize > 0) humanBytesV11(fileSize) else "未知"); InfoRowV11("格式", format); InfoRowV11("导入时间", if (importedAt > 0) formatTimeV11(importedAt) else "旧版本导入", false) } }
            if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) { Text("简介", Modifier.padding(top = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(book.premise, lineHeight = 24.sp, color = tokens.textSecondary) }
            if (!isLocal) TextButton(onClick = onWriting, modifier = Modifier.padding(top = 18.dp).align(Alignment.End)) { Text("打开创作工作台") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun InfoRowV11(label: String, value: String, divider: Boolean = true) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) { Text(label, Modifier.width(86.dp), color = tokens.textSecondary, style = MaterialTheme.typography.bodyMedium); Text(value, Modifier.weight(1f), textAlign = TextAlign.End, color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f))
}

@Composable
private fun readerPaletteV11(theme: ReaderThemeV11, customBg: String, customFg: String): ReaderPaletteV11 {
    val customBackground = parseReaderHexColorV10(customBg) ?: Color(0xFFF4F0E6)
    val customForeground = parseReaderHexColorV10(customFg) ?: Color(0xFF302D28)
    val scheme = MaterialTheme.colorScheme
    return when (theme) {
        // 「默认」跟随应用主题，因此浅色/深色和纸白配色都能到达阅读页。
        ReaderThemeV11.SYSTEM -> ReaderPaletteV11(scheme.background, scheme.onBackground, scheme.onSurfaceVariant, scheme.surfaceContainer)
        ReaderThemeV11.PAPER -> ReaderPaletteV11(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF7B756C), Color(0xFFEDE8DB))
        ReaderThemeV11.WARM -> ReaderPaletteV11(Color(0xFFF3E5C9), Color(0xFF362D23), Color(0xFF7E6E5B), Color(0xFFEADAB9))
        ReaderThemeV11.GREEN -> ReaderPaletteV11(Color(0xFFE4ECE0), Color(0xFF283129), Color(0xFF657166), Color(0xFFD8E3D5))
        ReaderThemeV11.NIGHT -> ReaderPaletteV11(Color(0xFF171819), Color(0xFFD5D2CC), Color(0xFF8E8C87), Color(0xFF242527))
        ReaderThemeV11.CUSTOM -> ReaderPaletteV11(customBackground, customForeground, customForeground.copy(alpha = .58f), customBackground.copy(alpha = .96f))
    }
}

private fun readerFontFamilyV11(key: String): FontFamily = when {
    key == ReaderBuiltinFontV11.SERIF.key -> FontFamily.Serif
    key == ReaderBuiltinFontV11.SANS.key -> FontFamily.SansSerif
    key == ReaderBuiltinFontV11.MONO.key -> FontFamily.Monospace
    key.startsWith("custom:") -> ReaderFontStoreV10.family(key.removePrefix("custom:")) ?: FontFamily.Default
    else -> FontFamily.Default
}

private fun currentPageModeKeyV11(context: Context, bookId: String): String = context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE).getString("page_mode_$bookId", ReaderPageModeV10.SCROLL.key) ?: ReaderPageModeV10.SCROLL.key
private fun searchSnippetV11(text: String, query: String): String { val index = text.indexOf(query, ignoreCase = true); if (index < 0) return text.replace('\n', ' ').take(90); val start = (index - 35).coerceAtLeast(0); val end = (index + query.length + 55).coerceAtMost(text.length); return text.substring(start, end).replace('\n', ' ').trim() }
private fun humanWordsV11(words: Int): String = if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
private fun humanBytesV11(bytes: Long): String = when { bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f); bytes >= 1024L -> "%.1f KB".format(bytes / 1024f); else -> "$bytes B" }
private fun formatTimeV11(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
