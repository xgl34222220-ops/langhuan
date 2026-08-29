package com.xiguli.langhuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.engine.RunEvent
import com.xiguli.langhuan.engine.RunStage
import com.xiguli.langhuan.engine.RunStatus
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.squircle.squircleClip

@Composable
internal fun RunInspectorPanel(
    events: List<RunEvent>,
    title: String = "Run Inspector",
) {
    if (events.isEmpty()) return
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

    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(1.dp, shape)
            .squircleClip(24.dp)
            .background(LocalMiuixTokens.current.cardBackground.copy(alpha = .95f))
            .border(.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .28f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HourglassTop, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    current?.let { "当前：${it.stage.label}" } ?: "本轮阶段已记录",
                    color = LocalMiuixTokens.current.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("$finished/${stages.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        stages.forEach { stage ->
            val event = latest.getValue(stage)
            val elapsed = elapsedMillis(events, stage, now)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                RunStatusIcon(event.status)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stage.label, fontWeight = if (event.status == RunStatus.RUNNING) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (elapsed >= 0L) {
                            Text(formatElapsed(elapsed), color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (event.detail.isNotBlank()) {
                        Text(event.detail, color = LocalMiuixTokens.current.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatusIcon(status: RunStatus) {
    when (status) {
        RunStatus.RUNNING -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        RunStatus.SUCCESS -> Icon(Icons.Rounded.CheckCircle, null, tint = LocalMiuixTokens.current.success, modifier = Modifier.size(19.dp))
        RunStatus.WARNING -> Icon(Icons.Rounded.WarningAmber, null, tint = LocalMiuixTokens.current.warning, modifier = Modifier.size(19.dp))
        RunStatus.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(19.dp))
        RunStatus.SKIPPED -> Icon(Icons.Rounded.RemoveCircleOutline, null, tint = LocalMiuixTokens.current.textSecondary, modifier = Modifier.size(19.dp))
    }
}

private fun elapsedMillis(events: List<RunEvent>, stage: RunStage, now: Long): Long {
    val start = events.firstOrNull { it.stage == stage && it.status == RunStatus.RUNNING }?.atMillis ?: return -1L
    val end = events.lastOrNull { it.stage == stage && it.status != RunStatus.RUNNING }?.atMillis ?: now
    return (end - start).coerceAtLeast(0L)
}

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1_000.0
    return if (seconds < 10) String.format("%.1fs", seconds) else "${seconds.toInt()}s"
}
