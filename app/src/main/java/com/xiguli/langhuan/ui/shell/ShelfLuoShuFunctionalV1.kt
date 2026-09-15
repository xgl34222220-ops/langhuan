package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LuoShelfScreenV1 {
    HOME, SHELF, CREATE, PROFILE, PROFILE_EDIT, SHELF_MANAGER, NEW_SHELF, SETTINGS, EXPLORE, HISTORY, MEDALS,
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfLuoShuFunctionalV1(
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qingmo_shelf_v9", 0) }
    val t = LocalLanghuanUiTokens.current
    val editViewModel: BookEditViewModelV5 = viewModel()

    var screen by rememberSaveable { mutableStateOf(LuoShelfScreenV1.HOME) }
    var addOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingDelete by remember { mutableStateOf<ReaderBookUi?>(null) }
    var editingBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var nickname by rememberSaveable { mutableStateOf(prefs.getString("nickname", "游客") ?: "游客") }
    var syncEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("sync_enabled", false)) }
    var shelfRevision by rememberSaveable { mutableStateOf(0) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var checkedIn by rememberSaveable { mutableStateOf(prefs.getString("checkin_date", "") == today) }
    val customShelves = remember(shelfRevision) {
        prefs.getStringSet("custom_shelves", emptySet())?.toList()?.sorted().orEmpty()
    }

    editingBookId?.let { id -> state.stories.firstOrNull { it.id == id } }?.let { book ->
        BookEditPageV5(book = book, editViewModel = editViewModel) {
            editViewModel.clearFeedback()
            editingBookId = null
        }
        return
    }

    val mainScreens = listOf(LuoShelfScreenV1.HOME, LuoShelfScreenV1.SHELF, LuoShelfScreenV1.CREATE, LuoShelfScreenV1.PROFILE)
    BackHandler(enabled = screen != LuoShelfScreenV1.HOME) {
        screen = if (screen in mainScreens) LuoShelfScreenV1.HOME else LuoShelfScreenV1.PROFILE
    }

    Box(Modifier.fillMaxSize().background(t.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (slideInHorizontally(tween(190, easing = FastOutSlowInEasing)) { it / 7 } + fadeIn(tween(120))) togetherWith
                            (slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -it / 8 } + fadeOut(tween(100)))
                    },
                    label = "luoshuShelfRoute",
                ) { current ->
                    when (current) {
                        LuoShelfScreenV1.HOME -> LuoShelfHomeV1(state.stories, openingBookId, onOpenBook, { screen = LuoShelfScreenV1.SHELF }, onImportLocal, onCreate)
                        LuoShelfScreenV1.SHELF -> LuoShelfLibraryV1(
                            state, importState, openingBookId, query, searchOpen,
                            onSearchOpen = { searchOpen = it; if (!it) query = "" },
                            onQuery = { query = it }, onAdd = { addOpen = true }, onOpenBook = onOpenBook, onLongPress = { actionsFor = it },
                        )
                        LuoShelfScreenV1.CREATE -> LuoShelfCreateV1(onCreate, onImportLocal, onSkills)
                        LuoShelfScreenV1.PROFILE -> LuoShelfProfileV1(
                            nickname = nickname, bookCount = state.stories.size, checkedIn = checkedIn, syncEnabled = syncEnabled,
                            onEditProfile = { screen = LuoShelfScreenV1.PROFILE_EDIT },
                            onCheckIn = { checkedIn = true; prefs.edit().putString("checkin_date", today).apply() },
                            onExplore = { screen = LuoShelfScreenV1.EXPLORE }, onHistory = { screen = LuoShelfScreenV1.HISTORY },
                            onMedals = { screen = LuoShelfScreenV1.MEDALS }, onShelfManager = { screen = LuoShelfScreenV1.SHELF_MANAGER },
                            onSyncChanged = { syncEnabled = it; prefs.edit().putBoolean("sync_enabled", it).apply() },
                            onSettings = { screen = LuoShelfScreenV1.SETTINGS },
                        )
                        LuoShelfScreenV1.PROFILE_EDIT -> LuoProfileEditV1(nickname, { screen = LuoShelfScreenV1.PROFILE }) {
                            nickname = it
                            prefs.edit().putString("nickname", it).apply()
                            screen = LuoShelfScreenV1.PROFILE
                        }
                        LuoShelfScreenV1.SHELF_MANAGER -> LuoShelfManagerV1(
                            state.stories, customShelves, { screen = LuoShelfScreenV1.PROFILE }, { screen = LuoShelfScreenV1.NEW_SHELF },
                            { screen = LuoShelfScreenV1.SHELF },
                        ) { name ->
                            prefs.edit().putStringSet("custom_shelves", customShelves.filterNot { it == name }.toSet()).apply()
                            shelfRevision++
                        }
                        LuoShelfScreenV1.NEW_SHELF -> LuoNewShelfV1({ screen = LuoShelfScreenV1.SHELF_MANAGER }) { name ->
                            prefs.edit().putStringSet("custom_shelves", (customShelves + name).toSet()).apply()
                            shelfRevision++
                            screen = LuoShelfScreenV1.SHELF_MANAGER
                        }
                        LuoShelfScreenV1.SETTINGS -> LuoToolsV1({ screen = LuoShelfScreenV1.PROFILE }, onAiSetup, onRunCenter, onSkills)
                        LuoShelfScreenV1.EXPLORE -> LuoExploreV1(state.stories, { screen = LuoShelfScreenV1.PROFILE }, onCreate, onImportLocal, onOpenBook)
                        LuoShelfScreenV1.HISTORY -> LuoHistoryV1(state.stories.sortedByDescending { it.updatedAt }, { screen = LuoShelfScreenV1.PROFILE }, onOpenBook)
                        LuoShelfScreenV1.MEDALS -> LuoMedalsV1(state.stories, checkedIn) { screen = LuoShelfScreenV1.PROFILE }
                    }
                }
            }
            if (screen in mainScreens) LuoFloatingDockV1(screen) { screen = it }
        }
    }

    if (addOpen) {
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("添加作品", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
                LanghuanMenuRow(Icons.Rounded.FolderOpen, "导入本地小说", { addOpen = false; onImportLocal() }, subtitle = "TXT · EPUB · Markdown")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.AutoAwesome, "AI 创建小说", { addOpen = false; onCreate() }, subtitle = "通过对话逐步创建新作品")
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    LuoBookCoverV1(book, Modifier.width(52.dp).aspectRatio(.70f))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(book.title, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.genre.ifBlank { "小说" }, color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
                LanghuanMenuRow(Icons.Rounded.Edit, "编辑书籍", { actionsFor = null; editingBookId = book.id }, subtitle = "修改书名、类型、简介和封面")
                LanghuanMenuRow(Icons.Rounded.Book, "继续阅读", { actionsFor = null; onOpenBook(book.id) }, subtitle = "回到上次阅读位置")
                LanghuanMenuRow(Icons.Rounded.TheaterComedy, "进入故事", { actionsFor = null; onOpenTavern(book.id) }, subtitle = "进入互动故事模式")
                LanghuanMenuRow(Icons.Rounded.DeleteOutline, "删除小说", { actionsFor = null; pendingDelete = book }, subtitle = "删除章节与项目数据")
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") }, text = { Text("章节、版本和项目数据会一起删除。") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDeleteBook(book.id) }) { Text("删除", color = t.destructive) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun LuoFloatingDockV1(screen: LuoShelfScreenV1, onSelect: (LuoShelfScreenV1) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val targets = listOf(LuoShelfScreenV1.HOME, LuoShelfScreenV1.SHELF, LuoShelfScreenV1.CREATE, LuoShelfScreenV1.PROFILE)
    val labels = listOf("首页", "书架", "创作", "我的")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Book, Icons.Rounded.AutoAwesome, Icons.Outlined.Person)
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp), color = t.card.copy(alpha = .94f), shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            targets.forEachIndexed { index, target ->
                val active = target == screen
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(if (active) t.accent else Color.Transparent)
                        .clickable { onSelect(target) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icons[index], labels[index], Modifier.size(21.dp), tint = if (active) t.accentForeground else t.mutedForeground)
                    Text(labels[index], Modifier.padding(top = 3.dp), color = if (active) t.accentForeground else t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LuoShelfHomeV1(books: List<ReaderBookUi>, openingBookId: String?, onOpenBook: (String) -> Unit, onLibrary: () -> Unit, onImport: () -> Unit, onCreate: () -> Unit) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current
    val progressPrefs = remember { context.getSharedPreferences("reader_progress_v1", 0) }
    val recent = books.maxByOrNull { progressPrefs.getLong("last_${it.id}", it.updatedAt) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("留一点时间，给故事", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Text("琅嬛", Modifier.padding(top = 4.dp, bottom = 24.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text("继续阅读", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (recent != null) {
            LanghuanCard(Modifier.fillMaxWidth().clickable(enabled = openingBookId == null) { onOpenBook(recent.id) }, depth = 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LuoBookCoverV1(recent, Modifier.width(88.dp).aspectRatio(.70f), openingBookId == recent.id)
                    Column(Modifier.padding(start = 18.dp).weight(1f)) {
                        Text(recent.title, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("第 ${ReaderProgressStoreV11.load(context, recent.id, recent.currentChapter.coerceAtLeast(1)).chapterNumber} 章", Modifier.padding(top = 7.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Text("继续阅读 →", Modifier.padding(top = 18.dp), color = t.primary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onImport)) { Text("导入一本小说，开始阅读 →", color = t.primary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LuoQuickCardV1(Icons.Rounded.FolderOpen, "导入小说", "TXT · EPUB", Modifier.weight(1f), onImport)
            LuoQuickCardV1(Icons.Rounded.AutoAwesome, "开始创作", "从一个想法开始", Modifier.weight(1f), onCreate)
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onLibrary).padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("我的书架", Modifier.weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text("${books.size} 本 →", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LuoQuickCardV1(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(modifier.clickable(onClick = onClick), depth = 0) {
        Icon(icon, null, Modifier.size(23.dp), tint = t.strong)
        Text(title, Modifier.padding(top = 14.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(subtitle, Modifier.padding(top = 4.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuoShelfLibraryV1(
    state: LibraryExperienceState, importState: LocalBookImportUiStateV1, openingBookId: String?, query: String, searchOpen: Boolean,
    onSearchOpen: (Boolean) -> Unit, onQuery: (String) -> Unit, onAdd: () -> Unit, onOpenBook: (String) -> Unit, onLongPress: (ReaderBookUi) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val books = remember(state.stories, query) {
        state.stories.sortedByDescending { it.updatedAt }.filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("书架", Modifier.weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
                LanghuanIconButton(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", { onSearchOpen(!searchOpen) })
                LanghuanIconButton(Icons.Rounded.Add, "添加", onAdd)
            }
            AnimatedVisibility(searchOpen, enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
                OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), placeholder = { Text("搜索书名或类型") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp))
            }
            when {
                !state.libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
                books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Book, null, Modifier.size(42.dp), tint = t.mutedForeground)
                        Text(if (query.isBlank()) "把喜欢的故事放进琅嬛" else "没有匹配的作品", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        if (query.isBlank()) TextButton(onClick = onAdd) { Text("导入小说 / 开始创作") }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    gridItems(books, key = { it.id }) { book ->
                        Column(Modifier.fillMaxWidth().combinedClickable(enabled = openingBookId == null, onClick = { onOpenBook(book.id) }, onLongClick = { onLongPress(book) })) {
                            LuoBookCoverV1(book, Modifier.fillMaxWidth().aspectRatio(.70f), openingBookId == book.id)
                            Text(book.title, Modifier.fillMaxWidth().padding(top = 9.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(book.genre.ifBlank { "小说" }, Modifier.padding(top = 3.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (importState.busy) {
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = t.card, shadowElevation = 3.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在导入 ${importState.currentFileName}", Modifier.padding(start = 10.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LuoShelfCreateV1(onCreate: () -> Unit, onImport: () -> Unit, onSkills: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("创作", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text("让脑海里的故事，有一个开始。", Modifier.padding(top = 5.dp, bottom = 22.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onCreate), depth = 2) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(28.dp), tint = t.primary)
            Text("和 AI 一起写一本书", Modifier.padding(top = 18.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text("聊设定、人物与情节，逐步整理成可持续创作的小说蓝图。", Modifier.padding(top = 10.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            Text("开始创作 →", Modifier.padding(top = 22.dp), color = t.primary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(18.dp))
        LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
            Column {
                LanghuanMenuRow(Icons.Rounded.FolderOpen, "导入已有作品", onImport, subtitle = "TXT · EPUB · Markdown")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.TaskAlt, "创作技能", onSkills, subtitle = "管理写作时使用的能力")
            }
        }
    }
}

@Composable
private fun LuoShelfProfileV1(
    nickname: String, bookCount: Int, checkedIn: Boolean, syncEnabled: Boolean,
    onEditProfile: () -> Unit, onCheckIn: () -> Unit, onExplore: () -> Unit, onHistory: () -> Unit, onMedals: () -> Unit,
    onShelfManager: () -> Unit, onSyncChanged: (Boolean) -> Unit, onSettings: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("我的", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))
        LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onEditProfile), depth = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = t.accent) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, Modifier.size(26.dp), tint = t.accentForeground) } }
                Column(Modifier.padding(start = 15.dp).weight(1f)) {
                    Text(nickname, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                    Text("书架中有 $bookCount 本故事", Modifier.padding(top = 3.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Rounded.ChevronRight, "编辑资料", tint = t.mutedForeground)
            }
        }
        Text("阅读与收藏", Modifier.padding(top = 24.dp, bottom = 8.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
            Column {
                LanghuanMenuRow(Icons.Rounded.History, "阅读记录", onHistory, subtitle = "回到最近读过的故事")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.Book, "书架管理", onShelfManager, subtitle = "整理自定义书架")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.WorkspacePremium, "勋章", onMedals, subtitle = if (checkedIn) "今日已签到" else "签到并查看阅读成就")
            }
        }
        Text("服务", Modifier.padding(top = 24.dp, bottom = 8.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
            Column {
                LanghuanMenuRow(Icons.Rounded.Explore, "探索", onExplore, subtitle = "最近作品与创建入口")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.WorkspacePremium, if (checkedIn) "今日已签到" else "今日签到", onCheckIn, subtitle = if (checkedIn) "明天再来" else "记录今天的阅读")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.CloudSync, "同步", {}, subtitle = if (syncEnabled) "已开启" else "仅保存在本机") { Switch(checked = syncEnabled, onCheckedChange = onSyncChanged) }
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.Settings, "设置", onSettings, subtitle = "阅读、AI 与创作能力")
            }
        }
    }
}

@Composable
private fun LuoPageHeaderV1(title: String, onBack: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
        Text(title, Modifier.padding(start = 8.dp).weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun LuoProfileEditV1(initial: String, onBack: () -> Unit, onSave: (String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("个人资料", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(value, { value = it.take(24) }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("昵称") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("保存") }
        }
    }
}

@Composable
private fun LuoShelfManagerV1(books: List<ReaderBookUi>, customShelves: List<String>, onBack: () -> Unit, onNewShelf: () -> Unit, onOpenReadingShelf: () -> Unit, onDeleteShelf: (String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
            Text("书架管理", Modifier.padding(start = 8.dp).weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            LanghuanIconButton(Icons.Rounded.Add, "新建书架", onNewShelf)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LanghuanCard(Modifier.fillMaxWidth().clickable(onClick = onOpenReadingShelf), depth = 0) {
                    Text("正在阅读 (${books.size})", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { books.take(5).forEach { LuoBookCoverV1(it, Modifier.width(42.dp).aspectRatio(.70f)) } }
                }
            }
            items(customShelves, key = { it }) { name ->
                LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 8.dp) {
                    LanghuanMenuRow(Icons.Rounded.Book, name, { }, subtitle = "自定义书架") {
                        LanghuanIconButton(Icons.Rounded.DeleteOutline, "删除书架", { onDeleteShelf(name) })
                    }
                }
            }
            if (customShelves.isEmpty()) item { Text("还没有自定义书架，右上角 + 可以创建。", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun LuoNewShelfV1(onBack: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var convenient by rememberSaveable { mutableStateOf(true) }
    var newest by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("新建书架", onBack)
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("书架名称") }, singleLine = true, shape = RoundedCornerShape(18.dp))
            LuoToggleRowV1("布局", "网格", "列表", grid) { grid = it }
            LuoToggleRowV1("便捷", "开启", "关闭", convenient) { convenient = it }
            LuoToggleRowV1("排序", "时间", "其他", newest) { newest = it }
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("保存") }
        }
    }
}

@Composable
private fun LuoToggleRowV1(label: String, first: String, second: String, selectedFirst: Boolean, onSelect: (Boolean) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onSelect(true) }) { Text(first, color = if (selectedFirst) t.primary else t.mutedForeground) }
        TextButton(onClick = { onSelect(false) }) { Text(second, color = if (!selectedFirst) t.primary else t.mutedForeground) }
    }
}

@Composable
private fun LuoExploreV1(books: List<ReaderBookUi>, onBack: () -> Unit, onCreate: () -> Unit, onImport: () -> Unit, onOpenBook: (String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("探索", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LuoQuickCardV1(Icons.Rounded.AutoAwesome, "AI 创建小说", "从想法开始", Modifier.weight(1f), onCreate)
            LuoQuickCardV1(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB", Modifier.weight(1f), onImport)
        }
        Text("最近作品", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            items(books.sortedByDescending { it.updatedAt }.take(20), key = { it.id }) { book ->
                Row(Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    LuoBookCoverV1(book, Modifier.width(40.dp).aspectRatio(.70f))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(book.title, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(book.genre.ifBlank { "小说" }, color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
                }
                LanghuanSeparator(Modifier.padding(start = 52.dp))
            }
        }
    }
}

@Composable
private fun LuoHistoryV1(books: List<ReaderBookUi>, onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("阅读记录", onBack)
        if (books.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有阅读记录", color = t.mutedForeground) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            items(books, key = { it.id }) { book ->
                Row(Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    LuoBookCoverV1(book, Modifier.width(44.dp).aspectRatio(.70f))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(book.title, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${book.genre.ifBlank { "小说" }} · 最近打开", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = t.mutedForeground)
                }
                LanghuanSeparator(Modifier.padding(start = 56.dp))
            }
        }
    }
}

@Composable
private fun LuoMedalsV1(books: List<ReaderBookUi>, checkedIn: Boolean, onBack: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val medals = listOf(
        Triple("初入琅嬛", "书架中拥有至少 1 本作品", books.isNotEmpty()),
        Triple("藏书小成", "书架中拥有至少 3 本作品", books.size >= 3),
        Triple("今日有约", "完成今日签到", checkedIn),
        Triple("创作旅人", "书架中拥有至少 5 本作品", books.size >= 5),
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("勋章", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(medals) { (title, desc, unlocked) ->
                LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), shape = CircleShape, color = if (unlocked) t.accent else t.muted) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.WorkspacePremium, null, tint = if (unlocked) t.accentForeground else t.mutedForeground) } }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(title, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(desc, color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        Text(if (unlocked) "已解锁" else "未解锁", color = if (unlocked) t.primary else t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LuoToolsV1(onBack: () -> Unit, onAi: () -> Unit, onRun: () -> Unit, onSkills: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("设置", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("创作与服务", Modifier.padding(top = 8.dp, bottom = 8.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LanghuanCard(Modifier.fillMaxWidth(), depth = 0, contentPadding = 6.dp) {
                Column {
                    LanghuanMenuRow(Icons.Rounded.AutoAwesome, "AI 与模型", onAi, subtitle = "管理服务、模型和连接")
                    LanghuanSeparator(Modifier.padding(start = 54.dp))
                    LanghuanMenuRow(Icons.Rounded.TaskAlt, "创作技能", onSkills, subtitle = "选择和管理写作能力")
                    LanghuanSeparator(Modifier.padding(start = 54.dp))
                    LanghuanMenuRow(Icons.Rounded.History, "运行中心", onRun, subtitle = "查看生成进度与任务记录")
                }
            }
            Text("阅读", Modifier.padding(top = 22.dp, bottom = 8.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            LanghuanCard(Modifier.fillMaxWidth(), depth = 0) {
                Text("阅读时轻点屏幕中间，即可调整字号、行距、背景与翻页方式。设置会自动保存。", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun LuoBookCoverV1(book: ReaderBookUi, modifier: Modifier, busy: Boolean = false) {
    val t = LocalLanghuanUiTokens.current
    val bitmap = remember(book.coverPath) { book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.clip(shape).background(t.muted)) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.strong, t.primary))).padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(book.genre.ifBlank { "小说" }, color = Color.White.copy(.68f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            Text(book.title, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(.16f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) }
    }
}
