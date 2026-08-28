package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.ProviderSaveRequest
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.ProviderAutoDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderQuickSwitchState(
    val providers: List<StoredAiProvider> = emptyList(),
    val selectedProviderId: String? = null,
    val models: List<DiscoveredModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val isSwitching: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val selectedProvider: StoredAiProvider?
        get() = providers.firstOrNull { it.id == selectedProviderId }
}

class ProviderQuickSwitchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val detector = ProviderAutoDetector()
    private val _state = MutableStateFlow(ProviderQuickSwitchState())
    val state: StateFlow<ProviderQuickSwitchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                _state.update { current ->
                    val selected = current.selectedProviderId?.takeIf { id -> providers.any { it.id == id } }
                        ?: providers.firstOrNull { it.isDefault }?.id
                        ?: providers.firstOrNull()?.id
                    current.copy(providers = providers, selectedProviderId = selected)
                }
            }
        }
    }

    fun prepare(preferredProviderId: String?) {
        val target = preferredProviderId?.takeIf { id -> _state.value.providers.any { it.id == id } }
            ?: _state.value.providers.firstOrNull { it.isDefault }?.id
            ?: _state.value.providers.firstOrNull()?.id
        if (target == null) return
        selectProvider(target, refresh = true)
    }

    fun selectProvider(id: String, refresh: Boolean = true) {
        if (_state.value.selectedProviderId == id && !refresh && _state.value.models.isNotEmpty()) return
        _state.update { it.copy(selectedProviderId = id, models = emptyList(), error = null, message = null) }
        if (refresh) refreshModels()
    }

    fun refreshModels() {
        val provider = _state.value.selectedProvider ?: return
        if (_state.value.isLoadingModels) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true, error = null, message = null) }
            val key = repository.apiKey(provider.id).orEmpty()
            runCatching { detector.detect(provider.baseUrl, key) }
                .onSuccess { discovery ->
                    val ordered = discovery.models.sortedWith(compareByDescending<DiscoveredModel> { it.id == provider.model }.thenBy { it.displayName.lowercase() })
                    _state.update {
                        it.copy(
                            isLoadingModels = false,
                            models = ordered,
                            message = if (ordered.isEmpty()) "接口可识别，但没有开放模型列表；请在 AI 服务编辑页手动填写模型名。" else "已读取 ${ordered.size} 个模型",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoadingModels = false, error = error.message ?: "读取模型列表失败") }
                }
        }
    }

    fun switchModel(modelId: String) {
        val provider = _state.value.selectedProvider ?: return
        if (modelId.isBlank() || _state.value.isSwitching) return
        viewModelScope.launch {
            _state.update { it.copy(isSwitching = true, error = null) }
            runCatching {
                repository.saveProvider(
                    ProviderSaveRequest(
                        id = provider.id,
                        name = provider.name,
                        baseUrl = provider.baseUrl,
                        protocol = provider.protocol,
                        model = modelId,
                        temperature = provider.temperature,
                        supportsJsonMode = provider.supportsJsonMode,
                        apiKey = "",
                        makeDefault = true,
                    )
                )
            }.onSuccess { saved ->
                _state.update { it.copy(isSwitching = false, message = "已切换到 ${saved.model}") }
            }.onFailure { error ->
                _state.update { it.copy(isSwitching = false, error = error.message ?: "切换模型失败") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderQuickSwitchSheet(
    viewModel: ProviderQuickSwitchViewModel,
    preferredProviderId: String?,
    onDismiss: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(preferredProviderId) { viewModel.prepare(preferredProviderId) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("快速切换模型", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("一个 AI 服务可以直接读取并切换它提供的模型，不需要复制成多个服务。", color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (state.providers.isEmpty()) {
                Text("还没有保存 AI 服务，请先到设置中添加。")
                return@Column
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.providers.forEach { provider ->
                    FilterChip(
                        selected = provider.id == state.selectedProviderId,
                        onClick = { viewModel.selectProvider(provider.id) },
                        label = { Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (provider.isDefault) ({ Icon(Icons.Rounded.CloudDone, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }

            state.selectedProvider?.let { provider ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, fontWeight = FontWeight.Bold)
                            Text("当前：${provider.model} · ${provider.protocol.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = viewModel::refreshModels, enabled = !state.isLoadingModels) {
                            if (state.isLoadingModels) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Rounded.Refresh, "重新读取模型")
                        }
                    }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

            if (state.isLoadingModels) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if (state.models.isNotEmpty()) {
                Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    state.models.take(60).forEach { model ->
                        val selected = model.id == state.selectedProvider?.model
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !state.isSwitching) { viewModel.switchModel(model.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                    Text(model.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (model.id != model.displayName) Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (selected) Text("当前", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
