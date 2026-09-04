package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.theme.LanghuanShape
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * “进入故事”原著角色身份绑定。
 *
 * 这里直接读取原著抽取库，但在解析的第一步就按 anchorChapter 截断，
 * 所以角色卡、人物已知事实和关系都不可能从未来章节构建。
 */
@Serializable
private data class StoryRoleArchiveV1(
    val novelId: String = "",
    val digests: List<StoryRoleDigestV1> = emptyList(),
)

@Serializable
private data class StoryRoleDigestV1(
    val chapterNumber: Int = 0,
    val entities: List<StoryRoleEntityV1> = emptyList(),
    val knowledge: List<StoryRoleKnowledgeV1> = emptyList(),
    val relations: List<StoryRoleRelationSourceV1> = emptyList(),
)

@Serializable
private data class StoryRoleEntityV1(
    val type: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val description: String = "",
)

@Serializable
private data class StoryRoleKnowledgeV1(
    val character: String = "",
    val fact: String = "",
    val evidence: String = "",
)

@Serializable
private data class StoryRoleRelationSourceV1(
    val from: String = "",
    val to: String = "",
    val label: String = "",
    val value: String = "",
    val evidence: String = "",
)

data class StoryCanonRoleFactV1(
    val chapter: Int,
    val fact: String,
    val evidence: String = "",
)

data class StoryCanonRoleRelationV1(
    val chapter: Int,
    val from: String,
    val to: String,
    val label: String,
    val value: String = "",
    val evidence: String = "",
)

data class StoryCanonRoleCandidateV1(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val knownFacts: List<StoryCanonRoleFactV1> = emptyList(),
    val relationships: List<StoryCanonRoleRelationV1> = emptyList(),
    val firstChapter: Int = 1,
    val lastChapter: Int = 1,
    val mentions: Int = 1,
)

private data class MutableRoleClusterV1(
    var name: String,
    val names: MutableSet<String>,
    val descriptions: MutableList<Pair<Int, String>>,
    var firstChapter: Int,
    var lastChapter: Int,
    var mentions: Int,
)

private val StoryRoleJsonV1 = Json { ignoreUnknownKeys = true }

/** 纯函数，供 JVM 单测直接验证“未来章节永不进入角色卡”。 */
internal fun parseStoryCanonRoleCandidatesV1(rawJson: String, anchorChapter: Int): List<StoryCanonRoleCandidateV1> {
    if (rawJson.isBlank() || anchorChapter <= 0) return emptyList()
    val archive = runCatching {
        StoryRoleJsonV1.decodeFromString(StoryRoleArchiveV1.serializer(), rawJson)
    }.getOrElse { return emptyList() }
    val visible = archive.digests
        .asSequence()
        .filter { it.chapterNumber in 1..anchorChapter }
        .sortedBy { it.chapterNumber }
        .toList()
    if (visible.isEmpty()) return emptyList()

    val clusters = mutableListOf<MutableRoleClusterV1>()
    visible.forEach { digest ->
        digest.entities
            .filter { it.type.equals("CHARACTER", ignoreCase = true) && it.name.isNotBlank() }
            .forEach { entity ->
                val sourceNames = (listOf(entity.name) + entity.aliases)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val normalized = sourceNames.map(::normalizeStoryRoleNameV1).filter { it.isNotBlank() }.toSet()
                val found = clusters.firstOrNull { cluster ->
                    cluster.names.any { normalizeStoryRoleNameV1(it) in normalized }
                }
                if (found == null) {
                    clusters += MutableRoleClusterV1(
                        name = entity.name.trim(),
                        names = sourceNames.toMutableSet(),
                        descriptions = mutableListOf(digest.chapterNumber to entity.description.trim()),
                        firstChapter = digest.chapterNumber,
                        lastChapter = digest.chapterNumber,
                        mentions = 1,
                    )
                } else {
                    found.names += sourceNames
                    if (entity.description.isNotBlank()) found.descriptions += digest.chapterNumber to entity.description.trim()
                    found.firstChapter = minOf(found.firstChapter, digest.chapterNumber)
                    found.lastChapter = maxOf(found.lastChapter, digest.chapterNumber)
                    found.mentions += 1
                    if (entity.name.isNotBlank() && entity.name.length < found.name.length) found.name = entity.name.trim()
                }
            }
    }

    visible.flatMap { digest -> digest.knowledge.map { digest.chapterNumber to it } }.forEach { (chapter, item) ->
        if (item.character.isBlank()) return@forEach
        val key = normalizeStoryRoleNameV1(item.character)
        if (clusters.none { cluster -> cluster.names.any { normalizeStoryRoleNameV1(it) == key } }) {
            clusters += MutableRoleClusterV1(
                name = item.character.trim(),
                names = mutableSetOf(item.character.trim()),
                descriptions = mutableListOf(),
                firstChapter = chapter,
                lastChapter = chapter,
                mentions = 1,
            )
        }
    }

    return clusters.map { cluster ->
        val keys = cluster.names.map(::normalizeStoryRoleNameV1).filter { it.isNotBlank() }.toSet()
        val facts = visible.flatMap { digest ->
            digest.knowledge
                .filter { normalizeStoryRoleNameV1(it.character) in keys }
                .map { item -> StoryCanonRoleFactV1(digest.chapterNumber, item.fact.trim(), item.evidence.trim()) }
        }.filter { it.fact.isNotBlank() }
            .distinctBy { normalizeStoryRoleFactV1(it.fact) }
            .sortedBy { it.chapter }

        val relations = visible.flatMap { digest ->
            digest.relations.filter { relation ->
                normalizeStoryRoleNameV1(relation.from) in keys || normalizeStoryRoleNameV1(relation.to) in keys
            }.map { relation ->
                StoryCanonRoleRelationV1(
                    chapter = digest.chapterNumber,
                    from = relation.from.trim(),
                    to = relation.to.trim(),
                    label = relation.label.trim(),
                    value = relation.value.trim(),
                    evidence = relation.evidence.trim(),
                )
            }
        }.filter { it.from.isNotBlank() && it.to.isNotBlank() && it.label.isNotBlank() }
            .distinctBy {
                listOf(
                    normalizeStoryRoleNameV1(it.from),
                    normalizeStoryRoleNameV1(it.to),
                    normalizeStoryRoleFactV1(it.label),
                    normalizeStoryRoleFactV1(it.value),
                ).joinToString("|")
            }
            .sortedBy { it.chapter }

        val description = cluster.descriptions
            .filter { it.second.isNotBlank() }
            .sortedBy { it.first }
            .takeLast(6)
            .map { it.second }
            .distinct()
            .joinToString("；")
            .take(1_400)

        StoryCanonRoleCandidateV1(
            name = cluster.name,
            aliases = cluster.names.filterNot { normalizeStoryRoleNameV1(it) == normalizeStoryRoleNameV1(cluster.name) }.distinct().take(16),
            description = description,
            knownFacts = facts,
            relationships = relations,
            firstChapter = cluster.firstChapter,
            lastChapter = maxOf(cluster.lastChapter, facts.maxOfOrNull { it.chapter } ?: cluster.lastChapter),
            mentions = cluster.mentions,
        )
    }.sortedWith(
        compareByDescending<StoryCanonRoleCandidateV1> { it.mentions + it.knownFacts.size }
            .thenByDescending { it.lastChapter }
            .thenBy { it.name }
    )
}

private fun normalizeStoryRoleNameV1(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s·•._—-]"), "")
    .trim()

private fun normalizeStoryRoleFactV1(value: String): String = value
    .lowercase()
    .replace(Regex("\\s+"), "")
    .trim()

data class StoryRoleCatalogUiStateV1(
    val novelId: String = "",
    val anchorChapter: Int = 0,
    val loading: Boolean = false,
    val candidates: List<StoryCanonRoleCandidateV1> = emptyList(),
    val error: String? = null,
)

class StoryRoleCatalogViewModelV1(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(StoryRoleCatalogUiStateV1())
    val state: StateFlow<StoryRoleCatalogUiStateV1> = _state.asStateFlow()
    private var lastKey = ""

    fun load(novelId: String, anchorChapter: Int) {
        if (novelId.isBlank() || anchorChapter <= 0) return
        val key = "$novelId|$anchorChapter"
        if (key == lastKey && !_state.value.loading) return
        lastKey = key
        viewModelScope.launch {
            _state.value = StoryRoleCatalogUiStateV1(novelId, anchorChapter, loading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = roleArchiveFileV1(getApplication(), novelId)
                    if (!file.isFile) emptyList()
                    else parseStoryCanonRoleCandidatesV1(file.readText(), anchorChapter)
                }
            }.onSuccess { candidates ->
                _state.value = StoryRoleCatalogUiStateV1(
                    novelId = novelId,
                    anchorChapter = anchorChapter,
                    candidates = candidates,
                )
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "读取原著角色失败") }
            }
        }
    }
}

private fun roleArchiveFileV1(application: Application, novelId: String): File = File(
    application.filesDir,
    "original_canon/${novelId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json",
)

private enum class StoryRoleModeV1(val label: String) {
    CANON("原著角色"), SELF("作为自己"), CUSTOM("原创角色")
}

@Composable
fun StoryPlayPanelV6(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val catalogVm: StoryRoleCatalogViewModelV1 = viewModel()
    val catalog by catalogVm.state.collectAsStateWithLifecycle()
    val active = storyState.active
    val anchorChapter = active?.anchorChapter
        ?: libraryState.readingChapter?.chapterNumber
        ?: book.currentChapter.coerceAtLeast(1)
    var showBinding by remember(book.id, active?.id) { mutableStateOf(false) }

    LaunchedEffect(book.id, anchorChapter) { catalogVm.load(book.id, anchorChapter) }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV2(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
            onAdopted = onAdopted,
        )

        ExtendedFloatingActionButton(
            onClick = { showBinding = true },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 18.dp, bottom = 86.dp),
            icon = { Icon(Icons.Rounded.Badge, null) },
            text = {
                Text(
                    active?.playerProfile?.name?.takeIf { it.isNotBlank() } ?: "选择身份",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }

    if (showBinding) {
        StoryRoleBindingDialogV1(
            anchorChapter = anchorChapter,
            current = active?.playerProfile ?: StoryPlayerProfile(),
            loading = catalog.loading,
            candidates = catalog.candidates,
            error = catalog.error,
            onDismiss = { showBinding = false },
            onBindCanon = { candidate ->
                val visibleFacts = candidate.knownFacts.takeLast(MAX_PROFILE_FACTS_V1)
                val profile = StoryPlayerProfile(
                    name = candidate.name,
                    identity = "原著角色 · 截至第${anchorChapter}章",
                    traits = candidate.description.take(1_200),
                    knownFacts = visibleFacts.joinToString("；") { "第${it.chapter}章：${it.fact}" }.take(5_500),
                    forbiddenKnowledge = "严格禁止知道第${anchorChapter + 1}章及之后的原著信息；未在截至第${anchorChapter}章知识账本中出现的秘密视为未知。",
                )
                storyVm.updateProfile(profile)
                // 只自动写入明确的 KNOWLEDGE。关系观察用于角色预览，但不等于玩家角色主观知道该关系。
                visibleFacts.takeLast(MAX_RUNTIME_FACTS_V1).forEach { fact ->
                    storyVm.addKnowledge(
                        character = candidate.name,
                        fact = fact.fact,
                        source = "原著第${fact.chapter}章${fact.evidence.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}",
                        kind = StoryKnowledgeKindV3.KNOWN,
                    )
                }
                showBinding = false
            },
            onSaveManual = { storyVm.updateProfile(it); showBinding = false },
        )
    }
}

@Composable
private fun StoryRoleBindingDialogV1(
    anchorChapter: Int,
    current: StoryPlayerProfile,
    loading: Boolean,
    candidates: List<StoryCanonRoleCandidateV1>,
    error: String?,
    onDismiss: () -> Unit,
    onBindCanon: (StoryCanonRoleCandidateV1) -> Unit,
    onSaveManual: (StoryPlayerProfile) -> Unit,
) {
    var mode by remember { mutableStateOf(StoryRoleModeV1.CANON) }
    var selected by remember { mutableStateOf<StoryCanonRoleCandidateV1?>(null) }
    var query by remember { mutableStateOf("") }
    var selfName by remember { mutableStateOf(current.name.ifBlank { "我" }) }
    var customName by remember { mutableStateOf(current.name) }
    var customIdentity by remember { mutableStateOf(current.identity) }
    var customTraits by remember { mutableStateOf(current.traits) }
    var customKnown by remember { mutableStateOf(current.knownFacts) }
    var customForbidden by remember { mutableStateOf(current.forbiddenKnowledge) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择进入故事的身份") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 590.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = LanghuanShape.card, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                    Text(
                        "身份只读取原著第 1～$anchorChapter 章。第 ${anchorChapter + 1} 章及之后的角色设定、秘密和关系不会进入角色卡。",
                        Modifier.fillMaxWidth().padding(11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StoryRoleModeV1.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item; selected = null },
                            label = { Text(item.label) },
                            leadingIcon = if (mode == item) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                        )
                    }
                }
                HorizontalDivider()

                when (mode) {
                    StoryRoleModeV1.CANON -> {
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("正在整理截至第 $anchorChapter 章可扮演角色……", Modifier.padding(start = 9.dp))
                            }
                        } else if (candidates.isEmpty()) {
                            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.TheaterComedy, null, tint = MaterialTheme.colorScheme.primary)
                                Text("还没有原著角色索引", fontWeight = FontWeight.Bold)
                                Text(
                                    error ?: "先在“设定 → 从原著抽设定”完成正文抽取；也可以直接选择“作为自己”或“原创角色”。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            OutlinedTextField(
                                query,
                                { query = it },
                                Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("搜索角色 / 别名") },
                            )
                            val filtered = remember(candidates, query) {
                                val q = query.trim().lowercase()
                                if (q.isBlank()) candidates else candidates.filter { candidate ->
                                    candidate.name.lowercase().contains(q) || candidate.aliases.any { it.lowercase().contains(q) }
                                }
                            }
                            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(filtered, key = { it.name }) { candidate ->
                                    val isSelected = selected?.name == candidate.name
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable { selected = candidate },
                                        shape = LanghuanShape.card,
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Person, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                                                Text(candidate.name, Modifier.padding(start = 7.dp).weight(1f), fontWeight = FontWeight.Bold)
                                                if (isSelected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                            if (candidate.aliases.isNotEmpty()) {
                                                Text("别名：${candidate.aliases.take(5).joinToString("、")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                "出场记录 ${candidate.mentions} · 已知事实 ${candidate.knownFacts.size} · 关系 ${candidate.relationships.size} · 最近≤第${candidate.lastChapter}章",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (candidate.description.isNotBlank()) {
                                                Text(candidate.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    StoryRoleModeV1.SELF -> {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                            Text("把自己放进当前世界", fontWeight = FontWeight.Bold)
                            OutlinedTextField(selfName, { selfName = it }, Modifier.fillMaxWidth(), label = { Text("故事中的名字") }, singleLine = true)
                            Text(
                                "默认只拥有截至第 $anchorChapter 章公开、亲历或剧情明确给予的信息；未来原著内容仍然锁死。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    StoryRoleModeV1.CUSTOM -> {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(customName, { customName = it }, Modifier.fillMaxWidth(), label = { Text("姓名") })
                            OutlinedTextField(customIdentity, { customIdentity = it }, Modifier.fillMaxWidth(), label = { Text("身份 / 来历") })
                            OutlinedTextField(customTraits, { customTraits = it }, Modifier.fillMaxWidth(), label = { Text("性格 / 特征") }, minLines = 2)
                            OutlinedTextField(customKnown, { customKnown = it }, Modifier.fillMaxWidth(), label = { Text("进入时已知信息") }, minLines = 2)
                            OutlinedTextField(customForbidden, { customForbidden = it }, Modifier.fillMaxWidth(), label = { Text("额外禁止知道") }, minLines = 2)
                            Text("系统仍会额外禁止第 ${anchorChapter + 1} 章及之后的原著信息。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        StoryRoleModeV1.CANON -> selected?.let(onBindCanon)
                        StoryRoleModeV1.SELF -> onSaveManual(
                            StoryPlayerProfile(
                                name = selfName.trim().ifBlank { "我" },
                                identity = "作为自己进入故事 · 锚点第${anchorChapter}章",
                                traits = "由玩家本人决定言行与性格，DM 不替玩家做重大决定。",
                                knownFacts = "只知道截至第${anchorChapter}章已经公开、亲历或剧情明确给予的信息。",
                                forbiddenKnowledge = "严格禁止知道第${anchorChapter + 1}章及之后的原著信息；未获得证据的秘密一律视为未知。",
                            )
                        )
                        StoryRoleModeV1.CUSTOM -> onSaveManual(
                            StoryPlayerProfile(
                                name = customName.trim(),
                                identity = customIdentity.trim().ifBlank { "原创角色 · 锚点第${anchorChapter}章" },
                                traits = customTraits.trim(),
                                knownFacts = customKnown.trim(),
                                forbiddenKnowledge = listOf(
                                    customForbidden.trim(),
                                    "严格禁止知道第${anchorChapter + 1}章及之后的原著信息。",
                                ).filter { it.isNotBlank() }.joinToString("；"),
                            )
                        )
                    }
                },
                enabled = when (mode) {
                    StoryRoleModeV1.CANON -> selected != null
                    StoryRoleModeV1.SELF -> true
                    StoryRoleModeV1.CUSTOM -> customName.isNotBlank()
                },
            ) { Text("绑定身份") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}

private const val MAX_PROFILE_FACTS_V1 = 32
private const val MAX_RUNTIME_FACTS_V1 = 18
