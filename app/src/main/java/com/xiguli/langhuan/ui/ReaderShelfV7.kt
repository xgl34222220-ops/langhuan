package com.xiguli.langhuan.ui

import android.content.Context
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 参考成熟本地阅读器的信息密度重做：正在阅读 + 全部图书，导入是一级动作，AI 退到更多。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderShelfV7(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    val context = LocalContext.current
    val progressPrefs = remember { context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<ReaderBookUi?>(null) }

    val books = remember(state.stories, query) {
        val key = query.trim()
        state.stories.sortedByDescending { it.updatedAt }.filter {
            key.isBlank() || it.title.contains(key, true) || it.genre.contains(key, true)
        }
    }
    val reading = books.firstOrNull()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(start = 20.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("琅嬛", modifier = Modifier.weight(1f), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    IconButton({ searchVisible = !searchVisible }) { Icon(Icons.Rounded.Search, "搜索") }
                    IconButton(onImportLocal) { Icon(Icons.Rounded.Add, "导入小说") }
                    Box {
                        IconButton({ showMore = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("AI 新建小说") },
                                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                                onClick = { showMore = false; onCreate() },
                            )
                            HorizontalDivider()
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
                if (searchVisible) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
                        placeholder = { Text("搜索书名") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "清空") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }
                if (importState.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
        },
    ) { inner ->
        when {
            importState.busy -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                    Text("正在导入 ${importState.currentFileName}", modifier = Modifier.padding(top = 16.dp))
                    Text("解析完成后会直接打开阅读", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            books.isEmpty() -> EmptyReaderShelfV7(Modifier.padding(inner), query, onImportLocal, onCreate)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                reading?.let { book ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("正在阅读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            FeaturedReadingV7(
                                book = book,
                                chapter = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1)),
                                onClick = { onOpenBook(book.id) },
                                onLongClick = { selectedBook = book },
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("全部图书", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${books.size} 本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(books, key = { it.id }) { book ->
                    Column(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { selectedBook = book },
                        )
                    ) {
                        CoverPreviewV3(
                            book.coverPath,
                            book.title,
                            Modifier.fillMaxWidth().aspectRatio(.69f).clip(RoundedCornerShape(7.dp)),
                        )
                        Text(
                            book.title,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "第 ${progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))} 章",
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    selectedBook?.let { book ->
        ModalBottomSheet(onDismissRequest = { selectedBook = null }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp)) {
                ListItem(
                    headlineContent = { Text(book.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val last = progressPrefs.getLong("last_${book.id}", 0L)
                        Text(if (last > 0L) "最近阅读 ${formatShelfTimeV7(last)}" else "本地书籍")
                    },
                    leadingContent = {
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(5.dp)))
                    },
                )
                ListItem(
                    headlineContent = { Text("继续阅读") },
                    leadingContent = { Icon(Icons.Rounded.MenuBook, null) },
                    modifier = Modifier.combinedClickable(onClick = { selectedBook = null; onOpenBook(book.id) }),
                )
                ListItem(
                    headlineContent = { Text("删除图书", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("只删除琅嬛中的书籍记录，不修改手机上的原文件") },
                    leadingContent = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.combinedClickable(onClick = { selectedBook = null; onDeleteBook(book.id) }),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedReadingV7(book: ReaderBookUi, chapter: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(102.dp).clip(RoundedCornerShape(7.dp)))
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("读到第 $chapter 章", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (book.genre == "导入作品") "本地导入" else book.genre,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyReaderShelfV7(modifier: Modifier, query: String, onImportLocal: () -> Unit, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.MenuBook, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (query.isBlank()) "书架还是空的" else "没有找到这本书", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (query.isBlank()) "从手机导入 TXT、EPUB 或 Markdown，导入后直接开始阅读。" else "换个关键词试试。",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (query.isBlank()) {
            Button(onImportLocal, Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("导入本地小说")
            }
            TextButton(onCreate) { Text("AI 新建小说") }
        }
    }
}

private fun formatShelfTimeV7(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
