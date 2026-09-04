package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Qingmo-like shelf tuned against device screenshots rather than generic card proportions.
 * Main canvas intentionally stays quiet: title + two actions + three-column covers + book names.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfQingmoV7(
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
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF101113) else Color(0xFFFDFDFD)
    val ink = if (dark) Color(0xFFF5F5F6) else Color(0xFF181A20)
    val secondaryInk = if (dark) Color(0xFFA7A9AE) else Color(0xFF6E7178)
    val editViewModel: BookEditViewModelV5 = viewModel()

    var addOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var editingBookId by rememberSaveable { mutableStateOf<String?>(null) }

    val editingBook = editingBookId?.let { id -> state.stories.firstOrNull { it.id == id } }
    if (editingBook != null) {
        BookEditPageV5(
            book = editingBook,
            editViewModel = editViewModel,
            onClose = {
                editViewModel.clearFeedback()
                editingBookId = null
            },
        )
        return
    }

    val books = remember(state.stories, query) {
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }

    Box(Modifier.fillMaxSize().background(canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在阅读",
                    modifier = Modifier.weight(1f),
                    color = ink,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { addOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Add, "添加作品", Modifier.size(29.dp), tint = ink)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { toolsOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Person, "书架工具", Modifier.size(28.dp), tint = ink)
                }
            }

            if (searchOpen) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
                    placeholder = { Text("搜索书名") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { searchOpen = false; query = "" }) {
                            Icon(Icons.Rounded.Close, "关闭搜索", Modifier.size(18.dp))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (dark) Color(0xFF1A1B1E) else Color(0xFFF3F3F4),
                        unfocusedContainerColor = if (dark) Color(0xFF1A1B1E) else Color(0xFFF3F3F4),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            when {
                !state.libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.primary)
                }

                books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有作品", color = ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        TextButton(onClick = { addOpen = true }) { Text("添加一本") }
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(25.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        QingmoBookV7(
                            book = book,
                            busy = openingBookId == book.id,
                            enabled = openingBookId == null,
                            ink = ink,
                            onOpen = { onOpenBook(book.id) },
                            onLongPress = { actionsFor = book },
                        )
                    }
                }
            }
        }

        if (importState.busy) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = t.card,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.primary)
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("正在导入小说", style = MaterialTheme.typography.bodyMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                        Text(importState.currentFileName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (addOpen) {
        ModalBottomSheet(
            onDismissRequest = { addOpen = false },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text("添加作品", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                QingmoActionV7(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                    addOpen = false
                    onImportLocal()
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.AutoAwesome, "AI 创建小说", "通过对话建立新作品") {
                    addOpen = false
                    onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    if (toolsOpen) {
        ModalBottomSheet(
            onDismissRequest = { toolsOpen = false },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text("书架工具", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                QingmoActionV7(Icons.Rounded.Search, "搜索书架", "按书名快速查找") {
                    toolsOpen = false
                    searchOpen = true
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.TaskAlt, "运行中心", "查看执行中的任务") {
                    toolsOpen = false
                    onRunCenter()
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.AutoAwesome, "写作能力", "管理参与写作的 Skill") {
                    toolsOpen = false
                    onSkills()
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.Settings, "AI 服务", "模型、中转站与路由") {
                    toolsOpen = false
                    onAiSetup()
                }
                Spacer(Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { actionsFor = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QingmoCoverV7(book, Modifier.width(48.dp).aspectRatio(.71f), false)
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.genre.ifBlank { "小说" }, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = secondaryInk)
                    }
                }
                Spacer(Modifier.height(10.dp))
                QingmoActionV7(Icons.Rounded.Edit, "编辑书籍", "修改书名、类型、简介和封面") {
                    actionsFor = null
                    editingBookId = book.id
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.AutoStories, "继续阅读", "打开这本小说") {
                    actionsFor = null
                    onOpenBook(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.TheaterComedy, "进入故事", "进入互动故事模式") {
                    actionsFor = null
                    onOpenTavern(book.id)
                }
                HorizontalDivider(color = t.border.copy(alpha = .28f), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV7(Icons.Rounded.DeleteOutline, "删除小说", "删除章节与项目数据", destructive = true) {
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
            shape = RoundedCornerShape(22.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QingmoBookV7(
    book: ReaderBookUi,
    busy: Boolean,
    enabled: Boolean,
    ink: Color,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            enabled = enabled,
            onClick = onOpen,
            onLongClick = onLongPress,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QingmoCoverV7(book, Modifier.fillMaxWidth().aspectRatio(.71f), busy)
        Text(
            text = book.title,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = ink,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QingmoCoverV7(book: ReaderBookUi, modifier: Modifier, busy: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val bitmap = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val palettes = listOf(
        listOf(Color(0xFF3A4653), Color(0xFF70859A)),
        listOf(Color(0xFF574A40), Color(0xFF9B7E68)),
        listOf(Color(0xFF354842), Color(0xFF718B80)),
        listOf(Color(0xFF50465B), Color(0xFF8E809E)),
    )
    val palette = palettes[(book.title.hashCode() and Int.MAX_VALUE) % palettes.size]

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(1.dp))
            .background(t.muted),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(palette)).padding(9.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(book.genre.ifBlank { "小说" }, color = Color.White.copy(alpha = .66f), fontSize = 9.sp)
                Text(book.title, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Text("琅嬛", color = Color.White.copy(alpha = .46f), fontSize = 9.sp)
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun QingmoActionV7(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (destructive) t.destructive else t.foreground)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (destructive) t.destructive else t.foreground, fontWeight = FontWeight.Medium)
                Text(subtitle, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }
    }
}
