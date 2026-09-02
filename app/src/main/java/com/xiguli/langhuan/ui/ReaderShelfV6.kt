package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class ShelfLayoutV6 { GRID, LIST }

/**
 * Stable reader-first shelf with a MIUIx-inspired hierarchy built only from Material3 primitives.
 * It intentionally avoids Miuix runtime classes until device compatibility is proven again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderShelfV6(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var layout by rememberSaveable { mutableStateOf(ShelfLayoutV6.GRID) }
    var showTools by remember { mutableStateOf(false) }

    val books = remember(state.stories, query) {
        val key = query.trim()
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { book ->
                key.isBlank() || book.title.contains(key, true) || book.genre.contains(key, true)
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ShelfTopBarV8(
                totalBooks = state.stories.size,
                query = query,
                searchVisible = searchVisible,
                layout = layout,
                onToggleSearch = { searchVisible = !searchVisible },
                onQueryChange = { query = it },
                onClearQuery = { query = "" },
                onToggleLayout = {
                    layout = if (layout == ShelfLayoutV6.GRID) ShelfLayoutV6.LIST else ShelfLayoutV6.GRID
                },
            )
        },
        bottomBar = {
            ShelfFloatingDockV8(
                showTools = showTools,
                onShowTools = { showTools = true },
                onDismissTools = { showTools = false },
                onCreate = onCreate,
                onImportLocal = onImportLocal,
                onAiSetup = onAiSetup,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
            )
        },
    ) { padding ->
        when {
            importState.busy -> ImportingBookStateV6(importState.currentFileName, Modifier.padding(padding))
            books.isEmpty() -> EmptyShelfV6(
                query = query,
                modifier = Modifier.padding(padding),
                onImportLocal = onImportLocal,
                onCreate = onCreate,
            )
            layout == ShelfLayoutV6.GRID -> ShelfHomeGridV8(
                books = books,
                query = query,
                modifier = Modifier.padding(padding),
                onOpenBook = onOpenBook,
            )
            else -> ShelfListV8(
                books = books,
                query = query,
                modifier = Modifier.padding(padding),
                onOpenBook = onOpenBook,
            )
        }
    }
}

@Composable
private fun ShelfTopBarV8(
    totalBooks: Int,
    query: String,
    searchVisible: Boolean,
    layout: ShelfLayoutV6,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleLayout: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "琅嬛",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (totalBooks == 0) "阅读与创作" else "书架 · $totalBooks 本",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = onToggleSearch,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search, "搜索")
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = onToggleLayout,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        if (layout == ShelfLayoutV6.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                        "切换布局",
                    )
                }
            }

            if (searchVisible) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    placeholder = { Text("搜索书名或类型") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = onClearQuery) { Icon(Icons.Rounded.Close, "清空") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ShelfHomeGridV8(
    books: List<ReaderBookUi>,
    query: String,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    val recent = books.firstOrNull()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (query.isBlank() && recent != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecentReadingCardV8(recent, onOpenBook)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfSectionHeaderV8("全部书籍", "${books.size} 本")
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfSectionHeaderV8("搜索结果", "${books.size} 本")
            }
        }

        items(books, key = { it.id }) { book ->
            BookGridCardV8(book, onOpenBook)
        }
    }
}

@Composable
private fun RecentReadingCardV8(book: ReaderBookUi, onOpenBook: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .clickable { onOpenBook(book.id) },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverPreviewV3(
                path = book.coverPath,
                title = book.title,
                modifier = Modifier
                    .width(76.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    "最近阅读",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    book.title,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (book.genre == "导入作品") "本地书籍 · 第 ${book.currentChapter} 章" else "${book.genre} · 第 ${book.currentChapter} 章",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.MenuBook,
                            null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "继续阅读",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfSectionHeaderV8(title: String, meta: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookGridCardV8(book: ReaderBookUi, onOpenBook: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(.70f),
            shape = RoundedCornerShape(15.dp),
            shadowElevation = 3.dp,
            tonalElevation = 1.dp,
        ) {
            CoverPreviewV3(
                path = book.coverPath,
                title = book.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            book.title,
            modifier = Modifier.padding(top = 8.dp, start = 1.dp, end = 1.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "第 ${book.currentChapter} 章",
            modifier = Modifier.padding(top = 2.dp, start = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShelfListV8(
    books: List<ReaderBookUi>,
    query: String,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ShelfSectionHeaderV8(if (query.isBlank()) "全部书籍" else "搜索结果", "${books.size} 本")
        }
        listItems(books, key = { it.id }) { book ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverPreviewV3(
                        book.coverPath,
                        book.title,
                        Modifier.width(58.dp).height(82.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (book.genre == "导入作品") "本地书籍" else book.genre,
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "上次读到第 ${book.currentChapter} 章",
                            modifier = Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            null,
                            modifier = Modifier.padding(9.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfFloatingDockV8(
    showTools: Boolean,
    onShowTools: () -> Unit,
    onDismissTools: () -> Unit,
    onCreate: () -> Unit,
    onImportLocal: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .97f),
            tonalElevation = 7.dp,
            shadowElevation = 10.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShelfDockItemV8(Icons.Rounded.AutoStories, "书架", selected = true, onClick = {})
                ShelfDockItemV8(Icons.Rounded.AutoAwesome, "创作", selected = false, onClick = onCreate)
                ShelfDockItemV8(Icons.Rounded.Add, "导入", selected = false, onClick = onImportLocal)
                Box {
                    ShelfDockItemV8(Icons.Rounded.Apps, "工具", selected = false, onClick = onShowTools)
                    DropdownMenu(expanded = showTools, onDismissRequest = onDismissTools) {
                        DropdownMenuItem(
                            text = { Text("AI 设置") },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                            onClick = { onDismissTools(); onAiSetup() },
                        )
                        DropdownMenuItem(
                            text = { Text("写作 Skills") },
                            leadingIcon = { Icon(Icons.Rounded.AutoStories, null) },
                            onClick = { onDismissTools(); onSkills() },
                        )
                        DropdownMenuItem(
                            text = { Text("后台任务") },
                            leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                            onClick = { onDismissTools(); onRunCenter() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfDockItemV8(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(min = 64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(21.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyShelfV6(
    query: String,
    modifier: Modifier,
    onImportLocal: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.AutoStories,
                    null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            if (query.isBlank()) "把故事装进琅嬛" else "没有找到这本书",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            if (query.isBlank()) "导入现有小说继续阅读，或者直接让 AI 帮你从一个想法开始写。" else "换个书名或类型关键词试试。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (query.isBlank()) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onImportLocal,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("导入本地小说")
            }
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text("AI 新建小说")
            }
        }
    }
}

@Composable
private fun ImportingBookStateV6(fileName: String, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(82.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 3.dp) }
        }
        Spacer(Modifier.height(18.dp))
        Text("正在导入", style = MaterialTheme.typography.titleLarge)
        Text(
            fileName.ifBlank { "正在读取本地小说…" },
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
