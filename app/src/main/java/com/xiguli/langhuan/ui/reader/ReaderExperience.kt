package com.xiguli.langhuan.ui.reader

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
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
import com.xiguli.langhuan.ui.story.StoryCleanExperience
import com.xiguli.langhuan.ui.shell.StudioUiState
import com.xiguli.langhuan.ui.cover.CoverPreviewV3
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import com.xiguli.langhuan.ui.shell.verticalScroll
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.launch

private enum class ReaderExperienceRoute { INFO, READER, STORY }
private enum class ReaderSettingsTab(val label: String) { DISPLAY("排版"), FONT("字体"), MARKS("书签批注") }
private enum class ReaderExperienceTheme(val key: String, val label: String) {
    SYSTEM("system", "默认"), PAPER("paper", "纸张"), WARM("warm", "暖黄"),
    GREEN("green", "护眼"), NIGHT("night", "夜间"), CUSTOM("custom", "自定义")
}
internal data class ReaderExperiencePalette(val background: Color, val foreground: Color, val secondary: Color, val chrome: Color)
private data class ReaderTocEntry(val title: String, val chapterNumber: Int?, val depth: Int)

/**
 * Reader shell rebuilt around three invariants used by mature readers:
 * 1. resolve the saved chapter before rendering any chapter; never use chapter 1 as a temporary UI fallback;
 * 2. persist chapter + stable textOffset, while page index is only a same-layout convenience;
 * 3. paginate and render only inside safeDrawing, then cross chapter boundaries after a pager settles.
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

    // Critical resume gate: do not compose the first chapter while the persisted chapter is still resolving.
    LaunchedEffect(book.id, state.chapters.size) {
        if (state.chapters.isNotEmpty() && state.readingChapter == null) {
            val saved = ReaderProgressStoreV11.load(context, book.id, book.currentChapter.coerceAtLeast(1))
            val target = state.chapters.firstOrNull { it.chapterNumber == saved.chapterNumber }
                ?: state.chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                ?: state.chapters.first()
            viewModel.openReader(target.chapterNumber)
        }
    }

    fun openChapter(number: Int, resetPosition: Boolean, atEnd: Boolean = false) {
        if (resetPosition) {
            ReaderProgressStoreV11.moveTo(
                context = context,
                bookId = book.id,
                chapterNumber = number,
                pageIndex = 0,
                scrollY = 0,
                modeKey = currentReaderMode(context, book.id),
                positionFraction = if (atEnd) 1f else 0f,
                textOffset = if (atEnd) Int.MAX_VALUE else 0,
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

        ReaderExperienceRoute.READER -> {
            val chapter = state.readingChapter
            if (chapter == null) {
                ReaderResumeGate(book.title)
            } else {
                ReaderExperiencePage(
                    book = book,
                    state = state,
                    chapter = chapter,
                    isLocal = isLocal,
                    onBack = onBackToShelf,
                    onOpenInfo = { route = ReaderExperienceRoute.INFO },
                    onChapter = ::openChapter,
                    onStory = { route = ReaderExperienceRoute.STORY },
                    onEdit = { onOpenEditor(book.id, it) },
                )
            }
        }

        ReaderExperienceRoute.STORY -> Box(Modifier.fillMaxSize()) {
            StoryCleanExperience(
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
private fun ReaderResumeGate(title: String) {
    val t = LocalLanghuanUiTokens.current
    Box(Modifier.fillMaxSize().background(t.background).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = t.accent)
            Text("正在恢复阅读位置", Modifier.padding(top = 14.dp), color = t.foreground, fontWeight = FontWeight.Medium)
            Text(title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}

@Composable
private fun ReaderExperiencePage(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    chapter: ChapterDraft,
    isLocal: Boolean,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onChapter: (Int, Boolean, Boolean) -> Unit,
    onStory: () -> Unit,
    onEdit: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(book.id) {
        context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE).also {
            migrateReaderTypographyV14(it, book.id)
        }
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
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.68f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph_${book.id}", 8f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent_${book.id}", true)) }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "paper") ?: "paper") }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "serif") ?: "serif") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.PAGE.key) ?: ReaderPageModeV10.PAGE.key)
    }
    var customBg by remember(book.id) { mutableStateOf(prefs.getString("custom_bg_${book.id}", "#FFF4F0E6") ?: "#FFF4F0E6") }
    var customFg by remember(book.id) { mutableStateOf(prefs.getString("custom_fg_${book.id}", "#FF302D28") ?: "#FF302D28") }
    var presetBaseId by remember(book.id) { mutableStateOf(prefs.getString("preset_base_${book.id}", "").orEmpty()) }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val theme = ReaderExperienceTheme.entries.firstOrNull { it.key == themeKey } ?: ReaderExperienceTheme.PAPER
    val palette = readerExperiencePalette(theme, customBg, customFg)
    val family = remember(fontKey) { readerExperienceFont(fontKey) }
    val displayTitle = remember(chapter.id, chapter.title, chapter.chapterNumber) {
        readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
    }
    val readingText = remember(chapter.id, chapter.title, chapter.content) {
        readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
            .ifBlank { "这一章没有正文。" }
    }
    val scrollState = rememberScrollState()
    val measuredPagination = rememberReaderMeasuredPaginationV16(
        text = readingText,
        displayTitle = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val pages = measuredPagination.pages
    val pageOffsets = measuredPagination.offsets
    val leadingPageCount = if (previous != null) 1 else 0
    val trailingPageCount = if (next != null) 1 else 0
    val totalPagerPages = (leadingPageCount + pages.size + trailingPageCount).coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { totalPagerPages })
    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${sidePadding.roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measuredPagination.layoutToken}"
    var progressRestored by remember(chapter.id) { mutableStateOf(false) }
    var reflowAnchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf("") }
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }

    fun contentPageFromPager(pagerPage: Int): Int = (pagerPage - leadingPageCount).coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    fun pagerIsContent(page: Int): Boolean = page in leadingPageCount until (leadingPageCount + pages.size)
    fun currentContentPage(): Int = contentPageFromPager(pagerState.settledPage)

    fun currentTextOffset(): Int = when (pageMode) {
        ReaderPageModeV10.SCROLL -> {
            val fraction = if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            (readingText.length * fraction).roundToInt().coerceIn(0, readingText.length)
        }
        else -> pageOffsets.getOrElse(currentContentPage()) { 0 }.coerceIn(0, readingText.length)
    }

    fun currentFraction(): Float = when (pageMode) {
        ReaderPageModeV10.SCROLL -> if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        else -> if (readingText.isBlank()) 0f else currentTextOffset().toFloat() / readingText.length.toFloat()
    }.coerceIn(0f, 1f)

    fun saveCurrentProgress() {
        if (!progressRestored || crossingChapter) return
        if (pageMode != ReaderPageModeV10.SCROLL && !pagerIsContent(pagerState.settledPage)) return
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else currentContentPage(),
                scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
                positionFraction = currentFraction(),
                textOffset = currentTextOffset(),
                modeKey = pageMode.key,
            ),
        )
    }

    fun rememberCurrentAnchor() {
        if (progressRestored) reflowAnchorOffset = currentTextOffset().coerceIn(0, readingText.length)
        saveCurrentProgress()
    }

    fun moveChapter(target: ChapterDraft?, reset: Boolean = true, atEnd: Boolean = false) {
        target ?: return
        saveCurrentProgress()
        crossingChapter = true
        onChapter(target.chapterNumber, reset, atEnd)
    }

    // Initial chapter restore is the only operation allowed to cover the page. Layout changes after
    // that keep the old page visible and silently remap the stable textOffset to the new layout.
    LaunchedEffect(chapter.id, pages.size, scrollState.maxValue) {
        if (progressRestored) return@LaunchedEffect
        if (saved.chapterNumber != chapter.chapterNumber) return@LaunchedEffect
        val restoredOffset = when {
            saved.textOffset > 0 -> saved.textOffset.coerceIn(0, readingText.length)
            saved.positionFraction > 0f -> (readingText.length * saved.positionFraction).roundToInt().coerceIn(0, readingText.length)
            else -> 0
        }
        if (pageMode == ReaderPageModeV10.SCROLL) {
            val fraction = if (readingText.isBlank()) 0f else restoredOffset.toFloat() / readingText.length.toFloat()
            if (fraction > 0f && scrollState.maxValue <= 0) return@LaunchedEffect
            if (scrollState.maxValue > 0) scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue))
        } else {
            val contentPage = when {
                restoredOffset > 0 -> pageForRawTextOffset(pageOffsets, restoredOffset)
                saved.modeKey == pageMode.key -> saved.pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
                else -> 0
            }
            pagerState.scrollToPage((leadingPageCount + contentPage).coerceIn(0, totalPagerPages - 1))
        }
        reflowAnchorOffset = restoredOffset
        appliedLayoutKey = layoutKey
        progressRestored = true
    }

    // Font/line/page-mode changes used to reset progressRestored and paint a full-page loading layer,
    // which is the visible white flash in the recordings. Reflow now happens behind the stable page.
    LaunchedEffect(layoutKey, progressRestored, pages.size, scrollState.maxValue) {
        if (!progressRestored || appliedLayoutKey == layoutKey) return@LaunchedEffect
        val anchorOffset = reflowAnchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            val fraction = if (readingText.isBlank()) 0f else anchorOffset.toFloat() / readingText.length.toFloat()
            if (fraction > 0f && scrollState.maxValue <= 0) return@LaunchedEffect
            if (scrollState.maxValue > 0) scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue))
        } else {
            val contentPage = pageForRawTextOffset(pageOffsets, anchorOffset)
            pagerState.scrollToPage((leadingPageCount + contentPage).coerceIn(0, totalPagerPages - 1))
        }
        appliedLayoutKey = layoutKey
    }

    // Scroll progress is deliberately debounced; the old reader did a synchronous preference commit for almost every pixel.
    LaunchedEffect(chapter.id, layoutKey, progressRestored) {
        if (!progressRestored || pageMode != ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collectLatest {
                delay(180)
                saveCurrentProgress()
            }
    }

    LaunchedEffect(chapter.id, layoutKey, progressRestored) {
        if (!progressRestored || pageMode != ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { Triple(scrollState.value, scrollState.maxValue, scrollState.isScrollInProgress) }
            .distinctUntilChanged()
            .collect { (value, max, dragging) ->
                if (!crossingChapter && dragging && next != null && max > 0 && value >= max - 1) {
                    moveChapter(next, reset = true, atEnd = false)
                }
            }
    }

    // Cross chapters only after the horizontal pager SETTLES. currentPage changes during a drag and was a source of old-page races.
    LaunchedEffect(chapter.id, layoutKey, progressRestored) {
        if (!progressRestored || pageMode == ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                when {
                    leadingPageCount == 1 && page == 0 && previous != null && !crossingChapter -> {
                        moveChapter(previous, reset = true, atEnd = true)
                    }
                    page >= leadingPageCount + pages.size && next != null && !crossingChapter -> {
                        moveChapter(next, reset = true, atEnd = false)
                    }
                    pagerIsContent(page) -> saveCurrentProgress()
                }
            }
    }

    DisposableEffect(chapter.id, progressRestored) {
        onDispose { saveCurrentProgress() }
    }

    // UI preferences are asynchronous and cheap. Progress durability is handled separately above.
    LaunchedEffect(fontSize, lineFactor, sidePadding, paragraphSpacing, firstLineIndent, themeKey, fontKey, pageModeKey, customBg, customFg, presetBaseId) {
        delay(100)
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
            .putString("preset_base_${book.id}", presetBaseId)
            .apply()
    }

    fun toggleBookmark() {
        val fraction = currentFraction()
        archive = ReaderReadingStoreV11.addBookmark(
            context, book.id, chapter.chapterNumber,
            if (pageMode == ReaderPageModeV10.SCROLL) 0 else currentContentPage(),
            if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
            displayTitle,
            readerExcerptAtV11(readingText, fraction), fraction, currentTextOffset(),
        )
    }

    val bookmarked = archive.bookmarks.any {
        it.chapterNumber == chapter.chapterNumber && abs(it.positionFraction - currentFraction()) <= .025f
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        // Reading content and all persistent chrome share the same safe viewport.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (pageMode) {
                ReaderPageModeV10.SCROLL -> SelectionContainer {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(scrollState)
                            .clickable { chromeVisible = !chromeVisible }
                            .padding(horizontal = sidePadding.dp)
                            .padding(top = 22.dp, bottom = 58.dp),
                    ) {
                        ReaderChapterTitle(chapter, fontSize, family, palette)
                        Spacer(Modifier.height(16.dp))
                        ReaderParagraphs(
                            readingText, fontSize, lineFactor,
                            paragraphSpacing, firstLineIndent, family, palette.foreground,
                        )
                        Spacer(Modifier.height(42.dp))
                        Text(
                            if (next != null) "继续向下滑动 · ${next.title.ifBlank { "第 ${next.chapterNumber} 章" }}" else "— 已读到全书末尾 —",
                            Modifier.fillMaxWidth().padding(bottom = 22.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.secondary,
                        )
                    }
                }

                ReaderPageModeV10.PAGE, ReaderPageModeV10.COVER -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        beyondViewportPageCount = if (pageMode == ReaderPageModeV10.COVER) 0 else 1,
                    ) { page ->
                        when {
                            leadingPageCount == 1 && page == 0 -> ReaderChapterSentinel(previous, "上一章", palette)
                            page >= leadingPageCount + pages.size -> ReaderChapterSentinel(next, "下一章", palette)
                            else -> {
                                val contentPage = contentPageFromPager(page)
                                val pageBookFraction = (
                                    (chapterIndex.toFloat() + (contentPage + 1f) / pages.size.coerceAtLeast(1).toFloat()) /
                                        ordered.size.coerceAtLeast(1).toFloat()
                                    ).coerceIn(0f, 1f)
                                ReaderPagedLayoutV14(
                                    pageText = pages[contentPage],
                                    contentPage = contentPage,
                                    pageCount = pages.size,
                                    displayTitle = displayTitle,
                                    fontSize = fontSize,
                                    lineFactor = lineFactor,
                                    sidePadding = sidePadding,
                                    paragraphSpacing = paragraphSpacing,
                                    firstLineIndent = firstLineIndent,
                                    indentFirstParagraph = measuredPagination.indentFirstParagraph.getOrElse(contentPage) { true },
                                    family = family,
                                    background = palette.background,
                                    foreground = palette.foreground,
                                    secondary = palette.secondary,
                                    overallFraction = pageBookFraction,
                                    onToggleChrome = { chromeVisible = !chromeVisible },
                                )
                            }
                        }
                    }
                    if (!progressRestored) Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.secondary)
                    }
                }
            }

            if (!chromeVisible && pageMode == ReaderPageModeV10.SCROLL) {
                val overall = ((chapterIndex.toFloat() + currentFraction()) / ordered.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                ReaderQuietFooter(Modifier.align(Alignment.BottomCenter), overall, palette)
            } else if (chromeVisible) {
                ReaderQingmoChrome(
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
                    fontKey = fontKey,
                    lineFactor = lineFactor,
                    pageModeKey = pageModeKey,
                    onBack = { saveCurrentProgress(); onBack() },
                    onInfo = { showInfo = true },
                    onBookmark = ::toggleBookmark,
                    onPrevious = { moveChapter(previous, reset = true, atEnd = false) },
                    onNext = { moveChapter(next, reset = true, atEnd = false) },
                    onProgress = { value ->
                        scope.launch {
                            saveCurrentProgress()
                            if (pageMode == ReaderPageModeV10.SCROLL) {
                                scrollState.scrollTo((scrollState.maxValue * value).roundToInt().coerceIn(0, scrollState.maxValue))
                            } else {
                                val offset = (readingText.length * value).roundToInt()
                                val contentPage = pageForRawTextOffset(pageOffsets, offset)
                                pagerState.animateScrollToPage((leadingPageCount + contentPage).coerceIn(0, totalPagerPages - 1))
                            }
                        }
                    },
                    onDirectory = { showDirectory = true },
                    onSearch = { showSearch = true },
                    onNight = { themeKey = if (themeKey == "night") "paper" else "night" },
                    onFontKey = { key -> rememberCurrentAnchor(); fontKey = key },
                    onLineFactor = { value -> rememberCurrentAnchor(); lineFactor = value },
                    onPageMode = { key -> rememberCurrentAnchor(); pageModeKey = key },
                    onSettings = { showSettings = true },
                    onStory = onStory,
                )
            }
        }
    }

    if (showDirectory) ReaderDirectorySheet(book.id, state, { showDirectory = false }) { number ->
        showDirectory = false
        onChapter(number, true, false)
    }
    if (showSearch) ReaderSearchSheet(state, { showSearch = false }) { number ->
        showSearch = false
        onChapter(number, true, false)
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
            presetBaseId = presetBaseId,
            currentFraction = currentFraction(),
            currentTextOffset = currentTextOffset(),
            pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else currentContentPage(),
            scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
            onArchive = { archive = it },
            onApplyPreset = { preset ->
                rememberCurrentAnchor()
                ReaderReadingStoreV11.applyPreset(context, book.id, preset)
                presetBaseId = preset.id
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
            onFont = { rememberCurrentAnchor(); fontKey = it },
            onPageMode = { rememberCurrentAnchor(); pageModeKey = it },
            onFontSize = { rememberCurrentAnchor(); fontSize = it },
            onLine = { rememberCurrentAnchor(); lineFactor = it },
            onPadding = { rememberCurrentAnchor(); sidePadding = it },
            onParagraph = { rememberCurrentAnchor(); paragraphSpacing = it },
            onIndent = { rememberCurrentAnchor(); firstLineIndent = it },
            onCustomBg = { customBg = it; themeKey = "custom" },
            onCustomFg = { customFg = it; themeKey = "custom" },
            onSearch = { showSettings = false; showSearch = true },
            onStory = { showSettings = false; onStory() },
            onEdit = { showSettings = false; onEdit(chapter.chapterNumber) },
            onJump = { itemChapter, fraction, offset ->
                showSettings = false
                ReaderProgressStoreV11.moveTo(context, book.id, itemChapter, 0, 0, pageModeKey, fraction, offset)
                onChapter(itemChapter, false, false)
            },
            onDismiss = { saveCurrentProgress(); showSettings = false },
        )
    }
}

@Composable
private fun ReaderChapterSentinel(chapter: ChapterDraft?, direction: String, palette: ReaderExperiencePalette) {
    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.secondary)
            Text(direction, Modifier.padding(top = 12.dp), color = palette.foreground, fontWeight = FontWeight.Medium)
            Text(chapter?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) } ?: "没有更多章节", Modifier.padding(top = 5.dp), color = palette.secondary)
        }
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
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = palette.chrome.copy(alpha = .98f),
            contentColor = palette.foreground,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = palette.foreground) }
                Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
                    Text(readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber), maxLines = 1, overflow = TextOverflow.Ellipsis, color = palette.foreground, fontWeight = FontWeight.Medium)
                    Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                }
                IconButton(onClick = onBookmark) { Icon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "书签", tint = palette.foreground) }
                IconButton(onClick = onInfo) { Icon(Icons.Rounded.MoreHoriz, "图书信息", tint = palette.foreground) }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = palette.chrome.copy(alpha = .99f),
            contentColor = palette.foreground,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious, enabled = canPrevious) { Text("上一章") }
                    Slider(value = fraction.coerceIn(0f, 1f), onValueChange = onProgress, modifier = Modifier.weight(1f))
                    TextButton(onClick = onNext, enabled = canNext) { Text("下一章") }
                }
                Text(
                    "${readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)} · 本章 ${(fraction * 100).roundToInt()}% · 左右翻页可跨章",
                    Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = palette.secondary,
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
    Column(Modifier.width(62.dp).clickable(onClick = onClick).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ReaderQuietFooter(modifier: Modifier, overallFraction: Float, palette: ReaderExperiencePalette) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .72f))
        Spacer(Modifier.weight(1f))
        Text("全书 ${(overallFraction.coerceIn(0f, 1f) * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .72f))
    }
}

@Composable
private fun ReaderParagraphs(text: String, fontSize: Float, lineFactor: Float, paragraphSpacing: Float, firstLineIndent: Boolean, family: FontFamily, color: Color) {
    val paragraphs = remember(text) { text.replace("\r\n", "\n").split(Regex("\\n+")).map { it.trim() }.filter { it.isNotBlank() } }
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
    Text(readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber), fontSize = (fontSize + 3).sp, lineHeight = (fontSize + 8).sp, fontWeight = FontWeight.SemiBold, fontFamily = family, color = palette.foreground)
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
    presetBaseId: String,
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
    var tab by remember { mutableStateOf(ReaderSettingsTab.DISPLAY) }
    var noteText by remember { mutableStateOf("") }
    var noteDialog by remember { mutableStateOf(false) }
    var customPresetName by remember { mutableStateOf("") }
    var fontRefresh by remember { mutableIntStateOf(0) }
    var fontMessage by remember { mutableStateOf<String?>(null) }
    val fonts = remember(fontRefresh) { ReaderFontStoreV10.list(context) }
    val builtIns = remember { readerBuiltInPresetsV12() }
    val allPresets = remember(builtIns, archive.presets) { builtIns + archive.presets }
    val basePreset = allPresets.firstOrNull { it.id == presetBaseId }
    val exactPreset = allPresets.firstOrNull {
        readerPresetMatches(it, themeKey, fontKey, pageModeKey, fontSize, lineFactor, sidePadding, paragraphSpacing, firstLineIndent, customBg, customFg)
    }
    val statusText = when {
        exactPreset != null -> "当前：${exactPreset.name} · 已应用"
        basePreset != null -> "当前：${basePreset.name} · 已微调"
        else -> "当前：自定义排版"
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ReaderFontStoreV10.import(context, uri)
                .onSuccess { asset -> onFont("custom:${asset.path}"); fontRefresh++; fontMessage = "已导入 ${asset.name}" }
                .onFailure { fontMessage = it.message ?: "字体导入失败" }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("阅读设置", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (exactPreset != null) t.accent else t.mutedForeground)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderSettingsTab.entries.forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.label) }, modifier = Modifier.weight(1f))
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 690.dp).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    ReaderSettingsTab.DISPLAY -> {
                        item {
                            Text("排版方案", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                            Text("选择后仍可继续细调；被细调的方案会明确显示“已微调”。", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        items(builtIns, key = { it.id }) { preset ->
                            ReaderPresetCard(
                                preset = preset,
                                builtIn = true,
                                selected = exactPreset?.id == preset.id,
                                basedOn = exactPreset == null && presetBaseId == preset.id,
                                onApply = { onApplyPreset(preset) },
                                onDelete = {},
                            )
                        }
                        item {
                            Surface(Modifier.fillMaxWidth(), shape = LanghuanShape.card, color = t.warmSurface, border = BorderStroke(1.dp, t.accent.copy(alpha = .25f))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Tune, null, tint = t.accent)
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text("细调当前排版", color = t.foreground, fontWeight = FontWeight.SemiBold)
                                        Text("下面的字号、行距、段距、页边距、主题、翻页方式都可以继续改。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                                    }
                                }
                            }
                        }
                        item {
                            Text("主题", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReaderExperienceTheme.entries.forEach { theme ->
                                    ReaderSelectRow(theme.label, if (theme == ReaderExperienceTheme.CUSTOM) "自定义背景与正文色" else "阅读背景", themeKey == theme.key) { onTheme(theme.key) }
                                }
                            }
                        }
                        item {
                            Text("翻页方式", style = MaterialTheme.typography.labelLarge, color = t.foreground)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReaderPageModeV10.entries.forEach { mode ->
                                    ReaderSelectRow(mode.label, mode.summary, pageModeKey == mode.key) { onPageMode(mode.key) }
                                }
                            }
                        }
                        item { ReaderSettingSlider("字号", fontSize, 14f..30f, { "${it.roundToInt()}" }, onFontSize) }
                        item { ReaderSettingSlider("行距", lineFactor, 1.42f..2.25f, { "%.2f".format(it) }, onLine) }
                        item { ReaderSettingSlider("段距", paragraphSpacing, 0f..30f, { "${it.roundToInt()} dp" }, onParagraph) }
                        item { ReaderSettingSlider("页边距", sidePadding, 14f..44f, { "${it.roundToInt()} dp" }, onPadding) }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("首行缩进", color = t.foreground); Text("中文小说默认缩进两字", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
                                Switch(checked = firstLineIndent, onCheckedChange = onIndent)
                            }
                        }
                        if (themeKey == "custom") {
                            item { OutlinedTextField(customBg, onCustomBg, Modifier.fillMaxWidth(), label = { Text("背景色 #AARRGGBB") }, singleLine = true) }
                            item { OutlinedTextField(customFg, onCustomFg, Modifier.fillMaxWidth(), label = { Text("正文色 #AARRGGBB") }, singleLine = true) }
                        }
                        item {
                            HorizontalDivider(color = t.border)
                            Text("我的方案", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, color = t.foreground)
                            OutlinedTextField(customPresetName, { customPresetName = it }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = { Text("例如：夜间大字") }, singleLine = true)
                            Button(
                                onClick = {
                                    val preset = ReaderThemePresetV11(
                                        id = "custom-${System.currentTimeMillis()}",
                                        name = customPresetName.ifBlank { "我的排版 ${archive.presets.size + 1}" },
                                        themeKey = themeKey,
                                        fontKey = fontKey,
                                        pageModeKey = pageModeKey,
                                        fontSize = fontSize,
                                        lineFactor = lineFactor,
                                        sidePadding = sidePadding,
                                        paragraphSpacing = paragraphSpacing,
                                        firstLineIndent = firstLineIndent,
                                        customBg = customBg,
                                        customFg = customFg,
                                    )
                                    onArchive(ReaderReadingStoreV11.savePreset(context, bookId, preset))
                                    customPresetName = ""
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(7.dp)); Text("保存当前排版") }
                        }
                        items(archive.presets, key = { it.id }) { preset ->
                            ReaderPresetCard(
                                preset, false,
                                selected = exactPreset?.id == preset.id,
                                basedOn = exactPreset == null && presetBaseId == preset.id,
                                onApply = { onApplyPreset(preset) },
                                onDelete = { onArchive(ReaderReadingStoreV11.deletePreset(context, bookId, preset.id)) },
                            )
                        }
                    }

                    ReaderSettingsTab.FONT -> {
                        item {
                            Button(onClick = { launcher.launch(arrayOf("font/ttf", "font/otf", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(7.dp)); Text("导入 TTF / OTF 字体")
                            }
                            fontMessage?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
                        }
                        items(listOf("default" to "系统默认", "serif" to "衬线", "sans" to "无衬线", "mono" to "等宽")) { (key, label) ->
                            ReaderSelectRow(label, "内置字体", fontKey == key, readerExperienceFont(key)) { onFont(key) }
                        }
                        items(fonts, key = { it.id }) { asset ->
                            val key = "custom:${asset.path}"
                            Surface(shape = LanghuanShape.card, color = if (fontKey == key) t.warmSurface else t.card, border = BorderStroke(1.dp, if (fontKey == key) t.accent.copy(alpha = .32f) else t.border)) {
                                Row(Modifier.fillMaxWidth().clickable { onFont(key) }.padding(start = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(vertical = 11.dp)) {
                                        Text(asset.name, color = t.foreground, fontFamily = ReaderFontStoreV10.family(asset.path) ?: FontFamily.Default)
                                        Text(asset.path.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                                    }
                                    if (fontKey == key) Icon(Icons.Rounded.CheckCircle, "已选择", tint = t.accent)
                                    IconButton(onClick = { if (deleteReaderFontV11(context, asset)) { if (fontKey == key) onFont("default"); fontRefresh++ } }) {
                                        Icon(Icons.Rounded.DeleteOutline, "删除字体", tint = t.destructive)
                                    }
                                }
                            }
                        }
                    }

                    ReaderSettingsTab.MARKS -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onArchive(ReaderReadingStoreV11.addBookmark(context, bookId, chapter.chapterNumber, pageIndex, scrollY, chapter.title, readerExcerptAtV11(chapter.content, currentFraction), currentFraction, currentTextOffset))
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(5.dp)); Text("书签") }
                                OutlinedButton(onClick = { noteText = ""; noteDialog = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.AddComment, null); Spacer(Modifier.width(5.dp)); Text("批注")
                                }
                            }
                        }
                        item { ReaderSettingsAction(Icons.Rounded.Search, "全文搜索", "搜索标题与正文", onSearch) }
                        item { ReaderSettingsAction(Icons.Rounded.AutoAwesome, "进入故事", "从当前作品进入故事模式", onStory) }
                        if (!isLocal) item { ReaderSettingsAction(Icons.Rounded.Edit, "编辑当前章节", "第 ${chapter.chapterNumber} 章", onEdit) }
                        if (archive.bookmarks.isEmpty() && archive.annotations.isEmpty()) item { ReaderEmptyCard("还没有书签或批注") }
                        items(archive.bookmarks, key = { "b:${it.id}" }) { item ->
                            ReaderArchiveCard(item.title.ifBlank { "第 ${item.chapterNumber} 章" }, item.excerpt.ifBlank { "阅读位置 ${(item.positionFraction * 100).roundToInt()}%" }, { onJump(item.chapterNumber, item.positionFraction, item.textOffset) }, { onArchive(ReaderReadingStoreV11.deleteBookmark(context, bookId, item.id)) })
                        }
                        items(archive.annotations, key = { "n:${it.id}" }) { item ->
                            ReaderArchiveCard("第 ${item.chapterNumber} 章 · 批注", buildString { if (item.quote.isNotBlank()) append("“${item.quote}”\n"); append(item.note) }, { onJump(item.chapterNumber, item.positionFraction, item.textOffset) }, { onArchive(ReaderReadingStoreV11.deleteAnnotation(context, bookId, item.id)) })
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (noteDialog) AlertDialog(
        onDismissRequest = { noteDialog = false },
        title = { Text("添加批注") },
        text = { OutlinedTextField(noteText, { noteText = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("你的想法") }) },
        confirmButton = {
            TextButton(onClick = {
                if (noteText.isNotBlank()) {
                    onArchive(ReaderReadingStoreV11.addAnnotation(context, bookId, chapter.chapterNumber, pageIndex, scrollY, readerExcerptAtV11(chapter.content, currentFraction, 160), noteText, currentFraction, currentTextOffset))
                    noteDialog = false
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("取消") } },
    )
}

@Composable
private fun ReaderPresetCard(preset: ReaderThemePresetV11, builtIn: Boolean, selected: Boolean, basedOn: Boolean, onApply: () -> Unit, onDelete: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply),
        shape = LanghuanShape.panel,
        color = if (selected || basedOn) t.warmSurface else t.card,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected || basedOn) t.accent.copy(alpha = if (selected) .7f else .34f) else t.border),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    if (builtIn) ReaderPresetBadge("内置", t.mutedForeground)
                    if (selected) ReaderPresetBadge("已应用", t.accent)
                    else if (basedOn) ReaderPresetBadge("已微调", t.warning)
                }
                Text(
                    "${preset.fontSize.roundToInt()}号 · 行距 %.2f · 段距 ${preset.paragraphSpacing.roundToInt()} · ${ReaderPageModeV10.entries.firstOrNull { it.key == preset.pageModeKey }?.label ?: "阅读"}".format(preset.lineFactor),
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground,
                )
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "当前排版", tint = t.accent) else Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
            if (!builtIn) IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderPresetBadge(text: String, color: Color) {
    Surface(Modifier.padding(start = 6.dp), shape = LanghuanShape.pill, color = color.copy(alpha = .1f)) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun readerPresetMatches(
    preset: ReaderThemePresetV11,
    themeKey: String,
    fontKey: String,
    pageModeKey: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    customBg: String,
    customFg: String,
): Boolean = preset.themeKey == themeKey && preset.fontKey == fontKey && preset.pageModeKey == pageModeKey &&
    abs(preset.fontSize - fontSize) < .05f && abs(preset.lineFactor - lineFactor) < .01f &&
    abs(preset.sidePadding - sidePadding) < .05f && abs(preset.paragraphSpacing - paragraphSpacing) < .05f &&
    preset.firstLineIndent == firstLineIndent && preset.customBg.equals(customBg, true) && preset.customFg.equals(customFg, true)

@Composable
private fun ReaderSettingsAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = t.accent)
            Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(title, color = t.foreground, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
            Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
        }
    }
}

@Composable
private fun ReaderSelectRow(title: String, subtitle: String, selected: Boolean, fontFamily: FontFamily? = null, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = LanghuanShape.card, color = if (selected) t.warmSurface else t.card, border = BorderStroke(1.dp, if (selected) t.accent.copy(alpha = .4f) else t.border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = t.foreground, fontFamily = fontFamily, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选择", tint = t.accent)
        }
    }
}

/** Slider only applies the layout value after the finger is released, preventing continuous repagination jitter. */
@Composable
private fun ReaderSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, text: (Float) -> String, onValue: (Float) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    var draft by remember(value) { mutableFloatStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.foreground)
        Text(text(draft), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
    }
    Slider(value = draft, onValueChange = { draft = it }, onValueChangeFinished = { onValue(draft) }, valueRange = range)
}

@Composable
private fun ReaderArchiveCard(title: String, subtitle: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = LanghuanShape.card, color = t.card, border = BorderStroke(1.dp, t.border)) {
        Row(Modifier.padding(start = 13.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(vertical = 5.dp)) { Text(title, color = t.foreground, fontWeight = FontWeight.SemiBold); Text(subtitle, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = t.destructive) }
        }
    }
}

@Composable
private fun ReaderEmptyCard(text: String) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth(), shape = LanghuanShape.card, color = t.muted, border = BorderStroke(1.dp, t.border)) {
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
    val originalEntries = remember(original, query, descending) { flattenReaderToc(original).filter { query.isBlank() || it.title.contains(query, true) }.let { if (descending) it.asReversed() else it } }
    val chapters = remember(state.chapters, query, descending) { state.chapters.sortedBy { it.chapterNumber }.filter { query.isBlank() || it.title.contains(query, true) }.let { if (descending) it.asReversed() else it } }
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
                        Row(Modifier.fillMaxWidth().then(if (item.chapterNumber != null) Modifier.clickable { onChapter(item.chapterNumber) } else Modifier).padding(start = (8 + item.depth * 18).dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val results = remember(state.chapters, query) { if (query.trim().length < 2) emptyList() else state.chapters.filter { it.title.contains(query, true) || it.content.contains(query, true) }.take(120) }
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
private fun ReaderExperienceInfo(book: ReaderBookUi, state: LibraryExperienceState, isLocal: Boolean, onBack: () -> Unit, onRead: () -> Unit, onStory: () -> Unit, onWriting: () -> Unit, onAiSetup: () -> Unit) {
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
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(92.dp).height(132.dp).clip(LanghuanShape.cover))
                Column(Modifier.padding(start = 17.dp).weight(1f)) { Text(book.title, style = MaterialTheme.typography.headlineSmall, color = t.foreground); Text(book.genre.ifBlank { "小说" }, Modifier.padding(top = 6.dp), color = t.mutedForeground); Text("${state.chapters.size} 章 · ${book.currentWords} 字", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground) }
            }
            Surface(Modifier.fillMaxWidth(), shape = LanghuanShape.panel, color = t.card, border = BorderStroke(1.dp, t.border)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) { Text("${(overall * 100).roundToInt()}%", fontSize = MaterialTheme.typography.displaySmall.fontSize, fontWeight = FontWeight.SemiBold, color = t.foreground); Spacer(Modifier.weight(1f)); Text("第 ${progress.chapterNumber} 章 · 本章 ${(progress.positionFraction * 100).roundToInt()}%", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall) }
                    LinearProgressIndicator(progress = { overall }, Modifier.fillMaxWidth().padding(top = 12.dp), color = t.accent, trackColor = t.muted)
                    Text("阅读位置按章节 + 字符锚点保存，换排版或重新进入也会继续原位置。", Modifier.padding(top = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            if (book.premise.isNotBlank() && !book.premise.startsWith("从外部稿件导入")) Surface(Modifier.fillMaxWidth(), shape = LanghuanShape.panel, color = t.card, border = BorderStroke(1.dp, t.border)) {
                Column(Modifier.padding(16.dp)) { Text("简介", color = t.foreground, fontWeight = FontWeight.SemiBold); Text(book.premise, Modifier.padding(top = 9.dp), lineHeight = 23.sp, color = t.mutedForeground) }
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
    Surface(modifier.size(44.dp), shape = LanghuanShape.card, color = t.card.copy(alpha = .94f), border = BorderStroke(1.dp, t.border), shadowElevation = 4.dp) {
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

private fun currentReaderMode(context: Context, bookId: String): String = context.getSharedPreferences("reader_settings_v2", Context.MODE_PRIVATE).getString("page_mode_$bookId", ReaderPageModeV10.PAGE.key) ?: ReaderPageModeV10.PAGE.key

private fun readerPageStartOffsets(raw: String, pages: List<String>): List<Int> {
    if (pages.isEmpty()) return listOf(0)
    var cursor = 0
    return pages.map { page ->
        val sample = page.trim().take(48)
        val found = if (sample.isBlank()) cursor else raw.indexOf(sample, startIndex = cursor.coerceIn(0, raw.length))
        val start = if (found >= 0) found else cursor.coerceIn(0, raw.length)
        cursor = (start + page.length).coerceAtMost(raw.length)
        start
    }
}

private fun pageForRawTextOffset(offsets: List<Int>, offset: Int): Int {
    if (offsets.isEmpty()) return 0
    val safe = offset.coerceAtLeast(0)
    var result = 0
    offsets.forEachIndexed { index, start -> if (start <= safe) result = index }
    return result.coerceIn(0, offsets.lastIndex)
}

private fun formattedPageText(text: String, indent: Boolean, paragraphSpacing: Float): String {
    val separator = when {
        paragraphSpacing < 5f -> "\n"
        paragraphSpacing < 19f -> "\n\n"
        else -> "\n\n\n"
    }
    return text.split(Regex("\\n\\s*\\n")).joinToString(separator) { paragraph ->
        paragraph.trim().let { if (it.isBlank() || !indent) it else "　　$it" }
    }
}
private fun flattenReaderToc(nodes: List<EpubTocNodeV1>, depth: Int = 0): List<ReaderTocEntry> = buildList { nodes.forEach { node -> add(ReaderTocEntry(node.title, node.chapterNumber, depth)); addAll(flattenReaderToc(node.children, depth + 1)) } }
private fun searchReaderSnippet(text: String, query: String): String { val index = text.indexOf(query, ignoreCase = true); if (index < 0) return text.replace('\n', ' ').take(100); val start = (index - 40).coerceAtLeast(0); val end = (index + query.length + 65).coerceAtMost(text.length); return text.substring(start, end).replace('\n', ' ').trim() }
