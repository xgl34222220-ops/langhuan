package com.xiguli.langhuan.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LanghuanMenuRow
import com.xiguli.langhuan.ui.design.LanghuanSeparator
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens

/**
 * A calm project hub between the shelf and the reader.
 *
 * Reading remains content-first; writing, story, intelligence and AI live here instead of being
 * crowded into reader chrome. Book identity editing is also retained here during the new-shell
 * migration so the Literary Shelf never regresses the existing editing capability.
 */
@Composable
fun LanghuanBookWorkspaceV1(
    book: ReaderBookUi,
    chapterCount: Int,
    aiReady: Boolean,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onStory: () -> Unit,
    onIntelligence: () -> Unit,
    onAgent: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val editViewModel: BookEditViewModelV5 = viewModel()
    var editing by rememberSaveable(book.id) { mutableStateOf(false) }

    if (editing) {
        BookEditPageV5(
            book = book,
            editViewModel = editViewModel,
            onClose = {
                editViewModel.clearFeedback()
                editing = false
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(t.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "返回书库",
                    onClick = onBack,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        "作品工作台",
                        style = MaterialTheme.typography.titleMedium,
                        color = t.foreground,
                    )
                    Text(
                        "阅读、创作与世界设定",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                LanghuanIconButton(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "编辑书籍",
                    onClick = { editing = true },
                )
            }
        }

        item {
            LanghuanCard(modifier = Modifier.fillMaxWidth(), depth = 2, contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    WorkspaceCoverV1(
                        book = book,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(.70f),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = t.foreground,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.padding(top = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LanghuanBadge(book.genre.ifBlank { "小说" })
                            if (chapterCount > 0) LanghuanBadge("$chapterCount 章", accent = true)
                        }
                        Text(
                            text = book.premise.ifBlank { "还没有作品简介，可以点右上角编辑完善。" },
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.mutedForeground,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                WorkspaceProgressV1(
                    current = book.currentWords,
                    target = book.targetWords,
                    chapter = book.currentChapter,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }

        item {
            Surface(
                onClick = onRead,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(t.radiusLg),
                color = t.primary,
                contentColor = t.primaryForeground,
                shadowElevation = 7.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Book, null, Modifier.size(20.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text("继续正文", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (chapterCount > 0) "从第 ${book.currentChapter.coerceAtLeast(1)} 章附近继续" else "打开正文与章节目录",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("进入", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        item { WorkspaceSectionTitleV1("创作") }
        item {
            LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Column {
                    LanghuanMenuRow(
                        icon = Icons.Rounded.Edit,
                        title = "写作",
                        subtitle = "章纲、正文生成与章节编辑",
                        onClick = onWrite,
                    )
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                    LanghuanMenuRow(
                        icon = Icons.Rounded.TheaterComedy,
                        title = "故事",
                        subtitle = "进入互动故事与角色体验",
                        onClick = onStory,
                    )
                }
            }
        }

        item { WorkspaceSectionTitleV1("作品设定") }
        item {
            LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Column {
                    LanghuanMenuRow(
                        icon = Icons.Rounded.Public,
                        title = "世界与角色",
                        subtitle = "人物、关系、时间线、伏笔与世界状态",
                        onClick = onIntelligence,
                    )
                    LanghuanSeparator(Modifier.padding(horizontal = 14.dp))
                    LanghuanMenuRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "AI 助手",
                        subtitle = if (aiReady) "已连接 · 与当前作品上下文对话" else "未配置 AI · 点击后可继续配置",
                        onClick = onAgent,
                        trailing = { LanghuanBadge(if (aiReady) "就绪" else "未配置", accent = aiReady) },
                    )
                }
            }
        }

        item {
            Text(
                "阅读器只保留阅读操作；创作、故事和 AI 都从作品工作台进入。",
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
    }
}

@Composable
private fun WorkspaceSectionTitleV1(title: String) {
    val t = LocalLanghuanUiTokens.current
    Text(
        text = title,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = t.foreground,
    )
}

@Composable
private fun WorkspaceProgressV1(
    current: Int,
    target: Int,
    chapter: Int,
    modifier: Modifier = Modifier,
) {
    val t = LocalLanghuanUiTokens.current
    val ratio = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${current.coerceAtLeast(0)} 字",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = t.foreground,
            )
            Text(
                if (target > 0) "目标 $target · 第 ${chapter.coerceAtLeast(1)} 章" else "第 ${chapter.coerceAtLeast(1)} 章",
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(t.track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(t.primary),
            )
        }
    }
}

@Composable
private fun WorkspaceCoverV1(book: ReaderBookUi, modifier: Modifier = Modifier) {
    val t = LocalLanghuanUiTokens.current
    val bitmap = remember(book.coverPath) {
        book.coverPath
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(t.muted),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                t.primary.copy(alpha = .72f),
                                t.strong.copy(alpha = .82f),
                            ),
                        ),
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    book.genre.ifBlank { "琅嬛" },
                    style = MaterialTheme.typography.labelSmall,
                    color = t.primaryForeground.copy(alpha = .72f),
                )
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = t.primaryForeground,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
