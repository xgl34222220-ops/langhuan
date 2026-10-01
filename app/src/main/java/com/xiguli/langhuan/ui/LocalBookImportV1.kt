package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.EpubImportedCoverV2
import com.xiguli.langhuan.data.epub.EpubImportBridge
import com.xiguli.langhuan.data.epub.EpubOriginalStore
import com.xiguli.langhuan.data.EpubOriginalTocV1
import com.xiguli.langhuan.data.StoryExchange
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.LocalImportLimitsV1
import com.xiguli.langhuan.data.readBoundedImportV1
import java.io.File
import java.io.InputStream
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalBookImportUiStateV1(
    val busy: Boolean = false,
    val canCancel: Boolean = false,
    val externalRequestUri: String? = null,
    val currentFileName: String = "",
    val importedBookId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class LocalBookImportViewModelV1(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val _state = MutableStateFlow(LocalBookImportUiStateV1())
    val state: StateFlow<LocalBookImportUiStateV1> = _state.asStateFlow()

    private var importJob: Job? = null
    private val openStream = AtomicReference<InputStream?>(null)

    fun importUri(uri: Uri, mimeType: String? = null, externalRequestUri: String? = null): Boolean {
        return importDocument(
            displayName = { queryDisplayName(uri) },
            openInput = { getApplication<Application>().contentResolver.openInputStream(uri) },
            mimeType = {
                mimeType ?: runCatching { getApplication<Application>().contentResolver.getType(uri) }
                    .onFailure { if (it is CancellationException || it is SecurityException) throw it }.getOrNull()
            },
            externalRequestUri = externalRequestUri,
        )
    }

    internal fun importDocument(
        displayName: () -> String,
        openInput: () -> InputStream?,
        mimeType: () -> String? = { null },
        externalRequestUri: String? = null,
    ): Boolean {
        if (_state.value.busy) return false
        // Acquire synchronously: a second confirmation in the same frame cannot start a job.
        _state.value = LocalBookImportUiStateV1(busy = true, canCancel = true, externalRequestUri = externalRequestUri)
        importJob = viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val importContext = currentCoroutineContext()
                    val checkCancelled = { importContext.ensureActive() }
                    val fileName = safeLocalBookNameV1(displayName())
                    _state.update { it.copy(currentFileName = fileName) }
                    val bytes = openInput()?.use { input ->
                        openStream.set(input)
                        try {
                            input.readBoundedImportV1(LocalImportLimitsV1.BOOK_BYTES, "文件过大，目前单本最大支持 96 MB", checkCancelled)
                        } finally {
                            openStream.compareAndSet(input, null)
                        }
                    } ?: error("无法读取这个文件")
                    val payload = prepareLocalBookPayloadV1(fileName, mimeType(), bytes, checkCancelled)
                    val preparedEpub = if (payload.format == LocalBookFormatV1.EPUB) {
                        EpubImportBridge.prepare(app, payload.fileName, payload.bytes, checkCancelled)
                    } else null
                    val epub = preparedEpub?.text
                    val manuscript = epub?.manuscript ?: StoryExchange.`import`(payload.fileName, payload.bytes, checkCancelled)
                    require(manuscript.chapters.any { it.content.isNotBlank() }) { "没有识别到可阅读正文" }
                    checkCancelled()
                    ParsedLocalBookV1(fileName, bytes.size, payload.format, manuscript, epub, preparedEpub?.original)
                }
                currentCoroutineContext().ensureActive()
                // Parsing and all size/content checks finish before the only shelf transaction.
                // Once this short commit starts, disable Cancel and finish recording its result.
                _state.update { it.copy(canCancel = false) }
                withContext(NonCancellable + Dispatchers.IO) {
                    var created = projects.createImportedStory(parsed.manuscript) { createdBook ->
                        parsed.original?.let { original ->
                            EpubOriginalStore(File(app.filesDir, "epub_originals_v1"))
                                .associate(createdBook.snapshot.novel.id, original)
                        }
                    }
                    // Optional presentation metadata cannot turn a committed import into a reported
                    // failure. It only uses the newly allocated ID and never rewrites an existing book.
                    runCatching {
                        val cover = parsed.epub?.cover
                        if (cover != null && cover.bytes.isNotEmpty()) {
                            val coverPath = persistImportedCoverV2(app, created.snapshot.novel.id, cover)
                            if (coverPath.isNotBlank()) {
                                val updated = created.snapshot.copy(novel = created.snapshot.novel.copy(coverPath = coverPath))
                                created = projects.saveStructure(updated, created.draft)
                            }
                        }
                    }
                    val id = created.snapshot.novel.id
                    runCatching {
                        if (parsed.format == LocalBookFormatV1.EPUB) EpubOriginalTocV1.save(app, id, parsed.epub?.originalToc.orEmpty())
                        app.getSharedPreferences("local_book_meta_v1", Application.MODE_PRIVATE)
                            .edit()
                            .putString("name_$id", parsed.originalName)
                            .putLong("size_$id", parsed.size.toLong())
                            .putString("format_$id", parsed.format.label)
                            .putString("author_$id", parsed.epub?.author.orEmpty())
                            .putString("epub_parser_$id", if (parsed.format == LocalBookFormatV1.EPUB) "readium-original-3.4.0" else "")
                            .putLong("imported_$id", System.currentTimeMillis())
                            .apply()
                    }
                    _state.update {
                        it.copy(busy = false, canCancel = false, importedBookId = id,
                            message = "《${created.snapshot.novel.title}》已加入书架", currentFileName = "")
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException || !currentCoroutineContext().isActive) {
                    _state.update {
                        if (it.importedBookId != null) it else it.copy(busy = false, canCancel = false, currentFileName = "", message = "已取消导入")
                    }
                    throw CancellationException("导入已取消", error)
                }
                val message = when (error) {
                    is SecurityException -> "文件读取授权已失效，请回到来源 App 重新打开或分享"
                    is FileNotFoundException -> "文件已移动、删除或无法读取，请重新选择"
                    else -> error.message ?: "导入本地书籍失败"
                }
                _state.update { it.copy(busy = false, canCancel = false, currentFileName = "", error = message) }
            }
        }
        return true
    }

    fun cancelImport() {
        if (!_state.value.busy || !_state.value.canCancel) return
        importJob?.cancel()
        // Closing the currently owned stream also releases a provider blocked in read().
        closeOwnedStream()
    }

    override fun onCleared() {
        importJob?.cancel()
        closeOwnedStream()
        super.onCleared()
    }

    private fun closeOwnedStream() {
        val input = openStream.getAndSet(null) ?: return
        // ViewModel clearing cancels viewModelScope before onCleared. This one close job must
        // outlive that scope so a provider blocked in read() is still released; it writes no state.
        val cleanup = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        cleanup.launch {
            try { runCatching { input.close() } }
            finally { cleanup.coroutineContext[kotlinx.coroutines.Job]?.cancel() }
        }
    }

    fun consumeImportedBook() = _state.update { it.copy(importedBookId = null) }
    fun clearFeedback() = _state.update { it.copy(message = null, error = null) }
    fun dismissExternalRequest(uri: String) = _state.update {
        if (!it.busy && it.externalRequestUri == uri) it.copy(externalRequestUri = null, message = null, error = null) else it
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.onFailure { if (it is CancellationException || it is SecurityException) throw it }.getOrDefault("")
    }
}

private fun persistImportedCoverV2(
    application: Application,
    novelId: String,
    cover: EpubImportedCoverV2,
): String {
    val extension = importedCoverExtensionV2(cover)
    val directory = File(application.filesDir, "covers").apply { mkdirs() }
    val file = File(directory, "$novelId-imported.$extension")
    return runCatching {
        file.writeBytes(cover.bytes)
        file.absolutePath
    }.getOrDefault("")
}

private fun importedCoverExtensionV2(cover: EpubImportedCoverV2): String {
    val fromName = cover.fileName.substringAfterLast('.', "").lowercase()
    if (fromName in setOf("jpg", "jpeg", "png", "webp", "gif")) return fromName
    return when (cover.mediaType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
}

private data class ParsedLocalBookV1(
    val originalName: String,
    val size: Int,
    val format: LocalBookFormatV1,
    val manuscript: com.xiguli.langhuan.data.ImportedManuscript,
    val epub: com.xiguli.langhuan.data.EpubImportResultV2?,
    val original: EpubOriginalStore.Prepared?,
)
