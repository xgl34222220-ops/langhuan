package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

private enum class MobileShelfTab { LIBRARY, ME }

/**
 * Reading-first mobile library.
 * Book covers own the screen; creation and system tools stay one level behind the library.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfMobileExperience(
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
    val t = LocalLanghuanUiTokens.current
    var tab by rememberSaveable { mutableStateOf(MobileShelfTab.LIBRARY) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }

    val books = remember(state.stories, query) {
        state.stories.sortedByDescending { it.updatedAt }
            .filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }

    Box(Modifier.fillMaxSize().background(t.background)) {
        when (tab) {
            MobileShelfTab.LIBRARY -> MobileLibraryPage(
                books = books,
                libraryLoaded = state.libraryLoaded,
                openingBookId = openingBookId,
                searchOpen = searchOpen,
                query = query,
                onQuery = { query = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onAdd = { showAdd = true },
                onOpenBook = onOpenBook,
                onLongPress = { actionsFor = it },
            )

            MobileShelfTab.ME -> MobileMePage(
                books = state.stories,
                onCreate = onCreate,
                onImportLocal = onImportLocal,
                onOpenTavern = onOpenTavern,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
                onAiSetup = onAiSetup,
            )
        }

        MobileShelfNavigation(
            selected = tab,
            onSelected = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (importState.busy) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 76.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = t.card.copy(alpha = .98f),
                shadowElevation = 7.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("正在导入小说", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                        Text(importState.currentFileName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("添加小说", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text("导入已有小说，或开始一本新的 AI 作品。", Modifier.padding(top = 3.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                MobileMenuGroup {
                    MobileGroupedMenuRow(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                        showAdd = false
                        onImportLocal()
                    }
                    HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                    MobileGroupedMenuRow(Icons.Rounded.AutoAwesome, "AI 创建小说", "通过对话逐步建立一本新书") {
                        showAdd = false
                        onCreate()
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.genre.ifBlank { "小说" }} · 读到第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 3.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                MobileMenuGroup {
                    MobileGroupedMenuRow(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置") {
                        actionsFor = null
                        onOpenBook(book.id)
                    }
                    HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                    MobileGroupedMenuRow(Icons.Rounded.TheaterComedy, "进入故事", "从这本书进入互动故事") {
                        actionsFor = null
                        onOpenTavern(book.id)
                    }
                    HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                    MobileGroupedMenuRow(Icons.Rounded.DeleteOutline, "删除小说", "同时删除章节与项目数据", destructive = true) {
                        actionsFor = null
                        pendingDelete = book
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = t.card,
            shape = RoundedCornerShape(26.dp),
            title = { Text("删除《${book.title}》？", color = t.foreground) },
            text = { Text("章节、版本和项目数据会一起删除。", color = t.mutedForeground) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteBook(book.id)
                }) { Text("删除", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun MobileLibraryPage(
    books: List<ReaderBookUi>,
    libraryLoaded: Boolean,
    openingBookId: String?,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenBook: (String) -> Unit,
    onLongPress: (ReaderBookUi) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("书架", style = MaterialTheme.typography.headlineMedium, color = t.foreground, fontWeight = FontWeight.Bold)
                if (books.isNotEmpty()) Text("${books.size} 本小说", Modifier.padding(top = 1.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            MobileTopAction(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", onToggleSearch)
            MobileTopAction(Icons.Rounded.Add, "添加小说", onAdd)
        }

        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                placeholder = { Text("搜索书名或类型") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }

        when {
            !libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 2.dp, color = t.primary)
            }

            books.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 92.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(62.dp), shape = CircleShape, color = t.accent) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoStories, null, Modifier.size(28.dp), tint = t.accentForeground) }
                    }
                    Text("书架还是空的", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                    Text("导入一本小说，或者开始创作。", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    TextButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text("添加小说", Modifier.padding(start = 4.dp))
                    }
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 9.dp, bottom = 92.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(21.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    MobileBookTile(
                        book = book,
                        busy = openingBookId == book.id,
                        enabled = openingBookId == null,
                        onClick = { onOpenBook(book.id) },
                        onLongClick = { onLongPress(book) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileTopAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(21.dp), tint = t.foreground) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileBookTile(
    book: ReaderBookUi,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    Column(Modifier.fillMaxWidth().combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(.69f),
            shape = RoundedCornerShape(14.dp),
            color = t.card,
            shadowElevation = 1.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (cover != null) {
                    Image(cover.asImageBitmap(), book.title, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                } else {
                    Column(Modifier.fillMaxSize().background(t.accent).padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(book.genre.ifBlank { "小说" }, style = MaterialTheme.typography.labelSmall, color = t.accentForeground.copy(alpha = .70f))
                        Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.accentForeground, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = t.accentForeground.copy(alpha = .58f))
                    }
                }
                if (busy) {
                    Box(Modifier.fillMaxSize().background(t.background.copy(alpha = .52f)), contentAlignment = Alignment.Center) {
                        Surface(Modifier.size(44.dp), shape = CircleShape, color = t.card.copy(alpha = .96f)) {
                            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.primary) }
                        }
                    }
                }
            }
        }
        Text(book.title, Modifier.padding(start = 1.dp, end = 1.dp, top = 9.dp), style = MaterialTheme.typography.titleSmall, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(start = 1.dp, top = 2.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MobileMePage(
    books: List<ReaderBookUi>,
    onCreate: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenTavern: (String) -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
    onAiSetup: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val totalWords = remember(books) { books.sumOf { it.currentWords.coerceAtLeast(0) } }
    val recent = remember(books) { books.maxByOrNull { it.updatedAt } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.headlineMedium, color = t.foreground, fontWeight = FontWeight.Bold)
            Text("${books.size} 本作品 · $totalWords 字", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }
        item {
            Text("创作", Modifier.padding(start = 4.dp, bottom = 7.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            MobileMenuGroup {
                MobileGroupedMenuRow(Icons.Rounded.AutoAwesome, "AI 新建", "从一个想法开始建立小说", onClick = onCreate)
                HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                MobileGroupedMenuRow(Icons.Rounded.FolderOpen, "导入小说", "从手机添加本地小说", onClick = onImportLocal)
                if (recent != null) {
                    HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                    MobileGroupedMenuRow(Icons.Rounded.TheaterComedy, "进入故事", "继续最近作品的互动故事") { onOpenTavern(recent.id) }
                }
            }
        }
        item {
            Text("工具", Modifier.padding(start = 4.dp, bottom = 7.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            MobileMenuGroup {
                MobileGroupedMenuRow(Icons.Rounded.TaskAlt, "运行中心", "查看未完成和后台任务", onClick = onRunCenter)
                HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                MobileGroupedMenuRow(Icons.Rounded.AutoAwesome, "写作能力", "管理实际参与写作的 Skill", onClick = onSkills)
                HorizontalDivider(color = t.border.copy(alpha = .42f), modifier = Modifier.padding(start = 58.dp))
                MobileGroupedMenuRow(Icons.Rounded.Settings, "AI 服务", "模型、中转站和任务路由", onClick = onAiSetup)
            }
        }
    }
}

@Composable
private fun MobileMenuGroup(content: @Composable () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = t.card) { Column { content() } }
}

@Composable
private fun MobileGroupedMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(34.dp), shape = RoundedCornerShape(11.dp), color = if (destructive) t.destructive.copy(alpha = .10f) else t.accent) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = if (destructive) t.destructive else t.accentForeground) }
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 1.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .62f))
    }
}

@Composable
private fun MobileShelfNavigation(
    selected: MobileShelfTab,
    onSelected: (MobileShelfTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier = modifier.fillMaxWidth(), color = t.card.copy(alpha = .98f), shadowElevation = 5.dp) {
        Row(Modifier.navigationBarsPadding().height(62.dp).padding(horizontal = 46.dp), verticalAlignment = Alignment.CenterVertically) {
            MobileNavigationItem(Modifier.weight(1f), Icons.Rounded.AutoStories, "书架", selected == MobileShelfTab.LIBRARY) { onSelected(MobileShelfTab.LIBRARY) }
            MobileNavigationItem(Modifier.weight(1f), Icons.Rounded.Person, "我的", selected == MobileShelfTab.ME) { onSelected(MobileShelfTab.ME) }
        }
    }
}

@Composable
private fun MobileNavigationItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(Modifier.size(30.dp), shape = CircleShape, color = if (selected) t.accent else Color.Transparent) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(19.dp), tint = if (selected) t.accentForeground else t.mutedForeground) }
        }
        Text(label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (selected) t.foreground else t.mutedForeground, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
