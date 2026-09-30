package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.rememberLanghuanCoverV30
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReaderMenuActionV30(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
internal fun ReaderMenuV30(
    visible: Boolean,
    tab: ReaderMenuTabV30,
    panel: ReaderMenuPanelV30,
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int,
    settings: ReaderSettingsV30,
    theme: ReaderThemeV30,
    bookmarked: Boolean,
    onDismiss: () -> Unit,
    onTab: (ReaderMenuTabV30) -> Unit,
    onPanel: (ReaderMenuPanelV30) -> Unit,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onJumpChapter: (Int, Int) -> Unit,
    onPageFraction: (Float) -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
    bookmarkedChapters: Set<Int> = emptySet(),
    onRenameChapter: (Int, String) -> Unit = { _, _ -> },
    onAppendChapter: () -> Unit = {},
    onDeleteLastChapter: () -> Unit = {},
    listening: Boolean = false,
    onListen: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (theme.dark) .34f else .10f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .88f, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
        ) {
            // Drag the handle strip down to dismiss; a short drag springs back.
            val dragY = remember { Animatable(0f) }
            val dragScope = rememberCoroutineScope()
            val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }
            LaunchedEffect(visible) { if (visible) dragY.snapTo(0f) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .offset { IntOffset(0, dragY.value.roundToInt()) },
                color = theme.sheet,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                shadowElevation = 16.dp,
            ) {
                Column(Modifier.navigationBarsPadding()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        dragScope.launch {
                                            if (dragY.value > dismissPx) onDismiss()
                                            else dragY.animateTo(0f, spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium))
                                        }
                                    },
                                    onDragCancel = { dragScope.launch { dragY.animateTo(0f) } },
                                ) { change, amount ->
                                    change.consume()
                                    dragScope.launch { dragY.snapTo((dragY.value + amount).coerceAtLeast(0f)) }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(theme.sheetMuted.copy(alpha = .35f)),
                        )
                    }
                    AnimatedContent(
                        targetState = panel to tab,
                        transitionSpec = {
                            val (fromPanel, _) = initialState
                            val (toPanel, _) = targetState
                            val base = when {
                                fromPanel == ReaderMenuPanelV30.MAIN && toPanel != ReaderMenuPanelV30.MAIN ->
                                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(140)))
                                fromPanel != ReaderMenuPanelV30.MAIN && toPanel == ReaderMenuPanelV30.MAIN ->
                                    (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(200))) togetherWith
                                        (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(140)))
                                else -> fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            }
                            base using SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) }
                        },
                        label = "readerMenuV30",
                    ) { (currentPanel, currentTab) ->
                        when (currentPanel) {
                            ReaderMenuPanelV30.MAIN -> when (currentTab) {
                                ReaderMenuTabV30.DETAILS -> ReaderDetailsTabV30(
                                    book, chapters, chapterIndex, pageIndex, pageCount, theme, bookmarked, settings,
                                    onBack, onToggleBookmark, onJumpChapter, onPageFraction, onEdit, onWriting, onStory,
                                )
                                ReaderMenuTabV30.DIRECTORY -> ReaderDirectoryTabV30(
                                    book, chapters, chapterIndex, theme, bookmarked, settings, onBack, onToggleBookmark, onJumpChapter,
                                    bookmarkedChapters, onRenameChapter, onAppendChapter, onDeleteLastChapter,
                                )
                                ReaderMenuTabV30.MORE -> ReaderMoreTabV30(settings, theme, onPanel, onLocate = { onTab(ReaderMenuTabV30.DIRECTORY) }, listening = listening, onListen = onListen)
                            }
                            ReaderMenuPanelV30.THEME -> ReaderThemePanelV30(settings, theme) { onPanel(ReaderMenuPanelV30.MAIN) }
                            ReaderMenuPanelV30.FONT -> ReaderFontPanelV30(settings, theme) { onPanel(ReaderMenuPanelV30.MAIN) }
                            ReaderMenuPanelV30.SIZE -> ReaderSizePanelV30(settings, theme) { onPanel(ReaderMenuPanelV30.MAIN) }
                            ReaderMenuPanelV30.SPACING -> ReaderSpacingPanelV30(settings, theme) { onPanel(ReaderMenuPanelV30.MAIN) }
                            ReaderMenuPanelV30.TURN -> ReaderTurnPanelV30(settings, theme) { onPanel(ReaderMenuPanelV30.MAIN) }
                            ReaderMenuPanelV30.SEARCH -> ReaderSearchPanelV30(chapters, theme, onJumpChapter) { onPanel(ReaderMenuPanelV30.MAIN) }
                            ReaderMenuPanelV30.STATS -> ReaderStatsPanelV35(theme) { onPanel(ReaderMenuPanelV30.MAIN) }
                        }
                    }
                    AnimatedVisibility(visible = panel == ReaderMenuPanelV30.MAIN) {
                        ReaderMenuTabsV30(tab, theme, onTab)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReaderMenuTabsV30(tab: ReaderMenuTabV30, theme: ReaderThemeV30, onTab: (ReaderMenuTabV30) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(theme.sheetDivider))
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(ReaderMenuTabV30.DETAILS to "详情", ReaderMenuTabV30.DIRECTORY to "目录", ReaderMenuTabV30.MORE to "更多").forEach { (item, label) ->
                val selected = item == tab
                val color by animateColorAsState(if (selected) theme.accent else theme.sheetMuted, tween(180), label = "tabColor")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTab(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ReaderSheetHeaderV30(
    title: String,
    theme: ReaderThemeV30,
    bookmarked: Boolean,
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ChevronLeft, "返回书架", tint = theme.sheetText) }
        Text(
            title,
            Modifier.weight(1f),
            color = theme.sheetText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onBookmark) {
            Icon(
                if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                "书签",
                tint = if (bookmarked) theme.accent else theme.sheetText,
            )
        }
        IconButton(onClick = { settings.toggleNight() }) {
            Icon(if (settings.night) Icons.Rounded.LightMode else Icons.Outlined.DarkMode, "日夜切换", tint = theme.sheetText)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderDirectoryTabV30(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    theme: ReaderThemeV30,
    bookmarked: Boolean,
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onJumpChapter: (Int, Int) -> Unit,
    bookmarkedChapters: Set<Int> = emptySet(),
    onRenameChapter: (Int, String) -> Unit = { _, _ -> },
    onAppendChapter: () -> Unit = {},
    onDeleteLastChapter: () -> Unit = {},
) {
    val height = (LocalConfiguration.current.screenHeightDp * .46f).dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (chapterIndex - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    var showBookmarks by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChapterDraft?>(null) }
    Column {
        ReaderSheetHeaderV30(book.title, theme, bookmarked, settings, onBack, onBookmark)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderSegmentV30("目录 ${chapters.size}", !showBookmarks, theme) { showBookmarks = false }
            Spacer(Modifier.width(8.dp))
            ReaderSegmentV30("书签 ${bookmarkedChapters.size}", showBookmarks, theme) { showBookmarks = true }
            Spacer(Modifier.weight(1f))
            if (!showBookmarks) {
                Text(
                    "+ 新章",
                    Modifier.clip(CircleShape).clickable { onAppendChapter() }.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (showBookmarks) {
            val marked = chapters.withIndex().filter { it.value.chapterNumber in bookmarkedChapters }
            Box(Modifier.fillMaxWidth().height(height)) {
                if (marked.isEmpty()) {
                    Text(
                        "还没有书签。阅读时点顶部书签图标，或长按段落选「书签」。",
                        Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                        color = theme.sheetMuted,
                        fontSize = 13.sp,
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                        itemsIndexed(marked, key = { _, item -> "bm-" + item.value.id }) { _, entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onJumpChapter(entry.index, 0) }.padding(horizontal = 20.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Bookmark, null, Modifier.size(16.dp), tint = theme.accent)
                                Text(
                                    readerDisplayChapterTitleV13(entry.value.title, entry.value.chapterNumber),
                                    Modifier.padding(start = 10.dp).weight(1f),
                                    color = theme.sheetText,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        } else {
        Box(Modifier.fillMaxWidth().height(height)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
                itemsIndexed(chapters, key = { _, item -> item.id }) { index, item ->
                    val current = index == chapterIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // Long press: rename (and delete, for the last chapter).
                            .combinedClickable(onLongClick = { editing = item }) { onJumpChapter(index, 0) }
                            .padding(start = 20.dp, end = 32.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            readerDisplayChapterTitleV13(item.title, item.chapterNumber),
                            Modifier.weight(1f),
                            color = if (current) theme.accent else theme.sheetText,
                            fontSize = 15.sp,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (current) Text("当前", color = theme.accent, fontSize = 11.sp)
                    }
                }
            }
            if (chapters.size > 24) {
                // Fast scroller: drag the pill to jump through long books.
                var trackSize by remember { mutableStateOf(IntSize.Zero) }
                val density = LocalDensity.current
                val first = listState.firstVisibleItemIndex
                val fraction = if (chapters.size <= 1) 0f else first.toFloat() / (chapters.size - 1).toFloat()
                val thumbHeight = 34.dp
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                        .onSizeChanged { trackSize = it }
                        .pointerInput(chapters.size) {
                            detectVerticalDragGestures { change, _ ->
                                change.consume()
                                val f = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                scope.launch { listState.scrollToItem((f * (chapters.size - 1)).roundToInt()) }
                            }
                        },
                ) {
                    val travel = with(density) { (trackSize.height.toDp() - thumbHeight).coerceAtLeast(0.dp) }
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = travel * fraction.coerceIn(0f, 1f))
                            .size(width = 5.dp, height = thumbHeight)
                            .clip(CircleShape)
                            .background(theme.accent),
                    )
                }
            }
        }
        }
    }
    editing?.let { chapter ->
        var title by remember(chapter.id) { mutableStateOf(chapter.title) }
        val isLast = chapter.chapterNumber == chapters.maxOfOrNull { it.chapterNumber } && chapters.size > 1
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("第 ${chapter.chapterNumber} 章") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it.take(40) }, label = { Text("章节标题") }, singleLine = true)
                    if (isLast) {
                        Text(
                            "删除这一章",
                            Modifier.padding(top = 14.dp).clip(CircleShape).clickable { editing = null; onDeleteLastChapter() }.padding(vertical = 6.dp),
                            color = Color(0xFFE5484D),
                            fontSize = 14.sp,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null; onRenameChapter(chapter.chapterNumber, title) }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ReaderSegmentV30(label: String, selected: Boolean, theme: ReaderThemeV30, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) theme.accent.copy(alpha = .14f) else Color.Transparent, tween(180), label = "segBg")
    Text(
        label,
        Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        color = if (selected) theme.accent else theme.sheetMuted,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun ReaderDetailsTabV30(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int,
    theme: ReaderThemeV30,
    bookmarked: Boolean,
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onJumpChapter: (Int, Int) -> Unit,
    onPageFraction: (Float) -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
) {
    val chapter = chapters.getOrNull(chapterIndex)
    val bookProgress = if (chapters.isEmpty()) 0f else
        ((chapterIndex + if (pageCount > 0) (pageIndex + 1f) / pageCount else 0f) / chapters.size).coerceIn(0f, 1f)
    val cover = rememberLanghuanCoverV30(book.coverPath, 220)
    Column {
        ReaderSheetHeaderV30(book.title, theme, bookmarked, settings, onBack, onBookmark)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(54.dp)
                    .aspectRatio(.72f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.sheetTile),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) Image(cover, book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(book.title.take(2), color = theme.sheetMuted, fontSize = 13.sp)
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    chapter?.let { readerDisplayChapterTitleV13(it.title, it.chapterNumber) } ?: book.title,
                    color = theme.sheetText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "第 ${chapterIndex + 1} / ${chapters.size} 章 · 本章 ${pageIndex + 1}/${pageCount.coerceAtLeast(1)} 页",
                    Modifier.padding(top = 4.dp),
                    color = theme.sheetMuted,
                    fontSize = 12.sp,
                )
                Text(
                    String.format(Locale.US, "全书已读 %.1f%%", bookProgress * 100f),
                    Modifier.padding(top = 2.dp),
                    color = theme.accent,
                    fontSize = 12.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderTextButtonV30("上一章", theme, enabled = chapterIndex > 0) { onJumpChapter(chapterIndex - 1, 0) }
            var dragging by remember { mutableStateOf<Float?>(null) }
            val shown = dragging ?: if (pageCount <= 1) 0f else pageIndex.toFloat() / (pageCount - 1)
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let(onPageFraction)
                    dragging = null
                },
                modifier = Modifier.weight(1f),
                colors = readerSliderColorsV30(theme),
            )
            ReaderTextButtonV30("下一章", theme, enabled = chapterIndex < chapters.lastIndex) { onJumpChapter(chapterIndex + 1, 0) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            listOf(
                ReaderMenuActionV30("编辑本章", Icons.Rounded.Edit, onClick = onEdit),
                ReaderMenuActionV30("AI 创作", Icons.Rounded.AutoAwesome, onClick = onWriting),
                ReaderMenuActionV30("进入故事", Icons.Rounded.TheaterComedy, onClick = onStory),
                ReaderMenuActionV30(if (bookmarked) "已加书签" else "加书签", Icons.Outlined.BookmarkBorder, bookmarked, onBookmark),
            ).forEach { action ->
                Box(Modifier.weight(1f)) { ReaderActionTileV30(action, theme) }
            }
        }
    }
}

@Composable
private fun ReaderMoreTabV30(
    settings: ReaderSettingsV30,
    theme: ReaderThemeV30,
    onPanel: (ReaderMenuPanelV30) -> Unit,
    onLocate: () -> Unit,
    listening: Boolean = false,
    onListen: () -> Unit = {},
) {
    val mode = settings.turnMode
    val actions = listOf(
        ReaderMenuActionV30(if (listening) "停止听书" else "听书", Icons.Rounded.Headphones, listening, onListen),
        ReaderMenuActionV30("阅读统计", Icons.Rounded.BarChart) { onPanel(ReaderMenuPanelV30.STATS) },
        ReaderMenuActionV30("主题", Icons.Rounded.Palette) { onPanel(ReaderMenuPanelV30.THEME) },
        ReaderMenuActionV30("字体", Icons.Rounded.TextFields) { onPanel(ReaderMenuPanelV30.FONT) },
        ReaderMenuActionV30("字号", Icons.Rounded.FormatSize) { onPanel(ReaderMenuPanelV30.SIZE) },
        ReaderMenuActionV30("行距", Icons.Rounded.FormatLineSpacing) { onPanel(ReaderMenuPanelV30.SPACING) },
        ReaderMenuActionV30("翻页·${if (mode == ReaderTurnModeV30.SCROLL) settings.lastPagedMode.label else mode.label}", Icons.Rounded.Swipe) { onPanel(ReaderMenuPanelV30.TURN) },
        ReaderMenuActionV30("上下滚动", Icons.Rounded.SwapVert, mode == ReaderTurnModeV30.SCROLL) {
            settings.selectTurnMode(if (mode == ReaderTurnModeV30.SCROLL) settings.lastPagedMode else ReaderTurnModeV30.SCROLL)
        },
        ReaderMenuActionV30("仿真翻页", Icons.Rounded.AutoStories, mode == ReaderTurnModeV30.SIMULATION) {
            settings.selectTurnMode(if (mode == ReaderTurnModeV30.SIMULATION) ReaderTurnModeV30.COVER else ReaderTurnModeV30.SIMULATION)
        },
        ReaderMenuActionV30("全文搜索", Icons.Rounded.Search) { onPanel(ReaderMenuPanelV30.SEARCH) },
        ReaderMenuActionV30("音量键翻页", Icons.Rounded.VolumeUp, settings.volumeTurn) { settings.volumeTurn = !settings.volumeTurn },
        ReaderMenuActionV30("屏幕常亮", Icons.Rounded.LightMode, settings.keepScreen) { settings.keepScreen = !settings.keepScreen },
        ReaderMenuActionV30("时间电量", Icons.Rounded.Schedule, settings.showTimeBattery) { settings.showTimeBattery = !settings.showTimeBattery },
        ReaderMenuActionV30("沉浸式", Icons.Rounded.Fullscreen, settings.immersive) { settings.immersive = !settings.immersive },
        ReaderMenuActionV30("点击动画", Icons.Rounded.TouchApp, settings.clickAnimation) { settings.clickAnimation = !settings.clickAnimation },
        ReaderMenuActionV30("全屏下一页", Icons.Rounded.SkipNext, settings.fullNext) { settings.fullNext = !settings.fullNext },
        ReaderMenuActionV30("定位", Icons.Rounded.MyLocation, onClick = onLocate),
        ReaderMenuActionV30("锁定竖屏", Icons.Rounded.ScreenLockPortrait, settings.lockPortrait) { settings.lockPortrait = !settings.lockPortrait },
    )
    Column(
        Modifier
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * .5f).dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { action -> Box(Modifier.weight(1f)) { ReaderActionTileV30(action, theme) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings panels
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReaderPanelHeaderV30(title: String, theme: ReaderThemeV30, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回", tint = theme.sheetText) }
        Text(title, Modifier.weight(1f), color = theme.sheetText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        trailing()
    }
}

@Composable
private fun ReaderThemePanelV30(settings: ReaderSettingsV30, theme: ReaderThemeV30, onBack: () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        ReaderPanelHeaderV30("阅读主题", theme, onBack)
        READER_THEMES_V30.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    val selected = option.key == settings.theme
                    val scale by animateFloatAsState(if (selected) 1f else .96f, spring(stiffness = Spring.StiffnessMedium), label = "themeScale")
                    Column(
                        Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settings.selectTheme(option.key) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(.82f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(option.page)
                                .border(
                                    BorderStroke(if (selected) 2.dp else 0.8.dp, if (selected) theme.accent else theme.sheetDivider),
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("文", color = option.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            if (selected) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(theme.accent),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = Color.White) }
                            }
                        }
                        Text(option.name, Modifier.padding(top = 6.dp), color = if (selected) theme.accent else theme.sheetMuted, fontSize = 12.sp)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ReaderFontPanelV30(settings: ReaderSettingsV30, theme: ReaderThemeV30, onBack: () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        ReaderPanelHeaderV30("字体", theme, onBack)
        listOf(
            Triple("sans", "系统黑体", FontFamily.SansSerif),
            Triple("serif", "系统宋体", FontFamily.Serif),
            Triple("mono", "等宽字体", FontFamily.Monospace),
        ).forEach { (key, name, family) ->
            val selected = settings.fontKey == key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { settings.fontKey = key }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = if (selected) theme.accent else theme.sheetText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("琅嬛福地，书香一卷", Modifier.padding(top = 3.dp), color = theme.sheetMuted, fontSize = 14.sp, fontFamily = family)
                }
                if (selected) Icon(Icons.Rounded.Check, null, tint = theme.accent)
            }
        }
        Text("字重", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp), color = theme.sheetMuted, fontSize = 12.sp)
        ReaderSegmentedV30(
            options = listOf(400 to "常规", 500 to "中等", 600 to "中粗", 700 to "粗体"),
            selected = settings.weight,
            theme = theme,
            onSelect = { settings.weight = it },
        )
    }
}

@Composable
private fun ReaderSizePanelV30(settings: ReaderSettingsV30, theme: ReaderThemeV30, onBack: () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        ReaderPanelHeaderV30("字号", theme, onBack) {
            ReaderTextButtonV30("默认", theme) { settings.fontSize = ReaderSettingsV30.DEFAULT_FONT }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderStepButtonV30("A-", theme) { settings.fontSize = (settings.fontSize - 1f).coerceAtLeast(12f) }
            Text(
                "${settings.fontSize.roundToInt()}",
                Modifier.weight(1f),
                color = theme.sheetText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            ReaderStepButtonV30("A+", theme) { settings.fontSize = (settings.fontSize + 1f).coerceAtMost(34f) }
        }
        ReaderSliderRowV30("字号", "${settings.fontSize.roundToInt()}", settings.fontSize, 12f..34f, 1f, theme) { settings.fontSize = it }
        ReaderSliderRowV30("字距", String.format(Locale.US, "%.2f", settings.letterSpacing), settings.letterSpacing, 0f..0.2f, .01f, theme) { settings.letterSpacing = it }
    }
}

@Composable
private fun ReaderSpacingPanelV30(settings: ReaderSettingsV30, theme: ReaderThemeV30, onBack: () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        ReaderPanelHeaderV30("排版", theme, onBack) {
            ReaderTextButtonV30("恢复默认", theme) { settings.resetTypography() }
        }
        val presets = listOf(
            Triple("紧凑", 1.5f, 6f to 16f),
            Triple("标准", ReaderSettingsV30.DEFAULT_LINE, ReaderSettingsV30.DEFAULT_PARAGRAPH to ReaderSettingsV30.DEFAULT_SIDE),
            Triple("舒适", 1.9f, 14f to 24f),
            Triple("宽松", 2.1f, 18f to 28f),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (name, line, rest) ->
                val selected = abs2(settings.lineFactor - line) < .01f && abs2(settings.paragraphSpacing - rest.first) < .5f && abs2(settings.sidePadding - rest.second) < .5f
                ReaderChipV30(name, selected, theme, Modifier.weight(1f)) {
                    settings.lineFactor = line
                    settings.paragraphSpacing = rest.first
                    settings.sidePadding = rest.second
                }
            }
        }
        ReaderSliderRowV30("行距", String.format(Locale.US, "%.2f", settings.lineFactor), settings.lineFactor, 1.2f..2.4f, .05f, theme) { settings.lineFactor = it }
        ReaderSliderRowV30("段距", "${settings.paragraphSpacing.roundToInt()}", settings.paragraphSpacing, 0f..28f, 1f, theme) { settings.paragraphSpacing = it }
        ReaderSliderRowV30("页边距", "${settings.sidePadding.roundToInt()}", settings.sidePadding, 8f..40f, 1f, theme) { settings.sidePadding = it }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("首行缩进", Modifier.weight(1f), color = theme.sheetText, fontSize = 14.sp)
            Switch(checked = settings.indent, onCheckedChange = { settings.indent = it }, colors = readerSwitchColorsV30(theme))
        }
    }
}

private fun abs2(value: Float): Float = if (value < 0f) -value else value

@Composable
private fun ReaderTurnPanelV30(settings: ReaderSettingsV30, theme: ReaderThemeV30, onBack: () -> Unit) {
    Column(Modifier.padding(bottom = 16.dp)) {
        ReaderPanelHeaderV30("翻页方式", theme, onBack)
        val options = listOf(
            ReaderTurnModeV30.COVER to Icons.Rounded.Layers,
            ReaderTurnModeV30.SLIDE to Icons.Rounded.ViewCarousel,
            ReaderTurnModeV30.SIMULATION to Icons.Rounded.AutoStories,
            ReaderTurnModeV30.SCROLL to Icons.Rounded.SwapVert,
            ReaderTurnModeV30.NONE to Icons.Rounded.Block,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            options.forEach { (option, icon) ->
                Box(Modifier.weight(1f)) {
                    ReaderActionTileV30(
                        ReaderMenuActionV30(option.label, icon, settings.turnMode == option) { settings.selectTurnMode(option) },
                        theme,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("点击翻页动画", color = theme.sheetText, fontSize = 14.sp)
                Text("关闭后点击左右两侧立即换页", color = theme.sheetMuted, fontSize = 12.sp)
            }
            Switch(checked = settings.clickAnimation, onCheckedChange = { settings.clickAnimation = it }, colors = readerSwitchColorsV30(theme))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("全屏点击下一页", color = theme.sheetText, fontSize = 14.sp)
                Text("除中间区域外，点哪里都向后翻", color = theme.sheetMuted, fontSize = 12.sp)
            }
            Switch(checked = settings.fullNext, onCheckedChange = { settings.fullNext = it }, colors = readerSwitchColorsV30(theme))
        }
    }
}

private data class ReaderSearchHitV30(val chapterIndex: Int, val title: String, val offset: Int, val preview: String)

@Composable
private fun ReaderSearchPanelV30(
    chapters: List<ChapterDraft>,
    theme: ReaderThemeV30,
    onJumpChapter: (Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ReaderSearchHitV30>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query, chapters) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(260)
        searching = true
        hits = withContext(Dispatchers.Default) {
            val found = ArrayList<ReaderSearchHitV30>()
            for ((index, chapter) in chapters.withIndex()) {
                val body = readerNormalizeBodyV14(readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content))
                var at = body.indexOf(q, ignoreCase = true)
                var perChapter = 0
                while (at >= 0 && perChapter < 3 && found.size < 80) {
                    val start = (at - 16).coerceAtLeast(0)
                    val end = (at + q.length + 30).coerceAtMost(body.length)
                    found += ReaderSearchHitV30(
                        index,
                        readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
                        at,
                        body.substring(start, end).replace(Regex("\\s+"), " "),
                    )
                    perChapter++
                    at = body.indexOf(q, at + q.length, ignoreCase = true)
                }
                if (found.size >= 80) break
            }
            found
        }
        searching = false
    }
    Column(Modifier.padding(bottom = 10.dp)) {
        ReaderPanelHeaderV30("全文搜索", theme, onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("搜索整本书", color = theme.sheetMuted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = theme.sheetMuted) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除", tint = theme.sheetMuted) }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = theme.accent,
                unfocusedBorderColor = theme.sheetDivider,
                focusedTextColor = theme.sheetText,
                unfocusedTextColor = theme.sheetText,
                cursorColor = theme.accent,
            ),
        )
        Text(
            when {
                query.isBlank() -> "输入关键词，结果会直接定位到原文位置"
                searching -> "正在搜索…"
                else -> "找到 ${hits.size} 处" + if (hits.size >= 80) "（仅显示前 80 处）" else ""
            },
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = theme.sheetMuted,
            fontSize = 12.sp,
        )
        LazyColumn(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .38f).dp)) {
            items(hits, key = { "${it.chapterIndex}:${it.offset}" }) { hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onJumpChapter(hit.chapterIndex, hit.offset) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(hit.title, color = theme.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hit.preview, Modifier.padding(top = 3.dp), color = theme.sheetText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun ReaderActionTileV30(action: ReaderMenuActionV30, theme: ReaderThemeV30) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "tileScale")
    val tint by animateColorAsState(if (action.selected) theme.accent else theme.sheetText, tween(180), label = "tileTint")
    val haptics = LocalHapticFeedback.current
    // Toggles pop when they switch on, so the state change is felt as well as seen.
    val pop = remember { Animatable(1f) }
    var wasSelected by remember { mutableStateOf(action.selected) }
    LaunchedEffect(action.selected) {
        if (action.selected && !wasSelected) {
            pop.snapTo(.82f)
            pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium))
        }
        wasSelected = action.selected
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                action.onClick()
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(action.icon, action.label, Modifier.size(24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, tint = tint)
        Text(
            action.label,
            Modifier.padding(top = 6.dp),
            color = if (action.selected) theme.accent else theme.sheetMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReaderTextButtonV30(label: String, theme: ReaderThemeV30, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (enabled) theme.sheetText else theme.sheetMuted.copy(alpha = .5f),
        fontSize = 14.sp,
    )
}

@Composable
private fun ReaderStepButtonV30(label: String, theme: ReaderThemeV30, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 64.dp, height = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(theme.sheetTile)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = theme.sheetText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReaderChipV30(label: String, selected: Boolean, theme: ReaderThemeV30, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) theme.accent.copy(alpha = .12f) else theme.sheetTile, tween(180), label = "chipBg")
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) theme.accent else theme.sheetText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ReaderSegmentedV30(options: List<Pair<Int, String>>, selected: Int, theme: ReaderThemeV30, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.sheetTile)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val bg by animateColorAsState(if (active) theme.sheet else Color.Transparent, tween(180), label = "segBg")
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (active) theme.accent else theme.sheetText, fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ReaderSliderRowV30(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    theme: ReaderThemeV30,
    onValue: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(52.dp), color = theme.sheetText, fontSize = 14.sp)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { raw -> onValue(((raw / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)) },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = readerSliderColorsV30(theme),
        )
        Text(valueLabel, Modifier.width(44.dp), color = theme.sheetMuted, fontSize = 13.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun readerSliderColorsV30(theme: ReaderThemeV30) = SliderDefaults.colors(
    thumbColor = theme.accent,
    activeTrackColor = theme.accent,
    inactiveTrackColor = theme.sheetDivider,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun readerSwitchColorsV30(theme: ReaderThemeV30) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = theme.accent,
    checkedBorderColor = theme.accent,
    uncheckedThumbColor = theme.sheetMuted,
    uncheckedTrackColor = theme.sheetTile,
    uncheckedBorderColor = theme.sheetDivider,
)
