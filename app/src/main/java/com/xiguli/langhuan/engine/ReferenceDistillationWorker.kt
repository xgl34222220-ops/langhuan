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
import kotlinx.coroutines.flow.first

object ReferenceDistillationJobs {
    const val TAG = "reference-novel-distillation"
    private const val KEY_PATH = "path"
    private const val KEY_NAME = "name"

    fun enqueue(context: Context, sourceFile: File, displayName: String): String {
        val fingerprint = sha256("${sourceFile.absolutePath}|${sourceFile.length()}|${sourceFile.lastModified()}").take(16)
        val request = OneTimeWorkRequestBuilder<ReferenceDistillationWorker>()
            .setInputData(workDataOf(KEY_PATH to sourceFile.absolutePath, KEY_NAME to displayName))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(TAG)
            .addTag("reference:$fingerprint")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reference-distill-$fingerprint",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id.toString()
    }

    internal fun path(input: Data): String = input.getString(KEY_PATH).orEmpty()
    internal fun name(input: Data): String = input.getString(KEY_NAME).orEmpty()

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

    override suspend fun doWork(): Result {
        val path = ReferenceDistillationJobs.path(inputData)
        val fileName = ReferenceDistillationJobs.name(inputData).ifBlank { File(path).name }
        val source = File(path)
        val initialTitle = fileName.substringBeforeLast('.').ifBlank { "参考小说" }
        if (!source.exists() || source.length() == 0L) {
            return Result.failure(workDataOf("error" to "参考小说文件不存在或为空", "title" to initialTitle))
        }

        setForeground(foreground("正在读取《$initialTitle》", 2))
        setProgress(progressData(stage = "parse", progress = 2, title = initialTitle))

        return runCatching {
            val manuscript = StoryExchange.import(fileName, source.readBytes())
            require(manuscript.chapters.isNotEmpty()) { "没有识别到可蒸馏的章节" }

            val providers = repository.observeProviders().first()
            val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                ?: error("没有可用 AI 服务，请先在琅嬛添加 Key / 模型")
            val config = repository.providerConfig(provider.id)
                ?: error("当前 AI 服务配置无法读取")
            val gateway: AiGateway = UniversalAiGateway(config)
            val providerLabel = provider.name.ifBlank { provider.protocol.label }
            val modelLabel = provider.model.ifBlank { config.model }

            setProgress(
                progressData(
                    stage = "prepare",
                    progress = 6,
                    title = manuscript.title,
                    provider = providerLabel,
                    model = modelLabel,
                )
            )
            setForeground(foreground("《${manuscript.title}》已解析，准备 AI 蒸馏", 6))

            val samples = buildSamples(manuscript)
            val localMetrics = localMetrics(manuscript)
            val observations = mutableListOf<String>()
            val batches = samples.chunked(2).take(4)

            batches.forEachIndexed { index, batch ->
                val progress = 10 + ((index + 1) * 55 / batches.size.coerceAtLeast(1))
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
                setForeground(foreground("正在蒸馏《${manuscript.title}》 · ${index + 1}/${batches.size}", progress))
                val observation = distillBatch(gateway, manuscript.title, batch, index + 1, batches.size)
                observations += observation
            }

            setProgress(
                progressData(
                    stage = "aggregate",
                    progress = 78,
                    title = manuscript.title,
                    provider = providerLabel,
                    model = modelLabel,
                )
            )
            setForeground(foreground("正在聚合《${manuscript.title}》风格 DNA", 78))
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
                                query = "本地导入蒸馏 · ${manuscript.chapters.size} 章 · 不保存原文",
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
                                engine = "Local import + AI distillation",
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
                )
            )
            setForeground(foreground("《${manuscript.title}》蒸馏完成，已加入长期研究档案", 100))
            runCatching { source.delete() }
            Result.success(
                workDataOf(
                    "title" to manuscript.title,
                    "chapters" to manuscript.chapters.size,
                    "samples" to samples.size,
                    "provider" to providerLabel,
                    "model" to modelLabel,
                    "reportId" to reportId,
                )
            )
        }.getOrElse { error ->
            val message = error.message ?: "参考小说蒸馏失败"
            if (runAttemptCount < 2 && isRetryable(message)) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to message.take(500), "title" to initialTitle))
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
            "【样本：${sample.label}】\n${sample.text}"
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
                    输出 GeneratedChapter JSON：title="DISTILL_BATCH"；content=350-700字高层观察；summary=100-220字本批最稳定规律；stateChanges可用 subject="DNA"、field=维度、after=抽象规律、evidence=极短证据描述（不要引用原句）；touchedForeshadowingIds=[]。
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
            appendLine(output.content.take(1800))
            if (output.summary.isNotBlank()) appendLine("稳定规律：${output.summary.take(600)}")
            output.stateChanges.take(12).forEach { change ->
                appendLine("${change.field}: ${change.after.ifBlank { change.before }.take(360)}")
            }
        }.take(4200)
    }

    private suspend fun aggregate(
        gateway: AiGateway,
        manuscript: ImportedManuscript,
        metrics: String,
        observations: List<String>,
    ): GeneratedChapter = gateway.generate(
        PromptBundle(
            system = """
                你是琅嬛的“作品 Style DNA 聚合器”。把多批局部观察合并成一份稳定、可复用的参考作品档案。
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
                - evidence 只写“开篇样本/中段样本/结尾样本/本地统计”等短标签，不写原句；
                - touchedForeshadowingIds=[]。
            """.trimIndent(),
            user = """
                作品：${manuscript.title}
                章节数：${manuscript.chapters.size}

                【本地结构统计】
                $metrics

                【分批蒸馏观察】
                ${observations.joinToString("\n\n---\n\n").take(13_000)}

                请只聚合可迁移的高层写作技术，不还原原文，不做逐章剧情复述。
            """.trimIndent(),
        )
    )

    private fun buildSamples(manuscript: ImportedManuscript): List<SampleChunk> {
        val chapters = manuscript.chapters.filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) return emptyList()
        val indices = linkedSetOf<Int>()
        fun add(index: Int) { indices += index.coerceIn(0, chapters.lastIndex) }
        add(0); add(1)
        add(chapters.size / 3)
        add(chapters.size / 2)
        add(chapters.size * 2 / 3)
        add(chapters.lastIndex - 1); add(chapters.lastIndex)
        return indices.sorted().take(7).map { index ->
            val chapter = chapters[index]
            SampleChunk(
                label = "第${index + 1}/${chapters.size}章 ${chapter.title}",
                text = compactChapterSample(chapter),
            )
        }
    }

    private fun compactChapterSample(chapter: ImportedChapter): String {
        val text = chapter.content.trim()
        if (text.length <= SAMPLE_CHARS) return text
        val piece = SAMPLE_CHARS / 3
        val middleStart = (text.length / 2 - piece / 2).coerceAtLeast(piece)
        return buildString {
            appendLine(text.take(piece))
            appendLine("\n[…中间省略，仅用于结构采样…]\n")
            appendLine(text.substring(middleStart, (middleStart + piece).coerceAtMost(text.length)))
            appendLine("\n[…后段采样…]\n")
            append(text.takeLast(piece))
        }
    }

    private fun localMetrics(manuscript: ImportedManuscript): String {
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
            appendLine("平均章节长度：${chapterAverage.toInt()} 字符")
            appendLine("平均段落长度：${paragraphAverage.toInt()} 字符")
            appendLine("含对白标记的段落约占：$dialogueRatio%")
            appendLine("45字符以内短段约占：$shortParagraphRatio%")
            appendLine("总章节：${chapters.size}；总文本约：${lengths.sum()} 字符")
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

    private data class SampleChunk(val label: String, val text: String)

    private companion object {
        const val CHANNEL_ID = "langhuan_long_tasks"
        const val NOTIFICATION_ID = 43021
        const val SAMPLE_CHARS = 5_400
    }
}
