package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.ShadcnButton
import com.xiguli.langhuan.ui.design.ShadcnButtonSize
import com.xiguli.langhuan.ui.design.ShadcnButtonVariant
import com.xiguli.langhuan.ui.design.ShadcnIconButton
import com.xiguli.langhuan.ui.design.ShadcnInput
import com.xiguli.langhuan.ui.theme.LanghuanShape

private enum class MobileShelfTab { LIBRARY, ME }

/**
 * Mobile-first bookshelf. shadcn is used only as a semantic/component baseline; composition follows
 * a native reading app: books are the primary visual object, search is secondary, and navigation is
 * a compact floating control rather than a desktop/admin navigation shell.
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
        state.stories.sortedByDescending { it.updatedAt }.filter {
            query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true)
        }
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

        MobileShelfDock(
            selected = tab,
            onSelected = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (importState.busy) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 88.dp)
                    .fillMaxWidth(),
                shape = LanghuanShape.card,
                color = t.card,
                border = BorderStroke(1.dp, t.border),
                shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.foreground)
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("正在导入小说", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                        Text(
                            importState.currentFileName.orEmpty(),
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

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("添加小说", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                        Text("从本地导入，或让 AI 帮你创建。", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    }
                    ShadcnIconButton(Icons.Rounded.Close, "关闭", { showAdd = false })
                }
                Spacer(Modifier.height(14.dp))
                MobileActionCard(
                    icon = Icons.Rounded.FolderOpen,
                    title = "导入本地小说",
                    subtitle = "支持 TXT、EPUB、Markdown",
                    onClick = { showAdd = false; onImportLocal() },
                )
                Spacer(Modifier.height(10.dp))
                MobileActionCard(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "AI 创建小说",
                    subtitle = "像聊天一样把想法逐步整理成一本书",
                    onClick = { showAdd = false; onCreate() },
                )
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章",
                    Modifier.padding(top = 3.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                MobileGroupedMenuRow(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置") {
                    actionsFor = null
                    onOpenBook(book.id)
                }
                MobileGroupedMenuRow(Icons.Rounded.TheaterComedy, "进入故事模式", "从这本书开启独立互动故事") {
                    actionsFor = null
                    onOpenTavern(book.id)
                }
                MobileGroupedMenuRow(Icons.Rounded.DeleteOutline, "删除小说", "同时删除章节与本地项目数据", destructive = true) {
                    actionsFor = null
                    pendingDelete = book
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = t.card,
            shape = LanghuanShape.card,
            title = { Text("删除《${book.title}》？", color = t.foreground) },
            text = { Text("章节、版本和项目数据会一起删除。", color = t.mutedForeground) },
            confirmButton = {
                ShadcnButton("删除", {
                    pendingDelete = null
                    onDeleteBook(book.id)
                }, variant = ShadcnButtonVariant.DESTRUCTIVE, size = ShadcnButtonSize.SM)
            },
            dismissButton = {
                ShadcnButton("取消", { pendingDelete = null }, variant = ShadcnButtonVariant.GHOST, size = ShadcnButtonSize.SM)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 15.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("琅嬛", style = MaterialTheme.typography.headlineLarge, color = t.foreground, fontWeight = FontWeight.Bold)
                Text(
                    if (books.isEmpty()) "你的私人书阁" else "${books.size} 本小说",
                    Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            Surface(
                onClick = onToggleSearch,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (searchOpen) t.muted else Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", Modifier.size(20.dp), tint = t.foreground)
                }
            }
            Spacer(Modifier.width(3.dp))
            Surface(
                onClick = onAdd,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = t.foreground,
                contentColor = t.primaryForeground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "添加小说", Modifier.size(21.dp), tint = t.primaryForeground)
                }
            }
        }

        if (searchOpen) {
            ShadcnInput(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                placeholder = "搜索书名或类型",
                leadingIcon = Icons.Rounded.Search,
            )
        }

        when {
            !libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.foreground)
            }

            books.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(68.dp), shape = CircleShape, color = t.muted) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoStories, null, Modifier.size(30.dp), tint = t.mutedForeground)
                        }
                    }
                    Text("书架还是空的", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text("导入一本小说，或者从一个想法开始。", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    ShadcnButton("添加第一本书", onAdd, Modifier.padding(top = 16.dp), size = ShadcnButtonSize.SM, leadingIcon = Icons.Rounded.Add)
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
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

    Column(
        Modifier.fillMaxWidth().combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(.70f),
            shape = LanghuanShape.cover,
            color = t.muted,
            border = BorderStroke(1.dp, t.border.copy(alpha = .72f)),
            shadowElevation = 4.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize().clip(LanghuanShape.cover),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().background(t.muted).padding(15.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("琅嬛 · ${book.genre.ifBlank { "小说" }}", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = t.foreground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("第 ${book.currentChapter.coerceAtLeast(1)} 章", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                    }
                }
                if (busy) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        shape = CircleShape,
                        color = t.card.copy(alpha = .92f),
                        shadowElevation = 4.dp,
                    ) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp, color = t.foreground)
                    }
                }
            }
        }
        Text(
            book.title,
            Modifier.padding(top = 9.dp),
            style = MaterialTheme.typography.titleSmall,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "读到第 ${book.currentChapter.coerceAtLeast(1)} 章 · ${book.currentWords.coerceAtLeast(0)} 字",
            Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(58.dp), shape = CircleShape, color = t.foreground, contentColor = t.primaryForeground) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, Modifier.size(27.dp), tint = t.primaryForeground) }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text("我的琅嬛", style = MaterialTheme.typography.headlineSmall, color = t.foreground, fontWeight = FontWeight.Bold)
                    Text("${books.size} 本作品 · $totalWords 字", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MobileQuickAction(Modifier.weight(1f), Icons.Rounded.AutoAwesome, "AI 新建", onCreate)
                MobileQuickAction(Modifier.weight(1f), Icons.Rounded.FolderOpen, "导入", onImportLocal)
                MobileQuickAction(Modifier.weight(1f), Icons.Rounded.TheaterComedy, "故事", { recent?.let { onOpenTavern(it.id) } })
            }
        }

        item {
            MobileSectionTitle("创作工具")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LanghuanShape.card,
                color = t.card,
                border = BorderStroke(1.dp, t.border),
            ) {
                Column {
                    MobileGroupedMenuRow(Icons.Rounded.TaskAlt, "运行中心", "查看正在执行和已经完成的生成任务", onClick = onRunCenter)
                    HorizontalDivider(color = t.border, modifier = Modifier.padding(start = 54.dp))
                    MobileGroupedMenuRow(Icons.Rounded.AutoAwesome, "Skill 与写作能力", "管理琅嬛实际会调用的写作能力", onClick = onSkills)
                    HorizontalDivider(color = t.border, modifier = Modifier.padding(start = 54.dp))
                    MobileGroupedMenuRow(Icons.Rounded.Settings, "AI 模型与中转站", "配置模型、接口地址和连接状态", onClick = onAiSetup)
                }
            }
        }
    }
}

@Composable
private fun MobileQuickAction(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = LanghuanShape.card,
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = t.foreground)
            Text(label, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelMedium, color = t.foreground, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MobileActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.card,
        color = t.card,
        border = BorderStroke(1.dp, t.border),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(44.dp), shape = LanghuanShape.cover, color = t.muted) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), tint = t.foreground) }
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = t.mutedForeground)
        }
    }
}

@Composable
private fun MobileGroupedMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(30.dp), shape = LanghuanShape.chip, color = if (destructive) t.destructive.copy(alpha = .10f) else t.muted) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(17.dp), tint = if (destructive) t.destructive else t.foreground)
            }
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 1.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .7f))
    }
}

@Composable
private fun MobileSectionTitle(text: String) {
    val t = LocalLanghuanUiTokens.current
    Text(text, Modifier.padding(start = 3.dp, bottom = 7.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground, fontWeight = FontWeight.Medium)
}

@Composable
private fun MobileShelfDock(
    selected: MobileShelfTab,
    onSelected: (MobileShelfTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 46.dp, vertical = 12.dp).fillMaxWidth(),
        shape = LanghuanShape.panel,
        color = t.card.copy(alpha = .97f),
        border = BorderStroke(1.dp, t.border),
        shadowElevation = 12.dp,
    ) {
        Row(Modifier.height(54.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            MobileDockItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AutoStories,
                label = "书架",
                selected = selected == MobileShelfTab.LIBRARY,
                onClick = { onSelected(MobileShelfTab.LIBRARY) },
            )
            MobileDockItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Person,
                label = "我的",
                selected = selected == MobileShelfTab.ME,
                onClick = { onSelected(MobileShelfTab.ME) },
            )
        }
    }
}

@Composable
private fun MobileDockItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxSize(),
        shape = LanghuanShape.card,
        color = if (selected) t.muted else Color.Transparent,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(19.dp), tint = if (selected) t.foreground else t.mutedForeground)
            if (selected) {
                Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
