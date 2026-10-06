package com.xiguli.langhuan.ui

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import okhttp3.HttpUrl
import org.jsoup.nodes.Document

internal fun sourceOriginV46(url: HttpUrl): String =
    url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString().removeSuffix("/")

internal fun sourceRetryAfterMillisV46(raw: String?, now: Long = System.currentTimeMillis()): Long? {
    val value = raw?.trim()?.takeIf { it.length in 1..128 } ?: return null
    value.toLongOrNull()?.let { seconds -> return seconds.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000) }
    return runCatching { (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now).coerceAtLeast(0) }.getOrNull()
}

/** Memory only; all callers respect a server's Retry-After without holding a socket or sleeping. */
internal object SourceCooldownV46 {
    private val until = HashMap<String, Long>()
    @Synchronized fun record(error: SourceHttpStatusExceptionV44) {
        if (error.statusCode != 429) return
        val delay = error.retryAfterMillis ?: 60_000L
        val now = System.currentTimeMillis()
        until[error.origin] = now + delay.coerceAtMost(Long.MAX_VALUE - now)
    }
    @Synchronized fun check(url: HttpUrl) {
        val origin = sourceOriginV46(url)
        val left = (until[origin] ?: return) - System.currentTimeMillis()
        if (left <= 0) { until.remove(origin); return }
        throw SourceHttpStatusExceptionV44(429, origin, "网站仍处于请求冷却期，约 ${(left / 1000).coerceAtLeast(1)} 秒后可重试；未发送新请求", left)
    }
}

internal fun sourceStopFailureV46(error: Throwable): Throwable? =
    generateSequence(error) { it.cause }.take(12).firstOrNull {
        it is SourceDnsBlockedV54 || it is SourceBrowserChallengeV46 || it is SourceHttpStatusExceptionV44 && (it.statusCode == 429 || it.browserChallenge)
    }

/** One AI build owns one bounded, credential-scoped cache. Cached DOMs are always cloned. */
internal class SourceRequestSessionV46(private val minimumGapMillis: Long = 0,
    private val fetch: (BookSourceV36, SourceRequestV36) -> Document) {
    private data class Key(val id: String, val base: String, val headers: Map<String, String>, val browser: Boolean, val request: SourceRequestV36)
    private val cache = LinkedHashMap<Key, Document>()
    private var weight = 0L
    private var lastRequestNanos = 0L
    var stopFailure: Throwable? = null
        private set

    fun reset() { cache.clear(); weight = 0; stopFailure = null; lastRequestNanos = 0L }
    fun checkActive() { stopFailure?.let { throw it } }

    fun document(source: BookSourceV36, request: SourceRequestV36): Document {
        val key = Key(source.id, source.baseUrl, source.headers.toMap(), source.useBrowser, request)
        cache[key]?.let { return it.clone() }
        checkActive()
        try {
            val remaining = minimumGapMillis * 1_000_000 - (System.nanoTime() - lastRequestNanos)
            if (lastRequestNanos != 0L && remaining > 0) Thread.sleep((remaining + 999_999) / 1_000_000)
            lastRequestNanos = System.nanoTime()
            val doc = fetch(source, request)
            val size = doc.outerHtml().length.toLong() * 2
            if (size <= 8L * 1024 * 1024) {
                while (cache.isNotEmpty() && (cache.size >= 24 || weight + size > 8L * 1024 * 1024)) {
                    val first = cache.entries.first()
                    weight -= first.value.outerHtml().length.toLong() * 2
                    cache.remove(first.key)
                }
                cache[key] = doc.clone()
                weight += size
            }
            return doc
        } catch (error: Exception) {
            sourceStopFailureV46(error)?.let { stopFailure = it }
            throw error
        }
    }
}
