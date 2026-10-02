package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


/**
 * 书架管理页所需的 UI 投影。
 *
 * 这里只描述上层传入的书架展示状态，
 * 不负责存储，也不代表新的 Repository / 数据表。
 */
@Immutable
internal data class ShelfManageShelfV50(
    val id: String,
    val name: String,
    val bookCount: Int,
    val order: Int,
)


/**
 * 书架管理 · V50
 *
 * 页面只负责展示和回调：
 *
 * - 自定义书架列表由上层传入
 * - 总书数由上层传入
 * - 创建 / 查看 / 重命名 / 删除 / 排序全部交回上层
 * - 不直接访问任何 SharedPreferences / Room / Repository
 */
@Composable
internal fun ShelfManageScreenV50(
    shelves: List<ShelfManageShelfV50>,
    totalBookCount: Int,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onCreateShelf: () -> Unit,
    onOpenAllBooks: () -> Unit,
    onOpenShelf: (ShelfManageShelfV50) -> Unit,
    onRenameShelf: (ShelfManageShelfV50) -> Unit,
    onDeleteShelf: (ShelfManageShelfV50) -> Unit,
    onReorderShelf: (
        shelf: ShelfManageShelfV50,
        targetIndex: Int,
    ) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val orderedShelves =
        remember(
            shelves,
        ) {
            shelves.sortedBy {
                it.order
            }
        }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                t.background,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ShelfManageHeaderV50(
            onBack =
                onBack,
            onCreateShelf =
                onCreateShelf,
        )

        LazyColumn(
            modifier =
                Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start =
                        t.space4,
                    end =
                        t.space4,
                    top =
                        t.space3,
                    bottom =
                        t.space6,
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    t.space4,
                ),
        ) {
            item(
                key =
                    "shelf-all",
            ) {
                ShelfManageAllCardV50(
                    totalBookCount =
                        totalBookCount,
                    onClick =
                        onOpenAllBooks,
                )
            }

            item(
                key =
                    "shelf-custom-title",
            ) {
                Text(
                    text =
                        "自定义书架",
                    style =
                        MaterialTheme.typography.titleLarge,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                )
            }

            itemsIndexed(
                items =
                    orderedShelves,
                key = {
                    _,
                    shelf ->
                    shelf.id
                },
            ) {
                index,
                shelf ->

                ShelfManageCustomCardV50(
                    shelf =
                        shelf,
                    index =
                        index,
                    shelfCount =
                        orderedShelves.size,
                    onOpen = {
                        onOpenShelf(
                            shelf,
                        )
                    },
                    onRename = {
                        onRenameShelf(
                            shelf,
                        )
                    },
                    onDelete = {
                        onDeleteShelf(
                            shelf,
                        )
                    },
                    onReorder = {
                        targetIndex ->

                        onReorderShelf(
                            shelf,
                            targetIndex,
                        )
                    },
                )
            }

            item(
                key =
                    "shelf-note",
            ) {
                Text(
                    text =
                        "书架标签按这里的顺序排列。删除书架只删除书架本身，里面的书会到「全部」。",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        t.mutedForeground,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Header                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ShelfManageHeaderV50(
    onBack: () -> Unit,
    onCreateShelf: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal =
                    t.space4,
                vertical =
                    t.space3,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color =
                        t.card,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                )
                .border(
                    width =
                        1.dp,
                    color =
                        t.border,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                )
                .clickable(
                    onClick =
                        onBack,
                )
                .padding(
                    t.space2,
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.ArrowBack,
                contentDescription =
                    "返回",
                tint =
                    t.foreground,
            )
        }

        Spacer(
            Modifier.padding(
                horizontal =
                    t.space1,
            ),
        )

        Column(
            modifier = Modifier
                .weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    "书架管理",
                style =
                    MaterialTheme.typography.titleLarge,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Text(
                text =
                    "一本书只放在一个书架里",
                modifier =
                    Modifier.padding(
                        top =
                            t.space1,
                    ),
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        Box(
            modifier = Modifier
                .background(
                    color =
                        t.primary,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                )
                .clickable(
                    onClick =
                        onCreateShelf,
                )
                .padding(
                    t.space2,
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.Add,
                contentDescription =
                    "新建书架",
                tint =
                    t.accentForeground,
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                 All Shelf                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ShelfManageAllCardV50(
    totalBookCount: Int,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            )
            .padding(
                horizontal =
                    t.space4,
                vertical =
                    t.space4,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        ShelfManageInitialV50(
            text =
                "全",
            accent =
                true,
        )

        Spacer(
            Modifier.padding(
                horizontal =
                    t.space2,
            ),
        )

        Column(
            modifier = Modifier
                .weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    "全部",
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Text(
                text =
                    "所有书都在这里",
                modifier =
                    Modifier.padding(
                        top =
                            t.space1,
                    ),
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        Text(
            text =
                "$totalBookCount 本",
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                t.secondaryForeground,
        )

        Spacer(
            Modifier.padding(
                horizontal =
                    t.space1,
            ),
        )

        Icon(
            imageVector =
                Icons.Rounded.ChevronRight,
            contentDescription =
                null,
            tint =
                t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Custom Shelf                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun ShelfManageCustomCardV50(
    shelf: ShelfManageShelfV50,
    index: Int,
    shelfCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (Int) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    var menuOpen by
        remember {
            mutableStateOf(
                false,
            )
        }

    var dragDistance by
        remember {
            mutableFloatStateOf(
                0f,
            )
        }

    val shape =
        RoundedCornerShape(
            t.radiusLg,
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onOpen,
            )
            .pointerInput(
                shelf.id,
                index,
                shelfCount,
            ) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragDistance =
                            0f
                    },
                    onDragEnd = {
                        val step =
                            (dragDistance / 160f)
                                .toInt()

                        val targetIndex =
                            (
                                index + step
                            ).coerceIn(
                                0,
                                shelfCount - 1,
                            )

                        if (targetIndex != index) {
                            onReorder(
                                targetIndex,
                            )
                        }

                        dragDistance =
                            0f
                    },
                    onDragCancel = {
                        dragDistance =
                            0f
                    },
                    onDrag = { _,
                        dragAmount ->
                        dragDistance +=
                            dragAmount.y
                    },
                )
            }
            .padding(
                horizontal =
                    t.space4,
                vertical =
                    t.space4,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        ShelfManageInitialV50(
            text =
                shelf.name.firstOrNull()
                    ?.toString()
                    ?: "书",
            accent =
                false,
        )

        Spacer(
            Modifier.padding(
                horizontal =
                    t.space2,
            ),
        )

        Column(
            modifier = Modifier
                .weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    shelf.name,
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
                maxLines =
                    1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Text(
                text =
                    "${shelf.bookCount} 本 · 点按查看",
                modifier =
                    Modifier.padding(
                        top =
                            t.space1,
                    ),
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        Box {
            Box(
                modifier = Modifier
                    .clickable(
                        onClick = {
                            menuOpen =
                                true
                        },
                    )
                    .padding(
                        t.space2,
                    ),
                contentAlignment =
                    Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.MoreHoriz,
                    contentDescription =
                        "更多操作",
                    tint =
                        t.secondaryForeground,
                )
            }

            DropdownMenu(
                expanded =
                    menuOpen,
                onDismissRequest = {
                    menuOpen =
                        false
                },
            ) {
                ShelfManageRenameMenuItemV50(
                    onClick = {
                        menuOpen =
                            false
                        onRename()
                    },
                )

                ShelfManageDeleteMenuItemV50(
                    onClick = {
                        menuOpen =
                            false
                        onDelete()
                    },
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                Initial                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ShelfManageInitialV50(
    text: String,
    accent: Boolean,
) {
    val t =
        LocalLanghuanUiTokens.current

    Box(
        modifier = Modifier
            .background(
                color =
                    if (accent) {
                        t.accent
                    } else {
                        t.input
                    },
                shape =
                    RoundedCornerShape(
                        t.radiusMd,
                    ),
            )
            .border(
                width =
                    1.dp,
                color =
                    t.border,
                shape =
                    RoundedCornerShape(
                        t.radiusMd,
                    ),
            )
            .padding(
                horizontal =
                    t.space3,
                vertical =
                    t.space3,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                text,
            style =
                MaterialTheme.typography.titleMedium,
            color =
                if (accent) {
                    t.accentForeground
                } else {
                    t.secondaryForeground
                },
            fontWeight =
                FontWeight.SemiBold,
            maxLines =
                1,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              More Menu                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun ShelfManageRenameMenuItemV50(
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    DropdownMenuItem(
        text = {
            Text(
                text =
                    "重命名",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    t.foreground,
            )
        },
        onClick =
            onClick,
        leadingIcon = {
            Icon(
                imageVector =
                    Icons.Rounded.Edit,
                contentDescription =
                    null,
                tint =
                    t.secondaryForeground,
            )
        },
        contentPadding =
            PaddingValues(
                horizontal =
                    t.space3,
                vertical =
                    t.space1,
            ),
    )
}


@Composable
private fun ShelfManageDeleteMenuItemV50(
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text =
                        "删除书架",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        t.destructive,
                    fontWeight =
                        FontWeight.Medium,
                )

                Text(
                    text =
                        "书会回到「全部」",
                    modifier =
                        Modifier.padding(
                            top =
                                t.space1,
                        ),
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        t.mutedForeground,
                )
            }
        },
        onClick =
            onClick,
        leadingIcon = {
            Icon(
                imageVector =
                    Icons.Rounded.DeleteOutline,
                contentDescription =
                    null,
                tint =
                    t.destructive,
            )
        },
        contentPadding =
            PaddingValues(
                horizontal =
                    t.space3,
                vertical =
                    t.space1,
            ),
    )
}
