package com.xiguli.langhuan.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.theme.LanghuanShape

private enum class HomeFilterV4(val label: String) { ALL("全部"), CREATED("创作"), LOCAL("本地") }
private enum class HomeLayoutV4 { GRID, LIST }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LanghuanHomeV4(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenTavern: (String) -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(HomeFilterV4.ALL) }
    var layout by rememberSaveable { mutableStateOf(HomeLayoutV4.GRID) }
    var actionBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var deleteBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var toolsOpen by remember { mutableStateOf(false) }
    var tavernPicker by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }

    val books = remember(state.stories, query, filter) {
        val key = query.trim()
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { book ->
                when (filter) {
                    HomeFilterV4.ALL -> true
                    HomeFilterV4.CREATED -> book.genre != "导入作品"
                    HomeFilterV4.LOCAL -> book.genre == "导入作品"
                }
            }
            .filter { book -> key.isBlank() || book.title.contains(key, true) || book.genre.contains(key, true) }
    }

    val bg = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .24f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint, bg, bg))),
    ) {
        if (layout == HomeLayoutV4.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 116.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeHeaderV4(
                        totalBooks = state.stories.size,
                        query = query,
                        searchVisible = searchVisible,
                        filter = filter,
                        pageMenu = pageMenu,
                        layout = layout,
                        onToggleSearch = { searchVisible = !searchVisible },
                        onQueryChange = { query = it },
                        onImport = onImportLocal,
                        onOpenTools = { toolsOpen = true },
                        onOpenMenu = { pageMenu = true },
                        onDismissMenu = { pageMenu = false },
                        onFilter = { filter = it },
                        onLayout = { layout = it },
                    )
                }
                if (books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HomeEmptyV4(query, onImportLocal, onCreate)
                    }
                } else {
                    items(books, key = { it.id }) { book ->
                        HomeBookGridItemV4(
                            book = book,
                            onOpen = { onOpenBook(book.id) },
                            onMore = { actionBook = book },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 116.dp),
            ) {
                item {
                    HomeHeaderV4(
                        totalBooks = state.stories.size,
                        query = query,
                        searchVisible = searchVisible,
                        filter = filter,
                        pageMenu = pageMenu,
                        layout = layout,
                        onToggleSearch = { searchVisible = !searchVisible },
                        onQueryChange = { query = it },
                        onImport = onImportLocal,
                        onOpenTools = { toolsOpen = true },
                        onOpenMenu = { pageMenu = true },
                        onDismissMenu = { pageMenu = false },
                        onFilter = { filter = it },
                        onLayout = { layout = it },
                    )
                }
                if (books.isEmpty()) item { HomeEmptyV4(query, onImportLocal, onCreate) }
                listItems(books, key = { it.id }) { book ->
                    HomeBookListItemV4(book, onOpen = { onOpenBook(book.id) }, onMore = { actionBook = book })
                }
            }
        }

        if (importState.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = .18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .clip(LanghuanShape.sheet)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("正在导入", fontWeight = FontWeight.SemiBold)
                            Text(importState.currentFileName.ifBlank { "正在读取小说…" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        HomeBottomNavV4(
            modifier = Modifier.align(Alignment.BottomCenter),
            onCreate = onCreate,
            onTavern = { tavernPicker = true },
        )
    }

    actionBook?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionBook = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverPreviewV3(book.coverPath, book.title, Modifier.width(54.dp).height(78.dp).clip(LanghuanShape.cover))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(
                            if (book.genre == "导入作品") "本地书籍" else book.genre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HomeSheetRowV4(Icons.Rounded.MenuBook, "打开 / 继续阅读") {
                    actionBook = null
                    onOpenBook(book.id)
                }
                HomeSheetRowV4(Icons.Rounded.AutoAwesome, "进入酒馆") {
                    actionBook = null
                    onOpenTavern(book.id)
                }
                HomeSheetRowV4(Icons.Rounded.DeleteOutline, "删除小说", MaterialTheme.colorScheme.error) {
                    actionBook = null
                    deleteBook = book
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (toolsOpen) {
        ModalBottomSheet(onDismissRequest = { toolsOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text("琅嬛工具", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("把设置留在设置里，不再挤占书架。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                HomeSheetRowV4(Icons.Rounded.Tune, "AI 服务与模型") { toolsOpen = false; onAiSetup() }
                HomeSheetRowV4(Icons.Rounded.AutoStories, "写作 Skills") { toolsOpen = false; onSkills() }
                HomeSheetRowV4(Icons.Rounded.TaskAlt, "运行中心") { toolsOpen = false; onRunCenter() }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    if (tavernPicker) {
        ModalBottomSheet(onDismissRequest = { tavernPicker = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                Text("进入酒馆", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("选择一个世界，直接开始互动。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (state.stories.isEmpty()) {
                    Text("还没有小说。先导入一本或创作一本。", modifier = Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.stories.sortedByDescending { it.updatedAt }.forEach { book ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(LanghuanShape.card)
                                .combinedClickable(
                                    onClick = { tavernPicker = false; onOpenTavern(book.id) },
                                    onLongClick = { actionBook = book },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverPreviewV3(book.coverPath, book.title, Modifier.width(48.dp).height(68.dp).clip(LanghuanShape.cover))
                            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                                Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (book.genre == "导入作品") "本地世界 · 第 ${book.currentChapter} 章" else "${book.genre} · 第 ${book.currentChapter} 章",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    deleteBook?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteBook = null },
            shape = LanghuanShape.sheet,
            title = { Text("删除《${book.title}》？") },
            text = { Text("会同时删除正文、章节版本、长期记忆和琅嬛保存的本地封面。这个操作不能撤销。") },
            confirmButton = {
                Button(
                    onClick = { deleteBook = null; onDeleteBook(book.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteBook = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeHeaderV4(
    totalBooks: Int,
    query: String,
    searchVisible: Boolean,
    filter: HomeFilterV4,
    pageMenu: Boolean,
    layout: HomeLayoutV4,
    onToggleSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onFilter: (HomeFilterV4) -> Unit,
    onLayout: (HomeLayoutV4) -> Unit,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 18.dp, bottom = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("琅嬛", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (totalBooks == 0) "藏书 · 创作 · 故事" else "$totalBooks 本藏书",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HomeRoundButtonV4(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", onToggleSearch)
            Spacer(Modifier.width(7.dp))
            HomeRoundButtonV4(Icons.Rounded.Add, "导入", onImport)
            Spacer(Modifier.width(7.dp))
            Box {
                HomeRoundButtonV4(Icons.Rounded.MoreHoriz, "更多", onOpenMenu)
                DropdownMenu(expanded = pageMenu, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(
                        text = { Text(if (layout == HomeLayoutV4.GRID) "切换为列表" else "切换为网格") },
                        leadingIcon = { Icon(if (layout == HomeLayoutV4.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, null) },
                        onClick = { onDismissMenu(); onLayout(if (layout == HomeLayoutV4.GRID) HomeLayoutV4.LIST else HomeLayoutV4.GRID) },
                    )
                    DropdownMenuItem(
                        text = { Text("设置与工具") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = { onDismissMenu(); onOpenTools() },
                    )
                }
            }
        }

        if (searchVisible) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(LanghuanShape.panel)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .72f))
                    .border(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), LanghuanShape.panel)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).padding(start = 9.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (query.isBlank()) Text("搜索书名或类型", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                }
            }
        }

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            HomeFilterV4.entries.forEach { item ->
                val selected = filter == item
                Column(
                    Modifier.combinedClickable(onClick = { onFilter(item) }, onLongClick = { onFilter(item) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.label,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .width(if (selected) 18.dp else 0.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRoundButtonV4(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .shadow(5.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBookGridItemV4(book: ReaderBookUi, onOpen: () -> Unit, onMore: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(.68f)
                .shadow(9.dp, LanghuanShape.cover, clip = false)
                .clip(LanghuanShape.cover)
                .combinedClickable(onClick = onOpen, onLongClick = onMore),
        ) {
            CoverPreviewV3(book.coverPath, book.title, Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .34f))
                    .combinedClickable(onClick = onMore, onLongClick = onMore),
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
            "第 ${book.currentChapter} 章",
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBookListItemV4(book: ReaderBookUi, onOpen: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onMore)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverPreviewV3(book.coverPath, book.title, Modifier.width(58.dp).height(84.dp).clip(LanghuanShape.cover))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (book.genre == "导入作品") "本地书籍" else book.genre,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("读到第 ${book.currentChapter} 章", modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HomeRoundButtonV4(Icons.Rounded.MoreHoriz, "书籍菜单", onMore)
    }
}

@Composable
private fun HomeEmptyV4(query: String, onImport: () -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 72.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (query.isBlank()) Icons.Rounded.AutoStories else Icons.Rounded.SearchOff,
            null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f),
        )
        Text(if (query.isBlank()) "这里还没有书" else "没有找到这本书", modifier = Modifier.padding(top = 15.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (query.isBlank()) {
            Text("导入一本开始读，或者直接和 AI 聊出一本新的。", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onImport) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("导入") }
                Button(onClick = onCreate, shape = LanghuanShape.card) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("开始创作") }
            }
        }
    }
}

@Composable
private fun HomeBottomNavV4(modifier: Modifier = Modifier, onCreate: () -> Unit, onTavern: () -> Unit) {
    val shape = LanghuanShape.sheet
    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 9.dp)
            .shadow(16.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = .95f),
                        MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                    )
                )
            )
            .border(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .62f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            HomeNavItemV4(Icons.Rounded.AutoStories, "书架", true, {})
            HomeNavItemV4(Icons.Rounded.EditNote, "创作", false, onCreate)
            HomeNavItemV4(Icons.Rounded.Forum, "酒馆", false, onTavern)
        }
    }
}

@Composable
private fun HomeNavItemV4(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(LanghuanShape.panel)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, Modifier.size(19.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) {
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun HomeSheetRowV4(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(LanghuanShape.card)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(19.dp), tint = tint) }
        Text(title, modifier = Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.Medium, color = tint)
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
    }
}
