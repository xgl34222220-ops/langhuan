package com.xiguli.langhuan.data.epub

import android.content.Context
import com.xiguli.langhuan.data.EpubImportResultV2
import com.xiguli.langhuan.data.EpubImporterV2
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import java.io.File

/** Stage and verify the original before the shelf transaction; keep text as a separate fallback. */
object EpubImportBridge {
    data class PreparedImport(val staged: EpubOriginalStore.Staged, val text: EpubImportResultV2) {
        val original get() = staged.prepared
    }

    suspend fun prepare(context: Context, fileName: String, bytes: ByteArray, checkCancelled: () -> Unit): PreparedImport {
        val store = EpubOriginalStore(File(context.filesDir, "epub_originals_v1"))
        val staged = bytes.inputStream().use { store.stage(it, checkCancelled) }
        try {
            val prepared = staged.prepared
            EpubPublicationSession.open(context, prepared).close()
            checkCancelled()
            val text = try {
                EpubImporterV2.import(fileName, prepared.rendering.readBytes(), checkCancelled)
            } catch (error: IllegalArgumentException) {
                // Image-only books have valid original pages even when text extraction has no body.
                if (error.message != "EPUB 没有识别到可阅读正文") throw error
                EpubImportResultV2(ImportedManuscript(
                    title = prepared.archive.title.ifBlank { fileName.substringBeforeLast('.') },
                    chapters = prepared.archive.spine.mapIndexed { index, _ ->
                        ImportedChapter("第${index + 1}部分", "本部分为图像内容，请使用 EPUB 原版阅读查看。")
                    },
                ))
            }
            return PreparedImport(staged, text)
        } catch (error: Exception) {
            runCatching { staged.close() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }
}
