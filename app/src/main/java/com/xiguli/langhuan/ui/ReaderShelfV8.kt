package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import java.util.Locale

enum class ReaderShelfLayoutV8(val key: String) { GRID("grid"), LIST("list") }
enum class ReaderShelfSortV8(val key: String, val label: String) {
    RECENT("recent", "最近阅读"), UPDATED("updated", "最近更新"),
    TITLE("title", "书名"), IMPORTED("imported", "最近导入"),
}

private enum class ReaderShelfPageV8 { BOOKS, SHELVES, PROFILE }

/**
 * 阅读器优先书架。布局与交互以 2026-09-02 用户提供的第二段录屏为基准：
 * 三列封面、可点击的书架标题、长按管理面板、书架列表与低噪声个人页。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderShelfV8(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onOpenBookInfo: (String) -> Unit,
    onOpenTavern: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalMiuixTokens.current
    val progress = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val prefs = remember { context.getSharedPreferences("reader_shelf_v3", Context.MODE_PRIVATE) }

    var page by rememberSaveable { mutableStateOf(ReaderShelfPageV8.BOOKS) }
    var selectedShelf by rememberSaveable { mutableStateOf(prefs.getString("active_shelf", "正在阅读") ?: "正在阅读") }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var movingBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var shelfTick by remember { mutableIntStateOf(0) }
    var newShelfName by remember { mutableStateOf("") }

    val shelves = remember(shelfTick) {
        prefs.getString("shelf_names", null)?.split('\u001F')
            ?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
            ?.takeIf { it.isNotEmpty() } ?: listOf("正在阅读")
    }
    val sortedBooks = remember(state.stories, query, shelfTick) {
        sortShelfBooksV8(
            books = state.stories, query = query, sort = ReaderShelfSortV8.RECENT,
            lastRead = { id -> progress.getLong("last_$id", 0L) },
            importedAt = { id -> meta.getLong("imported_$id", 0L) },
        )
    }
    val visibleBooks = remember(sortedBooks, selectedShelf, shelfTick) {
        sortedBooks.filter { prefs.getString("book_shelf_${it.id}", "正在阅读") == selectedShelf }
    }
    val latest = sortedBooks.firstOrNull()
    LaunchedEffect(selectedShelf) { prefs.edit().putString("active_shelf", selectedShelf).apply() }

    Surface(Modifier.fillMaxSize(), color = tokens.pageBackground) {
        AnimatedContent(page, label = "reader-shelf-page") { current ->
            when (current) {
                ReaderShelfPageV8.BOOKS -> ReplicaBookGridV8(
                    shelfName = selectedShelf, books = visibleBooks,
                    busy = importState.busy, fileName = importState.currentFileName,
                    onShelves = { page = ReaderShelfPageV8.SHELVES },
                    onAdd = { showAdd = true }, onProfile = { page = ReaderShelfPageV8.PROFILE },
                    onOpen = onOpenBook, onLong = { selectedBook = it },
                )
                ReaderShelfPageV8.SHELVES -> ReplicaShelfListV8(
                    shelves = shelves, books = sortedBooks, query = query, showSearch = showSearch,
                    assignment = { book -> prefs.getString("book_shelf_${book.id}", "正在阅读") ?: "正在阅读" },
                    onBack = { page = ReaderShelfPageV8.BOOKS },
                    onSearch = { showSearch = !showSearch; if (!showSearch) query = "" },
                    onQuery = { query = it }, onAdd = { showCreateShelf = true },
                    onOpenShelf = { selectedShelf = it; page = ReaderShelfPageV8.BOOKS },
                    onOpenBook = onOpenBook,
                )
                ReaderShelfPageV8.PROFILE -> ReplicaProfileV8(
                    bookCount = state.stories.size, latest = latest,
                    onBack = { page = ReaderShelfPageV8.BOOKS },
                    onShelves = { page = ReaderShelfPageV8.SHELVES }, onImport = onImportLocal,
                    onCreate = onCreate, onTavern = { latest?.let { onOpenTavern(it.id) } },
                    onSkills = onSkills, onTasks = onRunCenter, onSettings = onAiSetup,
                )
            }
        }
    }

    if (showAdd) ModalBottomSheet(
        onDismissRequest = { showAdd = false }, containerColor = tokens.pageBackground,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
            Text("添加图书", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
            ReplicaMenuRowV8(Icons.Rounded.FolderOpen, "导入本地小说") { showAdd = false; onImportLocal() }
            ReplicaMenuRowV8(Icons.Rounded.AutoAwesome, "AI 新建小说") { showAdd = false; onCreate() }
            Text("支持 TXT、EPUB、Markdown；EPUB 会保留原书封面和目录。", Modifier.padding(vertical = 8.dp), color = tokens.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }

    selectedBook?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { selectedBook = null }, containerColor = tokens.pageBackground,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp)) {
                Text(book.title, Modifier.fillMaxWidth().padding(bottom = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ReplicaMenuRowV8(Icons.Rounded.FactCheck, "查看详情") { selectedBook = null; onOpenBookInfo(book.id) }
                ReplicaMenuRowV8(Icons.Rounded.DriveFileMove, "移动书架") { selectedBook = null; movingBook = book }
                ReplicaMenuRowV8(Icons.Rounded.DeleteOutline, "删除图书") { selectedBook = null; pendingDelete = book }
                Button(
                    onClick = { selectedBook = null }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = tokens.textPrimary),
                ) { Text("取消", fontSize = 17.sp) }
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }

    movingBook?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { movingBook = null }, containerColor = tokens.pageBackground,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text("移动《${book.title}》", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                shelves.forEach { shelf ->
                    val checked = prefs.getString("book_shelf_${book.id}", "正在阅读") == shelf
                    ReplicaMenuRowV8(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, shelf) {
                        prefs.edit().putString("book_shelf_${book.id}", shelf).apply()
                        movingBook = null; shelfTick++
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null }, title = { Text("删除《${book.title}》？") },
            text = { Text("只删除琅嬛中的书架记录与阅读数据，不会修改手机上的原文件。") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDeleteBook(book.id) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    if (showCreateShelf) AlertDialog(
        onDismissRequest = { showCreateShelf = false }, title = { Text("新建书架") },
        text = { OutlinedTextField(newShelfName, { newShelfName = it }, label = { Text("书架名称") }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = {
                val name = newShelfName.trim()
                if (name.isNotEmpty() && name !in shelves) {
                    prefs.edit().putString("shelf_names", (shelves + name).joinToString("\u001F")).apply()
                    selectedShelf = name; shelfTick++
                }
                newShelfName = ""; showCreateShelf = false
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = { showCreateShelf = false }) { Text("取消") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReplicaBookGridV8(
    shelfName: String, books: List<ReaderBookUi>, busy: Boolean, fileName: String,
    onShelves: () -> Unit, onAdd: () -> Unit, onProfile: () -> Unit,
    onOpen: (String) -> Unit, onLong: (ReaderBookUi) -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(76.dp).padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shelfName, Modifier.weight(1f).clickable(onClick = onShelves), fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            IconButton(onClick = onAdd, Modifier.size(48.dp)) { Icon(Icons.Rounded.Add, "添加图书", Modifier.size(29.dp), tint = tokens.textPrimary) }
            IconButton(onClick = onProfile, Modifier.size(48.dp)) { Icon(Icons.Rounded.PersonOutline, "个人中心", Modifier.size(26.dp), tint = tokens.textPrimary) }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            Text("正在导入 $fileName", Modifier.padding(horizontal = 24.dp, vertical = 6.dp), color = tokens.textSecondary, fontSize = 12.sp)
        }
        if (books.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.MenuBook, null, Modifier.size(50.dp), tint = tokens.textSecondary)
                Text("这个书架还是空的", Modifier.padding(top = 18.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                Text("导入一本小说，或让 AI 和你一起写一本。", Modifier.padding(top = 8.dp), color = tokens.textSecondary)
                Button(onClick = onAdd, Modifier.padding(top = 22.dp), shape = RoundedCornerShape(24.dp)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("添加图书") }
            }
        } else LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            gridItems(books, key = { it.id }) { book ->
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(book.id) }, onLongClick = { onLong(book) })) {
                    CoverPreviewV3(book.coverPath, book.title, Modifier.fillMaxWidth().aspectRatio(.69f).clip(RoundedCornerShape(3.dp)))
                    Text(book.title, Modifier.padding(top = 8.dp), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ReplicaShelfListV8(
    shelves: List<String>, books: List<ReaderBookUi>, query: String, showSearch: Boolean,
    assignment: (ReaderBookUi) -> String, onBack: () -> Unit, onSearch: () -> Unit,
    onQuery: (String) -> Unit, onAdd: () -> Unit, onOpenShelf: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
            Text("书架列表", Modifier.weight(1f).padding(start = 2.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "搜索", Modifier.size(29.dp)) }
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "新建书架", Modifier.size(31.dp)) }
        }
        if (showSearch) TextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            placeholder = { Text("搜索书名、分类") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            shape = RoundedCornerShape(4.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(shelves, key = { it }) { shelf ->
                val shelfBooks = books.filter { assignment(it) == shelf }
                Column(Modifier.fillMaxWidth().clickable { onOpenShelf(shelf) }.padding(top = 8.dp, bottom = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(25.dp), tint = tokens.textPrimary)
                        Text(shelf, Modifier.padding(start = 18.dp).weight(1f), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                        Text("(${shelfBooks.size})", color = tokens.textSecondary, fontSize = 16.sp)
                        IconButton(onClick = { }) { Icon(Icons.Rounded.MoreHoriz, "书架菜单") }
                    }
                    HorizontalDivider(Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
                    if (shelfBooks.isNotEmpty()) LazyRow(
                        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        items(shelfBooks.take(12), key = { it.id }) { book ->
                            CoverPreviewV3(book.coverPath, book.title, Modifier.width(82.dp).height(118.dp).clip(RoundedCornerShape(2.dp)).clickable { onOpenBook(book.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplicaProfileV8(
    bookCount: Int, latest: ReaderBookUi?, onBack: () -> Unit, onShelves: () -> Unit,
    onImport: () -> Unit, onCreate: () -> Unit, onTavern: () -> Unit,
    onSkills: () -> Unit, onTasks: () -> Unit, onSettings: () -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        IconButton(onClick = onBack, Modifier.padding(start = 10.dp, top = 8.dp)) { Icon(Icons.Rounded.ArrowBack, "返回", Modifier.size(28.dp)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, Modifier.size(44.dp), tint = tokens.textSecondary) }
            Column(Modifier.padding(start = 22.dp)) {
                Text("游客", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary)
                Text("$bookCount 本图书 · 本地优先", Modifier.padding(top = 6.dp), color = tokens.textSecondary)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            ReplicaProfileRowV8(Icons.Rounded.MenuBook, "书架列表", onShelves)
            ReplicaProfileRowV8(Icons.Rounded.FolderOpen, "导入图书", onImport)
            ReplicaProfileRowV8(Icons.Rounded.AutoAwesome, "AI 新建小说", onCreate)
            if (latest != null) ReplicaProfileRowV8(Icons.Rounded.TheaterComedy, "进入故事", onTavern)
            ReplicaProfileRowV8(Icons.Rounded.AutoStories, "写作 Skills", onSkills)
            ReplicaProfileRowV8(Icons.Rounded.TaskAlt, "后台任务", onTasks)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            ReplicaProfileRowV8(Icons.Rounded.Settings, "AI 与应用设置", onSettings)
        }
    }
}

@Composable
private fun ReplicaProfileRowV8(icon: ImageVector, label: String, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(26.dp), tint = tokens.textPrimary)
        Text(label, Modifier.padding(start = 22.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.textPrimary)
    }
}

@Composable
private fun ReplicaMenuRowV8(icon: ImageVector, label: String, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(25.dp), tint = tokens.textPrimary)
        Text(label, Modifier.padding(start = 22.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.textPrimary)
    }
}

internal fun sortShelfBooksV8(
    books: List<ReaderBookUi>, query: String, sort: ReaderShelfSortV8,
    lastRead: (String) -> Long, importedAt: (String) -> Long,
): List<ReaderBookUi> {
    val key = query.trim()
    val filtered = books.filter { key.isBlank() || it.title.contains(key, true) || it.genre.contains(key, true) }
    return when (sort) {
        ReaderShelfSortV8.RECENT -> filtered.sortedWith(compareByDescending<ReaderBookUi> { lastRead(it.id) }.thenByDescending { it.updatedAt })
        ReaderShelfSortV8.UPDATED -> filtered.sortedByDescending { it.updatedAt }
        ReaderShelfSortV8.TITLE -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
        ReaderShelfSortV8.IMPORTED -> filtered.sortedWith(compareByDescending<ReaderBookUi> { importedAt(it.id) }.thenByDescending { it.updatedAt })
    }
}
