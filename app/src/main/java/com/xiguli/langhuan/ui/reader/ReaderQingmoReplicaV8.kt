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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.LockRotation
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class QingmoReaderPaletteV8(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color = Color(0xFF347DEC),
)

private enum class QingmoReaderTabV8 { DETAILS, DIRECTORY, MORE }

private data class QingmoMoreItemV8(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ReaderQingmoReplicaV8(
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

    ReaderQingmoPageV8(
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
private fun ReaderQingmoPageV8(
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
    val prefs = remember(book.id) { context.getSharedPreferences("reader_qingmo_v8", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val chapters = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } }
    val chapterIndex = chapters.indexOfFirst { it.id == chapter.id }.coerceAtLeast(0)
    val previous = chapters.getOrNull(chapterIndex - 1)
    val next = chapters.getOrNull(chapterIndex + 1)

    var panelVisible by remember(chapter.id) { mutableStateOf(startPanel) }
    var tab by rememberSaveable(chapter.id) { mutableStateOf(QingmoReaderTabV8.DIRECTORY) }
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

    val pageMode = ReaderPageModeV10.entries.firstOrNull { it.key == pageModeKey } ?: ReaderPageModeV10.PAGE
    val palette = qingmoPaletteV8(themeKey)
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
            saved.textOffset > 0 -> qingmoPageForOffsetV8(offsets, saved.textOffset.coerceIn(0, readingText.length))
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

    fun rememberAnchor() { anchorOffset = currentOffset(); persist() }
    fun jumpChapter(target: ChapterDraft?, atEnd: Boolean = false) {
        target ?: return
        if (crossingChapter) return
        persist(); crossingChapter = true
        ReaderProgressStoreV11.moveTo(
            context, book.id, target.chapterNumber, 0, 0, pageMode.key,
            if (atEnd) 1f else 0f, if (atEnd) Int.MAX_VALUE else 0,
        )
        onOpenChapter(target.chapterNumber)
    }
    fun previousPage() {
        val page = currentPage()
        if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) } else jumpChapter(previous, true)
    }
    fun nextPage() {
        val page = currentPage()
        if (page < pages.lastIndex) scope.launch { pagerState.animateScrollToPage(page + 1) } else jumpChapter(next, false)
    }

    LaunchedEffect(layoutKey, pages.size, scrollState.maxValue) {
        if (appliedLayoutKey == layoutKey) return@LaunchedEffect
        val targetOffset = anchorOffset.coerceIn(0, readingText.length)
        if (pageMode == ReaderPageModeV10.SCROLL) {
            if (scrollState.maxValue > 0) {
                val f = if (readingText.isBlank()) 0f else targetOffset.toFloat() / readingText.length
                scrollState.scrollTo((scrollState.maxValue * f).roundToInt())
            }
        } else pagerState.scrollToPage(qingmoPageForOffsetV8(offsets, targetOffset).coerceIn(0, pages.lastIndex))
        appliedLayoutKey = layoutKey
    }

    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode != ReaderPageModeV10.SCROLL) snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { persist() }
    }
    LaunchedEffect(chapter.id, pageMode, layoutKey) {
        if (pageMode == ReaderPageModeV10.SCROLL) snapshotFlow { scrollState.value }.distinctUntilChanged().collectLatest { delay(180); persist() }
    }
    LaunchedEffect(fontSize, lineFactor, paragraphSpacing, firstLineIndent, fontKey, themeKey, pageModeKey, volumeTurn, keepScreen, showTimeBattery, immersive, clickAnimation, pullBookmark, fullNext, backgroundMask, backgroundFollow, statusBar, navigationBar, lockPortrait) {
        prefs.edit()
            .putFloat("font", fontSize).putFloat("line", lineFactor).putFloat("paragraph", paragraphSpacing)
            .putBoolean("indent", firstLineIndent).putString("fontKey", fontKey).putString("theme", themeKey).putString("pageMode", pageModeKey)
            .putBoolean("volumeTurn", volumeTurn).putBoolean("keepScreen", keepScreen).putBoolean("timeBattery", showTimeBattery)
            .putBoolean("immersive", immersive).putBoolean("clickAnimation", clickAnimation).putBoolean("pullBookmark", pullBookmark)
            .putBoolean("fullNext", fullNext).putBoolean("backgroundMask", backgroundMask).putBoolean("backgroundFollow", backgroundFollow)
            .putBoolean("statusBar", statusBar).putBoolean("navigationBar", navigationBar).putBoolean("lockPortrait", lockPortrait).apply()
    }
    DisposableEffect(chapter.id, layoutKey) { onDispose { persist() } }
    DisposableEffect(keepScreen) {
        if (keepScreen) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(lockPortrait) {
        if (lockPortrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (lockPortrait) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    Box(Modifier.fillMaxSize().background(palette.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(
            Modifier.fillMaxSize().pointerInput(chapter.id, pageModeKey, pagerState.settledPage) {
                detectTapGestures { offset ->
                    if (panelVisible) panelVisible = false
                    else if (pageMode == ReaderPageModeV10.SCROLL) panelVisible = true
                    else when {
                        offset.x < size.width * .28f -> previousPage()
                        offset.x > size.width * .72f -> nextPage()
                        else -> panelVisible = true
                    }
                }
            }
        ) {
            if (pageMode == ReaderPageModeV10.SCROLL) {
                Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 22.dp)) {
                    QingmoReaderHeaderV8(displayTitle, palette)
                    QingmoReaderParagraphsV8(readingText, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text)
                    Spacer(Modifier.height(70.dp))
                }
            } else {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    val safe = page.coerceIn(0, pages.lastIndex)
                    QingmoReaderPageContentV8(
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
            if (backgroundMask) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .035f)))
        }

        AnimatedVisibility(
            visible = panelVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(150, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(100)),
        ) {
            QingmoReaderPanelV8(
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
                onTab = { tab = it },
                onBack = { persist(); onBack() },
                onSelectChapter = { number -> jumpChapter(chapters.firstOrNull { it.chapterNumber == number }) },
                onTheme = { panelVisible = false; themeGallery = true },
                onFont = { panelVisible = false; fontPage = true },
                onFontSize = { panelVisible = false; quickType = "字号" },
                onLine = { panelVisible = false; quickType = "行段" },
                onLocate = { tab = QingmoReaderTabV8.DIRECTORY },
                onVertical = { rememberAnchor(); pageModeKey = if (pageMode == ReaderPageModeV10.SCROLL) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.SCROLL.key },
                onSimulated = { rememberAnchor(); pageModeKey = if (pageMode == ReaderPageModeV10.COVER) ReaderPageModeV10.PAGE.key else ReaderPageModeV10.COVER.key },
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
            QingmoThemeGalleryV8(themeKey, onTheme = { themeKey = it }, onBack = { themeGallery = false })
        }

        AnimatedVisibility(
            visible = fontPage,
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(100)),
        ) {
            QingmoFontPageV8(fontKey, onFont = { rememberAnchor(); fontKey = it }, onBack = { fontPage = false })
        }

        AnimatedVisibility(
            visible = searchPage,
            enter = slideInHorizontally(tween(190)) { it / 4 } + fadeIn(tween(120)),
            exit = slideOutHorizontally(tween(160)) { it / 4 } + fadeOut(tween(100)),
        ) {
            QingmoSearchV8(readingText, onBack = { searchPage = false })
        }

        quickType?.let { type ->
            QingmoQuickAdjustV8(
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
private fun QingmoReaderHeaderV8(title: String, palette: QingmoReaderPaletteV8) {
    Text(title, fontSize = 12.sp, color = palette.secondary.copy(alpha = .55f), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun QingmoReaderPageContentV8(
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
    palette: QingmoReaderPaletteV8,
    showTimeBattery: Boolean,
) {
    Column(Modifier.fillMaxSize().background(palette.background).padding(horizontal = 20.dp, vertical = 18.dp)) {
        if (firstPage) QingmoReaderHeaderV8(title, palette) else {
            Text(title, fontSize = 10.sp, color = palette.secondary.copy(alpha = .42f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(13.dp))
        }
        QingmoReaderParagraphsV8(body, fontSize, lineFactor, paragraphSpacing, firstLineIndent, family, palette.text)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (showTimeBattery) {
                val battery = qingmoBatteryV8()
                Text(qingmoTimeV8(), fontSize = 9.sp, color = palette.secondary.copy(alpha = .48f))
                Spacer(Modifier.width(8.dp))
                Text("$battery%", fontSize = 9.sp, color = palette.secondary.copy(alpha = .48f))
            }
            Spacer(Modifier.weight(1f))
            Text("$page/$pageCount", fontSize = 9.sp, color = palette.secondary.copy(alpha = .48f))
        }
    }
}

@Composable
private fun QingmoReaderParagraphsV8(text: String, fontSize: Float, lineFactor: Float, paragraphSpacing: Float, indent: Boolean, family: FontFamily, color: Color) {
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
private fun QingmoReaderPanelV8(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapter: ChapterDraft,
    tab: QingmoReaderTabV8,
    palette: QingmoReaderPaletteV8,
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
    onTab: (QingmoReaderTabV8) -> Unit,
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
                QingmoReaderTabV8.DETAILS -> QingmoDetailsV8(book, chapter, palette, onBack, onEdit, onWriting, onStory)
                QingmoReaderTabV8.DIRECTORY -> QingmoDirectoryV8(book, chapters, chapter.chapterNumber, palette, onBack, onSelectChapter)
                QingmoReaderTabV8.MORE -> {
                    val more = listOf(
                        QingmoMoreItemV8("主题", Icons.Rounded.Palette, onClick = onTheme),
                        QingmoMoreItemV8("字体", Icons.Rounded.TextFields, onClick = onFont),
                        QingmoMoreItemV8("字号", Icons.Rounded.FormatSize, onClick = onFontSize),
                        QingmoMoreItemV8("行段", Icons.Rounded.FormatAlignJustify, onClick = onLine),
                        QingmoMoreItemV8("定位", Icons.Rounded.MyLocation, onClick = onLocate),
                        QingmoMoreItemV8("上下翻页", Icons.Rounded.SwapVert, pageMode == ReaderPageModeV10.SCROLL, onVertical),
                        QingmoMoreItemV8("仿真翻页", Icons.Rounded.Book, pageMode == ReaderPageModeV10.COVER, onSimulated),
                        QingmoMoreItemV8("全文搜索", Icons.Rounded.Search, onClick = onSearch),
                        QingmoMoreItemV8("音量键翻页", Icons.Rounded.VolumeUp, volumeTurn, onVolume),
                        QingmoMoreItemV8("屏幕常亮", Icons.Rounded.Smartphone, keepScreen, onKeepScreen),
                        QingmoMoreItemV8("时间电量", Icons.Rounded.Timer, showTimeBattery, onTimeBattery),
                        QingmoMoreItemV8("沉浸式", Icons.Rounded.Fullscreen, immersive, onImmersive),
                        QingmoMoreItemV8("点击动画", Icons.Rounded.TouchApp, clickAnimation, onClickAnimation),
                        QingmoMoreItemV8("下拉书签", Icons.Outlined.BookmarkBorder, pullBookmark, onPullBookmark),
                        QingmoMoreItemV8("全屏下一页", Icons.Rounded.NavigateNext, fullNext, onFullNext),
                        QingmoMoreItemV8("背景图遮罩", Icons.Rounded.Image, backgroundMask, onBackgroundMask),
                        QingmoMoreItemV8("背景跟随", Icons.Rounded.Landscape, backgroundFollow, onBackgroundFollow),
                        QingmoMoreItemV8("状态栏", Icons.Rounded.Smartphone, statusBar, onStatusBar),
                        QingmoMoreItemV8("导航栏", Icons.Rounded.Smartphone, navigationBar, onNavigationBar),
                        QingmoMoreItemV8("锁定竖屏", Icons.Rounded.LockRotation, lockPortrait, onLockPortrait),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().height(500.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(more, key = { it.label }) { item -> QingmoMoreButtonV8(item, palette) }
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFEEEFF1))
            Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                QingmoTabV8("详情", tab == QingmoReaderTabV8.DETAILS, Modifier.weight(1f)) { onTab(QingmoReaderTabV8.DETAILS) }
                QingmoTabV8("目录", tab == QingmoReaderTabV8.DIRECTORY, Modifier.weight(1f)) { onTab(QingmoReaderTabV8.DIRECTORY) }
                QingmoTabV8("更多", tab == QingmoReaderTabV8.MORE, Modifier.weight(1f)) { onTab(QingmoReaderTabV8.MORE) }
            }
        }
    }
}

@Composable
private fun QingmoDetailsV8(book: ReaderBookUi, chapter: ChapterDraft, palette: QingmoReaderPaletteV8, onBack: () -> Unit, onEdit: () -> Unit, onWriting: () -> Unit, onStory: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowLeft, "返回") }
            Text(book.title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E2024))
            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(20.dp), tint = Color(0xFF2F3237))
            Spacer(Modifier.width(20.dp))
            Icon(Icons.Outlined.DarkMode, null, Modifier.size(20.dp), tint = Color(0xFF2F3237))
        }
        Text("${book.genre.ifBlank { "作品" }} · 第 ${chapter.chapterNumber} 章", fontSize = 12.sp, color = Color(0xFF8D9096))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QingmoSmallActionV8("编辑本章", onEdit)
            QingmoSmallActionV8("AI 创作", onWriting)
            QingmoSmallActionV8("进入故事", onStory)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun QingmoDirectoryV8(book: ReaderBookUi, chapters: List<ChapterDraft>, current: Int, palette: QingmoReaderPaletteV8, onBack: () -> Unit, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.KeyboardArrowLeft, "返回") }
            Text(book.title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202226))
            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(20.dp), tint = Color(0xFF35383D))
            Spacer(Modifier.width(20.dp))
            Icon(Icons.Outlined.DarkMode, null, Modifier.size(20.dp), tint = Color(0xFF35383D))
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
private fun QingmoMoreButtonV8(item: QingmoMoreItemV8, palette: QingmoReaderPaletteV8) {
    Column(Modifier.fillMaxWidth().clickable(onClick = item.onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            color = if (item.selected) Color(0xFFEEF4FF) else Color(0xFFF3F4F6),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(item.icon, null, Modifier.size(23.dp), tint = if (item.selected) palette.accent else Color(0xFF35383E)) }
        }
        Text(item.label, Modifier.padding(top = 6.dp), fontSize = 10.sp, color = Color(0xFFAAADB2), maxLines = 1)
    }
}

@Composable
private fun QingmoTabV8(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color(0xFF347DEC) else Color(0xFFB3B5B9))
    }
}

@Composable
private fun QingmoSmallActionV8(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = Color(0xFFF4F5F6)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 11.sp, color = Color(0xFF55585E))
    }
}

@Composable
private fun QingmoThemeGalleryV8(current: String, onTheme: (String) -> Unit, onBack: () -> Unit) {
    val themes = listOf(
        "tea" to Color(0xFFE6E1C9), "mint" to Color(0xFFDCE6D6), "paper" to Color(0xFFF1ECE2), "mist" to Color(0xFFE6EAF1),
        "green" to Color(0xFFD5DFD0), "cream" to Color(0xFFE9DEC9), "blue" to Color(0xFFDCE3EE), "sage" to Color(0xFFCED9CB),
        "rose" to Color(0xFFE7D7D3), "white" to Color(0xFFF8F8F8), "sand" to Color(0xFFE3D6BC), "night" to Color(0xFF171719),
    )
    var nightTab by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("日间主题", Modifier.clickable { nightTab = false }.padding(horizontal = 10.dp, vertical = 15.dp), fontSize = 14.sp, fontWeight = if (!nightTab) FontWeight.SemiBold else FontWeight.Normal, color = if (!nightTab) Color(0xFF202226) else Color(0xFF999CA2))
                Text("夜间主题", Modifier.clickable { nightTab = true }.padding(horizontal = 10.dp, vertical = 15.dp), fontSize = 14.sp, fontWeight = if (nightTab) FontWeight.SemiBold else FontWeight.Normal, color = if (nightTab) Color(0xFF202226) else Color(0xFF999CA2))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) { Icon(Icons.Rounded.Search, "搜索") }
                IconButton(onClick = {}) { Icon(Icons.Rounded.Add, "新增") }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(themes.filter { nightTab == (it.first == "night") || !nightTab && it.first != "night" }, key = { it.first }) { (key, color) ->
                    val selected = current == key
                    Surface(onClick = { onTheme(key) }, shape = RoundedCornerShape(2.dp), color = color, modifier = Modifier.fillMaxWidth().height(150.dp)) {
                        Box(Modifier.fillMaxSize().padding(10.dp)) {
                            Text(qingmoThemeNameV8(key), fontSize = 13.sp, color = if (key == "night") Color.White else Color(0xFF33363A), fontWeight = FontWeight.Medium)
                            if (selected) Icon(Icons.Rounded.Check, null, Modifier.align(Alignment.TopEnd).size(20.dp), tint = Color(0xFF347DEC))
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) Color(0xFF9B9DA2) else Color(0xFF347DEC),
                            ) { Text(if (selected) "已使用" else "应用", Modifier.padding(horizontal = 15.dp, vertical = 5.dp), color = Color.White, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QingmoFontPageV8(current: String, onFont: (String) -> Unit, onBack: () -> Unit) {
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
private fun QingmoSearchV8(text: String, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(text, query) { if (query.isBlank()) emptyList() else text.split(Regex("\\n+")).filter { it.contains(query, true) }.take(50) }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                OutlinedTextField(query, { query = it }, Modifier.weight(1f).padding(end = 12.dp), placeholder = { Text("全文搜索") }, singleLine = true)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)) {
                items(results) { line -> Text(line, Modifier.fillMaxWidth().padding(vertical = 11.dp), fontSize = 13.sp, color = Color(0xFF303338)) }
            }
        }
    }
}

@Composable
private fun QingmoQuickAdjustV8(
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
                    listOf(1.55f, 1.75f, 1.95f).forEach { value -> TextButton(onClick = { onLine(value) }) { Text(if (value < 1.7f) "紧" else if (value < 1.9f) "中" else "松") } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0f, 3f, 7f).forEach { value -> TextButton(onClick = { onParagraph(value) }) { Text(if (value == 0f) "段紧" else if (value < 5f) "段中" else "段松") } }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("首行缩进", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { onIndent(!firstLineIndent) }) { Text(if (firstLineIndent) "开启" else "关闭") }
                }
            }
        }
    }
}

private fun qingmoPageForOffsetV8(offsets: List<Int>, offset: Int): Int {
    if (offsets.isEmpty()) return 0
    var result = 0
    offsets.forEachIndexed { index, start -> if (start <= offset) result = index }
    return result.coerceIn(0, offsets.lastIndex)
}

private fun qingmoPaletteV8(key: String): QingmoReaderPaletteV8 = when (key) {
    "night" -> QingmoReaderPaletteV8(Color(0xFF171719), Color(0xFFE4E1DC), Color(0xFF9C9994))
    "mint" -> QingmoReaderPaletteV8(Color(0xFFDCE6D6), Color(0xFF2D332C), Color(0xFF6C756A))
    "paper" -> QingmoReaderPaletteV8(Color(0xFFF1ECE2), Color(0xFF302D29), Color(0xFF7B756D))
    "mist" -> QingmoReaderPaletteV8(Color(0xFFE6EAF1), Color(0xFF2B2F36), Color(0xFF707680))
    "green" -> QingmoReaderPaletteV8(Color(0xFFD5DFD0), Color(0xFF2C332B), Color(0xFF6B7568))
    "cream" -> QingmoReaderPaletteV8(Color(0xFFE9DEC9), Color(0xFF342E26), Color(0xFF796F61))
    "blue" -> QingmoReaderPaletteV8(Color(0xFFDCE3EE), Color(0xFF29303A), Color(0xFF6D7580))
    "sage" -> QingmoReaderPaletteV8(Color(0xFFCED9CB), Color(0xFF293229), Color(0xFF657063))
    "rose" -> QingmoReaderPaletteV8(Color(0xFFE7D7D3), Color(0xFF352D2C), Color(0xFF7B6C69))
    "white" -> QingmoReaderPaletteV8(Color(0xFFF8F8F8), Color(0xFF27292D), Color(0xFF777A80))
    "sand" -> QingmoReaderPaletteV8(Color(0xFFE3D6BC), Color(0xFF352F26), Color(0xFF766C5E))
    else -> QingmoReaderPaletteV8(Color(0xFFE6E1C9), Color(0xFF2E2B2B), Color(0xFF8A8575))
}

private fun qingmoThemeNameV8(key: String): String = when (key) {
    "tea" -> "番茄"; "mint" -> "花开"; "paper" -> "书卷"; "mist" -> "雾白"; "green" -> "护眼"; "cream" -> "羊皮纸";
    "blue" -> "群山"; "sage" -> "生机盎然"; "rose" -> "浅墨"; "white" -> "白灰侠"; "sand" -> "仿真阅读书"; "night" -> "夜间"; else -> key
}

@Composable
private fun qingmoTimeV8(): String {
    var value by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    }
    return value
}

@Composable
private fun qingmoBatteryV8(): Int {
    val context = LocalContext.current
    return remember { context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0 }
}
