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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LanghuanActionTileV4
import com.xiguli.langhuan.ui.design.LanghuanAmbientBackdrop
import com.xiguli.langhuan.ui.design.LanghuanConstellationField
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
import kotlin.math.abs
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
    HeroReaderPresetV13("langhuan", "琅嬛星图", "轻雾星图 · 正文优先 · 低干扰", "langhuan", 17.5f, 1.48f, 0f, 16f, true, "sans"),
    HeroReaderPresetV13("qingmo", "清墨", "成熟网文密度 · 克制留白", "tea", 17.5f, 1.48f, 0f, 16f, true, "sans"),
    HeroReaderPresetV13("tomato", "番茄小说风格", "稍大字号 · 紧凑行距 · 暖色背景", "tea", 18.5f, 1.46f, 0f, 15f, true, "sans"),
    HeroReaderPresetV13("weread", "微信读书风格", "适中字号 · 轻纸白 · 稍宽页边距", "paper", 17.5f, 1.52f, 0f, 18f, true, "sans"),
    HeroReaderPresetV13("qidian", "起点阅读风格", "正文密度均衡 · 窄页边距", "paper", 18f, 1.48f, 0f, 16f, true, "sans"),
    HeroReaderPresetV13("ireader", "掌阅风格", "宋体阅读 · 适度舒展", "tea", 18f, 1.54f, 1f, 18f, true, "serif"),
    HeroReaderPresetV13("compact", "紧凑阅读", "一屏更多正文", "paper", 17f, 1.40f, 0f, 14f, true, "sans"),
    HeroReaderPresetV13("comfort", "舒适阅读", "舒展行距 · 清晰段落 · 暖纸", "paper", 20f, 1.65f, 8f, 22f, true, "serif"),
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
    interactionEnabled: Boolean = true,
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
        interactionEnabled = interactionEnabled,
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
    interactionEnabled: Boolean,
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
    var tab by rememberSaveable(chapter.id) { mutableStateOf(HeroReaderTabV13.MORE) }
    var overlay by rememberSaveable { mutableStateOf(HeroReaderOverlayV13.NONE) }
    var typePage by rememberSaveable { mutableStateOf("字号") }

    // Only migrate the old untouched default. User typography choices remain intact.
    val migrateDefault = remember(book.id) {
        !prefs.getBoolean("reader_comfort_v26", false) &&
            readerUsesLegacyDefaultV26(
                prefs.getString("preset", "qingmo") ?: "qingmo",
                prefs.getFloat("font", 18f), prefs.getFloat("line", 1.75f),
                prefs.getFloat("paragraph", 3f), prefs.getFloat("sidePadding", 20f),
            )
    }
    var fontSize by remember(book.id) { mutableFloatStateOf(if (migrateDefault) 20f else prefs.getFloat("font", 20f)) }
    var lineFactor by remember(book.id) { mutableFloatStateOf(if (migrateDefault) 1.65f else prefs.getFloat("line", 1.65f)) }
    var paragraphSpacing by remember(book.id) { mutableFloatStateOf(if (migrateDefault) 8f else prefs.getFloat("paragraph", 8f)) }
    var sidePadding by remember(book.id) { mutableFloatStateOf(if (migrateDefault) 22f else prefs.getFloat("sidePadding", 22f)) }
    var firstLineIndent by remember(book.id) { mutableStateOf(prefs.getBoolean("indent", true)) }
    var fontKey by remember(book.id) { mutableStateOf(prefs.getString("fontKey", "serif") ?: "serif") }
    var themeKey by remember(book.id) { mutableStateOf(prefs.getString("theme", "paper") ?: "paper") }
    var presetKey by remember(book.id) { mutableStateOf(if (migrateDefault) "comfort" else prefs.getString("preset", "comfort") ?: "comfort") }

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

    val requestedPageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val pageMode = if (requestedPageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE else requestedPageMode
    val rawPalette = heroReaderPaletteV13(themeKey)
    // A reading background must remain stable between chapters. The old chapter-parity tint
    // changed the page colour while reading and made the book feel visually inconsistent.
    val palette = rawPalette
    val spatialBackground = themeKey == "langhuan"
    val tokens = remember(palette) { langhuanTokensV4(palette.page, palette.text, palette.accent) }
    val family = if (fontKey == "serif") FontFamily.Serif else FontFamily.SansSerif

    fun chapterTitle(item: ChapterDraft): String = readerDisplayChapterTitleV13(item.title, item.chapterNumber)
    fun chapterText(item: ChapterDraft): String = readerNormalizeBodyV14(
        readerBodyWithoutDuplicateHeadingV13(item.title, item.content),
    ).ifBlank { "这一章没有正文。" }

    val displayTitle = remember(chapter.id, chapter.title) { chapterTitle(chapter) }
    val readingText = remember(chapter.id, chapter.content) { chapterText(chapter) }
    var measuredBodyViewport by remember(book.id) { mutableStateOf(IntSize.Zero) }

    // Page and scroll modes must honor the same typography controls. Previously paged mode
    // forced paragraph spacing to zero, making part of the type sheet look ineffective.
    val pagedParagraphSpacing = paragraphSpacing
    val pagination = rememberReaderPaginationV18(
        text = readingText,
        title = displayTitle,
        fontSize = fontSize,
        lineFactor = lineFactor,
        sidePadding = sidePadding,
        paragraphSpacing = pagedParagraphSpacing,
        firstLineIndent = firstLineIndent,
        family = family,
        viewportWidthPx = measuredBodyViewport.width,
        viewportHeightPx = measuredBodyViewport.height,
    )

    val pages = pagination.pages.ifEmpty { listOf(readingText) }
    val offsets = pagination.offsets.ifEmpty { listOf(0) }
    val starts = pagination.pageStartsParagraph.ifEmpty { listOf(true) }

    val saved = remember(chapter.id) { ReaderProgressStoreV11.load(context, book.id, chapter.chapterNumber) }
    val initialPage = remember(chapter.id, pagination.layoutToken, pageModeKey) {
        when {
            saved.chapterNumber != chapter.chapterNumber -> 0
            saved.textOffset > 0 -> heroReaderPageForOffsetV13(offsets, saved.textOffset.coerceIn(0, readingText.length))
            else -> saved.pageIndex.coerceIn(0, pages.lastIndex)
        }
    }
    val pagerPageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pagerPageCount - 1),
        pageCount = { pagerPageCount },
    )
    val pagerFling = PagerDefaults.flingBehavior(
        state = pagerState,
        snapPositionalThreshold = 0.32f,
    )
    val scrollState = rememberScrollState()
    var crossingChapter by remember(chapter.id) { mutableStateOf(false) }
    var anchorOffset by remember(chapter.id) { mutableIntStateOf(saved.textOffset.coerceIn(0, readingText.length)) }
    val layoutKey = "$pageModeKey|$fontKey|${fontSize.roundToInt()}|${(lineFactor * 100).roundToInt()}|${paragraphSpacing.roundToInt()}|${sidePadding.roundToInt()}|$firstLineIndent|${pagination.layoutToken}"
    var appliedLayoutKey by remember(chapter.id) { mutableStateOf("") }
    var positionReady by remember(chapter.id) { mutableStateOf(false) }
    val currentBodyViewport = remember { mutableStateOf(IntSize.Zero) }

    fun currentPage(): Int = pagerState.settledPage.coerceIn(0, pages.lastIndex)

    fun currentOffset(): Int = if (pageMode == ReaderPageModeV10.SCROLL) {
        val fraction = if (scrollState.maxValue <= 0) 0f else scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        (readingText.length * fraction).roundToInt().coerceIn(0, readingText.length)
    } else offsets.getOrElse(currentPage()) { 0 }.coerceIn(0, readingText.length)

    fun persist() {
        if (crossingChapter || !positionReady || appliedLayoutKey != layoutKey) return
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
        if (target.id == chapter.id) {
            panelVisible = false
            overlay = HeroReaderOverlayV13.NONE
            return
        }
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
        if (!interactionEnabled || !positionReady) return
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.value == 0) jumpChapter(previous, atEnd = true)
            else scope.launch { scrollState.animateScrollTo((scrollState.value - currentBodyViewport.value.height * .85f).roundToInt().coerceAtLeast(0)) }
            return
        }
        val page = currentPage()
        if (page > 0) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(page - 1) else pagerState.scrollToPage(page - 1)
            }
        } else jumpChapter(previous, atEnd = true)
    }

    fun nextPage() {
        if (!interactionEnabled || !positionReady) return
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.value >= scrollState.maxValue) jumpChapter(next)
            else scope.launch { scrollState.animateScrollTo((scrollState.value + currentBodyViewport.value.height * .85f).roundToInt().coerceAtMost(scrollState.maxValue)) }
            return
        }
        val page = currentPage()
        if (page < pages.lastIndex) {
            scope.launch {
                if (clickAnimation) pagerState.animateScrollToPage(page + 1) else pagerState.scrollToPage(page + 1)
            }
        } else jumpChapter(next, atEnd = false)
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

    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue, measuredBodyViewport) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        if (pageMode != ReaderPageModeV10.SCROLL && measuredBodyViewport == IntSize.Zero) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue == Int.MAX_VALUE) return@LaunchedEffect
            val y = if (!positionReady && saved.modeKey == pageMode.key && saved.textOffset != Int.MAX_VALUE) {
                saved.scrollY.coerceIn(0, scrollState.maxValue)
            } else {
                val fraction = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length
                (scrollState.maxValue * fraction).roundToInt()
            }
            scrollState.scrollTo(y)
        } else {
            val page = if (!positionReady && saved.chapterNumber == chapter.chapterNumber && saved.textOffset == 0) {
                saved.pageIndex.coerceIn(0, pages.lastIndex)
            } else heroReaderPageForOffsetV13(offsets, targetOffset).coerceIn(0, pages.lastIndex)
            pagerState.scrollToPage(page)
        }
        appliedLayoutKey = layoutKey
        positionReady = true
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey, pages.size, positionReady) {
        if (pageMode != ReaderPageModeV10.SCROLL) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect {
                if (positionReady && appliedLayoutKey == layoutKey) anchorOffset = currentOffset()
                persist()
            }
        }
    }

    // A pager must never rest between two pages. Some OEM gesture stacks can cancel the
    // pager's final settle animation, leaving two chapter pages permanently visible at once.
    // Observe the idle state and hard-snap to the nearest current page if an offset remains.
    LaunchedEffect(chapter.id, pagerState, pagerPageCount) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPageOffsetFraction }
            .collectLatest { (scrolling, offset) ->
                if (!scrolling && abs(offset) > 0.001f) {
                    pagerState.scrollToPage(pagerState.currentPage.coerceIn(0, pagerPageCount - 1))
                }
            }
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey, positionReady) {
        if (pageMode == ReaderPageModeV10.SCROLL) {
            snapshotFlow { scrollState.value }.distinctUntilChanged().collectLatest {
                delay(180)
                if (positionReady && appliedLayoutKey == layoutKey) anchorOffset = currentOffset()
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
            .putBoolean("reader_comfort_v26", true)
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

    val saveLatest by rememberUpdatedState { persist() }
    DisposableEffect(chapter.id) { onDispose { saveLatest() } }
    val turnPrevious by rememberUpdatedState { previousPage() }
    val turnNext by rememberUpdatedState { nextPage() }
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
    DisposableEffect(activity, immersive, themeKey) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val wasLightStatus = controller?.isAppearanceLightStatusBars
        val wasLightNavigation = controller?.isAppearanceLightNavigationBars
        if (controller != null) {
            controller.isAppearanceLightStatusBars = themeKey != "night"
            controller.isAppearanceLightNavigationBars = themeKey != "night"
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) controller.hide(WindowInsetsCompat.Type.systemBars())
            else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            wasLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
            wasLightNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }

    val edgeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
    val edgeSwipe = remember(chapter.id, pages.size, pageMode, previous?.id, next?.id, interactionEnabled) {
        object : NestedScrollConnection {
            var edgeDrag = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!interactionEnabled || pageMode == ReaderPageModeV10.SCROLL || source != NestedScrollSource.UserInput) return Offset.Zero
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

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.page)
            .windowInsetsPadding(WindowInsets.systemBars)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!interactionEnabled || !volumeTurn || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.VolumeUp -> { previousPage(); true }
                    Key.VolumeDown -> { nextPage(); true }
                    else -> false
                }
            },
    ) {
        if (spatialBackground) {
            LanghuanAmbientBackdrop(Modifier.fillMaxSize(), active = false)
            LanghuanConstellationField(Modifier.fillMaxSize(), active = false)
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(chapter.id, pageModeKey, panelVisible, interactionEnabled) {
                    if (!interactionEnabled) return@pointerInput
                    detectTapGestures(
                        onLongPress = { panelVisible = true },
                        onTap = {
                            if (panelVisible) panelVisible = false
                            else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                            // Paged-mode taps are handled by the pager page itself. Keeping the
                            // tap target below HorizontalPager made taps lose to the pager gesture
                            // detector on some devices.
                        },
                    )
                },
        ) {
            if (pageMode == ReaderPageModeV10.SCROLL) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { currentBodyViewport.value = it }
                        .verticalScroll(scrollState, enabled = interactionEnabled)
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
                    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { jumpChapter(previous, atEnd = true) }, enabled = previous != null) { Text("上一章", color = palette.accent) }
                        TextButton(onClick = { jumpChapter(next) }, enabled = next != null) { Text(if (next == null) "已读至末章" else "下一章", color = palette.accent) }
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().clipToBounds().nestedScroll(edgeSwipe),
                    beyondViewportPageCount = 0,
                    flingBehavior = pagerFling,
                    userScrollEnabled = interactionEnabled && !panelVisible && overlay == HeroReaderOverlayV13.NONE,
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

                    Box(
                        transition
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(chapter.id, pagerPage, panelVisible, interactionEnabled) {
                                if (!interactionEnabled) return@pointerInput
                                detectTapGestures(
                                                onLongPress = { panelVisible = true },
                                    onTap = { point ->
                                        if (panelVisible) panelVisible = false
                                        else when {
                                            point.x < size.width * .28f -> turnPrevious()
                                            point.x > size.width * .72f -> turnNext()
                                            else -> panelVisible = true
                                        }
                                    },
                                )
                            },
                    ) {
                        val safe = pagerPage.coerceIn(0, pages.lastIndex)
                        HeroReaderCanvasV13(
                            title = displayTitle,
                            body = pages[safe],
                            pageStartsParagraph = starts.getOrElse(safe) { true },
                            page = safe + 1,
                            pageCount = pages.size,
                            fontSize = fontSize,
                            lineFactor = lineFactor,
                            paragraphSpacing = pagedParagraphSpacing,
                            sidePadding = sidePadding,
                            firstLineIndent = firstLineIndent,
                            family = family,
                            palette = palette,
                            showTimeBattery = showTimeBattery,
                            spatialBackground = spatialBackground,
                            onBodyViewportChanged = { size ->
                                if (size.width > 0 && size.height > 0 && size != measuredBodyViewport) {
                                    measuredBodyViewport = size
                                }
                            },
                        )
                    }
                }
            }
        }

        if (bookmarked) {
            Icon(
                Icons.Outlined.Bookmark,
                "已加入书签",
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 11.dp).size(18.dp),
                tint = palette.accent.copy(alpha = .76f),
            )
        }


        if (panelVisible || overlay != HeroReaderOverlayV13.NONE) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .10f)).clickable {
                overlay = HeroReaderOverlayV13.NONE
                panelVisible = false
            })
        }
        AnimatedVisibility(
            visible = panelVisible && overlay == HeroReaderOverlayV13.NONE,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(tween(140)), exit = fadeOut(tween(100)),
        ) {
            Surface(color = tokens.surfaceRaised, shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { persist(); onBack() }) { Icon(Icons.Rounded.ArrowBack, "返回书架", tint = tokens.foreground) }
                    Text(book.title, Modifier.weight(1f), color = tokens.foreground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = ::toggleBookmark) { Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "书签", tint = tokens.primary) }
                    IconButton(onClick = { panelVisible = false }) { Icon(Icons.Rounded.Close, "收起菜单", tint = tokens.foreground) }
                }
            }
        }
        AnimatedVisibility(
            visible = panelVisible && overlay == HeroReaderOverlayV13.NONE,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(190, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(90)),
        ) {
            HeroReaderControlsV13(
                tokens = tokens,
                fontSize = fontSize,
                themeKey = themeKey,
                onFontSize = { rememberAnchor(); presetKey = "custom"; fontSize = it },
                onQuickTheme = { themeKey = it; presetKey = "custom" },
                progress = if (pageMode == ReaderPageModeV10.SCROLL) scrollState.value.toFloat() / scrollState.maxValue.coerceAtLeast(1) else currentPage().toFloat() / pages.lastIndex.coerceAtLeast(1),
                onProgress = { fraction -> scope.launch {
                    if (pageMode == ReaderPageModeV10.SCROLL) scrollState.scrollTo((fraction * scrollState.maxValue).roundToInt())
                    else pagerState.scrollToPage((fraction * pages.lastIndex).roundToInt())
                } },
                onPrevious = { jumpChapter(previous, atEnd = false) },
                onNext = { jumpChapter(next) },
                hasPrevious = previous != null,
                hasNext = next != null,
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
    spatialBackground: Boolean,
    onBodyViewportChanged: (IntSize) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(if (spatialBackground) Color.Transparent else palette.page)
            .padding(start = sidePadding.dp, end = sidePadding.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Text(
            title,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            color = palette.secondary.copy(alpha = .58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .onSizeChanged(onBodyViewportChanged),
        ) {
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
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showTimeBattery) {
                Text(heroReaderTimeV13(), fontSize = 8.sp, lineHeight = 10.sp, color = palette.secondary.copy(alpha = .44f))
                Spacer(Modifier.width(8.dp))
                Text("${heroReaderBatteryV13()}%", fontSize = 8.sp, lineHeight = 10.sp, color = palette.secondary.copy(alpha = .44f))
            }
            Spacer(Modifier.weight(1f))
            Text("$page/$pageCount", fontSize = 8.sp, lineHeight = 10.sp, color = palette.secondary.copy(alpha = .44f))
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
                paragraph.trim(),
                style = TextStyle(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineFactor).sp,
                    fontFamily = family,
                    fontWeight = FontWeight.Normal,
                    color = color,
                    textAlign = TextAlign.Start,
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
            paragraph.trim(),
            style = TextStyle(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineFactor).sp,
                fontFamily = family,
                color = color,
                textAlign = TextAlign.Start,
                textIndent = TextIndent(firstLine = if (indent) (fontSize * 2f).sp else 0.sp),
            ),
        )
        if (index < paragraphs.lastIndex) Spacer(Modifier.height(paragraphSpacing.dp))
    }
}

@Composable
private fun HeroReaderControlsV13(
    tokens: LanghuanTokensV4,
    fontSize: Float,
    themeKey: String,
    onFontSize: (Float) -> Unit,
    onQuickTheme: (String) -> Unit,
    progress: Float,
    onProgress: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
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
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * .62f).dp
    var directoryQuery by rememberSaveable { mutableStateOf("") }
    val visibleChapters = remember(chapters, directoryQuery) {
        chapters.filter { directoryQuery.isBlank() || readerDisplayChapterTitleV13(it.title, it.chapterNumber).contains(directoryQuery, true) }
    }
    val directoryState = rememberLazyListState(initialFirstVisibleItemIndex = chapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0))
    LaunchedEffect(tab, directoryQuery, chapter.id) {
        if (tab == HeroReaderTabV13.DIRECTORY) {
            val index = visibleChapters.indexOfFirst { it.id == chapter.id }
            if (index >= 0) directoryState.scrollToItem(index)
        }
    }
    LanghuanSheetV4(tokens = tokens, modifier = Modifier.heightIn(max = maxPanelHeight)) {
        LanghuanTabsV4(
            labels = listOf("详情", "目录", "设置"),
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
            HeroReaderTabV13.DIRECTORY -> Column {
                OutlinedTextField(
                    value = directoryQuery, onValueChange = { directoryQuery = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("搜索章节 · 共 ${chapters.size} 章") },
                    shape = RoundedCornerShape(16.dp),
                )
                LazyColumn(state = directoryState, modifier = Modifier.heightIn(max = maxPanelHeight - 140.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(visibleChapters, key = { it.id }) { item ->
                        Surface(color = if (item.id == chapter.id) tokens.primary.copy(alpha = .10f) else Color.Transparent, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().clickable { onChapter(item.chapterNumber) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(readerDisplayChapterTitleV13(item.title, item.chapterNumber), Modifier.weight(1f), fontSize = 15.sp, lineHeight = 23.sp, color = if (item.id == chapter.id) tokens.primary else tokens.foreground)
                                if (item.id == chapter.id) Text("正在读", color = tokens.primary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            HeroReaderTabV13.MORE -> Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, enabled = hasPrevious) { Icon(Icons.Rounded.ChevronLeft, "上一章", tint = tokens.foreground.copy(alpha = if (hasPrevious) 1f else .25f)) }
                    Text(readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber), Modifier.weight(1f), fontSize = 13.sp, color = tokens.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    IconButton(onClick = onNext, enabled = hasNext) { Icon(Icons.Rounded.ChevronRight, "下一章", tint = tokens.foreground.copy(alpha = if (hasNext) 1f else .25f)) }
                }
                Slider(value = progress.coerceIn(0f, 1f), onValueChange = onProgress, colors = SliderDefaults.colors(thumbColor = tokens.primary, activeTrackColor = tokens.primary))
                HeroReaderAdjustRowV26("字号", "${fontSize.roundToInt()}", fontSize, 14f..30f, tokens, onFontSize)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("paper" to "纸白", "tea" to "暖纸", "green" to "青叶", "night" to "夜间").forEach { (key, name) ->
                        val colors = heroReaderPaletteV13(key)
                        Surface(Modifier.weight(1f).clickable { onQuickTheme(key) }, shape = RoundedCornerShape(14.dp), color = colors.page) {
                            Text((if (themeKey == key) "✓ " else "") + name, Modifier.padding(vertical = 13.dp), color = colors.text, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                val actions = listOf(
                    HeroReaderActionV13("排版预设", Icons.Rounded.Tune, onClick = onPreset),
                    HeroReaderActionV13("主题", Icons.Rounded.Palette, onClick = onTheme),
                    HeroReaderActionV13("字体", Icons.Rounded.TextFields, onClick = onFont),
                    HeroReaderActionV13("字号", Icons.Rounded.FormatSize, onClick = { onType("字号") }),
                    HeroReaderActionV13("行距页边距", Icons.Rounded.FormatAlignJustify, onClick = { onType("行段") }),
                    HeroReaderActionV13("滚动阅读", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),
                    HeroReaderActionV13("全文搜索", Icons.Rounded.Search, onClick = onSearch),
                    HeroReaderActionV13("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),
                    HeroReaderActionV13("屏幕常亮", Icons.Rounded.LightMode, keepScreen, onKeepScreen),
                    HeroReaderActionV13("时间电量", Icons.Rounded.BatteryFull, showTimeBattery, onTimeBattery),
                    HeroReaderActionV13("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),
                    HeroReaderActionV13("锁定竖屏", Icons.Rounded.Landscape, lockPortrait, onLockPortrait),
                )
                actions.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { action ->
                            Box(Modifier.weight(1f)) {
                                LanghuanActionTileV4(action.label, action.icon, action.selected, tokens, action.onClick)
                            }
                        }
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
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .5f).dp).verticalScroll(rememberScrollState())) {
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
        listOf("langhuan" to "琅嬛星图", "paper" to "纸白", "tea" to "茶纸", "green" to "青叶", "night" to "夜间").forEach { (key, name) ->
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
    LanghuanSheetV4(tokens, title = "阅读排版") {
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .55f).dp).verticalScroll(rememberScrollState())) {
            HeroReaderAdjustRowV26("字号", "${fontSize.roundToInt()}", fontSize, 14f..30f, tokens, onFontSize)
            HeroReaderAdjustRowV26("行距", String.format(Locale.US, "%.2f", lineFactor), lineFactor, 1.3f..2.3f, tokens, onLine)
            HeroReaderAdjustRowV26("段距", "${paragraphSpacing.roundToInt()}", paragraphSpacing, 0f..24f, tokens, onParagraph)
            HeroReaderAdjustRowV26("页边距", "${sidePadding.roundToInt()}", sidePadding, 12f..40f, tokens, onPadding)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("首行缩进", Modifier.weight(1f), color = tokens.foreground, fontSize = 14.sp)
                Switch(checked = indent, onCheckedChange = onIndent)
            }
            LanghuanRowV4("恢复推荐排版", tokens, trailing = "舒适阅读", onClick = { onFontSize(20f); onLine(1.65f); onParagraph(8f); onPadding(22f); onIndent(true) })
        }
        LanghuanRowV4("完成", tokens, trailing = "✓", onClick = onBack)
    }
}

@Composable
private fun HeroReaderAdjustRowV26(
    label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>,
    tokens: LanghuanTokensV4, onValue: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), color = tokens.foreground, fontSize = 14.sp)
        Slider(value = value.coerceIn(range), onValueChange = { raw -> val step = if (range.endInclusive < 3f) .05f else 1f; onValue((raw / step).roundToInt() * step) }, valueRange = range, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = tokens.primary, activeTrackColor = tokens.primary))
        Text(valueLabel, Modifier.width(42.dp), color = tokens.mutedForeground, fontSize = 13.sp, textAlign = TextAlign.End)
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
    "langhuan" -> HeroReaderPaletteV13(Color(0xFFF5F6FE), Color(0xFF22232A), Color(0xFF747784), Color(0xFF5D78B8))
    "paper" -> HeroReaderPaletteV13(Color(0xFFF7F3EA), Color(0xFF282622), Color(0xFF716D64), Color(0xFFA77836))
    "green" -> HeroReaderPaletteV13(Color(0xFFDDE6D1), Color(0xFF283126), Color(0xFF65705F), Color(0xFF4A7652))
    "night" -> HeroReaderPaletteV13(Color(0xFF17191D), Color(0xFFD2D4D8), Color(0xFF858A91), Color(0xFF7EA8E8))
    else -> HeroReaderPaletteV13(Color(0xFFEDE4CE), Color(0xFF302B23), Color(0xFF817866), Color(0xFFA77836))
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

