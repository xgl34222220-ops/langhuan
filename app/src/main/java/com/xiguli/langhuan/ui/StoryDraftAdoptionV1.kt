package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.ChapterEditorStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryDraftCandidateV1(
    val novelId: String,
    val chapterNumber: Int,
    val sourceSessionId: String,
    val sourceSessionTitle: String,
    val title: String,
    val content: String,
)

data class StoryDraftAdoptionUiState(
    val busy: Boolean = false,
    val adoptedVersion: Int? = null,
    val message: String? = null,
    val error: String? = null,
)

class StoryDraftAdoptionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ChapterEditorStore(application)
    private val _state = MutableStateFlow(StoryDraftAdoptionUiState())
    val state: StateFlow<StoryDraftAdoptionUiState> = _state.asStateFlow()

    fun adopt(candidate: StoryDraftCandidateV1, onAdopted: () -> Unit) {
        if (_state.value.busy || candidate.content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, adoptedVersion = null, message = null, error = null) }
            runCatching {
                val loaded = store.load(candidate.novelId, candidate.chapterNumber)
                val originalCheckpoint = store.checkpoint(loaded.snapshot, loaded.draft)
                val adoptedDraft = originalCheckpoint.draft.copy(
                    title = candidate.title.trim().ifBlank { originalCheckpoint.draft.title },
                    content = candidate.content.trim(),
                    summary = buildString {
                        append("由“进入故事”分支「")
                        append(candidate.sourceSessionTitle.ifBlank { "故事分支" })
                        append("」整理并人工确认采用。")
                        if (originalCheckpoint.draft.summary.isNotBlank()) {
                            append(" 原章节摘要：").append(originalCheckpoint.draft.summary.take(240))
                        }
                    },
                )
                store.checkpoint(originalCheckpoint.snapshot, adoptedDraft)
            }.onSuccess { persisted ->
                _state.update {
                    it.copy(
                        busy = false,
                        adoptedVersion = persisted.draft.version,
                        message = "已采用为第 ${candidate.chapterNumber} 章 v${persisted.draft.version}；采用前原稿已永久留档",
                    )
                }
                onAdopted()
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "采用章节草稿失败") }
            }
        }
    }

    fun clearResult() = _state.update { StoryDraftAdoptionUiState() }
}

@Composable
fun StoryPlayPanelV2(
    book: ReaderBookUi,
    libraryState: LibraryExperienceState,
    aiReady: Boolean,
    onAiSetup: () -> Unit,
    onAdopted: () -> Unit,
) {
    val storyVm: StoryPlayViewModel = viewModel()
    val storyState by storyVm.state.collectAsStateWithLifecycle()
    val adoptionVm: StoryDraftAdoptionViewModel = viewModel()
    val adoptionState by adoptionVm.state.collectAsStateWithLifecycle()
    var showCandidate by remember(book.id, storyState.active?.id) { mutableStateOf(false) }

    val session = storyState.active
    val parsed = remember(session?.chapterDraftCandidate, session?.id) {
        parseStoryDraftCandidate(book.id, session)
    }

    Box(Modifier.fillMaxSize()) {
        StoryPlayPanelV1(
            book = book,
            libraryState = libraryState,
            aiReady = aiReady,
            onAiSetup = onAiSetup,
        )

        if (parsed != null && !storyState.busy) {
            ExtendedFloatingActionButton(
                onClick = {
                    adoptionVm.clearResult()
                    showCandidate = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 86.dp),
                icon = { Icon(Icons.Rounded.EditNote, null) },
                text = { Text("预览并采用草稿") },
            )
        }
    }

    if (showCandidate && parsed != null) {
        val original = libraryState.chapters.firstOrNull { it.chapterNumber == parsed.chapterNumber }
        AlertDialog(
            onDismissRequest = { if (!adoptionState.busy) showCandidate = false },
            title = { Text("演绎草稿 → 正文") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Save, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(7.dp))
                                Text("采用前自动保护原稿", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "确认采用时，会先把当前第 ${parsed.chapterNumber} 章建立一个永久历史版本，再把候选草稿保存为新的版本。不会静默覆盖。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CompareArrows, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(7.dp))
                        Text("采用前对比", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "原稿 ${original?.content?.length ?: 0} 字 → 候选 ${parsed.content.length} 字 · 来源：${parsed.sourceSessionTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text("当前正文", fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        SelectionContainer {
                            Text(
                                original?.content?.ifBlank { "（当前章节暂无正文）" } ?: "（未读取到当前正文）",
                                Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Text("候选：${parsed.title}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f),
                    ) {
                        SelectionContainer {
                            Text(parsed.content, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    adoptionState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    adoptionState.message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { adoptionVm.adopt(parsed, onAdopted) },
                    enabled = !adoptionState.busy && adoptionState.adoptedVersion == null,
                ) {
                    if (adoptionState.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("正在保护原稿并采用")
                    } else {
                        Text(if (adoptionState.adoptedVersion == null) "备份原稿并采用" else "已采用")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCandidate = false },
                    enabled = !adoptionState.busy,
                ) { Text(if (adoptionState.adoptedVersion == null) "继续保留候选" else "完成") }
            },
        )
    }
}

private fun parseStoryDraftCandidate(novelId: String, session: StoryPlaySession?): StoryDraftCandidateV1? {
    val source = session ?: return null
    val raw = source.chapterDraftCandidate.trim()
    if (raw.isBlank()) return null
    val firstBreak = raw.indexOf("\n\n")
    val title = if (firstBreak > 0) raw.substring(0, firstBreak).trim() else "演绎章节草稿"
    val body = if (firstBreak > 0) raw.substring(firstBreak + 2).trim() else raw
    if (body.isBlank()) return null
    return StoryDraftCandidateV1(
        novelId = novelId,
        chapterNumber = source.anchorChapter,
        sourceSessionId = source.id,
        sourceSessionTitle = source.title,
        title = title,
        content = body,
    )
}
