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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
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
import kotlinx.coroutines.withTimeoutOrNull


internal enum class ReaderMenuTabV30 { DETAILS, DIRECTORY, MORE }

internal enum class ReaderMenuPanelV30 {
    MAIN, THEME, FONT, SIZE, SPACING, TURN, SEARCH, STATS
}

private class ReaderTurnHolderV30 {
    var job: Job? = null
    var end: (() -> Unit)? = null
}


/* -------------------------------------------------------------------------- */
/*                               Reader Engine                                */
/* -------------------------------------------------------------------------- */

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
    val prefs = remember {
        context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE)
    }
    val settings = remember { ReaderSettingsV30(prefs) }
    val theme = readerThemeV30(settings.theme)

    if (startChapter == null) {
        BackHandler { onBackToShelf() }
        Box(
            modifier = Modifier.fillMaxSize().background(theme.page),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = book.title, color = theme.text, fontSize = 18.sp)
                Text(
                    text = "这本书还没有章节",
                    modifier = Modifier.padding(top = 8.dp),
                    color = theme.secondary,
                    fontSize = 13.sp,
                )
                androidx.compose.material3.TextButton(
                    onClick = onBackToShelf,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(text = "返回书架", color = theme.accent)
                }
            }
        }
        return
    }

    var showWritingCopy by rememberSaveable(book.id) { mutableStateOf(false) }
    if (showWritingCopy) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                viewModel.cancelWritingCopy()
                showWritingCopy = false
            },
            title = { Text("从本章开始 AI 创作") },
            text = {
                Column {
                    Text(
                        "把《${startChapter.title}》已读取的正文复制到独立创作项目。" +
                            "原书、目录和阅读进度保留，可在副本中规划、改写或续写。",
                    )
                    if (startChapter.content.isBlank()) {
                        Text(
                            text = if (book.sourceId.isNotBlank()) {
                                "当前章尚未缓存，请先加载正文。"
                            } else {
                                "这一章没有可复制的文字正文，可返回书架创建新作品。"
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    state.readerLoadError?.let {
                        Text(text = it, modifier = Modifier.padding(top = 8.dp))
                    }
                    state.writingCopyError?.let {
                        Text(text = it, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !state.preparingWritingCopy &&
                        state.loadingChapterNumber != startChapter.chapterNumber &&
                        (startChapter.content.isNotBlank() || book.sourceId.isNotBlank()),
                    onClick = {
                        if (startChapter.content.isBlank()) {
                            viewModel.ensureOnlineChapter(startChapter.chapterNumber)
                        } else {
                            viewModel.prepareWritingCopy { id ->
                                showWritingCopy = false
                                onEnterWriting(id)
                            }
                        }
                    },
                ) {
                    Text(
                        when {
                            state.preparingWritingCopy -> "正在创建…"
                            startChapter.content.isBlank() && book.sourceId.isNotBlank() -> "加载当前章"
                            startChapter.content.isBlank() -> "本章暂无文字"
                            else -> "创建副本并进入"
                        },
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.cancelWritingCopy()
                        showWritingCopy = false
                    },
                ) { Text("取消") }
            },
        )
    }

    var storyMode by rememberSaveable(book.id) { mutableStateOf(false) }
    if (storyMode) {
        BackHandler { storyMode = false }
        StoryCleanExperience(
            book = book,
            libraryState = state,
            aiReady = studioState.provider.ready,
            onAiSetup = onOpenAiSetup,
            onAdopted = { viewModel.openBook(book.id) },
            onBack = { storyMode = false },
            onLoadCurrentChapter = { viewModel.ensureOnlineChapter(startChapter.chapterNumber) },
        )
        return
    }

    LaunchedEffect(book.id, startChapter.id) {
        viewModel.ensureOnlineChapter(startChapter.chapterNumber)
    }

    key(book.id) {
        ReaderSessionV30(
            book = book,
            chapters = remember(state.chapters) {
                state.chapters.sortedBy { it.readingOrder }
            },
            externalChapterId = startChapter.id,
            settings = settings,
            startOnInfo = startOnInfo,
            interactionEnabled = interactionEnabled,
            onChapterChanged = { number -> viewModel.openReader(number) },
            onLoadChapter = viewModel::ensureOnlineChapter,
            onRefreshCatalogue = viewModel::refreshOnlineCatalogue,
            refreshingCatalogue = state.isRefreshingCatalogue,
            catalogueMessage = state.catalogueMessage,
            loadingChapterNumber = state.loadingChapterNumber,
            chapterLoadError = state.readerLoadError,
            onBack = onBackToShelf,
            onEdit = { number -> onOpenEditor(book.id, number) },
            onWriting = {
                val imported = book.sourceId.isNotBlank() ||
                    book.genre == "导入作品" ||
                    context
                        .getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE)
                        .contains("imported_${book.id}")
                if (imported) {
                    showWritingCopy = true
                } else {
                    onEnterWriting(book.id)
                }
            },
            onStory = { storyMode = true },
            chapterOps = remember(book.id) {
                ReaderChapterOpsV35(
                    rename = { number, title ->
                        viewModel.renameChapter(book.id, number, title)
                    },
                    append = { viewModel.appendChapter(book.id, "") },
                    deleteLast = { viewModel.deleteLastChapter(book.id) },
                )
            },
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Reader Session                                */
/* -------------------------------------------------------------------------- */

@Composable
internal fun ReaderSessionV30(
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
    chapterOps: ReaderChapterOpsV35 = ReaderChapterOpsV35(),
    onLoadChapter: (Int) -> Unit = {},
    loadingChapterNumber: Int? = null,
    chapterLoadError: String? = null,
    speechFactory: ReaderSpeechFactoryV47 = systemReaderSpeechFactoryV47,
    onRefreshCatalogue: () -> Unit = {},
    refreshingCatalogue: Boolean = false,
    catalogueMessage: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val prefs = remember {
        context.getSharedPreferences("reader_qingmo_v9", Context.MODE_PRIVATE)
    }
    // 阅读正文主题独立于 App 全局浅深色；paper/warm/tea/green/blue/pink/white/night
    // 的实际颜色由 ReaderThemeV30 管理。
    val theme = readerThemeV30(settings.theme)
    val mode = settings.turnMode
    val v3Tokens = LocalLanghuanUiTokens.current

    /* ------------------------------ Position ------------------------------ */
    val initialIndex = remember {
        chapters.indexOfFirst { it.id == externalChapterId }.coerceAtLeast(0)
    }
    val initialProgress = remember {
        ReaderProgressStoreV11.load(
            context,
            book.id,
            chapters.getOrNull(initialIndex)?.chapterNumber ?: 1,
        )
    }
    val initialAnchor = remember {
        val chapter = chapters.getOrNull(initialIndex)
        if (
            chapter != null &&
            initialProgress.chapterNumber == chapter.chapterNumber
        ) {
            readerRestoreBodyOffsetV48(
                chapter.title,
                chapter.content,
                initialProgress.textOffset,
                initialProgress.bodyVersion,
            )
        } else {
            0
        }
    }

    var deferredHeadingAnchor by remember {
        mutableStateOf(
            initialProgress.bodyVersion < 48 &&
                initialProgress.textOffset > 0 &&
                initialProgress.chapterNumber ==
                chapters.getOrNull(initialIndex)?.chapterNumber &&
                chapters.getOrNull(initialIndex)?.content?.isBlank() == true,
        )
    }

    var chapterIndex by remember { mutableIntStateOf(initialIndex) }
    var previousChapterIds by remember { mutableStateOf(chapters.map { it.id }) }
    val chapterIds = chapters.map { it.id }
    if (previousChapterIds != chapterIds) {
        val heldId = previousChapterIds.getOrNull(chapterIndex)
        chapterIndex = chapterIds.indexOf(heldId).takeIf { it >= 0 }
            ?: chapterIndex.coerceAtMost(chapters.lastIndex.coerceAtLeast(0))
        previousChapterIds = chapterIds
    }

    var pageIndex by remember { mutableIntStateOf(0) }
    var pendingAnchor by remember { mutableStateOf<Int?>(initialAnchor) }
    val anchorHolder = remember { intArrayOf(initialAnchor) }

    LaunchedEffect(chapters, chapterIndex) {
        if (deferredHeadingAnchor) {
            if (
                chapters.getOrNull(chapterIndex)?.chapterNumber !=
                initialProgress.chapterNumber
            ) {
                deferredHeadingAnchor = false
            } else {
                chapters.getOrNull(chapterIndex)?.takeIf { it.content.isNotBlank() }
                    ?.let { chapter ->
                        val restored = readerRestoreBodyOffsetV48(
                            chapter.title,
                            chapter.content,
                            initialProgress.textOffset,
                            initialProgress.bodyVersion,
                        )
                        pendingAnchor = restored
                        anchorHolder[0] = restored
                        deferredHeadingAnchor = false
                    }
            }
        }
    }

    val appliedPageHolder = remember { arrayOfNulls<Pair<String, Int>>(1) }

    LaunchedEffect(chapters.size) {
        if (chapters.isNotEmpty() && chapterIndex > chapters.lastIndex) {
            chapterIndex = chapters.lastIndex
            pageIndex = 0
            pendingAnchor = 0
            anchorHolder[0] = 0
        }
    }

    /* ---------------------- Geometry & typography ---------------------- */
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val topInset = maxOf(
        WindowInsets.statusBarsIgnoringVisibility.getTop(density),
        WindowInsets.displayCutout.getTop(density),
    ).toFloat()
    val bottomInset = WindowInsets.navigationBarsIgnoringVisibility
        .getBottom(density).toFloat()

    val geometry = remember(
        viewport, topInset, bottomInset, settings.sidePadding, density.density,
    ) {
        ReaderGeometryV30.of(
            width = viewport.width.toFloat(),
            height = viewport.height.toFloat(),
            density = density.density,
            topInset = topInset,
            bottomInset = bottomInset,
            sidePaddingDp = settings.sidePadding,
        )
    }

    // 默认 18sp / 1.95 行距在 ReaderSettingsV30 中定稿；这里读取用户当前设置。
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
    val paints = remember(spec.key, theme, chromePx) {
        readerPaintsV30(spec, theme, chromePx)
    }

    /* ---------------------------- Layout cache ---------------------------- */
    val layouts = remember { mutableStateMapOf<String, ReaderChapterPagesV30>() }
    val stale = remember { mutableStateMapOf<String, ReaderChapterPagesV30>() }

    fun keyFor(chapter: ChapterDraft): String {
        return "${spec.key}#${chapter.id}#${chapter.readingOrder}" +
            "#${chapter.content.length}#${chapter.content.hashCode()}" +
            "#${chapter.title.hashCode()}"
    }

    fun layoutFor(index: Int): ReaderChapterPagesV30? {
        return chapters.getOrNull(index)?.let { layouts[keyFor(it)] }
    }

    var lastSpecKey by remember { mutableStateOf(spec.key) }
    if (lastSpecKey != spec.key) {
        lastSpecKey = spec.key
        pendingAnchor = pendingAnchor ?: anchorHolder[0]
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
                val body = readerNormalizeBodyV14(
                    readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content),
                )
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
    val waitingForOnlineBody =
        book.sourceId.isNotBlank() &&
            currentChapter?.sourceUrl?.isNotBlank() == true &&
            currentChapter.content.isBlank()
    val currentLayout = layoutFor(chapterIndex)
    val shownLayout = currentLayout ?: currentChapter?.let { stale[it.id] }

    val shownPageIndex = when {
        shownLayout == null -> 0
        currentLayout == null -> shownLayout.pageForOffset(anchorHolder[0])
        pendingAnchor != null -> {
            val anchor = pendingAnchor ?: 0
            if (anchor == Int.MAX_VALUE) currentLayout.pages.lastIndex
            else currentLayout.pageForOffset(anchor)
        }
        else -> pageIndex.coerceIn(0, currentLayout.pages.lastIndex)
    }
    val shownPage = shownLayout?.pages?.getOrNull(shownPageIndex)

    SideEffect {
        if (currentLayout != null && shownPage != null && currentChapter != null) {
            val position = currentChapter.id to shownPageIndex
            val requestedAnchor = pendingAnchor
            if (requestedAnchor != null) {
                anchorHolder[0] = requestedAnchor.coerceIn(0, currentLayout.textLength)
                pageIndex = shownPageIndex
                pendingAnchor = null
            } else if (appliedPageHolder[0] != position) {
                anchorHolder[0] = shownPage.startOffset
            }
            appliedPageHolder[0] = position
        }
    }

    LaunchedEffect(externalChapterId, chapters.size) {
        val index = chapters.indexOfFirst { it.id == externalChapterId }
        if (index >= 0 && index != chapterIndex) {
            chapterIndex = index
            pageIndex = 0
            pendingAnchor = 0
            anchorHolder[0] = 0
        }
    }

    /* ---------------------------- Page chrome ---------------------------- */
    var clock by remember { mutableStateOf(readerClockV30()) }
    var battery by remember { mutableIntStateOf(readerBatteryV30(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = readerClockV30()
            battery = readerBatteryV30(context)
            delay(20_000)
        }
    }

    var bookmarkState by remember(book.id) {
        mutableStateOf(ReaderBookmarkStoreV49.load(prefs, book.id))
    }
    val bookmarks = bookmarkState.getOrDefault(emptySet())
    var bookmarkError by remember(book.id) {
        mutableStateOf(bookmarkState.exceptionOrNull()?.message)
    }
    val legacyBookmarkState = remember { ReaderBookmarkStoreV49.legacy(prefs) }
    val catalogueIncomplete = remember(book.sourceId, chapters) {
        book.sourceId.isNotBlank() && catalogueMiddleGapV53(chapters.map { it.title }, catalogueVolumeTitlesV53(chapters.map { it.title })) != null
    }

    fun infoFor(page: ReaderPageV30?, index: Int): ReaderChromeInfoV30 {
        val chapter = chapters.getOrNull(index)
        val layout = layoutFor(index) ?: chapter?.let { stale[it.id] }
        val count = layout?.pages?.size ?: 0
        val pageNumber = (page?.index ?: 0) + 1
        val bookProgress = if (chapters.isEmpty()) {
            0f
        } else {
            ((index + (if (count > 0) pageNumber.toFloat() / count.toFloat() else 0f)) /
                chapters.size.toFloat() * 100f).coerceIn(0f, 100f)
        }
        val chapterLabel = chapter
            ?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) }
            .orEmpty()
        // v3 顶栏：书名 + 当前章节标题。ReaderRenderV30 只接收一个 chapterTitle
        // 字段，因此这里组合成一条真实 chrome 文案，不新增数据层接口。
        val header = when {
            chapterLabel.isBlank() -> book.title
            book.title.isBlank() -> chapterLabel
            else -> "${book.title} · $chapterLabel"
        }
        return ReaderChromeInfoV30(
            chapterTitle = header,
            pageLabel = if (count > 0) "本章 $pageNumber / $count 页" else "",
            progressLabel = if (catalogueIncomplete) "目录待补全" else "全书 ${bookProgress.roundToInt()}%",
            time = clock,
            battery = battery,
            showTimeBattery = settings.showTimeBattery,
            bookmarked = chapter != null &&
                chapter.chapterNumber.toString() in bookmarks,
        )
    }

    fun persist() {
        if (waitingForOnlineBody) return
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        val layout = layoutFor(chapterIndex)
        val previousLayout = stale[chapter.id]
        val page = if (pendingAnchor == null) {
            layout?.pages?.getOrNull(pageIndex)
        } else {
            null
        }
        val textLength = layout?.textLength ?: readerNormalizeBodyV14(
            readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content),
        ).length
        val position = chapter.id to pageIndex
        val offset = (pendingAnchor ?: when {
            page != null && appliedPageHolder[0] != position -> page.startOffset
            appliedPageHolder[0]?.first != chapter.id -> 0
            else -> anchorHolder[0]
        }).coerceIn(0, textLength)
        val savedPage = page?.index
            ?: (layout ?: previousLayout)?.pageForOffset(offset) ?: 0
        ReaderProgressStoreV11.save(
            context,
            book.id,
            ReaderProgressV11(
                chapterNumber = chapter.chapterNumber,
                pageIndex = savedPage,
                scrollY = 0,
                positionFraction = if (textLength <= 0) 0f
                else offset.toFloat() / textLength.toFloat(),
                textOffset = offset,
                modeKey = mode.key,
            ),
        )
        context
            .getSharedPreferences("reader_progress_v2", Context.MODE_PRIVATE)
            .edit()
            .putInt("total_${book.id}", chapters.size)
            .putInt("index_${book.id}", chapterIndex)
            .apply()
    }

    /* ---------------------------- Navigation ---------------------------- */
    fun neighbor(delta: Int): ReaderPageV30? {
        val layout = layoutFor(chapterIndex) ?: return null
        if (pendingAnchor != null) return null
        val target = pageIndex + delta
        if (target in layout.pages.indices) return layout.pages[target]
        return if (delta > 0) {
            layoutFor(chapterIndex + 1)?.pages?.firstOrNull()
        } else {
            layoutFor(chapterIndex - 1)?.pages?.lastOrNull()
        }
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
        val beforePage = pageIndex
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
        if (chapterIndex == before && pageIndex == beforePage) return
        layoutFor(chapterIndex)?.pages?.getOrNull(pageIndex)?.let {
            anchorHolder[0] = it.startOffset
            if (pendingAnchor != null) pendingAnchor = it.startOffset
        }
        if (chapterIndex != before) {
            chapters.getOrNull(chapterIndex)?.let { onChapterChanged(it.chapterNumber) }
        }
        ReaderStatsV35.addPage(context)
        if (chapterIndex == before + 1) {
            chapters.getOrNull(before)?.let {
                ReaderStatsV35.markChapterFinished(context, book.id, it.chapterNumber)
            }
        }
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

    /* ----------------------- Page turn animation ----------------------- */
    var turnDir by remember { mutableIntStateOf(0) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val turn = remember { ReaderTurnHolderV30() }
    var edgeHint by remember { mutableStateOf<String?>(null) }

    fun applyBookmarkResult(
        result: Result<Set<String>>,
        successMessage: String? = null,
    ) {
        result
            .onSuccess {
                bookmarkState = Result.success(it)
                bookmarkError = null
                if (successMessage != null) edgeHint = successMessage
            }
            .onFailure {
                bookmarkError = it.message ?: "书签保存失败，未修改原数据"
                edgeHint = bookmarkError
            }
    }

    LaunchedEffect(bookmarkError) {
        if (bookmarkError != null) edgeHint = bookmarkError
    }

    /* ------------------- Selection / Notes / Highlights ------------------- */
    var selection by remember { mutableStateOf<ReaderSelectionV30?>(null) }
    var noteEditorFor by remember { mutableStateOf<ReaderSelectionV30?>(null) }
    // v3 修正：不再在点击「划线」后直接保存默认颜色；先保留选区，
    // 然后打开 ReaderParagraphHighlightPickerV50。
    var highlightPickerFor by remember { mutableStateOf<ReaderSelectionV30?>(null) }
    var highlightRevision by remember { mutableIntStateOf(0) }

    val v3Highlights = remember(book.id, highlightRevision) {
        ReaderParagraphHighlightStoreV50.load(prefs, book.id).getOrElse { emptyList() }
    }

    val selectionAlpha = remember {
        androidx.compose.animation.core.Animatable(0f)
    }
    LaunchedEffect(selection) {
        if (selection != null) selectionAlpha.animateTo(1f, tween(160))
        else selectionAlpha.snapTo(0f)
    }
    LaunchedEffect(chapterIndex, pageIndex, mode) { selection = null }

    /* --------------------------- Turn helpers --------------------------- */
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
                animationSpec = tween(
                    durationMs,
                    easing = if (mode == ReaderTurnModeV30.SIMULATION) {
                        LinearOutSlowInEasing
                    } else {
                        FastOutSlowInEasing
                    },
                ),
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

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun showEdge(delta: Int) {
        if (chapters.getOrNull(chapterIndex + delta) == null) {
            haptics.performHapticFeedback(
                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
            )
        }
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
        runTurn(
            target = if (delta > 0) -width else width,
            durationMs = baseDuration,
        ) { commitAndReset(delta) }
    }

    /* -------------------------------- Menu -------------------------------- */
    var menuVisible by remember { mutableStateOf(startOnInfo) }
    var menuTab by remember {
        mutableStateOf(
            if (startOnInfo) ReaderMenuTabV30.DETAILS else ReaderMenuTabV30.MORE,
        )
    }
    var menuPanel by remember { mutableStateOf(ReaderMenuPanelV30.MAIN) }

    fun openMenu() {
        finishTurnNow()
        menuPanel = ReaderMenuPanelV30.MAIN
        menuVisible = true
    }

    fun selectAt(point: Offset) {
        if (pendingAnchor != null) return
        val page = layoutFor(chapterIndex)?.pages?.getOrNull(pageIndex) ?: return
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        val body = readerNormalizeBodyV14(
            readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content),
        )
        val picked = readerParagraphAtV30(page, point, geometry, body) ?: return
        haptics.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
        )
        selection = picked
    }

    fun tapAt(point: Offset) {
        if (selection != null) {
            selection = null
            return
        }
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

    /* ----------------------- Persistence / stats ----------------------- */
    LaunchedEffect(chapterIndex, pageIndex, pendingAnchor, waitingForOnlineBody, currentLayout) {
        delay(260)
        persist()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(book.id) {
        while (true) {
            delay(30_000)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                ReaderStatsV35.addSeconds(context, 30)
            }
        }
    }
    /* -------------------------------- TTS -------------------------------- */
    var listening by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableFloatStateOf(prefs.getFloat("tts_rate", 1f)) }
    var ttsFollowPage by remember { mutableIntStateOf(-1) }
    var ttsAdvancing by remember { mutableStateOf(false) }
    val ttsHolder = remember { arrayOfNulls<ReaderSpeechV47>(1) }
    val ttsEngineGeneration = remember { intArrayOf(0) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsChapterId by remember { mutableStateOf<String?>(null) }

    fun stopListening() {
        listening = false
        ttsAdvancing = false
        ttsHolder[0]?.stop()
    }

    fun failListening() {
        stopListening()
        edgeHint = "朗读失败，已停止；请点听书重试"
    }

    val ttsSleepTimerV50 = remember {
        ReaderTtsSleepTimerV50(
            prefs = prefs,
            stopPlayback = { stopListening() },
            onExpired = { edgeHint = "定时已到，已停止朗读" },
        )
    }

    DisposableEffect(ttsSleepTimerV50) {
        onDispose { ttsSleepTimerV50.release() }
    }

    var ttsTimerPanelOpen by remember { mutableStateOf(false) }

    fun ttsStartFromPage() {
        if (!listening || !ttsReady) return
        val tts = ttsHolder[0] ?: return
        val chapter = chapters.getOrNull(chapterIndex) ?: return
        if (waitingForOnlineBody || layoutFor(chapterIndex) == null) {
            ttsAdvancing = true
            if (
                waitingForOnlineBody &&
                loadingChapterNumber != chapter.chapterNumber &&
                chapterLoadError == null
            ) {
                onLoadChapter(chapter.chapterNumber)
            }
            return
        }
        val body = readerNormalizeBodyV14(
            readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content),
        )
        val from = layoutFor(chapterIndex)?.pages?.getOrNull(pageIndex)?.startOffset ?: 0
        ttsAdvancing = false
        ttsFollowPage = pageIndex
        ttsChapterId = chapter.id
        if (!tts.speak(readerTtsChunksV35(body, from))) failListening()
    }

    val speechReady = rememberUpdatedState<(Boolean) -> Unit> { ok ->
        ttsReady = ok
        if (!ok) {
            if (listening) edgeHint = "朗读引擎不可用，请安装中文语音后点听书重试"
            stopListening()
            ttsEngineGeneration[0]++
            ttsHolder[0]?.release()
            ttsHolder[0] = null
        } else if (listening) {
            ttsHolder[0]?.rate = ttsRate
            ttsStartFromPage()
        }
    }

    val speechChunk = rememberUpdatedState<(Int) -> Unit> { offset ->
        // V50 段落进度记忆：offset 就是 ReaderTtsChunkV35.offset，不另造段落 ID。
        if (listening) {
            ttsSleepTimerV50.rememberParagraph(
                bookId = book.id,
                chapterIndex = chapterIndex,
                paragraphOffset = offset,
            )
        }
        val layout = layoutFor(chapterIndex)
        if (
            listening &&
            currentChapter?.id == ttsChapterId &&
            !waitingForOnlineBody &&
            layout != null
        ) {
            val target = layout.pageForOffset(offset)
            if (target != pageIndex) {
                ttsFollowPage = target
                anchorHolder[0] = offset
                pendingAnchor = offset
                pageIndex = target
                if (mode == ReaderTurnModeV30.SCROLL) scrollJump++
            }
        }
    }

    val speechDone = rememberUpdatedState<() -> Unit> {
        when {
            !listening || currentChapter?.id != ttsChapterId -> Unit
            waitingForOnlineBody -> ttsAdvancing = true
            else -> {
                chapters.getOrNull(chapterIndex)?.let {
                    ReaderStatsV35.markChapterFinished(context, book.id, it.chapterNumber)
                }
                val stopAtChapterEnd = ttsSleepTimerV50.active &&
                    ttsSleepTimerV50.option == ReaderTtsSleepOptionV50.END_OF_CHAPTER
                // END_OF_CHAPTER 会在这里真正 stopListening()。
                ttsSleepTimerV50.onChapterFinished()
                if (stopAtChapterEnd) {
                    edgeHint = "已在本章结束后停止朗读"
                } else {
                    val next = chapters.getOrNull(chapterIndex + 1)
                    if (next == null) {
                        stopListening()
                        edgeHint = "已读到最后一章"
                    } else {
                        ttsAdvancing = true
                        anchorHolder[0] = 0
                        pendingAnchor = 0
                        chapterIndex += 1
                        pageIndex = 0
                        onChapterChanged(next.chapterNumber)
                    }
                }
            }
        }
    }

    val speechError = rememberUpdatedState<() -> Unit> {
        if (listening && currentChapter?.id == ttsChapterId) failListening()
    }

    fun startListening() {
        listening = true
        if (ttsHolder[0] != null) {
            ttsStartFromPage()
            return
        }
        ttsReady = false
        val engineGeneration = ++ttsEngineGeneration[0]
        ttsHolder[0] = speechFactory.create(
            context,
            { if (engineGeneration == ttsEngineGeneration[0]) speechReady.value(it) },
            { if (engineGeneration == ttsEngineGeneration[0]) speechChunk.value(it) },
            { if (engineGeneration == ttsEngineGeneration[0]) speechDone.value() },
            { if (engineGeneration == ttsEngineGeneration[0]) speechError.value() },
        )
    }

    LaunchedEffect(
        ttsAdvancing, listening, chapterIndex, currentLayout,
        waitingForOnlineBody, loadingChapterNumber, chapterLoadError,
    ) {
        when (
            readerSpeechResumeV47(
                advancing = ttsAdvancing,
                listening = listening,
                awaitingOnlineBody = waitingForOnlineBody,
                hasLoadError = chapterLoadError != null &&
                    loadingChapterNumber != currentChapter?.chapterNumber,
                hasLayout = currentLayout != null,
            )
        ) {
            ReaderSpeechResumeV47.START -> {
                ttsAdvancing = false
                ttsStartFromPage()
            }
            ReaderSpeechResumeV47.PAUSE_FOR_ERROR -> {
                ttsHolder[0]?.stop()
                edgeHint = "本章加载失败，朗读已暂停；重试后可继续"
            }
            else -> Unit
        }
    }

    LaunchedEffect(chapterIndex, pageIndex) {
        if (
            listening && !ttsAdvancing &&
            (currentChapter?.id != ttsChapterId || pageIndex != ttsFollowPage)
        ) {
            ttsHolder[0]?.stop()
            ttsStartFromPage()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            listening = false
            ttsAdvancing = false
            ttsReady = false
            ttsEngineGeneration[0]++
            ttsHolder[0]?.release()
            ttsHolder[0] = null
        }
    }

    /* ---------------------- Lifecycle / settings ---------------------- */
    val latestPersist = rememberUpdatedState {
        finishTurnNow()
        persist()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) latestPersist.value()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestPersist.value()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { settings.snapshot() }
            .distinctUntilChanged()
            .collect { settings.save() }
    }

    LaunchedEffect(edgeHint) {
        if (edgeHint != null) {
            delay(1_400)
            edgeHint = null
        }
    }

    LaunchedEffect(mode) {
        finishTurnNow()
        resetTurn()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(settings.volumeTurn, menuVisible) {
        if (settings.volumeTurn && !menuVisible) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    BackHandler {
        when {
            menuVisible && menuPanel != ReaderMenuPanelV30.MAIN -> {
                menuPanel = ReaderMenuPanelV30.MAIN
            }
            menuVisible -> menuVisible = false
            else -> {
                persist()
                onBack()
            }
        }
    }

    val listState = rememberLazyListState()
    /* ------------------------------- Canvas ------------------------------- */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.page)
            .onSizeChanged { viewport = it }
            .semantics {
                contentDescription = "阅读正文"
                stateDescription = when {
                    !interactionEnabled -> "正在恢复阅读"
                    waitingForOnlineBody -> "正在加载在线正文"
                    currentLayout == null || pendingAnchor != null -> "正在排版"
                    else -> "第${chapterIndex + 1}章，" +
                        "本章第${shownPageIndex + 1}/" +
                        "${currentLayout.pages.size}页"
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (
                    !interactionEnabled || waitingForOnlineBody ||
                    !settings.volumeTurn || menuVisible ||
                    event.type != KeyEventType.KeyDown
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.VolumeUp -> {
                        if (mode == ReaderTurnModeV30.SCROLL) {
                            scope.launch {
                                listState.animateScrollBy(-geometry.bodyHeight * 0.9f)
                            }
                        } else {
                            turnPage(-1)
                        }
                        true
                    }
                    Key.VolumeDown -> {
                        if (mode == ReaderTurnModeV30.SCROLL) {
                            scope.launch {
                                listState.animateScrollBy(geometry.bodyHeight * 0.9f)
                            }
                        } else {
                            turnPage(1)
                        }
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
                layoutToken = spec.key,
                interactionEnabled = interactionEnabled && !waitingForOnlineBody,
                onVisible = { index, page ->
                    if (pendingAnchor == null) {
                        val changed = index != chapterIndex
                        if (changed || page != pageIndex) {
                            anchorHolder[0] = layoutFor(index)
                                ?.pages?.getOrNull(page)?.startOffset ?: 0
                        }
                        chapterIndex = index
                        pageIndex = page
                        if (changed) {
                            chapters.getOrNull(index)?.let {
                                onChapterChanged(it.chapterNumber)
                            }
                        }
                    }
                },
                onTap = { openMenu() },
            )
        } else {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        interactionEnabled,
                        waitingForOnlineBody,
                        spec.key,
                        chapters,
                        mode,
                        settings.fullNext,
                        settings.clickAnimation,
                        geometry,
                    ) {
                        if (!interactionEnabled || waitingForOnlineBody) {
                            return@pointerInput
                        }
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
                            var longPressed = false
                            val width = size.width.toFloat()
                            val longPressAt =
                                down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                            var lastTime = down.uptimeMillis

                            while (true) {
                                val waitingForHold = !dragging && !blocked && !longPressed
                                val event = if (waitingForHold) {
                                    withTimeoutOrNull(
                                        (longPressAt - lastTime).coerceAtLeast(1L),
                                    ) {
                                        awaitPointerEvent()
                                    }
                                } else {
                                    awaitPointerEvent()
                                }
                                if (event == null) {
                                    longPressed = true
                                    selectAt(down.position)
                                    continue
                                }
                                if (longPressed) {
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.all { !it.pressed }) break
                                    continue
                                }
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: break
                                lastTime = change.uptimeMillis
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (change.changedToUpIgnoreConsumed()) {
                                    released = true
                                    break
                                }
                                val delta = change.positionChangeIgnoreConsumed()
                                totalX += delta.x
                                totalY += delta.y
                                if (!dragging && !blocked) {
                                    if (selection != null &&
                                        (abs(totalX) > slop || abs(totalY) > slop)
                                    ) {
                                        selection = null
                                        blocked = true
                                    } else if (
                                        abs(totalX) > slop &&
                                        abs(totalX) > abs(totalY) * 0.8f
                                    ) {
                                        direction = if (totalX < 0f) 1 else -1
                                        if (neighbor(direction) != null) {
                                            dragging = true
                                            turnDir = direction
                                            dragX = 0f
                                        } else {
                                            blocked = true
                                            showEdge(direction)
                                        }
                                    } else if (
                                        abs(totalY) > slop * 2f && abs(totalY) > abs(totalX)
                                    ) {
                                        blocked = true
                                    }
                                }
                                if (dragging) {
                                    change.consume()
                                    dragX = if (direction > 0) {
                                        (dragX + delta.x).coerceIn(-width, 0f)
                                    } else {
                                        (dragX + delta.x).coerceIn(0f, width)
                                    }
                                }
                            }

                            if (dragging) {
                                val velocity = tracker.calculateVelocity().x
                                val threshold = width * 0.16f
                                val shouldCommit = if (direction > 0) {
                                    velocity < -flingVelocity ||
                                        (dragX < -threshold && velocity < flingVelocity)
                                } else {
                                    velocity > flingVelocity ||
                                        (dragX > threshold && velocity > -flingVelocity)
                                }
                                if (mode == ReaderTurnModeV30.NONE) {
                                    if (shouldCommit) commitAndReset(direction)
                                    else resetTurn()
                                } else {
                                    val target = if (!shouldCommit) {
                                        0f
                                    } else if (direction > 0) {
                                        -width
                                    } else {
                                        width
                                    }
                                    val remaining = abs(target - dragX) /
                                        width.coerceAtLeast(1f)
                                    val duration = (baseDuration * remaining)
                                        .roundToInt().coerceIn(120, baseDuration)
                                    runTurn(target, duration) {
                                        if (shouldCommit) commitAndReset(direction)
                                        else resetTurn()
                                    }
                                }
                            } else if (
                                released && !blocked && !longPressed &&
                                abs(totalX) <= slop && abs(totalY) <= slop
                            ) {
                                tapAt(down.position)
                            }
                        }
                    }
                    .drawBehind {
                        val current = shownPage
                        val direction = turnDir
                        if (current == null || direction == 0) {
                            drawReaderPageV30(
                                page = current,
                                geometry = geometry,
                                theme = theme,
                                paints = paints,
                                info = infoFor(current, chapterIndex),
                                placeholder = currentChapter?.let {
                                    readerDisplayChapterTitleV13(
                                        it.title, it.chapterNumber,
                                    )
                                },
                            )
                            val picked = selection
                            if (
                                picked != null && current != null &&
                                picked.chapterIndex == current.chapterIndex &&
                                picked.pageIndex == current.index
                            ) {
                                drawReaderSelectionV30(
                                    picked, geometry, theme, selectionAlpha.value,
                                )
                            }
                            if (current != null) {
                                v3Highlights.forEach { highlight ->
                                    readerParagraphHighlightBandV50(
                                        current, geometry, highlight,
                                    )?.let { band ->
                                        drawReaderParagraphHighlightV50(
                                            band, geometry, v3Tokens,
                                        )
                                    }
                                }
                            }
                            return@drawBehind
                        }
                        val w = size.width.coerceAtLeast(1f)
                        if (direction > 0) {
                            val next = neighbor(1)
                            val nextChapter = neighborChapterIndex(1)
                            drawReaderTurnV30(
                                mode = mode,
                                progress = -dragX / w,
                                drawTop = {
                                    drawReaderPageV30(
                                        current, geometry, theme, paints,
                                        infoFor(current, chapterIndex),
                                    )
                                },
                                drawBottom = {
                                    drawReaderPageV30(
                                        next, geometry, theme, paints,
                                        infoFor(next, nextChapter),
                                    )
                                },
                                theme = theme,
                            )
                        } else {
                            val previous = neighbor(-1)
                            val previousChapter = neighborChapterIndex(-1)
                            drawReaderTurnV30(
                                mode = mode,
                                progress = 1f - dragX / w,
                                drawTop = {
                                    drawReaderPageV30(
                                        previous, geometry, theme, paints,
                                        infoFor(previous, previousChapter),
                                    )
                                },
                                drawBottom = {
                                    drawReaderPageV30(
                                        current, geometry, theme, paints,
                                        infoFor(current, chapterIndex),
                                    )
                                },
                                theme = theme,
                            )
                        }
                    },
            )
        }

        /* ------------------------------- Loading ------------------------------- */
        AnimatedVisibility(
            visible = shownLayout == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentChapter?.let {
                        readerDisplayChapterTitleV13(it.title, it.chapterNumber)
                    } ?: book.title,
                    modifier = Modifier.padding(horizontal = 40.dp),
                    color = theme.text,
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = book.title,
                    modifier = Modifier.padding(top = 6.dp),
                    color = theme.secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(22.dp))
                LinearProgressIndicator(
                    modifier = Modifier.width(96.dp).height(2.dp),
                    color = theme.accent,
                    trackColor = theme.secondary.copy(alpha = 0.18f),
                )
            }
        }

        if (waitingForOnlineBody && !menuVisible) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (
                        loadingChapterNumber == currentChapter?.chapterNumber
                    ) {
                        "正在加载本章…"
                    } else {
                        chapterLoadError ?: "本章尚未缓存"
                    },
                    color = theme.secondary,
                    fontSize = 14.sp,
                )
                if (loadingChapterNumber != currentChapter?.chapterNumber) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            currentChapter?.let { onLoadChapter(it.chapterNumber) }
                        },
                    ) {
                        Text(text = "重试加载", color = theme.accent)
                    }
                }
                androidx.compose.material3.TextButton(onClick = { openMenu() }) {
                    Text(text = "打开目录", color = theme.accent)
                }
            }
        }

        /* ------------------------------- Feedback ------------------------------- */
        AnimatedVisibility(
            visible = edgeHint != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
            enter = fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f),
        ) {
            val shape = RoundedCornerShape(v3Tokens.radiusLg)
            Box(
                modifier = Modifier
                    .background(color = v3Tokens.card, shape = shape)
                    .border(width = 1.dp, color = v3Tokens.border, shape = shape)
                    .padding(
                        horizontal = v3Tokens.space4,
                        vertical = v3Tokens.space2,
                    ),
            ) {
                Text(text = edgeHint.orEmpty(), color = v3Tokens.foreground, fontSize = 13.sp)
            }
        }

        /* ------------------------------ TTS mini player ------------------------------ */
        AnimatedVisibility(
            visible = listening && !menuVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(bottom = 34.dp),
            enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + fadeOut(),
        ) {
            val shape = RoundedCornerShape(v3Tokens.radiusXl)
            Row(
                modifier = Modifier
                    .background(color = v3Tokens.card, shape = shape)
                    .border(width = 1.dp, color = v3Tokens.border, shape = shape)
                    .padding(
                        start = v3Tokens.space4, end = v3Tokens.space2,
                        top = v3Tokens.space2, bottom = v3Tokens.space2,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.xiguli.langhuan.ui.design.LanghuanTypingDotsV31(
                    color = v3Tokens.primary,
                    dot = 5.dp,
                )
                Text(
                    text = if (waitingForOnlineBody) {
                        if (chapterLoadError != null && loadingChapterNumber == null) {
                            "朗读已暂停"
                        } else {
                            "等待本章正文"
                        }
                    } else {
                        "正在朗读"
                    },
                    modifier = Modifier.padding(
                        start = v3Tokens.space2,
                        end = v3Tokens.space3,
                    ),
                    color = v3Tokens.foreground,
                    fontSize = 13.sp,
                )
                Text(
                    text = "${ttsRate}x",
                    modifier = Modifier
                        .clip(RoundedCornerShape(v3Tokens.radiusSm))
                        .clickable {
                            val rates = listOf(0.8f, 1f, 1.25f, 1.5f, 2f)
                            ttsRate = rates[
                                (rates.indexOf(ttsRate).coerceAtLeast(0) + 1) % rates.size
                            ]
                            prefs.edit().putFloat("tts_rate", ttsRate).apply()
                            ttsHolder[0]?.rate = ttsRate
                        }
                        .padding(
                            horizontal = v3Tokens.space2,
                            vertical = v3Tokens.space1,
                        ),
                    color = v3Tokens.primary,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                IconButton(
                    onClick = { stopListening() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "停止朗读",
                        tint = v3Tokens.secondaryForeground,
                    )
                }
                ReaderTtsSleepTimerChipV50(
                    timer = ttsSleepTimerV50,
                    onClick = { ttsTimerPanelOpen = true },
                )
            }
        }

        /* ----------------------------- TTS sleep timer ----------------------------- */
        if (ttsTimerPanelOpen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { ttsTimerPanelOpen = false },
            ) {
                val shape = RoundedCornerShape(v3Tokens.radiusXl)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = v3Tokens.card, shape = shape)
                        .border(width = 1.dp, color = v3Tokens.border, shape = shape)
                        .padding(v3Tokens.space4),
                ) {
                    ReaderTtsSleepTimerPanelV50(timer = ttsSleepTimerV50)
                }
            }
        }
        /* --------------------------- Long-press selection --------------------------- */
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        selection
            ?.takeIf {
                !menuVisible && mode != ReaderTurnModeV30.SCROLL
            }
            ?.let { picked ->
                ReaderSelectionBarV30(
                    selection = picked,
                    geometry = geometry,
                    theme = theme,
                    onCopy = {
                        clipboard.setText(
                            androidx.compose.ui.text.AnnotatedString(picked.text),
                        )
                        selection = null
                        edgeHint = "已复制这一段"
                    },
                    onShare = {
                        selection = null
                        runCatching {
                            context.startActivity(
                                readerShareIntentV30(picked.text, book.title),
                            )
                        }
                    },
                    onSearch = {
                        selection = null
                        runCatching {
                            context.startActivity(readerSearchIntentV30(picked.text))
                        }.onFailure {
                            edgeHint = "没有可用的搜索应用"
                        }
                    },
                    onBookmark = {
                        val number = chapters.getOrNull(picked.chapterIndex)?.chapterNumber
                        if (number != null) {
                            applyBookmarkResult(
                                ReaderBookmarkStoreV49.add(prefs, book.id, number),
                                "已加入本书书签",
                            )
                        }
                        selection = null
                    },
                    onNote = {
                        noteEditorFor = picked
                        selection = null
                    },
                    onHighlight = {
                        // v3：不直接保存默认颜色，打开玉青/赤金选择器。
                        highlightPickerFor = picked
                        selection = null
                    },
                    onDismiss = { selection = null },
                )
            }

        /* ----------------------------- Paragraph note ----------------------------- */
        noteEditorFor?.let { target ->
            val chapterTitle = chapters
                .getOrNull(target.chapterIndex)
                ?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) }
                ?: ""
            ReaderParagraphNoteEditorV50(
                prefs = prefs,
                bookId = book.id,
                selection = target,
                chapterTitle = chapterTitle,
                onDismiss = { noteEditorFor = null },
                onSaved = {
                    noteEditorFor = null
                    edgeHint = "笔记已保存"
                },
            )
        }

        /* ------------------------ Paragraph highlight picker ------------------------ */
        highlightPickerFor?.let { target ->
            val chapterTitle = chapters
                .getOrNull(target.chapterIndex)
                ?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) }
                ?: ""
            ReaderParagraphHighlightPickerV50(
                prefs = prefs,
                bookId = book.id,
                selection = target,
                chapterTitle = chapterTitle,
                onDismiss = { highlightPickerFor = null },
                onSaved = {
                    highlightRevision++
                    highlightPickerFor = null
                    edgeHint = "划线已保存"
                },
                onDeleted = {
                    highlightRevision++
                    highlightPickerFor = null
                    edgeHint = "已取消划线"
                },
            )
        }

        /* ------------------------------- Reader menu ------------------------------- */
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
                val number = currentChapter?.chapterNumber
                if (number != null) {
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                    )
                    applyBookmarkResult(
                        ReaderBookmarkStoreV49.setMarked(
                            prefs, book.id, number,
                            number.toString() !in bookmarks,
                        ),
                    )
                }
            },
            onJumpChapter = { index, anchor ->
                menuVisible = false
                jumpTo(index, anchor)
            },
            onPageFraction = { fraction ->
                val layout = layoutFor(chapterIndex)
                if (layout != null) {
                    val target = (fraction * layout.pages.lastIndex)
                        .roundToInt().coerceIn(0, layout.pages.lastIndex)
                    anchorHolder[0] = layout.pages[target].startOffset
                    pageIndex = target
                    pendingAnchor = null
                    if (mode == ReaderTurnModeV30.SCROLL) scrollJump++
                }
            },
            onEdit = { currentChapter?.let { onEdit(it.chapterNumber) } },
            onWriting = onWriting,
            onStory = onStory,
            bookmarkedChapters = bookmarks.mapNotNull { it.toIntOrNull() }.toSet(),
            bookmarkError = bookmarkError,
            legacyBookmarkedChapters = legacyBookmarkState
                .getOrDefault(emptySet()).mapNotNull { it.toIntOrNull() }.toSet(),
            legacyBookmarkError = legacyBookmarkState.exceptionOrNull()?.message,
            onRestoreLegacyBookmark = { number ->
                applyBookmarkResult(
                    ReaderBookmarkStoreV49.restoreLegacy(
                        prefs, book.id, number,
                        chapters.map { it.chapterNumber }.toSet(),
                    ),
                    "已归入本书，旧版暂存仍保留",
                )
            },
            listening = listening,
            onListen = {
                if (listening) stopListening()
                else {
                    menuVisible = false
                    startListening()
                }
            },
            onRenameChapter = chapterOps.rename,
            onAppendChapter = chapterOps.append,
            onDeleteLastChapter = chapterOps.deleteLast,
            onRefreshCatalogue = onRefreshCatalogue,
            refreshingCatalogue = refreshingCatalogue,
            catalogueMessage = catalogueMessage,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                           Continuous scroll                               */
/* -------------------------------------------------------------------------- */

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
    layoutToken: String,
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

    LaunchedEffect(jumpToken, layoutToken) {
        positioned = false
        val target = snapshotFlow {
            val (chapter, page) = targetState.value
            itemsState.value.indexOfFirst {
                it.first == chapter && it.second.index == page
            }
        }.first { it >= 0 }
        listState.scrollToItem(target)
        positioned = true
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to positioned }
            .distinctUntilChanged()
            .collect { (_, ready) ->
                if (!ready) return@collect
                val key = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as?
                    String ?: return@collect
                val parts = key.split(':')
                val chapter = parts.getOrNull(0)?.toIntOrNull() ?: return@collect
                val page = parts.getOrNull(1)?.toIntOrNull() ?: return@collect
                visibleCallback.value(chapter, page)
            }
    }

    val topPad = with(density) { geometry.bodyTop.toDp() }
    val bottomPad = with(density) { (geometry.height - geometry.bodyBottom).toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            userScrollEnabled = interactionEnabled,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPad, bottom = bottomPad)
                .clipToBounds()
                .pointerInput(interactionEnabled) {
                    if (interactionEnabled) {
                        detectTapGestures(onTap = { onTap() })
                    }
                },
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(
                items = pageItems,
                key = { "${it.first}:${it.second.index}" },
            ) { (index, page) ->
                val chapterStart = page.index == 0
                val height = with(density) {
                    (page.usedHeight + page.leadingGap).toDp()
                } + if (chapterStart && index > 0) 28.dp else 0.dp
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .drawBehind {
                            val offsetTop = if (chapterStart && index > 0) {
                                28.dp.toPx()
                            } else {
                                0f
                            }
                            if (offsetTop > 0f) {
                                drawLine(
                                    color = theme.secondary.copy(alpha = 0.25f),
                                    start = Offset(geometry.left, offsetTop / 2f),
                                    end = Offset(geometry.right, offsetTop / 2f),
                                    strokeWidth = 1f,
                                )
                            }
                            drawReaderLinesV30(
                                lines = page.lines,
                                originX = geometry.left,
                                originY = page.leadingGap + offsetTop,
                                paints = paints,
                            )
                        },
                )
            }
        }

        // 上下滚动模式仍固定显示同一套：书名·章节 / 本章 x/y 页 / 全书 xx% / 时间电量
        val visibleScrollLocation by remember(listState, chapterIndex, currentPageIndex) {
            derivedStateOf {
                val parts = (listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String)
                    ?.split(':')
                val visibleChapter = parts?.getOrNull(0)?.toIntOrNull() ?: chapterIndex
                val visiblePage = parts?.getOrNull(1)?.toIntOrNull() ?: currentPageIndex
                visibleChapter to visiblePage
            }
        }
        val (visibleChapter, visiblePage) = visibleScrollLocation
        val page = layoutFor(visibleChapter)?.pages?.getOrNull(visiblePage)
        val info = infoFor(page, visibleChapter)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(topPad)
                .background(theme.page),
        ) {
            drawIntoReaderChromeV30(
                geometry = geometry, theme = theme, paints = paints,
                info = info, header = true,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomPad)
                .align(Alignment.BottomCenter)
                .background(theme.page),
        ) {
            drawIntoReaderChromeV30(
                geometry = geometry, theme = theme, paints = paints,
                info = info, header = false,
            )
        }
        if (!positioned) {
            Box(modifier = Modifier.fillMaxSize().background(theme.page))
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Chrome slices                                  */
/* -------------------------------------------------------------------------- */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoReaderChromeV30(
    geometry: ReaderGeometryV30,
    theme: ReaderThemeV30,
    paints: ReaderPaintsV30,
    info: ReaderChromeInfoV30,
    header: Boolean,
) {
    val shift = if (header) 0f else -(geometry.height - size.height)
    withTransform(transformBlock = { translate(0f, shift) }) {
        drawReaderPageV30(
            page = null,
            geometry = geometry,
            theme = theme.copy(page = Color.Transparent),
            paints = paints,
            info = if (header) {
                info.copy(
                    pageLabel = "", progressLabel = "", time = "",
                    showTimeBattery = false,
                )
            } else {
                info.copy(chapterTitle = "", bookmarked = false)
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Helpers                                    */
/* -------------------------------------------------------------------------- */

private fun readerClockV30(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun readerBatteryV30(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return manager
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
        ?: 100
}

/**
 * Chapter-management callbacks handed from the engine to ReaderSessionV30.
 */
internal class ReaderChapterOpsV35(
    val rename: (Int, String) -> Unit = { _, _ -> Unit },
    val append: () -> Unit = {},
    val deleteLast: () -> Unit = {},
)
