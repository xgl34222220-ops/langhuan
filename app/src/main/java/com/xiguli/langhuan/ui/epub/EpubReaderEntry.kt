package com.xiguli.langhuan.ui.epub

import android.content.Context
import android.content.Intent
import com.xiguli.langhuan.data.epub.EpubOriginalStore
import java.io.File

object EpubReaderEntry {
    fun store(context: Context) = EpubOriginalStore(File(context.filesDir, "epub_originals_v1"))
    fun isEpub(context: Context, bookId: String): Boolean = store(context).hasOriginal(bookId) ||
        context.getSharedPreferences("local_book_meta_v1", Context.MODE_PRIVATE)
            .getString("format_$bookId", "").equals("EPUB", ignoreCase = true)
    fun intent(context: Context, bookId: String) = Intent(context, EpubReaderActivity::class.java)
        .putExtra(EpubReaderActivity.EXTRA_BOOK_ID, bookId)
}
