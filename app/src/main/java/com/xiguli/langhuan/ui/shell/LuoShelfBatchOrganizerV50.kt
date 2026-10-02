package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * 琅嬛书架批量整理 · V50
 *
 * 功能：
 * 1. 进入独立批量选择模式。
 * 2. 多选书籍。
 * 3. 全选 / 取消全选。
 * 4. 批量移动到已有自定义书架。
 * 5. 批量移回「全部」。
 * 6. 批量删除。
 * 7. 删除后 Undo。
 *
 * 删除 Undo 的实现原则非常重要：
 * 当前仓库的 LibraryExperienceViewModel.deleteBook(id) 会真正删除
 * chapter_versions / chapter_state / memory_chunks / story_state / 本地封面，
 * 这条现有链路本身不可恢复。
 * 因此这里不先调用真正删除再伪造"恢复"。
 * 正确做法是：用户点击删除 → 进入 pending delete → UI 立即隐藏这些书 →
 * 6 秒 Undo 窗口 → Undo 取消 pending 不删除任何数据库数据；
 * 6 秒结束才调用 onDeleteBooks(ids)。这样 Undo 是真正安全的。
 *
 * v3 UI：LocalLanghuanUiTokens、1dp border、无 shadow、radius 8/12/16/24、space1~space6。
 */

/* -------------------------------------------------------------------------- */
/*                               Constants                                    */
/* -------------------------------------------------------------------------- */

private const val LUO_BATCH_DELETE_UNDO_MS_V50 = 6_000L

/* -------------------------------------------------------------------------- */
/*                         Batch Selection State                              */
/* -------------------------------------------------------------------------- */

@Stable
internal class LuoShelfBatchSelectionStateV50 {

    var active by mutableStateOf(false)
        private set

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val selectedCount: Int
        get() = selectedIds.size

    fun enter(initialBookId: String? = null) {
        active = true
        selectedIds = initialBookId
            ?.takeIf { it.isNotBlank() }
            ?.let { setOf(it) }
            ?: emptySet()
    }

    fun exit() {
        active = false
        selectedIds = emptySet()
    }

    fun clear() {
        selectedIds = emptySet()
    }

    fun toggle(bookId: String) {
        if (bookId.isBlank()) return
        selectedIds = if (bookId in selectedIds) selectedIds - bookId else selectedIds + bookId
    }

    fun select(bookId: String) {
        if (bookId.isBlank()) return
        selectedIds = selectedIds + bookId
    }

    fun deselect(bookId: String) {
        selectedIds = selectedIds - bookId
    }

    fun selectAll(bookIds: Collection<String>) {
        selectedIds = selectedIds + bookIds.filter { it.isNotBlank() }
    }

    fun deselectAll(bookIds: Collection<String>) {
        selectedIds = selectedIds - bookIds.toSet()
    }

    fun retainAvailable(availableIds: Set<String>) {
        selectedIds = selectedIds.intersect(availableIds)
    }
}

@Composable
internal fun rememberLuoShelfBatchSelectionStateV50(): LuoShelfBatchSelectionStateV50 =
    remember { LuoShelfBatchSelectionStateV50() }

/* -------------------------------------------------------------------------- */
/*                             Pending Delete                                 */
/* -------------------------------------------------------------------------- */

@Stable
private class LuoShelfBatchDeleteUndoV50(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {

    var pendingBooks by mutableStateOf<List<ReaderBookUi>>(emptyList())
        private set

    var deadlineMillis by mutableStateOf(0L)
        private set

    private var commitCallback: (List<String>) -> Unit = {}
    private var pendingChangedCallback: (Set<String>) -> Unit = {}
    private var commitRunnable: Runnable? = null

    val active: Boolean
        get() = pendingBooks.isNotEmpty()

    val pendingIds: Set<String>
        get() = pendingBooks.mapTo(linkedSetOf()) { it.id }

    fun bind(
        onCommit: (List<String>) -> Unit,
        onPendingChanged: (Set<String>) -> Unit,
    ) {
        commitCallback = onCommit
        pendingChangedCallback = onPendingChanged
    }

    /**
     * 如果前面还有一组等待删除，新删除开始前先提交上一组。
     * 这样不会出现多组 Undo 状态相互覆盖。
     */
    fun stage(books: List<ReaderBookUi>) {
        val valid = books.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (valid.isEmpty()) return
        if (pendingBooks.isNotEmpty()) commitNow()
        commitRunnable?.let { handler.removeCallbacks(it) }
        pendingBooks = valid
        deadlineMillis = System.currentTimeMillis() + LUO_BATCH_DELETE_UNDO_MS_V50
        pendingChangedCallback(pendingIds)
        val runnable = Runnable { commitNow() }
        commitRunnable = runnable
        handler.postDelayed(runnable, LUO_BATCH_DELETE_UNDO_MS_V50)
    }

    fun undo() {
        commitRunnable?.let { handler.removeCallbacks(it) }
        commitRunnable = null
        pendingBooks = emptyList()
        deadlineMillis = 0L
        pendingChangedCallback(emptySet())
    }

    fun commitNow() {
        val ids = pendingBooks.map { it.id }
        if (ids.isEmpty()) {
            clearInternal()
            return
        }
        commitRunnable?.let { handler.removeCallbacks(it) }
        commitRunnable = null
        /* 先拿快照，再清 UI pending。 */
        pendingBooks = emptyList()
        deadlineMillis = 0L
        pendingChangedCallback(emptySet())
        commitCallback(ids)
    }

    /**
     * 组件销毁时不取消已经 stage 的真正删除。
     * Handler 中的 Runnable 会在 6 秒后提交，
     * 避免用户关闭整理面板后 pending 删除永久丢失。
     */
    private fun clearInternal() {
        commitRunnable?.let { handler.removeCallbacks(it) }
        commitRunnable = null
        pendingBooks = emptyList()
        deadlineMillis = 0L
        pendingChangedCallback(emptySet())
    }
}

/* -------------------------------------------------------------------------- */
/*                            Main Organizer                                  */
/* -------------------------------------------------------------------------- */

/**
 * 书架批量整理主面板。
 *
 * [books] 当前书架可整理的全部书籍。
 * [shelves] 已存在的自定义书架名称。
 * [shelfPrefs] 与 LuoShelfAssignmentsV33 使用同一个 SharedPreferences。
 * [onDeleteBooks] Undo 窗口结束后才会被调用。
 * 由于当前仓库只有单本 viewModel.deleteBook(id)，上层接入时可：
 * onDeleteBooks = { ids -> ids.forEach(viewModel::deleteBook) }
 * 后续如果 ViewModel 增加真正的事务级批量删除，只需替换这个 callback。
 * [onPendingDeleteChanged] 用于让外部书架立即隐藏 pending 删除的书。
 * 推荐 Home：var pendingIds by remember { mutableStateOf(emptySet<String>()) }，
 * 展示列表：books.filterNot { it.id in pendingIds }，
 * 这样删除后的 Undo 视觉反馈能同步到整理面板之外。
 */
@Composable
internal fun LuoShelfBatchOrganizerV50(
    books: List<ReaderBookUi>,
    shelves: List<String>,
    shelfPrefs: SharedPreferences,
    visible: Boolean,
    onDismiss: () -> Unit,
    onDeleteBooks: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onPendingDeleteChanged: (Set<String>) -> Unit = {},
    onMoveCompleted: (bookIds: Set<String>, shelf: String?) -> Unit = { _, _ -> },
) {
    if (!visible) return

    val t = LocalLanghuanUiTokens.current
    val selection = rememberLuoShelfBatchSelectionStateV50()
    var assignmentVersion by remember { mutableIntStateOf(0) }
    var movePanel by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val latestDeleteCallback = rememberUpdatedState(onDeleteBooks)
    val latestPendingCallback = rememberUpdatedState(onPendingDeleteChanged)
    val deleteUndo = remember { LuoShelfBatchDeleteUndoV50() }

    /* 不让 Handler 持有旧 callback。 */
    deleteUndo.bind(
        onCommit = { latestDeleteCallback.value(it) },
        onPendingChanged = { latestPendingCallback.value(it) },
    )

    /* Dialog 第一次显示即进入批量模式。 */
    DisposableEffect(visible) {
        selection.enter()
        onDispose { selection.exit() }
    }

    val pendingIds = deleteUndo.pendingIds
    val visibleBooks = remember(books, pendingIds) {
        books.filterNot { it.id in pendingIds }
    }

    /* 外部数据更新后清理不存在的 selection。 */
    val availableIds = remember(visibleBooks) {
        visibleBooks.mapTo(linkedSetOf()) { it.id }
    }
    selection.retainAvailable(availableIds)

    val selectedBooks = remember(visibleBooks, selection.selectedIds) {
        visibleBooks.filter { it.id in selection.selectedIds }
    }

    val allSelected = visibleBooks.isNotEmpty() &&
        visibleBooks.all { it.id in selection.selectedIds }

    val assignments = remember(shelfPrefs, assignmentVersion) {
        LuoShelfAssignmentsV33.all(shelfPrefs)
    }

    Dialog(
        onDismissRequest = {
            if (!confirmDelete && !movePanel) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(t.background)
                    .statusBarsPadding(),
            ) {

                /* ---------------- Top bar ---------------- */

                LuoShelfBatchTopBarV50(
                    totalBooks = visibleBooks.size,
                    selectedCount = selection.selectedCount,
                    allSelected = allSelected,
                    onToggleAll = {
                        if (allSelected) {
                            selection.deselectAll(visibleBooks.map { it.id })
                        } else {
                            selection.selectAll(visibleBooks.map { it.id })
                        }
                    },
                    onClose = { onDismiss() },
                )

                /* ---------------- List ---------------- */

                if (visibleBooks.isEmpty()) {
                    LuoShelfBatchEmptyV50(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = t.space4,
                            end = t.space4,
                            top = t.space2,
                            bottom = t.space4,
                        ),
                        verticalArrangement = Arrangement.spacedBy(t.space2),
                    ) {
                        items(items = visibleBooks, key = { it.id }) { book ->
                            LuoShelfBatchBookRowV50(
                                book = book,
                                shelf = assignments[book.id],
                                selected = book.id in selection.selectedIds,
                                onClick = { selection.toggle(book.id) },
                            )
                        }
                    }
                }

                /* ---------------- Bottom actions ---------------- */

                LuoShelfBatchActionBarV50(
                    selectedCount = selection.selectedCount,
                    onMove = { if (selection.selectedCount > 0) movePanel = true },
                    onDelete = { if (selection.selectedCount > 0) confirmDelete = true },
                )
            }

            /* ---------------- Undo ---------------- */

            AnimatedVisibility(
                visible = deleteUndo.active,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = t.space4, vertical = t.space4),
                enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 2 },
            ) {
                LuoShelfUndoBarV50(
                    count = deleteUndo.pendingBooks.size,
                    onUndo = { deleteUndo.undo() },
                )
            }

            /* ---------------- Move panel ---------------- */

            if (movePanel) {
                LuoShelfBatchMoveDialogV50(
                    shelves = shelves,
                    selectedCount = selectedBooks.size,
                    onDismiss = { movePanel = false },
                    onMove = { target ->
                        val ids = selectedBooks.mapTo(linkedSetOf()) { it.id }
                        ids.forEach {
                            LuoShelfAssignmentsV33.assign(
                                prefs = shelfPrefs,
                                bookId = it,
                                shelf = target,
                            )
                        }
                        assignmentVersion++
                        selection.clear()
                        movePanel = false
                        onMoveCompleted(ids, target)
                    },
                )
            }

            /* ---------------- Delete confirm ---------------- */

            if (confirmDelete) {
                LuoShelfBatchDeleteConfirmV50(
                    books = selectedBooks,
                    onDismiss = { confirmDelete = false },
                    onConfirm = {
                        val deleting = selectedBooks.toList()
                        confirmDelete = false
                        selection.clear()
                        deleteUndo.stage(deleting)
                    },
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                               Top Bar                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchTopBarV50(
    totalBooks: Int,
    selectedCount: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = t.space4, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LuoShelfBatchIconButtonV50(
            icon = Icons.Rounded.Close,
            contentDescription = "退出整理",
            onClick = onClose,
        )
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "整理书架",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = if (selectedCount > 0) {
                    "已选择 $selectedCount 本 · 共 $totalBooks 本"
                } else {
                    "选择要整理的书籍 · 共 $totalBooks 本"
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
            )
        }
        LuoShelfBatchTextActionV50(
            text = if (allSelected) "取消全选" else "全选",
            icon = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
            onClick = onToggleAll,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                              Book Row                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchBookRowV50(
    book: ReaderBookUi,
    shelf: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val interaction = remember { MutableInteractionSource() }

    val background by animateColorAsState(
        targetValue = if (selected) t.accent else t.card,
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "batchBookBackground",
    )
    val border by animateColorAsState(
        targetValue = if (selected) t.primary else t.border,
        animationSpec = tween(LanghuanMotionV31.MEDIUM),
        label = "batchBookBorder",
    )
    val shape = RoundedCornerShape(t.radiusLg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = background, shape = shape)
            .border(width = 1.dp, color = border, shape = shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /* Book mark */
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (selected) t.primary else t.input,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = if (selected) t.primary else t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = t.card,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = t.secondaryForeground,
                )
            }
        }
        Spacer(Modifier.width(t.space3))
        /* Info */
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
            Text(
                text = if (book.genre == "导入作品") {
                    "本地书籍 · 第 ${book.currentChapter} 章"
                } else {
                    "${book.genre} · 第 ${book.currentChapter} 章"
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(t.space2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (shelf.isNullOrBlank()) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = t.mutedForeground,
                )
                Spacer(Modifier.width(t.space1))
                Text(
                    text = shelf?.takeIf { it.isNotBlank() } ?: "未分组",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(t.space3))
        /* Selection indicator */
        Icon(
            imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (selected) "已选择" else "未选择",
            modifier = Modifier.size(23.dp),
            tint = if (selected) t.primary else t.mutedForeground,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                           Bottom Action Bar                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchActionBarV50(
    selectedCount: Int,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val enabled = selectedCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.background)
            .border(
                width = 1.dp,
                color = t.border,
                shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
            )
            .navigationBarsPadding()
            .padding(horizontal = t.space4, vertical = t.space3),
    ) {
        if (selectedCount > 0) {
            Text(
                text = "已选择 $selectedCount 本",
                style = MaterialTheme.typography.labelMedium,
                color = t.secondaryForeground,
            )
            Spacer(Modifier.height(t.space2))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(t.space2),
        ) {
            LuoShelfBatchButtonV50(
                text = "移动",
                icon = Icons.Rounded.FolderOpen,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = onMove,
            )
            LuoShelfBatchButtonV50(
                text = "删除",
                icon = Icons.Rounded.DeleteOutline,
                enabled = enabled,
                destructive = true,
                modifier = Modifier.weight(1f),
                onClick = onDelete,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                              Move Dialog                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchMoveDialogV50(
    shelves: List<String>,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = t.background, shape = shape)
                    .border(width = 1.dp, color = t.border, shape = shape)
                    .navigationBarsPadding()
                    .padding(t.space4),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color = t.accent, shape = RoundedCornerShape(t.radiusMd))
                            .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = t.accentForeground,
                        )
                    }
                    Spacer(Modifier.width(t.space3))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "移动到书架",
                            style = MaterialTheme.typography.titleLarge,
                            color = t.foreground,
                        )
                        Spacer(Modifier.height(t.space1))
                        Text(
                            text = "将 $selectedCount 本书一起移动",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.secondaryForeground,
                        )
                    }
                    LuoShelfBatchIconButtonV50(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        onClick = onDismiss,
                    )
                }
                Spacer(Modifier.height(t.space4))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(t.space2),
                ) {
                    item {
                        LuoShelfBatchDestinationV50(
                            icon = Icons.Rounded.FolderOff,
                            label = "不放入自定义书架",
                            subtitle = "仅保留在「全部」中",
                            onClick = { onMove(null) },
                        )
                    }
                    items(items = shelves.distinct(), key = { it }) { shelf ->
                        LuoShelfBatchDestinationV50(
                            icon = Icons.Rounded.Folder,
                            label = shelf,
                            subtitle = "移动 $selectedCount 本",
                            onClick = { onMove(shelf) },
                        )
                    }
                }
                if (shelves.isEmpty()) {
                    Spacer(Modifier.height(t.space3))
                    Text(
                        text = "还没有自定义书架。可先在书架管理中创建分组。",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                            Destination Row                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchDestinationV50(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.card, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .clickable(onClick = onClick)
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color = t.input, shape = RoundedCornerShape(t.radiusMd))
                .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = t.secondaryForeground,
            )
        }
        Spacer(Modifier.width(t.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
/*                         Delete Confirmation                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchDeleteConfirmV50(
    books: List<ReaderBookUi>,
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = t.destructive.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(t.radiusMd),
                    )
                    .border(
                        width = 1.dp,
                        color = t.destructive.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(t.radiusMd),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = t.destructive,
                )
            }
            Spacer(Modifier.height(t.space4))
            Text(
                text = "删除 ${books.size} 本书？",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
            )
            Spacer(Modifier.height(t.space2))
            Text(
                text = "删除会移除这些作品的正文、章节版本、长期记忆和本地封面。确认后仍有 6 秒可以撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = t.secondaryForeground,
            )
            if (books.isNotEmpty()) {
                Spacer(Modifier.height(t.space3))
                val preview = books.take(3).joinToString("、") { "《${it.title}》" }
                Text(
                    text = if (books.size > 3) "$preview 等 ${books.size} 本" else preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(t.space5))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(t.space2),
            ) {
                LuoShelfBatchButtonV50(
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                LuoShelfBatchButtonV50(
                    text = "删除",
                    icon = Icons.Rounded.DeleteOutline,
                    destructive = true,
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                                Undo Bar                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfUndoBarV50(
    count: Int,
    onUndo: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = t.foreground, shape = shape)
            .border(width = 1.dp, color = t.border, shape = shape)
            .padding(horizontal = t.space4, vertical = t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已移出 $count 本书",
                style = MaterialTheme.typography.bodyMedium,
                color = t.background,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(t.space1))
            Text(
                text = "6 秒内可撤销",
                style = MaterialTheme.typography.bodySmall,
                color = t.background.copy(alpha = 0.68f),
            )
        }
        Row(
            modifier = Modifier
                .clickable(onClick = onUndo)
                .padding(horizontal = t.space2, vertical = t.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Undo,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = t.gold,
            )
            Spacer(Modifier.width(t.space1))
            Text(
                text = "撤销",
                style = MaterialTheme.typography.labelLarge,
                color = t.gold,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*                               Empty State                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchEmptyV50(modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(t.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = t.input, shape = RoundedCornerShape(t.radiusLg))
                .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusLg)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = t.mutedForeground,
            )
        }
        Spacer(Modifier.height(t.space4))
        Text(
            text = "没有可整理的书",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
        )
        Spacer(Modifier.height(t.space2))
        Text(
            text = "书架里的作品会显示在这里。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                               Buttons                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun LuoShelfBatchButtonV50(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)

    val background = when {
        !enabled -> t.input
        destructive && filled -> t.destructive
        else -> t.card
    }
    val foreground = when {
        !enabled -> t.mutedForeground
        destructive && filled -> t.destructiveForeground
        destructive -> t.destructive
        else -> t.foreground
    }
    val border = when {
        !enabled -> t.border
        destructive -> t.destructive
        else -> t.border
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .background(color = background, shape = shape)
            .border(
                width = 1.dp,
                color = border.copy(alpha = if (enabled) 1f else 0.5f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = t.space4),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = foreground.copy(alpha = if (enabled) 1f else 0.45f),
            )
            Spacer(Modifier.width(t.space2))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = foreground.copy(alpha = if (enabled) 1f else 0.45f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LuoShelfBatchIconButtonV50(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = t.card, shape = CircleShape)
            .border(width = 1.dp, color = t.border, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = t.secondaryForeground,
        )
    }
}

@Composable
private fun LuoShelfBatchTextActionV50(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .background(color = t.input, shape = RoundedCornerShape(t.radiusMd))
            .border(width = 1.dp, color = t.border, shape = RoundedCornerShape(t.radiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = t.space2, vertical = t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = t.primary,
        )
        Spacer(Modifier.width(t.space1))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = t.primary,
        )
    }
}
