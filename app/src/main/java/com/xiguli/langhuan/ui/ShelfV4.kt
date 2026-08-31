package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private enum class HomeTabV4(val label: String) {
    SHELF("书架"),
    CREATE("创作"),
    STORY("故事"),
    MINE("我的"),
}

/**
 * 琅嬛第一阶段产品壳：把原来的“AI 工具首页”改成真正的小说产品首页。
 *
 * 这一步只重构信息架构，不动现有写作/阅读内核：
 * 书架 = 像小说 App 一样找书和打开作品；
 * 创作 = AI 建书、导入、Skills 与后台任务入口；
 * 故事 = 为后续酒馆式演绎预留独立入口；
 * 我的 = AI 与全局配置。
 */
@Composable
fun ShelfV4(
    state: LibraryExperienceState,
    aiReady: Boolean,
    aiLabel: String,
    onOpenBook: (String) -> Unit,
    onCreate: () -> Unit,
    onReference: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTabV4.SHELF) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTabV4.SHELF,
                    onClick = { selectedTab = HomeTabV4.SHELF },
                    icon = { Icon(Icons.Rounded.MenuBook, null) },
                    label = { Text(HomeTabV4.SHELF.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTabV4.CREATE,
                    onClick = { selectedTab = HomeTabV4.CREATE },
                    icon = { Icon(Icons.Rounded.EditNote, null) },
                    label = { Text(HomeTabV4.CREATE.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTabV4.STORY,
                    onClick = { selectedTab = HomeTabV4.STORY },
                    icon = { Icon(Icons.Rounded.AutoStories, null) },
                    label = { Text(HomeTabV4.STORY.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTabV4.MINE,
                    onClick = { selectedTab = HomeTabV4.MINE },
                    icon = { Icon(Icons.Rounded.AccountCircle, null) },
                    label = { Text(HomeTabV4.MINE.label) },
                )
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            HomeTabV4.SHELF -> NewShelfHome(
                state = state,
                modifier = Modifier.padding(innerPadding),
                onOpenBook = onOpenBook,
                onCreate = onCreate,
                onAiSetup = onAiSetup,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
            )

            HomeTabV4.CREATE -> CreationHome(
                state = state,
                aiReady = aiReady,
                aiLabel = aiLabel,
                modifier = Modifier.padding(innerPadding),
                onOpenBook = onOpenBook,
                onCreate = onCreate,
                onReference = onReference,
                onAiSetup = onAiSetup,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
            )

            HomeTabV4.STORY -> StoryHome(
                state = state,
                modifier = Modifier.padding(innerPadding),
                onOpenBook = onOpenBook,
            )

            HomeTabV4.MINE -> MineHome(
                aiReady = aiReady,
                aiLabel = aiLabel,
                modifier = Modifier.padding(innerPadding),
                onAiSetup = onAiSetup,
                onRunCenter = onRunCenter,
                onSkills = onSkills,
            )
        }
    }
}

@Composable
private fun NewShelfHome(
    state: LibraryExperienceState,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
    onCreate: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }

    val books = remember(state.stories, query) {
        val key = query.trim()
        if (key.isBlank()) state.stories
        else state.stories.filter { book ->
            book.title.contains(key, ignoreCase = true) ||
                book.genre.contains(key, ignoreCase = true) ||
                book.premise.contains(key, ignoreCase = true)
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的书架", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton({ searching = !searching }) { Icon(Icons.Rounded.Search, "搜索") }
            IconButton(onCreate) { Icon(Icons.Rounded.Add, "新建小说") }
            Box {
                IconButton({ menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("AI 设置") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                        onClick = { menuExpanded = false; onAiSetup() },
                    )
                    DropdownMenuItem(
                        text = { Text("写作 Skills") },
                        leadingIcon = { Icon(Icons.Rounded.AutoStories, null) },
                        onClick = { menuExpanded = false; onSkills() },
                    )
                    DropdownMenuItem(
                        text = { Text("后台任务") },
                        leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) },
                        onClick = { menuExpanded = false; onRunCenter() },
                    )
                }
            }
        }

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                placeholder = { Text("搜索书名、题材或简介") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "清空") }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }

        if (books.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.MenuBook, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))
                Text(if (query.isBlank()) "书架还是空的" else "没有找到作品", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (query.isBlank()) "这里以后只负责看书和找书，创作入口放到“创作”页。" else "换个关键词试试。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (query.isBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Button(onCreate, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("创建第一本小说")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    ShelfBookTile(book = book, onClick = { onOpenBook(book.id) })
                }
            }
        }
    }
}

@Composable
private fun ShelfBookTile(book: ReaderBookUi, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            CoverPreviewV3(
                path = book.coverPath,
                title = book.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
            )
            Surface(
                modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .94f),
            ) {
                Text(
                    "创作中",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            book.title,
            modifier = Modifier.padding(top = 9.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${book.genre} · 第 ${book.currentChapter} 章 · ${book.currentWords} 字",
            modifier = Modifier.padding(top = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CreationHome(
    state: LibraryExperienceState,
    aiReady: Boolean,
    aiLabel: String,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
    onCreate: () -> Unit,
    onReference: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("创作", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("建书、写正文和高级 AI 能力都集中在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Surface(shape = RoundedCornerShape(26.dp), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (aiReady) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                            null,
                            tint = if (aiReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(if (aiReady) aiLabel.ifBlank { "AI 已连接" } else "还没有可用 AI", fontWeight = FontWeight.Bold)
                            Text(
                                if (aiReady) "可以直接建书、规划和写作" else "先配置中转站或官方 API",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onAiSetup) { Text(if (aiReady) "切换" else "配置") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 新建小说")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CreationActionCard(
                    title = "导入资料",
                    subtitle = "小说、设定、参考资料",
                    icon = Icons.Rounded.LibraryBooks,
                    modifier = Modifier.weight(1f),
                    onClick = onReference,
                )
                CreationActionCard(
                    title = "写作 Skills",
                    subtitle = "章纲导演、文风与方法",
                    icon = Icons.Rounded.AutoStories,
                    modifier = Modifier.weight(1f),
                    onClick = onSkills,
                )
            }
        }

        item {
            CreationActionCard(
                title = "后台任务",
                subtitle = "查看正在生成、失败或可恢复的任务",
                icon = Icons.Rounded.TaskAlt,
                modifier = Modifier.fillMaxWidth(),
                onClick = onRunCenter,
            )
        }

        if (state.stories.isNotEmpty()) {
            item {
                Text("继续创作", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            items(state.stories.sortedByDescending { it.updatedAt }.take(5), key = { it.id }) { book ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 1.dp,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(58.dp).height(82.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("第 ${book.currentChapter} 章 · ${book.currentWords} 字", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreationActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StoryHome(
    state: LibraryExperienceState,
    modifier: Modifier,
    onOpenBook: (String) -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("故事", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("这里会承载酒馆式演绎、分支存档和角色扮演。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("故事模式正在接入", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "下一阶段会从作品详情加入“进入故事”：从某一章建立世界快照，然后让你自由输入动作、对话或选择分支，同时保持原著线不被改写。",
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (state.stories.isNotEmpty()) {
            item { Text("选择一本作品", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(state.stories, key = { it.id }) { book ->
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverPreviewV3(book.coverPath, book.title, Modifier.width(66.dp).height(94.dp))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(book.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(book.genre, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(7.dp))
                            FilledTonalButton(
                                onClick = { onOpenBook(book.id) },
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("打开作品") }
                        }
                    }
                }
            }
        } else {
            item {
                Text("书架里还没有作品。先在“创作”页建一本小说，之后就能从这里进入故事。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MineHome(
    aiReady: Boolean,
    aiLabel: String,
    modifier: Modifier,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("我的", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("模型、能力和全局设置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (aiReady) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        null,
                        tint = if (aiReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(if (aiReady) aiLabel.ifBlank { "AI 已连接" } else "AI 未配置", fontWeight = FontWeight.Bold)
                        Text("中转站、官方 API 与模型切换", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SettingsRow("AI 与模型", "配置提供商、模型和连接", Icons.Rounded.Tune, onAiSetup) }
        item { SettingsRow("写作 Skills", "写作方法、章纲导演与能力开关", Icons.Rounded.AutoStories, onSkills) }
        item { SettingsRow("后台任务", "生成进度、失败任务和恢复", Icons.Rounded.TaskAlt, onRunCenter) }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
