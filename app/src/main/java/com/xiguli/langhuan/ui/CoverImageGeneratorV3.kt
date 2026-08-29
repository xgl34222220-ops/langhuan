package com.xiguli.langhuan.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.PersistentStoryRepository
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.ApiProtocol
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.ProviderAutoDetector
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val CoverWireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

data class CoverGenerationUiState(
    val busy: Boolean = false,
    val candidatePath: String? = null,
    val sourceLabel: String = "",
    val notice: String? = null,
    val error: String? = null,
)

private data class GeneratedCoverCandidate(
    val path: String,
    val sourceLabel: String,
    val notice: String,
)

/**
 * V3 封面生成：
 * 1. 自动发现当前 AI 服务里的图像生成模型；
 * 2. 图像模型只负责“无文字背景图”，避免中文乱码；
 * 3. 书名/类型由琅嬛本地排版；
 * 4. 服务没有图像模型或图片接口不兼容时，明确降级到干净的本地封面模板。
 *
 * 生成结果先写 candidates 目录，只有用户点“设为当前封面”后才进入正式 coverPath。
 */
class CoverStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val generator = CoverImageGeneratorV3()
    private val _state = MutableStateFlow(CoverGenerationUiState())
    val state: StateFlow<CoverGenerationUiState> = _state.asStateFlow()
    private var activeProviderId: String? = null

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { providers ->
                activeProviderId = providers.firstOrNull { it.isDefault }?.id ?: providers.firstOrNull()?.id
            }
        }
    }

    fun generate(book: ReaderBookUi) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = null, error = null) }
            runCatching {
                val config = activeProviderId?.let { repository.providerConfig(it) }
                generator.generate(getApplication(), book, config)
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        busy = false,
                        candidatePath = result.path,
                        sourceLabel = result.sourceLabel,
                        notice = result.notice,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(busy = false, error = error.message ?: "封面生成失败") }
            }
        }
    }

    fun consumeCandidate() = _state.update {
        it.copy(candidatePath = null, sourceLabel = "", notice = null, error = null)
    }

    fun clearNotice() = _state.update { it.copy(notice = null, error = null) }
}

private class CoverImageGeneratorV3 {
    private val detector = ProviderAutoDetector()

    suspend fun generate(
        application: Application,
        book: ReaderBookUi,
        config: AiProviderConfig?,
    ): GeneratedCoverCandidate = withContext(Dispatchers.IO) {
        val prompt = coverPrompt(book)
        var fallbackReason = "当前没有配置可用的图像生成模型"

        if (config != null) {
            val discovery = runCatching { detector.detect(config.baseUrl, config.apiKey) }.getOrNull()
            val protocol = when {
                config.protocol != ApiProtocol.AUTO -> config.protocol
                discovery != null -> discovery.protocol
                else -> ApiProtocol.AUTO
            }
            val imageModel = chooseImageModel(config.model, discovery?.models.orEmpty())

            if (imageModel != null) {
                val remote = runCatching {
                    when (protocol) {
                        ApiProtocol.GEMINI -> generateGemini(config, imageModel, prompt)
                        ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.AUTO -> generateOpenAiCompatible(config, imageModel, prompt)
                        else -> null
                    }
                }
                val bytes = remote.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    val file = candidateFile(application, book.id)
                    composeRemoteBackground(bytes, file, book)
                    return@withContext GeneratedCoverCandidate(
                        path = file.absolutePath,
                        sourceLabel = "AI 图像模型 · $imageModel",
                        notice = "已生成 AI 背景。中文书名由琅嬛本地排版，确认后再设为封面。",
                    )
                }
                fallbackReason = remote.exceptionOrNull()?.message
                    ?.take(120)
                    ?.let { "图像接口未兼容：$it" }
                    ?: "图像模型没有返回可用图片"
            } else {
                fallbackReason = "当前 AI 服务未发现图像生成模型"
            }
        }

        val file = candidateFile(application, book.id)
        renderLocalFallback(file, book)
        GeneratedCoverCandidate(
            path = file.absolutePath,
            sourceLabel = "本地封面模板",
            notice = "$fallbackReason；已改用本地干净模板，没有冒充 AI 图片。",
        )
    }

    private fun chooseImageModel(currentModel: String, models: List<DiscoveredModel>): String? {
        if (looksLikeImageModel(currentModel)) return currentModel
        return models
            .map { it.id }
            .sortedByDescending(::imageModelScore)
            .firstOrNull { imageModelScore(it) > 0 }
    }

    private fun looksLikeImageModel(model: String): Boolean = imageModelScore(model) > 0

    private fun imageModelScore(model: String): Int {
        val id = model.lowercase()
        return when {
            "gpt-image" in id -> 100
            "dall-e" in id || "dalle" in id -> 95
            "gemini" in id && "image" in id -> 92
            "flux" in id -> 90
            "imagen" in id -> 88
            "stable-diffusion" in id || "stable_diffusion" in id -> 85
            "sdxl" in id -> 82
            "image-gen" in id || "imagegen" in id -> 78
            id.startsWith("image-") || id.endsWith("-image") -> 70
            else -> 0
        }
    }

    private fun coverPrompt(book: ReaderBookUi): String = buildString {
        append("Create a premium vertical cover BACKGROUND for a Chinese web novel. ")
        append("Absolutely no words, no letters, no typography, no logo, no watermark. ")
        append("Leave deliberate negative space around the upper-middle area for a Chinese title overlay. ")
        append("Cinematic composition, strong focal image, readable at phone-thumbnail size, restrained details, professional publishing quality. ")
        append("Genre: ${book.genre}. ")
        append("Story premise: ${book.premise.take(650)}. ")
        if (book.theme.isNotBlank()) append("Theme: ${book.theme.take(260)}. ")
        append("Portrait aspect ratio approximately 2:3.")
    }

    private fun generateOpenAiCompatible(config: AiProviderConfig, model: String, prompt: String): ByteArray? {
        val endpoint = openAiImageEndpoint(config.baseUrl)
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1536")
        }.toString()
        val response = request(
            url = endpoint,
            method = "POST",
            headers = buildMap {
                put("Content-Type", "application/json")
                if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
            },
            body = body,
        )
        require(response.first in 200..299) {
            "图片接口 HTTP ${response.first}: ${response.second.decodeToString().take(120)}"
        }
        val root = CoverWireJson.parseToJsonElement(response.second.decodeToString()).jsonObject
        val first = (root["data"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        first["b64_json"]?.jsonPrimitive?.contentOrNull?.let { encoded ->
            return Base64.getDecoder().decode(encoded)
        }
        first["url"]?.jsonPrimitive?.contentOrNull?.let { imageUrl ->
            return downloadImage(imageUrl)
        }
        return null
    }

    private fun generateGemini(config: AiProviderConfig, model: String, prompt: String): ByteArray? {
        val endpoint = geminiImageEndpoint(config.baseUrl, model)
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray {
                    add(JsonPrimitive("TEXT"))
                    add(JsonPrimitive("IMAGE"))
                })
            })
        }.toString()
        val response = request(
            url = endpoint,
            method = "POST",
            headers = buildMap {
                put("Content-Type", "application/json")
                if (config.apiKey.isNotBlank()) put("x-goog-api-key", config.apiKey)
            },
            body = body,
        )
        require(response.first in 200..299) {
            "Gemini 图片接口 HTTP ${response.first}: ${response.second.decodeToString().take(120)}"
        }
        val root = CoverWireJson.parseToJsonElement(response.second.decodeToString()).jsonObject
        val candidates = root["candidates"] as? JsonArray ?: return null
        candidates.forEach { candidate ->
            val parts = candidate.jsonObject["content"]
                ?.jsonObject
                ?.get("parts") as? JsonArray ?: return@forEach
            parts.forEach { partElement ->
                val part = partElement.jsonObject
                val inline = (part["inlineData"] ?: part["inline_data"]) as? JsonObject ?: return@forEach
                val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                return Base64.getDecoder().decode(data)
            }
        }
        return null
    }

    private fun openAiImageEndpoint(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        listOf("/chat/completions", "/completions", "/responses", "/models").firstOrNull { base.endsWith(it) }?.let {
            base = base.removeSuffix(it)
        }
        val uri = runCatching { URI(base) }.getOrNull()
        if (uri?.host?.equals("api.openai.com", ignoreCase = true) == true && !base.endsWith("/v1")) {
            base += "/v1"
        }
        return "$base/images/generations"
    }

    private fun geminiImageEndpoint(baseUrl: String, model: String): String {
        var base = baseUrl.trim().trimEnd('/')
        val marker = "/models/"
        if (marker in base) base = base.substringBefore(marker)
        if (!base.contains("/v1beta") && !base.contains("/v1")) {
            base += "/v1beta"
        }
        return "$base/models/${model.removePrefix("models/")}:generateContent"
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
    ): Pair<Int, ByteArray> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        headers.forEach(connection::setRequestProperty)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.encodeToByteArray()) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        connection.disconnect()
        return status to bytes
    }

    private fun downloadImage(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        require(connection.responseCode in 200..299) { "图片下载失败 HTTP ${connection.responseCode}" }
        return connection.inputStream.use { it.readBytes() }.also { connection.disconnect() }
    }

    private fun candidateFile(application: Application, bookId: String): File {
        val dir = File(application.filesDir, "covers/candidates").apply { mkdirs() }
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$bookId-") }
            ?.forEach { runCatching { it.delete() } }
        return File(dir, "$bookId-${System.currentTimeMillis()}.png")
    }

    private fun composeRemoteBackground(bytes: ByteArray, file: File, book: ReaderBookUi) {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("图片模型返回的文件无法解码")
        val bitmap = Bitmap.createBitmap(900, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawCenterCrop(canvas, source, bitmap.width, bitmap.height)
        source.recycle()
        drawReadableOverlay(canvas, bitmap.width, bitmap.height)
        drawTypography(canvas, book, bitmap.width, bitmap.height)
        saveBitmap(bitmap, file)
    }

    private fun renderLocalFallback(file: File, book: ReaderBookUi) {
        val width = 900
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val seed = (book.title + book.genre + book.premise + book.theme).hashCode()
        val random = Random(seed.toLong())
        val hue = ((seed.toLong() and 0x7fffffffL) % 360L).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.54f, 0.34f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 36f) % 360f, 0.72f, 0.10f))
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        repeat(7) { index ->
            val radius = 80f + random.nextFloat() * 190f
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            soft.color = Color.argb(16 + index * 3, 255, 255, 255)
            canvas.drawCircle(x, y, radius, soft)
        }

        val geometry = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val inset = 78f
        canvas.drawRect(inset, 210f, width - inset, height - 180f, geometry)
        canvas.drawCircle(width * .75f, height * .27f, 152f, geometry)
        canvas.drawLine(90f, height * .72f, width - 90f, height * .55f, geometry)

        drawReadableOverlay(canvas, width, height)
        drawTypography(canvas, book, width, height)
        saveBitmap(bitmap, file)
    }

    private fun drawCenterCrop(canvas: Canvas, source: Bitmap, width: Int, height: Int) {
        val sourceRatio = source.width.toFloat() / source.height.toFloat()
        val targetRatio = width.toFloat() / height.toFloat()
        val src = if (sourceRatio > targetRatio) {
            val cropWidth = (source.height * targetRatio).toInt().coerceAtLeast(1)
            val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + cropWidth).coerceAtMost(source.width), source.height)
        } else {
            val cropHeight = (source.width / targetRatio).toInt().coerceAtLeast(1)
            val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
            Rect(0, top, source.width, (top + cropHeight).coerceAtMost(source.height))
        }
        canvas.drawBitmap(source, src, Rect(0, 0, width, height), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawReadableOverlay(canvas: Canvas, width: Int, height: Int) {
        val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(
                    Color.argb(105, 0, 0, 0),
                    Color.argb(28, 0, 0, 0),
                    Color.argb(48, 0, 0, 0),
                    Color.argb(175, 0, 0, 0),
                ),
                floatArrayOf(0f, .32f, .62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)
    }

    private fun drawTypography(canvas: Canvas, book: ReaderBookUi, width: Int, height: Int) {
        val genrePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 255, 255, 255)
            textSize = 29f
            letterSpacing = .06f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(8f, 0f, 3f, Color.argb(130, 0, 0, 0))
        }
        canvas.drawText(book.genre.take(18), 74f, 108f, genrePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = when {
                book.title.length <= 4 -> 116f
                book.title.length <= 8 -> 98f
                book.title.length <= 12 -> 82f
                else -> 72f
            }
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setShadowLayer(14f, 0f, 5f, Color.argb(190, 0, 0, 0))
        }
        drawWrappedTitle(
            canvas = canvas,
            text = book.title.ifBlank { "未命名小说" },
            paint = titlePaint,
            x = 72f,
            y = height * .40f,
            maxWidth = width - 144f,
            maxLines = 4,
        )

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(205, 255, 255, 255)
            strokeWidth = 3f
        }
        canvas.drawLine(74f, height - 128f, 238f, height - 128f, line)
    }

    private fun drawWrappedTitle(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        y: Float,
        maxWidth: Float,
        maxLines: Int,
    ) {
        val lines = mutableListOf<String>()
        var current = ""
        text.forEach { char ->
            val candidate = current + char
            if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                lines += current
                current = char.toString()
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        lines.take(maxLines).forEachIndexed { index, line ->
            canvas.drawText(line, x, y + index * paint.textSize * 1.18f, paint)
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 96, output)) { "封面写入失败" }
        }
        bitmap.recycle()
    }
}
