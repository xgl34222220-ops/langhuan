package com.xiguli.langhuan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStage
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.ui.design.LanghuanBadge
import com.xiguli.langhuan.ui.design.LanghuanCard
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import kotlinx.coroutines.delay

@Composable
internal fun RunInspectorPanel(
    events: List<RunEvent>,
    title: String = "Run Inspector",
) {
    if (events.isEmpty()) return
    if (title == "建书 Run Inspector") return

    val t = LocalLanghuanUiTokens.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = events.any { it.status == RunStatus.RUNNING }
    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val stages = events.map { it.stage }.distinct()
    val latest = stages.associateWith { stage -> events.last { it.stage == stage } }
    val current = events.lastOrNull { it.status == RunStatus.RUNNING }
    val finished = latest.values.count { it.status == RunStatus.SUCCESS || it.status == RunStatus.SKIPPED }

    LanghuanCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(t.radiusSm),
                    color = if (running) t.warmSurface else t.muted,
                    border = BorderStroke(1.dp, if (running) t.accent.copy(alpha = .15f) else t.border),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp, color = t.accent)
                        else Icon(Icons.Rounded.HourglassTop, null, Modifier.size(17.dp), tint = t.mutedForeground)
                    }
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = t.foreground)
                    Text(
                        current?.let { "当前：${it.stage.label}" } ?: "本轮阶段已记录",
                        color = t.mutedForeground,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LanghuanBadge("$finished/${stages.size}", accent = running)
            }

            stages.forEach { stage ->
                val event = latest.getValue(stage)
                val elapsed = elapsedMillis(events, stage, now)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    RunStatusIcon(event.status)
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stage.label,
                                color = t.foreground,
                                fontWeight = if (event.status == RunStatus.RUNNING) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (elapsed >= 0L) Text(formatElapsed(elapsed), color = t.mutedForeground, style = MaterialTheme.typography.labelSmall)
                        }
                        if (event.detail.isNotBlank()) Text(event.detail, color = t.mutedForeground, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatusIcon(status: RunStatus) {
    val t = LocalLanghuanUiTokens.current
    when (status) {
        RunStatus.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.8.dp, color = t.accent)
        RunStatus.SUCCESS -> Icon(Icons.Rounded.CheckCircle, null, tint = t.success, modifier = Modifier.size(19.dp))
        RunStatus.WARNING -> Icon(Icons.Rounded.WarningAmber, null, tint = t.warning, modifier = Modifier.size(19.dp))
        RunStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = t.destructive, modifier = Modifier.size(19.dp))
        RunStatus.SKIPPED -> Icon(Icons.Rounded.RemoveCircleOutline, null, tint = t.mutedForeground, modifier = Modifier.size(19.dp))
    }
}

private fun elapsedMillis(events: List<RunEvent>, stage: RunStage, now: Long): Long {
    val start = events.lastOrNull { it.stage == stage && it.status == RunStatus.RUNNING }?.atMillis ?: return -1L
    val end = events.lastOrNull { it.stage == stage && it.status != RunStatus.RUNNING && it.atMillis >= start }?.atMillis ?: now
    return (end - start).coerceAtLeast(0L)
}

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1_000.0
    return if (seconds < 10) String.format("%.1fs", seconds) else "${seconds.toInt()}s"
}
