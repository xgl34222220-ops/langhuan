package com.xiguli.langhuan.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.xiguli.langhuan.data.ImportedChapter
import com.xiguli.langhuan.data.ImportedManuscript
import com.xiguli.langhuan.data.StoryProjectManager
import com.xiguli.langhuan.data.local.LanghuanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorWindowImportV33Test {
    @Test
    fun oversizedImportedChapterCanBeReadWithoutCursorWindowCrash() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = StoryProjectManager(context)
        // Large enough to exceed the common CursorWindow per-row ceiling once serialized.
        val body = buildString(1_200_000) {
            repeat(1_200_000) { append(if (it % 97 == 0) '\n' else '章') }
        }
        val created = manager.createImportedStory(
            ImportedManuscript(
                title = "CursorWindow QA ${System.nanoTime()}",
                chapters = listOf(ImportedChapter("超长章节", body)),
            )
        )
        val id = created.snapshot.novel.id
        try {
            val chapters = manager.chapterDrafts(id)
            assertEquals(1, chapters.size)
            assertEquals(body.length, chapters.single().content.length)
            assertTrue(chapters.single().content.startsWith("章") || chapters.single().content.startsWith("\n"))
        } finally {
            val sql = LanghuanDatabase.get(context).openHelper.writableDatabase
            sql.execSQL("DELETE FROM chapter_versions WHERE novelId = ?", arrayOf(id))
            sql.execSQL("DELETE FROM chapter_state WHERE novelId = ?", arrayOf(id))
            sql.execSQL("DELETE FROM memory_chunks WHERE novelId = ?", arrayOf(id))
            sql.execSQL("DELETE FROM story_state WHERE novelId = ?", arrayOf(id))
        }
    }
}
