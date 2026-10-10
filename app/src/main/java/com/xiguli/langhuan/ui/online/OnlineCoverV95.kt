package com.xiguli.langhuan.ui

import java.io.File
import java.util.Base64

/*
 * V95 在线书籍封面。
 *
 * 加入书架后书架上没有封面，原因有三层：
 *  1. 加入书架只保存了书名、简介和目录，封面地址从未下载、也没有写入 novel.coverPath（书架只读本地文件）；
 *  2. 封面规则常写成 XPath / @js:，导入时被当作“不支持”删掉，搜索结果本身就没有封面地址；
 *  3. 列表里的封面请求不带书源的 Referer / User-Agent，也不认 Legado 的 `url,{"headers":…}` 写法，
 *     很多图床防盗链直接 403。
 * 这里统一处理：带书源请求头下载（仍走书源 HTTP 客户端的公网 DNS 校验与大小上限），校验是图片后
 * 保存为 files/covers/<id>-online.<ext> 并写回 coverPath；拿不到时保留原有的排版占位封面。
 */

/** Which source a cover url came from, so list thumbnails can send that source's Referer/User-Agent. */
internal object OnlineCoverSourcesV95 {
    private val map = object : LinkedHashMap<String, BookSourceV36>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BookSourceV36>?): Boolean = size > 512
    }
    fun remember(cover: String, source: BookSourceV36) {
        if (cover.isNotBlank()) synchronized(map) { map[cover] = source }
    }
    fun sourceFor(cover: String): BookSourceV36? = synchronized(map) { map[cover] }
}

/** The request for a cover: Legado header options, plus the source's Referer and User-Agent. */
internal fun coverRequestV95(source: BookSourceV36?, rawCover: String): SourceRequestV36 {
    val cover = rawCover.trim()
    val base = source?.baseUrl.orEmpty()
    val parsed = runCatching { parseSourceRequestUrlV95(cover, base.ifBlank { cover.substringBefore(",{") }, source) }.getOrNull()
        ?: SourceRequestV36(resolveUrlV36(base, cover.substringBefore(",{")))
    val headers = LinkedHashMap<String, String>()
    if (source != null) {
        source.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.let { headers["User-Agent"] = it.value }
        source.headers.entries.firstOrNull { it.key.equals("Referer", true) }?.let { headers["Referer"] = it.value }
        if (headers.keys.none { it.equals("Referer", true) } && base.isNotBlank()) headers["Referer"] = base.trimEnd('/') + "/"
    }
    parsed.headers.forEach { (k, v) ->
        headers.keys.firstOrNull { it.equals(k, true) }?.let(headers::remove)
        headers[k] = v
    }
    return parsed.copy(method = "GET", body = null, headers = headers)
}

/** True for the image formats Android can decode (JPEG, PNG, GIF, WebP, BMP, AVIF/HEIF). */
internal fun looksLikeImageV95(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    fun at(i: Int) = bytes[i].toInt() and 0xff
    fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
    return (at(0) == 0xff && at(1) == 0xd8 && at(2) == 0xff) ||
        (at(0) == 0x89 && ascii(1, "PNG")) ||
        ascii(0, "GIF8") ||
        (ascii(0, "RIFF") && ascii(8, "WEBP")) ||
        ascii(0, "BM") ||
        (ascii(4, "ftyp") && listOf("avif", "avis", "heic", "heix", "mif1", "msf1").any { ascii(8, it) })
}

internal fun imageExtensionV95(bytes: ByteArray): String {
    fun at(i: Int) = bytes[i].toInt() and 0xff
    return when {
        at(0) == 0x89 -> "png"
        at(0) == 'G'.code -> "gif"
        at(0) == 'R'.code -> "webp"
        at(0) == 'B'.code -> "bmp"
        bytes.size > 11 && at(4) == 'f'.code -> "avif"
        else -> "jpg"
    }
}

/**
 * Downloads a cover through the guarded source client. `data:image/…;base64,` covers are decoded locally.
 * Throws when the response is not an image.
 */
internal fun downloadOnlineCoverV95(
    rawCover: String,
    source: BookSourceV36? = OnlineCoverSourcesV95.sourceFor(rawCover),
    maxBytes: Int = 2 * 1024 * 1024,
    fetch: (BookSourceV36?, SourceRequestV36, Int) -> ByteArray = ::fetchSourceRequestBytesV95,
): ByteArray {
    val cover = rawCover.trim()
    require(cover.isNotEmpty()) { "没有封面地址" }
    if (cover.startsWith("data:image/", true)) {
        val payload = cover.substringAfter("base64,", "")
        require(payload.isNotEmpty()) { "封面数据无效" }
        val bytes = Base64.getMimeDecoder().decode(payload)
        require(bytes.size <= maxBytes && looksLikeImageV95(bytes)) { "封面数据无效" }
        return bytes
    }
    val request = coverRequestV95(source, cover)
    val bytes = try {
        fetch(source, request, maxBytes)
    } catch (error: Exception) {
        if (error is java.util.concurrent.CancellationException) throw error
        // Some image hosts reject any foreign Referer; try once more without it.
        val retry = request.copy(headers = request.headers.filterKeys { !it.equals("Referer", true) })
        if (retry.headers.size == request.headers.size) throw error
        fetch(source, retry, maxBytes)
    }
    require(looksLikeImageV95(bytes)) { "封面地址返回的不是图片" }
    return bytes
}

/** Writes the cover next to the app's other covers; returns its absolute path. */
internal fun persistOnlineCoverFileV95(coversDir: File, novelId: String, bytes: ByteArray): String {
    require(novelId.isNotBlank() && !novelId.contains('/') && !novelId.contains("..")) { "书籍标识无效" }
    coversDir.mkdirs()
    coversDir.listFiles()?.filter { it.name.startsWith("$novelId-online.") }?.forEach { it.delete() }
    val file = File(coversDir, "$novelId-online.${imageExtensionV95(bytes)}")
    val temp = File(coversDir, "$novelId-online.tmp")
    temp.writeBytes(bytes)
    check(temp.renameTo(file)) { "封面写入失败" }
    return file.absolutePath
}

/** True when a shelf book has no cover file at all. A cover the user picked is never replaced. */
internal fun onlineCoverMissingV95(coverPath: String): Boolean {
    if (coverPath.isBlank()) return true
    val file = File(coverPath)
    return !file.isFile || file.length() == 0L
}
