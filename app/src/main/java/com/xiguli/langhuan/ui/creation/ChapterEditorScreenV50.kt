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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


@Composable
internal fun ChapterEditorScreenV50(
    draft: ChapterDraft,
    saving: Boolean,
    isDirty: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onAdvancedCheck: () -> Unit,
    onMore: () -> Unit,
    onRewrite: () -> Unit,
    onTextChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    var editorValue by remember(draft.id) {
        mutableStateOf(draft.content)
    }
    var hasSelection by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        ChapterEditorHeaderV50(
            chapterLabel = draft.chapterLabel,
            saved = !isDirty,
            onBack = onBack,
            onHistory = onHistory,
            onAdvancedCheck = onAdvancedCheck,
            onMore = onMore,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = t.space4,
                end = t.space4,
                top = t.space2,
                bottom = t.space4,
            ),
            verticalArrangement = Arrangement.spacedBy(t.space3),
        ) {
            item("editor-title") {
                Column {
                    Text(
                        text = draft.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(t.space1))

                    Text(
                        text = "${draft.wordCount} 字",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
            }

            item("editor-hint") {
                ChapterRewriteHintV50(
                    hasSelection = hasSelection,
                    onRewrite = onRewrite,
                )
            }

            item("editor-body") {
                val bodyShape = RoundedCornerShape(t.radiusLg)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.card, bodyShape)
                        .border(
                            width = 1.dp,
                            color = t.border,
                            shape = bodyShape,
                        )
                        .padding(t.space4),
                ) {
                    BasicTextField(
                        value = editorValue,
                        onValueChange = {
                            editorValue = it
                            onTextChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = t.foreground,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6,
                        ),
                        cursorBrush = SolidColor(t.primary),
                        onTextLayout = { layout ->
                            hasSelection = layout.hasVisualOverflow
                        },
                    )
                }
            }
        }

        ChapterEditorBottomBarV50(
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            saving = saving,
            isDirty = isDirty,
            onPrevious = onPrevious,
            onNext = onNext,
        )
    }
}


@Composable
private fun ChapterEditorHeaderV50(
    chapterLabel: String,
    saved: Boolean,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onAdvancedCheck: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = t.space2,
                    end = t.space2,
                    top = t.space2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChapterEditorIconV50(
                icon = Icons.Rounded.ArrowBack,
                description = "返回",
                onClick = onBack,
            )

            Text(
                text = chapterLabel,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = t.space2),
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )

            ChapterEditorHeaderActionV50(
                icon = Icons.Rounded.History,
                text = "版本历史",
                onClick = onHistory,
            )

            ChapterEditorHeaderActionV50(
                icon = Icons.Rounded.PlaylistAddCheck,
                text = "高级检查",
                onClick = onAdvancedCheck,
            )

            ChapterEditorIconV50(
                icon = Icons.Rounded.MoreVert,
                description = "更多",
                onClick = onMore,
            )
        }

        Text(
            text = if (saved) "已保存" else "未保存",
            modifier = Modifier.padding(
                start = t.space4,
                end = t.space4,
                top = t.space1,
                bottom = t.space2,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (saved) t.primary else t.goldForeground,
        )
    }
}


@Composable
private fun ChapterRewriteHintV50(
    hasSelection: Boolean,
    onRewrite: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = t.accent,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .clickable(
                enabled = hasSelection,
                onClick = onRewrite,
            )
            .padding(t.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (hasSelection) {
                t.primary
            } else {
                t.mutedForeground
            },
        )

        Spacer(Modifier.width(t.space2))

        Text(
            text = if (hasSelection) {
                "已选中一段 · 使用 AI 局部精修"
            } else {
                "选中一段，试试 AI 局部精修"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (hasSelection) {
                t.accentForeground
            } else {
                t.mutedForeground
            },
        )
    }
}


@Composable
private fun ChapterEditorBottomBarV50(
    hasPrevious: Boolean,
    hasNext: Boolean,
    saving: Boolean,
    isDirty: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.background)
            .navigationBarsPadding()
            .padding(
                start = t.space4,
                end = t.space4,
                top = t.space2,
                bottom = t.space3,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChapterEditorChapterButtonV50(
            icon = Icons.Rounded.ChevronLeft,
            text = "上一章",
            enabled = hasPrevious,
            modifier = Modifier.weight(1f),
            onClick = onPrevious,
        )

        Spacer(Modifier.width(t.space2))

        Box(
            modifier = Modifier
                .weight(1.35f)
                .height(44.dp)
                .background(
                    color = t.input,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    saving -> "正在自动保存"
                    isDirty -> "等待自动保存"
                    else -> "自动保存，不刷版本"
                },
                style = MaterialTheme.typography.labelMedium,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(t.space2))

        ChapterEditorChapterButtonV50(
            icon = Icons.Rounded.ChevronRight,
            text = "下一章",
            iconAfter = true,
            enabled = hasNext,
            modifier = Modifier.weight(1f),
            onClick = onNext,
        )
    }
}


@Composable
private fun ChapterEditorChapterButtonV50(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    iconAfter: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)

    Row(
        modifier = modifier
            .height(44.dp)
            .background(
                color = if (enabled) t.card else t.input,
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
            .padding(horizontal = t.space2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!iconAfter) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (enabled) {
                    t.secondaryForeground
                } else {
                    t.mutedForeground
                },
            )

            Spacer(Modifier.width(t.space1))
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                t.secondaryForeground
            } else {
                t.mutedForeground
            },
            fontWeight = FontWeight.Medium,
        )

        if (iconAfter) {
            Spacer(Modifier.width(t.space1))

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = if (enabled) {
                    t.secondaryForeground
                } else {
                    t.mutedForeground
                },
            )
        }
    }
}


@Composable
private fun ChapterEditorHeaderActionV50(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(t.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = t.primary,
        )

        Spacer(Modifier.width(t.space1))

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = t.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}


@Composable
private fun ChapterEditorIconV50(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(t.card, shape)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = t.secondaryForeground,
        )
    }
}
