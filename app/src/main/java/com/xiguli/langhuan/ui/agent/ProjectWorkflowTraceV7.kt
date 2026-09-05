package com.xiguli.langhuan.ui.agent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import com.xiguli.langhuan.engine.NovelIntent
import com.xiguli.langhuan.engine.NovelWorkflowArtifact
import com.xiguli.langhuan.engine.NovelWorkflowHistoryEntry
import com.xiguli.langhuan.engine.NovelWorkflowState
import com.xiguli.langhuan.engine.NovelWorkflowStatus
import com.xiguli.langhuan.engine.ProjectRuntimeReceiptState
import com.xiguli.langhuan.engine.ProjectRuntimeSkillReceipt
import com.xiguli.langhuan.ui.writing.WritingFlowUiState
import com.xiguli.langhuan.ui.creation.ProjectConversationUiState
import com.xiguli.langhuan.ui.design.LocalLanghuanUiTokens
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val t = LocalLanghuanUiTokens.current
    val runtime = runtimeTraceV7(flow)
    val stage = workflow?.currentStage?.label ?: "工作流"
    val status = workflow?.stageStatus?.label ?: runtime.label
    val staleCount = workflow?.staleArtifacts?.size ?: 0
    val tone = if (staleCount > 0) t.destructive else runtime.color()

    Surface(
        shape = LanghuanShape.pill,
        color = tone.copy(alpha = .08f),
        border = BorderStroke(1.dp, tone.copy(alpha = .18f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (runtime.active) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = tone)
            } else {
                Icon(
                    if (staleCount > 0) Icons.Rounded.WarningAmber else Icons.Rounded.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = tone,
                )
            }
            Text(
                "$stage · $status${if (staleCount > 0) " · 待复核 $staleCount" else ""}",
                modifier = Modifier.padding(start = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (staleCount > 0) t.destructive else t.foreground,
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
    val t = LocalLanghuanUiTokens.current
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

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.9f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = LanghuanShape.card,
                    color = t.warmSurface,
                    border = BorderStroke(1.dp, t.accent.copy(alpha = .18f)),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AccountTree, null, tint = t.accent)
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Skill OS 执行详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = t.foreground)
                    Text(
                        "路由是候选；只有 RunEvent / Runtime 回执才能算真实执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.mutedForeground,
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
                            Text("尚未载入工作流状态", color = t.mutedForeground)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WorkflowStatusIconV7(workflow.stageStatus)
                                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                    Text(
                                        "${workflow.currentStage.label} · ${workflow.stageStatus.label}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = t.foreground,
                                    )
                                    if (workflow.pendingRequest.isNotBlank()) {
                                        Text(
                                            workflow.pendingRequest,
                                            modifier = Modifier.padding(top = 3.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = t.mutedForeground,
                                        )
                                    }
                                }
                            }
                            workflow.nextStage?.let { next ->
                                Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("通过后", style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
                                    Icon(Icons.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = t.mutedForeground)
                                    Text(next.label, style = MaterialTheme.typography.labelMedium, color = t.accent)
                                }
                            }
                        }
                    }
                }

                item {
                    TraceSectionV7(title = "真实执行状态") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (runtime.active) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = runtime.color())
                            else Icon(runtime.icon, null, Modifier.size(19.dp), tint = runtime.color())
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text(runtime.label, fontWeight = FontWeight.SemiBold, color = t.foreground)
                                Text(runtime.detail, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
                            }
                        }
                        flow.runEvents.lastOrNull()?.let { event ->
                            Text(
                                "最近执行：${event.stage.label}${event.detail.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty()}",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                            )
                        }
                        if (pendingCandidates > 0) {
                            TraceInlineNoticeV7(
                                "Candidate：还有 $pendingCandidates 条待确认/拒绝，处理前不能关闭本章 Canon Gate。",
                                t.warning,
                            )
                        }
                    }
                }

                flow.runtimeAudit?.let { audit ->
                    item {
                        TraceSectionV7(
                            title = "真实执行回执 · 已执行 ${audit.executedCount}/${audit.receipts.size}",
                            danger = audit.failedCount > 0,
                        ) {
                            Text(
                                buildString {
                                    append("只认实际运行证据")
                                    if (audit.skippedCount > 0) append(" · 未触发/跳过 ${audit.skippedCount}")
                                    if (audit.pendingCount > 0) append(" · 无专属证据 ${audit.pendingCount}")
                                    if (audit.failedCount > 0) append(" · 失败 ${audit.failedCount}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = t.mutedForeground,
                            )
                            audit.receipts.forEach { receipt -> ExecutionReceiptRowV9(receipt) }
                        }
                    }
                }

                if (capabilities.isNotEmpty()) {
                    item {
                        TraceSectionV7(title = "路由候选能力 · 不等于已执行") {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                capabilities.forEach { capability ->
                                    Surface(
                                        shape = LanghuanShape.pill,
                                        color = t.warmSurface,
                                        border = BorderStroke(1.dp, t.accent.copy(alpha = .16f)),
                                    ) {
                                        Text(
                                            capability.label,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = t.accent,
                                        )
                                    }
                                }
                            }
                            val intent = workflow?.capabilities?.routeIntent.orEmpty()
                            if (intent.isNotBlank()) {
                                val intentLabel = NovelIntent.entries.firstOrNull { it.name == intent }?.label ?: intent
                                Text(
                                    "当前意图：$intentLabel · 路由只说明本轮允许调用这些能力，实际执行仍以回执为准。",
                                    modifier = Modifier.padding(top = 7.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = t.mutedForeground,
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = LanghuanShape.card,
                        color = t.muted,
                        border = BorderStroke(1.dp, t.border),
                    ) {
                        Text(
                            "这里显示流程状态、候选路由和真实执行证据。小说事实仍以 StorySnapshot / Candidate / Canon 管道为准；执行回执本身不会写入 Canon。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = t.mutedForeground,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalLanghuanUiTokens.current
    val color = if (danger) t.destructive else t.border
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LanghuanShape.panel,
        color = if (danger) t.destructive.copy(alpha = .06f) else t.card,
        border = BorderStroke(1.dp, if (danger) t.destructive.copy(alpha = .20f) else t.border),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (danger) t.destructive else t.foreground,
            )
            content()
        }
    }
}

@Composable
private fun TraceInlineNoticeV7(text: String, color: Color) {
    val t = LocalLanghuanUiTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
        shape = LanghuanShape.chip,
        color = color.copy(alpha = .07f),
        border = BorderStroke(1.dp, color.copy(alpha = .16f)),
    ) {
        Text(text, Modifier.padding(9.dp), style = MaterialTheme.typography.bodySmall, color = t.foreground)
    }
}

@Composable
private fun ExecutionReceiptRowV9(receipt: ProjectRuntimeSkillReceipt) {
    val t = LocalLanghuanUiTokens.current
    val icon = when (receipt.state) {
        ProjectRuntimeReceiptState.EXECUTED -> Icons.Rounded.CheckCircle
        ProjectRuntimeReceiptState.SKIPPED -> Icons.Rounded.RemoveCircleOutline
        ProjectRuntimeReceiptState.PENDING -> Icons.Rounded.HourglassTop
        ProjectRuntimeReceiptState.FAILED -> Icons.Rounded.ErrorOutline
    }
    val tint = when (receipt.state) {
        ProjectRuntimeReceiptState.EXECUTED -> t.success
        ProjectRuntimeReceiptState.SKIPPED -> t.mutedForeground
        ProjectRuntimeReceiptState.PENDING -> t.warning
        ProjectRuntimeReceiptState.FAILED -> t.destructive
    }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    receipt.step.capability.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    color = t.foreground,
                )
                Surface(shape = LanghuanShape.pill, color = tint.copy(alpha = .08f)) {
                    Text(
                        receipt.state.label + receipt.durationMs?.let { " · ${formatDurationV9(it)}" }.orEmpty(),
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                    )
                }
            }
            Text(
                "${receipt.step.phase.label} · ${receipt.step.engine}",
                style = MaterialTheme.typography.labelSmall,
                color = t.mutedForeground,
            )
            if (receipt.state == ProjectRuntimeReceiptState.PENDING) {
                Text(
                    "尚无专属执行证据；不会计为已执行。",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.mutedForeground,
                )
            }
            if (receipt.dataInputs.isNotEmpty()) {
                Text(
                    "读取：${receipt.dataInputs.joinToString(" · ")}",
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = t.foreground,
                )
            }
            if (receipt.outputSummary.isNotBlank()) {
                Text(
                    when (receipt.state) {
                        ProjectRuntimeReceiptState.SKIPPED -> "未触发原因：${receipt.outputSummary}"
                        ProjectRuntimeReceiptState.FAILED -> "失败证据：${receipt.outputSummary}"
                        else -> "产出：${receipt.outputSummary}"
                    },
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (receipt.state == ProjectRuntimeReceiptState.FAILED) t.destructive else t.mutedForeground,
                )
            }
            receipt.evidenceTrail.takeLast(2).forEach { evidence ->
                Text(
                    "证据 · ${evidence.stage.label} / ${evidence.status.name}${evidence.detail.takeIf(String::isNotBlank)?.let { " · ${it.take(120)}" }.orEmpty()}",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun ArtifactRowV7(artifact: NovelWorkflowArtifact) {
    val t = LocalLanghuanUiTokens.current
    val tint = if (artifact.stale) t.destructive else t.success
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (artifact.stale) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
            null,
            Modifier.size(17.dp),
            tint = tint,
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
                color = t.foreground,
            )
            if (artifact.stale && artifact.staleReason.isNotBlank()) {
                Text(artifact.staleReason, style = MaterialTheme.typography.bodySmall, color = t.destructive)
            } else {
                Text(
                    "${artifact.stage.label} · ${formatTraceTimeV7(artifact.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = t.mutedForeground,
                )
            }
        }
    }
}

@Composable
private fun HistoryRowV7(entry: NovelWorkflowHistoryEntry) {
    val t = LocalLanghuanUiTokens.current
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.Top) {
        WorkflowStatusIconV7(entry.status)
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.stage.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), color = t.foreground)
                Text(formatTraceTimeV7(entry.atMillis), style = MaterialTheme.typography.labelSmall, color = t.mutedForeground)
            }
            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = t.mutedForeground)
            }
        }
    }
}

@Composable
private fun WorkflowStatusIconV7(status: NovelWorkflowStatus) {
    val t = LocalLanghuanUiTokens.current
    val icon = when (status) {
        NovelWorkflowStatus.CONFIRMED -> Icons.Rounded.CheckCircle
        NovelWorkflowStatus.NEEDS_REWORK -> Icons.Rounded.Replay
        NovelWorkflowStatus.AWAITING_CONFIRMATION,
        NovelWorkflowStatus.RUNNING -> Icons.Rounded.HourglassTop
        NovelWorkflowStatus.SKIPPED,
        NovelWorkflowStatus.NOT_STARTED -> Icons.Rounded.RemoveCircleOutline
    }
    val tint = when (status) {
        NovelWorkflowStatus.CONFIRMED -> t.success
        NovelWorkflowStatus.NEEDS_REWORK -> t.destructive
        NovelWorkflowStatus.AWAITING_CONFIRMATION -> t.warning
        NovelWorkflowStatus.RUNNING -> t.accent
        NovelWorkflowStatus.SKIPPED,
        NovelWorkflowStatus.NOT_STARTED -> t.mutedForeground
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
    fun color(): Color {
        val t = LocalLanghuanUiTokens.current
        return when (tone) {
            RuntimeToneV7.NORMAL -> t.accent
            RuntimeToneV7.SUCCESS -> t.success
            RuntimeToneV7.WARNING -> t.destructive
            RuntimeToneV7.IDLE -> t.mutedForeground
        }
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
        flow.ready -> RuntimeTraceV7("执行空闲", "$chapter · 等待下一次真实执行动作", false, Icons.Rounded.History, RuntimeToneV7.IDLE)
        else -> RuntimeTraceV7("执行状态未载入", "正在等待章节工作台状态", false, Icons.Rounded.History, RuntimeToneV7.IDLE)
    }
}

private fun formatDurationV9(millis: Long): String = when {
    millis < 1_000L -> "${millis}ms"
    millis < 60_000L -> String.format(Locale.getDefault(), "%.1fs", millis / 1_000.0)
    else -> "${millis / 60_000}m ${(millis % 60_000) / 1_000}s"
}

private fun formatTraceTimeV7(millis: Long): String = runCatching {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}.getOrDefault("--:--")
