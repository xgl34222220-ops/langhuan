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
import androidx.compose.foundation.layout.weight
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
import com.xiguli.langhuan.ui.design.ShadcnInput

private enum class MobileShelfTab { LIBRARY, ME }

/**
 * 琅嬛移动书架 V2。
 *
 * 目标：像真正的中文小说阅读器，而不是工具后台。
 * 封面是主视觉，操作退到顶部和 BottomSheet；shadcn 只保留底层 primitive，不参与页面构图。
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
        state.stories
            .sortedByDescending { it.updatedAt }
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
                    .padding(horizontal = 22.dp, vertical = 86.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = t.card.copy(alpha = .97f),
                shadowElevation = 10.dp,
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("正在导入小说", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("添加小说", style = MaterialTheme.typography.headlineSmall, color = t.foreground, fontWeight = FontWeight.Bold)
                Text("本地阅读优先，AI 创作放在第二入口。", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                Spacer(Modifier.height(18.dp))
                MobileActionRow(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                    showAdd = false
                    onImportLocal()
                }
                Spacer(Modifier.height(10.dp))
                MobileActionRow(Icons.Rounded.AutoAwesome, "AI 创建小说", "用自然语言逐步建立一本新书") {
                    showAdd = false
                    onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(20.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${book.genre.ifBlank { "小说" }} · 读到第 ${book.currentChapter.coerceAtLeast(1)} 章",
                    Modifier.padding(top = 4.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                MobileMenuGroup {
                    MobileGroupedMenuRow(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置") {
                        actionsFor = null
                        onOpenBook(book.id)
                    }
                    HorizontalDivider(color = t.border.copy(alpha = .65f), modifier = Modifier.padding(start = 58.dp))
                    MobileGroupedMenuRow(Icons.Rounded.TheaterComedy, "进入故事", "从这本书进入互动故事") {
                        actionsFor = null
                        onOpenTavern(book.id)
                    }
                    HorizontalDivider(color = t.border.copy(alpha = .65f), modifier = Modifier.padding(start = 58.dp))
                    MobileGroupedMenuRow(Icons.Rounded.DeleteOutline, "删除小说", "同时删除章节与项目数据", destructive = true) {
                        actionsFor = null
                        pendingDelete = book
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(20.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = t.card,
            shape = RoundedCornerShape(28.dp),
            title = { Text("删除《${book.title}》？", color = t.foreground) },
            text = { Text("章节、版本和项目数据会一起删除。", color = t.mutedForeground) },
            confirmButton = {
                ShadcnButton(
                    "删除",
                    {
                        pendingDelete = null
                        onDeleteBook(book.id)
                    },
                    variant = ShadcnButtonVariant.DESTRUCTIVE,
                    size = ShadcnButtonSize.SM,
                )
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
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("书架", style = MaterialTheme.typography.headlineLarge, color = t.foreground, fontWeight = FontWeight.Bold)
                if (books.isNotEmpty()) {
                    Text("${books.size} 本小说", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
            }
            MobileTopIcon(
                selected = searchOpen,
                onClick = onToggleSearch,
                icon = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                description = "搜索",
            )
            Spacer(Modifier.size(8.dp))
            MobileTopIcon(onClick = onAdd, icon = Icons.Rounded.Add, description = "添加小说")
        }

        if (searchOpen) {
            ShadcnInput(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                placeholder = "搜索书名或类型",
                leadingIcon = Icons.Rounded.Search,
            )
        }

        when {
            !libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.primary)
            }

            books.isEmpty() -> Box(Modifier.fillMaxSize().padding(horizontal = 36.dp, bottom = 92.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(74.dp), shape = RoundedCornerShape(24.dp), color = t.accent) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoStories, null, Modifier.size(32.dp), tint = t.accentForeground)
                        }
                    }
                    Text("还没有书", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.titleLarge, color = t.foreignOrForeground(), fontWeight = FontWeight.SemiBold)
                    Text("导入本地小说，或者从一个想法开始创作。", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = t.mutedForeground)
                    ShadcnButton("添加第一本书", onAdd, Modifier.padding(top = 20.dp), size = ShadcnButtonSize.SM, leadingIcon = Icons.Rounded.Add)
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 118.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
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

@Composable
private fun MobileTopIcon(
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (selected) t.accent else t.card,
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(21.dp), tint = if (selected) t.accentForeground else t.foreground)
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

    Column(Modifier.fillMaxWidth().combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(.69f),
            shape = RoundedCornerShape(18.dp),
            color = t.card,
            shadowElevation = 2.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().background(t.accent).padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(book.genre.ifBlank { "小说" }, style = MaterialTheme.typography.labelSmall, color = t.accentForeground.copy(alpha = .72f))
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = t.accentForeground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = t.accentForeground.copy(alpha = .65f))
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(9.dp),
                    shape = RoundedCornerShape(50),
                    color = t.card.copy(alpha = .88f),
                ) {
                    Text(
                        "第 ${book.currentChapter.coerceAtLeast(1)} 章",
                        Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = t.foreground,
                    )
                }

                if (busy) {
                    Surface(modifier = Modifier.align(Alignment.Center), shape = CircleShape, color = t.card.copy(alpha = .94f), shadowElevation = 5.dp) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp, color = t.primary)
                    }
                }
            }
        }

        Text(
            book.title,
            Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.genre.ifBlank { "小说" },
            Modifier.padding(start = 2.dp, top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
            maxLines = 1,
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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.headlineLarge, color = t.foreground, fontWeight = FontWeight.Bold)
            Text("${books.size} 本作品 · $totalWords 字", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileShortcut(Modifier.weight(1f), Icons.Rounded.AutoAwesome, "AI 新建", onCreate)
                MobileShortcut(Modifier.weight(1f), Icons.Rounded.FolderOpen, "导入", onImportLocal)
                MobileShortcut(Modifier.weight(1f), Icons.Rounded.TheaterComedy, "进入故事") { recent?.let { onOpenTavern(it.id) } }
            }
        }

        item {
            Text("工具", Modifier.padding(start = 4.dp, bottom = 9.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
            MobileMenuGroup {
                MobileGroupedMenuRow(Icons.Rounded.TaskAlt, "运行中心", "查看正在执行和已完成的任务", onClick = onRunCenter)
                HorizontalDivider(color = t.border.copy(alpha = .65f), modifier = Modifier.padding(start = 58.dp))
                MobileGroupedMenuRow(Icons.Rounded.AutoAwesome, "Skill 与写作能力", "管理实际参与写作的能力", onClick = onSkills)
                HorizontalDivider(color = t.border.copy(alpha = .65f), modifier = Modifier.padding(start = 58.dp))
                MobileGroupedMenuRow(Icons.Rounded.Settings, "AI 模型与中转站", "模型、接口和连接状态", onClick = onAiSetup)
            }
        }
    }
}

@Composable
private fun MobileShortcut(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, modifier = modifier.height(88.dp), shape = RoundedCornerShape(22.dp), color = t.card) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(13.dp), color = t.accent) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = t.accentForeground) }
            }
            Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = t.foreground, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MobileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = t.card) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(15.dp), color = t.accent) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp), tint = t.accentForeground) }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = t.mutedForeground)
        }
    }
}

@Composable
private fun MobileMenuGroup(content: @Composable () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = t.card) {
        Column(content = content)
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(11.dp),
            color = if (destructive) t.destructive.copy(alpha = .10f) else t.accent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (destructive) t.destructive else t.accentForeground)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .65f))
    }
}

@Composable
private fun MobileShelfDock(
    selected: MobileShelfTab,
    onSelected: (MobileShelfTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 58.dp, vertical = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = t.card.copy(alpha = .96f),
        shadowElevation = 12.dp,
    ) {
        Row(Modifier.height(58.dp).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            MobileDockItem(Modifier.weight(1f), Icons.Rounded.AutoStories, "书架", selected == MobileShelfTab.LIBRARY) {
                onSelected(MobileShelfTab.LIBRARY)
            }
            MobileDockItem(Modifier.weight(1f), Icons.Rounded.Person, "我的", selected == MobileShelfTab.ME) {
                onSelected(MobileShelfTab.ME)
            }
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
        shape = RoundedCornerShape(24.dp),
        color = if (selected) t.accent else Color.Transparent,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) t.accentForeground else t.mutedForeground)
            Text(
                label,
                Modifier.padding(start = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) t.accentForeground else t.mutedForeground,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

private fun com.xiguli.langhuan.ui.design.LanghuanUiTokens.foreignOrForeground(): Color = foreground
