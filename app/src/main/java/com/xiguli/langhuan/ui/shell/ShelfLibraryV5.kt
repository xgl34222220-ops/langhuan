package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfLibraryV5(
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
    val t = LocalLanghuanUiTokens.current
    val editViewModel: BookEditViewModelV5 = viewModel()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var editingBookId by rememberSaveable { mutableStateOf<String?>(null) }

    val editingBook = editingBookId?.let { id -> state.stories.firstOrNull { it.id == id } }
    if (editingBook != null) {
        BookEditPageV5(
            book = editingBook,
            editViewModel = editViewModel,
            onClose = {
                editViewModel.clearFeedback()
                editingBookId = null
            },
        )
        return
    }

    val books = remember(state.stories, query) {
        state.stories.sortedByDescending { it.updatedAt }
            .filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 13.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "书架",
                        style = MaterialTheme.typography.headlineLarge,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.stories.isEmpty()) "把故事放进这里" else "${state.stories.size} 本作品",
                        Modifier.padding(top = 1.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                ShelfV5Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索") {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                }
                ShelfV5Icon(Icons.Rounded.Add, "添加") { addOpen = true }
                ShelfV5Icon(Icons.Rounded.MoreHoriz, "更多") { toolsOpen = true }
            }

            if (searchOpen) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                    placeholder = { Text("搜索书名或类型") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = t.muted,
                        unfocusedContainerColor = t.muted,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            when {
                !state.libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.primary)
                }
                books.isEmpty() -> ShelfV5Empty { addOpen = true }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 34.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(25.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("最近更新", style = MaterialTheme.typography.labelLarge, color = t.mutedForeground, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text("按更新时间", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground.copy(alpha = .72f))
                        }
                    }
                    if (books.size == 1) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                ShelfBookV5(
                                    book = books.first(),
                                    busy = openingBookId == books.first().id,
                                    enabled = openingBookId == null,
                                    modifier = Modifier.width(174.dp),
                                    onOpen = { onOpenBook(books.first().id) },
                                    onMore = { actionsFor = books.first() },
                                )
                            }
                        }
                    } else {
                        items(books, key = { it.id }) { book ->
                            ShelfBookV5(
                                book = book,
                                busy = openingBookId == book.id,
                                enabled = openingBookId == null,
                                modifier = Modifier.fillMaxWidth(),
                                onOpen = { onOpenBook(book.id) },
                                onMore = { actionsFor = book },
                            )
                        }
                    }
                }
            }
        }
    }

    if (importState.busy) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
            shape = RoundedCornerShape(18.dp),
            color = t.card,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("正在导入小说", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                    Text(importState.currentFileName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    if (addOpen) {
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = t.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("添加作品", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("导入已有小说，或从一个想法开始创作。", Modifier.padding(top = 3.dp, bottom = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                ShelfV5Action(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                    addOpen = false; onImportLocal()
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 40.dp))
                ShelfV5Action(Icons.Rounded.AutoAwesome, "AI 创建小说", "通过对话建立新作品") {
                    addOpen = false; onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    if (toolsOpen) {
        ModalBottomSheet(onDismissRequest = { toolsOpen = false }, containerColor = t.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("琅嬛工具", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("创作与系统工具不占用书架主画面。", Modifier.padding(top = 3.dp, bottom = 9.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                ShelfV5Action(Icons.Rounded.TaskAlt, "运行中心", "查看执行中的任务") { toolsOpen = false; onRunCenter() }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 40.dp))
                ShelfV5Action(Icons.Rounded.AutoAwesome, "写作能力", "管理参与写作的 Skill") { toolsOpen = false; onSkills() }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 40.dp))
                ShelfV5Action(Icons.Rounded.Settings, "AI 服务", "模型、中转站与路由") { toolsOpen = false; onAiSetup() }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ShelfMiniCoverV5(book, Modifier.width(54.dp).aspectRatio(.68f))
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                }
                Spacer(Modifier.height(10.dp))
                ShelfV5Action(Icons.Rounded.Edit, "编辑书籍", "修改书名、类型、简介和封面") {
                    actionsFor = null
                    editingBookId = book.id
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 40.dp))
                ShelfV5Action(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置") {
                    actionsFor = null; onOpenBook(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 40.dp))
                ShelfV5Action(Icons.Rounded.TheaterComedy, "进入故事", "进入这本书的互动世界") {
                    actionsFor = null; onOpenTavern(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 40.dp))
                ShelfV5Action(Icons.Rounded.DeleteOutline, "删除小说", "删除章节与项目数据", destructive = true) {
                    actionsFor = null; pendingDelete = book
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = t.card,
            shape = RoundedCornerShape(24.dp),
            title = { Text("删除《${book.title}》？", color = t.foreground) },
            text = { Text("章节、版本和项目数据会一起删除。", color = t.mutedForeground) },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDeleteBook(book.id) }) { Text("删除", color = t.destructive) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookV5(
    book: ReaderBookUi,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(modifier.combinedClickable(enabled = enabled, onClick = onOpen, onLongClick = onMore)) {
        Box {
            ShelfMiniCoverV5(book, Modifier.fillMaxWidth().aspectRatio(.68f), busy)
            Surface(
                onClick = onMore,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(32.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = .38f),
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreHoriz, "书籍操作", Modifier.size(18.dp)) }
            }
        }
        Text(
            book.title,
            Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.genre.ifBlank { "小说" },
            Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "第 ${book.currentChapter.coerceAtLeast(1)} 章 · ${formatShelfWordsV5(book.currentWords)}",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground.copy(alpha = .78f),
            maxLines = 1,
        )
    }
}

@Composable
private fun ShelfMiniCoverV5(book: ReaderBookUi, modifier: Modifier, busy: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val palettes = listOf(
        listOf(Color(0xFF293543), Color(0xFF718699)),
        listOf(Color(0xFF53473D), Color(0xFFA1856B)),
        listOf(Color(0xFF304842), Color(0xFF789186)),
        listOf(Color(0xFF4B4257), Color(0xFF8D809E)),
    )
    val palette = palettes[(book.title.hashCode() and Int.MAX_VALUE) % palettes.size]
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = t.card, shadowElevation = 3.dp) {
        Box(Modifier.fillMaxSize()) {
            if (cover != null) {
                Image(cover.asImageBitmap(), book.title, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(palette)).padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(book.genre.ifBlank { "小说" }, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .74f))
                    Text(book.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .48f))
                }
            }
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ShelfV5Icon(icon: ImageVector, description: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(21.dp), tint = t.foreground) }
    }
}

@Composable
private fun ShelfV5Action(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(21.dp), tint = if (destructive) t.destructive else t.primary)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShelfV5Empty(onAdd: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Box(Modifier.fillMaxSize().padding(horizontal = 46.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoStories, null, Modifier.size(38.dp), tint = t.mutedForeground.copy(alpha = .55f))
            Text("书架还是空的", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            Text("导入一本小说，或者创建你的第一部作品。", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("添加作品", Modifier.padding(start = 5.dp))
            }
        }
    }
}

private fun formatShelfWordsV5(words: Int): String = when {
    words >= 10000 -> "%.1f 万字".format(words / 10000f)
    words > 0 -> "$words 字"
    else -> "未统计字数"
}
