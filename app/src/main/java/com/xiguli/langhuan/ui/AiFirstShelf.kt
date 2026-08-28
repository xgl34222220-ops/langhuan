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
) {
    val active: Boolean
        get() = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED
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
    val distillationTasks = rememberReferenceDistillationTasks()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(18.dp, 20.dp, 18.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("琅嬛书架", style = MaterialTheme.typography.displaySmall)
                    Text("先和 AI 聊出一本书，再进入长期创作", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.stories.isNotEmpty()) {
                    IconButton(onClick = onCloseShelf) { Icon(Icons.Rounded.Close, "进入工作台") }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = if (aiReady) onStartCreation else onConfigureAi,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(19.dp),
                ) {
                    Icon(if (aiReady) Icons.Rounded.AutoAwesome else Icons.Rounded.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (aiReady) "和 AI 聊出一本新小说" else "先配置 AI，再开始聊新小说")
                }
                FilledTonalButton(
                    onClick = onConfigureAi,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(Icons.Rounded.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (aiReady) "AI 服务 / API Key" else "添加 API Key")
                }
                OutlinedButton(
                    onClick = if (aiReady) onDistillReference else onConfigureAi,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (aiReady) "导入参考小说 · AI 后台蒸馏" else "配置 AI 后导入参考小说")
                }
                Text(
                    "支持 TXT / Markdown / EPUB。先做本地结构统计，再由当前 AI 分批提炼视角、节奏、信息释放、悬念、人物塑造、规则呈现等高层 Style DNA，最后再由 AI 聚合成长期研究档案；不会把原文导入书架。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (distillationTasks.isNotEmpty()) {
            item {
                Text("参考小说蒸馏任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(distillationTasks, key = { it.id }) { task ->
                ReferenceDistillationTaskCard(
                    task = task,
                    fallbackProvider = aiProviderLabel,
                    fallbackModel = aiModel,
                )
            }
        }

        if (state.stories.isEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(28.dp), tonalElevation = 2.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(14.dp))
                        Text("从一个想法开始", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (aiReady) {
                                "不用先想好书名、类型和简介。告诉 AI 一个题材、一个画面，或者你喜欢的作品气质，聊到满意后再创建。"
                            } else {
                                "第一次使用先在上面添加 AI 服务和 API Key。配置完成后，就可以直接从一个题材、画面或角色开始聊天建书。"
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
private fun ReferenceDistillationTaskCard(
    task: ReferenceDistillationTaskUi,
    fallbackProvider: String,
    fallbackModel: String,
) {
    val progress = task.progress.coerceIn(0, 100)
    val stageLabel = when {
        task.state == WorkInfo.State.SUCCEEDED -> "蒸馏完成 · 已写入长期研究档案"
        task.state == WorkInfo.State.FAILED -> "蒸馏失败"
        task.state == WorkInfo.State.CANCELLED -> "任务已取消"
        task.state == WorkInfo.State.ENQUEUED && task.runAttemptCount > 0 -> "等待自动重试 · 第 ${task.runAttemptCount + 1} 次尝试"
        task.state == WorkInfo.State.ENQUEUED -> "等待后台调度 / 网络连接"
        task.state == WorkInfo.State.BLOCKED -> "等待前置条件"
        task.stage == "parse" -> "正在读取并解析小说"
        task.stage == "prepare" -> "小说已解析 · 正在准备 AI 蒸馏"
        task.stage == "distill" && task.batches > 0 -> "AI 正在分批蒸馏 Style DNA · ${task.batch}/${task.batches}"
        task.stage == "aggregate" -> "AI 正在聚合整部作品的 Style DNA"
        task.stage == "done" -> "正在完成长期研究档案入库"
        else -> "AI 蒸馏任务正在运行"
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
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stageLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$progress%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
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

            if (task.active || task.state == WorkInfo.State.SUCCEEDED) {
                Text(
                    "AI：$provider · $model",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.active) {
                Text(
                    "这是实际 AI 分析任务，不是本地假进度。任务锁定启动时选中的 AI；之后即使切换模型，这张卡仍显示本任务真正使用的服务与模型。退出琅嬛后由 WorkManager + 前台通知继续执行，再次进入会重新读取真实任务状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (task.state == WorkInfo.State.SUCCEEDED) {
                Text(
                    "已分析 ${task.chapters.coerceAtLeast(0)} 章 · 抽取 ${task.samples.coerceAtLeast(0)} 个代表样本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.state == WorkInfo.State.FAILED && task.error.isNotBlank()) {
                Text(
                    task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun rememberReferenceDistillationTasks(): List<ReferenceDistillationTaskUi> {
    val context = LocalContext.current.applicationContext
    val workManager = remember(context) { WorkManager.getInstance(context) }
    var tasks by remember { mutableStateOf<List<ReferenceDistillationTaskUi>>(emptyList()) }

    LaunchedEffect(workManager) {
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
                    ReferenceDistillationTaskUi(
                        id = info.id.toString(),
                        state = info.state,
                        progress = when (info.state) {
                            WorkInfo.State.SUCCEEDED -> 100
                            else -> progressData.getInt("progress", 0)
                        },
                        stage = progressData.getString("stage").orEmpty(),
                        batch = progressData.getInt("batch", 0),
                        batches = progressData.getInt("batches", 0),
                        title = progressData.getString("title").orEmpty().ifBlank { output.getString("title").orEmpty() },
                        provider = progressData.getString("provider").orEmpty().ifBlank { output.getString("provider").orEmpty() },
                        model = progressData.getString("model").orEmpty().ifBlank { output.getString("model").orEmpty() },
                        error = output.getString("error").orEmpty(),
                        chapters = output.getInt("chapters", 0),
                        samples = output.getInt("samples", 0),
                        runAttemptCount = info.runAttemptCount,
                    )
                }
                .sortedByDescending { it.active }
                .take(3)
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
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AiShelfCover(book, Modifier.width(88.dp).height(126.dp))
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(book.genre, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(
                    book.premise,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "${book.currentWords} 字 · 写到第 ${book.currentChapter} 章",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null)
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
            modifier = modifier.clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
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
