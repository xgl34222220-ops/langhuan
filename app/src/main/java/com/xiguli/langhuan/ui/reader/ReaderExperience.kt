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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.EpubOriginalTocV1
import com.xiguli.langhuan.data.EpubTocNodeV1
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow

private enum class ReaderExperienceRoute { INFO, READER, STORY }
private enum class ReaderSettingsPage { HOME, TYPOGRAPHY, FONTS, PRESETS, BOOKMARKS, NOTES }
private enum class ReaderExperienceTheme(val key: String, val label: String) {
    SYSTEM("system", "默认"),
    PAPER("paper", "纸张"),
    WARM("warm", "暖黄"),
    GREEN("green", "护眼"),
    NIGHT("night", "夜间"),
    CUSTOM("custom", "自定义"),
}

private data class ReaderExperiencePalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
)

private data class ReaderTocEntry(val title: String, val chapterNumber: Int?, val depth: Int)

/**
 * Functional-folder replacement for the old version-stacked reader shell.
 * The old V11 file remains temporarily as a rollback target while migration is in Draft.
 */
@Composable
fun ReaderExperience(
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
        mutableStateOf(if (startOnInfo || !isLocal) ReaderExperienceRoute.INFO else ReaderExperienceRoute.READER)
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

    fun openChapter(number: Int, resetPosition: Boolean) {
        if (resetPosition) {
            ReaderProgressStoreV11.moveTo(
                context = context,
                bookId = book.id,
                chapterNumber = number,
                pageIndex = 0,
                scrollY = 0,
                modeKey = currentReaderMode(context, book.id),
                positionFraction = 0f,
                textOffset = 0,
            )
        }
        viewModel.openReader(number)
    }

    when (route) {
        ReaderExperienceRoute.INFO -> ReaderExperienceInfo(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = { if (isLocal) route = ReaderExperienceRoute.READER else onBackToShelf() },
            onRead = {
                val saved = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
                openChapter(saved.chapterNumber, resetPosition = false)
                route = ReaderExperienceRoute.READER
            },
            onStory = { route = ReaderExperienceRoute.STORY },
            onWriting = { onEnterWriting(book.id) },
            onAiSetup = onOpenAiSetup,
        )

        ReaderExperienceRoute.READER -> ReaderExperiencePage(
            book = book,
            state = state,
            isLocal = isLocal,
            onBack = onBackToShelf,
            onOpenInfo = { route = ReaderExperienceRoute.INFO },
            onChapter = { chapter, reset -> openChapter(chapter, reset) },
            onStory = { route = ReaderExperienceRoute.STORY },
            onEdit = { onOpenEditor(book.id, it) },
        )

        ReaderExperienceRoute.STORY -> Box(Modifier.fillMaxSize()) {
            StoryPlayPanelV17(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            ReaderFloatingButton(
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                icon = Icons.Rounded.ArrowBack,
                description = "返回阅读",
                onClick = { route = ReaderExperienceRoute.READER },
            )
        }
    }
}

@Composable
private fun ReaderExperiencePage(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    isLocal: Boolean,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onChapter: (Int, Boolean) -> Unit,
    onStory: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(book.id) { context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE) }
    val chapter = state.readingChapter ?: state.chapters.firstOrNull()
    if (chapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("这本书没有可阅读正文") }
        return
    }

    val ordered = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val chapterIndex = ordered.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = ordered.getOrNull(chapterIndex - 1)
    val next = ordered.getOrNull(chapterIndex + 1)

    var chromeVisible by remember { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var archive by remember(book.id) { mutableStateOf(ReaderReadingStoreV11.load(context, book.id)) }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 20f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.82f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph_${book.id}", 12f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent_${book.id}", true)) }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "paper") ?: "paper") }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "serif") ?: "serif") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.COVER.key) ?: ReaderPageModeV10.COVER.key)
    }
    var customBg by remember(book.id) { mutableStateOf(prefs.getString("custom_bg_${book.id}", "#FFF4F0E6") ?: "#FFF4F0E6") }
    var customFg by remember(book.id) { mutableStateOf(prefs.getString("custom_fg_${book.id}", "#FF302D28") ?: "#FF302D28") }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.COVER
    val theme = ReaderExperienceTheme.entries.firstOrNull { it.key == themeKey } ?: ReaderExperienceTheme.PAPER
    val palette = readerExperiencePalette(theme, customBg, customFg)
    val family = remember(fontKey) { readerExperienceFont(fontKey) }
    val scrollState = rememberScrollState()
    val pages = remember(chapter.id, chapter.content, fontSize, lineFactor, sidePadding) {
        splitReaderPagesV10(chapter.content.ifBlank { "这一章没有正文。" }, fontSize, lineFactor, sidePadding)
    }
    val pageCount = pages.size + if (next != null) 1 else 0
    val pagerState = rememberPagerState(pageCount = { pageCount.coerceAtLeast(1) })
    val saved = remember(chapter.id, pageModeKey) {
        ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber)
    }
    var progressRestored by remember(chapter.id, pageModeKey) { mutableStateOf(false) }
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }

    fun contentFractionForPage(page: Int): Float {
        if (pages.isEmpty()) return 0f
        val charsBefore = pages.take(page.coerceIn(0, pages.size)).sumOf { it.length }
        return if (chapter.content.isBlank()) 0f else charsBefore.toFloat() / chapter.content.length.toFloat()
    }

    fun textOffsetForPage(page: Int): Int =
        pages.take(page.coerceIn(0, pages.size)).sumOf { it.length }.coerceAtMost(chapter.content.length)

    fun currentFraction(): Float = when (pageMode) {
        ReaderPageModeV10.SCROLL -> if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        else -> contentFractionForPage(pagerState.currentPage.coerceAtMost(pages.lastIndex.coerceAtLeast(0)))
    }.coerceIn(0f, 1f)

    fun currentTextOffset(): Int = when (pageMode) {
        ReaderPageModeV10.SCROLL -> (chapter.content.length * currentFraction()).roundToInt().coerceIn(0, chapter.content.length)
        else -> textOffsetForPage(pagerState.currentPage)
    }

    fun saveCurrentProgress() {
        if (!progressRestored || crossingChapter) return
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else pagerState.currentPage.coerceAtMost(pages.lastIndex.coerceAtLeast(0)),
                scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
                positionFraction = currentFraction(),
                textOffset = currentTextOffset(),
                modeKey = pageMode.key,
            ),
        )
    }

    fun moveToChapter(target: ChapterDraft?, reset: Boolean = true) {
        target ?: return
        saveCurrentProgress()
        crossingChapter = true
        onChapter(target.chapterNumber, reset)
    }

    LaunchedEffect(chapter.id, pageModeKey, pages.size, scrollState.maxValue) {
        if (progressRestored) return@LaunchedEffect
        if (saved.chapterNumber != chapter.chapterNumber) {
            progressRestored = true
            return@LaunchedEffect
        }
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue <= 0) return@LaunchedEffect
            val fractionFromOffset = if (chapter.content.isNotBlank() && saved.textOffset > 0) {
                saved.textOffset.toFloat() / chapter.content.length.toFloat()
            } else saved.positionFraction
            val target = when {
                fractionFromOffset > 0f -> (scrollState.maxValue * fractionFromOffset.coerceIn(0f, 1f)).roundToInt()
                saved.scrollY > 0 -> saved.scrollY
                else -> 0
            }
            scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
            progressRestored = true
        } else {
            val restorePage = when {
                saved.textOffset > 0 && chapter.content.isNotBlank() -> pageForTextOffset(pages, saved.textOffset)
                saved.positionFraction > 0f -> ((pages.size - 1).coerceAtLeast(0) * saved.positionFraction).roundToInt()
                saved.modeKey == pageMode.key -> saved.pageIndex
                else -> 0
            }.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
            pagerState.scrollToPage(restorePage)
            progressRestored = true
        }
    }

    LaunchedEffect(chapter.id, pageModeKey, progressRestored) {
        if (!progressRestored || pageMode != ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { Triple(scrollState.value, scrollState.maxValue, scrollState.isScrollInProgress) }
            .distinctUntilChanged()
            .collect { (value, max, dragging) ->
                if (max > 0) saveCurrentProgress()
                if (!crossingChapter && dragging && next != null && max > 0 && value >= max - 2) {
                    moveToChapter(next, reset = true)
                }
            }
    }

    LaunchedEffect(chapter.id, pageModeKey, progressRestored) {
        if (!progressRestored || pageMode == ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page >= pages.size && next != null && !crossingChapter) {
                    moveToChapter(next, reset = true)
                } else if (page < pages.size) {
                    saveCurrentProgress()
                }
            }
    }

    DisposableEffect(chapter.id, pageModeKey, progressRestored) {
        onDispose { saveCurrentProgress() }
    }

    LaunchedEffect(fontSize, lineFactor, sidePadding, paragraphSpacing, firstLineIndent, themeKey, fontKey, pageModeKey, customBg, customFg) {
        prefs.edit()
            .putFloat("font_${book.id}", fontSize)
            .putFloat("line_${book.id}", lineFactor)
            .putFloat("padding_${book.id}", sidePadding)
            .putFloat("paragraph_${book.id}", paragraphSpacing)
            .putBoolean("indent_${book.id}", firstLineIndent)
            .putString("theme_${book.id}", themeKey)
            .putString("family_${book.id}", fontKey)
            .putString("page_mode_${book.id}", pageModeKey)
            .putString("custom_bg_${book.id}", customBg)
            .putString("custom_fg_${book.id}", customFg)
            .apply()
    }

    fun toggleBookmark() {
        val fraction = currentFraction()
        archive = ReaderReadingStoreV11.addBookmark(
            context = context,
            bookId = book.id,
            chapterNumber = chapter.chapterNumber,
            pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else pagerState.currentPage,
            scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
            title = chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
            excerpt = readerExcerptAtV11(chapter.content, fraction),
            positionFraction = fraction,
            textOffset = currentTextOffset(),
        )
    }

    val bookmarked = archive.bookmarks.any {
        it.chapterNumber == chapter.chapterNumber && kotlin.math.abs(it.positionFraction - currentFraction()) <= .025f
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> SelectionContainer {
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .clickable { chromeVisible = !chromeVisible }
                        .padding(horizontal = sidePadding.dp)
                        .padding(top = 58.dp, bottom = 78.dp),
                ) {
                    ReaderChapterTitle(chapter, fontSize, family, palette)
                    Spacer(Modifier.height(28.dp))
                    ReaderParagraphs(
                        text = chapter.content.ifBlank { "这一章没有正文。" },
                        fontSize = fontSize,
                        lineFactor = lineFactor,
                        paragraphSpacing = paragraphSpacing,
                        firstLineIndent = firstLineIndent,
                        family = family,
                        color = palette.foreground,
                    )
                    Spacer(Modifier.height(46.dp))
                    if (next != null) {
                        Text(
                            "继续向下滑动 · ${next.title.ifBlank { "第 ${next.chapterNumber} 章" }}",
                            Modifier.fillMaxWidth().padding(bottom = 24.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondary,
                        )
                    } else {
                        Text("— 已读到全书末尾 —", Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = TextAlign.Center, color = palette.secondary)
                    }
                }
            }

            ReaderPageModeV10.PAGE, ReaderPageModeV10.COVER -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = if (pageMode == ReaderPageModeV10.COVER) 10.dp else 0.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                if (page >= pages.size) {
                    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = palette.foreground)
                            Text(
                                next?.title?.ifBlank { "下一章" } ?: "已读完",
                                Modifier.padding(top = 14.dp),
                                color = palette.secondary,
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Surface(
                            modifier = Modifier.fillMaxSize()
                                .padding(if (pageMode == ReaderPageModeV10.COVER) 4.dp else 0.dp)
                                .clickable { chromeVisible = !chromeVisible },
                            shape = RoundedCornerShape(if (pageMode == ReaderPageModeV10.COVER) 14.dp else 0.dp),
                            color = palette.background,
                            contentColor = palette.foreground,
                        ) {
                            Column(
                                Modifier.fillMaxSize()
                                    .padding(horizontal = sidePadding.dp)
                                    .padding(top = 58.dp, bottom = 58.dp),
                            ) {
                                if (page == 0) {
                                    ReaderChapterTitle(chapter, fontSize, family, palette)
                                    Spacer(Modifier.height(22.dp))
                                }
                                Text(
                                    formattedPageText(pages[page], firstLineIndent),
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
        }

        if (!chromeVisible) {
            ReaderQuietFooter(
                modifier = Modifier.align(Alignment.BottomCenter),
                chapterIndex = chapterIndex,
                chapterCount = ordered.size,
                fraction = currentFraction(),
                palette = palette,
            )
        } else {
            ReaderMatureChrome(
                modifier = Modifier.align(Alignment.Center),
                bookTitle = book.title,
                chapter = chapter,
                chapterIndex = chapterIndex,
                chapterCount = ordered.size,
                fraction = currentFraction(),
                palette = palette,
                bookmarked = bookmarked,
                canPrevious = previous != null,
                canNext = next != null,
                onBack = { saveCurrentProgress(); onBack() },
                onInfo = { showInfo = true },
                onBookmark = ::toggleBookmark,
                onPrevious = { moveToChapter(previous, true) },
                onNext = { moveToChapter(next, true) },
                onProgress = { value ->
                    scope.launch {
                        if (pageMode == ReaderPageModeV10.SCROLL) {
                            scrollState.scrollTo((scrollState.maxValue * value).roundToInt().coerceIn(0, scrollState.maxValue))
                        } else {
                            val target = ((pages.size - 1).coerceAtLeast(0) * value).roundToInt().coerceIn(0, pages.lastIndex.coerceAtLeast(0))
                            pagerState.animateScrollToPage(target)
                        }
                    }
                },
                onDirectory = { showDirectory = true },
                onSearch = { showSearch = true },
                onNight = { themeKey = if (themeKey == "night") "paper" else "night" },
                onSettings = { showSettings = true },
                onStory = onStory,
            )
        }
    }

    if (showDirectory) ReaderDirectorySheet(book.id, state, { showDirectory = false }) { number ->
        showDirectory = false
        onChapter(number, true)
    }
    if (showSearch) ReaderSearchSheet(state, { showSearch = false }) { number ->
        showSearch = false
        onChapter(number, true)
    }
    if (showInfo) ReaderQuickInfoSheet(book, state, { showInfo = false }, onOpenInfo)
    if (showSettings) {
        ReaderSettingsSheet(
            bookId = book.id,
            chapter = chapter,
            archive = archive,
            isLocal = isLocal,
            pageModeKey = pageModeKey,
            themeKey = themeKey,
            fontKey = fontKey,
            fontSize = fontSize,
            lineFactor = lineFactor,
            sidePadding = sidePadding,
            paragraphSpacing = paragraphSpacing,
            firstLineIndent = firstLineIndent,
            customBg = customBg,
            customFg = customFg,
            currentFraction = currentFraction(),
            currentTextOffset = currentTextOffset(),
            pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else pagerState.currentPage,
            scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
            onArchive = { archive = it },
            onApplyPreset = { preset ->
                ReaderReadingStoreV11.applyPreset(context, book.id, preset)
                saveCurrentProgress()
                themeKey = preset.themeKey
                fontKey = preset.fontKey
                pageModeKey = preset.pageModeKey
                fontSize = preset.fontSize
                lineFactor = preset.lineFactor
                sidePadding = preset.sidePadding
                paragraphSpacing = preset.paragraphSpacing
                firstLineIndent = preset.firstLineIndent
                customBg = preset.customBg
                customFg = preset.customFg
            },
            onTheme = { themeKey = it },
            onFont = { fontKey = it },
            onPageMode = { saveCurrentProgress(); pageModeKey = it },
            onFontSize = { saveCurrentProgress(); fontSize = it },
            onLine = { saveCurrentProgress(); lineFactor = it },
            onPadding = { saveCurrentProgress(); sidePadding = it },
            onParagraph = { saveCurrentProgress(); paragraphSpacing = it },
            onIndent = { firstLineIndent = it },
            onCustomBg = { customBg = it; themeKey = "custom" },
            onCustomFg = { customFg = it; themeKey = "custom" },
            onSearch = { showSettings = false; showSearch = true },
            onStory = { showSettings = false; onStory() },
            onEdit = { showSettings = false; onEdit(chapter.chapterNumber) },
            onJump = { itemChapter, fraction, offset ->
                showSettings = false
                ReaderProgressStoreV11.moveTo(
                    context, book.id, itemChapter, 0, 0, pageModeKey,
                    positionFraction = fraction,
                    textOffset = offset,
                )
                onChapter(itemChapter, false)
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ReaderMatureChrome(
    modifier: Modifier,
    bookTitle: String,
    chapter: ChapterDraft,
    chapterIndex: Int,
    chapterCount: Int,
    fraction: Float,
    palette: ReaderExperiencePalette,
    bookmarked: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onBookmark: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Float) -> Unit,
    onDirectory: () -> Unit,
    onSearch: () -> Unit,
    onNight: () -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth(),
            color = palette.chrome.copy(alpha = .98f),
            contentColor = palette.foreground,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = palette.foreground) }
                Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
                    Text(chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.foreground, fontWeight = FontWeight.Medium)
                    Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                }
                IconButton(onClick = onBookmark) {
                    Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "书签", tint = palette.foreground)
                }
                IconButton(onClick = onInfo) { Icon(Icons.Rounded.MoreHoriz, "图书信息", tint = palette.foreground) }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth(),
            color = palette.chrome.copy(alpha = .99f),
            contentColor = palette.foreground,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious, enabled = canPrevious) { Text("上一章") }
                    Slider(
                        value = fraction.coerceIn(0f, 1f),
                        onValueChange = onProgress,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onNext, enabled = canNext) { Text("下一章") }
                }
                Text(
                    "第 ${chapterIndex + 1}/$chapterCount 章 · 本章 ${(fraction * 100).roundToInt()}% · 到章末继续翻页自动进入下一章",
                    Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReaderChromeAction(Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
                    ReaderChromeAction(Icons.Rounded.Search, "搜索", palette.foreground, onSearch)
                    ReaderChromeAction(Icons.Rounded.DarkMode, "夜间", palette.foreground, onNight)
                    ReaderChromeAction(Icons.Rounded.Tune, "排版", palette.foreground, onSettings)
                    ReaderChromeAction(Icons.Rounded.AutoAwesome, "故事", palette.foreground, onStory)
                }
            }
        }
    }
}

@Composable
private fun ReaderChromeAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(62.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ReaderQuietFooter(
    modifier: Modifier,
    chapterIndex: Int,
    chapterCount: Int,
    fraction: Float,
    palette: ReaderExperiencePalette,
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .72f))
        Spacer(Modifier.weight(1f))
        Text("${chapterIndex + 1}/$chapterCount · ${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .72f))
    }
}

@Composable
private fun ReaderParagraphs(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) {
        text.replace("\r\n", "\n")
            .split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val style = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineFactor).sp,
        fontFamily = family,
        color = color,
        textIndent = TextIndent(firstLine = if (firstLineIndent) (fontSize * 2f).sp else 0.sp),
    )
    paragraphs.forEachIndexed { index, paragraph ->
        Text(paragraph, style = style)
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}

@Composable
private fun ReaderChapterTitle(chapter: ChapterDraft, fontSize: Float, family: FontFamily, palette: ReaderExperiencePalette) {
    Text(
        chapter.title.ifBlank { "第 ${chapter.chapterNumber} 章" },
        fontSize = (fontSize + 5).sp,
        lineHeight = (fontSize + 11).sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = family,
        color = palette.foreground,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    bookId: String,
    chapter: ChapterDraft,
    archive: ReaderReadingArchiveV11,
    isLocal: Boolean,
    pageModeKey: String,
    themeKey: String,
    fontKey: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    customBg: String,
    customFg: String,
    currentFraction: Float,
    currentTextOffset: Int,
    pageIndex: Int,
    scrollY: Int,
    onArchive: (ReaderReadingArchiveV11) -> Unit,
    onApplyPreset: (ReaderThemePresetV11) -> Unit,
    onTheme: (String) -> Unit,
    onFont: (String) -> Unit,
    onPageMode: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onParagraph: (Float) -> Unit,
    onIndent: (Boolean) -> Unit,
    onCustomBg: (String) -> Unit,
    onCustomFg: (String) -> Unit,
    onSearch: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onJump: (Int, Float, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current
    var page by remember { mutableStateOf(ReaderSettingsPage.HOME) }
    var noteText by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var customPresetName by remember { mutableStateOf("") }
    var fontRefresh by remember { mutableIntStateOf(0) }
    var fontMessage by remember { mutableStateOf<String?>(null) }
    val fonts = remember(fontRefresh) { ReaderFontStoreV10.list(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ReaderFontStoreV10.import(context, uri)
                .onSuccess { asset -> onFont("custom:${asset.path}"); fontRefresh++; fontMessage = "已导入 ${asset.name}" }
                .onFailure { fontMessage = it.message ?: "字体导入失败" }
        }
    }

    val title = when (page) {
        ReaderSettingsPage.HOME -> "阅读设置"
        ReaderSettingsPage.TYPOGRAPHY -> "排版与翻页"
        ReaderSettingsPage.FONTS -> "字体"
        ReaderSettingsPage.PRESETS -> "阅读排版"
        ReaderSettingsPage.BOOKMARKS -> "书签"
        ReaderSettingsPage.NOTES -> "批注"
    }

    ModalBottomSheet(
        onDismissRequest = { if (page == ReaderSettingsPage.HOME) onDismiss() else page = ReaderSettingsPage.HOME },
        containerColor = t.background,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != ReaderSettingsPage.HOME) IconButton(onClick = { page = ReaderSettingsPage.HOME }) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    if (page == ReaderSettingsPage.HOME) Text("排版、翻页、书签与批注", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }

            when (page) {
                ReaderSettingsPage.HOME -> {
                    ReaderSettingsAction(Icons.Rounded.Style, "内置排版", "琅嬛 / 番茄灵感 / 微信读书灵感 / 起点灵感") { page = ReaderSettingsPage.PRESETS }
                    ReaderSettingsAction(Icons.Rounded.Palette, "排版与翻页", "字号、行距、段距、页边距、首行缩进、主题") { page = ReaderSettingsPage.TYPOGRAPHY }
                    ReaderSettingsAction(Icons.Rounded.TextFields, "字体", "内置字体与本地 TTF / OTF") { page = ReaderSettingsPage.FONTS }
                    ReaderSettingsAction(Icons.Rounded.BookmarkBorder, "书签", "稳定文本锚点，换排版后仍能跳回附近") { page = ReaderSettingsPage.BOOKMARKS }
                    ReaderSettingsAction(Icons.Rounded.EditNote, "批注", "记录当前阅读位置与笔记") { page = ReaderSettingsPage.NOTES }
                    ReaderSettingsAction(Icons.Rounded.Search, "全文搜索", "搜索标题与正文", onSearch)
                    ReaderSettingsAction(Icons.Rounded.AutoAwesome, "进入故事", "从当前作品进入沉浸式故事模式", onStory)
                    if (!isLocal) ReaderSettingsAction(Icons.Rounded.Edit, "编辑当前章节", "打开第 ${chapter.chapterNumber} 章编辑器", onEdit)
                    Spacer(Modifier.height(8.dp))
                }

                ReaderSettingsPage.PRESETS -> {
                    Text("内置排版 · 一键切换", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                    Text("参考成熟中文阅读器的留白和信息密度重新调校，不复制品牌 UI。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    readerBuiltInPresetsV12().forEach { preset ->
                        ReaderPresetCard(preset, builtIn = true, onApply = { onApplyPreset(preset) }, onDelete = {})
                    }
                    HorizontalDivider(color = t.border)
                    Text("我的方案", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                    OutlinedTextField(customPresetName, { customPresetName = it }, Modifier.fillMaxWidth(), placeholder = { Text("例如：夜间大字") }, singleLine = true)
                    Button(
                        onClick = {
                            val preset = ReaderReadingStoreV11.capturePreset(context, bookId, customPresetName.ifBlank { "我的排版 ${archive.presets.size + 1}" })
                            onArchive(ReaderReadingStoreV11.savePreset(context, bookId, preset))
                            customPresetName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(7.dp)); Text("保存当前排版") }
                    archive.presets.forEach { preset ->
                        ReaderPresetCard(preset, builtIn = false, onApply = { onApplyPreset(preset) }, onDelete = { onArchive(ReaderReadingStoreV11.deletePreset(context, bookId, preset.id)) })
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReaderSettingsPage.TYPOGRAPHY -> {
                    Text("主题", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                    ReaderExperienceTheme.entries.filter { it != ReaderExperienceTheme.CUSTOM }.forEach { theme ->
                        ReaderSelectRow(theme.label, "阅读背景", selected = themeKey == theme.key) { onTheme(theme.key) }
                    }
                    Text("翻页", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                    ReaderPageModeV10.entries.forEach { mode ->
                        ReaderSelectRow(mode.label, mode.summary, selected = pageModeKey == mode.key) { onPageMode(mode.key) }
                    }
                    ReaderSettingSlider("字号", fontSize, 14f..30f, { "${it.roundToInt()}" }, onFontSize)
                    ReaderSettingSlider("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine)
                    ReaderSettingSlider("段距", paragraphSpacing, 0f..30f, { "${it.roundToInt()} dp" }, onParagraph)
                    ReaderSettingSlider("页边距", sidePadding, 14f..44f, { "${it.roundToInt()} dp" }, onPadding)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("首行缩进", color = t.foreground); Text("中文小说段落默认缩进两字", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
                        Switch(checked = firstLineIndent, onCheckedChange = onIndent)
                    }
                    if (themeKey == "custom") {
                        OutlinedTextField(customBg, onCustomBg, Modifier.fillMaxWidth(), label = { Text("背景色") }, singleLine = true)
                        OutlinedTextField(customFg, onCustomFg, Modifier.fillMaxWidth(), label = { Text("正文色") }, singleLine = true)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReaderSettingsPage.FONTS -> {
                    Button(onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(7.dp)); Text("导入字体")
                    }
                    fontMessage?.let { Text(it, color = t.mutedForeground, style = MaterialTheme.typography.bodySmall) }
                    listOf("default" to "系统默认", "serif" to "衬线", "sans" to "无衬线", "mono" to "等宽").forEach { (key, label) ->
                        ReaderSelectRow(label, "内置字体", selected = fontKey == key, fontFamily = readerExperienceFont(key)) { onFont(key) }
                    }
                    fonts.forEach { asset ->
                        val key = "custom:${asset.path}"
                        Surface(shape = RoundedCornerShape(t.radiusMd), color = if (fontKey == key) t.warmSurface else t.card, border = BorderStroke(1.dp, t.border)) {
                            Row(Modifier.fillMaxWidth().clickable { onFont(key) }.padding(start = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(vertical = 11.dp)) {
                                    Text(asset.name, color = t.foreground, fontFamily = ReaderFontStoreV10.family(asset.path) ?: FontFamily.Default)
                                    Text(asset.path.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                                }
                                if (fontKey == key) Icon(Icons.Rounded.CheckCircle, "已选择", tint = t.accent)
                                IconButton(onClick = {
                                    if (deleteReaderFontV11(context, asset)) {
                                        if (fontKey == key) onFont("default")
                                        fontRefresh++
                                    }
                                }) { Icon(Icons.Rounded.DeleteOutline, "删除字体", tint = t.destructive) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReaderSettingsPage.BOOKMARKS -> {
                    Button(
                        onClick = {
                            onArchive(
                                ReaderReadingStoreV11.addBookmark(
                                    context, bookId, chapter.chapterNumber, pageIndex, scrollY,
                                    chapter.title, readerExcerptAtV11(chapter.content, currentFraction),
                                    positionFraction = currentFraction, textOffset = currentTextOffset,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(7.dp)); Text("添加 / 取消当前位置书签") }
                    if (archive.bookmarks.isEmpty()) ReaderEmptyCard("还没有书签")
                    archive.bookmarks.forEach { item ->
                        ReaderArchiveCard(
                            title = item.title.ifBlank { "第 ${item.chapterNumber} 章" },
                            subtitle = item.excerpt.ifBlank { "阅读位置 ${(item.positionFraction * 100).roundToInt()}%" },
                            onClick = { onJump(item.chapterNumber, item.positionFraction, item.textOffset) },
                            onDelete = { onArchive(ReaderReadingStoreV11.deleteBookmark(context, bookId, item.id)) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                ReaderSettingsPage.NOTES -> {
                    Button(onClick = { noteText = ""; noteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AddComment, null); Spacer(Modifier.width(7.dp)); Text("给当前位置添加批注")
                    }
                    if (archive.annotations.isEmpty()) ReaderEmptyCard("还没有批注")
                    archive.annotations.forEach { item ->
                        ReaderArchiveCard(
                            title = "第 ${item.chapterNumber} 章",
                            subtitle = buildString { if (item.quote.isNotBlank()) append("“${item.quote}”\n"); append(item.note) },
                            onClick = { onJump(item.chapterNumber, item.positionFraction, item.textOffset) },
                            onDelete = { onArchive(ReaderReadingStoreV11.deleteAnnotation(context, bookId, item.id)) },
                        )
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
            text = { OutlinedTextField(noteText, { noteText = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("你的想法") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotBlank()) {
                        onArchive(
                            ReaderReadingStoreV11.addAnnotation(
                                context, bookId, chapter.chapterNumber, pageIndex, scrollY,
                                readerExcerptAtV11(chapter.content, currentFraction, 160), noteText,
                                positionFraction = currentFraction, textOffset = currentTextOffset,
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

@Composable
private fun ReaderPresetCard(preset: ReaderThemePresetV11, builtIn: Boolean, onApply: () -> Unit, onDelete: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
        shape = RoundedCornerShape(t.radiusLg),
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    if (builtIn) {
                        Surface(Modifier.padding(start = 7.dp), shape = RoundedCornerShape(99.dp), color = t.warmSurface) {
                            Text("内置", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = t.accent)
                        }
                    }
                }
                Text(
                    "${preset.fontSize.roundToInt()}号 · 行距 %.2f · 段距 ${preset.paragraphSpacing.roundToInt()} · ${ReaderPageModeV10.entries.firstOrNull { it.key == preset.pageModeKey }?.label ?: "阅读"}".format(preset.lineFactor),
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
            if (!builtIn) IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderSettingsAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(t.radiusMd), color = t.card, border = BorderStroke(1.dp, t.border)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = t.accent)
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(title, color = t.foreground, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
        }
    }
}

@Composable
private fun ReaderSelectRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(t.radiusMd),
        color = if (selected) t.warmSurface else t.card,
        border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .32f) else t.border),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = t.foreground, fontFamily = fontFamily, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = t.accent)
        }
    }
}

@Composable
private fun ReaderSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: (Float) -> String, onValue: (Float) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.foreground)
        Text(text(value), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
    Slider(value = value, onValueChange = onValue, valueRange = range)
}

@Composable
private fun ReaderArchiveCard(title: String, subtitle: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(t.radiusMd), color = t.card, border = BorderStroke(1.dp, t.border)) {
        Row(Modifier.padding(start = 13.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
                Text(title, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderEmptyCard(text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(t.radiusMd), color = t.muted, border = BorderStroke(1.dp, t.border)) {
        Text(text, Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center, color = t.mutedForeground)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderDirectorySheet(bookId: String, state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current
    var query by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    val original = remember(bookId) { EpubOriginalTocV1.load(context, bookId) }
    val originalEntries = remember(original, query, descending) {
        val list = flattenReaderToc(original).filter { query.isBlank() || it.title.contains(query, true) }
        if (descending) list.asReversed() else list
    }
    val chapters = remember(state.chapters, query, descending) {
        val list = state.chapters.sortedBy { it.chapterNumber }.filter { query.isBlank() || it.title.contains(query, true) }
        if (descending) list.asReversed() else list
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(state.openedBook?.title ?: "目录", style = MaterialTheme.typography.titleLarge, color = t.foreground); Text("${state.chapters.size} 章", color = t.mutedForeground) }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("搜索章节") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
                IconButton(onClick = { descending = !descending }) { Icon(if (descending) Icons.Rounded.South else Icons.Rounded.North, "排序") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(top = 8.dp)) {
                if (original.isNotEmpty()) {
                    items(originalEntries, key = { "${it.depth}:${it.chapterNumber}:${it.title}" }) { item ->
                        val current = item.chapterNumber == state.readingChapter?.chapterNumber
                        Row(
                            Modifier.fillMaxWidth().then(if (item.chapterNumber != null) Modifier.clickable { onChapter(item.chapterNumber) } else Modifier)
                                .padding(start = (8 + item.depth * 18).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.title, Modifier.weight(1f), color = if (current) t.accent else t.foreground, fontWeight = if (current || item.chapterNumber == null) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (current) Icon(Icons.Rounded.Check, null, tint = t.accent)
                        }
                    }
                } else {
                    items(chapters, key = { it.id }) { item ->
                        val current = item.id == state.readingChapter?.id
                        Row(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, Modifier.weight(1f), color = if (current) t.accent else t.foreground, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                            if (current) Icon(Icons.Rounded.Check, null, tint = t.accent)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSearchSheet(state: LibraryExperienceState, onDismiss: () -> Unit, onChapter: (Int) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    var query by remember { mutableStateOf("") }
    val results = remember(state.chapters, query) {
        if (query.trim().length < 2) emptyList() else state.chapters.filter { it.title.contains(query, true) || it.content.contains(query, true) }.take(120)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("全文搜索", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = t.foreground); IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") } }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("至少输入两个字") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(top = 8.dp)) {
                items(results, key = { it.id }) { item ->
                    Column(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(vertical = 12.dp)) {
                        Text(item.title, color = t.foreground, fontWeight = FontWeight.SemiBold)
                        Text(searchReaderSnippet(item.content, query), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderQuickInfoSheet(book: ReaderBookUi, state: LibraryExperienceState, onDismiss: () -> Unit, onOpenFull: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val context = LocalContext.current
    val progress = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground)
            Text("上次读到第 ${progress.chapterNumber} 章 · 本章 ${(progress.positionFraction * 100).roundToInt()}%", color = t.mutedForeground)
            LinearProgressIndicator(progress = { ((progress.chapterNumber - 1 + progress.positionFraction) / state.chapters.size.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onDismiss(); onOpenFull() }, modifier = Modifier.fillMaxWidth()) { Text("查看完整详情") }
        }
    }
}

@Composable
private fun ReaderExperienceInfo(
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
    val progress = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
    val total = state.chapters.size.coerceAtLeast(1)
    val overall = ((progress.chapterNumber - 1 + progress.positionFraction) / total.toFloat()).coerceIn(0f, 1f)
    Scaffold(
        containerColor = t.background,
        topBar = {
            TopAppBar(
                title = { Column { Text("图书详情", color = t.foreground); Text(if (isLocal) "本地图书" else "琅嬛创作", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                actions = { if (!isLocal) IconButton(onClick = onAiSetup) { Icon(Icons.Rounded.Tune, "AI 设置") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = t.background),
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(92.dp).height(132.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 17.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall, color = t.foreground)
                    Text(book.genre.ifBlank { "小说" }, Modifier.padding(top = 6.dp), color = t.mutedForeground)
                    Text("${state.chapters.size} 章 · ${book.currentWords} 字", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(t.radiusLg), color = t.card, border = BorderStroke(1.dp, t.border)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${(overall * 100).roundToInt()}%", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = t.foreground)
                        Spacer(Modifier.weight(1f))
                        Text("第 ${progress.chapterNumber} 章 · 本章 ${(progress.positionFraction * 100).roundToInt()}%", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(progress = { overall }, Modifier.fillMaxWidth().padding(top = 12.dp), color = t.accent, trackColor = t.muted)
                    Text("阅读位置会自动记忆，重新进入直接继续。", Modifier.padding(top = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(t.radiusLg), color = t.card, border = BorderStroke(1.dp, t.border)) {
                    Column(Modifier.padding(16.dp)) { Text("简介", color = t.foreground, fontWeight = FontWeight.SemiBold); Text(book.premise, Modifier.padding(top = 9.dp), lineHeight = 23.sp, color = t.mutedForeground) }
                }
            }
            Button(onClick = onRead, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Rounded.MenuBook, null); Spacer(Modifier.width(7.dp)); Text(if (progress.updatedAt > 0) "继续阅读" else "开始阅读") }
            OutlinedButton(onClick = onStory, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("进入故事") }
            if (!isLocal) TextButton(onClick = onWriting, Modifier.fillMaxWidth()) { Text("打开创作工作台") }
            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}

@Composable
private fun ReaderFloatingButton(modifier: Modifier, icon: ImageVector, description: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier.size(44.dp), shape = RoundedCornerShape(t.radiusMd), color = t.card.copy(alpha = .94f), border = BorderStroke(1.dp, t.border), shadowElevation = 4.dp) {
        IconButton(onClick = onClick) { Icon(icon, description, tint = t.foreground) }
    }
}

@Composable
private fun readerExperiencePalette(theme: ReaderExperienceTheme, customBg: String, customFg: String): ReaderExperiencePalette {
    val scheme = MaterialTheme.colorScheme
    val customBackground = parseReaderHexColorV10(customBg) ?: Color(0xFFF4F0E6)
    val customForeground = parseReaderHexColorV10(customFg) ?: Color(0xFF302D28)
    return when (theme) {
        ReaderExperienceTheme.SYSTEM -> ReaderExperiencePalette(scheme.background, scheme.onBackground, scheme.onSurfaceVariant, scheme.surface)
        ReaderExperienceTheme.PAPER -> ReaderExperiencePalette(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF7B756C), Color(0xFFEDE8DB))
        ReaderExperienceTheme.WARM -> ReaderExperiencePalette(Color(0xFFF3E5C9), Color(0xFF362D23), Color(0xFF7E6E5B), Color(0xFFEADAB9))
        ReaderExperienceTheme.GREEN -> ReaderExperiencePalette(Color(0xFFE4ECE0), Color(0xFF283129), Color(0xFF657166), Color(0xFFD8E3D5))
        ReaderExperienceTheme.NIGHT -> ReaderExperiencePalette(Color(0xFF151617), Color(0xFFD7D4CE), Color(0xFF8F8C87), Color(0xFF222324))
        ReaderExperienceTheme.CUSTOM -> ReaderExperiencePalette(customBackground, customForeground, customForeground.copy(alpha = .58f), customBackground)
    }
}

private fun readerExperienceFont(key: String): FontFamily = when {
    key == "serif" -> FontFamily.Serif
    key == "sans" -> FontFamily.SansSerif
    key == "mono" -> FontFamily.Monospace
    key.startsWith("custom:") -> ReaderFontStoreV10.family(key.removePrefix("custom:")) ?: FontFamily.Default
    else -> FontFamily.Default
}

private fun currentReaderMode(context: Context, bookId: String): String =
    context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE)
        .getString("page_mode_$bookId", ReaderPageModeV10.COVER.key) ?: ReaderPageModeV10.COVER.key

private fun pageForTextOffset(pages: List<String>, offset: Int): Int {
    if (pages.isEmpty()) return 0
    var consumed = 0
    pages.forEachIndexed { index, page ->
        consumed += page.length
        if (offset <= consumed) return index
    }
    return pages.lastIndex
}

private fun formattedPageText(text: String, indent: Boolean): String =
    if (!indent) text else text.split(Regex("\\n\\s*\\n")).joinToString("\n\n") { paragraph ->
        val clean = paragraph.trim()
        if (clean.isBlank()) clean else "　　$clean"
    }

private fun flattenReaderToc(nodes: List<EpubTocNodeV1>, depth: Int = 0): List<ReaderTocEntry> = buildList {
    nodes.forEach { node ->
        add(ReaderTocEntry(node.title, node.chapterNumber, depth))
        addAll(flattenReaderToc(node.children, depth + 1))
    }
}

private fun searchReaderSnippet(text: String, query: String): String {
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return text.replace('\n', ' ').take(100)
    val start = (index - 40).coerceAtLeast(0)
    val end = (index + query.length + 65).coerceAtMost(text.length)
    return text.substring(start, end).replace('\n', ' ').trim()
}
