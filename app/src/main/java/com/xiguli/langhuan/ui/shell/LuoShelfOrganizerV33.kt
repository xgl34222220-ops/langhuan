package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.springClickV31
import java.text.Collator
import java.util.Locale

/** How the shelf grid is ordered. Stored by [key] so the choice survives restarts. */
internal enum class LuoShelfSortV33(val key: String, val label: String) {
    RECENT_READ("read", "最近阅读"),
    UPDATED("updated", "最近更新"),
    TITLE("title", "书名"),
    ;

    fun next(): LuoShelfSortV33 = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(key: String?): LuoShelfSortV33 = entries.firstOrNull { it.key == key } ?: RECENT_READ
    }
}

/**
 * Which custom shelf each book lives on. A book sits on at most one custom shelf; every book is
 * always in "全部". Kept in the same prefs file that already stores the shelf names.
 */
internal object LuoShelfAssignmentsV33 {
    private const val PREFIX = "shelf_of_"

    fun all(prefs: SharedPreferences): Map<String, String> =
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(PREFIX) && value is String) key.removePrefix(PREFIX) to value else null
        }.toMap()

    fun assign(prefs: SharedPreferences, bookId: String, shelf: String?) {
        prefs.edit().apply {
            if (shelf.isNullOrBlank()) remove(PREFIX + bookId) else putString(PREFIX + bookId, shelf)
        }.apply()
    }

    /** Deleting a shelf returns its books to "全部" rather than losing track of them. */
    fun removeShelf(prefs: SharedPreferences, name: String) {
        val editor = prefs.edit()
        all(prefs).filterValues { it == name }.keys.forEach { editor.remove(PREFIX + it) }
        editor.apply()
    }

    fun forget(prefs: SharedPreferences, bookId: String) {
        prefs.edit().remove(PREFIX + bookId).apply()
    }
}

internal fun luoSortBooksV33(
    books: List<ReaderBookUi>,
    sort: LuoShelfSortV33,
    lastRead: (ReaderBookUi) -> Long,
): List<ReaderBookUi> = when (sort) {
    LuoShelfSortV33.RECENT_READ -> books.sortedByDescending { lastRead(it).takeIf { v -> v > 0L } ?: it.updatedAt }
    LuoShelfSortV33.UPDATED -> books.sortedByDescending { it.updatedAt }
    LuoShelfSortV33.TITLE -> {
        val collator = Collator.getInstance(Locale.CHINA)
        books.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }
}

/** "全部 · 书架A · 书架B" chips; the active one fills with the accent colour. */
@Composable
internal fun LuoShelfTabsV33(
    shelves: List<String>,
    counts: Map<String?, Int>,
    active: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LuoShelfChipV33("全部", counts[null] ?: 0, active == null) { onSelect(null) }
        shelves.forEach { name -> LuoShelfChipV33(name, counts[name] ?: 0, active == name) { onSelect(name) } }
    }
}

@Composable
private fun LuoShelfChipV33(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val bg by animateColorAsState(if (selected) t.accent else t.card, tween(LanghuanMotionV31.MEDIUM), label = "shelfChipBg")
    val fg by animateColorAsState(if (selected) t.accentForeground else t.mutedForeground, tween(LanghuanMotionV31.MEDIUM), label = "shelfChipFg")
    Surface(
        modifier = Modifier.springClickV31(pressedScale = .94f, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = bg,
        contentColor = fg,
        shadowElevation = if (selected) 0.dp else 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.width(5.dp))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = .7f))
        }
    }
}

/** Pick a shelf for one book, or create a new one on the spot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuoMoveShelfSheetV33(
    book: ReaderBookUi,
    shelves: List<String>,
    current: String?,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit,
    onCreateAndMove: (String) -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.card, shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp)) {
            Text("移动《${book.title}》", color = t.foreground, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("一本书只放在一个书架里，「全部」里始终能看到它", Modifier.padding(top = 4.dp, bottom = 12.dp), color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
            LuoShelfOptionV33("不放入书架", current == null) { onMove(null) }
            shelves.forEach { name -> LuoShelfOptionV33(name, current == name) { onMove(name) } }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newName,
                    onValueChange = { newName = it.take(12) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("新书架名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = t.muted,
                        unfocusedContainerColor = t.muted,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                val canCreate = newName.isNotBlank() && newName.trim() !in shelves
                val createBg by animateColorAsState(if (canCreate) t.primary else t.muted, tween(LanghuanMotionV31.MEDIUM), label = "createShelfBg")
                Surface(
                    Modifier.padding(start = 10.dp).size(48.dp).springClickV31(enabled = canCreate, pressedScale = .9f) { onCreateAndMove(newName.trim()) },
                    shape = RoundedCornerShape(16.dp),
                    color = createBg,
                    contentColor = if (canCreate) t.primaryForeground else t.mutedForeground,
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Add, "新建并移动") }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun LuoShelfOptionV33(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalLanghuanUiTokens.current
    val bg by animateColorAsState(if (selected) t.accent.copy(alpha = .45f) else Color.Transparent, tween(LanghuanMotionV31.MEDIUM), label = "shelfOptionBg")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .springClickV31(pressedScale = .98f, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp), tint = if (selected) t.accentForeground else t.mutedForeground)
        Text(label, Modifier.padding(start = 12.dp).weight(1f), color = t.foreground, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Rounded.Check, "当前书架", Modifier.size(20.dp), tint = t.primary)
    }
}
