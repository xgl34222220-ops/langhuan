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
        if (!force) {
            val active = runCatching { workManager.getWorkInfosForUniqueWork(uniqueName).get() }
                .getOrDefault(emptyList())
                .firstOrNull { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
            if (active != null) return active.id.toString()
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
            setForeground(foreground("《${manuscript.title}》已解析，准备 AI 分层蒸馏", 6))

            val samples = buildSamples(manuscript)
            val localMetrics = localMetrics(manuscript, samples.size)
            val batchSize = when {
                samples.size <= 12 -> 2
                samples.size <= 24 -> 3
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
                val resumeProgress = 10 + (completed * 60 / batches.size.coerceAtLeast(1))
                setProgress(
                    progressData(
                        stage = "distill",
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
                        "《${manuscript.title}》从断点继续 · 已完成 $completed/${batches.size} 批",
                        resumeProgress,
                    )
                )
            }

            for (index in completed until batches.size) {
                val batch = batches[index]
                val progress = 10 + ((index + 1) * 60 / batches.size.coerceAtLeast(1))
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
                        "正在分层蒸馏《${manuscript.title}》 · ${index + 1}/${batches.size}（共${samples.size}章样本）",
                        progress,
                    )
                )
                val observation = distillBatch(gateway, manuscript.title, batch, index + 1, batches.size)
                observations += observation
                checkpointStore.save(
                    ReferenceDistillationCheckpoint(
                        fingerprint = fingerprint,
                        title = manuscript.title,
                        chapters = manuscript.chapters.size,
                        samples = samples.size,
                        providerId = provider.id,
                        provider = providerLabel,
                        model = modelLabel,
                        completedBatches = index + 1,
                        totalBatches = batches.size,
                        observations = observations.toList(),
                        localMetrics = localMetrics,
                    )
                )
            }

            setProgress(
                progressData(
                    stage = "aggregate",
                    progress = 82,
                    title = manuscript.title,
                    provider = providerLabel,
                    model = modelLabel,
                    batch = batches.size,
                    batches = batches.size,
                )
            )
            setForeground(foreground("正在聚合《${manuscript.title}》整书 Style DNA", 82))
            val dossier = aggregate(gateway, manuscript, localMetrics, observations)
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
                    originalText = "本地导入参考小说蒸馏：${manuscript.title}",
                    groups = listOf(
                        ReferenceResearchGroup(
                            target = manuscript.title,
                            result = WebResearchResult(
                                query = "本地导入分层蒸馏 · ${manuscript.chapters.size} 章 · AI覆盖${samples.size}章 · 不保存原文",
                                sources = listOf(
                                    WebResearchSource(
                                        title = "[本地蒸馏] ${manuscript.title} · Style DNA",
                                        url = "local://distillation/$reportId",
                                        snippet = dossier.content.take(650),
                                        detail = buildString {
                                            appendLine(dossier.summary)
                                            dossier.stateChanges.take(18).forEach { change ->
                                                appendLine("${change.subject}/${change.field}: ${change.after.ifBlank { change.before }}")
                                            }
                                        }.take(1600),
                                    )
                                ),
                                engine = "Local import + adaptive AI distillation",
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
            setForeground(foreground("《${manuscript.title}》蒸馏完成，已加入长期研究档案", 100))
            checkpointStore.clear(fingerprint)
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
                )
            )
        }.getOrElse { error ->
            val message = error.message ?: "参考小说蒸馏失败"
            if (runAttemptCount < 2 && isRetryable(message)) {
                Result.retry()
            } else {
                val checkpoint = checkpointStore.load(fingerprint)
                Result.failure(
                    workDataOf(
                        "error" to message.take(500),
                        "title" to initialTitle,
                        "resumable" to ((checkpoint?.completedBatches ?: 0) > 0),
                        "completedBatches" to (checkpoint?.completedBatches ?: 0),
                        "batches" to (checkpoint?.totalBatches ?: 0),
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
                    你是琅嬛的“参考小说特征蒸馏器”。用户合法导入了一部自己可访问的小说，用于提炼高层写作规律。
                    你的任务是抽象分析，不是续写、仿写或复制原文。

                    只分析这些高层维度：
                    - 叙事视角与叙事距离
                    - 句子/段落节奏、对白与动作的配比倾向
                    - 信息释放、悬念制造、章末钩子
                    - 人物性格如何通过行动/对白呈现
                    - 世界规则/能力/设定如何被展示，而不是具体规则内容
                    - 场景进入/退出、时间跳转、冲突升级方式
                    - 情绪温度、恐怖/悬疑/爽感来自何处
                    - 可迁移的结构模式，以及不能照搬的标志性表达/专名/剧情骨架

                    禁止：复述长段原文；输出连续原文；记忆或要求保留原作句子；建议换名照搬人物或剧情。
                    输出 GeneratedChapter JSON：title="DISTILL_BATCH"；content=250-500字高层观察；summary=80-180字本批最稳定规律；stateChanges可用 subject="DNA"、field=维度、after=抽象规律、evidence=极短证据描述（不要引用原句）；touchedForeshadowingIds=[]。
                """.trimIndent(),
                user = """
                    参考作品：$title
                    当前批次：$index/$total

                    $sampleText

                    只做抽象特征蒸馏，不评价作品优劣，不输出原文摘抄。
                """.trimIndent(),
            )
        )
        return buildString {
            if (output.summary.isNotBlank()) appendLine("稳定规律：${output.summary.take(420)}")
            output.stateChanges.take(10).forEach { change ->
                val value = change.after.ifBlank { change.before }.take(180)
                if (value.isNotBlank()) appendLine("${change.field}: $value")
            }
            if (length < 500 && output.content.isNotBlank()) appendLine(output.content.take(700))
        }.take(2_000)
    }

    private suspend fun aggregate(
        gateway: AiGateway,
        manuscript: ImportedManuscript,
        metrics: String,
        observations: List<String>,
    ): GeneratedChapter = gateway.generate(
        PromptBundle(
            system = """
                你是琅嬛的“作品 Style DNA 聚合器”。把覆盖全书不同阶段的多批观察合并成一份稳定、可复用的参考作品档案。
                不得复制原作文本，不得输出标志性原句，不得要求模仿具体作者的独特句式。目标是让后续原创小说借鉴高层技术，而不是复刻作品。

                输出 GeneratedChapter JSON：
                - title=作品名；
                - content=350-650字“作品高层档案”，包括大致阅读体验、叙事组织、信息释放、人物塑造、规则呈现、节奏/悬念；
                - summary=180-350字“Style DNA 摘要”；
                - stateChanges 8-18项，subject 只能用 DNA / KEEP / TRANSFORM / AVOID：
                  * DNA：field=POV/RHYTHM/DIALOGUE/INFO/SUSPENSE/CHARACTER/RULE/SCENE/EMOTION/STRUCTURE 等维度，after=抽象规律；
                  * KEEP：可直接借鉴的高层机制；
                  * TRANSFORM：可借鉴但必须原创化改造的机制；
                  * AVOID：标志性角色、专名、独特剧情骨架、原句等不得照搬的内容；
                - evidence 只写“前段分层样本/中段分层样本/后段分层样本/本地统计”等短标签，不写原句；
                - touchedForeshadowingIds=[]。
            """.trimIndent(),
            user = """
                作品：${manuscript.title}
                章节数：${manuscript.chapters.size}

                【全书本地结构统计】
                $metrics

                【跨全书分层 AI 蒸馏观察】
                ${observations.joinToString("\n\n---\n\n").take(20_000)}

                请综合前中后不同阶段的稳定共同规律和明显变化，不还原原文，不做逐章剧情复述。
            """.trimIndent(),
        )
    )

    private fun buildSamples(manuscript: ImportedManuscript): List<SampleChunk> {
        val chapters = manuscript.chapters.filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) return emptyList()
        val desired = desiredSampleCount(chapters.size).coerceAtMost(chapters.size)
        val indices = linkedSetOf<Int>()
        fun add(index: Int) {
            if (index in chapters.indices) indices += index
        }

        add(0)
        add(1)
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

    private fun desiredSampleCount(chapterCount: Int): Int = when {
        chapterCount <= 12 -> chapterCount
        chapterCount <= 60 -> 12
        chapterCount <= 150 -> 18
        chapterCount <= 300 -> 24
        chapterCount <= 600 -> 30
        chapterCount <= 1_000 -> 36
        else -> 42
    }

    private fun sampleCharsFor(chapterCount: Int): Int = when {
        chapterCount > 1_000 -> 2_000
        chapterCount > 600 -> 2_300
        chapterCount > 300 -> 2_700
        chapterCount > 150 -> 3_100
        chapterCount > 60 -> 3_600
        else -> 4_800
    }

    private fun compactChapterSample(chapter: ImportedChapter, maxChars: Int): String {
        val text = chapter.content.trim()
        if (text.length <= maxChars) return text
        val piece = (maxChars / 3).coerceAtLeast(300)
        val middleStart = (text.length / 2 - piece / 2).coerceAtLeast(piece)
        return buildString {
            appendLine(text.take(piece))
            appendLine("\n[…中段省略，仅做结构分层采样…]\n")
            appendLine(text.substring(middleStart, (middleStart + piece).coerceAtMost(text.length)))
            appendLine("\n[…后段采样…]\n")
            append(text.takeLast(piece))
        }.take(maxChars + 100)
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
            appendLine("总章节：${chapters.size}；总文本约：${lengths.sum()} 字符")
            appendLine("全书平均章节长度：${chapterAverage.toInt()} 字符")
            appendLine("全书平均段落长度：${paragraphAverage.toInt()} 字符")
            appendLine("全书含对白标记的段落约占：$dialogueRatio%")
            appendLine("全书45字符以内短段约占：$shortParagraphRatio%")
            appendLine("AI分层阅读章节：$sampledChapters/${chapters.size}；样本按开篇、前中段、中段、中后段、结尾均匀覆盖。")
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
            .setContentTitle("琅嬛 · 参考小说蒸馏")
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
