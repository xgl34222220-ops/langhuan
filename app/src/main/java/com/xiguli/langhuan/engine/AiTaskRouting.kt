package com.xiguli.langhuan.engine

import android.content.Context
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.domain.ModelUsageAttribution
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Long-form writing tasks that may intentionally use different models.
 * Missing routes always inherit the global default provider/model.
 */
@Serializable
enum class AiTaskType(
    val label: String,
    val description: String,
) {
    SCENE_DIRECTOR("章纲导演", "下一章/本章场景编排，优先推理与规划稳定性"),
    PROSE_AUTHOR("正文作者", "长篇正文流式生成，优先长文本、文风和稳定输出"),
    NOVELIZATION("小说化重构", "把报告体/AI腔改成场景化小说正文"),
    EDITOR_REVIEW("四视角主编", "结构、人物、文字、连续性对抗审稿"),
    EDITOR_REWRITE("主编修订", "根据主编意见整章重写"),
    FACT_EXTRACTION("事实提取", "摘要、状态变化、伏笔触碰等结构化提取"),
    AGENT_EXTRACTION("Agent 复盘", "章节事实抽取、Candidate 与下一章候选"),
    EXECUTION_AUDIT("执行审计", "比较滚动计划与实际正文，判断偏航"),
    AUTONOMOUS_PLANNER("自治规划", "未来滚动章节、人物弧、伏笔节奏与局部重规划"),
    FULL_BOOK_EDITOR("全书主编", "跨章节结构疲劳、人物声线、支线和文风巡检"),
}

@Serializable
data class TaskModelRoute(
    val task: AiTaskType,
    val providerId: String,
    val modelId: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ModelCapabilityProfile(
    val providerId: String,
    val modelId: String,
    val contextWindow: Int = 0,
    val supportsStreaming: Boolean = true,
    val supportsJson: Boolean = false,
    val reasoning: Boolean = false,
    val vision: Boolean = false,
    val longText: Boolean = false,
    val transportSupported: Boolean = true,
    val estimated: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val contextLabel: String
        get() = when {
            contextWindow >= 1_000_000 -> "${contextWindow / 1_000_000}M"
            contextWindow >= 1_000 -> "${contextWindow / 1_000}K"
            else -> "未知"
        }

    fun badges(): List<String> = buildList {
        add("上下文 $contextLabel")
        if (supportsStreaming) add("流式")
        if (supportsJson) add("JSON")
        if (reasoning) add("推理")
        if (vision) add("视觉")
        if (longText) add("长文本")
        if (!transportSupported) add("当前协议不可用")
    }
}

@Serializable
private data class AiTaskRoutingConfig(
    val routes: List<TaskModelRoute> = emptyList(),
    val profiles: List<ModelCapabilityProfile> = emptyList(),
)

/** Runtime-only preferences; never part of a novel project, Canon or RAG. */
class AiTaskRoutingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun routes(): Map<AiTaskType, TaskModelRoute> = load().routes.associateBy { it.task }

    @Synchronized
    fun route(task: AiTaskType): TaskModelRoute? = load().routes.lastOrNull { it.task == task }

    @Synchronized
    fun setRoute(task: AiTaskType, providerId: String, modelId: String) {
        if (providerId.isBlank() || modelId.isBlank()) return
        val current = load()
        save(
            current.copy(
                routes = current.routes.filterNot { it.task == task } +
                    TaskModelRoute(task, providerId, modelId.trim()),
            )
        )
    }

    @Synchronized
    fun clearRoute(task: AiTaskType) {
        val current = load()
        save(current.copy(routes = current.routes.filterNot { it.task == task }))
    }

    @Synchronized
    fun rememberDiscovery(provider: StoredAiProvider, models: List<DiscoveredModel>) {
        if (models.isEmpty()) return
        val current = load()
        val replacements = models.associate { model ->
            key(provider.id, model.id) to ModelCapabilityProfiler.infer(provider, model.id, model)
        }
        val kept = current.profiles.filterNot { key(it.providerId, it.modelId) in replacements }
        save(current.copy(profiles = (kept + replacements.values).takeLast(500)))
    }

    @Synchronized
    fun profile(provider: StoredAiProvider, modelId: String, discovered: DiscoveredModel? = null): ModelCapabilityProfile {
        val stored = load().profiles.lastOrNull { it.providerId == provider.id && it.modelId == modelId }
        if (stored != null && discovered == null) return stored
        return ModelCapabilityProfiler.infer(provider, modelId, discovered)
    }

    private fun load(): AiTaskRoutingConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return AiTaskRoutingConfig()
        return runCatching { json.decodeFromString(AiTaskRoutingConfig.serializer(), raw) }
            .getOrElse {
                prefs.edit().remove(KEY_CONFIG).commit()
                AiTaskRoutingConfig()
            }
    }

    private fun save(config: AiTaskRoutingConfig) {
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(AiTaskRoutingConfig.serializer(), config)).commit()
    }

    private fun key(providerId: String, modelId: String) = "$providerId::$modelId"

    private companion object {
        const val PREFS = "langhuan_ai_task_routing"
        const val KEY_CONFIG = "routing_config_v1"
    }
}

object ModelCapabilityProfiler {
    fun infer(
        provider: StoredAiProvider,
        modelId: String,
        discovered: DiscoveredModel? = null,
    ): ModelCapabilityProfile {
        val id = modelId.lowercase()
        val reasoning = discovered?.reasoning == true || listOf(
            "reason", "thinking", "o1", "o3", "o4", "deepseek-r1", "qwq", "gpt-5", "gpt-oss",
        ).any(id::contains)
        val vision = discovered?.vision == true || listOf(
            "vision", "vl", "gemini", "claude-3", "claude-4", "gpt-4o", "gpt-4.1", "gpt-5",
        ).any(id::contains)
        val discoveredContext = discovered?.contextWindow?.takeIf { it > 0 }
        val contextWindow = discoveredContext ?: estimateContext(id)
        val longText = contextWindow >= 128_000 || listOf(
            "claude", "gemini", "gpt-4.1", "gpt-5", "qwen", "deepseek", "minimax",
        ).any(id::contains)
        val supportsJson = provider.supportsJsonMode || provider.protocol in setOf(
            ApiProtocol.OPENAI_COMPATIBLE,
            ApiProtocol.GEMINI,
            ApiProtocol.OLLAMA,
        )
        return ModelCapabilityProfile(
            providerId = provider.id,
            modelId = modelId,
            contextWindow = contextWindow,
            supportsStreaming = true,
            supportsJson = supportsJson,
            reasoning = reasoning,
            vision = vision,
            longText = longText,
            transportSupported = transportSupported(provider.baseUrl, modelId),
            estimated = discoveredContext == null,
        )
    }

    fun warnings(task: AiTaskType, profile: ModelCapabilityProfile): List<String> = buildList {
        if (!profile.transportSupported) add("当前接口需要不同的模型协议，琅嬛不会伪装请求")
        if (task in setOf(AiTaskType.PROSE_AUTHOR, AiTaskType.NOVELIZATION, AiTaskType.EDITOR_REWRITE) && !profile.longText) {
            add("未识别为长文本模型，长章可能更容易截断")
        }
        if (task in setOf(AiTaskType.EDITOR_REVIEW, AiTaskType.FACT_EXTRACTION, AiTaskType.AGENT_EXTRACTION) && !profile.supportsJson) {
            add("未识别到 JSON/结构化输出能力，将依赖文本 JSON 解析")
        }
        if (task in setOf(AiTaskType.SCENE_DIRECTOR, AiTaskType.EXECUTION_AUDIT, AiTaskType.AUTONOMOUS_PLANNER, AiTaskType.FULL_BOOK_EDITOR) && !profile.reasoning) {
            add("未识别为推理模型；可以使用，但规划/审计能力可能较弱")
        }
        if (task == AiTaskType.FULL_BOOK_EDITOR && profile.contextWindow in 1 until 128_000) {
            add("上下文窗口偏小，不适合较长的全书巡检输入")
        }
    }

    private fun estimateContext(id: String): Int = when {
        "gemini" in id -> 1_000_000
        "gpt-4.1" in id -> 1_000_000
        "claude" in id -> 200_000
        "deepseek" in id -> 128_000
        "qwen" in id -> 128_000
        "minimax" in id -> 128_000
        else -> 0
    }

    /** Keep parity with the existing quick-switch safety gate for mixed-protocol aggregators. */
    private fun transportSupported(baseUrl: String, modelId: String): Boolean {
        if (!baseUrl.contains("opencode.ai/zen/go", ignoreCase = true)) return true
        val id = modelId.substringAfterLast('/').lowercase()
        val responses = setOf("grok-4.6", "gpt-5.6-luna", "muse-spark-1.2-contributor")
        val messages = setOf(
            "minimax-m3", "minimax-m2.7", "minimax-m2.5",
            "qwen3.8-max", "qwen3.7-max", "qwen3.7-plus", "qwen3.6-plus",
        )
        return id !in responses && id !in messages
    }
}

data class ResolvedTaskModel(
    val task: AiTaskType,
    val provider: StoredAiProvider,
    val modelId: String,
    val profile: ModelCapabilityProfile,
    val inheritedGlobal: Boolean,
    val gateway: AiGateway,
) {
    val label: String get() = "${provider.name} · $modelId"
}

class TaskRoutingSession internal constructor(
    val defaultProvider: StoredAiProvider,
    val defaultGateway: AiGateway,
    private val selections: Map<AiTaskType, ResolvedTaskModel>,
    private val telemetry: AiModelTelemetryStore,
) {
    fun selection(task: AiTaskType): ResolvedTaskModel = selections[task]
        ?: ResolvedTaskModel(
            task = task,
            provider = defaultProvider,
            modelId = defaultProvider.model,
            profile = ModelCapabilityProfiler.infer(defaultProvider, defaultProvider.model),
            inheritedGlobal = true,
            gateway = defaultGateway,
        )

    fun routeSummary(): String {
        val overrides = selections.values.filterNot { it.inheritedGlobal }
        return if (overrides.isEmpty()) {
            "全局：${defaultProvider.name} · ${defaultProvider.model}"
        } else {
            "全局 ${defaultProvider.model} · ${overrides.size} 个任务覆盖"
        }
    }

    fun modelAttributions(): List<ModelUsageAttribution> = AiTaskType.entries.map { task ->
        val selected = selection(task)
        ModelUsageAttribution(task.name, selected.provider.id, selected.modelId)
    }.distinct()

    fun recordQualitySignal(task: AiTaskType, signal: AiQualitySignal) {
        val selected = selection(task)
        telemetry.recordSignal(
            ModelUsageAttribution(task.name, selected.provider.id, selected.modelId),
            signal,
        )
    }
}

class TaskModelRouter(context: Context) {
    private val app = context.applicationContext
    private val repository = PersistentStoryRepository(app)
    private val store = AiTaskRoutingStore(app)
    private val telemetry = AiModelTelemetryStore(app)
    private val skillStore = WritingSkillStore(app)

    suspend fun snapshot(): TaskRoutingSession {
        val providers = repository.observeProviders().first()
        val defaultProvider = providers.firstOrNull { it.isDefault } ?: providers.firstOrNull()
            ?: error("请先到设置添加并启用一个 AI 服务")
        val defaultGateway = gateway(defaultProvider, defaultProvider.model)
        val routes = store.routes()
        val skillSnapshot = skillStore.snapshot()
        val selections = linkedMapOf<AiTaskType, ResolvedTaskModel>()
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
            val baseGateway = if (!safe || (provider.id == defaultProvider.id && model == defaultProvider.model)) {
                defaultGateway
            } else {
                gateway(provider, model)
            }
            val attribution = ModelUsageAttribution(task.name, provider.id, model)
            selections[task] = ResolvedTaskModel(
                task = task,
                provider = provider,
                modelId = model,
                profile = profile,
                inheritedGlobal = !safe,
                gateway = TelemetryAiGateway(
                    SkillAwareAiGateway(baseGateway, task, skillSnapshot.forTask(task)),
                    attribution,
                    telemetry,
                ),
            )
        }
        return TaskRoutingSession(defaultProvider, defaultGateway, selections, telemetry)
    }

    private suspend fun gateway(provider: StoredAiProvider, modelId: String): UniversalAiGateway = UniversalAiGateway(
        AiProviderConfig(
            baseUrl = provider.baseUrl,
            apiKey = repository.apiKey(provider.id).orEmpty(),
            model = modelId,
            protocol = provider.protocol,
            temperature = provider.temperature,
            supportsJsonMode = provider.supportsJsonMode,
        )
    )
}

/**
 * AiGateway-compatible dispatcher so existing engines do not need routing branches sprinkled through
 * every call site. A session is frozen before a run, therefore changing settings mid-generation cannot
 * switch the model halfway through the same paid chapter run.
 */
class TaskDispatchingAiGateway(
    private val session: TaskRoutingSession,
) : AiGateway, AiTaskAttributionSource, AiTaskQualityFeedback {
    val summary: String get() = session.routeSummary()

    override suspend fun generate(prompt: PromptBundle) = gateway(prompt).generate(prompt)
    override suspend fun generateText(prompt: PromptBundle): String = gateway(prompt).generateText(prompt)
    override suspend fun generateStreaming(prompt: PromptBundle, onDelta: (String) -> Unit) =
        gateway(prompt).generateStreaming(prompt, onDelta)
    override suspend fun generateTextStreaming(prompt: PromptBundle, onDelta: (String) -> Unit): String =
        gateway(prompt).generateTextStreaming(prompt, onDelta)

    fun routeFor(prompt: PromptBundle): AiTaskType? = AiPromptTaskClassifier.classify(prompt)

    override fun modelAttributions(): List<ModelUsageAttribution> = session.modelAttributions()

    override fun recordQualitySignal(task: AiTaskType, signal: AiQualitySignal) {
        session.recordQualitySignal(task, signal)
    }

    private fun gateway(prompt: PromptBundle): AiGateway {
        val task = AiPromptTaskClassifier.classify(prompt) ?: return session.defaultGateway
        return session.selection(task).gateway
    }
}

object AiPromptTaskClassifier {
    fun classify(prompt: PromptBundle): AiTaskType? {
        val system = prompt.system
        val user = prompt.user
        return when {
            "小说化重构编辑" in system -> AiTaskType.NOVELIZATION
            "对抗式章节主编委员会" in system || "严苛的中文长篇小说章节主编" in system -> AiTaskType.EDITOR_REVIEW
            "章节事实提取器" in system -> AiTaskType.FACT_EXTRACTION
            "正文作者" in system && ("第二稿" in system || "主编退回意见" in user) -> AiTaskType.EDITOR_REWRITE
            "正文作者" in system -> AiTaskType.PROSE_AUTHOR
            "长篇小说创作 Agent" in system && ("本次是全书巡检" in system || "全书尺度巡检" in user) -> AiTaskType.FULL_BOOK_EDITOR
            "长篇小说创作 Agent" in system -> AiTaskType.AGENT_EXTRACTION
            containsAny(system, user, "计划与实际", "执行完成度", "偏航", "执行审计") -> AiTaskType.EXECUTION_AUDIT
            containsAny(system, user, "自治规划", "滚动计划", "未来 6 章", "未来6章", "局部重规划") -> AiTaskType.AUTONOMOUS_PLANNER
            containsAny(system, user, "场景导演", "场景计划", "编排本章场景", "下一章规划", "章纲导演") -> AiTaskType.SCENE_DIRECTOR
            else -> null
        }
    }

    private fun containsAny(system: String, user: String, vararg markers: String): Boolean =
        markers.any { it in system || it in user }
}
