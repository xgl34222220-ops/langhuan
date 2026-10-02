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
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.ScenePlan
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


@Composable
internal fun ChapterWorkbenchScreenV50(
    state: ChapterWorkbenchUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onStoryStatus: () -> Unit,
    onRunTrail: () -> Unit,
    onAdjustScene: (ScenePlan) -> Unit,
    onGenerate: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
    ) {
        ChapterWorkbenchHeaderV50(
            bookTitle = state.bookTitle,
            chapterLabel = state.chapterLabel,
            onBack = onBack,
            onStoryStatus = onStoryStatus,
            onRunTrail = onRunTrail,
        )

        ChapterWorkbenchStepsV50(
            steps = state.steps,
            currentIndex = state.currentStepIndex,
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
            item("workbench-task") {
                ChapterCurrentTaskV50(
                    chapterNumber = state.chapterNumber,
                    title = state.taskTitle,
                    objective = state.taskObjective,
                )
            }

            item("workbench-scenes-title") {
                Text(
                    text = "场景计划",
                    style = MaterialTheme.typography.titleLarge,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            items(
                items = state.scenes,
                key = { it.order },
            ) { scene ->
                ChapterSceneCardV50(
                    scene = scene,
                    onAdjust = { onAdjustScene(scene) },
                )
            }

            if (state.generatedText.isNotBlank() || state.generating) {
                item("workbench-preview") {
                    ChapterGenerationPreviewV50(
                        text = state.generatedText,
                        running = state.generating,
                    )
                }
            }

            item("workbench-generate") {
                ChapterGenerateButtonV50(
                    running = state.generating,
                    enabled = state.canGenerate,
                    onClick = onGenerate,
                )
            }
        }

        ChapterWorkbenchInputV50(
            value = state.input,
            enabled = state.inputEnabled,
            onValueChange = onInputChange,
            onSend = onSend,
        )
    }
}


@Composable
private fun ChapterWorkbenchHeaderV50(
    bookTitle: String,
    chapterLabel: String,
    onBack: () -> Unit,
    onStoryStatus: () -> Unit,
    onRunTrail: () -> Unit,
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
        ChapterWorkbenchIconV50(
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
                text = "章节工作台",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "$bookTitle $chapterLabel",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }

        ChapterHeaderActionV50(
            text = "故事状态",
            onClick = onStoryStatus,
        )

        ChapterHeaderActionV50(
            text = "运行轨迹",
            onClick = onRunTrail,
        )
    }
}


@Composable
private fun ChapterWorkbenchStepsV50(
    steps: List<String>,
    currentIndex: Int,
) {
    val t = LocalLanghuanUiTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = t.space4,
                vertical = t.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val done = index < currentIndex
            val current = index == currentIndex

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = when {
                                done -> t.primary
                                current -> t.accent
                                else -> t.input
                            },
                            shape = RoundedCornerShape(t.radiusSm),
                        )
                        .border(
                            width = 1.dp,
                            color = t.border,
                            shape = RoundedCornerShape(t.radiusSm),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            done -> t.card
                            current -> t.accentForeground
                            else -> t.mutedForeground
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.width(t.space1))

                Text(
                    text = step,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current || done) {
                        t.foreground
                    } else {
                        t.mutedForeground
                    },
                    fontWeight = if (current) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                )
            }

            if (index < steps.lastIndex) {
                Spacer(Modifier.width(t.space2))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(t.border),
                )

                Spacer(Modifier.width(t.space2))
            }
        }
    }
}


@Composable
private fun ChapterCurrentTaskV50(
    chapterNumber: Int,
    title: String,
    objective: String,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

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
        Text(
            text = "第 $chapterNumber 章 $title",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(t.space2))

        Text(
            text = objective,
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
        )
    }
}


@Composable
private fun ChapterSceneCardV50(
    scene: ScenePlan,
    onAdjust: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

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
                text = "${scene.order}",
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
                style = MaterialTheme.typography.labelMedium,
                color = t.accentForeground,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.width(t.space2))

            Text(
                text = "${scene.viewpoint} · ${scene.location}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "AI 调整",
                modifier = Modifier
                    .background(
                        color = t.input,
                        shape = RoundedCornerShape(t.radiusSm),
                    )
                    .border(
                        width = 1.dp,
                        color = t.border,
                        shape = RoundedCornerShape(t.radiusSm),
                    )
                    .clickable(onClick = onAdjust)
                    .padding(
                        horizontal = t.space2,
                        vertical = t.space1,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = t.secondaryForeground,
            )
        }

        Spacer(Modifier.height(t.space2))

        Text(
            text = scene.purpose,
            style = MaterialTheme.typography.bodyMedium,
            color = t.secondaryForeground,
        )
    }
}


@Composable
private fun ChapterGenerationPreviewV50(
    text: String,
    running: Boolean,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

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
        Text(
            text = if (running) "正在生成…" else "生成结果",
            style = MaterialTheme.typography.labelMedium,
            color = t.mutedForeground,
        )

        Spacer(Modifier.height(t.space2))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = t.foreground,
        )
    }
}


@Composable
private fun ChapterGenerateButtonV50(
    running: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusMd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = if (enabled && !running) t.primary else t.input,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (enabled && !running) t.primary else t.border,
                shape = shape,
            )
            .clickable(
                enabled = enabled && !running,
                onClick = onClick,
            )
            .padding(horizontal = t.space4),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled && !running) t.card else t.mutedForeground,
        )

        Spacer(Modifier.width(t.space2))

        Text(
            text = if (running) "正在生成…" else "生成正文",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled && !running) t.card else t.mutedForeground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


@Composable
private fun ChapterWorkbenchInputV50(
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
                    text = "对琅嬛说",
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
private fun ChapterWorkbenchIconV50(
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


@Composable
private fun ChapterHeaderActionV50(
    text: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(t.space2),
        style = MaterialTheme.typography.labelMedium,
        color = t.primary,
        fontWeight = FontWeight.Medium,
    )
}

internal data class ChapterWorkbenchUiState(
    val bookTitle: String = "",
    val chapterLabel: String = "",
    val steps: List<String> = emptyList(),
    val currentStepIndex: Int = 0,
    val chapterNumber: Int = 0,
    val taskTitle: String = "",
    val taskObjective: String = "",
    val scenes: List<ScenePlan> = emptyList(),
    val generatedText: String = "",
    val generating: Boolean = false,
    val canGenerate: Boolean = false,
    val input: String = "",
    val inputEnabled: Boolean = true,
)
