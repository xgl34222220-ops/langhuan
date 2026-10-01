package com.xiguli.langhuan.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiguli.langhuan.data.LocalImportLimitsV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** No ContentResolver access, import ViewModel or database work happens at intent receipt. */
class ExternalBookImportCoordinatorV1(private val savedState: SavedStateHandle) : ViewModel() {
    private val queue = ExternalBookImportQueueV1(restoredExternalBookRequestsV1(savedState))
    private val _pending = MutableStateFlow(queue.snapshot())
    val pending = _pending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    init { publish() }

    fun receive(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_SEND) return
        runCatching {
            val uri = externalBookUriV1(intent)
            queue.offer(intent.action, uri?.toString(), intent.type)
        }.onSuccess { publish() }
            .onFailure { _error.value = it.message ?: "无法读取这次分享，请重新选择小说文件" }
    }

    fun dismiss(uri: String) { queue.dismiss(uri); publish() }
    fun clearError() { _error.value = null }

    private fun publish() {
        val snapshot = queue.snapshot()
        savedState["external_book_uris"] = ArrayList(snapshot.map { it.uri })
        savedState["external_book_mimes"] = ArrayList(snapshot.map { it.mimeType.orEmpty() })
        _pending.value = snapshot
    }
}

private fun restoredExternalBookRequestsV1(saved: SavedStateHandle): List<ExternalBookRequestV1> {
    val uris = runCatching { saved.get<Any?>("external_book_uris") as? List<*> }.getOrNull().orEmpty()
    val mimes = runCatching { saved.get<Any?>("external_book_mimes") as? List<*> }.getOrNull().orEmpty()
    return uris.take(4).mapIndexedNotNull { index, value ->
        (value as? String)?.let { uri -> ExternalBookRequestV1(uri, (mimes.getOrNull(index) as? String)?.ifBlank { null }) }
    }
}

@Suppress("DEPRECATION")
internal fun externalBookUriV1(intent: Intent): Uri? = when (intent.action) {
    Intent.ACTION_VIEW -> intent.data
    Intent.ACTION_SEND -> {
        // EXTRA_TEXT may contain a URL or path; it is never treated as a document.
        val stream = intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM)
        require(stream == null || stream is Uri) { "分享的附件不是有效文件" }
        (stream as? Uri) ?: intent.clipData?.let { clip ->
            require(clip.itemCount == 1) { "请每次分享一本小说" }
            clip.getItemAt(0).uri
        }
    }
    else -> null
}

internal data class ExternalBookPreviewV1(val name: String, val size: Long?)

internal fun externalBookPreviewV1(resolver: ContentResolver, uri: Uri): ExternalBookPreviewV1 {
    var name = "外部小说"
    var size: Long? = null
    try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = safeLocalBookNameV1(cursor.getString(nameIndex).orEmpty())
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
            }
        }
    } catch (error: SecurityException) {
        throw SecurityException("文件读取授权已失效，请回到来源 App 重新打开或分享", error)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Display-name queries are optional. Actual readability is checked on confirmed import.
    }
    require((size ?: 0) <= LocalImportLimitsV1.BOOK_BYTES) { "文件过大，目前单本最大支持 96 MB" }
    return ExternalBookPreviewV1(name, size)
}

/** Mounted by the ready-only root, so a pending external intent cannot bypass startup checks. */
@Composable
fun ExternalBookImportHostV1(coordinator: ExternalBookImportCoordinatorV1, importer: LocalBookImportViewModelV1) {
    val requests by coordinator.pending.collectAsStateWithLifecycle()
    val coordinatorError by coordinator.error.collectAsStateWithLifecycle()
    val importState by importer.state.collectAsStateWithLifecycle()
    val request = requests.firstOrNull()
    val context = LocalContext.current
    var preview by remember(request?.uri) { mutableStateOf<ExternalBookPreviewV1?>(null) }
    var previewError by remember(request?.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(request?.uri) {
        val current = request ?: return@LaunchedEffect
        try {
            preview = withContext(Dispatchers.IO) { externalBookPreviewV1(context.contentResolver, Uri.parse(current.uri)) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            previewError = error.message ?: "无法读取这个文件，请重新分享"
        }
    }
    if (request != null) {
        val ownsImport = importState.externalRequestUri == request.uri
        val importing = ownsImport && importState.busy
        val finished = ownsImport && !importState.busy
        fun dismiss() {
            if (!importing) {
                coordinator.dismiss(request.uri)
                if (ownsImport) importer.dismissExternalRequest(request.uri)
            } else if (importState.canCancel) importer.cancelImport()
        }
        AlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(if (importing) "正在导入小说" else if (finished) "导入结果" else "导入到琅嬛？") },
            text = {
                Column {
                    Text(preview?.name ?: "外部小说")
                    Spacer(Modifier.height(12.dp))
                    when {
                        importing -> {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(if (importState.canCancel) "正在读取和检查文件…" else "正在保存到书架…")
                        }
                        finished -> Text(importState.error ?: importState.message ?: "导入已结束")
                        previewError != null -> Text(previewError.orEmpty())
                        preview == null -> Text("正在读取文件信息…")
                        importState.busy -> Text("请先等待当前小说导入完成")
                        else -> Text("将作为新书加入书架，支持 TXT、Markdown 和 EPUB，单本最大 96 MB。")
                    }
                }
            },
            confirmButton = {
                when {
                    importing -> if (importState.canCancel) TextButton(onClick = importer::cancelImport) { Text("取消导入") }
                    finished || previewError != null -> TextButton(onClick = ::dismiss) { Text("关闭") }
                    else -> TextButton(
                        enabled = preview != null && !importState.busy,
                        onClick = { importer.importUri(Uri.parse(request.uri), request.mimeType, request.uri) },
                    ) { Text("导入") }
                }
            },
            dismissButton = {
                if (!importing && !finished && previewError == null) TextButton(onClick = ::dismiss) { Text("取消") }
            },
        )
    } else if (coordinatorError != null) {
        AlertDialog(
            onDismissRequest = coordinator::clearError,
            title = { Text("无法导入这次分享") },
            text = { Text(coordinatorError.orEmpty()) },
            confirmButton = { TextButton(onClick = coordinator::clearError) { Text("关闭") } },
        )
    }
}
