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
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

/**
 * 真实 Miuix 书架：页面骨架、顶部栏、卡片和主按钮直接使用 Miuix 组件，
 * 阅读内容仍保留现有成熟逻辑，避免为了换皮破坏书架/阅读数据。
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
    val tokens = LocalMiuixTokens.current
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

    MiuixScaffold(
        containerColor = tokens.pageBackground,
        topBar = {
            MiuixTopAppBar(
                title = "琅嬛",
                largeTitle = "琅嬛",
                subtitle = when {
                    importState.busy -> "正在导入 ${importState.currentFileName}"
                    books.isEmpty() -> "阅读 · 创作 · 进入故事"
                    else -> "${books.size} 本书 · 阅读与创作"
                },
                actions = {
                    MiuixIconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(Icons.Rounded.Search, "搜索", tint = tokens.textPrimary)
                    }
                    MiuixIconButton(onClick = onImportLocal) {
                        Icon(Icons.Rounded.Add, "导入小说", tint = tokens.textPrimary)
                    }
                    Box {
                        MiuixIconButton(onClick = { showMore = true }) {
                            Icon(Icons.Rounded.MoreVert, "更多", tint = tokens.textPrimary)
                        }
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
                },
                bottomContent = {
                    Column {
                        if (searchVisible) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                                placeholder = { Text("搜索书名或分类") },
                                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                trailingIcon = {
                                    if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "清空") }
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
            importState.busy -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                MiuixCard(cornerRadius = 22.dp, insideMargin = PaddingValues(horizontal = 28.dp, vertical = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("正在整理这本书", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                importState.currentFileName,
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("封面、目录和正文会一起读取", modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        }
                    }
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
                            Text("继续阅读", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                            FeaturedReadingV7(
                                book = book,
                                chapter = progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1)),
                                onClick = { onOpenBook(book.id) },
                                onLongClick = { selectedBook = book },
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("全部图书", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary)
                                Text("${books.size} 本", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
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
                            Modifier.fillMaxWidth().aspectRatio(.69f).clip(RoundedCornerShape(10.dp)),
                        )
                        Text(
                            book.title,
                            modifier = Modifier.padding(top = 9.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "第 ${progressPrefs.getInt("chapter_${book.id}", book.currentChapter.coerceAtLeast(1))} 章",
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary,
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
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(42.dp).height(60.dp).clip(RoundedCornerShape(6.dp)))
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

@Composable
private fun FeaturedReadingV7(book: ReaderBookUi, chapter: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            CoverPreviewV3(book.coverPath, book.title, Modifier.width(72.dp).height(102.dp).clip(RoundedCornerShape(9.dp)))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("读到第 $chapter 章", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                Text(
                    if (book.genre == "导入作品") "本地导入" else book.genre,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = tokens.textSecondary)
        }
    }
}

@Composable
private fun EmptyReaderShelfV7(modifier: Modifier, query: String, onImportLocal: () -> Unit, onCreate: () -> Unit) {
    val tokens = LocalMiuixTokens.current
    Column(
        modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MiuixCard(cornerRadius = 26.dp, insideMargin = PaddingValues(26.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MenuBook, null, Modifier.size(44.dp), tint = tokens.textSecondary)
                Text(
                    if (query.isBlank()) "把第一本书放进琅嬛" else "没有找到这本书",
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                )
                Text(
                    if (query.isBlank()) "TXT、EPUB、Markdown 都可以。EPUB 会读取原书封面和目录。" else "换个关键词试试。",
                    modifier = Modifier.padding(top = 8.dp),
                    color = tokens.textSecondary,
                )
                if (query.isBlank()) {
                    MiuixButton(onClick = onImportLocal, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), cornerRadius = 18.dp) {
                        Icon(Icons.Rounded.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入本地小说")
                    }
                    TextButton(onCreate) { Text("AI 新建小说") }
                }
            }
        }
    }
}

private fun formatShelfTimeV7(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
