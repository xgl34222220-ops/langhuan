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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.xiguli.langhuan.ui.design.FlatHairlineV93
import com.xiguli.langhuan.ui.design.FlatListRowV93
import com.xiguli.langhuan.ui.design.FlatSectionLabelV93
import com.xiguli.langhuan.ui.design.FlatSwitchRowV93
import com.xiguli.langhuan.ui.design.FlatTopBarV93
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map


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
/** V92: books whose saved reader position is the end of the last chapter. */
private const val HOME_TAB_FINISHED_V4 = "__finished__"
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
            entries.firstOrNull { it.key == key } ?: GRID
    }
}

private data class HomeShelfTabV4(
    val key: String,
    val label: String,
    /** Null while the library is still loading: never show a false 「全部 0」. */
    val count: Int?,
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
    onOnline: () -> Unit = {},
    onRenameBook: (String, String) -> Unit = { _, _ -> },
    onCancelImport: () -> Unit = {},
    /**
     * V94: the shelf is the first bottom-bar tab. The bar owns the navigation-bar inset, so the
     * list does not add it again. AI 与模型、运行中心、写作技能 moved to the 创作/我的 tabs.
     */
    insideTabs: Boolean = false,
) {
    val context = LocalContext.current
    val t = LocalLanghuanUiTokens.current

    val shelfPrefs = remember(context) {
        context.getSharedPreferences(HOME_SHELF_PREFS_V4, Context.MODE_PRIVATE)
    }
    val progressPrefs = remember(context) {
        context.getSharedPreferences("reader_progress_v1", Context.MODE_PRIVATE)
    }
    val bookProgressPrefs = remember(context) {
        context.getSharedPreferences(ShelfReadingProgressStoreV92.PREFS, Context.MODE_PRIVATE)
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
            // V93: the cover grid is the default shelf view; an explicit 「列表」 choice is kept.
            shelfPrefs.getString(HOME_LAYOUT_V4, HomeLayoutV4.GRID.key)
                ?: HomeLayoutV4.GRID.key,
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

    // Whole-book progress per book, read once per library/reader change (the reader writes it
    // when it closes, which changes openedBook). Used by the progress sort, the 「已读完」 tab
    // and the per-row progress label.
    val readingProgress = remember(availableBooks, state.openedBook?.id) {
        availableBooks.associate { it.id to ShelfReadingProgressStoreV92.load(bookProgressPrefs, it.id) }
    }
    val finishedBooks = remember(availableBooks, readingProgress) {
        availableBooks.filter { readingProgress[it.id]?.finished == true }
    }
    // Before the first library load finishes, show placeholders instead of a false empty shelf.
    val loadingShelf = !state.libraryLoaded && state.stories.isEmpty()

    val tabs = buildList {
        fun count(value: Int): Int? = if (loadingShelf) null else value
        add(HomeShelfTabV4(HOME_TAB_ALL_V4, "全部", count(availableBooks.size)))
        add(HomeShelfTabV4(HOME_TAB_WRITING_V4, "在写", count(writingBooks.size)))
        add(HomeShelfTabV4(HOME_TAB_FOLLOWING_V4, "追更", count(followingBooks.size)))
        add(HomeShelfTabV4(HOME_TAB_FINISHED_V4, "已读完", count(finishedBooks.size)))
        customShelves.forEach { shelf ->
            add(
                HomeShelfTabV4(
                    key = customShelfKeyV4(shelf),
                    label = shelf,
                    count = count(assignments.count { it.value == shelf && it.key in availableIds }),
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
        activeTab == HOME_TAB_FINISHED_V4 -> finishedBooks
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
    val books = remember(searched, sort, lastReadAt, readingProgress) {
        luoSortBooksV33(
            books = searched,
            sort = sort,
            lastRead = { book -> lastReadAt[book.id] ?: 0L },
            progress = { book -> readingProgress[book.id]?.sortKey ?: -1f },
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
    val libraryEmpty = state.libraryLoaded && availableBooks.isEmpty()
    // The shelf is edge-to-edge: keep the last row clear of the gesture/navigation bar.
    val navigationBottom = if (insideTabs) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(enabled = searchOpen) { searchOpen = false; query = "" }
    BackHandler(enabled = organizeOpen && !searchOpen) { organizeOpen = false }

    Box(modifier = Modifier.fillMaxSize().background(t.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = HOME_PAGE_GUTTER_V93, end = t.space2, top = t.space2),
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
                )

                AnimatedVisibility(
                    visible = searchOpen,
                    enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { -it / 3 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { -it / 3 },
                ) {
                    HomeShelfSearchV4(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.padding(top = t.space2, end = t.space2),
                    )
                }

                Spacer(Modifier.height(t.space1))

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
                        modifier = Modifier.padding(top = t.space2, end = t.space2),
                    )
                }

                Spacer(Modifier.height(t.space3))
            }

            when (layout) {
                HomeLayoutV4.LIST -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = HOME_PAGE_GUTTER_V93, end = HOME_PAGE_GUTTER_V93,
                            bottom = t.space6 + navigationBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(t.space1),
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
                                    progress = readingProgress[book.id] ?: ShelfReadingProgressV92.UNREAD,
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
                            start = HOME_PAGE_GUTTER_V93, end = HOME_PAGE_GUTTER_V93,
                            bottom = t.space6 + navigationBottom,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(HOME_GRID_GAP_V93),
                        verticalArrangement = Arrangement.spacedBy(t.space5),
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
                                    progress = readingProgress[book.id] ?: ShelfReadingProgressV92.UNREAD,
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
            progress = readingProgress[book.id] ?: ShelfReadingProgressV92.UNREAD,
            progressLabel = homeBookProgressLabelV4(
                context = context, state = state, book = book,
                shelfProgress = readingProgress[book.id] ?: ShelfReadingProgressV92.UNREAD,
            ),
            chapterCount = bookProgressPrefs.getInt("total_${book.id}", 0),
            chapterIndex = bookProgressPrefs.getInt("index_${book.id}", -1),
            lastReadAt = lastReadAt[book.id] ?: 0L,
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
) {
    val t = LocalLanghuanUiTokens.current
    // V93: a small title on the left and plain line icons on the right, like the reference
    // reader's 「正在阅读」 shelf. No filled circles or outlines around the buttons.
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "书架",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = t.foreground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        HomeToolbarButtonV4(
            icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
            contentDescription = if (searchOpen) "关闭搜索" else "搜索书架",
            selected = searchOpen,
            onClick = onSearch,
        )
        Spacer(Modifier.width(HOME_TOOLBAR_GAP_V91))
        HomeToolbarButtonV4(
            icon = Icons.Outlined.Tune,
            contentDescription = "整理书架",
            selected = organizeOpen,
            onClick = onOrganize,
        )
        Spacer(Modifier.width(HOME_TOOLBAR_GAP_V91))
        HomeToolbarButtonV4(
            icon = Icons.Outlined.Add,
            contentDescription = "添加书籍",
            onClick = onAdd,
        )
    }
}

/** Toolbar icon buttons keep a 44 dp touch target with a tight gap so five fit on 360 dp. */
private val HOME_TOOLBAR_GAP_V91 = 2.dp
internal const val HOME_TOOLBAR_BUTTON_DP_V91 = 44

/** V93 page gutter and grid gap: generous whitespace around a plain cover grid. */
private val HOME_PAGE_GUTTER_V93 = 20.dp
private val HOME_GRID_GAP_V93 = 18.dp

@Composable
private fun HomeToolbarButtonV4(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val interaction = remember { MutableInteractionSource() }
    Box {
    Box(
        modifier = Modifier
            .size(HOME_TOOLBAR_BUTTON_DP_V91.dp)
            .pressScaleV31(interaction)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                if (badge) stateDescription = HOME_RUN_ACTIVE_LABEL_V92
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = if (selected) t.primary else t.foreground.copy(alpha = 0.84f),
        )
    }
    if (badge) {
        HomeRunBadgeDotV92(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp),
        )
    }
    }
}

internal const val HOME_RUN_ACTIVE_LABEL_V92 = "有 AI 写作任务正在运行"

/** Small pulsing dot: a run-center task is active. Purely visual; semantics live on the button. */
@Composable
private fun HomeRunBadgeDotV92(modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "runBadgePulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "runBadgeAlpha",
    )
    Box(
        modifier = modifier
            .size(9.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color = t.background, shape = CircleShape)
            .padding(1.5.dp)
            .background(color = t.primary, shape = CircleShape),
    )
}

/**
 * Whether the chapter-run runtime has a task running or queued, observed only while the shelf
 * is at least STARTED. The runtime is lazily created by the app; the shelf never forces it to
 * start (keeping cold start side-effect free) and maps its state on a background dispatcher.
 */
@Composable
internal fun rememberRunCenterActiveV92(): Boolean {
    val context = LocalContext.current
    val runtime = remember(context) {
        (context.applicationContext as? com.xiguli.langhuan.LanghuanApplication)?.chapterRunRuntimeIfStarted
    }
    val flow = remember(runtime) {
        runtime?.state
            ?.map { it.active || it.queuedCount > 0 }
            ?.distinctUntilChanged()
            ?.flowOn(Dispatchers.Default)
            ?: flowOf(false)
    }
    val active by flow.collectAsStateWithLifecycle(initialValue = false)
    return active
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
    val shape = CircleShape
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Opening search is an explicit intent to type: focus the field and raise the keyboard.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(color = t.foreground.copy(alpha = 0.05f), shape = shape)
            .padding(horizontal = t.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
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
                    imageVector = Icons.Outlined.Close,
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
    // V93: plain text tabs (no chips); the selected one is bold with a short accent underline.
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(t.space4),
        verticalAlignment = Alignment.CenterVertically,
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
        Spacer(Modifier.width(t.space2))
    }
}

@Composable
private fun HomeShelfTabChipV4(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(t.radiusSm))
            .semantics {
                this.selected = selected
                if (count != null) stateDescription = "$count 本"
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) t.foreground else t.mutedForeground,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            // While the first library load is running the count is unknown: show nothing rather
            // than a misleading 「全部 0」.
            if (count != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(bottom = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground.copy(alpha = 0.75f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(14.dp)
                .height(3.dp)
                .background(
                    color = if (selected) t.primary else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}

@Composable
private fun HomeNewShelfChipV4(onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "新建书架",
            modifier = Modifier.size(18.dp),
            tint = t.mutedForeground,
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
    // V93: a flat inline panel — text choices and line-icon rows, no boxed chips.
    Column(modifier = modifier.fillMaxWidth()) {
        HomeOrganizerSectionTitleV4(text = "排序")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(t.space1),
        ) {
            LuoShelfSortV33.entries.forEach { item ->
                HomeOrganizerChoiceV4(
                    text = item.label,
                    selected = sort == item,
                    onClick = { onSort(item) },
                )
            }
        }
        HomeOrganizerSectionTitleV4(text = "显示")
        Row(horizontalArrangement = Arrangement.spacedBy(t.space1)) {
            HomeOrganizerChoiceV4(
                text = "网格",
                icon = Icons.Outlined.GridView,
                selected = layout == HomeLayoutV4.GRID,
                onClick = { onLayout(HomeLayoutV4.GRID) },
            )
            HomeOrganizerChoiceV4(
                text = "列表",
                icon = Icons.Outlined.ViewAgenda,
                selected = layout == HomeLayoutV4.LIST,
                onClick = { onLayout(HomeLayoutV4.LIST) },
            )
        }
        Spacer(Modifier.height(t.space1))
        HomeOrganizerActionV4(
            icon = Icons.Outlined.Checklist,
            title = "批量整理",
            subtitle = "多选书籍后一起移动或删除",
            onClick = onBatch,
        )
        HomeOrganizerActionV4(
            icon = Icons.Outlined.FolderOpen,
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
        modifier = Modifier.padding(top = t.space2, bottom = 2.dp),
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
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(t.radiusSm))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = t.space2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (selected) t.primary else t.mutedForeground,
            )
            Spacer(Modifier.width(t.space1))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) t.primary else t.secondaryForeground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
    FlatListRowV93(title = title, subtitle = subtitle, icon = icon, onClick = onClick)
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
    val chapterLine = buildString {
        append("第 ${item.chapterNumber} 章")
        val title = item.chapterTitle?.trim()?.takeIf { it.isNotBlank() }
        if (title != null) { append(" · "); append(title) }
    }
    val lastRead = homeRelativeTimeV91(item.lastReadAt)
    // V93: a flat resume line (small cover, text, thin progress) instead of a tinted card.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusMd))
            .clickable(onClickLabel = "继续阅读", role = Role.Button, onClick = onOpen)
            .padding(vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeBookCoverV4(
            book = item.book,
            modifier = Modifier.width(40.dp).height(54.dp),
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (lastRead != null) "继续阅读 · $lastRead" else "继续阅读",
                style = MaterialTheme.typography.labelSmall,
                color = t.primary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.book.title,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapterLine,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.chapterFraction > 0.01f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.chapterFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(2.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "本章已读 ${(item.chapterFraction * 100).roundToInt()}%" },
                    color = t.primary,
                    trackColor = t.foreground.copy(alpha = 0.08f),
                    drawStopIndicator = {},
                )
            }
        }
        Spacer(Modifier.width(t.space2))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = t.mutedForeground.copy(alpha = 0.6f),
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
    progress: ShelfReadingProgressV92,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val isEpub = remember(book.id, book.updatedAt) {
        EpubReaderEntry.isEpub(context, book.id)
    }
    val isOnline = isFollowingBookV4(book)
    // V93: flat row — cover, text and a plain ⋯; no card or border.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusMd))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onMore,
                onClickLabel = "打开",
                onLongClickLabel = "书籍菜单",
            )
            .padding(vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeBookCoverV4(
            book = book,
            modifier = Modifier.width(54.dp).height(72.dp),
        )
        Spacer(Modifier.width(t.space4))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = homeGenreLabelV4(book),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
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
            Spacer(Modifier.height(t.space1))
            Text(
                text = homeBookProgressLabelV4(context = context, state = state, book = book, shelfProgress = progress),
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
                imageVector = Icons.Outlined.MoreHoriz,
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
    progress: ShelfReadingProgressV92,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val isEpub = remember(book.id, book.updatedAt) {
        EpubReaderEntry.isEpub(context, book.id)
    }
    val online = isFollowingBookV4(book)
    val coverShape = RoundedCornerShape(HOME_COVER_RADIUS_V93)
    // The whole tile (cover + title) opens the book, like Legado / Moon+ shelves; long-press or
    // the small ⋯ beside the progress opens the book page.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onMore,
                onClickLabel = "打开",
                onLongClickLabel = "书籍菜单",
            ),
    ) {
        // V93: a tall 3:4 cover with a small radius and a soft shadow; no frame.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(HOME_COVER_ASPECT_V93)
                .shadow(elevation = 5.dp, shape = coverShape, ambientColor = Color.Black.copy(alpha = 0.16f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(coverShape)
                .background(color = t.input, shape = coverShape),
        ) {
            CoverPreviewV3(
                path = book.coverPath,
                title = book.title,
                modifier = Modifier.fillMaxSize(),
                targetWidthPx = HOME_GRID_COVER_PX_V86,
            )
            val badge = when {
                online -> "在线"
                isEpub -> "EPUB"
                else -> null
            }
            if (badge != null) {
                Text(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(t.space2))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = homeBookProgressLabelV4(context = context, state = state, book = book, shelfProgress = progress),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Compact target beside the progress line; long-press on the tile does the same.
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onMore),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "书籍菜单",
                    modifier = Modifier.size(16.dp),
                    tint = t.mutedForeground.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private val HOME_COVER_RADIUS_V93 = 5.dp
private const val HOME_COVER_ASPECT_V93 = 0.75f


/* -------------------------------------------------------------------------- */
/*                                 Cover                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun HomeBookCoverV4(
    book: ReaderBookUi,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 3.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.18f))
            .clip(shape)
            .background(color = t.input, shape = shape),
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
    // V93: a quiet text tag; colour only, no box.
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (gold) t.goldForeground else t.primary,
        fontWeight = FontWeight.Medium,
    )
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
        Icon(
            imageVector = if (query.isBlank()) Icons.AutoMirrored.Outlined.LibraryBooks else Icons.Outlined.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = t.mutedForeground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(t.space4))
        Text(
            text = when {
                query.isNotBlank() -> "没有找到「${query.trim()}」"
                libraryEmpty && activeTab == HOME_TAB_ALL_V4 -> "书架还是空的"
                activeTab == HOME_TAB_WRITING_V4 -> "还没有在写的作品"
                activeTab == HOME_TAB_FOLLOWING_V4 -> "还没有追更中的书"
                activeTab == HOME_TAB_FINISHED_V4 -> "还没有读完的书"
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
                activeTab == HOME_TAB_FINISHED_V4 -> "读到最后一章的最后一页，书就会出现在这里。"
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
                icon = Icons.Outlined.FolderOpen,
                onClick = onImport,
            )
            Spacer(Modifier.height(t.space2))
            Row(horizontalArrangement = Arrangement.spacedBy(t.space2)) {
                HomeTextActionV91(text = "开始创作", icon = Icons.Outlined.AutoAwesome, onClick = onCreate)
                HomeTextActionV91(text = "在线书城", icon = Icons.Outlined.Explore, onClick = onOnline)
            }
        } else if (query.isBlank() && activeTab != HOME_TAB_FINISHED_V4) {
            Spacer(Modifier.height(t.space4))
            HomePrimaryButtonV4(
                text = "添加书籍",
                icon = Icons.Outlined.Add,
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
                HomeTextActionV91(text = "取消导入", icon = Icons.Outlined.Close, onClick = onCancel)
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
                icon = Icons.Outlined.FolderOpen,
                title = "导入本地书籍",
                subtitle = "TXT · Markdown · EPUB",
                onClick = onImport,
            )
            Spacer(Modifier.height(t.space2))
            HomeDialogActionV4(
                icon = Icons.Outlined.AutoAwesome,
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

@Composable
private fun HomeBookActionsV4(
    context: Context,
    book: ReaderBookUi,
    progress: ShelfReadingProgressV92,
    progressLabel: String,
    chapterCount: Int,
    chapterIndex: Int,
    lastReadAt: Long,
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
    val isEpub = remember(book.id, book.updatedAt) { EpubReaderEntry.isEpub(context, book.id) }
    val format = when {
        isFollowingBookV4(book) -> "在线书源"
        isEpub -> "EPUB"
        isWritingBookV4(book) -> "创作"
        else -> "TXT"
    }
    // V93 「图书详情」: a full page in the reference's layout — cover with title fields, a large
    // whole-book progress figure, grey key/value rows, then the book actions as flat rows.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(t.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            FlatTopBarV93(title = "图书详情", onBack = onDismiss, backDescription = "关闭图书详情")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HOME_PAGE_GUTTER_V93),
            ) {
                Spacer(Modifier.height(t.space2))
                Row(verticalAlignment = Alignment.Top) {
                    HomeBookCoverV4(
                        book = book,
                        modifier = Modifier.width(76.dp).height(101.dp),
                    )
                    Spacer(Modifier.width(t.space5))
                    Column(modifier = Modifier.weight(1f)) {
                        HomeDetailFieldV93(label = "书名", value = book.title)
                        Spacer(Modifier.height(t.space3))
                        HomeDetailFieldV93(label = "分类", value = homeGenreLabelV4(book))
                    }
                }
                Spacer(Modifier.height(t.space6))
                val percent = when {
                    progress.finished -> "100"
                    progress.fraction != null -> String.format(java.util.Locale.US, "%.1f", progress.fraction.coerceIn(0f, 1f) * 100f)
                    else -> "0"
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = percent,
                        style = MaterialTheme.typography.displaySmall,
                        color = t.foreground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "%",
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = t.mutedForeground,
                    )
                    Spacer(Modifier.weight(1f))
                    if (chapterCount > 0 && chapterIndex in 0 until chapterCount) {
                        Text(
                            text = "读到 ${chapterIndex + 1} / $chapterCount 章",
                            modifier = Modifier.padding(bottom = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
                Spacer(Modifier.height(t.space2))
                LinearProgressIndicator(
                    progress = { if (progress.finished) 1f else (progress.fraction ?: 0f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape),
                    color = t.primary,
                    trackColor = t.foreground.copy(alpha = 0.08f),
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(t.space5))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.foreground.copy(alpha = 0.035f), RoundedCornerShape(t.radiusMd))
                        .padding(horizontal = t.space4, vertical = t.space2),
                ) {
                    HomeDetailKeyValueV93("文件格式", format)
                    if (book.currentWords > 0) HomeDetailKeyValueV93("全文字数", homeWordCountV93(book.currentWords))
                    if (chapterCount > 0) HomeDetailKeyValueV93("总章节数", "$chapterCount 章")
                    HomeDetailKeyValueV93("所在书架", shelf?.takeIf { it.isNotBlank() } ?: "未分组")
                    if (book.updatedAt > 0L) HomeDetailKeyValueV93("更新时间", homeDateTimeV93(book.updatedAt))
                    HomeDetailKeyValueV93("最近阅读", if (lastReadAt > 0L) homeDateTimeV93(lastReadAt) else "尚未阅读")
                }
                Spacer(Modifier.height(t.space4))
                HomeDialogActionV4(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "打开 / 继续阅读",
                    onClick = onOpen,
                )
                HomeDialogActionV4(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "进入酒馆",
                    onClick = onTavern,
                )
                HomeDialogActionV4(
                    icon = Icons.Outlined.Edit,
                    title = "修改书名",
                    onClick = onRename,
                )
                HomeDialogActionV4(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    title = "移动书架",
                    subtitle = shelf ?: "当前未分组",
                    onClick = { moveOpen = true },
                )
                HomeDialogActionV4(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "删除小说",
                    destructive = true,
                    onClick = onDelete,
                )
                Spacer(Modifier.height(t.space5))
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
}

@Composable
private fun HomeDetailFieldV93(label: String, value: String) {
    val t = LocalLanghuanUiTokens.current
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
    Spacer(Modifier.height(4.dp))
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = t.foreground,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(6.dp))
    FlatHairlineV93()
}

@Composable
private fun HomeDetailKeyValueV93(key: String, value: String) {
    val t = LocalLanghuanUiTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = key, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
        Spacer(Modifier.width(t.space4))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = t.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

private fun homeWordCountV93(words: Int): String =
    if (words >= 10_000) String.format(java.util.Locale.US, "%.1f万字", words / 10_000f) else "$words 字"

private fun homeDateTimeV93(at: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(at))

@Composable
private fun HomeDialogActionV4(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    destructive: Boolean = false,
    gold: Boolean = false,
) {
    // V93: actions are flat line-icon rows; the accent marks the AI action, red the destructive.
    FlatListRowV93(
        title = title,
        subtitle = subtitle,
        icon = icon,
        destructive = destructive,
        subtitleAccent = gold,
        chevron = false,
        onClick = onClick,
    )
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
                imageVector = Icons.Outlined.Check,
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
    shelfProgress: ShelfReadingProgressV92,
): String {
    if (isWritingBookV4(book)) {
        return if (book.currentChapter > 0) "写到第 ${book.currentChapter} 章" else "尚未开始写作"
    }
    if (!shelfProgress.started) {
        return if (isFollowingBookV4(book) && book.currentChapter > 0) {
            "更新 ${book.currentChapter} 章"
        } else {
            "未读"
        }
    }
    if (shelfProgress.finished) return "已读完"
    // Whole-book percent comes from the chapter count the reader saved with the position,
    // so it is available for every book, not only the one currently open.
    shelfProgress.percent?.takeIf { it > 0 }?.let { return "已读 $it%" }
    val progress = ReaderProgressStoreV11.load(
        context = context,
        bookId = book.id,
        fallbackChapter = book.currentChapter.coerceAtLeast(1),
    )
    if (state.openedBook?.id == book.id && state.chapters.isNotEmpty()) {
        val ordered = state.chapters.sortedBy { it.chapterNumber }
        val index = ordered.indexOfFirst { it.chapterNumber == progress.chapterNumber }
        if (index >= 0) {
            val raw = (index.toFloat() + progress.positionFraction) / ordered.size.toFloat()
            val percent = (raw.coerceIn(0f, 1f) * 100f).roundToInt()
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
            .padding(vertical = t.space2)
            .semantics { contentDescription = "正在载入书架" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanghuanSkeletonV31(Modifier.width(54.dp).height(72.dp), RoundedCornerShape(4.dp))
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
        LanghuanSkeletonV31(Modifier.fillMaxWidth().aspectRatio(HOME_COVER_ASPECT_V93), RoundedCornerShape(HOME_COVER_RADIUS_V93))
        Spacer(Modifier.height(t.space2))
        LanghuanSkeletonV31(Modifier.fillMaxWidth(0.8f).height(14.dp))
        Spacer(Modifier.height(t.space1))
        LanghuanSkeletonV31(Modifier.fillMaxWidth(0.5f).height(11.dp))
    }
}
