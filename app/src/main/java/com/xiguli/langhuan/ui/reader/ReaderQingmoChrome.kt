package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.domain.ChapterDraft
import kotlin.math.roundToInt

private enum class ReaderQingmoTab(val label: String) {
    DETAILS("详情"), DIRECTORY("目录"), MORE("更多")
}

private data class ReaderQingmoAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

/**
 * Compact reader controls inspired by the interaction structure of mature Chinese readers:
 * the page stays visually quiet, and secondary actions live in a bottom Details/Directory/More panel.
 */
@Composable
internal fun ReaderQingmoChrome(
    modifier: Modifier,
    bookTitle: String,
    chapter: ChapterDraft,
    chapterIndex: Int,
    chapterCount: Int,
    fraction: Float,
    palette: ReaderExperiencePalette,
    bookmarked: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onBookmark: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Float) -> Unit,
    onDirectory: () -> Unit,
    onSearch: () -> Unit,
    onNight: () -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
) {
    var tab by remember { mutableStateOf(ReaderQingmoTab.MORE) }

    Box(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = palette.chrome.copy(alpha = .995f),
            contentColor = palette.foreground,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.width(34.dp).height(4.dp),
                        shape = CircleShape,
                        color = palette.secondary.copy(alpha = .28f),
                    ) {}
                }

                when (tab) {
                    ReaderQingmoTab.DETAILS -> ReaderQingmoDetails(
                        bookTitle = bookTitle,
                        chapter = chapter,
                        chapterIndex = chapterIndex,
                        chapterCount = chapterCount,
                        fraction = fraction,
                        palette = palette,
                        bookmarked = bookmarked,
                        canPrevious = canPrevious,
                        canNext = canNext,
                        onBack = onBack,
                        onInfo = onInfo,
                        onBookmark = onBookmark,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onProgress = onProgress,
                    )

                    ReaderQingmoTab.DIRECTORY -> {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Rounded.MenuBook, null, Modifier.size(28.dp), tint = palette.secondary)
                            Text(
                                "打开章节目录",
                                Modifier.padding(top = 8.dp).clickable(onClick = onDirectory).padding(10.dp),
                                color = palette.foreground,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    ReaderQingmoTab.MORE -> ReaderQingmoMoreGrid(
                        palette = palette,
                        bookmarked = bookmarked,
                        canPrevious = canPrevious,
                        canNext = canNext,
                        onInfo = onInfo,
                        onBookmark = onBookmark,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onDirectory = onDirectory,
                        onSearch = onSearch,
                        onNight = onNight,
                        onSettings = onSettings,
                        onStory = onStory,
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    ReaderQingmoTab.entries.forEach { item ->
                        Text(
                            item.label,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (item == ReaderQingmoTab.DIRECTORY) onDirectory() else tab = item
                                }
                                .padding(vertical = 10.dp),
                            color = if (tab == item) palette.foreground else palette.secondary.copy(alpha = .66f),
                            fontSize = 13.sp,
                            fontWeight = if (tab == item) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderQingmoDetails(
    bookTitle: String,
    chapter: ChapterDraft,
    chapterIndex: Int,
    chapterCount: Int,
    fraction: Float,
    palette: ReaderExperiencePalette,
    bookmarked: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onBookmark: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = palette.foreground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            "$bookTitle · ${chapterIndex + 1}/${chapterCount.coerceAtLeast(1)}",
            Modifier.padding(top = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = palette.secondary,
            fontSize = 12.sp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%", color = palette.secondary, fontSize = 11.sp)
            Slider(value = fraction.coerceIn(0f, 1f), onValueChange = onProgress, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ReaderQingmoSmallAction(Icons.Rounded.KeyboardArrowLeft, "上一章", canPrevious, palette, onPrevious)
            ReaderQingmoSmallAction(Icons.Rounded.BookmarkAdd, if (bookmarked) "已书签" else "书签", true, palette, onBookmark)
            ReaderQingmoSmallAction(Icons.Rounded.Info, "详情", true, palette, onInfo)
            ReaderQingmoSmallAction(Icons.Rounded.KeyboardArrowRight, "下一章", canNext, palette, onNext)
        }
        Text(
            "返回书架",
            Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).clickable(onClick = onBack).padding(8.dp),
            color = palette.secondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ReaderQingmoMoreGrid(
    palette: ReaderExperiencePalette,
    bookmarked: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onInfo: () -> Unit,
    onBookmark: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDirectory: () -> Unit,
    onSearch: () -> Unit,
    onNight: () -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
) {
    val actions = listOf(
        ReaderQingmoAction(Icons.Rounded.DarkMode, "主题", action = onNight),
        ReaderQingmoAction(Icons.Rounded.TextFields, "字体", action = onSettings),
        ReaderQingmoAction(Icons.Rounded.FormatLineSpacing, "行距", action = onSettings),
        ReaderQingmoAction(Icons.Rounded.ViewCarousel, "翻页", action = onSettings),
        ReaderQingmoAction(Icons.Rounded.FormatListBulleted, "目录", action = onDirectory),
        ReaderQingmoAction(Icons.Rounded.Search, "全文搜索", action = onSearch),
        ReaderQingmoAction(Icons.Rounded.BookmarkAdd, if (bookmarked) "已书签" else "加书签", action = onBookmark),
        ReaderQingmoAction(Icons.Rounded.Tune, "排版", action = onSettings),
        ReaderQingmoAction(Icons.Rounded.KeyboardArrowLeft, "上一章", enabled = canPrevious, action = onPrevious),
        ReaderQingmoAction(Icons.Rounded.KeyboardArrowRight, "下一章", enabled = canNext, action = onNext),
        ReaderQingmoAction(Icons.Rounded.Info, "图书详情", action = onInfo),
        ReaderQingmoAction(Icons.Rounded.AutoAwesome, "故事模式", action = onStory),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        actions.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { action ->
                    ReaderQingmoGridAction(action, palette, Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ReaderQingmoGridAction(action: ReaderQingmoAction, palette: ReaderExperiencePalette, modifier: Modifier = Modifier) {
    val alpha = if (action.enabled) 1f else .32f
    Column(
        modifier.clickable(enabled = action.enabled, onClick = action.action).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = palette.secondary.copy(alpha = .08f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(action.icon, null, Modifier.size(21.dp), tint = palette.foreground.copy(alpha = alpha))
            }
        }
        Text(
            action.label,
            Modifier.padding(top = 5.dp),
            color = palette.secondary.copy(alpha = .82f * alpha),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReaderQingmoSmallAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    palette: ReaderExperiencePalette,
    action: () -> Unit,
) {
    Column(
        Modifier.clickable(enabled = enabled, onClick = action).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = palette.foreground.copy(alpha = if (enabled) 1f else .28f))
        Text(label, Modifier.padding(top = 3.dp), color = palette.secondary, fontSize = 11.sp)
    }
}
