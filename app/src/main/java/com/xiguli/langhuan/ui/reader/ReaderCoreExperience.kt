package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.xiguli.langhuan.domain.ChapterDraft
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.snapshotFlow
import kotlinx.coroutines.launch

private data class ReaderCorePalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
)

/**
 * Fresh reader implementation. No ReaderExperience, ReaderExperienceEntryGuard,
 * ReaderQingmoChrome, ReaderPagedLayoutV14 or old reader screens are mounted here.
 *
 * The chapter is already resolved before this screen is entered. The pager is created at the
 * persisted page immediately, so the first tap/swipe is an actual reading action instead of a
 * hidden restore transition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderCoreExperience(
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("正在准备正文", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                IconButton(onClick = { storyMode = false }) { Icon(Icons.Rounded.ArrowBack, "返回阅读") }
            }
        }
        return
    }

    ReaderCorePage(
        book = book,
        state = state,
        chapter = chapter,
        onBack = onBackToShelf,
        onOpenInfo = { showInfo = true },
        onOpenChapter = { number, atEnd ->
            ReaderProgressStoreV11.moveTo(
                context = LocalContext.current,
                bookId = book.id,
                chapterNumber = number,
                pageIndex = 0,
                scrollY = 0,
                modeKey = currentReaderMode(LocalContext.current, book.id),
                positionFraction = if (atEnd) 1f else 0f,
                textOffset = if (atEnd) Int.MAX_VALUE else 0,
            )
            viewModel.openReader(number)
        },
        onStory = { storyMode = true },
        onWriting = { onEnterWriting(book.id) },
        onEdit = { onOpenEditor(book.id, chapter.chapterNumber) },
    )

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (book.genre.isNotBlank()) {
                    Text(book.genre, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    book.premise.ifBlank { "暂无简介" },
                    Modifier.padding(top = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("${state.chapters.size} 章 · ${book.currentWords} 字", style = MaterialTheme.typography.labelLarge)
                Button(
                    onClick = { showInfo = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                ) { Text("继续阅读") }
                OutlinedButton(
                    onClick = {
                        showInfo = false
                        onEnterWriting(book.id)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("进入创作") }
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderCorePage(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    chapter: ChapterDraft,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenChapter: (Int, Boolean) -> Unit,
    onStory: () -> Unit,
    onWriting: () -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(book.id) { context.getSharedPreferences("reader_core_settings_v1", Context.MODE_PRIVATE) }
    val ordered = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val chapterIndex = ordered.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = ordered.getOrNull(chapterIndex - 1)
    val next = ordered.getOrNull(chapterIndex + 1)

    var chromeVisible by remember(chapter.id) { mutableStateOf(false) }
    var showDirectory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font_${book.id}", 20f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line_${book.id}", 1.65f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("padding_${book.id}", 22f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph_${book.id}", 8f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent_${book.id}", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("family_${book.id}", "serif") ?: "serif") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme_${book.id}", "paper") ?: "paper") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(prefs.getString("page_mode_${book.id}", ReaderPageModeV10.PAGE.key) ?: ReaderPageModeV10.PAGE.key)
    }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val palette = readerCorePalette(themeKey)
    val family = readerCoreFont(fontKey)
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
    val leading = if (previous != null) 1 else 0
    val trailing = if (next != null) 1 else 0
    val totalPages = (leading + pages.size + trailing).coerceAtLeast(1)
    val initialContentPage = remember(chapter.id, measured.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> pageForRawTextOffset(offsets, saved.textOffset.coerceIn(0, readingText.length))
            saved.modeKey == pageMode.key -> saved.pageIndex.coerceIn(0, pages.lastIndex)
            else -> 0
        }
    }
    val pagerState = rememberPagerState(
        initialPage = (leading + initialContentPage).coerceIn(0, totalPages - 1),
        pageCount = { totalPages },
    )
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) {
        mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length))
    }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${sidePadding.roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measured.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf(layoutKey) }

    fun isContentPage(page: Int): Boolean = page in leading until (leading + pages.size)
    fun contentPage(page: Int = pagerState.settledPage): Int = (page - leading).coerceIn(0, pages.lastIndex)
    fun currentTextOffset(): Int = when (pageMode) {
        ReaderPageModeV10.SCROLL -> {
            val fraction = if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            (readingText.length * fraction).roundToInt().coerceIn(0, readingText.length)
        }
        else -> offsets.getOrElse(contentPage()) { 0 }.coerceIn(0, readingText.length)
    }
    fun currentFraction(): Float = if (readingText.isBlank()) 0f else currentTextOffset().toFloat() / readingText.length.toFloat()

    fun persist() {
        if (crossingChapter) return
        if (pageMode != ReaderPageModeV10.SCROLL && !isContentPage(pagerState.settledPage)) return
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else contentPage(),
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

    fun moveChapter(target: ChapterDraft?, atEnd: Boolean) {
        target ?: return
        persist()
        crossingChapter = true
        onOpenChapter(target.chapterNumber, atEnd)
    }

    fun tapPrevious() {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> scope.launch {
                val target = (scrollState.value - scrollState.viewportSize.coerceAtLeast(1)).coerceAtLeast(0)
                scrollState.animateScrollTo(target)
            }
            else -> {
                val p = pagerState.settledPage
                if (isContentPage(p) && contentPage(p) > 0) {
                    scope.launch { pagerState.animateScrollToPage(p - 1) }
                } else if (previous != null) {
                    moveChapter(previous, atEnd = true)
                }
            }
        }
    }

    fun tapNext() {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> scope.launch {
                val target = (scrollState.value + scrollState.viewportSize.coerceAtLeast(1)).coerceAtMost(scrollState.maxValue)
                if (target == scrollState.value && next != null) moveChapter(next, atEnd = false)
                else scrollState.animateScrollTo(target)
            }
            else -> {
                val p = pagerState.settledPage
                if (isContentPage(p) && contentPage(p) < pages.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(p + 1) }
                } else if (next != null) {
                    moveChapter(next, atEnd = false)
                }
            }
        }
    }

    // Layout changes remap the same raw-text anchor without hiding/replacing the reader surface.
    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue > 0) {
                val fraction = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length.toFloat()
                scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt().coerceIn(0, scrollState.maxValue))
            }
        } else {
            val target = pageForRawTextOffset(offsets, targetOffset)
            pagerState.scrollToPage((leading + target).coerceIn(0, totalPages - 1))
        }
        appliedLayoutKey = layoutKey
    }

    // Scroll restore happens in place; no loading cover is ever painted.
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
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                when {
                    leading == 1 && page == 0 && previous != null && !crossingChapter -> moveChapter(previous, atEnd = true)
                    page >= leading + pages.size && next != null && !crossingChapter -> moveChapter(next, atEnd = false)
                    isContentPage(page) -> persist()
                }
            }
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode != ReaderPageModeV10.SCROLL) return@LaunchedEffect
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collectLatest {
                delay(180)
                persist()
            }
    }

    DisposableEffect(chapter.id, layoutKey) {
        onDispose { persist() }
    }

    SideEffect {
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

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .pointerInput(chapter.id, pageModeKey, pagerState.settledPage) {
                detectTapGestures { offset ->
                    when {
                        offset.x < size.width * .30f -> tapPrevious()
                        offset.x > size.width * .70f -> tapNext()
                        else -> chromeVisible = !chromeVisible
                    }
                }
            }
    ) {
        when (pageMode) {
            ReaderPageModeV10.SCROLL -> Column(
                Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = sidePadding.dp, vertical = 18.dp)
            ) {
                Text(
                    displayTitle,
                    style = TextStyle(
                        fontSize = (fontSize + 3).sp,
                        lineHeight = (fontSize + 8).sp,
                        fontFamily = family,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.foreground,
                    ),
                )
                Spacer(Modifier.height(18.dp))
                ReaderCoreParagraphs(readingText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.foreground)
                Spacer(Modifier.height(54.dp))
                Text(
                    if (next == null) "— 已读到全书末尾 —" else "下一章 · ${readerDisplayChapterTitleV13(next.title, next.chapterNumber)}",
                    Modifier.fillMaxWidth().padding(bottom = 22.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondary,
                )
            }

            ReaderPageModeV10.PAGE, ReaderPageModeV10.COVER -> HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
                beyondViewportPageCount = 1,
            ) { page ->
                when {
                    leading == 1 && page == 0 -> ReaderCoreBoundary("上一章", previous, palette)
                    page >= leading + pages.size -> ReaderCoreBoundary("下一章", next, palette)
                    else -> {
                        val content = contentPage(page)
                        ReaderCorePageContent(
                            pageText = pages[content],
                            title = displayTitle,
                            firstPage = content == 0,
                            page = content + 1,
                            pageCount = pages.size,
                            fontSize = fontSize,
                            lineFactor = lineFactor,
                            sidePadding = sidePadding,
                            paragraphSpacing = paragraphSpacing,
                            firstLineIndent = firstLineIndent && measured.indentFirstParagraph.getOrElse(content) { true },
                            family = family,
                            palette = palette,
                        )
                    }
                }
            }
        }

        if (!chromeVisible) {
            Text(
                "${chapterIndex + 1}/${ordered.size.coerceAtLeast(1)} · ${(currentFraction().coerceIn(0f, 1f) * 100).roundToInt()}%",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondary.copy(alpha = .72f),
            )
        } else {
            ReaderCoreChrome(
                modifier = Modifier.fillMaxSize(),
                bookTitle = book.title,
                chapterTitle = displayTitle,
                canPrevious = previous != null,
                canNext = next != null,
                palette = palette,
                onBack = { persist(); onBack() },
                onInfo = onOpenInfo,
                onPrevious = { moveChapter(previous, atEnd = false) },
                onNext = { moveChapter(next, atEnd = false) },
                onDirectory = { showDirectory = true },
                onSettings = { showSettings = true },
                onStory = onStory,
                onWriting = onWriting,
                onEdit = onEdit,
            )
        }
    }

    if (showDirectory) {
        ReaderCoreDirectory(
            chapters = ordered,
            current = chapter.chapterNumber,
            onDismiss = { showDirectory = false },
            onSelect = {
                showDirectory = false
                persist()
                onOpenChapter(it, false)
            },
        )
    }

    if (showSettings) {
        ReaderCoreSettings(
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
private fun ReaderCorePageContent(
    pageText: String,
    title: String,
    firstPage: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    sidePadding: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    palette: ReaderCorePalette,
) {
    Column(
        Modifier.fillMaxSize().background(palette.background).padding(horizontal = sidePadding.dp, vertical = 8.dp)
    ) {
        if (firstPage) {
            Text(
                title,
                style = TextStyle(
                    fontSize = (fontSize + 3).sp,
                    lineHeight = (fontSize + 8).sp,
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.foreground,
                ),
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
            Spacer(Modifier.height(10.dp))
        }
        ReaderCoreParagraphs(pageText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.foreground)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${page}/${pageCount}", style = MaterialTheme.typography.labelSmall, color = palette.secondary)
            Spacer(Modifier.weight(1f))
            Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = palette.secondary.copy(alpha = .65f))
        }
    }
}

@Composable
private fun ReaderCoreParagraphs(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) {
        text.replace("\r\n", "\n").split(Regex("\\n+")).filter { it.isNotBlank() }
    }
    paragraphs.forEachIndexed { index, paragraph ->
        Text(
            paragraph.trim(),
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineFactor).sp,
                fontFamily = family,
                color = color,
                textIndent = TextIndent(firstLine = if (firstLineIndent && index == 0) (fontSize * 2f).sp else 0.sp),
            ),
        )
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}

@Composable
private fun ReaderCoreBoundary(label: String, chapter: ChapterDraft?, palette: ReaderCorePalette) {
    Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold, color = palette.foreground)
            Text(
                chapter?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) } ?: "没有更多章节",
                Modifier.padding(top = 6.dp),
                color = palette.secondary,
            )
        }
    }
}

@Composable
private fun ReaderCoreChrome(
    modifier: Modifier,
    bookTitle: String,
    chapterTitle: String,
    canPrevious: Boolean,
    canNext: Boolean,
    palette: ReaderCorePalette,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDirectory: () -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
    onWriting: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(modifier) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = palette.chrome,
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.height(58.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = palette.foreground) }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = palette.foreground)
                    Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                }
                IconButton(onClick = onInfo) { Icon(Icons.Rounded.MoreHoriz, "图书信息", tint = palette.foreground) }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = palette.chrome,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious, enabled = canPrevious) { Text("上一章") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onNext, enabled = canNext) { Text("下一章") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ReaderCoreAction(Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
                    ReaderCoreAction(Icons.Rounded.Tune, "排版", palette.foreground, onSettings)
                    ReaderCoreAction(Icons.Rounded.TheaterComedy, "故事", palette.foreground, onStory)
                    ReaderCoreAction(Icons.Rounded.Edit, "编辑", palette.foreground, onEdit)
                    ReaderCoreAction(Icons.Rounded.AutoAwesome, "创作", palette.foreground, onWriting)
                }
            }
        }
    }
}

@Composable
private fun ReaderCoreAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(58.dp).clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
        Text(label, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderCoreDirectory(
    chapters: List<ChapterDraft>,
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 18.dp)) {
            Text("目录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("共 ${chapters.size} 章", Modifier.padding(top = 3.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth()) {
                items(chapters, key = { it.id }) { chapter ->
                    Surface(
                        onClick = { onSelect(chapter.chapterNumber) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (chapter.chapterNumber == current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ) {
                        Text(
                            readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
                            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderCoreSettings(
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
    var fs by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var lf by remember(lineFactor) { mutableFloatStateOf(lineFactor) }
    var pad by remember(sidePadding) { mutableFloatStateOf(sidePadding) }
    var para by remember(paragraphSpacing) { mutableFloatStateOf(paragraphSpacing) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Text("翻页", Modifier.padding(top = 18.dp, bottom = 8.dp), fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ReaderPageModeV10.PAGE to "左右滑页",
                    ReaderPageModeV10.COVER to "覆盖翻页",
                    ReaderPageModeV10.SCROLL to "上下滚动",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = pageModeKey == mode.key,
                        onClick = {
                            if (pageModeKey != mode.key) onBeforeLayoutChange()
                            onPageMode(mode.key)
                        },
                        label = { Text(label) },
                    )
                }
            }

            Text("主题", Modifier.padding(top = 14.dp, bottom = 8.dp), fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("paper" to "纸张", "warm" to "暖黄", "green" to "护眼", "night" to "夜间").forEach { (key, label) ->
                    FilterChip(selected = themeKey == key, onClick = { onTheme(key) }, label = { Text(label) })
                }
            }

            Text("字体", Modifier.padding(top = 14.dp, bottom = 8.dp), fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("serif" to "宋体感", "sans" to "无衬线").forEach { (key, label) ->
                    FilterChip(
                        selected = fontKey == key,
                        onClick = {
                            if (fontKey != key) onBeforeLayoutChange()
                            onFont(key)
                        },
                        label = { Text(label) },
                    )
                }
            }

            ReaderCoreSlider("字号", fs, 15f..30f, { fs = it }) {
                onBeforeLayoutChange(); onFontSize(fs)
            }
            ReaderCoreSlider("行距", lf, 1.25f..2.25f, { lf = it }) {
                onBeforeLayoutChange(); onLine(lf)
            }
            ReaderCoreSlider("页边距", pad, 12f..42f, { pad = it }) {
                onBeforeLayoutChange(); onPadding(pad)
            }
            ReaderCoreSlider("段间距", para, 0f..18f, { para = it }) {
                onBeforeLayoutChange(); onParagraph(para)
            }

            Row(
                Modifier.fillMaxWidth().clickable {
                    onBeforeLayoutChange()
                    onIndent(!firstLineIndent)
                }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("首行缩进", fontWeight = FontWeight.Medium)
                    Text("正文段落首行缩进两个汉字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = firstLineIndent, onCheckedChange = {
                    onBeforeLayoutChange(); onIndent(it)
                })
            }
            Spacer(Modifier.navigationBarsPadding().height(10.dp))
        }
    }
}

@Composable
private fun ReaderCoreSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(String.format("%.1f", value), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onValue, valueRange = range, onValueChangeFinished = onFinished)
    }
}

private fun readerCoreFont(key: String): FontFamily = when (key) {
    "sans" -> FontFamily.SansSerif
    else -> FontFamily.Serif
}

private fun readerCorePalette(key: String): ReaderCorePalette = when (key) {
    "night" -> ReaderCorePalette(Color(0xFF141311), Color(0xFFEAE5DC), Color(0xFF9F9A91), Color(0xFF1D1B18))
    "green" -> ReaderCorePalette(Color(0xFFE8F0E5), Color(0xFF253126), Color(0xFF667267), Color(0xFFF0F5EE))
    "warm" -> ReaderCorePalette(Color(0xFFF5E8CE), Color(0xFF352D23), Color(0xFF776A59), Color(0xFFF8EDD8))
    else -> ReaderCorePalette(Color(0xFFF4F0E6), Color(0xFF302D28), Color(0xFF756F65), Color(0xFFF9F6EF))
}
