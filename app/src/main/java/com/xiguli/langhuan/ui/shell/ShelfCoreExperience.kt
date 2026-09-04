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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.material3.Scaffold
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
import com.xiguli.langhuan.ui.design.ShadcnCard
import com.xiguli.langhuan.ui.design.ShadcnIconButton
import com.xiguli.langhuan.ui.design.ShadcnInput
import com.xiguli.langhuan.ui.design.ShadcnMenuRow
import com.xiguli.langhuan.ui.design.ShadcnSeparator

private enum class ShelfCoreTab { BOOKS, SHELVES, PROFILE }

/**
 * Main shelf rebuilt from the shadcn/ui New York composition model:
 * neutral surfaces, compact controls, border-first grouping and almost no nested containers.
 * Navigation semantics remain unchanged: one tap emits one open request while the selected book
 * stays in-place with a local progress indicator until the root has prepared the reader state.
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
    val t = LocalLanghuanUiTokens.current
    var tab by rememberSaveable { mutableStateOf(ShelfCoreTab.BOOKS) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val books = remember(state.stories, query) {
        state.stories
            .sortedByDescending { it.updatedAt }
            .filter {
                query.isBlank() ||
                    it.title.contains(query, ignoreCase = true) ||
                    it.genre.contains(query, ignoreCase = true)
            }
    }

    Box(Modifier.fillMaxSize().background(t.background)) {
        Scaffold(
            containerColor = t.background,
            topBar = {
                ShelfShadcnHeader(
                    tab = tab,
                    showAdd = tab != ShelfCoreTab.PROFILE,
                    onAdd = { showAdd = true },
                )
            },
            bottomBar = {
                ShelfShadcnBottomBar(
                    selected = tab,
                    onSelected = { tab = it },
                )
            },
        ) { inner ->
            when (tab) {
                ShelfCoreTab.BOOKS, ShelfCoreTab.SHELVES -> ShelfBookGrid(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    books = books,
                    query = query,
                    onQuery = { query = it },
                    libraryLoaded = state.libraryLoaded,
                    openingBookId = openingBookId,
                    onOpenBook = onOpenBook,
                    onLongPressBook = { actionsFor = it },
                    onAdd = { showAdd = true },
                )

                ShelfCoreTab.PROFILE -> ShelfProfile(
                    modifier = Modifier.fillMaxSize().padding(inner),
                    state = state,
                    onCreate = onCreate,
                    onImportLocal = onImportLocal,
                    onOpenTavern = onOpenTavern,
                    onRunCenter = onRunCenter,
                    onSkills = onSkills,
                    onAiSetup = onAiSetup,
                )
            }
        }

        if (importState.busy) {
            ShadcnCard(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 68.dp)
                    .fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = t.foreground)
                    Text(
                        "正在导入 ${importState.currentFileName.orEmpty()}",
                        Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("添加小说", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                Text(
                    "导入已有作品，或直接和 AI 对话创建。",
                    Modifier.padding(top = 4.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                ShadcnCard(Modifier.fillMaxWidth()) {
                    ShadcnMenuRow(Icons.Rounded.FolderOpen, "导入本地小说", "TXT / EPUB / Markdown", {
                        showAdd = false
                        onImportLocal()
                    })
                    ShadcnSeparator()
                    ShadcnMenuRow(Icons.Rounded.AutoAwesome, "AI 新建小说", "用自然语言建立作品", {
                        showAdd = false
                        onCreate()
                    })
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = t.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${book.genre.ifBlank { "小说" }} · 第 ${book.currentChapter.coerceAtLeast(1)} 章",
                    Modifier.padding(top = 4.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                ShadcnCard(Modifier.fillMaxWidth()) {
                    ShadcnMenuRow(Icons.Rounded.AutoStories, "继续阅读", "回到上次阅读位置", {
                        actionsFor = null
                        onOpenBook(book.id)
                    })
                    ShadcnSeparator()
                    ShadcnMenuRow(Icons.Rounded.TheaterComedy, "故事模式", "进入互动故事", {
                        actionsFor = null
                        onOpenTavern(book.id)
                    })
                    ShadcnSeparator()
                    ShadcnMenuRow(Icons.Rounded.DeleteOutline, "删除小说", "删除作品及本地章节数据", {
                        actionsFor = null
                        pendingDelete = book
                    }, destructive = true)
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = t.card,
            title = { Text("删除《${book.title}》？", color = t.foreground) },
            text = { Text("章节、版本和项目数据会一起删除。", color = t.mutedForeground) },
            confirmButton = {
                ShadcnButton(
                    text = "删除",
                    onClick = {
                        pendingDelete = null
                        onDeleteBook(book.id)
                    },
                    variant = ShadcnButtonVariant.DESTRUCTIVE,
                    size = ShadcnButtonSize.SM,
                )
            },
            dismissButton = {
                ShadcnButton(
                    text = "取消",
                    onClick = { pendingDelete = null },
                    variant = ShadcnButtonVariant.OUTLINE,
                    size = ShadcnButtonSize.SM,
                )
            },
        )
    }
}

@Composable
private fun ShelfShadcnHeader(
    tab: ShelfCoreTab,
    showAdd: Boolean,
    onAdd: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(color = t.background, contentColor = t.foreground) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛", style = MaterialTheme.typography.headlineMedium, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (tab) {
                            ShelfCoreTab.BOOKS -> "阅读"
                            ShelfCoreTab.SHELVES -> "书架"
                            ShelfCoreTab.PROFILE -> "创作与设置"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = t.mutedForeground,
                    )
                }
                if (showAdd) {
                    ShadcnIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "添加小说",
                        onClick = onAdd,
                        variant = ShadcnButtonVariant.OUTLINE,
                    )
                }
            }
            HorizontalDivider(color = t.border, thickness = 1.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookGrid(
    modifier: Modifier,
    books: List<ReaderBookUi>,
    query: String,
    onQuery: (String) -> Unit,
    libraryLoaded: Boolean,
    openingBookId: String?,
    onOpenBook: (String) -> Unit,
    onLongPressBook: (ReaderBookUi) -> Unit,
    onAdd: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(modifier.padding(horizontal = 16.dp)) {
        ShadcnInput(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
            placeholder = "搜索书名或类型",
            leadingIcon = Icons.Rounded.Search,
        )

        when {
            !libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = t.foreground)
            }

            books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.AutoStories, null, Modifier.size(30.dp), tint = t.mutedForeground)
                    Text("还没有小说", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text("导入一本，或用 AI 创建。", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                    ShadcnButton(
                        text = "添加小说",
                        onClick = onAdd,
                        modifier = Modifier.padding(top = 14.dp),
                        size = ShadcnButtonSize.SM,
                        leadingIcon = Icons.Rounded.Add,
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    ShelfBookTile(
                        book = book,
                        busy = openingBookId == book.id,
                        enabled = openingBookId == null,
                        onClick = { onOpenBook(book.id) },
                        onLongClick = { onLongPressBook(book) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookTile(
    book: ReaderBookUi,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val cover = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(.73f),
            shape = RoundedCornerShape(t.radiusLg),
            color = t.card,
            border = BorderStroke(1.dp, t.border),
            shadowElevation = 1.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(t.radiusLg)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().background(t.muted).padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("琅嬛", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = t.foreground,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(book.genre.ifBlank { "小说" }, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                    }
                }

                if (busy) {
                    Surface(color = t.card.copy(alpha = .88f), shape = RoundedCornerShape(999.dp)) {
                        CircularProgressIndicator(Modifier.padding(10.dp).size(20.dp), strokeWidth = 2.dp, color = t.foreground)
                    }
                }
            }
        }

        Text(
            book.title,
            Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "第 ${book.currentChapter.coerceAtLeast(1)} 章",
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
            Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(15.dp), tint = t.mutedForeground.copy(alpha = .7f))
        }
    }
}

@Composable
private fun ShelfProfile(
    modifier: Modifier,
    state: LibraryExperienceState,
    onCreate: () -> Unit,
    onImportLocal: () -> Unit,
    onOpenTavern: (String) -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
    onAiSetup: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("创作", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
            ShadcnCard(Modifier.fillMaxWidth()) {
                ShadcnMenuRow(Icons.Rounded.AutoAwesome, "AI 新建小说", "用自然语言对话建立新作品", onCreate)
                ShadcnSeparator()
                ShadcnMenuRow(Icons.Rounded.FolderOpen, "导入本地小说", "TXT / EPUB / Markdown", onImportLocal)
                ShadcnSeparator()
                ShadcnMenuRow(Icons.Rounded.TheaterComedy, "故事模式", "从最近作品进入互动故事", {
                    state.stories.maxByOrNull { it.updatedAt }?.let { onOpenTavern(it.id) }
                })
            }
        }
        item {
            Text("工具", style = MaterialTheme.typography.labelMedium, color = t.mutedForeground, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
            ShadcnCard(Modifier.fillMaxWidth()) {
                ShadcnMenuRow(Icons.Rounded.TaskAlt, "运行中心", "查看生成任务与执行记录", onRunCenter)
                ShadcnSeparator()
                ShadcnMenuRow(Icons.Rounded.AutoAwesome, "Skill", "管理写作能力与工具", onSkills)
                ShadcnSeparator()
                ShadcnMenuRow(Icons.Rounded.Settings, "AI 设置", "模型、中转站和连接配置", onAiSetup)
            }
        }
    }
}

@Composable
private fun ShelfShadcnBottomBar(
    selected: ShelfCoreTab,
    onSelected: (ShelfCoreTab) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Surface(color = t.card, contentColor = t.foreground) {
        Column {
            HorizontalDivider(color = t.border, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShelfBottomItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.AutoStories,
                    label = "阅读",
                    selected = selected == ShelfCoreTab.BOOKS,
                    onClick = { onSelected(ShelfCoreTab.BOOKS) },
                )
                ShelfBottomItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.AutoStories,
                    label = "书架",
                    selected = selected == ShelfCoreTab.SHELVES,
                    onClick = { onSelected(ShelfCoreTab.SHELVES) },
                )
                ShelfBottomItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Person,
                    label = "我的",
                    selected = selected == ShelfCoreTab.PROFILE,
                    onClick = { onSelected(ShelfCoreTab.PROFILE) },
                )
            }
        }
    }
}

@Composable
private fun ShelfBottomItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(2.dp).width(30.dp).background(if (selected) t.foreground else Color.Transparent))
        Spacer(Modifier.height(7.dp))
        Icon(icon, null, Modifier.size(19.dp), tint = if (selected) t.foreground else t.mutedForeground)
        Text(
            label,
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.foreground else t.mutedForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
