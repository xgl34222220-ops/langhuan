package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import java.util.Locale
import kotlin.math.roundToInt


@Composable
internal fun BookDetailScreenV50(
    state: LibraryExperienceState,
    continueChapterNumber: Int,
    readChapterCount: Int,
    blueprintChapterCount: Int,
    generatingChapterNumber: Int?,
    bookmarkCount: Int,
    noteCount: Int,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onContinueReading: (Int) -> Unit,
    onAiCreation: () -> Unit,
    onEnterStory: () -> Unit,
    onCoverStudio: () -> Unit,
    onBookmarksAndNotes: () -> Unit,
    onEditChapter: (ChapterDraft) -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val book =
        state.openedBook
            ?: return

    var introExpanded by
        rememberSaveable(
            book.id,
        ) {
            mutableStateOf(
                false,
            )
        }

    val chapters =
        remember(
            state.chapters,
        ) {
            state.chapters.sortedWith(
                compareBy<ChapterDraft> {
                    it.readingOrder
                }.thenBy {
                    it.chapterNumber
                },
            )
        }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                t.background,
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
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
                "book-detail-header",
        ) {
            BookDetailHeaderV50(
                onBack =
                    onBack,
                onMore =
                    onMore,
            )
        }

        item(
            key =
                "book-detail-title",
        ) {
            BookDetailTitleBlockV50(
                title =
                    book.title,
                words =
                    book.currentWords,
                premise =
                    book.premise,
                expanded =
                    introExpanded,
                onToggleIntro = {
                    introExpanded =
                        !introExpanded
                },
            )
        }

        item(
            key =
                "book-detail-actions",
        ) {
            BookDetailActionSectionV50(
                continueChapterNumber =
                    continueChapterNumber,
                onContinueReading = {
                    onContinueReading(
                        continueChapterNumber,
                    )
                },
                onAiCreation =
                    onAiCreation,
                onEnterStory =
                    onEnterStory,
                onCoverStudio =
                    onCoverStudio,
            )
        }

        item(
            key =
                "book-detail-notes",
        ) {
            BookDetailNotesCardV50(
                bookmarkCount =
                    bookmarkCount,
                noteCount =
                    noteCount,
                onClick =
                    onBookmarksAndNotes,
            )
        }

        item(
            key =
                "book-detail-progress",
        ) {
            BookDetailProgressV50(
                readChapterCount =
                    readChapterCount,
                continueChapterNumber =
                    continueChapterNumber,
                generatingChapterNumber =
                    generatingChapterNumber,
                blueprintChapterCount =
                    blueprintChapterCount,
            )
        }

        item(
            key =
                "book-detail-directory-title",
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        "目录",
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                    style =
                        MaterialTheme.typography.titleLarge,
                    color =
                        t.foreground,
                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text =
                        "${chapters.size} 章",
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        t.mutedForeground,
                )
            }
        }

        items(
            items =
                chapters,
            key = {
                it.id
            },
        ) {
            chapter ->

            BookDetailChapterRowV50(
                chapter =
                    chapter,
                generating =
                    generatingChapterNumber ==
                        chapter.chapterNumber,
                onEdit = {
                    onEditChapter(
                        chapter,
                    )
                },
            )
        }
    }
}


@Composable
private fun BookDetailHeaderV50(
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        BookDetailIconButtonV50(
            icon =
                Icons.Rounded.ArrowBack,
            description =
                "返回",
            onClick =
                onBack,
        )

        Spacer(
            Modifier.weight(
                1f,
            ),
        )

        BookDetailIconButtonV50(
            icon =
                Icons.Rounded.MoreHoriz,
            description =
                "更多操作",
            onClick =
                onMore,
        )
    }
}


@Composable
private fun BookDetailTitleBlockV50(
    title: String,
    words: Int,
    premise: String,
    expanded: Boolean,
    onToggleIntro: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Column(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                title.ifBlank {
                    "未命名作品"
                },
            style =
                MaterialTheme.typography.headlineLarge,
            color =
                t.foreground,
            fontFamily =
                FontFamily.Serif,
            fontWeight =
                FontWeight.SemiBold,
            maxLines =
                3,
            overflow =
                TextOverflow.Ellipsis,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Text(
                text =
                    bookDetailCompactWordsV50(
                        words,
                    ),
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    t.secondaryForeground,
                fontWeight =
                    FontWeight.Medium,
            )

            Spacer(
                Modifier.width(
                    t.space3,
                ),
            )

            val shape =
                RoundedCornerShape(
                    t.radiusMd,
                )

            Text(
                text =
                    if (expanded) {
                        "收起简介"
                    } else {
                        "展开简介"
                    },
                modifier = Modifier
                    .background(
                        color =
                            t.background,
                        shape =
                            shape,
                    )
                    .border(
                        width =
                            t.space1 /
                                4f,
                        color =
                            t.border,
                        shape =
                            shape,
                    )
                    .clickable(
                        onClick =
                            onToggleIntro,
                    )
                    .padding(
                        horizontal =
                            t.space3,
                        vertical =
                            t.space2,
                    ),
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    t.secondaryForeground,
                fontWeight =
                    FontWeight.Medium,
            )
        }

        if (
            expanded
        ) {
            Spacer(
                Modifier.height(
                    t.space3,
                ),
            )

            Text(
                text =
                    premise.ifBlank {
                        "暂无作品简介"
                    },
                style =
                    MaterialTheme.typography.bodyLarge,
                color =
                    t.secondaryForeground,
            )
        }
    }
}


@Composable
private fun BookDetailActionSectionV50(
    continueChapterNumber: Int,
    onContinueReading: () -> Unit,
    onAiCreation: () -> Unit,
    onEnterStory: () -> Unit,
    onCoverStudio: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                t.space2,
            ),
    ) {
        BookDetailPrimaryActionV50(
            icon =
                Icons.Rounded.PlayArrow,
            text =
                "继续阅读 第 ${continueChapterNumber.coerceAtLeast(1)} 章",
            onClick =
                onContinueReading,
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    t.space2,
                ),
        ) {
            BookDetailSecondaryActionV50(
                icon =
                    Icons.Rounded.AutoAwesome,
                text =
                    "AI 创作",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onAiCreation,
            )

            BookDetailSecondaryActionV50(
                icon =
                    Icons.Rounded.MenuBook,
                text =
                    "进入故事",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onEnterStory,
            )

            BookDetailSecondaryActionV50(
                icon =
                    Icons.Rounded.Image,
                text =
                    "封面工作室",
                modifier =
                    Modifier.weight(
                        1f,
                    ),
                onClick =
                    onCoverStudio,
            )
        }
    }
}


@Composable
private fun BookDetailPrimaryActionV50(
    icon: ImageVector,
    text: String,
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
            .height(
                t.space6 +
                    t.space5,
            )
            .background(
                color =
                    t.primary,
                shape =
                    shape,
            )
            .border(
                width =
                    t.space1 /
                        4f,
                color =
                    t.primary,
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            ),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    t.space5,
                ),
            tint =
                t.card,
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Text(
            text =
                text,
            style =
                MaterialTheme.typography.titleMedium,
            color =
                t.card,
            fontWeight =
                FontWeight.SemiBold,
        )
    }
}


@Composable
private fun BookDetailSecondaryActionV50(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Column(
        modifier = modifier
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    t.space1 /
                        4f,
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
                vertical =
                    t.space3,
                horizontal =
                    t.space2,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    t.space5,
                ),
            tint =
                t.primary,
        )

        Spacer(
            Modifier.height(
                t.space1,
            ),
        )

        Text(
            text =
                text,
            style =
                MaterialTheme.typography.labelMedium,
            color =
                t.secondaryForeground,
            fontWeight =
                FontWeight.Medium,
            maxLines =
                1,
        )
    }
}


@Composable
private fun BookDetailNotesCardV50(
    bookmarkCount: Int,
    noteCount: Int,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusXl,
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
                    t.space1 /
                        4f,
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
                t.space4,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(
                    t.space6 +
                        t.space2,
                )
                .background(
                    color =
                        t.accent,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                )
                .border(
                    width =
                        t.space1 /
                            4f,
                    color =
                        t.border,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.BookmarkBorder,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(
                        t.space5,
                    ),
                tint =
                    t.primary,
            )
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Column(
            modifier =
                Modifier.weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    "书签与笔记",
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
            )

            Spacer(
                Modifier.height(
                    t.space1,
                ),
            )

            Text(
                text =
                    "回到做过标记的段落",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    t.mutedForeground,
            )
        }

        Text(
            text =
                "${bookmarkCount.coerceAtLeast(0)} 个书签 ${noteCount.coerceAtLeast(0)} 条笔记",
            style =
                MaterialTheme.typography.labelMedium,
            color =
                t.secondaryForeground,
            fontWeight =
                FontWeight.Medium,
        )

        Spacer(
            Modifier.width(
                t.space2,
            ),
        )

        Icon(
            imageVector =
                Icons.Rounded.ChevronRight,
            contentDescription =
                null,
            modifier =
                Modifier.size(
                    t.space5,
                ),
            tint =
                t.mutedForeground,
        )
    }
}


@Composable
private fun BookDetailProgressV50(
    readChapterCount: Int,
    continueChapterNumber: Int,
    generatingChapterNumber: Int?,
    blueprintChapterCount: Int,
) {
    val t =
        LocalLanghuanUiTokens.current

    val safeRead =
        readChapterCount
            .coerceAtLeast(
                0,
            )

    val safeBlueprint =
        blueprintChapterCount
            .coerceAtLeast(
                1,
            )

    val progress =
        (
            safeRead.toFloat() /
                safeBlueprint.toFloat()
            )
            .coerceIn(
                0f,
                1f,
            )

    Column(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Text(
            text = buildString {
                append(
                    "读到第 ${continueChapterNumber.coerceAtLeast(1)} 章",
                )

                generatingChapterNumber
                    ?.takeIf {
                        it >
                            0
                    }
                    ?.let {
                        append(
                            "，第 $it 章正在生成",
                        )
                    }

                append(
                    "，蓝图排到第 $safeBlueprint 章",
                )
            },
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                t.secondaryForeground,
        )

        Spacer(
            Modifier.height(
                t.space3,
            ),
        )

        LinearProgressIndicator(
            progress = {
                progress
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    t.space1,
                ),
            color =
                t.primary,
            trackColor =
                t.border,
        )

        Spacer(
            Modifier.height(
                t.space2,
            ),
        )

        Text(
            text =
                "$safeBlueprint 章中已读 $safeRead 章，正在读第 ${continueChapterNumber.coerceAtLeast(1)} 章",
            style =
                MaterialTheme.typography.bodySmall,
            color =
                t.mutedForeground,
        )
    }
}


@Composable
private fun BookDetailChapterRowV50(
    chapter: ChapterDraft,
    generating: Boolean,
    onEdit: () -> Unit,
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
                    t.space1 /
                        4f,
                color =
                    t.border,
                shape =
                    shape,
            )
            .padding(
                t.space4,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(
                    t.space6 +
                        t.space1,
                )
                .background(
                    color =
                        if (generating) {
                            t.goldContainer
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
                        t.space1 /
                            4f,
                    color =
                        t.border,
                    shape =
                        RoundedCornerShape(
                            t.radiusMd,
                        ),
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            if (generating) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(
                            t.space4,
                        ),
                    color =
                        t.gold,
                    strokeWidth =
                        t.space1 /
                            2f,
                )
            } else {
                Text(
                    text =
                        chapter.chapterNumber
                            .toString(),
                    style =
                        MaterialTheme.typography.labelLarge,
                    color =
                        t.secondaryForeground,
                    fontWeight =
                        FontWeight.SemiBold,
                )
            }
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        Column(
            modifier =
                Modifier.weight(
                    1f,
                ),
        ) {
            Text(
                text =
                    chapter.title
                        .ifBlank {
                            "第 ${chapter.chapterNumber} 章"
                        },
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    t.foreground,
                fontWeight =
                    FontWeight.SemiBold,
                maxLines =
                    2,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Spacer(
                Modifier.height(
                    t.space1,
                ),
            )

            Text(
                text =
                    if (generating) {
                        "正文生成中"
                    } else {
                        "${bookDetailCompactWordsV50(chapter.content.length)}字"
                    },
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    if (generating) {
                        t.goldForeground
                    } else {
                        t.mutedForeground
                    },
                fontWeight =
                    if (generating) {
                        FontWeight.Medium
                    } else {
                        FontWeight.Normal
                    },
            )
        }

        Spacer(
            Modifier.width(
                t.space3,
            ),
        )

        val buttonShape =
            RoundedCornerShape(
                t.radiusMd,
            )

        Row(
            modifier = Modifier
                .background(
                    color =
                        t.background,
                    shape =
                        buttonShape,
                )
                .border(
                    width =
                        t.space1 /
                            4f,
                    color =
                        t.border,
                    shape =
                        buttonShape,
                )
                .clickable(
                    onClick =
                        onEdit,
                )
                .padding(
                    horizontal =
                        t.space3,
                    vertical =
                        t.space2,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.EditNote,
                contentDescription =
                    null,
                modifier =
                    Modifier.size(
                        t.space4,
                    ),
                tint =
                    t.primary,
            )

            Spacer(
                Modifier.width(
                    t.space1,
                ),
            )

            Text(
                text =
                    "编辑第 ${chapter.chapterNumber} 章",
                style =
                    MaterialTheme.typography.labelMedium,
                color =
                    t.primary,
                fontWeight =
                    FontWeight.Medium,
                maxLines =
                    1,
            )
        }
    }
}


@Composable
private fun BookDetailIconButtonV50(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val t =
        LocalLanghuanUiTokens.current

    val shape =
        RoundedCornerShape(
            t.radiusMd,
        )

    Box(
        modifier = Modifier
            .size(
                t.space6 +
                    t.space2,
            )
            .background(
                color =
                    t.card,
                shape =
                    shape,
            )
            .border(
                width =
                    t.space1 /
                        4f,
                color =
                    t.border,
                shape =
                    shape,
            )
            .clickable(
                onClick =
                    onClick,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Icon(
            imageVector =
                icon,
            contentDescription =
                description,
            modifier =
                Modifier.size(
                    t.space5,
                ),
            tint =
                t.secondaryForeground,
        )
    }
}


private fun bookDetailCompactWordsV50(
    words: Int,
): String {
    val safe =
        words
            .coerceAtLeast(
                0,
            )

    return when {
        safe <
            1_000 -> {
            safe.toString()
        }

        safe <
            10_000 -> {
            val value =
                safe /
                    100f

            val rendered =
                if (
                    value.roundToInt()
                        .toFloat() ==
                    value
                ) {
                    value
                        .roundToInt()
                        .toString()
                } else {
                    String.format(
                        Locale.getDefault(),
                        "%.1f",
                        value,
                    )
                }

            "$rendered 百"
        }

        else -> {
            val value =
                safe /
                    10_000f

            val rendered =
                String.format(
                    Locale.getDefault(),
                    "%.1f",
                    value,
                )
                    .trimEnd(
                        '0',
                    )
                    .trimEnd(
                        '.',
                    )

            "$rendered 万"
        }
    }
}
