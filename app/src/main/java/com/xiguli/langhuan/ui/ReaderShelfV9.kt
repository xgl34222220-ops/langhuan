package com.xiguli.langhuan.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanPageHeader
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

private enum class ReaderShelfPageV9 { BOOKS, SHELVES, PROFILE }

/**
 * Shadcn-inspired reader shell for Android Compose.
 *
 * The goal is not to imitate a web dashboard. It ports the useful design-system ideas:
 * semantic tokens, quiet borders, predictable primitives, restrained radii, compact actions,
 * and a stable mobile navigation shell.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderShelfV9(
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
    val t = LocalLanghuanUiTokens.current
    val progress = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    val meta = remember { context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE) }
    val prefs = remember { context.getSharedPreferences("reader_shelf_v3", Context.MODE_PRIVATE) }

    var page by rememberSaveable { mutableStateOf(ReaderShelfPageV9.BOOKS) }
    var selectedShelf by rememberSaveable {
        mutableStateOf(prefs.getString("active_shelf", "正在阅读") ?: "正在阅读")
    }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var movingBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var shelfTick by remember { mutableIntStateOf(0) }
    var newShelfName by remember { mutableStateOf("") }

    val shelves = remember(shelfTick) {
        prefs.getString("shelf_names", null)?.split('\u001F')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("正在阅读")
    }
    val sortedBooks = remember(state.stories, query, shelfTick) {
        sortShelfBooksV8(
            books = state.stories,
            query = query,
            sort = ReaderShelfSortV8.RECENT,
            lastRead = { id -> progress.getLong("last_$id", 0L) },
            importedAt = { id -> meta.getLong("imported_$id", 0L) },
        )
    }
    val visibleBooks = remember(sortedBooks, selectedShelf, shelfTick) {
        sortedBooks.filter { prefs.getString("book_shelf_${it.id}", "正在阅读") == selectedShelf }
    }
    val latest = sortedBooks.firstOrNull()

    LaunchedEffect(selectedShelf) {
        prefs.edit().putString("active_shelf", selectedShelf).apply()
    }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(page, label = "reader-shelf-v9-page") { current ->
                    when (current) {
                        ReaderShelfPageV9.BOOKS -> ReaderBookGridV9(
                            shelfName = selectedShelf,
                            books = visibleBooks,
                            libraryLoaded = state.libraryLoaded,
                            query = query,
                            searchVisible = searchVisible,
                            busy = importState.busy,
                            fileName = importState.currentFileName,
                            onQueryChange = { query = it },
                            onToggleSearch = {
                                searchVisible = !searchVisible
                                if (!searchVisible) query = ""
                            },
                            onAdd = { showAdd = true },
                            onOpen = onOpenBook,
                            onLong = { selectedBook = it },
                        )

                        ReaderShelfPageV9.SHELVES -> ReaderShelfListV9(
                            shelves = shelves,
                            books = sortedBooks,
                            assignment = { book ->
                                prefs.getString("book_shelf_${book.id}", "正在阅读") ?: "正在阅读"
                            },
                            onAdd = { showCreateShelf = true },
                            onOpenShelf = {
                                selectedShelf = it
                                page = ReaderShelfPageV9.BOOKS
                            },
                            onOpenBook = onOpenBook,
                        )

                        ReaderShelfPageV9.PROFILE -> ReaderProfileV9(
                            bookCount = state.stories.size,
                            latest = latest,
                            onShelves = { page = ReaderShelfPageV9.SHELVES },
                            onImport = onImportLocal,
                            onCreate = onCreate,
                            onTavern = { latest?.let { onOpenTavern(it.id) } },
                            onSkills = onSkills,
                            onTasks = onRunCenter,
                            onSettings = onAiSetup,
                        )
                    }
                }
            }

            ReaderBottomNavV9(
                selected = page,
                onSelected = { page = it },
            )
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text(
                    "添加图书",
                    style = MaterialTheme.typography.titleLarge,
                    color = t.foreground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 2.dp) {
                    Column {
                        LanghuanMenuRow(Icons.Rounded.FolderOpen, "导入本地小说", onClick = {
                            showAdd = false
                            onImportLocal()
                        }, subtitle = "TXT / EPUB / Markdown")
                        LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                        LanghuanMenuRow(Icons.Rounded.AutoAwesome, "AI 新建小说", onClick = {
                            showAdd = false
                            onCreate()
                        }, subtitle = "通过对话建立设定、蓝图和第一版方案")
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(12.dp))
            }
        }
    }

    selectedBook?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { selectedBook = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(
                    book.title,
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 2.dp) {
                    Column {
                        LanghuanMenuRow(Icons.Rounded.Info, "查看详情", onClick = {
                            selectedBook = null
                            onOpenBookInfo(book.id)
                        })
                        LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                        LanghuanMenuRow(Icons.Rounded.DriveFileMove, "移动到其他书架", onClick = {
                            selectedBook = null
                            movingBook = book
                        })
                        LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                        LanghuanMenuRow(Icons.Rounded.DeleteOutline, "删除图书", onClick = {
                            selectedBook = null
                            pendingDelete = book
                        })
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }

    movingBook?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { movingBook = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(
                    "移动《${book.title}》",
                    Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 2.dp) {
                    Column {
                        shelves.forEachIndexed { index, shelf ->
                            val checked = prefs.getString("book_shelf_${book.id}", "正在阅读") == shelf
                            LanghuanMenuRow(
                                icon = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                title = shelf,
                                onClick = {
                                    prefs.edit().putString("book_shelf_${book.id}", shelf).apply()
                                    movingBook = null
                                    shelfTick++
                                },
                                trailing = { if (checked) LanghuanBadge("当前", accent = true) },
                            )
                            if (index != shelves.lastIndex) LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("只删除琅嬛中的书架记录与阅读数据，不会修改手机上的原文件。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteBook(book.id)
                }) { Text("删除", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    if (showCreateShelf) {
        AlertDialog(
            onDismissRequest = { showCreateShelf = false },
            title = { Text("新建书架") },
            text = {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    label = { Text("书架名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(t.radiusMd),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newShelfName.trim()
                    if (name.isNotEmpty() && name !in shelves) {
                        prefs.edit().putString("shelf_names", (shelves + name).joinToString("\u001F")).apply()
                        selectedShelf = name
                        shelfTick++
                    }
                    newShelfName = ""
                    showCreateShelf = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateShelf = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderBookGridV9(
    shelfName: String,
    books: List<ReaderBookUi>,
    libraryLoaded: Boolean,
    query: String,
    searchVisible: Boolean,
    busy: Boolean,
    fileName: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onLong: (ReaderBookUi) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LanghuanPageHeader(
            title = shelfName,
            subtitle = if (libraryLoaded) "${books.size} 本" else "正在载入书架",
            actions = {
                LanghuanIconButton(Icons.Rounded.Search, "搜索", onToggleSearch, selected = searchVisible)
                LanghuanIconButton(Icons.Rounded.Add, "添加图书", onAdd)
            },
        )

        if (searchVisible) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                placeholder = { Text("搜索书名或分类") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(19.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(t.radiusMd),
            )
            Spacer(Modifier.height(8.dp))
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = t.accent)
            Text(
                "正在导入 $fileName",
                Modifier.padding(horizontal = 20.dp, vertical = 7.dp),
                color = t.mutedForeground,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!libraryLoaded) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(88.dp).height(2.dp),
                        color = t.accent,
                    )
                    Text(
                        "正在载入书架",
                        Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.mutedForeground,
                    )
                }
            }
        } else if (books.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 24.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(t.radiusMd),
                            color = t.muted,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MenuBook, null, Modifier.size(24.dp), tint = t.mutedForeground)
                            }
                        }
                        Text(
                            "这个书架还是空的",
                            Modifier.padding(top = 14.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = t.foreground,
                        )
                        Text(
                            "导入一本小说，或让 AI 和你一起写一本。",
                            Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.mutedForeground,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = onAdd,
                            modifier = Modifier.padding(top = 18.dp),
                            shape = RoundedCornerShape(t.radiusMd),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = t.foreground,
                                contentColor = t.primaryForeground,
                            ),
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("添加图书")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                gridItems(books, key = { it.id }) { book ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpen(book.id) },
                                onLongClick = { onLong(book) },
                            ),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().aspectRatio(.69f),
                            shape = RoundedCornerShape(t.radiusSm),
                            border = BorderStroke(1.dp, t.border),
                            color = t.muted,
                            shadowElevation = 2.dp,
                        ) {
                            CoverPreviewV3(book.coverPath, book.title, Modifier.fillMaxSize())
                        }
                        Text(
                            text = book.title,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = t.foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (book.genre.isNotBlank()) {
                            Text(
                                text = book.genre,
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderShelfListV9(
    shelves: List<String>,
    books: List<ReaderBookUi>,
    assignment: (ReaderBookUi) -> String,
    onAdd: () -> Unit,
    onOpenShelf: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LanghuanPageHeader(
            eyebrow = "收藏与分类",
            title = "书架",
            subtitle = "${shelves.size} 个书架 · ${books.size} 本图书",
            actions = { LanghuanIconButton(Icons.Rounded.Add, "新建书架", onAdd) },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelves, key = { it }) { shelf ->
                val shelfBooks = books.filter { assignment(it) == shelf }
                LanghuanCard(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenShelf(shelf) },
                    contentPadding = 0.dp,
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(t.radiusSm),
                                color = t.muted,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.ViewModule, null, Modifier.size(19.dp), tint = t.foreground)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(shelf, style = MaterialTheme.typography.titleMedium, color = t.foreground)
                                Text("${shelfBooks.size} 本", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                            LanghuanBadge("打开")
                        }
                        if (shelfBooks.isNotEmpty()) {
                            LanghuanSeparator()
                            LazyRow(
                                contentPadding = PaddingValues(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(shelfBooks.take(8), key = { it.id }) { book ->
                                    Surface(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(92.dp)
                                            .clickable { onOpenBook(book.id) },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, t.border),
                                        color = t.muted,
                                    ) {
                                        CoverPreviewV3(book.coverPath, book.title, Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderProfileV9(
    bookCount: Int,
    latest: ReaderBookUi?,
    onShelves: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
    onTavern: () -> Unit,
    onSkills: () -> Unit,
    onTasks: () -> Unit,
    onSettings: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        LanghuanPageHeader(
            eyebrow = "琅嬛",
            title = "我的",
            subtitle = "阅读、创作与故事都从这里管理",
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        color = t.muted,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Person, null, Modifier.size(26.dp), tint = t.mutedForeground)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("本地创作空间", style = MaterialTheme.typography.titleMedium, color = t.foreground)
                        Text("$bookCount 本图书 · 数据优先保存在本机", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    LanghuanBadge("LOCAL", accent = true)
                }
            }

            ProfileGroupV9(
                items = listOf(
                    ProfileActionV9(Icons.Rounded.ViewModule, "书架管理", "整理分类与收藏", onShelves),
                    ProfileActionV9(Icons.Rounded.FolderOpen, "导入图书", "TXT、EPUB、Markdown", onImport),
                    ProfileActionV9(Icons.Rounded.AutoAwesome, "AI 新建小说", "对话建书与蓝图", onCreate),
                ),
            )

            ProfileGroupV9(
                items = buildList {
                    if (latest != null) add(ProfileActionV9(Icons.Rounded.TheaterComedy, "进入故事", "以角色身份进入当前世界", onTavern))
                    add(ProfileActionV9(Icons.Rounded.AutoStories, "写作 Skills", "管理小说能力与工作流", onSkills))
                    add(ProfileActionV9(Icons.Rounded.TaskAlt, "运行中心", "查看后台生成与任务状态", onTasks))
                },
            )

            ProfileGroupV9(
                items = listOf(
                    ProfileActionV9(Icons.Rounded.Settings, "AI 与应用设置", "模型、中转站与应用选项", onSettings),
                ),
            )
        }
    }
}

private data class ProfileActionV9(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileGroupV9(items: List<ProfileActionV9>) {
    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 2.dp) {
        Column {
            items.forEachIndexed { index, item ->
                LanghuanMenuRow(
                    icon = item.icon,
                    title = item.title,
                    subtitle = item.subtitle,
                    onClick = item.onClick,
                )
                if (index != items.lastIndex) LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}

@Composable
private fun ReaderBottomNavV9(
    selected: ReaderShelfPageV9,
    onSelected: (ReaderShelfPageV9) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(t.radiusLg),
        color = t.card,
        border = BorderStroke(1.dp, t.border),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BottomNavItemV9(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.MenuBook,
                label = "图书",
                selected = selected == ReaderShelfPageV9.BOOKS,
                onClick = { onSelected(ReaderShelfPageV9.BOOKS) },
            )
            BottomNavItemV9(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ViewModule,
                label = "书架",
                selected = selected == ReaderShelfPageV9.SHELVES,
                onClick = { onSelected(ReaderShelfPageV9.SHELVES) },
            )
            BottomNavItemV9(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.PersonOutline,
                label = "我的",
                selected = selected == ReaderShelfPageV9.PROFILE,
                onClick = { onSelected(ReaderShelfPageV9.PROFILE) },
            )
        }
    }
}

@Composable
private fun BottomNavItemV9(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier,
        color = if (selected) t.muted else t.card,
        shape = RoundedCornerShape(t.radiusMd),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, label, Modifier.size(18.dp), tint = if (selected) t.foreground else t.mutedForeground)
            if (selected) {
                Spacer(Modifier.width(7.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
