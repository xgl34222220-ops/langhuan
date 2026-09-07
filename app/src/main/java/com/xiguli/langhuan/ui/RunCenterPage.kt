package com.xiguli.langhuan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
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
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(onClick = onClose, modifier = Modifier.size(42.dp), shape = CircleShape, color = Color.Transparent) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "关闭运行中心", Modifier.size(21.dp), tint = t.foreground) }
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("运行中心", style = MaterialTheme.typography.titleLarge, color = t.foreground, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (live.active) "有任务正在后台执行" else "只保留未完成或需要处理的任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (live.active) t.accentForeground else t.mutedForeground,
                    )
                }
            }

            when {
                state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.accentForeground)
                }

                state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 40.dp, vertical = 80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(Modifier.size(54.dp), shape = CircleShape, color = t.accent) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.TaskAlt, null, Modifier.size(25.dp), tint = t.accentForeground)
                            }
                        }
                        Text("暂无运行任务", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium, color = t.foreground, fontWeight = FontWeight.Medium)
                        Text(
                            "中断、待保存或待后处理的章节会出现在这里。",
                            Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 26.dp),
                ) {
                    item {
                        Text(
                            "运行断点只保存执行进度，不会改写小说 Canon。",
                            Modifier.padding(start = 4.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
                    }
                    itemsIndexed(state.items, key = { _, item -> "${item.novelId}:${item.chapterNumber}" }) { index, item ->
                        val isLive = live.active && live.novelId == item.novelId && live.chapterNumber == item.chapterNumber
                        RunCenterRow(
                            item = item,
                            isLive = isLive,
                            liveDetail = if (isLive) live.detail else "",
                            onOpen = { viewModel.open(item) },
                            onAbandon = { pendingAbandon = item },
                        )
                        if (index != state.items.lastIndex) HorizontalDivider(color = t.border.copy(alpha = .45f), modifier = Modifier.padding(start = 52.dp))
                    }
                    state.error?.let { error ->
                        item { Text(error, Modifier.padding(top = 12.dp), color = t.destructive, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    pendingAbandon?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingAbandon = null },
            title = { Text("放弃这个任务？") },
            text = { Text("会删除《${item.novelTitle}》第${item.chapterNumber}章的运行断点。已经保存到小说的正文不会删除。") },
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
    val legacy = LocalMiuixTokens.current
    val statusText = when {
        isLive -> "执行中"
        item.phase == DurableRunPhase.GENERATING -> "可能中断"
        item.phase == DurableRunPhase.INTERRUPTED -> "可续跑"
        item.phase == DurableRunPhase.READY_TO_COMMIT -> "待保存"
        item.phase == DurableRunPhase.COMMITTING -> "待后处理"
        else -> "待处理"
    }
    val statusColor = when {
        isLive -> t.accentForeground
        item.phase == DurableRunPhase.READY_TO_COMMIT -> legacy.success
        item.phase == DurableRunPhase.INTERRUPTED || item.phase == DurableRunPhase.GENERATING -> legacy.warning
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

    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = statusColor.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (isLive) Icons.Rounded.TaskAlt else Icons.Rounded.History, null, Modifier.size(19.dp), tint = statusColor)
                }
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.novelTitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = t.foreground, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RunStatusPill(statusText, statusColor)
                }
                Text("第${item.chapterNumber}章 · ${item.chapterTitle}", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    liveDetail.ifBlank { item.note.ifBlank { "已完成 ${item.completedCount} 个阶段" } },
                    Modifier.padding(top = 7.dp),
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
                    Text("$prefix · ${event.stage.label} · ${event.detail}", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = t.foreground.copy(alpha = .72f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text("${ageText(item.updatedAt)} · ${item.runId.take(8)}", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground.copy(alpha = .78f))

                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAbandon, enabled = !isLive) {
                        Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(16.dp))
                        Text(if (isLive) "运行中" else "放弃", Modifier.padding(start = 4.dp), color = if (isLive) t.mutedForeground else t.destructive)
                    }
                    TextButton(onClick = onOpen) {
                        Icon(actionIcon, null, Modifier.size(16.dp))
                        Text(actionLabel, Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .10f), contentColor = color, shape = RoundedCornerShape(999.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
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
