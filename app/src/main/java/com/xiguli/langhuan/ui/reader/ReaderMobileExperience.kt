package com.xiguli.langhuan.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class MobileReaderPalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
)

/**
 * Reading-first mobile reader.
 *
 * The reading surface is the product. Chrome is hidden by default, only reading controls are
 * allowed in the first layer, and authoring/AI actions live behind the book-more sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMobileExperience(
    viewModel: LibraryExperienceViewModel,
    studioState: StudioUiState,
    onBackToShelf: () -> Unit,
    onEnterWriting: (String) -> Unit,
    onOpenEditor: (String, Int) -> Unit,
    onOpenAiSetup: () -> Unit,
    startOnInfo: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalLanghuanUiTokens.current
    val book = state.openedBook ?: return
    val chapter = state.readingChapter

    if (chapter == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.primary)
                    Text(
                        "正在准备正文",
                        Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }
        }
        return
    }

    var showInfo by rememberSaveable(book.id) { mutableStateOf(startOnInfo) }
    var storyMode by rememberSaveable(book.id) { mutableStateOf(false) }

    if (storyMode) {
        Box(Modifier.fillMaxSize()) {
            StoryCleanExperience(
                book = book,
                libraryState = state,
                aiReady = studioState.provider.ready,
                onAiSetup = onOpenAiSetup,
                onAdopted = { viewModel.openBook(book.id) },
            )
            Surface(
                onClick = { storyMode = false },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(42.dp),
                shape = CircleShape,
                color = t.card.copy(alpha = .94f),
                shadowElevation = 5.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "返回阅读", Modifier.size(20.dp), tint = t.foreground)
                }
            }
        }
        return
    }

    MobileReaderPage(
        book = book,
        state = state,
        chapter = chapter,
        onBack = onBackToShelf,
        onInfo = { showInfo = true },
        onOpenChapter = viewModel::openReader,
    )

    if (showInfo) {
        MobileBookInfoSheet(
            book = book,
            chapterCount = state.chapters.size,
            currentChapter = chapter.chapterNumber,
            onDismiss = { showInfo = false },
            onContinue = { showInfo = false },
            onStory = {
                showInfo = false
                storyMode = true
            },
            onEdit = {
                showInfo = false
                onOpenEditor(book.id, chapter.chapterNumber)
            },
            onWriting = {
                showInfo = false
                onEnterWriting(book.id)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileReaderPage(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    chapter: ChapterDraft,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onOpenChapter: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val prefs = remember(book.id) {
        context.getSharedPreferences("reader_mobile_settings_v2", Context.MODE_PRIVATE)
    }
    val ordered = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val chapterIndex = ordered.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = ordered.getOrNull(chapterIndex - 1)
    val next = ordered.getOrNull(chapterIndex + 1)

    var chromeVisible by remember(chapter.id) { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 18.5f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.78f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 24f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph_${book.id}", 6f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent_${book.id}", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "serif") ?: "serif") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "paper") ?: "paper") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(
            prefs.getString("page_mode_${book.id}", ReaderPageModeV10.PAGE.key)
                ?: ReaderPageModeV10.PAGE.key,
        )
    }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val palette = mobileReaderPalette(themeKey)
    val family = mobileReaderFont(fontKey)
    val displayTitle = remember(chapter.id, chapter.title, chapter.chapterNumber) {
        readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
    }
    val readingText = remember(chapter.id, chapter.title, chapter.content) {
        readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
            .ifBlank { "这一章没有正文。" }
    }
    val measured = rememberReaderMeasuredPaginationV16(
        text = readingText,
        displayTitle = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val pages = measured.pages.ifEmpty { listOf(readingText) }
    val offsets = measured.offsets.ifEmpty { listOf(0) }
    val saved = remember(chapter.id) {
        ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber)
    }
    val initialPage = remember(chapter.id, measured.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset >
                0 -> pageForRawTextOffset(offsets, saved.textOffset.coerceIn(0, readingText.length))
            saved.modeKey == pageMode.key -> saved.pageIndex.coerceIn(0, pages.lastIndex)
            else -> 0
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size.coerceAtLeast(1) },
    )
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) {
        mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length))
    }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${sidePadding.roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measured.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf(layoutKey) }

    fun currentPage(): Int = pagerState.settledPage.coerceIn(0, pages.lastIndex)

    fun currentTextOffset(): Int = when (pageMode) {
        ReaderPageModeV10.SCROLL -> {
            val fraction = if (scrollState.maxValue <= 0) {
                0f
            } else {
                scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            }
            (readingText.length * fraction).roundToInt().coerceIn(0, readingText.length)
        }
        else -> offsets.getOrElse(currentPage()) { 0 }.coerceIn(0, readingText.length)
    }

    fun currentFraction(): Float = if (readingText.isBlank()) {
        0f
    } else {
        currentTextOffset().toFloat() / readingText.length.toFloat()
    }

    fun persist() {
        if (crossingChapter) return
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else currentPage(),
                scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
                positionFraction = currentFraction().coerceIn(0f, 1f),
                textOffset = currentTextOffset(),
                modeKey = pageMode.key,
            ),
        )
    }

    fun rememberAnchor() {
        anchorOffset = currentTextOffset().coerceIn(0, readingText.length)
        persist()
    }

    fun jumpChapter(target: ChapterDraft?, atEnd: Boolean = false) {
        target ?: return
        if (crossingChapter) return
        persist()
        crossingChapter = true
        ReaderProgressStoreV11.moveTo(
            context = context,
            bookId = book.id,
            chapterNumber = target.chapterNumber,
            pageIndex = 0,
            scrollY = 0,
            modeKey = pageMode.key,
            positionFraction = if (atEnd) 1f else 0f,
            textOffset = if (atEnd) Int.MAX_VALUE else 0,
        )
        onOpenChapter(target.chapterNumber)
    }

    fun tapPrevious() {
        val page = currentPage()
        if (page > 0) {
            scope.launch { pagerState.animateScrollToPage(page - 1) }
        } else {
            jumpChapter(previous, atEnd = true)
        }
    }

    fun tapNext() {
        val page = currentPage()
        if (page < pages.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(page + 1) }
        } else {
            jumpChapter(next, atEnd = false)
        }
    }

    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue > 0) {
                val fraction = if (readingText.isBlank()) {
                    0f
                } else {
                    targetOffset.toFloat() / readingText.length.toFloat()
                }
                scrollState.scrollTo(
                    (scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue),
                )
            }
        } else {
            pagerState.scrollToPage(
                pageForRawTextOffset(offsets, targetOffset).coerceIn(0, pages.lastIndex),
            )
        }
        appliedLayoutKey = layoutKey
    }

    LaunchedEffect(chapter.id, pageMode, scrollState.maxValue) {
        if (pageMode != ReaderPageModeV10.SCROLL || scrollState.maxValue <= 0) return@LaunchedEffect
        val offset = when {
            saved.textOffset > 0 -> saved.textOffset.coerceIn(0, readingText.length)
            saved.positionFraction > 0f ->
                (readingText.length * saved.positionFraction).roundToInt().coerceIn(0, readingText.length)
            else -> 0
        }
        val fraction = if (readingText.isBlank()) 0f else offset.toFloat() / readingText.length.toFloat()
        scrollState.scrollTo(
            (scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue),
        )
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode == ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { persist() }
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode != ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { scrollState.value }.distinctUntilChanged().collectLatest {
            delay(180)
            persist()
        }
    }

    LaunchedEffect(
        fontSize,
        lineFactor,
        sidePadding,
        paragraphSpacing,
        firstLineIndent,
        fontKey,
        themeKey,
        pageModeKey,
    ) {
        prefs.edit()
            .putFloat("font_${book.id}", fontSize)
            .putFloat("line_${book.id}", lineFactor)
            .putFloat("padding_${book.id}", sidePadding)
            .putFloat("paragraph_${book.id}", paragraphSpacing)
            .putBoolean("indent_${book.id}", firstLineIndent)
            .putString("family_${book.id}", fontKey)
            .putString("theme_${book.id}", themeKey)
            .putString("page_mode_${book.id}", pageModeKey)
            .apply()
    }

    DisposableEffect(chapter.id, layoutKey) { onDispose { persist() } }

    val edgeThresholdPx = with(density) { 52.dp.toPx() }
    val edgeSwipe = remember(chapter.id, pages.size, pageMode, previous?.id, next?.id) {
        object : NestedScrollConnection {
            var edgeDrag = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (pageMode == ReaderPageModeV10.SCROLL || source != NestedScrollSource.UserInput) {
                    return Offset.Zero
                }
                val page = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                edgeDrag = when {
                    page == 0 && available.x > 0f ->
                        (edgeDrag + available.x).coerceAtMost(edgeThresholdPx * 2f)
                    page == pages.lastIndex && available.x < 0f ->
                        (edgeDrag + available.x).coerceAtLeast(-edgeThresholdPx * 2f)
                    else -> 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val drag = edgeDrag
                edgeDrag = 0f
                if (abs(drag) >= edgeThresholdPx && !crossingChapter) {
                    when {
                        drag > 0f && pagerState.currentPage == 0 -> jumpChapter(previous, atEnd = true)
                        drag < 0f && pagerState.currentPage == pages.lastIndex -> jumpChapter(next, atEnd = false)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(
            Modifier.fillMaxSize().pointerInput(chapter.id, pageModeKey, pagerState.settledPage) {
                detectTapGestures { offset ->
                    if (pageMode == ReaderPageModeV10.SCROLL) {
                        chromeVisible = !chromeVisible
                    } else {
                        when {
                            offset.x < size.width * .28f -> tapPrevious()
                            offset.x > size.width * .72f -> tapNext()
                            else -> chromeVisible = !chromeVisible
                        }
                    }
                }
            },
        ) {
            when (pageMode) {
                ReaderPageModeV10.SCROLL -> MobileScrollReadingPage(
                    title = displayTitle,
                    text = readingText,
                    next = next,
                    fontSize = fontSize,
                    lineFactor = lineFactor,
                    sidePadding = sidePadding,
                    paragraphSpacing = paragraphSpacing,
                    indentEnabled = firstLineIndent,
                    family = family,
                    palette = palette,
                    scrollState = scrollState,
                )

                ReaderPageModeV10.PAGE,
                ReaderPageModeV10.COVER,
                -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().nestedScroll(edgeSwipe),
                    pageSpacing = 0.dp,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val safePage = page.coerceIn(0, pages.lastIndex)
                    MobileReaderPageContent(
                        pageText = pages[safePage],
                        title = displayTitle,
                        firstPage = safePage == 0,
                        page = safePage + 1,
                        pageCount = pages.size,
                        fontSize = fontSize,
                        lineFactor = lineFactor,
                        sidePadding = sidePadding,
                        paragraphSpacing = paragraphSpacing,
                        indentEnabled = firstLineIndent,
                        indentFirstParagraph = firstLineIndent &&
                            measured.indentFirstParagraph.getOrElse(safePage) { true },
                        family = family,
                        palette = palette,
                    )
                }
            }

            if (!chromeVisible) {
                Text(
                    "${chapterIndex + 1}/${ordered.size.coerceAtLeast(1)} · ${(currentFraction().coerceIn(0f, 1f) * 100).roundToInt()}%",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondary.copy(alpha = .58f),
                )
            }
        }

        if (chromeVisible) {
            MobileReaderChrome(
                modifier = Modifier.fillMaxSize(),
                chapterTitle = displayTitle,
                palette = palette,
                onBack = {
                    persist()
                    onBack()
                },
                onMore = onInfo,
                onDirectory = { showDirectory = true },
                onFontDecrease = {
                    rememberAnchor()
                    fontSize = (fontSize - 1f).coerceAtLeast(15f)
                },
                onFontIncrease = {
                    rememberAnchor()
                    fontSize = (fontSize + 1f).coerceAtMost(30f)
                },
                onTheme = { themeKey = nextReaderTheme(themeKey) },
                onSettings = { showSettings = true },
            )
        }
    }

    if (showDirectory) {
        MobileReaderDirectory(
            chapters = ordered,
            current = chapter.chapterNumber,
            onDismiss = { showDirectory = false },
            onSelect = { number ->
                showDirectory = false
                val target = ordered.firstOrNull { it.chapterNumber == number }
                jumpChapter(target, atEnd = false)
            },
        )
    }

    if (showSettings) {
        MobileReaderSettings(
            pageModeKey = pageModeKey,
            themeKey = themeKey,
            fontKey = fontKey,
            fontSize = fontSize,
            lineFactor = lineFactor,
            sidePadding = sidePadding,
            paragraphSpacing = paragraphSpacing,
            firstLineIndent = firstLineIndent,
            onBeforeLayoutChange = ::rememberAnchor,
            onPageMode = { pageModeKey = it },
            onTheme = { themeKey = it },
            onFont = { fontKey = it },
            onFontSize = { fontSize = it },
            onLine = { lineFactor = it },
            onPadding = { sidePadding = it },
            onParagraph = { paragraphSpacing = it },
            onIndent = { firstLineIndent = it },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun MobileReaderPageContent(
    pageText: String,
    title: String,
    firstPage: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    indentFirstParagraph: Boolean,
    family: FontFamily,
    palette: MobileReaderPalette,
) {
    Column(
        Modifier.fillMaxSize().background(palette.background)
            .padding(horizontal = sidePadding.dp, vertical = 14.dp),
    ) {
        if (firstPage) {
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                style = TextStyle(
                    fontSize = (fontSize + 2f).sp,
                    lineHeight = (fontSize + 8f).sp,
                    fontFamily = family,
                    fontWeight = FontWeight.Medium,
                    color = palette.foreground,
                ),
            )
            Spacer(Modifier.height(20.dp))
        } else {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .58f),
            )
            Spacer(Modifier.height(12.dp))
        }
        MobileReaderParagraphs(
            text = pageText,
            fontSize = fontSize,
            lineFactor = lineFactor,
            paragraphSpacing = paragraphSpacing,
            indentEnabled = indentEnabled,
            indentFirstParagraph = indentFirstParagraph,
            family = family,
            color = palette.foreground,
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$page / $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .54f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "琅嬛",
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .38f),
            )
        }
    }
}

@Composable
private fun MobileScrollReadingPage(
    title: String,
    text: String,
    next: ChapterDraft?,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    family: FontFamily,
    palette: MobileReaderPalette,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState)
            .padding(horizontal = sidePadding.dp, vertical = 22.dp),
    ) {
        Text(
            title,
            style = TextStyle(
                fontSize = (fontSize + 2f).sp,
                lineHeight = (fontSize + 8f).sp,
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                color = palette.foreground,
            ),
        )
        Spacer(Modifier.height(22.dp))
        MobileReaderParagraphs(
            text = text,
            fontSize = fontSize,
            lineFactor = lineFactor,
            paragraphSpacing = paragraphSpacing,
            indentEnabled = indentEnabled,
            indentFirstParagraph = indentEnabled,
            family = family,
            color = palette.foreground,
        )
        Spacer(Modifier.height(56.dp))
        Text(
            if (next == null) {
                "— 全书完 —"
            } else {
                "下一章 · ${readerDisplayChapterTitleV13(next.title, next.chapterNumber)}"
            },
            Modifier.fillMaxWidth().padding(bottom = 28.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondary.copy(alpha = .72f),
        )
    }
}

@Composable
private fun MobileReaderParagraphs(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    indentFirstParagraph: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) {
        text.replace("\r\n", "\n").split(Regex("\\n+")).filter { it.isNotBlank() }
    }
    paragraphs.forEachIndexed { index, paragraph ->
        val shouldIndent = indentEnabled && (index > 0 || indentFirstParagraph)
        Text(
            paragraph.trim(),
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineFactor).sp,
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                color = color,
                textIndent = TextIndent(firstLine = if (shouldIndent) (fontSize * 2f).sp else 0.sp),
            ),
        )
        if (index < paragraphs.lastIndex) {
            Spacer(Modifier.height(paragraphSpacing.dp))
        }
    }
}

@Composable
private fun MobileReaderChrome(
    modifier: Modifier,
    chapterTitle: String,
    palette: MobileReaderPalette,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onDirectory: () -> Unit,
    onFontDecrease: () -> Unit,
    onFontIncrease: () -> Unit,
    onTheme: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(modifier) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = palette.chrome.copy(alpha = .92f),
            contentColor = palette.foreground,
            shadowElevation = 0.dp,
        ) {
            Row(
                Modifier.height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderChromeIcon(Icons.Rounded.ArrowBack, "返回", palette.foreground, onBack)
                Text(
                    chapterTitle,
                    Modifier.padding(horizontal = 8.dp).weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.foreground.copy(alpha = .84f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                ReaderChromeIcon(Icons.Rounded.MoreHoriz, "更多", palette.foreground, onMore)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = palette.chrome.copy(alpha = .96f),
            contentColor = palette.foreground,
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MobileReaderAction(
                    Modifier.weight(1f),
                    Icons.Rounded.FormatListBulleted,
                    "目录",
                    palette.foreground,
                    onDirectory,
                )
                MobileReaderAction(
                    Modifier.weight(1f),
                    Icons.Rounded.Remove,
                    "A−",
                    palette.foreground,
                    onFontDecrease,
                )
                MobileReaderAction(
                    Modifier.weight(1f),
                    Icons.Rounded.AutoStories,
                    "背景",
                    palette.foreground,
                    onTheme,
                )
                MobileReaderAction(
                    Modifier.weight(1f),
                    Icons.Rounded.Add,
                    "A+",
                    palette.foreground,
                    onFontIncrease,
                )
                MobileReaderAction(
                    Modifier.weight(1f),
                    Icons.Rounded.Tune,
                    "排版",
                    palette.foreground,
                    onSettings,
                )
            }
        }
    }
}

@Composable
private fun ReaderChromeIcon(
    icon: ImageVector,
    description: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(20.dp), tint = color.copy(alpha = .86f))
        }
    }
}

@Composable
private fun MobileReaderAction(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = color.copy(alpha = .90f))
        Text(
            label,
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = .72f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileReaderDirectory(
    chapters: List<ChapterDraft>,
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(chapters, query) {
        chapters.filter {
            query.isBlank() || readerDisplayChapterTitleV13(it.title, it.chapterNumber)
                .contains(query, true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 690.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "目录",
                        style = MaterialTheme.typography.headlineSmall,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${chapters.size} 章 · 当前第 ${current.coerceAtLeast(1)} 章",
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                Surface(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = if (searchOpen) t.accent else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            "搜索章节",
                            Modifier.size(19.dp),
                            tint = if (searchOpen) t.accentForeground else t.foreground,
                        )
                    }
                }
            }

            if (searchOpen) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = t.card,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = t.mutedForeground)
                        androidx.compose.material3.OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.padding(start = 6.dp).weight(1f),
                            placeholder = { Text("搜索章节") },
                            singleLine = true,
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(filtered, key = { it.id }) { item ->
                    val selected = item.chapterNumber == current
                    Surface(
                        onClick = { onSelect(item.chapterNumber) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) t.accent.copy(alpha = .72f) else Color.Transparent,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) {
                                Surface(
                                    modifier = Modifier.size(width = 3.dp, height = 26.dp),
                                    shape = CircleShape,
                                    color = t.primary,
                                ) {}
                            } else {
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                                Modifier.padding(start = 12.dp).weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.foreground,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selected) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = t.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileReaderSettings(
    pageModeKey: String,
    themeKey: String,
    fontKey: String,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    onBeforeLayoutChange: () -> Unit,
    onPageMode: (String) -> Unit,
    onTheme: (String) -> Unit,
    onFont: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onPadding: (Float) -> Unit,
    onParagraph: (Float) -> Unit,
    onIndent: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    val themes = listOf(
        Triple("paper", "纸张", Color(0xFFFAF7F2)),
        Triple("warm", "暖黄", Color(0xFFF5E8CE)),
        Triple("green", "护眼", Color(0xFFE8F0E5)),
        Triple("night", "夜间", Color(0xFF191816)),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "阅读设置",
                        style = MaterialTheme.typography.headlineSmall,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "常用设置在前，高级排版按需展开",
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                ReaderChromeIcon(Icons.Rounded.Close, "关闭", t.foreground, onDismiss)
            }

            MobileSettingLabel("阅读背景")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                themes.forEach { (key, label, color) ->
                    MobileThemeChoice(
                        label = label,
                        color = color,
                        selected = themeKey == key,
                    ) { onTheme(key) }
                }
            }

            MobileSettingLabel("字号")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = t.card,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MobileRoundControl(Icons.Rounded.Remove, "减小字号") {
                        onBeforeLayoutChange()
                        onFontSize((fontSize - 1f).coerceAtLeast(15f))
                    }
                    Text(
                        "${fontSize.roundToInt()}",
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.Medium,
                    )
                    MobileRoundControl(Icons.Rounded.Add, "增大字号") {
                        onBeforeLayoutChange()
                        onFontSize((fontSize + 1f).coerceAtMost(30f))
                    }
                }
            }

            MobileSettingLabel("翻页方式")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ReaderPageModeV10.PAGE.key to "左右",
                    ReaderPageModeV10.COVER.key to "覆盖",
                    ReaderPageModeV10.SCROLL.key to "滚动",
                ).forEach { (key, label) ->
                    MobileChoiceChip(
                        text = label,
                        selected = pageModeKey == key,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (pageModeKey != key) onBeforeLayoutChange()
                        onPageMode(key)
                    }
                }
            }

            Surface(
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                shape = RoundedCornerShape(20.dp),
                color = t.card,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "高级排版",
                            style = MaterialTheme.typography.bodyLarge,
                            color = t.foreground,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "字体、行距、页边距、段间距与缩进",
                            Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                    Icon(
                        if (advancedOpen) Icons.Rounded.KeyboardArrowLeft else Icons.Rounded.KeyboardArrowRight,
                        null,
                        Modifier.size(20.dp),
                        tint = t.mutedForeground,
                    )
                }
            }

            if (advancedOpen) {
                MobileSettingLabel("字体")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MobileChoiceChip("衬线", fontKey == "serif", Modifier.weight(1f)) {
                        if (fontKey != "serif") onBeforeLayoutChange()
                        onFont("serif")
                    }
                    MobileChoiceChip("无衬线", fontKey == "sans", Modifier.weight(1f)) {
                        if (fontKey != "sans") onBeforeLayoutChange()
                        onFont("sans")
                    }
                }

                MobileSettingLabel("排版密度")
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = t.card,
                ) {
                    Column {
                        MobilePresetRow(
                            label = "行距",
                            options = listOf("紧" to 1.55f, "标准" to 1.78f, "松" to 1.94f),
                            current = lineFactor,
                        ) {
                            onBeforeLayoutChange()
                            onLine(it)
                        }
                        HorizontalDivider(
                            color = t.border.copy(alpha = .55f),
                            modifier = Modifier.padding(start = 14.dp),
                        )
                        MobilePresetRow(
                            label = "页边距",
                            options = listOf("窄" to 18f, "标准" to 24f, "宽" to 32f),
                            current = sidePadding,
                        ) {
                            onBeforeLayoutChange()
                            onPadding(it)
                        }
                        HorizontalDivider(
                            color = t.border.copy(alpha = .55f),
                            modifier = Modifier.padding(start = 14.dp),
                        )
                        MobilePresetRow(
                            label = "段间距",
                            options = listOf("紧" to 2f, "标准" to 6f, "松" to 12f),
                            current = paragraphSpacing,
                        ) {
                            onBeforeLayoutChange()
                            onParagraph(it)
                        }
                        HorizontalDivider(
                            color = t.border.copy(alpha = .55f),
                            modifier = Modifier.padding(start = 14.dp),
                        )
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onBeforeLayoutChange()
                                onIndent(!firstLineIndent)
                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "首行缩进",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = t.foreground,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "每个自然段缩进两个汉字",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                            Switch(
                                checked = firstLineIndent,
                                onCheckedChange = {
                                    onBeforeLayoutChange()
                                    onIndent(it)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(22.dp))
        }
    }
}

@Composable
private fun MobileSettingLabel(text: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text,
        Modifier.padding(top = 18.dp, start = 2.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = t.mutedForeground,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun MobileThemeChoice(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        Modifier.width(68.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = color,
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) t.primary else t.border.copy(alpha = .72f),
            ),
        ) {
            if (selected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        Modifier.size(18.dp),
                        tint = if (label == "夜间") Color(0xFFF3EEE7) else Color(0xFF3B3833),
                    )
                }
            }
        }
        Text(
            label,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.foreground else t.mutedForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MobileChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) t.accent else t.card,
        contentColor = if (selected) t.accentForeground else t.foreground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MobileRoundControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = t.accent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(19.dp), tint = t.accentForeground)
        }
    }
}

@Composable
private fun MobilePresetRow(
    label: String,
    options: List<Pair<String, Float>>,
    current: Float,
    onValue: (Float) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.width(66.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
            fontWeight = FontWeight.Medium,
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (name, value) ->
                val selected = abs(current - value) < .08f
                Surface(
                    onClick = { onValue(value) },
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) t.accent else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) t.accentForeground else t.mutedForeground,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileBookInfoSheet(
    book: ReaderBookUi,
    chapterCount: Int,
    currentChapter: Int,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.background,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.width(88.dp).aspectRatio(.70f),
                    shape = RoundedCornerShape(16.dp),
                    color = t.muted,
                    shadowElevation = 2.dp,
                ) {
                    if (cover != null) {
                        Image(
                            cover.asImageBitmap(),
                            book.title,
                            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().padding(10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = t.foreground,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Column(Modifier.padding(start = 15.dp).weight(1f)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        book.genre.ifBlank { "小说" },
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MobileBookMetric("章节", chapterCount.toString())
                        MobileBookMetric("字数", book.currentWords.coerceAtLeast(0).toString())
                        MobileBookMetric("当前", "第${currentChapter.coerceAtLeast(1)}章")
                    }
                }
            }

            Surface(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                shape = RoundedCornerShape(18.dp),
                color = t.primary,
                contentColor = t.primaryForeground,
            ) {
                Box(Modifier.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text("继续阅读", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                "更多操作",
                Modifier.padding(top = 20.dp, start = 2.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = t.card,
            ) {
                Column {
                    ReaderMoreActionRow(
                        Icons.Rounded.Edit,
                        "编辑本章",
                        "修改当前章节正文",
                        onEdit,
                    )
                    HorizontalDivider(color = t.border.copy(alpha = .55f), modifier = Modifier.padding(start = 58.dp))
                    ReaderMoreActionRow(
                        Icons.Rounded.TheaterComedy,
                        "进入故事",
                        "以当前作品世界进入互动模式",
                        onStory,
                    )
                    HorizontalDivider(color = t.border.copy(alpha = .55f), modifier = Modifier.padding(start = 58.dp))
                    ReaderMoreActionRow(
                        Icons.Rounded.AutoAwesome,
                        "AI 创作",
                        "继续写作、规划或与 AI 对话",
                        onWriting,
                    )
                }
            }

            Text(
                "简介",
                Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.titleSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                book.premise.ifBlank { "暂无简介" },
                Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.foreground.copy(alpha = .78f),
                lineHeight = 22.sp,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}

@Composable
private fun ReaderMoreActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(12.dp),
            color = t.accent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = t.accentForeground)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
            )
            Text(
                subtitle,
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground)
    }
}

@Composable
private fun MobileBookMetric(label: String, value: String) {
    val t = LocalLanghuanUiTokens.current
    Column {
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            Modifier.padding(top = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
        )
    }
}

private fun mobileReaderFont(key: String): FontFamily = when (key) {
    "sans" -> FontFamily.SansSerif
    else -> FontFamily.Serif
}

private fun nextReaderTheme(key: String): String = when (key) {
    "paper" -> "warm"
    "warm" -> "green"
    "green" -> "night"
    else -> "paper"
}

private fun mobileReaderPalette(key: String): MobileReaderPalette = when (key) {
    "night" -> MobileReaderPalette(
        Color(0xFF171613),
        Color(0xFFE8E2D8),
        Color(0xFF9D978E),
        Color(0xFF201F1B),
    )
    "green" -> MobileReaderPalette(
        Color(0xFFE8F0E5),
        Color(0xFF253126),
        Color(0xFF667267),
        Color(0xFFF0F5EE),
    )
    "warm" -> MobileReaderPalette(
        Color(0xFFF5E8CE),
        Color(0xFF352D23),
        Color(0xFF776A59),
        Color(0xFFF8EDD8),
    )
    else -> MobileReaderPalette(
        Color(0xFFFAF7F2),
        Color(0xFF1C1A17),
        Color(0xFF8A817A),
        Color(0xFFFEFCF8),
    )
}
