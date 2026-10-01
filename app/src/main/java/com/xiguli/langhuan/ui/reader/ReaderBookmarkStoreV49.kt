package com.xiguli.langhuan.ui

import android.content.SharedPreferences

/** Chapter bookmarks belong to a book. The unscoped legacy set is preserved, never guessed. */
internal object ReaderBookmarkStoreV49 {
    private fun key(bookId: String): String {
        require(bookId.isNotBlank()) { "书籍标识无效，未修改书签" }
        return "bookmarks_v49_$bookId"
    }
    private fun read(prefs: SharedPreferences, name: String): Set<String> {
        val raw = prefs.getStringSet(name, emptySet()).orEmpty().toSet()
        require(raw.all { (it.toIntOrNull() ?: 0) > 0 }) { "书签章节标识无效" }
        return raw.map { it.toInt().toString() }.toSet()
    }
    @Synchronized
    fun load(prefs: SharedPreferences, bookId: String): Result<Set<String>> = runCatching { read(prefs, key(bookId)) }
        .recoverCatching { throw IllegalStateException("本书书签无法完整读取，原始数据已保留，暂停修改", it) }

    @Synchronized
    fun legacy(prefs: SharedPreferences): Result<Set<String>> = runCatching { read(prefs, "bookmarks") }
        .recoverCatching { throw IllegalStateException("旧版书签暂存无法完整读取，原始数据仍保留", it) }

    @Synchronized
    fun replace(prefs: SharedPreferences, bookId: String, bookmarks: Set<String>): Result<Set<String>> = runCatching {
        load(prefs, bookId).getOrThrow() // Never overwrite an unreadable current-book set.
        require(bookmarks.all { (it.toIntOrNull() ?: 0) > 0 }) { "书签章节标识无效" }
        val name = key(bookId)
        val existed = prefs.contains(name)
        val original = prefs.getStringSet(name, null)?.toSet()
        val next = bookmarks.map { it.toInt().toString() }.toSet()
        check(!Thread.currentThread().isInterrupted) { "保存已取消，书签未修改" }
        val committed = runCatching { prefs.edit().putStringSet(name, next).commit() }.getOrDefault(false)
        if (!committed) {
            // commit may update in-memory preferences before reporting a disk failure.
            runCatching {
                val rollback = prefs.edit()
                if (existed) rollback.putStringSet(name, original) else rollback.remove(name)
                rollback.commit()
            }
            error("书签保存失败，未应用本次修改，请检查存储空间后重试")
        }
        next
    }

    @Synchronized
    fun add(prefs: SharedPreferences, bookId: String, chapterNumber: Int): Result<Set<String>> = runCatching {
        require(chapterNumber > 0) { "章节编号无效，未修改书签" }
        replace(prefs, bookId, load(prefs, bookId).getOrThrow() + chapterNumber.toString()).getOrThrow()
    }

    @Synchronized
    fun setMarked(prefs: SharedPreferences, bookId: String, chapterNumber: Int, marked: Boolean): Result<Set<String>> = runCatching {
        require(chapterNumber > 0) { "章节编号无效，未修改书签" }
        val next = load(prefs, bookId).getOrThrow().toMutableSet()
        if (marked) next.add(chapterNumber.toString()) else next.remove(chapterNumber.toString())
        replace(prefs, bookId, next).getOrThrow()
    }

    @Synchronized
    fun restoreLegacy(prefs: SharedPreferences, bookId: String, chapterNumber: Int, availableChapters: Set<Int>): Result<Set<String>> = runCatching {
        require(chapterNumber in availableChapters && chapterNumber.toString() in legacy(prefs).getOrThrow()) { "该旧版条目不在本书目录中，未修改书签" }
        add(prefs, bookId, chapterNumber).getOrThrow()
    }
}
