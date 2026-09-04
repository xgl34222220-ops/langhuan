@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xiguli.langhuan.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LanghuanActionTileV4
import com.xiguli.langhuan.ui.design.LanghuanDividerV4
import com.xiguli.langhuan.ui.design.LanghuanRowV4
import com.xiguli.langhuan.ui.design.LanghuanSheetV4
import com.xiguli.langhuan.ui.design.LanghuanTabsV4
import com.xiguli.langhuan.ui.design.LanghuanTokensV4
import com.xiguli.langhuan.ui.design.langhuanTokensV4
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class HeroReaderPaletteV12(
    val page: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
)

private enum class HeroReaderTabV12 { DETAILS, DIRECTORY, MORE }
private enum class HeroReaderOverlayV12 { NONE, THEME, FONT, SEARCH, TYPE }

private data class HeroReaderActionV12(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ReaderQingmoHeroV12(
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
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F6F2)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        return
    }

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
            IconButton(onClick = { storyMode = false }, modifier = Modifier.padding(10.dp)) {
                Icon(Icons.Rounded.TouchApp, "返回阅读")
            }
        }
        return
    }

    HeroReaderPageV12(
        book = book,
        state = state,
        chapter = chapter,
        startPanel = startOnInfo,
        onBack = onBackToShelf,
        onOpenChapter = viewModel::openReader,
        onEdit = { onOpenEditor(book.id, chapter.chapterNumber) },
        onWriting = { onEnterWriting(book.id) },
        onStory = { storyMode = true },
    )
}

@Composable
private fun HeroReaderPageV12(
    book: ReaderBookUi,
    state: LibraryExperienceState,
    chapter: ChapterDraft,
    startPanel: Boolean,
    onBack: () -> Unit,
    onOpenChapter: (Int) -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember(book.id) { context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val chapters = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val chapterIndex = chapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = chapters.getOrNull(chapterIndex - 1)
    val next = chapters.getOrNull(chapterIndex + 1)

    var panelVisible by remember(chapter.id) { mutableStateOf(startPanel) }
    var tab by rememberSaveable(chapter.id) { mutableStateOf(HeroReaderTabV12.DIRECTORY) }
    var overlay by rememberSaveable { mutableStateOf(HeroReaderOverlayV12.NONE) }
    var typePage by rememberSaveable { mutableStateOf("字号") }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font", 18f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line", 1.75f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph", 3f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("fontKey", "sans") ?: "sans") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme", "tea") ?: "tea") }
    var pageModeKey by remember(book.id) {
        mutableStateOf(prefs.getString("pageMode", ReaderPageModeV10.PAGE.key) ?: ReaderPageModeV10.PAGE.key)
    }
    var volumeTurn by rememberSaveable { mutableStateOf(prefs.getBoolean("volumeTurn", false)) }
    var keepScreen by rememberSaveable { mutableStateOf(prefs.getBoolean("keepScreen", false)) }
    var showTimeBattery by rememberSaveable { mutableStateOf(prefs.getBoolean("timeBattery", true)) }
    var immersive by rememberSaveable { mutableStateOf(prefs.getBoolean("immersive", false)) }
    var clickAnimation by rememberSaveable { mutableStateOf(prefs.getBoolean("clickAnimation", true)) }
    var pullBookmark by rememberSaveable { mutableStateOf(prefs.getBoolean("pullBookmark", true)) }
    var fullNext by rememberSaveable { mutableStateOf(prefs.getBoolean("fullNext", false)) }
    var backgroundMask by rememberSaveable { mutableStateOf(prefs.getBoolean("backgroundMask", false)) }
    var backgroundFollow by rememberSaveable { mutableStateOf(prefs.getBoolean("backgroundFollow", false)) }
    var statusBar by rememberSaveable { mutableStateOf(prefs.getBoolean("statusBar", true)) }
    var navigationBar by rememberSaveable { mutableStateOf(prefs.getBoolean("navigationBar", true)) }
    var lockPortrait by rememberSaveable { mutableStateOf(prefs.getBoolean("lockPortrait", true)) }
    var bookmarked by rememberSaveable(chapter.id) {
        mutableStateOf(prefs.getStringSet("bookmarks", emptySet())?.contains(chapter.chapterNumber.toString()) == true)
    }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val rawPalette = heroReaderPaletteV12(themeKey)
    val palette = remember(rawPalette, backgroundFollow, chapter.chapterNumber) {
        if (!backgroundFollow || themeKey == "night") rawPalette
        else rawPalette.copy(
            page = lerp(
                rawPalette.page,
                if (chapter.chapterNumber % 2 == 0) Color.White else Color(0xFFB8C8AE),
                .035f,
            ),
        )
    }
    val tokens = remember(palette) { langhuanTokensV4(palette.page, palette.text, palette.accent) }
    val family = if (fontKey == "serif") FontFamily.Serif else FontFamily.SansSerif
    val displayTitle = remember(chapter.id, chapter.title) {
        readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
    }
    val readingText = remember(chapter.id, chapter.content) {
        readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
            .ifBlank { "这一章没有正文。" }
    }

    val pagination = rememberReaderPaginationV18(
        text = readingText,
        title = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = 20f,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val pages = pagination.pages.ifEmpty { listOf(readingText) }
    val offsets = pagination.offsets.ifEmpty { listOf(0) }
    val starts = pagination.pageStartsParagraph.ifEmpty { listOf(true) }
    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val initialPage = remember(chapter.id, pagination.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> heroReaderPageForOffsetV12(offsets, saved.textOffset.coerceIn(0, readingText.length))
            else -> saved.pageIndex.coerceIn(0, pages.lastIndex)
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size.coerceAtLeast(1) })
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${pagination.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf(layoutKey) }

    fun currentPage(): Int = pagerState.settledPage.coerceIn(0, pages.lastIndex)
    fun currentOffset(): Int = if (pageMode == ReaderPageModeV10.SCROLL) {
        val fraction = if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        (readingText.length * fraction).roundToInt().coerceIn(0, readingText.length)
    } else offsets.getOrElse(currentPage()) { 0 }.coerceIn(0, readingText.length)

    fun persist() {
        if (crossingChapter) return
        val offset = currentOffset()
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = if (pageMode == ReaderPageModeV10.SCROLL) 0 else currentPage(),
                scrollY = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value else 0,
                positionFraction = if (readingText.isBlank()) 0f else offset.toFloat() / readingText.length.toFloat(),
                textOffset = offset,
                modeKey = pageMode.key,
            ),
        )
    }

    fun rememberAnchor() {
        anchorOffset = currentOffset()
        persist()
    }

    fun toggleBookmark() {
        val key = chapter.chapterNumber.toString()
        val current = prefs.getStringSet("bookmarks", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!current.add(key)) current.remove(key)
        prefs.edit().putStringSet("bookmarks", current).apply()
        bookmarked = key in current
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
        if (page > 0) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(page - 1) else pagerState.scrollToPage(page - 1)
            }
        } else jumpChapter(previous, true)
    }

    fun nextPage() {
        val page = currentPage()
        if (page < pages.lastIndex) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(page + 1) else pagerState.scrollToPage(page + 1)
            }
        } else jumpChapter(next, false)
    }

    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue > 0) {
                val fraction = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length
                scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt())
            }
        } else {
            pagerState.scrollToPage(heroReaderPageForOffsetV12(offsets, targetOffset).coerceIn(0, pages.lastIndex))
        }
        appliedLayoutKey = layoutKey
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode != ReaderPageModeV10.SCROLL) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { persist() }
        }
    }
    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode == ReaderPageModeV10.SCROLL) {
            snapshotFlow { scrollState.value }.distinctUntilChanged().collectLatest {
                delay(180)
                persist()
            }
        }
    }
    LaunchedEffect(volumeTurn) {
        if (volumeTurn) runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(
        fontSize, lineFactor, paragraphSpacing, firstLineIndent, fontKey, themeKey, pageModeKey,
        volumeTurn, keepScreen, showTimeBattery, immersive, clickAnimation, pullBookmark, fullNext,
        backgroundMask, backgroundFollow, statusBar, navigationBar, lockPortrait,
    ) {
        prefs.edit()
            .putFloat("font", fontSize)
            .putFloat("line", lineFactor)
            .putFloat("paragraph", paragraphSpacing)
            .putBoolean("indent", firstLineIndent)
            .putString("fontKey", fontKey)
            .putString("theme", themeKey)
            .putString("pageMode", pageModeKey)
            .putBoolean("volumeTurn", volumeTurn)
            .putBoolean("keepScreen", keepScreen)
            .putBoolean("timeBattery", showTimeBattery)
            .putBoolean("immersive", immersive)
            .putBoolean("clickAnimation", clickAnimation)
            .putBoolean("pullBookmark", pullBookmark)
            .putBoolean("fullNext", fullNext)
            .putBoolean("backgroundMask", backgroundMask)
            .putBoolean("backgroundFollow", backgroundFollow)
            .putBoolean("statusBar", statusBar)
            .putBoolean("navigationBar", navigationBar)
            .putBoolean("lockPortrait", lockPortrait)
            .apply()
    }

    DisposableEffect(chapter.id, layoutKey) { onDispose { persist() } }
    DisposableEffect(keepScreen) {
        if (keepScreen) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(lockPortrait) {
        if (lockPortrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { if (lockPortrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(activity, immersive, statusBar, navigationBar) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (controller != null) {
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars()) else {
                if (statusBar) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
                if (navigationBar) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.page)
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!volumeTurn || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.VolumeUp -> { previousPage(); true }
                    Key.VolumeDown -> { nextPage(); true }
                    else -> false
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(chapter.id, pageModeKey, fullNext, panelVisible) {
                    detectTapGestures(
                        onDoubleTap = { panelVisible = true },
                        onLongPress = { panelVisible = true },
                        onTap = { point ->
                            if (panelVisible) panelVisible = false
                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            else if (fullNext) {
                                if (point.x < size.width * .18f) previousPage() else nextPage()
                            } else when {
                                point.x < size.width * .28f -> previousPage()
                                point.x > size.width * .72f -> nextPage()
                                else -> panelVisible = true
                            }
                        },
                    )
                },
        ) {
            if (pageMode == ReaderPageModeV10.SCROLL) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                ) {
                    Text(
                        displayTitle,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = palette.secondary.copy(alpha = .60f),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(14.dp))
                    HeroReaderWholeBodyV12(
                        readingText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text,
                    )
                    Spacer(Modifier.height(54.dp))
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    val safe = page.coerceIn(0, pages.lastIndex)
                    val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val transition = if (pageMode == ReaderPageModeV10.COVER) {
                        Modifier.graphicsLayer {
                            val distance = rawOffset.absoluteValue.coerceIn(0f, 1f)
                            rotationY = rawOffset * -6f
                            scaleX = 1f - distance * .02f
                            scaleY = 1f - distance * .02f
                            alpha = 1f - distance * .06f
                            translationX = rawOffset * size.width * .045f
                        }
                    } else Modifier
                    Box(transition.fillMaxSize()) {
                        HeroReaderCanvasV12(
                            title = displayTitle,
                            body = pages[safe],
                            pageStartsParagraph = starts.getOrElse(safe) { true },
                            page = safe + 1,
                            pageCount = pages.size,
                            fontSize = fontSize,
                            lineFactor = lineFactor,
                            paragraphSpacing = paragraphSpacing,
                            firstLineIndent = firstLineIndent,
                            family = family,
                            palette = palette,
                            showTimeBattery = showTimeBattery,
                        )
                    }
                }
            }
            if (backgroundMask) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .045f)))
        }

        if (bookmarked) {
            Icon(
                Icons.Outlined.Bookmark,
                "已加入书签",
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 11.dp).size(18.dp),
                tint = palette.accent.copy(alpha = .76f),
            )
        }

        if (pullBookmark) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 76.dp, height = 92.dp)
                    .pointerInput(chapter.id, pullBookmark) {
                        var distance = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount -> if (amount > 0f) distance += amount },
                            onDragEnd = {
                                if (distance >= 36.dp.toPx()) toggleBookmark()
                                distance = 0f
                            },
                            onDragCancel = { distance = 0f },
                        )
                    },
            )
        }

        AnimatedVisibility(
            visible = panelVisible && overlay == HeroReaderOverlayV12.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(90)),
        ) {
            HeroReaderControlsV12(
                tokens = tokens,
                tab = tab,
                book = book,
                chapters = chapters,
                chapter = chapter,
                pageMode = pageMode,
                volumeTurn = volumeTurn,
                keepScreen = keepScreen,
                showTimeBattery = showTimeBattery,
                immersive = immersive,
                clickAnimation = clickAnimation,
                pullBookmark = pullBookmark,
                fullNext = fullNext,
                backgroundMask = backgroundMask,
                backgroundFollow = backgroundFollow,
                statusBar = statusBar,
                navigationBar = navigationBar,
                lockPortrait = lockPortrait,
                bookmarked = bookmarked,
                onTab = { tab = it },
                onBack = { persist(); onBack() },
                onChapter = { number -> jumpChapter(chapters.firstOrNull { it.chapterNumber == number }) },
                onBookmark = ::toggleBookmark,
                onTheme = { panelVisible = false; overlay = HeroReaderOverlayV12.THEME },
                onFont = { panelVisible = false; overlay = HeroReaderOverlayV12.FONT },
                onType = { kind -> typePage = kind; panelVisible = false; overlay = HeroReaderOverlayV12.TYPE },
                onLocate = { tab = HeroReaderTabV12.DIRECTORY },
                onVertical = {
                    rememberAnchor()
                    pageModeKey = if (pageMode == ReaderPageModeV10.SCROLL) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.SCROLL.key
                },
                onSimulated = {
                    rememberAnchor()
                    pageModeKey = if (pageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.COVER.key
                },
                onSearch = { panelVisible = false; overlay = HeroReaderOverlayV12.SEARCH },
                onVolume = { volumeTurn = !volumeTurn },
                onKeepScreen = { keepScreen = !keepScreen },
                onTimeBattery = { showTimeBattery = !showTimeBattery },
                onImmersive = { immersive = !immersive },
                onClickAnimation = { clickAnimation = !clickAnimation },
                onPullBookmark = { pullBookmark = !pullBookmark },
                onFullNext = { fullNext = !fullNext },
                onBackgroundMask = { backgroundMask = !backgroundMask },
                onBackgroundFollow = { backgroundFollow = !backgroundFollow },
                onStatusBar = { statusBar = !statusBar },
                onNavigationBar = { navigationBar = !navigationBar },
                onLockPortrait = { lockPortrait = !lockPortrait },
                onEdit = onEdit,
                onWriting = onWriting,
                onStory = onStory,
            )
        }

        AnimatedVisibility(
            visible = overlay != HeroReaderOverlayV12.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(90)),
        ) {
            when (overlay) {
                HeroReaderOverlayV12.THEME -> HeroThemeSheetV12(
                    tokens, themeKey,
                    onTheme = { rememberAnchor(); themeKey = it },
                    onBack = { overlay = HeroReaderOverlayV12.NONE; panelVisible = true },
                )
                HeroReaderOverlayV12.FONT -> HeroFontSheetV12(
                    tokens, fontKey,
                    onFont = { rememberAnchor(); fontKey = it },
                    onBack = { overlay = HeroReaderOverlayV12.NONE; panelVisible = true },
                )
                HeroReaderOverlayV12.SEARCH -> HeroSearchSheetV12(
                    tokens, chapters,
                    onOpen = { number ->
                        overlay = HeroReaderOverlayV12.NONE
                        jumpChapter(chapters.firstOrNull { it.chapterNumber == number })
                    },
                    onBack = { overlay = HeroReaderOverlayV12.NONE; panelVisible = true },
                )
                HeroReaderOverlayV12.TYPE -> HeroTypeSheetV12(
                    tokens = tokens,
                    title = typePage,
                    fontSize = fontSize,
                    lineFactor = lineFactor,
                    paragraphSpacing = paragraphSpacing,
                    indent = firstLineIndent,
                    onFontSize = { rememberAnchor(); fontSize = it },
                    onLine = { rememberAnchor(); lineFactor = it },
                    onParagraph = { rememberAnchor(); paragraphSpacing = it },
                    onIndent = { rememberAnchor(); firstLineIndent = it },
                    onBack = { overlay = HeroReaderOverlayV12.NONE; panelVisible = true },
                )
                HeroReaderOverlayV12.NONE -> Unit
            }
        }
    }
}

@Composable
private fun HeroReaderCanvasV12(
    title: String,
    body: String,
    pageStartsParagraph: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    palette: HeroReaderPaletteV12,
    showTimeBattery: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.page)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
    ) {
        Text(
            title,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            color = palette.secondary.copy(alpha = .58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            HeroReaderPageBodyV12(
                text = body,
                pageStartsParagraph = pageStartsParagraph,
                fontSize = fontSize,
                lineFactor = lineFactor,
                paragraphSpacing = paragraphSpacing,
                indent = firstLineIndent,
                family = family,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showTimeBattery) {
                Text(heroReaderTimeV12(), fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
                Spacer(Modifier.width(8.dp))
                Text("${heroReaderBatteryV12()}%", fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
            }
            Spacer(Modifier.weight(1f))
            Text("$page/$pageCount", fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
        }
    }
}

@Composable
private fun HeroReaderPageBodyV12(
    text: String,
    pageStartsParagraph: Boolean,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indent: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) { text.split(Regex("\\n+")).filter { it.isNotEmpty() } }
    Column(Modifier.fillMaxSize().clipToBounds()) {
        paragraphs.forEachIndexed { index, paragraph ->
            val shouldIndent = indent && (index > 0 || pageStartsParagraph)
            Text(
                paragraph.trimEnd(),
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
}

@Composable
private fun HeroReaderWholeBodyV12(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indent: Boolean,
    family: FontFamily,
    color: Color,
) {
    val paragraphs = remember(text) { text.split(Regex("\\n+")).filter { it.isNotEmpty() } }
    paragraphs.forEachIndexed { index, paragraph ->
        Text(
            paragraph.trimEnd(),
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineFactor).sp,
                fontFamily = family,
                color = color,
                textIndent = TextIndent(firstLine = if (indent) (fontSize * 2f).sp else 0.sp),
            ),
        )
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}

@Composable
private fun HeroReaderControlsV12(
    tokens: LanghuanTokensV4,
    tab: HeroReaderTabV12,
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapter: ChapterDraft,
    pageMode: ReaderPageModeV10,
    volumeTurn: Boolean,
    keepScreen: Boolean,
    showTimeBattery: Boolean,
    immersive: Boolean,
    clickAnimation: Boolean,
    pullBookmark: Boolean,
    fullNext: Boolean,
    backgroundMask: Boolean,
    backgroundFollow: Boolean,
    statusBar: Boolean,
    navigationBar: Boolean,
    lockPortrait: Boolean,
    bookmarked: Boolean,
    onTab: (HeroReaderTabV12) -> Unit,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
    onBookmark: () -> Unit,
    onTheme: () -> Unit,
    onFont: () -> Unit,
    onType: (String) -> Unit,
    onLocate: () -> Unit,
    onVertical: () -> Unit,
    onSimulated: () -> Unit,
    onSearch: () -> Unit,
    onVolume: () -> Unit,
    onKeepScreen: () -> Unit,
    onTimeBattery: () -> Unit,
    onImmersive: () -> Unit,
    onClickAnimation: () -> Unit,
    onPullBookmark: () -> Unit,
    onFullNext: () -> Unit,
    onBackgroundMask: () -> Unit,
    onBackgroundFollow: () -> Unit,
    onStatusBar: () -> Unit,
    onNavigationBar: () -> Unit,
    onLockPortrait: () -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
) {
    LanghuanSheetV4(tokens = tokens) {
        LanghuanTabsV4(
            labels = listOf("详情", "目录", "更多"),
            selected = tab.ordinal,
            onSelected = { onTab(HeroReaderTabV12.entries[it]) },
            tokens = tokens,
        )
        Spacer(Modifier.height(12.dp))
        when (tab) {
            HeroReaderTabV12.DETAILS -> Column(Modifier.heightIn(max = 400.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(book.title, color = tokens.foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "第 ${chapter.chapterNumber} 章 · ${chapter.title}",
                            Modifier.padding(top = 4.dp),
                            color = tokens.mutedForeground,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onBookmark) {
                        Icon(
                            if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            "书签",
                            tint = if (bookmarked) tokens.primary else tokens.foreground,
                        )
                    }
                }
                LanghuanDividerV4(tokens)
                LanghuanRowV4("编辑本章", tokens, icon = Icons.Rounded.Edit, onClick = onEdit)
                LanghuanRowV4("AI 创作", tokens, icon = Icons.Rounded.AutoStories, onClick = onWriting)
                LanghuanRowV4("进入故事", tokens, icon = Icons.Rounded.TouchApp, onClick = onStory)
                LanghuanRowV4("返回书架", tokens, trailing = "‹", onClick = onBack)
            }
            HeroReaderTabV12.DIRECTORY -> LazyColumn(
                Modifier.heightIn(max = 430.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(chapters, key = { it.id }) { item ->
                    LanghuanRowV4(
                        title = readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                        tokens = tokens,
                        trailing = if (item.id == chapter.id) "当前" else null,
                        onClick = { onChapter(item.chapterNumber) },
                    )
                    LanghuanDividerV4(tokens)
                }
            }
            HeroReaderTabV12.MORE -> {
                val actions = listOf(
                    HeroReaderActionV12("主题", Icons.Rounded.Palette, onClick = onTheme),
                    HeroReaderActionV12("字体", Icons.Rounded.TextFields, onClick = onFont),
                    HeroReaderActionV12("字号", Icons.Rounded.FormatSize, onClick = { onType("字号") }),
                    HeroReaderActionV12("行段", Icons.Rounded.FormatAlignJustify, onClick = { onType("行段") }),
                    HeroReaderActionV12("定位", Icons.Rounded.MyLocation, onClick = onLocate),
                    HeroReaderActionV12("上下翻页", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),
                    HeroReaderActionV12("仿真翻页", Icons.Rounded.Refresh, pageMode == ReaderPageModeV10.COVER, onSimulated),
                    HeroReaderActionV12("全文搜索", Icons.Rounded.Search, onClick = onSearch),
                    HeroReaderActionV12("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),
                    HeroReaderActionV12("屏幕常亮", Icons.Rounded.LightMode, keepScreen, onKeepScreen),
                    HeroReaderActionV12("时间电量", Icons.Rounded.BatteryFull, showTimeBattery, onTimeBattery),
                    HeroReaderActionV12("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),
                    HeroReaderActionV12("点击动画", Icons.Rounded.TouchApp, clickAnimation, onClickAnimation),
                    HeroReaderActionV12("下拉书签", Icons.Outlined.BookmarkBorder, pullBookmark, onPullBookmark),
                    HeroReaderActionV12("全屏下一页", Icons.Rounded.Smartphone, fullNext, onFullNext),
                    HeroReaderActionV12("背景图遮罩", Icons.Rounded.Image, backgroundMask, onBackgroundMask),
                    HeroReaderActionV12("背景跟随", Icons.Rounded.Brightness6, backgroundFollow, onBackgroundFollow),
                    HeroReaderActionV12("状态栏", Icons.Rounded.Smartphone, statusBar, onStatusBar),
                    HeroReaderActionV12("导航栏", Icons.Rounded.FormatIndentIncrease, navigationBar, onNavigationBar),
                    HeroReaderActionV12("锁定竖屏", Icons.Rounded.Landscape, lockPortrait, onLockPortrait),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    gridItems(actions, key = { it.label }) { action ->
                        LanghuanActionTileV4(
                            label = action.label,
                            icon = action.icon,
                            selected = action.selected,
                            tokens = tokens,
                            onClick = action.onClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroThemeSheetV12(
    tokens: LanghuanTokensV4,
    current: String,
    onTheme: (String) -> Unit,
    onBack: () -> Unit,
) {
    LanghuanSheetV4(tokens, title = "阅读主题") {
        listOf("paper" to "纸白", "tea" to "茶纸", "green" to "青叶", "night" to "夜间").forEach { (key, name) ->
            LanghuanRowV4(name, tokens, trailing = if (current == key) "✓" else null, onClick = { onTheme(key) })
            LanghuanDividerV4(tokens)
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun HeroFontSheetV12(
    tokens: LanghuanTokensV4,
    current: String,
    onFont: (String) -> Unit,
    onBack: () -> Unit,
) {
    LanghuanSheetV4(tokens, title = "字体") {
        listOf("sans" to "系统黑体", "serif" to "系统宋体").forEach { (key, name) ->
            LanghuanRowV4(name, tokens, trailing = if (current == key) "✓" else null, onClick = { onFont(key) })
            LanghuanDividerV4(tokens)
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun HeroTypeSheetV12(
    tokens: LanghuanTokensV4,
    title: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indent: Boolean,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onParagraph: (Float) -> Unit,
    onIndent: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    LanghuanSheetV4(tokens, title = title) {
        if (title == "字号") {
            LanghuanRowV4("减小字号", tokens, trailing = "${fontSize.roundToInt()}sp", onClick = { onFontSize((fontSize - 1f).coerceAtLeast(14f)) })
            LanghuanRowV4("增大字号", tokens, trailing = "${fontSize.roundToInt()}sp", onClick = { onFontSize((fontSize + 1f).coerceAtMost(30f)) })
        } else {
            LanghuanRowV4("减小行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor - .05f).coerceAtLeast(1.35f)) })
            LanghuanRowV4("增大行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor + .05f).coerceAtMost(2.20f)) })
            LanghuanRowV4("减小段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing - 1f).coerceAtLeast(0f)) })
            LanghuanRowV4("增大段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing + 1f).coerceAtMost(20f)) })
            LanghuanRowV4("首行缩进", tokens, trailing = if (indent) "开" else "关", onClick = { onIndent(!indent) })
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun HeroSearchSheetV12(
    tokens: LanghuanTokensV4,
    chapters: List<ChapterDraft>,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, chapters) {
        val q = query.trim()
        if (q.isBlank()) emptyList() else chapters.filter {
            it.title.contains(q, true) || it.content.contains(q, true)
        }.take(50)
    }
    LanghuanSheetV4(tokens, title = "全文搜索") {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索整本书") },
        )
        LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
            items(hits, key = { it.id }) { item ->
                LanghuanRowV4(
                    title = readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                    subtitle = heroReaderSearchPreviewV12(item.content, query),
                    tokens = tokens,
                    onClick = { onOpen(item.chapterNumber) },
                )
                LanghuanDividerV4(tokens)
            }
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

private fun heroReaderPaletteV12(key: String): HeroReaderPaletteV12 = when (key) {
    "paper" -> HeroReaderPaletteV12(Color(0xFFF7F3EA), Color(0xFF282622), Color(0xFF716D64), Color(0xFF476B9A))
    "green" -> HeroReaderPaletteV12(Color(0xFFDDE6D1), Color(0xFF283126), Color(0xFF65705F), Color(0xFF4A7652))
    "night" -> HeroReaderPaletteV12(Color(0xFF17191D), Color(0xFFD2D4D8), Color(0xFF858A91), Color(0xFF7EA8E8))
    else -> HeroReaderPaletteV12(Color(0xFFE9D9B9), Color(0xFF302B23), Color(0xFF817866), Color(0xFF4F73A5))
}

private fun heroReaderPageForOffsetV12(offsets: List<Int>, offset: Int): Int {
    if (offsets.isEmpty()) return 0
    var low = 0
    var high = offsets.lastIndex
    var answer = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (offsets[mid] <= offset) {
            answer = mid
            low = mid + 1
        } else high = mid - 1
    }
    return answer
}

private fun heroReaderTimeV12(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun heroReaderBatteryV12(): Int {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 0
}

private fun heroReaderSearchPreviewV12(content: String, query: String): String {
    val q = query.trim()
    if (q.isBlank()) return ""
    val index = content.indexOf(q, ignoreCase = true)
    if (index < 0) return content.replace(Regex("\\s+"), " ").take(72)
    val start = (index - 28).coerceAtLeast(0)
    val end = (index + q.length + 44).coerceAtMost(content.length)
    return content.substring(start, end).replace(Regex("\\s+"), " ")
}
