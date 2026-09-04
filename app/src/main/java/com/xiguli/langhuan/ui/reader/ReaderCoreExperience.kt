package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
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
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.ShadcnButton
import com.xiguli.langhuan.ui.design.ShadcnButtonSize
import com.xiguli.langhuan.ui.design.ShadcnButtonVariant
import com.xiguli.langhuan.ui.design.ShadcnCard
import com.xiguli.langhuan.ui.design.ShadcnIconButton
import com.xiguli.langhuan.ui.design.ShadcnInput
import com.xiguli.langhuan.ui.design.ShadcnMenuRow
import com.xiguli.langhuan.ui.design.ShadcnSeparator
import com.xiguli.langhuan.ui.design.ShadcnTabs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class ReaderCorePalette(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val chrome: Color,
)

/**
 * Reader core with a stable one-pass resume/pager state and a shadcn/ui New York inspired chrome.
 * The text gesture layer is physically separated from the toolbar layer so toolbar taps can never
 * be interpreted as page-turn/toggle taps.
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
    val t = LocalLanghuanUiTokens.current

    if (chapter == null) {
        Surface(Modifier.fillMaxSize(), color = t.background) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.chapters.isEmpty()) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(30.dp), tint = t.mutedForeground)
                        Text("没有可阅读章节", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground)
                        ShadcnButton(
                            text = "返回书架",
                            onClick = onBackToShelf,
                            modifier = Modifier.padding(top = 12.dp),
                            variant = ShadcnButtonVariant.OUTLINE,
                            size = ShadcnButtonSize.SM,
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.foreground)
                        Text("正在准备正文", Modifier.padding(top = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
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
            ShadcnIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = "返回阅读",
                onClick = { storyMode = false },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                variant = ShadcnButtonVariant.OUTLINE,
            )
        }
        return
    }

    ReaderCorePage(
        book = book,
        state = state,
        chapter = chapter,
        onBack = onBackToShelf,
        onOpenInfo = { showInfo = true },
        onOpenChapter = viewModel::openReader,
        onStory = { storyMode = true },
        onWriting = { onEnterWriting(book.id) },
        onEdit = { onOpenEditor(book.id, chapter.chapterNumber) },
    )

    if (showInfo) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(book.genre.ifBlank { "小说" }, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                    }
                    ShadcnIconButton(Icons.Rounded.Close, "关闭", { showInfo = false })
                }
                ShadcnCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Text(book.premise.ifBlank { "暂无简介" }, style = MaterialTheme.typography.bodyMedium, color = t.foreground)
                        ShadcnSeparator(Modifier.padding(vertical = 13.dp))
                        Text("${state.chapters.size} 章 · ${book.currentWords} 字", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                    }
                }
                ShadcnButton(
                    text = "继续阅读",
                    onClick = { showInfo = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
                ShadcnButton(
                    text = "进入创作",
                    onClick = { showInfo = false; onEnterWriting(book.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    variant = ShadcnButtonVariant.OUTLINE,
                )
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
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
    onOpenChapter: (Int) -> Unit,
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
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
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

    fun jumpChapter(target: ChapterDraft?, atEnd: Boolean = false) {
        target ?: return
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
        val p = pagerState.settledPage
        if (isContentPage(p) && contentPage(p) > 0) {
            scope.launch { pagerState.animateScrollToPage(p - 1) }
        } else if (previous != null) {
            jumpChapter(previous, atEnd = true)
        }
    }

    fun tapNext() {
        val p = pagerState.settledPage
        if (isContentPage(p) && contentPage(p) < pages.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(p + 1) }
        } else if (next != null) {
            jumpChapter(next, atEnd = false)
        }
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
            val target = pageForRawTextOffset(offsets, targetOffset)
            pagerState.scrollToPage((leading + target).coerceIn(0, totalPages - 1))
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
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                when {
                    leading == 1 && page == 0 && previous != null && !crossingChapter -> jumpChapter(previous, atEnd = true)
                    page >= leading + pages.size && next != null && !crossingChapter -> jumpChapter(next, atEnd = false)
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

    DisposableEffect(chapter.id, layoutKey) {
        onDispose { persist() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Reading gesture surface is behind chrome; controls never share this pointer input node.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(chapter.id, pageModeKey, pagerState.settledPage) {
                    detectTapGestures { offset ->
                        if (pageMode == ReaderPageModeV10.SCROLL) {
                            chromeVisible = !chromeVisible
                        } else {
                            when {
                                offset.x < size.width * .30f -> tapPrevious()
                                offset.x > size.width * .70f -> tapNext()
                                else -> chromeVisible = !chromeVisible
                            }
                        }
                    }
                },
        ) {
            when (pageMode) {
                ReaderPageModeV10.SCROLL -> Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = sidePadding.dp, vertical = 18.dp),
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
            }
        }

        if (chromeVisible) {
            ReaderCoreChrome(
                modifier = Modifier.fillMaxSize(),
                bookTitle = book.title,
                chapterTitle = displayTitle,
                canPrevious = previous != null,
                canNext = next != null,
                palette = palette,
                onBack = { persist(); onBack() },
                onInfo = onOpenInfo,
                onPrevious = { jumpChapter(previous) },
                onNext = { jumpChapter(next) },
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
            onSelect = { number ->
                showDirectory = false
                val target = ordered.firstOrNull { it.chapterNumber == number }
                jumpChapter(target)
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
    Column(Modifier.fillMaxSize().background(palette.background).padding(horizontal = sidePadding.dp, vertical = 8.dp)) {
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
            Text("$page/$pageCount", style = MaterialTheme.typography.labelSmall, color = palette.secondary)
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
    val paragraphs = remember(text) { text.replace("\r\n", "\n").split(Regex("\\n+")).filter { it.isNotBlank() } }
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
    val t = LocalLanghuanUiTokens.current
    Box(modifier) {
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = palette.chrome,
            contentColor = palette.foreground,
        ) {
            Column {
                Row(Modifier.height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShadcnIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = palette.foreground)
                        Text(bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = palette.secondary)
                    }
                    ShadcnIconButton(Icons.Rounded.MoreHoriz, "图书信息", onInfo)
                }
                HorizontalDivider(thickness = 1.dp, color = palette.secondary.copy(alpha = .18f))
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = palette.chrome,
            contentColor = palette.foreground,
        ) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = palette.secondary.copy(alpha = .18f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShadcnButton(
                        text = "上一章",
                        onClick = onPrevious,
                        enabled = canPrevious,
                        variant = ShadcnButtonVariant.GHOST,
                        size = ShadcnButtonSize.SM,
                    )
                    Spacer(Modifier.weight(1f))
                    ShadcnButton(
                        text = "下一章",
                        onClick = onNext,
                        enabled = canNext,
                        variant = ShadcnButtonVariant.GHOST,
                        size = ShadcnButtonSize.SM,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
                    ReaderCoreAction(Modifier.weight(1f), Icons.Rounded.FormatListBulleted, "目录", palette.foreground, onDirectory)
                    ReaderCoreAction(Modifier.weight(1f), Icons.Rounded.Tune, "排版", palette.foreground, onSettings)
                    ReaderCoreAction(Modifier.weight(1f), Icons.Rounded.TheaterComedy, "故事", palette.foreground, onStory)
                    ReaderCoreAction(Modifier.weight(1f), Icons.Rounded.Edit, "编辑", palette.foreground, onEdit)
                    ReaderCoreAction(Modifier.weight(1f), Icons.Rounded.AutoAwesome, "创作", palette.foreground, onWriting)
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun ReaderCoreAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = color)
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
    val t = LocalLanghuanUiTokens.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(chapters, query) {
        chapters.filter { query.isBlank() || readerDisplayChapterTitleV13(it.title, it.chapterNumber).contains(query, true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("目录", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    Text("共 ${chapters.size} 章", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                }
                ShadcnIconButton(Icons.Rounded.Close, "关闭", onDismiss)
            }
            ShadcnInput(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
                placeholder = "搜索章节",
                leadingIcon = Icons.Rounded.Search,
            )
            ShadcnCard(Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { item ->
                        val selected = item.chapterNumber == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (selected) t.muted else Color.Transparent)
                                .clickable { onSelect(item.chapterNumber) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                                Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = t.foreground,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (selected) Icon(Icons.Rounded.Check, "当前章节", Modifier.size(17.dp), tint = t.foreground)
                        }
                        if (item != filtered.lastOrNull()) ShadcnSeparator()
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
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
    val t = LocalLanghuanUiTokens.current
    var fs by remember(fontSize) { mutableFloatStateOf(fontSize) }
    var lf by remember(lineFactor) { mutableFloatStateOf(lineFactor) }
    var pad by remember(sidePadding) { mutableFloatStateOf(sidePadding) }
    var para by remember(paragraphSpacing) { mutableFloatStateOf(paragraphSpacing) }

    val pageModes = listOf(
        ReaderPageModeV10.PAGE.key to "左右",
        ReaderPageModeV10.COVER.key to "覆盖",
        ReaderPageModeV10.SCROLL.key to "滚动",
    )
    val themes = listOf("paper" to "纸张", "warm" to "暖黄", "green" to "护眼", "night" to "夜间")
    val fonts = listOf("serif" to "衬线", "sans" to "无衬线")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("阅读设置", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    Text("调整后保持当前阅读位置", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                }
                ShadcnIconButton(Icons.Rounded.Close, "关闭", onDismiss)
            }

            ReaderSettingLabel("翻页")
            ShadcnTabs(
                items = pageModes.map { it.second },
                selectedIndex = pageModes.indexOfFirst { it.first == pageModeKey }.coerceAtLeast(0),
                onSelected = { index ->
                    val key = pageModes[index].first
                    if (key != pageModeKey) onBeforeLayoutChange()
                    onPageMode(key)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            ReaderSettingLabel("主题")
            ShadcnTabs(
                items = themes.map { it.second },
                selectedIndex = themes.indexOfFirst { it.first == themeKey }.coerceAtLeast(0),
                onSelected = { onTheme(themes[it].first) },
                modifier = Modifier.fillMaxWidth(),
            )

            ReaderSettingLabel("字体")
            ShadcnTabs(
                items = fonts.map { it.second },
                selectedIndex = fonts.indexOfFirst { it.first == fontKey }.coerceAtLeast(0),
                onSelected = { index ->
                    val key = fonts[index].first
                    if (key != fontKey) onBeforeLayoutChange()
                    onFont(key)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            ReaderSettingLabel("排版")
            ShadcnCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    ReaderCoreSlider("字号", fs, 15f..30f, { fs = it }) { onBeforeLayoutChange(); onFontSize(fs) }
                    ShadcnSeparator()
                    ReaderCoreSlider("行距", lf, 1.25f..2.25f, { lf = it }) { onBeforeLayoutChange(); onLine(lf) }
                    ShadcnSeparator()
                    ReaderCoreSlider("页边距", pad, 12f..42f, { pad = it }) { onBeforeLayoutChange(); onPadding(pad) }
                    ShadcnSeparator()
                    ReaderCoreSlider("段间距", para, 0f..18f, { para = it }) { onBeforeLayoutChange(); onParagraph(para) }
                    ShadcnSeparator()
                    Row(
                        Modifier.fillMaxWidth().clickable { onBeforeLayoutChange(); onIndent(!firstLineIndent) }.padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("首行缩进", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                            Text("正文段落首行缩进两个汉字", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                        }
                        Switch(checked = firstLineIndent, onCheckedChange = { onBeforeLayoutChange(); onIndent(it) })
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(14.dp))
        }
    }
}

@Composable
private fun ReaderSettingLabel(text: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text,
        Modifier.padding(top = 16.dp, start = 2.dp, bottom = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        color = t.mutedForeground,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ReaderCoreSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(String.format("%.1f", value), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            onValueChangeFinished = onFinished,
            modifier = Modifier.padding(top = 2.dp),
        )
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
