from pathlib import Path

ROOT = Path('.')


def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'missing patch anchor in {path}: {old[:100]!r}')
    p.write_text(text.replace(old, new, 1))


# Domain attribution survives generation -> commit and can be serialized in durable checkpoints.
replace(
    'app/src/main/java/com/xiguli/langhuan/domain/GenerationModels.kt',
    '''@Serializable\ndata class StateChange(\n    val subject: String = "",\n    val field: String = "",\n    val before: String = "",\n    val after: String = "",\n    val evidence: String = "",\n)\n''',
    '''@Serializable\ndata class StateChange(\n    val subject: String = "",\n    val field: String = "",\n    val before: String = "",\n    val after: String = "",\n    val evidence: String = "",\n)\n\n/** Operational attribution only; never Canon/RAG. */\n@Serializable\ndata class ModelUsageAttribution(\n    val task: String,\n    val providerId: String,\n    val modelId: String,\n)\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/domain/GenerationModels.kt',
    '''data class GenerationResult(\n    val chapter: GeneratedChapter,\n    val issues: List<ConsistencyIssue>,\n) {''',
    '''data class GenerationResult(\n    val chapter: GeneratedChapter,\n    val issues: List<ConsistencyIssue>,\n    /** Frozen task/provider/model map from the run that produced this result. */\n    val modelAttributions: List<ModelUsageAttribution> = emptyList(),\n) {''',
)

# Crash-safe attribution + exactly-once quality-signal ledger.
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/ChapterRunCheckpointStore.kt',
    '''    val metadataSucceeded: Boolean = false,\n    val metadata: com.xiguli.langhuan.domain.GeneratedChapter? = null,\n)''',
    '''    val metadataSucceeded: Boolean = false,\n    val metadata: com.xiguli.langhuan.domain.GeneratedChapter? = null,\n    val modelAttributions: List<com.xiguli.langhuan.domain.ModelUsageAttribution> = emptyList(),\n    val telemetrySignals: List<String> = emptyList(),\n)''',
)

# Task router wraps each selected lane with empirical telemetry and exposes frozen attribution/feedback.
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''import com.xiguli.langhuan.data.StoredAiProvider\n''',
    '''import com.xiguli.langhuan.data.StoredAiProvider\nimport com.xiguli.langhuan.domain.ModelUsageAttribution\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''class TaskRoutingSession internal constructor(\n    val defaultProvider: StoredAiProvider,\n    val defaultGateway: AiGateway,\n    private val selections: Map<AiTaskType, ResolvedTaskModel>,\n) {''',
    '''class TaskRoutingSession internal constructor(\n    val defaultProvider: StoredAiProvider,\n    val defaultGateway: AiGateway,\n    private val selections: Map<AiTaskType, ResolvedTaskModel>,\n    private val telemetry: AiModelTelemetryStore,\n) {''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''    fun routeSummary(): String {\n        val overrides = selections.values.filterNot { it.inheritedGlobal }\n        return if (overrides.isEmpty()) {\n            "全局：${defaultProvider.name} · ${defaultProvider.model}"\n        } else {\n            "全局 ${defaultProvider.model} · ${overrides.size} 个任务覆盖"\n        }\n    }\n}''',
    '''    fun routeSummary(): String {\n        val overrides = selections.values.filterNot { it.inheritedGlobal }\n        return if (overrides.isEmpty()) {\n            "全局：${defaultProvider.name} · ${defaultProvider.model}"\n        } else {\n            "全局 ${defaultProvider.model} · ${overrides.size} 个任务覆盖"\n        }\n    }\n\n    fun modelAttributions(): List<ModelUsageAttribution> = AiTaskType.entries.map { task ->\n        val selected = selection(task)\n        ModelUsageAttribution(task.name, selected.provider.id, selected.modelId)\n    }.distinct()\n\n    fun recordQualitySignal(task: AiTaskType, signal: AiQualitySignal) {\n        val selected = selection(task)\n        telemetry.recordSignal(\n            ModelUsageAttribution(task.name, selected.provider.id, selected.modelId),\n            signal,\n        )\n    }\n}''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''    private val repository = PersistentStoryRepository(app)\n    private val store = AiTaskRoutingStore(app)\n''',
    '''    private val repository = PersistentStoryRepository(app)\n    private val store = AiTaskRoutingStore(app)\n    private val telemetry = AiModelTelemetryStore(app)\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''            val selectedGateway = if (!safe || (provider.id == defaultProvider.id && model == defaultProvider.model)) {\n                defaultGateway\n            } else {\n                gateway(provider, model)\n            }\n            selections[task] = ResolvedTaskModel(\n                task = task,\n                provider = provider,\n                modelId = model,\n                profile = profile,\n                inheritedGlobal = !safe,\n                gateway = selectedGateway,\n            )\n        }\n        return TaskRoutingSession(defaultProvider, defaultGateway, selections)\n''',
    '''            val baseGateway = if (!safe || (provider.id == defaultProvider.id && model == defaultProvider.model)) {\n                defaultGateway\n            } else {\n                gateway(provider, model)\n            }\n            val attribution = ModelUsageAttribution(task.name, provider.id, model)\n            selections[task] = ResolvedTaskModel(\n                task = task,\n                provider = provider,\n                modelId = model,\n                profile = profile,\n                inheritedGlobal = !safe,\n                gateway = TelemetryAiGateway(baseGateway, attribution, telemetry),\n            )\n        }\n        return TaskRoutingSession(defaultProvider, defaultGateway, selections, telemetry)\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''class TaskDispatchingAiGateway(\n    private val session: TaskRoutingSession,\n) : AiGateway {''',
    '''class TaskDispatchingAiGateway(\n    private val session: TaskRoutingSession,\n) : AiGateway, AiTaskAttributionSource, AiTaskQualityFeedback {''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiTaskRouting.kt',
    '''    fun routeFor(prompt: PromptBundle): AiTaskType? = AiPromptTaskClassifier.classify(prompt)\n\n    private fun gateway(prompt: PromptBundle): AiGateway {''',
    '''    fun routeFor(prompt: PromptBundle): AiTaskType? = AiPromptTaskClassifier.classify(prompt)\n\n    override fun modelAttributions(): List<ModelUsageAttribution> = session.modelAttributions()\n\n    override fun recordQualitySignal(task: AiTaskType, signal: AiQualitySignal) {\n        session.recordQualitySignal(task, signal)\n    }\n\n    private fun gateway(prompt: PromptBundle): AiGateway {''',
)

# Persist frozen attribution before the first paid call and record quality outcomes once across resume.
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt',
    '''        fun persist(next: GenerationStageCheckpoint) {\n            checkpoint = next\n            onCheckpoint(next)\n        }\n\n        // 1) Draft prose.''',
    '''        fun persist(next: GenerationStageCheckpoint) {\n            checkpoint = next\n            onCheckpoint(next)\n        }\n        if (checkpoint.modelAttributions.isEmpty()) {\n            val frozen = (aiGateway as? AiTaskAttributionSource)?.modelAttributions().orEmpty()\n            if (frozen.isNotEmpty()) persist(checkpoint.copy(modelAttributions = frozen))\n        }\n        fun signalOnce(task: AiTaskType, signal: AiQualitySignal) {\n            val feedback = aiGateway as? AiTaskQualityFeedback ?: return\n            val key = "${task.name}:${signal.name}"\n            if (key in checkpoint.telemetrySignals) return\n            feedback.recordQualitySignal(task, signal)\n            persist(checkpoint.copy(telemetrySignals = (checkpoint.telemetrySignals + key).distinct()))\n        }\n\n        // 1) Draft prose.''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt',
    '''        val initialQuality = novelizationEngine.analyze(draftProse)\n        var prose = draftProse\n''',
    '''        val initialQuality = novelizationEngine.analyze(draftProse)\n        if (initialQuality.requiresNovelization) {\n            signalOnce(AiTaskType.PROSE_AUTHOR, AiQualitySignal.NOVELIZATION_REQUIRED)\n        }\n        var prose = draftProse\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt',
    '''        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()\n        emit(''',
    '''        val firstRejected = adversarialEditor.requestsRewrite(firstReview) || firstDeterministic.isNotEmpty()\n        if (firstReview != null || firstDeterministic.isNotEmpty()) {\n            signalOnce(\n                AiTaskType.PROSE_AUTHOR,\n                if (firstRejected) AiQualitySignal.QUALITY_REJECTED else AiQualitySignal.QUALITY_PASSED,\n            )\n        }\n        if (firstRejected) signalOnce(AiTaskType.PROSE_AUTHOR, AiQualitySignal.REWRITE_REQUIRED)\n        emit(''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt',
    '''        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }\n        emit(''',
    '''        val warningCount = issues.count { it.severity == IssueSeverity.WARNING }\n        if (blockingCount > 0) signalOnce(AiTaskType.PROSE_AUTHOR, AiQualitySignal.PIPELINE_BLOCKED)\n        emit(''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/GenerationPipeline.kt',
    '''        return GenerationResult(chapter = chapter, issues = issues)''',
    '''        return GenerationResult(\n            chapter = chapter,\n            issues = issues,\n            modelAttributions = checkpoint.modelAttributions,\n        )''',
)

# Successful formal save counts as user adoption for the frozen prose-author attribution.
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/ChapterRunRuntime.kt',
    '''    private val repository = PersistentStoryRepository(app)\n    private val projects = StoryProjectManager(app)\n''',
    '''    private val repository = PersistentStoryRepository(app)\n    private val projects = StoryProjectManager(app)\n    private val modelTelemetry = AiModelTelemetryStore(app)\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/ChapterRunRuntime.kt',
    '''            val outcome = coordinator.commit(\n                snapshot = command.snapshot,\n                draft = command.draft,\n                result = command.result,\n                gateway = gateway,\n                onRunEvent = { event -> emit(command, event, canStop = false) },\n            )\n            applyPersistedOutcome(''',
    '''            val outcome = coordinator.commit(\n                snapshot = command.snapshot,\n                draft = command.draft,\n                result = command.result,\n                gateway = gateway,\n                onRunEvent = { event -> emit(command, event, canStop = false) },\n            )\n            command.result.modelAttributions\n                .firstOrNull { it.task == AiTaskType.PROSE_AUTHOR.name }\n                ?.let { modelTelemetry.recordSignal(it, AiQualitySignal.USER_ACCEPTED) }\n            applyPersistedOutcome(''',
)

# Settings UI: real recommendation plus per-model measured metrics. No monetary estimate is fabricated.
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''import com.xiguli.langhuan.engine.AiTaskRoutingStore\nimport com.xiguli.langhuan.engine.AiTaskType\n''',
    '''import com.xiguli.langhuan.engine.AiModelTelemetryStore\nimport com.xiguli.langhuan.engine.AiTaskRoutingStore\nimport com.xiguli.langhuan.engine.AiTaskType\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''import com.xiguli.langhuan.engine.ModelCapabilityProfile\nimport com.xiguli.langhuan.engine.ModelCapabilityProfiler\n''',
    '''import com.xiguli.langhuan.engine.ModelCapabilityProfile\nimport com.xiguli.langhuan.engine.ModelCapabilityProfiler\nimport com.xiguli.langhuan.engine.ModelRecommendation\nimport com.xiguli.langhuan.engine.ModelTaskTelemetry\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''    val error: String? = null,\n)''',
    '''    val error: String? = null,\n    val recommendations: Map<AiTaskType, ModelRecommendation> = emptyMap(),\n)''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''    private val detector = ProviderAutoDetector()\n    private val routing = AiTaskRoutingStore(application)\n''',
    '''    private val detector = ProviderAutoDetector()\n    private val routing = AiTaskRoutingStore(application)\n    private val telemetry = AiModelTelemetryStore(application)\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''                    current.copy(providers = providers, selectedProviderId = selected, routes = routing.routes())\n''',
    '''                    current.copy(\n                        providers = providers,\n                        selectedProviderId = selected,\n                        routes = routing.routes(),\n                        recommendations = AiTaskType.entries.mapNotNull { task ->\n                            telemetry.recommendation(task, providers)?.let { task to it }\n                        }.toMap(),\n                    )\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''    fun profile(provider: StoredAiProvider, model: DiscoveredModel): ModelCapabilityProfile =\n        routing.profile(provider, model.id, model)\n\n    private fun loadModels(providerId: String) {''',
    '''    fun profile(provider: StoredAiProvider, model: DiscoveredModel): ModelCapabilityProfile =\n        routing.profile(provider, model.id, model)\n\n    fun taskStats(task: AiTaskType, providerId: String, modelId: String): ModelTaskTelemetry? =\n        telemetry.stats(task).firstOrNull { it.providerId == providerId && it.modelId == modelId }\n\n    private fun loadModels(providerId: String) {''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''                        Text(task.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n\n                        if (expanded) {''',
    '''                        Text(task.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)\n                        state.recommendations[task]?.let { recommendation ->\n                            Text(\n                                "实测推荐：${recommendation.providerName} · ${recommendation.modelId} · ${recommendation.score}分 · ${recommendation.confidence}",\n                                style = MaterialTheme.typography.labelSmall,\n                                color = MaterialTheme.colorScheme.primary,\n                            )\n                            Text(\n                                recommendation.reason,\n                                style = MaterialTheme.typography.labelSmall,\n                                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                            )\n                        } ?: Text(\n                            "实测推荐：暂无真实运行样本，先按能力画像选择；琅嬛不会自动换模型。",\n                            style = MaterialTheme.typography.labelSmall,\n                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        )\n\n                        if (expanded) {''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''                                            if (warnings.isNotEmpty()) {\n                                                Text(\n                                                    warnings.joinToString("；"),\n                                                    style = MaterialTheme.typography.labelSmall,\n                                                    color = if (profile.transportSupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,\n                                                )\n                                            }\n''',
    '''                                            val measured = viewModel.taskStats(task, selectedProvider.id, model.id)\n                                            if (measured != null) {\n                                                val firstToken = measured.averageFirstTokenMs.takeIf { it > 0 }?.let { " · 首字 ${it}ms" }.orEmpty()\n                                                val throughput = measured.charsPerSecond.takeIf { it > 0 }?.let { " · ${it.toInt()}字/s" }.orEmpty()\n                                                val quality = if (task == AiTaskType.PROSE_AUTHOR && measured.qualitySamples > 0) {\n                                                    " · 一审通过 ${(measured.qualityPassRate * 100).toInt()}% · 已采用 ${measured.userAccepted}次"\n                                                } else if (measured.structuredAttempts > 0) {\n                                                    " · 结构化成功 ${(measured.structuredRate * 100).toInt()}%"\n                                                } else ""\n                                                Text(\n                                                    "实测 ${measured.calls} 次 · 成功 ${(measured.successRate * 100).toInt()}%$firstToken$throughput$quality",\n                                                    style = MaterialTheme.typography.labelSmall,\n                                                    color = MaterialTheme.colorScheme.primary,\n                                                )\n                                            }\n                                            if (warnings.isNotEmpty()) {\n                                                Text(\n                                                    warnings.joinToString("；"),\n                                                    style = MaterialTheme.typography.labelSmall,\n                                                    color = if (profile.transportSupported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,\n                                                )\n                                            }\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/ui/TaskModelRoutingPanel.kt',
    '''                "能力画像中的上下文窗口为保守估算值；中转站没有返回官方参数时会标记为未知或估算，不会当作官方规格。",''',
    '''                "能力画像中的上下文窗口为保守估算值；实测分数只来自本机真实 Run。接口未返回 token usage / 价格时不伪造金额成本，琅嬛也不会根据推荐自动切换模型。",''',
)

# Version.
replace(
    'app/build.gradle.kts',
    '''        versionCode = 67\n        versionName = "0.26.9-alpha01"''',
    '''        versionCode = 68\n        versionName = "0.27.0-alpha01"''',
)

print('model telemetry patch applied')
