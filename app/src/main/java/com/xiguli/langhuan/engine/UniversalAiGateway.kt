package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
enum class ApiProtocol(val label: String) {
    AUTO("自动识别"),
    OPENAI_COMPATIBLE("OpenAI 兼容"),
    ANTHROPIC("Claude / Anthropic"),
    GEMINI("Gemini"),
    AZURE_OPENAI("Azure OpenAI"),
    OLLAMA("Ollama"),
}

@Serializable
data class AiProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val protocol: ApiProtocol = ApiProtocol.AUTO,
    val temperature: Double = 0.72,
    val supportsJsonMode: Boolean = false,
)

@Serializable
data class DiscoveredModel(
    val id: String,
    val displayName: String = id,
    val reasoning: Boolean = false,
    val vision: Boolean = false,
)

@Serializable
data class ProviderDiscovery(
    val protocol: ApiProtocol,
    val providerLabel: String,
    val normalizedBaseUrl: String,
    val models: List<DiscoveredModel>,
    val supportsJsonMode: Boolean,
    val message: String,
)

private data class HttpResult(val status: Int, val body: String)

private val WireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

class ProviderAutoDetector {
    suspend fun detect(baseUrl: String, apiKey: String): ProviderDiscovery = withContext(Dispatchers.IO) {
        val normalized = normalizeBaseUrl(baseUrl)
        require(normalized.isNotBlank()) { "请输入中转站或官方 API 地址" }
        val order = candidateOrder(normalized)
        val errors = mutableListOf<String>()
        order.forEach { protocol ->
            val result = runCatching { discover(protocol, normalized, apiKey) }
                .onFailure { errors += "${protocol.label}: ${it.message.orEmpty()}" }
                .getOrNull()
            if (result != null) return@withContext result
        }

        val hinted = order.first()
        if (hinted != ApiProtocol.OPENAI_COMPATIBLE || looksLikeApiEndpoint(normalized)) {
            return@withContext ProviderDiscovery(
                protocol = hinted,
                providerLabel = providerName(hinted, normalized),
                normalizedBaseUrl = normalized,
                models = inferredModels(hinted, normalized),
                supportsJsonMode = hinted in setOf(ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.GEMINI),
                message = "已按地址识别 ${hinted.label}；服务未开放模型列表，可手动填写模型名",
            )
        }
        error(errors.lastOrNull() ?: "无法识别接口协议，请检查地址和密钥")
    }

    private fun discover(protocol: ApiProtocol, base: String, key: String): ProviderDiscovery? {
        if (protocol == ApiProtocol.AZURE_OPENAI) {
            val models = inferredModels(protocol, base)
            return ProviderDiscovery(
                protocol,
                "Azure OpenAI",
                base,
                models,
                false,
                if (models.isEmpty()) "已识别 Azure OpenAI，请填写部署名称" else "已从部署地址识别模型",
            )
        }
        val endpoint = modelEndpoint(protocol, base, key)
        val headers = authHeaders(protocol, key)
        val response = http(endpoint, "GET", headers)
        if (response.status !in 200..299) return null
        val root = WireJson.parseToJsonElement(response.body).jsonObject
        val models = when (protocol) {
            ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.ANTHROPIC ->
                root["data"].asObjects().mapNotNull { item -> item.string("id")?.toModel() }

            ApiProtocol.GEMINI -> root["models"].asObjects().mapNotNull { item ->
                val methods = item["supportedGenerationMethods"] as? JsonArray
                if (methods != null && methods.none { it.jsonPrimitive.contentOrNull == "generateContent" }) return@mapNotNull null
                item.string("name")?.removePrefix("models/")?.toModel(item.string("displayName"))
            }

            ApiProtocol.OLLAMA -> root["models"].asObjects().mapNotNull { item ->
                item.string("name")?.toModel(item.string("name"))
            }

            else -> emptyList()
        }.distinctBy { it.id }.sortedBy { it.id.lowercase() }

        return ProviderDiscovery(
            protocol = protocol,
            providerLabel = providerName(protocol, base),
            normalizedBaseUrl = base,
            models = models,
            supportsJsonMode = protocol in setOf(ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.GEMINI, ApiProtocol.OLLAMA),
            message = if (models.isEmpty()) "接口连通，但模型列表为空" else "已自动发现 ${models.size} 个模型",
        )
    }
}

class UniversalAiGateway(
    private val config: AiProviderConfig,
) : AiGateway {
    override suspend fun generate(prompt: PromptBundle): GeneratedChapter = withContext(Dispatchers.IO) {
        require(config.baseUrl.isNotBlank()) { "请先配置 API 地址" }
        require(config.model.isNotBlank()) { "请先选择或填写模型" }
        val protocol = resolvedProtocol()
        val response = when (protocol) {
            ApiProtocol.ANTHROPIC -> callAnthropic(prompt)
            ApiProtocol.GEMINI -> callGemini(prompt)
            ApiProtocol.AZURE_OPENAI -> callOpenAi(prompt, azure = true)
            ApiProtocol.OLLAMA -> callOllama(prompt)
            else -> callOpenAi(prompt, azure = false)
        }
        decodeChapter(extractText(protocol, response))
    }

    override suspend fun generateText(prompt: PromptBundle): String = withContext(Dispatchers.IO) {
        require(config.baseUrl.isNotBlank()) { "请先配置 API 地址" }
        require(config.model.isNotBlank()) { "请先选择或填写模型" }
        val protocol = resolvedProtocol()
        val response = when (protocol) {
            ApiProtocol.ANTHROPIC -> callAnthropic(prompt)
            ApiProtocol.GEMINI -> callGemini(prompt)
            ApiProtocol.AZURE_OPENAI -> callOpenAi(prompt, azure = true)
            ApiProtocol.OLLAMA -> callOllama(prompt)
            else -> callOpenAi(prompt, azure = false)
        }
        extractText(protocol, response)
    }

    override suspend fun generateStreaming(
        prompt: PromptBundle,
        onDelta: (String) -> Unit,
    ): GeneratedChapter {
        require(config.baseUrl.isNotBlank()) { "请先配置 API 地址" }
        require(config.model.isNotBlank()) { "请先选择或填写模型" }
        val protocol = resolvedProtocol()
        var emitted = false
        val guardedDelta: (String) -> Unit = { preview ->
            emitted = true
            onDelta(preview)
        }

        return try {
            val raw = when (protocol) {
                ApiProtocol.ANTHROPIC -> streamAnthropic(prompt, guardedDelta)
                ApiProtocol.GEMINI -> streamGemini(prompt, guardedDelta)
                ApiProtocol.AZURE_OPENAI -> streamOpenAi(prompt, azure = true, guardedDelta)
                ApiProtocol.OLLAMA -> streamOllama(prompt, guardedDelta)
                else -> streamOpenAi(prompt, azure = false, guardedDelta)
            }
            decodeChapter(raw)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (emitted) {
                throw IllegalStateException(
                    "流式连接在已经开始返回正文后中断。为避免重复扣费，琅嬛没有自动发起第二次请求；可手动重新生成。",
                    error,
                )
            }
            if (prompt.attachments.isNotEmpty()) {
                throw IllegalStateException(
                    "附件请求失败：当前模型或中转站可能不支持该图片/PDF输入格式。琅嬛没有自动重发，避免重复消耗。${error.message.orEmpty()}",
                    error,
                )
            }
            val chapter = generate(prompt)
            onDelta(chapter.content)
            chapter
        }
    }

    private fun resolvedProtocol(): ApiProtocol = if (config.protocol == ApiProtocol.AUTO) {
        candidateOrder(normalizeBaseUrl(config.baseUrl)).first()
    } else config.protocol

    private fun callOpenAi(prompt: PromptBundle, azure: Boolean): String {
        val endpoint = if (azure) azureChatEndpoint(config.baseUrl, config.model) else openAiChatEndpoint(config.baseUrl)
        val body = openAiBody(prompt, stream = false, azure = azure)
        val protocol = if (azure) ApiProtocol.AZURE_OPENAI else ApiProtocol.OPENAI_COMPATIBLE
        return requireSuccess(http(endpoint, "POST", authHeaders(protocol, config.apiKey), body.toString()))
    }

    private suspend fun streamOpenAi(prompt: PromptBundle, azure: Boolean, onDelta: (String) -> Unit): String {
        val endpoint = if (azure) azureChatEndpoint(config.baseUrl, config.model) else openAiChatEndpoint(config.baseUrl)
        val protocol = if (azure) ApiProtocol.AZURE_OPENAI else ApiProtocol.OPENAI_COMPATIBLE
        val buffer = StringBuilder()
        streamHttp(endpoint, authHeaders(protocol, config.apiKey), openAiBody(prompt, stream = true, azure = azure).toString()) { line ->
            val data = line.removePrefix("data:").trim()
            if (data.isBlank() || data == "[DONE]") return@streamHttp
            val root = runCatching { WireJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@streamHttp
            val delta = root["choices"].asObjects().firstOrNull()
                ?.get("delta")?.let { it as? JsonObject }?.string("content")
                ?: root["choices"].asObjects().firstOrNull()
                    ?.get("message")?.let { it as? JsonObject }?.string("content")
            appendDelta(buffer, delta, onDelta)
        }
        return buffer.toString().ifBlank { error("流式响应为空") }
    }

    private fun openAiBody(prompt: PromptBundle, stream: Boolean, azure: Boolean): JsonObject = buildJsonObject {
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("model", config.model)
        put("temperature", config.temperature)
        put("stream", stream)
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "assistant" else "user"
                val withAttachments = role == "user" && index == turns.lastIndex && prompt.attachments.isNotEmpty()
                add(buildJsonObject {
                    put("role", role)
                    if (!withAttachments) {
                        put("content", turn.content)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", turn.content) })
                            prompt.attachments.forEach { attachment ->
                                if (attachment.mimeType.startsWith("image/")) {
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                                        })
                                    })
                                } else {
                                    add(buildJsonObject {
                                        put("type", "file")
                                        put("file", buildJsonObject {
                                            put("filename", attachment.fileName)
                                            put("file_data", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                                        })
                                    })
                                }
                            }
                        })
                    }
                })
            }
        })
        if (prompt.jsonMode && config.supportsJsonMode && !azure) {
            put("response_format", buildJsonObject { put("type", "json_object") })
        }
    }

    private fun callAnthropic(prompt: PromptBundle): String {
        val body = anthropicBody(prompt, stream = false)
        return requireSuccess(http(anthropicMessagesEndpoint(config.baseUrl), "POST", authHeaders(ApiProtocol.ANTHROPIC, config.apiKey), body.toString()))
    }

    private suspend fun streamAnthropic(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val buffer = StringBuilder()
        streamHttp(
            anthropicMessagesEndpoint(config.baseUrl),
            authHeaders(ApiProtocol.ANTHROPIC, config.apiKey),
            anthropicBody(prompt, stream = true).toString(),
        ) { line ->
            val data = line.removePrefix("data:").trim()
            if (data.isBlank() || data == "[DONE]") return@streamHttp
            val root = runCatching { WireJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@streamHttp
            val delta = (root["delta"] as? JsonObject)?.string("text")
            appendDelta(buffer, delta, onDelta)
        }
        return buffer.toString().ifBlank { error("Claude 流式响应为空") }
    }

    private fun anthropicBody(prompt: PromptBundle, stream: Boolean): JsonObject = buildJsonObject {
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("model", config.model)
        put("max_tokens", 8192)
        put("temperature", config.temperature)
        put("stream", stream)
        put("system", prompt.system)
        put("messages", buildJsonArray {
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "assistant" else "user"
                val withAttachments = role == "user" && index == turns.lastIndex && prompt.attachments.isNotEmpty()
                add(buildJsonObject {
                    put("role", role)
                    if (!withAttachments) {
                        put("content", turn.content)
                    } else {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", turn.content) })
                            prompt.attachments.forEach { attachment ->
                                val kind = if (attachment.mimeType.startsWith("image/")) "image" else "document"
                                add(buildJsonObject {
                                    put("type", kind)
                                    put("source", buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", attachment.mimeType)
                                        put("data", attachment.base64Data)
                                    })
                                    if (kind == "document") put("title", attachment.fileName)
                                })
                            }
                        })
                    }
                })
            }
        })
    }

    private fun callGemini(prompt: PromptBundle): String {
        return requireSuccess(
            http(
                geminiGenerateEndpoint(config.baseUrl, config.model, config.apiKey),
                "POST",
                emptyMap(),
                geminiBody(prompt).toString(),
            )
        )
    }

    private suspend fun streamGemini(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val buffer = StringBuilder()
        streamHttp(
            geminiStreamEndpoint(config.baseUrl, config.model, config.apiKey),
            emptyMap(),
            geminiBody(prompt).toString(),
        ) { line ->
            val data = line.removePrefix("data:").trim()
            if (data.isBlank()) return@streamHttp
            val root = runCatching { WireJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return@streamHttp
            val delta = root["candidates"].asObjects().firstOrNull()
                ?.get("content")?.let { it as? JsonObject }?.get("parts").asObjects()
                .orEmpty().joinToString("") { it.string("text").orEmpty() }
            appendDelta(buffer, delta, onDelta)
        }
        return buffer.toString().ifBlank { error("Gemini 流式响应为空") }
    }

    private fun geminiBody(prompt: PromptBundle): JsonObject = buildJsonObject {
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("system_instruction", buildJsonObject {
            put("parts", buildJsonArray { add(buildJsonObject { put("text", prompt.system) }) })
        })
        put("contents", buildJsonArray {
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "model" else "user"
                add(buildJsonObject {
                    put("role", role)
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", turn.content) })
                        if (role == "user" && index == turns.lastIndex) {
                            prompt.attachments.forEach { attachment ->
                                add(buildJsonObject {
                                    put("inline_data", buildJsonObject {
                                        put("mime_type", attachment.mimeType)
                                        put("data", attachment.base64Data)
                                    })
                                })
                            }
                        }
                    })
                })
            }
        })
        put("generationConfig", buildJsonObject {
            put("temperature", config.temperature)
            if (prompt.jsonMode) put("responseMimeType", "application/json")
        })
    }

    private fun callOllama(prompt: PromptBundle): String {
        return requireSuccess(
            http(
                ollamaChatEndpoint(config.baseUrl),
                "POST",
                emptyMap(),
                ollamaBody(prompt, stream = false).toString(),
            )
        )
    }

    private suspend fun streamOllama(prompt: PromptBundle, onDelta: (String) -> Unit): String {
        val buffer = StringBuilder()
        streamHttp(ollamaChatEndpoint(config.baseUrl), emptyMap(), ollamaBody(prompt, stream = true).toString()) { line ->
            val root = runCatching { WireJson.parseToJsonElement(line.trim()).jsonObject }.getOrNull() ?: return@streamHttp
            val delta = (root["message"] as? JsonObject)?.string("content")
            appendDelta(buffer, delta, onDelta)
        }
        return buffer.toString().ifBlank { error("Ollama 流式响应为空") }
    }

    private fun ollamaBody(prompt: PromptBundle, stream: Boolean): JsonObject = buildJsonObject {
        require(prompt.attachments.all { it.mimeType.startsWith("image/") }) {
            "当前 Ollama 对话只支持图片附件；PDF 请切换支持文档输入的 OpenAI、Claude 或 Gemini 模型。"
        }
        val turns = prompt.messages.ifEmpty { listOf(PromptMessage("user", prompt.user)) }
        put("model", config.model)
        put("stream", stream)
        if (prompt.jsonMode) put("format", "json")
        put("messages", buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
            turns.forEachIndexed { index, turn ->
                val role = if (turn.role.equals("assistant", true)) "assistant" else "user"
                add(buildJsonObject {
                    put("role", role)
                    put("content", turn.content)
                    if (role == "user" && index == turns.lastIndex && prompt.attachments.isNotEmpty()) {
                        put("images", buildJsonArray { prompt.attachments.forEach { add(JsonPrimitive(it.base64Data)) } })
                    }
                })
            }
        })
        put("options", buildJsonObject { put("temperature", config.temperature) })
    }

    private fun appendDelta(buffer: StringBuilder, delta: String?, onDelta: (String) -> Unit) {
        if (delta.isNullOrEmpty()) return
        buffer.append(delta)
        onDelta(chapterContentPreview(buffer.toString()))
    }

    private fun extractText(protocol: ApiProtocol, body: String): String {
        val root = WireJson.parseToJsonElement(body).jsonObject
        return when (protocol) {
            ApiProtocol.ANTHROPIC -> root["content"].asObjects().firstNotNullOfOrNull { it.string("text") }
            ApiProtocol.GEMINI -> root["candidates"].asObjects().firstOrNull()
                ?.get("content")?.let { it as? JsonObject }?.get("parts").asObjects()
                .orEmpty().joinToString("") { it.string("text").orEmpty() }

            ApiProtocol.OLLAMA -> root["message"]?.jsonObject?.string("content")
            else -> root["choices"].asObjects().firstOrNull()
                ?.get("message")?.jsonObject?.string("content")
                ?: root.string("output_text")
        }.orEmpty().ifBlank { error("AI 返回内容为空或协议格式不兼容") }
    }

    private fun decodeChapter(raw: String): GeneratedChapter {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        val jsonBody = if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
        return WireJson.decodeFromString<GeneratedChapter>(jsonBody)
    }
}

private fun http(url: String, method: String, headers: Map<String, String>, body: String? = null): HttpResult {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = method
        connection.connectTimeout = 25_000
        connection.readTimeout = 180_000
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach(connection::setRequestProperty)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        }
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        HttpResult(status, text)
    } finally {
        connection.disconnect()
    }
}

private suspend fun streamHttp(
    url: String,
    headers: Map<String, String>,
    body: String,
    onLine: (String) -> Unit,
) = runInterruptible(Dispatchers.IO) {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 25_000
        connection.readTimeout = 180_000
        connection.doOutput = true
        connection.setRequestProperty("Accept", "text/event-stream, application/x-ndjson, application/json")
        connection.setRequestProperty("Content-Type", "application/json")
        headers.forEach(connection::setRequestProperty)
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        val status = connection.responseCode
        if (status !in 200..299) {
            val error = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            error("AI 服务返回 $status：${error.take(500)}")
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (Thread.currentThread().isInterrupted) throw InterruptedException("AI stream cancelled")
                if (line.isNotBlank() && !line.startsWith("event:")) onLine(line)
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun chapterContentPreview(raw: String): String {
    val match = Regex("\\\"content\\\"\\s*:\\s*\\\"").find(raw) ?: return "已接收 ${raw.length} 个字符…"
    val value = raw.substring(match.range.last + 1)
    val out = StringBuilder()
    var escaped = false
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (escaped) {
            when (c) {
                'n' -> out.append('\n')
                'r' -> Unit
                't' -> out.append('\t')
                '\\' -> out.append('\\')
                '"' -> out.append('"')
                else -> out.append(c)
            }
            escaped = false
        } else if (c == '\\') {
            escaped = true
        } else if (c == '"') {
            break
        } else {
            out.append(c)
        }
        i++
    }
    return out.toString().ifBlank { "正在生成正文…" }.takeLast(5_000)
}

private fun requireSuccess(result: HttpResult): String {
    check(result.status in 200..299) { "AI 服务返回 ${result.status}：${result.body.take(500)}" }
    return result.body
}

private fun authHeaders(protocol: ApiProtocol, key: String): Map<String, String> = when (protocol) {
    ApiProtocol.ANTHROPIC -> mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")
    ApiProtocol.AZURE_OPENAI -> mapOf("api-key" to key)
    ApiProtocol.GEMINI, ApiProtocol.OLLAMA -> emptyMap()
    else -> if (key.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $key")
}

private fun candidateOrder(base: String): List<ApiProtocol> {
    val value = base.lowercase()
    val first = when {
        "generativelanguage.googleapis.com" in value || "/v1beta" in value -> ApiProtocol.GEMINI
        "anthropic" in value || value.endsWith("/messages") -> ApiProtocol.ANTHROPIC
        "azure.com" in value || ("/openai/deployments/" in value && "api-version=" in value) -> ApiProtocol.AZURE_OPENAI
        ":11434" in value || value.endsWith("/api/tags") || value.endsWith("/api/chat") -> ApiProtocol.OLLAMA
        else -> ApiProtocol.OPENAI_COMPATIBLE
    }
    return listOf(first, ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.ANTHROPIC, ApiProtocol.GEMINI, ApiProtocol.OLLAMA)
        .distinct()
}

private fun normalizeBaseUrl(input: String): String = input.trim().trimEnd('/')

private fun modelEndpoint(protocol: ApiProtocol, base: String, key: String): String = when (protocol) {
    ApiProtocol.OPENAI_COMPATIBLE -> when {
        base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/models"
        base.endsWith("/responses") -> base.removeSuffix("/responses") + "/models"
        base.endsWith("/models") -> base
        base.endsWith("/v1") -> "$base/models"
        else -> "$base/v1/models"
    }

    ApiProtocol.ANTHROPIC -> when {
        base.endsWith("/messages") -> base.removeSuffix("/messages") + "/models"
        base.endsWith("/models") -> base
        base.endsWith("/v1") -> "$base/models"
        else -> "$base/v1/models"
    }

    ApiProtocol.GEMINI -> {
        val root = base.substringBefore("/v1beta").trimEnd('/')
        "$root/v1beta/models?key=${urlEncode(key)}"
    }

    ApiProtocol.OLLAMA -> when {
        base.endsWith("/api/chat") -> base.removeSuffix("/api/chat") + "/api/tags"
        base.endsWith("/api/tags") -> base
        else -> "$base/api/tags"
    }

    else -> base
}

private fun openAiChatEndpoint(input: String): String {
    val base = normalizeBaseUrl(input)
    return when {
        "/chat/completions" in base -> base
        base.endsWith("/responses") -> base.removeSuffix("/responses") + "/chat/completions"
        base.endsWith("/v1") -> "$base/chat/completions"
        else -> "$base/v1/chat/completions"
    }
}

private fun anthropicMessagesEndpoint(input: String): String {
    val base = normalizeBaseUrl(input)
    return when {
        base.endsWith("/messages") -> base
        base.endsWith("/v1") -> "$base/messages"
        else -> "$base/v1/messages"
    }
}

private fun geminiGenerateEndpoint(input: String, model: String, key: String): String {
    val root = normalizeBaseUrl(input).substringBefore("/v1beta").trimEnd('/')
    return "$root/v1beta/models/${urlEncode(model.removePrefix("models/"))}:generateContent?key=${urlEncode(key)}"
}

private fun geminiStreamEndpoint(input: String, model: String, key: String): String {
    val root = normalizeBaseUrl(input).substringBefore("/v1beta").trimEnd('/')
    return "$root/v1beta/models/${urlEncode(model.removePrefix("models/"))}:streamGenerateContent?alt=sse&key=${urlEncode(key)}"
}

private fun azureChatEndpoint(input: String, deployment: String): String {
    val base = normalizeBaseUrl(input)
    if ("/chat/completions" in base && "api-version=" in base) return base
    val root = base.substringBefore("/openai/").trimEnd('/')
    return "$root/openai/deployments/${urlEncode(deployment)}/chat/completions?api-version=2024-10-21"
}

private fun ollamaChatEndpoint(input: String): String {
    val base = normalizeBaseUrl(input)
    return when {
        base.endsWith("/api/chat") -> base
        base.endsWith("/api/tags") -> base.removeSuffix("/api/tags") + "/api/chat"
        else -> "$base/api/chat"
    }
}

private fun inferredModels(protocol: ApiProtocol, base: String): List<DiscoveredModel> {
    if (protocol != ApiProtocol.AZURE_OPENAI) return emptyList()
    val deployment = base.substringAfter("/deployments/", "").substringBefore('/').substringBefore('?')
    return if (deployment.isBlank()) emptyList() else listOf(deployment.toModel())
}

private fun providerName(protocol: ApiProtocol, base: String): String {
    val host = runCatching { URI(base).host.orEmpty() }.getOrDefault("")
    return when {
        "deepseek" in host -> "DeepSeek"
        "openai" in host && protocol != ApiProtocol.AZURE_OPENAI -> "OpenAI"
        "anthropic" in host -> "Anthropic"
        "googleapis" in host -> "Google Gemini"
        protocol == ApiProtocol.AZURE_OPENAI -> "Azure OpenAI"
        protocol == ApiProtocol.OLLAMA -> "Ollama"
        else -> host.ifBlank { "自定义中转站" }
    }
}

private fun looksLikeApiEndpoint(value: String): Boolean =
    value.startsWith("http://") || value.startsWith("https://")

private fun String.toModel(display: String? = null): DiscoveredModel {
    val lower = lowercase()
    return DiscoveredModel(
        id = this,
        displayName = display?.takeIf { it.isNotBlank() } ?: this,
        reasoning = listOf("reason", "o1", "o3", "o4", "r1", "thinking").any(lower::contains),
        vision = listOf("vision", "vl", "gemini", "gpt-4o", "claude-3").any(lower::contains),
    )
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonElement?.asObjects(): List<JsonObject> =
    (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
