package com.xiguli.langhuan.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class ShelfCoreTab { BOOKS, SHELVES, PROFILE }

/**
 * New shelf surface. It intentionally does not depend on ReaderShelfV6/V8/V9 or the old
 * Langhuan card/menu primitives. One tap emits exactly one navigation request; async book loading
 * is represented locally on the tapped card instead of switching the whole root route twice.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfCoreExperience(
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
    var tab by rememberSaveable { mutableStateOf(ShelfCoreTab.BOOKS) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val books = remember(state.stories, query) {
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.genre.contains(query, ignoreCase = true) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("琅嬛", fontWeight = FontWeight.SemiBold)
                        Text(
                            when (tab) {
                                ShelfCoreTab.BOOKS -> "你的小说"
                                ShelfCoreTab.SHELVES -> "全部作品"
                                ShelfCoreTab.PROFILE -> "创作与设置"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (tab != ShelfCoreTab.PROFILE) {
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, "添加") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == ShelfCoreTab.BOOKS,
                    onClick = { tab = ShelfCoreTab.BOOKS },
                    icon = { Icon(Icons.Rounded.AutoStories, null) },
                    label = { Text("图书") },
                )
                NavigationBarItem(
                    selected = tab == ShelfCoreTab.SHELVES,
                    onClick = { tab = ShelfCoreTab.SHELVES },
                    icon = { Icon(Icons.Rounded.MenuBook, null) },
                    label = { Text("书架") },
                )
                NavigationBarItem(
                    selected = tab == ShelfCoreTab.PROFILE,
                    onClick = { tab = ShelfCoreTab.PROFILE },
                    icon = { Icon(Icons.Rounded.Person, null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { inner ->
        when (tab) {
            ShelfCoreTab.BOOKS, ShelfCoreTab.SHELVES -> Column(
                Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("搜索书名或类型") },
                    shape = RoundedCornerShape(18.dp),
                )

                if (!state.libraryLoaded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } else if (books.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.AutoStories, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("还没有小说", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
                            Text("导入本地作品，或让 AI 新建一本。", Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showAdd = true }, modifier = Modifier.padding(top = 16.dp)) { Text("添加小说") }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(154.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(books, key = { it.id }) { book ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = openingBookId == null,
                                        onClick = { onOpenBook(book.id) },
                                        onLongClick = { actionsFor = book },
                                    ),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 1.dp,
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(.78f),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Surface(
                                            Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {}
                                        if (openingBookId == book.id) {
                                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                book.title.take(4),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                    Text(
                                        book.title,
                                        Modifier.padding(top = 11.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章",
                                        Modifier.padding(top = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ShelfCoreTab.PROFILE -> Column(
                Modifier.fillMaxSize().padding(inner).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShelfCoreAction(Icons.Rounded.AutoAwesome, "AI 新建小说", "用自然语言对话建立新作品", onCreate)
                ShelfCoreAction(Icons.Rounded.FolderOpen, "导入本地小说", "TXT / EPUB / Markdown", onImportLocal)
                ShelfCoreAction(Icons.Rounded.TheaterComedy, "进入故事模式", "从最近一本作品进入互动故事") {
                    state.stories.maxByOrNull { it.updatedAt }?.let { onOpenTavern(it.id) }
                }
                ShelfCoreAction(Icons.Rounded.TaskAlt, "运行中心", "查看生成任务与执行记录", onRunCenter)
                ShelfCoreAction(Icons.Rounded.AutoAwesome, "Skill", "管理写作能力与工具", onSkills)
                ShelfCoreAction(Icons.Rounded.Settings, "AI 设置", "模型、中转站和连接配置", onAiSetup)
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("添加小说", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                ShelfCoreAction(Icons.Rounded.FolderOpen, "导入本地小说", "从设备选择小说文件") {
                    showAdd = false
                    onImportLocal()
                }
                ShelfCoreAction(Icons.Rounded.AutoAwesome, "AI 新建小说", "通过对话创建新作品") {
                    showAdd = false
                    onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ShelfCoreAction(Icons.Rounded.AutoStories, "继续阅读", "直接进入上次阅读位置") {
                    actionsFor = null
                    onOpenBook(book.id)
                }
                ShelfCoreAction(Icons.Rounded.TheaterComedy, "故事模式", "进入互动故事") {
                    actionsFor = null
                    onOpenTavern(book.id)
                }
                ShelfCoreAction(Icons.Rounded.DeleteOutline, "删除小说", "删除作品与本地章节数据") {
                    actionsFor = null
                    pendingDelete = book
                }
                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("章节、版本和项目数据会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteBook(book.id)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    if (importState.busy) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("正在导入 ${importState.currentFileName.orEmpty()}", Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun ShelfCoreAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(23.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
