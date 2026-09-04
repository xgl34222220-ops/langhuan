package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class QingmoShelfScreenV9 {
    SHELF,
    PROFILE,
    PROFILE_EDIT,
    SHELF_MANAGER,
    NEW_SHELF,
    SETTINGS,
    EXPLORE,
    HISTORY,
    MEDALS,
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShelfQingmoFunctionalV9(
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

    var screen by rememberSaveable { mutableStateOf(QingmoShelfScreenV9.SHELF) }
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

    val editingBook = editingBookId?.let { id -> state.stories.firstOrNull { it.id == id } }
    if (editingBook != null) {
        BookEditPageV5(
            book = editingBook,
            editViewModel = editViewModel,
            onClose = { editViewModel.clearFeedback(); editingBookId = null },
        )
        return
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (slideInHorizontally(tween(190, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(120))) togetherWith
                (slideOutHorizontally(tween(170, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(100)))
        },
        label = "qingmoShelfRouteV9",
    ) { current ->
        when (current) {
            QingmoShelfScreenV9.SHELF -> QingmoShelfHomeV9(
                state = state,
                importState = importState,
                openingBookId = openingBookId,
                query = query,
                searchOpen = searchOpen,
                onSearchOpen = { searchOpen = it; if (!it) query = "" },
                onQuery = { query = it },
                onAdd = { addOpen = true },
                onProfile = { screen = QingmoShelfScreenV9.PROFILE },
                onOpenBook = onOpenBook,
                onLongPress = { actionsFor = it },
            )

            QingmoShelfScreenV9.PROFILE -> QingmoProfileV9(
                nickname = nickname,
                checkedIn = checkedIn,
                syncEnabled = syncEnabled,
                onBack = { screen = QingmoShelfScreenV9.SHELF },
                onEditProfile = { screen = QingmoShelfScreenV9.PROFILE_EDIT },
                onCheckIn = {
                    checkedIn = true
                    prefs.edit().putString("checkin_date", today).apply()
                },
                onExplore = { screen = QingmoShelfScreenV9.EXPLORE },
                onHistory = { screen = QingmoShelfScreenV9.HISTORY },
                onMedals = { screen = QingmoShelfScreenV9.MEDALS },
                onShelfManager = { screen = QingmoShelfScreenV9.SHELF_MANAGER },
                onSyncChanged = {
                    syncEnabled = it
                    prefs.edit().putBoolean("sync_enabled", it).apply()
                },
                onSettings = { screen = QingmoShelfScreenV9.SETTINGS },
            )

            QingmoShelfScreenV9.PROFILE_EDIT -> QingmoProfileEditV9(
                initial = nickname,
                onBack = { screen = QingmoShelfScreenV9.PROFILE },
                onSave = {
                    nickname = it
                    prefs.edit().putString("nickname", it).apply()
                    screen = QingmoShelfScreenV9.PROFILE
                },
            )

            QingmoShelfScreenV9.SHELF_MANAGER -> QingmoShelfManagerV9(
                books = state.stories,
                customShelves = customShelves,
                onBack = { screen = QingmoShelfScreenV9.PROFILE },
                onNewShelf = { screen = QingmoShelfScreenV9.NEW_SHELF },
                onOpenReadingShelf = { screen = QingmoShelfScreenV9.SHELF },
                onDeleteShelf = { name ->
                    val next = customShelves.filterNot { it == name }.toSet()
                    prefs.edit().putStringSet("custom_shelves", next).apply()
                    shelfRevision++
                },
            )

            QingmoShelfScreenV9.NEW_SHELF -> QingmoNewShelfV9(
                onBack = { screen = QingmoShelfScreenV9.SHELF_MANAGER },
                onSave = { name ->
                    val next = (customShelves + name).toSet()
                    prefs.edit().putStringSet("custom_shelves", next).apply()
                    shelfRevision++
                    screen = QingmoShelfScreenV9.SHELF_MANAGER
                },
            )

            QingmoShelfScreenV9.SETTINGS -> QingmoToolsV9(
                onBack = { screen = QingmoShelfScreenV9.PROFILE },
                onAi = onAiSetup,
                onRun = onRunCenter,
                onSkills = onSkills,
            )

            QingmoShelfScreenV9.EXPLORE -> QingmoExploreV9(
                books = state.stories,
                onBack = { screen = QingmoShelfScreenV9.PROFILE },
                onCreate = onCreate,
                onImport = onImportLocal,
                onOpenBook = onOpenBook,
            )

            QingmoShelfScreenV9.HISTORY -> QingmoHistoryV9(
                books = state.stories.sortedByDescending { it.updatedAt },
                onBack = { screen = QingmoShelfScreenV9.PROFILE },
                onOpenBook = onOpenBook,
            )

            QingmoShelfScreenV9.MEDALS -> QingmoMedalsV9(
                books = state.stories,
                checkedIn = checkedIn,
                onBack = { screen = QingmoShelfScreenV9.PROFILE },
            )
        }
    }

    if (addOpen) {
        ModalBottomSheet(
            onDismissRequest = { addOpen = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Text("添加作品", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF17191D))
                QingmoActionV9(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB · Markdown") {
                    addOpen = false
                    onImportLocal()
                }
                HorizontalDivider(color = Color(0xFFECEDEF), modifier = Modifier.padding(start = 42.dp))
                QingmoActionV9(Icons.Rounded.AutoAwesome, "AI 创建小说", "通过对话创建新作品") {
                    addOpen = false
                    onCreate()
                }
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }

    actionsFor?.let { book ->
        ModalBottomSheet(
            onDismissRequest = { actionsFor = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    QingmoMiniCoverV9(book, Modifier.width(46.dp).aspectRatio(.71f))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(book.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF17191D), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(book.genre.ifBlank { "小说" }, fontSize = 12.sp, color = Color(0xFF8B8E95))
                    }
                }
                QingmoActionV9(Icons.Rounded.Edit, "编辑书籍", "修改书名、类型、简介和封面") {
                    actionsFor = null
                    editingBookId = book.id
                }
                QingmoActionV9(Icons.Rounded.Book, "继续阅读", "回到上次阅读位置") {
                    actionsFor = null
                    onOpenBook(book.id)
                }
                QingmoActionV9(Icons.Rounded.TheaterComedy, "进入故事", "进入互动故事模式") {
                    actionsFor = null
                    onOpenTavern(book.id)
                }
                QingmoActionV9(Icons.Rounded.DeleteOutline, "删除小说", "删除章节与项目数据", destructive = true) {
                    actionsFor = null
                    pendingDelete = book
                }
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") },
            text = { Text("章节、版本和项目数据会一起删除。") },
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
private fun QingmoShelfHomeV9(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    openingBookId: String?,
    query: String,
    searchOpen: Boolean,
    onSearchOpen: (Boolean) -> Unit,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    onProfile: () -> Unit,
    onOpenBook: (String) -> Unit,
    onLongPress: (ReaderBookUi) -> Unit,
) {
    val ink = Color(0xFF17191D)
    val secondary = Color(0xFF8A8D93)
    val books = remember(state.stories, query) {
        state.stories.sortedByDescending { it.updatedAt }.filter {
            query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("正在阅读", Modifier.weight(1f), color = ink, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onSearchOpen(!searchOpen) }, modifier = Modifier.size(42.dp)) {
                    Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, "搜索", Modifier.size(23.dp), tint = ink)
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Rounded.Add, "添加", Modifier.size(29.dp), tint = ink)
                }
                IconButton(onClick = onProfile, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Person, "个人中心", Modifier.size(27.dp), tint = ink)
                }
            }

            AnimatedVisibility(visible = searchOpen, enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 4.dp),
                    placeholder = { Text("搜索书名或类型") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                )
            }

            when {
                !state.libraryLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "还没有作品" else "没有匹配的作品", color = secondary, fontSize = 14.sp)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                ) {
                    gridItems(books, key = { it.id }) { book ->
                        Column(
                            Modifier.fillMaxWidth().combinedClickable(
                                enabled = openingBookId == null,
                                onClick = { onOpenBook(book.id) },
                                onLongClick = { onLongPress(book) },
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            QingmoMiniCoverV9(book, Modifier.fillMaxWidth().aspectRatio(.71f), openingBookId == book.id)
                            Text(
                                book.title,
                                Modifier.fillMaxWidth().padding(top = 8.dp),
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
                }
            }
        }

        if (importState.busy) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp).fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "正在导入 ${importState.currentFileName.orEmpty()}",
                        Modifier.padding(start = 10.dp),
                        color = ink,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun QingmoProfileV9(
    nickname: String,
    checkedIn: Boolean,
    syncEnabled: Boolean,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onCheckIn: () -> Unit,
    onExplore: () -> Unit,
    onHistory: () -> Unit,
    onMedals: () -> Unit,
    onShelfManager: () -> Unit,
    onSyncChanged: (Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    val ink = Color(0xFF17191D)
    val secondary = Color(0xFF92959A)
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Icon(Icons.Rounded.ArrowBack, "返回", tint = ink)
            }
            Surface(onClick = onEditProfile, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(46.dp), shape = CircleShape, color = Color(0xFFF1F2F3)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = secondary) }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(nickname, fontSize = 16.sp, color = ink, fontWeight = FontWeight.Medium)
                        Text("编辑个人资料", fontSize = 11.sp, color = secondary)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFFC5C6C9))
                }
            }
            Spacer(Modifier.height(8.dp))
            QingmoProfileRowV9(Icons.Rounded.TaskAlt, "签到", if (checkedIn) "今日已签到" else "点击签到") {
                if (!checkedIn) onCheckIn()
            }
            QingmoProfileRowV9(Icons.Rounded.Explore, "探索", "发现作品与创作入口", onExplore)
            QingmoProfileRowV9(Icons.Rounded.History, "阅历", "查看最近阅读记录", onHistory)
            QingmoProfileRowV9(Icons.Rounded.WorkspacePremium, "勋章", "查看本地阅读成就", onMedals)
            HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = Color(0xFFF0F0F1))
            QingmoProfileRowV9(Icons.Rounded.Book, "书架", "管理书架", onShelfManager)
            QingmoProfileRowV9(Icons.Rounded.History, "读过", "按最近阅读排序", onHistory)
            QingmoProfileRowV9(
                icon = Icons.Rounded.CloudSync,
                title = "同步",
                subtitle = if (syncEnabled) "本地同步状态已开启" else "本地同步状态已关闭",
                trailing = {
                    Switch(checked = syncEnabled, onCheckedChange = onSyncChanged)
                },
                onClick = { onSyncChanged(!syncEnabled) },
            )
            QingmoProfileRowV9(Icons.Rounded.Settings, "设置", "AI、Skill 与运行中心", onSettings)
        }
    }
}

@Composable
private fun QingmoProfileEditV9(initial: String, onBack: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(initial) { mutableStateOf(initial) }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("个人资料", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(48.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(24) },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                label = { Text("昵称") },
                singleLine = true,
            )
            TextButton(
                onClick = { onSave(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) { Text("保存") }
        }
    }
}

@Composable
private fun QingmoShelfManagerV9(
    books: List<ReaderBookUi>,
    customShelves: List<String>,
    onBack: () -> Unit,
    onNewShelf: () -> Unit,
    onOpenReadingShelf: () -> Unit,
    onDeleteShelf: (String) -> Unit,
) {
    val ink = Color(0xFF17191D)
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("书架列表", Modifier.weight(1f), fontSize = 17.sp, color = ink, fontWeight = FontWeight.Medium)
                IconButton(onClick = onNewShelf) { Icon(Icons.Rounded.Add, "新建书架") }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                item {
                    Surface(onClick = onOpenReadingShelf, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text("正在阅读 (${books.size})", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                books.take(5).forEach { book -> QingmoMiniCoverV9(book, Modifier.width(48.dp).aspectRatio(.71f)) }
                            }
                            Text("点击返回主书架重新选择", Modifier.padding(top = 12.dp), color = Color(0xFF92959A), fontSize = 11.sp)
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F1))
                }
                items(customShelves, key = { it }) { name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Book, null, Modifier.size(18.dp), tint = Color(0xFF60636A))
                        Text(name, Modifier.padding(start = 12.dp).weight(1f), fontSize = 14.sp, color = ink)
                        IconButton(onClick = { onDeleteShelf(name) }) {
                            Icon(Icons.Rounded.DeleteOutline, "删除书架", tint = Color(0xFF9A9DA2))
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F1))
                }
                if (customShelves.isEmpty()) {
                    item {
                        Text("还没有自定义书架，右上角 + 可以创建。", Modifier.padding(top = 24.dp), color = Color(0xFF92959A), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QingmoNewShelfV9(onBack: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var convenient by rememberSaveable { mutableStateOf(true) }
    var newest by rememberSaveable { mutableStateOf(true) }
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
                Text("新建书架", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(48.dp))
            }
            OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth().padding(top = 18.dp), label = { Text("书架名称") }, singleLine = true)
            QingmoToggleRowV9("布局", "网格", "列表", grid) { grid = it }
            QingmoToggleRowV9("便捷", "开启", "关闭", convenient) { convenient = it }
            QingmoToggleRowV9("排序", "时间", "其他", newest) { newest = it }
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) { Text("保存") }
        }
    }
}

@Composable
private fun QingmoExploreV9(
    books: List<ReaderBookUi>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            QingmoPageHeaderV9("探索", onBack)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = onCreate, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = Color(0xFFF4F6FA)) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF347DEC))
                        Text("AI 创建小说", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(onClick = onImport, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = Color(0xFFF4F6FA)) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = Color(0xFF347DEC))
                        Text("导入本地小说", Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Text("最近作品", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontSize = 13.sp, color = Color(0xFF777A80))
            LazyColumn(Modifier.fillMaxSize()) {
                items(books.sortedByDescending { it.updatedAt }.take(20), key = { it.id }) { book ->
                    Surface(onClick = { onOpenBook(book.id) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            QingmoMiniCoverV9(book, Modifier.width(38.dp).aspectRatio(.71f))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(book.title, fontSize = 14.sp, color = Color(0xFF202226), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(book.genre.ifBlank { "小说" }, fontSize = 11.sp, color = Color(0xFF96999E))
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFFC5C6C9))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QingmoHistoryV9(books: List<ReaderBookUi>, onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            QingmoPageHeaderV9("阅历 / 读过", onBack)
            if (books.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有阅读记录", color = Color(0xFF96999E), fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(books, key = { it.id }) { book ->
                        Surface(onClick = { onOpenBook(book.id) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                QingmoMiniCoverV9(book, Modifier.width(42.dp).aspectRatio(.71f))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(book.title, fontSize = 14.sp, color = Color(0xFF202226), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${book.genre.ifBlank { "小说" }} · 最近打开", fontSize = 11.sp, color = Color(0xFF96999E))
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFFC5C6C9))
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 78.dp), color = Color(0xFFF1F1F2))
                    }
                }
            }
        }
    }
}

@Composable
private fun QingmoMedalsV9(books: List<ReaderBookUi>, checkedIn: Boolean, onBack: () -> Unit) {
    val medals = listOf(
        Triple("初入琅嬛", "书架中拥有至少 1 本作品", books.isNotEmpty()),
        Triple("藏书小成", "书架中拥有至少 3 本作品", books.size >= 3),
        Triple("今日有约", "完成今日签到", checkedIn),
        Triple("创作旅人", "书架中拥有至少 5 本作品", books.size >= 5),
    )
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            QingmoPageHeaderV9("勋章", onBack)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                items(medals) { (title, desc, unlocked) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(42.dp), shape = CircleShape, color = if (unlocked) Color(0xFFEEF4FF) else Color(0xFFF3F4F5)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.WorkspacePremium, null, tint = if (unlocked) Color(0xFF347DEC) else Color(0xFFB6B8BC))
                            }
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(title, fontSize = 14.sp, color = Color(0xFF202226), fontWeight = FontWeight.Medium)
                            Text(desc, fontSize = 11.sp, color = Color(0xFF96999E))
                        }
                        Text(if (unlocked) "已解锁" else "未解锁", fontSize = 11.sp, color = if (unlocked) Color(0xFF347DEC) else Color(0xFFB0B2B7))
                    }
                }
            }
        }
    }
}

@Composable
private fun QingmoToolsV9(onBack: () -> Unit, onAi: () -> Unit, onRun: () -> Unit, onSkills: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Color.White) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            QingmoPageHeaderV9("设置", onBack)
            QingmoProfileRowV9(Icons.Rounded.Settings, "AI 服务", "模型、中转站与路由", onAi)
            QingmoProfileRowV9(Icons.Rounded.AutoAwesome, "写作能力", "Skill 管理", onSkills)
            QingmoProfileRowV9(Icons.Rounded.TaskAlt, "运行中心", "查看执行任务", onRun)
        }
    }
}

@Composable
private fun QingmoPageHeaderV9(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") }
        Text(title, Modifier.weight(1f), fontSize = 17.sp, color = Color(0xFF17191D), fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun QingmoProfileRowV9(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) = QingmoProfileRowV9(icon, title, subtitle, trailing = null, onClick = onClick)

@Composable
private fun QingmoProfileRowV9(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color(0xFF5E6167))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontSize = 14.sp, color = Color(0xFF202226))
                subtitle?.let { Text(it, fontSize = 10.sp, color = Color(0xFF9B9DA2)) }
            }
            if (trailing != null) trailing() else Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = Color(0xFFC5C6C9))
        }
    }
}

@Composable
private fun QingmoToggleRowV9(label: String, first: String, second: String, selectedFirst: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = Color(0xFF202226))
        TextButton(onClick = { onSelect(true) }) { Text(first, color = if (selectedFirst) Color(0xFF2F7DEB) else Color(0xFFB0B2B7)) }
        TextButton(onClick = { onSelect(false) }) { Text(second, color = if (!selectedFirst) Color(0xFF2F7DEB) else Color(0xFFB0B2B7)) }
    }
}

@Composable
private fun QingmoMiniCoverV9(book: ReaderBookUi, modifier: Modifier, busy: Boolean = false) {
    val bitmap = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(modifier.clip(RoundedCornerShape(1.dp)).background(Color(0xFFEDEEEF))) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF45515D), Color(0xFF7D8A96)))).padding(7.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(book.genre.ifBlank { "小说" }, color = Color.White.copy(.62f), fontSize = 8.sp)
                Text(book.title, color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
        if (busy) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(.16f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun QingmoActionV9(icon: ImageVector, title: String, subtitle: String, destructive: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (destructive) Color(0xFFD94A4A) else Color(0xFF33363B))
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontSize = 14.sp, color = if (destructive) Color(0xFFD94A4A) else Color(0xFF202226), fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 11.sp, color = Color(0xFF96999E))
            }
        }
    }
}
