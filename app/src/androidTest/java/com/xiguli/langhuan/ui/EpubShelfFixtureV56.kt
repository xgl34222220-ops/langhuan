package com.xiguli.langhuan.ui

import android.content.Context
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import kotlinx.coroutines.runBlocking

/** The V50 original reader requires the same real Room metadata as a user import. */
internal fun epubShelfFixtureV56(context: Context, key: String): String = runBlocking {
    val prefs = context.getSharedPreferences("epub_device_fixture_v56", 0)
    val projects = StoryProjectManager(context)
    prefs.getString(key, null)?.takeIf { projects.loadStory(it) != null } ?: run {
        val imported = projects.createImportedStory(ImportedManuscript(
            "EPUB 合法原创测试 $key", listOf(ImportedChapter("第一章 原创", "原创测试正文。".repeat(40))),
        ))
        imported.snapshot.novel.id.also { check(prefs.edit().putString(key, it).commit()) }
    }
}
