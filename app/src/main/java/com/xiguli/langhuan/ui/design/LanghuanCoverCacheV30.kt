package com.xiguli.langhuan.ui.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shelf covers used to be decoded at full resolution on the main thread during composition,
 * which made the grid stutter while scrolling. Covers are now decoded off the main thread,
 * downsampled to the size actually shown and kept in a small memory cache.
 */
object LanghuanCoverCacheV30 {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun key(path: String, width: Int): String {
        val modified = runCatching { File(path).lastModified() }.getOrDefault(0L)
        return "$path@$width@$modified"
    }

    fun peek(path: String, width: Int): Bitmap? = cache.get(key(path, width))

    fun load(path: String, width: Int): Bitmap? {
        if (path.isBlank()) return null
        val cacheKey = key(path, width)
        cache.get(cacheKey)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull() ?: return null
        cache.put(cacheKey, bitmap)
        return bitmap
    }
}

/** Returns the cover once decoded; null while loading or when the book has no cover. */
@Composable
fun rememberLanghuanCoverV30(path: String, targetWidthPx: Int = 360): ImageBitmap? {
    val state by produceState<ImageBitmap?>(
        initialValue = if (path.isBlank()) null else LanghuanCoverCacheV30.peek(path, targetWidthPx)?.asImageBitmap(),
        path,
        targetWidthPx,
    ) {
        if (path.isBlank()) {
            value = null
            return@produceState
        }
        if (value == null) {
            value = withContext(Dispatchers.IO) { LanghuanCoverCacheV30.load(path, targetWidthPx) }?.asImageBitmap()
        }
    }
    return state
}
