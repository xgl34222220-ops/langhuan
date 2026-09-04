package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LanghuanIconButton
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LocalLanghuanTokens
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(Icons.Rounded.Close, "关闭运行中心", onClose)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("运行中心", style = MaterialTheme.typography.headlineMedium, color = t.foreground)
                    Text("后台任务 · 断点恢复 · 保存与后处理", style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                }
                LanghuanBadge(if (live.active) "RUNNING" else "IDLE", accent = live.active)
            }

            when {
                state.isLoading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(color = t.accent)
                            Text("正在读取持久化断点……", color = t.mutedForeground)
                        }
                    }
                }

                state.items.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 24.dp) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(t.radiusMd),
                                    color = t.muted,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.TaskAlt, null, Modifier.size(24.dp), tint = LocalLanghuanTokens.current.success)
                                    }
                                }
                                Text("没有待恢复的 Run", style = MaterialTheme.typography.titleLarge, color = t.foreground)
                                Text(
                                    "已完成任务会自动清理；只有运行中、被中断、等待保存或等待后处理的章节会留在这里。",
                                    color = t.mutedForeground,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
                                Row(verticalAlignment = Alignment.Top) {
                                    LanghuanBadge("RUNTIME", accent = true)
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text("这些不是小说 Canon", style = MaterialTheme.typography.titleSmall, color = t.foreground)
                                        Text(
                                            "Run 断点只保存执行进度和已完成的模型结果；恢复会复用已完成阶段，不会因为重开 App 自动重发相同请求。",
                                            color = t.mutedForeground,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }

                        items(state.items, key = { "${it.novelId}:${it.chapterNumber}" }) { item ->
                            val isLive = live.active && live.novelId == item.novelId && live.chapterNumber == item.chapterNumber
                            RunCenterCard(
                                item = item,
                                isLive = isLive,
                                liveDetail = if (isLive) live.detail else "",
                                onOpen = { viewModel.open(item) },
                                onAbandon = { pendingAbandon = item },
                            )
                        }

                        state.error?.let { error ->
                            item {
                                LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                                    Text(error, color = t.destructive, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingAbandon?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingAbandon = null },
            title = { Text("放弃这个 Run？") },
            text = {
                Text("会删除《${item.novelTitle}》第${item.chapterNumber}章的运行断点。已经正式保存进小说的正文不会被删除。")
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
private fun RunCenterCard(
    item: RunCenterItemUi,
    isLive: Boolean,
    liveDetail: String,
    onOpen: () -> Unit,
    onAbandon: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val legacy = LocalLanghuanTokens.current
    val statusText = when {
        isLive -> "后台执行中"
        item.phase == DurableRunPhase.GENERATING -> "上次运行可能被中断"
        item.phase == DurableRunPhase.INTERRUPTED -> "已中断 · 可续跑"
        item.phase == DurableRunPhase.READY_TO_COMMIT -> "正文已完成 · 待保存"
        item.phase == DurableRunPhase.COMMITTING -> "正文已保存 · 待后处理"
        else -> "待处理"
    }
    val statusColor = when {
        isLive -> t.accent
        item.phase == DurableRunPhase.READY_TO_COMMIT -> legacy.success
        item.phase == DurableRunPhase.INTERRUPTED || item.phase == DurableRunPhase.GENERATING -> legacy.warning
        item.phase == DurableRunPhase.COMMITTING -> t.foreground
        else -> t.mutedForeground
    }
    val actionLabel = when {
        isLive -> "查看运行"
        item.phase == DurableRunPhase.READY_TO_COMMIT -> "查看并保存"
        item.phase == DurableRunPhase.COMMITTING -> "继续后处理"
        else -> "继续处理"
    }
    val actionIcon = if (isLive || item.phase == DurableRunPhase.READY_TO_COMMIT) Icons.Rounded.OpenInNew else Icons.Rounded.PlayArrow
    val latest = item.events.lastOrNull()

    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    color = statusColor.copy(alpha = .12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isLive) Icons.Rounded.TaskAlt else Icons.Rounded.History,
                            null,
                            Modifier.size(20.dp),
                            tint = statusColor,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        item.novelTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = t.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "第${item.chapterNumber}章 · ${item.chapterTitle}",
                        color = t.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RunStatusPill(statusText, statusColor)
            }

            Text(
                liveDetail.ifBlank { item.note.ifBlank { "Run ${item.runId.take(8)} · 已完成 ${item.completedCount} 个阶段" } },
                style = MaterialTheme.typography.bodySmall,
                color = t.mutedForeground,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = t.foreground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.preview.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(t.radiusSm),
                    color = t.muted,
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Text(
                        item.preview.replace(Regex("\\s+"), " ").trim(),
                        modifier = Modifier.padding(11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                "${ageText(item.updatedAt)} · Run ${item.runId.take(8)} · 当前阶段 ${item.currentStage}",
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAbandon,
                    enabled = !isLive,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    border = BorderStroke(1.dp, t.border),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (isLive) "先停止任务" else "放弃断点")
                }
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = t.foreground,
                        contentColor = t.primaryForeground,
                    ),
                ) {
                    Icon(actionIcon, null, Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(actionLabel, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RunStatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = .10f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .20f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        seconds < 3_600 -> "${seconds / 60} 分钟前更新"
        seconds < 86_400 -> "${seconds / 3_600} 小时前更新"
        else -> "${seconds / 86_400} 天前更新"
    }
}
