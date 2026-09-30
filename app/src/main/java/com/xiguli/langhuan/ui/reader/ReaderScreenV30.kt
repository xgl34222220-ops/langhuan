@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.xiguli.langhuan.ui

import android.content.Context
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.ChapterDraft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ReaderMenuTabV30 { DETAILS, DIRECTORY, MORE }
internal enum class ReaderMenuPanelV30 { MAIN, THEME, FONT, SIZE, SPACING, TURN, SEARCH }

/** Holds the running page-turn animation and what must happen when it ends. */
private class ReaderTurnHolderV30 {
    var job: Job? = null
    var end: (() -> Unit)? = null
}

@Composable
internal fun ReaderEngineV30(
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
    val startChapter = state.readingChapter
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE) }
    val settings = remember { ReaderSettingsV30(prefs) }
    val theme = readerThemeV30(settings.theme)

    if (startChapter == null) {
        BackHandler { onBackToShelf() }
        Box(Modifier.fillMaxSize().background(theme.page), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(book.title, color = theme.text, fontSize = 18.sp)
                Text("这本书还没有章节", Modifier.padding(top = 8.dp), color = theme.secondary, fontSize = 13.sp)
                androidx.compose.material3.TextButton(onClick = onBackToShelf, modifier = Modifier.padding(top = 12.dp)) {
                    Text("返回书架", color = theme.accent)
                }
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

    key(book.id) {
    ReaderSessionV30(
        book = book,
        chapters = remember(state.chapters) { state.chapters.sortedBy { it.chapterNumber } },
        externalChapterId = startChapter.id,
        settings = settings,
        startOnInfo = startOnInfo,
        interactionEnabled = interactionEnabled,
        onChapterChanged = { number -> viewModel.openReader(number) },
        onBack = onBackToShelf,
        onEdit = { number -> onOpenEditor(book.id, number) },
        onWriting = { onEnterWriting(book.id) },
        onStory = { storyMode = true },
    )
    }
}

@Composable
private fun ReaderSessionV30(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    externalChapterId: String,
    settings: ReaderSettingsV30,
    startOnInfo: Boolean,
    interactionEnabled: Boolean,
    onChapterChanged: (Int) -> Unit,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE) }
    val theme = readerThemeV30(settings.theme)
    val mode = settings.turnMode

    // ---- Position -----------------------------------------------------------------------
    val initialIndex = remember { chapters.indexOfFirst { it.id == externalChapterId }.coerceAtLeast(0) }
    val initialAnchor = remember {
        val chapter = chapters.getOrNull(initialIndex)
        val saved = ReaderProgressStoreV11.load(context, book.id, chapter?.chapterNumber ?: 1)
        if (chapter != null && saved.chapterNumber == chapter.chapterNumber) saved.textOffset.coerceAtLeast(0) else 0
    }
    var chapterIndex by remember { mutableIntStateOf(initialIndex) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pendingAnchor by remember { mutableStateOf<Int?>(initialAnchor) }
    val anchorHolder = remember { intArrayOf(initialAnchor) }

    // ---- Geometry & typography ------------------------------------------------------------
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val topInset = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ).toFloat()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toFloat()
    val geometry = remember(viewport, topInset, bottomInset, settings.sidePadding, density.density) {
        ReaderGeometryV30.of(
            width = viewport.width.toFloat(),
            height = viewport.height.toFloat(),
            density = density.density,
            topInset = topInset,
            bottomInset = bottomInset,
            sidePaddingDp = settings.sidePadding,
        )
    }
    val spec = with(density) {
        val fontPx = settings.fontSize.sp.toPx()
        val titlePx = fontPx * 1.36f
        ReaderTypeSpecV30(
            bodyWidthPx = geometry.bodyWidth.toInt().coerceAtLeast(0),
            bodyHeightPx = geometry.bodyHeight.toInt().coerceAtLeast(0),
            fontSizePx = fontPx,
            lineHeightPx = (fontPx * settings.lineFactor).roundToInt().toFloat(),
            paragraphGapPx = settings.paragraphSpacing.dp.toPx().roundToInt().toFloat(),
            titleSizePx = titlePx,
            titleLineHeightPx = (titlePx * 1.45f).roundToInt().toFloat(),
            titleGapPx = (fontPx * 1.6f).roundToInt().toFloat(),
            indent = settings.indent,
            fontKey = settings.fontKey,
            weight = settings.weight,
            letterSpacingEm = settings.letterSpacing,
        )
    }
    val chromePx = with(density) { 12.sp.toPx() }
    val paints = remember(spec.key, theme, chromePx) { readerPaintsV30(spec, theme, chromePx) }

    // ---- Layout cache ------------------------------------------------------------------------
    val layouts = remember { mutableStateMapOf<String, ReaderChapterPagesV30>() }
    val stale = remember { mutableStateMapOf<String, ReaderChapterPagesV30>() }
    fun keyFor(chapter: ChapterDraft): String =
        "${spec.key}#${chapter.id}#${chapter.content.length}#${chapter.title.hashCode()}"
    fun layoutFor(index: Int): ReaderChapterPagesV30? = chapters.getOrNull(index)?.let { layouts[keyFor(it)] }

    // When typography changes, remember where the reader was so the same sentence stays on screen.
    var lastSpecKey by remember { mutableStateOf(spec.key) }
    if (lastSpecKey != spec.key) {
        lastSpecKey = spec.key
        pendingAnchor = anchorHolder[0]
    }

    LaunchedEffect(spec.key, chapterIndex, chapters) {
        if (spec.bodyWidthPx <= 0 || spec.bodyHeightPx <= 0) return@LaunchedEffect
        val wanted = listOf(chapterIndex, chapterIndex + 1, chapterIndex - 1)
        for (index in wanted) {
            val chapter = chapters.getOrNull(index) ?: continue
            val key = keyFor(chapter)
            if (layouts.containsKey(key)) continue
            val title = readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
            val result = withContext(Dispatchers.Default) {
                val body = readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
                readerPaginateChapterV30(index, chapter.id, title, body, spec)
            }
            layouts[key] = result
            stale[chapter.id] = result
        }
        val keep = wanted.mapNotNull { chapters.getOrNull(it) }
        val keepKeys = keep.map { keyFor(it) }.toSet()
        layouts.keys.filter { it !in keepKeys }.forEach { layouts.remove(it) }
        val keepIds = keep.map { it.id }.toSet()
        stale.keys.filter { it !in keepIds }.forEach { stale.remove(it) }
    }

    val currentChapter = chapters.getOrNull(chapterIndex)
    val currentLayout = layoutFor(chapterIndex)
    val shownLayout = currentLayout ?: currentChapter?.let { stale[it.id] }
    val shownPageIndex = when {
        shownLayout == null -> 0
        currentLayout == null -> shownLayout.pageForOffset(anchorHolder[0])
        pendingAnchor != null -> {
            val anchor = pendingAnchor ?: 0
            if (anchor == Int.MAX_VALUE) currentLayout.pages.lastIndex else currentLayout.pageForOffset(anchor)
        }
        else -> pageIndex.coerceIn(0, currentLayout.pages.lastIndex)
    }
    val shownPage = shownLayout?.pages?.getOrNull(shownPageIndex)
    SideEffect {
        if (currentLayout != null && pendingAnchor != null) {
            pageIndex = shownPageIndex
            pendingAnchor = null
        }
        if (currentLayout != null && shownPage != null) anchorHolder[0] = shownPage.startOffset
    }

    // Follow chapter changes that come from outside the reader (root restore, editor return).
    LaunchedEffect(externalChapterId, chapters.size) {
        val index = chapters.indexOfFirst { it.id == externalChapterId }
        if (index >= 0 && index != chapterIndex) {
            chapterIndex = index
            pageIndex = 0
            pendingAnchor = 0
        }
    }

    // ---- Chrome info ---------------------------------------------------------------------------
    var clock by remember { mutableStateOf(readerClockV30()) }
    var battery by remember { mutableIntStateOf(readerBatteryV30(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = readerClockV30()
            battery = readerBatteryV30(context)
            delay(20_000)
        }
    }
    var bookmarks by remember { mutableStateOf(prefs.getStringSet("bookmarks", emptySet())?.toSet().orEmpty()) }

    fun infoFor(page: ReaderPageV30?, index: Int): ReaderChromeInfoV30 {
        val chapter = chapters.getOrNull(index)
        val layout = layoutFor(index) ?: chapter?.let { stale[it.id] }
        val count = layout?.pages?.size ?: 0
        val pageNumber = (page?.index ?: 0) + 1
        val bookProgress = if (chapters.isEmpty()) 0f else
            ((index + if (count > 0) pageNumber.toFloat() / count else 0f) / chapters.size * 100f).coerceIn(0f, 100f)
        val header = when {
            chapter == null -> book.title
            page?.index == 0 -> book.title
            else -> readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)
        }
        return ReaderChromeInfoV30(
            chapterTitle = header,
            pageLabel = if (count > 0) "$pageNumber/$count" else "",
            progressLabel = String.format(Locale.US, "%.1f%%", bookProgress),
            time = clock,
            battery = battery,
            showTimeBattery = settings.showTimeBattery,
            bookmarked = chapter != null && chapter.chapterNumber.toString() in bookmarks,
        )
    }

    fun persist() {
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        val layout = layoutFor(chapterIndex) ?: return
        if (pendingAnchor != null) return
        val page = layout.pages.getOrNull(pageIndex) ?: return
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = pageIndex,
                scrollY = 0,
                positionFraction = if (layout.textLength <= 0) 0f else page.startOffset.toFloat() / layout.textLength,
                textOffset = page.startOffset,
                modeKey = mode.key,
            ),
        )
    }

    // ---- Navigation --------------------------------------------------------------------------
    fun neighbor(delta: Int): ReaderPageV30? {
        val layout = layoutFor(chapterIndex) ?: return null
        if (pendingAnchor != null) return null
        val target = pageIndex + delta
        if (target in layout.pages.indices) return layout.pages[target]
        return if (delta > 0) layoutFor(chapterIndex + 1)?.pages?.firstOrNull()
        else layoutFor(chapterIndex - 1)?.pages?.lastOrNull()
    }

    fun neighborChapterIndex(delta: Int): Int {
        val layout = layoutFor(chapterIndex) ?: return chapterIndex
        val target = pageIndex + delta
        return if (target in layout.pages.indices) chapterIndex else chapterIndex + delta
    }

    fun commit(delta: Int) {
        val layout = layoutFor(chapterIndex) ?: return
        val target = pageIndex + delta
        val before = chapterIndex
        when {
            target in layout.pages.indices -> pageIndex = target
            delta > 0 && layoutFor(chapterIndex + 1) != null -> {
                chapterIndex += 1
                pageIndex = 0
            }
            delta < 0 && layoutFor(chapterIndex - 1) != null -> {
                val previous = layoutFor(chapterIndex - 1) ?: return
                chapterIndex -= 1
                pageIndex = previous.pages.lastIndex
            }
        }
        if (chapterIndex != before) chapters.getOrNull(chapterIndex)?.let { onChapterChanged(it.chapterNumber) }
    }

    var scrollJump by remember { mutableIntStateOf(0) }

    fun jumpTo(index: Int, anchor: Int = 0) {
        val chapter = chapters.getOrNull(index) ?: return
        scrollJump++
        chapterIndex = index
        pageIndex = 0
        pendingAnchor = anchor
        anchorHolder[0] = anchor
        onChapterChanged(chapter.chapterNumber)
    }

    // ---- Page-turn animation ----------------------------------------------------------------------
    var turnDir by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val turn = remember { ReaderTurnHolderV30() }
    var edgeHint by remember { mutableStateOf<String?>(null) }

    fun resetTurn() {
        turnDir = 0
        dragX = 0f
    }

    fun finishTurnNow() {
        val end = turn.end
        turn.end = null
        turn.job?.cancel()
        turn.job = null
        end?.invoke()
    }

    fun runTurn(target: Float, durationMs: Int, end: () -> Unit) {
        turn.end = end
        turn.job = scope.launch {
            animate(
                initialValue = dragX,
                targetValue = target,
                animationSpec = tween(durationMs, easing = if (mode == ReaderTurnModeV30.SIMULATION) LinearOutSlowInEasing else FastOutSlowInEasing),
            ) { value, _ -> dragX = value }
            val finished = turn.end
            turn.end = null
            turn.job = null
            finished?.invoke()
        }
    }

    fun commitAndReset(delta: Int) {
        Snapshot.withMutableSnapshot {
            commit(delta)
            resetTurn()
        }
    }

    fun showEdge(delta: Int) {
        val nextExists = chapters.getOrNull(chapterIndex + delta) != null
        edgeHint = when {
            nextExists -> "正在排版下一章…"
            delta > 0 -> "已经是最后一页了"
            else -> "已经是第一页了"
        }
    }

    val baseDuration = if (mode == ReaderTurnModeV30.SIMULATION) 420 else 300

    fun turnPage(delta: Int) {
        if (!interactionEnabled) return
        finishTurnNow()
        if (neighbor(delta) == null) {
            showEdge(delta)
            return
        }
        val width = geometry.width
        if (!settings.clickAnimation || mode == ReaderTurnModeV30.NONE || width <= 0f) {
            commit(delta)
            return
        }
        turnDir = delta
        dragX = 0f
        runTurn(if (delta > 0) -width else width, baseDuration) { commitAndReset(delta) }
    }

    // ---- Menu state ---------------------------------------------------------------------------
    var menuVisible by remember { mutableStateOf(startOnInfo) }
    var menuTab by remember { mutableStateOf(if (startOnInfo) ReaderMenuTabV30.DETAILS else ReaderMenuTabV30.MORE) }
    var menuPanel by remember { mutableStateOf(ReaderMenuPanelV30.MAIN) }

    fun openMenu() {
        finishTurnNow()
        menuPanel = ReaderMenuPanelV30.MAIN
        menuVisible = true
    }

    fun tapAt(point: Offset) {
        val w = geometry.width
        val h = geometry.height
        val inCenterColumn = point.x > w / 3f && point.x < w * 2f / 3f
        when {
            inCenterColumn && point.y > h / 4f && point.y < h * 3f / 4f -> openMenu()
            settings.fullNext -> turnPage(1)
            point.x < w / 3f -> turnPage(-1)
            point.x > w * 2f / 3f -> turnPage(1)
            point.y <= h / 4f -> turnPage(-1)
            else -> turnPage(1)
        }
    }

    // ---- Effects: persistence, lifecycle, settings ------------------------------------------------
    LaunchedEffect(chapterIndex, pageIndex, pendingAnchor) {
        delay(260)
        persist()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) persist()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            persist()
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { settings.snapshot() }.distinctUntilChanged().collect { settings.save() }
    }
    LaunchedEffect(edgeHint) {
        if (edgeHint != null) {
            delay(1400)
            edgeHint = null
        }
    }
    // Leaving scroll mode must not leave an animation half way.
    LaunchedEffect(mode) {
        finishTurnNow()
        resetTurn()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(settings.volumeTurn, menuVisible) {
        if (settings.volumeTurn && !menuVisible) runCatching { focusRequester.requestFocus() }
    }

    BackHandler {
        when {
            menuVisible && menuPanel != ReaderMenuPanelV30.MAIN -> menuPanel = ReaderMenuPanelV30.MAIN
            menuVisible -> menuVisible = false
            else -> {
                persist()
                onBack()
            }
        }
    }

    // Scroll mode shares position and layouts with paged mode.
    val listState = rememberLazyListState()

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.page)
            .onSizeChanged { viewport = it }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!interactionEnabled || !settings.volumeTurn || menuVisible || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.VolumeUp -> {
                        if (mode == ReaderTurnModeV30.SCROLL) scope.launch { listState.animateScrollBy(-geometry.bodyHeight * .9f) }
                        else turnPage(-1)
                        true
                    }
                    Key.VolumeDown -> {
                        if (mode == ReaderTurnModeV30.SCROLL) scope.launch { listState.animateScrollBy(geometry.bodyHeight * .9f) }
                        else turnPage(1)
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (mode == ReaderTurnModeV30.SCROLL) {
            ReaderScrollModeV30(
                chapters = chapters,
                chapterIndex = chapterIndex,
                currentPageIndex = shownPageIndex,
                layoutFor = ::layoutFor,
                geometry = geometry,
                theme = theme,
                paints = paints,
                infoFor = ::infoFor,
                listState = listState,
                jumpToken = scrollJump,
                interactionEnabled = interactionEnabled,
                onVisible = { index, page ->
                    if (pendingAnchor == null) {
                        val changed = index != chapterIndex
                        chapterIndex = index
                        pageIndex = page
                        if (changed) chapters.getOrNull(index)?.let { onChapterChanged(it.chapterNumber) }
                    }
                },
                onTap = { openMenu() },
            )
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .pointerInput(interactionEnabled, spec.key, chapters, mode, settings.fullNext, settings.clickAnimation, geometry) {
                        if (!interactionEnabled) return@pointerInput
                        val flingVelocity = 520.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            finishTurnNow()
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            val slop = viewConfiguration.touchSlop
                            var dragging = false
                            var blocked = false
                            var totalX = 0f
                            var totalY = 0f
                            var direction = 0
                            var released = false
                            val width = size.width.toFloat()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.changedToUpIgnoreConsumed()) {
                                    released = true
                                    break
                                }
                                val delta = change.positionChangeIgnoreConsumed()
                                totalX += delta.x
                                totalY += delta.y
                                if (!dragging && !blocked) {
                                    if (abs(totalX) > slop && abs(totalX) > abs(totalY) * .8f) {
                                        direction = if (totalX < 0f) 1 else -1
                                        if (neighbor(direction) != null) {
                                            dragging = true
                                            turnDir = direction
                                            dragX = 0f
                                        } else {
                                            blocked = true
                                            showEdge(direction)
                                        }
                                    } else if (abs(totalY) > slop * 2f && abs(totalY) > abs(totalX)) {
                                        blocked = true
                                    }
                                }
                                if (dragging) {
                                    change.consume()
                                    dragX = if (direction > 0) (dragX + delta.x).coerceIn(-width, 0f)
                                    else (dragX + delta.x).coerceIn(0f, width)
                                }
                            }
                            if (dragging) {
                                val velocity = tracker.calculateVelocity().x
                                val threshold = width * .16f
                                val commit = if (direction > 0) {
                                    velocity < -flingVelocity || (dragX < -threshold && velocity < flingVelocity)
                                } else {
                                    velocity > flingVelocity || (dragX > threshold && velocity > -flingVelocity)
                                }
                                if (mode == ReaderTurnModeV30.NONE) {
                                    if (commit) commitAndReset(direction) else resetTurn()
                                } else {
                                    val target = if (!commit) 0f else if (direction > 0) -width else width
                                    val remaining = abs(target - dragX) / width.coerceAtLeast(1f)
                                    val duration = (baseDuration * remaining).roundToInt().coerceIn(120, baseDuration)
                                    runTurn(target, duration) {
                                        if (commit) commitAndReset(direction) else resetTurn()
                                    }
                                }
                            } else if (released && !blocked && abs(totalX) <= slop && abs(totalY) <= slop) {
                                tapAt(down.position)
                            }
                        }
                    }
                    .drawBehind {
                        val current = shownPage
                        val direction = turnDir
                        if (current == null || direction == 0) {
                            drawReaderPageV30(
                                current, geometry, theme, paints, infoFor(current, chapterIndex),
                                placeholder = currentChapter?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) },
                            )
                            return@drawBehind
                        }
                        val w = size.width.coerceAtLeast(1f)
                        if (direction > 0) {
                            val next = neighbor(1)
                            val nextChapter = neighborChapterIndex(1)
                            drawReaderTurnV30(
                                mode = mode,
                                progress = -dragX / w,
                                drawTop = { drawReaderPageV30(current, geometry, theme, paints, infoFor(current, chapterIndex)) },
                                drawBottom = { drawReaderPageV30(next, geometry, theme, paints, infoFor(next, nextChapter)) },
                                theme = theme,
                            )
                        } else {
                            val previous = neighbor(-1)
                            val previousChapter = neighborChapterIndex(-1)
                            drawReaderTurnV30(
                                mode = mode,
                                progress = 1f - dragX / w,
                                drawTop = { drawReaderPageV30(previous, geometry, theme, paints, infoFor(previous, previousChapter)) },
                                drawBottom = { drawReaderPageV30(current, geometry, theme, paints, infoFor(current, chapterIndex)) },
                                theme = theme,
                            )
                        }
                    },
            )
        }

        // First open / typography rebuild of a chapter that has never been laid out.
        AnimatedVisibility(
            visible = shownLayout == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(96.dp).height(2.dp),
                    color = theme.accent,
                    trackColor = theme.secondary.copy(alpha = .18f),
                )
            }
        }

        AnimatedVisibility(
            visible = edgeHint != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
            enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = .92f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = .96f),
        ) {
            Surface(shape = RoundedCornerShape(50), color = theme.sheetText.copy(alpha = .86f)) {
                Text(
                    edgeHint.orEmpty(),
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    color = theme.sheet,
                    fontSize = 13.sp,
                )
            }
        }

        ReaderMenuV30(
            visible = menuVisible,
            tab = menuTab,
            panel = menuPanel,
            book = book,
            chapters = chapters,
            chapterIndex = chapterIndex,
            pageIndex = shownPageIndex,
            pageCount = shownLayout?.pages?.size ?: 0,
            settings = settings,
            theme = theme,
            bookmarked = currentChapter?.chapterNumber?.toString() in bookmarks,
            onDismiss = { menuVisible = false },
            onTab = { menuTab = it },
            onPanel = { menuPanel = it },
            onBack = {
                persist()
                onBack()
            },
            onToggleBookmark = {
                val number = currentChapter?.chapterNumber?.toString()
                if (number != null) {
                    val next = bookmarks.toMutableSet()
                    if (!next.add(number)) next.remove(number)
                    prefs.edit().putStringSet("bookmarks", next).apply()
                    bookmarks = next
                }
            },
            onJumpChapter = { index, anchor ->
                menuVisible = false
                jumpTo(index, anchor)
            },
            onPageFraction = { fraction ->
                val layout = layoutFor(chapterIndex)
                if (layout != null) {
                    val target = (fraction * layout.pages.lastIndex).roundToInt().coerceIn(0, layout.pages.lastIndex)
                    pageIndex = target
                    pendingAnchor = null
                    if (mode == ReaderTurnModeV30.SCROLL) scrollJump++
                }
            },
            onEdit = { currentChapter?.let { onEdit(it.chapterNumber) } },
            onWriting = onWriting,
            onStory = onStory,
        )
    }
}

/**
 * Continuous vertical reading. Pages of the current window of chapters are stacked, each only as
 * tall as its text, so the flow reads like one long column; header and footer stay pinned.
 */
@Composable
private fun ReaderScrollModeV30(
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    currentPageIndex: Int,
    layoutFor: (Int) -> ReaderChapterPagesV30?,
    geometry: ReaderGeometryV30,
    theme: ReaderThemeV30,
    paints: ReaderPaintsV30,
    infoFor: (ReaderPageV30?, Int) -> ReaderChromeInfoV30,
    listState: androidx.compose.foundation.lazy.LazyListState,
    jumpToken: Int,
    interactionEnabled: Boolean,
    onVisible: (Int, Int) -> Unit,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val pageItems = (chapterIndex - 1..chapterIndex + 1).flatMap { index ->
        layoutFor(index)?.pages?.map { index to it }.orEmpty()
    }
    val itemsState = rememberUpdatedState(pageItems)
    val targetState = rememberUpdatedState(chapterIndex to currentPageIndex)
    val visibleCallback = rememberUpdatedState(onVisible)
    var positioned by remember { mutableStateOf(false) }

    LaunchedEffect(jumpToken) {
        positioned = false
        val target = snapshotFlow {
            val (chapter, page) = targetState.value
            itemsState.value.indexOfFirst { it.first == chapter && it.second.index == page }
        }.first { it >= 0 }
        listState.scrollToItem(target)
        positioned = true
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to positioned }
            .distinctUntilChanged()
            .collect { (_, ready) ->
                if (!ready) return@collect
                val key = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String ?: return@collect
                val parts = key.split(':')
                val chapter = parts.getOrNull(0)?.toIntOrNull() ?: return@collect
                val page = parts.getOrNull(1)?.toIntOrNull() ?: return@collect
                visibleCallback.value(chapter, page)
            }
    }

    val topPad = with(density) { geometry.bodyTop.toDp() }
    val bottomPad = with(density) { (geometry.height - geometry.bodyBottom).toDp() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = interactionEnabled,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPad, bottom = bottomPad)
                .clipToBounds()
                .pointerInput(interactionEnabled) {
                    if (interactionEnabled) detectTapGestures(onTap = { onTap() })
                },
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(pageItems, key = { "${it.first}:${it.second.index}" }) { (index, page) ->
                val chapterStart = page.index == 0
                val height = with(density) { (page.usedHeight + page.leadingGap).toDp() } +
                    if (chapterStart && index > 0) 28.dp else 0.dp
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .drawBehind {
                            val offsetTop = if (chapterStart && index > 0) 28.dp.toPx() else 0f
                            if (offsetTop > 0f) {
                                drawLine(
                                    color = theme.secondary.copy(alpha = .25f),
                                    start = Offset(geometry.left, offsetTop / 2f),
                                    end = Offset(geometry.right, offsetTop / 2f),
                                    strokeWidth = 1f,
                                )
                            }
                            drawReaderLinesV30(page.lines, geometry.left, page.leadingGap + offsetTop, paints)
                        },
                )
            }
        }
        // Pinned header / footer strips.
        val firstKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
        val visibleChapter = firstKey?.split(':')?.getOrNull(0)?.toIntOrNull() ?: chapterIndex
        val visiblePage = firstKey?.split(':')?.getOrNull(1)?.toIntOrNull() ?: currentPageIndex
        val page = layoutFor(visibleChapter)?.pages?.getOrNull(visiblePage)
        val info = infoFor(page, visibleChapter).let {
            val chapter = chapters.getOrNull(visibleChapter)
            if (chapter != null) it.copy(chapterTitle = readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber)) else it
        }
        Canvas(Modifier.fillMaxWidth().height(topPad).background(theme.page)) {
            drawIntoReaderChromeV30(geometry, theme, paints, info, header = true)
        }
        Canvas(Modifier.fillMaxWidth().height(bottomPad).align(Alignment.BottomCenter).background(theme.page)) {
            drawIntoReaderChromeV30(geometry, theme, paints, info, header = false)
        }
        if (!positioned) Box(Modifier.fillMaxSize().background(theme.page))
    }
}

/** Header or footer strip for scroll mode, drawn with the same metrics as paged mode. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoReaderChromeV30(
    geometry: ReaderGeometryV30,
    theme: ReaderThemeV30,
    paints: ReaderPaintsV30,
    info: ReaderChromeInfoV30,
    header: Boolean,
) {
    // Shift the full-page chrome drawing so that the strip shows the right slice of it.
    val shift = if (header) 0f else -(geometry.height - size.height)
    androidx.compose.ui.graphics.drawscope.translate(top = shift) {
        drawReaderPageV30(
            page = null,
            geometry = geometry,
            theme = theme.copy(page = Color.Transparent),
            paints = paints,
            info = if (header) info.copy(pageLabel = "", progressLabel = "", time = "", showTimeBattery = false) else info.copy(chapterTitle = "", bookmarked = false),
        )
    }
}

private fun readerClockV30(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun readerBatteryV30(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
}
