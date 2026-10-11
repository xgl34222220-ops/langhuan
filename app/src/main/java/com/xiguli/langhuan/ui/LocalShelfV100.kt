package com.xiguli.langhuan.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiguli.langhuan.data.shelf.ShelfGroup
import com.xiguli.langhuan.data.shelf.ShelfLibrary

/* -------------------------------------------------------------------------- */
/*                                   书架                                     */
/* -------------------------------------------------------------------------- */

/**
 * Home: the current shelf's name top-left (tap for 书架列表), import and 我的 top-right, and a
 * three-column cover grid. Long-press opens the book sheet.
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
    val group = state.shelf.groups.firstOrNull { it.id == state.currentGroupId }
        ?: state.shelf.groups.first()
    val books = state.books.filter { it.groupId == group.id }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenShelfList)
                    .testTag("shelf_title").padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "书架列表", modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onImport, modifier = Modifier.testTag("shelf_import")) {
                Icon(Icons.Outlined.Add, contentDescription = "导入本地书籍", modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onMine, modifier = Modifier.testTag("shelf_mine")) {
                Icon(Icons.Outlined.PersonOutline, contentDescription = "我的", modifier = Modifier.size(26.dp))
            }
        }
        if (importState.busy) ImportBannerV100(importState, onCancelImport)
        if (!state.libraryLoaded) {
            Spacer(Modifier.weight(1f))
        } else if (books.isEmpty()) {
            EmptyShelfV100(onImport = onImport, defaultShelf = group.id == ShelfLibrary.DEFAULT_GROUP_ID, modifier = Modifier.weight(1f))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().testTag("shelf_grid"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    Column(
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .combinedClickable(onClick = { onOpenBook(book.id) }, onLongClick = { onLongPressBook(book.id) })
                            .testTag("shelf_book_${book.id}"),
                    ) {
                        Box {
                            BookCoverV100(book, Modifier.fillMaxWidth().aspectRatio(BOOK_COVER_RATIO_V100))
                            val percent = book.progress.percent
                            if (book.progress.finished || percent != null) {
                                Text(
                                    text = if (book.progress.finished) "读完" else "$percent%",
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                        Text(
                            text = book.title,
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportBannerV100(importState: LocalBookImportUiStateV1, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "正在导入 ${importState.currentFileName}".trim() + "…",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (importState.canCancel) TextButton(onClick = onCancel) { Text("取消") }
        else Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun EmptyShelfV100(onImport: () -> Unit, defaultShelf: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (defaultShelf) "书架还是空的" else "这个书架还没有书", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "支持导入 TXT、EPUB 等本地文件",
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onImport, modifier = Modifier.padding(top = 12.dp).testTag("shelf_empty_import")) { Text("导入本地书籍") }
    }
}

/* -------------------------------------------------------------------------- */
/*                                 书架列表                                   */
/* -------------------------------------------------------------------------- */

@Composable
fun ShelfListPageV100(
    state: LocalReaderState,
    onBack: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onCreateGroup: (String) -> Unit,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onOpenBook: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ShelfGroup?>(null) }
    var deleting by remember { mutableStateOf<ShelfGroup?>(null) }
    val counts = ShelfLibrary.counts(state.shelf)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().testTag("shelf_list_page")) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
            Text("书架列表", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { creating = true }, modifier = Modifier.testTag("shelf_list_add")) {
                Icon(Icons.Outlined.Add, contentDescription = "新建书架", modifier = Modifier.size(26.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            items(state.shelf.groups, key = { it.id }) { group ->
                val books = state.books.filter { it.groupId == group.id }
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
            title = { Text("删除书架「${group.name}」？") },
            text = { Text("书架里的书不会被删除，会移到「${ShelfLibrary.DEFAULT_GROUP_NAME}」。") },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDeleteGroup(group.id) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
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
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("shelf_group_${group.id}")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(group.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
            Text("（$count）", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("shelf_group_menu_${group.id}")) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "书架操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("打开") }, onClick = { menu = false; onClick() })
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                    if (group.id != ShelfLibrary.DEFAULT_GROUP_ID) {
                        DropdownMenuItem(text = { Text("删除书架", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        if (books.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    BookCoverV100(
                        book,
                        Modifier.width(76.dp).aspectRatio(BOOK_COVER_RATIO_V100).clickable { onOpenBook(book.id) },
                        targetWidthPx = 220,
                    )
                }
            }
        } else Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun ShelfNameDialogV100(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ShelfLibrary.MAX_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("书架名称") },
                modifier = Modifier.testTag("shelf_name_field"),
            )
        },
        confirmButton = {
            TextButton(enabled = ShelfLibrary.cleanName(name) != null, onClick = { onConfirm(name) }, modifier = Modifier.testTag("shelf_name_confirm")) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/* -------------------------------------------------------------------------- */
/*                              Long-press sheets                             */
/* -------------------------------------------------------------------------- */

@Composable
fun BookActionSheetV100(
    book: LocalShelfBook,
    onDismiss: () -> Unit,
    onDetail: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("book_action_sheet")) {
            Text(
                text = book.title,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SheetRowV100(Icons.Outlined.Info, "查看详情", "sheet_detail", onDetail)
            SheetRowV100(Icons.AutoMirrored.Outlined.DriveFileMove, "移动书架", "sheet_move", onMove)
            SheetRowV100(Icons.Outlined.DeleteOutline, "删除图书", "sheet_delete", onDelete)
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("取消", fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun SheetRowV100(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, fontSize = 15.sp)
    }
}

@Composable
fun MoveShelfSheetV100(
    book: LocalShelfBook,
    groups: List<ShelfGroup>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().testTag("move_shelf_sheet")) {
            Text(
                "移动到书架",
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            groups.forEach { group ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(group.id) }.testTag("move_to_${group.id}")
                        .padding(horizontal = 28.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (group.id == book.groupId) Icon(Icons.Outlined.Check, contentDescription = "当前书架", tint = MaterialTheme.colorScheme.primary)
                }
            }
            SheetRowV100(Icons.Outlined.Add, "新建书架", "move_new_shelf") { creating = true }
            Surface(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("取消", fontSize = 15.sp) }
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除《${book.title}》？") },
        text = { Text("将从书架移除这本书并删除导入的本地文本，此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_book_confirm")) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
