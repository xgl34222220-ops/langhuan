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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xiguli.langhuan.engine.ReferenceDistillationJobs
import com.xiguli.langhuan.engine.ReferenceDistillationSourceStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private data class ReferenceDistillationTaskUi(
    val id: String,
    val state: WorkInfo.State,
    val progress: Int,
    val stage: String,
    val batch: Int,
    val batches: Int,
    val title: String,
    val provider: String,
    val model: String,
    val error: String,
    val chapters: Int,
    val samples: Int,
    val runAttemptCount: Int,
    val sourcePath: String,
    val sourceName: String,
    val resumable: Boolean,
    val completedBatches: Int,
) {
    val active: Boolean
        get() = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
    val sourceAvailable: Boolean
        get() = sourcePath.isNotBlank() && File(sourcePath).exists()
}

@Composable
fun AiFirstShelf(
    state: LibraryExperienceState,
    aiReady: Boolean,
    aiProviderLabel: String,
    aiModel: String,
    onOpenBook: (String) -> Unit,
    onStartCreation: () -> Unit,
    onConfigureAi: () -> Unit,
    onDistillReference: () -> Unit,
    onCloseShelf: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val sourceStore = remember(context) { ReferenceDistillationSourceStore(context) }
    val distillationTasks = rememberReferenceDistillationTasks()
    var reportTask by remember { mutableStateOf<ReferenceDistillationTaskUi?>(null) }

    reportTask?.let { task ->
        ReferenceDistillationReportDialog(
            taskId = task.id,
            title = task.title.ifBlank { "参考小说" },
            fallbackProvider = task.provider.ifBlank { aiProviderLabel },
            fallbackModel = task.model.ifBlank { aiModel },
            onDismiss = { reportTask = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛书架", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Text(
                        "聊出一本书 · 蒸馏参考 · 长期创作",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.stories.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        IconButton(onClick = onCloseShelf) { Icon(Icons.Rounded.Close, "进入工作台") }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = if (aiReady) onStartCreation else onConfigureAi,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(19.dp),
                    ) {
                        Icon(if (aiReady) Icons.Rounded.AutoAwesome else Icons.Rounded.Key, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (aiReady) "和 AI 聊出一本新小说" else "先配置 AI，再开始创作", fontWeight = FontWeight.SemiBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FilledTonalButton(
                            onClick = onConfigureAi,
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            Icon(Icons.Rounded.Key, null)
                            Spacer(Modifier.width(6.dp))
                            Text("AI / Key", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = if (aiReady) onDistillReference else onConfigureAi,
                            modifier = Modifier.weight(1f).height(50.dp),
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("导入蒸馏", maxLines = 1)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShelfPill("TXT / EPUB / MD")
                        ShelfPill("Style + Story DNA")
                        ShelfPill("断点续跑")
                    }
                    Text(
                        "整本小说先做全书结构扫描，AI 再按书长深度分层阅读；长篇不再固定只抽 30 章。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (distillationTasks.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("参考小说蒸馏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "后台任务",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(distillationTasks, key = { it.id }) { task ->
                ReferenceDistillationTaskCard(
                    task = task,
                    fallbackProvider = aiProviderLabel,
                    fallbackModel = aiModel,
                    onOpenReport = { reportTask = task },
                    onCancel = { ReferenceDistillationJobs.cancel(context, task.id) },
                    onRetry = {
                        val source = File(task.sourcePath)
                        if (source.exists()) {
                            val nextId = ReferenceDistillationJobs.retry(
                                context,
                                source,
                                task.sourceName.ifBlank { source.name },
                            )
                            sourceStore.save(nextId, source, task.sourceName.ifBlank { source.name })
                        }
                    },
                )
            }
        }

        if (state.stories.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                Icons.Rounded.AutoStories,
                                null,
                                Modifier.padding(15.dp).size(38.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("从一个想法开始", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (aiReady) {
                                "不用先填表。说一个题材、画面、主角，或选一份蒸馏模板，AI 会陪你把它聊成完整蓝图。"
                            } else {
                                "先添加 AI 服务和 API Key，然后就可以从一个题材、画面或角色直接开始。"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(state.stories, key = { it.id }) { book ->
            AiShelfBookCard(book = book, onClick = { onOpenBook(book.id) })
        }
    }
}

@Composable
private fun ShelfPill(text: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReferenceDistillationTaskCard(
    task: ReferenceDistillationTaskUi,
    fallbackProvider: String,
    fallbackModel: String,
    onOpenReport: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val progress = task.progress.coerceIn(0, 100)
    val stageLabel = when {
        task.state == WorkInfo.State.SUCCEEDED -> "双层 DNA 完成 · 已入长期研究档案"
        task.state == WorkInfo.State.FAILED && task.resumable -> "任务中断 · 已保存 ${task.completedBatches}/${task.batches} 批断点"
        task.state == WorkInfo.State.FAILED -> "蒸馏失败"
        task.state == WorkInfo.State.CANCELLED -> "任务已取消"
        task.state == WorkInfo.State.ENQUEUED && task.runAttemptCount > 0 -> "等待自动重试 · 第 ${task.runAttemptCount + 1} 次尝试"
        task.state == WorkInfo.State.ENQUEUED -> "等待后台调度 / 网络连接"
        task.state == WorkInfo.State.BLOCKED -> "等待前置条件"
        task.stage == "parse" -> "正在扫描全书结构"
        task.stage == "prepare" -> "已解析 · 准备双层 DNA"
        task.stage == "distill" && task.batches > 0 -> "AI 深度分层阅读 · ${task.batch}/${task.batches} 批"
        task.stage == "aggregate" -> "正在聚合 Style + Story DNA"
        task.stage == "done" -> "正在保存研究档案"
        else -> "AI 后台蒸馏正在运行"
    }
    val icon = when (task.state) {
        WorkInfo.State.SUCCEEDED -> Icons.Rounded.CheckCircle
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Icons.Rounded.ErrorOutline
        else -> Icons.Rounded.HourglassTop
    }
    val title = task.title.ifBlank { "参考小说" }
    val provider = task.provider.ifBlank { fallbackProvider }.ifBlank { "等待选择 AI" }
    val model = task.model.ifBlank { fallbackModel }.ifBlank { "等待模型" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = when (task.state) {
            WorkInfo.State.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
            WorkInfo.State.SUCCEEDED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stageLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$progress%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress / 100f)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            if (task.active || task.state == WorkInfo.State.SUCCEEDED || task.resumable) {
                Text(
                    "$provider · $model",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (task.active) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShelfPill("AI 真分析")
                    ShelfPill("锁定模型")
                    ShelfPill("批次断点")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Rounded.Close, null)
                    Spacer(Modifier.width(7.dp))
                    Text("取消后台蒸馏")
                }
            }

            if (task.state == WorkInfo.State.SUCCEEDED) {
                val coverage = if (task.chapters > 0) (task.samples * 100 / task.chapters).coerceAtMost(100) else 0
                Text(
                    "全书扫描 ${task.chapters.coerceAtLeast(0)} 章 · AI 深度 ${task.samples.coerceAtLeast(0)} 章${if (task.chapters > 0) " · 约 $coverage% 深度覆盖" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onOpenReport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Rounded.AutoFixHigh, null)
                    Spacer(Modifier.width(7.dp))
                    Text("查看 Story + Style DNA")
                }
            }

            if (task.state == WorkInfo.State.FAILED) {
                if (task.error.isNotBlank()) {
                    Text(
                        task.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (task.sourceAvailable) {
                    FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(7.dp))
                        Text(if (task.resumable) "从断点继续" else "重新尝试")
                    }
                } else {
                    Text(
                        "原导入副本已不可用，请重新选择小说；已有完整报告不会受影响。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberReferenceDistillationTasks(): List<ReferenceDistillationTaskUi> {
    val context = LocalContext.current.applicationContext
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val sourceStore = remember(context) { ReferenceDistillationSourceStore(context) }
    var tasks by remember { mutableStateOf<List<ReferenceDistillationTaskUi>>(emptyList()) }

    LaunchedEffect(workManager, sourceStore) {
        while (isActive) {
            val infos = withContext(Dispatchers.IO) {
                runCatching { workManager.getWorkInfosByTag(ReferenceDistillationJobs.TAG).get() }
                    .getOrDefault(emptyList())
            }
            tasks = infos
                .filter { it.state != WorkInfo.State.CANCELLED }
                .map { info ->
                    val progressData = info.progress
                    val output = info.outputData
                    val source = sourceStore.load(info.id.toString())
                    ReferenceDistillationTaskUi(
                        id = info.id.toString(),
                        state = info.state,
                        progress = when (info.state) {
                            WorkInfo.State.SUCCEEDED -> 100
                            else -> progressData.getInt("progress", 0)
                        },
                        stage = progressData.getString("stage").orEmpty(),
                        batch = progressData.getInt("batch", 0),
                        batches = progressData.getInt("batches", 0).takeIf { it > 0 }
                            ?: output.getInt("batches", 0),
                        title = progressData.getString("title").orEmpty().ifBlank { output.getString("title").orEmpty() },
                        provider = progressData.getString("provider").orEmpty().ifBlank { output.getString("provider").orEmpty() },
                        model = progressData.getString("model").orEmpty().ifBlank { output.getString("model").orEmpty() },
                        error = output.getString("error").orEmpty(),
                        chapters = output.getInt("chapters", 0),
                        samples = output.getInt("samples", 0),
                        runAttemptCount = info.runAttemptCount,
                        sourcePath = source?.path.orEmpty(),
                        sourceName = source?.displayName.orEmpty(),
                        resumable = output.getBoolean("resumable", false),
                        completedBatches = output.getInt("completedBatches", 0),
                    )
                }
                .sortedWith(
                    compareByDescending<ReferenceDistillationTaskUi> { it.active }
                        .thenByDescending { it.state == WorkInfo.State.FAILED && it.resumable }
                        .thenByDescending { it.state == WorkInfo.State.SUCCEEDED }
                )
                .take(5)
            delay(if (tasks.any { it.active }) 1_000L else 4_000L)
        }
    }
    return tasks
}

@Composable
private fun AiShelfBookCard(book: ReaderBookUi, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            AiShelfCover(book, Modifier.width(84.dp).height(120.dp))
            Column(Modifier.padding(start = 15.dp).weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(book.genre, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(7.dp))
                Text(
                    book.premise,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${book.currentWords} 字 · 第 ${book.currentChapter} 章",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiShelfCover(book: ReaderBookUi, modifier: Modifier) {
    val bitmap = remember(book.coverPath) {
        book.coverPath.takeIf { it.isNotBlank() }
            ?.let(BitmapFactory::decodeFile)
            ?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = book.title,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        book.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
