from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'missing anchor: {path}')
    p.write_text(text.replace(old, new, 1))

replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiModelTelemetry.kt',
    '''private data class ModelTelemetryConfig(\n    val stats: List<ModelTaskTelemetry> = emptyList(),\n)''',
    '''private data class ModelTelemetryConfig(\n    val stats: List<ModelTaskTelemetry> = emptyList(),\n    /** Bounded idempotency ledger for successful formal-save adoption signals. */\n    val acceptedKeys: List<String> = emptyList(),\n)''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/AiModelTelemetry.kt',
    '''    @Synchronized\n    fun recordSignal(attribution: ModelUsageAttribution, signal: AiQualitySignal) {\n''',
    '''    @Synchronized\n    fun recordUserAccepted(attribution: ModelUsageAttribution, acceptanceKey: String) {\n        if (acceptanceKey.isBlank()) return\n        val task = runCatching { AiTaskType.valueOf(attribution.task) }.getOrNull() ?: return\n        val current = load()\n        val scopedKey = "${attribution.providerId}|${attribution.modelId}|${attribution.task}|$acceptanceKey"\n        if (scopedKey in current.acceptedKeys) return\n        val old = current.stats.lastOrNull {\n            it.providerId == attribution.providerId && it.modelId == attribution.modelId && it.task == task\n        } ?: ModelTaskTelemetry(attribution.providerId, attribution.modelId, task)\n        val next = old.copy(userAccepted = old.userAccepted + 1, updatedAt = System.currentTimeMillis())\n        val kept = current.stats.filterNot {\n            it.providerId == next.providerId && it.modelId == next.modelId && it.task == next.task\n        }\n        save(\n            current.copy(\n                stats = (kept + next).sortedByDescending { it.updatedAt }.take(500),\n                acceptedKeys = (current.acceptedKeys + scopedKey).takeLast(2_000),\n            )\n        )\n    }\n\n    @Synchronized\n    fun recordSignal(attribution: ModelUsageAttribution, signal: AiQualitySignal) {\n''',
)
replace(
    'app/src/main/java/com/xiguli/langhuan/engine/ChapterRunRuntime.kt',
    '''            command.result.modelAttributions\n                .firstOrNull { it.task == AiTaskType.PROSE_AUTHOR.name }\n                ?.let { modelTelemetry.recordSignal(it, AiQualitySignal.USER_ACCEPTED) }\n''',
    '''            command.result.modelAttributions\n                .firstOrNull { it.task == AiTaskType.PROSE_AUTHOR.name }\n                ?.let { attribution ->\n                    val acceptanceKey = listOf(\n                        command.novelId,\n                        command.chapterNumber.toString(),\n                        command.draft.version.toString(),\n                        command.result.chapter.content.hashCode().toString(),\n                    ).joinToString(":")\n                    modelTelemetry.recordUserAccepted(attribution, acceptanceKey)\n                }\n''',
)
print('acceptance dedupe applied')
