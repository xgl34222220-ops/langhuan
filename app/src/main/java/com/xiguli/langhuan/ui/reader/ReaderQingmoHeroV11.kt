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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.weight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
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
import com.xiguli.langhuan.ui.design.LanghuanComponentTokensV3
import com.xiguli.langhuan.ui.design.LanghuanHairlineV3
import com.xiguli.langhuan.ui.design.LanghuanHeroActionTileV3
import com.xiguli.langhuan.ui.design.LanghuanHeroRowV3
import com.xiguli.langhuan.ui.design.LanghuanHeroSheetV3
import com.xiguli.langhuan.ui.design.LanghuanHeroTabsV3
import com.xiguli.langhuan.ui.design.langhuanComponentTokensV3
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class ReaderPaletteV11(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
)

private enum class ReaderPanelTabV11 { DETAILS, DIRECTORY, MORE }
private enum class ReaderOverlayV11 { NONE, THEME, FONT, SEARCH, TYPE }

private data class ReaderActionV11(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ReaderQingmoHeroV11(
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
                Icon(Icons.Rounded.Close, "返回阅读")
            }
        }
        return
    }

    ReaderPageV11(
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
private fun ReaderPageV11(
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
    var tab by rememberSaveable(chapter.id) { mutableStateOf(ReaderPanelTabV11.DIRECTORY) }
    var overlay by rememberSaveable { mutableStateOf(ReaderOverlayV11.NONE) }
    var quickType by rememberSaveable { mutableStateOf("字号") }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font", 18f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line", 1.75f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph", 3f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("fontKey", "sans") ?: "sans") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme", "tea") ?: "tea") }
    var pageModeKey by remember(book.id) { mutableStateOf(prefs.getString("pageMode", ReaderPageModeV10.PAGE.key) ?: ReaderPageModeV10.PAGE.key) }

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
    val basePalette = readerPaletteV11(themeKey)
    val palette = remember(basePalette, backgroundFollow, chapter.chapterNumber) {
        if (!backgroundFollow || themeKey == "night") basePalette else {
            val target = if (chapter.chapterNumber % 2 == 0) Color.White else Color(0xFFB8C8AE)
            basePalette.copy(background = lerp(basePalette.background, target, .04f))
        }
    }
    val tokens = remember(palette) { langhuanComponentTokensV3(palette.background, palette.text, palette.accent) }
    val family = if (fontKey == "serif") FontFamily.Serif else FontFamily.SansSerif
    val displayTitle = remember(chapter.id, chapter.title) {
        readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
    }
    val readingText = remember(chapter.id, chapter.content) {
        readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
            .ifBlank { "这一章没有正文。" }
    }

    val measured = rememberReaderMeasuredPaginationV17(
        text = readingText,
        displayTitle = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = 20f,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val pages = measured.pages.ifEmpty { listOf(readingText) }
    val offsets = measured.offsets.ifEmpty { listOf(0) }
    val starts = measured.firstParagraphStartsAtBoundary.ifEmpty { listOf(true) }
    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val initialPage = remember(chapter.id, measured.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> readerPageForOffsetV11(offsets, saved.textOffset.coerceIn(0, readingText.length))
            else -> saved.pageIndex.coerceIn(0, pages.lastIndex)
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size.coerceAtLeast(1) })
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measured.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf(layoutKey) }

    fun currentPage() = pagerState.settledPage.coerceIn(0, pages.lastIndex)
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
        bookmarked = current.contains(key)
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
        if (page > 0) scope.launch {
            if (clickAnimation) pagerState.animateScrollToPage(page - 1) else pagerState.scrollToPage(page - 1)
        } else jumpChapter(previous, true)
    }

    fun nextPage() {
        val page = currentPage()
        if (page < pages.lastIndex) scope.launch {
            if (clickAnimation) pagerState.animateScrollToPage(page + 1) else pagerState.scrollToPage(page + 1)
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
            pagerState.scrollToPage(readerPageForOffsetV11(offsets, targetOffset).coerceIn(0, pages.lastIndex))
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
        volumeTurn, keepScreen, showTimeBattery, immersive, clickAnimation, pullBookmark,
        fullNext, backgroundMask, backgroundFollow, statusBar, navigationBar, lockPortrait,
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
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                if (statusBar) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
                if (navigationBar) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
            }
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
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
                        onTap = { offset ->
                            if (panelVisible) panelVisible = false
                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            else if (fullNext) {
                                if (offset.x < size.width * .18f) previousPage() else nextPage()
                            } else when {
                                offset.x < size.width * .28f -> previousPage()
                                offset.x > size.width * .72f -> nextPage()
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(displayTitle, fontSize = 11.sp, lineHeight = 15.sp, color = palette.secondary.copy(alpha = .62f), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(14.dp))
                    ReaderWholeBodyV11(readingText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text)
                    Spacer(Modifier.height(54.dp))
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    val safe = page.coerceIn(0, pages.lastIndex)
                    val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val pageModifier = if (pageMode == ReaderPageModeV10.COVER) {
                        Modifier.graphicsLayer {
                            val distance = rawOffset.absoluteValue.coerceIn(0f, 1f)
                            rotationY = rawOffset * -6f
                            scaleX = 1f - distance * .02f
                            scaleY = 1f - distance * .02f
                            alpha = 1f - distance * .06f
                            translationX = rawOffset * size.width * .045f
                        }
                    } else Modifier
                    Box(pageModifier.fillMaxSize()) {
                        ReaderPageCanvasV11(
                            title = displayTitle,
                            body = pages[safe],
                            firstParagraphStartsAtBoundary = starts.getOrElse(safe) { true },
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
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp).size(18.dp),
                tint = palette.accent.copy(alpha = .76f),
            )
        }

        AnimatedVisibility(
            visible = panelVisible && overlay == ReaderOverlayV11.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(90)),
        ) {
            ReaderControlSheetV11(
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
                onTheme = { panelVisible = false; overlay = ReaderOverlayV11.THEME },
                onFont = { panelVisible = false; overlay = ReaderOverlayV11.FONT },
                onType = { type -> quickType = type; panelVisible = false; overlay = ReaderOverlayV11.TYPE },
                onLocate = { tab = ReaderPanelTabV11.DIRECTORY },
                onVertical = { rememberAnchor(); pageModeKey = if (pageMode == ReaderPageModeV10.SCROLL) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.SCROLL.key },
                onSimulated = { rememberAnchor(); pageModeKey = if (pageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.COVER.key },
                onSearch = { panelVisible = false; overlay = ReaderOverlayV11.SEARCH },
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
            visible = overlay != ReaderOverlayV11.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(90)),
        ) {
            when (overlay) {
                ReaderOverlayV11.THEME -> ReaderThemeSheetV11(tokens, themeKey, onTheme = { rememberAnchor(); themeKey = it }, onBack = { overlay = ReaderOverlayV11.NONE; panelVisible = true })
                ReaderOverlayV11.FONT -> ReaderFontSheetV11(tokens, fontKey, onFont = { rememberAnchor(); fontKey = it }, onBack = { overlay = ReaderOverlayV11.NONE; panelVisible = true })
                ReaderOverlayV11.SEARCH -> ReaderSearchSheetV11(tokens, chapters, onOpen = { number -> overlay = ReaderOverlayV11.NONE; jumpChapter(chapters.firstOrNull { it.chapterNumber == number }) }, onBack = { overlay = ReaderOverlayV11.NONE; panelVisible = true })
                ReaderOverlayV11.TYPE -> ReaderTypeSheetV11(
                    tokens = tokens,
                    title = quickType,
                    fontSize = fontSize,
                    lineFactor = lineFactor,
                    paragraphSpacing = paragraphSpacing,
                    indent = firstLineIndent,
                    onFontSize = { rememberAnchor(); fontSize = it },
                    onLine = { rememberAnchor(); lineFactor = it },
                    onParagraph = { rememberAnchor(); paragraphSpacing = it },
                    onIndent = { rememberAnchor(); firstLineIndent = it },
                    onBack = { overlay = ReaderOverlayV11.NONE; panelVisible = true },
                )
                ReaderOverlayV11.NONE -> Unit
            }
        }
    }
}

/**
 * Fixed page chrome: header and footer own their rows; only the body receives weight(1f).
 * The footer can therefore never be pushed below the viewport by a text line.
 */
@Composable
private fun ReaderPageCanvasV11(
    title: String,
    body: String,
    firstParagraphStartsAtBoundary: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    palette: ReaderPaletteV11,
    showTimeBattery: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 20.dp, top = 16.dp, bottom = 12.dp),
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
            ReaderPageBodyV11(
                text = body,
                firstParagraphStartsAtBoundary = firstParagraphStartsAtBoundary,
                fontSize = fontSize,
                lineFactor = lineFactor,
                paragraphSpacing = paragraphSpacing,
                indent = firstLineIndent,
                family = family,
                color = palette.text,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showTimeBattery) {
                Text(readerTimeV11(), fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
                Spacer(Modifier.width(8.dp))
                Text("${readerBatteryV11()}%", fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
            }
            Spacer(Modifier.weight(1f))
            Text("$page/$pageCount", fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
        }
    }
}

@Composable
private fun ReaderPageBodyV11(
    text: String,
    firstParagraphStartsAtBoundary: Boolean,
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
            val shouldIndent = indent && (index > 0 || firstParagraphStartsAtBoundary)
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
private fun ReaderWholeBodyV11(
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
private fun ReaderControlSheetV11(
    tokens: LanghuanComponentTokensV3,
    tab: ReaderPanelTabV11,
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
    onTab: (ReaderPanelTabV11) -> Unit,
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
    LanghuanHeroSheetV3(tokens = tokens) {
        LanghuanHeroTabsV3(
            items = listOf("详情", "目录", "更多"),
            selectedIndex = tab.ordinal,
            onSelected = { onTab(ReaderPanelTabV11.entries[it]) },
            tokens = tokens,
        )
        Spacer(Modifier.height(12.dp))
        when (tab) {
            ReaderPanelTabV11.DETAILS -> Column(Modifier.heightIn(max = 400.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(book.title, color = tokens.foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("第 ${chapter.chapterNumber} 章 · ${chapter.title}", Modifier.padding(top = 4.dp), color = tokens.mutedForeground, fontSize = 12.sp)
                    }
                    IconButton(onClick = onBookmark) {
                        Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "书签", tint = if (bookmarked) tokens.primary else tokens.foreground)
                    }
                }
                LanghuanHairlineV3(tokens, 0.dp)
                LanghuanHeroRowV3("编辑本章", tokens, icon = Icons.Rounded.Edit, onClick = onEdit)
                LanghuanHeroRowV3("AI 创作", tokens, icon = Icons.Rounded.AutoStories, onClick = onWriting)
                LanghuanHeroRowV3("进入故事", tokens, icon = Icons.Rounded.TouchApp, onClick = onStory)
                LanghuanHeroRowV3("返回书架", tokens, trailing = "‹", onClick = onBack)
            }
            ReaderPanelTabV11.DIRECTORY -> LazyColumn(Modifier.heightIn(max = 430.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(chapters, key = { it.id }) { item ->
                    LanghuanHeroRowV3(
                        title = readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                        tokens = tokens,
                        trailing = if (item.id == chapter.id) "当前" else null,
                        onClick = { onChapter(item.chapterNumber) },
                    )
                    LanghuanHairlineV3(tokens)
                }
            }
            ReaderPanelTabV11.MORE -> {
                val actions = listOf(
                    ReaderActionV11("主题", Icons.Rounded.Palette, onClick = onTheme),
                    ReaderActionV11("字体", Icons.Rounded.TextFields, onClick = onFont),
                    ReaderActionV11("字号", Icons.Rounded.FormatSize, onClick = { onType("字号") }),
                    ReaderActionV11("行段", Icons.Rounded.FormatAlignJustify, onClick = { onType("行段") }),
                    ReaderActionV11("定位", Icons.Rounded.MyLocation, onClick = onLocate),
                    ReaderActionV11("上下翻页", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),
                    ReaderActionV11("仿真翻页", Icons.Rounded.Refresh, pageMode == ReaderPageModeV10.COVER, onSimulated),
                    ReaderActionV11("全文搜索", Icons.Rounded.Search, onClick = onSearch),
                    ReaderActionV11("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),
                    ReaderActionV11("屏幕常亮", Icons.Rounded.LightMode, keepScreen, onKeepScreen),
                    ReaderActionV11("时间电量", Icons.Rounded.BatteryFull, showTimeBattery, onTimeBattery),
                    ReaderActionV11("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),
                    ReaderActionV11("点击动画", Icons.Rounded.TouchApp, clickAnimation, onClickAnimation),
                    ReaderActionV11("下拉书签", Icons.Outlined.BookmarkBorder, pullBookmark, onPullBookmark),
                    ReaderActionV11("全屏下一页", Icons.Rounded.Smartphone, fullNext, onFullNext),
                    ReaderActionV11("背景图遮罩", Icons.Rounded.Image, backgroundMask, onBackgroundMask),
                    ReaderActionV11("背景跟随", Icons.Rounded.Brightness6, backgroundFollow, onBackgroundFollow),
                    ReaderActionV11("状态栏", Icons.Rounded.Smartphone, statusBar, onStatusBar),
                    ReaderActionV11("导航栏", Icons.Rounded.FormatIndentIncrease, navigationBar, onNavigationBar),
                    ReaderActionV11("锁定竖屏", Icons.Rounded.Landscape, lockPortrait, onLockPortrait),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    gridItems(actions, key = { it.label }) { action ->
                        LanghuanHeroActionTileV3(
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
private fun ReaderThemeSheetV11(
    tokens: LanghuanComponentTokensV3,
    current: String,
    onTheme: (String) -> Unit,
    onBack: () -> Unit,
) {
    val themes = listOf("paper" to "纸白", "tea" to "茶纸", "green" to "青叶", "night" to "夜间")
    LanghuanHeroSheetV3(tokens, title = "阅读主题") {
        themes.forEach { (key, name) ->
            LanghuanHeroRowV3(name, tokens, trailing = if (current == key) "✓" else null, onClick = { onTheme(key) })
            LanghuanHairlineV3(tokens)
        }
        LanghuanHeroRowV3("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun ReaderFontSheetV11(
    tokens: LanghuanComponentTokensV3,
    current: String,
    onFont: (String) -> Unit,
    onBack: () -> Unit,
) {
    LanghuanHeroSheetV3(tokens, title = "字体") {
        listOf("sans" to "系统黑体", "serif" to "系统宋体").forEach { (key, name) ->
            LanghuanHeroRowV3(name, tokens, trailing = if (current == key) "✓" else null, onClick = { onFont(key) })
            LanghuanHairlineV3(tokens)
        }
        LanghuanHeroRowV3("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun ReaderTypeSheetV11(
    tokens: LanghuanComponentTokensV3,
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
    LanghuanHeroSheetV3(tokens, title = title) {
        if (title == "字号") {
            LanghuanHeroRowV3("减小字号", tokens, trailing = "${fontSize.roundToInt()}sp", onClick = { onFontSize((fontSize - 1f).coerceAtLeast(14f)) })
            LanghuanHeroRowV3("增大字号", tokens, trailing = "${fontSize.roundToInt()}sp", onClick = { onFontSize((fontSize + 1f).coerceAtMost(30f)) })
        } else {
            LanghuanHeroRowV3("减小行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor - .05f).coerceAtLeast(1.35f)) })
            LanghuanHeroRowV3("增大行距", tokens, trailing = String.format(Locale.US, "%.2f", lineFactor), onClick = { onLine((lineFactor + .05f).coerceAtMost(2.20f)) })
            LanghuanHeroRowV3("减小段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing - 1f).coerceAtLeast(0f)) })
            LanghuanHeroRowV3("增大段距", tokens, trailing = "${paragraphSpacing.roundToInt()}dp", onClick = { onParagraph((paragraphSpacing + 1f).coerceAtMost(20f)) })
            LanghuanHeroRowV3("首行缩进", tokens, trailing = if (indent) "开" else "关", onClick = { onIndent(!indent) })
        }
        LanghuanHeroRowV3("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun ReaderSearchSheetV11(
    tokens: LanghuanComponentTokensV3,
    chapters: List<ChapterDraft>,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, chapters) {
        val q = query.trim()
        if (q.isBlank()) emptyList() else chapters.filter {
            it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
        }.take(50)
    }
    LanghuanHeroSheetV3(tokens, title = "全文搜索") {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("搜索整本书") },
        )
        LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
            items(hits, key = { it.id }) { item ->
                LanghuanHeroRowV3(
                    title = readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                    subtitle = readerSearchPreviewV11(item.content, query),
                    tokens = tokens,
                    onClick = { onOpen(item.chapterNumber) },
                )
                LanghuanHairlineV3(tokens)
            }
        }
        LanghuanHeroRowV3("返回", tokens, onClick = onBack)
    }
}

private fun readerPaletteV11(key: String): ReaderPaletteV11 = when (key) {
    "paper" -> ReaderPaletteV11(Color(0xFFF7F3EA), Color(0xFF282622), Color(0xFF716D64), Color(0xFF476B9A))
    "green" -> ReaderPaletteV11(Color(0xFFDDE6D1), Color(0xFF283126), Color(0xFF65705F), Color(0xFF4A7652))
    "night" -> ReaderPaletteV11(Color(0xFF17191D), Color(0xFFD2D4D8), Color(0xFF858A91), Color(0xFF7EA8E8))
    else -> ReaderPaletteV11(Color(0xFFE9D9B9), Color(0xFF302B23), Color(0xFF817866), Color(0xFF4F73A5))
}

private fun readerPageForOffsetV11(offsets: List<Int>, offset: Int): Int {
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

private fun readerTimeV11(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun readerBatteryV11(): Int {
    val context = LocalContext.current
    val battery = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    return battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 0
}

private fun readerSearchPreviewV11(content: String, query: String): String {
    val q = query.trim()
    if (q.isBlank()) return ""
    val index = content.indexOf(q, ignoreCase = true)
    if (index < 0) return content.replace(Regex("\\s+"), " ").take(72)
    val start = (index - 28).coerceAtLeast(0)
    val end = (index + q.length + 44).coerceAtMost(content.length)
    return content.substring(start, end).replace(Regex("\\s+"), " ")
}
