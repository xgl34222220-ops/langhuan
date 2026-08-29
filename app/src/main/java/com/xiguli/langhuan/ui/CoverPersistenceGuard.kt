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
 * 封面持久化守卫。
 *
 * 旧实现一直覆盖 files/covers/<novelId>.png，Compose 又按 path remember Bitmap，导致
 * “磁盘已经换图，但界面还显示旧图”。现在稳定写入口仍兼容旧 CoverComposer，但守卫会把
 * 每次生成结果立刻归档成不可变版本文件，并把 novel.coverPath 改成新路径。
 *
 * 这样同时解决：
 * 1. 同路径图片缓存不刷新；
 * 2. AI 封面覆盖后无法找回；
 * 3. 数据库 coverPath 被旧 snapshot 冲掉；
 * 4. 封面文件丢失后只能显示占位图。
 */
class CoverPersistenceGuardViewModel(application: Application) : AndroidViewModel(application) {
    private val storyDao = LanghuanDatabase.get(application).storyStateDao()
    private val coversDir = File(application.filesDir, "covers").apply { mkdirs() }

    init {
        viewModelScope.launch {
            storyDao.observeAll().collectLatest { rows ->
                rows.forEach { row ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repairRow(row.novelId, row.snapshotJson)
                        }
                    }
                }
            }
        }
    }

    private suspend fun repairRow(novelId: String, snapshotJson: String) {
        val snapshot = runCatching {
            CoverGuardJson.decodeFromString(StorySnapshot.serializer(), snapshotJson)
        }.getOrNull() ?: return

        val stable = File(coversDir, "${snapshot.novel.id}.png")
        val current = snapshot.novel.coverPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)

        val target = when {
            // 已经是存在的版本化文件，不需要再写数据库。
            current != null && current.isFile && current.parentFile == coversDir && current.name.startsWith("${snapshot.novel.id}-") -> current

            // 旧生成器刚刚覆盖了稳定路径：立即归档成新版本，让 path 真正发生变化。
            stable.isFile && stable.length() > 0L -> archiveStableCover(snapshot.novel.id, stable)

            // coverPath 仍指向一个有效文件（例如导入项目带来的文件），归档进应用自己的封面目录。
            current != null && current.isFile && current.length() > 0L -> archiveExternalCover(snapshot.novel.id, current)

            // 文件丢失时优先恢复最近历史版本。
            else -> latestVersion(snapshot.novel.id) ?: run {
                createFallbackCover(
                    file = stable,
                    title = snapshot.novel.title,
                    genre = snapshot.novel.genre,
                    premise = snapshot.novel.premise,
                )
                archiveStableCover(snapshot.novel.id, stable)
            }
        }

        val row = storyDao.get(novelId) ?: return
        val latestSnapshot = runCatching {
            CoverGuardJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
        }.getOrNull() ?: return

        if (latestSnapshot.novel.coverPath != target.absolutePath) {
            val repaired = latestSnapshot.copy(
                novel = latestSnapshot.novel.copy(coverPath = target.absolutePath),
            )
            storyDao.upsert(
                row.copy(
                    snapshotJson = CoverGuardJson.encodeToString(StorySnapshot.serializer(), repaired),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        trimHistory(snapshot.novel.id, target.absolutePath)
    }

    private fun archiveStableCover(novelId: String, stable: File): File {
        val version = versionFile(novelId)
        stable.copyTo(version, overwrite = false)
        // 旧生成器下次仍会写回 stable；删除后更容易判断“是否真的生成了新封面”。
        stable.delete()
        return version
    }

    private fun archiveExternalCover(novelId: String, source: File): File {
        val version = versionFile(novelId)
        source.copyTo(version, overwrite = false)
        return version
    }

    private fun versionFile(novelId: String): File {
        var stamp = System.currentTimeMillis()
        var file = File(coversDir, "$novelId-$stamp.png")
        while (file.exists()) {
            stamp += 1
            file = File(coversDir, "$novelId-$stamp.png")
        }
        return file
    }

    private fun latestVersion(novelId: String): File? = coversDir
        .listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.length() > 0L && it.name.startsWith("$novelId-") && it.extension.equals("png", true) }
        ?.maxByOrNull { it.lastModified() }

    private fun trimHistory(novelId: String, keepPath: String) {
        val versions = coversDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$novelId-") && it.extension.equals("png", true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        versions.drop(12).forEach { file ->
            if (file.absolutePath != keepPath) runCatching { file.delete() }
        }
    }

    private fun createFallbackCover(file: File, title: String, genre: String, premise: String) {
        file.parentFile?.mkdirs()
        val width = 900
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hue = (title.hashCode().toLong().let { if (it < 0) -it else it } % 360L).toFloat()
        val top = Color.HSVToColor(floatArrayOf(hue, 0.42f, 0.80f))
        val bottom = Color.HSVToColor(floatArrayOf((hue + 28f) % 360f, 0.72f, 0.24f))
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        val veil = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(38, 255, 255, 255) }
        canvas.drawCircle(width * 0.82f, height * 0.18f, width * 0.34f, veil)
        canvas.drawCircle(width * 0.14f, height * 0.80f, width * 0.48f, Paint(veil).apply { color = Color.argb(22, 255, 255, 255) })

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = when {
                title.length <= 4 -> 106f
                title.length <= 8 -> 90f
                else -> 74f
            }
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 255, 255, 255)
            textSize = 25f
        }

        var y = 500f
        wrapText(title.ifBlank { "未命名小说" }, 9).take(4).forEach { line ->
            canvas.drawText(line, 78f, y, titlePaint)
            y += titlePaint.textSize * 1.18f
        }
        canvas.drawText(genre.ifBlank { "长篇小说" }, 82f, (y + 20f).coerceAtMost(990f), metaPaint)

        // 不再把整段视觉简报/简介塞进封面，只保留极短副标，避免缩略图变成一坨小字。
        val tagline = premise.trim().replace(Regex("\\s+"), " ").take(26)
        if (tagline.isNotBlank()) canvas.drawText(tagline, 82f, 1150f, smallPaint)
        canvas.drawText("琅嬛", 82f, 1214f, smallPaint)

        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 96, out)) { "封面写入失败" }
        }
        bitmap.recycle()
    }

    private fun wrapText(text: String, maxChars: Int): List<String> = text.chunked(maxChars.coerceAtLeast(1))
}
