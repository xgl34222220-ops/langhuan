package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.springClickV31
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reader V50 · 段落笔记。
 *
 * 功能：
 *
 * 1. ReaderSelectionV30 长按选中段落后创建笔记。
 * 2. 保存选中段落的原文摘录。
 * 3. 保存用户笔记正文。
 * 4. 同一书籍内按 chapter + range 精确定位。
 * 5. 再次对同一段落添加笔记时更新原笔记，而不是重复创建。
 * 6. 支持删除。
 * 7. 支持笔记列表中的摘录展示。
 *
 * 数据保存在 SharedPreferences 中，按 bookId 隔离。
 *
 * UI 严格使用 v3 Token：
 *
 * - card
 * - input
 * - border
 * - foreground
 * - secondaryForeground
 * - mutedForeground
 * - primary
 * - accent
 * - accentForeground
 * - gold / goldContainer / goldForeground
 *
 * 所有普通层级只使用 1dp border，不使用阴影。
 */


/* -------------------------------------------------------------------------- */
/*                                   Model                                    */
/* -------------------------------------------------------------------------- */

internal data class ReaderParagraphNoteV50(
    val id: String,
    val bookId: String,

    /**
     * 与 ReaderSelectionV30 保持一致。
     * 这里保存的是 Reader 内部 chapterIndex。
     */
    val chapterIndex: Int,

    /**
     * 保存创建笔记时所在分页。
     *
     * 分页会随字体设置发生变化，因此它只用于辅助恢复，
     * 真正的定位依据仍是 chapterIndex + start/end。
     */
    val pageIndex: Int,

    /**
     * 在规范化章节正文中的字符范围。
     */
    val start: Int,
    val end: Int,

    /**
     * 用户选中时看到的原文。
     *
     * 即使未来原书文件发生轻微变化，
     * 笔记列表仍可展示创建时的摘录。
     */
    val excerpt: String,

    /**
     * 用户写下的笔记。
     */
    val note: String,

    /**
     * 保存时的章节标题。
     *
     * 仅用于列表展示，不作为定位依据。
     */
    val chapterTitle: String,

    val createdAt: Long,
    val updatedAt: Long,
) {
    val hasNote: Boolean
        get() = note.isNotBlank()

    val normalizedExcerpt: String
        get() = excerpt
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()

    val anchorKey: String
        get() = "$chapterIndex:$start:$end"
}


/* -------------------------------------------------------------------------- */
/*                                   Store                                    */
/* -------------------------------------------------------------------------- */

internal object ReaderParagraphNoteStoreV50 {

    private const val PREFIX = "reader_paragraph_notes_v50_"

    private fun key(bookId: String): String {
        require(bookId.isNotBlank()) {
            "书籍标识无效，未修改段落笔记"
        }

        return PREFIX + bookId
    }


    /* ---------------------------------------------------------------------- */
    /*                                  Read                                  */
    /* ---------------------------------------------------------------------- */

    @Synchronized
    fun load(
        prefs: SharedPreferences,
        bookId: String,
    ): Result<List<ReaderParagraphNoteV50>> = runCatching {
        val storageKey = key(bookId)

        val raw = prefs.getString(
            storageKey,
            "[]",
        ) ?: "[]"

        val array = JSONArray(raw)

        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)

                val item = json.toReaderParagraphNoteV50()

                require(item.bookId == bookId) {
                    "检测到不属于当前书籍的段落笔记"
                }

                add(item)
            }
        }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<ReaderParagraphNoteV50> {
                    it.chapterIndex
                }.thenBy {
                    it.start
                }.thenBy {
                    it.createdAt
                },
            )
    }.recoverCatching {
        throw IllegalStateException(
            "本书段落笔记无法完整读取，原始数据已保留，暂停修改",
            it,
        )
    }


    /**
     * 读取指定章节笔记。
     */
    @Synchronized
    fun loadChapter(
        prefs: SharedPreferences,
        bookId: String,
        chapterIndex: Int,
    ): Result<List<ReaderParagraphNoteV50>> = runCatching {
        require(chapterIndex >= 0) {
            "章节索引无效"
        }

        load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()
            .filter {
                it.chapterIndex == chapterIndex
            }
            .sortedBy {
                it.start
            }
    }


    /**
     * 找到当前选区已经存在的笔记。
     *
     * 使用字符范围匹配，不依赖 pageIndex，
     * 因为字号 / 行距变化会改变分页。
     */
    @Synchronized
    fun findForSelection(
        prefs: SharedPreferences,
        bookId: String,
        selection: ReaderSelectionV30,
    ): Result<ReaderParagraphNoteV50?> = runCatching {
        load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()
            .firstOrNull {
                it.chapterIndex == selection.chapterIndex &&
                    it.start == selection.start &&
                    it.end == selection.end
            }
    }


    /* ---------------------------------------------------------------------- */
    /*                                  Save                                  */
    /* ---------------------------------------------------------------------- */

    /**
     * 从 ReaderSelectionV30 保存或更新一条段落笔记。
     *
     * 同一个：
     *
     * chapterIndex + start + end
     *
     * 被视为同一条原文锚点。
     */
    @Synchronized
    fun saveSelection(
        prefs: SharedPreferences,
        bookId: String,
        selection: ReaderSelectionV30,
        note: String,
        chapterTitle: String = "",
    ): Result<ReaderParagraphNoteV50> = runCatching {
        require(bookId.isNotBlank()) {
            "书籍标识无效，无法保存笔记"
        }

        require(selection.chapterIndex >= 0) {
            "章节索引无效，无法保存笔记"
        }

        require(selection.start >= 0) {
            "段落起点无效，无法保存笔记"
        }

        require(selection.end > selection.start) {
            "段落范围无效，无法保存笔记"
        }

        val excerpt = selection.text
            .trim()
            .trim('\u3000')

        require(excerpt.isNotBlank()) {
            "选中的段落为空，无法保存笔记"
        }

        val normalizedNote = note.trim()

        require(normalizedNote.isNotBlank()) {
            "笔记内容不能为空"
        }

        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        val existing = current.firstOrNull {
            it.chapterIndex == selection.chapterIndex &&
                it.start == selection.start &&
                it.end == selection.end
        }

        val now = System.currentTimeMillis()

        val next = if (existing != null) {
            existing.copy(
                pageIndex = selection.pageIndex,
                excerpt = excerpt,
                note = normalizedNote,
                chapterTitle = chapterTitle.trim(),
                updatedAt = now,
            )
        } else {
            ReaderParagraphNoteV50(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = selection.chapterIndex,
                pageIndex = selection.pageIndex,
                start = selection.start,
                end = selection.end,
                excerpt = excerpt,
                note = normalizedNote,
                chapterTitle = chapterTitle.trim(),
                createdAt = now,
                updatedAt = now,
            )
        }

        val updated = current
            .filterNot {
                it.id == next.id ||
                    (
                        it.chapterIndex == next.chapterIndex &&
                            it.start == next.start &&
                            it.end == next.end
                        )
            }
            .plus(next)
            .sortedWith(
                compareBy<ReaderParagraphNoteV50> {
                    it.chapterIndex
                }.thenBy {
                    it.start
                }.thenBy {
                    it.createdAt
                },
            )

        replace(
            prefs = prefs,
            bookId = bookId,
            notes = updated,
        ).getOrThrow()

        next
    }


    /**
     * 编辑已经存在的笔记正文。
     */
    @Synchronized
    fun updateText(
        prefs: SharedPreferences,
        bookId: String,
        noteId: String,
        text: String,
    ): Result<ReaderParagraphNoteV50> = runCatching {
        require(noteId.isNotBlank()) {
            "笔记标识无效"
        }

        val normalized = text.trim()

        require(normalized.isNotBlank()) {
            "笔记内容不能为空"
        }

        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        val old = current.firstOrNull {
            it.id == noteId
        } ?: error("未找到需要修改的段落笔记")

        val updatedNote = old.copy(
            note = normalized,
            updatedAt = System.currentTimeMillis(),
        )

        val updated = current.map {
            if (it.id == noteId) {
                updatedNote
            } else {
                it
            }
        }

        replace(
            prefs = prefs,
            bookId = bookId,
            notes = updated,
        ).getOrThrow()

        updatedNote
    }


    /* ---------------------------------------------------------------------- */
    /*                                 Delete                                 */
    /* ---------------------------------------------------------------------- */

    @Synchronized
    fun delete(
        prefs: SharedPreferences,
        bookId: String,
        noteId: String,
    ): Result<List<ReaderParagraphNoteV50>> = runCatching {
        require(noteId.isNotBlank()) {
            "笔记标识无效"
        }

        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        require(current.any { it.id == noteId }) {
            "未找到需要删除的段落笔记"
        }

        val updated = current.filterNot {
            it.id == noteId
        }

        replace(
            prefs = prefs,
            bookId = bookId,
            notes = updated,
        ).getOrThrow()
    }


    /* ---------------------------------------------------------------------- */
    /*                                Replace                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 完整替换一本书的笔记集合。
     *
     * 和 ReaderBookmarkStoreV49 一样：
     *
     * - 保存前先确认当前数据能完整读取；
     * - 使用 commit() 确认磁盘写入结果；
     * - 写入失败后尝试恢复原始字符串。
     */
    @Synchronized
    private fun replace(
        prefs: SharedPreferences,
        bookId: String,
        notes: List<ReaderParagraphNoteV50>,
    ): Result<List<ReaderParagraphNoteV50>> = runCatching {
        val storageKey = key(bookId)

        load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        require(
            notes.all {
                it.bookId == bookId &&
                    it.id.isNotBlank() &&
                    it.chapterIndex >= 0 &&
                    it.start >= 0 &&
                    it.end > it.start &&
                    it.excerpt.isNotBlank() &&
                    it.note.isNotBlank()
            },
        ) {
            "段落笔记数据无效，拒绝覆盖原始数据"
        }

        val normalized = notes
            .distinctBy {
                it.id
            }
            .sortedWith(
                compareBy<ReaderParagraphNoteV50> {
                    it.chapterIndex
                }.thenBy {
                    it.start
                }.thenBy {
                    it.createdAt
                },
            )

        val json = JSONArray()

        normalized.forEach {
            json.put(
                it.toJsonV50(),
            )
        }

        val existed = prefs.contains(storageKey)

        val original = prefs.getString(
            storageKey,
            null,
        )

        check(!Thread.currentThread().isInterrupted) {
            "保存已取消，段落笔记未修改"
        }

        val committed = runCatching {
            prefs.edit()
                .putString(
                    storageKey,
                    json.toString(),
                )
                .commit()
        }.getOrDefault(false)

        if (!committed) {
            /*
             * SharedPreferences.commit() 极少数情况下可能在返回 false
             * 前已经更新了内存副本，因此显式尝试恢复原值。
             */
            runCatching {
                val rollback = prefs.edit()

                if (existed) {
                    rollback.putString(
                        storageKey,
                        original,
                    )
                } else {
                    rollback.remove(storageKey)
                }

                rollback.commit()
            }

            error(
                "段落笔记保存失败，未应用本次修改，请检查存储空间后重试",
            )
        }

        normalized
    }
}


/* -------------------------------------------------------------------------- */
/*                              JSON Conversion                               */
/* -------------------------------------------------------------------------- */

private fun ReaderParagraphNoteV50.toJsonV50(): JSONObject =
    JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("chapterIndex", chapterIndex)
        put("pageIndex", pageIndex)
        put("start", start)
        put("end", end)
        put("excerpt", excerpt)
        put("note", note)
        put("chapterTitle", chapterTitle)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }


private fun JSONObject.toReaderParagraphNoteV50(): ReaderParagraphNoteV50 {
    val id = getString("id")
    val bookId = getString("bookId")
    val chapterIndex = getInt("chapterIndex")
    val pageIndex = optInt(
        "pageIndex",
        0,
    )
    val start = getInt("start")
    val end = getInt("end")
    val excerpt = getString("excerpt")
    val note = getString("note")
    val chapterTitle = optString(
        "chapterTitle",
        "",
    )
    val createdAt = getLong("createdAt")
    val updatedAt = optLong(
        "updatedAt",
        createdAt,
    )

    require(id.isNotBlank()) {
        "段落笔记 ID 为空"
    }

    require(bookId.isNotBlank()) {
        "段落笔记书籍 ID 为空"
    }

    require(chapterIndex >= 0) {
        "段落笔记章节索引无效"
    }

    require(start >= 0 && end > start) {
        "段落笔记原文范围无效"
    }

    require(excerpt.isNotBlank()) {
        "段落笔记摘录为空"
    }

    require(note.isNotBlank()) {
        "段落笔记正文为空"
    }

    return ReaderParagraphNoteV50(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex.coerceAtLeast(0),
        start = start,
        end = end,
        excerpt = excerpt,
        note = note,
        chapterTitle = chapterTitle,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}


/* -------------------------------------------------------------------------- */
/*                              Editor Dialog                                 */
/* -------------------------------------------------------------------------- */

/**
 * 选中段落后的笔记编辑器。
 *
 * 可直接从 ReaderSelectionBarV30 的「笔记」动作打开。
 *
 * 它自己负责：
 *
 * - 查找当前选区是否已有笔记；
 * - 展示原文摘录；
 * - 保存；
 * - 修改已有笔记；
 * - 展示保存错误。
 */
@Composable
internal fun ReaderParagraphNoteEditorV50(
    prefs: SharedPreferences,
    bookId: String,
    selection: ReaderSelectionV30,
    chapterTitle: String,
    onDismiss: () -> Unit,
    onSaved: (ReaderParagraphNoteV50) -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current

    val scope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current

    var existing by remember(
        bookId,
        selection.chapterIndex,
        selection.start,
        selection.end,
    ) {
        mutableStateOf<ReaderParagraphNoteV50?>(null)
    }

    var noteText by remember(
        bookId,
        selection.chapterIndex,
        selection.start,
        selection.end,
    ) {
        mutableStateOf("")
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(
        prefs,
        bookId,
        selection.chapterIndex,
        selection.start,
        selection.end,
    ) {
        loading = true
        error = null

        val result = withContext(Dispatchers.IO) {
            ReaderParagraphNoteStoreV50.findForSelection(
                prefs = prefs,
                bookId = bookId,
                selection = selection,
            )
        }

        result.fold(
            onSuccess = {
                existing = it
                noteText = it?.note.orEmpty()
            },
            onFailure = {
                error = it.message ?: "读取段落笔记失败"
            },
        )

        loading = false
    }

    Dialog(
        onDismissRequest = {
            if (!saving) {
                focusManager.clearFocus()
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !saving,
            dismissOnClickOutside = !saving,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val shape = RoundedCornerShape(
            t.radiusXl,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = t.space4,
                )
                .background(
                    color = t.card,
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = shape,
                )
                .padding(
                    t.space4,
                ),
        ) {

            /* ------------------------------------------------------------------ */
            /* Header                                                             */
            /* ------------------------------------------------------------------ */

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = t.accent,
                            shape = RoundedCornerShape(
                                t.radiusMd,
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = t.border,
                            shape = RoundedCornerShape(
                                t.radiusMd,
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = t.accentForeground,
                    )
                }

                Spacer(
                    Modifier.width(
                        t.space3,
                    ),
                )

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (existing == null) {
                            "添加段落笔记"
                        } else {
                            "编辑段落笔记"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = t.foreground,
                    )

                    if (chapterTitle.isNotBlank()) {
                        Spacer(
                            Modifier.height(
                                t.space1,
                            ),
                        )

                        Text(
                            text = chapterTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                ReaderParagraphNoteIconButtonV50(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    enabled = !saving,
                    onClick = {
                        focusManager.clearFocus()
                        onDismiss()
                    },
                )
            }

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )


            /* ------------------------------------------------------------------ */
            /* Excerpt                                                            */
            /* ------------------------------------------------------------------ */

            ReaderParagraphExcerptV50(
                text = selection.text,
            )

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )


            /* ------------------------------------------------------------------ */
            /* Editor                                                             */
            /* ------------------------------------------------------------------ */

            Text(
                text = "笔记",
                style = MaterialTheme.typography.labelLarge,
                color = t.secondaryForeground,
            )

            Spacer(
                Modifier.height(
                    t.space2,
                ),
            )

            ReaderParagraphNoteInputV50(
                value = noteText,
                onValueChange = {
                    noteText = it
                    error = null
                },
                enabled = !loading && !saving,
            )


            /* ------------------------------------------------------------------ */
            /* Error                                                              */
            /* ------------------------------------------------------------------ */

            AnimatedVisibility(
                visible = !error.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = error.orEmpty(),
                    modifier = Modifier.padding(
                        top = t.space2,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.destructive,
                )
            }

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )


            /* ------------------------------------------------------------------ */
            /* Actions                                                            */
            /* ------------------------------------------------------------------ */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = t.primary,
                    )
                } else {
                    ReaderParagraphNoteCancelButtonV50(
                        enabled = !saving,
                        onClick = {
                            focusManager.clearFocus()
                            onDismiss()
                        },
                    )

                    Spacer(
                        Modifier.width(
                            t.space2,
                        ),
                    )

                    ReaderParagraphNoteSaveButtonV50(
                        enabled = noteText.isNotBlank() && !saving,
                        loading = saving,
                        onClick = {
                            if (noteText.isBlank()) {
                                error = "请输入笔记内容"
                                return@ReaderParagraphNoteSaveButtonV50
                            }

                            focusManager.clearFocus()

                            scope.launch {
                                saving = true
                                error = null

                                val result = withContext(Dispatchers.IO) {
                                    ReaderParagraphNoteStoreV50.saveSelection(
                                        prefs = prefs,
                                        bookId = bookId,
                                        selection = selection,
                                        note = noteText,
                                        chapterTitle = chapterTitle,
                                    )
                                }

                                result.fold(
                                    onSuccess = {
                                        saving = false
                                        existing = it
                                        noteText = it.note
                                        onSaved(it)
                                        onDismiss()
                                    },
                                    onFailure = {
                                        saving = false
                                        error = it.message ?: "保存笔记失败"
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Note Input                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderParagraphNoteInputV50(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusLg,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = 128.dp,
                max = 260.dp,
            )
            .background(
                color = t.input,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(
                t.space3,
            ),
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = t.foreground,
        ),
        cursorBrush = SolidColor(
            t.primary,
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "写下这一段让你想到的事……",
                        style = MaterialTheme.typography.bodyLarge,
                        color = t.mutedForeground,
                    )
                }

                innerTextField()
            }
        },
    )
}


/* -------------------------------------------------------------------------- */
/*                                Excerpt                                     */
/* -------------------------------------------------------------------------- */

/**
 * 编辑器和笔记详情共用的原文摘录组件。
 */
@Composable
internal fun ReaderParagraphExcerptV50(
    text: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusLg,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = t.goldContainer,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(
                t.space3,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Rounded.FormatQuote,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = t.goldForeground,
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text = text
                .trim()
                .trim('\u3000'),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = t.goldForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Display Card                                  */
/* -------------------------------------------------------------------------- */

/**
 * 笔记列表中的单条摘录。
 *
 * 展示层级：
 *
 * 章节标题 / 章节编号
 * → 原文摘录
 * → 用户笔记
 * → 编辑 / 删除
 */
@Composable
internal fun ReaderParagraphNoteCardV50(
    note: ReaderParagraphNoteV50,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusLg,
    )

    val interaction = remember {
        MutableInteractionSource()
    }

    val cardModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .then(cardModifier)
            .padding(
                t.space4,
            ),
    ) {

        /* Header */

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = note.chapterTitle
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "第 ${note.chapterIndex + 1} 章",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (onDelete != null) {
                ReaderParagraphNoteIconButtonV50(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除笔记",
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )


        /* Excerpt */

        ReaderParagraphExcerptV50(
            text = note.excerpt,
        )

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )


        /* Note */

        Text(
            text = note.note,
            style = MaterialTheme.typography.bodyLarge,
            color = t.foreground,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text = readerParagraphNoteTimeLabelV50(
                note.updatedAt,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                           Compact Excerpt Row                              */
/* -------------------------------------------------------------------------- */

/**
 * 适合目录 / 笔记总览中更紧凑的展示方式。
 */
@Composable
internal fun ReaderParagraphNoteCompactV50(
    note: ReaderParagraphNoteV50,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .springClickV31(
                pressedScale = 0.985f,
                onClick = onClick,
            )
            .background(
                color = t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(
                t.space3,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = t.goldContainer,
                        shape = CircleShape,
                    )
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatQuote,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = t.goldForeground,
                )
            }

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            Text(
                text = note.chapterTitle
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "第 ${note.chapterIndex + 1} 章",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text = note.normalizedExcerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text = note.note,
            style = MaterialTheme.typography.bodySmall,
            color = t.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                 Buttons                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderParagraphNoteSaveButtonV50(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    Row(
        modifier = Modifier
            .height(44.dp)
            .background(
                color = t.primary.copy(
                    alpha = if (enabled) {
                        1f
                    } else {
                        0.42f
                    },
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.primary.copy(
                    alpha = if (enabled) {
                        1f
                    } else {
                        0.42f
                    },
                ),
                shape = shape,
            )
            .clickable(
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                strokeWidth = 2.dp,
                color = t.card,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = t.card,
            )

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            Text(
                text = "保存",
                style = MaterialTheme.typography.labelLarge,
                color = t.card,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


@Composable
private fun ReaderParagraphNoteCancelButtonV50(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    Box(
        modifier = Modifier
            .height(44.dp)
            .background(
                color = t.card,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space4,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "取消",
            style = MaterialTheme.typography.labelLarge,
            color = t.secondaryForeground.copy(
                alpha = if (enabled) {
                    1f
                } else {
                    0.45f
                },
            ),
        )
    }
}


@Composable
private fun ReaderParagraphNoteIconButtonV50(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val foreground = if (destructive) {
        t.destructive
    } else {
        t.secondaryForeground
    }

    val interaction = remember {
        MutableInteractionSource()
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = Color.Transparent,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = foreground.copy(
                alpha = if (enabled) {
                    1f
                } else {
                    0.4f
                },
            ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Helpers                                     */
/* -------------------------------------------------------------------------- */

/**
 * 本地轻量时间文案。
 *
 * 不引入额外 DateFormat 状态，也不会把绝对时间写进持久层。
 */
private fun readerParagraphNoteTimeLabelV50(
    timestamp: Long,
): String {
    val diff = (
        System.currentTimeMillis() - timestamp
        ).coerceAtLeast(0L)

    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour

    return when {
        diff < minute -> {
            "刚刚"
        }

        diff < hour -> {
            "${diff / minute} 分钟前"
        }

        diff < day -> {
            "${diff / hour} 小时前"
        }

        diff < 7L * day -> {
            "${diff / day} 天前"
        }

        else -> {
            java.text.SimpleDateFormat(
                "yyyy-MM-dd",
                java.util.Locale.getDefault(),
            ).format(
                java.util.Date(timestamp),
            )
        }
    }
}
