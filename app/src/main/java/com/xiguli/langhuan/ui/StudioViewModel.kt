package com.xiguli.langhuan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.DemoStoryRepository
import com.xiguli.langhuan.domain.GeneratedChapter
import com.xiguli.langhuan.domain.GenerationRequest
import com.xiguli.langhuan.domain.GenerationResult
import com.xiguli.langhuan.domain.StorySnapshot
import com.xiguli.langhuan.engine.DemoAiGateway
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.GenerationPipeline
import com.xiguli.langhuan.engine.ProviderAutoDetector
import com.xiguli.langhuan.engine.ProviderDiscovery
import com.xiguli.langhuan.engine.UniversalAiGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val snapshot: StorySnapshot,
    val provider: ProviderUiState = ProviderUiState(),
    val isGenerating: Boolean = false,
    val result: GenerationResult? = null,
    val error: String? = null,
)

data class ProviderUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val isDetecting: Boolean = false,
    val discovery: ProviderDiscovery? = null,
    val selectedModel: String = "",
    val manualModel: String = "",
    val error: String? = null,
) {
    val activeModel: String get() = selectedModel.ifBlank { manualModel.trim() }
    val ready: Boolean get() = discovery != null && activeModel.isNotBlank()
}

class StudioViewModel : ViewModel() {
    private val repository = DemoStoryRepository()
    private val detector = ProviderAutoDetector()
    private val _state = MutableStateFlow(StudioUiState(snapshot = repository.snapshot))
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    fun setBaseUrl(value: String) = updateProvider {
        it.copy(baseUrl = value, discovery = null, selectedModel = "", error = null)
    }

    fun setApiKey(value: String) = updateProvider { it.copy(apiKey = value, error = null) }

    fun setManualModel(value: String) = updateProvider {
        it.copy(manualModel = value, selectedModel = "", error = null)
    }

    fun selectModel(model: DiscoveredModel) = updateProvider {
        it.copy(selectedModel = model.id, manualModel = "", error = null)
    }

    fun detectProvider() {
        val provider = _state.value.provider
        if (provider.isDetecting) return
        viewModelScope.launch {
            updateProvider { it.copy(isDetecting = true, error = null, discovery = null) }
            runCatching { detector.detect(provider.baseUrl, provider.apiKey) }
                .onSuccess { discovery ->
                    updateProvider {
                        it.copy(
                            isDetecting = false,
                            baseUrl = discovery.normalizedBaseUrl,
                            discovery = discovery,
                            selectedModel = discovery.models.firstOrNull()?.id.orEmpty(),
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    updateProvider {
                        it.copy(isDetecting = false, error = error.message ?: "无法识别该接口")
                    }
                }
        }
    }

    fun generateChapter() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null) }
            val provider = _state.value.provider
            val gateway = if (provider.ready) {
                val discovery = requireNotNull(provider.discovery)
                UniversalAiGateway(
                    AiProviderConfig(
                        baseUrl = discovery.normalizedBaseUrl,
                        apiKey = provider.apiKey,
                        model = provider.activeModel,
                        protocol = discovery.protocol,
                        supportsJsonMode = discovery.supportsJsonMode,
                    )
                )
            } else {
                DemoAiGateway()
            }
            runCatching {
                GenerationPipeline(gateway).generate(
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

    private fun updateProvider(block: (ProviderUiState) -> ProviderUiState) {
        _state.update { it.copy(provider = block(it.provider)) }
    }
}
