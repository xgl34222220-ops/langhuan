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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.material3.OutlinedTextField
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
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

private enum class MobileShelfTab { LIBRARY, ME }

/**
 * Langhuan shelf v3.
 *
 * This is intentionally composed like a reading app instead of a dashboard: one recent-reading
 * anchor, then a quiet cover wall. AI/system tools live behind the second tab.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfMobileExperience(
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
    var tab by rememberSaveable { mutableStateOf(MobileShelfTab.LIBRARY) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }

    val books = remember(state.stories, query) {
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }

    Box(Modifier.fillMaxSize().background(t.background)) {
        when (tab) {
            MobileShelfTab.LIBRARY -> MobileLibraryPageV3(
                books = books,
                libraryLoaded = state.libraryLoaded,
                openingBookId = openingBookId,
                searchOpen = searchOpen,
                query = query,
                onQuery = { query = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onAdd = { showAdd = true },
                onOpenBook = onOpenBook,
                onLongPress = { actionsFor = it },
            )

            MobileShelfTab.ME -> MobileMePageV3(
                books = state.stories,
                onCreate = onCreate,
                onImportLocal = onImportLocal,
                onOpenTavern = onOpenTavern,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
                onAiSetup = onAiSetup,
            )
        }

        MobileShelfNavigationV3(
            selected = tab,
            onSelected = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (importState.busy) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 70.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = t.card,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
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
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("添加小说", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("导入已有作品，或从一个想法开始。", Modifier.padding(top = 3.dp, bottom = 10.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                MobileSheetActionV3(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                    showAdd = false
                    onImportLocal()
                }
                HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 54.dp))
                MobileSheetActionV3(Icons.Rounded.AutoAwesome, "AI 创建小说", "通过对话逐步建立作品") {
                    showAdd = false
                    onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 3.dp, bottom = 10.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                MobileSheetActionV3(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置") {
                    actionsFor = null
                    onOpenBook(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 54.dp))
                MobileSheetActionV3(Icons.Rounded.TheaterComedy, "进入故事", "从这本书进入互动故事") {
                    actionsFor = null
                    onOpenTavern(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 54.dp))
                MobileSheetActionV3(Icons.Rounded.DeleteOutline, "删除小说", "同时删除章节与项目数据", destructive = true) {
                    actionsFor = null
                    pendingDelete = book
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
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteBook(book.id)
                }) { Text("删除", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun MobileLibraryPageV3(
    books: List<ReaderBookUi>,
    libraryLoaded: Boolean,
    openingBookId: String?,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenBook: (String) -> Unit,
    onLongPress: (ReaderBookUi) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("书架", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, color = t.foreground, fontWeight = FontWeight.Bold)
            ShelfTopActionV3(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", onToggleSearch)
            ShelfTopActionV3(Icons.Rounded.Add, "添加小说", onAdd)
        }

        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                placeholder = { Text("搜索书名或类型") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }

        when {
            !libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp, color = t.primary)
            }

            books.isEmpty() -> MobileShelfEmptyV3(onAdd)

            else -> {
                val recent = books.first()
                val remaining = books.drop(1)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 82.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueReadingHeroV3(
                            book = recent,
                            busy = openingBookId == recent.id,
                            onClick = { onOpenBook(recent.id) },
                            onLongClick = { onLongPress(recent) },
                        )
                    }
                    if (remaining.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 1.dp), verticalAlignment = Alignment.Bottom) {
                                Text("全部书籍", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
                                Text("${books.size} 本", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
                            }
                        }
                        items(remaining, key = { it.id }) { book ->
                            ShelfBookTileV3(
                                book = book,
                                busy = openingBookId == book.id,
                                enabled = openingBookId == null,
                                onClick = { onOpenBook(book.id) },
                                onLongClick = { onLongPress(book) },
                            )
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
                                Text("再添加一本", Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileShelfEmptyV3(onAdd: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Box(Modifier.fillMaxSize().padding(horizontal = 44.dp, vertical = 92.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoStories, null, Modifier.size(34.dp), tint = t.mutedForeground.copy(alpha = .58f))
            Text("还没有书", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            Text("导入一本小说，或者开始你的第一部作品。", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Text("添加小说", Modifier.padding(start = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueReadingHeroV3(
    book: ReaderBookUi,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp),
        color = t.warmSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShelfCoverV3(book = book, modifier = Modifier.width(72.dp).aspectRatio(.68f), busy = busy)
            Column(Modifier.padding(start = 15.dp).weight(1f)) {
                Text("继续阅读", style = MaterialTheme.typography.labelMedium, color = t.primary, fontWeight = FontWeight.SemiBold)
                Text(book.title, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(22.dp), tint = t.mutedForeground.copy(alpha = .66f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookTileV3(
    book: ReaderBookUi,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)) {
        ShelfCoverV3(book = book, modifier = Modifier.fillMaxWidth().aspectRatio(.68f), busy = busy)
        Text(book.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground, maxLines = 1)
    }
}

@Composable
private fun ShelfCoverV3(book: ReaderBookUi, modifier: Modifier, busy: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val palettes = listOf(
        listOf(Color(0xFFDDD4C5), Color(0xFF9F8F78)),
        listOf(Color(0xFFD8E2DC), Color(0xFF7A9486)),
        listOf(Color(0xFFD9D7E8), Color(0xFF8580A0)),
        listOf(Color(0xFFE4D5D1), Color(0xFFA47E77)),
    )
    val palette = palettes[(book.title.hashCode() and Int.MAX_VALUE) % palettes.size]

    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = t.card, shadowElevation = 2.dp) {
        Box(Modifier.fillMaxSize()) {
            if (cover != null) {
                Image(cover.asImageBitmap(), book.title, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier.fillMaxSize().background(Brush.linearGradient(palette)).padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(book.genre.ifBlank { "小说" }, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .80f))
                    Text(book.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .28f)))
                }
            }
            if (busy) {
                Box(Modifier.fillMaxSize().background(t.background.copy(alpha = .52f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp, color = t.primary)
                }
            }
        }
    }
}

@Composable
private fun ShelfTopActionV3(icon: ImageVector, description: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(21.dp), tint = t.foreground) }
    }
}

@Composable
private fun MobileMePageV3(
    books: List<ReaderBookUi>,
    onCreate: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenTavern: (String) -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
    onAiSetup: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val totalWords = remember(books) { books.sumOf { it.currentWords.coerceAtLeast(0) } }
    val recent = remember(books) { books.maxByOrNull { it.updatedAt } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 82.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.headlineLarge, color = t.foreground, fontWeight = FontWeight.Bold)
            Text("${books.size} 本作品 · $totalWords 字", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        item {
            ShelfSectionV3("创作") {
                MobileMeRowV3(Icons.Rounded.AutoAwesome, "AI 新建", "从一个想法开始建立小说", onCreate)
                HorizontalDivider(color = t.border.copy(alpha = .38f), modifier = Modifier.padding(start = 54.dp))
                MobileMeRowV3(Icons.Rounded.FolderOpen, "导入小说", "从手机添加本地小说", onImportLocal)
                if (recent != null) {
                    HorizontalDivider(color = t.border.copy(alpha = .38f), modifier = Modifier.padding(start = 54.dp))
                    MobileMeRowV3(Icons.Rounded.TheaterComedy, "进入故事", "继续最近作品的互动故事") { onOpenTavern(recent.id) }
                }
            }
        }
        item {
            ShelfSectionV3("工具") {
                MobileMeRowV3(Icons.Rounded.TaskAlt, "运行中心", "查看执行中的任务", onRunCenter)
                HorizontalDivider(color = t.border.copy(alpha = .38f), modifier = Modifier.padding(start = 54.dp))
                MobileMeRowV3(Icons.Rounded.AutoAwesome, "写作能力", "管理实际参与写作的 Skill", onSkills)
                HorizontalDivider(color = t.border.copy(alpha = .38f), modifier = Modifier.padding(start = 54.dp))
                MobileMeRowV3(Icons.Rounded.Settings, "AI 服务", "模型、中转站和任务路由", onAiSetup)
            }
        }
    }
}

@Composable
private fun ShelfSectionV3(title: String, content: @Composable () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column {
        Text(title, Modifier.padding(start = 3.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = t.card) { Column { content() } }
    }
}

@Composable
private fun MobileMeRowV3(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = t.primary)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 1.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .58f))
    }
}

@Composable
private fun MobileSheetActionV3(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (destructive) t.destructive else t.primary)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .58f))
    }
}

@Composable
private fun MobileShelfNavigationV3(
    selected: MobileShelfTab,
    onSelected: (MobileShelfTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier = modifier.fillMaxWidth(), color = t.card, shadowElevation = 0.dp) {
        Column {
            HorizontalDivider(color = t.border.copy(alpha = .42f))
            Row(Modifier.navigationBarsPadding().height(58.dp).padding(horizontal = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                MobileNavigationItemV3(Modifier.weight(1f), Icons.Rounded.AutoStories, "书架", selected == MobileShelfTab.LIBRARY) { onSelected(MobileShelfTab.LIBRARY) }
                MobileNavigationItemV3(Modifier.weight(1f), Icons.Rounded.Person, "我的", selected == MobileShelfTab.ME) { onSelected(MobileShelfTab.ME) }
            }
        }
    }
}

@Composable
private fun MobileNavigationItemV3(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (selected) t.primary else t.mutedForeground)
        Text(label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (selected) t.primary else t.mutedForeground, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
