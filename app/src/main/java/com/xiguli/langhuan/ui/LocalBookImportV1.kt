package com.xiguli.langhuan.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiguli.langhuan.data.StoryExchange
import com.xiguli.langhuan.data.StoryProjectManager
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalBookImportUiStateV1(
    val busy: Boolean = false,
    val currentFileName: String = "",
    val importedBookId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class LocalBookImportViewModelV1(application: Application) : AndroidViewModel(application) {
    private val projects = StoryProjectManager(application)
    private val _state = MutableStateFlow(LocalBookImportUiStateV1())
    val state: StateFlow<LocalBookImportUiStateV1> = _state.asStateFlow()

    fun importUri(uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            _state.update { it.copy(busy = true, error = null, message = null, importedBookId = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = app.contentResolver
                    val fileName = queryDisplayName(uri).ifBlank { "本地小说.txt" }
                    _state.update { it.copy(currentFileName = fileName) }
                    val bytes = resolver.openInputStream(uri)?.use { input -> input.readBytes() }
                        ?: error("无法读取这个文件")
                    require(bytes.isNotEmpty()) { "文件是空的" }
                    require(bytes.size <= MAX_LOCAL_BOOK_BYTES) { "文件过大，目前单本最大支持 96 MB" }
                    val normalized = normalizeBookBytesV1(fileName, bytes)
                    val manuscript = StoryExchange.`import`(fileName, normalized)
                    require(manuscript.chapters.any { it.content.isNotBlank() }) { "没有识别到可阅读正文" }
                    val created = projects.createImportedStory(manuscript)
                    val id = created.snapshot.novel.id
                    app.getSharedPreferences("local_book_meta_v1", Application.MODE_PRIVATE)
                        .edit()
                        .putString("name_$id", fileName)
                        .putLong("size_$id", bytes.size.toLong())
                        .putString("format_$id", localBookFormatV1(fileName))
                        .putLong("imported_$id", System.currentTimeMillis())
                        .apply()
                    created
                }
            }.onSuccess { created ->
                _state.update {
                    it.copy(
                        busy = false,
                        importedBookId = created.snapshot.novel.id,
                        message = "《${created.snapshot.novel.title}》已加入书架",
                        currentFileName = "",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        currentFileName = "",
                        error = error.message ?: "导入本地书籍失败",
                    )
                }
            }
        }
    }

    fun consumeImportedBook() = _state.update { it.copy(importedBookId = null) }
    fun clearFeedback() = _state.update { it.copy(message = null, error = null) }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    companion object {
        private const val MAX_LOCAL_BOOK_BYTES = 96 * 1024 * 1024
    }
}

private fun localBookFormatV1(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "epub" -> "EPUB"
    "md", "markdown" -> "Markdown"
    "txt" -> "TXT"
    else -> "本地文本"
}

internal fun normalizeBookBytesV1(fileName: String, bytes: ByteArray): ByteArray {
    if (fileName.lowercase().endsWith(".epub")) return bytes
    return decodeLocalBookTextV1(bytes).toByteArray(StandardCharsets.UTF_8)
}

internal fun decodeLocalBookTextV1(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    val decoded = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(StandardCharsets.UTF_16BE)
        else -> decodeStrictUtf8V1(bytes) ?: Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString()
    }
    return decoded.removePrefix("\uFEFF")
}

private fun decodeStrictUtf8V1(bytes: ByteArray): String? = runCatching {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()
