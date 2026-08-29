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
        runCatching { UUID.fromString(workId) }.getOrNull()
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
                .firstOrNull { it.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED) }
            if (active != null) {
                sourceStore.save(active.id.toString(), sourceFile, displayName)
                return active.id.toString()
            }
        }
        val request = OneTimeWorkRequestBuilder<ReferenceDistillationWorker>()
            .setInputData(workDataOf(KEY_PATH to sourceFile.absolutePath, KEY_NAME to displayName, KEY_FINGERPRINT to fingerprint))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .addTag("reference:$fingerprint")
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        sourceStore.save(request.id.toString(), sourceFile, displayName)
        return request.id.toString()
    }

    internal fun path(input: Data): String = input.getString(KEY_PATH).orEmpty()
    internal fun name(input: Data): String = input.getString(KEY_NAME).orEmpty()
    internal fun fingerprint(input: Data): String = input.getString(KEY_FINGERPRINT).orEmpty()

    private fun stableFingerprint(sourceFile: File): String = sha256("${sourceFile.name}|${sourceFile.length()}").take(16)
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

class ReferenceDistillationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
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
            return Result.failure(workDataOf("error" to "参考小说文件不存在或为空", "title" to initialTitle))
        }

        setForeground(foreground("正在读取《$initialTitle》", 2))
        setProgress(progressData("parse", 2, initialTitle))

        return runCatching {
            val manuscript = StoryExchange.import(fileName, source.readBytes())
            require(manuscript.chapters.isNotEmpty()) { "没有识别到可蒸馏的章节" }
            val savedCheckpoint = checkpointStore.load(fingerprint)
            val providers = repository.observeProviders().first()
            val provider = savedCheckpoint?.providerId?.let { id -> providers.firstOrNull { it.id == id } }
                ?: providers.firstOrNull { it.isDefault }
                ?: providers.firstOrNull()
                ?: error("没有可用 AI 服务，请先在琅嬛添加 Key / 模型")
            val rawConfig = repository.providerConfig(provider.id) ?: error("当前 AI 服务配置无法读取")
            val config = if (savedCheckpoint != null && savedCheckpoint.providerId == provider.id && savedCheckpoint.model.isNotBlank()) rawConfig.copy(model = savedCheckpoint.model) else rawConfig
            val gateway: AiGateway = UniversalAiGateway(config)
            val providerLabel = provider.name.ifBlank { provider.protocol.label }
            val modelLabel = config.model.ifBlank { provider.model }

            setProgress(progressData("prepare", 6, manuscript.title, providerLabel, modelLabel))
            setForeground(foreground("《${manuscript.title}》已解析，准备 V2 可检索 DNA 蒸馏", 6))

            val samples = buildSamples(manuscript)
            val localMetrics = localMetrics(manuscript, samples.size)
            val batchSize = when {
                samples.size <= 20 -> 2
                samples.size <= 72 -> 3
                else -> 4
            }
            val batches = samples.chunked(batchSize)
            val checkpoint = savedCheckpoint?.takeIf { saved ->
                saved.fingerprint == fingerprint && saved.title == manuscript.title && saved.chapters == manuscript.chapters.size &&
                    saved.samples == samples.size && saved.totalBatches == batches.size && saved.providerId == provider.id && saved.model == modelLabel
            }
            if (savedCheckpoint != null && checkpoint == null) checkpointStore.clear(fingerprint)

            val completed = checkpoint?.completedBatches?.coerceIn(0, batches.size) ?: 0
            val observations = checkpoint?.observations?.take(completed)?.toMutableList() ?: mutableListOf()
            if (checkpoint == null) {
                checkpointStore.save(
                    ReferenceDistillationCheckpoint(
                        fingerprint = fingerprint, title = manuscript.title, chapters = manuscript.chapters.size, samples = samples.size,
                        providerId = provider.id, provider = providerLabel, model = modelLabel, completedBatches = 0,
                        totalBatches = batches.size, observations = emptyList(), localMetrics = localMetrics,
                    )
                )
            } else if (completed > 0) {
                val resumeProgress = if (completed >= batches.size) 84 else 10 + (completed * 62 / batches.size.coerceAtLeast(1))
                setProgress(progressData(if (completed >= batches.size) "aggregate_prepare" else "distill", resumeProgress, manuscript.title, providerLabel, modelLabel, completed, batches.size))
                setForeground(foreground(if (completed >= batches.size) "《${manuscript.title}》批次已完成 · 从 DNA 聚合断点继续" else "《${manuscript.title}》从断点继续 · 已完成 $completed/${batches.size} 批", resumeProgress))
            }

            for (index in completed until batches.size) {
                val batch = batches[index]
                val progress = 10 + ((index + 1) * 62 / batches.size.coerceAtLeast(1))
                setProgress(progressData("distill", progress, manuscript.title, providerLabel, modelLabel, index + 1, batches.size))
                setForeground(foreground("V2 深度蒸馏《${manuscript.title}》 · ${index + 1}/${batches.size}（${samples.size}章样本）", progress))
                val observation = distillBatch(gateway, manuscript.title, batch, index + 1, batches.size)
                observations += observation
                val previous = checkpointStore.load(fingerprint)
                checkpointStore.save(
                    (previous ?: ReferenceDistillationCheckpoint(
                        fingerprint = fingerprint, title = manuscript.title, chapters = manuscript.chapters.size, samples = samples.size,
                        providerId = provider.id, provider = providerLabel, model = modelLabel, completedBatches = 0,
                        totalBatches = batches.size, observations = emptyList(), localMetrics = localMetrics,
                    )).copy(
                        completedBatches = index + 1,
                        observations = observations.toList(),
                        completedAggregateGroups = 0,
                        totalAggregateGroups = 0,
                        aggregateSummaries = emptyList(),
                    )
                )
            }

            setProgress(progressData("aggregate_prepare", 84, manuscript.title, providerLabel, modelLabel, batches.size, batches.size))
            setForeground(foreground("正在准备《${manuscript.title}》分层 DNA 聚合", 84))
            val dossier = ReferenceDistillationHierarchicalAggregator(checkpointStore).aggregate(
                gateway = gateway, manuscript = manuscript, metrics = localMetrics, observations = observations, fingerprint = fingerprint,
                onProgress = { stage, progress, group, groups ->
                    setProgress(progressData(stage, progress, manuscript.title, providerLabel, modelLabel, group, groups))
                    val text = when (stage) {
                        "aggregate_group" -> "分层聚合《${manuscript.title}》 · $group/$groups"
                        "aggregate_final" -> "生成《${manuscript.title}》最终总览；批次 DNA 同时长期保留"
                        else -> "聚合《${manuscript.title}》双层 DNA"
                    }
                    setForeground(foreground(text, progress))
                },
            )
            val reportId = id.toString()
            val report = reportStore.save(
                taskId = reportId, title = manuscript.title, chapters = manuscript.chapters.size, samples = samples.size,
                provider = providerLabel, model = modelLabel, localMetrics = localMetrics, dossier = dossier, retainedObservations = observations,
            )

            archive.merge(
                bundle = CreationResearchBundle(
                    originalText = "本地导入参考小说 V2 双层蒸馏：${manuscript.title}",
                    groups = listOf(
                        ReferenceResearchGroup(
                            target = manuscript.title,
                            result = WebResearchResult(
                                query = "本地 V2 蒸馏 · ${manuscript.chapters.size}章全书统计 · AI深读${samples.size}章 · 可检索DNA ${reportStore.retainedItemCount(report)}条",
                                sources = listOf(
                                    WebResearchSource(
                                        title = "[本地蒸馏V2] ${manuscript.title} · 可检索 Story + Style DNA",
                                        url = "local://distillation/$reportId",
                                        snippet = dossier.content.take(1_200),
                                        detail = buildString {
                                            appendLine(dossier.summary)
                                            appendLine("可检索DNA=${reportStore.retainedItemCount(report)}")
                                            report.kindCounts(report).forEach { (kind, count) -> appendLine("$kind=$count") }
                                        }.take(3_600),
                                    )
                                ),
                                engine = "Local import + retained hierarchical dual-layer AI distillation V2",
                            ),
                        )
                    ),
                ),
                detectedTargets = listOf(manuscript.title),
            )

            setProgress(progressData("done", 100, manuscript.title, providerLabel, modelLabel, batches.size, batches.size))
            setForeground(foreground("《${manuscript.title}》V2 蒸馏完成 · ${reportStore.retainedItemCount(report)} 条可检索 DNA", 100))
            checkpointStore.clear(fingerprint)
            sourceStore.remove(id.toString())
            runCatching { source.delete() }
            Result.success(workDataOf(
                "title" to manuscript.title, "chapters" to manuscript.chapters.size, "samples" to samples.size,
                "provider" to providerLabel, "model" to modelLabel, "reportId" to reportId, "fingerprint" to fingerprint,
                "storyDna" to true, "retrievalItems" to reportStore.retainedItemCount(report),
            ))
        }.getOrElse { error ->
            val rawMessage = error.message ?: "参考小说蒸馏失败"
            if (runAttemptCount < 2 && isRetryable(rawMessage)) Result.retry()
            else {
                val checkpoint = checkpointStore.load(fingerprint)
                Result.failure(workDataOf(
                    "error" to friendlyFailure(rawMessage, checkpoint).take(500),
                    "title" to checkpoint?.title.orEmpty().ifBlank { initialTitle },
                    "provider" to checkpoint?.provider.orEmpty(), "model" to checkpoint?.model.orEmpty(),
                    "resumable" to ((checkpoint?.completedBatches ?: 0) > 0),
                    "completedBatches" to (checkpoint?.completedBatches ?: 0), "batches" to (checkpoint?.totalBatches ?: 0),
                    "completedAggregateGroups" to (checkpoint?.completedAggregateGroups ?: 0), "aggregateGroups" to (checkpoint?.totalAggregateGroups ?: 0),
                    "fingerprint" to fingerprint,
                ))
            }
        }
    }

    private fun progressData(stage: String, progress: Int, title: String, provider: String = "", model: String = "", batch: Int = 0, batches: Int = 0): Data =
        workDataOf("stage" to stage, "progress" to progress.coerceIn(0, 100), "title" to title, "provider" to provider, "model" to model, "batch" to batch, "batches" to batches)

    private suspend fun distillBatch(gateway: AiGateway, title: String, batch: List<SampleChunk>, index: Int, total: Int): String {
        val sampleText = batch.joinToString("\n\n") { "【分层样本：${it.label}】\n${it.text}" }
        val output = gateway.generate(
            PromptBundle(
                system = """
                    你是琅嬛“参考小说 V2 双层蒸馏器”。这里不是写摘要，而是在建立之后能被检索的作品 DNA 库。
                    同一批材料同时抽取：
                    A. STYLE：POV / RHYTHM / DIALOGUE / INFO / SUSPENSE / CHARACTERIZATION / RULE_PRESENTATION / SCENE / EMOTION / STRUCTURE。
                    B. STORY：PROTAGONIST / SUPPORTING / RELATIONSHIP / WORLD / RULE / POWER / FACTION / LOCATION / CONFLICT / MYSTERY / ARC / PROGRESSION / THEME；如果作品存在副本、试炼、关卡、梦境任务、规则场域等，还必须优先抽取 INSTANCE / INSTANCE_RULE / INSTANCE_OBJECTIVE / INSTANCE_NPC / INSTANCE_THREAT / INSTANCE_CLUE / INSTANCE_CLEAR / INSTANCE_REWARD / INSTANCE_MAINLINE。

                    副本信息抽取标准：
                    - INSTANCE：副本名/类型/整体场景/首次出现阶段；
                    - INSTANCE_RULE：进入条件、限制、禁忌、触发规则；
                    - INSTANCE_OBJECTIVE：阶段目标与任务推进；INSTANCE_NPC：关键 NPC/怪物/参与者；
                    - INSTANCE_THREAT：失败、死亡、惩罚或失控条件；INSTANCE_CLUE：核心线索链与误导；
                    - INSTANCE_CLEAR：通关/离开/结算条件；INSTANCE_REWARD：奖励、代价、能力或资源变化；
                    - INSTANCE_MAINLINE：该副本与主线、人物成长、谜团或世界观的关系。
                    同一副本不要压成一句总述；文本支持时拆成多条单一、可检索事实。若样本只覆盖副本的一部分，明确“阶段信息/未见结算”，禁止脑补完整通关流程。

                    输出 GeneratedChapter JSON：
                    - title="DISTILL_BATCH"；content=180-380字阶段观察；summary=100-220字写法规律；
                    - stateChanges=12-32项，subject 只能 STYLE 或 STORY；每项只表达一个可检索事实/规律，不把多个维度塞进同一句；
                    - STORY 可以准确保存作品人物名、身份、规则、能力、副本名与副本机制，以支持原作事实问答，但证据不足必须省略；
                    - evidence 写“第X章附近/前段样本/中段样本/后段样本”等短证据标签，不抄原文；
                    - touchedForeshadowingIds=[]。
                    禁止逐章复述、禁止长引原文、禁止靠类型常识补事实。宁可保留更多互不重复的细粒度 DNA，也不要把整批压成几句空泛总结。
                """.trimIndent(),
                user = """
                    参考作品：$title
                    当前批次：$index/$total

                    $sampleText

                    先提取本批真正被文本支持的 STORY；若出现副本/试炼/规则场域，先把副本结构拆清楚，再提取其他 STORY 与 STYLE。每条 DNA 尽量单一、具体、可检索。
                """.trimIndent(),
            )
        )
        return buildString {
            if (output.summary.isNotBlank()) appendLine("STYLE_SUMMARY: ${output.summary.take(520)}")
            output.stateChanges.take(32).forEach { change ->
                val kind = change.subject.trim().uppercase().let { if (it == "DNA") "STYLE" else it }
                if (kind !in setOf("STYLE", "STORY")) return@forEach
                val value = change.after.ifBlank { change.before }.trim().take(420)
                if (value.isNotBlank()) {
                    appendLine("$kind/${change.field.trim().ifBlank { "UNKNOWN" }}: $value${change.evidence.trim().takeIf(String::isNotBlank)?.let { " [${it.take(100)}]" }.orEmpty()}")
                }
            }
            if (length < 900 && output.content.isNotBlank()) appendLine("BATCH_OVERVIEW: ${output.content.take(800)}")
        }.take(5_800)
    }

    private fun buildSamples(manuscript: ImportedManuscript): List<SampleChunk> {
        val chapters = manuscript.chapters.filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) return emptyList()
        val desired = desiredSampleCount(chapters.size).coerceAtMost(chapters.size)
        val indices = linkedSetOf<Int>()
        fun add(index: Int) { if (index in chapters.indices) indices += index }
        (0..4).forEach(::add)
        (chapters.lastIndex - 4..chapters.lastIndex).forEach(::add)
        val slots = (desired - indices.size).coerceAtLeast(0)
        if (slots > 0) for (slot in 1..slots) add((slot.toDouble() / (slots + 1).toDouble() * chapters.lastIndex).roundToInt())
        if (indices.size < desired) {
            val step = chapters.size.toDouble() / desired.coerceAtLeast(1)
            var cursor = step / 2.0
            while (indices.size < desired && cursor < chapters.size) { add(cursor.roundToInt().coerceAtMost(chapters.lastIndex)); cursor += step }
        }
        val sampleChars = sampleCharsFor(chapters.size)
        return indices.sorted().take(desired).map { index ->
            val chapter = chapters[index]
            SampleChunk("第${index + 1}/${chapters.size}章 ${chapter.title}", compactChapterSample(chapter, sampleChars))
        }
    }

    private fun desiredSampleCount(chapterCount: Int): Int = when {
        chapterCount <= 24 -> chapterCount
        chapterCount <= 80 -> 40
        chapterCount <= 200 -> 64
        chapterCount <= 400 -> 80
        chapterCount <= 700 -> 96
        chapterCount <= 1_000 -> 112
        chapterCount <= 1_500 -> 128
        else -> 144
    }

    private fun sampleCharsFor(chapterCount: Int): Int = when {
        chapterCount > 1_500 -> 1_800
        chapterCount > 1_000 -> 2_000
        chapterCount > 700 -> 2_150
        chapterCount > 400 -> 2_350
        chapterCount > 200 -> 2_650
        chapterCount > 80 -> 3_100
        else -> 4_400
    }

    private fun compactChapterSample(chapter: ImportedChapter, maxChars: Int): String {
        val text = chapter.content.trim()
        if (text.length <= maxChars) return text
        val piece = (maxChars / 3).coerceAtLeast(320)
        val middleStart = (text.length / 2 - piece / 2).coerceAtLeast(piece)
        return buildString {
            appendLine(text.take(piece)); appendLine("\n[…保留本章中段采样…]\n")
            appendLine(text.substring(middleStart, (middleStart + piece).coerceAtMost(text.length)))
            appendLine("\n[…保留本章后段采样…]\n"); append(text.takeLast(piece))
        }.take(maxChars + 120)
    }

    private fun localMetrics(manuscript: ImportedManuscript, sampledChapters: Int): String {
        val chapters = manuscript.chapters.filter { it.content.isNotBlank() }
        val lengths = chapters.map { it.content.length }
        val allParagraphs = chapters.flatMap { it.content.split(Regex("\\n\\s*\\n|\\n")) }.map(String::trim).filter(String::isNotBlank)
        val paragraphAverage = allParagraphs.map(String::length).average().takeIf { !it.isNaN() } ?: 0.0
        val dialogueParagraphs = allParagraphs.count { it.contains('“') || it.contains('”') || it.startsWith("\"") }
        val dialogueRatio = if (allParagraphs.isEmpty()) 0 else dialogueParagraphs * 100 / allParagraphs.size
        val chapterAverage = lengths.average().takeIf { !it.isNaN() } ?: 0.0
        val shortParagraphRatio = if (allParagraphs.isEmpty()) 0 else allParagraphs.count { it.length <= 45 } * 100 / allParagraphs.size
        return buildString {
            appendLine("全书结构扫描：${chapters.size}/${manuscript.chapters.size} 个有效章节")
            appendLine("总文本约：${lengths.sum()} 字符；平均章节：${chapterAverage.toInt()} 字符")
            appendLine("平均段落：${paragraphAverage.toInt()} 字符；含对白标记段落约：$dialogueRatio%；45字符以内短段约：$shortParagraphRatio%")
            appendLine("V2 AI 深度阅读：$sampledChapters/${chapters.size} 章；覆盖首尾与全程均匀阶段。每批结构化 DNA 会长期保留，不再只保存最终摘要。")
        }
    }

    private fun foreground(text: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "琅嬛长任务", NotificationManager.IMPORTANCE_LOW).apply { description = "参考小说蒸馏、全书分析等长时间任务" })
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("琅嬛 · 参考小说 V2 蒸馏").setContentText(text)
            .setOnlyAlertOnce(true).setOngoing(progress < 100).setProgress(100, progress.coerceIn(0, 100), false).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun isRetryable(message: String): Boolean {
        val lower = message.lowercase()
        return listOf("timeout", "timed out", "429", "502", "503", "504", "network", "connection", "socket").any(lower::contains)
    }

    private fun friendlyFailure(message: String, checkpoint: ReferenceDistillationCheckpoint?): String {
        val lower = message.lowercase()
        val suffix = if ((checkpoint?.completedBatches ?: 0) >= (checkpoint?.totalBatches ?: Int.MAX_VALUE) && (checkpoint?.totalBatches ?: 0) > 0) {
            val done = checkpoint?.completedAggregateGroups ?: 0
            val total = checkpoint?.totalAggregateGroups ?: 0
            if (total > 0) "前面 AI 批次已完成，聚合断点 $done/$total 已保存；继续时不会重跑批次。" else "前面 AI 批次已完成；继续时直接聚合。"
        } else "已保留完成批次断点，可从断点继续。"
        return when {
            "unexpected json" in lower || "json token" in lower || "serialization" in lower -> "AI 已返回结果，但结构化 JSON 格式异常。$suffix"
            "timeout" in lower || "timed out" in lower || "超时" in message -> "AI 或中转站响应超时。$suffix"
            "429" in lower -> "AI 服务触发频率限制（429）。$suffix"
            "502" in lower || "503" in lower || "504" in lower -> "中转站或上游模型暂时不可用。$suffix"
            else -> message
        }
    }

    private fun fallbackFingerprint(source: File): String = MessageDigest.getInstance("SHA-256")
        .digest("${source.name}|${source.length()}".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private data class SampleChunk(val label: String, val text: String)
    private companion object {
        const val CHANNEL_ID = "langhuan_long_tasks"
        const val NOTIFICATION_ID = 43021
    }
}