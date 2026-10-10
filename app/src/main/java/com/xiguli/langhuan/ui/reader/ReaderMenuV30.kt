package com.xiguli.langhuan.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import com.xiguli.langhuan.ui.design.LanghuanMotion
import com.xiguli.langhuan.ui.design.LocalLanghuanReducedMotion
import com.xiguli.langhuan.ui.design.langhuanEnterOnMount
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


/* -------------------------------------------------------------------------- */
/*                                  Models                                    */
/* -------------------------------------------------------------------------- */

private data class ReaderSearchHitV30(
    val chapterIndex: Int,
    val title: String,
    val offset: Int,
    val preview: String,
)

private enum class ReaderDirectoryModeV30 { CHAPTERS, BOOKMARKS }

@Composable
private fun readerMenuWindowHeightV63() = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.height.toDp()
}


/* -------------------------------------------------------------------------- */
/*                                Main Menu                                   */
/* -------------------------------------------------------------------------- */

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
    bookmarkedChapters: Set<Int>,
    bookmarkError: String?,
    legacyBookmarkedChapters: Set<Int>,
    legacyBookmarkError: String?,
    onRestoreLegacyBookmark: (Int) -> Unit,
    listening: Boolean,
    onListen: () -> Unit,
    onRenameChapter: (Int, String) -> Unit,
    onAppendChapter: () -> Unit,
    onDeleteLastChapter: () -> Unit,
    onRefreshCatalogue: () -> Unit,
    refreshingCatalogue: Boolean,
    catalogueMessage: String?,
    onOpenOriginalEdition: (() -> Unit)? = null,
) {
    if (!visible) return
    val t = LocalLanghuanUiTokens.current
    val reducedMotion = LocalLanghuanReducedMotion.current
    val maxHeight = readerMenuWindowHeightV63() * 0.88f

    Dialog(
        onDismissRequest = onDismiss,
        // Own the insets in Compose so the measured root and visible window agree
        // after API 35 landscape cutout fitting. Keep controls inside safe edges below.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrim fades in; the panel rises 24dp with the ease-out token (snap when reduced).
                .langhuanEnterOnMount(initialScale = 1f, durationMillis = LanghuanMotion.DURATION_QUICK)
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) }
                .semantics { dismiss { onDismiss(); true } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val panelShape = RoundedCornerShape(t.radiusXl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = t.space3).padding(bottom = t.space2)
                    .navigationBarsPadding()
                    .imePadding()
                    .heightIn(max = maxHeight)
                    .langhuanEnterOnMount(rise = 24.dp, initialScale = 1f)
                    .background(color = t.background, shape = panelShape)
                    .border(width = 1.dp, color = t.border, shape = panelShape)
                    // Swallow backdrop taps without merging the body's scroll viewport
                    // or unrelated text into one large clickable accessibility node.
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
            ) {
                ReaderMenuHandleV30()

                // 三个主操作固定在菜单顶部。
                // 注意：当前仓库 ReaderBookmarkStoreV49 的持久层仍然是
                // chapterNumber 级书签。ReaderMenu 只消费 Screen 提供的
                // onToggleBookmark，不额外虚构新的 page bookmark API。
                ReaderMenuPrimaryActionsV30(
                    bookmarked = bookmarked,
                    night = settings.night,
                    onBack = onBack,
                    onBookmark = onToggleBookmark,
                    onNight = { settings.toggleNight() },
                )

                ReaderMenuDividerV30()

                AnimatedContent(
                    // Reserve the fixed bottom tabs before sizing the scrollable body.
                    // Landscape has less height; a body measured first can hide every tab.
                    modifier = Modifier.weight(1f).clipToBounds(),
                    targetState = panel to tab,
                    transitionSpec = {
                        val oldPanel = initialState.first
                        val newPanel = targetState.first
                        val m = LanghuanMotion
                        val enterMs = m.DURATION_STANDARD
                        val exitMs = m.exitDuration(enterMs)
                        // Shared-axis push between the main menu and a sub-panel; tab switches
                        // are a quick fade-through. Reduced motion swaps instantly.
                        val transition = when {
                            reducedMotion ->
                                androidx.compose.animation.EnterTransition.None togetherWith
                                    androidx.compose.animation.ExitTransition.None
                            oldPanel == ReaderMenuPanelV30.MAIN &&
                                newPanel != ReaderMenuPanelV30.MAIN -> {
                                (slideInHorizontally(tween(enterMs, easing = m.EaseOut)) { it / 6 } +
                                    fadeIn(tween(m.DURATION_QUICK, easing = m.EaseOut))) togetherWith
                                    (slideOutHorizontally(tween(exitMs, easing = m.EaseIn)) { -it / 8 } +
                                        fadeOut(tween(exitMs, easing = m.EaseIn)))
                            }
                            oldPanel != ReaderMenuPanelV30.MAIN &&
                                newPanel == ReaderMenuPanelV30.MAIN -> {
                                (slideInHorizontally(tween(enterMs, easing = m.EaseOut)) { -it / 6 } +
                                    fadeIn(tween(m.DURATION_QUICK, easing = m.EaseOut))) togetherWith
                                    (slideOutHorizontally(tween(exitMs, easing = m.EaseIn)) { it / 8 } +
                                        fadeOut(tween(exitMs, easing = m.EaseIn)))
                            }
                            else -> fadeIn(tween(m.DURATION_QUICK, delayMillis = m.DURATION_INSTANT / 2, easing = m.EaseOut)) togetherWith
                                fadeOut(tween(m.DURATION_INSTANT, easing = m.EaseIn))
                        }
                        transition using SizeTransform(clip = true)
                    },
                    label = "readerMenuV3",
                ) { (currentPanel, currentTab) ->
                    when (currentPanel) {
                        ReaderMenuPanelV30.MAIN -> {
                            when (currentTab) {
                                ReaderMenuTabV30.DETAILS -> {
                                    ReaderDetailsTabV30(
                                        book = book,
                                        chapters = chapters,
                                        chapterIndex = chapterIndex,
                                        pageIndex = pageIndex,
                                        pageCount = pageCount,
                                        settings = settings,
                                        theme = theme,
                                        onPageFraction = onPageFraction,
                                        onThemePanel = {
                                            onPanel(ReaderMenuPanelV30.THEME)
                                        },
                                        onSizePanel = {
                                            onPanel(ReaderMenuPanelV30.SIZE)
                                        },
                                    )
                                }
                                ReaderMenuTabV30.DIRECTORY -> {
                                    ReaderDirectoryTabV30(
                                        book = book,
                                        chapters = chapters,
                                        chapterIndex = chapterIndex,
                                        bookmarkedChapters = bookmarkedChapters,
                                        bookmarkError = bookmarkError,
                                        legacyBookmarkedChapters = legacyBookmarkedChapters,
                                        legacyBookmarkError = legacyBookmarkError,
                                        onRestoreLegacyBookmark = onRestoreLegacyBookmark,
                                        onJumpChapter = onJumpChapter,
                                        onRefreshCatalogue = onRefreshCatalogue,
                                        refreshingCatalogue = refreshingCatalogue,
                                        catalogueMessage = catalogueMessage,
                                    )
                                }
                                ReaderMenuTabV30.MORE -> {
                                    ReaderMoreTabV30(
                                        book = book,
                                        chapters = chapters,
                                        chapterIndex = chapterIndex,
                                        settings = settings,
                                        listening = listening,
                                        onListen = onListen,
                                        onPanel = onPanel,
                                        onLocate = { onTab(ReaderMenuTabV30.DIRECTORY) },
                                        onEdit = onEdit,
                                        onWriting = onWriting,
                                        onStory = onStory,
                                        onRenameChapter = onRenameChapter,
                                        onAppendChapter = onAppendChapter,
                                        onDeleteLastChapter = onDeleteLastChapter,
                                        onOpenOriginalEdition = onOpenOriginalEdition,
                                    )
                                }
                            }
                        }
                        ReaderMenuPanelV30.THEME -> {
                            ReaderThemePanelV30(
                                settings = settings,
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                        ReaderMenuPanelV30.FONT -> {
                            ReaderFontPanelV30(
                                settings = settings,
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                        ReaderMenuPanelV30.SIZE -> {
                            ReaderSizePanelV30(
                                settings = settings,
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                        ReaderMenuPanelV30.SPACING -> {
                            ReaderSpacingPanelV30(
                                settings = settings,
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                        ReaderMenuPanelV30.TURN -> {
                            ReaderTurnPanelV30(
                                settings = settings,
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                        ReaderMenuPanelV30.SEARCH -> {
                            ReaderSearchPanelV30(
                                chapters = chapters,
                                onJumpChapter = onJumpChapter,
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                        ReaderMenuPanelV30.STATS -> {
                            ReaderStatsPanelV30(
                                onBack = { onPanel(ReaderMenuPanelV30.MAIN) },
                            )
                        }
                    }
                }

                if (panel == ReaderMenuPanelV30.MAIN) {
                    ReaderMenuDividerV30()
                    ReaderMenuTabsV30(tab = tab, onTab = onTab)
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Top Primary Actions                            */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderMenuPrimaryActionsV30(
    bookmarked: Boolean,
    night: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onNight: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space3).padding(bottom = t.space3),
        horizontalArrangement = Arrangement.spacedBy(t.space2),
    ) {
        ReaderPrimaryActionV30(
            icon = Icons.Rounded.ArrowBack,
            label = "返回书架",
            modifier = Modifier.weight(1f),
            onClick = onBack,
        )
        ReaderPrimaryActionV30(
            icon = if (bookmarked) Icons.Outlined.Bookmark
            else Icons.Outlined.BookmarkBorder,
            label = if (bookmarked) "已加书签" else "本章加书签",
            description = if (bookmarked) "取消本章书签" else "添加本章书签",
            selected = bookmarked,
            gold = bookmarked,
            modifier = Modifier.weight(1f),
            onClick = onBookmark,
        )
        ReaderPrimaryActionV30(
            icon = if (night) Icons.Rounded.LightMode else Icons.Outlined.DarkMode,
            label = if (night) "切换日间" else "切换夜间",
            selected = night,
            modifier = Modifier.weight(1f),
            onClick = onNight,
        )
    }
}

@Composable
private fun ReaderPrimaryActionV30(
    icon: ImageVector,
    label: String,
    description: String = label,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    gold: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    val background = when {
        gold -> t.goldContainer
        selected -> t.accent
        else -> t.card
    }
    val foreground = when {
        gold -> t.goldForeground
        selected -> t.accentForeground
        else -> t.secondaryForeground
    }
    Column(
        modifier = modifier
            .semantics { contentDescription = description }
            .height(72.dp)
            .background(color = background, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2, vertical = t.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = foreground,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Details                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDetailsTabV30(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    pageIndex: Int,
    pageCount: Int,
    settings: ReaderSettingsV30,
    theme: ReaderThemeV30,
    onPageFraction: (Float) -> Unit,
    onThemePanel: () -> Unit,
    onSizePanel: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val windowHeight = readerMenuWindowHeightV63()
    val chapter = chapters.getOrNull(chapterIndex)
    val fraction = when {
        pageCount <= 1 -> 0f
        else -> pageIndex.toFloat().div(pageCount - 1f).coerceIn(0f, 1f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = windowHeight * 0.58f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.space4, vertical = t.space3),
    ) {
        if (book.sourceId.isNotBlank() && catalogueMiddleGapV53(chapters.map { it.title }, catalogueVolumeTitlesV53(chapters.map { it.title })) != null) {
            Text("目录待补全，暂不能计算全书进度", color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(t.space2))
        }
        ReaderSectionTitleV30(title = "当前阅读")
        Spacer(Modifier.height(t.space2))
        ReaderBookInfoCardV30(
            book = book, chapter = chapter,
            pageIndex = pageIndex, pageCount = pageCount,
        )
        if (pageCount > 1) {
            Spacer(Modifier.height(t.space3))
            Text(
                text = "本章位置",
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
            )
            Slider(
                value = fraction,
                onValueChange = onPageFraction,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = t.primary,
                    activeTrackColor = t.primary,
                    inactiveTrackColor = t.border,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
        }
        Spacer(Modifier.height(t.space5))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderSectionTitleV30(title = "阅读主题", modifier = Modifier.weight(1f))
            ReaderInlineTextActionV30(text = "全部设置", onClick = onThemePanel)
        }
        Spacer(Modifier.height(t.space3))
        ReaderThemeGridV30(settings = settings, compact = true)
        Spacer(Modifier.height(t.space5))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderSectionTitleV30(title = "字号", modifier = Modifier.weight(1f))
            ReaderInlineTextActionV30(text = "更多", onClick = onSizePanel)
        }
        Spacer(Modifier.height(t.space3))
        ReaderFontSizeStepperV30(settings = settings)
        Spacer(Modifier.height(t.space4))
        Text(
            text = "当前正文主题：${readerThemeDisplayNameV30(theme.key)} · " +
                "${settings.fontSize.roundToInt()}sp",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Book Info                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderBookInfoCardV30(
    book: ReaderBookUi,
    chapter: ChapterDraft?,
    pageIndex: Int,
    pageCount: Int,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(68.dp)
                .background(color = t.input, shape = RoundedCornerShape(t.radiusSm))
                .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusSm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = t.primary,
            )
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = chapter?.let {
                    readerDisplayChapterTitleV13(it.title, it.chapterNumber)
                } ?: "暂无章节",
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = buildString {
                    if (book.genre.isNotBlank() && book.genre != "导入作品") {
                        append(book.genre)
                        append(" · ")
                    }
                    if (pageCount > 0) {
                        append("本章 ${pageIndex + 1} / $pageCount 页")
                    } else {
                        append("正在排版")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                               Directory                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDirectoryTabV30(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    bookmarkedChapters: Set<Int>,
    bookmarkError: String?,
    legacyBookmarkedChapters: Set<Int>,
    legacyBookmarkError: String?,
    onRestoreLegacyBookmark: (Int) -> Unit,
    onJumpChapter: (Int, Int) -> Unit,
    onRefreshCatalogue: () -> Unit,
    refreshingCatalogue: Boolean,
    catalogueMessage: String?,
) {
    val t = LocalLanghuanUiTokens.current
    var mode by rememberSaveable { mutableStateOf(ReaderDirectoryModeV30.CHAPTERS) }
    var legacy by rememberSaveable { mutableStateOf(false) }

    // Fill the whole menu body: the panel is always laid out at its max height (weighted
    // body), so a list capped at a fraction of the window left a large blank band below it.
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = t.space4, vertical = t.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderDirectorySegmentV30(
                text = "目录 ${chapters.size}",
                selected = mode == ReaderDirectoryModeV30.CHAPTERS,
                onClick = {
                    mode = ReaderDirectoryModeV30.CHAPTERS
                    legacy = false
                },
            )
            Spacer(Modifier.width(t.space2))
            ReaderDirectorySegmentV30(
                text = "书签 ${bookmarkedChapters.size}",
                selected = mode == ReaderDirectoryModeV30.BOOKMARKS && !legacy,
                onClick = {
                    mode = ReaderDirectoryModeV30.BOOKMARKS
                    legacy = false
                },
            )
            if (legacyBookmarkedChapters.isNotEmpty() || legacyBookmarkError != null) {
                Spacer(Modifier.width(t.space2))
                ReaderDirectorySegmentV30(
                    text = "旧版",
                    selected = legacy,
                    onClick = {
                        mode = ReaderDirectoryModeV30.BOOKMARKS
                        legacy = true
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (book.sourceId.isNotBlank()) {
                ReaderSmallIconActionV30(
                    icon = Icons.Rounded.AutoStories,
                    contentDescription = "刷新目录",
                    loading = refreshingCatalogue,
                    onClick = onRefreshCatalogue,
                )
            }
        }

        bookmarkError?.let { ReaderInlineErrorV30(it) }
        legacyBookmarkError?.takeIf { legacy }?.let { ReaderInlineErrorV30(it) }
        catalogueMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = t.space4, vertical = t.space1),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }

        when {
            mode == ReaderDirectoryModeV30.CHAPTERS -> {
                ReaderChapterListV30(
                    chapters = chapters,
                    chapterIndex = chapterIndex,
                    bookmarkedChapters = bookmarkedChapters,
                    onJumpChapter = onJumpChapter,
                    modifier = Modifier.weight(1f),
                )
            }
            legacy -> {
                ReaderLegacyBookmarkListV30(
                    chapters = chapters,
                    bookmarks = legacyBookmarkedChapters,
                    restoredBookmarks = bookmarkedChapters,
                    onRestore = onRestoreLegacyBookmark,
                    onJumpChapter = onJumpChapter,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                ReaderBookmarkListV30(
                    chapters = chapters,
                    bookmarks = bookmarkedChapters,
                    onJumpChapter = onJumpChapter,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReaderChapterListV30(
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    bookmarkedChapters: Set<Int>,
    onJumpChapter: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    if (chapters.isEmpty()) {
        ReaderMenuEmptyV30(title = "暂无章节", description = "这本书还没有可阅读的章节。")
        return
    }
    // Open with the current chapter in view (two rows of context above it).
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = readerTocInitialIndexV90(chapterIndex, chapters.size),
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = t.space3, end = t.space3, bottom = t.space3,
        ),
    ) {
        itemsIndexed(items = chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
            val current = index == chapterIndex
            val marked = chapter.chapterNumber in bookmarkedChapters
            ReaderChapterRowV30(
                chapter = chapter,
                current = current,
                bookmarked = marked,
                onClick = { onJumpChapter(index, 0) },
            )
        }
    }
}

/** First visible row when the directory opens: the current chapter with two rows above it. */
internal fun readerTocInitialIndexV90(chapterIndex: Int, chapterCount: Int): Int =
    if (chapterCount <= 0) 0 else (chapterIndex - 2).coerceIn(0, chapterCount - 1)

@Composable
private fun ReaderBookmarkListV30(
    chapters: List<ChapterDraft>,
    bookmarks: Set<Int>,
    onJumpChapter: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val rows = chapters.filter { it.chapterNumber in bookmarks }
    if (rows.isEmpty()) {
        ReaderMenuEmptyV30(
            title = "还没有书签",
            description = "在阅读菜单顶部点击「这一页加书签」后，会显示在这里。",
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = t.space3, end = t.space3, bottom = t.space3,
        ),
    ) {
        items(items = rows, key = { "bookmark-${it.id}" }) { chapter ->
            val index = chapters.indexOf(chapter)
            ReaderChapterRowV30(
                chapter = chapter,
                current = false,
                bookmarked = true,
                onClick = { onJumpChapter(index, 0) },
            )
        }
    }
}

@Composable
private fun ReaderLegacyBookmarkListV30(
    chapters: List<ChapterDraft>,
    bookmarks: Set<Int>,
    restoredBookmarks: Set<Int>,
    onRestore: (Int) -> Unit,
    onJumpChapter: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    var selected by remember { mutableStateOf<ChapterDraft?>(null) }
    val rows = chapters.filter { it.chapterNumber in bookmarks }
    selected?.let { chapter ->
        val restored = chapter.chapterNumber in restoredBookmarks
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("旧版书签暂存") },
            text = { Column {
                Text("旧版书签未区分书籍。请核对这条书签属于本书后再归入；暂存数据会保留。")
                Text(readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber))
                val outside = bookmarks.count { number -> chapters.none { it.chapterNumber == number } }
                if (outside > 0) Text("另有 $outside 条超出本书目录，仍保留在暂存中。")
                if (restored) Text("已归入")
            } },
            confirmButton = { TextButton(onClick = { onRestore(chapter.chapterNumber) }, enabled = !restored) { Text("归入本书") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
        )
    }
    if (rows.isEmpty()) {
        ReaderMenuEmptyV30(
            title = "没有可恢复的旧版书签",
            description = "旧版暂存不会自动归入本书，以免把不同书籍的章节混在一起。",
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = t.space3, end = t.space3, bottom = t.space3,
        ),
    ) {
        items(items = rows, key = { "legacy-${it.id}" }) { chapter ->
            val index = chapters.indexOf(chapter)
            val shape = RoundedCornerShape(t.radiusMd)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = t.space1)
                    .background(color = t.card, shape = shape)
                    .border(width = 1.dp, color = t.border, shape = shape)
                    .padding(t.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f).clickable { selected = chapter },
                ) {
                    Text(
                        text = readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
                        style = MaterialTheme.typography.bodyLarge,
                        color = t.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "旧版暂存",
                        modifier = Modifier.padding(top = t.space1),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                ReaderInlineTextActionV30(
                    text = "归入本书",
                    onClick = { selected = chapter },
                )
            }
        }
    }
}

@Composable
private fun ReaderChapterRowV30(
    chapter: ChapterDraft,
    current: Boolean,
    bookmarked: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                color = if (current) t.input else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (current) t.border else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = t.space3, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current) {
            // A 3dp jade rail marks the current chapter; the row itself stays neutral.
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(color = t.primary, shape = CircleShape),
            )
            Spacer(Modifier.width(t.space2 + 2.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) t.foreground else t.secondaryForeground,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current) {
                Text(
                    text = "正在阅读",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.primary,
                )
            }
        }
        if (bookmarked) {
            Icon(
                imageVector = Icons.Outlined.Bookmark,
                contentDescription = "已加书签",
                modifier = Modifier.size(18.dp),
                tint = t.gold,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  More                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderMoreTabV30(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    chapterIndex: Int,
    settings: ReaderSettingsV30,
    listening: Boolean,
    onListen: () -> Unit,
    onPanel: (ReaderMenuPanelV30) -> Unit,
    onLocate: () -> Unit,
    onEdit: () -> Unit,
    onWriting: () -> Unit,
    onStory: () -> Unit,
    onRenameChapter: (Int, String) -> Unit,
    onAppendChapter: () -> Unit,
    onDeleteLastChapter: () -> Unit,
    onOpenOriginalEdition: (() -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    val windowHeight = readerMenuWindowHeightV63()
    val currentChapter = chapters.getOrNull(chapterIndex)
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = windowHeight * 0.58f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.space4, vertical = t.space3),
    ) {
        ReaderSectionTitleV30(title = "常用")
        Spacer(Modifier.height(t.space3))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            ReaderActionTileV30(
                icon = Icons.Rounded.Headphones,
                label = if (listening) "停止听书" else "听书",
                selected = listening,
                modifier = Modifier.weight(1f),
                onClick = onListen,
            )
            ReaderActionTileV30(
                icon = Icons.Rounded.BarChart,
                label = "阅读统计",
                modifier = Modifier.weight(1f),
                onClick = { onPanel(ReaderMenuPanelV30.STATS) },
            )
            ReaderActionTileV30(
                icon = Icons.Rounded.Search,
                label = "全文搜索",
                modifier = Modifier.weight(1f),
                onClick = { onPanel(ReaderMenuPanelV30.SEARCH) },
            )
            ReaderActionTileV30(
                icon = Icons.Rounded.MyLocation,
                label = "定位",
                modifier = Modifier.weight(1f),
                onClick = onLocate,
            )
        }
        if (onOpenOriginalEdition != null) {
            Spacer(Modifier.height(t.space3))
            // This EPUB opens in 文字版 because that was the last choice; offer the way back.
            ReaderSettingsNavigationRowV30(
                icon = Icons.Rounded.AutoStories,
                title = "切换到原版",
                value = "EPUB 原版排版",
                onClick = onOpenOriginalEdition,
            )
        }
        Spacer(Modifier.height(t.space5))
        ReaderSectionTitleV30(title = "排版与翻页")
        Spacer(Modifier.height(t.space2))
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.TextFields,
            title = "字体",
            value = readerFontLabelV30(settings.fontKey),
            onClick = { onPanel(ReaderMenuPanelV30.FONT) },
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.FormatLineSpacing,
            title = "行距与排版",
            value = String.format(Locale.US, "%.2f", settings.lineFactor),
            onClick = { onPanel(ReaderMenuPanelV30.SPACING) },
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.ViewCarousel,
            title = "翻页方式",
            value = settings.turnMode.label,
            onClick = { onPanel(ReaderMenuPanelV30.TURN) },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.SwapVert,
            title = "上下滚动",
            description = "连续纵向阅读正文",
            checked = settings.turnMode == ReaderTurnModeV30.SCROLL,
            onCheckedChange = { checked ->
                if (checked) settings.selectTurnMode(ReaderTurnModeV30.SCROLL)
                else settings.selectTurnMode(settings.lastPagedMode)
            },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.AutoStories,
            title = "仿真翻页",
            description = "模拟纸张翻页效果",
            checked = settings.turnMode == ReaderTurnModeV30.SIMULATION,
            onCheckedChange = { checked ->
                settings.selectTurnMode(
                    if (checked) ReaderTurnModeV30.SIMULATION else ReaderTurnModeV30.COVER,
                )
            },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.TouchApp,
            title = "点击动画",
            description = "点击翻页时播放过渡动画",
            checked = settings.clickAnimation,
            onCheckedChange = { settings.clickAnimation = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.Fullscreen,
            title = "全屏下一页",
            description = "除中间区域外点击均向后翻页",
            checked = settings.fullNext,
            onCheckedChange = { settings.fullNext = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.VolumeUp,
            title = "音量键翻页",
            description = "音量加减键控制上一页与下一页",
            checked = settings.volumeTurn,
            onCheckedChange = { settings.volumeTurn = it },
        )
        Spacer(Modifier.height(t.space5))
        ReaderSectionTitleV30(title = "屏幕")
        Spacer(Modifier.height(t.space2))
        ReaderSwitchRowV30(
            icon = Icons.Rounded.WbSunny,
            title = "屏幕常亮",
            description = "阅读时阻止屏幕自动熄灭",
            checked = settings.keepScreen,
            onCheckedChange = { settings.keepScreen = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.Visibility,
            title = "时间电量",
            description = "页脚显示当前时间与电量",
            checked = settings.showTimeBattery,
            onCheckedChange = { settings.showTimeBattery = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.Fullscreen,
            title = "沉浸式",
            description = "隐藏系统栏，扩大正文阅读区域",
            checked = settings.immersive,
            onCheckedChange = { settings.immersive = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.ScreenLockPortrait,
            title = "锁定竖屏",
            description = "阅读期间保持竖屏方向",
            checked = settings.lockPortrait,
            onCheckedChange = { settings.lockPortrait = it },
        )
        Spacer(Modifier.height(t.space5))
        ReaderSectionTitleV30(title = "书籍与章节")
        Spacer(Modifier.height(t.space2))
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.Edit,
            title = "编辑当前章节",
            value = currentChapter?.let { "第 ${it.chapterNumber} 章" } ?: "",
            enabled = currentChapter != null,
            onClick = onEdit,
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.AutoAwesome,
            title = "AI 创作",
            value = "规划、改写或续写",
            onClick = onWriting,
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.MenuBook,
            title = "故事模式",
            value = "进入世界互动",
            onClick = onStory,
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.Edit,
            title = "重命名当前章节",
            value = currentChapter?.title.orEmpty(),
            enabled = currentChapter != null,
            onClick = { renameOpen = true },
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.Add,
            title = "新增章节",
            value = "添加到章节末尾",
            onClick = onAppendChapter,
        )
        ReaderSettingsNavigationRowV30(
            icon = Icons.Rounded.DeleteOutline,
            title = "删除最后一章",
            value = chapters.lastOrNull()?.let { "第 ${it.chapterNumber} 章" }.orEmpty(),
            destructive = true,
            enabled = chapters.isNotEmpty(),
            onClick = { deleteOpen = true },
        )
        Spacer(Modifier.height(t.space3))
        Text(
            text = "《${book.title}》 · 共 ${chapters.size} 章",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }

    if (renameOpen && currentChapter != null) {
        ReaderRenameChapterDialogV30(
            chapter = currentChapter,
            onDismiss = { renameOpen = false },
            onConfirm = { title ->
                renameOpen = false
                onRenameChapter(currentChapter.chapterNumber, title)
            },
        )
    }
    if (deleteOpen) {
        ReaderDeleteLastChapterDialogV30(
            chapter = chapters.lastOrNull(),
            onDismiss = { deleteOpen = false },
            onConfirm = {
                deleteOpen = false
                onDeleteLastChapter()
            },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Theme Panel                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderThemePanelV30(
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(title = "阅读主题", onBack = onBack)
        Text(
            text = "正文主题独立于琅嬛 App 的浅色 / 深色主题。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space4))
        ReaderThemeGridV30(settings = settings, compact = false)
    }
}

@Composable
private fun ReaderThemeGridV30(
    settings: ReaderSettingsV30,
    compact: Boolean,
) {
    val t = LocalLanghuanUiTokens.current
    val orderedKeys = listOf(
        "paper", "sheep", "tea", "green", "langhuan", "pink", "white", "night",
    )
    val options = orderedKeys.mapNotNull { key ->
        READER_THEMES_V30.firstOrNull { it.key == key }
    }
    options.chunked(4).forEachIndexed { rowIndex, row ->
        if (rowIndex > 0) Spacer(Modifier.height(t.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            row.forEach { option ->
                ReaderThemeOptionV30(
                    option = option,
                    label = readerThemeDisplayNameV30(option.key),
                    selected = settings.theme == option.key,
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = { settings.selectTheme(option.key) },
                )
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ReaderThemeOptionV30(
    option: ReaderThemeV30,
    label: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(t.radiusMd)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (compact) 1.18f else 0.94f)
                .background(color = option.page, shape = shape)
                .border(
                    width = 1.dp,
                    color = if (selected) t.primary else t.border,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "文",
                color = option.text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(t.space1)
                        .size(20.dp)
                        .background(color = t.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已选择",
                        modifier = Modifier.size(13.dp),
                        tint = t.card,
                    )
                }
            }
        }
        Spacer(Modifier.height(t.space1))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.primary else t.secondaryForeground,
            maxLines = 1,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Font Panel                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderFontPanelV30(
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(title = "字体", onBack = onBack)
        val fonts = listOf(
            Triple("sans", "系统黑体", FontFamily.SansSerif),
            Triple("serif", "系统宋体", FontFamily.Serif),
            Triple("mono", "等宽字体", FontFamily.Monospace),
        )
        fonts.forEach { (key, label, family) ->
            val selected = settings.fontKey == key
            val shape = RoundedCornerShape(t.radiusMd)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = t.space2)
                    .background(
                        color = if (selected) t.accent else t.card,
                        shape = shape,
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) t.primary else t.border,
                        shape = shape,
                    )
                    .clickable { settings.fontKey = key }
                    .padding(t.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "琅嬛福地，书香一卷",
                        modifier = Modifier.padding(top = t.space1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.secondaryForeground,
                        fontFamily = family,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = t.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(t.space3))
        ReaderSectionTitleV30(title = "字重")
        Spacer(Modifier.height(t.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            listOf(
                400 to "常规", 500 to "中等", 600 to "中粗", 700 to "粗体",
            ).forEach { (value, label) ->
                ReaderChoiceChipV30(
                    text = label,
                    selected = settings.weight == value,
                    modifier = Modifier.weight(1f),
                    onClick = { settings.weight = value },
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Size Panel                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderSizePanelV30(
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(
            title = "字号",
            onBack = onBack,
            action = {
                ReaderInlineTextActionV30(
                    text = "默认",
                    onClick = { settings.fontSize = ReaderSettingsV30.DEFAULT_FONT },
                )
            },
        )
        ReaderFontSizeStepperV30(settings = settings)
        Spacer(Modifier.height(t.space4))
        ReaderSliderRowV30(
            label = "字号",
            valueLabel = "${settings.fontSize.roundToInt()}sp",
            value = settings.fontSize,
            range = 12f..34f,
            step = 1f,
            onValue = { settings.fontSize = it },
        )
        ReaderSliderRowV30(
            label = "字距",
            valueLabel = String.format(Locale.US, "%.2f", settings.letterSpacing),
            value = settings.letterSpacing,
            range = 0f..0.20f,
            step = 0.01f,
            onValue = { settings.letterSpacing = it },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Spacing Panel                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderSpacingPanelV30(
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(
            title = "行距与排版",
            onBack = onBack,
            action = {
                ReaderInlineTextActionV30(
                    text = "恢复默认",
                    onClick = { settings.resetTypography() },
                )
            },
        )
        Text(
            text = "行距",
            style = MaterialTheme.typography.labelMedium,
            color = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            listOf(
                1.65f to "紧凑", 1.80f to "标准", 1.95f to "舒适", 2.10f to "宽松",
            ).forEach { (value, label) ->
                ReaderChoiceChipV30(
                    text = label,
                    selected = kotlin.math.abs(settings.lineFactor - value) < 0.02f,
                    modifier = Modifier.weight(1f),
                    onClick = { settings.lineFactor = value },
                )
            }
        }
        Spacer(Modifier.height(t.space3))
        ReaderSliderRowV30(
            label = "行距",
            valueLabel = String.format(Locale.US, "%.2f", settings.lineFactor),
            value = settings.lineFactor,
            range = 1.20f..2.40f,
            step = 0.05f,
            onValue = { settings.lineFactor = it },
        )
        ReaderSliderRowV30(
            label = "段距",
            valueLabel = "${settings.paragraphSpacing.roundToInt()}",
            value = settings.paragraphSpacing,
            range = 0f..28f,
            step = 1f,
            onValue = { settings.paragraphSpacing = it },
        )
        ReaderSliderRowV30(
            label = "页边距",
            valueLabel = "${settings.sidePadding.roundToInt()}",
            value = settings.sidePadding,
            range = 8f..40f,
            step = 1f,
            onValue = { settings.sidePadding = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.FormatSize,
            title = "首行缩进",
            description = "段落首行缩进两个中文字符",
            checked = settings.indent,
            onCheckedChange = { settings.indent = it },
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Turn Panel                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderTurnPanelV30(
    settings: ReaderSettingsV30,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(title = "翻页方式", onBack = onBack)
        val options = listOf(
            Triple(ReaderTurnModeV30.COVER, "覆盖", Icons.Rounded.Layers),
            Triple(ReaderTurnModeV30.SLIDE, "平移", Icons.Rounded.ViewCarousel),
            Triple(ReaderTurnModeV30.SIMULATION, "仿真", Icons.Rounded.AutoStories),
            Triple(ReaderTurnModeV30.SCROLL, "上下滚动", Icons.Rounded.SwapVert),
            Triple(ReaderTurnModeV30.NONE, "无动画", Icons.Rounded.Block),
        )
        Column(verticalArrangement = Arrangement.spacedBy(t.space2)) {
            options.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(t.space2),
                ) {
                    row.forEach { (option, label, icon) ->
                        ReaderActionTileV30(
                            icon = icon,
                            label = label,
                            selected = settings.turnMode == option,
                            modifier = Modifier.weight(1f),
                            onClick = { settings.selectTurnMode(option) },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(t.space4))
        ReaderSwitchRowV30(
            icon = Icons.Rounded.TouchApp,
            title = "点击动画",
            description = "关闭后点击左右区域立即换页",
            checked = settings.clickAnimation,
            onCheckedChange = { settings.clickAnimation = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.Fullscreen,
            title = "全屏下一页",
            description = "除中间菜单区外点击均向后翻页",
            checked = settings.fullNext,
            onCheckedChange = { settings.fullNext = it },
        )
        ReaderSwitchRowV30(
            icon = Icons.Rounded.VolumeUp,
            title = "音量键翻页",
            description = "音量加减键控制阅读位置",
            checked = settings.volumeTurn,
            onCheckedChange = { settings.volumeTurn = it },
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Search Panel                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderSearchPanelV30(
    chapters: List<ChapterDraft>,
    onJumpChapter: (Int, Int) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val windowHeight = readerMenuWindowHeightV63()
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<ReaderSearchHitV30>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query, chapters) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(220)
        searching = true
        hits = withContext(Dispatchers.Default) {
            val result = ArrayList<ReaderSearchHitV30>()
            for ((index, chapter) in chapters.withIndex()) {
                val body = readerNormalizeBodyV14(
                    readerBodyWithoutDuplicateHeadingV13(chapter.title, chapter.content),
                )
                var offset = body.indexOf(keyword, ignoreCase = true)
                var chapterHits = 0
                while (offset >= 0 && chapterHits < 4 && result.size < 100) {
                    val start = (offset - 20).coerceAtLeast(0)
                    val end = (offset + keyword.length + 40).coerceAtMost(body.length)
                    result += ReaderSearchHitV30(
                        chapterIndex = index,
                        title = readerDisplayChapterTitleV13(
                            chapter.title, chapter.chapterNumber,
                        ),
                        offset = offset,
                        preview = body.substring(start, end)
                            .replace(Regex("\\s+"), " "),
                    )
                    chapterHits++
                    offset = body.indexOf(
                        string = keyword,
                        startIndex = (offset + keyword.length).coerceAtMost(body.length),
                        ignoreCase = true,
                    )
                }
                if (result.size >= 100) break
            }
            result
        }
        searching = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(title = "全文搜索", onBack = onBack)
        val fieldShape = RoundedCornerShape(t.radiusMd)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(color = t.input, shape = fieldShape)
                .border(width = 1.dp, color = t.border, shape = fieldShape)
                .padding(horizontal = t.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = t.mutedForeground,
            )
            Spacer(Modifier.width(t.space2))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isBlank()) {
                    Text(
                        text = "搜索整本书",
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = t.foreground,
                    ),
                    cursorBrush = SolidColor(t.primary),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier.size(32.dp).clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(18.dp),
                        tint = t.mutedForeground,
                    )
                }
            }
        }
        Text(
            text = when {
                query.isBlank() -> "输入关键词，点击结果会定位到原文位置。"
                searching -> "正在搜索…"
                hits.size >= 100 -> "找到至少 100 处，仅显示前 100 处"
                else -> "找到 ${hits.size} 处"
            },
            modifier = Modifier.padding(vertical = t.space2),
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = windowHeight * 0.42f),
            verticalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            items(items = hits, key = { "${it.chapterIndex}:${it.offset}" }) { hit ->
                val shape = RoundedCornerShape(t.radiusMd)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = t.card, shape = shape)
                        .border(width = 1.dp, color = t.border, shape = shape)
                        .clickable { onJumpChapter(hit.chapterIndex, hit.offset) }
                        .padding(t.space3),
                ) {
                    Text(
                        text = hit.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = t.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(t.space1))
                    Text(
                        text = hit.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.foreground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Stats Panel                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderStatsPanelV30(
    onBack: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val windowHeight = readerMenuWindowHeightV63()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = windowHeight * 0.62f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = t.space4).padding(bottom = t.space4),
    ) {
        ReaderPanelHeaderV30(title = "阅读统计", onBack = onBack)
        ReaderDailyGoalPanelV50()
    }
}


/* -------------------------------------------------------------------------- */
/*                               Rename Dialog                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderRenameChapterDialogV30(
    chapter: ChapterDraft,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var value by remember(chapter.id) { mutableStateOf(chapter.title) }
    val valid = value.trim().isNotBlank()
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .langhuanEnterOnMount()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "重命名章节",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space3))
            val inputShape = RoundedCornerShape(t.radiusMd)
            BasicTextField(
                value = value,
                onValueChange = { value = it.take(80) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(color = t.input, shape = inputShape)
                    .border(width = 1.dp, color = t.border, shape = inputShape)
                    .padding(horizontal = t.space3),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.foreground),
                cursorBrush = SolidColor(t.primary),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isBlank()) {
                            Text(text = "章节标题", color = t.mutedForeground)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(t.space4))
            ReaderDialogButtonsV30(
                confirmText = "保存",
                confirmEnabled = valid,
                onDismiss = onDismiss,
                onConfirm = { onConfirm(value.trim()) },
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Delete Dialog                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderDeleteLastChapterDialogV30(
    chapter: ChapterDraft?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .langhuanEnterOnMount()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "删除最后一章？",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = chapter?.let {
                    "将删除「${
                        readerDisplayChapterTitleV13(it.title, it.chapterNumber)
                    }」。"
                } ?: "当前没有可删除的章节。",
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
            )
            Spacer(Modifier.height(t.space4))
            ReaderDialogButtonsV30(
                confirmText = "删除",
                destructive = true,
                confirmEnabled = chapter != null,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Bottom Tabs                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderMenuTabsV30(
    tab: ReaderMenuTabV30,
    onTab: (ReaderMenuTabV30) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val reduced = LocalLanghuanReducedMotion.current
    val items = listOf(
        ReaderMenuTabV30.DETAILS to "详情",
        ReaderMenuTabV30.DIRECTORY to "目录",
        ReaderMenuTabV30.MORE to "更多",
    )
    val selectedIndex = items.indexOfFirst { it.first == tab }.coerceAtLeast(0)
    // The pill follows the active tab (transitions.dev "tabs sliding"); only translationX animates.
    val indicator by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else tween(LanghuanMotion.DURATION_STANDARD, easing = LanghuanMotion.EaseInOut),
        label = "readerTabIndicator",
    )
    val outerShape = RoundedCornerShape(t.radiusMd)
    val innerShape = RoundedCornerShape(t.radiusSm)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = t.space3, vertical = t.space1),
    ) {
        val segment = maxWidth / items.size
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = t.input, shape = outerShape)
                .padding(3.dp),
        ) {
            val inner = (segment * items.size - 6.dp) / items.size
            Box(
                modifier = Modifier
                    .width(inner)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = indicator * inner.toPx() }
                    .background(color = t.card, shape = innerShape)
                    .border(width = 1.dp, color = t.border, shape = innerShape),
            )
            Row(modifier = Modifier.fillMaxSize()) {
                items.forEach { (item, label) ->
                    val selected = tab == item
                    val textColor by animateColorAsState(
                        targetValue = if (selected) t.foreground else t.mutedForeground,
                        animationSpec = LanghuanMotion.standard(reduced = reduced),
                        label = "readerTabText",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab) { onTab(item) }
                            .semantics { contentDescription = "阅读菜单：$label" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                           Common Components                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderMenuHandleV30() {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier.fillMaxWidth().height(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .background(color = t.mutedForeground.copy(alpha = 0.35f), shape = CircleShape),
        )
    }
}

@Composable
private fun ReaderMenuDividerV30() {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier.fillMaxWidth().height(1.dp).background(t.border),
    )
}

@Composable
private fun ReaderSectionTitleV30(
    title: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = t.foreground,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ReaderPanelHeaderV30(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "返回",
                modifier = Modifier.size(22.dp),
                tint = t.secondaryForeground,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        action?.invoke()
    }
}

@Composable
private fun ReaderActionTileV30(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Column(
        modifier = modifier
            .height(74.dp)
            .background(
                color = if (selected) t.accent else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(t.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (selected) t.primary else t.secondaryForeground,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReaderFontSizeStepperV30(settings: ReaderSettingsV30) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderStepperButtonV30(
            text = "A−",
            enabled = settings.fontSize > 12f,
            modifier = Modifier.weight(1f),
            onClick = {
                settings.fontSize = (settings.fontSize - 1f).coerceAtLeast(12f)
            },
        )
        val shape = RoundedCornerShape(t.radiusMd)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .background(color = t.input, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${settings.fontSize.roundToInt()}sp",
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        ReaderStepperButtonV30(
            text = "A+",
            enabled = settings.fontSize < 34f,
            modifier = Modifier.weight(1f),
            onClick = {
                settings.fontSize = (settings.fontSize + 1f).coerceAtMost(34f)
            },
        )
    }
}

@Composable
private fun ReaderStepperButtonV30(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .height(46.dp)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (text == "A+") "增大阅读字号" else "减小阅读字号" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) t.primary else t.mutedForeground.copy(alpha = 0.4f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ReaderSettingsNavigationRowV30(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (destructive) t.destructive.copy(alpha = 0.08f)
                    else t.input,
                    shape = RoundedCornerShape(t.radiusSm),
                )
                .border(
                    width = 1.dp, color = t.border,
                    shape = RoundedCornerShape(t.radiusSm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (destructive) t.destructive else t.secondaryForeground,
            )
        }
        Spacer(Modifier.width(t.space3))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> t.mutedForeground.copy(alpha = 0.45f)
                destructive -> t.destructive
                else -> t.foreground
            },
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                modifier = Modifier.width(112.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ReaderSwitchRowV30(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (checked) t.accent else t.input,
                    shape = RoundedCornerShape(t.radiusSm),
                )
                .border(
                    width = 1.dp, color = t.border,
                    shape = RoundedCornerShape(t.radiusSm),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (checked) t.primary else t.secondaryForeground,
            )
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = t.space1),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
        ReaderSwitchV30(checked = checked)
    }
}

@Composable
private fun ReaderSwitchV30(checked: Boolean) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .background(
                color = if (checked) t.primary else t.input,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (checked) t.primary else t.border,
                shape = CircleShape,
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 20.dp else 2.dp, y = 2.dp)
                .size(20.dp)
                .background(
                    color = if (checked) t.card else t.mutedForeground,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun ReaderChoiceChipV30(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                color = if (selected) t.accent else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderSliderRowV30(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValue: (Float) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(54.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { raw ->
                val rounded = (raw / step).roundToInt() * step
                onValue(rounded.coerceIn(range.start, range.endInclusive))
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = t.primary,
                activeTrackColor = t.primary,
                inactiveTrackColor = t.border,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        Text(
            text = valueLabel,
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.labelMedium,
            color = t.mutedForeground,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ReaderDirectorySegmentV30(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Box(
        modifier = Modifier
            .height(34.dp)
            .background(
                color = if (selected) t.accent else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.accentForeground else t.secondaryForeground,
        )
    }
}

@Composable
private fun ReaderSmallIconActionV30(
    icon: ImageVector,
    contentDescription: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier.size(36.dp).clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = t.primary,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = t.secondaryForeground,
            )
        }
    }
}

@Composable
private fun ReaderInlineTextActionV30(
    text: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2, vertical = t.space1),
        style = MaterialTheme.typography.labelMedium,
        color = t.primary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun ReaderInlineErrorV30(text: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = t.space4, vertical = t.space1),
        style = MaterialTheme.typography.bodySmall,
        color = t.destructive,
    )
}

@Composable
private fun ReaderMenuEmptyV30(
    title: String,
    description: String,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space5, vertical = t.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.height(t.space3))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
        )
        Spacer(Modifier.height(t.space2))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReaderDialogButtonsV30(
    confirmText: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(t.space2),
    ) {
        val cancelShape = RoundedCornerShape(t.radiusMd)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .background(color = t.card, shape = cancelShape)
                .border(width = 1.dp, color = t.border, shape = cancelShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelLarge,
                color = t.secondaryForeground,
            )
        }
        val confirmShape = RoundedCornerShape(t.radiusMd)
        val confirmBackground = if (destructive) t.destructive else t.primary
        val confirmForeground = if (destructive) t.destructiveForeground else t.card
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .background(
                    color = confirmBackground.copy(alpha = if (confirmEnabled) 1f else 0.42f),
                    shape = confirmShape,
                )
                .border(
                    width = 1.dp,
                    color = confirmBackground.copy(alpha = if (confirmEnabled) 1f else 0.42f),
                    shape = confirmShape,
                )
                .clickable(enabled = confirmEnabled, onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = confirmText,
                style = MaterialTheme.typography.labelLarge,
                color = confirmForeground.copy(alpha = if (confirmEnabled) 1f else 0.65f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Helpers                                   */
/* -------------------------------------------------------------------------- */

private fun readerThemeDisplayNameV30(key: String): String = when (key) {
    "paper" -> "纸白"
    "sheep" -> "暖纸"
    "tea" -> "茶纸"
    "green" -> "青叶"
    "langhuan" -> "雾蓝"
    "pink" -> "樱粉"
    "white" -> "纯白"
    "night" -> "夜间"
    else -> "纸白"
}

private fun readerFontLabelV30(key: String): String = when (key) {
    "serif" -> "系统宋体"
    "mono" -> "等宽字体"
    else -> "系统黑体"
}
