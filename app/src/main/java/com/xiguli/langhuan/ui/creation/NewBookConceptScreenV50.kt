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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.data.NewStoryRequest
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


@Composable
internal fun NewBookConceptScreenV50(
    state: NewBookConceptUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onGenerateBlueprint: () -> Unit,
    onReorganize: () -> Unit,
    onUpload: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        NewBookConceptHeaderV50(
            onBack = onBack,
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
            item("concept-progress") {
                Text(
                    text = "建书进度：${state.progressLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = t.mutedForeground,
                )
            }

            items(
                items = state.messages,
                key = { it.id },
            ) { message ->
                NewBookChatBubbleV50(
                    text = message.text,
                    user = message.user,
                )
            }

            if (state.proposal != null) {
                item("concept-proposal") {
                    NewBookProposalCardV50(
                        proposal = state.proposal,
                        protagonist = state.protagonist,
                        coreMystery = state.coreMystery,
                    )
                }

                item("concept-actions") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(t.space2),
                    ) {
                        NewBookMainActionV50(
                            icon = Icons.Rounded.AutoAwesome,
                            text = "生成蓝图",
                            enabled = state.canGenerate,
                            primary = true,
                            onClick = onGenerateBlueprint,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(t.space2),
                        ) {
                            NewBookMainActionV50(
                                icon = Icons.Rounded.Refresh,
                                text = "重新整理方案",
                                enabled = state.canReorganize,
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = onReorganize,
                            )

                            NewBookMainActionV50(
                                icon = Icons.Rounded.UploadFile,
                                text = "上传文件",
                                enabled = true,
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = onUpload,
                            )
                        }
                    }
                }
            }
        }

        NewBookConceptInputV50(
            value = state.input,
            enabled = state.inputEnabled,
            onValueChange = onInputChange,
            onSend = onSend,
        )
    }
}


@Composable
private fun NewBookConceptHeaderV50(
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = t.space2,
                end = t.space2,
                top = t.space2,
                bottom = t.space1,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewBookIconButtonV50(
            icon = Icons.Rounded.ArrowBack,
            description = "返回",
            onClick = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = t.space2),
        ) {
            Text(
                text = "新书构思",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "像聊天一样把一本书聊清楚",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }

        NewBookIconButtonV50(
            icon = Icons.Rounded.MoreVert,
            description = "更多",
            onClick = onMore,
        )
    }
}


@Composable
private fun NewBookChatBubbleV50(
    text: String,
    user: Boolean,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (user) t.primary else t.card,
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = shape,
                )
                .padding(
                    horizontal = t.space3,
                    vertical = t.space2,
                ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (user) t.card else t.foreground,
            )
        }
    }
}


@Composable
private fun NewBookProposalCardV50(
    proposal: NewStoryRequest,
    protagonist: String,
    coreMystery: String,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusXl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(t.space4),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "方案",
                modifier = Modifier
                    .background(
                        color = t.goldContainer,
                        shape = RoundedCornerShape(t.radiusSm),
                    )
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = RoundedCornerShape(t.radiusSm),
                    )
                    .padding(
                        horizontal = t.space2,
                        vertical = t.space1,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = t.goldForeground,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.width(t.space2))

            Text(
                text = "蓝图",
                modifier = Modifier
                    .background(
                        color = t.accent,
                        shape = RoundedCornerShape(t.radiusSm),
                    )
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = RoundedCornerShape(t.radiusSm),
                    )
                    .padding(
                        horizontal = t.space2,
                        vertical = t.space1,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = t.accentForeground,
            )

            Spacer(Modifier.width(t.space2))

            Text(
                text = "建书",
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
        }

        Spacer(Modifier.height(t.space4))

        NewBookProposalRowV50(
            label = "书名",
            value = proposal.title.ifBlank { "暂定" },
        )

        NewBookProposalDividerV50()

        NewBookProposalRowV50(
            label = "题材",
            value = proposal.genre.ifBlank { "待整理" },
        )

        NewBookProposalDividerV50()

        NewBookProposalRowV50(
            label = "主角",
            value = protagonist.ifBlank { "待确认" },
        )

        NewBookProposalDividerV50()

        NewBookProposalRowV50(
            label = "核心悬念",
            value = coreMystery.ifBlank {
                proposal.premise.ifBlank { "待确认" }
            },
        )

        NewBookProposalDividerV50()

        NewBookProposalRowV50(
            label = "篇幅",
            value = if (proposal.targetWords > 0) {
                "${proposal.targetWords} 字"
            } else {
                "待确认"
            },
        )
    }
}


@Composable
private fun NewBookProposalRowV50(
    label: String,
    value: String,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = t.space2),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
            fontWeight = FontWeight.Medium,
        )
    }
}


@Composable
private fun NewBookProposalDividerV50() {
    val t = LocalLanghuanUiTokens.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(t.border),
    )
}


@Composable
private fun NewBookMainActionV50(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                color = when {
                    !enabled -> t.input
                    primary -> t.primary
                    else -> t.card
                },
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = when {
                    !enabled -> t.border
                    primary -> t.primary
                    else -> t.border
                },
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = t.space3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = when {
                !enabled -> t.mutedForeground
                primary -> t.card
                else -> t.primary
            },
        )

        Spacer(Modifier.width(t.space2))

        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> t.mutedForeground
                primary -> t.card
                else -> t.secondaryForeground
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}


@Composable
private fun NewBookConceptInputV50(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
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
        verticalAlignment = Alignment.Bottom,
    ) {
        val inputShape = RoundedCornerShape(t.radiusLg)

        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .background(t.input, inputShape)
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = inputShape,
                )
                .padding(horizontal = t.space3),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isBlank()) {
                Text(
                    text = "输入想法",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = t.foreground,
                ),
                cursorBrush = SolidColor(t.primary),
            )
        }

        Spacer(Modifier.width(t.space2))

        val sendShape = RoundedCornerShape(t.radiusMd)

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (enabled && value.isNotBlank()) {
                        t.primary
                    } else {
                        t.input
                    },
                    shape = sendShape,
                )
                .border(
                    width = 1.dp,
                    color = if (enabled && value.isNotBlank()) {
                        t.primary
                    } else {
                        t.border
                    },
                    shape = sendShape,
                )
                .clickable(
                    enabled = enabled && value.isNotBlank(),
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Send,
                contentDescription = "发送",
                modifier = Modifier.size(19.dp),
                tint = if (enabled && value.isNotBlank()) {
                    t.card
                } else {
                    t.mutedForeground
                },
            )
        }
    }
}


@Composable
private fun NewBookIconButtonV50(
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

internal data class NewBookChatMessageV50(
    val id: String,
    val text: String,
    val user: Boolean,
)

internal data class NewBookConceptUiState(
    val progressLabel: String = "",
    val messages: List<NewBookChatMessageV50> = emptyList(),
    val proposal: NewStoryRequest? = null,
    val protagonist: String = "",
    val coreMystery: String = "",
    val canGenerate: Boolean = false,
    val canReorganize: Boolean = false,
    val input: String = "",
    val inputEnabled: Boolean = true,
)
