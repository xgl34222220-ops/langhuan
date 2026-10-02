package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.LanghuanSkeletonV31
import com.xiguli.langhuan.ui.design.enterOnceV31
import com.xiguli.langhuan.ui.design.rememberEnterRegistryV31
import com.xiguli.langhuan.ui.design.springClickV31
import androidx.compose.ui.graphics.graphicsLayer
import com.xiguli.langhuan.ui.design.rememberLanghuanCoverV30
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.PaperReaderThemeV44
import com.xiguli.langhuan.ui.design.PaperPageTitleV44
import com.xiguli.langhuan.ui.design.PaperIconButtonV44
import com.xiguli.langhuan.ui.design.PaperReaderPaletteV44
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LuoShelfScreenV1 {
    HOME, SHELF, BOOKSTORE, CREATE, PROFILE, PROFILE_EDIT, SHELF_MANAGER, NEW_SHELF, SETTINGS, EXPLORE, HISTORY, MEDALS,
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
    onCreateBlank: (String, String) -> Unit = { _, _ -> },
    onExport: (String, com.xiguli.langhuan.data.ExportFormat) -> Unit = { _, _ -> },
    onOnline: () -> Unit = {},
    onCheckUpdate: (String) -> Unit = {},
    onDownloadBook: (String) -> Unit = {},
    onlineContent: @Composable (Boolean) -> Unit = {},
) = PaperReaderThemeV44 {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("qingmo_shelf_v9", 0) }
    val t = LocalLanghuanUiTokens.current
    val editViewModel: BookEditViewModelV5 = viewModel()

    var screen by rememberSaveable { mutableStateOf(LuoShelfScreenV1.SHELF) }
    var manageSources by rememberSaveable { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var blankOpen by remember { mutableStateOf(false) }
    var exportFor by remember { mutableStateOf<ReaderBookUi?>(null) }
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
    val assignments = remember(shelfRevision) { LuoShelfAssignmentsV33.all(prefs) }
    var activeShelf by rememberSaveable { mutableStateOf<String?>(null) }
    var sortKey by rememberSaveable {
        mutableStateOf(prefs.getString("shelf_sort", LuoShelfSortV33.RECENT_READ.key) ?: LuoShelfSortV33.RECENT_READ.key)
    }
    var moveFor by remember { mutableStateOf<ReaderBookUi?>(null) }
    // v3: 批量整理面板。
    var batchOpen by remember { mutableStateOf(false) }
    var pendingShelfDelete by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(activeShelf, customShelves) {
        if (activeShelf != null && activeShelf !in customShelves) activeShelf = null
    }

    editingBookId?.let { id -> state.stories.firstOrNull { it.id == id } }?.let { book ->
        BackHandler {
            editViewModel.clearFeedback()
            editingBookId = null
        }
        BookEditPageV5(book = book, editViewModel = editViewModel) {
            editViewModel.clearFeedback()
            editingBookId = null
        }
        return@PaperReaderThemeV44
    }

    val mainScreens = listOf(LuoShelfScreenV1.SHELF, LuoShelfScreenV1.BOOKSTORE, LuoShelfScreenV1.PROFILE)
    BackHandler(enabled = screen != LuoShelfScreenV1.SHELF) {
        screen = when (screen) {
            in mainScreens -> LuoShelfScreenV1.SHELF
            LuoShelfScreenV1.NEW_SHELF -> LuoShelfScreenV1.SHELF_MANAGER
            else -> LuoShelfScreenV1.PROFILE
        }
    }
    BackHandler(enabled = searchOpen && screen == LuoShelfScreenV1.SHELF) {
        searchOpen = false
        query = ""
    }

    Box(Modifier.fillMaxSize().background(if (screen in mainScreens) PaperReaderPaletteV44.background else t.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState.ordinal >= initialState.ordinal
                        (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (forward) it / 6 else -it / 6 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160)))
                    },
                    label = "luoshuShelfRoute",
                ) { current ->
                    when (current) {
                        LuoShelfScreenV1.HOME -> LuoShelfHomeV1(state.stories, openingBookId, onOpenBook, { screen = LuoShelfScreenV1.SHELF }, onImportLocal, onCreate)
                        LuoShelfScreenV1.SHELF -> {
                            LuoShelfLibraryV1(
                                state, importState, openingBookId, query, searchOpen,
                                onSearchOpen = { searchOpen = it; if (!it) query = "" },
                                onQuery = { query = it }, onAdd = { addOpen = true }, onOpenBook = onOpenBook, onLongPress = { actionsFor = it },
                                shelves = customShelves,
                                assignments = assignments,
                                activeShelf = activeShelf,
                                onShelf = { activeShelf = it },
                                sort = LuoShelfSortV33.of(sortKey),
                                onSort = {
                                    sortKey = it.key
                                    prefs.edit().putString("shelf_sort", it.key).apply()
                                },
                                onBatchOrganize = { batchOpen = true },
                            )
                            // v3: 批量整理面板（多选 / 移动 / 删除 / 6 秒撤销）。
                            LuoShelfBatchOrganizerV50(
                                books = state.stories,
                                shelves = customShelves,
                                shelfPrefs = prefs,
                                visible = batchOpen,
                                onDismiss = { batchOpen = false },
                                onDeleteBooks = { ids ->
                                    ids.forEach(onDeleteBook)
                                    shelfRevision++
                                },
                                onMoveCompleted = { _, _ -> shelfRevision++ },
                            )
                        }
                        LuoShelfScreenV1.CREATE -> LuoShelfCreateV1(onCreate, onImportLocal, onSkills)
                        LuoShelfScreenV1.BOOKSTORE -> onlineContent(manageSources)
                        LuoShelfScreenV1.PROFILE -> ReaderProfileV41(
                            nickname = nickname,
                            bookCount = state.stories.size,
                            onEditProfile = { screen = LuoShelfScreenV1.PROFILE_EDIT },
                            onHistory = { screen = LuoShelfScreenV1.HISTORY },
                            onShelfManager = { screen = LuoShelfScreenV1.SHELF_MANAGER },
                            onSources = { manageSources = true; screen = LuoShelfScreenV1.BOOKSTORE },
                            onAiSetup = onAiSetup,
                            onImport = onImportLocal,
                            onCreate = onCreate,
                            onBlankBook = { blankOpen = true },
                            onRunCenter = onRunCenter,
                            onSkills = onSkills,
                        )
                        LuoShelfScreenV1.PROFILE_EDIT -> LuoProfileEditV1(nickname, { screen = LuoShelfScreenV1.PROFILE }) {
                            nickname = it
                            prefs.edit().putString("nickname", it).apply()
                            screen = LuoShelfScreenV1.PROFILE
                        }
                        LuoShelfScreenV1.SHELF_MANAGER -> LuoShelfManagerV1(
                            state.stories,
                            customShelves,
                            { screen = LuoShelfScreenV1.PROFILE },
                            { screen = LuoShelfScreenV1.NEW_SHELF },
                            { activeShelf = null; screen = LuoShelfScreenV1.SHELF },
                            counts = customShelves.associateWith { name ->
                                assignments.count { (id, shelf) -> shelf == name && state.stories.any { it.id == id } }
                            },
                            onOpenShelf = { name -> activeShelf = name; screen = LuoShelfScreenV1.SHELF },
                        ) { name -> pendingShelfDelete = name }
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
            if (screen in mainScreens) LuoFloatingDockV1(screen) { manageSources = false; screen = it }
        }
    }

    if (addOpen) {
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("添加到书架", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
                LanghuanMenuRow(Icons.Rounded.FolderOpen, "导入本地小说", { addOpen = false; onImportLocal() }, subtitle = "TXT · EPUB · Markdown")
                LanghuanSeparator(Modifier.padding(start = 54.dp))
                LanghuanMenuRow(Icons.Rounded.Search, "去书城找书", { addOpen = false; manageSources = false; screen = LuoShelfScreenV1.BOOKSTORE }, subtitle = "用你导入的书源搜索、下载、追更")
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
                if (book.sourceId.isNotBlank() || remember(book.id) { BookSourceStoreV36.link(context, book.id) != null }) {
                    LanghuanMenuRow(Icons.Rounded.Refresh, "检查更新", { actionsFor = null; onCheckUpdate(book.id) }, subtitle = "仅更新目录，阅读时加载正文")
                }
                if (book.sourceId.isNotBlank()) {
                    LanghuanMenuRow(Icons.Rounded.Download, "离线下载", { actionsFor = null; onDownloadBook(book.id) }, subtitle = "缓存未下载章节，已保存正文不会覆盖")
                }
                LanghuanMenuRow(Icons.Rounded.IosShare, "导出", { actionsFor = null; exportFor = book }, subtitle = "TXT · EPUB · Markdown")
                LanghuanMenuRow(
                    Icons.Rounded.FolderOpen,
                    "移动书架",
                    { actionsFor = null; moveFor = book },
                    subtitle = assignments[book.id]?.let { "当前在「$it」" } ?: "放进一个自定义书架",
                )
                LanghuanMenuRow(Icons.Rounded.DeleteOutline, "删除小说", { actionsFor = null; pendingDelete = book }, subtitle = "删除章节与项目数据")
                Surface(
                    Modifier.fillMaxWidth().padding(top = 10.dp).springClickV31(pressedScale = .98f) { actionsFor = null },
                    shape = RoundedCornerShape(16.dp),
                    color = t.muted,
                ) {
                    Text("取消", Modifier.padding(vertical = 13.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${book.title}》？") }, text = { Text("章节、版本和项目数据会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    LuoShelfAssignmentsV33.forget(prefs, book.id)
                    shelfRevision++
                    onDeleteBook(book.id)
                }) { Text("删除", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }

    moveFor?.let { book ->
        LuoMoveShelfSheetV33(
            book = book,
            shelves = customShelves,
            current = assignments[book.id],
            onDismiss = { moveFor = null },
            onMove = { shelf ->
                LuoShelfAssignmentsV33.assign(prefs, book.id, shelf)
                shelfRevision++
                moveFor = null
            },
            onCreateAndMove = { name ->
                prefs.edit().putStringSet("custom_shelves", (customShelves + name).toSet()).apply()
                LuoShelfAssignmentsV33.assign(prefs, book.id, name)
                shelfRevision++
                moveFor = null
            },
        )
    }

    pendingShelfDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingShelfDelete = null },
            title = { Text("删除书架「$name」？") },
            text = { Text("只删除书架本身，里面的书会回到「全部」，不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingShelfDelete = null
                    LuoShelfAssignmentsV33.removeShelf(prefs, name)
                    prefs.edit().putStringSet("custom_shelves", customShelves.filterNot { it == name }.toSet()).apply()
                    shelfRevision++
                }) { Text("删除", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingShelfDelete = null }) { Text("取消") } },
            containerColor = t.card,
        )
    }

    if (blankOpen) {
        var blankTitle by remember { mutableStateOf("") }
        var blankGenre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { blankOpen = false },
            title = { Text("空白新书") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(blankTitle, { blankTitle = it.take(30) }, label = { Text("书名") }, singleLine = true)
                    androidx.compose.material3.OutlinedTextField(blankGenre, { blankGenre = it.take(16) }, label = { Text("类型（可不填）") }, singleLine = true)
                    Text("建好后直接进入第 1 章编辑，之后随时可以再用 AI。", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { blankOpen = false; onCreateBlank(blankTitle, blankGenre) }) { Text("开始写") }
            },
            dismissButton = { TextButton(onClick = { blankOpen = false }) { Text("取消") } },
            containerColor = t.card,
        )
    }

    exportFor?.let { book ->
        ModalBottomSheet(onDismissRequest = { exportFor = null }, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("导出《${book.title}》", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, maxLines = 1)
                Text("选择格式后再选保存位置", Modifier.padding(top = 4.dp, bottom = 10.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                LanghuanMenuRow(Icons.Rounded.Description, "TXT 纯文本", { exportFor = null; onExport(book.id, com.xiguli.langhuan.data.ExportFormat.TXT) }, subtitle = "任何阅读器都能打开，适合投稿平台")
                LanghuanMenuRow(Icons.Rounded.AutoStories, "EPUB 电子书", { exportFor = null; onExport(book.id, com.xiguli.langhuan.data.ExportFormat.EPUB) }, subtitle = "带目录，适合电子书阅读器")
                LanghuanMenuRow(Icons.Rounded.Code, "Markdown", { exportFor = null; onExport(book.id, com.xiguli.langhuan.data.ExportFormat.MARKDOWN) }, subtitle = "保留标题层级，方便再编辑")
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}

@Composable
private fun LuoFloatingDockV1(screen: LuoShelfScreenV1, onSelect: (LuoShelfScreenV1) -> Unit) = PaperReaderThemeV44 {
    val t = LocalLanghuanUiTokens.current
    val targets = listOf(LuoShelfScreenV1.SHELF, LuoShelfScreenV1.BOOKSTORE, LuoShelfScreenV1.PROFILE)
    val labels = listOf("书架", "书城", "我的")
    val icons = listOf(Icons.Rounded.Book, Icons.Rounded.Explore, Icons.Outlined.Person)
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 12.dp),
        shape = RoundedCornerShape(20.dp), color = t.card, shadowElevation = 2.dp,
        border = BorderStroke(.7.dp, t.border),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp)) {
            val gap = 0.dp
            val slot = (maxWidth - gap * (targets.size - 1)) / targets.size
            val indicatorWidth = minOf(64.dp, slot)
            val activeIndex = targets.indexOf(screen).coerceAtLeast(0)
            // A small selection tile moves between full-size touch targets.
            val pillX by animateDpAsState((slot + gap) * activeIndex, LanghuanMotionV31.settle(), label = "dockPill")
            Box(
                Modifier
                    .offset(x = pillX + (slot - indicatorWidth) / 2, y = 5.dp)
                    .width(indicatorWidth)
                    .height(54.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(t.accent),
            )
            Row(Modifier.fillMaxSize().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                targets.forEachIndexed { index, target ->
                    val active = target == screen
                    val fg by animateColorAsState(if (active) t.accentForeground else t.mutedForeground, tween(220), label = "dockFg")
                    val iconScale by animateFloatAsState(if (active) 1.03f else 1f, spring(dampingRatio = .7f, stiffness = Spring.StiffnessMediumLow), label = "dockScale")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(15.dp))
                            .selectable(selected = active, role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (!active) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(target)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icons[index], null, Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = fg)
                        Text(labels[index], Modifier.padding(top = 3.dp), color = fg, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                    }
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
    val enter = rememberEnterRegistryV31()
    val greeting = remember { luoGreetingV31() }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Column(Modifier.enterOnceV31(enter, "home-title", 0)) {
            Text(greeting, color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text("琅嬛", Modifier.padding(top = 4.dp, bottom = 24.dp), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        }
        Text("继续阅读", Modifier.enterOnceV31(enter, "home-continue-title", 1), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (recent != null) {
            LanghuanCard(Modifier.fillMaxWidth().enterOnceV31(enter, "home-continue", 1), depth = 2, onClick = { onOpenBook(recent.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LuoBookCoverV1(recent, Modifier.width(88.dp).aspectRatio(.70f), openingBookId == recent.id)
                    Column(Modifier.padding(start = 18.dp).weight(1f)) {
                        Text(recent.title, color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val progress = remember(recent.id) { ReaderProgressStoreV11.load(context, recent.id, recent.currentChapter.coerceAtLeast(1)) }
                        val bookStats = remember(recent.id, recent.updatedAt) {
                            val progressPrefs = context.getSharedPreferences("reader_progress_v2", 0)
                            progressPrefs.getInt("total_${recent.id}", 0) to progressPrefs.getInt("index_${recent.id}", -1)
                        }
                        val total = bookStats.first
                        val index = if (bookStats.second >= 0) bookStats.second else progress.chapterNumber - 1
                        val bookFraction = if (total > 0) ((index + progress.positionFraction) / total).coerceIn(0f, 1f) else progress.positionFraction
                        Text(
                            if (total > 0) "第 ${index + 1} 章 · 已读 ${(bookFraction * 100).toInt()}%" else "第 ${progress.chapterNumber} 章",
                            Modifier.padding(top = 7.dp),
                            color = t.mutedForeground,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        LuoProgressBarV31(bookFraction, Modifier.padding(top = 12.dp).fillMaxWidth())
                        Text("继续阅读 →", Modifier.padding(top = 14.dp), color = t.primary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            LanghuanCard(Modifier.fillMaxWidth().enterOnceV31(enter, "home-empty", 1), onClick = onImport) { Text("导入一本小说，开始阅读 →", color = t.primary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().enterOnceV31(enter, "home-quick", 2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LuoQuickCardV1(Icons.Rounded.FolderOpen, "导入小说", "TXT · EPUB", Modifier.weight(1f), onImport)
            LuoQuickCardV1(Icons.Rounded.AutoAwesome, "开始创作", "从一个想法开始", Modifier.weight(1f), onCreate)
        }
        Row(Modifier.fillMaxWidth().enterOnceV31(enter, "home-shelf", 3).springClickV31(pressedScale = .98f, onClick = onLibrary).padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("我的书架", Modifier.weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text("${books.size} 本 →", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LuoQuickCardV1(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    LanghuanCard(modifier, depth = 0, onClick = onClick) {
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
    shelves: List<String> = emptyList(),
    assignments: Map<String, String> = emptyMap(),
    activeShelf: String? = null,
    onShelf: (String?) -> Unit = {},
    sort: LuoShelfSortV33 = LuoShelfSortV33.RECENT_READ,
    onSort: (LuoShelfSortV33) -> Unit = {},
    onBatchOrganize: () -> Unit = {},
) = PaperReaderThemeV44 {
    val t = LocalLanghuanUiTokens.current
    val context = LocalContext.current
    val readPrefs = remember(context) { context.getSharedPreferences("reader_progress_v1", 0) }
    val books = remember(state.stories, query, activeShelf, assignments, sort) {
        luoSortBooksV33(state.stories, sort) { readPrefs.getLong("last_${it.id}", 0L) }
            .filter { activeShelf == null || assignments[it.id] == activeShelf }
            .filter { query.isBlank() || it.title.contains(query, true) || it.genre.contains(query, true) }
    }
    val shelfCounts = remember(state.stories, assignments, shelves) {
        buildMap<String?, Int> {
            put(null, state.stories.size)
            shelves.forEach { name -> put(name, state.stories.count { assignments[it.id] == name }) }
        }
    }
    Box(Modifier.fillMaxSize().background(t.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("琅嬛", Modifier.padding(start = 20.dp, top = 17.dp, bottom = 14.dp), color = t.foreground, fontFamily = FontFamily.Serif, fontSize = 17.sp, letterSpacing = 2.sp)
            PaperPageTitleV44("书架", Modifier.padding(start = 20.dp, end = 16.dp, bottom = 9.dp)) {
                PaperIconButtonV44(Icons.Rounded.SwapVert, "排序：${sort.label}", { onSort(sort.next()) })
                PaperIconButtonV44(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, if (searchOpen) "关闭搜索" else "搜索书架", { onSearchOpen(!searchOpen) }, selected = searchOpen)
                // v3: 批量整理（多选 / 移动 / 删除 / 撤销）。
                PaperIconButtonV44(Icons.Rounded.PlaylistAddCheck, "批量整理", onBatchOrganize)
                PaperIconButtonV44(Icons.Rounded.Add, "添加书籍", onAdd)
            }
            AnimatedVisibility(searchOpen, enter = expandVertically(LanghuanMotionV31.settle()) + fadeIn(tween(160)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), placeholder = { Text("搜索书名或类型") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(18.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = sort,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 }) togetherWith
                            (fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it / 2 })
                    },
                    label = "shelfSortLabel",
                ) { current ->
                    Text("按${current.label}排列 · ${books.size} 本", color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
            AnimatedVisibility(shelves.isNotEmpty()) {
                LuoShelfTabsV33(shelves, shelfCounts, activeShelf, onShelf, Modifier.padding(bottom = 8.dp))
            }
            when {
                !state.libraryLoaded -> LuoShelfSkeletonV31()
                books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val emptyEnter = rememberEnterRegistryV31()
                    Column(
                        Modifier.padding(horizontal = 40.dp).padding(bottom = 60.dp).enterOnceV31(emptyEnter, "empty-$query".take(40), 0),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LuoEmptyStackV31(searching = query.isNotBlank())
                        Text(
                            when {
                                query.isNotBlank() -> "没有匹配的作品"
                                activeShelf != null -> "「$activeShelf」还是空的"
                                else -> "把喜欢的故事放进琅嬛"
                            },
                            Modifier.padding(top = 10.dp),
                            color = t.foreground,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                query.isNotBlank() -> "换个关键词试试，书名和类型都能搜"
                                activeShelf != null -> "长按任意一本书，选「移动书架」放进来"
                                else -> "导入本地书籍，或到书城寻找下一段故事"
                            },
                            color = t.mutedForeground,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        if (query.isBlank() && activeShelf == null) {
                            Surface(
                                Modifier.padding(top = 8.dp).springClickV31(onClick = onAdd),
                                shape = RoundedCornerShape(999.dp),
                                color = t.primary,
                            ) {
                                Text("添加书籍", Modifier.padding(horizontal = 22.dp, vertical = 12.dp), color = t.primaryForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                else -> {
                    val progressPrefs = remember(context) { context.getSharedPreferences("reader_progress_v2", 0) }
                    val enter = rememberEnterRegistryV31()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        gridItemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                            val chapter = remember(book.id, book.updatedAt) { progressPrefs.getInt("chapter_${book.id}", 0) }
                            val readFraction = remember(book.id, book.updatedAt) {
                                val total = progressPrefs.getInt("total_${book.id}", 0)
                                val chapterIndex = progressPrefs.getInt("index_${book.id}", -1)
                                if (total > 0 && chapterIndex >= 0) {
                                    ((chapterIndex + progressPrefs.getFloat("fraction_${book.id}", 0f)) / total).coerceIn(0f, 1f)
                                } else -1f
                            }
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                if (pressed) .94f else 1f,
                                spring(stiffness = Spring.StiffnessMedium),
                                label = "bookOpenPress",
                            )
                            Column(
                                Modifier
                                    .animateItem()
                                    .enterOnceV31(enter, book.id, index)
                                    .fillMaxWidth()
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .combinedClickable(
                                        interactionSource = interaction,
                                        indication = null,
                                        enabled = true,
                                        onLongClick = { onLongPress(book) },
                                        onClick = { onOpenBook(book.id) },
                                    ),
                            ) {
                                Box {
                                    LuoBookCoverV1(book, Modifier.fillMaxWidth().aspectRatio(.72f), openingBookId == book.id)
                                    if (readFraction > 0f) {
                                        Box(
                                            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                                                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                                .background(Color.Black.copy(alpha = .18f)),
                                        ) {
                                            Box(
                                                Modifier.fillMaxHeight().fillMaxWidth(readFraction.coerceAtLeast(.03f)).background(t.primary),
                                            )
                                        }
                                    }
                                }
                                Text(book.title, Modifier.fillMaxWidth().padding(top = 10.dp), color = t.foreground, fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        readFraction >= .995f -> "已读完"
                                        readFraction > 0f -> "第 $chapter 章 · ${(readFraction * 100).toInt()}%"
                                        chapter > 0 -> "读到第 $chapter 章"
                                        else -> book.genre.ifBlank { "未读" }
                                    },
                                    Modifier.padding(top = 3.dp),
                                    color = t.mutedForeground,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = importState.busy,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(LanghuanMotionV31.settle()) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
        ) {
            Surface(Modifier.padding(18.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = t.card, shadowElevation = 3.dp) {
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
        LanghuanCard(Modifier.fillMaxWidth(), depth = 2, onClick = onCreate) {
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
        LanghuanCard(Modifier.fillMaxWidth(), depth = 1, onClick = onEditProfile) {
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
private fun LuoShelfManagerV1(
    books: List<ReaderBookUi>,
    customShelves: List<String>,
    onBack: () -> Unit,
    onNewShelf: () -> Unit,
    onOpenReadingShelf: () -> Unit,
    counts: Map<String, Int> = emptyMap(),
    onOpenShelf: (String) -> Unit = {},
    onDeleteShelf: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LanghuanIconButton(Icons.Rounded.ArrowBack, "返回", onBack)
            Text("书架管理", Modifier.padding(start = 8.dp).weight(1f), color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            LanghuanIconButton(Icons.Rounded.Add, "新建书架", onNewShelf)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LanghuanCard(Modifier.fillMaxWidth(), depth = 0, onClick = onOpenReadingShelf) {
                    Text("正在阅读 (${books.size})", color = t.foreground, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { books.take(5).forEach { LuoBookCoverV1(it, Modifier.width(42.dp).aspectRatio(.70f)) } }
                }
            }
            items(customShelves, key = { it }) { name ->
                LanghuanCard(Modifier.fillMaxWidth().animateItem(), depth = 0, contentPadding = 8.dp) {
                    LanghuanMenuRow(Icons.Rounded.Book, name, { onOpenShelf(name) }, subtitle = "${counts[name] ?: 0} 本 · 点按查看") {
                        LanghuanIconButton(Icons.Rounded.DeleteOutline, "删除书架", { onDeleteShelf(name) })
                    }
                }
            }
            if (customShelves.isEmpty()) item {
                Text(
                    "还没有自定义书架，右上角 + 可以创建；也可以在书架里长按一本书，选「移动书架」时直接新建。",
                    color = t.mutedForeground,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
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
    val exploreEnter = rememberEnterRegistryV31()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("探索", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LuoQuickCardV1(Icons.Rounded.AutoAwesome, "AI 创建小说", "从想法开始", Modifier.weight(1f), onCreate)
            LuoQuickCardV1(Icons.Rounded.FolderOpen, "导入本地小说", "TXT · EPUB", Modifier.weight(1f), onImport)
        }
        Text("最近作品", Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = t.mutedForeground, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            itemsIndexed(books.sortedByDescending { it.updatedAt }.take(20), key = { _, it -> it.id }) { index, book ->
                Row(Modifier.fillMaxWidth().enterOnceV31(exploreEnter, book.id, index + 2).springClickV31(pressedScale = .98f) { onOpenBook(book.id) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val historyEnter = rememberEnterRegistryV31()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("阅读记录", onBack)
        if (books.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有阅读记录", color = t.mutedForeground) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            itemsIndexed(books, key = { _, it -> it.id }) { index, book ->
                Row(Modifier.fillMaxWidth().enterOnceV31(historyEnter, book.id, index).springClickV31(pressedScale = .98f) { onOpenBook(book.id) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val medalEnter = rememberEnterRegistryV31()
    val medals = listOf(
        Triple("初入琅嬛", "书架中拥有至少 1 本作品", books.isNotEmpty()),
        Triple("藏书小成", "书架中拥有至少 3 本作品", books.size >= 3),
        Triple("今日有约", "完成今日签到", checkedIn),
        Triple("创作旅人", "书架中拥有至少 5 本作品", books.size >= 5),
    )
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LuoPageHeaderV1("勋章", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(medals) { index, (title, desc, unlocked) ->
                LanghuanCard(Modifier.fillMaxWidth().enterOnceV31(medalEnter, title, index), depth = 0, contentPadding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Unlocked medals glint once when they appear.
                        val glint = remember { Animatable(if (unlocked) 0f else 1f) }
                        LaunchedEffect(unlocked) {
                            if (unlocked) {
                                delay(160L + index * 60L)
                                glint.animateTo(1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessLow))
                            }
                        }
                        Surface(
                            Modifier.size(42.dp).graphicsLayer {
                                val g = glint.value
                                scaleX = .7f + .3f * g; scaleY = .7f + .3f * g
                                rotationZ = if (unlocked) (1f - g) * -25f else 0f
                            },
                            shape = CircleShape,
                            color = if (unlocked) t.accent else t.muted,
                        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.WorkspacePremium, null, tint = if (unlocked) t.accentForeground else t.mutedForeground) } }
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
    val bitmap = rememberLanghuanCoverV30(book.coverPath, 360)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .22f))
            .clip(shape)
            .background(t.muted),
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(220), label = "cover") { image ->
            if (image != null) {
                Image(image, book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                val seed = (book.title.hashCode() and 0x7fffffff) % LUO_COVER_TONES_V30.size
                val (top, bottom) = LUO_COVER_TONES_V30[seed]
                Column(
                    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))).padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(Modifier.size(width = 18.dp, height = 2.dp).background(Color.White.copy(alpha = .55f)))
                    Text(book.title, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text(book.genre.ifBlank { "小说" }, color = Color.White.copy(.72f), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        // Spine highlight + hairline edge make flat covers read as books.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = .10f), .05f to Color.White.copy(alpha = .06f), .1f to Color.Transparent)))
        if (busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(.28f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) }
    }
}

private val LUO_COVER_TONES_V30 = listOf(
    Color(0xFF3B4A5E) to Color(0xFF1E2733),
    Color(0xFF5B4636) to Color(0xFF2E231B),
    Color(0xFF3F5A4C) to Color(0xFF1E2E26),
    Color(0xFF5A3F52) to Color(0xFF2B1E28),
    Color(0xFF4A4F6B) to Color(0xFF23263A),
    Color(0xFF6B5A3A) to Color(0xFF362C1A),
)


private fun luoGreetingV31(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..10 -> "早上好，留一点时间给故事"
    in 11..13 -> "午间小憩，读一章再出发"
    in 14..17 -> "下午好，留一点时间给故事"
    in 18..22 -> "晚上好，今晚读点什么"
    else -> "夜深了，读完这一章就睡吧"
}

@Composable
private fun LuoProgressBarV31(fraction: Float, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    val animated = remember { Animatable(0f) }
    LaunchedEffect(fraction) { animated.animateTo(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing)) }
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated.value.coerceAtLeast(.02f)).clip(RoundedCornerShape(2.dp)).background(t.primary))
    }
}

/** Placeholder grid with the exact geometry of the real one, so loading never jumps. */
@Composable
private fun LuoShelfSkeletonV31() {
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                    Column(Modifier.weight(1f).graphicsLayer { alpha = 1f - row * .22f }) {
                        LanghuanSkeletonV31(Modifier.fillMaxWidth().aspectRatio(.72f), RoundedCornerShape(6.dp))
                        LanghuanSkeletonV31(Modifier.padding(top = 9.dp).fillMaxWidth(.8f).height(12.dp), RoundedCornerShape(4.dp))
                        LanghuanSkeletonV31(Modifier.padding(top = 6.dp).fillMaxWidth(.5f).height(9.dp), RoundedCornerShape(4.dp))
                    }
                }
            }
        }
    }
}

/** Three fanned book spines; they fan out a little when shown. */
@Composable
private fun LuoEmptyStackV31(searching: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val fan = remember { Animatable(0f) }
    LaunchedEffect(searching) {
        fan.snapTo(0f)
        fan.animateTo(1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessLow))
    }
    Box(Modifier.size(width = 120.dp, height = 96.dp), contentAlignment = Alignment.BottomCenter) {
        val tones = listOf(t.accent, t.muted, t.card)
        listOf(-1, 1, 0).forEach { side ->
            Box(
                Modifier
                    .size(width = 52.dp, height = 74.dp)
                    .graphicsLayer {
                        rotationZ = side * 12f * fan.value
                        translationX = side * 26.dp.toPx() * fan.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f)
                    }
                    .shadow(if (side == 0) 4.dp else 1.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(tones[side + 1]),
                contentAlignment = Alignment.Center,
            ) {
                if (side == 0) Icon(if (searching) Icons.Rounded.Search else Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp), tint = t.primary)
            }
        }
    }
}
