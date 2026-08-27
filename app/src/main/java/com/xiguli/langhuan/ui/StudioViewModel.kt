package com.xiguli.langhuan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.DemoStoryRepository
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.DemoAiGateway
import com.xiguli.langhuan.engine.GenerationPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val snapshot: StorySnapshot,
    val isGenerating: Boolean = false,
    val result: GenerationResult? = null,
    val error: String? = null,
)

class StudioViewModel : ViewModel() {
    private val repository = DemoStoryRepository()
    private val pipeline = GenerationPipeline(DemoAiGateway())
    private val _state = MutableStateFlow(StudioUiState(snapshot = repository.snapshot))
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    fun generateChapter() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            runCatching {
                pipeline.generate(
                    GenerationRequest(
                        snapshot = repository.snapshot,
                        chapter = repository.currentDraft,
                        targetWords = 2_500,
                    )
                )
            }.onSuccess { result ->
                _state.update { it.copy(isGenerating = false, result = result) }
            }.onFailure { error ->
                _state.update { it.copy(isGenerating = false, error = error.message ?: "生成失败") }
            }
        }
    }

    fun dismissResult() = _state.update { it.copy(result = null) }
}

