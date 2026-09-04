package com.xiguli.langhuan.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class QingmoReaderPaletteV9(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color = Color(0xFF347DEC),
)

private enum class QingmoReaderTabV9 { DETAILS, DIRECTORY, MORE }

private data class QingmoMoreItemV9(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private data class QingmoSearchHitV9(
    val chapterNumber: Int,
    val chapterTitle: String,
    val preview: String,
)

@Composable
fun ReaderQingmoFunctionalV9(
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
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF347DEC))
                    Text("正在打开章节", Modifier.padding(top = 12.dp), fontSize = 12.sp, color = Color(0xFF85888E))
                }
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
            IconButton(onClick = { storyMode = false }, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.ArrowBack, "返回阅读")
            }
        }
        return
    }

    ReaderQingmoPageV9(
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
private fun ReaderQingmoPageV9(
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
    var tab by rememberSaveable(chapter.id) { mutableStateOf(QingmoReaderTabV9.DIRECTORY) }
    var themeGallery by rememberSaveable { mutableStateOf(false) }
    var fontPage by rememberSaveable { mutableStateOf(false) }
    var searchPage by rememberSaveable { mutableStateOf(false) }
    var quickType by rememberSaveable { mutableStateOf<String?>(null) }

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
    var customThemes by rememberSaveable {
        mutableStateOf(prefs.getStringSet("customThemes", emptySet())?.toList()?.sorted().orEmpty())
    }
    var bookmarked by rememberSaveable(chapter.id) {
        mutableStateOf(prefs.getStringSet("bookmarks", emptySet())?.contains(chapter.chapterNumber.toString()) == true)
    }

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val basePalette = qingmoPaletteV9(themeKey)
    val palette = remember(basePalette, backgroundFollow, chapter.chapterNumber) {
        if (!backgroundFollow || themeKey == "night") basePalette
        else {
            val target = if (chapter.chapterNumber % 2 == 0) Color.White else Color(0xFFB8C8AE)
            basePalette.copy(background = lerp(basePalette.background, target, if (chapter.chapterNumber % 2 == 0) .055f else .035f))
        }
    }
    val family = if (fontKey == "serif") FontFamily.Serif else FontFamily.SansSerif
    val displayTitle = remember(chapter.id, chapter.title) { readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber) }
    val readingText = remember(chapter.id, chapter.content) {
        readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content)).ifBlank { "这一章没有正文。" }
    }
    val measured = rememberReaderMeasuredPaginationV16(
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
    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val initialPage = remember(chapter.id, measured.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> qingmoPageForOffsetV9(offsets, saved.textOffset.coerceIn(0, readingText.length))
            else -> saved.pageIndex.coerceIn(0, pages.lastIndex)
        }
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size.coerceAtLeast(1) })
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    val layoutKey = "$pageModeKey|$fontKey|$themeKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${paragraphSpacing.roundToInt()}|$firstLineIndent|${measured.layoutToken}"
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
        if (current.contains(key)) current.remove(key) else current.add(key)
        prefs.edit().putStringSet("bookmarks", current).apply()
        bookmarked = current.contains(key)
    }

    fun jumpChapter(target: ChapterDraft?, atEnd: Boolean = false) {
        target ?: return
        if (crossingChapter) return
        persist()
        crossingChapter = true
        ReaderProgressStoreV11.moveTo(
            context,
            book.id,
            target.chapterNumber,
            0,
            0,
            pageMode.key,
            if (atEnd) 1f else 0f,
            if (atEnd) Int.MAX_VALUE else 0,
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
                val f = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length
                scrollState.scrollTo((scrollState.maxValue * f).roundToInt())
            }
        } else {
            pagerState.scrollToPage(qingmoPageForOffsetV9(offsets, targetOffset).coerceIn(0, pages.lastIndex))
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
        fontSize,
        lineFactor,
        paragraphSpacing,
        firstLineIndent,
        fontKey,
        themeKey,
        pageModeKey,
        volumeTurn,
        keepScreen,
        showTimeBattery,
        immersive,
        clickAnimation,
        pullBookmark,
        fullNext,
        backgroundMask,
        backgroundFollow,
        statusBar,
        navigationBar,
        lockPortrait,
        customThemes,
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
            .putStringSet("customThemes", customThemes.toSet())
            .apply()
    }

    DisposableEffect(chapter.id, layoutKey) {
        onDispose { persist() }
    }

    DisposableEffect(keepScreen) {
        if (keepScreen) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(lockPortrait) {
        if (lockPortrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            if (lockPortrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(activity, immersive, statusBar, navigationBar) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (controller != null) {
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            when {
                immersive -> controller.hide(WindowInsetsCompat.Type.systemBars())
                else -> {
                    if (statusBar) controller.show(WindowInsetsCompat.Type.statusBars()) else controller.hide(WindowInsetsCompat.Type.statusBars())
                    if (navigationBar) controller.show(WindowInsetsCompat.Type.navigationBars()) else controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!volumeTurn || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.VolumeUp -> {
                        previousPage()
                        true
                    }
                    Key.VolumeDown -> {
                        nextPage()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().pointerInput(chapter.id, pageModeKey, pagerState.settledPage, fullNext) {
                detectTapGestures(
                    onDoubleTap = { panelVisible = true },
                    onLongPress = { panelVisible = true },
                    onTap = { offset ->
                        if (panelVisible) {
                            panelVisible = false
                        } else if (pageMode == ReaderPageModeV10.SCROLL) {
                            panelVisible = true
                        } else if (fullNext) {
                            if (offset.x < size.width * .18f) previousPage() else nextPage()
                        } else {
                            when {
                                offset.x < size.width * .28f -> previousPage()
                                offset.x > size.width * .72f -> nextPage()
                                else -> panelVisible = true
                            }
                        }
                    },
                )
            },
        ) {
            if (pageMode == ReaderPageModeV10.SCROLL) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 22.dp)) {
                    QingmoReaderHeaderV9(displayTitle, palette)
                    QingmoReaderParagraphsV9(readingText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text)
                    Spacer(Modifier.height(70.dp))
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    val safe = page.coerceIn(0, pages.lastIndex)
                    val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val coverModifier = if (pageMode == ReaderPageModeV10.COVER) {
                        Modifier.graphicsLayer {
                            val distance = rawOffset.absoluteValue.coerceIn(0f, 1f)
                            rotationY = rawOffset * -7f
                            scaleX = 1f - distance * .025f
                            scaleY = 1f - distance * .025f
                            alpha = 1f - distance * .08f
                            translationX = rawOffset * size.width * .055f
                        }
                    } else Modifier
                    Box(coverModifier.fillMaxSize()) {
                        QingmoReaderPageContentV9(
                            title = displayTitle,
                            body = pages[safe],
                            firstPage = safe == 0,
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
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp),
                shape = CircleShape,
                color = palette.accent.copy(alpha = .12f),
            ) {
                Icon(Icons.Outlined.BookmarkBorder, "已加入书签", Modifier.padding(7.dp).size(16.dp), tint = palette.accent)
            }
        }

        if (pullBookmark) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(width = 76.dp, height = 90.dp)
                    .pointerInput(chapter.id, pullBookmark) {
                        var dragY = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount -> if (amount > 0f) dragY += amount },
                            onDragEnd = {
                                if (dragY >= 36.dp.toPx()) toggleBookmark()
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        )
                    },
            )
        }

        AnimatedVisibility(
            visible = panelVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(100)),
        ) {
            QingmoReaderPanelV9(
                book = book,
                chapters = chapters,
                chapter = chapter,
                tab = tab,
                palette = palette,
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
                onSelectChapter = { number -> jumpChapter(chapters.firstOrNull { it.chapterNumber == number }) },
                onTheme = { panelVisible = false; themeGallery = true },
                onFont = { panelVisible = false; fontPage = true },
                onFontSize = { panelVisible = false; quickType = "字号" },
                onLine = { panelVisible = false; quickType = "行段" },
                onLocate = { tab = QingmoReaderTabV9.DIRECTORY },
                onVertical = {
                    rememberAnchor()
                    pageModeKey = if (pageMode == ReaderPageModeV10.SCROLL) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.SCROLL.key
                },
                onSimulated = {
                    rememberAnchor()
                    pageModeKey = if (pageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.COVER.key
                },
                onSearch = { panelVisible = false; searchPage = true },
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
                onBookmark = ::toggleBookmark,
                onDarkToggle = {
                    rememberAnchor()
                    themeKey = if (themeKey == "night") "tea" else "night"
                },
                onEdit = onEdit,
                onWriting = onWriting,
                onStory = onStory,
            )
        }

        AnimatedVisibility(
            visible = themeGallery,
            enter = slideInHorizontally(tween(190, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(170, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(100)),
        ) {
            QingmoThemeGalleryV9(
                current = themeKey,
                customThemes = customThemes,
                onTheme = {
                    rememberAnchor()
                    themeKey = it
                },
                onAddTheme = { key ->
                    if (key !in customThemes) customThemes = (customThemes + key).distinct()
                    themeKey = key
                },
                onBack = { themeGallery = false },
            )
        }

        AnimatedVisibility(
            visible = fontPage,
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(100)),
        ) {
            QingmoFontPageV9(fontKey, onFont = { rememberAnchor(); fontKey = it }, onBack = { fontPage = false })
        }

        AnimatedVisibility(
            visible = searchPage,
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(100)),
        ) {
            QingmoSearchV9(
                chapters = chapters,
                onOpenChapter = { number ->
                    searchPage = false
                    jumpChapter(chapters.firstOrNull { it.chapterNumber == number })
                },
                onBack = { searchPage = false },
            )
        }

        quickType?.let { type ->
            QingmoQuickAdjustV9(
                type = type,
                fontSize = fontSize,
                lineFactor = lineFactor,
                paragraphSpacing = paragraphSpacing,
                firstLineIndent = firstLineIndent,
                onFontSize = { rememberAnchor(); fontSize = it },
                onLine = { rememberAnchor(); lineFactor = it },
                onParagraph = { rememberAnchor(); paragraphSpacing = it },
                onIndent = { rememberAnchor(); firstLineIndent = it },
                onDismiss = { quickType = null },
            )
        }
    }
}

@Composable
private fun QingmoReaderHeaderV9(title: String, palette: QingmoReaderPaletteV9) {
    Text(title, fontSize = 12.sp, color = palette.secondary.copy(alpha = .55f), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun QingmoReaderPageContentV9(
    title: String,
    body: String,
    firstPage: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    palette: QingmoReaderPaletteV9,
    showTimeBattery: Boolean,
) {
    Column(Modifier.fillMaxSize().background(palette.background).padding(horizontal = 20.dp, vertical = 18.dp)) {
        if (firstPage) QingmoReaderHeaderV9(title, palette) else {
            Text(title, fontSize = 10.sp, color = palette.secondary.copy(alpha = .42f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(13.dp))
        }
        QingmoReaderParagraphsV9(body, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showTimeBattery) {
                val battery = qingmoBatteryV9()
                Text(qingmoTimeV9(), fontSize = 9.sp, color = palette.secondary.copy(alpha = .48f))
                Spacer(Modifier.width(8.dp))
                Text("$battery%", fontSize = 9.sp, color = palette.secondary.copy(alpha = .48f))
            }
            Spacer(Modifier.weight(1f))
            Text("$page/$pageCount", fontSize = 9.sp, color = palette.secondary.copy(alpha = .48f))
        }
    }
}

@Composable
private fun QingmoReaderParagraphsV9(
    text: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    indent: Boolean,
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
                fontWeight = FontWeight.Normal,
                color = color,
                textIndent = TextIndent(firstLine = if (indent) (fontSize * 2f).sp else 0.sp),
            ),
        )
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}

@Composable
private fun QingmoReaderPanelV9(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapter: ChapterDraft,
    tab: QingmoReaderTabV9,
    palette: QingmoReaderPaletteV9,
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
    onTab: (QingmoReaderTabV9) -> Unit,
    onBack: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onTheme: () -> Unit,
    onFont: () -> Unit,
    onFontSize: () -> Unit,
    onLine: () -> Unit,
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
    onBookmark: () -> Unit,
    onDarkToggle: () -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(180, easing = FastOutSlowInEasing)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(34.dp).height(4.dp).background(Color(0xFFD7D8DA), CircleShape))
            }
            when (tab) {
                QingmoReaderTabV9.DETAILS -> QingmoDetailsV9(book, chapter, bookmarked, onBack, onBookmark, onDarkToggle, onEdit, onWriting, onStory)
                QingmoReaderTabV9.DIRECTORY -> QingmoDirectoryV9(book, chapters, chapter.chapterNumber, palette, bookmarked, onBack, onBookmark, onDarkToggle, onSelectChapter)
                QingmoReaderTabV9.MORE -> {
                    val more = listOf(
                        QingmoMoreItemV9("主题", Icons.Rounded.Palette, onClick = onTheme),
                        QingmoMoreItemV9("字体", Icons.Rounded.TextFields, onClick = onFont),
                        QingmoMoreItemV9("字号", Icons.Rounded.FormatSize, onClick = onFontSize),
                        QingmoMoreItemV9("行段", Icons.Rounded.FormatAlignJustify, onClick = onLine),
                        QingmoMoreItemV9("定位", Icons.Rounded.MyLocation, onClick = onLocate),
                        QingmoMoreItemV9("上下翻页", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),
                        QingmoMoreItemV9("仿真翻页", Icons.Rounded.AutoStories, pageMode == ReaderPageModeV10.COVER, onSimulated),
                        QingmoMoreItemV9("全文搜索", Icons.Rounded.Search, onClick = onSearch),
                        QingmoMoreItemV9("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),
                        QingmoMoreItemV9("屏幕常亮", Icons.Rounded.Smartphone, keepScreen, onKeepScreen),
                        QingmoMoreItemV9("时间电量", Icons.Rounded.Timer, showTimeBattery, onTimeBattery),
                        QingmoMoreItemV9("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),
                        QingmoMoreItemV9("点击动画", Icons.Rounded.TouchApp, clickAnimation, onClickAnimation),
                        QingmoMoreItemV9("下拉书签", Icons.Outlined.BookmarkBorder, pullBookmark, onPullBookmark),
                        QingmoMoreItemV9("全屏下一页", Icons.Rounded.NavigateNext, fullNext, onFullNext),
                        QingmoMoreItemV9("背景图遮罩", Icons.Rounded.Image, backgroundMask, onBackgroundMask),
                        QingmoMoreItemV9("背景跟随", Icons.Rounded.Landscape, backgroundFollow, onBackgroundFollow),
                        QingmoMoreItemV9("状态栏", Icons.Rounded.Smartphone, statusBar, onStatusBar),
                        QingmoMoreItemV9("导航栏", Icons.Rounded.Smartphone, navigationBar, onNavigationBar),
                        QingmoMoreItemV9("锁定竖屏", Icons.Rounded.Refresh, lockPortrait, onLockPortrait),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height(500.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        gridItems(more, key = { it.label }) { item -> QingmoMoreButtonV9(item, palette) }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFEEEFF1))
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                QingmoTabV9("详情", tab == QingmoReaderTabV9.DETAILS, Modifier.weight(1f)) { onTab(QingmoReaderTabV9.DETAILS) }
                QingmoTabV9("目录", tab == QingmoReaderTabV9.DIRECTORY, Modifier.weight(1f)) { onTab(QingmoReaderTabV9.DIRECTORY) }
                QingmoTabV9("更多", tab == QingmoReaderTabV9.MORE, Modifier.weight(1f)) { onTab(QingmoReaderTabV9.MORE) }
            }
        }
    }
}

@Composable
private fun QingmoDetailsV9(
    book: ReaderBookUi,
    chapter: ChapterDraft,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onDarkToggle: () -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowLeft, "返回") }
            Text(book.title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E2024))
            IconButton(onClick = onBookmark) {
                Icon(Icons.Outlined.BookmarkBorder, if (bookmarked) "取消书签" else "加入书签", Modifier.size(20.dp), tint = if (bookmarked) Color(0xFF347DEC) else Color(0xFF2F3237))
            }
            IconButton(onClick = onDarkToggle) {
                Icon(Icons.Outlined.DarkMode, "切换夜间主题", Modifier.size(20.dp), tint = Color(0xFF2F3237))
            }
        }
        Text("${book.genre.ifBlank { "作品" }} · 第 ${chapter.chapterNumber} 章", fontSize = 12.sp, color = Color(0xFF8D9096))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QingmoSmallActionV9("编辑本章", onEdit)
            QingmoSmallActionV9("AI 创作", onWriting)
            QingmoSmallActionV9("进入故事", onStory)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun QingmoDirectoryV9(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    current: Int,
    palette: QingmoReaderPaletteV9,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onDarkToggle: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowLeft, "返回") }
            Text(book.title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202226))
            IconButton(onClick = onBookmark) {
                Icon(Icons.Outlined.BookmarkBorder, if (bookmarked) "取消书签" else "加入书签", Modifier.size(20.dp), tint = if (bookmarked) palette.accent else Color(0xFF35383D))
            }
            IconButton(onClick = onDarkToggle) {
                Icon(Icons.Outlined.DarkMode, "切换夜间主题", Modifier.size(20.dp), tint = Color(0xFF35383D))
            }
        }
        Text(book.genre.ifBlank { "作品" }, Modifier.padding(horizontal = 22.dp, vertical = 7.dp), fontSize = 12.sp, color = Color(0xFF44474D))
        LazyColumn(Modifier.fillMaxWidth().height(180.dp), contentPadding = PaddingValues(horizontal = 42.dp)) {
            items(chapters, key = { it.id }) { item ->
                Text(
                    readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                    Modifier.fillMaxWidth().clickable { onSelect(item.chapterNumber) }.padding(vertical = 9.dp),
                    fontSize = 13.sp,
                    color = if (item.chapterNumber == current) palette.accent else Color(0xFF292C31),
                    fontWeight = if (item.chapterNumber == current) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QingmoMoreButtonV9(item: QingmoMoreItemV9, palette: QingmoReaderPaletteV9) {
    Column(Modifier.fillMaxWidth().clickable(onClick = item.onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = if (item.selected) Color(0xFFEEF4FF) else Color(0xFFF3F4F6)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, null, Modifier.size(23.dp), tint = if (item.selected) palette.accent else Color(0xFF35383E))
            }
        }
        Text(item.label, Modifier.padding(top = 6.dp), fontSize = 10.sp, color = Color(0xFFAAADB2), maxLines = 1)
    }
}

@Composable
private fun QingmoTabV9(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color(0xFF347DEC) else Color(0xFFB3B5B9))
    }
}

@Composable
private fun QingmoSmallActionV9(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F5F6)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 11.sp, color = Color(0xFF55585E))
    }
}

@Composable
private fun QingmoThemeGalleryV9(
    current: String,
    customThemes: List<String>,
    onTheme: (String) -> Unit,
    onAddTheme: (String) -> Unit,
    onBack: () -> Unit,
) {
    val builtIn = listOf("tea", "mint", "paper", "mist", "green", "cream", "blue", "sage", "rose", "white", "sand", "night")
    val allThemes = builtIn + customThemes
    var nightTab by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("日间主题", Modifier.clickable { nightTab = false }.padding(horizontal = 10.dp, vertical = 15.dp), fontSize = 14.sp, fontWeight = if (!nightTab) FontWeight.SemiBold else FontWeight.Normal, color = if (!nightTab) Color(0xFF202226) else Color(0xFF999CA2))
                Text("夜间主题", Modifier.clickable { nightTab = true }.padding(horizontal = 10.dp, vertical = 15.dp), fontSize = 14.sp, fontWeight = if (nightTab) FontWeight.SemiBold else FontWeight.Normal, color = if (nightTab) Color(0xFF202226) else Color(0xFF999CA2))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(Icons.Rounded.Search, "搜索主题") }
                IconButton(onClick = { addOpen = true }) { Icon(Icons.Rounded.Add, "新增主题") }
            }
            AnimatedVisibility(searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                    placeholder = { Text("搜索主题") },
                    singleLine = true,
                )
            }
            val visibleThemes = remember(allThemes, nightTab, query) {
                allThemes.filter { key ->
                    val nightMatch = if (nightTab) key == "night" || key.startsWith("custom-night:") else key != "night" && !key.startsWith("custom-night:")
                    nightMatch && (query.isBlank() || qingmoThemeNameV9(key).contains(query, true))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(visibleThemes, key = { it }) { key ->
                    val palette = qingmoPaletteV9(key)
                    val selected = current == key
                    Surface(onClick = { onTheme(key) }, shape = RoundedCornerShape(2.dp), color = palette.background, modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        Box(Modifier.fillMaxSize().padding(10.dp)) {
                            Text(qingmoThemeNameV9(key), fontSize = 13.sp, color = palette.text, fontWeight = FontWeight.Medium)
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.align(Alignment.TopEnd).size(20.dp), tint = palette.accent)
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) Color(0xFF9B9DA2) else Color(0xFF347DEC),
                            ) {
                                Text(if (selected) "已使用" else "应用", Modifier.padding(horizontal = 15.dp, vertical = 5.dp), color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text("新增主题") },
            text = {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it.take(20) },
                    label = { Text("主题名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val key = (if (nightTab) "custom-night:" else "custom:") + customName.trim()
                        onAddTheme(key)
                        customName = ""
                        addOpen = false
                    },
                    enabled = customName.isNotBlank(),
                ) { Text("创建并应用") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun QingmoFontPageV9(current: String, onFont: (String) -> Unit, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("字体", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
            listOf("sans" to "系统黑体", "serif" to "阅读衬线").forEach { (key, name) ->
                Row(Modifier.fillMaxWidth().clickable { onFont(key) }.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), fontSize = 15.sp)
                    if (current == key) Icon(Icons.Rounded.Check, null, tint = Color(0xFF347DEC))
                }
                HorizontalDivider(color = Color(0xFFF0F1F2), modifier = Modifier.padding(start = 24.dp))
            }
        }
    }
}

@Composable
private fun QingmoSearchV9(chapters: List<ChapterDraft>, onOpenChapter: (Int) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(chapters, query) {
        if (query.isBlank()) emptyList()
        else buildList {
            chapters.forEach { chapter ->
                val clean = readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
                val index = clean.indexOf(query, ignoreCase = true)
                if (chapter.title.contains(query, true) || index >= 0) {
                    val start = (index - 35).coerceAtLeast(0)
                    val end = if (index >= 0) (index + query.length + 70).coerceAtMost(clean.length) else clean.length.coerceAtMost(100)
                    add(
                        QingmoSearchHitV9(
                            chapterNumber = chapter.chapterNumber,
                            chapterTitle = readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
                            preview = if (clean.isBlank()) "无正文" else clean.substring(start, end).replace("\n", " "),
                        ),
                    )
                }
            }
        }.take(80)
    }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                OutlinedTextField(query, { query = it }, Modifier.weight(1f).padding(end = 12.dp), placeholder = { Text("全文搜索") }, singleLine = true)
            }
            if (query.isNotBlank()) {
                Text("找到 ${results.size} 个章节", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 11.sp, color = Color(0xFF96999E))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                items(results, key = { it.chapterNumber }) { hit ->
                    Surface(onClick = { onOpenChapter(hit.chapterNumber) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
                            Text(hit.chapterTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF25282D))
                            Text(hit.preview, Modifier.padding(top = 4.dp), fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFF7D8086), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F1F2))
                }
            }
        }
    }
}

@Composable
private fun QingmoQuickAdjustV9(
    type: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    firstLineIndent: Boolean,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onParagraph: (Float) -> Unit,
    onIndent: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(type, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
            }
            if (type == "字号") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onFontSize((fontSize - 1f).coerceAtLeast(14f)) }) { Text("A−") }
                    Text("${fontSize.roundToInt()}", fontSize = 18.sp)
                    TextButton(onClick = { onFontSize((fontSize + 1f).coerceAtMost(30f)) }) { Text("A+") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(1.55f, 1.75f, 1.95f).forEach { value ->
                        TextButton(onClick = { onLine(value) }) { Text(if (value < 1.7f) "紧" else if (value < 1.9f) "中" else "松") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0f, 3f, 7f).forEach { value ->
                        TextButton(onClick = { onParagraph(value) }) { Text(if (value == 0f) "段紧" else if (value < 5f) "段中" else "段松") }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("首行缩进", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { onIndent(!firstLineIndent) }) { Text(if (firstLineIndent) "开启" else "关闭") }
                }
            }
        }
    }
}

private fun qingmoPageForOffsetV9(offsets: List<Int>, offset: Int): Int {
    if (offsets.isEmpty()) return 0
    var result = 0
    offsets.forEachIndexed { index, start -> if (start <= offset) result = index }
    return result.coerceIn(0, offsets.lastIndex)
}

private fun qingmoPaletteV9(key: String): QingmoReaderPaletteV9 {
    if (key.startsWith("custom-night:")) {
        val colors = listOf(Color(0xFF15181B), Color(0xFF1C171A), Color(0xFF171B16), Color(0xFF1B1814))
        val bg = colors[(key.hashCode() and Int.MAX_VALUE) % colors.size]
        return QingmoReaderPaletteV9(bg, Color(0xFFE6E3DE), Color(0xFF9D9993))
    }
    if (key.startsWith("custom:")) {
        val colors = listOf(Color(0xFFF1E2E2), Color(0xFFE4ECDD), Color(0xFFE3E8F1), Color(0xFFF0E7D8), Color(0xFFE8E1F0))
        val bg = colors[(key.hashCode() and Int.MAX_VALUE) % colors.size]
        return QingmoReaderPaletteV9(bg, Color(0xFF2F3033), Color(0xFF777A80))
    }
    return when (key) {
        "night" -> QingmoReaderPaletteV9(Color(0xFF171719), Color(0xFFE4E1DC), Color(0xFF9C9994))
        "mint" -> QingmoReaderPaletteV9(Color(0xFFDCE6D6), Color(0xFF2D332C), Color(0xFF6C756A))
        "paper" -> QingmoReaderPaletteV9(Color(0xFFF1ECE2), Color(0xFF302D29), Color(0xFF7B756D))
        "mist" -> QingmoReaderPaletteV9(Color(0xFFE6EAF1), Color(0xFF2B2F36), Color(0xFF707680))
        "green" -> QingmoReaderPaletteV9(Color(0xFFD5DFD0), Color(0xFF2C332B), Color(0xFF6B7568))
        "cream" -> QingmoReaderPaletteV9(Color(0xFFE9DEC9), Color(0xFF342E26), Color(0xFF796F61))
        "blue" -> QingmoReaderPaletteV9(Color(0xFFDCE3EE), Color(0xFF29303A), Color(0xFF6D7580))
        "sage" -> QingmoReaderPaletteV9(Color(0xFFCED9CB), Color(0xFF293229), Color(0xFF657063))
        "rose" -> QingmoReaderPaletteV9(Color(0xFFE7D7D3), Color(0xFF352D2C), Color(0xFF7B6C69))
        "white" -> QingmoReaderPaletteV9(Color(0xFFF8F8F8), Color(0xFF27292D), Color(0xFF777A80))
        "sand" -> QingmoReaderPaletteV9(Color(0xFFE3D6BC), Color(0xFF352F26), Color(0xFF766C5E))
        else -> QingmoReaderPaletteV9(Color(0xFFE6E1C9), Color(0xFF2E2B2B), Color(0xFF8A8575))
    }
}

private fun qingmoThemeNameV9(key: String): String = when {
    key.startsWith("custom-night:") -> key.removePrefix("custom-night:")
    key.startsWith("custom:") -> key.removePrefix("custom:")
    else -> when (key) {
        "tea" -> "番茄"
        "mint" -> "花开"
        "paper" -> "书卷"
        "mist" -> "雾白"
        "green" -> "护眼"
        "cream" -> "羊皮纸"
        "blue" -> "群山"
        "sage" -> "生机盎然"
        "rose" -> "浅墨"
        "white" -> "白灰侠"
        "sand" -> "仿真阅读书"
        "night" -> "夜间"
        else -> key
    }
}

@Composable
private fun qingmoTimeV9(): String {
    var value by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        }
    }
    return value
}

@Composable
private fun qingmoBatteryV9(): Int {
    val context = LocalContext.current
    return remember {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.coerceIn(0, 100) ?: 0
    }
}
