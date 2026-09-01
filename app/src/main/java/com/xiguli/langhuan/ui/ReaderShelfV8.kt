package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

enum class ReaderShelfLayoutV8(val key: String) { GRID("grid"), LIST("list") }
enum class ReaderShelfSortV8(val key: String, val label: String) {
    RECENT("recent", "最近阅读"),
    UPDATED("updated", "最近更新"),
    TITLE("title", "书名"),
    IMPORTED("imported", "最近导入"),
}

/**
 * Reader Shelf V8：把本地书籍管理做成真正的阅读器书架。
 * 添加、排序、布局切换、长按菜单全部收进低噪声 Miuix 交互。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderShelfV8(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onOpenBookInfo: (String) -> Unit,
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
    val shelfPrefs = remember { context.getSharedPreferences("reader_shelf_v2", Context.MODE_PRIVATE) }

    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showShelfOptions by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var layoutKey by rememberSaveable {
        mutableStateOf(shelfPrefs.getString("layout", ReaderShelfLayoutV8.GRID.key) ?: ReaderShelfLayoutV8.GRID.key)
    }
    var sortKey by rememberSaveable {
        mutableStateOf(shelfPrefs.getString("sort", ReaderShelfSortV8.RECENT.key) ?: ReaderShelfSortV8.RECENT.key)
    }
    val layout = ReaderShelfLayoutV8.entries.firstOrNull { it.key == layoutKey } ?: ReaderShelfLayoutV8.GRID
    val sort = ReaderShelfSortV8.entries.firstOrNull { it.key == sortKey } ?: ReaderShelfSortV8.RECENT

    LaunchedEffect(layoutKey, sortKey) {
        shelfPrefs.edit().putString("layout", layoutKey).putString("sort", sortKey).apply()
    }

    val books = remember(state.stories, query, sortKey) {
        sortShelfBooksV8(
            books = state.stories,
            query = query,
            sort = sort,
            lastRead = { id -> progress.getLong("last_$id", 0L) },
            importedAt = { id -> meta.getLong("imported_$id", 0L) },
        )
    }
    val reading = remember(books) {
        books.maxByOrNull { progress.getLong("last_${it.id}", 0L) }
            ?: books.firstOrNull()
    }

    MiuixScaffold(
        containerColor = tokens.pageBackground,
        topBar = {
            MiuixTopAppBar(
                title = "琅嬛",
                largeTitle = "琅嬛",
                subtitle = when {
                    importState.busy -> "正在导入 ${importState.currentFileName}"
                    books.isEmpty() -> "本地阅读 · AI 创作 · 进入故事"
                    else -> "${books.size} 本图书 · ${sort.label}"
                },
                actions = {
                    MiuixIconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Rounded.Search, "搜索", tint = tokens.textPrimary)
                    }
                    MiuixIconButton(onClick = {
                        layoutKey = if (layout == ReaderShelfLayoutV8.GRID) ReaderShelfLayoutV8.LIST.key else ReaderShelfLayoutV8.GRID.key
                    }) {
                        Icon(
                            if (layout == ReaderShelfLayoutV8.GRID) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                            if (layout == ReaderShelfLayoutV8.GRID) "切换列表" else "切换网格",
                            tint = tokens.textPrimary,
                        )
                    }
                    MiuixIconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, "添加图书", tint = tokens.textPrimary)
                    }
                    MiuixIconButton(onClick = { showShelfOptions = true }) {
                        Icon(Icons.Rounded.MoreVert, "书架设置", tint = tokens.textPrimary)
                    }
                },
                bottomContent = {
                    Column {
                        if (searchVisible) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                                placeholder = { Text("搜索书名、分类") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                trailingIcon = {
                                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清空") }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedContainerColor = tokens.cardBackground,
                                    unfocusedContainerColor = tokens.cardBackground,
                                ),
                            )
                        }
                        if (importState.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    }
                },
            )
        },
    ) { inner ->
        when {
            books.isEmpty() -> EmptyShelfV8(
                modifier = Modifier.fillMaxSize().padding(inner),
                query = query,
                busy = importState.busy,
                onAdd = { showAdd = true },
            )
            layout == ReaderShelfLayoutV8.GRID -> ShelfGridV8(
                modifier = Modifier.fillMaxSize().padding(inner),
                books = books,
                reading = reading,
                progress = progress,
                onOpen = onOpenBook,
                onLong = { selectedBook = it },
            )
            else -> ShelfListV8(
                modifier = Modifier.fillMaxSize().padding(inner),
                books = books,
                reading = reading,
                progress = progress,
                meta = meta,
                onOpen = onOpenBook,
                onLong = { selectedBook = it },
            )
        }
    }

    if (showAdd) {
        OverlayBottomSheet(show = true, title = "添加图书", onDismissRequest = { showAdd = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                MiuixButton(
                    onClick = { showAdd = false; onImportLocal() },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入本地小说")
                }
                Text(
                    "支持 TXT、EPUB、Markdown。EPUB 会读取原书封面、作者和目录。",
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                MiuixCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    onClick = { showAdd = false; onCreate() },
                    showIndication = true,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = tokens.textSecondary)
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("AI 新建小说", fontWeight = FontWeight.Medium, color = tokens.textPrimary)
                            Text("进入创作流程，不影响本地阅读书架", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = tokens.textSecondary)
                    }
                }
            }
        }
    }

    if (showShelfOptions) {
        OverlayBottomSheet(show = true, title = "书架", onDismissRequest = { showShelfOptions = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("排序", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary)
                ReaderShelfSortV8.entries.forEach { option ->
                    ShelfOptionRowV8(
                        icon = if (sort == option) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        title = option.label,
                        selected = sort == option,
                    ) {
                        sortKey = option.key
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f))
                ShelfActionRowV8(Icons.Rounded.Tune, "AI 设置", "配置模型与中转站") { showShelfOptions = false; onAiSetup() }
                ShelfActionRowV8(Icons.Rounded.AutoStories, "写作 Skills", "管理创作技能") { showShelfOptions = false; onSkills() }
                ShelfActionRowV8(Icons.Rounded.TaskAlt, "后台任务", "查看分析与生成任务") { showShelfOptions = false; onRunCenter() }
            }
        }
    }

    selectedBook?.let { book ->
        val author = meta.getString("author_${book.id}", "").orEmpty()
        val format = meta.getString("format_${book.id}", if (book.genre == "导入作品") "本地" else book.genre).orEmpty()
        val last = progress.getLong("last_${book.id}", 0L)
        val chapter = progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))
        OverlayBottomSheet(show = true, title = null, onDismissRequest = { selectedBook = null }) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverPreviewV3(book.coverPath, book.title, Modifier.width(70.dp).height(101.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (author.isNotBlank()) Text(author, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        Text("$format · 第 $chapter 章", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        if (last > 0L) Text("最近阅读 ${formatShelfTimeV8(last)}", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                    }
                }
                MiuixButton(
                    onClick = { selectedBook = null; onOpenBook(book.id) },
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    cornerRadius = 18.dp,
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) { Text("继续阅读") }
                MiuixCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(0.dp),
                ) {
                    ShelfActionRowV8(Icons.Rounded.Info, "图书详情", "查看文件、章节、字数和导入信息") {
                        selectedBook = null
                        onOpenBookInfo(book.id)
                    }
                    HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .32f))
                    ShelfActionRowV8(Icons.Rounded.DeleteOutline, "删除图书", "只删除琅嬛记录，不删除手机原文件", danger = true) {
                        selectedBook = null
                        pendingDelete = book
                    }
                }
            }
        }
    }

    pendingDelete?.let { book ->
        OverlayBottomSheet(show = true, title = "删除《${book.title}》？", onDismissRequest = { pendingDelete = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("只会移除琅嬛中的书架记录与阅读数据，不会修改手机上的原文件。", color = tokens.textSecondary)
                MiuixButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteBook(book.id)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    cornerRadius = 18.dp,
                    colors = MiuixButtonDefaults.buttonColors(
                        color = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("删除图书") }
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("取消") }
            }
        }
    }
}

@Composable
private fun ShelfGridV8(
    modifier: Modifier,
    books: List<ReaderBookUi>,
    reading: ReaderBookUi?,
    progress: android.content.SharedPreferences,
    onOpen: (String) -> Unit,
    onLong: (ReaderBookUi) -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        reading?.let { book ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("继续阅读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                    FeaturedReadingV8(book, progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1)), { onOpen(book.id) }) { onLong(book) }
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("全部图书", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                        Text("${books.size} 本", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                }
            }
        }
        gridItems(books, key = { it.id }) { book ->
            ShelfGridBookV8(book, progress, { onOpen(book.id) }) { onLong(book) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfGridBookV8(
    book: ReaderBookUi,
    progress: android.content.SharedPreferences,
    onOpen: () -> Unit,
    onLong: () -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLong)) {
        CoverPreviewV3(book.coverPath, book.title, Modifier.fillMaxWidth().aspectRatio(.69f).clip(RoundedCornerShape(11.dp)))
        Text(book.title, Modifier.padding(top = 9.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("第 ${progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))} 章", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfListV8(
    modifier: Modifier,
    books: List<ReaderBookUi>,
    reading: ReaderBookUi?,
    progress: android.content.SharedPreferences,
    meta: android.content.SharedPreferences,
    onOpen: (String) -> Unit,
    onLong: (ReaderBookUi) -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (reading != null) {
            item(key = "continue_${reading.id}") {
                Column {
                    Text("继续阅读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                    FeaturedReadingV8(reading, progress.getInt("chapter_${reading.id}", reading.currentChapter.coerceAtLeast(1)), { onOpen(reading.id) }) { onLong(reading) }
                    Text("全部图书", Modifier.padding(top = 22.dp, bottom = 2.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                }
            }
        }
        lazyItems(books, key = { it.id }) { book ->
            MiuixCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                onClick = { onOpen(book.id) },
                onLongPress = { onLong(book) },
                showIndication = true,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverPreviewV3(book.coverPath, book.title, Modifier.width(58.dp).height(84.dp).clip(RoundedCornerShape(9.dp)))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(book.title, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val author = meta.getString("author_${book.id}", "").orEmpty()
                        val format = meta.getString("format_${book.id}", if (book.genre == "导入作品") "本地" else book.genre).orEmpty()
                        if (author.isNotBlank()) Text(author, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$format · 第 ${progress.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))} 章", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = tokens.textSecondary)
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun FeaturedReadingV8(book: ReaderBookUi, chapter: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    MiuixCard(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
        onClick = onClick,
        onLongPress = onLongClick,
        showIndication = true,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(102.dp).clip(RoundedCornerShape(10.dp)))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("读到第 $chapter 章", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                Text(if (book.genre == "导入作品") "本地书籍" else book.genre, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = tokens.textSecondary)
        }
    }
}

@Composable
private fun EmptyShelfV8(modifier: Modifier, query: String, busy: Boolean, onAdd: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Column(modifier.padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (busy) Icons.Rounded.HourglassTop else Icons.Rounded.MenuBook, null, Modifier.size(48.dp), tint = tokens.textSecondary)
        Text(
            when {
                busy -> "正在整理这本书"
                query.isNotBlank() -> "没有找到这本书"
                else -> "把第一本书放进琅嬛"
            },
            Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary,
        )
        Text(
            when {
                busy -> "封面、目录和正文会一起读取"
                query.isNotBlank() -> "换个关键词试试"
                else -> "TXT、EPUB、Markdown 都可以直接导入阅读"
            },
            Modifier.padding(top = 8.dp),
            color = tokens.textSecondary,
        )
        if (!busy && query.isBlank()) {
            MiuixButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), cornerRadius = 18.dp, colors = MiuixButtonDefaults.buttonColorsPrimary()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("添加图书")
            }
        }
    }
}

@Composable
private fun ShelfOptionRowV8(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else tokens.textSecondary)
        Text(title, Modifier.padding(start = 14.dp).weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ShelfActionRowV8(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalMiuixTokens.current
    val main = if (danger) MaterialTheme.colorScheme.error else tokens.textPrimary
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (danger) MaterialTheme.colorScheme.error else tokens.textSecondary)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, color = main, fontWeight = FontWeight.Medium)
            Text(summary, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
        }
        if (!danger) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = tokens.textSecondary)
    }
}

internal fun sortShelfBooksV8(
    books: List<ReaderBookUi>,
    query: String,
    sort: ReaderShelfSortV8,
    lastRead: (String) -> Long,
    importedAt: (String) -> Long,
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

private fun formatShelfTimeV8(time: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
