package com.xiguli.langhuan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiguli.langhuan.domain.CandidateFactStatus
import com.xiguli.langhuan.engine.NovelCapability
import com.xiguli.langhuan.engine.NovelWorkflowArtifact
import com.xiguli.langhuan.engine.NovelWorkflowHistoryEntry
import com.xiguli.langhuan.engine.NovelWorkflowState
import com.xiguli.langhuan.engine.NovelWorkflowStatus
import com.xiguli.langhuan.ui.theme.LocalMiuixTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.squircle.squircleClip

/**
 * Compact entry shown in the normal authoring controller. The full process detail stays hidden
 * until the user explicitly opens it, so Skill OS remains inspectable without turning chat into a
 * developer console.
 */
@Composable
internal fun ProjectWorkflowTracePillV7(
    workflow: NovelWorkflowState?,
    flow: WritingFlowUiState,
    onClick: () -> Unit,
) {
    val runtime = runtimeTraceV7(flow)
    val stage = workflow?.currentStage?.label ?: "工作流"
    val status = workflow?.stageStatus?.label ?: runtime.label
    val staleCount = workflow?.staleArtifacts?.size ?: 0

    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (staleCount > 0) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = .62f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (runtime.active) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (staleCount > 0) Icons.Rounded.WarningAmber else Icons.Rounded.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (staleCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "$stage · $status${if (staleCount > 0) " · 待复核 $staleCount" else ""}",
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (staleCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProjectWorkflowTraceSheetV7(
    conversation: ProjectConversationUiState,
    flow: WritingFlowUiState,
    onDismiss: () -> Unit,
) {
    val workflow = conversation.workflow
    val runtime = runtimeTraceV7(flow)
    val capabilities = workflow?.capabilities?.enabledCapabilities.orEmpty().mapNotNull { name ->
        NovelCapability.entries.firstOrNull { it.name == name }
    }
    val activeArtifacts = workflow?.artifacts.orEmpty().filterNot { it.stale }.takeLast(10).reversed()
    val staleArtifacts = workflow?.staleArtifacts.orEmpty().takeLast(10).reversed()
    val history = workflow?.stageHistory.orEmpty().takeLast(12).reversed()
    val pendingCandidates = flow.snapshot?.candidateFacts.orEmpty().count {
        it.sourceChapter == flow.draft?.chapterNumber && it.status == CandidateFactStatus.PENDING
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountTree, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("Skill OS 执行详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "只显示真实流程证据；工作流元数据不等于 Canon",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMiuixTokens.current.textSecondary,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TraceSectionV7(title = "当前 Gate") {
                        if (workflow == null) {
                            Text("尚未载入工作流状态", color = LocalMiuixTokens.current.textSecondary)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WorkflowStatusIconV7(workflow.stageStatus)
                                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                    Text(
                                        "${workflow.currentStage.label} · ${workflow.stageStatus.label}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (workflow.pendingRequest.isNotBlank()) {
                                        Text(
                                            workflow.pendingRequest,
                                            modifier = Modifier.padding(top = 3.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LocalMiuixTokens.current.textSecondary,
                                        )
                                    }
                                }
                            }
                            workflow.nextStage?.let { next ->
                                Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("通过后", style = MaterialTheme.typography.labelSmall, color = LocalMiuixTokens.current.textSecondary)
                                    Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = LocalMiuixTokens.current.textSecondary)
                                    Text(next.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                item {
                    TraceSectionV7(title = "真实 Runtime") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (runtime.active) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(runtime.icon, null, Modifier.size(19.dp), tint = runtime.color())
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text(runtime.label, fontWeight = FontWeight.SemiBold)
                                Text(runtime.detail, style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
                            }
                        }
                        flow.runEvents.lastOrNull()?.let { event ->
                            Text(
                                "最近执行：${event.stage.label}${event.detail.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalMiuixTokens.current.textSecondary,
                            )
                        }
                        if (pendingCandidates > 0) {
                            Text(
                                "Candidate：还有 $pendingCandidates 条待确认/拒绝，处理前不能关闭本章 Canon Gate。",
                                modifier = Modifier.padding(top = 7.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (capabilities.isNotEmpty()) {
                    item {
                        TraceSectionV7(title = "本轮启用能力") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                capabilities.forEach { capability ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f)) {
                                        Text(
                                            capability.label,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                            val intent = workflow?.capabilities?.routeIntent.orEmpty()
                            if (intent.isNotBlank()) {
                                Text(
                                    "route: $intent",
                                    modifier = Modifier.padding(top = 7.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalMiuixTokens.current.textSecondary,
                                )
                            }
                        }
                    }
                }

                if (activeArtifacts.isNotEmpty()) {
                    item {
                        TraceSectionV7(title = "当前有效产物 · ${activeArtifacts.size}") {
                            activeArtifacts.forEach { artifact -> ArtifactRowV7(artifact) }
                        }
                    }
                }

                if (staleArtifacts.isNotEmpty()) {
                    item {
                        TraceSectionV7(title = "待复核 / stale · ${staleArtifacts.size}", danger = true) {
                            staleArtifacts.forEach { artifact -> ArtifactRowV7(artifact) }
                        }
                    }
                }

                if (history.isNotEmpty()) {
                    item {
                        TraceSectionV7(title = "最近运行轨迹") {
                            history.forEach { entry -> HistoryRowV7(entry) }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().squircleClip(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Text(
                            "这里显示的是流程状态、路由能力和执行证据。小说事实仍以 StorySnapshot / Candidate / Canon 管道为准；普通聊天不会因为这里显示“已执行”就自动改写正式正文或 Canon。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMiuixTokens.current.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceSectionV7(
    title: String,
    danger: Boolean = false,
    content: @Composable Column.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().squircleClip(20.dp),
        color = if (danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = .36f) else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun ArtifactRowV7(artifact: NovelWorkflowArtifact) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (artifact.stale) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
            null,
            Modifier.size(17.dp),
            tint = if (artifact.stale) MaterialTheme.colorScheme.error else LocalMiuixTokens.current.success,
        )
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                buildString {
                    artifact.chapterNumber?.let { append("第${it}章 · ") }
                    append(artifact.label)
                    if (artifact.revision > 1) append(" · v${artifact.revision}")
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (artifact.stale && artifact.staleReason.isNotBlank()) {
                Text(
                    artifact.staleReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    "${artifact.stage.label} · ${formatTraceTimeV7(artifact.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalMiuixTokens.current.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HistoryRowV7(entry: NovelWorkflowHistoryEntry) {
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        WorkflowStatusIconV7(entry.status)
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.stage.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(formatTraceTimeV7(entry.atMillis), style = MaterialTheme.typography.labelSmall, color = LocalMiuixTokens.current.textSecondary)
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = LocalMiuixTokens.current.textSecondary)
            }
        }
    }
}

@Composable
private fun WorkflowStatusIconV7(status: NovelWorkflowStatus) {
    val icon = when (status) {
        NovelWorkflowStatus.CONFIRMED -> Icons.Rounded.CheckCircle
        NovelWorkflowStatus.NEEDS_REWORK -> Icons.Rounded.Replay
        NovelWorkflowStatus.AWAITING_CONFIRMATION,
        NovelWorkflowStatus.RUNNING -> Icons.Rounded.HourglassTop
        NovelWorkflowStatus.SKIPPED,
        NovelWorkflowStatus.NOT_STARTED -> Icons.Rounded.RemoveCircleOutline
    }
    val tint = when (status) {
        NovelWorkflowStatus.CONFIRMED -> LocalMiuixTokens.current.success
        NovelWorkflowStatus.NEEDS_REWORK -> MaterialTheme.colorScheme.error
        NovelWorkflowStatus.AWAITING_CONFIRMATION,
        NovelWorkflowStatus.RUNNING -> MaterialTheme.colorScheme.primary
        NovelWorkflowStatus.SKIPPED,
        NovelWorkflowStatus.NOT_STARTED -> LocalMiuixTokens.current.textSecondary
    }
    Icon(icon, null, Modifier.size(18.dp), tint = tint)
}

private data class RuntimeTraceV7(
    val label: String,
    val detail: String,
    val active: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: RuntimeToneV7,
) {
    @Composable
    fun color(): Color = when (tone) {
        RuntimeToneV7.NORMAL -> MaterialTheme.colorScheme.primary
        RuntimeToneV7.SUCCESS -> LocalMiuixTokens.current.success
        RuntimeToneV7.WARNING -> MaterialTheme.colorScheme.error
        RuntimeToneV7.IDLE -> LocalMiuixTokens.current.textSecondary
    }
}

private enum class RuntimeToneV7 { NORMAL, SUCCESS, WARNING, IDLE }

private fun runtimeTraceV7(flow: WritingFlowUiState): RuntimeTraceV7 {
    val chapter = flow.draft?.chapterNumber?.let { "第${it}章" } ?: "当前章节"
    return when {
        flow.isPlanningScenes -> RuntimeTraceV7("场景规划中", "$chapter · AI 正在调整 working ScenePlan", true, Icons.Rounded.HourglassTop, RuntimeToneV7.NORMAL)
        flow.isGenerating -> RuntimeTraceV7("正文生成中", "$chapter · 生成结果完成前不会自动进入 Canon", true, Icons.Rounded.HourglassTop, RuntimeToneV7.NORMAL)
        flow.isReviewing -> RuntimeTraceV7("Agent 审校中", "$chapter · 正在检查一致性、记忆与 Candidate", true, Icons.Rounded.HourglassTop, RuntimeToneV7.NORMAL)
        flow.isSaving -> RuntimeTraceV7("保存 / 同步中", "$chapter · 正在执行真实持久化动作", true, Icons.Rounded.HourglassTop, RuntimeToneV7.NORMAL)
        flow.result?.canCommit == false -> RuntimeTraceV7("正文需要返工", "$chapter · 存在阻断级问题，当前稿不可正式保存", false, Icons.Rounded.ErrorOutline, RuntimeToneV7.WARNING)
        flow.result?.canCommit == true && !flow.chapterCommitted -> RuntimeTraceV7("正文待确认", "$chapter · 已通过阻断级检查，可正式保存后进入审校", false, Icons.Rounded.HourglassTop, RuntimeToneV7.NORMAL)
        flow.review != null -> RuntimeTraceV7("审校已完成", "$chapter · 检查 Candidate 后即可关闭本章 Canon Gate", false, Icons.Rounded.CheckCircle, RuntimeToneV7.SUCCESS)
        flow.chapterCommitted -> RuntimeTraceV7("正文已保存", "$chapter · 等待审校 / Candidate 处理", false, Icons.Rounded.CheckCircle, RuntimeToneV7.SUCCESS)
        flow.ready -> RuntimeTraceV7("Runtime 空闲", "$chapter · 等待下一次真实执行动作", false, Icons.Rounded.History, RuntimeToneV7.IDLE)
        else -> RuntimeTraceV7("Runtime 未载入", "正在等待章节工作台状态", false, Icons.Rounded.History, RuntimeToneV7.IDLE)
    }
}

private fun formatTraceTimeV7(millis: Long): String = runCatching {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}.getOrDefault("--:--")
