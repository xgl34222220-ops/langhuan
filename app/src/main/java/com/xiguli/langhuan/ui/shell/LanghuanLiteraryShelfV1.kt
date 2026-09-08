package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.settings.LanghuanSettingsExperienceV1
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LiteraryShelfTabV1 { HOME, LIBRARY, CREATION, PROFILE }
private enum class LiteraryShelfOverlayV1 { NONE, SETTINGS }

/**
 * First production shell for the Literary MIUIx / Langhuan Glass redesign.
 *
 * The previous Qingmo shelf is intentionally kept in the repository as a fallback while the new
 * shell is migrated. This screen only rearranges existing callbacks/data and does not change the
 * database, reader pagination, AI gateway, or writing engine.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanghuanLiteraryShelfV1(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    openingBookId: String?,
    onOpenBook: (String) -> Unit,
    onOpenTavern: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    val studioVm: StudioViewModel = viewModel()
    val studioState by studioVm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LiteraryShelfTabV1.HOME) }
    var overlay by rememberSaveable { mutableStateOf(LiteraryShelfOverlayV1.NONE) }

    if (overlay == LiteraryShelfOverlayV1.SETTINGS) {
        LanghuanSettingsExperienceV1(
            aiReady = studioState.provider.ready,
            onBack = { overlay = LiteraryShelfOverlayV1.NONE },
            onAi = onAiSetup,
            onSkills = onSkills,
            onRunCenter = onRunCenter,
        )
        return
    }

    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.background),
    ) {
        when (tab) {
            LiteraryShelfTabV1.HOME -> LiteraryHomeV1(
                state = state,
                importState = importState,
                openingBookId = openingBookId,
                onOpenBook = onOpenBook,
                onCreate = onCreate,
                onImport = onImportLocal,
                onOpenLibrary = { tab = LiteraryShelfTabV1.LIBRARY },
            )

            LiteraryShelfTabV1.LIBRARY -> LiteraryLibraryV1(
                state = state,
                openingBookId = openingBookId,
                onOpenBook = onOpenBook,
                onOpenTavern = onOpenTavern,
                onDeleteBook = onDeleteBook,
            )

            LiteraryShelfTabV1.CREATION -> LiteraryCreationV1(
                onCreate = onCreate,
                onImport = onImportLocal,
                onAiSetup = onAiSetup,
                onSkills = onSkills,
                onRunCenter = onRunCenter,
            )

            LiteraryShelfTabV1.PROFILE -> LiteraryProfileV1(
                state = state,
                aiReady = studioState.provider.ready,
                onSettings = { overlay = LiteraryShelfOverlayV1.SETTINGS },
                onAiSetup = onAiSetup,
            )
        }

        LiteraryBottomBarV1(
            selected = tab,
            onSelected = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LiteraryHomeV1(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    openingBookId: String?,
    onOpenBook: (String) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val recent = remember(state.stories) { state.stories.sortedByDescending { it.updatedAt } }
    val current = recent.firstOrNull()
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
                    Text(
                        "琅嬛",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.headlineLarge,
                        color = t.foreground,
                    )
                }
                LanghuanIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "打开书库",
                    onClick = onOpenLibrary,
                )
            }
        }

        item { LiterarySectionTitleV1("继续", action = "查看书库", onAction = onOpenLibrary) }

        item {
            if (current == null) {
                LanghuanCard(modifier = Modifier.fillMaxWidth(), depth = 2) {
                    Column {
                        Text("你的琅嬛还是空的", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                        Text(
                            "可以导入一本小说开始阅读，也可以和 AI 对话创建第一部作品。",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.mutedForeground,
                        )
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LiteraryPillActionV1("AI 建书", Icons.Rounded.AutoAwesome, onCreate)
                            LiteraryPillActionV1("导入小说", Icons.Rounded.FolderOpen, onImport)
                        }
                    }
                }
            } else {
                LiteraryContinueCardV1(
                    book = current,
                    busy = openingBookId == current.id,
                    onClick = { onOpenBook(current.id) },
                )
            }
        }

        item { LiterarySectionTitleV1("快速开始") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiteraryQuickCardV1(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.AutoAwesome,
                    title = "AI 建书",
                    subtitle = "像聊天一样把想法变成作品",
                    onClick = onCreate,
                )
                LiteraryQuickCardV1(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.FolderOpen,
                    title = "导入小说",
                    subtitle = "本地阅读与资料整理",
                    onClick = onImport,
                )
            }
        }

        if (recent.isNotEmpty()) {
            item { LiterarySectionTitleV1("最近作品", action = "全部", onAction = onOpenLibrary) }
            items(recent.take(4), key = { it.id }) { book ->
                LiteraryBookRowV1(
                    book = book,
                    busy = openingBookId == book.id,
                    onClick = { onOpenBook(book.id) },
                )
            }
        }

        if (importState.busy) {
            item {
                LanghuanCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                        Text(
                            text = "正在导入 ${importState.currentFileName.orEmpty()}",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiteraryLibraryV1(
    state: LibraryExperienceState,
    openingBookId: String?,
    onOpenBook: (String) -> Unit,
    onOpenTavern: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var query by rememberSaveable { mutableStateOf("") }
    var pendingActions by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    val books = remember(state.stories, query) {
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("书库", style = MaterialTheme.typography.headlineLarge, color = t.foreground)
                Text(
                    "${state.stories.size} 本作品 · 阅读与创作都在这里",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }
            LanghuanBadge(text = if (state.libraryLoaded) "已同步" else "加载中")
        }

        LiterarySearchV1(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )

        if (!state.libraryLoaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.primary, strokeWidth = 2.dp)
            }
        } else if (books.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "书库还是空的" else "没有找到匹配的作品", color = t.mutedForeground)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 118.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridItems(books, key = { it.id }) { book ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = openingBookId == null,
                                onClick = { onOpenBook(book.id) },
                                onLongClick = { pendingActions = book },
                            ),
                    ) {
                        LiteraryCoverV1(
                            book = book,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(.72f),
                            busy = openingBookId == book.id,
                        )
                        Text(
                            book.title,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = t.foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章",
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    pendingActions?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingActions = null },
            containerColor = t.card,
            title = { Text(book.title, color = t.foreground) },
            text = {
                Column {
                    Text("选择要进入的模式。", color = t.mutedForeground, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    LanghuanMenuRow(
                        icon = Icons.Rounded.Book,
                        title = "继续阅读",
                        subtitle = "回到上次章节",
                        onClick = { pendingActions = null; onOpenBook(book.id) },
                    )
                    LanghuanMenuRow(
                        icon = Icons.Rounded.TheaterComedy,
                        title = "进入故事",
                        subtitle = "打开互动故事模式",
                        onClick = { pendingActions = null; onOpenTavern(book.id) },
                    )
                    LanghuanMenuRow(
                        icon = Icons.Rounded.Close,
                        title = "删除作品",
                        subtitle = "删除章节与项目数据",
                        onClick = { pendingActions = null; pendingDelete = book },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingActions = null }) { Text("取消") } },
        )
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = t.card,
            title = { Text("删除《${book.title}》？") },
            text = { Text("章节、版本和项目数据会一起删除。") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDeleteBook(book.id) }) {
                    Text("删除", color = t.destructive)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun LiteraryCreationV1(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onAiSetup: () -> Unit,
    onSkills: () -> Unit,
    onRunCenter: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("创作", style = MaterialTheme.typography.headlineLarge, color = t.foreground)
            Text(
                "从一句想法开始，复杂能力由琅嬛在背后组织。",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.mutedForeground,
            )
        }

        item {
            LanghuanCard(modifier = Modifier.fillMaxWidth(), depth = 2) {
                Column {
                    LanghuanBadge("推荐", accent = true)
                    Text(
                        "和 AI 一起建一本书",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                    )
                    Text(
                        "不用填复杂表单。直接说题材、氛围、参考作品和你的要求，琅嬛会持续和你修改。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                    Surface(
                        onClick = onCreate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = t.primary,
                        contentColor = t.primaryForeground,
                        shadowElevation = 5.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(19.dp))
                            Text("开始 AI 建书", Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.Medium)
                            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item { LiterarySectionTitleV1("其他入口") }
        item {
            LanghuanCard(contentPadding = 0.dp) {
                Column {
                    LanghuanMenuRow(Icons.Rounded.FolderOpen, "导入小说", onImport, subtitle = "TXT · EPUB · Markdown")
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                    LanghuanMenuRow(Icons.Rounded.AutoAwesome, "Skill 与能力", onSkills, subtitle = "查看当前可用的创作能力")
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                    LanghuanMenuRow(Icons.Rounded.Settings, "AI 与模型", onAiSetup, subtitle = "模型、中转站与路由")
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                    LanghuanMenuRow(Icons.Rounded.TaskAlt, "运行中心", onRunCenter, subtitle = "查看正在执行的任务")
                }
            }
        }
    }
}

@Composable
private fun LiteraryProfileV1(
    state: LibraryExperienceState,
    aiReady: Boolean,
    onSettings: () -> Unit,
    onAiSetup: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val totalWords = remember(state.stories) { state.stories.sumOf { it.currentWords } }
    val updated = remember(state.stories) { state.stories.maxOfOrNull { it.updatedAt } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = t.accent, modifier = Modifier.size(54.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, null, tint = t.accentForeground, modifier = Modifier.size(25.dp))
                    }
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("我的琅嬛", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                    Text(
                        if (updated != null) "最近整理 ${formatDateV1(updated)}" else "从第一本书开始",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                }
                LanghuanIconButton(Icons.Rounded.Settings, "设置", onSettings)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiteraryStatV1("作品", state.stories.size.toString(), Modifier.weight(1f))
                LiteraryStatV1("总字数", compactWordsV1(totalWords), Modifier.weight(1f))
                LiteraryStatV1("AI", if (aiReady) "可用" else "待配置", Modifier.weight(1f))
            }
        }

        item { LiterarySectionTitleV1("状态") }
        item {
            LanghuanCard(contentPadding = 0.dp) {
                Column {
                    LanghuanMenuRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AI 服务",
                        subtitle = if (aiReady) "模型已就绪" else "尚未配置模型",
                        onClick = onAiSetup,
                        trailing = { LanghuanBadge(if (aiReady) "可用" else "待配置", accent = aiReady) },
                    )
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                    LanghuanMenuRow(
                        icon = Icons.Rounded.Settings,
                        title = "设置",
                        subtitle = "创作、阅读、外观、数据与关于",
                        onClick = onSettings,
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = t.mutedForeground) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LiteraryBottomBarV1(
    selected: LiteraryShelfTabV1,
    onSelected: (LiteraryShelfTabV1) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = t.card.copy(alpha = .97f),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LiteraryNavItemV1(Icons.Rounded.History, "首页", selected == LiteraryShelfTabV1.HOME, Modifier.weight(1f)) {
                onSelected(LiteraryShelfTabV1.HOME)
            }
            LiteraryNavItemV1(Icons.Rounded.Book, "书库", selected == LiteraryShelfTabV1.LIBRARY, Modifier.weight(1f)) {
                onSelected(LiteraryShelfTabV1.LIBRARY)
            }
            LiteraryNavItemV1(Icons.Rounded.AutoAwesome, "创作", selected == LiteraryShelfTabV1.CREATION, Modifier.weight(1f)) {
                onSelected(LiteraryShelfTabV1.CREATION)
            }
            LiteraryNavItemV1(Icons.Outlined.Person, "我的", selected == LiteraryShelfTabV1.PROFILE, Modifier.weight(1f)) {
                onSelected(LiteraryShelfTabV1.PROFILE)
            }
        }
    }
}

@Composable
private fun LiteraryNavItemV1(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = if (selected) t.accent else Color.Transparent,
        contentColor = if (selected) t.accentForeground else t.mutedForeground,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, Modifier.size(19.dp))
            Text(label, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LiteraryContinueCardV1(book: ReaderBookUi, busy: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick),
        contentPadding = 16.dp,
        depth = 2,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiteraryCoverV1(book, Modifier.width(82.dp).aspectRatio(.72f), busy)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                LanghuanBadge(if (book.currentWords > 0) "继续创作 / 阅读" else "开始阅读", accent = true)
                Text(
                    book.title,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = t.foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "第 ${book.currentChapter.coerceAtLeast(1)} 章 · ${compactWordsV1(book.currentWords)} 字",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                Text(
                    "继续进入",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = t.primary,
                )
            }
        }
    }
}

@Composable
private fun LiteraryQuickCardV1(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = 14.dp,
    ) {
        Column {
            Surface(shape = CircleShape, color = t.accent, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(19.dp), tint = t.accentForeground)
                }
            }
            Text(title, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, color = t.foreground)
            Text(
                subtitle,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiteraryBookRowV1(book: ReaderBookUi, busy: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiteraryCoverV1(book, Modifier.width(48.dp).aspectRatio(.72f), busy)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章",
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = t.mutedForeground)
    }
}

@Composable
private fun LiteraryCoverV1(book: ReaderBookUi, modifier: Modifier, busy: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    val bitmap = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(t.muted),
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(t.accentForeground.copy(alpha = .82f), t.primary.copy(alpha = .86f)),
                        ),
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(book.genre.ifBlank { "小说" }, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                Text(
                    book.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .16f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun LiterarySearchV1(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(t.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, Modifier.size(19.dp), tint = t.mutedForeground)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.foreground),
            decorationBox = { inner ->
                if (query.isBlank()) Text("搜索书名或类型", style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
                inner()
            },
        )
        if (query.isNotBlank()) {
            Icon(
                Icons.Rounded.Close,
                "清除",
                Modifier
                    .size(19.dp)
                    .clickable { onQueryChange("") },
                tint = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun LiterarySectionTitleV1(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val t = LocalLanghuanUiTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = t.foreground)
        if (action != null && onAction != null) {
            Text(
                action,
                modifier = Modifier.clickable(onClick = onAction).padding(6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.primary,
            )
        }
    }
}

@Composable
private fun LiteraryPillActionV1(label: String, icon: ImageVector, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = t.accent,
        contentColor = t.accentForeground,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LiteraryStatV1(label: String, value: String, modifier: Modifier) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(modifier = modifier, contentPadding = 12.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = t.foreground)
            Text(label, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
    }
}

private fun compactWordsV1(value: Int): String = when {
    value >= 10000 -> String.format(Locale.getDefault(), "%.1f万", value / 10000f)
    else -> value.toString()
}

private fun formatDateV1(timestamp: Long): String =
    SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(timestamp))
