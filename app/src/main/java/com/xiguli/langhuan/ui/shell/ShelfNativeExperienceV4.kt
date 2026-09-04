package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

private enum class ShelfSceneV4 { LIBRARY, ME }

/**
 * Cover-first mobile shelf.
 *
 * There is deliberately no recent-reading hero card and no persistent bottom navigation. A shelf
 * should look like a shelf: covers are the visual hierarchy, secondary tools stay one level away.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfNativeExperienceV4(
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
    var scene by rememberSaveable { mutableStateOf(ShelfSceneV4.LIBRARY) }
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
        when (scene) {
            ShelfSceneV4.LIBRARY -> LibrarySceneV4(
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
                onOpenMe = { scene = ShelfSceneV4.ME },
                onOpenBook = onOpenBook,
                onLongPress = { actionsFor = it },
            )

            ShelfSceneV4.ME -> MeSceneV4(
                books = state.stories,
                onBack = { scene = ShelfSceneV4.LIBRARY },
                onCreate = onCreate,
                onImportLocal = onImportLocal,
                onOpenTavern = onOpenTavern,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
                onAiSetup = onAiSetup,
            )
        }

        if (importState.busy) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = t.card,
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("添加到书架", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                ShelfActionRowV4(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                    showAdd = false
                    onImportLocal()
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 36.dp))
                ShelfActionRowV4(Icons.Rounded.AutoAwesome, "AI 创建小说", "从一个想法开始") {
                    showAdd = false
                    onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { actionsFor = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章", Modifier.padding(top = 3.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                ShelfActionRowV4(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置") {
                    actionsFor = null
                    onOpenBook(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 36.dp))
                ShelfActionRowV4(Icons.Rounded.TheaterComedy, "进入故事", "进入这本书的互动世界") {
                    actionsFor = null
                    onOpenTavern(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .30f), modifier = Modifier.padding(start = 36.dp))
                ShelfActionRowV4(Icons.Rounded.DeleteOutline, "删除小说", "删除章节与项目数据", destructive = true) {
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
            shape = RoundedCornerShape(24.dp),
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
private fun LibrarySceneV4(
    books: List<ReaderBookUi>,
    libraryLoaded: Boolean,
    openingBookId: String?,
    searchOpen: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onAdd: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenBook: (String) -> Unit,
    onLongPress: (ReaderBookUi) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("书架", style = MaterialTheme.typography.headlineMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
                if (books.isNotEmpty()) {
                    Text("${books.size} 本", Modifier.padding(top = 1.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                }
            }
            ShelfIconButtonV4(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", onToggleSearch)
            ShelfIconButtonV4(Icons.Rounded.Add, "添加小说", onAdd)
            ShelfIconButtonV4(Icons.Rounded.Person, "我的", onOpenMe)
        }

        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                placeholder = { Text("搜索书名") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }

        when {
            !libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.primary)
            }

            books.isEmpty() -> ShelfEmptyV4(onAdd)

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (books.size == 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            ShelfBookV4(
                                book = books.first(),
                                busy = openingBookId == books.first().id,
                                enabled = openingBookId == null,
                                modifier = Modifier.width(152.dp),
                                onClick = { onOpenBook(books.first().id) },
                                onLongClick = { onLongPress(books.first()) },
                            )
                        }
                    }
                } else {
                    items(books, key = { it.id }) { book ->
                        ShelfBookV4(
                            book = book,
                            busy = openingBookId == book.id,
                            enabled = openingBookId == null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { onLongPress(book) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookV4(
    book: ReaderBookUi,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)) {
        ShelfCoverV4(book, Modifier.fillMaxWidth().aspectRatio(.68f), busy)
        Text(
            book.title,
            Modifier.padding(top = 9.dp),
            style = MaterialTheme.typography.titleSmall,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "第 ${book.currentChapter.coerceAtLeast(1)} 章",
            Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShelfCoverV4(book: ReaderBookUi, modifier: Modifier, busy: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val palettes = listOf(
        listOf(Color(0xFF2C3849), Color(0xFF788CA1)),
        listOf(Color(0xFF5A4C40), Color(0xFFA88C70)),
        listOf(Color(0xFF364B45), Color(0xFF7F9A8F)),
        listOf(Color(0xFF51465F), Color(0xFF9789A8)),
    )
    val palette = palettes[(book.title.hashCode() and Int.MAX_VALUE) % palettes.size]

    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = t.card, shadowElevation = 1.dp) {
        Box(Modifier.fillMaxSize()) {
            if (cover != null) {
                Image(cover.asImageBitmap(), book.title, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(palette)).padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(book.genre.ifBlank { "小说" }, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .70f))
                    Text(book.title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .50f))
                }
            }
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ShelfEmptyV4(onAdd: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Box(Modifier.fillMaxSize().padding(horizontal = 42.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoStories, null, Modifier.size(32.dp), tint = t.mutedForeground.copy(alpha = .50f))
            Text("书架还是空的", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            Text("导入一本小说，或开始写第一本。", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Text("添加小说", Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun MeSceneV4(
    books: List<ReaderBookUi>,
    onBack: () -> Unit,
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShelfIconButtonV4(Icons.Rounded.ArrowBack, "返回书架", onBack)
                Column(Modifier.padding(start = 4.dp)) {
                    Text("我的", style = MaterialTheme.typography.headlineMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text("${books.size} 本作品 · $totalWords 字", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                }
            }
        }
        item {
            MeGroupV4("创作") {
                MeRowV4(Icons.Rounded.AutoAwesome, "AI 新建", "从一个想法开始建立小说", onCreate)
                MeDividerV4()
                MeRowV4(Icons.Rounded.FolderOpen, "导入小说", "从手机添加本地小说", onImportLocal)
                if (recent != null) {
                    MeDividerV4()
                    MeRowV4(Icons.Rounded.TheaterComedy, "进入故事", "继续最近作品的互动故事") { onOpenTavern(recent.id) }
                }
            }
        }
        item {
            MeGroupV4("工具") {
                MeRowV4(Icons.Rounded.TaskAlt, "运行中心", "查看执行中的任务", onRunCenter)
                MeDividerV4()
                MeRowV4(Icons.Rounded.AutoAwesome, "写作能力", "管理参与写作的 Skill", onSkills)
                MeDividerV4()
                MeRowV4(Icons.Rounded.Settings, "AI 服务", "模型、中转站和任务路由", onAiSetup)
            }
        }
    }
}

@Composable
private fun MeGroupV4(title: String, content: @Composable () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column {
        Text(title, Modifier.padding(start = 3.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = t.mutedForeground)
        Column { content() }
    }
}

@Composable
private fun MeRowV4(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(34.dp), shape = RoundedCornerShape(11.dp), color = t.accent) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = t.accentForeground) }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 1.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .54f))
    }
}

@Composable
private fun MeDividerV4() {
    val t = LocalLanghuanUiTokens.current
    HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 46.dp))
}

@Composable
private fun ShelfActionRowV4(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (destructive) t.destructive else t.primary)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
            Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = t.mutedForeground.copy(alpha = .52f))
    }
}

@Composable
private fun ShelfIconButtonV4(icon: ImageVector, description: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(20.dp), tint = t.foreground.copy(alpha = .88f))
        }
    }
}
