package com.xiguli.langhuan.engine

import com.xiguli.langhuan.domain.GeneratedChapter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AiProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.72,
    val supportsJsonMode: Boolean = false,
)

@Serializable
private data class ChatMessage(val role: String, val content: String)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val response_format: ResponseFormat? = null,
)

@Serializable
private data class ResponseFormat(val type: String = "json_object")

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(val message: ChatMessage)

/**
 * 兼容 OpenAI Chat Completions 协议，可用于 OpenAI、DeepSeek 及大多数中转站。
 * 密钥应由设置层从 Android Keystore 读取，不应写进源码或普通数据库。
 */
class OpenAiCompatibleGateway(
    private val config: AiProviderConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : AiGateway {
    override suspend fun generate(prompt: PromptBundle): GeneratedChapter = withContext(Dispatchers.IO) {
        require(config.apiKey.isNotBlank()) { "请先配置 API 密钥" }
        require(config.model.isNotBlank()) { "请先选择模型" }

        val endpoint = config.baseUrl.trimEnd('/').let { base ->
            if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
        }
        val payload = ChatRequest(
            model = config.model,
            messages = listOf(
                ChatMessage("system", prompt.system),
                ChatMessage("user", prompt.user),
            ),
            temperature = config.temperature,
            response_format = if (config.supportsJsonMode) ResponseFormat() else null,
        )

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(json.encodeToString(payload))
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            check(status in 200..299) {
                "AI 服务返回 $status：${body.take(500)}"
            }

            val content = json.decodeFromString<ChatResponse>(body)
                .choices.firstOrNull()?.message?.content
                ?.removePrefix("```json")
                ?.removePrefix("```")
                ?.removeSuffix("```")
                ?.trim()
                .orEmpty()
            check(content.isNotBlank()) { "AI 返回内容为空" }
            json.decodeFromString<GeneratedChapter>(content)
        } finally {
            connection.disconnect()
        }
    }
}
