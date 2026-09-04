package com.xiguli.langhuan.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanRadius
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow

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
    var route by remember(book.id) {
        mutableStateOf(if (startOnInfo || !isLocal) ReaderBookRouteV11.INFO else ReaderBookRouteV11.READER)
    }

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
        if (resetPosition) {
            ReaderProgressStoreV11.moveTo(context, book.id, number, 0, 0, currentPageModeKeyV11(context, book.id))
        }
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
            ReaderFloatingIconButtonV11(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                icon = Icons.Rounded.ArrowBack,
                description = "返回阅读",
                onClick = { route = ReaderBookRouteV11.READER },
            )
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

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 21f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.82f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "paper") ?: "paper") }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "default") ?: "default") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.SCROLL.key) ?: ReaderPageModeV10.SCROLL.key)
    }
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
    val savedProgress = remember(chapter.id, pageModeKey) {
        ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber)
    }

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
            if (pageMode == ReaderPageModeV10.SCROLL) {
                ReaderProgressStoreV11.save(context, book.id, ReaderProgressV11(chapter.chapterNumber, 0, y, pageMode.key))
            }
        }
    }
    LaunchedEffect(chapter.id, pageModeKey, pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            if (pageMode != ReaderPageModeV10.SCROLL) {
                ReaderProgressStoreV11.save(context, book.id, ReaderProgressV11(chapter.chapterNumber, page, 0, pageMode.key))
            }
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
            context,
            book.id,
            chapter.chapterNumber,
            currentPageIndex(),
            currentScrollY(),
            chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
            readerExcerptAtV11(chapter.content, currentProgressFraction()),
        )
    }

    val bookmarked = archive.bookmarks.any {
        it.chapterNumber == chapter.chapterNumber &&
            it.pageIndex == currentPageIndex() &&
            kotlin.math.abs(it.scrollY - currentScrollY()) <= 80
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> SelectionContainer {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(scroll)
                        .clickable { chrome = !chrome }
                        .padding(horizontal = sidePadding.dp)
                        .padding(top = 54.dp, bottom = 92.dp),
                ) {
                    ReaderChapterHeaderV11(chapter, fontSize, family, palette)
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
                        Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.secondary,
                    )
                }
            }

            ReaderPageModeV10.PAGE, ReaderPageModeV10.COVER -> HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = if (pageMode == ReaderPageModeV10.COVER) 12.dp else 0.dp,
                beyondViewportPageCount = if (pageMode == ReaderPageModeV10.COVER) 0 else 1,
            ) { page ->
                SelectionContainer {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                            .padding(if (pageMode == ReaderPageModeV10.COVER) 4.dp else 0.dp)
                            .clickable { chrome = !chrome },
                        shape = RoundedCornerShape(if (pageMode == ReaderPageModeV10.COVER) LanghuanRadius.cover else 0.dp),
                        color = palette.background,
                        contentColor = palette.foreground,
                    ) {
                        Column(
                            Modifier.fillMaxSize()
                                .padding(horizontal = sidePadding.dp)
                                .padding(top = 58.dp, bottom = 62.dp),
                        ) {
                            if (page == 0) {
                                ReaderChapterHeaderV11(chapter, fontSize, family, palette)
                                Spacer(Modifier.height(24.dp))
                            }
                            Text(
                                pages[page],
                                modifier = Modifier.weight(1f),
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * lineFactor).sp,
                                fontFamily = family,
                                color = palette.foreground,
                            )
                            Text(
                                "${page + 1}/${pages.size} · 第 ${chapter.chapterNumber} 章",
                                Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.secondary,
                            )
                        }
                    }
                }
            }
        }

        if (!chrome) {
            val pageText = if (pageMode == ReaderPageModeV10.SCROLL) "${index + 1} / ${ordered.size}" else "${pager.currentPage + 1}/${pages.size}"
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .72f),
            )
            Text(
                "$pageText  ${(currentProgressFraction() * 100).toInt()}%",
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .72f),
            )
        }

        if (chrome) {
            ReaderChromeV11(
                chapter = chapter,
                index = index,
                total = ordered.size,
                palette = palette,
                bookmarked = bookmarked,
                onBack = onBack,
                onPrev = { move(-1) },
                onNext = { move(1) },
                onBookmark = ::toggleBookmark,
                onDirectory = { showDirectory = true },
                onInfo = { showInfo = true },
                onTools = { showTools = true },
            )
        }
    }

    if (showDirectory) DirectorySheetV11(book.id, state, { showDirectory = false }) {
        showDirectory = false
        onChapter(it)
    }
    if (showInfo) BookInfoSheetV11(book, state, { showInfo = false }, onOpenInfo)
    if (showSearch) SearchBookSheetV11(state, { showSearch = false }) {
        showSearch = false
        onChapter(it)
    }
    if (showTools) {
        ReaderToolsSheetV11(
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
                themeKey = preset.themeKey
                fontKey = preset.fontKey
                pageModeKey = preset.pageModeKey
                fontSize = preset.fontSize
                lineFactor = preset.lineFactor
                sidePadding = preset.sidePadding
                customBg = preset.customBg
                customFg = preset.customFg
            },
            onSearch = { showTools = false; showSearch = true },
            onStory = { showTools = false; onStory() },
            onEdit = { showTools = false; onEdit(chapter.chapterNumber) },
            onDismiss = { showTools = false },
        )
    }
}

@Composable
private fun ReaderChapterHeaderV11(
    chapter: ChapterDraft,
    fontSize: Float,
    family: FontFamily,
    palette: ReaderPaletteV11,
) {
    Text(
        chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
        fontSize = (fontSize + 5).sp,
        lineHeight = (fontSize + 11).sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = family,
        color = palette.foreground,
    )
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
) {
    val border = palette.secondary.copy(alpha = .18f)
    Surface(
        modifier = Modifier.align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .fillMaxWidth(),
        shape = LanghuanShape.card,
        color = palette.chrome.copy(alpha = .97f),
        contentColor = palette.foreground,
        border = BorderStroke(1.dp, border),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderChromeIconButtonV11(Icons.Rounded.ArrowBack, "返回", palette.foreground, onBack)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(
                    chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.foreground,
                )
                Text(
                    "${index + 1} / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary,
                )
            }
            ReaderChromeIconButtonV11(
                if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                "书签",
                palette.foreground,
                onBookmark,
            )
            ReaderChromeIconButtonV11(Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
        }
    }

    Surface(
        modifier = Modifier.align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = LanghuanShape.card,
        color = palette.chrome.copy(alpha = .985f),
        contentColor = palette.foreground,
        border = BorderStroke(1.dp, border),
        shadowElevation = 5.dp,
    ) {
        Column {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev, enabled = index > 0) {
                    Text("上一章", color = if (index > 0) palette.foreground else palette.secondary)
                }
                Text(
                    "第 ${chapter.chapterNumber} 章",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondary,
                )
                TextButton(onClick = onNext, enabled = index < total - 1) {
                    Text("下一章", color = if (index < total - 1) palette.foreground else palette.secondary)
                }
            }
            HorizontalDivider(color = border)
            Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ReaderBottomActionV11(Icons.Rounded.Info, "详情", palette.foreground, onInfo)
                ReaderBottomActionV11(Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
                ReaderBottomActionV11(Icons.Rounded.Tune, "阅读设置", palette.foreground, onTools)
            }
        }
    }
}

@Composable
private fun ReaderChromeIconButtonV11(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, description, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun ReaderBottomActionV11(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val t = LocalLanghuanUiTokens.current
    var page by remember { mutableStateOf(ReaderToolPageV11.HOME) }
    var noteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var quoteText by remember(excerpt) { mutableStateOf(excerpt) }
    var presetName by remember { mutableStateOf("") }
    var fontRefresh by remember { mutableIntStateOf(0) }
    var fontMessage by remember { mutableStateOf<String?>(null) }
    val fonts = remember(fontRefresh) { ReaderFontStoreV10.list(context) }
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ReaderFontStoreV10.import(context, uri)
                .onSuccess { asset ->
                    onFont("custom:${asset.path}")
                    fontRefresh++
                    fontMessage = "已导入 ${asset.name}"
                }
                .onFailure { fontMessage = it.message ?: "字体导入失败" }
        }
    }

    val title = when (page) {
        ReaderToolPageV11.HOME -> "阅读设置"
        ReaderToolPageV11.BOOKMARKS -> "书签"
        ReaderToolPageV11.NOTES -> "批注"
        ReaderToolPageV11.DISPLAY -> "显示与翻页"
        ReaderToolPageV11.FONTS -> "字体"
        ReaderToolPageV11.PRESETS -> "阅读方案"
    }

    ModalBottomSheet(
        onDismissRequest = { if (page == ReaderToolPageV11.HOME) onDismiss() else page = ReaderToolPageV11.HOME },
        containerColor = t.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = t.border) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReaderSheetHeaderV11(
                title = title,
                subtitle = if (page == ReaderToolPageV11.HOME) "只保留真正可用的阅读控制" else null,
                canGoBack = page != ReaderToolPageV11.HOME,
                onBack = { page = ReaderToolPageV11.HOME },
                onClose = onDismiss,
            )

            when (page) {
                ReaderToolPageV11.HOME -> {
                    ReaderQuickGroupV11(
                        title = "阅读",
                        items = listOf(
                            ReaderQuickItemV11(Icons.Rounded.Palette, "主题与字号") { page = ReaderToolPageV11.DISPLAY },
                            ReaderQuickItemV11(Icons.Rounded.TextFields, "字体") { page = ReaderToolPageV11.FONTS },
                            ReaderQuickItemV11(Icons.Rounded.BookmarkBorder, "书签") { page = ReaderToolPageV11.BOOKMARKS },
                            ReaderQuickItemV11(Icons.Rounded.EditNote, "批注") { page = ReaderToolPageV11.NOTES },
                            ReaderQuickItemV11(Icons.Rounded.Search, "全文搜索", onSearch),
                            ReaderQuickItemV11(Icons.Rounded.Style, "阅读方案") { page = ReaderToolPageV11.PRESETS },
                        ),
                    )
                    Text("翻页方式", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                    ReaderPageModeV10.entries.forEach { mode ->
                        ReaderSelectionRowV11(
                            title = mode.label,
                            subtitle = mode.summary,
                            selected = pageModeKey == mode.key,
                            onClick = { onPageMode(mode.key) },
                        )
                    }
                    HorizontalDivider(color = t.border)
                    ReaderActionRowV11(Icons.Rounded.AutoAwesome, "进入故事", "从当前图书进入沉浸式 Story 模式", onStory)
                    if (!isLocal) {
                        ReaderActionRowV11(Icons.Rounded.Edit, "修改当前正文", "打开第 ${chapter.chapterNumber} 章编辑器", onEdit)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReaderToolPageV11.BOOKMARKS -> {
                    Button(
                        onClick = {
                            onArchive(
                                ReaderReadingStoreV11.addBookmark(
                                    context, bookId, chapter.chapterNumber, pageIndex, scrollY, chapter.title, excerpt,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加 / 取消当前位置书签")
                    }
                    if (archive.bookmarks.isEmpty()) {
                        ReaderEmptyV11("还没有书签")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(archive.bookmarks, key = { it.id }) { item ->
                                ReaderArchiveCardV11(
                                    title = item.title.ifBlank { "第 ${item.chapterNumber} 章" },
                                    subtitle = item.excerpt.ifBlank {
                                        readerLocationLabelV11(item.chapterNumber, item.pageIndex, item.scrollY, pageMode)
                                    },
                                    onClick = {
                                        val mode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey }
                                            ?: ReaderPageModeV10.SCROLL
                                        onJump(item.chapterNumber, item.pageIndex, item.scrollY, mode)
                                    },
                                    onDelete = { onArchive(ReaderReadingStoreV11.deleteBookmark(context, bookId, item.id)) },
                                )
                            }
                        }
                    }
                }

                ReaderToolPageV11.NOTES -> {
                    Button(
                        onClick = { quoteText = excerpt; noteText = ""; noteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.AddComment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("给当前位置添加批注")
                    }
                    if (archive.annotations.isEmpty()) {
                        ReaderEmptyV11("还没有批注")
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(archive.annotations, key = { it.id }) { item ->
                                ReaderArchiveCardV11(
                                    title = "第 ${item.chapterNumber} 章",
                                    subtitle = buildString {
                                        if (item.quote.isNotBlank()) append("“${item.quote}”\n")
                                        append(item.note)
                                    },
                                    onClick = {
                                        val mode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey }
                                            ?: ReaderPageModeV10.SCROLL
                                        onJump(item.chapterNumber, item.pageIndex, item.scrollY, mode)
                                    },
                                    onDelete = { onArchive(ReaderReadingStoreV11.deleteAnnotation(context, bookId, item.id)) },
                                )
                            }
                        }
                    }
                }

                ReaderToolPageV11.DISPLAY -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(ReaderThemeV11.entries) { theme ->
                            ReaderThemeCardV11(theme, selected = themeKey == theme.key) { onTheme(theme.key) }
                        }
                    }
                    if (themeKey == ReaderThemeV11.CUSTOM.key) {
                        OutlinedTextField(customBg, onCustomBg, Modifier.fillMaxWidth(), label = { Text("背景色") }, singleLine = true)
                        OutlinedTextField(customFg, onCustomFg, Modifier.fillMaxWidth(), label = { Text("正文色") }, singleLine = true)
                    }
                    Text("翻页方式", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                    ReaderPageModeV10.entries.forEach { mode ->
                        ReaderSelectionRowV11(mode.label, mode.summary, pageModeKey == mode.key) { onPageMode(mode.key) }
                    }
                    ReaderSliderV11("字号", fontSize, 14f..30f, { it.toInt().toString() }, onFontSize)
                    ReaderSliderV11("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine)
                    ReaderSliderV11("页边距", sidePadding, 14f..44f, { "${it.toInt()} dp" }, onPadding)
                    Spacer(Modifier.height(8.dp))
                }

                ReaderToolPageV11.FONTS -> {
                    Button(
                        onClick = {
                            fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.FileOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入字体")
                    }
                    fontMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
                    ReaderBuiltinFontV11.entries.forEach { font ->
                        ReaderSelectionRowV11(
                            title = font.label,
                            subtitle = "内置字体",
                            selected = fontKey == font.key,
                            fontFamily = readerFontFamilyV11(font.key),
                            onClick = { onFont(font.key) },
                        )
                    }
                    fonts.forEach { asset ->
                        val key = "custom:${asset.path}"
                        ReaderFontRowV11(
                            title = asset.name,
                            subtitle = asset.path.substringAfterLast('/'),
                            selected = fontKey == key,
                            fontFamily = ReaderFontStoreV10.family(asset.path) ?: FontFamily.Default,
                            onClick = { onFont(key) },
                            onDelete = {
                                if (deleteReaderFontV11(context, asset)) {
                                    if (fontKey == key) onFont("default")
                                    fontRefresh++
                                    fontMessage = "已删除 ${asset.name}"
                                } else {
                                    fontMessage = "删除失败"
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReaderToolPageV11.PRESETS -> {
                    OutlinedTextField(
                        presetName,
                        { presetName = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("方案名称") },
                        placeholder = { Text("例如：夜间阅读") },
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val preset = ReaderReadingStoreV11.capturePreset(
                                context,
                                bookId,
                                presetName.ifBlank { "阅读方案 ${archive.presets.size + 1}" },
                            )
                            onArchive(ReaderReadingStoreV11.savePreset(context, bookId, preset))
                            presetName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存当前阅读方案")
                    }
                    if (archive.presets.isEmpty()) {
                        ReaderEmptyV11("还没有保存阅读方案")
                    } else {
                        archive.presets.forEach { preset ->
                            ReaderPresetRowV11(
                                preset = preset,
                                onApply = { onApplyPreset(preset) },
                                onDelete = { onArchive(ReaderReadingStoreV11.deletePreset(context, bookId, preset.id)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (noteDialog) {
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("添加批注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(quoteText, { quoteText = it }, Modifier.fillMaxWidth(), label = { Text("摘录，可修改") }, minLines = 2, maxLines = 4)
                    OutlinedTextField(noteText, { noteText = it }, Modifier.fillMaxWidth(), label = { Text("批注") }, minLines = 3, maxLines = 6)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotBlank()) {
                        onArchive(
                            ReaderReadingStoreV11.addAnnotation(
                                context, bookId, chapter.chapterNumber, pageIndex, scrollY, quoteText, noteText,
                            )
                        )
                        noteDialog = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("取消") } },
        )
    }
}

private data class ReaderQuickItemV11(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun ReaderQuickGroupV11(title: String, items: List<ReaderQuickItemV11>) {
    val t = LocalLanghuanUiTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(items) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
                    shape = LanghuanShape.card,
                    color = t.card,
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Column(
                        Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(item.icon, null, Modifier.size(22.dp), tint = t.foreground)
                        Text(item.label, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelSmall, color = t.foreground)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSheetHeaderV11(
    title: String,
    subtitle: String?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canGoBack) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") }
    }
}

@Composable
private fun ReaderActionRowV11(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = LanghuanShape.card,
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = t.accent)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
        }
    }
}

@Composable
private fun ReaderSelectionRowV11(
    title: String,
    subtitle: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = LanghuanShape.card,
        color = if (selected) t.warmSurface else t.card,
        border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .35f) else t.border),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = t.foreground, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontFamily = fontFamily)
                Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = t.accent)
        }
    }
}

@Composable
private fun ReaderArchiveCardV11(title: String, subtitle: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = LanghuanShape.card,
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderFontRowV11(
    title: String,
    subtitle: String,
    selected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = LanghuanShape.card,
        color = if (selected) t.warmSurface else t.card,
        border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .35f) else t.border),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = t.foreground, fontFamily = fontFamily, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = t.accent)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除字体", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderPresetRowV11(preset: ReaderThemePresetV11, onApply: () -> Unit, onDelete: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
        shape = LanghuanShape.card,
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text(
                    "${preset.fontSize.toInt()} 号 · ${ReaderPageModeV10.entries.firstOrNull { it.key == preset.pageModeKey }?.label ?: "阅读"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除方案", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderThemeCardV11(theme: ReaderThemeV11, selected: Boolean, onClick: () -> Unit) {
    val palette = readerPaletteV11(theme, "#FFF4F0E6", "#FF302D28")
    Surface(
        modifier = Modifier.fillMaxWidth().height(138.dp).clickable(onClick = onClick),
        shape = LanghuanShape.cover,
        color = palette.background,
        contentColor = palette.foreground,
        border = BorderStroke(2.dp, if (selected) LocalLanghuanUiTokens.current.accent else palette.secondary.copy(alpha = .18f)),
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                    shape = LanghuanShape.pill,
                    color = LocalLanghuanUiTokens.current.accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, "已选择", Modifier.size(18.dp), tint = LocalLanghuanUiTokens.current.accentForeground)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(theme.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.foreground)
                Text("正文预览", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondary)
            }
        }
    }
}

@Composable
private fun ReaderEmptyV11(text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.card,
        color = t.muted,
        border = BorderStroke(1.dp, t.border),
    ) {
        Text(text, Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center, color = t.mutedForeground)
    }
}

@Composable
private fun ReaderSliderV11(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onValue: (Float) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = t.foreground)
        Text(valueText(value), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}

private fun flattenTocV11(nodes: List<EpubTocNodeV1>, depth: Int = 0): List<TocDisplayEntryV11> = buildList {
    nodes.forEach { node ->
        add(TocDisplayEntryV11(node.title, node.chapterNumber, depth))
        addAll(flattenTocV11(node.children, depth + 1))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorySheetV11(
    bookId: String,
    state: LibraryExperienceState,
    onDismiss: () -> Unit,
    onChapter: (Int) -> Unit,
) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    val original = remember(bookId) { EpubOriginalTocV1.load(context, bookId) }
    val originalEntries = remember(original, query, descending) {
        val flat = flattenTocV11(original).filter { query.isBlank() || it.title.contains(query, true) }
        if (descending) flat.asReversed() else flat
    }
    val chapters = remember(state.chapters, query, descending) {
        val list = state.chapters.sortedBy { it.chapterNumber }.filter { query.isBlank() || it.title.contains(query, true) }
        if (descending) list.asReversed() else list
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = t.border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            ReaderSheetHeaderV11(
                title = state.openedBook?.title ?: "目录",
                subtitle = "${state.chapters.size} 章 · ${if (descending) "倒序" else "正序"}",
                canGoBack = false,
                onBack = {},
                onClose = onDismiss,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.weight(1f),
                    placeholder = { Text("搜索章节") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                )
                IconButton(onClick = { descending = !descending }, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(if (descending) Icons.Rounded.South else Icons.Rounded.North, if (descending) "倒序" else "正序")
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (original.isNotEmpty()) {
                    items(originalEntries, key = { "${it.depth}:${it.chapterNumber}:${it.title}" }) { item ->
                        val selected = item.chapterNumber == state.readingChapter?.chapterNumber
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (item.chapterNumber != null) Modifier.clickable { onChapter(item.chapterNumber) } else Modifier)
                                .padding(start = (8 + item.depth * 18).dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.chapterNumber == null) Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(17.dp), tint = t.mutedForeground)
                            Text(
                                item.title,
                                Modifier.padding(start = if (item.chapterNumber == null) 4.dp else 0.dp).weight(1f),
                                color = if (selected) t.accent else t.foreground,
                                fontWeight = if (item.chapterNumber == null || selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = t.accent)
                        }
                    }
                } else {
                    items(chapters, key = { it.id }) { item ->
                        val selected = item.id == state.readingChapter?.id
                        Row(
                            Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(horizontal = 8.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.title.ifBlank { "第 ${item.chapterNumber} 章" },
                                Modifier.weight(1f),
                                color = if (selected) t.accent else t.foreground,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = t.accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBookSheetV11(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val t = LocalLanghuanUiTokens.current
    val results = remember(state.chapters, query) {
        if (query.trim().length < 2) emptyList()
        else state.chapters.filter { it.title.contains(query, true) || it.content.contains(query, true) }.take(120)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = t.border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            ReaderSheetHeaderV11("全文搜索", "输入两个字以上搜索标题与正文", false, {}, onDismiss)
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("搜索整本书") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
            )
            if (query.length >= 2) {
                Text("找到 ${results.size} 个章节", Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = { it.id }) { item ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    ) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val hit = searchSnippetV11(item.content, query)
                        if (hit.isNotBlank()) {
                            Text(hit, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookInfoSheetV11(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val author = meta.getString("author_${book.id}", "").orEmpty()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = t.border) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(104.dp).clip(LanghuanShape.cover))
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (author.isNotBlank()) Text(author, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    Text("${state.chapters.size} 章 · ${humanWordsV11(book.currentWords)}", Modifier.padding(top = 6.dp), color = t.mutedForeground)
                }
            }
            Button(
                onClick = { onDismiss(); onOpenFull() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) { Text("查看完整详情") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderBookInfoPageV11(
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
    val t = LocalLanghuanUiTokens.current
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val fileName = meta.getString("name_${book.id}", book.title).orEmpty()
    val fileSize = meta.getLong("size_${book.id}", 0L)
    val format = meta.getString("format_${book.id}", if (isLocal) "本地" else "创作").orEmpty()
    val author = meta.getString("author_${book.id}", "").orEmpty()
    val importedAt = meta.getLong("imported_${book.id}", 0L)
    val progress = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
    val rawPercent = if (state.chapters.isEmpty()) 0f else progress.chapterNumber.coerceIn(1, state.chapters.size).toFloat() / state.chapters.size * 100f
    var tab by rememberSaveable(book.id) { mutableIntStateOf(0) }

    Scaffold(
        containerColor = t.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("图书详情", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                        Text(if (isLocal) "本地图书" else "琅嬛创作项目", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                actions = { if (!isLocal) IconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.background),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.Top) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(92.dp).height(132.dp).clip(LanghuanShape.cover))
                Column(Modifier.padding(start = 18.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, color = t.foreground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(author.ifBlank { "佚名" }, Modifier.padding(top = 6.dp), color = t.mutedForeground)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ReaderMetaBadgeV11("${state.chapters.size} 章")
                        ReaderMetaBadgeV11(humanWordsV11(book.currentWords))
                        ReaderMetaBadgeV11(format.uppercase(Locale.getDefault()))
                    }
                }
            }

            TabRow(selectedTabIndex = tab, containerColor = t.background, divider = { HorizontalDivider(color = t.border) }) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("详情") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("封面") })
            }

            if (tab == 0) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LanghuanShape.panel,
                        color = t.card,
                        border = BorderStroke(1.dp, t.border),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                Text(String.format(Locale.getDefault(), "%.1f", rawPercent), fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, color = t.foreground)
                                Text("%", Modifier.padding(start = 3.dp, bottom = 3.dp), color = t.mutedForeground)
                                Spacer(Modifier.weight(1f))
                                Text("${progress.chapterNumber}/${state.chapters.size} 章", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                            }
                            LinearProgressIndicator(
                                progress = { rawPercent / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                color = t.accent,
                                trackColor = t.muted,
                            )
                            Text(
                                state.chapters.getOrNull(progress.chapterNumber - 1)?.title ?: "第 ${progress.chapterNumber} 章",
                                Modifier.padding(top = 9.dp),
                                color = t.mutedForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = LanghuanShape.panel,
                        color = t.card,
                        border = BorderStroke(1.dp, t.border),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            InfoRowV11("文件名称", fileName.ifBlank { book.title })
                            InfoRowV11("文件大小", if (fileSize > 0) humanBytesV11(fileSize) else "未知")
                            InfoRowV11("文件格式", format.uppercase(Locale.getDefault()))
                            InfoRowV11("全文字数", humanWordsV11(book.currentWords))
                            InfoRowV11("总章节数", "${state.chapters.size} 章")
                            InfoRowV11("保存位置", "本机")
                            InfoRowV11("导入时间", if (importedAt > 0) formatTimeV11(importedAt) else "旧版本导入", false)
                        }
                    }

                    if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = LanghuanShape.panel,
                            color = t.card,
                            border = BorderStroke(1.dp, t.border),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("简介", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                Text(book.premise, Modifier.padding(top = 10.dp), lineHeight = 23.sp, color = t.mutedForeground)
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverPreviewV3(book.coverPath, book.title, Modifier.width(210.dp).height(304.dp).clip(LanghuanShape.cover))
                    Text(book.title, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    Text("封面随图书保存在本机", Modifier.padding(top = 6.dp), color = t.mutedForeground)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRead, Modifier.weight(1f).height(50.dp)) { Text("继续阅读") }
                OutlinedButton(onClick = onStory, Modifier.weight(1f).height(50.dp)) { Text("进入故事") }
            }
            if (!isLocal) {
                TextButton(onClick = onWriting, modifier = Modifier.fillMaxWidth()) { Text("打开创作工作台") }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun ReaderMetaBadgeV11(text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(shape = LanghuanShape.pill, color = t.muted, border = BorderStroke(1.dp, t.border)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
    }
}

@Composable
private fun InfoRowV11(label: String, value: String, divider: Boolean = true) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(86.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            Modifier.weight(1f),
            textAlign = TextAlign.End,
            color = t.foreground,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (divider) HorizontalDivider(color = t.border)
}

@Composable
private fun ReaderFloatingIconButtonV11(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier.size(44.dp),
        shape = LanghuanShape.card,
        color = t.card.copy(alpha = .94f),
        border = BorderStroke(1.dp, t.border),
        shadowElevation = 4.dp,
    ) {
        IconButton(onClick = onClick) { Icon(icon, description, tint = t.foreground) }
    }
}

@Composable
private fun readerPaletteV11(theme: ReaderThemeV11, customBg: String, customFg: String): ReaderPaletteV11 {
    val customBackground = parseReaderHexColorV10(customBg) ?: Color(0xFFF4F0E6)
    val customForeground = parseReaderHexColorV10(customFg) ?: Color(0xFF302D28)
    val scheme = MaterialTheme.colorScheme
    return when (theme) {
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

private fun currentPageModeKeyV11(context: Context, bookId: String): String =
    context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE)
        .getString("page_mode_$bookId", ReaderPageModeV10.SCROLL.key)
        ?: ReaderPageModeV10.SCROLL.key

private fun searchSnippetV11(text: String, query: String): String {
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return text.replace('\n', ' ').take(90)
    val start = (index - 35).coerceAtLeast(0)
    val end = (index + query.length + 55).coerceAtMost(text.length)
    return text.substring(start, end).replace('\n', ' ').trim()
}

private fun humanWordsV11(words: Int): String = if (words >= 10_000) "%.1f 万字".format(words / 10_000f) else "$words 字"
private fun humanBytesV11(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
private fun formatTimeV11(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
