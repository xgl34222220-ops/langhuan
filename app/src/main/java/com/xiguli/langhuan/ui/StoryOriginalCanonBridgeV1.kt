package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.engine.HybridMemoryRetriever
import com.xiguli.langhuan.engine.MemoryCandidate
import com.xiguli.langhuan.engine.RetrievedContextItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryCanonBridgeUiStateV1(
    val loading: Boolean = false,
    val anchorChapter: Int = 0,
    val items: List<RetrievedContextItem> = emptyList(),
    val syncToken: Long = 0L,
    val error: String? = null,
)

class StoryCanonBridgeViewModelV1(application: Application) : AndroidViewModel(application) {
    private val dao = LanghuanDatabase.get(application).memoryChunkDao()
    private val retriever = HybridMemoryRetriever()
    private val _state = MutableStateFlow(StoryCanonBridgeUiStateV1())
    val state: StateFlow<StoryCanonBridgeUiStateV1> = _state.asStateFlow()
    private var lastKey: String = ""

    fun sync(novelId: String, anchorChapter: Int, query: String) {
        if (novelId.isBlank() || anchorChapter <= 0 || query.isBlank()) return
        val key = "$novelId|$anchorChapter|${query.hashCode()}"
        if (key == lastKey && _state.value.items.isNotEmpty()) return
        lastKey = key
        viewModelScope.launch {
            _state.update { it.copy(loading = true, anchorChapter = anchorChapter, error = null) }
            runCatching {
                val candidates = dao.originalCanonBefore(novelId, anchorChapter, 2_400).map { item ->
                    MemoryCandidate(
                        text = item.text,
                        sourceType = item.sourceType,
                        sourceId = item.sourceId,
                        chapterNumber = item.chapterNumber,
                        updatedAt = item.updatedAt,
                    )
                }
                // HybridMemoryRetriever 对 ORIGINAL_* 使用严格 < currentChapter；传 anchor+1 即得到 <= anchor。
                retriever.rank(query, candidates, anchorChapter + 1, 28).map { hit ->
                    RetrievedContextItem(
                        sourceType = hit.candidate.sourceType,
                        sourceId = hit.candidate.sourceId,
                        chapterNumber = hit.candidate.chapterNumber,
                        text = hit.candidate.text,
                        score = hit.score,
                        reasons = hit.reasons,
                    )
                }
            }.onSuccess { items ->
                _state.value = StoryCanonBridgeUiStateV1(
                    loading = false,
                    anchorChapter = anchorChapter,
                    items = items,
                    syncToken = System.nanoTime(),
                )
            }.onFailure { error ->
                _state.update { it.copy(loading = false, error = error.message ?: "原著边界同步失败") }
            }
        }
    }
}

/**
 * Story Runtime V3 的章节边界桥。
 * 不重写酒馆引擎：把“截至进入章节可见”的原著证据同步到现有知识账本、关系网和世界注记，
 * 因此 V3 的 DM Prompt、回溯和分支持久化可以直接复用。
 */
@Composable
fun StoryPlayPanelV4(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
) {
    val storyVm: StoryPlayV3ViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val bridgeVm: StoryCanonBridgeViewModelV1 = viewModel()
    val bridge by bridgeVm.state.collectAsStateWithLifecycle()
    val session = storyState.active
    val runtime = storyState.runtime
    val latestTurn = session?.turns?.lastOrNull()

    LaunchedEffect(session?.id, latestTurn?.id, session?.anchorChapter) {
        val active = session ?: return@LaunchedEffect
        val query = buildString {
            append(book.title).append(' ')
            append(active.anchorTitle).append(' ')
            append(active.playerProfile.name).append(' ')
            append(active.playerProfile.identity).append(' ')
            append(active.worldSnapshot.takeLast(1_600)).append(' ')
            active.turns.takeLast(2).forEach { turn ->
                append(turn.player).append(' ').append(turn.narration.takeLast(1_200)).append(' ')
            }
        }.trim()
        bridgeVm.sync(book.id, active.anchorChapter, query)
    }

    LaunchedEffect(bridge.syncToken, session?.id) {
        val active = session ?: return@LaunchedEffect
        if (bridge.items.isEmpty()) return@LaunchedEffect

        bridge.items.filter { it.sourceType == "ORIGINAL_KNOWLEDGE" }.forEach { hit ->
            val character = field(hit.text, "角色")
            val fact = field(hit.text, "事实")
            if (character.isNotBlank() && fact.isNotBlank()) {
                storyVm.addKnowledge(
                    character = character,
                    fact = fact,
                    source = "原著第${hit.chapterNumber ?: active.anchorChapter}章",
                    kind = StoryKnowledgeKindV3.KNOWN,
                )
            }
        }
        bridge.items.filter { it.sourceType == "ORIGINAL_RELATION" }.forEach { hit ->
            val from = field(hit.text, "起点")
            val to = field(hit.text, "终点")
            val raw = field(hit.text, "关系")
            val label = raw.substringBefore(" = ").trim()
            val value = raw.substringAfter(" = ", "").trim()
            if (from.isNotBlank() && to.isNotBlank() && label.isNotBlank()) {
                storyVm.addRelationship(
                    from = from,
                    to = to,
                    label = label,
                    value = value,
                    evidence = "原著第${hit.chapterNumber ?: active.anchorChapter}章",
                )
            }
        }

        val currentWorld = runtime?.world
        val worldEvidence = bridge.items
            .filter { it.sourceType in setOf("ORIGINAL_SUMMARY", "ORIGINAL_ENTITY", "ORIGINAL_EVENT") }
            .take(14)
            .joinToString("\n") { hit ->
                "- 第${hit.chapterNumber ?: active.anchorChapter}章｜${hit.text.replace('\n', '；').take(520)}"
            }
            .take(5_600)
        if (currentWorld != null && worldEvidence.isNotBlank()) {
            val manualNotes = currentWorld.notes.substringBefore(CANON_MARKER).trim()
            val merged = buildString {
                if (manualNotes.isNotBlank()) append(manualNotes).append("\n\n")
                append(CANON_MARKER).append('\n').append(worldEvidence)
            }
            if (merged != currentWorld.notes) storyVm.updateWorld(currentWorld.copy(notes = merged))
        }
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV3(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
        )
        if (session != null && (bridge.loading || bridge.items.isNotEmpty())) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (bridge.loading) "正在同步原著章节边界…"
                        else "原著边界 ≤ 第 ${session.anchorChapter} 章 · ${bridge.items.size} 条证据",
                        modifier = Modifier.padding(start = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

private fun field(text: String, name: String): String = text.lineSequence()
    .firstOrNull { it.startsWith("$name：") }
    ?.substringAfter('：')
    ?.trim()
    .orEmpty()

private const val CANON_MARKER = "【原著章节边界证据】"
