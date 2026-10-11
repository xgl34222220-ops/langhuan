package com.xiguli.langhuan.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import com.xiguli.langhuan.data.shelf.BookFacts
import com.xiguli.langhuan.data.shelf.ShelfBookMeta
import com.xiguli.langhuan.data.shelf.ShelfLibrary
import com.xiguli.langhuan.data.shelf.ShelfLibraryData
import com.xiguli.langhuan.data.shelf.ShelfLibraryStore
import com.xiguli.langhuan.domain.ChapterDraft
import com.xiguli.langhuan.domain.StorySnapshot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The book identity the reader screens work with. Local books never carry an online source. */
data class ReaderBookUi(
    val id: String,
    val title: String,
    val genre: String = "",
    val premise: String = "",
    val theme: String = "",
    val coverPath: String = "",
    val currentWords: Int = 0,
    val targetWords: Int = 0,
    val currentChapter: Int = 1,
    val updatedAt: Long = 0L,
    val sourceId: String = "",
    val sourceBookUrl: String = "",
)

/** What the importer recorded about the original file (prefs `local_book_meta_v1`). */
data class LocalBookFileInfo(
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val format: String = "",
    val author: String = "",
    val importedAt: Long = 0L,
)

/** One book as the shelf shows it. */
data class LocalShelfBook(
    val book: ReaderBookUi,
    val meta: ShelfBookMeta,
    val file: LocalBookFileInfo,
    val progress: ShelfReadingProgressV92,
    val chapterIndex: Int,
    val chapterTotal: Int,
    val lastReadAt: Long,
) {
    val id: String get() = book.id
    val title: String get() = book.title
    val author: String get() = meta.author ?: file.author
    val groupId: String get() = meta.groupId
    val importedAt: Long get() = file.importedAt.takeIf { it > 0 } ?: meta.addedAt
}

/** Figures for 图书详情 that need the whole text; computed on demand and cached in the meta. */
data class LocalBookDetail(
    val bookId: String,
    val wordCount: Long,
    val chapterCount: Int,
    val currentChapterTitle: String,
    val currentChapterNumber: Int,
)

data class LocalReaderState(
    val stories: List<ReaderBookUi> = emptyList(),
    val libraryLoaded: Boolean = false,
    val shelf: ShelfLibraryData = ShelfLibrary.empty(),
    val books: List<LocalShelfBook> = emptyList(),
    val currentGroupId: String = ShelfLibrary.DEFAULT_GROUP_ID,
    val detail: LocalBookDetail? = null,
    val openedBook: ReaderBookUi? = null,
    val chapters: List<ChapterDraft> = emptyList(),
    val readingChapter: ChapterDraft? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
)

private val LocalJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

class LocalReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LanghuanDatabase.get(application)
    private val projects = StoryProjectManager(application)
    private val store = ShelfLibraryStore(File(application.filesDir, ShelfLibraryStore.FILE_NAME))
    private val importPrefs = application.getSharedPreferences(LOCAL_BOOK_META_PREFS, Context.MODE_PRIVATE)
    private val progressPrefs = application.getSharedPreferences(ShelfReadingProgressStoreV92.PREFS, Context.MODE_PRIVATE)
    private val uiPrefs = application.getSharedPreferences(SHELF_UI_PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        LocalReaderState(currentGroupId = uiPrefs.getString(KEY_CURRENT_GROUP, null) ?: ShelfLibrary.DEFAULT_GROUP_ID),
    )
    val state: StateFlow<LocalReaderState> = _state.asStateFlow()

    /** Book id whose import just finished: it joins the shelf that was open at the time. */
    private var pendingImportGroup: Pair<String, String>? = null

    init {
        viewModelScope.launch {
            db.storyStateDao().observeAll().map { rows ->
                rows.mapNotNull { row ->
                    runCatching {
                        val snapshot = LocalJson.decodeFromString(StorySnapshot.serializer(), row.snapshotJson)
                        ReaderBookUi(
                            id = snapshot.novel.id,
                            title = snapshot.novel.title,
                            genre = snapshot.novel.genre,
                            premise = snapshot.novel.premise,
                            theme = snapshot.novel.theme,
                            coverPath = snapshot.novel.coverPath,
                            currentWords = snapshot.novel.currentWords,
                            targetWords = snapshot.novel.targetWords,
                            currentChapter = snapshot.novel.currentChapter,
                            updatedAt = row.updatedAt,
                        )
                    }.getOrNull()
                }
            }.flowOn(Dispatchers.Default).collect { books ->
                val pending = pendingImportGroup
                val shelf = store.update { data ->
                    val reconciled = ShelfLibrary.reconcile(
                        data,
                        books.map { it.id },
                        now = System.currentTimeMillis(),
                        firstSeenAt = { id -> importPrefs.getLong("imported_$id", 0L) },
                    )
                    if (pending != null && reconciled.books.containsKey(pending.first)) {
                        ShelfLibrary.moveBook(reconciled, pending.first, pending.second).getOrDefault(reconciled)
                    } else reconciled
                }
                if (pending != null && shelf.books.containsKey(pending.first)) pendingImportGroup = null
                publish(books, shelf)
            }
        }
        viewModelScope.launch {
            store.data.collect { shelf -> publish(_state.value.stories, shelf) }
        }
    }

    private fun publish(stories: List<ReaderBookUi>, shelf: ShelfLibraryData) {
        val books = stories.map { book -> shelfBook(book, shelf) }
            .sortedWith(compareByDescending<LocalShelfBook> { maxOf(it.lastReadAt, it.importedAt) }.thenBy { it.title })
        _state.update { current ->
            val group = current.currentGroupId.takeIf { id -> shelf.groups.any { it.id == id } } ?: ShelfLibrary.DEFAULT_GROUP_ID
            val opened = current.openedBook?.id?.let { id -> stories.firstOrNull { it.id == id } }
            current.copy(stories = stories, libraryLoaded = true, shelf = shelf, books = books, currentGroupId = group,
                openedBook = opened ?: current.openedBook?.takeIf { book -> stories.any { it.id == book.id } })
        }
    }

    private fun shelfBook(book: ReaderBookUi, shelf: ShelfLibraryData): LocalShelfBook {
        val id = book.id
        val file = LocalBookFileInfo(
            fileName = importPrefs.getString("name_$id", null).orEmpty(),
            sizeBytes = importPrefs.getLong("size_$id", 0L),
            format = importPrefs.getString("format_$id", null).orEmpty(),
            author = importPrefs.getString("author_$id", null).orEmpty(),
            importedAt = importPrefs.getLong("imported_$id", 0L),
        )
        return LocalShelfBook(
            book = book,
            meta = shelf.books[id] ?: ShelfBookMeta(bookId = id),
            file = file,
            progress = ShelfReadingProgressStoreV92.load(progressPrefs, id),
            chapterIndex = progressPrefs.getInt("index_$id", -1),
            chapterTotal = progressPrefs.getInt("total_$id", 0),
            lastReadAt = progressPrefs.getLong("updated_$id", 0L),
        )
    }

    /** Re-reads reading progress after the reader closes. */
    fun refreshShelf() = publish(_state.value.stories, store.data.value)

    fun selectGroup(groupId: String) {
        uiPrefs.edit().putString(KEY_CURRENT_GROUP, groupId).apply()
        _state.update { it.copy(currentGroupId = groupId) }
    }

    fun rememberImportTarget(bookId: String) {
        val group = _state.value.currentGroupId
        if (group == ShelfLibrary.DEFAULT_GROUP_ID) return
        if (_state.value.shelf.books.containsKey(bookId)) {
            viewModelScope.launch { store.tryUpdate { ShelfLibrary.moveBook(it, bookId, group) } }
        } else pendingImportGroup = bookId to group
    }

    fun createGroup(name: String, moveBookId: String? = null) = viewModelScope.launch {
        val id = java.util.UUID.randomUUID().toString()
        store.tryUpdate { data ->
            ShelfLibrary.createGroup(data, name, System.currentTimeMillis(), id).map { created ->
                moveBookId?.let { book -> ShelfLibrary.moveBook(created, book, id).getOrDefault(created) } ?: created
            }
        }.onSuccess { _state.update { it.copy(message = if (moveBookId != null) "已新建书架并移入" else "已新建书架") } }
            .onFailure { e -> _state.update { it.copy(message = e.message) } }
    }

    fun renameGroup(groupId: String, name: String) = viewModelScope.launch {
        store.tryUpdate { ShelfLibrary.renameGroup(it, groupId, name) }
            .onSuccess { _state.update { it.copy(message = "书架已重命名") } }
            .onFailure { e -> _state.update { it.copy(message = e.message) } }
    }

    fun deleteGroup(groupId: String) = viewModelScope.launch {
        store.tryUpdate { ShelfLibrary.deleteGroup(it, groupId) }
            .onSuccess { _state.update { it.copy(message = "书架已删除，书籍已移到「${ShelfLibrary.DEFAULT_GROUP_NAME}」") } }
            .onFailure { e -> _state.update { it.copy(message = e.message) } }
    }

    fun moveBook(bookId: String, groupId: String) = viewModelScope.launch {
        store.tryUpdate { ShelfLibrary.moveBook(it, bookId, groupId) }
            .onSuccess { data -> _state.update { it.copy(message = "已移动到「${data.groups.first { g -> g.id == groupId }.name}」") } }
            .onFailure { e -> _state.update { it.copy(message = e.message) } }
    }

    fun deleteBook(bookId: String) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    projects.deleteBook(bookId)
                    val app = getApplication<Application>()
                    File(app.filesDir, "covers").listFiles()?.filter { it.name.startsWith(bookId) }?.forEach { it.delete() }
                    store.data.value.books[bookId]?.customCoverPath?.let { File(it).delete() }
                    importPrefs.edit().remove("name_$bookId").remove("size_$bookId").remove("format_$bookId")
                        .remove("author_$bookId").remove("epub_parser_$bookId").remove("imported_$bookId").apply()
                }
                store.update { ShelfLibrary.removeBook(it, bookId) }
            }.onSuccess {
                _state.update { it.copy(isBusy = false, message = "图书已删除") }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, message = e.message ?: "删除失败") }
            }
        }
    }

    /** Loads (or reuses cached) whole-book figures for 图书详情. */
    fun loadDetail(bookId: String) {
        viewModelScope.launch {
            val shelfBook = _state.value.books.firstOrNull { it.id == bookId } ?: return@launch
            val cached = shelfBook.meta
            val chapters = withContext(Dispatchers.IO) { projects.chapterDrafts(bookId) }.sortedBy { it.readingOrder }
            val words = cached.wordCount ?: withContext(Dispatchers.Default) { chapters.sumOf { BookFacts.wordCount(it.content) } }
            val index = shelfBook.chapterIndex.takeIf { it in chapters.indices }
            val saved = ReaderProgressStoreV11.load(getApplication(), bookId, shelfBook.book.currentChapter.coerceAtLeast(1))
            val current = index?.let { chapters[it] } ?: chapters.firstOrNull { it.chapterNumber == saved.chapterNumber } ?: chapters.firstOrNull()
            _state.update {
                it.copy(detail = LocalBookDetail(
                    bookId = bookId,
                    wordCount = words,
                    chapterCount = chapters.size,
                    currentChapterTitle = current?.let { c -> readerDisplayChapterTitleV13(c.title, c.chapterNumber) }.orEmpty(),
                    currentChapterNumber = (current?.let { c -> chapters.indexOf(c) } ?: 0) + 1,
                ))
            }
            if (cached.wordCount != words || cached.chapterCount != chapters.size) {
                store.update { data -> ShelfLibrary.updateBook(data, bookId) { it.copy(wordCount = words, chapterCount = chapters.size) } }
            }
        }
    }

    fun clearDetail() = _state.update { it.copy(detail = null) }

    /**
     * Saves 图书详情: the title goes into the book itself, the author and cover choice into the
     * shelf metadata. [cover] is null when the cover was not changed.
     */
    fun saveDetails(bookId: String, title: String, author: String, cover: CoverChoice?) {
        viewModelScope.launch {
            val book = _state.value.books.firstOrNull { it.id == bookId } ?: return@launch
            runCatching {
                if (title.trim() != book.title) {
                    withContext(Dispatchers.IO) { projects.renameBook(bookId, title) } ?: error("书名不能为空")
                }
                store.update { data ->
                    ShelfLibrary.updateBook(data, bookId) { meta ->
                        val withAuthor = meta.copy(author = author.trim().takeIf { it != book.file.author || meta.author != null })
                        when (cover) {
                            null -> withAuthor
                            is CoverChoice.Original -> withAuthor.copy(customCoverPath = null, preferGenerated = false)
                            is CoverChoice.Generated -> withAuthor.copy(customCoverPath = null, generatedCoverStyle = cover.style, preferGenerated = true)
                            is CoverChoice.Custom -> withAuthor.copy(customCoverPath = cover.path, preferGenerated = false)
                        }
                    }
                }
            }.onSuccess { _state.update { it.copy(message = "已保存") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "保存失败") } }
        }
    }

    /** Copies a picked gallery image into app storage and returns its path. */
    suspend fun importCoverImage(bookId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val dir = File(app.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "$bookId-custom-${System.currentTimeMillis()}.img")
            app.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (file.length() <= 0L || file.length() > 12L * 1024 * 1024) {
                file.delete()
                null
            } else file.absolutePath
        }.getOrNull()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /* ------------------------------- Reader ------------------------------- */

    fun openBook(id: String, onReady: () -> Unit = {}) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            runCatching {
                val book = _state.value.stories.firstOrNull { it.id == id } ?: error("找不到这本书")
                val chapters = withContext(Dispatchers.IO) { projects.chapterDrafts(id) }.sortedBy { it.readingOrder }
                require(chapters.isNotEmpty()) { "这本书没有可读取的章节" }
                val saved = ReaderProgressStoreV11.load(getApplication(), id, book.currentChapter.coerceAtLeast(1))
                val target = chapters.firstOrNull { it.chapterNumber == saved.chapterNumber }
                    ?: chapters.firstOrNull { it.chapterNumber == book.currentChapter }
                    ?: chapters.first()
                Triple(book, chapters, target)
            }.onSuccess { (book, chapters, target) ->
                _state.update { it.copy(isBusy = false, openedBook = book, chapters = chapters, readingChapter = target) }
                onReady()
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, message = e.message ?: "打开失败") }
            }
        }
    }

    fun closeBook() {
        _state.update { it.copy(openedBook = null, chapters = emptyList(), readingChapter = null) }
        refreshShelf()
    }

    fun openReader(chapterNumber: Int) {
        val chapter = _state.value.chapters.firstOrNull { it.chapterNumber == chapterNumber } ?: return
        _state.update { it.copy(readingChapter = chapter) }
    }

    fun renameChapter(bookId: String, chapterNumber: Int, title: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    projects.renameChapter(bookId, chapterNumber, title)
                    projects.chapterDrafts(bookId)
                }
            }.onSuccess { chapters ->
                _state.update { state ->
                    if (state.openedBook?.id != bookId) state else state.copy(
                        chapters = chapters.sortedBy { it.readingOrder },
                        readingChapter = chapters.firstOrNull { it.chapterNumber == state.readingChapter?.chapterNumber } ?: state.readingChapter,
                    )
                }
            }.onFailure { e -> _state.update { it.copy(message = e.message ?: "重命名失败") } }
        }
    }

    companion object {
        const val LOCAL_BOOK_META_PREFS = "local_book_meta_v1"
        private const val SHELF_UI_PREFS = "shelf_ui_v1"
        private const val KEY_CURRENT_GROUP = "current_group"
    }
}

/** A cover picked on the 封面 tab. */
sealed class CoverChoice {
    data object Original : CoverChoice()
    data class Generated(val style: Int) : CoverChoice()
    data class Custom(val path: String) : CoverChoice()
}
