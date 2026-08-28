package com.xiguli.langhuan.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.domain.StorySnapshot
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val CoverGuardJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * 封面文件始终使用 files/covers/<novelId>.png 的稳定路径。
 *
 * 两层兜底：
 * 1. AI/本地封面已经生成，但旧 ViewModel 用过期 snapshot 把 coverPath 冲掉时，从真实文件修复数据库。
 * 2. 封面文件本身不存在时，立即用书名/类型生成一张本地保底封面。这样 AI 美术简报失败也不会让作品
 *    永远停留在默认占位图；以后 AI 封面成功写入同一路径时会自然覆盖这张保底图。
 */
class CoverPersistenceGuardViewModel(application: Application) : AndroidViewModel(application) {
    private val storyDao = LanghuanDatabase.get(application).storyStateDao()
    private val coversDir = File(application.filesDir, "covers")

    init {
        viewModelScope.launch {
            storyDao.observeAll().collectLatest { rows ->
                rows.forEach { row ->
                    val snapshot = runCatching {
                        CoverGuardJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
                    }.getOrNull() ?: return@forEach

                    val file = File(coversDir, "${snapshot.novel.id}.png")
                    if (!file.isFile || file.length() <= 0L) {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                createFallbackCover(
                                    file = file,
                                    title = snapshot.novel.title,
                                    genre = snapshot.novel.genre,
                                    premise = snapshot.novel.premise,
                                )
                            }
                        }.getOrElse { return@forEach }
                    }

                    if (snapshot.novel.coverPath == file.absolutePath) return@forEach
                    val repaired = snapshot.copy(
                        novel = snapshot.novel.copy(coverPath = file.absolutePath),
                    )
                    storyDao.upsert(
                        row.copy(
                            snapshotJson = CoverGuardJson.encodeToString(StorySnapshot.serializer(), repaired),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
    }

    private fun createFallbackCover(file: File, title: String, genre: String, premise: String) {
        file.parentFile?.mkdirs()
        val width = 900
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hue = (title.hashCode().toLong().let { if (it < 0) -it else it } % 360L).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.46f, 0.88f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 34f) % 360f, 0.68f, 0.42f))
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(54, 255, 255, 255) }
        canvas.drawCircle(width * 0.82f, height * 0.18f, width * 0.34f, veil)
        canvas.drawCircle(width * 0.18f, height * 0.78f, width * 0.46f, Paint(veil).apply { color = Color.argb(32, 255, 255, 255) })

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 82f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            textSize = 27f
        }

        var y = 570f
        wrapText(title.ifBlank { "未命名小说" }, 9).take(4).forEach { line ->
            canvas.drawText(line, 78f, y, titlePaint)
            y += 104f
        }
        canvas.drawText(genre.ifBlank { "长篇小说" }, 82f, (y + 24f).coerceAtMost(1040f), metaPaint)

        val tagline = premise.trim().replace(Regex("\\s+"), " ").take(46)
        if (tagline.isNotBlank()) {
            canvas.drawText(tagline, 82f, 1162f, smallPaint)
        }
        canvas.drawText("琅嬛", 82f, 1224f, smallPaint)

        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)) { "封面写入失败" }
        }
        bitmap.recycle()
    }

    private fun wrapText(text: String, maxChars: Int): List<String> =
        text.chunked(maxChars.coerceAtLeast(1))
}
