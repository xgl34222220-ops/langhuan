from pathlib import Path
import re


def read(path):
    return Path(path).read_text(encoding="utf-8")


def write(path, text):
    Path(path).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)


# 1) Fix TaskModelRouter suspend construction and use discovered exact context limits when available.
p = "app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt"
t = read(p)
t = re.sub(
    r'''        val selections = AiTaskType\.entries\.associateWith \{ task ->\n(?P<body>.*?)\n        \}\n        return TaskRoutingSession\(defaultProvider, defaultGateway, selections\)''',
    '''        val selections = linkedMapOf<AiTaskType, ResolvedTaskModel>()
        for (task in AiTaskType.entries) {
            val route = routes[task]
            val routedProvider = route?.let { target -> providers.firstOrNull { it.id == target.providerId } }
            val candidateProvider = routedProvider ?: defaultProvider
            val candidateModel = route?.modelId?.takeIf { routedProvider != null }.orEmpty().ifBlank { candidateProvider.model }
            val candidateProfile = store.profile(candidateProvider, candidateModel)
            val safe = route != null && routedProvider != null && candidateProfile.transportSupported
            val provider = if (safe) candidateProvider else defaultProvider
            val model = if (safe) candidateModel else defaultProvider.model
            val profile = store.profile(provider, model)
            val selectedGateway = if (!safe || (provider.id == defaultProvider.id && model == defaultProvider.model)) {
                defaultGateway
            } else {
                gateway(provider, model)
            }
            selections[task] = ResolvedTaskModel(
                task = task,
                provider = provider,
                modelId = model,
                profile = profile,
                inheritedGlobal = !safe,
                gateway = selectedGateway,
            )
        }
        return TaskRoutingSession(defaultProvider, defaultGateway, selections)''',
    t,
    count=1,
    flags=re.S,
)
if "val selections = linkedMapOf<AiTaskType, ResolvedTaskModel>()" not in t:
    raise RuntimeError("AiTaskRouting router loop patch did not apply")
t = replace_once(
    t,
    '''        val contextWindow = estimateContext(id)
        val longText = contextWindow >= 128_000 || listOf(''',
    '''        val discoveredContext = discovered?.contextWindow?.takeIf { it > 0 }
        val contextWindow = discoveredContext ?: estimateContext(id)
        val longText = contextWindow >= 128_000 || listOf(''',
    "capability context",
)
t = replace_once(t, "            estimated = true,\n", "            estimated = discoveredContext == null,\n", "capability estimated")
write(p, t)

# 2) Model discovery can preserve exact context limits exposed by APIs.
p = "app/src/main/java/com/xiguli/langhuan/engine/UniversalAiGateway.kt"
t = read(p)
t = replace_once(
    t,
    '''data class DiscoveredModel(
    val id: String,
    val displayName: String = id,
    val reasoning: Boolean = false,
    val vision: Boolean = false,
)''',
    '''data class DiscoveredModel(
    val id: String,
    val displayName: String = id,
    val reasoning: Boolean = false,
    val vision: Boolean = false,
    /** Exact only when the provider's model endpoint exposes it; otherwise 0 and capability profiling stays estimated. */
    val contextWindow: Int = 0,
)''',
    "DiscoveredModel contextWindow",
)
t = replace_once(
    t,
    '''            ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.ANTHROPIC ->
                root["data"].asObjects().mapNotNull { item -> item.string("id")?.toModel() }

            ApiProtocol.GEMINI -> root["models"].asObjects().mapNotNull { item ->
                val methods = item["supportedGenerationMethods"] as? JsonArray
                if (methods != null && methods.none { it.jsonPrimitive.contentOrNull == "generateContent" }) return@mapNotNull null
                item.string("name")?.removePrefix("models/")?.toModel(item.string("displayName"))
            }

            ApiProtocol.OLLAMA -> root["models"].asObjects().mapNotNull { item ->
                item.string("name")?.toModel(item.string("name"))
            }''',
    '''            ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.ANTHROPIC ->
                root["data"].asObjects().mapNotNull { item ->
                    item.string("id")?.toModel()?.copy(
                        contextWindow = item.firstInt("context_window", "context_length", "max_context_length", "input_token_limit"),
                    )
                }

            ApiProtocol.GEMINI -> root["models"].asObjects().mapNotNull { item ->
                val methods = item["supportedGenerationMethods"] as? JsonArray
                if (methods != null && methods.none { it.jsonPrimitive.contentOrNull == "generateContent" }) return@mapNotNull null
                item.string("name")?.removePrefix("models/")?.toModel(item.string("displayName"))?.copy(
                    contextWindow = item.firstInt("inputTokenLimit", "contextWindow", "context_window"),
                )
            }

            ApiProtocol.OLLAMA -> root["models"].asObjects().mapNotNull { item ->
                item.string("name")?.toModel(item.string("name"))?.copy(
                    contextWindow = item.firstInt("context_length", "context_window"),
                )
            }''',
    "model discovery context",
)
t = replace_once(
    t,
    '''private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonElement?.asObjects(): List<JsonObject> =''',
    '''private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.firstInt(vararg keys: String): Int =
    keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() } ?: 0

private fun JsonElement?.asObjects(): List<JsonObject> =''',
    "JsonObject firstInt",
)
write(p, t)

# 3) Task routing panel compile fixes.
p = "app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt"
t = read(p)
t = replace_once(t, "import androidx.compose.foundation.layout.size\n", "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.weight\n", "weight import")
t = replace_once(
    t,
    '''                .onSuccess { discovery ->
                    val models = discovery.models.sortedWith(
                        compareByDescending<DiscoveredModel> { it.id == _state.value.routes[_state.value.expandedTask]?.modelId }
                            .thenByDescending { it.id == provider.model }
                            .thenBy { it.displayName.lowercase() }
                    )''',
    '''                .onSuccess { discovery ->
                    val routedModel = _state.value.expandedTask?.let { task -> _state.value.routes[task]?.modelId }
                    val models = discovery.models.sortedWith(
                        compareByDescending<DiscoveredModel> { it.id == routedModel }
                            .thenByDescending { it.id == provider.model }
                            .thenBy { it.displayName.lowercase() }
                    )''',
    "routing panel nullable route",
)
write(p, t)

# 4) Application Runtime freezes one routed session per command.
p = "app/src/main/java/com/xiguli/langhuan/engine/ChapterRunRuntime.kt"
t = read(p)
t = re.sub(
    r'''    private suspend fun resolveGatewayOrNull\(\): Pair<String, UniversalAiGateway>\? \{\n        val providers = repository\.observeProviders\(\)\.first\(\)\n        val provider = providers\.firstOrNull \{ it\.isDefault \} \?: providers\.firstOrNull\(\) \?: return null\n        val config = repository\.providerConfig\(provider\.id\) \?: return null\n        return "\$\{provider\.name\} · \$\{provider\.model\}" to UniversalAiGateway\(config\)\n    \}''',
    '''    private suspend fun resolveGatewayOrNull(): Pair<String, AiGateway>? = runCatching {
        val routed = TaskDispatchingAiGateway(TaskModelRouter(app).snapshot())
        routed.summary to routed
    }.getOrNull()''',
    t,
    count=1,
)
if "TaskDispatchingAiGateway(TaskModelRouter(app).snapshot())" not in t:
    raise RuntimeError("ChapterRunRuntime routing patch did not apply")
write(p, t)

# 5) Studio non-runtime AI actions (planning/audit/etc.) use same routed gateway; transient unsaved provider still works.
p = "app/src/main/java/com/xiguli/langhuan/ui/StudioViewModel.kt"
t = read(p)
t = replace_once(t, "import com.xiguli.langhuan.engine.ProviderDiscovery\n", "import com.xiguli.langhuan.engine.ProviderDiscovery\nimport com.xiguli.langhuan.engine.TaskDispatchingAiGateway\nimport com.xiguli.langhuan.engine.TaskModelRouter\n", "Studio routing imports")
t = re.sub(
    r'''    private suspend fun configuredGateway\(\): AiGateway\? \{\n        val provider = _state\.value\.provider\n        val savedConfig = provider\.activeProviderId\?\.let \{ repository\.providerConfig\(it\) \}\n        val transientConfig = provider\.discovery\?\.takeIf \{ provider\.formModel\.isNotBlank\(\) \}\?\.let \{ discovery ->\n            AiProviderConfig\(discovery\.normalizedBaseUrl, provider\.apiKey, provider\.formModel, discovery\.protocol, supportsJsonMode = discovery\.supportsJsonMode\)\n        \}\n        return \(savedConfig \?: transientConfig\)\?\.let\(::UniversalAiGateway\)\n    \}''',
    '''    private suspend fun configuredGateway(): AiGateway? {
        val provider = _state.value.provider
        if (provider.activeProviderId != null) {
            val routed = runCatching {
                TaskDispatchingAiGateway(TaskModelRouter(getApplication<Application>()).snapshot())
            }.getOrNull()
            if (routed != null) return routed
        }
        val transientConfig = provider.discovery?.takeIf { provider.formModel.isNotBlank() }?.let { discovery ->
            AiProviderConfig(discovery.normalizedBaseUrl, provider.apiKey, provider.formModel, discovery.protocol, supportsJsonMode = discovery.supportsJsonMode)
        }
        return transientConfig?.let(::UniversalAiGateway)
    }''',
    t,
    count=1,
)
if "TaskModelRouter(getApplication<Application>()).snapshot()" not in t:
    raise RuntimeError("Studio configuredGateway patch did not apply")
write(p, t)

# 6) Writing flow scene/next-chapter AI uses the same router; chapter generation already goes through Runtime.
p = "app/src/main/java/com/xiguli/langhuan/ui/WritingFlowViewModel.kt"
t = read(p)
t = replace_once(t, "import com.xiguli.langhuan.engine.AgentReview\n", "import com.xiguli.langhuan.engine.AgentReview\nimport com.xiguli.langhuan.engine.AiGateway\n", "Writing AiGateway import")
t = replace_once(t, "import com.xiguli.langhuan.engine.RunEvent\n", "import com.xiguli.langhuan.engine.RunEvent\nimport com.xiguli.langhuan.engine.TaskDispatchingAiGateway\nimport com.xiguli.langhuan.engine.TaskModelRouter\n", "Writing routing imports")
t = re.sub(
    r'''    private suspend fun activeGateway\(\): UniversalAiGateway \{\n        val providers = repository\.observeProviders\(\)\.first\(\)\n        val provider = providers\.firstOrNull \{ it\.isDefault \} \?: providers\.firstOrNull\(\)\n            \?: error\("请先到设置添加并启用一个 AI 服务"\)\n        val config = repository\.providerConfig\(provider\.id\) \?: error\("当前 AI 服务配置不可用"\)\n        _state\.update \{ it\.copy\(providerLabel = "\$\{provider\.name\} · \$\{provider\.model\}"\) \}\n        return UniversalAiGateway\(config\)\n    \}''',
    '''    private suspend fun activeGateway(): AiGateway {
        val routed = TaskDispatchingAiGateway(TaskModelRouter(getApplication<Application>()).snapshot())
        _state.update { it.copy(providerLabel = routed.summary) }
        return routed
    }''',
    t,
    count=1,
)
if "private suspend fun activeGateway(): AiGateway" not in t:
    raise RuntimeError("WritingFlow activeGateway patch did not apply")
write(p, t)

# 7) Normal quick-switch discovery also refreshes capability profiles.
p = "app/src/main/java/com/xiguli/langhuan/ui/ProviderQuickSwitch.kt"
t = read(p)
t = replace_once(t, "import com.xiguli.langhuan.engine.DiscoveredModel\n", "import com.xiguli.langhuan.engine.AiTaskRoutingStore\nimport com.xiguli.langhuan.engine.DiscoveredModel\n", "QuickSwitch store import")
t = replace_once(t, "    private val detector = ProviderAutoDetector()\n", "    private val detector = ProviderAutoDetector()\n    private val routingStore = AiTaskRoutingStore(application)\n", "QuickSwitch routing store")
t = replace_once(
    t,
    '''                .onSuccess { discovery ->
                    val ordered = discovery.models.sortedWith(''',
    '''                .onSuccess { discovery ->
                    routingStore.rememberDiscovery(provider, discovery.models)
                    val ordered = discovery.models.sortedWith(''',
    "QuickSwitch remember profiles",
)
write(p, t)

# 8) Put task routing inside the existing AI service settings page.
p = "app/src/main/java/com/xiguli/langhuan/ui/AiProviderSetupPage.kt"
t = read(p)
t = replace_once(t, "    val quickModelVm: ProviderQuickSwitchViewModel = viewModel()\n", "    val quickModelVm: ProviderQuickSwitchViewModel = viewModel()\n    val taskRoutingVm: TaskModelRoutingViewModel = viewModel()\n", "setup routing vm")
t = replace_once(
    t,
    '''            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (p.editingProviderId == null) "添加 AI 服务" else "编辑 AI 服务",''',
    '''            if (p.savedProviders.isNotEmpty()) {
                item { TaskModelRoutingPanel(taskRoutingVm) }
            }

            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (p.editingProviderId == null) "添加 AI 服务" else "编辑 AI 服务",''',
    "setup routing panel",
)
write(p, t)

# 9) Version bump.
p = "app/build.gradle.kts"
t = read(p)
t = replace_once(t, "        versionCode = 66\n        versionName = \"0.26.8-alpha01\"", "        versionCode = 67\n        versionName = \"0.26.9-alpha01\"", "version bump")
write(p, t)

print("task model routing wiring applied")
