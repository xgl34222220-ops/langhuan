@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xiguli.langhuan.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.compose.material.icons.rounded.Tune
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

private data class HeroReaderPaletteV13(
    val page: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
)

private data class HeroReaderPresetV13(
    val key: String,
    val name: String,
    val subtitle: String,
    val theme: String,
    val fontSize: Float,
    val lineFactor: Float,
    val paragraphSpacing: Float,
    val sidePadding: Float,
    val indent: Boolean,
    val fontKey: String,
)

private val HERO_READER_PRESETS_V13 = listOf(
    HeroReaderPresetV13("qingmo", "清墨", "均衡留白 · 温润纸色", "tea", 18f, 1.75f, 3f, 20f, true, "sans"),
    HeroReaderPresetV13("tomato", "番茄小说风格", "稍大字号 · 紧凑行距 · 暖色背景", "tea", 19f, 1.68f, 2f, 19f, true, "sans"),
    HeroReaderPresetV13("weread", "微信读书风格", "宽页边距 · 舒展行距 · 轻纸白", "paper", 17.5f, 1.80f, 4f, 24f, true, "sans"),
    HeroReaderPresetV13("qidian", "起点阅读风格", "正文密度适中 · 页边距偏窄", "paper", 18f, 1.72f, 3f, 18f, true, "sans"),
    HeroReaderPresetV13("ireader", "掌阅风格", "宋体阅读 · 行距更舒展", "tea", 18f, 1.82f, 4f, 22f, true, "serif"),
    HeroReaderPresetV13("compact", "紧凑阅读", "一屏更多文字", "paper", 17f, 1.55f, 1f, 18f, true, "sans"),
    HeroReaderPresetV13("comfort", "舒适阅读", "大字号 · 大行距 · 宽留白", "tea", 19f, 1.88f, 5f, 25f, true, "serif"),
)

private enum class HeroReaderTabV13 { DETAILS, DIRECTORY, MORE }
private enum class HeroReaderOverlayV13 { NONE, PRESET, THEME, FONT, SEARCH, TYPE }

private data class HeroReaderActionV13(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ReaderQingmoHeroV13(
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
        BackHandler { storyMode = false }
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

    HeroReaderPageV13(
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
private fun HeroReaderPageV13(
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
    var tab by rememberSaveable(chapter.id) { mutableStateOf(HeroReaderTabV13.DIRECTORY) }
    var overlay by rememberSaveable { mutableStateOf(HeroReaderOverlayV13.NONE) }
    var typePage by rememberSaveable { mutableStateOf("字号") }

    var fontSize by remember(book.id) { mutableFloatStateOf(prefs.getFloat("font", 18f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(prefs.getFloat("line", 1.75f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(prefs.getFloat("paragraph", 3f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(prefs.getFloat("sidePadding", 20f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("fontKey", "sans") ?: "sans") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme", "tea") ?: "tea") }
    var presetKey by remember(book.id) { mutableStateOf(prefs.getString("preset", "qingmo") ?: "qingmo") }
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
    val rawPalette = heroReaderPaletteV13(themeKey)
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

    fun chapterTitle(item: ChapterDraft): String = readerDisplayChapterTitleV13(item.title, item.chapterNumber)
    fun chapterText(item: ChapterDraft): String = readerNormalizeBodyV14(
        readerBodyWithoutDuplicateHeadingV13(item.title, item.content),
    ).ifBlank { "这一章没有正文。" }

    val displayTitle = remember(chapter.id, chapter.title) { chapterTitle(chapter) }
    val readingText = remember(chapter.id, chapter.content) { chapterText(chapter) }
    val previousTitle = remember(previous?.id, previous?.title) { previous?.let(::chapterTitle).orEmpty() }
    val previousText = remember(previous?.id, previous?.content) { previous?.let(::chapterText).orEmpty() }
    val nextTitle = remember(next?.id, next?.title) { next?.let(::chapterTitle).orEmpty() }
    val nextText = remember(next?.id, next?.content) { next?.let(::chapterText).orEmpty() }

    val pagination = rememberReaderPaginationV18(
        text = readingText,
        title = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val previousPagination = rememberReaderPaginationV18(
        text = previousText.ifBlank { " " },
        title = previousTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )
    val nextPagination = rememberReaderPaginationV18(
        text = nextText.ifBlank { " " },
        title = nextTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = paragraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
    )

    val pages = pagination.pages.ifEmpty { listOf(readingText) }
    val offsets = pagination.offsets.ifEmpty { listOf(0) }
    val starts = pagination.pageStartsParagraph.ifEmpty { listOf(true) }
    val previousPages = previousPagination.pages.ifEmpty { listOf(previousText) }
    val previousStarts = previousPagination.pageStartsParagraph.ifEmpty { listOf(true) }
    val nextPages = nextPagination.pages.ifEmpty { listOf(nextText) }
    val nextStarts = nextPagination.pageStartsParagraph.ifEmpty { listOf(true) }

    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val initialPage = remember(chapter.id, pagination.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> heroReaderPageForOffsetV13(offsets, saved.textOffset.coerceIn(0, readingText.length))
            else -> saved.pageIndex.coerceIn(0, pages.lastIndex)
        }
    }
    val leadingBoundary = if (pageMode != ReaderPageModeV10.SCROLL && previous != null) 1 else 0
    val trailingBoundary = if (pageMode != ReaderPageModeV10.SCROLL && next != null) 1 else 0
    val pagerPageCount = (leadingBoundary + pages.size + trailingBoundary).coerceAtLeast(1)
    val initialPagerPage = (initialPage + leadingBoundary).coerceIn(0, pagerPageCount - 1)
    val pagerState = rememberPagerState(initialPage = initialPagerPage, pageCount = { pagerPageCount })
    val pagerFling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.15f,
    )
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${paragraphSpacing.roundToInt()}|${sidePadding.roundToInt()}|$firstLineIndent|${pagination.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf(layoutKey) }

    fun currentPage(): Int = (pagerState.settledPage - leadingBoundary).coerceIn(0, pages.lastIndex)

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
        if (pageMode == ReaderPageModeV10.SCROLL) return
        val target = pagerState.settledPage - 1
        if (target >= 0) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
            }
        } else jumpChapter(previous, true)
    }

    fun nextPage() {
        if (pageMode == ReaderPageModeV10.SCROLL) return
        val target = pagerState.settledPage + 1
        if (target < pagerPageCount) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
            }
        } else jumpChapter(next, false)
    }

    BackHandler {
        when {
            overlay != HeroReaderOverlayV13.NONE -> {
                overlay = HeroReaderOverlayV13.NONE
                panelVisible = true
            }
            panelVisible -> panelVisible = false
            else -> {
                persist()
                onBack()
            }
        }
    }

    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue, leadingBoundary) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue > 0) {
                val fraction = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length
                scrollState.scrollTo((scrollState.maxValue * fraction).roundToInt())
            }
        } else {
            val page = heroReaderPageForOffsetV13(offsets, targetOffset).coerceIn(0, pages.lastIndex)
            pagerState.scrollToPage((page + leadingBoundary).coerceIn(0, pagerPageCount - 1))
        }
        appliedLayoutKey = layoutKey
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey, leadingBoundary, trailingBoundary, pages.size) {
        if (pageMode != ReaderPageModeV10.SCROLL) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { settled ->
                when {
                    previous != null && leadingBoundary == 1 && settled == 0 -> jumpChapter(previous, true)
                    next != null && trailingBoundary == 1 && settled == leadingBoundary + pages.size -> jumpChapter(next, false)
                    else -> persist()
                }
            }
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
        fontSize, lineFactor, paragraphSpacing, sidePadding, firstLineIndent, fontKey, themeKey, presetKey, pageModeKey,
        volumeTurn, keepScreen, showTimeBattery, immersive, clickAnimation, pullBookmark, fullNext,
        backgroundMask, backgroundFollow, statusBar, navigationBar, lockPortrait,
    ) {
        prefs.edit()
            .putFloat("font", fontSize)
            .putFloat("line", lineFactor)
            .putFloat("paragraph", paragraphSpacing)
            .putFloat("sidePadding", sidePadding)
            .putBoolean("indent", firstLineIndent)
            .putString("fontKey", fontKey)
            .putString("theme", themeKey)
            .putString("preset", presetKey)
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
                        .padding(start = sidePadding.dp, end = sidePadding.dp, top = 16.dp, bottom = 16.dp),
                ) {
                    Text(
                        displayTitle,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = palette.secondary.copy(alpha = .60f),
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(14.dp))
                    HeroReaderWholeBodyV13(
                        readingText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text,
                    )
                    Spacer(Modifier.height(54.dp))
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    flingBehavior = pagerFling,
                    userScrollEnabled = !panelVisible && overlay == HeroReaderOverlayV13.NONE,
                ) { pagerPage ->
                    val rawOffset = (pagerState.currentPage - pagerPage) + pagerState.currentPageOffsetFraction
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
                        when {
                            previous != null && leadingBoundary == 1 && pagerPage == 0 -> {
                                val safe = previousPages.lastIndex.coerceAtLeast(0)
                                HeroReaderCanvasV13(
                                    title = previousTitle,
                                    body = previousPages[safe],
                                    pageStartsParagraph = previousStarts.getOrElse(safe) { true },
                                    page = safe + 1,
                                    pageCount = previousPages.size,
                                    fontSize = fontSize,
                                    lineFactor = lineFactor,
                                    paragraphSpacing = paragraphSpacing,
                                    sidePadding = sidePadding,
                                    firstLineIndent = firstLineIndent,
                                    family = family,
                                    palette = palette,
                                    showTimeBattery = showTimeBattery,
                                )
                            }
                            next != null && trailingBoundary == 1 && pagerPage == leadingBoundary + pages.size -> {
                                HeroReaderCanvasV13(
                                    title = nextTitle,
                                    body = nextPages.first(),
                                    pageStartsParagraph = nextStarts.firstOrNull() ?: true,
                                    page = 1,
                                    pageCount = nextPages.size,
                                    fontSize = fontSize,
                                    lineFactor = lineFactor,
                                    paragraphSpacing = paragraphSpacing,
                                    sidePadding = sidePadding,
                                    firstLineIndent = firstLineIndent,
                                    family = family,
                                    palette = palette,
                                    showTimeBattery = showTimeBattery,
                                )
                            }
                            else -> {
                                val safe = (pagerPage - leadingBoundary).coerceIn(0, pages.lastIndex)
                                HeroReaderCanvasV13(
                                    title = displayTitle,
                                    body = pages[safe],
                                    pageStartsParagraph = starts.getOrElse(safe) { true },
                                    page = safe + 1,
                                    pageCount = pages.size,
                                    fontSize = fontSize,
                                    lineFactor = lineFactor,
                                    paragraphSpacing = paragraphSpacing,
                                    sidePadding = sidePadding,
                                    firstLineIndent = firstLineIndent,
                                    family = family,
                                    palette = palette,
                                    showTimeBattery = showTimeBattery,
                                )
                            }
                        }
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
            visible = panelVisible && overlay == HeroReaderOverlayV13.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(90)),
        ) {
            HeroReaderControlsV13(
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
                onPreset = { panelVisible = false; overlay = HeroReaderOverlayV13.PRESET },
                onTheme = { panelVisible = false; overlay = HeroReaderOverlayV13.THEME },
                onFont = { panelVisible = false; overlay = HeroReaderOverlayV13.FONT },
                onType = { kind -> typePage = kind; panelVisible = false; overlay = HeroReaderOverlayV13.TYPE },
                onLocate = { tab = HeroReaderTabV13.DIRECTORY },
                onVertical = {
                    rememberAnchor()
                    pageModeKey = if (pageMode == ReaderPageModeV10.SCROLL) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.SCROLL.key
                },
                onSimulated = {
                    rememberAnchor()
                    pageModeKey = if (pageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.COVER.key
                },
                onSearch = { panelVisible = false; overlay = HeroReaderOverlayV13.SEARCH },
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
            visible = overlay != HeroReaderOverlayV13.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(90)),
        ) {
            when (overlay) {
                HeroReaderOverlayV13.PRESET -> HeroPresetSheetV13(
                    tokens = tokens,
                    current = presetKey,
                    onPreset = { preset ->
                        rememberAnchor()
                        presetKey = preset.key
                        themeKey = preset.theme
                        fontSize = preset.fontSize
                        lineFactor = preset.lineFactor
                        paragraphSpacing = preset.paragraphSpacing
                        sidePadding = preset.sidePadding
                        firstLineIndent = preset.indent
                        fontKey = preset.fontKey
                    },
                    onBack = { overlay = HeroReaderOverlayV13.NONE; panelVisible = true },
                )
                HeroReaderOverlayV13.THEME -> HeroThemeSheetV13(
                    tokens, themeKey,
                    onTheme = { rememberAnchor(); presetKey = "custom"; themeKey = it },
                    onBack = { overlay = HeroReaderOverlayV13.NONE; panelVisible = true },
                )
                HeroReaderOverlayV13.FONT -> HeroFontSheetV13(
                    tokens, fontKey,
                    onFont = { rememberAnchor(); presetKey = "custom"; fontKey = it },
                    onBack = { overlay = HeroReaderOverlayV13.NONE; panelVisible = true },
                )
                HeroReaderOverlayV13.SEARCH -> HeroSearchSheetV13(
                    tokens, chapters,
                    onOpen = { number ->
                        overlay = HeroReaderOverlayV13.NONE
                        jumpChapter(chapters.firstOrNull { it.chapterNumber == number })
                    },
                    onBack = { overlay = HeroReaderOverlayV13.NONE; panelVisible = true },
                )
                HeroReaderOverlayV13.TYPE -> HeroTypeSheetV13(
                    tokens = tokens,
                    title = typePage,
                    fontSize = fontSize,
                    lineFactor = lineFactor,
                    paragraphSpacing = paragraphSpacing,
                    sidePadding = sidePadding,
                    indent = firstLineIndent,
                    onFontSize = { rememberAnchor(); presetKey = "custom"; fontSize = it },
                    onLine = { rememberAnchor(); presetKey = "custom"; lineFactor = it },
                    onParagraph = { rememberAnchor(); presetKey = "custom"; paragraphSpacing = it },
                    onPadding = { rememberAnchor(); presetKey = "custom"; sidePadding = it },
                    onIndent = { rememberAnchor(); presetKey = "custom"; firstLineIndent = it },
                    onBack = { overlay = HeroReaderOverlayV13.NONE; panelVisible = true },
                )
                HeroReaderOverlayV13.NONE -> Unit
            }
        }
    }
}

@Composable
private fun HeroReaderCanvasV13(
    title: String,
    body: String,
    pageStartsParagraph: Boolean,
    page: Int,
    pageCount: Int,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    sidePadding: Float,
    firstLineIndent: Boolean,
    family: FontFamily,
    palette: HeroReaderPaletteV13,
    showTimeBattery: Boolean,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.page)
            .padding(start = sidePadding.dp, end = sidePadding.dp, top = 16.dp, bottom = 12.dp),
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
            HeroReaderPageBodyV13(
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
                Text(heroReaderTimeV13(), fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
                Spacer(Modifier.width(8.dp))
                Text("${heroReaderBatteryV13()}%", fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
            }
            Spacer(Modifier.weight(1f))
            Text("$page/$pageCount", fontSize = 9.sp, lineHeight = 12.sp, color = palette.secondary.copy(alpha = .44f))
        }
    }
}

@Composable
private fun HeroReaderPageBodyV13(
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
private fun HeroReaderWholeBodyV13(
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
private fun HeroReaderControlsV13(
    tokens: LanghuanTokensV4,
    tab: HeroReaderTabV13,
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
    onTab: (HeroReaderTabV13) -> Unit,
    onBack: () -> Unit,
    onChapter: (Int) -> Unit,
    onBookmark: () -> Unit,
    onPreset: () -> Unit,
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
            onSelected = { onTab(HeroReaderTabV13.entries[it]) },
            tokens = tokens,
        )
        Spacer(Modifier.height(12.dp))
        when (tab) {
            HeroReaderTabV13.DETAILS -> Column(Modifier.heightIn(max = 400.dp)) {
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
            HeroReaderTabV13.DIRECTORY -> LazyColumn(
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
            HeroReaderTabV13.MORE -> {
                val actions = listOf(
                    HeroReaderActionV13("排版预设", Icons.Rounded.Tune, onClick = onPreset),
                    HeroReaderActionV13("主题", Icons.Rounded.Palette, onClick = onTheme),
                    HeroReaderActionV13("字体", Icons.Rounded.TextFields, onClick = onFont),
                    HeroReaderActionV13("字号", Icons.Rounded.FormatSize, onClick = { onType("字号") }),
                    HeroReaderActionV13("行段", Icons.Rounded.FormatAlignJustify, onClick = { onType("行段") }),
                    HeroReaderActionV13("定位", Icons.Rounded.MyLocation, onClick = onLocate),
                    HeroReaderActionV13("上下翻页", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),
                    HeroReaderActionV13("仿真翻页", Icons.Rounded.Refresh, pageMode == ReaderPageModeV10.COVER, onSimulated),
                    HeroReaderActionV13("全文搜索", Icons.Rounded.Search, onClick = onSearch),
                    HeroReaderActionV13("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),
                    HeroReaderActionV13("屏幕常亮", Icons.Rounded.LightMode, keepScreen, onKeepScreen),
                    HeroReaderActionV13("时间电量", Icons.Rounded.BatteryFull, showTimeBattery, onTimeBattery),
                    HeroReaderActionV13("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),
                    HeroReaderActionV13("点击动画", Icons.Rounded.TouchApp, clickAnimation, onClickAnimation),
                    HeroReaderActionV13("下拉书签", Icons.Outlined.BookmarkBorder, pullBookmark, onPullBookmark),
                    HeroReaderActionV13("全屏下一页", Icons.Rounded.Smartphone, fullNext, onFullNext),
                    HeroReaderActionV13("背景图遮罩", Icons.Rounded.Image, backgroundMask, onBackgroundMask),
                    HeroReaderActionV13("背景跟随", Icons.Rounded.Brightness6, backgroundFollow, onBackgroundFollow),
                    HeroReaderActionV13("状态栏", Icons.Rounded.Smartphone, statusBar, onStatusBar),
                    HeroReaderActionV13("导航栏", Icons.Rounded.FormatIndentIncrease, navigationBar, onNavigationBar),
                    HeroReaderActionV13("锁定竖屏", Icons.Rounded.Landscape, lockPortrait, onLockPortrait),
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
private fun HeroPresetSheetV13(
    tokens: LanghuanTokensV4,
    current: String,
    onPreset: (HeroReaderPresetV13) -> Unit,
    onBack: () -> Unit,
) {
    LanghuanSheetV4(tokens, title = "排版预设") {
        Text(
            "一键套用主题、字号、行距、段距与页边距，之后仍可继续微调。",
            color = tokens.mutedForeground,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HERO_READER_PRESETS_V13.forEach { preset ->
            LanghuanRowV4(
                title = preset.name,
                subtitle = preset.subtitle,
                tokens = tokens,
                trailing = if (current == preset.key) "✓" else null,
                onClick = { onPreset(preset) },
            )
            LanghuanDividerV4(tokens)
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun HeroThemeSheetV13(
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
private fun HeroFontSheetV13(
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
private fun HeroTypeSheetV13(
    tokens: LanghuanTokensV4,
    title: String,
    fontSize: Float,
    lineFactor: Float,
    paragraphSpacing: Float,
    sidePadding: Float,
    indent: Boolean,
    onFontSize: (Float) -> Unit,
    onLine: (Float) -> Unit,
    onParagraph: (Float) -> Unit,
    onPadding: (Float) -> Unit,
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
            LanghuanRowV4("减小页边距", tokens, trailing = "${sidePadding.roundToInt()}dp", onClick = { onPadding((sidePadding - 2f).coerceAtLeast(14f)) })
            LanghuanRowV4("增大页边距", tokens, trailing = "${sidePadding.roundToInt()}dp", onClick = { onPadding((sidePadding + 2f).coerceAtMost(36f)) })
            LanghuanRowV4("首行缩进", tokens, trailing = if (indent) "开" else "关", onClick = { onIndent(!indent) })
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

@Composable
private fun HeroSearchSheetV13(
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
                    subtitle = heroReaderSearchPreviewV13(item.content, query),
                    tokens = tokens,
                    onClick = { onOpen(item.chapterNumber) },
                )
                LanghuanDividerV4(tokens)
            }
        }
        LanghuanRowV4("返回", tokens, onClick = onBack)
    }
}

private fun heroReaderPaletteV13(key: String): HeroReaderPaletteV13 = when (key) {
    "paper" -> HeroReaderPaletteV13(Color(0xFFF7F3EA), Color(0xFF282622), Color(0xFF716D64), Color(0xFF476B9A))
    "green" -> HeroReaderPaletteV13(Color(0xFFDDE6D1), Color(0xFF283126), Color(0xFF65705F), Color(0xFF4A7652))
    "night" -> HeroReaderPaletteV13(Color(0xFF17191D), Color(0xFFD2D4D8), Color(0xFF858A91), Color(0xFF7EA8E8))
    else -> HeroReaderPaletteV13(Color(0xFFE9D9B9), Color(0xFF302B23), Color(0xFF817866), Color(0xFF4F73A5))
}

private fun heroReaderPageForOffsetV13(offsets: List<Int>, offset: Int): Int {
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

private fun heroReaderTimeV13(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun heroReaderBatteryV13(): Int {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 0
}

private fun heroReaderSearchPreviewV13(content: String, query: String): String {
    val q = query.trim()
    if (q.isBlank()) return ""
    val index = content.indexOf(q, ignoreCase = true)
    if (index < 0) return content.replace(Regex("\\s+"), " ").take(72)
    val start = (index - 28).coerceAtLeast(0)
    val end = (index + q.length + 44).coerceAtMost(content.length)
    return content.substring(start, end).replace(Regex("\\s+"), " ")
}
