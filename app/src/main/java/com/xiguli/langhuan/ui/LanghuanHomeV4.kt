package com.xiguli.langhuan.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import android.text.format.DateUtils
import com.xiguli.langhuan.ui.design.LanghuanSkeletonV31
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.pressScaleV31
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.draw.clip
import androidx.compose.material3.LinearProgressIndicator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.epub.EpubReaderEntry
import kotlin.math.roundToInt


/* -------------------------------------------------------------------------- */
/*                                  Constants                                 */
/* -------------------------------------------------------------------------- */

private const val HOME_SHELF_PREFS_V4 = "qingmo_shelf_v9"
private const val HOME_CUSTOM_SHELVES_V4 = "custom_shelves"
private const val HOME_CUSTOM_SHELF_ORDER_V4 = "custom_shelf_order_v50"
private const val HOME_SORT_V4 = "shelf_sort"
private const val HOME_LAYOUT_V4 = "shelf_layout_v4"
private const val HOME_TAB_ALL_V4 = "__all__"
private const val HOME_TAB_WRITING_V4 = "__writing__"
private const val HOME_TAB_FOLLOWING_V4 = "__following__"
private const val HOME_TAB_CUSTOM_PREFIX_V4 = "__custom__:"
private const val HOME_SHELF_SEPARATOR_V4 = ""


/* -------------------------------------------------------------------------- */
/*                                   Model                                    */
/* -------------------------------------------------------------------------- */

private enum class HomeLayoutV4(val key: String) {
    GRID("grid"),
    LIST("list");
    companion object {
        fun fromKey(key: String?): HomeLayoutV4 =
            entries.firstOrNull { it.key == key } ?: LIST
    }
}

private data class HomeShelfTabV4(
    val key: String,
    val label: String,
    val count: Int,
)

private data class HomeContinueReadingV4(
    val book: ReaderBookUi,
    val chapterNumber: Int,
    val chapterTitle: String?,
    val lastReadAt: Long = 0L,
    /** Position inside the current chapter, 0..1. */
    val chapterFraction: Float = 0f,
)


/* -------------------------------------------------------------------------- */
/*                                    Page                                    */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanghuanHomeV4(
    state: LibraryExperienceState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onImportLocal: () -> Unit,
    onDeleteBook: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenTavern: (String) -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
    onOnline: () -> Unit = {},
    onRenameBook: (String, String) -> Unit = { _, _ -> },
    onCancelImport: () -> Unit = {},
) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current

    val shelfPrefs = remember(context) {
        context.getSharedPreferences(HOME_SHELF_PREFS_V4, Context.MODE_PRIVATE)
    }
    val progressPrefs = remember(context) {
        context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var organizeOpen by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableStateOf(HOME_TAB_ALL_V4) }
    var sortKey by rememberSaveable {
        mutableStateOf(
            shelfPrefs.getString(HOME_SORT_V4, LuoShelfSortV33.RECENT_READ.key)
                ?: LuoShelfSortV33.RECENT_READ.key,
        )
    }
    var layoutKey by rememberSaveable {
        mutableStateOf(
            shelfPrefs.getString(HOME_LAYOUT_V4, HomeLayoutV4.LIST.key)
                ?: HomeLayoutV4.LIST.key,
        )
    }
    var shelfRevision by rememberSaveable { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var shelfManagerOpen by remember { mutableStateOf(false) }
    var batchOrganizerOpen by remember { mutableStateOf(false) }
    var actionBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var deleteBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var renameBook by remember { mutableStateOf<ReaderBookUi?>(null) }
    var pendingBatchDeleteIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val customShelves = remember(shelfRevision) { loadOrderedShelvesV4(shelfPrefs) }
    val assignments = remember(shelfRevision) { LuoShelfAssignmentsV33.all(shelfPrefs) }
    val sort = LuoShelfSortV33.of(sortKey)
    val layout = HomeLayoutV4.fromKey(layoutKey)

    // Derived shelf lists are cached so unrelated recompositions (search typing, sheet toggles,
    // import progress) do not re-filter the whole library every frame.
    val availableBooks = remember(state.stories, pendingBatchDeleteIds) {
        state.stories.filterNot { it.id in pendingBatchDeleteIds }
    }
    val writingBooks = remember(availableBooks) { availableBooks.filter { isWritingBookV4(it) } }
    val followingBooks = remember(availableBooks) { availableBooks.filter { isFollowingBookV4(it) } }
    val availableIds = remember(availableBooks) { availableBooks.mapTo(HashSet()) { it.id } }

    val tabs = buildList {
        add(HomeShelfTabV4(HOME_TAB_ALL_V4, "全部", availableBooks.size))
        add(HomeShelfTabV4(HOME_TAB_WRITING_V4, "在写", writingBooks.size))
        add(HomeShelfTabV4(HOME_TAB_FOLLOWING_V4, "追更", followingBooks.size))
        customShelves.forEach { shelf ->
            add(
                HomeShelfTabV4(
                    key = customShelfKeyV4(shelf),
                    label = shelf,
                    count = assignments.count { it.value == shelf && it.key in availableIds },
                ),
            )
        }
    }

    LaunchedEffect(activeTab, customShelves) {
        val custom = customShelfNameV4(activeTab)
        if (custom != null && custom !in customShelves) activeTab = HOME_TAB_ALL_V4
    }

    val tabFiltered = when {
        activeTab == HOME_TAB_ALL_V4 -> availableBooks
        activeTab == HOME_TAB_WRITING_V4 -> writingBooks
        activeTab == HOME_TAB_FOLLOWING_V4 -> followingBooks
        else -> {
            val shelf = customShelfNameV4(activeTab)
            if (shelf == null) availableBooks
            else availableBooks.filter { assignments[it.id] == shelf }
        }
    }

    val searched = remember(tabFiltered, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) tabFiltered
        else tabFiltered.filter {
            it.title.contains(keyword, ignoreCase = true) ||
                it.genre.contains(keyword, ignoreCase = true) ||
                it.premise.contains(keyword, ignoreCase = true)
        }
    }

    // Read each book's last-open stamp once per library/reader change instead of once per
    // sort comparison. Closing the reader changes openedBook, which refreshes the stamps.
    val lastReadAt = remember(availableBooks, state.openedBook?.id) {
        availableBooks.associate { it.id to progressPrefs.getLong("last_${it.id}", 0L) }
    }
    val books = remember(searched, sort, lastReadAt) {
        luoSortBooksV33(
            books = searched,
            sort = sort,
            lastRead = { book -> lastReadAt[book.id] ?: 0L },
        )
    }

    val continueReading = remember(availableBooks, lastReadAt, state.openedBook?.id, state.chapters) {
        homeContinueReadingV4(
            context = context,
            state = state,
            books = availableBooks,
            lastReadAt = lastReadAt,
        )
    }
    // Before the first library load finishes, show placeholders instead of a false empty shelf.
    val loadingShelf = !state.libraryLoaded && state.stories.isEmpty()
    val libraryEmpty = state.libraryLoaded && availableBooks.isEmpty()
    // The shelf is edge-to-edge: keep the last row clear of the gesture/navigation bar.
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    BackHandler(enabled = organizeOpen && !searchOpen) { organizeOpen = false }

    Box(modifier = Modifier.fillMaxSize().background(t.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = t.space4, end = t.space4, top = t.space3),
            ) {
                HomeShelfHeaderV4(
                    searchOpen = searchOpen,
                    organizeOpen = organizeOpen,
                    onSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                    onOrganize = { organizeOpen = !organizeOpen },
                    onAdd = { addOpen = true },
                    onOnline = onOnline,
                    onAiSetup = onAiSetup,
                    onRunCenter = onRunCenter,
                    onSkills = onSkills,
                )

                AnimatedVisibility(
                    visible = searchOpen,
                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { -it / 3 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { -it / 3 },
                ) {
                    HomeShelfSearchV4(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.padding(top = t.space3),
                    )
                }

                Spacer(Modifier.height(t.space3))

                HomeShelfTabsV4(
                    tabs = tabs,
                    activeTab = activeTab,
                    onTab = { activeTab = it },
                    onNewShelf = { shelfManagerOpen = true },
                )

                AnimatedVisibility(
                    visible = organizeOpen,
                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { -it / 4 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { -it / 4 },
                ) {
                    HomeShelfOrganizerPanelV4(
                        sort = sort,
                        layout = layout,
                        onSort = {
                            sortKey = it.key
                            shelfPrefs.edit().putString(HOME_SORT_V4, it.key).apply()
                        },
                        onLayout = {
                            layoutKey = it.key
                            shelfPrefs.edit().putString(HOME_LAYOUT_V4, it.key).apply()
                        },
                        onBatch = { organizeOpen = false; batchOrganizerOpen = true },
                        onShelfManager = { organizeOpen = false; shelfManagerOpen = true },
                        modifier = Modifier.padding(top = t.space3),
                    )
                }

                Spacer(Modifier.height(t.space3))
            }

            when (layout) {
                HomeLayoutV4.LIST -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = t.space4, end = t.space4, bottom = t.space6 + navigationBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(t.space2),
                    ) {
                        if (continueReading != null) {
                            item(key = "continue-reading") {
                                HomeContinueReadingV4(
                                    item = continueReading,
                                    onOpen = { onOpenBook(continueReading.book.id) },
                                )
                                Spacer(Modifier.height(t.space2))
                            }
                        }
                        if (loadingShelf) {
                            items(count = 4, key = { "skeleton-$it" }) { HomeBookListSkeletonV4() }
                        } else if (books.isEmpty()) {
                            item(key = "empty") {
                                HomeShelfEmptyV4(
                                    query = query,
                                    activeTab = activeTab,
                                    libraryEmpty = libraryEmpty,
                                    onAdd = { addOpen = true },
                                    onImport = onImportLocal,
                                    onCreate = onCreate,
                                    onOnline = onOnline,
                                )
                            }
                        } else {
                            lazyItems(items = books, key = { it.id }) { book ->
                                HomeBookListItemV4(
                                    context = context,
                                    state = state,
                                    book = book,
                                    onOpen = { onOpenBook(book.id) },
                                    onMore = { actionBook = book },
                                )
                            }
                        }
                    }
                }
                HomeLayoutV4.GRID -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = t.space4, end = t.space4, bottom = t.space6 + navigationBottom,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(t.space3),
                        verticalArrangement = Arrangement.spacedBy(t.space4),
                    ) {
                        if (continueReading != null) {
                            item(
                                key = "continue-reading",
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            ) {
                                Column {
                                    HomeContinueReadingV4(
                                        item = continueReading,
                                        onOpen = { onOpenBook(continueReading.book.id) },
                                    )
                                    Spacer(Modifier.height(t.space2))
                                }
                            }
                        }
                        if (loadingShelf) {
                            items(count = 6, key = { "skeleton-$it" }) { HomeBookGridSkeletonV4() }
                        } else if (books.isEmpty()) {
                            item(
                                key = "empty",
                                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                            ) {
                                HomeShelfEmptyV4(
                                    query = query,
                                    activeTab = activeTab,
                                    libraryEmpty = libraryEmpty,
                                    onAdd = { addOpen = true },
                                    onImport = onImportLocal,
                                    onCreate = onCreate,
                                    onOnline = onOnline,
                                )
                            }
                        } else {
                            items(items = books, key = { it.id }) { book ->
                                HomeBookGridItemV4(
                                    context = context,
                                    state = state,
                                    book = book,
                                    onOpen = { onOpenBook(book.id) },
                                    onMore = { actionBook = book },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (importState.busy) {
            HomeImportOverlayV4(
                currentFileName = importState.currentFileName,
                onCancel = onCancelImport.takeIf { importState.canCancel },
            )
        }
    }

    LuoShelfBatchOrganizerV50(
        books = availableBooks,
        shelves = customShelves,
        shelfPrefs = shelfPrefs,
        visible = batchOrganizerOpen,
        onDismiss = { batchOrganizerOpen = false },
        onDeleteBooks = { ids ->
            ids.forEach { onDeleteBook(it) }
            shelfRevision++
        },
        onPendingDeleteChanged = { pendingBatchDeleteIds = it },
        onMoveCompleted = { _, _ -> shelfRevision++ },
    )

    if (addOpen) {
        HomeAddBookDialogV4(
            onDismiss = { addOpen = false },
            onImport = { addOpen = false; onImportLocal() },
            onCreate = { addOpen = false; onCreate() },
        )
    }

    if (shelfManagerOpen) {
        HomeShelfManagerV4(
            shelves = customShelves,
            prefs = shelfPrefs,
            onDismiss = { shelfManagerOpen = false },
            onChanged = { shelfRevision++ },
        )
    }

    actionBook?.let { book ->
        HomeBookActionsV4(
            context = context,
            book = book,
            shelf = assignments[book.id],
            shelves = customShelves,
            onDismiss = { actionBook = null },
            onOpen = { actionBook = null; onOpenBook(book.id) },
            onTavern = { actionBook = null; onOpenTavern(book.id) },
            onMove = { shelf ->
                LuoShelfAssignmentsV33.assign(prefs = shelfPrefs, bookId = book.id, shelf = shelf)
                shelfRevision++
                actionBook = null
            },
            onDelete = { actionBook = null; deleteBook = book },
            onRename = { actionBook = null; renameBook = book },
        )
    }

    renameBook?.let { book ->
        HomeRenameBookDialogV4(
            book = book,
            onDismiss = { renameBook = null },
            onConfirm = { title ->
                renameBook = null
                if (title != book.title) onRenameBook(book.id, title)
            },
        )
    }

    deleteBook?.let { book ->
        HomeDeleteBookDialogV4(
            book = book,
            onDismiss = { deleteBook = null },
            onConfirm = {
                deleteBook = null
                LuoShelfAssignmentsV33.forget(prefs = shelfPrefs, bookId = book.id)
                com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90.forget(
                    com.xiguli.langhuan.ui.epub.ReaderEditionPreferenceV90.prefs(context), book.id,
                )
                shelfRevision++
                onDeleteBook(book.id)
            },
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeShelfHeaderV4(
    searchOpen: Boolean,
    organizeOpen: Boolean,
    onSearch: () -> Unit,
    onOrganize: () -> Unit,
    onAdd: () -> Unit,
    onOnline: () -> Unit,
    onAiSetup: () -> Unit,
    onRunCenter: () -> Unit,
    onSkills: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var moreOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "书架",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        HomeToolbarButtonV4(
            icon = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
            contentDescription = if (searchOpen) "关闭搜索" else "搜索书架",
            selected = searchOpen,
            onClick = onSearch,
        )
        Spacer(Modifier.width(HOME_TOOLBAR_GAP_V91))
        HomeToolbarButtonV4(
            icon = Icons.Rounded.Tune,
            contentDescription = "整理书架",
            selected = organizeOpen,
            onClick = onOrganize,
        )
        Spacer(Modifier.width(HOME_TOOLBAR_GAP_V91))
        HomeToolbarButtonV4(
            icon = Icons.Rounded.Explore,
            contentDescription = "在线书城",
            onClick = onOnline,
        )
        Spacer(Modifier.width(HOME_TOOLBAR_GAP_V91))
        HomeToolbarButtonV4(
            icon = Icons.Rounded.Add,
            contentDescription = "添加书籍",
            onClick = onAdd,
        )
        Spacer(Modifier.width(HOME_TOOLBAR_GAP_V91))
        // AI 服务、运行中心和写作技能原本只能从旧首页进入；在书架“更多”里恢复入口，
        // 保持书架顶部只有一个层级的工具按钮。
        Box {
            HomeToolbarButtonV4(
                icon = Icons.Rounded.MoreHoriz,
                contentDescription = "更多功能",
                selected = moreOpen,
                onClick = { moreOpen = true },
            )
            DropdownMenu(
                expanded = moreOpen,
                onDismissRequest = { moreOpen = false },
                shape = RoundedCornerShape(t.radiusLg),
                containerColor = t.card,
            ) {
                HomeMoreMenuItemV91(
                    icon = Icons.Rounded.SettingsSuggest,
                    title = "AI 与模型",
                    subtitle = "服务商、模型与任务路由",
                    onClick = { moreOpen = false; onAiSetup() },
                )
                HomeMoreMenuItemV91(
                    icon = Icons.Rounded.Insights,
                    title = "运行中心",
                    subtitle = "查看 AI 写作任务与日志",
                    onClick = { moreOpen = false; onRunCenter() },
                )
                HomeMoreMenuItemV91(
                    icon = Icons.Rounded.Psychology,
                    title = "写作技能",
                    subtitle = "管理创作 Skill",
                    onClick = { moreOpen = false; onSkills() },
                )
            }
        }
    }
}

@Composable
private fun HomeMoreMenuItemV91(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = t.space1)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = t.foreground,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = t.secondaryForeground,
            )
        },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

/** Toolbar icon buttons keep a 44 dp touch target with a tighter gap so five fit on 360 dp. */
private val HOME_TOOLBAR_GAP_V91 = 6.dp
internal const val HOME_TOOLBAR_BUTTON_DP_V91 = 44

@Composable
private fun HomeToolbarButtonV4(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(HOME_TOOLBAR_BUTTON_DP_V91.dp)
            .pressScaleV31(interaction)
            .clip(CircleShape)
            .background(
                color = if (selected) t.accent else t.card,
                shape = CircleShape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (selected) t.accentForeground else t.secondaryForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Search                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeShelfSearchV4(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Opening search is an explicit intent to type: focus the field and raise the keyboard.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = t.mutedForeground,
        )
        Spacer(Modifier.width(t.space2))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = "搜索书名、分类或简介",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "搜索书架输入框" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.foreground),
                cursorBrush = SolidColor(t.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Filtering is live; the IME action only dismisses the keyboard so results show.
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清空搜索",
                    modifier = Modifier.size(18.dp),
                    tint = t.mutedForeground,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                   Tabs                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeShelfTabsV4(
    tabs: List<HomeShelfTabV4>,
    activeTab: String,
    onTab: (String) -> Unit,
    onNewShelf: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(t.space2),
    ) {
        tabs.forEach { tab ->
            HomeShelfTabChipV4(
                label = tab.label,
                count = tab.count,
                selected = activeTab == tab.key,
                onClick = { onTab(tab.key) },
            )
        }
        HomeNewShelfChipV4(onClick = onNewShelf)
    }
}

@Composable
private fun HomeShelfTabChipV4(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(
                color = if (selected) t.accent else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .semantics {
                this.selected = selected
                stateDescription = "$count 本"
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Spacer(Modifier.width(t.space1))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) t.primary else t.mutedForeground,
        )
    }
}

@Composable
private fun HomeNewShelfChipV4(onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = t.primary,
        )
        Spacer(Modifier.width(t.space1))
        Text(
            text = "新建书架",
            style = MaterialTheme.typography.labelLarge,
            color = t.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                            Organizer panel                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeShelfOrganizerPanelV4(
    sort: LuoShelfSortV33,
    layout: HomeLayoutV4,
    onSort: (LuoShelfSortV33) -> Unit,
    onLayout: (HomeLayoutV4) -> Unit,
    onBatch: () -> Unit,
    onShelfManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space4),
    ) {
        HomeOrganizerSectionTitleV4(text = "排序")
        Spacer(Modifier.height(t.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            LuoShelfSortV33.entries.forEach { item ->
                HomeOrganizerChoiceV4(
                    text = item.label,
                    selected = sort == item,
                    modifier = Modifier.weight(1f),
                    onClick = { onSort(item) },
                )
            }
        }
        Spacer(Modifier.height(t.space4))
        HomeOrganizerSectionTitleV4(text = "显示")
        Spacer(Modifier.height(t.space2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            HomeOrganizerChoiceV4(
                text = "网格",
                icon = Icons.Rounded.GridView,
                selected = layout == HomeLayoutV4.GRID,
                modifier = Modifier.weight(1f),
                onClick = { onLayout(HomeLayoutV4.GRID) },
            )
            HomeOrganizerChoiceV4(
                text = "列表",
                icon = Icons.Rounded.ViewAgenda,
                selected = layout == HomeLayoutV4.LIST,
                modifier = Modifier.weight(1f),
                onClick = { onLayout(HomeLayoutV4.LIST) },
            )
        }
        Spacer(Modifier.height(t.space4))
        HomeOrganizerActionV4(
            icon = Icons.Rounded.SelectAll,
            title = "批量整理",
            subtitle = "多选书籍后一起移动或删除",
            onClick = onBatch,
        )
        Spacer(Modifier.height(t.space2))
        HomeOrganizerActionV4(
            icon = Icons.Rounded.FolderOpen,
            title = "书架管理",
            subtitle = "新建、重命名和调整书架顺序",
            onClick = onShelfManager,
        )
    }
}

@Composable
private fun HomeOrganizerSectionTitleV4(text: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = t.mutedForeground,
    )
}

@Composable
private fun HomeOrganizerChoiceV4(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = modifier
            .height(42.dp)
            .background(
                color = if (selected) t.accent else t.input,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (selected) t.accentForeground else t.secondaryForeground,
            )
            Spacer(Modifier.width(t.space1))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) t.accentForeground else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeOrganizerActionV4(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(color = t.accent, shape = RoundedCornerShape(t.radiusMd))
                .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = t.accentForeground,
            )
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                            Continue Reading                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeContinueReadingV4(
    item: HomeContinueReadingV4,
    onOpen: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    val chapterLine = buildString {
        append("第 ${item.chapterNumber} 章")
        val title = item.chapterTitle?.trim()?.takeIf { it.isNotBlank() }
        if (title != null) { append(" · "); append(title) }
    }
    val lastRead = homeRelativeTimeV91(item.lastReadAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color = t.goldContainer, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClickLabel = "继续阅读", role = Role.Button, onClick = onOpen)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A real cover (Legado / ReadYou style) makes the resume card read as "this book",
        // not as a generic banner; the bookmark glyph stays as a small corner tag.
        Box {
            HomeBookCoverV4(
                book = item.book,
                modifier = Modifier.width(46.dp).height(64.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(18.dp)
                    .background(color = t.gold, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = t.card,
                )
            }
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (lastRead != null) "继续阅读 · $lastRead" else "继续阅读",
                style = MaterialTheme.typography.labelMedium,
                color = t.goldForeground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.book.title,
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = chapterLine,
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.chapterFraction > 0.01f) {
                Spacer(Modifier.height(t.space2))
                LinearProgressIndicator(
                    progress = { item.chapterFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "本章已读 ${(item.chapterFraction * 100).roundToInt()}%" },
                    color = t.gold,
                    trackColor = t.gold.copy(alpha = 0.18f),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.width(t.space2))
        Icon(
            imageVector = Icons.Rounded.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = t.goldForeground,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              List Item                                     */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBookListItemV4(
    context: Context,
    state: LibraryExperienceState,
    book: ReaderBookUi,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val isEpub = remember(book.id, book.updatedAt) {
        EpubReaderEntry.isEpub(context, book.id)
    }
    val isOnline = isFollowingBookV4(book)
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onMore,
                onClickLabel = "打开",
                onLongClickLabel = "书籍菜单",
            )
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeBookCoverV4(
            book = book,
            modifier = Modifier.width(58.dp).height(82.dp),
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = homeGenreLabelV4(book),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badge = when {
                    isOnline -> "在线"
                    isEpub -> "EPUB"
                    else -> null
                }
                if (badge != null) {
                    Spacer(Modifier.width(t.space2))
                    HomeSourceBadgeV4(text = badge, gold = isEpub && !isOnline)
                }
            }
            Spacer(Modifier.height(t.space2))
            Text(
                text = homeBookProgressLabelV4(context = context, state = state, book = book),
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(t.space1))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "书籍菜单",
                modifier = Modifier.size(20.dp),
                tint = t.mutedForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Grid Item                                    */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeBookGridItemV4(
    context: Context,
    state: LibraryExperienceState,
    book: ReaderBookUi,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val isEpub = remember(book.id, book.updatedAt) {
        EpubReaderEntry.isEpub(context, book.id)
    }
    val online = isFollowingBookV4(book)
    // The whole tile (cover + title) opens the book, like Legado / Moon+ shelves; previously
    // only the cover responded and taps on the title were silently ignored.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusMd))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onMore,
                onClickLabel = "打开",
                onLongClickLabel = "书籍菜单",
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .clip(RoundedCornerShape(t.radiusMd))
                .background(color = t.input, shape = RoundedCornerShape(t.radiusMd))
                .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd)),
        ) {
            CoverPreviewV3(
                path = book.coverPath,
                title = book.title,
                modifier = Modifier.fillMaxSize(),
                targetWidthPx = HOME_GRID_COVER_PX_V86,
            )
            // 44 dp touch target around a 30 dp visual chip.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(color = t.card.copy(alpha = 0.92f), shape = CircleShape)
                        .border(width = 1.dp, color = t.border, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "书籍菜单",
                        modifier = Modifier.size(17.dp),
                        tint = t.secondaryForeground,
                    )
                }
            }
        }
        Spacer(Modifier.height(t.space2))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(t.space1))
        Text(
            text = homeGenreLabelV4(book),
            style = MaterialTheme.typography.bodySmall,
            color = t.secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val badge = when {
            online -> "在线"
            isEpub -> "EPUB"
            else -> null
        }
        if (badge != null) {
            Spacer(Modifier.height(t.space1))
            HomeSourceBadgeV4(text = badge, gold = isEpub && !online)
        }
        Spacer(Modifier.height(t.space1))
        Text(
            text = homeBookProgressLabelV4(context = context, state = state, book = book),
            style = MaterialTheme.typography.labelSmall,
            color = t.mutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Cover                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeBookCoverV4(
    book: ReaderBookUi,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Box(
        modifier = modifier
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape),
    ) {
        CoverPreviewV3(
            path = book.coverPath,
            title = book.title,
            modifier = Modifier.fillMaxSize(),
            targetWidthPx = HOME_LIST_COVER_PX_V86,
        )
    }
}

/**
 * Shelf thumbnails are 50–58 dp wide in the list and ~1/3 screen in the grid. Decoding them at the
 * 720 px detail size wasted ~8x memory and evicted the 24 MB cover cache while scrolling.
 */
internal const val HOME_LIST_COVER_PX_V86 = 240
internal const val HOME_GRID_COVER_PX_V86 = 400


/* -------------------------------------------------------------------------- */
/*                              Source Badge                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeSourceBadgeV4(
    text: String,
    gold: Boolean,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Row(
        modifier = Modifier
            .background(
                color = if (gold) t.goldContainer else t.accent,
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(horizontal = t.space2, vertical = t.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (gold) Icons.Rounded.MenuBook else Icons.Rounded.Wifi,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = if (gold) t.goldForeground else t.accentForeground,
        )
        Spacer(Modifier.width(t.space1))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (gold) t.goldForeground else t.accentForeground,
            fontWeight = FontWeight.Medium,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Empty                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeShelfEmptyV4(
    query: String,
    activeTab: String,
    onAdd: () -> Unit,
    libraryEmpty: Boolean = false,
    onImport: () -> Unit = onAdd,
    onCreate: () -> Unit = onAdd,
    onOnline: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.space6, horizontal = t.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(t.radiusLg)
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = t.input, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (query.isBlank()) Icons.Rounded.LibraryBooks else Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = t.mutedForeground,
            )
        }
        Spacer(Modifier.height(t.space4))
        Text(
            text = when {
                query.isNotBlank() -> "没有找到「${query.trim()}」"
                libraryEmpty && activeTab == HOME_TAB_ALL_V4 -> "书架还是空的"
                activeTab == HOME_TAB_WRITING_V4 -> "还没有在写的作品"
                activeTab == HOME_TAB_FOLLOWING_V4 -> "还没有追更中的书"
                else -> "这个书架还是空的"
            },
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(t.space2))
        Text(
            text = when {
                query.isNotBlank() -> "换个书名、分类或简介里的关键词试试。"
                libraryEmpty && activeTab == HOME_TAB_ALL_V4 -> "导入手机里的 TXT / EPUB，或者从书源找一本开始读。"
                else -> "添加一本书，或者把已有作品移动到这里。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
            textAlign = TextAlign.Center,
        )
        if (query.isBlank() && libraryEmpty && activeTab == HOME_TAB_ALL_V4) {
            // DESIGN.md §7: an empty shelf has one primary action (import); AI creation and the
            // online store stay secondary.
            Spacer(Modifier.height(t.space4))
            HomePrimaryButtonV4(
                text = "导入本地书籍",
                icon = Icons.Rounded.FolderOpen,
                onClick = onImport,
            )
            Spacer(Modifier.height(t.space2))
            Row(horizontalArrangement = Arrangement.spacedBy(t.space2)) {
                HomeTextActionV91(text = "开始创作", icon = Icons.Rounded.AutoAwesome, onClick = onCreate)
                HomeTextActionV91(text = "在线书城", icon = Icons.Rounded.Explore, onClick = onOnline)
            }
        } else if (query.isBlank()) {
            Spacer(Modifier.height(t.space4))
            HomePrimaryButtonV4(
                text = "添加书籍",
                icon = Icons.Rounded.Add,
                onClick = onAdd,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Import Overlay                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeImportOverlayV4(currentFileName: String, onCancel: (() -> Unit)? = null) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(t.radiusXl)
        Row(
            modifier = Modifier
                .padding(t.space5)
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = t.primary,
            )
            Spacer(Modifier.width(t.space3))
            Column {
                Text(
                    text = "正在导入",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                )
                Spacer(Modifier.height(t.space1))
                Text(
                    text = currentFileName.ifBlank { "正在读取小说…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onCancel != null) {
                Spacer(Modifier.width(t.space3))
                HomeTextActionV91(text = "取消导入", icon = Icons.Rounded.Close, onClick = onCancel)
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                              Add Dialog                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeAddBookDialogV4(
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onCreate: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "添加书籍",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = "导入已有小说，或者开始创作一本新的。",
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
            )
            Spacer(Modifier.height(t.space4))
            HomeDialogActionV4(
                icon = Icons.Rounded.FolderOpen,
                title = "导入本地书籍",
                subtitle = "TXT · Markdown · EPUB",
                onClick = onImport,
            )
            Spacer(Modifier.height(t.space2))
            HomeDialogActionV4(
                icon = Icons.Rounded.AutoAwesome,
                title = "开始创作",
                subtitle = "和 AI 一起构思一本新小说",
                gold = true,
                onClick = onCreate,
            )
            Spacer(Modifier.height(t.space3))
            HomeSecondaryButtonV4(text = "取消", onClick = onDismiss)
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Book Actions                                   */
/* -------------------------------------------------------------------------- */

// 注意：ChatGPT 原稿中 HomeBookActionsV4 的 onMove 参数重复声明了两次，
// 适配阶段需删除其中一个，此处存档保留原样以便核对。
@Composable
private fun HomeBookActionsV4(
    context: Context,
    book: ReaderBookUi,
    shelf: String?,
    shelves: List<String>,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTavern: () -> Unit,
    onMove: (String?) -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current
    var moveOpen by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeBookCoverV4(
                    book = book,
                    modifier = Modifier.width(50.dp).height(70.dp),
                )
                Spacer(Modifier.width(t.space3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(t.space1))
                    Text(
                        text = shelf?.takeIf { it.isNotBlank() } ?: "全部",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }
            Spacer(Modifier.height(t.space4))
            HomeDialogActionV4(
                icon = Icons.Rounded.MenuBook,
                title = "打开 / 继续阅读",
                onClick = onOpen,
            )
            Spacer(Modifier.height(t.space2))
            HomeDialogActionV4(
                icon = Icons.Rounded.AutoAwesome,
                title = "进入酒馆",
                onClick = onTavern,
            )
            Spacer(Modifier.height(t.space2))
            HomeDialogActionV4(
                icon = Icons.Rounded.Edit,
                title = "修改书名",
                onClick = onRename,
            )
            Spacer(Modifier.height(t.space2))
            HomeDialogActionV4(
                icon = Icons.Rounded.DriveFileMove,
                title = "移动书架",
                subtitle = shelf ?: "当前未分组",
                onClick = { moveOpen = true },
            )
            Spacer(Modifier.height(t.space2))
            HomeDialogActionV4(
                icon = Icons.Rounded.DeleteOutline,
                title = "删除小说",
                destructive = true,
                onClick = onDelete,
            )
        }
    }
    if (moveOpen) {
        HomeMoveBookDialogV4(
            book = book,
            shelves = shelves,
            current = shelf,
            onDismiss = { moveOpen = false },
            onMove = { moveOpen = false; onMove(it) },
        )
    }
}

@Composable
private fun HomeDialogActionV4(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
    gold: Boolean = false,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when {
                    gold -> t.goldContainer
                    destructive -> t.destructive.copy(alpha = 0.08f)
                    else -> t.input
                },
                shape = shape,
            )
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when {
                gold -> t.goldForeground
                destructive -> t.destructive
                else -> t.secondaryForeground
            },
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    destructive -> t.destructive
                    gold -> t.goldForeground
                    else -> t.foreground
                },
                fontWeight = FontWeight.Medium,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(t.space1))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun HomePrimaryButtonV4(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(color = t.primary, shape = shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = t.space4),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = t.card,
            )
            Spacer(Modifier.width(t.space2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = t.card,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HomeSecondaryButtonV4(
    text: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = t.secondaryForeground,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun HomeMoveBookDialogV4(
    book: ReaderBookUi,
    shelves: List<String>,
    current: String?,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "移动《${book.title}》",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space3))
            HomeMoveTargetV4(
                label = "全部（不分组）",
                selected = current.isNullOrBlank(),
                onClick = { onMove(null) },
            )
            Spacer(Modifier.height(t.space2))
            shelves.forEach { shelf ->
                HomeMoveTargetV4(
                    label = shelf,
                    selected = current == shelf,
                    onClick = { onMove(shelf) },
                )
                Spacer(Modifier.height(t.space2))
            }
        }
    }
}

@Composable
private fun HomeMoveTargetV4(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = if (selected) t.accent else t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) t.primary else t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = if (selected) t.accentForeground else t.secondaryForeground,
        )
        Spacer(Modifier.width(t.space3))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) t.foreground else t.secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = t.primary,
            )
        }
    }
}

@Composable
private fun HomeRenameBookDialogV4(
    book: ReaderBookUi,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var text by remember(book.id) {
        mutableStateOf(TextFieldValue(book.title, selection = TextRange(0, book.title.length)))
    }
    val clean = com.xiguli.langhuan.data.normalizeBookTitleV90(text.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "修改书名",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space3))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .focusRequester(focus)
                    .background(color = t.input, shape = RoundedCornerShape(t.radiusMd))
                    .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd))
                    .padding(horizontal = t.space3, vertical = t.space3)
                    .semantics { contentDescription = "书名" },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.foreground),
                cursorBrush = SolidColor(t.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { clean?.let(onConfirm) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.text.isEmpty()) {
                            Text(
                                text = "输入新的书名",
                                style = MaterialTheme.typography.bodyLarge,
                                color = t.mutedForeground,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = if (clean == null) "书名不能为空" else "只修改书架与阅读器显示的书名，正文与进度不变。",
                style = MaterialTheme.typography.bodySmall,
                color = if (clean == null) t.destructive else t.mutedForeground,
            )
            Spacer(Modifier.height(t.space4))
            Row(horizontalArrangement = Arrangement.spacedBy(t.space2)) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeSecondaryButtonV4(text = "取消", onClick = onDismiss)
                }
                Box(modifier = Modifier.weight(1f)) {
                    HomePrimaryButtonV4(
                        text = "保存",
                        onClick = { clean?.let(onConfirm) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDeleteBookDialogV4(
    book: ReaderBookUi,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "删除《${book.title}》？",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = "将移除正文、章节版本、长期记忆与本地封面，此操作不可恢复。",
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
            )
            Spacer(Modifier.height(t.space4))
            Row(horizontalArrangement = Arrangement.spacedBy(t.space2)) {
                Box(modifier = Modifier.weight(1f)) {
                    HomeSecondaryButtonV4(text = "取消", onClick = onDismiss)
                }
                Box(modifier = Modifier.weight(1f)) {
                    HomeDestructiveButtonV4(text = "删除", onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun HomeDestructiveButtonV4(
    text: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(color = t.destructive, shape = shape)
            .border(width = 1.dp, color = t.destructive, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = t.destructiveForeground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HomeShelfManagerV4(
    shelves: List<String>,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var items by remember(shelves) { mutableStateOf(shelves) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun persist(next: List<String>) {
        saveOrderedShelvesV4(prefs, next)
        items = next
        onChanged()
    }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(t.radiusXl)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = t.card, shape = shape)
                .border(width = 1.dp, color = t.border, shape = shape)
                .padding(t.space4),
        ) {
            Text(
                text = "书架管理",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space3))
            items.forEachIndexed { index, shelf ->
                if (renaming == shelf) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(color = t.input, shape = RoundedCornerShape(t.radiusMd))
                                .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd))
                                .padding(horizontal = t.space3, vertical = t.space2),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.foreground),
                            cursorBrush = SolidColor(t.primary),
                        )
                        Spacer(Modifier.width(t.space2))
                        HomeMiniActionV4(text = "确定", enabled = renameText.isNotBlank()) {
                            renameShelfV4(prefs, items, shelf, renameText)
                            persist(items.map { if (it == shelf) renameText.trim() else it })
                            renaming = null
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shelf,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = t.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (index > 0) {
                            HomeMiniActionV4(text = "上移", enabled = true) {
                                persist(items.toMutableList().also {
                                    it.removeAt(index); it.add(index - 1, shelf)
                                })
                            }
                            Spacer(Modifier.width(t.space1))
                        }
                        HomeMiniActionV4(text = "重命名", enabled = true) {
                            renaming = shelf; renameText = shelf
                        }
                        Spacer(Modifier.width(t.space1))
                        HomeMiniActionV4(text = "删除", enabled = true, destructive = true) {
                            LuoShelfAssignmentsV33.removeShelf(prefs, shelf)
                            persist(items.filterNot { it == shelf })
                        }
                    }
                }
                Spacer(Modifier.height(t.space2))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(color = t.input, shape = RoundedCornerShape(t.radiusMd))
                        .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd))
                        .padding(horizontal = t.space3, vertical = t.space2),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = t.foreground),
                    cursorBrush = SolidColor(t.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (newName.isBlank()) {
                                Text(
                                    text = "新建书架名称",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = t.mutedForeground,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(t.space2))
                HomeMiniActionV4(text = "添加", enabled = newName.isNotBlank()) {
                    val name = newName.trim()
                    if (name.isNotBlank() && name !in items) persist(items + name)
                    newName = ""
                }
            }
            Spacer(Modifier.height(t.space3))
            HomeSecondaryButtonV4(text = "完成", onClick = onDismiss)
        }
    }
}

@Composable
private fun HomeMiniActionV4(
    text: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusSm)
    Text(
        text = text,
        modifier = Modifier
            .background(color = t.input, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = t.space2, vertical = t.space1),
        style = MaterialTheme.typography.labelMedium,
        color = if (!enabled) {
            t.mutedForeground.copy(alpha = 0.4f)
        } else if (destructive) {
            t.destructive
        } else {
            t.secondaryForeground
        },
    )
}


/* -------------------------------------------------------------------------- */
/*                              Shelf Storage                                 */
/* -------------------------------------------------------------------------- */

private fun loadOrderedShelvesV4(prefs: SharedPreferences): List<String> {
    val set = prefs.getStringSet(HOME_CUSTOM_SHELVES_V4, emptySet())
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        .orEmpty()
    if (set.isEmpty()) return emptyList()
    val stored = prefs.getString(HOME_CUSTOM_SHELF_ORDER_V4, "")
        .orEmpty()
        .split(HOME_SHELF_SEPARATOR_V4)
        .map { it.trim() }
        .filter { it.isNotBlank() && it in set }
        .distinct()
    return buildList {
        addAll(stored)
        addAll(set.filterNot { it in stored }.sorted())
    }
}

private fun saveOrderedShelvesV4(prefs: SharedPreferences, shelves: List<String>) {
    val normalized = shelves.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    prefs.edit()
        .putStringSet(HOME_CUSTOM_SHELVES_V4, normalized.toSet())
        .putString(HOME_CUSTOM_SHELF_ORDER_V4, normalized.joinToString(HOME_SHELF_SEPARATOR_V4))
        .apply()
}

private fun renameShelfV4(
    prefs: SharedPreferences,
    shelves: List<String>,
    oldName: String,
    newName: String,
) {
    val normalized = newName.trim()
    if (normalized.isBlank() || normalized == oldName) return
    val assignments = LuoShelfAssignmentsV33.all(prefs)
    assignments.filterValues { it == oldName }.keys.forEach { bookId ->
        LuoShelfAssignmentsV33.assign(prefs = prefs, bookId = bookId, shelf = normalized)
    }
    saveOrderedShelvesV4(
        prefs = prefs,
        shelves = shelves.map { if (it == oldName) normalized else it },
    )
}


/* -------------------------------------------------------------------------- */
/*                               Data Helpers                                 */
/* -------------------------------------------------------------------------- */

private fun isFollowingBookV4(book: ReaderBookUi): Boolean =
    book.sourceId.isNotBlank() || book.sourceBookUrl.isNotBlank()

private fun isWritingBookV4(book: ReaderBookUi): Boolean =
    book.genre != "导入作品" && !isFollowingBookV4(book)

private fun customShelfKeyV4(shelf: String): String = HOME_TAB_CUSTOM_PREFIX_V4 + shelf

private fun customShelfNameV4(key: String): String? =
    key.takeIf { it.startsWith(HOME_TAB_CUSTOM_PREFIX_V4) }
        ?.removePrefix(HOME_TAB_CUSTOM_PREFIX_V4)
        ?.takeIf { it.isNotBlank() }

private fun homeGenreLabelV4(book: ReaderBookUi): String = when {
    book.genre.isBlank() -> "未分类"
    book.genre == "导入作品" -> "本地小说"
    else -> book.genre
}

private fun homeBookProgressLabelV4(
    context: Context,
    state: LibraryExperienceState,
    book: ReaderBookUi,
): String {
    if (isWritingBookV4(book)) {
        return if (book.currentChapter > 0) "写到第 ${book.currentChapter} 章" else "尚未开始写作"
    }
    val progress = ReaderProgressStoreV11.load(
        context = context,
        bookId = book.id,
        fallbackChapter = book.currentChapter.coerceAtLeast(1),
    )
    if (progress.updatedAt <= 0L) {
        return if (isFollowingBookV4(book) && book.currentChapter > 0) {
            "更新 ${book.currentChapter} 章"
        } else {
            "未读"
        }
    }
    if (state.openedBook?.id == book.id && state.chapters.isNotEmpty()) {
        val ordered = state.chapters.sortedBy { it.chapterNumber }
        val index = ordered.indexOfFirst { it.chapterNumber == progress.chapterNumber }
        if (index >= 0) {
            val raw = (index.toFloat() + progress.positionFraction) / ordered.size.toFloat()
            val percent = (raw.coerceIn(0f, 1f) * 100f).roundToInt()
            if (index == ordered.lastIndex && progress.positionFraction >= 0.995f) {
                return "已读完"
            }
            if (percent > 0) return "已读 $percent%"
        }
    }
    return "读到第 ${progress.chapterNumber} 章"
}

private fun homeContinueReadingV4(
    context: Context,
    state: LibraryExperienceState,
    books: List<ReaderBookUi>,
    lastReadAt: Map<String, Long>,
): HomeContinueReadingV4? {
    val recentPair = books
        .map { it to (lastReadAt[it.id] ?: 0L) }
        .filter { it.second > 0L }
        .maxByOrNull { it.second } ?: return null
    val recent = recentPair.first
    val progress = ReaderProgressStoreV11.load(
        context = context,
        bookId = recent.id,
        fallbackChapter = recent.currentChapter.coerceAtLeast(1),
    )
    val title = if (state.openedBook?.id == recent.id) {
        state.chapters
            .firstOrNull { it.chapterNumber == progress.chapterNumber }
            ?.title
            ?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    return HomeContinueReadingV4(
        book = recent,
        chapterNumber = progress.chapterNumber,
        chapterTitle = title,
        lastReadAt = recentPair.second,
        chapterFraction = progress.positionFraction,
    )
}

/** "3 分钟前" style stamp for the resume card; null when unknown or in the future. */
private fun homeRelativeTimeV91(at: Long, now: Long = System.currentTimeMillis()): String? {
    if (at <= 0L || at > now + 60_000L) return null
    if (now - at < DateUtils.MINUTE_IN_MILLIS) return "刚刚"
    return DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
private fun HomeTextActionV91(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = t.primary)
        Spacer(Modifier.width(t.space1))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = t.primary, fontWeight = FontWeight.Medium)
    }
}

/** Placeholder row shaped like [HomeBookListItemV4]; shown only until the first library load. */
@Composable
private fun HomeBookListSkeletonV4() {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(t.space3)
            .semantics { contentDescription = "正在载入书架" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanghuanSkeletonV31(Modifier.width(58.dp).height(82.dp), RoundedCornerShape(t.radiusSm))
        Spacer(Modifier.width(t.space3))
        Column(Modifier.weight(1f)) {
            LanghuanSkeletonV31(Modifier.fillMaxWidth(0.6f).height(16.dp))
            Spacer(Modifier.height(t.space2))
            LanghuanSkeletonV31(Modifier.fillMaxWidth(0.35f).height(12.dp))
            Spacer(Modifier.height(t.space2))
            LanghuanSkeletonV31(Modifier.fillMaxWidth(0.45f).height(12.dp))
        }
    }
}

@Composable
private fun HomeBookGridSkeletonV4() {
    val t = LocalLanghuanUiTokens.current
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "正在载入书架" }) {
        LanghuanSkeletonV31(Modifier.fillMaxWidth().aspectRatio(0.70f), RoundedCornerShape(t.radiusMd))
        Spacer(Modifier.height(t.space2))
        LanghuanSkeletonV31(Modifier.fillMaxWidth(0.8f).height(14.dp))
        Spacer(Modifier.height(t.space1))
        LanghuanSkeletonV31(Modifier.fillMaxWidth(0.5f).height(11.dp))
    }
}
