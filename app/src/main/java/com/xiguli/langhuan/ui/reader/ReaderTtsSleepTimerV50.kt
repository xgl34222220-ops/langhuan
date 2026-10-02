package com.xiguli.langhuan.ui

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LanghuanMotionV31
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * Reader V50 · 听书定时关闭。
 *
 * 本文件负责：
 *
 * 1. 定时关闭选项：
 *    - 不定时
 *    - 10 分钟
 *    - 20 分钟
 *    - 30 分钟
 *    - 60 分钟
 *    - 本章结束
 *
 * 2. 分钟级定时关闭的实时倒计时。
 *
 * 3. 到期自动调用 [stopPlayback] 停止 TTS。
 *
 * 4. "本章结束"模式下，在 Reader 告知章节播放完成时自动停止。
 *
 * 5. 听书段落进度记忆：
 *    - bookId
 *    - chapterIndex
 *    - paragraphOffset
 *    - 更新时间
 *
 * 6. 进程内恢复定时器；时间型定时器同时保存绝对截止时间，
 *    Reader 重建后仍能继续倒计时。
 *
 * UI 使用 v3 Token，不使用投影，仅通过 1dp border 分层。
 */


/* -------------------------------------------------------------------------- */
/*                               Timer Option                                 */
/* -------------------------------------------------------------------------- */

internal enum class ReaderTtsSleepOptionV50(
    val key: String,
    val label: String,
    val minutes: Int?,
) {
    OFF(
        key = "off",
        label = "不定时",
        minutes = null,
    ),

    MINUTES_10(
        key = "10m",
        label = "10 分钟",
        minutes = 10,
    ),

    MINUTES_20(
        key = "20m",
        label = "20 分钟",
        minutes = 20,
    ),

    MINUTES_30(
        key = "30m",
        label = "30 分钟",
        minutes = 30,
    ),

    MINUTES_60(
        key = "60m",
        label = "60 分钟",
        minutes = 60,
    ),

    END_OF_CHAPTER(
        key = "chapter",
        label = "本章结束",
        minutes = null,
    );

    val isTimed: Boolean
        get() = minutes != null

    companion object {
        fun fromKey(
            key: String?,
        ): ReaderTtsSleepOptionV50 {
            return values().firstOrNull {
                it.key == key
            } ?: OFF
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                          Paragraph Progress                                */
/* -------------------------------------------------------------------------- */

internal data class ReaderTtsParagraphProgressV50(
    val bookId: String,
    val chapterIndex: Int,
    val paragraphOffset: Int,
    val updatedAt: Long,
)


/* -------------------------------------------------------------------------- */
/*                                  Store                                    */
/* -------------------------------------------------------------------------- */

internal object ReaderTtsSleepTimerStoreV50 {

    private const val TIMER_OPTION =
        "reader_tts_sleep_v50_option"

    private const val TIMER_DEADLINE =
        "reader_tts_sleep_v50_deadline"

    private const val PROGRESS_PREFIX =
        "reader_tts_progress_v50_"


    /* ---------------------------------------------------------------------- */
    /*                                Timer                                   */
    /* ---------------------------------------------------------------------- */

    fun loadOption(
        prefs: SharedPreferences,
    ): ReaderTtsSleepOptionV50 {
        return ReaderTtsSleepOptionV50.fromKey(
            runCatching {
                prefs.getString(
                    TIMER_OPTION,
                    ReaderTtsSleepOptionV50.OFF.key,
                )
            }.getOrNull(),
        )
    }


    fun loadDeadline(
        prefs: SharedPreferences,
    ): Long {
        return runCatching {
            prefs.getLong(
                TIMER_DEADLINE,
                0L,
            )
        }.getOrDefault(
            0L,
        )
    }


    fun saveTimer(
        prefs: SharedPreferences,
        option: ReaderTtsSleepOptionV50,
        deadlineMillis: Long,
    ): Boolean {
        return prefs.edit()
            .putString(
                TIMER_OPTION,
                option.key,
            )
            .putLong(
                TIMER_DEADLINE,
                deadlineMillis.coerceAtLeast(0L),
            )
            .commit()
    }


    fun clearTimer(
        prefs: SharedPreferences,
    ): Boolean {
        return prefs.edit()
            .putString(
                TIMER_OPTION,
                ReaderTtsSleepOptionV50.OFF.key,
            )
            .putLong(
                TIMER_DEADLINE,
                0L,
            )
            .commit()
    }


    /* ---------------------------------------------------------------------- */
    /*                              Progress                                  */
    /* ---------------------------------------------------------------------- */

    private fun progressKey(
        bookId: String,
    ): String {
        require(bookId.isNotBlank()) {
            "书籍标识无效"
        }

        return PROGRESS_PREFIX + bookId
    }


    /**
     * 使用一个紧凑字符串保存：
     *
     * chapterIndex|paragraphOffset|updatedAt
     *
     * 不保存正文文本，
     * 避免 SharedPreferences 长期堆积小说内容。
     */
    fun saveParagraphProgress(
        prefs: SharedPreferences,
        bookId: String,
        chapterIndex: Int,
        paragraphOffset: Int,
    ): Result<ReaderTtsParagraphProgressV50> =
        runCatching {
            require(bookId.isNotBlank()) {
                "书籍标识无效，无法保存听书进度"
            }

            require(chapterIndex >= 0) {
                "章节索引无效，无法保存听书进度"
            }

            require(paragraphOffset >= 0) {
                "段落位置无效，无法保存听书进度"
            }

            val now = System.currentTimeMillis()

            val progress = ReaderTtsParagraphProgressV50(
                bookId = bookId,
                chapterIndex = chapterIndex,
                paragraphOffset = paragraphOffset,
                updatedAt = now,
            )

            val raw = buildString {
                append(progress.chapterIndex)
                append('|')
                append(progress.paragraphOffset)
                append('|')
                append(progress.updatedAt)
            }

            val committed = prefs.edit()
                .putString(
                    progressKey(bookId),
                    raw,
                )
                .commit()

            check(committed) {
                "听书段落进度保存失败"
            }

            progress
        }


    fun loadParagraphProgress(
        prefs: SharedPreferences,
        bookId: String,
    ): Result<ReaderTtsParagraphProgressV50?> =
        runCatching {
            require(bookId.isNotBlank()) {
                "书籍标识无效"
            }

            val raw = prefs.getString(
                progressKey(bookId),
                null,
            ) ?: return@runCatching null

            val parts = raw.split('|')

            require(parts.size == 3) {
                "听书进度数据格式无效"
            }

            val chapterIndex = parts[0]
                .toIntOrNull()
                ?: error("听书章节进度无效")

            val paragraphOffset = parts[1]
                .toIntOrNull()
                ?: error("听书段落进度无效")

            val updatedAt = parts[2]
                .toLongOrNull()
                ?: error("听书进度时间无效")

            require(chapterIndex >= 0) {
                "听书章节进度无效"
            }

            require(paragraphOffset >= 0) {
                "听书段落进度无效"
            }

            ReaderTtsParagraphProgressV50(
                bookId = bookId,
                chapterIndex = chapterIndex,
                paragraphOffset = paragraphOffset,
                updatedAt = updatedAt,
            )
        }


    fun clearParagraphProgress(
        prefs: SharedPreferences,
        bookId: String,
    ): Boolean {
        return runCatching {
            prefs.edit()
                .remove(
                    progressKey(bookId),
                )
                .commit()
        }.getOrDefault(
            false,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Timer Controller                              */
/* -------------------------------------------------------------------------- */

/**
 * TTS 定时关闭状态控制器。
 *
 * 推荐 Reader 创建方式：
 *
 * ReaderTtsSleepTimerV50(
 *     prefs = prefs,
 *     stopPlayback = {
 *         speech.stop()
 *         listening = false
 *     },
 * )
 *
 * 当 TTS onChunkStart(offset) 回调触发时：
 *
 * timer.rememberParagraph(
 *     bookId = book.id,
 *     chapterIndex = chapterIndex,
 *     paragraphOffset = offset,
 * )
 *
 * 当本章听完后：
 *
 * timer.onChapterFinished()
 */
@Stable
internal class ReaderTtsSleepTimerV50(
    private val prefs: SharedPreferences,
    private val stopPlayback: () -> Unit,
    private val onExpired: () -> Unit = {},
    private val handler: Handler = Handler(
        Looper.getMainLooper(),
    ),
    private val nowMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {

    var option by mutableStateOf(
        ReaderTtsSleepOptionV50.OFF,
    )
        private set

    var remainingMillis by mutableLongStateOf(
        0L,
    )
        private set

    var deadlineMillis by mutableLongStateOf(
        0L,
    )
        private set

    var active by mutableStateOf(
        false,
    )
        private set


    private var released = false


    private val ticker = object : Runnable {
        override fun run() {
            if (
                released ||
                !active ||
                !option.isTimed
            ) {
                return
            }

            val remaining = (
                deadlineMillis - nowMillis()
                ).coerceAtLeast(
                0L,
            )

            remainingMillis = remaining

            if (remaining <= 0L) {
                expire()
                return
            }

            handler.postDelayed(
                this,
                nextTickDelay(
                    remaining,
                ),
            )
        }
    }


    init {
        restore()
    }


    /* ---------------------------------------------------------------------- */
    /*                                  Start                                 */
    /* ---------------------------------------------------------------------- */

    fun select(
        next: ReaderTtsSleepOptionV50,
    ) {
        check(!released) {
            "听书定时器已释放"
        }

        handler.removeCallbacks(
            ticker,
        )

        when (next) {
            ReaderTtsSleepOptionV50.OFF -> {
                cancel()
            }

            ReaderTtsSleepOptionV50.END_OF_CHAPTER -> {
                option = next
                active = true
                deadlineMillis = 0L
                remainingMillis = 0L

                ReaderTtsSleepTimerStoreV50.saveTimer(
                    prefs = prefs,
                    option = next,
                    deadlineMillis = 0L,
                )
            }

            else -> {
                val minutes = requireNotNull(
                    next.minutes,
                )

                val duration =
                    minutes * 60_000L

                val deadline =
                    nowMillis() + duration

                option = next
                active = true
                deadlineMillis = deadline
                remainingMillis = duration

                ReaderTtsSleepTimerStoreV50.saveTimer(
                    prefs = prefs,
                    option = next,
                    deadlineMillis = deadline,
                )

                handler.post(
                    ticker,
                )
            }
        }
    }


    /* ---------------------------------------------------------------------- */
    /*                                 Cancel                                 */
    /* ---------------------------------------------------------------------- */

    fun cancel() {
        handler.removeCallbacks(
            ticker,
        )

        option = ReaderTtsSleepOptionV50.OFF

        active = false

        deadlineMillis = 0L

        remainingMillis = 0L

        ReaderTtsSleepTimerStoreV50.clearTimer(
            prefs,
        )
    }


    /* ---------------------------------------------------------------------- */
    /*                            Chapter Finished                            */
    /* ---------------------------------------------------------------------- */

    /**
     * Reader 在当前章节 TTS 队列播放完成时调用。
     *
     * 只有用户选择"本章结束"时才会停止听书。
     */
    fun onChapterFinished() {
        if (
            released ||
            !active ||
            option != ReaderTtsSleepOptionV50.END_OF_CHAPTER
        ) {
            return
        }

        expire()
    }


    /* ---------------------------------------------------------------------- */
    /*                              Paragraph                                 */
    /* ---------------------------------------------------------------------- */

    /**
     * 每次 TTS 真正开始朗读一个 ReaderTtsChunkV35 时调用。
     *
     * paragraphOffset 应直接使用：
     *
     * ReaderTtsChunkV35.offset
     *
     * 这样恢复时可以直接：
     *
     * readerTtsChunksV35(
     *     body,
     *     progress.paragraphOffset
     * )
     */
    fun rememberParagraph(
        bookId: String,
        chapterIndex: Int,
        paragraphOffset: Int,
    ): Result<ReaderTtsParagraphProgressV50> {
        return ReaderTtsSleepTimerStoreV50
            .saveParagraphProgress(
                prefs = prefs,
                bookId = bookId,
                chapterIndex = chapterIndex,
                paragraphOffset = paragraphOffset,
            )
    }


    fun paragraphProgress(
        bookId: String,
    ): Result<ReaderTtsParagraphProgressV50?> {
        return ReaderTtsSleepTimerStoreV50
            .loadParagraphProgress(
                prefs = prefs,
                bookId = bookId,
            )
    }


    /* ---------------------------------------------------------------------- */
    /*                               Restore                                  */
    /* ---------------------------------------------------------------------- */

    private fun restore() {
        val storedOption =
            ReaderTtsSleepTimerStoreV50.loadOption(
                prefs,
            )

        when {
            storedOption ==
                ReaderTtsSleepOptionV50.OFF -> {
                option =
                    ReaderTtsSleepOptionV50.OFF

                active = false

                deadlineMillis = 0L

                remainingMillis = 0L
            }

            storedOption ==
                ReaderTtsSleepOptionV50.END_OF_CHAPTER -> {
                /*
                 * "本章结束"是当前一次播放会话的语义，
                 * App/Reader 被完全重建后不应该莫名继续等待
                 * 一个已经失去上下文的旧章节。
                 */
                ReaderTtsSleepTimerStoreV50.clearTimer(
                    prefs,
                )

                option =
                    ReaderTtsSleepOptionV50.OFF

                active = false
            }

            storedOption.isTimed -> {
                val storedDeadline =
                    ReaderTtsSleepTimerStoreV50
                        .loadDeadline(
                            prefs,
                        )

                val remaining = (
                    storedDeadline - nowMillis()
                    ).coerceAtLeast(
                    0L,
                )

                if (
                    storedDeadline <= 0L ||
                    remaining <= 0L
                ) {
                    /*
                     * 如果 Reader 重建时旧定时已经到期，
                     * 立即清除并确保 TTS 停止。
                     */
                    option = storedOption
                    active = true
                    deadlineMillis =
                        storedDeadline
                    remainingMillis = 0L

                    handler.post {
                        if (!released) {
                            expire()
                        }
                    }
                } else {
                    option = storedOption
                    active = true
                    deadlineMillis =
                        storedDeadline
                    remainingMillis =
                        remaining

                    handler.post(
                        ticker,
                    )
                }
            }
        }
    }


    /* ---------------------------------------------------------------------- */
    /*                                Expire                                  */
    /* ---------------------------------------------------------------------- */

    private fun expire() {
        if (
            released ||
            !active
        ) {
            return
        }

        handler.removeCallbacks(
            ticker,
        )

        active = false

        remainingMillis = 0L

        /*
         * 先清空持久化状态，
         * 再停止 TTS。
         *
         * 这样即使 stopPlayback 内部触发 Reader 状态重建，
         * 新实例也不会重新加载已经到期的旧 Timer。
         */
        ReaderTtsSleepTimerStoreV50.clearTimer(
            prefs,
        )

        option =
            ReaderTtsSleepOptionV50.OFF

        deadlineMillis = 0L

        stopPlayback()

        onExpired()
    }


    /* ---------------------------------------------------------------------- */
    /*                               Release                                  */
    /* ---------------------------------------------------------------------- */

    /**
     * 只释放 Controller 自身的 Handler。
     *
     * 注意：
     *
     * 不调用 cancel()，
     * 时间型 Timer 的 deadline 会继续保留，
     * Reader 重建时可继续恢复倒计时。
     */
    fun release() {
        if (released) {
            return
        }

        released = true

        handler.removeCallbacks(
            ticker,
        )
    }


    private fun nextTickDelay(
        remaining: Long,
    ): Long {
        /*
         * 最后一分钟每秒刷新，
         * 其余时间按秒也足够轻量，
         * 同时 UI 倒计时看起来连续。
         */
        return if (remaining <= 60_000L) {
            1_000L
        } else {
            1_000L
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Formatting                                   */
/* -------------------------------------------------------------------------- */

internal fun readerTtsSleepRemainingLabelV50(
    remainingMillis: Long,
): String {
    val totalSeconds = (
        remainingMillis.coerceAtLeast(
            0L,
        ) + 999L
        ) / 1_000L

    val hours =
        totalSeconds / 3_600L

    val minutes =
        (totalSeconds % 3_600L) / 60L

    val seconds =
        totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(
            hours,
            minutes,
            seconds,
        )
    } else {
        "%02d:%02d".format(
            minutes,
            seconds,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                              Timer Panel                                   */
/* -------------------------------------------------------------------------- */

/**
 * Reader TTS 设置里的"定时关闭"区块。
 *
 * 可直接嵌入 ReaderMenu / TTS BottomSheet。
 */
@Composable
internal fun ReaderTtsSleepTimerPanelV50(
    timer: ReaderTtsSleepTimerV50,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
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
                    imageVector = Icons.Rounded.HourglassBottom,
                    contentDescription = null,
                    modifier = Modifier.size(
                        20.dp,
                    ),
                    tint = t.accentForeground,
                )
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier = Modifier.weight(
                    1f,
                ),
            ) {
                Text(
                    text = "定时关闭",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text = when {
                        !timer.active -> {
                            "听书将持续播放"
                        }

                        timer.option ==
                            ReaderTtsSleepOptionV50.END_OF_CHAPTER -> {
                            "将在本章朗读结束后停止"
                        }

                        else -> {
                            "剩余 ${
                                readerTtsSleepRemainingLabelV50(
                                    timer.remainingMillis,
                                )
                            }"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (timer.active) {
                        t.primary
                    } else {
                        t.mutedForeground
                    },
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space4,
            ),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(
                t.space2,
            ),
        ) {
            ReaderTtsSleepOptionRowV50(
                option = ReaderTtsSleepOptionV50.OFF,
                selected = timer.option ==
                    ReaderTtsSleepOptionV50.OFF,
                onClick = {
                    timer.select(
                        ReaderTtsSleepOptionV50.OFF,
                    )
                },
            )

            ReaderTtsSleepOptionRowV50(
                option = ReaderTtsSleepOptionV50.MINUTES_10,
                selected = timer.option ==
                    ReaderTtsSleepOptionV50.MINUTES_10,
                onClick = {
                    timer.select(
                        ReaderTtsSleepOptionV50.MINUTES_10,
                    )
                },
            )

            ReaderTtsSleepOptionRowV50(
                option = ReaderTtsSleepOptionV50.MINUTES_20,
                selected = timer.option ==
                    ReaderTtsSleepOptionV50.MINUTES_20,
                onClick = {
                    timer.select(
                        ReaderTtsSleepOptionV50.MINUTES_20,
                    )
                },
            )

            ReaderTtsSleepOptionRowV50(
                option = ReaderTtsSleepOptionV50.MINUTES_30,
                selected = timer.option ==
                    ReaderTtsSleepOptionV50.MINUTES_30,
                onClick = {
                    timer.select(
                        ReaderTtsSleepOptionV50.MINUTES_30,
                    )
                },
            )

            ReaderTtsSleepOptionRowV50(
                option = ReaderTtsSleepOptionV50.MINUTES_60,
                selected = timer.option ==
                    ReaderTtsSleepOptionV50.MINUTES_60,
                onClick = {
                    timer.select(
                        ReaderTtsSleepOptionV50.MINUTES_60,
                    )
                },
            )

            ReaderTtsSleepOptionRowV50(
                option = ReaderTtsSleepOptionV50.END_OF_CHAPTER,
                selected = timer.option ==
                    ReaderTtsSleepOptionV50.END_OF_CHAPTER,
                onClick = {
                    timer.select(
                        ReaderTtsSleepOptionV50.END_OF_CHAPTER,
                    )
                },
            )
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                               Option Row                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun ReaderTtsSleepOptionRowV50(
    option: ReaderTtsSleepOptionV50,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val interaction =
        remember {
            MutableInteractionSource()
        }

    val background by animateColorAsState(
        targetValue = if (selected) {
            t.accent
        } else {
            t.card
        },
        animationSpec = tween(
            LanghuanMotionV31.MEDIUM,
        ),
        label = "ttsSleepOptionBackground",
    )

    val border by animateColorAsState(
        targetValue = if (selected) {
            t.primary
        } else {
            t.border
        },
        animationSpec = tween(
            LanghuanMotionV31.MEDIUM,
        ),
        label = "ttsSleepOptionBorder",
    )

    val foreground by animateColorAsState(
        targetValue = if (selected) {
            t.accentForeground
        } else {
            t.secondaryForeground
        },
        animationSpec = tween(
            LanghuanMotionV31.MEDIUM,
        ),
        label = "ttsSleepOptionForeground",
    )

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(
                48.dp,
            )
            .background(
                color = background,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = border,
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(
                horizontal = t.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = option.iconV50(),
            contentDescription = null,
            modifier = Modifier.size(
                19.dp,
            ),
            tint = foreground,
        )

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Text(
            text = option.label,
            modifier = Modifier.weight(
                1f,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                t.foreground
            } else {
                t.secondaryForeground
            },
            fontWeight = if (selected) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .size(
                        24.dp,
                    )
                    .background(
                        color = t.primary,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(
                        15.dp,
                    ),
                    /*
                     * v3 没有 primaryForeground。
                     *
                     * 浅色 primary 深、card 白；
                     * 深色 primary 亮、card 深。
                     */
                    tint = t.card,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                             Compact Status                                 */
/* -------------------------------------------------------------------------- */

/**
 * 听书主控制栏可以使用的紧凑倒计时状态。
 */
@Composable
internal fun ReaderTtsSleepTimerChipV50(
    timer: ReaderTtsSleepTimerV50,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val active = timer.active

    val shape = RoundedCornerShape(
        t.radiusMd,
    )

    Row(
        modifier = modifier
            .height(
                36.dp,
            )
            .background(
                color = if (active) {
                    t.accent
                } else {
                    t.input
                },
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    t.primary
                } else {
                    t.border
                },
                shape = shape,
            )
            .clickable(
                onClick = onClick,
            )
            .padding(
                horizontal = t.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (active) {
                Icons.Rounded.AccessTime
            } else {
                Icons.Rounded.TimerOff
            },
            contentDescription = null,
            modifier = Modifier.size(
                17.dp,
            ),
            tint = if (active) {
                t.primary
            } else {
                t.mutedForeground
            },
        )

        Spacer(
            Modifier.width(
                t.space1,
            ),
        )

        Text(
            text = when {
                !active -> {
                    "定时"
                }

                timer.option ==
                    ReaderTtsSleepOptionV50.END_OF_CHAPTER -> {
                    "本章结束"
                }

                else -> {
                    readerTtsSleepRemainingLabelV50(
                        timer.remainingMillis,
                    )
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                t.accentForeground
            } else {
                t.secondaryForeground
            },
            maxLines = 1,
        )
    }
}


/* -------------------------------------------------------------------------- */
/*                               Progress UI                                  */
/* -------------------------------------------------------------------------- */

/**
 * "上次听到这里"的轻量提示。
 *
 * Reader 恢复到记忆的段落前，可以展示一次。
 */
@Composable
internal fun ReaderTtsResumeProgressCardV50(
    progress: ReaderTtsParagraphProgressV50,
    chapterTitle: String?,
    modifier: Modifier = Modifier,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    val shape = RoundedCornerShape(
        t.radiusLg,
    )

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
            .padding(
                t.space4,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(
                        40.dp,
                    )
                    .background(
                        color = t.goldContainer,
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
                    imageVector = Icons.Rounded.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(
                        20.dp,
                    ),
                    tint = t.goldForeground,
                )
            }

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            Column(
                modifier = Modifier.weight(
                    1f,
                ),
            ) {
                Text(
                    text = "继续听书",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.foreground,
                )

                Spacer(
                    Modifier.height(
                        t.space1,
                    ),
                )

                Text(
                    text = chapterTitle
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "第 ${progress.chapterIndex + 1} 章",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.secondaryForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )

        Text(
            text = "已记住上次朗读到的段落，可从这里继续。",
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
        )

        Spacer(
            Modifier.height(
                t.space4,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .height(
                        40.dp,
                    )
                    .clickable(
                        onClick = onDismiss,
                    )
                    .padding(
                        horizontal = t.space3,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "忽略",
                    style = MaterialTheme.typography.labelLarge,
                    color = t.secondaryForeground,
                )
            }

            Spacer(
                Modifier.width(
                    t.space2,
                ),
            )

            val buttonShape = RoundedCornerShape(
                t.radiusMd,
            )

            Box(
                modifier = Modifier
                    .height(
                        40.dp,
                    )
                    .background(
                        color = t.primary,
                        shape = buttonShape,
                    )
                    .border(
                        width = 1.dp,
                        color = t.primary,
                        shape = buttonShape,
                    )
                    .clickable(
                        onClick = onResume,
                    )
                    .padding(
                        horizontal = t.space4,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "继续播放",
                    style = MaterialTheme.typography.labelLarge,
                    color = t.card,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/*                                  Icon                                      */
/* -------------------------------------------------------------------------- */

private fun ReaderTtsSleepOptionV50.iconV50(): ImageVector {
    return when (this) {
        ReaderTtsSleepOptionV50.OFF -> {
            Icons.Rounded.TimerOff
        }

        ReaderTtsSleepOptionV50.END_OF_CHAPTER -> {
            Icons.Rounded.MenuBook
        }

        else -> {
            Icons.Rounded.AccessTime
        }
    }
}
