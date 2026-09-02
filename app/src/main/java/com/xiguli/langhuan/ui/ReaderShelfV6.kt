package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
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
 * Reader-first shelf. Content stays visually flat; glass/layering is reserved for the floating
 * navigation surface so book covers remain the strongest visual element on the page.
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
    var showViewMenu by remember { mutableStateOf(false) }

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
            ShelfTopBarV9(
                totalBooks = state.stories.size,
                query = query,
                searchVisible = searchVisible,
                layout = layout,
                showViewMenu = showViewMenu,
                onToggleSearch = { searchVisible = !searchVisible },
                onQueryChange = { query = it },
                onClearQuery = { query = "" },
                onImportLocal = onImportLocal,
                onShowViewMenu = { showViewMenu = true },
                onDismissViewMenu = { showViewMenu = false },
                onLayoutChange = { layout = it },
            )
        },
        bottomBar = {
            ShelfGlassDockV9(
                showTools = showTools,
                onShowTools = { showTools = true },
                onDismissTools = { showTools = false },
                onCreate = onCreate,
                onAiSetup = onAiSetup,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
            )
        },
    ) { padding ->
        when {
            importState.busy -> ImportingBookStateV9(importState.currentFileName, Modifier.padding(padding))
            books.isEmpty() -> EmptyShelfV9(
                query = query,
                modifier = Modifier.padding(padding),
                onImportLocal = onImportLocal,
                onCreate = onCreate,
            )
            layout == ShelfLayoutV6.GRID -> ShelfGridV9(
                books = books,
                query = query,
                modifier = Modifier.padding(padding),
                onOpenBook = onOpenBook,
            )
            else -> ShelfListV9(
                books = books,
                query = query,
                modifier = Modifier.padding(padding),
                onOpenBook = onOpenBook,
            )
        }
    }
}

@Composable
private fun ShelfTopBarV9(
    totalBooks: Int,
    query: String,
    searchVisible: Boolean,
    layout: ShelfLayoutV6,
    showViewMenu: Boolean,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onImportLocal: () -> Unit,
    onShowViewMenu: () -> Unit,
    onDismissViewMenu: () -> Unit,
    onLayoutChange: (ShelfLayoutV6) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 10.dp, top = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "琅嬛",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (totalBooks == 0) "阅读与创作" else "$totalBooks 本书",
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search, "搜索")
            }
            IconButton(onClick = onImportLocal) {
                Icon(Icons.Rounded.Add, "导入小说")
            }
            Box {
                IconButton(onClick = onShowViewMenu) {
                    Icon(Icons.Rounded.MoreHoriz, "显示选项")
                }
                DropdownMenu(expanded = showViewMenu, onDismissRequest = onDismissViewMenu) {
                    DropdownMenuItem(
                        text = { Text("网格书架") },
                        leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                        trailingIcon = if (layout == ShelfLayoutV6.GRID) {
                            { Icon(Icons.Rounded.Check, null) }
                        } else null,
                        onClick = { onDismissViewMenu(); onLayoutChange(ShelfLayoutV6.GRID) },
                    )
                    DropdownMenuItem(
                        text = { Text("列表书架") },
                        leadingIcon = { Icon(Icons.Rounded.ViewAgenda, null) },
                        trailingIcon = if (layout == ShelfLayoutV6.LIST) {
                            { Icon(Icons.Rounded.Check, null) }
                        } else null,
                        onClick = { onDismissViewMenu(); onLayoutChange(ShelfLayoutV6.LIST) },
                    )
                }
            }
        }

        if (searchVisible) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp, top = 10.dp, bottom = 4.dp),
                placeholder = { Text("搜索书名或类型") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) { Icon(Icons.Rounded.Cancel, "清空") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun ShelfGridV9(
    books: List<ReaderBookUi>,
    query: String,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    val recent = books.firstOrNull()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (query.isBlank() && recent != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecentReadingV9(recent, onOpenBook)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfSectionV9("书架", "${books.size} 本")
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ShelfSectionV9("搜索结果", "${books.size} 本")
            }
        }

        items(books, key = { it.id }) { book ->
            BookCoverItemV9(book, onOpenBook)
        }
    }
}

@Composable
private fun RecentReadingV9(book: ReaderBookUi, onOpenBook: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenBook(book.id) }
            .padding(top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverPreviewV3(
            path = book.coverPath,
            title = book.title,
            modifier = Modifier
                .width(82.dp)
                .height(116.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Column(
            Modifier
                .padding(start = 16.dp)
                .weight(1f),
        ) {
            Text(
                "最近阅读",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                book.title,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (book.genre == "导入作品") "第 ${book.currentChapter} 章" else "${book.genre} · 第 ${book.currentChapter} 章",
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Surface(
                modifier = Modifier.padding(top = 12.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MenuBook,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
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

@Composable
private fun ShelfSectionV9(title: String, meta: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookCoverItemV9(book: ReaderBookUi, onOpenBook: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
    ) {
        CoverPreviewV3(
            path = book.coverPath,
            title = book.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.69f)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            book.title,
            modifier = Modifier.padding(top = 7.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "第 ${book.currentChapter} 章",
            modifier = Modifier.padding(top = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShelfListV9(
    books: List<ReaderBookUi>,
    query: String,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp),
    ) {
        item {
            ShelfSectionV9(if (query.isBlank()) "书架" else "搜索结果", "${books.size} 本")
            Spacer(Modifier.height(6.dp))
        }
        listItems(books, key = { it.id }) { book ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBook(book.id) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPreviewV3(
                    book.coverPath,
                    book.title,
                    Modifier.width(54.dp).height(76.dp).clip(RoundedCornerShape(9.dp)),
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
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "读到第 ${book.currentChapter} 章",
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f),
                )
            }
        }
    }
}

@Composable
private fun ShelfGlassDockV9(
    showTools: Boolean,
    onShowTools: () -> Unit,
    onDismissTools: () -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
            border = BorderStroke(
                .5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .70f),
            ),
            shadowElevation = 5.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShelfDockItemV9(Icons.Rounded.AutoStories, "书架", selected = true, onClick = {})
                ShelfDockItemV9(Icons.Rounded.AutoAwesome, "创作", selected = false, onClick = onCreate)
                Box {
                    ShelfDockItemV9(Icons.Rounded.MoreHoriz, "工具", selected = false, onClick = onShowTools)
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
private fun ShelfDockItemV9(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f) else Color.Transparent,
        ) {
            Icon(
                icon,
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp).size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            modifier = Modifier.padding(top = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun EmptyShelfV9(
    query: String,
    modifier: Modifier,
    onImportLocal: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (query.isBlank()) Icons.Rounded.AutoStories else Icons.Rounded.SearchOff,
            null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .62f),
        )
        Text(
            if (query.isBlank()) "书架还是空的" else "没有找到这本书",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (query.isBlank()) "导入一本小说开始阅读，或者直接用 AI 写一本新的。" else "换个关键词试试。",
            modifier = Modifier.padding(top = 7.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (query.isBlank()) {
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onImportLocal, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("导入小说")
                }
                TextButton(onClick = onCreate) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text("AI 创作")
                }
            }
        }
    }
}

@Composable
private fun ImportingBookStateV9(fileName: String, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(30.dp))
        Text("正在导入", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
        Text(
            fileName.ifBlank { "正在读取本地小说…" },
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
