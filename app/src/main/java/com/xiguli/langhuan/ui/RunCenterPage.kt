package com.xiguli.langhuan.ui

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
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
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanghuanIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "关闭运行中心",
                    onClick = onClose,
                )
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(
                        "运行中心",
                        style = MaterialTheme.typography.headlineSmall,
                        color = t.foreground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (live.active) "有任务正在后台执行" else "只保留未完成或需要处理的任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
                    )
                }
                LanghuanBadge(
                    text = if (live.active) "后台运行" else "任务队列",
                    accent = live.active,
                )
            }

            when {
                state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = t.primary)
                }

                state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 24.dp, depth = 1) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Surface(Modifier.size(56.dp), shape = RoundedCornerShape(t.radiusMd), color = t.accent) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.TaskAlt, null, Modifier.size(26.dp), tint = t.accentForeground)
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
                                Modifier.padding(top = 5.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                            )
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "运行断点只保存执行进度，不会改写小说 Canon。",
                            Modifier.padding(horizontal = 4.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
                        )
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
                            LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, depth = 0) {
                                Text(error, color = t.destructive, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    pendingAbandon?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { pendingAbandon = null },
            containerColor = t.background,
            shape = RoundedCornerShape(topStart = t.radiusXl, topEnd = t.radiusXl),
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("放弃这个任务？", style = MaterialTheme.typography.headlineSmall, color = t.foreground, fontWeight = FontWeight.SemiBold)
                Text(
                    "会删除《${item.novelTitle}》第${item.chapterNumber}章的运行断点。已经保存到小说的正文不会删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.mutedForeground,
                )
                Button(
                    onClick = {
                        viewModel.abandon(item)
                        pendingAbandon = null
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("删除运行断点")
                }
                Surface(
                    onClick = { pendingAbandon = null },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(t.radiusMd),
                    color = t.muted,
                    contentColor = t.foreground,
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("取消", fontWeight = FontWeight.Medium) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
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
    val status = when {
        isLive -> RunStatusUi("执行中", t.primary)
        item.phase == DurableRunPhase.GENERATING -> RunStatusUi("可能中断", t.warning)
        item.phase == DurableRunPhase.INTERRUPTED -> RunStatusUi("可续跑", t.warning)
        item.phase == DurableRunPhase.READY_TO_COMMIT -> RunStatusUi("待保存", t.success)
        item.phase == DurableRunPhase.COMMITTING -> RunStatusUi("待后处理", t.accentForeground)
        else -> RunStatusUi("待处理", t.mutedForeground)
    }
    val actionLabel = when {
        isLive -> "查看"
        item.phase == DurableRunPhase.READY_TO_COMMIT -> "查看并保存"
        item.phase == DurableRunPhase.COMMITTING -> "继续后处理"
        else -> "继续"
    }
    val actionIcon = if (isLive || item.phase == DurableRunPhase.READY_TO_COMMIT) Icons.Rounded.OpenInNew else Icons.Rounded.PlayArrow
    val latest = item.events.lastOrNull()

    LanghuanCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp, depth = 1) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(Modifier.size(42.dp), shape = RoundedCornerShape(t.radiusSm), color = status.color.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (isLive) Icons.Rounded.TaskAlt else Icons.Rounded.History, null, Modifier.size(20.dp), tint = status.color)
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
                    RunStatusPill(status.text, status.color)
                }
                Text(
                    "第${item.chapterNumber}章 · ${item.chapterTitle}",
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    liveDetail.ifBlank { item.note.ifBlank { "已完成 ${item.completedCount} 个阶段" } },
                    Modifier.padding(top = 9.dp),
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
                        color = t.foreground.copy(alpha = .72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${ageText(item.updatedAt)} · ${item.runId.take(8)}",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground.copy(alpha = .8f),
                )

                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactAction(
                        icon = Icons.Rounded.DeleteOutline,
                        label = if (isLive) "运行中" else "放弃",
                        enabled = !isLive,
                        destructive = true,
                        modifier = Modifier.weight(1f),
                        onClick = onAbandon,
                    )
                    CompactAction(
                        icon = actionIcon,
                        label = actionLabel,
                        enabled = true,
                        modifier = Modifier.weight(1.2f),
                        onClick = onOpen,
                    )
                }
            }
        }
    }
}

private data class RunStatusUi(val text: String, val color: Color)

@Composable
private fun CompactAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val background = when {
        !enabled -> t.muted.copy(alpha = .65f)
        destructive -> t.muted
        else -> t.accent
    }
    val foreground = when {
        !enabled -> t.mutedForeground
        destructive -> t.destructive
        else -> t.accentForeground
    }
    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(t.radiusSm),
        color = background,
        contentColor = foreground,
    ) {
        Row(
            Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1)
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
