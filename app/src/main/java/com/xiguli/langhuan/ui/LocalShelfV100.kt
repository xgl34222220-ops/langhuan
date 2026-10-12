package com.xiguli.langhuan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.data.shelf.ShelfGroup
import com.xiguli.langhuan.data.shelf.ShelfLibrary
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.ThinIconsV102
import kotlinx.coroutines.launch

/* -------------------------------------------------------------------------- */
/*                           书架列表 ⇄ 正在阅读 pager                           */
/* -------------------------------------------------------------------------- */

private val PAGE_EASING_V102 = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

/**
 * V102 home: two horizontally swiped pages. Page 0 is 书架列表 (left), page 1 the current shelf.
 * Dragging right reveals the list (it emerges from under the shelf with parallax and a soft edge
 * shadow); tapping a shelf there selects it and slides back. Back returns from the list.
 */
@Composable
fun LocalShelfPagerV102(
    pagerState: PagerState,
    state: LocalReaderState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onLongPressBook: (String) -> Unit,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onMine: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = PAGE_EASING_V102) }
    }
    BackHandler(enabled = ShelfPagerV102.backTarget(pagerState.currentPage) != null) {
        ShelfPagerV102.backTarget(pagerState.currentPage)?.let(::goTo)
    }
    val shadowColor = Color.Black.copy(alpha = if (LocalLanghuanUiTokens.current.background.luminanceV102() < 0.5f) 0.45f else 0.12f)
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("shelf_pager"),
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapPositionalThreshold = 0.22f),
        key = { it },
    ) { page ->
        if (page == ShelfPagerV102.LIST_PAGE) {
            // The list lags inside its own (clipped) slot — parallax without ever overlapping the
            // shelf — and is dimmed plus shaded at its right edge while the shelf still covers it.
            Box(
                Modifier.fillMaxSize().clipToBounds().drawWithContent {
                    drawContent()
                    val reveal = ShelfPagerV102.listReveal(pagerState.currentPage, pagerState.currentPageOffsetFraction)
                    val dim = ShelfPagerV102.listDim(reveal)
                    if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                    val strength = ShelfPagerV102.shelfEdgeShadow(reveal)
                    if (strength > 0f) {
                        val w = 18.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, shadowColor.copy(alpha = shadowColor.alpha * strength)),
                                startX = size.width - w,
                                endX = size.width,
                            ),
                            topLeft = Offset(size.width - w, 0f),
                            size = Size(w, size.height),
                        )
                    }
                },
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val reveal = ShelfPagerV102.listReveal(pagerState.currentPage, pagerState.currentPageOffsetFraction)
                        translationX = ShelfPagerV102.listTranslation(reveal) * size.width
                    },
                ) {
                    ShelfListPageV100(
                        state = state,
                        onSelectGroup = { id ->
                            onSelectGroup(id)
                            goTo(ShelfPagerV102.SHELF_PAGE)
                        },
                        onCreateGroup = onCreateGroup,
                        onRenameGroup = onRenameGroup,
                        onDeleteGroup = onDeleteGroup,
                        onOpenBook = onOpenBook,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                LocalShelfHomeV100(
                    state = state,
                    importState = importState,
                    onOpenBook = onOpenBook,
                    onLongPressBook = onLongPressBook,
                    onImport = onImport,
                    onCancelImport = onCancelImport,
                    onMine = onMine,
                    onOpenShelfList = { goTo(ShelfPagerV102.LIST_PAGE) },
                )
            }
        }
    }
}

private fun Color.luminanceV102(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Gentle press feedback: scales the content to 96 % while pressed. */
@Composable
private fun rememberPressScaleV102(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, animationSpec = tween(140), label = "press")
    return scale
}

/* -------------------------------------------------------------------------- */
/*                                   书架                                     */
/* -------------------------------------------------------------------------- */

/**
 * 正在阅读: the shelf name big and bold top-left (tap also opens 书架列表), + import and 我的 as
 * thin line icons top-right, and a three-column 3:4 cover grid. Long-press opens the book sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalShelfHomeV100(
    state: LocalReaderState,
    importState: LocalBookImportUiStateV1,
    onOpenBook: (String) -> Unit,
    onLongPressBook: (String) -> Unit,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onMine: () -> Unit,
    onOpenShelfList: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val group = state.shelf.groups.firstOrNull { it.id == state.currentGroupId }
        ?: state.shelf.groups.first()
    val books = state.books.filter { it.groupId == group.id }
    Column(Modifier.fillMaxSize().background(t.background).statusBarsPadding().testTag("shelf_home")) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.name,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenShelfList)
                    .semantics { contentDescription = "${group.name}，打开书架列表" }
                    .testTag("shelf_title").padding(vertical = 4.dp),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onImport, modifier = Modifier.testTag("shelf_import")) {
                Icon(ThinIconsV102.Add, contentDescription = "导入本地书籍", modifier = Modifier.size(25.dp), tint = t.foreground)
            }
            IconButton(onClick = onMine, modifier = Modifier.testTag("shelf_mine")) {
                Icon(ThinIconsV102.Person, contentDescription = "我的", modifier = Modifier.size(25.dp), tint = t.foreground)
            }
        }
        AnimatedVisibility(visible = importState.busy, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            ImportBannerV100(importState, onCancelImport)
        }
        if (!state.libraryLoaded) {
            Spacer(Modifier.weight(1f))
        } else if (books.isEmpty()) {
            EmptyShelfV100(onImport = onImport, defaultShelf = group.id == ShelfLibrary.DEFAULT_GROUP_ID, modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().testTag("shelf_grid"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    val source = remember { MutableInteractionSource() }
                    val scale = rememberPressScaleV102(source)
                    Column(
                        Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .combinedClickable(
                                interactionSource = source,
                                indication = null,
                                onClick = { onOpenBook(book.id) },
                                // combinedClickable plays the long-press haptic itself.
                                onLongClick = { onLongPressBook(book.id) },
                            )
                            .testTag("shelf_book_${book.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ShelfCoverV102(book, Modifier.fillMaxWidth())
                        Text(
                            text = book.title,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = t.foreground,
                        )
                        val caption = when {
                            book.progress.finished -> "已读完"
                            book.progress.percent != null && book.progress.started -> "已读 ${book.progress.percent}%"
                            else -> null
                        }
                        if (caption != null) {
                            Text(
                                text = caption,
                                modifier = Modifier.padding(top = 2.dp),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = t.mutedForeground,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A shelf cover with the soft printed-book shadow (Apple Books / 微信读书 style). */
@Composable
private fun ShelfCoverV102(book: LocalShelfBook, modifier: Modifier = Modifier, targetWidthPx: Int = 360) {
    val shape = RoundedCornerShape(COVER_RADIUS_V102)
    val dark = LocalLanghuanUiTokens.current.background.luminanceV102() < 0.5f
    BookCoverV100(
        book,
        modifier
            .aspectRatio(BOOK_COVER_RATIO_V100)
            .shadow(
                elevation = if (dark) 0.dp else 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.22f),
            ),
        targetWidthPx = targetWidthPx,
    )
}

@Composable
private fun ImportBannerV100(importState: LocalBookImportUiStateV1, onCancel: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(t.input, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = t.primary, trackColor = Color.Transparent)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "正在导入 ${importState.currentFileName}".trim() + "…",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = t.secondaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (importState.canCancel) TextButton(onClick = onCancel) { Text("取消", color = t.primary) }
        else Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun EmptyShelfV100(onImport: () -> Unit, defaultShelf: Boolean, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(76.dp).background(t.muted, CircleShape), contentAlignment = Alignment.Center) {
            Icon(ThinIconsV102.Book, contentDescription = null, modifier = Modifier.size(32.dp), tint = t.mutedForeground)
        }
        Text(
            if (defaultShelf) "书架还是空的" else "这个书架还没有书",
            modifier = Modifier.padding(top = 20.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = t.foreground,
        )
        Text(
            "支持导入 TXT、EPUB 等本地文件",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            color = t.mutedForeground,
        )
        Box(
            Modifier.padding(top = 22.dp).height(42.dp).clip(CircleShape).background(t.primary)
                .clickable(onClick = onImport).testTag("shelf_empty_import").padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("导入本地书籍", color = t.primaryForeground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                 书架列表                                   */
/* -------------------------------------------------------------------------- */

/**
 * 书架列表: big bold title with search and + on the right; each shelf is a row with a square
 * checkbox (filled blue for the current shelf), the bold name, a gray count and ···, followed by
 * a strip of its covers. Search filters shelves and books by name, title or author.
 */
@Composable
fun ShelfListPageV100(
    state: LocalReaderState,
    onSelectGroup: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var creating by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ShelfGroup?>(null) }
    var deleting by remember { mutableStateOf<ShelfGroup?>(null) }
    val counts = ShelfLibrary.counts(state.shelf)
    val needle = query.trim()
    Column(Modifier.fillMaxSize().background(t.background).statusBarsPadding().testTag("shelf_list_page")) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) {
                Row(
                    Modifier.weight(1f).height(36.dp).background(t.input, CircleShape).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ThinIconsV102.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = t.mutedForeground)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("搜索书架、书名或作者", fontSize = 14.sp, color = t.mutedForeground)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it.take(40) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = t.foreground),
                            cursorBrush = SolidColor(t.primary),
                            modifier = Modifier.fillMaxWidth().testTag("shelf_list_query"),
                        )
                    }
                }
                TextButton(onClick = { searching = false; query = "" }) { Text("取消", color = t.primary, fontSize = 15.sp) }
            } else {
                Text("书架列表", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = t.foreground, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = true }, modifier = Modifier.testTag("shelf_list_search")) {
                    Icon(ThinIconsV102.Search, contentDescription = "搜索", modifier = Modifier.size(24.dp), tint = t.foreground)
                }
                IconButton(onClick = { creating = true }, modifier = Modifier.testTag("shelf_list_add")) {
                    Icon(ThinIconsV102.Add, contentDescription = "新建书架", modifier = Modifier.size(25.dp), tint = t.foreground)
                }
            }
        }
        val rows = state.shelf.groups.mapNotNull { group ->
            val all = state.books.filter { it.groupId == group.id }
            if (needle.isEmpty()) group to all
            else {
                val hits = all.filter { it.title.contains(needle, ignoreCase = true) || it.author.contains(needle, ignoreCase = true) }
                if (group.name.contains(needle, ignoreCase = true)) group to all
                else if (hits.isNotEmpty()) group to hits
                else null
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
            items(rows, key = { it.first.id }) { (group, books) ->
                ShelfGroupRowV100(
                    group = group,
                    count = counts[group.id] ?: 0,
                    selected = group.id == state.currentGroupId,
                    books = books,
                    onClick = { onSelectGroup(group.id) },
                    onRename = { renaming = group },
                    onDelete = { deleting = group },
                    onOpenBook = onOpenBook,
                )
            }
            if (rows.isEmpty()) item {
                Text(
                    "没有找到「$needle」",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = t.mutedForeground,
                )
            }
        }
    }
    if (creating) {
        ShelfNameDialogV100(title = "新建书架", initial = "", onDismiss = { creating = false }) { name ->
            creating = false
            onCreateGroup(name)
        }
    }
    renaming?.let { group ->
        ShelfNameDialogV100(title = "重命名书架", initial = group.name, onDismiss = { renaming = null }) { name ->
            renaming = null
            onRenameGroup(group.id, name)
        }
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = t.card,
            title = { Text("删除书架「${group.name}」？", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("书架里的书不会被删除，会移到「${ShelfLibrary.DEFAULT_GROUP_NAME}」。", color = t.secondaryForeground) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDeleteGroup(group.id) }) {
                    Text("删除", color = t.destructive)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消", color = t.secondaryForeground) } },
        )
    }
}

@Composable
private fun ShelfCheckboxV102(checked: Boolean) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(18.dp).clip(shape)
            .background(if (checked) t.primary else Color.Transparent)
            .border(1.3.dp, if (checked) t.primary else t.mutedForeground.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(ThinIconsV102.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = t.primaryForeground)
    }
}

@Composable
private fun ShelfGroupRowV100(
    group: ShelfGroup,
    count: Int,
    selected: Boolean,
    books: List<LocalShelfBook>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("shelf_group_${group.id}")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfCheckboxV102(selected)
            Spacer(Modifier.width(12.dp))
            Text(
                group.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).testTag("shelf_group_name_${group.id}"),
            )
            Text("（$count）", fontSize = 13.sp, color = t.mutedForeground)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("shelf_group_menu_${group.id}")) {
                    Icon(ThinIconsV102.More, contentDescription = "书架操作", tint = t.secondaryForeground)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = t.card) {
                    DropdownMenuItem(text = { Text("打开") }, onClick = { menu = false; onClick() })
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                    if (group.id != ShelfLibrary.DEFAULT_GROUP_ID) {
                        DropdownMenuItem(text = { Text("删除书架", color = t.destructive) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        if (books.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    val source = remember { MutableInteractionSource() }
                    val scale = rememberPressScaleV102(source)
                    Box(
                        Modifier.width(64.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                            .clickable(interactionSource = source, indication = null) { onOpenBook(book.id) },
                    ) {
                        BookCoverV100(book, Modifier.fillMaxWidth().aspectRatio(BOOK_COVER_RATIO_V100), targetWidthPx = 200)
                    }
                }
            }
        } else Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(start = 20.dp).fillMaxWidth().height(0.5.dp).background(t.border))
    }
}

@Composable
fun ShelfNameDialogV100(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val t = LocalLanghuanUiTokens.current
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ShelfLibrary.MAX_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("书架名称", color = t.mutedForeground) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = t.primary,
                    unfocusedBorderColor = t.border,
                    cursorColor = t.primary,
                ),
                modifier = Modifier.testTag("shelf_name_field"),
            )
        },
        confirmButton = {
            TextButton(enabled = ShelfLibrary.cleanName(name) != null, onClick = { onConfirm(name) }, modifier = Modifier.testTag("shelf_name_confirm")) {
                Text("确定", color = if (ShelfLibrary.cleanName(name) != null) t.primary else t.mutedForeground)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = t.secondaryForeground) } },
    )
}

/* -------------------------------------------------------------------------- */
/*                              Long-press sheets                             */
/* -------------------------------------------------------------------------- */

private val SHEET_SHAPE_V102 = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
private const val SHEET_SCRIM_V102 = 0.40f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionSheetV100(
    book: LocalShelfBook,
    onDismiss: () -> Unit,
    onDetail: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        dragHandle = null,
        shape = SHEET_SHAPE_V102,
        containerColor = t.card,
        scrimColor = Color.Black.copy(alpha = SHEET_SCRIM_V102),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("book_action_sheet")) {
            Text(
                text = book.title,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp, start = 24.dp, end = 24.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SheetRowV100(ThinIconsV102.Detail, "查看详情", "sheet_detail", onDetail)
            SheetRowV100(ThinIconsV102.Move, "移动书架", "sheet_move", onMove)
            SheetRowV100(ThinIconsV102.Delete, "删除图书", "sheet_delete", onDelete)
            SheetCancelV102(onDismiss)
        }
    }
}

@Composable
private fun SheetCancelV102(onDismiss: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp).height(48.dp),
        shape = CircleShape,
        color = t.input,
    ) {
        Box(contentAlignment = Alignment.Center) { Text("取消", fontSize = 15.sp, color = t.foreground) }
    }
}

@Composable
private fun SheetRowV100(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = t.foreground)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 15.sp, color = t.foreground)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveShelfSheetV100(
    book: LocalShelfBook,
    groups: List<ShelfGroup>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var creating by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        dragHandle = null,
        shape = SHEET_SHAPE_V102,
        containerColor = t.card,
        scrimColor = Color.Black.copy(alpha = SHEET_SCRIM_V102),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("move_shelf_sheet")) {
            Text(
                "移动到书架",
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.foreground,
            )
            groups.forEach { group ->
                val current = group.id == book.groupId
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(group.id) }.testTag("move_to_${group.id}")
                        .padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.name,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                        color = if (current) t.primary else t.foreground,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (current) Icon(ThinIconsV102.Check, contentDescription = "当前书架", modifier = Modifier.size(20.dp), tint = t.primary)
                }
            }
            SheetRowV100(ThinIconsV102.Add, "新建书架", "move_new_shelf") { creating = true }
            SheetCancelV102(onDismiss)
        }
    }
    if (creating) {
        ShelfNameDialogV100(title = "新建书架", initial = "", onDismiss = { creating = false }) { name ->
            creating = false
            onCreate(name)
        }
    }
}

@Composable
fun DeleteBookDialogV100(book: LocalShelfBook, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.card,
        title = { Text("删除《${book.title}》？", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text("将从书架移除这本书并删除导入的本地文本，此操作无法撤销。", color = t.secondaryForeground) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_book_confirm")) {
                Text("删除", color = t.destructive)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = t.secondaryForeground) } },
    )
}

internal val COVER_RADIUS_V102 = 3.dp
