package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.engine.ChapterRunKeepAliveRegistry
import com.xiguli.langhuan.engine.DurableRunPhase
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlinx.coroutines.delay

@Composable
fun RunCenterPage(
    viewModel: RunCenterViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live by ChapterRunKeepAliveRegistry.state.collectAsState()
    val t = LocalLanghuanUiTokens.current
    var pendingAbandon by remember { mutableStateOf<RunCenterItemUi?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        while (true) {
            delay(2_000)
            viewModel.refresh(silent = true)
        }
    }

    Surface(Modifier.fillMaxSize(), color = t.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanghuanIconButton(Icons.Rounded.Close, "关闭运行中心", onClose)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("运行中心", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                        Text(
                            if (live.active) "有任务正在后台执行" else "只保留未完成或需要处理的任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (live.active) t.primary else t.mutedForeground,
                        )
                    }
                    if (live.active) RunStatusPill("执行中", t.primary)
                }

                when {
                    state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.primary)
                    }

                    state.items.isEmpty() -> Box(
                        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 30.dp, vertical = 72.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(t.radiusLg),
                            color = t.card,
                            shadowElevation = 5.dp,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Surface(Modifier.size(52.dp), shape = CircleShape, color = t.warmSurface) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.TaskAlt, null, Modifier.size(24.dp), tint = t.primary)
                                    }
                                }
                                Text(
                                    "暂无运行任务",
                                    Modifier.padding(top = 16.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = t.foreground,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "中断、待保存或待后处理的章节会出现在这里。",
                                    Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = t.mutedForeground,
                                )
                            }
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "运行断点只保存执行进度，不会改写小说 Canon。",
                                Modifier.padding(start = 4.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                            )
                        }
                        items(state.items, key = { item -> "${item.novelId}:${item.chapterNumber}" }) { item ->
                            val isLive = live.active && live.novelId == item.novelId && live.chapterNumber == item.chapterNumber
                            RunCenterRow(
                                item = item,
                                isLive = isLive,
                                liveDetail = if (isLive) live.detail else "",
                                onOpen = { viewModel.open(item) },
                                onAbandon = { pendingAbandon = item },
                            )
                        }
                        state.error?.let { error ->
                            item { Text(error, Modifier.padding(top = 4.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }

    pendingAbandon?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingAbandon = null },
            shape = RoundedCornerShape(t.radiusXl),
            containerColor = t.card,
            title = { Text("放弃这个任务？", color = t.foreground) },
            text = {
                Text(
                    "会删除《${item.novelTitle}》第${item.chapterNumber}章的运行断点。已经保存到小说的正文不会删除。",
                    color = t.mutedForeground,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.abandon(item)
                    pendingAbandon = null
                }) { Text("删除断点", color = t.destructive) }
            },
            dismissButton = { TextButton(onClick = { pendingAbandon = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun RunCenterRow(
    item: RunCenterItemUi,
    isLive: Boolean,
    liveDetail: String,
    onOpen: () -> Unit,
    onAbandon: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val statusText = when {
        isLive -> "执行中"
        item.phase == DurableRunPhase.GENERATING -> "可能中断"
        item.phase == DurableRunPhase.INTERRUPTED -> "可续跑"
        item.phase == DurableRunPhase.READY_TO_COMMIT -> "待保存"
        item.phase == DurableRunPhase.COMMITTING -> "待后处理"
        else -> "待处理"
    }
    val statusColor = when {
        isLive -> t.primary
        item.phase == DurableRunPhase.READY_TO_COMMIT -> t.success
        item.phase == DurableRunPhase.INTERRUPTED || item.phase == DurableRunPhase.GENERATING -> t.warning
        else -> t.mutedForeground
    }
    val actionLabel = when {
        isLive -> "查看"
        item.phase == DurableRunPhase.READY_TO_COMMIT -> "查看并保存"
        item.phase == DurableRunPhase.COMMITTING -> "继续后处理"
        else -> "继续"
    }
    val actionIcon = if (isLive || item.phase == DurableRunPhase.READY_TO_COMMIT) Icons.Rounded.OpenInNew else Icons.Rounded.PlayArrow
    val latest = item.events.lastOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(t.radiusLg),
        color = if (isLive) t.warmSurface else t.card,
        shadowElevation = if (isLive) 7.dp else 3.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(t.radiusSm), color = statusColor.copy(alpha = .11f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (isLive) Icons.Rounded.TaskAlt else Icons.Rounded.History, null, Modifier.size(19.dp), tint = statusColor)
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.novelTitle,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = t.foreground,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RunStatusPill(statusText, statusColor)
                    }
                    Text(
                        "第${item.chapterNumber}章 · ${item.chapterTitle}",
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                liveDetail.ifBlank { item.note.ifBlank { "已完成 ${item.completedCount} 个阶段" } },
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            latest?.let { event ->
                val prefix = when (event.status) {
                    RunStatus.RUNNING -> "进行中"
                    RunStatus.SUCCESS -> "完成"
                    RunStatus.SKIPPED -> "跳过"
                    RunStatus.WARNING -> "注意"
                    RunStatus.FAILED -> "失败"
                }
                Text(
                    "$prefix · ${event.stage.label} · ${event.detail}",
                    Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.strong,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${ageText(item.updatedAt)} · ${item.runId.take(8)}",
                Modifier.padding(top = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAbandon, enabled = !isLive) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp))
                    Text(
                        if (isLive) "运行中" else "放弃",
                        Modifier.padding(start = 4.dp),
                        color = if (isLive) t.mutedForeground else t.destructive,
                    )
                }
                TextButton(onClick = onOpen) {
                    Icon(actionIcon, null, Modifier.size(16.dp), tint = t.primary)
                    Text(actionLabel, Modifier.padding(start = 4.dp), color = t.primary)
                }
            }
        }
    }
}

@Composable
private fun RunStatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .10f), contentColor = color, shape = RoundedCornerShape(999.dp)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun ageText(time: Long): String {
    val seconds = ((System.currentTimeMillis() - time).coerceAtLeast(0L) / 1_000L)
    return when {
        seconds < 60 -> "刚刚更新"
        seconds < 3_600 -> "${seconds / 60} 分钟前"
        seconds < 86_400 -> "${seconds / 3_600} 小时前"
        else -> "${seconds / 86_400} 天前"
    }
}
