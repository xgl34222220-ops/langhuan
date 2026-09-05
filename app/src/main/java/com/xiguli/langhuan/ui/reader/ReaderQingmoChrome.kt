package com.xiguli.langhuan.ui.reader

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.theme.LanghuanShape
import kotlin.math.roundToInt

private enum class ReaderQingmoTab(val label: String) {
    DETAILS("详情"), DIRECTORY("目录"), MORE("更多")
}

private enum class ReaderQingmoQuick { FONT, LINE, PAGE }

private data class ReaderQingmoAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val action: () -> Unit,
)

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
    fontKey: String,
    lineFactor: Float,
    pageModeKey: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onBookmark: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onProgress: (Float) -> Unit,
    onDirectory: () -> Unit,
    onSearch: () -> Unit,
    onNight: () -> Unit,
    onFontKey: (String) -> Unit,
    onLineFactor: (Float) -> Unit,
    onPageMode: (String) -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
) {
    var tab by remember { mutableStateOf(ReaderQingmoTab.MORE) }
    var quick by remember { mutableStateOf<ReaderQingmoQuick?>(null) }

    Box(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = palette.chrome,
            contentColor = palette.foreground,
            shadowElevation = 4.dp,
            shape = LanghuanShape.sheetTop,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.width(32.dp).height(3.dp),
                        shape = CircleShape,
                        color = palette.secondary.copy(alpha = .22f),
                    ) {}
                }

                when (quick) {
                    ReaderQingmoQuick.FONT -> ReaderQingmoFontQuick(
                        fontKey = fontKey,
                        palette = palette,
                        onSelect = onFontKey,
                        onMore = onSettings,
                    )
                    ReaderQingmoQuick.LINE -> ReaderQingmoLineQuick(
                        lineFactor = lineFactor,
                        palette = palette,
                        onSelect = onLineFactor,
                    )
                    ReaderQingmoQuick.PAGE -> ReaderQingmoPageQuick(
                        pageModeKey = pageModeKey,
                        palette = palette,
                        onSelect = onPageMode,
                    )
                    null -> when (tab) {
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
                        ReaderQingmoTab.DIRECTORY -> Unit
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
                            onFont = { quick = ReaderQingmoQuick.FONT },
                            onLine = { quick = ReaderQingmoQuick.LINE },
                            onPage = { quick = ReaderQingmoQuick.PAGE },
                            onSettings = onSettings,
                            onStory = onStory,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    ReaderQingmoTab.entries.forEach { item ->
                        Text(
                            item.label,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    quick = null
                                    if (item == ReaderQingmoTab.DIRECTORY) onDirectory() else tab = item
                                }
                                .padding(vertical = 9.dp),
                            color = if (quick == null && tab == item) palette.foreground else palette.secondary.copy(alpha = .62f),
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            fontWeight = if (quick == null && tab == item) FontWeight.SemiBold else FontWeight.Normal,
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            readerDisplayChapterTitleV13(chapter.title, chapter.chapterNumber),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = palette.foreground,
            fontWeight = FontWeight.SemiBold,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
        )
        Text(
            "$bookTitle · ${chapterIndex + 1}/${chapterCount.coerceAtLeast(1)}",
            Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = palette.secondary,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%", color = palette.secondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
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
            Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onBack).padding(8.dp),
            color = palette.secondary,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
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
    onFont: () -> Unit,
    onLine: () -> Unit,
    onPage: () -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
) {
    val actions = listOf(
        ReaderQingmoAction(Icons.Rounded.DarkMode, "主题", action = onNight),
        ReaderQingmoAction(Icons.Rounded.TextFields, "字体", action = onFont),
        ReaderQingmoAction(Icons.Rounded.FormatLineSpacing, "行距", action = onLine),
        ReaderQingmoAction(Icons.Rounded.ViewCarousel, "翻页", action = onPage),
        ReaderQingmoAction(Icons.Rounded.FormatListBulleted, "目录", action = onDirectory),
        ReaderQingmoAction(Icons.Rounded.Search, "全文搜索", action = onSearch),
        ReaderQingmoAction(Icons.Rounded.BookmarkAdd, if (bookmarked) "已书签" else "加书签", action = onBookmark),
        ReaderQingmoAction(Icons.Rounded.Tune, "排版", action = onSettings),
        ReaderQingmoAction(Icons.Rounded.KeyboardArrowLeft, "上一章", enabled = canPrevious, action = onPrevious),
        ReaderQingmoAction(Icons.Rounded.KeyboardArrowRight, "下一章", enabled = canNext, action = onNext),
        ReaderQingmoAction(Icons.Rounded.Info, "图书详情", action = onInfo),
        ReaderQingmoAction(Icons.Rounded.AutoAwesome, "故事模式", action = onStory),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp)) {
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { action -> ReaderQingmoGridAction(action, palette, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ReaderQingmoFontQuick(
    fontKey: String,
    palette: ReaderExperiencePalette,
    onSelect: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("字体", color = palette.foreground, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleSmall.fontSize)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("default" to "默认", "serif" to "衬线", "sans" to "无衬线", "mono" to "等宽").forEach { (key, label) ->
                ReaderQingmoChoice(label, selected = fontKey == key, palette = palette, modifier = Modifier.weight(1f)) { onSelect(key) }
            }
        }
        Text("更多字体 / 导入字体", Modifier.align(Alignment.End).clickable(onClick = onMore).padding(top = 12.dp, bottom = 4.dp), color = palette.secondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}

@Composable
private fun ReaderQingmoLineQuick(
    lineFactor: Float,
    palette: ReaderExperiencePalette,
    onSelect: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("行距", color = palette.foreground, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleSmall.fontSize)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1.45f to "紧凑", 1.68f to "舒适", 1.85f to "宽松", 2.05f to "大").forEach { (value, label) ->
                ReaderQingmoChoice(label, selected = kotlin.math.abs(lineFactor - value) < .08f, palette = palette, modifier = Modifier.weight(1f)) { onSelect(value) }
            }
        }
    }
}

@Composable
private fun ReaderQingmoPageQuick(
    pageModeKey: String,
    palette: ReaderExperiencePalette,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("翻页", color = palette.foreground, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleSmall.fontSize)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderPageModeV10.entries.forEach { mode ->
                ReaderQingmoChoice(mode.label, selected = pageModeKey == mode.key, palette = palette, modifier = Modifier.weight(1f)) { onSelect(mode.key) }
            }
        }
    }
}

@Composable
private fun ReaderQingmoChoice(
    label: String,
    selected: Boolean,
    palette: ReaderExperiencePalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = LanghuanShape.chip,
        color = if (selected) palette.foreground.copy(alpha = .10f) else palette.secondary.copy(alpha = .06f),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            textAlign = TextAlign.Center,
            color = if (selected) palette.foreground else palette.secondary,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReaderQingmoGridAction(action: ReaderQingmoAction, palette: ReaderExperiencePalette, modifier: Modifier = Modifier) {
    val alpha = if (action.enabled) 1f else .32f
    Column(
        modifier.clickable(enabled = action.enabled, onClick = action.action).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = palette.secondary.copy(alpha = .08f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(action.icon, null, Modifier.size(21.dp), tint = palette.foreground.copy(alpha = alpha))
            }
        }
        Text(action.label, Modifier.padding(top = 5.dp), color = palette.secondary.copy(alpha = .82f * alpha), fontSize = MaterialTheme.typography.labelSmall.fontSize, maxLines = 1)
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
        Text(label, Modifier.padding(top = 3.dp), color = palette.secondary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}
