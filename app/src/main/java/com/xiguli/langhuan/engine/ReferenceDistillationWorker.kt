package com.xiguli.langhuan.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xiguli.langhuan.R
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryExchange
import com.xiguli.langhuan.domain.GeneratedChapter
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

object ReferenceDistillationJobs {
    const val TAG = "reference-novel-distillation"
    internal const val KEY_PATH = "path"
    internal const val KEY_NAME = "name"
    internal const val KEY_FINGERPRINT = "fingerprint"

    fun enqueue(context: Context, sourceFile: File, displayName: String): String =
        enqueueInternal(context, sourceFile, displayName, force = false)

    fun retry(context: Context, sourceFile: File, displayName: String): String =
        enqueueInternal(context, sourceFile, displayName, force = true)

    fun cancel(context: Context, workId: String) {
        runCatching { UUID.fromString(workId) }
            .getOrNull()
            ?.let { WorkManager.getInstance(context).cancelWorkById(it) }
    }

    private fun enqueueInternal(context: Context, sourceFile: File, displayName: String, force: Boolean): String {
        val fingerprint = stableFingerprint(sourceFile)
        val uniqueName = "reference-distill-$fingerprint"
        val workManager = WorkManager.getInstance(context)
        val sourceStore = ReferenceDistillationSourceStore(context.applicationContext)

        if (!force) {
            val active = runCatching { workManager.getWorkInfosForUniqueWork(uniqueName).get() }
                .getOrDefault(emptyList())
                .firstOrNull { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
            if (active != null) {
                sourceStore.save(active.id.toString(), sourceFile, displayName)
                return active.id.toString()
            }
        }

        val request = OneTimeWorkRequestBuilder<ReferenceDistillationWorker>()
            .setInputData(
                workDataOf(
                    KEY_PATH to sourceFile.absolutePath,
                    KEY_NAME to displayName,
                    KEY_FINGERPRINT to fingerprint,
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG)
            .addTag("reference:$fingerprint")
            .build()

        workManager.enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        sourceStore.save(request.id.toString(), sourceFile, displayName)
        return request.id.toString()
    }

    internal fun path(input: Data): String = input.getString(KEY_PATH).orEmpty()
    internal fun name(input: Data): String = input.getString(KEY_NAME).orEmpty()
    internal fun fingerprint(input: Data): String = input.getString(KEY_FINGERPRINT).orEmpty()

    private fun stableFingerprint(sourceFile: File): String = sha256("${sourceFile.name}|${sourceFile.length()}").take(16)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

class ReferenceDistillationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val repository = PersistentStoryRepository(appContext)
    private val archive = CreationResearchArchiveStore(appContext)
    private val reportStore = ReferenceDistillationReportStore(appContext)
    private val checkpointStore = ReferenceDistillationCheckpointStore(appContext)
    private val sourceStore = ReferenceDistillationSourceStore(appContext)

    override suspend fun doWork(): Result {
        val path = ReferenceDistillationJobs.path(inputData)
        val fileName = ReferenceDistillationJobs.name(inputData).ifBlank { File(path).name }
        val source = File(path)
        val fingerprint = ReferenceDistillationJobs.fingerprint(inputData).ifBlank { fallbackFingerprint(source) }
        val initialTitle = fileName.substringBeforeLast('.').ifBlank { "参考小说" }

        if (!source.exists() || source.length() == 0L) {
            checkpointStore.clear(fingerprint)
            return Result.failure(
                workDataOf(
                    "error" to "参考小说文件不存在或为空",
                    "title" to initialTitle,
                )
            )
        }

        setForeground(foreground("正在读取《$initialTitle》", 2))
        setProgress(progressData(stage = "parse", progress = 2, title = initialTitle))

        return runCatching {
            val manuscript = StoryExchange.import(fileName, source.readBytes())
            require(manuscript.chapters.isNotEmpty()) { "没有识别到可蒸馏的章节" }

            val savedCheckpoint = checkpointStore.load(fingerprint)
            val providers = repository.observeProviders().first()
            val provider = savedCheckpoint?.providerId
                ?.let { providerId -> providers.firstOrNull { it.id == providerId } }
                ?: providers.firstOrNull { it.isDefault }
                ?: providers.firstOrNull()
                ?: error("没有可用 AI 服务，请先在琅嬛添加 Key / 模型")
            val rawConfig = repository.providerConfig(provider.id)
                ?: error("当前 AI 服务配置无法读取")
            val config = if (
                savedCheckpoint != null &&
                savedCheckpoint.providerId == provider.id &&
                savedCheckpoint.model.isNotBlank()
            ) {
                rawConfig.copy(model = savedCheckpoint.model)
            } else {
                rawConfig
            }
            val gateway: AiGateway = UniversalAiGateway(config)
            val providerLabel = provider.name.ifBlank { provider.protocol.label }
            val modelLabel = config.model.ifBlank { provider.model }

            setProgress(
                progressData(
                    stage = "prepare",
                    progress = 6,
                    title = manuscript.title,
                    provider = providerLabel,
                    model = modelLabel,
                )
            )
            setForeground(foreground("《${manuscript.title}》已解析，准备双层 DNA 蒸馏", 6))

            val samples = buildSamples(manuscript)
            val localMetrics = localMetrics(manuscript, samples.size)
            val batchSize = when {
                samples.size <= 16 -> 2
                samples.size <= 36 -> 3
                else -> 4
            }
            val batches = samples.chunked(batchSize)

            val checkpoint = savedCheckpoint?.takeIf { saved ->
                saved.fingerprint == fingerprint &&
                    saved.title == manuscript.title &&
                    saved.chapters == manuscript.chapters.size &&
                    saved.samples == samples.size &&
                    saved.totalBatches == batches.size &&
                    saved.providerId == provider.id &&
                    saved.model == modelLabel
            }
            if (savedCheckpoint != null && checkpoint == null) checkpointStore.clear(fingerprint)

            val completed = checkpoint?.completedBatches?.coerceIn(0, batches.size) ?: 0
            val observations = checkpoint?.observations
                ?.take(completed)
                ?.toMutableList()
                ?: mutableListOf()

            if (checkpoint == null) {
                checkpointStore.save(
                    ReferenceDistillationCheckpoint(
                        fingerprint = fingerprint,
                        title = manuscript.title,
                        chapters = manuscript.chapters.size,
                        samples = samples.size,
                        providerId = provider.id,
                        provider = providerLabel,
                        model = modelLabel,
                        completedBatches = 0,
                        totalBatches = batches.size,
                        observations = emptyList(),
                        localMetrics = localMetrics,
                    )
                )
            } else if (completed > 0) {
                val resumeProgress = if (completed >= batches.size) 84 else 10 + (completed * 62 / batches.size.coerceAtLeast(1))
                setProgress(
                    progressData(
                        stage = if (completed >= batches.size) "aggregate_prepare" else "distill",
                        progress = resumeProgress,
                        title = manuscript.title,
                        provider = providerLabel,
                        model = modelLabel,
                        batch = completed,
                        batches = batches.size,
                    )
                )
                setForeground(
                    foreground(
                        if (completed >= batches.size) {
                            "《${manuscript.title}》批次已全部完成 · 从 DNA 聚合断点继续"
                        } else {
                            "《${manuscript.title}》从断点继续 · 已完成 $completed/${batches.size} 批"
                        },
                        resumeProgress,
                    )
                )
            }

            for (index in completed until batches.size) {
                val batch = batches[index]
                val progress = 10 + ((index + 1) * 62 / batches.size.coerceAtLeast(1))
                setProgress(
                    progressData(
                        stage = "distill",
                        progress = progress,
                        title = manuscript.title,
                        provider = providerLabel,
                        model = modelLabel,
                        batch = index + 1,
                        batches = batches.size,
                    )
                )
                setForeground(
                    foreground(
                        "双层蒸馏《${manuscript.title}》 · ${index + 1}/${batches.size}（${samples.size}章深度样本）",
                        progress,
                    )
                )

                val observation = distillBatch(
                    gateway = gateway,
                    title = manuscript.title,
                    batch = batch,
                    index = index + 1,
                    total = batches.size,
                )
                observations += observation

                val previous = checkpointStore.load(fingerprint)
                checkpointStore.save(
                    (previous ?: ReferenceDistillationCheckpoint(
                        fingerprint = fingerprint,
                        title = manuscript.title,
                        chapters = manuscript.chapters.size,
                        samples = samples.size,
                        providerId = provider.id,
                        provider = providerLabel,
                        model = modelLabel,
                        completedBatches = 0,
                        totalBatches = batches.size,
                        observations = emptyList(),
                        localMetrics = localMetrics,
                    )).copy(
                        completedBatches = index + 1,
                        observations = observations.toList(),
                        completedAggregateGroups = 0,
                        totalAggregateGroups = 0,
                        aggregateSummaries = emptyList(),
                    )
                )
            }

            setProgress(
                progressData(
                    stage = "aggregate_prepare",
                    progress = 84,
                    title = manuscript.title,
                    provider = providerLabel,
                    model = modelLabel,
                    batch = batches.size,
                    batches = batches.size,
                )
            )
            setForeground(foreground("正在准备《${manuscript.title}》分层 DNA 聚合", 84))

            val dossier = ReferenceDistillationHierarchicalAggregator(checkpointStore).aggregate(
                gateway = gateway,
                manuscript = manuscript,
                metrics = localMetrics,
                observations = observations,
                fingerprint = fingerprint,
                onProgress = { stage, progress, group, groups ->
                    setProgress(
                        progressData(
                            stage = stage,
                            progress = progress,
                            title = manuscript.title,
                            provider = providerLabel,
                            model = modelLabel,
                            batch = group,
                            batches = groups,
                        )
                    )
                    val text = when (stage) {
                        "aggregate_group" -> "分层聚合《${manuscript.title}》 · $group/$groups"
                        "aggregate_final" -> "正在生成《${manuscript.title}》最终 Story + Style DNA"
                        else -> "正在聚合《${manuscript.title}》双层 DNA"
                    }
                    setForeground(foreground(text, progress))
                },
            )
            val reportId = id.toString()
            reportStore.save(
                taskId = reportId,
                title = manuscript.title,
                chapters = manuscript.chapters.size,
                samples = samples.size,
                provider = providerLabel,
                model = modelLabel,
                localMetrics = localMetrics,
                dossier = dossier,
            )

            archive.merge(
                bundle = CreationResearchBundle(
                    originalText = "本地导入参考小说双层蒸馏：${manuscript.title}",
                    groups = listOf(
                        ReferenceResearchGroup(
                            target = manuscript.title,
                            result = WebResearchResult(
                                query = "本地双层蒸馏 · ${manuscript.chapters.size}章全书统计 · AI深度分层${samples.size}章 · Style+Story DNA",
                                sources = listOf(
                                    WebResearchSource(
                                        title = "[本地蒸馏] ${manuscript.title} · Style + Story DNA",
                                        url = "local://distillation/$reportId",
                                        snippet = dossier.content.take(1_000),
                                        detail = buildString {
                                            appendLine(dossier.summary)
                                            dossier.stateChanges.take(30).forEach { change ->
                                                appendLine("${change.subject}/${change.field}: ${change.after.ifBlank { change.before }}")
                                            }
                                        }.take(3_200),
                                    )
                                ),
                                engine = "Local import + bounded hierarchical dual-layer AI distillation",
                            ),
                        )
                    ),
                ),
                detectedTargets = listOf(manuscript.title),
            )

            setProgress(
                progressData(
                    stage = "done",
                    progress = 100,
                    title = manuscript.title,
                    provider = providerLabel,
                    model = modelLabel,
                    batch = batches.size,
                    batches = batches.size,
                )
            )
            setForeground(foreground("《${manuscript.title}》双层 DNA 蒸馏完成", 100))
            checkpointStore.clear(fingerprint)
            sourceStore.remove(id.toString())
            runCatching { source.delete() }

            Result.success(
                workDataOf(
                    "title" to manuscript.title,
                    "chapters" to manuscript.chapters.size,
                    "samples" to samples.size,
                    "provider" to providerLabel,
                    "model" to modelLabel,
                    "reportId" to reportId,
                    "fingerprint" to fingerprint,
                    "storyDna" to true,
                )
            )
        }.getOrElse { error ->
            val rawMessage = error.message ?: "参考小说蒸馏失败"
            if (runAttemptCount < 2 && isRetryable(rawMessage)) {
                Result.retry()
            } else {
                val checkpoint = checkpointStore.load(fingerprint)
                Result.failure(
                    workDataOf(
                        "error" to friendlyFailure(rawMessage, checkpoint).take(500),
                        "title" to checkpoint?.title.orEmpty().ifBlank { initialTitle },
                        "provider" to checkpoint?.provider.orEmpty(),
                        "model" to checkpoint?.model.orEmpty(),
                        "resumable" to ((checkpoint?.completedBatches ?: 0) > 0),
                        "completedBatches" to (checkpoint?.completedBatches ?: 0),
                        "batches" to (checkpoint?.totalBatches ?: 0),
                        "completedAggregateGroups" to (checkpoint?.completedAggregateGroups ?: 0),
                        "aggregateGroups" to (checkpoint?.totalAggregateGroups ?: 0),
                        "fingerprint" to fingerprint,
                    )
                )
            }
        }
    }

    private fun progressData(
        stage: String,
        progress: Int,
        title: String,
        provider: String = "",
        model: String = "",
        batch: Int = 0,
        batches: Int = 0,
    ): Data = workDataOf(
        "stage" to stage,
        "progress" to progress.coerceIn(0, 100),
        "title" to title,
        "provider" to provider,
        "model" to model,
        "batch" to batch,
        "batches" to batches,
    )

    /**
     * One pass extracts two different layers:
     * STYLE = how the book is written; STORY = what structural content the sampled chapters establish.
     * STORY may contain source-specific names for analysis, but later creation prompts explicitly treat
     * those details as understanding-only and require original replacements.
     */
    private suspend fun distillBatch(
        gateway: AiGateway,
        title: String,
        batch: List<SampleChunk>,
        index: Int,
        total: Int,
    ): String {
        val sampleText = batch.joinToString("\n\n") { sample ->
            "【分层样本：${sample.label}】\n${sample.text}"
        }
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是琅嬛的“参考小说双层蒸馏器”。用户导入了自己可访问的小说，用于创作研究。
                    同一批材料必须同时抽取两层信息，但不要复述长段原文。

                    A. STYLE（怎么写）只分析：
                    POV / RHYTHM / DIALOGUE / INFO / SUSPENSE / CHARACTERIZATION / RULE_PRESENTATION /
                    SCENE / EMOTION / STRUCTURE。

                    B. STORY（写了什么结构）只在本批证据足够时分析：
                    PROTAGONIST / SUPPORTING / RELATIONSHIP / WORLD / RULE / POWER / FACTION / LOCATION /
                    CONFLICT / MYSTERY / ARC / PROGRESSION / THEME。
                    STORY 可以写本作品真实人物名、身份、已明确能力或规则，目的是帮助用户理解原作；
                    但证据不足时必须省略或明确“本批无法确认”，禁止靠常识补剧情。

                    输出 GeneratedChapter JSON：
                    - title="DISTILL_BATCH"；
                    - content=150-320字本批整体观察；
                    - summary=80-180字最稳定写法规律；
                    - stateChanges=8-18项，subject 只能是 STYLE 或 STORY；
                      STYLE: field=上述写法维度，after=高层规律；
                      STORY: field=上述内容结构维度，after=简洁事实/结构判断；
                      evidence 只能写“第X章附近/前段样本/中段样本/后段样本”等短证据标签，不抄原句；
                    - touchedForeshadowingIds=[]。

                    禁止输出连续原文、标志性长句、逐章复述；禁止为了凑字段编造人物、能力或世界规则。
                """.trimIndent(),
                user = """
                    参考作品：$title
                    当前批次：$index/$total

                    $sampleText

                    先判断本批真正支持哪些 STORY 事实，再抽 STYLE。宁可少写不确定项，也不要脑补。
                """.trimIndent(),
            )
        )

        return buildString {
            if (output.summary.isNotBlank()) appendLine("STYLE_SUMMARY: ${output.summary.take(420)}")
            output.stateChanges.take(18).forEach { change ->
                val kind = change.subject.trim().uppercase()
                if (kind !in setOf("STYLE", "DNA", "STORY")) return@forEach
                val normalized = if (kind == "DNA") "STYLE" else kind
                val value = change.after.ifBlank { change.before }.trim().take(320)
                if (value.isNotBlank()) {
                    appendLine("$normalized/${change.field.trim().ifBlank { "UNKNOWN" }}: $value${change.evidence.trim().takeIf(String::isNotBlank)?.let { " [${it.take(80)}]" }.orEmpty()}")
                }
            }
            if (length < 700 && output.content.isNotBlank()) appendLine("BATCH_OVERVIEW: ${output.content.take(700)}")
        }.take(3_500)
    }

    private fun buildSamples(manuscript: ImportedManuscript): List<SampleChunk> {
        val chapters = manuscript.chapters.filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) return emptyList()
        val desired = desiredSampleCount(chapters.size).coerceAtMost(chapters.size)
        val indices = linkedSetOf<Int>()

        fun add(index: Int) {
            if (index in chapters.indices) indices += index
        }

        // Always inspect the exact beginning/end plus evenly distributed interior stages.
        add(0)
        add(1)
        add(2)
        add(chapters.lastIndex - 2)
        add(chapters.lastIndex - 1)
        add(chapters.lastIndex)

        val slots = (desired - indices.size).coerceAtLeast(0)
        if (slots > 0) {
            for (slot in 1..slots) {
                val ratio = slot.toDouble() / (slots + 1).toDouble()
                add((ratio * chapters.lastIndex).roundToInt())
            }
        }

        if (indices.size < desired) {
            val step = chapters.size.toDouble() / desired.coerceAtLeast(1)
            var cursor = step / 2.0
            while (indices.size < desired && cursor < chapters.size) {
                add(cursor.roundToInt().coerceAtMost(chapters.lastIndex))
                cursor += step
            }
        }

        val sampleChars = sampleCharsFor(chapters.size)
        return indices.sorted().take(desired).map { index ->
            val chapter = chapters[index]
            SampleChunk(
                label = "第${index + 1}/${chapters.size}章 ${chapter.title}",
                text = compactChapterSample(chapter, sampleChars),
            )
        }
    }

    /** Adaptive deep sampling. All chapters still participate in local statistics. */
    private fun desiredSampleCount(chapterCount: Int): Int = when {
        chapterCount <= 20 -> chapterCount
        chapterCount <= 80 -> 24
        chapterCount <= 200 -> 36
        chapterCount <= 400 -> 48
        chapterCount <= 700 -> 56
        chapterCount <= 1_000 -> 64
        chapterCount <= 1_500 -> 72
        else -> 80
    }

    private fun sampleCharsFor(chapterCount: Int): Int = when {
        chapterCount > 1_500 -> 1_700
        chapterCount > 1_000 -> 1_900
        chapterCount > 700 -> 2_100
        chapterCount > 400 -> 2_300
        chapterCount > 200 -> 2_600
        chapterCount > 80 -> 3_000
        else -> 4_200
    }

    private fun compactChapterSample(chapter: ImportedChapter, maxChars: Int): String {
        val text = chapter.content.trim()
        if (text.length <= maxChars) return text
        val piece = (maxChars / 3).coerceAtLeast(300)
        val middleStart = (text.length / 2 - piece / 2).coerceAtLeast(piece)
        return buildString {
            appendLine(text.take(piece))
            appendLine("\n[…本章中段省略，保留中段采样…]\n")
            appendLine(text.substring(middleStart, (middleStart + piece).coerceAtMost(text.length)))
            appendLine("\n[…本章后段采样…]\n")
            append(text.takeLast(piece))
        }.take(maxChars + 120)
    }

    private fun localMetrics(manuscript: ImportedManuscript, sampledChapters: Int): String {
        val chapters = manuscript.chapters.filter { it.content.isNotBlank() }
        val lengths = chapters.map { it.content.length }
        val allParagraphs = chapters.flatMap { it.content.split(Regex("\\n\\s*\\n|\\n")) }
            .map(String::trim)
            .filter(String::isNotBlank)
        val paragraphAverage = allParagraphs.map(String::length).average().takeIf { !it.isNaN() } ?: 0.0
        val dialogueParagraphs = allParagraphs.count { paragraph ->
            paragraph.contains('“') || paragraph.contains('”') || paragraph.startsWith("\"")
        }
        val dialogueRatio = if (allParagraphs.isEmpty()) 0 else dialogueParagraphs * 100 / allParagraphs.size
        val chapterAverage = lengths.average().takeIf { !it.isNaN() } ?: 0.0
        val shortParagraphRatio = if (allParagraphs.isEmpty()) 0 else allParagraphs.count { it.length <= 45 } * 100 / allParagraphs.size
        return buildString {
            appendLine("全书结构扫描：${chapters.size}/${manuscript.chapters.size} 个有效章节（不是只处理 $sampledChapters 章）")
            appendLine("总文本约：${lengths.sum()} 字符；平均章节：${chapterAverage.toInt()} 字符")
            appendLine("平均段落：${paragraphAverage.toInt()} 字符；含对白标记段落约：$dialogueRatio%；45字符以内短段约：$shortParagraphRatio%")
            appendLine("AI 深度分层阅读：$sampledChapters/${chapters.size} 章；覆盖开篇、前中段、中段、中后段、后段与结尾，用于 Style DNA + Story DNA。")
        }
    }

    private fun foreground(text: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "琅嬛长任务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "参考小说蒸馏、全书分析等长时间任务"
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("琅嬛 · 参考小说双层蒸馏")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(progress < 100)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun isRetryable(message: String): Boolean {
        val lower = message.lowercase()
        return listOf("timeout", "timed out", "429", "502", "503", "504", "network", "connection", "socket").any(lower::contains)
    }

    private fun friendlyFailure(message: String, checkpoint: ReferenceDistillationCheckpoint?): String {
        val lower = message.lowercase()
        val aggregateSuffix = if ((checkpoint?.completedBatches ?: 0) >= (checkpoint?.totalBatches ?: Int.MAX_VALUE) && (checkpoint?.totalBatches ?: 0) > 0) {
            val done = checkpoint?.completedAggregateGroups ?: 0
            val total = checkpoint?.totalAggregateGroups ?: 0
            if (total > 0) "前面 AI 批次已全部完成，聚合断点 $done/$total 已保存；继续时不会重跑前面的批次。"
            else "前面 AI 批次已全部完成；继续时会直接进入分层聚合，不会重跑前面的批次。"
        } else {
            "已保留完成批次断点，可从断点继续。"
        }
        return when {
            "unexpected json" in lower || "json token" in lower || "serialization" in lower ->
                "AI 已返回结果，但结构化 JSON 格式异常。$aggregateSuffix"
            "timeout" in lower || "timed out" in lower || "超时" in message ->
                "AI 或中转站响应超时。$aggregateSuffix"
            "429" in lower -> "AI 服务触发频率限制（429）。$aggregateSuffix"
            "502" in lower || "503" in lower || "504" in lower -> "中转站或上游模型暂时不可用。$aggregateSuffix"
            else -> message
        }
    }

    private fun fallbackFingerprint(source: File): String = MessageDigest.getInstance("SHA-256")
        .digest("${source.name}|${source.length()}".toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)

    private data class SampleChunk(val label: String, val text: String)

    private companion object {
        const val CHANNEL_ID = "langhuan_long_tasks"
        const val NOTIFICATION_ID = 43021
    }
}
