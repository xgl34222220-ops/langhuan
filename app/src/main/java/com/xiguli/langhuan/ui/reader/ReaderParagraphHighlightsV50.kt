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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Highlight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiguli.langhuan.ui.design.LanghuanUiTokens
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.design.springClickV31
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reader V50 · 段落划线。
 *
 * 与 ReaderParagraphNotesV50 使用相同的数据与存储设计：
 *
 * - bookId 隔离
 * - chapterIndex + start + end 作为原文稳定锚点
 * - pageIndex 只作为辅助信息
 * - 保留划线时的原文 excerpt
 * - SharedPreferences + JSON 持久化
 * - commit() 确认真正写盘
 * - 写入失败尝试回滚原始数据
 *
 * 功能：
 *
 * 1. 长按 ReaderSelectionV30 选中段落。
 * 2. 创建玉青 / 赤金两种划线。
 * 3. 保存。
 * 4. 阅读页重新渲染划线。
 * 5. 笔记/划线列表展示原文摘录。
 * 6. 删除。
 * 7. 同一段落重复划线时更新颜色而不是重复创建。
 *
 * UI 使用 v3 Token：
 *
 * - primary
 * - accent
 * - accentForeground
 * - gold
 * - goldForeground
 * - goldContainer
 * - card
 * - input
 * - border
 * - foreground
 * - secondaryForeground
 * - mutedForeground
 *
 * 所有普通 UI 分层均为 1dp border，不使用投影。
 */


/* -------------------------------------------------------------------------- */
/*                                    Tone                                    */
/* -------------------------------------------------------------------------- */

internal enum class ReaderParagraphHighlightToneV50(
    val key: String,
) {
    JADE("jade"),
    GOLD("gold");

    companion object {
        fun fromKey(
            key: String?,
        ): ReaderParagraphHighlightToneV50 {
            return entries.firstOrNull {
                it.key == key
            } ?: JADE
        }
    }
}


/**
 * 从 v3 Token 获取真正的划线强调色。
 */
internal fun ReaderParagraphHighlightToneV50.markColor(
    tokens: LanghuanUiTokens,
): Color {
    return when (this) {
        ReaderParagraphHighlightToneV50.JADE -> {
            tokens.primary
        }

        ReaderParagraphHighlightToneV50.GOLD -> {
            tokens.gold
        }
    }
}


/**
 * 划线列表/选择器中的低强调背景。
 */
internal fun ReaderParagraphHighlightToneV50.containerColor(
    tokens: LanghuanUiTokens,
): Color {
    return when (this) {
        ReaderParagraphHighlightToneV50.JADE -> {
            tokens.accent
        }

        ReaderParagraphHighlightToneV50.GOLD -> {
            tokens.goldContainer
        }
    }
}


/**
 * 对应低强调背景上的前景色。
 */
internal fun ReaderParagraphHighlightToneV50.foregroundColor(
    tokens: LanghuanUiTokens,
): Color {
    return when (this) {
        ReaderParagraphHighlightToneV50.JADE -> {
            tokens.accentForeground
        }

        ReaderParagraphHighlightToneV50.GOLD -> {
            tokens.goldForeground
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                   Model                                    */
/* -------------------------------------------------------------------------- */

internal data class ReaderParagraphHighlightV50(
    val id: String,
    val bookId: String,

    /**
     * Reader 内部章节索引。
     */
    val chapterIndex: Int,

    /**
     * 保存时所在页。
     *
     * 字体、字号、行距改变后 pageIndex 可能变化，
     * 因此不能作为永久锚点。
     */
    val pageIndex: Int,

    /**
     * 在规范化章节正文中的字符范围。
     */
    val start: Int,
    val end: Int,

    /**
     * 创建划线时的原文副本。
     *
     * 即使未来正文略有变化，
     * 划线管理页仍可展示原摘录。
     */
    val excerpt: String,

    /**
     * 保存时的章节标题。
     */
    val chapterTitle: String,

    /**
     * 玉青 / 赤金。
     */
    val tone: ReaderParagraphHighlightToneV50,

    val createdAt: Long,
    val updatedAt: Long,
) {
    val anchorKey: String
        get() = "$chapterIndex:$start:$end"

    val normalizedExcerpt: String
        get() = excerpt
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
}


/* -------------------------------------------------------------------------- */
/*                                   Store                                    */
/* -------------------------------------------------------------------------- */

internal object ReaderParagraphHighlightStoreV50 {

    private const val PREFIX =
        "reader_paragraph_highlights_v50_"


    private fun key(
        bookId: String,
    ): String {
        require(bookId.isNotBlank()) {
            "书籍标识无效，未修改段落划线"
        }

        return PREFIX + bookId
    }


    /* ---------------------------------------------------------------------- */
    /*                                   Load                                 */
    /* ---------------------------------------------------------------------- */

    @Synchronized
    fun load(
        prefs: SharedPreferences,
        bookId: String,
    ): Result<List<ReaderParagraphHighlightV50>> = runCatching {
        val storageKey = key(bookId)

        val raw = prefs.getString(
            storageKey,
            "[]",
        ) ?: "[]"

        val array = JSONArray(raw)

        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)

                val item = json.toReaderParagraphHighlightV50()

                require(item.bookId == bookId) {
                    "检测到不属于当前书籍的段落划线"
                }

                add(item)
            }
        }
            .distinctBy {
                it.id
            }
            .sortedWith(
                compareBy<ReaderParagraphHighlightV50> {
                    it.chapterIndex
                }.thenBy {
                    it.start
                }.thenBy {
                    it.createdAt
                },
            )
    }.recoverCatching {
        throw IllegalStateException(
            "本书段落划线无法完整读取，原始数据已保留，暂停修改",
            it,
        )
    }


    @Synchronized
    fun loadChapter(
        prefs: SharedPreferences,
        bookId: String,
        chapterIndex: Int,
    ): Result<List<ReaderParagraphHighlightV50>> = runCatching {
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
     * 获取与当前长按段落完全相同的划线。
     */
    @Synchronized
    fun findForSelection(
        prefs: SharedPreferences,
        bookId: String,
        selection: ReaderSelectionV30,
    ): Result<ReaderParagraphHighlightV50?> = runCatching {
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


    /**
     * 检查一个正文范围是否已经有划线。
     */
    @Synchronized
    fun findAt(
        prefs: SharedPreferences,
        bookId: String,
        chapterIndex: Int,
        start: Int,
        end: Int,
    ): Result<ReaderParagraphHighlightV50?> = runCatching {
        load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()
            .firstOrNull {
                it.chapterIndex == chapterIndex &&
                    it.start == start &&
                    it.end == end
            }
    }


    /* ---------------------------------------------------------------------- */
    /*                                   Save                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 保存当前选段。
     *
     * 若锚点已经存在：
     *
     * - 不创建重复记录；
     * - 更新 tone；
     * - 更新 excerpt / pageIndex / chapterTitle；
     * - 保留 createdAt。
     */
    @Synchronized
    fun saveSelection(
        prefs: SharedPreferences,
        bookId: String,
        selection: ReaderSelectionV30,
        chapterTitle: String = "",
        tone: ReaderParagraphHighlightToneV50 =
            ReaderParagraphHighlightToneV50.JADE,
    ): Result<ReaderParagraphHighlightV50> = runCatching {
        require(bookId.isNotBlank()) {
            "书籍标识无效，无法保存划线"
        }

        require(selection.chapterIndex >= 0) {
            "章节索引无效，无法保存划线"
        }

        require(selection.start >= 0) {
            "划线起点无效"
        }

        require(selection.end > selection.start) {
            "划线范围无效"
        }

        val excerpt = selection.text
            .trim()
            .trim('\u3000')

        require(excerpt.isNotBlank()) {
            "选中的段落为空，无法保存划线"
        }

        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        val old = current.firstOrNull {
            it.chapterIndex == selection.chapterIndex &&
                it.start == selection.start &&
                it.end == selection.end
        }

        val now = System.currentTimeMillis()

        val next = if (old != null) {
            old.copy(
                pageIndex = selection.pageIndex,
                excerpt = excerpt,
                chapterTitle = chapterTitle.trim(),
                tone = tone,
                updatedAt = now,
            )
        } else {
            ReaderParagraphHighlightV50(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterIndex = selection.chapterIndex,
                pageIndex = selection.pageIndex,
                start = selection.start,
                end = selection.end,
                excerpt = excerpt,
                chapterTitle = chapterTitle.trim(),
                tone = tone,
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
                compareBy<ReaderParagraphHighlightV50> {
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
            highlights = updated,
        ).getOrThrow()

        next
    }


    /**
     * 已有划线只修改颜色。
     */
    @Synchronized
    fun updateTone(
        prefs: SharedPreferences,
        bookId: String,
        highlightId: String,
        tone: ReaderParagraphHighlightToneV50,
    ): Result<ReaderParagraphHighlightV50> = runCatching {
        require(highlightId.isNotBlank()) {
            "划线标识无效"
        }

        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        val old = current.firstOrNull {
            it.id == highlightId
        } ?: error("未找到需要修改的段落划线")

        val next = old.copy(
            tone = tone,
            updatedAt = System.currentTimeMillis(),
        )

        replace(
            prefs = prefs,
            bookId = bookId,
            highlights = current.map {
                if (it.id == highlightId) {
                    next
                } else {
                    it
                }
            },
        ).getOrThrow()

        next
    }


    /* ---------------------------------------------------------------------- */
    /*                                  Delete                                */
    /* ---------------------------------------------------------------------- */

    @Synchronized
    fun delete(
        prefs: SharedPreferences,
        bookId: String,
        highlightId: String,
    ): Result<List<ReaderParagraphHighlightV50>> = runCatching {
        require(highlightId.isNotBlank()) {
            "划线标识无效"
        }

        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        require(
            current.any {
                it.id == highlightId
            },
        ) {
            "未找到需要删除的段落划线"
        }

        val updated = current.filterNot {
            it.id == highlightId
        }

        replace(
            prefs = prefs,
            bookId = bookId,
            highlights = updated,
        ).getOrThrow()
    }


    /**
     * 根据当前选段直接删除。
     *
     * 用于 ReaderSelection 菜单中的"取消划线"。
     */
    @Synchronized
    fun deleteSelection(
        prefs: SharedPreferences,
        bookId: String,
        selection: ReaderSelectionV30,
    ): Result<List<ReaderParagraphHighlightV50>> = runCatching {
        val current = load(
            prefs = prefs,
            bookId = bookId,
        ).getOrThrow()

        val target = current.firstOrNull {
            it.chapterIndex == selection.chapterIndex &&
                it.start == selection.start &&
                it.end == selection.end
        } ?: error("当前段落没有划线")

        delete(
            prefs = prefs,
            bookId = bookId,
            highlightId = target.id,
        ).getOrThrow()
    }


    /* ---------------------------------------------------------------------- */
    /*                                 Replace                                */
    /* ---------------------------------------------------------------------- */

    @Synchronized
    private fun replace(
        prefs: SharedPreferences,
        bookId: String,
        highlights: List<ReaderParagraphHighlightV50>,
    ): Result<List<ReaderParagraphHighlightV50>> =
        runCatching {
            val storageKey = key(bookId)

            /*
             * 在覆盖之前先确认原始内容能够完整解析。
             * 如果已有数据损坏，不继续写入。
             */
            load(
                prefs = prefs,
                bookId = bookId,
            ).getOrThrow()

            require(
                highlights.all {
                    it.bookId == bookId &&
                        it.id.isNotBlank() &&
                        it.chapterIndex >= 0 &&
                        it.start >= 0 &&
                        it.end > it.start &&
                        it.excerpt.isNotBlank()
                },
            ) {
                "段落划线数据无效，拒绝覆盖原始数据"
            }

            val normalized = highlights
                .distinctBy {
                    it.id
                }
                .sortedWith(
                    compareBy<ReaderParagraphHighlightV50> {
                        it.chapterIndex
                    }.thenBy {
                        it.start
                    }.thenBy {
                        it.createdAt
                    },
                )

            val array = JSONArray()

            normalized.forEach {
                array.put(
                    it.toJsonV50(),
                )
            }

            val existed = prefs.contains(
                storageKey,
            )

            val original = prefs.getString(
                storageKey,
                null,
            )

            check(
                !Thread.currentThread().isInterrupted,
            ) {
                "保存已取消，段落划线未修改"
            }

            val committed = runCatching {
                prefs.edit()
                    .putString(
                        storageKey,
                        array.toString(),
                    )
                    .commit()
            }.getOrDefault(false)

            if (!committed) {
                /*
                 * commit() 在极少数失败路径中可能已改变
                 * SharedPreferences 的内存副本，
                 * 因此显式尝试恢复原值。
                 */
                runCatching {
                    val rollback = prefs.edit()

                    if (existed) {
                        rollback.putString(
                            storageKey,
                            original,
                        )
                    } else {
                        rollback.remove(
                            storageKey,
                        )
                    }

                    rollback.commit()
                }

                error(
                    "段落划线保存失败，未应用本次修改，请检查存储空间后重试",
                )
            }

            normalized
        }
}


/* -------------------------------------------------------------------------- */
/*                              JSON Conversion                               */
/* -------------------------------------------------------------------------- */

private fun ReaderParagraphHighlightV50.toJsonV50(): JSONObject =
    JSONObject().apply {
        put("id", id)
        put("bookId", bookId)
        put("chapterIndex", chapterIndex)
        put("pageIndex", pageIndex)
        put("start", start)
        put("end", end)
        put("excerpt", excerpt)
        put("chapterTitle", chapterTitle)
        put("tone", tone.key)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }


private fun JSONObject.toReaderParagraphHighlightV50():
    ReaderParagraphHighlightV50 {

    val id = getString("id")

    val bookId = getString("bookId")

    val chapterIndex = getInt(
        "chapterIndex",
    )

    val pageIndex = optInt(
        "pageIndex",
        0,
    )

    val start = getInt(
        "start",
    )

    val end = getInt(
        "end",
    )

    val excerpt = getString(
        "excerpt",
    )

    val chapterTitle = optString(
        "chapterTitle",
        "",
    )

    val tone = ReaderParagraphHighlightToneV50.fromKey(
        optString(
            "tone",
            ReaderParagraphHighlightToneV50.JADE.key,
        ),
    )

    val createdAt = getLong(
        "createdAt",
    )

    val updatedAt = optLong(
        "updatedAt",
        createdAt,
    )

    require(id.isNotBlank()) {
        "段落划线 ID 为空"
    }

    require(bookId.isNotBlank()) {
        "段落划线书籍 ID 为空"
    }

    require(chapterIndex >= 0) {
        "段落划线章节索引无效"
    }

    require(start >= 0 && end > start) {
        "段落划线正文范围无效"
    }

    require(excerpt.isNotBlank()) {
        "段落划线摘录为空"
    }

    return ReaderParagraphHighlightV50(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex.coerceAtLeast(0),
        start = start,
        end = end,
        excerpt = excerpt,
        chapterTitle = chapterTitle,
        tone = tone,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}


/* -------------------------------------------------------------------------- */
/*                        Current Page Rendering                              */
/* -------------------------------------------------------------------------- */

/**
 * 一个持久化划线在当前分页上的可见带。
 *
 * Highlight 本身保存的是正文字符范围，
 * Band 是由当前字体/分页实时计算出的屏幕位置。
 */
internal data class ReaderParagraphHighlightBandV50(
    val highlight: ReaderParagraphHighlightV50,
    val top: Float,
    val bottom: Float,
)


/**
 * 把持久化字符锚点重新映射到当前 ReaderPageV30。
 *
 * 因此：
 *
 * - 改字号
 * - 改字体
 * - 改行距
 * - 改左右留白
 *
 * 后划线仍然跟随原文，而不是跟随旧 pageIndex。
 */
internal fun readerParagraphHighlightBandV50(
    page: ReaderPageV30,
    geometry: ReaderGeometryV30,
    highlight: ReaderParagraphHighlightV50,
): ReaderParagraphHighlightBandV50? {
    if (
        page.chapterIndex != highlight.chapterIndex
    ) {
        return null
    }

    if (page.lines.isEmpty()) {
        return null
    }

    val relevant = page.lines.filter { line ->
        !line.title &&
            line.offset >= highlight.start &&
            line.offset < highlight.end
    }

    if (relevant.isEmpty()) {
        return null
    }

    val first = relevant.first()

    val last = relevant.last()

    val lastIndex = page.lines.indexOf(
        last,
    )

    val estimatedLineHeight =
        (last.baseline - last.top)
            .coerceAtLeast(1f) * 1.32f

    val nextTop = page.lines
        .getOrNull(lastIndex + 1)
        ?.top
        ?.takeIf {
            it > last.top
        }

    val bottom = nextTop
        ?: (
            last.top +
                estimatedLineHeight
            )

    return ReaderParagraphHighlightBandV50(
        highlight = highlight,
        top = geometry.bodyTop + first.top,
        bottom = geometry.bodyTop + bottom,
    )
}


/**
 * 在 Reader Canvas 上绘制已保存的段落划线。
 *
 * v3 视觉：
 *
 * - 非传统粗 Marker
 * - 极轻透明底色
 * - 左侧 2dp 识别线
 * - 底部 1.5dp 划线
 *
 * 颜色直接从 v3 Token 获取：
 *
 * JADE -> primary
 * GOLD -> gold
 */
internal fun DrawScope.drawReaderParagraphHighlightV50(
    band: ReaderParagraphHighlightBandV50,
    geometry: ReaderGeometryV30,
    tokens: LanghuanUiTokens,
    alpha: Float = 1f,
) {
    val color = band.highlight.tone.markColor(
        tokens,
    )

    val safeAlpha = alpha.coerceIn(
        0f,
        1f,
    )

    val sidePadding = 3f * density

    val top = band.top

    val bottom = band.bottom

    val height = (
        bottom - top
        ).coerceAtLeast(
        1f,
    )

    /*
     * 极轻原文底色。
     */
    drawRoundRect(
        color = color.copy(
            alpha = 0.075f * safeAlpha,
        ),
        topLeft = Offset(
            x = geometry.left - sidePadding,
            y = top,
        ),
        size = Size(
            width = geometry.bodyWidth + sidePadding * 2f,
            height = height,
        ),
        cornerRadius = CornerRadius(
            x = 4f * density,
            y = 4f * density,
        ),
    )

    /*
     * 左侧 Margin Mark。
     */
    drawRoundRect(
        color = color.copy(
            alpha = 0.78f * safeAlpha,
        ),
        topLeft = Offset(
            x = geometry.left - 6f * density,
            y = top + 1f * density,
        ),
        size = Size(
            width = 2f * density,
            height = (
                height - 2f * density
                ).coerceAtLeast(
                1f,
            ),
        ),
        cornerRadius = CornerRadius(
            x = 1f * density,
            y = 1f * density,
        ),
    )

    /*
     * 底部"划线"。
     *
     * 不绘制整块高饱和 Marker，
     * 让中文长文仍保持干净。
     */
    drawRoundRect(
        color = color.copy(
            alpha = 0.62f * safeAlpha,
        ),
        topLeft = Offset(
            x = geometry.left,
            y = bottom - 1.5f * density,
        ),
        size = Size(
            width = geometry.bodyWidth,
            height = 1.5f * density,
        ),
        cornerRadius = CornerRadius(
            x = 1f * density,
            y = 1f * density,
        ),
    )
}


/* -------------------------------------------------------------------------- */
/*                           Highlight Picker                                 */
/* -------------------------------------------------------------------------- */

/**
 * 长按段落后选择划线颜色。
 *
 * 如果当前段落已有划线：
 *
 * - 默认选中原颜色
 * - 可以换颜色
 * - 可以取消划线
 */
@Composable
internal fun ReaderParagraphHighlightPickerV50(
    prefs: SharedPreferences,
    bookId: String,
    selection: ReaderSelectionV30,
    chapterTitle: String,
    onDismiss: () -> Unit,
    onSaved: (
        ReaderParagraphHighlightV50,
    ) -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val t = LocalLanghuanUiTokens.current

    val scope = rememberCoroutineScope()

    var existing by remember(
        bookId,
        selection.chapterIndex,
        selection.start,
        selection.end,
    ) {
        mutableStateOf<ReaderParagraphHighlightV50?>(
            null,
        )
    }

    var selectedTone by remember(
        bookId,
        selection.chapterIndex,
        selection.start,
        selection.end,
    ) {
        mutableStateOf(
            ReaderParagraphHighlightToneV50.JADE,
        )
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

        val result = withContext(
            Dispatchers.IO,
        ) {
            ReaderParagraphHighlightStoreV50
                .findForSelection(
                    prefs = prefs,
                    bookId = bookId,
                    selection = selection,
                )
        }

        result.fold(
            onSuccess = {
                existing = it

                selectedTone = it?.tone
                    ?: ReaderParagraphHighlightToneV50.JADE
            },
            onFailure = {
                error = it.message
                    ?: "读取划线失败"
            },
        )

        loading = false
    }

    Dialog(
        onDismissRequest = {
            if (!saving) {
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

            /* Header */

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
                        imageVector = Icons.Rounded.Highlight,
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
                            "段落划线"
                        } else {
                            "修改划线"
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

                ReaderHighlightIconActionV50(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    enabled = !saving,
                    onClick = onDismiss,
                )
            }

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )


            /* Excerpt */

            ReaderParagraphHighlightExcerptV50(
                text = selection.text,
                tone = selectedTone,
            )

            Spacer(
                Modifier.height(
                    t.space4,
                ),
            )


            /* Tone */

            Text(
                text = "划线颜色",
                style = MaterialTheme.typography.labelLarge,
                color = t.secondaryForeground,
            )

            Spacer(
                Modifier.height(
                    t.space2,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    t.space2,
                ),
            ) {
                ReaderHighlightToneOptionV50(
                    tone = ReaderParagraphHighlightToneV50.JADE,
                    selected = selectedTone ==
                        ReaderParagraphHighlightToneV50.JADE,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedTone =
                            ReaderParagraphHighlightToneV50.JADE
                    },
                )

                ReaderHighlightToneOptionV50(
                    tone = ReaderParagraphHighlightToneV50.GOLD,
                    selected = selectedTone ==
                        ReaderParagraphHighlightToneV50.GOLD,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedTone =
                            ReaderParagraphHighlightToneV50.GOLD
                    },
                )
            }

            AnimatedVisibility(
                visible = !error.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = error.orEmpty(),
                    modifier = Modifier.padding(
                        top = t.space3,
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


            /* Actions */

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (existing != null) {
                    ReaderHighlightDeleteButtonV50(
                        enabled = !loading && !saving,
                        onClick = {
                            val target = existing
                                ?: return@ReaderHighlightDeleteButtonV50

                            scope.launch {
                                saving = true
                                error = null

                                val result = withContext(
                                    Dispatchers.IO,
                                ) {
                                    ReaderParagraphHighlightStoreV50.delete(
                                        prefs = prefs,
                                        bookId = bookId,
                                        highlightId = target.id,
                                    )
                                }

                                result.fold(
                                    onSuccess = {
                                        saving = false
                                        existing = null
                                        onDeleted()
                                        onDismiss()
                                    },
                                    onFailure = {
                                        saving = false
                                        error = it.message
                                            ?: "删除划线失败"
                                    },
                                )
                            }
                        },
                    )
                } else {
                    Spacer(
                        Modifier.width(
                            t.space1,
                        ),
                    )
                }

                ReaderHighlightSaveButtonV50(
                    enabled = !loading && !saving,
                    existing = existing != null,
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null

                            val result = withContext(
                                Dispatchers.IO,
                            ) {
                                ReaderParagraphHighlightStoreV50.saveSelection(
                                    prefs = prefs,
                                    bookId = bookId,
                                    selection = selection,
                                    chapterTitle = chapterTitle,
                                    tone = selectedTone,
                                )
                            }

                            result.fold(
                                onSuccess = {
                                    saving = false
                                    existing = it
                                    onSaved(it)
                                    onDismiss()
                                },
                                onFailure = {
                                    saving = false
                                    error = it.message
                                        ?: "保存划线失败"
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Tone Option                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderHighlightToneOptionV50(
    tone: ReaderParagraphHighlightToneV50,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    val background = tone.containerColor(
        t,
    )

    val foreground = tone.foregroundColor(
        t,
    )

    val mark = tone.markColor(
        t,
    )

    Column(
        modifier = modifier
            .springClickV31(
                pressedScale = 0.98f,
                onClick = onClick,
            )
            .background(
                color = background,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    mark
                } else {
                    t.border
                },
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
                    .size(22.dp)
                    .background(
                        color = mark,
                        shape = CircleShape,
                    ),
            )

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            Text(
                text = when (tone) {
                    ReaderParagraphHighlightToneV50.JADE -> {
                        "玉青"
                    }

                    ReaderParagraphHighlightToneV50.GOLD -> {
                        "赤金"
                    }
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = foreground,
            )

            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = foreground,
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = mark,
                    shape = CircleShape,
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                                Excerpt                                     */
/* -------------------------------------------------------------------------- */

@Composable
internal fun ReaderParagraphHighlightExcerptV50(
    text: String,
    tone: ReaderParagraphHighlightToneV50,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current

    val container = tone.containerColor(
        t,
    )

    val foreground = tone.foregroundColor(
        t,
    )

    val mark = tone.markColor(
        t,
    )

    val shape = RoundedCornerShape(
        t.radiusLg,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = container,
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
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatQuote,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = foreground,
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
                color = foreground,
            )
        }

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = mark.copy(
                        alpha = 0.72f,
                    ),
                    shape = CircleShape,
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Display Card                                  */
/* -------------------------------------------------------------------------- */

/**
 * 划线管理页中的完整展示卡。
 */
@Composable
internal fun ReaderParagraphHighlightCardV50(
    highlight: ReaderParagraphHighlightV50,
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

    val clickModifier = if (onClick != null) {
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
            .then(
                clickModifier,
            )
            .padding(
                t.space4,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val markColor = highlight.tone.markColor(
                t,
            )

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        color = highlight.tone.containerColor(
                            t,
                        ),
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
                    imageVector = Icons.Rounded.Highlight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = markColor,
                )
            }

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            Text(
                text = highlight.chapterTitle
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "第 ${highlight.chapterIndex + 1} 章",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = t.secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (onDelete != null) {
                ReaderHighlightIconActionV50(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除划线",
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

        ReaderParagraphHighlightExcerptV50(
            text = highlight.excerpt,
            tone = highlight.tone,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text = readerParagraphHighlightTimeLabelV50(
                highlight.updatedAt,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )
    }
}


/**
 * 更紧凑的摘录展示。
 *
 * 可用于：
 * - 目录页划线 Tab
 * - 我的摘录
 * - 书籍详情
 */
@Composable
internal fun ReaderParagraphHighlightCompactV50(
    highlight: ReaderParagraphHighlightV50,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    val mark = highlight.tone.markColor(
        t,
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
                    .size(8.dp)
                    .background(
                        color = mark,
                        shape = CircleShape,
                    ),
            )

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            Text(
                text = highlight.chapterTitle
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "第 ${highlight.chapterIndex + 1} 章",
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
            text = highlight.normalizedExcerpt,
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = mark.copy(
                        alpha = 0.68f,
                    ),
                    shape = CircleShape,
                ),
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Save Button                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderHighlightSaveButtonV50(
    enabled: Boolean,
    existing: Boolean,
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
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (existing) {
                Icons.Rounded.Check
            } else {
                Icons.Rounded.BookmarkBorder
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),

            /*
             * v3 没有 primaryForeground Token。
             *
             * Light primary 较深 -> card 是白色。
             * Dark primary 较亮 -> card 是深色。
             *
             * 因此直接使用 card 作为高对比前景。
             */
            tint = t.card,
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text = if (existing) {
                "更新划线"
            } else {
                "保存划线"
            },
            style = MaterialTheme.typography.labelLarge,
            color = t.card,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Delete Button                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderHighlightDeleteButtonV50(
    enabled: Boolean,
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
                color = t.destructive.copy(
                    alpha = 0.08f,
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.destructive.copy(
                    alpha = if (enabled) {
                        0.38f
                    } else {
                        0.16f
                    },
                ),
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = t.destructive.copy(
                alpha = if (enabled) {
                    1f
                } else {
                    0.4f
                },
            ),
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text = "取消划线",
            style = MaterialTheme.typography.labelLarge,
            color = t.destructive.copy(
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
/*                               Icon Action                                  */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderHighlightIconActionV50(
    icon: ImageVector,
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
            contentDescription = null,
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
/*                                  Helpers                                   */
/* -------------------------------------------------------------------------- */

private fun readerParagraphHighlightTimeLabelV50(
    timestamp: Long,
): String {
    val diff = (
        System.currentTimeMillis() - timestamp
        ).coerceAtLeast(
        0L,
    )

    val minute = 60_000L
    val hour = minute * 60L
    val day = hour * 24L

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

        diff < day * 7L -> {
            "${diff / day} 天前"
        }

        else -> {
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault(),
            ).format(
                Date(timestamp),
            )
        }
    }
}
