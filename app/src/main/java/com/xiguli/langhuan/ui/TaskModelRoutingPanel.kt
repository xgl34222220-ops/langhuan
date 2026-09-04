package com.xiguli.langhuan.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.engine.AiModelTelemetryStore
import com.xiguli.langhuan.engine.AiTaskRoutingStore
import com.xiguli.langhuan.engine.AiTaskType
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.ModelCapabilityProfile
import com.xiguli.langhuan.engine.ModelCapabilityProfiler
import com.xiguli.langhuan.engine.ModelRecommendation
import com.xiguli.langhuan.engine.ModelTaskTelemetry
import com.xiguli.langhuan.engine.ProviderAutoDetector
import com.xiguli.langhuan.engine.TaskModelRoute
import com.xiguli.langhuan.ui.theme.LanghuanShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskModelRoutingUiState(
    val providers: List<StoredAiProvider> = emptyList(),
    val routes: Map<AiTaskType, TaskModelRoute> = emptyMap(),
    val expandedTask: AiTaskType? = null,
    val selectedProviderId: String? = null,
    val models: List<DiscoveredModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val recommendations: Map<AiTaskType, ModelRecommendation> = emptyMap(),
)

class TaskModelRoutingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val detector = ProviderAutoDetector()
    private val routing = AiTaskRoutingStore(application)
    private val telemetry = AiModelTelemetryStore(application)
    private val _state = MutableStateFlow(TaskModelRoutingUiState(routes = routing.routes()))
    val state: StateFlow<TaskModelRoutingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                _state.update { current ->
                    val selected = current.selectedProviderId?.takeIf { id -> providers.any { it.id == id } }
                        ?: providers.firstOrNull { it.isDefault }?.id
                        ?: providers.firstOrNull()?.id
                    current.copy(
                        providers = providers,
                        selectedProviderId = selected,
                        routes = routing.routes(),
                        recommendations = AiTaskType.entries.mapNotNull { task ->
                            telemetry.recommendation(task, providers)?.let { task to it }
                        }.toMap(),
                    )
                }
            }
        }
    }

    fun toggle(task: AiTaskType) {
        val opening = _state.value.expandedTask != task
        val preferred = _state.value.routes[task]?.providerId
            ?: _state.value.providers.firstOrNull { it.isDefault }?.id
            ?: _state.value.providers.firstOrNull()?.id
        _state.update {
            it.copy(
                expandedTask = task.takeIf { opening },
                selectedProviderId = preferred,
                models = emptyList(),
                message = null,
                error = null,
            )
        }
        if (opening && preferred != null) loadModels(preferred)
    }

    fun selectProvider(providerId: String) {
        _state.update { it.copy(selectedProviderId = providerId, models = emptyList(), message = null, error = null) }
        loadModels(providerId)
    }

    fun refreshModels() {
        _state.value.selectedProviderId?.let(::loadModels)
    }

    fun setRoute(task: AiTaskType, model: DiscoveredModel) {
        val provider = _state.value.providers.firstOrNull { it.id == _state.value.selectedProviderId } ?: return
        val profile = routing.profile(provider, model.id, model)
        if (!profile.transportSupported) {
            _state.update { it.copy(error = "${model.displayName} 需要当前版本尚未实现的请求协议，不能用于任务路由。") }
            return
        }
        routing.setRoute(task, provider.id, model.id)
        _state.update {
            it.copy(
                routes = routing.routes(),
                message = "${task.label} → ${provider.name} · ${model.id}",
                error = null,
            )
        }
    }

    fun clearRoute(task: AiTaskType) {
        routing.clearRoute(task)
        _state.update { it.copy(routes = routing.routes(), message = "${task.label} 已恢复继承全局默认模型", error = null) }
    }

    fun profile(provider: StoredAiProvider, model: DiscoveredModel): ModelCapabilityProfile =
        routing.profile(provider, model.id, model)

    fun taskStats(task: AiTaskType, providerId: String, modelId: String): ModelTaskTelemetry? =
        telemetry.stats(task).firstOrNull { it.providerId == providerId && it.modelId == modelId }

    private fun loadModels(providerId: String) {
        val provider = _state.value.providers.firstOrNull { it.id == providerId } ?: return
        if (_state.value.isLoadingModels) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true, error = null, message = null) }
            val key = repository.apiKey(provider.id).orEmpty()
            runCatching { detector.detect(provider.baseUrl, key) }
                .onSuccess { discovery ->
                    val routedModel = _state.value.expandedTask?.let { task -> _state.value.routes[task]?.modelId }
                    val models = discovery.models.sortedWith(
                        compareByDescending<DiscoveredModel> { it.id == routedModel }
                            .thenByDescending { it.id == provider.model }
                            .thenBy { it.displayName.lowercase() }
                    )
                    routing.rememberDiscovery(provider, models)
                    _state.update {
                        it.copy(
                            models = models,
                            isLoadingModels = false,
                            message = if (models.isEmpty()) "接口没有返回模型列表；任务路由需要先能读取可选模型。" else "已读取 ${models.size} 个模型并更新能力画像",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoadingModels = false, error = error.message ?: "读取模型能力失败") }
                }
        }
    }
}

@Composable
fun TaskModelRoutingPanel(viewModel: TaskModelRoutingViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val global = state.providers.firstOrNull { it.isDefault } ?: state.providers.firstOrNull()

    Card(shape = LanghuanShape.panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Hub, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("任务级模型路由", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "未单独指定的任务继承全局默认；一次 Run 开始后会冻结路由，写到一半不会突然换模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            global?.let {
                Surface(
                    shape = LanghuanShape.cover,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                ) {
                    Text(
                        "全局默认：${it.name} · ${it.model}",
                        modifier = Modifier.fillMaxWidth().padding(11.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

            AiTaskType.entries.forEach { task ->
                val route = state.routes[task]
                val provider = route?.let { target -> state.providers.firstOrNull { it.id == target.providerId } }
                val expanded = state.expandedTask == task
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggle(task) },
                    shape = LanghuanShape.card,
                    color = if (route == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f)
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(task.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (route == null) "继承全局默认" else "${provider?.name ?: "服务已删除"} · ${route.modelId}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                        }
                        Text(task.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.recommendations[task]?.let { recommendation ->
                            Text(
                                "实测推荐：${recommendation.providerName} · ${recommendation.modelId} · ${recommendation.score}分 · ${recommendation.confidence}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                recommendation.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } ?: Text(
                            "实测推荐：暂无真实运行样本，先按能力画像选择；琅嬛不会自动换模型。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (expanded) {
                            if (route != null) {
                                OutlinedButton(onClick = { viewModel.clearRoute(task) }) { Text("恢复全局默认") }
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                state.providers.forEach { item ->
                                    FilterChip(
                                        selected = item.id == state.selectedProviderId,
                                        onClick = { viewModel.selectProvider(item.id) },
                                        label = { Text(item.name, maxLines = 1) },
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("模型与能力", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                IconButton(onClick = viewModel::refreshModels, enabled = !state.isLoadingModels) {
                                    if (state.isLoadingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.Refresh, "刷新模型")
                                }
                            }

                            val selectedProvider = state.providers.firstOrNull { it.id == state.selectedProviderId }
                            if (!state.isLoadingModels && selectedProvider != null) {
                                state.models.take(40).forEach { model ->
                                    val profile = viewModel.profile(selectedProvider, model)
                                    val selected = route?.providerId == selectedProvider.id && route.modelId == model.id
                                    val warnings = ModelCapabilityProfiler.warnings(task, profile)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = profile.transportSupported) { viewModel.setRoute(task, model) },
                                        shape = LanghuanShape.cover,
                                        color = when {
                                            selected -> MaterialTheme.colorScheme.primaryContainer
                                            profile.transportSupported -> MaterialTheme.colorScheme.surface.copy(alpha = .75f)
                                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .30f)
                                        },
                                    ) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(model.displayName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                            Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(
                                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            ) {
                                                profile.badges().forEach { badge ->
                                                    AssistChip(onClick = {}, label = { Text(badge, style = MaterialTheme.typography.labelSmall) })
                                                }
                                            }
                                            val measured = viewModel.taskStats(task, selectedProvider.id, model.id)
                                            if (measured != null) {
                                                val firstToken = measured.averageFirstTokenMs.takeIf { it > 0 }?.let { " · 首字 ${it}ms" }.orEmpty()
                                                val throughput = measured.charsPerSecond.takeIf { it > 0 }?.let { " · ${it.toInt()}字/s" }.orEmpty()
                                                val quality = if (task == AiTaskType.PROSE_AUTHOR && measured.qualitySamples > 0) {
                                                    " · 一审通过 ${(measured.qualityPassRate * 100).toInt()}% · 已采用 ${measured.userAccepted}次"
                                                } else if (measured.structuredAttempts > 0) {
                                                    " · 结构化成功 ${(measured.structuredRate * 100).toInt()}%"
                                                } else ""
                                                Text(
                                                    "实测 ${measured.calls} 次 · 成功 ${(measured.successRate * 100).toInt()}%$firstToken$throughput$quality",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            if (warnings.isNotEmpty()) {
                                                Text(
                                                    warnings.joinToString("；"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (profile.transportSupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.size(2.dp))
            Text(
                "能力画像中的上下文窗口为保守估算值；实测分数只来自本机真实 Run。接口未返回 token usage / 价格时不伪造金额成本，琅嬛也不会根据推荐自动切换模型。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
