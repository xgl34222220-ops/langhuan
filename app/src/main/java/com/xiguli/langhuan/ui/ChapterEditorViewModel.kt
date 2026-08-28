package com.xiguli.langhuan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.ChapterEditorStore
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoredChapterVersion
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.PromptBundle
import com.xiguli.langhuan.engine.UniversalAiGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewriteProposal(
    val start: Int,
    val end: Int,
    val original: String,
    val replacement: String,
    val instruction: String,
)

data class VersionComparison(
    val version: StoredChapterVersion,
    val oldChanged: String,
    val currentChanged: String,
    val prefixChars: Int,
    val suffixChars: Int,
)

data class ChapterEditorUiState(
    val novelId: String = "",
    val snapshot: StorySnapshot? = null,
    val draft: ChapterDraft? = null,
    val chapters: List<ChapterDraft> = emptyList(),
    val versions: List<StoredChapterVersion> = emptyList(),
    val dirty: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isRewriting: Boolean = false,
    val lastSavedAt: Long? = null,
    val rewriteProposal: RewriteProposal? = null,
    val comparison: VersionComparison? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val ready: Boolean get() = snapshot != null && draft != null
    val busy: Boolean get() = isLoading || isSaving || isRewriting
}

class ChapterEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ChapterEditorStore(application)
    private val repository = PersistentStoryRepository(application)
    private val _state = MutableStateFlow(ChapterEditorUiState())
    val state: StateFlow<ChapterEditorUiState> = _state.asStateFlow()
    private var autosaveJob: Job? = null

    fun load(novelId: String, chapterNumber: Int? = null) {
        if (novelId.isBlank()) return
        val current = _state.value
        if (current.novelId == novelId && current.ready && (chapterNumber == null || current.draft?.chapterNumber == chapterNumber)) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.value = ChapterEditorUiState(novelId = novelId, isLoading = true)
            runCatching {
                val loaded = store.load(novelId, chapterNumber)
                Triple(loaded, store.chapters(novelId), store.versions(novelId, loaded.draft.chapterNumber))
            }.onSuccess { (loaded, chapters, versions) ->
                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        chapters = chapters,
                        versions = versions,
                        dirty = false,
                        isLoading = false,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "加载正文失败") }
            }
        }
    }

    fun updateTitle(title: String) {
        val draft = _state.value.draft ?: return
        if (draft.title == title) return
        _state.update { it.copy(draft = draft.copy(title = title), dirty = true) }
        scheduleAutosave()
    }

    fun updateContent(content: String) {
        val draft = _state.value.draft ?: return
        if (draft.content == content) return
        _state.update { it.copy(draft = draft.copy(content = content), dirty = true, rewriteProposal = null, comparison = null) }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1_100)
            persist(createVersion = false, announce = false)
        }
    }

    fun saveCheckpoint() {
        autosaveJob?.cancel()
        viewModelScope.launch { persist(createVersion = true, announce = true) }
    }

    private suspend fun persist(createVersion: Boolean, announce: Boolean): Boolean {
        val current = _state.value
        val snapshot = current.snapshot ?: return false
        val draft = current.draft ?: return false
        if (current.isSaving) return false
        if (!current.dirty && !createVersion) return true
        _state.update { it.copy(isSaving = true, error = null) }
        return runCatching {
            if (createVersion) store.checkpoint(snapshot, draft) else store.autosave(snapshot, draft)
        }.fold(
            onSuccess = { persisted ->
                val versions = if (createVersion) store.versions(persisted.draft.novelId, persisted.draft.chapterNumber) else _state.value.versions
                _state.update {
                    it.copy(
                        snapshot = persisted.snapshot,
                        draft = persisted.draft,
                        versions = versions,
                        dirty = false,
                        isSaving = false,
                        lastSavedAt = System.currentTimeMillis(),
                        message = if (announce) "已创建版本 v${persisted.draft.version}" else it.message,
                    )
                }
                true
            },
            onFailure = { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "保存正文失败") }
                false
            },
        )
    }

    fun openChapter(chapterNumber: Int) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (draft.chapterNumber == chapterNumber || current.busy) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = current.dirty, error = null) }
            runCatching {
                if (current.dirty) store.autosave(snapshot, draft)
                val loaded = store.load(current.novelId, chapterNumber)
                Triple(loaded, store.chapters(current.novelId), store.versions(current.novelId, chapterNumber))
            }.onSuccess { (loaded, chapters, versions) ->
                _state.update {
                    it.copy(
                        snapshot = loaded.snapshot,
                        draft = loaded.draft,
                        chapters = chapters,
                        versions = versions,
                        dirty = false,
                        isSaving = false,
                        rewriteProposal = null,
                        comparison = null,
                        lastSavedAt = System.currentTimeMillis(),
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSaving = false, error = error.message ?: "切换章节失败") }
            }
        }
    }

    fun rewriteSelection(start: Int, end: Int, instruction: String) {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.busy) return
        val safeStart = start.coerceIn(0, draft.content.length)
        val safeEnd = end.coerceIn(safeStart, draft.content.length)
        val selected = draft.content.substring(safeStart, safeEnd)
        if (selected.isBlank()) {
            _state.update { it.copy(error = "请先选中要重写的正文") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRewriting = true, error = null, rewriteProposal = null) }
            runCatching {
                val providers = repository.observeProviders().first()
                val provider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
                    ?: error("请先在设置里添加 AI 服务")
                val config = repository.providerConfig(provider.id) ?: error("当前 AI 服务不可用")
                val gateway = UniversalAiGateway(config)
                val before = draft.content.substring(0, safeStart).takeLast(900)
                val after = draft.content.substring(safeEnd).take(900)
                val request = instruction.trim().ifBlank { "在不改变事实和剧情含义的前提下润色，使表达更自然、更有画面感。" }
                val output = gateway.generate(
                    PromptBundle(
                        system = """
                            你是中文长篇小说精修编辑。只改用户选中的片段，绝不能擅自续写选区外剧情，不能改变人物身份、时间线、地点、因果、伏笔事实。
                            必须输出 GeneratedChapter JSON：title 固定 rewrite；content 只放替换后的片段正文；summary 留空；stateChanges=[]；touchedForeshadowingIds=[]。
                            不要在 content 里解释修改原因，不要加 Markdown 代码块。
                        """.trimIndent(),
                        user = """
                            小说：${current.snapshot?.novel?.title.orEmpty()}
                            第${draft.chapterNumber}章：${draft.title}
                            修改要求：$request

                            选区前文：
                            $before

                            ===== 只重写下面选区 =====
                            $selected
                            ===== 选区结束 =====

                            选区后文：
                            $after
                        """.trimIndent(),
                    )
                )
                RewriteProposal(
                    start = safeStart,
                    end = safeEnd,
                    original = selected,
                    replacement = output.content.trim().ifBlank { error("AI 返回了空内容") },
                    instruction = request,
                )
            }.onSuccess { proposal ->
                _state.update { it.copy(isRewriting = false, rewriteProposal = proposal) }
            }.onFailure { error ->
                _state.update { it.copy(isRewriting = false, error = error.message ?: "局部重写失败") }
            }
        }
    }

    fun applyRewrite() {
        val proposal = _state.value.rewriteProposal ?: return
        val draft = _state.value.draft ?: return
        if (proposal.end > draft.content.length || draft.content.substring(proposal.start, proposal.end) != proposal.original) {
            _state.update { it.copy(rewriteProposal = null, error = "正文已发生变化，请重新选择片段后再重写") }
            return
        }
        val updated = draft.content.substring(0, proposal.start) + proposal.replacement + draft.content.substring(proposal.end)
        _state.update {
            it.copy(
                draft = draft.copy(content = updated),
                dirty = true,
                rewriteProposal = null,
                message = "已应用 AI 局部重写，正在自动保存",
            )
        }
        scheduleAutosave()
    }

    fun dismissRewrite() = _state.update { it.copy(rewriteProposal = null) }

    fun compare(version: StoredChapterVersion) {
        val current = _state.value.draft?.content ?: return
        val old = version.content
        var prefix = 0
        val maxPrefix = minOf(old.length, current.length)
        while (prefix < maxPrefix && old[prefix] == current[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(old.length - prefix, current.length - prefix)
        while (suffix < maxSuffix && old[old.length - 1 - suffix] == current[current.length - 1 - suffix]) suffix++
        val oldEnd = (old.length - suffix).coerceAtLeast(prefix)
        val currentEnd = (current.length - suffix).coerceAtLeast(prefix)
        _state.update {
            it.copy(
                comparison = VersionComparison(
                    version = version,
                    oldChanged = old.substring(prefix, oldEnd),
                    currentChanged = current.substring(prefix, currentEnd),
                    prefixChars = prefix,
                    suffixChars = suffix,
                )
            )
        }
    }

    fun dismissComparison() = _state.update { it.copy(comparison = null) }

    fun restore(version: StoredChapterVersion) {
        val current = _state.value
        val snapshot = current.snapshot ?: return
        val draft = current.draft ?: return
        if (current.busy) return
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, comparison = null) }
            runCatching { store.restore(snapshot, draft, version) }
                .onSuccess { persisted ->
                    val versions = store.versions(persisted.draft.novelId, persisted.draft.chapterNumber)
                    _state.update {
                        it.copy(
                            snapshot = persisted.snapshot,
                            draft = persisted.draft,
                            versions = versions,
                            dirty = false,
                            isSaving = false,
                            rewriteProposal = null,
                            lastSavedAt = System.currentTimeMillis(),
                            message = "已恢复 v${version.version} 内容，并保存为新的 v${persisted.draft.version}",
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(isSaving = false, error = error.message ?: "恢复版本失败") }
                }
        }
    }

    fun flushAndClose(onDone: () -> Unit) {
        autosaveJob?.cancel()
        viewModelScope.launch {
            val ok = if (_state.value.dirty) persist(createVersion = false, announce = false) else true
            if (ok) onDone()
        }
    }

    fun clearNotice() = _state.update { it.copy(message = null, error = null) }
}
