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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens


@Composable
internal fun CreationScreenV50(
    stories: List<StoryShelfUi>,
    blueprintStatusByStoryId: Map<String, String>,
    activeSkillCount: Int,
    runningTaskCount: Int,
    modifier: Modifier = Modifier,
    onStartAiCreation: () -> Unit,
    onContinueStory: (StoryShelfUi) -> Unit,
    onBlankBook: () -> Unit,
    onImportWork: () -> Unit,
    onSkills: () -> Unit,
    onRunCenter: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = t.space4,
            end = t.space4,
            top = t.space4,
            bottom = t.space6,
        ),
        verticalArrangement = Arrangement.spacedBy(t.space5),
    ) {
        item("creation-header") {
            Column {
                Text(
                    text = "创作",
                    style = MaterialTheme.typography.headlineLarge,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(t.space1))

                Text(
                    text = "让脑海里的故事，有一个开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
            }
        }

        item("creation-hero") {
            CreationHeroV50(
                onClick = onStartAiCreation,
            )
        }

        item("creation-writing-title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "在写的作品",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = t.foreground,
                    fontWeight = FontWeight.SemiBold,
                )

                if (stories.isNotEmpty()) {
                    Text(
                        text = "${stories.size} 本",
                        style = MaterialTheme.typography.labelMedium,
                        color = t.mutedForeground,
                    )
                }
            }
        }

        if (stories.isEmpty()) {
            item("creation-empty") {
                CreationEmptyWorksV50(
                    onStart = onStartAiCreation,
                )
            }
        } else {
            items(
                items = stories,
                key = { it.id },
            ) { story ->
                CreationStoryCardV50(
                    story = story,
                    blueprintStatus = blueprintStatusByStoryId[story.id].orEmpty(),
                    onClick = {
                        onContinueStory(story)
                    },
                )
            }
        }

        item("creation-tools-title") {
            Text(
                text = "工具",
                style = MaterialTheme.typography.titleLarge,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item("creation-tools") {
            Column(
                verticalArrangement = Arrangement.spacedBy(t.space2),
            ) {
                CreationToolRowV50(
                    icon = Icons.Rounded.Add,
                    title = "空白新书",
                    subtitle = "不用 AI，从第 1 章直接写起",
                    onClick = onBlankBook,
                )

                CreationToolRowV50(
                    icon = Icons.Rounded.FileOpen,
                    title = "导入已有作品",
                    subtitle = "接着别处写过的稿子继续",
                    onClick = onImportWork,
                )

                CreationToolRowV50(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "创作技能",
                    subtitle = "管理写作时使用的能力",
                    value = "${activeSkillCount.coerceAtLeast(0)} 项生效",
                    onClick = onSkills,
                )

                CreationToolRowV50(
                    icon = Icons.Rounded.TaskAlt,
                    title = "运行中心",
                    subtitle = "查看生成进度与任务记录",
                    value = if (runningTaskCount > 0) {
                        "$runningTaskCount 个执行中"
                    } else {
                        "无执行中"
                    },
                    gold = runningTaskCount > 0,
                    onClick = onRunCenter,
                )
            }
        }
    }
}


@Composable
private fun CreationHeroV50(
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusXl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.accent, shape)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(t.space5),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = t.card,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = t.primary,
            )
        }

        Spacer(Modifier.height(t.space4))

        Text(
            text = "和 AI 一起写一本书",
            style = MaterialTheme.typography.headlineSmall,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(t.space2))

        Text(
            text = "聊设定、人物与情节，逐步整理成可以一直写下去的小说蓝图。不用填表。",
            style = MaterialTheme.typography.bodyLarge,
            color = t.secondaryForeground,
        )

        Spacer(Modifier.height(t.space5))

        val buttonShape = RoundedCornerShape(t.radiusMd)

        Row(
            modifier = Modifier
                .height(48.dp)
                .background(
                    color = t.primary,
                    shape = buttonShape,
                )
                .border(
                    width = 1.dp,
                    color = t.primary,
                    shape = buttonShape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = t.space4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "开始创作",
                style = MaterialTheme.typography.labelLarge,
                color = t.card,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.width(t.space2))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = t.card,
            )
        }
    }
}


@Composable
private fun CreationStoryCardV50(
    story: StoryShelfUi,
    blueprintStatus: String,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .padding(t.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    color = t.goldContainer,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.EditNote,
                contentDescription = null,
                modifier = Modifier.size(25.dp),
                tint = t.goldForeground,
            )
        }

        Spacer(Modifier.width(t.space3))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = story.title.ifBlank { "未命名作品" },
                style = MaterialTheme.typography.titleMedium,
                color = t.foreground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(t.space1))

            Text(
                text = buildString {
                    if (story.genre.isNotBlank()) {
                        append(story.genre)
                        append(" · ")
                    }
                    append("写到第${story.currentChapter.coerceAtLeast(1)}章")
                    if (blueprintStatus.isNotBlank()) {
                        append(" · ")
                        append(blueprintStatus)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (story.targetWords > 0) {
                Spacer(Modifier.height(t.space2))

                Text(
                    text = "${story.currentWords.coerceAtLeast(0)} / ${story.targetWords} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.secondaryForeground,
                )
            }
        }

        Spacer(Modifier.width(t.space3))

        val buttonShape = RoundedCornerShape(t.radiusMd)

        Box(
            modifier = Modifier
                .height(40.dp)
                .background(t.accent, buttonShape)
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = buttonShape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = t.space3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "继续写",
                style = MaterialTheme.typography.labelMedium,
                color = t.accentForeground,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


@Composable
private fun CreationToolRowV50(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String = "",
    gold: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val shape = RoundedCornerShape(t.radiusLg)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.card, shape)
            .border(
                width = 1.dp,
                color = t.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(t.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (gold) t.goldContainer else t.input,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (gold) t.goldForeground else t.secondaryForeground,
            )
        }

        Spacer(Modifier.width(t.space3))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = t.foreground,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(t.space1))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (value.isNotBlank()) {
            Spacer(Modifier.width(t.space2))

            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = if (gold) t.goldForeground else t.secondaryForeground,
                fontWeight = if (gold) FontWeight.SemiBold else FontWeight.Normal,
            )
        }

        Spacer(Modifier.width(t.space2))

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = t.mutedForeground,
        )
    }
}


@Composable
private fun CreationEmptyWorksV50(
    onStart: () -> Unit,
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
            .padding(t.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.EditNote,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = t.primary,
        )

        Spacer(Modifier.height(t.space3))

        Text(
            text = "还没有正在写的作品",
            style = MaterialTheme.typography.titleMedium,
            color = t.foreground,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(t.space2))

        Text(
            text = "从一个想法开始，慢慢把它聊成一本书。",
            style = MaterialTheme.typography.bodySmall,
            color = t.mutedForeground,
        )

        Spacer(Modifier.height(t.space4))

        Text(
            text = "开始创作",
            modifier = Modifier
                .background(
                    color = t.accent,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .border(
                    width = 1.dp,
                    color = t.border,
                    shape = RoundedCornerShape(t.radiusMd),
                )
                .clickable(onClick = onStart)
                .padding(
                    horizontal = t.space4,
                    vertical = t.space2,
                ),
            style = MaterialTheme.typography.labelLarge,
            color = t.accentForeground,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
