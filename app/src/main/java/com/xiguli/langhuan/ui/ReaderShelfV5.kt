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

private enum class ShelfLayoutV5 { GRID, LIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderShelfV5(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var layout by rememberSaveable { mutableStateOf(ShelfLayoutV5.GRID) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    val books = remember(state.stories, query) {
        val key = query.trim()
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { book ->
                key.isBlank() || book.title.contains(key, true) || book.genre.contains(key, true) || book.premise.contains(key, true)
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("琅嬛", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (state.stories.isEmpty()) "把喜欢的故事放进来" else "${state.stories.size} 本书 · 安静阅读，随时入戏",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton({ searchMode = !searchMode }) { Icon(Icons.Rounded.Search, "搜索") }
                    IconButton({ layout = if (layout == ShelfLayoutV5.GRID) ShelfLayoutV5.LIST else ShelfLayoutV5.GRID }) {
                        Icon(if (layout == ShelfLayoutV5.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, "切换布局")
                    }
                    IconButton({ showAddSheet = true }) { Icon(Icons.Rounded.Add, "添加书籍") }
                    Box {
                        IconButton({ showMore = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
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

                if (searchMode) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                        placeholder = { Text("搜索书名、类型或简介") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "清空") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    shape = RoundedCornerShape(20.dp),
                ) { Icon(Icons.Rounded.Add, "导入书籍") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                importState.busy -> ImportingBookStateV5(importState.currentFileName)
                books.isEmpty() -> EmptyShelfV5(query, onImportLocal, onCreate)
                layout == ShelfLayoutV5.GRID -> ShelfGridV5(books, onOpenBook)
                else -> ShelfListV5(books, onOpenBook)
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("添加到书架", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("本地小说优先。导入后立刻可以阅读，原文件不会被修改。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                AddBookActionV5(
                    icon = Icons.Rounded.FolderOpen,
                    title = "导入本地书籍",
                    subtitle = "TXT · EPUB · Markdown，支持常见中文编码",
                    primary = true,
                ) {
                    showAddSheet = false
                    onImportLocal()
                }
                AddBookActionV5(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "AI 新建小说",
                    subtitle = "从一个想法开始创作自己的故事",
                    primary = false,
                ) {
                    showAddSheet = false
                    onCreate()
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ShelfGridV5(books: List<ReaderBookUi>, onOpenBook: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(138.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(books, key = { it.id }) { book ->
            Column(Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }) {
                Box {
                    CoverPreviewV3(
                        path = book.coverPath,
                        title = book.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(.70f).clip(RoundedCornerShape(15.dp)),
                    )
                    if (book.genre == "导入作品") {
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                            tonalElevation = 2.dp,
                        ) {
                            Text("本地", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    book.title,
                    modifier = Modifier.padding(top = 9.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "第 ${book.currentChapter} 章 · ${humanWordsV5(book.currentWords)}",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShelfListV5(books: List<ReaderBookUi>, onOpenBook: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listItems(books, key = { it.id }) { book ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverPreviewV3(book.coverPath, book.title, Modifier.width(62.dp).height(88.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (book.genre == "导入作品") "本地书籍 · 第 ${book.currentChapter} 章" else "${book.genre} · 第 ${book.currentChapter} 章",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        progress = { readingProgressV5(book) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(99.dp)),
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun EmptyShelfV5(query: String, onImportLocal: () -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.AutoStories, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text(if (query.isBlank()) "书架还空着" else "没有找到这本书", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (query.isBlank()) "从手机里选一本小说。先像普通阅读器一样读，想改变故事时再进入角色。" else "换一个关键词试试。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (query.isBlank()) {
            Spacer(Modifier.height(24.dp))
            Button(onImportLocal, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(vertical = 15.dp)) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("导入本地书籍")
            }
            TextButton(onCreate, modifier = Modifier.padding(top = 6.dp)) { Text("或者用 AI 新建一本") }
        }
    }
}

@Composable
private fun ImportingBookStateV5(fileName: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.height(18.dp))
        Text("正在放进书架", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(fileName.ifBlank { "正在读取本地小说…" }, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("解析章节后就能直接阅读，不会改动原文件。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddBookActionV5(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

private fun humanWordsV5(words: Int): String = when {
    words >= 10_000 -> "%.1f 万字".format(words / 10_000f)
    else -> "$words 字"
}

private fun readingProgressV5(book: ReaderBookUi): Float {
    val words = book.currentWords.coerceAtLeast(1)
    val target = book.targetWords.coerceAtLeast(words)
    return (words.toFloat() / target.toFloat()).coerceIn(.02f, 1f)
}
