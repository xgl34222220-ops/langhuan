package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import org.readium.r2.shared.publication.Link


/* -------------------------------------------------------------------------- */
/*                                    Model                                   */
/* -------------------------------------------------------------------------- */

/**
 * EPUB V50 原版阅读器的纯 UI 状态。
 *
 * 真正 EPUB：Publication / Locator / Readium Navigator / 原文件关联 /
 * Locator 持久化仍由现有 EPUB 宿主负责。
 *
 * 此状态只描述页面当前需要显示的内容，不复制 EPUB 数据层。
 */
internal data class EpubReaderUiStateV50(
    val publicationTitle: String = "",
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val bookProgress: Float = 0f,
    val loaded: Boolean = false,
    val statusMessage: String = "正在打开 EPUB 原版…",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val bookmarked: Boolean = false,
)

/** Readium 目录扁平化后的 UI 行。 */
private data class EpubTocRowV50(
    val link: Link,
    val depth: Int,
)


/* -------------------------------------------------------------------------- */
/*                                  Screen                                    */
/* -------------------------------------------------------------------------- */

@Composable
internal fun EpubReaderScreenV50(
    book: ReaderBookUi,
    prefs: SharedPreferences,
    state: EpubReaderUiStateV50,
    tableOfContents: List<Link>,
    currentTocIndex: Int,
    readerHostId: Int,
    selection: ReaderSelectionV30?,
    onReaderHostReady: (FrameLayout) -> Unit,
    onBack: () -> Unit,
    onOpenTextVersion: () -> Unit,
    onOpenOriginalVersion: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onNavigateTo: (Link) -> Unit,
    onToggleBookmark: () -> Unit,
    onRelinkOriginal: () -> Unit,
    onClearSelection: () -> Unit,
    onNoteSaved: () -> Unit = {},
    onHighlightChanged: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    var directoryOpen by remember { mutableStateOf(false) }
    var noteSelection by remember { mutableStateOf<ReaderSelectionV30?>(null) }
    var highlightSelection by remember { mutableStateOf<ReaderSelectionV30?>(null) }

    LaunchedEffect(selection) {
        if (selection == null) {
            noteSelection = null
            highlightSelection = null
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(t.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            /* ------------------------------- Top bar ------------------------------- */
            EpubReaderTopBarV50(
                bookTitle = state.publicationTitle.ifBlank { book.title },
                onBack = onBack,
                onOriginal = onOpenOriginalVersion,
                onText = onOpenTextVersion,
                onDirectory = { directoryOpen = true },
            )

            /* ---------------------------- Chapter heading ---------------------------- */
            if (state.loaded) {
                EpubReaderChapterHeadingV50(
                    chapterIndex = state.chapterIndex,
                    chapterTitle = state.chapterTitle,
                    bookmarked = state.bookmarked,
                    onToggleBookmark = onToggleBookmark,
                )
            }

            /* --------------------------- Readium original host --------------------------- */
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(t.card),
            ) {
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).apply {
                            id = readerHostId
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            onReaderHostReady(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!state.loaded) {
                    EpubReaderStatusOverlayV50(
                        message = state.statusMessage,
                        onRelinkOriginal = onRelinkOriginal,
                    )
                }

                if (state.loaded && selection != null) {
                    EpubSelectionBarV50(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                horizontal = t.space4,
                                bottom = t.space3,
                            ),
                        onNote = { noteSelection = selection },
                        onHighlight = { highlightSelection = selection },
                        onDismiss = onClearSelection,
                    )
                }
            }

            /* ----------------------------- Bottom pagination ----------------------------- */
            EpubReaderBottomBarV50(
                pageIndex = state.pageIndex,
                pageCount = state.pageCount,
                bookProgress = state.bookProgress,
                previousEnabled = state.loaded && state.canGoBack,
                nextEnabled = state.loaded && state.canGoForward,
                onPrevious = onPreviousPage,
                onNext = onNextPage,
            )
        }

        if (directoryOpen) {
            EpubDirectoryDialogV50(
                bookTitle = state.publicationTitle.ifBlank { book.title },
                tableOfContents = tableOfContents,
                currentTocIndex = currentTocIndex,
                onDismiss = { directoryOpen = false },
                onNavigate = { link ->
                    directoryOpen = false
                    onNavigateTo(link)
                },
            )
        }

        noteSelection?.let { picked ->
            ReaderParagraphNoteEditorV50(
                prefs = prefs,
                bookId = book.id,
                selection = picked,
                chapterTitle = state.chapterTitle,
                onDismiss = {
                    noteSelection = null
                    onClearSelection()
                },
                onSaved = {
                    noteSelection = null
                    onClearSelection()
                    onNoteSaved()
                },
            )
        }

        highlightSelection?.let { picked ->
            ReaderParagraphHighlightPickerV50(
                prefs = prefs,
                bookId = book.id,
                selection = picked,
                chapterTitle = state.chapterTitle,
                onDismiss = {
                    highlightSelection = null
                    onClearSelection()
                },
                onSaved = {
                    highlightSelection = null
                    onClearSelection()
                    onHighlightChanged()
                },
                onDeleted = {
                    highlightSelection = null
                    onClearSelection()
                    onHighlightChanged()
                },
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Top Bar                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubReaderTopBarV50(
    bookTitle: String,
    onBack: () -> Unit,
    onOriginal: () -> Unit,
    onText: () -> Unit,
    onDirectory: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.background)
            .statusBarsPadding()
            .padding(horizontal = t.space4, vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpubIconButtonV50(
            icon = Icons.Rounded.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = "EPUB 原版",
                style = MaterialTheme.typography.labelSmall,
                color = t.goldForeground,
            )
        }
        Spacer(Modifier.width(t.space2))
        EpubVersionSegmentV50(
            onOriginal = onOriginal,
            onText = onText,
        )
        Spacer(Modifier.width(t.space2))
        EpubIconButtonV50(
            icon = Icons.Rounded.MenuBook,
            contentDescription = "目录",
            onClick = onDirectory,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Version Segment                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubVersionSegmentV50(
    onOriginal: () -> Unit,
    onText: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .height(38.dp)
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpubVersionItemV50(text = "原版", selected = true, onClick = onOriginal)
        EpubVersionItemV50(text = "文字版", selected = false, onClick = onText)
    }
}

@Composable
private fun EpubVersionItemV50(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Box(
        modifier = Modifier
            .height(30.dp)
            .background(
                color = if (selected) t.card else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.border else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.primary else t.mutedForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                            Chapter Heading                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubReaderChapterHeadingV50(
    chapterIndex: Int,
    chapterTitle: String,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.background)
            .padding(
                start = t.space4, end = t.space4,
                top = t.space3, bottom = t.space4,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = epubChineseChapterLabelV50(chapterIndex + 1),
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = chapterTitle.ifBlank { "正文" },
                modifier = Modifier.padding(horizontal = t.space6),
                style = MaterialTheme.typography.headlineSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space3))
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(2.dp)
                    .background(color = t.gold, shape = CircleShape),
            )
        }
        EpubBookmarkButtonV50(
            bookmarked = bookmarked,
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = onToggleBookmark,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Bookmark                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubBookmarkButtonV50(
    bookmarked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = modifier
            .size(38.dp)
            .background(
                color = if (bookmarked) t.goldContainer else t.card,
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (bookmarked) Icons.Outlined.Bookmark
            else Icons.Outlined.BookmarkBorder,
            contentDescription = if (bookmarked) "取消书签" else "添加书签",
            modifier = Modifier.size(19.dp),
            tint = if (bookmarked) t.gold else t.secondaryForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Bottom Bar                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubReaderBottomBarV50(
    pageIndex: Int,
    pageCount: Int,
    bookProgress: Float,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.background)
            .navigationBarsPadding()
            .padding(horizontal = t.space4, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpubPageButtonV50(
            icon = Icons.Rounded.ChevronLeft,
            contentDescription = "上一页",
            enabled = previousEnabled,
            onClick = onPrevious,
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            EpubProgressTrackV50(progress = bookProgress)
            Spacer(Modifier.height(t.space2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        pageCount <= 0 -> "正在排版"
                        else -> "第 ${(pageIndex + 1).coerceAtMost(pageCount)} / $pageCount 页"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = t.secondaryForeground,
                )
                Text(
                    text = "${(bookProgress.coerceIn(0f, 1f) * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                )
            }
        }
        Spacer(Modifier.width(t.space3))
        EpubPageButtonV50(
            icon = Icons.Rounded.ChevronRight,
            contentDescription = "下一页",
            enabled = nextEnabled,
            onClick = onNext,
        )
    }
}

@Composable
private fun EpubProgressTrackV50(progress: Float) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(color = t.border, shape = CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(color = t.primary, shape = CircleShape),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Page Buttons                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubPageButtonV50(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = if (enabled) t.card else t.input,
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = if (enabled) t.primary else t.mutedForeground.copy(alpha = 0.4f),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Status                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubReaderStatusOverlayV50(
    message: String,
    onRelinkOriginal: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier.fillMaxSize().background(t.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = t.space6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = t.primary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(t.space4))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(t.space4))
            val shape = RoundedCornerShape(t.radiusMd)
            Row(
                modifier = Modifier
                    .height(42.dp)
                    .background(color = t.card, shape = shape)
                    .border(width = 1.dp, color = t.border, shape = shape)
                    .clickable(onClick = onRelinkOriginal)
                    .padding(horizontal = t.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = t.primary,
                )
                Spacer(Modifier.width(t.space2))
                Text(
                    text = "重新关联原 EPUB",
                    style = MaterialTheme.typography.labelLarge,
                    color = t.primary,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Selection Bar                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubSelectionBarV50(
    modifier: Modifier = Modifier,
    onNote: () -> Unit,
    onHighlight: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = modifier
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space2),
        horizontalArrangement = Arrangement.spacedBy(t.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpubSelectionActionV50(
            icon = Icons.Rounded.EditNote,
            text = "笔记",
            onClick = onNote,
        )
        EpubSelectionActionV50(
            icon = Icons.Rounded.FormatQuote,
            text = "划线",
            gold = true,
            onClick = onHighlight,
        )
        EpubSelectionActionV50(
            icon = Icons.Rounded.Close,
            text = "取消",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun EpubSelectionActionV50(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    gold: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .height(38.dp)
            .background(
                color = if (gold) t.goldContainer else t.input,
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (gold) t.goldForeground else t.secondaryForeground,
        )
        Spacer(Modifier.width(t.space1))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (gold) t.goldForeground else t.secondaryForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                             Directory Dialog                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubDirectoryDialogV50(
    bookTitle: String,
    tableOfContents: List<Link>,
    currentTocIndex: Int,
    onDismiss: () -> Unit,
    onNavigate: (Link) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val flattened = remember(tableOfContents) { epubFlattenTocV50(tableOfContents) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(
                topStart = t.radiusXl,
                topEnd = t.radiusXl,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .background(color = t.background, shape = shape)
                    .border(width = 1.dp, color = t.border, shape = shape)
                    .navigationBarsPadding()
                    .padding(top = t.space3),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = t.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "目录",
                            style = MaterialTheme.typography.titleLarge,
                            color = t.foreground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(t.space1))
                        Text(
                            text = bookTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    EpubIconButtonV50(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭目录",
                        onClick = onDismiss,
                    )
                }
                Spacer(Modifier.height(t.space3))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(t.border),
                )
                if (flattened.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(t.space6),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = t.mutedForeground,
                        )
                        Spacer(Modifier.height(t.space3))
                        Text(
                            text = "这本 EPUB 没有目录",
                            style = MaterialTheme.typography.titleMedium,
                            color = t.foreground,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = t.space3,
                            vertical = t.space2,
                        ),
                    ) {
                        itemsIndexed(
                            items = flattened,
                            key = { index, row -> "${index}:${row.link.url()}" },
                        ) { index, row ->
                            EpubDirectoryRowV50(
                                row = row,
                                selected = index == currentTocIndex,
                                onClick = { onNavigate(row.link) },
                            )
                        }
                    }
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Directory Row                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubDirectoryRowV50(
    row: EpubTocRowV50,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = (row.depth.coerceIn(0, 5) * 12).dp,
                bottom = t.space1,
            )
            .background(
                color = if (selected) t.accent else Color.Transparent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space3, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .background(color = t.gold, shape = CircleShape),
            )
            Spacer(Modifier.width(t.space2))
        }
        Text(
            text = row.link.title?.takeIf { it.isNotBlank() } ?: "章节",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) t.accentForeground else t.foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelSmall,
                color = t.goldForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Icon Button                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun EpubIconButtonV50(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = t.secondaryForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Helpers                                   */
/* -------------------------------------------------------------------------- */

private fun epubFlattenTocV50(links: List<Link>): List<EpubTocRowV50> {
    val result = ArrayList<EpubTocRowV50>()
    fun add(source: List<Link>, depth: Int) {
        source.forEach { link ->
            result += EpubTocRowV50(link = link, depth = depth)
            if (link.children.isNotEmpty()) add(source = link.children, depth = depth + 1)
        }
    }
    add(source = links, depth = 0)
    return result
}

/** 原型使用「第一章」这种小字章节序号。 */
private fun epubChineseChapterLabelV50(number: Int): String {
    val safe = number.coerceAtLeast(1)
    return "第${epubChineseNumberV50(safe)}章"
}

private fun epubChineseNumberV50(number: Int): String {
    val digits = arrayOf(
        "零", "一", "二", "三", "四", "五", "六", "七", "八", "九",
    )
    return when {
        number < 10 -> digits[number]
        number < 20 -> "十" + if (number % 10 == 0) "" else digits[number % 10]
        number < 100 -> buildString {
            append(digits[number / 10])
            append("十")
            val ones = number % 10
            if (ones != 0) append(digits[ones])
        }
        number < 1000 -> {
            val hundreds = number / 100
            val remainder = number % 100
            buildString {
                append(digits[hundreds])
                append("百")
                when {
                    remainder == 0 -> Unit
                    remainder < 10 -> {
                        append("零")
                        append(digits[remainder])
                    }
                    else -> append(epubChineseNumberV50(remainder))
                }
            }
        }
        else -> number.toString()
    }
}
