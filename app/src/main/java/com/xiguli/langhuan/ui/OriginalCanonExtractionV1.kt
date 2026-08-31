package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.domain.BibleCategory
import com.xiguli.langhuan.domain.BibleEntry
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.CharacterState
import com.xiguli.langhuan.domain.TimelineEvent
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class CanonEntityTypeV1 {
    CHARACTER,
    FACTION,
    LOCATION,
    ITEM,
    RULE,
    WORLD,
    STYLE,
}

@Serializable
data class CanonEntityObservationV1(
    val chapterNumber: Int,
    val partIndex: Int,
    val type: CanonEntityTypeV1,
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val evidence: String = "",
)

@Serializable
data class CanonEventObservationV1(
    val chapterNumber: Int,
    val partIndex: Int,
    val storyTime: String = "",
    val location: String = "",
    val participants: List<String> = emptyList(),
    val summary: String,
    val consequences: List<String> = emptyList(),
    val evidence: String = "",
)

@Serializable
data class CanonKnowledgeObservationV1(
    val chapterNumber: Int,
    val partIndex: Int,
    val character: String,
    val fact: String,
    val evidence: String = "",
)

@Serializable
data class CanonRelationObservationV1(
    val chapterNumber: Int,
    val partIndex: Int,
    val from: String,
    val to: String,
    val label: String,
    val value: String = "",
    val evidence: String = "",
)

@Serializable
data class CanonSourceDigestV1(
    val chapterNumber: Int,
    val chapterTitle: String,
    val partIndex: Int,
    val partCount: Int,
    val fingerprint: String,
    val summary: String,
    val entities: List<CanonEntityObservationV1> = emptyList(),
    val events: List<CanonEventObservationV1> = emptyList(),
    val knowledge: List<CanonKnowledgeObservationV1> = emptyList(),
    val relations: List<CanonRelationObservationV1> = emptyList(),
)

@Serializable
data class OriginalCanonArchiveV1(
    val novelId: String,
    val title: String,
    val totalChapters: Int = 0,
    val totalCharacters: Int = 0,
    val digests: List<CanonSourceDigestV1> = emptyList(),
    val updatedAt: Long = 0L,
    val appliedAt: Long = 0L,
)

data class OriginalCanonUiStateV1(
    val novelId: String = "",
    val totalChapters: Int = 0,
    val totalCharacters: Int = 0,
    val totalUnits: Int = 0,
    val processedUnits: Int = 0,
    val entityCount: Int = 0,
    val eventCount: Int = 0,
    val knowledgeCount: Int = 0,
    val relationCount: Int = 0,
    val currentLabel: String = "",
    val busy: Boolean = false,
    val applying: Boolean = false,
    val complete: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
)

private data class CanonSourceUnitV1(
    val chapterNumber: Int,
    val chapterTitle: String,
    val partIndex: Int,
    val partCount: Int,
    val text: String,
    val fingerprint: String,
) {
    val key: String get() = "$chapterNumber:$partIndex"
}

class OriginalCanonArchiveStoreV1(private val application: Application) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun load(novelId: String): OriginalCanonArchiveV1? {
        val file = fileFor(novelId)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(OriginalCanonArchiveV1.serializer(), file.readText())
        }.getOrNull()
    }

    fun save(archive: OriginalCanonArchiveV1) {
        val file = fileFor(archive.novelId)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(OriginalCanonArchiveV1.serializer(), archive))
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun clear(novelId: String) {
        runCatching { fileFor(novelId).delete() }
    }

    private fun fileFor(novelId: String): File = File(
        application.filesDir,
        "original_canon/${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json",
    )
}

class OriginalCanonExtractionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val projects = StoryProjectManager(application)
    private val store = OriginalCanonArchiveStoreV1(application)
    private val _state = MutableStateFlow(OriginalCanonUiStateV1())
    val state: StateFlow<OriginalCanonUiStateV1> = _state.asStateFlow()
    private var stopRequested = false

    fun open(book: ReaderBookUi, chapters: List<ChapterDraft>) {
        val units = buildSourceUnits(chapters)
        val existing = store.load(book.id)
        val valid = retainValidDigests(existing?.digests.orEmpty(), units)
        val archive = OriginalCanonArchiveV1(
            novelId = book.id,
            title = book.title,
            totalChapters = chapters.count { it.content.isNotBlank() },
            totalCharacters = chapters.sumOf { it.content.length },
            digests = valid,
            updatedAt = existing?.updatedAt ?: 0L,
            appliedAt = existing?.appliedAt ?: 0L,
        )
        if (existing != null && valid.size != existing.digests.size) store.save(archive)
        _state.value = archive.toUi(units.size)
    }

    fun start(book: ReaderBookUi, chapters: List<ChapterDraft>) {
        if (_state.value.busy || _state.value.applying) return
        viewModelScope.launch {
            stopRequested = false
            _state.update { it.copy(busy = true, error = null, notice = null, currentLabel = "正在准备全书任务") }
            runCatching {
                val gateway = activeGateway()
                val units = buildSourceUnits(chapters)
                require(units.isNotEmpty()) { "这本书还没有可抽取的正文" }
                val old = store.load(book.id)
                val digests = retainValidDigests(old?.digests.orEmpty(), units).associateBy { "${it.chapterNumber}:${it.partIndex}" }.toMutableMap()
                val pending = units.filter { unit ->
                    val cached = digests[unit.key]
                    cached == null || cached.fingerprint != unit.fingerprint
                }
                if (pending.isEmpty()) {
                    val ready = OriginalCanonArchiveV1(
                        novelId = book.id,
                        title = book.title,
                        totalChapters = chapters.count { it.content.isNotBlank() },
                        totalCharacters = chapters.sumOf { it.content.length },
                        digests = digests.values.sortedWith(compareBy({ it.chapterNumber }, { it.partIndex })),
                        updatedAt = System.currentTimeMillis(),
                        appliedAt = old?.appliedAt ?: 0L,
                    )
                    store.save(ready)
                    _state.value = ready.toUi(units.size).copy(notice = "全书正文没有变化，缓存仍然有效")
                    return@runCatching
                }

                val batches = makeBatches(pending)
                for ((batchIndex, batch) in batches.withIndex()) {
                    if (stopRequested) break
                    val first = batch.first()
                    val last = batch.last()
                    _state.update {
                        it.copy(
                            currentLabel = "抽取第 ${first.chapterNumber} 章${if (first.chapterNumber != last.chapterNumber) " ～ 第 ${last.chapterNumber} 章" else ""} · 批次 ${batchIndex + 1}/${batches.size}",
                        )
                    }
                    val output = gateway.generate(
                        PromptBundle(
                            system = extractionSystemPrompt(),
                            user = buildBatchPrompt(book, batch),
                        )
                    )
                    val parsed = parseBatch(output.content, batch)
                    parsed.forEach { digest -> digests["${digest.chapterNumber}:${digest.partIndex}"] = digest }
                    val archive = OriginalCanonArchiveV1(
                        novelId = book.id,
                        title = book.title,
                        totalChapters = chapters.count { it.content.isNotBlank() },
                        totalCharacters = chapters.sumOf { it.content.length },
                        digests = digests.values.sortedWith(compareBy({ it.chapterNumber }, { it.partIndex })),
                        updatedAt = System.currentTimeMillis(),
                        appliedAt = old?.appliedAt ?: 0L,
                    )
                    store.save(archive)
                    _state.value = archive.toUi(units.size).copy(
                        busy = true,
                        currentLabel = "已缓存 ${archive.digests.size}/${units.size} 个原文片段",
                    )
                }

                val finalArchive = store.load(book.id) ?: error("抽取结果没有写入本地缓存")
                val stopped = stopRequested
                _state.value = finalArchive.toUi(units.size).copy(
                    busy = false,
                    currentLabel = "",
                    notice = if (stopped) "已暂停；下次继续时会从缓存断点接着抽，不会重跑已完成片段" else "全书抽取完成，可写入人物 / 世界 / 时间线设定",
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        currentLabel = "",
                        error = error.message ?: "原著设定抽取失败",
                    )
                }
            }
        }
    }

    fun requestStop() {
        if (_state.value.busy) {
            stopRequested = true
            _state.update { it.copy(currentLabel = "将在当前 AI 批次完成后暂停") }
        }
    }

    fun clearCache(book: ReaderBookUi, chapters: List<ChapterDraft>) {
        if (_state.value.busy || _state.value.applying) return
        store.clear(book.id)
        open(book, chapters)
        _state.update { it.copy(notice = "原著抽取缓存已清空") }
    }

    fun applyToCanon(novelId: String, onApplied: () -> Unit) {
        if (_state.value.busy || _state.value.applying) return
        viewModelScope.launch {
            _state.update { it.copy(applying = true, error = null, notice = null) }
            runCatching {
                val archive = store.load(novelId) ?: error("还没有可写入的原著抽取结果")
                require(archive.digests.isNotEmpty()) { "还没有可写入的原著抽取结果" }
                val loaded = projects.loadStory(novelId) ?: error("找不到当前小说")
                val snapshot = loaded.snapshot
                val chapterSummaries = buildChapterSummaries(archive)
                val clusters = clusterEntities(archive.digests.flatMap { it.entities })
                val extractedBible = buildBibleEntries(novelId, clusters)
                val extractedCharacters = buildCharacters(novelId, snapshot.characters, clusters, archive)
                val extractedTimeline = buildTimeline(novelId, archive)
                val existingManualBible = snapshot.bible.filterNot { it.id.startsWith(EXTRACTED_PREFIX) }
                val summariesOrdered = chapterSummaries.entries.sortedBy { it.key }
                val recent = summariesOrdered.takeLast(36).map { (chapter, summary) -> "第${chapter}章：$summary" }
                val earlier = summariesOrdered.dropLast(36)
                    .joinToString("\n") { (chapter, summary) -> "第${chapter}章：$summary" }
                    .takeLast(20_000)
                val updated = snapshot.copy(
                    bible = existingManualBible + extractedBible,
                    characters = extractedCharacters,
                    recentTimeline = extractedTimeline.takeLast(240),
                    recentSummaries = recent,
                    longTermSummary = if (earlier.isBlank()) snapshot.longTermSummary else earlier,
                )
                projects.saveStructure(updated, loaded.draft)
                val applied = archive.copy(appliedAt = System.currentTimeMillis())
                store.save(applied)
                applied
            }.onSuccess { archive ->
                _state.update {
                    it.copy(
                        applying = false,
                        notice = "已写入 Canon：全书摘要、人物、世界设定与时间线已更新；完整证据索引仍保留在原著库",
                    )
                }
                onApplied()
            }.onFailure { error ->
                _state.update { it.copy(applying = false, error = error.message ?: "写入设定失败") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(notice = null, error = null) }

    private suspend fun activeGateway(): UniversalAiGateway {
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
            ?: error("还没有可用 AI，请先配置模型")
        val config = repository.providerConfig(provider.id) ?: error("AI 配置不可用")
        return UniversalAiGateway(config)
    }
}

@Composable
fun OriginalCanonExtractionDialogV1(
    book: ReaderBookUi,
    chapters: List<ChapterDraft>,
    aiReady: Boolean,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val vm: OriginalCanonExtractionViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    LaunchedEffect(book.id, chapters.size, chapters.sumOf { it.content.length }) {
        vm.open(book, chapters)
    }

    Dialog(
        onDismissRequest = { if (!state.applying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onDismiss, enabled = !state.applying) { Icon(Icons.Rounded.Close, "关闭") }
                    Column(Modifier.weight(1f)) {
                        Text("从原著抽设定", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(book.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { confirmReset = true },
                        enabled = !state.busy && !state.applying,
                    ) { Icon(Icons.Rounded.RestartAlt, "清空缓存重来") }
                }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("真正按全书正文抽取，不做开头 + 随机章节抽样", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "正文会按章节拆成可控片段，逐批读取全部内容；每批结果立刻缓存。中途退出或失败后可以继续，正文没变化的片段不会重复消耗模型。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val fraction = if (state.totalUnits <= 0) 0f else state.processedUnits.toFloat() / state.totalUnits.toFloat()
                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("全书进度", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${state.processedUnits}/${state.totalUnits}", color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(
                                "${state.totalChapters} 章 · ${state.totalCharacters} 字 · 已发现 ${state.entityCount} 个实体 / ${state.eventCount} 条事件 / ${state.relationCount} 条关系 / ${state.knowledgeCount} 条人物知情事实",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.currentLabel.isNotBlank()) {
                                Text(state.currentLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("抽取内容", fontWeight = FontWeight.Bold)
                            Text("• 每章 / 每片段摘要，形成完整章节索引")
                            Text("• 人物、势力、地点、物品、规则、世界观、文风")
                            Text("• 事件时间线、参与者、结果与原文章节证据")
                            Text("• 人物在某章已经明确知道的事实，以及角色关系变化")
                            Text(
                                "完整索引单独保存在原著库；写入 Canon 时只把适合长期上下文的压缩结果写入小说状态，避免每轮 AI 把整本书全部塞进 Prompt。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.error?.let {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text(it, Modifier.fillMaxWidth().padding(13.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                    state.notice?.let {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(it, Modifier.fillMaxWidth().padding(13.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }

                    if (state.busy) {
                        OutlinedButton(
                            onClick = vm::requestStop,
                            Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Rounded.Stop, null)
                            Spacer(Modifier.width(7.dp))
                            Text("当前批次完成后暂停")
                        }
                    } else {
                        Button(
                            onClick = { vm.start(book, chapters) },
                            enabled = aiReady && !state.applying && state.totalUnits > 0,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(if (state.processedUnits > 0) Icons.Rounded.PlayArrow else Icons.Rounded.AutoAwesome, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (state.complete) "检查正文变化并增量更新" else if (state.processedUnits > 0) "继续全书抽取" else "开始全书抽取")
                        }
                    }

                    FilledTonalButton(
                        onClick = { vm.applyToCanon(book.id, onApplied) },
                        enabled = !state.busy && !state.applying && state.processedUnits > 0,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        if (state.applying) {
                            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在整理并写入 Canon")
                        } else {
                            Icon(Icons.Rounded.DoneAll, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (state.complete) "写入人物 / 世界 / 时间线" else "把已完成部分写入 Canon")
                        }
                    }

                    if (!aiReady) {
                        Text("需要先配置可用 AI 服务才能开始抽取。已经完成的本地缓存仍可查看或写入。", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("清空原著抽取缓存？") },
            text = { Text("只删除 AI 抽取索引，不删除小说正文。之后重新开始会再次读取全书并产生模型消耗。") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    vm.clearCache(book, chapters)
                }) { Text("清空") }
            },
            dismissButton = { TextButton({ confirmReset = false }) { Text("取消") } },
        )
    }
}

private fun OriginalCanonArchiveV1.toUi(totalUnits: Int): OriginalCanonUiStateV1 {
    val processed = digests.size.coerceAtMost(totalUnits)
    return OriginalCanonUiStateV1(
        novelId = novelId,
        totalChapters = totalChapters,
        totalCharacters = totalCharacters,
        totalUnits = totalUnits,
        processedUnits = processed,
        entityCount = digests.sumOf { it.entities.size },
        eventCount = digests.sumOf { it.events.size },
        knowledgeCount = digests.sumOf { it.knowledge.size },
        relationCount = digests.sumOf { it.relations.size },
        complete = totalUnits > 0 && processed == totalUnits,
    )
}

private fun buildSourceUnits(chapters: List<ChapterDraft>): List<CanonSourceUnitV1> {
    return chapters.sortedBy { it.chapterNumber }.flatMap { chapter ->
        val text = chapter.content.trim()
        if (text.isBlank()) return@flatMap emptyList()
        val parts = text.chunked(SOURCE_PART_CHARS)
        parts.mapIndexed { index, part ->
            CanonSourceUnitV1(
                chapterNumber = chapter.chapterNumber,
                chapterTitle = chapter.title.ifBlank { "第${chapter.chapterNumber}章" },
                partIndex = index + 1,
                partCount = parts.size,
                text = part,
                fingerprint = sha256("${chapter.chapterNumber}|${index + 1}|$part"),
            )
        }
    }
}

private fun retainValidDigests(
    digests: List<CanonSourceDigestV1>,
    units: List<CanonSourceUnitV1>,
): List<CanonSourceDigestV1> {
    val unitMap = units.associateBy { it.key }
    return digests.filter { digest ->
        val unit = unitMap["${digest.chapterNumber}:${digest.partIndex}"]
        unit != null && unit.fingerprint == digest.fingerprint
    }
}

private fun makeBatches(units: List<CanonSourceUnitV1>): List<List<CanonSourceUnitV1>> {
    val result = mutableListOf<MutableList<CanonSourceUnitV1>>()
    var current = mutableListOf<CanonSourceUnitV1>()
    var chars = 0
    fun flush() {
        if (current.isNotEmpty()) result += current
        current = mutableListOf()
        chars = 0
    }
    units.forEach { unit ->
        if (current.isNotEmpty() && (chars + unit.text.length > BATCH_CHAR_BUDGET || current.size >= MAX_UNITS_PER_BATCH)) flush()
        current += unit
        chars += unit.text.length
    }
    flush()
    return result
}

private fun extractionSystemPrompt(): String = """
    你是“琅嬛原著 Canon 抽取器”。任务不是续写，而是把用户合法导入的小说正文整理成可检索、可校验、可追溯的结构化事实。
    只依据本批提供的原文，不得用你训练数据里对同名作品的记忆补齐，不得猜测未出现内容。
    每个事实必须绑定章号和片段号；evidence 只写不超过 70 字的证据提示/短释义，不要大段复述原文。
    人物“知道某事实”只有原文明示、亲眼见到、亲耳听到或本段明确推断成立时才记 KNOWLEDGE；旁白知道不等于角色知道。
    关系只在原文能明确支持时记录，不要把同场出现自动当成朋友/敌人。

    必须输出 GeneratedChapter JSON。title 固定 canon_extract；summary 写本批一句话概况；stateChanges=[]；touchedForeshadowingIds=[]。
    content 必须是纯文本记录，每条一行，字段内部严禁出现竖线 |，需要分隔时改用中文顿号/斜杠。只允许以下记录：
    UNIT|章号|片段号|本片段摘要
    ENTITY|章号|片段号|CHARACTER/FACTION/LOCATION/ITEM/RULE/WORLD/STYLE|标准名|别名用逗号分隔|客观描述|证据提示
    EVENT|章号|片段号|故事内时间|地点|参与者逗号分隔|事件摘要|后果逗号分隔|证据提示
    KNOWLEDGE|章号|片段号|人物名|该人物此时已经知道的事实|证据提示
    RELATION|章号|片段号|人物A|人物B|关系标签|关系状态/强度|证据提示

    每个输入片段必须恰好有一条 UNIT。没有实体/事件时也必须保留 UNIT。不要 markdown，不要代码块，不要额外解释。
""".trimIndent()

private fun buildBatchPrompt(book: ReaderBookUi, units: List<CanonSourceUnitV1>): String = buildString {
    appendLine("作品：${book.title}")
    appendLine("类型：${book.genre}")
    appendLine("下面是本次必须逐段抽取的原著正文。")
    units.forEach { unit ->
        appendLine()
        appendLine("===== CHAPTER ${unit.chapterNumber} PART ${unit.partIndex}/${unit.partCount} · ${unit.chapterTitle} =====")
        appendLine(unit.text)
        appendLine("===== END CHAPTER ${unit.chapterNumber} PART ${unit.partIndex}/${unit.partCount} =====")
    }
}

private fun parseBatch(content: String, units: List<CanonSourceUnitV1>): List<CanonSourceDigestV1> {
    val summaries = mutableMapOf<String, String>()
    val entities = mutableListOf<CanonEntityObservationV1>()
    val events = mutableListOf<CanonEventObservationV1>()
    val knowledge = mutableListOf<CanonKnowledgeObservationV1>()
    val relations = mutableListOf<CanonRelationObservationV1>()

    content.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
        when {
            line.startsWith("UNIT|") -> {
                val p = line.split('|', limit = 4)
                if (p.size == 4) summaries["${p[1].toIntOrNull()}:${p[2].toIntOrNull()}"] = cleanField(p[3])
            }
            line.startsWith("ENTITY|") -> {
                val p = line.split('|', limit = 9)
                if (p.size == 9) {
                    val chapter = p[1].toIntOrNull() ?: return@forEach
                    val part = p[2].toIntOrNull() ?: return@forEach
                    val type = runCatching { CanonEntityTypeV1.valueOf(p[3].trim().uppercase()) }.getOrNull() ?: return@forEach
                    val name = cleanField(p[4])
                    if (name.isBlank()) return@forEach
                    entities += CanonEntityObservationV1(
                        chapterNumber = chapter,
                        partIndex = part,
                        type = type,
                        name = name,
                        aliases = splitList(p[5]),
                        description = cleanField(p[6]),
                        evidence = cleanField(p[7] + if (p[8].isNotBlank()) " / ${p[8]}" else "").take(180),
                    )
                }
            }
            line.startsWith("EVENT|") -> {
                val p = line.split('|', limit = 9)
                if (p.size == 9) {
                    val chapter = p[1].toIntOrNull() ?: return@forEach
                    val part = p[2].toIntOrNull() ?: return@forEach
                    events += CanonEventObservationV1(
                        chapterNumber = chapter,
                        partIndex = part,
                        storyTime = cleanField(p[3]),
                        location = cleanField(p[4]),
                        participants = splitList(p[5]),
                        summary = cleanField(p[6]),
                        consequences = splitList(p[7]),
                        evidence = cleanField(p[8]).take(180),
                    )
                }
            }
            line.startsWith("KNOWLEDGE|") -> {
                val p = line.split('|', limit = 6)
                if (p.size == 6) {
                    val chapter = p[1].toIntOrNull() ?: return@forEach
                    val part = p[2].toIntOrNull() ?: return@forEach
                    val character = cleanField(p[3])
                    val fact = cleanField(p[4])
                    if (character.isNotBlank() && fact.isNotBlank()) {
                        knowledge += CanonKnowledgeObservationV1(chapter, part, character, fact, cleanField(p[5]).take(180))
                    }
                }
            }
            line.startsWith("RELATION|") -> {
                val p = line.split('|', limit = 8)
                if (p.size == 8) {
                    val chapter = p[1].toIntOrNull() ?: return@forEach
                    val part = p[2].toIntOrNull() ?: return@forEach
                    val from = cleanField(p[3])
                    val to = cleanField(p[4])
                    val label = cleanField(p[5])
                    if (from.isNotBlank() && to.isNotBlank() && label.isNotBlank()) {
                        relations += CanonRelationObservationV1(chapter, part, from, to, label, cleanField(p[6]), cleanField(p[7]).take(180))
                    }
                }
            }
        }
    }

    return units.map { unit ->
        val key = unit.key
        val summary = summaries[key]?.takeIf { it.isNotBlank() }
            ?: error("AI 返回缺少第 ${unit.chapterNumber} 章片段 ${unit.partIndex} 的 UNIT 摘要，本批未写入缓存，可直接重试")
        CanonSourceDigestV1(
            chapterNumber = unit.chapterNumber,
            chapterTitle = unit.chapterTitle,
            partIndex = unit.partIndex,
            partCount = unit.partCount,
            fingerprint = unit.fingerprint,
            summary = summary,
            entities = entities.filter { it.chapterNumber == unit.chapterNumber && it.partIndex == unit.partIndex },
            events = events.filter { it.chapterNumber == unit.chapterNumber && it.partIndex == unit.partIndex },
            knowledge = knowledge.filter { it.chapterNumber == unit.chapterNumber && it.partIndex == unit.partIndex },
            relations = relations.filter { it.chapterNumber == unit.chapterNumber && it.partIndex == unit.partIndex },
        )
    }
}

private data class EntityClusterV1(
    val type: CanonEntityTypeV1,
    var name: String,
    val names: MutableSet<String>,
    val observations: MutableList<CanonEntityObservationV1>,
)

private fun clusterEntities(observations: List<CanonEntityObservationV1>): List<EntityClusterV1> {
    val clusters = mutableListOf<EntityClusterV1>()
    observations.sortedWith(compareBy({ it.chapterNumber }, { it.partIndex })).forEach { obs ->
        val keys = (listOf(obs.name) + obs.aliases).map(::normalizeName).filter { it.isNotBlank() }.toSet()
        val found = clusters.firstOrNull { cluster ->
            cluster.type == obs.type && cluster.names.map(::normalizeName).any { it in keys }
        }
        if (found == null) {
            clusters += EntityClusterV1(obs.type, obs.name, (listOf(obs.name) + obs.aliases).toMutableSet(), mutableListOf(obs))
        } else {
            found.observations += obs
            found.names += obs.name
            found.names += obs.aliases
            if (obs.name.length < found.name.length && obs.name.isNotBlank()) found.name = obs.name
        }
    }
    return clusters.sortedByDescending { it.observations.size }
}

private fun buildBibleEntries(novelId: String, clusters: List<EntityClusterV1>): List<BibleEntry> {
    return clusters.filter { it.type != CanonEntityTypeV1.CHARACTER }
        .take(700)
        .map { cluster ->
            val category = when (cluster.type) {
                CanonEntityTypeV1.FACTION -> BibleCategory.FACTION
                CanonEntityTypeV1.LOCATION -> BibleCategory.LOCATION
                CanonEntityTypeV1.ITEM -> BibleCategory.ITEM
                CanonEntityTypeV1.RULE -> BibleCategory.RULE
                CanonEntityTypeV1.WORLD -> BibleCategory.WORLD
                CanonEntityTypeV1.STYLE -> BibleCategory.STYLE
                CanonEntityTypeV1.CHARACTER -> BibleCategory.CHARACTER
            }
            val observations = cluster.observations.sortedWith(compareBy({ it.chapterNumber }, { it.partIndex }))
            val latestEvidence = observations.takeLast(14).joinToString("\n") { obs ->
                buildString {
                    append("第${obs.chapterNumber}章：").append(obs.description)
                    if (obs.evidence.isNotBlank()) append("；证据提示：").append(obs.evidence)
                }
            }
            BibleEntry(
                id = "$EXTRACTED_PREFIX${cluster.type.name.lowercase()}:${stableId(cluster.name)}",
                novelId = novelId,
                category = category,
                name = cluster.name,
                content = buildString {
                    append(latestEvidence)
                    if (observations.size > 14) append("\n（完整索引共 ${observations.size} 处原文章节记录，保存在原著库）")
                },
                aliases = cluster.names.filterNot { normalizeName(it) == normalizeName(cluster.name) }.distinct().take(20),
                locked = true,
            )
        }
}

private fun buildCharacters(
    novelId: String,
    existing: List<CharacterState>,
    clusters: List<EntityClusterV1>,
    archive: OriginalCanonArchiveV1,
): List<CharacterState> {
    val result = existing.toMutableList()
    val characterClusters = clusters.filter { it.type == CanonEntityTypeV1.CHARACTER }.take(240)
    val allEvents = archive.digests.flatMap { it.events }
    val allKnowledge = archive.digests.flatMap { it.knowledge }
    val allRelations = archive.digests.flatMap { it.relations }

    characterClusters.forEach { cluster ->
        val names = cluster.names.map(::normalizeName).toSet() + normalizeName(cluster.name)
        val index = result.indexOfFirst { normalizeName(it.name) in names }
        val old = result.getOrNull(index)
        val lastChapter = cluster.observations.maxOfOrNull { it.chapterNumber } ?: 1
        val lastLocation = allEvents
            .filter { event -> event.participants.any { normalizeName(it) in names } && event.location.isNotBlank() }
            .maxWithOrNull(compareBy<CanonEventObservationV1>({ it.chapterNumber }, { it.partIndex }))
            ?.location.orEmpty()
        val known = allKnowledge
            .filter { normalizeName(it.character) in names }
            .sortedBy { it.chapterNumber }
            .map { it.fact }
            .distinct()
            .takeLast(80)
        val relationNotes = allRelations
            .filter { normalizeName(it.from) in names }
            .groupBy { it.to }
            .mapValues { (_, values) ->
                values.maxWithOrNull(compareBy<CanonRelationObservationV1>({ it.chapterNumber }, { it.partIndex }))
                    ?.let { "${it.label}${if (it.value.isNotBlank()) "：${it.value}" else ""}" }.orEmpty()
            }
            .filterValues { it.isNotBlank() }
        val description = cluster.observations.takeLast(8).joinToString("；") { it.description }.take(1200)

        val next = if (old != null) {
            old.copy(
                location = old.location.ifBlank { lastLocation },
                goal = old.goal.ifBlank { description },
                knownSecrets = (old.knownSecrets + known).distinct().takeLast(120),
                relationshipNotes = old.relationshipNotes + relationNotes,
                lastUpdatedChapter = max(old.lastUpdatedChapter, lastChapter),
            )
        } else {
            CharacterState(
                id = "$EXTRACTED_PREFIX" + "character:${stableId(cluster.name)}",
                novelId = novelId,
                name = cluster.name,
                personality = emptyList(),
                location = lastLocation,
                physicalState = "",
                emotionalState = "",
                goal = description,
                knownSecrets = known,
                possessions = emptyList(),
                relationshipNotes = relationNotes,
                lastUpdatedChapter = lastChapter,
            )
        }
        if (index >= 0) result[index] = next else result += next
    }
    return result
}

private fun buildTimeline(novelId: String, archive: OriginalCanonArchiveV1): List<TimelineEvent> {
    return archive.digests.flatMap { it.events }
        .sortedWith(compareBy({ it.chapterNumber }, { it.partIndex }))
        .mapIndexed { index, event ->
            TimelineEvent(
                id = "$EXTRACTED_PREFIX" + "event:${event.chapterNumber}:${event.partIndex}:${index}",
                novelId = novelId,
                chapter = event.chapterNumber,
                storyTime = event.storyTime,
                location = event.location,
                participants = event.participants,
                summary = event.summary + if (event.evidence.isNotBlank()) "【证据提示：${event.evidence}】" else "",
                consequences = event.consequences,
                orderInChapter = event.partIndex,
            )
        }
}

private fun buildChapterSummaries(archive: OriginalCanonArchiveV1): Map<Int, String> = archive.digests
    .groupBy { it.chapterNumber }
    .mapValues { (_, parts) ->
        parts.sortedBy { it.partIndex }
            .joinToString(" ") { it.summary }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(2_400)
    }

private fun cleanField(value: String): String = value
    .replace('|', '／')
    .replace(Regex("\\s+"), " ")
    .trim()

private fun splitList(value: String): List<String> = value
    .split(',', '，', '、', ';', '；')
    .map(::cleanField)
    .filter { it.isNotBlank() && it != "无" && it != "未知" }
    .distinct()

private fun normalizeName(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s·•._—-]"), "")
    .trim()

private fun stableId(value: String): String = sha256(value).take(16)

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private const val SOURCE_PART_CHARS = 7_800
private const val BATCH_CHAR_BUDGET = 24_000
private const val MAX_UNITS_PER_BATCH = 5
private const val EXTRACTED_PREFIX = "original-canon:"
