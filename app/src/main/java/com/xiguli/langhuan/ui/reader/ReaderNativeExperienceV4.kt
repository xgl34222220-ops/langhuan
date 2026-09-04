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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.ChapterDraft
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class ReaderPaletteV4(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
    val accent: Color,
)

/**
 * Reader v4 built from the rejected 2026-09-04 device recording.
 *
 * The reading page is the product. Directory is a book drawer, not a settings sheet. Appearance is
 * a compact reading control, not a developer form. Authoring actions stay behind More.
 */
@Composable
fun ReaderNativeExperienceV4(
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
    val chapter = state.readingChapter

    if (chapter == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
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
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp).size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "返回阅读", Modifier.size(19.dp))
                }
            }
        }
        return
    }

    ReaderPageV4(
        book = book,
        state = state,
        chapter = chapter,
        onBack = onBackToShelf,
        onInfo = { showInfo = true },
        onOpenChapter = viewModel::openReader,
    )

    if (showInfo) {
        ReaderMorePanelV4(
            book = book,
            chapterCount = state.chapters.size,
            currentChapter = chapter.chapterNumber,
            onDismiss = { showInfo = false },
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

@Composable
private fun ReaderPageV4(
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
    val prefs = remember(book.id) { context.getSharedPreferences("reader_native_settings_v4", Context.MODE_PRIVATE) }
    val ordered = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val chapterIndex = ordered.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = ordered.getOrNull(chapterIndex - 1)
    val next = ordered.getOrNull(chapterIndex + 1)

    var chromeVisible by remember(chapter.id) { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 17.5f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.70f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 22f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph_${book.id}", 4f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent_${book.id}", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "serif") ?: "serif") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "paper") ?: "paper") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.PAGE.key) ?: ReaderPageModeV10.PAGE.key)
    }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val palette = readerPaletteV4(themeKey)
    val family = if (fontKey == "sans") FontFamily.SansSerif else FontFamily.Serif
    val displayTitle = remember(chapter.id, chapter.title, chapter.chapterNumber) {
        readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
    }
    val readingText = remember(chapter.id, chapter.title, chapter.content) {
        readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content)).ifBlank { "这一章没有正文。" }
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
    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val initialPage = remember(chapter.id, measured.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> pageForOffsetV4(offsets, saved.textOffset.coerceIn(0, readingText.length))
            saved.modeKey == pageMode.key -> saved.pageIndex.coerceIn(0, pages.lastIndex)
            else -> 0
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size.coerceAtLeast(1) })
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${sidePadding.roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measured.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf(layoutKey) }

    fun currentPage(): Int = pagerState.settledPage.coerceIn(0, pages.lastIndex)

    fun currentTextOffset(): Int = when (pageMode) {
        ReaderPageModeV10.SCROLL -> {
            val fraction = if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            (readingText.length * fraction).roundToInt().coerceIn(0, readingText.length)
        }
        else -> offsets.getOrElse(currentPage()) { 0 }.coerceIn(0, readingText.length)
    }

    fun persist() {
        if (crossingChapter) return
        val offset = currentTextOffset()
        val fraction = if (readingText.isBlank()) 0f else offset.toFloat() / readingText.length.toFloat()
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else currentPage(),
                scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
                positionFraction = fraction.coerceIn(0f, 1f),
                textOffset = offset,
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

    fun previousPage() {
        val page = currentPage()
        if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) }
        else jumpChapter(previous, atEnd = true)
    }

    fun nextPage() {
        val page = currentPage()
        if (page < pages.lastIndex) scope.launch { pagerState.animateScrollToPage(page + 1) }
        else jumpChapter(next, atEnd = false)
    }

    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue > 0) {
                val fraction = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length.toFloat()
                scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue))
            }
        } else {
            pagerState.scrollToPage(pageForOffsetV4(offsets, targetOffset).coerceIn(0, pages.lastIndex))
        }
        appliedLayoutKey = layoutKey
    }

    LaunchedEffect(chapter.id, pageMode, scrollState.maxValue) {
        if (pageMode != ReaderPageModeV10.SCROLL || scrollState.maxValue <= 0) return@LaunchedEffect
        val offset = when {
            saved.textOffset > 0 -> saved.textOffset.coerceIn(0, readingText.length)
            saved.positionFraction > 0f -> (readingText.length * saved.positionFraction).roundToInt().coerceIn(0, readingText.length)
            else -> 0
        }
        val fraction = if (readingText.isBlank()) 0f else offset.toFloat() / readingText.length.toFloat()
        scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue))
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

    LaunchedEffect(fontSize, lineFactor, sidePadding, paragraphSpacing, firstLineIndent, fontKey, themeKey, pageModeKey) {
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

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (pageMode == ReaderPageModeV10.SCROLL || source != NestedScrollSource.UserInput) return Offset.Zero
                val page = pagerState.currentPage.coerceIn(0, pages.lastIndex)
                edgeDrag = when {
                    page == 0 && available.x > 0f -> (edgeDrag + available.x).coerceAtMost(edgeThresholdPx * 2f)
                    page == pages.lastIndex && available.x < 0f -> (edgeDrag + available.x).coerceAtLeast(-edgeThresholdPx * 2f)
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

    Box(Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(
            Modifier.fillMaxSize().pointerInput(chapter.id, pageModeKey, pagerState.settledPage) {
                detectTapGestures { offset ->
                    if (pageMode == ReaderPageModeV10.SCROLL) {
                        chromeVisible = !chromeVisible
                    } else {
                        when {
                            offset.x < size.width * .27f -> previousPage()
                            offset.x > size.width * .73f -> nextPage()
                            else -> chromeVisible = !chromeVisible
                        }
                    }
                }
            },
        ) {
            when (pageMode) {
                ReaderPageModeV10.SCROLL -> ScrollPageV4(
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
                    ReaderContentPageV4(
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
                        indentFirstParagraph = firstLineIndent && measured.indentFirstParagraph.getOrElse(safePage) { true },
                        family = family,
                        palette = palette,
                    )
                }
            }
        }

        if (chromeVisible) {
            ReaderChromeV4(
                chapterTitle = displayTitle,
                palette = palette,
                onBack = {
                    persist()
                    onBack()
                },
                onMore = {
                    chromeVisible = false
                    onInfo()
                },
                onDirectory = {
                    chromeVisible = false
                    showDirectory = true
                },
                onAppearance = {
                    chromeVisible = false
                    showAppearance = true
                },
                onTheme = { themeKey = nextThemeV4(themeKey) },
            )
        }
    }

    if (showDirectory) {
        DirectoryDrawerV4(
            chapters = ordered,
            current = chapter.chapterNumber,
            palette = palette,
            onDismiss = { showDirectory = false },
            onSelect = { number ->
                showDirectory = false
                jumpChapter(ordered.firstOrNull { it.chapterNumber == number })
            },
        )
    }

    if (showAppearance) {
        AppearancePanelV4(
            palette = palette,
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
            onDismiss = { showAppearance = false },
        )
    }
}

@Composable
private fun ReaderContentPageV4(
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
    palette: ReaderPaletteV4,
) {
    Column(
        Modifier.fillMaxSize().background(palette.background)
            .padding(horizontal = sidePadding.dp)
            .padding(top = 22.dp, bottom = 10.dp),
    ) {
        if (firstPage) {
            Text(
                title,
                style = TextStyle(
                    fontSize = (fontSize + 1.5f).sp,
                    lineHeight = (fontSize + 8f).sp,
                    fontFamily = family,
                    fontWeight = FontWeight.Medium,
                    color = palette.foreground,
                ),
            )
            Spacer(Modifier.height(18.dp))
        } else {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = palette.secondary.copy(alpha = .38f),
            )
            Spacer(Modifier.height(14.dp))
        }

        ReaderParagraphsV4(
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
        Text(
            "$page / $pageCount",
            Modifier.fillMaxWidth().padding(top = 7.dp),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = palette.secondary.copy(alpha = .38f),
        )
    }
}

@Composable
private fun ScrollPageV4(
    title: String,
    text: String,
    next: ChapterDraft?,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    family: FontFamily,
    palette: ReaderPaletteV4,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState)
            .padding(horizontal = sidePadding.dp)
            .padding(top = 28.dp, bottom = 28.dp),
    ) {
        Text(
            title,
            fontSize = (fontSize + 1.5f).sp,
            lineHeight = (fontSize + 8f).sp,
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            color = palette.foreground,
        )
        Spacer(Modifier.height(20.dp))
        ReaderParagraphsV4(text, fontSize, lineFactor, paragraphSpacing, indentEnabled, indentEnabled, family, palette.foreground)
        Spacer(Modifier.height(72.dp))
        Text(
            if (next == null) "— 全书完 —" else "下一章 · ${readerDisplayChapterTitleV13(next.title, next.chapterNumber)}",
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = palette.secondary.copy(alpha = .58f),
        )
    }
}

@Composable
private fun ReaderParagraphsV4(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indentEnabled: Boolean,
    indentFirstParagraph: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) { text.replace("\r\n", "\n").split(Regex("\\n+")).filter { it.isNotBlank() } }
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
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}

@Composable
private fun ReaderChromeV4(
    chapterTitle: String,
    palette: ReaderPaletteV4,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onDirectory: () -> Unit,
    onAppearance: () -> Unit,
    onTheme: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(palette.chrome.copy(alpha = .94f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderIconV4(Icons.Rounded.ArrowBack, "返回", palette, onBack)
            Text(
                chapterTitle,
                Modifier.padding(horizontal = 10.dp).weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = palette.foreground.copy(alpha = .60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            ReaderIconV4(Icons.Rounded.MoreHoriz, "更多", palette, onMore)
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = palette.chrome.copy(alpha = .97f),
            contentColor = palette.foreground,
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderBottomItemV4(Modifier.weight(1f), Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
                ReaderBottomItemV4(Modifier.weight(1f), Icons.Rounded.TextFields, "字体", palette.foreground, onAppearance)
                ReaderBottomItemV4(Modifier.weight(1f), Icons.Rounded.Palette, "主题", palette.foreground, onTheme)
                ReaderBottomItemV4(Modifier.weight(1f), Icons.Rounded.Tune, "排版", palette.foreground, onAppearance)
            }
        }
    }
}

@Composable
private fun ReaderIconV4(icon: ImageVector, description: String, palette: ReaderPaletteV4, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(19.dp), tint = palette.foreground.copy(alpha = .80f))
        }
    }
}

@Composable
private fun ReaderBottomItemV4(modifier: Modifier, icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color.copy(alpha = .78f))
        Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = .58f))
    }
}

@Composable
private fun DirectoryDrawerV4(
    chapters: List<ChapterDraft>,
    current: Int,
    palette: ReaderPaletteV4,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(chapters, query) {
        chapters.filter { query.isBlank() || readerDisplayChapterTitleV13(it.title, it.chapterNumber).contains(query, true) }
    }
    val initialIndex = remember(chapters, current) { (chapters.indexOfFirst { it.chapterNumber == current } - 2).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).clickable(onClick = onDismiss)) {
            Surface(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(.86f).align(Alignment.CenterStart).clickable(onClick = {}),
                color = palette.background,
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("目录", style = MaterialTheme.typography.headlineSmall, color = palette.foreground, fontWeight = FontWeight.SemiBold)
                            Text("${chapters.size} 章", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                        }
                        ReaderIconV4(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索章节", palette) {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }
                    }

                    if (searchOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                            placeholder = { Text("搜索章节") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            val selected = item.chapterNumber == current
                            Surface(
                                onClick = { onSelect(item.chapterNumber) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) palette.accent.copy(alpha = .12f) else Color.Transparent,
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.width(3.dp).height(22.dp)
                                            .background(if (selected) palette.accent else Color.Transparent, CircleShape),
                                    )
                                    Text(
                                        readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                                        Modifier.padding(start = 10.dp).weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.foreground.copy(alpha = if (selected) .96f else .76f),
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearancePanelV4(
    palette: ReaderPaletteV4,
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
    var advanced by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)).clickable(onClick = onDismiss)) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(onClick = {}),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = palette.chrome,
            ) {
                if (!advanced) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("阅读", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = palette.foreground, fontWeight = FontWeight.SemiBold)
                            ReaderIconV4(Icons.Rounded.Close, "关闭", palette, onDismiss)
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(
                                Triple("paper", "纸", Color(0xFFF8F5EE)),
                                Triple("warm", "暖", Color(0xFFF1DFC1)),
                                Triple("green", "护", Color(0xFFE9EFE6)),
                                Triple("night", "夜", Color(0xFF171717)),
                            ).forEach { (key, label, color) ->
                                ThemeDotV4(label, color, themeKey == key, palette) { onTheme(key) }
                            }
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundStepV4(Icons.Rounded.Remove, "减小字号", palette) {
                                onBeforeLayoutChange(); onFontSize((fontSize - .5f).coerceAtLeast(15f))
                            }
                            Text(
                                "${fontSize.times(10).roundToInt() / 10f}",
                                Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.foreground,
                                fontWeight = FontWeight.Medium,
                            )
                            RoundStepV4(Icons.Rounded.Add, "增大字号", palette) {
                                onBeforeLayoutChange(); onFontSize((fontSize + .5f).coerceAtMost(28f))
                            }
                        }

                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceV4("左右", pageModeKey == ReaderPageModeV10.PAGE.key, Modifier.weight(1f), palette) {
                                if (pageModeKey != ReaderPageModeV10.PAGE.key) onBeforeLayoutChange()
                                onPageMode(ReaderPageModeV10.PAGE.key)
                            }
                            ChoiceV4("覆盖", pageModeKey == ReaderPageModeV10.COVER.key, Modifier.weight(1f), palette) {
                                if (pageModeKey != ReaderPageModeV10.COVER.key) onBeforeLayoutChange()
                                onPageMode(ReaderPageModeV10.COVER.key)
                            }
                            ChoiceV4("滚动", pageModeKey == ReaderPageModeV10.SCROLL.key, Modifier.weight(1f), palette) {
                                if (pageModeKey != ReaderPageModeV10.SCROLL.key) onBeforeLayoutChange()
                                onPageMode(ReaderPageModeV10.SCROLL.key)
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().clickable { advanced = true }.padding(top = 16.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("排版细节", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = palette.foreground.copy(alpha = .76f))
                            Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = palette.secondary)
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            ReaderIconV4(Icons.Rounded.KeyboardArrowLeft, "返回", palette) { advanced = false }
                            Text("排版细节", Modifier.padding(start = 4.dp).weight(1f), style = MaterialTheme.typography.titleMedium, color = palette.foreground, fontWeight = FontWeight.SemiBold)
                            ReaderIconV4(Icons.Rounded.Close, "关闭", palette, onDismiss)
                        }

                        SettingRowV4("字体", palette) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChoiceV4("衬线", fontKey == "serif", Modifier.width(72.dp), palette) {
                                    if (fontKey != "serif") onBeforeLayoutChange(); onFont("serif")
                                }
                                ChoiceV4("无衬线", fontKey == "sans", Modifier.width(80.dp), palette) {
                                    if (fontKey != "sans") onBeforeLayoutChange(); onFont("sans")
                                }
                            }
                        }
                        SettingRowV4("行距", palette) {
                            PresetsV4(listOf("紧" to 1.56f, "中" to 1.70f, "松" to 1.86f), lineFactor, palette) {
                                onBeforeLayoutChange(); onLine(it)
                            }
                        }
                        SettingRowV4("页边距", palette) {
                            PresetsV4(listOf("窄" to 18f, "中" to 22f, "宽" to 28f), sidePadding, palette) {
                                onBeforeLayoutChange(); onPadding(it)
                            }
                        }
                        SettingRowV4("段间距", palette) {
                            PresetsV4(listOf("紧" to 1f, "中" to 4f, "松" to 8f), paragraphSpacing, palette) {
                                onBeforeLayoutChange(); onParagraph(it)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("首行缩进", style = MaterialTheme.typography.bodyMedium, color = palette.foreground)
                                Text("自然段缩进两个汉字", style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                            }
                            Switch(checked = firstLineIndent, onCheckedChange = {
                                onBeforeLayoutChange(); onIndent(it)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeDotV4(label: String, color: Color, selected: Boolean, palette: ReaderPaletteV4, onClick: () -> Unit) {
    Column(Modifier.width(62.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = color,
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) palette.accent else palette.secondary.copy(alpha = .24f)),
        ) {
            if (selected) Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = if (label == "夜") Color.White else Color(0xFF34312C))
            }
        }
        Text(label, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondary)
    }
}

@Composable
private fun RoundStepV4(icon: ImageVector, description: String, palette: ReaderPaletteV4, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(38.dp), shape = CircleShape, color = palette.foreground.copy(alpha = .07f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(18.dp), tint = palette.foreground.copy(alpha = .78f)) }
    }
}

@Composable
private fun ChoiceV4(text: String, selected: Boolean, modifier: Modifier, palette: ReaderPaletteV4, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) palette.accent.copy(alpha = .14f) else palette.foreground.copy(alpha = .045f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) palette.accent else palette.foreground.copy(alpha = .66f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun SettingRowV4(label: String, palette: ReaderPaletteV4, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, color = palette.foreground.copy(alpha = .74f))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { content() }
    }
    HorizontalDivider(color = palette.secondary.copy(alpha = .12f))
}

@Composable
private fun PresetsV4(options: List<Pair<String, Float>>, current: Float, palette: ReaderPaletteV4, onValue: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (name, value) ->
            val selected = abs(current - value) < .08f
            ChoiceV4(name, selected, Modifier.width(54.dp), palette) { onValue(value) }
        }
    }
}

@Composable
private fun ReaderMorePanelV4(
    book: ReaderBookUi,
    chapterCount: Int,
    currentChapter: Int,
    onDismiss: () -> Unit,
    onStory: () -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
) {
    val palette = readerPaletteV4("paper")
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)).clickable(onClick = onDismiss)) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().clickable(onClick = {}),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = palette.chrome,
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Surface(Modifier.width(64.dp).aspectRatio(.68f), shape = RoundedCornerShape(10.dp), color = palette.foreground.copy(alpha = .06f)) {
                            if (cover != null) Image(cover.asImageBitmap(), book.title, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.padding(start = 13.dp).weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium, color = palette.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("$chapterCount 章 · 当前第 ${currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                        }
                        ReaderIconV4(Icons.Rounded.Close, "关闭", palette, onDismiss)
                    }
                    Spacer(Modifier.height(10.dp))
                    MoreRowV4(Icons.Rounded.Edit, "编辑本章", palette, onEdit)
                    MoreRowV4(Icons.Rounded.TheaterComedy, "进入故事", palette, onStory)
                    MoreRowV4(Icons.Rounded.AutoAwesome, "AI 创作", palette, onWriting)
                }
            }
        }
    }
}

@Composable
private fun MoreRowV4(icon: ImageVector, title: String, palette: ReaderPaletteV4, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(19.dp), tint = palette.accent)
        Text(title, Modifier.padding(start = 13.dp).weight(1f), style = MaterialTheme.typography.bodyMedium, color = palette.foreground)
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = palette.secondary.copy(alpha = .54f))
    }
}

private fun pageForOffsetV4(offsets: List<Int>, offset: Int): Int {
    if (offsets.isEmpty()) return 0
    val safe = offset.coerceAtLeast(0)
    var result = 0
    offsets.forEachIndexed { index, start -> if (start <= safe) result = index }
    return result.coerceIn(0, offsets.lastIndex)
}

private fun nextThemeV4(key: String): String = when (key) {
    "paper" -> "warm"
    "warm" -> "green"
    "green" -> "night"
    else -> "paper"
}

private fun readerPaletteV4(key: String): ReaderPaletteV4 = when (key) {
    "night" -> ReaderPaletteV4(Color(0xFF171717), Color(0xFFE7E2DA), Color(0xFF9B968F), Color(0xFF20201F), Color(0xFF9E8AC8))
    "green" -> ReaderPaletteV4(Color(0xFFE9EFE6), Color(0xFF263028), Color(0xFF69736B), Color(0xFFF1F4EF), Color(0xFF4F7562))
    "warm" -> ReaderPaletteV4(Color(0xFFF1DFC1), Color(0xFF342D24), Color(0xFF786B59), Color(0xFFF5E6CE), Color(0xFF8C6449))
    else -> ReaderPaletteV4(Color(0xFFF8F5EE), Color(0xFF25221E), Color(0xFF817A72), Color(0xFFFCFAF5), Color(0xFF6A6E91))
}
