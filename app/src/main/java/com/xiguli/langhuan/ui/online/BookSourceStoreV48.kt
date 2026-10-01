package com.xiguli.langhuan.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Where a shelf book came from online, for "检查更新". */
@Serializable
internal data class OnlineLinkV36(val novelId: String, val sourceId: String, val bookUrl: String, val chapterCount: Int)

internal const val SOURCE_STORAGE_ERROR_V48 = "本地书源配置无法完整读取，原始数据已保留，暂不能修改或覆盖；请勿清除应用数据"

internal object BookSourceStoreV36 {
    private const val PREFS = "book_sources_v36"

    @Synchronized
    fun read(context: Context): Result<List<BookSourceV36>> = sourceAttemptV36 {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sources", null)
        if (raw == null) emptyList() else BookSourceJsonV36.decodeFromString(ListSerializer(BookSourceV36.serializer()), raw).also(::validateIdentities)
    }.recoverCatching { throw IllegalStateException(SOURCE_STORAGE_ERROR_V48, it) }

    /** Export preserves a corrupt original too, so recovery bytes are never replaced with []. */
    fun raw(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sources", null)

    /** Callers doing online work must surface storage failure, never treat it as no sources. */
    fun load(context: Context): List<BookSourceV36> = read(context).getOrThrow()

    @Synchronized
    fun save(context: Context, sources: List<BookSourceV36>, expected: List<BookSourceV36>? = null) {
        validateIdentities(sources)
        val current = load(context) // A corrupt original must not be replaced by an ordinary save.
        check(expected == null || current == expected) { "书源已在其他操作中更新，请重新打开书源管理后重试" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString("sources", null)
        val encoded = BookSourceJsonV36.encodeToString(ListSerializer(BookSourceV36.serializer()), sources)
        check(!Thread.currentThread().isInterrupted) { "保存已取消，原书源未修改" }
        if (!prefs.edit().putString("sources", encoded).commit()) {
            // SharedPreferences updates memory before reporting disk failure. Restore that view too.
            val rollback = prefs.edit()
            if (previous == null) rollback.remove("sources") else rollback.putString("sources", previous)
            rollback.commit()
            error("书源保存失败，未应用本次修改；请检查存储空间并保留原数据")
        }
    }

    private fun validateIdentities(sources: List<BookSourceV36>) {
        require(sources.all { it.id.isNotBlank() } && sources.map { it.id }.distinct().size == sources.size) {
            "书源标识为空或重复，未保存本次修改"
        }
    }

    fun link(context: Context, novelId: String): OnlineLinkV36? = sourceAttemptV36 {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("link_$novelId", null) ?: return null
        BookSourceJsonV36.decodeFromString(OnlineLinkV36.serializer(), raw)
    }.getOrNull()

    fun saveLink(context: Context, link: OnlineLinkV36) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("link_${link.novelId}", BookSourceJsonV36.encodeToString(OnlineLinkV36.serializer(), link)).apply()
    }
}
