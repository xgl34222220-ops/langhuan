package com.xiguli.langhuan.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private enum class ShelfLayoutV6 { GRID, LIST }
private enum class ShelfFilterV10(val label: String) { ALL("全部"), WRITING("创作"), LOCAL("本地") }

/**
 * Startup-safe production shelf.
 *
 * This page intentionally avoids the default Material card/chip-heavy look. Covers and typography
 * carry the hierarchy; Material is only used for stable primitives and menus after startup.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderShelfV6(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var layout by rememberSaveable { mutableStateOf(ShelfLayoutV6.GRID) }
    var filter by rememberSaveable { mutableStateOf(ShelfFilterV10.ALL) }
    var showPageMenu by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var deleteBook by remember { mutableStateOf<ReaderBookUi?>(null) }

    val books = remember(state.stories, query, filter) {
        val key = query.trim()
        state.stories
            .asSequence()
            .sortedByDescending { it.updatedAt }
            .filter { book ->
                when (filter) {
                    ShelfFilterV10.ALL -> true
                    ShelfFilterV10.WRITING -> book.genre != "导入作品"
                    ShelfFilterV10.LOCAL -> book.genre == "导入作品"
                }
            }
            .filter { book -> key.isBlank() || book.title.contains(key, true) || book.genre.contains(key, true) }
            .toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            ShelfHeaderV10(
                totalBooks = state.stories.size,
                query = query,
                searchVisible = searchVisible,
                filter = filter,
                layout = layout,
                showPageMenu = showPageMenu,
                onQueryChange = { query = it },
                onToggleSearch = {
                    searchVisible = !searchVisible
                    if (!searchVisible) query = ""
                },
                onImportLocal = onImportLocal,
                onShowPageMenu = { showPageMenu = true },
                onDismissPageMenu = { showPageMenu = false },
                onFilterChange = { filter = it },
                onLayoutChange = { layout = it },
            )

            when {
                importState.busy -> ImportingBookStateV10(importState.currentFileName, Modifier.weight(1f))
                books.isEmpty() -> EmptyShelfV10(
                    hasAnyBook = state.stories.isNotEmpty(),
                    query = query,
                    modifier = Modifier.weight(1f),
                    onImportLocal = onImportLocal,
                    onCreate = onCreate,
                )
                layout == ShelfLayoutV6.GRID -> ShelfGridV10(
                    books = books,
                    modifier = Modifier.weight(1f),
                    onOpenBook = onOpenBook,
                    onBookMenu = { actionBook = it },
                )
                else -> ShelfListV10(
                    books = books,
                    modifier = Modifier.weight(1f),
                    onOpenBook = onOpenBook,
                    onBookMenu = { actionBook = it },
                )
            }
        }

        ShelfDockV10(
            modifier = Modifier.align(Alignment.BottomCenter),
            onCreate = onCreate,
            onShowTools = { showTools = true },
        )
    }

    if (showTools) {
        ModalBottomSheet(onDismissRequest = { showTools = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text("更多", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "AI、写作技能和后台任务",
                    modifier = Modifier.padding(top = 3.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShelfToolRowV10(Icons.Rounded.Tune, "AI 服务", "模型、中转站与默认服务") {
                    showTools = false
                    onAiSetup()
                }
                ShelfToolRowV10(Icons.Rounded.AutoStories, "写作 Skills", "章纲、导演、校验等能力") {
                    showTools = false
                    onSkills()
                }
                ShelfToolRowV10(Icons.Rounded.TaskAlt, "运行中心", "查看生成和后台任务") {
                    showTools = false
                    onRunCenter()
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    actionBook?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionBook = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverPreviewV3(
                        path = book.coverPath,
                        title = book.title,
                        modifier = Modifier.width(52.dp).height(74.dp).clip(RoundedCornerShape(9.dp)),
                    )
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (book.genre == "导入作品") "本地书籍 · 第 ${book.currentChapter} 章" else "${book.genre} · 第 ${book.currentChapter} 章",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ShelfActionRowV10(Icons.Rounded.MenuBook, "打开 / 继续阅读") {
                    actionBook = null
                    onOpenBook(book.id)
                }
                ShelfActionRowV10(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "删除小说",
                    tint = MaterialTheme.colorScheme.error,
                ) {
                    actionBook = null
                    deleteBook = book
                }
            }
        }
    }

    deleteBook?.let { book ->
        DeleteBookDialogV10(
            book = book,
            onDismiss = { deleteBook = null },
            onConfirm = {
                deleteBook = null
                onDeleteBook(book.id)
            },
        )
    }
}

@Composable
private fun ShelfHeaderV10(
    totalBooks: Int,
    query: String,
    searchVisible: Boolean,
    filter: ShelfFilterV10,
    layout: ShelfLayoutV6,
    showPageMenu: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onImportLocal: () -> Unit,
    onShowPageMenu: () -> Unit,
    onDismissPageMenu: () -> Unit,
    onFilterChange: (ShelfFilterV10) -> Unit,
    onLayoutChange: (ShelfLayoutV6) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 14.dp, top = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "琅嬛",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "书架",
                    modifier = Modifier.padding(top = 1.dp),
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    if (totalBooks == 0) "把阅读和创作放在同一个地方" else "$totalBooks 本书",
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ShelfRoundActionV10(
                icon = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                description = "搜索",
                onClick = onToggleSearch,
            )
            Spacer(Modifier.width(6.dp))
            ShelfRoundActionV10(Icons.Rounded.Add, "导入小说", onImportLocal)
            Spacer(Modifier.width(6.dp))
            Box {
                ShelfRoundActionV10(Icons.Rounded.MoreHoriz, "显示选项", onShowPageMenu)
                DropdownMenu(expanded = showPageMenu, onDismissRequest = onDismissPageMenu) {
                    DropdownMenuItem(
                        text = { Text("网格书架") },
                        leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                        trailingIcon = if (layout == ShelfLayoutV6.GRID) {{ Icon(Icons.Rounded.Check, null) }} else null,
                        onClick = { onDismissPageMenu(); onLayoutChange(ShelfLayoutV6.GRID) },
                    )
                    DropdownMenuItem(
                        text = { Text("列表书架") },
                        leadingIcon = { Icon(Icons.Rounded.ViewAgenda, null) },
                        trailingIcon = if (layout == ShelfLayoutV6.LIST) {{ Icon(Icons.Rounded.Check, null) }} else null,
                        onClick = { onDismissPageMenu(); onLayoutChange(ShelfLayoutV6.LIST) },
                    )
                }
            }
        }

        if (searchVisible) {
            ShelfSearchFieldV10(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.padding(top = 14.dp, end = 6.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = if (searchVisible) 12.dp else 18.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfFilterV10.entries.forEach { item ->
                ShelfFilterTabV10(
                    label = item.label,
                    selected = filter == item,
                    onClick = { onFilterChange(item) },
                )
            }
        }
    }
}

@Composable
private fun ShelfRoundActionV10(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f))
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ShelfSearchFieldV10(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .46f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text("搜索书名或类型", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
        if (value.isNotBlank()) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).combinedClickable(
                    onClick = { onValueChange("") },
                    onLongClick = { onValueChange("") },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Cancel, "清空", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ShelfFilterTabV10(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .padding(top = 5.dp)
                .width(if (selected) 18.dp else 0.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGridV10(
    books: List<ReaderBookUi>,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
    onBookMenu: (ReaderBookUi) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(books, key = { it.id }) { book ->
            ShelfBookV10(book, onOpenBook, onBookMenu)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookV10(
    book: ReaderBookUi,
    onOpenBook: (String) -> Unit,
    onBookMenu: (ReaderBookUi) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(.69f)
                .shadow(5.dp, RoundedCornerShape(13.dp), clip = false)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClick = { onOpenBook(book.id) },
                    onLongClick = { onBookMenu(book) },
                ),
        ) {
            CoverPreviewV3(
                path = book.coverPath,
                title = book.title,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(29.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .38f))
                    .combinedClickable(
                        onClick = { onBookMenu(book) },
                        onLongClick = { onBookMenu(book) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreHoriz, "书籍菜单", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            book.title,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (book.genre == "导入作品") "本地 · 第 ${book.currentChapter} 章" else "第 ${book.currentChapter} 章 · ${book.genre}",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfListV10(
    books: List<ReaderBookUi>,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
    onBookMenu: (ReaderBookUi) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 14.dp, top = 6.dp, bottom = 112.dp),
    ) {
        listItems(books, key = { it.id }) { book ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { onOpenBook(book.id) },
                        onLongClick = { onBookMenu(book) },
                    )
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPreviewV3(
                    book.coverPath,
                    book.title,
                    Modifier.width(54.dp).height(78.dp).clip(RoundedCornerShape(9.dp)),
                )
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (book.genre == "导入作品") "本地书籍" else book.genre,
                        modifier = Modifier.padding(top = 5.dp),
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
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { onBookMenu(book) },
                            onLongClick = { onBookMenu(book) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreHoriz, "书籍菜单", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ShelfDockV10(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    onShowTools: () -> Unit,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp)
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(32.dp), clip = false)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f))
            .border(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .58f), RoundedCornerShape(32.dp))
            .height(62.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfDockItemV10(Icons.Rounded.AutoStories, "书架", selected = true, onClick = {})
        ShelfDockItemV10(Icons.Rounded.AutoAwesome, "创作", selected = false, onClick = onCreate)
        ShelfDockItemV10(Icons.Rounded.MoreHoriz, "更多", selected = false, onClick = onShowTools)
    }
}

@Composable
private fun ShelfDockItemV10(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .78f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = if (selected) 15.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            label,
            modifier = Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected) {
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ShelfToolRowV10(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .60f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
    }
}

@Composable
private fun ShelfActionRowV10(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Text(title, modifier = Modifier.padding(start = 13.dp), color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeleteBookDialogV10(
    book: ReaderBookUi,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(22.dp),
        ) {
            Text("删除《${book.title}》？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "正文、章节版本、记忆数据和本地封面都会一起删除。此操作不可撤销。",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 22.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun EmptyShelfV10(
    hasAnyBook: Boolean,
    query: String,
    modifier: Modifier,
    onImportLocal: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .48f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (hasAnyBook || query.isNotBlank()) Icons.Rounded.SearchOff else Icons.Rounded.AutoStories,
                null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (hasAnyBook || query.isNotBlank()) "这里没有匹配的书" else "书架还是空的",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (hasAnyBook || query.isNotBlank()) "换个筛选或关键词试试。" else "导入一本小说阅读，或者直接从一个想法开始创作。",
            modifier = Modifier.padding(top = 7.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasAnyBook && query.isBlank()) {
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCreate, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("开始创作")
                }
                OutlinedButton(onClick = onImportLocal, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入")
                }
            }
        }
    }
}

@Composable
private fun ImportingBookStateV10(fileName: String, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Text("正在导入小说", modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
        Text(
            fileName.ifBlank { "正在读取本地文件…" },
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
