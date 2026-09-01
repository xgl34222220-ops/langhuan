package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class ShelfLayoutV6 { GRID, LIST }

/**
 * 真正的阅读器第一层：书架只负责找书、导入、继续阅读。
 * AI / 创作入口全部降级，不再抢占书架视觉焦点。
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
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    val books = remember(state.stories, query) {
        val key = query.trim()
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { book -> key.isBlank() || book.title.contains(key, true) || book.genre.contains(key, true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "书架",
                        modifier = Modifier.weight(1f),
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = {
                        layout = if (layout == ShelfLayoutV6.GRID) ShelfLayoutV6.LIST else ShelfLayoutV6.GRID
                    }) {
                        Icon(
                            if (layout == ShelfLayoutV6.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                            contentDescription = "切换布局",
                        )
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("AI 新建小说") },
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                                onClick = { showMore = false; onCreate() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("AI 设置") },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                                onClick = { showMore = false; onAiSetup() },
                            )
                            DropdownMenuItem(
                                text = { Text("写作 Skills") },
                                leadingIcon = { Icon(Icons.Rounded.AutoStories, null) },
                                onClick = { showMore = false; onSkills() },
                            )
                            DropdownMenuItem(
                                text = { Text("后台任务") },
                                leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                                onClick = { showMore = false; onRunCenter() },
                            )
                        }
                    }
                }

                if (searchVisible) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                        placeholder = { Text("搜索书名") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清空") }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }

                if (importState.busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImportLocal,
                icon = { Icon(Icons.Rounded.FolderOpen, null) },
                text = { Text("导入小说") },
                shape = RoundedCornerShape(16.dp),
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
            layout == ShelfLayoutV6.GRID -> ShelfGridV6(books, Modifier.padding(padding), onOpenBook)
            else -> ShelfListV6(books, Modifier.padding(padding), onOpenBook)
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("添加书籍", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ListItem(
                    headlineContent = { Text("导入本地小说") },
                    supportingContent = { Text("TXT · EPUB · Markdown") },
                    leadingContent = { Icon(Icons.Rounded.FolderOpen, null) },
                    modifier = Modifier.clickable { showAdd = false; onImportLocal() },
                )
                ListItem(
                    headlineContent = { Text("AI 新建小说") },
                    supportingContent = { Text("进入创作工作台") },
                    leadingContent = { Icon(Icons.Rounded.AutoAwesome, null) },
                    modifier = Modifier.clickable { showAdd = false; onCreate() },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ShelfGridV6(
    books: List<ReaderBookUi>,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items(books, key = { it.id }) { book ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
            ) {
                CoverPreviewV3(
                    path = book.coverPath,
                    title = book.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(.70f).clip(RoundedCornerShape(8.dp)),
                )
                Text(
                    book.title,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (book.genre == "导入作品") "本地 · 第 ${book.currentChapter} 章" else "第 ${book.currentChapter} 章",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShelfListV6(
    books: List<ReaderBookUi>,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 112.dp),
    ) {
        listItems(books, key = { it.id }) { book ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPreviewV3(
                    book.coverPath,
                    book.title,
                    Modifier.width(54.dp).height(77.dp).clip(RoundedCornerShape(6.dp)),
                )
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
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
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
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
        modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.AutoStories,
            null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (query.isBlank()) "还没有书" else "没有找到",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (query.isBlank()) "从手机里导入 TXT、EPUB 或 Markdown，导入后直接开始阅读。" else "换个关键词试试。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (query.isBlank()) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImportLocal,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("导入本地小说")
            }
            TextButton(onClick = onCreate, modifier = Modifier.padding(top = 4.dp)) { Text("AI 新建小说") }
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
        CircularProgressIndicator(strokeWidth = 2.5.dp)
        Spacer(Modifier.height(18.dp))
        Text("正在导入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            fileName.ifBlank { "正在读取本地小说…" },
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
