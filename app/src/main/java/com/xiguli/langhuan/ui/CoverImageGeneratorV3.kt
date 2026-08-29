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
import com.xiguli.langhuan.data.StoredAiProvider
import com.xiguli.langhuan.engine.AiProviderConfig
import com.xiguli.langhuan.engine.ApiProtocol
import com.xiguli.langhuan.engine.DiscoveredModel
import com.xiguli.langhuan.engine.ProviderAutoDetector
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
import kotlinx.serialization.json.JsonElement
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

private data class CoverProviderCandidate(
    val name: String,
    val isDefault: Boolean,
    val config: AiProviderConfig,
)

/**
 * 封面生成与正文模型彻底解耦：封面工作室会检查全部已保存 AI 服务，而不是只看默认文字服务。
 * 名称启发式只负责排序；OpenAI-compatible 服务在找不到典型图像模型名时，会对该服务当前模型
 * 做一次真实 images/generations 能力探测。这样中转站自定义模型名也不会被直接漏掉。
 */
class CoverStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PersistentStoryRepository(application)
    private val generator = CoverImageGeneratorV3()
    private val _state = MutableStateFlow(CoverGenerationUiState())
    val state: StateFlow<CoverGenerationUiState> = _state.asStateFlow()
    private var providers: List<StoredAiProvider> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeProviders().collect { stored ->
                providers = stored
            }
        }
    }

    fun generate(book: ReaderBookUi) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = null, error = null) }
            runCatching {
                val routes = providers
                    .sortedWith(compareByDescending<StoredAiProvider> { it.isDefault }.thenBy { it.name })
                    .mapNotNull { stored ->
                        repository.providerConfig(stored.id)?.let { config ->
                            CoverProviderCandidate(
                                name = stored.name.ifBlank { config.protocol.label },
                                isDefault = stored.isDefault,
                                config = config,
                            )
                        }
                    }
                generator.generate(getApplication(), book, routes)
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
        providers: List<CoverProviderCandidate>,
    ): GeneratedCoverCandidate = withContext(Dispatchers.IO) {
        val prompt = coverPrompt(book)
        val failures = mutableListOf<String>()

        providers.forEach { provider ->
            val config = provider.config
            val discovery = runCatching { detector.detect(config.baseUrl, config.apiKey) }.getOrNull()
            val protocol = when {
                config.protocol != ApiProtocol.AUTO -> config.protocol
                discovery != null -> discovery.protocol
                else -> ApiProtocol.OPENAI_COMPATIBLE
            }

            if (protocol !in setOf(ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.AUTO, ApiProtocol.GEMINI)) {
                failures += "${provider.name}：${protocol.label}没有图片生成协议"
                return@forEach
            }

            val modelCandidates = chooseImageModels(
                currentModel = config.model,
                models = discovery?.models.orEmpty(),
                protocol = protocol,
            )
            if (modelCandidates.isEmpty()) {
                failures += "${provider.name}：模型列表未发现可出图模型"
                return@forEach
            }

            modelCandidates.forEach { model ->
                val remote = runCatching {
                    when (protocol) {
                        ApiProtocol.GEMINI -> generateGemini(config, model, prompt)
                        ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.AUTO -> generateOpenAiCompatible(config, model, prompt)
                        else -> null
                    }
                }
                val bytes = remote.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    val file = candidateFile(application, book.id)
                    composeRemoteBackground(bytes, file, book)
                    return@withContext GeneratedCoverCandidate(
                        path = file.absolutePath,
                        sourceLabel = "AI 图像 · ${provider.name} · $model",
                        notice = "已从“${provider.name}”使用 $model 生成真实 AI 背景；中文书名由琅嬛本地排版。",
                    )
                }
                val reason = remote.exceptionOrNull()?.message.orEmpty().take(140)
                failures += "${provider.name}/$model：${reason.ifBlank { "没有返回图片" }}"
            }
        }

        val file = candidateFile(application, book.id)
        renderLocalFallback(file, book)
        val reason = when {
            providers.isEmpty() -> "还没有配置 AI 服务"
            failures.isEmpty() -> "已检查 ${providers.size} 个 AI 服务，但没有可调用的图片生成模型"
            else -> "已检查 ${providers.size} 个 AI 服务；${failures.take(2).joinToString("；")}"
        }
        GeneratedCoverCandidate(
            path = file.absolutePath,
            sourceLabel = "本地封面模板",
            notice = "$reason。已暂用本地模板；不会把模板冒充 AI 图片。",
        )
    }

    private fun chooseImageModels(
        currentModel: String,
        models: List<DiscoveredModel>,
        protocol: ApiProtocol,
    ): List<String> {
        val discovered = models
            .map { model -> model to maxOf(imageModelScore(model.id), imageModelScore(model.displayName)) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .map { (model, _) -> model.id }

        val result = mutableListOf<String>()
        if (imageModelScore(currentModel) > 0) result += currentModel
        result += discovered

        // OpenAI-compatible 中转站经常把图片模型改成私有名称，/models 也不声明 output modality。
        // 用户已经主动点击“生成封面”，因此在没有识别到典型名字时，对当前配置模型做一次真实能力探测。
        // Unsupported text models通常会在 images/generations 立即返回 4xx，不会继续生成文本。
        if (protocol in setOf(ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.AUTO) && currentModel.isNotBlank()) {
            result += currentModel
        }
        return result.filter(String::isNotBlank).distinct().take(8)
    }

    private fun imageModelScore(model: String): Int {
        val id = model.lowercase().replace('_', '-')
        return when {
            "gpt-image" in id -> 120
            "dall-e" in id || "dalle" in id -> 118
            "gemini" in id && ("image" in id || "banana" in id) -> 116
            "imagen" in id -> 114
            "seedream" in id || "dreamina" in id -> 112
            "qwen-image" in id || "qwen image" in id -> 110
            "flux" in id -> 108
            "cogview" in id -> 106
            "hunyuan-image" in id || "hunyuan image" in id -> 104
            "kolors" in id -> 102
            "recraft" in id -> 100
            "ideogram" in id -> 98
            "stable-diffusion" in id || "stable diffusion" in id -> 96
            "sdxl" in id -> 94
            "playground-v" in id -> 92
            "firefly" in id -> 90
            "image-generation" in id || "image-gen" in id || "imagegen" in id -> 88
            "text-to-image" in id || "text2image" in id || "txt2img" in id -> 86
            "flash-image" in id || "image-preview" in id -> 84
            "janus" in id && ("pro" in id || "flow" in id) -> 80
            id.startsWith("image-") || id.endsWith("-image") -> 76
            else -> 0
        }
    }

    private fun coverPrompt(book: ReaderBookUi): String = buildString {
        append("Create a premium vertical cover BACKGROUND for a Chinese web novel. ")
        append("Absolutely no words, no letters, no typography, no logo, no watermark. ")
        append("Leave deliberate negative space around the upper-middle area for a Chinese title overlay. ")
        append("Cinematic composition, one strong focal image, readable at phone-thumbnail size, restrained details, professional publishing quality. ")
        append("Genre: ${book.genre}. ")
        append("Story premise: ${book.premise.take(650)}. ")
        if (book.theme.isNotBlank()) append("Theme: ${book.theme.take(260)}. ")
        append("Portrait aspect ratio approximately 2:3.")
    }

    private fun generateOpenAiCompatible(config: AiProviderConfig, model: String, prompt: String): ByteArray? {
        val endpoint = openAiImageEndpoint(config.baseUrl)
        val standardBody = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", "1024x1536")
        }.toString()
        var response = request(
            url = endpoint,
            method = "POST",
            headers = openAiHeaders(config),
            body = standardBody,
        )

        // 一些中转站声明 OpenAI-compatible，但图片接口采用 image_size/batch_size 字段。
        if (response.first in setOf(400, 404, 405, 422)) {
            val alternateBody = buildJsonObject {
                put("model", model)
                put("prompt", prompt)
                put("image_size", "1024x1536")
                put("batch_size", 1)
            }.toString()
            response = request(
                url = endpoint,
                method = "POST",
                headers = openAiHeaders(config),
                body = alternateBody,
            )
        }
        require(response.first in 200..299) {
            "图片接口 HTTP ${response.first}: ${response.second.decodeToString().take(140)}"
        }
        return extractOpenAiImage(response.second)
    }

    private fun openAiHeaders(config: AiProviderConfig): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) put("Authorization", "Bearer ${config.apiKey}")
    }

    private fun extractOpenAiImage(bytes: ByteArray): ByteArray? {
        val root = CoverWireJson.parseToJsonElement(bytes.decodeToString()).jsonObject
        val containers = buildList<JsonElement> {
            (root["data"] as? JsonArray)?.forEach(::add)
            (root["images"] as? JsonArray)?.forEach(::add)
            root["image"]?.let(::add)
            root["output"]?.let(::add)
        }
        containers.forEach { element ->
            val obj = element as? JsonObject ?: return@forEach
            listOf("b64_json", "base64", "image_base64", "b64").forEach { key ->
                obj[key]?.jsonPrimitive?.contentOrNull?.let { encoded ->
                    decodeBase64Image(encoded)?.let { return it }
                }
            }
            listOf("url", "image_url", "imageUrl").forEach { key ->
                obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    if (imageUrl.startsWith("data:")) {
                        decodeBase64Image(imageUrl.substringAfter("base64,"))?.let { return it }
                    }
                    return downloadImage(imageUrl)
                }
            }
        }
        return null
    }

    private fun decodeBase64Image(raw: String): ByteArray? = runCatching {
        Base64.getDecoder().decode(raw.substringAfter("base64,", raw).trim())
    }.getOrNull()

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
            "Gemini 图片接口 HTTP ${response.first}: ${response.second.decodeToString().take(140)}"
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
                return decodeBase64Image(data)
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
        val status = connection.responseCode
        require(status in 200..299) { "图片下载失败 HTTP $status" }
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
