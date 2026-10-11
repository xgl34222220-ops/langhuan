package com.xiguli.langhuan.data.shelf

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One shelf group (书架), such as the default 「正在阅读」. */
@Serializable
data class ShelfGroup(
    val id: String,
    val name: String,
    val createdAt: Long = 0L,
)

/**
 * Shelf-side metadata for one book. The book text itself stays in Room; this only records which
 * shelf it is on and the details the user edited on 图书详情.
 */
@Serializable
data class ShelfBookMeta(
    val bookId: String,
    val groupId: String = ShelfLibrary.DEFAULT_GROUP_ID,
    /** Author typed on 图书详情, or null to fall back to the author found at import. */
    val author: String? = null,
    /** A cover picked from the gallery (absolute path in app storage), or null. */
    val customCoverPath: String? = null,
    /** Index of the chosen generated cover style, or null for the default look. */
    val generatedCoverStyle: Int? = null,
    /** True when the user explicitly chose a generated cover over the imported artwork. */
    val preferGenerated: Boolean = false,
    /** When the book was first seen on the shelf (fallback import time for older books). */
    val addedAt: Long = 0L,
    /** Cached whole-book statistics, filled the first time 图书详情 computes them. */
    val wordCount: Long? = null,
    val chapterCount: Int? = null,
)

@Serializable
data class ShelfLibraryData(
    val version: Int = 1,
    val groups: List<ShelfGroup> = emptyList(),
    val books: Map<String, ShelfBookMeta> = emptyMap(),
)

/** Pure shelf operations. Every function returns new data and never throws for normal input. */
object ShelfLibrary {
    const val DEFAULT_GROUP_ID = "reading"
    const val DEFAULT_GROUP_NAME = "正在阅读"
    const val MAX_NAME_LENGTH = 20

    fun empty(now: Long = 0L) = ShelfLibraryData(groups = listOf(ShelfGroup(DEFAULT_GROUP_ID, DEFAULT_GROUP_NAME, now)))

    /** Guarantees the default shelf exists and every meta row points at an existing shelf. */
    fun normalize(data: ShelfLibraryData, now: Long = 0L): ShelfLibraryData {
        val groups = data.groups.distinctBy { it.id }.toMutableList()
        if (groups.none { it.id == DEFAULT_GROUP_ID }) groups.add(0, ShelfGroup(DEFAULT_GROUP_ID, DEFAULT_GROUP_NAME, now))
        val ids = groups.map { it.id }.toSet()
        val books = data.books.mapValues { (_, meta) ->
            if (meta.groupId in ids) meta else meta.copy(groupId = DEFAULT_GROUP_ID)
        }
        return data.copy(groups = groups, books = books)
    }

    /**
     * Brings the metadata in line with the books stored in Room: books seen for the first time
     * land on [targetGroupId] (the default shelf unless the caller says otherwise) and rows of
     * books that no longer exist are dropped. Existing books keep their shelf.
     */
    fun reconcile(
        data: ShelfLibraryData,
        bookIds: Collection<String>,
        now: Long,
        firstSeenAt: (String) -> Long = { now },
        targetGroupId: String = DEFAULT_GROUP_ID,
    ): ShelfLibraryData {
        val base = normalize(data, now)
        val target = targetGroupId.takeIf { id -> base.groups.any { it.id == id } } ?: DEFAULT_GROUP_ID
        val present = bookIds.toSet()
        val books = base.books.filterKeys { it in present }.toMutableMap()
        bookIds.forEach { id ->
            if (id !in books) books[id] = ShelfBookMeta(bookId = id, groupId = target, addedAt = firstSeenAt(id).takeIf { it > 0 } ?: now)
        }
        return base.copy(books = books)
    }

    fun cleanName(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }

    fun createGroup(data: ShelfLibraryData, name: String, now: Long, id: String = UUID.randomUUID().toString()): Result<ShelfLibraryData> = runCatching {
        val clean = cleanName(name) ?: error("书架名称不能为空")
        require(data.groups.none { it.name == clean }) { "已经有名为「$clean」的书架" }
        data.copy(groups = data.groups + ShelfGroup(id, clean, now))
    }

    fun renameGroup(data: ShelfLibraryData, groupId: String, name: String): Result<ShelfLibraryData> = runCatching {
        val clean = cleanName(name) ?: error("书架名称不能为空")
        require(data.groups.any { it.id == groupId }) { "书架不存在" }
        require(data.groups.none { it.id != groupId && it.name == clean }) { "已经有名为「$clean」的书架" }
        data.copy(groups = data.groups.map { if (it.id == groupId) it.copy(name = clean) else it })
    }

    /** Deletes a shelf. Its books are kept and move to the default shelf. */
    fun deleteGroup(data: ShelfLibraryData, groupId: String): Result<ShelfLibraryData> = runCatching {
        require(groupId != DEFAULT_GROUP_ID) { "默认书架不能删除" }
        require(data.groups.any { it.id == groupId }) { "书架不存在" }
        data.copy(
            groups = data.groups.filterNot { it.id == groupId },
            books = data.books.mapValues { (_, meta) -> if (meta.groupId == groupId) meta.copy(groupId = DEFAULT_GROUP_ID) else meta },
        )
    }

    fun moveBook(data: ShelfLibraryData, bookId: String, groupId: String): Result<ShelfLibraryData> = runCatching {
        require(data.groups.any { it.id == groupId }) { "书架不存在" }
        val meta = data.books[bookId] ?: ShelfBookMeta(bookId = bookId)
        data.copy(books = data.books + (bookId to meta.copy(groupId = groupId)))
    }

    fun updateBook(data: ShelfLibraryData, bookId: String, transform: (ShelfBookMeta) -> ShelfBookMeta): ShelfLibraryData {
        val meta = data.books[bookId] ?: ShelfBookMeta(bookId = bookId)
        return data.copy(books = data.books + (bookId to transform(meta)))
    }

    fun removeBook(data: ShelfLibraryData, bookId: String): ShelfLibraryData = data.copy(books = data.books - bookId)

    fun groupOf(data: ShelfLibraryData, bookId: String): String = data.books[bookId]?.groupId ?: DEFAULT_GROUP_ID

    fun counts(data: ShelfLibraryData): Map<String, Int> =
        data.groups.associate { group -> group.id to data.books.values.count { it.groupId == group.id } }
}

private val ShelfJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Encodes/decodes the shelf file; a damaged file decodes to null so the caller can keep a backup. */
object ShelfLibraryCodec {
    fun encode(data: ShelfLibraryData): String = ShelfJson.encodeToString(ShelfLibraryData.serializer(), data)
    fun decode(text: String): ShelfLibraryData? = runCatching { ShelfJson.decodeFromString(ShelfLibraryData.serializer(), text) }.getOrNull()
}

/**
 * File-backed shelf store (`files/shelf_library_v1.json`). Writes go to a temporary file and are
 * renamed into place, so a crash mid-write never leaves a half-written shelf.
 */
class ShelfLibraryStore(private val file: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<ShelfLibraryData> = _data.asStateFlow()

    private fun load(): ShelfLibraryData {
        if (!file.exists()) return ShelfLibrary.empty(clock())
        val decoded = ShelfLibraryCodec.decode(runCatching { file.readText() }.getOrDefault(""))
        if (decoded == null) {
            // Keep the unreadable file for diagnosis instead of silently overwriting it.
            runCatching { file.copyTo(File(file.parentFile, file.name + ".broken-" + clock()), overwrite = true) }
            return ShelfLibrary.empty(clock())
        }
        return ShelfLibrary.normalize(decoded, clock())
    }

    suspend fun update(transform: (ShelfLibraryData) -> ShelfLibraryData): ShelfLibraryData = mutex.withLock {
        val next = ShelfLibrary.normalize(transform(_data.value), clock())
        if (next != _data.value) {
            persist(next)
            _data.value = next
        }
        next
    }

    /** Like [update] but for operations that can be refused (duplicate names and so on). */
    suspend fun tryUpdate(transform: (ShelfLibraryData) -> Result<ShelfLibraryData>): Result<ShelfLibraryData> = mutex.withLock {
        transform(_data.value).map { result ->
            val next = ShelfLibrary.normalize(result, clock())
            if (next != _data.value) {
                persist(next)
                _data.value = next
            }
            next
        }
    }

    private fun persist(data: ShelfLibraryData) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(ShelfLibraryCodec.encode(data))
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "书架保存失败" }
        }
    }

    companion object {
        const val FILE_NAME = "shelf_library_v1.json"
    }
}
